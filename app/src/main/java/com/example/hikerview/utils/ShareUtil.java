package com.example.hikerview.utils;

import com.example.hikerview.ui.browser.util.CollectionUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/ShareUtil.smali）——
 * 本批只落嗅探栈用到的 MIME 表族四件：getMimeTypeMap（官 .line 231 起、
 * 355 次 loadKV 装表，逐条照 smali 提取、次序与重复键累加语义照官）、
 * loadKV、getExtension(String, String)、getExtensions()。loadKV 官以
 * annimon Stream.of(...).toList() 建表，本树无 annimon Stream 类，按
 * UrlDetector 批先例以等义 new ArrayList&lt;&gt;(Arrays.asList(...)) 落
 * （annimon toList 本体即 ArrayList 拷贝）。getMimeTypeByExt 已随
 * 2026-11-14 图片团补落（ImgUtil 存相册族消费）。ShareUtil 其余法
 * （chooser/shareText/startUrl/toWeChatScan/getMIMEType 等）仍未落，
 * 记名在案；本类落盘后，FileUtil 批停手的 clearExtension/
 * generateFileName 两法的前置（getMimeTypeMap/getExtension）已齐，
 * 待该批另行解锁、本团未动 FileUtil。
 */
public class ShareUtil {

    public static String getExtension(String str, String str2) {
        if (StringUtil.isEmpty(str2)) {
            return null;
        }
        Map<String, List<String>> mimeTypeMap = getMimeTypeMap();
        List<String> list = mimeTypeMap.containsKey(str2) ? mimeTypeMap.get(str2) : null;
        if (CollectionUtil.isNotEmpty(list)) {
            if (list.size() == 1) {
                return list.get(0);
            }
            if (StringUtil.isNotEmpty(str)) {
                for (String extension : list) {
                    if (str.contains("." + extension)) {
                        return extension;
                    }
                }
            }
            if ("text/plain".equals(str2)) {
                return "txt";
            }
            if ("text/html".equals(str2)) {
                return "html";
            }
            if ("audio/mpeg".equals(str2)) {
                return "mp3";
            }
            return list.get(0);
        }
        return null;
    }

