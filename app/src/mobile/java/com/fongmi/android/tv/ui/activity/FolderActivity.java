package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.databinding.ActivityFolderBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.FolderFragment;

public class FolderActivity extends BaseActivity {

    private ActivityFolderBinding mBinding;

    public static void start(Activity activity, String key, Result result) {
        if (result == null || result.getTypes().isEmpty()) return;
        Intent intent = new Intent(activity, FolderActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("result", result);
        activity.startActivity(intent);
    }

    private String getKey() {
        return getIntent().getStringExtra("key");
    }

    private Result getResult() {
        return getIntent().getParcelableExtra("result");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityFolderBinding.inflate(getLayoutInflater());
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setSupportActionBar(mBinding.toolbar);
        Result result = getResult();
        if (result == null || result.getTypes().isEmpty()) {
            finish();
            return;
        }
        Class type = result.getTypes().get(0);
        setTitle(type.getTypeName());
        getSupportFragmentManager().beginTransaction().replace(R.id.container, FolderFragment.newInstance(getKey(), type, 8), "0").commit();
    }

    private FolderFragment getFragment() {
        return (FolderFragment) getSupportFragmentManager().findFragmentByTag("0");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_folder, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // 页内筛选入口（Tp-23）：本分类无筛选定义时不露钮，与首页筛选 FAB 的显隐口径一致；
        // 显隐只读 intent 里的入口分类，不依赖碎片挂载时机
        MenuItem filter = menu.findItem(R.id.filter);
        if (filter != null) {
            Result result = getResult();
            Class type = result == null || result.getTypes().isEmpty() ? null : result.getTypes().get(0);
            filter.setVisible(type != null && type.getFilters() != null && !type.getFilters().isEmpty());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackInvoked();
        else if (item.getItemId() == R.id.filter) {
            FolderFragment fragment = getFragment();
            if (fragment != null) fragment.showFilter();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onBackInvoked() {
        FolderFragment fragment = getFragment();
        if (fragment != null && fragment.canBack()) fragment.goBack();
        else super.onBackInvoked();
    }
}
