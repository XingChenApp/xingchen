package com.fongmi.android.tv.ui.dialog;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.ai.AiActionExecutor;
import com.fongmi.android.tv.ai.AiVolumeDuck;
import com.fongmi.android.tv.ai.AiClient;
import com.fongmi.android.tv.ai.AiProvider;
import com.fongmi.android.tv.ai.AiQaHelper;
import com.fongmi.android.tv.ai.AiStore;
import com.fongmi.android.tv.ai.AiVoiceCommand;
import com.fongmi.android.tv.databinding.DialogAiChatBinding;
import com.fongmi.android.tv.utils.Notify;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AI 对话框：文字 + 语音输入，底部弹出。
 */
public class AiChatDialog extends BaseBottomSheetDialog {

    private DialogAiChatBinding binding;
    private AiProvider provider;
    private boolean autoVoice = false;
    private final List<AiClient.Message> history = new ArrayList<>();
    private SpeechRecognizer recognizer;
    private boolean listening = false;

    public static void show(FragmentManager manager) {
        try {
            AiChatDialog d = new AiChatDialog();
            d.show(manager, "ai_chat");
        } catch (Exception ignored) {
        }
    }

    /** 长按悬浮球：直接打开并自动开始语音输入 */
    public static void showWithVoice(FragmentManager manager) {
        try {
            AiChatDialog d = new AiChatDialog();
            d.autoVoice = true;
            d.show(manager, "ai_chat");
        } catch (Exception ignored) {
        }
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        binding = DialogAiChatBinding.inflate(inflater, container, false);
        return binding;
    }

    @Override
    protected void initView() {
        // AI 对话定案：对话期间压低视频声音，弹窗关闭（onDestroyView）恢复
        AiVolumeDuck.duck(requireContext());
        provider = AiStore.getDefault(requireContext());
        if (provider != null) {
            binding.tvModel.setText(provider.name + " · " + provider.displayModel());
        } else {
            binding.tvModel.setText("未配置");
        }
        addAiMessage("你好，我是 AI 助手，有什么可以帮你？");
        binding.btnSend.setOnClickListener(v -> send());
        binding.btnMic.setOnClickListener(v -> toggleVoice());
        refreshWatchingBar();
        // 长按悬浮球进来：自动开始语音输入
        if (autoVoice) {
            binding.getRoot().post(() -> toggleVoice());
        }
    }

    /** P3：播放页打开时在输入框上方显示"正在看：xxx" */
    private void refreshWatchingBar() {
        try {
            if (AiQaHelper.isWatching()) {
                binding.tvWatching.setVisibility(View.VISIBLE);
                binding.tvWatching.setText("正在看：" + AiQaHelper.getVodName());
            } else {
                binding.tvWatching.setVisibility(View.GONE);
            }
        } catch (Exception ignored) {
        }
    }

