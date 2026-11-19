#!/bin/bash
# Rebuild prebuilt/<abi>/chaquopy.so from src/ (the vendored java Cython package
# with F1/F2 applied), following Chaquopy 17.0.0's own build recipe.
#
# Required environment:
#   CHAQUOPY_SRC   pristine clone of https://github.com/chaquo/chaquopy at tag 17.0.0
#                  (only its product/runtime/CMakeLists.txt and build rules are used;
#                  the java package itself comes from this directory's src/)
#   ANDROID_NDK    Android NDK root (official 17.0.0 used r27 / clang 18.0.4;
#                  r29 / clang 21 also works, with codegen-level size differences)
#   CYTHON         cython executable, must be 3.0.11 (17.0.0 requirements-build.txt pin)
#   CMAKE, NINJA   cmake >= 3.22 and ninja
# Official CPython target artifacts (com.chaquo.python:target:3.10.19-0) are
# downloaded from Maven Central into ./.work/ on first run.
set -e
cd "$(dirname "$0")"
: "${CHAQUOPY_SRC:?set CHAQUOPY_SRC to a pristine chaquopy 17.0.0 clone}"
: "${ANDROID_NDK:?set ANDROID_NDK to the NDK root}"
: "${CYTHON:?set CYTHON to a Cython 3.0.11 executable}"
CMAKE=${CMAKE:-cmake}
NINJA=${NINJA:-ninja}
WORK=.work
mkdir -p $WORK
export TMPDIR=${TMPDIR:-$PWD/$WORK/tmp}
mkdir -p $TMPDIR

# --- official CPython target prefixes (headers + libpython) -------------------
for abi in arm64-v8a armeabi-v7a; do
    if [ ! -d $WORK/target-$abi ]; then
        curl -s -o $WORK/target-$abi.zip \
            "https://repo1.maven.org/maven2/com/chaquo/python/target/3.10.19-0/target-3.10.19-0-$abi.zip"
        mkdir -p $WORK/target-$abi
        (cd $WORK/target-$abi && unzip -q ../target-$abi.zip)
    fi
done

# --- Cython generation (mirrors the official cythonTask in
#     product/runtime/build.gradle, including its two post-processing rules) ---
GEN=$WORK/cython
mkdir -p $GEN
# Same layout as the official tree: the package in src/java, the shim pyx in src/.
(cd src/java && "$CYTHON" -Wextra -Werror chaquopy.pyx -I "$OLDPWD/$GEN" -o "$OLDPWD/$GEN/chaquopy.c")
(cd src && "$CYTHON" -Wextra -Werror chaquopy_java.pyx -I "$OLDPWD/$GEN" -o "$OLDPWD/$GEN/chaquopy_java.c")
python3 - "$GEN/chaquopy.c" "$GEN/chaquopy_java.c" <<'EOF'
import re, sys
for path in sys.argv[1:]:
    lines = open(path).read().split('\n')
    out = []
    for line in lines:
        line = re.sub(r'^__PYX_EXTERN_C (\w+)',
                      r'/* cythonTask altered */ __PYX_EXTERN_C JNIEXPORT \1 JNICALL', line)
        line = re.sub(r'__pyx_insert_code_object\(c_line',
                      lambda m: '// cythonTask disabled: ' + m.group(0), line)
        out.append(line)
    open(path, 'w').write('\n'.join(out))
EOF

# --- CMake build per ABI (the runtime's own CMakeLists, Release) --------------
for abi in arm64-v8a armeabi-v7a; do
    OUT=$WORK/build-$abi
    mkdir -p $OUT
    (cd $OUT && $CMAKE -GNinja -DCMAKE_MAKE_PROGRAM=$NINJA \
        -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
        -DANDROID_ABI=$abi \
        -DANDROID_NATIVE_API_LEVEL=24 \
        -DCMAKE_BUILD_TYPE=Release \
        -DCHAQUOPY_SRC_DIR=$OLDPWD/$GEN \
        -DCHAQUOPY_PYTHON_VERSIONS=3.10 \
        -DCHAQUOPY_LIB_DIRS=$OLDPWD/$WORK/target-$abi/jniLibs/$abi \
        -DCHAQUOPY_INCLUDE_PYTHON=$OLDPWD/$WORK/target-$abi/include \
        -DCHAQUOPY_PY_SUFFIX=.so \
        $CHAQUOPY_SRC/product/runtime > cmake.log 2>&1 \
     && $CMAKE --build . --parallel 4 > build.log 2>&1)
    cp $OUT/chaquopy-3.10.so prebuilt/$abi/chaquopy.so
    echo "built prebuilt/$abi/chaquopy.so"
done

# --- symbol-surface verification ----------------------------------------------
READELF=$(ls $ANDROID_NDK/toolchains/llvm/prebuilt/*/bin/llvm-readelf)
fail=0
for abi in arm64-v8a armeabi-v7a; do
    f=prebuilt/$abi/chaquopy.so
    $READELF -d $f | grep -q "SONAME.*chaquopy-3.10.so" || { echo "$abi: SONAME mismatch"; fail=1; }
    for lib in libpython3.10.so libdl.so libandroid.so libm.so libc.so; do
        $READELF -d $f | grep -q "NEEDED.*$lib" || { echo "$abi: NEEDED $lib missing"; fail=1; }
    done
    defs=$($READELF --dyn-syms $f | awk '$7!="UND" && ($4=="FUNC"||$4=="OBJECT") {print $8}' | sort)
    [ "$defs" = "$(printf 'PyInit_chaquopy\n__pyx_module_is_main_java__chaquopy')" ] \
        || { echo "$abi: defined symbol set mismatch: $defs"; fail=1; }
done
[ $fail -eq 0 ] || { echo "symbol verification FAILED"; exit 1; }

# --- refresh expected hashes ----------------------------------------------------
(cd prebuilt && sha256sum arm64-v8a/chaquopy.so armeabi-v7a/chaquopy.so) > expected-sha256.txt
cat expected-sha256.txt
echo "OK"
