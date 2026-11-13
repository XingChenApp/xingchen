package com.fongmi.android.tv.ui.hk.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.Spanned;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.fongmi.android.tv.R;

/**
 * 照抄 8.83 com.example.hikerview.ui.view.DrawableInlineTextView（smali 逐方法转写）。
 * 与 DrawableTextView 共用同一 styleable：0=center(整数) 1=drawableSrc 3=text；
 * center==1 用 view_img_text_view_inline，否则 view_img_text_view_inline2。
 */
public class DrawableInlineTextView extends FrameLayout {

    public static final String TAG = "DrawableTextView";

    private ImageView imageView;
    private TextView textView;

    public DrawableInlineTextView(Context context) {
        super(context);
        init(context, null);
    }

    public DrawableInlineTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        init(context, attrs);
    }

    public DrawableInlineTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DrawableTextView);
        Drawable drawable = a.getDrawable(1);
        String text = a.getString(3);
        int center = a.getInteger(0, 0);
        a.recycle();

        int layout = center == 1 ? R.layout.view_img_text_view_inline : R.layout.view_img_text_view_inline2;
        View root = LayoutInflater.from(getContext()).inflate(layout, this, true);
        imageView = root.findViewById(R.id.item_reult_img);
        textView = root.findViewById(R.id.textView);

        if (text != null) textView.setText(text);
        if (drawable != null) imageView.setImageDrawable(drawable);
    }

    public ImageView getImageView() {
        return imageView;
    }

    public void setText(Spanned text) {
        textView.setText(text);
    }

    public void setText(String text) {
        textView.setText(text);
    }
}
