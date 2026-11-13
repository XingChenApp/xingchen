package com.fongmi.android.tv.ai;

import android.content.Context;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 字幕翻译（P4，云端 API 版）。
 * 流程：解析外挂 SRT → 按 50 条一批调 AI 翻译 → 生成新 SRT → 加载到播放器。
 * 单批失败时该批回退原文，不中断整体。
 */
public class AiSubtitleTranslator {

    /** 每批翻译行数（省额度：50 条一批） */
    public static final int BATCH_SIZE = 50;
    /** 语言：简体中文 */
    public static final String LANG_ZH = "zh";
    /** 语言：英文 */
    public static final String LANG_EN = "en";
    /** 语言：日文 */
    public static final String LANG_JA = "ja";
    /** 语言：韩文 */
    public static final String LANG_KO = "ko";
    /** 语言：泰文 */
    public static final String LANG_TH = "th";
    /** 语言：印地语 */
    public static final String LANG_HI = "hi";
    /** 语言：西班牙语 */
    public static final String LANG_ES = "es";
    /** 语言：法语 */
    public static final String LANG_FR = "fr";
    /** 语言：德语 */
    public static final String LANG_DE = "de";
    /** 语言：葡萄牙语 */
    public static final String LANG_PT = "pt";
    /** 语言：俄语 */
    public static final String LANG_RU = "ru";
    /** 语言：越南语 */
    public static final String LANG_VI = "vi";

    /** 所有支持语言的代码（与 LANG_NAMES 一一对应） */
    public static final String[] LANG_CODES = {LANG_ZH, LANG_EN, LANG_JA, LANG_KO, LANG_TH, LANG_HI, LANG_ES, LANG_FR, LANG_DE, LANG_PT, LANG_RU, LANG_VI};
    /** 所有支持语言的显示名 */
    public static final String[] LANG_NAMES = {"中文", "English", "日本語", "한국어", "ไทย", "हिन्दी", "Español", "Français", "Deutsch", "Português", "Русский", "Tiếng Việt"};

    /** 语言代码转 prompt 用的英文名 */
    public static String langNameForPrompt(String code) {
        if (LANG_JA.equals(code)) return "Japanese";
        if (LANG_KO.equals(code)) return "Korean";
        if (LANG_TH.equals(code)) return "Thai";
        if (LANG_HI.equals(code)) return "Hindi";
        if (LANG_ES.equals(code)) return "Spanish";
        if (LANG_FR.equals(code)) return "French";
        if (LANG_DE.equals(code)) return "German";
        if (LANG_PT.equals(code)) return "Portuguese";
        if (LANG_RU.equals(code)) return "Russian";
        if (LANG_VI.equals(code)) return "Vietnamese";
        if (LANG_EN.equals(code)) return "English";
        return "Simplified Chinese";
    }

    /** 语言代码转显示名 */
    public static String langDisplayName(String code) {
        for (int i = 0; i < LANG_CODES.length; i++) {
            if (LANG_CODES[i].equals(code)) return LANG_NAMES[i];
        }
        return code;
    }

    /** 当前外挂字幕文件（翻译源） */
    private static File currentSubFile;
    /** 翻译后生成的文件（激活中） */
    private static File translatedFile;
    /** 翻译前的原文件（用于一键恢复） */
    private static File originalFile;

    public static class SubtitleLine {
        public int index;
        public String timeLine;
        public String text;
    }

    public interface Callback {
        void onProgress(int done, int total);
        void onDone(List<SubtitleLine> translated);
        void onError(String msg);
    }

    // ---------------- 文件状态 ----------------

    public static void setCurrentSubFile(File file) {
        currentSubFile = file;
    }

    public static File getCurrentSubFile() {
        return currentSubFile;
    }

    /** 翻译已应用：记住原文件与译文文件 */
    public static void onTranslated(File translated, File original) {
        translatedFile = translated;
        originalFile = original;
        currentSubFile = translated;
    }

    /** 已恢复原文 */
    public static void onRestored() {
        if (originalFile != null) currentSubFile = originalFile;
        translatedFile = null;
        originalFile = null;
    }

