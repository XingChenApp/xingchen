package com.fongmi.android.tv.gson;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class MsgAdapter implements JsonDeserializer<String> {

    @Override
    public String deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        // 畸形守卫：msg 给成对象/数组/null 时 getAsString 直接抛，会让整份 Result 解析失败；
        // 标量取原文，null 按无消息，对象/数组退回其 JSON 文本（不丢信息也不拖死整份）。
        if (json == null || json.isJsonNull()) return "";
        return json.isJsonPrimitive() ? json.getAsString() : json.toString();
    }
}
