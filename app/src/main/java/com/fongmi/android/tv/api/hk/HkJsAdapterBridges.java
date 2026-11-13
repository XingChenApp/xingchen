package com.fongmi.android.tv.api.hk;

import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hikerview.ui.home.view.BookmarkFolderAdapter;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.core.CenterPopupView;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JavaAdapter 抽象类桥（2026-11-13，聚阅 class file 案结构修）：
 * 规则侧标准插件 hikerPop.js 有 12 处 new JavaAdapter(&lt;抽象类&gt;, jsObj, ...)
 * （XPopup BottomPopupView/CenterPopupView、RecyclerView.Adapter/ViewHolder、
 * ItemTouchHelper.Callback、LinearSmoothScroller、BookmarkFolderAdapter），
 * 咱家 HkJavaAdapterFn 旧形只接「首参单接口」走 Proxy、其余穿透 Rhino 原生
 * JavaAdapter——原生实现靠运行时生成 JVM 字节码再 defineClass，安卓 ART 上
 * 必抛 UnsupportedOperationException "can't load this type of class file"
 * （他真机刷新聚阅主页即此串）。本类为这七个已知父类各备一个真 Java 子类：
 * 被 JS 对象定义的方法分发进 JS（this＝BridgeSelf，见下），没定义的走
 * super 原实现；JS 里的 this.super$Xxx() 经 BridgeSelf 的 super$ 函数表回到
 * 本实例的 super 实现。接口形仍走旧 Proxy 路，本类不碰。
 * 分发口径与 JsProxyHandler 同：回调线程进 Context、ambient 登记定义 scope、
 * 返回值同梯转换；新增两点跨界规则——桥实例进 JS 时以其 BridgeSelf 表示
 * （自定义 JS 方法/属性才找得到），BridgeSelf 实现 Wrapper，传回 Java 时
 * Rhino 的 jsToJava 自动解包成本体。
 */
public final class HkJsAdapterBridges {

    private HkJsAdapterBridges() {
    }

    /** 桥实例标记：跨界转换时据此换回其 BridgeSelf / 本体。 */
    public interface SelfBound {
        BridgeSelf hkSelf();
    }

    // ================= JS self =================

    /**
     * 桥实例在 JS 侧的化身：属性查找序＝super$ 函数表 → 本实例自存属性 →
     * JS 定义对象（方法与初值）→ Java 本体成员（NativeJavaObject 反射面）。
     * 写入一律落本实例自存（this.titleView = ... 的逐实例语义）。
     */
    public static final class BridgeSelf extends ScriptableObject implements Wrapper {
        final Scriptable jsObj;
        final Object javaSelf;
        private final Map<String, Object> supers = new HashMap<>();
        private final Map<String, Object> own = new HashMap<>();
        private NativeJavaObject javaWrap;

        BridgeSelf(Scriptable jsObj, Object javaSelf) {
            this.jsObj = jsObj;
            this.javaSelf = javaSelf;
        }

        @Override
        public String getClassName() {
            return "HkJsBridge";
        }

        @Override
        public Object unwrap() {
            return javaSelf;
        }

        void addSuper(String name, BaseFunction fn) {
            supers.put("super$" + name, fn);
        }

        private NativeJavaObject wrap() {
            if (javaWrap == null) {
                Scriptable scope = jsObj.getParentScope();
                javaWrap = new NativeJavaObject(scope != null ? scope : jsObj,
                        javaSelf, javaSelf.getClass());
            }
            return javaWrap;
        }

        @Override
        public Object get(String name, Scriptable start) {
            Object v = supers.get(name);
            if (v != null) return v;
            if (own.containsKey(name)) return own.get(name);
            Object j = ScriptableObject.getProperty(jsObj, name);
            if (j != NOT_FOUND) return j;
            NativeJavaObject w = wrap();
            if (w.has(name, w)) return w.get(name, w);
            return super.get(name, start);
        }

        @Override
        public boolean has(String name, Scriptable start) {
            return get(name, start) != NOT_FOUND;
        }

        @Override
        public void put(String name, Scriptable start, Object value) {
            own.put(name, value);
        }
    }

    // ================= 分发核（与 JsProxyHandler 同口径） =================

