package com.fongmi.android.tv.ai;
import com.fongmi.android.tv.utils.ToastUtil;
import com.fongmi.android.tv.utils.XcFadeTransition;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AI 悬浮球管理器：可拖动悬浮按钮。
 * 挂载到当前 Activity 的 decorView，无需系统悬浮窗权限。
 * 显示条件：AiStore.shouldShowFloat()（开关开 + 已配置服务商）
 * 且当前页是星辰首页——潇洒哥铁律：悬浮球只显示在星辰首页，
 * 搜索、历史、设置、播放、音乐等其它页面一律不显示。
 */
public class AiFloatManager {

    private static final AiFloatManager INSTANCE = new AiFloatManager();

    private WeakReference<Activity> activityRef;
    private TextView floatView;
    private float downX, downY;
    private int downLeft, downTop;
    private boolean dragging;
    private boolean longPressed;
    private final android.os.Handler longPressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable longPressRunnable;
    private static final int TOUCH_SLOP_DP = 16;
    private static final int LONG_PRESS_TIMEOUT = 400;
    private static final int REQ_RECORD_AUDIO = 2001;

    // 按住说话（无弹窗）相关
    private SpeechRecognizer voiceRecognizer;
    private boolean voiceRecording;
    private TextView recordTip;
    private android.graphics.drawable.Drawable ballBgBackup;

    public static AiFloatManager get() {
        return INSTANCE;
    }

    private AiFloatManager() {
    }

    /** 在 Activity resume 时调用：按需挂载/显示 */
    public void onResume(Activity activity) {
        activityRef = new WeakReference<>(activity);
        Context ctx = activity;
        if (!AiStore.shouldShowFloat(ctx) || !isHomePage(activity)) {
            detach();
            return;
        }
        attach(activity);
        if (floatView != null) {
            XcFadeTransition.fadeIn(floatView); // 出现走统一淡入 200ms
        }
    }

    /** 首页壳向悬浮球提供的页签信号：只在视频首页（点播页签可见、非网页沉浸态）时挂球。
     * 由 HomeActivity 实现；未实现本接口的 Activity 沿用旧的类名判定（leanback 等其它壳不受影响）。 */
    public interface AiFloatHost {
        boolean isAiFloatHomePage();
    }

    /** 铁律：只在星辰首页显示。页签感知：首页壳自己报当前是不是视频首页，
     * 壳内设置页签、网页沉浸态一律不挂球。 */
    private static boolean isHomePage(Activity activity) {
        if (activity instanceof AiFloatHost host) return host.isAiFloatHomePage();
        return "com.fongmi.android.tv.ui.activity.HomeActivity".equals(activity.getClass().getName());
    }

    /** 在 Activity pause 时调用：摘除悬浮球（避免窗口泄漏/跨页残留） */
    public void onPause() {
        cancelPressToTalk();
        AiQuickReply.dismiss();
        detach();
    }

    /** 配置变化后刷新显示状态 */
    public void refresh(Activity activity) {
        onResume(activity);
    }

