package com.fongmi.android.tv.api;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.player.danmaku.DanmakuUrlPolicy;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Response;

public class DanmakuApi {

    private static final String TAG = DanmakuApi.class.getSimpleName();

    /** 置信度评分：标题精确匹配 */
    private static final int SCORE_TITLE_EXACT = 100;
    /** 置信度评分：标题包含匹配 */
    private static final int SCORE_TITLE_CONTAINS = 60;
    /** 置信度评分：集数匹配 */
    private static final int SCORE_EPISODE_MATCH = 50;
    /** 置信度评分：请求有集数但候选无集数信息 */
    private static final int SCORE_EPISODE_MISSING = -20;
    /** 置信度评分：集数不一致（直接否决） */
    private static final int SCORE_EPISODE_MISMATCH = -100;
    /** 自动加载的最低置信度：标题必须基本匹配 */
    private static final int CONFIDENCE_THRESHOLD = 100;

    private static final Pattern EPISODE_NUMBER = Pattern.compile("第\\s*(\\d+)\\s*[集话期]");
    private static final Pattern EPISODE_EP = Pattern.compile("[Ee][Pp]?\\s*(\\d{1,4})(?!\\d)");

    public static boolean canSearch() {
        return DanmakuSetting.isLoad() && DanmakuSetting.isAuto() && DanmakuSetting.hasValidApiUrl();
    }

    public static boolean canAutoSearch(List<Danmaku> siteDanmakus) {
        return canSearch() && (!DanmakuSetting.isSpiderFirst() || siteDanmakus == null || siteDanmakus.isEmpty());
    }

