package com.fongmi.android.tv.ai;

import android.app.Activity;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 剧情问答助手（P3）。
 * 看剧时点悬浮窗提问，自动带上当前影片上下文，流式回答（打字机效果）。
 */
public class AiQaHelper {

    public interface QaCallback {
        void onToken(String token);

        void onDone(String fullText);

        void onError(String msg);
    }

    /** 当前是否在播放页且有影片信息 */
    public static boolean isWatching() {
        return !TextUtils.isEmpty(getVodName());
    }

    /** 当前影片名（仅播放页有值） */
    public static String getVodName() {
        try {
            Activity top = AiActivityTracker.top();
            if (!isVideoActivity(top)) return "";
            Object name = top.getClass().getMethod("aiGetVodName").invoke(top);
            return name == null ? "" : String.valueOf(name);
        } catch (Exception e) {
            return "";
        }
    }

    /** 当前影片元数据文本（拼 system prompt 用） */
    public static String getVodMeta() {
        try {
            Activity top = AiActivityTracker.top();
            if (!isVideoActivity(top)) return "";
            Object meta = top.getClass().getMethod("aiGetVodMeta").invoke(top);
            return meta == null ? "" : String.valueOf(meta);
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isVideoActivity(Activity activity) {
        if (activity == null) return false;
        try {
            Class<?> videoCls = Class.forName("com.fongmi.android.tv.ui.activity.VideoActivity");
            return videoCls.isInstance(activity);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 剧情问答：带影片上下文，流式返回。
     *
     * @param provider AI 服务商（默认）
     * @param question 用户问题
     * @param history  历史对话（含刚问的这一句），用于追问上下文；可为 null
     * @param cb       流式回调（子线程触发）
     */
    public static void ask(AiProvider provider, String question, List<AiClient.Message> history, QaCallback cb) {
        if (provider == null) {
            if (cb != null) cb.onError("请先在 AI 设置中配置服务商");
            return;
        }
        String meta = getVodMeta();
        StringBuilder sys = new StringBuilder();
        sys.append("你是星辰 App 的追剧助手，擅长回答剧情相关问题。用中文回答，简洁准确，不要编造不存在的剧情。");
        if (!TextUtils.isEmpty(meta)) {
            sys.append("\n\n用户正在观看的影片信息：\n").append(meta);
            sys.append("\n\n回答时优先结合这部影片的信息；用户说\"这部剧\"\"主角\"\"结局\"等指代时，指的就是它。");
        }
        List<AiClient.Message> msgs = new ArrayList<>();
        msgs.add(AiClient.Message.system(sys.toString()));
        // 带上最近几轮对话，方便追问（"那他后来呢"这类）
        if (history != null && !history.isEmpty()) {
            int from = Math.max(0, history.size() - 6);
            for (int i = from; i < history.size(); i++) msgs.add(history.get(i));
        } else if (!TextUtils.isEmpty(question)) {
            msgs.add(AiClient.Message.user(question));
        }
        AiClient.Options opt = new AiClient.Options();
        opt.temperature = 0.7;
        opt.maxTokens = 1024;
        AiClient.chatStream(provider, msgs, opt, new AiClient.StreamCallback() {
            @Override
            public void onToken(String token) {
                if (cb != null) cb.onToken(token);
            }

            @Override
            public void onDone(String fullText) {
                if (cb != null) cb.onDone(fullText);
            }

            @Override
            public void onError(String msg) {
                if (cb != null) cb.onError(msg);
            }
        });
    }
}
