package com.fongmi.android.tv.cloud.drive115;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 115 视频截帧封面：服务端缩略图（回包 u 键，Drive115 已解析进 CloudFile.thumbUrl）
 * 缺失时的兜底——从视频里截一帧当封面用。取流走本机代理 Proxy115 的现成链（新铸原画直链
 * downUrl → proxy.put 包成 127.0.0.1 地址），MediaMetadataRetriever 远程取帧；
 * 取帧点＝时长一成处（片头黑屏多，避开），取不到时长固定 5 秒。
 * 截不到、或截出来近全黑（平均亮度过低）一律算没截到，不拿黑屏帧糊弄。
 * 落盘缓存按 pickcode 命名（cacheDir/pan115cover/），截失败留 .none 标记，
 * 24 小时内不重试，免得每次开列表都白跑一遍取链取帧。
 */
public class Pan115Cover {

    /** 取帧结果回调（在后台线程回调，调用方自己切主线程）。 */
    public interface Callback {
        void onCoverDone(String pickCode, boolean ok);
    }

    private static final long NONE_RETRY_MS = 24L * 60 * 60 * 1000;
    private static final int MAX_FRAME_EDGE = 360;
    /** 平均亮度低于此值算近全黑（0~255）。 */
    private static final double DARK_AVG = 16.0;

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "pan115cover");
        t.setDaemon(true);
        return t;
    });
    private static final Set<String> INFLIGHT = new HashSet<>();
    private static final Map<String, List<Callback>> PENDING = new HashMap<>();

    private Pan115Cover() {
    }

    private static File dir(Context ctx) {
        File d = new File(ctx.getCacheDir(), "pan115cover");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static String safe(String pc) {
        return pc == null ? "" : pc.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    /** 已截好的封面文件（不存在/空文件返回 null）。 */
    public static File coverFile(Context ctx, String pickCode) {
        if (TextUtils.isEmpty(pickCode)) return null;
        File f = new File(dir(ctx), safe(pickCode) + ".jpg");
        return f.isFile() && f.length() > 0 ? f : null;
    }

    private static File noneFile(Context ctx, String pickCode) {
        return new File(dir(ctx), safe(pickCode) + ".none");
    }

    /**
     * 请求封面：已有缓存直接回调 ok；正在截的挂进等候名单；
     * 24 小时内截失败过的直接回调失败；其余起后台任务截帧。
     */
    public static void requestCover(Context ctx, Drive115 drive, String pickCode, Callback cb) {
        if (TextUtils.isEmpty(pickCode) || drive == null) {
            if (cb != null) cb.onCoverDone(pickCode, false);
            return;
        }
        if (coverFile(ctx, pickCode) != null) {
            if (cb != null) cb.onCoverDone(pickCode, true);
            return;
        }
        File none = noneFile(ctx, pickCode);
        if (none.isFile() && System.currentTimeMillis() - none.lastModified() < NONE_RETRY_MS) {
            if (cb != null) cb.onCoverDone(pickCode, false);
            return;
        }
        synchronized (INFLIGHT) {
            if (INFLIGHT.contains(pickCode)) {
                if (cb != null) {
                    List<Callback> list = PENDING.get(pickCode);
                    if (list == null) {
                        list = new ArrayList<>();
                        PENDING.put(pickCode, list);
                    }
                    list.add(cb);
                }
                return;
            }
            INFLIGHT.add(pickCode);
        }
        if (cb != null) {
            synchronized (INFLIGHT) {
                List<Callback> list = PENDING.get(pickCode);
                if (list == null) {
                    list = new ArrayList<>();
                    PENDING.put(pickCode, list);
                }
                list.add(cb);
            }
        }
        Context appCtx = ctx.getApplicationContext();
        EXEC.execute(() -> {
            boolean ok = false;
            try {
                ok = grab(appCtx, drive, pickCode);
            } catch (Throwable t) {
                log(pickCode, false, "异常 " + t.getClass().getSimpleName());
            }
            if (!ok) {
                try {
                    FileOutputStream fos = new FileOutputStream(noneFile(appCtx, pickCode));
                    fos.close();
                } catch (Exception ignored) {
                }
            }
            List<Callback> cbs;
            synchronized (INFLIGHT) {
                INFLIGHT.remove(pickCode);
                cbs = PENDING.remove(pickCode);
            }
            if (cbs != null) {
                for (Callback c : cbs) {
                    try {
                        c.onCoverDone(pickCode, ok);
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
    }

    /** 截帧本体：铸直链 → 包代理 → MMR 取时长与帧 → 黑帧判定 → 缩放落盘。 */
    private static boolean grab(Context ctx, Drive115 drive, String pickCode) {
        String raw = null;
        try {
            raw = drive.downUrl(pickCode);
        } catch (Exception e) {
            log(pickCode, false, "取直链失败");
            return false;
        }
        if (TextUtils.isEmpty(raw)) {
            log(pickCode, false, "直链为空");
            return false;
        }
        Proxy115 proxy = Proxy115.getInstance(drive.getClient(), drive.getCookieJar());
        int port = proxy.startProxy();
        if (port <= 0) {
            log(pickCode, false, "代理启动失败");
            return false;
        }
        // 请求头与 PlayUrlBuilder115.viaProxy 同一套（115 认 UA/Referer/Cookie）
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 115Browser/27.0.5.7");
        headers.put("Referer", "https://115.com/");
        String cookie = drive.getCookieJar().merged(drive.getCookie());
        if (!TextUtils.isEmpty(cookie)) headers.put("Cookie", cookie);
        String token = proxy.put(headers);
        proxy.bindTarget(token, raw, pickCode);
        String url = "http://127.0.0.1:" + port + "/v?t=" + token + "&u=" + Proxy115.b64e(raw);

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        Bitmap frame = null;
        try {
            mmr.setDataSource(url);
            long durationMs = -1;
            try {
                String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (!TextUtils.isEmpty(d)) durationMs = Long.parseLong(d.trim());
            } catch (Exception ignored) {
            }
            long timeUs;
            if (durationMs > 0) {
                // 时长一成处，避开片头黑屏；夹住别越过片尾
                timeUs = durationMs * 100L; // ms → 时长 10% 的 µs
                if (timeUs < 1_000_000L) timeUs = Math.min(1_000_000L, durationMs * 1000L / 2);
            } else {
                timeUs = 5_000_000L; // 取不到时长：固定 5 秒
            }
            frame = mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) {
                log(pickCode, false, "取帧为空 dur=" + durationMs);
                return false;
            }
            if (isNearBlack(frame)) {
                log(pickCode, false, "帧近全黑 dur=" + durationMs);
                frame.recycle();
                return false;
            }
            Bitmap out = scaleDown(frame);
            File tmp = new File(dir(ctx), safe(pickCode) + ".tmp");
            File dst = new File(dir(ctx), safe(pickCode) + ".jpg");
            boolean saved = false;
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(tmp);
                saved = out.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            } finally {
                if (fos != null) fos.close();
            }
            if (out != frame) out.recycle();
            frame.recycle();
            if (!saved || tmp.length() == 0) {
                tmp.delete();
                log(pickCode, false, "落盘失败");
                return false;
            }
            if (dst.exists()) dst.delete();
            if (!tmp.renameTo(dst)) {
                tmp.delete();
                log(pickCode, false, "落盘改名失败");
                return false;
            }
            try {
                noneFile(ctx, pickCode).delete();
            } catch (Exception ignored) {
            }
            log(pickCode, true, "dur=" + durationMs);
            return true;
        } catch (Throwable t) {
            if (frame != null) frame.recycle();
            log(pickCode, false, "取帧异常 " + t.getClass().getSimpleName());
            return false;
        } finally {
            try {
                mmr.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static Bitmap scaleDown(Bitmap frame) {
        int w = frame.getWidth();
        int h = frame.getHeight();
        int max = Math.max(w, h);
        if (max <= MAX_FRAME_EDGE) return frame;
        float ratio = (float) MAX_FRAME_EDGE / max;
        return Bitmap.createScaledBitmap(frame, Math.max(1, Math.round(w * ratio)),
                Math.max(1, Math.round(h * ratio)), true);
    }

    /** 近全黑判定：缩到 12×12 采样平均亮度。 */
    private static boolean isNearBlack(Bitmap frame) {
        Bitmap small = Bitmap.createScaledBitmap(frame, 12, 12, true);
        long sum = 0;
        for (int y = 0; y < small.getHeight(); y++) {
            for (int x = 0; x < small.getWidth(); x++) {
                int c = small.getPixel(x, y);
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                sum += (int) (0.299 * r + 0.587 * g + 0.114 * b);
            }
        }
        double avg = (double) sum / (small.getWidth() * small.getHeight());
        if (small != frame) small.recycle();
        return avg < DARK_AVG;
    }

    private static void log(String pickCode, boolean ok, String why) {
        try {
            SpiderDebug.log("pancover", "115 pc=%s ok=%s %s", pickCode, ok, why);
        } catch (Throwable ignored) {
        }
    }
}
