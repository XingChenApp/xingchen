package com.fongmi.android.tv.api.hk;

import android.os.Handler;
import android.os.Looper;

import com.fongmi.android.tv.player.engine.HkPlayCompanion;
import com.fongmi.android.tv.utils.Task;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 环34 复核订正 B2：官 VideoPlayerActivity.loadLyric 的咱家落点——按
 * {@link HkPlayCompanion#classifyLyric} 值分形把 PlayData.lyric 变成歌词
 * 文本，交播放面歌词位（VideoActivity → LyricsController.setInlineLyrics）：
 * <ul>
 *   <li>LOCAL_FILE：后台经 HkJsRuntime.getFilePath 定位读文件文本（官
 *   lambda$loadLyric$57：getFilePath＋fileToString，非空才上屏）；</li>
 *   <li>REMOTE：经 HkHttpParser.get（CodeUtil.get 等价面）带请求头取文本
 *   （官 lambda$loadLyric$59：HttpHelper.get(url, headers)，非空才上屏）；</li>
 *   <li>INLINE_TEXT：值本身即歌词文本，直交（官 loadLyric(值, null)）；</li>
 *   <li>EMPTY：回调 null（调用方清歌词位，与官 loadLyric(空表) 同终态）。</li>
 * </ul>
 * 回调恒在主线程；取不到文本回 null（官同形：静默不上屏，不报错）。
 */
public final class HkLyricLoader {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private HkLyricLoader() {
    }

    public static void load(String lyric, Map<String, String> headers, Consumer<String> onText) {
        switch (HkPlayCompanion.classifyLyric(lyric)) {
            case EMPTY:
                onText.accept(null);
                return;
            case INLINE_TEXT:
                onText.accept(lyric);
                return;
            case LOCAL_FILE:
                Task.execute(() -> {
                    String text = null;
                    try {
                        String path = HkJsRuntime.getFilePath(lyric);
                        File f = new File(path);
                        if (f.isFile()) {
                            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                            if (!s.isEmpty()) text = s;
                        }
                    } catch (Throwable ignored) {
                    }
                    String result = text;
                    MAIN.post(() -> onText.accept(result));
                });
                return;
            case REMOTE:
                HkHttpParser.get(lyric, null, headers, new HkHttpParser.OnSearchCallBack() {
                    @Override
                    public void onFailure(int code, String msg) {
                        MAIN.post(() -> onText.accept(null));
                    }

                    @Override
                    public void onSuccess(String url, String code) {
                        String text = code != null && !code.isEmpty() ? code : null;
                        MAIN.post(() -> onText.accept(text));
                    }
                });
                return;
        }
    }
}
