import os

BASE = "/tmp/webhtv-src"

def read(p):
    with open(p, "r", encoding="utf-8") as f:
        return f.read()

def write(p, c):
    with open(p, "w", encoding="utf-8") as f:
        f.write(c)

BASE_JAVA = os.path.join(BASE, "app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java")
if os.path.exists(BASE_JAVA):
    bc = read(BASE_JAVA)
    if "poster_shanjian" not in bc:
        bc = bc.replace(
            "super.onCreate(savedInstanceState);",
            "super.onCreate(savedInstanceState);\n        { getWindow().setStatusBarColor(0x00000000); android.view.ViewGroup xc_decor = (android.view.ViewGroup) getWindow().getDecorView(); xc_decor.post(() -> { getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN); android.view.View xc_content = findViewById(android.R.id.content); if (xc_content != null) { xc_content.setBackgroundResource(com.fongmi.android.tv.R.drawable.poster_shanjian); } }); }"
        )
        write(BASE_JAVA, bc)
        print("BaseActivity patched")
