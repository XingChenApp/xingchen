import os
import glob
import shutil
import re

BASE = "/tmp/webhtv-src"
MOBILE_RES = os.path.join(BASE, "app/src/mobile/res")
MOBILE_JAVA = os.path.join(BASE, "app/src/mobile/java/com/fongmi/android/tv")

def read(path):
    assert os.path.exists(path), "missing: " + path
    with open(path, "r", encoding="utf-8") as f:
        return f.read()

def write(path, content):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

def must_replace(content, old, new, desc):
    assert old in content, "NOT FOUND: " + desc
    content = content.replace(old, new)
    assert new in content, "REPLACE FAILED: " + desc
    return content

# Change applicationId, versionCode, versionName
p = os.path.join(BASE, "app/build.gradle")
c = read(p)
c = must_replace(c, 'applicationId "com.fongmi.android.tv"', 'applicationId "com.XingChen.tv"', "applicationId")
c = must_replace(c, 'versionCode 560', 'versionCode 2', "versionCode")
c = must_replace(c, 'versionName "5.6.0"', 'versionName "1.0.1"', "versionName")
write(p, c)
print("build.gradle patched")

# Change app name to 星辰
for res_dir in ["app/src/main/res/values/strings.xml", "app/src/main/res/values-zh-rCN/strings.xml"]:
    p = os.path.join(BASE, res_dir)
    if os.path.exists(p):
        c = read(p)
        orig = c
        c = c.replace('<string name="app_name">TV</string>', '<string name="app_name">星辰</string>')
        c = c.replace('<string name="app_name">影视</string>', '<string name="app_name">星辰</string>')
        c = c.replace('<string name="app_name">WebHomeTV</string>', '<string name="app_name">星辰</string>')
        c = c.replace('WebHomeTV', '星辰')
        assert c != orig, "app_name not changed in " + res_dir
        assert "星辰" in c, "星辰 not in " + res_dir
        write(p, c)
        print("app_name patched: " + res_dir)

def replace_once(path, old, new):
    c = read(path)
    n = c.count(old)
    assert n == 1, "expected 1, found %d for %s in %s" % (n, old[:40], path)
    write(path, c.replace(old, new, 1))

# Theme patches removed - do not touch theme per user request
# Wallpaper via BaseActivity code, white pill via layout only
for theme_path in ["app/src/main/res/values/styles.xml", "app/src/mobile/res/values/styles.xml"]:
    p = os.path.join(BASE, theme_path)
    if os.path.exists(p):
        print("Skipping theme patch for " + theme_path)

p = os.path.join(MOBILE_RES, "menu/menu_nav.xml")
c = read(p)
assert c.count('android:visible="false"') == 3
c = c.replace('android:visible="false"', 'android:visible="true"')
write(p, c)
print("menu_nav patched")

# Remove icon tint so custom line-style icons show correctly
# Also disable white pill indicator like Moying (app:itemActiveIndicatorStyle="@null")
p = os.path.join(MOBILE_RES, "layout/activity_home.xml")
if os.path.exists(p):
    c = read(p)
    if 'app:itemIconTint' in c:
        c = re.sub(r'app:itemIconTint="[^"]*"', 'app:itemIconTint="@null"', c)
    if 'app:itemActiveIndicatorStyle' not in c:
        c = c.replace(
            'app:menu="@menu/menu_nav"',
            'app:menu="@menu/menu_nav"\n        app:itemActiveIndicatorStyle="@null"'
        )
    # Remove custom ripple color if present (was too dark/big)
    c = re.sub(r'\s*app:itemRippleColor="[^"]*"', '', c)
    write(p, c)
    print("Patched nav: tint removed, indicator disabled, ripple default")

p = os.path.join(MOBILE_RES, "values/strings.xml")
c = read(p)

p = os.path.join(MOBILE_RES, "layout/activity_home.xml")
replace_once(p, 'android:background="@color/transparent"', 'android:background="@drawable/xc_bottombar_pill"')
# Add fullscreen background ImageView behind everything (Home)
c = read(p)
if '@+id/xc_bg_full' not in c:
    import re
    m = re.search(r'<RelativeLayout[^>]*>', c)
    if m:
        bg = '\n    <ImageView android:id="@+id/xc_bg_full" android:layout_width="match_parent" android:layout_height="match_parent" android:scaleType="centerCrop" android:src="@drawable/poster_shanjian" />'
        c = c[:m.end()] + bg + c[m.end():]
        write(p, c)

