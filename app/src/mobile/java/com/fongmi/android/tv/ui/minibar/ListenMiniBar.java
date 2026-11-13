package com.fongmi.android.tv.ui.minibar;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.minibar.MiniBarPolicy;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.activity.ReadingSkin;
import com.fongmi.android.tv.utils.ImgUtil;
import com.github.catvod.crawler.SpiderDebug;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 听书迷你条总控（单例，常驻应用级）：
 * 露条范围按页白名单收口（潇洒哥亲定）：音乐条只在音乐首页、听书条只在
 * 阅读页（ReadingShelfActivity），别处一律不挂；槽位仲裁同一时刻只露一条；
 * 「消失＝关闭」三条真关闭路径；暂停超 10 秒自动关；声音绝不能没主。
 *
 * 纯加法：不改任何现有播放逻辑，只在外层包一层条。
 */
public class ListenMiniBar implements MiniBarPolicy.Actions {

    private static ListenMiniBar instance;

    public static synchronized ListenMiniBar get(Context ctx) {
        if (instance == null) {
            instance = new ListenMiniBar(ctx.getApplicationContext());
        }
        return instance;
    }

    private final Context app;
    private final MiniBarPolicy policy;
    private final Map<String, ListenSession> sessions = new LinkedHashMap<>();
    private final Map<String, WeakReference<Activity>> pages = new LinkedHashMap<>();
    /** 会话页至少 resume 过一次（注册到 onResume 前不露条，防旧页闪现）。 */
    private final java.util.Set<String> pageSeen = new java.util.HashSet<>();

