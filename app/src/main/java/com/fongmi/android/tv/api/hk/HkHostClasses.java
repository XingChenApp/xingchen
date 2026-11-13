package com.fongmi.android.tv.api.hk;

import org.mozilla.javascript.NativeJavaPackage;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * 8.83 宿主类在规则作用域里的可达性补齐（纯 Rhino，无 Android 依赖）。
 *
 * <p>8.83 的 {@code com.example.hikerview.*} 是 App 包内真实类，规则 JS 在任何
 * ImporterTopLevel 作用域里经包路径原生解析。星辰已按原包名补回真实类
 * （{@code com.example.hikerview.utils.FileUtil}），这类作用域无需处理；但
 * 共享作用域的 {@code com} 是宿主预置的普通 JS 对象（见 HkJsRuntime.init 的
 * RequireUtils 预置段），包解析走不到，规则直调
 * {@code com.example.hikerview.utils.FileUtil.toBytes(...)} 仍会落空。本助手
 * 在每次 API 注册时把同一宿主实例挂进对象链兜底：</p>
 * <ul>
 * <li>目标作用域的 {@code com} 是 NativeJavaPackage（ImporterTopLevel 作用域）
 * → 跳过，真实类原生解析，且不许用普通对象遮蔽整个 com 包；</li>
 * <li>{@code com} 不存在 → 新建普通对象链并挂上；</li>
 * <li>{@code com} 已是普通对象 → 只补缺失层级，FileUtil 槽位为空才写入，
 * 不覆盖已有绑定（如 init 预置段的 RequireUtils）。</li>
 * </ul>
 */
public final class HkHostClasses {

    private HkHostClasses() {
    }

    /**
     * 把 FileUtil 等价实例（已 javaToJS 包装的 __cryptoFileUtil）挂到
     * target 作用域的 com.example.hikerview.utils.FileUtil 上。
     *
     * @return true 表示本次写入了对象链；false 表示包路径原生可达或槽位已有绑定。
     */
    public static boolean installFileUtilChain(ScriptableObject target, Object fileUtil) {
        if (target == null || fileUtil == null) return false;
        try {
            Object comProp = ScriptableObject.getProperty(target, "com");
            if (comProp instanceof NativeJavaPackage) {
                // ImporterTopLevel 作用域：com.example.hikerview.utils.FileUtil
                // 是包内真实类，原生解析即可，不做任何遮蔽。
                return false;
            }
            Scriptable comObj;
            if (comProp == Scriptable.NOT_FOUND || comProp == null) {
                comObj = new NativeObject();
                ScriptableObject.putProperty(target, "com", comObj);
            } else if (comProp instanceof Scriptable) {
                comObj = (Scriptable) comProp;
            } else {
                return false;
            }
            Scriptable utils = childChain(comObj, "example", "hikerview", "utils");
            Object existing = ScriptableObject.getProperty(utils, "FileUtil");
            if (existing != Scriptable.NOT_FOUND && existing != null
                    && !(existing instanceof org.mozilla.javascript.Undefined)) {
                return false;
            }
            ScriptableObject.putProperty(utils, "FileUtil", fileUtil);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 沿普通对象逐层取/建子对象，返回末层。 */
    private static Scriptable childChain(Scriptable parent, String... names) {
        Scriptable cur = parent;
        for (String name : names) {
            Object child = ScriptableObject.getProperty(cur, name);
            if (child == Scriptable.NOT_FOUND || child == null || !(child instanceof Scriptable)) {
                NativeObject next = new NativeObject();
                ScriptableObject.putProperty(cur, name, next);
                cur = next;
            } else {
                cur = (Scriptable) child;
            }
        }
        return cur;
    }
}
