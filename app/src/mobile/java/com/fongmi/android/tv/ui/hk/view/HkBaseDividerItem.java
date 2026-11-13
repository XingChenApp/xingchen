package com.fongmi.android.tv.ui.hk.view;

import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 照抄 8.83 BaseDividerItem（ui/view）：GridLayoutManager 下按 viewType 的 leftRight 做等分间距。
 * noBorder 分支按 poly 位置分摊；竖向整行 span 时左右各给满 leftRight、否则按 spanIndex 比例分；
 * 横向时只有第一组给 left，right 恒给满。
 */
public abstract class HkBaseDividerItem extends RecyclerView.ItemDecoration {

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        if (!(parent.getLayoutManager() instanceof GridLayoutManager)) return;
        if (!(view.getLayoutParams() instanceof GridLayoutManager.LayoutParams)) return;
        GridLayoutManager layoutManager = (GridLayoutManager) parent.getLayoutManager();
        GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams) view.getLayoutParams();
        int position = parent.getChildAdapterPosition(view);
        int spanCount = layoutManager.getSpanCount();
        int viewType = layoutManager.getItemViewType(view);
        boolean noBorder = noBorder(viewType);
        int leftRight = getLeftRight(viewType);
        if (noBorder) {
            int polyPos = getPolyPos(viewType, position) % lp.getSpanSize();
            outRect.left = polyPos * leftRight / lp.getSpanSize();
            outRect.right = leftRight - (polyPos + 1) * leftRight / lp.getSpanSize();
            return;
        }
        if (layoutManager.getOrientation() == RecyclerView.VERTICAL) {
            if (lp.getSpanSize() == spanCount) {
                outRect.left = leftRight;
                outRect.right = leftRight;
            } else {
                outRect.left = (int) ((float) (spanCount - lp.getSpanIndex()) / (float) spanCount
                        * (float) leftRight);
                outRect.right = (int) ((float) (spanCount + 1) * (float) leftRight / (float) spanCount
                        - (float) outRect.left);
            }
        } else {
            int groupIndex = layoutManager.getSpanSizeLookup().getSpanGroupIndex(position, spanCount);
            if (groupIndex == 0) outRect.left = leftRight;
            outRect.right = leftRight;
        }
    }

    public abstract int getLeftRight(int viewType);

    protected int getPolyPos(int viewType, int position) {
        return position;
    }

    protected boolean noBorder(int viewType) {
        return false;
    }
}
