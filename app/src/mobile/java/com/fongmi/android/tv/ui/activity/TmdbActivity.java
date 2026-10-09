package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.ActivityTmdbBinding;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;

public class TmdbActivity extends BaseActivity {

    private static final String PREFS = "xingchen";
    private static final String KEY_API_KEY = "xingchen.tmdb_api_key";

    private ActivityTmdbBinding binding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, TmdbActivity.class));
    }

    public static String getApiKey(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API_KEY, "");
    }

    public static boolean isConfigured(Context context) {
        String key = getApiKey(context);
        return key != null && !key.isEmpty();
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityTmdbBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        refreshSub();
        binding.cardTmdbKey.setOnClickListener(v -> showApiKeyInput());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSub();
    }

    private void refreshSub() {
        String key = getApiKey(this);
        if (key == null || key.isEmpty()) {
            binding.tvTmdbKeySub.setText("未配置");
        } else {
            String masked = key.length() > 8 ? key.substring(0, 4) + "****" + key.substring(key.length() - 4) : "****";
            binding.tvTmdbKeySub.setText("已配置 (" + masked + ")");
        }
    }

    private void showApiKeyInput() {
        View view = getLayoutInflater().inflate(R.layout.dialog_tmdb_api_key, null);
        EditText input = view.findViewById(R.id.et_api_key);
        input.setText(getApiKey(this));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();
        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_API_KEY, input.getText().toString().trim()).apply();
            refreshSub();
            Notify.show("已保存");
            dialog.dismiss();
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
}
