package com.fongmi.android.tv.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

import com.fongmi.android.tv.App;

/**
 * AI 动作执行器。
 * 根据当前页面执行语音/文字指令：播放页走 VideoActivity，导航/开关类全局可用，复杂指令走 AI 意图理解。
 */
public class AiActionExecutor {

    public interface Callback {
        void onFeedback(String text);
    }

    /** 本地指令直接执行 */
    public static void executeLocal(Context context, AiVoiceCommand.Command cmd, Callback cb) {
        if (cmd == null) {
            if (cb != null) cb.onFeedback("没听懂，换个说法试试");
            return;
        }
        // 全局导航指令：任何页面都能用
        if (isNavCommand(cmd)) {
            boolean done = navigate(context, cmd);
            if (cb != null) cb.onFeedback(done ? cmd.feedback : "打开失败，请重试");
            return;
        }
        // 全局开关指令：任何页面都能用
        if (isSwitchCommand(cmd)) {
            String feedback = executeSwitch(context, cmd);
            if (cb != null) cb.onFeedback(feedback);
            return;
        }
        // 播放控制指令：需要播放页
        Activity activity = findTopActivity(context);
        boolean done = activity != null && dispatchToActivity(activity, cmd);
        if (cb != null) cb.onFeedback(done ? cmd.feedback : wrongPageHint(cmd));
    }

    /** 落不了地时的页面提示要名副其实（低-08）：阅读类指令说阅读页，其余说播放页。 */
    private static String wrongPageHint(AiVoiceCommand.Command cmd) {
        if (cmd != null && cmd.action != null && cmd.action.startsWith("reading_")) {
            return "请在阅读页面使用该指令";
        }
        return "请在播放页面使用该指令";
    }

    /** 是否为音乐指令 */
    private static Activity topActivity(Context context) {
        Activity a = findTopActivity(context);
        if (a != null) return a;
        return context instanceof Activity ? (Activity) context : null;
    }

    /** 是否为全局导航指令 */
    private static boolean isNavCommand(AiVoiceCommand.Command cmd) {
        return cmd != null && cmd.action != null && cmd.action.startsWith("nav_");
    }

    /** 是否为全局开关指令 */
    private static boolean isSwitchCommand(AiVoiceCommand.Command cmd) {
        if (cmd == null || cmd.action == null) return false;
        String a = cmd.action;
        return a.equals(AiVoiceCommand.ACTION_DOH_NEXT)
                || a.equals(AiVoiceCommand.ACTION_FLOAT_ON)
                || a.equals(AiVoiceCommand.ACTION_FLOAT_OFF)
                || a.equals(AiVoiceCommand.ACTION_NODE_ON)
                || a.equals(AiVoiceCommand.ACTION_NODE_OFF)
                || a.equals(AiVoiceCommand.ACTION_PY_REFRESH);
    }

