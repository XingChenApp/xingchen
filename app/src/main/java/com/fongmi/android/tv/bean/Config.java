package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.db.AppDatabase;
import com.github.catvod.utils.Prefers;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

@Entity(indices = @Index(value = {"url", "type"}, unique = true))
public class Config {

    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    private int id;
    @SerializedName("type")
    private int type;
    @SerializedName("time")
    private long time;
    @SerializedName("url")
    private String url;
    @SerializedName("json")
    private String json;
    @SerializedName("name")
    private String name;
    @SerializedName("logo")
    private String logo;
    @SerializedName("home")
    private String home;
    @SerializedName("parse")
    private String parse;

    @Ignore
    @SerializedName("notice")
    private String notice;
    @Ignore
    @SerializedName("danmaku")
    private String danmaku;
    @Ignore
    @SerializedName("assrt")
    private String assrt;

    public static List<Config> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, Config.class).getType();
        List<Config> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public static Config objectFrom(String str) {
        try {
            Config config = App.gson().fromJson(str, Config.class);
            return config == null ? new Config() : config;
        } catch (Exception e) {
            // 畸形 JSON 不直穿调用方（S5-10）：Config.find(Config) 对 null 入参无守卫，给空对象
            return new Config();
        }
    }

    public static Config create(int type) {
        return new Config().type(type);
    }

    public static Config create(int type, String url) {
        return new Config().type(type).url(url).insert();
    }

    public static Config create(int type, String url, String name) {
        return new Config().type(type).url(url).name(name).insert();
    }

    public static List<Config> getAll(int type) {
        return AppDatabase.get().getConfigDao().findByType(type);
    }

    public static List<Config> findUrls() {
        return AppDatabase.get().getConfigDao().findUrlByType(0);
    }

    public static void delete(String url) {
        AppDatabase.get().getConfigDao().delete(url);
    }

    public static void delete(String url, int type) {
        AppDatabase.get().getConfigDao().delete(url, type);
    }

    /**
     * 名单内亲手删条目的「全清」口径（与 {@link #markCleared} 的撤下严格分开：
     * 撤下只落标记，条目和它的残留全留；本方法只许名单里的删除动作调用）。
     * 删一个条目，要跟它一起清干净的残留：
     * <ul>
     *   <li>配置行本身；点播条目还要带走同 URL 的派生直播行——加载点播配置时
     *   initLive 按同 URL 建的 type=1 行，删点播不带走它，冷启 LiveConfig 还会
     *   拿着已删点播的地址当独立直播配置继续加载。</li>
     *   <li>Prefers 里指着它的 {@code config_<type>} 指针：不清的话冷启
     *   {@link #lastActive} 按指针找行落空、回退 findOne 把同类型剩余的最新一行
     *   静默顶成当前配置——删掉的接口「阴魂不散」、顶上来的配置每次冷启弹它
     *   自己的公告，断点就在这里；顶替者加载成功后又经 update() 改写指针、
     *   刷新 time，顶替还会自我锁定。</li>
     *   <li>删的正是当前条目（或指针正指着它）时落 {@link #markCleared}：
     *   冷启回空配置，不许任何剩余行顶替生效。</li>
     * </ul>
     * 观看历史与收藏是用户自己的数据，不归本方法管，一条不碰。
     * 猫源 bundle 的落盘与在服务进程由调用方另走 CatSource 的孤儿清理。
     *
     * @param wasCurrent 调用方按内存态判定的「删的是不是当前在用条目」
     * @return 当前槽位是否被这次删除影响（调用方据此清内存态、发刷新事件）
     */
    public static boolean deleteEntry(String url, int type, boolean wasCurrent) {
        if (TextUtils.isEmpty(url)) return false;
        AppDatabase.get().getConfigDao().delete(url, type);
        if (type == 0) AppDatabase.get().getConfigDao().delete(url, 1);
        boolean pointed = url.equals(Prefers.getString("config_" + type));
        if (pointed) Prefers.remove("config_" + type);
        boolean slotHit = wasCurrent || pointed;
        if (slotHit) markCleared(type);
        return slotHit;
    }

    /**
     * 用户亲手清掉当前槽位的持久标记。findOne 取的是历史表里 time 最新的一行，
     * 清掉当前行后下一条历史就会顶上来当当前配置——清槽位的口径是「清成空、
     * 不自动切别的线路」，所以清空动作必须落这个标记：vod()/live()/wall() 见标记
     * 直接回空配置；任一配置经 update() 成为当前（新加/切换/加载成功）时摘除。
     * 从没配过（首次安装、无标记无记录）不走标记，原样回空，不受影响。
     */
    public static void markCleared(int type) {
        Prefers.put("config_cleared_" + type, true);
    }

    private static boolean isCleared(int type) {
        return Prefers.getBoolean("config_cleared_" + type, false);
    }

    /**
     * 启动时取「上次实际使用」的配置，而不是数据库里 time 最新的一行（照上游默影视口径恢复）。
     *
     * <p>{@link #update()} 每次加载成功都会同时写 time 和 {@code config_<type>} 偏好。只按
     * {@code ORDER BY time DESC} 取最新行：一旦某行（典型是猫源）成功加载过一次，它的 time 就是最新的；
     * 之后每次冷启动都会优先再拉它——猫源要重启 :node 子进程，慢机器上就绪探测超时，启动就显示
     * "配置取得失败"，而用户实际在用的 http 接口根本没被加载。
     *
     * <p>这里先按持久化的 {@code config_<type>} 偏好精确找行；偏好缺失或对应行已被删掉时，回退 findOne。
     * 清空标记先于本方法裁决（vod()/live()/wall() 见标记直接回空），两口径互不干扰。
     */
    private static Config lastActive(int type) {
        String url = Prefers.getString("config_" + type);
        if (!TextUtils.isEmpty(url)) {
            Config item = AppDatabase.get().getConfigDao().find(url, type);
            if (item != null) return item;
        }
        return AppDatabase.get().getConfigDao().findOne(type);
    }

    public static Config vod() {
        if (isCleared(0)) return create(0);
        Config item = lastActive(0);
        return item == null ? create(0) : item;
    }

    public static Config live() {
        if (isCleared(1)) return create(1);
        Config item = lastActive(1);
        return item == null ? create(1) : item;
    }

    public static Config wall() {
        if (isCleared(2)) return create(2);
        Config item = lastActive(2);
        return item == null ? create(2) : item;
    }

    public static Config find(int id) {
        return AppDatabase.get().getConfigDao().findById(id);
    }

    public static Config find(String url, int type) {
        Config item = AppDatabase.get().getConfigDao().find(url, type);
        return item == null ? create(type, url) : item.type(type);
    }

    public static Config find(String url, String name, int type) {
        Config item = AppDatabase.get().getConfigDao().find(url, type);
        return item == null ? create(type, url, name) : item.type(type).name(name);
    }

    public static Config find(Config config) {
        return find(config, config.getType());
    }

    public static Config find(Config config, int type) {
        Config item = AppDatabase.get().getConfigDao().find(config.getUrl(), type);
        return item == null ? create(type, config.getUrl(), config.getName()) : item.type(type).name(config.getName());
    }

    public static Config find(Depot depot, int type) {
        Config item = AppDatabase.get().getConfigDao().find(depot.getUrl(), type);
        return item == null ? create(type, depot.getUrl(), depot.getName()) : item.type(type).name(depot.getName());
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getHome() {
        return home;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public String getParse() {
        return parse;
    }

    public void setParse(String parse) {
        this.parse = parse;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getNotice() {
        return notice;
    }

    public void setNotice(String notice) {
        this.notice = notice;
    }

    public String getDanmaku() {
        return danmaku;
    }

    public void setDanmaku(String danmaku) {
        this.danmaku = danmaku;
    }

    public String getAssrt() {
        return assrt == null ? "" : assrt;
    }

    public void setAssrt(String assrt) {
        this.assrt = assrt;
    }

    public Config type(int type) {
        setType(type);
        return this;
    }

    public Config url(String url) {
        setUrl(url);
        return this;
    }

    public Config json(String json) {
        setJson(json);
        return this;
    }

    public Config name(String name) {
        setName(name);
        return this;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public String getDesc() {
        if (!TextUtils.isEmpty(getName())) return getName();
        if (!TextUtils.isEmpty(getUrl())) return getUrl();
        return "";
    }

    public Config insert() {
        if (isEmpty()) return this;
        setId(Math.toIntExact(AppDatabase.get().getConfigDao().insert(this)));
        return this;
    }

    public Config save() {
        if (isEmpty()) return this;
        AppDatabase.get().getConfigDao().insertOrUpdate(this);
        return this;
    }

    public Config update() {
        if (isEmpty()) return this;
        setTime(System.currentTimeMillis());
        Prefers.put("config_" + getType(), getUrl());
        Prefers.remove("config_cleared_" + getType());
        return save();
    }

    public void delete() {
        AppDatabase.get().getConfigDao().delete(getUrl(), getType());
        History.delete(getId());
        Keep.delete(getId());
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Config it)) return false;
        return getId() == it.getId();
    }
}
