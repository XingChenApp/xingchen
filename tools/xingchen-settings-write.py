import os

BASE = "/tmp/webhtv-src"
MOBILE_RES = os.path.join(BASE, "app/src/mobile/res")
MOBILE_JAVA = os.path.join(BASE, "app/src/mobile/java/com/fongmi/android/tv")

def read(p):
    with open(p, "r", encoding="utf-8") as f:
        return f.read()

def write(p, c):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        f.write(c)

layout = '''<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/poster_shanjian"
    android:fillViewport="true"
    android:fitsSystemWindows="false">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingLeft="16dp"
        android:paddingRight="16dp"
        android:paddingTop="0dp"
        android:paddingBottom="16dp">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp"
            android:text="设置"
            android:textColor="#171B23"
            android:textSize="22sp"
            android:textStyle="bold" />
        <LinearLayout
            android:id="@+id/card_config"
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
                    android:text="当前配置"
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
            android:id="@+id/card_player"
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
                    android:text="播放器"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
                <TextView
                    android:id="@+id/text_player_sub"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="3dp"
                    android:text="ExoPlayer·硬解·1.0x"
                    android:textColor="#2E3A4E"
                    android:textSize="13sp" />
            </LinearLayout>
            <ImageView
                android:layout_width="18dp"
                android:layout_height="18dp"
                android:src="@drawable/ic_chev_v2" />
        </LinearLayout>
        <LinearLayout
            android:id="@+id/card_appearance"
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
                    android:text="界面"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
                <TextView
                    android:id="@+id/text_appearance_sub"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="3dp"
                    android:text="浅色主题·中封面·竖屏 2:3·默认壁纸"
                    android:textColor="#2E3A4E"
                    android:textSize="13sp" />
            </LinearLayout>
            <ImageView
                android:layout_width="18dp"
                android:layout_height="18dp"
                android:src="@drawable/ic_chev_v2" />
        </LinearLayout>
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:layout_marginBottom="12dp"
            android:text="插件"
            android:textColor="#171B23"
            android:textSize="14sp"
            android:textStyle="bold" />
        <LinearLayout
            android:id="@+id/card_plugin"
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
                    android:text="插件"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
                <TextView
                    android:id="@+id/text_plugin_sub"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="3dp"
                    android:text="已安装 5 个插件，点进去管理开关、删除和导入"
                    android:textColor="#2E3A4E"
                    android:textSize="13sp" />
            </LinearLayout>
            <ImageView
                android:layout_width="18dp"
                android:layout_height="18dp"
                android:src="@drawable/ic_chev_v2" />
        </LinearLayout>
        <LinearLayout
            android:id="@+id/card_features"
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
                    android:text="个性功能"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
                <TextView
                    android:id="@+id/text_features_sub"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="3dp"
                    android:text="TMDB 未配置"
                    android:textColor="#2E3A4E"
                    android:textSize="13sp" />
            </LinearLayout>
            <ImageView
                android:layout_width="18dp"
                android:layout_height="18dp"
                android:src="@drawable/ic_chev_v2" />
        </LinearLayout>
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:layout_marginBottom="12dp"
            android:text="源管理"
            android:textColor="#171B23"
            android:textSize="14sp"
            android:textStyle="bold" />
        <LinearLayout
            android:id="@+id/card_health"
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
                    android:text="源健康检测"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
                <TextView
                    android:id="@+id/text_health_sub"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="3dp"
                    android:text="上次检测：2 个可用·1 个不可用"
                    android:textColor="#2E3A4E"
                    android:textSize="13sp" />
            </LinearLayout>
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginEnd="8dp"
                android:text="刚刚"
                android:textColor="#4D5665"
                android:textSize="13sp" />
            <ImageView
                android:layout_width="18dp"
                android:layout_height="18dp"
                android:src="@drawable/ic_chev_v2" />
        </LinearLayout>
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:layout_marginBottom="12dp"
            android:text="关于"
            android:textColor="#171B23"
            android:textSize="14sp"
            android:textStyle="bold" />
        <LinearLayout
            android:id="@+id/card_about"
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
                    android:text="星辰"
                    android:textColor="#171B23"
                    android:textSize="16sp"
                    android:textStyle="bold" />
                <TextView
                    android:id="@+id/text_about_sub"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="3dp"
                    android:text="版本 0.1.0（UI 草稿）"
                    android:textColor="#2E3A4E"
                    android:textSize="13sp" />
            </LinearLayout>
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginEnd="8dp"
                android:text="已是最新"
                android:textColor="#4D5665"
                android:textSize="13sp" />
            <ImageView
                android:layout_width="18dp"
                android:layout_height="18dp"
                android:src="@drawable/ic_chev_v2" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
'''

write(os.path.join(MOBILE_RES, "layout/fragment_setting.xml"), layout)

card_drawable = '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="10dp" />
    <solid android:color="#57FFFFFF" />
    <stroke android:width="1dp" android:color="#61FFFFFF" />
</shape>
'''
write(os.path.join(MOBILE_RES, "drawable/xc_setcard_v2.xml"), card_drawable)

chev_drawable = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="18dp"
    android:height="18dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#2E3A4E"
        android:pathData="M9,6l6,6l-6,6" />
</vector>
'''
write(os.path.join(MOBILE_RES, "drawable/ic_chev_v2.xml"), chev_drawable)

home_layout_path = os.path.join(MOBILE_RES, "layout/activity_home.xml")
if os.path.exists(home_layout_path):
    home_content = read(home_layout_path)
    if 'fitsSystemWindows' not in home_content:
        home_content = home_content.replace(
            '<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"',
            '<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"\n    android:fitsSystemWindows="false"'
        )
        write(home_layout_path, home_content)
        print("activity_home patched")

print("settings layout written")
