package com.example.hikerview.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.example.hikerview.R;
import com.example.hikerview.service.parser.PageParser;
import com.example.hikerview.ui.ActivityManager;
import com.example.hikerview.ui.setting.model.SettingConfig;
import com.example.hikerview.ui.view.popup.MyXpopup;
import com.example.hikerview.utils.view.DialogUtil;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.ui.hk.HkColType;
import com.fongmi.android.tv.ui.hk.HkDetailBridge;
import com.fongmi.android.tv.ui.hk.HkListActivity;
import com.google.gson.Gson;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.interfaces.OnCancelListener;
import com.lxj.xpopup.interfaces.OnConfirmListener;
import com.lxj.xpopup.interfaces.OnInputConfirmListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Objects;

import timber.log.Timber;

/**
 * 官 {@code com.example.hikerview.utils.AutoImportHelper} 的插件面适配。
 *
 * <p>只落插件（hikerPop.js 的 toNextPage）实调的一个合成方法
 * {@code lambda$couldCloudImport$2(String)}：官义为「云口令求值结果分发器」，
 * 插件借它打开 hiker://page/ 子页。分发顺序照官 jadx 原样：
 * error: → 失败提示；空/undefined → 未处理；toast:// → 提示；
 * web:// → 网页；x5:// → 临时规则页；hiker://page/ → 开子页并 return；
 * 其余 → checkText 口令分发；未处理 → 成功提示。
 *
 * <p>各支的星辰落点：toast 走本树 ToastMgr；web 走 HkDetailBridge.goWeb
 * （＝官 WebUtil.goWeb 的星辰转写宿主钩子）；x5 按星辰 HkArticleFragment
 * 既有的 x5 子页构造法（临时规则＋findRule JS＋X5_WEB_VIEW 列型）经
 * HkListActivity.startRule 打开，标题照官为「云口令」；page 支走宿主
 * HkListActivity.openPageUrlFromJs（以本页当前规则经 HkPageParser 组页）；
 * checkText 走 HkDetailBridge.hooks（星辰早已转写的同一官方法）。
 * 官 couldCloudImport 本体（云口令弹窗链）已于 2026-11-14 ③团补齐：
 * MyXpopup/MyInputConfirmPopupView 建面链照官落树后本体转写落地
 * （见 couldCloudImport 法注）。
 *
 * <p>本批（对官补缺口 16 法）另落纯文本/偏好/剪贴板六法：getCommand、
 * getCommandText、getRealRule、getShareRule、setShareRule、shareWithCommand
 * （逐法官出处见各法注）。2026-11-14 一团（导入链）复核订正：RuleChecker 安全链与
 * RuleImporter 接口已落树、ArticleListRuleJO/Bookmark/LitePal 已在位，但目标 7 行仍全停，
 * 逐行新堵点与接线未改指说明全账见 worknotes/hk-14-fill-parts/c1.md（下文旧记名为前批
 * 口径、与 c1 冲突处以 c1 为准）。其余十法停手记名（见各缺口回执）：checkAutoText×2、
 * realCheckAutoText×2 赖官 PromptDialog＋getCurrentTopActivity 的
 * FilmListActivity/RuleWindowActivity/MainActivity/VideoPlayerActivity/
 * Application 宿主面，咱家无此等价面；checkText 赖 couldThemeImport/
 * RuleImporterManager.parse，无等价物（HkDetailBridge.hooks
 * 的 importRuleText 只覆盖规则 JSON/云口令/JS 子集，无主题支，
 * 不等价）；importBookmarkByStr 赖 BookmarkModel.addByList＋GlobalDialogActivity，
 * 皆无（Bookmark 在咱家只是纯模型、无 LitePal 存取）；importJSPlugin 赖
 * JSManager（咱家无用户脚本管理器，HkWebkitHolder 已记名）；importRuleByRuleTextList
 * 赖 ArticleListRuleJO＋importHomeRulesByText(List) 的 GlobalDialogActivity/LitePal
 * 落库链＋ClipboardUtil 清检测事件，皆无；importRulesByTextWithDialog 三支分别赖
 * RuleChecker/BackupUtil/LitePal.deleteAll/checkText，皆无或已停手；
 * showWithAdUrlsDialog 已于③团照官新建布局后落地（见法注）。
 */
