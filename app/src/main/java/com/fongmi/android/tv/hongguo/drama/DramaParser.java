package com.fongmi.android.tv.hongguo.drama;

import com.fongmi.android.tv.hongguo.model.HgContinuation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 红果回包解析（纯 Gson，无 Android 依赖，可在纯 JVM 单测）。
 *
 * <p>吃两种已实证形态：</p>
 * <ul>
 *   <li>multi_video_model 回包：data 是 {集id: {video_model: "字符串化JSON", ...}}，
 *       内层 video_list 是数组，档位字段在 video_meta、加密字段在 encrypt_info。</li>
 *   <li>fplay 回包：video_info.data 即 video_model，video_list 是 {video_N: {...}} 对象表，
 *       字段扁平，且带 key_seed / poster_url。</li>
 * </ul>
 * <p>详情与 cell 类回包字段口径未在样本里定版，解析走容忍抽取并保留 raw。</p>
 */
public final class DramaParser {

    private DramaParser() {
    }

    // ---------- 播放模型 ----------

    /** multi_video_model 回包 → 以请求集 id 为键的模型表（保持回包顺序）。 */
    public static Map<String, DramaVideoModel> parseMultiVideoModel(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, DramaVideoModel> out = new LinkedHashMap<>();
        JsonObject data = obj(root, "data");
        if (data == null) return out;
        for (Map.Entry<String, JsonElement> e : data.entrySet()) {
            JsonObject wrap = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : null;
            if (wrap == null) continue;
            JsonElement vmEl = wrap.get("video_model");
            JsonObject vm = asObject(vmEl);
            if (vm == null) continue;
            int vw = intOf(wrap, "video_width", 0);
            int vh = intOf(wrap, "video_height", 0);
            out.put(e.getKey(), buildModel(e.getKey(), vm, vw, vh));
        }
        return out;
    }

