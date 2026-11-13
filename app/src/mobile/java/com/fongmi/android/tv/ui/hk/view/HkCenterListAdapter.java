package com.fongmi.android.tv.ui.hk.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.hk.HkTextViewUtils;

import java.util.List;

/**
 * 照抄 8.83 CustomRecyclerViewAdapter（smali_classes2/ui/view/CustomRecyclerViewAdapter.smali）：
 * 中央选择弹窗的文本网格适配器。四种条目布局按 center/dark 组合选：
 * center+dark→item_rect_radius_dark、center→item_rect_radius、dark→item_rect_radius2_dark、其余→item_rect_radius2。
 * 选中项（selectIndex>=0 且相等）文字 redAction；其余在 reSetTextColor 时按 dark 取 white/black_666。
 */
public class HkCenterListAdapter extends RecyclerView.Adapter<HkCenterListAdapter.ArticleListRuleHolder> {

    public interface OnItemClickListener {
        void onClick(View view, int position);

        void onLongClick(View view, int position);
    }

    private final Context context;
    private final List<String> list;
    private OnItemClickListener onItemClickListener;
    private final boolean center;
    private boolean dark;
    private boolean reSetTextColor = true;
    private int selectIndex = -1;

    public HkCenterListAdapter(Context context, List<String> list, OnItemClickListener listener) {
        this(context, list, listener, true);
    }

    public HkCenterListAdapter(Context context, List<String> list, OnItemClickListener listener, boolean center) {
        this.context = context;
        this.list = list;
        this.onItemClickListener = listener;
        this.center = center;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void setDark(boolean dark) {
        this.dark = dark;
    }

    public boolean isDark() {
        return dark;
    }

    public void setSelectIndex(int selectIndex) {
        this.selectIndex = selectIndex;
    }

    public int getSelectIndex() {
        return selectIndex;
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    @NonNull
    @Override
    public ArticleListRuleHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId;
        if (center) {
            layoutId = dark ? R.layout.item_rect_radius_dark : R.layout.item_rect_radius;
        } else {
            layoutId = dark ? R.layout.item_rect_radius2_dark : R.layout.item_rect_radius2;
        }
        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return new ArticleListRuleHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArticleListRuleHolder holder, int position) {
        {
            ArticleListRuleHolder h = holder;
            String item = list.get(position);
            HkTextViewUtils.setSpanText(h.item_rect_text, item);
            if (selectIndex >= 0) {
                if (selectIndex == position) {
                    h.item_rect_text.setTextColor(context.getResources().getColor(R.color.redAction));
                } else {
                    h.item_rect_text.setTextColor(context.getResources().getColor(dark ? R.color.white : R.color.black_666));
                }
            } else if (reSetTextColor) {
                h.item_rect_text.setTextColor(context.getResources().getColor(dark ? R.color.white : R.color.black_666));
            }
            h.item_rect_text.setOnClickListener(v -> {
                if (onItemClickListener != null && h.getAdapterPosition() >= 0) {
                    onItemClickListener.onClick(v, h.getAdapterPosition());
                }
            });
            h.item_rect_text.setOnLongClickListener(v -> {
                if (onItemClickListener != null && h.getAdapterPosition() >= 0) {
                    onItemClickListener.onLongClick(v, h.getAdapterPosition());
                }
                return true;
            });
        }
    }

    public static class ArticleListRuleHolder extends RecyclerView.ViewHolder {
        public TextView item_rect_text;

        public ArticleListRuleHolder(View itemView) {
            super(itemView);
            item_rect_text = itemView.findViewById(R.id.item_rect_text);
        }
    }
}
