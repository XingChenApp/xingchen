package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.ActivityPanLoginBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.PanLoginDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PanAuth;

public class PanLoginActivity extends BaseActivity {

    private ActivityPanLoginBinding binding;

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSub();
    }

    private void refreshSub() {
        if (PanAuth.is115LoggedIn()) {
            String vip = PanAuth.get115Vip();
            binding.tvPan115Sub.setText(vip.isEmpty() ? "已登录" : "已登录 · " + vip);
        } else {
            binding.tvPan115Sub.setText("未登录");
        }
        if (PanAuth.isQuarkLoggedIn()) {
            String vip = PanAuth.getQuarkVip();
            binding.tvPanQuarkSub.setText(vip.isEmpty() ? "已登录" : "已登录 · " + vip);
        } else {
            binding.tvPanQuarkSub.setText("未登录");
        }
    }

    private void on115Click() {
        if (PanAuth.is115LoggedIn()) {
            PanLoginDialog.create()
                    .title("115")
                    .logoutAction(() -> {
                        PanAuth.clear115();
                        refreshSub();
                        Notify.show("已退出 115 登录");
                    })
                    .onRelogin(this::show115Login)
                    .showOptions(this);
        } else {
            show115Login();
        }
    }

    private void show115Login() {
        PanLoginDialog.create()
                .title("115")
                .loginUrl("https://115.com/")
                .cookieDomain("115.com")
                .hint("请在下方页面完成 115 登录，然后点「完成登录」")
                .onLogin(cookie -> {
                    PanAuth.put115Cookie(cookie);
                    refreshSub();
                    Notify.show("115 登录成功");
                })
                .showLogin(this);
    }

    private void onQuarkClick() {
        if (PanAuth.isQuarkLoggedIn()) {
            PanLoginDialog.create()
                    .title("夸克")
                    .logoutAction(() -> {
                        PanAuth.clearQuark();
                        refreshSub();
                        Notify.show("已退出夸克登录");
                    })
                    .onRelogin(this::showQuarkLogin)
                    .showOptions(this);
        } else {
            showQuarkLogin();
        }
    }

    private void showQuarkLogin() {
        PanLoginDialog.create()
                .title("夸克")
                .loginUrl("https://pan.quark.cn/")
                .cookieDomain("quark.cn")
                .hint("请在下方页面完成夸克登录，然后点「完成登录」")
                .onLogin(cookie -> {
                    PanAuth.putQuarkCookie(cookie);
                    refreshSub();
                    Notify.show("夸克登录成功");
                })
                .showLogin(this);
    }
}
