# Gson TypeToken 泛型签名保留（1.3.52／1.4.8 LinkedTreeMap 崩同源）：匿名 TypeToken 子类的
# 泛型参数只存在于 Signature 属性里，R8 剥掉它则 TypeToken 退化为裸类型读回；
# 显式 TypeToken 定型（榜单／歌单）只有签保留才生效，故两混淆文件都要补齐。
-keepattributes Signature
# TV
-keep class androidx.leanback.widget.** { *; }
-keep class com.fongmi.quickjs.method.** { *; }
# PY 蜘蛛以完整类名反射调 WebActivity 的静态面（open/openWeb/getUserAgent），类名方法名不许改
-keep class com.fongmi.android.tv.ui.activity.WebActivity { *; }
-keep class com.fongmi.android.tv.bean.** { *; }
# 海阔详情短时缓存落盘结构（2026-10-17 崩溃修法保险丝：R8 曾把 Entry 的 List<HkItem>
# 字段泛型签名剥掉、读回全变 LinkedTreeMap 直接崩；现在 Entry 只存字符串无泛型可剥，
# 这里再加一道保险，防 R8 对缓存结构动手脚）
-keep class com.fongmi.android.tv.api.hk.HkDetailCache$Entry { *; }
# 歌词/播放列表落盘结构（2026-10-24 批 7 R8 审计）：不在 bean 包、无 keep 时
# R8 改字段名/裁未读字段，而读写两侧一侧反射字段名、一侧字面键，不对称即静默丢数据
# （AudioPlaylistStore 已在 1.4.26 成品 dex 实证打坏：字段改名 a/b/c、同版往返即丢）。
-keep class com.fongmi.android.tv.player.lyrics.AudioPlaylistStore$* { *; }
-keep class com.fongmi.android.tv.player.lyrics.LyricsResult { *; }
# AI 实时字幕插件桥（2026-10-27 真机定罪）：插件 dex 里的 SherpaBridge 按全名实现宿主的
# IAiAsrBridge，AiAsrPluginManager 靠 instanceof 认亲。无此 keep 时 R8 把接口改名，
# 插件只能用自带的那份接口副本，两份接口对不上 → 「插件类型不匹配」，下载再对也加载不了。
-keep interface com.fongmi.android.tv.ai.asr.IAiAsrBridge { *; }

# MPV JNI bridge
-keep class is.xyz.mpv.MPVLib { *; }
-keep class is.xyz.mpv.MPVLib$* { *; }

# libplayer.so resolves this class and these methods by their literal JNI names.
-keep class com.fongmi.android.tv.player.iso.IsoSessionManager {
    public static long length(long);
    public static int readAt(long, long, java.nio.ByteBuffer, int);
    public static void close(long);
    public static void prepareTrackMetadata(long, int);
}

# MPV owns one process-wide native context. Keep its lifecycle code intact so
# release inlining does not amplify timing-sensitive create/destroy transitions.
-keep,allowobfuscation class androidx.media3.mpvplayer.MpvPlayer { *; }
-keep,allowobfuscation class androidx.media3.mpvplayer.MpvPlayer$* { *; }
-keep,allowobfuscation class com.fongmi.android.tv.player.engine.MpvPlayerEngine { *; }

# Gson
-keep class com.google.gson.** { *; }
-keep class com.fongmi.android.tv.remote.** { *; }
-keep class com.fongmi.android.tv.gitcloud.** { *; }
-keep class com.fongmi.android.tv.legado.** { *; }

