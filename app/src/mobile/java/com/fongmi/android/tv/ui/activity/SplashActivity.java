package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        TextView tvXing = findViewById(R.id.tv_xing);
        TextView tvChen = findViewById(R.id.tv_chen);
        tvXing.animate().alpha(1f).setDuration(600).withEndAction(() -> {
            tvChen.animate().alpha(1f).setDuration(600).withEndAction(() -> {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    startActivity(new Intent(SplashActivity.this, HomeActivity.class));
                    finish();
                }, 400);
            }).start();
        }).start();
    }
}