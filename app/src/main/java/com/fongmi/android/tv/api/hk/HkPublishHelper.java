package com.fongmi.android.tv.api.hk;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.ToastUtil;
import com.google.gson.Gson;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.interfaces.OnCancelListener;
import com.lxj.xpopup.interfaces.OnConfirmListener;

/**
 * 官 {@code com.example.hikerview.ui.rules.utils.PublishHelper} 的等价面
 * （PublishHelper.smali 全流程转写）。
 *
 * <p>存储载体差（记名）：官走 LitePal BigTextDO 两键——publish_code 存
 * 提交云仓库的 JS 规则原文、publish_account_code 存 AccountPwd 的
 * fastjson 串；咱家无 LitePal，照 HkHomeSubService/HkAdUrlBlocker 的现行
 * 持久面口径以 SharedPreferences「hk_publish」同名两键承接，账号值仍为
 * {account,password} 的 JSON 串（Gson 字段名与官 fastjson 同形）。
 * 官无任何写死端点：发布目标由 publish_code 里的用户 JS 自行决定
 * （parsePublishRule 把 MY_ACCOUNT/MY_PASSWORD 注入后求值该 JS），
 * 故本面纯本地存取＋JS 求值、无第三方服务端依赖。
 *
 * <p>结果提示载体差（记名）：官经 EventBus ShowToastMessageEvent 提示，
 * 咱家无该事件类（AutoImportHelper 的 ToastMessage 同判先例），此处以
 * ToastUtil 主线程直落同文案。
 */
public class HkPublishHelper {

    private static final String TAG = "HkPublishHelper";
    private static final String PREFS = "hk_publish";
    private static final String KEY_PUBLISH_CODE = "publish_code";
    private static final String KEY_PUBLISH_ACCOUNT = "publish_account_code";

    private static final Gson GSON = new Gson();

    private HkPublishHelper() {
    }

