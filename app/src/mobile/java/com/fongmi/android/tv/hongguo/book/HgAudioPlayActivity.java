package com.fongmi.android.tv.hongguo.book;
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

import com.fongmi.android.tv.legado.HgAudioStore;
import com.fongmi.android.tv.legado.HgBookBridge;
import com.fongmi.android.tv.legado.LegadoAudio;
import com.fongmi.android.tv.legado.LegadoException;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.tts.EdgeVoice;
import com.fongmi.android.tv.tts.EdgeVoiceCatalog;
import com.fongmi.android.tv.ui.activity.AudioBookActivity;
import com.fongmi.android.tv.ui.activity.ReadingSkin;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import com.fongmi.android.tv.R;

/**
 * 红果听书单独的播放页（2026-10-30 分家令）：
 * 红果听书是内置的独立一套，只许在本页播；进度/续播只存它自己的一份
 * （HgAudioStore），不写普通听书的 ReadingStore、不注册普通听书的迷你条
 * 会话、不读普通听书的任何状态。服务层自 2026-10-31 W-15 拆套起走自家
 * HgAudioPlaybackService（与主 PlaybackService 同机制、独立实例：自有
 * PlayerManager/会话/通知/导航槽，播放键前缀 hgaudio:），与普通听书、
 * 视频、TTS 互不顶播、互不连坐回收。
 * 本页是播放的唯一主人：页销毁即停播＋落盘，不留 detached 孤儿声；
 * 续播从红果书城/目录再进时按 bookId 回 HgAudioStore 取位置。
 * 由 AudioBookActivity 同链 fork 后按分家口径裁剪（章节解析仍走
 * LegadoAudio 管线，命中哨兵源自动改道 HgBookBridge）。
 */
public class HgAudioPlayActivity extends Activity implements ServiceConnection {

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
    /** 本书 bookId（hgbook 章节地址解出），HgAudioStore 的键。 */
    private String bookId = "";

    public static void start(Activity activity, String sessionId) {
        Intent i = new Intent(activity, HgAudioPlayActivity.class);
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
        // 入口来源门（2026-10-30 纠偏）：本页只许红果听书自家来源进入。
        // 身份键不是红果听书哨兵的专辑（海阔电台 "hk:<规则名>"、普通听书
        // 源的 bookSourceUrl）误入本页时，不许在本页播、更不许把它的进度
        // 写进红果存档 HgAudioStore——专辑原样交回普通听书页接管，本页
        // 只关门不落任何状态。
        if (!HgBookBridge.isAudioKey(album.legadoKey)) {
            AudioBookActivity.start(this, sessionId);
            finish();
            return;
        }
        buildUi();
        // 续播只读自己的一份状态（HgAudioStore 按 bookId 存）：按存档的
        // itemId 在本章表里定位章，再带位置起播。不读普通听书的记录。
        bookId = album.chapters.isEmpty() ? ""
                : HgBookBridge.bookIdOf(album.chapters.get(0).url);
        HgAudioStore.Entry st = HgAudioStore.get(this, bookId);
        if (st != null && st.itemId != null && !st.itemId.isEmpty()) {
            for (int i = 0; i < album.chapters.size(); i++) {
                if (st.itemId.equals(HgBookBridge.itemIdOf(album.chapters.get(i).url))) {
                    album.index = i;
                    resumeMs = Math.max(0, st.position);
                    break;
                }
            }
        }
        bindPlaybackService();
        handler.post(ticker);

        // 页面进出统一渐入渐出（与 BaseActivity 一致）
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
}

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
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

