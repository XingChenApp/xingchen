package com.example.hikerview.ui.setting.office;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.hikerview.R;
import com.example.hikerview.event.LockStateChangeEvent;
import com.example.hikerview.ui.ActivityManager;
import com.example.hikerview.ui.browser.util.CollectionUtil;
import com.example.hikerview.ui.ext.DohProvidersKt;
import com.example.hikerview.ui.setting.model.SettingConfig;
import com.example.hikerview.ui.setting.model.VersionInfo;
import com.example.hikerview.ui.view.CustomCenterRecyclerViewPopup;
import com.example.hikerview.utils.DisplayUtil;
import com.example.hikerview.utils.FileUtil;
import com.example.hikerview.utils.PreferenceMgr;
import com.example.hikerview.utils.StringUtil;
import com.example.hikerview.utils.ThreadTool;
import com.example.hikerview.utils.ToastMgr;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.HkHttpHelper;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.ui.hk.HkListActivity;
import com.fongmi.android.tv.utils.XingChenDoh;
import com.fongmi.android.tv.ui.hk.HkWebCarrier;
import com.google.gson.Gson;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.interfaces.OnConfirmListener;
import com.lxj.xpopup.interfaces.OnInputConfirmListener;
import com.lxj.xpopup.interfaces.OnSelectListener;

import org.greenrobot.eventbus.EventBus;

import okhttp3.OkHttpClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

import timber.log.Timber;

/**
 * 官 {@code com.example.hikerview.ui.setting.office.MoreSettingOfficer}
 * 的插件面移植：先落 hikerPop.checkByBiometric 实调的
 * {@code INSTANCE.checkByBiometric(Context, Function0)}，照官 jadx 逐行。
 *
 * <p>2026-11-14 对官补缺口批（官源 MoreSettingOfficer.smali，
 * smali_classes2/com/example/hikerview/ui/setting/office/，85 法）：
 * 另落 getCpuAbi、getTrackers（连私有 getTrackers0）、search、
 * loadTrackers 四法，逐法出处见各法注。PreferenceMgr／UriUtils／
 * FilesInAppUtil 三个官工具类咱家无同名类：PreferenceMgr 照本树
 * SettingConfig 批先例直连 SharedPreferences（默认文件 "share_data"，
 * PreferenceMgr.smali:15）；UriUtils.getRootDir 与
 * FilesInAppUtil.getAssetsString 按官 smali 公式在本文件内联复刻，
 * 不另立类。
 * 停手记名（依赖咱家没有的官类/库/资源或被停手链牵连，不落空壳）：
 * autoClearData、showClearDataPopup（私有 clearData 六格中：缓存格赖
 * CleanMessageUtil（802 行可转写）且内嵌 QbSdk.clearAllWebViewCache
 * 子段——不接 X5 既定决策停手；账号密码格赖 BigTextDO.clearFormInputs
 * （官 model 2161 行、①团未落）；搜索格赖 SearchHistroyModel；整法
 * 照官落不齐、不落半截；LitePal/ViewHistory 两格依赖已随①团落位、
 * 不再是堵点）、handle（BaseSettingActivity 设置框架的条目分发体，
 * 框架与 ISettingOfficer 均无，且分发到多个停手法）、inject
 * （ISettingOfficer 框架法、框架无；其 lambda 另赖 CleanMessageUtil
 * .getTotalCacheSize）、loadDns（2026-11-14 ④团已落本体，见下
 * loadDns 法注：读 "custom"/"dns" 分发 DohProvidersKt 八家工厂＋
 * 自定义支；DohProvidersKt 所需段——八家工厂/cacheClient/dohCustom/
 * checkToHttpUrl/toHostMap——照官落 main 源集官 FQN
 * com.example.hikerview.ui.ext.DohProvidersKt，auto-DNS 段已等价
 * 落位 HkHttp 不重复立；消费接线一段停手记名：官四调用点对位咱家
 * HkHttp.client()/imageClient() 构建位在 main 源集、本类在 mobile
 * 源集，直调将使 leanback flavor 断编，且用户 DoH 消费已由壳子
 * 统一链 XingChenDoh→HkHttp.applyDoh 承担、不叠第二套分发，详见
 * worknotes/hk-14-fill-parts/c4.md；写侧已于 2026-11-14 簇4补齐：
 * handle 的「自定义DNS解析」支以独立入口 showDnsSetting 落在本类
 * （见下「DNS 设置写侧」段：12 选项/键名/文案照官 handle 支逐字，
 * 写键后经 XingChenDoh 同步路使 HkHttp 当场消费，同步步官无、记名
 * 于法注与分账 fill-136b-c4-host.md），handle 分发本体仍随设置框架
 * 缺位停手）、show 两重载
 * （BaseSettingActivity 无真等价设置入口）、showMagnetEngine
 * （TorrentEngineBean 与 libtorrent4j 链无、libtorrent4j 不在已批
 * 引库名单）。
 * 2026-11-14 ③团复核续补：autoCheckUpdate、checkAppUpdate 两重载已落——
 * VersionInfo 照官转写落 ui.setting.model；R.string.version_check_url/2
 * 照官值补入 res/values/hk_hiker.xml 并补 R 垫片字段；CommonUtil
 * .getVersionCode 按官公式内联为本文件私有 getVersionCode；WebUtil
 * .goWeb 接本树既有决策口（详见私有 goWeb 注）；HttpHelper.get 接
 * HkHttpHelper.fetch0（同本文件 loadTrackers 先例）。
 *
 * <p>2026-11-14 ③团续补：showPrivacyMode、checkPrivacyPwdMenu、
 * showPrivacyPwdMenu、showPrivacyModeMenu 四法互牵链已照官落齐
 * （LockStateChangeEvent 照官落 com.example.hikerview.event；
 * JSEngine.putVar2 接 HkJsRuntime.getInstance().putVar2；
 * FilmListActivity.putTempRule 接 HkListActivity.putTempRule；
 * ArticleListRule 接 HkRule；详见各法注），showPrivacyModeMenu 内
 * 三支（添加小程序/管理书签/管理网站密码）宿主面仍无等价物、
 * 法内记名停手；showToolsView 亦已照官落（assets tools.json 现存、
 * 解析接 HkRule）。
 */
