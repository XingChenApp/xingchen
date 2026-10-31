package com.fongmi.android.tv.hongguo.book;

import com.fongmi.android.tv.hongguo.api.HgApiClient;
import com.fongmi.android.tv.hongguo.api.HgDevice;
import com.fongmi.android.tv.hongguo.api.HgRequests;
import com.fongmi.android.tv.hongguo.model.HgContinuation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 红果书（小说/听书）同步客户端：与短剧同一域名、同一签名、同一台匿名设备，
 * 全部走 /reading/bookapi/ 家族（规格 dig-audio-novel-source-20261028，本机实拉定版）。
 * 签名与设备注册复用 HgApiClient 的同步口，不另抄一套。
 * 条目解析走递归收集（bookmall cell 结构深处带 book_id 的对象即书条目），
 * 字段名按实测回包（book_id/title/cover_url/episode_count）＋同族备选名兜底。
 */
public final class HgBookClient {

    public static final int CHANNEL_TAB_NOVEL = 2;
    public static final int CHANNEL_TAB_AUDIO = 5;
    public static final int SEARCH_TAB_NOVEL = 1;
    public static final int SEARCH_TAB_AUDIO = 2;

    private static final String PATH_SEARCH = "/reading/bookapi/search/tab/v";
    private static final String PATH_DETAIL = "/reading/bookapi/detail/v1/";
    private static final String PATH_TOC = "/reading/bookapi/directory/all_items/v1/";
    private static final String PATH_FULL = "/reading/reader/full/v1/";
    private static final String PATH_REGISTER_KEY = "/reading/crypt/registerkey";
    private static final String PATH_LANDING = "/reading/bookapi/new_category/landing/v1/";
    private static final String PATH_TONEINFO = "/reading/bookapi/audio/toneinfo/";
    private static final String PATH_BATCH_PLAY = "/reading/bookapi/audio/batch_play/";
    private static final String PATH_PLAYINFO = "/reading/reader/audio/playinfo/";

    private final HgApiClient api = new HgApiClient();

    public static final class Entry {
        public String bookId = "";
        public String title = "";
        public String author = "";
        public String coverUrl = "";
        public String intro = "";
        public int episodeCount = -1;
        /** 详情回包实有键（2026-10-30 探针实拉）：category 分类名、score 评分（0=未评分）。 */
        public String category = "";
        public double score = -1;
    }

    public static final class Chapter {
        public String itemId = "";
        public String title = "";
    }

    public static final class Category {
        public String id = "";
        public String name = "";
    }

    public static final class Page {
        public final List<Entry> entries = new ArrayList<>();
        public final List<Category> categories = new ArrayList<>();
        public HgContinuation next;
        public boolean hasMore;
        /** 分类 landing 回包自带的下一批游标（A-22）；-1＝回包没给。 */
        public int nextOffset = -1;
    }

    // ---------- 频道/分类/搜索 ----------

    /** 频道开页（cont 为 null）或续页（cont 为上一页凭证）。 */
    public Page channel(boolean audio, HgContinuation cont) throws Exception {
        int tab = audio ? CHANNEL_TAB_AUDIO : CHANNEL_TAB_NOVEL;
        String text;
        if (cont == null) {
            text = api.getSignedSync(HgDevice.PATH_BOOKMALL_TAB,
                    HgRequests.discoverPairs(tab, "0"));
            JsonObject root = parseRoot(text);
            Page page = new Page();
            JsonObject data = obj(root, "data");
            JsonObject tabObj = data == null ? null : findTab(data, tab);
            if (tabObj != null) {
                collectEntries(tabObj, page.entries);
                page.next = extractContinuation(tabObj, tab);
            }
            // has_more 不可信（2026-10-31 探针 Probe4 实证）：开页两频道
            // 都报 false 却带续页凭证、续页后一路有书；小说续到第 3 包
            // 报 false、第 4 包照样 16 条。续页与否只认凭证与本包条数：
            // 有凭证且本包有书就还能翻，整包零条目或无凭证才算到底。
            page.hasMore = page.next != null && !page.entries.isEmpty();
            collectCategories(root, page.categories);
            return page;
        }
        text = api.getSignedSync(HgDevice.PATH_BOOKMALL_CELL_CHANGE,
                HgRequests.cellChangePairs(cont));
        JsonObject root = parseRoot(text);
        Page page = new Page();
        JsonObject data = obj(root, "data");
        if (data != null) {
            String nextOffset = firstStr(data, "next_offset");
            if (nextOffset == null) {
                JsonElement no = data.get("next_offset");
                if (no != null && no.isJsonPrimitive() && no.getAsJsonPrimitive().isNumber()) {
                    nextOffset = String.valueOf(no.getAsLong());
                }
            }
            JsonElement view = data.get("cell_view");
            if (view != null) collectEntries(view, page.entries);
            page.next = cont.advanced(nextOffset, str(data, "session_id"));
        }
        // 同开页口径：续页 has_more 同样不可信，只认本包条数
        // （凭证 advanced 永不为空，零条目即到底）。
        page.hasMore = !page.entries.isEmpty();
        return page;
    }

