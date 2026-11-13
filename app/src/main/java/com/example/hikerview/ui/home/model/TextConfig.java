package com.example.hikerview.ui.home.model;

/**
 * 照官 8.83 本体逐字转写（官源 smali_classes2/com/example/hikerview/ui/home/model/TextConfig.smali，
 * 全类照落）。四构造的默认值照官：无参 textSize/lineSpacingExtra 置 -1（UN_SET）、
 * background="#eee7df"、textColor=-0xddddde；(II) 构造仍带 background/textColor 默认、
 * (II,String) 只带 textColor 默认、(II,String,I) 全用入参。
 */
public class TextConfig {

    public static final int UN_SET = -1;

    private String background;
    private int lineSpacingExtra;
    private int textColor;
    private int textSize;

    public TextConfig() {
        this.textSize = -1;
        this.lineSpacingExtra = -1;
        this.background = "#eee7df";
        this.textColor = -0xddddde;
    }

    public TextConfig(int textSize, int lineSpacingExtra) {
        this.background = "#eee7df";
        this.textColor = -0xddddde;
        this.textSize = textSize;
        this.lineSpacingExtra = lineSpacingExtra;
    }

    public TextConfig(int textSize, int lineSpacingExtra, String background) {
        this.textColor = -0xddddde;
        this.textSize = textSize;
        this.lineSpacingExtra = lineSpacingExtra;
        this.background = background;
    }

    public TextConfig(int textSize, int lineSpacingExtra, String background, int textColor) {
        this.textSize = textSize;
        this.lineSpacingExtra = lineSpacingExtra;
        this.background = background;
        this.textColor = textColor;
    }

    public String getBackground() {
        return this.background;
    }

    public void setBackground(String str) {
        this.background = str;
    }

    public int getLineSpacingExtra() {
        return this.lineSpacingExtra;
    }

    public void setLineSpacingExtra(int i) {
        this.lineSpacingExtra = i;
    }

    public int getTextColor() {
        return this.textColor;
    }

    public void setTextColor(int i) {
        this.textColor = i;
    }

    public int getTextSize() {
        return this.textSize;
    }

    public void setTextSize(int i) {
        this.textSize = i;
    }
}
