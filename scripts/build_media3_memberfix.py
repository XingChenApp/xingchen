#!/usr/bin/env python3
"""Swap locked-source classes into the media3 AARs to close member-level
ABI gaps left by the old AAR baseline.

Why: the shipped media3 AAR baseline predates the locked source baseline
(3c2cbe8a). Classes recompiled from the locked source by the patch chain
(HlsSampleStreamWrapper et al.) link against members the baseline classes
do not have: LoadEventInfo$Builder (1.4.116 NoClassDefFoundError, fixed by
build_media3_loadeventinfo_fix.py) and Chunk.steeredPathwayId plus
HlsChunkSource.getRedundantGroups (1.4.117 NoSuchFieldError on device,
2026-11-09). This script performs the closure swap for the steered-pathway
cluster: the whole Chunk constructor family and the HLS chunk-source side
are compiled from the locked source in one javac run (so the family is
internally consistent) and swapped into their module AARs.

Use --install to write the AARs and refresh media-lock.json checksums.
"""
import argparse
import hashlib
import io
import json
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.11.0-alpha01-fongmi"
MAVEN = ROOT / "third_party/maven/androidx/media3"
SRCROOT = ROOT / "third_party/sources/media/libraries"

# (module, [source files relative to SRCROOT])
TARGETS = [
    ("media3-exoplayer", [
        "exoplayer/src/main/java/androidx/media3/exoplayer/source/chunk/Chunk.java",
        "exoplayer/src/main/java/androidx/media3/exoplayer/source/chunk/BaseMediaChunk.java",
        "exoplayer/src/main/java/androidx/media3/exoplayer/source/chunk/MediaChunk.java",
        "exoplayer/src/main/java/androidx/media3/exoplayer/source/chunk/ContainerMediaChunk.java",
        "exoplayer/src/main/java/androidx/media3/exoplayer/source/chunk/SingleSampleMediaChunk.java",
        "exoplayer/src/main/java/androidx/media3/exoplayer/source/chunk/DataChunk.java",
        "exoplayer/src/main/java/androidx/media3/exoplayer/source/chunk/InitializationChunk.java",
        "exoplayer/src/main/java/androidx/media3/exoplayer/upstream/ParsingLoadable.java",
    ]),
    ("media3-exoplayer-hls", [
        "exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsMediaChunk.java",
        "exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsChunkSource.java",
        "exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/playlist/HlsRedundantGroup.java",
        "exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/playlist/DefaultHlsPlaylistTracker.java",
        "exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/playlist/HlsMediaPlaylist.java",
        "exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/playlist/HlsPlaylistParser.java",
        "exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsMediaChunkExtractor.java",
        "exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/BundledHlsMediaChunkExtractor.java",
        "exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/MediaParserHlsMediaChunkExtractor.java",
    ]),
    ("media3-extractor", [
        "extractor/src/main/java/androidx/media3/extractor/ts/TsExtractor.java",
        "extractor/src/main/java/androidx/media3/extractor/ts/HlsSampleAesExtractorOutput.java",
    ]),
    ("media3-common", [
        "common/src/main/java/androidx/media3/common/Format.java",
    ]),
]
CP_MODULES = ["media3-common", "media3-container", "media3-datasource",
              "media3-decoder", "media3-extractor", "media3-exoplayer",
              "media3-exoplayer-hls", "media3-exoplayer-dash",
              "media3-exoplayer-smoothstreaming", "media3-ui"]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def aar_path(module):
    return MAVEN / module / VERSION / f"{module}-{VERSION}.aar"


def classes_jar(module):
    with zipfile.ZipFile(aar_path(module)) as z:
        return z.read("classes.jar")


