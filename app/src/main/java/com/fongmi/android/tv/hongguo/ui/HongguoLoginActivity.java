package com.fongmi.android.tv.hongguo.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.hongguo.api.HgAccountStore;
import com.fongmi.android.tv.hongguo.api.HgCallback;
import com.fongmi.android.tv.hongguo.api.HgPassportClient;

/**
 * 红果短信登录页：官方短信验证码登录（手机号＋短信验证码＋协议勾选），
 * 纯代码浅底白页。发码走 HgPassportClient.sendSmsCode（成功按服务端
 * 回的重试倒计时秒数禁用发码钮），登录走 smsLogin，成功后会话经
 * HgAccountStore.save 落盘、提示后退页。回调在后台线程，碰 UI 一律
 * 切回主线程。
 */
public class HongguoLoginActivity extends AppCompatActivity {

    public static void start(Context context) {
        Intent intent = new Intent(context, HongguoLoginActivity.class);
        HgTransition.push(context, intent);
    }

    @Override
    public void finish() {
        super.finish();
        HgTransition.applyPushExit(this);
    }

    private final HgPassportClient passportClient = new HgPassportClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText phoneInput;
    private EditText codeInput;
    private TextView sendCodeBtn;
    private CheckBox agreeBox;
    private TextView loginBtn;
    private boolean sending;
    private boolean loggingIn;
    private volatile boolean destroyed;
    private int remainSeconds;
    /** 发码倒计时截止时刻（重建存档口 E-03）：存截止点而非剩余秒，
     * 重建恢复时按真实流逝时间折算，不把倒计时凭空续满。 */
    private long countdownEndAtMs;

