package com.example.hikerview.ui.view;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.function.Consumer;
import com.example.hikerview.R;
import com.example.hikerview.utils.StringUtil;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.util.XPopupUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 照官 8.83 本体移植（jadx 官包源，资源号已按官 public.xml 还原名）：
 * 底部列表弹窗，与居中共用 pop_custom_center_recycler_view(_dark) 布局。
 * 点击关窗模式 dismissWhenClick（默认开）；长按关窗模式三态
 * dismissWhenLongClickMode（1=先关后回调、-1=只回调、0=随 dismissWhenClick）；
 * 高度比 height 默认 0.69；支持菜单钮、onCreate 回调、换列数与删行。
 */
public class CustomBottomRecyclerViewPopup extends BottomPopupView {

    private CustomRecyclerViewAdapter adapter;
    private CustomCenterRecyclerViewPopup.ClickListener clickListener;
    private boolean dark;
    private List<String> data;
    private boolean dismissWhenClick;
    private int dismissWhenLongClickMode;
    private GridLayoutManager gridLayoutManager;
    private float height;
    private View.OnClickListener menuClick;
    private Consumer<RecyclerView> onCreateCallback;
    private int selectedIndex;
    private int span;
    private String title;

    public CustomBottomRecyclerViewPopup with(String[] strArr, int i, CustomCenterRecyclerViewPopup.ClickListener clickListener) {
        this.data = new ArrayList<>(Arrays.asList(strArr));
        this.clickListener = clickListener;
        this.span = i;
        return this;
    }

    public CustomBottomRecyclerViewPopup with(List<String> list, int i, CustomCenterRecyclerViewPopup.ClickListener clickListener) {
        this.data = list;
        this.clickListener = clickListener;
        this.span = i;
        return this;
    }

    public CustomBottomRecyclerViewPopup withTitle(String str) {
        this.title = str;
        return this;
    }

    public CustomBottomRecyclerViewPopup withMenu(View.OnClickListener onClickListener) {
        this.menuClick = onClickListener;
        return this;
    }

    public CustomBottomRecyclerViewPopup withSelectedIndex(int i) {
        this.selectedIndex = i;
        return this;
    }

    public CustomBottomRecyclerViewPopup(Context context) {
        super(context);
        this.span = 2;
        this.dismissWhenClick = true;
        this.dismissWhenLongClickMode = 0;
        this.height = 0.69f;
        this.selectedIndex = -1;
    }

    @Override
    protected int getImplLayoutId() {
        return this.dark ? R.layout.pop_custom_center_recycler_view_dark : R.layout.pop_custom_center_recycler_view;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        Window window = this.dialog.getWindow();
        if (window != null && Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
            window.getDecorView().setSystemUiVisibility(1280);
        }
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        if (StringUtil.isNotEmpty(this.title)) {
            ((TextView) findViewById(R.id.title)).setText(this.title);
        }
        if (this.menuClick != null) {
            View menuIcon = findViewById(R.id.menu_icon);
            menuIcon.setVisibility(View.VISIBLE);
            menuIcon.setOnClickListener(this.menuClick);
        }
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), this.span);
        this.gridLayoutManager = gridLayoutManager;
        recyclerView.setLayoutManager(gridLayoutManager);
        CustomRecyclerViewAdapter customRecyclerViewAdapter = new CustomRecyclerViewAdapter(getContext(), this.data, new CustomRecyclerViewAdapter.OnItemClickListener() {
            @Override
            public void onClick(View view, final int i) {
                if (dismissWhenClick) {
                    dismissWith(() -> clickListener.click(data.get(i), i));
                } else {
                    clickListener.click(data.get(i), i);
                }
            }

            @Override
            public void onLongClick(View view, final int i) {
                if (dismissWhenLongClickMode == 1) {
                    dismissWith(() -> clickListener.onLongClick(data.get(i), i));
                    return;
                }
                if (dismissWhenLongClickMode == -1) {
                    clickListener.onLongClick(data.get(i), i);
                } else if (dismissWhenClick) {
                    dismissWith(() -> clickListener.onLongClick(data.get(i), i));
                } else {
                    clickListener.onLongClick(data.get(i), i);
                }
            }
        });
        this.adapter = customRecyclerViewAdapter;
        customRecyclerViewAdapter.setDark(this.dark);
        this.adapter.setSelectIndex(this.selectedIndex);
        recyclerView.setAdapter(this.adapter);
        Consumer<RecyclerView> consumer = this.onCreateCallback;
        if (consumer != null) {
            consumer.accept(recyclerView);
        }
    }

    public void changeSpanCount(int i) {
        this.span = i;
        this.gridLayoutManager.setSpanCount(i);
    }

    public void removePosition(int i) {
        this.data.remove(i);
        this.adapter.notifyItemRemoved(i);
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

    @Override
    protected int getMaxWidth() {
        if (getContext() instanceof Activity) {
            Activity activity = (Activity) getContext();
            if (activity.getRequestedOrientation() != 9 && activity.getRequestedOrientation() != 9) {
                WindowManager windowManager = (WindowManager) getContext().getSystemService("window");
                if (windowManager == null) {
                    return -1;
                }
                Point point = new Point();
                windowManager.getDefaultDisplay().getRealSize(point);
                return Math.max(point.x, point.y);
            }
        }
        return super.getMaxWidth();
    }

    public Consumer<RecyclerView> getOnCreateCallback() {
        return this.onCreateCallback;
    }

    public CustomBottomRecyclerViewPopup withOnCreateCallback(Consumer<RecyclerView> consumer) {
        this.onCreateCallback = consumer;
        return this;
    }

    public boolean isDismissWhenClick() {
        return this.dismissWhenClick;
    }

    public CustomBottomRecyclerViewPopup dismissWhenClick(boolean z) {
        this.dismissWhenClick = z;
        return this;
    }

    public CustomBottomRecyclerViewPopup withHeight(float f) {
        this.height = f;
        return this;
    }

    public void notifyDataChange() {
        CustomRecyclerViewAdapter customRecyclerViewAdapter = this.adapter;
        if (customRecyclerViewAdapter != null) {
            customRecyclerViewAdapter.notifyDataSetChanged();
        }
    }

    public void notifyDataRemoved(int i) {
        CustomRecyclerViewAdapter customRecyclerViewAdapter = this.adapter;
        if (customRecyclerViewAdapter != null) {
            customRecyclerViewAdapter.notifyItemRemoved(i);
        }
    }

    public CustomBottomRecyclerViewPopup setDismissWhenLongClickMode(int i) {
        this.dismissWhenLongClickMode = i;
        return this;
    }

    public boolean isDark() {
        return this.dark;
    }

    public void setDark(boolean z) {
        this.dark = z;
    }
}