public class AutoImportHelper {

    /** 官 shareRule 静态字段（AutoImportHelper.smali:64；&lt;clinit&gt; 初值 ""，smali:105-107）。 */
    private static String shareRule = "";

    /** 官 cloudImport 常量（AutoImportHelper.smali 字段表：public static final = "@import=js:"）。 */
    public static final String cloudImport = "@import=js:";

    /** 官 cloudImportFilterTags 静态字段（&lt;clinit&gt;，smali:83-103）：命中任一「￥tag￥」即拒导入。 */
    private static final String[] cloudImportFilterTags = {"home_rule", "home_rule_v2", "home_rule_url"};

    public static void lambda$couldCloudImport$2(String str) {
        Activity currentActivity = ActivityManager.getInstance().getCurrentActivity();
        if (StringUtil.isNotEmpty(str) && str.startsWith("error:")) {
            ToastMgr.shortBottomCenter(currentActivity, "云口令导入失败");
            return;
        }
        boolean z;
        if (!StringUtil.isNotEmpty(str) || "undefined".equalsIgnoreCase(str)) {
            z = false;
        } else {
            z = true;
            if (str.startsWith(StringUtil.SCHEME_TOAST)) {
                // 官为 StringUtils.replaceOnceIgnoreCase；前置 startsWith 已定原样前缀，截头等义
                ToastMgr.shortBottomCenter(currentActivity,
                        str.substring(StringUtil.SCHEME_TOAST.length()));
            } else if (str.startsWith(StringUtil.SCHEME_WEB)) {
                new HkDetailBridge().goWeb(currentActivity,
                        firstPageUrl(str));
            } else if (str.startsWith("x5://")) {
                String inner = str.substring("x5://".length());
                HkRule rule = new HkRule();
                rule.setUrl("hiker://empty");
                rule.setFindRule("js:setResult([{\n    url:\""
                        + HkJsRuntime.escapeJsString(inner)
                        + "\",\ncol_type:\"x5_webview_single\",\ndesc:\"100%&&float\",\nextra:{canBack: true}\n}]);");
                rule.setColType(HkColType.X5_WEB_VIEW.code);
                rule.setTitle("云口令");
                if (currentActivity != null) {
                    HkListActivity.startRule(currentActivity, rule, "云口令");
                }
            } else {
                if (PageParser.isPageUrl(str)) {
                    if (currentActivity instanceof HkListActivity) {
                        ((HkListActivity) currentActivity).openPageUrlFromJs(str);
                    }
                    return;
                }
                z = HkDetailBridge.hooks.checkText(currentActivity, str);
            }
        }
        if (z) {
            return;
        }
        ToastMgr.shortBottomCenter(currentActivity, "云口令导入成功");
    }

