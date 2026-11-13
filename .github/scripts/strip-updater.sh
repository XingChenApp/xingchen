#!/usr/bin/env bash
# 朋友版专用：构建前把「更新」换成只检测版（潇洒哥 2026-11-11 15:08 亲令
# 「朋友版可以把那个检测更新加上」）。只在 CI 的临时检出上执行，仓库源码
# 不动。手法：不删 Updater 类，把 Updater.java 本体换成同名同签名的只查版
# （payload 内嵌在本脚本里，不单置文件——公开镜像的内部脚本删除清单是逐
# 文件名列的，内嵌即随本脚本一起不进镜像）；入口三处：
#   BaseActivity.resume(this)      → 每进程首次静默查一次（真源原样）
#   设置页版本卡 → force().start(this) → 手动查（2026-11-11 正式版检测
#     更新全摘后适配：真源版本卡只弹关于弹窗，本脚本在临时检出上把版本
#     卡点击行换挂到只查版 force().start 并补回 Updater import；正式版
#     关于弹窗在朋友版点版本卡不再弹，此形态差已在全摘账里点名）
#   App.notifyModules()            → 只查版置空实现（真源原样）
# 只查版行为：查公开仓（XingChenApp/xingchen 将来翻公开后的）发布页
# （payload 内写死公开仓匿名 API 口：Github.java 常量已随正式版改指
# 私有仓 xingchen-main，朋友版检测不许跟它走、只认公开仓，见 payload
# 内 FRIEND_LATEST_API），有新版只
# 弹提示、「去下载」开浏览器，不下载不安装；仓库未公开/查不到一律静默，
# 不弹错不挡路。任何一处对不上就让构建失败，防重构改写法后朋友版悄悄
# 变回全链更新或丢了检测。
set -euo pipefail

fail() { echo "::error title=朋友版换检测更新失败::$1"; exit 1; }

UPDATER=app/src/main/java/com/fongmi/android/tv/Updater.java
[ -f "$UPDATER" ] || fail "找不到 Updater.java"
grep -q "FORMAL_UPDATE_REMOVED" "$UPDATER" || fail "Updater.java 不是正式版零检测空壳（注入前对不上）"

# 入口三处先对位（注入后还要复查同一组，一行不许少）
BASE=app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java
grep -q "import com.fongmi.android.tv.Updater;" "$BASE" || fail "BaseActivity 的 Updater import 对不上"
grep -q "Updater.create().resume(this);" "$BASE" || fail "BaseActivity 的 resume 调用对不上"
SETA=app/src/mobile/java/com/fongmi/android/tv/ui/activity/XingChenSettingsActivity.java
grep -qF "binding.cardVersion.setOnClickListener(v -> AboutDialog.show(this));" "$SETA" || fail "设置页版本卡点击行对不上（正式版全摘形态）"
APP=app/src/main/java/com/fongmi/android/tv/App.java
grep -q "Updater.create().notifyModules();" "$APP" || fail "App 的模块更新提示调用对不上"

# 注入只查版 Updater（同包同名同签名，入口零改动）
cat > "$UPDATER" <<'JAVAEOF'
package com.fongmi.android.tv;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.update.UpdateHttp;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 朋友版专用 Updater（构建时由 strip-updater.sh 注入，只存在于朋友版产物）：
 * 只检测、不下载、不安装。打开时（BaseActivity.resume 每进程首次）静默查
 * 一次，设置里可手动查（force().start）；查的是公开仓发布页（Github 发布的
 * latest 口，与正式版同一地址常量），仓库未公开或网络不通时查不到就静默，
 * 不弹错不挡路；有新版只弹提示，「去下载」用浏览器打开下载地址。
 * FRIEND_UPDATE_CHECK_ONLY
 */
public class Updater {

    private static final Map<String, String> GITHUB_API_HEADERS = Map.of("Accept", "application/vnd.github+json", "X-GitHub-Api-Version", "2022-11-28");
    private static final long CHECK_TIMEOUT_MS = 4000;
    // 朋友版只查公开仓发布（两仓口径）：写死公开仓匿名 API，不走 Github.java
    // 常量——那组常量已随正式版改指私有仓，朋友版跟去即查错仓。
    private static final String FRIEND_LATEST_API = "https://api.github.com/repos/XingChenApp/xingchen/releases/latest";
    private static final Updater INSTANCE = new Updater();

