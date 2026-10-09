package com.fongmi.android.tv.api.hk;

/**
 * 详情解析出的原始条目（M3）。
 * 选择器：detail_find_rule 6 段 = 列表;标题;图片;描述;链接;样式。
 * JS：setResult 项的 title/url/pic_url/desc + 可选 line（线路名）/col_type。
 */
public class HkDetailItem {

    private String title = "";
    private String url = "";
    private String pic = "";
    private String desc = "";
    private String line = "";
    private String colType = "";

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url == null ? "" : url;
    }

    public String getPic() {
        return pic;
    }

    public void setPic(String pic) {
        this.pic = pic == null ? "" : pic;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc == null ? "" : desc;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line == null ? "" : line;
    }

    public String getColType() {
        return colType;
    }

    public void setColType(String colType) {
        this.colType = colType == null ? "" : colType;
    }

    /** 海报卡/纯展示项：无可播链接。 */
    public boolean isDisplayOnly() {
        String u = url.trim();
        return u.isEmpty() || "javascript:;".equalsIgnoreCase(u) || "javascript:void(0)".equalsIgnoreCase(u);
    }
}
