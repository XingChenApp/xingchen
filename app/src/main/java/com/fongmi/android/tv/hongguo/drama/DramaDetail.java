package com.fongmi.android.tv.hongguo.drama;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;

/** 一部剧的详情（不可变）。集目表是播放页选集与预取的输入。 */
public final class DramaDetail {

    /** 一位演员（详情回包 celebrities[]，字段口径照真回包 resp-002/003 逐字）。 */
    public static final class Celebrity {
        private final String nickname;
        private final String roleName;
        private final String avatar;
        private final String subTitle;

        Celebrity(String nickname, String roleName, String avatar, String subTitle) {
            this.nickname = nickname;
            this.roleName = roleName;
            this.avatar = avatar;
            this.subTitle = subTitle;
        }

        public String getNickname() { return nickname; }
        /** 饰演角色名（role_name）；没带为 null。 */
        public String getRoleName() { return roleName; }
        public String getAvatar() { return avatar; }
        /** 饰演副行（sub_title，如「饰 王妈」）；没带为 null。 */
        public String getSubTitle() { return subTitle; }
    }

    /**
     * 相关作品一条（不可变）：官方详情页「相关作品」段的真数据源——
     * plan 口（from＝detail_page_more_related）回包 show_type＝547 的
     * cell（官方 ShowType.DetailPageRelatedWorks），内容是与本剧同 IP
     * 绑定的作品：同剧其它季在前、同 IP 衍生/其它形态（漫剧等）在后，
     * 不是分类/标签推荐流（2026-10-30 本机实拉三部剧定版，账
     * hongguo-related-20261030）。字段口径照 547 cell 的 video_data 条目：
     * series_id/title/cover/episode_cnt/content_type/tag_info.text
     * （角标，如「第2季」「同IP」）/sub_title_list（副行文案，如
     * 「爱情 · 3868万热度」「预告 · 141.2万人已预约」）。集数官方在
     * cell 条目里常给 0，DramaClient 取回后用批量详情补 episode_cnt 与
     * series_status，补不到保持 0（未知，卡片回落副行文案，不编数）。
     * book_data（同 IP 的书形态）不进本列表：红果详情页不落书行。
     */
    public static final class RelatedWork {
        private final String seriesId;
        private final String title;
        private final String coverUrl;
        private final int episodeCount;
        private final Integer seriesStatus;
        private final String tagText;
        private final String subText;
        private final int contentType;

        RelatedWork(String seriesId, String title, String coverUrl, int episodeCount,
                    Integer seriesStatus, String tagText, String subText, int contentType) {
            this.seriesId = seriesId;
            this.title = title;
            this.coverUrl = coverUrl;
            this.episodeCount = episodeCount;
            this.seriesStatus = seriesStatus;
            this.tagText = tagText;
            this.subText = subText;
            this.contentType = contentType;
        }

        /** 合集季行 → 相关作品条（集数/完结态/角标都走季行自有字段）。 */
        public static RelatedWork fromAlbumItem(DramaItem item) {
            return new RelatedWork(item.getSeriesId(), item.getTitle(), item.getCoverUrl(),
                    item.getEpisodeCount(), item.getSeriesStatus(), item.getBadge(),
                    null, -1);
        }

        /** 批量详情补回集数/完结态后的副本（原条不动）。 */
        RelatedWork withEpisodeInfo(int count, Integer status) {
            return new RelatedWork(seriesId, title, coverUrl, count,
                    status != null ? status : seriesStatus, tagText, subText, contentType);
        }

        public String getSeriesId() { return seriesId; }
        public String getTitle() { return title; }
        public String getCoverUrl() { return coverUrl; }
        /** 集数；未知为 0。 */
        public int getEpisodeCount() { return episodeCount; }
        /** 完结态原值（series_status）；没补到为 null。 */
        public Integer getSeriesStatus() { return seriesStatus; }
        /** 封面角标文案（第N季/同IP 等）；没带为 null。 */
        public String getTagText() { return tagText; }
        /** 副行文案（官方 sub_title_list 拼接）；没带为 null。 */
        public String getSubText() { return subText; }
        /** 内容类型原值（content_type，1004＝漫剧）；没带为 -1。 */
        public int getContentType() { return contentType; }