    /**
     * 官 couldCloudImport(Context,String)（AutoImportHelper.smali:482-721，
     * .line 364-455）照官转写：先经 getCurrentTopActivity 定弹窗宿主；文本须含
     * "@import=js:"、切分后首段非 "["/"{" 开头、且不含 cloudImportFilterTags
     * 任一「￥tag￥」，否则回 false；字节长 &gt; 51200 时预览截前 2000 字加
     * 「...字符过多，请点击完整口令按钮查看全部内容」、取消钮文案改「完整口令」
     * 并挂 lambda$1（写缓存文件后拉起系统选择器）；余形预览为原文、取消钮默认。
     * 建面链照官：MyXpopup.Builder + borderRadius(dpToPx 16) +
     * autoOpenSoftInput(false) + autoFocusEditText(false) +
     * isDestroyOnDismiss(true) + asInputMyConfirm(...,R.layout
     * .xpopup_confirm_input, cancelText).show()，回 true。
     * 确认钮回调＝官 lambda$4：清空剪贴板后 HeavyTaskUtil 后台跑 lambda$3
     * （JSEngine.evalJS 接咱家 HkJsRuntime.getInstance().evalJS、结果经
     * ThreadTool.runOnUI 回 lambda$2 分发——该法已在本文件落地）。
     * ClipboardUtil.copyToClipboardForce(ctx,"") 的 ClearDetectedEvent 支
     * 咱家无该事件类（HkWebkitHolder 已记名同判），剪贴板清空本效由本文件
     * 既有 copyToClipboardForce 私有体承接。
     */
    public static boolean couldCloudImport(Context context, String str) {
        Context topContext = getCurrentTopActivity(context);
        if (str != null && str.contains(cloudImport)) {
            String[] split = str.split(cloudImport);
            if (split.length >= 1 && !split[0].startsWith("[") && !split[0].startsWith("{")) {
                for (String tag : cloudImportFilterTags) {
                    if (split[0].contains("￥" + tag + "￥")) {
                        return false;
                    }
                }
                OnCancelListener onCancelListener;
                String preview;
                String cancelText;
                if (str.getBytes().length > 51200) {
                    final String fullText = str;
                    onCancelListener = () -> lambda$couldCloudImport$1(topContext, fullText);
                    preview = str.substring(0, Math.min(2000, str.length()))
                            + "...字符过多，请点击完整口令按钮查看全部内容";
                    cancelText = "完整口令";
                } else {
                    onCancelListener = null;
                    preview = str;
                    cancelText = null;
                }
                final Context ctx = topContext;
                OnInputConfirmListener onInputConfirmListener =
                        text -> lambda$couldCloudImport$4(ctx, split, text);
                new MyXpopup().Builder(topContext)
                        .borderRadius((float) DisplayUtil.dpToPx(topContext, 16))
                        .autoOpenSoftInput(Boolean.valueOf(false))
                        .autoFocusEditText(false)
                        .isDestroyOnDismiss(true)
                        .asInputMyConfirm("云口令导入提示",
                                "确定导入以下云口令？非正常渠道获取的云口令谨慎导入",
                                preview, "云口令", onInputConfirmListener, onCancelListener,
                                R.layout.xpopup_confirm_input, cancelText)
                        .show();
                return true;
            }
        }
        return false;
    }

    /**
     * 官 lambda$couldCloudImport$4(Context,String[],String)（smali:3903-3919，
     * .line 401-402）：官本体把入参文本覆写为 ""（即不采纳弹窗内编辑文本），
     * copyToClipboardForce(ctx,"") 清剪贴板后，HeavyTaskUtil.executeNewTask
     * 后台执行 lambda$3。咱家 executeNewTask 在 HeavyTaskUtil 已落地。
     */
    private static void lambda$couldCloudImport$4(Context context, String[] split, String text) {
        copyToClipboardForce(context, "");
        HeavyTaskUtil.executeNewTask(() -> lambda$couldCloudImport$3(split));
    }

    /**
     * 官 lambda$couldCloudImport$3(String[])（smali:3848-3901，.line 403-409）：
     * 规则名取 split[0] 的最后一个换行段（无换行取整段），
     * JSEngine.getInstance().evalJS(split[1], name) 求值，结果经
     * ThreadTool.runOnUI 回 lambda$2 分发。JSEngine 单例接咱家
     * HkJsRuntime.getInstance()（其类注自记为官 getInstance 对位）。
     */
    private static void lambda$couldCloudImport$3(String[] split) {
        String name = split[0];
        String[] lines = split[0].split("\n");
        if (lines.length > 1) {
            name = lines[lines.length - 1];
        }
        String result = HkJsRuntime.getInstance().evalJS(split[1], name);
        ThreadTool.INSTANCE.runOnUI(() -> lambda$couldCloudImport$2(result));
    }

