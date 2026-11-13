package com.fongmi.android.tv.ui.hk.view;

import android.text.Layout;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * 照抄 8.83 com.example.hikerview.ui.view.TextViewUtil（Kotlin object，smali 逐方法转写）。
 * 原版状态全部存放在静态字段里，这里原样保留。
 */
public final class TextViewUtil {

    public static final TextViewUtil INSTANCE = new TextViewUtil();

    private static int mContentTotalHeight_PX;
    private static Layout mLayout;
    private static ViewGroup.LayoutParams mLayoutParams;
    private static int mMaxAdjustSpace_PX;
    private static int mMaxLines;
    private static int mPaddingBottom_PX;
    private static int mPaddingTop_PX;
    private static int mViewHeight_PX;

    private TextViewUtil() {
    }

    private int getMinDescent(TextView textView, Layout layout) {
        if (layout == null || textView == null) return 0;
        int min = layout.getLineDescent(0);
        int count = textView.getLineCount();
        for (int i = 0; i < count; i++) {
            min = Math.min(min, layout.getLineDescent(i));
        }
        return min;
    }

    private void logline(TextView textView) {
        for (int i = 0; i < textView.getMaxLines(); i++) {
            Log.d("MainActivity", "layout.getLineDescent(" + i + ")" + mLayout.getLineDescent(i)
                    + "---- mLayout!!.getLineAscent(0)" + mLayout.getLineAscent(i));
        }
        for (int i = 0; i < textView.getMaxLines(); i++) {
            Log.d("MainActivity", "mLayout!!.getLineBaseline(" + i + ")" + mLayout.getLineBaseline(i));
        }
        for (int i = 0; i < textView.getMaxLines(); i++) {
            Log.d("MainActivity", "mLayout!!.getLineTop(" + i + ")" + mLayout.getLineTop(i)
                    + "---- mLayout!!.getLineBottom(" + i + ")" + mLayout.getLineBottom(i));
        }
    }

    private int simulateContentHeight(Layout layout, TextView textView) {
        return layout.getLineTop(textView.getLineCount())
                + (textView.getMaxLines() - textView.getLineCount()) * textView.getLineHeight();
    }

    public boolean adjustTextViewLineSpace(TextView textView, Integer lowerLimit) {
        if (!isNeedAdjust(textView)) return false;
        mMaxAdjustSpace_PX = getMinDescent(textView, mLayout);
        if (mMaxAdjustSpace_PX <= 0) return false;
        int content = mContentTotalHeight_PX - mMaxAdjustSpace_PX * (textView.getMaxLines() - 1);
        int avail = mViewHeight_PX - mPaddingBottom_PX - mPaddingTop_PX;
        if (content > avail) return false;
        int space = (mViewHeight_PX - mPaddingBottom_PX - mPaddingTop_PX - content) / (textView.getMaxLines() - 1);
        if (space <= 0) return false;
        float reduce;
        if (lowerLimit != null && lowerLimit >= 0 && space >= lowerLimit) {
            reduce = (float) (lowerLimit - mMaxAdjustSpace_PX);
        } else {
            reduce = (float) (space - mMaxAdjustSpace_PX);
        }
        Log.d("MainActivity", "reduceSpace:" + reduce);
        textView.setLineSpacing(reduce, 1.0f);
        return true;
    }

    public boolean isNeedAdjust(TextView textView) {
        if (textView == null) return false;
        if (textView.getMaxLines() <= 0 || textView.getMaxLines() == Integer.MAX_VALUE) return false;
        mLayoutParams = textView.getLayoutParams();
        mViewHeight_PX = mLayoutParams.height;
        if (mViewHeight_PX <= 0) return false;
        mPaddingTop_PX = textView.getPaddingTop();
        mPaddingBottom_PX = textView.getPaddingBottom();
        mLayout = textView.getLayout();
        if (mLayout == null) return false;
        mMaxLines = textView.getMaxLines();
        if (textView.getLineCount() < textView.getMaxLines()) {
            mContentTotalHeight_PX = simulateContentHeight(mLayout, textView);
        } else {
            mContentTotalHeight_PX = mLayout.getLineTop(textView.getMaxLines());
            if (mContentTotalHeight_PX <= 0) return false;
        }
        return mViewHeight_PX - mPaddingTop_PX - mPaddingBottom_PX < mContentTotalHeight_PX;
    }
}
