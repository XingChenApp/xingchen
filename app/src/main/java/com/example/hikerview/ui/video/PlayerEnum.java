package com.example.hikerview.ui.video;

/**
 * 照官 8.83 本体逐字转写（官源 smali_classes2/com/example/hikerview/ui/video/PlayerEnum.smali，
 * 全枚举照落）。18 常量的声明序、code 与显示名逐项对官 <clinit>（code 与 ordinal 在官
 * clinit 中以寄存器复用交错给出，按声明序＝ordinal 解出 code：PLAYER_TWO=2、BO_LAN_ASSIST=45、
 * REEX=28、MX=11、X=12、KM=13、MO_BO=14、QQ=15、VLC=16、UC=17、UC_INTL=20、DAN_DAN=18、
 * N_PLAYER=19、WEB_VIDEO_CASTER=25、LUA=26、SYSTEM=29、KODI=27、NEXT=46）。
 * findName 照官：按 code 命中回 name、无命中回 null。
 */
public enum PlayerEnum {

    PLAYER_TWO(2, "默认播放器"),
    BO_LAN_ASSIST(45, "波澜工具箱"),
    REEX(28, "Reex"),
    MX_PLAYER(11, "MxPlayer"),
    X_PLAYER(12, "XPlayer"),
    KM_PLAYER(13, "KMPlayer"),
    MO_BO_PLAYER(14, "MoboPlayer"),
    QQ_PLAYER(15, "QQ浏览器"),
    VLC_PLAYER(16, "VLC"),
    UC_PLAYER(17, "UC浏览器"),
    UC_INTL_PLAYER(20, "UC国际版"),
    DAN_DAN_PLAYER(18, "弹弹Play概念版"),
    N_PLAYER(19, "nPlayer"),
    WEB_VIDEO_CASTER(25, "Web Video Caster"),
    LUA_PLAYER(26, "LUA PLAYER Pro"),
    SYSTEM(29, "系统播放器"),
    KODI(27, "Kodi"),
    NEXT_PLAYER(46, "Next Player");

    private int code;
    private String name;

    PlayerEnum(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static String findName(int code) {
        for (PlayerEnum player : values()) {
            if (player.getCode() == code) {
                return player.getName();
            }
        }
        return null;
    }

    public int getCode() {
        return this.code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
