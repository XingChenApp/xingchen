package com.fongmi.android.tv.ui.hk.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.RelativeLayout;

import androidx.viewpager.widget.ViewPager;

/**
 * 照抄 8.83 ScrollRelativeLayout（smali_classes2/ui/view/scroll/ScrollRelativeLayout.smali）：
 * ACTION_MOVE 时对直接父级 requestDisallowInterceptTouchEvent(true)；再向上找 ViewPager 祖先，
 * 原版要求该祖先是 EnhanceViewPager 且其父非空时才对祖父级也 disallow——星辰无 EnhanceViewPager，
 * 该分支在原版类缺席时本就不触发，故此处保留找 ViewPager 祖先的遍历但不做第二段 disallow。
 */
public class ScrollRelativeLayout extends RelativeLayout {

    public ScrollRelativeLayout(Context context) {
        super(context);
    }

    public ScrollRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ScrollRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ScrollRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private ViewParent getPagerParent() {
        ViewParent parent = getParent();
        while (parent != null) {
            if (parent instanceof ViewPager) {
                return parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            ViewParent pager = getPagerParent();
            if (pager != null) {
                // 原版：pager 为 EnhanceViewPager 时再对其父 disallow；星辰无此类，等价略过
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}
