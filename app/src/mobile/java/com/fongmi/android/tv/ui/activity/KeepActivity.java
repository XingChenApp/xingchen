package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.ActivityKeepBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.adapter.KeepAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.SyncDialog;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class KeepActivity extends BaseActivity implements KeepAdapter.OnClickListener {

    private ActivityKeepBinding mBinding;
    private KeepAdapter mAdapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, KeepActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityKeepBinding.inflate(getLayoutInflater());
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setSupportActionBar(mBinding.toolbar);
        setRecyclerView();
        getKeep();
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn(this)));
        mBinding.recycler.setAdapter(mAdapter = new KeepAdapter(this));
        mAdapter.setSize(Product.getSpec(this));
    }

    private void getKeep() {
        java.util.List<Keep> items = Keep.getVod();
        // 集/进度副行（K4-2）：Keep 行本身无集/进度字段，按 (cid, key) 回读同剧 History 行补齐展示数据，
        // 与点进详情落点回读同一行同源；没看过的收藏查不到行、副行自然不露
        for (Keep item : items) {
            History history = History.find(item.getCid(), item.getKey());
            if (history == null) continue;
            item.setVodRemarks(history.getVodRemarks());
            item.setPosition(history.getPosition());
            item.setDuration(history.getDuration());
        }
        mAdapter.setItems(items, () -> mBinding.progressLayout.showContent(true, mAdapter.getItemCount()));
    }

    private boolean resumedOnce;

    @Override
    protected void onResume() {
        super.onResume();
        // 兜底（深挖 L-14）：刷新只有 RefreshEvent 单通路，退回本页时补拉一次；
        // 首启 initView 已拉过，跳过第一次。
        if (resumedOnce) getKeep();
        resumedOnce = true;
    }

    private void onSync() {
        SyncDialog.create().keep().show(this);
    }

    private void onDelete() {
        if (mAdapter.isDelete()) {
            pinCenteredDialog(new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_WebHTV_LightDialog).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_keep).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> mAdapter.clear()).show());
        } else if (mAdapter.getItemCount() > 0) {
            mAdapter.setDelete(true);
        }
    }

    /** 居中框写死（K6-1）：清空确认框原裸 MaterialAlertDialogBuilder、无尺寸钉，照全 App 居中框口径在代码写死——宽 320dp（下载页居中框同定值）、高自适应、居中。 */
    private void pinCenteredDialog(androidx.appcompat.app.AlertDialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = com.fongmi.android.tv.utils.ResUtil.dp2px(320);
        lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = android.view.Gravity.CENTER;
        dialog.getWindow().setAttributes(lp);
    }

    private void loadConfig(Config config, Keep item) {
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                VideoActivity.start(getActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType().equals(RefreshEvent.Type.KEEP)) getKeep();
    }

    @Override
    public void onItemClick(Keep item) {
        Config config = Config.find(item.getCid());
        if (config == null) {
            // 降级先明示（J5）：原配置已删、落点从直达详情降为按剧名重搜，不许静默降级
            Notify.show(R.string.keep_config_deleted);
            SearchActivity.start(this, item.getVodName());
        }
        else if (item.getCid() != VodConfig.getCid()) loadConfig(config, item);
        else VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(Keep item) {
        mAdapter.remove(item.delete(), () -> {
            if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);
        });
    }

    @Override
    public boolean onLongClick() {
        mAdapter.setDelete(!mAdapter.isDelete());
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_keep, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackInvoked();
        else if (item.getItemId() == R.id.delete) onDelete();
        else if (item.getItemId() == R.id.sync) onSync();
        else if (item.getItemId() == R.id.history) HistoryActivity.start(this);
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onBackInvoked() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackInvoked();
    }
}
