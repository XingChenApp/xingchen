package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogGlobalProxyBinding;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.XingChenProxy;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 全局 HTTP/SOCKS5 代理设置弹窗。
 * 开启后全部流量（点播/直播/播放器/PY源）都走该代理；与“走壳代理”互斥，优先于走壳代理。
 */
public class GlobalProxyDialog extends BaseAlertDialog {

    private DialogGlobalProxyBinding binding;
    private int type = XingChenProxy.TYPE_OFF;

    public static GlobalProxyDialog create() {
        return new GlobalProxyDialog();
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof GlobalProxyDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        binding = DialogGlobalProxyBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.CENTER);
        WindowManager.LayoutParams params = window.getAttributes();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        float density = getResources().getDisplayMetrics().density;
        params.width = Math.min((int) (screenWidth * 0.94), (int) (700 * density));
        window.setAttributes(params);
    }

    @Override
    protected void initView() {
        type = XingChenProxy.getType(requireContext());
        binding.etHost.setText(XingChenProxy.getHost(requireContext()));
        int port = XingChenProxy.getPort(requireContext());
        binding.etPort.setText(port > 0 ? String.valueOf(port) : "");
        binding.etUser.setText(XingChenProxy.getUser(requireContext()));
        binding.etPass.setText(XingChenProxy.getPass(requireContext()));
        refreshTabs();
    }

    @Override
    protected void initEvent() {
        binding.tabOff.setOnClickListener(v -> {
            type = XingChenProxy.TYPE_OFF;
            refreshTabs();
        });
        binding.tabHttp.setOnClickListener(v -> {
            type = XingChenProxy.TYPE_HTTP;
            refreshTabs();
        });
        binding.tabSocks.setOnClickListener(v -> {
            type = XingChenProxy.TYPE_SOCKS5;
            refreshTabs();
        });
        binding.btnTest.setOnClickListener(v -> testProxy());
        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnConfirm.setOnClickListener(v -> saveAndClose());
    }

    private void refreshTabs() {
        styleTab(binding.tabOff, type == XingChenProxy.TYPE_OFF);
        styleTab(binding.tabHttp, type == XingChenProxy.TYPE_HTTP);
        styleTab(binding.tabSocks, type == XingChenProxy.TYPE_SOCKS5);
    }

    private void styleTab(android.widget.TextView tab, boolean selected) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.shape_shield_tab_selected);
            tab.setTextColor(0xFFFFFFFF);
        } else {
            tab.setBackground(null);
            tab.setTextColor(0xB3000000);
        }
    }

    private int parsePort() {
        try {
            return Integer.parseInt(binding.etPort.getText().toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void saveAndClose() {
        String host = binding.etHost.getText().toString().trim();
        int port = parsePort();
        String user = binding.etUser.getText().toString().trim();
        String pass = binding.etPass.getText().toString();
        if (type != XingChenProxy.TYPE_OFF) {
            if (!XingChenProxy.validHost(host)) {
                Notify.show("请填写代理主机");
                return;
            }
            if (!XingChenProxy.validPort(port)) {
                Notify.show("端口无效（1-65535）");
                return;
            }
        }
        XingChenProxy.save(requireContext(), type, host, port, user, pass);
        Notify.show(type == XingChenProxy.TYPE_OFF ? "全局代理已关闭" : "全局代理已启用");
        dismiss();
    }

    private void testProxy() {
        String host = binding.etHost.getText().toString().trim();
        int port = parsePort();
        String user = binding.etUser.getText().toString().trim();
        String pass = binding.etPass.getText().toString();
        int t = type;
        if (t == XingChenProxy.TYPE_OFF) {
            Notify.show("请先选择代理类型");
            return;
        }
        if (!XingChenProxy.validHost(host) || !XingChenProxy.validPort(port)) {
            Notify.show("请先填写正确的主机和端口");
            return;
        }
        binding.btnTest.setText("测试中…");
        binding.btnTest.setEnabled(false);
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean ok = false;
            String msg;
            try {
                Proxy.Type pt = t == XingChenProxy.TYPE_SOCKS5 ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
                OkHttpClient.Builder b = new OkHttpClient.Builder()
                        .proxy(new Proxy(pt, new InetSocketAddress(host, port)))
                        .connectTimeout(8, TimeUnit.SECONDS)
                        .readTimeout(8, TimeUnit.SECONDS)
                        .writeTimeout(8, TimeUnit.SECONDS);
                if (!TextUtils.isEmpty(user) && pt == Proxy.Type.HTTP) {
                    String u = user, p = pass;
                    b.proxyAuthenticator((route, response) -> {
                        String cred = Credentials.basic(u, p);
                        if (cred.equals(response.request().header("Proxy-Authorization"))) return null;
                        return response.request().newBuilder().header("Proxy-Authorization", cred).build();
                    });
                }
                try (Response resp = b.build().newCall(new Request.Builder().url("https://www.baidu.com").build()).execute()) {
                    ok = resp.isSuccessful();
                    msg = "HTTP " + resp.code();
                }
            } catch (Exception e) {
                msg = e.getMessage() != null ? e.getMessage() : "连接失败";
            }
            boolean fok = ok;
            String fmsg = msg;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isAdded() || binding == null) return;
                binding.btnTest.setText("测试代理");
                binding.btnTest.setEnabled(true);
                Notify.show(fok ? "代理可用（" + fmsg + "）" : "代理不可用：" + fmsg);
            });
        });
    }
}
