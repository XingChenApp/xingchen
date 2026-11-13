package com.fongmi.android.tv.gson;

import com.fongmi.android.tv.bean.Url;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class UrlAdapter implements JsonDeserializer<Url> {

    @Override
    public Url deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json.isJsonArray()) return convert(json.getAsJsonArray());
        if (json.isJsonObject()) return Url.objectFrom(json);
        // 畸形守卫：JsonNull 直达 getAsString 会抛，拖死整份 Result；按无地址处理。
        if (!json.isJsonPrimitive()) return Url.create();
        return Url.create().add(json.getAsString());
    }

    private Url convert(JsonArray array) {
        Url url = Url.create();
        for (int i = 0; i + 1 < array.size(); i += 2) {
            JsonElement name = array.get(i);
            JsonElement value = array.get(i + 1);
            if (name.isJsonPrimitive() && value.isJsonPrimitive()) url.add(name.getAsString(), value.getAsString());
        }
        return url;
    }
}
