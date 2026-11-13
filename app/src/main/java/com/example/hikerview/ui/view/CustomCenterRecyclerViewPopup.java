package com.example.hikerview.ui.view;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hikerview.R;
import com.example.hikerview.utils.StringUtil;
import com.lxj.xpopup.core.CenterPopupView;
import com.lxj.xpopup.util.XPopupUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 照官 8.83 本体移植（jadx 官包源，资源号已按官 public.xml 还原名）：
 * 居中列表选择弹窗。with(数据, 列数, ClickListener)、withTitle、
 * withSelectedIndex、useCenter；点按/长按都先关窗再回调（dismissWith）。
 */
public class CustomCenterRecyclerViewPopup extends CenterPopupView {

    private boolean center;
    private ClickListener clickListener;
    private List<String> data;
    private int selectedIndex;
    private int span;
    private String title;

    public interface ClickListener {
        void click(String str, int i);

        void onLongClick(String str, int i);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.pop_custom_center_recycler_view;
    }

    public CustomCenterRecyclerViewPopup with(String[] strArr, int i, ClickListener clickListener) {
        this.data = new ArrayList<>(Arrays.asList(strArr));
        this.clickListener = clickListener;
        this.span = i;
        return this;
    }

    public CustomCenterRecyclerViewPopup with(List<String> list, int i, ClickListener clickListener) {
        this.data = list;
        this.clickListener = clickListener;
        this.span = i;
        return this;
    }

    public CustomCenterRecyclerViewPopup useCenter(boolean z) {
        this.center = z;
        return this;
    }

    public CustomCenterRecyclerViewPopup withTitle(String str) {
        this.title = str;
        return this;
    }

    public CustomCenterRecyclerViewPopup withSelectedIndex(int i) {
        this.selectedIndex = i;
        return this;
    }

    public CustomCenterRecyclerViewPopup(Context context) {
        super(context);
        this.span = 2;
        this.center = true;
        this.selectedIndex = -1;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        if (StringUtil.isNotEmpty(this.title)) {
            ((TextView) findViewById(R.id.title)).setText(this.title);
        }
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), this.span));
        CustomRecyclerViewAdapter customRecyclerViewAdapter = new CustomRecyclerViewAdapter(getContext(), this.data, new CustomRecyclerViewAdapter.OnItemClickListener() {
            @Override
            public void onClick(View view, final int i) {
                dismissWith(() -> clickListener.click(data.get(i), i));
            }

            @Override
            public void onLongClick(View view, final int i) {
                dismissWith(() -> clickListener.onLongClick(data.get(i), i));
            }
        }, this.center);
        customRecyclerViewAdapter.setSelectIndex(this.selectedIndex);
        recyclerView.setAdapter(customRecyclerViewAdapter);
    }

    @Override
    protected int getMaxHeight() {
        return (int) (XPopupUtils.getScreenHeight(getContext()) * 0.85f);
    }
}
