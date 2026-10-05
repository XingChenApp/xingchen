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
            assert "xc_bottombar" in hc or "GradientDrawable" in hc, "HomeActivity background patch failed"
            hc = hc.replace(
                "mBinding.navigation.setItemActiveIndicatorEnabled(false); }",
                "mBinding.navigation.setItemActiveIndicatorEnabled(false); }\\n        mBinding.navigation.setOnItemSelectedListener(item -> { boolean r = onNavigationItemSelected(item); android.view.View iv = mBinding.navigation.findViewById(item.getItemId()); if (iv != null) iv.animate().scaleX(1.2f).scaleY(1.2f).setDuration(120).withEndAction(() -> iv.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start(); return r; });"
            )
            assert "scaleX(1.2f)" in hc, "HomeActivity animation patch failed"
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
            "super.onCreate(savedInstanceState);\n        getWindow().setBackgroundDrawableResource(com.fongmi.android.tv.R.drawable.poster_shanjian);"
        )
        assert "poster_shanjian" in bc, "BaseActivity patch failed"
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
