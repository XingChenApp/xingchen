package com.fongmi.android.tv.player.danmaku.live;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 源无关识别层（设计第四节）：只认带房号的强信号，弱信号（CDN 域名/标题）永不单独定罪。
 * 斗鱼：vod_id 前缀 douyu_{rid} 与 live.douyu.com/{rid} 链接形态。
 * B站：vod_id 前缀 bili_{候选号} 与 live.bilibili.com/{候选号} 链接形态（候选号可能是短号，
 * 真实号换算与在播校验在 provider 预备步兜底）；裸数字是最弱形态，只许在上下文
 * （源名/playFrom/标题含哔哩、B站、bilibili）明确指向 B站时才作候选；聚合源名 SimpleLive
 * 只是弱语境，上下文另有别家平台明文标记（斗鱼/虎牙/抖音/快手）时不许冒认。
 * 虎牙：vod_id 前缀 huya_{房号}（房号可为数字或字母短号）与 huya.com/{房号} 链接形态
 * （www/m 子域可有可无）；不设裸数字路，带房号的三件套提取与在播校验在 provider 预备步兜底。
 * 抖音：vod_id 前缀 douyin_{webRid} 与 live.douyin.com/{webRid} 链接形态（webRid 为纯数字串）；
 * 不设裸数字路，进房要先经房间页把 webRid 换成真实 room_id 并校验在播，
 * 都在 provider 预备步兜底。
 * 快手：vod_id 前缀 kuaishou_{principalId} 与 live.kuaishou.com/u/{principalId} 链接形态
 * （principalId 为字母/数字/连字符/下划线的主播 id，如 3x…、laoayi-88）；不设裸数字路，
 * liveStreamId 换取与在播校验在 provider 预备步兜底。
 * 「数字即认」永不成立。识别不出返回 empty＝不接，绝不拿标题反查房间。
 */
public final class LiveRoomResolver {

    private static final Pattern DOUYU_PREFIX = Pattern.compile("^douyu_(\\d{1,15})$");
    private static final Pattern DOUYU_LINK = Pattern.compile("live\\.douyu\\.com/(\\d{1,15})(?:[/?#]|$)");
    private static final Pattern BILI_PREFIX = Pattern.compile("^bili_(\\d{1,15})$");
    private static final Pattern BILI_LINK = Pattern.compile("live\\.bilibili\\.com/(\\d{1,15})(?:[/?#]|$)");
    private static final Pattern HUYA_PREFIX = Pattern.compile("^huya_([0-9A-Za-z]{1,20})$");
    private static final Pattern HUYA_LINK = Pattern.compile("(?:www\\.|m\\.)?huya\\.com/([0-9A-Za-z]{1,20})(?:[/?#]|$)");
    private static final Pattern DOUYIN_PREFIX = Pattern.compile("^douyin_(\\d{1,20})$");
    private static final Pattern DOUYIN_LINK = Pattern.compile("live\\.douyin\\.com/(\\d{1,20})(?:[/?#]|$)");
    private static final Pattern KUAISHOU_PREFIX = Pattern.compile("^kuaishou_([0-9A-Za-z_-]{1,32})$");
    private static final Pattern KUAISHOU_LINK = Pattern.compile("live\\.kuaishou\\.com/u/([0-9A-Za-z_-]{1,32})(?:[/?#]|$)");
    private static final Pattern BARE_DIGITS = Pattern.compile("^\\d{1,15}$");

    private LiveRoomResolver() {
    }

    /**
     * 起播装配点的信号快照（取自 PlaySpec 与其源 Result 的 Vod，全部可空）。
     * siteKey/name/playFrom 只作加权证据，本步不单独成立任何判定。
     */
    public record Snapshot(String siteKey, String vodId, String playUrl, String playFrom, String name, String streamUrl) {

        public static final Snapshot EMPTY = new Snapshot("", "", "", "", "", "");

        public Snapshot {
            siteKey = siteKey == null ? "" : siteKey;
            vodId = vodId == null ? "" : vodId;
            playUrl = playUrl == null ? "" : playUrl;
            playFrom = playFrom == null ? "" : playFrom;
            name = name == null ? "" : name;
            streamUrl = streamUrl == null ? "" : streamUrl;
        }
    }

    public static Optional<RoomRef> resolve(Snapshot snapshot) {
        if (snapshot == null) return Optional.empty();
        Optional<RoomRef> douyu = resolveDouyu(snapshot);
        if (douyu.isPresent()) return douyu;
        Optional<RoomRef> bili = resolveBili(snapshot);
        if (bili.isPresent()) return bili;
        Optional<RoomRef> huya = resolveHuya(snapshot);
        if (huya.isPresent()) return huya;
        Optional<RoomRef> douyin = resolveDouyin(snapshot);
        if (douyin.isPresent()) return douyin;
        return resolveKuaishou(snapshot);
    }

