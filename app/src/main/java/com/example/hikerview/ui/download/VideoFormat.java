package com.example.hikerview.ui.download;

import java.util.List;

/**
 * 照官逐字转写（官包 smali_classes2/com/example/hikerview/ui/download/
 * VideoFormat.smali 全文）：嗅探栈一员——VideoFormatUtil 的格式条目类
 * （name＋mimeList），随④团转写。
 */
public class VideoFormat {

    private List<String> mimeList;
    private String name;

    public VideoFormat(String name, List<String> mimeList) {
        this.name = name;
        this.mimeList = mimeList;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getMimeList() {
        return mimeList;
    }

    public void setMimeList(List<String> mimeList) {
        this.mimeList = mimeList;
    }
}