    static boolean hasJs(Scriptable jsObj, String name) {
        return ScriptableObject.getProperty(jsObj, name) instanceof Function;
    }

    /** JS 参数 → Java 本体（桥化身解包、NativeJavaObject 解包）。 */
    static Object unwrapJs(Object o) {
        if (o instanceof BridgeSelf) return ((BridgeSelf) o).javaSelf;
        if (o instanceof NativeJavaObject) return ((NativeJavaObject) o).unwrap();
        return o;
    }

    /** Java 参数 → JS 表示（桥实例换其化身，其余走标准 javaToJS）。 */
    private static Object toJs(Object arg, Scriptable scope) {
        if (arg instanceof SelfBound) return ((SelfBound) arg).hkSelf();
        return Context.javaToJS(arg, scope);
    }

    /** JS 返回 → Java 目标型（同 JsProxyHandler 梯，另加桥化身解包）。 */
    private static Object fromJs(Object r, Class<?> rt) {
        if (rt == void.class || rt == Void.class) return null;
        Object u = r;
        if (u instanceof BridgeSelf) u = ((BridgeSelf) u).javaSelf;
        else if (u instanceof NativeJavaObject) u = ((NativeJavaObject) u).unwrap();
        if (u == null || u == Undefined.instance) {
            if (rt == boolean.class) return false;
            if (rt.isPrimitive()) return 0;
            return null;
        }
        if (rt == boolean.class || rt == Boolean.class) return Context.toBoolean(u);
        if (rt.isPrimitive() || Number.class.isAssignableFrom(rt)) return Context.toNumber(u);
        if (rt == String.class) return Context.toString(u);
        if (rt.isInstance(u)) return u;
        try {
            return Context.jsToJava(u, rt);
        } catch (Throwable ignored) {
            return u;
        }
    }

    static Object callJs(Scriptable jsObj, ThreadLocal<Scriptable> ambientTl,
                         BridgeSelf self, String name, Class<?> rt, Object[] mArgs) {
        return ContextFactory.getGlobal().call(cx -> {
            Object fn = ScriptableObject.getProperty(jsObj, name);
            if (!(fn instanceof Function)) {
                throw Context.reportRuntimeError("js bridge missing fn: " + name);
            }
            Function f = (Function) fn;
            Scriptable fnScope = f.getParentScope();
            if (fnScope == null) fnScope = jsObj;
            Object[] jsArgs = new Object[mArgs == null ? 0 : mArgs.length];
            for (int i = 0; i < jsArgs.length; i++) jsArgs[i] = toJs(mArgs[i], fnScope);
            Scriptable prevAmb = null;
            boolean ambSet = false;
            if (ambientTl != null && fnScope != null) {
                prevAmb = ambientTl.get();
                ambientTl.set(fnScope);
                ambSet = true;
            }
            Object r;
            try {
                r = f.call(cx, fnScope, self, jsArgs);
            } finally {
                if (ambSet) {
                    if (prevAmb != null) ambientTl.set(prevAmb);
                    else ambientTl.remove();
                }
            }
            return fromJs(r, rt);
        });
    }

    // ---- 桥内速记（hasJs 才分发，否则用缺省/super 值） ----

    private static void dVoid(Scriptable j, ThreadLocal<Scriptable> t, BridgeSelf s,
                              String n, Object... a) {
        if (hasJs(j, n)) callJs(j, t, s, n, void.class, a);
    }

    private static boolean dBool(Scriptable j, ThreadLocal<Scriptable> t, BridgeSelf s,
                                 String n, boolean dflt, Object... a) {
        if (!hasJs(j, n)) return dflt;
        Object r = callJs(j, t, s, n, boolean.class, a);
        return r instanceof Boolean ? (Boolean) r : dflt;
    }

    private static int dInt(Scriptable j, ThreadLocal<Scriptable> t, BridgeSelf s,
                            String n, int dflt, Object... a) {
        if (!hasJs(j, n)) return dflt;
        Object r = callJs(j, t, s, n, int.class, a);
        return r instanceof Number ? ((Number) r).intValue() : dflt;
    }

