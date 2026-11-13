package com.example.hikerview.ui.setting;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.function.Consumer;
import com.example.hikerview.R;
import com.example.hikerview.ui.setting.office.OfficeItem;
import com.example.hikerview.ui.setting.office.OfficerAdapter;
import com.example.hikerview.utils.StringUtil;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.util.XPopupUtils;

import java.util.List;

import timber.log.Timber;

/**
 * 照官 8.83 本体移植（jadx 官包源，资源号已按官 public.xml 还原名）：
 * 设置菜单底部弹窗（OfficeItem 列表）；带 searchKey 时延迟滚动到同名
 * 条目，未选中的再延迟点一下。与官同布局 view_setting_menu_popup，
 * 高度 0.85 屏高。
 */
public class SettingMenuPopup extends BottomPopupView {

    private OnItemClickListener onItemClickListener;
    private List<OfficeItem> operations;
    private String searchKey;
    private String title;

    public interface OnItemClickListener {
        void onClick(String str, OfficeItem officeItem, Consumer<OfficeItem> consumer);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.view_setting_menu_popup;
    }

    public SettingMenuPopup(Context context) {
        super(context);
    }

    public SettingMenuPopup(Activity activity, String str, List<OfficeItem> list, String str2, OnItemClickListener onItemClickListener) {
        super(activity);
        this.title = str;
        this.onItemClickListener = onItemClickListener;
        this.operations = list;
        this.searchKey = str2;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        ((TextView) findViewById(R.id.textView)).setText(this.title);
        final RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(new OfficerAdapter(getContext(), this.operations,
                (str, officeItem) -> onItemForward(recyclerView, str, officeItem)));
        if (StringUtil.isNotEmpty(this.searchKey)) {
            recyclerView.postDelayed(() -> scrollToSearchKey(recyclerView), 500L);
        }
    }

    private void onItemForward(final RecyclerView recyclerView, String str, OfficeItem officeItem) {
        OnItemClickListener listener = this.onItemClickListener;
        if (listener != null) {
            listener.onClick(str, officeItem, updated -> {
                try {
                    recyclerView.getAdapter().notifyItemChanged(this.operations.indexOf(updated));
                } catch (Exception e) {
                    Timber.e(e);
                }
            });
        }
    }

    private void scrollToSearchKey(final RecyclerView recyclerView) {
        for (int i = 0; i < this.operations.size(); i++) {
            if (this.searchKey.equals(this.operations.get(i).getTitle())) {
                recyclerView.smoothScrollToPosition(i);
                if (this.operations.get(i).getSelected() == 0) {
                    final int index = i;
                    recyclerView.postDelayed(() -> {
                        View holder = recyclerView.getLayoutManager().findViewByPosition(index);
                        View bg;
                        if (holder == null || (bg = holder.findViewById(R.id.bg)) == null) {
                            return;
                        }
                        bg.performClick();
                    }, 300L);
                }
                return;
            }
        }
    }

    @Override
    public BasePopupView show() {
        return super.show();
    }

    @Override
    protected int getPopupHeight() {
        return (int) (XPopupUtils.getScreenHeight(getContext()) * 0.85f);
    }
}