    /**
     * 单集 video_model 回包（Outer=3 口径，海阔 hgApiOnce 同形态）→ 单集模型：
     * data.video_model 是字符串化 JSON（也容忍对象形态），宽高在 data 一层。
     * 无 data / 无 video_model 返回 null（hgApiOnce 同判据）。
     */
    public static DramaVideoModel parseSingleVideoModel(String json, String requestedId) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject data = obj(root, "data");
        if (data == null) return null;
        JsonObject vm = asObject(data.get("video_model"));
        if (vm == null) return null;
        int vw = intOf(data, "video_width", 0);
        int vh = intOf(data, "video_height", 0);
        return buildModel(requestedId, vm, vw, vh);
    }

    /**
     * 回包顶层业务码（结构化拒绝，如 UGC vid 的 101000「服务异常」）；
     * 空体、无 code 字段、解析不了都返回 0——与限流空体区分开，供
     * 取链层判定「是该条目被口径拒绝、不是档案被限流」。code 数字形
     * 与字符串形都认（intOf 两形兼容，与 isOkCode 同口径）。
     */
    public static int businessCodeOf(String json) {
        if (json == null || json.isEmpty()) return 0;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root == null) return 0;
            int code = intOf(root, "code", 0);
            if (code != 0) return code;
            // 模型口的拒绝回包是 BaseResp 信封形（探针实录 125B，
            // hongguo-shared-profile-20261030.md 第二节）：顶层 "Code"
            // ＋BaseResp.StatusCode（如 110001 身份层拒绝）。只认小写
            // code 会把它读成 0、与限流空体同形——取模型兜底状态机
            // 靠本函数分「身份被拒」与「额度空体」，故补认信封两键。
            code = intOf(root, "Code", 0);
            if (code != 0) return code;
            JsonObject base = obj(root, "BaseResp");
            return base == null ? 0 : intOf(base, "StatusCode", 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 回包顶层业务消息（message/msg/BaseResp.StatusMessage，缺失回空串）。 */
    public static String businessMessageOf(String json) {
        if (json == null || json.isEmpty()) return "";
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root == null) return "";
            String m = firstStr(root, "message", "msg");
            if (m != null) return m;
            JsonObject base = obj(root, "BaseResp");
            m = base == null ? null : firstStr(base, "StatusMessage", "status_message");
            return m == null ? "" : m;
        } catch (Exception e) {
            return "";
        }
    }

    /** fplay 回包（fallback_api 的返回）→ 单集模型。 */
    public static DramaVideoModel parseFplay(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject vi = obj(root, "video_info");
        JsonObject data = vi == null ? null : obj(vi, "data");
        if (data == null) return null;
        return buildModel(null, data, 0, 0);
    }

    /** 单个 video_model 对象（已解开字符串化）→ 模型。 */
    public static DramaVideoModel parseVideoModel(JsonObject vm, String requestedId) {
        return buildModel(requestedId, vm, 0, 0);
    }

    /**
     * UGC 随详情下发的 video_model 原串（字符串化 JSON）＋详情层宽高
     * → 播放模型；串坏/无档返回 null（调用方回落常规取链）。
     */
    public static DramaVideoModel parseUgcVideoModel(String modelJson, String requestedId,
                                                  int videoWidth, int videoHeight) {
        if (modelJson == null || modelJson.isEmpty()) return null;
        JsonElement el;
        try {
            el = JsonParser.parseString(modelJson);
        } catch (Exception e) {
            return null;
        }
        JsonObject vm = asObject(el);
        if (vm == null) return null;
        DramaVideoModel m = buildModel(requestedId, vm, videoWidth, videoHeight);
        return m.getEntries().isEmpty() ? null : m;
    }

    private static DramaVideoModel buildModel(String requestedId, JsonObject vm, int vw, int vh) {
        List<DramaEntry> entries = new ArrayList<>();
        JsonElement listEl = vm.get("video_list");
        if (listEl != null && listEl.isJsonArray()) {
            for (JsonElement el : listEl.getAsJsonArray()) {
                if (el.isJsonObject()) entries.add(buildEntryArrayForm(el.getAsJsonObject()));
            }
        } else if (listEl != null && listEl.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : listEl.getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonObject()) entries.add(buildEntryFlatForm(e.getValue().getAsJsonObject()));
            }
        }
        String fallback = null;
        JsonObject fb = obj(vm, "fallback_api");
        if (fb != null) fallback = str(fb, "fallback_api");
        return new DramaVideoModel(
                requestedId,
                str(vm, "video_id"),
                intOf(vm, "status", 0),
                str(vm, "message"),
                doubleOf(vm, "video_duration", 0),
                str(vm, "media_type"),
                longOf(vm, "url_expire", 0),
                str(vm, "poster_url"),
                str(vm, "key_seed"),
                fallback,
                vw, vh,
                longOf(vm, "digged_count", -1),
                entries,
                vm.toString());
    }

    /**
     * 落盘缓存读回：video_model 原始 JSON 串＋外层宽高 → 模型，与当初
     * buildModel 同路重建（字段口径零漂移）；串坏/为空返回 null。
     */
    public static DramaVideoModel restoreModel(String requestedId, String modelJson, int vw, int vh) {
        if (modelJson == null || modelJson.isEmpty()) return null;
        try {
            JsonObject vm = asObject(JsonParser.parseString(modelJson));
            return vm == null ? null : buildModel(requestedId, vm, vw, vh);
        } catch (Exception e) {
            return null;
        }
    }

    /** multi 形态：字段在 video_meta / encrypt_info 子对象里（子对象也可能是字符串化 JSON）。 */
    private static DramaEntry buildEntryArrayForm(JsonObject item) {
        JsonObject meta = asObject(item.get("video_meta"));
        if (meta == null) meta = new JsonObject();
        JsonObject enc = asObject(item.get("encrypt_info"));
        if (enc == null) enc = new JsonObject();
        String backup = str(item, "backup_url");
        if (backup == null) backup = str(item, "backup_url_1");
        return new DramaEntry(
                str(meta, "definition"), str(meta, "quality"), str(meta, "vtype"),
                intOf(meta, "vwidth", 0), intOf(meta, "vheight", 0),
                longOf(meta, "bitrate", 0), longOf(meta, "real_bitrate", 0),
                doubleOf(meta, "fps", 0), str(meta, "codec_type"),
                longOf(meta, "size", 0), str(meta, "file_id"), str(meta, "file_hash"),
                str(item, "main_url"), backup,
                str(enc, "spade_a"), str(enc, "kid"), str(enc, "encryption_method"),
                str(item, "gear_des_key"));
    }

    /** fplay 形态：字段扁平在项上。 */
    private static DramaEntry buildEntryFlatForm(JsonObject item) {
        String backup = str(item, "backup_url_1");
        if (backup == null) backup = str(item, "backup_url");
        return new DramaEntry(
                str(item, "definition"), str(item, "quality"), str(item, "vtype"),
                intFlexible(item, "vwidth"), intFlexible(item, "vheight"),
                longFlexible(item, "bitrate"), longFlexible(item, "real_bitrate"),
                doubleFlexible(item, "fps"), str(item, "codec_type"),
                longFlexible(item, "size"), str(item, "file_id"), str(item, "file_hash"),
                str(item, "main_url"), backup,
                str(item, "spade_a"), str(item, "kid"), str(item, "encryption_method"),
                str(item, "gear_des_key"));
    }

    // ---------- 详情（容忍抽取，字段口径未定版） ----------

    public static DramaDetail parseVideoDetail(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject data = obj(root, "data");
        JsonObject d = data != null ? data : root;
        // data 下可能再套一层以剧 id 为键的表
        if (str(d, "series_id") == null && str(d, "book_id") == null && str(d, "title") == null
                && str(d, "series_name") == null && !d.entrySet().isEmpty()) {
            for (Map.Entry<String, JsonElement> e : d.entrySet()) {
                if (e.getValue().isJsonObject()) {
                    d = e.getValue().getAsJsonObject();
                    break;
                }
            }
        }
        List<DramaDetail.Episode> episodes = new ArrayList<>();
        JsonArray eps = arr(d, "episode_list");
        if (eps == null) eps = arr(d, "episodes");
        if (eps == null) eps = arr(d, "video_list");
        if (eps != null) {
            int i = 0;
            for (JsonElement el : eps) {
                if (!el.isJsonObject()) continue;
                JsonObject eo = el.getAsJsonObject();
                String vid = firstStr(eo, "video_id", "vid", "id");
                if (vid == null) continue;
                int idx = intOf(eo, "index", -1);
                if (idx < 0) idx = intOf(eo, "episode_index", -1);
                if (idx < 0) idx = intOf(eo, "vid_index", i + 1);
                episodes.add(new DramaDetail.Episode(vid, idx,
                        longOf(eo, "digged_count", -1)));
                i++;
            }
        }
        String seriesId = firstStr(d, "series_id_str", "series_id", "book_id", "id");
        String title = firstStr(d, "series_title", "title", "series_name", "name");
        String cover = firstStr(d, "series_cover", "cover", "poster_url", "thumb_url", "cover_url");
        String desc = firstStr(d, "series_intro", "desc", "description", "intro", "abstract");
        String author = firstStr(d, "author_name", "author");
        int count = intOf(d, "episode_count", -1);
        if (count < 0) count = intOf(d, "episode_cnt", episodes.isEmpty() ? -1 : episodes.size());
        JsonObject appt = obj(d, "appointment");
        // 演员表与完结态（详情真回包 data.video_data 内：celebrities[] 与
        // series_status，openhongguo-probe resp-002/003 逐字实证；官方模型
        // SaasVideoDetailModel.getCelebrityList()/episodesStatus 的下发形态）
        List<DramaDetail.Celebrity> celebrities = new ArrayList<>();
        JsonArray celebs = arr(d, "celebrities");
        if (celebs != null) {
            for (JsonElement el : celebs) {
                if (!el.isJsonObject()) continue;
                JsonObject co = el.getAsJsonObject();
                String nick = firstStr(co, "nickname", "name");
                String role = str(co, "role_name");
                if (nick == null && role == null) continue;
                celebrities.add(new DramaDetail.Celebrity(
                        nick, role, str(co, "avatar"), str(co, "sub_title")));
            }
        }
        // 详情页增量（video_data 真回包逐字，e1_detail 存档实证）：热度原值、
        // 收藏数、剧主色、分类标签名。（服务端收藏态 followed／副标题段
        // sub_title_list／声明／多季／专辑 id 曾在此抽取装入详情模型，
        // 全树零消费，已随簇十五 P-05 摘除，不再抽取。）
        List<String> tags = new ArrayList<>();
        String schema = str(d, "category_schema");
        if (schema != null && !schema.isEmpty()) {
            try {
                JsonElement parsed = JsonParser.parseString(schema);
                if (parsed.isJsonArray()) {
                    for (JsonElement el : parsed.getAsJsonArray()) {
                        if (el.isJsonObject()) {
                            String n = str(el.getAsJsonObject(), "name");
                            if (n != null && !n.isEmpty()) tags.add(n);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (tags.isEmpty()) {
            for (String key : new String[]{"abstract_tags", "rec_tags"}) {
                JsonArray ta = arr(d, key);
                if (ta == null) continue;
                for (JsonElement el : ta) {
                    String n = null;
                    if (el.isJsonObject()) n = str(el.getAsJsonObject(), "name");
                    else if (el.isJsonPrimitive()) {
                        try {
                            n = el.getAsString();
                        } catch (Exception ignored) {
                        }
                    }
                    if (n != null && !n.isEmpty()) tags.add(n);
                }
                if (!tags.isEmpty()) break;
            }
        }
        // UGC 单条视频（动态 tab 的 video 条目）：详情的 video_data 是
        // UgcVideoDetail 形——没有 video_list，视频本体由 video_id＋
        // video_model 随详情下发（seriessdk UgcVideoDetail 定版；ohg 详情
        // 口实证该 vid 可当 1 集详情查、playable）。集表按其 video_id 成
        // 唯一一集，模型原串挂到 detail 上，会话层展开时灌进模型缓存，
        // 不走单集取模型口（那口对 UGC vid 实拉回 101000）。
        String ugcModel = str(d, "video_model");
        boolean ugcShape = ugcModel != null && !ugcModel.isEmpty();
        if (ugcShape && episodes.isEmpty()) {
            String ugcVid = firstStr(d, "video_id", "vid");
            if (ugcVid != null) {
                episodes.add(new DramaDetail.Episode(ugcVid, 1));
                if (count < 0) count = 1;
            } else {
                ugcShape = false;
            }
        }
        String colorHex = str(d, "series_color_hex");
        DramaDetail detail = new DramaDetail(seriesId, title, cover, desc, author, count, episodes, root,
                appt != null, appt != null && boolOf(appt, "is_subscribed"),
                appt == null ? null : str(appt, "due_start_text"),
                celebrities, intOrNull(d, "series_status"),
                longOf(d, "hot_score", -1),
                longOf(d, "followed_cnt", -1),
                colorHex == null || colorHex.isEmpty() ? null : colorHex,
                tags);
        // 内容类型与 series_status 同在 video_data 本体（官方
        // VideoDetailInfo 两键并列，抠查账 manju-follow-dig 件 1）：
        // 构造器没带此参，照 ugcVideoModel 的 attach 惯例回填
        detail.attachContentType(intOf(d, "content_type", -1));
        if (ugcShape) {
            detail.attachUgcModel(ugcModel, intOf(d, "video_width", 0), intOf(d, "video_height", 0));
        }
        // 作者 uid 抬升（与 cell 面 ugc_user_info 同口径：顶层 user_id、
        // base_info.user_id 兜底；author 对象形也认）。2026-10-29 实拉
        // 定根（author-page-why-missing-20261029 追补）：单剧/批量详情对
        // 剧集内容恒不下发，此处现行恒空转——口先建对，UGC 形详情或回包
        // 形状变化时即刻生效。（旧注称「正剧实际来源走会话层搜索反查
        // 兜底」——该反查从未实现，P-03 已摘链，见簇二账。）
        JsonObject dUgc = obj(d, "ugc_user_info");
        String dUid = null;
        if (dUgc != null) {
            dUid = str(dUgc, "user_id");
            if (dUid == null) {
                JsonObject dBase = obj(dUgc, "base_info");
                if (dBase != null) dUid = str(dBase, "user_id");
            }
        }
        if (dUid == null) {
            JsonObject dAuthor = obj(d, "author");
            if (dAuthor != null) dUid = firstStr(dAuthor, "user_id", "uid");
        }
        if (dUid != null) detail.attachAuthorUid(dUid);
        return detail;
    }

    /**
     * 专辑详情回包 → 各季条目（照 openhongguo ParseAlbum 的线上形状）：
     * data.album_data{album_id_str/album_title/…/series_list|video_list[]}，
     * 每季 series_id_str/series_title/season_index/series_cover/episode_cnt/
     * series_status。UGC 合集回包同口不同形：名单在 video_detail_list
     * （card 形，同键集）。code 非 0（如 101001 已下架）返回空表，调用方直进剧。
     */
    public static List<DramaItem> parseAlbum(String json) {
        DramaAlbumDetail detail = parseAlbumDetail(json);
        return detail == null ? new ArrayList<>() : new ArrayList<>(detail.rows);
    }

    /**
     * 详情页相关作品（plan 口 from＝detail_page_more_related 回包）：
     * data[] 里找 show_type＝547 的 cell（官方 ShowType.
     * DetailPageRelatedWorks、cell_name＝「相关作品」），其 video_data
     * 就是与本剧同 IP 绑定的作品表（其它季在前、衍生/漫剧形态在后，
     * 本剧自身不含在内；无绑定作品时整 cell 不下发）。字段口径照
     * 2026-10-30 实拉三部剧定版（账 hongguo-related-20261030）：
     * 角标与副行优先取条目顶层 tag_info/sub_title_list，缺时回落
     * recommend_reason_list（对象形/字符串形都认）里的同名段。
     * book_data（同 IP 书形态）不收。回包坏/code 非 0/无 547 cell
     * 一律空表（调用方按无相关作品处理，整段不显）。
     */
    public static List<DramaDetail.RelatedWork> parseRelatedWorks(String json) {
        List<DramaDetail.RelatedWork> out = new ArrayList<>();
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return out;
        }
        if (intOf(root, "code", 0) != 0) return out;
        JsonArray data = arr(root, "data");
        if (data == null) return out;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (JsonElement el : data) {
            if (!el.isJsonObject()) continue;
            JsonObject cell = el.getAsJsonObject();
            if (intOf(cell, "show_type", -1) != 547) continue;
            JsonArray videos = arr(cell, "video_data");
            if (videos == null) continue;
            for (JsonElement ve : videos) {
                if (!ve.isJsonObject()) continue;
                JsonObject o = ve.getAsJsonObject();
                String sid = firstStr(o, "series_id_str", "series_id");
                if (sid == null || !seen.add(sid)) continue;
                JsonObject reason = asObject(o.get("recommend_reason_list"));
                JsonObject tagInfo = obj(o, "tag_info");
                if (tagInfo == null && reason != null) tagInfo = obj(reason, "tag_info");
                List<String> subs = subTitleList(o);
                if (subs.isEmpty() && reason != null) subs = subTitleList(reason);
                String subText = subs.isEmpty() ? null
                        : String.join(" · ", subs.subList(0, Math.min(2, subs.size())));
                out.add(new DramaDetail.RelatedWork(sid,
                        firstStr(o, "title", "series_title"),
                        firstStr(o, "cover", "series_cover"),
                        intOf(o, "episode_cnt", 0),
                        tagInfo == null ? null : str(tagInfo, "text"),
                        subText));
            }
        }
        return out;
    }

    /**
     * 专辑/合集详情回包 → 头部＋集列表（批四卡 8 落点）：头部字段只收
     * album_data 实有键（见 {@link DramaAlbumDetail} 类注），集列表行与
     * parseAlbum 旧抽取同一份（series_list／video_detail_list／video_list
     * 三形、按 seasonIndex 排序）。回包坏/无 album_data 返回 null；
     * code 非 0 返回头部为空、行为空表的实例（与旧 parseAlbum 空表口径
     * 一致，调用方按空名单处理）。
     */
    public static DramaAlbumDetail parseAlbumDetail(String json) {
        List<DramaItem> out = new ArrayList<>();
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
        String code = str(root, "code");
        if (code != null && !"0".equals(code)) {
            return new DramaAlbumDetail(null, null, null, null, -1, null, null, -1, -1,
                    null, null, null, null, null, false, -1, out);
        }
        JsonObject data = obj(root, "data");
        if (data == null) return null;
        JsonObject album = obj(data, "album_data");
        if (album == null) return null;
        // 头部（官方 VideoAlbumHeaderLayout 字段位）：封面/标题/副标题段/
        // 作者行/简介；计数与热度文案只收回包实有键（见 DramaAlbumDetail 注）
        JsonObject ugcUser = obj(album, "ugc_user_info");
        JsonObject ugcBase = ugcUser == null ? null : obj(ugcUser, "base_info");
        JsonObject ugcRel = ugcUser == null ? null : obj(ugcUser, "user_relation");
        String hdrAuthorUid = ugcUser == null ? null : str(ugcUser, "user_id");
        if (hdrAuthorUid == null && ugcBase != null) hdrAuthorUid = str(ugcBase, "user_id");
        JsonArray rows = arr(album, "series_list");
        // UGC 合集（搜索合集条点开的 album_detail 真回包 2026-10-29 实拉）：
        // 名单在 album_data.video_detail_list，每行 card 形
        // （series_id_str/series_title/series_cover），与多季的 series_list
        // 同位不同名，不补此路合集点开恒空表。
        if (rows == null) rows = arr(album, "video_detail_list");
        if (rows == null) rows = arr(album, "video_list");
        List<DramaItem> seasons = new ArrayList<>();
        if (rows != null) {
        for (JsonElement el : rows) {
            if (!el.isJsonObject()) continue;
            JsonObject r = el.getAsJsonObject();
            String sid = firstStr(r, "series_id_str", "series_id");
            if (sid == null) continue;
            // 行内 vid 必须抽（#10）：此前恒传 null，合集页逐行起播
            // 无从取链、入口 vid 也对不上落点。抽取顺序照条目归一
            // 的同序：行顶层 → video_detail 子对象 → video_data 首元。
            String rowVid = firstStr(r, "vid", "video_id");
            if (rowVid == null) {
                JsonObject rvd = obj(r, "video_detail");
                if (rvd != null) rowVid = firstStr(rvd, "vid", "video_id");
            }
            if (rowVid == null) {
                JsonArray vdata = arr(r, "video_data");
                if (vdata != null && vdata.size() > 0 && vdata.get(0).isJsonObject()) {
                    rowVid = firstStr(vdata.get(0).getAsJsonObject(), "vid", "video_id");
                }
            }
            DramaItem rowItem = new DramaItem(sid, str(r, "series_title"), str(r, "series_cover"),
                    null, intOf(r, "episode_cnt", -1), false, false, null,
                    rowVid, null, null, subTitleList(r), null, false,
                    firstStr(album, "album_id_str", "album_id"), intOf(album, "season_cnt", -1),
                    Collections.emptyList(), -1, -1, intOrNull(r, "series_status"), null, null, null,
                    null, null, null);
            // 行的类型码（取链按它分口，缺了 fetchModelForPlay 拿
            // -1 恒取空）：行自带优先、合集本体兜底，都没有保持 -1。
            int rowType = intOf(r, "content_type", -1);
            if (rowType < 0) rowType = intOf(album, "content_type", -1);
            if (rowType > 0) rowItem.contentType = rowType;
            seasons.add(rowItem);
        }
        final JsonArray seasonRows = rows;
        if (seasonRows != null) {
            seasons.sort((a, b) -> Integer.compare(seasonIndex(seasonRows, a.getSeriesId()),
                    seasonIndex(seasonRows, b.getSeriesId())));
        }
        }
        return new DramaAlbumDetail(
                firstStr(album, "album_id_str", "album_id"),
                str(album, "title"), str(album, "cover"), str(album, "intro"),
                intOf(album, "episode_cnt", -1), subTitleList(album),
                str(album, "hot_score_text"),
                longOf(album, "followed_cnt", -1), longOf(album, "play_cnt", -1),
                intOrNull(album, "series_status"), str(album, "color_hex"),
                hdrAuthorUid,
                ugcBase == null ? null : str(ugcBase, "user_name"),
                ugcBase == null ? null : str(ugcBase, "user_avatar"),
                ugcRel != null && boolOf(ugcRel, "can_follow"),
                ugcRel == null ? -1 : intOf(ugcRel, "relation_type", -1),
                seasons);
    }

    /**
     * 季行副标题表（U5 定版，u7-u5-dig-20261028.md）：官方系列单行的副行
     * 就是 sub_title_list 前两条，没有独立的集数/热度字段位。REST 侧键名
     * 两形并存（video_detail 是 series_sub_title_list、搜索 subscribe 行是
     * sub_title_list），元素两形并存（对象 {content}／纯字符串），全容忍抽。
     * 专辑回包本体尚无真样本，键名集待真样本逐字校——现按已实证字段族接。
     */
    private static List<String> subTitleList(JsonObject r) {
        List<String> out = new ArrayList<>();
        JsonArray a = arr(r, "series_sub_title_list");
        if (a == null) a = arr(r, "sub_title_list");
        if (a == null) return out;
        for (JsonElement el : a) {
            String v = null;
            if (el.isJsonObject()) v = str(el.getAsJsonObject(), "content");
            else if (el.isJsonPrimitive()) {
                try {
                    v = el.getAsString();
                } catch (Exception ignored) {
                }
            }
            if (v != null && !v.isEmpty()) out.add(v);
        }
        return out;
    }

    private static int seasonIndex(JsonArray rows, String seriesId) {
        for (JsonElement el : rows) {
            if (!el.isJsonObject()) continue;
            JsonObject r = el.getAsJsonObject();
            if (seriesId.equals(firstStr(r, "series_id_str", "series_id"))) {
                int idx = intOf(r, "season_index", -1);
                return idx < 0 ? Integer.MAX_VALUE : idx;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * 搜索联想回包（openhongguo ParseSuggest 线上形状）：先取
     * data.query_result_v2、再 suggest_result（行名 name/keyword/word_type），
     * 都没有时回落 data.query_result 纯词表。code 非 0 回空表。
     */
    public static java.util.List<DramaSuggestion> parseSuggest(String json) {
        java.util.List<DramaSuggestion> out = new java.util.ArrayList<>();
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return out;
        }
        String code = str(root, "code");
        if (code != null && !"0".equals(code)) return out;
        JsonObject data = obj(root, "data");
        if (data == null) return out;
        for (String key : new String[]{"query_result_v2", "suggest_result"}) {
            JsonArray rows = arr(data, key);
            if (rows == null) continue;
            for (JsonElement el : rows) {
                if (!el.isJsonObject()) continue;
                JsonObject r = el.getAsJsonObject();
                String name = str(r, "name");
                if (name == null || name.isEmpty()) continue;
                out.add(new DramaSuggestion(name, str(r, "keyword"), intOf(r, "word_type", -1)));
            }
            if (!out.isEmpty()) return out;
        }
        JsonArray plain = arr(data, "query_result");
        if (plain != null) {
            for (JsonElement el : plain) {
                if (el.isJsonPrimitive()) {
                    String w = el.getAsString();
                    if (w != null && !w.isEmpty()) out.add(new DramaSuggestion(w, w, -1));
                }
            }
        }
        return out;
    }

    // ---------- 热搜面（plan/v1 search_input_page，openhongguo ParseHotSearch 逐步译） ----------

    /**
     * 热搜面回包 → 猜你想搜行＋热搜榜板（照 openhongguo ParseHotSearch，
     * 账 hongguo-hotsearch-dig-20261030 §2）：顶层 code 非 0/回包坏回
     * 空面；遍历 data[] 取 lynx_data（字符串化的 JSON 数组、内联数组
     * 亦容忍），cells 里自带 video_data 的 cell 进 guesses（段名取该
     * cell 的 cell_name 原文）、带 cell_data 的容器 cell 逐板收
     * items（video_data）／topics（video_topic），空板丢弃。行字段
     * 逐字照 §2.3 收（series_id/title/cover/episode_cnt/content_type/
     * card_tips→searchScore/rec_text→heat/tag_info.text→tag，另收
     * tag_info.bg_color[0] 与 vid）；series_id 为空的行丢弃，position
     * 按各面收录序自算。本机首发探针实证（2026-10-30）：同口两次回包
     * 猜面 cell 一次为 483/video_data 形、一次为 620/search_tag_data
     * 词标形——后者不在 ohg 模型与 fixture 内，本解析按定版只认
     * video_data 形，那次回包即只出榜面（猜面静默不出），如实记账。
     */
    public static DramaHotSearch parseHotSearch(String json) {
        DramaHotSearch empty = new DramaHotSearch(null, null, null);
        if (json == null || json.isEmpty()) return empty;
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return empty;
        }
        // code 容忍判定与 ohg/parseSuggest 同口径：带了且非 0 才算错
        // （str 对数字形 code 同样返回 "0" 文本，两形都认）
        String code = str(root, "code");
        if (code != null && !"0".equals(code)) return empty;
        JsonArray data = arr(root, "data");
        if (data == null) return empty;
        List<DramaHotItem> guesses = new ArrayList<>();
        List<DramaHotBoard> boards = new ArrayList<>();
        String guessName = null;
        for (JsonElement cellEl : data) {
            if (cellEl == null || !cellEl.isJsonObject()) continue;
            JsonArray cells = lynxCells(cellEl.getAsJsonObject().get("lynx_data"));
            if (cells == null) continue;
            for (JsonElement ce : cells) {
                if (ce == null || !ce.isJsonObject()) continue;
                JsonObject cell = ce.getAsJsonObject();
                JsonArray vd = arr(cell, "video_data");
                if (vd != null && vd.size() > 0) {
                    List<DramaHotItem> rows = hotItems(vd);
                    if (!rows.isEmpty()) {
                        if (guessName == null) guessName = str(cell, "cell_name");
                        guesses.addAll(rows);
                    }
                }
                JsonArray boardCells = arr(cell, "cell_data");
                if (boardCells == null) continue;
                for (JsonElement be : boardCells) {
                    if (be == null || !be.isJsonObject()) continue;
                    JsonObject b = be.getAsJsonObject();
                    List<DramaHotItem> items = hotItems(arr(b, "video_data"));
                    List<DramaHotTopic> topics = hotTopics(arr(b, "video_topic"));
                    if (items.isEmpty() && topics.isEmpty()) continue;
                    boards.add(new DramaHotBoard(str(b, "cell_name"), items, topics));
                }
            }
        }
        return new DramaHotSearch(guessName, guesses, boards);
    }

    /** lynx_data 解开：字符串先解串成数组、内联数组直用、对象包一层；解不开返回 null。 */
    private static JsonArray lynxCells(JsonElement el) {
        if (el == null) return null;
        if (el.isJsonArray()) return el.getAsJsonArray();
        if (el.isJsonObject()) {
            JsonArray one = new JsonArray();
            one.add(el);
            return one;
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String s = el.getAsString();
            if (s == null || s.trim().isEmpty()) return null;
            try {
                JsonElement parsed = JsonParser.parseString(s);
                if (parsed != null && parsed.isJsonArray()) return parsed.getAsJsonArray();
                if (parsed != null && parsed.isJsonObject()) {
                    JsonArray one = new JsonArray();
                    one.add(parsed);
                    return one;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** video_data 行表 → 热搜行（series_id 为空丢弃，position 按收录序自算）。 */
    private static List<DramaHotItem> hotItems(JsonArray rows) {
        List<DramaHotItem> out = new ArrayList<>();
        if (rows == null) return out;
        for (JsonElement el : rows) {
            if (el == null || !el.isJsonObject()) continue;
            JsonObject r = el.getAsJsonObject();
            String id = firstStr(r, "series_id");
            if (id == null) id = longStr(r, "series_id");
            if (id == null) continue;
            JsonObject tagInfo = obj(r, "tag_info");
            String tagBg = null;
            if (tagInfo != null) {
                JsonArray bg = arr(tagInfo, "bg_color");
                if (bg != null && bg.size() > 0 && bg.get(0).isJsonPrimitive()) {
                    tagBg = bg.get(0).getAsString();
                }
            }
            out.add(new DramaHotItem(out.size() + 1, id, str(r, "title"), str(r, "cover"),
                    intOf(r, "episode_cnt", -1), intOf(r, "content_type", -1),
                    str(r, "card_tips"), str(r, "rec_text"),
                    tagInfo == null ? null : str(tagInfo, "text"), tagBg,
                    firstStr(r, "vid")));
        }
        return out;
    }

    /** video_topic 行表 → 话题行（heat 取 secondary_info_list[0].content，query 从 url 解 directQueryWord）。 */
    private static List<DramaHotTopic> hotTopics(JsonArray rows) {
        List<DramaHotTopic> out = new ArrayList<>();
        if (rows == null) return out;
        for (JsonElement el : rows) {
            if (el == null || !el.isJsonObject()) continue;
            JsonObject t = el.getAsJsonObject();
            String id = firstStr(t, "topic_id");
            if (id == null) id = longStr(t, "topic_id");
            String title = str(t, "title");
            if (title == null || title.isEmpty()) continue;
            String heat = null;
            JsonArray infos = arr(t, "secondary_info_list");
            if (infos != null && infos.size() > 0 && infos.get(0).isJsonObject()) {
                heat = str(infos.get(0).getAsJsonObject(), "content");
            }
            out.add(new DramaHotTopic(id, title, topicQuery(str(t, "url")), heat));
        }
        return out;
    }

    /** 话题 url 里解 directQueryWord（dragon8662://search?…&directQueryWord=…&…#…），解不出返回 null。 */
    private static String topicQuery(String url) {
        if (url == null) return null;
        String key = "directQueryWord=";
        int at = url.indexOf(key);
        if (at < 0) return null;
        String rest = url.substring(at + key.length());
        int end = rest.length();
        int amp = rest.indexOf('&');
        if (amp >= 0) end = Math.min(end, amp);
        int hash = rest.indexOf('#');
        if (hash >= 0) end = Math.min(end, hash);
        String raw = rest.substring(0, end);
        if (raw.isEmpty()) return null;
        try {
            String decoded = java.net.URLDecoder.decode(raw, "UTF-8");
            return decoded.isEmpty() ? null : decoded;
        } catch (Exception e) {
            return raw;
        }
    }

    /** 数字字段取值：没带或非数字为 null（区别于 intOf 的 -1 哨兵，状态类字段用）。 */
    private static Integer intOrNull(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
        return e.getAsInt();
    }

    /**
     * 批量详情 multi_video_detail 回包 → 以剧 id 为键的详情表
     * （openhongguo ParseSeriesBatch 形态：data 是 {剧id: {video_data: {...}}}）。
     * 每条包回单剧信封复用 {@link #parseVideoDetail} 的容忍抽取。
     */
    public static Map<String, DramaDetail> parseSeriesBatch(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, DramaDetail> out = new LinkedHashMap<>();
        JsonObject data = obj(root, "data");
        if (data == null) return out;
        for (Map.Entry<String, JsonElement> e : data.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject wrap = new JsonObject();
            wrap.add("data", e.getValue());
            DramaDetail detail = parseVideoDetail(wrap.toString());
            if (detail == null) continue;
            String key = detail.getSeriesId() != null ? detail.getSeriesId() : e.getKey();
            out.put(key, detail);
        }
        return out;
    }

    // ---------- cell 类回包（容忍抽取 + 保留 raw） ----------

    public static DramaCellPage parseCellPage(String json) {
        return parseCellPage(json, -1);
    }

    /**
     * 搜索口专用重载（批三 C 卡 18）：tabType&gt;0 且回包带 search_tabs 时
     * 按该签段（search_tabs[].data）定域收录——成员按型分形进
     * {@link DramaCellPage#getSearchRows()}（剧集/视频卡、小说卡、用户卡、
     * 帖卡，保回包原序），items 只留剧/视频子集；签段的 has_more/
     * next_offset 一并抬进页（续拉触发与 offset 全签统一走服务端下发，
     * 不再按 items 条数自算——行表签 items 恒空/偏少的同源病根）。
     * 此前无定域的整树递归收录有三宗丢/压：用户成员（celebrity_user_list）
     * 无剧 id 整条丢弃；帖子（post_data）被 asSeriesItem 以 book_id＝0
     * 冒充剧 id 压成假剧条目（存档 tab11 改前实跑：6 条里第 4 条 id＝"0"）；
     * 小说（book_data）被压成剧条目、点进集流死路。找不到对应签段时回落
     * 整树容忍抽取（与无参版同口径），不让异常回包把结果清零。
     */
    public static DramaCellPage parseCellPage(String json, int tabType) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int code = intOf(root, "code", 0);
        String msg = str(root, "message");
        List<DramaItem> items = new ArrayList<>();
        List<DramaSearchRow> rows = new ArrayList<>();
        JsonObject data = obj(root, "data");
        // 搜索回包的页签面（data.search_tabs）：收六签（11 综合/29 剧集/
        // 30 视频/31 讨论/27 用户，批四补讨论与用户；1 小说，2026-10-30
        // 他亲裁接入），顺序照服务端下发；听书(2)未裁，仍不收不摆位
        List<DramaSearchTab> tabs = new ArrayList<>();
        // search_tabs 的挂载层随回包形变（存档真回包在根级、无 data 包裹）：
        // 与 collectSeries 同口径，data 在用 data、否则用根级
        JsonObject tabScope = data != null && data.has("search_tabs") ? data : root;
        if (tabScope.has("search_tabs") && tabScope.get("search_tabs").isJsonArray()) {
            for (com.google.gson.JsonElement el : tabScope.getAsJsonArray("search_tabs")) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject t = el.getAsJsonObject();
                int type = intOf(t, "tab_type", -1);
                String title = str(t, "title");
                if ((type == 11 || type == 29 || type == 30 || type == 31 || type == 27
                        || type == 1)
                        && title != null && !title.isEmpty()) {
                    tabs.add(new DramaSearchTab(type, title));
                }
            }
        }
        JsonObject seg = tabType > 0 ? findSearchTabSegment(tabScope, tabType) : null;
        boolean hasMore = false;
        String nextOffset = null;
        if (seg != null && arr(seg, "data") != null) {
            collectSearchSegment(seg, items, rows);
            // 续拉口径以服务端下发为准（存档真回包逐签实证，2026-10-30）：
            // 签段自带 has_more/next_offset/passback——next_offset 是服务端
            // 游标，不等于解析条数（用户签回 15~19 行时 next_offset 恒 20；
            // 综合签回 6 cell 时 next_offset=6 而解析 items5+rows6 两头都
            // 对不上；小说签步长 10）。解析层此前把这两字段整段丢掉，
            // 消费侧只能拿 items 条数自算 offset，行表签（讨论/用户/小说）
            // items 恒空或偏少 → 续页永不触发或游标错位。此处抬升入页，
            // 六签共享同一口径。
            hasMore = boolOf(seg, "has_more");
            nextOffset = firstStr(seg, "next_offset");
            if (nextOffset == null) {
                JsonElement no = seg.get("next_offset");
                if (no != null && no.isJsonPrimitive() && no.getAsJsonPrimitive().isNumber()) {
                    nextOffset = String.valueOf(no.getAsLong());
                }
            }
        } else {
            collectSeries(data != null ? data : root, items, 0);
            for (DramaItem it : items) rows.add(DramaSearchRow.ofSeries(it));
        }
        return new DramaCellPage(code, msg, items, root, hasMore, nextOffset, null, null, tabs, rows);
    }

    /** search_tabs 里 tab_type 命中的签段；没有返回 null。 */
    private static JsonObject findSearchTabSegment(JsonObject tabScope, int tabType) {
        if (tabScope == null || !tabScope.has("search_tabs")
                || !tabScope.get("search_tabs").isJsonArray()) return null;
        for (JsonElement el : tabScope.getAsJsonArray("search_tabs")) {
            if (el != null && el.isJsonObject()
                    && intOf(el.getAsJsonObject(), "tab_type", -1) == tabType) {
                return el.getAsJsonObject();
            }
        }
        return null;
    }

    /**
     * 搜索签段定域收录：cell 按回包序遍历，cell 内成员按 JSON 键序分发
     * （服务端原序，客户端不重排、不混排——普查分册二卡 2 定版）。
     * video_data→剧/视频行；book_data→小说行；celebrity_user_list→用户行；
     * post_data→帖行。成员值容忍数组/单对象两形（协议原件 bookList/
     * postList 同口径）。
     */
    private static void collectSearchSegment(JsonObject seg, List<DramaItem> items,
                                             List<DramaSearchRow> rows) {
        JsonArray cells = arr(seg, "data");
        if (cells == null) return;
        for (JsonElement cellEl : cells) {
            if (cellEl == null || !cellEl.isJsonObject()) continue;
            JsonObject cell = cellEl.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : cell.entrySet()) {
                JsonElement v = e.getValue();
                if (v == null) continue;
                switch (e.getKey()) {
                    case "video_data":
                        for (JsonElement el : memberList(v)) collectSearchVideo(el, items, rows);
                        break;
                    case "book_data":
                        for (JsonElement el : memberList(v)) {
                            DramaSearchRow r = searchBookRow(el);
                            if (r != null) rows.add(r);
                        }
                        break;
                    case "celebrity_user_list":
                        for (JsonElement el : memberList(v)) {
                            DramaSearchRow r = searchUserRow(el);
                            if (r != null) rows.add(r);
                        }
                        break;
                    case "post_data":
                        for (JsonElement el : memberList(v)) {
                            DramaSearchRow r = searchPostRow(el);
                            if (r != null) rows.add(r);
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /** 成员值归一为元素表：数组逐个、单对象包一层、其余空表。 */
    private static List<JsonElement> memberList(JsonElement v) {
        List<JsonElement> out = new ArrayList<>();
        if (v.isJsonArray()) {
            for (JsonElement el : v.getAsJsonArray()) out.add(el);
        } else if (v.isJsonObject()) {
            out.add(v);
        }
        return out;
    }

    /**
     * video_data 一员 → 剧/视频行（0..n 条）：本体能成条直接收；本体只是
     * 容器时先把 video_detail 的 series_* 扁平字段补进副本再试；仍不成
     * 则展开 video_detail.video_detail_list 嵌套卡逐条收（协议原件
     * ParseSearchTab 同三路）。
     */
    private static void collectSearchVideo(JsonElement el, List<DramaItem> items,
                                           List<DramaSearchRow> rows) {
        if (el == null || !el.isJsonObject()) return;
        JsonObject o = el.getAsJsonObject();
        DramaItem item = asSeriesItem(o);
        if (item == null) {
            JsonObject vd = obj(o, "video_detail");
            if (vd != null) {
                JsonObject merged = o.deepCopy();
                fillIfBlank(merged, "title", str(vd, "series_title"));
                fillIfBlank(merged, "series_id", firstStr(vd, "series_id", "series_id_str"));
                fillIfBlank(merged, "cover", str(vd, "series_cover"));
                fillIfBlank(merged, "desc", str(vd, "series_intro"));
                if (intOf(merged, "episode_cnt", -1) < 0 && intOf(vd, "episode_cnt", -1) >= 0) {
                    merged.addProperty("episode_cnt", intOf(vd, "episode_cnt", -1));
                }
                item = asSeriesItem(merged);
            }
        }
        if (item != null) {
            items.add(item);
            rows.add(DramaSearchRow.ofSeries(item));
            return;
        }
        JsonObject vd = obj(o, "video_detail");
        JsonArray cards = vd == null ? null : arr(vd, "video_detail_list");
        if (cards != null) {
            for (JsonElement card : cards) {
                if (card == null || !card.isJsonObject()) continue;
                DramaItem ci = asSeriesItem(card);
                if (ci != null) {
                    items.add(ci);
                    rows.add(DramaSearchRow.ofSeries(ci));
                }
            }
        }
    }

    /**
     * book_data 一员 → 小说行（字段集照协议原件 catalogBook 逐字：
     * book_id/book_name/thumb_url/abstract/serial_count；另 author 一键
     * 协议原件未收、存档真回包实有（e1_search 综合签段 book_data），
     * 照实收）。
     */
    private static DramaSearchRow searchBookRow(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        JsonObject b = el.getAsJsonObject();
        String id = firstStr(b, "book_id");
        if (id == null) id = longStr(b, "book_id");
        String title = firstStr(b, "book_name", "title");
        if (id == null || title == null) return null;
        return DramaSearchRow.ofBook(id, title, firstStr(b, "thumb_url", "cover"),
                str(b, "abstract"), intOf(b, "serial_count", 0), str(b, "author"));
    }

    /** celebrity_user_list 一员 → 搜索结果用户行。 */
    private static DramaSearchRow searchUserRow(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        JsonObject u = el.getAsJsonObject();
        JsonObject base = obj(u, "base_info");
        String uid = str(u, "user_id");
        if (uid == null) uid = longStr(u, "user_id");
        if (uid == null && base != null) uid = str(base, "user_id");
        String name = base == null ? null : str(base, "user_name");
        if (name == null) name = str(u, "user_name");
        if (uid == null || name == null) return null;
        JsonObject stat = obj(u, "user_stat");
        return DramaSearchRow.ofUser(new DramaAuthorHit(uid, name,
                base == null ? null : str(base, "user_avatar"),
                stat == null ? -1 : intOf(stat, "celebrity_work_cnt", -1)));
    }

    /**
     * post_data 一员 → 帖行（存档 tab11 真回包逐字：post_id/title/
     * pure_content 正文/user_info 平铺作者/digg_cnt/cover_url）。
     * 正文装入 DramaWork.PostBody（只承载正文，搜索帖行在用）；
     * materials 图集/关联剧与计数曾一并抽取，全树零消费已摘（P-05）。
     */
    private static DramaSearchRow searchPostRow(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        JsonObject p = el.getAsJsonObject();
        String postId = firstStr(p, "post_id");
        if (postId == null) postId = longStr(p, "post_id");
        String title = str(p, "title");
        if (postId == null || title == null || title.isEmpty()) return null;
        JsonObject ui = obj(p, "user_info");
        JsonObject base = ui == null ? null : obj(ui, "base_info");
        String authorUid = ui == null ? null : firstStr(ui, "user_id");
        if (authorUid == null && base != null) authorUid = str(base, "user_id");
        String authorName = base != null ? str(base, "user_name")
                : ui == null ? null : str(ui, "user_name");
        String authorAvatar = base != null ? str(base, "user_avatar")
                : ui == null ? null : str(ui, "user_avatar");
        DramaWork.PostBody body = new DramaWork.PostBody(str(p, "pure_content"));
        return DramaSearchRow.ofPost(new DramaWork(DramaWork.KIND_POST,
                postId, null, firstStr(p, "cover_url", "cover"), title, null, 0,
                longOf(p, "digg_cnt", 0), 0, null,
                authorName, authorAvatar, -1, body),
                authorUid);
    }


    // ---------- bookmall 频道面（openhongguo parse.go 逐函数对照移植） ----------

    /**
     * 频道开页回包 → 指定 tab_type 的一页（对照 ParseDiscoverTab＋
     * appendCatalogCell＋DiscoverContinuationTab）：只收 tab_type 命中的
     * tab 项，单元格按 video_data / video_detail 卡 / 嵌套 cell_data 递归抽；
     * has_more 取 tab 项的；续页凭证从同 tab 抽（session_id 在 tab 上、
     * cell 取 cell_id_str 非空且 show_type≠0 的、优先带嵌套 cell_data 的）。
     */
    public static DramaCellPage parseDiscoverTab(String json, int tabType) {
        return parseDiscoverTab(json, tabType, false);
    }

    /** skipLive=true 时直播型 cell 在组表层整条跳过（仅刷流口传此旗）。 */
    public static DramaCellPage parseDiscoverTab(String json, int tabType, boolean skipLive) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int code = intOf(root, "code", 0);
        String msg = str(root, "message");
        List<DramaItem> items = new ArrayList<>();
        JsonObject data = obj(root, "data");
        JsonObject tab = data == null ? null : findTab(data, tabType);
        boolean hasMore = false;
        HgContinuation cont = null;
        if (tab != null) {
            hasMore = boolOf(tab, "has_more");
            JsonArray cells = arr(tab, "cell_data");
            if (cells != null) {
                for (JsonElement cell : cells) collectDiscoverCells(cell, items, 0, skipLive);
            }
            // 续拉首轮 offset＝开页回包的 next_offset（HgContinuation
            // 类注口径；此前 extractContinuation 硬编码 "0"，续拉恒
            // 从第一页重拉、被消费侧去重整页滤光——#13「只有四条」
            // 的成形路）。tab 上没有再看 data 层，都没有才 "0"。
            String firstOffset = firstStr(tab, "next_offset");
            if (firstOffset == null) {
                JsonElement no = tab.get("next_offset");
                if (no != null && no.isJsonPrimitive() && no.getAsJsonPrimitive().isNumber()) {
                    firstOffset = String.valueOf(no.getAsLong());
                }
            }
            if (firstOffset == null && data != null) {
                firstOffset = firstStr(data, "next_offset");
            }
            cont = extractContinuation(tab, tabType, firstOffset);
        }
        // 小分类筹的正源是官方筛选面（distribution 面板），不在频道回包
        // 里抽——旧 collectCategories 对字符串形 category_schema 抽 0、
        // 唯一命中的是长按举报项，整条已废（账 hongguo-chip-fix-20261030）。
        return new DramaCellPage(code, msg, items, root, hasMore,
                cont == null ? null : cont.getOffset(),
                cont == null ? null : cont.getSessionId(), cont,
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList());
    }

    /**
     * 官方筛选面面板回包 → 维度行表（对照 openhongguo ParseShortFilter
     * 的 selector_rows 读取）：data.selector_rows[]＝{type,row_name,
     * items[{show_name,selector_item_id}]}，全行原样返回，消费方按
     * key 取需要的维度（剧场用 category_dim_theme 主题＋
     * category_dim_role 设定两行）。
     */
    public static List<DramaFilterRow> parseFilterPanel(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<DramaFilterRow> rows = new ArrayList<>();
        JsonObject data = obj(root, "data");
        JsonArray srows = data == null ? null : arr(data, "selector_rows");
        if (srows == null) return rows;
        for (JsonElement re : srows) {
            if (re == null || !re.isJsonObject()) continue;
            JsonObject r = re.getAsJsonObject();
            DramaFilterRow row = new DramaFilterRow();
            row.key = str(r, "type");
            row.name = str(r, "row_name");
            JsonArray items = arr(r, "items");
            if (items != null) {
                for (JsonElement ie : items) {
                    if (ie == null || !ie.isJsonObject()) continue;
                    JsonObject it = ie.getAsJsonObject();
                    String nm = firstStr(it, "show_name");
                    String val = firstStr(it, "selector_item_id");
                    if (nm != null && !nm.isEmpty() && val != null && !val.isEmpty()) {
                        row.options.add(new DramaFilterRow.Option(nm, val));
                    }
                }
            }
            if (row.key != null && !row.key.isEmpty()) rows.add(row);
        }
        return rows;
    }

    /**
     * 官方筛选面内容回包 → 条目页：data.video_data 是扁平剧条目数组
     * （顶层 series_id/title/cover/episode_cnt/category_schema…），走
     * 与频道面同一套单元格抽取；游标取 data 的 has_more/next_offset/
     * session_id（filter_ids 排除已展示由请求侧维护，探针实证页间
     * 零重叠）。
     */
    public static DramaCellPage parseFilterList(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int code = intOf(root, "code", 0);
        String msg = str(root, "message");
        List<DramaItem> items = new ArrayList<>();
        JsonObject data = obj(root, "data");
        boolean hasMore = false;
        String nextOffset = null;
        String sessionId = null;
        if (data != null) {
            hasMore = boolOf(data, "has_more");
            nextOffset = firstStr(data, "next_offset");
            if (nextOffset == null) {
                JsonElement no = data.get("next_offset");
                if (no != null && no.isJsonPrimitive() && no.getAsJsonPrimitive().isNumber()) {
                    nextOffset = String.valueOf(no.getAsLong());
                }
            }
            sessionId = str(data, "session_id");
            collectDiscoverCells(data, items, 0);
        }
        return new DramaCellPage(code, msg, items, root, hasMore, nextOffset, sessionId, null);
    }

    /**
     * 频道续页回包 → 一页（对照 ParseCellChange）：data{has_more,
     * next_offset, session_id, cell_view}，cell_view 走同一套单元格抽取。
     */
    public static DramaCellPage parseCellChange(String json) {
        return parseCellChange(json, false);
    }

    /** skipLive=true 时直播型 cell 在组表层整条跳过（仅刷流口传此旗）。 */
    public static DramaCellPage parseCellChange(String json, boolean skipLive) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int code = intOf(root, "code", 0);
        String msg = str(root, "message");
        List<DramaItem> items = new ArrayList<>();
        JsonObject data = obj(root, "data");
        boolean hasMore = false;
        String nextOffset = null;
        String sessionId = null;
        if (data != null) {
            hasMore = boolOf(data, "has_more");
            nextOffset = firstStr(data, "next_offset");
            if (nextOffset == null) {
                JsonElement no = data.get("next_offset");
                if (no != null && no.isJsonPrimitive() && no.getAsJsonPrimitive().isNumber()) {
                    nextOffset = String.valueOf(no.getAsLong());
                }
            }
            sessionId = str(data, "session_id");
            JsonElement view = data.get("cell_view");
            if (view != null) collectDiscoverCells(view, items, 0, skipLive);
        }
        return new DramaCellPage(code, msg, items, root, hasMore, nextOffset, sessionId, null);
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

    /** 续页凭证抽取（对照 DiscoverContinuationTab 的挑选顺序）。
     * initialOffset＝开页回包 next_offset（没给传 null、凭证内落 "0"）。 */
    private static HgContinuation extractContinuation(JsonObject tab, int tabType,
                                                      String initialOffset) {
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
            if (cellId == null) cellId = longStr(cell, "cell_id");
            if (cellId == null) continue;
            int showType = intOf(cell, "show_type", 0);
            if (showType == 0) continue;
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
        if (pickId == null) pickId = longStr(pick, "cell_id");
        if (pickId == null) return null;
        return new HgContinuation(pickId, sessionId,
                template, String.valueOf(intOf(pick, "show_type", 0)),
                String.valueOf(tabType),
                initialOffset == null || initialOffset.isEmpty() ? "0" : initialOffset);
    }

    /** 单元格递归抽取（对照 appendCatalogCell 的三路：video_data 数组/对象、video_detail 卡表、嵌套 cell_data）。 */
    private static void collectDiscoverCells(JsonElement el, List<DramaItem> out, int depth) {
        collectDiscoverCells(el, out, depth, false);
    }

    private static void collectDiscoverCells(JsonElement el, List<DramaItem> out, int depth,
                                             boolean skipLive) {
        if (el == null || depth > 8) return;
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) collectDiscoverCells(child, out, depth, skipLive);
            return;
        }
        if (!el.isJsonObject()) return;
        JsonObject cell = el.getAsJsonObject();
        // 直播型 cell 整条跳过（仅刷流 skipLive 时）：官方 cell 模型
        // CellViewData 的直播载荷是本体字段 room_list（List<LiveRoomData>，
        // 条目带 room_id/room_id_str），video_data 条目自身无此字段——按
        // 本体类型字段判，不按文案猜。命中即整条不收（含其嵌套 cell_data），
        // 条目根本不进 items，分页/补拉计数天然按过滤后条数走（账
        // hongguo-live-skip-20261030）。
        if (skipLive && isLiveCell(cell)) return;
        // 服务端类型类挂在 cell 本体的长按卡上（不在 video_data 条目里），
        // 借道随本 cell 的自有条目下发（跨推卡不是本 cell 的剧，不借）。
        String cellCct = contentCategoryTypeOf(cell);
        JsonElement vd = cell.get("video_data");
        if (vd != null) {
            if (vd.isJsonArray()) {
                for (JsonElement entry : vd.getAsJsonArray()) addDiscoverItem(entry, out, cellCct);
            } else {
                addDiscoverItem(vd, out, cellCct);
            }
        }
        JsonObject detail = obj(cell, "video_detail");
        if (detail != null) {
            // 单元格自身的短片（video_detail.video_list 首条自带 vid/封面/
            // 时长/计数，是完整短片对象）先入流——此前只收 video_detail_list
            // 的跨推卡、把单元格本体短片整条丢了（存档实证：推荐 tab16
            // inner[0]，audit-1455-identical.md）
            JsonArray own = arr(detail, "video_list");
            if (own != null && own.size() > 0 && own.get(0).isJsonObject()) {
                JsonObject first = own.get(0).getAsJsonObject();
                if (firstStr(first, "vid") != null && firstStr(first, "series_id") != null) {
                    // 作者信息挂在 detail 本体（ugc_user_info），不在
                    // video_list[0] 里——本体短片入流时把 ugc 借道塞进
                    // 合成 video_detail，让 normalize 的作者抬升照常命中；
                    // 条目自带 video_detail 时以自带为准、不借道。
                    // 信息摞三件（followed_cnt/secondary_infos/rec_tags）
                    // 同挂 detail 本体、video_list[0] 自身不带——旧借道只
                    // 塞 ugc，UGC 本体短片的星数/筹行/榜单条整批丢失
                    // （存档实证：推荐首屏 UGC cell 的 detail 带
                    // followed_cnt=109849 与 rec_tags，解析后条目全空，
                    // feed-infostack-reaudit-20261029），借道一并带走。
                    JsonObject ugc = obj(detail, "ugc_user_info");
                    boolean borrow = ugc != null || arr(detail, "secondary_infos") != null
                            || arr(detail, "rec_tags") != null
                            || obj(detail, "bottom_bar") != null
                            || longOf(detail, "followed_cnt", -1) >= 0;
                    if (borrow && obj(first, "video_detail") == null) {
                        JsonObject wrapped = first.deepCopy();
                        JsonObject carrier = new JsonObject();
                        if (ugc != null) carrier.add("ugc_user_info", ugc);
                        JsonArray osi = arr(detail, "secondary_infos");
                        if (osi != null) carrier.add("secondary_infos", osi.deepCopy());
                        JsonArray ort = arr(detail, "rec_tags");
                        if (ort != null) carrier.add("rec_tags", ort.deepCopy());
                        JsonObject obb = obj(detail, "bottom_bar");
                        if (obb != null) carrier.add("bottom_bar", obb.deepCopy());
                        if (longOf(detail, "followed_cnt", -1) >= 0) {
                            carrier.addProperty("followed_cnt", longOf(detail, "followed_cnt", -1));
                        }
                        wrapped.add("video_detail", carrier);
                        first = wrapped;
                    }
                    addDiscoverItem(first, out, cellCct);
                }
            }
            JsonArray cards = arr(detail, "video_detail_list");
            if (cards != null) {
                for (JsonElement card : cards) {
                    // 卡自身不带信息摞数据（筹行/追更数挂在父 video_detail
                    // 上，是本 cell 同剧的数据）——卡没自带 video_detail 时
                    // 借道塞进合成载体，让 normalize 照常抬升（与上方本体
                    // 短片的 ugc 借道同法；作者信息不借道，那是本 cell
                    // 短片自己的、不属于跨推卡）
                    if (card != null && card.isJsonObject() && obj(card.getAsJsonObject(), "video_detail") == null
                            && (arr(detail, "secondary_infos") != null || arr(detail, "rec_tags") != null
                            || obj(detail, "bottom_bar") != null
                            || longOf(detail, "followed_cnt", -1) >= 0)) {
                        JsonObject wrapped = card.getAsJsonObject().deepCopy();
                        JsonObject carrier = new JsonObject();
                        JsonArray si = arr(detail, "secondary_infos");
                        if (si != null) carrier.add("secondary_infos", si.deepCopy());
                        JsonArray rt = arr(detail, "rec_tags");
                        if (rt != null) carrier.add("rec_tags", rt.deepCopy());
                        JsonObject cbb = obj(detail, "bottom_bar");
                        if (cbb != null) carrier.add("bottom_bar", cbb.deepCopy());
                        if (longOf(detail, "followed_cnt", -1) >= 0) {
                            carrier.addProperty("followed_cnt", longOf(detail, "followed_cnt", -1));
                        }
                        wrapped.add("video_detail", carrier);
                        addDiscoverItem(wrapped, out, null);
                        continue;
                    }
                    addDiscoverItem(card, out, null);
                }
            }
        }
        JsonElement inner = cell.get("cell_data");
        if (inner != null) collectDiscoverCells(inner, out, depth + 1, skipLive);
    }

    /**
     * 直播型 cell 判定：cell 本体带非空 room_list（官方 rpc 模型
     * CellViewData.roomList，@SerializedName("room_list")，元素
     * LiveRoomData 带 room_id）。空数组/缺失都不算——没房间就不是
     * 直播条目。存档样本未见此型（账 hongguo-live-skip-20261030）。
     */
    private static boolean isLiveCell(JsonObject cell) {
        JsonArray rooms = arr(cell, "room_list");
        return rooms != null && rooms.size() > 0;
    }

    private static void addDiscoverItem(JsonElement entry, List<DramaItem> out, String cellCct) {
        if (entry == null || !entry.isJsonObject()) return;
        DramaItem item = asSeriesItem(normalizeDiscoverEntry(entry.getAsJsonObject()));
        if (item != null) {
            if (cellCct != null) item.contentCategoryType = cellCct;
            out.add(item);
        }
    }

    /**
     * 服务端类型类抽取：cell 长按卡（cell_long_press_action）里
     * selection_id＝30「视频类型」一项的 selection_dislike_report_extra
     * .content_category_type（"1"真人剧／"1004"漫剧／"1004_ai_video"
     * AI剧，分类探针 2026-10-30 实拉配对定版）；没带返回 null。
     */
    private static String contentCategoryTypeOf(JsonObject cell) {
        JsonObject lp = obj(cell, "cell_long_press_action");
        if (lp == null) return null;
        JsonArray cards = arr(lp, "new_long_press_action_card_playlet");
        if (cards == null) return null;
        for (JsonElement el : cards) {
            if (el == null || !el.isJsonObject()) continue;
            JsonObject card = el.getAsJsonObject();
            if (intOf(card, "selection_id", -1) != 30) continue;
            JsonObject extra = obj(card, "selection_dislike_report_extra");
            if (extra != null) return str(extra, "content_category_type");
        }
        return null;
    }

    /**
     * 频道条目键名归一：video_data 条目的剧信息常嵌在 video_detail 子对象
     * （series_title / series_cover / series_intro），video_detail_list 卡用
     * series_id_str / series_title 等直名——统一映射到 asSeriesItem 的扁平
     * 口径（条目自身字段优先，video_detail 只补缺）。
     */
    private static JsonObject normalizeDiscoverEntry(JsonObject o) {
        JsonObject merged = new JsonObject();
        JsonObject vd = obj(o, "video_detail");
        if (vd != null) {
            putIfPresent(merged, "title", str(vd, "series_title"));
            putIfPresent(merged, "cover", str(vd, "series_cover"));
            putIfPresent(merged, "desc", str(vd, "series_intro"));
            if (intOf(vd, "episode_cnt", -1) >= 0) {
                merged.addProperty("episode_cnt", intOf(vd, "episode_cnt", -1));
            }
            // 计数的位置随频道变（2026-10-28 存档回包逐层实测）：推荐频道在
            // video_data 条目顶层（digged_count；comment_count 不下发），
            // 漫剧/真人剧在 video_detail.video_list[0]（两项齐全）——归一到
            // 条目顶层，asSeriesItem 一处读。条目自身带同名字段时以自身为准
            // （下方 entrySet 覆盖循环会盖回来）
            JsonArray vids = arr(vd, "video_list");
            if (vids != null && vids.size() > 0 && vids.get(0).isJsonObject()) {
                JsonObject v0 = vids.get(0).getAsJsonObject();
                if (longOf(v0, "comment_count", -1) >= 0) {
                    merged.addProperty("comment_count", longOf(v0, "comment_count", -1));
                }
                if (longOf(v0, "digged_count", -1) >= 0) {
                    merged.addProperty("digged_count", longOf(v0, "digged_count", -1));
                }
                // 内容类型同在这条上（存档频道回包实证：1001＝UGC 短片、
                // 1004＝正剧短片）——UGC 判别靠它，漏抬则流里 UGC 永远
                // 认不出、起播只能撞单集口的 101000（定根账
                // feed-item-fetch-fail-20261029 §1）
                if (intOf(v0, "content_type", -1) >= 0) {
                    merged.addProperty("content_type", intOf(v0, "content_type", -1));
                }
                // 出品方品牌同在这条上（copyright，存档频道回包与搜索
                // 剧集签实证）——流页作者名缺失时以它回落显示（唯一
                // 消费点 HongguoFeedActivity 作者行）；旧注称的「作者
                // uid 反查搜索词」从未实现，P-03 已摘链（簇二账）
                putIfPresent(merged, "publisher", str(v0, "copyright"));
            }
            putIfPresent(merged, "series_id", firstStr(vd, "series_id", "series_id_str"));
            // 完结态在 video_data 条目的 video_detail 子对象上（频道存档逐层
            // 实证），归一到条目顶层；条目自身带同名键时下方覆盖循环以自身为准
            Integer status = intOrNull(vd, "series_status");
            if (status != null) merged.addProperty("series_status", status);
            // 内容类型同在 video_detail 本体（官方 VideoDetailInfo
            // .contentType 的键位，抠查账 manju-follow-dig 件 1 定版）：
            // 与 series_status 同处抬到顶层——上方 video_list[0] 抬的是
            // 逐集层同名键，只供 kindOf 在条目自带键缺失时回落；剧级
            // 判别（漫剧）以本体键为准，两处冲突以本体覆写为准
            Integer cType = intOrNull(vd, "content_type");
            if (cType != null) merged.addProperty("content_type", cType);
            Integer aiUsage = intOrNull(vd, "ai_usage_type");
            if (aiUsage != null) merged.addProperty("ai_usage_type", aiUsage);
            // 信息摞数据同在 video_detail 里（存档频道回包逐字实证）：
            // followed_cnt＝追更/收藏人数（官方右列星数来源）、
            // secondary_infos＝筹行全集（评分/季/题材/演员）——不抬到
            // 条目顶层，流页这两件永远空（2026-10-29 他真机对比点名）
            if (longOf(vd, "followed_cnt", -1) >= 0) {
                merged.addProperty("followed_cnt", longOf(vd, "followed_cnt", -1));
            }
            JsonArray secInfos = arr(vd, "secondary_infos");
            if (secInfos != null) merged.add("secondary_infos", secInfos.deepCopy());
            // 底栏条分段同在 video_detail 里（bottom_bar.text，存档逐字
            // 实证）——不抬到顶层，asSeriesItem 的 bottomBarTextOf 虽能
            // 读 video_detail，但归一后 video_detail 键会被条目自带空值
            // 覆盖循环干扰，顶层留一份原文最稳
            JsonObject bottomBar = obj(vd, "bottom_bar");
            if (bottomBar != null) merged.add("bottom_bar", bottomBar.deepCopy());
            // 榜单条同理：rec_tags 在本体短片/跨推卡形态下只挂父
            // video_detail（借道载体），不抬到顶层 fillInfoStack 读不到
            JsonArray recTags = arr(vd, "rec_tags");
            if (recTags != null) merged.add("rec_tags", recTags.deepCopy());
            // 分类架构同在 video_detail 里（category_schema 字符串化
            // JSON）——不抬到顶层，asSeriesItem 的 tags 永远空（剧场
            // 筹面靠条目自带分类名，无此抬升整格无筹；条目自带同名键
            // 时下方覆盖循环以自带为准）
            JsonElement schemaEl = vd.get("category_schema");
            if (schemaEl != null) merged.add("category_schema", schemaEl.deepCopy());
            // 专辑关系与 UGC 作者同在 video_detail 里（存档逐层实证：详情面
            // is_multi_season/related_album_id、cell 面 ugc_user_info）——不抬
            // 到条目顶层，asSeriesItem 读不到：多季剧在流里永远认不出、
            // 系列单弹不出，短片作者行/头像永远空（2026-10-28 逐项重对）
            if (vd.has("is_multi_season")) {
                merged.addProperty("is_multi_season", boolOf(vd, "is_multi_season"));
            }
            putIfPresent(merged, "album_id", firstStr(vd, "album_id_str", "album_id"));
            if (intOf(vd, "album_serial_count", -1) >= 0) {
                merged.addProperty("album_serial_count", intOf(vd, "album_serial_count", -1));
            }
            // 集数同在 video_detail 里时一并抬（#20 乙：系列剧条目
            // 顶层没带 episode_cnt、数只在 vd 里，观看完整条就只剩
            // 「观看完整漫剧」没集数）——条目自带时不覆盖。
            if (!merged.has("episode_cnt") && !merged.has("episode_count")
                    && intOf(vd, "episode_cnt", -1) >= 0) {
                merged.addProperty("episode_cnt", intOf(vd, "episode_cnt", -1));
            }
            JsonObject ugc = obj(vd, "ugc_user_info");
            JsonObject ugcBase = ugc == null ? null : obj(ugc, "base_info");
            if (ugcBase != null) {
                putIfPresent(merged, "author_name", str(ugcBase, "user_name"));
                putIfPresent(merged, "author_avatar", str(ugcBase, "user_avatar"));
                putIfPresent(merged, "author_desc", str(ugcBase, "description"));
            }
            // 作者 uid 同在 ugc_user_info 里（存档真回包实证：ugc 顶层与
            // base_info 各一份同值）——作者主页/关注作者的 object_id 来源；
            // 此前只抬名与头像、uid 被漏掉，头像上的关注只能退化成追剧。
            if (ugc != null) {
                String uid = str(ugc, "user_id");
                if (uid == null && ugcBase != null) uid = str(ugcBase, "user_id");
                putIfPresent(merged, "author_uid", uid);
            }
            // 作者关系态同在 ugc_user_info.user_relation 里（存档频道回包
            // 实证：匿名恒 relation_type=1、can_follow=true）——抬到条目
            // 顶层成 author_relation_type/author_can_follow 两键（与
            // followed_cnt 抬升同法），asSeriesItem 出口一处收；出口另有
            // authorUgc 直读兜底（账 hongguo-ugc-follow-dig-20261030
            // §3.3/§5.2）
            JsonObject ugcRel = ugc == null ? null : obj(ugc, "user_relation");
            int relType = relationTypeOf(ugcRel);
            if (relType >= 0) merged.addProperty("author_relation_type", relType);
            if (ugcRel != null && ugcRel.has("can_follow")) {
                merged.addProperty("author_can_follow", boolOf(ugcRel, "can_follow"));
            }
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            // 条目自带空串不许盖掉已从 video_detail 抬升的真值：推荐频道
            // video_data 条目自带 cover:""/vid:""、真封面只在 video_detail
            // .series_cover 里——无条件覆盖时封面被空串抹掉（2026-10-28
            // 《镇国杀神》真回包探针定罪：parseDiscoverTab 出 cover=null）
            JsonElement v = e.getValue();
            if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()
                    && v.getAsString().isEmpty()
                    && firstStr(merged, e.getKey()) != null) {
                continue;
            }
            merged.add(e.getKey(), v);
        }
        if (firstStr(merged, "series_id", "book_id") == null) {
            putIfPresent(merged, "series_id", firstStr(merged, "series_id_str"));
        }
        if (firstStr(merged, "title") == null) {
            putIfPresent(merged, "title", firstStr(merged, "series_title"));
        }
        if (firstStr(merged, "cover") == null) {
            putIfPresent(merged, "cover", firstStr(merged, "series_cover"));
        }
        if (firstStr(merged, "desc") == null) {
            putIfPresent(merged, "desc", firstStr(merged, "series_intro"));
        }
        if (firstStr(merged, "vid", "video_id") == null) {
            // 频道 video_data 条目的 vid 恒空（存档 24 条全量实证），短片
            // 指针在 highlight.vid（数值型，指向 video_detail.video_list
            // 中的被推集，不恒为首集）——不接它条目永远是剧头、播前被迫
            // 先拉详情（2026-10-28 逐项重对定罪，audit-1455-identical.md）
            JsonObject hl = obj(o, "highlight");
            String hv = hl == null ? null : flexStr(hl, "vid");
            if (hv != null) merged.addProperty("vid", hv);
        }
        // 被推集集序：流页简介的「第N集｜」前缀靠它（官方流页实拍
        // 113476/113592 定版形态）。集序在 video_detail.video_list 里
        // 与 vid 对应那条的 vid_index 上（存档逐层实证：episode 是布尔
        // 旗、真集序在 vid_index）；条目自带 vid_index 时覆盖循环已带入。
        if (vd != null && intOf(merged, "vid_index", -1) < 0) {
            String vidNow = firstStr(merged, "vid", "video_id");
            JsonArray vids = arr(vd, "video_list");
            if (vidNow != null && vids != null) {
                for (JsonElement ve : vids) {
                    if (!ve.isJsonObject()) continue;
                    JsonObject v = ve.getAsJsonObject();
                    if (!vidNow.equals(firstStr(v, "vid"))) continue;
                    int vi = intOf(v, "vid_index", -1);
                    if (vi > 0) merged.addProperty("vid_index", vi);
                    break;
                }
            }
        }
        return merged;
    }

    /** 数值/字符串两形的 id 读取（highlight.vid 是数值型）。 */
    private static String flexStr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return null;
        if (e.getAsJsonPrimitive().isNumber()) return String.valueOf(e.getAsLong());
        String s = e.getAsString();
        return s == null || s.isEmpty() ? null : s;
    }

    private static void putIfPresent(JsonObject o, String key, String value) {
        if (value != null && !value.isEmpty()) o.addProperty(key, value);
    }

    /** 目标键缺失或为空串、且来源值非空时才填（来源为空不写，不拿空串盖有值）。 */
    private static void fillIfBlank(JsonObject o, String key, String value) {
        if (value == null || value.isEmpty()) return;
        String cur = str(o, key);
        if (cur == null || cur.isEmpty()) o.addProperty(key, value);
    }

    /**
     * 在 data 子树里找数组，抽出带剧 id + 标题的对象为条目。深度上限 8：
     * bookmall/tab 真回包（2026-10-27 实拉存档）的剧条目在
     * tab_item[].cell_data[].cell_data[].video_data[] 里（带 series_id /
     * title / cover / episode_cnt），嵌套 7 层——旧上限 3 永远抽不到，
     * 是路径修好后解析面剩下的第二个坑。
     */
    private static void collectSeries(JsonElement el, List<DramaItem> out, int depth) {
        if (el == null || depth > 8) return;
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                DramaItem item = asSeriesItem(child);
                if (item != null) out.add(item);
                else collectSeries(child, out, depth + 1);
            }
        } else if (el.isJsonObject()) {
            JsonObject cell = el.getAsJsonObject();
            // video_detail 本体形：条目在 video_list 里、作者挂本体
            // ugc_user_info 上，asSeriesItem 只读条目自身字段永远抬不到
            // 作者——在此把作者四件补进条目副本再收（条目自带同名键
            // 不覆盖），补过的 video_list 不再走通用递归防重复收条。
            JsonObject ugc = obj(cell, "ugc_user_info");
            JsonArray vids = arr(cell, "video_list");
            boolean lifted = false;
            if (ugc != null && vids != null) {
                JsonObject base = obj(ugc, "base_info");
                String aName = base == null ? null : str(base, "user_name");
                String aAvatar = base == null ? null : str(base, "user_avatar");
                String aDesc = base == null ? null : str(base, "description");
                String aUid = str(ugc, "user_id");
                if (aUid == null && base != null) aUid = str(base, "user_id");
                // 作者关系态同挂本体 user_relation（与作者四件同源）：
                // 补成条目顶层键供 asSeriesItem 出口收（§3.3/§5.2）
                JsonObject aRel = obj(ugc, "user_relation");
                int aRelType = relationTypeOf(aRel);
                Boolean aCanFollow = aRel != null && aRel.has("can_follow")
                        ? boolOf(aRel, "can_follow") : null;
                if (aName != null || aUid != null) {
                    lifted = true;
                    for (JsonElement ve : vids) {
                        if (ve == null || !ve.isJsonObject()) continue;
                        JsonObject copy = ve.getAsJsonObject().deepCopy();
                        // 条目自带空串同样算缺：str() 对 "" 返回非 null，旧判
                        // 「==null 才补」会让自带 author_*:"" 挡住 ugc 层真值
                        // ——与 normalizeDiscoverEntry 的空串盖真值同型
                        // （96fa6fa5 只修了归一路径，此处是 collectSeries 路径）
                        fillIfBlank(copy, "author_name", aName);
                        fillIfBlank(copy, "author_avatar", aAvatar);
                        fillIfBlank(copy, "author_desc", aDesc);
                        fillIfBlank(copy, "author_uid", aUid);
                        if (aRelType >= 0 && intOf(copy, "author_relation_type", -1) < 0) {
                            copy.addProperty("author_relation_type", aRelType);
                        }
                        if (aCanFollow != null && !copy.has("author_can_follow")) {
                            copy.addProperty("author_can_follow", aCanFollow);
                        }
                        DramaItem item = asSeriesItem(copy);
                        if (item != null) out.add(item);
                    }
                }
            }
            for (Map.Entry<String, JsonElement> e : cell.entrySet()) {
                if (lifted && "video_list".equals(e.getKey())) continue;
                collectSeries(e.getValue(), out, depth + 1);
            }
        }
    }

    /**
     * user_relation.relation_type 读取（官方 UserRelationType 原值：
     * 1=None、2=Follow、3=Followed、4=MutualFollow）：线上两形兼容——
     * 数值形直接取、枚举名字符串形按名映射（与 parseUserProfile 的两形
     * 判读同口径，数字串亦容忍）；对象缺失/值认不出返回 -1（没带）。
     */
    private static int relationTypeOf(JsonObject rel) {
        if (rel == null) return -1;
        JsonElement rt = rel.get("relation_type");
        if (rt == null || !rt.isJsonPrimitive()) return -1;
        if (rt.getAsJsonPrimitive().isNumber()) return rt.getAsInt();
        if (rt.getAsJsonPrimitive().isString()) {
            switch (rt.getAsString()) {
                case "None": return 1;
                case "Follow": return 2;
                case "Followed": return 3;
                case "MutualFollow": return 4;
                default:
                    try {
                        return Integer.parseInt(rt.getAsString().trim());
                    } catch (Exception e) {
                        return -1;
                    }
            }
        }
        return -1;
    }

    private static DramaItem asSeriesItem(JsonElement el) {
        return asSeriesItem(el, 0);
    }

    /**
     * 官方底栏条分段原文：bottom_bar.text 数组（bottom_bar_type 为
     * short_play/album 等，文字以分段数组下发，客户端原序以「·」相接
     * 展示——频道存档 chan_* 与搜索 tab30 存档逐字实证）。位置两处：
     * 条目本体（归一后）或 video_detail 子对象；都没有返回 null。
     */
    private static List<String> bottomBarTextOf(JsonObject o) {
        JsonObject bar = obj(o, "bottom_bar");
        if (bar == null) {
            JsonObject vd = obj(o, "video_detail");
            if (vd != null) bar = obj(vd, "bottom_bar");
        }
        if (bar == null) return null;
        JsonArray texts = arr(bar, "text");
        if (texts == null || texts.size() == 0) return null;
        List<String> out = new ArrayList<>();
        for (JsonElement t : texts) {
            if (t != null && t.isJsonPrimitive()) {
                String s = t.getAsString();
                if (s != null && !s.trim().isEmpty()) out.add(s.trim());
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static DramaItem asSeriesItem(JsonElement el, int depth) {
        if (el == null || !el.isJsonObject()) return null;
        JsonObject o = el.getAsJsonObject();
        // 搜索视频签的合集条（content_type=1003，真回包 2026-10-29 实拉）：
        // video_data 本体无 series_id、vid 为空，唯一身份在子对象
        // album_info（album_id_str/title/episode_cnt/ugc_user_info）——
        // 不归一则整条被判无 id 丢弃（同词回包 20 行里 15 行是合集条，
        // 咱家只剩 5 条 UGC 单集，账 hongguo-search-parity-20261029）。
        JsonObject albumInfo = obj(o, "album_info");
        String id = firstStr(o, "series_id", "book_id", "seriesId");
        if (id == null) id = longStr(o, "item_id");
        if (id == null && albumInfo != null) id = firstStr(albumInfo, "album_id_str", "album_id");
        String title = firstStr(o, "title", "series_name", "name", "book_name");
        if (title == null && albumInfo != null) title = str(albumInfo, "title");
        if (id == null || title == null) return null;
        String cover = firstStr(o, "cover", "poster_url", "thumb_url", "cover_url", "image_url");
        String desc = firstStr(o, "desc", "description", "intro", "video_desc");
        if (desc == null) {
            // 简介的另一栖身处：video_detail.series_intro（搜索剧集签
            // tab29 存档逐条实证——本体直成条时 collectSearchVideo 的
            // 扁平补填分支不走、频道归一 normalizeDiscoverEntry 又只管
            // 频道面，搜索面整签 desc 恒空；与该归一的 desc 抬升同口径）
            JsonObject vdDesc = obj(o, "video_detail");
            if (vdDesc != null) desc = str(vdDesc, "series_intro");
        }
        int count = intOf(o, "episode_count", -1);
        if (count < 0) count = intOf(o, "episode_cnt", -1);
        if (count < 0 && albumInfo != null) count = intOf(albumInfo, "episode_cnt", -1);
        // 预约面：rpc 模型 Feed 带 appointment: Appointment{is_subscribed /
        // due_start_text / due_start_time}（classes11.dex），键名蛇形逐字验过；
        // 以前解析器没读、被容忍吞掉，未上线剧的预约态从这里进模型。
        JsonObject appt = obj(o, "appointment");
        // 短片面（研究账 §2.5，bookmall/tab 真回包逐字）：短片自身 vid、
        // 角标 tag_info.text、热度 rec_text、分类 category_schema（真回包是
        // 字符串化 JSON，解开后 [].name——验收 B1）、朝向 vertical——
        // 流改短片流后条目靠这组字段成立。
        String vid = firstStr(o, "vid", "video_id");
        JsonObject tagInfo = obj(o, "tag_info");
        String badge = tagInfo == null ? null : str(tagInfo, "text");
        // 角标底色与文案同源同读（tag_info.bg_color 首色，频道回包
        // 实证：爆剧 #FF6868／新剧 #00AE83；热榜 hotItems 同读法）
        String badgeColor = tagInfo == null ? null : firstBgColor(tagInfo);
        if (badge == null) {
            JsonArray ctl = arr(o, "cover_tag_info_list");
            if (ctl != null && ctl.size() > 0 && ctl.get(0).isJsonObject()) {
                badge = str(ctl.get(0).getAsJsonObject(), "text");
                badgeColor = firstBgColor(ctl.get(0).getAsJsonObject());
            }
        }
        // video_data 条目的角标/筹行还藏在 recommend_reason_list（字符串化
        // JSON）里：tag_info 是角标（「爆剧」）、secondary_info_list 是筹行
        JsonObject rrl = parseStringifiedObj(o.get("recommend_reason_list"));
        if (badge == null && rrl != null) {
            JsonObject rti = obj(rrl, "tag_info");
            if (rti != null) {
                badge = str(rti, "text");
                badgeColor = firstBgColor(rti);
            }
        }
        // 热度文案：rec_text 直字段没带时退 rec_tags 的非榜单条目
        // （「共X万人在追」「X万热度」，存档回包实证）
        String recText = str(o, "rec_text");
        if (recText == null) {
            JsonArray rts = arr(o, "rec_tags");
            if (rts != null) {
                for (JsonElement te : rts) {
                    if (!te.isJsonObject()) continue;
                    String c = str(te.getAsJsonObject(), "content");
                    if (c != null && !isRankText(c)) {
                        recText = c;
                        break;
                    }
                }
            }
        }
        List<String> tags = new ArrayList<>();
        JsonArray schema = asArray(o.get("category_schema"));
        if (schema != null) {
            for (JsonElement te : schema) {
                if (te.isJsonObject()) {
                    String name = str(te.getAsJsonObject(), "name");
                    if (name != null && !name.isEmpty()) tags.add(name);
                }
            }
        }
        Boolean vertical = null;
        JsonElement ve = o.get("vertical");
        if (ve != null && ve.isJsonPrimitive() && ve.getAsJsonPrimitive().isBoolean()) {
            vertical = ve.getAsBoolean();
        }
        // 多季/专辑面（研究账 §5.3，classes11 字符串池实证键名）：回包带了才有，
        // 没带全空——系列半屏单只认这组真数据（账 §5.4）。
        List<DramaItem> siblings = new ArrayList<>();
        if (depth == 0) {
            JsonArray album = arr(o, "album_data_list");
            if (album != null) {
                for (JsonElement se : album) {
                    DramaItem sib = asSeriesItem(se, 1);
                    if (sib != null) siblings.add(sib);
                }
            }
        }
        // 专辑 id：条目自身带 album_id_str 先用，合集条取 album_info 的
        String albumId = firstStr(o, "album_id_str", "album_id");
        if (albumId == null && albumInfo != null) {
            albumId = firstStr(albumInfo, "album_id_str", "album_id");
        }
        // UGC 短片的所属合集 id（批四残项 1 定点抠查）：频道存档回包的
        // UGC 条目本体无此字段，但搜索视频签真回包（tab30 存档逐键实证）
        // 与详情面 video_data 都带 related_album_id——只给 1001 型作
        // albumId 补源（正剧的 related_album_id 是多季专辑身份、已有
        // 详情路消费，不在此改判，防串正剧系列款/系列单口径）
        if (albumId == null && intOf(o, "content_type", -1) == 1001) {
            albumId = firstStr(o, "related_album_id_str", "related_album_id");
            if (albumId == null) {
                // 搜索视频签真回包里此字段在 video_detail 子对象内
                // （tab30 存档逐键定位：5 条 UGC 恰 5 处，全在此层）
                JsonObject vdObj = obj(o, "video_detail");
                if (vdObj != null) {
                    albumId = firstStr(vdObj, "related_album_id_str", "related_album_id");
                }
            }
        }
        // 作者四件补源（本体 author_* 为空时）：合集条在
        // album_info.ugc_user_info、UGC 单集条在 video_detail.ugc_user_info
        // （与 collectSeries 的 ugc 抬升同一来源口径；此前搜索条这两路
        // 都抬不到，作者行恒空）。
        String authorName = str(o, "author_name");
        String authorAvatar = str(o, "author_avatar");
        String authorUid = str(o, "author_uid");
        JsonObject authorUgc = albumInfo == null ? null : obj(albumInfo, "ugc_user_info");
        if (authorUgc == null) {
            JsonObject detailObj = obj(o, "video_detail");
            if (detailObj != null) authorUgc = obj(detailObj, "ugc_user_info");
        }
        // 作者关系态（正收口，账 §3.3/§5.2）：顶层键优先（频道归一与
        // collectSeries 本体形两路抬升的落点），缺了再从 authorUgc 的
        // user_relation 子对象直读（合集条 album_info／UGC 条
        // video_detail 两栖身处，存档实证 relation 恰在此）
        int authorRelationType = intOf(o, "author_relation_type", -1);
        boolean authorCanFollow = boolOf(o, "author_can_follow");
        boolean canFollowKnown = o.has("author_can_follow");
        if (authorUgc != null) {
            JsonObject base = obj(authorUgc, "base_info");
            if (authorName == null && base != null) authorName = str(base, "user_name");
            if (authorAvatar == null && base != null) authorAvatar = str(base, "user_avatar");
            if (authorUid == null) {
                authorUid = str(authorUgc, "user_id");
                if (authorUid == null && base != null) authorUid = str(base, "user_id");
            }
            JsonObject rel = obj(authorUgc, "user_relation");
            if (authorRelationType < 0) authorRelationType = relationTypeOf(rel);
            if (!canFollowKnown && rel != null && rel.has("can_follow")) {
                authorCanFollow = boolOf(rel, "can_follow");
            }
        }
        DramaItem item = new DramaItem(id, title, cover, desc, count,
                appt != null, appt != null && boolOf(appt, "is_subscribed"),
                appt == null ? null : str(appt, "due_start_text"),
                vid, badge, recText, tags, vertical,
                boolOf(o, "is_multi_season"),
                albumId,
                intOf(o, "album_serial_count", -1), siblings,
                longOf(o, "comment_count", -1), longOf(o, "digged_count", -1),
                intOrNull(o, "series_status"), statementOf(intOrNull(o, "ai_usage_type")),
                authorName, authorAvatar,
                authorUid,
                firstStr(o, "publisher", "copyright"));
        item.badgeColor = badgeColor;
        item.vidIndex = intOf(o, "vid_index", -1);
        item.authorRelationType = authorRelationType;
        item.authorCanFollow = authorCanFollow;
        item.kind = kindOf(o, albumInfo);
        item.contentType = contentTypeOf(o);
        // 高光标记四键（条目 highlight 对象；官方 VideoHighlight 模型
        // 逐字键名，频道存档回包实证条目带此对象、缺省全 0 形）。
        // 以前只消费 highlight.vid 定被推集，其余三键在此补收——
        // 播完进剧的同集/下一集分流（官方 T7）全靠它们（互动账 §五.1）。
        JsonObject hlObj = obj(o, "highlight");
        if (hlObj != null) {
            item.highlightType = intOf(hlObj, "highlight_type", -1);
            item.highlightProgressBarType = intOf(hlObj, "progress_bar_type", -1);
            item.highlightStartMs = intOf(hlObj, "start_time_in_millisecond", -1);
            item.highlightEndMs = intOf(hlObj, "end_time_in_millisecond", -1);
        }
        item.bottomBarText = bottomBarTextOf(o);
        fillInfoStack(item, o, rrl);
        // 合集条的追更数挂 album_info（fillInfoStack 只读本体 followed_cnt）
        if (albumInfo != null && item.followedCnt < 0) {
            item.followedCnt = longOf(albumInfo, "followed_cnt", -1);
        }
        return item;
    }

    /**
     * 条目定型（普查卡 6/7/8 三型分家，账 hongguo-batch1-item-types
     * -20261029）：只在解析入口按协议字段定一次——
     * <ul>
     *   <li>ALBUM：content_type＝1003（搜索侧实拉定版，见账
     *       hongguo-search-parity-20261029），或条目本体无任何剧 id、
     *       身份只来自 album_info（合集条的归一形态，与上一条同物）。</li>
     *   <li>UGC_CLIP：content_type＝1001（频道存档回包实证）。</li>
     *   <li>SERIES：其余全部（含 1004 正剧短片、content_type 缺失/未知
     *       值）——多季/albumId 是正剧条目的属性（普查卡 8 第一义），
     *       不许拿它们把正剧判成合集。</li>
     * </ul>
     * 流/频道侧存档回包里从未出现 1003 与 album_info（2026-10-28 两批
     * 频道存档全量扫：只有 1001/1004），流侧合集的独立定型字段未抠到，
     * 本函数在流侧实际只分出 SERIES/UGC_CLIP 两型——如实记账，不编字段。
     */
    private static DramaItemKind kindOf(JsonObject o, JsonObject albumInfo) {
        int ct = intOf(o, "content_type", -1);
        if (ct == 1003) return DramaItemKind.ALBUM;
        if (albumInfo != null
                && firstStr(o, "series_id", "book_id", "seriesId") == null
                && longStr(o, "item_id") == null) {
            return DramaItemKind.ALBUM;
        }
        if (ct == 1001) return DramaItemKind.UGC_CLIP;
        return DramaItemKind.SERIES;
    }

    /**
     * 内容类型原值收口（抠查账 manju-follow-dig 件 1 规格 2，账
     * hongguo-rework-p1-feedplay-20261030 落码）：归一后的顶层优先；
     * 0 与缺失同作未下发（tab30 UGC 存根形 video_detail.content_type
     * ＝0 是实证坑），顶层无效再回落 video_detail 本体同名键
     * （cell 没走归一的出口兜底）；都没有返回 -1。与 kindOf 的分工：
     * kindOf 是三型分发判别（读顶层、含 0 回落 video_list 的旧链），
     * 本助手是剧级原值留底，两者不互相调用。
     */
    private static int contentTypeOf(JsonObject o) {
        int ct = intOf(o, "content_type", -1);
        if (ct > 0) return ct;
        JsonObject vd = obj(o, "video_detail");
        if (vd != null) {
            int vct = intOf(vd, "content_type", -1);
            if (vct > 0) return vct;
        }
        return -1;
    }

    /** 字符串化 JSON 对象（recommend_reason_list 形态）；解不开返回 null。 */
    private static JsonObject parseStringifiedObj(JsonElement el) {
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) return null;
        try {
            JsonElement parsed = JsonParser.parseString(el.getAsString());
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 榜单文案判别：「…榜 No.N」形。注意 rec_type=9 不是判别条件——
     * 热度条目（「8686万热度」）同样带 rec_type=9 与渐变色组，它是
     * 榜单条的载体条目：条面文案取它的 recommend_reason（榜单形时），
     * content 的热度文案归次级信息行（存档回包两形逐字实证）。
     */
    private static boolean isRankText(String text) {
        return text != null && text.contains("榜") && text.contains("No.");
    }

    /** tag_info 底色首色（bg_color[0]，热榜 hotItems 同读法）；没带为 null。 */
    private static String firstBgColor(JsonObject tagInfo) {
        if (tagInfo == null) return null;
        JsonArray bg = arr(tagInfo, "bg_color");
        if (bg != null && bg.size() > 0 && bg.get(0).isJsonPrimitive()) {
            return bg.get(0).getAsString();
        }
        return null;
    }

    /**
     * 信息摞后置填充（筹行/榜单条/追更数，存档频道回包逐字实证）：
     * 筹行＝secondary_infos（直数组优先，其次 recommend_reason_list 的
     * secondary_info_list），条目形 {content, data_type, KeepPriority}——
     * data_type 4＝评分（KeepPriority 100）、无 type＋highlight＝季
     * （99）、3＝题材、33＝演员；官方筹行按 KeepPriority 降序（稳定
     * 排序，同级保回包序）：实拍两例（评分/季/题材/演员 与 题材组/演员）
     * 都与此序一致。榜单条＝rec_tags 的榜单条目，渐变色与图标随条目
     * 下发、原样带走。系列剧药丸＝rec_tags 的 rec_type=15 条目
     * （recommend_reason＋content 两段与 rec_reason_style 的单色底，
     * 频道回包逐字实证）。followed_cnt＝官方右列星数（追更/收藏人数）。
     */
    private static void fillInfoStack(DramaItem item, JsonObject o, JsonObject rrl) {
        JsonArray infos = arr(o, "secondary_infos");
        if (infos == null && rrl != null) infos = arr(rrl, "secondary_info_list");
        if (infos != null) {
            List<JsonObject> entries = new ArrayList<>();
            for (JsonElement e : infos) {
                if (e.isJsonObject() && str(e.getAsJsonObject(), "content") != null) {
                    entries.add(e.getAsJsonObject());
                }
            }
            entries.sort((a, b) -> Integer.compare(
                    intOf(b, "KeepPriority", 0), intOf(a, "KeepPriority", 0)));
            List<String> chips = new ArrayList<>();
            int scorePos = -1;
            for (JsonObject e : entries) {
                if (intOf(e, "data_type", -1) == 4 && scorePos < 0) scorePos = chips.size();
                chips.add(str(e, "content"));
            }
            if (!chips.isEmpty()) {
                item.infoChips = chips;
                item.scoreChipPos = scorePos;
            }
        }
        JsonArray recTags = arr(o, "rec_tags");
        if (recTags != null) {
            for (JsonElement te : recTags) {
                if (!te.isJsonObject()) continue;
                JsonObject t = te.getAsJsonObject();
                // 系列剧药丸：rec_type=15 一条（两段文案＋单色底样式，
                // 频道回包逐字实证；与榜单条同条共存回包零例，流页同位二选一）
                if (intOf(t, "rec_type", -1) == 15 && item.seriesReason == null) {
                    item.seriesReason = str(t, "recommend_reason");
                    item.seriesContent = str(t, "content");
                    JsonObject sstyle = obj(t, "rec_reason_style");
                    if (sstyle != null) {
                        item.seriesColor = str(sstyle, "rec_style_background_color");
                        if (item.seriesColor == null) {
                            item.seriesColor = str(sstyle, "rec_style_dark_background_color");
                        }
                        item.seriesAlpha = str(sstyle, "rec_style_background_transparency");
                    }
                }
                // 条面文案两形：content 直接是榜单形，或 recommend_reason
                // 是榜单形（此时 content 是热度文案、已归 recText）
                String rankStr = null;
                boolean rankFromReason = false;
                if (isRankText(str(t, "content"))) rankStr = str(t, "content");
                else if (isRankText(str(t, "recommend_reason"))) {
                    rankStr = str(t, "recommend_reason");
                    rankFromReason = true;
                }
                if (rankStr == null || item.rankText != null) continue;
                // reason 榜单形时 content 是同条的热度段：官方药丸两段
                // 同显（「真人剧热播榜 No.7｜5593万热度」，rec_divider_type
                // =1 分隔，存档回包实证），合成一条带走
                if (rankFromReason) {
                    String heat = str(t, "content");
                    if (heat != null && !heat.isEmpty()) rankStr = rankStr + "｜" + heat;
                }
                item.rankText = rankStr;
                item.rankIconUrl = str(t, "rec_icon_url");
                JsonObject style = obj(t, "rec_reason_style");
                if (style != null) {
                    JsonArray cols = asArray(style.get("rec_style_background_colors"));
                    if (cols == null) cols = asArray(style.get("rec_style_dark_background_colors"));
                    if (cols != null) {
                        List<String> colors = new ArrayList<>();
                        for (JsonElement ce : cols) {
                            if (ce.isJsonPrimitive() && ce.getAsJsonPrimitive().isString()) {
                                colors.add(ce.getAsString());
                            }
                        }
                        if (!colors.isEmpty()) item.rankColors = colors;
                    }
                }
            }
        }
        item.followedCnt = longOf(o, "followed_cnt", -1);
    }

    /**
     * 作者声明映射：ai_usage_type=1 → 「内容由AI生成」（官方信息面实拍串，
     * 研究账 §3.5）；其余值无实拍样本，不编造，返回 null 不显。
     */
    static String statementOf(Integer aiUsageType) {
        return aiUsageType != null && aiUsageType == 1 ? "内容由AI生成" : null;
    }


    // ---------- 看剧偏好 ----------


    /** PreferenceStatus 容忍判定：数字 1／串 "like"／"1" 记已选（枚举在回包里的序列化形态未定版）。 */
    private static boolean statusLiked(JsonElement el) {
        if (el == null || !el.isJsonPrimitive()) return false;
        if (el.getAsJsonPrimitive().isNumber()) return el.getAsInt() == 1;
        String s = el.getAsString();
        return "1".equals(s) || "like".equalsIgnoreCase(s);
    }

    // ---------- 基础取值 ----------

    private static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    /** 元素可能是数组，也可能是字符串化 JSON（真回包 category_schema 即此形态，验收 B1）；解不开安静返回 null。 */
    private static JsonArray asArray(JsonElement e) {
        if (e == null) return null;
        if (e.isJsonArray()) return e.getAsJsonArray();
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            try {
                JsonElement p = JsonParser.parseString(e.getAsString());
                return p.isJsonArray() ? p.getAsJsonArray() : null;
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    /** 元素可能是对象，也可能是字符串化 JSON。 */
    private static JsonObject asObject(JsonElement e) {
        if (e == null) return null;
        if (e.isJsonObject()) return e.getAsJsonObject();
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            try {
                JsonElement p = JsonParser.parseString(e.getAsString());
                return p.isJsonObject() ? p.getAsJsonObject() : null;
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static String firstStr(JsonObject o, String... keys) {
        for (String k : keys) {
            String v = str(o, k);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static boolean boolOf(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        if (e != null && e.isJsonPrimitive()) {
            if (e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
            if (e.getAsJsonPrimitive().isNumber()) return e.getAsInt() != 0;
        }
        return false;
    }

    /** 数值型 id 字段（如 item_id:J）转字符串；字符串形态也认。 */
    private static String longStr(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive()) return null;
        if (e.getAsJsonPrimitive().isNumber()) return String.valueOf(e.getAsLong());
        String s = e.getAsString();
        return s == null || s.isEmpty() ? null : s;
    }

    private static int intOf(JsonObject o, String key, int def) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;
        if (e.getAsJsonPrimitive().isNumber()) return e.getAsInt();
        if (e.getAsJsonPrimitive().isString()) {
            // 字符串形数值（同接口族的 code 等字段有此形态，HgApiClient
            // .isOkCode 两形都认）：解得出才认，解不出回缺省——此前只认
            // 数字形，code:"101000" 会被 businessCodeOf 读成 0、把结构化
            // 拒绝误判成限流空体进全域冷却（扫查 H2）
            try {
                return Integer.parseInt(e.getAsString().trim());
            } catch (Exception ex) {
                try {
                    return (int) Double.parseDouble(e.getAsString().trim());
                } catch (Exception ex2) {
                    return def;
                }
            }
        }
        return def;
    }

    private static long longOf(JsonObject o, String key, long def) {
        JsonElement e = o == null ? null : o.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsLong();
        return def;
    }

    private static double doubleOf(JsonObject o, String key, double def) {
        JsonElement e = o == null ? null : o.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsDouble();
        return def;
    }

    /** fplay 形态里数值常以字符串给出。 */
    private static int intFlexible(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive()) return 0;
        try {
            return e.getAsJsonPrimitive().isNumber() ? e.getAsInt()
                    : (int) Double.parseDouble(e.getAsString());
        } catch (Exception ex) {
            return 0;
        }
    }

    private static long longFlexible(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive()) return 0;
        try {
            return e.getAsJsonPrimitive().isNumber() ? e.getAsLong()
                    : (long) Double.parseDouble(e.getAsString());
        } catch (Exception ex) {
            return 0;
        }
    }

    private static double doubleFlexible(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive()) return 0;
        try {
            return e.getAsJsonPrimitive().isNumber() ? e.getAsDouble()
                    : Double.parseDouble(e.getAsString());
        } catch (Exception ex) {
            return 0;
        }
    }

    /**
     * 用户资料解析（GET /reading/user/profile/get/，模型逐字段见
     * {@link DramaUserProfile} 类注）：data.ugc_user{user_id, base_info
     * {user_name/user_avatar/description}, user_relation{follow_user_num/
     * fans_num/recv_digged_count/relation_type}}。计数字段缺失记 -1（整行
     * 不显的判据在 DramaUserProfile.hasRelationCounts）；relation_type 兼容
     * 数字（UserRelationType 值 1–4）与枚举名字符串两形态。
     */
    public static DramaUserProfile parseUserProfile(String json) {
        if (json == null || json.isEmpty()) return null;
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
        JsonObject data = obj(root, "data");
        if (data == null) data = root;
        JsonObject ugc = obj(data, "ugc_user");
        if (ugc == null) return null;
        JsonObject base = obj(ugc, "base_info");
        JsonObject rel = obj(ugc, "user_relation");
        String relationRaw = null;
        Boolean followed = null;
        if (rel != null) {
            JsonElement rt = rel.get("relation_type");
            if (rt != null && rt.isJsonPrimitive()) {
                if (rt.getAsJsonPrimitive().isNumber()) {
                    int v = rt.getAsInt();
                    relationRaw = String.valueOf(v);
                    followed = v == 2 || v == 4;
                } else if (rt.getAsJsonPrimitive().isString()) {
                    relationRaw = rt.getAsString();
                    followed = "Follow".equals(relationRaw) || "MutualFollow".equals(relationRaw);
                }
            }
        }
        // 副标题行（定版 2026-10-29 再挖）：官方 KMP 映射 yy3/a 逐字——
        // 段文取 user_tag.sub_title_list（UserTag 模型 @SerializedName 逐字，
        // 不是 user_expand 那份：expand 的元素 text 恒 null、只带标签图标，
        // 旧版读它永远拼不出行）；末段「红果号：」＋base_info.biz_user_id
        // （rpc UserBaseInfo @SerializedName 逐字；实拉灿灿剧场 biz＝
        // 195870576971 与实拍 113563 校验值逐字对上）。红果号原值另存
        // hongguoId 供点行复制（官方点行复制的正是 biz 值，KMP 依赖层
        // 参数名 hgAccount）。
        JsonObject tag = obj(ugc, "user_tag");
        List<String> subParts = subTitleTexts(tag);
        if (subParts.isEmpty()) {
            // 兜底：个别形状把带字段放 user_expand 时仍读得到（定版主源在上）
            subParts = subTitleTexts(obj(ugc, "user_expand"));
        }
        String hongguoId = base == null ? null : firstStr(base, "biz_user_id");
        if (hongguoId != null && hongguoId.isEmpty()) hongguoId = null;
        if (hongguoId != null) subParts.add("红果号：" + hongguoId);
        String subTitleLine = subParts.isEmpty() ? null : String.join(" | ", subParts);
        // 头衔徽标（user_tag.user_title_info：zh_title＋icon.icon_default_url，
        // 本机实拉出品方作者验通：「已入驻」＋35×16 图标）
        String titleBadge = null;
        String titleBadgeIcon = null;
        int titleBadgeIconW = 0;
        int titleBadgeIconH = 0;
        JsonArray titles = tag == null ? null : arr(tag, "user_title_info");
        if (titles != null && titles.size() > 0 && titles.get(0).isJsonObject()) {
            JsonObject t0 = titles.get(0).getAsJsonObject();
            titleBadge = str(t0, "zh_title");
            String iconRaw = str(t0, "icon");
            if (iconRaw != null && !iconRaw.isEmpty()) {
                try {
                    JsonElement ip = JsonParser.parseString(iconRaw);
                    if (ip.isJsonObject()) {
                        JsonObject ipo = ip.getAsJsonObject();
                        titleBadgeIcon = str(ipo, "icon_default_url");
                        // 图标定版尺寸（icon_width/icon_height，dp）：页面按此
                        // 尺寸定框整图显示，不许裁（实拉已入驻图标 35×16）。
                        titleBadgeIconW = intOf(ipo, "icon_width", 0);
                        titleBadgeIconH = intOf(ipo, "icon_height", 0);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        // 页头底（data.bg_color/bg_covers）与两签隐私开关（data 层，与 ugc_user 平级）
        String bgColor = str(data, "bg_color");
        String bgCover = null;
        JsonArray covers = arr(data, "bg_covers");
        if (covers != null && covers.size() > 0 && covers.get(0).isJsonPrimitive()) {
            try {
                bgCover = covers.get(0).getAsString();
            } catch (Exception ignored) {
            }
        }
        boolean shelfOpen = true;
        boolean diggOpen = true;
        JsonObject privacy = obj(data, "person_privacy_switch_conf");
        if (privacy != null) {
            shelfOpen = !"off".equals(str(privacy, "person_bookshelf_video_list_switcher"));
            diggOpen = !"off".equals(str(privacy, "person_digg_video_switcher"));
        }
        // V 标（user_tag.is_blue_vip＋vip_icon_url 定版 2026-10-29：官方
        // KMP 映射 yy3/a 把 isBlueVip 搬进作者页 tag 状态、评论海报侧同旗
        // 选蓝 V 图标 icon_short_video_comment_blue_v_*；实拉两样本的
        // vip_icon_url 均为 48×48 圆 V 图、色随账号档变（蓝/橙），故图直用
        // 服务端地址、不自绘。显隐门＝旗为真且地址非空，见 Activity。
        boolean blueVip = tag != null && boolOf(tag, "is_blue_vip");
        String vipIconUrl = tag == null ? null : str(tag, "vip_icon_url");
        if (vipIconUrl != null && vipIconUrl.isEmpty()) vipIconUrl = null;
        // 可关注旗与 relation_type 同在 user_relation 对象（字段名在树内
        // 专辑解析/频道归一两路已用，非新字段）；无此键按可关注计
        // （官方卡 23：canFollow=false 才不显关注钮）。
        boolean canFollow = rel == null || !rel.has("can_follow")
                || boolOf(rel, "can_follow");
        return new DramaUserProfile(
                firstStr(ugc, "user_id"),
                base == null ? null : firstStr(base, "user_name"),
                base == null ? null : firstStr(base, "user_avatar"),
                base == null ? null : firstStr(base, "description"),
                rel == null ? -1 : intOf(rel, "follow_user_num", -1),
                rel == null ? -1 : intOf(rel, "fans_num", -1),
                rel == null ? -1 : intOf(rel, "recv_digged_count", -1),
                relationRaw, followed, subTitleLine, hongguoId,
                titleBadge, titleBadgeIcon, titleBadgeIconW, titleBadgeIconH,
                bgColor == null || bgColor.isEmpty() ? null : bgColor,
                bgCover == null || bgCover.isEmpty() ? null : bgCover,
                shelfOpen, diggOpen, blueVip, vipIconUrl, canFollow);
    }

    /** sub_title_list 段文抽取（元素两形并存：对象取 text/content、纯字符串直用；空段跳过）。 */
    private static List<String> subTitleTexts(JsonObject holder) {
        List<String> parts = new ArrayList<>();
        JsonArray subs = holder == null ? null : arr(holder, "sub_title_list");
        if (subs == null) return parts;
        for (JsonElement el : subs) {
            String v = null;
            if (el.isJsonObject()) v = firstStr(el.getAsJsonObject(), "text", "content");
            else if (el.isJsonPrimitive()) {
                try {
                    v = el.getAsString();
                } catch (Exception ignored) {
                }
            }
            if (v != null && !v.isEmpty()) parts.add(v);
        }
        return parts;
    }

    // ---------- 作者主页三签（2026-10-29 本机实拉定版，probe-author 存档） ----------

    /**
     * 作者作品口回包（POST commentapi/user/profile/:uid/v1/）→ 一页。
     * data.tabs[0].sub_tabs 是子档表（全部100/合集13/剧评121…，服务端按
     * 作者类型下发）；data_list 按 data_type 分形（1 video／3 post／
     * 4 comment）；续拉凭据＝common_list_info.cursor 整串原样回传。
     * 信封坏或无 data 返回 null。
     */
    public static DramaWork.Page parseAuthorWorks(String json) {
        JsonObject root = parseEnvelope(json);
        if (root == null) return null;
        JsonObject data = obj(root, "data");
        if (data == null) return null;
        List<DramaWork.SubTab> subTabs = new ArrayList<>();
        JsonArray tabs = arr(data, "tabs");
        if (tabs != null && tabs.size() > 0 && tabs.get(0).isJsonObject()) {
            JsonArray subs = arr(tabs.get(0).getAsJsonObject(), "sub_tabs");
            if (subs != null) {
                for (JsonElement el : subs) {
                    if (!el.isJsonObject()) continue;
                    JsonObject so = el.getAsJsonObject();
                    subTabs.add(new DramaWork.SubTab(
                            str(so, "name"), intOf(so, "tab_type", -1),
                            longOf(so, "total", -1)));
                }
            }
        }
        List<DramaWork> items = new ArrayList<>();
        JsonArray rows = arr(data, "data_list");
        if (rows != null) {
            for (JsonElement el : rows) {
                if (!el.isJsonObject()) continue;
                DramaWork w = authorWorkOf(el.getAsJsonObject());
                if (w != null) items.add(w);
            }
        }
        JsonObject info = obj(data, "common_list_info");
        boolean more = info != null && boolOf(info, "has_more");
        String cursor = info == null ? null : str(info, "cursor");
        return new DramaWork.Page(items, more ? cursor : "", more, subTabs);
    }

    /** 作品口单条分形抽取（video/post/comment 三形＋收藏/点赞的 video_data 形复用点不在此）。 */
    private static DramaWork authorWorkOf(JsonObject row) {
        int type = intOf(row, "data_type", -1);
        if (type == DramaWork.KIND_VIDEO) {
            JsonObject v = obj(row, "video");
            if (v == null) return null;
            JsonObject common = obj(v, "common");
            JsonObject expand = obj(v, "expand");
            JsonObject stat = obj(v, "stat");
            JsonObject base = baseInfoOf(common);
            String cover = common == null ? null
                    : firstStr(common, "cover", "cover_vertical", "horiz_cover");
            List<String> subs = new ArrayList<>();
            if (expand != null) {
                JsonArray sa = arr(expand, "sub_title_list");
                if (sa != null) {
                    for (JsonElement el : sa) {
                        if (el.isJsonObject()) {
                            String t = str(el.getAsJsonObject(), "text");
                            if (t != null && !t.isEmpty()) subs.add(t);
                        }
                    }
                }
            }
            return new DramaWork(DramaWork.KIND_VIDEO,
                    firstStr(v, "video_id", "vid"),
                    null,
                    cover,
                    common == null ? null : str(common, "title"),
                    common == null ? null : str(common, "intro"),
                    expand == null ? 0 : intOf(expand, "episode_cnt", 0),
                    stat == null ? 0 : longOf(stat, "digg_cnt", 0),
                    stat == null ? 0 : longOf(stat, "play_cnt", 0),
                    subs,
                    base == null ? null : str(base, "user_name"),
                    base == null ? null : str(base, "user_avatar"),
                    -1);
        }
        if (type == DramaWork.KIND_POST) {
            JsonObject p = obj(row, "post");
            if (p == null) return null;
            JsonObject common = obj(p, "common");
            JsonObject stat = obj(p, "stat");
            JsonObject base = baseInfoOf(common);
            String cover = null;
            JsonObject coverObj = common == null ? null : obj(common, "cover");
            if (coverObj != null) cover = str(coverObj, "web_url");
            String title = common == null ? null : str(common, "title");
            JsonObject content = common == null ? null : obj(common, "content");
            if (title == null || title.isEmpty()) {
                title = content == null ? null : str(content, "pure_text");
            }
            return new DramaWork(DramaWork.KIND_POST,
                    str(p, "post_id"),
                    null, cover, title, null, 0,
                    stat == null ? 0 : longOf(stat, "digg_count", 0),
                    0, null,
                    base == null ? null : str(base, "user_name"),
                    base == null ? null : str(base, "user_avatar"),
                    -1,
                    postBodyOf(content));
        }
        if (type == DramaWork.KIND_COMMENT) {
            JsonObject c = obj(row, "comment");
            if (c == null) return null;
            JsonObject common = obj(c, "common");
            JsonObject expand = obj(c, "expand");
            JsonObject stat = obj(c, "stat");
            JsonObject base = baseInfoOf(common);
            JsonObject content = common == null ? null : obj(common, "content");
            return new DramaWork(DramaWork.KIND_COMMENT,
                    str(c, "comment_id"),
                    null, null,
                    content == null ? null : str(content, "text"),
                    null, 0,
                    stat == null ? 0 : longOf(stat, "digg_count", 0),
                    0, null,
                    base == null ? null : str(base, "user_name"),
                    base == null ? null : str(base, "user_avatar"),
                    scoreOf(expand));
        }
        if (type == DramaWork.KIND_ALBUM) {
            // 合集形（2026-10-31 本机复打定版）：row 只挂 album{}——
            // album_id/album_name/thumb_url/desc/is_stick_top/
            // series_status/video_count＋consumer_info{hot_score 现成
            // 热度文案，followed_cnt 数字串}＋album_video_list 预览集
            // （common{cover,title,vid_index}＋stat{digg_cnt}＋video_id）。
            JsonObject a = obj(row, "album");
            if (a == null) return null;
            JsonObject consumer = obj(a, "consumer_info");
            long followedCnt = 0;
            String fc = consumer == null ? null : str(consumer, "followed_cnt");
            if (fc != null && !fc.isEmpty()) {
                try {
                    followedCnt = Long.parseLong(fc.trim());
                } catch (Exception ignored) {
                }
            }
            List<DramaWork.AlbumEp> eps = new ArrayList<>();
            JsonArray vl = arr(a, "album_video_list");
            if (vl != null) {
                for (JsonElement el : vl) {
                    if (!el.isJsonObject()) continue;
                    JsonObject eo = el.getAsJsonObject();
                    JsonObject ec = obj(eo, "common");
                    JsonObject es = obj(eo, "stat");
                    eps.add(new DramaWork.AlbumEp(
                            str(eo, "video_id"),
                            ec == null ? null : str(ec, "cover"),
                            ec == null ? eps.size() : intOf(ec, "vid_index", eps.size()),
                            es == null ? 0 : longOf(es, "digg_cnt", 0)));
                }
            }
            String albumId = str(a, "album_id");
            DramaWork.AlbumInfo info = new DramaWork.AlbumInfo(
                    albumId, boolOf(a, "is_stick_top"),
                    intOf(a, "series_status", 0),
                    consumer == null ? null : str(consumer, "hot_score"),
                    followedCnt, eps);
            return new DramaWork(DramaWork.KIND_ALBUM,
                    albumId, albumId,
                    str(a, "thumb_url"),
                    str(a, "album_name"),
                    str(a, "desc"),
                    intOf(a, "video_count", 0),
                    0, 0, null, null, null, -1, null, info);
        }
        return null;
    }

    /**
     * 帖子正文包抽取：正文＝content.pure_text。content 缺失返回 null
     * （卡面照旧）。materials 图集/关联剧与 stat 计数曾一并抽取装入，
     * 全树零消费已随簇十五 P-05 摘除，stat 参数随之撤下。
     */
    private static DramaWork.PostBody postBodyOf(JsonObject content) {
        if (content == null) return null;
        return new DramaWork.PostBody(str(content, "pure_text"));
    }

    /** 剧评分（expand.score 真回包是字符串型 "10"，数字型也容忍）；没带为 -1。 */
    private static int scoreOf(JsonObject expand) {
        if (expand == null || !expand.has("score")) return -1;
        JsonElement e = expand.get("score");
        if (e == null || !e.isJsonPrimitive()) return -1;
        try {
            if (e.getAsJsonPrimitive().isNumber()) return e.getAsInt();
            if (e.getAsJsonPrimitive().isString()) return Integer.parseInt(e.getAsString().trim());
        } catch (Exception ignored) {
        }
        return -1;
    }

    /** common.user_info.base_info 抽取（作品口三形共用）。 */
    private static JsonObject baseInfoOf(JsonObject common) {
        if (common == null) return null;
        JsonObject ui = obj(common, "user_info");
        return ui == null ? null : obj(ui, "base_info");
    }

    /**
     * 作者收藏签回包（GET bookshelf/video/info?target_user_id=，本机实拉
     * 验通）：data.video_shelf_info[]＋next_offset/has_more。条目剧信息在
     * 本层（series_title/series_cover_url），video_data 里有 series_id。
     */
    public static DramaWork.Page parseAuthorShelf(String json) {
        JsonObject root = parseEnvelope(json);
        if (root == null) return null;
        JsonObject data = obj(root, "data");
        if (data == null) return null;
        List<DramaWork> items = new ArrayList<>();
        JsonArray rows = arr(data, "video_shelf_info");
        if (rows != null) {
            for (JsonElement el : rows) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                JsonObject vd = obj(o, "video_data");
                String seriesId = vd == null ? null
                        : firstStr(vd, "series_id_str", "series_id");
                if (seriesId == null) seriesId = firstStr(o, "series_id_str", "series_id");
                items.add(new DramaWork(DramaWork.KIND_VIDEO,
                        vd == null ? null : firstStr(vd, "vid", "video_id"),
                        seriesId,
                        firstStr(o, "series_cover_url", "cover"),
                        firstStr(o, "series_title", "title"),
                        str(o, "series_intro"),
                        vd == null ? 0 : intOf(vd, "episode_cnt", 0),
                        0, 0, null, null, null, -1));
            }
        }
        boolean more = boolOf(data, "has_more");
        int next = intOf(data, "next_offset", -1);
        return new DramaWork.Page(items,
                more && next >= 0 ? String.valueOf(next) : "", more, null);
    }




    /** 信封解析（code 缺失或 JSON 坏返回 null；code 非 0 也返回根，消费侧按 data 空判失败）。 */
    private static JsonObject parseEnvelope(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JsonElement el = JsonParser.parseString(json);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
