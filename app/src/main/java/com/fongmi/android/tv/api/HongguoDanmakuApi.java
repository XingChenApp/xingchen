package com.fongmi.android.tv.api;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Danmaku;
import com.github.catvod.crawler.SpiderDebug;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 红果弹幕壳子抓取器：与 HongguoCommentApi 同层共建——共用其传输（轻签名器/设备号/全局
 * OkHttp 直连），只差 body 形态（601）与解析。形态逐字段照 hongguo-danmaku-trace.md §2：
 * comment/list/{vid} 按 30 秒一窗分段拉完整集，窗间 ≥180ms，整集 ≤40 窗、≤3000 条。
 * 产出渲染层现成认的 Bilibili 弹幕 XML 落本地缓存文件，渲染层零改。
 * 增量口径（douyu-live-danmaku-noshow-trace.md 第五节修法）：已拉到的窗先落盘先挂，
 * 任一窗失败只停在已得部分、不整集连坐；全程零条才按无弹幕返回 null。
 * 缓存复用口径：一次拉取「自然收尾」（服务端没更多/时长到头/游标停/撞上限，且无一窗
 * 失败）就在同目录落 {vid}.done 完成标记，标记内容区分两种收尾——有条目写 '1v2'、真零行
 * 空集写 '0v2'（v2 ＝ XML 带点赞段的格式代号；旧版单字节标记不认、逼一次带种子的重拉补
 * 点赞数，账 hg-danmaku-geo-20261103 点赞数节），免得日后把「拉过但真没有」与「拉满了」
 * 混作一谈；下次 fetch 命中标记直接
 * 用现成文件（有文件给文件、没文件是已知空集），不再重复拉。没标记的半截文件当种子：重拉时与旧货合并
 * 去重继续长大，拉失败也不缩水；中断/失败不记完成，下次进来接着补。
 * 预拉（下一集）与本集制备走同一条 fetchToFile：预拉只落盘、永不挂载，
 * 挂载只在播放侧按当前集 vid 裁定（VideoActivity.onHongguoDanmakuReady 复核当前集）。
 * 全部方法失败静默降级，绝不抛异常进播放链；平台只下发采样热弹幕，
 * 条数少于平台自报是平台行为（trace §结论 4），本类不做任何补凑。
 */
public class HongguoDanmakuApi {

    public static final String SOURCE_NAME = "红果弹幕";

    private static final long WINDOW_GAP_MS = 180L;
    private static final int MAX_WINDOWS = 40;
    private static final int MAX_ITEMS = 3000;
    private static final int WINDOW_COUNT = 90;

    public static class Item {
        public final long offsetMs;
        public final String text;
        /** 评论 id（批二 P-15 单条动作面用）：回包 common.comment_id，缺失为空串。 */
        public final String commentId;
        /** 发送者标识（批二 P-13 用户类屏蔽／P-15 用）：回包 user.user_id/uid，缺失为空串。 */
        public final String userKey;
        /** 点赞数（回包 comment.stat.digg_count，官方高赞弹幕尾随显数用）：此前整链没带下来、绘制无从画（账 hg-danmaku-geo-20261103 点赞数节）。 */
        public final long diggCount;

        Item(long offsetMs, String text) {
            this(offsetMs, text, "", "", 0);
        }

        Item(long offsetMs, String text, String commentId, String userKey) {
            this(offsetMs, text, commentId, userKey, 0);
        }

        Item(long offsetMs, String text, String commentId, String userKey, long diggCount) {
            this.offsetMs = offsetMs;
            this.text = text;
            this.commentId = commentId == null ? "" : commentId;
            this.userKey = userKey == null ? "" : userKey;
            this.diggCount = Math.max(0, diggCount);
        }
    }

    /** 制备进度回调：每落盘一份可用文件（首份或内容增长后的新版）调一次，item 的 url 始终指向同一文件。在抓取线程调用，UI 挂载由宿主自己切线程。 */
    public interface ProgressListener {
        void onProgress(Danmaku item);
    }

    /** 一窗数据源接缝（生产＝post＋parseWindow，单测喂假窗）：取一窗，失败/回验不过返回 null。 */
    interface WindowSource {
        Window fetch(long start, String cursor);
    }

    /** 累积有增长时的回调，参数为当前全量快照（防御性拷贝）。 */
    interface GrowthListener {
        void onGrowth(List<Item> snapshot);
    }

