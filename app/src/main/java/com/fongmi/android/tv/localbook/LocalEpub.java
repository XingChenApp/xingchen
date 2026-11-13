package com.fongmi.android.tv.localbook;

import android.util.Xml;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * epub 解析：container → opf（manifest/spine/metadata）→ 按 spine 取 XHTML。
 * <p>
 * DRM 判定：META-INF/encryption.xml 里只要有一条 EncryptedData 指向非字体资源
 * （字体混淆如 .ttf/.otf/.woff 允许），就判定带 DRM，直接拒收并提示。
 * <p>
 * 小说/漫画判据（写死）：图片数 ≥5 且正文总字数 &lt;3000 → COMIC；其余 → NOVEL。
 * 插图小说（字多）归小说，图片型电子书归漫画。
 */
public class LocalEpub {

    private static final Pattern XML_ENCODING = Pattern.compile(
            "<\\?xml[^>]*encoding\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    public static class Book {
        public String title = "";
        public String charset = "UTF-8";
        public String opfDir = "";
        public List<SpineItem> spine = new ArrayList<>();
        public String coverEntry = "";
        public long textChars = 0;
        public int imageCount = 0;
    }

    public static class SpineItem {
        public String id = "";
        /** 包内完整路径。 */
        public String path = "";
        public String title = "";
    }

