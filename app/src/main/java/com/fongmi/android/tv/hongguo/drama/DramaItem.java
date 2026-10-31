package com.fongmi.android.tv.hongguo.drama;

import java.util.Collections;
import java.util.List;

/** cell 驱动页面（剧场/搜索/追剧架）里一部剧的最小条目（不可变）。 */
public final class DramaItem {

    private final String seriesId;
    private final String title;
    private final String coverUrl;
    private final String desc;
    private final int episodeCount;
    /** 回包带 appointment 对象（未上线剧的预约面）；没带为 false。 */
    private final boolean hasAppointment;
    /** appointment.is_subscribed：服务端给的当前账号预约态。 */
    private final boolean appointmentSubscribed;
    /** appointment.due_start_text：上线文案（如「8月20日上线」）；没带为 null。 */
    private final String dueStartText;
    /** 短片自身 id（cell video_data.vid，研究账 §2.5 真回包实证）；没带为 null。 */
    private final String vid;
    /** 角标文案（tag_info.text，如「爆剧」「新剧」，§2.5）；没带为 null。 */
    private final String badge;
    /** 热度文案（rec_text，如「4805万热度」，§2.5）；没带为 null。 */
    private final String recText;
    /** 分类名（category_schema[].name，§2.5）；没带为空表。 */
    private final List<String> tags;
    /** 是否竖屏短片（cell vertical，§3.8 朝向预判）；回包没带为 null。 */
    private final Boolean vertical;
    /** 是否多季系列（is_multi_season，§5.3 字符串池实证键名）；没带为 false。 */
    private final boolean multiSeason;
    /** 所属专辑 id（album_id_str/album_id，§5.3）；没带为 null。 */
    private final String albumId;
    /** 专辑季/部数（album_serial_count，§5.3）；没带为 -1。 */
    private final int albumSerialCount;
    /** 同专辑的兄弟部（album_data_list 逐条抽，§5.3）；回包没带为空表。 */
    private final List<DramaItem> siblings;
    /** 评论数（cell video_data.comment_count，2026-10-28 频道真回包实证）；没带为 -1。
     * 非 final：评论面板拉到真实 total 后回写本条（D6 两面同源）。 */
    private long commentCount;
    /** 点赞数（cell video_data.digged_count，同上实证）；没带为 -1。
     * 非 final：详情/模型路后到时回写本条（#15，与评论数同口径）。 */
    private long diggCount;
    /**
     * 完结态（series_status：1＝已完结；频道回包在 video_detail 或
     * video_detail_list 卡上，2026-10-28 存档逐层实证）；没带为 null。
     */
    private final Integer seriesStatus;
    /** 作者声明文案（ai_usage_type 映射，见 DramaParser.statementOf）；没带为 null。 */
    private final String statement;
    /** 作者名/头像（cell 的 ugc_user_info.base_info 抬升；没带为 null）。 */
    private final String authorName;
    private final String authorAvatar;
    /**
     * 作者 uid（cell 的 ugc_user_info.user_id 抬升，base_info.user_id 兜底；
     * 频道存档真回包实证——推荐频道条目带、搜索 cell 不带）。作者主页与
     * 关注作者的 object_id 就靠它；没带为 null，点不开主页、不许拿名字认人。
     */
    private final String authorUid;
    /**
     * 出品方品牌（cell video_list[].copyright 抬升，如「魔方短剧」）；
     * 没带为 null。唯一消费：流页作者名缺失时回落显示（正剧条目作者
     * 身份全链无源、详情口恒不下发 uid，认人只认 cell 自带的
     * authorUid，没带就点不开主页）。旧注称的「会话层拿它走搜索
     * 『用户』签反查作者 uid」从未实现，P-03 已摘链（簇二账）。
     */
    private final String publisher;

