package com.example.hikerview.ui.view;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.hikerview.R;
import com.example.hikerview.ui.view.util.TextViewUtils;

import java.util.List;

/**
 * 照官 8.83 本体移植（jadx 官包源）：弹窗列表适配器。center 形用圆角卡
 * 条目（item_rect_radius），非 center 形用底边线条目（item_rect_radius2），
 * 各有 dark 变体；选中项文字 redAction，其余按明暗取 black_666/white。
 */
public class CustomRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private boolean center;
    protected Context context;
    private boolean dark;
    private List<String> list;
    private OnItemClickListener onItemClickListener;
    protected boolean reSetTextColor;
    private int selectIndex;

    public interface OnItemClickListener {
        void onClick(View view, int i);

        void onLongClick(View view, int i);
    }

    public CustomRecyclerViewAdapter(Context context, List<String> list, OnItemClickListener onItemClickListener) {
        this(context, list, onItemClickListener, true);
    }

    public CustomRecyclerViewAdapter(Context context, List<String> list, OnItemClickListener onItemClickListener, boolean z) {
        this.reSetTextColor = true;
        this.selectIndex = -1;
        this.context = context;
        this.list = list;
        this.onItemClickListener = onItemClickListener;
        this.center = z;
    }

    public int getSelectIndex() {
        return this.selectIndex;
    }

    public void setSelectIndex(int i) {
        this.selectIndex = i;
    }

    public boolean isDark() {
        return this.dark;
    }

    public void setDark(boolean z) {
        this.dark = z;
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        int i2;
        LayoutInflater from = LayoutInflater.from(this.context);
        if (this.center) {
            i2 = this.dark ? R.layout.item_rect_radius_dark : R.layout.item_rect_radius;
        } else {
            i2 = this.dark ? R.layout.item_rect_radius2_dark : R.layout.item_rect_radius2;
        }
        return new ArticleListRuleHolder(from.inflate(i2, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
        if (viewHolder instanceof ArticleListRuleHolder) {
            final ArticleListRuleHolder articleListRuleHolder = (ArticleListRuleHolder) viewHolder;
            TextViewUtils.setSpanText(articleListRuleHolder.item_rect_text, this.list.get(i));
            int i2 = this.selectIndex;
            int i3 = R.color.white;
            if (i2 >= 0) {
                if (i2 == i) {
                    articleListRuleHolder.item_rect_text.setTextColor(this.context.getResources().getColor(R.color.redAction));
                } else {
                    TextView textView = articleListRuleHolder.item_rect_text;
                    Resources resources = this.context.getResources();
                    if (!this.dark) {
                        i3 = R.color.black_666;
                    }
                    textView.setTextColor(resources.getColor(i3));
                }
            } else if (this.reSetTextColor) {
                TextView textView2 = articleListRuleHolder.item_rect_text;
                Resources resources2 = this.context.getResources();
                if (!this.dark) {
                    i3 = R.color.black_666;
                }
                textView2.setTextColor(resources2.getColor(i3));
            }
            articleListRuleHolder.item_rect_text.setOnClickListener(view ->
                    forwardItemClick(articleListRuleHolder, view));
            articleListRuleHolder.item_rect_text.setOnLongClickListener(view ->
                    forwardItemLongClick(articleListRuleHolder, view));
        }
    }

    private void forwardItemClick(ArticleListRuleHolder articleListRuleHolder, View view) {
        if (this.onItemClickListener == null || articleListRuleHolder.getAdapterPosition() < 0) {
            return;
        }
        this.onItemClickListener.onClick(view, articleListRuleHolder.getAdapterPosition());
    }

    private boolean forwardItemLongClick(ArticleListRuleHolder articleListRuleHolder, View view) {
        if (this.onItemClickListener == null || articleListRuleHolder.getAdapterPosition() < 0) {
            return true;
        }
        this.onItemClickListener.onLongClick(view, articleListRuleHolder.getAdapterPosition());
        return true;
    }

    @Override
    public int getItemCount() {
        List<String> list = this.list;
        if (list == null) {
            return 0;
        }
        return list.size();
    }

    protected static class ArticleListRuleHolder extends RecyclerView.ViewHolder {
        TextView item_rect_text;

        public ArticleListRuleHolder(View view) {
            super(view);
            this.item_rect_text = (TextView) view.findViewById(R.id.item_rect_text);
        }
    }
}