def repack(data, updates):
    out = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as src, \
            zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as dst:
        for info in src.infolist():
            dst.writestr(info, updates.pop(info.filename, src.read(info.filename)))
        for name, value in sorted(updates.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            dst.writestr(info, value)
    return out.getvalue()


def module_of(class_path):
    if "/common/" in class_path:
        return "media3-common"
    if "/extractor/" in class_path:
        return "media3-extractor"
    if "/hls/" in class_path:
        return "media3-exoplayer-hls"
    if "/dash/" in class_path:
        return "media3-exoplayer-dash"
    if "/smoothstreaming/" in class_path:
        return "media3-exoplayer-smoothstreaming"
    return "media3-exoplayer"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", action="store_true")
    ap.add_argument("--work", default=str(Path.home() / "workspace/tmpwork/memberfix"))
    args = ap.parse_args()

    work = Path(args.work)
    (work / "out").mkdir(parents=True, exist_ok=True)

    deps = Path.home() / "workspace/.deps1416/jars"
    guava = sorted(deps.glob("guava-*-android.jar"))
    annot = sorted(deps.glob("annotation-jvm-*.jar"))
    checker = sorted(deps.glob("checker-qual-*.jar"))
    if not (guava and annot and checker):
        sys.exit("missing compile deps in .deps1416/jars")
    errorprone = Path("/home/hatch/gradle-9.5.1/lib/error_prone_annotations-2.42.0.jar")

    jar_files = []
    for module in CP_MODULES:
        p = aar_path(module)
        if not p.exists():
            continue
        jf = work / f"{module}-classes.jar"
        jf.write_bytes(classes_jar(module))
        jar_files.append(str(jf))
    cp = jar_files + [str(guava[0]), str(annot[0]), str(checker[0]),
                      str(errorprone),
                      str(Path.home() / "workspace/android-sdk/platforms/android-36/android.jar")]
    javac = str(Path.home() / "workspace/.jdk21/bin/javac")
    sources = [str(SRCROOT / rel) for _, files in TARGETS for rel in files]
    cmd = [javac, "--release", "8", "-nowarn", "-cp", ":".join(cp),
           "-d", str(work / "out")] + sources
    print("+ javac", len(sources), "files")
    subprocess.run(cmd, check=True)

    emitted = {}
    for p in sorted((work / "out").rglob("*.class")):
        rel = p.relative_to(work / "out").as_posix()
        emitted[rel] = p.read_bytes()
    print("emitted:", len(emitted), "classes")
    by_module = {}
    for rel, data in emitted.items():
        by_module.setdefault(module_of(rel), {})[rel] = data
    for module, upd in sorted(by_module.items()):
        old_aar = aar_path(module).read_bytes()
        old_jar = classes_jar(module)
        with zipfile.ZipFile(io.BytesIO(old_jar)) as z:
            old_names = set(z.namelist())
        new_jar = repack(old_jar, dict(upd))
        with zipfile.ZipFile(io.BytesIO(new_jar)) as z:
            new_names = set(z.namelist())
        added = new_names - old_names
        print(f"{module}: {len(old_names)} -> {len(new_names)} entries, "
              f"swapped {len(upd)}, added {sorted(added)}")
        new_aar = repack(old_aar, {"classes.jar": new_jar})
        print(f"  AAR sha256 {digest(old_aar)[:16]} -> {digest(new_aar)[:16]}")
        if args.install:
            aar_path(module).write_bytes(new_aar)
            lock_path = ROOT / "third_party/media-lock.json"
            lock = json.loads(lock_path.read_text())
            coord = f"androidx.media3:{module}:{VERSION}"
            for a in lock["fongmi_media"]["artifact_overrides"]:
                if a["coordinate"] == coord:
                    a["aar_sha256"] = digest(new_aar)
                    a["reason"] = (a.get("reason", "") +
                                   " +memberfix: steered-pathway cluster "
                                   "swapped from locked source (Chunk family, "
                                   "HlsChunkSource/HlsMediaChunk/"
                                   "DefaultHlsPlaylistTracker, "
                                   "HlsRedundantGroup).")
                    break
            else:
                sys.exit(f"{coord} override not found in media-lock.json")
            lock_path.write_text(json.dumps(lock, indent=2) + "\n")
    print("installed + lock updated" if args.install else "dry run (no --install)")


if __name__ == "__main__":
    main()