    public static Call newCall(String name, String episode) {
        String url = DanmakuSetting.getValidApiUrl();
        if (TextUtils.isEmpty(url)) return null;
        OkHttp.cancel(TAG);
        name = Trans.t2s(false, name);
        episode = Trans.t2s(false, episode);
        try {
            if (url.contains("{name}") || url.contains("{episode}")) {
                return OkHttp.newCall(url.replace("{name}", Uri.encode(name)).replace("{episode}", Uri.encode(episode)), TAG);
            } else {
                url = getSearchUrl(url);
                if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "search name=%s episode=%s %s", name, episode, DanmakuUrlPolicy.logSummary(url));
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("name", name);
                params.put("episode", episode);
                return OkHttp.newCall(url, OkHttp.toBody(params), TAG);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String getSearchUrl(String url) {
        Uri uri = Uri.parse(url);
        List<String> segments = uri.getPathSegments();
        if (!segments.isEmpty() && "danmaku".equalsIgnoreCase(segments.get(segments.size() - 1))) return url;
        if (segments.size() > 1) return url;
        return uri.buildUpon().appendPath("danmaku").build().toString();
    }

    public static List<Danmaku> arrayFrom(String body) {
        return normalize(Danmaku.arrayFrom(body));
    }

    private static List<Danmaku> normalize(List<Danmaku> items) {
        if (items.isEmpty()) return items;
        String api = getSearchUrl(DanmakuSetting.getValidApiUrl());
        for (Danmaku item : items) item.setUrl(normalizeUrl(api, item.getUrl()));
        return items;
    }

    private static String normalizeUrl(String api, String url) {
        String normalized = DanmakuUrlPolicy.normalize(api, url);
        if (SpiderDebug.isEnabled() && !TextUtils.equals(url, normalized)) SpiderDebug.log("danmaku", "normalize result %s", DanmakuUrlPolicy.logSummary(normalized));
        return normalized;
    }

    public static void search(String name, String episode, Consumer<Danmaku> found) {
        Call call = newCall(name, episode);
        if (call == null) return;
        call.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    arrayFrom(response.body().string()).stream().findFirst().ifPresent(item -> App.post(() -> found.accept(item)));
                } catch (Exception ignored) {
                }
            }
        });
    }

    /**
     * 自动搜索：从 API 返回的候选中选出最匹配的一个。
     * 置信度不足（标题/集数对不上）时不回调，静默放弃，宁可不加载也不加载错的。
     * 多个高置信度候选中，优先选择结果最多的来源。
     */
    public static void searchBest(String name, String episode, Consumer<Danmaku> found) {
        Call call = newCall(name, episode);
        if (call == null) return;
        String reqName = name == null ? "" : name.trim();
        String reqEpisode = episode == null ? "" : episode.trim();
        call.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    Danmaku best = pickBest(arrayFrom(response.body().string()), reqName, reqEpisode);
                    if (best != null) {
                        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "auto search pick name=%s source=%s", best.getName(), best.getSourceName());
                        App.post(() -> found.accept(best));
                    } else {
                        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "auto search no confident match for name=%s episode=%s", reqName, reqEpisode);
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static Danmaku pickBest(List<Danmaku> items, String name, String episode) {
        if (items == null || items.isEmpty() || TextUtils.isEmpty(name)) return null;
        String normName = normalizeTitle(name);
        if (TextUtils.isEmpty(normName)) return null;
        String reqEpNum = extractEpisodeNumber(episode);
        // 按来源分组
        Map<String, List<Danmaku>> groups = new LinkedHashMap<>();
        for (Danmaku item : items) {
            if (item == null || item.isEmpty()) continue;
            String source = item.getSourceName();
            if (!groups.containsKey(source)) groups.put(source, new ArrayList<>());
            groups.get(source).add(item);
        }
        // 先按标题+集数打分过滤，置信度不足的直接淘汰
        List<Danmaku> confident = new ArrayList<>();
        for (List<Danmaku> group : groups.values()) {
            for (Danmaku item : group) {
                if (scoreItem(item, normName, reqEpNum) >= CONFIDENCE_THRESHOLD) confident.add(item);
            }
        }
        if (confident.isEmpty()) return null;
        // 高置信度候选中，优先选择结果最多的来源
        Map<String, List<Danmaku>> bestGroups = new LinkedHashMap<>();
        for (Danmaku item : confident) {
            String source = item.getSourceName();
            if (!bestGroups.containsKey(source)) bestGroups.put(source, new ArrayList<>());
            bestGroups.get(source).add(item);
        }
        List<Danmaku> largest = null;
        for (List<Danmaku> group : bestGroups.values()) {
            if (largest == null || group.size() > largest.size()) largest = group;
        }
        if (largest == null || largest.isEmpty()) return null;
        // 组内按分数取最高
        Danmaku best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Danmaku item : largest) {
            int score = scoreItem(item, normName, reqEpNum);
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }
        return best;
    }

    private static int scoreItem(Danmaku item, String normName, String reqEpNum) {
        String itemTitle = normalizeTitle(extractItemTitle(item.getName()));
        int score = 0;
        if (TextUtils.isEmpty(itemTitle)) return Integer.MIN_VALUE;
        if (itemTitle.equals(normName)) {
            score += SCORE_TITLE_EXACT;
        } else if ((itemTitle.contains(normName) || normName.contains(itemTitle))
                && Math.min(itemTitle.length(), normName.length()) * 1.0 / Math.max(itemTitle.length(), normName.length()) >= 0.5) {
            score += SCORE_TITLE_CONTAINS;
        } else {
            return Integer.MIN_VALUE;
        }
        String itemEpNum = extractEpisodeNumber(item.getName());
        if (!TextUtils.isEmpty(reqEpNum)) {
            if (reqEpNum.equals(itemEpNum)) {
                score += SCORE_EPISODE_MATCH;
            } else if (TextUtils.isEmpty(itemEpNum)) {
                score += SCORE_EPISODE_MISSING;
            } else {
                score += SCORE_EPISODE_MISMATCH;
            }
        }
        return score;
    }

    private static String extractItemTitle(String itemName) {
        if (TextUtils.isEmpty(itemName)) return "";
        String s = itemName;
        int idx = s.indexOf('【');
        if (idx > 0) s = s.substring(0, idx);
        idx = s.indexOf('[');
        if (idx > 0) s = s.substring(0, idx);
        idx = s.toLowerCase().indexOf("from");
        if (idx > 0) s = s.substring(0, idx);
        return s.trim();
    }

    private static String normalizeTitle(String s) {
        if (TextUtils.isEmpty(s)) return "";
        s = Trans.t2s(false, s);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (Character.isWhitespace(c)) continue;
            if (c >= '!' && c <= '/') continue;
            if (c >= ':' && c <= '@') continue;
            if (c >= '[' && c <= '`') continue;
            if (c >= '{' && c <= '~') continue;
            if (c == '·' || c == '・' || c == '，' || c == '。' || c == '、' || c == '！' || c == '？'
                    || c == '：' || c == '；' || c == '"' || c == '"' || c == ''' || c == '''
                    || c == '（' || c == '）' || c == '《' || c == '》' || c == '「' || c == '」'
                    || c == '『' || c == '』' || c == '—' || c == '–' || c == '…') continue;
            sb.append(c);
        }
        return sb.toString();
    }

    private static String extractEpisodeNumber(String s) {
        if (TextUtils.isEmpty(s)) return "";
        Matcher m = EPISODE_NUMBER.matcher(s);
        if (m.find()) return String.valueOf(Integer.parseInt(m.group(1)));
        m = EPISODE_EP.matcher(s);
        if (m.find()) return String.valueOf(Integer.parseInt(m.group(1)));
        return "";
    }

    public static void cancel() {
        OkHttp.cancel(TAG);
    }
}
