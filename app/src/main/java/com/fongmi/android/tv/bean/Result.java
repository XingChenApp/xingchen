package com.fongmi.android.tv.bean;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.gson.DanmakuAdapter;
import com.fongmi.android.tv.gson.FilterAdapter;
import com.fongmi.android.tv.gson.HeaderAdapter;
import com.fongmi.android.tv.gson.MsgAdapter;
import com.fongmi.android.tv.gson.SkipAdapter;
import com.fongmi.android.tv.gson.UrlAdapter;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.utils.AdBlock;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Trans;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Persister;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Root(name = "rss", strict = false)
public class Result implements Parcelable {

    @Path("class")
    @ElementList(entry = "ty", required = false, inline = true)
    @SerializedName("class")
    private List<Class> types;

    @Path("list")
    @ElementList(entry = "video", required = false, inline = true)
    @SerializedName("list")
    private List<Vod> list;

    @SerializedName("filters")
    @JsonAdapter(FilterAdapter.class)
    private LinkedHashMap<String, List<Filter>> filters;

    @SerializedName("url")
    @JsonAdapter(UrlAdapter.class)
    private Url url;

    @SerializedName("header")
    @JsonAdapter(HeaderAdapter.class)
    private Map<String, String> header;

    @SerializedName("msg")
    @JsonAdapter(MsgAdapter.class)
    private String msg;

    @SerializedName("web")
    private Web web;

    // 动作协议（动作.py 那套，参照缺位、按协议原文补）：纯提示命令回 {'toast': …}，
    // 页面 dict 也常带 toast 键（与 msg 并行，消费口在 ActionResult）。
    @SerializedName("toast")
    private String toast;

    // 动作协议：结果带 refresh=1 表示「把当前页整页换成这个结果」（文件内搜等）。
    @SerializedName("refresh")
    private Integer refresh;

    // 动作协议：{'action': {...}} 指令 dict（browser/msgbox/表单配置），原样捕获、消费口分发。
    // 存 JsonElement 而非 JsonObject：action 键的形态随协议变（dict/字符串都见过），
    // 钉死 JsonObject 会让异形值把整包结果解析打回空——那是全体源共用的解析路，不许收窄容忍度。
    @SerializedName("action")
    private JsonElement action;

    @SerializedName("danmaku")
    @JsonAdapter(DanmakuAdapter.class)
    private List<Danmaku> danmaku;

    @SerializedName("subs")
    private List<Sub> subs;
    @SerializedName("skips")
    @JsonAdapter(SkipAdapter.class)
    private List<SkipSegment> skipSegments;
    @SerializedName("playUrl")
    private String playUrl;
    @SerializedName("artwork")
    private String artwork;
    @SerializedName("jxFrom")
    private String jxFrom;
    @SerializedName("flag")
    private String flag;
    @SerializedName("desc")
    private String desc;
    @SerializedName("format")
    private String format;
    @SerializedName("click")
    private String click;
    @SerializedName("key")
    private String key;
    @SerializedName("position")
    private Long position;
    @SerializedName("pagecount")
    private Integer pagecount;
    @SerializedName("parse")
    private Integer parse;
    @SerializedName("code")
    private Integer code;
    @SerializedName("jx")
    private Integer jx;
    @SerializedName("drm")
    private Drm drm;

    /**
     * 阅读分流标记（transient：Gson 跳过、Parcel 不碰，只在进程内 Result 流转时携带）。
     * 由 SiteApi 的 playerContent 判别层置入，VideoActivity.setPlayer 消费后改道阅读器。
     */
    private transient com.fongmi.android.tv.read.ReadingSession readingSession;

    /**
     * 动作协议顶层表单/指令捕获（transient，同 readingSession 口径）：结果 JSON 顶层
     * 带 actionId 且 type∈input/multiInput/menu/msgbox/browser 时，整包 JSON 暂存于此，
     * 由 retype() 在解析时写入，ActionResult 消费端据此渲染表单/弹窗。
     */
    private transient JsonObject actionForm;

    /**
     * 海阔 PlayData 伴声音轨/歌词（transient，同 readingSession 口径）：环34
     * 终端消费补齐——SiteApi.hkPlayerContent 从 PlayData 置入，PlaySpec.from
     * 携带进播放层（相碰清单：PlaySpec 是全源共用 Result→播放器漏斗，本字段
     * 只由海阔面写入、其他源恒空，不改任何既有字段语义）。
     */
    private transient java.util.List<String> hkAudioUrls;

    /** 海阔 PlayData 歌词地址（hiker:// / file:// / / / http(s) 形态，transient）。 */
    private transient String hkLyric;

    public java.util.List<String> getHkAudioUrls() {
        return hkAudioUrls;
    }

    public void setHkAudioUrls(java.util.List<String> hkAudioUrls) {
        this.hkAudioUrls = hkAudioUrls;
    }

    public String getHkLyric() {
        return hkLyric;
    }

    public void setHkLyric(String hkLyric) {
        this.hkLyric = hkLyric;
    }

    /** 取阅读会话（无则 null）。 */
    public com.fongmi.android.tv.read.ReadingSession getReadingSession() {
        return readingSession;
    }

