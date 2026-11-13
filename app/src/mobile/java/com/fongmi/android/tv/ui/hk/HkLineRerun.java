package com.fongmi.android.tv.ui.hk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.fongmi.android.tv.api.hk.HkHttpParser;
import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.api.hk.HkParserKit;
import com.fongmi.android.tv.api.hk.HkRule;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 页函数型线路（橘汁型，普查 G3）的播放页重跑取集：线路是详情函数的页内状态，
 * 换线＝带新入参重跑详情函数（规则侧契约：线钮条目 url 是
 * {@code $("hiker://empty").rule(detailFn, "vid|线序")} 形态的 @rule= 子页面地址，
 * 点它在详情页里就是经 dealRule 组子规则开子页）。播放页没有详情页的宿主环境，
 * 这里照 dealRule 的组装法把线钮地址组回子规则，用 HkArticleListModel 无头跑一遍
 * 子页加载，再按与详情页 getChapters 同口径的过滤收集，交回播放页替换该线路的选集。
 *
 * 章节过滤（chapterCandidate 及其辅助判据）与 HkArticleFragment 的现行实现同口径
 * 复制一份在本文件：收集层若再改口径（正表化等），此处必须同步——两处分叉即新坑。
 * isDupTitleEntry 依赖「被点条目」概念、重跑页没有被点条目，不适用，未复制。
 *
 * 回调线程：bind/error 来自模型 IO 线程，原样透传；调用方（播放页）负责切主线程。
 */
public final class HkLineRerun {

    /** 未加载线路在播放页选集里的占位集地址前缀（带载荷引用号），绝不可进播放解析。 */
    public static final String MARKER = "hk-rerun://";

    private static final long TIMEOUT_MS = 60_000;

    public static boolean isMarker(String url) {
        return url != null && url.startsWith(MARKER);
    }

    /** 重跑产出的一集（name/url/pic，与交接载荷 episodes 同字段）。 */
    public static final class Ep {
        public final String name;
        public final String url;
        public final String pic;

        Ep(String name, String url, String pic) {
            this.name = name;
            this.url = url;
            this.pic = pic;
        }
    }

    public interface Callback {
        void onEpisodes(List<Ep> episodes);

        void onError(String message);
    }

    private HkLineRerun() {
    }

    /**
     * 重跑一发线钮子页并收集。
     *
     * @param ruleJson    详情页父规则 JSON（extraData 的 "rule"，子规则继承源）
     * @param pageUrl     线钮条目原始地址（@rule= 形态）
     * @param itemExtra   线钮条目 extra 原串（dealRule 口径：非空作子规则 params）
     * @param titlePrefix 集标题前缀（actTitle + "-"，与详情页 getChapters 同口径，可空）
     */
    public static void load(Context context, String ruleJson, String pageUrl, String itemExtra,
                            String titlePrefix, Callback callback) {
        if (callback == null) return;
        AtomicBoolean settled = new AtomicBoolean(false);
        Handler main = new Handler(Looper.getMainLooper());
        Runnable timeout = () -> {
            if (settled.compareAndSet(false, true)) callback.onError("线路加载超时，请重试");
        };
        main.postDelayed(timeout, TIMEOUT_MS);
        Callback guarded = new Callback() {
            @Override
            public void onEpisodes(List<Ep> episodes) {
                if (settled.compareAndSet(false, true)) {
                    main.removeCallbacks(timeout);
                    callback.onEpisodes(episodes);
                }
            }

            @Override
            public void onError(String message) {
                if (settled.compareAndSet(false, true)) {
                    main.removeCallbacks(timeout);
                    callback.onError(message);
                }
            }
        };
        try {
            HkRule parent = null;
            if (!TextUtils.isEmpty(ruleJson)) {
                try {
                    parent = new Gson().fromJson(ruleJson, HkRule.class);
                } catch (Exception ignored) {
                }
            }
            if (parent == null) {
                guarded.onError("线路加载失败：规则数据缺失");
                return;
            }
            if (TextUtils.isEmpty(pageUrl) || !pageUrl.contains("@rule=")) {
                guarded.onError("线路加载失败：线路入口缺失");
                return;
            }
            HkRule sub = buildSubRule(parent, pageUrl, itemExtra);
            if (sub == null) {
                guarded.onError("线路加载失败：线路入口无法解析");
                return;
            }
            HkDetailBridge bridge = new HkDetailBridge();
            // 子页内点集时的 codeAndHeader 口径：以子规则地址 +「被点集带懒函数」
            // 为入参重算（与 playUrlForChapter 的拼接条件配套）。
            String pageCah = bridge.getCodeAndHeader(
                    HkHttpParser.getUrlAppendUA(sub.getUrl(), sub.getUa()),
                    new String[]{"", "lazy"});
            String prefix = titlePrefix == null ? "" : titlePrefix;
            new HkArticleListModel()
                    .params(context, 1, true, sub, Boolean.TRUE, Boolean.FALSE)
                    .process("ARTICLE_LIST", new HkArticleListModel.Callback() {
                        @Override
                        public void bindArrayToView(String key, List<HkItem> list) {
                            List<Ep> episodes = collect(list, prefix, pageCah);
                            if (episodes.isEmpty()) {
                                guarded.onError("该线路没有可播的集，请换一条线路");
                            } else {
                                guarded.onEpisodes(episodes);
                            }
                        }

                        @Override
                        public void bindObjectToView(String key, HkItem item) {
                        }

                        @Override
                        public void error(String title, String msg, String code, Exception e) {
                            guarded.onError("线路加载失败" + (TextUtils.isEmpty(msg) ? "" : "：" + msg));
                        }

                        @Override
                        public void loading(boolean show) {
                        }
                    });
        } catch (Throwable t) {
            guarded.onError("线路加载失败：" + t.getClass().getSimpleName());
        }
    }