        // 音色
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
            com.fongmi.android.tv.ui.activity.ReadingShelfActivity.start(this);
            finish();
        }));

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

    // ================= 服务绑定（W-15 拆套：绑自家 HgAudioPlaybackService） =================

    private void bindPlaybackService() {
        if (bound) return;
        startService(new Intent(this, HgAudioPlaybackService.class));
        bindService(new Intent(this, HgAudioPlaybackService.class)
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
        return "hgaudio:" + a.legadoKey.hashCode() + ":" + a.title.hashCode();
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
                if (tryTtsFallback(index)) return;
                runOnUiThread(() -> {
                    String m = t instanceof LegadoException ? t.getMessage() : "音频地址解析失败";
                    ToastUtil.show(HgAudioPlayActivity.this, "源级错误：" + m,
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

    /**
     * 红果听书朗读兜底（worker 线程调）：本章地址解析失败时问桥——
     * 是 TTS 书且双地址皆空才回正文（判定在桥/实现内，见 HgBookOps.ttsFallbackText），
     * 拿到正文就整本转朗读页续读（后续章走小说正文链懒取，同一条兜底链）。
     * 返回 true＝已接管，不再弹「源级错误」。只认红果听书专辑＋hgbook 章节地址，
     * 海阔电台/其它书源一律不碰；是 TTS 书但正文也没取到时把话说清，不哑着。
     */
    private boolean tryTtsFallback(int index) {
        try {
            if (album == null || !album.hkRule.isEmpty()) return false;
            if (!com.fongmi.android.tv.legado.HgBookBridge.URL_AUDIO.equals(album.legadoKey)) {
                return false;
            }
            LegadoAudio.Chapter c = album.current();
            if (c == null || !c.url.startsWith("hgbook://")) return false;
            com.fongmi.android.tv.legado.LegadoPipeline.Content content;
            try {
                content = com.fongmi.android.tv.legado.HgBookBridge.ttsFallbackText(c.url);
            } catch (LegadoException e) {
                runOnUiThread(() -> ToastUtil.show(HgAudioPlayActivity.this,
                        "这一章没有播放地址，正文也没取到，暂时听不了",
                        Toast.LENGTH_LONG));
                return true;
            }
            if (content == null || content.text == null
                    || content.text.trim().isEmpty()) return false;
            final String text = content.text;
            runOnUiThread(() -> switchToTtsRead(index, text));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 转朗读页：整本章节表＋本章正文递给 TtsJob，音色用全局默认（与本页音色卡同口径）。 */
    private void switchToTtsRead(int index, String text) {
        if (released) return;
        try {
            com.fongmi.android.tv.read.HgBookSeeder.ensureSeeded();
            com.fongmi.android.tv.tts.TtsJob job = new com.fongmi.android.tv.tts.TtsJob();
            job.bookTitle = album.title;
            job.coverUrl = album.coverUrl;
            job.sourceName = album.sourceName;
            job.legadoKey = com.fongmi.android.tv.legado.HgBookBridge.URL_NOVEL;
            job.inlineText = text;
            for (LegadoAudio.Chapter ch : album.chapters) {
                job.chapters.add(new com.fongmi.android.tv.read.ReadingSession.Chapter(
                        ch.name, ch.url));
            }
            job.startChapter = Math.max(0, Math.min(index, job.chapters.size() - 1));
            job.startFraction = 0;
            job.voiceId = EdgeVoiceCatalog.defaultVoice();
            job.rate = 1.0f;
            String id = com.fongmi.android.tv.tts.TtsJob.put(job);
            ToastUtil.show(this, "这一章没有音频，已切换为朗读", Toast.LENGTH_LONG);
            com.fongmi.android.tv.ui.activity.TtsReadActivity.start(this, id);
            finish();
        } catch (Throwable t) {
            ToastUtil.show(this, "朗读切换失败", Toast.LENGTH_SHORT);
        }
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
                    // 睡眠定时
                    if (sleepAt > 0 && System.currentTimeMillis() >= sleepAt) {
                        sleepAt = 0;
                        setOptValue(tvTimerVal, "定时关");
                        player().pause();
                        ToastUtil.show(HgAudioPlayActivity.this, "定时到，已暂停",
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
                            ToastUtil.show(HgAudioPlayActivity.this, "专辑播完",
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

    // ================= 进度（只存自己的一份：HgAudioStore） =================

    private void saveProgress() {
        try {
            // 存档门：红果存档只收红果自家专辑（身份键恰为哨兵），非红果
            // 专辑一律不落——与入口来源门同一判据（HgBookBridge.isAudioKey），
            // 防任何路径（销毁落盘/切章/暂停）把外来专辑写进红果账。
            if (album == null || !HgBookBridge.isAudioKey(album.legadoKey)) return;
            LegadoAudio.Chapter c = album.current();
            if (c == null || bookId.isEmpty()) return;
            int pos = mService == null ? 0 : (int) player().getPosition();
            HgAudioStore.save(this, bookId, album.title, album.coverUrl,
                    HgBookBridge.itemIdOf(c.url), c.name, album.index,
                    Math.max(0, pos));
        } catch (Throwable ignored) {
        }
    }

    private void onClose() {
        closed = true;
        // 本页就是播放的唯一主人：关页＝落盘＋停播＋注销锁屏回调＋删静态仓
        saveProgress();
        LegadoAudio.remove(sessionId);
        try {
            if (mService != null) player().stop();
        } catch (Throwable ignored) {
        }
        unregisterNav();
        finish();
    }

    @Override
    protected void onDestroy() {
        released = true;
        saveProgress();
        handler.removeCallbacks(ticker);
        // 分家口径：没有迷你条接管，页销毁即收尾——停播＋注销锁屏回调，
        // 不留 detached 孤儿声；进度已在上面落进 HgAudioStore，下次从
        // 红果书城/目录再进本页按 bookId 续播。
        if (!closed) {
            try {
                if (mService != null) player().stop();
            } catch (Throwable ignored) {
            }
            unregisterNav();
        }
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
