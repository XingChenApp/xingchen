package com.example.hikerview.constants;

/**
 * 照官逐字转写（官包 smali/com/example/hikerview/constants/Media.smali 全文）：
 * 七个静态类型串初值照官 &lt;clinit&gt; 取 MediaType 同名枚举的 getName()；
 * 四构造与八个存取法逐字照官。本类为 UrlDetector.getMediaType 的返回类，
 * 随④团嗅探栈一并转写记名。
 */
public class Media {

    public static String VIDEO = MediaType.VIDEO.getName();
    public static String VIDEO_MUSIC = MediaType.VIDEO_MUSIC.getName();
    public static String IMAGE = MediaType.IMAGE.getName();
    public static String MUSIC = MediaType.MUSIC.getName();
    public static String HTML = MediaType.HTML.getName();
    public static String OTHER = MediaType.OTHER.getName();
    public static String BLOCK = MediaType.BLOCK.getName();

    private String name;
    private String realUrl;
    private String size;
    private String type;

    public Media(MediaType mediaType) {
        this.name = mediaType.getName();
    }

    public Media(String name) {
        this.name = name;
    }

    public Media(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public Media(String name, String type, String size) {
        this.name = name;
        this.type = type;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRealUrl() {
        return realUrl;
    }

    public void setRealUrl(String realUrl) {
        this.realUrl = realUrl;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
