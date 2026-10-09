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

import com.fongmi.android.tv.databinding.DialogShieldWebviewBinding;
import com.fongmi.android.tv.subtitle.ShieldBypass;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.WebViewUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 字幕站过盾：毛玻璃居中弹窗，内嵌 WebView 让人完成人机验证。
 * 点「完成验证」后从 CookieManager 取 Cookie 存入 ShieldBypass，回调用 onVerified()。
 */
public class ShieldWebViewDialog extends BaseAlertDialog {

    private DialogShieldWebviewBinding binding;
    private String providerId;
    private String verifyUrl;
    private OnVerifiedListener listener;

    public static ShieldWebViewDialog create() {
        return new ShieldWebViewDialog();
    }

    public ShieldWebViewDialog provider(String providerId) {
        this.providerId = providerId;
        this.verifyUrl = ShieldBypass.verifyUrl(providerId);
        return this;
    }

    public ShieldWebViewDialog listener(OnVerifiedListener listener) {
        this.listener = listener;
        return this;
    }

    public void show(FragmentActivity activity) {
        if (TextUtils.isEmpty(verifyUrl)) return;
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof ShieldWebViewDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogShieldWebviewBinding.inflate(getLayoutInflater());
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
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void initView() {
        binding.tvTitle.setText(ShieldBypass.providerName(providerId) + " 需要验证");
        setupWebView();
        binding.webview.loadUrl(verifyUrl);
    }

    @Override
    protected void initEvent() {
        binding.ivClose.setOnClickListener(v -> dismissAllowingStateLoss());
        binding.btnReload.setOnClickListener(v -> binding.webview.reload());
        binding.btnDone.setOnClickListener(v -> onDone());
    }

    private void setupWebView() {
        WebView webView = binding.webview;
        WebViewUtil.configureBase(webView, "shield");
        WebSettingsHolder.apply(webView);
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
        String host = ShieldBypass.hostOf(verifyUrl);
        String cookie = "";
        try {
            cookie = CookieManager.getInstance().getCookie(verifyUrl);
        } catch (Throwable ignored) {
        }
        if (TextUtils.isEmpty(cookie)) {
            Notify.show("还没拿到 Cookie，请先完成页面上的验证");
            return;
        }
        ShieldBypass.saveCookie(host, cookie);
        Notify.show("验证完成，已保存，下次自动带上");
        if (listener != null) listener.onVerified();
        dismissAllowingStateLoss();
    }

    @Override
    public void onDestroyView() {
        try {
            if (binding != null && binding.webview != null) {
                binding.webview.stopLoading();
                binding.webview.setWebViewClient(null);
                binding.webview.setWebChromeClient(null);
                binding.webview.destroy();
            }
        } catch (Throwable ignored) {
        }
        super.onDestroyView();
    }

    /** 小封装：移动端 UA + 视口，避免被识别为爬虫 UA */
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

    public interface OnVerifiedListener {
        void onVerified();
    }
}