    public static Set<String> getExtensions() {
        Set<String> extensions = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : getMimeTypeMap().entrySet()) {
            extensions.addAll(entry.getValue());
        }
        return extensions;
    }

    /**
     * 扩展名反查 MIME（官行 164）：遍历 MIME 表，ext 与某 MIME 的扩展名
     * 清单逐个 equals 比对（官体是 ext.equals(候选)），命中回该 MIME，
     * 全不中回 "image/jpeg"。2026-11-14 图片团随 ImgUtil 存相册族补落
     * （saveBmpToAlbum 的 MediaStore mime_type 取值走此法）。
     */
    public static String getMimeTypeByExt(String ext) {
        for (Map.Entry<String, List<String>> entry : getMimeTypeMap().entrySet()) {
            for (String e : entry.getValue()) {
                if (ext.equals(e)) {
                    return entry.getKey();
                }
            }
        }
        return "image/jpeg";
    }

    private static void loadKV(Map<String, List<String>> map, String key, String... values) {
        if (map.containsKey(key) && map.get(key) != null) {
            map.get(key).addAll(new ArrayList<>(Arrays.asList(values)));
            return;
        }
        map.put(key, new ArrayList<>(Arrays.asList(values)));
    }

    public static Map<String, List<String>> getMimeTypeMap() {
        Map<String, List<String>> map = new HashMap<>();
        loadKV(map, "application/andrew-inset", "ez");
        loadKV(map, "application/dsptype", "tsp");
        loadKV(map, "application/futuresplash", "spl");
        loadKV(map, "application/hta", "hta");
        loadKV(map, "application/mac-binhex40", "hqx");
        loadKV(map, "application/mac-compactpro", "cpt");
        loadKV(map, "application/mathematica", "nb");
        loadKV(map, "application/msaccess", "mdb");
        loadKV(map, "application/oda", "oda");
        loadKV(map, "application/ogg", "ogg", "ogx");
        loadKV(map, "application/pdf", "pdf");
        loadKV(map, "application/pgp-keys", "key");
        loadKV(map, "application/pgp-signature", "pgp");
        loadKV(map, "application/pics-rules", "prf");
        loadKV(map, "application/rar", "rar");
        loadKV(map, "application/vnd.rar", "rar");
        loadKV(map, "application/rdf+xml", "rdf");
        loadKV(map, "application/rss+xml", "rss");
        loadKV(map, "application/zip", "zip", "hkpkg", "hkzip");
        loadKV(map, "application/vnd.android.package-archive", "apk", "apks", "aab");
        loadKV(map, "application/vnd.cinderella", "cdy");
        loadKV(map, "application/vnd.ms-pki.stl", "stl");
        loadKV(map, "application/vnd.oasis.opendocument.database", "odb");
        loadKV(map, "application/vnd.oasis.opendocument.formula", "odf");
        loadKV(map, "application/vnd.oasis.opendocument.graphics", "odg");
        loadKV(map, "application/vnd.oasis.opendocument.graphics-template", "otg");
        loadKV(map, "application/vnd.oasis.opendocument.image", "odi");
        loadKV(map, "application/vnd.oasis.opendocument.spreadsheet", "ods");
        loadKV(map, "application/vnd.oasis.opendocument.spreadsheet-template", "ots");
        loadKV(map, "application/vnd.oasis.opendocument.text", "odt");
        loadKV(map, "application/vnd.oasis.opendocument.text-master", "odm");
        loadKV(map, "application/vnd.oasis.opendocument.text-template", "ott");
        loadKV(map, "application/vnd.oasis.opendocument.text-web", "oth");
        loadKV(map, "application/msword", "doc");
        loadKV(map, "application/msword", "dot");
        loadKV(map, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        loadKV(map, "application/vnd.openxmlformats-officedocument.wordprocessingml.template", "dotx");
        loadKV(map, "application/vnd.ms-excel", "xls");
        loadKV(map, "application/vnd.ms-excel", "xlt");
        loadKV(map, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
        loadKV(map, "application/vnd.openxmlformats-officedocument.spreadsheetml.template", "xltx");
        loadKV(map, "application/vnd.ms-powerpoint", "ppt");
        loadKV(map, "application/vnd.ms-powerpoint", "pot");
        loadKV(map, "application/vnd.ms-powerpoint", "pps");
        loadKV(map, "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");
        loadKV(map, "application/vnd.openxmlformats-officedocument.presentationml.template", "potx");
        loadKV(map, "application/vnd.openxmlformats-officedocument.presentationml.slideshow", "ppsx");
        loadKV(map, "application/vnd.rim.cod", "cod");
        loadKV(map, "application/vnd.smaf", "mmf");
        loadKV(map, "application/vnd.stardivision.calc", "sdc");
        loadKV(map, "application/vnd.stardivision.draw", "sda");
        loadKV(map, "application/vnd.stardivision.impress", "sdd");
        loadKV(map, "application/vnd.stardivision.impress", "sdp");
        loadKV(map, "application/vnd.stardivision.math", "smf");
        loadKV(map, "application/vnd.stardivision.writer", "sdw");
        loadKV(map, "application/vnd.stardivision.writer", "vor");
        loadKV(map, "application/vnd.stardivision.writer-global", "sgl");
        loadKV(map, "application/vnd.sun.xml.calc", "sxc");
        loadKV(map, "application/vnd.sun.xml.calc.template", "stc");
        loadKV(map, "application/vnd.sun.xml.draw", "sxd");
        loadKV(map, "application/vnd.sun.xml.draw.template", "std");
        loadKV(map, "application/vnd.sun.xml.impress", "sxi");
        loadKV(map, "application/vnd.sun.xml.impress.template", "sti");
        loadKV(map, "application/vnd.sun.xml.math", "sxm");
        loadKV(map, "application/vnd.sun.xml.writer", "sxw");
        loadKV(map, "application/vnd.sun.xml.writer.global", "sxg");
        loadKV(map, "application/vnd.sun.xml.writer.template", "stw");
        loadKV(map, "application/vnd.visio", "vsd");
        loadKV(map, "application/x-abiword", "abw");
        loadKV(map, "application/x-apple-diskimage", "dmg");
        loadKV(map, "application/x-bcpio", "bcpio");
        loadKV(map, "application/x-bittorrent", "torrent");
        loadKV(map, "application/x-cdf", "cdf");
        loadKV(map, "application/x-cdlink", "vcd");
        loadKV(map, "application/x-chess-pgn", "pgn");
        loadKV(map, "application/x-cpio", "cpio");
        loadKV(map, "application/x-debian-package", "deb");
        loadKV(map, "application/x-debian-package", "udeb");
        loadKV(map, "application/x-director", "dcr");
        loadKV(map, "application/x-director", "dir");
        loadKV(map, "application/x-director", "dxr");
        loadKV(map, "application/x-dms", "dms");
        loadKV(map, "application/x-doom", "wad");
        loadKV(map, "application/x-dvi", "dvi");
        loadKV(map, "application/x-flac", "flac");
        loadKV(map, "application/x-font", "pfa");
        loadKV(map, "application/x-font", "pfb");
        loadKV(map, "application/x-font", "gsf");
        loadKV(map, "application/x-font", "pcf");
        loadKV(map, "application/x-font", "pcf.Z");
        loadKV(map, "application/x-freemind", "mm");
        loadKV(map, "application/x-futuresplash", "spl");
        loadKV(map, "application/x-gnumeric", "gnumeric");
        loadKV(map, "application/x-Go-sgf", "sgf");
        loadKV(map, "application/x-graphing-calculator", "gcf");
        loadKV(map, "application/x-gtar", "gtar");
        loadKV(map, "application/x-gtar", "tgz");
        loadKV(map, "application/x-gtar", "taz");
        loadKV(map, "application/x-hdf", "hdf");
        loadKV(map, "application/x-ica", "ica");
        loadKV(map, "application/x-internet-signup", "ins");
        loadKV(map, "application/x-internet-signup", "isp");
        loadKV(map, "application/x-iphone", "iii");
        loadKV(map, "application/x-iso9660-image", "iso");
        loadKV(map, "application/x-jmol", "jmz");
        loadKV(map, "application/x-kchart", "chrt");
        loadKV(map, "application/x-killustrator", "kil");
        loadKV(map, "application/x-koan", "skp");
        loadKV(map, "application/x-koan", "skd");
        loadKV(map, "application/x-koan", "skt");
        loadKV(map, "application/x-koan", "skm");
        loadKV(map, "application/x-kpresenter", "kpr");
        loadKV(map, "application/x-kpresenter", "kpt");
        loadKV(map, "application/x-kspread", "ksp");
        loadKV(map, "application/x-kword", "kwd");
        loadKV(map, "application/x-kword", "kwt");
        loadKV(map, "application/x-latex", "latex");
        loadKV(map, "application/x-lha", "lha");
        loadKV(map, "application/x-lzh", "lzh");
        loadKV(map, "application/x-lzx", "lzx");
        loadKV(map, "application/x-maker", "frm");
        loadKV(map, "application/x-maker", "maker");
        loadKV(map, "application/x-maker", "frame");
        loadKV(map, "application/x-maker", "fb");
        loadKV(map, "application/x-maker", "book");
        loadKV(map, "application/x-maker", "fbdoc");
        loadKV(map, "application/x-mif", "mif");
        loadKV(map, "application/x-ms-wmd", "wmd");
        loadKV(map, "application/x-ms-wmz", "wmz");
        loadKV(map, "application/x-msi", "msi");
        loadKV(map, "application/x-ns-proxy-autoconfig", "pac");
        loadKV(map, "application/x-nwc", "nwc");
        loadKV(map, "application/x-object", "o");
        loadKV(map, "application/x-oz-application", "oza");
        loadKV(map, "application/x-pkcs12", "p12");
        loadKV(map, "application/x-pkcs7-certreqresp", "p7r");
        loadKV(map, "application/x-pkcs7-crl", "crl");
        loadKV(map, "application/x-quicktimeplayer", "qtl");
        loadKV(map, "application/x-shar", "shar");
        loadKV(map, "application/x-shockwave-flash", "swf");
        loadKV(map, "application/x-stuffit", "sit");
        loadKV(map, "application/x-sv4cpio", "sv4cpio");
        loadKV(map, "application/x-sv4crc", "sv4crc");
        loadKV(map, "application/x-tar", "tar");
        loadKV(map, "application/x-texinfo", "texinfo");
        loadKV(map, "application/x-texinfo", "texi");
        loadKV(map, "application/x-troff", "t");
        loadKV(map, "application/x-troff", "roff");
        loadKV(map, "application/x-troff-man", "man");
        loadKV(map, "application/x-ustar", "ustar");
        loadKV(map, "application/x-wais-source", "src");
        loadKV(map, "application/x-wingz", "wz");
        loadKV(map, "application/x-webarchive", "webarchive");
        loadKV(map, "application/x-x509-ca-cert", "crt");
        loadKV(map, "application/x-x509-user-cert", "crt");
        loadKV(map, "application/x-xcf", "xcf");
        loadKV(map, "application/x-xfig", "fig");
        loadKV(map, "application/xhtml+xml", "xhtml");
        loadKV(map, "application/epub+zip", "epub");
        loadKV(map, "application/ofd", "ofd");
        loadKV(map, "audio/3gpp", "3gpp");
        loadKV(map, "audio/amr", "amr");
        loadKV(map, "audio/basic", "snd");
        loadKV(map, "audio/midi", "mid");
        loadKV(map, "audio/midi", "midi");
        loadKV(map, "audio/midi", "kar");
        loadKV(map, "audio/midi", "xmf");
        loadKV(map, "audio/mobile-xmf", "mxmf");
        loadKV(map, "audio/mpeg", "mpga");
        loadKV(map, "audio/mpeg", "mpega");
        loadKV(map, "audio/mpeg", "mp2");
        loadKV(map, "audio/mpeg", "mp3");
        loadKV(map, "audio/mpeg", "m4a");
        loadKV(map, "audio/mpegurl", "m3u");
        loadKV(map, "audio/prs.sid", "sid");
        loadKV(map, "audio/x-aiff", "aif");
        loadKV(map, "audio/x-aiff", "aiff");
        loadKV(map, "audio/x-aiff", "aifc");
        loadKV(map, "audio/x-gsm", "gsm");
        loadKV(map, "audio/x-mpegurl", "m3u");
        loadKV(map, "audio/x-ms-wma", "wma");
        loadKV(map, "audio/x-ms-wax", "wax");
        loadKV(map, "audio/x-pn-realaudio", "ra");
        loadKV(map, "audio/x-pn-realaudio", "rm");
        loadKV(map, "audio/x-pn-realaudio", "ram");
        loadKV(map, "audio/x-realaudio", "ra");
        loadKV(map, "audio/x-scpls", "pls");
        loadKV(map, "audio/x-sd2", "sd2");
        loadKV(map, "audio/x-wav", "wav");
        loadKV(map, "audio/wav", "wav");
        loadKV(map, "image/bmp", "bmp");
        loadKV(map, "image/gif", "gif", "GIF");
        loadKV(map, "image/ico", "cur");
        loadKV(map, "image/ico", "ico");
        loadKV(map, "image/ief", "ief");
        loadKV(map, "image/jpeg", "jpeg", "JPEG");
        loadKV(map, "image/jpeg", "jpg", "JPG");
        loadKV(map, "image/jpeg", "jpe");
        loadKV(map, "image/pcx", "pcx");
        loadKV(map, "image/png", "png", "PNG");
        loadKV(map, "image/svg+xml", "svg");
        loadKV(map, "image/svg+xml", "svgz");
        loadKV(map, "image/tiff", "tiff");
        loadKV(map, "image/tiff", "tif");
        loadKV(map, "image/vnd.djvu", "djvu");
        loadKV(map, "image/vnd.djvu", "djv");
        loadKV(map, "image/vnd.wap.wbmp", "wbmp");
        loadKV(map, "image/x-cmu-raster", "ras");
        loadKV(map, "image/x-coreldraw", "cdr");
        loadKV(map, "image/x-coreldrawpattern", "pat");
        loadKV(map, "image/x-coreldrawtemplate", "cdt");
        loadKV(map, "image/x-corelphotopaint", "cpt");
        loadKV(map, "image/x-icon", "ico");
        loadKV(map, "image/x-jg", "art");
        loadKV(map, "image/x-jng", "jng");
        loadKV(map, "image/x-ms-bmp", "bmp");
        loadKV(map, "image/x-photoshop", "psd");
        loadKV(map, "image/x-portable-anymap", "pnm");
        loadKV(map, "image/x-portable-bitmap", "pbm");
        loadKV(map, "image/x-portable-graymap", "pgm");
        loadKV(map, "image/x-portable-pixmap", "ppm");
        loadKV(map, "image/x-rgb", "rgb");
        loadKV(map, "image/x-xbitmap", "xbm");
        loadKV(map, "image/x-xpixmap", "xpm");
        loadKV(map, "image/x-xwindowdump", "xwd");
        loadKV(map, "image/avif", "avif");
        loadKV(map, "model/iges", "igs");
        loadKV(map, "model/iges", "iges");
        loadKV(map, "model/mesh", "msh");
        loadKV(map, "model/mesh", "mesh");
        loadKV(map, "model/mesh", "silo");
        loadKV(map, "text/calendar", "ics");
        loadKV(map, "text/calendar", "icz");
        loadKV(map, "text/csv", "csv");
        loadKV(map, "text/comma-separated-values", "csv");
        loadKV(map, "text/css", "css");
        loadKV(map, "text/html", "htm");
        loadKV(map, "text/html", "html");
        loadKV(map, "text/h323", "323");
        loadKV(map, "text/iuls", "uls");
        loadKV(map, "text/mathml", "mml");
        loadKV(map, "text/plain", "txt");
        loadKV(map, "text/plain", "js");
        loadKV(map, "text/javascript", "js");
        loadKV(map, "application/json", "json");
        loadKV(map, "text/plain", "json");
        loadKV(map, "text/plain", "asc");
        loadKV(map, "text/plain", "text");
        loadKV(map, "text/plain", "diff");
        loadKV(map, "text/plain", "po");
        loadKV(map, "text/richtext", "rtx");
        loadKV(map, "text/rtf", "rtf");
        loadKV(map, "text/texmacs", "ts");
        loadKV(map, "video/mp2t", "ts");
        loadKV(map, "text/text", "phps");
        loadKV(map, "text/tab-separated-values", "tsv");
        loadKV(map, "text/xml", "xml");
        loadKV(map, "text/x-bibtex", "bib");
        loadKV(map, "text/x-boo", "boo");
        loadKV(map, "text/x-C++hdr", "h++");
        loadKV(map, "text/x-c++hdr", "hpp");
        loadKV(map, "text/x-c++hdr", "hxx");
        loadKV(map, "text/x-c++hdr", "hh");
        loadKV(map, "text/x-c++src", "c++");
        loadKV(map, "text/x-c++src", "cpp");
        loadKV(map, "text/x-c++src", "cxx");
        loadKV(map, "text/x-chdr", "h");
        loadKV(map, "text/x-component", "htc");
        loadKV(map, "text/x-csh", "csh");
        loadKV(map, "text/x-csrc", "c");
        loadKV(map, "text/x-dsrc", "d");
        loadKV(map, "text/x-haskell", "hs");
        loadKV(map, "text/x-Java", "java");
        loadKV(map, "text/x-literate-haskell", "lhs");
        loadKV(map, "text/x-moc", "moc");
        loadKV(map, "text/x-pascal", "p");
        loadKV(map, "text/x-pascal", "pas");
        loadKV(map, "text/x-pcs-gcd", "gcd");
        loadKV(map, "text/x-setext", "etx");
        loadKV(map, "text/x-tcl", "tcl");
        loadKV(map, "text/x-tex", "tex");
        loadKV(map, "text/x-tex", "ltx");
        loadKV(map, "text/x-tex", "sty");
        loadKV(map, "text/x-tex", "cls");
        loadKV(map, "text/x-vcalendar", "vcs");
        loadKV(map, "text/x-vcard", "vcf");
        loadKV(map, "video/3gpp", "3gpp");
        loadKV(map, "video/3gpp", "3gp");
        loadKV(map, "video/3gpp", "3g2");
        loadKV(map, "video/dl", "dl");
        loadKV(map, "video/dv", "dif");
        loadKV(map, "video/dv", "dv");
        loadKV(map, "video/fli", "fli");
        loadKV(map, "video/m4v", "m4v");
        loadKV(map, "video/mpeg", "mpeg");
        loadKV(map, "video/mpeg", "mpg");
        loadKV(map, "video/mpeg", "mpe");
        loadKV(map, "video/mp4", "mp4", "MP4", "m4s");
        loadKV(map, "video/x-matroska", "mkv", "MKV");
        loadKV(map, "video/x-flv", "flv", "FLV");
        loadKV(map, "video/flv", "flv", "FLV");
        loadKV(map, "video/iso.segment", "m4s");
        loadKV(map, "application/x-mpegURL", "m3u8");
        loadKV(map, "application/vnd.apple.mpegurl", "m3u8");
        loadKV(map, "application/mpegurl", "m3u8");
        loadKV(map, "application/x-mpegurl", "m3u8");
        loadKV(map, "application/x-mpeg", "m3u8");
        loadKV(map, "application/x-msdownload", "exe");
        loadKV(map, "application/wps-office.docx", "wps");
        loadKV(map, "font/ttf", "ttf");
        loadKV(map, "font/collection", "collection");
        loadKV(map, "font/otf", "otf");
        loadKV(map, "font/sfnt", "sfnt");
        loadKV(map, "font/woff2", "woff2");
        loadKV(map, "font/woff", "woff");
        loadKV(map, "application/x-gzip", "gz");
        loadKV(map, "application/x-7z-compressed", "7z");
        loadKV(map, "application/vnd.mozilla.xul+xml", "xul");
        loadKV(map, "image/webp", "webp");
        loadKV(map, "video/webm", "webm");
        loadKV(map, "audio/webm", "weba");
        loadKV(map, "application/x-sh", "sh");
        loadKV(map, "application/x-httpd-php", "php");
        loadKV(map, "audio/opus", "opus");
        loadKV(map, "text/javascript", "mjs");
        loadKV(map, "application/java-archive", "jar");
        loadKV(map, "application/vnd.ms-fontobject", "eot");
        loadKV(map, "application/x-bzip", "bz");
        loadKV(map, "application/octet-stream", "bin", "so", "o", "a", "obb", "bds", "hwt", "mtz", "itz", "nsp", "hap", "har");
        loadKV(map, "application/vnd.huawei.hap", "hap");
        loadKV(map, "application/vnd.huawei.har", "har");
        loadKV(map, "application/vnd.amazon.ebook", "azw");
        loadKV(map, "application/x-freearc", "arc");
        loadKV(map, "audio/aac", "aac");
        loadKV(map, "application/x-subrip", "srt", "ass");
        loadKV(map, "text/vtt", "vtt");
        loadKV(map, "text/plain", "hiker");
        loadKV(map, "text/markdown", "md");
        loadKV(map, "video/mpeg", "VOB");
        loadKV(map, "video/quicktime", "qt");
        loadKV(map, "video/quicktime", "mov", "MOV");
        loadKV(map, "video/vnd.mpegurl", "mxu");
        loadKV(map, "video/x-la-asf", "lsf");
        loadKV(map, "video/x-la-asf", "lsx");
        loadKV(map, "video/x-mng", "mng");
        loadKV(map, "video/x-ms-asf", "asf");
        loadKV(map, "video/x-ms-asf", "asx");
        loadKV(map, "video/x-ms-wm", "wm");
        loadKV(map, "video/x-ms-wmv", "wmv");
        loadKV(map, "video/x-ms-wmx", "wmx");
        loadKV(map, "video/x-ms-wvx", "wvx");
        loadKV(map, "video/x-msvideo", "avi");
        loadKV(map, "video/x-sgi-movie", "movie");
        loadKV(map, "x-conference/x-cooltalk", "ice");
        loadKV(map, "x-epoc/x-sisx-app", "sisx");
        return map;
    }
}