    /**
     * 搜索签整池（A-22，2026-10-31 探针 Probe6/7/8 实证，样本词「都市」小说签）：
     * offset 不是条数偏移——offset=0 是多签首屏形态、目标签只给头部 6 条；
     * 目标签自带的 has_more/next_offset 是唯一续拉信号，按它补一发即得整池
     * （20 条）；此后 1/20/23/40 任意 offset 恒回同一池、has_more 恒 true、
     * next_offset 纯数字递增，没有第 21 条之后。故整池一次交付、hasMore
     * 恒 false，调用方不许再按页码/条数推 offset 假装续拉。听书签未单独
     * 探针，同口同签机制、同一处置。
     */
    public Page search(boolean audio, String keyword) throws Exception {
        int tab = audio ? SEARCH_TAB_AUDIO : SEARCH_TAB_NOVEL;
        String text = api.getSignedSync(PATH_SEARCH, HgRequests.searchPairs(keyword, 0, tab));
        JsonObject root = parseRoot(text);
        Page page = new Page();
        collectEntries(root, page.entries);
        JsonObject tabObj = findSearchTab(root, tab);
        if (tabObj != null && boolOf(tabObj, "has_more")) {
            int next = intOf(tabObj, "next_offset", 0);
            if (next > 0) {
                String more = api.getSignedSync(PATH_SEARCH,
                        HgRequests.searchPairs(keyword, next, tab));
                // collectEntries 按 book_id 去重并入：首屏头部与整池同序子集，
                // 并入后即完整池序，池外漂移条目按回包序尾随。
                collectEntries(parseRoot(more), page.entries);
            }
        }
        page.hasMore = false;
        return page;
    }

    /**
     * 分类落地页：query_gender 8＝全部（页签值 全部8/男生1/女生0，规格 §1.1）。
     * offset 是推荐流批次游标不是条数偏移（A-22 探针实证）：每批恒 10 条、
     * 批间零重叠，回包 data.offset 恒＝请求 offset＋本批条数、has_more 恒
     * true。续拉起点只认回包 offset（Page.nextOffset），本地不许假设步长。
     */
    public Page categoryLanding(String categoryId, int offset) throws Exception {
        List<String[]> pairs = HgDevice.deviceQueryPairs();
        pairs.add(new String[]{"category_id", categoryId});
        pairs.add(new String[]{"query_gender", "8"});
        pairs.add(new String[]{"offset", String.valueOf(offset)});
        pairs.add(new String[]{"count", "20"});
        String text = api.getSignedSync(PATH_LANDING, pairs);
        JsonObject root = parseRoot(text);
        Page page = new Page();
        collectEntries(root, page.entries);
        JsonObject data = obj(root, "data");
        page.nextOffset = data == null ? -1 : intOf(data, "offset", -1);
        page.hasMore = data != null && boolOf(data, "has_more");
        return page;
    }

    // ---------- 详情/目录/正文 ----------

    public Entry detail(String bookId) throws Exception {
        List<String[]> pairs = HgDevice.deviceQueryPairs();
        pairs.add(new String[]{"book_id", bookId});
        String text = api.getSignedSync(PATH_DETAIL, pairs);
        JsonObject root = parseRoot(text);
        JsonObject data = obj(root, "data");
        Entry e = new Entry();
        e.bookId = bookId;
        if (data != null) fillEntry(data, e);
        return e;
    }

