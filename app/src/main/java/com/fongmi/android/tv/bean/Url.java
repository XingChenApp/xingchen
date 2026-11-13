package com.fongmi.android.tv.bean;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class Url {

    @SerializedName("values")
    private List<Value> values;
    @SerializedName("position")
    private int position;

    public static Url objectFrom(JsonElement element) {
        try {
            Url url = App.gson().fromJson(element, Url.class);
            if (url == null) return create();
            retype(url, element);
            return url;
        } catch (Exception e) {
            return create();
        }
    }

    /**
     * 定型修复（体检类2）：values 是 List&lt;Value&gt;，整包反射读靠类文件 Signature 保型，
     * 签名失效时元素退化成 LinkedTreeMap、getValues() 下游按 Value 取用即崩。这里用程序化
     * 构造的参数化类型（不读类文件签名）按 JSON 树重读 values 覆盖；读失败保持反射结果不动。
     */
    private static void retype(Url url, JsonElement element) {
        if (element == null || !element.isJsonObject()) return;
        JsonElement values = element.getAsJsonObject().get("values");
        if (values == null || !values.isJsonArray()) return;
        try {
            List<Value> typed = App.gson().fromJson(values, TypeToken.getParameterized(List.class, Value.class).getType());
            if (typed != null) url.values = typed;
        } catch (Exception ignored) {
        }
    }

    public static Url create() {
        return new Url();
    }

    public List<Value> getValues() {
        return values = values == null ? new ArrayList<>() : values;
    }

    public int getPosition() {
        return position;
    }

    public Uri uri() {
        return Uri.parse(v());
    }

    public String v() {
        return v(getPosition());
    }

    public String v(int position) {
        return position >= getValues().size() ? "" : getValues().get(position).getV();
    }

    public String n(int position) {
        return position >= getValues().size() ? "" : getValues().get(position).getN();
    }

    public Url add(String v) {
        getValues().add(Value.create(v));
        return this;
    }

    public Url add(String n, String v) {
        getValues().add(Value.create(n, v));
        return this;
    }

    public Url replace(String url) {
        if (getValues().isEmpty()) add(url);
        else getValues().get(getPosition()).setV(url);
        return this;
    }

    public Url set(int position) {
        this.position = Math.min(position, getValues().size() - 1);
        return this;
    }

    public boolean isEmpty() {
        return getValues().isEmpty() || TextUtils.isEmpty(v());
    }

    public boolean isMulti() {
        return getValues().size() > 1;
    }
}
