package com.example.hikerview.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Base64;
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
import com.example.hikerview.ui.rules.ImportSubscribeCheckPopup;
import com.example.hikerview.ui.rules.model.DetailPageRule;
import com.example.hikerview.ui.setting.model.SettingConfig;
import com.example.hikerview.ui.view.colorDialog.PromptDialog;
import com.example.hikerview.ui.view.popup.ImportRulesPopup;
import com.example.hikerview.ui.view.popup.InputPopup;
import com.example.hikerview.ui.view.popup.MyXpopup;
import com.example.hikerview.utils.view.DialogUtil;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.HkAccountPwd;
import com.fongmi.android.tv.api.hk.HkHomeSubService;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkPublishHelper;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.api.hk.event.rule.OnArticleListRuleChangedEvent;
import com.fongmi.android.tv.api.hk.util.HkCodeUtil;
import com.fongmi.android.tv.ui.hk.HkAdUrlBlocker;
import com.fongmi.android.tv.ui.hk.HkColType;
import com.fongmi.android.tv.ui.hk.HkDetailBridge;
import com.fongmi.android.tv.ui.hk.HkListActivity;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.interfaces.OnCancelListener;
import com.lxj.xpopup.interfaces.OnConfirmListener;
import com.lxj.xpopup.interfaces.OnInputConfirmListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.greenrobot.eventbus.EventBus;

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
 * 口径、与 c1 冲突处以 c1 为准）。其余十法停手记名（见各缺口回执）：checkText 赖 couldThemeImport/
 * RuleImporterManager.parse，无等价物（HkDetailBridge.hooks
 * 的 importRuleText 只覆盖规则 JSON/云口令/JS 子集，无主题支，
 * 不等价）；importBookmarkByStr 赖 BookmarkModel.addByList＋GlobalDialogActivity，
 * 皆无（Bookmark 在咱家只是纯模型、无 LitePal 存取）；importJSPlugin 赖
 * JSManager（咱家无用户脚本管理器，HkWebkitHolder 已记名）；importRulesByTextWithDialog 三支分别赖
 * RuleChecker/BackupUtil/LitePal.deleteAll/checkText——其中 BackupUtil 与
 * ImportRulesPopup 已于 136b 簇1 落、importRulesByTextWithDialog 随之照官
 * 落地（见该法注），余支仍缺或已停手；
 * showWithAdUrlsDialog 已于③团照官新建布局后落地（见法注）。
 *
 * <p>2026-11-14 136 批续补（W5 路）：checkAutoText×2＋realCheckAutoText×2 已落
 * （PromptDialog 等价件新建 ui/view/colorDialog/PromptDialog、底层接 HkDialogs；
 * 19 标签支逐支处置见 realCheckAutoText 法注：5 支终点在树照官接上、
 * search_engine_v2 照官下线提示、publish_account 照官出 InputPopup 表单而终点
 * 停手、其余 12 支终点不存在照官弹窗形态回官同形失败）；lambda$couldCloudImport$2
 * 的 checkText 尾步补 checkAutoText（官 checkText 的组成尾步），￥tag￥ 口令形
 * 结果不再假报「云口令导入成功」；importRuleByRuleTextList 已落（落盘换
 * HkRuleManager 现行面、GlobalDialogActivity/LoadingDismissEvent 加载面不建，
 * 见法注）。importBookmarkByStr 停手原因 136b 簇1 更新：书签存取整层
 * （BookmarkModel/addByList＋BookmarkRefreshEvent/LoadingDismissEvent）
 * 已于 2026-11-14 21:50 被他亲口取消整建、终点层他亲口不要，不再按缺件追。
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
                if (!z) {
                    // 官 checkText 的尾步即 checkAutoText（19 标签分发，本批已落）：
                    // ￥tag￥ 口令形结果在此认领（导入或官同形失败），认领后不再
                    // 落尾部成功提示——旧形此路回 false 后仍报「云口令导入成功」
                    // 是假成功（￥home_rule_url￥/￥ad_url_rule￥ 口令实证），136 批修。
                    z = checkAutoText(currentActivity, str);
                }
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

    // —— 导入链 A3/A6（2026-11-14 136 批，官源 AutoImportHelper.smali） ——

    /** 官 checkAutoText(Context,String)（smali:121，.line 319-321）：try{ realCheckAutoText }catch(Exception e){ Timber.e(e); return false; }，照官原样。 */
    public static boolean checkAutoText(Context context, String str) {
        try {
            return realCheckAutoText(context, str);
        } catch (Exception e) {
            Timber.e(e);
            return false;
        }
    }

    /** 官 checkAutoText(Context,String,boolean)（smali:145，.line 328-330）：同上、三参形。 */
    public static boolean checkAutoText(Context context, String str, boolean z) {
        try {
            return realCheckAutoText(context, str, z);
        } catch (Exception e) {
            Timber.e(e);
            return false;
        }
    }

    /** 官 realCheckAutoText(Context,String)（smali:6527，.line 462）：直调三参版、showDialog=true。 */
    public static boolean realCheckAutoText(Context context, String str) {
        return realCheckAutoText(context, str, true);
    }

    /**
     * 官 realCheckAutoText(Context,String,boolean)（smali:6540，.line 466-918）照官转写：
     * getCurrentTopActivity→trim→「方圆」头走 checkAutoTextByFangYuan→裸 JSON
     * （{...}＋含 col_type/find_rule）包成 home_rule 口令→按 ￥ 切段（&lt;3 段回 false）、
     * sss[1] 按 tag 分发 19 支，每支建 PromptDialog（DIALOG_TYPE_INFO/动画开/标题
     * 「温馨提示」）挂各自 lambda（弹窗件接本批等价 {@link PromptDialog}）。
     *
     * <p>逐支咱家落点：home_rule/home_rule_v2（λ16/λ17）、home_rule_url（λ18＋$1）、
     * require_url（λ24/λ23）、ad_url_rule（λ7）、file_url（λ22＋importFileByUrl＋$4）
     * 五支终点在树、照官接上；search_engine_v2 支官本体即一句下线提示、照官直落。
     * 2026-11-14 136b 簇1 续补三支：publish_account（λ9 落 HkPublishHelper/
     * HkAccountPwd、toast「账号密码已保存」）、home_sub（λ11 落
     * ImportSubscribeCheckPopup＋HkHomeSubService.addSubRecords）、page_detail
     * （λ28 落 DetailPageRule＋HkListActivity.putTempRule）。其余 9 支逐支记名在
     * 各 case 注（bookmark/bookmark_url/js_url 三支终点层他亲口不要、余六支
     * 终点整层不存在），照官弹窗形态出示、确认时回官同形失败（ToastMgr
     * .shortCenter「出错：…」，与官各 lambda 的 catch 出口同形），不假报成功。
     * 方圆支见 checkAutoTextByFangYuan 注。
     */
    public static boolean realCheckAutoText(Context context, String str, boolean showDialog) {
        Context top = getCurrentTopActivity(context);
        String text = str.trim();
        if (text.startsWith("方圆")) {
            return checkAutoTextByFangYuan(top, text);
        }
        String trimmed = StringUtil.trimBlanks(text);
        if (trimmed.startsWith("{") && trimmed.endsWith("}")
                && trimmed.contains("col_type") && trimmed.contains("find_rule")) {
            text = "海阔视界规则分享，当前分享的是：小程序￥home_rule￥" + trimmed;
        }
        String[] sss = text.split("￥");
        if (sss.length < 3) {
            return false;
        }
        switch (sss[1]) {
            case "home_rule":
            case "home_rule_v2": {
                // 官 pswitch_6：λ16 为导入本体、λ17 为弹窗回调（dismiss 后跑 λ16）；
                // showDialog=false 时官直跑 λ16、不弹窗，照官。
                Runnable run = () -> lambda$realCheckAutoText$16(sss, top);
                if (showDialog) {
                    showImportPrompt(top, "剪贴板检测到小程序规则，是否立即导入？", "立即导入",
                            dialog -> run.run());
                } else {
                    run.run();
                }
                return true;
            }
            case "home_rule_url":
                showImportPrompt(top, "剪贴板检测到小程序远程规则（合集/单个规则），是否立即导入？", "立即导入",
                        dialog -> lambda$realCheckAutoText$18(sss, top));
                return true;
            case "require_url":
                showImportPrompt(top, "剪贴板检测到远程依赖更新请求，是否立即更新？", "立即更新",
                        dialog -> lambda$realCheckAutoText$24(sss, top));
                return true;
            case "ad_url_rule":
                showImportPrompt(top, "剪贴板检测到广告网址过滤规则", "查看",
                        dialog -> lambda$realCheckAutoText$7(top, sss));
                return true;
            case "file_url":
                showImportPrompt(top, "剪贴板检测到本地文件导入请求，导入后会覆盖规则中的本地文件，是否立即导入？", "立即导入",
                        dialog -> lambda$realCheckAutoText$22(sss, top));
                return true;
            case "search_engine_v2":
                // 官 pswitch_b 本体：无弹窗、一句下线提示后回 true，照官直落。
                ToastMgr.shortBottomCenter(top, "搜索引擎页面已下线，请用无解析规则的小程序规则代替！");
                return true;
            case "publish_account": {
                // 官 pswitch_3：sss[2] 按 "@" 切恰 2 段才出 InputPopup 表单
                // （bind 标题/两提示/两预填照官），确认回调 λ9 落
                // HkPublishHelper.savePublishAccount（136b 簇1 补齐，见 λ9 法注）。
                String[] parts = sss[2].split("@");
                if (parts.length == 2) {
                    InputPopup popup = new InputPopup(top).bind("设置云仓库账号密码",
                            "MY_ACCOUNT", parts[0], "MY_PASSWORD", parts[1],
                            (account, password) -> lambda$realCheckAutoText$9(top, account, password));
                    new XPopup.Builder(top)
                            .borderRadius((float) DisplayUtil.dpToPx(top, 16))
                            .asCustom(popup)
                            .show();
                } else {
                    // 官此形 switch 落穿进 home_sub 支弹窗，照官同形走 home_sub 支。
                    showHomeSubPrompt(top, sss);
                }
                return true;
            }
            case "bookmark":
                // 停手记名：终点层他亲口不要（2026-11-14 21:50 取消书签整建：
                // BookmarkActivity 与书签存取层不建），保留官文案形。
                showUnsupportedPrompt(top, "剪贴板检测到书签信息", "查看");
                return true;
            case "bookmark_url":
                // 停手记名：终点层他亲口不要（同 bookmark：书签合集导入层不建），
                // 保留官文案形。ImportRulesPopup/BackupUtil 本体已于 136b 簇1 落，
                // 缺的只是他不要的书签层。
                showUnsupportedPrompt(top, "剪贴板检测到书签合集规则，是否立即导入？", "立即导入");
                return true;
            case "projection_screen":
                // 停手记名：终点 PlayerOfficerKt.showProjectionScreenView＋
                // ProjectionScreenRule 整层不存在（HkDlanController 是播放侧
                // DLNA、非投屏规则编辑/保存面），保留官文案形。
                showUnsupportedPrompt(top, "剪贴板检测到自定义投屏规则，是否立即导入？", "立即导入");
                return true;
            case "publish":
                // 停手记名：终点 PublishCodeEditActivity 编辑页整层不存在；
                // 发布存取面 HkPublishHelper 已于 136b 簇1 落，缺的是编辑页
                // （含其清单登记），保留官文案形。
                showUnsupportedPrompt(top, "剪贴板检测到提交云仓库规则", "立即查看");
                return true;
            case "ad_block_url":
                // 停手记名：终点 DialogBuilder.showInputConfirm＋AdBlockModel
                // .saveRule（AdBlockRule 存储）整层不存在（HkAdUrlBlocker 是
                // 网址拦截表、非元素拦截规则面），保留官文案形。
                showUnsupportedPrompt(top, "剪贴板检测到广告元素拦截规则", "立即导入");
                return true;
            case "home_sub":
                showHomeSubPrompt(top, sss);
                return true;
            case "search_engine_url":
                // 停手记名：终点 SearchRuleJO/SearchEngineDO 独立存储层不存在
                // （HkSearchEngine 由规则库派生、无独立存取面）；ImportRulesPopup
                // 本体已于 136b 簇1 落，缺的是搜索引擎存储层，保留官文案形。
                showUnsupportedPrompt(top, "剪贴板检测到搜索引擎合集规则，是否立即导入？", "立即导入");
                return true;
            case "ad_subscribe_url":
                // 停手记名：终点 MoreSettingActivity.addAdSubRule＋AdUrlSubscribe
                // 订阅存取/更新整层不存在（HkAdUrlBlocker 无订阅地址面），
                // 保留官文案形。
                showUnsupportedPrompt(top, "剪贴板检测到广告拦截订阅地址，是否查看详情", "查看详情");
                return true;
            case "js_url":
                // 停手记名：终点层他亲口不要（2026-11-14 21:50 取消 JS 插件
                // 管理整建：JSManager/插件存储层不建），保留官文案形。
                showUnsupportedPrompt(top, "剪贴板检测到网页插件规则信息，是否立即导入？", "立即导入");
                return true;
            case "page_detail":
                showImportPrompt(top, "剪贴板检测到二级页面详情口令，是否立即查看？", "立即查看",
                        dialog -> lambda$realCheckAutoText$28(top, sss));
                return true;
            case "xiu_tan":
                // 停手记名：终点 VideoRuleListActivity 整层不存在（UrlDetector
                // 有嗅探规则存储、无此列表页），保留官文案形。
                showUnsupportedPrompt(top, "剪贴板检测到音视频嗅探规则", "查看");
                return true;
            case "web-proxy":
                // 停手记名：终点 BrowserOfficer.showAddProxyInput 及其
                // showProxyRuleInput/showProxyRuleView 编辑面整层不存在
                // （HkBrowserProxy 只有注册表＋文件读、无编辑/保存面），
                // 保留官文案形。
                showUnsupportedPrompt(top, "剪贴板检测到浏览器代理规则，是否立即导入？", "立即导入");
                return true;
            default:
                return false;
        }
    }

    /**
     * 官 checkAutoTextByFangYuan(Context,String)（smali:169 起）：按 ￥ 切段、&lt;3 段
     * 回 false、sss[1] 非 "source" 回 false；余两形（一形 PromptDialog 挂
     * lambda$42、一形 MovieInfoUse→SearchRuleJO→getCommand(search_engine_v2)
     * 再回 checkAutoText）终点同赖 SearchRuleJO/SearchEngineDO/MovieInfoUse，
     * 三类咱家皆无。咱家落官门形：过门后照官弹窗形态（文案「剪贴板检测到搜索
     * 引擎规则，是否立即导入？」/钮「立即导入」）出示、确认回官同形失败提示、
     * 回 true（官过门恒回 true）；未过门回 false 与官同。
     */
    private static boolean checkAutoTextByFangYuan(Context context, String str) {
        String[] sss = str.split("￥");
        if (sss.length < 3) {
            return false;
        }
        if (!"source".equals(sss[1])) {
            return false;
        }
        showUnsupportedPrompt(context, "剪贴板检测到搜索引擎规则，是否立即导入？", "立即导入");
        return true;
    }

    /** 官各支 PromptDialog 的统一建面形（DIALOG_TYPE_INFO/动画开/标题「温馨提示」），接本批等价 PromptDialog。 */
    private static void showImportPrompt(Context context, String content, String positiveText,
                                         PromptDialog.OnPositiveListener listener) {
        new PromptDialog(context)
                .setDialogType(PromptDialog.DIALOG_TYPE_INFO)
                .setAnimationEnable(true)
                .setTitleText("温馨提示")
                .setContentText(content)
                .setPositiveListener(positiveText, listener)
                .show();
    }

    /**
     * 终点不存在支的官同形失败面：弹窗形态照官各支（文案/钮文案逐支照官），
     * 确认后回官各 lambda catch 的同形出口——ToastMgr.shortCenter「出错：…」，
     * 不导入、不报成功。逐支终点缺失记名在 realCheckAutoText 各 case 注。
     */
    private static void showUnsupportedPrompt(Context context, String content, String positiveText) {
        showImportPrompt(context, content, positiveText,
                dialog -> ToastMgr.shortCenter(context, "出错：该口令类型暂不支持导入"));
    }

    /**
     * 官 lambda$realCheckAutoText$16(String[],Context)（smali:4931，.line 661-670）：
     * 段数≠3 时尾段以 ￥ 拼回、=3 取 sss[2]，清剪贴板后 importRuleByRuleText；
     * 异常 Timber＋shortCenter「出错：」＋消息。咱家 importRuleByRuleText 的等价路
     * ＝HkDetailBridge.hooks.checkText（宿主 HkListActivity.importRuleText：
     * HkRuleManager.unwrapShareText 剥壳＋parseJsonList 校验落盘＋问打开，与官
     * RuleChecker→importRuleByRuleTextNow 的落盘终效同路）；回 false 时官路为
     * 规则校验不过（importRuleByRuleTextNow 的「规则有误」形），此处以
     * 「导入失败」明示、不假报成功。
     */
    private static void lambda$realCheckAutoText$16(String[] sss, Context context) {
        try {
            String ruleText = sss.length == 3 ? sss[2]
                    : StringUtil.arrayToString(sss, 2, sss.length, "￥");
            copyToClipboardForce(context, "");
            Activity activity = activityOf(context);
            boolean ok = activity != null && HkDetailBridge.hooks.checkText(activity, ruleText);
            if (!ok) {
                ToastMgr.shortBottomCenter(context, "导入失败");
            }
        } catch (Exception e) {
            Timber.e(e);
            ToastMgr.shortCenter(context, "出错：" + e.getMessage());
        }
    }

    /**
     * 官 lambda$realCheckAutoText$18(String[],Context,PromptDialog)（smali:5015，
     * .line 694-719，弹窗 dismiss 由 PromptDialog 回调序承接）：段数≠3→「格式
     * 错误！」；sss[2] 空或不以 http/file/hiker:// 开头→「链接有误！」；余经
     * CodeUtil.get 拉取（接咱家 HkCodeUtil.get 同名转写），回调 $1：成功且非空
     * →importRulesByTextWithDialog（136b 簇1 起照官直落本文件同名法：单规则走
     * hooks.checkText、合集数组走 ImportRulesPopup 三支）、空→shortCenter
     * 「出错：文件内容为空」、失败→「导入失败，网络连接错误！」；尾清剪贴板照官。
     */
    private static void lambda$realCheckAutoText$18(String[] sss, Context context) {
        if (sss.length != 3) {
            ToastMgr.shortBottomCenter(context, "格式错误！");
            return;
        }
        String url = sss[2];
        if (StringUtil.isEmpty(url) || !(url.startsWith("http") || url.startsWith("file")
                || url.startsWith("hiker://"))) {
            ToastMgr.shortBottomCenter(context, "链接有误！");
            return;
        }
        HkCodeUtil.get(url, new HkCodeUtil.OnCodeGetListener() {
            @Override
            public void onSuccess(String result) {
                if (StringUtil.isEmpty(result)) {
                    ToastMgr.shortCenter(context, "出错：文件内容为空");
                    return;
                }
                importRulesByTextWithDialog(context, result, url);
            }

            @Override
            public void onFailure(int code, String msg) {
                ToastMgr.shortBottomCenter(context, "导入失败，网络连接错误！");
            }
        });
        copyToClipboardForce(context, "");
    }

    /**
     * 官 lambda$realCheckAutoText$24(String[],Context,PromptDialog)（smali:5393，
     * .line 781-787）：段数≠3→「格式错误！」；余清剪贴板后 HeavyTaskUtil 后台跑 λ23。
     * λ23（smali:5322，.line 789-793）：JSEngine.updateRequire(sss[2])（接咱家
     * HkJsRuntime.getInstance().updateRequire）→EventBus ToastMessage「更新成功」；
     * 异常 Timber＋ToastMessage「出错：」＋消息。咱家无 ToastMessage 事件类，其官
     * 消费终效即当前页 toast，此处以 ToastMgr 同文案直落（记名：事件面不建）。
     */
    private static void lambda$realCheckAutoText$24(String[] sss, Context context) {
        if (sss.length != 3) {
            ToastMgr.shortBottomCenter(context, "格式错误！");
            return;
        }
        copyToClipboardForce(context, "");
        HeavyTaskUtil.executeNewTask(() -> {
            Context toastContext = activityOf(context);
            try {
                HkJsRuntime.getInstance().updateRequire(sss[2]);
                ToastMgr.shortBottomCenter(toastContext, "更新成功");
            } catch (Exception e) {
                Timber.e(e);
                ToastMgr.shortBottomCenter(toastContext, "出错：" + e.getMessage());
            }
        });
    }

    /**
     * 官 lambda$realCheckAutoText$7(Context,String[],PromptDialog)（smali:6249，
     * .line 532-540）：清剪贴板后以 sss[2] 为 "data" 开 AdUrlListActivity，异常
     * Timber＋shortCenter「出错：」＋消息。咱家无 AdUrlListActivity，其数据终效
     * ＝AdUrlListActivity.addRules→addRulesForce：按 "&&" 切分→AdUrlBlocker
     * .addUrls→toast「成功导入N条规则」；咱家存取面接 HkAdUrlBlocker.addUrls
     * （同一全局拦截表），空数据照其 addRulesForce 的 λ12 回「规则不能为空」。
     * 活动页的逐条长度校验/列表展示面不随此支建（记名）。
     */
    private static void lambda$realCheckAutoText$7(Context context, String[] sss) {
        copyToClipboardForce(context, "");
        try {
            String data = sss[2];
            if (StringUtil.isEmpty(data)) {
                ToastMgr.shortBottomCenter(context, "规则不能为空");
                return;
            }
            int added = HkAdUrlBlocker.addUrls(Arrays.asList(data.split("&&")));
            ToastMgr.shortBottomCenter(context, "成功导入" + added + "条规则");
        } catch (Exception e) {
            Timber.e(e);
            ToastMgr.shortCenter(context, "出错：" + e.getMessage());
        }
    }

    /**
     * 官 lambda$realCheckAutoText$9(Context,String,String)（smali，.line 568-570）：
     * new AccountPwd(account,password)→PublishHelper.savePublishAccount→
     * ToastMgr.shortCenter「账号密码已保存」。咱家落点＝HkPublishHelper/
     * HkAccountPwd（136b 簇1 照官整建，存储走 hk 现行 SharedPreferences 面）。
     */
    private static void lambda$realCheckAutoText$9(Context context, String account, String password) {
        HkPublishHelper.savePublishAccount(new HkAccountPwd(account, password));
        ToastMgr.shortCenter(context, "账号密码已保存");
    }

    /** home_sub 支与 publish_account 落穿支共用的官形弹窗（文案/钮照官 home_sub 支）。 */
    private static void showHomeSubPrompt(Context context, String[] sss) {
        showImportPrompt(context, "剪贴板检测到合集规则订阅地址", "立即导入",
                dialog -> lambda$realCheckAutoText$11(context, sss));
    }

    /**
     * 官 lambda$realCheckAutoText$11(Context,String[],PromptDialog)（smali，
     * .line 585-594，弹窗 dismiss 由 PromptDialog 回调序承接）：清剪贴板后
     * sss[2] 按 "@@" 切，段数≠2→shortBottomCenter「格式有误」；余
     * HomeRulesSubService.addSubWithPopup(ctx, rules[0], rules[1], λ10)，
     * λ10（.line 593-594）：结果为真→shortBottomCenter「已添加订阅」。
     */
    private static void lambda$realCheckAutoText$11(Context context, String[] sss) {
        copyToClipboardForce(context, "");
        String[] rules = sss[2].split("@@");
        if (rules.length != 2) {
            ToastMgr.shortBottomCenter(context, "格式有误");
            return;
        }
        addSubWithPopup(context, rules[0], rules[1], ok -> {
            if (ok) {
                ToastMgr.shortBottomCenter(context, "已添加订阅");
            }
        });
    }

    /**
     * 官 HomeRulesSubService.addSubWithPopup(Context,String,String,Consumer)
     * （HomeRulesSubService.smali:90-99）转写：ImportSubscribeCheckPopup.bind
     * （「新建规则订阅」/「订阅名称」/名称预填/「订阅地址」/地址预填/
     * 「是否立即启用」/默认勾选）＋XPopup borderRadius=dpToPx(16) 出示；
     * 弹窗确认回调 lambda$addSubWithPopup$0＝addSubRecords(ctx,名称,地址,
     * 启用,自动更新删除) 后把布尔结果回 Consumer。咱家落点：弹窗本体本批
     * 已落（ui/rules/ImportSubscribeCheckPopup），存取走 HkHomeSubService
     * .addSubRecords（官同名法的咱家转写，校验/toast/立即更新俱在）。
     */
    private static void addSubWithPopup(Context context, String title, String url,
                                        Consumer<Boolean> consumer) {
        ImportSubscribeCheckPopup popup = new ImportSubscribeCheckPopup(context)
                .bind("新建规则订阅", "订阅名称", title, "订阅地址", url,
                        "是否立即启用", true,
                        (name, u, use, update) -> consumer.accept(
                                HkHomeSubService.addSubRecords(context, name, u, use, update)));
        new XPopup.Builder(context)
                .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                .asCustom(popup)
                .show();
    }

    /**
     * 官 lambda$realCheckAutoText$28(Context,String[],PromptDialog)（smali，
     * .line 840-857，弹窗 dismiss 由 PromptDialog 回调序承接）：清剪贴板后
     * sss[2] 按 "@@" 切，段数≠2→shortCenter「规则有误」；余段[1] Base64
     * 解码→DetailPageRule（官 fastjson parseObject，咱家 Gson 同字段等价），
     * 以 FilmListActivity.putTempRule(data)＋picUrl/title/fromHistory=true
     * 开页；异常 Timber＋shortCenter「出错：」＋消息。咱家开页落点＝
     * HkListActivity（官 FilmListActivity 等价宿主，putTempRule 同名同形）。
     */
    private static void lambda$realCheckAutoText$28(Context context, String[] sss) {
        try {
            copyToClipboardForce(context, "");
            String[] rules = sss[2].split("@@");
            if (rules.length != 2) {
                ToastMgr.shortCenter(context, "规则有误");
                return;
            }
            DetailPageRule pageRule = new Gson().fromJson(
                    new String(Base64.decode(rules[1], Base64.NO_WRAP)), DetailPageRule.class);
            Intent intent = new Intent(context, HkListActivity.class);
            HkListActivity.putTempRule(intent, pageRule.getData());
            intent.putExtra("picUrl", pageRule.getPicUrl());
            intent.putExtra("title", pageRule.getTitle());
            intent.putExtra("fromHistory", true);
            context.startActivity(intent);
        } catch (Exception e) {
            Timber.e(e);
            ToastMgr.shortCenter(context, "出错：" + e.getMessage());
        }
    }

    /**
     * 官 lambda$realCheckAutoText$22(String[],Context,PromptDialog)（smali:5253，
     * .line 760-770）：段数≠3→「格式错误！」；余清剪贴板后 try importFileByUrl
     * (sss[2])，异常 Timber＋shortCenter「出错：」＋消息。
     */
    private static void lambda$realCheckAutoText$22(String[] sss, Context context) {
        if (sss.length != 3) {
            ToastMgr.shortBottomCenter(context, "格式错误！");
            return;
        }
        copyToClipboardForce(context, "");
        try {
            importFileByUrl(context, sss[2]);
        } catch (Exception e) {
            Timber.e(e);
            ToastMgr.shortCenter(context, "出错：" + e.getMessage());
        }
    }

    /**
     * 官 importFileByUrl(Context,String)（smali:2254，.line 1186-1194）：按 "@" 切
     * 恰 2 段、两段非空、址段以 http/file 开头、存段以 hiker://files//file://
     * 开头才 CodeUtil.download(址, 存段, null, $4)；余「规则有误！」。
     * CodeUtil.download 接咱家 HkCodeUtil.download 同名转写；$4 回调照官：
     * 成功经 runOnUI toast「导入成功，内容已保存到」＋存段原文，失败经 runOnUI
     * toast「导入失败，网络连接错误！」。
     */
    private static void importFileByUrl(Context context, String str) {
        String[] parts = str.split("@");
        if (parts.length == 2 && StringUtil.isNotEmpty(parts[0]) && StringUtil.isNotEmpty(parts[1])
                && (parts[1].startsWith("http") || parts[1].startsWith("file"))
                && (parts[0].startsWith("hiker://files/") || parts[0].startsWith("file://"))) {
            final String savePath = parts[0];
            HkCodeUtil.download(parts[1], savePath, null, new HkCodeUtil.OnCodeGetListener() {
                @Override
                public void onSuccess(String result) {
                    ThreadTool.INSTANCE.runOnUI(() -> ToastMgr.shortBottomCenter(context,
                            "导入成功，内容已保存到" + savePath));
                }

                @Override
                public void onFailure(int code, String msg) {
                    ThreadTool.INSTANCE.runOnUI(() -> ToastMgr.shortBottomCenter(context,
                            "导入失败，网络连接错误！"));
                }
            });
            return;
        }
        ToastMgr.shortBottomCenter(context, "规则有误！");
    }

    /**
     * 官 importRuleByRuleTextList(Context,List&lt;String&gt;)（smali:2699，.line 995-1035）
     * ＋importHomeRulesByText(Context,List)（smali:2363 List 形，.line 1303-1330，
     * λ41）照官转写、落点换咱家现行面：逐条按 ￥ 切（&lt;3 段跳过）、尾段拼回、
     * 清剪贴板、解码＋校验收表——解码/校验走 HkRuleManager.parseJsonList
     * （其 unwrapShareText 含 base64:// 剥壳、parseJson 含 title 校验，与官
     * 「base64 解码→ArticleListRuleJO title 非空」同口），逐条失败照官 toast
     * 「规则有误：」＋消息续跑；收表空→「导入失败，规则为空」。
     * 落盘段（λ41）：后台逐条按 title 查存、已有则保其 group 后 save——咱家
     * 查存走 HkRuleManager.getInstalledRule、保 group 写回规则 JSON 的 group
     * 字段、落盘走 saveRule；尾发咱家现行规则变更事件
     * OnArticleListRuleChangedEvent（官同名事件的星辰形）＋成功条数提示。
     * 差异记名：官 GlobalDialogActivity.startLoading 加载面与
     * LoadingDismissEvent 咱家无此面、不建（任务口径），成功提示以 toast 承接
     * 其用户可见终效；官逐条收表只收单规则对象，咱家 parseJsonList 兼收数组形
     * （HkRuleManager 现行校验口径）。
     */
    public static void importRuleByRuleTextList(Context context, List<String> list) {
        List<HkRuleManager.ParsedRule> collected = new ArrayList<>();
        for (String item : list) {
            String[] split = item.split("￥");
            if (split.length < 3) {
                continue;
            }
            try {
                String ruleText = split.length == 3 ? split[2]
                        : StringUtil.arrayToString(split, 2, split.length, "￥");
                copyToClipboardForce(context, "");
                collected.addAll(HkRuleManager.get().parseJsonList(ruleText));
            } catch (Exception e) {
                ToastMgr.shortBottomCenter(context, "规则有误：" + e.getMessage());
            }
        }
        importHomeRulesByText(context, collected);
    }

    /** 官 importHomeRulesByText(Context,List) 的咱家落盘段（见 importRuleByRuleTextList 注）。 */
    private static void importHomeRulesByText(Context context, List<HkRuleManager.ParsedRule> rules) {
        if (rules == null || rules.isEmpty()) {
            ToastMgr.shortBottomCenter(context, "导入失败，规则为空");
            return;
        }
        HeavyTaskUtil.executeNewTask(() -> {
            int saved = 0;
            for (HkRuleManager.ParsedRule parsed : rules) {
                try {
                    String json = parsed.json;
                    HkRule existing = HkRuleManager.get().getInstalledRule(parsed.rule.getTitle());
                    if (existing != null) {
                        String group = existing.getGroup();
                        parsed.rule.setGroup(group);
                        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                        obj.addProperty("group", group);
                        json = obj.toString();
                    }
                    HkRuleManager.get().saveRule(parsed.rule, json);
                    saved++;
                } catch (Exception e) {
                    Timber.e(e);
                }
            }
            final int count = saved;
            EventBus.getDefault().post(new OnArticleListRuleChangedEvent());
            ThreadTool.INSTANCE.runOnUI(() -> ToastMgr.shortBottomCenter(App.get(),
                    "已成功导入" + count + "条规则"));
        });
    }

    /**
     * 官 importRulesWithDialog(Context,OnOkListener,OnOkListener,Consumer)
     * （官源 AutoImportHelper）照官转写：ImportRulesPopup（136b 簇1 落本体）
     * .withDetail(showDetail!=null).withListener——ok(backup,delete) 中
     * delete 支先经 XPopup 二次确认「确认要删除后再导入规则吗？」才回
     * deleteImport、余直接回 onlyImport，backup 勾选以 "backup"/null 串
     * 回传（官同形）；showDetail 支回 showDetail.accept(null)。建面照官
     * XPopup.Builder.asCustom 直出（无 borderRadius）。
     */
    private static void importRulesWithDialog(Context context, OnOkListener deleteImportListener,
                                              OnOkListener onlyImportListener,
                                              Consumer<String> showDetail) {
        ImportRulesPopup popup = new ImportRulesPopup(context)
                .withDetail(showDetail != null)
                .withListener(new ImportRulesPopup.OkListener() {
                    @Override
                    public void ok(boolean backup, boolean delete) {
                        if (delete) {
                            new XPopup.Builder(context)
                                    .asConfirm("温馨提示", "确认要删除后再导入规则吗？",
                                            () -> deleteImportListener.ok(backup ? "backup" : null))
                                    .show();
                        } else {
                            onlyImportListener.ok(backup ? "backup" : null);
                        }
                    }

                    @Override
                    public void showDetail() {
                        if (showDetail != null) {
                            showDetail.accept(null);
                        }
                    }
                });
        new XPopup.Builder(context)
                .asCustom(popup)
                .show();
    }

    /**
     * 官 importRulesByTextWithDialog(Context,String,String)（官源）照官转写：
     * 文本 trim 后单对象形（{...}）→importRuleByRuleText（咱家等价路＝
     * HkDetailBridge.hooks.checkText）；非数组形→shortCenter「口令有误，
     * 请确认导入链接是否有效」；数组形→importRulesWithDialog 三支——删除支
     * （data 非空先 BackupUtil.backupDBAndJs(ctx,true)、LitePal.deleteAll
     * 等价＝deleteAllRules 全删、再 importHomeRulesByText）、仅导入支
     * （data 非空先备份、直接 importHomeRulesByText）、详情支（putVar
     * "rulesImportUrl"＋assets homeSubView.json 的 detail_find_rule 组
     * 「查看合集」临时规则，col_type=TEXT_3、经 putTempRule 开 HkListActivity，
     * 即官 FilmListActivity 载体对位）。逐支 try/catch＋「出错：」提示照官。
     */
    public static void importRulesByTextWithDialog(Context context, String text, String originalUrl) {
        String s = text.trim();
        if (s.startsWith("{") && s.endsWith("}")) {
            //单个规则
            Activity activity = activityOf(context);
            if (activity != null) {
                HkDetailBridge.hooks.checkText(activity, s);
            }
            return;
        }
        if (!s.startsWith("[") || !s.endsWith("]")) {
            ToastMgr.shortCenter(context, "口令有误，请确认导入链接是否有效");
            return;
        }
        //规则合集
        importRulesWithDialog(context, data -> {
            try {
                if (StringUtil.isNotEmpty(data)) {
                    BackupUtil.backupDBAndJs(context, true);
                }
                deleteAllRules();
                importHomeRulesByText(context, s);
            } catch (Exception e) {
                Timber.e(e);
                ToastMgr.shortCenter(context, "出错：" + e.getMessage());
            }
        }, data -> {
            try {
                if (StringUtil.isNotEmpty(data)) {
                    BackupUtil.backupDBAndJs(context, true);
                }
                importHomeRulesByText(context, s);
            } catch (Exception e) {
                Timber.e(e);
                ToastMgr.shortCenter(context, "出错：" + e.getMessage());
            }
        }, s1 -> {
            HkRule rule = new HkRule();
            rule.setUrl("hiker://empty");
            HkJsRuntime.getInstance().putVar("rulesImportUrl", originalUrl);
            String viewJson = getAssetsString(App.get(), "homeSubView.json");
            HkRule viewRule = HkRuleManager.parseRuleJson(viewJson);
            rule.setFindRule(viewRule == null ? null : viewRule.getDetailFindRule());
            rule.setColType(HkColType.TEXT_3.code);
            rule.setTitle("查看合集");
            Intent intent = new Intent(context, HkListActivity.class);
            HkListActivity.putTempRule(intent, new Gson().toJson(rule));
            intent.putExtra("title", "查看合集");
            context.startActivity(intent);
        });
    }

    /** 官 LitePal.deleteAll(ArticleListRule.class) 的咱家等价：现行规则全删（HkRuleManager.delete 逐条）。 */
    private static void deleteAllRules() {
        List<HkRule> rules = HkRuleManager.get().getRules();
        for (HkRule rule : rules) {
            if (rule != null) {
                HkRuleManager.get().delete(rule.getTitle());
            }
        }
    }

    /**
     * 官 importHomeRulesByText(Context,String)（官源）转写、落点换咱家现行面：
     * 空→「导入失败，规则为空」；违禁词走本文件 hasFilterWord（官 FilterUtil
     * 本体恒 false 桩、同口径）；解表走 HkRuleManager.parseJsonList（坏串/空表
     * 同回「导入失败，规则为空」）；800 条上限以 getRules().size() 现行计数面
     * 承接（文案照官）；落盘复用本文件 List 形 importHomeRulesByText（逐条保
     * group 落盘＋规则变更事件＋成功数提示）。
     */
    private static void importHomeRulesByText(Context context, String s) {
        if (StringUtil.isEmpty(s)) {
            ToastMgr.shortBottomCenter(context, "导入失败，规则为空");
            return;
        }
        if (hasFilterWord(s)) {
            ToastMgr.shortBottomCenter(context, "规则含有违禁词，禁止导入");
            return;
        }
        List<HkRuleManager.ParsedRule> parsed;
        try {
            parsed = HkRuleManager.get().parseJsonList(s);
        } catch (Exception e) {
            ToastMgr.shortBottomCenter(context, "导入失败，规则为空");
            return;
        }
        if (parsed.isEmpty()) {
            ToastMgr.shortBottomCenter(context, "导入失败，规则为空");
            return;
        }
        int c = HkRuleManager.get().getRules().size();
        if (parsed.size() > 800 || c > 800) {
            ToastMgr.shortBottomCenter(context,
                    "规则数目过多，已有规则" + c + "条，欲导入规则" + parsed.size() + "条，均不能超过800条！");
            return;
        }
        importHomeRulesByText(context, parsed);
    }

    /** Context→Activity 取口：是 Activity 直用、余走 ActivityManager 当前活动（与本文件既有取口同路）。 */
    private static Activity activityOf(Context context) {
        if (context instanceof Activity) {
            return (Activity) context;
        }
        return ActivityManager.getInstance().getCurrentActivity();
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
            HkRule rule = HkRuleManager.parseRuleJson(homeJson);
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
