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
        initThemeSection();
        initCoverSizeSection();
        initOrientSection();
        initRatioSection();
        initUiStyleSection();
        initGlassSection();
        initWallpaperSection();
        updateVisibility();
    }

    private String getPref(String key, String def) {
        return getSharedPreferences("xingchen", MODE_PRIVATE).getString(key, def);
    }

    private void setPref(String key, String value) {
        getSharedPreferences("xingchen", MODE_PRIVATE).edit().putString(key, value).apply();
    }

    private void initThemeSection() {
        binding.segThemeLight.setOnClickListener(v -> setPrefAndUpdate("theme_mode", "light", this::updateThemeUI));
        binding.segThemeDark.setOnClickListener(v -> setPrefAndUpdate("theme_mode", "dark", this::updateThemeUI));
        binding.segThemeSystem.setOnClickListener(v -> setPrefAndUpdate("theme_mode", "system", this::updateThemeUI));
        updateThemeUI();
    }

    private void setPrefAndUpdate(String key, String value, Runnable update) {
        setPref(key, value);
        update.run();
    }

    private void updateThemeUI() {
        String theme = getPref("theme_mode", "light");
        setSegSelected(binding.segThemeLight, "light".equals(theme));
        setSegSelected(binding.segThemeDark, "dark".equals(theme));
        setSegSelected(binding.segThemeSystem, "system".equals(theme));
    }

    private void initCoverSizeSection() {
        binding.segCoverSmall.setOnClickListener(v -> setPrefAndUpdate("cover_size", "small", this::updateCoverSizeUI));
        binding.segCoverMedium.setOnClickListener(v -> setPrefAndUpdate("cover_size", "medium", this::updateCoverSizeUI));
        binding.segCoverLarge.setOnClickListener(v -> setPrefAndUpdate("cover_size", "large", this::updateCoverSizeUI));
        updateCoverSizeUI();
    }

    private void updateCoverSizeUI() {
        String size = getPref("cover_size", "medium");
        setSegSelected(binding.segCoverSmall, "small".equals(size));
        setSegSelected(binding.segCoverMedium, "medium".equals(size));
        setSegSelected(binding.segCoverLarge, "large".equals(size));
    }

    private void initOrientSection() {
        binding.segOrientPortrait.setOnClickListener(v -> setPrefAndUpdate("cover_orient", "portrait", this::updateOrientUI));
        binding.segOrientLandscape.setOnClickListener(v -> setPrefAndUpdate("cover_orient", "landscape", this::updateOrientUI));
        updateOrientUI();
    }

    private void updateOrientUI() {
        String orient = getPref("cover_orient", "portrait");
        setSegSelected(binding.segOrientPortrait, "portrait".equals(orient));
        setSegSelected(binding.segOrientLandscape, "landscape".equals(orient));
    }

    private void initRatioSection() {
        binding.segRatio23.setOnClickListener(v -> setPrefAndUpdate("cover_ratio", "2:3", this::updateRatioUI));
        binding.segRatio34.setOnClickListener(v -> setPrefAndUpdate("cover_ratio", "3:4", this::updateRatioUI));
        binding.segRatio916.setOnClickListener(v -> setPrefAndUpdate("cover_ratio", "9:16", this::updateRatioUI));
        updateRatioUI();
    }

    private void updateRatioUI() {
        String ratio = getPref("cover_ratio", "2:3");
        setSegSelected(binding.segRatio23, "2:3".equals(ratio));
        setSegSelected(binding.segRatio34, "3:4".equals(ratio));
        setSegSelected(binding.segRatio916, "9:16".equals(ratio));
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
        // Switch card backgrounds: normal=white, glass=semi-transparent
        int cardBg = isNormal ? R.drawable.card_bg : R.drawable.card_bg_glass;
        int[] cardIds = {R.id.card_theme, R.id.card_cover, R.id.card_orient, R.id.card_ratio, R.id.card_style, R.id.card_glass, R.id.card_wp};
        for (int id : cardIds) {
            android.view.View card = findViewById(id);
            if (card != null) card.setBackgroundResource(cardBg);
        }
        // Show/hide wallpaper card: only in glass mode
        android.view.View cardWp = findViewById(R.id.card_wp);
        if (cardWp != null) cardWp.setVisibility(isNormal ? android.view.View.GONE : android.view.View.VISIBLE);
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
        updateThemeUI();
        updateCoverSizeUI();
        updateOrientUI();
        updateRatioUI();
        updateUiStyleUI();
        updateWallpaperUI();
    }
}
