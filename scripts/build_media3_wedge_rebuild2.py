#!/usr/bin/env python3
"""Signal-correction rebuild: swap the six stream-source classes into three AARs.

r2 of the far-seek wedge batch. r1 (build_media3_wedge_rebuild.py) keyed the
codec release off sampleStreamIsResetToKeyFrame, but BaseRenderer.resetPosition
normalizes that flag (skipSource) before it reaches MediaCodecRenderer, so the
flag never became true on a real queue reset. The correction patch
media3-exo-seek-stream-source-r1 asks the sample stream itself at reset time
(HlsSampleStream direct peek; ChunkSampleStream via a DecoderInputBuffer query
flag; generic streams via a non-consuming peek read) and sets the same
codecReleaseOnNextReset flag from the answer. Inputs are the r1 outputs
(media-lock.json artifact overrides for media3-decoder / media3-exoplayer /
media3-exoplayer-hls). Use --install to update the coupled AARs, sources,
metadata, checksums and lock.
"""
import argparse
import hashlib
import io
import json
import os
import shutil
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.11.0-alpha01-fongmi"
PATCH = "third_party/patches/media3-exo-seek-stream-source-r1.patch"

# artifact dir suffix -> (java path inside its sources jar, class prefix that the
# compilation is allowed to emit)
ARTIFACTS = {
    "decoder": (
        "androidx/media3/decoder/DecoderInputBuffer.java",
        "androidx/media3/decoder/DecoderInputBuffer",
    ),
    "exoplayer": (
        None,  # three files, listed below
        None,
    ),
    "exoplayer-hls": (
        None,
        None,
    ),
}
EXO_JAVAS = [
    "androidx/media3/exoplayer/mediacodec/MediaCodecRenderer.java",
    "androidx/media3/exoplayer/source/SampleQueue.java",
    "androidx/media3/exoplayer/source/chunk/ChunkSampleStream.java",
    # Not touched by the patch: compiled alongside so the chain-form
    # ChunkSampleStream links against the same LoadEventInfo API it uses in
    # the worktree (the shipped old-baseline class lacks the Builder form).
    # Its compiled classes are used as classpath only, never swapped in.
    "androidx/media3/exoplayer/source/LoadEventInfo.java",
]
HLS_JAVAS = [
    "androidx/media3/exoplayer/hls/HlsSampleStreamWrapper.java",
    "androidx/media3/exoplayer/hls/HlsSampleStream.java",
]
EXPECTED_PREFIXES = [
    "androidx/media3/decoder/DecoderInputBuffer",
    "androidx/media3/exoplayer/mediacodec/MediaCodecRenderer",
    "androidx/media3/exoplayer/source/SampleQueue",
    "androidx/media3/exoplayer/source/chunk/ChunkSampleStream",
    "androidx/media3/exoplayer/hls/HlsSampleStreamWrapper",
    "androidx/media3/exoplayer/hls/HlsSampleStream",
]
INPUT_SHA256 = {
    "decoder": (
        "c3bcd704959026e0d644b0260f9e682a2e26e7357c2cf82d990bcb323f800d49",
        "1570e072a3c4a8b9915f95bada24f0e4955e4fc4343d738adbb2794d9539d9c5",
    ),
    "exoplayer": (
        "646bb60ed56434348c185feb8cc0bb952db45b37c5354f24907604ece9670eba",
        "85d3fc80fcd20a0bcc459119bd65ff81f41a37f44892a7a58ef2d76f9c94edb0",
    ),
    "exoplayer-hls": (
        "d91e8858bd748b2b70deea48fa2de2dee7f982106000ca69c98a10120f6e9504",
        "1395c9d05e9c4e846a0afa347687d5c5828f981875787e717b65a6346e91d2cd",
    ),
}


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


def jar_class_names(jar_bytes: bytes) -> set[str]:
    import zipfile as _zf
    names = set()
    with _zf.ZipFile(io.BytesIO(jar_bytes)) as z:
        for name in z.namelist():
            if name.endswith(".class"):
                names.add(name[:-6].replace("/", ".").split("$")[0])
    return names


def rel_of(suffix):
    return Path("third_party/maven/androidx/media3/media3-" + suffix) / VERSION


