package com.fongmi.android.tv.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * AI 智能搜片解析器（P2）。
 * 自然语言 → 结构化搜索意图 JSON → 取 keyword 走现有搜索链路。
 * 未配置 AI 或解析失败时回调 null，调用方回退直接搜（降级友好）。
 */
public class AiSearchParser {

    public static class Intent {
        public String keyword = "";
        public String type = "";   // 电影 / 剧集 / 综艺 / 动漫 / 全部
        public String genre = "";
        public String year = "";
        public String actor = "";
    }

    public interface Callback {
        /** intent 为 null 表示回退：用原文直接搜 */
        void onResult(Intent intent);
    }

    /** 自然语言标记：命中即认为是描述性搜索而非片名关键词 */
    private static final Pattern NL_PATTERN = Pattern.compile(
            "(电影|电视剧|剧集|综艺|动漫|纪录片|喜剧|悬疑|恐怖|爱情|科幻|动作|剧情|犯罪|奇幻|冒险|战争|历史|传记|音乐|歌舞|西部|武侠|古装|都市|青春|家庭|儿童|动画|真人秀|脱口秀|推荐|最近|最火|好看|经典|高分|想看|找|搜一下|有什么|一部|那些|关于|讲|主演|导演|年份|年代|\\d{4}年)");

    /** 启发式判断输入是否为自然语言（而非片名关键词） */
    public static boolean isNaturalLanguage(String text) {
        if (TextUtils.isEmpty(text)) return false;
        String t = text.trim();
        if (t.length() >= 10) return true;
        return NL_PATTERN.matcher(t).find();
    }

    /** 异步解析；回调在主线程。AI 未配置时直接回退。 */
    public static void parse(Context context, String input, Callback cb) {
        Handler main = new Handler(Looper.getMainLooper());
        AiProvider provider = AiStore.resolveForFeature(context.getApplicationContext(), AiStore.FEATURE_SMART_SEARCH);
        if (provider == null) {
            // AI 未配置：本地简单提取关键词，不直接用全文
            final Intent local = localParse(input);
            main.post(() -> cb.onResult(local));
            return;
        }
        new Thread(() -> {
            Intent intent = null;
            try {
                intent = requestIntent(provider, input);
            } catch (Exception e) {
                intent = null;
            }
            // AI 失败时回退到本地解析，不返回 null
            if (intent == null || TextUtils.isEmpty(intent.keyword)) {
                intent = localParse(input);
            }
            final Intent r = intent;
            main.post(() -> cb.onResult(r));
        }).start();
    }

