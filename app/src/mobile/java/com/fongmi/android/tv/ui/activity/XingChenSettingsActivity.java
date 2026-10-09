package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.databinding.ActivityXingchenSettingsBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.AboutDialog;
import com.fongmi.android.tv.ui.dialog.ChoiceDialog;
import com.fongmi.android.tv.utils.AppVersion;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.XingChenDoh;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class XingChenSettingsActivity extends BaseActivity {

    private ActivityXingchenSettingsBinding binding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, XingChenSettingsActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityXingchenSettingsBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.tvVersionSub.setText(AppVersion.fullName());
        binding.cardVersion.setOnClickListener(v -> AboutDialog.show(this, () -> Updater.create().force().start(this)));
        updateProxySub();
        binding.cardProxy.setOnClickListener(v -> ProxyActivity.start(this));
        updateDohSub();
        binding.cardDoh.setOnClickListener(v -> ChoiceDialog.showSingle(getSupportFragmentManager(), "DoH", XingChenDoh.NAMES, XingChenDoh.getIndex(this), which -> {
            XingChenDoh.setIndex(this, which);
            updateDohSub();
        }));
        refreshCacheSize();
        binding.cardCache.setOnClickListener(v -> FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                refreshCacheSize();
                Notify.show("缓存已清理");
            }
        }));
        binding.cardBackup.setOnClickListener(v -> doBackup());
        binding.tvRestore.setOnClickListener(v -> doRestore());
    }

    private void updateProxySub() {
        binding.tvProxySub.setText(Setting.isShellProxy() ? "已启用" : "未启用");
    }

    private void updateDohSub() {
        binding.tvDohSub.setText(XingChenDoh.getName(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateProxySub();
        updateDohSub();
    }

    private void refreshCacheSize() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                binding.tvCacheSize.setText(result);
            }
        });
    }

    private File backupDir() {
        return new File(getFilesDir(), "xc_backup");
    }

    private void doBackup() {
        try {
            File dir = backupDir();
            deleteRecursive(dir);
            if (!dir.mkdirs()) return;
            File sp = new File(getApplicationInfo().dataDir + "/shared_prefs/xingchen.xml");
            if (sp.exists()) copyFile(sp, new File(dir, "xingchen.xml"));
            File plugins = new File(getFilesDir(), "plugins");
            if (plugins.exists()) copyRecursive(plugins, new File(dir, "plugins"));
            Notify.show("备份完成");
        } catch (Exception e) {
            e.printStackTrace();
            Notify.show("备份失败");
        }
    }

    private void doRestore() {
        try {
            File dir = backupDir();
            if (!dir.exists()) {
                Notify.show("没有备份");
                return;
            }
            File spBak = new File(dir, "xingchen.xml");
            if (spBak.exists()) copyFile(spBak, new File(getApplicationInfo().dataDir + "/shared_prefs/xingchen.xml"));
            File plBak = new File(dir, "plugins");
            if (plBak.exists()) {
                deleteRecursive(new File(getFilesDir(), "plugins"));
                copyRecursive(plBak, new File(getFilesDir(), "plugins"));
            }
            XingChenDoh.apply(this);
            updateDohSub();
            Notify.show("恢复完成");
        } catch (Exception e) {
            e.printStackTrace();
            Notify.show("恢复失败");
        }
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static void copyRecursive(File src, File dst) throws Exception {
        if (src.isDirectory()) {
            if (!dst.exists()) dst.mkdirs();
            File[] files = src.listFiles();
            if (files != null) for (File f : files) copyRecursive(f, new File(dst, f.getName()));
        } else {
            copyFile(src, dst);
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) for (File child : files) deleteRecursive(child);
        }
        f.delete();
    }
}