    /** DRM 检查：true=带 DRM（内容加密），应拒收。 */
    public static boolean hasDrm(ZipFile zip) {
        try {
            ZipEntry enc = zip.getEntry("META-INF/encryption.xml");
            if (enc == null) return false;
            String xml = readEntry(zip, enc, "UTF-8");
            // 找所有 CipherReference URI
            Matcher m = Pattern.compile("URI\\s*=\\s*[\"']([^\"']+)[\"']")
                    .matcher(xml);
            while (m.find()) {
                String uri = m.group(1).toLowerCase();
                // 字体混淆放行
                if (uri.endsWith(".ttf") || uri.endsWith(".otf") || uri.endsWith(".woff")
                        || uri.endsWith(".woff2")) {
                    continue;
                }
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Book parse(File epubFile) throws Exception {
        Book book = new Book();
        try (ZipFile zip = new ZipFile(epubFile)) {
            if (hasDrm(zip)) throw new DrmException();
            ZipEntry container = zip.getEntry("META-INF/container.xml");
            if (container == null) throw new Exception("不是有效的 epub（缺 container）");
            String opfPath = parseContainer(readEntry(zip, container, "UTF-8"));
            if (opfPath == null) throw new Exception("不是有效的 epub（缺 opf）");
            int slash = opfPath.lastIndexOf('/');
            book.opfDir = slash >= 0 ? opfPath.substring(0, slash + 1) : "";
            String opfXml = readEntry(zip, zip.getEntry(opfPath), "UTF-8");
            parseOpf(opfXml, book);
            for (SpineItem item : book.spine) {
                ZipEntry e = zip.getEntry(item.path);
                if (e == null) continue;
                String charset = detectXhtmlCharset(zip, e);
                String html = readEntry(zip, e, charset);
                if (item.title.isEmpty()) {
                    try {
                        Document d = Jsoup.parse(html);
                        Element t = d.selectFirst("title");
                        if (t != null) item.title = t.text().trim();
                    } catch (Throwable ignored) {
                    }
                }
            }
            book.charset = "UTF-8";
        }
        return book;
    }

    /** 统计整本：正文字数＋图片数（供小说/漫画判据用；不把全文放内存）。 */
    public static void analyze(File epubFile, Book book) throws Exception {
        long textChars = 0;
        int images = 0;
        try (ZipFile zip = new ZipFile(epubFile)) {
            for (SpineItem item : book.spine) {
                ZipEntry e = zip.getEntry(item.path);
                if (e == null || e.getSize() > 8L * 1024 * 1024) continue;
                String html = readEntry(zip, e, "UTF-8");
                try {
                    Document d = Jsoup.parse(html);
                    String t = d.body() != null ? d.body().text() : "";
                    textChars += t.length();
                    images += d.select("img").size();
                } catch (Throwable ignored) {
                }
            }
        }
        book.textChars = textChars;
        book.imageCount = images;
    }

    /** 取某 spine 条目的纯文本（小说阅读器懒取用）。 */
    public static String textOf(File epubFile, SpineItem item) {
        try (ZipFile zip = new ZipFile(epubFile)) {
            ZipEntry e = zip.getEntry(item.path);
            if (e == null || e.getSize() > 8L * 1024 * 1024) return "";
            String html = readEntry(zip, e, "UTF-8");
            Document d = Jsoup.parse(html);
            return d.body() != null ? d.body().text() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** 取某 spine 条目的图片包内路径（漫画阅读器用，按文档序）。 */
    public static List<String> imagesOf(File epubFile, SpineItem item, String opfDir) {
        List<String> out = new ArrayList<>();
        try (ZipFile zip = new ZipFile(epubFile)) {
            ZipEntry e = zip.getEntry(item.path);
            if (e == null || e.getSize() > 8L * 1024 * 1024) return out;
            String html = readEntry(zip, e, "UTF-8");
            Document d = Jsoup.parse(html);
            int slash = item.path.lastIndexOf('/');
            String base = slash >= 0 ? item.path.substring(0, slash + 1) : opfDir;
            for (Element img : d.select("img")) {
                String src = img.attr("src").trim();
                if (src.isEmpty() || src.startsWith("data:") || src.startsWith("http")) continue;
                out.add(normalize(base + src));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 按包内路径取原始字节（图片解包用）。 */
    public static byte[] rawOf(ZipFile zip, String entryPath) throws Exception {
        ZipEntry e = zip.getEntry(entryPath);
        if (e == null) return null;
        try (InputStream in = zip.getInputStream(e)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            long total = 0;
            while ((r = in.read(buf)) > 0) {
                total += r;
                if (total > 100L * 1024 * 1024) break;
                bos.write(buf, 0, r);
            }
            return bos.toByteArray();
        }
    }

    private static String parseContainer(String xml) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        try {
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        } catch (Throwable ignored) {
        }
        p.setInput(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "UTF-8");
        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && "rootfile".equals(p.getName())) {
                String fp = p.getAttributeValue(null, "full-path");
                if (fp != null && !fp.isEmpty()) return fp;
            }
            ev = p.next();
        }
        return null;
    }

    private static void parseOpf(String xml, Book book) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        try {
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        } catch (Throwable ignored) {
        }
        p.setInput(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "UTF-8");
        Map<String, String> hrefById = new LinkedHashMap<>();
        Map<String, String> typeById = new LinkedHashMap<>();
        List<String> spineIds = new ArrayList<>();
        String coverId = null;
        String title = "";
        boolean inMetadata = false, inTitle = false;
        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            String name = p.getName();
            if (ev == XmlPullParser.START_TAG) {
                if ("metadata".equals(name)) inMetadata = true;
                else if (inMetadata && "title".equals(name)) inTitle = true;
                else if ("item".equals(name)) {
                    String id = p.getAttributeValue(null, "id");
                    String href = p.getAttributeValue(null, "href");
                    String type = p.getAttributeValue(null, "media-type");
                    if (id != null && href != null) {
                        hrefById.put(id, book.opfDir + href);
                        typeById.put(id, type == null ? "" : type);
                        if ("cover".equals(id) || "cover-image".equals(id)) coverId = id;
                    }
                } else if ("itemref".equals(name)) {
                    String idref = p.getAttributeValue(null, "idref");
                    if (idref != null) spineIds.add(idref);
                } else if ("meta".equals(name)) {
                    String n = p.getAttributeValue(null, "name");
                    String c = p.getAttributeValue(null, "content");
                    if ("cover".equals(n) && c != null) coverId = c;
                }
            } else if (ev == XmlPullParser.TEXT) {
                if (inTitle) title += p.getText();
            } else if (ev == XmlPullParser.END_TAG) {
                if ("metadata".equals(name)) inMetadata = false;
                else if ("title".equals(name)) inTitle = false;
            }
            ev = p.next();
        }
        book.title = title.trim();
        for (String id : spineIds) {
            String href = hrefById.get(id);
            String type = typeById.get(id);
            if (href == null) continue;
            if (type != null && !type.contains("html") && !type.contains("xhtml")
                    && !type.contains("xml")) {
                continue;
            }
            SpineItem item = new SpineItem();
            item.id = id;
            item.path = normalize(href);
            book.spine.add(item);
        }
        if (coverId != null && hrefById.containsKey(coverId)) {
            book.coverEntry = normalize(hrefById.get(coverId));
        }
    }

    private static String detectXhtmlCharset(ZipFile zip, ZipEntry e) {
        try (InputStream in = zip.getInputStream(e)) {
            byte[] head = new byte[2048];
            int n = in.read(head);
            if (n > 0) {
                Matcher m = XML_ENCODING.matcher(new String(head, 0, n, StandardCharsets.ISO_8859_1));
                if (m.find()) return m.group(1);
            }
        } catch (Throwable ignored) {
        }
        return "UTF-8";
    }

    private static String readEntry(ZipFile zip, ZipEntry e, String charset) throws Exception {
        try (InputStream in = zip.getInputStream(e)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            long total = 0;
            while ((r = in.read(buf)) > 0) {
                total += r;
                if (total > 16L * 1024 * 1024) break;
                bos.write(buf, 0, r);
            }
            return new String(bos.toByteArray(), charset);
        }
    }

    /** 路径规范化：处理 ../ 与 ./，防 zip slip。 */
    public static String normalize(String path) {
        String[] parts = path.replace('\\', '/').split("/");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p.isEmpty() || ".".equals(p)) continue;
            if ("..".equals(p)) {
                if (!out.isEmpty()) out.remove(out.size() - 1);
            } else {
                out.add(p);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) sb.append('/');
            sb.append(out.get(i));
        }
        return sb.toString();
    }

    public static class DrmException extends Exception {
        public DrmException() {
            super("drm");
        }
    }
}
