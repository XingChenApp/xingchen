package com.example.hikerview.ui.view;

import android.app.Activity;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.example.hikerview.R;
import com.example.hikerview.ui.view.util.TextViewUtils;
import com.example.hikerview.utils.StringUtil;
import com.lxj.xpopup.core.BottomPopupView;

/**
 * 照官 8.83 本体移植（jadx 官包源）：底部消息弹窗（查看详情/忽略）。
 * 触摸穿透官形保留：点外部按 popupInfo 开关关窗，手势一律透传给宿主
 * Activity 的 dispatchTouchEvent。
 */
public class CustomBottomPopup extends BottomPopupView {

    private View.OnClickListener onClickListener;
    private String title;

    @Override
    protected int getImplLayoutId() {
        return R.layout.view_msg_bottom_pop;
    }

    public CustomBottomPopup addOnClickListener(View.OnClickListener onClickListener) {
        this.onClickListener = onClickListener;
        return this;
    }

    public CustomBottomPopup(Context context) {
        this(context, null);
    }

    public CustomBottomPopup(Context context, String str) {
        super(context);
        this.title = str;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        findViewById(R.id.btn_show).setOnClickListener(view -> {
            this.onClickListener.onClick(view);
            dismiss();
        });
        if (StringUtil.isNotEmpty(this.title)) {
            TextViewUtils.setSpanText((TextView) findViewById(R.id.msg_text), this.title);
        }
        findViewById(R.id.dismiss).setOnClickListener(view -> dismiss());
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (this.popupInfo.isDismissOnTouchOutside.booleanValue()) {
            dismiss();
        }
        if (this.dialog == null) {
            return true;
        }
        passClick(motionEvent);
        return true;
    }

    private void passClick(MotionEvent motionEvent) {
        if (this.dialog == null || !(this.dialog.getContext() instanceof Activity)) {
            return;
        }
        ((Activity) this.dialog.getContext()).dispatchTouchEvent(motionEvent);
    }
}
