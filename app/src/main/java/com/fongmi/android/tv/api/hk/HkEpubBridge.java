package com.fongmi.android.tv.api.hk;

import com.fongmi.android.tv.localbook.LocalEpub;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.zip.ZipFile;

/**
 * 8.83 EpubFile（epublib）三面的星辰等价（G42 getEpubChapters/getEpubContent0/
 * getEpubMetadata 的承接）：咱家无 epublib，以在树 LocalEpub（OPF/spine 解析）落
 * spine 形——章节 {title, url, index, nextUrl, startFragmentId, endFragmentId}
 * 字段同名同序、内容回章节 XHTML 原文、元数据回 OPF 可得字段。
 * 与官的记名差（见第九阶段回执）：官章节是 TOC 树压平＋spine 补章（fragment
 * 分片章在此合并为整文件章、两 fragment 字段恒空）、官内容是 jsoup 按 fragment
 * 区间取的 Elements.outerHtml（此为文件原文）、官元数据是 epublib Metadata
 * bean 全字段（此为 OPF 解析可得集）。
 */
public final class HkEpubBridge {

    private HkEpubBridge() {
    }

    public static String chaptersJson(String path) {
        // 官三法均 Throws：坏 epub 的解析异常穿出（首落全吞回空形，复核订正）
        LocalEpub.Book book;
        try {
            book = LocalEpub.parse(new File(path));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        JsonArray arr = new JsonArray();
        for (int i = 0; i < book.spine.size(); i++) {
            LocalEpub.SpineItem item = book.spine.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("title", item.title == null ? "" : item.title);
            o.addProperty("url", item.path == null ? "" : item.path);
            o.addProperty("index", i);
            o.addProperty("nextUrl", i + 1 < book.spine.size()
                    ? (book.spine.get(i + 1).path == null ? "" : book.spine.get(i + 1).path) : "");
            o.addProperty("startFragmentId", "");
            o.addProperty("endFragmentId", "");
            arr.add(o);
        }
        return new Gson().toJson(arr);
    }

    public static String content(String path, String chapterUrl) {
        File file = new File(path);
        LocalEpub.Book book;
        try {
            book = LocalEpub.parse(file);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        LocalEpub.SpineItem hit = null;
        for (LocalEpub.SpineItem item : book.spine) {
            if (item.path != null && item.path.equals(chapterUrl)) {
                hit = item;
                break;
            }
        }
        if (hit == null) return "";
        try (ZipFile zip = new ZipFile(file)) {
            byte[] raw = LocalEpub.rawOf(zip, hit.path);
            if (raw == null) return "";
            return new String(raw, book.charset == null ? "UTF-8" : book.charset);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * 官 getEpubMetadata 回 epublib Metadata bean 的 fastjson 序列化（先 clear
     * otherProperties）：键集为 authors/contributors/dates/descriptions/firstTitle/
     * format/identifiers/language/otherProperties/publishers/rights/subjects/titles/
     * types，**没有裸 "title" 键**（首落只回 {"title"} 与官不符，复核订正）。
     * 值由本桥自带 OPF dc 抽取填（LocalEpub 只暴露 title，不动它）：
     * titles/firstTitle 取 dc:title、authors 取 dc:creator、format 取 package
     * version、identifiers 取 dc:identifier（键为 opf:scheme、缺省 "ID"）、
     * dates 取 dc:date 作 {date, event:""}（fastjson 对 epublib Date bean 的
     * 序列化细形未逐字验，记名近似）。
     */
    public static String metadataJson(String path) {
        File file = new File(path);
        LocalEpub.Book book;
        try {
            book = LocalEpub.parse(file);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        DcFields dc = extractDc(file);
        String firstTitle = !dc.titles.isEmpty() ? dc.titles.get(0)
                : (book.title == null ? "" : book.title);
        if (dc.titles.isEmpty() && !firstTitle.isEmpty()) dc.titles.add(firstTitle);
        JsonObject o = new JsonObject();
        o.add("authors", strArray(dc.creators));
        o.add("contributors", strArray(dc.contributors));
        JsonArray dates = new JsonArray();
        for (String d : dc.dates) {
            JsonObject dj = new JsonObject();
            dj.addProperty("date", d);
            dj.addProperty("event", "");
            dates.add(dj);
        }
        o.add("dates", dates);
        o.add("descriptions", strArray(dc.descriptions));
        o.addProperty("firstTitle", firstTitle);
        o.addProperty("format", dc.format);
        JsonObject identifiers = new JsonObject();
        for (String[] pair : dc.identifiers) identifiers.addProperty(pair[0], pair[1]);
        o.add("identifiers", identifiers);
        o.addProperty("language", dc.language);
        o.add("otherProperties", new JsonObject());
        o.add("publishers", strArray(dc.publishers));
        o.add("rights", strArray(dc.rights));
        o.add("subjects", strArray(dc.subjects));
        o.add("titles", strArray(dc.titles));
        o.add("types", strArray(dc.types));
        return new Gson().toJson(o);
    }

    private static JsonArray strArray(java.util.List<String> values) {
        JsonArray arr = new JsonArray();
        for (String v : values) arr.add(v);
        return arr;
    }

    private static final class DcFields {
        final java.util.List<String> titles = new java.util.ArrayList<>();
        final java.util.List<String> creators = new java.util.ArrayList<>();
        final java.util.List<String> contributors = new java.util.ArrayList<>();
        final java.util.List<String> descriptions = new java.util.ArrayList<>();
        final java.util.List<String> publishers = new java.util.ArrayList<>();
        final java.util.List<String> subjects = new java.util.ArrayList<>();
        final java.util.List<String> rights = new java.util.ArrayList<>();
        final java.util.List<String> types = new java.util.ArrayList<>();
        final java.util.List<String> dates = new java.util.ArrayList<>();
        final java.util.List<String[]> identifiers = new java.util.ArrayList<>();
        String language = "";
        String format = "";
    }

    /** 自带 OPF dc 抽取：container.xml → rootfile → 解析 metadata 子元素与 package version。 */
    private static DcFields extractDc(File file) {
        DcFields dc = new DcFields();
        try (ZipFile zip = new ZipFile(file)) {
            String container = readEntryText(zip, "META-INF/container.xml");
            if (container == null) return dc;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("full-path=\"([^\"]+)\"").matcher(container);
            if (!m.find()) return dc;
            String opf = readEntryText(zip, m.group(1));
            if (opf == null) return dc;
            org.xmlpull.v1.XmlPullParser p = android.util.Xml.newPullParser();
            try {
                p.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            } catch (Throwable ignored) {
            }
            p.setInput(new java.io.StringReader(opf));
            boolean inMetadata = false;
            String current = null;
            String currentScheme = null;
            int event = p.getEventType();
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    String tag = p.getName();
                    if ("package".equals(tag)) {
                        String v = p.getAttributeValue(null, "version");
                        if (v != null) dc.format = v;
                    } else if ("metadata".equals(tag)) {
                        inMetadata = true;
                    } else if (inMetadata) {
                        current = tag;
                        currentScheme = p.getAttributeValue(null, "scheme");
                    }
                } else if (event == org.xmlpull.v1.XmlPullParser.TEXT && current != null) {
                    String text = p.getText() == null ? "" : p.getText().trim();
                    if (!text.isEmpty()) {
                        switch (current) {
                            case "title": dc.titles.add(text); break;
                            case "creator": dc.creators.add(text); break;
                            case "contributor": dc.contributors.add(text); break;
                            case "description": dc.descriptions.add(text); break;
                            case "publisher": dc.publishers.add(text); break;
                            case "subject": dc.subjects.add(text); break;
                            case "rights": dc.rights.add(text); break;
                            case "type": dc.types.add(text); break;
                            case "date": dc.dates.add(text); break;
                            case "language": if (dc.language.isEmpty()) dc.language = text; break;
                            case "identifier":
                                dc.identifiers.add(new String[]{
                                        currentScheme == null || currentScheme.isEmpty() ? "ID" : currentScheme,
                                        text});
                                break;
                            default: break;
                        }
                    }
                } else if (event == org.xmlpull.v1.XmlPullParser.END_TAG) {
                    if ("metadata".equals(p.getName())) inMetadata = false;
                    current = null;
                    currentScheme = null;
                }
                event = p.next();
            }
        } catch (Throwable ignored) {
            // dc 抽取是尽力而为：失败回已积累集（标题仍有 LocalEpub 兜底）
        }
        return dc;
    }

    private static String readEntryText(ZipFile zip, String name) {
        try {
            java.util.zip.ZipEntry e = zip.getEntry(name);
            if (e == null) return null;
            try (java.io.InputStream in = zip.getInputStream(e)) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } catch (Throwable t) {
            return null;
        }
    }
}
