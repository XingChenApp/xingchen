package com.fongmi.android.tv.tts;

/**
 * Edge 神经音色（微软大声朗读同款音色库）。
 * 画像标签是给用户挑音色的中文描述；storyteller＝张震挂（低沉叙事型）。
 */
public class EdgeVoice {

    /** 如 zh-CN-XiaoxiaoNeural（传给合成接口的正式名）。 */
    public String id = "";
    /** 如 晓晓。 */
    public String name = "";
    /** 如 zh-CN / zh-HK / zh-TW。 */
    public String locale = "";
    /** F / M。 */
    public String gender = "";
    /** 画像：如 温润女声 / 低沉磁性·讲故事感。 */
    public String portrait = "";
    /** 张震挂：低沉叙事型男声。 */
    public boolean storyteller;

    public EdgeVoice() {
    }

    public EdgeVoice(String id, String name, String locale, String gender,
                     String portrait, boolean storyteller) {
        this.id = id;
        this.name = name;
        this.locale = locale;
        this.gender = gender;
        this.portrait = portrait;
        this.storyteller = storyteller;
    }

    public String label() {
        return name + " · " + portrait;
    }
}
