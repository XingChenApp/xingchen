package com.fongmi.android.tv.api.hk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 海阔「同片多址」归并判定（纯逻辑，无 Android 依赖，可 JVM 单测）。
 *
 * 背景：海阔民间规则有第二种同片多址表达——同一个视频的一组候选节点被规则逐条
 * 报成独立条目（本体同一播放页，仅差节点参数，如 ?zk=0..3），原版海阔没有
 * 线路×选集模型所以不成病；星辰交接层旧口径只认单条目内嵌 PlayData 的多址表达，
 * 多条目形态被默认吞成「一条线、多集」（播放地址被当成选集，2026-10-23 真机复现）。
 * 本类在交接翻译层（章节链与详情桥链共用）判定哪些条目是同址组，供调用方折成
 * 一集多线路。判定是协议形态＋同址结构，不含任何规则名/站点名/标题文案匹配。
 *
 * 入参契约（2026-10-24 法医定案，hk-episode-1418-forensics）：plan() 只收原始
 * 形态地址——规则条目的原始包装地址（hiker://empty#…?zk=N@lazyRule=… 一类）
 * 或未经解析的原始播放页地址。已被懒解析换成干净纯地址的条目（无 ?zk=、
 * 无 @lazyRule=，甚至异 host/异路径）与原始条目不同桶，喂进来会把同组劈开：
 * 被点条剩成单例集、其余条反成另一集的线路，层级倒置（1.4.18 真机复现）。
 * 调用方手里的地址若已被点击/懒解析污染，须先按原始地址归一化再喂入——
 * 章节链在 HkListActivity.startPlayerChapters 用每章 originalUrl 优先、
 * 为空回退现行地址组装判定输入；播放地址本身不经本类，归一化只动判定输入。
 *
 * 窄口径（拿不准不并，任一校验不过整盘不并）：
 * 1. 参与条目须能剥出 http(s) 本体（剥 @lazyRule= 尾巴、剥 hiker://empty# 包装）；
 *    磁力/ed2k/迅雷/js:/@rule=/ftp 一律不参与（规则标记型与 P2P 型另有链路）。
 * 2. 同组本体：scheme＋host＋路径逐字相同、查询键集合相同（形态一：本体全等，
 *    仅 lazyRule 函数体不同；形态二：仅差查询参数值）。
 * 3. 内容编号：路径里须含同一内容编号（最长数字串，各成员一致且都真实出现在
 *    路径段中）——挡「同页靠参数分集/分内容」。
 * 4. 差异键（值在成员间不全等的查询键）不得命中：分集语义键（ep/part/chapter/
 *    page/index…）、内容 id 类键（*id 结尾）、会话签名类键（token/sign/ts…）。
 * 5. 节点名须能从条目标题提取出互不相同的非集号名（标题呈「第N集」形态的判为
 *    分集、否决）——名字提取失败即拿不准，不并。
 * 6. 多个同址组并存时，各组节点名序列须完全同构，否则整盘不并（下游线路模型
 *    要求各集共用同一组线路名）。
 *
 * selIndex（被点条目下标，桥链无此概念时传 -1）落在组内时，全局线路序旋转为
 * 被点地址居首（默认线路＝被点地址），各组按线名同步对齐。
 */
public final class HkEpisodeMerger {

    private HkEpisodeMerger() {
    }

    /** 一个同址组：members 是原下标（已按全局线路序对齐），lineNames 与之对齐。 */
    public static final class Group {
        public final int[] members;
        public final String[] lineNames;

        Group(int[] members, String[] lineNames) {
            this.members = members;
            this.lineNames = lineNames;
        }
    }

    public static final class Plan {
        public final boolean merge;
        /** 全部同址组（成员数 ≥2）；merge=false 时为空数组。 */
        public final Group[] groups;
        /** 原下标 → 收缩后的集下标；merge=false 时为恒等映射。 */
        public final int[] episodeIndexOf;
        /** 全局线路名序（各组同构）；merge=false 时为 null。 */
        public final String[] lineNames;

        Plan(boolean merge, Group[] groups, int[] episodeIndexOf, String[] lineNames) {
            this.merge = merge;
            this.groups = groups;
            this.episodeIndexOf = episodeIndexOf;
            this.lineNames = lineNames;
        }
    }

