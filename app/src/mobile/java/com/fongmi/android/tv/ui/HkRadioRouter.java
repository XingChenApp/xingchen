package com.fongmi.android.tv.ui;

import android.app.Activity;

import com.fongmi.android.tv.legado.LegadoAudio;
import com.fongmi.android.tv.ui.activity.AudioBookActivity;
import com.github.catvod.crawler.SpiderDebug;

/**
 * 海阔电台路由：跟音乐路由一样是「调用」别的播放器——电台归听书播放器。
 * 识别口径：规则名含 电台/广播 且不含 音乐/听歌/歌曲（唱片电台那类歌单
 * 仍归音乐播放器）。命中后把选集整表建成听书专辑（台名＝集名），
 * 章节地址在听书页内走海阔解析（AudioBookActivity 的 hkRule 分支）。
 * 此前电台规则被音乐关键词（PyMusicSource 的识别表含「电台」）吞进
 * 音乐播放器、或落到视频播放器，听书从没被调用（他 2026-10-28 点名）。
 */
public class HkRadioRouter {

    private HkRadioRouter() {
    }

    public static boolean looksRadio(String ruleTitle) {
        boolean hit = false;
        if (ruleTitle != null) {
            String n = ruleTitle.toLowerCase();
            hit = !n.contains("音乐") && !n.contains("听歌") && !n.contains("歌曲")
                    && (n.contains("电台") || n.contains("广播") || n.contains("radio"));
        }
        // 诊断（定根账可观测性缺口）：判定点此前全无日志，真机出问题无从定位
        // 是哪一关没问/没认。只记规则名与判定布尔。
        try {
            SpiderDebug.log("hk", "looksRadio rule=%s hit=%b", ruleTitle, hit);
        } catch (Throwable ignored) {
        }
        return hit;
    }

    /** 标题类文本的音乐否决词（与 looksRadio 同口径）。 */
    private static boolean hasMusicVeto(String title) {
        if (title == null) return false;
        String n = title.toLowerCase();
        return n.contains("音乐") || n.contains("听歌") || n.contains("歌曲");
    }

    /**
     * 标题类文本的电台特征：含「电台」/「广播」或以 FM 结尾，且不含音乐否决词。
     * 只认标题文本，不认地址后缀（后缀是媒体门的事，不在此处扩口径）。
     */
    private static boolean looksTitleRadio(String title) {
        if (title == null) return false;
        String n = title.trim().toLowerCase();
        if (n.isEmpty() || hasMusicVeto(n)) return false;
        return n.contains("电台") || n.contains("广播") || n.endsWith("fm");
    }

    /**
     * 认领信号（前置问询落点专用）：0=没中、1=规则名、2=页标题、3=条目标题、
     * 4=条目是「播放音频」钮。第四信号的来由（他 2026-10-31 录屏实证）：
     * 「一个APP」电台区的帖子点开是图文详情页，页内「播放音频」钮被点时——
     * 规则名（一个APP）、页标题（帖子标题）、条目标题（播放音频）三路全不中，
     * 音频被甩去源站网页播放页（空白页＋源站自己的频道条）。钮面文字就是
     * 播放意图本身：条目标题含「播放音频」即认（页标题带音乐否决词时不认，
     * 与条目信号同分寸）。分寸：页标题含音乐否决词＝整页是音乐区，
     * 条目信号一并否决，避免抢音乐页；规则名信号仍走 looksRadio 原口径、
     * 优先于标题信号。一般播放链（startHkPlay 内的 routeHk）不走此口，
     * 判定口径不扩散。
     */
    public static int claimSignal(String ruleTitle, String pageTitle, String itemTitle) {
        int sig = 0;
        if (looksRadio(ruleTitle)) {
            sig = 1;
        } else if (looksTitleRadio(pageTitle)) {
            sig = 2;
        } else if (!hasMusicVeto(pageTitle) && looksTitleRadio(itemTitle)) {
            sig = 3;
        } else if (!hasMusicVeto(pageTitle) && itemTitle != null
                && itemTitle.contains("播放音频")) {
            sig = 4;
        }
        // 诊断：记清命中哪路信号（规则名/页标题/条目标题/都没中），下次真机
        // 一次定位是哪路信号在起作用。
        try {
            SpiderDebug.log("hk", "claimSignal sig=%d rule=%s page=%s item=%s",
                    sig, ruleTitle, pageTitle, itemTitle);
        } catch (Throwable ignored) {
        }
        return sig;
    }