# Wallpaper is set via BaseActivity code (setBackgroundResource on android.R.id.content)
# No XML changes needed - keeps it dynamic for future user wallpaper switching
# (Previous XML ImageView/background attempts removed to avoid hardcoding)

p = os.path.join(MOBILE_RES, "color/selector_nav.xml")
c = read(p)
c = c.replace('android:color="?attr/colorPrimary" android:state_checked="true"', 'android:color="#F0A400" android:state_checked="true"')
c = c.replace('android:color="@color/white" android:state_checked="false"', 'android:color="#171B23" android:state_checked="false"')
c = c.replace('android:color="@color/black" android:state_checked="false"', 'android:color="#171B23" android:state_checked="false"')
write(p, c)

p = os.path.join(MOBILE_RES, "drawable/shape_item.xml")
c = read(p)
c = c.replace('android:radius="4dp"', 'android:radius="10dp"')
write(p, c)

for fp in glob.glob(os.path.join(MOBILE_RES, "layout/fragment_setting*.xml")):
    c = read(fp)
    assert "MaterialToolbar" in c
    if 'app:navigationIcon=' not in c:
        assert c.count("app:navigationIconTint=") == 1
        c = c.replace("app:navigationIconTint=", 'app:navigationIcon="@null"\n        app:navigationIconTint=', 1)
        write(fp, c)

for fp in glob.glob(os.path.join(MOBILE_RES, "**/*.xml"), recursive=True):
    c = read(fp)
    if "#F5F0E8" in c or "#FFF5F0E8" in c:
        c = c.replace("#FFF5F0E8", "#FFFFFFFF")
        c = c.replace("#F5F0E8", "#FFFFFF")
        write(fp, c)

p = os.path.join(MOBILE_RES, "drawable/xc_bottombar_pill.xml")
pill = '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#57FFFFFF" />
    <stroke android:width="1dp" android:color="#61FFFFFF" />
    <corners android:radius="10dp" />
</shape>
'''
write(p, pill)

p = os.path.join(BASE, "app/src/mobile/AndroidManifest.xml")
c = read(p)
idx = c.find(".ui.activity.HomeActivity")
assert idx > 0
block_end = c.find("</activity>", idx)
assert block_end > idx
block = c[idx:block_end]
assert block.count('@style/Theme.Splash') == 1
block = block.replace("@style/Theme.Splash", "@style/Theme.App", 1)
write(p, c[:idx] + block + c[block_end:])

p = os.path.join(MOBILE_JAVA, "ui/activity/HomeActivity.java")
c = read(p)
old_live = "mBinding.navigation.getMenu().findItem(R.id.live).setVisible(LiveConfig.hasUrl());"
assert c.count(old_live) == 1
new_live = "mBinding.navigation.getMenu().findItem(R.id.live).setVisible(true);"
c = c.replace(old_live, new_live, 1)
old_handler = "        if (item.getItemId() == R.id.live) return openLive();"
assert c.count(old_handler) == 1
new_handler = old_handler
c = c.replace(old_handler, new_handler, 1)
write(p, c)

p = os.path.join(MOBILE_RES, "values/styles.xml")
c = read(p)
old_base = '<style name="Theme.Base" parent="Theme.Material3.DynamicColors.DayNight.NoActionBar">'
assert c.count(old_base) == 1
new_base = old_base + '\n        <item name="android:windowBackground">@drawable/poster_shanjian</item>'
c = c.replace(old_base, new_base, 1)
write(p, c)

config_layout = '''<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingLeft="16dp"
        android:paddingRight="16dp"
        android:paddingTop="48dp"
        android:paddingBottom="16dp">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="配置源"
            android:textColor="#171B23"
            android:textSize="24sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />
        <LinearLayout
            android:id="@+id/card_vod"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:clickable="true"
            android:focusable="true"
            android:foreground="?attr/selectableItemBackground"
            android:gravity="center_vertical"
            android:minHeight="64dp"
            android:orientation="horizontal"
            android:padding="15dp">
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="点播"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
            </LinearLayout>
            <ImageView
                android:layout_width="18dp"
                android:layout_height="18dp"
                android:src="@drawable/ic_chev_v2" />
        </LinearLayout>
        <LinearLayout
            android:id="@+id/card_live"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:clickable="true"
            android:focusable="true"
            android:foreground="?attr/selectableItemBackground"
            android:gravity="center_vertical"
            android:minHeight="64dp"
            android:orientation="horizontal"
            android:padding="15dp">
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="直播"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
            </LinearLayout>
            <ImageView
                android:layout_width="18dp"
                android:layout_height="18dp"
                android:src="@drawable/ic_chev_v2" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
