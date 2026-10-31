package com.fongmi.android.tv.hongguo.drama;

import android.os.Handler;
import android.os.Looper;

import com.fongmi.android.tv.hongguo.api.HgCallback;
import com.fongmi.android.tv.hongguo.crypto.HgUrlCrypto;
import com.fongmi.android.tv.hongguo.proxy.HgStreamRegistry;
import com.fongmi.android.tv.hongguo.proxy.HgStreamSpec;
import com.fongmi.android.tv.server.Server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单集解链（2026-10-30 从零重建）：模型（DramaClient 全链：缓存→
 * multi→单集→fplay）→ 选档 → 明文链/加密链解 → 推 DEK → 注册本地
 * 解密流（HgStreamRegistry＋HongguoStream 代理，小说听书共用件）→
 * 本地 URL。回调一律回主线程。
 *
 * <p>出口编号（诊断）：E1 取模型失败；E2 模型无可播条目；E3 明文链
 * 拿不到且 fplay 兜底也空；E4 已保护但推不出 DEK；E5 注册流失败。</p>
 */
public final class DramaResolver {

    public interface Listener {
        void onReady(String videoId, String localUrl, int videoWidth, int videoHeight);

        void onFailed(String videoId, String code, String message);
    }

    private final HgStreamRegistry registry = HgStreamRegistry.get();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, String> tokenByVideo = new ConcurrentHashMap<>();
    private final Map<String, String> urlByVideo = new ConcurrentHashMap<>();
    private final Map<String, String> preferredDefByVideo = new ConcurrentHashMap<>();
    private final Map<String, String> chosenDefByVideo = new ConcurrentHashMap<>();
    private final Set<String> resolving = ConcurrentHashMap.newKeySet();
    private final Object pendingLock = new Object();
    private final Map<String, List<Listener>> pendingListeners = new HashMap<>();

    public String readyUrl(String videoId) {
        return videoId == null ? null : urlByVideo.get(videoId);
    }

    /** 当前实际解出的档（definition 原串）；未解出返回 null。 */
    public String currentDefinition(String videoId) {
        return videoId == null ? null : chosenDefByVideo.get(videoId);
    }

    public void resolve(String videoId, int contentType, Listener listener) {
        resolveInternal(videoId, contentType, listener, false);
    }

    private void resolveInternal(String videoId, int contentType, Listener listener,
                                 boolean speculative) {
        if (videoId == null || videoId.isEmpty()) {
            if (listener != null) main.post(() -> listener.onFailed(videoId, null, "videoId 为空"));
            return;
        }
        String ready = urlByVideo.get(videoId);
        if (ready != null) {
            DramaVideoModel m = DramaCache.get(videoId);
            int w = m == null ? 0 : m.getVideoWidth();
            int h = m == null ? 0 : m.getVideoHeight();
            if (listener != null) main.post(() -> listener.onReady(videoId, ready, w, h));
            return;
        }
        synchronized (pendingLock) {
            if (listener != null) {
                pendingListeners.computeIfAbsent(videoId, k -> new java.util.ArrayList<>()).add(listener);
            }
            if (!resolving.add(videoId)) return; // 在途：listener 已挂载
        }
        DramaClient.fetchModelForPlay(videoId, contentType, new HgCallback<DramaVideoModel>() {
            @Override
            public void onSuccess(DramaVideoModel model) {
                settleWithModel(videoId, model);
            }

            @Override
            public void onError(int code, String message) {
                finish(videoId, null, "E1");
            }
        }, speculative);
    }

    /** 以指定档重解（清晰度切换）：落偏好、回收旧流后重走解链。 */
    public void resolveAs(String videoId, int contentType, String definition, Listener listener) {
        if (videoId == null) return;
        if (definition != null) preferredDefByVideo.put(videoId, definition);
        close(videoId);
        resolve(videoId, contentType, listener);
    }

    /**
     * 预热一条流（解链注册后 warm 代理层）。无 token 时走推测口解链
     * （speculative=true，2026-10-31 补）：预热撞空不许写播放冷却——
     * 原先直借正片 resolve()，一发空就把正片全赶去共享面 15 分钟。
     */
    public void warm(String videoId, int contentType) {
        if (videoId == null) return;
        String token = tokenByVideo.get(videoId);
        if (token != null) {
            registry.warmAsync(token);
            return;
        }
        resolveInternal(videoId, contentType, null, true);
    }

    /** 回收一条流。选档偏好保留，实际档记录清掉。 */
    public void close(String videoId) {
        if (videoId == null) return;
        urlByVideo.remove(videoId);
        chosenDefByVideo.remove(videoId);
        String token = tokenByVideo.remove(videoId);
        if (token != null) registry.close(token);
    }

    public void closeAll() {
        for (String id : tokenByVideo.keySet().toArray(new String[0])) close(id);
        urlByVideo.clear();
    }

    /**
     * 关掉除指定 vid 外的全部流（交接泊回方销毁时用）：泊回的引擎
     * 若正播本 resolver 解出的那条流，整表关流会把领回页的续播当场
     * 掐断——注册表 close 是摘规格＋关活流，在途读直接断。当前集
     * 那条留命，领回页 reclaim 时经 adoptStream 把账接过去。
     */
    public void closeAllExcept(String keepVideoId) {
        for (String id : tokenByVideo.keySet().toArray(new String[0])) {
            if (keepVideoId == null || !keepVideoId.equals(id)) close(id);
        }
        for (String id : urlByVideo.keySet().toArray(new String[0])) {
            if (keepVideoId == null || !keepVideoId.equals(id)) {
                urlByVideo.remove(id);
            }
        }
    }

