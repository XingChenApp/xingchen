package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
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
        initThemeSection();
        initUiStyleSection();
        initGlassSection();
        initWallpaperSection();
        updateVisibility();
    }

    private void initThemeSection() {
        binding.themeLight.setOnClickListener(v -> setTheme("light"));
        binding.themeDark.setOnClickListener(v -> setTheme("dark"));
        binding.themeSystem.setOnClickListener(v -> setTheme("system"));
        updateThemeUI();
    }

    private void setTheme(String theme) {
        getSharedPreferences("xingchen", MODE_PRIVATE).edit().putString("theme_mode", theme).apply();
        updateThemeUI();
    }

    private void updateThemeUI() {
        String theme = getSharedPreferences("xingchen", MODE_PRIVATE).getString("theme_mode", "light");
        binding.themeLight.setSelected("light".equals(theme));
        binding.themeDark.setSelected("dark".equals(theme));
        binding.themeSystem.setSelected("system".equals(theme));
    }

    private void initUiStyleSection() {
        binding.uiNormal.setOnClickListener(v -> setUiStyle(XingChenTheme.UI_NORMAL));
        binding.uiGlass.setOnClickListener(v -> setUiStyle(XingChenTheme.UI_GLASS));
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
        binding.uiNormal.setSelected(XingChenTheme.UI_NORMAL.equals(style));
        binding.uiGlass.setSelected(XingChenTheme.UI_GLASS.equals(style));
    }

    private void initGlassSection() {
        int alpha = ThemeManager.get().getTheme().glassAlpha;
        binding.glassSeek.setProgress(alpha);
        binding.glassValue.setText(alpha + "%");
        binding.glassSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                binding.glassValue.setText(progress + "%");
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
        binding.wpDefault.setOnClickListener(v -> setWallpaper(XingChenTheme.WP_BUILTIN, "shanjian"));
        binding.wpLocal.setOnClickListener(v -> pickLocalWallpaper());
        binding.wpUrl.setOnClickListener(v -> inputUrlWallpaper());
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
        String value = ThemeManager.get().getTheme().wallpaperValue;
        binding.wpDefault.setSelected(XingChenTheme.WP_BUILTIN.equals(type));
        binding.wpLocal.setSelected(XingChenTheme.WP_LOCAL.equals(type));
        binding.wpUrl.setSelected(XingChenTheme.WP_URL.equals(type));
    }

    private void updateVisibility() {
        String style = ThemeManager.get().getTheme().uiStyle;
        boolean isGlass = XingChenTheme.UI_GLASS.equals(style);
        binding.themeSection.setVisibility(isGlass ? View.GONE : View.VISIBLE);
        binding.wallpaperSection.setVisibility(isGlass ? View.VISIBLE : View.GONE);
        binding.glassSection.setVisibility(isGlass ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateVisibility();
        updateUiStyleUI();
        updateWallpaperUI();
    }
}
