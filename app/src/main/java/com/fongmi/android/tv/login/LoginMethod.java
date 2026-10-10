package com.fongmi.android.tv.login;

/**
 * 115 登录方式。
 * <p>
 * 扫码类（Web / App / Mac）走 115 官方二维码接口，已实测接通：
 * qrcodeapi.115.com/api/1.0/{app}/1.0/token/（web、android、ios、mac 均返回有效 uid/time/sign）。
 * Cookie 导入为本地解析 + 格式校验。
 * 账号密码 / 短信验证 / 微信小程序 / 支付宝小程序待 115 协议抓包确认后接入，
 * 列表中先占位，点选时明确提示「正在接入中」，不伪造请求。
 */
public enum LoginMethod {

    WEB_QR("web_qr", "Web 扫码", "用 115 App 扫码，登录网页端", "web", true),
    APP_QR("app_qr", "App 扫码", "用 115 App 扫码，登录手机端", "android", true),
    MAC_QR("mac_qr", "Mac 扫码", "用 115 App 扫码，登录 Mac 客户端", "mac", true),
    PASSWORD("password", "账号密码", "输入 115 账号和密码登录", null, false),
    SMS("sms", "短信验证", "手机号接收验证码登录", null, false),
    COOKIE("cookie", "Cookie 导入", "粘贴 115 Cookie 直接登录", null, true),
    WECHAT_MINI("wechat_mini", "微信小程序", "微信内打开 115 小程序扫码", null, false),
    ALIPAY_MINI("alipay_mini", "支付宝小程序", "支付宝内打开 115 小程序扫码", null, false);

    private final String id;
    private final String title;
    private final String desc;
    /** 扫码类方式对应的 115 app 参数；非扫码方式为 null */
    private final String qrApp;
    private final boolean implemented;

    LoginMethod(String id, String title, String desc, String qrApp, boolean implemented) {
        this.id = id;
        this.title = title;
        this.desc = desc;
        this.qrApp = qrApp;
        this.implemented = implemented;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String desc() {
        return desc;
    }

    public String qrApp() {
        return qrApp;
    }

    public boolean isQr() {
        return qrApp != null;
    }

    public boolean isImplemented() {
        return implemented;
    }

    public static LoginMethod fromId(String id) {
        if (id == null) return null;
        for (LoginMethod m : values()) if (m.id.equals(id)) return m;
        return null;
    }
}