    private static float dFloat(Scriptable j, ThreadLocal<Scriptable> t, BridgeSelf s,
                                String n, float dflt, Object... a) {
        if (!hasJs(j, n)) return dflt;
        Object r = callJs(j, t, s, n, float.class, a);
        return r instanceof Number ? ((Number) r).floatValue() : dflt;
    }

    private static Object dObj(Scriptable j, ThreadLocal<Scriptable> t, BridgeSelf s,
                               String n, Class<?> rt, Object... a) {
        if (!hasJs(j, n)) return null;
        return callJs(j, t, s, n, rt, a);
    }

    // ---- super$ 函数 ----

    private interface SuperInvoker {
        Object invoke(String name, Object[] args);
    }

    private static BaseFunction superFn(String name, SuperInvoker inv) {
        return new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                Object[] conv = new Object[args == null ? 0 : args.length];
                for (int i = 0; i < conv.length; i++) conv[i] = unwrapJs(args[i]);
                Object r = inv.invoke(name, conv);
                return r == null ? null : Context.javaToJS(r, scope);
            }
        };
    }

    private static void regSupers(BridgeSelf self, SuperInvoker inv, String... names) {
        for (String n : names) self.addSuper(n, superFn(n, inv));
    }

    private static int numI(Object[] a, int i) {
        return a[i] instanceof Number ? ((Number) a[i]).intValue() : 0;
    }

    // ================= 工厂 =================

    /**
     * HkJavaAdapterFn 命中已知抽象父类时建桥；不认识的类回 null（维持旧穿透）。
     * 返回 BridgeSelf（Scriptable＋Wrapper），call/construct 两形通用。
     */
    static Object tryCreate(Class<?> cls, Scriptable jsObj, Object[] ctorArgs,
                            ThreadLocal<Scriptable> ambientTl) {
        if (cls == BottomPopupView.class) {
            return new BottomPopupBridge(ctxArg(ctorArgs, 0), jsObj, ambientTl).hkSelf();
        }
        if (cls == CenterPopupView.class) {
            return new CenterPopupBridge(ctxArg(ctorArgs, 0), jsObj, ambientTl).hkSelf();
        }
        if (cls == RecyclerView.ViewHolder.class) {
            return new ViewHolderBridge(viewArg(ctorArgs, 0), jsObj, ambientTl).hkSelf();
        }
        if (cls == RecyclerView.Adapter.class) {
            return new AdapterBridge(jsObj, ambientTl).hkSelf();
        }
        if (cls == ItemTouchHelper.Callback.class) {
            return new TouchCallbackBridge(jsObj, ambientTl).hkSelf();
        }
        if (cls == LinearSmoothScroller.class) {
            return new SmoothScrollerBridge(ctxArg(ctorArgs, 0), jsObj, ambientTl).hkSelf();
        }
        if (cls == BookmarkFolderAdapter.class) {
            return new BookmarkFolderBridge(ctxArg(ctorArgs, 0), listArg(ctorArgs, 1),
                    listenerArg(ctorArgs, 2, ambientTl), boolArg(ctorArgs, 3, true),
                    jsObj, ambientTl).hkSelf();
        }
        return null;
    }

    private static android.content.Context ctxArg(Object[] a, int i) {
        Object u = i < a.length ? unwrapJs(a[i]) : null;
        return u instanceof android.content.Context ? (android.content.Context) u : null;
    }

    private static View viewArg(Object[] a, int i) {
        Object u = i < a.length ? unwrapJs(a[i]) : null;
        return u instanceof View ? (View) u : null;
    }

    @SuppressWarnings("unchecked")
    private static List<com.example.hikerview.model.Bookmark> listArg(Object[] a, int i) {
        Object u = i < a.length ? unwrapJs(a[i]) : null;
        return u instanceof List ? (List<com.example.hikerview.model.Bookmark>) u : null;
    }

    private static boolean boolArg(Object[] a, int i, boolean dflt) {
        Object u = i < a.length ? unwrapJs(a[i]) : null;
        if (u instanceof Boolean) return (Boolean) u;
        if (u instanceof Number) return ((Number) u).intValue() != 0;
        return dflt;
    }

    /**
     * BookmarkFolderAdapter 的 OnItemClickListener 参数：JS 侧给的是箭头函数
     * （(v,i)=>...）或回调对象——函数形按单方法接口直调该函数（Rhino 对
     * 函数→单方法接口的惯例），对象形走既有 JsProxyHandler 按名分发。
     */
    private static BookmarkFolderAdapter.OnItemClickListener listenerArg(
            Object[] a, int i, ThreadLocal<Scriptable> ambientTl) {
        if (i >= a.length) return null;
        Object raw = a[i];
        Object u = unwrapJs(raw);
        if (u == null) return null;
        if (u instanceof BookmarkFolderAdapter.OnItemClickListener) {
            return (BookmarkFolderAdapter.OnItemClickListener) u;
        }
        ClassLoader cl = BookmarkFolderAdapter.class.getClassLoader();
        if (cl == null) cl = HkJsAdapterBridges.class.getClassLoader();
        Class<?>[] ifaces = {BookmarkFolderAdapter.OnItemClickListener.class};
        if (u instanceof Function) {
            final Function fn = (Function) u;
            return (BookmarkFolderAdapter.OnItemClickListener) Proxy.newProxyInstance(cl, ifaces,
                    (proxy, method, mArgs) -> {
                        if ("toString".equals(method.getName())
                                && (mArgs == null || mArgs.length == 0)) return "HkJsFnListener";
                        if ("hashCode".equals(method.getName())
                                && (mArgs == null || mArgs.length == 0)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(method.getName())
                                && mArgs != null && mArgs.length == 1) return proxy == mArgs[0];
                        ContextFactory.getGlobal().call(cx -> {
                            Scriptable fs = fn.getParentScope();
                            if (fs == null) fs = fn;
                            Object[] jsArgs = new Object[mArgs == null ? 0 : mArgs.length];
                            for (int k = 0; k < jsArgs.length; k++) jsArgs[k] = toJs(mArgs[k], fs);
                            fn.call(cx, fs, fs, jsArgs);
                            return null;
                        });
                        return null;
                    });
        }
        if (u instanceof Scriptable) {
            return (BookmarkFolderAdapter.OnItemClickListener) Proxy.newProxyInstance(cl, ifaces,
                    new HkJsRuntime.JsProxyHandler((Scriptable) u, ambientTl));
        }
        return null;
    }

    private static long dLong(Scriptable j, ThreadLocal<Scriptable> t, BridgeSelf s,
                              String n, long dflt, Object... a) {
        if (!hasJs(j, n)) return dflt;
        Object r = callJs(j, t, s, n, long.class, a);
        return r instanceof Number ? ((Number) r).longValue() : dflt;
    }

    // ================= 桥类 =================

    /** XPopup 底部弹窗桥（hikerPop 用法：getImplLayoutId/onCreate/
     * getPopupHeight/getMaxHeight/addInnerContent 等，含 super$ 回调）。 */
    public static final class BottomPopupBridge extends BottomPopupView implements SelfBound {
        private final Scriptable jsObj;
        private final ThreadLocal<Scriptable> tl;
        private final BridgeSelf self;

        BottomPopupBridge(android.content.Context ctx, Scriptable jsObj,
                          ThreadLocal<Scriptable> tl) {
            super(ctx);
            this.jsObj = jsObj;
            this.tl = tl;
            this.self = new BridgeSelf(jsObj, this);
            regSupers(self, this::callSuper, "onCreate", "onShow", "onDismiss",
                    "beforeShow", "beforeDismiss", "getImplLayoutId", "getPopupWidth",
                    "getPopupHeight", "getMaxWidth", "getMaxHeight", "addInnerContent",
                    "onBackPressed");
        }

        @Override
        public BridgeSelf hkSelf() {
            return self;
        }

        private Object callSuper(String name, Object[] a) {
            switch (name) {
                case "onCreate": super.onCreate(); return null;
                case "onShow": super.onShow(); return null;
                case "onDismiss": super.onDismiss(); return null;
                case "beforeShow": super.beforeShow(); return null;
                case "beforeDismiss": super.beforeDismiss(); return null;
                case "getImplLayoutId": return super.getImplLayoutId();
                case "getPopupWidth": return super.getPopupWidth();
                case "getPopupHeight": return super.getPopupHeight();
                case "getMaxWidth": return super.getMaxWidth();
                case "getMaxHeight": return super.getMaxHeight();
                case "addInnerContent": super.addInnerContent(); return null;
                case "onBackPressed": return super.onBackPressed();
                default: throw Context.reportRuntimeError("no super$" + name);
            }
        }

        @Override
        protected int getImplLayoutId() {
            return dInt(jsObj, tl, self, "getImplLayoutId", super.getImplLayoutId());
        }

        @Override
        protected void onCreate() {
            if (hasJs(jsObj, "onCreate")) callJs(jsObj, tl, self, "onCreate", void.class, new Object[0]);
            else super.onCreate();
        }

        @Override
        protected void onShow() {
            if (hasJs(jsObj, "onShow")) callJs(jsObj, tl, self, "onShow", void.class, new Object[0]);
            else super.onShow();
        }

        @Override
        protected void onDismiss() {
            if (hasJs(jsObj, "onDismiss")) callJs(jsObj, tl, self, "onDismiss", void.class, new Object[0]);
            else super.onDismiss();
        }

        @Override
        protected void beforeShow() {
            if (hasJs(jsObj, "beforeShow")) callJs(jsObj, tl, self, "beforeShow", void.class, new Object[0]);
            else super.beforeShow();
        }

        @Override
        protected void beforeDismiss() {
            if (hasJs(jsObj, "beforeDismiss")) callJs(jsObj, tl, self, "beforeDismiss", void.class, new Object[0]);
            else super.beforeDismiss();
        }

        @Override
        protected int getPopupWidth() {
            return dInt(jsObj, tl, self, "getPopupWidth", super.getPopupWidth());
        }

        @Override
        protected int getPopupHeight() {
            return dInt(jsObj, tl, self, "getPopupHeight", super.getPopupHeight());
        }

        @Override
        protected int getMaxWidth() {
            return dInt(jsObj, tl, self, "getMaxWidth", super.getMaxWidth());
        }

        @Override
        protected int getMaxHeight() {
            return dInt(jsObj, tl, self, "getMaxHeight", super.getMaxHeight());
        }

        @Override
        protected void addInnerContent() {
            if (hasJs(jsObj, "addInnerContent")) {
                callJs(jsObj, tl, self, "addInnerContent", void.class, new Object[0]);
            } else super.addInnerContent();
        }

        @Override
        protected boolean onBackPressed() {
            return dBool(jsObj, tl, self, "onBackPressed", super.onBackPressed());
        }

        @Override
        protected void onKeyboardHeightChange(int height) {
            if (hasJs(jsObj, "onKeyboardHeightChange")) {
                callJs(jsObj, tl, self, "onKeyboardHeightChange", void.class,
                        new Object[]{height});
            } else super.onKeyboardHeightChange(height);
        }
    }

    /** XPopup 中部弹窗桥（方法集与底部桥同，showRhinoUI 中心形用）。 */
    public static final class CenterPopupBridge extends CenterPopupView implements SelfBound {
        private final Scriptable jsObj;
        private final ThreadLocal<Scriptable> tl;
        private final BridgeSelf self;

        CenterPopupBridge(android.content.Context ctx, Scriptable jsObj,
                          ThreadLocal<Scriptable> tl) {
            super(ctx);
            this.jsObj = jsObj;
            this.tl = tl;
            this.self = new BridgeSelf(jsObj, this);
            regSupers(self, this::callSuper, "onCreate", "onShow", "onDismiss",
                    "beforeShow", "beforeDismiss", "getImplLayoutId", "getPopupWidth",
                    "getPopupHeight", "getMaxWidth", "getMaxHeight", "addInnerContent",
                    "onBackPressed");
        }

        @Override
        public BridgeSelf hkSelf() {
            return self;
        }

        private Object callSuper(String name, Object[] a) {
            switch (name) {
                case "onCreate": super.onCreate(); return null;
                case "onShow": super.onShow(); return null;
                case "onDismiss": super.onDismiss(); return null;
                case "beforeShow": super.beforeShow(); return null;
                case "beforeDismiss": super.beforeDismiss(); return null;
                case "getImplLayoutId": return super.getImplLayoutId();
                case "getPopupWidth": return super.getPopupWidth();
                case "getPopupHeight": return super.getPopupHeight();
                case "getMaxWidth": return super.getMaxWidth();
                case "getMaxHeight": return super.getMaxHeight();
                case "addInnerContent": super.addInnerContent(); return null;
                case "onBackPressed": return super.onBackPressed();
                default: throw Context.reportRuntimeError("no super$" + name);
            }
        }

        @Override
        protected int getImplLayoutId() {
            return dInt(jsObj, tl, self, "getImplLayoutId", super.getImplLayoutId());
        }

        @Override
        protected void onCreate() {
            if (hasJs(jsObj, "onCreate")) callJs(jsObj, tl, self, "onCreate", void.class, new Object[0]);
            else super.onCreate();
        }

        @Override
        protected void onShow() {
            if (hasJs(jsObj, "onShow")) callJs(jsObj, tl, self, "onShow", void.class, new Object[0]);
            else super.onShow();
        }

        @Override
        protected void onDismiss() {
            if (hasJs(jsObj, "onDismiss")) callJs(jsObj, tl, self, "onDismiss", void.class, new Object[0]);
            else super.onDismiss();
        }

        @Override
        protected void beforeShow() {
            if (hasJs(jsObj, "beforeShow")) callJs(jsObj, tl, self, "beforeShow", void.class, new Object[0]);
            else super.beforeShow();
        }

        @Override
        protected void beforeDismiss() {
            if (hasJs(jsObj, "beforeDismiss")) callJs(jsObj, tl, self, "beforeDismiss", void.class, new Object[0]);
            else super.beforeDismiss();
        }

        @Override
        protected int getPopupWidth() {
            return dInt(jsObj, tl, self, "getPopupWidth", super.getPopupWidth());
        }

        @Override
        protected int getPopupHeight() {
            return dInt(jsObj, tl, self, "getPopupHeight", super.getPopupHeight());
        }

        @Override
        protected int getMaxWidth() {
            return dInt(jsObj, tl, self, "getMaxWidth", super.getMaxWidth());
        }

        @Override
        protected int getMaxHeight() {
            return dInt(jsObj, tl, self, "getMaxHeight", super.getMaxHeight());
        }

        @Override
        protected void addInnerContent() {
            if (hasJs(jsObj, "addInnerContent")) {
                callJs(jsObj, tl, self, "addInnerContent", void.class, new Object[0]);
            } else super.addInnerContent();
        }

        @Override
        protected boolean onBackPressed() {
            return dBool(jsObj, tl, self, "onBackPressed", super.onBackPressed());
        }

        @Override
        protected void onKeyboardHeightChange(int height) {
            if (hasJs(jsObj, "onKeyboardHeightChange")) {
                callJs(jsObj, tl, self, "onKeyboardHeightChange", void.class,
                        new Object[]{height});
            } else super.onKeyboardHeightChange(height);
        }
    }

    /** RecyclerView 适配器桥（三抽象方法＋常用可覆写面）。 */
    public static final class AdapterBridge extends RecyclerView.Adapter<RecyclerView.ViewHolder>
            implements SelfBound {
        private final Scriptable jsObj;
        private final ThreadLocal<Scriptable> tl;
        private final BridgeSelf self;

        AdapterBridge(Scriptable jsObj, ThreadLocal<Scriptable> tl) {
            this.jsObj = jsObj;
            this.tl = tl;
            this.self = new BridgeSelf(jsObj, this);
            regSupers(self, this::callSuper, "onCreateViewHolder", "onBindViewHolder",
                    "getItemCount", "getItemViewType", "getItemId");
        }

        @Override
        public BridgeSelf hkSelf() {
            return self;
        }

        private Object callSuper(String name, Object[] a) {
            switch (name) {
                case "onCreateViewHolder": return null; // 抽象方法无 super
                case "onBindViewHolder": return null;   // 抽象方法无 super
                case "getItemCount": return 0;          // 抽象方法无 super
                case "getItemViewType": return super.getItemViewType(numI(a, 0));
                case "getItemId": return super.getItemId(numI(a, 0));
                default: throw Context.reportRuntimeError("no super$" + name);
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Object r = dObj(jsObj, tl, self, "onCreateViewHolder",
                    RecyclerView.ViewHolder.class, parent, viewType);
            if (r instanceof RecyclerView.ViewHolder) return (RecyclerView.ViewHolder) r;
            // JS 未定义/返回空：给个空 View 的持有者兜住 RecyclerView 的非空契约
            //（抽象方法无 super 可回；hikerPop 的适配器恒定义此方法）。
            return new ViewHolderBridge(new View(parent.getContext()), jsObj, tl);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            dVoid(jsObj, tl, self, "onBindViewHolder", holder, position);
        }

        @Override
        public int getItemCount() {
            return dInt(jsObj, tl, self, "getItemCount", 0);
        }

        @Override
        public int getItemViewType(int position) {
            return dInt(jsObj, tl, self, "getItemViewType", super.getItemViewType(position), position);
        }

        @Override
        public long getItemId(int position) {
            return dLong(jsObj, tl, self, "getItemId", super.getItemId(position), position);
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            if (hasJs(jsObj, "onAttachedToRecyclerView")) {
                callJs(jsObj, tl, self, "onAttachedToRecyclerView", void.class,
                        new Object[]{recyclerView});
            } else super.onAttachedToRecyclerView(recyclerView);
        }

        @Override
        public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
            if (hasJs(jsObj, "onDetachedFromRecyclerView")) {
                callJs(jsObj, tl, self, "onDetachedFromRecyclerView", void.class,
                        new Object[]{recyclerView});
            } else super.onDetachedFromRecyclerView(recyclerView);
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (hasJs(jsObj, "onViewRecycled")) {
                callJs(jsObj, tl, self, "onViewRecycled", void.class, new Object[]{holder});
            } else super.onViewRecycled(holder);
        }
    }

    /** ViewHolder 桥：本体无可覆写行为面，价值在 JS 自定义方法/属性（化身上）。 */
    public static final class ViewHolderBridge extends RecyclerView.ViewHolder
            implements SelfBound {
        private final BridgeSelf self;

        ViewHolderBridge(View itemView, Scriptable jsObj, ThreadLocal<Scriptable> tl) {
            super(itemView);
            this.self = new BridgeSelf(jsObj, this);
        }

        @Override
        public BridgeSelf hkSelf() {
            return self;
        }
    }

    /** ItemTouchHelper.Callback 桥（拖拽/滑动回调）。 */
    public static final class TouchCallbackBridge extends ItemTouchHelper.Callback
            implements SelfBound {
        private final Scriptable jsObj;
        private final ThreadLocal<Scriptable> tl;
        private final BridgeSelf self;

        TouchCallbackBridge(Scriptable jsObj, ThreadLocal<Scriptable> tl) {
            this.jsObj = jsObj;
            this.tl = tl;
            this.self = new BridgeSelf(jsObj, this);
            regSupers(self, this::callSuper, "getMovementFlags", "onMove", "onSwiped",
                    "isLongPressDragEnabled", "isItemViewSwipeEnabled",
                    "onSelectedChanged", "clearView");
        }

        @Override
        public BridgeSelf hkSelf() {
            return self;
        }

        private Object callSuper(String name, Object[] a) {
            switch (name) {
                case "getMovementFlags": return 0; // 抽象方法无 super，给 0
                case "onMove": return false;       // 抽象方法无 super，给 false
                case "onSwiped": return null;      // 抽象方法无 super
                case "isLongPressDragEnabled": return super.isLongPressDragEnabled();
                case "isItemViewSwipeEnabled": return super.isItemViewSwipeEnabled();
                case "onSelectedChanged":
                    super.onSelectedChanged((RecyclerView.ViewHolder) a[0], numI(a, 1));
                    return null;
                case "clearView":
                    super.clearView((RecyclerView) a[0], (RecyclerView.ViewHolder) a[1]);
                    return null;
                default: throw Context.reportRuntimeError("no super$" + name);
            }
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView,
                                    RecyclerView.ViewHolder viewHolder) {
            return dInt(jsObj, tl, self, "getMovementFlags", 0, recyclerView, viewHolder);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source,
                              RecyclerView.ViewHolder target) {
            return dBool(jsObj, tl, self, "onMove", false, recyclerView, source, target);
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            dVoid(jsObj, tl, self, "onSwiped", viewHolder, direction);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return dBool(jsObj, tl, self, "isLongPressDragEnabled",
                    super.isLongPressDragEnabled());
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return dBool(jsObj, tl, self, "isItemViewSwipeEnabled",
                    super.isItemViewSwipeEnabled());
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (hasJs(jsObj, "onSelectedChanged")) {
                callJs(jsObj, tl, self, "onSelectedChanged", void.class,
                        new Object[]{viewHolder, actionState});
            } else super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (hasJs(jsObj, "clearView")) {
                callJs(jsObj, tl, self, "clearView", void.class,
                        new Object[]{recyclerView, viewHolder});
            } else super.clearView(recyclerView, viewHolder);
        }
    }

    /** LinearSmoothScroller 桥（hikerPop 只覆写 calculateSpeedPerPixel）。 */
    public static final class SmoothScrollerBridge extends LinearSmoothScroller
            implements SelfBound {
        private final Scriptable jsObj;
        private final ThreadLocal<Scriptable> tl;
        private final BridgeSelf self;

        SmoothScrollerBridge(android.content.Context ctx, Scriptable jsObj,
                             ThreadLocal<Scriptable> tl) {
            super(ctx);
            this.jsObj = jsObj;
            this.tl = tl;
            this.self = new BridgeSelf(jsObj, this);
            regSupers(self, this::callSuper, "calculateSpeedPerPixel",
                    "getVerticalSnapPreference", "getHorizontalSnapPreference");
        }

        @Override
        public BridgeSelf hkSelf() {
            return self;
        }

        private Object callSuper(String name, Object[] a) {
            switch (name) {
                case "calculateSpeedPerPixel":
                    return super.calculateSpeedPerPixel((DisplayMetrics) a[0]);
                case "getVerticalSnapPreference": return super.getVerticalSnapPreference();
                case "getHorizontalSnapPreference": return super.getHorizontalSnapPreference();
                default: throw Context.reportRuntimeError("no super$" + name);
            }
        }

        @Override
        protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
            return dFloat(jsObj, tl, self, "calculateSpeedPerPixel",
                    super.calculateSpeedPerPixel(displayMetrics), displayMetrics);
        }

        @Override
        public int getVerticalSnapPreference() {
            return dInt(jsObj, tl, self, "getVerticalSnapPreference",
                    super.getVerticalSnapPreference());
        }

        @Override
        public int getHorizontalSnapPreference() {
            return dInt(jsObj, tl, self, "getHorizontalSnapPreference",
                    super.getHorizontalSnapPreference());
        }
    }

    /** 收藏文件夹适配器桥（本体照官移植件；JS 只覆写 onBindViewHolder 并
     * 追加 onItemMove 自定义方法，构造四参含函数形点击监听，见 listenerArg）。 */
    public static final class BookmarkFolderBridge extends BookmarkFolderAdapter
            implements SelfBound {
        private final Scriptable jsObj;
        private final ThreadLocal<Scriptable> tl;
        private final BridgeSelf self;

        BookmarkFolderBridge(android.content.Context ctx,
                             List<com.example.hikerview.model.Bookmark> list,
                             BookmarkFolderAdapter.OnItemClickListener listener,
                             boolean z, Scriptable jsObj, ThreadLocal<Scriptable> tl) {
            super(ctx, list, listener, z);
            this.jsObj = jsObj;
            this.tl = tl;
            this.self = new BridgeSelf(jsObj, this);
            regSupers(self, this::callSuper, "onBindViewHolder", "onCreateViewHolder",
                    "getItemCount");
        }

        @Override
        public BridgeSelf hkSelf() {
            return self;
        }

        private Object callSuper(String name, Object[] a) {
            switch (name) {
                case "onBindViewHolder":
                    super.onBindViewHolder((RecyclerView.ViewHolder) a[0], numI(a, 1));
                    return null;
                case "onCreateViewHolder":
                    return super.onCreateViewHolder((ViewGroup) a[0], numI(a, 1));
                case "getItemCount": return super.getItemCount();
                default: throw Context.reportRuntimeError("no super$" + name);
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (hasJs(jsObj, "onBindViewHolder")) {
                callJs(jsObj, tl, self, "onBindViewHolder", void.class,
                        new Object[]{holder, position});
            } else super.onBindViewHolder(holder, position);
        }
    }
}
