package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.event.StateEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.receiver.ShortcutReceiver;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.FragmentStateManager;
import com.fongmi.android.tv.ui.view.BottomTabs;
import com.fongmi.android.tv.ui.view.XcBottomBar;
import com.fongmi.android.tv.ui.fragment.SettingDanmakuFragment;
import com.fongmi.android.tv.ui.fragment.SettingFragment;
import com.fongmi.android.tv.ui.fragment.ModuleFragment;
import com.fongmi.android.tv.ui.fragment.SettingPlayerFragment;
import com.fongmi.android.tv.ui.fragment.VodFragment;
import com.fongmi.android.tv.ui.lab.LabHomeActivity;
import com.fongmi.android.tv.ui.lab.LabSettingActivity;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.MobileWindow;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.TxtParser;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.ai.AiFloatManager;
import com.fongmi.android.tv.web.WebHomeChrome;
import com.fongmi.android.tv.web.WebHomeChromeStartup;
import com.fongmi.android.tv.web.WebHomeViewport;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.Subscribe;
import org.json.JSONArray;
import org.json.JSONObject;
import org.greenrobot.eventbus.ThreadMode;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends BaseActivity implements XcBottomBar.OnItemSelectedListener, WebHomeChromeController.Host, com.fongmi.android.tv.ui.minibar.MiniBarHost, AiFloatManager.AiFloatHost {

    public static final String EXTRA_NAV_POSITION = "nav_position";
    private static final String STATE_CURRENT_POSITION = "currentPosition";


    private FragmentStateManager mManager;
    private ActivityHomeBinding mBinding;
    private WebHomeChromeController mChrome;
    private Config mStartupConfig;
    private boolean mLocalMediaClaimed;
    private boolean wideWindow;
    private int currentPosition;

    // 首页顶是壁纸/网页深色区，现白图标看得见，保持原样（BaseActivity 默认已改深色图标，这里盖回）。
    @Override
    protected boolean useDarkSystemIcons() {
        return false;
    }

    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!claimLocalMedia(intent)) checkAction(intent);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        wideWindow = MobileWindow.isWide(this);
        currentPosition = savedInstanceState == null ? 0 : savedInstanceState.getInt(STATE_CURRENT_POSITION, 0);
        mStartupConfig = Config.vod();
        mChrome = new WebHomeChromeController(this, mBinding, this, savedInstanceState, WebHomeChromeStartup.restore(mStartupConfig));
        mBinding.getRoot().addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> checkWindowShape(right - left, bottom - top));
        mBinding.navigation.setOnItemSelectedListener(this);
        initBottomTabs();
        initNavigationInsets();
        PermissionUtil.requestFile(this, allGranted -> PermissionUtil.requestNotify(this));
        initFragment(savedInstanceState);
        // 实验室自启排期必须抢在 initConfig 之前：schedule 的同步段只置
        // scheduled 防重标记并投递 3 秒延迟 boot，不做任何同步登记；延迟
        // boot 段随后拉起服务。此前排在 initConfig 之后，App.onCreate 的
        // 反射 kick 又被 R8 改名打死（proguard 已补 LabBoot 保名），冷启
        // 只剩这条腿还迟到。
        try {
            com.fongmi.android.tv.ui.lab.LabBoot.schedule(this);
        } catch (Exception ignored) {
        }
        initConfig();
        // 本地媒体直达：外部 VIEW 打开本地视频/音频，起播不依赖配置，在此先行
        // 分流。原链把 checkAction 只挂在配置回调上——回调要等配置拉完（回环
        // 配置失败还要重试约 20 秒），且被后来的加载顶掉时永不回来，意图会被
        // 首页吞掉（只把应用唤到前台、落首页转圈，视频不播）。认领后配置回调
        // 不再重复处理同一意图；txt/直播/分享等其余意图仍走原回调口径。
        mLocalMediaClaimed = claimLocalMedia(getIntent());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_CURRENT_POSITION, currentPosition);
        if (mChrome != null) mChrome.save(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setNavigation();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 宿主存活步 6（清单 host-survival-step6-
        // inventory；档位经补改复核反汇编订正）：宿主被不
        // 透明页全盖（播放页等）后进程转入可回收档，三签
        // 图墙与全域封面的 Glide 内存缓存＋位图池仍占着
        // 数十至上百 MB——在此整表释放。档位用
        // TRIM_MEMORY_BACKGROUND：Glide 5.0.7 在 UI_HIDDEN
        // 档对内存缓存/数组池只砍到上限一半（当前量低于
        // 半额时释放为零），全清须 ≥BACKGROUND；位图池在
        // 本档同为全清。系统 trim 回调在同进程内盖住时不
        // 触发，在此补上；触发面除全盖外还含切后台、
        // recreate、finish，后果同限一笔缓存清空。视图正
        // 贴着的图不在缓存账上、回面形态不变，后续新贴
        // 图走磁盘缓存重解码。释放是尽力而为：Glide 未就
        // 绪时异常按本文件跨模块调用惯例吞掉，失败即旧形。
        try {
            com.bumptech.glide.Glide.get(this).trimMemory(
                    android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void initEvent() {
        View liveView = mBinding.navigation.findViewById(R.id.live);
        if (liveView != null) liveView.setOnLongClickListener(this::addShortcut);
    }

    private void checkAction(Intent intent) {
        if (intent.hasExtra(EXTRA_NAV_POSITION)) {
            change(intent.getIntExtra(EXTRA_NAV_POSITION, 0));
            intent.removeExtra(EXTRA_NAV_POSITION);
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            String url = com.fongmi.android.tv.utils.Sniffer.getUrl(sharedText);
            if (url == null || url.isEmpty()) url = sharedText;
            if (url != null && !url.isEmpty()) {
                String lower = url.toLowerCase(java.util.Locale.ROOT);
                if (lower.startsWith("magnet:") || lower.startsWith("ed2k:") || lower.startsWith("thunder:")) {
                    VideoActivity.push(this, sharedText);
                } else {
                    try {
                        com.fongmi.android.tv.utils.download.DownloadManager.get().add(url, null);
                        com.fongmi.android.tv.ui.activity.DownloadActivity.start(this);
                    } catch (IllegalArgumentException e) {
                        com.fongmi.android.tv.utils.Notify.show(e.getMessage());
                    }
                }
            }
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, allGranted -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void checkType(Intent intent) {
        String path = UrlUtil.path(intent.getData());
        if ("text/plain".equals(intent.getType()) || path.endsWith(".m3u") || path.endsWith(".txt")) {
            // txt 混合链接导入播放：含 magnet:/ed2k:/thunder:/ftp: 即解析成选集列表播放
            // （ed2k 只认 scheme 前缀、不要求 //；纯 thunder／纯 ftp txt 同样进迅雷线）；
            // 纯 http 的 txt 仍走直播列表（兼容旧行为）。需求（2026-10-10）。
            String text = readText(intent.getData());
            if (text != null && TxtParser.isMixedLinks(text)) {
                List<TxtParser.Link> links = TxtParser.parse(text);
                if (links.isEmpty()) {
                    Notify.show("未找到可播放链接");
                } else {
                    playTxtLinks(intent.getData(), links);
                }
                return;
            }
            loadLive("file:/" + FileChooser.getPathFromUri(intent.getData()));
        } else {
            VideoActivity.push(this, intent.getData().toString());
        }
    }

    private String readText(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (sb.length() > 512 * 1024) break;
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void playTxtLinks(Uri uri, List<TxtParser.Link> links) {
        try {
            JSONArray arr = new JSONArray();
            for (TxtParser.Link link : links) {
                JSONObject obj = new JSONObject();
                obj.put("name", link.name);
                obj.put("url", link.url);
                arr.put(obj);
            }
            String filePath = FileChooser.getPathFromUri(uri);
            String name = filePath == null ? "TXT播放列表" : new File(filePath).getName();
            VideoActivity.startTxt(this, name, arr.toString());
        } catch (Exception e) {
            Notify.show("解析失败");
        }
    }

    private void initFragment(Bundle savedInstanceState) {
        mManager = new FragmentStateManager(mBinding.container, getSupportFragmentManager(), position -> switch (position) {
            case 0 -> VodFragment.newInstance();
            case 1 -> SettingFragment.newInstance();
            case 2 -> SettingPlayerFragment.newInstance();
            case 3 -> SettingDanmakuFragment.newInstance();
            default -> null;
        });
        if (savedInstanceState == null) change(0);
        else restorePosition(currentPosition);
    }

    private void restorePosition(int position) {
        setNavigation();
        syncNavigationSelection();
        changeFragment(position <= 0 ? 0 : Math.min(position, 3));
    }

    private void initConfig() {
        VodConfig.get().config(mStartupConfig == null ? Config.vod() : mStartupConfig).load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                if (!mLocalMediaClaimed) checkAction(getIntent());
            }

            @Override
            public void error(String msg) {
                resetVodChrome();
                if (!mLocalMediaClaimed) checkAction(getIntent());
                StateEvent.empty();
                Notify.show(msg);
            }
        };
    }

    private static final String[] LOCAL_MEDIA_EXTS = {".mp4", ".mkv", ".avi", ".mov", ".webm", ".flv", ".ts", ".m2ts", ".mts", ".rmvb", ".rm", ".wmv", ".mpg", ".mpeg", ".m4v", ".3gp", ".f4v", ".vob", ".mp3", ".flac", ".aac", ".m4a", ".ogg", ".wav", ".ape", ".opus", ".mka"};

    /**
     * 本地媒体意图认领：是本地媒体就直接分流起播并返回 true（调用方不再走
     * checkAction 的推送链）。判别口径见 isLocalMediaIntent；权限门与原链一致
     * （已授权时同步通过、不挡；content 地址自带授权但播放器跨页读仍靠它）。
     */
    private boolean claimLocalMedia(Intent intent) {
        if (!isLocalMediaIntent(this, intent)) return false;
        PermissionUtil.requestFile(this, allGranted -> dispatchLocalMedia(this, intent));
        return true;
    }

    /**
     * 本地媒体判别（分流条件）：VIEW＋data 为 file/content 方案，且满足其一——
     * ① 意图 MIME 是 video/ 或 audio/（含向提供方回查 content 的 MIME）；
     * ② 路径以本地媒体扩展名结尾（覆盖文件管理器不给 MIME、只给 octet-stream
     * 的情形）。text/plain 先排除（txt 混合链接与 m3u 直播清单仍走原链）；
     * http/https/smb/rtsp 等网络方案、磁力/迅雷等推送链接一律不认领。
     */
    static boolean isLocalMediaIntent(Activity activity, Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) return false;
        String scheme = intent.getData().getScheme();
        boolean content = ContentResolver.SCHEME_CONTENT.equals(scheme);
        if (!content && !ContentResolver.SCHEME_FILE.equalsIgnoreCase(scheme)) return false;
        String type = intent.getType();
        if ("text/plain".equals(type)) return false;
        if (type == null && content) {
            try {
                type = activity.getContentResolver().getType(intent.getData());
            } catch (Exception ignored) {
            }
        }
        if (type != null && (type.startsWith("video/") || type.startsWith("audio/"))) return true;
        String path = UrlUtil.path(intent.getData()).toLowerCase(java.util.Locale.ROOT);
        for (String ext : LOCAL_MEDIA_EXTS) if (path.endsWith(ext)) return true;
        return false;
    }

    /** 本地媒体直达分发：能解出真实路径就按 file:// 播（最稳），解不出保留 content 原串；标题取文件名全名。 */
    static void dispatchLocalMedia(Activity activity, Intent intent) {
        Uri data = intent.getData();
        if (data == null) return;
        String path = null;
        try {
            path = FileChooser.getPathFromUri(data);
        } catch (Exception ignored) {
        }
        if (!TextUtils.isEmpty(path)) {
            VideoActivity.startLocal(activity, "file://" + path, new File(path).getName());
            return;
        }
        String name = queryDisplayName(activity, data);
        if (TextUtils.isEmpty(name)) name = data.getLastPathSegment();
        VideoActivity.startLocal(activity, data.toString(), name);
    }

    private static String queryDisplayName(Activity activity, Uri uri) {
        try (Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                openLive();
            }
        });
    }

    /**
     * 底栏页签注册表（自家 XcBottomBar，替代 Material 菜单）：顺序即注册顺序，
     * 全集＝点播／直播／音乐／阅读／小程序／实验室（共用格，二选一占位）／
     * 设置。显隐不在这里定，分别由
     * setNavigation（直播看有没有直播源）与 syncOptionalTabs（共用格归谁）维护。
     * 标签文案取各格现有的 nav_* 标题资源，不另造。
     */
    private void initBottomTabs() {
        List<BottomTabs.Spec> tabs = new ArrayList<>();
        tabs.add(new BottomTabs.Spec(R.id.vod, R.drawable.ic_nav_home_outline, R.drawable.ic_nav_home_filled, getString(R.string.nav_home), true));
        tabs.add(new BottomTabs.Spec(R.id.live, R.drawable.ic_nav_live, 0, getString(R.string.nav_live), LiveConfig.hasUrl()));
        tabs.add(new BottomTabs.Spec(R.id.reading, R.drawable.ic_nav_reading, 0, getString(R.string.nav_reading), true));
        tabs.add(new BottomTabs.Spec(R.id.miniapp, R.drawable.ic_nav_miniapp, 0, getString(R.string.nav_miniapp), false));
        tabs.add(new BottomTabs.Spec(R.id.lab, R.drawable.ic_nav_lab, 0, getString(R.string.nav_lab), false));
        tabs.add(new BottomTabs.Spec(R.id.setting, R.drawable.ic_nav_setting, 0, getString(R.string.nav_setting), true));
        mBinding.navigation.setTabs(tabs);
    }

    private void setNavigation() {
        mBinding.navigation.setTabVisible(R.id.vod, true);
        mBinding.navigation.setTabVisible(R.id.setting, true);
        mBinding.navigation.setTabVisible(R.id.live, LiveConfig.hasUrl());
        mBinding.navigation.setTabVisible(R.id.reading, true);
        syncOptionalTabs();
        syncNavigationSelection();
    }

    private void syncOptionalTabs() {
        // 共用格（实验室／小程序）二选一是他的老规矩，永不同时上底栏，实验室优先，
        // 保持不变；两边开关各自独立存着，丢格不丢数据（实验室关掉后小程序格自动
        // 回来；功能页小程序卡点开也能进小程序首页，见 FeaturesActivity）。
        // 历史背景：旧实现跑在 Material 菜单上，写死最多 6 项（MAX_BOTTOM_TABS，
        // 1.3.53 加第 7 项崩过），所以每次都要先摘再加、逐项对总数守门。自家底栏
        // （XcBottomBar）没有项数上限，这里退化为纯显隐，不再摘加、不再守门。
        boolean wantLab = LabSettingActivity.isTabEnabled(this);
        mBinding.navigation.setTabVisible(R.id.lab, wantLab);
        mBinding.navigation.setTabVisible(R.id.miniapp, !wantLab && FeaturesActivity.isMiniAppEnabled(this));
    }

    private boolean openLive() {
        LiveActivity.start(this);
        overridePendingTransition(R.anim.xc_tab_fade_in, R.anim.xc_tab_fade_out);
        return true;
    }

    private boolean addShortcut(View view) {
        ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(this, getString(R.string.nav_live)).setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher)).setIntent(new Intent(Intent.ACTION_VIEW, null, this, LiveActivity.class)).setShortLabel(getString(R.string.nav_live)).build();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, ShortcutReceiver.class).setAction(ShortcutReceiver.ACTION), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ShortcutManagerCompat.requestPinShortcut(this, info, pendingIntent.getIntentSender());
        return true;
    }

    public void change(int position) {
        setNavigationVisible(true);
        if (position < 2) selectNavigation(position);
        else changeFragment(position);
    }

    private void initNavigationInsets() {
        int baseMargin = getResources().getDimensionPixelSize(R.dimen.xc_bottombar_margin_bottom);
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.navigation, (v, insets) -> {
            int navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            int target = baseMargin + navBar;
            if (lp.bottomMargin != target) {
                lp.bottomMargin = target;
                v.setLayoutParams(lp);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(mBinding.navigation);
    }

    /** MiniBarHost：迷你条贴在底栏上方，多留底栏高度。 */
    @Override
    public int miniBarBottomExtraPx() {
        try {
            int h = mBinding.navigation.getHeight();
            if (h > 0) return h;
            return Math.round(72 * getResources().getDisplayMetrics().density);
        } catch (Throwable t) {
            return 0;
        }
    }

    public void setNavigationVisible(boolean visible) {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mBinding.container.getLayoutParams();
        if (visible) {
            params.addRule(RelativeLayout.ABOVE, R.id.navigation);
            mBinding.navigation.setVisibility(View.VISIBLE);
        } else {
            params.removeRule(RelativeLayout.ABOVE);
            mBinding.navigation.setVisibility(View.GONE);
        }
        mBinding.container.setLayoutParams(params);
        mBinding.getRoot().requestApplyInsets();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.home();
                break;
            case LIVE:
                setNavigation();
                break;
            case COMMON:
                setNavigation();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.THEME) recreate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() == ServerEvent.Type.PUSH) VideoActivity.push(this, event.text());
        if (event.type() == ServerEvent.Type.SEARCH) SearchActivity.start(this, event.text());
    }

    /**
     * 活动型页签（直播/音乐/阅读/小程序/实验室）起页前先把根页切回
     * 点播首页：这些页是独立 Activity、盖在根页上，从设置页点进去、
     * 退出就原样落回设置页（他 2026-11-13 连环亲定的返回规矩：
     * 哪个页签返回都落点播首页）。起页前先切，退出时底下已是首页，
     * 不攒待办旗、不等 onResume 补。
     */
    private void resetRootBeforeTabLaunch() {
        if (currentPosition != 0) change(0);
    }

    @Override
    public boolean onItemSelected(int itemId) {
        setNavigationVisible(true);
        if (itemId == R.id.setting) return changeFragment(1);
        if (itemId == R.id.vod) return changeFragment(0);
        if (itemId == R.id.live) {
            resetRootBeforeTabLaunch();
            return openLive();
        }
        if (itemId == R.id.reading) {
            resetRootBeforeTabLaunch();
            ReadingShelfActivity.start(this);
            overridePendingTransition(R.anim.xc_tab_fade_in, R.anim.xc_tab_fade_out);
            return true;
        }
        if (itemId == R.id.miniapp) {
            resetRootBeforeTabLaunch();
            HkPageActivity.start(this);
            overridePendingTransition(R.anim.xc_tab_fade_in, R.anim.xc_tab_fade_out);
            return true;
        }
        if (itemId == R.id.lab) {
            resetRootBeforeTabLaunch();
            LabHomeActivity.start(this);
            overridePendingTransition(R.anim.xc_tab_fade_in, R.anim.xc_tab_fade_out);
            return true;
        }
        return false;
    }

    private void selectNavigation(int position) {
        int itemId = position == 0 ? R.id.vod : R.id.setting;
        if (mBinding.navigation.getSelectedItemId() == itemId) changeFragment(position);
        else mBinding.navigation.setSelectedItemId(itemId);
    }

    private void syncNavigationSelection() {
        int itemId = currentPosition == 0 ? R.id.vod : R.id.setting;
        if (mBinding.navigation.getSelectedItemId() == itemId) return;
        mBinding.navigation.setOnItemSelectedListener(null);
        mBinding.navigation.setSelectedItemId(itemId);
        mBinding.navigation.setOnItemSelectedListener(this);
    }

    private boolean changeFragment(int position) {
        closeModuleFragment();
        boolean changed = mManager.change(position);
        if (changed) currentPosition = position;
        refreshWebHomeChromeLayout();
        refreshAiFloat();
        return changed;
    }

    // 手机端模块页入口：ModuleFragment 只能宿在 HomeActivity（它把 activity 强转
    // HomeActivity），所以不另起 Activity，直接以覆盖方式加在首页容器上。
    // 不走 FragmentStateManager 的页签位：模块页不是第五个页签，不动首页导航；
    // 切页签／返回时先把模块页收掉，底下页签原样恢复。
    public void showModuleFragment() {
        Fragment module = getSupportFragmentManager().findFragmentByTag("module");
        if (module != null && module.isVisible()) return;
        androidx.fragment.app.FragmentTransaction ft = getSupportFragmentManager().beginTransaction().setCustomAnimations(R.anim.xc_frag_fade_in, R.anim.xc_frag_fade_out);
        Fragment primary = getSupportFragmentManager().getPrimaryNavigationFragment();
        if (primary != null && primary != module) ft.hide(primary);
        if (module == null) {
            module = ModuleFragment.newInstance();
            ft.add(mBinding.container.getId(), module, "module");
        } else {
            ft.show(module);
        }
        ft.setPrimaryNavigationFragment(module).setReorderingAllowed(true).commit();
    }

    public boolean isModuleFragmentShown() {
        Fragment module = getSupportFragmentManager().findFragmentByTag("module");
        return module != null && module.isVisible();
    }

    private void closeModuleFragment() {
        Fragment module = getSupportFragmentManager().findFragmentByTag("module");
        if (module == null || !module.isVisible()) return;
        Fragment primary = getSupportFragmentManager().getPrimaryNavigationFragment();
        if (primary == module) {
            // 模块页就是主导航页：交给页签管理器切回当前页签，它会顺手藏起模块页。
            mManager.change(currentPosition);
            return;
        }
        androidx.fragment.app.FragmentTransaction ft = getSupportFragmentManager().beginTransaction().setCustomAnimations(R.anim.xc_frag_fade_in, R.anim.xc_frag_fade_out);
        ft.hide(module);
        if (primary != null) ft.show(primary).setPrimaryNavigationFragment(primary);
        ft.setReorderingAllowed(true).commit();
        if (primary == null) mManager.change(currentPosition);
    }

    /** D-02 页签信号：只在点播页签可见、非网页沉浸态时挂球。
     * 挂载口径：BaseActivity.onResume 调 AiFloatManager.onResume（壳内页签切换不走 onResume，
     * 所以 changeFragment/onWebHomeChromeChanged 里另调 refreshAiFloat 补一次）。 */
    @Override
    public boolean isAiFloatHomePage() {
        if (mManager == null || !mManager.isVisible(0)) return false;
        return !isWebHomeChromeActive() || !WebHomeChrome.IMMERSIVE.equals(getWebHomeChromeMode());
    }


    private void refreshAiFloat() {
        refreshAiFloatNow();
        // 立即刷一次管页签切换，再排队刷一次管状态落定后的恢复。
        try {
            App.post(this::refreshAiFloatNow);
        } catch (Exception ignored) {
        }
    }

    private void refreshAiFloatNow() {
        try {
            AiFloatManager.get().refresh(this);
        } catch (Exception ignored) {
        }
    }

    private void refreshWebHomeChromeLayout() {
        if (mChrome != null) mChrome.refreshLayout();
    }

    private void resetVodChrome() {
        if (mChrome != null) mChrome.setLegacyToolbar(true);
    }

    public void applyWebHomeDefaultChrome(Site site) {
        if (!Setting.isWebHomeFullscreen()) {
            if (mChrome != null) mChrome.setChrome(normalWebHomeChrome());
            return;
        }
        if (mChrome != null) mChrome.applyDefault(WebHomeChromeStartup.resolve(VodConfig.get().getConfig(), site));
    }

    public void setWebHomeChrome(JsonObject payload) {
        if (!Setting.isWebHomeFullscreen()) {
            if (mChrome != null) mChrome.setChrome(normalWebHomeChrome());
            return;
        }
        if (isStartupChrome(payload)) WebHomeChromeStartup.remember(VodConfig.get().getConfig(), VodConfig.get().getHome(), payload);
        if (mChrome != null) mChrome.setChrome(payload);
    }

    private boolean isStartupChrome(JsonObject payload) {
        try {
            return payload != null && payload.has("startup") && payload.get("startup").getAsBoolean();
        } catch (Throwable e) {
            return false;
        }
    }

    public void restoreWebHomeChrome() {
        if (!Setting.isWebHomeFullscreen()) {
            if (mChrome != null) mChrome.setChrome(normalWebHomeChrome());
            return;
        }
        if (mChrome != null) mChrome.restore();
    }

    public void setWebHomeLegacyToolbar(boolean visible) {
        if (!Setting.isWebHomeFullscreen()) {
            if (mChrome != null) mChrome.setChrome(normalWebHomeChrome());
            return;
        }
        if (mChrome != null) mChrome.setLegacyToolbar(visible);
    }

    public void refreshWebHomeChromeState() {
        onWebHomeChromeChanged(getWebHomeChromeMode());
    }

    private JsonObject normalWebHomeChrome() {
        JsonObject object = new JsonObject();
        object.addProperty("mode", "normal");
        return object;
    }

    public void openVod() {
        resetVodChrome();
        setNavigationVisible(true);
        mBinding.navigation.setSelectedItemId(R.id.vod);
        VodFragment fragment = (VodFragment) mManager.getFragment(0);
        if (fragment != null) fragment.openVodHome();
    }

    public String getWebHomeChromeMode() {
        return mChrome == null ? "normal" : mChrome.getMode();
    }

    public WebHomeViewport getWebHomeViewport() {
        return mChrome == null ? WebHomeViewport.EMPTY : mChrome.getViewport();
    }

    @Override
    public boolean isWebHomeChromeActive() {
        return mManager != null && mManager.isVisible(0);
    }

    @Override
    public void onWebHomeChromeChanged(String mode) {
        if (mManager == null) return;
        VodFragment fragment = (VodFragment) mManager.getFragment(0);
        if (fragment != null) fragment.applyWebHomeChrome(mode);
        refreshAiFloatNow();
    }

    @Override
    public void onWebHomeViewportChanged(WebHomeViewport viewport) {
        if (mManager == null) return;
        VodFragment fragment = (VodFragment) mManager.getFragment(0);
        if (fragment != null) fragment.applyWebHomeViewport(viewport);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mChrome != null) mChrome.onConfigurationChanged();
        App.post(this::checkWindowShape, 100);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (mChrome != null) mChrome.onWindowFocusChanged(hasFocus);
    }

    private void checkWindowShape() {
        checkWindowShape(MobileWindow.getWidth(this), MobileWindow.getHeight(this));
    }

    private void checkWindowShape(int width, int height) {
        if (width <= 0 || height <= 0) return;
        boolean wide = width > height;
        if (wideWindow != wide) {
            wideWindow = wide;
            RefreshEvent.home();
        }
    }

    @Override
    protected void onBackInvoked() {
        if (isModuleFragmentShown()) {
            closeModuleFragment();
            return;
        }
        if (mChrome != null && mChrome.consumeBack()) {
            return;
        } else if (mManager.isVisible(2) || mManager.isVisible(3)) {
            change(1);
        } else if (mManager.isVisible(1)) {
            change(0);
        } else if (mManager.canBack(0)) {
            if (PlaybackService.isRunning()) Util.moveToBackground(this);
            else super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        if (mChrome != null) mChrome.destroy();
        // 主题切换 recreate 也走 onDestroy：此时不是真 finish，配置与服务留给新实例接手，
        // 不清 Vod/Live 配置、不停本地服务，否则重建后首页要重拉配置、后台播放也被掐断。
        // 真 finish 时仍按原顺序全清；其它行（OkHttp 清理）与原行为一致。
        if (isFinishing()) LiveConfig.get().clear();
        if (isFinishing()) VodConfig.get().clear();
        // 备份只走手动（2026-11-13 他定）：自动备份全摘——退出不打包、
        // 非真退出销毁不立旗补跑，设置里手动备份/恢复原样。
        OkHttp.get().clear();
        if (isFinishing()) Source.get().exit();
        if (isFinishing()) Server.get().stop();
        super.onDestroy();
    }
}
