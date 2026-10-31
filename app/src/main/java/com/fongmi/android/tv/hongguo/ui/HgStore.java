package com.fongmi.android.tv.hongguo.ui;

import com.fongmi.android.tv.hongguo.drama.DramaItem;
import com.fongmi.android.tv.hongguo.drama.DramaItemKind;
import com.github.catvod.utils.Prefers;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 红果自有存储：全部键带 {@code hongguo_} 前缀走 {@link Prefers}（与音乐
 * MusicStore 同惯例），独立命名空间，不碰点播的历史表（bean.History / Room）。
 *
 * <p>存三类东西：互动本地状态（点赞/收藏/关注，四阶段换真接口后以服务端为准）、
 * 弹幕开关、观看断点（按剧存最近一集与位置，重进续播）。</p>
 */
public final class HgStore {

    private static final String K_DANMAKU_ON = "hongguo_danmaku_on";
    private static final String K_DM_OPACITY = "hongguo_dm_opacity";
    private static final String K_DM_SPEED = "hongguo_dm_speed";
    private static final String K_DM_LINES = "hongguo_dm_lines";
    private static final String K_DM_FONT = "hongguo_dm_font";
    // 批三 C 卡 37 新增三键（登记于 hongguo-batch3c 账）：屏蔽彩色／
    // 清屏展示／显示区域档。均为新增键，不动既有键语义。
    private static final String K_DM_BLOCK_COLORFUL = "hongguo_dm_block_colorful";
    private static final String K_DM_SHOW_IN_CLEAN = "hongguo_dm_show_in_clean";
    private static final String K_DM_LINE_SPACE = "hongguo_dm_line_space";
    private static final String K_SPEED = "hongguo_speed";
    private static final String K_LAUNCH_DIRECT = "hongguo_launch_direct";
    private static final String K_LIKED = "hongguo_liked_series";
    /** 旧收藏集键：批二归一后只作一次性并集迁移的来源，迁移完清空、不再读写。 */
    private static final String K_FAVED = "hongguo_faved_series";
    /** 追剧架本地正源（批二定版）：服务端只有一个追剧架动作与一套 followed
     * 状态，本地只留这一份；收藏名下全部入口转读本集。 */
    private static final String K_FOLLOWED = "hongguo_followed_series";
    /** 追剧架归一迁移旗：faved∪followed 并集落正源后置位，保证只迁一次。 */
    private static final String K_SHELF_MIGRATED = "hongguo_shelf_migrated_v1";
    private static boolean shelfMigrated;
    /** 作者关注集（key＝作者 uid，与按剧的追更集 K_FOLLOWED 分家：那套喂
     * 追更页签的剧列表，混进 uid 会生出无元信息的鬼行）。 */
    private static final String K_FOLLOWED_AUTHORS = "hongguo_followed_authors";
    private static final String K_RESERVED = "hongguo_reserved_series";
    private static final String K_RESERVED_ONLINE = "hongguo_reserved_online_series";
    private static final String K_HIST_PREFIX = "hongguo_hist_";

    private HgStore() {
    }

    // ---------- 弹幕开关 ----------

    public static boolean isDanmakuOn() {
        // 缺省开（#21 定根）：弹幕显示是定案保留项，此前缺省 false、
        // 两页（竖屏 Feed/横屏 Land）的取数与渲染又都以本开关为闸，
        // 没主动开过抽屉开关的装机弹幕全无（横竖屏同断）。关的动作
        // 仍照旧持久（抽屉/更多面板写 false 即关）。
        return Prefers.getBoolean(K_DANMAKU_ON, true);
    }

    public static void setDanmakuOn(boolean on) {
        Prefers.put(K_DANMAKU_ON, on);
    }

    // ---------- 弹幕设置（红果自有口：面板「弹幕设置」单的四项落点） ----------
    // 键带 hongguo_dm_ 前缀自成一口，不读点播 DanmakuSetting 的 danmaku_*
    // 全局键（此前喂 PlayerView 用的是点播那套配置，两边互串已断）。
    // 档表/缺省/映射全在 HgPanelLogic（官方 DanmakuOptionDialog 定版）。

    /** 不透明度百分比（20–100，缺省 90，官方真机缺省）。 */
    public static int getDanmakuOpacity() {
        return HgPanelLogic.clampDanmakuOpacity(
                Prefers.getInt(K_DM_OPACITY, HgPanelLogic.DM_OPACITY_DEFAULT));
    }

    public static void setDanmakuOpacity(int percent) {
        Prefers.put(K_DM_OPACITY, HgPanelLogic.clampDanmakuOpacity(percent));
    }

    /** 移动速度档下标（0–4，缺省末档「快」，官方真机缺省）。 */
    public static int getDanmakuSpeedIndex() {
        return HgPanelLogic.clampDanmakuIndex(
                Prefers.getInt(K_DM_SPEED, HgPanelLogic.DM_SPEED_DEFAULT_INDEX),
                HgPanelLogic.DM_SPEED_FACTORS.length);
    }

