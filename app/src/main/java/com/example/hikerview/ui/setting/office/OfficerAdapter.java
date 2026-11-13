package com.example.hikerview.ui.setting.office;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hikerview.R;
import com.example.hikerview.utils.StringUtil;

import java.util.List;

/**
 * 照官 8.83 本体移植（jadx 官包源，资源号已按官 public.xml 还原名）：
 * 设置菜单条目适配器。空标题条目走分隔线型（item_line_card），余为
 * 标题型（item_setting_more）；选中态图标：0=右箭头、1=实心勾框、余=空心框。
 */
public class OfficerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private OnClickListener clickListener;
    private Context context;
    private List<OfficeItem> list;

    public interface OnClickListener {
        void click(String str, OfficeItem officeItem);
    }

    public List<OfficeItem> getList() {
        return this.list;
    }

    public OfficerAdapter(Context context, List<OfficeItem> list, OnClickListener onClickListener) {
        this.context = context;
        this.list = list;
        this.clickListener = onClickListener;
    }

    @Override
    public int getItemViewType(int i) {
        return StringUtil.isEmpty(this.list.get(i).getTitle()) ? -1 : 1;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        if (i == -1) {
            return new LineHolder(LayoutInflater.from(this.context).inflate(R.layout.item_line_card, viewGroup, false));
        }
        return new TitleHolder(LayoutInflater.from(this.context).inflate(R.layout.item_setting_more, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
        if (viewHolder instanceof TitleHolder) {
            final TitleHolder titleHolder = (TitleHolder) viewHolder;
            titleHolder.title.setText(this.list.get(i).getTitle());
            int selected = this.list.get(i).getSelected();
            if (selected == 0) {
                titleHolder.imageView.setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_arrow_right));
            } else if (selected == 1) {
                titleHolder.imageView.setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.icon_rect_fill2));
            } else {
                titleHolder.imageView.setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.icon_rect));
            }
            String desc = this.list.get(i).getDesc();
            if (desc == null) {
                desc = "";
            }
            titleHolder.desc.setText(desc);
            titleHolder.bg.setOnClickListener(view -> {
                int pos = titleHolder.getBindingAdapterPosition();
                this.clickListener.click(this.list.get(pos).getTitle(), this.list.get(pos));
            });
        }
    }

    @Override
    public int getItemCount() {
        return this.list.size();
    }

    class TitleHolder extends RecyclerView.ViewHolder {
        View bg;
        TextView desc;
        ImageView imageView;
        TextView title;

        TitleHolder(View view) {
            super(view);
            this.bg = view.findViewById(R.id.bg);
            this.title = (TextView) view.findViewById(R.id.textView);
            this.desc = (TextView) view.findViewById(R.id.descView);
            this.imageView = (ImageView) view.findViewById(R.id.imageView);
        }
    }

    private class LineHolder extends RecyclerView.ViewHolder {
        LineHolder(View view) {
            super(view);
        }
    }
}
