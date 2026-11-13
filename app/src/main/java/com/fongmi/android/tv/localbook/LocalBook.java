package com.fongmi.android.tv.localbook;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 本地书元数据。导入时把源文件拷进应用私有目录（files/localbook/&lt;id&gt;/），
 * 之后读的都是这份拷贝——用户在文件管理器里删了源文件不影响已导入的书。
 * 章节索引存在 chapters 里（随注册表 JSON 一起持久化）。
 */
public class LocalBook {

    /** 章节引用。 */
    public static class ChapterRef {
        public String name = "";
        // txt：字节偏移（charMode=false）；
        public long start = 0;
        public long length = 0;
        /** txt 为 UTF-16 等换行含 0x00 的编码时用字符偏移（此时 start/length 无效）。 */
        public boolean charMode = false;
        public long charStart = 0;
        public long charLength = 0;
        /** epub：spine 条目的包内路径；zip/cbz：章节目录前缀（""=根）；dir：子目录相对路径（""=根）。 */
        public String entry = "";
        public int imageCount = 0;
        /** PDF：本章起始页/结束页（0 起，含两端；非 PDF 格式忽略）。 */
        public int startPage = 0;
        public int endPage = 0;

        public ChapterRef() {
        }
    }

    public String id = "";
    public String title = "";
    /** "NOVEL" / "COMIC"。 */
    public String kind = "NOVEL";
    /** "TXT" / "EPUB" / "ZIP" / "CBZ" / "DIR"。 */
    public String format = "TXT";
    public String charset = "UTF-8";
    /** 应用私有目录绝对路径（files/localbook/&lt;id&gt;）。 */
    public String dir = "";
    /** 拷入后的文件名（book.txt / book.epub / book.zip / book.cbz；DIR 格式为目录名 files）。 */
    public String fileName = "";
    public long size = 0;
    public int chapterCount = 0;
    public long addedAt = 0;
    /** 封面（导入时提取首图，绝对路径；txt 无封面）。 */
    public String coverPath = "";
    public List<ChapterRef> chapters = new ArrayList<>();

    public LocalBook() {
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 章节寻址串：localbook://&lt;id&gt;/&lt;chapterIndex&gt;，进会话 chapterUrl。 */
    public static String chapterUrl(String id, int index) {
        return "localbook://" + id + "/" + index;
    }

    public static boolean isLocalUrl(String url) {
        return url != null && url.startsWith("localbook://");
    }

    /** 解析出 [id, chapterIndex]，非法返回 null。 */
    public static String[] parseUrl(String url) {
        try {
            if (!isLocalUrl(url)) return null;
            String rest = url.substring("localbook://".length());
            int slash = rest.lastIndexOf('/');
            if (slash <= 0) return null;
            return new String[]{rest.substring(0, slash), rest.substring(slash + 1)};
        } catch (Throwable t) {
            return null;
        }
    }

    /** 自然排序：数字段按数值比，其余按字符比。 */
    public static int naturalCompare(String a, String b) {
        int i = 0, j = 0;
        int la = a.length(), lb = b.length();
        while (i < la && j < lb) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < la && Character.isDigit(a.charAt(i))) i++;
                while (j < lb && Character.isDigit(b.charAt(j))) j++;
                // 数字段比较改走「去前导零后先比长度、再字典序」（低-12）：
                // Long.parseLong 遇到超 19 位数字段（如超长纯数字文件名）直接抛
                // NumberFormatException，把整个排序/列表加载带崩。
                String da = stripLeadingZeros(a.substring(si, i));
                String db = stripLeadingZeros(b.substring(sj, j));
                if (da.length() != db.length()) return Integer.compare(da.length(), db.length());
                int cmp = da.compareTo(db);
                if (cmp != 0) return cmp;
            } else {
                if (ca != cb) return Character.compare(ca, cb);
                i++;
                j++;
            }
        }
        return Integer.compare(la, lb);
    }

    private static String stripLeadingZeros(String s) {
        int k = 0;
        while (k < s.length() - 1 && s.charAt(k) == '0') k++;
        return s.substring(k);
    }

    public static boolean isImageName(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp");
    }
}