        /** 是否已完结（与 DramaDetail 同口径：status＝1 或没带判完结）。 */
        public boolean isFinished() {
            return seriesStatus == null || seriesStatus == 1;
        }

        /**
         * 未上线（预告/预约态）：官方副行带「预告」「已预约」字样即是
         * （实拉定版：未上线季的 sub_title_list 恒含其一）。这类条目点开
         * 进详情页（看预告/信息），不直进播放。
         */
        public boolean isComingSoon() {
            return (subText != null && (subText.contains("已预约") || subText.contains("预告")))
                    || (tagText != null && tagText.contains("预告"));
        }
    }

    /** 一集的引用（不可变）。 */
    public static final class Episode {
        private final String videoId;
        private final int index;
        private final String title;
        /** 本集点赞数（回包 video_list[i].digged_count）；没带为 -1。 */
        private final long diggCount;

        Episode(String videoId, int index, String title) {
            this(videoId, index, title, -1);
        }

        Episode(String videoId, int index, String title, long diggCount) {
            this.videoId = videoId;
            this.index = index;
            this.title = title;
            this.diggCount = diggCount;
        }

        public String getVideoId() { return videoId; }
        /** 集序号（从回包顺序或字段取，未知为 -1）。 */
        public int getIndex() { return index; }
        public String getTitle() { return title; }
        public long getDiggCount() { return diggCount; }
    }

    private final String seriesId;
    private final String title;
    private final String coverUrl;
    private final String desc;
    private final String authorName;
    private final int episodeCount;
    private final List<Episode> episodes;
    private final JsonObject raw;
    /** 回包带 appointment 对象（未上线剧的预约面）；没带为 false。 */
    private final boolean hasAppointment;
    /** appointment.is_subscribed：服务端给的当前账号预约态。 */
    private final boolean appointmentSubscribed;
    /** appointment.due_start_text：上线文案；没带为 null。 */
    private final String dueStartText;
    /** 演员表（回包 celebrities[]）；没带为空表。 */
    private final List<Celebrity> celebrities;
    /**
     * 完结态（回包 series_status：0 更新中、1 已完结、3 今日更新、4 停更）；
     * 回包没带为 null。
     */
    private final Integer seriesStatus;
    /** 作者声明文案（ai_usage_type 映射）；没带为 null。 */
    private final String statement;
    /** 是否多季系列（is_multi_season）。 */
    private final boolean multiSeason;
    /** 所属专辑 id（related_album_id，系列单取数钥匙）；没带为 null。 */
    private final String relatedAlbumId;
    /** 热度原值（hot_score，详情页「红果热度值」拼串用）；没带为 -1。 */
    private final long hotScore;
    /** 服务端收藏态（followed）；回包没带为 false。 */
    private final boolean followed;
    /** 收藏数（followed_cnt）；没带为 -1。 */
    private final long followedCnt;
    /** 服务端下发的剧主色（series_color_hex，详情页沉浸底色）；没带为 null。 */
    private final String colorHex;
    /** 副标题段（series_sub_title_list，如「全74集」「红果热度值4463万」）；没带为空表。 */
    private final List<String> subTitles;
    /** 分类标签名（category_schema 的 name 列）；没带为空表。 */
    private final List<String> tags;

