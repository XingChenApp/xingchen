package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
 * 强制执行 android:maxHeight 的 ScrollView。
 * 原生 ScrollView 不处理 maxHeight（属性写在布局里也不生效），
 * 音乐工具面板的 680dp 上限因此形同虚设、面板顶满屏截断最后一行；
 * 本类在 onMeasure 里把高度钳到 maxHeight，超限部分在限高内正常滚动。
 */
public class MaxHeightScrollView extends ScrollView {

    private int maxHeightPx = Integer.MAX_VALUE;

    public MaxHeightScrollView(Context context) {
        super(context);
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            try {
                android.content.res.TypedArray a = context.obtainStyledAttributes(
                        attrs, new int[]{android.R.attr.maxHeight});
                maxHeightPx = a.getDimensionPixelSize(0, Integer.MAX_VALUE);
                a.recycle();
            } catch (Throwable ignored) {
            }
        }
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (attrs != null) {
            try {
                android.content.res.TypedArray a = context.obtainStyledAttributes(
                        attrs, new int[]{android.R.attr.maxHeight});
                maxHeightPx = a.getDimensionPixelSize(0, Integer.MAX_VALUE);
                a.recycle();
            } catch (Throwable ignored) {
            }
        }
    }

    public void setMaxHeightPx(int maxHeightPx) {
        this.maxHeightPx = maxHeightPx;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeightPx != Integer.MAX_VALUE) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int size = MeasureSpec.getSize(heightMeasureSpec);
            if (mode == MeasureSpec.UNSPECIFIED || size > maxHeightPx) {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
