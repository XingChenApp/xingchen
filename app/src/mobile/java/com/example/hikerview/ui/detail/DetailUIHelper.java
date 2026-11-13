package com.example.hikerview.ui.detail;

import android.app.Activity;
import android.view.View;

import com.annimon.stream.function.Consumer;
import com.example.hikerview.model.BaseResultItem;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.ui.hk.HkDetailBridge;
import com.google.gson.Gson;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.Scriptable;

/**
 * 官 {@code com.example.hikerview.ui.detail.DetailUIHelper} 的插件面适配。
 *
 * <p>官 dealUrlSimply 本体星辰早已逐条转写在
 * {@code com.fongmi.android.tv.ui.hk.HkDetailBridge#dealUrlSimply}
 * （分支顺序与语义照官 smali），本类只做签名对齐与入参转换：
 * obj（JS 侧 MY_RULE）→ HkRule、annimon Consumer → java.util.function.Consumer。
 * BaseResultItem 恒为插件所传的 null，条目按 null 转交（见 BaseResultItem 类注）。
 */
public class DetailUIHelper {

    public static boolean dealUrlSimply(Activity activity, View view, Object obj,
                                        BaseResultItem baseResultItem, String str,
                                        Consumer<String> consumer) {
        return dealUrlSimply(activity, view, obj, baseResultItem, str, consumer, null);
    }

    public static boolean dealUrlSimply(Activity activity, View view, Object obj,
                                        BaseResultItem baseResultItem, String str,
                                        Consumer<String> consumer, Consumer<String> consumer2) {
        return new HkDetailBridge().dealUrlSimply(activity, view, toRule(obj), null, str,
                wrap(consumer), wrap(consumer2));
    }

    private static java.util.function.Consumer<String> wrap(Consumer<String> consumer) {
        return consumer == null ? null : consumer::accept;
    }

    /**
     * JS 侧 MY_RULE 的三种到达形态：null → null；已是 HkRule → 直用；
     * JS 对象（引擎以 JSON.parse 注入的原对象）→ 串化后按 HkRule 模型解析，
     * 与 HkJsRuntime.myRuleFromScope 同一转换口径（Gson 模型面）。
     * 转换失败回 null —— 与插件 MY_RULE 未定义时自设 null 的语义一致。
     */
    private static HkRule toRule(Object obj) {
        if (obj == null) return null;
        if (obj instanceof HkRule) return (HkRule) obj;
        if (obj instanceof Scriptable) {
            try {
                Context cx = Context.getCurrentContext();
                if (cx == null) return null;
                Scriptable scope = ((Scriptable) obj).getParentScope();
                if (scope == null) scope = cx.initStandardObjects();
                Object json = NativeJSON.stringify(cx, scope, obj, null, null);
                if (json == null) return null;
                return new Gson().fromJson(json.toString(), HkRule.class);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}