    /**
     * 信息摞筹行（cell secondary_infos／recommend_reason_list 的
     * secondary_info_list，存档频道回包逐字实证）：已按 KeepPriority
     * 降序排好的展示文案（评分 data_type=4、季 highlight、第N季、题材
     * data_type=3、演员 data_type=33 全在这一列里，官方筹行就是它）；
     * 解析器后置填充（不进构造器，与流页条目的后置字段同
     * 惯例），没带为 null（适配器退回 category_schema 标签）。
     */
    public List<String> infoChips;
    /** 评分筹在 infoChips 里的下标（data_type=4）；没有为 -1。 */
    public int scoreChipPos = -1;
    /**
     * 官方底栏条分段原文（回包 video_detail.bottom_bar.text 数组，存档
     * 频道/搜索回包逐字实证：如 ["漫剧","剧名","全63集"]、
     * ["观看完整短剧","全303集"]、["合集","剧名","更新至114集"]）：
     * 服务端逐条下发的成品分段，流页底栏照原序以「·」相接即官方文案；
     * 解析器后置填充（不进构造器，与 infoChips 同惯例），没带为 null。
     */
    public List<String> bottomBarText;
    /** 榜单徽章条文案（rec_tags 中 rec_type=9 的一条，如「喜剧·真人剧榜 No.4」）；没上榜为 null。 */
    public String rankText;
    /** 榜单条渐变色（rec_reason_style 的背景色组，原样 #AARRGGBB 串）；没带为 null。 */
    public List<String> rankColors;
    /** 榜单条奖杯图标址（rec_icon_url）；没带为 null。 */
    public String rankIconUrl;
    /** 系列剧药丸主文案（rec_tags 中 rec_type=15 的 recommend_reason，如「系列剧·已上线6季」）；没带为 null。 */
    public String seriesReason;
    /** 系列剧药丸副文案（rec_tags 中 rec_type=15 的 content，如「共888万人在追」）；没带为 null。 */
    public String seriesContent;
    /** 系列剧药丸底色（rec_reason_style 的 rec_style_background_color 原样串）；没带为 null。 */
    public String seriesColor;
    /** 系列剧药丸底色透明度（rec_style_background_transparency 原样串，如「0.4」）；没带为 null。 */
    public String seriesAlpha;
    /** 角标底色（tag_info.bg_color 首色，与 badge 同源同读，如爆剧 #FF6868／新剧 #00AE83）；没带为 null。 */
    public String badgeColor;
    /** 追更/收藏人数原值（cell video_detail.followed_cnt）；没带为 -1。 */
    public long followedCnt = -1;
    /**
     * 被推集集序（video_list 里与本条 vid 对应那条的 vid_index）：
     * 流页简介「第N集｜」前缀的集号来源，解析器后置填充（不进构造器，
     * 与 followedCnt 同惯例）；没带为 -1。
     */
    public int vidIndex = -1;
    /**
     * 条目类型（普查卡 6/7/8 三型）：只由解析器在解析入口按协议字段
     * 定型后置填充（DramaParser.asSeriesItem：content_type＝1003 或身份
     * 只来自 album_info → ALBUM；content_type＝1001 → UGC_CLIP；其余
     * → SERIES），手工构造的条目（专辑名单行/系列单补行）恒为正剧
     * 缺省。消费端只读本字段分发，不许再按 content_type 原值推断。
     * content_type 原值字段（旧 contentType/getContentType/isUgc）已于
     * 批五按全树零真消费裁定删除：定型只在解析入口读 JSON 原值完成，
     * 模型不再携带原值（账 hongguo-batch5-cleanup-20261029）。
     */
    public DramaItemKind kind = DramaItemKind.SERIES;
    /**
     * 内容类型原值（官方 VideoContentType：1＝正剧短片、1001＝UGC、
     * 1003＝合集、1004＝漫剧 MotionComic、1007＝虚构漫剧；协议键
     * content_type，所在层是剧级对象——频道回包 video_detail 本体、
     * 详情回包 video_data 本体，抠查账 hongguo-manju-follow-dig
     * -20261030 件 1 定版）：解析器后置填充（不进构造器，与 kind
     * 同惯例），回包没带为 -1。与 kind 的分工：kind 是解析入口定完
     * 的三型分发结论、消费端只读它分发；本字段是原值留底，真消费
     * 只有漫剧判别（追更药丸 B 路径）一处等值判，不许拿它在消费端
     * 反推类型（批一分家口径不破）。旧 contentType 字段曾于批五以
     * 「零真消费」删除，本次因药丸 B 路径成为真消费方而按规格复置，
     * 不是把批五的删除翻案（账 hongguo-rework-p1-feedplay-20261030）。
     */
    public int contentType = -1;

    /** 是否漫剧（content_type＝1004，官方 VideoContentType.MotionComic）。 */
    public boolean isMotionComic() {
        return contentType == 1004;
    }
    /**
     * 高光片段标记（cell 条目 highlight 对象四键，官方 rpc 模型
     * VideoHighlight 逐字：highlight_type／progress_bar_type／
     * start_time_in_millisecond／end_time_in_millisecond；官方包
     * VideoHighlight.java SerializedName 实证＋频道存档回包实证——
     * 推荐频道条目带 highlight 对象、缺省全 0 形）：解析器后置填充
     * （不进构造器，与 vidIndex 同惯例），对象或键没带为 -1。
     */
    public int highlightType = -1;
    public int highlightProgressBarType = -1;
    public int highlightStartMs = -1;
    public int highlightEndMs = -1;

