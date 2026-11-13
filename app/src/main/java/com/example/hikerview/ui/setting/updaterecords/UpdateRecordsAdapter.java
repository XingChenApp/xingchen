package com.example.hikerview.ui.setting.updaterecords;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.hikerview.R;
import com.example.hikerview.ui.view.util.TextViewUtils;

import java.util.List;

/**
 * 官 {@code com.example.hikerview.ui.setting.updaterecords.UpdateRecordsAdapter}
 * 照官 jadx 转写：布局按官 public.xml 还原名 item_txt_title（type=1）/
 * item_txt，标题视图同为 R.id.item_ad_title；holder 在官为 private 内部类，
 * 插件面不触，保持 private。DetatilHolder 拼写照官原样。
 */
public class UpdateRecordsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RecordDetail> list;

    public List<RecordDetail> getList() {
        return this.list;
    }

    public UpdateRecordsAdapter(Context context, List<RecordDetail> list) {
        this.context = context;
        this.list = list;
    }

    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder viewHolder) {
        super.onViewAttachedToWindow(viewHolder);
        if (viewHolder instanceof TitleHolder) {
            TitleHolder titleHolder = (TitleHolder) viewHolder;
            titleHolder.title.setEnabled(false);
            titleHolder.title.setEnabled(true);
        } else if (viewHolder instanceof DetatilHolder) {
            DetatilHolder detatilHolder = (DetatilHolder) viewHolder;
            detatilHolder.title.setEnabled(false);
            detatilHolder.title.setEnabled(true);
        }
    }

    @Override
    public int getItemViewType(int i) {
        return this.list.get(i).getType();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        if (i == 1) {
            return new TitleHolder(LayoutInflater.from(this.context).inflate(R.layout.item_txt_title, viewGroup, false));
        }
        return new DetatilHolder(LayoutInflater.from(this.context).inflate(R.layout.item_txt, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
        if (viewHolder instanceof TitleHolder) {
            TextViewUtils.setSpanText(((TitleHolder) viewHolder).title, this.list.get(i).getDetail());
        } else if (viewHolder instanceof DetatilHolder) {
            TextViewUtils.setSpanText(((DetatilHolder) viewHolder).title, this.list.get(i).getDetail());
        }
    }

    @Override
    public int getItemCount() {
        return this.list.size();
    }

    private class TitleHolder extends RecyclerView.ViewHolder {
        TextView title;

        TitleHolder(View view) {
            super(view);
            this.title = view.findViewById(R.id.item_ad_title);
        }
    }

    private class DetatilHolder extends RecyclerView.ViewHolder {
        TextView title;

        DetatilHolder(View view) {
            super(view);
            this.title = view.findViewById(R.id.item_ad_title);
        }
    }
}