    /** 置阅读会话（判别层专用）。 */
    public void setReadingSession(com.fongmi.android.tv.read.ReadingSession session) {
        this.readingSession = session;
    }

    public Result() {
    }

    protected Result(Parcel in) {
        this.types = new ArrayList<>();
        in.readList(this.types, Class.class.getClassLoader());
    }

    public static Result objectFrom(String str) {
        try {
            Result result = App.gson().fromJson(str, Result.class);
            if (result != null) retype(result, str);
            return result;
        } catch (Exception e) {
            return empty();
        }
    }

    /**
     * 定型修复（体检类2，最危险一条）：types/list/subs 三个 List 字段整包反射读靠类文件
     * Signature 保型，签名失效时元素退化成 LinkedTreeMap，而 fromJson 的 trans() 在
     * objectFrom 的 try 之外逐元素强转、必崩且穿透全部站源解析入口。这里用程序化构造的
     * 参数化类型（不读类文件签名）按 JSON 树重读三字段覆盖（直写字段，不走 setList 的
     * AdBlock 副作用，保持原解析结果一字不变），元素再走 Class/Vod 自身的定型修复；
     * filters/danmaku/url/header 走 @JsonAdapter、由各自 objectFrom/适配器内的定型修复
     * 覆盖。修复全程自守，读失败保持反射结果不动。
     */
    private static void retype(Result result, String str) {
        try {
            JsonElement tree = JsonParser.parseString(str);
            if (tree == null || !tree.isJsonObject()) return;
            JsonObject object = tree.getAsJsonObject();
            JsonElement types = object.get("class");
            if (types != null && types.isJsonArray()) {
                List<Class> typed = App.gson().fromJson(types, TypeToken.getParameterized(List.class, Class.class).getType());
                if (typed != null) {
                    JsonArray array = types.getAsJsonArray();
                    for (int i = 0; i < typed.size() && i < array.size(); i++) Class.retype(typed.get(i), array.get(i));
                    result.types = typed;
                }
            }
            JsonElement list = object.get("list");
            if (list != null && list.isJsonArray()) {
                List<Vod> typed = App.gson().fromJson(list, TypeToken.getParameterized(List.class, Vod.class).getType());
                if (typed != null) {
                    JsonArray array = list.getAsJsonArray();
                    for (int i = 0; i < typed.size() && i < array.size(); i++) Vod.retype(App.gson(), typed.get(i), array.get(i));
                    result.list = typed;
                }
            }
            JsonElement subs = object.get("subs");
            if (subs != null && subs.isJsonArray()) {
                List<Sub> typed = App.gson().fromJson(subs, TypeToken.getParameterized(List.class, Sub.class).getType());
                if (typed != null) result.subs = typed;
            }
            JsonElement typeEl = object.get("type");
            JsonElement actionIdEl = object.get("actionId");
            if (typeEl != null && typeEl.isJsonPrimitive() && actionIdEl != null && actionIdEl.isJsonPrimitive()) {
                String t = typeEl.getAsString();
                if ("input".equals(t) || "multiInput".equals(t) || "menu".equals(t) || "msgbox".equals(t) || "browser".equals(t)) result.actionForm = object;
            }
        } catch (Exception ignored) {
        }
    }

    public static Result fromJson(String str) {
        Result result = objectFrom(str);
        return result == null ? empty() : result.trans();
    }

    public static Result fromXml(String str) {
        try {
            return new Persister().read(Result.class, str, false).trans();
        } catch (Exception e) {
            return empty();
        }
    }

    public static Result fromType(int type, String str) {
        return type == 0 ? fromXml(str) : fromJson(str);
    }

    public static Result fromObject(JSONObject object) {
        return object == null ? empty() : objectFrom(object.toString());
    }

    public static Result empty() {
        return new Result();
    }

    public static Result error(String msg) {
        Result result = new Result();
        result.setParse(0);
        result.setMsg(msg);
        return result;
    }

    public static Result folder(Vod item) {
        Result result = new Result();
        Class type = new Class();
        type.setTypeFlag("1");
        type.setTypeId(item.getId());
        type.setTypeName(item.getName());
        result.setTypes(Arrays.asList(type));
        return result;
    }

    public static Result type(String json) {
        Result result = new Result();
        result.setTypes(Arrays.asList(Class.objectFrom(json)));
        return result.trans();
    }

    public static Result list(List<Vod> items) {
        Result result = new Result();
        result.setList(items);
        return result;
    }

    public static Result vod(Vod item) {
        return list(Arrays.asList(item));
    }

    public List<Class> getTypes() {
        return types == null ? Collections.emptyList() : types;
    }

    public void setTypes(List<Class> types) {
        this.types = types;
    }

    public List<Vod> getList() {
        return list == null ? Collections.emptyList() : list;
    }

    public void setList(List<Vod> list) {
        this.list = AdBlock.filter(list);
    }

    public LinkedHashMap<String, List<Filter>> getFilters() {
        return filters == null ? new LinkedHashMap<>() : filters;
    }

    public Url getUrl() {
        return url == null ? Url.create() : url;
    }

