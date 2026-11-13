package com.fongmi.android.tv.gson;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Danmaku;
import com.github.catvod.utils.Json;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DanmakuAdapter implements JsonDeserializer<List<Danmaku>> {

    // 定型修复（体检类2）：不再用字段声明给的 typeOfT（靠类文件 Signature 保型，
    // 签名失效即退化成裸 List、元素成 LinkedTreeMap 在下方 stream 强转崩），
    // 改用程序化构造的参数化类型（不读类文件签名）。
    private static final Type LIST_TYPE = TypeToken.getParameterized(List.class, Danmaku.class).getType();

    @Override
    public List<Danmaku> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        List<Danmaku> items = json.isJsonPrimitive() ? parsePrimitive(json.getAsString().trim(), LIST_TYPE) : App.gson().fromJson(json, LIST_TYPE);
        // 畸形守卫：元素为 null 字面量时 fromJson 返回 null，直达 stream 必 NPE；
        // 数组里混 null 元素同理，过滤时一并挡掉。
        if (items == null) return new ArrayList<>();
        return items.stream().filter(d -> d != null && !d.isEmpty()).collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Danmaku> parsePrimitive(String text, Type type) {
        if (!Json.isArray(text)) return List.of(Danmaku.from(text));
        List<Danmaku> items = App.gson().fromJson(text, type);
        return items == null ? new ArrayList<>() : items;
    }
}
