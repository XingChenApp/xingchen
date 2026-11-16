package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class PlaybackConfigIdentity {

    private PlaybackConfigIdentity() {
    }

    public static String currentKey() {
        return keyForUrl(VodConfig.getUrl());
    }

    public static String currentName() {
        return safe(VodConfig.getDesc());
    }

    public static String keyForCid(int cid) {
        Config config = Config.find(cid);
        if (config == null || TextUtils.isEmpty(config.getUrl())) return currentKey();
        return keyForUrl(config.getUrl());
    }

    public static String nameForCid(int cid) {
        Config config = Config.find(cid);
        if (config == null || TextUtils.isEmpty(config.getDesc())) return currentName();
        return config.getDesc();
    }

    public static int cidForKey(String configKey) {
        configKey = normalizeKey(configKey);
        if (TextUtils.isEmpty(configKey)) return 0;
        for (Config config : Config.getAll(0)) {
            if (config == null || TextUtils.isEmpty(config.getUrl())) continue;
            if (TextUtils.equals(configKey, keyForUrl(config.getUrl()))) return config.getId();
        }
        return 0;
    }

    /**
     * 与 {@link #cidForKey} 的区别只在未命中：cidForKey 以 0 表
     * 未命中，但 0 本身是合法 cid（历史读路 dao.find(0, …) 恒
     * 在用、行键尾段 @@@0 是存量实证）。删除/写入的目标解析
     * 必须把「查无此接口」与「命中 0 号接口」分开：本口空键
     * 或未命中回 -1，命中回其 id（可为 0）。
     */
    public static int resolveCidForKey(String configKey) {
        configKey = normalizeKey(configKey);
        if (TextUtils.isEmpty(configKey)) return -1;
        for (Config config : Config.getAll(0)) {
            if (config == null || TextUtils.isEmpty(config.getUrl())) continue;
            if (TextUtils.equals(configKey, keyForUrl(config.getUrl()))) return config.getId();
        }
        return -1;
    }

    public static String keyForUrl(String url) {
        url = safe(url).trim();
        return TextUtils.isEmpty(url) ? "" : sha256(url);
    }

    public static String normalizeKey(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(safe(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) builder.append(String.format(Locale.ROOT, "%02x", b));
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
