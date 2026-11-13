package com.fongmi.android.tv.ui.hk.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Layout;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

import com.fongmi.android.tv.R;

/**
 * 照抄 8.83 com.example.hikerview.ui.view.AdaptiveTextView（Kotlin，smali 逐方法转写）。
 * styleable 下标：0=lineNumLowerLimit 1=lineSpaceLowerLimit 2=operateType
 * （1=changeLines 2=changeLineHeight 3=changeLinesAndHeight）。
 */
public class AdaptiveTextView extends AppCompatTextView {

    private int mContentTotalHeight;
    private Layout mLayout;
    private int mLineSpaceLowerLimit_PX = -1;
    private int mMaxLineNumLowerLimit = 1;
    private int mMaxLines;
    private int mOperateType = 1;
    private int mPaddingBottom_PX;
    private int mPaddingTop_PX;
    private int mTextFontHeight_PX;
    private int mViewHeight_PX;

    public AdaptiveTextView(Context context) {
        this(context, null);
    }

    public AdaptiveTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AdaptiveTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AdaptiveTextView);
        mOperateType = a.getInt(2, 1);
        mLineSpaceLowerLimit_PX = (int) a.getDimension(1, -1.0f);
        mMaxLineNumLowerLimit = a.getInt(0, 1);
        a.recycle();
    }

    private void doAdjustTextView() {
        if (getVisibility() != VISIBLE) return;
        if (!isDiff(this)) return;
        initAttr(this);
        if (mOperateType == 3) {
            if (!TextViewUtil.INSTANCE.isNeedAdjust(this)) return;
            if (TextViewUtil.INSTANCE.adjustTextViewLineSpace(this, mLineSpaceLowerLimit_PX)) {
                post(() -> initAttr(this));
                return;
            }
            if (getMaxLines() <= mMaxLineNumLowerLimit) return;
            setMaxLines(getMaxLines() - 1);
            if (TextViewUtil.INSTANCE.isNeedAdjust(this)) {
                doAdjustTextView();
            } else {
                post(() -> initAttr(this));
            }
        } else if (mOperateType == 2) {
            TextViewUtil.INSTANCE.adjustTextViewLineSpace(this, mLineSpaceLowerLimit_PX);
        } else if (mOperateType == 1) {
            if (getMaxLines() <= mMaxLineNumLowerLimit) return;
            if (!TextViewUtil.INSTANCE.isNeedAdjust(this)) return;
            setMaxLines(getMaxLines() - 1);
            if (getMaxLines() <= mMaxLineNumLowerLimit) return;
            doAdjustTextView();
        }
    }

    private void initAttr(AdaptiveTextView v) {
        mViewHeight_PX = v.getMeasuredHeight();
        mLayout = v.getLayout();
        mMaxLines = v.getMaxLines();
        mPaddingTop_PX = v.getPaddingTop();
        mPaddingBottom_PX = v.getPaddingBottom();
        mTextFontHeight_PX = (int) v.getTextSize();
        try {
            Layout layout = v.getLayout();
            mContentTotalHeight = layout != null ? layout.getLineTop(v.getMaxLines()) : 0;
        } catch (Exception ignored) {
        }
    }

    private boolean isDiff(AdaptiveTextView v) {
        if (mViewHeight_PX != 0 && mLayout != null && mMaxLines != 0
                && mTextFontHeight_PX != 0 && mContentTotalHeight != 0) {
            try {
                Layout layout = v.getLayout();
                if (mViewHeight_PX == v.getMeasuredHeight()
                        && (mLayout == layout || (mLayout != null && mLayout.equals(layout)))
                        && mMaxLines == v.getMaxLines()
                        && mPaddingTop_PX == v.getPaddingTop()
                        && mPaddingBottom_PX == v.getPaddingBottom()
                        && layout != null
                        && mContentTotalHeight == layout.getLineTop(v.getMaxLines())) {
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) doAdjustTextView();
    }
}
