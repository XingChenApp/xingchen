package com.fongmi.android.tv.ui.activity;
import com.fongmi.android.tv.utils.ToastUtil;

import android.app.Activity;
import com.fongmi.android.tv.read.ReadDialogs;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;

import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.read.ReadingSession;
import com.fongmi.android.tv.read.ReadingStore;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.tts.EdgeTtsClient;
import com.fongmi.android.tv.tts.EdgeVoice;
import com.fongmi.android.tv.tts.EdgeVoiceCatalog;
import com.fongmi.android.tv.tts.TtsEngine;
import com.fongmi.android.tv.tts.TtsJob;
import com.fongmi.android.tv.ui.minibar.ListenMiniBar;
import com.fongmi.android.tv.ui.minibar.ListenSession;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.fongmi.android.tv.R;

/**
 * 智能朗读播放页（2026-10-28 照短剧智能朗读页定版重做）：
 * 小说阅读器「朗读」入口 → 按段合成 → 逐段播放；深青底、封面＋章名行、
 * 大字正文当前段高亮跟读、±15s 进度、功能行＋运输行、底部音色/边听边读双卡。
 * 供应商两家：Edge 神经音色优先、失败切系统 TTS 兜底（他 2026-10-27 令）。
 * 复用 PlaybackService（后台＋锁屏）＋ PlayerManager；进度记 ReadingStore（kind="TTS"，
 * 与 NOVEL 阅读进度分开记，互不冲）；与音乐播放器分家（只借底层服务）。
 */
