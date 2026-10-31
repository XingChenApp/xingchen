package com.fongmi.android.tv.hongguo.api;

import com.fongmi.android.tv.hongguo.drama.DramaCellPage;
import com.fongmi.android.tv.hongguo.drama.DramaItem;
import com.fongmi.android.tv.hongguo.drama.DramaParser;
import com.fongmi.android.tv.hongguo.sign.HgNativeSign;
import com.fongmi.android.tv.hongguo.sign.HgSign;
import com.fongmi.android.tv.hongguo.ui.HgStore;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 红果账号云端片单同步（2026-10-31 点赞/预约补全批）：登录成功后把
 * 官方账号里的收藏/历史/点赞/预约拉回本地镜像（{@link HgStore}），
 * 收藏与预约动作发生时向服务端上报。
 *
 * <p>端点与请求体/回包形状全部照旧四阶段实现的实证（2026-10-30 重建
 * 时整套删除、git 历史在案，逐项出处见各方法注；与 hongguo-assistant
 * 拆解账第六节 cloud.rs 的六口逐一对得上）：</p>
 * <ul>
 *   <li>收藏拉取 GET /reading/bookapi/bookshelf/video/list（本人追剧架）；</li>
 *   <li>收藏上报 POST /reading/bookapi/bookshelf/video/update（AddShelf/DelShelf）；</li>
 *   <li>历史拉取 GET /reading/bookapi/read_history/list（book_type 2/3 两路分页）；</li>
 *   <li>点赞拉取 GET /reading/ugc/action/mget（action_type=3 本人维度）；</li>
 *   <li>预约拉取 GET /reading/user/subscribe/list（tab_type=1、is_online 两档）；</li>
 *   <li>预约上报 POST /reading/bookapi/search/uncover_subscribe（op_type 1/2）。</li>
 * </ul>
 *
 * <p>未接两项（证据不齐，不猜）：①历史<b>上报</b>（read_history/update）——
 * 旧实现全树零调用、无已验请求体；②点赞<b>上报</b>（articleapi/do_action）——
 * 其传输件 HgCommentClient 已随重建删除，本批不复活。两项本地镜像照常
 * 工作，只是登录后不与云端互通该方向。</p>
 *
 * <p>传输口径（与旧实现的一处自觉差异）：旧实现走 freshQuery 随机编造
 * iid/device_id——与现行「一张脸」纪律冲突，不复活。本类改走现行自家面：
 * ensureDevice 注册闸→HgSign 逐请求签名（设备段用注册档案）→会话头
 * （Cookie＋x-tt-token，出自 {@link HgAccountStore}）→HgNativeSign 原生
 * 优先重签，与 HgPassportClient 的 passport 链同路。账号 Cookie 只挂
 * 自家面，绝不挂共享呈现（HgApiClient.headers 注的禁令同理）。</p>
 *
 * <p>合并口径（旧 HgMineView 定版）：拉取只<b>增</b>不删——服务端回空
 * 不冲本地镜像（防冲空），元信息本地已有字段优先、只补缺；历史走
 * {@link HgStore#importServerHistory}（本地已有断点的剧不动）。上报
 * 失败静默（本地镜像先行是现行口径，不回滚 UI），只记日志待下次
 * 登录同步对齐。全部方法未登录即空转，不发请求。</p>
 */
public final class HgAccountSync {

    private static final String TAG = "HgAcctSync";
    private static final String BV = "/v1/";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hg-acct-sync");
        t.setDaemon(true);
        return t;
    });

    private HgAccountSync() {
    }

    // ---------- 对外口 ----------

    /** 本进程是否已完成过一轮全量拉取（D-12 进程级闸：进面补拉只跑一次）。 */
    private static volatile boolean syncedThisProcess;

    /** 登录成功后全量拉取合并（收藏/历史/点赞/预约四面，逐面独立、互不连坐）。 */
    public static void syncAfterLogin() {
        if (!HgAccountStore.isLoggedIn()) return;
        WORKER.execute(() -> {
            runPulls();
            syncedThisProcess = true;
        });
    }

    /**
     * 进我的页补拉（D-12）：此前同步唯一触发点是登录成功瞬间——靠落盘
     * 会话恢复登录态的后续冷启从不重拉，他端新增的收藏/历史永不进本地。
     * 现：已登录且本进程未同步过才跑一轮，跑完回调（在 WORKER 线程，
     * 调用方自行切主线程刷面）；已同步过/未登录则立即回调。
     */
    public static void syncOnEnter(Runnable onDone) {
        if (!HgAccountStore.isLoggedIn() || syncedThisProcess) {
            if (onDone != null) onDone.run();
            return;
        }
        WORKER.execute(() -> {
            runPulls();
            syncedThisProcess = true;
            if (onDone != null) onDone.run();
        });
    }

    /** 四面拉取合并一轮（调用方须在 WORKER 线程）。 */
    private static void runPulls() {
        pullReservations();
        pullShelf();
        pullHistory();
        pullLikes();
    }

    /** 收藏/追更动作上报（追剧架 AddShelf/DelShelf）；未登录空转、失败静默。 */
    public static void reportShelf(String seriesId, boolean add) {
        if (seriesId == null || seriesId.isEmpty() || !HgAccountStore.isLoggedIn()) return;
        WORKER.execute(() -> {
            try {
                String text = accountPostSync(
                        "/reading/bookapi/bookshelf/video/update" + BV,
                        shelfUpdateBody(seriesId, add));
                if (!HgApiClient.isOkCode(text)) {
                    android.util.Log.w(TAG, "追剧架上报被拒 id=" + seriesId + " add=" + add);
                }
            } catch (Exception e) {
                android.util.Log.w(TAG, "追剧架上报失败 id=" + seriesId + " " + e.getMessage());
            }
        });
    }

    /** 预约/取消预约上报（uncover_subscribe）；未登录空转、失败静默。 */
    public static void reportReserve(String seriesId, boolean subscribe) {
        if (seriesId == null || seriesId.isEmpty() || !HgAccountStore.isLoggedIn()) return;
        WORKER.execute(() -> {
            try {
                String text = accountPostSync(
                        "/reading/bookapi/search/uncover_subscribe" + BV,
                        subscribeBody(seriesId, subscribe));
                if (!HgApiClient.isOkCode(text)) {
                    android.util.Log.w(TAG, "预约上报被拒 id=" + seriesId + " sub=" + subscribe);
                }
            } catch (Exception e) {
                android.util.Log.w(TAG, "预约上报失败 id=" + seriesId + " " + e.getMessage());
            }
        });
    }

    // ---------- 拉取合并 ----------

    /** 预约两档（已上线/待上线）各自分页拉全：置 reserved 镜像＋元信息；已上线档落 online 分档集。 */
    private static void pullReservations() {
        for (boolean online : new boolean[]{true, false}) {
            try {
                Set<String> tierIds = new LinkedHashSet<>();
                int offset = 0;
                while (true) {
                    List<Entry> page = parseSubscribeList(accountGetSync(
                            "/reading/user/subscribe/list" + BV,
                            subscribeListPairs(offset, online)));
                    if (page == null) break;
                    for (Entry e : page) {
                        HgStore.setReserved(e.id, true);
                        fillMeta(e);
                        tierIds.add(e.id);
                    }
                    if (page.size() >= 50 && offset < 450) offset += 50;
                    else break;
                }
                if (online && !tierIds.isEmpty()) HgStore.setReservedOnlineIds(tierIds);
            } catch (Exception e) {
                android.util.Log.w(TAG, "预约拉取失败 online=" + online + " " + e.getMessage());
            }
        }
    }

    /** 追剧架本人列表：回包走频道同族 cell 信封（旧实现同解析路），条目置 followed 正源。 */
    private static void pullShelf() {
        try {
            String text = accountGetSync(
                    "/reading/bookapi/bookshelf/video/list" + BV, new ArrayList<>());
            DramaCellPage page = DramaParser.parseCellPage(text);
            if (page == null || page.getItems() == null) return;
            java.util.Set<String> cloud = new java.util.HashSet<>();
            for (DramaItem item : page.getItems()) {
                if (item == null || item.getSeriesId() == null) continue;
                cloud.add(item.getSeriesId());
                HgStore.setShelfMirrored(item.getSeriesId(), true);
                fillMeta(new Entry(item.getSeriesId(), item.getTitle(),
                        item.getCoverUrl(), item.getEpisodeCount()));
            }
            // 存量上行迁移（#22）：本地优先时代攒下、云端还没有的
            // 收藏逐个 AddShelf 上行——上行后归账号，此后以云端态
            // 为准（下次拉取即收口）。点赞无已验上行口，存量暂留
            // 镜像待端点验到再迁，不删用户数据。
            for (String id : HgStore.followedIds()) {
                if (!cloud.contains(id)) reportShelf(id, true);
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "收藏拉取失败 " + e.getMessage());
        }
    }

    /** 服务端历史：book_type 2（短剧）/3（合集）两路分页拉全，本地没有的剧才导入。 */
    private static void pullHistory() {
        for (int bookType : new int[]{2, 3}) {
            try {
                int offset = 0;
                int depth = 0;
                while (true) {
                    List<String[]> pairs = new ArrayList<>();
                    pairs.add(new String[]{"book_type", String.valueOf(bookType)});
                    pairs.add(new String[]{"offset", String.valueOf(Math.max(0, offset))});
                    pairs.add(new String[]{"is_first_load", offset == 0 ? "true" : "false"});
                    HistoryPage page = parseReadHistory(accountGetSync(
                            "/reading/bookapi/read_history/list" + BV, pairs));
                    if (page == null) break;
                    for (HistoryEntry e : page.items) {
                        fillMeta(new Entry(e.seriesId, e.title, e.cover, e.episodeCount));
                        HgStore.importServerHistory(e.seriesId, e.episodeIndex,
                                e.positionMs, e.readTsMs);
                    }
                    if (page.hasMore && page.nextOffset >= 0 && depth < 20) {
                        offset = page.nextOffset;
                        depth++;
                    } else break;
                }
            } catch (Exception e) {
                android.util.Log.w(TAG, "历史拉取失败 type=" + bookType + " " + e.getMessage());
            }
        }
    }

    /** 本人点赞列表（ugc/action/mget，action_type=3）：分页拉全，置 liked 镜像。 */
    private static void pullLikes() {
        try {
            int offset = 0;
            int depth = 0;
            while (true) {
                List<String[]> pairs = new ArrayList<>();
                pairs.add(new String[]{"action_type", "3"});
                pairs.add(new String[]{"object_type_list", "6,15,10"});
                pairs.add(new String[]{"count", "50"});
                pairs.add(new String[]{"offset", String.valueOf(Math.max(0, offset))});
                LikesPage page = parseMyLikes(accountGetSync(
                        "/reading/ugc/action/mget" + BV, pairs));
                if (page == null) break;
                for (Entry e : page.items) {
                    HgStore.setLiked(e.id, true);
                    fillMeta(e);
                }
                if (page.hasMore && page.nextOffset >= 0 && depth < 20) {
                    offset = page.nextOffset;
                    depth++;
                } else break;
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "点赞拉取失败 " + e.getMessage());
        }
    }

    /** 元信息合并：本地已有字段优先、新数据只补缺（旧 HgMineView 同口径）。 */
    private static void fillMeta(Entry e) {
        if (e == null || e.id == null || e.id.isEmpty()) return;
        String[] local = HgStore.metaOf(e.id);
        String title = e.title != null && !e.title.isEmpty() ? e.title
                : local != null ? local[0] : null;
        String cover = e.cover != null && !e.cover.isEmpty() ? e.cover
                : local != null ? local[1] : null;
        int count = e.count > 0 ? e.count : -1;
        if (count < 0 && local != null) {
            try {
                count = Integer.parseInt(local[2]);
            } catch (Exception ignored) {
            }
        }
        HgStore.putMeta(e.id, title, cover, count);
    }

    // ---------- 请求体（旧 HgRequests 实证形状，逐字复原） ----------

    /** 追剧架更新体（UpdateBookShelfVideo rpc 模型：Watch=2、AddShelf=0/DelShelf=1；add 时带 shark_extra）。 */
    private static JsonObject shelfUpdateBody(String seriesId, boolean add) {
        JsonObject item = new JsonObject();
        item.addProperty("book_id", seriesId);
        item.addProperty("book_type", 2);
        item.addProperty("group_name", "");
        item.addProperty("modify_time", System.currentTimeMillis());
        item.addProperty("video_shelf_operate_type", add ? 0 : 1);
        JsonArray list = new JsonArray();
        list.add(item);
        JsonObject body = new JsonObject();
        body.add("update_bookshelf_video_list", list);
        if (add) {
            JsonObject shark = new JsonObject();
            shark.addProperty("is_active_behavior", "true");
            shark.addProperty("inactive_type", "0");
            body.add("shark_extra", shark);
        }
        return body;
    }

    /** 预约体（SearchUncoverSubscribeRequest：item_type 短剧=14、op_type Subscribe=1/CancelSubscribe=2）。 */
    private static JsonObject subscribeBody(String seriesId, boolean subscribe) {
        JsonObject body = new JsonObject();
        try {
            body.addProperty("item_id", Long.parseLong(seriesId));
        } catch (Exception e) {
            body.addProperty("item_id", seriesId);
        }
        body.addProperty("item_type", 14);
        body.addProperty("op_type", subscribe ? 1 : 2);
        body.addProperty("item_id_str", seriesId);
        return body;
    }

    /** 预约列表读参（GetUserSubscribeListRequest：tab_type MySubscribed=1、is_online 分档）。 */
    private static List<String[]> subscribeListPairs(int offset, boolean online) {
        List<String[]> pairs = new ArrayList<>();
        pairs.add(new String[]{"tab_type", "1"});
        pairs.add(new String[]{"limit", "50"});
        pairs.add(new String[]{"offset", String.valueOf(Math.max(0, offset))});
        pairs.add(new String[]{"is_online", String.valueOf(online)});
        return pairs;
    }

    // ---------- 回包解析（旧 HgParser 容忍抽取的移植，只取合并所需字段） ----------

    private static final class Entry {
        final String id;
        final String title;
        final String cover;
        final int count;

        Entry(String id, String title, String cover, int count) {
            this.id = id;
            this.title = title;
            this.cover = cover;
            this.count = count;
        }
    }

    private static final class HistoryEntry {
        String seriesId;
        String title;
        String cover;
        int episodeCount;
        int episodeIndex;
        long positionMs;
        long readTsMs;
    }

    private static final class HistoryPage {
        final List<HistoryEntry> items = new ArrayList<>();
        boolean hasMore;
        int nextOffset = -1;
    }

    private static final class LikesPage {
        final List<Entry> items = new ArrayList<>();
        boolean hasMore;
        int nextOffset = -1;
    }

    /** 预约列表：data.subscribe_items[]，条目抽取照旧 asSeriesItem 的键名集；解析不出返回空表（消费侧按无增量处理）。 */
    private static List<Entry> parseSubscribeList(String text) {
        List<Entry> out = new ArrayList<>();
        JsonObject data = dataOf(text);
        if (data == null) return out;
        JsonArray items = arr(data, "subscribe_items");
        if (items == null) items = arr(data, "subscribeItems");
        if (items == null) return out;
        for (JsonElement el : items) {
            Entry e = asEntry(el);
            if (e == null && el != null && el.isJsonObject()) {
                for (Map.Entry<String, JsonElement> en : el.getAsJsonObject().entrySet()) {
                    e = asEntry(en.getValue());
                    if (e != null) break;
                }
            }
            if (e != null) out.add(e);
        }
        return out;
    }

    /** 通用条目抽取（旧 asSeriesItem 键名集的子集：id/标题/封面/集数）。 */
    private static Entry asEntry(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        JsonObject o = el.getAsJsonObject();
        JsonObject albumInfo = obj(o, "album_info");
        String id = firstStr(o, "series_id", "book_id", "seriesId");
        if (id == null) id = longStr(o, "item_id");
        if (id == null && albumInfo != null) id = firstStr(albumInfo, "album_id_str", "album_id");
        String title = firstStr(o, "title", "series_name", "name", "book_name");
        if (title == null && albumInfo != null) title = str(albumInfo, "title");
        if (id == null || title == null) return null;
        String cover = firstStr(o, "cover", "poster_url", "thumb_url", "cover_url", "image_url");
        int count = intOf(o, "episode_count", -1);
        if (count < 0) count = intOf(o, "episode_cnt", -1);
        if (count < 0 && albumInfo != null) count = intOf(albumInfo, "episode_cnt", -1);
        return new Entry(id, title, cover, count);
    }

    /** 历史一页（旧 parseReadHistory 逐字段：is_delete 跳过、无剧名跳过）。 */
    private static HistoryPage parseReadHistory(String text) {
        JsonObject data = dataOf(text);
        if (data == null) return null;
        HistoryPage page = new HistoryPage();
        JsonArray rows = arr(data, "data_list");
        if (rows != null) {
            for (JsonElement el : rows) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                if (boolOf(o, "is_delete")) continue;
                String seriesId = firstStr(o, "book_id_str");
                if (seriesId == null) {
                    long bid = longOf(o, "book_id", 0);
                    if (bid > 0) seriesId = String.valueOf(bid);
                }
                String title = str(o, "book_name");
                if (seriesId == null || title == null || title.isEmpty()) continue;
                HistoryEntry e = new HistoryEntry();
                e.seriesId = seriesId;
                e.title = title;
                e.cover = str(o, "thumb_url");
                e.episodeCount = intOf(o, "episode_cnt", -1);
                e.episodeIndex = intOf(o, "vid_index", 0);
                e.positionMs = longOf(o, "current_play_position", 0);
                e.readTsMs = longOf(o, "read_timestamp_ms", 0);
                page.items.add(e);
            }
        }
        page.hasMore = boolOf(data, "has_more");
        int next = intOf(data, "next_offset", -1);
        page.nextOffset = page.hasMore && next >= 0 ? next : -1;
        return page;
    }

    /** 点赞一页（旧 parseMyLikes 抽取：mixed_data_list→video_data→series_id_str 等）。 */
    private static LikesPage parseMyLikes(String text) {
        JsonObject data = dataOf(text);
        if (data == null) return null;
        LikesPage page = new LikesPage();
        JsonArray rows = arr(data, "mixed_data_list");
        if (rows != null) {
            for (JsonElement el : rows) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                JsonObject vd = obj(o, "video_data");
                if (vd == null) {
                    JsonObject info = obj(o, "video_detail_info");
                    if (info != null) vd = obj(info, "video_data");
                    if (vd == null) vd = info;
                }
                if (vd == null) continue;
                JsonObject detail = obj(vd, "video_detail");
                String seriesId = firstStr(vd, "series_id_str", "series_id");
                if (seriesId == null && detail != null) {
                    seriesId = firstStr(detail, "series_id_str", "series_id");
                }
                if (seriesId == null) continue;
                String title = detail == null ? null : str(detail, "series_title");
                if (title == null || title.isEmpty()) title = str(vd, "title");
                String cover = detail == null ? null : str(detail, "series_cover");
                if (cover == null || cover.isEmpty()) {
                    cover = firstStr(vd, "cover", "horiz_cover", "series_cover");
                }
                int count = detail == null ? 0 : intOf(detail, "episode_cnt", 0);
                if (count <= 0) count = intOf(vd, "episode_cnt", 0);
                page.items.add(new Entry(seriesId, title, cover, count));
            }
        }
        page.hasMore = boolOf(data, "has_more");
        int next = intOf(data, "next_offset", -1);
        page.nextOffset = page.hasMore && next >= 0 ? next : -1;
        return page;
    }

    // ---------- 传输（自家面签名＋会话头；同步版，调用方都在 WORKER 线程） ----------

    private interface Starter {
        void start(HgCallback<String> cb);
    }

    private static String sync(Starter starter) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] body = new String[1];
        final String[] error = new String[1];
        starter.start(new HgCallback<String>() {
            @Override
            public void onSuccess(String text) {
                body[0] = text;
                latch.countDown();
            }

            @Override
            public void onError(int code, String msg) {
                error[0] = msg == null ? ("请求失败(" + code + ")") : msg;
                latch.countDown();
            }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) throw new Exception("请求超时");
        if (error[0] != null) throw new Exception(error[0]);
        return body[0] == null ? "" : body[0];
    }

    private static String accountGetSync(String path, List<String[]> bizPairs) throws Exception {
        return sync(cb -> accountGet(path, bizPairs, cb));
    }

    private static String accountPostSync(String path, JsonObject bodyObj) throws Exception {
        return sync(cb -> accountPost(path, bodyObj, cb));
    }

    private static void accountGet(String path, List<String[]> bizPairs, HgCallback<String> cb) {
        HgAccountStore.Session session = HgAccountStore.load();
        if (session == null || !HgAccountStore.isLoggedIn()) {
            cb.onError(-3, "未登录");
            return;
        }
        HgApiClient.getInstance().ensureDevice(new HgCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean ok) {
                List<String[]> pairs = new ArrayList<>(HgDevice.deviceQueryPairs());
                pairs.addAll(bizPairs);
                long ticket = System.currentTimeMillis();
                HgSign.Signed signed = HgSign.signGet(path, pairs, ticket,
                        HgDevice.signDeviceId(), HgDevice.VERSION_NAME, null);
                String url = HgApiClient.HOST + path + "?" + signed.query;
                Map<String, String> h = sessionHeaders(session);
                h.putAll(signed.headers);
                h = HgNativeSign.resign(url, h, null);
                Call call = OkHttp.newCall(url, h);
                enqueue(call, cb, path);
            }

            @Override
            public void onError(int code, String msg) {
                cb.onError(code, msg);
            }
        });
    }

    private static void accountPost(String path, JsonObject bodyObj, HgCallback<String> cb) {
        HgAccountStore.Session session = HgAccountStore.load();
        if (session == null || !HgAccountStore.isLoggedIn()) {
            cb.onError(-3, "未登录");
            return;
        }
        HgApiClient.getInstance().ensureDevice(new HgCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean ok) {
                String body = bodyObj.toString();
                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                long ticket = System.currentTimeMillis();
                HgSign.Signed signed = HgSign.signPost(path,
                        HgDevice.deviceQueryPairs(), ticket,
                        HgDevice.signDeviceId(), HgDevice.VERSION_NAME, bodyBytes, null);
                String url = HgApiClient.HOST + path + "?" + signed.query;
                Map<String, String> h = sessionHeaders(session);
                h.putAll(signed.headers);
                h.remove("Content-Type");
                h = HgNativeSign.resign(url, h, bodyBytes);
                Call call = OkHttp.newCall(url, h, RequestBody.create(body, JSON));
                enqueue(call, cb, path);
            }

            @Override
            public void onError(int code, String msg) {
                cb.onError(code, msg);
            }
        });
    }

    /** 会话头：UA 用设备面、Cookie/令牌出自登录落盘（assistant account.rs 的 session_headers 同族）。 */
    private static Map<String, String> sessionHeaders(HgAccountStore.Session session) {
        Map<String, String> h = new HashMap<>();
        h.put("user-agent", HgDevice.userAgent());
        if (session.cookie != null && !session.cookie.isEmpty()) h.put("Cookie", session.cookie);
        if (session.ttToken != null && !session.ttToken.isEmpty()) h.put("x-tt-token", session.ttToken);
        if (session.csrf != null && !session.csrf.isEmpty()) {
            h.put("x-tt-passport-csrf-token", session.csrf);
        }
        h.put("x-ss-dp", "8662");
        return h;
    }

    private static void enqueue(Call call, HgCallback<String> cb, String path) {
        if (call == null) {
            cb.onError(-1, "无法创建请求");
            return;
        }
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call c, IOException e) {
                android.util.Log.w(TAG, path + " 网络失败 " + e.getMessage());
                cb.onError(-1, e.getMessage());
            }

            @Override
            public void onResponse(Call c, Response response) {
                try (Response r = response) {
                    ResponseBody rb = r.body();
                    String text = rb == null ? "" : rb.string();
                    if (!r.isSuccessful()) cb.onError(r.code(), "HTTP " + r.code());
                    else if (text.isEmpty()) cb.onError(-2, "回包空体");
                    else cb.onSuccess(text);
                } catch (Exception e) {
                    cb.onError(-1, e.getMessage());
                }
            }
        });
    }

    // ---------- Gson 容忍读取小件（旧 HgParser 同名口径） ----------

    private static JsonObject dataOf(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            JsonElement el = JsonParser.parseString(text);
            if (el == null || !el.isJsonObject()) return null;
            JsonObject root = el.getAsJsonObject();
            JsonObject data = obj(root, "data");
            return data != null ? data : root;
        } catch (Exception e) {
            return null;
        }
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
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static String firstStr(JsonObject o, String... keys) {
        for (String k : keys) {
            String v = str(o, k);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static String longStr(JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return null;
        try {
            long v = e.getAsJsonPrimitive().isNumber() ? e.getAsLong()
                    : Long.parseLong(e.getAsString().trim());
            return v > 0 ? String.valueOf(v) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static int intOf(JsonObject o, String key, int def) {
        if (o == null) return def;
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;
        try {
            return e.getAsJsonPrimitive().isNumber() ? e.getAsInt()
                    : Integer.parseInt(e.getAsString().trim());
        } catch (Exception ex) {
            return def;
        }
    }

    private static long longOf(JsonObject o, String key, long def) {
        if (o == null) return def;
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;
        try {
            return e.getAsJsonPrimitive().isNumber() ? e.getAsLong()
                    : Long.parseLong(e.getAsString().trim());
        } catch (Exception ex) {
            return def;
        }
    }

    private static boolean boolOf(JsonObject o, String key) {
        if (o == null) return false;
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return false;
        try {
            return e.getAsBoolean();
        } catch (Exception ex) {
            return false;
        }
    }
}
