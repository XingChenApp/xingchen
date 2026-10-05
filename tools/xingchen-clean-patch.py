import os
import glob
import shutil

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

# Change applicationId to com.xinhchen.tv
p = os.path.join(BASE, "app/build.gradle")
c = read(p)
c = c.replace('applicationId "com.fongmi.android.tv"', 'applicationId "com.xinhchen.tv"')
write(p, c)

def replace_once(path, old, new):
    c = read(path)
    n = c.count(old)
    assert n == 1, "expected 1, found %d for %s in %s" % (n, old[:40], path)
    write(path, c.replace(old, new, 1))

p = os.path.join(MOBILE_RES, "menu/menu_nav.xml")
c = read(p)
assert c.count('android:visible="false"') == 3
c = c.replace('android:visible="false"', 'android:visible="true"')
write(p, c)

p = os.path.join(MOBILE_RES, "values/strings.xml")
c = read(p)

p = os.path.join(MOBILE_RES, "layout/activity_home.xml")
replace_once(p, 'android:background="@color/transparent"', 'android:background="@drawable/xc_bottombar_pill"')
# Add fullscreen background ImageView behind everything
c = read(p)
if '@+id/xc_bg_full' not in c:
    import re
    m = re.search(r'<RelativeLayout[^>]*>', c)
    if m:
        bg = '\n    <ImageView android:id="@+id/xc_bg_full" android:layout_width="match_parent" android:layout_height="match_parent" android:scaleType="centerCrop" android:src="@drawable/poster_shanjian" />'
        c = c[:m.end()] + bg + c[m.end():]
        write(p, c)

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
# windowBackground not used; background via ImageView instead
write(p, c)

print("xingchen-clean-patch: all done")