'''
write(os.path.join(MOBILE_RES, "layout/activity_config_source.xml"), config_layout)

ui_layout = '''<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingLeft="16dp"
        android:paddingRight="16dp"
        android:paddingTop="48dp"
        android:paddingBottom="16dp">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="界面"
            android:textColor="#171B23"
            android:textSize="24sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="horizontal"
            android:padding="15dp"
            android:gravity="center_vertical">
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="主题" android:textColor="#171B23" android:textSize="16sp" android:textStyle="bold" />
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="当前为浅色主题" android:textColor="#5A5F68" android:textSize="12sp" />
            </LinearLayout>
            <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="horizontal" android:background="@drawable/xc_seg_container" android:padding="4dp">
                <TextView android:id="@+id/theme_light" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="浅色" android:textSize="14sp" android:background="@drawable/xc_seg_selected" />
                <TextView android:id="@+id/theme_dark" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="深色" android:textSize="14sp" />
                <TextView android:id="@+id/theme_system" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="跟随系统" android:textSize="14sp" />
            </LinearLayout>
        </LinearLayout>
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="horizontal"
            android:padding="15dp"
            android:gravity="center_vertical">
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="首页封面" android:textColor="#171B23" android:textSize="16sp" android:textStyle="bold" />
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="一行显示 3 个海报" android:textColor="#5A5F68" android:textSize="12sp" />
            </LinearLayout>
            <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="horizontal" android:background="@drawable/xc_seg_container" android:padding="4dp">
                <TextView android:id="@+id/cover_small" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="小" android:textSize="14sp" android:clickable="true" android:focusable="true" />
                <TextView android:id="@+id/cover_medium" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="中" android:textSize="14sp" android:background="@drawable/xc_seg_selected" android:clickable="true" android:focusable="true" />
                <TextView android:id="@+id/cover_large" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="大" android:textSize="14sp" android:clickable="true" android:focusable="true" />
            </LinearLayout>
        </LinearLayout>
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="horizontal"
            android:padding="15dp"
            android:gravity="center_vertical">
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="封面方向" android:textColor="#171B23" android:textSize="16sp" android:textStyle="bold" />
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="海报按 2:3 竖版显示" android:textColor="#5A5F68" android:textSize="12sp" />
            </LinearLayout>
            <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="horizontal" android:background="@drawable/xc_seg_container" android:padding="4dp">
                <TextView android:id="@+id/orient_portrait" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="竖屏" android:textSize="14sp" android:background="@drawable/xc_seg_selected" android:clickable="true" android:focusable="true" />
                <TextView android:id="@+id/orient_landscape" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="横屏" android:textSize="14sp" android:clickable="true" android:focusable="true" />
            </LinearLayout>
        </LinearLayout>
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="horizontal"
            android:padding="15dp"
            android:gravity="center_vertical">
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="封面比例" android:textColor="#171B23" android:textSize="16sp" android:textStyle="bold" />
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="竖屏比例 2:3" android:textColor="#5A5F68" android:textSize="12sp" />
            </LinearLayout>
            <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="horizontal" android:background="@drawable/xc_seg_container" android:padding="4dp">
                <TextView android:id="@+id/ratio_23" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="2:3" android:textSize="14sp" android:background="@drawable/xc_seg_selected" android:clickable="true" android:focusable="true" />
                <TextView android:id="@+id/ratio_34" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="3:4" android:textSize="14sp" android:clickable="true" android:focusable="true" />
                <TextView android:id="@+id/ratio_916" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="9:16" android:textSize="14sp" android:clickable="true" android:focusable="true" />
            </LinearLayout>
        </LinearLayout>
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="horizontal"
            android:padding="15dp"
            android:gravity="center_vertical">
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="UI 风格" android:textColor="#171B23" android:textSize="16sp" android:textStyle="bold" />
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="毛玻璃：面板半透明" android:textColor="#5A5F68" android:textSize="12sp" />
            </LinearLayout>
            <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="horizontal" android:background="@drawable/xc_seg_container" android:padding="4dp">
                <TextView android:id="@+id/style_normal" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="普通" android:textSize="14sp" android:clickable="true" android:focusable="true" />
                <TextView android:id="@+id/style_glass" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="毛玻璃" android:textSize="14sp" android:background="@drawable/xc_seg_selected" android:clickable="true" android:focusable="true" />
            </LinearLayout>
        </LinearLayout>
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="vertical"
            android:padding="15dp">
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="毛玻璃透明度" android:textColor="#171B23" android:textSize="16sp" android:textStyle="bold" />
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="越往右越通透" android:textColor="#5A5F68" android:textSize="12sp" android:layout_marginBottom="8dp" />
            <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center_vertical">
                <SeekBar android:id="@+id/glass_alpha" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:max="100" android:progress="55" />
                <TextView android:id="@+id/glass_alpha_text" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="55%" android:textColor="#171B23" android:textSize="14sp" android:layout_marginLeft="12dp" />
            </LinearLayout>
        </LinearLayout>
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="vertical"
            android:padding="15dp">
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="壁纸" android:textColor="#171B23" android:textSize="16sp" android:textStyle="bold" android:layout_marginBottom="4dp" />
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="选预置壁纸、纯色或上传自己的图片" android:textColor="#5A5F68" android:textSize="12sp" android:layout_marginBottom="12dp" />
            <ImageView android:id="@+id/wp_shanjian" android:layout_width="120dp" android:layout_height="160dp" android:scaleType="centerCrop" android:src="@drawable/poster_shanjian" android:clickable="true" android:focusable="true" android:foreground="?attr/selectableItemBackground" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
