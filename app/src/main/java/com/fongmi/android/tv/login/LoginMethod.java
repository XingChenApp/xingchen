package com.fongmi.android.tv.login;

/**
 * 115 登录方式。只列真能走完的方式，不留占位：
 * <ul>
 *   <li>扫码：唯一正本 {@link Qr115Provider}，逐环照 115 网盘 py 爬虫里真机验证过的
 *       QrLogin 流程（app=android、qrcodeapi 取码/轮询、passportapi confirm 换 Cookie）。</li>
 *   <li>账号密码：{@link Pwd115Provider} 直登（getKey 取 RSA 公钥加密密码后 POST
 *       /login/login），服务端实测可达凭据校验环。</li>
 *   <li>Cookie 导入：本地解析 + 格式校验。</li>
 * </ul>
 * 手机号（短信）登录未上列表：发码环实测可通（POST /sms/login 只带 account 回 state=1），
 * 但校验环的验证码字段名无凭证探不死（假号无待校验码，code/sms_code 都只回
 * 「请重新获取验证码」），按潇洒哥口径做不真就不上，不许摆名占位。
 * 旧 id（web_qr/app_qr/mac_qr）是已删除的扫码变体，fromId 一律映射到扫码正本，
 * 源侧（py/js）传旧 id 不断链。
 */
public enum LoginMethod {

    QR("qr", "扫码登录", "用 115 App 扫码登录"),
    PASSWORD("password", "账号密码", "输入 115 账号和密码登录"),
    COOKIE("cookie", "Cookie 导入", "粘贴 115 Cookie 直接登录");

    private final String id;
    private final String title;
    private final String desc;

    LoginMethod(String id, String title, String desc) {
        this.id = id;
        this.title = title;
        this.desc = desc;
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

    public boolean isQr() {
        return this == QR;
    }

    /** 列表里的每个方式都是真能用的（占位方式已全删） */
    public boolean isImplemented() {
        return true;
    }

    public static LoginMethod fromId(String id) {
        if (id == null) return null;
        // 已删的扫码变体 id 全部归到唯一正本
        if ("web_qr".equals(id) || "app_qr".equals(id) || "mac_qr".equals(id)) return QR;
        for (LoginMethod m : values()) if (m.id.equals(id)) return m;
        return null;
    }
}
