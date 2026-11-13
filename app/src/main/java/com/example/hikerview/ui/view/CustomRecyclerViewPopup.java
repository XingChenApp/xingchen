package com.example.hikerview.ui.view;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hikerview.R;
import com.example.hikerview.utils.StringUtil;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.util.XPopupUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 照官 8.83 本体移植（jadx 官包源）：通用文字列底部弹窗
 * （pop_xiu_tan_result 布局，与 CustomBottomRecyclerViewPopup 的
 * 居中版布局不同，官就是两套）。dismissAfterClick 默认 true：
 * 点击先关窗再回调；菜单钮（R.id.code）同理。
 * getPopupHeight 照官直接用 height 字段（未设时为 0）。
 */
public class CustomRecyclerViewPopup extends BottomPopupView {

    private CustomRecyclerViewAdapter adapter;
    private ClickListener clickListener;
    private List<String> data;
    private boolean dismissAfterClick;
    private float height;
    private View.OnClickListener menuClick;
    private int menuDrawable;
    private int span;
    private String title;

    public interface ClickListener {
        void click(String str, int i);

        void onLongClick(String str, int i);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.pop_xiu_tan_result;
    }

    public CustomRecyclerViewPopup withMenu(int i, View.OnClickListener onClickListener) {
        this.menuClick = onClickListener;
        this.menuDrawable = i;
        return this;
    }

    public CustomRecyclerViewPopup with(String[] strArr, int i, ClickListener clickListener) {
        this.data = new ArrayList<>(Arrays.asList(strArr));
        this.clickListener = clickListener;
        this.span = i;
        return this;
    }

    public CustomRecyclerViewPopup with(List<String> list, int i, ClickListener clickListener) {
        this.data = list;
        this.clickListener = clickListener;
        this.span = i;
        return this;
    }

    public CustomRecyclerViewPopup withTitle(String str) {
        this.title = str;
        return this;
    }

    public CustomRecyclerViewPopup height(float f) {
        this.height = f;
        return this;
    }

    public CustomRecyclerViewPopup dismissAfterClick(boolean z) {
        this.dismissAfterClick = z;
        return this;
    }

    public void updateData(List<String> list) {
        this.data.clear();
        this.data.addAll(list);
        this.adapter.notifyDataSetChanged();
    }

    public CustomRecyclerViewPopup(Context context) {
        super(context);
        this.span = 2;
        this.dismissAfterClick = true;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        if (StringUtil.isNotEmpty(this.title)) {
            ((TextView) findViewById(R.id.title)).setText(this.title);
        }
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), this.span));
        CustomRecyclerViewAdapter customRecyclerViewAdapter = new CustomRecyclerViewAdapter(getContext(), this.data, new CustomRecyclerViewAdapter.OnItemClickListener() {
            @Override
            public void onClick(View view, final int i) {
                if (CustomRecyclerViewPopup.this.dismissAfterClick) {
                    CustomRecyclerViewPopup.this.dismissWith(() ->
                            CustomRecyclerViewPopup.this.clickListener.click(CustomRecyclerViewPopup.this.data.get(i), i));
                } else {
                    CustomRecyclerViewPopup.this.clickListener.click(CustomRecyclerViewPopup.this.data.get(i), i);
                }
            }

            @Override
            public void onLongClick(View view, final int i) {
                if (CustomRecyclerViewPopup.this.dismissAfterClick) {
                    CustomRecyclerViewPopup.this.dismissWith(() ->
                            CustomRecyclerViewPopup.this.clickListener.onLongClick(CustomRecyclerViewPopup.this.data.get(i), i));
                } else {
                    CustomRecyclerViewPopup.this.clickListener.onLongClick(CustomRecyclerViewPopup.this.data.get(i), i);
                }
            }
        });
        this.adapter = customRecyclerViewAdapter;
        recyclerView.setAdapter(customRecyclerViewAdapter);
        if (this.menuClick != null) {
            ImageView imageView = (ImageView) findViewById(R.id.code);
            imageView.setVisibility(0);
            imageView.setImageDrawable(ContextCompat.getDrawable(getContext(), this.menuDrawable));
            imageView.setOnClickListener(view -> {
                if (CustomRecyclerViewPopup.this.dismissAfterClick) {
                    CustomRecyclerViewPopup.this.dismissWith(() -> CustomRecyclerViewPopup.this.menuClick.onClick(view));
                } else {
                    CustomRecyclerViewPopup.this.menuClick.onClick(view);
                }
            });
        }
    }

    @Override
    protected int getMaxHeight() {
        float screenHeight = XPopupUtils.getScreenHeight(getContext());
        float f = this.height;
        if (f <= 0.0f) {
            f = 0.85f;
        }
        return (int) (screenHeight * f);
    }

    @Override
    protected int getPopupHeight() {
        return (int) (XPopupUtils.getScreenHeight(getContext()) * this.height);
    }
}
