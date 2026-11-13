package com.fongmi.android.tv.ai;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AI 统一调用客户端（设计文档 §3）。
 * 本期只实现 openai_compatible 协议，其他协议抛 UnsupportedOperationException。
 */
public class AiClient {

    private static final Gson GSON = new Gson();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static volatile OkHttpClient client;

    private static OkHttpClient client() {
        if (client == null) {
            synchronized (AiClient.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return client;
    }

    public static class Message {
        public final String role;
        public final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message system(String content) {
            return new Message("system", content);
        }
    }

    public static class Options {
        public Double temperature;
        public Integer maxTokens;
        public boolean stream = false;

        public static Options simple(int maxTokens) {
            Options o = new Options();
            o.maxTokens = maxTokens;
            return o;
        }
    }

    public static class ChatResult {
        public String text = "";
        public int promptTokens;
        public int completionTokens;
        public String rawJson = "";
    }

    public interface StreamCallback {
        void onToken(String token);

        void onDone(String fullText);

        void onError(String msg);
    }

    /** 同步 chat 调用（必须在子线程调用） */
    public static ChatResult chat(AiProvider provider, List<Message> messages, Options options) throws Exception {
        if (provider == null) throw new IllegalArgumentException("未配置 AI 服务商");
        if (!AiProvider.PROTOCOL_OPENAI.equals(provider.protocol)) {
            throw new UnsupportedOperationException("暂不支持该协议：" + provider.protocol);
        }
        String base = normalizeBaseUrl(provider.baseUrl);
        String url = base + "/chat/completions";

        JsonObject body = new JsonObject();
        body.addProperty("model", provider.model);
        JsonArray arr = new JsonArray();
        for (Message m : messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role);
            o.addProperty("content", m.content);
            arr.add(o);
        }
        body.add("messages", arr);
        double temp = options != null && options.temperature != null ? options.temperature : provider.optTemperature(0.7);
        int maxTok = options != null && options.maxTokens != null ? options.maxTokens : provider.optMaxTokens(2048);
        body.addProperty("temperature", temp);
        body.addProperty("max_tokens", maxTok);
        body.addProperty("stream", false);

        Request req = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + provider.apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(GSON.toJson(body), JSON))
                .build();

        try (Response resp = client().newCall(req).execute()) {
            String raw = resp.body() != null ? resp.body().string() : "";
            if (resp.code() == 401) {
                throw new Exception("Key 无效，请检查 AI 设置（" + provider.name + "）");
            }
            if (!resp.isSuccessful()) {
                String err = extractError(raw);
                throw new Exception("调用失败（" + resp.code() + "）" + (TextUtils.isEmpty(err) ? "" : "：" + err));
            }
            ChatResult r = new ChatResult();
            r.rawJson = raw;
            r.text = extractContent(raw);
            return r;
        } catch (java.net.SocketTimeoutException e) {
            throw new Exception("请求超时，请稍后重试");
        }
    }

