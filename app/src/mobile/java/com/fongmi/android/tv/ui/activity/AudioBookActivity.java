package com.fongmi.android.tv.ui.activity;
import com.fongmi.android.tv.utils.ToastUtil;

import android.app.Activity;
import com.fongmi.android.tv.read.ReadDialogs;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;

import com.fongmi.android.tv.api.hk.HkHistoryRecorder;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.legado.LegadoAudio;
import com.fongmi.android.tv.legado.LegadoException;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.read.ReadingStore;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.tts.EdgeVoice;
import com.fongmi.android.tv.tts.EdgeVoiceCatalog;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.ui.minibar.ListenMiniBar;
import com.fongmi.android.tv.ui.minibar.ListenSession;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import com.fongmi.android.tv.R;

/**
 * 听书播放页（阶段二·甲最小面）：
 * type 1 音频源出口。复用现有链——PlaybackService（后台播放＋锁屏控制）＋
 * PlayerManager（倍速/进度）。断点续播/书架复用 ReadingStore。精修稿另案。
 */
public class AudioBookActivity extends Activity implements ServiceConnection {

    private static final float[] SPEEDS = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

    private LegadoAudio.AudioAlbum album;
    /** 静态仓 id（get 不删除，重建可复读；真关闭时按此 id remove）。 */
    private String sessionId;
    private PlaybackService mService;
    private boolean bound;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvTitle, tvChapter, tvPos, tvDur;
    private android.widget.LinearLayout tvSpeedVal, tvTimerVal;
    private android.widget.SeekBar seekBar;
    private android.widget.ImageView btnPlay;
    private android.widget.LinearLayout voiceRow;
    private boolean seeking;
    private int speedIndex = 1;
    private long sleepAt = 0;
    private int endedGuard = -1;
    private long resumeMs = 0;
    /** 用户点了关闭（真结束）；released=页面已销毁（后台可能还在播）。 */
    private boolean closed;
    private boolean released;
    /** 迷你条会话（与 TtsReadActivity 共用一套条）。 */
    private ListenSession miniSession;
    /** 进度保存失败提示的节流时间戳（349：失败要可感知，但不许刷屏）。 */
    private long lastProgressFailToastAt;
    private static final String MINI_ID = "audiobook";

