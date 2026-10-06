import os
import re

BASE = "/tmp/webhtv-src"
JAVA_PATH = os.path.join(BASE, "app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingFragment.java")

def read(p):
    with open(p, "r", encoding="utf-8") as f:
        return f.read()

def write(p, c):
    with open(p, "w", encoding="utf-8") as f:
        f.write(c)

content = read(JAVA_PATH)

new_ids = ["cardConfig", "cardPlayer", "cardAppearance", "cardPlugin", "cardFeatures", "cardHealth", "cardAbout",
           "textPlayerSub", "textAppearanceSub", "textPluginSub", "textFeaturesSub", "textHealthSub", "textAboutSub"]

lines = content.split("\n")
kept = []
skip_method = False
brace_depth = 0
method_start_depth = 0

i = 0
while i < len(lines):
    line = lines[i]
    has_old = False
    for m in re.finditer(r"mBinding\.(\w+)", line):
        name = m.group(1)
        if name not in new_ids:
            has_old = True
            break
    if has_old:
        stripped = line.strip()
        if stripped.startswith("private ") or stripped.startswith("public ") or stripped.startswith("protected "):
            if "(" in stripped and ")" in stripped:
                skip_method = True
                method_start_depth = brace_depth
                brace_depth += line.count("{") - line.count("}")
                i += 1
                continue
        if skip_method:
            brace_depth += line.count("{") - line.count("}")
            if brace_depth <= method_start_depth and "}" in line:
                skip_method = False
            i += 1
            continue
        else:
            i += 1
            continue
    if skip_method:
        brace_depth += line.count("{") - line.count("}")
        if brace_depth <= method_start_depth and "}" in line:
            skip_method = False
        i += 1
        continue
    brace_depth += line.count("{") - line.count("}")
    kept.append(line)
    i += 1

content = "\n".join(kept)

old_init_pattern = r"    @Override\n    protected void initView\(\) \{.*?\n    \}"
new_init = """    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        if (getActivity() != null && getActivity().getWindow() != null) {
            getActivity().getWindow().setStatusBarColor(0xFFF5E3B8);
        }
        mBinding.textAboutSub.setText(com.fongmi.android.tv.utils.AppVersion.fullName());
        mBinding.cardConfig.setOnClickListener(v -> onVod(v));
        mBinding.cardPlayer.setOnClickListener(v -> onPlayer(v));
        mBinding.cardAppearance.setOnClickListener(v -> onAppearance(v));
        mBinding.cardPlugin.setOnClickListener(v -> com.fongmi.android.tv.utils.Notify.show("插件管理"));
        mBinding.cardFeatures.setOnClickListener(v -> com.fongmi.android.tv.utils.Notify.show("个性功能"));
        mBinding.cardHealth.setOnClickListener(v -> com.fongmi.android.tv.utils.Notify.show("源健康检测"));
        mBinding.cardAbout.setOnClickListener(v -> onVersion(v));
    }"""

content = re.sub(old_init_pattern, new_init, content, flags=re.DOTALL)

content = content.replace("mBinding.cardConfig.setOnClickListener(v -> onVod());", "mBinding.cardConfig.setOnClickListener(v -> onVod(v));")
content = content.replace("mBinding.cardPlayer.setOnClickListener(v -> onPlayer());", "mBinding.cardPlayer.setOnClickListener(v -> onPlayer(v));")
content = content.replace("mBinding.cardAppearance.setOnClickListener(v -> onAppearance());", "mBinding.cardAppearance.setOnClickListener(v -> onAppearance(v));")
content = content.replace("mBinding.cardAbout.setOnClickListener(v -> onVersion());", "mBinding.cardAbout.setOnClickListener(v -> onVersion(v));")

write(JAVA_PATH, content)
print("cleaned")

