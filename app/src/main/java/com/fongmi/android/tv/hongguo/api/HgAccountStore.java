package com.fongmi.android.tv.hongguo.api;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import com.fongmi.android.tv.App;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 红果登录会话加密落盘：会话 Cookie＋x-tt-token＋csrf＋账号信息（user_id /
 * name / avatar）以 JSON 序列化后 AES-GCM 加密存 App 私有目录文件
 * hg-account.enc，密钥由 AndroidKeyStore 托管（alias hg_account_key，
 * 无则生成 AES-256）——这是 hongguo-assistant 的 official-account.enc
 * 系统保护加密的等价物（定版见 metasec-integration-design-20261031.md §5）。
 *
 * <p>文件字节＝12B IV 前缀＋密文。load 无文件/损坏/解密失败一律返
 * null，clear 删文件。save 返回成败（写完读回自验，能原样读回才算
 * 成）——漏派 A-17 定根：旧版 save 失败全静默，登录面报了成功却没
 * 落盘、重启即掉登录态无从归因；登录面必须据返回值给真失败。</p>
 */
public final class HgAccountStore {

    private static final String KEY_ALIAS = "hg_account_key";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String FILE_NAME = "hg-account.enc";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static final Gson GSON = new Gson();

    private HgAccountStore() {
    }

    /** 登录会话（字段与落盘 JSON 同名，gson 直接序列化/反序列化）。 */
    public static class Session {
        public String userId;
        public String name;
        public String avatar;
        public String cookie;
        public String ttToken;
        public String csrf;
    }

    /** 读回会话；无文件、损坏或解密/解析失败返 null。 */
    public static Session load() {
        try {
            File file = file();
            if (file == null || !file.isFile()) return null;
            byte[] all = readFully(file);
            if (all == null || all.length <= IV_BYTES) return null;
            byte[] iv = new byte[IV_BYTES];
            byte[] body = new byte[all.length - IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            System.arraycopy(all, IV_BYTES, body, 0, body.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            String json = new String(cipher.doFinal(body), StandardCharsets.UTF_8);
            return GSON.fromJson(json, Session.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 已登录判定：会话在且 Cookie 含 sessionid。 */
    public static boolean isLoggedIn() {
        Session s = load();
        return s != null && s.cookie != null && s.cookie.contains("sessionid");
    }

    /**
     * 加密落盘；写完读回自验、能原样读回才返 true（漏派 A-17）。
     * 全树唯一调用方是登录面，它据此决定报成功还是真失败。
     */
    public static boolean save(Session session) {
        try {
            if (session == null) return false;
            File file = file();
            if (file == null) return false;
            // IV 由 Cipher 生成再取回（AndroidKeyStore 密钥默认要求随机加密、
            // 调用方自带 IV 加密会被拒；生成 IV 恒 12B，与 GitCloudTokenStore 同口径）。
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != IV_BYTES) return false;
            byte[] body = cipher.doFinal(GSON.toJson(session).getBytes(StandardCharsets.UTF_8));
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(iv);
                out.write(body);
                out.flush();
            }
            // 读回自验：写完读不回等于没落盘（重启即掉登录态），
            // 按失败报，让登录面给得出真失败、不报假成功。
            Session back = load();
            return back != null
                    && java.util.Objects.equals(back.cookie, session.cookie)
                    && java.util.Objects.equals(back.userId, session.userId);
        } catch (Exception e) {
            return false;
        }
    }

    /** 登出：删会话文件。 */
    public static void clear() {
        try {
            File file = file();
            if (file != null && file.exists()) file.delete();
        } catch (Exception e) {
            // 静默。
        }
    }

    private static File file() {
        try {
            App app = App.get();
            return app == null ? null : new File(app.getFilesDir(), FILE_NAME);
        } catch (Exception e) {
            return null;
        }
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            SecretKey existing = (SecretKey) store.getKey(KEY_ALIAS, null);
            if (existing != null) return existing;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    private static byte[] readFully(File file) {
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
