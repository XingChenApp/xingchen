package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.fongmi.android.tv.api.hk.HkHttpParser;
import com.fongmi.android.tv.utils.ToastUtil;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 照抄 8.83 LivePlayerHelper（smali_classes2/ui/video/remote/LivePlayerHelper.smali 及 $1/$2/$3/$4/$4$1）
 * 与 SettingConfig 的世界直播地址存取：世界直播是局域网内电视端软件的 HTTP 远程接口
 * （GET /api/remote/version 探版本，POST /api/remote/batch 批量投屏，POST /api/remote/update 单条投屏，
 * GET /api/updateUrl?title=&url= 老版单条投屏）；地址为空时经 HkScanLiveTvUtils 扫描发现。
 * 承载差异（本树既有口径）：原版 CodeUtil(OkGo) 换 OkHttp、回调统一回主线程（原版 OkGo 回调即主线程）；
 * XPopup 的 LoadingPopupView 换原生加载框；地址变换的 PlayerChooser.getThirdPlaySource 与
 * LocalServerParser.getRealUrlForRemotedPlay 本树无对应件，恒等直通（本树播放链同样没有这两层）。
 */
public final class HkLivePlayerHelper {

    /** 对应原版 CodeUtil.OnCodeGetListener。 */
    public interface CodeListener {
        void onSuccess(String body);

        void onFailure(int code, String msg);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHttpClient CLIENT = new OkHttpClient();

    /** 对应原版 SettingConfig.liveTvAddress 静态字段。 */
    private static String liveTvAddress;

    private HkLivePlayerHelper() {
    }

    // —— SettingConfig 地址存取（PreferenceMgr 文件 "custom"）——

    /** 照抄 SettingConfig.loadLiveAddress：liveTvAddressLocal 非空则先覆盖回 liveTvAddress，再读入静态字段。 */
    public static void loadLiveAddress(Context context) {
        SharedPreferences sp = context.getSharedPreferences("custom", Context.MODE_PRIVATE);
        String local = sp.getString("liveTvAddressLocal", "");
        if (!TextUtils.isEmpty(local)) {
            sp.edit().putString("liveTvAddress", local).apply();
        }
        liveTvAddress = sp.getString("liveTvAddress", null);
    }

    /** 照抄 SettingConfig.setLiveTvAddress：写静态字段 + PreferenceMgr "custom"/"liveTvAddress"。 */
    public static void setLiveTvAddress(Context context, String address) {
        liveTvAddress = address;
        context.getSharedPreferences("custom", Context.MODE_PRIVATE)
                .edit().putString("liveTvAddress", address).apply();
    }

    // —— HTTP（对应 CodeUtil.get/post，回调回主线程）——

    private static void get(String url, CodeListener listener) {
        http(new Request.Builder().url(url).get().build(), listener);
    }

    private static void postJson(String url, String json, CodeListener listener) {
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        http(new Request.Builder().url(url).post(body).build(), listener);
    }

    private static void http(Request request, final CodeListener listener) {
        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                final String msg = e.getMessage() == null ? "" : e.getMessage();
                MAIN.post(() -> listener.onFailure(-1, msg));
            }

