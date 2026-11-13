package com.fongmi.android.tv.ui.holder;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.api.TmdbApi;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterVodRectBinding;
import com.fongmi.android.tv.ui.adapter.VodAdapter;
import com.fongmi.android.tv.ui.base.BaseVodHolder;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.Locale;

public class VodRectHolder extends BaseVodHolder {

    private final VodAdapter.OnClickListener listener;
    private final AdapterVodRectBinding binding;
    private String boundTitle = "";
    private int[] specSize;
    private Runnable tmdbRunnable;

    public VodRectHolder(@NonNull AdapterVodRectBinding binding, VodAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
    }

    public VodRectHolder size(int[] size) {
        this.specSize = size;
        binding.imageWrap.getLayoutParams().height = size[1];
        binding.getRoot().getLayoutParams().width = size[0];
        return this;
    }

    @Override
    public void initView(Vod item) {
        binding.name.setText(item.getName());
        binding.year.setText(item.getYear());
        binding.site.setText(item.getSiteName());
        binding.remark.setText(item.getRemarks());
        binding.rating.setVisibility(View.GONE);
        binding.site.setVisibility(item.getSiteVisible());
        binding.name.setVisibility(item.getNameVisible());
        binding.year.setVisibility(item.getYearVisible());
        binding.remark.setVisibility(item.getRemarkVisible());
        binding.getRoot().setOnClickListener(v -> listener.onItemClick(item, binding.image));
        binding.getRoot().setOnLongClickListener(v -> listener.onLongClick(item));
        if (specSize != null) ImgUtil.loadTopRounded(item.getName(), item.getPic(), binding.image, specSize[0], specSize[1]);
        else ImgUtil.loadTopRounded(item.getName(), item.getPic(), binding.image, 0, 0);
        boundTitle = item.getName();
        // TMDB 请求防抖：item 可见超过 300ms 才触发，快速滑动时不发起请求
        binding.getRoot().removeCallbacks(tmdbRunnable);
        if (!TextUtils.isEmpty(boundTitle) && TmdbApi.isConfigured(binding.getRoot().getContext())) {
            final String title = boundTitle;
            tmdbRunnable = () -> {
                if (!title.equals(boundTitle)) return;
                TmdbApi.fetch(binding.getRoot().getContext(), title, info -> {
                    if (info == null || !title.equals(boundTitle) || !title.equals(item.getName())) return;
                    if (!TextUtils.isEmpty(info.year)) {
                        binding.year.setText(info.year);
                        binding.year.setVisibility(View.VISIBLE);
                    }
                    if (info.rating > 0) {
                        binding.rating.setText(String.format(Locale.US, "%.1f", info.rating));
                        binding.rating.setVisibility(View.VISIBLE);
                    }
                });
            };
            binding.getRoot().postDelayed(tmdbRunnable, 300);
        }
    }

    @Override
    public void unbind() {
        boundTitle = "";
        binding.getRoot().removeCallbacks(tmdbRunnable);
        Glide.with(binding.image).clear(binding.image);
    }
}