public final class MoreSettingOfficer {

    public static final MoreSettingOfficer INSTANCE = new MoreSettingOfficer();

    /** 官 cpuAbi 静态位（MoreSettingOfficer.smali 字段表，初值 null）。 */
    private static String cpuAbi;

    private MoreSettingOfficer() {
    }

    public final void checkByBiometric(final Context context, final Function0<Unit> callback) {
        BiometricManager from = BiometricManager.from(context);
        int canAuthenticate = from.canAuthenticate();
        if (canAuthenticate == 0) {
            BiometricPrompt.PromptInfo build = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("生物识别认证")
                    .setSubtitle("请使用您的生物特征进行认证")
                    .setNegativeButtonText("取消")
                    .build();
            FragmentActivity currentActivity = (FragmentActivity) ActivityManager.getInstance().getCurrentActivity();
            new BiometricPrompt(currentActivity, ContextCompat.getMainExecutor(context),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            callback.invoke();
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            ToastMgr.shortBottomCenter(context, "生物识别出错：" + errString);
                        }
                    }).authenticate(build);
            return;
        }
        if (canAuthenticate == 1) {
            ToastMgr.shortBottomCenter(context, "生物识别当前不可用");
        } else if (canAuthenticate == 11) {
            ToastMgr.shortBottomCenter(context, "生物识别信息未录入");
        } else {
            if (canAuthenticate != 12) {
                return;
            }
            ToastMgr.shortBottomCenter(context, "当前设备没有对应硬件设施");
        }
    }

    // —— 对官补缺口批（2026-11-14） ——

    /**
     * 官 UriUtils.getRootDir（UriUtils.smali，官行 202-205）公式内联：
     * 咱家无 UriUtils 类，按官原文取 getExternalFilesDir(DOCUMENTS)、
     * requireNonNull、不存在则 mkdirs、回绝对路径。
     */
    private static String getRootDir(Context context) {
        File dir = Objects.requireNonNull(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS));
        if (!dir.exists()) {
            Objects.requireNonNull(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)).mkdirs();
        }
        return Objects.requireNonNull(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)).getAbsolutePath();
    }

    /**
     * 官 FilesInAppUtil.getAssetsString（FilesInAppUtil.smali，官行
     * 173-193）内联：咱家无 FilesInAppUtil 类；逐行读 assets、以 "\n"
     * 连接（首行无前缀），Throwable 只 Timber.e、回已拼部分；官本体
     * 不关流，照官。
     */
    private static String getAssetsString(Context context, String name) {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(name)));
            int i = -1;
            String line;
            while ((line = reader.readLine()) != null) {
                if (i == -1) {
                    i++;
                    sb.append(line);
                } else {
                    sb.append("\n").append(line);
                }
            }
        } catch (Throwable t) {
            Timber.e(t);
        }
        return sb.toString();
    }

    /** 官 getCpuAbi（MoreSettingOfficer.smali，官行 970-985）：静态位命中直回；否则反射读 ApplicationInfo.primaryCpuAbi，异常只打栈；仍空回落 Build.SUPPORTED_ABIS[0]。 */
    public final String getCpuAbi(Context context) {
        if (cpuAbi != null) {
            return cpuAbi;
        }
        try {
            ApplicationInfo info = context.getApplicationInfo();
            Field field = ApplicationInfo.class.getDeclaredField("primaryCpuAbi");
            field.setAccessible(true);
            cpuAbi = (String) field.get(info);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (TextUtils.isEmpty(cpuAbi)) {
            cpuAbi = Build.SUPPORTED_ABIS[0];
        }
        return cpuAbi;
    }

    /** 官 getTrackers0（MoreSettingOfficer.smali，官行 856-861）：根目录/plugins/trackers.txt 存在读文件；不存在时 always 才回 assets 的 trackers.txt，否则回空串。 */
    private final String getTrackers0(Context context, boolean always) {
        String path = getRootDir(context) + File.separator + "plugins" + File.separator + "trackers.txt";
        if (!new File(path).exists()) {
            if (always) {
                return getAssetsString(context, "trackers.txt");
            }
            return "";
        }
        return FileUtil.fileToString(path);
    }

    /** 官 getTrackers（MoreSettingOfficer.smali，官行 851）：转 getTrackers0(context, true)。 */
    public final String getTrackers(Context context) {
        return getTrackers0(context, true);
    }

    /** 官 search（MoreSettingOfficer.smali，官行 98-115）：16 个条目名按 key 过滤——条目名转小写（默认 Locale）后 contains(key)（key 不转小写，官原样）。 */
    public final List<String> search(String key) {
        List<String> all = new ArrayList<>();
        all.add("内部文件管理");
        all.add("磁力种子引擎");
        all.add("世界直播地址");
        all.add("足迹相关设置");
        all.add("自定义DNS解析");
        all.add("自定义图片UA");
        all.add("秘密空间设置");
        all.add("使用常见问题");
        all.add("退出时清除数据");
        all.add("清除Cookie");
        all.add("检查更新");
        all.add("自动检查更新");
        all.add("鼓励更新");
        all.add("软件开源地址");
        all.add("使用协议");
        all.add("隐私政策");
        List<String> result = new ArrayList<>();
        for (String item : all) {
            if (item.toLowerCase(Locale.getDefault()).contains(key)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 官 loadTrackers（MoreSettingOfficer.smali，官行 931-946，连
     * $1 协程体官行 947-966）：偏好 "tracker"（默认文件 "share_data"，
     * 照官 PreferenceMgr）为空直回 callback("")；checkTime 且本地
     * trackers.txt 未满一天同样回 ""；余下后台拉取、滤 http/udp 开头
     * 行、"\n" 拼接、非空写回 trackers.txt 后 callback(拼接串)。
     * 接等价物（记名）：官 ThreadTool.async(Function2 协程块)——$1 体
     * 内无挂起点，接咱家 ThreadTool.async(Runnable)（ThreadTool.java，
     * 同 IO 执行器）；官 HttpHelper.get(url, 空头表)＝fetch(url,
     * {headers})，接其移植 HkHttpHelper.fetch0（HkHttpHelper.java，
     * opts 同官 get 的装配：headers 键放空头表）；官
     * Application.getContext() 接 App.get()（本树既有先例）。
     */
    public final void loadTrackers(boolean checkTime, final Function1<String, Unit> callback) {
        Context appContext = App.get();
        String tracker = appContext.getSharedPreferences("share_data", Context.MODE_PRIVATE)
                .getString("tracker", "");
        if (tracker == null || tracker.length() == 0) {
            callback.invoke("");
            return;
        }
        if (checkTime) {
            String path = getRootDir(appContext) + File.separator + "plugins" + File.separator + "trackers.txt";
            File file = new File(path);
            if (file.exists() && System.currentTimeMillis() - file.lastModified() < 86400000L) {
                callback.invoke("");
                return;
            }
        }
        final String url = tracker;
        ThreadTool.INSTANCE.async(() -> {
            Map<String, Object> opts = new HashMap<>();
            opts.put("headers", new HashMap<String, String>());
            String text = (String) HkHttpHelper.fetch0(url, opts, null, null);
            if (text == null || text.length() == 0) {
                callback.invoke("");
                return;
            }
            String[] lines = text.split("\n", -1);
            List<String> kept = new ArrayList<>();
            for (String line : lines) {
                if (line.startsWith("http") || line.startsWith("udp")) {
                    kept.add(line);
                }
            }
            String joined = String.join("\n", kept);
            if (joined.length() != 0) {
                String path = getRootDir(App.get()) + File.separator + "plugins" + File.separator + "trackers.txt";
                FileUtil.stringToFile(joined, path);
            }
            callback.invoke(joined);
        });
    }

    // —— DNS 装载（2026-11-14 ④团补齐，官源 MoreSettingOfficer.smali） ——

    /**
     * 官 loadDns（MoreSettingOfficer.smali:3585，@JvmStatic，
     * .line 1201-1218）：读偏好文件 "custom" 键 "dns"（默认
     * "智能"，官 PreferenceMgr.getString 接本树同名同签名的
     * PreferenceMgr；官 Application.getContext() 接 App.get()，
     * 同本文件 loadTrackers 先例），按值分发 DohProvidersKt 八家
     * 工厂——Cloudflare(.line 1202)/Google(1203)/AdGuard(1204)/
     * Quad9(1205)/AliDNS(1206)/DNSPod(1207)/360(1208)/
     * Quad 101(1209)；"自定义DoH"(1211-1218) 支再读同文件键
     * "dnsCustom"（默认 ""），非空才 dohCustom(builder, 值, null)
     * （官 dohCustom$default mask=0x2、host 缺省 null）；"智能" 与
     * 未知值及偏好为 null 时不装任何 Dns、builder 原样（官
     * sparse-switch 无命中直 goto 返回）。DohProvidersKt 所需段
     * 已照官落 main 源集 com.example.hikerview.ui.ext.DohProvidersKt。
     * 消费接线停手记名见类头 loadDns 条与
     * worknotes/hk-14-fill-parts/c4.md。
     */
    public static void loadDns(OkHttpClient.Builder builder) {
        String dns = PreferenceMgr.getString(App.get(), "custom", "dns", "智能");
        if (dns == null) {
            return;
        }
        switch (dns) {
            case "Cloudflare":
                DohProvidersKt.dohCloudflare(builder);
                break;
            case "Google":
                DohProvidersKt.dohGoogle(builder);
                break;
            case "AdGuard":
                DohProvidersKt.dohAdGuard(builder);
                break;
            case "Quad9":
                DohProvidersKt.dohQuad9(builder);
                break;
            case "AliDNS":
                DohProvidersKt.dohAliDNS(builder);
                break;
            case "DNSPod":
                DohProvidersKt.dohDNSPod(builder);
                break;
            case "360":
                DohProvidersKt.doh360(builder);
                break;
            case "Quad 101":
                DohProvidersKt.dohQuad101(builder);
                break;
            case "自定义DoH":
                String dnsCustom = PreferenceMgr.getString(App.get(), "custom", "dnsCustom", "");
                if (dnsCustom != null && dnsCustom.length() != 0) {
                    DohProvidersKt.dohCustom(builder, dnsCustom, null);
                }
                break;
            default:
                break;
        }
    }

    // —— DNS 设置写侧（2026-11-14 簇4补齐，官源 MoreSettingOfficer.handle 的
    // 「自定义DNS解析」支：handle 本体 smali:8177-8371＋选择回调
    // handle$lambda$10 smali:1695-1965＋自定义输入回调 handle$lambda$10$lambda$8
    // smali:2181-2294＋重启回调 $lambda$7/$lambda$9 smali:2296-2350） ——

    /** 官 handle「自定义DNS解析」支的展示选项（.line 196-207，序逐字）。 */
    private static final String[] DNS_OPTIONS = {
            "智能", "禁用", "阿里AliDNS", "腾讯DNSPod", "360", "Cloudflare",
            "Google", "AdGuard", "Quad9", "Quad 101", "自定义DoH", "使用说明 & 全局自定义"
    };

    /**
     * 官 handle 的「自定义DNS解析」支本体（框架 handle 无，本支以独立入口
     * 落在本类的设置项体系内——search()/show() 的条目名「自定义DNS解析」
     * 已在位，宿主设置面接线时调本法即可，见分账）：读
     * PreferenceMgr.getString(context, "custom", "dns", "智能")，展示名逐个
     * replace("阿里","").replace("腾讯","") 得存储值表，当前值在表中的下标
     * 作 asBottomList 的 checkedPosition（官 .line 211-215 同形）；弹窗
     * borderRadius=dpToPx(16)、标题即条目名。
     */
    public final void showDnsSetting(final Activity context) {
        String current = PreferenceMgr.getString(App.get(), "custom", "dns", "智能");
        List<String> storedValues = new ArrayList<>();
        for (String option : DNS_OPTIONS) {
            storedValues.add(option.replace("阿里", "").replace("腾讯", ""));
        }
        int checked = storedValues.indexOf(current);
        OnSelectListener listener = (position, text) -> onDnsOptionSelected(context, text);
        new XPopup.Builder(context)
                .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                .asBottomList("自定义DNS解析", DNS_OPTIONS, null, checked, listener)
                .show();
    }

    /**
     * 官 handle$lambda$10（选择回调，按选中文本分发）：
     * 「使用说明 & 全局自定义」→说明确认弹窗；「自定义DoH」→输入弹窗；
     * 余项→存储值＝文本 replace("阿里","").replace("腾讯","") 写
     * "custom"/"dns"，再弹重启确认（「智能」头文案不同，余为「设置成功」）。
     * 写键后另经壳子既有同步路把等价 DoH 同步进 XingChenDoh（见
     * syncDnsToShell 注），官无此步、记名于分账。
     */
    private void onDnsOptionSelected(final Activity context, String text) {
        if (text == null) {
            return;
        }
        if ("使用说明 & 全局自定义".equals(text)) {
            new XPopup.Builder(context)
                    .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                    .asConfirm(text,
                            "自定义DNS解析可以解决部分网络问题，如运营商劫持、恶意广告泛滥等，建议国内大陆用户选AliDNS、DNSPod、360等国内DNS，否则可能出现访问不了的情况。另外这里的自定义DNS只对除网页浏览之外的请求有效，因为软件使用系统WebView内核来浏览网页，请求由系统发出，因此要想全局自定义DNS解析，只能在手机系统设置中搜索私人DNS（或加密DNS），设置DNS主机域名，具体如何设置可以在浏览器中搜索教程，不过这个自定义会影响整个手机系统",
                            "复制可选主机域名", "确定",
                            () -> {
                            },
                            () -> showDnsHostCopyList(context),
                            false)
                    .show();
            return;
        }
        if ("自定义DoH".equals(text)) {
            String dnsCustom = PreferenceMgr.getString(App.get(), "custom", "dnsCustom", "");
            OnInputConfirmListener listener = input -> onDnsCustomInput(context, text, input);
            new XPopup.Builder(context)
                    .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                    .asInputConfirm(text, "注意仅支持DNS-over-HTTPS", dnsCustom, null, listener)
                    .show();
            return;
        }
        String value = text.replace("阿里", "").replace("腾讯", "");
        PreferenceMgr.put(context, "custom", "dns", value);
        syncDnsToShell(context, value, null);
        String head = "智能".equals(text)
                ? "已设置为智能模式，对容易被劫持的网站使用自定义DNS，其它网站不启用"
                : "设置成功";
        showDnsRestartConfirm(context, head + "，需要重启软件才能生效，是否立即重启？");
    }

    /**
     * 官 handle$lambda$10$lambda$6（「复制可选主机域名」支）：底部列表
     * [AliDNS, DNSPod, 360, 18bit]（官 asBottomList 的 checkedPosition 字面
     * 5＝无选中），点选经 ClipboardUtil.copyToClipboard 复制主机域名
     * （咱家无 ClipboardUtil 类，以系统 ClipboardManager 等价承接）：
     * AliDNS→dns.alidns.com、DNSPod→dot.pub、360→dot.360.cn、18bit→dns.18bit.cn。
     */
    private void showDnsHostCopyList(final Activity context) {
        String[] names = {"AliDNS", "DNSPod", "360", "18bit"};
        OnSelectListener listener = (position, text) -> {
            if (text == null) {
                return;
            }
            String host;
            switch (text) {
                case "AliDNS":
                    host = "dns.alidns.com";
                    break;
                case "DNSPod":
                    host = "dot.pub";
                    break;
                case "360":
                    host = "dot.360.cn";
                    break;
                case "18bit":
                    host = "dns.18bit.cn";
                    break;
                default:
                    return;
            }
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(null, host));
            }
        };
        new XPopup.Builder(context)
                .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                .asBottomList(null, names, null, 5, listener)
                .show();
    }

    /**
     * 官 handle$lambda$10$lambda$8（自定义 DoH 输入回调）：输入为空直回；
     * 不以 "https" 开头 toast「链接格式不对，注意仅支持DNS-over-HTTPS，
     * 必须https开头」；余写 "custom"/"dns"＝选中文本（"自定义DoH"）、
     * "custom"/"dnsCustom"＝输入值，再弹重启确认（文案「设置成功，需要
     * 重启软件才能生效，是否立即重启？」）。
     */
    private void onDnsCustomInput(final Activity context, String selectedText, String input) {
        if (input == null || input.length() == 0) {
            return;
        }
        if (!input.startsWith("https")) {
            ToastMgr.shortCenter(context, "链接格式不对，注意仅支持DNS-over-HTTPS，必须https开头");
            return;
        }
        PreferenceMgr.put(context, "custom", "dns", selectedText);
        PreferenceMgr.put(context, "custom", "dnsCustom", input);
        syncDnsToShell(context, selectedText, input);
        showDnsRestartConfirm(context, "设置成功，需要重启软件才能生效，是否立即重启？");
    }

    /** 官重启确认弹窗（标题「温馨提示」、borderRadius=dpToPx(16)、asConfirm 单钮形）；确认回调＝killProcess＋System.exit(0)（官 $lambda$7/$lambda$9 逐字）。 */
    private void showDnsRestartConfirm(final Activity context, String content) {
        OnConfirmListener restart = () -> {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        };
        new XPopup.Builder(context)
                .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                .asConfirm("温馨提示", content, restart)
                .show();
    }

    /**
     * 咱家同步步（官无此步，记名）：官 "custom"/"dns" 的唯一读者是 loadDns，
     * 而咱家 HkHttp 构建位不直调 loadDns（mobile/main 源集隔离＋leanback
     * 断编，④团已记名）——海阔请求的实际 DoH 消费走壳子统一链
     * XingChenDoh.apply→HkHttp.applyDoh（同步路在位）。故写键后把等价选择
     * 同步进 XingChenDoh 的存储（Setting "doh"），使新 handle 写的值当场
     * 被 HkHttp 消费、不成死键：内置三家走 XingChenDoh.setIndex（腾讯=1/
     * 阿里=2/360=3），其余具名家与自定义DoH 按 DohProvidersKt 的端点 URL
     * 组 Doh 写入并 apply；「智能」清 Doh 回系统档（HkHttp.applyDoh 口径：
     * 未指定 DoH＝智能档、dnsAuto 开）；「禁用」同样清 Doh——咱家无「系统
     * 解析＋关 autoDns」的独立档，禁用与智能在消费侧同落系统档，此差记名。
     */
    private void syncDnsToShell(Context context, String storedValue, String customUrl) {
        try {
            switch (storedValue) {
                case "智能":
                case "禁用":
                    XingChenDoh.setIndex(context, 0);
                    break;
                case "DNSPod":
                    XingChenDoh.setIndex(context, 1);
                    break;
                case "AliDNS":
                    XingChenDoh.setIndex(context, 2);
                    break;
                case "360":
                    XingChenDoh.setIndex(context, 3);
                    break;
                case "Cloudflare":
                    putShellDoh(context, "Cloudflare", "https://cloudflare-dns.com/dns-query");
                    break;
                case "Google":
                    putShellDoh(context, "Google", "https://dns.google/dns-query");
                    break;
                case "AdGuard":
                    putShellDoh(context, "AdGuard", "https://dns-unfiltered.adguard.com/dns-query");
                    break;
                case "Quad9":
                    putShellDoh(context, "Quad9", "https://dns.quad9.net/dns-query");
                    break;
                case "Quad 101":
                    putShellDoh(context, "Quad 101", "https://dns.twnic.tw/dns-query");
                    break;
                case "自定义DoH":
                    if (customUrl != null && customUrl.length() != 0) {
                        putShellDoh(context, "自定义DoH", customUrl);
                    }
                    break;
                default:
                    break;
            }
        } catch (Throwable ignored) {
        }
    }

    private void putShellDoh(Context context, String name, String url) {
        com.fongmi.android.tv.setting.Setting.putDoh(
                new com.github.catvod.bean.Doh().name(name).url(url).toString());
        XingChenDoh.apply(context);
    }

    // —— 隐私空间链（2026-11-14 ③团补齐，官源 MoreSettingOfficer.smali） ——

    /**
     * 官 PreferenceMgr.getString/put/remove（默认文件 "share_data"）按本文件
     * 既有先例直连 SharedPreferences（put 对 String 走 putString、remove
     * 走 edit().remove()，均 apply，与官 SharedPreferencesCompat.apply 同效）。
     */
    private static String prefGetString(Context context, String key, String def) {
        return context.getSharedPreferences("share_data", Context.MODE_PRIVATE).getString(key, def);
    }

    private static void prefPutString(Context context, String key, String value) {
        context.getSharedPreferences("share_data", Context.MODE_PRIVATE)
                .edit().putString(key, value).apply();
    }

    private static void prefRemove(Context context, String key) {
        context.getSharedPreferences("share_data", Context.MODE_PRIVATE)
                .edit().remove(key).apply();
    }

    /**
     * 官 showPrivacyMode（smali:9333，.line 1337-1341）：ppwd（默认 null）
     * 非空走 checkPrivacyPwdMenu 验密支，否则走 showPrivacyPwdMenu 初始化支。
     */
    public final void showPrivacyMode(Context context, boolean z) {
        String ppwd = prefGetString(context, "ppwd", null);
        if (StringUtil.isNotEmpty(ppwd)) {
            checkPrivacyPwdMenu(context, z);
        } else {
            showPrivacyPwdMenu(context, z);
        }
    }

    /**
     * 官 showPrivacyPwdMenu（smali:9469，.line 1427-1438）＋其选择回调
     * lambda$54（smali:5960）：XPopup 居中列表「秘密空间初始化」
     * [使用数字密码, 使用生物识别(指纹/人脸）]，borderRadius=dpToPx(16)；
     * 选中 0 走 useNumberForPrivacy、余走 useBiometricForPrivacy（z 透传）。
     */
    public final void showPrivacyPwdMenu(final Context context, final boolean z) {
        OnSelectListener listener = (position, text) -> {
            if (position == 0) {
                useNumberForPrivacy(context, z);
            } else {
                useBiometricForPrivacy(context, z);
            }
        };
        new XPopup.Builder(context)
                .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                .asCenterList("秘密空间初始化",
                        new String[]{"使用数字密码", "使用生物识别(指纹/人脸)"}, listener)
                .show();
    }

    /**
     * 官 useNumberForPrivacy（smali:6540，.line 1443-1476）：XPopup
     * asInputConfirm「秘密空间密码设置」，borderRadius=dpToPx(16)、
     * autoOpenSoftInput(true)；确认回调 lambda$55（smali:6613）照官：
     * 空回提示「不能为空」；逐位剔除 0-9 后仍有残余、或剔除结果等于魔术串
     * （官原文如此比对，该支实际不可达、照官保留）回「格式不对，只能输入
     * 0-9的数字」；余写入 ppwd、提示「保存成功」，z 为真再开
     * showPrivacyModeMenu。
     */
    private final void useNumberForPrivacy(final Context context, final boolean z) {
        OnInputConfirmListener listener = text -> {
            if (StringUtil.isEmpty(text)) {
                ToastMgr.shortCenter(context, "不能为空");
                return;
            }
            String stripped = text;
            for (int d = 0; d <= 9; d++) {
                stripped = stripped.replace(String.valueOf(d), "");
            }
            if (StringUtil.isNotEmpty(stripped) || "88888888088888888".equals(stripped)) {
                ToastMgr.shortCenter(context, "格式不对，只能输入0-9的数字");
                return;
            }
            prefPutString(context, "ppwd", text);
            ToastMgr.shortCenter(context, "保存成功");
            if (z) {
                showPrivacyModeMenu(context);
            }
        };
        new XPopup.Builder(context)
                .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                .autoOpenSoftInput(Boolean.valueOf(true))
                .asInputConfirm("秘密空间密码设置",
                        "请设置隐私密码启用秘密空间，只能输入0-9的数字，位数不限，注意输入后一定要存好，无法找回",
                        listener)
                .show();
    }

    /**
     * 官 useBiometricForPrivacy（smali:5986，.line 1480-1558）：
     * canAuthenticate==1 提示「生物识别当前不可用」；==11 弹 XPopup 确认
     * 「您还没有设置生物识别如指纹或人脸，请先添加，是否到系统设置中添加？」
     * （此支官无 borderRadius，照官），确认跳系统设置（lambda$58）；
     * ==12 提示「当前设备没有对应硬件设施」；==0 时若 ppwd 已是魔术串
     * 「88888888088888888」弹确认「您已开启生物识别，确定要关闭它吗？」
     * （borderRadius=dpToPx(16)），确认（lambda$56）删 ppwd 并重开
     * showPrivacyPwdMenu（官经 $default 强制 z=false）；否则拉起
     * BiometricPrompt（标题/副题/取消文案照官），回调
     * $useBiometricForPrivacy$biometricPrompt$1 照官：成功写 ppwd=魔术串、
     * 提示「生物识别验证已启用」、z 为真再开 showPrivacyModeMenu；
     * 出错提示「生物识别出错：」+errString。
     */
    private final void useBiometricForPrivacy(final Context context, final boolean z) {
        int canAuthenticate = BiometricManager.from(context).canAuthenticate();
        if (canAuthenticate != 0) {
            if (canAuthenticate == 1) {
                ToastMgr.shortBottomCenter(context, "生物识别当前不可用");
            } else if (canAuthenticate == 11) {
                OnConfirmListener goSettings = () -> {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings"));
                    context.startActivity(intent);
                };
                new XPopup.Builder(context)
                        .asConfirm("温馨提示",
                                "您还没有设置生物识别如指纹或人脸，请先添加，是否到系统设置中添加？",
                                goSettings)
                        .show();
            } else if (canAuthenticate == 12) {
                ToastMgr.shortBottomCenter(context, "当前设备没有对应硬件设施");
            }
            return;
        }
        String ppwd = prefGetString(context, "ppwd", "");
        if ("88888888088888888".equals(ppwd)) {
            OnConfirmListener closeBiometric = () -> {
                prefRemove(context, "ppwd");
                showPrivacyPwdMenu(context, false);
            };
            new XPopup.Builder(context)
                    .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                    .asConfirm("温馨提示", "您已开启生物识别，确定要关闭它吗？", closeBiometric)
                    .show();
            return;
        }
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("生物识别认证")
                .setSubtitle("请使用您的生物特征进行认证")
                .setNegativeButtonText("取消")
                .build();
        FragmentActivity currentActivity = (FragmentActivity) ActivityManager.getInstance().getCurrentActivity();
        new BiometricPrompt(currentActivity, ContextCompat.getMainExecutor(context),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        prefPutString(context, "ppwd", "88888888088888888");
                        ToastMgr.shortBottomCenter(context, "生物识别验证已启用");
                        if (z) {
                            showPrivacyModeMenu(context);
                        }
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        ToastMgr.shortBottomCenter(context, "生物识别出错：" + errString);
                    }
                }).authenticate(promptInfo);
    }

    /**
     * 官 checkPrivacyPwdMenu（smali:7198，.line 1346-1373）：
     * SettingConfig.privacyModeUnlocked 已解锁直开 showPrivacyModeMenu；
     * ppwd 为魔术串走本文件既有 checkByBiometric，成功回调
     * （$checkPrivacyPwdMenu$1）置 privacyModeUnlocked=true，z 为真开菜单、
     * 否则提示「验证通过」并发 LockStateChangeEvent（事件类已照官落
     * com.example.hikerview.event，咱家暂无订阅方、发送即无操作，同
     * OnTopEvent 先例）；余支照官：读 assets privacy.json 解析为规则
     * （官 ArticleListRule 接咱家 HkRule，Gson 口径）、JSEngine.putVar2
     * ("zzf_&9","1")（接 HkJsRuntime.getInstance().putVar2）、以
     * HkListActivity.putTempRule（官 FilmListActivity.putTempRule 对位）
     * 携规则 JSON＋title＋showPrivacyMenu=z 打开列表页。
     */
    public final void checkPrivacyPwdMenu(final Context context, final boolean z) {
        if (SettingConfig.privacyModeUnlocked) {
            showPrivacyModeMenu(context);
            return;
        }
        String ppwd = prefGetString(context, "ppwd", "");
        if ("88888888088888888".equals(ppwd)) {
            checkByBiometric(context, () -> {
                SettingConfig.privacyModeUnlocked = true;
                if (z) {
                    showPrivacyModeMenu(context);
                } else {
                    ToastMgr.shortBottomCenter(context, "验证通过");
                    EventBus.getDefault().post(new LockStateChangeEvent());
                }
                return Unit.INSTANCE;
            });
            return;
        }
        String privacyJson = getAssetsString(App.get(), "privacy.json");
        HkRule rule = HkRuleManager.parseRuleJson(privacyJson);
        HkJsRuntime.getInstance().putVar2("zzf_&9", "1");
        Intent intent = new Intent(context, HkListActivity.class);
        HkListActivity.putTempRule(intent, new Gson().toJson(rule));
        intent.putExtra("title", rule.getTitle());
        intent.putExtra("showPrivacyMenu", z);
        context.startActivity(intent);
    }

    /**
     * 官 showPrivacyModeMenu（smali:9371，.line 1564-1675）＋选择回调
     * lambda$60（smali:5575，按选中文本分发）：
     * XPopup 居中列表「请选择操作」七项，borderRadius=dpToPx(16)。
     * 可落四支照官：修改密码→showPrivacyPwdMenu（z=false，官经 $default
     * 强制）；使用生物识别→useBiometricForPrivacy（z=false，同上）；
     * 移除小程序→见下；关闭秘密空间→XPopup 确认（borderRadius 同上），
     * 确认回调 lambda$59（smali:5909）删 ppwd/ppr、privacyModeUnlocked
     * =false、privacyRules=null、提示「已关闭」。
     * 停手三支（咱家无对应宿主面，不硬挂、选中无动作，记名在此）：
     * 添加小程序（官 ArticleListRuleSearchActivity 的 batch/privacy 形态，
     * 咱家 HkRuleSearchActivity 无此形态）、管理书签（官 BookmarkActivity
     * 无等价面）、管理网站密码（官 FormInputMagActivity 无等价面）。
     */
    public final void showPrivacyModeMenu(final Context context) {
        OnSelectListener listener = (position, text) -> {
            if (text == null) {
                return;
            }
            switch (text) {
                case "修改密码":
                    showPrivacyPwdMenu(context, false);
                    break;
                case "使用生物识别(指纹/人脸)":
                    useBiometricForPrivacy(context, false);
                    break;
                case "添加小程序":
                    // 停手记名：官 ArticleListRuleSearchActivity batch/privacy 形态无等价面。
                    break;
                case "移除小程序":
                    showRemovePrivacyRulePopup(context);
                    break;
                case "管理书签":
                    // 停手记名：官 BookmarkActivity 无等价面。
                    break;
                case "管理网站密码":
                    // 停手记名：官 FormInputMagActivity 无等价面。
                    break;
                case "关闭秘密空间":
                    OnConfirmListener closePrivacy = () -> {
                        prefRemove(context, "ppwd");
                        prefRemove(context, "ppr");
                        SettingConfig.privacyModeUnlocked = false;
                        SettingConfig.privacyRules = null;
                        ToastMgr.shortCenter(context, "已关闭");
                    };
                    new XPopup.Builder(context)
                            .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                            .asConfirm("温馨提示",
                                    "确定关闭秘密空间吗？关闭后相关数据将会清除，清除后不能恢复",
                                    closePrivacy)
                            .show();
                    break;
                default:
                    break;
            }
        };
        new XPopup.Builder(context)
                .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                .asCenterList("请选择操作",
                        new String[]{"修改密码", "使用生物识别(指纹/人脸)", "添加小程序",
                                "移除小程序", "管理书签", "管理网站密码", "关闭秘密空间"},
                        listener)
                .show();
    }

    /**
     * 官 lambda$60「移除小程序」支（smali:5750-5890，.line 1604-1657）：
     * SettingConfig.privacyRules 空/未设提示「没有可移除的小程序」；
     * 余以 CustomCenterRecyclerViewPopup（withTitle「请选择要移除的
     * 小程序」＋with(规则名列表， 2, 点击监听）,该弹窗类咱家已照官落树）
     * 经 XPopup.Builder（borderRadius=dpToPx(16)).asCustom 展示。
     * 点击监听 $showPrivacyModeMenu$1$popup$1 照官：自 privacyRules 移除
     * 该名，集空删 ppr、否则以 "&&&" 拼接写回 ppr，提示「已删除」+名；
     * onLongClick 官为空体。
     */
    private final void showRemovePrivacyRulePopup(final Context context) {
        Set<String> rules = SettingConfig.privacyRules;
        if (rules == null || rules.isEmpty()) {
            ToastMgr.shortBottomCenter(context, "没有可移除的小程序");
            return;
        }
        CustomCenterRecyclerViewPopup.ClickListener clickListener =
                new CustomCenterRecyclerViewPopup.ClickListener() {
                    @Override
                    public void click(String url, int position) {
                        SettingConfig.privacyRules.remove(url);
                        if (CollectionUtil.isEmpty(SettingConfig.privacyRules)) {
                            prefRemove(context, "ppr");
                        } else {
                            prefPutString(context, "ppr", CollectionUtil.listToString(
                                    new ArrayList<>(SettingConfig.privacyRules), "&&&"));
                        }
                        ToastMgr.shortBottomCenter(context, "已删除" + url);
                    }

                    @Override
                    public void onLongClick(String url, int position) {
                    }
                };
        CustomCenterRecyclerViewPopup popup = new CustomCenterRecyclerViewPopup(context)
                .withTitle("请选择要移除的小程序")
                .with(new ArrayList<>(rules), 2, clickListener);
        new XPopup.Builder(context)
                .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                .asCustom(popup)
                .show();
    }

    /**
     * 官 showToolsView（smali:9497 起，.line 1226-1234）：读 assets
     * tools.json（本文件既有 getAssetsString 内联体承接官 FilesInAppUtil
     * .getAssetsString），解析取标题（官 ArticleListRule 接咱家 HkRule，
     * Gson 口径），以 HkListActivity.putTempRule（官 FilmListActivity
     * .putTempRule 对位，官本体即传原文 JSON 串）＋title 打开列表页；
     * 异常 printStackTrace 并 ToastMgr.shortCenter 提示消息。
     */
    public final void showToolsView(Context context) {
        try {
            String toolsJson = getAssetsString(context, "tools.json");
            HkRule rule = HkRuleManager.parseRuleJson(toolsJson);
            Intent intent = new Intent(context, HkListActivity.class);
            HkListActivity.putTempRule(intent, toolsJson);
            intent.putExtra("title", rule.getTitle());
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            ToastMgr.shortCenter(context, e.getMessage());
        }
    }

    // —— 检查更新链（2026-11-14 ③团补齐，官源 MoreSettingOfficer.smali） ——

    /**
     * 官 autoCheckUpdate（smali:6802，.line 1096-1103）：偏好
     * "autoCheckUpdate" 为真且距上次 "checkTamp" 超过 86400000ms 时，
     * 先回写 checkTamp=now，再 checkAppUpdate(context, true)。偏好读写接
     * 本树 PreferenceMgr（官同名类，默认文件 share_data）。
     */
    public final void autoCheckUpdate(Activity context) {
        if (PreferenceMgr.getBoolean(context, "autoCheckUpdate", false)) {
            long now = System.currentTimeMillis();
            long last = PreferenceMgr.getLong(context, "checkTamp", 0L);
            if (now - last > 86400000L) {
                PreferenceMgr.put(context, "checkTamp", now);
                checkAppUpdate(context, true);
            }
        }
    }

    /** 官 checkAppUpdate(Activity)（smali:6984，.line 1109）：转两参形、silence=false。 */
    public final void checkAppUpdate(Activity context) {
        checkAppUpdate(context, false);
    }

    /**
     * 官 checkAppUpdate(Activity, boolean)（smali:6999＋checkAppUpdate$1，
     * .line 1113-1145）：官以 GlobalScope＋Dispatchers.IO 协程跑——先以
     * R.string.version_check_url 取最新版，VersionInfo 为空或其 v/url
     * 任一为空时改以 version_check_url2 再取；随后 ThreadTool.runOnUI 走
     * UI 段。本树以 ThreadTool.async 承接 IO 段（同本文件 loadTrackers
     * 先例），两段结构与官一一对应。
     */
    public final void checkAppUpdate(final Activity context, final boolean silence) {
        ThreadTool.INSTANCE.async(() -> {
            VersionInfo info = getLatestVersion(context.getResources().getString(R.string.version_check_url));
            if (info == null || StringUtil.isEmpty(info.getV()) || StringUtil.isEmpty(info.getUrl())) {
                info = getLatestVersion(context.getResources().getString(R.string.version_check_url2));
            }
            final VersionInfo fInfo = info;
            ThreadTool.INSTANCE.runOnUI(() -> onCheckUpdateResult(fInfo, silence));
        });
    }

    /**
     * 官 checkAppUpdate$1.invokeSuspend$lambda$2（.line 1121-1142）：UI 段，
     * 宿主取 ActivityManager 当前 Activity。VersionInfo 无效（空或 v/url
     * 任一为空）时 toast「检查新版本失败」（不看 silence，照官）；c 大于
     * 本机 versionCode 时 XPopup 确认（16dp 圆角、「有新版本」／「检查到
     * 有新版本V{v}，更新内容请更新后查看，是否立即更新？」／取消／立即
     * 更新／isHideCancel=false），确认走 goWeb(url)；否则非 silence 时
     * toast「已是最新版」。
     */
    private void onCheckUpdateResult(VersionInfo info, boolean silence) {
        Activity current = ActivityManager.getInstance().getCurrentActivity();
        if (info == null || StringUtil.isEmpty(info.getV()) || StringUtil.isEmpty(info.getUrl())) {
            ToastMgr.shortCenter(current, "检查新版本失败");
            return;
        }
        if (info.getC() > getVersionCode(current)) {
            new XPopup.Builder(current)
                    .borderRadius((float) DisplayUtil.dpToPx(current, 16))
                    .asConfirm("有新版本", "检查到有新版本V" + info.getV() + "，更新内容请更新后查看，是否立即更新？",
                            "取消", "立即更新",
                            () -> goWeb(current, info.getUrl()),
                            () -> {
                            },
                            false)
                    .show();
        } else if (!silence) {
            ToastMgr.shortCenter(current, "已是最新版");
        }
    }

    /**
     * 官 getLatestVersion 私有（smali:1488，.line 1149-1162）：官本体为
     * suspend、体内无挂起点，IO 线程直跑——HttpHelper.get(url, 空 headers)
     * 取回文本，非空时 fastjson 解析、含 "hiker" 键则取其子对象再
     * parseObject 成 VersionInfo；异常 printStackTrace、回 null。
     * HttpHelper.get 接 HkHttpHelper.fetch0（opts={"headers": 空表}，
     * 同本文件 loadTrackers 先例的接法）。
     */
    private VersionInfo getLatestVersion(String url) {
        Map<String, Object> opts = new HashMap<>();
        opts.put("headers", new HashMap<String, String>());
        String response = (String) HkHttpHelper.fetch0(url, opts, null, null);
        if (response == null || response.length() == 0) {
            return null;
        }
        try {
            JSONObject json = JSON.parseObject(response);
            if (!json.containsKey("hiker")) {
                return null;
            }
            return JSON.parseObject(json.getJSONObject("hiker").toJSONString(), VersionInfo.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 官 CommonUtil.getVersionCode 按官公式内联（CommonUtil.smali .line
     * 26-30）：PackageInfo.versionCode，异常 Timber.e 后回 -1。官
     * CommonUtil 类未立、只此一法被本链用到，照本文件 UriUtils/
     * FilesInAppUtil 内联先例。
     */
    private static int getVersionCode(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            Timber.e(e);
            return -1;
        }
    }

    /**
     * 官 WebUtil.goWeb(context, url, true) 的本树决策口（同 HkDetailBridge/
     * HkRouteBlocker 既有口径的前段＋末段）：先 HkWebCarrier
     * .loadIntoExisting 载入既有网页承载，无承载回系统浏览器；中间段
     * HkListActivity.openX5PlayPage 为 hk 包包内私有、本包不可达，记名。
     * 官第三参 true 影响官 WebViewActivity 内的 WebUpdateUrlEvent 标记，
     * 本树承载面无此标记、对位止于打开动作。
     */
    private static void goWeb(Context context, String url) {
        if (HkWebCarrier.loadIntoExisting(url)) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }
}