    private PlaybackService svc;
    private boolean bound;
    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            svc = ((PlaybackService.LocalBinder) binder).getService();
            refreshPlayIcon();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            svc = null;
        }
    };

    private View barView;
    /** 挂载代次：attach/detach 各进一步。hideBar 的淡出收尾动画即使被 cancel、
     * 它的 withEndAction 仍可能跑——收尾只认发起隐藏时那一代，代次变了（条已被
     * 重新挂上或摘下）就什么都不做，防「状态说在、条被旧收尾摘掉、再无事件能救」。 */
    private int barGen;
    private ImageView ivCover;
    private TextView tvTitle;
    private ImageView btnPlay;
    private TextView btnNext;
    private TextView btnPrev;
    private WeakReference<Activity> hostRef;
    private String lastVisible;


    private ListenMiniBar(Context app) {
        this.app = app;
        Handler h = new Handler(Looper.getMainLooper());
        this.policy = new MiniBarPolicy(this, System::currentTimeMillis,
                new MiniBarPolicy.Timer() {
                    private Runnable pending;

                    @Override
                    public void schedule(long delayMs, Runnable task) {
                        cancel();
                        pending = task;
                        h.postDelayed(task, delayMs);
                    }

                    @Override
                    public void cancel() {
                        if (pending != null) h.removeCallbacks(pending);
                        pending = null;
                    }
                });
    }

    // ================= 会话管理（页调用） =================

    /** 页 onCreate：注册会话（后来者占位，先来者让位不关闭）。 */
    public synchronized void register(ListenSession s, Activity page) {
        sessions.put(s.id(), s);
        pages.put(s.id(), new WeakReference<>(page));
        pageSeen.remove(s.id());
        policy.register(s);
        ensureBound();
        refreshBar();
    }

    /** 真关闭后内部调用（不触发回调，避免递归）。 */
    private synchronized void unregisterLocked(String id) {
        sessions.remove(id);
        pages.remove(id);
        policy.unregister(id);
        if (sessions.isEmpty()) releaseBind();
        refreshBar();
    }

    public void pagePaused(String id) {
        policy.pagePaused(id);
        refreshBar();
    }

    public void pageResumed(String id) {
        pageSeen.add(id);
        policy.pageResumed(id);
        refreshBar();
    }

    public void playingChanged(String id, boolean playing) {
        policy.playingChanged(id, playing);
        refreshPlayIcon();
    }

    /** 更新条上文案（切章/切集时页调）。 */
    public void updateMeta(String id, String title, String sub) {
        ListenSession s = sessions.get(id);
        if (s == null) return;
        s.setMeta(title, sub, s.getCoverUrl());
        if (tvTitle != null && id.equals(policy.visibleId())) {
            tvTitle.setText(oneLine(s));
        }
    }

    /** 封面点击 / 页内关闭钮：真关闭（停播＋落盘）。 */
    public void closeSession(String id) {
        policy.closeSession(id);
    }

    /**
     * 书架删条目时调：删的正是条上挂的那个会话（按会话标题认）就一并
     * 真关闭（停播＋收条）；不是就什么都不动。调用方须在本方法之后再删
     * 存储记录——真关闭的落盘会把记录写回，先删后关等于白删。
     */
    public void closeSessionIfTitle(String id, String title) {
        ListenSession s = sessions.get(id);
        if (s == null || title == null || title.isEmpty()) return;
        if (title.equals(s.getTitle())) policy.closeSession(id);
    }







    // ================= MiniBarPolicy.Actions =================

    @Override
    public void onCloseSession(String id) {
        ListenSession s;
        synchronized (this) {
            s = sessions.get(id);
        }
        if (s != null) s.performClose(app, svc);
        synchronized (this) {
            unregisterLocked(id);
        }
    }

    @Override
    public void onVisibleChanged(String visibleId) {
        lastVisible = visibleId;
        refreshBar();
    }

    // ================= 宿主生命周期（App 回调） =================

    public void onActivityResumed(Activity activity) {
        refreshBar();
    }

    public void onActivityPaused(Activity activity) {
        Activity host = hostRef == null ? null : hostRef.get();
        if (host == activity) {
            // 收尾动作与 detachBar 同口径（代次推进＋停封面旋转/进度轮询），但摘除改走
            // 淡出：此前只有这条暂停路是直接 removeView 硬没，与 hideBar 的 200ms 淡出
            // 口径不一致。淡出途中若新宿主接管，attachBar 再推进代次即作废本次收尾
            // （hideBar 的收尾动作里有代次守卫），不会误摘新条。
            barGen++;
            hideBar();
        }
    }

    // ================= 条的显示 =================

    private synchronized void refreshBar() {
        String visible = policy.visibleId();
        Activity host = currentHost();
        if (visible == null || host == null || isSessionPage(host, visible)
                || isListenOffReadingPage(host, visible)) {
            hideBar();
            return;
        }
        // 会话页尚在启动中（注册了但还没 onResume）：不露条，防旧页闪现
        WeakReference<Activity> ref = pages.get(visible);
        if (ref != null && ref.get() != null && !pageSeen.contains(visible)) {
            hideBar();
            return;
        }
        attachBar(host, visible);
    }

    /** 当前前台 Activity（App 维护的引用）。 */
    private Activity currentHost() {
        try {
            return com.fongmi.android.tv.App.activity();
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isSessionPage(Activity host, String visibleId) {
        WeakReference<Activity> ref = pages.get(visibleId);
        Activity page = ref == null ? null : ref.get();
        return page == host;
    }


    /**
     * 听书条白名单（潇洒哥 2026-10-29 亲定「只在阅读页显示，别处一律不挂」）：
     * 听书会话（audiobook／tts）只许在阅读页（ui.activity.ReadingShelfActivity，
     * 底栏「阅读」的落点、阅读模块主页面）露条，其它任何页面——视频首页/点播
     * 各页、搜索、设置、短剧、小说/漫画阅读器页——一律不露。会话页（听书播放页
     * AudioBookActivity、朗读页 TtsReadActivity）本就被 isSessionPage 先挡，条在
     * 播放页上面是多余的，不在此白名单内。
     * 与音乐白名单同型：判定挂在 refreshBar 必经之路上，进阅读页时新页 resume
     * 必触发 refreshBar 把条挂上，离开阅读页同样必经 refreshBar 把条收掉。
     * 收/挂只是摘挂视图，会话与播放状态（队列、断点、播放/暂停）全在策略层与
     * 会话对象里不动，回阅读页 bindSession 现读现绑，不丢状态。
     * 2026-10-30 补注：短剧听书的专辑不许再占这个槽——串线摘除在路由与
     * AudioBookActivity 注册侧（见 audiobook-minibar-sticky-20261030.md），
     * 本白名单不动，普通听书／朗读的条行为照旧。
     */
    private boolean isListenOffReadingPage(Activity host, String visibleId) {
        return !"com.fongmi.android.tv.ui.activity.ReadingShelfActivity"
                        .equals(host.getClass().getName());
    }

    private void attachBar(Activity host, String visibleId) {
        barGen++;
        ListenSession s = sessions.get(visibleId);
        if (s == null) return;
        Activity old = hostRef == null ? null : hostRef.get();
        if (old != host) detachBar();
        if (barView == null) {
            detachBar();
            // 建条前先把宿主立进 hostRef（条的宿主口径与挂条决策同源）
            hostRef = new WeakReference<>(host);
            buildBar();
        }
        bindSession(s);
        ViewGroup decor;
        try {
            decor = (ViewGroup) host.getWindow().getDecorView();
        } catch (Throwable t) {
            return;
        }
        if (barView.getParent() != null) {
            if (barView.getParent() == decor) {
                hostRef = new WeakReference<>(host);
                // 可能正处在淡出中：取消并恢复可见
                try {
                    barView.animate().cancel();
                    barView.setAlpha(1f);
                } catch (Throwable ignored) {
                }
                return;
            }
            detachBar();
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        // 2026-10-25 用户点名改悬浮：音乐条往上抬、两边收——左右边距 16dp、底浮空 18dp
        // （见 bottomMargin 音乐分支），配条布局 elevation 12dp 拉开层次；听书条不动。
        int side = ReadingSkin.dp(host, 16);
        lp.leftMargin = side;
        lp.rightMargin = side;
        lp.bottomMargin = bottomMargin(host);
        decor.addView(barView, lp);
        hostRef = new WeakReference<>(host);
        // 条自身淡入（200ms；页面级过渡这批不做，等海阔试水后统一上）；
        // 例外：播放页收回落点重挂——契约旗在时免淡入直接满显，防落点闪一下
        try {
            barView.animate().cancel();
            barView.setAlpha(0f);
            barView.animate().alpha(1f).setDuration(200).start();
        } catch (Throwable ignored) {
        }
    }

    private int bottomMargin(Activity host) {
        int nav = 0;
        try {
            androidx.core.graphics.Insets in = ViewCompat.getRootWindowInsets(
                    host.getWindow().getDecorView())
                    .getInsets(WindowInsetsCompat.Type.navigationBars());
            nav = in.bottom;
        } catch (Throwable ignored) {
        }
        int extra = 0;
        if (host instanceof MiniBarHost) {
            try {
                extra = ((MiniBarHost) host).miniBarBottomExtraPx();
            } catch (Throwable ignored) {
            }
        }
        return nav + extra + ReadingSkin.dp(host, 12);
    }

    private void detachBar() {
        barGen++;
        try {
            if (barView != null) barView.animate().cancel();
            if (barView != null && barView.getParent() instanceof ViewGroup) {
                ((ViewGroup) barView.getParent()).removeView(barView);
            }
        } catch (Throwable ignored) {
        }
        hostRef = null;
    }

    /** 条自身淡出（200ms）后摘除。 */
    private void hideBar() {
        Activity host = hostRef == null ? null : hostRef.get();
        if (barView == null || !(barView.getParent() instanceof ViewGroup)) {
            hostRef = null;
            return;
        }
        final ViewGroup parent = (ViewGroup) barView.getParent();
        final View v = barView;
        final int gen = barGen;
        hostRef = null;
        try {
            v.animate().cancel();
            v.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                // 代次已变＝隐藏发起后条又被挂上/摘下：这次收尾作废，不许摘新条
                if (gen != barGen) return;
                try {
                    parent.removeView(v);
                } catch (Throwable ignored) {
                }
                v.setAlpha(1f);
            }).start();
        } catch (Throwable t) {
            detachBar();
        }
    }

    // ================= 条的构建与绑定 =================

    /**
     * 听书条外观（用户定稿）：矮条、简洁、书的形态——
     * 长方形小封面（无圆盘，一眼分清是书不是歌）＋一行标题（书名/集名单行）
     * ＋暂停主钮＋下一集小图标，不堆控件。行为规矩不变。
     */
    private void buildBar() {
        Context c = app;
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int hpad = ReadingSkin.dp(c, 12);
        int vpad = ReadingSkin.dp(c, 8);
        bar.setPadding(hpad, vpad, hpad, vpad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD914141C);
        bg.setCornerRadius(ReadingSkin.dp(c, 18));
        bg.setStroke(ReadingSkin.dp(c, 2), 0x2EFFFFFF);
        bar.setBackground(bg);
        try {
            bar.setElevation(ReadingSkin.dp(c, 10));
        } catch (Throwable ignored) {
        }

        // 书形小封面：长方形，无圆盘
        ivCover = new ImageView(c);
        int cw = ReadingSkin.dp(c, 36);
        int ch = ReadingSkin.dp(c, 48);
        LinearLayout.LayoutParams clp =
                new LinearLayout.LayoutParams(cw, ch);
        clp.rightMargin = ReadingSkin.dp(c, 10);
        ivCover.setLayoutParams(clp);
        ivCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ReadingSkin.roundClip(ivCover, c, 8);
        ivCover.setOnClickListener(v -> {
            // 用户语义：点封面＝关闭听书（真关闭，停播＋落盘）
            String vid = policy.visibleId();
            if (vid != null) closeSession(vid);
        });
        bar.addView(ivCover);

        // 一行标题：书名/集名单行
        tvTitle = new TextView(c);
        tvTitle.setTextSize(14);
        tvTitle.setTextColor(ReadingSkin.TEXT);
        tvTitle.getPaint().setFakeBoldText(true);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tvTitle.setLayoutParams(tlp);
        bar.addView(tvTitle);

        // 下一集：小图标，不堆控件
        btnNext = new TextView(c);
        btnNext.setText("\u23ed");
        btnNext.setTextSize(16);
        btnNext.setTextColor(ReadingSkin.SUB);
        btnNext.setGravity(Gravity.CENTER);
        int nbHit = ReadingSkin.dp(c, 48);
        btnNext.setLayoutParams(new LinearLayout.LayoutParams(nbHit, nbHit));
        btnNext.setOnClickListener(v -> {
            String vid = policy.visibleId();
            ListenSession s = vid == null ? null : sessions.get(vid);
            ListenSession.Control ctl = s == null ? null : s.control();
            if (ctl != null && ctl.canNext()) ctl.next();
        });
        bar.addView(btnNext);

        // 上一集：小图标（用户要求上下集都要有，保持矮条简洁）
        btnPrev = new TextView(c);
        btnPrev.setText("\u23ee");
        btnPrev.setTextSize(16);
        btnPrev.setTextColor(ReadingSkin.SUB);
        btnPrev.setGravity(Gravity.CENTER);
        btnPrev.setLayoutParams(new LinearLayout.LayoutParams(nbHit, nbHit));
        btnPrev.setOnClickListener(v -> {
            String vid = policy.visibleId();
            ListenSession s = vid == null ? null : sessions.get(vid);
            ListenSession.Control ctl = s == null ? null : s.control();
            if (ctl != null && ctl.canPrev()) ctl.prev();
        });
        bar.addView(btnPrev);

        // 暂停主钮：矢量图标（原 TextView 的 ⏸ 字符被荣耀 emoji 字体渲成橙方块；
        // 圆底/尺寸/点击行为都不动，只换中间的图标）
        btnPlay = new ImageView(c);
        int bpad = ReadingSkin.dp(c, 11);
        btnPlay.setPadding(bpad, bpad, bpad, bpad);
        btnPlay.setImageResource(R.drawable.ic_music_play);
        btnPlay.setImageTintList(ColorStateList.valueOf(0xFF1A1408));
        int ps = ReadingSkin.dp(c, 44);
        LinearLayout.LayoutParams plp =
                new LinearLayout.LayoutParams(ps, ps);
        plp.leftMargin = ReadingSkin.dp(c, 2);
        btnPlay.setLayoutParams(plp);
        btnPlay.setBackground(ReadingSkin.goldCircle(c));
        btnPlay.setOnClickListener(v -> {
            String vid = policy.visibleId();
            ListenSession s = vid == null ? null : sessions.get(vid);
            if (s == null) return;
            ListenSession.Control ctl = s.control();
            if (ctl != null) {
                ctl.toggle();
            } else {
                s.detachedToggle(svc);
                // 页已销毁时条就是唯一主人：切换后把真实播放态回喂策略
                // （不然策略还记着旧状态、暂停 10 秒自动关不武装，名单里
                // 的记录也停在旧位置——名单与条两套状态打架的根，2026-10-30）
                boolean nowPlaying = s.detachedPlaying(svc);
                policy.playingChanged(vid, nowPlaying);
                if (!nowPlaying) s.detachedSave(app, svc);
            }
            refreshPlayIcon();
        });
        bar.addView(btnPlay);

        // 点条身（非按钮区）回播放页
        bar.setOnClickListener(v -> {
            String vid = policy.visibleId();
            ListenSession s = vid == null ? null : sessions.get(vid);
            if (s == null) return;
            Activity host = hostRef == null ? null : hostRef.get();
            Context ctx = host != null ? host : app;
            s.reopen(ctx);
        });

        barView = bar;
    }

    /** 一行标题：书名/集名单行（用户定的简洁口径）。 */
    private static String oneLine(ListenSession s) {
        String t = s.getTitle() == null ? "" : s.getTitle();
        String sub = s.getSub() == null ? "" : s.getSub().trim();
        return sub.isEmpty() ? t : t + " · " + sub;
    }

    private void bindSession(ListenSession s) {
        tvTitle.setText(oneLine(s));
        loadCover(s);
        if (btnNext != null) btnNext.setVisibility(s.canNext() ? View.VISIBLE : View.GONE);
        if (btnPrev != null) btnPrev.setVisibility(s.canPrev() ? View.VISIBLE : View.GONE);
        refreshPlayIcon();
    }

    private void loadCover(ListenSession s) {
        if (!TextUtils.isEmpty(s.getCoverUrl())) {
            try {
                ImgUtil.load(s.getTitle(), s.getCoverUrl(), ivCover, true);
                return;
            } catch (Throwable ignored) {
            }
        }
        // 无封面：深色书形占位（长方形，无圆盘）
        GradientDrawable ph = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF2A2F3D, 0xFF1B1E28});
        ph.setCornerRadius(ReadingSkin.dp(app, 8));
        ph.setStroke(ReadingSkin.dp(app, 1), 0x40FFFFFF);
        ivCover.setImageDrawable(ph);
    }

    private void refreshPlayIcon() {
        if (btnPlay == null) return;
        String vid = policy.visibleId();
        boolean playing = false;
        ListenSession s = vid == null ? null : sessions.get(vid);
        if (s != null) {
            ListenSession.Control ctl = s.control();
            if (ctl != null) {
                try {
                    playing = ctl.isPlaying();
                } catch (Throwable ignored) {
                }
            } else {
                playing = s.detachedPlaying(svc);
            }
        }
        btnPlay.setImageResource(playing ? R.drawable.ic_music_pause : R.drawable.ic_music_play);
    }










    // ================= 服务绑定（detached 控制用） =================

    private void ensureBound() {
        if (bound) return;
        try {
            Intent it = new Intent(app, PlaybackService.class);
            app.startService(it);
            bound = app.bindService(new Intent(app, PlaybackService.class)
                    .setAction(PlaybackService.LOCAL_BIND_ACTION), conn,
                    Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            bound = false;
        }
    }

    private void releaseBind() {
        if (!bound) return;
        try {
            app.unbindService(conn);
        } catch (Throwable ignored) {
        }
        bound = false;
        svc = null;
    }

    /** 供页内 DetachedOps 兜底读取播放器（包内）。 */
    PlaybackService service() {
        return svc;
    }

    /** 当前是否有存活会话（壳层自查用）。 */
    public boolean hasSessions() {
        return policy.hasSessions();
    }

    /** 当前可见会话 id（自查用）。 */
    public String visibleSessionId() {
        return policy.visibleId();
    }

    /** drawable 小工具：金圆心（封面占位用）。 */
    static Drawable goldDot(Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(ReadingSkin.GOLD);
        int s = ReadingSkin.dp(c, 14);
        d.setSize(s, s);
        return d;
    }
}
