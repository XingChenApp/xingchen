package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;

/**
 * DoH 只有一套：选择结果统一存 Setting 的 "doh"（Doh 的 JSON 串，空＝系统 DNS）。
 * 本类是它的门面：内置四档（系统/腾讯/阿里/360）供星辰设置页与语音口令选用；
 * leanback 设置页从配置下发列表里选的任意 Doh 也存同一处，启动时由 apply 回放，
 * 不再有第二套存储在重启后把选择清掉。
 * 旧版存 "xingchen" 文件的下标（xingchen.doh_provider）首次使用时迁移过来并清掉。
 */
public class XingChenDoh {

    private static final String TAG = "XingChenDoh";
    private static final String LEGACY_KEY = "xingchen.doh_provider";
    public static final String[] NAMES = {"系统", "腾讯", "阿里", "360"};
    private static final String[] URLS = {"", "https://doh.pub/dns-query", "https://dns.alidns.com/dns-query", "https://doh.360.cn/dns-query"};

    /** 当前生效的 Doh；null 表示系统 DNS。 */
    public static Doh currentDoh(Context context) {
        migrateLegacy(context);
        String stored = Setting.getDoh();
        if (TextUtils.isEmpty(stored)) return null;
        Doh doh = Doh.objectFrom(stored);
        return TextUtils.isEmpty(doh.getUrl()) ? null : doh;
    }

    private static void migrateLegacy(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("xingchen", Context.MODE_PRIVATE);
            if (!prefs.contains(LEGACY_KEY)) return;
            int legacy = prefs.getInt(LEGACY_KEY, 0);
            if (legacy > 0 && legacy < URLS.length && TextUtils.isEmpty(Setting.getDoh())) {
                // 先写新值并回读校验，全等才删旧键（旧代码先删旧键再写，
                // 写失败旧设置就丢了）；不一致保留旧键待下次重试。
                String value = builtin(legacy).toString();
                Setting.putDoh(value);
                if (!value.equals(Setting.getDoh())) {
                    Log.w(TAG, "migrateLegacy verify failed, keep legacy key");
                    return;
                }
            }
            prefs.edit().remove(LEGACY_KEY).apply();
        } catch (Exception e) {
            Log.w(TAG, "migrateLegacy failed, keep legacy key", e);
        }
    }

    private static Doh builtin(int index) {
        // FongMi 原版行为：内置条目不带 ips（无 bootstrap），ips 只从配置下发来
        return new Doh().name(NAMES[index]).url(URLS[index]);
    }

    /** 内置档下标：当前选择恰是内置档时返回其下标；系统或配置下发的自定义档返回 0。 */
    public static int getIndex(Context context) {
        Doh doh = currentDoh(context);
        if (doh == null) return 0;
        for (int i = 1; i < URLS.length; i++) if (URLS[i].equals(doh.getUrl())) return i;
        return 0;
    }

    public static String getName(Context context) {
        Doh doh = currentDoh(context);
        return doh == null ? NAMES[0] : doh.getName();
    }

    public static Doh toDoh(Context context) {
        return currentDoh(context);
    }

    /** @return 主客户端与海阔客户端是否都已应用；任一失败回 false（设置值已存，但网络侧可能仍走旧 DNS）。 */
    public static boolean apply(Context context) {
        boolean ok = true;
        try {
            Doh doh = currentDoh(context);
            if (doh == null) {
                // 系统模式：清除 DoH，直接走系统 DNS
                OkHttp.dns().clearDoh();
            } else {
                OkHttp.dns().setDoh(doh);
            }
            // 注意：不要在这里 resetClients，会把封面搞没（1.1.8 教训）
        } catch (Throwable t) {
            android.util.Log.e("XingChenDoh", "apply DoH to main client failed, still on old DNS", t);
            ok = false;
        }
        // 同步应用到海阔客户端（8.83 loadDns 逻辑）
        try {
            com.fongmi.android.tv.api.hk.HkHttp.applyDoh(context);
        } catch (Throwable t) {
            android.util.Log.e("XingChenDoh", "apply DoH to hk client failed, hk still on old DNS", t);
            ok = false;
        }
        return ok;
    }

    public static void setIndex(Context context, int index) {
        if (index <= 0 || index >= URLS.length) Setting.putDoh("");
        else Setting.putDoh(builtin(index).toString());
        apply(context);
    }
}
