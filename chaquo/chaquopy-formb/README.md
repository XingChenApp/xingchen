# Chaquopy 形态乙：java Cython 包自维护（F1/F2）

## 这是什么

Chaquopy 桥崩（`PyProxy._chaquopyGetDict is abstract` / `DynamicProxy._chaquopyGetType
is abstract`）的根在桥的 `java` Cython 包（`class.pxi` / `proxy.pxi` 编成的
`java/chaquopy.so`，APK 里位于 `assets/chaquopy/bootstrap-native/<abi>/java/chaquopy.so`）。

形态乙（用户 2026-11-19 亲点）：桥的其余部分——Gradle 插件、运行时 Java 类、
`libchaquopy_java.so`、CPython 本体、bootstrap 资产——继续用官方 Chaquopy **17.0.0**
成品逐字不动；只有这一个 Cython 包拿出来咱家维护，在包内落两处结构修正：

- **F1（解析语义）** `class.pxi` `reflect_member`：代理类成员解析原有一条整包回退——
  代理类的任何成员都只取继承来的声明，于是接口的抽象声明（如
  `PyProxy._chaquopyGetDict`）能被粘进具体代理类的类字典并永久生效，之后每次分发还原
  都撞抽象成员报错。修正：桥自身的协议名（`<init>`、`_chaquopyGetDict`、
  `_chaquopySetDict`、`_chaquopyGetType`，即 `ProxyClass.add_members` 从类自身装入的
  同一组名字）改走与一切类相同的「自身声明优先、继承垫底」解析；其余名字维持原回退
  （JDK 代理类把每个接口方法都「声明」成转发桩，按类自身解析未实现方法会绕回调用
  处理器造成无限递归——原注释的用意只对普通方法成立）。
- **F2（建类原子性）** `proxy.pxi` `ProxyClass.__new__`：建类全程（取 klass、填
  cls_dict、`JavaClass.__new__` 登记进 `jclass_cache`、`add_members` 装员）持
  `class_lock`，与 `jclass()` 等读路同锁——半成品代理类不再能被别的线程从缓存里
  解析出来先行使用。

完整 diff 见 `formb-17.0.0.patch`（路径相对包目录）；`src/java/` 是修正后的包源码
全本（自官方 17.0.0 的 `product/runtime/src/main/python/java/`），`src/chaquopy_java.pyx`
与运行库 `CMakeLists.txt` 一并附入供重编对照（两者未改），目录相对关系与官方树一致。

## 基线等价（先证未改版与官方成品等价，再叠 F1/F2）

按官方 17.0.0 源码（tag `17.0.0`）原样重编未改版，在工件层与官方 Maven 成品
`com.chaquo.python.runtime:chaquopy:17.0.0:3.10-arm64-v8a@so` 对照：

- SONAME 相同（`chaquopy-3.10.so`）；NEEDED 相同（libpython3.10.so、libdl.so、
  libandroid.so、libm.so、libc.so）；
- 定义动态符号集合相同（仅 `PyInit_chaquopy` 与模块标记）；未定义动态符号集合
  相同（235 个）；
- 行为层：官方自带 `chaquopy.test` 十二个模块共 135 项在桌面回放台（同源运行时
  Java 类 + JDK21 + CPython 3.10）上全过；崩样回放（见下）在未改版上确定性复现。
- 未达逐字相同：官方成品的 .comment 是 clang 18.0.4（Android build 13691557）；
  本机可得的 NDK 里 r27 是 clang 18.0.1、r27c 是 18.0.3、r29 是 clang 21，都不是
  那个点版本。用 r27（同为 r522817 系）＋平台 24（官方 MIN_SDK）重编：arm64
  763,608 B 对官方 763,640 B（差 −32 B）、armeabi-v7a 547,068 B 对 547,084 B
  （差 −16 B），符号面逐项全等；字节内容因代码生成与布局位移大面积不同。
  结论：ABI/符号/行为三层等价，字节层不等价——差异只在编译器点版本。

## 崩样回放（桌面，同源同块）

回放台：官方 17.0.0 运行时 Java 类全量（105 类，JDK21 以 `--release 8` 编）+
CPython 3.10 + 被测 `chaquopy.so`。两种崩样终态（类字典里协议成员被删后经第二类
触发重解析，再走 j2p 还原）与 tgdrama 型压力模式（函数体内现定义 dynamic_proxy
类 + 定时执行器延迟派发 + 并行还原线程，400 轮）：

- 未改版：变体 1/2 报错原文与真机三次崩溃逐字相同
  （`...PyProxy._chaquopyGetDict is abstract and cannot be called` /
  `...DynamicProxy._chaquopyGetType is abstract and cannot be called`）；压力模式
  400 轮中 18 次撞抽象成员错、仅 246 个回调跑完。
- 修正版：两变体还原成功，重解析装回的是类自身的具体成员；压力模式 400/400
  回调跑完、抽象成员错 0 次；官方测试集 135 项结果与未改版逐项相同（全过）。

注：F1 初版曾按「整包回退全删」落码，官方测试集的未实现方法三项
（test_unimplemented / test_use_before_init / test_object_methods_unimplemented）
以 RecursionError 失败——证明整包回退对普通方法不可删，故 F1 收窄为只对桥协议名
改解析（即上文定版）。

## 换装（构建步骤）

`chaquo/build.gradle` 末尾的 configureEach 钩子：

1. 每个变体的 `generate<Variant>PythonMiscAssets` 任务（官方插件把 Maven 成品
   `chaquopy.so` 拷进 assets 的那一步）执行后，把其输出目录里
   `bootstrap-native/<abi>/java/chaquopy.so` 覆盖为本目录 `prebuilt/<abi>/chaquopy.so`；
   覆盖发生在随后的 `PythonBuildAssets` 计算 build.json 资产哈希之前，哈希与实物
   一致。`prebuilt/` 文件登记为该任务输入，换件即触发重拷。
2. `generate<Variant>PythonBuildAssets` 执行后校验：misc 输出里的 `chaquopy.so`
   SHA-256 必须等于 `expected-sha256.txt` 中对应 ABI 的值，对不上构建直接失败。

除这一个文件（每 ABI 一份）外，成品里桥的一切字节与官方 17.0.0 相同；插件版本
pin（gradle/libs.versions.toml `python = "17.0.0"`）不动。任务名与输出结构按
17.0.0 插件源码（PythonTasks.kt 的 registerTask/registerAssetTask）核对；本机
Gradle 守护进程在本 VM 起不来（dispatch 失败），钩子未在本机真跑，首次实跑是
下次 CI 编包——哈希校验保证钩子没生效时构建直接失败，不会静默装回官方件。

## 重编 prebuilt

`./build-artifact.sh`（在本目录）：需要官方 17.0.0 源码克隆、Cython 3.0.11
（`product/runtime/requirements-build.txt` 的 pin）、Android NDK、官方 CPython
target 工件（脚本从 Maven Central 拉 `com.chaquo.python:target:3.10.19-0` 两 ABI）。
流程与官方 build.gradle 的 cythonTask 一致：cython `-Wextra -Werror` 生成 C 后按
官方两条正则后处理，再用运行库自带 CMakeLists（Release）编。产出后脚本自动做
readelf 符号面校验（SONAME/NEEDED/定义符号须与期望集合全等）并刷新
`expected-sha256.txt`。

## 待验

成品层（编包后真机 tgdrama 点设置卡片不再崩、其余 py 消费面行为不变）等下次
编包验证；本批只验到工件层与桌面回放层。