HOME_JAVA = os.path.join(BASE, "app/src/mobile/java/com/fongmi/android/tv/ui/activity/HomeActivity.java")
if os.path.exists(HOME_JAVA):
    hc = read(HOME_JAVA)
    if "setDecorFitsSystemWindows" not in hc:
        hc = hc.replace(
            "import com.fongmi.android.tv.utils.MobileWindow;",
            "import androidx.core.view.WindowCompat;\nimport com.fongmi.android.tv.utils.MobileWindow;"
        )
        hc = hc.replace(
            "super.onCreate(savedInstanceState);",
            "super.onCreate(savedInstanceState);\\n        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);\\n        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);"
        )
        if "xc_bottombar" not in hc:
            assert "mBinding.navigation.setOnItemSelectedListener(this);" in hc, "HomeActivity listener not found"
            hc = hc.replace(
                "mBinding.navigation.setOnItemSelectedListener(this);",
                "mBinding.navigation.setOnItemSelectedListener(this);\\n        { android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(); gd.setColor(0x57FFFFFF); gd.setStroke((int)(1 * getResources().getDisplayMetrics().density), 0x61FFFFFF); gd.setCornerRadius(10 * getResources().getDisplayMetrics().density); mBinding.navigation.setBackground(gd); mBinding.navigation.setItemActiveIndicatorEnabled(false); }"
            )
            assert "GradientDrawable" in hc, "HomeActivity background patch failed"
        print("HomeActivity patched")

BASE_JAVA = os.path.join(BASE, "app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java")
if os.path.exists(BASE_JAVA):
    bc = read(BASE_JAVA)
    if "poster_shanjian" not in bc:
        assert "super.onCreate(savedInstanceState);" in bc, "BaseActivity onCreate not found"
        assert "enableDynamicColor();" in bc, "BaseActivity enableDynamicColor not found"
        bc = bc.replace(
            "enableDynamicColor();",
            "// enableDynamicColor(); // Disabled for wallpaper"
        )
        assert "// Disabled for wallpaper" in bc, "BaseActivity dynamic color disable failed"
        bc = bc.replace(
            "super.onCreate(savedInstanceState);",
            "super.onCreate(savedInstanceState);\n        { getWindow().setStatusBarColor(0x00000000); getWindow().getDecorView().post(() -> { com.xingchen.tv.theme.ThemeManager.get().apply(this); }); }",
        )
        assert "ThemeManager" in bc, "BaseActivity patch failed"
        # Add onResume to refresh wallpaper when returning - robust version
        if "xc_onResume" not in bc:
            if "protected void onResume()" in bc:
                bc = bc.replace(
                    "protected void onResume() {",
                    "protected void onResume() {\n        { com.xingchen.tv.theme.ThemeManager.get().apply(this); } // xc_onResume",
                    1
                )
            else:
                # No onResume exists, add one before the last closing brace of class
                # Find the last } and insert before it
                insert_code = "    @Override\n    protected void onResume() {\n        super.onResume();\n        { com.xingchen.tv.theme.ThemeManager.get().apply(this); } // xc_onResume\n    }\n"
                # Simple: append before final }
                bc = bc.rstrip()
                if bc.endswith("}"):
                    bc = bc[:-1] + "\n" + insert_code + "}\n"
            assert "xc_onResume" in bc, "BaseActivity onResume patch failed" 
        write(BASE_JAVA, bc)
        print("BaseActivity patched")

APP_JAVA = os.path.join(BASE, "app/src/mobile/java/com/fongmi/android/tv/App.java")
if os.path.exists(APP_JAVA):
    ac = read(APP_JAVA)
    if "ThemeManager.init" not in ac:
        ac = ac.replace(
            "super.onCreate();",
            "super.onCreate();\n        com.xingchen.tv.theme.ThemeManager.init(this);"
        )
        write(APP_JAVA, ac)
        print("App.java patched for global theme")

BASE_FRAG = os.path.join(BASE, "app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseFragment.java")
if os.path.exists(BASE_FRAG):
    fc = read(BASE_FRAG)
    if "setBackgroundColor(0x00000000)" not in fc:
        assert "public void onViewCreated" in fc, "BaseFragment onViewCreated not found"
        fc = fc.replace(
            "public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {",
            "public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {\n        view.setBackgroundColor(0x00000000);"
        )
        assert "setBackgroundColor(0x00000000)" in fc, "BaseFragment patch failed"
        write(BASE_FRAG, fc)
        print("BaseFragment patched")

