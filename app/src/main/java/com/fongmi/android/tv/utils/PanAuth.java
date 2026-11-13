package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.SpiderDebug;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;

public class PanAuth {

    private static final String PREFS = "xingchen";
    private static final String KEY_115_COOKIE = "pan115_cookie";
    private static final String KEY_115_VIP = "pan115_vip";
    private static final String KEY_115_SPACE = "pan115_space";
    private static final String KEY_115_INFO_TS = "pan115_info_ts";
    private static final String KEY_QUARK_COOKIE = "panquark_cookie";
    private static final String KEY_QUARK_VIP = "panquark_vip";
    private static final String KEY_QUARK_SPACE = "panquark_space";
    private static final String KEY_QUARK_INFO_TS = "panquark_info_ts";
    private static final String KEY_PIKPAK_VIP = "panpikpak_vip";
    private static final String KEY_PIKPAK_SPACE = "panpikpak_space";
    private static final String KEY_PIKPAK_INFO_TS = "panpikpak_info_ts";

    private static SharedPreferences prefs() {
        Context ctx = App.get();
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * 网盘 cookie 的准存储是 CloudAccountStore 加密轨（与 PikPak 同口径）。
     * 此前 115/夸克 cookie 明文躺在普通 SharedPreferences 里，与加密轨双轨并存。
     * 读时先看加密轨；加密轨没有而老明文还在，就迁过去并删明文，完成单轨收口。
     */
    private static String secureGetCookie(String driveId, String legacyKey) {
        try {
            String c = com.fongmi.android.tv.cloud.CloudAccountStore.getCookie(App.get(), driveId);
            if (c != null && !c.isEmpty()) return c;
            String legacy = prefs().getString(legacyKey, "");
            if (legacy != null && !legacy.isEmpty()) {
                com.fongmi.android.tv.cloud.CloudAccountStore.saveCookie(App.get(), driveId, legacy);
                // W7-05：写后回读校验，回读与刚写的值全等才删明文——加密轨
                // 写失败被吞时无条件删明文会把 cookie 整体丢掉；不一致就
                // 保留明文（读路下次还会重试迁移）并留痕。回读本身抛错按
                // 校验失败处理、不许把本次应返回的 legacy 一并吞掉。
                String migrated = null;
                try {
                    migrated = com.fongmi.android.tv.cloud.CloudAccountStore.getCookie(App.get(), driveId);
                } catch (Throwable ignored) {
                }
                if (legacy.equals(migrated)) {
                    prefs().edit().remove(legacyKey).apply();
                } else {
                    SpiderDebug.log("panauth", "cookie migrate verify failed drive=%s, keep legacy plaintext", driveId);
                }
                return legacy;
            }
            return "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void securePutCookie(String driveId, String legacyKey, String cookie) {
        String value = cookie == null ? "" : cookie;
        try {
            com.fongmi.android.tv.cloud.CloudAccountStore.saveCookie(App.get(), driveId, value);
            // W7-05：同迁移路——写后回读校验成功才删明文键；写失败或
            // 回读不一致都保留明文并留痕，不许新 cookie 两头落空。
            String saved = com.fongmi.android.tv.cloud.CloudAccountStore.getCookie(App.get(), driveId);
            if (value.equals(saved)) {
                prefs().edit().remove(legacyKey).apply();
            } else {
                SpiderDebug.log("panauth", "cookie save verify failed drive=%s, keep legacy plaintext", driveId);
            }
        } catch (Throwable t) {
            SpiderDebug.log("panauth", "cookie save failed drive=%s error=%s, keep legacy plaintext", driveId, t.getMessage());
        }
    }

    private static void secureClearCookie(String driveId) {
        try {
            com.fongmi.android.tv.cloud.CloudAccountStore.clear(App.get(), driveId);
        } catch (Throwable ignored) {
        }
    }

    /**
     * App 内登录流程的 115 cookie（加密存储，见 secureGetCookie）。
     * 用于 UI 登录态判定。
     */
    public static String get115Cookie() {
        return secureGetCookie("115", KEY_115_COOKIE);
    }

    /**
     * 115 分享解析用的 cookie，App 自身没有时回退读取用户 PY 脚本的 115_cookie.json。
     */
    public static String get115CookieForParser() {
        String c = get115Cookie();
        if (!c.isEmpty()) return c;
        try {
            Context ctx = App.get();
            if (ctx != null) {
                File f = new File(ctx.getFilesDir(), "plugins/py/115_cookie.json");
                if (f.exists()) {
                    String txt = readAll(f);
                    String cookie = new JSONObject(txt).optString("cookie", "");
                    if (!cookie.isEmpty()) return cookie;
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * 115 登录态 cookie 必须包含 UID / CID / SEID。
     */
    public static boolean is115CookieValid(String cookie) {
        if (cookie == null || cookie.isEmpty()) return false;
        boolean uid = false, cid = false, seid = false;
        for (String pair : cookie.split(";")) {
            String name = pair.trim();
            int eq = name.indexOf('=');
            if (eq > 0) name = name.substring(0, eq).trim();
            if ("UID".equals(name)) uid = true;
            else if ("CID".equals(name)) cid = true;
            else if ("SEID".equals(name)) seid = true;
            if (uid && cid && seid) return true;
        }
        return false;
    }

    public static void put115Cookie(String cookie) {
        securePutCookie("115", KEY_115_COOKIE, cookie);
        // 登录写 cookie 是账号信息的失效点：时间戳清零，下次进登录页必重拉
        prefs().edit().putLong(KEY_115_INFO_TS, 0).apply();
    }

    public static void clear115() {
        secureClearCookie("115");
        prefs().edit().remove(KEY_115_COOKIE).remove(KEY_115_VIP)
                .remove(KEY_115_SPACE).remove(KEY_115_INFO_TS).apply();
    }

    public static boolean is115LoggedIn() {
        return isCookieValid(get115Cookie());
    }

    /**
     * 115 登录态格式校验：必须包含 UID/CID/SEID 三个关键字段，
     * 与 115 网盘爬虫的 cookie_ok 判定一致。光有 cookie 字符串不算登录。
     */
    private static boolean isCookieValid(String cookie) {
        if (cookie == null || cookie.isEmpty()) return false;
        String upper = cookie.toUpperCase(Locale.US);
        return upper.contains("UID=") && upper.contains("CID=") && upper.contains("SEID=");
    }

    public static String get115Vip() {
        return prefs().getString(KEY_115_VIP, "");
    }

    public static void put115Vip(String vip) {
        prefs().edit().putString(KEY_115_VIP, vip == null ? "" : vip).apply();
    }

    /** 115 容量摘要（如「已用 12GB / 共 2TB」），由账号信息抓取写入 */
    public static String get115Space() {
        return prefs().getString(KEY_115_SPACE, "");
    }

    public static void put115Space(String space) {
        prefs().edit().putString(KEY_115_SPACE, space == null ? "" : space).apply();
    }

    /** 115 账号信息上次抓取尝试时刻（0=从未/已失效，须重拉） */
    public static long get115InfoTs() {
        return prefs().getLong(KEY_115_INFO_TS, 0);
    }

    public static void mark115InfoTs(long ts) {
        prefs().edit().putLong(KEY_115_INFO_TS, ts).apply();
    }

    public static String getQuarkCookie() {
        return secureGetCookie("quark", KEY_QUARK_COOKIE);
    }

    public static void putQuarkCookie(String cookie) {
        securePutCookie("quark", KEY_QUARK_COOKIE, cookie);
        // 同 115：登录写 cookie 即账号信息失效点
        prefs().edit().putLong(KEY_QUARK_INFO_TS, 0).apply();
    }

    public static void clearQuark() {
        secureClearCookie("quark");
        prefs().edit().remove(KEY_QUARK_COOKIE).remove(KEY_QUARK_VIP)
                .remove(KEY_QUARK_SPACE).remove(KEY_QUARK_INFO_TS).apply();
    }

    public static boolean isQuarkLoggedIn() {
        return !getQuarkCookie().isEmpty();
    }

    public static String getQuarkVip() {
        return prefs().getString(KEY_QUARK_VIP, "");
    }

    public static void putQuarkVip(String vip) {
        prefs().edit().putString(KEY_QUARK_VIP, vip == null ? "" : vip).apply();
    }

    /** 夸克容量摘要（如「已用 12GB / 共 6TB」），由账号信息抓取写入 */
    public static String getQuarkSpace() {
        return prefs().getString(KEY_QUARK_SPACE, "");
    }

    public static void putQuarkSpace(String space) {
        prefs().edit().putString(KEY_QUARK_SPACE, space == null ? "" : space).apply();
    }

    /** 夸克账号信息上次抓取尝试时刻（0=从未/已失效，须重拉） */
    public static long getQuarkInfoTs() {
        return prefs().getLong(KEY_QUARK_INFO_TS, 0);
    }

    public static void markQuarkInfoTs(long ts) {
        prefs().edit().putLong(KEY_QUARK_INFO_TS, ts).apply();
    }

    public static String loginSummary() {
        boolean l115 = is115LoggedIn();
        boolean lQuark = isQuarkLoggedIn();
        boolean lPikPak = isPikPakLoggedIn();
        if (!l115 && !lQuark && !lPikPak) return "未登录";
        StringBuilder sb = new StringBuilder();
        if (l115) sb.append("115");
        if (lQuark) {
            if (sb.length() > 0) sb.append("、");
            sb.append("夸克");
        }
        if (lPikPak) {
            if (sb.length() > 0) sb.append("、");
            sb.append("PikPak");
        }
        sb.append("已登录");
        return sb.toString();
    }

    // ---------- PikPak（会话由 DrivePikPak 经 CloudAccountStore 加密存储） ----------

    /**
     * PikPak 登录态缓存：原来每次 isPikPakLoggedIn() 都 new DrivePikPak，
     * 构造器做 OkHttpClient + 7 次加密存储读写，在主线程卡 UI。
     * 登录态只在登录/退出时变化，缓存后主线程只读内存。
     */
    private static volatile Boolean sPikPakLoggedIn;
    private static volatile String sPikPakUserName;

    public static boolean isPikPakLoggedIn() {
        Boolean cached = sPikPakLoggedIn;
        if (cached != null) return cached;
        boolean login = false;
        String name = "";
        try {
            com.fongmi.android.tv.cloud.drivepikpak.DrivePikPak d =
                    new com.fongmi.android.tv.cloud.drivepikpak.DrivePikPak(App.get());
            login = d.isLogin();
            if (login) name = d.getUserName();
        } catch (Throwable ignored) {
        }
        sPikPakLoggedIn = login;
        sPikPakUserName = name;
        return login;
    }

    public static String getPikPakUserName() {
        if (!isPikPakLoggedIn()) return "";
        String n = sPikPakUserName;
        return n == null ? "" : n;
    }

    /** 登录成功 / 退出登录后调用，刷新缓存 */
    public static void invalidatePikPakCache() {
        sPikPakLoggedIn = null;
        sPikPakUserName = null;
        // 登录/退出都是账号信息失效点：时间戳清零，下次进登录页必重拉
        prefs().edit().putLong(KEY_PIKPAK_INFO_TS, 0).apply();
    }

    public static void clearPikPak() {
        try {
            new com.fongmi.android.tv.cloud.drivepikpak.DrivePikPak(App.get()).logout();
        } catch (Throwable ignored) {
        }
        prefs().edit().remove(KEY_PIKPAK_VIP)
                .remove(KEY_PIKPAK_SPACE).remove(KEY_PIKPAK_INFO_TS).apply();
        invalidatePikPakCache();
    }

    public static String getPikPakVip() {
        return prefs().getString(KEY_PIKPAK_VIP, "");
    }

    public static void putPikPakVip(String vip) {
        prefs().edit().putString(KEY_PIKPAK_VIP, vip == null ? "" : vip).apply();
    }

    /** PikPak 容量摘要（如「已用 12GB / 共 10TB」），由账号信息抓取写入 */
    public static String getPikPakSpace() {
        return prefs().getString(KEY_PIKPAK_SPACE, "");
    }

    public static void putPikPakSpace(String space) {
        prefs().edit().putString(KEY_PIKPAK_SPACE, space == null ? "" : space).apply();
    }

    /** PikPak 账号信息上次抓取尝试时刻（0=从未/已失效，须重拉） */
    public static long getPikPakInfoTs() {
        return prefs().getLong(KEY_PIKPAK_INFO_TS, 0);
    }

    public static void markPikPakInfoTs(long ts) {
        prefs().edit().putLong(KEY_PIKPAK_INFO_TS, ts).apply();
    }

    private static String readAll(File f) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }
}