    private static Optional<RoomRef> resolveKuaishou(Snapshot snapshot) {
        Matcher prefix = KUAISHOU_PREFIX.matcher(snapshot.vodId().trim());
        if (prefix.matches()) return Optional.of(new RoomRef(RoomRef.Platform.KUAISHOU, prefix.group(1)));
        String link = findKuaishouLink(snapshot.vodId());
        if (link != null) return Optional.of(new RoomRef(RoomRef.Platform.KUAISHOU, link));
        link = findKuaishouLink(snapshot.playUrl());
        if (link != null) return Optional.of(new RoomRef(RoomRef.Platform.KUAISHOU, link));
        return Optional.empty();
    }

    private static String findKuaishouLink(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher matcher = KUAISHOU_LINK.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Optional<RoomRef> resolveDouyin(Snapshot snapshot) {
        Matcher prefix = DOUYIN_PREFIX.matcher(snapshot.vodId().trim());
        if (prefix.matches()) return Optional.of(new RoomRef(RoomRef.Platform.DOUYIN, prefix.group(1)));
        String link = findDouyinLink(snapshot.vodId());
        if (link != null) return Optional.of(new RoomRef(RoomRef.Platform.DOUYIN, link));
        link = findDouyinLink(snapshot.playUrl());
        if (link != null) return Optional.of(new RoomRef(RoomRef.Platform.DOUYIN, link));
        return Optional.empty();
    }

    private static String findDouyinLink(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher matcher = DOUYIN_LINK.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Optional<RoomRef> resolveHuya(Snapshot snapshot) {
        Matcher prefix = HUYA_PREFIX.matcher(snapshot.vodId().trim());
        if (prefix.matches()) return Optional.of(new RoomRef(RoomRef.Platform.HUYA, prefix.group(1)));
        String link = findHuyaLink(snapshot.vodId());
        if (link != null) return Optional.of(new RoomRef(RoomRef.Platform.HUYA, link));
        link = findHuyaLink(snapshot.playUrl());
        if (link != null) return Optional.of(new RoomRef(RoomRef.Platform.HUYA, link));
        return Optional.empty();
    }

    private static String findHuyaLink(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher matcher = HUYA_LINK.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Optional<RoomRef> resolveBili(Snapshot snapshot) {
        Matcher prefix = BILI_PREFIX.matcher(snapshot.vodId().trim());
        if (prefix.matches()) return Optional.of(new RoomRef(RoomRef.Platform.BILI, prefix.group(1)));
        String link = findBiliLink(snapshot.vodId());
        if (link != null) return Optional.of(new RoomRef(RoomRef.Platform.BILI, link));
        link = findBiliLink(snapshot.playUrl());
        if (link != null) return Optional.of(new RoomRef(RoomRef.Platform.BILI, link));
        // 裸数字（SimpleLive 形态）：没有平台语境一律不认，语境只认明文标记，不认域名/标题猜测
        String candidate = snapshot.vodId().trim();
        if (!BARE_DIGITS.matcher(candidate).matches()) return Optional.empty();
        String context = (snapshot.siteKey() + " " + snapshot.playFrom() + " " + snapshot.name()).toLowerCase(Locale.ROOT);
        if (context.contains("哔哩") || context.contains("b站") || context.contains("bilibili")) {
            return Optional.of(new RoomRef(RoomRef.Platform.BILI, candidate));
        }
        // SimpleLive 是聚合源名而非 B站标记，只作弱语境：上下文另有别家平台明文标记时不许冒认
        boolean otherPlatform = context.contains("斗鱼") || context.contains("douyu")
                || context.contains("虎牙") || context.contains("huya")
                || context.contains("抖音") || context.contains("douyin")
                || context.contains("快手") || context.contains("kuaishou");
        if (!otherPlatform && context.contains("simplelive")) {
            return Optional.of(new RoomRef(RoomRef.Platform.BILI, candidate));
        }
        return Optional.empty();
    }

    private static String findBiliLink(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher matcher = BILI_LINK.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Optional<RoomRef> resolveDouyu(Snapshot snapshot) {
        Matcher prefix = DOUYU_PREFIX.matcher(snapshot.vodId().trim());
        if (prefix.matches()) return Optional.of(new RoomRef(RoomRef.Platform.DOUYU, prefix.group(1)));
        String link = findDouyuLink(snapshot.vodId());
        if (link != null) return Optional.of(new RoomRef(RoomRef.Platform.DOUYU, link));
        link = findDouyuLink(snapshot.playUrl());
        if (link != null) return Optional.of(new RoomRef(RoomRef.Platform.DOUYU, link));
        return Optional.empty();
    }

    private static String findDouyuLink(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher matcher = DOUYU_LINK.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
