package com.fongmi.android.tv.ui.lab;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 环境包公开仓目录（上游 lab_template 口径的星辰版）：
 * assets/lab_env_catalog.json —— 11 个环境的 id/name/version/downloads[]，
 * downloads[] 每项 {arch, url, size, sha256, mirrors[], file?}。
 * 目录还没生成时全空，安装回退旧下载地址口径（见 LabRuntimeManager）。
 *
 * 仓包命名约定（大改包换新文件名，禁止原名覆盖，防 CDN 按 URL 缓存旧包）：
 * 整包 env-<包id>-<版本号>.zip（如 env-python-3.14.8-1.zip），
 * 分片清单 env-<包id>-<版本号>.json；版本号用条目 version 原样。
 * downloads[] 的 url 尾段即包名；也可显式给 file 字段（优先用它）。
 * 老文件名文件保留（老版本 App 还指着它），只增不覆盖。
 *
 * 对接点：仓代理产出 ~/workspace/lab-assets-manifest.json 后，主会话转成此资产文件打包。
 */
public final class LabEnvCatalog {

    public static class Download {
        public String arch = "";
        public String url = "";
        public long size = 0;
        public String sha256 = "";
        /** 包文件名（可选）：显式给就优先用它；不给从 url 尾段取 */
        public String file = "";
        public final List<String> mirrors = new ArrayList<>();

        /** 主地址＋镜像依次试 */
        public List<String> allUrls() {
            List<String> out = new ArrayList<>();
            if (!TextUtils.isEmpty(url)) out.add(url);
            out.addAll(mirrors);
            return out;
        }
    }

    public static class EnvEntry {
        public String id = "";
        public String name = "";
        public String version = "";
        public final List<Download> downloads = new ArrayList<>();
    }

    private LabEnvCatalog() {
    }

    private static Map<String, EnvEntry> cache;

    private static synchronized Map<String, EnvEntry> all(Context c) {
        if (cache != null) return cache;
        cache = new HashMap<>();
        try (InputStream in = c.getAssets().open("lab_env_catalog.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            JSONObject root = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            JSONArray envs = root.optJSONArray("envs");
            if (envs != null) {
                for (int i = 0; i < envs.length(); i++) {
                    JSONObject e = envs.optJSONObject(i);
                    if (e == null) continue;
                    EnvEntry entry = new EnvEntry();
                    entry.id = e.optString("id", "");
                    entry.name = e.optString("name", entry.id);
                    entry.version = e.optString("version", "");
                    JSONArray ds = e.optJSONArray("downloads");
                    if (ds != null) {
                        for (int j = 0; j < ds.length(); j++) {
                            JSONObject d = ds.optJSONObject(j);
                            if (d == null) continue;
                            Download dl = new Download();
                            dl.arch = d.optString("arch", "").trim().toLowerCase();
                            dl.url = d.optString("url", "");
                            dl.size = d.optLong("size", 0);
                            dl.sha256 = d.optString("sha256", "");
                            dl.file = d.optString("file", "").trim();
                            JSONArray ms = d.optJSONArray("mirrors");
                            if (ms != null) {
                                for (int k = 0; k < ms.length(); k++) {
                                    String m = ms.optString(k, "");
                                    if (!TextUtils.isEmpty(m)) dl.mirrors.add(m);
                                }
                            }
                            if (!TextUtils.isEmpty(dl.url)) entry.downloads.add(dl);
                        }
                    }
                    if (!TextUtils.isEmpty(entry.id)) cache.put(entry.id, entry);
                }
            }
        } catch (Exception ignored) {
        }
        return cache;
    }

    public static EnvEntry entry(Context c, String id) {
        // 运行时 id 是 nodejs、仓目录 id 是 node，进门先经全仓唯一归一入口对齐，
        // node / nodejs 两种写法都落到仓侧键，别让 Node 查无此包
        String rt = LabManifest.normalizeRuntime(id);
        if ("nodejs".equals(rt)) rt = "node";
        return all(c).get(rt);
    }

    /**
     * 架构匹配选包（上游 LabEnv.arch 口径）：按本机 ABI 挑 downloads[]，
     * 挑不到返回 null（调用方报「没有你手机架构的包」）。
     */
    public static Download chooseDownload(EnvEntry entry) {
        if (entry == null) return null;
        String[] abis = Build.SUPPORTED_ABIS;
        // 第一趟只认精确相等（arch 为空＝通用包）：原写法 a.contains(arch) 会让
        // "arm64-v8a" 命中 arch="arm"的 32 位包，先精确、再前缀，两趟都落空才返回 null
        for (String abi : abis) {
            String a = abi.toLowerCase();
            for (Download d : entry.downloads) {
                if (d.arch.isEmpty() || a.equals(d.arch)) return d;
            }
        }
        // 第二趟前缀匹配（abi 以 arch 开头，如 abi=arm64-v8a 对 arch=arm64），
        // 不再用反向 contains（arch 把 abi 包住那种宽松命中已去掉）
        for (String abi : abis) {
            String a = abi.toLowerCase();
            for (Download d : entry.downloads) {
                if (!d.arch.isEmpty() && a.startsWith(d.arch)) return d;
            }
        }
        return null;
    }

}
