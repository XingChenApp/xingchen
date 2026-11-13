package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;

public class CustomRecyclerView extends RecyclerView {

    private int minWidth;
    private int minHeight;
    private int maxWidth;
    private int maxHeight;

    public CustomRecyclerView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public CustomRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CustomRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        if (attrs != null) setAttrs(context, attrs);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    private void setAttrs(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CustomRecyclerView);
        minWidth = a.getDimensionPixelSize(R.styleable.CustomRecyclerView_android_minWidth, 0);
        minHeight = a.getDimensionPixelSize(R.styleable.CustomRecyclerView_android_minHeight, 0);
        maxWidth = a.getDimensionPixelSize(R.styleable.CustomRecyclerView_maxWidth, 0);
        maxHeight = a.getDimensionPixelSize(R.styleable.CustomRecyclerView_maxHeight, 0);
        a.recycle();
    }

    public void setMinWidth(int minWidth) {
        this.minWidth = minWidth;
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }

    public void setMinHeight(int minHeight) {
        this.minHeight = minHeight;
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    private int getConstrainedSize(int measuredSize, int minSize, int maxSize) {
        int finalSize = measuredSize;
        if (maxSize > 0) finalSize = Math.min(finalSize, maxSize);
        if (minSize > 0) finalSize = Math.max(finalSize, minSize);
        return finalSize;
    }

    private int getConstrainedMeasureSpec(int measureSpec, int maxSize) {
        if (maxSize <= 0) return measureSpec;
        int mode = View.MeasureSpec.getMode(measureSpec);
        int size = View.MeasureSpec.getSize(measureSpec);
        if (mode == View.MeasureSpec.UNSPECIFIED) return View.MeasureSpec.makeMeasureSpec(maxSize, View.MeasureSpec.AT_MOST);
        if (mode == View.MeasureSpec.AT_MOST && size > maxSize) return View.MeasureSpec.makeMeasureSpec(maxSize, View.MeasureSpec.AT_MOST);
        return measureSpec;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        widthMeasureSpec = getConstrainedMeasureSpec(widthMeasureSpec, maxWidth);
        heightMeasureSpec = getConstrainedMeasureSpec(heightMeasureSpec, maxHeight);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int finalWidth = getConstrainedSize(getMeasuredWidth(), minWidth, maxWidth);
        int finalHeight = getConstrainedSize(getMeasuredHeight(), minHeight, maxHeight);
        setMeasuredDimension(finalWidth, finalHeight);
    }

    private void focus(int position) {
        ViewHolder holder = findViewHolderForLayoutPosition(position);
        if (holder != null) holder.itemView.requestFocus();
    }

    @Override
    public void scrollToPosition(int position) {
        super.scrollToPosition(position);
        postDelayed(() -> focus(position), 50);
    }

    // 下拉刷新失效的根子在这里，此前的实现已删除：
    // 旧实现在 ACTION_DOWN 就 requestDisallowInterceptTouchEvent(true) 锁死所有祖先的拦截，
    // 再在 MOVE 里按方向“放行”——10月11日加的竖直放行分支在真机上仍救不活：
    // 放行时手势流已过半，SwipeRefreshLayout 接不上，下拉连转圈都不出现（用户两次实证）。
    // 删除整段自创握手后，ViewPager（横滑翻页）、SwipeRefreshLayout（顶部下拉刷新）、
    // RecyclerView（竖滑列表）走安卓标准仲裁，不再依赖放行时序。
}
