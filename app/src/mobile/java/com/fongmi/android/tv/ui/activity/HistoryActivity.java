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
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.ActivityHistoryBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.adapter.HistoryAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.SyncDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HistoryActivity extends BaseActivity implements HistoryAdapter.OnClickListener {

    private ActivityHistoryBinding mBinding;
    private HistoryAdapter mAdapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, HistoryActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHistoryBinding.inflate(getLayoutInflater());
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
        getHistory();
    }

    private void setRecyclerView() {
        // 列数与收藏页同口径（K4-1）：恒走 Product.getColumn（随显示尺寸偏好），不再竖屏写死 3 列——
        // 旧形默认偏好下恰同为 3 看不出差，改过偏好后两页格宽分叉
        int column = Product.getColumn(this);
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, column));
        mBinding.recycler.setAdapter(mAdapter = new HistoryAdapter(this));
        mAdapter.setSize(Product.getSpec(this, column));
    }

    private void getHistory() {
        // 与收藏页同路：只刷新内容、不动滚动位——回页时用户原本看到哪儿还停在哪儿，
        // 有新条目也只是列表内容更新，不许把滚动拽回顶。
        mAdapter.setItems(History.getForHome(), (hasChange) -> mBinding.progressLayout.showContent(true, mAdapter.getItemCount()));
    }

    private boolean resumedOnce;

    @Override
    protected void onResume() {
        super.onResume();
        // 兜底（深挖 L-14）：刷新只有 RefreshEvent 单通路，退回本页时补拉一次，
        // 避免事件丢失后列表陈旧；首启 initView 已拉过，跳过第一次。
        if (resumedOnce) getHistory();
        resumedOnce = true;
    }

    private void onSync() {
        SyncDialog.create().history().show(this);
    }

    private void onDelete() {
        if (mAdapter.isDelete()) {
            pinCenteredDialog(new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_WebHTV_LightDialog).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_history).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> mAdapter.clear()).show());
        } else if (mAdapter.getItemCount() > 0) {
            mAdapter.setDelete(true);
        }
    }

    /** 居中框写死（L6-1）：清空确认框原裸 MaterialAlertDialogBuilder、无尺寸钉，照全 App 居中框口径在代码写死——宽 320dp（下载页居中框同定值）、高自适应、居中。 */
    private void pinCenteredDialog(androidx.appcompat.app.AlertDialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = com.fongmi.android.tv.utils.ResUtil.dp2px(320);
        lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = android.view.Gravity.CENTER;
        dialog.getWindow().setAttributes(lp);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType().equals(RefreshEvent.Type.HISTORY)) getHistory();
    }

    @Override
    public void onItemClick(History item) {
        // 海阔条目分流回海阔再入实现：通用 start 拿 hk_ 键进 detailContent 无海阔
        // 分支必死（2026-11-14 再入普查钉死），与 HkHistoryActivity 同一路。
        if (History.isHk(item)) {
            HkHistoryActivity.openRecord(this, item);
            return;
        }
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic(), null, item.getWallPic());
    }

    @Override
    public void onItemDelete(History item) {
        mAdapter.remove(item.deleteAndSync(), () -> {
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
        getMenuInflater().inflate(R.menu.menu_history, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackInvoked();
        else if (item.getItemId() == R.id.delete) onDelete();
        else if (item.getItemId() == R.id.sync) onSync();
        else if (item.getItemId() == R.id.keep) KeepActivity.start(this);
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onBackInvoked() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackInvoked();
    }
}
