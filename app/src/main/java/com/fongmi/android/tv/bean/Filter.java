package com.fongmi.android.tv.bean;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Trans;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Filter implements Parcelable {

    @SerializedName("key")
    private String key;
    @SerializedName("name")
    private String name;
    @SerializedName("init")
    private String init;
    @SerializedName("value")
    private List<Value> value;

    public Filter() {
    }

    protected Filter(Parcel in) {
        this.key = in.readString();
        this.name = in.readString();
        this.init = in.readString();
        this.value = new ArrayList<>();
        in.readList(this.value, Value.class.getClassLoader());
    }

    public static Filter objectFrom(JsonElement element) {
        Filter item = App.gson().fromJson(element, Filter.class);
        retype(item, element);
        return item;
    }

    /**
     * 定型修复（体检类2）：value 是 List&lt;Value&gt;，整包反射读靠类文件 Signature 保型，
     * 签名失效时元素退化成 LinkedTreeMap、下游按 Value 消费即崩。这里用程序化构造的
     * 参数化类型（不读类文件签名）按 JSON 树重读 value 覆盖；读失败保持反射结果不动。
     */
    static void retype(Filter item, JsonElement element) {
        if (item == null || element == null || !element.isJsonObject()) return;
        JsonElement value = element.getAsJsonObject().get("value");
        if (value == null || !value.isJsonArray()) return;
        try {
            List<Value> values = App.gson().fromJson(value, TypeToken.getParameterized(List.class, Value.class).getType());
            if (values != null) item.value = values;
        } catch (Exception ignored) {
        }
    }

    public static List<Filter> arrayFrom(String result) {
        Type listType = TypeToken.getParameterized(List.class, Filter.class).getType();
        List<Filter> items = App.gson().fromJson(result, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public String getInit() {
        return init;
    }

    public List<Value> getValue() {
        return value == null ? Collections.emptyList() : value;
    }

    public String setSelected(String v) {
        getValue().stream().filter(item -> item.equals(Value.create(v))).findFirst().ifPresent(item -> item.setSelected(true));
        return v;
    }

    public Filter check() {
        Iterables.removeIf(getValue(), Predicates.isNull());
        return this;
    }

    public Filter copy() {
        Filter copy = new Filter();
        copy.key = this.key;
        copy.name = this.name;
        copy.init = this.init;
        copy.value = new ArrayList<>();
        getValue().forEach(item -> copy.value.add(item.copy()));
        return copy;
    }

    public Filter trans() {
        if (Trans.pass()) return this;
        getValue().forEach(Value::trans);
        return this;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.key);
        dest.writeString(this.name);
        dest.writeString(this.init);
        dest.writeList(this.value);
    }

    public static final Creator<Filter> CREATOR = new Creator<>() {
        @Override
        public Filter createFromParcel(Parcel source) {
            return new Filter(source);
        }

        @Override
        public Filter[] newArray(int size) {
            return new Filter[size];
        }
    };
}
