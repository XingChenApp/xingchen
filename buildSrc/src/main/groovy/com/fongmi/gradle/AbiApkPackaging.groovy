package com.fongmi.gradle

// 运行时打包常量：PrepareRuntimeTask（上游原文照搬）的静态依赖。
// 本文件不是上游 e5ec 原文：上游对象库已不在 /tmp（见交回卡点），
// e5ec 的 AbiApkPackaging.groovy 无法逐字节取回；这里的值全部取自树内
// 已照搬的上游原文，不新编：
//  - THUNDER_LIBRARIES / PYTHON_LIBRARIES / MPV_LIBRARIES：
//    catvod RuntimeModule.libraries() 各模块顺序（thunder：libxl_stat 在前）；
//  - RUNTIME_PROFILES 各字段：RuntimeModule 枚举（library）、getPackageName()
//    （com.fongmi.runtime.<id>）、getCompatibilityId()、getEntryClass()，
//    与 scripts/runtime_catalog.py 的模块对（兼容 id、主库名、JAVA_ENTRIES）逐项对过；
//  - ABI_FLAVORS：PrepareRuntimeTask 只用 containsValue(abi)，
//    这里给值就是两个 ABI 名；键名只作区分，不参与打包逻辑。
// thunder 路只碰 THUNDER_LIBRARIES / RUNTIME_PROFILES.thunder / ABI_FLAVORS，
// 不碰 MpvNativeLibraries 与 PreparePythonAarTask（mpv / python 分支才调，
// 那个分支的辅助类不在本段范围，thunder 打包不受影响）。
class AbiApkPackaging {

    static final Map<String, String> ABI_FLAVORS = [
            arm64_v8a  : 'arm64-v8a',
            armeabi_v7a: 'armeabi-v7a',
    ]

    static final List<String> PYTHON_LIBRARIES = [
            'libcrypto_python.so', 'libssl_python.so', 'libsqlite3_python.so',
            'libcrypto_chaquopy.so', 'libssl_chaquopy.so', 'libsqlite3_chaquopy.so',
            'libpython3.10.so', 'libpython3.so', 'libchaquopy_java.so',
    ]

    static final List<String> THUNDER_LIBRARIES = [
            'libxl_stat.so', 'libxl_thunder_sdk.so',
    ]

    static final List<String> MPV_LIBRARIES = [
            'libmpv.so', 'libplayer.so',
    ]

    static final Map<String, Map<String, Object>> RUNTIME_PROFILES = [
            python : [packageName: 'com.fongmi.runtime.python', compatibilityId: 'catvod-python-plugin-v2', library: 'libchaquopy_java.so', entryClass: 'com.fongmi.chaquo.RuntimeEntry'],
            quickjs: [packageName: 'com.fongmi.runtime.quickjs', compatibilityId: 'fongmi-quickjs-ng-0.17-v11', library: 'libquickjs-wrapper.so', entryClass: 'com.fongmi.quickjs.RuntimeEntry'],
            thunder: [packageName: 'com.fongmi.runtime.thunder', compatibilityId: 'catvod-thunder-plugin-v1', library: 'libxl_thunder_sdk.so', entryClass: 'com.xunlei.downloadlib.RuntimeEntry'],
            mpv    : [packageName: 'com.fongmi.runtime.mpv', compatibilityId: 'mpv-jni-ffmpeg-shared-v1', library: 'libplayer.so', entryClass: null],
            bdj    : [packageName: 'com.fongmi.runtime.bdj', compatibilityId: 'libbluray-1.4.1-openjdk8-zero-v1', library: '', entryClass: null, archive: 'assets/bdj/runtime.zip'],
    ]
}