# SimpleXML
-keep interface org.simpleframework.xml.core.Label { public *; }
-keep class * implements org.simpleframework.xml.core.Label { public *; }
-keep interface org.simpleframework.xml.core.Parameter { public *; }
-keep class * implements org.simpleframework.xml.core.Parameter { public *; }
-keep interface org.simpleframework.xml.core.Extractor { public *; }
-keep class * implements org.simpleframework.xml.core.Extractor { public *; }
-keepclassmembers,allowobfuscation class * { @org.simpleframework.xml.Path <fields>; }
-keepclassmembers,allowobfuscation class * { @org.simpleframework.xml.Root <fields>; }
-keepclassmembers,allowobfuscation class * { @org.simpleframework.xml.Text <fields>; }
-keepclassmembers,allowobfuscation class * { @org.simpleframework.xml.Element <fields>; }
-keepclassmembers,allowobfuscation class * { @org.simpleframework.xml.Attribute <fields>; }
-keepclassmembers,allowobfuscation class * { @org.simpleframework.xml.ElementList <fields>; }

# OkHttp
-dontwarn okhttp3.**
-keep class okio.** { *; }
-keep class okhttp3.** { *; }

# Kotlin
-keeppackagenames kotlin.**
-keep class kotlin.** { *; }

# JGit
-dontwarn java.lang.ProcessHandle
-dontwarn java.lang.management.ManagementFactory
-dontwarn org.eclipse.jgit.**
-keep class org.eclipse.jgit.** { *; }
-keeppackagenames org.slf4j.**
-keep class org.slf4j.** { *; }

# CatVod
# 宿主面全包保名（2026-10-23 jar 全灭实锤补）：jar 里的爬虫（含加密载荷）在
# 编译期按 com.github.catvod.** 标准宿主面链接，成品里任一类被 R8 改名或裁掉，
# 载荷一执行到引用点就 NoClassDefFoundError、整 jar 失效。1.4.16 成品 dex 实测：
# 只保了 Proxy/crawler/net，utils 整包（Util/Json/Path/Prefers/Auth/Shell 等 12 类）
# ＋顶层 Init/Runtime＋bean 3 类＋runtime 2 类共 19 个类原名消失。此行与 FongMi
# catvod/consumer-rules.pro 的 `-keep class com.github.catvod.** { *; }` 同口径，
# 全包保原名与成员；下面三条旧规则已被此行覆盖，保留不动。
-keep class com.github.catvod.** { *; }
-keep class com.github.catvod.Proxy { *; }
-keep class com.github.catvod.crawler.** { *; }
-keep class com.github.catvod.net.** { *; }
-keep class * extends com.github.catvod.crawler.Spider

# jar 按名引用的宿主配置门面（2026-10-23 全量对差 appclass-keep-sweep 补）：
# vox.jar 的 merge 框架运行时按类名构造 com.fongmi.android.tv.api.config.VodConfig
# （类名在 jar 内为加密字符串池，本地仓拆解 jadx 解密实证；本批字节直查为密文形态）。
# 1.4.16 成品类清单实测：api.config 整包 5 类（BaseConfig/LiveConfig/RuleConfig/
# VodConfig/WallConfig）原名全缺、同包同命运，故按小包粒度整包保原名与成员。
-keep class com.fongmi.android.tv.api.config.** { *; }

# QuickJS 桥面全包保名（同批对差补）：FongMi 由 quickjs/consumer-rules.pro
# （com.fongmi.quickjs.*）与 runtime/proguard-rules.pro（com.fongmi.quickjs.**）
# 两处保名；星辰此前只有 method 子包一条，1.4.16 成品实测 bean 4 类＋utils 6 类
# ＋crawler/Loader 共 11 类原名缺失。按三家并集口径全包补齐（与本文件第 7 行
# 既有的 method.** 规则重叠无副作用）。
-keep class com.fongmi.quickjs.** { *; }

# Jianpian
-keep class com.p2p.** { *; }

# JUPnP
-dontwarn org.jupnp.**
-keep class org.jupnp.** { *; }
-keep class javax.xml.** { *; }

# Nano
-keep class fi.iki.elonen.** { *; }