'''
write(os.path.join(MOBILE_RES, "layout/activity_ui_settings.xml"), ui_layout)

wp_thumb = '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="12dp" />
    <stroke android:width="2dp" android:color="#E0E0E0" />
</shape>
'''
write(os.path.join(MOBILE_RES, "drawable/xc_wp_thumb.xml"), wp_thumb)

seg_selected = '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="20dp" />
    <solid android:color="#f0a400" />
</shape>
'''
write(os.path.join(MOBILE_RES, "drawable/xc_seg_selected.xml"), seg_selected)

seg_normal = '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="20dp" />
    <solid android:color="#FFFFFF" />
    <stroke android:width="1dp" android:color="#E0E0E0" />
</shape>
'''
write(os.path.join(MOBILE_RES, "drawable/xc_seg_normal.xml"), seg_normal)

seg_container = '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="20dp" />
    <solid android:color="#FFFFFF" />
</shape>
'''
write(os.path.join(MOBILE_RES, "drawable/xc_seg_container.xml"), seg_container)

switch_thumb = '''<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_checked="true">
        <shape android:shape="oval">
            <solid android:color="#f0a400" />
            <size android:width="24dp" android:height="24dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="oval">
            <solid android:color="#FFFFFF" />
            <size android:width="24dp" android:height="24dp" />
        </shape>
    </item>
