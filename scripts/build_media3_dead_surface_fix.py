#!/usr/bin/env python3
"""Swap the worktree-compiled MediaCodecVideoRenderer classes into the
exoplayer AAR.

Why: 1.4.119 on-device log 2026-11-09 16:41:18 — a page surface was
destroyed (page swiped away) while its MSG_SET_VIDEO_OUTPUT still sat in
the playback-thread queue; setOutput() handed the released surface to
MediaCodec.setOutputSurface, which threw IllegalArgumentException and
killed playback with ERROR_CODE_FAILED_RUNTIME_CHECK. The locked source
now revalidates at handle time (setOutput: an invalid surface is a
removal, output cleared in order, next valid surface restores it) — the
same outcome the official client gets from its holder-side dead-surface
filter. The patch adds statements inside one method only: no signature,
field or inner-class changes, so swapping the class set is ABI-neutral
for every other class in the AAR.

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
# The shipped AAR is an old-baseline surgical artifact; its matching
# source lives in the shipped sources jar. The locked worktree source
# of this file has drifted APIs vs the shipped AAR (MediaCodecInfo.
# alignVideoSize etc.) and must NOT be compiled against it — the patch
# is applied to the sources-jar file, which links against the shipped
# classes by construction. (The worktree file carries the same patch
# for future full builds.)
SOURCES_JAR = (MAVEN / "media3-exoplayer" / VERSION /
               f"media3-exoplayer-{VERSION}-sources.jar")
SRC_ENTRY = "androidx/media3/exoplayer/video/MediaCodecVideoRenderer.java"
PREFIX = "androidx/media3/exoplayer/video/MediaCodecVideoRenderer"

PATCH_ANCHOR = """    @Nullable Surface displaySurface = output instanceof Surface ? (Surface) output : null;
"""
PATCH_GUARD = """
    // [hg patch: dead-surface filter, 2026-11-09] A surface can be destroyed
    // after this message was queued (its page swiped away or its host page
    // finished) but before it is handled on the playback thread; handing the
    // codec a released surface makes MediaCodec.setOutputSurface throw
    // IllegalArgumentException (player ERROR_CODE_FAILED_RUNTIME_CHECK;
    // 1.4.119 on-device log 2026-11-09 16:41:18). Revalidate at handle time:
    // an invalid surface is a removal — clear the output in order here and
    // let the next valid surface message restore it. Mirrors the official
    // client's holder-side rule that a dead surface never reaches the engine.
    if (displaySurface != null && !displaySurface.isValid()) {
      displaySurface = null;
    }
"""


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
    ap.add_argument("--work", default=str(
        Path.home() / "workspace/tmpwork/deadsurface-fix"))
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
    for module in ("media3-exoplayer", "media3-common", "media3-datasource",
                   "media3-decoder", "media3-extractor", "media3-container"):
        jf = work / f"{module}-classes.jar"
        jf.write_bytes(classes_jar(module))
        jar_files.append(str(jf))
    cp = jar_files + [str(guava[0]), str(annot[0]),
          str(checker[0]), str(errorprone),
          str(Path.home() / "workspace/android-sdk/platforms/android-36/android.jar")]
    javac = str(Path.home() / "workspace/.jdk21/bin/javac")
    # Extract the shipped-baseline source and apply the guard in place.
    with zipfile.ZipFile(SOURCES_JAR) as z:
        src_text = z.read(SRC_ENTRY).decode("utf-8")
    if "dead-surface filter" in src_text:
        sys.exit("sources-jar file already patched — unexpected baseline")
    if src_text.count(PATCH_ANCHOR) != 1:
        sys.exit("patch anchor not found exactly once in sources-jar file")
    src_text = src_text.replace(PATCH_ANCHOR, PATCH_ANCHOR + PATCH_GUARD, 1)
    src_path = work / "src" / SRC_ENTRY
    src_path.parent.mkdir(parents=True, exist_ok=True)
    src_path.write_text(src_text)
    cmd = [javac, "--release", "8", "-cp", ":".join(cp), "-d", str(work / "out"),
           str(src_path)]
    print("+", " ".join(cmd))
    subprocess.run(cmd, check=True)

    emitted = {}
    for p in sorted((work / "out").rglob("*.class")):
        rel = p.relative_to(work / "out").as_posix()
        if rel == PREFIX + ".class" or rel.startswith(PREFIX + "$"):
            emitted[rel] = p.read_bytes()
    if not emitted or (PREFIX + ".class") not in emitted:
        sys.exit(f"unexpected emitted classes: {sorted(emitted)}")
    print("emitted:", {k: digest(v)[:12] for k, v in emitted.items()})

    old_aar = aar_path("media3-exoplayer").read_bytes()
    old_jar = classes_jar("media3-exoplayer")
    with zipfile.ZipFile(io.BytesIO(old_jar)) as z:
        old_names = set(z.namelist())
    aar_prefix = {n for n in old_names
                  if n == PREFIX + ".class" or n.startswith(PREFIX + "$")}
    if set(emitted) != aar_prefix:
        sys.exit(f"emitted set != AAR prefix set:\n emitted={sorted(emitted)}\n"
                 f" aar={sorted(aar_prefix)}")
    new_jar = repack(old_jar, dict(emitted))
    with zipfile.ZipFile(io.BytesIO(new_jar)) as z:
        new_names = set(z.namelist())
    assert new_names == old_names
    print(f"classes.jar {len(old_names)} entries (unchanged set)")
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
                               " +dead-surface-fix: MediaCodecVideoRenderer"
                               ".setOutput revalidates the surface at handle"
                               " time (invalid = removal) so a surface"
                               " destroyed while its message was queued can"
                               " no longer reach MediaCodec.setOutputSurface"
                               " (1.4.119 device crash 2026-11-09 16:41:18).")
                break
        else:
            sys.exit("exoplayer override not found in media-lock.json")
        lock_path.write_text(json.dumps(lock, indent=2) + "\n")
        print("installed + lock updated")
    else:
        print("dry run only (pass --install to write)")


if __name__ == "__main__":
    main()
