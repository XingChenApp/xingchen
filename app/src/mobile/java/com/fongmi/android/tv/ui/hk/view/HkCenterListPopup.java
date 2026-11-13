package com.fongmi.android.tv.ui.hk.view;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 照抄 8.83 CustomCenterRecyclerViewPopup（smali_classes2/ui/view/CustomCenterRecyclerViewPopup.smali）的
 * 布局与行为：布局 pop_custom_center_recycler_view（bg_round_all 自带 16dp 圆角白底）、
 * 标题 title、GridLayoutManager(span)、最大高度为屏幕 85%、条目点击/长按先关弹窗再回调。
 * 承载壳原版是 XPopup 的 CenterPopupView（经 XPopup.Builder.borderRadius(16dp) 弹出）；
 * 星辰未引入 XPopup，按本工程既有先例（更新日志弹窗）以原生 Dialog 承载，内容与交互不变。
 */
public class HkCenterListPopup {

    public interface ClickListener {
        void click(String text, int position);

        void onLongClick(String text, int position);
    }

    private final Context context;
    private List<String> data;
    private ClickListener clickListener;
    private int span = 2;
    private boolean center = true;
    private int selectedIndex = -1;
    private String title;
    private Dialog dialog;

    public HkCenterListPopup(Context context) {
        this.context = context;
    }

    public HkCenterListPopup with(List<String> data, int span, ClickListener listener) {
        this.data = data;
        this.clickListener = listener;
        this.span = span;
        return this;
    }

    public HkCenterListPopup with(String[] data, int span, ClickListener listener) {
        return with(new ArrayList<>(Arrays.asList(data)), span, listener);
    }

    public HkCenterListPopup withTitle(String title) {
        this.title = title;
        return this;
    }

    /** 对应原版构造参数 selectedIndex（选中项在适配器里以红字标出）。 */
    public HkCenterListPopup withSelectIndex(int selectIndex) {
        this.selectedIndex = selectIndex;
        return this;
    }

    public HkCenterListPopup useCenter(boolean center) {
        this.center = center;
        return this;
    }

    public void show() {
        if (com.fongmi.android.tv.ui.hk.HkDialogs.isHostGone(context)) return;
        View root = LayoutInflater.from(context).inflate(R.layout.pop_custom_center_recycler_view, null);
        if (title != null && !title.isEmpty()) {
            TextView titleView = root.findViewById(R.id.title);
            titleView.setText(title);
        }
        RecyclerView recyclerView = root.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(context, span));
        HkCenterListAdapter adapter = new HkCenterListAdapter(context, data, new HkCenterListAdapter.OnItemClickListener() {
            @Override
            public void onClick(View view, int position) {
                dismissWith(() -> {
                    if (clickListener != null) clickListener.click(data.get(position), position);
                });
            }

            @Override
            public void onLongClick(View view, int position) {
                dismissWith(() -> {
                    if (clickListener != null) clickListener.onLongClick(data.get(position), position);
                });
            }
        }, center);
        adapter.setSelectIndex(selectedIndex);
        recyclerView.setAdapter(adapter);

        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            // 渐入渐出试水批：弹窗淡入淡出（与 HkDialogs 同一套）
            window.setWindowAnimations(com.fongmi.android.tv.R.style.HkDialogFade);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
            int maxHeight = (int) (screenHeight * 0.85f);
            window.setLayout((int) (screenWidth * 0.9f), WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.setOnShowListener(d -> {
                if (root.getHeight() > maxHeight) {
                    window.setLayout((int) (screenWidth * 0.9f), maxHeight);
                }
            });
        }
        dialog.show();
    }

    /** 对应原版 dismissWith：先关弹窗，再执行回调。 */
    private void dismissWith(Runnable runnable) {
        if (dialog != null) dialog.dismiss();
        runnable.run();
    }
}