    /**
     * 官 lambda$couldCloudImport$1(Context,String)（smali:3525-3614，
     * .line 384-390）：全文写 UriUtils.getRootDir + "/_cache/_cloud_import.txt"，
     * 提示「文件过大，请使用第三方APP查看」，再 ShareUtil
     * .findToDealFileByPath 拉起查看；IOException 时 Timber.e 并提示
     * 「出错：」+消息。咱家 FileUtil.stringToFile 本体已吞异常，官 catch 支
     * 在此以 Exception 形保留（findToDealFileByPath 的运行时异常同入此支）；
     * getRootDir/findToDealFileByPath 按官公式在本文件内联（见下两法）。
     */
    private static void lambda$couldCloudImport$1(Context context, String str) {
        try {
            String path = getRootDir(context) + File.separator + "_cache"
                    + File.separator + "_cloud_import.txt";
            FileUtil.stringToFile(str, path);
            ToastMgr.shortBottomCenter(context, "文件过大，请使用第三方APP查看");
            findToDealFileByPath(context, path);
        } catch (Exception e) {
            Timber.e(e);
            ToastMgr.shortBottomCenter(context, "出错：" + e.getMessage());
        }
    }

    /**
     * 官 UriUtils.getRootDir（UriUtils.smali，官行 202-205）公式内联（与
     * MoreSettingOfficer 批同款）：getExternalFilesDir(DOCUMENTS)、
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
     * 官 ShareUtil.findToDealFileByPath（ShareUtil.smali，.line 596-608）内联：
     * 路径为空提示「此链接有问题，不过我们为您复制到了剪贴板」并复制后回；
     * 余组 ACTION_VIEW 意图（flags=1 授读权限）、FileProvider 取 uri
     * （官 authority 取 R.string.authority＝"com.example.hikerview.provider"，
     * 即「包名.provider」公式，咱家按同公式取本包名）、setData 后经
     * ShareUtil.findChooser 拉起——findChooser 终效与咱家 HkJsRuntime 既有
     * 转写同款：Intent.createChooser(intent,"请选择应用") 后 startActivity
     * （官 UriTool 过滤选择器支咱家无该类，按既有先例记名）。
     * 本调用点路径恒为 getRootDir 拼固定文件名、非空，空支不可达、照官保留。
     */
    private static void findToDealFileByPath(Context context, String path) {
        if (TextUtils.isEmpty(path)) {
            ToastMgr.shortBottomCenter(context, "此链接有问题，不过我们为您复制到了剪贴板");
            copyToClipboardForce(context, path);
            return;
        }
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", new File(path));
        intent.setData(uri);
        context.startActivity(Intent.createChooser(intent, "请选择应用"));
    }

    /**
     * 官 getCurrentTopActivity(Context)（smali:1612-1831）的可落支转写：
     * 首支 FilmListActivity 接咱家等价宿主 HkListActivity（其 isOnPause 即
     * 官 FilmListActivity.isOnPause 的转写）——是该活动且未 onPause 直回；
     * 官余支（RuleWindowActivity、Application.getHomeActivity 的
     * MainActivity、VideoPlayerActivity 旗标支）咱家无对应宿主类，落官末支
     * ActivityManager.getCurrentActivity()（官 .line 301-302 本体）。
     */
    private static Context getCurrentTopActivity(Context context) {
        if (context instanceof HkListActivity && !((HkListActivity) context).isOnPause()) {
            return context;
        }
        return ActivityManager.getInstance().getCurrentActivity();
    }

    /** 官嵌套接口 OnOkWithAdUrlsListener（AutoImportHelper$OnOkWithAdUrlsListener.smali：ok(Z)）。 */
    public interface OnOkWithAdUrlsListener {
        void ok(boolean z);
    }

    /** 官嵌套接口 OnOkListener（AutoImportHelper$OnOkListener.smali：ok(String)，官为私有接口）。 */
    private interface OnOkListener {
        void ok(String str);
    }

    /**
     * 官 couldThemeImport(Context,String)（smali:722-806，.line 336-357）照官
     * 转写：SettingConfig.homeName 为「我的主页」且文本含「主题￥sharePaste￥」
     * /「￥my_home_single￥」/「￥my_home_theme￥」任一时，XPopup 确认
     * 「主题导入提示／检测到可导入的主页主题口令，是否立即导入？」
     * （borderRadius=dpToPx(16)），确认走 lambda$0，回 true；余回 false。
     */
    public static boolean couldThemeImport(Context context, String str) {
        if (str != null && "我的主页".equals(SettingConfig.homeName)
                && (str.contains("主题￥sharePaste￥") || str.contains("￥my_home_single￥")
                || str.contains("￥my_home_theme￥"))) {
            OnConfirmListener listener = () -> lambda$couldThemeImport$0(str, context);
            new XPopup.Builder(context)
                    .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                    .asConfirm("主题导入提示", "检测到可导入的主页主题口令，是否立即导入？", listener)
                    .show();
            return true;
        }
        return false;
    }

