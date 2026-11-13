package com.fongmi.android.tv.ui.activity;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.web.WebHomeChrome;
import com.fongmi.android.tv.web.WebHomeChromeOptions;
import com.fongmi.android.tv.web.WebHomeViewport;
import com.google.gson.JsonObject;

final class WebHomeChromeController {

    private static final String STATE_MODE = "webHomeChromeMode";
    private static final String STATE_PREVIOUS_MODE = "webHomeChromePreviousMode";
    private static final String STATE_STATUS_BAR_STYLE = "webHomeChromeStatusBarStyle";
    private static final String STATE_NAVIGATION_BAR_STYLE = "webHomeChromeNavigationBarStyle";
    private static final String STATE_RESTORE_AFFORDANCE = "webHomeChromeRestoreAffordance";
    private static final String STATE_TOP_SCRIM = "webHomeChromeTopScrim";
    private static final String STATE_BOTTOM_SCRIM = "webHomeChromeBottomScrim";

    interface Host {

        boolean isWebHomeChromeActive();

        void onWebHomeChromeChanged(String mode);

        void onWebHomeViewportChanged(WebHomeViewport viewport);
    }

    private final HomeActivity activity;
    private final ActivityHomeBinding binding;
    private final Host host;
    private final int navigationBaseHeight;
    private WindowInsetsCompat insets;
    private WebHomeChromeOptions options;
    private WebHomeViewport viewport;
    private String mode;
    private String previousMode;
    private int safeBottomMax;

    WebHomeChromeController(HomeActivity activity, ActivityHomeBinding binding, Host host, Bundle savedInstanceState, JsonObject startupChrome) {
        this.activity = activity;
        this.binding = binding;
        this.host = host;
        this.navigationBaseHeight = binding.navigation.getLayoutParams().height;
        if (!Setting.isWebHomeFullscreen()) {
            this.mode = WebHomeChrome.NORMAL;
            this.previousMode = WebHomeChrome.NORMAL;
            this.options = WebHomeChromeOptions.normal();
        } else if (savedInstanceState == null && startupChrome != null) {
            this.options = WebHomeChromeOptions.from(startupChrome, WebHomeChrome.EDGE);
            this.mode = WebHomeChrome.normalize(options.mode, WebHomeChrome.EDGE);
            this.previousMode = WebHomeChrome.IMMERSIVE.equals(mode) ? WebHomeChrome.NORMAL : mode;
        } else {
            this.mode = savedInstanceState == null ? WebHomeChrome.NORMAL : WebHomeChrome.normalize(savedInstanceState.getString(STATE_MODE), WebHomeChrome.NORMAL);
            this.previousMode = savedInstanceState == null ? WebHomeChrome.NORMAL : WebHomeChrome.normalize(savedInstanceState.getString(STATE_PREVIOUS_MODE), WebHomeChrome.NORMAL);
            // 重建恢复：按 save() 存下的样式逐字段重建，不再 from(null, mode) 丢回默认
            //（站点给的 scrim 色／状态栏与导航栏样式／恢复入口样式都得跟回来）。
            // 无存档（savedInstanceState 为 null）才走原来的默认构建。
            this.options = savedInstanceState == null ? WebHomeChromeOptions.from(null, mode) : WebHomeChromeOptions.fromSaved(mode,
                    savedInstanceState.getString(STATE_STATUS_BAR_STYLE),
                    savedInstanceState.getString(STATE_NAVIGATION_BAR_STYLE),
                    savedInstanceState.getString(STATE_RESTORE_AFFORDANCE),
                    savedInstanceState.getInt(STATE_TOP_SCRIM, Color.TRANSPARENT),
                    savedInstanceState.getInt(STATE_BOTTOM_SCRIM, Color.TRANSPARENT));
        }
        this.viewport = WebHomeViewport.EMPTY.withChrome(mode, isImmersive());
        init();
    }

