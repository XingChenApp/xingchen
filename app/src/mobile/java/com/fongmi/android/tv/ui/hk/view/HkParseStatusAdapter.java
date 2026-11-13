package com.fongmi.android.tv.ui.hk.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.hk.HkRemotePlaySource;

import java.net.URLDecoder;
import java.util.List;

/**
 * 照抄 8.83 SimpleParseStatusAdapter（smali_classes2/ui/home/SimpleParseStatusAdapter.smali）：
 * 标题按 UTF-8 URL 解码后显示；未解析完→状态文「正在获取地址...」+ 转圈可见；
 * 解析完→状态文 = parseResult + 转圈 INVISIBLE。转圈原版是 SpinKitView，本树以 ProgressBar 承接。
 */
public class HkParseStatusAdapter extends RecyclerView.Adapter<HkParseStatusAdapter.Holder> {

    private final Context context;
    private final List<HkRemotePlaySource> data;

    public HkParseStatusAdapter(Context context, List<HkRemotePlaySource> data) {
        this.context = context;
        this.data = data;
    }

    public List<HkRemotePlaySource> getData() {
        return data;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.hk_item_parse_status, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        HkRemotePlaySource source = data.get(position);
        String title = source.getTitle();
        try {
            title = URLDecoder.decode(title == null ? "" : title, "UTF-8");
        } catch (Exception ignored) {
        }
        holder.tvTitle.setText(title);
        if (!source.isParseFinish()) {
            holder.tvStatus.setText("正在获取地址...");
            holder.spin.setVisibility(View.VISIBLE);
        } else {
            holder.tvStatus.setText(source.getParseResult());
            holder.spin.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvStatus;
        final View spin;

        Holder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.hk_parse_status_item_title);
            tvStatus = itemView.findViewById(R.id.hk_parse_status_item_status);
            spin = itemView.findViewById(R.id.hk_parse_status_item_spin);
        }
    }
}
