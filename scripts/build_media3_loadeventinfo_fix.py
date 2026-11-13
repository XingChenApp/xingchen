#!/usr/bin/env python3
"""Swap the worktree-compiled LoadEventInfo classes into the exoplayer AAR.

Why: the shipped media3 AAR baseline predates the locked source baseline
(3c2cbe8a). Classes recompiled from the locked source during the seek/wedge
batches (HlsSampleStreamWrapper, ChunkSampleStream) link against
LoadEventInfo.Builder, but the shipped AAR only carries the old-baseline
LoadEventInfo (constructors only, no Builder). On a real device any load
event dispatch through those classes died with NoClassDefFoundError:
Landroidx/media3/exoplayer/source/LoadEventInfo$Builder; (1.4.116, user
device, 2026-11-09). The wedge rebuild scripts already compiled
LoadEventInfo.java for their classpath but deliberately never swapped it in;
this script performs exactly that swap.

ABI check (done by hand before writing this): the locked-source
LoadEventInfo keeps both legacy constructors and
copyWithTaskIdAndDurationMs with identical signatures and all legacy
fields; it only ADDS the Builder class, buildUpon() and the
steeredPathwayId field. Swapping the outer class plus adding Builder is
therefore safe for old-baseline callers.

Use --install to write the AAR and refresh media-lock.json checksums.
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
SRC = (ROOT / "third_party/sources/media/libraries/exoplayer/src/main/java/"
       "androidx/media3/exoplayer/source/LoadEventInfo.java")
PREFIX = "androidx/media3/exoplayer/source/LoadEventInfo"


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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", action="store_true")
    ap.add_argument("--work", default="/tmp/loadeventinfo-fix")
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
    for module in ("media3-exoplayer", "media3-common", "media3-datasource"):
        jf = work / f"{module}-classes.jar"
        jf.write_bytes(classes_jar(module))
        jar_files.append(str(jf))
    cp = jar_files + [str(guava[0]), str(annot[0]),
          str(checker[0]), str(errorprone),
          str(Path.home() / "workspace/android-sdk/platforms/android-36/android.jar")]
    javac = str(Path.home() / "workspace/.jdk21/bin/javac")
    cmd = [javac, "--release", "8", "-cp", ":".join(cp), "-d", str(work / "out"),
           str(SRC)]
    print("+", " ".join(cmd))
    subprocess.run(cmd, check=True)

    emitted = {}
    for p in sorted((work / "out").rglob("*.class")):
        rel = p.relative_to(work / "out").as_posix()
        emitted[rel] = p.read_bytes()
    expect = {PREFIX + ".class", PREFIX + "$Builder.class", PREFIX + "$1.class"}
    if set(emitted) != expect:
        sys.exit(f"unexpected emitted classes: {sorted(emitted)}")
    print("emitted:", {k: digest(v)[:12] for k, v in emitted.items()})

    old_aar = aar_path("media3-exoplayer").read_bytes()
    old_jar = classes_jar("media3-exoplayer")
    with zipfile.ZipFile(io.BytesIO(old_jar)) as z:
        old_names = set(z.namelist())
    assert PREFIX + ".class" in old_names
    assert PREFIX + "$Builder.class" not in old_names
    new_jar = repack(old_jar, dict(emitted))
    with zipfile.ZipFile(io.BytesIO(new_jar)) as z:
        new_names = set(z.namelist())
    assert new_names == old_names | expect
    print(f"classes.jar {len(old_names)} -> {len(new_names)} entries")
    new_aar = repack(old_aar, {"classes.jar": new_jar})
    print(f"exoplayer AAR sha256 {digest(old_aar)[:16]} -> {digest(new_aar)[:16]}")

    if args.install:
        aar_path("media3-exoplayer").write_bytes(new_aar)
        lock_path = ROOT / "third_party/media-lock.json"
        lock = json.loads(lock_path.read_text())
        coord = f"androidx.media3:media3-exoplayer:{VERSION}"
        for a in lock["fongmi_media"]["artifact_overrides"]:
            if a["coordinate"] == coord:
                a["aar_sha256"] = digest(new_aar)
                a["reason"] = (a.get("reason", "") +
                               " +loadeventinfo-fix: swapped in locked-source "
                               "LoadEventInfo + Builder (old baseline lacked "
                               "Builder; recompiled HLS/chunk classes link "
                               "against it, NoClassDefFoundError on device).")
                break
        else:
            sys.exit("exoplayer override not found in media-lock.json")
        lock_path.write_text(json.dumps(lock, indent=2) + "\n")
        print("installed + lock updated")
    else:
        print("dry run (no --install)")


if __name__ == "__main__":
    main()
