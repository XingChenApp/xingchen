package com.example.hikerview.ui.setting.file;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.hikerview.R;

import java.util.List;

/**
 * 照官 8.83 本体移植（jadx 官包源，资源号已按官 public.xml 还原名）：
 * 文件详情条目适配器（item_file_detail 单行文本，点击/长按回调）。
 */
public class FileDetailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private OnClickListener clickListener;
    private Context context;
    private List<String> list;

    public interface OnClickListener {
        void click(String str);

        void longClick(View view, String str);
    }

    public List<String> getList() {
        return this.list;
    }

    public FileDetailAdapter(Context context, List<String> list, OnClickListener onClickListener) {
        this.context = context;
        this.list = list;
        this.clickListener = onClickListener;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        return new TitleHolder(LayoutInflater.from(this.context).inflate(R.layout.item_file_detail, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
        if (viewHolder instanceof TitleHolder) {
            final TitleHolder titleHolder = (TitleHolder) viewHolder;
            titleHolder.title.setText(this.list.get(i));
            titleHolder.bg.setOnClickListener(view ->
                    this.clickListener.click(this.list.get(titleHolder.getAdapterPosition())));
            titleHolder.bg.setOnLongClickListener(view -> {
                this.clickListener.longClick(view, this.list.get(titleHolder.getAdapterPosition()));
                return true;
            });
        }
    }

    @Override
    public int getItemCount() {
        return this.list.size();
    }

    class TitleHolder extends RecyclerView.ViewHolder {
        View bg;
        TextView title;

        TitleHolder(View view) {
            super(view);
            this.bg = view.findViewById(R.id.bg);
            this.title = (TextView) view.findViewById(R.id.textView);
        }
    }
}
