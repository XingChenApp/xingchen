package com.example.hikerview.ui.setting.file;

import android.app.Activity;
import android.content.Context;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hikerview.R;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.util.XPopupUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 照官 8.83 本体移植（jadx 官包源，资源号已按官 public.xml 还原名）：
 * 文件详情底部弹窗（字符串列表），与 SettingMenuPopup 共用
 * view_setting_menu_popup 布局，高度 0.75 屏高。
 */
public class FileDetailPopup extends BottomPopupView {

    private FileDetailAdapter adapter;
    private FileDetailAdapter.OnClickListener clickListener;
    private List<String> operations;
    private String title;

    public interface OnItemClickListener {
        void onClick(String str);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.view_setting_menu_popup;
    }

    public FileDetailPopup(Context context) {
        super(context);
    }

    public FileDetailPopup(Activity activity, String str, String[] strArr) {
        super(activity);
        this.title = str;
        ArrayList<String> list = new ArrayList<>();
        this.operations = list;
        list.addAll(Arrays.asList(strArr));
    }

    public FileDetailPopup(Activity activity, String str, List<String> list) {
        super(activity);
        this.title = str;
        this.operations = list;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        ((TextView) findViewById(R.id.textView)).setText(this.title);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        FileDetailAdapter fileDetailAdapter = new FileDetailAdapter(getContext(), this.operations, new FileDetailAdapter.OnClickListener() {
            @Override
            public void click(String str) {
                if (FileDetailPopup.this.clickListener != null) {
                    FileDetailPopup.this.clickListener.click(str);
                }
            }

            @Override
            public void longClick(android.view.View view, String str) {
                if (FileDetailPopup.this.clickListener != null) {
                    FileDetailPopup.this.clickListener.longClick(view, str);
                }
            }
        });
        this.adapter = fileDetailAdapter;
        recyclerView.setAdapter(fileDetailAdapter);
    }

    public void updateData(String[] strArr) {
        this.operations.clear();
        this.operations.addAll(Arrays.asList(strArr));
        this.adapter.notifyDataSetChanged();
    }

    @Override
    public BasePopupView show() {
        return super.show();
    }

    @Override
    protected int getPopupHeight() {
        return (int) (XPopupUtils.getScreenHeight(getContext()) * 0.75f);
    }

    public FileDetailPopup withClickListener(FileDetailAdapter.OnClickListener onClickListener) {
        this.clickListener = onClickListener;
        return this;
    }

    public FileDetailAdapter.OnClickListener getClickListener() {
        return this.clickListener;
    }

    public void setClickListener(FileDetailAdapter.OnClickListener onClickListener) {
        this.clickListener = onClickListener;
    }
}