THEME_DIR = os.path.join(BASE, "app/src/mobile/java/com/xingchen/tv/theme")
os.makedirs(THEME_DIR, exist_ok=True)
write(os.path.join(THEME_DIR, "XingChenTheme.java"), """package com.xingchen.tv.theme;

public class XingChenTheme {
    public static final String UI_NORMAL = "normal";
    public static final String UI_GLASS = "glass";

    public static final String WP_BUILTIN = "builtin";
    public static final String WP_LOCAL = "local";
    public static final String WP_URL = "url";
    public static final String WP_COLOR = "color";

    public String uiStyle = UI_GLASS;
    public String wallpaperType = WP_BUILTIN;
    public String wallpaperValue = "shanjian";
    public int glassAlpha = 55;

    public static XingChenTheme load(android.content.Context ctx) {
        XingChenTheme t = new XingChenTheme();
        android.content.SharedPreferences sp = ctx.getSharedPreferences("xingchen", android.content.Context.MODE_PRIVATE);
        t.uiStyle = sp.getString("ui_style", UI_GLASS);
        t.wallpaperType = sp.getString("wallpaper_type", WP_BUILTIN);
        t.wallpaperValue = sp.getString("wallpaper_value", "shanjian");
        String oldWp = sp.getString("wallpaper", "shanjian");
        if (!"shanjian".equals(oldWp) && WP_BUILTIN.equals(t.wallpaperType)) {
            if ("color".equals(oldWp)) {
                t.wallpaperType = WP_COLOR;
                t.wallpaperValue = sp.getString("wallpaper_color", "#8fb0d1");
            }
        }
        t.glassAlpha = sp.getInt("glass_alpha", 55);
        return t;
    }

    public void save(android.content.Context ctx) {
        android.content.SharedPreferences.Editor e = ctx.getSharedPreferences("xingchen", android.content.Context.MODE_PRIVATE).edit();
        e.putString("ui_style", uiStyle);
        e.putString("wallpaper_type", wallpaperType);
        e.putString("wallpaper_value", wallpaperValue);
        e.putInt("glass_alpha", glassAlpha);
        e.apply();
    }
}
""")
write(os.path.join(THEME_DIR, "ThemeManager.java"), """package com.xingchen.tv.theme;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class ThemeManager {
    private static ThemeManager instance;
    private XingChenTheme theme;

    private ThemeManager() {}

    public static synchronized ThemeManager get() {
        if (instance == null) instance = new ThemeManager();
        return instance;
    }

    public static void init(Application app) {
        get().theme = XingChenTheme.load(app);
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) { get().apply(a); }
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    public XingChenTheme getTheme() {
        return theme;
    }

    public void refresh(android.content.Context ctx) {
        theme = XingChenTheme.load(ctx);
    }

    public void setUiStyle(android.content.Context ctx, String style) {
        theme.uiStyle = style;
        theme.save(ctx);
        applyAll();
    }

    public void setWallpaper(android.content.Context ctx, String type, String value) {
        theme.wallpaperType = type;
        theme.wallpaperValue = value;
        theme.save(ctx);
        applyAll();
    }

    private void applyAll() {
    }

    public void apply(Activity activity) {
        try {
            activity.getWindow().getDecorView().post(new Runnable() {
                @Override public void run() {
                    doApply(activity);
                }
            });
        } catch (Exception e) {}
    }

    private void doApply(Activity activity) {
        try {
            if (theme == null) theme = XingChenTheme.load(activity);
            ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            ensureWallpaperLayer(activity, decor);
            if (XingChenTheme.UI_GLASS.equals(theme.uiStyle)) {
                makeTransparent(decor);
            } else {
                decor.setBackgroundColor(0xFFF5F0E8);
            }
        } catch (Exception e) {}
    }

    private void ensureWallpaperLayer(Activity activity, ViewGroup decor) {
        try {
            View existing = decor.findViewWithTag("xc_wallpaper");
            ImageView iv;
            if (existing instanceof ImageView) {
                iv = (ImageView) existing;
            } else {
                iv = new ImageView(activity);
                iv.setTag("xc_wallpaper");
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                decor.addView(iv, 0, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            }
            if (XingChenTheme.UI_GLASS.equals(theme.uiStyle)) {
                iv.setVisibility(View.VISIBLE);
                setWallpaperDrawable(activity, iv);
            } else {
                iv.setVisibility(View.GONE);
            }
        } catch (Exception e) {}
    }

    private void setWallpaperDrawable(Activity activity, ImageView iv) {
        try {
            String type = theme.wallpaperType;
            String value = theme.wallpaperValue;
            if (XingChenTheme.WP_COLOR.equals(type)) {
                try {
                    iv.setImageDrawable(null);
                    iv.setBackgroundColor(android.graphics.Color.parseColor(value));
                } catch (Exception e) {
                    iv.setBackgroundColor(0x00000000);
                    iv.setImageResource(getBuiltinRes(activity, "shanjian"));
                }
            } else if (XingChenTheme.WP_LOCAL.equals(type) || XingChenTheme.WP_URL.equals(type)) {
                try {
                    android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(value);
                    if (bm != null) {
                        iv.setImageBitmap(bm);
                        iv.setBackgroundColor(0x00000000);
                    } else {
                        iv.setImageResource(getBuiltinRes(activity, "shanjian"));
                    }
                } catch (Exception e) {
                    iv.setImageResource(getBuiltinRes(activity, "shanjian"));
                }
            } else {
                iv.setBackgroundColor(0x00000000);
                iv.setImageResource(getBuiltinRes(activity, value));
            }
        } catch (Exception e) {}
    }

    private int getBuiltinRes(android.content.Context ctx, String name) {
        try {
            if ("shanjian".equals(name)) {
                return ctx.getResources().getIdentifier("poster_shanjian", "drawable", ctx.getPackageName());
            }
            return ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
        } catch (Exception e) {
            return 0;
        }
    }

    private void makeTransparent(ViewGroup root) {
        try {
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if ("xc_wallpaper".equals(child.getTag())) continue;
                String cls = child.getClass().getName();
                if (cls.contains("BottomNavigationView")) continue;
                if (child instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) child;
                    Drawable bg = vg.getBackground();
                    if (bg != null) {
                        vg.setBackgroundColor(0x00000000);
                    }
                    if (vg.getChildCount() > 0 && !(child instanceof android.widget.ScrollView)
                            && !(child instanceof androidx.recyclerview.widget.RecyclerView)
                            && !(child instanceof android.widget.ListView)) {
                        makeTransparent(vg);
                    }
                }
            }
        } catch (Exception e) {}
    }
}
""")
print("XingChen theme system created")
print("Theme system created")

