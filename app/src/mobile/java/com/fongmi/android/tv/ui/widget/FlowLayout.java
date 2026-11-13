package com.fongmi.android.tv.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** 搜索落地页最近搜索胶囊流式布局：子项按行排布，超宽换行（GZ RecentSearchFlow 同款形态） */
public class FlowLayout extends ViewGroup {
    private int hGap;
    private int vGap;

    public FlowLayout(Context context) {
        super(context);
        init(context);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        float d = context.getResources().getDisplayMetrics().density;
        hGap = (int) (8 * d + 0.5f);
        vGap = (int) (8 * d + 0.5f);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int y = 0;
        int lineHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            measureChild(c, widthMeasureSpec, heightMeasureSpec);
            int cw = c.getMeasuredWidth();
            int ch = c.getMeasuredHeight();
            if (x > 0 && x + cw > width) {
                x = 0;
                y += lineHeight + vGap;
                lineHeight = 0;
            }
            x += cw + hGap;
            lineHeight = Math.max(lineHeight, ch);
        }
        y += lineHeight;
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                y + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            int cw = c.getMeasuredWidth();
            int ch = c.getMeasuredHeight();
            if (x > getPaddingLeft() && x + cw > getPaddingLeft() + width) {
                x = getPaddingLeft();
                y += lineHeight + vGap;
                lineHeight = 0;
            }
            c.layout(x, y, x + cw, y + ch);
            x += cw + hGap;
            lineHeight = Math.max(lineHeight, ch);
        }
    }
}