    /**
     * 官 lambda$couldThemeImport$0(String,Context)（smali:3921-4051，
     * .line 340-355）：JSEngine.putVar("我的主页@input", 文本)（接咱家
     * HkJsRuntime.getInstance().putVar）；ClipboardUtil.copyToClipboard
     * (ctx,"") 清剪贴板（终效接本文件 copyToClipboardForce 私有体）；
     * 后段 try：读 assets home.json（FilesInAppUtil.getAssetsString 接
     * 本文件同名内联体，宿主 Application.getContext() 接 App.get()），
     * 解析规则（官 ArticleListRule 接 HkRule、Gson 口径），find_rule 换官
     * 固定串、url 尾加 "#noHistory##noRecordHistory#"，以
     * HkListActivity.putTempRule（官 FilmListActivity.putTempRule 对位）
     * 携 JSON＋title「主页设置-导入」＋parentTitle/parentUrl 开页；
     * 异常 Timber.e 并 ToastMgr.shortCenter 提示消息。
     */
    private static void lambda$couldThemeImport$0(String str, Context context) {
        HkJsRuntime.getInstance().putVar("我的主页@input", str);
        copyToClipboardForce(context, "");
        try {
            String homeJson = getAssetsString(App.get(), "home.json");
            HkRule rule = new Gson().fromJson(homeJson, HkRule.class);
            rule.setFindRule("js:\neval(fetch(\"hiker://assets/home.js\"));\n\nvar _urgent=false;\nsetResult(HikerHome.load(\"import\",_urgent));");
            rule.setUrl(rule.getUrl() + "#noHistory##noRecordHistory#");
            Intent intent = new Intent(context, HkListActivity.class);
            HkListActivity.putTempRule(intent, new Gson().toJson(rule));
            intent.putExtra("title", "主页设置-导入");
            intent.putExtra("parentTitle", rule.getTitle());
            intent.putExtra("parentUrl", rule.getUrl());
            context.startActivity(intent);
        } catch (Exception e) {
            Timber.e(e);
            ToastMgr.shortCenter(context, e.getMessage());
        }
    }

    /**
     * 官 FilesInAppUtil.getAssetsString（FilesInAppUtil.smali，官行
     * 173-193）内联（与 MoreSettingOfficer 批同款）：逐行读 assets、
     * "\n" 连接（首行无前缀），Throwable 只 Timber.e、回已拼部分；
     * 官本体不关流，照官。
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

    /**
     * 官 showWithAdUrlsDialog(Context,String,OnOkWithAdUrlsListener)
     * （smali:7911-8035，.line 1537-1563）＋lambda$44-$47（smali:6400-6526）
     * 照官转写：充气官布局 view_dialog_import_with_block_urls（③团照官
     * 新建于 res/layout，同批补 button_layer_green drawable），标题
     * TextView 置文案与 tag "with"；AlertDialog「温馨提示」＋setView＋
     * setCancelable(true)，正钮「确定」回调（lambda$44：Log.d 后以
     * tag=="with" 调 listener.ok 并 dismiss——官本体在回调后再 dismiss，
     * AlertDialog 正钮默认亦关窗，终效同）、负钮「取消」只 dismiss
     * （lambda$45）；两切换钮点击（lambda$46/$47）互换 tag 与
     * button_layer_green/button_layer 背景；最终经咱家已落的
     * DialogUtil.INSTANCE.showAsCard 展示（官同位调用）。
     */
    public static void showWithAdUrlsDialog(Context context, String title, OnOkWithAdUrlsListener listener) {
        View view = LayoutInflater.from(context).inflate(R.layout.view_dialog_import_with_block_urls, null, false);
        TextView titleView = view.findViewById(R.id.import_rule_title);
        titleView.setText(title);
        titleView.setTag("with");
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("温馨提示")
                .setView(view)
                .setCancelable(true)
                .setPositiveButton("确定", (d, which) -> {
                    String tag = (String) titleView.getTag();
                    Log.d("AutoImportHelper", "showWithAdUrlsDialog: " + tag);
                    listener.ok("with".equals(tag));
                    d.dismiss();
                })
                .setNegativeButton("取消", (d, which) -> d.dismiss())
                .create();
        Button importBtn = view.findViewById(R.id.import_rule_btn);
        Button notImportBtn = view.findViewById(R.id.not_import_rule_btn);
        importBtn.setOnClickListener(v -> {
            titleView.setTag("with");
            importBtn.setBackground(context.getResources().getDrawable(R.drawable.button_layer_green));
            notImportBtn.setBackground(context.getResources().getDrawable(R.drawable.button_layer));
        });
        notImportBtn.setOnClickListener(v -> {
            titleView.setTag("without");
            importBtn.setBackground(context.getResources().getDrawable(R.drawable.button_layer));
            notImportBtn.setBackground(context.getResources().getDrawable(R.drawable.button_layer_green));
        });
        DialogUtil.INSTANCE.showAsCard(context, dialog);
    }

