package com.fongmi.android.tv.ui.hk.view;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.hk.HkRemotePlaySource;

import java.util.List;

/**
 * 照抄 8.83 ParseStatusListPopup（smali_classes2/ui/home/view/ParseStatusListPopup.smali）：
 * 底部弹出，内容布局 view_setting_menu_popup（本树 hk_view_parse_status_popup），
 * 队列数据 List&lt;RemotePlaySource&gt;，带 title 的构造显示标题块。
 * 承载壳原版是 XPopup 的 BottomPopupView；星辰未引入 XPopup，按本工程既有先例
 * （HkCenterListPopup）以原生 Dialog 承接，内容与交互不变。
 */
public class HkParseStatusPopup {

    /** 1.4.16 修复（扫查中7）：全树共享的当前状态弹窗引用，保证同时最多一个，旧弹窗不失联冻结。 */
    private static Dialog sShowingDialog;

    private final Context context;
    private Activity activity;
    private List<HkRemotePlaySource> sources;
    private String title;
    private HkParseStatusAdapter adapter;
    private Dialog dialog;

    public HkParseStatusPopup(Context context) {
        this.context = context;
    }

    public HkParseStatusPopup(Activity activity) {
        this.context = activity;
        this.activity = activity;
    }

    public HkParseStatusPopup(Activity activity, List<HkRemotePlaySource> sources) {
        this.context = activity;
        this.activity = activity;
        this.sources = sources;
    }

    public HkParseStatusPopup(Activity activity, List<HkRemotePlaySource> sources, String title) {
        this.context = activity;
        this.activity = activity;
        this.sources = sources;
        this.title = title;
    }

    public HkParseStatusAdapter getAdapter() {
        return adapter;
    }

    public void show() {
        if (com.fongmi.android.tv.ui.hk.HkDialogs.isHostGone(context)) return;
        // 1.4.16 修复（扫查中7）：已有状态弹窗在显示时先关掉旧的再开新的——连点队列钮不叠弹窗，
        // 且新弹窗用本次传入的最新快照数据，不再是旧弹窗打开瞬间的冻结数据
        if (sShowingDialog != null && sShowingDialog.isShowing()) {
            try {
                sShowingDialog.dismiss();
            } catch (Exception ignored) {
            }
        }
        sShowingDialog = null;
        View root = LayoutInflater.from(context).inflate(R.layout.hk_view_parse_status_popup, null);
        if (title != null && !title.isEmpty()) {
            View titleBlock = root.findViewById(R.id.hk_parse_status_title_block);
            if (titleBlock != null) titleBlock.setVisibility(View.VISIBLE);
            TextView titleView = root.findViewById(R.id.hk_parse_status_title);
            if (titleView != null) titleView.setText(title);
        }
        RecyclerView recyclerView = root.findViewById(R.id.hk_parse_status_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new HkParseStatusAdapter(context, sources);
        recyclerView.setAdapter(adapter);

        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setWindowAnimations(R.style.HkDialogFade);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        final Dialog shown = dialog;
        shown.setOnDismissListener(d -> {
            if (sShowingDialog == shown) sShowingDialog = null;
        });
        shown.show();
        sShowingDialog = shown;
    }
}
