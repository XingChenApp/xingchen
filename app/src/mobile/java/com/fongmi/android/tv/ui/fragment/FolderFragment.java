package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.databinding.FragmentFolderBinding;
import com.fongmi.android.tv.ui.base.BaseFragment;

import java.util.HashMap;
import java.util.Optional;

public class FolderFragment extends BaseFragment {

    private Class mType;

    public static FolderFragment newInstance(String key, Class type, int y) {
        Bundle args = new Bundle();
        args.putInt("y", y);
        args.putString("key", key);
        args.putParcelable("type", type);
        FolderFragment fragment = new FolderFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKey() {
        return getArguments().getString("key");
    }

    public Class getType() {
        return getArguments().getParcelable("type");
    }

    private int getY() {
        return getArguments().getInt("y");
    }

    private VodFragment getParent() {
        return (VodFragment) getParentFragment();
    }

    private TypeFragment getChild() {
        return (TypeFragment) getChildFragmentManager().findFragmentById(R.id.container);
    }

    public void checkCoverChanged() {
        try {
            TypeFragment child = getChild();
            if (child != null) child.checkCoverChanged();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return FragmentFolderBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mType = getType();
        getChildFragmentManager().beginTransaction().replace(R.id.container, TypeFragment.newInstance(getKey(), mType.getTypeId(), mType.getStyle(), getExtend(), mType.isFolder(), getY())).commit();
    }

    private HashMap<String, String> getExtend() {
        HashMap<String, String> extend = new HashMap<>();
        for (Filter filter : mType.getFilters()) if (filter.getInit() != null) extend.put(filter.getKey(), filter.setSelected(filter.getInit()));
        return extend;
    }

    public void openFolder(String typeId, HashMap<String, String> extend) {
        TypeFragment next = TypeFragment.newInstance(getKey(), typeId, mType.getStyle(), extend, mType.isFolder(), getY());
        FragmentTransaction ft = getChildFragmentManager().beginTransaction().setCustomAnimations(R.anim.xc_frag_fade_in, R.anim.xc_frag_fade_out, R.anim.xc_frag_fade_in, R.anim.xc_frag_fade_out);
        Optional.ofNullable(getChild()).ifPresent(ft::hide);
        ft.add(R.id.container, next);
        ft.addToBackStack(null);
        ft.commit();
    }

    public Result getResult() {
        return getParentFragment() instanceof VodFragment parent ? parent.getResult() : null;
    }

    public void onRefresh() {
        Optional.ofNullable(getChild()).ifPresent(TypeFragment::onRefresh);
    }

    /** 「首页」页签刷新回到列表页那一发取数，页签自己不另拉首页。
     *  本碎片还被 FolderActivity 独立托管（父级不是 VodFragment），那条路没有
     *  「首页」页签、走不到这里；守卫只是不给那条路新增崩点。 */
    public void refreshHome() {
        if (getParentFragment() instanceof VodFragment) getParent().reloadHome();
    }

    public void scrollToTop() {
        Optional.ofNullable(getChild()).ifPresent(TypeFragment::scrollToTop);
    }

    public void setFilter(String key, Value value) {
        Optional.ofNullable(getChild()).ifPresent(f -> f.setFilter(key, value));
    }

    public boolean canBack() {
        return getChildFragmentManager().getBackStackEntryCount() > 0;
    }

    public void goBack() {
        getChildFragmentManager().popBackStack();
    }
}
