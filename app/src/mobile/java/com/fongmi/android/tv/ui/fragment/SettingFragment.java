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
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.AppearanceDialog;
import com.fongmi.android.tv.ui.dialog.ChoiceDialog;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.RestoreDialog;
import com.fongmi.android.tv.ui.dialog.BackupProgressDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.AppVersion;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class SettingFragment extends BaseFragment implements ConfigListener, SiteListener, LiveListener {

    private FragmentSettingBinding mBinding;

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    private HomeActivity getRoot() {
        return (HomeActivity) requireActivity();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        if (getActivity() != null && getActivity().getWindow() != null) {
            getActivity().getWindow().setStatusBarColor(0xFFF5E3B8);
        }
        mBinding.cardConfig.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.ConfigSourceActivity.start(getActivity()));
        mBinding.cardPlayer.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.PlayerSettingsActivity.start(getActivity()));
        mBinding.cardAppearance.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.UiSettingsActivity.start(getActivity()));
        mBinding.cardPlugin.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.PluginActivity.start(getActivity()));
        mBinding.cardDownload.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.DownloadActivity.start(getActivity()));
        mBinding.cardFeatures.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.FeaturesActivity.start(getActivity()));
        mBinding.cardHealth.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.HealthActivity.start(getActivity()));
        mBinding.cardXingchen.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.XingChenSettingsActivity.start(getActivity()));
        updateSubtitles();
    }

    private void setOtherText() {
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

    private void onVod(View view) {
        ConfigDialog.create().vod().show(this);
    }

    private void onLive(View view) {
        ConfigDialog.create().live().show(this);
    }

    private void onWall(View view) {
        ConfigDialog.create().wall().show(this);
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create().vod().edit().show(this);
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create().live().edit().show(this);
        return true;
    }

    private boolean onWallEdit(View view) {
        ConfigDialog.create().wall().edit().show(this);
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create().search().change().show(this);
    }

    private void onLiveHome(View view) {
        LiveDialog.show(this);
    }

    private void onVodHistory(View view) {
        HistoryDialog.create().vod().show(this);
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create().live().show(this);
    }

    private void onPlayer(View view) {
        getRoot().change(2);
    }

    private void onDanmaku(View view) {
        getRoot().change(3);
    }

    private void onAppearance(View view) {
        AppearanceDialog.show(this);
    }


    private void setWallDefault(View view) {
        Setting.putWall(Setting.nextDefaultWall());
        Setting.putWallType(0);
        setWallText();
        ConfigEvent.wall();
    }

    private void setWallRefresh(View view) {
        Setting.putWall(0);
        WallConfig.get().load(getCallback());
    }

    private boolean onWallHistory(View view) {
        HistoryDialog.create().wall().show(this);
        return true;
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
    }

    private void setDoh(View view) {
        ChoiceDialog.showSingle(this, R.string.setting_doh, getDohList(), getDohIndex(), which -> {
            setDoh(VodConfig.get().getDoh().get(which));
        });
    }

    private void setDoh(Doh doh) {
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
    }

    private void onCache(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
            }
        });
    }

    private void onBackup(View view) {
        PermissionUtil.requestFile(this, allGranted -> {
            BackupProgressDialog progress = BackupProgressDialog.open(getParentFragmentManager(), "备份应用数据");
            AppDatabase.backup(new Callback() {
            @Override
            public void success() {
                progress.finish();
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                progress.finish();
                Notify.show(R.string.backup_fail);
            }
            }, progress::update);
        });
    }

    private void onRestore(View view) {
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create().show(requireActivity(), new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                setOtherText();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }));
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
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
            android.content.SharedPreferences sp = requireActivity().getSharedPreferences("xingchen", android.content.Context.MODE_PRIVATE);
            String kernelRaw = sp.getString("player_kernel", "exo");
            String kernel = "mpv".equals(kernelRaw) ? "MPV" : "ExoPlayer";
            String decodeRaw = sp.getString("player_decode", "hard");
            String decode = "soft".equals(decodeRaw) ? "软解" : "硬解";
            float speedVal = sp.getFloat("player_speed", 1.0f);
            String speedStr = speedVal + "x";
            if (mBinding.textPlayerSub != null) {
                mBinding.textPlayerSub.setText(kernel + " · " + decode + " · " + speedStr);
            }
        } catch (Exception e) { e.printStackTrace(); }
        // UI subtitles - isolated
        try {
            android.content.SharedPreferences sp = requireActivity().getSharedPreferences("xingchen", android.content.Context.MODE_PRIVATE);
            int size = 2;
            try { size = com.fongmi.android.tv.setting.PlayerSetting.getSize(); } catch (Exception e) { e.printStackTrace(); }
            String sizeStr = size == 1 ? "小" : size == 3 ? "大" : "中";
            String orient = sp.getString("cover_orient", "portrait");
            String orientStr = "landscape".equals(orient) ? "横屏" : "竖屏";
            if (mBinding.textAppearanceSub != null) {
                mBinding.textAppearanceSub.setText(sizeStr + "封面 · " + orientStr);
            }
        } catch (Exception e) { e.printStackTrace(); }
        // Plugin subtitles - isolated
        try {
            java.io.File pyDir = new java.io.File(requireActivity().getFilesDir(), "plugins/py");
            java.io.File jsDir = new java.io.File(requireActivity().getFilesDir(), "plugins/js");
            int pyCount = 0, jsCount = 0;
            try {
                java.io.File[] pyFiles = pyDir.exists() ? pyDir.listFiles((d, n) -> n.endsWith(".py")) : null;
                if (pyFiles != null) pyCount = pyFiles.length;
            } catch (Exception e) { e.printStackTrace(); }
            try {
                java.io.File[] jsFiles = jsDir.exists() ? jsDir.listFiles((d, n) -> n.endsWith(".js")) : null;
                if (jsFiles != null) jsCount = jsFiles.length;
            } catch (Exception e) { e.printStackTrace(); }
            if (mBinding.textPluginSub != null) {
                mBinding.textPluginSub.setText("PY " + pyCount + " · JS " + jsCount);
            }
        } catch (Exception e) { e.printStackTrace(); }
        // XingChen version subtitle - isolated
        try {
            if (mBinding.textXingchenSub != null) {
                mBinding.textXingchenSub.setText(AppVersion.fullName());
            }
        } catch (Exception e) { e.printStackTrace(); }
        // Download subtitle - isolated (placeholder)
        try {
            if (mBinding.textDownloadSub != null) {
                mBinding.textDownloadSub.setText("暂无下载任务");
            }
        } catch (Exception e) { e.printStackTrace(); }
        // Health subtitle - isolated
        try {
            android.content.SharedPreferences sp = requireActivity().getSharedPreferences("xingchen", android.content.Context.MODE_PRIVATE);
            long time = sp.getLong("health_time", 0);
            if (time > 0 && mBinding.textHealthSub != null) {
                int ok = sp.getInt("health_ok", 0);
                int fail = sp.getInt("health_fail", 0);
                mBinding.textHealthSub.setText("上次检测：" + ok + " 个可用·" + fail + " 个不可用");
            } else if (mBinding.textHealthSub != null) {
                mBinding.textHealthSub.setText("暂未检测");
            }
            if (mBinding.textHealthTime != null) {
                mBinding.textHealthTime.setText(relativeHealthTime(time));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private String relativeHealthTime(long time) {
        if (time <= 0) return "";
        long diff = System.currentTimeMillis() - time;
        long minutes = diff / 60000;
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + " 分钟前";
        long hours = minutes / 60;
        if (hours < 24) return hours + " 小时前";
        long days = hours / 24;
        return days + " 天前";
    }

}
