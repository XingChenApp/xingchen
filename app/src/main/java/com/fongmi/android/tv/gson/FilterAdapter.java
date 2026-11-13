package com.fongmi.android.tv.gson;

import com.fongmi.android.tv.bean.Filter;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FilterAdapter implements JsonDeserializer<LinkedHashMap<String, List<Filter>>> {

    @Override
    public LinkedHashMap<String, List<Filter>> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        LinkedHashMap<String, List<Filter>> filterMap = new LinkedHashMap<>();
        // 畸形守卫：filters 给成数组/字符串/null 时 getAsJsonObject 直接抛，
        // 会让整份 Result 解析失败整页无数据；非对象一律按无筛选处理。
        if (json == null || !json.isJsonObject()) return filterMap;
        JsonObject filters = json.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : filters.entrySet()) {
            List<Filter> items = new ArrayList<>();
            JsonElement element = entry.getValue();
            if (element.isJsonObject()) add(items, element);
            else if (element.isJsonArray()) for (JsonElement item : element.getAsJsonArray()) if (item.isJsonObject()) add(items, item);
            else continue; // 元素是数字/字符串/null 等畸形形态：跳过这个 key，不拖死整份
            filterMap.put(entry.getKey(), items);
        }
        return filterMap;
    }

    private void add(List<Filter> items, JsonElement element) {
        try {
            Filter filter = Filter.objectFrom(element);
            if (filter != null) items.add(filter.check().trans());
        } catch (Exception ignored) {
            // 单个筛选元素畸形只丢这一条，不拖死整份结果
        }
    }
}
