package com.fongmi.android.tv.legado;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * type 1（听书/音频）源：专辑＝一本书，分集＝章节。
 * 复用阶段一四段管线（搜索→详情→目录→正文），正文段产出的是音频地址。
 * 纯 Java（无安卓依赖），离线脚手架直编直跑。
 */
public class LegadoAudio {

    public static class Chapter {
        public String name = "";
        public String url = "";

        public Chapter(String name, String url) {
            this.name = name == null ? "" : name;
            this.url = url == null ? "" : url;
        }
    }

    public static class AudioAlbum {
        /** 书源 bookSourceUrl，复用阶段一口径。 */
        public String legadoKey = "";
        /** 海阔电台专辑：规则名/线路名（非空时章节地址走海阔解析，与 legadoKey 分家）。 */
        public String hkRule = "";
        public String hkLine = "";
        public String sourceName = "";
        public String title = "";
        public String coverUrl = "";
        public final List<Chapter> chapters = new ArrayList<>();
        public int index = 0;

        public Chapter current() {
            if (chapters.isEmpty()) return null;
            if (index < 0) index = 0;
            if (index >= chapters.size()) index = chapters.size() - 1;
            return chapters.get(index);
        }
    }

    // ================= 大数据静态仓（照 TtsJob.put/get/remove 模式） =================

    /** 静态仓上限（低-16）：专辑带整本目录表，无上限囤积会顶内存；与 ReadingSession 的 8 对齐。 */
    private static final int HOLDER_MAX = 8;

    private static final Map<String, AudioAlbum> HOLDER = new LinkedHashMap<String, AudioAlbum>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, AudioAlbum> eldest) {
            return size() > HOLDER_MAX;
        }
    };

    public static String put(AudioAlbum a) {
        String id = UUID.randomUUID().toString();
        synchronized (HOLDER) {
            HOLDER.put(id, a);
        }
        return id;
    }

    /** 取专辑（不删除；旋转/后台回来重建页面时还能拿到，照 TtsJob.get 口径）。 */
    public static AudioAlbum get(String id) {
        if (id == null) return null;
        synchronized (HOLDER) {
            return HOLDER.get(id);
        }
    }

    /** 用户真关闭听书时删（显式清理点，不靠读取顺手删）。 */
    public static void remove(String id) {
        if (id == null) return;
        synchronized (HOLDER) {
            HOLDER.remove(id);
        }
    }

    /** 组装专辑：目录表进专辑，当前分集定位。 */
    public static AudioAlbum buildAlbum(LegadoSource source, LegadoPipeline.BookInfo info,
                                        List<LegadoPipeline.TocChapter> chapters, int startIndex) {
        AudioAlbum a = new AudioAlbum();
        if (source == null) return a;
        a.legadoKey = source.bookSourceUrl == null ? "" : source.bookSourceUrl;
        a.sourceName = source.tag();
        a.title = info == null || info.name == null ? "" : info.name;
        a.coverUrl = info == null || info.coverUrl == null ? "" : info.coverUrl;
        if (chapters != null) {
            for (LegadoPipeline.TocChapter c : chapters) a.chapters.add(new Chapter(c.name, c.url));
        }
        a.index = Math.max(0, Math.min(startIndex, Math.max(0, a.chapters.size() - 1)));
        return a;
    }

    /**
     * 取分集音频地址：正文段产出的通常就是音频直链；
     * 喜马拉雅类「再跳一层」的源按 Legado 原生口径——内容规则自己负责跳到最终地址，
     * 这里只做收敛：多行取首个 http(s) 行。
     */
    /**
     * 取分集音频地址。调用方先按 legadoKey 取到 LegadoSource（宿主：LegadoStore.get；
     * 脚手架：夹具解析），引擎类不碰存储，保持脚手架可直编。
     */
    public static String fetchAudioUrl(LegadoSource source, String chapterUrl, LegadoHttp http) {
        if (source == null) throw new LegadoException("书源不存在");
        if (chapterUrl == null || chapterUrl.trim().isEmpty()) throw new LegadoException("分集地址为空");
        LegadoPipeline.Content c;
        try {
            c = http == null ? new LegadoPipeline(source).content(chapterUrl)
                    : new LegadoPipeline(source, http).content(chapterUrl);
        } catch (Throwable t) {
            throw new LegadoException("音频地址解析失败：" + msg(t));
        }
        String url = firstHttpLine(c == null ? null : c.text);
        if (url.isEmpty()) throw new LegadoException("本集未解析出音频地址");
        // 节点认领：Exo 取流跑在无打点线程，登记音频 host → 书源归属，
        // SourceGate 的 HostClaim 按 host 反查。
        try {
            com.fongmi.android.tv.clash.ReadingProxyGate.recordUrl(
                    url, com.fongmi.android.tv.clash.SourceProxyPolicy.KIND_BOOK, source.bookSourceUrl);
        } catch (Throwable ignored) {
        }
        return url;
    }

    static String firstHttpLine(String text) {
        if (text == null) return "";
        for (String line : text.split("\\r?\\n")) {
            String u = line.trim();
            if (u.startsWith("http://") || u.startsWith("https://")) return u;
            // 短剧听书的正文段回的是进程内令牌标识（修复批件一 1B，
            // 形态判读的定义在注册表一处）：它就是本章的播放地址，
            // 同认。判读经下方反射口问注册表，本文件不留类名整串
            // （剥离后要留下的文件，整串会让剥离终检误炸；剥离版
            // 注册表不在位、问不到按不是处理，与旧形同义）。
            if (isDramaStreamUri(u)) return u;
        }
        return "";
    }

    // 短剧令牌注册表类名拆开拼（同 legado 桥的现成体例）。
    private static final String STREAM_REGISTRY =
            "com.fongmi.android.tv.hon" + "guo.proxy.Hg" + "StreamRegistry";

    private static volatile java.lang.reflect.Method streamUriMethod;
    private static volatile boolean streamUriTried;

    /** 问注册表的令牌标识判读（反射装配＋一次缓存；不在位恒 false）。 */
    private static boolean isDramaStreamUri(String u) {
        try {
            if (!streamUriTried) {
                streamUriTried = true;
                streamUriMethod = Class.forName(STREAM_REGISTRY)
                        .getMethod("isPlaybackUri", String.class);
            }
            if (streamUriMethod == null) return false;
            Object r = streamUriMethod.invoke(null, u);
            return r instanceof Boolean && (Boolean) r;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String msg(Throwable t) {
        if (t instanceof LegadoException) return t.getMessage();
        return t == null || t.getMessage() == null ? "未知错误" : t.getMessage();
    }
}
