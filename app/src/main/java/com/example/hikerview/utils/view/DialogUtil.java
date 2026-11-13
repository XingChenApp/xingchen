package com.example.hikerview.utils.view;

import android.content.Context;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

import com.example.hikerview.R;
import com.example.hikerview.utils.DisplayUtil;
import com.example.hikerview.utils.ScreenUtil;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.interfaces.OnSelectListener;

/**
 * 照官 8.83 本体移植（jadx 官包源，Kotlin object 落 Java 同形态）：
 * 对话框卡片化展示（shape_dialog_cardbg 底、宽 = w/5 × 屏短边，
 * 默认 w=4）与 XPopup 居中列表（宽 3/5 屏短边、圆角 16dp、
 * popup_center_list_item 条目布局）。
 */
public final class DialogUtil {

    public static final DialogUtil INSTANCE = new DialogUtil();

    private DialogUtil() {
    }

    private void useCardBg(AlertDialog alertDialog) {
        Window window = alertDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.shape_dialog_cardbg);
        }
    }

    public void showAsCard(Context context, AlertDialog alertDialog) {
        showAsCard(context, alertDialog, 4);
    }

    public void showAsCard(Context context, AlertDialog alertDialog, int w) {
        useCardBg(alertDialog);
        alertDialog.show();
        if (alertDialog.getWindow() != null) {
            Window window = alertDialog.getWindow();
            WindowManager.LayoutParams attributes = window.getAttributes();
            int screenMin = ScreenUtil.getScreenMin(context);
            if (screenMin > 0) {
                attributes.width = (w * screenMin) / 5;
                Window window2 = alertDialog.getWindow();
                window2.setAttributes(attributes);
            }
        }
    }

    private int getPopupWidth(Context context, int w) {
        return (w * ScreenUtil.getScreenMin(context)) / 5;
    }

    public BasePopupView showCenterList(Context context, String[] list, OnSelectListener listener) {
        return showCenterList(context, null, list, listener);
    }

    public BasePopupView showCenterList(Context context, String title, String[] list, OnSelectListener listener) {
        BasePopupView show = new XPopup.Builder(context).borderRadius(DisplayUtil.dpToPx(context, 16)).popupWidth(getPopupWidth(context, 3)).asCenterList(title, list, null, -1, listener, 0, R.layout.popup_center_list_item).show();
        return show;
    }
}
