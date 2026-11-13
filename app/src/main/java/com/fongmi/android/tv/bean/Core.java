package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.hook.Hook;
import com.github.catvod.net.OkHttp;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.util.Collections;
import java.util.List;

public class Core {

    @SerializedName("auth")
    private String auth;
    @SerializedName("name")
    private String name;
    @SerializedName("pass")
    private String pass;
    @SerializedName("broker")
    private String broker;
    @SerializedName("domain")
    private String domain;
    @SerializedName("resp")
    private String resp;
    @SerializedName("sign")
    private String sign;
    @SerializedName("pkg")
    private String pkg;
    @SerializedName("so")
    private String so;
    @SerializedName("key")
    private String key;
    @SerializedName("option")
    private List<Option> option;

    public static Core objectFrom(String str) {
        Gson gson = new Gson();
        Core core = gson.fromJson(str, Core.class);
        retype(gson, core, str);
        return core;
    }

    /**
     * 定型修复（体检类2）：option 是 List&lt;Option&gt;，整包反射读靠类文件 Signature 保型，
     * 签名失效时元素退化成 LinkedTreeMap、TVBus/LiveConfig 链按 Option 消费即崩且本方法
     * 无 try 无兜底。这里用程序化构造的参数化类型（不读类文件签名）按 JSON 树重读
     * option 覆盖；修复自守、读失败保持反射结果不动（主读的异常口径不变）。
     * Option.values 是 List&lt;String&gt;，裸读元素仍是字符串、不受签名失效影响。
     */
    private static void retype(Gson gson, Core core, String str) {
        if (core == null || str == null) return;
        try {
            JsonElement tree = JsonParser.parseString(str);
            if (tree == null || !tree.isJsonObject()) return;
            JsonElement option = tree.getAsJsonObject().get("option");
            if (option == null || !option.isJsonArray()) return;
            List<Option> typed = gson.fromJson(option, TypeToken.getParameterized(List.class, Option.class).getType());
            if (typed != null) core.option = typed;
        } catch (Exception ignored) {
        }
    }

    public static String getString(String value) {
        return (value = UrlUtil.convert(value)).startsWith("http") ? OkHttp.string(value) : value;
    }

    public String getAuth() {
        return !getResp().isEmpty() ? Server.get().getAddress("/tvbus") : TextUtils.isEmpty(auth) ? "" : UrlUtil.convert(auth);
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : getString(name);
    }

    public String getPass() {
        return TextUtils.isEmpty(pass) ? "" : getString(pass);
    }

    public String getBroker() {
        return TextUtils.isEmpty(broker) ? "" : UrlUtil.convert(broker);
    }

    public String getDomain() {
        return TextUtils.isEmpty(domain) ? "" : getString(domain);
    }

    public String getResp() {
        return TextUtils.isEmpty(resp) ? "" : getString(resp);
    }

    public String getSign() {
        return TextUtils.isEmpty(sign) ? "" : getString(sign);
    }

    public String getPkg() {
        return TextUtils.isEmpty(pkg) ? "" : getString(pkg);
    }

    public String getSo() {
        return TextUtils.isEmpty(so) ? "" : UrlUtil.convert(so);
    }

    public List<Option> getOption() {
        return option == null ? Collections.emptyList() : option;
    }

    public Hook getHook() {
        return !getPkg().isEmpty() && !getSign().isEmpty() ? new Hook(getSign(), getPkg()) : null;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Core it)) return false;
        return getSign().equals(it.getSign());
    }

    public static class Option {

        @SerializedName("key")
        private String key;
        @SerializedName("values")
        private List<String> values;

        public String getKey() {
            return TextUtils.isEmpty(key) ? "" : key;
        }

        public List<String> getValues() {
            return values == null ? Collections.emptyList() : values;
        }
    }
}
