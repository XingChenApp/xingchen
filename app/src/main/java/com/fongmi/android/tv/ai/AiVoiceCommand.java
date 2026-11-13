package com.fongmi.android.tv.ai;

import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 语音指令本地解析。
 * 毫秒级响应，不走网络。未命中返回 null，交由 AI 意图理解。
 *
 * ============ 完整指令清单 ============
 * 【播放控制】（仅播放页有效）
 *   next            下一集 / 下集
 *   prev            上一集 / 上集
 *   toggle_play     暂停 / 播放 / 继续播放
 *   seek_by         快进X秒 / 快退X秒 / 前进 / 后退
 *   volume_up       音量大 / 大声点 / 调大音量
 *   volume_down     音量小 / 小声点 / 调小音量
 *   speed_up        加速播放 / 倍速加快
 *   speed_down      减速播放 / 倍速减慢
 *   speed_set       2倍速 / 1.5倍速（param=倍速*100）
 *   bright_up       亮一点 / 调亮屏幕
 *   bright_down     暗一点 / 调暗屏幕
 *   danmaku_toggle  弹幕开关 / 打开弹幕 / 关闭弹幕（param:1=开,0=关,-1=切换）
 *   subtitle_toggle 字幕开关 / 打开字幕 / 关闭字幕（param:1=开,0=关,-1=切换）
 *   episode_set     第5集 / 播放第5集（param=集数）
 *   cast            投屏
 *   dl_episode      下载本集 / 缓存本集
 *   fullscreen      全屏 / 全屏播放
 *   exit_fullscreen 退出全屏
 *
 * 【全局导航】（任何页面都能用）
 *   nav_home        首页 / 回首页
 *   nav_live        直播
 *   nav_miniapp     小程序
 *   nav_search      搜索XXX（text=关键词）
 *   nav_history     播放历史 / 历史记录
 *   nav_keep        收藏 / 我的收藏
 *   nav_bookshelf   书架 / 打开书架 / 我的书架
 *   nav_continue_reading 继续读 / 继续看书
 *   nav_booksource  书源 / 打开书源 / 书源管理 / 导入书源
 *   reading_bookmark 书签 / 我的书签 / 打开书签（阅读器内打开书签列表）
 *   reading_changesource 换源 / 换书源 / 切换书源（阅读器内换源）
 *   nav_download    下载管理 / 我的下载
 *   nav_plugin      插件管理
 *   nav_cloud       网盘 / 云盘
 *   nav_settings    设置 / 打开设置
 *   nav_features    个性功能
 *   nav_lab         打开实验室
 *   nav_ai_settings AI设置
 *   nav_player_set  播放器设置
 *   nav_ui_set      界面设置
 *   nav_py          打开指定PY（由独立任务实现，此处预留）
 *
 * 【全局开关】（任何页面都能用）
 *   doh_next        切换DoH / 换DNS
 *   float_on        打开悬浮窗 / 显示悬浮球
 *   float_off       关闭悬浮窗 / 隐藏悬浮球
 *   py_refresh      刷新PY
 *
 * 【音乐】（全局，音乐在播时生效）
 *   music_toggle    音乐暂停 / 暂停音乐 / 音乐继续 / 继续音乐 / 播放音乐
 *   music_next      下一首 / 音乐下一首
 *   music_prev      上一首 / 音乐上一首
 *   music_search    搜歌XXX / 找歌XXX（text=关键词，进音乐搜索页）
 *   music_source    切换音乐源 / 换音乐源（源层：轮换主源）
 *   music_quality   切换音质 / 换音质（源层：128k/320k/FLAC/母带轮换）
 * ======================================
 */
public class AiVoiceCommand {

    // ---- 播放控制 ----
    public static final String ACTION_NEXT = "next";
    public static final String ACTION_PREV = "prev";
    public static final String ACTION_TOGGLE_PLAY = "toggle_play";
    public static final String ACTION_SEEK_BY = "seek_by";
    public static final String ACTION_VOLUME_UP = "volume_up";
    public static final String ACTION_VOLUME_DOWN = "volume_down";
    public static final String ACTION_SPEED_UP = "speed_up";
    public static final String ACTION_SPEED_DOWN = "speed_down";
    public static final String ACTION_SPEED_SET = "speed_set";
    public static final String ACTION_BRIGHT_UP = "bright_up";
    public static final String ACTION_BRIGHT_DOWN = "bright_down";
    public static final String ACTION_DANMAKU = "danmaku_toggle";
    public static final String ACTION_SUBTITLE = "subtitle_toggle";
    public static final String ACTION_AI_SUBTITLE = "ai_subtitle_toggle";
    public static final String ACTION_EPISODE = "episode_set";
    public static final String ACTION_CAST = "cast";
    public static final String ACTION_DL_EPISODE = "dl_episode";
    public static final String ACTION_FULLSCREEN = "fullscreen";
    public static final String ACTION_EXIT_FULLSCREEN = "exit_fullscreen";