CONFIG_JAVA = os.path.join(BASE, "app/src/mobile/java/com/fongmi/android/tv/ui/activity/ConfigSourceActivity.java")
config_code = """package com.fongmi.android.tv.ui.activity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.databinding.ActivityConfigSourceBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
public class ConfigSourceActivity extends BaseActivity {
    private ActivityConfigSourceBinding binding;
    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, ConfigSourceActivity.class));
    }
    @Override
    protected ViewBinding getBinding() {
        binding = ActivityConfigSourceBinding.inflate(getLayoutInflater());
        return binding;
    }
    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.cardVod.setOnClickListener(v -> ConfigDialog.create().vod().show(getSupportFragmentManager(), null));
        binding.cardLive.setOnClickListener(v -> ConfigDialog.create().live().show(getSupportFragmentManager(), null));
    }
}
"""
write(CONFIG_JAVA, config_code)
print("ConfigSourceActivity created")

MANIFEST = os.path.join(BASE, "app/src/mobile/AndroidManifest.xml")
mc = read(MANIFEST)
if "ConfigSourceActivity" not in mc:
    mc = mc.replace(
        '<activity\n            android:name=".ui.activity.HistoryActivity"',
        '<activity\n            android:name=".ui.activity.ConfigSourceActivity"\n            android:configChanges="screenSize|smallestScreenSize|screenLayout"\n            android:screenOrientation="fullUser" />\n\n        <activity\n            android:name=".ui.activity.HistoryActivity"'
    )
    write(MANIFEST, mc)
    print("Manifest updated")

SF = os.path.join(BASE, "app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingFragment.java")
sc = read(SF)
if "ConfigSourceActivity.start" not in sc:
    sc = sc.replace(
        "mBinding.cardConfig.setOnClickListener(v -> onVod(v));",
        "mBinding.cardConfig.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.ConfigSourceActivity.start(getActivity()));"
    )
    write(SF, sc)
    print("SettingFragment updated")