    private final AtomicBoolean silentChecked = new AtomicBoolean(false);
    private volatile boolean manual;
    private WeakReference<FragmentActivity> activityRef;

    private Updater() {
    }

    public static Updater create() {
        return INSTANCE;
    }

    public Updater force() {
        manual = true;
        Notify.show(R.string.update_check);
        return this;
    }

    public void start(FragmentActivity activity) {
        bind(activity);
        boolean manualCheck = manual;
        manual = false;
        check(activity, manualCheck);
    }

    public void resume(FragmentActivity activity) {
        bind(activity);
        if (!silentChecked.compareAndSet(false, true)) return;
        check(activity, false);
    }

    public void notifyModules() {
        // 朋友版无外置模块更新提示，置空实现（App 调用点保持原样）。
    }

    private void bind(FragmentActivity activity) {
        if (activity == null) return;
        activityRef = new WeakReference<>(activity);
    }

    private void check(FragmentActivity activity, boolean manualCheck) {
        if (activity == null) return;
        Task.execute(() -> {
            String[] result = fetchLatest();
            App.post(() -> onResult(manualCheck, result));
        });
    }

    /** 查公开仓最新发布：返回 {新版本号, 下载地址}；已是最新返回 {""}；查不到返回 null（静默）。 */
    private String[] fetchLatest() {
        try {
            String text = UpdateHttp.string(FRIEND_LATEST_API, GITHUB_API_HEADERS, CHECK_TIMEOUT_MS);
            JSONObject release = new JSONObject(text);
            String tag = release.optString("tag_name");
            String version = (tag.startsWith("v") || tag.startsWith("V")) ? tag.substring(1) : tag;
            if (TextUtils.isEmpty(version) || compareVersion(version, BuildConfig.VERSION_NAME) <= 0) return new String[]{""};
            String url = "";
            JSONArray assets = release.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset != null && asset.optString("name").endsWith(".apk")) {
                        url = asset.optString("browser_download_url");
                        break;
                    }
                }
            }
            if (TextUtils.isEmpty(url)) url = release.optString("html_url");
            if (TextUtils.isEmpty(url)) return null;
            return new String[]{version, url};
        } catch (Exception e) {
            ulog("friend check failed err=%s", String.valueOf(e.getMessage()));
            return null;
        }
    }

    private void onResult(boolean manualCheck, String[] result) {
        if (result == null) return; // 查不到：静默，不弹错不挡路
        if (result.length == 1) { // 已是最新
            if (manualCheck) Notify.show(R.string.update_latest);
            return;
        }
        FragmentActivity activity = activityRef == null ? null : activityRef.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (activity.getSupportFragmentManager().isStateSaved()) return;
        String version = result[0];
        String url = result[1];
        new AlertDialog.Builder(activity)
                .setTitle("发现新版本")
                .setMessage("星辰 " + version + " 已发布，是否前往下载？")
                .setPositiveButton("去下载", (dialog, which) -> openUrl(activity, url))
                .setNegativeButton("以后再说", null)
                .show();
    }

    private void openUrl(FragmentActivity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    /** 逐段数字比版号；解析不出按 0 段处理（比不出新旧即不提示）。 */
    private static int compareVersion(String a, String b) {
        String[] pa = String.valueOf(a).split("\\.");
        String[] pb = String.valueOf(b).split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? leadingNumber(pa[i]) : 0;
            int vb = i < pb.length ? leadingNumber(pb[i]) : 0;
            if (va != vb) return va > vb ? 1 : -1;
        }
        return 0;
    }

    private static int leadingNumber(String segment) {
        int value = 0;
        boolean any = false;
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c < '0' || c > '9') break;
            value = value * 10 + (c - '0');
            any = true;
        }
        return any ? value : 0;
    }

    private static void ulog(String fmt, Object... args) {
        try {
            com.github.catvod.crawler.SpiderDebug.log("update", fmt, args);
        } catch (Throwable ignored) {
        }
    }
}
JAVAEOF

