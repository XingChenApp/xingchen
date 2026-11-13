package com.fongmi.android.tv.ai;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AI 服务商配置（对应设计文档 §2.2）。
 * 以 JSON 存于 SharedPreferences("xingchen") 的 ai_providers key。
 */
public class AiProvider {

    public static final String PROTOCOL_OPENAI = "openai_compatible";
    public static final String PROTOCOL_ANTHROPIC = "anthropic";
    public static final String PROTOCOL_GEMINI = "gemini";
    public static final String PROTOCOL_CUSTOM = "custom";

    public static final String KIND_CHAT = "chat";
    public static final String KIND_TTS = "tts";
    public static final String KIND_STT = "stt";

    @SerializedName("id")
    public String id;
    @SerializedName("name")
    public String name;
    @SerializedName("protocol")
    public String protocol;
    @SerializedName("baseUrl")
    public String baseUrl;
    @SerializedName("apiKey")
    public String apiKey;
    @SerializedName("model")
    public String model;
    @SerializedName("enabled")
    public boolean enabled = true;
    @SerializedName("kind")
    public String kind = KIND_CHAT;
    @SerializedName("extra")
    public Map<String, Object> extra;
    @SerializedName("createdAt")
    public long createdAt;
    @SerializedName("updatedAt")
    public long updatedAt;

    public AiProvider() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.extra = new HashMap<>();
    }

    public static AiProvider create(String name, String protocol, String baseUrl, String apiKey, String model) {
        AiProvider p = new AiProvider();
        p.name = name;
        p.protocol = protocol;
        p.baseUrl = baseUrl;
        p.apiKey = apiKey;
        p.model = model;
        return p;
    }

    /** 模板定义 */
    public static class Template {
        public final String name;
        public final String baseUrl;
        public final String model;

        public Template(String name, String baseUrl, String model) {
            this.name = name;
            this.baseUrl = baseUrl;
            this.model = model;
        }
    }

    /** 6 个一键模板（设计文档 §2.4） */
    public static Template[] templates() {
        return new Template[]{
                new Template("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
                new Template("通义千问（兼容模式）", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
                new Template("Moonshot（Kimi）", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
                new Template("OpenAI 官方", "https://api.openai.com/v1", "gpt-4o-mini"),
                new Template("OpenRouter", "https://openrouter.ai/api/v1", ""),
                new Template("自定义", "", ""),
        };
    }

    public String displayModel() {
        return model == null || model.isEmpty() ? "未设置模型" : model;
    }

    public String maskedKey() {
        if (apiKey == null || apiKey.isEmpty()) return "未填写";
        if (apiKey.length() <= 8) return "****";
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    public double optTemperature(double def) {
        if (extra == null) return def;
        Object v = extra.get("temperature");
        if (v instanceof Number) return ((Number) v).doubleValue();
        return def;
    }

    public int optMaxTokens(int def) {
        if (extra == null) return def;
        Object v = extra.get("maxTokens");
        if (v instanceof Number) return ((Number) v).intValue();
        return def;
    }

    public void touch() {
        updatedAt = System.currentTimeMillis();
    }
}