    private static SharedPreferences prefs() {
        Context ctx = App.get();
        return ctx == null ? null : ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String getBigText(String key) {
        SharedPreferences sp = prefs();
        return sp == null ? null : sp.getString(key, null);
    }

    private static void saveBigText(String key, String value) {
        SharedPreferences sp = prefs();
        if (sp != null) sp.edit().putString(key, value).apply();
    }

    /** 官 getPublishAccount（PublishHelper.smali:86-92）：无记录/解析为空回空对象。 */
    public static HkAccountPwd getPublishAccount() {
        String value = getBigText(KEY_PUBLISH_ACCOUNT);
        if (value != null) {
            try {
                HkAccountPwd pwd = GSON.fromJson(value, HkAccountPwd.class);
                if (pwd != null) return pwd;
            } catch (Exception ignored) {
                // 官 fastjson parseObject 坏串回 null 后同走下方空对象支
            }
        }
        return new HkAccountPwd();
    }

    /** 官 savePublishAccount（smali:100-106）：账号对象串化后整键回写。 */
    public static void savePublishAccount(HkAccountPwd accountPwd) {
        saveBigText(KEY_PUBLISH_ACCOUNT, GSON.toJson(accountPwd));
    }

    /** 官 getPublishCode（smali:77-81）：无记录回 ""。 */
    public static String getPublishCode() {
        String value = getBigText(KEY_PUBLISH_CODE);
        return value == null ? "" : value;
    }

    /** 官 savePublishCode（smali:67-73）：发布 JS 规则原文整键回写。 */
    public static void savePublishCode(String code) {
        saveBigText(KEY_PUBLISH_CODE, code);
    }

    /**
     * 官 JSEngine.parsePublishRule(Object,String,AccountPwd)（JSEngine.smali:20956，
     * .line 389-391）照官转写：getMyRule(规则)＋generateMY("MY_ACCOUNT")＋
     * generateMY("MY_PASSWORD")＋发布代码原文拼串后 evalJS(串,"",false)。
     * 三个引擎面咱家 HkJsRuntime 俱在（getMyRule 静态/generateMY/evalJS 三参形）。
     */
    public static String parsePublishRule(Object rule, String code, HkAccountPwd accountPwd) {
        HkJsRuntime engine = HkJsRuntime.getInstance();
        // 核查 136b 簇1④ 空守：引擎未领用时 getInstance() 可回 null、
        // accountPwd 入参亦可空——裸解引用会 NPE 穿出后台 catch 的
        // Exception 面（NPE 属 Exception 可被兜，但消息无义）；改明确
        // 失败串由 publishRuleBackground 的失败支统一提示。
        if (engine == null) throw new IllegalStateException("引擎未就绪");
        if (accountPwd == null) accountPwd = new HkAccountPwd();
        String script = HkJsRuntime.getMyRule(rule)
                + engine.generateMY("MY_ACCOUNT", accountPwd.getAccount())
                + engine.generateMY("MY_PASSWORD", accountPwd.getPassword())
                + code;
        return engine.evalJS(script, "", false);
    }

    /**
     * 官 publishRule(Activity,Object)（smali:31-37）：publish_code 为空走
     * showPublishGuide；余后台跑 publishRuleBackground。官后台件为
     * HeavyTaskUtil（咱家 mobile 件），main 层以工作线程直跑同效（记名）。
     */
    public static void publishRule(Activity activity, Object rule) {
        String code = getPublishCode();
        if (TextUtils.isEmpty(code)) {
            // 核查 136b P1 接线补：调用方（授权 onAllow）可能在 JS 线程
            // （checkAuth 已授权快路直跑），XPopup 须主线程——归主线程落。
            if (Looper.myLooper() == Looper.getMainLooper()) {
                showPublishGuide(activity);
            } else {
                new Handler(Looper.getMainLooper()).post(() -> showPublishGuide(activity));
            }
            return;
        }
        new Thread(() -> publishRuleBackground(activity, rule, code), "hk-publish").start();
    }

    /**
     * 官 PublishHelper 的后台 lambda（smali lambda$publishRule$0:39-49，本法为
     * 其转写，手写名避与编译器合成符号相撞）：parsePublishRule 求值后按结果分发——
     * 恰 "success"→「提交云仓库完成」；非空且含 success/成功/完成 任一→原样
     * 提示结果串；余→「提交云仓库失败：」＋结果；异常→同前缀＋异常消息。
     */
    private static void publishRuleBackground(Activity activity, Object rule, String code) {
        try {
            String result = parsePublishRule(rule, code, getPublishAccount());
            if ("success".equals(result)) {
                toast(activity, "提交云仓库完成");
            } else if (!TextUtils.isEmpty(result)
                    && (result.contains("success") || result.contains("成功") || result.contains("完成"))) {
                toast(activity, result);
            } else {
                toast(activity, "提交云仓库失败：" + result);
            }
        } catch (Exception e) {
            Log.e(TAG, "publishRule failed", e);
            toast(activity, "提交云仓库失败：" + e.getMessage());
        }
    }

    /**
     * 官 showPublishGuide(Activity)（smali:56-63）：XPopup 说明弹窗
     * （borderRadius=dpToPx(16)、标题「提交云仓库说明」、正文照官、
     * 取消钮「取消」＝官空 lambda、确认钮「设置规则」）。
     * 确认支接线点（记名）：官确认后开 PublishCodeEditActivity 编辑
     * publish_code，咱家无此活动页（整层不存在），确认暂只关窗——
     * 编辑页落树后在此把确认回调改指该页即可，本面其余部分不受影响。
     */
    public static void showPublishGuide(Activity activity) {
        // 核查 136b 簇1④ 空守：无宿主时 XPopup 无从挂，记日志回落
        // （publishRule 的调用方在授权确认后取当前页，恒有宿主，此为兜底）。
        if (activity == null) {
            Log.w(TAG, "showPublishGuide: no host activity");
            return;
        }
        float radius = 16 * activity.getResources().getDisplayMetrics().density + 0.5f;
        OnConfirmListener onConfirm = () -> {
            // 接线点：PublishCodeEditActivity 未落（见类注），官此支为开编辑页。
        };
        OnCancelListener onCancel = () -> {
            // 官 lambda$showPublishGuide$2 本体为空，照官。
        };
        new XPopup.Builder(activity)
                .borderRadius(radius)
                .asConfirm("提交云仓库说明",
                        "当前未设置提交云仓库规则。开发者可设置提交云仓库的JS规则，在规则里面使用MY_RULE变量获取提交的规则详情，使用my_rule变量获取规则字符串，然后提交到云仓库最后返回一个包含‘成功’或‘success’或‘完成’的字符串即可",
                        "取消", "设置规则", onConfirm, onCancel, false, 0)
                .show();
    }

    private static void toast(Context context, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Context c = context != null ? context : App.get();
            if (c != null) ToastUtil.show(c, msg, Toast.LENGTH_SHORT);
        });
    }
}
