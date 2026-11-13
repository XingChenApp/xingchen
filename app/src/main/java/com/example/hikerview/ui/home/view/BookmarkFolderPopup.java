package com.example.hikerview.ui.home.view;

import android.content.Context;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hikerview.R;
import com.example.hikerview.model.Bookmark;
import com.example.hikerview.utils.StringUtil;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.CenterPopupView;
import com.lxj.xpopup.util.XPopupUtils;

import java.util.List;

/**
 * 照官 8.83 本体移植（jadx 官包源，资源号已按官 public.xml 还原名）：
 * 收藏文件夹居中弹窗（pop_bookmark_folder），点条目先关窗再回调
 * (url, index)；关闭时把 XPopup 动画时长设回 300（官原样）。
 */
public class BookmarkFolderPopup extends CenterPopupView {

    private boolean center;
    private ClickListener clickListener;
    private List<Bookmark> data;
    private int selectedIndex;
    private int span;
    private String title;

    public interface ClickListener {
        void click(String str, int i);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.pop_bookmark_folder;
    }

    public BookmarkFolderPopup with(List<Bookmark> list, int i, ClickListener clickListener) {
        this.data = list;
        this.clickListener = clickListener;
        this.span = i;
        return this;
    }

    public BookmarkFolderPopup useCenter(boolean z) {
        this.center = z;
        return this;
    }

    public BookmarkFolderPopup withTitle(String str) {
        this.title = str;
        return this;
    }

    public BookmarkFolderPopup withSelectedIndex(int i) {
        this.selectedIndex = i;
        return this;
    }

    public BookmarkFolderPopup(Context context) {
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
        BookmarkFolderAdapter bookmarkFolderAdapter = new BookmarkFolderAdapter(getContext(), this.data,
                (view, i) -> dismissWith(() -> this.clickListener.click(this.data.get(i).getUrl(), i)), this.center);
        bookmarkFolderAdapter.setSelectedIndex(this.selectedIndex);
        recyclerView.setAdapter(bookmarkFolderAdapter);
    }

    @Override
    protected int getMaxHeight() {
        return (int) (XPopupUtils.getScreenHeight(getContext()) * 0.65f);
    }

    @Override
    public void dismiss() {
        super.dismiss();
        XPopup.setAnimationDuration(300);
    }
}
