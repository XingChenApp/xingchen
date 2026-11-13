package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Vod;

import java.util.regex.Pattern;

/**
* 推送播放标题清理：只生成显示名，不动内部标识/地址/缓存键。
* 磁力种子文件名、ed2k 文件名：切掉英文串、年份、清晰度、编码、帧率、声道、片源等参数尾巴，只留名字；
* 多集时选集名保留集数标记，主标题只留剧名。
* 直链（m3u8 等）：只取路径最后一段文件名（去扩展名与链接参数），取不到有意义名字时返回 null，由调用方保持原样。
*/
public final class PushNameClean {

private static final Pattern EXT = Pattern.compile("(?i)\\.(mkv|mp4|avi|rmvb|wmv|flv|mov|ts|m2ts|m2t|iso|mpg|mpeg|3gp|webm|m3u8|mpd)$");
private static final Pattern YEAR = Pattern.compile("^(19|20)\\d{2}$");
private static final Pattern RESOLUTION = Pattern.compile("(?i)^(\\d{3,4}p|[48]k)$");
private static final Pattern CODEC = Pattern.compile("(?i)^(hevc|h\\.?264|h\\.?265|x264|x265|avc|av1|10-?bit|8-?bit|hi10p)$");
private static final Pattern FPS = Pattern.compile("(?i)^\\d+(\\.\\d+)?fps$");
private static final Pattern AUDIO = Pattern.compile("(?i)^(ddp\\d?(\\.\\d+)?|dts(hd)?|eac3|ac3|aac|flac|truehd|atmos|lpcm|mp3|opus)$");
private static final Pattern SOURCE = Pattern.compile("(?i)^(web-?dl|webrip|blu-?ray|bdrip|bdr|bd|hdtv|dvdrip|tvrip|hddvd|remux|itunes)$");
private static final Pattern SIZE = Pattern.compile("(?i)^\\d+(\\.\\d+)?(gb|mb)$");
private static final Pattern VERSION = Pattern.compile("(?i)^(v\\d+|end|uncut|repack|proper)$");
private static final Pattern PURE_ENGLISH = Pattern.compile("^[A-Za-z]{2,}$");
private static final Pattern BARE_NUMBER = Pattern.compile("^\\d{1,4}$");
private static final Pattern EPISODE_MARK = Pattern.compile("(?i)^(第\\d+[集话話期章回幕季]|ep?\\d+|s\\d{1,2}(e\\d{1,4})?|ova\\d*|oad\\d*|sp\\d*)$");
private static final Pattern MEANINGLESS = Pattern.compile("(?i)^(index|play|video|stream|file|download|media|playlist|master|chunklist|manifest|hls)$");

private PushNameClean() {
}

/** 推送详情主标题：rawTitle 为 pushTitle 取到的原始标题（磁力 dn / ed2k 文件名 / 直链原样）。 */
public static String cleanPushTitle(String rawTitle, String id) {
if (rawTitle == null || rawTitle.isEmpty()) return rawTitle == null? "": rawTitle;
if (id!= null && (id.startsWith("magnet:") || id.startsWith("ed2k:"))) {
return cleanMagnetTitle(rawTitle);
}
String file = fileNameFromLink(id);
if (file!= null) return file;
return rawTitle;
}

/** 磁力/ed2k 主标题：只留剧名。 */
public static String cleanMagnetTitle(String filename) {
return cleanCore(filename, false);
}

/** 磁力/ed2k 选集名：只留名字，集数标记保留。 */
public static String cleanEpisodeTitle(String filename) {
return cleanCore(filename, true);
}

/**
* 直链取文件名：路径最后一段，去链接参数与扩展名。
* 无意义（空、纯数字、index/play 这类）返回 null，调用方保持原样、不扩大规则。
*/
public static String fileNameFromLink(String url) {
if (url == null ||!url.contains("://")) return null;
String s = url.trim();
int q = s.indexOf('?');
if (q >= 0) s = s.substring(0, q);
int h = s.indexOf('#');
if (h >= 0) s = s.substring(0, h);
int slash = s.lastIndexOf('/');
String seg = slash >= 0? s.substring(slash + 1): s;
if (seg.isEmpty()) return null;
try {
seg = java.net.URLDecoder.decode(seg, "UTF-8");
} catch (Exception ignored) {
}
seg = EXT.matcher(seg).replaceFirst("").trim();
if (seg.isEmpty()) return null;
if (MEANINGLESS.matcher(seg).matches()) return null;
if (seg.matches("^\\d+$")) return null;
return seg;
}

/** 推送 vod 的全部选集：显示名按清理口径生成，原始名不动。 */
public static void applyEpisodeDisplayNames(Vod vod) {
if (vod == null || vod.getFlags() == null) return;
for (Flag flag: vod.getFlags()) {
if (flag.getEpisodes() == null) continue;
for (Episode episode: flag.getEpisodes()) {
String raw = episode.getRawDisplayName();
String cleaned = cleanEpisodeTitle(raw);
if (!cleaned.equals(raw)) episode.setDisplayName(cleaned);
}
}
}

private static String cleanCore(String filename, boolean keepEpisodeMark) {
if (filename == null || filename.isEmpty()) return filename;
String s = filename.trim();
if (s.contains("://") || s.startsWith("magnet:")) return filename;
s = EXT.matcher(s).replaceFirst("");
s = stripLeadingBracketGroup(s);
StringBuilder sb = new StringBuilder();
for (String token: s.split("[.\\-_ \\u3000]+")) {
String t = token.trim();
if (t.isEmpty() || isDropToken(t)) continue;
if (isEpisodeMark(t)) {
if (!keepEpisodeMark) continue;
} else if (PURE_ENGLISH.matcher(t).matches()) {
continue;
} else if (BARE_NUMBER.matcher(t).matches()) {
continue;
}
if (sb.length() > 0) sb.append(' ');
sb.append(t);
}
String result = sb.toString().trim();
return result.isEmpty()? filename: result;
}

private static boolean isDropToken(String t) {
return YEAR.matcher(t).matches()
|| RESOLUTION.matcher(t).matches()
|| CODEC.matcher(t).matches()
|| FPS.matcher(t).matches()
|| AUDIO.matcher(t).matches()
|| SOURCE.matcher(t).matches()
|| SIZE.matcher(t).matches()
|| VERSION.matcher(t).matches();
}

private static boolean isEpisodeMark(String t) {
return EPISODE_MARK.matcher(t).matches();
}

private static String stripLeadingBracketGroup(String s) {
if (s.length() < 2) return s;
char open = s.charAt(0);
        char close = open == '[' ? ']' : open == '\u3010' ? '\u3011' : open == '(' ? ')' : open == '\uFF08' ? '\uFF09' : 0;
if (close == 0) return s;
int end = s.indexOf(close);
if (end <= 0) return s;
String rest = s.substring(end + 1).trim();
return rest.isEmpty()? s: rest;
}
}
