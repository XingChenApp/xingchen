package com.fongmi.android.tv.read;

import java.util.List;
import java.util.regex.Pattern;

/**
* 阅读纯逻辑：书名归一化、换源匹配、章节对位、缓存键清洗。
* 零安卓依赖，离线脚手架可直编直断言。
*/
public final class ReadingMatch {

private ReadingMatch() {
}

private static final Pattern NOISE = Pattern.compile(
"[\\s\\p{Punct}《》「」『』（）()［］\\[\\]·・…—\\-]+");

/** 书名/作者归一化：去空白标点转小写，供换源匹配用。 */
public static String normalize(String s) {
if (s == null) return "";
String t = NOISE.matcher(s.toLowerCase()).replaceAll("");
// 全角转半角（数字字母）
StringBuilder sb = new StringBuilder(t.length());
for (int i = 0; i < t.length(); i++) {
char c = t.charAt(i);
if (c >= 0xFF10 && c <= 0xFF19) c = (char) (c - 0xFF10 + '0');
else if (c >= 0xFF21 && c <= 0xFF3A) c = (char) (c - 0xFF21 + 'a');
else if (c >= 0xFF41 && c <= 0xFF5A) c = (char) (c - 0xFF41 + 'a');
sb.append(c);
}
return sb.toString();
}

/**
* 同书判定：归一化书名相等，或一方包含另一方（且长度差不大防《斗罗》撞《斗罗大陆2》）。
* 作者都非空时要求作者也匹配（归一化包含）。
*/
public static boolean sameBook(String titleA, String authorA, String titleB, String authorB) {
String a = normalize(titleA);
String b = normalize(titleB);
if (a.isEmpty() || b.isEmpty()) return false;
boolean titleOk;
if (a.equals(b)) {
titleOk = true;
} else if (a.contains(b) || b.contains(a)) {
int longer = Math.max(a.length(), b.length());
int shorter = Math.min(a.length(), b.length());
titleOk = longer - shorter <= 3; // 允许"卷一"类后缀差
} else {
titleOk = false;
}
if (!titleOk) return false;
String aa = normalize(authorA);
String bb = normalize(authorB);
if (aa.isEmpty() || bb.isEmpty()) return true; // 一方无作者信息时只认书名
return aa.contains(bb) || bb.contains(aa);
}

/**
* 换源章节对位：当前章名在新目录里找位置。
* 精确→归一化相等→归一化包含→按下标兜底→0。
*/
public static int alignChapter(List<String> newNames, String curName, int curIndex) {
if (newNames == null || newNames.isEmpty()) return 0;
if (curName!= null &&!curName.isEmpty()) {
for (int i = 0; i < newNames.size(); i++) {
if (curName.equals(newNames.get(i))) return i;
}
String cn = normalize(curName);
if (!cn.isEmpty()) {
for (int i = 0; i < newNames.size(); i++) {
if (cn.equals(normalize(newNames.get(i)))) return i;
}
// 章节名常带"第X章"前缀，取数字核对位
String cnNum = digits(cn);
if (!cnNum.isEmpty()) {
for (int i = 0; i < newNames.size(); i++) {
if (cnNum.equals(digits(normalize(newNames.get(i))))) return i;
}
}
}
}
if (curIndex >= 0 && curIndex < newNames.size()) return curIndex;
return 0;
}

private static String digits(String s) {
StringBuilder sb = new StringBuilder();
for (int i = 0; i < s.length(); i++) {
char c = s.charAt(i);
if (c >= '0' && c <= '9') sb.append(c);
}
return sb.toString();
}

/** 缓存目录名清洗：只留安全字符，防路径穿越。 */
public static String safeFileKey(String key) {
if (key == null) return "book";
String s = key.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff_\\-]", "_");
if (s.length() > 80) s = s.substring(0, 80);
// 叠一层 hash 防重名
return s + "_" + Integer.toHexString(key.hashCode());
}
}
