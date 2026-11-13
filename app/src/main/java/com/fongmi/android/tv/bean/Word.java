package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.impl.Diffable;
import com.github.catvod.utils.Trans;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.util.Collections;
import java.util.List;

public class Word {

    @SerializedName("data")
    private List<Data> data;

    public static Word objectFrom(String str) {
        try {
            Word word = App.gson().fromJson(str, Word.class);
            if (word == null) return new Word();
            retype(word, str);
            return word.trans();
        } catch (Exception e) {
            return new Word();
        }
    }

    /**
     * 定型修复（体检类2）：data 是 List&lt;Data&gt;，整包反射读靠类文件 Signature 保型，
     * 签名失效时元素退化成 LinkedTreeMap，trans() 的逐元素强转在同一 try 内被吞成
     * 热搜词整份回空。这里用程序化构造的参数化类型（不读类文件签名）按 JSON 树重读
     * data 覆盖（须在 trans 之前）；读失败保持反射结果不动。
     */
    private static void retype(Word word, String str) {
        if (str == null) return;
        try {
            JsonElement tree = JsonParser.parseString(str);
            if (tree == null || !tree.isJsonObject()) return;
            JsonElement data = tree.getAsJsonObject().get("data");
            if (data == null || !data.isJsonArray()) return;
            List<Data> typed = App.gson().fromJson(data, TypeToken.getParameterized(List.class, Data.class).getType());
            if (typed != null) word.data = typed;
        } catch (Exception ignored) {
        }
    }

    public Word trans() {
        if (Trans.pass()) return this;
        getData().forEach(Data::trans);
        return this;
    }

    public List<Data> getData() {
        return data == null ? Collections.emptyList() : data;
    }

    public static class Data implements Diffable<Data> {

        @SerializedName(value = "title", alternate = "name")
        private String title;

        public static Data create(String title) {
            Data data = new Data();
            data.title = title;
            return data.trans();
        }

        public String getTitle() {
            return TextUtils.isEmpty(title) ? "" : title;
        }

        @Override
        public boolean isSameItem(Data other) {
            return getTitle().equals(other.getTitle());
        }

        @Override
        public boolean isSameContent(Data other) {
            return getTitle().equals(other.getTitle());
        }

        public Data trans() {
            this.title = Trans.s2t(title);
            return this;
        }
    }
}