    /**
     * 海阔直接播放点电台条目：整表进听书专辑、从被点那台起播，返回 true；
     * 不是电台规则返回 false，调用方继续走音乐/视频链。
     * episodesJson 两种形态与 音乐改道.hkQueueFromJson 同口径
     * （扁平 [{name,url,pic}] / {flags:[{name,episodes:[...]}]}）。
     */
    public static boolean routeHk(Activity activity, String ruleTitle, String line,
                                  String episodeUrl, String episodeName,
                                  String name, String pic,
                                  String episodesJson, int selIdx) {
        if (activity == null || !looksRadio(ruleTitle)) return false;
        return routeHkGo(activity, ruleTitle, line, episodeUrl, episodeName,
                name, pic, episodesJson, selIdx);
    }

    /**
     * 前置问询落点专用入口：认领判定走 claimSignal（规则名＋页标题＋条目标题
     * 三路信号），命中后与 routeHk 共用同一建专辑本体 routeHkGo——专辑的
     * hkRule 仍是真规则名（听书页按它走海阔解析），标题只作认领信号、
     * 不冒充规则名。没命中返回 false，调用方原样落回既有分流。
     */
    public static boolean routeHkWithTitles(Activity activity, String ruleTitle,
                                            String pageTitle, String itemTitle,
                                            String line, String episodeUrl,
                                            String episodeName, String name,
                                            String pic, String episodesJson, int selIdx) {
        if (activity == null) return false;
        if (claimSignal(ruleTitle, pageTitle, itemTitle) == 0) return false;
        return routeHkGo(activity, ruleTitle, line, episodeUrl, episodeName,
                name, pic, episodesJson, selIdx);
    }

