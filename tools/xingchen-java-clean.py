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
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.databinding.ActivityUiSettingsBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
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
        updateThemeUI();
        updateWallpaperUI();
        binding.themeLight.setOnClickListener(v -> { setTheme("light"); updateThemeUI(); });
        binding.themeDark.setOnClickListener(v -> { setTheme("dark"); updateThemeUI(); });
        binding.themeSystem.setOnClickListener(v -> { setTheme("system"); updateThemeUI(); });
        binding.wpDefault.setOnClickListener(v -> { setWallpaper("shanjian"); updateWallpaperUI(); });
        binding.wpShanjian.setOnClickListener(v -> { setWallpaper("shanjian"); updateWallpaperUI(); });
        binding.wpBlue.setOnClickListener(v -> { setWallpaperColor("#8fb0d1"); updateWallpaperUI(); });
        binding.wpGreen.setOnClickListener(v -> { setWallpaperColor("#8fb996"); updateWallpaperUI(); });
        binding.wpClay.setOnClickListener(v -> { setWallpaperColor("#d29a7c"); updateWallpaperUI(); });
        binding.wpPurple.setOnClickListener(v -> { setWallpaperColor("#b3a6d6"); updateWallpaperUI(); });
        binding.wpDark.setOnClickListener(v -> { setWallpaperColor("#43484f"); updateWallpaperUI(); });
        initCoverSize();
        initOrientation();
        initRatio();
        initUiStyle();
        initGlassAlpha();
    }
    private void initCoverSize() {
        updateCoverSizeUI();
        binding.coverSmall.setOnClickListener(v -> { setPref("cover_size", "small"); updateCoverSizeUI(); });
        binding.coverMedium.setOnClickListener(v -> { setPref("cover_size", "medium"); updateCoverSizeUI(); });
        binding.coverLarge.setOnClickListener(v -> { setPref("cover_size", "large"); updateCoverSizeUI(); });
    }
    private void updateCoverSizeUI() {
        String s = getPref("cover_size", "medium");
        binding.coverSmall.setBackgroundResource("small".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.coverMedium.setBackgroundResource("medium".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.coverLarge.setBackgroundResource("large".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void initOrientation() {
        updateOrientationUI();
        binding.orientPortrait.setOnClickListener(v -> { setPref("cover_orient", "portrait"); updateOrientationUI(); });
        binding.orientLandscape.setOnClickListener(v -> { setPref("cover_orient", "landscape"); updateOrientationUI(); });
    }
    private void updateOrientationUI() {
        String s = getPref("cover_orient", "portrait");
        binding.orientPortrait.setBackgroundResource("portrait".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.orientLandscape.setBackgroundResource("landscape".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void initRatio() {
        updateRatioUI();
        binding.ratio23.setOnClickListener(v -> { setPref("cover_ratio", "2:3"); updateRatioUI(); });
        binding.ratio34.setOnClickListener(v -> { setPref("cover_ratio", "3:4"); updateRatioUI(); });
        binding.ratio916.setOnClickListener(v -> { setPref("cover_ratio", "9:16"); updateRatioUI(); });
    }
    private void updateRatioUI() {
        String s = getPref("cover_ratio", "2:3");
        binding.ratio23.setBackgroundResource("2:3".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.ratio34.setBackgroundResource("3:4".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.ratio916.setBackgroundResource("9:16".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void initUiStyle() {
        updateUiStyleUI();
        binding.styleNormal.setOnClickListener(v -> { setPref("ui_style", "normal"); updateUiStyleUI(); });
        binding.styleGlass.setOnClickListener(v -> { setPref("ui_style", "glass"); updateUiStyleUI(); });
    }
    private void updateUiStyleUI() {
        String s = getPref("ui_style", "glass");
        binding.styleNormal.setBackgroundResource("normal".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.styleGlass.setBackgroundResource("glass".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void initGlassAlpha() {
        int a = getSharedPreferences("xingchen", MODE_PRIVATE).getInt("glass_alpha", 55);
        binding.glassAlpha.setProgress(a);
        binding.glassAlpha.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean f) {
                getSharedPreferences("xingchen", MODE_PRIVATE).edit().putInt("glass_alpha", p).apply();
            }
            public void onStartTrackingTouch(android.widget.SeekBar s) {}
            public void onStopTrackingTouch(android.widget.SeekBar s) {}
        });
    }
    private String getPref(String k, String d) {
        return getSharedPreferences("xingchen", MODE_PRIVATE).getString(k, d);
    }
    private void setPref(String k, String v) {
        getSharedPreferences("xingchen", MODE_PRIVATE).edit().putString(k, v).apply();
    }
    private void updateThemeUI() {
        String t = getSharedPreferences("xingchen", MODE_PRIVATE).getString("theme", "light");
        binding.themeLight.setBackgroundResource("light".equals(t) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.themeDark.setBackgroundResource("dark".equals(t) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.themeSystem.setBackgroundResource("system".equals(t) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void updateWallpaperUI() {
        String wp = getSharedPreferences("xingchen", MODE_PRIVATE).getString("wallpaper", "shanjian");
        String c = getSharedPreferences("xingchen", MODE_PRIVATE).getString("wallpaper_color", "");
        binding.wpDefault.setBackgroundResource("shanjian".equals(wp) && "".equals(c) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void setTheme(String theme) {
        SharedPreferences sp = getSharedPreferences("xingchen", MODE_PRIVATE);
        sp.edit().putString("theme", theme).apply();
        if ("dark".equals(theme)) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else if ("light".equals(theme)) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
        recreate();
    }
    private void setWallpaper(String name) {
        com.xingchen.tv.theme.ThemeManager.get().setWallpaper(this, com.xingchen.tv.theme.XingChenTheme.WP_BUILTIN, name);
        updateWallpaperUI();
    }
    private void setWallpaperColor(String color) {
        com.xingchen.tv.theme.ThemeManager.get().setWallpaper(this, com.xingchen.tv.theme.XingChenTheme.WP_COLOR, color);
        updateWallpaperUI();
    }
}
"""
write(UI_JAVA, ui_code)
print("UiSettingsActivity created")

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
