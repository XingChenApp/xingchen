package com.fongmi.android.tv.read;

import android.text.TextUtils;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.hk.HkDetailBridge;
import com.fongmi.android.tv.api.hk.HkPlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 手动阅读入口（"用阅读器打开"）的放宽判别。
 * <p>
 * 只为手动打开这一次分发阅读器（漫画/小说二选一），判错也只影响这一次；
 * <b>不引用、不修改 {@link ReadingGate} 的自动判别层</b>，自动判别一字未动。
 * <p>
 * 口径（宁可漏认、绝不错认在手动入口的对应）：
 * <ul>
 * <li>漫画：≥2 段真图片 URL（自动层要 ≥3），全串出现任一视频标记直接拒绝
 * （防手滑点视频条目）。多 host 允许（分片漏认形态）。</li>
 * <li>小说：剥掉 URL 后的可读文本 ≥50 字（自动层要 >200 纯文本），
 * 失败标记文本一律拒绝，JSON 拒绝。</li>
 * <li>海阔：pics 侧复用 {@link ReadingGate#fromHkComic}（公开方法），
 * 但本层要求 ≥2 段真图才送；小说侧复用 fromHkNovel 的懒取会话。</li>
 * </ul>
 */
public final class ReadingManual {

    private ReadingManual() {
    }

    /** 手动漫画：最少 2 段真图。 */
    public static final int MANUAL_MIN_SEGS = 2;
    /** 手动小说：剥 URL 后最少 50 字。 */
    public static final int MANUAL_MIN_LEN = 50;

    /** 视频标记：出现任一即拒绝（与自动层同表，手动入口再保险一层）。 */
    private static final String[] VIDEO_MARKERS = {
            ".mp4", ".m3u8", ".mkv", ".avi", ".flv", ".mov", ".webm", ".iso",
            "magnet:", "ed2k:", "thunder:", "rtmp://", "rtsp://", "video://",
            "application/x-mpegurl"
    };

    /** 失败标记：出现任一即拒绝（与自动层同表，错杀方向是漏认）。 */
    private static final String[] FAIL_MARKERS = {
            "解析失败", "播放失败", "获取失败", "加载失败", "请求失败",
            "failed", "failure", "error", "exception", "timeout", "超时", "404"
    };

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern HTTP_TOKEN = Pattern.compile("https?://\\S+");

    /** 图片串里的真图段（扩展名在 path 部分，复用自动层的公开口径）。 */
    public static List<String> realImages(String raw) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) return out;
        for (String seg : ReadingGate.splitImages(raw)) {
            if (ReadingGate.isImageUrl(seg)) out.add(seg);
        }
        return out;
    }

    /** 放宽漫画判别：≥2 真图段且全串无视频标记。 */
    public static boolean isComicRelaxed(String raw) {
        if (TextUtils.isEmpty(raw)) return false;
        if (realImages(raw).size() < MANUAL_MIN_SEGS) return false;
        String low = raw.toLowerCase(Locale.ROOT);
        for (String m : VIDEO_MARKERS) {
            if (low.contains(m)) return false;
        }
        return true;
    }

    /** 放宽小说判别：剥 URL 后 ≥50 字、有可读结构、无失败标记、非 JSON。 */
    public static boolean isNovelRelaxed(String raw) {
        if (TextUtils.isEmpty(raw)) return false;
        String t = raw.trim();
        if (t.startsWith("{") || t.startsWith("[") || t.startsWith("\"")) return false;
        String low = t.toLowerCase(Locale.ROOT);
        for (String m : FAIL_MARKERS) {
            if (low.contains(m.toLowerCase(Locale.ROOT))) return false;
        }
        String stripped = HTTP_TOKEN.matcher(t).replaceAll(" ").trim();
        if (stripped.length() < MANUAL_MIN_LEN) return false;
        return hasReadableStructure(stripped);
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

    /** py 漫画会话：图片串内联（2 图章节 / 多 host 分片）。 */
    public static ReadingSession comicFromImages(String siteKey, String siteName, String bookTitle,
                                                String episodeName, String episodeUrl, List<String> images) {
        ReadingSession s = new ReadingSession();
        s.kind = ReadingSession.Kind.COMIC;
        s.sourceName = siteName == null ? "" : siteName;
        s.siteKey = siteKey == null ? "" : siteKey;
        s.bookTitle = bookTitle == null ? "" : bookTitle;
        s.images = new ArrayList<>(images);
        s.chapters.add(new ReadingSession.Chapter(episodeName, episodeUrl));
        s.chapterIndex = 0;
        s.chapterUrl = episodeUrl == null ? "" : episodeUrl;
        return s;
    }

    /** py 小说会话：正文内联（短文本 / 含 URL 正文）。 */
    public static ReadingSession novelFromText(String siteKey, String siteName, String bookTitle,
                                              String episodeName, String episodeUrl, String text) {
        ReadingSession s = new ReadingSession();
        s.kind = ReadingSession.Kind.NOVEL;
        s.sourceName = siteName == null ? "" : siteName;
        s.siteKey = siteKey == null ? "" : siteKey;
        s.bookTitle = bookTitle == null ? "" : bookTitle;
        s.text = text == null ? "" : text;
        s.chapters.add(new ReadingSession.Chapter(episodeName, episodeUrl));
        s.chapterIndex = 0;
        s.chapterUrl = episodeUrl == null ? "" : episodeUrl;
        return s;
    }

    /**
     * 海阔漫画放宽判别：pics 里 ≥2 段真图才送（自动层 fromHkComic 只要 1 段含 "."；
     * 这里多要一段，pics://封面图 占位文本同样被挡）。
     */
    public static boolean isHkComicRelaxed(HkPlay play) {
        return play != null && realImagesOfPics(play).size() >= MANUAL_MIN_SEGS;
    }

    /**
     * 探针分发（手动入口 / 详情级改道共用）。
     * <p>
     * 只取解析产出（{@link SiteApi#readingProbe}：不做 Source.fetch、不写播放缓存），
     * 先按自动口径走一遍（与 playerContent 内部分流一致），未命中再按放宽口径分发。
     * 后台线程调用。都不中返回 null。
     *
     * @param key         源 key（hk_ 开头为海阔规则）
     * @param playFlag    播放线路 flag（探针用）
     * @param siteName    源显示名
     * @param ruleTitle   海阔规则名（非海阔可空，此时从 key 反推）
     * @param episodeName 章节名
     * @param chapterId   章节地址
     */
    public static ReadingSession probeSession(String key, String playFlag, String siteName,
                                              String ruleTitle, String episodeName, String chapterId) throws Exception {
        boolean hk = HkDetailBridge.isHkKey(key);
        String title = !TextUtils.isEmpty(ruleTitle) ? ruleTitle
                : (hk ? key.substring(HkDetailBridge.KEY_PREFIX.length()) : "");
        if (hk) {
            if (ReadingGate.isHkNovelRule(title)) {
                return ReadingGate.fromHkNovel(title, null,
                        Collections.singletonList(new ReadingSession.Chapter(episodeName, chapterId)), 0);
            }
            SiteApi.ReadingProbe probe = SiteApi.readingProbe(key, playFlag, chapterId);
            if (probe == null || probe.hkPlay == null) return null;
            if (isHkComicRelaxed(probe.hkPlay)) {
                // 与 SiteApi 入口同口径：整套 play 请求头带进会话，不再只摘 Referer
                return ReadingGate.fromHkComic(title, null, episodeName, chapterId, probe.hkPlay, probe.hkPlay.getHeaders());
            }
            if (isNovelRelaxed(probe.hkPlay.getUrl())) {
                return ReadingGate.fromHkNovel(title, null,
                        Collections.singletonList(new ReadingSession.Chapter(episodeName, chapterId)), 0);
            }
            return null;
        }
        SiteApi.ReadingProbe probe = SiteApi.readingProbe(key, playFlag, chapterId);
        if (probe == null || TextUtils.isEmpty(probe.pyRaw)) return null;
        ReadingSession auto = ReadingGate.detectPy(key, siteName, null, episodeName, probe.pyRaw);
        if (auto != null) return auto;
        if (isComicRelaxed(probe.pyRaw)) {
            return comicFromImages(key, siteName, null, episodeName, chapterId, realImages(probe.pyRaw));
        }
        if (isNovelRelaxed(probe.pyRaw)) {
            return novelFromText(key, siteName, null, episodeName, chapterId, probe.pyRaw.trim());
        }
        return null;
    }

    private static List<String> realImagesOfPics(HkPlay play) {
        List<String> out = new ArrayList<>();
        if (play.getPics() == null) return out;
        for (String p : play.getPics()) {
            if (p != null && p.contains(".")) out.add(p);
        }
        return out;
    }
}
