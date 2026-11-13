package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.databinding.DialogDanmakuBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.ui.adapter.DanmakuAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Util;

public final class DanmakuDialog extends BaseBottomSheetDialog implements DanmakuAdapter.OnClickListener {

    private final DanmakuAdapter adapter;
    private DialogDanmakuBinding binding;
    private PlayerManager player;

    public interface Host {

        boolean isDanmakuFullscreen();

        /** 环42 danmu# 调起面：宿主有 hk 弹幕搜索承接时回 true（入口钮才显示）。 */
        default boolean hasHkDanmuSearch() {
            return false;
        }

        /** 环42 danmu# 调起面：点入口钮时由宿主打开（默认无操作）。 */
        default void openHkDanmuSearch(FragmentActivity activity, PlayerManager player) {
        }
    }

    public static DanmakuDialog create() {
        return new DanmakuDialog();
    }

    public DanmakuDialog() {
        this.adapter = new DanmakuAdapter(this);
    }

    public DanmakuDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof DanmakuDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDanmakuBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter.addAll(player.getDanmakus()));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelected()));
        binding.recycler.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        addHkDanmuEntry();
    }

    /**
     * 环42 danmu# 调起面入口钮：宿主（Host.hasHkDanmuSearch）支持时才在
     * 头行「搜索」与「选择文件」之间插入一枚图标钮。编程式添加而不是改
     * dialog_danmaku.xml：该布局只有 mobile 源集有，改之会牵动
     * ViewBinding 生成面；钮的式样（图标/描边背景/16dp 间距）与头行
     * 既有三钮同款。
     */
    private void addHkDanmuEntry() {
        FragmentActivity activity = getActivity();
        if (!(activity instanceof Host) || !((Host) activity).hasHkDanmuSearch()) return;
        if (!(binding.getRoot() instanceof ViewGroup)) return;
        ViewGroup header = null;
        ViewGroup root = (ViewGroup) binding.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof ViewGroup) {
                header = (ViewGroup) root.getChildAt(i);
                break;
            }
        }
        if (header == null) return;
        androidx.appcompat.widget.AppCompatImageView entry = new androidx.appcompat.widget.AppCompatImageView(activity);
        entry.setImageResource(com.fongmi.android.tv.R.drawable.ic_action_hk_danmu);
        // 背景与头行既有三钮同法：布局里是 ?attr/selectableItemBackgroundBorderless，
        // 编程式建面须先经主题 resolveAttribute 取真实 resourceId 再
        // setBackgroundResource（直传 attr id 不是 drawable，真机抛 NotFoundException）。
        android.util.TypedValue bgValue = new android.util.TypedValue();
        if (activity.getTheme().resolveAttribute(androidx.appcompat.R.attr.selectableItemBackgroundBorderless, bgValue, true)) {
            entry.setBackgroundResource(bgValue.resourceId);
        }
        androidx.core.widget.ImageViewCompat.setImageTintList(entry,
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#B3000000")));
        int margin = (int) (16 * activity.getResources().getDisplayMetrics().density + 0.5f);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(margin);
        entry.setLayoutParams(lp);
        entry.setOnClickListener(this::onHkDanmuSearch);
        // 头行子序：标题(0)／搜索(1)／本钮(2)／选择文件／设置
        header.addView(entry, Math.min(2, header.getChildCount()));
    }

    private void onHkDanmuSearch(View view) {
        FragmentActivity activity = getActivity();
        if (activity == null || !(activity instanceof Host)) return;
        dismissAllowingStateLoss();
        ((Host) activity).openHkDanmuSearch(activity, player);
    }

    @Override
    protected void initEvent() {
        binding.search.setOnClickListener(this::onSearch);
        binding.choose.setOnClickListener(this::onChoose);
        binding.setting.setOnClickListener(this::onSetting);
    }

    private void onSearch(View view) {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        dismissAllowingStateLoss();
        if (DanmakuSetting.hasValidApiUrl()) {
            if (shouldUseInputDialog(activity)) DanmakuSearchInputDialog.create().player(player).restoreParent(true).show(activity);
            else DanmakuSearchDialog.create().player(player).restoreParent(true).show(activity);
        } else {
            DanDanPlaySearchDialog.create().player(player).restoreParent(true).show(activity);
        }
    }

    private boolean shouldUseInputDialog(FragmentActivity activity) {
        return Util.isMobile() && (!(activity instanceof Host) || !((Host) activity).isDanmakuFullscreen());
    }

    private void onChoose(View view) {
        FileChooser.from(launcher).show(new String[]{"text/*"});
        player.pause();
    }

    private void onSetting(View view) {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        dismissAllowingStateLoss();
        DanmakuSettingDialog.create().player(player).restoreParent(true).show(activity);
    }

    @Override
    public void onItemClick(Danmaku item) {
        player.setDanmaku(item.isSelected() ? Danmaku.empty() : item);
        dismiss();
    }

    @Override
    protected boolean transparent() {
        return true;
    }

    @Override
    protected boolean stableOverlay() {
        return true;
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        player.setDanmaku(Danmaku.from(FileChooser.getPathFromUri(result.getData().getData())));
        dismiss();
    });
}