    public static void setDanmakuSpeedIndex(int index) {
        Prefers.put(K_DM_SPEED, HgPanelLogic.clampDanmakuIndex(
                index, HgPanelLogic.DM_SPEED_FACTORS.length));
    }

    /** 显示区域行数（1–4，缺省 2，官方真机缺省；见 HgPanelLogic.DM_LINES_DEFAULT）。 */
    public static int getDanmakuLines() {
        return HgPanelLogic.clampDanmakuLines(
                Prefers.getInt(K_DM_LINES, HgPanelLogic.DM_LINES_DEFAULT));
    }

    public static void setDanmakuLines(int lines) {
        Prefers.put(K_DM_LINES, HgPanelLogic.clampDanmakuLines(lines));
    }

    /** 字体大小档下标（0–4，缺省适中）。 */
    public static int getDanmakuFontIndex() {
        return HgPanelLogic.clampDanmakuIndex(
                Prefers.getInt(K_DM_FONT, HgPanelLogic.DM_FONT_DEFAULT_INDEX),
                HgPanelLogic.DM_FONT_SIZES.length);
    }

    public static void setDanmakuFontIndex(int index) {
        Prefers.put(K_DM_FONT, HgPanelLogic.clampDanmakuIndex(
                index, HgPanelLogic.DM_FONT_SIZES.length));
    }

    /**
     * 屏蔽彩色弹幕（批三 C 卡 37 补实缺，新键 hongguo_dm_block_colorful）：
     * 官方 key_colorful_danmaku_blocked 缺省 false（jadx2
     * kmp_video_danmaku/config/a.java 一手实证）。「彩色」口径见
     * HgPanelLogic.isColorfulDanmaku；生效点在 HongguoFeedActivity 取数
     * 过滤与播放中即时重建，不是摆设键。
     */
    public static boolean getDanmakuBlockColorful() {
        return Prefers.getBoolean(K_DM_BLOCK_COLORFUL, false);
    }

    public static void setDanmakuBlockColorful(boolean on) {
        Prefers.put(K_DM_BLOCK_COLORFUL, on);
    }

    /**
     * 清屏模式展示弹幕（批三 C 卡 37 补实缺，新键 hongguo_dm_show_in_clean）：
     * 官方 key_show_danmaku_in_immersive 缺省 false——清屏态缺省不显弹幕，
     * 开此项才显。生效点在 HongguoFeedActivity 的显隐闸 danmakuVisibleNow。
     */
    public static boolean getDanmakuShowInClean() {
        return Prefers.getBoolean(K_DM_SHOW_IN_CLEAN, false);
    }

    public static void setDanmakuShowInClean(boolean on) {
        Prefers.put(K_DM_SHOW_IN_CLEAN, on);
    }

    /**
     * 显示区域档下标（批三 C 卡 37 补实缺，新键 hongguo_dm_line_space）：
     * 官方 key_line_space 是 DanmakuLineSpace 枚举 SINGLE/QUARTER/HALF/
     * THREE_QUARTER、缺省 QUARTER；语义＝横屏显示区高度档（官方设置弹层
     * 同一滑杆：竖屏读行数、横屏读此档）。本键存下标 0–3，缺省 1＝QUARTER；
     * 档表与比例在 HgPanelLogic，横屏生效点在 hgDanmakuConfig。
     */
    public static int getDanmakuLineSpaceIndex() {
        return HgPanelLogic.clampDanmakuIndex(
                Prefers.getInt(K_DM_LINE_SPACE, HgPanelLogic.DM_AREA_DEFAULT_INDEX),
                HgPanelLogic.DM_AREA_LABELS.length);
    }

    /** 恢复默认（官方同名动作：清全部七键回缺省，官方即清七键一条路）。 */
    public static void resetDanmakuOptions() {
        Prefers.put(K_DM_OPACITY, HgPanelLogic.DM_OPACITY_DEFAULT);
        Prefers.put(K_DM_SPEED, HgPanelLogic.DM_SPEED_DEFAULT_INDEX);
        Prefers.put(K_DM_LINES, HgPanelLogic.DM_LINES_DEFAULT);
        Prefers.put(K_DM_FONT, HgPanelLogic.DM_FONT_DEFAULT_INDEX);
        Prefers.put(K_DM_BLOCK_COLORFUL, false);
        Prefers.put(K_DM_SHOW_IN_CLEAN, false);
        Prefers.put(K_DM_LINE_SPACE, HgPanelLogic.DM_AREA_DEFAULT_INDEX);
    }

    // ---------- 红果默认倍速（更多面板选定即落此键，起播初始倍速读它） ----------
    // 红果链不经 PlayerManager/PlayerSetting（自有 engine 直起），此前起播
    // 倍速恒为播放器默认 1x；面板倍速是红果自己的持久档（官方按剧缓存
    // cacheVideoSpeed 的自家等价），与星辰全局倍速互不串。

