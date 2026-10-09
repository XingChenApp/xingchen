package com.fongmi.android.tv.ui.dialog;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogPanLoginBinding;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.WebViewUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class PanLoginDialog extends BaseAlertDialog {

    public interface OnLoginListener {
        void onLogin(String cookie);
    }

    public interface OnLogoutListener {
        void onLogout();
    }

    public interface CookieValidator {
        boolean isValid(String cookie);
    }

    private static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    public static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private DialogPanLoginBinding binding;
    private String title;
    private String loginUrl;
    private String cookieDomain;
    private String hint;
    private String userAgent = MOBILE_UA;
    private OnLoginListener loginListener;
    private OnLogoutListener logoutListener;
    private CookieValidator cookieValidator;
    private boolean optionsMode;

    public static PanLoginDialog create() {
        return new PanLoginDialog();
    }

    public PanLoginDialog title(String title) {
        this.title = title;
        return this;
    }

    public PanLoginDialog loginUrl(String url) {
        this.loginUrl = url;
        return this;
    }

    public PanLoginDialog cookieDomain(String domain) {
        this.cookieDomain = domain;
        return this;
    }

    public PanLoginDialog hint(String hint) {
        this.hint = hint;
        return this;
    }

    public PanLoginDialog onLogin(OnLoginListener listener) {
        this.loginListener = listener;
        return this;
    }

    public PanLoginDialog logoutAction(OnLogoutListener listener) {
        this.logoutListener = listener;
        return this;
    }

    public PanLoginDialog cookieValidator(CookieValidator validator) {
        this.cookieValidator = validator;
        return this;
    }

    public PanLoginDialog userAgent(String ua) {
        this.userAgent = ua;
        return this;
    }

    public void showLogin(FragmentActivity activity) {
        this.optionsMode = false;
        if (TextUtils.isEmpty(loginUrl)) return;
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof PanLoginDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    public void showOptions(FragmentActivity activity) {
        this.optionsMode = true;
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof PanLoginDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogPanLoginBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow();
    }

    private void configureWindow() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        int width = Math.min(Math.round(ResUtil.getScreenWidth(requireContext()) * 0.92f), ResUtil.dp2px(620));
        params.width = Math.max(width, ResUtil.dp2px(320));
        if (optionsMode) {
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        } else {
            params.height = Math.round(ResUtil.getScreenHeight(requireContext()) * 0.85f);
        }
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
    }

    @Override
    protected void initView() {
        binding.tvTitle.setText(title + (optionsMode ? "已登录" : "登录"));
        if (!TextUtils.isEmpty(hint)) binding.tvHint.setText(hint);
        if (optionsMode) {
            binding.webview.setVisibility(View.GONE);
            ((View) binding.webview.getParent()).setVisibility(View.GONE);
            binding.progress.setVisibility(View.GONE);
            binding.tvHint.setText("当前已登录，可重新登录或退出");
            binding.btnReload.setText("重新登录");
            binding.btnDone.setText("退出登录");
        } else {
            setupWebView();
            binding.webview.loadUrl(loginUrl);
        }
    }

    @Override
    protected void initEvent() {
        binding.ivClose.setOnClickListener(v -> dismissAllowingStateLoss());
        binding.btnReload.setOnClickListener(v -> {
            if (optionsMode) {
                switchToLoginMode();
            } else {
                binding.webview.reload();
            }
        });
        binding.btnDone.setOnClickListener(v -> {
            if (optionsMode) {
                if (logoutListener != null) logoutListener.onLogout();
                dismissAllowingStateLoss();
            } else {
                onDone();
            }
        });
    }

    /**
     * 已登录态点"重新登录"：复用当前弹窗直接切到登录模式，
     * 避免 dismiss+重建的竞态导致新弹窗被去重 guard 拦掉。
     */
    private void switchToLoginMode() {
        if (TextUtils.isEmpty(loginUrl)) return;
        optionsMode = false;
        binding.tvTitle.setText(title + "登录");
        binding.tvHint.setText(TextUtils.isEmpty(hint) ? "请在下方页面完成登录，然后点「完成登录」" : hint);
        ((View) binding.webview.getParent()).setVisibility(View.VISIBLE);
        binding.webview.setVisibility(View.VISIBLE);
        binding.progress.setVisibility(View.VISIBLE);
        binding.btnReload.setText("刷新");
        binding.btnDone.setText("完成登录");
        setupWebView();
        binding.webview.loadUrl(loginUrl);
        configureWindow();
    }

    private void setupWebView() {
        WebView webView = binding.webview;
        WebViewUtil.configureBase(webView, "panlogin");
        WebSettingsHolder.apply(webView);
        webView.getSettings().setUserAgentString(userAgent);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(webView, true);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                binding.progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                binding.progress.setVisibility(View.GONE);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
    }

    private void onDone() {
        String cookie = "";
        try {
            CookieManager cm = CookieManager.getInstance();
            String c1 = cm.getCookie("https://" + cookieDomain + "/");
            String c2 = cm.getCookie("." + cookieDomain);
            cookie = !TextUtils.isEmpty(c1) ? c1 : (!TextUtils.isEmpty(c2) ? c2 : "");
        } catch (Throwable ignored) {
        }
        if (TextUtils.isEmpty(cookie)) {
            Notify.show("未检测到登录 Cookie，请先在页面内完成登录");
            return;
        }
        if (cookieValidator != null && !cookieValidator.isValid(cookie)) {
            Notify.show("未检测到有效登录，请确认已在页面内完成登录");
            return;
        }
        if (loginListener != null) loginListener.onLogin(cookie);
        dismissAllowingStateLoss();
    }

    private static class WebSettingsHolder {
        static void apply(WebView webView) {
            android.webkit.WebSettings s = webView.getSettings();
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            s.setSupportZoom(true);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
            s.setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
        }
    }
}
