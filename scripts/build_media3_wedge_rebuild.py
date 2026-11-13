#!/usr/bin/env python3
"""Rebuild only MediaCodecRenderer classes, preserving every unrelated shipped class.

The input AAR/source JAR is the committed local baseline (media-lock.json
artifact override for media3-exoplayer). The source patch is also listed in
build_media_deps.sh for full source builds. Use --install to update the
coupled AAR, sources, metadata, checksums and lock.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.11.0-alpha01-fongmi"
NAME = "media3-exoplayer-" + VERSION
REL = Path("third_party/maven/androidx/media3/media3-exoplayer") / VERSION
JAVA = "androidx/media3/exoplayer/mediacodec/MediaCodecRenderer.java"
PATCH = "third_party/patches/media3-exo-seek-discontinuity-codec-rebuild-r1.patch"
INPUT_AAR_SHA256 = "6c7b3c95d191688bb2f9fb46f19d19c032a56fc99dd483cb04dc4569872c7eef"
INPUT_SOURCES_SHA256 = "4ecf03ca0b430d66cae5a0aa44b7300dd2c1d2dbf28dde2e430c689d27ba990a"


def digest(data, algorithm="sha256"):
    return hashlib.new(algorithm, data).hexdigest()


def repack(data, updates):
    out = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as src, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as dst:
        for info in src.infolist():
            dst.writestr(info, updates.pop(info.filename, src.read(info.filename)))
        for name, value in sorted(updates.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            dst.writestr(info, value)
    return out.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--install", action="store_true")
    parser.add_argument("--work-dir", type=Path, default=ROOT / "build/exo-wedge-rebuild-media3")
    parser.add_argument("--gradle-cache", type=Path, default=Path.home() / ".gradle/caches/modules-2/files-2.1")
    args = parser.parse_args()
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    original_aar = (ROOT / REL / (NAME + ".aar")).read_bytes()
    original_sources = (ROOT / REL / (NAME + "-sources.jar")).read_bytes()
    if digest(original_aar) != INPUT_AAR_SHA256:
        raise ValueError("Unexpected input AAR")
    if digest(original_sources) != INPUT_SOURCES_SHA256:
        raise ValueError("Unexpected input sources")
    source_root = work / "source"
    target = source_root / "libraries/exoplayer/src/main/java" / JAVA
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(original_sources)) as archive:
        target.write_bytes(archive.read(JAVA))
    subprocess.run(["patch", "-p1", "-i", str(ROOT / PATCH)], cwd=source_root, check=True)
    text = target.read_text()
    if "codecReleaseOnNextReset" not in text:
        raise ValueError("Wedge rebuild patch was not applied to the compiler input")
    classpath = []
    for module in ["common", "container", "datasource", "database", "decoder", "extractor", "exoplayer"]:
        artifact = "media3-" + module
        aar = (ROOT / "third_party/maven/androidx/media3" / artifact / VERSION / (artifact + "-" + VERSION + ".aar")).read_bytes()
        if module == "exoplayer":
            aar = original_aar
        with zipfile.ZipFile(io.BytesIO(aar)) as archive:
            jar = work / (artifact + ".jar")
            jar.write_bytes(archive.read("classes.jar"))
            classpath.append(jar)
    # Compile-only annotation/API jars. MediaCodecRenderer's external surface is
    # Guava Preconditions/collect plus androidx.annotation (already provided by
    # android.jar) and com.google.errorprone.annotations (inside the Guava jar);
    # checker-qual / annotation-jvm are only needed if present in the cache.
    guava = list(args.gradle_cache.glob("**/guava-*.jar")) or [
        p for p in [Path("/home/hatch/gradle-9.5.1/lib/guava-33.4.6-jre.jar")] if p.exists()]
    if not guava:
        raise ValueError("Resolve compile dependency first: guava")
    classpath.append(sorted(guava)[0])
    for name in ["checker-qual-*.jar", "annotation-jvm-*.jar"]:
        matches = list(args.gradle_cache.glob("**/" + name))
        if matches:
            classpath.append(matches[0])
    # androidx.annotation is not in android.jar nor in the media3 AARs (it is an
    # external androidx dep of the full Gradle build, absent on this machine).
    # Generate compile-only stubs for exactly the annotations this file uses;
    # annotations are CLASS-retention metadata and never enter the shipped
    # class bytes as code. The stub jar is compile-classpath only.
    stub_src = work / "annotation-stubs/androidx/annotation"
    stub_src.mkdir(parents=True, exist_ok=True)
    for ann in ["GuardedBy", "Nullable", "VisibleForTesting", "CallSuper", "IntDef",
                "StringDef", "SuppressLint", "CheckResult", "RequiresApi"]:
        if ann in ("IntDef", "StringDef"):
            elements = ("int[] value() default {}; boolean flag() default false;"
                        if ann == "IntDef" else
                        "String[] value() default {};")
        elif ann == "GuardedBy":
            elements = "String value();"
        elif ann == "VisibleForTesting":
            elements = "String otherwise() default \"\";"
        elif ann == "CallSuper":
            elements = ""
            # CallSuper's real retention is RUNTIME; keeping the stub CLASS-retention would
            # silently drop it from compiled classes, so it is generated separately below.
        elif ann == "SuppressLint":
            elements = "String[] value();"
        elif ann == "RequiresApi":
            elements = ("int value() default -1; int api() default -1;"
                        " String codename() default \"\";")
        else:
            elements = ""
        (stub_src / (ann + ".java")).write_text(
            "package androidx.annotation;\n"
            "import java.lang.annotation.*;\n"
            "@Retention(RetentionPolicy.CLASS)\n"
            "@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,"
            " ElementType.CONSTRUCTOR, ElementType.LOCAL_VARIABLE, ElementType.TYPE,"
            " ElementType.ANNOTATION_TYPE})\n"
            "public @interface " + ann + " { " + elements + " }\n")
    (stub_src / "CallSuper.java").write_text(
        "package androidx.annotation;\n"
        "import java.lang.annotation.*;\n"
        "@Retention(RetentionPolicy.RUNTIME)\n"
        "@Target({ElementType.METHOD})\n"
        "public @interface CallSuper { }\n")
    # Same treatment for the one error-prone annotation this file uses: the
    # gradle-bundled Guava (jre flavor) does not carry error-prone annotations
    # and the android flavor is absent on this machine.
    ep_src = work / "annotation-stubs/com/google/errorprone/annotations"
    ep_src.mkdir(parents=True, exist_ok=True)
    (ep_src / "CanIgnoreReturnValue.java").write_text(
        "package com.google.errorprone.annotations;\n"
        "import java.lang.annotation.*;\n"
        "@Retention(RetentionPolicy.CLASS)\n"
        "@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})\n"
        "public @interface CanIgnoreReturnValue { }\n")
    (ep_src / "CompileTimeConstant.java").write_text(
        "package com.google.errorprone.annotations;\n"
        "import java.lang.annotation.*;\n"
        "@Retention(RetentionPolicy.CLASS)\n"
        "@Target({ElementType.PARAMETER})\n"
        "public @interface CompileTimeConstant { }\n")
    cf_src = work / "annotation-stubs/org/checkerframework/checker/nullness/qual"
    cf_src.mkdir(parents=True, exist_ok=True)
    for ann, elems in [("MonotonicNonNull", ""),
                       ("RequiresNonNull", "String[] value();")]:
        (cf_src / (ann + ".java")).write_text(
            "package org.checkerframework.checker.nullness.qual;\n"
            "import java.lang.annotation.*;\n"
            "@Retention(RetentionPolicy.RUNTIME)\n"
            "@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,"
            " ElementType.CONSTRUCTOR, ElementType.TYPE})\n"
            "public @interface " + ann + " { " + elems + " }\n")
    imm_src = work / "annotation-stubs/org/immutables/value"
    imm_src.mkdir(parents=True, exist_ok=True)
    (imm_src / "Gson.java").write_text(
        "package org.immutables.value;\n"
        "import java.lang.annotation.*;\n"
        "import java.lang.reflect.*;\n"
        "@Retention(RetentionPolicy.RUNTIME)\n"
        "@Target({ElementType.TYPE})\n"
        "public @interface Gson {\n"
        "  @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE})"
        " @interface TypeAdapters { boolean empty() default false; }\n}\n")
    stub_classes = work / "annotation-stub-classes"
    stub_classes.mkdir(exist_ok=True)
    javac_bin = Path(os.environ.get("JAVA_HOME", "")) / "bin/javac"
    subprocess.run([str(javac_bin), "--release", "8", "-d", str(stub_classes)]
                   + [str(p) for p in stub_src.glob("*.java")]
                   + [str(p) for p in ep_src.glob("*.java")]
                   + [str(p) for p in imm_src.glob("*.java")]
                   + [str(p) for p in cf_src.glob("*.java")], check=True)
    stub_jar = work / "androidx-annotation-stubs.jar"
    subprocess.run(["jar", "--create", "--file", str(stub_jar), "-C", str(stub_classes), "."],
                   check=True, env={**os.environ, "PATH": str(javac_bin.parent) + ":" + os.environ["PATH"]})
    classpath.append(stub_jar)
    j2objc = list(args.gradle_cache.glob("**/j2objc-annotations-*.jar"))
    if j2objc:
        classpath.append(sorted(j2objc)[0])
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        for line in (ROOT / "local.properties").read_text().splitlines():
            if line.startswith("sdk.dir="):
                sdk = line.split("=", 1)[1]
    classpath.append(Path(sdk) / "platforms/android-36/android.jar")
    javac = Path(os.environ["JAVA_HOME"]) / "bin/javac" if "JAVA_HOME" in os.environ else Path("javac")
    classes = work / "classes"
    classes.mkdir(exist_ok=True)
    subprocess.run([str(javac), "-J-Duser.language=en", "-encoding", "UTF-8", "-g", "--release", "8", "-proc:none", "-Xlint:-classfile,-options",
                    "-classpath", os.pathsep.join(map(str, classpath)), "-d", str(classes), str(target)], check=True)
    updates = {str(p.relative_to(classes)): p.read_bytes() for p in classes.rglob("*.class")}
    if any(not n.startswith("androidx/media3/exoplayer/mediacodec/MediaCodecRenderer") for n in updates):
        raise ValueError("Compilation escaped the declared class boundary")
    if "androidx/media3/exoplayer/mediacodec/MediaCodecRenderer.class" not in updates:
        raise ValueError("MediaCodecRenderer classes missing from compiler output")
    with zipfile.ZipFile(io.BytesIO(original_aar)) as archive:
        old_classes = archive.read("classes.jar")
    new_classes = repack(old_classes, updates.copy())
    with zipfile.ZipFile(io.BytesIO(old_classes)) as old, zipfile.ZipFile(io.BytesIO(new_classes)) as new:
        preserved = 0
        for name in old.namelist():
            if name not in updates:
                if old.read(name) != new.read(name):
                    raise ValueError("Unrelated class changed: " + name)
                preserved += 1
    aar = repack(original_aar, {"classes.jar": new_classes})
    sources = repack(original_sources, {JAVA: target.read_bytes()})
    outputs = {NAME + ".aar": aar, NAME + "-sources.jar": sources}
    module = json.loads((ROOT / REL / (NAME + ".module")).read_text())
    for variant in module["variants"]:
        for item in variant.get("files", []):
            if item["name"] in outputs:
                data = outputs[item["name"]]
                item["size"] = len(data)
                for algorithm in ["md5", "sha1", "sha256", "sha512"]:
                    item[algorithm] = digest(data, algorithm)
    outputs[NAME + ".module"] = (json.dumps(module, indent=2) + "\n").encode()
    for name, data in list(outputs.items()):
        for algorithm in ["md5", "sha1", "sha256", "sha512"]:
            outputs[name + "." + algorithm] = digest(data, algorithm).encode()
    dest = ROOT / REL if args.install else work / "publication"
    dest.mkdir(parents=True, exist_ok=True)
    for name, data in outputs.items():
        (dest / name).write_bytes(data)
    if args.install:
        path = ROOT / "third_party/media-lock.json"
        lock = json.loads(path.read_text())
        patches = lock["fongmi_media"]["patches"]
        patches[:] = [p for p in patches if p["path"] != PATCH]
        patches.append({"path": PATCH, "sha256": digest((ROOT / PATCH).read_bytes())})
        for artifact in lock["fongmi_media"]["artifact_overrides"]:
            if artifact["coordinate"] == "androidx.media3:media3-exoplayer:" + VERSION:
                artifact["aar_sha256"] = digest(aar)
                artifact["sources_sha256"] = digest(sources)
                artifact["reason"] = artifact["reason"] + (
                    "; seek discontinuity codec rebuild batch"
                    " (media3-exo-seek-discontinuity-codec-rebuild-r1): after a seek that"
                    " reset the sample queues, the first codec reset releases and re-creates"
                    " the codec instead of flushing it, so a flushed codec is not refed the"
                    " tail of its pre-seek queue (far-seek wedge); surgical swap of"
                    " MediaCodecRenderer classes only, all"
                    " unrelated class bytes preserved")
        path.write_text(json.dumps(lock, indent=2) + "\n")
    print(json.dumps({"aar_sha256": digest(aar), "sources_sha256": digest(sources),
                      "preserved_class_entries": preserved, "compiled_classes": sorted(updates),
                      "compile_inputs": {str(p): digest(p.read_bytes()) for p in classpath},
                      "javac": str(javac)}, indent=2))


if __name__ == "__main__":
    main()
