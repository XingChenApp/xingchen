package com.example.hikerview.utils;

import android.text.TextUtils;
import android.util.Base64;

import com.example.hikerview.ui.video.VideoChapter;
import com.fongmi.android.tv.api.hk.HkHttpParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * 8.83 {@code com.example.hikerview.utils.StringUtil} 的同包名同签名补齐
 * （官包 jadx 反编译＋smali 逐项校 literals 与 getSimpleDom 的 indexOf 起点）。
 * 官类对官包内部类的依赖在本树内的落点：HttpParser.getFirstPageUrl/decodeUrl
 * → {@link HkHttpParser} 同名转写；HttpHelper.bytesToHexString → 本类内联同算法；
 * CollectionUtil.listToString → 内联拼接；commons-lang3 StringUtils 未引入，
 * replaceOnce/isBlank/CR 内联同语义；各三方库常量字段（umeng/jetty/markdown 等）
 * 已还原为字面量（LOWER/UPPER 字母表按位还原、MY_NAME="hiker"、cl.g="code"、
 * ScanDeviceUtil.HTTP="http://"、IDataSource.SCHEME_FILE_TAG="file"）。
 * md5 的文件路径解析走同包 {@link FileUtil#getFilePath(String)}（已补齐）。
 */
public class StringUtil {

    public static final String[] LOWER_CASES = {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"};
    public static final String[] UPPER_CASES = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
    public static final String[] NUMS_LIST = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
    public static final String[] SYMBOLS_ARRAY = {"[", "~", "^", "_", "*"};
    private static final String[] SCHEME_CANT_HANDLE = {"ftp:", "ed2k:", "magnet:", "thunder:", "xg:"};
    public static final String SCHEME_FILE_SELECT = "fileSelect://";
    public static final String SCHEME_TOAST = "toast://";
    public static final String SCHEME_WEB = "web://";
    public static final String SCHEME_INPUT = "input://";
    public static final String SCHEME_CONFIRM = "confirm://";
    public static final String SCHEME_SELECT = "select://";
    public static final String SCHEME_COPY = "copy://";
    public static final String SCHEME_EDIT_FILE = "editFile://";
    public static final String SCHEME_DOWNLOAD = "download://";
    public static final String SCHEME_SHARE = "share://";
    public static final String SCHEME_OPEN_FILE = "openFile://";
    private static final String[] innerScheme = {"http", "hiker", "file", "/", "content", "{", "[", "x5:", SCHEME_FILE_SELECT, SCHEME_TOAST, SCHEME_WEB, SCHEME_INPUT, SCHEME_CONFIRM, SCHEME_SELECT, SCHEME_COPY, "x5WebView:", SCHEME_EDIT_FILE, "rtmp:", "rtsp:", "rule:", "code:", "海阔视界", "x5Play:", "func:", SCHEME_DOWNLOAD, SCHEME_SHARE, SCHEME_OPEN_FILE, "webview://"};
    private static final Pattern FilePattern = Pattern.compile("[\\\\/:*?\"<>|]");

    public static boolean getIsEmoji(char c) {
        if (c == 0 || c == '\t' || c == '\n' || c == '\r') {
            return false;
        }
        if (c >= ' ' && c <= 55295) {
            return false;
        }
        if (c < 57344 || c > 65533) {
            return c < 0 || c > 65535;
        }
        return false;
    }

    private static boolean isChinese(char c) {
        return c >= 19968 && c <= 40869;
    }

    public static boolean isCannotHandleScheme(String str) {
        for (String str2 : SCHEME_CANT_HANDLE) {
            if (str.startsWith(str2)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isScheme(String str) {
        if (str.startsWith("video://")) {
            return false;
        }
        for (String str2 : innerScheme) {
            if (str.startsWith(str2)) {
                return false;
            }
        }
        String[] split = str.split("://");
        if (split.length < 2) {
            return false;
        }
        String str3 = split[0];
        if (str3.length() > 20) {
            return false;
        }
        return isOnlyEn(str3);
    }

    public static String genRandomPwd(int i) {
        return genRandomPwd(i, false);
    }

    public static String genRandomPwd(int i, boolean z) {
        int i2;
        int i3;
        int i4;
        if (i < 2 || i > 20) {
            return "";
        }
        int i5 = i / 2;
        if (z) {
            i4 = i - i5;
            i2 = 0;
            i3 = 0;
        } else {
            int i6 = i - i5;
            i2 = i6 / 2;
            i3 = (i6 - i2) - i2;
            i4 = i2;
        }
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        while (i5 + i4 + i2 + i3 > 0) {
            if (i5 > 0) {
                int nextInt = random.nextInt(sb.length() + 1);
                String[] strArr = LOWER_CASES;
                sb.insert(nextInt, strArr[random.nextInt(strArr.length)]);
                i5--;
            }
            if (i4 > 0) {
                int nextInt2 = random.nextInt(sb.length() + 1);
                String[] strArr2 = UPPER_CASES;
                sb.insert(nextInt2, strArr2[random.nextInt(strArr2.length)]);
                i4--;
            }
            if (i2 > 0) {
                int nextInt3 = random.nextInt(sb.length() + 1);
                String[] strArr3 = NUMS_LIST;
                sb.insert(nextInt3, strArr3[random.nextInt(strArr3.length)]);
                i2--;
            }
            if (i3 > 0) {
                int nextInt4 = random.nextInt(sb.length() + 1);
                String[] strArr4 = SYMBOLS_ARRAY;
                sb.insert(nextInt4, strArr4[random.nextInt(strArr4.length)]);
                i3--;
            }
            System.out.println(sb.toString());
        }
        return sb.toString();
    }

    public static String arrayToString(String[] strArr, int i, String str) {
        return arrayToString(strArr, i, strArr == null ? 0 : strArr.length, str);
    }

    public static String arrayToString(String[] strArr, int i, int i2, String str) {
        StringBuilder sb = new StringBuilder();
        if (strArr == null || strArr.length <= i) {
            return "";
        }
        if (strArr.length <= 1) {
            return strArr[0];
        }
        sb.append(strArr[i]);
        for (int i3 = i + 1; i3 < strArr.length && i3 < i2; i3++) {
            sb.append(str).append(strArr[i3]);
        }
        return sb.toString();
    }

    public static String listToString(List<String> list, String str) {
        StringBuilder sb = new StringBuilder();
        if (list == null || list.size() <= 0) {
            return "";
        }
        if (list.size() <= 1) {
            return list.get(0);
        }
        sb.append(list.get(0));
        for (int i = 1; i < list.size(); i++) {
            sb.append(str).append(list.get(i));
        }
        return sb.toString();
    }

    public static String listToString(List<String> list, int i, String str) {
        StringBuilder sb = new StringBuilder();
        if (list == null || list.size() <= i) {
            return "";
        }
        if (list.size() <= 1) {
            return list.get(0);
        }
        sb.append(list.get(i));
        for (int i2 = i + 1; i2 < list.size(); i2++) {
            sb.append(str).append(list.get(i2));
        }
        return sb.toString();
    }

    public static String listToString(List<String> list) {
        return listToString(list, "&&");
    }

    public static String replaceBlank(String str) {
        if (str == null) {
            return "";
        }
        try {
            return Pattern.compile("\\s*|\t|\r|\n").matcher(str).replaceAll("");
        } catch (Exception unused) {
            return str;
        }
    }

    public static String replaceLineBlank(String str) {
        try {
            return str.replaceAll("\n", "");
        } catch (Exception unused) {
            return str;
        }
    }

    public static String trimBlanks(String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        int length = str.length();
        int i = 0;
        while (i < length && (str.charAt(i) == '\n' || str.charAt(i) == '\r' || str.charAt(i) == '\f' || str.charAt(i) == '\t')) {
            i++;
        }
        while (i < length) {
            int i2 = length - 1;
            if (str.charAt(i2) != '\n' && str.charAt(i2) != '\r' && str.charAt(i2) != '\f' && str.charAt(i2) != '\t') {
                break;
            }
            length--;
        }
        return (i > 0 || length < str.length()) ? str.substring(i, length) : str;
    }

    public static String trimAll(String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        int length = str.length();
        int i = 0;
        while (i < length && (str.charAt(i) == '\n' || str.charAt(i) == '\r' || str.charAt(i) == '\f' || str.charAt(i) == '\t' || str.charAt(i) == 12288 || str.charAt(i) == ' ')) {
            i++;
        }
        while (i < length) {
            int i2 = length - 1;
            if (str.charAt(i2) != '\n' && str.charAt(i2) != '\r' && str.charAt(i2) != '\f' && str.charAt(i2) != '\t' && str.charAt(i2) != 12288 && str.charAt(i2) != ' ') {
                break;
            }
            length--;
        }
        return (i > 0 || length < str.length()) ? str.substring(i, length) : str;
    }

    public static String clearLine(String str) {
        String[] split = str.split("\n");
        int i = 0;
        for (int i2 = 0; i2 < split.length; i2++) {
            if (i2 == 0) {
                while (i < split[0].length() && str.charAt(i) == ' ') {
                    i++;
                }
            }
            if (i > 0 && i < split[i2].length()) {
                split[i2] = split[i2].substring(i);
            }
        }
        return arrayToString(split, 0, "\n");
    }

    public static boolean equalsDomUrl(String str, String str2) {
        if (str == null) {
            return str2 == null;
        }
        if (str2 == null) {
            return false;
        }
        if (str.endsWith("/")) {
            str = str.substring(0, str.length() - 1);
        }
        if (str2.endsWith("/")) {
            str2 = str2.substring(0, str2.length() - 1);
        }
        return str.equals(str2);
    }

    public static String getHomeUrl(String str) {
        return getHome(HkHttpParser.getFirstPageUrl(str));
    }

    public static boolean isHexStr(String str) {
        boolean z = false;
        if (TextUtils.isEmpty(str)) {
            return false;
        }
        if (!str.startsWith("#")) {
            str = "#" + str;
        }
        if (str.length() != 7 && str.length() != 9) {
            return false;
        }
        for (int i = 1; i < str.length(); i++) {
            char charAt = str.charAt(i);
            if (charAt == '0' || charAt == '1' || charAt == '2' || charAt == '3' || charAt == '4' || charAt == '5' || charAt == '6' || charAt == '7' || charAt == '8' || charAt == '9' || charAt == 'A' || charAt == 'B' || charAt == 'C' || charAt == 'D' || charAt == 'E' || charAt == 'F' || charAt == 'a' || charAt == 'b' || charAt == 'c' || charAt == 'd' || charAt == 'e' || charAt == 'f') {
                z = true;
            }
        }
        return z;
    }

    public static boolean containsChinese(String str) {
        if (str == null) {
            return false;
        }
        for (char c : str.toCharArray()) {
            if (isChinese(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAllChinese(String str) {
        if (str == null) {
            return true;
        }
        for (char c : str.toCharArray()) {
            if (!isChinese(c)) {
                return false;
            }
        }
        return true;
    }

    public static String decodeConflictStr(String str) {
        return isEmpty(str) ? str : str.replace("？？", "?").replace("＆＆", "&").replace("；；", ";").replace("，，", ",");
    }

    public static boolean isUrl(String str) {
        if (TextUtils.isEmpty(str)) {
            return false;
        }
        if (isWebUrl(str)) {
            return true;
        }
        return !(containsChinese(str) || !str.contains(".") || str.contains(" "));
    }

    public static String getRealUrl(String str, String str2) {
        String realUrl = getRealUrl(str2);
        return (isEmpty(realUrl) || !realUrl.startsWith("http")) ? str : realUrl;
    }

    public static String getRealUrl(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        try {
            str = str.split("@lazyRule=")[0].split("@rule=")[0];
            if (str.startsWith("hiker://empty#http")) {
                str = replaceOnce(str, "hiker://empty#", "");
            } else if (str.startsWith("webview://")) {
                str = replaceOnce(str, "webview://", "");
            } else {
                if (!str.startsWith("hiker://empty##https%3A%2F%2F") && !str.startsWith("hiker://empty##http%3A%2F%2F")) {
                    if (str.startsWith("hiker://empty##http")) {
                        str = replaceOnce(str, "hiker://empty##", "");
                    } else if (str.startsWith("hiker://empty$http")) {
                        str = replaceOnce(str, "hiker://empty$", "");
                    } else if (str.startsWith("hiker://empty$$http")) {
                        str = replaceOnce(str, "hiker://empty$$", "");
                    } else if (str.startsWith("hiker://empty$$$http")) {
                        str = replaceOnce(str, "hiker://empty$$$", "");
                    } else if (str.startsWith("hiker://empty?url=")) {
                        str = replaceOnce(str, "hiker://empty?url=", "");
                    }
                }
                str = HkHttpParser.decodeUrl(replaceOnce(str, "hiker://empty##", ""), "UTF-8");
            }
            String maxItem = getMaxItem(str, "\\$\\$\\$");
            if (isNotEmpty(maxItem)) {
                return maxItem;
            }
            String maxItem2 = getMaxItem(str, "\\$\\$");
            if (isNotEmpty(maxItem2)) {
                return maxItem2;
            }
            String maxItem3 = getMaxItem(str, "##");
            return isNotEmpty(maxItem3) ? maxItem3 : str;
        } catch (Exception e) {
            Timber.e(e);
            return str;
        }
    }

    /** commons-lang3 StringUtils.replaceOnce 同语义（本树未引入 lang3）。 */
    private static String replaceOnce(String text, String search, String replacement) {
        if (text == null || search == null || search.isEmpty()) {
            return text;
        }
        int i = text.indexOf(search);
        return i < 0 ? text : text.substring(0, i) + replacement + text.substring(i + search.length());
    }

    private static String getMaxItem(String str, String str2) {
        String str3 = null;
        int i = 0;
        for (String str4 : str.split(str2)) {
            if (str4.startsWith("http") && str4.length() > i) {
                i = str4.length();
                str3 = str4;
            }
        }
        return str3;
    }

    public static String getDom(String str) {
        return TextUtils.isEmpty(str) ? str : getDom0(getRealUrl(str));
    }

    private static String getDom0(String str) {
        if (TextUtils.isEmpty(str) || str.startsWith("thunder://") || str.startsWith("magnet:") || str.startsWith("ftp://") || str.startsWith("ed2k:") || str.startsWith("file://")) {
            return str;
        }
        try {
            String[] split = str.split("://");
            if (split.length > 1) {
                return split[1].split("/")[0];
            }
            String[] split2 = str.split("//");
            if (split2.length <= 1) {
                String replaceFirst = str.replaceFirst("http://", "").replaceFirst("https://", "").replaceFirst("hiker://", "");
                String[] split3 = replaceFirst.split("/");
                return split3.length > 0 ? split3[0] : replaceFirst;
            }
            return split2[1].split("/")[0];
        } catch (Exception unused) {
            return null;
        }
    }

    public static String getSecondDom(String str) {
        if (isEmpty(str)) {
            return str;
        }
        String dom = getDom(str);
        String[] split = dom.split("\\.");
        return split.length >= 3 ? arrayToString(split, split.length - 2, ".") : dom;
    }

    public static String getHome(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        String realUrl = getRealUrl(str);
        return realUrl.split("://")[0] + "://" + getDom0(realUrl);
    }

    public static String removeDom(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        try {
            str = str.replaceFirst("http://", "").replaceFirst("https://", "");
            String[] split = str.split("/");
            if (split.length > 1) {
                return arrayToString(split, 1, "/");
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        return str;
    }

    public static String getSimpleDom(String str) {
        String dom = getDom(str);
        if (isEmpty(str) || isEmpty(dom) || str.equals(dom)) {
            return str;
        }
        String[] split = dom.split("\\.");
        return split.length < 3 ? dom : dom.substring(dom.indexOf(".", split.length - 2) + 1);
    }

    public static StringBuilder spannableString(String str, List<String> list, StringBuilder sb) {
        int length = str.length();
        String str2 = "";
        for (int size = list.size() - 1; size >= 0; size--) {
            String str3 = list.get(size);
            int indexOf = str.indexOf(str3);
            if (indexOf == -1) {
                list.remove(size);
            } else if (indexOf < length) {
                str2 = str3;
                length = indexOf;
            }
        }
        if (length == str.length()) {
            sb.append(str);
        } else {
            sb.append(str.substring(0, length));
            sb.append("<font color='#FF0000'>").append(str.substring(length, str2.length() + length)).append("</font>");
            spannableString(str.substring(length + str2.length(), str.length()), list, sb);
        }
        return sb;
    }

    public static String escapeExprSpecialWord(String str) {
        if (!TextUtils.isEmpty(str)) {
            String[] strArr = {"\\", "$", "(", ")", "*", "+", ".", "[", "]", "?", "^", "{", "}", "|"};
            for (int i = 0; i < 14; i++) {
                String str2 = strArr[i];
                if (str.contains(str2)) {
                    str = str.replace(str2, "\\" + str2);
                }
            }
        }
        return str;
    }

    public static String removeSpecialWord(String str) {
        if (!TextUtils.isEmpty(str)) {
            String[] strArr = {"\\", "$", "(", ")", "*", "+", ".", "[", "]", "?", "^", "{", "}", "|"};
            for (int i = 0; i < 14; i++) {
                String str2 = strArr[i];
                if (str.contains(str2)) {
                    str = str.replace(str2, "");
                }
            }
        }
        return str;
    }

    public static String filenameFilter(String str) {
        if (isNotEmpty(str) && str.startsWith("http")) {
            return md5(str);
        }
        String replace = str == null ? null : FilePattern.matcher(str).replaceAll("_").replace(File.separator, "_").replace("...", "_").replace("\n", "_").replace("\r", "_");
        if (replace == null) {
            return "";
        }
        return replace.length() > 85 ? replace.substring(0, 42) + "-" + replace.substring(replace.length() - 42) : replace;
    }

    public static String getBaseUrl(String str) {
        if (isEmpty(str)) {
            return str;
        }
        String str2 = str.replace("http://", "").replace("https://", "").split("/")[0];
        if (str.startsWith("https")) {
            return "https://" + str2;
        }
        return "http://" + str2;
    }

public static boolean isEmpty(CharSequence charSequence) {
return charSequence == null || charSequence.length() == 0;
}

public static boolean isNotEmpty(CharSequence charSequence) {
return!isEmpty(charSequence);
}

public static boolean isUTF8(String str) {
try {
str.getBytes("utf-8");
return true;
} catch (UnsupportedEncodingException unused) {
return false;
}
}

public static String convertBlankToTagP(String str) {
try {
return (!isEmpty(str) && str.contains("\n"))? str.replace("\n", "<br>"): str;
} catch (Exception unused) {
return str;
}
}

public static String simplyGroup(String str) {
if (isEmpty(str)) {
return str;
}
String replace = str.replace("①", "").replace("②", "").replace("③", "").replace("④", "").replace("⑤", "").replace("⑥", "").replace("⑦", "").replace("⑧", "").replace("⑨", "").replace("⑩", "");
String[] split = replace.split("@@");
return split.length > 0? split[0]: replace;
}

public static boolean getIsSp(char c) {
return Character.getType(c) > 10;
}

public static boolean hasSpWord(String str) {
return Pattern.compile("[`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@①#￥%……&*（）——+|{}‘；：”“’。，、？]").matcher(str).find();
}

public static boolean containsInvalidChars(String str) {
if (str == null || str.isEmpty()) {
return false;
}
return str.matches(".*[\\\\/:*?\"<>|].*");
}

public static boolean isLetterDigit(String str) {
return str.matches("^[a-z0-9A-Z\\-._]+$");
}

public static boolean isOnlyEn(String str) {
return str.matches("^[a-zA-Z]+$");
}

public static boolean isOnlyEnOrNumOrPoint(String str) {
return str.matches("^[a-zA-Z0-9.]+$");
}

public static List<String> extractURLs(String str) {
Matcher matcher = Pattern.compile("((https?|ftp|magnet)://[\\w\\d:#@%/;$()~_?\\+-=\\.&]+)", 2).matcher(str);
ArrayList arrayList = new ArrayList();
while (matcher.find()) {
arrayList.add(matcher.group(1));
}
return arrayList;
}

public static boolean isWebUrl(String str) {
if (isEmpty(str)) {
return false;
}
if (!isEmpty(str) && ((str.startsWith("http://") || str.startsWith("https://")) &&!str.contains(" "))) {
return true;
}
String lowerCase = str.toLowerCase();
return lowerCase.startsWith("http://") || lowerCase.startsWith("https://") || lowerCase.startsWith("file://") || lowerCase.startsWith("ftp") || lowerCase.startsWith("rtmp://") || lowerCase.startsWith("rtsp://") || lowerCase.startsWith("magnet:?") || lowerCase.startsWith("thunder:");
}

public static String autoFixUrl(String str, String str2) {
if (!isEmpty(str) &&!isEmpty(str2)) {
String str3 = str.split(";")[0];
String baseUrl = getBaseUrl(str3);
String lowerCase = str2.toLowerCase();
if (!lowerCase.startsWith("http") &&!lowerCase.startsWith("hiker") &&!lowerCase.startsWith("pics") &&!lowerCase.startsWith("code")) {
if (str2.startsWith("//")) {
return "http:" + str2;
}
if (!str2.startsWith("magnet") &&!str2.startsWith("thunder") &&!str2.startsWith("ftp") &&!str2.startsWith("ed2k")) {
if (str2.startsWith("/")) {
if (baseUrl.endsWith("/")) {
return baseUrl.substring(0, baseUrl.length() - 1) + str2;
}
return baseUrl + str2;
}
if (str2.startsWith("./")) {
String[] split = str3.split("://");
if (split.length < 1) {
return str2;
}
String[] split2 = split[1].split("/");
if (split2.length <= 1) {
return baseUrl.endsWith("/")? baseUrl.substring(0, baseUrl.length() - 1) + str2.replace("./", ""): baseUrl + str2.replace("./", "");
}
return split[0] + "://" + split[1].replace(split2[split2.length - 1], "") + str2.replace("./", "");
}
if (str2.startsWith("?")) {
return str3 + str2;
}
}
}
}
return str2;
}

public static String[] splitUrlByQuestionMark(String str) {
if (isEmpty(str)) {
return new String[]{str};
}
String[] split = str.split("\\?");
return split.length <= 1? split: new String[]{split[0], arrayToString(split, 1, "?")};
}

public static String bytesToHexString(byte[] bArr) {
// 8.83 HttpHelper.bytesToHexString 同算法内联（HkHttpHelper 转写同源）。
StringBuilder sb = new StringBuilder();
if (bArr == null || bArr.length <= 0) return null;
for (byte b: bArr) {
String hex = Integer.toHexString(b & 0xff);
if (hex.length() < 2) sb.append(0);
sb.append(hex);
}
return sb.toString();
}

public static byte[] hexToBytes(String str) {
if (str.length() < 1) {
return null;
}
byte[] bArr = new byte[str.length() / 2];
int i = 0;
int i2 = 0;
while (i < str.length()) {
int i3 = i + 2;
bArr[i2] = (byte) Integer.parseInt(str.substring(i, i3), 16);
i2++;
i = i3;
}
return bArr;
}

public static String md5(String str) {
try {
MessageDigest messageDigest = MessageDigest.getInstance("MD5");
if (isNotEmpty(str) && (str.startsWith("file:///") || str.startsWith("hiker://files/"))) {
FileInputStream fileInputStream = new FileInputStream(new File(FileUtil.getFilePath(str)));
try {
byte[] bArr = new byte[10485760];
while (true) {
int read = fileInputStream.read(bArr);
if (read <= 0) {
break;
}
messageDigest.update(bArr, 0, read);
}
fileInputStream.close();
} finally {
}
} else {
messageDigest.update(str.getBytes());
}
return convertByte2String(messageDigest.digest());
} catch (Exception e) {
Timber.e(e);
return isEmpty(str)? "": new String(Base64.encode(str.getBytes(), 2));
}
}

private static String convertByte2String(byte[] bArr) {
char[] cArr = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
char[] cArr2 = new char[bArr.length * 2];
int i = 0;
for (byte b: bArr) {
int i2 = i + 1;
cArr2[i] = cArr[(b >>> 4) & 15];
i += 2;
cArr2[i2] = cArr[b & 15];
}
return new String(cArr2);
}

public static String unicode2String(String str) {
if (str == null || str.trim().isEmpty()) {
return null;
}
StringBuilder sb = new StringBuilder();
int i = 0;
while (true) {
int indexOf = str.indexOf("\\u", i);
if (indexOf!= -1) {
sb.append(str.substring(i, indexOf));
if (indexOf + 5 < str.length()) {
i = indexOf + 6;
sb.append((char) Integer.parseInt(str.substring(indexOf + 2, i), 16));
}
} else {
sb.append(str.substring(i));
return sb.toString();
}
}
}

public static boolean searchContains(String str, String str2, boolean z) {
if (isEmpty(str)) {
return false;
}
if (isEmpty(str2)) {
return true;
}
if (z) {
str = str.toLowerCase();
str2 = str2.toLowerCase();
}
String[] split = str2.split(" ");
if (split.length >= 2) {
for (String str3: split) {
int indexOf = str.indexOf(str3);
if (indexOf < 0) {
return false;
}
str = str.substring(indexOf + str3.length());
}
return true;
}
return str.contains(str2);
}

public static String replaceContains(String str, String str2, String str3, String str4, String str5, String str6) {
if (isEmpty(str) || isEmpty(str2)) {
return str;
}
String[] split = str2.split(" ");
if (split.length < 1) {
return str;
}
ArrayList arrayList = new ArrayList();
for (String str7: split) {
int indexOf = str.indexOf(str7);
if (indexOf < 0) {
break;
}
String substring = str.substring(0, indexOf);
if (isNotEmpty(str5)) {
arrayList.add(str5);
}
arrayList.add(substring);
if (isNotEmpty(str6)) {
arrayList.add(str6);
}
arrayList.add(str3);
arrayList.add(str7);
arrayList.add(str4);
str = str.substring(indexOf + str7.length());
}
if (isNotEmpty(str)) {
if (isNotEmpty(str5)) {
arrayList.add(str5);
}
arrayList.add(str);
if (isNotEmpty(str6)) {
arrayList.add(str6);
}
}
// 8.83 CollectionUtil.listToString(list, "") 同语义内联拼接。
StringBuilder sb = new StringBuilder();
for (int i = 0; i < arrayList.size(); i++) {
if (i > 0) sb.append("");
sb.append(arrayList.get(i));
}
return sb.toString();
}

public static String keepNums(String str) {
return isEmpty(str)? str: str.replaceAll("[^\\d]", "");
}

public static float levenshtein(String str, String str2) {
if (str == null && str2 == null) {
return 1.0f;
}
if (str == null || str2 == null) {
return 0.0f;
}
return 1.0f - ((float) editDis(str, str2) / (float) Math.max(str.length(), str2.length()));
}

private static int editDis(String str, String str2) {
int length = str.length();
int length2 = str2.length();
if (length == 0) {
return length;
}
if (length2 == 0) {
return length2;
}
int[][] iArr = (int[][]) Array.newInstance((Class<?>) Integer.TYPE, length + 1, length2 + 1);
for (int i = 0; i <= length; i++) {
for (int i2 = 0; i2 <= length2; i2++) {
if (i == 0) {
iArr[i][i2] = i2;
} else if (i2 == 0) {
iArr[i][i2] = i;
} else {
int i3 = i - 1;
int i4 = i2 - 1;
if (str.charAt(i3) == str2.charAt(i4)) {
iArr[i][i2] = iArr[i3][i4];
} else {
int[] iArr2 = iArr[i];
int[] iArr3 = iArr[i3];
iArr2[i2] = Math.min(iArr3[i4], Math.min(iArr2[i4], iArr3[i2])) + 1;
}
}
}
}
return iArr[length][length2];
}

public static int findSameCharStartCount(List<VideoChapter> list) {
if (list == null || list.size() <= 1 || list.get(0) == null || list.get(0).getMemoryTitle().isEmpty() || list.get(0).getMemoryTitle().contains("<") || list.get(0).getMemoryTitle().contains("/>")) {
return 0;
}
int i = 0;
int i2 = 0;
while (i < list.get(0).getMemoryTitle().length()) {
i++;
if (!hasSameCharStart(i, list)) {
return findSameCharStartCount2(list, i2);
}
i2++;
}
return findSameCharStartCount2(list, i2);
}

private static int findSameCharStartCount2(List<VideoChapter> list, int i) {
if (i > 0) {
for (int i2 = i - 1; i2 >= 0 && isNum(list.get(0).getMemoryTitle().substring(i2, i2 + 1)); i2--) {
i--;
}
}
return i;
}

private static boolean isNum(String str) {
String[] strArr = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
for (int i = 0; i < 10; i++) {
if (strArr[i].equals(str)) {
return true;
}
}
return false;
}

private static boolean hasSameCharStart(int i, List<VideoChapter> list) {
String substring = list.get(0).getMemoryTitle().substring(0, i);
for (int i2 = 1; i2 < list.size(); i2++) {
if (list.get(i2).getMemoryTitle() == null || list.get(i2).getMemoryTitle().length() < i ||!substring.equals(list.get(i2).getMemoryTitle().substring(0, i))) {
return false;
}
}
return true;
}

public static String toSafeBase64(String str) {
return isEmpty(str)? str: Base64.encodeToString(str.getBytes(), 10);
}

public static String fromSafeBase64(String str) {
return isEmpty(str)? str: new String(Base64.decode(str, 10));
}

public static String changeFileExtension(String str, String str2) {
int lastIndexOf = str.lastIndexOf(46);
if (lastIndexOf == -1) {
return str + "." + str2;
}
return str.substring(0, lastIndexOf + 1) + str2;
}
}