    /** 分集语义键：差异键命中即否决（同页 ?ep=N 分集型与节点型的键集合形态相同，靠它分开）。 */
    private static final String[] EPISODE_KEYS = {
            "ep", "episode", "episodes", "epi", "part", "parts", "chapter", "chapters",
            "chap", "section", "sec", "p", "page", "pg", "idx", "index", "no", "num",
            "number", "seq", "sort", "order", "e", "vol", "volume", "playepisode"
    };

    /** 会话/签名类键：值因会话而异，差异与内容无关，命中即否决。 */
    private static final String[] SESSION_KEYS = {
            "token", "sign", "signature", "auth", "key", "ts", "timestamp", "time",
            "nonce", "rand", "random", "hash", "pwd", "password", "session", "cookie"
    };

    public static Plan plan(List<String> urls, List<String> titles, int selIndex) {
        int n = urls == null ? 0 : urls.size();
        int[] identity = new int[n];
        for (int i = 0; i < n; i++) identity[i] = i;
        Plan none = new Plan(false, new Group[0], identity, null);
        if (n < 2) return none;

        // 第一遍：解析本体＋粗分桶（scheme/host/路径/键集合）
        Body[] bodies = new Body[n];
        Map<String, List<Integer>> buckets = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            bodies[i] = parse(urls.get(i));
            if (bodies[i] == null) continue;
            buckets.computeIfAbsent(bodies[i].bucketKey, k -> new ArrayList<>()).add(i);
        }

        // 第二遍：逐桶深验（任一桶失败整盘不并）
        List<RawGroup> rawGroups = new ArrayList<>();
        for (List<Integer> bucket : buckets.values()) {
            if (bucket.size() < 2) continue;
            RawGroup g = verify(bucket, bodies, titles);
            if (g == null) return none;
            rawGroups.add(g);
        }
        if (rawGroups.isEmpty()) return none;

        // 多组同构：节点名序列全等
        String[] globalNames = rawGroups.get(0).names;
        for (RawGroup g : rawGroups) {
            if (!Arrays.equals(globalNames, g.names)) return none;
        }
        // 被点地址居首：找 sel 所在组的线名，旋转全局序
        int selLine = -1;
        if (selIndex >= 0 && selIndex < n) {
            for (RawGroup g : rawGroups) {
                int pos = g.members.indexOf(selIndex);
                if (pos >= 0) {
                    selLine = indexOfName(globalNames, g.names[pos]);
                    break;
                }
            }
        }
        int rotate = selLine > 0 ? selLine : 0;
        String[] orderedNames = rotate(globalNames, rotate);

