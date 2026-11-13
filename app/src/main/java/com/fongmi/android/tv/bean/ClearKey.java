package com.fongmi.android.tv.bean;

import android.util.Base64;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Util;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class ClearKey {

    @SerializedName("keys")
    private List<Keys> keys;
    @SerializedName("type")
    private String type;

    public static ClearKey objectFrom(String str) throws Exception {
        ClearKey item = App.gson().fromJson(str, ClearKey.class);
        if (item.keys == null) throw new Exception();
        // 定型失败按本方法既有约定抛给调用方（LiveParser 落回 ClearKey.get
        // 重组、MediaItemFactory 判 false），不许把未定型 keys 静默交出去。
        if (!retype(item, str)) throw new Exception("ClearKey retype failed");
        return item;
    }

    /**
     * 定型修复（体检类2 回扫补齐）：keys 是 List&lt;Keys&gt;，整包反射读靠类文件 Signature
     * 保型，签名失效时元素退化成 LinkedTreeMap，DRM 消费点强转必崩。这里用程序化构造的
     * 参数化类型（不读类文件签名）按 JSON 树重读 keys 覆盖。回 true 表示已定型或
     * 本来就没有 keys 数组可定型；重读失败回 false，由 objectFrom 抛给调用方区分。
     */
    private static boolean retype(ClearKey item, String str) {
        try {
            com.google.gson.JsonElement tree = com.google.gson.JsonParser.parseString(str);
            if (tree == null || !tree.isJsonObject()) return true;
            com.google.gson.JsonElement keys = tree.getAsJsonObject().get("keys");
            if (keys == null || !keys.isJsonArray()) return true;
            java.util.List<Keys> typed = App.gson().fromJson(keys, com.google.gson.reflect.TypeToken.getParameterized(java.util.List.class, Keys.class).getType());
            if (typed == null) return false;
            item.keys = typed;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasKeys() {
        return keys != null && !keys.isEmpty();
    }

    public static ClearKey get(String line) {
        ClearKey item = new ClearKey();
        item.keys = new ArrayList<>();
        item.type = "temporary";
        item.addKeys(line);
        return item;
    }

    private void addKeys(String line) {
        int flags = Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP;
        for (String s : line.split(",")) {
            String[] a = s.split(":");
            // 无冒号的段没有 a[1]，直取必越界；这类畸形行跳过，其余段照常入表。
            if (a.length < 2) continue;
            String kid = Util.base64(Util.hex2byte(a[0].trim()), flags).replace("=", "");
            String k = Util.base64(Util.hex2byte(a[1].trim()), flags).replace("=", "");
            keys.add(new Keys(kid, k));
        }
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }

    public static class Keys {

        @SerializedName("kty")
        private String kty;
        @SerializedName("k")
        private String k;
        @SerializedName("kid")
        private String kid;

        public Keys(String kid, String k) {
            this.kty = "oct";
            this.kid = kid;
            this.k = k;
        }
    }
}