    /**
     * UGC 单条视频的随详情下发播放模型（官方 UgcVideoDetail.video_model
     * 原串，seriessdk 模型定版）：动态视频的详情回包把 model 直接装在
     * video_data 里、没有独立取模型口（单集 video_model 口对 UGC vid
     * 实拉回 101000）。解析期由 DramaParser 一次性回填（attachUgcModel），
     * 之后只读；普通剧详情恒 null。
     */
    private String ugcVideoModel;
    /** UGC 视频宽/高（UgcVideoDetail.video_width/video_height）；未知为 0。 */
    private int ugcVideoWidth;
    private int ugcVideoHeight;
    /**
     * 作者 uid（详情 video_data 的 ugc_user_info.user_id 抬升）：2026-10-29
     * 本机实拉 5 部正剧（含账号自制剧）单剧＋批量两口全量扫——详情口对
     * 剧集内容恒不下发作者身份，此字段在现行回包里恒 null，抬升口先建好
     * （回包形状变了/别的面带了即刻生效）。解析期回填（与
     * ugcVideoModel 同惯例），之后只读。旧注称的「会话层搜索『用户』签
     * 反查兜底/反查命中回填」从未实现，P-03 已摘链（簇二账）。
     */
    private String authorUid;

    /**
     * 相关作品表（官方 plan 口 547 cell ＋合集兄弟季合并，详情页
     * loadRelatedWorks 组装后回填，见 attachRelatedWorks）；解析期
     * 恒空表，详情回包本身不带相关字段（实拉定版）。
     */
    private List<RelatedWork> relatedWorks = Collections.emptyList();

