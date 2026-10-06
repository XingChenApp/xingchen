package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.fongmi.android.tv.R;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.databinding.ActivityUiSettingsBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.xingchen.tv.theme.ThemeManager;
import com.xingchen.tv.theme.XingChenTheme;

public class UiSettingsActivity extends BaseActivity {
    private ActivityUiSettingsBinding binding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, UiSettingsActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityUiSettingsBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        initUiStyleSection();
        initGlassSection();
        initWallpaperSection();
        updateVisibility();
    }

    private void setSegSelected(TextView tv, boolean selected) {
        if (selected) {
            tv.setBackgroundResource(R.drawable.seg_selected);
            tv.setTextColor(0xFFFFFFFF);
        } else {
            tv.setBackgroundResource(R.drawable.seg_unselected);
            tv.setTextColor(0xFF333333);
        }
    }

    private void initUiStyleSection() {
        binding.segUiNormal.setOnClickListener(v -> setUiStyle(XingChenTheme.UI_NORMAL));
        binding.segUiGlass.setOnClickListener(v -> setUiStyle(XingChenTheme.UI_GLASS));
        updateUiStyleUI();
    }

    private void setUiStyle(String style) {
        ThemeManager.get().setUiStyle(this, style);
        ThemeManager.get().apply(this);
        updateUiStyleUI();
        updateVisibility();
    }

    private void updateUiStyleUI() {
        String style = ThemeManager.get().getTheme().uiStyle;
        boolean isNormal = XingChenTheme.UI_NORMAL.equals(style);
        setSegSelected(binding.segUiNormal, isNormal);
        setSegSelected(binding.segUiGlass, !isNormal);
    }

    private void initGlassSection() {
        int alpha = ThemeManager.get().getTheme().glassAlpha;
        binding.seekAlpha.setProgress(alpha);
        binding.tvAlphaValue.setText(alpha + "%");
        binding.tvAlphaDesc.setText("当前 " + alpha + "%，越往右越通透");
        binding.seekAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                binding.tvAlphaValue.setText(progress + "%");
                binding.tvAlphaDesc.setText("当前 " + progress + "%，越往右越通透");
                if (fromUser) {
                    ThemeManager.get().getTheme().glassAlpha = progress;
                    ThemeManager.get().getTheme().save(UiSettingsActivity.this);
                    ThemeManager.get().apply(UiSettingsActivity.this);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void initWallpaperSection() {
        binding.segWpDefault.setOnClickListener(v -> setWallpaper(XingChenTheme.WP_BUILTIN, "shanjian"));
        binding.segWpLocal.setOnClickListener(v -> pickLocalWallpaper());
        binding.segWpUrl.setOnClickListener(v -> inputUrlWallpaper());
        updateWallpaperUI();
    }

    private void setWallpaper(String type, String value) {
        ThemeManager.get().setWallpaper(this, type, value);
        ThemeManager.get().apply(this);
        updateWallpaperUI();
    }

    private void pickLocalWallpaper() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, 1001);
    }

    private void inputUrlWallpaper() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("输入图片URL");
        final android.widget.EditText input = new android.widget.EditText(this);
        builder.setView(input);
        builder.setPositiveButton("确定", (d, w) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                downloadAndSetWallpaper(url);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void downloadAndSetWallpaper(String url) {
        new Thread(() -> {
            try {
                java.net.URL u = new java.net.URL(url);
                java.io.InputStream in = u.openStream();
                java.io.File outFile = new java.io.File(getFilesDir(), "wallpaper_url.jpg");
                java.io.FileOutputStream out = new java.io.FileOutputStream(outFile);
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                out.close();
                in.close();
                runOnUiThread(() -> setWallpaper(XingChenTheme.WP_URL, outFile.getAbsolutePath()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            try {
                android.net.Uri uri = data.getData();
                java.io.InputStream in = getContentResolver().openInputStream(uri);
                java.io.File outFile = new java.io.File(getFilesDir(), "wallpaper_local.jpg");
                java.io.FileOutputStream out = new java.io.FileOutputStream(outFile);
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                out.close();
                in.close();
                setWallpaper(XingChenTheme.WP_LOCAL, outFile.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateWallpaperUI() {
        String type = ThemeManager.get().getTheme().wallpaperType;
        setSegSelected(binding.segWpDefault, XingChenTheme.WP_BUILTIN.equals(type));
        setSegSelected(binding.segWpLocal, XingChenTheme.WP_LOCAL.equals(type));
        setSegSelected(binding.segWpUrl, XingChenTheme.WP_URL.equals(type));
        String desc = "当前：";
        if (XingChenTheme.WP_BUILTIN.equals(type)) desc += "默认壁纸";
        else if (XingChenTheme.WP_LOCAL.equals(type)) desc += "本地壁纸";
        else if (XingChenTheme.WP_URL.equals(type)) desc += "网络壁纸";
        else desc += "默认壁纸";
        binding.tvWpDesc.setText(desc);
    }

    private void updateVisibility() {
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUiStyleUI();
        updateWallpaperUI();
    }
}