    public static void start(Activity activity, String sessionId) {
        Intent i = new Intent(activity, AudioBookActivity.class);
        i.putExtra("session", sessionId);
        activity.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ReadingSkin.enableEdgeToEdge(this, true);
        sessionId = getIntent().getStringExtra("session");
        album = LegadoAudio.get(sessionId);
        if (album == null || album.chapters.isEmpty()) {
            ToastUtil.show(this, "专辑数据丢失", Toast.LENGTH_SHORT);
            finish();
            return;
        }
        buildUi();
        // 续播：同源同书的 AUDIO 记录带回位置。章序号只在「序号在表内且
        // 地址对得上」时才采信；位置则认「记录的章＝将要播的章」——书架
        // 续播是单章重建专辑（只有存档那一章），存档章序号是全表序号、
        // 必然越界，旧写法把位置连坐丢掉、每次从头重听（2026-10-30 实证）。
        // 海阔专辑不查 ReadingStore（2026-11-14 不留痕新规）：进度住所改
        // 海阔自家 History——按记录里的集址在全表里对位定章，position 即
        // 章内毫秒，采信口径与下方记录版一致（记录的章＝将要播的章才认）。
        if (!album.hkRule.isEmpty()) {
            History hh = hkHistoryRecord();
            if (hh != null) {
                int idx = -1;
                for (int i = 0; i < album.chapters.size(); i++) {
                    String u = album.chapters.get(i).url;
                    if (u != null && u.equals(hh.getEpisodeUrl())) {
                        idx = i;
                        break;
                    }
                }
                if (idx >= 0) album.index = idx;
                LegadoAudio.Chapter cur = album.current();
                if (cur != null && (album.chapters.size() == 1
                        || (hh.getEpisodeUrl() != null && hh.getEpisodeUrl().equals(cur.url)))) {
                    resumeMs = Math.max(0, hh.getPosition());
                }
            }
        } else {
            ReadingStore.Record r = findRecord();
            if (r != null) {
                if (r.chapterIndex >= 0 && r.chapterIndex < album.chapters.size()
                        && (r.chapterUrl == null || r.chapterUrl.isEmpty()
                            || r.chapterUrl.equals(album.chapters.get(r.chapterIndex).url))) {
                    album.index = r.chapterIndex;
                }
                LegadoAudio.Chapter cur = album.current();
                if (cur != null && (album.chapters.size() == 1
                        || (r.chapterUrl != null && r.chapterUrl.equals(cur.url)))) {
                    resumeMs = Math.max(0, r.position);
                }
            }
        }
        bindPlaybackService();
        setupMiniBar();
        handler.post(ticker);

        // 页面进出统一渐入渐出（与 BaseActivity 一致）
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
}

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
    }


    @Override
    protected void onPause() {
        super.onPause();
        try {
            ListenMiniBar.get(this).pagePaused(MINI_ID);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            ListenMiniBar.get(this).pageResumed(MINI_ID);
        } catch (Throwable ignored) {
        }
    }


    // ================= 迷你条（与 TtsReadActivity 共用一套条） =================

    private void setupMiniBar() {
        miniSession = new ListenSession(MINI_ID);
        miniSession.setKeyPrefix("audiobook:");
        miniSession.setMeta(album.title, miniSub(), album.coverUrl);
        miniSession.setReopenIntent(new Intent(this, AudioBookActivity.class));
        final LegadoAudio.AudioAlbum snap = album;
        final String sid = sessionId;
        miniSession.setControl(new ListenSession.Control() {
            @Override public boolean isPlaying() {
                return mService != null && player().isPlaying();
            }

            @Override public void toggle() {
                AudioBookActivity.this.toggle();
            }

            @Override public void next() {
                playDelta(1);
            }

            @Override public boolean canNext() {
                return true;
            }

            @Override public void prev() {
                playDelta(-1);
            }

            @Override public boolean canPrev() {
                return true;
            }

            @Override public void saveProgress() {
                AudioBookActivity.this.saveProgress();
            }

            @Override public void stopPlayback() {
                try {
                    if (mService != null) player().stop();
                } catch (Throwable ignored) {
                }
            }

            @Override public void onClosed() {
                unregisterNav();
                // 真关闭：静态仓显式删（照 TtsJob.remove 口径），不靠读取删
                LegadoAudio.remove(sessionId);
            }
        });
        miniSession.setDetached(new ListenSession.DetachedOps() {
            @Override public boolean isPlaying(PlaybackService svc) {
                try {
                    return svc.player().isPlaying()
                            && svc.player().getKey() != null
                            && svc.player().getKey().startsWith("audiobook:");
                } catch (Throwable t) {
                    return false;
                }
            }

            @Override public void toggle(PlaybackService svc) {
                try {
                    if (svc.player().isPlaying()) svc.player().pause();
                    else svc.player().play();
                } catch (Throwable t) {
                    // 迷你条上点了没反应是最坏的失败：必须让用户知道没切换成（349）
                    ToastUtil.show(svc, "播放切换失败，请打开听书页重试",
                            Toast.LENGTH_SHORT);
                }
            }

            @Override public void saveProgress(Context ctx, PlaybackService svc) {
                // 条上暂停落盘：名单（ReadingStore）与条读同一份状态，
                // 不许条这边暂停了、名单还记着旧位置/旧状态
                int pos = 0;
                try {
                    pos = (int) svc.player().getPosition();
                } catch (Throwable ignored) {
                }
                saveDetached(ctx.getApplicationContext(), snap, pos);
            }

            @Override public void close(Context ctx, PlaybackService svc) {
                // 真关闭：停播＋进度落盘（只用快照 POJO，不碰已销毁的页）
                int pos = 0;
                try {
                    pos = (int) svc.player().getPosition();
                } catch (Throwable ignored) {
                }
                saveDetached(ctx.getApplicationContext(), snap, pos);
                try {
                    svc.player().stop();
                } catch (Throwable ignored) {
                }
                try {
                    svc.setNavigationCallback(null, navKeyFor(snap));
                } catch (Throwable ignored) {
                }
                // 真关闭：静态仓显式删（快照 id，不碰已销毁的页）
                LegadoAudio.remove(sid);
            }

            @Override public void reopen(Context ctx) {
                try {
                    // get 不删除：原 id 还在就复用重开（照 TtsDetached.reopen 口径），
                    // 不再重复 put 攒新 id；真没了才按快照补存一份。
                    if (LegadoAudio.get(sid) != null) {
                        AudioBookActivity.start((Activity) ctx, sid);
                    } else {
                        AudioBookActivity.start((Activity) ctx, LegadoAudio.put(snap));
                    }
                } catch (Throwable t) {
                    // 点迷你条回页面没反应必须明示，并给一条能走通的路（349）
                    ToastUtil.show(ctx, "听书页打开失败，请从书架重新打开",
                            Toast.LENGTH_SHORT);
                }
            }
        });
        try {
            ListenMiniBar.get(this).register(miniSession, this);
        } catch (Throwable ignored) {
        }
    }

    private String miniSub() {
        LegadoAudio.Chapter c = album.current();
        String name = c == null ? "" : c.name;
        return "第 " + (album.index + 1) + " / " + album.chapters.size()
                + " 集" + (name.isEmpty() ? "" : " · " + name);
    }

    /** detached 真关闭时的落盘（静态，只用快照＋位置，不碰页实例）。 */
    static void saveDetached(Context ctx, LegadoAudio.AudioAlbum a, int pos) {
        // 海阔专辑进度只回海阔自家 History（2026-11-14 不留痕新规），不写
        // ReadingStore。此前在此给记录写 playFlag/ruleTitle 的两行随之收
        // 掉：海阔听书不再落本店记录，那两槽只剩阅读自家来源在用。
        if (a != null && !a.hkRule.isEmpty()) {
            writeHkHistoryProgress(a, pos);
            return;
        }
        try {
            LegadoAudio.Chapter c = a.current();
            if (c == null) return;
            ReadingStore.Record r = null;
            for (ReadingStore.Record x : ReadingStore.list()) {
                if ("AUDIO".equals(x.kind) && a.legadoKey.equals(x.legadoKey)
                        && a.title.equals(x.bookTitle)) {
                    r = x;
                    break;
                }
            }
            if (r == null) r = new ReadingStore.Record();
            r.kind = "AUDIO";
            r.sourceName = a.sourceName;
            r.bookTitle = a.title;
            r.coverUrl = a.coverUrl;
            r.chapterName = c.name;
            r.chapterIndex = a.index;
            r.chapterUrl = c.url;
            r.legadoKey = a.legadoKey;
            r.position = Math.max(0, pos);
            r.updatedAt = System.currentTimeMillis();
            ReadingStore.save(r);
        } catch (Throwable t) {
            // 落盘没成：旧记录还在盘上没被覆盖，但必须让用户知道这次没存上（349）。
            // 保存本身的失败另由 ReadingStore 的写失败提示承接，这里兜的是组装段。
            ToastUtil.show(ctx, "听书进度没存上，下次可能从旧位置开始",
                    Toast.LENGTH_LONG);
        }
    }

    // ================= UI（精修稿 audiobook：深色毛玻璃＋金点缀） =================

    private void buildUi() {
        android.widget.LinearLayout root =
                new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackground(ReadingSkin.pageBackground(this));

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        android.widget.LinearLayout body =
                new android.widget.LinearLayout(this);
        body.setOrientation(android.widget.LinearLayout.VERTICAL);
        sv.addView(body);
        root.addView(sv);

        // 顶栏：居中标题＋副；右上保留关闭（功能保留，稿未画）
        android.widget.FrameLayout topWrap =
                new android.widget.FrameLayout(this);
        body.addView(topWrap, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        android.widget.LinearLayout titles =
                new android.widget.LinearLayout(this);
        titles.setOrientation(android.widget.LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        titles.setPadding(dp(20), dp(16), dp(20), 0);
        topWrap.addView(titles, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        tvTitle = new TextView(this);
        tvTitle.setText(album.title);
        tvTitle.setTextSize(17);
        tvTitle.setTextColor(ReadingSkin.TEXT);
        tvTitle.getPaint().setFakeBoldText(true);
        tvTitle.setGravity(Gravity.CENTER);
        titles.addView(tvTitle);
        tvChapter = new TextView(this);
        tvChapter.setTextSize(13);
        tvChapter.setTextColor(ReadingSkin.SUB);
        tvChapter.setGravity(Gravity.CENTER);
        tvChapter.setPadding(0, dp(6), 0, 0);
        titles.addView(tvChapter);
        // 顶栏不设叉：走系统返回（离页转迷你条，关闭走迷你条三路径）
        ReadingSkin.applyTopInset(topWrap, this, 8);

        // 专辑大封面
        android.widget.FrameLayout coverWrap =
                new android.widget.FrameLayout(this);
        android.widget.LinearLayout.LayoutParams cwlp =
                new android.widget.LinearLayout.LayoutParams(
                        dp(190), dp(190));
        cwlp.gravity = Gravity.CENTER_HORIZONTAL;
        cwlp.topMargin = dp(18);
        body.addView(coverWrap, cwlp);
        android.widget.ImageView cover = new android.widget.ImageView(this);
        cover.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        cover.setScaleType(
                android.widget.ImageView.ScaleType.CENTER_CROP);
        ReadingSkin.roundClip(cover, this, 22);
        coverWrap.addView(cover);
        if (!android.text.TextUtils.isEmpty(album.coverUrl)) {
            try {
                ImgUtil.load(album.title, album.coverUrl, cover, true);
            } catch (Throwable ignored) {
            }
        } else {
            cover.setBackground(coverPlaceholder());
            TextView ct = new TextView(this);
            ct.setText(album.title);
            ct.setTextSize(22);
            ct.setTextColor(0xFFFFFFFF);
            ct.getPaint().setFakeBoldText(true);
            ct.setGravity(Gravity.CENTER);
            coverWrap.addView(ct, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // 金进度条＋两端时间
        android.widget.LinearLayout prog =
                new android.widget.LinearLayout(this);
        prog.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout.LayoutParams plp =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = dp(28);
        plp.rightMargin = dp(28);
        plp.topMargin = dp(22);
        body.addView(prog, plp);
        seekBar = new android.widget.SeekBar(this);
        ReadingSkin.styleGoldSeekBar(this, seekBar);
        prog.addView(seekBar, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        seekBar.setOnSeekBarChangeListener(
                new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            android.widget.SeekBar s, int p, boolean fromUser) {
                        if (fromUser && mService != null) player().seekTo(p);
                    }

                    @Override
                    public void onStartTrackingTouch(
                            android.widget.SeekBar s) {
                        seeking = true;
                    }

                    @Override
                    public void onStopTrackingTouch(
                            android.widget.SeekBar s) {
                        seeking = false;
                    }
                });
        android.widget.LinearLayout times =
                new android.widget.LinearLayout(this);
        times.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        times.setPadding(0, dp(6), 0, 0);
        tvPos = timeLabel();
        tvDur = timeLabel();
        tvDur.setGravity(Gravity.END);
        times.addView(tvPos, new android.widget.LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        times.addView(tvDur, new android.widget.LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        prog.addView(times);

        // 上一集 / 暂停 / 下一集
        android.widget.LinearLayout ctrls =
                new android.widget.LinearLayout(this);
        ctrls.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        ctrls.setGravity(Gravity.CENTER);
        ctrls.setPadding(0, dp(20), 0, 0);
        TextView prev = ctrlCircle("⏮", dp(60), false);
        prev.setOnClickListener(v -> playDelta(-1));
        btnPlay = new android.widget.ImageView(this);
        btnPlay.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                dp(88), dp(88)));
        btnPlay.setPadding(dp(26), dp(26), dp(26), dp(26));
        btnPlay.setBackground(ReadingSkin.goldCircle(this));
        btnPlay.setImageResource(R.drawable.ic_music_pause);
        btnPlay.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF0A2115));
        try {
            btnPlay.setElevation(dp(8));
        } catch (Throwable ignored) {
        }
        btnPlay.setOnClickListener(v -> toggle());
        TextView next = ctrlCircle("⏭", dp(60), false);
        next.setOnClickListener(v -> playDelta(1));
        android.widget.LinearLayout.LayoutParams gap =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.leftMargin = dp(18);
        gap.rightMargin = dp(18);
        ctrls.addView(prev);
        ctrls.addView(btnPlay, gap);
        ctrls.addView(next);
        body.addView(ctrls);

        // 倍速 / 定时 / 自动连播
        android.widget.LinearLayout opts =
                new android.widget.LinearLayout(this);
        opts.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        opts.setGravity(Gravity.CENTER);
        opts.setPadding(0, dp(18), 0, 0);
        tvSpeedVal = optCapsule("倍速", "1x");
        tvSpeedVal.setOnClickListener(v -> cycleSpeed());
        tvTimerVal = optCapsule("定时", "定时关");
        tvTimerVal.setOnClickListener(v -> pickTimer());
        opts.addView(tvSpeedVal);
        opts.addView(tvTimerVal);
        opts.addView(autoCapsule());
        body.addView(opts);

        // 音色：只给要朗读的专辑（文字章靠 TTS 念）。海阔电台这类真人录音
        // 专辑（hkRule 非空）章章都是真音频，音色挑选是死段——挂在电台页
        // 上只会让人以为进错了播放页（他 2026-10-31 录屏点名），整段不建。
        if (album.hkRule == null || album.hkRule.isEmpty()) {
        body.addView(ReadingSkin.sectionHeader(this, "音色",
                null, null));
        TextView vhint = new TextView(this);
        vhint.setText("神经人声 · 机械声不进这儿");
        vhint.setTextSize(12);
        vhint.setTextColor(ReadingSkin.FAINT);
        android.widget.LinearLayout.LayoutParams hlp =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        // 标题行右上角：单独一行右对齐
        vhint.setGravity(Gravity.END);
        hlp.leftMargin = dp(20);
        hlp.rightMargin = dp(20);
        hlp.bottomMargin = dp(8);
        body.addView(vhint, hlp);
        android.widget.HorizontalScrollView hsv =
                new android.widget.HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        voiceRow = new android.widget.LinearLayout(this);
        voiceRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        voiceRow.setPadding(dp(20), 0, dp(20), 0);
        hsv.addView(voiceRow);
        body.addView(hsv);
        buildVoiceCards();
        TextView vnote = new TextView(this);
        vnote.setText("挑顺耳的定成默认，听哪本都用它；另有方言音色在更多里。");
        vnote.setTextSize(12);
        vnote.setTextColor(ReadingSkin.FAINT);
        vnote.setGravity(Gravity.CENTER);
        vnote.setLineSpacing(dp(4), 1f);
        android.widget.LinearLayout.LayoutParams nlp =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.leftMargin = dp(20);
        nlp.rightMargin = dp(20);
        nlp.topMargin = dp(10);
        body.addView(vnote, nlp);
        }

        // 底部：选集 / 回书架（毛玻璃底；听书历史走书架在看列表，稿的历史按钮无对应功能位，未加）
        android.widget.LinearLayout sheet =
                new android.widget.LinearLayout(this);
        sheet.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        sheet.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable sheetBg =
                new android.graphics.drawable.GradientDrawable();
        sheetBg.setColor(0xB8121218);
        sheetBg.setCornerRadii(new float[]{
                dp(22), dp(22), dp(22), dp(22), 0, 0, 0, 0});
        sheetBg.setStroke(dp(2), 0x2EFFFFFF);
        sheet.setBackground(sheetBg);
        sheet.setPadding(0, dp(14), 0, dp(10));
        ReadingSkin.applyBottomInset(sheet, this, 10);
        root.addView(sheet, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        sheet.addView(sheetItem("☰", "选集", v -> showToc()));
        sheet.addView(sheetItem("↩", "回书架", v -> {
            ReadingShelfActivity.start(this);
            finish();
        }));
        // 海阔专辑专属收藏动作（2026-11-14 他亲定补齐产生面）：只借本面
        // 播放，收藏存海阔自家 HkFavoriteStore，不进阅读自家存档。
        if (album != null && album.hkRule != null && !album.hkRule.isEmpty()) {
            sheet.addView(sheetItem("☆", "收藏", v -> toggleAlbumFavorite()));
        }

        setContentView(root);
        refreshChapterLabel();
    }

    private android.graphics.drawable.Drawable coverPlaceholder() {
        return new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1D2A3A, 0xFF33506B, 0xFF7FA3C4});
    }

    private TextView timeLabel() {
        TextView t = new TextView(this);
        t.setText("00:00");
        t.setTextSize(12);
        t.setTextColor(ReadingSkin.SUB);
        return t;
    }

    /** 控制圆钮：gold=true 中央大金钮（深色字），否则深色圆钮。 */
    private TextView ctrlCircle(String s, int sizeDp, boolean gold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(gold ? 26 : 20);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                sizeDp, sizeDp));
        if (gold) {
            t.setTextColor(0xFF0A2115);
            t.setBackground(ReadingSkin.goldCircle(this));
            try {
                t.setElevation(dp(8));
            } catch (Throwable ignored) {
            }
        } else {
            t.setTextColor(ReadingSkin.TEXT);
            t.setBackground(ReadingSkin.darkCircle(this));
        }
        return t;
    }

    /** 选项胶囊：左灰 label＋右金 value。返回整颗（value 另存）。 */
    private android.widget.LinearLayout optCapsule(String label,
                                                   String value) {
        android.widget.LinearLayout box =
                new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(0x12FFFFFF);
        bg.setCornerRadius(dp(999));
        bg.setStroke(dp(2), 0x2EFFFFFF);
        box.setBackground(bg);
        int hp = dp(16), vp = dp(10);
        box.setPadding(hp, vp, hp, vp);
        android.widget.LinearLayout.LayoutParams blp =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = dp(6);
        blp.rightMargin = dp(6);
        box.setLayoutParams(blp);
        TextView l = new TextView(this);
        l.setText(label + " ");
        l.setTextSize(14);
        l.setTextColor(ReadingSkin.TEXT);
        box.addView(l);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(14);
        v.setTextColor(ReadingSkin.GOLD_TEXT);
        v.getPaint().setFakeBoldText(true);
        box.addView(v);
        box.setTag(v);
        box.setOnClickListener(null);
        return box;
    }

    /** 自动连播：代码里恒开（无开关逻辑），只做状态展示，不可点。 */
    private android.widget.LinearLayout autoCapsule() {
        android.widget.LinearLayout box = optCapsule("自动连播下一集", "开");
        box.setOnClickListener(null);
        box.setClickable(false);
        box.setFocusable(false);
        return box;
    }

    private void setOptValue(android.widget.LinearLayout capsule,
                             String value) {
        TextView v = (TextView) capsule.getTag();
        if (v != null) v.setText(value);
    }

    /** 底部三键其二：图标＋灰字。 */
    private android.widget.LinearLayout sheetItem(String icon,
                                                  String label,
                                                  View.OnClickListener onClick) {
        android.widget.LinearLayout item =
                new android.widget.LinearLayout(this);
        item.setOrientation(android.widget.LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(dp(28), dp(6), dp(28), dp(6));
        item.setOnClickListener(onClick);
        TextView i = new TextView(this);
        i.setText(icon);
        i.setTextSize(22);
        i.setTextColor(0xA6F4F2EC);
        i.setGravity(Gravity.CENTER);
        item.addView(i);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(11);
        l.setTextColor(ReadingSkin.SUB);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, dp(4), 0, 0);
        item.addView(l);
        return item;
    }

    /**
     * 海阔专辑的收藏切换：优先按历史键/站点+书名取海阔历史行统一构建
     * （与历史页长按同口径）；无行时按专辑自带的历史键拆解构建
     * （中间段＝vodId、vodId 命中章址即直达形、线路名随专辑）。
     */
    private void toggleAlbumFavorite() {
        com.fongmi.android.tv.bean.History row = null;
        try {
            row = HkHistoryRecorder.findByKey(album.hkHistoryKey);
            if (row == null) row = HkHistoryRecorder.findBySiteAndName(album.hkRule, album.title);
        } catch (Throwable ignored) {
        }
        com.fongmi.android.tv.api.hk.HkFavorite probe = row != null
                ? com.fongmi.android.tv.api.hk.HkFavoriteStore.fromHistory(row)
                : albumFavoriteProbe();
        if (probe == null) {
            ToastUtil.show(this, "暂时无法收藏", Toast.LENGTH_SHORT);
            return;
        }
        Boolean r = com.fongmi.android.tv.api.hk.HkFavoriteStore.toggle(probe);
        ToastUtil.show(this, r == null ? "收藏失败" : (r ? "已加入收藏" : "已取消收藏"),
                Toast.LENGTH_SHORT);
    }

    private com.fongmi.android.tv.api.hk.HkFavorite albumFavoriteProbe() {
        try {
            String key = album.hkHistoryKey == null ? "" : album.hkHistoryKey;
            String sym = com.fongmi.android.tv.db.AppDatabase.SYMBOL;
            int head = key.indexOf(sym);
            if (head < 0) return null;
            String rest = key.substring(head + sym.length());
            int tail = rest.lastIndexOf(sym);
            String vodId = tail >= 0 ? rest.substring(0, tail) : rest;
            if (vodId.isEmpty()) return null;
            com.fongmi.android.tv.api.hk.HkFavorite f =
                    new com.fongmi.android.tv.api.hk.HkFavorite();
            f.setRuleTitle(album.hkRule);
            f.setEpisodeUrl(vodId);
            boolean direct = false;
            for (LegadoAudio.Chapter ch : album.chapters) {
                if (vodId.equals(ch.url)) {
                    direct = true;
                    break;
                }
            }
            f.setDirect(direct);
            f.setFlag(album.hkLine == null ? "" : album.hkLine);
            f.setTitle(album.title == null ? "" : album.title);
            f.setPicUrl(album.coverUrl == null ? "" : album.coverUrl);
            f.setGroup(com.fongmi.android.tv.api.hk.HkFavoriteStore.DEFAULT_GROUP);
            f.setTime(System.currentTimeMillis());
            com.fongmi.android.tv.api.hk.HkRule rule =
                    com.fongmi.android.tv.api.hk.HkRuleManager.get().getInstalledRule(album.hkRule);
            if (rule != null) f.setRuleUrl(rule.getUrl());
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    // ================= 音色卡（真名单，不写死假数据） =================

    /**
     * 音色卡数据以 EdgeVoiceCatalog 真实名单为准：
     * 稿里 4 张（晓晓/云扬/云健/晓伊）都是内置真音色，按 id 取；
     * 取不到则顺延补齐。选中态＝当前默认音色。
     */
    private void buildVoiceCards() {
        voiceRow.removeAllViews();
        java.util.List<EdgeVoice> all = EdgeVoiceCatalog.builtIn();
        String[] want = {"zh-CN-XiaoxiaoNeural", "zh-CN-YunyangNeural",
                "zh-CN-YunjianNeural", "zh-CN-XiaoyiNeural"};
        java.util.List<EdgeVoice> show = new java.util.ArrayList<>();
        for (String id : want) {
            EdgeVoice v = EdgeVoiceCatalog.find(all, id);
            if (v != null) show.add(v);
        }
        for (EdgeVoice v : all) {
            if (show.size() >= 4) break;
            if (!show.contains(v) && v.locale.startsWith("zh-CN")
                    && !v.name.contains("（")) show.add(v);
        }
        String cur = EdgeVoiceCatalog.defaultVoice();
        for (EdgeVoice v : show) {
            voiceRow.addView(voiceCard(v, v.id.equals(cur)));
        }
    }

    private android.widget.LinearLayout voiceCard(EdgeVoice v,
                                                  boolean selected) {
        android.widget.LinearLayout card =
                new android.widget.LinearLayout(this);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(10));
        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(
                        dp(108), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(10);
        card.setLayoutParams(lp);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        if (selected) {
            bg.setColor(0x3845C08A);
            bg.setStroke(dp(2), ReadingSkin.GOLD);
        } else {
            bg.setColor(0x0DFFFFFF);
            bg.setStroke(dp(2), 0x24FFFFFF);
        }
        bg.setCornerRadius(dp(16));
        card.setBackground(bg);
        card.setOnClickListener(vv -> {
            EdgeVoiceCatalog.setDefaultVoice(v.id);
            buildVoiceCards();
        });
        TextView n = new TextView(this);
        n.setText(v.name);
        // 定高（卡片固定口径）：名字恒单行，长名截断不换行撑高
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        n.setTextSize(15);
        n.getPaint().setFakeBoldText(true);
        n.setTextColor(selected ? ReadingSkin.GOLD_TEXT : ReadingSkin.TEXT);
        card.addView(n);
        TextView d = new TextView(this);
        d.setText(v.portrait);
        // 定高（卡片固定口径）：画像恒占两行高（最短 4 字 1 行、最长 9 字 2 行），同排等高
        d.setMinLines(2);
        d.setMaxLines(2);
        d.setEllipsize(android.text.TextUtils.TruncateAt.END);
        d.setTextSize(11);
        d.setTextColor(ReadingSkin.SUB);
        d.setPadding(0, dp(6), 0, 0);
        d.setGravity(Gravity.CENTER);
        card.addView(d);
        // 画像自带「讲故事感」标签：数据驱动，不写死；无此标签的卡以 INVISIBLE
        // 占住同一行（卡片固定口径：四张音色卡同高，不随标签有无变化）
        TextView tag = new TextView(this);
        tag.setText("讲故事感");
        tag.setTextSize(10);
        tag.setTextColor(0xFF0F0D06);
        tag.getPaint().setFakeBoldText(true);
        tag.setPadding(dp(8), dp(4), dp(8), dp(4));
        android.graphics.drawable.GradientDrawable tb =
                new android.graphics.drawable.GradientDrawable();
        tb.setColor(ReadingSkin.GOLD);
        tb.setCornerRadius(dp(6));
        tag.setBackground(tb);
        boolean hasTag = v.portrait != null && v.portrait.contains("讲故事感");
        tag.setVisibility(hasTag ? View.VISIBLE : View.INVISIBLE);
        android.widget.LinearLayout.LayoutParams tlp =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(8);
        card.addView(tag, tlp);
        return card;
    }

    private int dp(int v) {
        return ReadingSkin.dp(this, v);
    }

    // ================= 服务绑定（复用 PlaybackService） =================

    private void bindPlaybackService() {
        if (bound) return;
        startService(new Intent(this, PlaybackService.class));
        bindService(new Intent(this, PlaybackService.class)
                .setAction(PlaybackService.LOCAL_BIND_ACTION), this, BIND_AUTO_CREATE);
        bound = true;
    }

    private PlayerManager player() {
        // 扫查待裁 24：与 PlaybackActivity 空安全口径对齐——服务未绑/已解绑时返 null，
        // 不再在访问器里裸解引用（现有调用点均先判 mService，行为不变；防新调用点踩）。
        return mService == null ? null : mService.player();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        mService = ((PlaybackService.LocalBinder) binder).getService();
        mService.setPlaybackForeground(true);
        mService.setNavigationCallback(navCallback, playbackKey());
        playChapter(album.index, resumeMs);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mService = null;
    }

    /** 导航回调的 key 单一出处：注册、页面清除、迷你条 detached 真关闭清除都用它，
     * 与服务侧「清除只有 key 与槽位一致才生效」的口径对齐，拼法不许各处手写一份。 */
    private static String navKeyFor(LegadoAudio.AudioAlbum a) {
        return "audiobook:" + a.legadoKey.hashCode() + ":" + a.title.hashCode();
    }

    private String playbackKey() {
        return navKeyFor(album);
    }

    private final PlaybackService.NavigationCallback navCallback = new PlaybackService.NavigationCallback() {
        @Override public void onPrev() { if (!released) playDelta(-1); }
        @Override public void onNext() { if (!released) playDelta(1); }
        @Override public void onStop() { if (!released) onClose(); }
    };

    /** 注销锁屏回调：只在听书真结束（用户关闭）时调；后台续播时保留。 */
    private void unregisterNav() {
        try {
            if (mService != null) mService.setNavigationCallback(null, playbackKey());
        } catch (Throwable ignored) {
        }
    }

    // ================= 播放 =================

    private void playDelta(int d) {
        int n = album.index + d;
        if (n < 0 || n >= album.chapters.size()) {
            ToastUtil.show(this, d > 0 ? "已经是最后一集" : "已经是第一集",
                    Toast.LENGTH_SHORT);
            return;
        }
        saveProgress();
        playChapter(n, 0);
    }

    private void playChapter(int index, long resume) {
        album.index = index;
        endedGuard = -1;
        refreshChapterLabel();
        new Thread(() -> {
            String url;
            try {
                LegadoAudio.Chapter c = album.current();
                if (!album.hkRule.isEmpty()) {
                    // 海阔电台专辑：直链直接播，其余走海阔解析（与音乐侧
                    // HkMusicSource.playUrl 同一口子）
                    if (com.fongmi.android.tv.api.hk.HkRouter.isVideoOrMusicUrl(c.url)) {
                        url = c.url;
                    } else {
                        com.fongmi.android.tv.bean.Result play =
                                com.fongmi.android.tv.api.SiteApi.playerContentParseOnly(
                                        com.fongmi.android.tv.api.hk.HkDetailBridge.siteKey(album.hkRule),
                                        album.hkLine, c.url);
                        url = play.getUrl() == null ? "" : play.getUrl().v();
                        if (url == null || url.isEmpty()) {
                            throw new com.fongmi.android.tv.legado.LegadoException("电台地址解析为空");
                        }
                    }
                } else {
                    com.fongmi.android.tv.legado.LegadoSource src =
                            com.fongmi.android.tv.legado.LegadoStore.get(album.legadoKey);
                    url = LegadoAudio.fetchAudioUrl(src, c.url, null);
                }
            } catch (Throwable t) {
                    runOnUiThread(() -> {
                    String m = t instanceof LegadoException ? t.getMessage() : "音频地址解析失败";
                    ToastUtil.show(AudioBookActivity.this, "源级错误：" + m,
                            Toast.LENGTH_LONG);
                });
                return;
            }
            final String audioUrl = url;
            runOnUiThread(() -> {
                if (released || mService == null) return;
                LegadoAudio.Chapter c = album.current();
                MediaMetadata meta = new MediaMetadata.Builder()
                        .setTitle(c.name)
                        .setArtist(album.title)
                        .setArtworkUri(album.coverUrl.isEmpty() ? null
                                : Uri.parse(album.coverUrl))
                        .build();
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/120.0 Mobile Safari/537.36");
                player().browse(PlaySpec.from(playbackKey(), audioUrl, headers, meta));
                if (resume > 0) player().seekTo(resume);
            });
        }).start();
    }



    private void toggle() {
        if (mService == null) return;
        if (player().isPlaying()) player().pause();
        else player().play();
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.length;
        float s = SPEEDS[speedIndex];
        if (mService != null) player().setSpeed(s);
        String label = String.format(Locale.US, "%.2f", s)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
        setOptValue(tvSpeedVal, label + "x");
        ToastUtil.show(this, "倍速 " + label + "x", Toast.LENGTH_SHORT);
    }

    private void pickTimer() {
        String[] items = {"15 分钟", "30 分钟", "60 分钟", "关闭定时"};
        ReadDialogs.options(this, "定时关闭", items, which -> {
            long[] ms = {15 * 60000L, 30 * 60000L, 60 * 60000L, 0};
            sleepAt = ms[which] == 0 ? 0 : System.currentTimeMillis() + ms[which];
            setOptValue(tvTimerVal, ms[which] == 0 ? "定时关"
                    : "剩" + items[which]);
            if (ms[which] == 0) ToastUtil.show(this, "已关闭定时",
                    Toast.LENGTH_SHORT);
        });
    }

    private void showToc() {
        String[] names = new String[album.chapters.size()];
        for (int i = 0; i < names.length; i++) names[i] = album.chapters.get(i).name;
        ReadDialogs.options(this, album.title + "（" + names.length + " 集）", names,
                which -> {
                    saveProgress();
                    playChapter(which, 0);
                });
    }

    private void refreshChapterLabel() {
        LegadoAudio.Chapter c = album.current();
        if (c != null && tvChapter != null) {
            tvChapter.setText("第 " + (album.index + 1) + " / "
                    + album.chapters.size() + " 集 · " + c.name);
            try {
                ListenMiniBar.get(this).updateMeta(MINI_ID, album.title,
                        miniSub());
            } catch (Throwable ignored) {
            }
        }
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            try {
                if (mService != null) {
                    Player p = player().getPlayer();
                    long pos = player().getPosition();
                    long dur = player().getDuration();
                    if (!seeking && dur > 0) {
                        seekBar.setMax((int) dur);
                        seekBar.setProgress((int) Math.min(pos, dur));
                    }
                    tvPos.setText(fmt(pos));
                    tvDur.setText(fmt(dur));
                    btnPlay.setImageResource(player().isPlaying()
                            ? R.drawable.ic_music_pause : R.drawable.ic_music_play);
                    // 迷你条：播放状态变化上报（恢复播放取消 10 秒计时）
                    try {
                        ListenMiniBar.get(AudioBookActivity.this)
                                .playingChanged(MINI_ID,
                                        player().isPlaying());
                    } catch (Throwable ignored) {
                    }
                    // 睡眠定时
                    if (sleepAt > 0 && System.currentTimeMillis() >= sleepAt) {
                        sleepAt = 0;
                        setOptValue(tvTimerVal, "定时关");
                        player().pause();
                        ToastUtil.show(AudioBookActivity.this, "定时到，已暂停",
                                Toast.LENGTH_SHORT);
                    }
                    // 本集播完自动下一集
                    if (p != null && p.getPlaybackState() == Player.STATE_ENDED
                            && endedGuard != album.index) {
                        endedGuard = album.index;
                        if (album.index + 1 < album.chapters.size()) {
                            saveProgress();
                            playChapter(album.index + 1, 0);
                        } else {
                            ToastUtil.show(AudioBookActivity.this, "专辑播完",
                                    Toast.LENGTH_SHORT);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            handler.postDelayed(this, 500);
        }
    };

    private String fmt(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    // ================= 进度/书架（复用 ReadingStore） =================

    /** 海阔专辑的历史记录定位：优先会话带入的历史键，缺键时按解析名＋书名扫描。 */
    private History hkHistoryRecord() {
        if (album == null) return null;
        History h = HkHistoryRecorder.findByKey(album.hkHistoryKey);
        if (h == null) h = HkHistoryRecorder.findBySiteAndName(album.hkRule, album.title);
        return h;
    }

    /** 海阔听书进度回写（存进度的唯一去处）：集序＝当前章、position＝章内毫秒。 */
    private static void writeHkHistoryProgress(LegadoAudio.AudioAlbum a, int pos) {
        try {
            if (a == null) return;
            LegadoAudio.Chapter c = a.current();
            if (c == null) return;
            String key = a.hkHistoryKey;
            if (key == null || key.isEmpty()) {
                History h = HkHistoryRecorder.findBySiteAndName(a.hkRule, a.title);
                if (h != null) key = h.getKey();
            }
            if (key == null || key.isEmpty()) return;
            HkHistoryRecorder.updateProgress(key, a.title, a.coverUrl,
                    a.hkLine, c.name, c.url, Math.max(0, pos));
        } catch (Throwable ignored) {
        }
    }

    private ReadingStore.Record findRecord() {
        try {
            for (ReadingStore.Record r : ReadingStore.list()) {
                if ("AUDIO".equals(r.kind) && album.legadoKey.equals(r.legadoKey)
                        && album.title.equals(r.bookTitle)) return r;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void saveProgress() {
        // 海阔听书不留痕（2026-11-14 他亲定）：进度回写海阔自家 History，
        // 不写 ReadingStore——听书书架从此看不见海阔的书。此前在此给记录
        // 写 playFlag（线路）与 ruleTitle（解析名）的两行随之收掉：海阔
        // 听书不再落本店记录，那两槽只剩阅读自家来源在用；线路与解析名
        // 本来就随专辑会话活在内存、海阔历史行上也有 vodFlag 一份。
        if (album != null && !album.hkRule.isEmpty()) {
            writeHkHistoryProgress(album,
                    mService == null ? 0 : (int) player().getPosition());
            return;
        }
        try {
            LegadoAudio.Chapter c = album.current();
            if (c == null) return;
            ReadingStore.Record r = findRecord();
            if (r == null) r = new ReadingStore.Record();
            r.kind = "AUDIO";
            r.sourceName = album.sourceName;
            r.bookTitle = album.title;
            r.coverUrl = album.coverUrl;
            r.chapterName = c.name;
            r.chapterIndex = album.index;
            r.chapterUrl = c.url;
            r.legadoKey = album.legadoKey;
            r.position = mService == null ? 0 : (int) player().getPosition();
            r.updatedAt = System.currentTimeMillis();
            ReadingStore.save(r);
        } catch (Throwable t) {
            // 进度没存上必须可感知（349）：旧记录仍在盘上、不会被半成品覆盖；
            // 本方法调用点多，提示节流 60 秒防刷屏。保存本身的失败另由
            // ReadingStore 的写失败提示承接，这里兜的是组装/取位置段。
            long now = System.currentTimeMillis();
            if (now - lastProgressFailToastAt >= 60_000L) {
                lastProgressFailToastAt = now;
                ToastUtil.show(this, "听书进度没存上，下次可能从旧位置开始",
                        Toast.LENGTH_LONG);
            }
        }
    }

    private void onClose() {
        closed = true;
        // 真关闭走迷你条策略：停播＋落盘＋注销（performClose 调页 control）
        try {
            ListenMiniBar.get(this).closeSession(MINI_ID);
        } catch (Throwable t) {
            LegadoAudio.remove(sessionId);
            saveProgress();
            try {
                if (mService != null) player().stop();
            } catch (Throwable ignored) {
            }
            unregisterNav();
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        released = true;
        saveProgress();
        handler.removeCallbacks(ticker);
        // 页销毁：控制权交给条（detached 兜底），会话不注销——后台续播时条就是主；
        // 真关闭（10秒/音乐页/点封面）才停播。不注销＝条还在，声音就有主。
        if (miniSession != null) {
            miniSession.setControl(null);
        }
        // 后台续播：页面销毁但播放继续，保留锁屏回调（服务 tryShutdown 会因此放行，
        // 且服务层 saveProgress 有 audiobook: 隔离，不会污染点播历史）。
        // 用户点了关闭才注销回调、放行服务回收。
        if (closed) unregisterNav();
        if (bound) {
            try {
                unbindService(this);
            } catch (Throwable ignored) {
            }
            bound = false;
        }
        mService = null;
        super.onDestroy();
    }
}
