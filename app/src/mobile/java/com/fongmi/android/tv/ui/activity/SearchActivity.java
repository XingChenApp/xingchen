package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.CollectFragment;
import com.fongmi.android.tv.ui.fragment.SearchFragment;

public class SearchActivity extends BaseActivity {

    public static void start(Activity activity) {
        start(activity, "");
    }

    public static void start(Activity activity, String keyword) {
        start(activity, keyword, null);
    }

    public static void start(Activity activity, String keyword, String siteKey) {
        start(activity, keyword, siteKey, null, null);
    }

    public static void start(Activity activity, String keyword, String siteKey, String pic, String wallPic) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        intent.putExtra("siteKey", siteKey);
        intent.putExtra("pic", pic);
        intent.putExtra("wallPic", wallPic);
        activity.startActivity(intent);
    }

    public static void direct(Activity activity, String keyword) {
        direct(activity, keyword, null);
    }

    public static void direct(Activity activity, String keyword, String siteKey) {
        direct(activity, keyword, siteKey, null, null);
    }

    public static void direct(Activity activity, String keyword, String siteKey, String pic, String wallPic) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        intent.putExtra("siteKey", siteKey);
        intent.putExtra("pic", pic);
        intent.putExtra("wallPic", wallPic);
        intent.putExtra("direct", true);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    private boolean isDirect() {
        return getIntent().getBooleanExtra("direct", false);
    }

    private String getSiteKey() {
        return getIntent().getStringExtra("siteKey");
    }

    private String getPic() {
        return getIntent().getStringExtra("pic");
    }

    private String getWallPic() {
        return getIntent().getStringExtra("wallPic");
    }

    @Override
    protected ViewBinding getBinding() {
        return ActivitySearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            // 直达只认有词的：CollectFragment 进页即无条件开搜，空词直达会变成「没输入就自己搜」
            // （与 1.4.22 全局搜索空词自搜同型）。空词/纯空白一律回输入页，等用户输词再搜。
            if (isDirect() && !TextUtils.isEmpty(getKeyword()) && !getKeyword().trim().isEmpty()) getSupportFragmentManager().beginTransaction().replace(R.id.container, CollectFragment.newInstance(getKeyword(), getSiteKey(), getPic(), getWallPic()), CollectFragment.class.getSimpleName()).commit();
            else getSupportFragmentManager().beginTransaction().replace(R.id.container, SearchFragment.newInstance(getKeyword(), getSiteKey(), getPic(), getWallPic()), SearchFragment.class.getSimpleName()).commit();
        }
    }

    @Override
    protected void onBackInvoked() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            super.onBackInvoked();
        }
    }
}
