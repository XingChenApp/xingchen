package com.fongmi.android.tv.hongguo.drama;

/**
 * 搜索结果里的一条账号命中：cell 的 celebrity_user_list[] 逐条抽出
 * （2026-10-29 本机实拉定版：user_id 与 base_info{user_name/
 * user_avatar}、user_stat.celebrity_work_cnt），由 DramaSearchRow
 * 的用户行承载、在搜索结果面渲染。
 *
 * <p>旧注称本类是「正剧条目作者 uid 的反查兜底」（会话层按名搜
 * tab_type＝27、同名精确匹配定正主）——该反查从未实现：入口
 * DramaClient.searchUsers 与其专用解析 parseUserTab 已随 P-03 摘除
 * （与「搜索只留剧集/视频两签」定案冲突、落点不存在，见簇二账）；
 * 本类因搜索结果用户行在用而保留。</p>
 */
public final class DramaAuthorHit {

    public final String uid;
    public final String name;
    /** 头像址；没带为 null。 */
    public final String avatar;
    /** 作品数（celebrity_work_cnt）；没带为 -1。 */
    public final int workCount;

    public DramaAuthorHit(String uid, String name, String avatar, int workCount) {
        this.uid = uid;
        this.name = name;
        this.avatar = avatar;
        this.workCount = workCount;
    }
}