    public static boolean isTranslatedActive() {
        return translatedFile != null && translatedFile.exists();
    }

    public static File getOriginalFile() {
        return originalFile;
    }

    /** 预估调用次数 */
    public static int getBatchCount(int lineCount) {
        if (lineCount <= 0) return 0;
        return (lineCount + BATCH_SIZE - 1) / BATCH_SIZE;
    }

    // ---------------- SRT 解析 ----------------

    public static List<SubtitleLine> parseSrt(String content) {
        List<SubtitleLine> lines = new ArrayList<>();
        if (TextUtils.isEmpty(content)) return lines;
        content = content.replace("\r\n", "\n").replace("\r", "\n");
        String[] blocks = content.split("\n\\s*\n");
        for (String block : blocks) {
            String[] parts = block.split("\n", 3);
            if (parts.length < 3) continue;
            try {
                SubtitleLine line = new SubtitleLine();
                line.index = Integer.parseInt(parts[0].trim());
                line.timeLine = parts[1].trim();
                if (!line.timeLine.contains("-->")) continue;
                line.text = parts[2].trim();
                lines.add(line);
            } catch (NumberFormatException ignored) {
            }
        }
        return lines;
    }

    public static String toSrt(List<SubtitleLine> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            SubtitleLine l = lines.get(i);
            sb.append(i + 1).append('\n');
            sb.append(l.timeLine).append('\n');
            sb.append(l.text).append("\n\n");
        }
        return sb.toString();
    }

    public static String readFile(File file) {
        if (file == null || !file.exists()) return "";
        StringBuilder sb = new StringBuilder();
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            String text;
            try {
                // 严格解码（低-10）：InputStreamReader 默认 REPLACE，GBK 字节常能「无错」拼出
                // 乱码而不抛异常，原先的 catch 回退永远进不来，GBK 字幕整片乱码。
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            } catch (java.nio.charset.CharacterCodingException e) {
                // 尝试 GBK（部分字幕是 GBK 编码）
                text = new String(bytes, "GBK");
            }
            String[] parts = text.split("\n", -1);
            int count = parts.length;
            // 与 readLine 口径一致：文本以换行结尾时，末尾的空段不算一行
            if (count > 0 && parts[count - 1].isEmpty() && text.endsWith("\n")) count--;
            for (int i = 0; i < count; i++) {
                String line = parts[i];
                if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                sb.append(line).append('\n');
            }
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    public static File writeTranslatedSrt(Context context, String srt, String tag) {
        try {
            File dir = new File(context.getCacheDir(), "subtitles");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "ai_translated_" + tag + "_" + System.currentTimeMillis() + ".srt");
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
                w.write(srt);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- 翻译 ----------------

    /**
     * 翻译字幕行。必须在子线程调用（内部已开新线程，回调也在子线程）。
     *
     * @param srcLang    源语言代码（如 LANG_EN）
     * @param targetLang 目标语言代码（如 LANG_ZH）
     */
    public static void translate(Context context, List<SubtitleLine> lines, String srcLang, String targetLang, Callback cb) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                AiProvider provider = AiStore.resolveForFeature(appContext, AiStore.FEATURE_SUBTITLE);
                if (provider == null) {
                    cb.onError("请先在 AI 设置中配置服务商");
                    return;
                }
                // 收集非空行（保留原位置映射）
                List<Integer> positions = new ArrayList<>();
                List<String> texts = new ArrayList<>();
                for (int i = 0; i < lines.size(); i++) {
                    String t = lines.get(i).text;
                    if (t != null && !t.trim().isEmpty()) {
                        positions.add(i);
                        // 多行压成一行，避免格式错乱
                        texts.add(t.trim().replace("\n", " "));
                    }
                }
                int total = texts.size();
                if (total == 0) {
                    cb.onError("字幕内容为空");
                    return;
                }
                Map<Integer, String> resultMap = new HashMap<>();
                AiClient.Options opts = new AiClient.Options();
                opts.temperature = 0.3;
                opts.maxTokens = 4000;
                int batches = getBatchCount(total);
                for (int b = 0; b < batches; b++) {
                    int from = b * BATCH_SIZE;
                    int to = Math.min(from + BATCH_SIZE, total);
                    List<String> batch = texts.subList(from, to);
                    try {
                        List<String> out = translateBatch(provider, batch, srcLang, targetLang, opts);
                        for (int i = 0; i < batch.size(); i++) {
                            String t = i < out.size() ? out.get(i) : batch.get(i);
                            resultMap.put(positions.get(from + i), TextUtils.isEmpty(t) ? batch.get(i) : t);
                        }
                    } catch (Exception e) {
                        // 单批失败：该批回退原文
                        for (int i = from; i < to; i++) resultMap.put(positions.get(i), texts.get(i));
                    }
                    cb.onProgress(to, total);
                }
                List<SubtitleLine> outLines = new ArrayList<>(lines.size());
                for (int i = 0; i < lines.size(); i++) {
                    SubtitleLine src = lines.get(i);
                    SubtitleLine dst = new SubtitleLine();
                    dst.index = src.index;
                    dst.timeLine = src.timeLine;
                    String t = resultMap.get(i);
                    dst.text = t != null ? t : src.text;
                    outLines.add(dst);
                }
                cb.onDone(outLines);
            } catch (Exception e) {
                cb.onError(e.getMessage() == null ? "翻译失败" : e.getMessage());
            }
        }).start();
    }

    private static List<String> translateBatch(AiProvider provider, List<String> batch, String srcLang, String targetLang, AiClient.Options opts) throws Exception {
        String srcName = langNameForPrompt(srcLang);
        String dstName = langNameForPrompt(targetLang);
        List<AiClient.Message> messages = new ArrayList<>();
        messages.add(AiClient.Message.system(
                "You are a professional subtitle translator. Translate the following subtitle lines from " + srcName + " to " + dstName + ".\n" +
                "Rules:\n" +
                "1. Each input line starts with a number and |||. Return the same format: number|||translation.\n" +
                "2. Translate EVERY line, keep the same order and count.\n" +
                "3. Keep translations concise and natural for on-screen subtitles.\n" +
                "4. If a line is already in the target language, or is a non-verbal cue like [Music] / (laughs), return it unchanged.\n" +
                "5. Output ONLY the translated lines, no explanations."));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) sb.append(i).append("|||").append(batch.get(i)).append('\n');
        messages.add(AiClient.Message.user(sb.toString()));
        AiClient.ChatResult result = AiClient.chat(provider, messages, opts);
        return parseBatchResponse(result.text, batch);
    }

    private static List<String> parseBatchResponse(String text, List<String> fallback) {
        // 按序号回填（中-08）：提示词序号从 0 起、与下标一致，序号是对齐的唯一可靠信号。
        // 此前剥掉序号按顺序 append，AI 漏一条就从缺口起整批平移错位；现按序号落位，
        // 缺条/空译文的槽位留原文兜底（out 预填 fallback），无序号行退回顺序兜底。
        List<String> out = new ArrayList<>(fallback);
        if (TextUtils.isEmpty(text)) return out;
        boolean[] filled = new boolean[fallback.size()];
        int cursor = 0;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Integer num = null;
            String t;
            int sep = line.indexOf("|||");
            if (sep >= 0) {
                try {
                    num = Integer.parseInt(line.substring(0, sep).trim());
                } catch (NumberFormatException ignored) {
                    num = null;
                }
                t = line.substring(sep + 3).trim();
            } else {
                t = line.replaceFirst("^\\d+[.:)\\s\\-]+", "").trim();
            }
            int idx;
            if (num != null) {
                idx = num;
                if (idx < 0 || idx >= out.size() || filled[idx]) continue;
            } else {
                while (cursor < out.size() && filled[cursor]) cursor++;
                if (cursor >= out.size()) break;
                idx = cursor;
            }
            if (!TextUtils.isEmpty(t)) {
                out.set(idx, t);
                filled[idx] = true;
            }
        }
        return out;
    }
}
