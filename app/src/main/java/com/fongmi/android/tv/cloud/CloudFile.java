package com.fongmi.android.tv.cloud;

/**
 * 网盘文件项
 * 115 字段对照：fid/cid, n=文件名, s=大小, sha, pc=pick_code, ico
 */
public class CloudFile {

    private String id;          // fid（文件）或 cid（目录）
    private String name;        // n
    private long size;          // s
    private boolean isDir;
    private String pickCode;    // pc
    private String sha1;        // sha
    private String parentCid;
    private String thumbUrl;    // 缩略图直链（PikPak/夸克服务端返回；115 取回包 u 键、备选 thumb，个别文件可为空）
    private String icon;        // 115 回包 ico 键＝文件类型（值即扩展名，如 mp3/flac/mkv）；其它网盘不给、恒空

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public boolean isDir() { return isDir; }
    public void setDir(boolean dir) { isDir = dir; }

    public String getPickCode() { return pickCode; }
    public void setPickCode(String pickCode) { this.pickCode = pickCode; }

    public String getSha1() { return sha1; }
    public void setSha1(String sha1) { this.sha1 = sha1; }

    public String getParentCid() { return parentCid; }
    public void setParentCid(String parentCid) { this.parentCid = parentCid; }

    public String getThumbUrl() { return thumbUrl; }
    public void setThumbUrl(String thumbUrl) { this.thumbUrl = thumbUrl; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    /** 音频双判：文件名后缀或 115 的 ico 类型任一命中音频表即认（115 侧 ico 值即扩展名）。
     * 后缀表与网盘音乐页 MusicCloudActivity.AUDIO_EXT 同表（mp3/flac/wav/aac/ogg/oga/m4a/opus/ape/wma/aif/aiff）。 */
    public boolean isAudio() {
        if (icon != null && !icon.isEmpty() && isAudioExt(icon.trim().toLowerCase())) return true;
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.endsWith(".mp3") || n.endsWith(".flac") || n.endsWith(".wav")
                || n.endsWith(".aac") || n.endsWith(".ogg") || n.endsWith(".oga")
                || n.endsWith(".m4a") || n.endsWith(".opus") || n.endsWith(".ape")
                || n.endsWith(".wma") || n.endsWith(".aif") || n.endsWith(".aiff");
    }

    private static boolean isAudioExt(String ext) {
        switch (ext) {
            case "mp3": case "flac": case "wav": case "aac": case "ogg": case "oga":
            case "m4a": case "opus": case "ape": case "wma": case "aif": case "aiff":
                return true;
            default:
                return false;
        }
    }

    /** 视频双判：文件名后缀或 115 的 ico 类型任一命中视频表即认（与 isAudio 同口径）。
     * 115 搜索回包的文件名常不带后缀、类型只在 ico 里（值即扩展名）；此前只判后缀，
     * 聚合搜索里无后缀的 115 视频全被挡在截帧封面与点开直达起播的门外（全是排字素图）。 */
    public boolean isVideo() {
        if (icon != null && !icon.isEmpty() && isVideoExt(icon.trim().toLowerCase())) return true;
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi")
                || n.endsWith(".rmvb") || n.endsWith(".flv") || n.endsWith(".mov")
                || n.endsWith(".wmv") || n.endsWith(".ts") || n.endsWith(".m2ts")
                || n.endsWith(".webm") || n.endsWith(".m3u8")
                || n.endsWith(".mpg") || n.endsWith(".mpeg") || n.endsWith(".rm")
                || n.endsWith(".3gp") || n.endsWith(".f4v") || n.endsWith(".asf")
                || n.endsWith(".vob") || n.endsWith(".m4v") || n.endsWith(".mts")
                || n.endsWith(".iso") || n.endsWith(".dat") || n.endsWith(".m2v")
                || n.endsWith(".mpe") || n.endsWith(".m2t") || n.endsWith(".3g2")
                || n.endsWith(".ogv") || n.endsWith(".ogm") || n.endsWith(".mod")
                || n.endsWith(".tod") || n.endsWith(".dvr-ms") || n.endsWith(".wtv")
                || n.endsWith(".tp") || n.endsWith(".trp") || n.endsWith(".divx")
                || n.endsWith(".qt") || n.endsWith(".amv") || n.endsWith(".vro");
    }

    private static boolean isVideoExt(String ext) {
        switch (ext) {
            case "mp4": case "mkv": case "avi": case "rmvb": case "flv": case "mov":
            case "wmv": case "ts": case "m2ts": case "webm": case "m3u8":
            case "mpg": case "mpeg": case "rm": case "3gp": case "f4v": case "asf":
            case "vob": case "m4v": case "mts": case "iso": case "dat": case "m2v":
            case "mpe": case "m2t": case "3g2": case "ogv": case "ogm": case "mod":
            case "tod": case "dvr-ms": case "wtv": case "tp": case "trp": case "divx":
            case "qt": case "amv": case "vro":
                return true;
            default:
                return false;
        }
    }

    public String getFormatSize() {
        if (size <= 0) return "";
        if (size < 1024) return size + "B";
        if (size < 1024 * 1024) return String.format("%.2fKB", size / 1024.0);
        if (size < 1024L * 1024 * 1024) return String.format("%.2fMB", size / 1048576.0);
        return String.format("%.2fGB", size / 1073741824.0);
    }
}
