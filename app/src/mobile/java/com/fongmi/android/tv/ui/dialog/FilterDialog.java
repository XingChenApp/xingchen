package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.databinding.DialogFilterBinding;
import com.fongmi.android.tv.impl.FilterListener;
import com.fongmi.android.tv.ui.adapter.FilterAdapter;

import java.util.List;

public class FilterDialog extends BaseBottomSheetDialog {

    private DialogFilterBinding binding;
    private FilterListener listener;
    private List<Filter> filter;

    public static FilterDialog create() {
        return new FilterDialog();
    }

    public FilterDialog filter(List<Filter> filter) {
        this.filter = filter;
        // 列表走 arguments 存一份：系统重建弹窗时实例字段会丢，arguments 能原样恢复（Filter 是 Parcelable）。
        android.os.Bundle args = getArguments();
        if (args == null) {
            args = new android.os.Bundle();
            setArguments(args);
        }
        args.putParcelableArrayList("filter", filter == null ? null : new java.util.ArrayList<>(filter));
        return this;
    }

    public void show(Fragment fragment) {
        if (fragment == null || !fragment.isAdded() || fragment.getChildFragmentManager().isStateSaved()) return;
        for (Fragment f : fragment.getChildFragmentManager().getFragments()) if (f instanceof FilterDialog) return;
        this.listener = fragment instanceof FilterListener filterListener ? filterListener : null;
        show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogFilterBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (filter == null && getArguments() != null) filter = getArguments().getParcelableArrayList("filter");
        if (filter == null) filter = new java.util.ArrayList<>();
        // 重建后 listener 字段已丢，从父 Fragment 重新取（show 时就是从它拿的）。
        if (listener == null && getParentFragment() instanceof FilterListener parent) listener = parent;
        binding.recycler.setHasFixedSize(true);
        // 封顶照 TypeDialog 同形（Hm-16）：列表换 CustomRecyclerView 后按屏高比例写死上限，超出框内滚动，不再整表撑满屏
        int ratio = com.fongmi.android.tv.utils.ResUtil.isLand(requireContext()) ? 46 : 58;
        binding.recycler.setMaxHeight(com.fongmi.android.tv.utils.ResUtil.getScreenHeight(requireContext()) * ratio / 100);
        binding.recycler.setAdapter(new FilterAdapter(listener, filter));
    }

    @Override
    protected boolean transparent() {
        return true;
    }
}
