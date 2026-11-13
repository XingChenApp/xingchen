package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.ui.adapter.VodAdapter;
import com.fongmi.android.tv.utils.ImgUtil;

/**
 * 点播列表图片预加载器。
 *
 * 下滑时提前把后面 N 个 item 的封面丢给 Glide 预加载，
 * 滑到时直接读缓存，不用等下载。Glide 的 preload 是幂等的，
 * 已在缓存或已在队列中的请求不会重复执行，所以 onScrolled 高频调用是安全的。
 */
public class VodPreloader extends RecyclerView.OnScrollListener {

    /** 向前预加载的 item 数量：太多浪费流量，太少没效果，10 个是经验值 */
    private static final int PRELOAD_AHEAD = 10;

    @Override
    public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
        if (dy <= 0) return;
        RecyclerView.Adapter<?> a = view.getAdapter();
        if (!(a instanceof VodAdapter)) return;
        VodAdapter adapter = (VodAdapter) a;
        RecyclerView.LayoutManager lm = view.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return;
        int lastVisible = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
        if (lastVisible == RecyclerView.NO_POSITION) return;
        int total = adapter.getItemCount();
        if (total == 0) return;
        Context context = view.getContext();
        int end = Math.min(lastVisible + PRELOAD_AHEAD, total - 1);
        for (int i = lastVisible + 1; i <= end; i++) {
            Vod vod;
            try {
                vod = adapter.getItem(i);
            } catch (Exception e) {
                continue;
            }
            if (vod == null) continue;
            String pic = vod.getPic();
            if (!TextUtils.isEmpty(pic)) ImgUtil.preload(context, pic);
        }
    }
}