    // ---- 全局导航 ----
    public static final String ACTION_NAV_HOME = "nav_home";
    public static final String ACTION_NAV_LIVE = "nav_live";
    public static final String ACTION_NAV_MINIAPP = "nav_miniapp";
    public static final String ACTION_NAV_SEARCH = "nav_search";
    public static final String ACTION_NAV_HISTORY = "nav_history";
    public static final String ACTION_NAV_KEEP = "nav_keep";
    public static final String ACTION_NAV_BOOKSHELF = "nav_bookshelf";
    public static final String ACTION_NAV_CONTINUE_READING = "nav_continue_reading";
    public static final String ACTION_NAV_BOOKSOURCE = "nav_booksource";
    public static final String ACTION_READING_BOOKMARK = "reading_bookmark";
    public static final String ACTION_READING_CHANGESOURCE = "reading_changesource";
    public static final String ACTION_NAV_DOWNLOAD = "nav_download";
    public static final String ACTION_NAV_PLUGIN = "nav_plugin";
    public static final String ACTION_NAV_CLOUD = "nav_cloud";
    public static final String ACTION_NAV_SETTINGS = "nav_settings";
    public static final String ACTION_NAV_FEATURES = "nav_features";
    public static final String ACTION_NAV_AI_SETTINGS = "nav_ai_settings";
    public static final String ACTION_NAV_PLAYER_SET = "nav_player_set";
    public static final String ACTION_NAV_UI_SET = "nav_ui_set";
    public static final String ACTION_NAV_NODE = "nav_node";
    public static final String ACTION_NAV_LAB = "nav_lab";
    public static final String ACTION_NAV_NODE_TEST = "nav_node_test";

    // ---- 全局开关 ----
    public static final String ACTION_DOH_NEXT = "doh_next";
    public static final String ACTION_FLOAT_ON = "float_on";
    public static final String ACTION_FLOAT_OFF = "float_off";
    public static final String ACTION_PY_REFRESH = "py_refresh";
    public static final String ACTION_NODE_ON = "node_on";
    public static final String ACTION_NODE_OFF = "node_off";

    // ---- 音乐 ----
    // ---- 音乐源（源层能力，由源实现者声明） ----

    private static final Pattern SEEK_PATTERN = Pattern.compile("(快进|快退|前进|后退)\\s*(\\d+)\\s*秒");
    private static final Pattern SEEK_PATTERN2 = Pattern.compile("(\\d+)\\s*秒.*(快进|前进)");
    private static final Pattern SEEK_BACK_PATTERN2 = Pattern.compile("(\\d+)\\s*秒.*(快退|后退)");
    private static final Pattern SPEED_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*倍速");
    private static final Pattern EPISODE_PATTERN = Pattern.compile("(?:播放|跳到|切到)?\\s*第\\s*(\\d+)\\s*集");

    public static class Command {
        public final String action;
        public final long param;
        public final String textParam;
        public final String feedback;

        public Command(String action, long param, String feedback) {
            this(action, param, null, feedback);
        }

        public Command(String action, String textParam, String feedback) {
            this(action, 0, textParam, feedback);
        }

        public Command(String action, long param, String textParam, String feedback) {
            this.action = action;
            this.param = param;
            this.textParam = textParam;
            this.feedback = feedback;
        }

        public Command(String action, String feedback) {
            this(action, 0, null, feedback);
        }
    }