    /**
     * 官 getCommand(String,String,String)（AutoImportHelper.smali:807，.line 122-187）：
     * 按类型 tag 选分享头文案，prefix 非空时拼「，prefix」，尾拼「￥tag￥rule」。
     * tag 为 null 时官首行 hashCode() 即抛 NPE，Java switch 同形，不另加空守卫。
     */
    public static String getCommand(String prefix, String rule, String tag) {
        String head;
        switch (tag) {
            case "xiu_tan":
                head = "海阔视界规则分享，当前分享的是：音视频嗅探规则";
                break;
            case "ad_url_rule":
                head = "海阔视界规则分享，当前分享的是：广告网址拦截";
                break;
            case "page_detail":
                head = "海阔视界规则分享，当前分享的是：二级页面详情";
                break;
            case "js_url":
                head = "海阔视界规则分享，当前分享的是：网页插件";
                break;
            case "ad_subscribe_url":
                head = "海阔视界规则分享，当前分享的是：广告拦截订阅";
                break;
            case "search_engine_url":
                head = "海阔视界规则分享，当前分享的是：搜索引擎合集";
                break;
            case "file_url":
                head = "海阔视界规则分享，当前分享的是：本地文件";
                break;
            case "search_engine_v2":
                head = "海阔视界规则分享，当前分享的是：搜索引擎";
                break;
            case "home_sub":
                head = "海阔视界规则分享，当前分享的是：合集规则订阅";
                break;
            case "ad_block_url":
                head = "海阔视界规则分享，当前分享的是：广告元素拦截";
                break;
            case "publish":
                head = "海阔视界规则分享，当前分享的是：提交云仓库规则";
                break;
            case "require_url":
                head = "海阔视界规则分享，当前分享的是：远程依赖";
                break;
            case "home_rule_v2":
            case "home_rule":
                head = "海阔视界规则分享，当前分享的是：小程序";
                break;
            case "home_rule_url":
                head = "海阔视界规则分享，当前分享的是：小程序合集";
                break;
            case "projection_screen":
                head = "海阔视界规则分享，当前分享的是：自定义投屏规则";
                break;
            case "publish_account":
                head = "海阔视界规则分享，当前分享的是：提交云仓库账号密码";
                break;
            case "bookmark_url":
                head = "海阔视界规则分享，当前分享的是：书签合集";
                break;
            case "bookmark":
                head = "海阔视界规则分享，当前分享的是：书签规则";
                break;
            case "web-proxy":
                head = "海阔视界规则分享，当前分享的是：浏览器代理规则";
                break;
            default:
                head = "海阔视界规则分享，当前分享的是：";
                break;
        }
        if (StringUtil.isNotEmpty(prefix)) {
            head = head + "，" + prefix;
        }
        return head + "￥" + tag + "￥" + rule;
    }