    private void init() {
        binding.getRoot().setFitsSystemWindows(false);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            insets = windowInsets;
            applyLayout();
            dispatchViewport();
            return windowInsets;
        });
        apply(options);
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    void save(Bundle outState) {
        outState.putString(STATE_MODE, mode);
        outState.putString(STATE_PREVIOUS_MODE, previousMode);
        // 样式一并存档：只存 mode 的话重建后 scrim 色／栏样式全丢回默认（F-18）。
        if (options != null) {
            outState.putString(STATE_STATUS_BAR_STYLE, options.statusBarStyle);
            outState.putString(STATE_NAVIGATION_BAR_STYLE, options.navigationBarStyle);
            outState.putString(STATE_RESTORE_AFFORDANCE, options.restoreAffordance);
            outState.putInt(STATE_TOP_SCRIM, options.topScrim);
            outState.putInt(STATE_BOTTOM_SCRIM, options.bottomScrim);
        }
    }

    void applyDefault(Site site) {
        apply(WebHomeChromeOptions.fromSite(site));
    }

    void applyDefault(JsonObject chrome) {
        apply(WebHomeChromeOptions.from(chrome, WebHomeChrome.EDGE));
    }

    void setChrome(JsonObject payload) {
        apply(WebHomeChromeOptions.from(payload, mode));
    }

    void setLegacyToolbar(boolean visible) {
        apply(visible ? WebHomeChromeOptions.normal() : WebHomeChromeOptions.legacyImmersive());
    }

    void restore() {
        apply(WebHomeChromeOptions.from(null, previousMode));
    }

    void refreshLayout() {
        applySystemBars();
        applyCutoutMode();
        applyLayout();
        dispatchViewport();
    }

    boolean consumeBack() {
        if (!isActive() || !isImmersive()) return false;
        if (WebHomeChromeOptions.RESTORE_NONE.equals(options.restoreAffordance)) return false;
        restore();
        return true;
    }

    void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && isActive() && isImmersive()) Util.hideSystemUI(activity);
    }

    void onConfigurationChanged() {
        safeBottomMax = 0;
        ViewCompat.requestApplyInsets(binding.getRoot());
        dispatchViewport();
    }

    void destroy() {
        if (isImmersive()) Util.showSystemUI(activity);
    }

    String getMode() {
        return mode;
    }

    WebHomeViewport getViewport() {
        return viewport;
    }

    private void apply(WebHomeChromeOptions next) {
        if (!Setting.isWebHomeFullscreen()) next = WebHomeChromeOptions.normal();
        String nextMode = WebHomeChrome.normalize(next.mode, WebHomeChrome.NORMAL);
        if (WebHomeChrome.IMMERSIVE.equals(nextMode) && !WebHomeChrome.IMMERSIVE.equals(mode)) previousMode = mode;
        if (!WebHomeChrome.IMMERSIVE.equals(nextMode)) previousMode = nextMode;
        mode = nextMode;
        options = next;
        refreshLayout();
        host.onWebHomeChromeChanged(mode);
    }

    private void applySystemBars() {
        String effectiveMode = effectiveMode();
        WebHomeChromeOptions effectiveOptions = isActive() ? options : WebHomeChromeOptions.normal();
        Window window = activity.getWindow();
        window.setStatusBarColor(effectiveOptions.topScrim);
        window.setNavigationBarColor(effectiveOptions.bottomScrim);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(useDarkIcons(effectiveOptions.statusBarStyle));
        controller.setAppearanceLightNavigationBars(useDarkIcons(effectiveOptions.navigationBarStyle));
        if (WebHomeChrome.IMMERSIVE.equals(effectiveMode)) Util.hideSystemUI(activity);
        else Util.showSystemUI(activity);
    }

    private void applyCutoutMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        params.layoutInDisplayCutoutMode = WebHomeChrome.hidesNativeChrome(effectiveMode()) ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        activity.getWindow().setAttributes(params);
    }

    private void applyLayout() {
        boolean active = isActive();
        boolean normal = !active || WebHomeChrome.NORMAL.equals(mode);
        // 底栏显隐只走根页唯一写口（2026-11-09 119 真机复报
        // 「底栏还是在」的根）：本类旧形直写 visibility，绕过
        // setNavigationVisible 里的短剧舞台闸——根页落闸时自带
        // 的 requestApplyInsets 又经本类窗口边距监听回进
        // applyLayout，当场把底栏直写回 VISIBLE 压在舞台上。
        // 改走写口后：舞台在场恒隐、不在场按 normal 原判；写口
        // 同时维护 container 的 ABOVE 规则，直写还会让规则与
        // 显隐脱同步。
        activity.setNavigationVisible(normal);
        WebHomeViewport current = buildViewport();
        int top = normal ? current.getSafeTop() : 0;
        // 底栏高度永不加底部安全区：那是上游"贴底栏"的延伸语义（栏贴屏幕底边、
        // 高度要吞掉系统导航区）。星辰是悬浮胶囊栏，整栏避让已由 HomeActivity
        // 的边距监听统一处理（基础边距+导航栏高）——这里再把 safeBottom 加进
        // 栏高就是重复计算，胶囊被撑高、图标顶在上方、下方留空（真机实锤）。
        // 1.3.20 曾用 active&&NORMAL 做门控，但 active 的口径是"点播页可见"、
        // 普通首页下恰好恒真，等于没改（潇洒哥 1.3.20 真机复验仍撑高）。
        // 网页视口上报（buildViewport/safeBottomMax）不受影响，页面自己读安全区。
        int bottom = 0;
        binding.container.setPadding(0, top, 0, 0);
        binding.navigation.setPadding(0, 0, 0, bottom);
        ViewGroup.LayoutParams params = binding.navigation.getLayoutParams();
        int height = navigationBaseHeight + bottom;
        if (params.height != height) {
            params.height = height;
            binding.navigation.setLayoutParams(params);
        }
        RelativeLayout.LayoutParams container = (RelativeLayout.LayoutParams) binding.container.getLayoutParams();
        if (binding.navigation.getVisibility() == View.VISIBLE) container.addRule(RelativeLayout.ABOVE, binding.navigation.getId());
        else container.removeRule(RelativeLayout.ABOVE);
        binding.container.setLayoutParams(container);
    }

    private void dispatchViewport() {
        viewport = buildViewport();
        host.onWebHomeViewportChanged(viewport);
    }

    private WebHomeViewport buildViewport() {
        String mode = effectiveMode();
        WebHomeViewport current = WebHomeViewport.from(insets, mode, safeBottomMax);
        safeBottomMax = Math.max(safeBottomMax, current.getSafeBottom());
        return WebHomeViewport.from(insets, mode, safeBottomMax);
    }

    private boolean isActive() {
        return host.isWebHomeChromeActive();
    }

    private String effectiveMode() {
        return isActive() ? mode : WebHomeChrome.NORMAL;
    }

    private boolean isImmersive() {
        return WebHomeChrome.IMMERSIVE.equals(mode);
    }

    private boolean useDarkIcons(String style) {
        if (WebHomeChromeOptions.STYLE_DARK.equals(style)) return true;
        if (WebHomeChromeOptions.STYLE_LIGHT.equals(style)) return false;
        int mask = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask != Configuration.UI_MODE_NIGHT_YES;
    }
}
