package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.cloud.PanAccountInfo;
import com.fongmi.android.tv.cloud.drivepikpak.DrivePikPak;
import com.fongmi.android.tv.login.LoginManager;
import com.fongmi.android.tv.cloud.drivequark.DriveQuark;
import com.fongmi.android.tv.databinding.ActivityPanLoginBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.PanLoginDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PanAuth;
import com.fongmi.android.tv.utils.QRCode;
import com.fongmi.android.tv.utils.ResUtil;

public class PanLoginActivity extends BaseActivity {

    private ActivityPanLoginBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed;
    private int qrLoginGen;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, PanLoginActivity.class));
    }

    public static String loginSummary(Activity activity) {
        return PanAuth.loginSummary();
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityPanLoginBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        refreshSub();
        binding.cardPan115.setOnClickListener(v -> on115Click());
        binding.cardPanQuark.setOnClickListener(v -> onQuarkClick());
        binding.cardPanPikpak.setOnClickListener(v -> onPikPakClick());
        binding.cardPan115.setOnLongClickListener(v -> on115LongClick());
        binding.cardPanQuark.setOnLongClickListener(v -> onQuarkLongClick());
        binding.cardPanPikpak.setOnLongClickListener(v -> onPikPakLongClick());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSub();
    }

    private void refreshSub() {
        if (PanAuth.is115LoggedIn()) {
            binding.tvPan115Sub.setText(accountSub(PanAuth.get115Vip(), PanAuth.get115Space()));
        } else {
            binding.tvPan115Sub.setText("未登录");
        }
        if (PanAuth.isQuarkLoggedIn()) {
            binding.tvPanQuarkSub.setText(accountSub(PanAuth.getQuarkVip(), PanAuth.getQuarkSpace()));
        } else {
            binding.tvPanQuarkSub.setText("未登录");
        }
        if (PanAuth.isPikPakLoggedIn()) {
            String name = PanAuth.getPikPakUserName();
            String vip = PanAuth.getPikPakVip();
            // 身份位优先用户名（原有口径），没名时用会员文案顶上
            String identity = !name.isEmpty() ? name : vip;
            binding.tvPanPikpakSub.setText(accountSub(identity, PanAuth.getPikPakSpace()));
        } else {
            binding.tvPanPikpakSub.setText("未登录");
        }
        // 容量/VIP 按需后台抓取（登录/过期自动失效），回来后刷一次副标题
        PanAccountInfo.maybeFetch(() -> {
            if (!destroyed && !isFinishing()) refreshSub();
        });
    }

    /** 副标题拼装：「已登录 · VIP · 已用/总量」；两样都没有时回退原提示 */
    private static String accountSub(String vip, String space) {
        boolean noVip = vip == null || vip.isEmpty();
        boolean noSpace = space == null || space.isEmpty();
        if (noVip && noSpace) return "已登录，点击进入文件";
        StringBuilder sb = new StringBuilder("已登录");
        if (!noVip) sb.append(" · ").append(vip);
        if (!noSpace) sb.append(" · ").append(space);
        return sb.toString();
    }

    // ================= 115 =================

    private void on115Click() {
        if (PanAuth.is115LoggedIn()) {
            CloudDriveActivity.start(this, "115");
        } else {
            showLoginMethods("115", new String[]{"扫码登录", "网页登录（支持账号密码）", "导入 Cookie"},
                    which -> {
                        if (which == 0) showQrLogin115();
                        else if (which == 1) base115Dialog().showLogin(this);
                        else showCookieImport("115");
                    });
        }
    }

    private boolean on115LongClick() {
        if (!PanAuth.is115LoggedIn()) return false;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("115")
                .setMessage("退出当前 115 登录？")
                .setPositiveButton("退出登录", (d, w) -> {
                    PanAuth.clear115();
                    refreshSub();
                    Notify.show("已退出 115 登录");
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        return true;
    }

    private PanLoginDialog base115Dialog() {
        return PanLoginDialog.create()
                .title("115")
                .loginUrl("https://115.com/")
                .cookieDomain("115.com")
                .userAgent(PanLoginDialog.DESKTOP_UA)
                .hint("请在下方页面完成 115 登录（支持账号密码），然后点「完成登录」")
                .cookieValidator(PanAuth::is115CookieValid)
                .onLogin(cookie -> {
                    PanAuth.put115Cookie(cookie);
                    refreshSub();
                    Notify.show("115 登录成功");
                });
    }

    /** 115 原生扫码登录（对照 PY QrLogin，真机验证过的流程） */
    private void showQrLogin115() {
        LoginManager.get().login115(this, new LoginManager.LoginCallback() {
            @Override
            public void onSuccess(String providerId, String cookie) {
                // Qr115Provider.confirm() 已写入 PanAuth
                refreshSub();
                Notify.show("115 登录成功");
            }
            @Override
            public void onError(String msg) {
                Notify.show("登录失败：" + msg);
            }
            @Override
            public void onCancel() {
            }
        });
    }

    // ================= 夸克 =================

    private void onQuarkClick() {
        if (PanAuth.isQuarkLoggedIn()) {
            CloudDriveActivity.start(this, "quark");
        } else {
            showLoginMethods("夸克", new String[]{"扫码登录", "网页登录（支持账号密码）", "导入 Cookie"},
                    which -> {
                        if (which == 0) showQrLoginQuark();
                        else if (which == 1) baseQuarkDialog().showLogin(this);
                        else showCookieImport("夸克");
                    });
        }
    }

    private boolean onQuarkLongClick() {
        if (!PanAuth.isQuarkLoggedIn()) return false;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("夸克")
                .setMessage("退出当前夸克登录？")
                .setPositiveButton("退出登录", (d, w) -> {
                    PanAuth.clearQuark();
                    refreshSub();
                    Notify.show("已退出夸克登录");
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        return true;
    }

    private PanLoginDialog baseQuarkDialog() {
        return PanLoginDialog.create()
                .title("夸克")
                .loginUrl("https://pan.quark.cn/")
                .cookieDomain("quark.cn")
                .userAgent(PanLoginDialog.DESKTOP_UA)
                .hint("请在下方页面完成夸克登录（支持账号密码），然后点「完成登录」")
                .onLogin(cookie -> {
                    PanAuth.putQuarkCookie(cookie);
                    refreshSub();
                    Notify.show("夸克登录成功");
                });
    }

    /** 夸克原生扫码登录（来源：h1d3rone/quarkmanager 二维码流程，已验证） */
    private void showQrLoginQuark() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = ResUtil.dp2px(24);
        layout.setPadding(pad, pad, pad, pad);
        ImageView qrView = new ImageView(this);
        int size = ResUtil.dp2px(220);
        qrView.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        layout.addView(qrView);
        TextView tvStatus = new TextView(this);
        tvStatus.setText("正在获取二维码…");
        tvStatus.setTextSize(14);
        tvStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ResUtil.dp2px(12);
        tvStatus.setLayoutParams(lp);
        layout.addView(tvStatus);

        final int qrGen = ++qrLoginGen;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("夸克扫码登录")
                .setView(layout)
                .setNegativeButton("取消", null)
                .setCancelable(true)
                .create();
        dialog.setOnDismissListener(d -> {
            if (qrGen == qrLoginGen) qrLoginGen++;
            handler.removeCallbacksAndMessages(null);
        });
        dialog.show();

        DriveQuark drive = new DriveQuark();
        new Thread(() -> {
            try {
                DriveQuark.QrSession session = drive.qrStart();
                Bitmap bmp = QRCode.getBitmap(session.qrUrl, 220, 1);
                runOnUiThread(() -> {
                    if (qrGen != qrLoginGen || destroyed) return;
                    if (bmp != null) qrView.setImageBitmap(bmp);
                    tvStatus.setText("请用夸克 App 内扫一扫（不要用系统相机/微信扫）");
                });
                Runnable poll = new Runnable() {
                    @Override
                    public void run() {
                        if (qrGen != qrLoginGen || destroyed) return;
                        new Thread(() -> {
                            try {
                                int r = drive.qrPoll(session);
                                if (qrGen != qrLoginGen || destroyed) return;
                                if (r == 2) {
                                    boolean ok = drive.qrLogin(session);
                                    if (qrGen != qrLoginGen || destroyed) return;
                                    runOnUiThread(() -> {
                                        if (qrGen != qrLoginGen || destroyed || isFinishing()) return;
                                        dialog.dismiss();
                                        if (ok) {
                                            PanAuth.putQuarkCookie(drive.getCookie());
                                            refreshSub();
                                            Notify.show("夸克登录成功");
                                        } else {
                                            Notify.show("登录失败，请重试");
                                        }
                                    });
                                } else if (r == -1) {
                                    runOnUiThread(() -> {
                                        if (qrGen == qrLoginGen && !destroyed) tvStatus.setText("二维码已失效，请关闭重试");
                                    });
                                } else {
                                    runOnUiThread(() -> {
                                        if (qrGen == qrLoginGen && !destroyed) tvStatus.setText("等待扫码…");
                                    });
                                    handler.postDelayed(this, 2500);
                                }
                            } catch (Exception e) {
                                if (qrGen != qrLoginGen || destroyed) return;
                                runOnUiThread(() -> {
                                    if (qrGen == qrLoginGen && !destroyed) tvStatus.setText("网络异常，稍后重试");
                                });
                                handler.postDelayed(this, 2500);
                            }
                        }).start();
                    }
                };
                if (qrGen == qrLoginGen && !destroyed) handler.postDelayed(poll, 2500);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (qrGen == qrLoginGen && !destroyed) tvStatus.setText("获取二维码失败：" + e.getMessage());
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        qrLoginGen++;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ================= PikPak =================

    private void onPikPakClick() {
        if (PanAuth.isPikPakLoggedIn()) {
            CloudDriveActivity.start(this, "pikpak");
        } else {
            showPikPakLoginMethods();
        }
    }

    /**
     * PikPak 登录方式选择。
     * 网页登录（推荐）：WebView 内完成登录，绕开 OkHttp 直调登录接口被风控掐连接的问题；
     * 账号密码登录：原有原生 API 链路，保留作备用。
     */
    private void showPikPakLoginMethods() {
        showLoginMethods("PikPak", new String[]{"网页登录（推荐）", "账号密码登录"}, which -> {
            if (which == 0) {
                PikPakWebLoginActivity.start(this);
            } else {
                showPikPakLogin();
            }
        });
    }

    private boolean onPikPakLongClick() {
        if (!PanAuth.isPikPakLoggedIn()) return false;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("PikPak")
                .setMessage("退出当前 PikPak 登录？")
                .setPositiveButton("退出登录", (d, w) -> {
                    PanAuth.clearPikPak();
                    refreshSub();
                    Notify.show("已退出 PikPak 登录");
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        return true;
    }

    /** PikPak 账号密码登录（原生 API，来源：pikpak-sdk/auth.go Login） */
    private void showPikPakLogin() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = ResUtil.dp2px(24);
        layout.setPadding(pad, pad, pad, pad);
        EditText etUser = new EditText(this);
        etUser.setHint("邮箱 / 手机号");
        etUser.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        layout.addView(etUser);
        EditText etPass = new EditText(this);
        etPass.setHint("密码");
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ResUtil.dp2px(12);
        etPass.setLayoutParams(lp);
        layout.addView(etPass);
        TextView tvTip = new TextView(this);
        tvTip.setText("大陆 IP 访问 PikPak 受限，请先开系统 VPN");
        tvTip.setTextSize(12);
        tvTip.setLayoutParams(lp);
        layout.addView(tvTip);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("PikPak 登录")
                .setView(layout)
                .setPositiveButton("登录", (d, w) -> {
                    String u = etUser.getText().toString().trim();
                    String p = etPass.getText().toString();
                    if (TextUtils.isEmpty(u) || TextUtils.isEmpty(p)) {
                        Notify.show("用户名和密码不能为空");
                        return;
                    }
                    doPikPakLogin(u, p, "");
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
    }

    /** PikPak 账号密码登录（后台线程；captchaToken 为空表示首次尝试） */
    private void doPikPakLogin(String u, String p, String captchaToken) {
        doPikPakLogin(u, p, captchaToken, 0);
    }

    private void doPikPakLogin(String u, String p, String captchaToken, int attempt) {
        Notify.show("正在登录…");
        new Thread(() -> {
            try {
                new DrivePikPak(this).login(u, p, captchaToken);
                runOnUiThread(() -> {
                    PanAuth.invalidatePikPakCache();
                    refreshSub();
                    Notify.show("PikPak 登录成功");
                });
            } catch (DrivePikPak.CaptchaRequiredException ce) {
                if (attempt >= 2) {
                    runOnUiThread(() -> Notify.show("人机验证失败，请稍后重试"));
                } else {
                    runOnUiThread(() -> showPikPakCaptcha(u, p, ce, attempt));
                }
            } catch (Exception e) {
                runOnUiThread(() -> Notify.show("登录失败：" + e.getMessage()));
            }
        }).start();
    }

    /**
     * PikPak 人机验证：在内置 WebView 打开服务端下发的验证页。
     *
     * 关键点（对着 pikpak-sdk / Alist 文档核过）：
     * 1. 滑块完成后服务端会下发一个「新的」captcha_token（跟登录接口返回的那个不一样），
     *    它藏在验证页的网络请求响应里；必须用新 token 重试，复用旧 token 会一直失败。
     * 2. WebView 默认 UA 带 "; wv" 标记，会被风控直接拦截，改成正常 Chrome UA。
     * 3. WebView 的 Cookie 要和 OkHttp 共享（DrivePikPak 已加 WebViewCookieJar）。
     */
    private void showPikPakCaptcha(String u, String p, DrivePikPak.CaptchaRequiredException ce, int attempt) {
        String url = ce.getVerifyUrl();
        String token = ce.getCaptchaToken();
        if (TextUtils.isEmpty(url)) {
            // 服务端没下发验证页：F12 手动填 token 在手机上不可用，引导改用网页登录
            Notify.show("未能获取验证页，请改用网页登录");
            PikPakWebLoginActivity.start(this);
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = ResUtil.dp2px(16);
        layout.setPadding(pad, pad, pad, 0);
        TextView tvTip = new TextView(this);
        tvTip.setText("请在下方页面完成滑块验证，看到「已捕获验证凭证」后再点「已完成验证」");
        tvTip.setTextSize(13);
        layout.addView(tvTip);
        TextView tvState = new TextView(this);
        tvState.setText("等待验证…");
        tvState.setTextSize(12);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = ResUtil.dp2px(8);
        tvState.setLayoutParams(slp);
        layout.addView(tvState);

        // JS 回调抓到的新 captcha_token（跟 ce 里那个旧的不一样）
        final String[] verifiedToken = new String[1];

        WebView web = new WebView(this);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ResUtil.dp2px(440));
        wlp.topMargin = ResUtil.dp2px(12);
        web.setLayoutParams(wlp);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        // 去掉 WebView 标记，用正常 Chrome 移动端 UA，否则风控不放行
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(web, true);
        }
        final String oldToken = token == null ? "" : token;
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onCaptchaToken(final String t) {
                if (TextUtils.isEmpty(t) || t.equals(oldToken)) return;
                verifiedToken[0] = t;
                runOnUiThread(() -> tvState.setText("已捕获验证凭证，可以点「已完成验证」"));
            }
        }, "XingChenCaptcha");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 有些验证页完成时会跳到带新 token 的 URL
                String t = extractCaptchaToken(request.getUrl().toString());
                if (!TextUtils.isEmpty(t) && !t.equals(oldToken)) {
                    verifiedToken[0] = t;
                    runOnUiThread(() -> tvState.setText("已捕获验证凭证，可以点「已完成验证」"));
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                super.onPageFinished(view, pageUrl);
                // 注入 XHR/fetch 钩子：验证完成时服务端下发的新 captcha_token 藏在接口响应里
                String js = "(function(){"
                        + "var ORIG='" + oldToken.replace("'", "") + "';"
                        + "function pick(s){try{var m=String(s).match(/\"captcha_token\"\\s*:\\s*\"([^\"]+)\"/);"
                        + "if(m&&m[1]&&m[1]!==ORIG&&window.XingChenCaptcha){window.XingChenCaptcha.onCaptchaToken(m[1]);}}catch(e){}}"
                        + "try{var _s=XMLHttpRequest.prototype.send;"
                        + "XMLHttpRequest.prototype.send=function(){"
                        + "this.addEventListener('load',function(){try{pick(this.responseText);}catch(e){}});"
                        + "return _s.apply(this,arguments);};}catch(e){}"
                        + "try{if(window.fetch){var _f=window.fetch;"
                        + "window.fetch=function(){return _f.apply(this,arguments).then(function(r){"
                        + "try{r.clone().text().then(pick).catch(function(){})}catch(e){}return r;});};}}catch(e){}"
                        + "try{var q=location.search.match(/[?&]captcha_token=([^&]+)/);"
                        + "if(q&&q[1]&&decodeURIComponent(q[1])!==ORIG&&window.XingChenCaptcha)"
                        + "{window.XingChenCaptcha.onCaptchaToken(decodeURIComponent(q[1]));}}catch(e){}"
                        + "})();";
                view.evaluateJavascript(js, null);
            }
        });
        web.loadUrl(url);
        layout.addView(web);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("PikPak 人机验证")
                .setView(layout)
                .setPositiveButton("已完成验证，继续登录", null)
                .setNegativeButton("取消", null)
                .create();
        dlg.setOnDismissListener(d -> web.destroy());
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { CookieManager.getInstance().flush(); } catch (Exception ignored) {}
            dlg.dismiss();
            String useToken = !TextUtils.isEmpty(verifiedToken[0]) ? verifiedToken[0] : oldToken;
            if (TextUtils.isEmpty(verifiedToken[0])) {
                Notify.show("没抓到新的验证凭证，用旧 token 重试一次");
            }
            doPikPakLogin(u, p, useToken, attempt + 1);
        });
    }

    /** 从 URL 里抠 captcha_token 参数 */
    private static String extractCaptchaToken(String url) {
        if (url == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[?&]captcha_token=([^&]+)").matcher(url);
        if (!m.find()) return "";
        try {
            return java.net.URLDecoder.decode(m.group(1), "UTF-8");
        } catch (Exception e) {
            return m.group(1);
        }
    }

    /**
     * @deprecated F12 手动填 captcha_token 在手机上不可用，已下线全部入口（保留代码备查）。
     * 新流程请走 {@link PikPakWebLoginActivity} 网页登录。
     *
     * 原兜底流程（跟 Alist 文档同一套）：在电脑/手机浏览器打开验证页完成滑块，
     * 按 F12 在网络请求里找到 captcha_token，粘贴到这里。
     */
    @Deprecated
    private void showPikPakManualToken(String u, String p, int attempt) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = ResUtil.dp2px(24);
        layout.setPadding(pad, pad, pad, pad);
        TextView tvTip = new TextView(this);
        tvTip.setText("在浏览器打开验证页完成滑块后，按 F12 在网络请求里找到 captcha_token，粘贴到下面");
        tvTip.setTextSize(13);
        layout.addView(tvTip);
        EditText et = new EditText(this);
        et.setHint("粘贴 captcha_token");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ResUtil.dp2px(12);
        et.setLayoutParams(lp);
        layout.addView(et);

        new AlertDialog.Builder(this)
                .setTitle("手动填写验证凭证")
                .setView(layout)
                .setPositiveButton("用此 token 登录", (d, w) -> {
                    String t = et.getText().toString().trim();
                    if (TextUtils.isEmpty(t)) {
                        Notify.show("token 是空的");
                        return;
                    }
                    doPikPakLogin(u, p, t, attempt + 1);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= 通用 =================

    private interface OnMethodSelected {
        void onSelect(int which);
    }

    private void showLoginMethods(String title, String[] methods, OnMethodSelected cb) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title + "登录方式")
                .setItems(methods, (d, which) -> cb.onSelect(which))
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
    }

    /** Cookie 导入（Alist 风格：直接粘贴 Cookie 字符串） */
    private void showCookieImport(String title) {
        EditText et = new EditText(this);
        et.setHint("粘贴 Cookie 字符串");
        et.setMinLines(4);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        int pad = ResUtil.dp2px(24);
        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("导入 " + title + " Cookie")
                .setView(layout)
                .setPositiveButton("导入", (d, w) -> {
                    String cookie = et.getText().toString().trim();
                    if (TextUtils.isEmpty(cookie)) {
                        Notify.show("Cookie 是空的");
                        return;
                    }
                    if ("115".equals(title)) {
                        if (!PanAuth.is115CookieValid(cookie)) {
                            Notify.show("Cookie 无效：缺少 115 登录关键字段");
                            return;
                        }
                        PanAuth.put115Cookie(cookie);
                    } else {
                        PanAuth.putQuarkCookie(cookie);
                    }
                    refreshSub();
                    Notify.show(title + " Cookie 已导入");
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
    }
}
