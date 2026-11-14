package com.example.hikerview.ui.detail;

import android.app.Activity;
import android.view.View;

import com.annimon.stream.function.Consumer;
import com.example.hikerview.model.BaseResultItem;
import com.example.hikerview.model.ViewCollection;
import com.example.hikerview.ui.browser.util.CollectionUtil;
import com.example.hikerview.ui.home.FilmListActivity;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.ui.hk.HkDetailBridge;
import com.fongmi.android.tv.ui.hk.HkStringUtil;
import com.google.gson.Gson;

import org.litepal.LitePal;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.Scriptable;

import java.util.List;

import timber.log.Timber;

/**
 * 官 {@code com.example.hikerview.ui.detail.DetailUIHelper} 的插件面适配。
 *
 * <p>官 dealUrlSimply 本体星辰早已逐条转写在
 * {@code com.fongmi.android.tv.ui.hk.HkDetailBridge#dealUrlSimply}
 * （分支顺序与语义照官 smali），本类只做签名对齐与入参转换：
 * obj（JS 侧 MY_RULE）→ HkRule、annimon Consumer → java.util.function.Consumer。
 * BaseResultItem 恒为插件所传的 null，条目按 null 转交（见 BaseResultItem 类注）。
 *
 * <p>①团订正：官 ViewCollection 模型与 LitePal 层已落位，
 * findViewCollectionById/getViewCollection 两法照官补落（见类尾）；
 * getViewCollection 的 instanceof 宿主 FilmListActivity 只落类型声明层、记名在该类头。
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

    /**
     * 官 getItemTitle（官 smali ui/detail/DetailUIHelper .line 425-429）：
     * (活动标题非空 ? 标题 + "-" : "") + getTitleText(条目标题)；整段异常回活动标题。
     * getTitleText 接 HkStringUtil 同名转写、getActivityTitle 接 HkDetailBridge 同名转写。
     * （HkArticleFragment.getItemTitle(int) 是按位置取条目的另一职责，不在此复用。）
     */
    public static String getItemTitle(Activity activity, String title) {
        try {
            String itemTitle = HkStringUtil.getTitleText(title);
            String activityTitle = HkDetailBridge.getActivityTitle(activity);
            return (HkStringUtil.isEmpty(activityTitle) ? "" : activityTitle + "-") + itemTitle;
        } catch (Exception e) {
            return HkDetailBridge.getActivityTitle(activity);
        }
    }

    /**
     * 官 getLastClickText（.line 434-438）：空回原样；取 "@@" 首段过
     * getTitleText，超 100 字回 null。getTitleText 接 HkStringUtil 同名转写。
     */
    public static String getLastClickText(String text) {
        if (HkStringUtil.isEmpty(text)) {
            return text;
        }
        String titleText = HkStringUtil.getTitleText(text.split("@@")[0]);
        if (titleText.length() > 100) {
            return null;
        }
        return titleText;
    }

    private static java.util.function.Consumer<String> wrap(Consumer<String> consumer) {
        return consumer == null ? null : consumer::accept;
    }

    /**
     * 官 findViewCollectionById（官 smali .line 516-523）逐字转写：
     * LitePal where("id = ?", String.valueOf(id)).limit(1) 查、异常 Timber.e 后按空处理、空回 null。
     */
    public static ViewCollection findViewCollectionById(long id) {
        List<ViewCollection> list = null;
        try {
            list = LitePal.where("id = ?", String.valueOf(id)).limit(1).find(ViewCollection.class);
        } catch (Exception e) {
            Timber.e(e);
            list = null;
        }
        if (CollectionUtil.isEmpty(list)) {
            return null;
        }
        return list.get(0);
    }

    /**
     * 官 getViewCollection（官 smali .line 482-491）逐字转写：仅 FilmListActivity 时
     * 以 intent 的 "title" extra 与入参 cUrl 按 "CUrl = ? and MTitle = ?" 查首条，其余回 null。
     */
    public static ViewCollection getViewCollection(Activity activity, String cUrl) {
        if (activity instanceof FilmListActivity) {
            String title = activity.getIntent().getStringExtra("title");
            List<ViewCollection> list = null;
            try {
                list = LitePal.where("CUrl = ? and MTitle = ?", cUrl, title).limit(1).find(ViewCollection.class);
            } catch (Exception e) {
                Timber.e(e);
                list = null;
            }
            if (!CollectionUtil.isEmpty(list)) {
                return list.get(0);
            }
        }
        return null;
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
                // 面②#3：与 myRuleFromScope 同一收敛口径落到实处——经
                // HkRuleManager.parseRuleJson 先按官 parseString 收敛
                // String 字段（复合值→JSON 文本）再解，裸 Gson 遇
                // MY_RULE.params={} 等复合值整单回 null。
                return HkRuleManager.parseRuleJson(json.toString());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}
