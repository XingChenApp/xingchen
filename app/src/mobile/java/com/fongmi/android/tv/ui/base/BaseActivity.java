package com.fongmi.android.tv.ui.base;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.ExitContract;

import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public abstract class BaseActivity extends AppCompatActivity {

    protected abstract ViewBinding getBinding();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(Setting.wrapDisplay(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdge();
        // // enableDynamicColor(); // Disabled for wallpaper // Disabled for wallpaper
        super.onCreate(savedInstanceState);
        { getWindow().setStatusBarColor(0x00000000); getWindow().getDecorView().post(() -> { com.xingchen.tv.theme.ThemeManager.get().apply(this); }); }
        setContentView(getBinding().getRoot());
        EventBus.getDefault().register(this);
        initView(savedInstanceState);
        setBackCallback();
        initEvent();
        // 精修 P4：全 App 页面进出统一渐入渐出（只用 alpha，200ms）
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
    }

    @Override
    public void finish() {
        super.finish();
        // 精修 P4：finish 路径统一淡出；recreate()/正常返回都走这里，视觉一致
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
    }


    protected FragmentActivity getActivity() {
        return this;
    }



    protected void initView(Bundle savedInstanceState) {
    }

    protected void initEvent() {
    }

    protected boolean isVisible(View view) {
        // 淡出途中（200ms 内 alpha 在降、visibility 仍 VISIBLE）视为未显示：
        // 点屏唤栏的判定走 visibility 老口径会把唤栏点击吞进隐藏分支（深挖渐入渐出①）
        return view.getVisibility() == View.VISIBLE && !com.fongmi.android.tv.utils.XcFadeTransition.isFadingOut(view);
    }

    protected boolean isGone(View view) {
        return view.getVisibility() == View.GONE;
    }

    protected void setPadding(ViewGroup layout) {
        setPadding(layout, false);
    }

    protected void setPadding(ViewGroup layout, boolean leftOnly) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        DisplayCutout cutout = ResUtil.getDisplay(this).getCutout();
        if (cutout == null) return;
        int top = cutout.getSafeInsetTop();
        int left = cutout.getSafeInsetLeft();
        int right = cutout.getSafeInsetRight();
        int bottom = cutout.getSafeInsetBottom();
        int padding = left | right | top | bottom;
        layout.setPadding(padding, 0, leftOnly ? 0 : padding, 0);
    }

    protected void noPadding(ViewGroup layout) {
        layout.setPadding(0, 0, 0, 0);
    }

    private void setBackCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBackInvoked();
            }
        });
    }

    /**
     * 状态栏/导航栏图标颜色：默认深色图标（浅色栏）。App 绝大多数页是浅色底（插件页/设置页/列表页/音乐各页/海阔浅色页），
     * 原先 BaseActivity 统一强制白色图标，浅底上完全隐形＝看上去“状态栏没了”（海阔三页此前只能各自再调 light 盖掉）。
     * 顶是深色的页（首页壁纸、播放页封面、随心听沉浸、节点列表/管理、线路政策、视频/直播）重写本方法返回 false，用回白色图标。
     */
    protected boolean useDarkSystemIcons() {
        return true;
    }

    private void enableEdgeToEdge() {
        SystemBarStyle style = useDarkSystemIcons() ? SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) : SystemBarStyle.dark(Color.TRANSPARENT);
        EdgeToEdge.enable(this, style, style);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    private void enableDynamicColor() {
        int color = Setting.getDynamicColor();
        if (color != 0) DynamicColors.applyToActivityIfAvailable(this, new DynamicColorsOptions.Builder().setContentBasedSource(color).build());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSubscribe(Object o) {
        if (o instanceof RefreshEvent event && event.getType() == RefreshEvent.Type.LANGUAGE) recreate();
    }

    protected void onBackInvoked() {
        // 退出契约（他 2026-11-13 定：退完层回点播首页，只有点播首页
        // 本身按返回才退出）：子类覆写处理完自己的层、落到本缺省路
        // 时，先过 ExitContract 的任务根自救再退——本页已成任务根时
        // 直接 finish 会穿到手机桌面。实现只此一处（utils.ExitContract），
        // 各页不许自备。
        ExitContract.rescueIfTaskRoot(this);
        finish();
    }

    @Override
    protected void onResume() {
        { getWindow().getDecorView().post(() -> { com.xingchen.tv.theme.ThemeManager.get().apply(this); }); } // xc_onResume
        super.onResume();
        Updater.create().resume(this);
        try {
            com.fongmi.android.tv.ai.AiFloatManager.get().onResume(this);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onPause() {
        try {
            com.fongmi.android.tv.ai.AiFloatManager.get().onPause();
        } catch (Exception ignored) {
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }
}