    private static boolean routeHkGo(Activity activity, String ruleTitle, String line,
                                     String episodeUrl, String episodeName,
                                     String name, String pic,
                                     String episodesJson, int selIdx) {
        try {
            if (episodeUrl == null || episodeUrl.isEmpty()) {
                // 诊断：名字认了却没播出去的窄口（候选四），此前静默 false 无痕
                try {
                    SpiderDebug.log("hk", "routeHk miss rule=%s reason=empty-url", ruleTitle);
                } catch (Throwable ignored) {
                }
                return false;
            }
            LegadoAudio.AudioAlbum album = new LegadoAudio.AudioAlbum();
            album.legadoKey = "hk:" + ruleTitle;
            album.hkRule = ruleTitle;
            album.hkLine = line == null ? "" : line;
            album.sourceName = ruleTitle;
            album.title = name == null || name.isEmpty() ? ruleTitle : name;
            album.coverUrl = pic == null ? "" : pic;
            boolean chaptersOk = fillChapters(album, line, episodesJson);
            if (!chaptersOk) {
                // 章节解析失败不再静默只剩部分/单集（残项处置 2026-11-14）：
                // 专辑照常以已得章节＋被点一集起播，但失败必须显形。
                com.fongmi.android.tv.utils.ToastUtil.show(activity,
                        "电台章节解析失败，章节可能不完整", android.widget.Toast.LENGTH_SHORT);
            }
            if (album.chapters.isEmpty()) {
                album.chapters.add(new LegadoAudio.Chapter(
                        episodeName == null || episodeName.isEmpty()
                                ? album.title : episodeName, episodeUrl));
            }
            int start = 0;
            boolean found = false;
            for (int i = 0; i < album.chapters.size(); i++) {
                if (episodeUrl.equals(album.chapters.get(i).url)) {
                    start = i;
                    found = true;
                    break;
                }
            }
            if (!found) start = Math.max(0, Math.min(selIdx, album.chapters.size() - 1));
            album.index = start;
            // 海阔历史（2026-11-14 他亲定：海阔历史必有数据）：直达形
            // （vodId＝被点集址，与收藏直达形同键）建记录并把键带入专
            // 辑——听书页据它恢复/回写进度；此前电台直达从不在海阔历
            // 史留行，历史页里找不到播过的电台。
            album.hkHistoryKey = com.fongmi.android.tv.api.hk.HkHistoryRecorder
                    .keyOf(ruleTitle, episodeUrl);
            com.fongmi.android.tv.api.hk.HkHistoryRecorder.recordOpened(
                    album.hkHistoryKey, album.title, album.coverUrl, album.hkLine,
                    album.chapters.get(start).name, album.chapters.get(start).url);
            AudioBookActivity.start(activity, LegadoAudio.put(album));
            try {
                SpiderDebug.log("hk", "routeHk claimed rule=%s chapters=%d start=%d",
                        ruleTitle, album.chapters.size(), start);
            } catch (Throwable ignored) {
            }
            return true;
        } catch (Throwable t) {
            try {
                SpiderDebug.log("hk", "routeHk miss rule=%s reason=error %s",
                        ruleTitle, t.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    /**
     * 海阔详情整表：收藏/历史走 startHk 打开视频详情页时，电台规则在详情
     * 数据到手后改道听书（与 音乐改道.routeHkDetail 同位、优先于它）。
     * 取第一条非空线路的选集建专辑，从第一台起播。
     */
    public static boolean routeHkDetail(Activity activity, String ruleTitle,
                                        com.fongmi.android.tv.bean.Vod vod) {
        try {
            if (activity == null || !looksRadio(ruleTitle)) return false;
            if (vod == null || vod.getFlags() == null) return false;
            LegadoAudio.AudioAlbum album = new LegadoAudio.AudioAlbum();
            album.legadoKey = "hk:" + ruleTitle;
            album.hkRule = ruleTitle;
            album.sourceName = ruleTitle;
            album.title = vod.getName() == null || vod.getName().isEmpty()
                    ? ruleTitle : vod.getName();
            album.coverUrl = vod.getPic() == null ? "" : vod.getPic();
            for (com.fongmi.android.tv.bean.Flag flag : vod.getFlags()) {
                if (flag == null || flag.getEpisodes() == null
                        || flag.getEpisodes().isEmpty()) continue;
                album.hkLine = flag.getFlag() == null ? "" : flag.getFlag();
                for (com.fongmi.android.tv.bean.Episode ep : flag.getEpisodes()) {
                    if (ep == null || ep.getUrl() == null || ep.getUrl().isEmpty()) continue;
                    album.chapters.add(new LegadoAudio.Chapter(
                            ep.getName() == null ? "" : ep.getName(), ep.getUrl()));
                }
                if (!album.chapters.isEmpty()) break;
            }
            if (album.chapters.isEmpty()) {
                // 诊断：详情整表被剔空的窄口（候选四），此前静默 false 无痕
                try {
                    SpiderDebug.log("hk", "routeHkDetail miss rule=%s reason=no-chapters", ruleTitle);
                } catch (Throwable ignored) {
                }
                return false;
            }
            album.index = 0;
            // 海阔历史（同 routeHkGo 口径）：详情形键＝本页 vodId（intent
            // 的 id，与 VideoActivity.getHistoryKey 同值），建记录带键。
            String detailId = activity.getIntent() == null ? ""
                    : activity.getIntent().getStringExtra("id");
            if (detailId != null && !detailId.isEmpty()) {
                album.hkHistoryKey = com.fongmi.android.tv.api.hk.HkHistoryRecorder
                        .keyOf(ruleTitle, detailId);
                com.fongmi.android.tv.api.hk.HkHistoryRecorder.recordOpened(
                        album.hkHistoryKey, album.title, album.coverUrl, album.hkLine,
                        album.chapters.get(0).name, album.chapters.get(0).url);
            }
            AudioBookActivity.start(activity, LegadoAudio.put(album));
            try {
                SpiderDebug.log("hk", "routeHkDetail claimed rule=%s chapters=%d",
                        ruleTitle, album.chapters.size());
            } catch (Throwable ignored) {
            }
            return true;
        } catch (Throwable t) {
            try {
                SpiderDebug.log("hk", "routeHkDetail miss rule=%s reason=error %s",
                        ruleTitle, t.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    /** 回 true＝无章节数据或解析完成；回 false＝解析失败（专辑可能只有部分章节）。 */
    private static boolean fillChapters(LegadoAudio.AudioAlbum album, String line,
                                     String episodesJson) {        if (episodesJson == null || episodesJson.isEmpty()) return true;
        try {
            String json = episodesJson.trim();
            if (json.startsWith("{")) {
                org.json.JSONObject root = new org.json.JSONObject(json);
                org.json.JSONArray flagArr = root.optJSONArray("flags");
                if (flagArr == null) return true;
                org.json.JSONObject chosen = null;
                org.json.JSONObject first = null;
                for (int fi = 0; fi < flagArr.length(); fi++) {
                    org.json.JSONObject fo = flagArr.optJSONObject(fi);
                    if (fo == null) continue;
                    org.json.JSONArray eps = fo.optJSONArray("episodes");
                    if (eps == null || eps.length() == 0) continue;
                    if (first == null) first = fo;
                    if (line != null && line.equals(fo.optString("name", ""))) {
                        chosen = fo;
                        break;
                    }
                }
                if (chosen == null) chosen = first;
                if (chosen == null) return true;
                if (album.hkLine.isEmpty()) {
                    album.hkLine = chosen.optString("name", "");
                }
                org.json.JSONArray eps = chosen.optJSONArray("episodes");
                for (int i = 0; i < eps.length(); i++) {
                    org.json.JSONObject o = eps.optJSONObject(i);
                    if (o == null) continue;
                    String url = o.optString("url", "");
                    if (url.isEmpty()) continue;
                    album.chapters.add(new LegadoAudio.Chapter(
                            o.optString("name", ""), url));
                }
                return true;
            }
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String url = o.optString("url", "");
                if (url.isEmpty()) continue;
                album.chapters.add(new LegadoAudio.Chapter(o.optString("name", ""), url));
            }
            return true;
        } catch (Throwable t) {
            SpiderDebug.log("hk", "fillChapters parse failed err=%s", t.toString());
            return false;
        }
    }
}
