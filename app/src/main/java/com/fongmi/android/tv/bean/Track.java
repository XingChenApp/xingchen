package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.db.AppDatabase;

import java.util.Collections;
import java.util.List;

@Entity(indices = @Index(value = {"key", "type"}, unique = true))
public class Track {

    private static final String DISABLED = "__disabled__";

    // 树适配：下面五个字段与 ROLE 常量是上游新版 Track 的成员，抄入的 TrackUtil 要用；
    // 本树先以 @Ignore 运行时字段接入、不落库——落库要动 Track 表结构与数据库版本，
    // 本批只修编译，落库迁移留待点播挂接批次单独验。访问器与 isSecondary 与上游同口径。
    public static final int ROLE_PRIMARY = 0;
    public static final int ROLE_SECONDARY = 1;

    @PrimaryKey(autoGenerate = true)
    private int id;
    private int type;
    private String key;
    private String name;
    private String format;
    private boolean selected;
    @Ignore
    private String playerId;
    @Ignore
    private int role;
    @Ignore
    private int ordinal;
    @Ignore
    private String label;
    @Ignore
    private String language;
    @Ignore
    private String mimeType;

    public Track(int type, String name, String format) {
        this.type = type;
        this.name = name;
        this.format = format;
        this.role = ROLE_PRIMARY;
        this.ordinal = -1;
    }

    public static Track disabled(int type, String name) {
        Track track = new Track(type, name, DISABLED);
        track.setSelected(true);
        return track;
    }

    public static List<Track> find(String key) {
        return TextUtils.isEmpty(key) ? Collections.emptyList() : AppDatabase.get().getTrackDao().find(key);
    }

    public static void delete(String key) {
        if (TextUtils.isEmpty(key)) return;
        AppDatabase.get().getTrackDao().delete(key);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public Track playerId(String playerId) {
        setPlayerId(playerId);
        return this;
    }

    public Track key(String key) {
        setKey(key);
        return this;
    }

    public int getRole() {
        return role;
    }

    public void setRole(int role) {
        this.role = role;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public boolean isSecondary() {
        return role == ROLE_SECONDARY;
    }

    public boolean isDisabled() {
        return DISABLED.equals(getFormat());
    }

    public Track toggle() {
        setSelected(!isSelected());
        return this;
    }

    public Track save() {
        if (TextUtils.isEmpty(getKey())) return this;
        AppDatabase.get().getTrackDao().insert(this);
        return this;
    }
}
