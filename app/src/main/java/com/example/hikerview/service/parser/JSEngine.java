package com.example.hikerview.service.parser;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.HkJsRuntime;

/**
 * 官方类名对接层（非官包代码）：PY源盒子的标准基类 libs_py/base/hiker.py、
 * hikerPop.py 在模块级按官方类名导入本类（com.example.hikerview.service.parser.JSEngine），
 * 咱家树此前无此类，标准基类的源导入即断（账 hk-pybox-usable-20261119 件 5）。
 * 本类只补名字与被用到的成员，签名逐字对官 JSEngine（官 smali 实证），干活全转调
 * 咱家 HkJsRuntime 现行同签名门面（引擎本体取 HkJsRuntime.getInstance()，即最近被
 * 宿主领用的运行时——本树既定的官单例同位口径，调用时现取）。
 * 尚无任何运行时被领用时：getCurrentActivity 直回 App.activity()（与门面本体同一
 * 出处，保住 hiker.py 模块级 ANDROID_CONTEXT 的捕获）、log 落 android.util.Log、
 * 余法回空串/null 不投递——此降级只在无引擎时成立，有引擎恒走门面。
 */
public class JSEngine {

    private static volatile JSEngine instance;

    public JSEngine() {
    }

    /** 官 JSEngine.getInstance()：双重检查单例（官 smali 4575 同形）。 */
    public static JSEngine getInstance() {
        JSEngine local = instance;
        if (local == null) {
            synchronized (JSEngine.class) {
                local = instance;
                if (local == null) {
                    local = new JSEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    private static HkJsRuntime runtime() {
        return HkJsRuntime.getInstance();
    }

    public Object getCurrentActivity(Object ruleTitleKey) {
        HkJsRuntime rt = runtime();
        if (rt != null) return rt.getCurrentActivity(ruleTitleKey);
        try {
            return App.activity();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void log(Object o, Object ruleKey) {
        HkJsRuntime rt = runtime();
        if (rt != null) {
            rt.log(o, ruleKey);
        } else {
            android.util.Log.i("JSEngine", o == null ? "" : String.valueOf(o));
        }
    }

    public void toast(Object str, Object ruleTitleKey) {
        HkJsRuntime rt = runtime();
        if (rt != null) rt.toast(str, ruleTitleKey);
    }

    public Object fetch(String url, Object options, Object ruleKey) {
        HkJsRuntime rt = runtime();
        return rt == null ? "" : rt.fetch(url, options, ruleKey);
    }

    public String parseDom(Object o1, Object o2, Object urlKey) {
        HkJsRuntime rt = runtime();
        return rt == null ? "" : rt.parseDom(o1, o2, urlKey);
    }

    /**
     * Python 侧 hiker.parseDom(html, rule) 只传 2 参，官方法只有 3 参形；
     * Chaquopy 按参量对位选重载，补此 2 参形转调 3 参形（urlKey 传 null，
     * 门面内归一为当前页址）。
     */
    public String parseDom(Object o1, Object o2) {
        return parseDom(o1, o2, null);
    }

    public String parseDomForArray(Object o1, Object o2) {
        HkJsRuntime rt = runtime();
        return rt == null ? "[]" : rt.parseDomForArray(o1, o2);
    }

    public String parseDomForHtml(Object o1, Object o2) {
        HkJsRuntime rt = runtime();
        return rt == null ? "" : rt.parseDomForHtml(o1, o2);
    }

    public void setHomeResult(Object o, Object callbackKey, Object ruleKey, Object typeKey) {
        HkJsRuntime rt = runtime();
        if (rt != null) rt.setHomeResult(o, callbackKey, ruleKey, typeKey);
    }

    public void setSearchResult(Object o, Object callbackKey, Object ruleKey, Object typeKey) {
        HkJsRuntime rt = runtime();
        if (rt != null) rt.setSearchResult(o, callbackKey, ruleKey, typeKey);
    }
}
