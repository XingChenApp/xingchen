package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterVodListBinding;
import com.fongmi.android.tv.databinding.AdapterVodOvalBinding;
import com.fongmi.android.tv.databinding.AdapterVodRectBinding;
import com.fongmi.android.tv.ui.base.BaseVodHolder;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.holder.VodListHolder;
import com.fongmi.android.tv.ui.holder.VodOvalHolder;
import com.fongmi.android.tv.ui.holder.VodRectHolder;
import com.fongmi.android.tv.utils.ShortDramaHelper;
import com.fongmi.android.tv.utils.VideoDimensionFetcher;

public class VodAdapter extends BaseDiffAdapter<Vod, BaseVodHolder> {

    private final OnClickListener listener;
    private Style style;
    private final int[] size;
    /** 站点 key，用于判断是否为用户标记的短剧源（只对标记源预取视频尺寸） */
    private String siteKey;

    public VodAdapter(OnClickListener listener, Style style, int[] size) {
        this.listener = listener;
        this.style = style;
        this.size = size;
    }

    public interface OnClickListener {

        /** coverView：点击行 holder 的封面 ImageView（X-17 现算直进用；未就绪时传 null 也可）。 */
        void onItemClick(Vod item, android.view.View coverView);

        boolean onLongClick(Vod item);
    }

    public Style getStyle() {
        return style;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    public void setSiteKey(String siteKey) {
        this.siteKey = siteKey;
    }

    @Override
    public int getItemViewType(int position) {
        String type = getItem(position).getStyle(style).getType();
        if ("list".equals(type)) return ViewType.LIST;
        if ("oval".equals(type)) return ViewType.OVAL;
        return ViewType.RECT;
    }

    @Override
    public void onBindViewHolder(@NonNull BaseVodHolder holder, int position) {
        Vod item = getItem(position);
        holder.initView(item);
        // 短剧源后台预取视频尺寸：用户标记的源才预取，控制流量
        // 注意：列表项通常无直链，此处为尽力而为；有 URL 时防抖预取
        try {
            if (siteKey != null && ShortDramaHelper.isSiteShortDrama(siteKey)
                    || (siteKey != null && ShortDramaHelper.isPluginShortDramaBySite(siteKey))) {
                String playUrl = item.getPlayUrl();
                if (playUrl != null && !playUrl.isEmpty()) {
                    VideoDimensionFetcher.preFetchDebounced(playUrl);
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onViewRecycled(@NonNull BaseVodHolder holder) {
        holder.unbind();
    }

    @NonNull
    @Override
    public BaseVodHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return switch (viewType) {
            case ViewType.LIST -> new VodListHolder(AdapterVodListBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener);
            case ViewType.OVAL -> new VodOvalHolder(AdapterVodOvalBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener).size(size);
            default -> new VodRectHolder(AdapterVodRectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener).size(size);
        };
    }
}