    public static float getSpeed() {
        float v = Prefers.getFloat(K_SPEED, 1f);
        return v > 0 ? v : 1f;
    }

    public static void setSpeed(float speed) {
        if (speed > 0) Prefers.put(K_SPEED, speed);
    }

    // ---------- 定时关闭（更多面板接线，2026-10-30 UI 同步批） ----------
    // 档义走 HgPanelLogic（关/播完本剧/30 分钟/自定义）；计时档在设档
    // 当刻把截止时刻落盘，播放页 tick 到点停播并清档；「播完本剧」无
    // 截止时刻，由播放页在本剧最后一集播完时拦截连播。

    private static final String K_TIMER_MODE = "hongguo_timer_mode";
    private static final String K_TIMER_MINUTES = "hongguo_timer_minutes";
    private static final String K_TIMER_DEADLINE = "hongguo_timer_deadline";

    public static int getTimerMode() {
        return Prefers.getInt(K_TIMER_MODE, HgPanelLogic.TIMER_OFF);
    }

    public static int getTimerMinutes() {
        return Prefers.getInt(K_TIMER_MINUTES, 0);
    }

    /** 设档即重算截止时刻（非计时档清零）。 */
    public static void setTimer(int mode, int minutes) {
        Prefers.put(K_TIMER_MODE, mode);
        Prefers.put(K_TIMER_MINUTES, minutes);
        long delay = HgPanelLogic.timerDelayMs(mode, minutes);
        Prefers.put(K_TIMER_DEADLINE,
                delay > 0 ? System.currentTimeMillis() + delay : 0L);
    }

    public static long getTimerDeadline() {
        return Prefers.getLong(K_TIMER_DEADLINE, 0L);
    }

    public static void clearTimer() {
        Prefers.put(K_TIMER_MODE, HgPanelLogic.TIMER_OFF);
        Prefers.put(K_TIMER_DEADLINE, 0L);
    }

    // ---------- 「启动直达短剧」开关（抽屉第一项，他亲定名） ----------

    public static boolean isLaunchDirect() {
        return Prefers.getBoolean(K_LAUNCH_DIRECT, false);
    }

    public static void setLaunchDirect(boolean on) {
        Prefers.put(K_LAUNCH_DIRECT, on);
    }

    // ---------- 自动连播（他 2026-10-30 21:42 亲令：不给开关、
    // 内置常开；抽屉开关行已整行摘除，连集只管播放页内一集接
    // 一集，刷流不接——流侧分支在 HongguoFeedActivity.onPlayEnded
    // 已按模式分流） ----------

    public static boolean isAutoNext() {
        return true;
    }

    // ---------- 互动本地状态（按剧 key） ----------

    public static boolean isLiked(String seriesId) {
        return contains(K_LIKED, seriesId);
    }

    public static boolean toggleLiked(String seriesId) {
        return toggle(K_LIKED, seriesId);
    }

    /**
     * 追剧架归一迁移（一次性、幂等）：旧 faved 集与 followed 集取并集写入
     * 正源 followed 集，再清空 faved 集并落迁移旗——两边存量一条不丢。
     * 每个追剧架读写入口先调本方法，保证任何面第一次用状态前迁移已完成。
     */
    static synchronized void ensureShelfMigrated() {
        if (shelfMigrated) return;
        shelfMigrated = true;
        if (Prefers.getBoolean(K_SHELF_MIGRATED, false)) return;
        Set<String> union = readSet(K_FOLLOWED);
        union.addAll(readSet(K_FAVED));
        Prefers.put(K_FOLLOWED, String.join(",", union));
        Prefers.put(K_FAVED, "");
        Prefers.put(K_SHELF_MIGRATED, true);
    }

    /** 收藏读口＝追剧架正源（批二归一：收藏与追更同体，isFaved 只是别名）。 */
    public static boolean isFaved(String seriesId) {
        return isFollowed(seriesId);
    }

    /** 收藏翻转口＝追剧架正源翻转（别名，与 toggleFollowed 同一份状态）。 */
    public static boolean toggleFaved(String seriesId) {
        return toggleFollowed(seriesId);
    }

    public static boolean isFollowed(String seriesId) {
        ensureShelfMigrated();
        return contains(K_FOLLOWED, seriesId);
    }

    public static boolean toggleFollowed(String seriesId) {
        ensureShelfMigrated();
        return toggle(K_FOLLOWED, seriesId);
    }