    /** 拉取结果：items 为最终累积；complete 为是否自然收尾（无一窗失败地拉到底/到上限）——只有 complete 才配落完成标记，半截不记。 */
    static class CollectOutcome {
        final List<Item> items;
        final boolean complete;

        CollectOutcome(List<Item> items, boolean complete) {
            this.items = items;
            this.complete = complete;
        }
    }

    /** 一窗的解析结果：nextStart 为服务端给的下一窗起点（没给时为 -1），hasMore 为服务端原值，推进与否由 nextWindowStart 裁定。 */
    static class Window {
        final List<Item> items;
        final long nextStart;
        final boolean hasMore;
        final String cursor;

        Window(List<Item> items, long nextStart, boolean hasMore, String cursor) {
            this.items = items;
            this.nextStart = nextStart;
            this.hasMore = hasMore;
            this.cursor = cursor;
        }
    }

    // ---------- 窗口推进（纯逻辑，可单测） ----------

    /**
     * 下一窗起点判定：has_more 为假、服务端没给推进值、推进值不大于本窗起点，一律停（返回 -1），
     * 防游标不推进死循环（与评论侧游标纪律同口径）。
     */
    static long nextWindowStart(long start, long responseNext, boolean hasMore) {
        if (!hasMore) return -1;
        if (responseNext <= start) return -1;
        return responseNext;
    }

    /** 合并一窗弹幕：按 offset_ms:text 去重（HGTV 同口径），空文/无时间整条丢弃。同条重到时点赞数取大者换入——种子是旧格式文件（无点赞段、恒 0），不换则重拉一遍数字还是 0（账 hg-danmaku-geo-20261103 点赞数节）。 */
    static void mergeWindow(List<Item> acc, Map<String, Integer> seenAt, List<Item> windowItems) {
        for (Item item : windowItems) {
            if (item == null || item.text == null || item.text.isEmpty() || item.offsetMs < 0) continue;
            String key = item.offsetMs + ":" + item.text;
            Integer at = seenAt.get(key);
            if (at != null) {
                Item old = acc.get(at);
                if (item.diggCount > old.diggCount) {
                    acc.set(at, new Item(old.offsetMs, old.text, old.commentId, old.userKey, item.diggCount));
                }
                continue;
            }
            seenAt.put(key, acc.size());
            acc.add(item);
            if (acc.size() >= MAX_ITEMS) return;
        }
    }

