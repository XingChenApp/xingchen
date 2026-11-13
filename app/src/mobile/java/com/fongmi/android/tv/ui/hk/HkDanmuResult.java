package com.fongmi.android.tv.ui.hk;

import com.fongmi.android.tv.App;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

/**
 * 环42 danmu#：8.83 DanmuSearchResult 的转写（ui/video/model/DanmuSearchResult，
 * 字段仅 title/url 两枚）。官解析口径＝JSONArray.toJSONString 后
 * JSON.parseArray(DanmuSearchResult.class)；咱家以 Gson 同形往返
 * （evalJSForObj 的 adjust 产物 List&lt;Map&gt; 先序列化再按本类解析，
 * 元素形坏到不可转时抛异常——与官 fastjson 加工期抛错同支，
 * 由 HkDanmuSearchDialog 的 catch 落「出错：」toast）。
 */
public class HkDanmuResult {

    private String title;
    private String url;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    /** 官 JSON.parseArray 等价：adjust 后的 List 原料 → List&lt;HkDanmuResult&gt;。 */
    public static List<HkDanmuResult> parseArray(List<?> raw) {
        String json = App.gson().toJson(raw);
        List<HkDanmuResult> list = App.gson().fromJson(json,
                TypeToken.getParameterized(List.class, HkDanmuResult.class).getType());
        return list == null ? new ArrayList<>() : list;
    }
}
