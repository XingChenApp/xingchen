package com.fongmi.android.tv.hongguo.drama;

/**
 * 红果用户资料（GET /reading/user/profile/get/ 定版，a45/k.java:344
 * GetUserProfile 逐字；模型 GetUserProfileData/UgcUserInfo/UserBaseInfo/
 * UserRelation 的 @SerializedName 逐字段）：ugc_user.base_info 的名/头像/简介
 * ＋user_relation 的三计数（follow_user_num 关注／fans_num 粉丝／
 * recv_digged_count 获赞）与 relation_type（我与他的关系）。
 *
 * <p>消费面：「我的」头部关系行（HongguoMineRelationLayout b98 三组拼法）
 * 与作者主页数据行（SeriesProfileSocialRecordLayout ct0 三组拼法）同源；
 * 作者页关注钮初态以 relation_type 为准（Follow=2/MutualFollow=4 记已关注，
 * 枚举值 UserRelationType 定版），本地镜像只作回源前的占位与失败兜底。
 * 关注钮显隐另从同对象 can_follow 读（canFollow，官方卡 23 定版：
 * canFollow=false 不显钮；回包无此键按可关注计）。</p>
 */
public final class DramaUserProfile {

    public final String userId;
    public final String name;
    public final String avatar;
    public final String desc;
    public final int followUserNum;
    public final int fansNum;
    public final int recvDiggedCount;
    /** relation_type 原始值（数字 1–4 或枚举名字符串），无法判读时为 null。 */
    public final String relationType;
    /** 我已关注他（relation_type＝Follow/MutualFollow）；判读不出为 null。 */
    public final Boolean followedByMe;
    /** 是否可关注（user_relation.can_follow；回包无此键按 true 计）。 */
    public final boolean canFollow;
    /**
     * 副标题行（user_tag.sub_title_list 各段 text 以「 | 」拼＋末段
     * 「红果号：biz_user_id」，官方作者页「出品方 | 红果号：数字」那行
     * 同源：KMP 映射 yy3/a 取 userTag.subTitleList 与 baseInfo.bizUserID
     * 逐字，2026-10-29 实拉三样本对实拍校验值定版）；无段为 null。
     */
    public final String subTitleLine;
    /**
     * 红果号数字＝base_info.biz_user_id 原值（官方作者页点行复制的正是
     * 它、KMP 依赖层参数名 hgAccount，SeriesGuestProfileFragment Ih 逐字）；
     * 供点行复制，无为 null。
     */
    public final String hongguoId;
    /** 蓝 V 标在身（user_tag.is_blue_vip；与「已入驻」头衔徽标是两回事）。 */
    public final boolean blueVip;
    /** V 标图标地址（user_tag.vip_icon_url，48×48 圆 V 图，服务端定版）；无为 null。 */
    public final String vipIconUrl;
    /**
     * 头衔徽标文案（user_tag.user_title_info 首条 zh_title，官方作者页名旁
     * 「已入驻」蓝标同源，本机实拉验通）；无为 null。
     */
    public final String titleBadge;
    /** 头衔徽标图标地址（title_info.icon 的 icon_default_url）；无为 null。 */
    public final String titleBadgeIcon;
    /** 头衔徽标图标宽（icon_width，dp，服务端定版 35；缺失为 0）。 */
    public final int titleBadgeIconW;
    /** 头衔徽标图标高（icon_height，dp，服务端定版 16；缺失为 0）。 */
    public final int titleBadgeIconH;
    /** 页头底色（data.bg_color，如 #707070）；无为 null。 */
    public final String bgColor;

    public DramaUserProfile(String userId, String name, String avatar, String desc,
                         int followUserNum, int fansNum, int recvDiggedCount,
                         String relationType, Boolean followedByMe) {
        this(userId, name, avatar, desc, followUserNum, fansNum, recvDiggedCount,
                relationType, followedByMe, null, null, null, null, null,
                false, null);
    }

    public DramaUserProfile(String userId, String name, String avatar, String desc,
                         int followUserNum, int fansNum, int recvDiggedCount,
                         String relationType, Boolean followedByMe,
                         String subTitleLine, String hongguoId) {
        this(userId, name, avatar, desc, followUserNum, fansNum, recvDiggedCount,
                relationType, followedByMe, subTitleLine, hongguoId,
                null, null, null, false, null);
    }

    public DramaUserProfile(String userId, String name, String avatar, String desc,
                         int followUserNum, int fansNum, int recvDiggedCount,
                         String relationType, Boolean followedByMe,
                         String subTitleLine, String hongguoId,
                         String titleBadge, String titleBadgeIcon,
                         String bgColor,
                         boolean blueVip, String vipIconUrl) {
        this(userId, name, avatar, desc, followUserNum, fansNum, recvDiggedCount,
                relationType, followedByMe, subTitleLine, hongguoId,
                titleBadge, titleBadgeIcon, 0, 0,
                bgColor, blueVip, vipIconUrl, true);
    }

    public DramaUserProfile(String userId, String name, String avatar, String desc,
                         int followUserNum, int fansNum, int recvDiggedCount,
                         String relationType, Boolean followedByMe,
                         String subTitleLine, String hongguoId,
                         String titleBadge, String titleBadgeIcon,
                         int titleBadgeIconW, int titleBadgeIconH,
                         String bgColor,
                         boolean blueVip, String vipIconUrl, boolean canFollow) {
        this.userId = userId;
        this.name = name;
        this.avatar = avatar;
        this.desc = desc;
        this.followUserNum = followUserNum;
        this.fansNum = fansNum;
        this.recvDiggedCount = recvDiggedCount;
        this.relationType = relationType;
        this.followedByMe = followedByMe;
        this.subTitleLine = subTitleLine;
        this.hongguoId = hongguoId;
        this.titleBadge = titleBadge;
        this.titleBadgeIcon = titleBadgeIcon;
        this.titleBadgeIconW = titleBadgeIconW;
        this.titleBadgeIconH = titleBadgeIconH;
        this.bgColor = bgColor;
        this.blueVip = blueVip;
        this.vipIconUrl = vipIconUrl;
        this.canFollow = canFollow;
    }

    /** 计数三组齐备才算有数据行（任一计数缺失时整行不显，不摆半份数）。 */
    public boolean hasRelationCounts() {
        return followUserNum >= 0 && fansNum >= 0 && recvDiggedCount >= 0;
    }
}