grep -q "FRIEND_UPDATE_CHECK_ONLY" "$UPDATER" || fail "朋友版 Updater 注入后标记对不上"
# 检测地址复查：只许查公开仓，混入私有仓地址即失败（防 Github.java 改指漂移带偏）
grep -q "repos/XingChenApp/xingchen/releases/latest" "$UPDATER" || fail "朋友版 Updater 未指向公开仓发布口"
if grep -q "xingchen-main" "$UPDATER"; then
  fail "朋友版 Updater 里混入私有仓地址"
fi
# 下载/安装全链不许出现在只查版 Updater 里（类引用逐词拦）
if grep -q "UpdateDialog\|UpdateTransfer\|HttpUpdateTransfer\|OciUpdateTransfer\|reconcileLocalFile\|FileUtil\|RuntimeInstaller" "$UPDATER"; then
  fail "朋友版 Updater 里混入了下载/安装全链引用"
fi

# 朋友版手动查挂载（2026-11-11 正式版全摘后适配）：真源设置页版本卡点击
# 只弹关于弹窗（AboutDialog 的检查更新钮已随全摘摘除），朋友版的手动查
# 改挂同一张版本卡——点击行换成只查版 force().start、补回 Updater import。
# 「更新方式」卡（cardUpdateSource）已随正式版全摘整卡摘除，朋友版天然
# 无此卡，不再需要旧 C-18 的置不可见收口；这里反过来断言它已摘净。
grep -qF "binding.cardVersion.setOnClickListener(v -> AboutDialog.show(this));" "$SETA" || fail "设置页版本卡点击行对不上"
sed -i "s|binding\.cardVersion\.setOnClickListener(v -> AboutDialog\.show(this));|binding.cardVersion.setOnClickListener(v -> Updater.create().force().start(this));|" "$SETA"
grep -qF "binding.cardVersion.setOnClickListener(v -> Updater.create().force().start(this));" "$SETA" || fail "设置页版本卡改手动查后复查对不上"
grep -q "import com.fongmi.android.tv.ui.dialog.AboutDialog;" "$SETA" || fail "设置页 AboutDialog import 锚对不上"
sed -i "s|import com.fongmi.android.tv.ui.dialog.AboutDialog;|import com.fongmi.android.tv.Updater;\nimport com.fongmi.android.tv.ui.dialog.AboutDialog;|" "$SETA"
grep -q "import com.fongmi.android.tv.Updater;" "$SETA" || fail "设置页补 Updater import 后复查对不上"
if grep -rn "cardUpdateSource" app/src/mobile/java app/src/mobile/res --include=*.java >/dev/null; then
  fail "正式版更新方式卡未摘净（朋友版不该再有此卡）"
fi
grep -rq "card_update_source" app/src/mobile/res/layout/activity_xingchen_settings.xml && fail "正式版更新方式卡布局未摘净"

# 复查一：入口三处仍在（朋友版检测的三个口一个不许少）
grep -q "Updater.create().resume(this);" "$BASE" || fail "朋友版 BaseActivity 静默查入口丢了"
grep -q "Updater.create().force().start(this)" "$SETA" || fail "朋友版设置页手动查入口丢了"
grep -q "Updater.create().notifyModules();" "$APP" || fail "朋友版 App 调用点丢了"
# 复查二：更新方式面已随正式版全摘整件摘除——mobile 里不许再有
# UpdateSettingsDialog 的任何引用（类本体已删，残引即编译错兼入口残留）。
if grep -rn "UpdateSettingsDialog" app/src/mobile/java --include=*.java; then
  fail "朋友版设置页仍有更新设置残留"
fi
# leanback 提示（口径沿用 X-20）：工作流只编 assembleMobileArm64_v8aRelease，
# leanback 源集不在本次构建内；其 BaseActivity/SettingActivity 两处 Updater
# 入口如今会落到同一个只查版 Updater（签名齐全），不是泄漏；编 leanback
# 朋友版之前须另行复核其弹窗形态。
if grep -rn "Updater\.create()" app/src/leanback/java --include=*.java >/dev/null; then
  echo "::warning title=朋友版 leanback 更新入口::leanback 源集不在 mobile 构建内，本次不处理；编 leanback 朋友版前须复核"
fi
echo "朋友版：检测更新已换成只查版（打开静默查＋设置手动查，只提示去下载，不下载不安装）"