</selector>
'''
write(os.path.join(MOBILE_RES, "drawable/xc_switch_thumb.xml"), switch_thumb)

player_layout = '''<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingLeft="16dp"
        android:paddingRight="16dp"
        android:paddingTop="48dp"
        android:paddingBottom="16dp">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="播放器"
            android:textColor="#171B23"
            android:textSize="24sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="horizontal"
            android:padding="15dp"
            android:gravity="center_vertical">
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="播放器内核"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="切换视频解码播放核心，切换后立即生效"
                    android:textColor="#5A5F68"
                    android:textSize="12sp" />
            </LinearLayout>
            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="@drawable/xc_seg_container" android:padding="4dp">
                <TextView android:id="@+id/kernel_exo" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="ExoPlayer" android:textSize="14sp" android:background="@drawable/xc_seg_selected" />
                <TextView android:id="@+id/kernel_mpv" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="mpv" android:textSize="14sp" />
                <TextView android:id="@+id/kernel_ijk" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="ijk" android:textSize="14sp" />
            </LinearLayout>
        </LinearLayout>
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="horizontal"
            android:padding="15dp"
            android:gravity="center_vertical">
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="解码方式"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="硬解兼容性更好，软解画质更稳"
                    android:textColor="#5A5F68"
                    android:textSize="12sp" />
            </LinearLayout>
            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="@drawable/xc_seg_container" android:padding="4dp">
                <TextView android:id="@+id/decode_hard" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="硬解" android:textSize="14sp" android:background="@drawable/xc_seg_selected" />
                <TextView android:id="@+id/decode_soft" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="16dp" android:paddingRight="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="软解" android:textSize="14sp" />
            </LinearLayout>
        </LinearLayout>
        <LinearLayout
            android:id="@+id/card_autonext"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="horizontal"
            android:padding="15dp"
            android:gravity="center_vertical"
            android:clickable="true"
            android:focusable="true">
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="自动连播"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="本集播完自动播放下一集"
                    android:textColor="#5A5F68"
                    android:textSize="12sp" />
            </LinearLayout>
            <Switch android:id="@+id/switch_autonext" android:layout_width="wrap_content" android:layout_height="wrap_content" android:checked="true"  android:thumb="@drawable/xc_switch_thumb"/>
        </LinearLayout>
        <LinearLayout
            android:id="@+id/card_skip"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="horizontal"
            android:padding="15dp"
            android:gravity="center_vertical"
            android:clickable="true"
            android:focusable="true">
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="跳过片头片尾"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="自动跳过已标记的片头与片尾片段"
                    android:textColor="#5A5F68"
                    android:textSize="12sp" />
            </LinearLayout>
            <Switch android:id="@+id/switch_skip" android:layout_width="wrap_content" android:layout_height="wrap_content" android:checked="false"  android:thumb="@drawable/xc_switch_thumb"/>
        </LinearLayout>
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="horizontal"
            android:padding="15dp"
            android:gravity="center_vertical">
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="默认倍速"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="播放时在播放页也可临时调速"
                    android:textColor="#5A5F68"
                    android:textSize="12sp" />
            </LinearLayout>
            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="@drawable/xc_seg_container" android:padding="4dp">
                <TextView android:id="@+id/speed_075" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="12dp" android:paddingRight="12dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="0.75x" android:textSize="13sp" />
                <TextView android:id="@+id/speed_100" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="12dp" android:paddingRight="12dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="1.0x" android:textSize="13sp" android:background="@drawable/xc_seg_selected" />
                <TextView android:id="@+id/speed_125" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="12dp" android:paddingRight="12dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="1.25x" android:textSize="13sp" />
                <TextView android:id="@+id/speed_150" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="12dp" android:paddingRight="12dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="1.5x" android:textSize="13sp" />
                <TextView android:id="@+id/speed_200" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="12dp" android:paddingRight="12dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="2.0x" android:textSize="13sp" />
            </LinearLayout>
        </LinearLayout>
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="horizontal"
            android:padding="15dp"
            android:gravity="center_vertical">
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="长按倍速" android:textColor="#171B23" android:textSize="16sp" android:textStyle="bold" />
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="播放时长按屏幕快进倍速" android:textColor="#5A5F68" android:textSize="12sp" />
            </LinearLayout>
            <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="horizontal" android:background="@drawable/xc_seg_container" android:padding="4dp">
                <TextView android:id="@+id/lp_off" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="12dp" android:paddingRight="12dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="关闭" android:textSize="13sp" android:clickable="true" android:focusable="true" />
                <TextView android:id="@+id/lp_20" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="12dp" android:paddingRight="12dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="2.0x" android:textSize="13sp" android:clickable="true" android:focusable="true" />
                <TextView android:id="@+id/lp_30" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="12dp" android:paddingRight="12dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="3.0x" android:textSize="13sp" android:background="@drawable/xc_seg_selected" android:clickable="true" android:focusable="true" />
                <TextView android:id="@+id/lp_40" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="12dp" android:paddingRight="12dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="4.0x" android:textSize="13sp" android:clickable="true" android:focusable="true" />
                <TextView android:id="@+id/lp_50" android:layout_width="wrap_content" android:layout_height="wrap_content" android:paddingLeft="12dp" android:paddingRight="12dp" android:paddingTop="8dp" android:paddingBottom="8dp" android:text="5.0x" android:textSize="13sp" android:clickable="true" android:focusable="true" />
            </LinearLayout>
        </LinearLayout>
        <LinearLayout
            android:id="@+id/card_danmu"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="horizontal"
            android:padding="15dp"
            android:gravity="center_vertical"
            android:clickable="true"
            android:focusable="true">
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="弹幕设置"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="已开启"
                    android:textColor="#5A5F68"
                    android:textSize="12sp" />
            </LinearLayout>
            <ImageView android:layout_width="18dp" android:layout_height="18dp" android:src="@drawable/ic_chev_v2" />
        </LinearLayout>
        <LinearLayout
            android:id="@+id/card_subtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:background="@drawable/xc_setcard_v2"
            android:orientation="horizontal"
            android:padding="15dp"
            android:gravity="center_vertical"
            android:clickable="true"
            android:focusable="true">
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="字幕设置"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="已开启"
                    android:textColor="#5A5F68"
                    android:textSize="12sp" />
            </LinearLayout>
            <ImageView android:layout_width="18dp" android:layout_height="18dp" android:src="@drawable/ic_chev_v2" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
'''
write(os.path.join(MOBILE_RES, "layout/activity_player_settings.xml"), player_layout)

print("xingchen-clean-patch: all done")