    /** 流式 chat（剧情问答用），回调在子线程触发 */
    public static void chatStream(AiProvider provider, List<Message> messages, Options options, StreamCallback cb) {
        new Thread(() -> {
            try {
                if (provider == null) throw new IllegalArgumentException("未配置 AI 服务商");
                if (!AiProvider.PROTOCOL_OPENAI.equals(provider.protocol)) {
                    throw new UnsupportedOperationException("暂不支持该协议：" + provider.protocol);
                }
                String base = normalizeBaseUrl(provider.baseUrl);
                String url = base + "/chat/completions";

                JsonObject body = new JsonObject();
                body.addProperty("model", provider.model);
                JsonArray arr = new JsonArray();
                for (Message m : messages) {
                    JsonObject o = new JsonObject();
                    o.addProperty("role", m.role);
                    o.addProperty("content", m.content);
                    arr.add(o);
                }
                body.add("messages", arr);
                double temp = options != null && options.temperature != null ? options.temperature : provider.optTemperature(0.7);
                int maxTok = options != null && options.maxTokens != null ? options.maxTokens : provider.optMaxTokens(2048);
                body.addProperty("temperature", temp);
                body.addProperty("max_tokens", maxTok);
                body.addProperty("stream", true);

                Request req = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + provider.apiKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "text/event-stream")
                        .post(RequestBody.create(GSON.toJson(body), JSON))
                        .build();

                StringBuilder full = new StringBuilder();
                try (Response resp = client().newCall(req).execute()) {
                    if (resp.code() == 401) throw new Exception("Key 无效，请检查 AI 设置（" + provider.name + "）");
                    if (!resp.isSuccessful()) throw new Exception("调用失败（" + resp.code() + "）");
                    if (resp.body() == null) throw new Exception("空响应");
                    java.io.BufferedReader br = new java.io.BufferedReader(resp.body().charStream());
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (!line.startsWith("data:")) continue;
                        String data = line.substring(5).trim();
                        if ("[DONE]".equals(data)) break;
                        String token = extractStreamToken(data);
                        if (!TextUtils.isEmpty(token)) {
                            full.append(token);
                            cb.onToken(token);
                        }
                    }
                }
                cb.onDone(full.toString());
            } catch (Exception e) {
                cb.onError(e.getMessage() == null ? "调用失败" : e.getMessage());
            }
        }).start();
    }

    /** 拉取模型列表 GET {baseUrl}/models */
    public static List<String> fetchModels(String baseUrl, String apiKey) throws Exception {
        String base = normalizeBaseUrl(baseUrl);
        Request.Builder rb = new Request.Builder().url(base + "/models").get()
                .addHeader("Content-Type", "application/json");
        if (!TextUtils.isEmpty(apiKey)) rb.addHeader("Authorization", "Bearer " + apiKey);
        try (Response resp = client().newCall(rb.build()).execute()) {
            String raw = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new Exception("获取模型列表失败（" + resp.code() + "）");
            }
            List<String> out = new ArrayList<>();
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            JsonArray data = root.has("data") ? root.getAsJsonArray("data") : null;
            if (data != null) {
                for (int i = 0; i < data.size(); i++) {
                    JsonObject o = data.get(i).getAsJsonObject();
                    if (o.has("id")) out.add(o.get("id").getAsString());
                }
            }
            return out;
        }
    }

    /** 测试连接：发一句 hi */
    public static ChatResult testConnection(AiProvider provider) throws Exception {
        List<Message> msgs = new ArrayList<>();
        msgs.add(Message.user("hi"));
        return chat(provider, msgs, Options.simple(8));
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (TextUtils.isEmpty(baseUrl)) return "";
        String s = baseUrl.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /**
     * content 字段转文本（低-06）：部分服务商回数组分片（[{type:text,text:...}]）或对象，
     * 此前 getAsString() 对非字符串直接抛错、被外层吞成空串 → 上层误判「AI 返回空」。
     * 字符串直取；数组拼接各 part 的 text；对象取 text 字段。
     */
    private static String contentToString(com.google.gson.JsonElement el) {
        if (el == null || el.isJsonNull()) return "";
        if (el.isJsonPrimitive()) return el.getAsString();
        if (el.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (com.google.gson.JsonElement part : el.getAsJsonArray()) {
                sb.append(contentToString(part));
            }
            return sb.toString();
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("text")) return contentToString(obj.get("text"));
            if (obj.has("content")) return contentToString(obj.get("content"));
        }
        return "";
    }

    private static String extractContent(String raw) {
        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                if (msg != null && msg.has("content")) {
                    return contentToString(msg.get("content"));
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String extractStreamToken(String data) {
        try {
            JsonObject root = JsonParser.parseString(data).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
                if (delta != null && delta.has("content")) {
                    return contentToString(delta.get("content"));
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String extractError(String raw) {
        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (root.has("error")) {
                JsonObject err = root.getAsJsonObject("error");
                if (err.has("message")) return err.get("message").getAsString();
            }
            if (root.has("message")) return root.get("message").getAsString();
        } catch (Exception ignored) {
        }
        return "";
    }
}