public class TtsReadActivity extends Activity
        implements ServiceConnection, TtsEngine.Listener {

    private static final float[] SPEEDS = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private static final String[] TIMER_ITEMS = {"15 分钟", "30 分钟", "60 分钟", "关闭定时"};
    private static final String PREFS_TTS = "tts_read";
    private static final String KEY_ENGINE_MODE = "engine_mode";
    private static final int ACCENT = 0xFFFF9E57;
    private static final int FG = 0xFFF2F5F4;
    private static final int SUB = 0xFF9FB8BC;

    private String jobId;
    private TtsJob job;
    private ReadingSession rs;
    private TtsEngine engine;
    private PlaybackService mService;
    private boolean bound;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int ci;
    private int si;
    private int segCount;
    private int wantCi = -1;
    private int wantSi = -1;
    private File currentFile;
    private boolean inTrial;
    private int trialResumeCi;
    private int trialResumeSi;

    private EdgeVoice curVoice;
    private List<EdgeVoice> voices = new ArrayList<>();
    private int speedIndex = 1;
    private long sleepAt = 0;
    private int endedGuardCi = -1;
    private int endedGuardSi = -1;
    private boolean ttsDead;
    private boolean closed;
    private boolean released;
    private boolean seeking;

    private TextView tvChapter, tvSeg, tvPos, tvDur, tvSpeed, tvTimer, tvVoice;
    private SeekBar seekBar;
    private android.widget.ImageView btnPlay;
    // 正文跟读：整章段拼接显示、当前段高亮
    private android.widget.ScrollView bodyScroll;
    private TextView tvBody;
    private TextView tvCardChapter;
    private int bodyChapter = -1;
    private java.util.List<String> bodySegs;
    private int[] bodyStarts;
    private android.text.style.BackgroundColorSpan curSegSpan;
    /** 迷你条会话（与 AudioBookActivity 共用一套条）。 */
    private ListenSession miniSession;
    private static final String MINI_ID = "tts";

    public static void start(Activity activity, String jobId) {
        Intent i = new Intent(activity, TtsReadActivity.class);
        i.putExtra("job", jobId);
        activity.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ReadingSkin.enableEdgeToEdge(this, false);
        jobId = getIntent().getStringExtra("job");
        job = TtsJob.get(jobId);
        if (job == null || job.chapters.isEmpty()) {
            ToastUtil.show(this, "朗读任务已失效", Toast.LENGTH_SHORT);
            finish();
            return;
        }
        buildSession();
        // 断点：同书的 TTS 记录优先于本次传入位置
        ReadingStore.Record r = findRecord();
        if (r != null && r.chapterIndex >= 0 && r.chapterIndex < job.chapters.size()) {
            ci = r.chapterIndex;
            si = Math.max(0, r.segIndex);
        } else {
            ci = Math.max(0, Math.min(job.startChapter, job.chapters.size() - 1));
            si = 0;
        }
        buildUi();
        loadVoices();
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

    // ================= 迷你条（与 AudioBookActivity 共用一套条） =================

    private void setupMiniBar() {
        miniSession = new ListenSession(MINI_ID);
        miniSession.setKeyPrefix("tts:");
        miniSession.setMeta(job.bookTitle, miniSub(), job.coverUrl);
        miniSession.setReopenIntent(new Intent(this, TtsReadActivity.class)
                .putExtra("job", jobId));
        miniSession.setControl(new ListenSession.Control() {
            @Override public boolean isPlaying() {
                return mService != null && player().isPlaying();
            }

            @Override public void toggle() {
                TtsReadActivity.this.toggle();
            }

            @Override public void next() {
                jumpChapter(1);
            }

            @Override public boolean canNext() {
                return true;
            }

            @Override public void prev() {
                jumpChapter(-1);
            }

            @Override public boolean canPrev() {
                return true;
            }

            @Override public void saveProgress() {
                TtsReadActivity.this.saveProgress();
            }

            @Override public void stopPlayback() {
                try {
                    if (mService != null) player().stop();
                } catch (Throwable ignored) {
                }
            }

            @Override public void onClosed() {
                unregisterNav();
                TtsJob.remove(jobId);
                try {
                    if (engine != null) engine.shutdown();
                } catch (Throwable ignored) {
                }
            }
        });
        try {
            ListenMiniBar.get(this).register(miniSession, this);
        } catch (Throwable ignored) {
        }
    }

    private String miniSub() {
        try {
            ReadingSession.Chapter c = job.chapters.get(
                    Math.min(ci, job.chapters.size() - 1));
            return c.name + " · 第" + (si + 1) + "段";
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 页销毁后兜底（静态类，不持有页实例，防泄漏）：
     * 真关闭＝停播＋进度落盘；点条身＝用 jobId 重开页。
     */
    static class TtsDetached implements ListenSession.DetachedOps {
        final String jobId, bookTitle, sourceName, coverUrl, siteKey,
                legadoKey, ruleTitle, localId, chapterName, chapterUrl, navKey;
        final int ci, si;

        TtsDetached(String jobId, TtsJob job, int ci, int si,
                    String navKey) {
            this.jobId = jobId;
            this.bookTitle = job.bookTitle;
            this.sourceName = job.sourceName;
            this.coverUrl = job.coverUrl;
            this.siteKey = job.siteKey;
            this.legadoKey = job.legadoKey;
            this.ruleTitle = job.ruleTitle;
            this.localId = job.localId;
            this.navKey = navKey;
            this.ci = ci;
            this.si = si;
            String cn = "", cu = "";
            try {
                ReadingSession.Chapter c = job.chapters.get(
                        Math.min(ci, job.chapters.size() - 1));
                cn = c.name;
                cu = c.url;
            } catch (Throwable t) {
                cn = "";
                cu = "";
            }
            this.chapterName = cn;
            this.chapterUrl = cu;
        }

        private boolean keyMatch(PlaybackService svc) {
            try {
                String k = svc.player().getKey();
                return k != null && k.startsWith("tts:");
            } catch (Throwable t) {
                return false;
            }
        }

        @Override public boolean isPlaying(PlaybackService svc) {
            try {
                return keyMatch(svc) && svc.player().isPlaying();
            } catch (Throwable t) {
                return false;
            }
        }

        @Override public void toggle(PlaybackService svc) {
            try {
                if (svc.player().isPlaying()) svc.player().pause();
                else svc.player().play();
            } catch (Throwable t) {
                // 页已销毁、条是唯一主人：记名留痕之外照 detached reopen 同形
                // 用服务上下文弹提示，不许点条无反应又查不到原因。
                Log.w("TtsReadActivity", "detached toggle failed", t);
                ToastUtil.show(svc, "播放控制失败，请重试", Toast.LENGTH_SHORT);
            }
        }

        @Override public void close(Context ctx, PlaybackService svc) {
            int pos = 0;
            try {
                pos = (int) svc.player().getPosition();
            } catch (Throwable ignored) {
            }
            try {
                ReadingStore.Record r = null;
                for (ReadingStore.Record x : ReadingStore.list()) {
                    if ("TTS".equals(x.kind)
                            && sourceName.equals(x.sourceName)
                            && bookTitle.equals(x.bookTitle)) {
                        r = x;
                        break;
                    }
                }
                if (r == null) r = new ReadingStore.Record();
                r.kind = "TTS";
                r.sourceName = sourceName;
                r.bookTitle = bookTitle;
                r.coverUrl = coverUrl;
                r.chapterName = chapterName;
                r.chapterIndex = ci;
                r.segIndex = si;
                r.chapterUrl = chapterUrl;
                r.siteKey = siteKey;
                r.legadoKey = legadoKey;
                r.ruleTitle = ruleTitle;
                r.localId = localId;
                r.origin = TextUtils.isEmpty(localId) ? "" : "local";
                r.position = Math.max(0, pos);
                r.updatedAt = System.currentTimeMillis();
                ReadingStore.save(r);
            } catch (Throwable t) {
                // 落盘失败：save 未完成则旧记录原样保留、不被覆盖；失败记名并提示，
                // 不许续听位置静默丢了还查不到。
                Log.w("TtsReadActivity", "detached close saveProgress failed", t);
                ToastUtil.show(ctx, "续听进度保存失败，下次将从上次位置继续", Toast.LENGTH_SHORT);
            }
            try {
                svc.player().stop();
            } catch (Throwable ignored) {
            }
            try {
                svc.setNavigationCallback(null, navKey);
            } catch (Throwable ignored) {
            }
            TtsJob.remove(jobId);
        }

        @Override public void reopen(Context ctx) {
            try {
                if (TtsJob.get(jobId) != null) {
                    TtsReadActivity.start((Activity) ctx, jobId);
                } else {
                    // 任务已不在：接不上必须显形（与 onCreate「朗读任务已失效」
                    // 同文案），不许点条静默无反应装作回去了。
                    Log.w("TtsReadActivity", "detached reopen: job gone jobId=" + jobId);
                    ToastUtil.show(ctx, "朗读任务已失效", Toast.LENGTH_SHORT);
                }
            } catch (Throwable t) {
                Log.w("TtsReadActivity", "detached reopen failed", t);
                try {
                    ToastUtil.show(ctx, "朗读任务已失效", Toast.LENGTH_SHORT);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void buildSession() {
        rs = new ReadingSession();
        rs.kind = ReadingSession.Kind.NOVEL;
        rs.sourceName = job.sourceName;
        rs.bookTitle = job.bookTitle;
        rs.coverUrl = job.coverUrl;
        rs.siteKey = job.siteKey;
        rs.legadoKey = job.legadoKey;
        rs.ruleTitle = job.ruleTitle;
        rs.localId = job.localId;
        rs.text = job.inlineText;
        rs.chapters.addAll(job.chapters);
    }

    // ================= UI（2026-10-28 照短剧智能朗读页重做） =================

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{0xFF0C2B33, 0xFF11404A});
        root.setBackground(bg);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶栏：收起＋标题＋更多
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(40), dp(8), dp(4));
        TextView collapse = topButton("⌄");
        collapse.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText("智能朗读");
        title.setTextSize(17);
        title.setTextColor(FG);
        title.getPaint().setFakeBoldText(true);
        TextView more = topButton("⋮");
        more.setOnClickListener(v -> showMore());
        top.addView(collapse);
        top.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(more);
        col.addView(top);

        // 封面＋章名行
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20), dp(10), dp(20), dp(6));
        FrameLayout coverBox = new FrameLayout(this);
        coverBox.setLayoutParams(new LinearLayout.LayoutParams(dp(54), dp(72)));
        android.widget.ImageView cover = new android.widget.ImageView(this);
        cover.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(0x26FFFFFF);
        coverBox.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (!TextUtils.isEmpty(job.coverUrl)) {
            com.fongmi.android.tv.utils.ImgUtil.load(job.bookTitle, job.coverUrl, cover, true);
        }
        head.addView(coverBox);
        LinearLayout headText = new LinearLayout(this);
        headText.setOrientation(LinearLayout.VERTICAL);
        headText.setPadding(dp(12), 0, 0, 0);
        tvChapter = new TextView(this);
        tvChapter.setTextSize(16.5f);
        tvChapter.setTextColor(FG);
        tvChapter.getPaint().setFakeBoldText(true);
        tvChapter.setMaxLines(2);
        tvChapter.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView tvBook = new TextView(this);
        tvBook.setText(job.bookTitle + " · " + job.sourceName);
        tvBook.setTextSize(12.5f);
        tvBook.setTextColor(SUB);
        tvBook.setPadding(0, dp(4), 0, 0);
        headText.addView(tvChapter);
        headText.addView(tvBook);
        head.addView(headText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        col.addView(head);

        // 正文跟读区
        bodyScroll = new android.widget.ScrollView(this);
        bodyScroll.setPadding(dp(20), dp(8), dp(20), dp(12));
        tvBody = new TextView(this);
        tvBody.setTextSize(17);
        tvBody.setTextColor(0xE8F2F5F4);
        tvBody.setLineSpacing(0, 1.55f);
        bodyScroll.addView(tvBody);
        col.addView(bodyScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // 底部面板
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xFF0F3540);
        panel.setPadding(dp(16), dp(10), dp(16), dp(18));
        // 底部让位：基础底距 18dp 上再叠系统导航区真实高度（与漫画/小说/听书页同款 ReadingSkin.applyBottomInset），防手势横杠压住面板底行、三键导航下底行点击被吃
        ReadingSkin.applyBottomInset(panel, this, 18);
        col.addView(panel);

        LinearLayout seekRow = new LinearLayout(this);
        seekRow.setOrientation(LinearLayout.HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView back15 = seekJumpButton("-15s");
        back15.setOnClickListener(v -> seekBy(-15000));
        TextView fwd15 = seekJumpButton("+15s");
        fwd15.setOnClickListener(v -> seekBy(15000));
        seekBar = new SeekBar(this);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && mService != null) player().seekTo(p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar s) { seeking = false; }
        });
        seekRow.addView(back15);
        seekRow.addView(seekBar, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        seekRow.addView(fwd15);
        panel.addView(seekRow);

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        tvPos = timeLabel();
        tvSeg = timeLabel();
        tvSeg.setGravity(Gravity.CENTER);
        tvDur = timeLabel();
        tvDur.setGravity(Gravity.END);
        times.addView(tvPos, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        times.addView(tvSeg, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        times.addView(tvDur, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        panel.addView(times);

        // 功能行：语速/音色/定时/目录
        LinearLayout opts = new LinearLayout(this);
        opts.setOrientation(LinearLayout.HORIZONTAL);
        opts.setPadding(0, dp(8), 0, 0);
        tvSpeed = optButton("语速 1.0x");
        tvSpeed.setOnClickListener(v -> cycleSpeed());
        tvVoice = optButton("音色");
        tvVoice.setOnClickListener(v -> pickVoice());
        tvTimer = optButton("定时关");
        tvTimer.setOnClickListener(v -> pickTimer());
        TextView list = optButton("目录");
        list.setOnClickListener(v -> showToc());
        opts.addView(tvSpeed, optLp());
        opts.addView(tvVoice, optLp());
        opts.addView(tvTimer, optLp());
        opts.addView(list, optLp());
        panel.addView(opts);

        // 运输行：上一章／大播放钮／下一章
        LinearLayout ctrls = new LinearLayout(this);
        ctrls.setOrientation(LinearLayout.HORIZONTAL);
        ctrls.setGravity(Gravity.CENTER);
        ctrls.setPadding(0, dp(10), 0, 0);
        TextView prev = ctrlButton("⏮");
        prev.setOnClickListener(v -> jumpChapter(-1));
        TextView next = ctrlButton("⏭");
        next.setOnClickListener(v -> jumpChapter(1));
        btnPlay = new android.widget.ImageView(this);
        btnPlay.setPadding(dp(18), dp(18), dp(18), dp(18));
        btnPlay.setImageResource(R.drawable.ic_music_pause);
        btnPlay.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF0C2B33));
        android.graphics.drawable.GradientDrawable disc =
                new android.graphics.drawable.GradientDrawable();
        disc.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        disc.setColor(0xFFF2F5F4);
        btnPlay.setBackground(disc);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(68), dp(68));
        blp.leftMargin = dp(28);
        blp.rightMargin = dp(28);
        btnPlay.setOnClickListener(v -> toggle());
        ctrls.addView(prev);
        ctrls.addView(btnPlay, blp);
        ctrls.addView(next);
        panel.addView(ctrls);

        // 底部双卡：音色设置／边听边读
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setPadding(0, dp(14), 0, 0);
        LinearLayout cardA = cardBox("音色设置");
        TextView voiceVal = new TextView(this);
        voiceVal.setTextSize(13);
        voiceVal.setTextColor(FG);
        voiceVal.setMaxLines(1);
        voiceVal.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cardA.addView(voiceVal);
        tvVoiceCard = voiceVal;
        cardA.setOnClickListener(v -> pickVoice());
        LinearLayout cardB = cardBox("边听边读");
        tvCardChapter = new TextView(this);
        tvCardChapter.setTextSize(13);
        tvCardChapter.setTextColor(FG);
        tvCardChapter.setMaxLines(1);
        tvCardChapter.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cardB.addView(tvCardChapter);
        cardB.setOnClickListener(v -> openReaderHere());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        clp2.leftMargin = dp(10);
        cards.addView(cardA, clp);
        cards.addView(cardB, clp2);
        panel.addView(cards);

        setContentView(root);
        refreshLabels();
    }

    private TextView tvVoiceCard;

    private LinearLayout.LayoutParams optLp() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private LinearLayout cardBox(String label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setColor(0x1FFFFFFF);
        g.setCornerRadius(dp(14));
        c.setBackground(g);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(11);
        l.setTextColor(SUB);
        l.setPadding(0, 0, 0, dp(3));
        c.addView(l);
        return c;
    }

    private TextView topButton(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(22);
        t.setTextColor(FG);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return t;
    }

    private TextView seekJumpButton(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(SUB);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(6), dp(6), dp(6));
        return t;
    }

    private TextView timeLabel() {
        TextView t = new TextView(this);
        t.setText("00:00");
        t.setTextSize(11);
        t.setTextColor(SUB);
        return t;
    }

    private TextView ctrlButton(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(26);
        t.setTextColor(FG);
        t.setPadding(dp(18), dp(8), dp(18), dp(8));
        return t;
    }

    private TextView optButton(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13.5f);
        t.setTextColor(FG);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(4), dp(8), dp(4), dp(8));
        return t;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void refreshLabels() {
        if (tvChapter == null) return;
        ReadingSession.Chapter c = job.chapters.get(Math.min(ci, job.chapters.size() - 1));
        tvChapter.setText(c.name);
        tvSeg.setText("第 " + (si + 1) + " 段" + (segCount > 0 ? " / 共 " + segCount + " 段" : ""));
        boolean sys = engine != null && engine.usingSystem();
        String voiceName = sys ? "系统语音" : (curVoice == null ? "音色" : curVoice.name);
        tvVoice.setText("音色 · " + voiceName);
        if (tvVoiceCard != null) tvVoiceCard.setText(voiceName);
        if (tvCardChapter != null) tvCardChapter.setText(c.name);
        try {
            ListenMiniBar.get(this).updateMeta(MINI_ID, job.bookTitle,
                    miniSub());
        } catch (Throwable ignored) {
        }
    }

    // ================= 引擎选择 / 更多 =================

    private int engineModePref() {
        return getSharedPreferences(PREFS_TTS, MODE_PRIVATE)
                .getInt(KEY_ENGINE_MODE, TtsEngine.MODE_AUTO);
    }

    private String engineModeName(int mode) {
        return mode == TtsEngine.MODE_EDGE ? "Edge 神经音色"
                : mode == TtsEngine.MODE_SYSTEM ? "系统语音" : "自动（Edge 优先）";
    }

    private void showMore() {
        String[] items = {"朗读引擎：" + engineModeName(engineModePref()), "边听边读（回阅读器）"};
        ReadDialogs.options(this, "更多", items, which -> {
            if (which == 0) pickEngine();
            else openReaderHere();
        });
    }

    private void pickEngine() {
        String[] items = {"自动（Edge 优先，失败切系统）", "Edge 神经音色", "系统语音"};
        ReadDialogs.optionsChecked(this, "朗读引擎", items, engineModePref(), which -> {
            getSharedPreferences(PREFS_TTS, MODE_PRIVATE).edit()
                    .putInt(KEY_ENGINE_MODE, which).apply();
            if (engine != null) {
                engine.setEngineMode(which);
                // 模式变了，当前段按新引擎重合成（缓存分家、不会串音色）
                ttsDead = false;
                prepareChapter(ci, si, false);
            }
            refreshLabels();
            ToastUtil.show(this, "已切换为" + engineModeName(which), Toast.LENGTH_SHORT);
        });
    }

    /** 边听边读：带当前章与段位进度回小说阅读器。 */
    private void openReaderHere() {
        try {
            saveProgress();
            rs.chapterIndex = ci;
            rs.startFraction = segCount > 0
                    ? Math.round(si * 10000f / segCount) : 0;
            com.fongmi.android.tv.read.ReadingRouter.openReading(this, rs);
        } catch (Throwable t) {
            ToastUtil.show(this, "阅读器打开失败", Toast.LENGTH_SHORT);
        }
    }

    private void seekBy(long deltaMs) {
        if (mService == null) return;
        try {
            long pos = player().getPosition();
            long dur = player().getDuration();
            long target = pos + deltaMs;
            if (target < 0) target = 0;
            if (dur > 0 && target > dur) target = dur;
            player().seekTo(target);
        } catch (Throwable t) {
            // 跳转失败按本页既有反馈形态（ToastUtil）显形＋记名，不许点了没动静。
            Log.w("TtsReadActivity", "seekBy failed", t);
            ToastUtil.show(this, "跳转失败", Toast.LENGTH_SHORT);
        }
    }

    // ================= 正文跟读 =================

    /** 当前章正文上屏（段拼接）＋当前段高亮；段表在后台线程取（可能触发正文抓取）。 */
    private void updateBody() {
        if (tvBody == null || engine == null) return;
        final int chapter = ci;
        final int seg = si;
        if (bodyChapter == chapter && bodySegs != null) {
            highlightSegment(seg);
            return;
        }
        new Thread(() -> {
            java.util.List<String> segs;
            try {
                segs = engine.segmentsOf(chapter);
            } catch (Throwable t) {
                segs = new ArrayList<>();
            }
            final java.util.List<String> fs = segs;
            runOnUiThread(() -> {
                if (released || chapter != ci) return;
                bodyChapter = chapter;
                bodySegs = fs;
                bodyStarts = new int[fs.size()];
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < fs.size(); i++) {
                    bodyStarts[i] = sb.length();
                    sb.append(fs.get(i));
                }
                curSegSpan = null;
                tvBody.setText(sb.toString());
                bodyScroll.scrollTo(0, 0);
                highlightSegment(si);
            });
        }).start();
    }

    private void highlightSegment(int seg) {
        if (tvBody == null || bodySegs == null || bodyStarts == null) return;
        if (seg < 0 || seg >= bodySegs.size()) return;
        CharSequence cs = tvBody.getText();
        if (!(cs instanceof android.text.Spannable)) return;
        android.text.Spannable sp = (android.text.Spannable) cs;
        if (curSegSpan != null) sp.removeSpan(curSegSpan);
        int start = bodyStarts[seg];
        int end = start + bodySegs.get(seg).length();
        if (end > sp.length()) end = sp.length();
        if (end <= start) return;
        curSegSpan = new android.text.style.BackgroundColorSpan(0x3DFF9E57);
        sp.setSpan(curSegSpan, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // 当前段滚到可见区上部
        bodyScroll.post(() -> {
            try {
                android.text.Layout layout = tvBody.getLayout();
                if (layout == null) return;
                int line = layout.getLineForOffset(start);
                int y = layout.getLineTop(line) - dp(60);
                bodyScroll.smoothScrollTo(0, Math.max(0, y));
            } catch (Throwable ignored) {
            }
        });
    }

    // ================= 服务绑定 =================

    private void bindPlaybackService() {
        if (bound) return;
        startService(new Intent(this, PlaybackService.class));
        bindService(new Intent(this, PlaybackService.class)
                .setAction(PlaybackService.LOCAL_BIND_ACTION), this, BIND_AUTO_CREATE);
        bound = true;
    }

    private PlayerManager player() {
        return mService.player();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        mService = ((PlaybackService.LocalBinder) binder).getService();
        mService.setPlaybackForeground(true);
        mService.setNavigationCallback(navCallback, playbackKey());
        engine = new TtsEngine(this, job.bookKey(), this::resolveText, this);
        engine.setEngineMode(engineModePref());
        engine.setVoice(job.voiceId, job.rate);
        // 起始段：按传入的阅读万分比估算（确定性切分可重算）
        prepareChapter(ci, si, true);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mService = null;
    }

    /** 导航回调的 key 单一出处：注册、页面清除、detached 真关闭清除（构造时传入的
     * navKey）都用它，与服务侧「清除只有 key 与槽位一致才生效」的口径对齐。 */
    private static String navKeyFor(String bookKey) {
        return "tts:" + bookKey.hashCode();
    }

    private String playbackKey() {
        return navKeyFor(job.bookKey());
    }

    private final PlaybackService.NavigationCallback navCallback =
            new PlaybackService.NavigationCallback() {
                @Override public void onPrev() { if (!released) jumpChapter(-1); }
                @Override public void onNext() { if (!released) jumpChapter(1); }
                @Override public void onStop() { if (!released) onClose(); }
            };

    private void unregisterNav() {
        try {
            if (mService != null) mService.setNavigationCallback(null, playbackKey());
        } catch (Throwable ignored) {
        }
    }

    // ================= 正文与段 =================

    /** 章正文（worker 线程调）：照小说阅读器同口径懒取。 */
    private String resolveText(int chapterIndex) {
        try {
            // 先吃阅读页落盘的章缓存（与阅读页 cachedOrResolve 同口径、同键）：
            // 读过的章朗读不再现抓，源侧一时抓不到（密钥/网络）也不整本哑火。
            if (rs.localId == null || rs.localId.isEmpty()) {
                String key = "NOVEL|" + rs.sourceName + "|" + rs.bookTitle;
                String cached = com.fongmi.android.tv.read.ReadingDownload
                        .loadNovelChapter(this, key, chapterIndex);
                if (cached != null && !cached.isEmpty()) return cached;
            }
            rs.chapterIndex = chapterIndex;
            ReadingSession.Chapter ch = rs.chapters.get(chapterIndex);
            return NovelReaderActivity.resolveText(rs, ch);
        } catch (Throwable t) {
            return "";
        }
    }

    /** 准备某章某段（后台线程做正文解析，主线程只发请求）。 */
    private void prepareChapter(int chapter, int seg, boolean fromStart) {
        new Thread(() -> {
            List<String> segs = engine.segmentsOf(chapter);
            if (segs.isEmpty()) {
                skipEmptyChapter(chapter, 1);
                return;
            }
            int s = Math.max(0, Math.min(seg, segs.size() - 1));
            if (fromStart && seg == 0 && job.startFraction > 0 && chapter == ci) {
                s = Math.max(0, Math.min(
                        Math.round(job.startFraction / 10000f * segs.size()), segs.size() - 1));
            }
            final int target = s;
            final int count = segs.size();
            runOnUiThread(() -> {
                if (released) return;
                segCount = count;
                wantCi = chapter;
                wantSi = target;
                refreshLabels();
                engine.request(chapter, target);
                if (mService != null && !player().isPlaying()
                        && player().getPlaybackState() != Player.STATE_BUFFERING) {
                    ToastUtil.show(this, "正在合成语音…", Toast.LENGTH_SHORT);
                }
            });
        }).start();
    }

    private void playFile(int chapter, int seg, File file) {
        if (released || mService == null) return;
        ci = chapter;
        si = seg;
        currentFile = file;
        wantCi = chapter;
        wantSi = seg;
        endedGuardCi = -1;
        refreshLabels();
        updateBody();
        ReadingSession.Chapter c = job.chapters.get(Math.min(ci, job.chapters.size() - 1));
        MediaMetadata meta = new MediaMetadata.Builder()
                .setTitle(c.name + " · 第" + (si + 1) + "段")
                .setArtist(job.bookTitle + " · 神经朗读")
                .build();
        player().browse(PlaySpec.from(playbackKey(), Uri.fromFile(file).toString(),
                new HashMap<>(), meta));
        saveProgress();
    }

    /**
     * 跳过空章：探章必须在后台线程跑——segmentsOf 会现抓正文（网络），
     * 调用方有两处是经 runOnUiThread 进来的，在主线程上逐章探抓取，
     * 源侧一慢就是几十秒主线程埋死→ANR（2026-10-31 他真机实证：
     * 短剧正文抓取失败时每章都空、连探带卡整App 无响应）。本方法自带
     * 线程，调用方在哪个线程调都安全；提示与起播动作回主线程发。
     */
    private void skipEmptyChapter(int chapter, int dir) {
        new Thread(() -> {
            int next = chapter + dir;
            int guard = 0;
            while (next >= 0 && next < job.chapters.size() && guard++ < 50) {
                final int n = next;
                List<String> segs;
                try {
                    segs = engine.segmentsOf(n);
                } catch (Throwable t) {
                    segs = new ArrayList<>();
                }
                if (!segs.isEmpty()) {
                    final boolean say = guard > 1 || dir > 0;
                    runOnUiThread(() -> {
                        if (released) return;
                        if (say) {
                            final ReadingSession.Chapter c = job.chapters.get(n);
                            ToastUtil.show(this, "「" + c.name + "」没有正文，已跳过",
                                    Toast.LENGTH_SHORT);
                        }
                        prepareChapter(n, 0, false);
                    });
                    return;
                }
                next += dir;
            }
            runOnUiThread(() -> {
                if (released) return;
                ToastUtil.show(this, "后面没有可读的章节了", Toast.LENGTH_SHORT);
            });
        }).start();
    }

    private void jumpChapter(int d) {
        int n = ci + d;
        if (n < 0 || n >= job.chapters.size()) {
            ToastUtil.show(this, d > 0 ? "已经是最后一章" : "已经是第一章",
                    Toast.LENGTH_SHORT);
            return;
        }
        saveProgress();
        if (mService != null) player().stop();
        new Thread(() -> {
            List<String> segs = engine.segmentsOf(n);
            runOnUiThread(() -> {
                if (segs.isEmpty()) skipEmptyChapter(n, d);
                else prepareChapter(n, 0, false);
            });
        }).start();
    }

    // ================= TtsEngine.Listener（worker 线程回调） =================

    @Override
    public void onSegmentReady(int chapter, int seg, File file) {
        runOnUiThread(() -> {
            if (released) return;
            if (chapter == wantCi && seg == wantSi) playFile(chapter, seg, file);
        });
    }

    @Override
    public void onChapterEmpty(int chapter) {
        // 预取命中空章：推进时由 skipEmptyChapter 处理，这里忽略
    }

    private long lastFallbackToastAt;
    private String lastFallbackReason;

    @Override
    public void onFallbackToSystem(String reason) {
        runOnUiThread(() -> {
            if (released) return;
            refreshLabels();
            long now = System.currentTimeMillis();
            boolean sameReason = reason != null && reason.equals(lastFallbackReason);
            if (sameReason && now - lastFallbackToastAt < 5 * 60_000L) return;
            lastFallbackToastAt = now;
            lastFallbackReason = reason;
            ToastUtil.show(this, "已切换系统语音（"
                            + (reason == null ? "Edge 朗读服务连不上" : reason) + "）",
                    Toast.LENGTH_LONG);
        });
    }

    @Override
    public void onTtsError(String humanMsg) {
        runOnUiThread(() -> {
            if (released) return;
            ttsDead = true;
            try {
                if (mService != null) player().pause();
            } catch (Throwable ignored) {
            }
            ToastUtil.show(this, humanMsg + "（点播放可重试）", Toast.LENGTH_LONG);
        });
    }

    private void toggle() {
        if (mService == null) return;
        if (ttsDead) {
            // 重试：重建引擎，从当前段重来
            ttsDead = false;
            try {
                engine.shutdown();
            } catch (Throwable ignored) {
            }
            engine = new TtsEngine(this, job.bookKey(), this::resolveText, this);
            engine.setEngineMode(engineModePref());
            engine.setVoice(curVoice == null ? job.voiceId : curVoice.id, SPEEDS[speedIndex]);
            prepareChapter(ci, si, false);
            ToastUtil.show(this, "正在重连朗读服务…", Toast.LENGTH_SHORT);
            return;
        }
        if (player().isPlaying()) player().pause();
        else player().play();
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.length;
        float s = SPEEDS[speedIndex];
        String label = String.format(Locale.US, "%.2f", s)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
        tvSpeed.setText("语速 " + label + "x");
        if (engine != null && curVoice != null) {
            engine.setVoice(curVoice.id, s);
            // 语速走服务端合成：从本段起用新语速重听
            prepareChapter(ci, si, false);
            ToastUtil.show(this, "语速 " + label + "x，本段起生效", Toast.LENGTH_SHORT);
        }
    }

    private void pickTimer() {
        ReadDialogs.options(this, "定时关闭", TIMER_ITEMS, which -> {
            long[] ms = {15 * 60000L, 30 * 60000L, 60 * 60000L, 0};
            sleepAt = ms[which] == 0 ? 0 : System.currentTimeMillis() + ms[which];
            tvTimer.setText(ms[which] == 0 ? "定时关" : "剩" + TIMER_ITEMS[which]);
        });
    }

    private void pickVoice() {
        if (engineModePref() == TtsEngine.MODE_SYSTEM) {
            // 系统模式下出声的是系统嗓：音色名单换系统嗓，选了真传到
            // 合成端（2026-11-01 定段补——此前此模式下选 Edge 音色在
            // 合成段被整段忽略，选哪个都是系统缺省一个音）。
            pickSysVoice();
            return;
        }
        if (voices.isEmpty()) {
            ToastUtil.show(this, "音色列表加载中，稍候再试", Toast.LENGTH_SHORT);
            return;
        }
        String[] items = new String[voices.size()];
        int checked = 0;
        String cur = curVoice == null ? job.voiceId : curVoice.id;
        for (int i = 0; i < voices.size(); i++) {
            EdgeVoice v = voices.get(i);
            items[i] = v.label() + (v.storyteller ? " ★" : "");
            if (v.id.equals(cur)) checked = i;
        }
        ReadDialogs.optionsChecked(this, "选择音色（神经人声）", items, checked,
                which -> {
                    EdgeVoice v = voices.get(which);
                    curVoice = v;
                    EdgeVoiceCatalog.setDefaultVoice(v.id);
                    if (engine != null) {
                        if (engine.usingSystem()
                                && engine.engineMode() == TtsEngine.MODE_AUTO) {
                            // 正在系统兜底里：选 Edge 音色＝想回 Edge，
                            // 重建引擎清兜底旗、用新音色重试
                            try {
                                engine.shutdown();
                            } catch (Throwable ignored) {
                            }
                            engine = new TtsEngine(this, job.bookKey(),
                                    this::resolveText, this);
                            engine.setEngineMode(engineModePref());
                        }
                        engine.setVoice(v.id, SPEEDS[speedIndex]);
                        ttsDead = false;
                        prepareChapter(ci, si, false);
                        ToastUtil.show(this, "已换「" + v.name + "」，本段起生效",
                                Toast.LENGTH_SHORT);
                    }
                    refreshLabels();
                }, "试听当前音色", this::trialVoice);
    }

    /** 系统模式选音色：列系统引擎的中文嗓（＋「系统默认」一项），选中落盘、本段重合成生效。 */
    private void pickSysVoice() {
        ToastUtil.show(this, "正在读取系统音色…", Toast.LENGTH_SHORT);
        com.fongmi.android.tv.tts.SysVoiceCatalog.listChineseVoices(this, list -> {
            if (released) return;
            if (list.isEmpty()) {
                ToastUtil.show(this, "系统语音没有可选的中文音色", Toast.LENGTH_LONG);
                return;
            }
            String cur = com.fongmi.android.tv.tts.SysVoiceCatalog.selected();
            String[] items = new String[list.size() + 1];
            items[0] = "系统默认";
            int checked = 0;
            for (int i = 0; i < list.size(); i++) {
                items[i + 1] = list.get(i).label;
                if (list.get(i).name.equals(cur)) checked = i + 1;
            }
            ReadDialogs.optionsChecked(this, "选择音色（系统语音）", items, checked,
                    which -> {
                        String name = which == 0 ? "" : list.get(which - 1).name;
                        com.fongmi.android.tv.tts.SysVoiceCatalog.select(name);
                        if (engine != null) {
                            ttsDead = false;
                            // 系统段文件按（语速）分目录、不含音色：重合成前
                            // 先把当前段的旧嗓文件摘掉，否则 fileFor 命中
                            // 旧文件直接播旧嗓（换了跟没换一样）。
                            engine.discardSegmentAudio(ci, si);
                            prepareChapter(ci, si, false);
                        }
                        refreshLabels();
                        ToastUtil.show(this, "已换系统音色，本段起生效",
                                Toast.LENGTH_SHORT);
                    });
        });
    }

    private void trialVoice() {
        final EdgeVoice v = curVoice != null ? curVoice
                : EdgeVoiceCatalog.find(voices, job.voiceId);
        if (v == null) return;
        ToastUtil.show(this, "正在合成试听…", Toast.LENGTH_SHORT);
        new Thread(() -> {
            try {
                byte[] mp3 = new EdgeTtsClient().synthesize(
                        EdgeVoiceCatalog.TRIAL_TEXT, v.id, SPEEDS[speedIndex]);
                File dir = new File(getCacheDir(), "tts_trial");
                dir.mkdirs();
                File f = new File(dir, "trial.mp3");
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(mp3);
                }
                runOnUiThread(() -> {
                    if (released || mService == null) return;
                    inTrial = true;
                    trialResumeCi = ci;
                    trialResumeSi = si;
                    MediaMetadata meta = new MediaMetadata.Builder()
                            .setTitle("试听 · " + v.name)
                            .setArtist("神经朗读").build();
                    player().browse(PlaySpec.from(playbackKey(),
                            Uri.fromFile(f).toString(), new HashMap<>(), meta));
                });
            } catch (EdgeTtsClient.TtsException e) {
                runOnUiThread(() -> ToastUtil.show(TtsReadActivity.this,
                        e.getMessage(), Toast.LENGTH_LONG));
            } catch (Throwable t) {
                runOnUiThread(() -> ToastUtil.show(TtsReadActivity.this,
                        EdgeTtsClient.ERR_OFFLINE, Toast.LENGTH_LONG));
            }
        }).start();
    }

    private void showToc() {
        String[] names = new String[job.chapters.size()];
        for (int i = 0; i < names.length; i++) names[i] = job.chapters.get(i).name;
        ReadDialogs.options(this, job.bookTitle + "（" + names.length + " 章）", names,
                which -> {
                    saveProgress();
                    if (mService != null) player().stop();
                    prepareChapter(which, 0, false);
                });
    }

    private void loadVoices() {
        curVoice = EdgeVoiceCatalog.find(EdgeVoiceCatalog.builtIn(), job.voiceId);
        if (curVoice == null && !EdgeVoiceCatalog.builtIn().isEmpty()) {
            curVoice = EdgeVoiceCatalog.builtIn().get(0);
        }
        new Thread(() -> {
            List<EdgeVoice> list = EdgeVoiceCatalog.fetchRemote();
            runOnUiThread(() -> {
                if (released) return;
                voices = list;
                EdgeVoice v = EdgeVoiceCatalog.find(list,
                        curVoice == null ? job.voiceId : curVoice.id);
                if (v != null) curVoice = v;
                refreshLabels();
            });
        }).start();
        voices = EdgeVoiceCatalog.builtIn();
    }

    private String fmt(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
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
                        ListenMiniBar.get(TtsReadActivity.this)
                                .playingChanged(MINI_ID, player().isPlaying());
                    } catch (Throwable ignored) {
                    }
                    if (sleepAt > 0 && System.currentTimeMillis() >= sleepAt) {
                        sleepAt = 0;
                        tvTimer.setText("定时关");
                        player().pause();
                        ToastUtil.show(TtsReadActivity.this, "定时到，已暂停",
                                Toast.LENGTH_SHORT);
                    }
                    if (p != null && p.getPlaybackState() == Player.STATE_ENDED
                            && !(endedGuardCi == ci && endedGuardSi == si)) {
                        endedGuardCi = ci;
                        endedGuardSi = si;
                        if (inTrial) {
                            inTrial = false;
                            prepareChapter(trialResumeCi, trialResumeSi, false);
                        } else if (si + 1 < segCount) {
                            prepareChapter(ci, si + 1, false);
                        } else {
                            saveProgress();
                            skipEmptyChapter(ci, 1);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            handler.postDelayed(this, 500);
        }
    };

    // ================= 进度（kind="TTS"，与阅读进度分开） =================

    private ReadingStore.Record findRecord() {
        try {
            for (ReadingStore.Record r : ReadingStore.list()) {
                if ("TTS".equals(r.kind) && job.sourceName.equals(r.sourceName)
                        && job.bookTitle.equals(r.bookTitle)) return r;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void saveProgress() {
        try {
            ReadingSession.Chapter c = job.chapters.get(Math.min(ci, job.chapters.size() - 1));
            ReadingStore.Record r = findRecord();
            if (r == null) r = new ReadingStore.Record();
            r.kind = "TTS";
            r.sourceName = job.sourceName;
            r.bookTitle = job.bookTitle;
            r.coverUrl = job.coverUrl;
            r.chapterName = c.name;
            r.chapterIndex = ci;
            r.segIndex = si;
            r.chapterUrl = c.url;
            r.siteKey = job.siteKey;
            r.legadoKey = job.legadoKey;
            r.ruleTitle = job.ruleTitle;
            r.localId = job.localId;
            r.origin = TextUtils.isEmpty(job.localId) ? "" : "local";
            r.position = mService == null ? 0 : (int) player().getPosition();
            r.updatedAt = System.currentTimeMillis();
            ReadingStore.save(r);
        } catch (Throwable t) {
            // 落盘失败：save 未达则旧记录原样保留、不被覆盖；失败记名并提示，
            // 不许续听位置静默丢失无痕。
            Log.w("TtsReadActivity", "saveProgress failed", t);
            ToastUtil.show(this, "续听进度保存失败", Toast.LENGTH_SHORT);
        }
    }

    private void onClose() {
        closed = true;
        // 真关闭走迷你条策略：停播＋落盘＋注销（performClose 调页 control）
        try {
            ListenMiniBar.get(this).closeSession(MINI_ID);
        } catch (Throwable t) {
            TtsJob.remove(jobId);
            saveProgress();
            try {
                if (engine != null) engine.shutdown();
            } catch (Throwable ignored) {
            }
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
        // 页销毁：控制权交给条（detached 兜底，不持有页实例），会话不注销——
        // 后台续播时条就是主；真关闭（10秒/音乐页/点封面）才停播。
        if (miniSession != null) {
            boolean handed = false;
            try {
                miniSession.setDetached(new TtsDetached(jobId, job, ci, si,
                        playbackKey()));
                handed = true;
            } catch (Throwable t) {
                Log.w("TtsReadActivity", "detached handover failed in onDestroy", t);
            }
            // 交接成功才清页控制；失败时保留 control——reopen 首路只靠
            // reopenIntent 重启本页（不调页方法），是接不上的最后明路，
            // 不许连它一起清掉让点条静默死。
            if (handed) {
                miniSession.setControl(null);
            } else {
                Log.w("TtsReadActivity", "handover failed: keep control for reopen route");
            }
        }
        // 页面销毁即停合成引擎（防泄漏）；当前段在后台服务里播到段尾。
        // 用户点关闭才 stop 播放＋注销锁屏回调（与 AudioBookActivity 同口径）。
        try {
            if (engine != null) engine.shutdown();
        } catch (Throwable ignored) {
        }
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