    private void attach(Activity activity) {
        if (floatView != null) {
            // 已挂载到别的 decor，先摘除
            detach();
        }
        Context ctx = activity;
        TextView tv = new TextView(ctx);
        tv.setText("AI");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundResource(com.fongmi.android.tv.R.drawable.xc_ai_float_bg);
        tv.setElevation(dp(ctx, 8));

        int size = dp(ctx, 56);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        lp.gravity = Gravity.START | Gravity.TOP;
        // 恢复上次位置，默认右下角
        int defX = 0; // gravity END 效果通过 margin 实现，见下
        android.graphics.Point p = new android.graphics.Point();
        activity.getWindowManager().getDefaultDisplay().getSize(p);
        int defLeft = p.x - size - dp(ctx, 16);
        int defTop = p.y - size - dp(ctx, 160);
        lp.leftMargin = clamp(AiStore.getFloatX(ctx, defLeft), 0, p.x - size);
        lp.topMargin = clamp(AiStore.getFloatY(ctx, defTop), 0, p.y - size);
        tv.setLayoutParams(lp);

        final int slop = dp(ctx, TOUCH_SLOP_DP);
        tv.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) v.getLayoutParams();
                    downLeft = l.leftMargin;
                    downTop = l.topMargin;
                    dragging = false;
                    longPressed = false;
                    // 长按检测：400ms（LONG_PRESS_TIMEOUT）后触发按住说话（无弹窗）
                    longPressRunnable = () -> {
                        longPressed = true;
                        startPressToTalk(activity);
                    };
                    longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    // 录音中：忽略移动，不触发拖动
                    if (voiceRecording) return true;
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (!dragging && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                        dragging = true;
                        // 开始拖动，取消长按
                        longPressHandler.removeCallbacks(longPressRunnable);
                    }
                    if (dragging) {
                        FrameLayout.LayoutParams ll = (FrameLayout.LayoutParams) v.getLayoutParams();
                        ll.leftMargin = clamp(downLeft + (int) dx, 0, p.x - v.getWidth());
                        ll.topMargin = clamp(downTop + (int) dy, 0, p.y - v.getHeight());
                        v.setLayoutParams(ll);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    longPressHandler.removeCallbacks(longPressRunnable);
                    if (voiceRecording) {
                        // 按住说话：松开发送
                        stopPressToTalk();
                        return true;
                    }
                    if (longPressed) {
                        // 长按已触发但录音未开始（如权限不足），不做点击
                        return true;
                    }
                    if (dragging) {
                        FrameLayout.LayoutParams ll = (FrameLayout.LayoutParams) v.getLayoutParams();
                        AiStore.saveFloatPos(v.getContext(), ll.leftMargin, ll.topMargin);
                        // 抬手后自动吸附到左右边缘
                        snapToEdge(v, p.x);
                    } else {
                        v.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    longPressHandler.removeCallbacks(longPressRunnable);
                    if (voiceRecording) {
                        cancelPressToTalk();
                    }
                    return true;
            }
            return false;
        });
        tv.setOnClickListener(v -> openChat(activity));

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        decor.addView(tv);
        floatView = tv;
    }

    private void snapToEdge(View v, int screenW) {
        FrameLayout.LayoutParams ll = (FrameLayout.LayoutParams) v.getLayoutParams();
        int mid = ll.leftMargin + v.getWidth() / 2;
        ll.leftMargin = mid < screenW / 2 ? dp(v.getContext(), 8) : screenW - v.getWidth() - dp(v.getContext(), 8);
        v.setLayoutParams(ll);
        AiStore.saveFloatPos(v.getContext(), ll.leftMargin, ll.topMargin);
    }

    private void detach() {
        cancelPressToTalk();
        // 识别器不随 detach 销毁（低-01）：它已改用 applicationContext 创建（低-05），
        // 不持有 Activity、无泄漏；且「只建一次反复用」是 startPressToTalk 的既定口径，
        // 每离开一页就销毁，下次长按新建后立即 startListening 会撞系统服务未绑定窗口哑火。
        // 识别器随本单例走进程生命周期，仅启动失败时在 startPressToTalk 内销毁重建。
        if (floatView != null) {
            View view = floatView;
            floatView = null;
            fadeOutAndRemove(view); // 消失走淡出 200ms 后摘除，不直摘
        }
    }

    /** 悬浮件消失统一口：淡出 200ms 后从父容器摘除（系统动画关时直接摘）。 */
    private void fadeOutAndRemove(View view) {
        try {
            if (!XcFadeTransition.isAnimEnabled(view.getContext())) {
                removeFromParent(view);
                return;
            }
            view.animate().alpha(0f).setDuration(XcFadeTransition.DURATION_MS)
                    .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                    .withEndAction(() -> removeFromParent(view))
                    .start();
        } catch (Exception ignored) {
            removeFromParent(view);
        }
    }

    private void removeFromParent(View view) {
        try {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) parent.removeView(view);
        } catch (Exception ignored) {
        }
    }

    private void openChat(Activity activity) {
        try {
            if (activity instanceof FragmentActivity) {
                com.fongmi.android.tv.ui.dialog.AiChatDialog.show(
                        ((FragmentActivity) activity).getSupportFragmentManager());
            }
        } catch (Exception ignored) {
        }
    }

    /** 长按按住说话：直接启动语音识别，不弹对话框（微信式） */
    private void startPressToTalk(Activity activity) {
        if (voiceRecording) return;
        Context ctx = activity;
        // 权限检查
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            try {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            } catch (Exception ignored) {
            }
            ToastUtil.show(ctx, "需要麦克风权限，请允许后重试", Toast.LENGTH_SHORT);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            ToastUtil.show(ctx, "当前设备不支持语音识别", Toast.LENGTH_SHORT);
            return;
        }
        try {
            // 与 AiChatDialog 同款用法：识别器只建一次反复用；每按必销毁重建会撞上系统服务未绑定的窗口，直接哑火
            // 用应用 Context 建（低-05）：识别器是长命单例字段，拿 Activity 建会把页面钉住泄漏
            if (voiceRecognizer == null) {
                voiceRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx.getApplicationContext());
            }
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            voiceRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {
                    // 录音音量条：格子动=麦克风在拾音，不动=识别服务没在听
                    try {
                        if (recordTip != null) {
                            int level = Math.max(0, Math.min(5, (int) ((rmsdB + 2) / 2.4f)));
                            StringBuilder bars = new StringBuilder();
                            for (int i = 0; i < 5; i++) bars.append(i < level ? "▮" : "▯");
                            recordTip.setText("🎤 正在录音，松开发送 " + bars);
                        }
                    } catch (Exception ignored) {
                    }
                }
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    if (!voiceRecording) return;
                    voiceRecording = false;
                    hideRecordingUI();
                    AiVolumeDuck.restore(ctx);
                    if (error == SpeechRecognizer.ERROR_NO_MATCH
                            || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        toast(ctx, "没听清，请重试");
                    } else {
                        toast(ctx, "语音识别失败(" + error + ")，请重试");
                    }
                }
                @Override public void onResults(Bundle results) {
                    if (!voiceRecording) return;
                    voiceRecording = false;
                    hideRecordingUI();
                    AiVolumeDuck.restore(ctx);
                    ArrayList<String> list = results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION);
                    String text = (list != null && !list.isEmpty()) ? list.get(0).trim() : "";
                    if (text.isEmpty()) {
                        toast(ctx, "没听清，请重试");
                        return;
                    }
                    handleVoiceText(activity, text);
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
            voiceRecognizer.startListening(intent);
            voiceRecording = true;
            // AI 对话定案：录音（对话）期间压低视频声音，识别结束/出错/超时恢复
            AiVolumeDuck.duck(ctx);
            showRecordingUI(activity);
        } catch (Exception e) {
            voiceRecording = false;
            destroyRecognizer();
            ToastUtil.show(ctx, "语音识别启动失败", Toast.LENGTH_SHORT);
        }
    }

    /** 松开：停止识别，onResults 会接管后续流程 */
    private void stopPressToTalk() {
        if (!voiceRecording) return;
        try {
            if (voiceRecognizer != null) voiceRecognizer.stopListening();
        } catch (Exception ignored) {
        }
        // onResults/onError 回调里会清理 UI；加个超时兜底
        longPressHandler.postDelayed(() -> {
            if (voiceRecording) {
                voiceRecording = false;
                hideRecordingUI();
                AiVolumeDuck.restore(activityRef.get());
                try {
                    if (voiceRecognizer != null) voiceRecognizer.cancel();
                } catch (Exception ignored) {
                }
                Activity act = activityRef.get();
                if (act != null) ToastUtil.show(act, "识别超时，请重试", Toast.LENGTH_SHORT);
            }
        }, 3000);
    }

    /** 取消：直接销毁，不处理结果 */
    private void cancelPressToTalk() {
        longPressHandler.removeCallbacks(longPressRunnable);
        if (!voiceRecording) return;
        voiceRecording = false;
        hideRecordingUI();
        AiVolumeDuck.restore(activityRef.get());
        try {
            if (voiceRecognizer != null) voiceRecognizer.cancel();
        } catch (Exception ignored) {
        }
    }

    private void destroyRecognizer() {
        try {
            if (voiceRecognizer != null) voiceRecognizer.destroy();
        } catch (Exception ignored) {
        }
        voiceRecognizer = null;
    }

    /** 录音中 UI：悬浮球变红 + 顶部提示条 */
    private void showRecordingUI(Activity activity) {
        try {
            if (floatView != null) {
                ballBgBackup = floatView.getBackground();
                GradientDrawable red = new GradientDrawable();
                red.setColor(0xFFE53935);
                red.setCornerRadius(dp(activity, 28));
                floatView.setBackground(red);
                floatView.setText("🎤");
            }
            TextView tip = new TextView(activity);
            tip.setText("🎤 正在录音，松开发送");
            tip.setTextColor(Color.WHITE);
            tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tip.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xCC1A1D24);
            bg.setCornerRadius(dp(activity, 20));
            tip.setBackground(bg);
            int pad = dp(activity, 16);
            tip.setPadding(pad, dp(activity, 10), pad, dp(activity, 10));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.topMargin = dp(activity, 120);
            tip.setLayoutParams(lp);
            ((ViewGroup) activity.getWindow().getDecorView()).addView(tip);
            recordTip = tip;
            XcFadeTransition.fadeIn(tip); // 录音提示条出现走统一淡入 200ms
        } catch (Exception ignored) {
        }
    }

    private void hideRecordingUI() {
        try {
            if (floatView != null) {
                if (ballBgBackup != null) floatView.setBackground(ballBgBackup);
                floatView.setText("AI");
                ballBgBackup = null;
            }
            if (recordTip != null) {
                View tip = recordTip;
                recordTip = null;
                fadeOutAndRemove(tip); // 录音提示条消失走淡出 200ms 后摘除
            }
        } catch (Exception ignored) {
        }
    }

    private void toast(Context ctx, String msg) {
        try {
            ToastUtil.show(ctx, msg, Toast.LENGTH_SHORT);
        } catch (Exception ignored) {
        }
    }

    /**
     * 处理语音识别出的文字，走与 AiChatDialog.send() 一致的流程：
     * P1 本地语音指令 → P2 智能搜片 → P3 AI 对话（轻量浮层展示）
     */
    private void handleVoiceText(Activity activity, String text) {
        if (activity == null || activity.isFinishing()) return;
        Context ctx = activity.getApplicationContext();
        // P1：本地语音指令，毫秒级执行
        AiVoiceCommand.Command local = AiVoiceCommand.parse(text);
        if (local != null) {
            AiActionExecutor.executeLocal(activity, local, feedback ->
                    longPressHandler.post(() -> toast(ctx, feedback)));
            return;
        }
        // P2：智能搜片，直接执行搜索
        if (AiSearchParser.isNaturalLanguage(text)) {
            toast(ctx, "搜索中…");
            AiSearchParser.parse(ctx, text, intent -> {
                Activity act = activityRef.get();
                if (act == null || act.isFinishing()) return;
                String keyword = (intent != null && intent.keyword != null
                        && !intent.keyword.isEmpty()) ? intent.keyword : text;
                try {
                    com.fongmi.android.tv.ui.activity.SearchActivity.start(act, keyword);
                } catch (Throwable t) {
                    toast(ctx, "搜索失败：" + t.getMessage());
                }
            });
            return;
        }
        // P3：AI 对话，轻量浮层展示
        AiProvider provider = AiStore.getDefault(ctx);
        if (provider == null) {
            toast(ctx, "请先在 AI 设置中配置服务商");
            return;
        }
        Activity act = activityRef.get();
        if (act == null || act.isFinishing()) return;
        AiQuickReply.showThinking(act, text);
        new Thread(() -> {
            try {
                List<AiClient.Message> messages = new ArrayList<>();
                messages.add(AiClient.Message.user(text));
                AiClient.ChatResult r = AiClient.chat(provider, messages, null);
                AiQuickReply.updateAnswer(r.text);
            } catch (Exception e) {
                AiQuickReply.showError(e.getMessage() != null ? e.getMessage() : "未知错误");
            }
        }).start();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int dp(Context ctx, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics());
    }
}
