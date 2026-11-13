package com.example.hikerview.ui.home.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.hikerview.R;
import com.example.hikerview.model.Bookmark;
import com.example.hikerview.utils.GlideUtil;
import com.example.hikerview.utils.StringUtil;

import java.net.URI;
import java.util.List;

import timber.log.Timber;

/**
 * 照官 8.83 本体移植（jadx 官包源，资源号已按官 public.xml 还原名）：
 * 收藏文件夹条目适配器。记名差异一处：官缩略图占位走其 Glide
 * 生成类 GlideApp.with(...).asDrawable()，星辰无该生成类，以
 * Glide.with(...).asDrawable() 等义落（同为本进程 Glide 入口）。
 * icons 四图（tree2/tree1/tree3/tree4）官仅置于字段、无读点，照留。
 */
public class BookmarkFolderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private boolean center;
    protected Context context;
    private int[] icons;
    private List<Bookmark> list;
    private OnItemClickListener onItemClickListener;
    protected boolean reSetTextColor;
    private int selectedIndex;

    public interface OnItemClickListener {
        void onClick(View view, int i);
    }

    public BookmarkFolderAdapter(Context context, List<Bookmark> list, OnItemClickListener onItemClickListener) {
        this(context, list, onItemClickListener, true);
    }

    public BookmarkFolderAdapter(Context context, List<Bookmark> list, OnItemClickListener onItemClickListener, boolean z) {
        this.reSetTextColor = true;
        this.selectedIndex = -1;
        this.icons = new int[]{R.drawable.tree2, R.drawable.tree1, R.drawable.tree3, R.drawable.tree4};
        this.context = context;
        this.list = list;
        this.onItemClickListener = onItemClickListener;
        this.center = z;
    }

    public int getSelectedIndex() {
        return this.selectedIndex;
    }

    public void setSelectedIndex(int i) {
        this.selectedIndex = i;
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        return new TitleHolder(LayoutInflater.from(this.context).inflate(R.layout.item_bookmark_folder, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
        if (viewHolder instanceof TitleHolder) {
            final TitleHolder titleHolder = (TitleHolder) viewHolder;
            Bookmark bookmark = this.list.get(i);
            titleHolder.title.setText(bookmark.getTitle());
            int i2 = this.selectedIndex;
            if (i2 >= 0) {
                if (i2 == i) {
                    titleHolder.title.setTextColor(this.context.getResources().getColor(R.color.redAction));
                } else {
                    titleHolder.title.setTextColor(this.context.getResources().getColor(R.color.blackText));
                }
            }
            if (StringUtil.isEmpty(bookmark.getUrl()) || bookmark.isDir()) {
                Glide.with(this.context).load(R.drawable.ic_bookmark_folder).into(titleHolder.item_icon);
            } else {
                String icon = bookmark.getIcon();
                if (StringUtil.isEmpty(icon)) {
                    try {
                        URI create = URI.create(bookmark.getUrl());
                        icon = create.getScheme() + "://" + create.getHost() + "/favicon.ico";
                    } catch (Exception e) {
                        Timber.e(e);
                        icon = StringUtil.getHomeUrl(bookmark.getUrl()) + "/favicon.ico";
                    }
                }
                Glide.with(this.context).load(GlideUtil.getGlideUrl(null, icon)).thumbnail(Glide.with(this.context).asDrawable().load((Integer) R.drawable.ic_bookmark_url)).apply(new RequestOptions().circleCrop()).into(titleHolder.item_icon);
            }
            titleHolder.item_bg.setOnClickListener(view -> {
                if (this.onItemClickListener == null || titleHolder.getAdapterPosition() < 0) {
                    return;
                }
                this.onItemClickListener.onClick(view, titleHolder.getAdapterPosition());
            });
        }
    }

    @Override
    public int getItemCount() {
        List<Bookmark> list = this.list;
        if (list == null) {
            return 0;
        }
        return list.size();
    }

    class TitleHolder extends RecyclerView.ViewHolder {
        CardView item_bg;
        ImageView item_icon;
        TextView title;

        TitleHolder(View view) {
            super(view);
            this.item_bg = view.findViewById(R.id.item_bg);
            this.item_icon = (ImageView) view.findViewById(R.id.item_icon);
            this.title = (TextView) view.findViewById(R.id.item_title);
        }
    }
}
