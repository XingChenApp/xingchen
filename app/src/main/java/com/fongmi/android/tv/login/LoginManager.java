package com.fongmi.android.tv.login;

import android.app.Activity;
import android.text.TextUtils;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.ui.dialog.PanLoginDialog;

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
        register(new KuaishouLoginProvider());
        register(new GoogleLoginProvider());
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

    public void logout(String providerId) {
        LoginProvider p = getProvider(providerId);
        if (p != null) p.clear();
    }

    /**
     * 弹 115 登录框：先选登录方式（扫码 / 账号密码 / Cookie 导入，全是真能用的），
     * 再进具体流程。异步，登录结果走 callback。
     */
    public void login115(FragmentActivity activity, LoginCallback callback) {
        login115(activity, (LoginMethod) null, callback);
    }

    /**
     * 直接进指定登录方式，methodId 见 {@link LoginMethod#id()}（"qr" / "password" / "cookie"；
     * 已删的扫码变体旧 id 由 fromId 映射到扫码正本）。
     * 传 null 或非法值时退回方式选择列表。
     */
    public void login115(FragmentActivity activity, String methodId, LoginCallback callback) {
        login115(activity, LoginMethod.fromId(methodId), callback);
    }

    public void login115(FragmentActivity activity, LoginMethod method, LoginCallback callback) {
        if (activity == null || activity.isFinishing()) {
            if (callback != null) callback.onError("界面不可用");
            return;
        }
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
            if (f instanceof LoginMethodDialog || f instanceof QrLoginDialog || f instanceof CookieImportDialog || f instanceof PwdLoginDialog) return;
        }
        if (method == null) {
            LoginMethodDialog.create()
                    .callback(callback)
                    .onPick(picked -> openMethod(activity, picked, callback))
                    .show(activity);
            return;
        }
        openMethod(activity, method, callback);
    }

    /**
     * 弹快手网页登录框：走壳子统一弹窗（与网盘网页登录同款 PanLoginDialog，
     * 中间弹窗、WebView 内完成快手网页登录），抓到的 Cookie 组校验后落盘托管。
     * 异步，登录成功走 callback；用户直接关弹窗视为取消，不回调。
     */
    public void loginKuaishou(FragmentActivity activity, LoginCallback callback) {
        if (activity == null || activity.isFinishing()) {
            if (callback != null) callback.onError("界面不可用");
            return;
        }
        PanLoginDialog.create()
                .title("快手")
                .loginUrl(KuaishouLoginProvider.LOGIN_URL)
                .cookieDomain(KuaishouLoginProvider.COOKIE_DOMAIN)
                .userAgent(PanLoginDialog.DESKTOP_UA)
                .hint("请在下方页面完成快手登录，然后点「完成登录」")
                .cookieValidator(KuaishouLoginProvider::isCookieValid)
                .onLogin(cookie -> {
                    KuaishouLoginProvider.putCookie(cookie);
                    if (callback != null) callback.onSuccess(KuaishouLoginProvider.ID, cookie);
                })
                .showLogin(activity);
    }

    /**
     * 弹谷歌网页登录框：走壳子统一弹窗（与快手同款 PanLoginDialog，
     * 中间弹窗、WebView 内在油管音乐页完成谷歌账号登录），抓到的 Cookie
     * 校验（须含 SAPISID 系种子）后落盘托管，供油管音乐源带态请求。
     * 异步，登录成功走 callback；用户直接关弹窗视为取消，不回调。
     */
    public void loginGoogle(FragmentActivity activity, LoginCallback callback) {
        if (activity == null || activity.isFinishing()) {
            if (callback != null) callback.onError("界面不可用");
            return;
        }
        PanLoginDialog.create()
                .title("谷歌")
                .loginUrl(GoogleLoginProvider.LOGIN_URL)
                .cookieDomain(GoogleLoginProvider.COOKIE_DOMAIN)
                .userAgent(PanLoginDialog.GOOGLE_UA)
                .hint("请在下方页面完成谷歌账号登录，然后点「完成登录」")
                .cookieValidator(GoogleLoginProvider::isCookieValid)
                .onLogin(cookie -> {
                    GoogleLoginProvider.putCookie(cookie);
                    if (callback != null) callback.onSuccess(GoogleLoginProvider.ID, cookie);
                })
                .showLogin(activity);
    }

    private void openMethod(FragmentActivity activity, LoginMethod method, LoginCallback callback) {
        if (method == LoginMethod.COOKIE) {
            CookieImportDialog.create().callback(callback).show(activity);
        } else if (method == LoginMethod.PASSWORD) {
            PwdLoginDialog.create().callback(callback).show(activity);
        } else if (method.isQr()) {
            QrLoginDialog.create(method).callback(callback).show(activity);
        }
    }

    // ---------------- 静态桥：给 py / js / 海阔规则直接调 ----------------

    private static FragmentActivity currentActivity() {
        Activity a = App.activity();
        return a instanceof FragmentActivity ? (FragmentActivity) a : null;
    }

    /** 弹壳子统一的 115 登录框（先选方式再进流程，异步）。py 调完轮询 {@link #is115LoggedIn()} 即可。 */
    public static void login115() {
        FragmentActivity activity = currentActivity();
        if (activity == null) return;
        App.post(() -> get().login115(activity, null));
    }

    /**
     * 直接进指定 115 登录方式（异步）。
     * methodId：qr / password / cookie（旧变体 id web_qr 等映射到扫码正本）；其它值退回方式选择列表。
     */
    public static void login115(String methodId) {
        FragmentActivity activity = currentActivity();
        if (activity == null) return;
        App.post(() -> get().login115(activity, methodId, null));
    }

    /** 115 是否已登录（凭据有效） */
    public static boolean is115LoggedIn() {
        return isLoggedIn("115");
    }

    /** 115 登录 cookie，未登录返回空串 */
    public static String get115Cookie() {
        return getCookie("115");
    }

    /** 退出 115 登录 */
    public static void logout115() {
        get().logout("115");
    }

    /** 弹壳子统一的快手网页登录框（异步）。 */
    public static void loginKuaishou() {
        FragmentActivity activity = currentActivity();
        if (activity == null) return;
        App.post(() -> get().loginKuaishou(activity, null));
    }

    /** 快手是否已登录（托管 Cookie 格式有效） */
    public static boolean isKuaishouLoggedIn() {
        return isLoggedIn(KuaishouLoginProvider.ID);
    }

    /** 快手登录 cookie，未登录返回空串（快手直播弹幕 provider 即读此口） */
    public static String getKuaishouCookie() {
        return getCookie(KuaishouLoginProvider.ID);
    }

    /** 退出快手登录 */
    public static void logoutKuaishou() {
        get().logout(KuaishouLoginProvider.ID);
    }

    /** 弹壳子统一的谷歌网页登录框（异步）。 */
    public static void loginGoogle() {
        FragmentActivity activity = currentActivity();
        if (activity == null) return;
        App.post(() -> get().loginGoogle(activity, null));
    }

    /** 谷歌是否已登录（托管 Cookie 含 SAPISID 系种子） */
    public static boolean isGoogleLoggedIn() {
        return isLoggedIn(GoogleLoginProvider.ID);
    }

    /** 谷歌登录 cookie，未登录返回空串（油管音乐源即读此口） */
    public static String getGoogleCookie() {
        return getCookie(GoogleLoginProvider.ID);
    }

    /** 退出谷歌登录 */
    public static void logoutGoogle() {
        get().logout(GoogleLoginProvider.ID);
    }

    /** 弹壳子统一的 QQ 音乐网页登录框（异步）。 */
    // 扫描 O13：谷歌/快手/115 三家补同型回执——requestLogin 此前对这三家无条件
    // return true，而静态桥在无前台界面时静默空转，FreeJS 桥把假回执原样回给插件，
    // 插件按「已请求登录」继续等登录态、登录链无声卡死。回执口径各家一致。
    public static boolean loginGoogleDelivered() {
        FragmentActivity activity = currentActivity();
        if (activity == null || activity.isFinishing()) return false;
        App.post(() -> get().loginGoogle(activity, null));
        return true;
    }

    public static boolean loginKuaishouDelivered() {
        FragmentActivity activity = currentActivity();
        if (activity == null || activity.isFinishing()) return false;
        App.post(() -> get().loginKuaishou(activity, null));
        return true;
    }

    public static boolean login115Delivered() {
        FragmentActivity activity = currentActivity();
        if (activity == null || activity.isFinishing()) return false;
        App.post(() -> get().login115(activity, null));
        return true;
    }

    /** QQ 是否已登录（托管 Cookie 含账号标识＋音乐钥匙） */
    /** QQ 登录 cookie，未登录返回空串（tx 取链兜底即读此口） */
    /** 退出 QQ 登录 */
    /**
     * 通用：按提供方 id 请壳子弹统一登录框（异步），返回的是投递结果：
     * 各家一致——无前台界面／界面正在关闭时弹窗投不出去、回 false（见各 *Delivered）。
     * 给不知道具体提供方 UI 细节的调用方（如 FreeJS 桥按插件声明的
     * loginProvider 触发）用；新增网页型提供方时在此登记一行分发。
     */
    public static boolean requestLogin(String providerId) {
        if (providerId == null) return false;
        // 四家回执统一如实（O13）：都走带回执的投递，投不出去（无前台界面）回 false
        if (GoogleLoginProvider.ID.equals(providerId)) {
            return loginGoogleDelivered();
        }
        if (KuaishouLoginProvider.ID.equals(providerId)) {
            return loginKuaishouDelivered();
        }
        if ("115".equals(providerId)) {
            return login115Delivered();
        }
        return false;
    }

    /** 通用：某提供方是否已登录 */
    public static boolean isLoggedIn(String providerId) {
        LoginProvider p = get().getProvider(providerId);
        return p != null && p.isLoggedIn();
    }

    /** 通用：取某提供方的登录凭据 */
    public static String getCookie(String providerId) {
        LoginProvider p = get().getProvider(providerId);
        return p == null ? "" : p.getCookie();
    }

    // ---------------- 115 API 网关：自动登录拦截 ----------------
    // py 调 115 接口走这里，不用写任何登录代码。未登录时自动弹框，
    // 登录成功后用新 cookie 自动重发一次，全程 py 无感。
    //
    // from java import jclass
    // LM = jclass("com.fongmi.android.tv.login.LoginManager")
    // body = LM.api115("https://webapi.115.com/files?aid=1&cid=0&o=user&format=json")

    /** 同步 GET 115 接口（自动带 cookie，未登录自动弹框登录后重试），返回响应体 */
    public static String api115(String url) throws Exception {
        return AutoLogin115.get(url);
    }

    /** 同步 POST 表单到 115 接口，formJson 如 {"offset":0,"limit":50}，返回响应体 */
    public static String api115Post(String url, String formJson) throws Exception {
        return AutoLogin115.post(url, formJson);
    }

    /** 确保 115 已登录并返回 cookie（未登录时阻塞弹框等待），失败返回空串 */
    public static String ensure115Cookie() {
        return AutoLogin115.ensureCookie();
    }
}
