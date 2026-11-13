#!/usr/bin/env python3
"""Swap the worktree-compiled MediaCodecRenderer classes into the exoplayer AAR.

Why: the far-seek wedge rebuild (media3-exo-seek-discontinuity-codec-rebuild-r1
+ media3-exo-seek-stream-source-r1) arms a full codec release from
queryStreamStartsAtKeyFrame(). Only ChunkSampleStream and HlsSampleStream have
a real metadata channel for that query. The generic fall-through answered by
FLAG_PEEK-ing the next sample, which cannot distinguish "queue survived the
reset" from "queue momentarily empty while refilling after an ordinary seek"
-- both present nothing readable -- so on progressive streams (Drama local
proxy playback) every routine seek / adopt position reset whose peek raced an
empty queue tore the codec down and re-created it (device log 2026-11-09:
five decoder instances in 8s on one player during a single seek drag; the
episode-advance seam stall the user reported as "cut to the next episode,
pauses, then plays").

media3-exo-seek-stream-source-r2.patch limits the arming to the two metadata
queue types; generic streams return true (anchored) and flush, as upstream
does. This script recompiles MediaCodecRenderer.java from the worktree and
swaps exactly its classes into the shipped AAR; all unrelated class bytes are
preserved. Use --install to write the AAR and refresh media-lock.json.
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
       "androidx/media3/exoplayer/mediacodec/MediaCodecRenderer.java")
PREFIX = "androidx/media3/exoplayer/mediacodec/MediaCodecRenderer"


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
    ap.add_argument("--work", default="/tmp/seek-query-fix")
    args = ap.parse_args()

    if "codecReleaseOnNextReset" not in SRC.read_text():
        sys.exit("wedge patch missing from compiler input")
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
    for aar in sorted(MAVEN.glob(f"*/{VERSION}/*.aar")):
        module = aar.parent.parent.name
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
    old_aar = aar_path("media3-exoplayer").read_bytes()
    old_jar = classes_jar("media3-exoplayer")
    with zipfile.ZipFile(io.BytesIO(old_jar)) as z:
        old_names = set(z.namelist())
        old_bytes = {n: z.read(n) for n in old_names}
    expect = {n for n in old_names
              if n == PREFIX + ".class" or n.startswith(PREFIX + "$")}
    if set(emitted) != expect:
        sys.exit(f"unexpected emitted classes: {sorted(emitted)} vs {sorted(expect)}")
    changed = [n for n in sorted(expect) if emitted[n] != old_bytes[n]]
    print("emitted:", {k: digest(v)[:12] for k, v in sorted(emitted.items())})
    print("byte-changed vs shipped:", changed)
    # Byte diffs beyond the outer class are debug/line metadata only: verified
    # 2026-11-09 by javap -c -p comparison of shipped vs recompiled classes --
    # all inner classes are code-identical, and in the outer class the only
    # code delta is the queryStreamStartsAtKeyFrame tail.

    new_jar = repack(old_jar, dict(emitted))
    with zipfile.ZipFile(io.BytesIO(new_jar)) as z:
        assert set(z.namelist()) == old_names
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
                               " +seek-stream-source-r2: wedge codec-release "
                               "arming limited to Chunk/HLS metadata queues; "
                               "generic (progressive) streams flush instead of "
                               "tearing the codec down on every seek/adopt "
                               "reset (episode-advance seam stall, device log "
                               "2026-11-09). MediaCodecRenderer classes only.")
                break
        else:
            sys.exit("exoplayer override not found in media-lock.json")
        lock_path.write_text(json.dumps(lock, indent=2) + "\n")
        print("installed + lock updated")
    else:
        print("dry run (no --install)")


if __name__ == "__main__":
    main()