    /**
     * 全局导航：用 Intent 跳转对应 Activity（Class.forName，因目标在 mobile 源集）。
     * 在任何页面都能用。
     */
    private static boolean navigate(Context context, AiVoiceCommand.Command cmd) {
        try {
            String pkg = "com.fongmi.android.tv.ui.activity.";
            String cls;
            Intent intent;
            switch (cmd.action) {
                case AiVoiceCommand.ACTION_NAV_SETTINGS:
                    cls = pkg + "XingChenSettingsActivity";
                    intent = new Intent(context, Class.forName(cls));
                    break;
                case AiVoiceCommand.ACTION_NAV_FEATURES:
                    cls = pkg + "FeaturesActivity";
                    intent = new Intent(context, Class.forName(cls));
                    break;
                case AiVoiceCommand.ACTION_NAV_NODE:
                    cls = pkg + "NodeManageActivity";
                    intent = new Intent(context, Class.forName(cls));
                    break;
                case AiVoiceCommand.ACTION_NAV_NODE_TEST:
                    cls = pkg + "NodeListActivity";
                    intent = new Intent(context, Class.forName(cls));
                    intent.putExtra("autotest", true);
                    break;
                case AiVoiceCommand.ACTION_NAV_AI_SETTINGS:
                    cls = pkg + "AiSettingsActivity";
                    intent = new Intent(context, Class.forName(cls));
                    break;
                case AiVoiceCommand.ACTION_NAV_PLAYER_SET:
                    cls = pkg + "PlayerSettingsActivity";
                    intent = new Intent(context, Class.forName(cls));
                    break;
                case AiVoiceCommand.ACTION_NAV_UI_SET:
                    cls = pkg + "UiSettingsActivity";
                    intent = new Intent(context, Class.forName(cls));
                    break;
                case AiVoiceCommand.ACTION_NAV_HOME:
                    cls = pkg + "HomeActivity";
                    intent = new Intent(context, Class.forName(cls))
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    break;
                case AiVoiceCommand.ACTION_NAV_LIVE:
                    cls = pkg + "LiveActivity";
                    intent = new Intent(context, Class.forName(cls));
                    break;
                case AiVoiceCommand.ACTION_NAV_MINIAPP:
                    cls = pkg + "HkPageActivity";
                    intent = new Intent(context, Class.forName(cls));
                    break;
                case AiVoiceCommand.ACTION_NAV_SEARCH:
                    cls = pkg + "SearchActivity";
                    intent = new Intent(context, Class.forName(cls));
                    if (!TextUtils.isEmpty(cmd.textParam)) {
                        intent.putExtra("keyword", cmd.textParam);
                    }
                    break;
                case AiVoiceCommand.ACTION_NAV_HISTORY:
                    cls = pkg + "HistoryActivity";
                    intent = new Intent(context, Class.forName(cls));
                    break;
                case AiVoiceCommand.ACTION_NAV_KEEP:
                    cls = pkg + "KeepActivity";
                    intent = new Intent(context, Class.forName(cls));
                    break;
                case AiVoiceCommand.ACTION_NAV_BOOKSHELF:
                    cls = pkg + "ReadingShelfActivity";
                    intent = new Intent(context, Class.forName(cls));
                    break;
                case AiVoiceCommand.ACTION_NAV_CONTINUE_READING:
                    // main 源集调不到 mobile 的 ReadingRouter：经书架页中转（extra 触发继续读）
                    cls = pkg + "ReadingShelfActivity";
                    intent = new Intent(context, Class.forName(cls));
                    intent.putExtra("auto_continue", true);
                    break;
                case AiVoiceCommand.ACTION_NAV_BOOKSOURCE:
                    cls = pkg + "LegadoActivity";
                    intent = new Intent(context, Class.forName(cls));
                    if ("import".equals(cmd.textParam)) {
                        intent.putExtra("auto_import", true);
                    }
                    break;
                case AiVoiceCommand.ACTION_NAV_DOWNLOAD:
                    cls = pkg + "DownloadActivity";
                    intent = new Intent(context, Class.forName(cls));
                    break;
                case AiVoiceCommand.ACTION_NAV_PLUGIN:
                    cls = pkg + "PluginActivity";
                    intent = new Intent(context, Class.forName(cls));
                    break;
                case AiVoiceCommand.ACTION_NAV_LAB:
                    // 实验室在独立包 ui.lab，不走上面的 activity 前缀
                    intent = new Intent(context, Class.forName("com.fongmi.android.tv.ui.lab.LabHomeActivity"));
                    break;
                case AiVoiceCommand.ACTION_NAV_CLOUD:
                    // 网盘入口：网盘登录页（各网盘从此处进入）
                    cls = pkg + "PanLoginActivity";
                    intent = new Intent(context, Class.forName(cls));
                    break;
                default:
                    return false;
            }
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 全局开关指令执行。返回反馈文案。
     */
    private static String executeSwitch(Context context, AiVoiceCommand.Command cmd) {
        try {
            switch (cmd.action) {
                case AiVoiceCommand.ACTION_DOH_NEXT: {
                    int next = (com.fongmi.android.tv.utils.XingChenDoh.getIndex(context) + 1)
                            % com.fongmi.android.tv.utils.XingChenDoh.NAMES.length;
                    com.fongmi.android.tv.utils.XingChenDoh.setIndex(context, next);
                    return "DoH 已切换为" + com.fongmi.android.tv.utils.XingChenDoh.getName(context);
                }
                case AiVoiceCommand.ACTION_FLOAT_ON: {
                    AiStore.setFloatEnabled(context, true);
                    Activity top = findTopActivity(context);
                    if (top != null) AiFloatManager.get().refresh(top);
                    return "悬浮窗已打开";
                }
                case AiVoiceCommand.ACTION_FLOAT_OFF: {
                    AiStore.setFloatEnabled(context, false);
                    Activity top = findTopActivity(context);
                    if (top != null) AiFloatManager.get().refresh(top);
                    return "悬浮窗已关闭";
                }
                case AiVoiceCommand.ACTION_PY_REFRESH: {
                    com.fongmi.android.tv.event.RefreshEvent.home();
                    return "正在刷新首页";
                }
                case AiVoiceCommand.ACTION_NODE_ON: {
                    com.fongmi.android.tv.clash.MihomoManager.get().setEnabled(context, true);
                    return "正在开启节点，内核启动需要几秒";
                }
                case AiVoiceCommand.ACTION_NODE_OFF: {
                    com.fongmi.android.tv.clash.MihomoManager.get().setEnabled(context, false);
                    return "节点已关闭";
                }
                default:
                    return "不支持的操作";
            }
        } catch (Exception e) {
            return "执行失败，请重试";
        }
    }

    /** 复杂指令走 AI 意图理解，返回 JSON {action, params} 后执行 */
    public static void executeViaAi(Context context, AiProvider provider, String text, Callback cb) {
        if (provider == null) {
            if (cb != null) cb.onFeedback("请先在 AI 设置中配置服务商");
            return;
        }
        new Thread(() -> {
            try {
                List<AiClient.Message> msgs = new ArrayList<>();
                msgs.add(AiClient.Message.system(
                        "你是星辰 App 的语音助手。把用户指令解析为 JSON，只输出 JSON，不要解释。\n"
                                + "【播放控制】next（下一集）、prev（上一集）、toggle_play（暂停/播放切换）、"
                                + "seek_by（快进快退，params 为毫秒数，正数快进负数快退）、"
                                + "volume_up（音量+）、volume_down（音量-）、"
                                + "speed_up（倍速加快）、speed_down（倍速减慢）、"
                                + "speed_set（设置倍速，params 为倍速*100，如 150 表示 1.5 倍）、"
                                + "bright_up（屏幕调亮）、bright_down（屏幕调暗）、"
                                + "danmaku_toggle（弹幕开关，params:1=开,0=关,-1=切换）、"
                                + "subtitle_toggle（字幕开关，params:1=开,0=关,-1=切换）、"
                                + "episode_set（播放第N集，params 为集数）、"
                                + "cast（投屏）、dl_episode（下载本集）、"
                                + "fullscreen（全屏）、exit_fullscreen（退出全屏）。\n"
                                + "【全局导航】nav_home（回首页）、nav_live（打开直播）、"
                                + "nav_miniapp（打开小程序）、nav_search（搜索，text 为关键词）、"
                                + "nav_history（播放历史）、nav_keep（收藏）、nav_download（下载管理）、"
                                + "nav_plugin（插件管理）、nav_cloud（网盘）、"
                                + "nav_settings（打开设置）、nav_features（个性功能）、"
                                + "nav_ai_settings（AI 设置）、nav_player_set（播放器设置）、nav_ui_set（界面设置）。\n"
                                + "【全局开关】doh_next（切换 DoH）、float_on（打开悬浮窗）、"
                                + "float_off（关闭悬浮窗）、py_refresh（刷新首页）。\n"
                                + "无法理解时 action 为 none。\n"
                                + "示例：{\"action\":\"seek_by\",\"params\":30000} "
                                + "示例：{\"action\":\"nav_search\",\"text\":\"周星驰\"} "
                                + "示例：{\"action\":\"speed_set\",\"params\":200}"));
                msgs.add(AiClient.Message.user(text));
                AiClient.Options opt = new AiClient.Options();
                opt.temperature = 0.1;
                opt.maxTokens = 128;
                AiClient.ChatResult r = AiClient.chat(provider, msgs, opt);
                AiVoiceCommand.Command cmd = parseAiJson(r.text);
                if (cmd == null) {
                    postFeedback(cb, "没听懂，换个说法试试");
                    return;
                }
                // 分流口径与本地路 executeLocal 完全一致（中-07）：导航→开关→播放页。
                String feedback;
                if (isNavCommand(cmd)) {
                    boolean done = navigate(context, cmd);
                    feedback = done ? cmd.feedback : "打开失败，请重试";
                } else if (isSwitchCommand(cmd)) {
                    feedback = executeSwitch(context, cmd);
                } else {
                    Activity activity = findTopActivity(context);
                    boolean done = activity != null && dispatchToActivity(activity, cmd);
                    feedback = done ? cmd.feedback : wrongPageHint(cmd);
                }
                postFeedback(cb, feedback);
            } catch (Exception e) {
                postFeedback(cb, "AI 调用失败：" + (e.getMessage() == null ? "" : e.getMessage()));
            }
        }).start();
    }

    private static void postFeedback(Callback cb, String text) {
        if (cb == null) return;
        try {
            cb.onFeedback(text);
        } catch (Exception ignored) {
        }
    }

    private static AiVoiceCommand.Command parseAiJson(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        try {
            String json = raw.trim();
            int s = json.indexOf('{');
            int e = json.lastIndexOf('}');
            if (s >= 0 && e > s) json = json.substring(s, e + 1);
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            String action = o.has("action") ? o.get("action").getAsString() : "";
            long params = o.has("params") ? o.get("params").getAsLong() : 0;
            String text = o.has("text") ? o.get("text").getAsString() : "";
            switch (action) {
                // ---- 播放控制 ----
                case AiVoiceCommand.ACTION_NEXT:
                    return new AiVoiceCommand.Command(action, "已切换下一集");
                case AiVoiceCommand.ACTION_PREV:
                    return new AiVoiceCommand.Command(action, "已切换上一集");
                case AiVoiceCommand.ACTION_TOGGLE_PLAY:
                    return new AiVoiceCommand.Command(action, "已切换播放状态");
                case AiVoiceCommand.ACTION_SEEK_BY:
                    return new AiVoiceCommand.Command(action, params,
                            params >= 0 ? "已快进 " + (params / 1000) + " 秒" : "已快退 " + (-params / 1000) + " 秒");
                case AiVoiceCommand.ACTION_VOLUME_UP:
                    return new AiVoiceCommand.Command(action, "音量已调大");
                case AiVoiceCommand.ACTION_VOLUME_DOWN:
                    return new AiVoiceCommand.Command(action, "音量已调小");
                case AiVoiceCommand.ACTION_SPEED_UP:
                    return new AiVoiceCommand.Command(action, "倍速已加快");
                case AiVoiceCommand.ACTION_SPEED_DOWN:
                    return new AiVoiceCommand.Command(action, "倍速已减慢");
                case AiVoiceCommand.ACTION_SPEED_SET:
                    return new AiVoiceCommand.Command(action, params, "已设为 " + (params / 100f) + " 倍速");
                case AiVoiceCommand.ACTION_BRIGHT_UP:
                    return new AiVoiceCommand.Command(action, "屏幕已调亮");
                case AiVoiceCommand.ACTION_BRIGHT_DOWN:
                    return new AiVoiceCommand.Command(action, "屏幕已调暗");
                case AiVoiceCommand.ACTION_DANMAKU:
                    return new AiVoiceCommand.Command(action, params, "已切换弹幕");
                case AiVoiceCommand.ACTION_SUBTITLE:
                    return new AiVoiceCommand.Command(action, params, "已切换字幕");
                case AiVoiceCommand.ACTION_EPISODE:
                    return new AiVoiceCommand.Command(action, params, "正在播放第 " + params + " 集");
                case AiVoiceCommand.ACTION_CAST:
                    return new AiVoiceCommand.Command(action, "正在打开投屏");
                case AiVoiceCommand.ACTION_DL_EPISODE:
                    return new AiVoiceCommand.Command(action, "开始下载本集");
                case AiVoiceCommand.ACTION_FULLSCREEN:
                    return new AiVoiceCommand.Command(action, "已全屏");
                case AiVoiceCommand.ACTION_EXIT_FULLSCREEN:
                    return new AiVoiceCommand.Command(action, "已退出全屏");
                // ---- 全局导航 ----
                case AiVoiceCommand.ACTION_NAV_HOME:
                    return new AiVoiceCommand.Command(action, "已回到首页");
                case AiVoiceCommand.ACTION_NAV_LIVE:
                    return new AiVoiceCommand.Command(action, "已打开直播");
                case AiVoiceCommand.ACTION_NAV_MINIAPP:
                    return new AiVoiceCommand.Command(action, "已打开小程序");
                case AiVoiceCommand.ACTION_NAV_HISTORY:
                    return new AiVoiceCommand.Command(action, "已打开播放历史");
                case AiVoiceCommand.ACTION_NAV_KEEP:
                    return new AiVoiceCommand.Command(action, "已打开收藏");
                case AiVoiceCommand.ACTION_NAV_BOOKSHELF:
                    return new AiVoiceCommand.Command(action, "已打开书架");
                case AiVoiceCommand.ACTION_NAV_CONTINUE_READING:
                    return new AiVoiceCommand.Command(action, "已继续阅读");
                case AiVoiceCommand.ACTION_NAV_BOOKSOURCE:
                    return new AiVoiceCommand.Command(action, "已打开书源");
                case AiVoiceCommand.ACTION_NAV_DOWNLOAD:
                    return new AiVoiceCommand.Command(action, "已打开下载管理");
                case AiVoiceCommand.ACTION_NAV_PLUGIN:
                    return new AiVoiceCommand.Command(action, "已打开插件管理");
                case AiVoiceCommand.ACTION_NAV_LAB:
                    return new AiVoiceCommand.Command(action, "已打开实验室");
                case AiVoiceCommand.ACTION_NAV_CLOUD:
                    return new AiVoiceCommand.Command(action, "已打开网盘");
                case AiVoiceCommand.ACTION_NAV_SETTINGS:
                    return new AiVoiceCommand.Command(action, "已打开设置");
                case AiVoiceCommand.ACTION_NAV_FEATURES:
                    return new AiVoiceCommand.Command(action, "已打开个性功能");
                case AiVoiceCommand.ACTION_NAV_AI_SETTINGS:
                    return new AiVoiceCommand.Command(action, "已打开 AI 设置");
                case AiVoiceCommand.ACTION_NAV_PLAYER_SET:
                    return new AiVoiceCommand.Command(action, "已打开播放器设置");
                case AiVoiceCommand.ACTION_NAV_UI_SET:
                    return new AiVoiceCommand.Command(action, "已打开界面设置");
                case AiVoiceCommand.ACTION_NAV_NODE:
                    return new AiVoiceCommand.Command(action, "已打开节点管理");
                case AiVoiceCommand.ACTION_NAV_NODE_TEST:
                    return new AiVoiceCommand.Command(action, "已打开节点列表，正在测延迟");
                case AiVoiceCommand.ACTION_NAV_SEARCH: {
                    if (TextUtils.isEmpty(text)) return null;
                    return new AiVoiceCommand.Command(action, text, "正在搜索 " + text);
                }
                // ---- 全局开关 ----
                case AiVoiceCommand.ACTION_DOH_NEXT:
                    return new AiVoiceCommand.Command(action, "已切换 DoH");
                case AiVoiceCommand.ACTION_FLOAT_ON:
                    return new AiVoiceCommand.Command(action, "悬浮窗已打开");
                case AiVoiceCommand.ACTION_FLOAT_OFF:
                    return new AiVoiceCommand.Command(action, "悬浮窗已关闭");
                case AiVoiceCommand.ACTION_PY_REFRESH:
                    return new AiVoiceCommand.Command(action, "正在刷新首页");
                case AiVoiceCommand.ACTION_NODE_ON:
                    return new AiVoiceCommand.Command(action, "正在开启节点");
                case AiVoiceCommand.ACTION_NODE_OFF:
                    return new AiVoiceCommand.Command(action, "节点已关闭");
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 找到顶层 Activity */
    private static Activity findTopActivity(Context context) {
        if (context instanceof Activity) return (Activity) context;
        try {
            return AiActivityTracker.top();
        } catch (Exception e) {
            return null;
        }
    }

    /** 分发到具体 Activity，返回是否执行成功 */
    private static boolean dispatchToActivity(Activity activity, AiVoiceCommand.Command cmd) {
        try {
            Class<?> videoCls = Class.forName("com.fongmi.android.tv.ui.activity.VideoActivity");
            if (videoCls.isInstance(activity)) {
                return dispatchToVideo(activity, cmd);
            }
            // 阅读器内指令：书签 / 换源（阅读器自己声明支持哪些口令）
            Class<?> novelCls = Class.forName("com.fongmi.android.tv.ui.activity.NovelReaderActivity");
            Class<?> comicCls = Class.forName("com.fongmi.android.tv.ui.activity.ComicReaderActivity");
            if (novelCls.isInstance(activity) || comicCls.isInstance(activity)) {
                String a = cmd.action;
                if (AiVoiceCommand.ACTION_READING_BOOKMARK.equals(a)) {
                    activity.getClass().getMethod("aiBookmarks").invoke(activity);
                    return true;
                }
                if (AiVoiceCommand.ACTION_READING_CHANGESOURCE.equals(a)) {
                    activity.getClass().getMethod("aiChangeSource").invoke(activity);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean dispatchToVideo(Activity activity, AiVoiceCommand.Command cmd) {
        try {
            switch (cmd.action) {
                case AiVoiceCommand.ACTION_NEXT:
                    invoke(activity, "aiNext");
                    return true;
                case AiVoiceCommand.ACTION_PREV:
                    invoke(activity, "aiPrev");
                    return true;
                case AiVoiceCommand.ACTION_TOGGLE_PLAY:
                    invoke(activity, "aiTogglePlay");
                    return true;
                case AiVoiceCommand.ACTION_SEEK_BY:
                    invoke(activity, "aiSeekBy", new Class[]{long.class}, cmd.param);
                    return true;
                case AiVoiceCommand.ACTION_VOLUME_UP:
                    invoke(activity, "aiVolumeUp");
                    return true;
                case AiVoiceCommand.ACTION_VOLUME_DOWN:
                    invoke(activity, "aiVolumeDown");
                    return true;
                case AiVoiceCommand.ACTION_SPEED_UP:
                    invoke(activity, "aiSpeedUp");
                    return true;
                case AiVoiceCommand.ACTION_SPEED_DOWN:
                    invoke(activity, "aiSpeedDown");
                    return true;
                case AiVoiceCommand.ACTION_SPEED_SET:
                    invoke(activity, "aiSetSpeed", new Class[]{float.class}, cmd.param / 100f);
                    return true;
                case AiVoiceCommand.ACTION_BRIGHT_UP:
                    invoke(activity, "aiBrightUp");
                    return true;
                case AiVoiceCommand.ACTION_BRIGHT_DOWN:
                    invoke(activity, "aiBrightDown");
                    return true;
                case AiVoiceCommand.ACTION_DANMAKU:
                    invoke(activity, "aiDanmakuSet", new Class[]{int.class}, (int) cmd.param);
                    return true;
                case AiVoiceCommand.ACTION_SUBTITLE:
                    invoke(activity, "aiSubtitleSet", new Class[]{int.class}, (int) cmd.param);
                    return true;
                case AiVoiceCommand.ACTION_AI_SUBTITLE:
                    invoke(activity, "aiAiSubtitleSet", new Class[]{int.class}, (int) cmd.param);
                    return true;
                case AiVoiceCommand.ACTION_EPISODE:
                    invoke(activity, "aiPlayEpisode", new Class[]{int.class}, (int) cmd.param);
                    return true;
                case AiVoiceCommand.ACTION_CAST:
                    invoke(activity, "aiCast");
                    return true;
                case AiVoiceCommand.ACTION_DL_EPISODE:
                    invoke(activity, "aiDownloadEpisode");
                    return true;
                case AiVoiceCommand.ACTION_FULLSCREEN:
                    invoke(activity, "aiEnterFullscreen");
                    return true;
                case AiVoiceCommand.ACTION_EXIT_FULLSCREEN:
                    invoke(activity, "aiExitFullscreen");
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void invoke(Activity activity, String method) throws Exception {
        activity.getClass().getMethod(method).invoke(activity);
    }

    private static void invoke(Activity activity, String method, Class<?>[] types, Object... args) throws Exception {
        activity.getClass().getMethod(method, types).invoke(activity, args);
    }
}