    /** 本地简单提取：去前后缀，取核心词。AI 未配置时的降级方案。 */
    private static Intent localParse(String input) {
        if (input == null) return null;
        String t = input.trim();
        // 去前缀：各种动词和语气词
        String[] prefixes = {"搜索一下", "搜索", "查找一下", "查找", "搜一下", "搜", "找一下", "找", "看一下", "看", "想看", "想", "推荐一下", "推荐", "给我", "帮我", "有没有", "有没有什么", "最近", "今年", "去年"};
        boolean changed;
        do {
            changed = false;
            for (String p : prefixes) {
                if (t.startsWith(p)) {
                    String rest = t.substring(p.length()).trim();
                    // 避免把"搜"后面的关键词吃掉：只有当剩余部分非空时才去
                    if (!rest.isEmpty()) {
                        t = rest;
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);
        // 去后缀：的电影、电影、片等
        String[] suffixes = {"的电影", "的电视剧", "的连续剧", "的剧集", "的综艺", "的动漫", "的纪录片", "的电影版", "电影版", "电视剧", "连续剧", "纪录片", "动漫", "综艺", "电影", "大片", "好片", "新片", "老片", "片"};
        for (String s : suffixes) {
            if (t.endsWith(s) && t.length() > s.length()) {
                t = t.substring(0, t.length() - s.length()).trim();
                break;
            }
        }
        // 去"的"：周星驰的喜剧 -> 周星驰；周星驰主演的 -> 周星驰
        // 先处理"主演的"、"导演的"等
        String[] dePatterns = {"主演的", "导演的", "演的"};
        for (String dp : dePatterns) {
            int idx = t.indexOf(dp);
            if (idx > 0) {
                t = t.substring(0, idx).trim();
                break;
            }
        }
        // 再处理普通的"的"
        int de = t.indexOf("的");
        if (de > 0) {
            // 保留"的"前面的核心词，但如果后面是类别词则去掉
            String before = t.substring(0, de).trim();
            String after = t.substring(de + 1).trim();
            // 如果后面是类别词（如喜剧、动作），保留前面的
            // 如果后面是空，保留前面的
            if (!before.isEmpty()) {
                t = before;
            }
        }
        // 去开头的类别词：电影僵尸 -> 僵尸
        String[] leadingCats = {"电影", "电视剧", "连续剧", "综艺", "动漫", "纪录片", "喜剧片", "动作片", "科幻片", "恐怖片"};
        for (String c : leadingCats) {
            if (t.startsWith(c)) {
                String rest = t.substring(c.length()).trim();
                if (!rest.isEmpty()) {
                    t = rest;
                    break;
                }
            }
        }
        // 去年份：2023年、2024年
        t = t.replaceAll("^\\d{4}年", "").trim();
        t = t.replaceAll("\\d{4}年$", "").trim();
        if (t.isEmpty()) return null;
        Intent intent = new Intent();
        intent.keyword = t;
        return intent;
    }

    private static Intent requestIntent(AiProvider provider, String input) throws Exception {
        List<AiClient.Message> messages = new ArrayList<>();
        messages.add(AiClient.Message.system(
                "你是影视搜索意图解析器。把用户的自然语言转成 JSON，只输出 JSON，不要解释，不要用代码块包裹。\n" +
                        "格式：{\"keyword\":\"核心搜索词\",\"type\":\"电影|剧集|综艺|动漫|全部\",\"genre\":\"类型\",\"year\":\"年份\",\"actor\":\"演员\"}\n" +
                        "规则：keyword 是最能搜到结果的 2-6 个字（优先片名、人名）；type 按描述判断，无法判断填\"全部\"；没有的信息填空字符串。\n" +
                        "示例：\"周星驰的喜剧电影\" → {\"keyword\":\"周星驰\",\"type\":\"电影\",\"genre\":\"喜剧\",\"year\":\"\",\"actor\":\"周星驰\"}\n" +
                        "示例：\"最近很火的悬疑剧\" → {\"keyword\":\"悬疑\",\"type\":\"剧集\",\"genre\":\"悬疑\",\"year\":\"\",\"actor\":\"\"}\n" +
                        "示例：\"庆余年\" → {\"keyword\":\"庆余年\",\"type\":\"全部\",\"genre\":\"\",\"year\":\"\",\"actor\":\"\"}"));
        messages.add(AiClient.Message.user(input));
        AiClient.Options options = AiClient.Options.simple(512);
        options.temperature = 0.3;
        AiClient.ChatResult result = AiClient.chat(provider, messages, options);
        return parseIntent(result.text);
    }

    /** 容错解析：去 markdown 包裹、截取首个 {...} */
    private static Intent parseIntent(String text) {
        if (TextUtils.isEmpty(text)) return null;
        String json = text.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) json = json.substring(start, end + 1);
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            Intent in = new Intent();
            in.keyword = opt(o, "keyword");
            in.type = opt(o, "type");
            in.genre = opt(o, "genre");
            in.year = opt(o, "year");
            in.actor = opt(o, "actor");
            if (TextUtils.isEmpty(in.keyword)) return null;
            return in;
        } catch (Exception e) {
            return null;
        }
    }

    private static String opt(JsonObject o, String key) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString().trim() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