    /**
     * 官 getCommandText(Context,String,String)（smali:1371，.line 203-208）：
     * 先取分享前缀，违禁词命中则提示并回 null，否则回 getCommand。
     * 前缀读法照官 PreferenceMgr.getString(Context,String,String) 本体
     * （PreferenceMgr.smali：默认文件 FILE_NAME="share_data" →
     * getSharedPreferences("share_data",0).getString(key,def)）直落框架调用，
     * 与咱家 HkJsRuntime 读 share_data 的既有口径一致。
     */
    public static String getCommandText(Context context, String rule, String tag) {
        String prefix = context.getSharedPreferences("share_data", Context.MODE_PRIVATE)
                .getString("shareRulePrefix", "");
        if (hasFilterWord(rule)) {
            ToastMgr.shortBottomCenter(context, "规则含有违禁词，禁止分享");
            return null;
        }
        return getCommand(prefix, rule, tag);
    }

    /**
     * 官 shareWithCommand(Context,String,String)（smali:7863，.line 191-199）：
     * 同 getCommandText 取前缀/查违禁词（命中只提示即回），生成口令后存
     * shareRule、写剪贴板（showToast=false）、提示「规则已复制到剪贴板」。
     */
    public static void shareWithCommand(Context context, String rule, String tag) {
        String prefix = context.getSharedPreferences("share_data", Context.MODE_PRIVATE)
                .getString("shareRulePrefix", "");
        if (hasFilterWord(rule)) {
            ToastMgr.shortBottomCenter(context, "规则含有违禁词，禁止分享");
            return;
        }
        String command = getCommand(prefix, rule, tag);
        setShareRule(command);
        copyToClipboardForce(context, command);
        ToastMgr.shortBottomCenter(context, "规则已复制到剪贴板");
    }

    /**
     * 官 ClipboardUtil.copyToClipboardForce(Context,String,boolean=false) 在本调用点的
     * 终效直落（ClipboardUtil.smali:169 起及 lambda$copyToClipboardForce$4:181-189）：
     * 官 SDK≥29 支经游离 EditText 的 performClick 同步回调同段代码、SDK&lt;29 支直走
     * 同段代码，终效均为 ClipboardManager.setPrimaryClip(ClipData "Label")；
     * showToast=false 故「复制成功」提示支不触发。官另有「文本为空发
     * ClearDetectedEvent」支：本调用点文本恒为 getCommand 产物（必含
     * 「￥tag￥」段、恒非空），该支在此不可达，记名不落（咱家亦无 EventBus/
     * ClearDetectedEvent）。咱家既有同判先例：HkDetailBridge.copyToClipboardForce
     * （HkDetailBridge.java「照 ClipboardUtil.copyToClipboardForce：直接写主剪贴板」）。
     */
    private static void copyToClipboardForce(Context context, String text) {
        ClipboardManager clipboardManager =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText("Label", text);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(clipData);
        }
    }

    /**
     * 官 FilterUtil.hasFilterWord(String) 本体（FilterUtil.smali：.locals 0、
     * 恒 return false 的桩）。咱家既有同判先例：HkHomeSubService.hasFilterWord、
     * HkDlanController 同注。此为官本体原样，不是自设默认假值。
     */
    private static boolean hasFilterWord(String str) {
        return false;
    }

    /** 官 getRealRule(String)（smali:1832，.line 212-219）：空回原串；按￥切恰 3 段回第 3 段，否则回原串。 */
    public static String getRealRule(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        String[] split = str.split("￥");
        if (split.length != 3) {
            return str;
        }
        return split[2];
    }

    /** 官 getShareRule()（smali:1870，.line 1528）。 */
    public static String getShareRule() {
        return shareRule;
    }

    /** 官 setShareRule(String)（smali:7854，.line 1532）。 */
    public static void setShareRule(String str) {
        shareRule = str;
    }

    /** 官支：HttpParser.getFirstPageUrl(replaceOnce(str, SCHEME_WEB, ""))，
     *  经星辰已转写的同名口径（HkDetailBridge.getFirstPageUrl）。 */
    private static String firstPageUrl(String str) {
        String body = str.substring(StringUtil.SCHEME_WEB.length());
        return new HkDetailBridge().getFirstPageUrl(body);
    }
}