    private final Runnable countdownTick = new Runnable() {
        @Override
        public void run() {
            remainSeconds--;
            if (remainSeconds > 0) {
                sendCodeBtn.setText(remainSeconds + "秒后重发");
                mainHandler.postDelayed(this, 1000);
            } else {
                restoreSendCodeBtn();
            }
        }
    };

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int navBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HgWindowStyle.apply(this, true, true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(0, 0, 0, navBarHeight());

        // 顶栏：返回 ‹ ＋标题
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), statusBarHeight() + dp(6), dp(16), dp(8));
        TextView backBtn = new TextView(this);
        backBtn.setText("‹");
        backBtn.setTextColor(0xFF1B1B1E);
        backBtn.setTextSize(26);
        backBtn.setGravity(Gravity.CENTER);
        backBtn.setOnClickListener(v -> finish());
        top.addView(backBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView titleText = new TextView(this);
        titleText.setText("登录红果");
        titleText.setTextColor(0xFF1B1B1E);
        titleText.setTextSize(18);
        titleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(titleText);
        root.addView(top);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(10), dp(20), 0);
        root.addView(body);

        TextView subtitle = new TextView(this);
        subtitle.setText("使用官方短信验证码登录");
        subtitle.setTextColor(0xFF8E8E96);
        subtitle.setTextSize(14);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.bottomMargin = dp(26);
        body.addView(subtitle, subLp);

        // 手机号行：左侧固定「+86」面＋手机号输入框
        LinearLayout phoneRow = new LinearLayout(this);
        phoneRow.setOrientation(LinearLayout.HORIZONTAL);
        phoneRow.setGravity(Gravity.CENTER_VERTICAL);
        phoneRow.setBackground(roundBg(0xFFF4F4F5, dp(10)));
        phoneRow.setPadding(dp(14), 0, dp(14), 0);
        TextView prefix = new TextView(this);
        prefix.setText("+86");
        prefix.setTextColor(0xFF1B1B1E);
        prefix.setTextSize(16);
        phoneRow.addView(prefix);
        View divider = new View(this);
        divider.setBackgroundColor(0xFFDDDDE1);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(1, dp(22));
        divLp.leftMargin = dp(12);
        divLp.rightMargin = dp(12);
        phoneRow.addView(divider, divLp);
        phoneInput = new EditText(this);
        phoneInput.setHint("中国大陆手机号");
        phoneInput.setHintTextColor(0xFFABABB2);
        phoneInput.setTextColor(0xFF1B1B1E);
        phoneInput.setTextSize(16);
        phoneInput.setSingleLine(true);
        phoneInput.setBackground(null);
        phoneInput.setPadding(0, 0, 0, 0);
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(11)});
        phoneRow.addView(phoneInput, new LinearLayout.LayoutParams(0, -1, 1f));
        body.addView(phoneRow, new LinearLayout.LayoutParams(-1, dp(52)));

        // 验证码行：验证码输入框＋右侧「获取验证码」钮
        LinearLayout codeRow = new LinearLayout(this);
        codeRow.setOrientation(LinearLayout.HORIZONTAL);
        codeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams codeRowLp = new LinearLayout.LayoutParams(-1, dp(52));
        codeRowLp.topMargin = dp(12);
        LinearLayout codeBox = new LinearLayout(this);
        codeBox.setOrientation(LinearLayout.HORIZONTAL);
        codeBox.setGravity(Gravity.CENTER_VERTICAL);
        codeBox.setBackground(roundBg(0xFFF4F4F5, dp(10)));
        codeBox.setPadding(dp(14), 0, dp(14), 0);
        codeInput = new EditText(this);
        codeInput.setHint("短信验证码");
        codeInput.setHintTextColor(0xFFABABB2);
        codeInput.setTextColor(0xFF1B1B1E);
        codeInput.setTextSize(16);
        codeInput.setSingleLine(true);
        codeInput.setBackground(null);
        codeInput.setPadding(0, 0, 0, 0);
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeBox.addView(codeInput, new LinearLayout.LayoutParams(0, -1, 1f));
        codeRow.addView(codeBox, new LinearLayout.LayoutParams(0, -1, 1f));
        sendCodeBtn = new TextView(this);
        sendCodeBtn.setText("获取验证码");
        sendCodeBtn.setTextColor(0xFFF0761A);
        sendCodeBtn.setTextSize(14);
        sendCodeBtn.setGravity(Gravity.CENTER);
        sendCodeBtn.setBackground(roundBg(0xFFFFF0DB, dp(10)));
        sendCodeBtn.setOnClickListener(v -> onSendCode());
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(112), -1);
        sendLp.leftMargin = dp(10);
        codeRow.addView(sendCodeBtn, sendLp);
        body.addView(codeRow, codeRowLp);

        // 协议行：勾选框＋协议文案
        LinearLayout agreeRow = new LinearLayout(this);
        agreeRow.setOrientation(LinearLayout.HORIZONTAL);
        agreeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams agreeLp = new LinearLayout.LayoutParams(-1, -2);
        agreeLp.topMargin = dp(18);
        agreeBox = new CheckBox(this);
        agreeRow.addView(agreeBox, new LinearLayout.LayoutParams(-2, -2));
        TextView agreeText = new TextView(this);
        agreeText.setText("已阅读并同意《用户协议》和《隐私政策》以及《运营商服务协议》");
        agreeText.setTextColor(0xFF6E6E76);
        agreeText.setTextSize(12.5f);
        agreeText.setOnClickListener(v -> agreeBox.setChecked(!agreeBox.isChecked()));
        LinearLayout.LayoutParams agreeTextLp = new LinearLayout.LayoutParams(0, -2, 1f);
        agreeTextLp.leftMargin = dp(4);
        agreeRow.addView(agreeText, agreeTextLp);
        body.addView(agreeRow, agreeLp);

        // 登录钮
        loginBtn = new TextView(this);
        loginBtn.setText("登录");
        loginBtn.setTextSize(16);
        loginBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        loginBtn.setGravity(Gravity.CENTER);
        loginBtn.setOnClickListener(v -> onLogin());
        LinearLayout.LayoutParams loginLp = new LinearLayout.LayoutParams(-1, dp(50));
        loginLp.topMargin = dp(26);
        body.addView(loginBtn, loginLp);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshLoginBtn();
            }
        };
        phoneInput.addTextChangedListener(watcher);
        codeInput.addTextChangedListener(watcher);
        refreshLoginBtn();

        if (savedInstanceState != null) {
            // 进程重建存档口（E-03）：手机号/验证码/协议勾选/发码倒
            // 计时此前全无存档，重建即清——验证码短信已到手却要重填
            // 重发。倒计时按截止时刻折算剩余秒续跑。
            String savedPhone = savedInstanceState.getString("hg_phone");
            if (savedPhone != null) phoneInput.setText(savedPhone);
            String savedCode = savedInstanceState.getString("hg_code");
            if (savedCode != null) codeInput.setText(savedCode);
            agreeBox.setChecked(savedInstanceState.getBoolean("hg_agree", false));
            long leftMs = savedInstanceState.getLong("hg_countdown_end", 0)
                    - System.currentTimeMillis();
            if (leftMs > 0) {
                countdownEndAtMs = System.currentTimeMillis() + leftMs;
                remainSeconds = (int) ((leftMs + 999) / 1000);
                sendCodeBtn.setEnabled(false);
                sendCodeBtn.setText(remainSeconds + "秒后重发");
                mainHandler.postDelayed(countdownTick, 1000);
            }
            refreshLoginBtn();
        }

        setContentView(root);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("hg_phone", phoneText());
        outState.putString("hg_code", codeText());
        outState.putBoolean("hg_agree", agreeBox != null && agreeBox.isChecked());
        outState.putLong("hg_countdown_end", countdownEndAtMs);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacks(countdownTick);
        super.onDestroy();
    }

    private String phoneText() {
        return phoneInput.getText() == null ? "" : phoneInput.getText().toString().trim();
    }

    private String codeText() {
        return codeInput.getText() == null ? "" : codeInput.getText().toString().trim();
    }

    /** 发码校验：11 位且 1 开头。 */
    private boolean phoneValidForCode() {
        String phone = phoneText();
        return phone.length() == 11 && phone.startsWith("1");
    }

    /** 登录钮可点口径：与发码同一校验（D-10，此前只判长度 11、非 1 开头号码也能点登录发请求）＋验证码至少 4 位。 */
    private void refreshLoginBtn() {
        boolean ready = !loggingIn && phoneValidForCode() && codeText().length() >= 4;
        loginBtn.setEnabled(ready);
        loginBtn.setBackground(roundBg(ready ? 0xFFF0761A : 0xFFE3E3E6, dp(10)));
        loginBtn.setTextColor(ready ? Color.WHITE : 0xFF9B9BA1);
    }

    private void onSendCode() {
        if (sending || remainSeconds > 0) return;
        // 协议把关守发码（D-11）：此前只守登录，未同意协议即可发短信验证码
        if (!agreeBox.isChecked()) {
            toast("请先阅读并同意协议");
            return;
        }
        if (!phoneValidForCode()) {
            toast("手机号好像不对，请检查一下再填");
            return;
        }
        sending = true;
        sendCodeBtn.setEnabled(false);
        sendCodeBtn.setText("发送中…");
        passportClient.sendSmsCode(phoneText(), new HgCallback<Integer>() {
            @Override
            public void onSuccess(Integer seconds) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    sending = false;
                    toast("验证码已发送，请查看手机短信");
                    startCountdown(seconds == null ? 60 : seconds);
                });
            }

            @Override
            public void onError(int code, String msg) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    sending = false;
                    restoreSendCodeBtn();
                    toast(msg);
                });
            }
        });
    }

    private void startCountdown(int seconds) {
        mainHandler.removeCallbacks(countdownTick);
        remainSeconds = seconds > 0 ? seconds : 60;
        countdownEndAtMs = System.currentTimeMillis() + remainSeconds * 1000L;
        sendCodeBtn.setEnabled(false);
        sendCodeBtn.setText(remainSeconds + "秒后重发");
        mainHandler.postDelayed(countdownTick, 1000);
    }

    private void restoreSendCodeBtn() {
        mainHandler.removeCallbacks(countdownTick);
        remainSeconds = 0;
        countdownEndAtMs = 0;
        sendCodeBtn.setText("获取验证码");
        sendCodeBtn.setEnabled(!sending);
    }

    private void onLogin() {
        if (loggingIn) return;
        if (!agreeBox.isChecked()) {
            toast("请先阅读并同意协议");
            return;
        }
        if (!phoneValidForCode()) {
            toast("手机号好像不对，请检查一下再填");
            return;
        }
        loggingIn = true;
        refreshLoginBtn();
        passportClient.smsLogin(phoneText(), codeText(), new HgCallback<HgAccountStore.Session>() {
            @Override
            public void onSuccess(HgAccountStore.Session session) {
                // 漏派 A-17：落盘失败不许报假成功——会话没存住重启
                // 即掉登录态；给真失败、留在本页可重试，也不拿没
                // 落盘的会话去同步。save 内部已读回自验。
                if (!HgAccountStore.save(session)) {
                    runOnUiThread(() -> {
                        if (destroyed) return;
                        loggingIn = false;
                        refreshLoginBtn();
                        toast("登录状态没能保存下来，请再试一次");
                    });
                    return;
                }
                // 登录成功即拉云端片单合并本地镜像（收藏/历史/点赞/预约，
                // HgAccountSync 内部后台跑、失败静默，不挡退页）
                com.fongmi.android.tv.hongguo.api.HgAccountSync.syncAfterLogin();
                runOnUiThread(() -> {
                    if (destroyed) return;
                    loggingIn = false;
                    toast("登录成功");
                    finish();
                });
            }

            @Override
            public void onError(int code, String msg) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    loggingIn = false;
                    refreshLoginBtn();
                    toast(msg);
                });
            }
        });
    }

    private void toast(String msg) {
        if (msg == null || msg.isEmpty()) return;
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private GradientDrawable roundBg(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }
}
