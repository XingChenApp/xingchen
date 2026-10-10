package com.fongmi.android.tv.login;

import android.app.Activity;
import android.text.TextUtils;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 壳子统一登录管理器。
 * <p>
 * 所有源的登录（py / js / 海阔规则，不只是 115：扫码、cookie 管理等）都走这里，
 * 源直接调用本类的静态方法，不用每个源自己实现登录流程。
 * <p>
 * py 调用示例（Chaquopy）：
 * <pre>
 * from java import jclass
 * LM = jclass("com.fongmi.android.tv.login.LoginManager")
 * LM.login115()              # 弹壳子统一的扫码登录框（异步）
 * LM.is115LoggedIn()         # 轮询登录态
 * cookie = LM.get115Cookie() # 拿到 cookie 接着干活
 * </pre>
 */
public class LoginManager {

    public interface LoginCallback {
        void onSuccess(String providerId, String cookie);

        void onError(String msg);

        void onCancel();
    }

    private static volatile LoginManager instance;

    private final Map<String, LoginProvider> providers = new LinkedHashMap<>();

    private LoginManager() {
        register(new Qr115Provider());
    }

    public static LoginManager get() {
        if (instance == null) {
            synchronized (LoginManager.class) {
                if (instance == null) instance = new LoginManager();
            }
        }
        return instance;
    }

    public void register(LoginProvider provider) {
        if (provider != null && !TextUtils.isEmpty(provider.id())) providers.put(provider.id(), provider);
    }

    public LoginProvider getProvider(String id) {
        return providers.get(id);
    }

    public boolean isLoggedIn(String providerId) {
        LoginProvider p = getProvider(providerId);
        return p != null && p.isLoggedIn();
    }

    public String getCookie(String providerId) {
        LoginProvider p = getProvider(providerId);
        return p == null ? "" : p.getCookie();
    }

    public void logout(String providerId) {
        LoginProvider p = getProvider(providerId);
        if (p != null) p.clear();
    }

    /**
     * 弹 115 扫码登录框（异步，登录结果走 callback）。
     */
    public void login115(FragmentActivity activity, LoginCallback callback) {
        if (activity == null || activity.isFinishing()) {
            if (callback != null) callback.onError("界面不可用");
            return;
        }
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
            if (f instanceof QrLoginDialog) return;
        }
        QrLoginDialog.create().callback(callback).show(activity.getSupportFragmentManager(), null);
    }

    // ---------------- 静态桥：给 py / js / 海阔规则直接调 ----------------

    private static FragmentActivity currentActivity() {
        Activity a = App.activity();
        return a instanceof FragmentActivity ? (FragmentActivity) a : null;
    }

    /** 弹壳子统一的 115 扫码登录框（异步）。py 调完轮询 {@link #is115LoggedIn()} 即可。 */
    public static void login115() {
        FragmentActivity activity = currentActivity();
        if (activity == null) return;
        App.post(() -> get().login115(activity, null));
    }

    /** 115 是否已登录（凭据有效） */
    public static boolean is115LoggedIn() {
        return get().isLoggedIn("115");
    }

    /** 115 登录 cookie，未登录返回空串 */
    public static String get115Cookie() {
        return get().getCookie("115");
    }

    /** 退出 115 登录 */
    public static void logout115() {
        get().logout("115");
    }

    /** 通用：某提供方是否已登录 */
    public static boolean isLoggedIn(String providerId) {
        return get().isLoggedIn(providerId);
    }

    /** 通用：取某提供方的登录凭据 */
    public static String getCookie(String providerId) {
        return get().getCookie(providerId);
    }
}
