#!/usr/bin/env python3
"""Verify the scoped E4-LIBASS publication; optionally check APKs and sanitize mask copies."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import re
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT / "third_party/exo-ass-native"
BASE = "845ce82c64b16c94a1db7a61ed6bd38d2d1d35ac"
VERSION = "1.11.0-alpha01-fongmi"
MAVEN = Path("third_party/maven/androidx/media3/media3-exoplayer") / VERSION
NAME = "media3-exoplayer-" + VERSION
PREFIX = "androidx/media3/exoplayer/text/TextRenderer"
ABI_ELF = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40)}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data, algorithm="sha256"):
    return hashlib.new(algorithm, data).hexdigest()


def archive(data):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        return {name: z.read(name) for name in z.namelist()}


def original(path):
    return subprocess.check_output(["git", "show", BASE + ":" + str(path)], cwd=ROOT)


def sdk_path():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        sdk = next(line.split("=", 1)[1] for line in (ROOT / "local.properties").read_text().splitlines()
                   if line.startswith("sdk.dir="))
    return Path(sdk)


def verify_elf(data, abi, api=24, page_size=16384):
    require(abi in ABI_ELF, "Unsupported ABI: " + abi)
    elf_class, machine = ABI_ELF[abi]
    is64 = elf_class == 2
    header_size, program_size = (64, 56) if is64 else (52, 32)
    require(len(data) >= header_size, "Truncated ELF header")
    require(data[:6] == b"\x7fELF" + bytes([elf_class, 1])
            and struct.unpack_from("<H", data, 18)[0] == machine, "ELF architecture mismatch: " + abi)
    require(struct.unpack_from("<H", data, 16)[0] == 3, "Expected an ELF shared object")
    phoff = struct.unpack_from("<Q" if is64 else "<I", data, 32 if is64 else 28)[0]
    phsize, phnum = struct.unpack_from("<HH", data, 54 if is64 else 42)
    require(phsize == program_size and phnum > 0 and phoff >= header_size
            and phoff + phsize * phnum <= len(data), "Invalid ELF program headers")
    alignment = []
    android_api = None
    for i in range(phnum):
        pos = phoff + i * phsize
        if is64:
            kind, flags, offset, vaddr, _, filesz, memsz, align = struct.unpack_from("<IIQQQQQQ", data, pos)
        else:
            kind, offset, vaddr, _, filesz, memsz, flags, align = struct.unpack_from("<IIIIIIII", data, pos)
        require(offset + filesz <= len(data), "Truncated ELF segment")
        if kind == 1:
            require(align >= page_size and align & (align - 1) == 0
                    and offset % page_size == vaddr % page_size, "Invalid ELF LOAD alignment")
            require(memsz >= filesz, "Invalid ELF LOAD size")
            require(flags & 3 != 3, "Writable executable LOAD")
            alignment.append(align)
        if kind == 4:
            end = offset + filesz
            while offset + 12 <= end:
                namesz, descsz, note_type = struct.unpack_from("<III", data, offset)
                offset += 12
                desc_start = offset + ((namesz + 3) & ~3)
                require(desc_start + descsz <= end, "Truncated ELF note")
                name = data[offset:offset + namesz].rstrip(b"\0")
                offset = desc_start + ((descsz + 3) & ~3)
                if name == b"Android" and note_type == 1:
                    require(descsz >= 4, "Truncated Android API note")
                    value = struct.unpack_from("<I", data, desc_start)[0]
                    require(android_api in (None, value), "Conflicting Android API notes")
                    android_api = value
    require(alignment and android_api == api, "Missing API " + str(api) + " Android note")
    return {"elf_class": "ELF64" if is64 else "ELF32", "machine": machine,
            "api": android_api, "load_alignment": alignment}


def verify_native(work):
    lock = json.loads((ROOT / "third_party/exo-ass-lock.json").read_text())
    provenance = json.loads((NATIVE / "build-provenance.json").read_text())
    require(provenance["schema"] == 2, "Expected per-ABI provenance")
    require(lock["android"] == provenance["android"], "Android provenance mismatch")
    require(lock["android"]["api"] == 24 and lock["android"]["page_size"] == 16384
            and lock["android"]["stl"] == "c++_static"
            and set(lock["android"]["abis"]) == set(ABI_ELF), "ABI/API/linkage drift")
    abis = lock["android"]["abis"]
    require(set(provenance["artifacts"]) == set(abis)
            and set(provenance["static_archives"]) == set(abis), "Incomplete ABI provenance")
    require(set(provenance["sources"]) == set(lock["sources"]), "Source graph mismatch")
    for name, source in lock["sources"].items():
        identity = source.get("commit") or source["sha256"]
        require(bool(re.fullmatch(r"[0-9a-f]{40}" if "commit" in source else r"[0-9a-f]{64}", identity)),
                "Unpinned source: " + name)
        require(provenance["sources"][name] == identity, "Source provenance mismatch: " + name)
        for notice in source["licenses"]:
            path = "licenses/" + name + "/" + notice
            require(digest((NATIVE / path).read_bytes()) == provenance["licenses"][path], "License mismatch: " + path)
    require(set(provenance["inputs"]) == {
        "third_party/exo-ass-lock.json", "scripts/build_exo_ass_native.py",
        "third_party/exo-ass-native/CMakeLists.txt", "third_party/exo-ass-native/exo_ass.cpp",
        "third_party/exo-ass-native/mask_copy.h"}, "Incomplete build inputs")
    for name, sha in provenance["inputs"].items():
        require(digest((ROOT / name).read_bytes()) == sha, "Rebuild required after input change: " + name)
    require({str(p.relative_to(NATIVE)) for p in (NATIVE / "prebuilt").rglob("*.so")}
            == {"prebuilt/" + abi + "/libexo_ass.so" for abi in abis}, "Unexpected prebuilt ABI/library set")
    host = "darwin-x86_64" if platform.system() == "Darwin" else "linux-x86_64"
    readelf = sdk_path() / "ndk" / lock["android"]["ndk"] / "toolchains/llvm/prebuilt" / host / "bin/llvm-readelf"
    result, manifest = {}, []
    for abi in abis:
        artifact = provenance["artifacts"][abi]
        require(artifact["path"] == "prebuilt/" + abi + "/libexo_ass.so", "Artifact path mismatch: " + abi)
        archives = provenance["static_archives"][abi]
        require(set(archives) == {"libass.a", "libfontconfig.a", "libfreetype.a", "libfribidi.a", "libharfbuzz.a", "libxml2.a"}
                and all(re.fullmatch(r"[0-9a-f]{64}", sha) for sha in archives.values()),
                "Incomplete independent archives: " + abi)
        lib = NATIVE / artifact["path"]
        data = lib.read_bytes()
        require(("meson, locked commit: " + lock["sources"]["libass"]["commit"]).encode() in data,
                "libass source-version stamp must match its locked commit: " + abi)
        sha = digest(data)
        require(sha == artifact["sha256"] and len(data) == artifact["bytes"], "Native provenance mismatch: " + abi)
        manifest.append(sha + "  " + artifact["path"])
        result[abi] = {"sha256": sha, "bytes": len(data), **verify_elf(data, abi)}
        elf = subprocess.check_output([str(readelf), "-h", "-l", "-d", "-n", "-A", "--dyn-syms", str(lib)], text=True)
        (work / ("libexo_ass." + abi + ".elf.txt")).write_text(elf)
        needed = re.findall(r"\(NEEDED\).*?\[([^]]+)\]", elf)
        require(set(needed) == {"libandroid.so", "liblog.so", "libEGL.so", "libGLESv2.so", "libm.so", "libdl.so", "libc.so"},
                "Unexpected native linkage: " + abi + " " + repr(needed))
        require("Library soname: [libexo_ass.so]" in elf, "SONAME mismatch: " + abi)
        exports = []
        for line in elf.splitlines():
            parts = line.split()
            if len(parts) == 8 and parts[0].rstrip(":").isdigit() and parts[4] in ("GLOBAL", "WEAK") and parts[6] != "UND":
                exports.append(parts[7])
        jni = "Java_com_fongmi_android_tv_player_exo_ass_AssNative_"
        require(set(exports) == {jni + x for x in ["create", "load", "loadHeader", "chunk", "render", "setSurface", "destroy", "createTestFonts", "testSurface", "readPixels"]},
                "Unexpected exported symbols: " + abi)
        result[abi].update(needed=needed, exports=exports)
    require((NATIVE / "MANIFEST.sha256").read_text() == "\n".join(manifest) + "\n", "Native manifest mismatch")
    return result


def verify_media():
    old = archive(original(MAVEN / (NAME + ".aar")))
    new = archive((ROOT / MAVEN / (NAME + ".aar")).read_bytes())
    require(old.keys() == new.keys(), "Unrelated AAR entry-set change")
    for name in old:
        if name != "classes.jar":
            require(old[name] == new[name], "Unrelated AAR resource changed: " + name)
    classes_old, classes_new = archive(old["classes.jar"]), archive(new["classes.jar"])
    preserved = 0
    for name, value in classes_old.items():
        if not name.startswith(PREFIX):
            require(classes_new.get(name) == value, "Unrelated class changed: " + name)
            preserved += 1
    require(all(name in classes_old or name.startswith(PREFIX) for name in classes_new), "Unexpected new class")
    require(PREFIX + "$Observer.class" in classes_new and PREFIX + "$Stream.class" in classes_new, "Observer API absent")
    old_src = archive(original(MAVEN / (NAME + "-sources.jar")))
    new_src = archive((ROOT / MAVEN / (NAME + "-sources.jar")).read_bytes())
    require(old_src.keys() == new_src.keys(), "Source entry-set change")
    for name in old_src:
        if name != PREFIX + ".java":
            require(old_src[name] == new_src[name], "Unrelated source changed: " + name)
    for suffix in [".aar", "-sources.jar", ".module"]:
        path = ROOT / MAVEN / (NAME + suffix)
        for algorithm in ["md5", "sha1", "sha256", "sha512"]:
            require(path.with_name(path.name + "." + algorithm).read_text().strip() == digest(path.read_bytes(), algorithm),
                    "Maven checksum mismatch: " + path.name)
    module = json.loads((ROOT / MAVEN / (NAME + ".module")).read_text())
    for variant in module["variants"]:
        for file in variant.get("files", []):
            data = (ROOT / MAVEN / file["name"]).read_bytes()
            require(file["size"] == len(data) and all(file[a] == digest(data, a) for a in ["md5", "sha1", "sha256", "sha512"]),
                    "Gradle metadata mismatch")
    lock = json.loads((ROOT / "third_party/media-lock.json").read_text())["fongmi_media"]
    patch = "third_party/patches/media3-exo-ass-observer.patch"
    entry = next(p for p in lock["patches"] if p["path"] == patch)
    require(entry["sha256"] == digest((ROOT / patch).read_bytes()), "Observer patch identity mismatch")
    entry = next(a for a in lock["artifact_overrides"] if a["coordinate"] == "androidx.media3:media3-exoplayer:" + VERSION)
    for key, suffix in [("aar_sha256", ".aar"), ("sources_sha256", "-sources.jar")]:
        require(entry[key] == digest((ROOT / MAVEN / (NAME + suffix)).read_bytes()), "Media lock mismatch")
    return {"preserved_class_entries": preserved, "aar_sha256": entry["aar_sha256"], "sources_sha256": entry["sources_sha256"]}


def verify_apk(path, native):
    data = path.read_bytes()
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        libs = [n for n in z.namelist() if n.endswith("/libexo_ass.so")]
        abis = {n.split("/")[1] for n in z.namelist()
                if n.startswith("lib/") and n.endswith(".so") and len(n.split("/")) == 3}
        require(abis and abis <= set(native), "Unsupported or missing APK native ABI")
        require(set(libs) == {"lib/" + abi + "/libexo_ass.so" for abi in abis}
                and len(libs) == len(abis), "APK is missing ASS or mixes ABI libraries")
        result = {"sha256": digest(data), "bytes": len(data), "libraries": {}}
        for abi in sorted(abis):
            info = z.getinfo("lib/" + abi + "/libexo_ass.so")
            require(digest(z.read(info)) == native[abi]["sha256"], "APK contains a stale or wrong-ABI ASS library: " + abi)
            name_len, extra_len = struct.unpack_from("<HH", data, info.header_offset + 26)
            offset = info.header_offset + 30 + name_len + extra_len
            require(info.compress_type != zipfile.ZIP_STORED or offset % 16384 == 0, "Uncompressed ASS library is not ZIP 16 KiB aligned")
            result["libraries"][abi] = {"bytes": info.file_size, "compressed_bytes": info.compress_size,
                                        "zip_method": info.compress_type, "data_offset": offset}
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", "--default-apk", type=Path, action="append", default=[],
                        help="Check a regular APK's ABI-matched ASS libraries; may be repeated")
    parser.add_argument("--sanitize", action="store_true")
    parser.add_argument("--work-dir", type=Path, default=ROOT / "build/exo-ass-verification")
    args = parser.parse_args()
    args.work_dir.mkdir(parents=True, exist_ok=True)
    result = {"native": verify_native(args.work_dir), "media3": verify_media()}
    if args.apk:
        result["apks"] = {str(path): verify_apk(path, result["native"]) for path in args.apk}
    if args.sanitize:
        binary = args.work_dir / "mask-copy-sanitized"
        subprocess.run(["clang++", "-std=c++17", "-g", "-O1", "-fsanitize=address,undefined", "-fno-sanitize-recover=all",
                        "-fno-omit-frame-pointer", str(NATIVE / "mask_copy_test.cpp"), "-o", str(binary)], check=True)
        subprocess.run([str(binary)], check=True)
        result["mask_copy_asan_ubsan"] = "PASS: 36 exact-last-row allocations and rejected invalid/oversized dimensions"
    (args.work_dir / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
