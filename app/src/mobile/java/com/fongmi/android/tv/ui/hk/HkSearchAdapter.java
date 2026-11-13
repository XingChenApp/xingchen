package com.fongmi.android.tv.ui.hk;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.hk.HkSearchEngine;
import com.fongmi.android.tv.api.hk.SearchResult;

import java.util.List;
import java.util.Random;

/**
 * 照 8.83 SearchAdapter（ui/search/SearchAdapter.smali，789 行）转写：
 * 条目布局 item_search_all；标题经 StringUtil.replaceContains 高亮
 * （关键词段包 #FF0000 红、其余段包 #003D8F，Html.fromHtml 上屏）；
 * 标题含画质/语言词（words 表）时 notice 显示 " · 词"；
 * 有图显示 72×100 图卡（GlideUtil.getGlideUrl(search_url, img)），desc/content/descMore 按非空显隐，
 * descMore 在 desc 非空时加 " · " 前缀；点击/长按回调带 (view, position, result.desc)。
 * 占位图：原版为 5 色浅色随机 ColorDrawable，按用户令（占位透明，D6）改透明占位，其余不变。
 */
public class HkSearchAdapter extends RecyclerView.Adapter<HkSearchAdapter.MyViewHolder> {

    public interface OnItemClickListener {
        void onClick(View view, int position, String desc);

        void onLongClick(View view, int position, String desc);
    }

    private static final String[] WORDS = {"蓝光", "超清", "高清", "BD", "HD", "国语", "粤语"};

    /** 原版 colors 五色（占位随机色，用户令改透明后仅保留数值备查）。 */
    private static final int[] COLORS = {-0x81101, -0xf190c1a, -0xa0a0b, -0xf0401, -0x10c11};

    private final Context context;
    private final List<SearchResult> list;
    private final String keyWord;
    private final HkSearchEngine searchEngine;
    private final Random random = new Random();
    private OnItemClickListener onItemClickListener;

    public HkSearchAdapter(Context context, List<SearchResult> list, String keyWord,
                           HkSearchEngine searchEngine) {
        this.context = context;
        this.list = list;
        this.keyWord = keyWord;
        this.searchEngine = searchEngine;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public List<SearchResult> getList() {
        return list;
    }

    public int getListSize() {
        return list == null ? 0 : list.size();
    }

    public void add(List<SearchResult> data) {
        list.addAll(data);
        notifyDataSetChanged();
    }

    /** 照原版 removeFooter：列表非空时删末项并刷新，恒回 true。 */
    public boolean removeFooter() {
        if (list.size() > 0) {
            list.remove(list.size() - 1);
            notifyDataSetChanged();
        }
        return true;
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_search_all, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        SearchResult result = list.get(position);
        String title = result.getTitle();
        if (title != null) {
            String highlighted = HkStringUtil.replaceContains(title, keyWord,
                    "<font color=#FF0000>", "</font>", "<font color=#003D8F>", "</font>");
            holder.title.setText(Html.fromHtml(highlighted));
        } else {
            holder.title.setText("");
        }
        int wordPos = getWordPos(title);
        if (wordPos >= 0) {
            holder.notice.setText(" · " + WORDS[wordPos]);
            holder.notice.setVisibility(View.VISIBLE);
        } else {
            holder.notice.setVisibility(View.GONE);
        }
        String img = result.getImg();
        if (!TextUtils.isEmpty(img)) {
            holder.imgBg.setVisibility(View.VISIBLE);
            int colorIdx = random.nextInt(COLORS.length);
            if (colorIdx < 0) colorIdx = 0;
            if (colorIdx >= COLORS.length) colorIdx = COLORS.length - 1;
            RequestOptions options = new RequestOptions()
                    .placeholder(new ColorDrawable(Color.TRANSPARENT));
            Glide.with(context)
                    .load(HkGlideUtil.getGlideUrl(searchEngine.getSearch_url(), img))
                    .apply(options)
                    .into(holder.img);
        } else {
            holder.imgBg.setVisibility(View.GONE);
        }
        String desc = result.getDesc();
        if (!TextUtils.isEmpty(desc)) {
            holder.desc.setVisibility(View.VISIBLE);
            holder.desc.setText(desc);
        } else {
            holder.desc.setVisibility(View.GONE);
        }
        String content = result.getContent();
        if (!TextUtils.isEmpty(content)) {
            holder.content.setVisibility(View.VISIBLE);
            holder.content.setText(content);
        } else {
            holder.content.setVisibility(View.GONE);
        }
        String descMore = result.getDescMore();
        if (!TextUtils.isEmpty(descMore)) {
            holder.descMore.setVisibility(View.VISIBLE);
            if (!TextUtils.isEmpty(desc)) {
                holder.descMore.setText(" · " + descMore);
            } else {
                holder.descMore.setText(descMore);
            }
        } else {
            holder.descMore.setVisibility(View.GONE);
        }
        holder.containner.setOnClickListener(v -> {
            if (onItemClickListener == null) return;
            int pos = holder.getLayoutPosition();
            if (pos < 0 || pos >= list.size()) return;
            onItemClickListener.onClick(v, pos, list.get(pos).getDesc());
        });
        holder.containner.setOnLongClickListener(v -> {
            if (onItemClickListener == null) return true;
            int pos = holder.getLayoutPosition();
            if (pos < 0 || pos >= list.size()) return true;
            onItemClickListener.onLongClick(v, pos, list.get(pos).getDesc());
            return true;
        });
    }

    /** 照原版 getWordPos：标题含 words[i] 回 i，否则 -1（标题空回 -1）。 */
    private int getWordPos(String title) {
        if (HkStringUtil.isEmpty(title)) return -1;
        for (int i = 0; i < WORDS.length; i++) {
            if (title.contains(WORDS[i])) return i;
        }
        return -1;
    }

    static class MyViewHolder extends RecyclerView.ViewHolder {
        final RelativeLayout containner;
        final View imgBg;
        final ImageView img;
        final TextView title;
        final TextView content;
        final TextView desc;
        final TextView notice;
        final TextView descMore;

        MyViewHolder(View itemView) {
            super(itemView);
            containner = itemView.findViewById(R.id.chooseitem_all_RelativeLayout);
            imgBg = itemView.findViewById(R.id.chooseitem_all_img_bg);
            img = itemView.findViewById(R.id.chooseitem_all_img);
            title = itemView.findViewById(R.id.chooseitem_all_title);
            content = itemView.findViewById(R.id.search_all_content);
            desc = itemView.findViewById(R.id.chooseitem_all_desc);
            notice = itemView.findViewById(R.id.choose_item_all_notice);
            descMore = itemView.findViewById(R.id.choose_item_all_desc_more);
        }
    }
}
