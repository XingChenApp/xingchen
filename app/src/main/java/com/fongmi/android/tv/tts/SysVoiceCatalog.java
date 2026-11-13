package com.fongmi.android.tv.tts;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 系统 TTS 音色目录（2026-11-01 补）：此前系统路没有音色这一维——
 * SystemTtsSynth 只 setLanguage 用引擎缺省嗓，用户在朗读页选的
 * Edge 音色在系统模式/系统兜底里被整段忽略，选哪个都是一个音。
 * 本类管系统嗓的枚举与选择落盘：选择存 Voice.getName()（引擎内
 * 唯一名），合成时由 SystemTtsSynth 按名 setVoice 真传到合成端。
 * 空选择＝用引擎缺省嗓（与接音色前行为一致）。
 */
public final class SysVoiceCatalog {

    private static final String TAG = "SysVoiceCatalog";
    private static final String PREFS = "tts_sys_voice";
    private static final String KEY_NAME = "voice_name";

    private SysVoiceCatalog() {
    }

    /** 一个系统嗓：name 是引擎内唯一名（落盘与 setVoice 都用它）。 */
    public static final class SysVoice {
        public final String name;
        public final String label;

        SysVoice(String name, String label) {
            this.name = name;
            this.label = label;
        }
    }

    private static SharedPreferences prefs() {
        return App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 已选系统嗓名；未选回空串（＝引擎缺省）。 */
    public static String selected() {
        String s = prefs().getString(KEY_NAME, "");
        return s == null ? "" : s;
    }

    public static void select(String voiceName) {
        prefs().edit().putString(KEY_NAME, voiceName == null ? "" : voiceName).apply();
        Log.i(TAG, "系统音色选择=" + (TextUtils.isEmpty(voiceName) ? "(缺省)" : voiceName));
    }

    /** 在已初始化的引擎实例上按当前选择套嗓；返回实际套上的嗓名，缺省/没找到回空串。 */
    static String applySelected(TextToSpeech tts) {
        String want = selected();
        if (tts == null || TextUtils.isEmpty(want)) return "";
        try {
            for (Voice v : tts.getVoices()) {
                if (v != null && want.equals(v.getName())) {
                    int rc = tts.setVoice(v);
                    if (rc == TextToSpeech.SUCCESS) return want;
                    Log.w(TAG, "setVoice 失败 rc=" + rc + " voice=" + want);
                    return "";
                }
            }
            Log.w(TAG, "所选系统音色已不在引擎名单里，回缺省嗓 voice=" + want);
        } catch (Throwable t) {
            Log.w(TAG, "套系统音色失败", t);
        }
        return "";
    }

    /**
     * 枚举系统引擎的中文嗓（异步：临时建一个引擎实例拿名单，拿完即关，
     * 不与合成实例抢）。回调在主线程；引擎不可用时回空表。
     */
    public static void listChineseVoices(Context context,
                                         java.util.function.Consumer<List<SysVoice>> cb) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        try {
            final java.util.concurrent.atomic.AtomicReference<TextToSpeech> self =
                    new java.util.concurrent.atomic.AtomicReference<>();
            TextToSpeech probe = new TextToSpeech(app, status -> {
                List<SysVoice> out = new ArrayList<>();
                TextToSpeech t = self.get();
                try {
                    if (status == TextToSpeech.SUCCESS && t != null) {
                        for (Voice v : t.getVoices()) {
                            if (v == null || v.getLocale() == null) continue;
                            String lang = v.getLocale().getLanguage();
                            if (lang != null && lang.toLowerCase(Locale.US).startsWith("zh")) {
                                out.add(new SysVoice(v.getName(), labelOf(v)));
                            }
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    try {
                        if (t != null) t.shutdown();
                    } catch (Throwable ignored) {
                    }
                }
                Collections.sort(out, (a, b) -> a.label.compareTo(b.label));
                main.post(() -> cb.accept(out));
            });
            self.set(probe);
        } catch (Throwable t) {
            main.post(() -> cb.accept(new ArrayList<>()));
        }
    }

    /** 嗓的人话名：引擎给的名字多是内部串，尽量提炼；提炼不出就原样。 */
    private static String labelOf(Voice v) {
        String name = v.getName() == null ? "" : v.getName();
        String loc = "";
        try {
            loc = v.getLocale() != null ? v.getLocale().getDisplayName(Locale.CHINESE) : "";
        } catch (Throwable ignored) {
        }
        return loc.isEmpty() ? name : loc + " · " + name;
    }
}
