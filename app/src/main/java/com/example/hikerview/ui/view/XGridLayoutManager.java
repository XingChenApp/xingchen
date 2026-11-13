package com.example.hikerview.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 照官 8.83 本体移植（jadx 官包源）：关预测动画，onLayoutChildren 吞异常防崩。
 * 官用 Timber 记日志，星辰无 Timber，同位换 android.util.Log。
 */
public class XGridLayoutManager extends GridLayoutManager {

    private static final String TAG = "XGridLayoutManager";

    @Override
    public boolean supportsPredictiveItemAnimations() {
        return false;
    }

    public XGridLayoutManager(Context context, int spanCount) {
        super(context, spanCount);
    }

    public XGridLayoutManager(Context context, int spanCount, int orientation, boolean reverseLayout) {
        super(context, spanCount, orientation, reverseLayout);
    }

    public XGridLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        try {
            super.onLayoutChildren(recycler, state);
        } catch (Exception e) {
            Log.e(TAG, "onLayoutChildren", e);
        }
    }
}
