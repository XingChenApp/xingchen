package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Trans;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Group {

    @SerializedName("channel")
    private List<Channel> channel;
    @SerializedName("name")
    private String name;
    @SerializedName("pass")
    private String pass;

    private boolean selected;
    private int position;
    private int width;

    public Group(String name) {
        this(name, false);
    }

    public Group(String name, boolean pass) {
        this.name = name;
        this.position = -1;
        if (name.contains("_")) parse(pass);
        if (name.isEmpty()) setName(ResUtil.getString(R.string.setting_live));
    }

    public static List<Group> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, Group.class).getType();
        List<Group> items = App.gson().fromJson(str, listType);
        if (items == null) return Collections.emptyList();
        try {
            JsonElement tree = JsonParser.parseString(str);
            if (tree != null && tree.isJsonArray()) {
                JsonArray array = tree.getAsJsonArray();
                for (int i = 0; i < items.size() && i < array.size(); i++) retype(items.get(i), array.get(i));
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    /**
     * 定型修复（体检类2）：channel 是 List&lt;Channel&gt;，元素随 Group 反射读时靠类文件
     * Signature 保型，签名失效时退化成 LinkedTreeMap、按 Channel 消费即崩。这里用
     * 程序化构造的参数化类型（不读类文件签名）按 JSON 树重读 channel 覆盖，元素再走
     * Channel 自身的定型修复；读失败保持反射结果不动。Live 定型修复复用本方法。
     */
    static void retype(Group item, JsonElement element) {
        if (item == null || element == null || !element.isJsonObject()) return;
        JsonElement channel = element.getAsJsonObject().get("channel");
        if (channel == null || !channel.isJsonArray()) return;
        try {
            List<Channel> typed = App.gson().fromJson(channel, TypeToken.getParameterized(List.class, Channel.class).getType());
            if (typed == null) return;
            JsonArray array = channel.getAsJsonArray();
            for (int i = 0; i < typed.size() && i < array.size(); i++) Channel.retype(typed.get(i), array.get(i));
            item.setChannel(typed);
        } catch (Exception ignored) {
        }
    }

    public static Group create() {
        return create(R.string.setting_live);
    }

    public static Group create(@StringRes int resId) {
        return new Group(ResUtil.getString(resId));
    }

    public static Group create(String name, boolean pass) {
        return new Group(name, pass);
    }

    private void parse(boolean pass) {
        String[] splits = name.split("_", 2);
        setName(splits[0]);
        if (pass || splits.length == 1) return;
        setPass(splits[1]);
    }

    public List<Channel> getChannel() {
        return channel = channel == null ? new ArrayList<>() : channel;
    }

    public void setChannel(List<Channel> channel) {
        this.channel = channel;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPass() {
        return TextUtils.isEmpty(pass) ? "" : pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public boolean isHidden() {
        return !TextUtils.isEmpty(getPass());
    }

    public boolean isKeep() {
        return getName().equals(ResUtil.getString(R.string.keep));
    }

    public boolean isEmpty() {
        return getChannel().isEmpty();
    }

    public boolean skip() {
        return isKeep();
    }

    public int find(int number) {
        return getChannel().lastIndexOf(Channel.create(number));
    }

    public int find(String name) {
        return getChannel().lastIndexOf(Channel.create(name));
    }

    public void add(Channel channel) {
        Channel exist = getChannel().stream().filter(item -> item.equals(channel)).findFirst().orElse(null);
        if (exist != null) exist.getUrls().addAll(channel.getUrls());
        else getChannel().add(Channel.create(channel));
    }

    public Channel find(Channel channel) {
        Channel exist = getChannel().stream().filter(item -> item.equals(channel)).findFirst().orElse(null);
        if (exist != null) return exist;
        getChannel().add(channel);
        return channel;
    }

    public Channel current() {
        // position 缺省 -1、列表可空，直取 get(position) 必越界。调用方（LiveActivity 共 6 处）
        // 拿到结果立刻解引用、无一判空，回 null 只是把崩移到调用方，故按 Channel.getIndex
        // 的既有口径在出口钳位：非空时钳到 [0, size-1]，空时回一个绑本组的新 Channel
        // （其 getCurrent/getData 对空表均有守卫、回空值不崩）。
        List<Channel> channels = getChannel();
        if (channels.isEmpty()) return new Channel().group(this);
        int position = Math.min(Math.max(getPosition(), 0), channels.size() - 1);
        return channels.get(position).group(this);
    }

    public Group trans() {
        if (Trans.pass()) return this;
        this.name = Trans.s2t(name);
        return this;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (!(obj instanceof Group it)) return false;
        return getName().equals(it.getName()) && getChannel().size() == it.getChannel().size();
    }
}
