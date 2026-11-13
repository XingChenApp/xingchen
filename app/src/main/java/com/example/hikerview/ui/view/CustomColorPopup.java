package com.example.hikerview.ui.view;

import android.content.Context;
import android.view.View;

import com.example.hikerview.R;
import com.lxj.xpopup.core.CenterPopupView;

/**
 * 照官 8.83 本体移植（jadx 官包源）：八色选择弹窗。色块 tag 由调用方
 * 在 onCreated 后 setTag(颜色串) 注入（聚阅 hikerPop 即如此），点击把
 * tag 原样回给 OnColorSelect 并关窗。
 */
public class CustomColorPopup extends CenterPopupView {

    private OnColorSelect colorSelect;

    public interface OnColorSelect {
        void select(String str);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.view_color_pop;
    }

    public CustomColorPopup(Context context) {
        super(context);
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        View.OnClickListener listener = view -> select(view);
        findViewById(R.id.color1_bg).setOnClickListener(listener);
        findViewById(R.id.color2_bg).setOnClickListener(listener);
        findViewById(R.id.color3_bg).setOnClickListener(listener);
        findViewById(R.id.color4_bg).setOnClickListener(listener);
        findViewById(R.id.color5_bg).setOnClickListener(listener);
        findViewById(R.id.color6_bg).setOnClickListener(listener);
        findViewById(R.id.color7_bg).setOnClickListener(listener);
        findViewById(R.id.color8_bg).setOnClickListener(listener);
    }

    public void select(View view) {
        this.colorSelect.select((String) view.getTag());
        dismiss();
    }

    public OnColorSelect getColorSelect() {
        return this.colorSelect;
    }

    public void setColorSelect(OnColorSelect onColorSelect) {
        this.colorSelect = onColorSelect;
    }
}