    /**
     * 高光/片段类素材判定（官方 videolike/f.b∩c 原式逐项，互动账
     * §五.1 铰链）：内容类型不在 {正片 ShortSeriesPlay＝1、ScenePlay
     * ＝2、漫剧 MotionComic＝1004} 三类内 ∧ highlight_type＝
     * HighLightFragment（枚举值 0）∧ 起止毫秒不等 ∧ progress_bar_type
     * ＝HighLight（枚举值 1）。枚举值据官方 rpc 模型
     * VideoHighlightType／ProgressBarType findByValue 定版。
     * 播完分流靠它定 isNext（false＝落同一集续看，见流页 onPlayEnded）。
     */
    public boolean isHighlightFragment() {
        if (contentType == 1 || contentType == 2 || contentType == 1004) return false;
        // 起止毫秒在官方生成类里是 int（缺键缺省 0），-1 哨兵按 0 读；
        // 两枚举是对象型（缺键为 null、永不命中），-1 哨兵天然不命中。
        int start = highlightStartMs < 0 ? 0 : highlightStartMs;
        int end = highlightEndMs < 0 ? 0 : highlightEndMs;
        return highlightType == 0
                && highlightProgressBarType == 1
                && start != end;
    }
    /**
     * 服务端类型类（cell 长按卡 selection_id＝30「视频类型」的
     * selection_dislike_report_extra.content_category_type 原值：
     * "1"＝真人剧、"1004"＝漫剧、"1004_ai_video"＝AI剧；分类探针
     * 2026-10-30 实拉配对定版，账 hongguo-theater-cat-probe-20261030）：
     * 解析器后置填充（不进构造器，与 contentType 同惯例），回包没带
     * 为 null。注意 ai_usage_type（作者声明）不是分家字段——漫剧与
     * AI剧条目都带，不许拿它分格（同账实证）。
     */
    public String contentCategoryType;
    /** 是否 AI剧（服务端类型类＝1004_ai_video，分家口径见上）。 */
    public boolean isAiDrama() {
        return "1004_ai_video".equals(contentCategoryType);
    }
    /**
     * 作者关系态（ugc_user_info.user_relation.relation_type，官方
     * UserRelationType 原值：1=None 未关注、2=Follow 我已关注他、
     * 3=Followed 他关注我、4=MutualFollow 互关）；解析器后置填充
     * （与 followedCnt 同惯例），回包没带为 -1。流页关注徽标的显隐与
     * HgStore 镜像校准靠它（账 hongguo-ugc-follow-dig-20261030 §3/§5.2）。
     */
    public int authorRelationType = -1;
    /** 作者可否关注（user_relation.can_follow）；回包没带为 false。 */
    public boolean authorCanFollow;

    DramaItem(String seriesId, String title, String coverUrl, String desc, int episodeCount) {
        this(seriesId, title, coverUrl, desc, episodeCount, false, false, null);
    }

    public static DramaItem of(String seriesId, String title, String coverUrl, String desc,
                                  int episodeCount, boolean hasAppointment,
                                  boolean appointmentSubscribed, String dueStartText) {
        return new DramaItem(seriesId, title, coverUrl, desc, episodeCount,
                hasAppointment, appointmentSubscribed, dueStartText);
    }

    DramaItem(String seriesId, String title, String coverUrl, String desc, int episodeCount,
                 boolean hasAppointment, boolean appointmentSubscribed, String dueStartText) {
        this(seriesId, title, coverUrl, desc, episodeCount, hasAppointment, appointmentSubscribed,
                dueStartText, null, null, null, Collections.emptyList(), null, false, null, -1,
                Collections.emptyList(), -1, -1, null, null, null, null, null, null, null);
    }

    DramaItem(String seriesId, String title, String coverUrl, String desc, int episodeCount,
                 boolean hasAppointment, boolean appointmentSubscribed, String dueStartText,
                 String vid, String badge, String recText, List<String> tags, Boolean vertical,
                 boolean multiSeason, String albumId, int albumSerialCount,
                 List<DramaItem> siblings, long commentCount, long diggCount,
                 Integer seriesStatus, String statement, String authorName, String authorAvatar,
                 String authorUid, String publisher) {
        this.seriesId = seriesId;
        this.title = title;
        this.coverUrl = coverUrl;
        this.desc = desc;
        this.episodeCount = episodeCount;
        this.hasAppointment = hasAppointment;
        this.appointmentSubscribed = appointmentSubscribed;
        this.dueStartText = dueStartText;
        this.vid = vid;
        this.badge = badge;
        this.recText = recText;
        this.tags = tags == null ? Collections.emptyList() : tags;
        this.vertical = vertical;
        this.multiSeason = multiSeason;
        this.albumId = albumId;
        this.albumSerialCount = albumSerialCount;
        this.siblings = siblings == null ? Collections.emptyList() : siblings;
        this.commentCount = commentCount;
        this.diggCount = diggCount;
        this.seriesStatus = seriesStatus;
        this.statement = statement;
        this.authorName = authorName;
        this.authorAvatar = authorAvatar;
        this.authorUid = authorUid;
        this.publisher = publisher;
    }