UI_JAVA = os.path.join(BASE, "app/src/mobile/java/com/fongmi/android/tv/ui/activity/UiSettingsActivity.java")
ui_code = """package com.fongmi.android.tv.ui.activity;

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
"""
write(UI_JAVA, ui_code)
UI_XML = os.path.join(BASE, "app/src/mobile/res/layout/activity_ui_settings.xml")
ui_xml = """<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <LinearLayout
            android:id="@+id/themeSection"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:layout_marginBottom="16dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="主题"
                android:textSize="16sp"
                android:textStyle="bold" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:layout_marginTop="8dp">

                <Button
                    android:id="@+id/themeLight"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="浅色" />

                <Button
                    android:id="@+id/themeDark"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="深色" />

                <Button
                    android:id="@+id/themeSystem"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="跟随系统" />
            </LinearLayout>
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:layout_marginBottom="16dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="UI 风格"
                android:textSize="16sp"
                android:textStyle="bold" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:layout_marginTop="8dp">

                <Button
                    android:id="@+id/uiNormal"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="普通" />

                <Button
                    android:id="@+id/uiGlass"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="毛玻璃" />
            </LinearLayout>
        </LinearLayout>

        <LinearLayout
            android:id="@+id/glassSection"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:layout_marginBottom="16dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="毛玻璃透明度"
                android:textSize="16sp"
                android:textStyle="bold" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:layout_marginTop="8dp">

                <SeekBar
                    android:id="@+id/glassSeek"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:max="100" />

                <TextView
                    android:id="@+id/glassValue"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="55%"
                    android:layout_marginStart="8dp" />
            </LinearLayout>
        </LinearLayout>

        <LinearLayout
            android:id="@+id/wallpaperSection"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:layout_marginBottom="16dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="壁纸"
                android:textSize="16sp"
                android:textStyle="bold" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:layout_marginTop="8dp">

                <Button
                    android:id="@+id/wpDefault"
                    android:layout_width="0dp"
                    android:layout_height="80dp"
                    android:layout_weight="1"
                    android:text="默认" />

                <Button
                    android:id="@+id/wpLocal"
                    android:layout_width="0dp"
                    android:layout_height="80dp"
                    android:layout_weight="1"
                    android:text="本地" />

                <Button
                    android:id="@+id/wpUrl"
                    android:layout_width="0dp"
                    android:layout_height="80dp"
                    android:layout_weight="1"
                    android:text="URL" />
            </LinearLayout>
        </LinearLayout>

    </LinearLayout>
</ScrollView>
"""
write(UI_XML, ui_xml)
print("UiSettingsActivity created (new theme UI)")


mc = read(MANIFEST)
if "UiSettingsActivity" not in mc:
    mc = mc.replace(
        '<activity\n            android:name=".ui.activity.ConfigSourceActivity"',
        '<activity\n            android:name=".ui.activity.UiSettingsActivity"\n            android:configChanges="screenSize|smallestScreenSize|screenLayout"\n            android:screenOrientation="fullUser" />\n\n        <activity\n            android:name=".ui.activity.ConfigSourceActivity"'
    )
    write(MANIFEST, mc)
    print("Manifest updated for UiSettings")

sc = read(SF)
if "UiSettingsActivity.start" not in sc:
    sc = sc.replace(
        "mBinding.cardAppearance.setOnClickListener(v -> onAppearance(v));",
        "mBinding.cardAppearance.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.UiSettingsActivity.start(getActivity()));"
    )
    write(SF, sc)
    print("SettingFragment updated for UiSettings")