    /** 本地解析，命中返回 Command，未命中返回 null */
    public static Command parse(String text) {
        if (TextUtils.isEmpty(text)) return null;
        String t = text.trim().replaceAll("[，。！？、]", "");

        // ---------- 播放控制 ----------
        if (t.contains("下一集") || t.equals("下集") || t.contains("下集播")
                || t.contains("下一个") && t.contains("集")) {
            return new Command(ACTION_NEXT, "已切换下一集");
        }
        if (t.contains("上一集") || t.equals("上集") || t.contains("上集播")
                || t.contains("上一个") && t.contains("集")) {
            return new Command(ACTION_PREV, "已切换上一集");
        }
        // 播放第X集
        Matcher ep = EPISODE_PATTERN.matcher(t);
        if (ep.find() && !t.contains("下一集") && !t.contains("上一集")) {
            long n = safeParseLong(ep.group(1), 0);
            if (n > 0) return new Command(ACTION_EPISODE, n, "正在播放第 " + n + " 集");
        }
        // 暂停 / 播放
        if (t.equals("暂停") || t.contains("暂停播放") || t.contains("暂停一下")) {
            return new Command(ACTION_TOGGLE_PLAY, "已暂停");
        }
        if ((t.equals("播放") || t.contains("继续播放") || t.contains("继续播"))
                && !t.contains("暂停")) {
            return new Command(ACTION_TOGGLE_PLAY, "已播放");
        }
        // 快进 / 快退 X 秒
        Matcher m = SEEK_PATTERN.matcher(t);
        if (m.find()) {
            boolean forward = m.group(1).contains("快进") || m.group(1).contains("前进");
            long sec = safeParseLong(m.group(2), 10);
            return new Command(ACTION_SEEK_BY, forward ? sec * 1000 : -sec * 1000,
                    forward ? "已快进 " + sec + " 秒" : "已快退 " + sec + " 秒");
        }
        m = SEEK_PATTERN2.matcher(t);
        if (m.find()) {
            long sec = safeParseLong(m.group(1), 10);
            return new Command(ACTION_SEEK_BY, sec * 1000, "已快进 " + sec + " 秒");
        }
        m = SEEK_BACK_PATTERN2.matcher(t);
        if (m.find()) {
            long sec = safeParseLong(m.group(1), 10);
            return new Command(ACTION_SEEK_BY, -sec * 1000, "已快退 " + sec + " 秒");
        }
        // 倍速：2倍速 / 1.5倍速
        m = SPEED_PATTERN.matcher(t);
        if (m.find()) {
            try {
                float s = Float.parseFloat(m.group(1));
                if (s >= 0.25f && s <= 5f) {
                    return new Command(ACTION_SPEED_SET, (long) (s * 100), "已设为 " + m.group(1) + " 倍速");
                }
            } catch (Exception ignored) {
            }
        }
        // 倍速加快 / 减慢
        if (t.contains("加速播放") || t.contains("倍速加快") || t.contains("加快倍速")
                || (t.contains("加快") && t.contains("速度"))) {
            return new Command(ACTION_SPEED_UP, "倍速已加快");
        }
        if (t.contains("减速播放") || t.contains("倍速减慢") || t.contains("减慢倍速")
                || (t.contains("减慢") && t.contains("速度"))) {
            return new Command(ACTION_SPEED_DOWN, "倍速已减慢");
        }
        // 音量
        if (t.contains("音量大") || t.contains("大声点") || t.contains("声音大")
                || t.equals("调大音量") || t.contains("音量调大")) {
            return new Command(ACTION_VOLUME_UP, "音量已调大");
        }
        if (t.contains("音量小") || t.contains("小声点") || t.contains("声音小")
                || t.equals("调小音量") || t.contains("音量调小")) {
            return new Command(ACTION_VOLUME_DOWN, "音量已调小");
        }
        // 亮度
        if (t.contains("亮一点") || t.contains("调亮") || t.contains("屏幕亮")) {
            return new Command(ACTION_BRIGHT_UP, "屏幕已调亮");
        }
        if (t.contains("暗一点") || t.contains("调暗") || t.contains("屏幕暗")) {
            return new Command(ACTION_BRIGHT_DOWN, "屏幕已调暗");
        }
        // 弹幕开关（param: 1=开，0=关，-1=切换）
        if (t.contains("关闭弹幕") || t.contains("关掉弹幕") || t.contains("隐藏弹幕")) {
            return new Command(ACTION_DANMAKU, 0, "弹幕已关闭");
        }
        if (t.contains("打开弹幕") || t.contains("显示弹幕")) {
            return new Command(ACTION_DANMAKU, 1, "弹幕已打开");
        }
        if (t.equals("弹幕") || t.contains("弹幕开关") || t.contains("切换弹幕")) {
            return new Command(ACTION_DANMAKU, -1, "已切换弹幕");
        }
        // 字幕开关（param: 1=开，0=关，-1=切换）
        if (t.contains("关闭字幕") || t.contains("关掉字幕") || t.contains("隐藏字幕")) {
            return new Command(ACTION_SUBTITLE, 0, "字幕已关闭");
        }
        if (t.contains("打开字幕") || t.contains("显示字幕")) {
            return new Command(ACTION_SUBTITLE, 1, "字幕已打开");
        }
        if (t.equals("字幕") || t.contains("字幕开关") || t.contains("切换字幕")) {
            return new Command(ACTION_SUBTITLE, -1, "已切换字幕");
        }
        // AI 本地字幕（param: 1=开，0=关，-1=切换）
        if (t.contains("AI字幕") || t.contains("ai字幕") || t.contains("Ai字幕")) {
            if (t.contains("关闭") || t.contains("关掉") || t.contains("隐藏")) {
                return new Command(ACTION_AI_SUBTITLE, 0, "AI 字幕已关闭");
            }
            if (t.contains("打开") || t.contains("显示") || t.contains("开启")) {
                return new Command(ACTION_AI_SUBTITLE, 1, "AI 字幕已开启");
            }
            return new Command(ACTION_AI_SUBTITLE, -1, "已切换 AI 字幕");
        }
        if (t.contains("本地字幕")) {
            if (t.contains("关闭") || t.contains("关掉")) {
                return new Command(ACTION_AI_SUBTITLE, 0, "AI 字幕已关闭");
            }
            return new Command(ACTION_AI_SUBTITLE, -1, "已切换 AI 字幕");
        }
        // 投屏
        if (t.contains("投屏")) {
            return new Command(ACTION_CAST, "正在打开投屏");
        }
        // 下载本集
        if (t.contains("下载本集") || t.contains("缓存本集") || t.contains("下载这集")
                || t.contains("缓存这集")) {
            return new Command(ACTION_DL_EPISODE, "开始下载本集");
        }
        // 全屏
        if (t.contains("退出全屏") || t.contains("取消全屏")) {
            return new Command(ACTION_EXIT_FULLSCREEN, "已退出全屏");
        }
        if (t.equals("全屏") || t.contains("全屏播放") || t.contains("进入全屏")) {
            return new Command(ACTION_FULLSCREEN, "已全屏");
        }

        // ---------- 全局开关 ----------
        if (t.contains("切换DoH") || t.contains("切换doh") || t.contains("换DNS")
                || t.contains("切换DNS") || (t.contains("DoH") && t.contains("切换"))) {
            return new Command(ACTION_DOH_NEXT, "已切换 DoH");
        }
        if (t.contains("关闭悬浮窗") || t.contains("隐藏悬浮球") || t.contains("关闭悬浮球")
                || t.contains("悬浮窗关掉")) {
            return new Command(ACTION_FLOAT_OFF, "悬浮窗已关闭");
        }
        if (t.contains("打开悬浮窗") || t.contains("显示悬浮球") || t.contains("打开悬浮球")) {
            return new Command(ACTION_FLOAT_ON, "悬浮窗已打开");
        }
        if (t.contains("刷新PY") || t.contains("刷新py") || t.contains("重载PY")
                || t.contains("重新加载PY")) {
            return new Command(ACTION_PY_REFRESH, "正在刷新 PY");
        }

        // ---------- 全局导航 ----------
        if (t.contains("AI设置") || t.contains("AI 设置")) {
            return new Command(ACTION_NAV_AI_SETTINGS, "已打开 AI 设置");
        }
        if (t.contains("播放器设置") || t.contains("播放设置")) {
            return new Command(ACTION_NAV_PLAYER_SET, "已打开播放器设置");
        }
        // 界面设置：赛博朋克/主题切换已取消，"换UI""换主题"类触发词摘掉，只留"界面设置"进外观设置页
        if (t.contains("界面设置")) {
            return new Command(ACTION_NAV_UI_SET, "已打开界面设置");
        }
        if (t.equals("设置") || t.contains("打开设置") || t.contains("进入设置")
                || t.contains("系统设置")) {
            return new Command(ACTION_NAV_SETTINGS, "已打开设置");
        }
        if (t.contains("个性功能")) {
            return new Command(ACTION_NAV_FEATURES, "已打开个性功能");
        }
        // 测延迟（2026-10-15 他要求节点列表加闪电测延迟，口令同步）：先进节点列表页自动开测，
        // 要放在代理开关判定之前，避免"测节点"类说法被其它分支吞掉。
        if (t.contains("测延迟") || t.contains("节点测速") || t.contains("测节点")) {
            return new Command(ACTION_NAV_NODE_TEST, "正在测延迟");
        }
        // 代理（原名节点，2026-10-15 他把入口改名"代理"；新旧说法都认。
        // 先判管理页，再判开关，避免"打开代理管理"被"打开代理"吞掉）
        if (t.contains("节点管理") || t.contains("节点设置")
                || t.contains("代理管理") || t.contains("代理设置")) {
            return new Command(ACTION_NAV_NODE, "已打开代理");
        }
        if (t.contains("关闭节点") || t.contains("关节点") || t.contains("停掉节点")
                || t.contains("关闭代理") || t.contains("关代理") || t.contains("停掉代理")) {
            return new Command(ACTION_NODE_OFF, "代理已关闭");
        }
        if (t.contains("打开节点") || t.contains("开启节点") || t.contains("开节点")
                || t.contains("启动节点")
                || t.contains("打开代理") || t.contains("开启代理") || t.contains("开代理")
                || t.contains("启动代理")) {
            return new Command(ACTION_NODE_ON, "正在开启代理");
        }
        if (t.contains("插件管理") || t.contains("打开插件")) {
            return new Command(ACTION_NAV_PLUGIN, "已打开插件管理");
        }
        // 实验室（新功能必注册口令，老规矩）
        if (t.contains("打开实验室") || t.equals("实验室")) {
            return new Command(ACTION_NAV_LAB, "已打开实验室");
        }
        if (t.contains("网盘") || t.contains("云盘")) {
            return new Command(ACTION_NAV_CLOUD, "已打开网盘");
        }
        if (t.contains("下载管理") || t.contains("我的下载") || t.contains("打开下载")) {
            return new Command(ACTION_NAV_DOWNLOAD, "已打开下载管理");
        }
        if (t.contains("我的收藏") || t.contains("打开收藏") || t.equals("收藏")) {
            return new Command(ACTION_NAV_KEEP, "已打开收藏");
        }
        if (t.contains("书签") || t.contains("我的书签") || t.contains("打开书签")) {
            return new Command(ACTION_READING_BOOKMARK, "已打开书签");
        }
        if (t.contains("换源") || t.contains("换书源") || t.contains("切换书源")) {
            return new Command(ACTION_READING_CHANGESOURCE, "正在换源");
        }
        if (t.contains("继续读") || t.contains("继续看书")) {
            return new Command(ACTION_NAV_CONTINUE_READING, "已继续阅读");
        }
        if (t.contains("书架") || t.contains("打开书架") || t.contains("我的书架")) {
            return new Command(ACTION_NAV_BOOKSHELF, "已打开书架");
        }
        if (t.contains("导入书源")) {
            return new Command(ACTION_NAV_BOOKSOURCE, "import", "已打开书源导入");
        }
        if (t.contains("书源管理") || t.contains("打开书源") || t.equals("书源")) {
            return new Command(ACTION_NAV_BOOKSOURCE, "已打开书源");
        }
        if (t.contains("直播")) {
            return new Command(ACTION_NAV_LIVE, "已打开直播");
        }
        if (t.equals("首页") || t.contains("回首页") || t.contains("回到首页")
                || t.contains("返回首页")) {
            return new Command(ACTION_NAV_HOME, "已回到首页");
        }
        if (t.contains("点播")) {
            return new Command(ACTION_NAV_HOME, "已回到点播");
        }
        if (t.contains("小程序")) {
            return new Command(ACTION_NAV_MINIAPP, "已打开小程序");
        }
        if (t.startsWith("搜索") && t.length() > 2) {
            String kw = t.substring(2).trim();
            if (!kw.isEmpty()) {
                return new Command(ACTION_NAV_SEARCH, kw, "正在搜索 " + kw);
            }
        }
        if ((t.startsWith("搜一下") || t.startsWith("搜下")) && t.length() > 3) {
            String kw = t.replaceFirst("^搜[一]?下", "").trim();
            if (!kw.isEmpty()) {
                return new Command(ACTION_NAV_SEARCH, kw, "正在搜索 " + kw);
            }
        }
        if (t.contains("播放历史") || t.contains("历史记录") || t.contains("观看历史")) {
            return new Command(ACTION_NAV_HISTORY, "已打开播放历史");
        }

        return null;
    }

    private static long safeParseLong(String s, long def) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return def;
        }
    }
}
