package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * txt 混合链接解析：提取 magnet:/ed2k:// /ftp:// /http(s):// 等可播链接，生成播放列表。
 * 需求（2026-10-10）：txt 文件导入播放，内混磁力/HTTP/m3u8/ed2k 等链接。
 */
public class TxtParser {

    public static class Link {
        public final String name;
        public final String url;

        public Link(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    /** 匹配 http(s)/ftp/thunder/magnet/video 及 ed2k 链接。ftp 走迅雷线（上游
     * Thunder SCHEMES 含 ftp，活链 XLTaskHelper.addThunderTask 内 ftp:// 走
     * P2sp 任务），故与 magnet/thunder 同款抽取，不再漏给直播列表。ed2k 只认
     * scheme 前缀，不做 |file|／大小／32 位哈希结构校验：上游
     * Thunder.Parser.PATTERN 就是 {@code ^(magnet|thunder|ed2k):.*} 前缀口径，
     * 活链 addThunderTask 不解析 ed2k 内部结构：ftp:// 走 P2sp、ed2k:// 走
     * Emule、其余 scheme 直接抛错拒绝；任务层唯一硬门是 addThunderTask
     * 要求 ed2k:// 前缀，无 // 的 ed2k 在此抽出后由下游以真错拒绝，
     * 不在本门静默丢弃。
     * 抽取时优先在规范 |/ 结尾处收束（其后可选 |sources,…|/ 段），无 |/
     * 才整段收 token，避免把行尾紧贴的中文名吞进地址。 */
    private static final Pattern LINK = Pattern.compile("(https?|ftp|thunder|magnet|video):\\S+|ed2k:[^\\s]*?\\|/(\\|sources,[^|]*\\|/)?|ed2k:\\S+");

    private static final int MAX_LINKS = 500;

    /**
     * 混合链接文件判定：含 magnet:/ed2k:/thunder:/ftp: 即走播放列表；
     * 否则按直播列表处理（兼容旧行为，纯 http 的 txt 仍进直播）。
     * ed2k/thunder 只认 scheme 前缀、不要求 //：纯 thunder txt、无 // 的 ed2k txt
     * 以前因不含 magnet:/ed2k:// 被误判成直播列表，改后进迅雷线。
     * ftp 同款：纯 ftp txt 以前因无上述前缀被误判成直播列表，改后进迅雷线。
     */
    public static boolean isMixedLinks(String text) {
        return text != null && (text.contains("magnet:") || text.contains("ed2k:") || text.contains("thunder:") || text.contains("ftp:"));
    }

    public static List<Link> parse(String text) {
        Map<String, String> dedup = new LinkedHashMap<>();
        if (text == null) return new ArrayList<>();
        int n = 0;
        for (String line : text.split("\\r?\\n")) {
            Matcher m = LINK.matcher(line);
            while (m.find()) {
                if (dedup.size() >= MAX_LINKS) break;
                String raw = m.group(0);
                String url = raw.replaceAll("[.,;!?\"'\\)\\]]+$", "");
                if (url.length() < 8 || dedup.containsKey(url)) continue;
                String name = line.replace(raw, "").trim();
                if (name.length() > 24) name = name.substring(0, 24);
                if (name.isEmpty()) name = "链接" + (++n);
                dedup.put(url, name);
            }
        }
        List<Link> list = new ArrayList<>(dedup.size());
        for (Map.Entry<String, String> e : dedup.entrySet()) list.add(new Link(e.getValue(), e.getKey()));
        return list;
    }
}
