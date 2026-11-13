package com.fongmi.android.tv.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 快手登录提供方（壳子统一登录托管）：注册在位、Cookie 校验口径、
 * 经 LoginManager 的存入→读出→清除整链，以及与 115 提供方的隔离。
 * 存储后端是与 115 同轨的 CloudAccountStore（已在产），离线运行 harness
 * 以同签名内存桩顶替其 Android 加密存储本体，验的是托管链语义。
 */
public class KuaishouLoginProviderTest {

    /** 与快手直播弹幕 provider 对拍夹具一致的登录 Cookie 形态。 */
    private static final String LOGIN_COOKIE = "did=web_abc; kwfv1=abc%20def; userId=424242";

    @Test
    public void providerRegisteredWithAlignedId() {
        LoginProvider provider = LoginManager.get().getProvider("kuaishou");
        assertNotNull(provider);
        assertTrue(provider instanceof KuaishouLoginProvider);
        assertEquals(KuaishouLoginProvider.ID, provider.id());
        assertEquals("快手", provider.name());
        // 与弹幕 provider 的读取口径逐字对齐
        assertEquals(com.fongmi.android.tv.player.danmaku.live.kuaishou.KuaishouProvider.LOGIN_PROVIDER_ID, provider.id());
    }

    @Test
    public void cookieValidityRequiresKwfv1AndUserId() {
        assertTrue(KuaishouLoginProvider.isCookieValid(LOGIN_COOKIE));
        assertTrue(KuaishouLoginProvider.isCookieValid("userId=1; kwfv1=x; extra=y"));
        assertFalse(KuaishouLoginProvider.isCookieValid("did=web_abc; userId=424242"));
        assertFalse(KuaishouLoginProvider.isCookieValid("did=web_abc; kwfv1=abc%20def"));
        assertFalse(KuaishouLoginProvider.isCookieValid("kwfv1=; userId=424242"));
        assertFalse(KuaishouLoginProvider.isCookieValid("kwfv1=x; userId="));
        assertFalse(KuaishouLoginProvider.isCookieValid("did=web_abc"));
        assertFalse(KuaishouLoginProvider.isCookieValid(""));
        assertFalse(KuaishouLoginProvider.isCookieValid(null));
    }

    @Test
    public void hostedRoundTripThroughLoginManager() {
        LoginManager.logoutKuaishou();
        assertEquals("", LoginManager.getCookie("kuaishou"));
        assertFalse(LoginManager.isKuaishouLoggedIn());

        KuaishouLoginProvider.putCookie(LOGIN_COOKIE);
        assertEquals(LOGIN_COOKIE, LoginManager.getCookie("kuaishou"));
        assertEquals(LOGIN_COOKIE, LoginManager.getKuaishouCookie());
        assertTrue(LoginManager.isKuaishouLoggedIn());
        assertTrue(LoginManager.isLoggedIn("kuaishou"));

        LoginManager.logoutKuaishou();
        assertEquals("", LoginManager.getCookie("kuaishou"));
        assertFalse(LoginManager.isKuaishouLoggedIn());
    }

    @Test
    public void invalidCookieStoredRawButNotCountedAsLoggedIn() {
        // 托管层原样存取（校验只决定登录态判定，与弹幕 provider 的透传口径一致）
        KuaishouLoginProvider.putCookie("did=web_abc");
        assertEquals("did=web_abc", LoginManager.getKuaishouCookie());
        assertFalse(LoginManager.isKuaishouLoggedIn());
        LoginManager.logoutKuaishou();
    }

    @Test
    public void provider115UnaffectedByKuaishouChain() {
        assertNotNull(LoginManager.get().getProvider("115"));
        String cookie115Before = LoginManager.get115Cookie();
        boolean logged115Before = LoginManager.is115LoggedIn();

        KuaishouLoginProvider.putCookie(LOGIN_COOKIE);
        assertEquals(cookie115Before, LoginManager.get115Cookie());
        assertEquals(logged115Before, LoginManager.is115LoggedIn());
        LoginManager.logoutKuaishou();
        assertEquals(cookie115Before, LoginManager.get115Cookie());
        assertEquals(logged115Before, LoginManager.is115LoggedIn());

        // 反向隔离由存储键分离保证（CloudAccountStore 按提供方 id 分键、
        // 115 另有自己的 prefs 键），此处再验快手清除后 115 读口仍是原值
        KuaishouLoginProvider.putCookie(LOGIN_COOKIE);
        LoginManager.logoutKuaishou();
        assertEquals(cookie115Before, LoginManager.get115Cookie());
    }
}