PLAYER_JAVA = os.path.join(BASE, "app/src/mobile/java/com/fongmi/android/tv/ui/activity/PlayerSettingsActivity.java")
player_code = """package com.fongmi.android.tv.ui.activity;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.databinding.ActivityPlayerSettingsBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;
public class PlayerSettingsActivity extends BaseActivity {
    private ActivityPlayerSettingsBinding binding;
    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, PlayerSettingsActivity.class));
    }
    @Override
    protected ViewBinding getBinding() {
        binding = ActivityPlayerSettingsBinding.inflate(getLayoutInflater());
        return binding;
    }
    @Override
    protected void initView(Bundle savedInstanceState) {
        updateKernelUI();
        updateDecodeUI();
        binding.kernelExo.setOnClickListener(v -> { setKernel("exo"); updateKernelUI(); });
        binding.kernelMpv.setOnClickListener(v -> { setKernel("mpv"); updateKernelUI(); });
        binding.kernelIjk.setOnClickListener(v -> { setKernel("ijk"); updateKernelUI(); });
        binding.decodeHard.setOnClickListener(v -> { setDecode("hard"); updateDecodeUI(); });
        binding.decodeSoft.setOnClickListener(v -> { setDecode("soft"); updateDecodeUI(); });
        binding.switchAutonext.setChecked(getSharedPreferences("xingchen", MODE_PRIVATE).getBoolean("auto_next", true));
        binding.switchAutonext.setOnCheckedChangeListener((b, c) -> getSharedPreferences("xingchen", MODE_PRIVATE).edit().putBoolean("auto_next", c).apply());
        binding.switchSkip.setChecked(getSharedPreferences("xingchen", MODE_PRIVATE).getBoolean("skip_intro", false));
        binding.switchSkip.setOnCheckedChangeListener((b, c) -> getSharedPreferences("xingchen", MODE_PRIVATE).edit().putBoolean("skip_intro", c).apply());
        updateSpeedUI();
        binding.speed075.setOnClickListener(v -> { setSpeed(0.75f); updateSpeedUI(); });
        binding.speed100.setOnClickListener(v -> { setSpeed(1.0f); updateSpeedUI(); });
        binding.speed125.setOnClickListener(v -> { setSpeed(1.25f); updateSpeedUI(); });
        binding.speed150.setOnClickListener(v -> { setSpeed(1.5f); updateSpeedUI(); });
        binding.speed200.setOnClickListener(v -> { setSpeed(2.0f); updateSpeedUI(); });
        binding.cardDanmu.setOnClickListener(v -> {});
        binding.cardSubtitle.setOnClickListener(v -> {});
    }
    private void updateSpeedUI() {
        float s = getSharedPreferences("xingchen", MODE_PRIVATE).getFloat("player_speed", 1.0f);
        binding.speed075.setBackgroundResource(s == 0.75f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.speed100.setBackgroundResource(s == 1.0f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.speed125.setBackgroundResource(s == 1.25f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.speed150.setBackgroundResource(s == 1.5f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.speed200.setBackgroundResource(s == 2.0f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void setSpeed(float s) {
        getSharedPreferences("xingchen", MODE_PRIVATE).edit().putFloat("player_speed", s).apply();
    }
    private void updateKernelUI() {
        String k = getSharedPreferences("xingchen", MODE_PRIVATE).getString("player_kernel", "exo");
        binding.kernelExo.setBackgroundResource("exo".equals(k) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.kernelMpv.setBackgroundResource("mpv".equals(k) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.kernelIjk.setBackgroundResource("ijk".equals(k) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void updateDecodeUI() {
        String d = getSharedPreferences("xingchen", MODE_PRIVATE).getString("player_decode", "hard");
        binding.decodeHard.setBackgroundResource("hard".equals(d) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.decodeSoft.setBackgroundResource("soft".equals(d) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void setKernel(String k) {
        getSharedPreferences("xingchen", MODE_PRIVATE).edit().putString("player_kernel", k).apply();
    }
    private void setDecode(String d) {
        getSharedPreferences("xingchen", MODE_PRIVATE).edit().putString("player_decode", d).apply();
    }
}
"""
write(PLAYER_JAVA, player_code)
print("PlayerSettingsActivity created")

mc = read(MANIFEST)
if "PlayerSettingsActivity" not in mc:
    mc = mc.replace(
        '<activity\n            android:name=".ui.activity.UiSettingsActivity"',
        '<activity\n            android:name=".ui.activity.PlayerSettingsActivity"\n            android:configChanges="screenSize|smallestScreenSize|screenLayout"\n            android:screenOrientation="fullUser" />\n\n        <activity\n            android:name=".ui.activity.UiSettingsActivity"'
    )
    write(MANIFEST, mc)
    print("Manifest updated for Player")

sc = read(SF)
if "PlayerSettingsActivity.start" not in sc:
    sc = sc.replace(
        "mBinding.cardPlayer.setOnClickListener(v -> onPlayer(v));",
        "mBinding.cardPlayer.setOnClickListener(v -> com.fongmi.android.tv.ui.activity.PlayerSettingsActivity.start(getActivity()));"
    )
    write(SF, sc)
    print("SettingFragment updated for Player")
