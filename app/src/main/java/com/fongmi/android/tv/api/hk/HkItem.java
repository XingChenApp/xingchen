package com.fongmi.android.tv.api.hk;

import java.util.HashMap;
import java.util.Map;

/**
 * 海阔规则解析出的一条列表/搜索结果。
 * 对应设计文档 §5 HkItem。
 */
public class HkItem {

    private String title;
    private String url;
    private String pic;
    private String desc;
    /** 海阔 col_type（movie_3/pic_1_full/input 等，deleteItemByCls/updateItem 用）。 */
    private String colType = "";
    /** 条目 extra 对象：pageTitle/newWindow/id/cls/lineVisible/textAlign 等（updateItem/deleteItem/findItemsByCls 用）。 */
    private Map<String, String> extra = new HashMap<>();
    /** 搜索多内容字段（官方 content）。 */
    private String content = "";
    /** 明细线路名（官方 line，详情页按 line 分组用；列表里一般为空）。 */
    private String line = "";

    public HkItem() {
    }

    public HkItem(String title, String url, String pic, String desc) {
        this.title = title;
        this.url = url;
        this.pic = pic;
        this.desc = desc;
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPic() {
        return pic == null ? "" : pic;
    }

    public void setPic(String pic) {
        this.pic = pic;
    }

    public String getDesc() {
        return desc == null ? "" : desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getColType() {
        return colType == null ? "" : colType;
    }

    public void setColType(String colType) {
        this.colType = colType;
    }

    public Map<String, String> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, String> extra) {
        this.extra = extra == null ? new HashMap<>() : extra;
    }

    /** extra 里取单个值（id/cls/pageTitle 等），缺省 ""。 */
    public String getExtra(String key) {
        if (extra == null || key == null) return "";
        String v = extra.get(key);
        return v == null ? "" : v;
    }

    public String getContent() {
        return content == null ? "" : content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLine() {
        return line == null ? "" : line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    @Override
    public String toString() {
        return "HkItem{title='" + title + "', url='" + url + "', pic='" + pic + "', desc='" + desc + "'}";
    }
}
