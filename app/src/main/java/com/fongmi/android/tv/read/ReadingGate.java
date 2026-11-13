package com.fongmi.android.tv.read;

import android.text.TextUtils;

import com.fongmi.android.tv.api.hk.HkPlay;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.bean.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 阅读分流判别层（阶段一·地基，加固版）。
 * <p>
 * 职责：在 playerContent 产出 Result、进 Source.fetch / 播放器之前，
 * 识别"漫画/小说"数据并改道去阅读器。
 * <p>
 * <b>零容忍红线（用户令）</b>：视频被误判劫去阅读器不可容忍——
 * <b>宁可漏认、绝不错认</b>。判别策略：
 * <ul>
 * <li>显式信号直认：{@code pics://}（8.83 语义＝非视频，HkRouter 第 1.5 步）、
 * 规则 type == "小说"（规则自声明）。</li>
 * <li>启发式信号必须交叉到"不可能与视频形态重叠"：
 * 漫画要求 ≥3 段全图片 URL＋同 host＋全串无视频标记；
 * 小说要求无 URL 特征、非 JSON、长度 ＞200、且有可读文本结构（中文或成句），
 * 并排除失败标记文本。</li>
 * <li>拿不准一律不改道；判别抛错 fail-open（调用方 try/catch 兜底）。</li>
 * <li>每次改道在日志留一行（形态＋判据），真机可回溯。</li>
 * </ul>
 * push_agent 链调用点直接跳过本层，不进判别。
 */
public class ReadingGate {

    private static final String LOG_TAG = "reading-gate";

    /** 图片 URL：扩展名必须在 path 部分（query 里的 ?x=a.jpg 不算，防 m3u8 误判）。 */
    private static final Pattern IMAGE_URL = Pattern.compile(
            "^https?://[^\\s?#]+\\.(jpg|jpeg|png|webp|gif|bmp)([?#].*)?$",
            Pattern.CASE_INSENSITIVE);

    /** 漫画：最少段数（2 段太容易撞，提到 3）。 */
    private static final int COMIC_MIN_SEGMENTS = 3;

    private static final int NOVEL_MIN_LEN = 200;

    /** 视频标记：全串出现任一即拒绝漫画判别（防混串）。 */
    private static final String[] VIDEO_MARKERS = {
            ".mp4", ".m3u8", ".mkv", ".avi", ".flv", ".mov", ".webm", ".iso",
            "magnet:", "ed2k:", "thunder:", "rtmp://", "rtsp://", "video://",
            "application/x-mpegurl"
    };

    /** 失败标记：正文含任一即拒绝小说判别（错杀方向是漏认，符合宁可漏认）。 */
    private static final String[] NOVEL_FAIL_MARKERS = {
            "解析失败", "播放失败", "获取失败", "加载失败", "请求失败",
            "failed", "failure", "error", "exception", "timeout", "超时", "404"
    };

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    private ReadingGate() {
    }

    private static void logDivert(String fmt, Object... args) {
        try {
            com.github.catvod.crawler.SpiderDebug.log(LOG_TAG, fmt, args);
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 海阔 ----------------

    /**
     * 海阔漫画：pics 非空即漫画（pics:// 分支是漫画专用标记，视频永不进）。
     * 加固：至少一段含 "."，过滤 "pics://封面图" 这类占位文本（否则走原链，旧行为）。
     */
    public static ReadingSession fromHkComic(String ruleTitle, String bookTitle, String chapterName,
                                             String chapterUrl, HkPlay play, Map<String, String> headers) {
        if (play == null || play.getPics().isEmpty()) return null;
        boolean anyReal = false;
        for (String p : play.getPics()) {
            if (p != null && p.contains(".")) {
                anyReal = true;
                break;
            }
        }
        if (!anyReal) return null;
        ReadingSession s = new ReadingSession();
        s.kind = ReadingSession.Kind.COMIC;
        s.sourceName = ruleTitle == null ? "" : ruleTitle;
        s.ruleTitle = s.sourceName;
        s.bookTitle = bookTitle == null ? "" : bookTitle;
        s.chapterUrl = chapterUrl == null ? "" : chapterUrl;
        ReadingSession.Chapter c = new ReadingSession.Chapter(chapterName, s.chapterUrl);
        s.chapters.add(c);
        s.chapterIndex = 0;
        s.images = new ArrayList<>(play.getPics());
        // 来源交接：章节页地址与 play 整套请求头分开存。旧口径只摘一个 Referer 值
        // 塞进 referer 字段当页面地址喂组装函数——头全丢、地址是假，图必死（断点报告）。
        // 页面地址只认 play 解析时记录的产出页（懒解析/规则求值实际跑的真实页面）：
        // 集地址本身——尤其 pics:// 串全文——不是页面地址，拿它冒充时组装层
        // getDom 算出首图碎片当域、自动 Referer 恒不成立（复核报告 §3 钉死）。
        // play 没记录到产出页（详情期直给 pics 串等）宁可留空：组装层走自动
        // 判定、请求头由 playHeaders 补缺，不拿假地址污染判定。
        String producedPage = play.getPageUrl();
        s.pageUrl = isHttpPage(producedPage) ? producedPage : "";
        if (headers != null) {
            s.playHeaders.putAll(headers);
            String ref = headers.get("Referer");
            if (ref != null) s.referer = ref;
        }
        logDivert("divert kind=COMIC rule=%s pics=%d why=pics-scheme", s.sourceName, play.getPics().size());
        return s;
    }

    /** 真实页面地址判定：http(s) 全地址才认（pics:// 串、js: 串、相对地址一律不认）。 */
    private static boolean isHttpPage(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String t = url.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("http://") || t.startsWith("https://");
    }

    /** 海阔小说规则判定（type == "小说"，规则自声明，显式信号直认）。 */
    public static boolean isHkNovelRule(String ruleTitle) {
        if (TextUtils.isEmpty(ruleTitle)) return false;
        try {
            HkRule rule = HkRuleManager.get().getRule(ruleTitle);
            return rule != null && "小说".equals(rule.getType());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 海阔小说会话：正文懒取（阅读器里按章拉）。 */
    public static ReadingSession fromHkNovel(String ruleTitle, String bookTitle,
                                             List<ReadingSession.Chapter> chapters, int chapterIndex) {
        ReadingSession s = new ReadingSession();
        s.kind = ReadingSession.Kind.NOVEL;
        s.sourceName = ruleTitle == null ? "" : ruleTitle;
        s.ruleTitle = s.sourceName;
        s.bookTitle = bookTitle == null ? "" : bookTitle;
        if (chapters != null) s.chapters = chapters;
        s.chapterIndex = Math.max(0, chapterIndex);
        ReadingSession.Chapter c = s.currentChapter();
        s.chapterUrl = c.url;
        s.referer = "";
        logDivert("divert kind=NOVEL rule=%s why=rule-type-novel", s.sourceName);
        return s;
    }

    // ---------------- py ----------------

    /**
     * py 链判别：rawUrl 是 spider playerContent 返回、Source.fetch 之前的原始地址串。
     * 只在蜘蛛/接口源调用点使用；push_agent、海阔不进这里。
     */
    public static ReadingSession detectPy(String siteKey, String siteName, String bookTitle,
                                          String episodeName, String rawUrl) {
        if (TextUtils.isEmpty(rawUrl)) return null;
        String raw = rawUrl.trim();
        // 漫画：≥3 段、全图片 URL、全段同 host、全串无视频标记
        List<String> images = splitImages(raw);
        if (images.size() >= COMIC_MIN_SEGMENTS && allImageUrl(images)
                && sameHost(images) && !containsVideoMarker(raw)) {
            ReadingSession s = new ReadingSession();
            s.kind = ReadingSession.Kind.COMIC;
            s.sourceName = siteName == null ? "" : siteName;
            s.siteKey = siteKey == null ? "" : siteKey;
            s.bookTitle = bookTitle == null ? "" : bookTitle;
            s.images = images;
            ReadingSession.Chapter c = new ReadingSession.Chapter(episodeName, raw);
            s.chapters.add(c);
            s.chapterIndex = 0;
            logDivert("divert kind=COMIC site=%s segs=%d host=%s why=multi-image-same-host",
                    s.siteKey, images.size(), hostOf(images.get(0)));
            return s;
        }
        // 小说：纯文本（无任何 URL 特征、非 JSON、够长、有可读结构、无失败标记）
        if (isNovelText(raw)) {
            ReadingSession s = new ReadingSession();
            s.kind = ReadingSession.Kind.NOVEL;
            s.sourceName = siteName == null ? "" : siteName;
            s.siteKey = siteKey == null ? "" : siteKey;
            s.bookTitle = bookTitle == null ? "" : bookTitle;
            s.text = raw;
            ReadingSession.Chapter c = new ReadingSession.Chapter(episodeName, "");
            s.chapters.add(c);
            s.chapterIndex = 0;
            logDivert("divert kind=NOVEL site=%s len=%d why=plain-readable-text",
                    s.siteKey, raw.length());
            return s;
        }
        return null;
    }

    private static boolean allImageUrl(List<String> segs) {
        for (String u : segs) {
            if (!isImageUrl(u)) return false;
        }
        return true;
    }

    /** 全段同 host（漫画章节图一般同源；分片多源的漏认，符合宁可漏认）。 */
    private static boolean sameHost(List<String> segs) {
        String host = hostOf(segs.get(0));
        if (host.isEmpty()) return false;
        for (int i = 1; i < segs.size(); i++) {
            if (!host.equals(hostOf(segs.get(i)))) return false;
        }
        return true;
    }

    private static String hostOf(String url) {
        try {
            String t = url.trim().toLowerCase(Locale.ROOT);
            int p = t.indexOf("://");
            if (p < 0) return "";
            String rest = t.substring(p + 3);
            int end = rest.length();
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (c == '/' || c == '?' || c == '#') {
                    end = i;
                    break;
                }
            }
            return rest.substring(0, end);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean containsVideoMarker(String raw) {
        String low = raw.toLowerCase(Locale.ROOT);
        for (String m : VIDEO_MARKERS) {
            if (low.contains(m)) return true;
        }
        return false;
    }

    /**
     * 小说文本判别（启发式，交叉到不可能与视频形态重叠）：
     * 无 URL 特征、非 JSON、够长、有可读结构（中文或成句）、无失败标记。
     * base64/加密 blob/纯 token 因无可读结构被拒；长错误文本因失败标记被拒。
     */
    private static boolean isNovelText(String raw) {
        if (looksLikeUrl(raw)) return false;
        if (raw.startsWith("{") || raw.startsWith("[") || raw.startsWith("\"")) return false;
        if (raw.length() <= NOVEL_MIN_LEN) return false;
        if (hasFailMarker(raw)) return false;
        return hasReadableStructure(raw);
    }

    private static boolean hasFailMarker(String raw) {
        String low = raw.toLowerCase(Locale.ROOT);
        for (String m : NOVEL_FAIL_MARKERS) {
            if (low.contains(m.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** 可读结构：中文，或（有空格的拉丁文且带句读）；且至少有一个句子标点/换行。 */
    private static boolean hasReadableStructure(String raw) {
        boolean cjk = CJK.matcher(raw).find();
        boolean spacedLatin = raw.contains(" ");
        boolean punct = raw.indexOf('。') >= 0 || raw.indexOf('！') >= 0
                || raw.indexOf('？') >= 0 || raw.indexOf('!') >= 0
                || raw.indexOf('?') >= 0 || raw.indexOf('\n') >= 0
                || raw.indexOf('…') >= 0 || raw.indexOf('.') >= 0;
        return (cjk || spacedLatin) && punct;
    }

    /**
     * 图片串拆分：先按行、再按字面 #、再按逗号。
     * <b>绝不按单个 & 拆</b>（HkRouter 的教训：图片地址 query 里的 & 会被切碎）。
     */
    public static List<String> splitImages(String raw) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) return out;
        for (String line : raw.split("\n")) {
            for (String seg : line.split("#")) {
                for (String part : seg.split(",")) {
                    String t = part.trim();
                    if (!t.isEmpty()) out.add(t);
                }
            }
        }
        return out;
    }

    public static boolean isImageUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        return IMAGE_URL.matcher(url.trim()).matches();
    }

    /**
     * 取某章的图片列表：章节 url 本身就是图片串（py 生态约定）时现场拆分；
     * 海阔章节走 HkPlay.pics（已在会话 images 里），这里只处理 url 内嵌串。
     * （阅读器内部二次判别，非改道决策，保持原口径。）
     */
    public static List<String> imagesOfChapter(ReadingSession.Chapter chapter) {
        List<String> out = new ArrayList<>();
        if (chapter == null || TextUtils.isEmpty(chapter.url)) return out;
        List<String> segs = splitImages(chapter.url);
        if (segs.size() >= 2) {
            boolean allImage = true;
            for (String u : segs) {
                if (!isImageUrl(u)) {
                    allImage = false;
                    break;
                }
            }
            if (allImage) return segs;
        }
        return out;
    }

    /** 是否带 URL 特征：有 :// 或常见协议头即算。 */
    public static boolean looksLikeUrl(String s) {
        if (TextUtils.isEmpty(s)) return false;
        String t = s.trim();
        if (t.contains("://")) return true;
        String low = t.toLowerCase(Locale.ROOT);
        return low.startsWith("magnet:") || low.startsWith("ed2k:") || low.startsWith("thunder:")
                || low.startsWith("ftp:") || low.startsWith("rtsp:") || low.startsWith("rtmp:")
                || low.startsWith("ws:") || low.startsWith("video://") || low.startsWith("file:");
    }

    /** 从 Result 取出阅读会话（无则 null）。 */
    public static ReadingSession extract(Result result) {
        if (result == null) return null;
        try {
            return result.getReadingSession();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
