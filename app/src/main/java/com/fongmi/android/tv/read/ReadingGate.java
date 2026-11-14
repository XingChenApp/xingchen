package com.fongmi.android.tv.read;

import android.text.TextUtils;

import com.fongmi.android.tv.api.hk.HkItem;
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

    /** 海阔小说规则判定（规则自声明 type 属阅读族小说格，显式信号直认）。 */
    public static boolean isHkNovelRule(String ruleTitle) {
        return classifyHkTitle(ruleTitle) == HkReadKind.NOVEL;
    }

    /** 海阔听书规则判定（规则自声明 type 属阅读族听书格）。 */
    public static boolean isHkAudioRule(String ruleTitle) {
        return classifyHkTitle(ruleTitle) == HkReadKind.AUDIO;
    }

    /** 海阔阅读族分类（小说/听书同属阅读板块，两面分开）。 */
    public enum HkReadKind {NONE, NOVEL, AUDIO}

    /**
     * 阅读族分类（规则对象版）：type=="小说" 直认小说；type=="read"/"阅读"
     * 是官 RuleType 的阅读族总称（小说与听书同装一格），再按规则自身的
     * 听书信号分小说与听书。其余 type 不是阅读族。
     * 旧口径只认 type=="小说" 一词——在野规则（含容器子规则）声明的全是
     * "read"，整族漏认、章节全落视频链（账 heji-subrule-routing）。
     */
    public static HkReadKind classifyHkRule(HkRule rule) {
        if (rule == null) return HkReadKind.NONE;
        String type = rule.getType() == null ? "" : rule.getType().trim();
        if ("小说".equals(type)) return HkReadKind.NOVEL;
        if ("read".equals(type) || "阅读".equals(type)) {
            return looksAudioRule(rule) ? HkReadKind.AUDIO : HkReadKind.NOVEL;
        }
        return HkReadKind.NONE;
    }

    /** 阅读族分类（规则库版，按标题取独立导入的规则）。 */
    public static HkReadKind classifyHkTitle(String ruleTitle) {
        if (TextUtils.isEmpty(ruleTitle)) return HkReadKind.NONE;
        try {
            return classifyHkRule(HkRuleManager.get().getRule(ruleTitle));
        } catch (Throwable ignored) {
            return HkReadKind.NONE;
        }
    }

    /** 规则自声明 type 原值（规则库版，无此规则回空串）。 */
    public static String hkRuleType(String ruleTitle) {
        if (TextUtils.isEmpty(ruleTitle)) return "";
        try {
            HkRule rule = HkRuleManager.get().getRule(ruleTitle);
            return rule == null || rule.getType() == null ? "" : rule.getType();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * 听书信号（只在 read 族内部分流，误判也只在阅读板块内部）：
     * 标题含听书字样直认；否则只扫规则自带页文本（pages）里的听书/音频
     * 字样——子规则把站点名写在自带页页眉里的形（如「易听书网·音频站」）。
     * 不扫 find/detail 正文：小说规则正文里有跨规则搜索串 type=听书 的
     * 残留（拿别的源听书版搜同名书），扫了必误判（语料 7 条实测钉死）。
     */
    private static boolean looksAudioRule(HkRule rule) {
        String title = rule.getTitle() == null ? "" : rule.getTitle();
        if (title.contains("听书") || title.contains("有声") || title.contains("悦听")
                || title.contains("听小说")) return true;
        String pages = rule.getPages() == null ? "" : rule.getPages();
        return pages.contains("听书") || pages.contains("音频") || pages.contains("有声");
    }

    /**
     * 容器劫持页的有效规则：聚合容器（影视合集式）把被点子规则全文以
     * base64 放在条目 extra.RULE 里、随开页透传进页规则的 params
     * （JS 侧 MY_PARAMS 同源）。宿主分发只看页规则（＝容器壳，type 恒
     * video/all）永远认不出子规则的 type——在此解码取子规则本体。
     * 无 RULE/解码失败回 null，调用方回落页规则自身分类，原分发一行不变。
     */
    public static HkRule effectiveRule(HkRule pageRule) {
        String json = effectiveRuleJson(pageRule);
        if (json == null) return null;
        try {
            return new com.google.gson.Gson().fromJson(json, HkRule.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 有效规则全文（解码后的子规则 JSON；无则 null）。 */
    public static String effectiveRuleJson(HkRule pageRule) {
        if (pageRule == null) return null;
        String params = pageRule.getParams();
        if (TextUtils.isEmpty(params)) return null;
        try {
            com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(params);
            if (root == null || !root.isJsonObject()) return null;
            com.google.gson.JsonElement r = root.getAsJsonObject().get("RULE");
            if (r == null || !r.isJsonPrimitive()) return null;
            String b64 = r.getAsString();
            if (b64 == null || b64.isEmpty()) return null;
            String json = new String(android.util.Base64.decode(b64, android.util.Base64.NO_WRAP),
                    java.nio.charset.StandardCharsets.UTF_8);
            return json.startsWith("{") ? json : null;
        } catch (Throwable ignored) {
            return null;
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

    // ---------------- 聚阅子源（续段求值） ----------------

    /**
     * 聚阅小说/漫画子源章节点击组会话（契约账 juyue-subsource-materials
     * §四/§五）：详情页「扩展功能」条目的 extra 带 chapterList＋info
     * （type=comic/novel、bookName、ruleName、parseCode、decode），章节
     * 条目（extra.cls="Juloadlist playlist"）的 url 带 @rule=/@lazyRule=
     * 续段码、extra.url 是章原址。分类只凭 info.type，不凭名字猜；同页
     * 找不到带 chapterList 的兄弟条目就不改道（回 null，调用方走原链）。
     * 章节表取同页已渲染的章节条目（与 chapterList 同一切片同序），每章
     * 的续段载体（码/MY_URL 段/MY_PARAMS 原文）逐章带入会话，取数走
     * HkJuFetcher 的续段求值口——实现经 jkdata 现取，不依赖注册表。
     *
     * @param pageItems    当前页全部条目（宿主适配器原序）
     * @param clickedIndex 被点条目在 pageItems 中的下标
     * @param hostRuleTitle 宿主规则名（聚阅；运行时绑定与历史键的解析名）
     * @param pageUrl      详情页地址（历史键的 vodId 段，与再入同键）
     */
    public static ReadingSession fromHkJuChapterClick(List<HkItem> pageItems, int clickedIndex,
                                                      String hostRuleTitle, String pageUrl) {
        try {
            if (pageItems == null || clickedIndex < 0 || clickedIndex >= pageItems.size()) return null;
            HkItem clicked = pageItems.get(clickedIndex);
            if (clicked == null) return null;
            if (!juStr(juExtraObj(clicked), "cls").contains("Juloadlist")) return null;
            String clickedUrl = clicked.getUrl() == null ? "" : clicked.getUrl();
            if (!clickedUrl.contains("@rule=") && !clickedUrl.contains("@lazyRule=")) return null;
            // 同页兄弟条目找分类与书信息（扩展功能条目）
            com.google.gson.JsonObject info = null;
            for (HkItem it : pageItems) {
                com.google.gson.JsonObject ex = juExtraObj(it);
                if (ex == null || !ex.has("chapterList") || !ex.has("info")) continue;
                if (!ex.get("info").isJsonObject()) continue;
                com.google.gson.JsonObject inf = ex.getAsJsonObject("info");
                String t = juStr(inf, "type");
                if ("comic".equals(t) || "novel".equals(t)) {
                    info = inf;
                    break;
                }
            }
            if (info == null) return null;
            String type = juStr(info, "type");
            ReadingSession s = new ReadingSession();
            s.juMode = true;
            s.kind = "comic".equals(type) ? ReadingSession.Kind.COMIC : ReadingSession.Kind.NOVEL;
            String ruleName = juStr(info, "ruleName");
            s.sourceName = !ruleName.isEmpty() ? ruleName
                    : (hostRuleTitle == null ? "" : hostRuleTitle);
            s.ruleTitle = hostRuleTitle == null ? "" : hostRuleTitle;
            s.bookTitle = juStr(info, "bookName");
            s.coverUrl = juStr(info, "bookTopPic");
            s.juDecode = juStr(info, "decode");
            int clickedChapter = -1;
            for (int i = 0; i < pageItems.size(); i++) {
                HkItem it = pageItems.get(i);
                if (it == null) continue;
                String cls = juStr(juExtraObj(it), "cls");
                if (!cls.contains("Juloadlist") || !cls.contains("playlist")) continue;
                ReadingSession.Chapter ch = juChapterOf(it);
                if (ch == null) continue;
                if (i == clickedIndex) clickedChapter = s.chapters.size();
                s.chapters.add(ch);
            }
            if (clickedChapter < 0 || s.chapters.isEmpty()) return null;
            s.chapterIndex = clickedChapter;
            ReadingSession.Chapter cur = s.currentChapter();
            s.chapterUrl = cur.url;
            if (s.bookTitle.isEmpty()) s.bookTitle = cur.name;
            s.hkHistoryKey = com.fongmi.android.tv.api.hk.HkHistoryRecorder.keyOf(
                    s.ruleTitle, pageUrl == null ? "" : pageUrl);
            logDivert("divert kind=%s rule=%s why=juyue-subsource-info-type chapters=%d",
                    s.kind, s.sourceName, s.chapters.size());
            return s;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 聚阅章节条目 → 会话章节（原址＋续段三件套）。 */
    private static ReadingSession.Chapter juChapterOf(HkItem it) {
        String url = it.getUrl() == null ? "" : it.getUrl();
        com.google.gson.JsonObject ex = juExtraObj(it);
        String raw = juStr(ex, "url");
        String[] tail = juRuleTail(url);
        String page = tail == null ? "" : tail[0];
        String code = tail == null ? "" : tail[1];
        if (raw.isEmpty()) raw = juRawFromPrefix(page);
        if (raw.isEmpty() && tail == null) raw = url;
        if (raw.isEmpty() && code.isEmpty()) return null;
        String title = it.getTitle() == null ? "" : it.getTitle().replaceAll("<[^>]+>", "").trim();
        ReadingSession.Chapter ch = new ReadingSession.Chapter(title, raw);
        ch.pic = it.getPic() == null ? "" : it.getPic();
        ch.juCode = code;
        ch.juPageUrl = page;
        ch.juParams = it.getExtra() == null ? "" : it.getExtra();
        return ch;
    }

    /**
     * 从条目 url 里拆续段：找尾部 @rule=/@lazyRule= 标记（其后须紧跟
     * js:/.js:），返回 {MY_URL 段, 剥前缀后的 JS 码}；无此形回 null。
     */
    private static String[] juRuleTail(String url) {
        if (TextUtils.isEmpty(url)) return null;
        String[] markers = {"@rule=", "@lazyRule="};
        for (String marker : markers) {
            int from = 0;
            while (true) {
                int idx = url.indexOf(marker, from);
                if (idx < 0) break;
                String tail = url.substring(idx + marker.length()).trim();
                String code = null;
                if (tail.startsWith(".js:")) code = tail.substring(4);
                else if (tail.startsWith("js:")) code = tail.substring(3);
                if (code != null && !code.trim().isEmpty()) {
                    return new String[]{url.substring(0, idx), code.trim()};
                }
                from = idx + marker.length();
            }
        }
        return null;
    }

    /** MY_URL 段还原章原址：剥 hiker://empty## 头与 #readTheme# 等标记段。 */
    private static String juRawFromPrefix(String page) {
        if (TextUtils.isEmpty(page)) return "";
        String p = page.trim();
        if (p.startsWith("hiker://empty##")) p = p.substring("hiker://empty##".length());
        else if (p.startsWith("hiker://empty#")) p = p.substring("hiker://empty#".length());
        String[] cuts = {"#readTheme#", "#noRecordHistory#", "#autoPage#"};
        int cut = -1;
        for (String c : cuts) {
            int i = p.indexOf(c);
            if (i >= 0 && (cut < 0 || i < cut)) cut = i;
        }
        if (cut >= 0) p = p.substring(0, cut);
        return p.trim();
    }

    /** 条目 extra 原文解析（原始 JSON 串；坏串回 null）。 */
    private static com.google.gson.JsonObject juExtraObj(HkItem it) {
        if (it == null) return null;
        String extra = it.getExtra();
        if (TextUtils.isEmpty(extra)) return null;
        try {
            com.google.gson.JsonElement e = com.google.gson.JsonParser.parseString(extra);
            return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String juStr(com.google.gson.JsonObject o, String key) {
        if (o == null || key == null || !o.has(key)) return "";
        try {
            com.google.gson.JsonElement e = o.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
        } catch (Throwable ignored) {
            return "";
        }
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
