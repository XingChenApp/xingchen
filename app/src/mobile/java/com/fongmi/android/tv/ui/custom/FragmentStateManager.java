package com.fongmi.android.tv.ui.custom;

import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.base.BaseFragment;

import java.util.function.IntFunction;

public class FragmentStateManager {

    private final ViewGroup container;
    private final FragmentManager fm;
    private final IntFunction<Fragment> factory;

    public FragmentStateManager(ViewGroup container, FragmentManager fm, IntFunction<Fragment> factory) {
        this.container = container;
        this.factory = factory;
        this.fm = fm;
    }

    public boolean change(int position) {
        String tag = getTag(position);
        Fragment expected = factory.apply(position);
        if (expected == null) return false;
        Fragment fragment = fm.findFragmentByTag(tag);
        // 同页签重复点：已是当前页且可见就不起事务，过渡不重播
        Fragment current0 = fm.getPrimaryNavigationFragment();
        if (fragment != null && fragment == current0 && fragment.isVisible()
                && fragment.getClass() == expected.getClass()) return true;
        // 页签互切过渡：新页淡入＋旧页淡出同一事务同时进行（交叉淡入淡出，
        // xc_tab_fade_*＝250ms＋Material 标准缓动），不用横滑（页签无左右次序、
        // 横滑会跟页内手势打架）。切换只 hide/show，Fragment 与视图状态全保留。
        FragmentTransaction ft = fm.beginTransaction().setCustomAnimations(R.anim.xc_tab_fade_in, R.anim.xc_tab_fade_out);
        if (fragment != null && fragment.getClass() != expected.getClass()) {
            ft.remove(fragment).commitNowAllowingStateLoss();
            fragment = null;
            ft = fm.beginTransaction().setCustomAnimations(R.anim.xc_tab_fade_in, R.anim.xc_tab_fade_out);
        }
        if (fragment == null) {
            fragment = expected;
            ft.add(container.getId(), fragment, tag);
        }
        Fragment current = fm.getPrimaryNavigationFragment();
        if (current != null && current != fragment) ft.hide(current);
        ft.show(fragment).setPrimaryNavigationFragment(fragment).setReorderingAllowed(true).commitNowAllowingStateLoss();
        return true;
    }

    private String getTag(int position) {
        return "android:switcher:" + position;
    }

    public BaseFragment getFragment(int position) {
        return (BaseFragment) fm.findFragmentByTag(getTag(position));
    }

    public boolean isVisible(int position) {
        Fragment fragment = getFragment(position);
        return fragment != null && fragment.isVisible();
    }

    public boolean canBack(int position) {
        BaseFragment fragment = getFragment(position);
        return fragment != null && fragment.canBack();
    }
}
