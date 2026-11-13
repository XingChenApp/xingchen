package com.fongmi.android.tv.api.hk;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import com.fongmi.android.tv.api.SiteApi;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 短剧类「本地代理整集下载」规则的点播详情预热（P0）。
 *
 * <p>机制（诊断 ~/workspace/本机诊断账 已证实）：这类规则的集地址是
 * {@code 127.0.0.1:PORT/proxy?rg=&k=&u=} 形态的本地代理 URL，代理在收到播放请求时才同步整包
 * 下载加密视频（CDN 约 24KB/s，一集 10–40 分钟），播放器 15 秒读超时先抛错 → 黑屏转圈。
 * 文章页能播是因为规则页 JS 的 {@code hgWarm} 预热提前把首集密文下好了；点播详情页不执行
 * 规则页 JS，预热永不发生，每集都是冷启动。</p>
 *
 * <p>本类就是 {@code hgWarm(vid, withFile=true)} 在点播链的等价物：详情打开时后台预解析当前集
 * 的懒地址，命中本地代理形态则把该集加密文件预下载到与代理按需下载**同一落点**
 * （{@code hiker://files/cache/rgs_<vid>_<p>.mp4}），已存在则跳过。</p>
 *
 * <p>写冲突避让：预热经临时文件 {@code .part} 下载、完成后再原子改名，代理的
 * {@code hgpExist} 永远看不到半截文件；同一落点已有预热在跑则跳过（IN_PROGRESS）。
 * 即使与代理的按需下载撞车，最终落盘的也是完整文件（内容相同），只是多费一点流量。</p>
 *
 * <p>触发门限极严：只有最终解析出 {@code 127.0.0.1|localhost /proxy + rg/k/u} 的集才下载，
 * 普通 http/m3u8/磁力/pics 的海阔规则与所有非海阔源零影响。</p>
 */
public class HkProxyPrewarm {

    private static final String TAG = "hk-prewarm";

    /** 与规则 rgproxy/hgWarm 对齐的 UA（lib_hg_pxy.js HGS_UA），CDN 侧行为一致。 */
    private static final String HG_UA =
            "com.phoenix.read/73932 (Linux; U; Android 14; zh_CN; 2509FPN0BC; Build/UQ1A.240205.09211156;tt-ok/3.12.13.20)";

    private static final String CACHE_VIRTUAL = "hiker://files/cache/";

    /** 单线程串行：一次只预热一集，不跟播放抢带宽；daemon 线程，不挡进程退出。 */
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hk-prewarm");
        t.setDaemon(true);
        return t;
    });

    /** 正在预热的落点（绝对路径），防重复下载。 */
    private static final Set<String> IN_PROGRESS = ConcurrentHashMap.newKeySet();

    /**
     * hk_ 点播详情打开时调用：后台预热当前集。非 hk_ key 直接 no-op。
     *
     * @param siteKey    详情页 key（hk_&lt;规则名&gt;）
     * @param episodeUrl 当前集地址（可能是 @lazyRule= 包裹的原始地址）
     */
    public static void prewarm(String siteKey, String episodeUrl) {
        if (!HkDetailBridge.isHkKey(siteKey) || TextUtils.isEmpty(episodeUrl)) return;
        String ruleTitle = siteKey.substring(HkDetailBridge.KEY_PREFIX.length());
        if (TextUtils.isEmpty(ruleTitle)) return;
        EXEC.execute(() -> {
            try {
                runPrewarm(ruleTitle, episodeUrl);
            } catch (Throwable t) {
                log("abort rule=%s err=%s", ruleTitle, t.getMessage());
            }
        });
    }

    private static void runPrewarm(String ruleTitle, String episodeUrl) throws Exception {
        String url = episodeUrl.trim();
        // 1) 懒地址后台解析：走现有 HkRouter.play，与播放链同一入口，不另起炉灶。
        if (url.contains("@lazyRule=")) {
            HkRouter router = SiteApi.getHkRouter(ruleTitle);
            if (router == null) {
                log("no router rule=%s", ruleTitle);
                return;
            }
            HkPlay play = router.play(url);
            url = play == null ? "" : play.getUrl(0);
            if (TextUtils.isEmpty(url)) {
                log("lazy resolve empty rule=%s", ruleTitle);
                return;
            }
        }
        // 2) 门控：只认本地代理形态（127.0.0.1|localhost + /proxy + rg/k/u）。
        int hash = url.indexOf('#');
        if (hash >= 0) url = url.substring(0, hash);
        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Throwable t) {
            return;
        }
        String host = uri.getHost();
        if (!"127.0.0.1".equals(host) && !"localhost".equalsIgnoreCase(host)) return;
        String path = uri.getPath();
        if (path == null || !path.contains("/proxy")) return;
        String rg = uri.getQueryParameter("rg");
        String k = uri.getQueryParameter("k");
        String u = uri.getQueryParameter("u");
        if (TextUtils.isEmpty(rg) || TextUtils.isEmpty(k) || TextUtils.isEmpty(u)) return;
        String vid = rg.replaceAll("[^0-9]", "");
        if (TextUtils.isEmpty(vid)) return;
        String p = uri.getQueryParameter("p");
        if (TextUtils.isEmpty(p)) p = "1080p";
        String srcUrl = b64UrlDecode(u);
        if (TextUtils.isEmpty(srcUrl) || !srcUrl.startsWith("http")) return;
        // 3) 落点与代理解析一致：hiker://files/cache/rgs_<vid>_<p>.mp4（hgStreamServe 原样）。
        String dest = HkJsRuntime.getFilePath(CACHE_VIRTUAL + "rgs_" + vid + "_" + p + ".mp4");
        if (TextUtils.isEmpty(dest)) return;
        File f = new File(dest);
        if (f.isFile() && f.length() > 0) {
            log("skip exists vid=%s p=%s", vid, p);
            return;
        }
        if (f.isFile()) {
            // 0 字节是坏账（代理见空文件会直接报错"本地密文为空"），删掉重下。
            // noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        if (!IN_PROGRESS.add(dest)) {
            log("skip in-progress vid=%s p=%s", vid, p);
            return;
        }
        try {
            downloadToTemp(srcUrl, dest);
            log("ok vid=%s p=%s", vid, p);
        } catch (Throwable t) {
            log("fail vid=%s p=%s err=%s", vid, p, t.getMessage());
        } finally {
            IN_PROGRESS.remove(dest);
        }
    }

    /**
     * 下载到临时文件再原子改名：与 downloadFile 同一落点、同 UA、同清理口径，
     * 但代理的 hgpExist 永远看不到半截文件。
     */
    private static void downloadToTemp(String srcUrl, String dest) throws Exception {
        File f = new File(dest);
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        File tmp = new File(dest + ".part");
        String opt = "{\"headers\":{\"user-agent\":\"" + HG_UA + "\"}}";
        okhttp3.Response res = HkHttp.to(srcUrl,
                com.fongmi.quickjs.bean.Req.objectFrom(opt)).execute();
        try {
            if (!res.isSuccessful() || res.body() == null) {
                throw new RuntimeException("HTTP " + res.code());
            }
            Files.write(tmp.toPath(), res.body().bytes());
        } catch (Throwable t) {
            // noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw t;
        } finally {
            res.close();
        }
        try {
            Files.move(tmp.toPath(), f.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        // 落盘清理口径与 downloadFile 一致（6 小时/8 文件/400MB 删最旧）。
        HkMediaCacheCleaner.scheduleClean(new File(HkJsRuntime.getFilePath("hiker://files/")));
    }

    private static String b64UrlDecode(String s) {
        try {
            String t = s.trim().replace('-', '+').replace('_', '/');
            while (t.length() % 4 != 0) t += "=";
            return new String(Base64.decode(t, Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    private static void log(String fmt, Object... args) {
        try {
            com.github.catvod.crawler.SpiderDebug.log(TAG, fmt, args);
        } catch (Throwable ignored) {
        }
    }
}
