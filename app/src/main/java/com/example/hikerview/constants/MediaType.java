package com.example.hikerview.constants;

/**
 * 照官逐字转写（官包 smali/com/example/hikerview/constants/MediaType.smali 全文）：
 * 枚举七值序与 (name, type) 初值照官 &lt;clinit&gt;——name 为枚举名本身、type 恒 ""。
 * 本类为嗅探栈下一层（constants.Media 的静态初值来源），随④团一并转写记名。
 */
public enum MediaType {
    VIDEO("VIDEO", ""),
    VIDEO_MUSIC("VIDEO_MUSIC", ""),
    IMAGE("IMAGE", ""),
    MUSIC("MUSIC", ""),
    HTML("HTML", ""),
    OTHER("OTHER", ""),
    BLOCK("BLOCK", "");

    private String name;
    private String type;

    MediaType(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