        // 各组按全局线名序对齐成员，生成最终 Plan
        Group[] groups = new Group[rawGroups.size()];
        int[] episodeIndexOf = identity.clone();
        Map<Integer, Integer> collapsedAt = new LinkedHashMap<>(); // 组序号 -> 收缩位（首个成员的原序位）
        for (int gi = 0; gi < rawGroups.size(); gi++) {
            RawGroup g = rawGroups.get(gi);
            int[] members = new int[g.members.size()];
            for (int line = 0; line < orderedNames.length; line++) {
                int pos = indexOfName(g.names, orderedNames[line]);
                members[line] = g.members.get(pos);
            }
            groups[gi] = new Group(members, orderedNames.clone());
            int min = Integer.MAX_VALUE;
            for (int m : g.members) min = Math.min(min, m);
            collapsedAt.put(gi, min);
        }
        int ep = 0;
        for (int i = 0; i < n; i++) {
            int gi = groupOf(rawGroups, i);
            if (gi < 0) {
                episodeIndexOf[i] = ep++;
            } else if (i == collapsedAt.get(gi)) {
                episodeIndexOf[i] = ep;
                for (int m : rawGroups.get(gi).members) episodeIndexOf[m] = ep;
                ep++;
            }
        }
        return new Plan(true, groups, episodeIndexOf, orderedNames);
    }

    private static int groupOf(List<RawGroup> groups, int index) {
        for (int gi = 0; gi < groups.size(); gi++) {
            if (groups.get(gi).members.contains(index)) return gi;
        }
        return -1;
    }

    private static int indexOfName(String[] names, String name) {
        for (int i = 0; i < names.length; i++) if (names[i].equals(name)) return i;
        return -1;
    }

    private static String[] rotate(String[] in, int by) {
        if (by <= 0) return in.clone();
        String[] out = new String[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[(i + by) % in.length];
        return out;
    }

    private static final class RawGroup {
        final List<Integer> members;
        /** 与 members 对齐的节点名（提取序，未旋转未对齐）。 */
        final String[] names;

        RawGroup(List<Integer> members, String[] names) {
            this.members = members;
            this.names = names;
        }
    }

    /** 逐桶深验：内容编号一致、差异键不命中否决表、节点名可提取。全过才成组。 */
    private static RawGroup verify(List<Integer> bucket, Body[] bodies, List<String> titles) {
        // 内容编号：路径里的主 id 全员一致且逐条真实命中路径段
        String mainId = null;
        for (int i : bucket) {
            String id = HkEpisodeSorter.extractMainId(bodies[i].path);
            if (id == null) return null;
            if (!HkEpisodeSorter.containsIdToken(bodies[i].path, id)) return null;
            if (mainId == null) mainId = id;
            else if (!mainId.equals(id)) return null;
        }
        // 差异键：值在成员间不全等的查询键
        Map<String, String> first = bodies[bucket.get(0)].query;
        List<String> diffKeys = new ArrayList<>();
        for (String key : first.keySet()) {
            String v0 = first.get(key);
            for (int i : bucket) {
                String v = bodies[i].query.get(key);
                if (v == null || !v.equals(v0)) {
                    diffKeys.add(key);
                    break;
                }
            }
        }
        for (String key : diffKeys) {
            String k = key.toLowerCase(Locale.ROOT);
            if (k.endsWith("id")) return null; // 内容 id 类键不同＝不同内容
            for (String bad : EPISODE_KEYS) if (k.equals(bad)) return null;
            for (String bad : SESSION_KEYS) if (k.equals(bad)) return null;
        }
        // 节点名提取
        List<String> ts = new ArrayList<>(bucket.size());
        for (int i : bucket) {
            ts.add(titles != null && i < titles.size() && titles.get(i) != null ? titles.get(i) : "");
        }
        String[] names = extractLineNames(ts);
        if (names == null) return null;
        return new RawGroup(new ArrayList<>(bucket), names);
    }

    // ---------------- 本体解析 ----------------

    private static final class Body {
        final String bucketKey;
        final String path;
        final Map<String, String> query;

        Body(String bucketKey, String path, Map<String, String> query) {
            this.bucketKey = bucketKey;
            this.path = path;
            this.query = query;
        }
    }

    /** 剥规则尾巴与包装后解析本体；不合参与形态返回 null。 */
    private static Body parse(String url) {
        if (url == null) return null;
        String u = url.trim();
        if (u.isEmpty()) return null;
        String low = u.toLowerCase(Locale.ROOT);
        if (low.startsWith("magnet:") || low.startsWith("ed2k:") || low.startsWith("thunder:")
                || low.startsWith("js:") || low.startsWith("ftp:")) return null;
        if (u.contains("@rule=")) return null;
        // 剥 @lazyRule= 尾巴（函数体不进本体）
        int lr = u.indexOf("@lazyRule=");
        if (lr >= 0) u = u.substring(0, lr);
        // 剥 hiker://empty#<真实地址> 包装
        int hash = u.indexOf('#');
        if (hash >= 0) {
            String tail = u.substring(hash + 1);
            String tailLow = tail.toLowerCase(Locale.ROOT);
            if (tailLow.startsWith("http://") || tailLow.startsWith("https://")) u = tail;
        }
        // 去 fragment
        int frag = u.indexOf('#');
        if (frag >= 0) u = u.substring(0, frag);
        String ulow = u.toLowerCase(Locale.ROOT);
        if (!ulow.startsWith("http://") && !ulow.startsWith("https://")) return null;
        // 拆 scheme/host/path/query
        int schemeEnd = u.indexOf("://");
        String scheme = ulow.substring(0, schemeEnd);
        String rest = u.substring(schemeEnd + 3);
        String queryStr = "";
        int q = rest.indexOf('?');
        if (q >= 0) {
            queryStr = rest.substring(q + 1);
            rest = rest.substring(0, q);
        }
        int slash = rest.indexOf('/');
        String host = (slash >= 0 ? rest.substring(0, slash) : rest).toLowerCase(Locale.ROOT);
        String path = slash >= 0 ? rest.substring(slash) : "";
        Map<String, String> query = new TreeMap<>();
        if (!queryStr.isEmpty()) {
            for (String pair : queryStr.split("&")) {
                if (pair.isEmpty()) continue;
                int eq = pair.indexOf('=');
                if (eq >= 0) query.put(pair.substring(0, eq), pair.substring(eq + 1));
                else query.put(pair, "");
            }
        }
        String bucketKey = scheme + "://" + host + path + "?" + String.join("&", query.keySet());
        return new Body(bucketKey, path, query);
    }

    // ---------------- 节点名提取 ----------------

    /**
     * 从组内标题提取节点名，与输入等长；提取不出整组互不相同的非集号名时返回 null（不并）。
     * 方案一（尾段法）：取最后一个强分隔符（· | ： :）之后的段——
     * 「▶ 播放地址1 · 高清加速节点」的节点名正在尾段。
     * 方案二（公共缀剥离）：剥组内公共前缀/后缀取中段，中段再走一次尾段法。
     */
    private static String[] extractLineNames(List<String> titles) {
        int m = titles.size();
        String[] ts = new String[m];
        for (int i = 0; i < m; i++) ts[i] = titles.get(i) == null ? "" : titles.get(i).trim();
        String[] tails = new String[m];
        for (int i = 0; i < m; i++) tails[i] = tailSegment(ts[i]);
        if (allValidNames(tails)) return tails;
        String prefix = commonPrefix(ts);
        String suffix = commonSuffix(ts, prefix.length());
        String[] mids = new String[m];
        for (int i = 0; i < m; i++) {
            String s = ts[i];
            int from = Math.min(prefix.length(), s.length());
            int to = Math.max(from, s.length() - suffix.length());
            String mid = s.substring(from, to).trim();
            mid = mid.replaceAll("^[0-9]+\\s*[·|、．.\\-—:：]?\\s*", "").trim();
            mid = mid.replaceAll("[·|、．.\\-—:：\\s]+$", "").trim();
            mids[i] = tailSegment(mid);
        }
        if (allValidNames(mids)) return mids;
        return null;
    }

    private static String tailSegment(String s) {
        int cut = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '·' || c == '|' || c == '：' || c == ':') cut = i;
        }
        return (cut >= 0 ? s.substring(cut + 1) : s).trim();
    }

    private static boolean allValidNames(String[] names) {
        for (int i = 0; i < names.length; i++) {
            if (!nameOk(names[i])) return false;
            for (int j = i + 1; j < names.length; j++) {
                if (names[i].equals(names[j])) return false;
            }
        }
        return true;
    }

    private static boolean nameOk(String s) {
        if (s == null || s.isEmpty() || s.length() > 24) return false;
        if (s.contains("://")) return false;
        if (s.matches("[0-9]+")) return false; // 纯数字是集号不是节点名
        if (s.matches("(?i)(EP|E|SP)\\s*[0-9]+")) return false;
        if (s.matches("第\\s*[0-9一二三四五六七八九十百]+\\s*(集|话|話|期|章|回)?")) return false;
        if (s.matches("[0-9]+\\s*(集|话|話|期|章|回)")) return false;
        return true;
    }

    private static String commonPrefix(String[] arr) {
        String p = arr[0];
        for (String s : arr) {
            int i = 0, max = Math.min(p.length(), s.length());
            while (i < max && p.charAt(i) == s.charAt(i)) i++;
            p = p.substring(0, i);
            if (p.isEmpty()) break;
        }
        return p;
    }

    private static String commonSuffix(String[] arr, int prefixLen) {
        String p = arr[0];
        for (String s : arr) {
            int i = 0, max = Math.min(p.length(), s.length());
            while (i < max && p.charAt(p.length() - 1 - i) == s.charAt(s.length() - 1 - i)) i++;
            p = p.substring(p.length() - i);
            if (p.isEmpty()) break;
        }
        int maxLen = Integer.MAX_VALUE;
        for (String s : arr) maxLen = Math.min(maxLen, s.length() - prefixLen);
        if (p.length() > Math.max(0, maxLen)) p = p.substring(p.length() - Math.max(0, maxLen));
        return p;
    }
}