    /** 由详情面拼一个流页条目（集流页首条/详情页入流用）：字段取详情口径。
     * diggCount＝本条（集）的点赞数，调用方有则带入、无则 -1（#15：
     * 此前恒 -1，播放页点赞行永远没数）。 */
    public static DramaItem fromDetail(String seriesId, String title, String coverUrl, String desc,
                                       int episodeCount, String firstVid, Integer seriesStatus,
                                       String authorName, String authorAvatar, String authorUid,
                                       int contentType, long commentCount, long diggCount) {
        return fromDetail(seriesId, title, coverUrl, desc, episodeCount, firstVid,
                seriesStatus, authorName, authorAvatar, authorUid, contentType,
                commentCount, diggCount, -1);
    }

    /** followedCnt 版：收藏数随详情带入（此前恒 -1、播放页收藏数永远显不出）。 */
    public static DramaItem fromDetail(String seriesId, String title, String coverUrl, String desc,
                                       int episodeCount, String firstVid, Integer seriesStatus,
                                       String authorName, String authorAvatar, String authorUid,
                                       int contentType, long commentCount, long diggCount,
                                       long followedCnt) {
        DramaItem item = new DramaItem(seriesId, title, coverUrl, desc, episodeCount,
                false, false, null, firstVid, null, null, Collections.emptyList(), null,
                false, null, -1, Collections.emptyList(), commentCount, diggCount, seriesStatus,
                null, authorName, authorAvatar, authorUid, null);
        item.contentType = contentType;
        item.followedCnt = followedCnt;
        return item;
    }

    /** 补完结态的副本（字段后补场景：如半屏单里当前行由页面条目拼装）。 */
    public DramaItem withSeriesStatus(Integer status) {
        DramaItem copy = new DramaItem(seriesId, title, coverUrl, desc, episodeCount,
                hasAppointment, appointmentSubscribed, dueStartText,
                vid, badge, recText, tags, vertical, multiSeason, albumId,
                albumSerialCount, siblings, commentCount, diggCount, status, statement,
                authorName, authorAvatar, authorUid, publisher);
        copy.kind = kind;
        copy.contentType = contentType;
        copy.authorRelationType = authorRelationType;
        copy.authorCanFollow = authorCanFollow;
        return copy;
    }

    public String getStatement() { return statement; }
    public String getAuthorName() { return authorName; }
    public String getAuthorAvatar() { return authorAvatar; }
    public String getAuthorUid() { return authorUid; }
    /** 出品方品牌（cell copyright）；没带为 null。 */
    public String getPublisher() { return publisher; }

    public String getSeriesId() { return seriesId; }
    public String getTitle() { return title; }
    public String getCoverUrl() { return coverUrl; }
    public String getDesc() { return desc; }
    /** 总集数；回包未给为 -1。 */
    public int getEpisodeCount() { return episodeCount; }
    public boolean hasAppointment() { return hasAppointment; }
    public boolean isAppointmentSubscribed() { return appointmentSubscribed; }
    public String getDueStartText() { return dueStartText; }
    public String getVid() { return vid; }
    public String getBadge() { return badge; }
    public String getRecText() { return recText; }
    public List<String> getTags() { return tags; }
    public Boolean getVertical() { return vertical; }
    public boolean isMultiSeason() { return multiSeason; }
    public String getAlbumId() { return albumId; }
    public int getAlbumSerialCount() { return albumSerialCount; }
    public List<DramaItem> getSiblings() { return siblings; }
    public long getCommentCount() { return commentCount; }
    /** 评论面板真实 total 回写（D6）；只接受正数回写，不拿 0 冲掉下发值。 */
    public void setCommentCount(long total) { if (total > 0) commentCount = total; }
    public long getDiggCount() { return diggCount; }
    /** 点赞数回写（#15）：只接受正数，不拿 0 冲掉下发值（与评论数回写同口径）。 */
    public void setDiggCount(long total) { if (total > 0) diggCount = total; }
    /** 完结态原值（series_status）；没带为 null。 */
    public Integer getSeriesStatus() { return seriesStatus; }
    /** 条目级已完结判定：只认下发值＝1（cell 面不套详情的 null 默认口径）。 */
    public boolean isFinished() {
        return seriesStatus != null && seriesStatus == 1;
    }
}
