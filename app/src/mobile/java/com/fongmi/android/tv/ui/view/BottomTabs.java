package com.fongmi.android.tv.ui.view;

import java.util.ArrayList;
import java.util.List;

/**
 * 底栏页签注册表的纯逻辑层（不依赖 Android 类，可独立做逻辑演练）。
 *
 * 页签全集按注册顺序固定，共用格（实验室／小程序）也在注册表里占位，
 * 靠 visible 决定谁上栏；可见页签的顺序永远等于注册顺序，增减格只是
 * 把某项从可见序列里拿掉或放回，不存在挤位、错位。
 */
public final class BottomTabs {

    /** 一个页签的定义：id、图标（选中态图标为 0 时与常态同图靠染色区分）、标题、是否可见。 */
    public static final class Spec {
        public final int id;
        public final int iconRes;
        public final int selectedIconRes;
        public final String title;
        public boolean visible;

        public Spec(int id, int iconRes, int selectedIconRes, String title, boolean visible) {
            this.id = id;
            this.iconRes = iconRes;
            this.selectedIconRes = selectedIconRes;
            this.title = title;
            this.visible = visible;
        }
    }

    private BottomTabs() {
    }

    /** 当前可见页签（保持注册顺序）。 */
    public static List<Spec> visibleTabs(List<Spec> specs) {
        List<Spec> result = new ArrayList<>();
        for (Spec spec : specs) if (spec.visible) result.add(spec);
        return result;
    }

    /** 当前可见页签的 id 序列（保持注册顺序）。 */
    public static List<Integer> visibleIds(List<Spec> specs) {
        List<Integer> result = new ArrayList<>();
        for (Spec spec : specs) if (spec.visible) result.add(spec.id);
        return result;
    }

    public static Spec find(List<Spec> specs, int id) {
        for (Spec spec : specs) if (spec.id == id) return spec;
        return null;
    }

    /** 可选页签（共用格／条件格）是否处于可选中状态：已注册且可见。 */
    public static boolean isSelectable(List<Spec> specs, int id) {
        Spec spec = find(specs, id);
        return spec != null && spec.visible;
    }
}
