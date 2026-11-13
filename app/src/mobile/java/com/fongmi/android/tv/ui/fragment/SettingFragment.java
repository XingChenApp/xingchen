package com.fongmi.android.tv.ui.fragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.FragmentSettingBinding;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.AppVersion;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class SettingFragment extends BaseFragment implements ConfigListener, SiteListener, LiveListener {

    private FragmentSettingBinding mBinding;

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        mBinding.cardConfig.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.ConfigSourceActivity.start(getActivity()));
        mBinding.cardPlayer.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.PlayerSettingsActivity.start(getActivity()));
        mBinding.cardAppearance.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.UiSettingsActivity.start(getActivity()));
        mBinding.cardPlugin.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.PluginActivity.start(getActivity()));
        mBinding.cardDownload.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.DownloadActivity.start(getActivity()));
        mBinding.cardFeatures.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.FeaturesActivity.start(getActivity()));
        // 实验室入口：原「源健康检测」卡的位置与样式保留，文案换实验室（2026-10-18 定）
        mBinding.cardHealth.setOnClickListener(v -> com.fongmi.android.tv.ui.lab.LabHomeActivity.start(getActivity()));
        mBinding.cardXingchen.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.XingChenSettingsActivity.start(getActivity()));
        updateSubtitles();
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
            }
        });
    }

    @Override
    protected void initEvent() {
    }

    @Override
    public void setConfig(Config config) {
        if (config == null) return;
        String url = config.getUrl();
        if (!TextUtils.isEmpty(url) && url.startsWith("file")) {
            requireView().post(() -> PermissionUtil.requestFile(this, allGranted -> load(config)));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                break;
            case 2:
                Setting.putWall(0);
                WallConfig.load(config, getCallback());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() == ConfigEvent.Type.WALL) {
            setWallText();
            return;
        }
        if (event.type() != ConfigEvent.Type.COMMON) return;
        setWallText();
    }

    private void setWallText() {
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) return;
        setCacheText();
        // F-20：壳内 tab 切回不走 onResume，下载卡/插件数等副文案只靠 onResume 刷会停在旧值；
        // 照 SettingPlayerFragment/SettingDanmakuFragment 的 onHiddenChanged(!hidden) 回读口径一并刷。
        // setCacheText 的回调仍是空实现（存量空转，与本次刷新无关，按现状保留）。
        if (mBinding != null) updateSubtitles();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSubtitles();
    }

    private void updateSubtitles() {
        // Player subtitles - isolated
        try {
            String kernel = com.fongmi.android.tv.setting.PlayerSetting.getPlayer() == com.fongmi.android.tv.setting.PlayerSetting.MPV ? "MPV" : "ExoPlayer";
            String decode = com.fongmi.android.tv.setting.PlayerSetting.isVideoPrefer() ? "硬解" : "软解";
            float speedVal = com.fongmi.android.tv.setting.PlayerSetting.getDefaultSpeed();
            String speedStr = speedVal + "x";
            if (mBinding.textPlayerSub != null) {
                mBinding.textPlayerSub.setText(kernel + " · " + decode + " · " + speedStr);
            }
        } catch (Exception e) { android.util.Log.e("SettingFragment", "refresh player subtitle failed", e); }
        // UI subtitles - isolated
        try {
            android.content.SharedPreferences sp = requireActivity().getSharedPreferences("xingchen", android.content.Context.MODE_PRIVATE);
            int size = -1;
            try { size = com.fongmi.android.tv.setting.PlayerSetting.getSize(); } catch (Exception e) { android.util.Log.e("SettingFragment", "read cover size failed", e); }
            String sizeStr = size == 0 ? "小" : size == 3 ? "特大" : size == 2 ? "大" : "中";
            String orient = sp.getString("cover_orient", "portrait");
            String orientStr = "landscape".equals(orient) ? "横屏" : "竖屏";
            if (size >= 0 && mBinding.textAppearanceSub != null) {
                mBinding.textAppearanceSub.setText(sizeStr + "封面 · " + orientStr);
            }
        } catch (Exception e) { android.util.Log.e("SettingFragment", "refresh appearance subtitle failed", e); }
        // Plugin subtitles - isolated
        try {
            java.io.File pyDir = new java.io.File(requireActivity().getFilesDir(), "plugins/py");
            java.io.File jsDir = new java.io.File(requireActivity().getFilesDir(), "plugins/js");
            java.io.File htmlDir = new java.io.File(requireActivity().getFilesDir(), "plugins/html");
            int pyCount = -1, jsCount = -1, htmlCount = -1;
            try {
                java.io.File[] pyFiles = pyDir.exists() ? pyDir.listFiles((d, n) -> n.endsWith(".py")) : null;
                pyCount = pyFiles != null ? pyFiles.length : 0;
            } catch (Exception e) { android.util.Log.e("SettingFragment", "count py plugins failed", e); }
            try {
                java.io.File[] jsFiles = jsDir.exists() ? jsDir.listFiles((d, n) -> n.endsWith(".js")) : null;
                jsCount = jsFiles != null ? jsFiles.length : 0;
            } catch (Exception e) { android.util.Log.e("SettingFragment", "count js plugins failed", e); }
            try {
                java.io.File[] htmlFiles = htmlDir.exists() ? htmlDir.listFiles((d, n) -> n.endsWith(".html")) : null;
                htmlCount = htmlFiles != null ? htmlFiles.length : 0;
            } catch (Exception e) { android.util.Log.e("SettingFragment", "count html plugins failed", e); }
            if (pyCount >= 0 && jsCount >= 0 && htmlCount >= 0 && mBinding.textPluginSub != null) {
                mBinding.textPluginSub.setText("PY " + pyCount + " · JS " + jsCount + " · HTML " + htmlCount);
            }
        } catch (Exception e) { android.util.Log.e("SettingFragment", "refresh plugin subtitle failed", e); }
        // XingChen version subtitle - isolated
        try {
            if (mBinding.textXingchenSub != null) {
                mBinding.textXingchenSub.setText(AppVersion.fullName());
            }
        } catch (Exception e) { android.util.Log.e("SettingFragment", "refresh version subtitle failed", e); }
        // Download subtitle - isolated：接 DownloadManager 现成任务表（DownloadActivity 同源），
        // onResume 时 updateSubtitles 重跑，进出下载页后数字会刷新。任务表只在进程内（重启后清空），与下载页口径一致。
        try {
            if (mBinding.textDownloadSub != null) {
                java.util.List<com.fongmi.android.tv.utils.download.DownloadTask> tasks =
                        com.fongmi.android.tv.utils.download.DownloadManager.get().getTasks();
                int active = 0;
                int completed = 0;
                int failed = 0;
                for (com.fongmi.android.tv.utils.download.DownloadTask t : tasks) {
                    int st = t.getStatus();
                    if (st == com.fongmi.android.tv.utils.download.DownloadTask.STATUS_COMPLETED) completed++;
                    else if (st == com.fongmi.android.tv.utils.download.DownloadTask.STATUS_ERROR) failed++;
                    else active++;
                }
                // A4：ERROR 不计入完成，全是 ERROR 时不许显示“已完成 N 个”，失败数另计展示。
                // A 类残留：有进行中任务时旧文案的“共 N 个”是全部任务数（含失败数），
                // 失败任务既不算进行中也不算完成，混进总数会误导；改成完成数口径。
                if (tasks.isEmpty()) mBinding.textDownloadSub.setText("暂无下载任务");
                else if (active > 0) mBinding.textDownloadSub.setText("进行中 " + active + " 个，已完成 " + completed + " 个" + (failed > 0 ? "，失败 " + failed + " 个" : ""));
                else if (completed > 0) mBinding.textDownloadSub.setText("已完成 " + completed + " 个" + (failed > 0 ? "，失败 " + failed + " 个" : ""));
                else mBinding.textDownloadSub.setText("下载失败 " + failed + " 个");
            }
        } catch (Exception e) { android.util.Log.e("SettingFragment", "refresh download subtitle failed", e); }
        // Features subtitle - TMDB 配置态（与布局缺省文案同口径）
        try {
            if (mBinding.textFeaturesSub != null) {
                String key = com.fongmi.android.tv.ui.activity.TmdbActivity.getApiKey(requireContext());
                mBinding.textFeaturesSub.setText(key == null || key.isEmpty() ? "TMDB 未配置" : "TMDB 已配置");
            }
        } catch (Exception e) { android.util.Log.e("SettingFragment", "refresh tmdb subtitle failed", e); }
        // Lab subtitle：里面几个服务、几个运行中（原健康检测文案随入口一起换掉）
        try {
            if (mBinding.textHealthSub != null) {
                java.util.List<com.fongmi.android.tv.ui.lab.LabRegistry.Svc> svcs =
                        com.fongmi.android.tv.ui.lab.LabRegistry.get(requireActivity()).visibleServices();
                int running = 0;
                for (com.fongmi.android.tv.ui.lab.LabRegistry.Svc s : svcs) {
                    if (com.fongmi.android.tv.ui.lab.LabRunner.isRunning(requireActivity(), s.id)) running++;
                }
                mBinding.textHealthSub.setText(svcs.isEmpty() ? "装服务、跑服务，一点就用"
                        : "里面 " + svcs.size() + " 个服务、" + running + " 个运行中");
            }
            if (mBinding.textHealthTime != null) {
                mBinding.textHealthTime.setText("");
            }
        } catch (Exception e) { android.util.Log.e("SettingFragment", "refresh lab subtitle failed", e); }
    }

}
