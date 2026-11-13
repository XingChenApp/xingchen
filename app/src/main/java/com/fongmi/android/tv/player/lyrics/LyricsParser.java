package com.fongmi.android.tv.player.lyrics;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricsParser {

    // 时间标签两种写法都要认：[mm:ss(.x/xx/xxx)] 与纯秒 [ss(.x/xx/xxx)]（部分音源的 LRC 是秒制，
    // 如 [50.87]无言地我在走——漏认会让整份词被当纯文本、标签原样上屏，或在混合格式里把该行吞掉）
    private static final Pattern TIME = Pattern.compile("\\[(?:(\\d{1,3}):(\\d{2})|(\\d{1,3}))(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_TIME = Pattern.compile("<\\d+,-?\\d+(?:,-?\\d+)?>");
    private static final Pattern WORD_TOKEN = Pattern.compile("<(\\d+),(-?\\d+)(?:,-?\\d+)?>([^<]*)");
    private static final Pattern BRACKET_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern PAREN_TIME = Pattern.compile("\\((\\d+),(\\d+)(?:,\\d+)?\\)");
    private static final Pattern ANGLE_TIME = Pattern.compile("<(\\d+),(-?\\d+)(?:,-?\\d+)?>");
    private static final long AUX_TOLERANCE_MS = 800;
    private static final List<String> CREDIT_BLOCKED_LABELS = List.of(
            "企划", "策划", "监制", "制作", "制作人", "出品", "发行", "统筹", "项目", "宣发", "推广", "营销", "文案", "封面", "设计", "摄影", "版权",
            "演唱", "原唱", "翻唱", "歌手", "艺人",
            "编曲", "配器", "和声", "和音", "合音", "伴唱", "配唱", "声乐指导", "音乐总监", "录音", "混音", "母带", "母带工程", "音频编辑", "调音", "缩混",
            "钢琴", "吉他", "鼓", "鼓手", "贝斯", "键盘", "弦乐", "管弦", "管乐", "小提琴", "大提琴", "萨克斯", "长笛", "短笛", "笛子", "古琴", "二胡",
            "op", "sp", "cp", "publisher", "copyright", "producer", "production", "arranger", "arrangement", "mix", "mixing", "master", "mastering", "recording", "vocal", "chorus", "artist", "singer",
            "piano", "guitar", "drum", "drums", "bass", "keyboard", "strings", "violin", "cello", "sax", "flute"
    );

    private static class Mark {

        private final long time;
        private final int start;
        private final int end;

        private Mark(long time, int start, int end) {
            this.time = time;
            this.start = start;
            this.end = end;
        }
    }

    private static class Content {

        private final String text;
        private final List<LyricsWord> words;

        private Content(String text, List<LyricsWord> words) {
            this.text = text;
            this.words = words;
        }
    }

    private static class AuxLine {

        private final long time;
        private final String text;

        private AuxLine(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    public static boolean hasTimedLine(String text) {
        return !TextUtils.isEmpty(text) && TIME.matcher(text).find();
    }

    public static String mergeTimedText(String primary, String... auxiliaries) {
        if (TextUtils.isEmpty(primary) || auxiliaries == null || auxiliaries.length == 0) return primary;
        List<AuxLine> auxLines = new ArrayList<>();
        for (String auxiliary : auxiliaries) collectAuxLines(auxLines, auxiliary);
        if (auxLines.isEmpty()) return primary;
        auxLines.sort(Comparator.comparingLong(item -> item.time));

        StringBuilder builder = new StringBuilder();
        Set<Integer> used = new HashSet<>();
        for (String raw : primary.replace("\r", "").split("\n")) {
            long time = firstTime(raw);
            if (time < 0) {
                builder.append(raw).append('\n');
                continue;
            }
            String auxiliary = findAuxLine(raw, time, auxLines, used);
            builder.append(raw);
            if (!TextUtils.isEmpty(auxiliary)) builder.append("\\n").append(auxiliary);
            builder.append('\n');
        }
        return builder.toString();
    }

    /** 边车元数据（M-05）：TtmlClient.toEnhancedLrc 在定时行前写一行 {@code @@LYRICMETA …}，
     * 旧解析器无时间标签会直接跳过该行；新解析器把它当作下一行的 songPart／agent／isBG／isDuet。
     * 格式：{@code @@LYRICMETA part=副歌|agent=A|bg=0|duet=0}（值内竖线已由生产侧替换为空格）。 */
    public static final String META_PREFIX = "@@LYRICMETA ";

    private static class Meta {
        String songPart = "";
        String agent = "";
        boolean bg;
        boolean duet;
    }

    private static Meta parseMetaLine(String line) {
        Meta meta = new Meta();
        String body = line.substring(META_PREFIX.length()).trim();
        for (String item : body.split("\\|")) {
            int eq = item.indexOf('=');
            if (eq <= 0) continue;
            String key = item.substring(0, eq).trim();
            String value = item.substring(eq + 1).trim();
            if ("part".equals(key)) meta.songPart = value;
            else if ("agent".equals(key)) meta.agent = value;
            else if ("bg".equals(key)) meta.bg = "1".equals(value);
            else if ("duet".equals(key)) meta.duet = "1".equals(value);
        }
        return meta;
    }

    private static void applyMeta(LyricsLine line, Meta meta) {
        if (line == null || meta == null) return;
        line.setSongPart(meta.songPart);
        line.setAgentName(meta.agent);
        line.setBG(meta.bg);
        line.setDuet(meta.duet);
    }

    public static List<LyricsLine> parseTimed(String text) {
        List<LyricsLine> lines = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return lines;
        Meta pending = null;
        for (String raw : text.replace("\r", "").split("\n")) {
            String trimmed = raw.trim();
            if (trimmed.startsWith(META_PREFIX)) {
                pending = parseMetaLine(trimmed);
                continue;
            }
            Matcher matcher = TIME.matcher(raw);
            List<Mark> marks = new ArrayList<>();
            while (matcher.find()) {
                marks.add(new Mark(parseTime(matcher), matcher.start(), matcher.end()));
            }
            if (marks.isEmpty()) {
                pending = null;
                continue;
            }
            int before = lines.size();
            addLine(lines, raw, marks, pending == null ? "" : pending.songPart);
            if (pending != null) {
                for (int i = before; i < lines.size(); i++) applyMeta(lines.get(i), pending);
                pending = null;
            }
        }
        lines.sort(Comparator.comparingLong(LyricsLine::getTimeMs));
        return compact(lines);
    }

    /**
     * 逐字歌词统一入口（只做加法，parseTimed 旧行为不变）：
     * QRC / YRC / KRC 这类「[绝对毫秒,时长] 行头」格式走括号/尖括号逐词解析，
     * 词时间一律换成相对行首的偏移；其余文本（普通 LRC、lxlyric 的
     * [mm:ss.xx]&lt;off,dur&gt; 形态等）原样走 parseTimed。
     * 入参若是原始 QRC XML（带 LyricContent 包裹），先解出内容再解析。
     */
    public static List<LyricsLine> parseWordLyric(String text) {
        List<LyricsLine> lines = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return lines;
        String content = extractLyricContent(text);
        boolean bracket = false;
        for (String raw : content.replace("\r", "").split("\n")) {
            if (BRACKET_LINE.matcher(raw.trim()).matches()) {
                bracket = true;
                break;
            }
        }
        if (!bracket) {
            List<LyricsLine> timed = parseTimed(content);
            if (!timed.isEmpty()) return timed;
            // 无任何定时行但有纯文本歌词：按无时间轴整行词出（空态只许真无词时出现）
            return parsePlainFallback(content);
        }
        Meta pending = null;
        for (String raw : content.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith(META_PREFIX)) {
                pending = parseMetaLine(line);
                continue;
            }
            Matcher lineMatcher = BRACKET_LINE.matcher(line);
            if (!lineMatcher.matches()) {
                // 混排的普通 LRC 行按老路解析，不丢行
                lines.addAll(parseTimed(line));
                pending = null;
                continue;
            }
            long lineStart = parseLong(lineMatcher.group(1));
            String rest = lineMatcher.group(3) == null ? "" : lineMatcher.group(3);
            List<LyricsWord> words = new ArrayList<>();
            StringBuilder textBuilder = new StringBuilder();
            parseBracketWords(rest, lineStart, words, textBuilder);
            String lineText = textBuilder.toString().trim();
            if (words.isEmpty()) {
                // 逐字标记损坏/不可识别（一对括号里没逗号等）：剥掉标记残渣按整行词收，
                // 不许把带定时头的正文行整行吞掉或带着标记垃圾上屏。
                // 只剥时间标记形状（见 stripWordMarkers），正文里的括号内容不许动
                String salvaged = stripWordMarkers(rest).trim();
                if (!salvaged.isEmpty()) lineText = salvaged;
            }
            if (lineText.isEmpty()) {
                pending = null;
                continue;
            }
            // M-05 收窄：带 songPart 的行不丢制作标签规则。
            if ((pending == null || pending.songPart.isEmpty()) && shouldDropCreditLine(lineText)) {
                pending = null;
                continue;
            }
            LyricsLine created = new LyricsLine(lineStart, lineText, words);
            applyMeta(created, pending);
            pending = null;
            lines.add(created);
        }
        lines.sort(Comparator.comparingLong(LyricsLine::getTimeMs));
        List<LyricsLine> result = compact(lines);
        if (!result.isEmpty()) return result;
        // 逐字支路整体产 0 行：同文本再走 plain 定时行解析，仍无定时行再按纯文本整行出
        List<LyricsLine> timed = parseTimed(content);
        if (!timed.isEmpty()) return timed;
        return parsePlainFallback(content);
    }

    /**
     * 只剥逐字时间标记本身：括号标记 (偏移,时长[,标志]) 与尖括号时间戳
     * &lt;mm:ss.xx&gt;/&lt;偏移,时长&gt;。正文里的括号/尖括号内容（如和声段
     * (oh oh)、注记）是歌词的一部分，不许整段剥掉——此前两处都用
     * \([^)]*\) 与 &lt;[^&gt;]*&gt; 整段删，纯文本歌词的括号内容会被吃掉。
     */
    private static String stripWordMarkers(String s) {
        return s.replaceAll("\\(\\d+\\s*,\\s*\\d+(\\s*,\\s*\\d+)?\\)", "")
                .replaceAll("<\\d[^>]*>", "");
    }

    /**
     * 无时间轴纯文本回落（GZ untimed 口径：显示、但不滚动、不均分）：
     * 每行 timeMs 一律 0（不是按 5 秒均分的假时间）；findLine 见首末时间相同即判无当前行（-1），
     * 歌词视图只作静态浏览（手滑可看、松手不回锚到假行）。旧实现经 parsePlain(cleaned, 0) 按 5000ms
     * 步进造假时间，等于给无时间轴文本编了滚动节奏——本轮改掉，视频侧 LyricsResult 的 parsePlain
     * （带真实时长均分）不动，不属音乐链。
     * 行过滤与旧 parsePlain 口径一致：空行、首尾方括号标签行、元数据行、制作行一律丢。
     */
    private static List<LyricsLine> parsePlainFallback(String content) {
        List<LyricsLine> lines = new ArrayList<>();
        if (TextUtils.isEmpty(content)) return lines;
        for (String raw : content.replace("\r", "").split("\n")) {
            String line = stripWordMarkers(raw.trim()
                    .replaceAll("^\\[\\d+,\\d+]", ""))
                    .trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("[") && line.endsWith("]")) continue;
            if (line.startsWith(META_PREFIX)) continue;
            if (shouldDropCreditLine(line)) continue;
            lines.add(new LyricsLine(0, line));
        }
        return lines;
    }

    /** 无时间轴判定（GZ rememberFrameLyricIndex 口径）：2 行以上且首末行时间相同＝无时间变化＝无当前行 */
    public static boolean isUntimed(List<LyricsLine> lines) {
        return lines != null && lines.size() >= 2
                && lines.get(0).getTimeMs() == lines.get(lines.size() - 1).getTimeMs();
    }

    /**
     * 一行 [start,dur] 后的词序列，三种词时间形态混扫：
     * QRC 词在括号前「词(abs,dur)」、YRC 括号在词前「(abs,dur,0)词」、
     * KRC 尖括号在词前「&lt;相对off,dur,0&gt;词」（off 本就是相对行首，不再减行首）。
     */
    private static void parseBracketWords(String rest, long lineStart, List<LyricsWord> words, StringBuilder textBuilder) {
        int i = 0;
        int length = rest.length();
        while (i < length) {
            char c = rest.charAt(i);
            if (c == '(') {
                Matcher m = PAREN_TIME.matcher(rest);
                m.region(i, length);
                if (!m.lookingAt()) {
                    i++;
                    continue;
                }
                long offset = Math.max(0, parseLong(m.group(1)) - lineStart);
                long duration = Math.max(0, parseLong(m.group(2)));
                int wordStart = m.end();
                int wordEnd = nextTokenStart(rest, wordStart);
                String word = rest.substring(wordStart, wordEnd);
                if (!word.isEmpty()) {
                    words.add(new LyricsWord(offset, duration, word));
                    textBuilder.append(word);
                }
                i = wordEnd;
            } else if (c == '<') {
                Matcher m = ANGLE_TIME.matcher(rest);
                m.region(i, length);
                if (!m.lookingAt()) {
                    i++;
                    continue;
                }
                long offset = Math.max(0, parseLong(m.group(1)));
                long duration = Math.max(0, parseLong(m.group(2)));
                int wordStart = m.end();
                int wordEnd = nextTokenStart(rest, wordStart);
                String word = rest.substring(wordStart, wordEnd);
                if (!word.isEmpty()) {
                    words.add(new LyricsWord(offset, duration, word));
                    textBuilder.append(word);
                }
                i = wordEnd;
            } else {
                int paren = rest.indexOf('(', i);
                int angle = rest.indexOf('<', i);
                int token = paren < 0 ? angle : (angle < 0 ? paren : Math.min(paren, angle));
                if (token < 0) {
                    // 行尾无时间标记的裸文本：只进整行文本，不造词
                    textBuilder.append(rest.substring(i));
                    break;
                }
                if (token != paren) {
                    // 下一个 token 是尖括号（KRC 式），中间文本无归属时间，只进整行文本
                    textBuilder.append(rest, i, token);
                    i = token;
                    continue;
                }
                String word = rest.substring(i, token);
                Matcher m = PAREN_TIME.matcher(rest);
                m.region(token, length);
                if (!m.lookingAt()) {
                    textBuilder.append(word);
                    i = token + 1;
                    continue;
                }
                long offset = Math.max(0, parseLong(m.group(1)) - lineStart);
                long duration = Math.max(0, parseLong(m.group(2)));
                if (!word.isEmpty()) {
                    words.add(new LyricsWord(offset, duration, word));
                    textBuilder.append(word);
                }
                i = m.end();
            }
        }
    }

    private static int nextTokenStart(String rest, int from) {
        int paren = rest.indexOf('(', from);
        int angle = rest.indexOf('<', from);
        if (paren < 0) return angle < 0 ? rest.length() : angle;
        if (angle < 0) return paren;
        return Math.min(paren, angle);
    }

    /** 原始 QRC XML 解包：取出 LyricContent 属性内容并解 XML 转义；非 XML 入参原样返回 */
    private static String extractLyricContent(String qrc) {
        if (TextUtils.isEmpty(qrc) || !qrc.contains("LyricContent")) return qrc == null ? "" : qrc;
        Matcher greedy = Pattern.compile("LyricContent\\s*=\\s*\"([\\s\\S]*)\"\\s*/?>").matcher(qrc);
        if (greedy.find() && !Pattern.compile("\\s+\\w+\\s*=\\s*\"").matcher(greedy.group(1)).find()) return decodeXmlEntities(greedy.group(1));
        Matcher normal = Pattern.compile("LyricContent\\s*=\\s*\"([^\"]*)\"").matcher(qrc);
        return decodeXmlEntities(normal.find() ? normal.group(1) : qrc);
    }

    private static String decodeXmlEntities(String text) {
        return (text == null ? "" : text)
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    public static List<LyricsLine> parsePlain(String text, long durationMs) {
        List<String> texts = new ArrayList<>();
        if (!TextUtils.isEmpty(text)) {
            for (String raw : text.replace("\r", "").split("\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("[") && line.endsWith("]")) continue;
                if (line.startsWith(META_PREFIX)) continue;
                if (shouldDropCreditLine(line)) continue;
                texts.add(line);
            }
        }
        List<LyricsLine> lines = new ArrayList<>();
        if (texts.isEmpty()) return lines;
        long step = durationMs > 0 ? Math.max(3000, durationMs / Math.max(1, texts.size())) : 5000;
        for (int i = 0; i < texts.size(); i++) lines.add(new LyricsLine(i * step, texts.get(i)));
        return lines;
    }

    public static int findLine(List<LyricsLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) return -1;
        // 无时间轴不推进当前行（见 isUntimed）：不许按假时间指到末行
        if (isUntimed(lines)) return -1;
        int low = 0;
        int high = lines.size() - 1;
        int result = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).getTimeMs() <= positionMs) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    public static List<LyricsLine> filterMetadataLines(List<LyricsLine> lines, String title, String artist) {
        if (lines == null || lines.isEmpty()) return lines;
        List<LyricsLine> output = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            LyricsLine line = lines.get(i);
            // M-05 收窄：带 songPart 的行豁免前 4 行身份行丢弃规则。
            if (i < 4 && line.getTimeMs() <= 10_000 && line.getSongPart().isEmpty()
                    && shouldDropIdentityLine(line.getText(), title, artist)) {
                changed = true;
                continue;
            }
            output.add(line);
        }
        return changed ? output : lines;
    }

    private static long parseTime(Matcher matcher) {
        // group1/2 = [mm:ss] 的分/秒；group3 = 纯秒 [ss] 的秒；group4 = 两种写法共用的小数段
        long minute = 0;
        long second;
        if (matcher.group(1) != null) {
            minute = parseLong(matcher.group(1));
            second = parseLong(matcher.group(2));
        } else {
            second = parseLong(matcher.group(3));
        }
        String fraction = matcher.group(4);
        long millis = 0;
        if (!TextUtils.isEmpty(fraction)) {
            if (fraction.length() == 1) millis = parseLong(fraction) * 100;
            else if (fraction.length() == 2) millis = parseLong(fraction) * 10;
            else millis = parseLong(fraction.substring(0, 3));
        }
        return minute * 60_000 + second * 1000 + millis;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void addLine(List<LyricsLine> lines, String raw, List<Mark> marks, String songPart) {
        boolean hasPart = songPart != null && !songPart.isEmpty();
        if (isPrefixTimed(raw, marks)) {
            Content content = parseContent(raw.substring(marks.get(marks.size() - 1).end));
            if (content.text.isEmpty()) return;
            // M-05 收窄：带 songPart 的段落标签行不丢（即使形似制作标签）。
            if (!hasPart && shouldDropCreditLine(content.text)) return;
            for (Mark mark : marks) lines.add(new LyricsLine(mark.time, content.text, content.words));
            return;
        }
        List<Long> pending = new ArrayList<>();
        for (int i = 0; i < marks.size(); i++) {
            Mark mark = marks.get(i);
            int next = i + 1 < marks.size() ? marks.get(i + 1).start : raw.length();
            Content content = parseContent(raw.substring(mark.end, next));
            pending.add(mark.time);
            if (content.text.isEmpty()) continue;
            if (!hasPart && shouldDropCreditLine(content.text)) {
                pending.clear();
                continue;
            }
            for (long time : pending) lines.add(new LyricsLine(time, content.text, content.words));
            pending.clear();
        }
    }

    private static boolean isPrefixTimed(String raw, List<Mark> marks) {
        int cursor = 0;
        for (Mark mark : marks) {
            if (!raw.substring(cursor, mark.start).trim().isEmpty()) return false;
            cursor = mark.end;
        }
        return true;
    }

    private static String cleanLyric(String text) {
        return WORD_TIME.matcher(text == null ? "" : text).replaceAll("").replace("\\n", "\n").trim();
    }

    private static Content parseContent(String raw) {
        String source = raw == null ? "" : raw;
        List<LyricsWord> words = parseWords(source);
        return new Content(cleanLyric(source), words);
    }

    private static List<LyricsWord> parseWords(String raw) {
        List<LyricsWord> words = new ArrayList<>();
        Matcher matcher = WORD_TOKEN.matcher(raw == null ? "" : raw);
        while (matcher.find()) {
            String text = matcher.group(3);
            if (TextUtils.isEmpty(text)) continue;
            words.add(new LyricsWord(parseLong(matcher.group(1)), parseLong(matcher.group(2)), text));
        }
        return words;
    }

    private static List<LyricsLine> compact(List<LyricsLine> input) {
        List<LyricsLine> output = new ArrayList<>();
        String last = null;
        long lastTime = -1;
        for (LyricsLine line : input) {
            if (line.getText().equals(last) && line.getTimeMs() == lastTime && sameWords(line, output) && sameMeta(line, output)) continue;
            output.add(line);
            last = line.getText();
            lastTime = line.getTimeMs();
        }
        return output;
    }

    private static boolean sameWords(LyricsLine line, List<LyricsLine> output) {
        return !output.isEmpty() && line.getWords().equals(output.get(output.size() - 1).getWords());
    }

    /** 去重不许把主行与同文本同时间的伴唱／段落行合并（M-03／M-05）。 */
    private static boolean sameMeta(LyricsLine line, List<LyricsLine> output) {
        if (output.isEmpty()) return false;
        LyricsLine prev = output.get(output.size() - 1);
        return line.isBG() == prev.isBG() && line.isDuet() == prev.isDuet()
                && line.getSongPart().equals(prev.getSongPart()) && line.getAgentName().equals(prev.getAgentName());
    }

    private static void collectAuxLines(List<AuxLine> output, String text) {
        if (TextUtils.isEmpty(text)) return;
        for (String raw : text.replace("\r", "").split("\n")) {
            List<Mark> marks = new ArrayList<>();
            Matcher matcher = TIME.matcher(raw);
            while (matcher.find()) marks.add(new Mark(parseTime(matcher), matcher.start(), matcher.end()));
            if (marks.isEmpty()) continue;
            Content content = parseContent(raw.substring(marks.get(marks.size() - 1).end));
            if (content.text.isEmpty()) continue;
            if (shouldDropCreditLine(content.text)) continue;
            for (Mark mark : marks) output.add(new AuxLine(mark.time, content.text));
        }
    }

    private static long firstTime(String raw) {
        Matcher matcher = TIME.matcher(raw == null ? "" : raw);
        return matcher.find() ? parseTime(matcher) : -1;
    }

    private static String findAuxLine(String raw, long time, List<AuxLine> auxLines, Set<Integer> used) {
        List<String> texts = new ArrayList<>();
        String primary = LyricsMatcher.normalize(cleanTimedLyric(raw));
        for (int i = 0; i < auxLines.size(); i++) {
            if (used.contains(i)) continue;
            AuxLine line = auxLines.get(i);
            long delta = Math.abs(line.time - time);
            if (delta > AUX_TOLERANCE_MS) continue;
            if (LyricsMatcher.normalize(line.text).equals(primary)) {
                used.add(i);
                continue;
            }
            if (!containsNormalized(texts, line.text)) texts.add(line.text);
            used.add(i);
        }
        return TextUtils.join(" / ", texts);
    }

    private static boolean containsNormalized(List<String> texts, String text) {
        String target = LyricsMatcher.normalize(text);
        for (String item : texts) if (LyricsMatcher.normalize(item).equals(target)) return true;
        return false;
    }

    private static String cleanTimedLyric(String text) {
        return cleanLyric(TIME.matcher(text == null ? "" : text).replaceAll(""));
    }

    private static boolean shouldDropCreditLine(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return false;
        int index = firstCreditDelimiter(value);
        if (index <= 0 || index > 24) return false;
        String label = normalizeCreditLabel(value.substring(0, index));
        if (label.isEmpty()) return false;
        return isBlockedCreditLabel(label);
    }

    private static boolean shouldDropIdentityLine(String text, String title, String artist) {
        String value = LyricsMatcher.normalize(text);
        String song = LyricsMatcher.normalize(title);
        String singer = LyricsMatcher.normalize(artist);
        if (value.isEmpty() || song.isEmpty() || singer.isEmpty()) return false;
        return value.contains(song) && value.contains(singer);
    }

    private static int firstCreditDelimiter(String text) {
        int colon = text.indexOf(':');
        int full = text.indexOf('：');
        if (colon < 0) return full;
        if (full < 0) return colon;
        return Math.min(colon, full);
    }

    private static String normalizeCreditLabel(String label) {
        return label.replaceAll("[\\s　\\[\\]【】()（）《》〈〉「」『』·•_\\-/|｜]+", "").toLowerCase();
    }

    private static boolean isBlockedCreditLabel(String label) {
        for (String item : CREDIT_BLOCKED_LABELS) if (label.equals(item) || label.contains(item)) return true;
        return false;
    }

}