    /**
     * 照 HkArticleFragment.dealRule 组子规则：url 段（@rule= 前）不含 ";" 时拼
     * codeAndHeader（codeAndHeader 按点击线钮时的同口径从父规则地址推导）；
     * findRule 取 @rule= 尾段（6 段 ";" 形时第 6 段是 col_type、头部取前 5 段，
     * "==>" 尾巴原样回拼）；ua/group/preRule/title/lastChapterRule/pages 继承父规则，
     * 条目 extra 非空作 params。
     */
    private static HkRule buildSubRule(HkRule parent, String pageUrl, String itemExtra) {
        String[] ruleSplit = pageUrl.split("@rule=", 2);
        if (ruleSplit.length < 2) return null;
        String base = ruleSplit[0];
        String ruleStr = ruleSplit[1];
        String[] seg = ruleStr.split("==>");
        String head = seg[0];
        String colType = parent.getColType();
        if (!ruleStr.startsWith("js:")) {
            String[] semi = head.split(";");
            if (semi.length == 6) {
                colType = semi[5];
                head = HkParserKit.arrayToString(semi, 0, 5, ";");
            }
        }
        String tail = seg.length > 1 ? "==>" + HkParserKit.arrayToString(seg, 1, "==>") : "";
        HkDetailBridge bridge = new HkDetailBridge();
        String codeAndHeader = bridge.getCodeAndHeader(
                HkHttpParser.getUrlAppendUA(parent.getUrl(), parent.getUa()),
                new String[]{base});
        HkRule sub = new HkRule();
        sub.setUrl(base.contains(";") ? base : base + (codeAndHeader == null ? "" : codeAndHeader));
        sub.setUa(parent.getUa());
        sub.setFindRule(head + tail);
        sub.setColType(colType);
        sub.setGroup(parent.getGroup());
        sub.setPreRule(parent.getPreRule());
        sub.setTitle(parent.getTitle());
        sub.setLastChapterRule(parent.getLastChapterRule());
        sub.setPages(parent.getPages());
        if (!TextUtils.isEmpty(itemExtra)) sub.setParams(itemExtra);
        return sub;
    }

    /**
     * 子页条目表 → 选集：与详情页 getChapters 同口径收全表（无被点条目，全表都是
     * 候选；标题按 getResourceTitle 公式：inheritTitle 只取标题文本、否则前缀+标题
     * 文本；地址按 playUrlForChapter 公式把 codeAndHeader 拼回懒地址；图按
     * addChapterPic 口径：非空且非 "*" 才带）。
     */
    private static List<Ep> collect(List<HkItem> items, String titlePrefix, String pageCah) {
        List<Ep> out = new ArrayList<>();
        if (items == null) return out;
        boolean playList = false;
        String playListCls = "";
        for (HkItem it : items) {
            if (it != null && isPlayList(it)) {
                playList = true;
                playListCls = getPlayListCls(it);
                break;
            }
        }
        for (HkItem item : items) {
            if (item == null || !chapterCandidate(item, playList, playListCls)) continue;
            String titleText = HkStringUtil.getTitleText(item.getTitle());
            String title = "true".equals(item.getExtra("inheritTitle"))
                    ? titleText : titlePrefix + titleText;
            if (TextUtils.isEmpty(title)) title = item.getTitle() == null ? "" : item.getTitle();
            String url = item.getUrl() == null ? "" : item.getUrl();
            if (!TextUtils.isEmpty(pageCah) && url.contains("@lazyRule=") && !url.contains(pageCah)) {
                int lr = url.indexOf("@lazyRule=");
                url = url.substring(0, lr) + pageCah + url.substring(lr);
            }
            String pic = item.getPic();
            if (TextUtils.isEmpty(pic) || "*".equals(pic)) pic = "";
            out.add(new Ep(title, url, pic));
        }
        return out;
    }