    /** 回填相关作品表（详情页组装完成后调用一次，之后只读）。 */
    public void attachRelatedWorks(List<RelatedWork> works) {
        this.relatedWorks = works == null ? Collections.emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<>(works));
    }

    /** 相关作品表；未回填为空表。 */
    public List<RelatedWork> getRelatedWorks() { return relatedWorks; }

    /**
     * 内容类型原值（详情回包 data.video_data.content_type，官方
     * VideoContentType：1004＝漫剧 MotionComic；抠查账
     * hongguo-manju-follow-dig-20261030 件 1 定版——e1 详情回包
     * 实值 1004 逐字实证）。解析期回填（与 ugcVideoModel 的
     * attach 同惯例），回包没带为 -1。消费方：集流展开时随
     * seriesStatus 一起传给逐集页（追更药丸 B 路径判别）。
     */
    private int contentType = -1;

    /** 解析期回填内容类型（仅 DramaParser 调用，构造后一次性）。 */
    void attachContentType(int contentType) {
        this.contentType = contentType;
    }

    /** 内容类型原值（content_type）；回包没带为 -1。 */
    public int getContentType() {
        return contentType;
    }

    /** 是否漫剧（content_type＝1004，官方 VideoContentType.MotionComic）。 */
    public boolean isMotionComic() {
        return contentType == 1004;
    }

    /** 解析期回填 UGC 播放模型（仅 DramaParser 调用，构造后一次性）。 */
    void attachUgcModel(String model, int width, int height) {
        this.ugcVideoModel = model;
        this.ugcVideoWidth = width;
        this.ugcVideoHeight = height;
    }

    /** 回填作者 uid（仅 DramaParser 解析期调用）。 */
    public void attachAuthorUid(String uid) {
        if (uid != null && !uid.isEmpty()) this.authorUid = uid;
    }

    /** 作者 uid；详情没下发为 null。 */
    public String getAuthorUid() { return authorUid; }

    /** UGC 随详情下发的播放模型原串；非 UGC 详情为 null。 */
    public String getUgcVideoModel() { return ugcVideoModel; }
    public int getUgcVideoWidth() { return ugcVideoWidth; }
    public int getUgcVideoHeight() { return ugcVideoHeight; }

    DramaDetail(String seriesId, String title, String coverUrl, String desc,
                   String authorName, int episodeCount, List<Episode> episodes, JsonObject raw) {
        this(seriesId, title, coverUrl, desc, authorName, episodeCount, episodes, raw,
                false, false, null, Collections.emptyList(), null, null, false, null,
                -1, false, -1, null, Collections.emptyList(), Collections.emptyList());
    }

    DramaDetail(String seriesId, String title, String coverUrl, String desc,
                   String authorName, int episodeCount, List<Episode> episodes, JsonObject raw,
                   boolean hasAppointment, boolean appointmentSubscribed, String dueStartText) {
        this(seriesId, title, coverUrl, desc, authorName, episodeCount, episodes, raw,
                hasAppointment, appointmentSubscribed, dueStartText,
                Collections.emptyList(), null, null, false, null,
                -1, false, -1, null, Collections.emptyList(), Collections.emptyList());
    }

    DramaDetail(String seriesId, String title, String coverUrl, String desc,
                   String authorName, int episodeCount, List<Episode> episodes, JsonObject raw,
                   boolean hasAppointment, boolean appointmentSubscribed, String dueStartText,
                   List<Celebrity> celebrities, Integer seriesStatus, String statement,
                   boolean multiSeason, String relatedAlbumId) {
        this(seriesId, title, coverUrl, desc, authorName, episodeCount, episodes, raw,
                hasAppointment, appointmentSubscribed, dueStartText,
                celebrities, seriesStatus, statement, multiSeason, relatedAlbumId,
                -1, false, -1, null, Collections.emptyList(), Collections.emptyList());
    }

    DramaDetail(String seriesId, String title, String coverUrl, String desc,
                   String authorName, int episodeCount, List<Episode> episodes, JsonObject raw,
                   boolean hasAppointment, boolean appointmentSubscribed, String dueStartText,
                   List<Celebrity> celebrities, Integer seriesStatus, String statement,
                   boolean multiSeason, String relatedAlbumId,
                   long hotScore, boolean followed, long followedCnt, String colorHex,
                   List<String> subTitles, List<String> tags) {
        this.seriesId = seriesId;
        this.title = title;
        this.coverUrl = coverUrl;
        this.desc = desc;
        this.authorName = authorName;
        this.episodeCount = episodeCount;
        this.episodes = Collections.unmodifiableList(episodes);
        this.raw = raw;
        this.hasAppointment = hasAppointment;
        this.appointmentSubscribed = appointmentSubscribed;
        this.dueStartText = dueStartText;
        this.celebrities = Collections.unmodifiableList(celebrities);
        this.seriesStatus = seriesStatus;
        this.statement = statement;
        this.multiSeason = multiSeason;
        this.relatedAlbumId = relatedAlbumId;
        this.hotScore = hotScore;
        this.followed = followed;
        this.followedCnt = followedCnt;
        this.colorHex = colorHex;
        this.subTitles = subTitles == null ? Collections.emptyList()
                : Collections.unmodifiableList(subTitles);
        this.tags = tags == null ? Collections.emptyList()
                : Collections.unmodifiableList(tags);
    }

    public String getStatement() { return statement; }
    public long getHotScore() { return hotScore; }
    public boolean isFollowed() { return followed; }
    public long getFollowedCnt() { return followedCnt; }
    public String getColorHex() { return colorHex; }
    public List<String> getSubTitles() { return subTitles; }
    public List<String> getTags() { return tags; }
    public boolean isMultiSeason() { return multiSeason; }
    public String getRelatedAlbumId() { return relatedAlbumId; }

    public String getSeriesId() { return seriesId; }
    public String getTitle() { return title; }
    public String getCoverUrl() { return coverUrl; }
    public String getDesc() { return desc; }
    public String getAuthorName() { return authorName; }
    public int getEpisodeCount() { return episodeCount; }
    public List<Episode> getEpisodes() { return episodes; }
    public boolean hasAppointment() { return hasAppointment; }
    public boolean isAppointmentSubscribed() { return appointmentSubscribed; }
    public String getDueStartText() { return dueStartText; }
    public List<Celebrity> getCelebrities() { return celebrities; }
    /** 完结态原值（series_status）；回包没带为 null。 */
    public Integer getSeriesStatus() { return seriesStatus; }

    /**
     * 是否已完结（官方口径）：series_status＝1 为完结；字段没下发时官方
     * 消费点（catalog/u2）对 episodesStatus 为 null 默认按 SeriesEnd 处理，
     * 星辰同口径——没带也判完结。
     */
    public boolean isFinished() {
        return seriesStatus == null || seriesStatus == 1;
    }
    /** 原始回包（详情字段口径未完全定版，上层需要冷门字段时从这里取，不丢信息）。 */
    public JsonObject getRaw() { return raw; }
}
