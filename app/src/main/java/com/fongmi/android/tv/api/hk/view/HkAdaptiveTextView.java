package com.fongmi.android.tv.api.hk.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.Log;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * 移植自 8.83 com.example.hikerview.ui.view.AdaptiveTextView（Kotlin）。
 * 自适应 TextView：内容放不下时自动调整行间距/行数以适应固定高度。
 *
 * operateType:
 *  1 = 仅调整行高（默认）
 *  2 = 调整行间距
 *  3 = changeLinesAndHeight：先压行间距，再减行数
 */
public class HkAdaptiveTextView extends AppCompatTextView {

    private static final String TAG = "HkAdaptiveTextView";

    public static final int OPERATE_ADJUST_HEIGHT = 1;
    public static final int OPERATE_ADJUST_LINE_SPACE = 2;
    public static final int OPERATE_CHANGE_LINES_AND_HEIGHT = 3;

    private int mOperateType = OPERATE_ADJUST_HEIGHT;
    private int mLineSpaceLowerLimitPx = -1;
    private int mMaxLineNumLowerLimit = 1;

    private boolean mAdjusting = false;

    public HkAdaptiveTextView(Context context) {
        this(context, null);
    }

    public HkAdaptiveTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HkAdaptiveTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (attrs != null) {
            // 8.83 R.styleable.AdaptiveTextView: [operateType, lineSpaceLowerLimit, maxLineNumLowerLimit]
            // 用反射兜底：没有 styleable 时按属性名解析
            try {
                int[] styleable = resolveStyleable(context);
                if (styleable != null) {
                    TypedArray a = context.obtainStyledAttributes(attrs, styleable);
                    try {
                        mOperateType = a.getInt(0, mOperateType);
                        mLineSpaceLowerLimitPx = (int) a.getDimension(1, mLineSpaceLowerLimitPx);
                        mMaxLineNumLowerLimit = a.getInt(2, mMaxLineNumLowerLimit);
                    } finally {
                        a.recycle();
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "initAttr failed", t);
            }
        }
    }

    private static int[] resolveStyleable(Context context) {
        try {
            Class<?> r = Class.forName(context.getPackageName() + ".R$styleable");
            java.lang.reflect.Field f = r.getField("HkAdaptiveTextView");
            return (int[]) f.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // 8.83 在 onWindowFocusChanged / setText 后触发 doAdjustTextView；此处在 layout 后触发等价
        if (getVisibility() == VISIBLE) {
            post(this::doAdjustTextView);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && getVisibility() == VISIBLE) {
            post(this::doAdjustTextView);
        }
    }

    private void doAdjustTextView() {
        if (mAdjusting) return;
        if (getVisibility() != VISIBLE) return;
        if (!isNeedAdjust()) return;

        mAdjusting = true;
        try {
            if (mOperateType == OPERATE_CHANGE_LINES_AND_HEIGHT) {
                // 8.83 type 3：先压行间距
                if (adjustLineSpace()) {
                    post(() -> {
                        mAdjusting = false;
                        doAdjustTextView();
                    });
                    return;
                }
                // 行间距已到下限，减行数
                int maxLines = getMaxLines();
                if (maxLines > mMaxLineNumLowerLimit && maxLines > 1) {
                    setMaxLines(maxLines - 1);
                    post(() -> {
                        mAdjusting = false;
                        doAdjustTextView();
                    });
                    return;
                }
            } else if (mOperateType == OPERATE_ADJUST_LINE_SPACE) {
                if (adjustLineSpace()) {
                    post(() -> {
                        mAdjusting = false;
                        doAdjustTextView();
                    });
                    return;
                }
            }
            // type 1 或无法继续调整：保持现状
        } finally {
            mAdjusting = false;
        }
    }

    /** 8.83 TextViewUtil.isNeedAdjust 等价：行数超出 maxLines 即需调整 */
    private boolean isNeedAdjust() {
        int maxLines = getMaxLines();
        if (maxLines <= 0 || maxLines == Integer.MAX_VALUE) return false;
        Layout layout = getLayout();
        if (layout == null) return false;
        // 8.83 还检查 layoutParams.height > 0；此处用 measuredHeight 兜底
        if (getMeasuredHeight() <= 0) return false;
        int lineCount = layout.getLineCount();
        if (lineCount <= maxLines) return false;
        // 内容高度超出可视高度才需调整
        int contentHeight = layout.getHeight() + getPaddingTop() + getPaddingBottom();
        return contentHeight > getMeasuredHeight();
    }

    /**
     * 8.83 TextViewUtil.adjustTextViewLineSpace 等价：
     * 把行间距（lineSpacingExtra）往下压，直到 lowerLimit。
     * @return true=本次压了行间距，需重查；false=已到下限
     */
    private boolean adjustLineSpace() {
        if (mLineSpaceLowerLimitPx < 0) return false;
        float curExtra = getLineSpacingExtra();
        // 8.83 按比例缩小；此处每次减 1px，直至下限
        float next = curExtra - 1f;
        // 下限语义：lineSpacingExtra 不低于 (lowerLimit - fontHeight)？8.83 直接比较 PX 值
        // 简化：extra 降到 0 即停；若 lowerLimit >= 0 则以 lowerLimit 为目标下限
        float floor = Math.max(0f, mLineSpaceLowerLimitPx - getLineHeight() + getLineSpacingExtra());
        if (next < 0) next = 0;
        if (curExtra <= 0 && mLineSpaceLowerLimitPx <= 0) return false;
        if (next >= curExtra) return false;
        setLineSpacing(next, getLineSpacingMultiplier());
        return true;
    }

    // ---- 供代码设置的 setter（8.83 无公开 setter，此处为兼容保留） ----
    public void setOperateType(int type) {
        mOperateType = type;
    }

    public void setLineSpaceLowerLimitPx(int px) {
        mLineSpaceLowerLimitPx = px;
    }

    public void setMaxLineNumLowerLimit(int n) {
        mMaxLineNumLowerLimit = n;
    }
}
