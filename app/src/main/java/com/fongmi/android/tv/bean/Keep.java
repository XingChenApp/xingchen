package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.impl.Diffable;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

// 复合主键 (key, cid, type)：同一 key 在不同配置（cid）或不同类型（点播 0／直播 1）
// 下各存一行，互不顶替。此前单 key 主键下 insertOrUpdate 整行覆盖，同名收藏跨配置
// 写层互覆（H14）；数据库 40→41 迁移同步换主键，见 Migrations.MIGRATION_40_41。
@Entity(primaryKeys = {"key", "cid", "type"})
public class Keep implements Diffable<Keep> {

    @NonNull
    @SerializedName("key")
    private String key;
    @SerializedName("siteName")
    private String siteName;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodPic")
    private String vodPic;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("type")
    private int type;
    @SerializedName("cid")
    private int cid;

    public static List<Keep> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, Keep.class).getType();
        List<Keep> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public static Keep find(String key) {
        return find(VodConfig.getCid(), key);
    }

    public static Keep find(int cid, String key) {
        return AppDatabase.get().getKeepDao().find(cid, key);
    }

    public static boolean exist(String key) {
        return exist(LiveConfig.get().getConfig().getId(), key);
    }

    public static boolean exist(int cid, String key) {
        return AppDatabase.get().getKeepDao().findLive(cid, key) != null;
    }

    public static Keep findLive(int cid, String key) {
        return AppDatabase.get().getKeepDao().findLive(cid, key);
    }

    public static void deleteAll() {
        AppDatabase.get().getKeepDao().delete();
    }

    public static void delete(int cid) {
        AppDatabase.get().getKeepDao().delete(cid);
    }

    public static void delete(String key) {
        delete(LiveConfig.get().getConfig().getId(), key);
    }

    public static void delete(int cid, String key) {
        AppDatabase.get().getKeepDao().deleteLive(cid, key);
    }

    public static void replace(String oldKey, String newKey) {
        if (oldKey.equals(newKey)) return;
        Keep keep = find(oldKey);
        if (keep == null) return;
        keep.delete();
        keep.setKey(newKey);
        keep.save();
    }

    public static List<Keep> getVod() {
        return AppDatabase.get().getKeepDao().getVod();
    }

    public static List<Keep> getLive() {
        return AppDatabase.get().getKeepDao().getLive();
    }

    public static void sync(List<Config> configs, List<Keep> targets) {
        targets.forEach(target -> configs.stream()
                .filter(config -> target.getCid() == config.getId()).findFirst()
                .ifPresent(config -> {
                    // 复合主键下改 cid 是换行不是改行：先删旧 (key, cid, type) 行再以新 cid 存，
                    // 否则旧 cid 行残留成孤儿（同一收藏在两个配置名下重复出现）。
                    target.delete();
                    target.save(Config.find(config).getId());
                }));
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public void setKey(@NonNull String key) {
        this.key = key;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getVodName() {
        return vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodPic() {
        return vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getCid() {
        return cid;
    }

    public void setCid(int cid) {
        this.cid = cid;
    }

    public String getSiteKey() {
        return getKey().split(AppDatabase.SYMBOL)[0];
    }

    public String getVodId() {
        String[] parts = Objects.toString(getKey(), "").split(AppDatabase.SYMBOL);
        return parts.length > 1 ? parts[1] : "";
    }

    public void save(int cid) {
        setCid(cid);
        save();
    }

    public void save() {
        AppDatabase.get().getKeepDao().insertOrUpdate(this);
    }

    public Keep delete() {
        if (getType() == 1) AppDatabase.get().getKeepDao().deleteLive(getCid(), getKey());
        else AppDatabase.get().getKeepDao().delete(getCid(), getKey());
        return this;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Keep it)) return false;
        // 与复合主键同口径：key＋cid＋type 三元才算同一行；收藏页按 type 混排全部 cid，
        // 只比 key 会把同 key 不同配置的两行判成同一项，DiffUtil 刷新错位。
        return getCid() == it.getCid() && getType() == it.getType() && Objects.equals(getKey(), it.getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey(), getCid(), getType());
    }

    @Override
    public boolean isSameItem(Keep other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Keep other) {
        return Objects.equals(getVodName(), other.getVodName()) && Objects.equals(getVodPic(), other.getVodPic()) && getCreateTime() == other.getCreateTime();
    }
}