    /**
     * 认领一条不是本 resolver 注册的流（交接领回）：引擎在泊出页
     * 解的链、本地 URL 里带 token，领回页把这条流的账接过来——
     * 之后这条流的关流/重解归本 resolver，生命周期有着落。自家已
     * 解过的 vid（urlByVideo 已有记录）不动。
     */
    public void adoptStream(String videoId, String localUrl) {
        if (videoId == null || localUrl == null) return;
        if (urlByVideo.containsKey(videoId)) return;
        String token = null;
        int i = localUrl.indexOf("t=");
        if (i >= 0) {
            int end = localUrl.indexOf('&', i + 2);
            token = end > i ? localUrl.substring(i + 2, end)
                    : localUrl.substring(i + 2);
        }
        if (token == null || token.isEmpty()) return;
        urlByVideo.put(videoId, localUrl);
        tokenByVideo.put(videoId, token);
    }

    // ---------- 模型 → 本地 URL ----------

    private void settleWithModel(String videoId, DramaVideoModel model) {
        DramaEntry entry = pickEntry(model, videoId);
        if (entry == null) {
            finish(videoId, null, "E2");
            return;
        }
        String plain = plainUrl(model, entry);
        if (plain != null) {
            finishWithEntry(videoId, model, entry, plain);
            return;
        }
        // 明文链拿不到（加密且无 key_seed）：经 fallback_api 换 fplay 形态重试
        String api = model.getFallbackApi();
        if (api == null || api.isEmpty()) {
            finish(videoId, null, "E3");
            return;
        }
        DramaClient.fetchFplay(api, new HgCallback<DramaVideoModel>() {
            @Override
            public void onSuccess(DramaVideoModel fplay) {
                DramaEntry e2 = pickEntry(fplay, videoId);
                String p2 = e2 == null ? null : plainUrl(fplay, e2);
                if (e2 == null) finish(videoId, null, "E2");
                else if (p2 == null) finish(videoId, null, "E3");
                else finishWithEntry(videoId, fplay, e2, p2);
            }

            @Override
            public void onError(int code, String message) {
                finish(videoId, null, "E3");
            }
        });
    }

    private void finishWithEntry(String videoId, DramaVideoModel model, DramaEntry entry, String plain) {
        String dek = null;
        if (model.isProtected()) {
            dek = model.deriveDekHex();
            if (dek == null) {
                finish(videoId, null, "E4");
                return;
            }
        }
        chosenDefByVideo.put(videoId, entry.getDefinition());
        HgStreamSpec spec = new HgStreamSpec(plain, dek, entry.getSize(), entry.getFileHash(),
                videoId + "/" + entry.getDefinition());
        String token = registry.register(spec);
        if (token == null) {
            finish(videoId, null, "E5");
            return;
        }
        tokenByVideo.put(videoId, token);
        String local = Server.get().getAddress(true) + registry.playbackPath(token);
        urlByVideo.put(videoId, local);
        registry.warmAsync(token);
        finish(videoId, new Ready(local, model.getVideoWidth(), model.getVideoHeight()), null);
    }

    private DramaEntry pickEntry(DramaVideoModel model, String videoId) {
        String pref = preferredDefByVideo.get(videoId);
        if (pref != null) {
            DramaEntry e = model.entryByDefinition(pref);
            if (e != null && e.isPlayableCodec()) return e;
        }
        return model.firstPlayableEntry();
    }

    /** 明文链直用；加密链用本回包 key_seed 解；缺 key_seed 返回 null 交上层走 fplay。 */
    private String plainUrl(DramaVideoModel model, DramaEntry entry) {
        String p = urlOf(model, entry, entry.getMainUrl());
        if (p != null) return p;
        // 主链拿不到回落备用链（backup_url 解析早已存档、此前零
        // 消费——主链解密失败/为空时整条就判死，白白丢一条链）。
        return urlOf(model, entry, entry.getBackupUrl());
    }

    private String urlOf(DramaVideoModel model, DramaEntry entry, String raw) {
        if (raw == null || raw.isEmpty()) return null;
        if (!entry.isUrlEncrypted()) return raw;
        if (model.getKeySeed() == null || model.getKeySeed().isEmpty()) return null;
        try {
            return HgUrlCrypto.decryptUrl(raw, model.getKeySeed());
        } catch (Exception e) {
            return null;
        }
    }

    private static final class Ready {
        final String url;
        final int w;
        final int h;

        Ready(String url, int w, int h) {
            this.url = url;
            this.w = w;
            this.h = h;
        }
    }

    private void finish(String videoId, Ready ready, String failCode) {
        List<Listener> ls;
        synchronized (pendingLock) {
            resolving.remove(videoId);
            ls = pendingListeners.remove(videoId);
        }
        if (ls == null || ls.isEmpty()) return;
        for (Listener l : ls) {
            if (ready != null) main.post(() -> l.onReady(videoId, ready.url, ready.w, ready.h));
            else main.post(() -> l.onFailed(videoId, failCode, "播放失败，请重试"));
        }
    }
}