    private static Set<String> readSet(String key) {
        Set<String> out = new LinkedHashSet<>();
        String raw = Prefers.getString(key, "");
        if (raw != null && !raw.isEmpty()) {
            for (String s : raw.split(",")) if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static boolean contains(String key, String id) {
        return id != null && readSet(key).contains(id);
    }

    // ---------- 搜索历史（最近在前，本地落盘；只存词、不带任何账号维度） ----------

    private static final String K_SEARCH_HISTORY = "hongguo_search_history_v1";
    private static final int SEARCH_HISTORY_MAX = 12;

    public static java.util.List<String> searchHistory() {
        java.util.List<String> out = new java.util.ArrayList<>();
        String raw = Prefers.getString(K_SEARCH_HISTORY, "");
        if (raw != null && !raw.isEmpty()) {
            for (String s : raw.split("\n")) {
                String w = s.trim();
                if (!w.isEmpty()) out.add(w);
            }
        }
        return out;
    }

    public static void addSearchHistory(String word) {
        if (word == null) return;
        String w = word.trim();
        if (w.isEmpty()) return;
        java.util.List<String> list = searchHistory();
        list.remove(w);
        list.add(0, w);
        while (list.size() > SEARCH_HISTORY_MAX) list.remove(list.size() - 1);
        Prefers.put(K_SEARCH_HISTORY, String.join("\n", list));
    }

    public static void clearSearchHistory() {
        Prefers.put(K_SEARCH_HISTORY, "");
    }

    // ---------- 互动状态直写（四阶段：远程同步后的镜像落点） ----------

    public static void setLiked(String seriesId, boolean on) {
        set(K_LIKED, seriesId, on);
    }

    /** 收藏直写口＝追剧架正源直写（别名，与 setFollowed 同一份状态）。 */
    public static void setFaved(String seriesId, boolean on) {
        setFollowed(seriesId, on);
    }

    public static void setFollowed(String seriesId, boolean on) {
        ensureShelfMigrated();
        set(K_FOLLOWED, seriesId, on);
    }

    /**
     * 追剧架镜像直写（批二归一为一条写路）：官方收藏与追更同走一个追剧架
     * （bookshelf/video/update）与一套 followed 状态，服务端态回来后只写
     * 正源一份。方法名保留给既有回读点（我的/关注签/收藏签）不改调用形态。
     */
    public static void setShelfMirrored(String seriesId, boolean on) {
        setFollowed(seriesId, on);
    }

    private static void set(String key, String id, boolean on) {
        if (id == null || id.isEmpty()) return;
        Set<String> set = readSet(key);
        if (on) set.add(id);
        else set.remove(id);
        Prefers.put(key, String.join(",", set));
    }

    // ---------- 预约镜像（服务端为准：回包 appointment 态与预约列表回读都往这里对） ----------

    // ---------- 作者关注（key＝作者 uid；主页关注钮与 feed 头像徽标同读） ----------

    public static boolean isAuthorFollowed(String authorUid) {
        return contains(K_FOLLOWED_AUTHORS, authorUid);
    }

    public static void setAuthorFollowed(String authorUid, boolean on) {
        set(K_FOLLOWED_AUTHORS, authorUid, on);
    }

    public static java.util.List<String> authorFollowedIds() {
        return new java.util.ArrayList<>(readSet(K_FOLLOWED_AUTHORS));
    }

    public static boolean isReserved(String seriesId) {
        return contains(K_RESERVED, seriesId);
    }

    public static void setReserved(String seriesId, boolean on) {
        set(K_RESERVED, seriesId, on);
    }

    public static boolean toggleReserved(String seriesId) {
        return toggle(K_RESERVED, seriesId);
    }

    public static java.util.List<String> reservedIds() {
        return new java.util.ArrayList<>(readSet(K_RESERVED));
    }

    /**
     * 预约「已上线」分档集（服务端 is_online=true 回读结果落盘）：子页签
     * 分档以回源为准（官方分档在服务端）；从未回源过时集为空、页面
     * 回落集数推断。登出随账号镜像整清。
     */
    public static java.util.Set<String> reservedOnlineIds() {
        return readSet(K_RESERVED_ONLINE);
    }

    public static void setReservedOnlineIds(java.util.Collection<String> ids) {
        StringBuilder sb = new StringBuilder();
        if (ids != null) for (String id : ids) {
            if (id == null || id.isEmpty()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        Prefers.put(K_RESERVED_ONLINE, sb.toString());
    }

    // ---------- 追更「已看快照」（红点判定基线，HgUpdateLogic.followHasUpdate 用） ----------

    private static final String K_FOLLOW_SEEN_PREFIX = "hongguo_follow_seen_";

    /** 上次进追更面时该剧的最新集数快照；没落过返回 -1。 */
    public static int followSeenCount(String seriesId) {
        if (seriesId == null || seriesId.isEmpty()) return -1;
        return Prefers.getInt(K_FOLLOW_SEEN_PREFIX + seriesId, -1);
    }

    public static void markFollowSeen(String seriesId, int latestCount) {
        if (seriesId == null || seriesId.isEmpty() || latestCount < 0) return;
        Prefers.put(K_FOLLOW_SEEN_PREFIX + seriesId, latestCount);
    }

    // ---------- 预约上线召回弹窗「已提示」集 ----------

    private static final String K_RECALL_PROMPTED = "hongguo_recall_prompted_series";
    private static final String K_RECALL_SEEDED = "hongguo_recall_seeded";

    /** 已弹过（或首轮播种时已在已上线档里）的预约剧集：不再重复弹。 */
    public static java.util.Set<String> recallPromptedIds() {
        return readSet(K_RECALL_PROMPTED);
    }

    public static void addRecallPrompted(java.util.Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        Set<String> set = readSet(K_RECALL_PROMPTED);
        boolean changed = false;
        for (String id : ids) {
            if (id != null && !id.isEmpty()) changed |= set.add(id);
        }
        if (changed) Prefers.put(K_RECALL_PROMPTED, String.join(",", set));
    }

    /**
     * 首轮播种旗：功能上线后第一次拉已上线档时，只把存量记进已提示集
     * 不弹窗——存量已上线不是「新上线」，全弹出来是假提示。此后新进
     * 已上线档的才弹（官方召回弹窗的数据是服务端按新上线下发的，
     * 咱家无此下发，用播种＋差集等价）。
     */
    public static boolean recallSeeded() {
        return Prefers.getBoolean(K_RECALL_SEEDED, false);
    }

    public static void markRecallSeeded() {
        Prefers.put(K_RECALL_SEEDED, true);
    }

    public static java.util.List<String> likedIds() {
        return new java.util.ArrayList<>(readSet(K_LIKED));
    }

    /** 收藏列表源＝追剧架正源（别名，与 followedIds 同一份集）。 */
    public static java.util.List<String> favedIds() {
        return followedIds();
    }

    public static java.util.List<String> followedIds() {
        ensureShelfMigrated();
        return new java.util.ArrayList<>(readSet(K_FOLLOWED));
    }

    /**
     * 登出清理：账号维度的本地镜像（点赞/收藏/关注/预约四套 id 集）整清——
     * 账号数据随账号走，登出后不清、换号登录会串上前一账号的格子（审计
     * 实证）。观看历史/断点是设备维度数据、剧目元信息是公开数据，都不在此列。
     */
    public static void clearAccountMirrors() {
        // 先归一再清：保证追更已看快照按并集后的正源逐个清干净
        ensureShelfMigrated();
        // 追更已看快照随关注集走：先按现行关注集逐个清，再清集本体
        for (String id : readSet(K_FOLLOWED)) {
            Prefers.put(K_FOLLOW_SEEN_PREFIX + id, -1);
        }
        // 预约已提示集是账号维度的（A 号弹过的不能压掉 B 号的新上线）
        Prefers.put(K_RECALL_PROMPTED, "");
        Prefers.put(K_RECALL_SEEDED, false);
        Prefers.put(K_LIKED, "");
        Prefers.put(K_FAVED, "");
        Prefers.put(K_FOLLOWED, "");
        Prefers.put(K_FOLLOWED_AUTHORS, "");
        Prefers.put(K_RESERVED, "");
        Prefers.put(K_RESERVED_ONLINE, "");
    }

    // ---------- 剧目元信息（列表页用：标题/封面/集数，随详情与流条目顺手落） ----------

    private static final String K_META_PREFIX = "hongguo_meta_";
    private static final String SEP = "\u0001";

    public static void putMeta(String seriesId, String title, String cover, int episodeCount) {
        putMeta(seriesId, title, cover, episodeCount, null);
    }

    /** badge＝服务端角标原文（漫剧/AI剧/真人剧等，流条目 tag_info.text）；第四段存，旧三段读法不受影响。badge 为 null 时保留已存的角标不冲掉。 */
    public static void putMeta(String seriesId, String title, String cover, int episodeCount,
                               String badge) {
        if (seriesId == null || seriesId.isEmpty()) return;
        String t = title == null ? "" : title.replace(SEP, " ");
        String c = cover == null ? "" : cover.replace(SEP, " ");
        String b = badge;
        if (b == null) {
            String[] old = metaOf(seriesId);
            b = old != null && old.length > 3 ? old[3] : "";
        }
        Prefers.put(K_META_PREFIX + seriesId,
                t + SEP + c + SEP + episodeCount + SEP + b.replace(SEP, " "));
    }

    /** 返回 {标题, 封面, 集数串, 角标}（角标可能缺省为""）；没存过返回 null。 */
    public static String[] metaOf(String seriesId) {
        if (seriesId == null || seriesId.isEmpty()) return null;
        String raw = Prefers.getString(K_META_PREFIX + seriesId, "");
        if (raw == null || raw.isEmpty()) return null;
        String[] parts = raw.split(SEP, -1);
        if (parts.length < 3) return null;
        return parts;
    }

    // ---------- 条目类型（历史/收藏海报格的角标与收藏分档用，2026-10-30
    // 海报格批新增）：meta 四段里没有类型段（角标段是 tag_info 营销文案、
    // 不是类型，且现行 putMeta 调用全走四参、角标段恒空），故另立一键
    // 按剧存类型码。写点在条目在手的收藏/起播落盘处（Feed 收藏与进度
    // 落盘、详情收藏）；判不出的不写——无类型码的条目角标不显、收藏
    // 只进「全部」档，不许拿营销角标或内容原值在消费端反推。

    private static final String K_CTYPE_PREFIX = "hongguo_ctype_";

    /** 类型码：真人剧。 */ public static final String TYPE_LIVE = "live";
    /** 类型码：漫剧。 */ public static final String TYPE_COMIC = "comic";
    /** 类型码：AI剧。 */ public static final String TYPE_AI = "ai";
    /** 类型码：合集。 */ public static final String TYPE_ALBUM = "album";
    /** 类型码：短片（无角标、收藏只进「全部」）。 */ public static final String TYPE_CLIP = "clip";
    /** 类型码：未上线预告（状态压过类型，与官方历史格角标同序）。 */
    public static final String TYPE_PREVIEW = "preview";

    /**
     * 由条目判类型码（分家口径与剧场同源：content_category_type 为准，
     * content_type=1004 兜底漫剧；ai_usage_type 作者声明不作数）：未上线
     * 预告＞合集＞短片＞AI剧＞漫剧＞真人剧；判不出返回 null（不落存）。
     */
    public static String typeCodeOf(DramaItem item) {
        if (item == null) return null;
        if (item.hasAppointment()) return TYPE_PREVIEW;
        if (item.kind == DramaItemKind.ALBUM) return TYPE_ALBUM;
        if (item.kind == DramaItemKind.UGC_CLIP) return TYPE_CLIP;
        if (item.isAiDrama()) return TYPE_AI;
        if ("1004".equals(item.contentCategoryType) || item.isMotionComic()) return TYPE_COMIC;
        if ("1".equals(item.contentCategoryType)) return TYPE_LIVE;
        return null;
    }

    /** 落一条类型码；code 为 null 不写（已有值不冲掉）。 */
    public static void putType(String seriesId, String code) {
        if (seriesId == null || seriesId.isEmpty() || code == null) return;
        Prefers.put(K_CTYPE_PREFIX + seriesId, code);
    }

    /** 读类型码；没存过返回 null。 */
    public static String typeOf(String seriesId) {
        if (seriesId == null || seriesId.isEmpty()) return null;
        String v = Prefers.getString(K_CTYPE_PREFIX + seriesId, "");
        return v == null || v.isEmpty() ? null : v;
    }

    /** 类型码→角标文案（官方历史/收藏格右上角标原词）；短片与未知不显角标。 */
    public static String badgeTextOf(String typeCode) {
        if (typeCode == null) return null;
        switch (typeCode) {
            case TYPE_LIVE: return "真人剧";
            case TYPE_COMIC: return "漫剧";
            case TYPE_AI: return "AI剧";
            case TYPE_ALBUM: return "合集";
            case TYPE_PREVIEW: return "预告";
            default: return null;
        }
    }

    /** 删除一条观看历史（断点+索引一并摘掉，我的页编辑模式用）。 */
    public static void removeHistory(String seriesId) {
        if (seriesId == null || seriesId.isEmpty()) return;
        Prefers.put(K_HIST_PREFIX + seriesId, "");
        java.util.List<String> ids = new java.util.ArrayList<>(readSet(K_HIST_INDEX));
        if (ids.remove(seriesId)) {
            Prefers.put(K_HIST_INDEX, String.join(",", ids));
        }
    }

    /** 切换成员状态，返回切换后的状态。 */
    private static boolean toggle(String key, String id) {
        if (id == null || id.isEmpty()) return false;
        Set<String> set = readSet(key);
        boolean now;
        if (set.contains(id)) {
            set.remove(id);
            now = false;
        } else {
            set.add(id);
            now = true;
        }
        Prefers.put(key, String.join(",", set));
        return now;
    }

    // ---------- 观看断点 ----------

    /** 一部剧的断点：集序号（1 起）＋集内位置毫秒＋最后观看时刻。 */
    public static final class Progress {
        public final int episodeIndex;
        public final long positionMs;
        public final long updatedMs;

        Progress(int episodeIndex, long positionMs, long updatedMs) {
            this.episodeIndex = episodeIndex;
            this.positionMs = positionMs;
            this.updatedMs = updatedMs;
        }
    }

    private static final String K_HIST_INDEX = "hongguo_hist_index";

    /** 只写断点本体、不进历史索引/最近位：发现流短片的集内进度落盘用。 */
    public static void saveProgressQuiet(String seriesId, int episodeIndex, long positionMs) {
        if (seriesId == null || seriesId.isEmpty()) return;
        Prefers.put(K_HIST_PREFIX + seriesId,
                episodeIndex + "|" + Math.max(0, positionMs) + "|" + System.currentTimeMillis());
    }

    /**
     * 服务端历史导入（「我的」历史格的同步源落点）：本地已有断点的剧
     * 一律不动——本地断点集数/秒数是续播的准，服务端只补本地没有的剧
     * （在官方 App/他端看过的），断点按服务端值写、时刻用服务端
     * read_timestamp，并按时刻序插入历史索引。返回是否真导入了新剧。
     */
    public static boolean importServerHistory(String seriesId, int episodeIndex,
                                              long positionMs, long readTsMs) {
        if (seriesId == null || seriesId.isEmpty()) return false;
        if (progressOf(seriesId) != null) return false;
        long ts = readTsMs > 0 ? readTsMs : System.currentTimeMillis();
        Prefers.put(K_HIST_PREFIX + seriesId,
                Math.max(0, episodeIndex) + "|" + Math.max(0, positionMs) + "|" + ts);
        java.util.List<String> ids = new java.util.ArrayList<>(readSet(K_HIST_INDEX));
        ids.remove(seriesId);
        int at = ids.size();
        for (int i = 0; i < ids.size(); i++) {
            Progress p = progressOf(ids.get(i));
            if (p != null && p.updatedMs < ts) {
                at = i;
                break;
            }
        }
        ids.add(at, seriesId);
        if (ids.size() > 200) ids = ids.subList(0, 200);
        Prefers.put(K_HIST_INDEX, String.join(",", ids));
        return true;
    }

    // ---------- 发现流位置（进程被杀后重进落位用） ----------

    private static final String K_FEED_POS = "hongguo_feed_pos";

    /** 发现流位置落盘：页签名＋流内下标，单值覆盖（只记最近一次浏览面）。 */
    public static void saveFeedPos(String tabName, int index) {
        if (tabName == null) return;
        Prefers.put(K_FEED_POS, tabName + "|" + Math.max(0, index));
    }

    /** 上次发现流所在页签名；没存过返回 null。 */
    public static String feedPosTab() {
        String raw = Prefers.getString(K_FEED_POS, "");
        if (raw == null || !raw.contains("|")) return null;
        return raw.substring(0, raw.indexOf('|'));
    }

    /** 某页签上次的流内下标；页签不符或没存过返回 -1。 */
    public static int feedPosIndex(String tabName) {
        String raw = Prefers.getString(K_FEED_POS, "");
        if (raw == null || tabName == null || !raw.startsWith(tabName + "|")) return -1;
        try {
            return Integer.parseInt(raw.substring(tabName.length() + 1));
        } catch (Exception e) {
            return -1;
        }
    }

    public static void saveProgress(String seriesId, int episodeIndex, long positionMs) {
        if (seriesId == null || seriesId.isEmpty()) return;
        Prefers.put(K_HIST_PREFIX + seriesId,
                episodeIndex + "|" + Math.max(0, positionMs) + "|" + System.currentTimeMillis());
        touchHistIndex(seriesId);
    }

    /** 历史索引：最近观看在前，逗号串存（与互动集合同格式），上限 200 部。 */
    private static void touchHistIndex(String seriesId) {
        java.util.List<String> ids = new java.util.ArrayList<>(readSet(K_HIST_INDEX));
        ids.remove(seriesId);
        ids.add(0, seriesId);
        if (ids.size() > 200) ids = ids.subList(0, 200);
        Prefers.put(K_HIST_INDEX, String.join(",", ids));
    }

    /** 一条观看历史（历史页用）：断点＋元信息齐。 */
    public static final class HistoryRecord {
        public final String seriesId;
        public final int episodeIndex;
        public final long positionMs;
        public final long updatedMs;
        public final String title;
        public final String cover;
        public final int episodeCount;

        HistoryRecord(String seriesId, Progress p, String[] meta) {
            this.seriesId = seriesId;
            this.episodeIndex = p.episodeIndex;
            this.positionMs = p.positionMs;
            this.updatedMs = p.updatedMs;
            this.title = meta == null ? null : meta[0];
            this.cover = meta == null ? null : meta[1];
            int count = -1;
            if (meta != null) {
                try {
                    count = Integer.parseInt(meta[2]);
                } catch (Exception ignored) {
                }
            }
            this.episodeCount = count;
        }
    }

    /** 全部观看历史（最近在前）；断点丢了的条目自动跳过。 */
    public static java.util.List<HistoryRecord> historyRecords() {
        java.util.List<HistoryRecord> out = new java.util.ArrayList<>();
        for (String id : readSet(K_HIST_INDEX)) {
            Progress p = progressOf(id);
            if (p != null) out.add(new HistoryRecord(id, p, metaOf(id)));
        }
        return out;
    }

    /** 取某剧断点；没有返回 null。历史只留最近位置，不做列表（列表面在后续阶段）。 */
    public static Progress progressOf(String seriesId) {
        if (seriesId == null || seriesId.isEmpty()) return null;
        String raw = Prefers.getString(K_HIST_PREFIX + seriesId, "");
        if (raw == null || raw.isEmpty()) return null;
        String[] parts = raw.split("\\|");
        try {
            int ep = Integer.parseInt(parts[0]);
            long pos = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
            long upd = parts.length > 2 ? Long.parseLong(parts[2]) : 0;
            return new Progress(ep, pos, upd);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 小说阅读断点（与短剧历史分家：独立命名空间 hongguo_novel_*，
    // 短剧 hongguo_hist_/hongguo_meta_ 的 id 生态与 book_id 同源、数值可能相撞，
    // 混用会串扰续播位，故另立一套，互不读写） ----------

    private static final String K_NOVEL_HIST_PREFIX = "hongguo_novel_hist_";
    private static final String K_NOVEL_HIST_INDEX = "hongguo_novel_hist_index";
    private static final String K_NOVEL_META_PREFIX = "hongguo_novel_meta_";

    /** 一条小说断点：读到的章下标＋该章 item_id＋落盘时刻。 */
    public static final class NovelMark {
        public final int chapterIndex;
        public final String itemId;
        public final long updatedMs;

        NovelMark(int chapterIndex, String itemId, long updatedMs) {
            this.chapterIndex = chapterIndex;
            this.itemId = itemId;
            this.updatedMs = updatedMs;
        }
    }

    /** 落一本书的阅读断点并顶到小说历史索引最前（上限 200 本）。 */
    public static void saveNovelMark(String bookId, int chapterIndex, String itemId) {
        if (bookId == null || bookId.isEmpty()) return;
        Prefers.put(K_NOVEL_HIST_PREFIX + bookId,
                Math.max(0, chapterIndex) + "|" + (itemId == null ? "" : itemId)
                        + "|" + System.currentTimeMillis());
        java.util.List<String> ids = new java.util.ArrayList<>(readSet(K_NOVEL_HIST_INDEX));
        ids.remove(bookId);
        ids.add(0, bookId);
        if (ids.size() > 200) ids = ids.subList(0, 200);
        Prefers.put(K_NOVEL_HIST_INDEX, String.join(",", ids));
    }

    /** 取一本书的阅读断点；没读过返回 null。 */
    public static NovelMark novelMarkOf(String bookId) {
        if (bookId == null || bookId.isEmpty()) return null;
        String raw = Prefers.getString(K_NOVEL_HIST_PREFIX + bookId, "");
        if (raw == null || raw.isEmpty()) return null;
        String[] parts = raw.split("\\|", -1);
        try {
            int ix = Integer.parseInt(parts[0]);
            String item = parts.length > 1 ? parts[1] : "";
            long upd = parts.length > 2 ? Long.parseLong(parts[2]) : 0;
            return new NovelMark(ix, item, upd);
        } catch (Exception e) {
            return null;
        }
    }

    /** 小说历史索引（最近读的在前）。 */
    public static java.util.List<String> novelHistoryIds() {
        return new java.util.ArrayList<>(readSet(K_NOVEL_HIST_INDEX));
    }

    /**
     * 删除一本小说的全部本地痕迹（断点＋元信息＋历史索引，D-20/D-09）：
     * 小说历史键组此前只进不出、无任何删除 API。删除入口形态属
     * W-05——他已裁要给，入口在 HongguoMineEditActivity 小说支
     * （与五模式同一套编辑/长按/确认口径），删除落本方法。
     */
    public static void removeNovelHistory(String bookId) {
        if (bookId == null || bookId.isEmpty()) return;
        Prefers.put(K_NOVEL_HIST_PREFIX + bookId, "");
        Prefers.put(K_NOVEL_META_PREFIX + bookId, "");
        java.util.List<String> ids = new java.util.ArrayList<>(readSet(K_NOVEL_HIST_INDEX));
        if (ids.remove(bookId)) {
            Prefers.put(K_NOVEL_HIST_INDEX, String.join(",", ids));
        }
    }

    /** 落一本书的展示元信息（标题/作者/封面，\u0001 分隔，与剧目 meta 同法）。 */
    public static void putNovelMeta(String bookId, String title, String author, String cover) {
        if (bookId == null || bookId.isEmpty()) return;
        Prefers.put(K_NOVEL_META_PREFIX + bookId,
                cleanSep(title) + SEP + cleanSep(author) + SEP + cleanSep(cover));
    }

    /** 返回 {标题, 作者, 封面}；没存过返回 null。 */
    public static String[] novelMetaOf(String bookId) {
        if (bookId == null || bookId.isEmpty()) return null;
        String raw = Prefers.getString(K_NOVEL_META_PREFIX + bookId, "");
        if (raw == null || raw.isEmpty()) return null;
        String[] parts = raw.split(SEP, -1);
        return parts.length < 3 ? null : parts;
    }

    private static String cleanSep(String s) {
        return s == null ? "" : s.replace(SEP, " ");
    }
}