    public void setUrl(String url) {
        this.url = getUrl().replace(url);
    }

    public String getMsg() {
        return TextUtils.isEmpty(msg) || getCode() != 0 ? "" : msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public synchronized Web takeWeb() {
        Web value = web;
        web = null;
        return value;
    }

    public String getToast() {
        return TextUtils.isEmpty(toast) ? "" : toast;
    }

    public boolean hasToast() {
        return !getToast().isEmpty();
    }

    public boolean isRefresh() {
        return refresh != null && refresh == 1;
    }

    public JsonObject getAction() {
        return action != null && action.isJsonObject() ? action.getAsJsonObject() : null;
    }

    public JsonObject getActionForm() {
        return actionForm;
    }

    public List<Sub> getSubs() {
        return subs == null ? new ArrayList<>() : new ArrayList<>(subs);
    }

    public List<SkipSegment> getSkipSegments() {
        return skipSegments == null ? Collections.emptyList() : skipSegments;
    }

    public void setSubs(List<Sub> subs) {
        if (getSubs().isEmpty()) this.subs = subs;
    }

    public Map<String, String> getHeader() {
        return header == null ? new HashMap<>() : header;
    }

    public void setHeader(Map<String, String> header) {
        if (getHeader().isEmpty()) this.header = header;
    }

    public String getPlayUrl() {
        return TextUtils.isEmpty(playUrl) ? "" : playUrl;
    }

    public void setPlayUrl(String playUrl) {
        this.playUrl = playUrl;
    }

    public String getArtwork() {
        return TextUtils.isEmpty(artwork) ? "" : artwork;
    }

    public String getJxFrom() {
        return TextUtils.isEmpty(jxFrom) ? "" : jxFrom;
    }

    public String getFlag() {
        return TextUtils.isEmpty(flag) ? "" : flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getDesc() {
        return TextUtils.isEmpty(desc) ? "" : Util.clean(desc);
    }

    public List<Danmaku> getDanmaku() {
        // 上游 Result.java:295-298 原文：未加载时返回空表；否则空字段时建表并回填字段。
        if (!DanmakuSetting.isLoad()) return new ArrayList<>();
        return danmaku = danmaku == null ? new ArrayList<>() : danmaku;
    }

    /** 程序化装配用（上游只有 JSON 适配器写此字段、无 setter）：海阔播放 JSON 的 danmu 附带字段经此入 Result（G49/G59）。 */
    public void setDanmaku(List<Danmaku> danmaku) {
        this.danmaku = danmaku;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getClick() {
        return TextUtils.isEmpty(click) ? "" : click;
    }

    public void setClick(String click) {
        this.click = click;
    }

    public String getKey() {
        return TextUtils.isEmpty(key) ? "" : key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Long getPosition() {
        return position;
    }

    public Integer getPageCount() {
        return pagecount == null ? 0 : pagecount;
    }

    public Integer getParse() {
        return parse == null ? 0 : parse;
    }

    public void setParse(Integer parse) {
        this.parse = parse;
    }

    public Integer getCode() {
        return code == null ? 0 : code;
    }

    public Integer getJx() {
        return jx == null ? 0 : jx;
    }

    public Drm getDrm() {
        return drm;
    }

    public void setDrm(Drm drm) {
        this.drm = drm;
    }

    public boolean hasMsg() {
        return !getMsg().isEmpty();
    }

    public boolean hasArtwork() {
        return !getArtwork().isEmpty();
    }

    public boolean hasPosition() {
        return getPosition() != null;
    }

    public boolean hasDesc() {
        return !getDesc().isEmpty();
    }

    public boolean shouldUseParse() {
        if (!VodConfig.hasParse()) return false;
        return (getPlayUrl().isEmpty() && VodConfig.get().getFlags().contains(getFlag())) || getJx() == 1;
    }

    // 上游 Result.java:379-382 原文名 isUseParse（VodPlaybackController:172、
    // VodPreloader:118 调此名；本树旧名 shouldUseParse 保留，不删，避免别处引用断链）。
    public boolean isUseParse() {
        if (!VodConfig.hasParse()) return false;
        return (getPlayUrl().isEmpty() && VodConfig.get().getFlags().contains(getFlag())) || getJx() == 1;
    }

    public boolean needParse() {
        return getParse() == 1 || getJx() == 1;
    }

    public String getRealUrl() {
        return getPlayUrl() + getUrl().v();
    }

    public Style getStyle(Style style) {
        return getList().isEmpty() ? Style.rect() : getList().get(0).getStyle(style);
    }

    public Vod getVod() {
        return getList().isEmpty() ? new Vod() : getList().get(0);
    }

    public Result clear() {
        getList().clear();
        return this;
    }

    public Result trans() {
        if (Trans.pass()) return this;
        getTypes().forEach(Class::trans);
        getList().forEach(Vod::trans);
        getSubs().forEach(Sub::trans);
        return this;
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeList(this.types);
    }

    public static final Creator<Result> CREATOR = new Creator<>() {
        @Override
        public Result createFromParcel(Parcel source) {
            return new Result(source);
        }

        @Override
        public Result[] newArray(int size) {
            return new Result[size];
        }
    };
}
