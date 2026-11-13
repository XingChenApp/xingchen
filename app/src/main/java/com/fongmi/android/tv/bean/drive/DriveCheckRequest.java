package com.fongmi.android.tv.bean.drive;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.util.Collections;
import java.util.List;

public class DriveCheckRequest {

    @SerializedName("items")
    private List<DriveCheckItem> items;
    @SerializedName("view_token")
    private String viewToken;

    /**
     * 定型读法（体检类2）：items 是 List&lt;DriveCheckItem&gt;，整包反射读靠类文件 Signature
     * 保型，签名失效时元素退化成 LinkedTreeMap、check() 按 DriveCheckItem 迭代即崩。
     * 先走与旧路完全相同的整包反射读，再用程序化构造的参数化类型（不读类文件签名）
     * 按树重读 items 覆盖；修复自守、读失败保持反射结果不动。DriveCheck 进程与
     * HomeWebBridge 两处调用共用本读法。
     */
    public static DriveCheckRequest objectFrom(Gson gson, JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        DriveCheckRequest request = gson.fromJson(element, DriveCheckRequest.class);
        if (request == null || element == null || !element.isJsonObject()) return request;
        JsonElement items = element.getAsJsonObject().get("items");
        if (items == null || !items.isJsonArray()) return request;
        try {
            List<DriveCheckItem> typed = gson.fromJson(items, TypeToken.getParameterized(List.class, DriveCheckItem.class).getType());
            if (typed != null) request.items = typed;
        } catch (Exception ignored) {
        }
        return request;
    }

    public List<DriveCheckItem> getItems() {
        return items == null ? Collections.emptyList() : items;
    }

    public String getViewToken() {
        return viewToken;
    }
}