    private void send() {
        String text = binding.etInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        binding.etInput.setText("");
        addUserMessage(text);
        // P1：先本地解析语音指令，命中则毫秒级执行，不走 AI
        AiVoiceCommand.Command local = AiVoiceCommand.parse(text);
        if (local != null) {
            TextView tv = addAiMessage("执行中…");
            AiActionExecutor.executeLocal(requireContext(), local, feedback -> {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    // 同本文件其余回调口径：真正摸视图前再判 isAdded/binding，弹窗已拆不碰已拆的视图
                    if (!isAdded() || binding == null) return;
                    tv.setText(feedback);
                });
            });
            return;
        }
        // P2：智能搜片——自然语言直接解析并执行搜索，不走 AI，不让用户点确定
        if (com.fongmi.android.tv.ai.AiSearchParser.isNaturalLanguage(text)) {
            TextView tv = addAiMessage("搜索中…");
            com.fongmi.android.tv.ai.AiSearchParser.parse(requireContext(), text, intent -> {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    // 同本文件其余回调口径：弹窗已拆时不摸视图、也不再多触发一次搜索跳转/dismiss
                    if (!isAdded() || binding == null) return;
                    String keyword = (intent != null && intent.keyword != null && !intent.keyword.isEmpty()) ? intent.keyword : text;
                    tv.setText("正在搜索：" + keyword);
                    try {
                        com.fongmi.android.tv.ui.activity.SearchActivity.start(getActivity(), keyword);
                    } catch (Throwable t) {
                        tv.setText("搜索失败：" + t.getMessage());
                        return;
                    }
                    dismiss();
                });
            });
            return;
        }
        // 未命中本地：需要 AI
        if (provider == null) {
            Notify.show("请先在 AI 设置中配置服务商");
            return;
        }
        history.add(AiClient.Message.user(text));
        // P3：播放页走剧情问答——带影片上下文，流式输出（打字机效果）
        if (AiQaHelper.isWatching()) {
            TextView tv = addAiMessage("");
            // 限制历史长度，避免 token 爆炸
            List<AiClient.Message> qaHistory = history.size() > 20
                    ? new ArrayList<>(history.subList(history.size() - 20, history.size()))
                    : new ArrayList<>(history);
            AiQaHelper.ask(provider, text, qaHistory, new AiQaHelper.QaCallback() {
                @Override
                public void onToken(String token) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        // getActivity() 非空只说明检查那一刻还挂着：真正摸视图
                        // 前再判 isAdded，弹窗已拆时不许碰已拆的视图。
                        if (!isAdded() || binding == null) return;
                        tv.append(token);
                        binding.svMessages.post(() -> binding.svMessages.fullScroll(View.FOCUS_DOWN));
                    });
                }

                @Override
                public void onDone(String fullText) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded() || binding == null) return;
                        String reply = TextUtils.isEmpty(fullText) ? "（空回复）" : fullText;
                        tv.setText(reply);
                        history.add(new AiClient.Message("assistant", reply));
                    });
                }

                @Override
                public void onError(String msg) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded() || binding == null) return;
                        tv.setText("出错了：" + msg);
                        Notify.show("AI 调用失败：" + msg);
                    });
                }
            });
            return;
        }
        // 非播放页：普通对话
        // 限制历史长度，避免 token 爆炸
        List<AiClient.Message> msgs = history.size() > 20
                ? history.subList(history.size() - 20, history.size())
                : new ArrayList<>(history);
        TextView thinking = addAiMessage("思考中…");
        new Thread(() -> {
            try {
                AiClient.ChatResult r = AiClient.chat(provider, msgs, null);
                String reply = r.text.isEmpty() ? "（空回复）" : r.text;
                history.add(new AiClient.Message("assistant", reply));
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (!isAdded() || binding == null) return;
                    thinking.setText(reply);
                });
            } catch (Exception e) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (!isAdded() || binding == null) return;
                    thinking.setText("出错了：" + e.getMessage());
                    Notify.show("AI 调用失败：" + e.getMessage());
                });
            }
        }).start();
    }

    private TextView addUserMessage(String text) {
        return addBubble(text, true);
    }

    private TextView addAiMessage(String text) {
        return addBubble(text, false);
    }

    private TextView addBubble(String text, boolean user) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(6);
        row.setLayoutParams(rp);
        row.setGravity(user ? Gravity.END : Gravity.START);

        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(15);
        tv.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (user) {
            tv.setTextColor(0xFFFFFFFF);
            tv.setBackgroundResource(com.fongmi.android.tv.R.drawable.xc_ai_bubble_user);
        } else {
            tv.setTextColor(0xFF0C0E12);
            tv.setBackgroundResource(com.fongmi.android.tv.R.drawable.xc_setcard_v2);
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(tp);
        tv.setMaxWidth((int) (requireContext().getResources().getDisplayMetrics().widthPixels * 0.85));
        row.addView(tv);
        binding.containerMessages.addView(row);
        binding.svMessages.post(() -> binding.svMessages.fullScroll(View.FOCUS_DOWN));
        return tv;
    }

    private int dp(int v) {
        return (int) (v * requireContext().getResources().getDisplayMetrics().density);
    }

    // ---------- 语音输入 ----------

    private void toggleVoice() {
        if (listening) {
            stopListening();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            Notify.show("当前设备不支持语音识别");
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
            return;
        }
        startListening();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else if (requestCode == 1001) {
            Notify.show("需要麦克风权限才能语音输入");
        }
    }

    private void startListening() {
        try {
            if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    listening = true;
                    binding.btnMic.setText("⏺");
                    setMicStatus("正在聆听…");
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    listening = false;
                    binding.btnMic.setText("🎙");
                    setMicStatus(null);
                    Notify.show("语音识别失败，请重试");
                }
                @Override public void onResults(Bundle results) {
                    listening = false;
                    binding.btnMic.setText("🎙");
                    setMicStatus(null);
                    ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) {
                        String recognized = list.get(0);
                        binding.etInput.setText(recognized);
                        binding.etInput.setSelection(recognized.length());
                        // 语音识别完成自动发送（走本地优先流程）
                        send();
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
            recognizer.startListening(intent);
        } catch (Exception e) {
            Notify.show("语音识别启动失败");
        }
    }

    private void stopListening() {
        try {
            if (recognizer != null) recognizer.stopListening();
        } catch (Exception ignored) {
        }
        listening = false;
        binding.btnMic.setText("🎙");
        setMicStatus(null);
    }

    /** 麦克风状态提示：显示在输入框上方，null 则隐藏 */
    private void setMicStatus(String text) {
        try {
            if (TextUtils.isEmpty(text)) {
                binding.tvMicStatus.setVisibility(View.GONE);
            } else {
                binding.tvMicStatus.setVisibility(View.VISIBLE);
                binding.tvMicStatus.setText(text);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroyView() {
        try {
            if (recognizer != null) recognizer.destroy();
        } catch (Exception ignored) {
        }
        recognizer = null;
        AiVolumeDuck.restore(getContext());
        super.onDestroyView();
    }
}