    // —— 以下过滤判据与 HkArticleFragment 同口径复制（见类头注记，改口径须两处同步） ——

    private static boolean chapterCandidate(HkItem item, boolean playList, String playListCls) {
        if ("header".equals(item.getType())) return false;
        String url = item.getUrl();
        if (TextUtils.isEmpty(url)) return false;
        if (url.regionMatches(true, 0, "pics://", 0, "pics://".length())) return false;
        if (isEmptyPlaceholder(url)) return false;
        if (isHikerNavUrl(url)) return false;
        if (!url.contains("@lazyRule=")
                && (url.contains("ignoreVideo") || url.contains("ignoreMusic"))) return false;
        if (url.contains("@rule=")) return false;
        String pic = item.getPic();
        String urlBare = url.regionMatches(true, 0, "pics://", 0, "pics://".length())
                ? url.substring("pics://".length()) : url;
        if (!TextUtils.isEmpty(pic) && urlBare.equals(pic)) return false;
        if (playList && !isSamePlayList(playListCls, item)) return false;
        // 正表形态门（与 HkArticleFragment 现行同口径：直链族/@lazyRule/PlayData/P2P
        // 四形之外不收），被拦条目同样打日志点便于核样本。
        if (!isPlayableChapterForm(url)) {
            try {
                com.github.catvod.crawler.SpiderDebug.log("hk", "rerun chapter dropped by form-gate url=%s title=%s",
                        url, item.getTitle());
            } catch (Throwable ignored) {
            }
            return false;
        }
        return true;
    }

    /** 能播的集的地址正表，与 HkArticleFragment.isPlayableChapterForm 同口径复制。 */
    private static boolean isPlayableChapterForm(String url) {
        if (url.contains("@lazyRule=")) return true;
        String t = url.trim();
        if (t.startsWith("{") && t.contains("\"urls\"")) return true;
        String l = t.toLowerCase();
        if (com.fongmi.android.tv.api.hk.HkRouter.isP2pPlayLink(t)) return true;
        if (l.startsWith("js:")) return true;
        int sep = l.indexOf("://");
        if (sep < 0) return true;
        switch (l.substring(0, sep)) {
            case "http":
            case "https":
            case "rtsp":
            case "rtmp":
            case "rtmps":
            case "udp":
            case "mms":
            case "file":
            case "content":
            case "webrule":
            case "video":
            case "x5play":
                return true;
            default:
                return false;
        }
    }

    private static boolean isEmptyPlaceholder(String url) {
        String u = url.trim();
        if (u.contains("@lazyRule=")) return false;
        return u.equals("hiker://empty") || u.startsWith("hiker://empty#") || u.startsWith("hiker://empty?");
    }

    private static boolean isHikerNavUrl(String url) {
        if (url.contains("@lazyRule=")) return false;
        String u = url.trim().toLowerCase();
        return navPrefix(u, "hiker://home") || navPrefix(u, "hiker://page");
    }

    private static boolean navPrefix(String u, String prefix) {
        if (!u.startsWith(prefix)) return false;
        if (u.length() == prefix.length()) return true;
        char c = u.charAt(prefix.length());
        return c == '@' || c == '/' || c == '?' || c == '#';
    }

    private static boolean isPlayList(HkItem item) {
        if (item == null) return false;
        String cls = item.getExtra("cls");
        return !TextUtils.isEmpty(cls) && cls.contains("playlist");
    }

    private static String getPlayListCls(HkItem item) {
        if (item == null) return "";
        String cls = item.getExtra("cls");
        return cls == null ? "" : cls;
    }

    private static boolean isSamePlayList(String cls, HkItem item) {
        if (item == null) return false;
        return TextUtils.equals(cls, item.getExtra("cls"));
    }
}