    /** 转 Bilibili 弹幕 XML：按时间升序，p=秒数浮点,滚动,25 号字,白字；正文做 XML 转义。 */
    static String buildXml(List<Item> items) {
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingLong(item -> item.offsetMs));
        StringBuilder sb = new StringBuilder(sorted.size() * 64 + 64);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><i>");
        for (Item item : sorted) {
            sb.append("<d p=\"")
                    .append(String.format(Locale.US, "%.3f", item.offsetMs / 1000f))
                    .append(",1,25,16777215,0,0,")
                    // 尾两段（批二 P-15）：commentId,userKey，只留
                    // 安全字符集，防破 XML 属性；空也占位保段位。
                    .append(safeSeg(item.commentId)).append(',')
                    .append(safeSeg(item.userKey))
                    // 尾第三段：点赞数（纯数字、安全字符集内）。
                    .append(',').append(item.diggCount)
                    .append("\">")
                    .append(escapeXml(item.text))
                    .append("</d>");
        }
        sb.append("</i>");
        return sb.toString();
    }

    static String escapeXml(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    // XML 1.0 非法控制字符直接丢弃，防整份文件被解析器拒收
                    if (c >= 0x20 || c == '\n' || c == '\r' || c == '\t') sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 调试打点（hongguo 两 API 原本零日志，真机定案只能靠样本回放）：只记日志、绝不影响行为——日志通道自身出问题也吞掉，本类的失败静默纪律优先。 */
    private static void log(String msg, Object... args) {
        try {
            SpiderDebug.log("hongguo-danmaku", msg, args);
        } catch (Throwable ignored) {
        }
    }

    // ---------- 抓取 ----------

    /**
     * 窗口拉取循环（纯逻辑，可单测）：逐窗取数合并去重，任一窗取不到（请求失败/回验不过）
     * 即停在已得部分——不再整集连坐；每窗有新增就回调当前全量快照，供增量落盘/挂载。
     * 窗数（40）/条数（3000）上限、窗间 ≥180ms、游标不推进即停的纪律都在这。
     *
     * @param durationMs 本集时长（毫秒），&lt;=0 表示未知——靠服务端推进值与窗数上限收尾。
     * @return 最终累积（可能为空：全程一窗都没取到或全是空窗）。
     */
    static List<Item> collectWindows(WindowSource source, long durationMs, GrowthListener growth) {
        return collectWindows(source, durationMs, growth, null).items;
    }

    /**
     * 带种子与完整性结果的拉取循环：seed 是半截旧文件的既有条目，先并入累积再逐窗拉，
     * 跨新旧按 offset:text 去重——重拉只长不缩。任一窗取不到（null/异常）或线程被中断，
     * 停在已得部分且 complete=false；服务端没更多、时长到头、游标不推进、撞窗数/条数
     * 上限而收尾的，complete=true。
     */
    static CollectOutcome collectWindows(WindowSource source, long durationMs, GrowthListener growth, List<Item> seed) {
        List<Item> all = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        if (seed != null) mergeWindow(all, seen, seed);
        boolean failed = false;
        String cursor = "";
        long start = 0;
        for (int window = 0; window < MAX_WINDOWS; window++) {
            if (window > 0) {
                try {
                    Thread.sleep(WINDOW_GAP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failed = true;
                    break;
                }
            }
            if (durationMs > 0 && start >= durationMs) break;
            Window parsed;
            try {
                parsed = source.fetch(start, cursor);
            } catch (Exception e) {
                failed = true;
                break;
            }
            if (parsed == null) {
                failed = true;
                break;
            }
            int before = all.size();
            mergeWindow(all, seen, parsed.items);
            if (growth != null && all.size() > before) growth.onGrowth(new ArrayList<>(all));
            if (all.size() >= MAX_ITEMS) break;
            long next = nextWindowStart(start, parsed.nextStart, parsed.hasMore);
            if (next < 0) break;
            start = next;
            cursor = parsed.cursor;
        }
        return new CollectOutcome(all, !failed);
    }

    public static Danmaku fetchToFile(String seriesId, String vid, long durationMs) {
        return fetchToFile(seriesId, vid, durationMs, null);
    }

    /**
     * 拉本集弹幕并增量落盘成本地 XML 文件：第一窗拿到货就落盘并经 listener 报出（不等整集），
     * 后续窗有新增就重写同一文件再报；单窗失败保留已得部分。最终返回指向该文件的 Danmaku
     * 条目（name=红果弹幕、url=本地绝对路径）；全程零条才返回 null。本类无静态状态，
     * 一次失败不毒化下一次制备（宿主的重试门在 VideoActivity 侧按成功与否置位）。
     * 命中完成标记时不发任何请求：有文件直接包条目返回、没文件返回 null（已知空集）。
     * 本集制备与下一集预拉共用本方法，预拉侧传 listener=null 且丢弃返回值——只落盘不挂载。
     */
    /** 同集制备串行锁（按 vid，深扫评 E-2）：本集制备与邻集
     * 预拉/异实例同集制备并发时，两条链交替重写同一 XML、
     * .done 由后到者按其结果落定——未拉满能被记成完成。 */
    private static final java.util.concurrent.ConcurrentHashMap<String, Object>
            FETCH_LOCKS = new java.util.concurrent.ConcurrentHashMap<>();

    /** 完成缓存保鲜期：过此期视为陈货、带种子重拉（深扫评
     * E-1）——旧形拉满即永久，他人新弹幕与点赞数变化永不进
     * 表、自发弹幕换实例即丢，只剩手动清缓存一条刷新路。 */
    private static final long COMPLETE_FRESH_MS = 24L * 60 * 60 * 1000;

    public static Danmaku fetchToFile(String seriesId, String vid, long durationMs, ProgressListener listener) {
        if (!HongguoCommentApi.isId(seriesId) || !HongguoCommentApi.isId(vid)) return null;
        Object lock = FETCH_LOCKS.computeIfAbsent(vid, k -> new Object());
        synchronized (lock) {
            return fetchToFileLocked(seriesId, vid, durationMs, listener);
        }
    }

    private static Danmaku fetchToFileLocked(String seriesId, String vid, long durationMs, ProgressListener listener) {
        // 完整缓存免重复拉：标记与文件同目录同生死，标记在就算拉满过
        if (hasCompleteCache(vid) && !completeCacheStale(vid)) {
            File file = cacheFile(vid);
            Danmaku cached = file != null && file.length() > 0 ? toDanmaku(file) : null;
            log("fetch cache-hit vid=%s item=%s", vid, cached != null);
            return cached;
        }
        // 半截文件当种子：旧货先入账，重拉在其上继续长，拉失败也不缩水
        List<Item> seed = readCacheItems(vid);
        Danmaku[] ready = new Danmaku[1];
        CollectOutcome outcome = collectWindows((start, cursor) -> {
            try {
                JSONObject payload = new JSONObject()
                        .put("comment_source", 601).put("server_channel", 1000)
                        .put("group_id", vid).put("group_type", 30)
                        .put("comment_type", 20).put("sort", 1)
                        .put("count", WINDOW_COUNT).put("cursor", cursor)
                        .put("aid", 8662).put("compliance_status", 0)
                        .put("business_param", new JSONObject()
                                .put("book_id", seriesId)
                                .put("start_offset_time", start)
                                .put("playlet_item_duration", Math.max(0, durationMs))
                                .put("need_danmaku_guide_type", new JSONArray().put(1).put(3).put(4).put(2)));
                return parseWindow(HongguoCommentApi.post("comment/list/" + vid, payload, 601, 1000), seriesId, vid);
            } catch (Exception e) {
                log("window fetch failed vid=%s start=%d err=%s", vid, start, e.getClass().getSimpleName());
                return null;
            }
        }, durationMs, snapshot -> {
            Danmaku item = writeItem(vid, snapshot);
            if (item != null) {
                ready[0] = item;
                if (listener != null) listener.onProgress(item);
            }
        }, seed);
        List<Item> all = outcome.items;
        if (all.isEmpty()) {
            // 自然收尾且真空集：记完成免得每次起播白跑全程；失败中断的不记，下次还能补
            if (outcome.complete) markComplete(vid, true);
            log("fetch done vid=%s items=0 complete=%s", vid, outcome.complete);
            return null;
        }
        Danmaku item = ready[0] != null ? ready[0] : writeItem(vid, all);
        // 记完成的门槛：自然收尾且最终文件真落了盘——半截（失败收尾）不记，下次继续增量补
        if (outcome.complete && item != null) markComplete(vid, false);
        log("fetch done vid=%s items=%d complete=%s item=%s", vid, all.size(), outcome.complete, item != null);
        return item;
    }

    /**
     * 清弹幕缓存（抽屉「清红果缓存」连带口，F-03）：XML 与完成标记
     * 整目录摘除——空集标记 '0' 也在内。此前清缓存只清播放模型
     * （DramaCache），某集一次空回包被记 '0' 后连用户自救路都没有、
     * 永久无弹幕；此口补上后，清一次缓存即可重拉。失败静默（与本
     * 类纪律一致）。
     */
    public static void clearCache() {
        try {
            File dir = new File(App.get().getCacheDir(), "hongguo_danmaku");
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File f : files) {
                try {
                    f.delete();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 把当前累积写盘并包成 Danmaku 条目；写盘失败返回 null（已有旧文件不删，后续窗还会再试）。 */
    private static Danmaku writeItem(String vid, List<Item> items) {
        File file = writeXmlFile(vid, buildXml(items));
        return file == null ? null : toDanmaku(file);
    }

    private static Danmaku toDanmaku(File file) {
        Danmaku danmaku = new Danmaku();
        danmaku.setName(SOURCE_NAME);
        danmaku.setUrl(file.getAbsolutePath());
        return danmaku;
    }

    // ---------- 缓存文件与完成标记 ----------

    private static File cacheFile(String vid) {
        try {
            return new File(new File(App.get().getCacheDir(), "hongguo_danmaku"), vid + ".xml");
        } catch (Throwable t) {
            return null;
        }
    }

    private static File markerFile(String vid) {
        try {
            return new File(new File(App.get().getCacheDir(), "hongguo_danmaku"), vid + ".done");
        } catch (Throwable t) {
            return null;
        }
    }

    /** 这集是不是已经拉满过（完成标记在手且为当前格式）。标记与 XML 同目录：系统清缓存两者同去，不会只剩标记骗过判定。旧标记（单字节 '1'/'0'、点赞段入 XML 之前）不认完整——逼一次带种子的重拉把点赞数补进文件，重拉后落新标记（账 hg-danmaku-geo-20261103 点赞数节）。 */
    static boolean hasCompleteCache(String vid) {
        File marker = markerFile(vid);
        if (marker == null || !marker.isFile()) return false;
        try (FileInputStream in = new FileInputStream(marker)) {
            byte[] buf = new byte[8];
            int n = in.read(buf);
            return n > 0 && new String(buf, 0, n, StandardCharsets.UTF_8).contains("v2");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 完成标记是否已过保鲜期（按标记落盘时刻，深扫评 E-1）。 */
    private static boolean completeCacheStale(String vid) {
        File marker = markerFile(vid);
        if (marker == null || !marker.isFile()) return true;
        long age = System.currentTimeMillis() - marker.lastModified();
        return age < 0 || age > COMPLETE_FRESH_MS;
    }

    /** 落完成标记：内容区分收尾形态——有条目的完整集写 '1v2'、真零行空集写 '0v2'，v2 ＝ XML 带点赞段的格式代号，只凭标记在不在分不出这两者，日后排查别再误判。 */
    private static void markComplete(String vid, boolean empty) {
        try {
            File marker = markerFile(vid);
            if (marker == null) return;
            File parent = marker.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return;
            try (FileOutputStream out = new FileOutputStream(marker)) {
                out.write((empty ? "0v2" : "1v2").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    /** 读半截文件的既有条目当种子；读不出（文件不在/坏）就当没有——重拉照常从零长。 */
    private static List<Item> readCacheItems(String vid) {
        File file = cacheFile(vid);
        if (file == null || !file.isFile() || file.length() == 0) return new ArrayList<>();
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) buffer.write(chunk, 0, read);
            return parseXmlItems(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    /** p 属性尾段安全化：只留 [A-Za-z0-9_-]，其余剔除（id 类字段足够）。 */
    private static String safeSeg(String v) {
        if (v == null || v.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final Pattern DANMAKU_ROW = Pattern.compile("<d p=\"([0-9]+(?:\\.[0-9]+)?),([^\"]*)\">(.*?)</d>", Pattern.DOTALL);

    /** 把自家 buildXml 写出的文件读回条目（只认自家格式）：秒数还原毫秒、正文反转义、尾段 id 还原；坏行跳过。 */
    static List<Item> parseXmlItems(String xml) {
        List<Item> items = new ArrayList<>();
        if (xml == null || xml.isEmpty()) return items;
        Matcher matcher = DANMAKU_ROW.matcher(xml);
        while (matcher.find()) {
            try {
                long offsetMs = Math.round(Double.parseDouble(matcher.group(1)) * 1000);
                String text = unescapeXml(matcher.group(3));
                String commentId = "";
                String userKey = "";
                long diggCount = 0;
                String[] seg = matcher.group(2).split(",", -1);
                // p 段序：mode,size,color,ts,pool,cid,userKey,digg
                if (seg.length > 5) commentId = seg[5];
                if (seg.length > 6) userKey = seg[6];
                if (seg.length > 7) {
                    try {
                        diggCount = Long.parseLong(seg[7]);
                    } catch (Exception ignored) {
                    }
                }
                if (offsetMs >= 0 && !text.isEmpty()) {
                    items.add(new Item(offsetMs, text, commentId, userKey, diggCount));
                }
            } catch (Exception ignored) {
            }
        }
        return items;
    }

    static String unescapeXml(String text) {
        // &amp; 必须最后还原，防 &amp;lt; 被二次解成 <
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    // ---------- 解析（含回验闸） ----------

    /** 解析一窗响应；code≠0 或归属回验不过返回 null——回验只管本窗，本窗数据整窗丢弃，先前已验过的窗不受牵连（collectWindows 按已得部分收尾）。 */
    static Window parseWindow(JSONObject result, String seriesId, String vid) {
        try {
            if (result == null) {
                log("gate reject vid=%s reason=no-result", vid);
                return null;
            }
            if (result.optInt("code", -1) != 0) {
                log("gate reject vid=%s reason=code=%d", vid, result.optInt("code", -1));
                return null;
            }
            JSONObject data = result.getJSONObject("data");
            JSONObject info = data.getJSONObject("common_list_info");
            boolean more = info.optBoolean("has_more", false);
            JSONObject extra = data.optJSONObject("extra");
            long next = extra == null ? -1 : extra.optLong("next_query_danmaku_list_time", -1);
            JSONArray rows = data.optJSONArray("data_list");
            List<Item> items = new ArrayList<>();
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row == null) continue;
                    JSONObject comment = row.optJSONObject("comment");
                    if (comment == null) continue;
                    JSONObject common = comment.optJSONObject("common");
                    JSONObject expand = comment.optJSONObject("expand");
                    if (common == null || expand == null) continue;
                    // 回验闸 group_id 腿：归属对不上整窗作废，防误抓别家弹幕（与评论侧同一道闸）。
                    // 窗口本就是按 group_id=vid 定向请求的，此腿对得上是请求寻址的本集 id，作废语义成立。
                    if (common.has("group_id") && !vid.equals(common.optString("group_id"))) {
                        log("gate reject vid=%s leg=group got=%s", vid, common.optString("group_id"));
                        return null;
                    }
                    // book_id 腿只拦硬冲突：601 弹幕载荷不守评论侧的字段语义——真人行 book_id
                    // 恒为 0（只有平台插入行带真值），0/空/缺失＝该行未带书籍归属，跳过此腿；
                    // 只有带了真 id 又与本剧对不上才整窗作废。照评论语义判 0 会把每窗全废（真机实证）。
                    String bookId = expand.optString("book_id", "");
                    if (HongguoCommentApi.isId(bookId) && !seriesId.equals(bookId)) {
                        log("gate reject vid=%s leg=book got=%s", vid, bookId);
                        return null;
                    }
                    JSONObject contentObj = common.optJSONObject("content");
                    String text = contentObj == null ? "" : contentObj.optString("text").trim();
                    long offset = expand.optLong("offset_time", -1);
                    if (text.isEmpty() || offset < 0) continue;
                    // 单条身份两字段（批二 P-15）：落点按 2026-11-01
                    // 活体回包实测形状定版——comment_id 在 comment 本层
                    // （common 下没有此键），user_id 在 common.user_info
                    // 下（comment 下无 user 对象、common 下无 user_id 直键）。
                    // 旧读法（common.comment_id／comment.user）两处全落空，
                    // 真回包 38 条 commentId/userKey 恒空（探针实证），
                    // 只伤屏蔽此人/单条动作匹配，不伤出字。
                    String commentId = comment.optString("comment_id",
                            common.optString("comment_id",
                                    common.optString("id", "")));
                    String userKey = "";
                    JSONObject userInfo = common.optJSONObject("user_info");
                    if (userInfo != null) {
                        userKey = userInfo.optString("user_id",
                                userInfo.optString("uid", ""));
                    }
                    if (userKey.isEmpty()) {
                        JSONObject userObj = comment.optJSONObject("user");
                        if (userObj != null) {
                            userKey = userObj.optString("user_id",
                                    userObj.optString("uid", ""));
                        }
                    }
                    if (userKey.isEmpty()) {
                        userKey = common.optString("user_id", "");
                    }
                    items.add(new Item(offset, text, commentId, userKey,
                            diggOf(row, comment, common, expand)));
                }
            }
            return new Window(items, next, more, info.optString("cursor"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 点赞数读取（与评论侧 HongguoCommentApi 同口径）：comment.stat 优先，row.stat／expand／common 兜底；此前弹幕解析整段没读、数字到不了绘制（账 hg-danmaku-geo-20261103 点赞数节）。 */
    private static long diggOf(JSONObject row, JSONObject comment, JSONObject common, JSONObject expand) {
        JSONObject stat = comment == null ? null : comment.optJSONObject("stat");
        if (stat != null && stat.has("digg_count")) return Math.max(0, stat.optLong("digg_count", 0));
        JSONObject rowStat = row == null ? null : row.optJSONObject("stat");
        if (rowStat != null && rowStat.has("digg_count")) return Math.max(0, rowStat.optLong("digg_count", 0));
        if (expand != null && expand.has("digg_count")) return Math.max(0, expand.optLong("digg_count", 0));
        return common == null ? 0 : Math.max(0, common.optLong("digg_count", 0));
    }

    private static File writeXmlFile(String vid, String xml) {
        try {
            File dir = new File(App.get().getCacheDir(), "hongguo_danmaku");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File file = new File(dir, vid + ".xml");
            // 增量重写走临时文件＋改名原子替换，防渲染层正好读到写一半的文件
            File tmp = new File(dir, vid + ".xml.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(xml.getBytes(StandardCharsets.UTF_8));
            }
            if (tmp.renameTo(file)) return file.length() > 0 ? file : null;
            // 个别文件系统改名失败：退回直写覆盖
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(xml.getBytes(StandardCharsets.UTF_8));
            }
            return file.length() > 0 ? file : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