# NewPipeExtractor
-keep class javax.script.** { *; }
-keep class jdk.dynalink.** { *; }
-keep class org.mozilla.javascript.* { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.schabi.newpipe.extractor.services.youtube.protos.** { *; }
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn com.google.re2j.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# Sardine
-keep class com.thegrizzlylabs.sardineandroid.** { *; }

# TVBus
-keep class com.tvbus.engine.** { *; }

# XunLei
-keep class com.xunlei.downloadlib.** { *; }

# Zxing
-keep class com.google.zxing.** { *; }

# 海阔引擎 JS 桥：HkJsRuntime 靠反射扫描 @JSAnnotation 方法生成同名 JS 函数
# （getAllFunctions），方法名即规则调用的 API 名；R8 改名后真机上 getUaObject
# 等全部 ReferenceError（本地未混淆测不出）。整类保名，含内部注解类型。
-keep class com.fongmi.android.tv.api.hk.HkJsRuntime { *; }
-keep class com.fongmi.android.tv.api.hk.HkJsRuntime$* { *; }

# JavaAdapter 抽象类桥（2026-11-13）：JS 侧经反射按名调桥的覆写方法与
# super$ 化身（HkJsAdapterBridges），改名即断，整类保名。
-keep class com.fongmi.android.tv.api.hk.HkJsAdapterBridges { *; }
-keep class com.fongmi.android.tv.api.hk.HkJsAdapterBridges$* { *; }

# 图片解密桥：crypto-java.js 按名调 FileUtil.toBytes（即本类），R8 改名后真机
# TypeError 找不到函数、封面空流（1.3.7 真机日志实锤）。整类保名。
-keep class com.fongmi.android.tv.api.hk.HkCryptoFileUtil { *; }

# 8.83 宿主类同包名补齐：规则 JS 在任意作用域按全名直调
# com.example.hikerview.utils.FileUtil.*（列表期解密封面等），无 Java 侧引用，
# R8 不保名会把类/方法改名或整类裁掉、类路径解析当场落空。整类保名。
-keep class com.example.hikerview.utils.FileUtil { *; }

# 聚阅插件本体类整批（2026-11-13，账 juyue-hikerpop-gap）：hikerPop.js 按
# 全名直调 com.example.hikerview.** 的弹窗/工具/模型/枚举，无 Java 侧静态
# 引用，R8 不保名 JS 侧类路径解析落空（与上 FileUtil 同病）。整域保名。
-keep class com.example.hikerview.** { *; }
-keep class com.annimon.stream.function.Consumer { *; }

# LitePal（org.litepal core 2.0.0，官包内嵌同库）：官方 ProGuard 规矩
# （LitePal 官方 README「ProGuard Configuration」原文）。其 AAR 不带
# consumer 规则，不在此补，R8 会把库类改名/裁剪——而 LitePal 把自家记账类
# org.litepal.model.Table_Schema 以字符串字面量塞进建表名单（LitePalAttr/
# LitePalConfig/LitePalDB 三处 ldc 同名），再由 LitePalBase 逐名
# Class.forName 建表，改名即抛「can not find a class named ...Table_Schema」
# （1.4.132 真机进海阔崩实锤；132 审计只验了 litepal.xml 六个用户模型类、
# 漏了库自家按名查找的这一名）。模型行覆盖 LitePalSupport/DataSupport 两代。
-keep class org.litepal.** { *; }
-keep class * extends org.litepal.crud.LitePalSupport { *; }
-keep class * extends org.litepal.crud.DataSupport { *; }

# 按名直调库整包保名（2026-11-14，账 byname-lib-keep-20261114）：海阔规则
# JS 经 Rhino Packages 按全限定名直调库类（HkJsRuntime 无白名单），官方包
# 不混淆、类名全原样，规则生态按原名写死。此前保名只防「库按名找 App 类」
# 一向，漏了反向的「规则按名找库类」——134 成品 dex 实测五库原名在位率：
# XPopup 7/180、fastjson 0/274、Glide 4/645、jsoup 0/301、EventBus 2/44，
# 其余全被 R8 改名；聚阅 hikerPop 行 358 直调 com.lxj.xpopup.XPopup.Builder
# 当场 TypeError 即此病首爆。聚阅语料实证被点名的类：xpopup 的 XPopup/
# core.BottomPopupView/core.CenterPopupView/util.XPopupUtils/interfaces.
# XPopupCallback、fastjson.JSON、glide.Glide＋request.RequestOptions、
# jsoup.Jsoup、eventbus.EventBus——按名可达集不可枚举，整包保名（与
# org.litepal 同口径）。咱家转写类 com.lxj.xpopup.impl.MyInputConfirm-
# PopupView 同在 XPopup 域内，一并覆盖（134 成品里它亦已被改名）。
-keep class com.lxj.xpopup.** { *; }
-keep class com.alibaba.fastjson.** { *; }
-keep class com.bumptech.glide.** { *; }
-keep class org.jsoup.** { *; }
-keep class org.greenrobot.eventbus.** { *; }

# 依赖补齐批同口径保名（2026-11-14，账 byname-dep-fill-20261114）：
# xtooltip 由 hikerPop 经 Packages 按名直调（Tooltip/ClosePolicy），
# okhttp3.brotli.BrotliInterceptor 由宿主 3 处 Class.forName 按名探入并反射
# 读 INSTANCE 字段——两者皆按名面，整包/整类保名，验名门 G8f/G8g 逐版验。
-keep class it.sephiroth.android.library.xtooltip.** { *; }
-keep class okhttp3.brotli.** { *; }

# 补丁拼包核 JNI 口（账 xc-delta-update）：libapkpatch.so 的唯一 JNI 入口
# Java_com_github_sisong_ApkPatch_patch 按声明类全限定名写死、无 JNI_OnLoad，
# 声明类 com.github.sisong.ApkPatch 类名一改（含 R8 改名）首次调用必抛
# UnsatisfiedLinkError（1.4.133 真机补丁门 4 实锤，当时声明落自家包名）。
# 整类保名，成品审计逐版验名＋验符号对位。
-keep class com.github.sisong.ApkPatch { *; }

# 海阔模型：Gson 按字段名序列化/反序列化（MY_RULE、hiker://page 规则体），字段名不能改
-keep class com.fongmi.android.tv.api.hk.HkRule { *; }
-keep class com.fongmi.android.tv.api.hk.HkPageRule { *; }
-keep class com.fongmi.android.tv.api.hk.HkPageParser$HkPage { *; }
-keep class com.fongmi.android.tv.api.hk.HkViewCollection { *; }
-keep class com.fongmi.android.tv.api.hk.HkSubscribeRecord { *; }
-keep class com.fongmi.android.tv.api.hk.HkPlayData { *; }
# 页面缓存模型（2026-10-29 1.4.60 崩定根，账 hk-list-cast-crash-20261029）：HkItem 与
# PageCache 此前不在保名名单，成品字段改名＋data 的 Signature 被剥，saveCache 写盘键
# 漂移成混淆名、getCache 反射读出 LinkedTreeMap 元素、retype 按源码键查树落空，
# bindArrayToView 强转崩。字段已另以 @SerializedName 钉死、getCache 已改树读定型，
# 此处保名作双保险（与 HkRule 同口径：Gson 按字段名序列化，字段名不能改）。
-keep class com.fongmi.android.tv.api.hk.HkItem { *; }
-keep class com.fongmi.android.tv.ui.hk.HkAutoCacheUtil$PageCache { *; }

# 实验室自启触发（同族第 3 步口径）：App 在 main 源集按类名 Class.forName
# 调 LabBoot.kick（main 不能直引 mobile 源集的实验室类），此前漏了保名——
# 1.4.44 成品解包实证：ui.lab 非组件类被 R8 全改成单字母名，LabBoot 亦在
# 其中，反射必抛 ClassNotFoundException 又被 catch 静默吞掉，冷启自启的
# 头一个触发点（抢在配置首读之前那一脚）在成品里从来没响过，总开关只剩
# HomeActivity 的延迟 schedule 一条腿。整类保名保成员（getMethod 按名找
# kick，方法名同样要保）。
-keep class com.fongmi.android.tv.ui.lab.LabBoot { *; }

# 实验室落盘模型（注册表 registry.json＋包内 lab.json）：全是 Gson 反射
# 落盘、跨版本读回的表。成员被 R8 改名/剥离时，旧版写的文件新版按新名
# 读回——字段对不上轻则全空、重则类型冲突令整表被当坏文件改名清空
# （1.4.27→1.4.28 真机实证：Svc 的布尔位在新版变 int 端口位，fromJson
# 抛 JsonSyntaxException，两个服务凭空消失）。整类保名保成员，落盘名
# 再由 @SerializedName 钉死（LabRegistry/LabManifest 已钉），双保险。
-keep class com.fongmi.android.tv.ui.lab.LabRegistry { *; }
-keep class com.fongmi.android.tv.ui.lab.LabRegistry$Svc { *; }
-keep class com.fongmi.android.tv.ui.lab.LabRegistry$Runtime { *; }
-keep class com.fongmi.android.tv.ui.lab.LabManifest { *; }
-keep class com.fongmi.android.tv.ui.lab.LabManifest$LabCommand { *; }
-keep class com.fongmi.android.tv.ui.lab.LabManifest$LabVar { *; }
-keep class com.fongmi.android.tv.ui.lab.LabManifest$LabOption { *; }
-keep class com.fongmi.android.tv.ui.lab.LabManifest$LabDownload { *; }
-keep class com.fongmi.android.tv.ui.lab.LabManifest$LabOutput { *; }

# Node 桥版本登记口（第 4 步接线）：登记主路由已改为 LabBoot 自启链
# 直调 LabNodeInfo.register()（真实引用保活），Class.forName 触发静态块
# 自登记只留兜底。1.4.59 成品实证教训：本 keep 只保住了类名、<clinit>
# 仍被 R8 吃掉（成品里只剩私有构造器），登记永不触发、模块页恒显未装
# ——keep 类名不等于保住 clinit 的副作用，登记类一律要有直调兜底。
# 本行保留作双保险（类名供 Class.forName 兜底与问题定位）。
-keep class com.fongmi.android.tv.ui.lab.LabNodeInfo { *; }

# R8 吞件定根（2026-10-29，账 r8-swallow-20261029）：1.4.60 成品实证，
# 上面那条 keep 保住了 LabNodeInfo 的壳，register() 体仍被整段摘空。
# 真根不在写方：模块页读链的唯一入口是设置页「运行模块卡」的点击，
# 1.4.11 补批 7bb78331a 删卡时把 HomeActivity.showModuleFragment()
# 全仓唯一调用方一起删掉，此后 ModuleFragment → ModuleViewModel →
# NodeLabInfo 整条读链在 R8 眼里是不可达死代码、整类被删；中转的
# provider 字段无人读，写方「new Provider＋写字段」遂被判无副作用。
# 教训：keep 保壳不保可达性，登记链的存活取决于整条读写图谱活着。
# 设置页入口卡是否恢复属产品裁定（见账）；在此之前把整条图谱钉为
# 入口点防再被吞——登记中转与接口、Provider 实现、读链四件一并保。
-keep class com.fongmi.android.tv.utils.NodeLabInfo { *; }
-keep class com.fongmi.android.tv.utils.NodeLabInfo$Provider { *; }
-keep class com.fongmi.android.tv.ui.lab.LabNodeInfo$* { *; }
-keep class com.fongmi.android.tv.model.ModuleViewModel { *; }
-keep class com.fongmi.android.tv.model.ModuleViewModel$* { *; }
-keep class com.fongmi.android.tv.ui.fragment.ModuleFragment { *; }
-keep class com.fongmi.android.tv.ui.fragment.ModuleFragment$* { *; }
-keep class com.fongmi.android.tv.ui.dialog.ModuleDialog { *; }
-keep class com.fongmi.android.tv.utils.ModuleUtil { *; }
-keep class com.fongmi.android.tv.ui.custom.ModuleProgressView { *; }

# AI 语音/问答反射口（全应用扫描 O8，与 LabBoot 同型）：AiActionExecutor /
# AiQaHelper 靠 getMethod 按字面名调播放页与阅读页的 ai* 方法（aiNext /
# aiSeekBy / aiGetVodName / aiBookmarks 等共 23 个名），这些方法除反射外无
# 任何代码引用，此前全套无保名——R8 成品里方法名被改，语音操控与 AI 问答
# 取元信息全部静默失效。声明这批方法的类只有四个，只保 ai* 成员名。
-keepclassmembers class com.fongmi.android.tv.ui.activity.PlaybackActivity {
    public *** ai*(...);
}
-keepclassmembers class com.fongmi.android.tv.ui.activity.VideoActivity {
    public *** ai*(...);
}
-keepclassmembers class com.fongmi.android.tv.ui.activity.NovelReaderActivity {
    public *** ai*(...);
}
-keepclassmembers class com.fongmi.android.tv.ui.activity.ComicReaderActivity {
    public *** ai*(...);
}

# 按字符串类名反射装配的播放引擎保名（Startup 按名预热，无编译期引用保名）
-keep class com.fongmi.android.tv.player.engine.ExoPlayerEngine { *; }

# Chaquopy（账 r8-byname-sweep-20261114 行 2）：上游 FongMi 的
# chaquo/consumer-rules.pro 五条原文——本树 chaquo 模块无此文件、主规此前
# 亦无，Python 侧 from com.chaquo.python import Python 与 catvod GlobalProxy
# 的 Class.forName 全靠按名命中；132 成品实测 com.chaquo.python 本体幸存但
# com.fongmi.chaquo.Platform 已改名/摘除，同 LitePal 一型隐患，照上游补齐。
-keep class com.chaquo.python.** { *; }
-keep class kotlin.jvm.functions.** { *; }
-keep class kotlin.jvm.internal.FunctionBase { *; }
-keep class kotlin.reflect.KAnnotatedElement { *; }
-keepclassmembers class com.fongmi.chaquo.Platform { public android.content.Context getApplication(); }

# EventBus（账 r8-byname-sweep-20261114 行 4）：官方 ProGuard 规矩原文。
# 本树运行时未注册 EventIndex、靠反射扫 @Subscribe 方法（三源集 50+ 处），
# 此前主规零 EventBus 规则——订阅方法被裁/改名即事件静默不达。
# （-keepattributes *Annotation* 已在文件首部。）
-keepclassmembers class ** { @org.greenrobot.eventbus.Subscribe <methods>; }
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent { <init>(java.lang.Throwable); }

# 收官大扫查类二加固（2026-10-31，账 round-close-20261031）：以下三类含
# 泛型集合字段、走 Gson .class 读回，此前只靠 -keepattributes Signature 保险丝
# 单层防护（无 TypeToken/retype/点名 keep）——保险丝在位时不崩，但单层就是单层，
# 按 HkRule/HkItem 同口径补点名保名作第二层。保险丝本体一字未动。
-keep class com.fongmi.android.tv.api.hk.HkProxyServer$ProxyResult { *; }
-keep class com.fongmi.android.tv.api.hk.browser.HkBrowserProxyRule { *; }
-keep class com.fongmi.android.tv.ui.hk.HkX5Extra { *; }

# fastjson 1.2.83（聚阅批接入）桌面/可选编解码器引用了安卓不存在的类，
# 官方安卓接法即以 dontwarn 放行这四系（这些编解码器在安卓上永不触发）：
-dontwarn java.awt.**
-dontwarn org.joda.**
-dontwarn org.javamoney.**
-dontwarn springfox.**
# 135 全族保名后补三系：support.spring/jaxrs/retrofit 集成类随全族 keep 成根，
# 其可选框架本体不在安卓依赖内（服务端适配、安卓永不触发），135 首编 R8
# 缺类 37 条全出自此三系，与上四系同口径 dontwarn 放行（keep 本体不动）。
-dontwarn org.springframework.**
-dontwarn org.glassfish.jersey.**
-dontwarn retrofit2.**