            @Override
            public void onResponse(Call call, Response response) {
                String body = "";
                try {
                    ResponseBody rb = response.body();
                    if (rb != null) body = rb.string();
                } catch (Exception ignored) {
                } finally {
                    response.close();
                }
                final String text = body;
                if (response.isSuccessful()) {
                    MAIN.post(() -> listener.onSuccess(text));
                } else {
                    final int code = response.code();
                    MAIN.post(() -> listener.onFailure(code, text));
                }
            }
        });
    }

    private static void toast(Context context, String text) {
        if (context != null) ToastUtil.show(context, text, android.widget.Toast.LENGTH_SHORT);
    }

    // —— 扫描（对应 scanLiveTvAddress）——

    /**
     * 照抄 LivePlayerHelper.scanLiveTvAddress：扫描中不重复开扫（1.4.16 改为 toast 提示稍后再试，不再静默返回）；弹「正在扫描远程地址」加载框；
     * 找到→关框、toast「扫描到远程地址：{地址}」、存地址、回调地址；
     * 没找到→关框、存空地址、toast「没有扫描到直播地址，请确认远程软件在同一个局域网内」。
     */
    public static void scanLiveTvAddress(final Activity activity, final Consumer<String> onFound) {
        if (HkScanLiveTvUtils.isScanning) {
            // 1.4.16 修复（扫查中6）：原版扫描在飞时直接 return、回调永不触发，投屏点击无声无息丢失；
            // 改为给用户可见提示（正在扫描、稍后再试），点击有明确去向，不再静默丢
            if (activity != null) {
                activity.runOnUiThread(() -> toast(activity, "正在扫描远程地址，请稍后再试"));
            }
            return;
        }
        final android.app.Dialog loading = showLoading(activity, "正在扫描远程地址");
        new HkScanLiveTvUtils().scan(
                found -> activity.runOnUiThread(() -> {
                    dismissLoading(loading);
                    toast(activity, "扫描到远程地址：" + found);
                    setLiveTvAddress(activity, found);
                    if (onFound != null) onFound.accept(found);
                }),
                fail -> activity.runOnUiThread(() -> {
                    dismissLoading(loading);
                    setLiveTvAddress(activity, "");
                    toast(activity, "没有扫描到直播地址，请确认远程软件在同一个局域网内");
                }));
    }

    private static android.app.Dialog showLoading(Activity activity, String text) {
        if (activity == null || activity.isFinishing()) return null;
        try {
            LinearLayout box = new LinearLayout(activity);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
            box.setPadding(pad, pad / 2, pad, pad / 2);
            box.addView(new ProgressBar(activity));
            TextView label = new TextView(activity);
            label.setText(text);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = pad / 2;
            box.addView(label, lp);
            android.app.Dialog dialog = new AlertDialog.Builder(activity).setView(box).create();
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
            return dialog;
        } catch (Exception e) {
            return null;
        }
    }

    private static void dismissLoading(android.app.Dialog dialog) {
        if (dialog == null) return;
        try {
            if (dialog.isShowing()) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    // —— 批量投屏完成（对应 finishBatchDlan）——

    public static void finishBatchDlan(Activity activity, List<HkRemotePlaySource> sources) {
        finishBatchDlan(activity, sources, 0);
    }

    /**
     * 照抄 LivePlayerHelper.finishBatchDlan(activity, popup, list, retry)：
     * retry&gt;2 或页面正在关闭即止；地址为空→扫描（成功后 retry+1 重入）；
     * 否则探版本：版本号&gt;1→POST /api/remote/batch（失败 toast「批量投屏失败：…」，成功无声）；
     * ≤1→toast「当前世界直播版本太低，不支持批量投屏」；
     * 探版本失败：retry&gt;0→toast「连接世界直播失败：…」，retry=0→重新扫描。
     * 原版 popup 参数在本树由 scanLiveTvAddress 自带加载框承接。
     */
    private static void finishBatchDlan(final Activity activity, final List<HkRemotePlaySource> sources, final int retry) {
        if (retry > 2 || activity == null || activity.isFinishing()) return;
        loadLiveAddress(activity);
        if (TextUtils.isEmpty(liveTvAddress)) {
            scanLiveTvAddress(activity, address -> finishBatchDlan(activity, sources, retry + 1));
            return;
        }
        final String address = liveTvAddress;
        get(address + "/api/remote/version", new CodeListener() {
            @Override
            public void onSuccess(String body) {
                int version = -1;
                try {
                    version = Integer.parseInt(body == null ? "" : body.trim());
                } catch (Exception ignored) {
                }
                if (version > 1) {
                    String json = new Gson().toJson(new HkRemotePlaySourceBatchData(sources));
                    postJson(address + "/api/remote/batch", json, new CodeListener() {
                        @Override
                        public void onSuccess(String b) {
                        }

                        @Override
                        public void onFailure(int code, String msg) {
                            toast(activity, "批量投屏失败：" + msg);
                        }
                    });
                } else {
                    toast(activity, "当前世界直播版本太低，不支持批量投屏");
                }
            }

            @Override
            public void onFailure(int code, String msg) {
                if (retry > 0) {
                    toast(activity, "连接世界直播失败：" + msg);
                } else {
                    scanLiveTvAddress(activity, found -> finishBatchDlan(activity, sources, retry + 1));
                }
            }
        });
    }

    // —— 单条世界直播投屏（对应 playWithLiveTv / rescanLiveTv / playWithLiveTvByAddress）——

    public static void playWithLiveTv(Activity activity, String title, String url) {
        playWithLiveTv(activity, title, url, false);
    }

    /**
     * 照抄 LivePlayerHelper.playWithLiveTv 五参版：从未存过地址（"custom" 里无 liveTvAddress 键）→
     * 先弹「温馨提示」首次说明框，点确定才继续；已有地址→loadLiveAddress，
     * 地址非空直投，为空走 rescanLiveTv 扫描后投。
     */
    public static void playWithLiveTv(final Activity activity, final String title, final String url, final boolean thirdSource) {
        if (activity == null) return;
        SharedPreferences sp = activity.getSharedPreferences("custom", Context.MODE_PRIVATE);
        if (!sp.contains("liveTvAddress")) {
            new HkDialogs(activity)
                    .setTitle("温馨提示")
                    .setMessage("这是你第一次使用该功能，以下是该功能的提示：该功能主要是配合第三方软件世界直播使用，主要用于将视频投放到电视上，点击即可让世界直播播放手机上播放的地址")
                    .setPositiveButton("确定", (dialog, which) -> proceedPlay(activity, title, url, thirdSource))
                    .show();
            return;
        }
        proceedPlay(activity, title, url, thirdSource);
    }

    private static void proceedPlay(Activity activity, String title, String url, boolean thirdSource) {
        loadLiveAddress(activity);
        if (!TextUtils.isEmpty(liveTvAddress)) {
            playWithLiveTvByAddress(activity, liveTvAddress, title, url, thirdSource);
        } else {
            rescanLiveTv(activity, title, url, thirdSource);
        }
    }

    /** 照抄 rescanLiveTv：扫描到地址后按地址投。 */
    public static void rescanLiveTv(final Activity activity, final String title, final String url, final boolean thirdSource) {
        scanLiveTvAddress(activity, address -> playWithLiveTvByAddress(activity, address, title, url, thirdSource));
    }

    /**
     * 照抄 playWithLiveTvByAddress + $1：先探版本——版本为空→GET 老接口；
     * 版本&gt;0→POST 新接口；≤0 或解析失败→GET 老接口；探版本失败→toast「投放失败，请长按重新扫描远程地址：{地址}」。
     * （thirdSource 在原版 $1 里不改变 GET/POST 分叉，本树唯一调用点恒 false。）
     */
    private static void playWithLiveTvByAddress(final Activity activity, final String address, final String title, final String url, boolean thirdSource) {
        get(address + "/api/remote/version", new CodeListener() {
            @Override
            public void onSuccess(String body) {
                if (TextUtils.isEmpty(body)) {
                    playByLiveTvWithGet(activity, address, title, url);
                    return;
                }
                try {
                    int version = Integer.parseInt(body.trim());
                    if (version > 0) {
                        playByLiveTvWithPost(activity, address, title, url);
                    } else {
                        playByLiveTvWithGet(activity, address, title, url);
                    }
                } catch (Exception e) {
                    playByLiveTvWithGet(activity, address, title, url);
                }
            }

            @Override
            public void onFailure(int code, String msg) {
                toast(activity, "投放失败，请长按重新扫描远程地址：" + address);
            }
        });
    }

    /** 照抄 playByLiveTvWithPost + $2：POST /api/remote/update，体为单条 RemotePlaySource 的 JSON。 */
    private static void playByLiveTvWithPost(final Context context, final String address, String title, String url) {
        HkRemotePlaySource source = new HkRemotePlaySource(title, HkHttpParser.encodeUrl(url), 0, true, null);
        postJson(address + "/api/remote/update", new Gson().toJson(source), playResultListener(context, address));
    }

    /** 照抄 playByLiveTvWithGet + $3：GET /api/updateUrl?title={标题}&url={编码后地址}（标题原版不编码，照抄）。 */
    private static void playByLiveTvWithGet(final Context context, final String address, String title, String url) {
        String encoded = HkHttpParser.encodeUrl(url);
        get(address + "/api/updateUrl?title=" + title + "&url=" + encoded, playResultListener(context, address));
    }

    /** $2/$3 共用结果口径：成功 toast「已投放到远程地址：{地址}」，失败 toast「投放失败，请长按重新扫描远程地址：{地址}」。 */
    private static CodeListener playResultListener(final Context context, final String address) {
        return new CodeListener() {
            @Override
            public void onSuccess(String body) {
                toast(context, "已投放到远程地址：" + address);
            }

            @Override
            public void onFailure(int code, String msg) {
                toast(context, "投放失败，请长按重新扫描远程地址：" + address);
            }
        };
    }
}