    public List<Chapter> toc(String bookId) throws Exception {
        List<String[]> pairs = HgDevice.deviceQueryPairs();
        pairs.add(new String[]{"book_id", bookId});
        String text = api.getSignedSync(PATH_TOC, pairs);
        JsonObject root = parseRoot(text);
        JsonObject data = obj(root, "data");
        List<Chapter> out = new ArrayList<>();
        JsonArray items = data == null ? null : arr(data, "item_data_list");
        if (items != null) {
            for (JsonElement el : items) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                Chapter c = new Chapter();
                c.itemId = str(o, "item_id");
                c.title = str(o, "title");
                if (c.itemId != null && !c.itemId.isEmpty()) {
                    if (c.title == null) c.title = "";
                    out.add(c);
                }
            }
        }
        return out;
    }

    /** 目录回包里的 book_info（书名/作者/封面），给详情面回填用；取不到返回 null。 */
    public Entry tocBookInfo(String bookId) throws Exception {
        List<String[]> pairs = HgDevice.deviceQueryPairs();
        pairs.add(new String[]{"book_id", bookId});
        String text = api.getSignedSync(PATH_TOC, pairs);
        JsonObject root = parseRoot(text);
        JsonObject data = obj(root, "data");
        JsonObject info = data == null ? null : obj(data, "book_info");
        if (info == null) return null;
        Entry e = new Entry();
        e.bookId = bookId;
        fillEntry(info, e);
        return e;
    }

    /**
     * 小说正文（已解密的纯文本）：未注册内容密钥时服务端只回 Invalid，
     * 这里自动注册后重试一次（HgNovelCrypto）。
     * 返回 [章标题, 正文纯文本]。
     */
    public String[] chapterText(String bookId, String itemId) throws Exception {
        JsonObject item = fullOnce(bookId, itemId);
        int crypt = item.has("crypt_status") ? item.get("crypt_status").getAsInt() : 0;
        String content = str(item, "content");
        if (crypt == 2 || "Invalid".equals(content)) {
            HgNovelCrypto.ensureRegistered(this);
            item = fullOnce(bookId, itemId);
            content = str(item, "content");
            if ("Invalid".equals(content)) {
                // 绑定核对过了还 Invalid：缓存密钥本身已失效（服务端轮换/过期），
                // 清掉强制重注册，再取最后一次（每次开章最多两次注册）。
                HgNovelCrypto.resetKey();
                HgNovelCrypto.ensureRegistered(this);
                item = fullOnce(bookId, itemId);
                content = str(item, "content");
            }
        }
        if (content == null || content.isEmpty() || "Invalid".equals(content)) {
            throw new Exception("这一章没取到正文");
        }
        byte[] plain = HgNovelCrypto.decryptContent(content);
        int compress = item.has("compress_status") ? item.get("compress_status").getAsInt() : 0;
        if (compress == 1) plain = HgNovelCrypto.gunzip(plain);
        String xhtml = new String(plain, StandardCharsets.UTF_8);
        String title = str(item, "title");
        return new String[]{title == null ? "" : title, xhtmlToText(xhtml)};
    }

    private JsonObject fullOnce(String bookId, String itemId) throws Exception {
        List<String[]> pairs = HgDevice.deviceQueryPairs();
        pairs.add(new String[]{"book_id", bookId});
        pairs.add(new String[]{"item_id", itemId});
        pairs.add(new String[]{"key_register_ts", String.valueOf(HgNovelCrypto.keyVer())});
        String text = api.getSignedSync(PATH_FULL, pairs);
        JsonObject root = parseRoot(text);
        JsonObject data = obj(root, "data");
        if (data == null) throw new Exception("正文回包为空");
        // full 的 data 就是 ItemContent 本体；个别形态按 item_id 分键时取内层
        if (!data.has("content") && data.has(itemId) && data.get(itemId).isJsonObject()) {
            return data.getAsJsonObject(itemId);
        }
        return data;
    }

    /** 内容密钥注册（HgNovelCrypto 构造 content 后调这里发请求）。 */
    public JsonObject registerKey(String content) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("content", content);
        body.addProperty("keyver", 1);
        String text = api.postSignedSync(PATH_REGISTER_KEY, body);
        JsonObject root = parseRoot(text);
        JsonObject data = obj(root, "data");
        if (data == null) throw new Exception("内容密钥注册回包为空");
        return data;
    }

    // ---------- 听书（浏览/音色/播放信息；音频解密未破，播放面暂不接） ----------

    public JsonObject toneInfo(String bookId) throws Exception {
        List<String[]> pairs = HgDevice.deviceQueryPairs();
        pairs.add(new String[]{"book_id", bookId});
        String text = api.getSignedSync(PATH_TONEINFO, pairs);
        JsonObject root = parseRoot(text);
        return obj(root, "data");
    }

    /** 一个可选音色：TTS 合成音（id＝tone_id）或真人音色（id＝abook_id）。 */
    public static final class Tone {
        public static final int KIND_TTS = 0;
        public static final int KIND_AUDIO = 1;
        public int kind;
        public long id;
        public String title = "";
        public String iconUrl = "";
        /** TTS 音色的一句画像（回包 description，如「超自然」）；真人音色无此字段。 */
        public String desc = "";
        /** TTS 音色性别（回包 tone_gender：1 男／2 女／0 多角色）；真人音色为 -1。 */
        public int gender = -1;
    }

    /** 一本书的音色全貌（toneinfo 回包解析，字段口径见官方模型 BookToneInfo/TtsToneInfo/AudioToneInfo）。 */
    public static final class ToneSet {
        public final List<Tone> ttsTones = new ArrayList<>();
        public final List<Tone> audioTones = new ArrayList<>();
        /** 服务端推荐（默认）音色 id；0＝回包没给。 */
        public long recommendTone;
        /** 本书 genre：0＝TTS 书（合成音）、1＝真人有声书；-1＝回包没给。 */
        public int genreType = -1;
        /** 关联的小说书 id（真人书上 tts_tones 归它）；TTS 书回的是自身 id。 */
        public String relateNovelBookId = "";

        public boolean isEmpty() {
            return ttsTones.isEmpty() && audioTones.isEmpty();
        }

        public boolean hasTts(long toneId) {
            for (Tone t : ttsTones) if (t.id == toneId) return true;
            return false;
        }
    }

    /**
     * 本书音色表（T-02，2026-10-31 探针实拉定版，样本书 7596021152615517209）：
     * tts_tones 6 条（id/title/icon_url/description/tone_gender）＋audio_tones
     * 1 条（abook_id/title/icon_url）＋recommend_tone＝74；真人书同口回的
     * 是同一张表（req_book_genre_type=1、relate_novel_bookid 指向 TTS 书）。
     */
    public ToneSet tones(String bookId) throws Exception {
        ToneSet set = new ToneSet();
        JsonObject data = toneInfo(bookId);
        if (data == null) return set;
        JsonArray tts = arr(data, "tts_tones");
        if (tts != null) {
            for (JsonElement el : tts) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                Tone t = new Tone();
                t.kind = Tone.KIND_TTS;
                t.id = longOf(o, "id", 0);
                if (t.id <= 0) continue;
                t.title = orEmpty(str(o, "title"));
                t.iconUrl = orEmpty(str(o, "icon_url"));
                t.desc = orEmpty(str(o, "description"));
                t.gender = intOf(o, "tone_gender", -1);
                set.ttsTones.add(t);
            }
        }
        JsonArray audio = arr(data, "audio_tones");
        if (audio != null) {
            for (JsonElement el : audio) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                Tone t = new Tone();
                t.kind = Tone.KIND_AUDIO;
                t.id = longOf(o, "abook_id", 0);
                if (t.id <= 0) continue;
                t.title = orEmpty(str(o, "title"));
                t.iconUrl = orEmpty(str(o, "icon_url"));
                set.audioTones.add(t);
            }
        }
        set.recommendTone = longOf(data, "recommend_tone", 0);
        set.genreType = intOf(data, "req_book_genre_type", -1);
        String relate = firstStr(data, "relate_novel_bookid_str", "relate_novel_bookid");
        set.relateNovelBookId = relate == null ? "" : relate;
        return set;
    }

    /**
     * 按音色取单章播放信息（T-02 换音色的取流口，探针实拉定版）：
     * GET reader/audio/playinfo 带 tone_id 才出数——不带回 code=101019
     * （NO_THIS_TONE）；六个音色各回不同 vid/时长，是真换音色不是换名。
     * batch_play 不认任何客户端音色字段（required_tone_id/tone_id/
     * tone_histories 三路探针回显恒推荐音色），故换音色只能走本口。
     * 回包 data[0] 即播放信息本体（main_url/backup_url/encryption_key/
     * is_encrypt，与 batch 的 play_info_data 同形），空地址失败口径与
     * audioPlayInfo 对齐（isNoUrlFailure 两句不变）。
     */
    public JsonObject audioPlayInfoTone(String bookId, String itemId, long toneId) throws Exception {
        List<String[]> pairs = HgDevice.deviceQueryPairs();
        pairs.add(new String[]{"book_id", bookId});
        pairs.add(new String[]{"item_ids", itemId});
        pairs.add(new String[]{"tone_id", String.valueOf(toneId)});
        String text = api.getSignedSync(PATH_PLAYINFO, pairs);
        JsonObject root = parseRoot(text);
        JsonArray data = arr(root, "data");
        if (data == null || data.size() == 0) throw new Exception("播放信息回包为空");
        JsonObject first = data.get(0).getAsJsonObject();
        JsonObject info = obj(first, "play_info_data");
        JsonObject back = info == null ? first : info;
        String main = str(back, "main_url");
        String backup = str(back, "backup_url");
        if ((main == null || main.isEmpty()) && (backup == null || backup.isEmpty())) {
            throw new Exception("服务端没给播放地址（tone_id=" + toneId + "）");
        }
        return back;
    }

    private static long longOf(JsonObject o, String key, long def) {
        String s = str(o, key);
        if (s == null) return def;
        try {
            return Long.parseLong(s.trim());
        } catch (Exception ignored) {
            return def;
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * audioPlayInfo 的失败是不是「这一章双地址皆空/没给数据」：
     * 只认本类自己抛的两句（服务端没给播放地址…／播放信息回包为空），
     * 网络失败、回包空体、接口报错都不算——听书朗读兜底的触发判据之一。
     */
    public static boolean isNoUrlFailure(String message) {
        return message != null && (message.startsWith("服务端没给播放地址")
                || message.startsWith("播放信息回包为空"));
    }

    /**
     * 书的 book_type（detail 原始字段，2026-10-28 双样本实拉定版）：
     * 0＝小说本体——听书靠合成音出声，即 TTS 书；1＝真人有声书（独立制作）。
     * 字段缺失返回 -1（判定不了，调用方按「不是 TTS 书」保守处理）。
     */
    public int bookType(String bookId) throws Exception {
        List<String[]> pairs = HgDevice.deviceQueryPairs();
        pairs.add(new String[]{"book_id", bookId});
        String text = api.getSignedSync(PATH_DETAIL, pairs);
        JsonObject root = parseRoot(text);
        JsonObject data = obj(root, "data");
        if (data == null) return -1;
        return intOf(data, "book_type", -1);
    }

    /** 批量播放信息：回 data[0].play_info_data（含 main_url/encryption_key 等）。 */
    public JsonObject audioPlayInfo(String bookId, String itemId) throws Exception {
        JsonObject req = new JsonObject();
        addId(req, "book_id", bookId);
        addId(req, "item_id", itemId);
        JsonArray list = new JsonArray();
        list.add(req);
        JsonObject body = new JsonObject();
        body.add("book_play_requests", list);
        String text = api.postSignedSync(PATH_BATCH_PLAY, body);
        JsonObject root = parseRoot(text);
        JsonArray data = arr(root, "data");
        if (data == null || data.size() == 0) throw new Exception("播放信息回包为空");
        JsonObject first = data.get(0).getAsJsonObject();
        JsonObject info = obj(first, "play_info_data");
        JsonObject back = info == null ? first : info;
        String main = str(back, "main_url");
        String backup = str(back, "backup_url");
        if ((main == null || main.isEmpty()) && (backup == null || backup.isEmpty())) {
            String err = str(first, "err_code");
            JsonObject state = obj(first, "audio_play_error_state");
            String stateText = state == null ? null : state.toString();
            StringBuilder sb = new StringBuilder("服务端没给播放地址");
            if (err != null && !err.isEmpty() && !"0".equals(err)) sb.append(" err_code=").append(err);
            if (stateText != null && !"{}".equals(stateText)) sb.append(" state=").append(stateText);
            throw new Exception(sb.toString());
        }
        return back;
    }

    private static void addId(JsonObject o, String key, String value) {
        if (value != null) {
            try {
                o.addProperty(key, Long.parseLong(value.trim()));
                return;
            } catch (NumberFormatException ignored) {
            }
        }
        o.addProperty(key, value);
    }

    // ---------- 解析工具 ----------

    private static JsonObject parseRoot(String text) throws Exception {
        if (text == null || text.isEmpty()) throw new Exception("红果书接口没回数据");
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        int code = root.has("code") ? root.get("code").getAsInt() : 0;
        if (code != 0) {
            String msg = str(root, "message");
            throw new Exception(msg == null || msg.isEmpty()
                    ? "红果书接口失败(" + code + ")" : msg);
        }
        return root;
    }

    /** 递归收集带 book_id 的书条目（按 book_id 去重、保序）。 */
    private static void collectEntries(JsonElement el, List<Entry> out) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (Entry e : out) seen.put(e.bookId, Boolean.TRUE);
        collectEntries(el, out, seen, 0);
    }

    private static void collectEntries(JsonElement el, List<Entry> out,
                                       Map<String, Boolean> seen, int depth) {
        if (el == null || depth > 10) return;
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                collectEntries(child, out, seen, depth + 1);
            }
            return;
        }
        if (!el.isJsonObject()) return;
        JsonObject o = el.getAsJsonObject();
        String bookId = str(o, "book_id");
        if (bookId != null && !bookId.isEmpty() && !seen.containsKey(bookId)) {
            Entry e = new Entry();
            e.bookId = bookId;
            fillEntry(o, e);
            if (!e.title.isEmpty()) {
                seen.put(bookId, Boolean.TRUE);
                out.add(e);
            }
        }
        for (Map.Entry<String, JsonElement> en : o.entrySet()) {
            collectEntries(en.getValue(), out, seen, depth + 1);
        }
    }

    private static void collectCategories(JsonElement el, List<Category> out) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (Category c : out) seen.put(c.id, Boolean.TRUE);
        collectCategories(el, out, seen, 0);
    }

    private static void collectCategories(JsonElement el, List<Category> out,
                                          Map<String, Boolean> seen, int depth) {
        if (el == null || depth > 10) return;
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                collectCategories(child, out, seen, depth + 1);
            }
            return;
        }
        if (!el.isJsonObject()) return;
        JsonObject o = el.getAsJsonObject();
        String cid = firstStr(o, "category_id");
        String name = firstStr(o, "name", "title", "category_name");
        if (cid != null && !cid.isEmpty() && name != null && !name.isEmpty()
                && !seen.containsKey(cid)) {
            Category c = new Category();
            c.id = cid;
            c.name = name;
            seen.put(cid, Boolean.TRUE);
            out.add(c);
        }
        for (Map.Entry<String, JsonElement> en : o.entrySet()) {
            collectCategories(en.getValue(), out, seen, depth + 1);
        }
    }

    private static void fillEntry(JsonObject o, Entry e) {
        String t = firstStr(o, "title", "book_name", "name");
        if (t != null) e.title = t;
        String a = firstStr(o, "author", "author_name");
        if (a != null) e.author = a;
        String cover = firstStr(o, "cover_url", "thumb_url", "large_thumb_url", "cover");
        if (cover != null) e.coverUrl = cover;
        String intro = firstStr(o, "abstract", "intro", "book_intro", "desc", "description");
        if (intro != null) e.intro = intro;
        if (o.has("episode_count")) e.episodeCount = intOf(o, "episode_count", -1);
        else if (o.has("chapter_count")) e.episodeCount = intOf(o, "chapter_count", -1);
        else if (o.has("serial_count")) e.episodeCount = intOf(o, "serial_count", -1);
        String cat = firstStr(o, "category");
        if (cat != null) e.category = cat;
        if (o.has("score")) {
            try {
                JsonElement se = o.get("score");
                e.score = se.isJsonPrimitive() && se.getAsJsonPrimitive().isString()
                        ? Double.parseDouble(se.getAsString().trim()) : se.getAsDouble();
            } catch (Exception ignored) {
            }
        }
    }

    /** XHTML 正文 → 纯文本：段落 </p> 断行、去标签、解常见实体。 */
    public static String xhtmlToText(String xhtml) {
        if (xhtml == null) return "";
        String s = xhtml;
        s = s.replaceAll("(?i)</p\\s*>", "\n\n");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</h[1-6]\\s*>", "\n\n");
        s = s.replaceAll("<[^>]+>", "");
        s = s.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) {
                if (sb.length() > 0 && !sb.toString().endsWith("\n\n")) sb.append("\n\n");
            } else {
                sb.append(t).append('\n');
            }
        }
        String out = sb.toString().replaceAll("\n{3,}", "\n\n").trim();
        return out;
    }

    /** 搜索回包顶层 search_tabs 里按 tab_type 找目标签（has_more/next_offset 在签上）。 */
    private static JsonObject findSearchTab(JsonObject root, int tabType) {
        JsonArray tabs = root == null ? null : arr(root, "search_tabs");
        if (tabs == null) return null;
        for (JsonElement el : tabs) {
            if (el != null && el.isJsonObject()
                    && intOf(el.getAsJsonObject(), "tab_type", -1) == tabType) {
                return el.getAsJsonObject();
            }
        }
        return null;
    }

    private static boolean boolOf(JsonObject o, String key) {
        if (o == null) return false;
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return false;
        if (e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
        if (e.getAsJsonPrimitive().isNumber()) return e.getAsInt() != 0;
        String s = e.getAsString();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private static JsonObject findTab(JsonObject data, int tabType) {
        JsonArray tabs = arr(data, "tab_item");
        if (tabs == null) return null;
        for (JsonElement el : tabs) {
            if (el != null && el.isJsonObject()
                    && intOf(el.getAsJsonObject(), "tab_type", -1) == tabType) {
                return el.getAsJsonObject();
            }
        }
        return null;
    }

    /** 续页凭证抽取：与 HgParser.extractContinuation 同口径（session 在 tab 上、cell 取 cell_id_str 非空且 show_type≠0）。 */
    private static HgContinuation extractContinuation(JsonObject tab, int tabType) {
        String sessionId = str(tab, "session_id");
        if (sessionId == null || sessionId.isEmpty()) return null;
        String template = null;
        JsonElement ct = tab.get("client_template");
        if (ct != null && ct.isJsonPrimitive()) {
            if (ct.getAsJsonPrimitive().isNumber()) {
                if (ct.getAsLong() > 0) template = String.valueOf(ct.getAsLong());
            } else {
                String s = ct.getAsString();
                if (s != null && !s.isEmpty()) template = s;
            }
        }
        JsonArray cells = arr(tab, "cell_data");
        if (cells == null) return null;
        JsonObject first = null;
        JsonObject nested = null;
        for (JsonElement el : cells) {
            if (el == null || !el.isJsonObject()) continue;
            JsonObject cell = el.getAsJsonObject();
            String cellId = firstStr(cell, "cell_id_str", "cell_id");
            if (cellId == null) continue;
            if (intOf(cell, "show_type", 0) == 0) continue;
            if (first == null) first = cell;
            JsonElement inner = cell.get("cell_data");
            if (inner != null && inner.isJsonArray() && inner.getAsJsonArray().size() > 0) {
                nested = cell;
                break;
            }
        }
        JsonObject pick = nested != null ? nested : first;
        if (pick == null) return null;
        String pickId = firstStr(pick, "cell_id_str", "cell_id");
        if (pickId == null) return null;
        return new HgContinuation(pickId, sessionId, template,
                String.valueOf(intOf(pick, "show_type", 0)),
                String.valueOf(tabType), "0");
    }

    private static JsonObject obj(JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonArray arr(JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    private static String str(JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return null;
        return e.getAsJsonPrimitive().isString() ? e.getAsString()
                : String.valueOf(e.getAsNumber());
    }

    private static String firstStr(JsonObject o, String... keys) {
        for (String k : keys) {
            String v = str(o, k);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static int intOf(JsonObject o, String key, int def) {
        if (o == null) return def;
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;
        if (e.getAsJsonPrimitive().isNumber()) return e.getAsInt();
        if (e.getAsJsonPrimitive().isString()) {
            try {
                return Integer.parseInt(e.getAsString().trim());
            } catch (Exception ignored) {
                return def;
            }
        }
        return def;
    }
}
