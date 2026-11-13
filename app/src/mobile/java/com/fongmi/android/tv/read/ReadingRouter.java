package com.fongmi.android.tv.read;
import com.fongmi.android.tv.utils.ToastUtil;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

import com.fongmi.android.tv.ui.activity.AudioBookActivity;
import com.fongmi.android.tv.ui.activity.ComicReaderActivity;
import com.fongmi.android.tv.ui.activity.NovelReaderActivity;
import com.fongmi.android.tv.ui.activity.ReadingShelfActivity;
import com.fongmi.android.tv.ui.activity.TtsReadActivity;
import com.fongmi.android.tv.legado.LegadoAudio;
import com.fongmi.android.tv.tts.EdgeVoiceCatalog;
import com.fongmi.android.tv.tts.TtsJob;

/**
 * 阅读路由：判别层 → 阅读器 / 书架的统一入口。
 * 大数据走 ReadingSession 静态仓，Intent 只带 id。
 */
public class ReadingRouter {

    private ReadingRouter() {
    }

    /** 打开阅读器（漫画/小说按会话 kind 分流）。 */
    public static void openReading(Context context, ReadingSession session) {
        if (context == null || session == null) return;
        String id = ReadingSession.put(session);
        Class<?> cls = session.kind == ReadingSession.Kind.NOVEL
                ? NovelReaderActivity.class : ComicReaderActivity.class;
        Intent intent = new Intent(context, cls);
        intent.putExtra("session", id);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Throwable t) {
            ReadingSession.remove(id);
        }
    }

    /** 打开阅读器并指定起始页（漫画第几张图）。 */
    public static void openReadingWithStartPage(Context context, ReadingSession session, int startPage) {
        if (session == null) return;
        session.startPage = Math.max(0, startPage);
        openReading(context, session);
    }

    /** 打开书架。 */
    public static void openShelf(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, ReadingShelfActivity.class);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 按书架记录重建会话并打开。章节表缺失时单章模式（切章按钮收起），不崩。
     * 海阔：阅读器按规则/章节地址懒取；py 漫画：章节地址即图片串现场拆；
     * py 小说：用记录里的正文快照。
     */
    public static void openRecord(Context context, ReadingStore.Record r) {
        if (context == null || r == null || TextUtils.isEmpty(r.bookTitle)) return;
        // 听书/朗读记录走自己的续播路（书架 audioCard 同款口径），不许误进漫画阅读器；
        // 放本地书分支前面：本地书开神经朗读产出的 TTS 记录也该回 TtsReadActivity。
        if ("AUDIO".equals(r.kind)) {
            openAudioRecord(context, r);
            return;
        }
        if ("TTS".equals(r.kind)) {
            openTtsRecord(context, r);
            return;
        }
        // 本地书：按书 id 重建完整会话（章节表一次带入）；书没了给人话提示
        if ("local".equals(r.origin) && !TextUtils.isEmpty(r.localId)) {
            ReadingSession ls = com.fongmi.android.tv.localbook.LocalBookSession.fromRecord(r);
            if (ls == null) {
                ToastUtil.show(context, "这本本地书找不到了，请重新导入", Toast.LENGTH_SHORT);
                return;
            }
            int sp = com.fongmi.android.tv.localbook.LocalBookSession.startPageOf(r);
            if (sp > 0) openReadingWithStartPage(context, ls, sp);
            else openReading(context, ls);
            return;
        }
        ReadingSession s = new ReadingSession();
        s.kind = "NOVEL".equals(r.kind) ? ReadingSession.Kind.NOVEL : ReadingSession.Kind.COMIC;
        s.sourceName = r.sourceName == null ? "" : r.sourceName;
        s.bookTitle = r.bookTitle;
        s.coverUrl = r.coverUrl == null ? "" : r.coverUrl;
        s.chapterIndex = 0;
        ReadingSession.Chapter c = new ReadingSession.Chapter(
                r.chapterName == null ? "" : r.chapterName,
                r.chapterUrl == null ? "" : r.chapterUrl);
        s.chapters.add(c);
        s.chapterUrl = c.url;
        s.siteKey = r.siteKey == null ? "" : r.siteKey;
        s.legadoKey = r.legadoKey == null ? "" : r.legadoKey;
        // 海阔记录的 sourceName 即规则名；py 源不走规则（ruleTitle 置空防误查）
        s.ruleTitle = TextUtils.isEmpty(s.siteKey) ? s.sourceName : "";
        // Legado 书源走 legadoKey 懒取，不许误进海阔规则链
        if (!TextUtils.isEmpty(s.legadoKey)) s.ruleTitle = "";
        // py 小说：记录里带详情 id 的，后台拉全量章节表重建会话（能翻章/跳目录）；
        // 老记录（无 detailId）走原来的单章口径，不崩、进度不错位。
        if (s.kind == ReadingSession.Kind.NOVEL && !TextUtils.isEmpty(s.siteKey)
                && TextUtils.isEmpty(s.legadoKey) && TextUtils.isEmpty(s.ruleTitle)
                && !TextUtils.isEmpty(r.detailId)) {
            fetchNovelChapters(context, r, s);
            return;
        }
        // py 漫画：与小说同路，带详情 id 的后台重拉全量章节表重建（能切章）；
        // 老记录（无 detailId）走原来的单章口径，页号照旧恢复。
        if (s.kind == ReadingSession.Kind.COMIC && !TextUtils.isEmpty(s.siteKey)
                && TextUtils.isEmpty(s.legadoKey) && TextUtils.isEmpty(s.ruleTitle)
                && !TextUtils.isEmpty(r.detailId)) {
            fetchComicChapters(context, r, s);
            return;
        }
        // Legado：带 bookUrl 的按源目录重建全量章节表（bookInfo→toc，与追更检查
        // 同一条路）；源没了/目录拉空时回退单章口径。bookUrl 被旧版存进度抹掉的
        // 老记录为空，只能等下次从搜索打开时由 LegadoReading 建会话写回。
        if (!TextUtils.isEmpty(s.legadoKey) && !TextUtils.isEmpty(r.bookUrl)) {
            fetchLegadoChapters(context, r, s);
            return;
        }
        if (s.kind == ReadingSession.Kind.NOVEL && !TextUtils.isEmpty(r.content)) {
            s.text = r.content;
        }
        if (s.kind == ReadingSession.Kind.COMIC && r.position > 0) {
            openReadingWithStartPage(context, s, r.position);
        } else {
            openReading(context, s);
        }
    }

    /**
     * py 小说章节表重建：按记录的详情 id 重拉详情，取同线路的选集表当章节表，
     * 把记录定位回正确下标再开阅读器。拉失败/表为空时回退单章口径（fail-open）。
     */
    private static void fetchNovelChapters(Context context, ReadingStore.Record r, ReadingSession s) {
        ToastUtil.show(context, "正在拉取目录…", Toast.LENGTH_SHORT);
        final String detailId = r.detailId;
        final String siteKey = r.siteKey;
        final String playFlag = r.playFlag;
        new Thread(() -> {
            java.util.List<ReadingSession.Chapter> chapters = new java.util.ArrayList<>();
            try {
                com.fongmi.android.tv.bean.Result res =
                        com.fongmi.android.tv.api.SiteApi.detailContent(siteKey, detailId);
                chapters = pickDetailChapters(res, playFlag);
            } catch (Throwable ignored) {
            }
            final java.util.List<ReadingSession.Chapter> table = chapters;
            // context 未必是 Activity（语音"继续读"），用主线程 Handler 回调
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (table.size() > 1) {
                    int idx = com.fongmi.android.tv.read.NovelChapterTable.resolveIndex(
                            table, r.chapterUrl, r.chapterName, r.chapterIndex);
                    s.chapters = new java.util.ArrayList<>(table);
                    s.chapterIndex = idx;
                    s.chapterUrl = s.currentChapter().url;
                    s.detailId = detailId;
                    s.playFlag = playFlag == null ? "" : playFlag;
                    // 正文快照只属于第一章：定位到其它章时不许带着旧快照
                    if (idx == 0 && !TextUtils.isEmpty(r.content)) s.text = r.content;
                    else s.text = "";
                    // 毒缓存自愈：记录索引与实际定位不符的旧槽位，只可能是 bug 写错的
                    if (r.chapterIndex != idx) {
                        com.fongmi.android.tv.read.ReadingDownload.deleteNovelChapter(
                                context, novelBookKey(s), r.chapterIndex);
                    }
                    openReading(context, s);
                } else {
                    if (!TextUtils.isEmpty(r.content)) s.text = r.content;
                    ToastUtil.show(context, "目录拉取失败，只读当前章", Toast.LENGTH_SHORT);
                    openReading(context, s);
                }
            });
        }).start();
    }

    /**
     * py 漫画章节表重建：与小说同路（重拉详情取选集表），定位回原话再开阅读器，
     * 页号按记录恢复。拉失败/表为空时回退单章口径（fail-open），页号不丢。
     */
    private static void fetchComicChapters(Context context, ReadingStore.Record r, ReadingSession s) {
        ToastUtil.show(context, "正在拉取目录…", Toast.LENGTH_SHORT);
        final String detailId = r.detailId;
        final String siteKey = r.siteKey;
        final String playFlag = r.playFlag;
        new Thread(() -> {
            java.util.List<ReadingSession.Chapter> chapters = new java.util.ArrayList<>();
            try {
                com.fongmi.android.tv.bean.Result res =
                        com.fongmi.android.tv.api.SiteApi.detailContent(siteKey, detailId);
                chapters = pickDetailChapters(res, playFlag);
            } catch (Throwable ignored) {
            }
            final java.util.List<ReadingSession.Chapter> table = chapters;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (table.size() > 1) {
                    int idx = com.fongmi.android.tv.read.NovelChapterTable.resolveIndex(
                            table, r.chapterUrl, r.chapterName, r.chapterIndex);
                    s.chapters = new java.util.ArrayList<>(table);
                    s.chapterIndex = idx;
                    s.chapterUrl = s.currentChapter().url;
                    s.detailId = detailId;
                    s.playFlag = playFlag == null ? "" : playFlag;
                }
                if (r.position > 0) openReadingWithStartPage(context, s, r.position);
                else openReading(context, s);
            });
        }).start();
    }

    /**
     * Legado 章节表重建：bookInfo→toc（与 ReadingUpdateChecker 追更同一条路），
     * 定位回原章再开阅读器；小说章内进度由阅读器按下标/地址恢复，漫画页号按记录恢复。
     * 源已删/书地址失效/目录为空时回退单章口径（fail-open）。
     */
    private static void fetchLegadoChapters(Context context, ReadingStore.Record r, ReadingSession s) {
        ToastUtil.show(context, "正在拉取目录…", Toast.LENGTH_SHORT);
        new Thread(() -> {
            java.util.List<ReadingSession.Chapter> table = new java.util.ArrayList<>();
            try {
                com.fongmi.android.tv.legado.LegadoSource src =
                        com.fongmi.android.tv.legado.LegadoStore.get(r.legadoKey);
                if (src != null) {
                    com.fongmi.android.tv.legado.LegadoPipeline pipe =
                            new com.fongmi.android.tv.legado.LegadoPipeline(src);
                    com.fongmi.android.tv.legado.LegadoPipeline.BookInfo info =
                            pipe.bookInfo(r.bookUrl);
                    java.util.List<com.fongmi.android.tv.legado.LegadoPipeline.TocChapter> toc =
                            pipe.toc(info.tocUrl);
                    if (toc != null) {
                        for (com.fongmi.android.tv.legado.LegadoPipeline.TocChapter c : toc) {
                            table.add(new ReadingSession.Chapter(c.name, c.url));
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            final java.util.List<ReadingSession.Chapter> chapters = table;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (chapters.size() > 1) {
                    int idx = com.fongmi.android.tv.read.NovelChapterTable.resolveIndex(
                            chapters, r.chapterUrl, r.chapterName, r.chapterIndex);
                    s.chapters = new java.util.ArrayList<>(chapters);
                    s.chapterIndex = idx;
                    s.chapterUrl = s.currentChapter().url;
                }
                if (s.kind == ReadingSession.Kind.COMIC && r.position > 0) {
                    openReadingWithStartPage(context, s, r.position);
                } else {
                    openReading(context, s);
                }
            });
        }).start();
    }

    /** 从详情结果取章节表：优先记录时的线路，找不到用第一个有选集的线路。 */
    private static java.util.List<ReadingSession.Chapter> pickDetailChapters(
            com.fongmi.android.tv.bean.Result res, String playFlag) {
        java.util.List<ReadingSession.Chapter> out = new java.util.ArrayList<>();
        try {
            if (res == null) return out;
            com.fongmi.android.tv.bean.Vod vod = res.getVod();
            if (vod == null) return out;
            vod.setFlags();
            java.util.List<com.fongmi.android.tv.bean.Flag> flags = vod.getFlags();
            java.util.List<com.fongmi.android.tv.bean.Episode> eps = null;
            if (!TextUtils.isEmpty(playFlag)) {
                for (com.fongmi.android.tv.bean.Flag f : flags) {
                    if (f != null && playFlag.equals(f.getFlag())
                            && f.getEpisodes() != null && !f.getEpisodes().isEmpty()) {
                        eps = f.getEpisodes();
                        break;
                    }
                }
            }
            if (eps == null) {
                for (com.fongmi.android.tv.bean.Flag f : flags) {
                    if (f != null && f.getEpisodes() != null && !f.getEpisodes().isEmpty()) {
                        eps = f.getEpisodes();
                        break;
                    }
                }
            }
            if (eps != null) out = ReadingDetailDivert.toChapters(eps);
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 小说章节缓存的 bookKey 口径（与 NovelReaderActivity.bookKey 一致）。 */
    private static String novelBookKey(ReadingSession s) {
        return "NOVEL|" + s.sourceName + "|" + s.bookTitle;
    }

    /**
     * 听书续播：按记录重建单集专辑（书架 ReadingShelfActivity.openAudioRecord 同口径）。
     */
    public static void openAudioRecord(Context context, ReadingStore.Record r) {
        if (context == null || r == null) return;
        try {
            LegadoAudio.AudioAlbum a = new LegadoAudio.AudioAlbum();
            a.legadoKey = r.legadoKey == null ? "" : r.legadoKey;
            // 海阔电台记录：legadoKey 是 "hk:<规则名>" 身份键，专辑要带回 hkRule 才走得通解析；
            // 线路名存在 playFlag 槽（AUDIO 记录无人读此字段），一并带回 hkLine
            if (a.legadoKey.startsWith("hk:")) {
                a.hkRule = a.legadoKey.substring(3);
                a.hkLine = r.playFlag == null ? "" : r.playFlag;
            }
            a.sourceName = r.sourceName == null ? "" : r.sourceName;
            a.title = r.bookTitle == null ? "" : r.bookTitle;
            a.coverUrl = r.coverUrl == null ? "" : r.coverUrl;
            a.chapters.add(new LegadoAudio.Chapter(
                    r.chapterName == null ? "" : r.chapterName,
                    r.chapterUrl == null ? "" : r.chapterUrl));
            a.index = 0;
            String id = LegadoAudio.put(a);
            if (context instanceof Activity) {
                AudioBookActivity.start((Activity) context, id);
            } else {
                Intent intent = new Intent(context, AudioBookActivity.class);
                intent.putExtra("session", id);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(intent);
                } catch (Throwable t) {
                    LegadoAudio.remove(id);
                }
            }
        } catch (Throwable t) {
            ToastUtil.show(context, "打开失败", Toast.LENGTH_SHORT);
        }
    }

    /**
     * 朗读续播：按记录重建最小朗读任务（书架 ReadingShelfActivity.openTtsRecord 同口径）。
     */
    public static void openTtsRecord(Context context, ReadingStore.Record r) {
        if (context == null || r == null) return;
        try {
            TtsJob job = new TtsJob();
            job.bookTitle = r.bookTitle == null ? "" : r.bookTitle;
            job.coverUrl = r.coverUrl == null ? "" : r.coverUrl;
            job.sourceName = r.sourceName == null ? "" : r.sourceName;
            job.siteKey = r.siteKey == null ? "" : r.siteKey;
            job.legadoKey = r.legadoKey == null ? "" : r.legadoKey;
            job.ruleTitle = r.ruleTitle == null ? "" : r.ruleTitle;
            job.localId = r.localId == null ? "" : r.localId;
            job.inlineText = r.content == null ? "" : r.content;
            job.chapters.add(new ReadingSession.Chapter(
                    r.chapterName == null ? "" : r.chapterName,
                    r.chapterUrl == null ? "" : r.chapterUrl));
            job.startChapter = 0;
            job.startFraction = 0;
            job.voiceId = EdgeVoiceCatalog.defaultVoice();
            job.rate = 1.0f;
            String id = TtsJob.put(job);
            if (context instanceof Activity) {
                TtsReadActivity.start((Activity) context, id);
            } else {
                Intent intent = new Intent(context, TtsReadActivity.class);
                intent.putExtra("job", id);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(intent);
                } catch (Throwable t) {
                    TtsJob.remove(id);
                }
            }
        } catch (Throwable t) {
            ToastUtil.show(context, "打开失败", Toast.LENGTH_SHORT);
        }
    }

    /**
     * 继续读：最近一本。记录里带章节地址，重建最小会话；
     * 章节表缺失时阅读器按单章打开（切章按钮收起），不崩。
     */
    public static void continueReading(Context context) {
        if (context == null) return;
        ReadingStore.Record r = ReadingStore.latest();
        if (r == null || TextUtils.isEmpty(r.bookTitle)) {
            ToastUtil.show(context, "书架是空的，先去看一本吧", Toast.LENGTH_SHORT);
            openShelf(context);
            return;
        }
        openRecord(context, r);
    }
}
