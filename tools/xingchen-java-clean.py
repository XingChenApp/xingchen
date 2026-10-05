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
            "super.onCreate(savedInstanceState);\n        { getWindow().setStatusBarColor(0x00000000); android.view.ViewGroup xc_decor = (android.view.ViewGroup) getWindow().getDecorView(); xc_decor.post(() -> { getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN); android.view.View xc_content = findViewById(android.R.id.content); if (xc_content != null) { String xc_wp = getSharedPreferences(\"xingchen\", MODE_PRIVATE).getString(\"wallpaper\", \"shanjian\"); if (\"color\".equals(xc_wp)) { String xc_c = getSharedPreferences(\"xingchen\", MODE_PRIVATE).getString(\"wallpaper_color\", \"#8fb0d1\"); try { xc_content.setBackgroundColor(android.graphics.Color.parseColor(xc_c)); } catch (Exception e) { xc_content.setBackgroundResource(com.fongmi.android.tv.R.drawable.poster_shanjian); } } else { xc_content.setBackgroundResource(com.fongmi.android.tv.R.drawable.poster_shanjian); } } }); }"
        )
        assert "xc_content" in bc, "BaseActivity patch failed"
        # Add onResume to refresh wallpaper when returning
        if "xc_onResume" not in bc:
            bc = bc.replace(
                "protected void onResume() {",
                "protected void onResume() {\n        { android.view.View xc_c2 = findViewById(android.R.id.content); if (xc_c2 != null) { String xc_wp2 = getSharedPreferences(\"xingchen\", MODE_PRIVATE).getString(\"wallpaper\", \"shanjian\"); if (\"color\".equals(xc_wp2)) { String xc_c = getSharedPreferences(\"xingchen\", MODE_PRIVATE).getString(\"wallpaper_color\", \"#8fb0d1\"); try { xc_c2.setBackgroundColor(android.graphics.Color.parseColor(xc_c)); } catch (Exception e) { xc_c2.setBackgroundResource(com.fongmi.android.tv.R.drawable.poster_shanjian); } } else { xc_c2.setBackgroundResource(com.fongmi.android.tv.R.drawable.poster_shanjian); } } } // xc_onResume"
            )
        write(BASE_JAVA, bc)
        print("BaseActivity patched")

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
        applyWallpaper();
        binding.cardVod.setOnClickListener(v -> ConfigDialog.create().vod().show(getSupportFragmentManager(), null));
        binding.cardLive.setOnClickListener(v -> ConfigDialog.create().live().show(getSupportFragmentManager(), null));
    }
    private void applyWallpaper() {
        android.view.View root = findViewById(android.R.id.content);
        if (root != null) {
            String wp = getSharedPreferences("xingchen", MODE_PRIVATE).getString("wallpaper", "shanjian");
            if ("color".equals(wp)) {
                String c = getSharedPreferences("xingchen", MODE_PRIVATE).getString("wallpaper_color", "#8fb0d1");
                try { root.setBackgroundColor(android.graphics.Color.parseColor(c)); }
                catch (Exception e) { root.setBackgroundResource(com.fongmi.android.tv.R.drawable.poster_shanjian); }
            } else {
                root.setBackgroundResource(com.fongmi.android.tv.R.drawable.poster_shanjian);
            }
        }
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
        applyWallpaper();
        binding.wpShanjian.setOnClickListener(v -> setWallpaper("shanjian"));
        binding.wpBlue.setOnClickListener(v -> setWallpaperColor("#8fb0d1"));
        binding.wpGreen.setOnClickListener(v -> setWallpaperColor("#8fb996"));
        binding.wpClay.setOnClickListener(v -> setWallpaperColor("#d29a7c"));
        binding.wpPurple.setOnClickListener(v -> setWallpaperColor("#b3a6d6"));
        binding.wpDark.setOnClickListener(v -> setWallpaperColor("#43484f"));
        binding.themeLight.setOnClickListener(v -> setTheme("light"));
        binding.themeDark.setOnClickListener(v -> setTheme("dark"));
        binding.themeSystem.setOnClickListener(v -> setTheme("system"));
    }
    private void applyWallpaper() {
        android.view.View root = findViewById(android.R.id.content);
        if (root != null) {
            String wp = getSharedPreferences("xingchen", MODE_PRIVATE).getString("wallpaper", "shanjian");
            if ("color".equals(wp)) {
                String c = getSharedPreferences("xingchen", MODE_PRIVATE).getString("wallpaper_color", "#8fb0d1");
                try { root.setBackgroundColor(android.graphics.Color.parseColor(c)); }
                catch (Exception e) { root.setBackgroundResource(com.fongmi.android.tv.R.drawable.poster_shanjian); }
            } else {
                root.setBackgroundResource(com.fongmi.android.tv.R.drawable.poster_shanjian);
            }
        }
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
        SharedPreferences sp = getSharedPreferences("xingchen", MODE_PRIVATE);
        sp.edit().putString("wallpaper", name).putString("wallpaper_color", "").apply();
        recreate();
    }
    private void setWallpaperColor(String color) {
        SharedPreferences sp = getSharedPreferences("xingchen", MODE_PRIVATE);
        sp.edit().putString("wallpaper", "color").putString("wallpaper_color", color).apply();
        recreate();
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
        applyWallpaper();
        updateKernelUI();
        updateDecodeUI();
        updateSpeedUI();
        binding.kernelExo.setOnClickListener(v -> { setKernel("exo"); updateKernelUI(); });
        binding.kernelMpv.setOnClickListener(v -> { setKernel("mpv"); updateKernelUI(); });
        binding.decodeHard.setOnClickListener(v -> { setDecode("hard"); updateDecodeUI(); });
        binding.decodeSoft.setOnClickListener(v -> { setDecode("soft"); updateDecodeUI(); });
        binding.speed075.setOnClickListener(v -> { setSpeed(0.75f); updateSpeedUI(); });
        binding.speed100.setOnClickListener(v -> { setSpeed(1.0f); updateSpeedUI(); });
        binding.speed125.setOnClickListener(v -> { setSpeed(1.25f); updateSpeedUI(); });
        binding.speed150.setOnClickListener(v -> { setSpeed(1.5f); updateSpeedUI(); });
        binding.speed200.setOnClickListener(v -> { setSpeed(2.0f); updateSpeedUI(); });
    }
    private void updateKernelUI() {
        String k = getSharedPreferences("xingchen", MODE_PRIVATE).getString("player_kernel", "exo");
        binding.kernelExo.setBackgroundResource("exo".equals(k) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : com.fongmi.android.tv.R.drawable.xc_seg_normal);
        binding.kernelMpv.setBackgroundResource("mpv".equals(k) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : com.fongmi.android.tv.R.drawable.xc_seg_normal);
    }
    private void updateDecodeUI() {
        String d = getSharedPreferences("xingchen", MODE_PRIVATE).getString("player_decode", "hard");
        binding.decodeHard.setBackgroundResource("hard".equals(d) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : com.fongmi.android.tv.R.drawable.xc_seg_normal);
        binding.decodeSoft.setBackgroundResource("soft".equals(d) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : com.fongmi.android.tv.R.drawable.xc_seg_normal);
    }
    private void updateSpeedUI() {
        float s = getSharedPreferences("xingchen", MODE_PRIVATE).getFloat("player_speed", 1.0f);
        binding.speed075.setBackgroundResource(s == 0.75f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : com.fongmi.android.tv.R.drawable.xc_seg_normal);
        binding.speed100.setBackgroundResource(s == 1.0f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : com.fongmi.android.tv.R.drawable.xc_seg_normal);
        binding.speed125.setBackgroundResource(s == 1.25f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : com.fongmi.android.tv.R.drawable.xc_seg_normal);
        binding.speed150.setBackgroundResource(s == 1.5f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : com.fongmi.android.tv.R.drawable.xc_seg_normal);
        binding.speed200.setBackgroundResource(s == 2.0f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : com.fongmi.android.tv.R.drawable.xc_seg_normal);
    }
    private void applyWallpaper() {
        android.view.View root = findViewById(android.R.id.content);
        if (root != null) {
            String wp = getSharedPreferences("xingchen", MODE_PRIVATE).getString("wallpaper", "shanjian");
            if ("color".equals(wp)) {
                String c = getSharedPreferences("xingchen", MODE_PRIVATE).getString("wallpaper_color", "#8fb0d1");
                try { root.setBackgroundColor(android.graphics.Color.parseColor(c)); }
                catch (Exception e) { root.setBackgroundResource(com.fongmi.android.tv.R.drawable.poster_shanjian); }
            } else {
                root.setBackgroundResource(com.fongmi.android.tv.R.drawable.poster_shanjian);
            }
        }
    }
    private void setKernel(String k) {
        getSharedPreferences("xingchen", MODE_PRIVATE).edit().putString("player_kernel", k).apply();
        Notify.show("播放器内核: " + k);
    }
    private void setDecode(String d) {
        getSharedPreferences("xingchen", MODE_PRIVATE).edit().putString("player_decode", d).apply();
        Notify.show("解码方式: " + d);
    }
    private void setSpeed(float s) {
        getSharedPreferences("xingchen", MODE_PRIVATE).edit().putFloat("player_speed", s).apply();
        Notify.show("倍速: " + s + "x");
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
