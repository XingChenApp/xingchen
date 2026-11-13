package com.fongmi.android.tv.ui.hk.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;

/**
 * 照抄 8.83 com.example.hikerview.ui.view.DrawableTextView（smali 逐方法转写，含原版
 * 二参构造器重复 init、iconTextView 复用 imageView LayoutParams 等原始行为）。
 * styleable 下标：0=center 1=drawableSrc 2=drawableWidth 3=text 4=textSize 5=textTop。
 */
public class DrawableTextView extends FrameLayout {

    public static final String TAG = "DrawableTextView";

    private TextView iconTextView;
    private ImageView imageView;
    private TextView textView;

    public DrawableTextView(Context context) {
        super(context);
        init(context, null);
    }

    public DrawableTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        init(context, attrs);
    }

    public DrawableTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DrawableTextView);
        Drawable drawable = a.getDrawable(1);
        int size = a.getDimensionPixelSize(2, 0);
        int topMargin = a.getDimensionPixelSize(5, ResUtil.dp2px(10));
        int textSize = a.getDimensionPixelSize(4, 0);
        String text = a.getString(3);
        a.recycle();

        View root = LayoutInflater.from(getContext()).inflate(R.layout.view_img_text_view, this, true);
        imageView = root.findViewById(R.id.item_reult_img);
        textView = root.findViewById(R.id.textView);
        iconTextView = root.findViewById(R.id.shortcut_icon_text);

        RelativeLayout.LayoutParams lp = asRelativeParams(textView);
        lp.topMargin = topMargin;
        textView.setLayoutParams(lp);

        RelativeLayout.LayoutParams ilp = asRelativeParams(imageView);
        ilp.width = size;
        ilp.height = size;
        imageView.setLayoutParams(ilp);

        // 原版把 imageView 的 LayoutParams 对象直接共用给 iconTextView（两边互相牵连），改用它自己的那份
        RelativeLayout.LayoutParams tlp = asRelativeParams(iconTextView);
        tlp.width = size;
        tlp.height = size;
        iconTextView.setLayoutParams(tlp);

        if (text != null) textView.setText(text);
        if (drawable != null) imageView.setImageDrawable(drawable);
        if (textSize > 0) textView.setTextSize(0, textSize);
    }

    /** 取视图现有的 RelativeLayout.LayoutParams；类型不符时按现有宽高/边距新造一份，不许强转崩。 */
    private static RelativeLayout.LayoutParams asRelativeParams(View view) {
        android.view.ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof RelativeLayout.LayoutParams) return (RelativeLayout.LayoutParams) lp;
        RelativeLayout.LayoutParams out = new RelativeLayout.LayoutParams(
                lp == null ? android.view.ViewGroup.LayoutParams.WRAP_CONTENT : lp.width,
                lp == null ? android.view.ViewGroup.LayoutParams.WRAP_CONTENT : lp.height);
        if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams m = (android.view.ViewGroup.MarginLayoutParams) lp;
            out.setMargins(m.leftMargin, m.topMargin, m.rightMargin, m.bottomMargin);
        }
        return out;
    }

    public TextView getIconTextView() {
        return iconTextView;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public TextView getTextView() {
        return textView;
    }

    public void setText(String text) {
        textView.setText(text);
    }
}