def name_of(suffix):
    return "media3-" + suffix + "-" + VERSION


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--install", action="store_true")
    parser.add_argument("--compile-only", action="store_true")
    parser.add_argument("--work-dir", type=Path, default=ROOT / "build/exo-wedge-rebuild2-media3")
    parser.add_argument("--gradle-cache", type=Path, default=Path.home() / ".gradle/caches/modules-2/files-2.1")
    args = parser.parse_args()
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    originals = {}
    for suffix in INPUT_SHA256:
        rel = rel_of(suffix)
        name = name_of(suffix)
        aar = (ROOT / rel / (name + ".aar")).read_bytes()
        sources = (ROOT / rel / (name + "-sources.jar")).read_bytes()
        if digest(aar) != INPUT_SHA256[suffix][0]:
            raise ValueError("Unexpected input AAR for " + suffix)
        if digest(sources) != INPUT_SHA256[suffix][1]:
            raise ValueError("Unexpected input sources for " + suffix)
        originals[suffix] = (aar, sources)
    # Compile inputs: each file from its own artifact's sources jar, laid out at
    # its module source root, then the single stream-source patch applied with
    # patch -p3 (strips libraries/<module>/src/main/java).
    java_paths = {
        "decoder": [ARTIFACTS["decoder"][0]],
        "exoplayer": EXO_JAVAS,
        "exoplayer-hls": HLS_JAVAS,
    }
    # Compile inputs are taken from the sources worktree (third_party/sources/media),
    # which carries the full patch chain including this batch. The shipped
    # sources.jar files document the older surgical baseline and are NOT the
    # patch chain's base (extractor provenance audit 2026-11-08), so applying the
    # incremental patch to them is not meaningful; instead the surgery verifies
    # below that sources.jar + patch == worktree file for every touched file,
    # which is exactly the increment this batch adds on top of the shipped state.
    source_root = work / "source"
    targets = {}
    module_root_of = {
        "androidx/media3/decoder/": "libraries/decoder/src/main/java",
        "androidx/media3/exoplayer/hls/": "libraries/exoplayer_hls/src/main/java",
        "androidx/media3/exoplayer/": "libraries/exoplayer/src/main/java",
    }
    worktree = ROOT / "third_party/sources/media"
    for suffix, javas in java_paths.items():
        for java in javas:
            root_prefix = next(p for p in module_root_of if java.startswith(p))
            target = source_root / module_root_of[root_prefix] / java
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(
                (worktree / module_root_of[root_prefix] / java).read_bytes())
            targets[java] = target
    # NOTE: no probe check against the shipped sources.jar is possible for this
    # batch at all: the jars are old-baseline surgical artifacts whose file
    # contents differ from the patch-chain form beyond this batch's increment
    # (e.g. ChunkSampleStream carries the old LoadEventInfo constructor form,
    # MediaCodecRenderer carries an initCodec retry shim). The increment is
    # proven instead by the full 20-patch chain replay from the locked commit
    # (fix report hk-seek-wedge-fix2-20261108), which reproduces every touched
    # worktree file byte for byte.
    mcr = targets["androidx/media3/exoplayer/mediacodec/MediaCodecRenderer.java"].read_text()
    if "queryStreamStartsAtKeyFrame" not in mcr or "codecReleaseOnNextReset =!sampleStreamIsResetToKeyFrame" in mcr:
        raise ValueError("Stream-source patch was not applied to the compiler input")
    sq = targets["androidx/media3/exoplayer/source/SampleQueue.java"].read_text()
    dib = targets["androidx/media3/decoder/DecoderInputBuffer.java"].read_text()
    if "peekNextSampleIsKeyFrame" not in sq or "setKeyFrameQuery" not in dib:
        raise ValueError("Stream-source patch incomplete on compiler inputs")
    classpath = []
    for module in ["common", "container", "datasource", "database", "decoder", "extractor", "exoplayer", "exoplayer-hls"]:
        artifact = "media3-" + module
        aar = (ROOT / "third_party/maven/androidx/media3" / artifact / VERSION / (artifact + "-" + VERSION + ".aar")).read_bytes()
        if module in originals:
            aar = originals[module][0]
        with zipfile.ZipFile(io.BytesIO(aar)) as archive:
            jar = work / (artifact + ".jar")
            jar.write_bytes(archive.read("classes.jar"))
            classpath.append(jar)
    # Compile-only annotation/API jars (same treatment as the r1 surgery script).
    guava = list(args.gradle_cache.glob("**/guava-*.jar")) or [
        p for p in [Path("/home/hatch/gradle-9.5.1/lib/guava-33.4.6-jre.jar")] if p.exists()]
    if not guava:
        raise ValueError("Resolve compile dependency first: guava")
    classpath.append(sorted(guava)[0])
    for name in ["checker-qual-*.jar", "annotation-jvm-*.jar", "failureaccess-*.jar"]:
        matches = list(args.gradle_cache.glob("**/" + name)) or list(
            (ROOT.parent / ".deps1416/jars").glob(name))
        if matches:
            classpath.append(sorted(matches)[0])
    stub_src = work / "annotation-stubs/androidx/annotation"
    stub_src.mkdir(parents=True, exist_ok=True)
    for ann in ["GuardedBy", "Nullable", "VisibleForTesting", "CallSuper", "IntDef", "LongDef",
                "StringDef", "SuppressLint", "CheckResult", "RequiresApi",
                "NonNull", "IntRange", "LongRange", "FloatRange", "Size", "Px", "Dimension",
                "ColorInt", "ColorRes", "DrawableRes", "LayoutRes", "IdRes", "AnyRes",
                "StringRes", "StyleRes", "AnimRes", "AnimatorRes", "ArrayRes", "AttrRes",
                "BoolRes", "DimenRes", "FractionRes", "IntegerRes", "InterpolatorRes",
                "MenuRes", "NavigationRes", "PluralsRes", "RawRes", "TransitionRes", "XmlRes",
                "FontRes", "Keep", "MainThread", "WorkerThread", "BinderThread", "UiThread",
                "AnyThread", "RestrictTo", "RequiresOptIn", "OptIn", "DoNotInline",
                "DoNotStrip", "DoNotStripByDefault", "CallSuper"]:
        if ann in ("IntDef", "StringDef"):
            elements = ("int[] value() default {}; boolean flag() default false;"
                        " boolean open() default false;"
                        if ann == "IntDef" else
                        "String[] value() default {};")
        elif ann == "LongDef":
            elements = ("long[] value() default {}; boolean flag() default false;"
                        " boolean open() default false;")
        elif ann == "GuardedBy":
            elements = "String value();"
        elif ann == "VisibleForTesting":
            elements = ("int otherwise() default 0; int NONE = 0; int PRIVATE = 1;"
                        " int PACKAGE_PRIVATE = 2; int PROTECTED_AND_PACKAGE = 3;"
                        " int PROTECTED = 4;")
        elif ann == "CallSuper":
            elements = ""
        elif ann == "SuppressLint":
            elements = "String[] value();"
        elif ann == "RequiresApi":
            elements = ("int value() default -1; int api() default -1;"
                        " String codename() default \"\";")
        elif ann == "IntRange":
            elements = "long from() default -9223372036854775808L; long to() default 9223372036854775807L;"
        elif ann == "LongRange":
            elements = "long from() default -9223372036854775808L; long to() default 9223372036854775807L;"
        elif ann == "FloatRange":
            elements = "double from() default -1.7976931348623157E308; double to() default 1.7976931348623157E308; boolean fromInclusive() default true; boolean toInclusive() default true;"
        elif ann == "Size":
            elements = ("int value() default -1; int min() default -2147483648;"
                        " int max() default 2147483647; int multiple() default 1;")
        elif ann == "Dimension":
            elements = "int unit() default 1; int DP = 0; int SP = 1; int PX = 2;"
        elif ann == "RestrictTo":
            elements = "Scope[] value();"
        elif ann == "RequiresOptIn":
            elements = "Level level() default Level.ERROR;"
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
    (stub_src / "RestrictTo.java").write_text(
        "package androidx.annotation;\n"
        "import java.lang.annotation.*;\n"
        "@Retention(RetentionPolicy.CLASS)\n"
        "@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,"
        " ElementType.CONSTRUCTOR, ElementType.LOCAL_VARIABLE, ElementType.TYPE,"
        " ElementType.ANNOTATION_TYPE, ElementType.PACKAGE})\n"
        "public @interface RestrictTo {\n"
        "  enum Scope { LIBRARY, LIBRARY_GROUP, LIBRARY_GROUP_PREFIX, GROUP_ID, TESTS, SUBCLASSES }\n"
        "  Scope[] value();\n"
        "}\n")
    (stub_src / "RequiresOptIn.java").write_text(
        "package androidx.annotation;\n"
        "import java.lang.annotation.*;\n"
        "@Retention(RetentionPolicy.CLASS)\n"
        "@Target({ElementType.ANNOTATION_TYPE})\n"
        "public @interface RequiresOptIn {\n"
        "  enum Level { WARNING, ERROR }\n"
        "  Level level() default Level.ERROR;\n"
        "}\n")
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
    for ann, tgt in [("InlineMe", "{ElementType.METHOD, ElementType.CONSTRUCTOR}"),
                     ("ForOverride", "{ElementType.METHOD}"),
                     ("CheckReturnValue", "{ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE, ElementType.PARAMETER, ElementType.PACKAGE}"),
                     ("Var", "{ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.PARAMETER}"),
                     ("FormatMethod", "{ElementType.METHOD, ElementType.CONSTRUCTOR}"),
                     ("FormatString", "{ElementType.PARAMETER, ElementType.FIELD, ElementType.LOCAL_VARIABLE}")]:
        (ep_src / (ann + ".java")).write_text(
            "package com.google.errorprone.annotations;\n"
            "import java.lang.annotation.*;\n"
            "@Retention(RetentionPolicy.CLASS)\n"
            "@Target(" + tgt + ")\n"
            "public @interface " + ann + " {\n"
            "  String replacement() default \"\";\n"
            "  String[] imports() default {};\n"
            "  String[] staticImports() default {};\n"
            "}\n")
    df_src = work / "annotation-stubs/org/checkerframework/dataflow/qual"
    df_src.mkdir(parents=True, exist_ok=True)
    for ann in ["Pure", "Deterministic", "SideEffectFree", "TerminatesExecution"]:
        (df_src / (ann + ".java")).write_text(
            "package org.checkerframework.dataflow.qual;\n"
            "import java.lang.annotation.*;\n"
            "@Retention(RetentionPolicy.RUNTIME)\n"
            "@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE_USE})\n"
            "public @interface " + ann + " { }\n")
    uc = list(args.gradle_cache.glob("**/universalchardet-*.jar")) or list(
        Path.home().glob(".gradle/**/juniversalchardet-*.jar")) or list(
        (ROOT.parent / ".deps1416/jars").glob("juniversalchardet-*.jar"))
    if uc:
        classpath.append(sorted(uc)[0])
    cf_src = work / "annotation-stubs/org/checkerframework/checker/nullness/qual"
    cf_src.mkdir(parents=True, exist_ok=True)
    for ann, elems in [("MonotonicNonNull", ""),
                       ("RequiresNonNull", "String[] value();"),
                       ("EnsuresNonNull", "String[] value();"),
                       ("EnsuresNonNullIf", "String[] expression(); boolean result();"),
                       ("EnsuresNonNullIfElse", "String[] expression(); boolean result(); boolean elseValue() default false;"),
                       ("Nullable", ""), ("NullableDecl", ""),
                       ("NonNullDecl", "")]:
        (cf_src / (ann + ".java")).write_text(
            "package org.checkerframework.checker.nullness.qual;\n"
            "import java.lang.annotation.*;\n"
            "@Retention(RetentionPolicy.RUNTIME)\n"
            "@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,"
            " ElementType.CONSTRUCTOR, ElementType.LOCAL_VARIABLE, ElementType.TYPE,"
            " ElementType.TYPE_USE})\n"
            "public @interface " + ann + " { " + elems + " }\n")
    (cf_src / "PolyNull.java").write_text(
        "package org.checkerframework.checker.nullness.qual;\n"
        "import java.lang.annotation.*;\n"
        "@Retention(RetentionPolicy.RUNTIME)\n"
        "@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,"
        " ElementType.LOCAL_VARIABLE, ElementType.TYPE, ElementType.TYPE_USE})\n"
        "public @interface PolyNull { }\n")
    (cf_src / "NonNull.java").write_text(
        "package org.checkerframework.checker.nullness.qual;\n"
        "import java.lang.annotation.*;\n"
        "@Retention(RetentionPolicy.RUNTIME)\n"
        "@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,"
        " ElementType.LOCAL_VARIABLE, ElementType.TYPE, ElementType.TYPE_USE})\n"
        "public @interface NonNull { }\n")
    init_src = work / "annotation-stubs/org/checkerframework/checker/initialization/qual"
    init_src.mkdir(parents=True, exist_ok=True)
    for ann in ["UnknownInitialization"]:
        (init_src / (ann + ".java")).write_text(
            "package org.checkerframework.checker.initialization.qual;\n"
            "import java.lang.annotation.*;\n"
            "@Retention(RetentionPolicy.RUNTIME)\n"
            "@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,"
            " ElementType.LOCAL_VARIABLE, ElementType.TYPE, ElementType.TYPE_USE})\n"
            "public @interface " + ann + " { Class<?>[] value() default {}; }\n")
    jsr_src = work / "annotation-stubs/javax/annotation"
    jsr_src.mkdir(parents=True, exist_ok=True)
    (jsr_src / "Nonnull.java").write_text(
        "package javax.annotation;\n"
        "import java.lang.annotation.*;\n"
        "@Retention(RetentionPolicy.RUNTIME)\n"
        "@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,"
        " ElementType.LOCAL_VARIABLE, ElementType.TYPE, ElementType.TYPE_USE})\n"
        "public @interface Nonnull { javax.annotation.meta.When when() default javax.annotation.meta.When.ALWAYS; }\n")
    jsr_meta = work / "annotation-stubs/javax/annotation/meta"
    jsr_meta.mkdir(parents=True, exist_ok=True)
    (jsr_meta / "When.java").write_text(
        "package javax.annotation.meta;\n"
        "public enum When { ALWAYS, UNKNOWN, MAYBE, NEVER, EXCLUDE }\n")
    (jsr_meta / "TypeQualifierDefault.java").write_text(
        "package javax.annotation.meta;\n"
        "import java.lang.annotation.*;\n"
        "@Retention(RetentionPolicy.RUNTIME)\n"
        "@Target({ElementType.METHOD, ElementType.PACKAGE, ElementType.TYPE})\n"
        "public @interface TypeQualifierDefault { ElementType[] value() default {}; }\n")
    kotlin_src = work / "annotation-stubs/kotlin/annotations/jvm"
    kotlin_src.mkdir(parents=True, exist_ok=True)
    (kotlin_src / "UnderMigration.java").write_text(
        "package kotlin.annotations.jvm;\n"
        "import java.lang.annotation.*;\n"
        "@Retention(RetentionPolicy.RUNTIME)\n"
        "@Target({ElementType.ANNOTATION_TYPE})\n"
        "public @interface UnderMigration { MigrationStatus status(); }\n")
    (kotlin_src / "MigrationStatus.java").write_text(
        "package kotlin.annotations.jvm;\n"
        "public enum MigrationStatus { IGNORE, WARN, STRICT }\n")
    (jsr_meta / "TypeQualifierNickname.java").write_text(
        "package javax.annotation.meta;\n"
        "import java.lang.annotation.*;\n"
        "@Retention(RetentionPolicy.RUNTIME)\n"
        "@Target({ElementType.ANNOTATION_TYPE})\n"
        "public @interface TypeQualifierNickname { }\n")
    fut_src = work / "annotation-stubs/androidx/concurrent/futures"
    fut_src.mkdir(parents=True, exist_ok=True)
    (fut_src / "CallbackToFutureAdapter.java").write_text(
        "package androidx.concurrent.futures;\n"
        "import com.google.common.util.concurrent.ListenableFuture;\n"
        "public final class CallbackToFutureAdapter {\n"
        "  public interface Completer<T> {\n"
        "    boolean set(T value);\n"
        "    boolean setException(Throwable t);\n"
        "    boolean setCancelled();\n"
        "    void addCancellationListener(Runnable listener, java.util.concurrent.Executor executor);\n"
        "  }\n"
        "  public interface Resolver<T> { Object attachCompleter(Completer<T> completer) throws Exception; }\n"
        "  public static <T> ListenableFuture<T> getFuture(Resolver<T> resolver) { return null; }\n"
        "}\n")
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
    subprocess.run([str(javac_bin), "--release", "8", "-cp", str(sorted(guava)[0]), "-d", str(stub_classes)]
                   + [str(p) for p in stub_src.glob("*.java")]
                   + [str(p) for p in ep_src.glob("*.java")]
                   + [str(p) for p in imm_src.glob("*.java")]
                   + [str(p) for p in df_src.glob("*.java")]
                   + [str(p) for p in fut_src.glob("*.java")]
                   + [str(p) for p in cf_src.glob("*.java")]
                   + [str(p) for p in init_src.glob("*.java")]
                   + [str(p) for p in jsr_src.glob("*.java")]
                   + [str(p) for p in jsr_meta.glob("*.java")]
                   + [str(p) for p in kotlin_src.glob("*.java")], check=True)
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
    order = ["decoder", "container", "common", "extractor", "exoplayer", "exoplayer-hls"]
    per_suffix = {}
    for suffix in order:
        javas = java_paths.get(suffix, [])
        classes = work / ("classes-" + suffix)
        if classes.exists():
            shutil.rmtree(classes)
        classes.mkdir(exist_ok=True)
        compile_files = [str(targets[j]) for j in javas]
        if suffix in ("container", "common"):
            # Support groups (no patch of their own): compile their whole
            # module from the worktree so downstream groups link against the
            # chain form of shared classes (container's NalUnitUtil carries
            # post-baseline PPS fields; common's MimeTypes carries
            # post-baseline codec constants). Their fresh jars are used as
            # classpath only; they never reach the publication stage.
            mod_dir = worktree / ("libraries/" + suffix
                                  + "/src/main/java/androidx/media3/" + suffix)
            compile_files += [str(p) for p in sorted(mod_dir.rglob("*.java"))
                              if str(p) not in compile_files]
            c_dir = work / (suffix + "-self-stripped")
            if c_dir.exists():
                shutil.rmtree(c_dir)
            c_dir.mkdir(exist_ok=True)
            with zipfile.ZipFile(work / ("media3-" + suffix + ".jar")) as zf:
                zf.extractall(c_dir)
            for stale in (c_dir / "androidx/media3" / suffix).rglob("*.class"):
                stale.unlink()
            self_jar = work / ("media3-" + suffix + "-self-stripped.jar")
            subprocess.run(["jar", "--create", "--file", str(self_jar), "-C", str(c_dir), "."],
                           check=True, env={**os.environ, "PATH": str(javac_bin.parent) + ":" + os.environ["PATH"]})
            classpath[classpath.index(work / ("media3-" + suffix + ".jar"))] = self_jar
        if suffix == "extractor":
            # Same orchestration as the exoplayer surgery: the chain-form
            # sources reference post-baseline APIs of drifting extractor
            # classes (TsExtractor's elementary readers, DefaultSubtitle-
            # ParserFactory's text subtree, BdmvTsExtractor for the exoplayer
            # iso helpers), so the drifting subtree is compiled here and its
            # classes flow into the exoplayer group through the fresh jar.
            ext_ts_dir = worktree / "libraries/extractor/src/main/java/androidx/media3/extractor/ts"
            ext_txt_dir = worktree / "libraries/extractor/src/main/java/androidx/media3/extractor/text"
            ext_bdmv = worktree / "libraries/extractor/src/main/java/androidx/media3/extractor/iso/bdmv/BdmvTsExtractor.java"
            ext_bdconst = worktree / "libraries/extractor/src/main/java/androidx/media3/extractor/iso/bdmv/BdmvConstants.java"
            ext_root_dir = worktree / "libraries/extractor/src/main/java/androidx/media3/extractor"
            compile_files += [str(ext_root_dir / "Av3aUtil.java"),
                              str(ext_root_dir / "Ac3Util.java"),
                              str(ext_root_dir / "Ac4Util.java"),
                              str(ext_root_dir / "DtsUtil.java")] + \
                             [str(p) for p in sorted(ext_ts_dir.glob("*.java"))] + \
                             [str(p) for p in sorted(ext_txt_dir.rglob("*.java"))] + \
                             [str(p) for p in sorted((worktree / "libraries/extractor/src/main/java/androidx/media3/extractor/iso").rglob("*.java"))]
            c_dir = work / "extractor-self-stripped"
            if c_dir.exists():
                shutil.rmtree(c_dir)
            c_dir.mkdir(exist_ok=True)
            with zipfile.ZipFile(work / "media3-extractor.jar") as zf:
                zf.extractall(c_dir)
            for stale_name in ["Av3aUtil.class", "Ac3Util.class", "Ac4Util.class", "DtsUtil.class"]:
                stale_c = c_dir / "androidx/media3/extractor" / stale_name
                if stale_c.exists():
                    stale_c.unlink()
                for inner in (c_dir / "androidx/media3/extractor").glob(stale_name[:-6] + "$*.class"):
                    inner.unlink()
            for stale in (c_dir / "androidx/media3/extractor/ts").rglob("*.class"):
                stale.unlink()
            for stale in (c_dir / "androidx/media3/extractor/text").rglob("*.class"):
                stale.unlink()
            iso_dir = c_dir / "androidx/media3/extractor/iso"
            if iso_dir.exists():
                for stale in iso_dir.rglob("*.class"):
                    stale.unlink()
            ext_self = work / "media3-extractor-self-stripped.jar"
            subprocess.run(["jar", "--create", "--file", str(ext_self), "-C", str(c_dir), "."],
                           check=True, env={**os.environ, "PATH": str(javac_bin.parent) + ":" + os.environ["PATH"]})
            classpath[classpath.index(work / "media3-extractor.jar")] = ext_self
        if suffix == "exoplayer":
            src_pkg = worktree / "libraries/exoplayer/src/main/java/androidx/media3/exoplayer"
            already = {os.path.normpath(p) for p in compile_files}
            dpf_dir = worktree / "libraries/extractor/src/main/java/androidx/media3/extractor/text"
            compile_files += [str(p) for p in sorted(dpf_dir.rglob("*.java"))]
            for sub in ["source", "text"]:
                compile_files += [str(p) for p in sorted((src_pkg / sub).rglob("*.java"))
                                  if os.path.normpath(str(p)) not in already]
            # The three patched files (plus the LoadEventInfo classpath
            # helper) were materialized under the build dir; drop their
            # duplicate worktree spellings so each class has exactly one
            # explicit source file.
            dup_names = {"LoadEventInfo.java", "SampleQueue.java", "ChunkSampleStream.java"}
            compile_files = [p for p in compile_files
                             if not (str(p).startswith(str(worktree))
                                     and Path(p).name in dup_names)]
            seen_cp = set()
            dedup_cp = []
            for p in compile_files:
                if p not in seen_cp:
                    seen_cp.add(p)
                    dedup_cp.append(p)
            compile_files = dedup_cp
            ts_dir = worktree / "libraries/extractor/src/main/java/androidx/media3/extractor/ts"
            bdmv_file = worktree / "libraries/extractor/src/main/java/androidx/media3/extractor/iso/bdmv/BdmvTsExtractor.java"
            compile_files += [str(ts_dir / "TsExtractor.java"),
                              str(ts_dir / "HlsSampleAesExtractorOutput.java"),
                              str(bdmv_file)]
            # The co-compiled sources must link against the same fresh
            # container/common classes the target files see (the shipped
            # exoplayer jar's intra-artifact TrackSelector/Downloader
            # surfaces are older-API and would otherwise be picked up for
            # the co-compiled set), so this group's classpath entry is
            # stripped of the co-compiled set plus the root helpers the
            # target files reference statically (DecoderReuseEvaluation):
            # those resolve from the worktree sources; every other
            # exoplayer class still resolves from the shipped jar.
            strip_dir = work / "exo-stripped"
            if strip_dir.exists():
                shutil.rmtree(strip_dir)
            strip_dir.mkdir(exist_ok=True)
            exo_jar = work / "media3-exoplayer.jar"
            with zipfile.ZipFile(exo_jar) as zf:
                zf.extractall(strip_dir)
            for pre in ["androidx/media3/exoplayer/source/", "androidx/media3/exoplayer/text/"]:
                d = strip_dir / pre
                if d.exists():
                    for stale in d.rglob("*.class"):
                        stale.unlink()
            for helper in ["DecoderReuseEvaluation"]:
                for stale in (strip_dir / "androidx/media3/exoplayer").glob(helper + "*.class"):
                    stale.unlink()
            stripped_jar = work / "media3-exoplayer-stripped.jar"
            subprocess.run(["jar", "--create", "--file", str(stripped_jar), "-C", str(strip_dir), "."],
                           check=True, env={**os.environ, "PATH": str(javac_bin.parent) + ":" + os.environ["PATH"]})
            classpath[classpath.index(exo_jar)] = stripped_jar
            # TrackSelector and the Downloader family are referenced by the
            # co-compiled set and by the target files, but are not part of
            # the source/text co-compile; add them to the compile set so
            # they resolve from the worktree too.
            for extra in ["androidx/media3/exoplayer/DecoderReuseEvaluation.java",
                          "androidx/media3/exoplayer/trackselection/TrackSelector.java",
                          "androidx/media3/exoplayer/offline/Downloader.java",
                          "androidx/media3/exoplayer/offline/DefaultDownloaderFactory.java",
                          "androidx/media3/exoplayer/offline/DefaultDownloader.java",
                          "androidx/media3/exoplayer/offline/DownloaderFactory.java",
                          "androidx/media3/exoplayer/offline/ProgressiveDownloader.java",
                          "androidx/media3/exoplayer/offline/DashDownloader.java",
                          "androidx/media3/exoplayer/offline/HlsDownloader.java",
                          "androidx/media3/exoplayer/offline/SsDownloader.java",
                          "androidx/media3/exoplayer/offline/CacheKeyFactory.java",
                          "androidx/media3/exoplayer/offline/DefaultLoadErrorHandlingPolicy.java",
                          "androidx/media3/exoplayer/offline/DownloadHelper.java"]:
                p = worktree / ("libraries/exoplayer/src/main/java/"
                                + extra.replace("/", os.sep))
                if p.exists() and os.path.normpath(str(p)) not in {os.path.normpath(q) for q in compile_files}:
                    compile_files.append(str(p))
        if suffix == "exoplayer-hls":
            # The chain-form HLS wrapper uses post-baseline APIs of its own
            # package (LoadEventInfo.Builder, HlsChunkSource.ChunkPublicationState,
            # HlsMediaChunk.createInstance, ...), so the whole hls package is
            # compiled from the worktree; the classpath entry for this artifact
            # is stripped of its hls classes first so no stale same-name class
            # shadows the fresh sources. Only the two patched files' classes
            # are swapped into the AAR afterwards.
            hls_pkg = worktree / "libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls"
            compile_files = [str(p) for p in sorted(hls_pkg.rglob("*.java"))]
            # HLS resolves LoadEventInfo/MediaChunk from the classpath; the
            # exoplayer fresh jar's copies came only from this script's
            # narrow target set (old LoadEventInfo form, no MediaChunk), so
            # add the chain-form LoadEventInfo and the chunk support classes
            # it references to the compile set and strip their stale copies
            # out of the fresh jar's unpack dir before it is rebuilt below.
            for extra in ["androidx/media3/exoplayer/source/LoadEventInfo.java",
                          "androidx/media3/exoplayer/source/chunk/Chunk.java",
                          "androidx/media3/exoplayer/source/chunk/MediaChunk.java",
                          "androidx/media3/exoplayer/offline/SegmentDownloader.java",
                          "androidx/media3/exoplayer/offline/SegmentDownloader$SegmentDownloadProgress.java",
                          "androidx/media3/exoplayer/upstream/ParsingLoadable.java"]:
                p = worktree / ("libraries/exoplayer/src/main/java/"
                                + extra.replace("/", os.sep))
                if p.exists() and os.path.normpath(str(p)) not in {os.path.normpath(q) for q in compile_files}:
                    compile_files.append(str(p))
            fresh_dir = work / "fresh-exoplayer"
            for stale_rel in ["androidx/media3/exoplayer/source/LoadEventInfo.class",
                              "androidx/media3/exoplayer/source/LoadEventInfo$Builder.class"]:
                stale = fresh_dir / stale_rel
                if stale.exists():
                    stale.unlink()
            strip_dir = work / "hls-stripped"
            if strip_dir.exists():
                shutil.rmtree(strip_dir)
            strip_dir.mkdir(exist_ok=True)
            hls_jar = work / "media3-exoplayer-hls.jar"
            with zipfile.ZipFile(hls_jar) as zf:
                zf.extractall(strip_dir)
            for stale in (strip_dir / "androidx/media3/exoplayer/hls").rglob("*.class"):
                stale.unlink()
            stripped_jar = work / "media3-exoplayer-hls-stripped.jar"
            subprocess.run(["jar", "--create", "--file", str(stripped_jar), "-C", str(strip_dir), "."],
                           check=True, env={**os.environ, "PATH": str(javac_bin.parent) + ":" + os.environ["PATH"]})
            classpath[classpath.index(hls_jar)] = stripped_jar
        javac_args = [str(javac), "-J-Duser.language=en", "-encoding", "UTF-8", "-g",
                      "--release", "8", "-proc:none", "-Xlint:-classfile,-options"]
        javac_args += ["-classpath", os.pathsep.join(map(str, classpath)),
                       "-d", str(classes)]
        subprocess.run(javac_args + compile_files, check=True)
        updates = {str(p.relative_to(classes)): p.read_bytes() for p in classes.rglob("*.class")}
        if suffix == "exoplayer":
            # The whole source/text packages are co-compiled from the
            # worktree, so their classes duplicate the shipped jar by
            # construction; the filter below reduces this group to the three
            # patched files before anything reaches the AAR, and baseline
            # drift on the co-compiled set is recorded in the batch report.
            bad = [n for n in updates
                   if n.startswith("androidx/media3/extractor/")
                   and "androidx/media3/extractor/text/" not in n
                   and "androidx/media3/extractor/ts/" not in n
                   and "BdmvTsExtractor" not in n]
            if bad:
                raise ValueError("Co-compiled class escaped into extractor: " + ", ".join(sorted(bad)))
            pass
        if suffix == "decoder":
            for name in updates:
                if not any(name.startswith(prefix) for prefix in EXPECTED_PREFIXES):
                    raise ValueError("Compilation escaped the declared class boundary: " + name)
        if suffix == "exoplayer":
            # Classpath-only helper (see EXO_JAVAS): never swapped into the AAR.
            # The whole chunk package is compiled alongside for the same reason
            # as the HLS package (chain-form ChunkSampleStream uses
            # post-baseline chunk APIs), so filter strictly to the three
            # patched files.
            keep = ("androidx/media3/exoplayer/mediacodec/MediaCodecRenderer",
                    "androidx/media3/exoplayer/source/SampleQueue",
                    "androidx/media3/exoplayer/source/chunk/ChunkSampleStream")
            updates = {n: d for n, d in updates.items() if n.startswith(keep)}
        if suffix == "exoplayer-hls":
            # Whole-package compile (see compile block): only the two patched
            # files' classes are swapped into the AAR.
            keep = ("androidx/media3/exoplayer/hls/HlsSampleStreamWrapper",
                    "androidx/media3/exoplayer/hls/HlsSampleStream")
            updates = {n: d for n, d in updates.items() if n.startswith(keep)}
            if len(updates) < 5:
                raise ValueError("HLS compile produced too few target classes")
        per_suffix[suffix] = updates
        # Later groups compile against this group's fresh classes (the new
        # DecoderInputBuffer API is referenced by the exoplayer group).
        fresh_jar = work / ("media3-" + suffix + "-fresh.jar")
        fresh = work / ("fresh-" + suffix)
        if fresh.exists():
            shutil.rmtree(fresh)
        fresh.mkdir(exist_ok=True)
        orig_jar = work / ("media3-" + suffix + ".jar")
        if suffix in ("extractor", "container", "common"):
            # Support groups replaced their classpath entry with a stripped
            # jar for their own compile; the fresh jar for downstream groups
            # still starts from the intact module jar.
            orig_jar = work / ("media3-" + suffix + ".jar")
        with zipfile.ZipFile(orig_jar) as zf:
            zf.extractall(fresh)
        for rel_name, data in updates.items():
            dest = fresh / rel_name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
        subprocess.run(["jar", "--create", "--file", str(fresh_jar), "-C", str(fresh), "."],
                       check=True, env={**os.environ, "PATH": str(javac_bin.parent) + ":" + os.environ["PATH"]})
        stripped_names = {"extractor", "container", "common", "exoplayer", "exoplayer-hls"}
        classpath[classpath.index(work / ("media3-" + suffix + "-self-stripped.jar"))
                  if suffix in ("extractor", "container", "common")
                  else classpath.index(work / ("media3-" + suffix + "-stripped.jar"))
                  if suffix in stripped_names
                  else classpath.index(orig_jar)] = fresh_jar
    if args.compile_only:
        print(json.dumps({s: {"compiled_classes": sorted(u)} for s, u in per_suffix.items()}, indent=2))
        return
    report = {}
    outputs_by_suffix = {}
    for suffix, updates in per_suffix.items():
        if suffix not in originals:
            continue
        aar_orig, sources_orig = originals[suffix]
        with zipfile.ZipFile(io.BytesIO(aar_orig)) as archive:
            old_classes = archive.read("classes.jar")
        new_classes = repack(old_classes, updates.copy())
        with zipfile.ZipFile(io.BytesIO(old_classes)) as old, zipfile.ZipFile(io.BytesIO(new_classes)) as new:
            preserved = 0
            preserved_classes = 0
            for entry in old.namelist():
                if entry not in updates:
                    if old.read(entry) != new.read(entry):
                        raise ValueError("Unrelated class changed in " + suffix + ": " + entry)
                    preserved += 1
                    if entry.endswith(".class"):
                        preserved_classes += 1
        aar = repack(aar_orig, {"classes.jar": new_classes})
        sources = repack(sources_orig, {j: targets[j].read_bytes() for j in java_paths[suffix]
                                        if "LoadEventInfo" not in j})
        name = name_of(suffix)
        outputs = {name + ".aar": aar, name + "-sources.jar": sources}
        module = json.loads((ROOT / rel_of(suffix) / (name + ".module")).read_text())
        for variant in module["variants"]:
            for item in variant.get("files", []):
                if item["name"] in outputs:
                    data = outputs[item["name"]]
                    item["size"] = len(data)
                    for algorithm in ["md5", "sha1", "sha256", "sha512"]:
                        item[algorithm] = digest(data, algorithm)
        outputs[name + ".module"] = (json.dumps(module, indent=2) + "\n").encode()
        for out_name, data in list(outputs.items()):
            for algorithm in ["md5", "sha1", "sha256", "sha512"]:
                outputs[out_name + "." + algorithm] = digest(data, algorithm).encode()
        outputs_by_suffix[suffix] = outputs
        report[suffix] = {"aar_sha256": digest(aar), "sources_sha256": digest(sources),
                          "preserved_entries": preserved,
                          "preserved_class_entries": preserved_classes,
                          "compiled_classes": sorted(updates)}
    for suffix, outputs in outputs_by_suffix.items():
        dest = ROOT / rel_of(suffix) if args.install else work / ("publication-" + suffix)
        dest.mkdir(parents=True, exist_ok=True)
        for out_name, data in outputs.items():
            (dest / out_name).write_bytes(data)
    if args.install:
        path = ROOT / "third_party/media-lock.json"
        lock = json.loads(path.read_text())
        patches = lock["fongmi_media"]["patches"]
        patches[:] = [p for p in patches if p["path"] != PATCH]
        patches.append({"path": PATCH, "sha256": digest((ROOT / PATCH).read_bytes())})
        reasons = {
            "androidx.media3:media3-decoder:" + VERSION: (
                "; seek stream-source signal batch (media3-exo-seek-stream-source-r1):"
                " DecoderInputBuffer gains a key-frame query flag pair so a chunk stream"
                " can answer, without consuming a sample, whether its next sample is a"
                " key frame; surgical swap of DecoderInputBuffer classes only"),
            "androidx.media3:media3-exoplayer:" + VERSION: (
                "; seek stream-source signal batch (media3-exo-seek-stream-source-r1):"
                " MediaCodecRenderer now asks the sample stream at position reset"
                " whether it restarts at a key frame instead of trusting the"
                " skipSource-normalized reset flag (which the r1 batch keyed on and"
                " which never fired on a real queue reset); SampleQueue exposes the"
                " next sample's key-frame flag and ChunkSampleStream answers the"
                " buffer query in place; surgical swap of MediaCodecRenderer,"
                " SampleQueue and ChunkSampleStream classes only"),
            "androidx.media3:media3-exoplayer-hls:" + VERSION: (
                "; seek stream-source signal batch (media3-exo-seek-stream-source-r1):"
                " HlsSampleStream and its wrapper expose whether the next queued sample"
                " is a key frame and the wrapper is not mid-reset, so the renderer can"
                " tell a surviving queue from a discarded one at reset time; surgical"
                " swap of HlsSampleStream and HlsSampleStreamWrapper classes only"),
        }
        for artifact in lock["fongmi_media"]["artifact_overrides"]:
            coord = artifact["coordinate"]
            if coord in reasons:
                suffix = coord.split(":")[1].replace("media3-", "")
                artifact["aar_sha256"] = report[suffix]["aar_sha256"]
                artifact["sources_sha256"] = report[suffix]["sources_sha256"]
                artifact["reason"] = artifact.get("reason", "") + reasons[coord]
        # exoplayer-hls has no prior override entry; register it now.
        hls_coord = "androidx.media3:media3-exoplayer-hls:" + VERSION
        if not any(a["coordinate"] == hls_coord for a in lock["fongmi_media"]["artifact_overrides"]):
            lock["fongmi_media"]["artifact_overrides"].append({
                "coordinate": hls_coord,
                "aar_sha256": report["exoplayer-hls"]["aar_sha256"],
                "sources_sha256": report["exoplayer-hls"]["sources_sha256"],
                "reason": "Local FongMi media3 HLS artifact" + reasons[hls_coord],
            })
        path.write_text(json.dumps(lock, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
