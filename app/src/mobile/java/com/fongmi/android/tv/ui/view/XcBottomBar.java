package com.fongmi.android.tv.ui.view;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 星辰自家底栏（替代 Material BottomNavigationView）。
 *
 * 换里子的原因：Material 菜单写死最多 6 项（MAX_BOTTOM_TABS 时代加第 7 项必崩，
 * 1.3.53 实锤），实验室与小程序被迫共用一格抢位；自家实现没有项数上限，
 * 页签由注册表（{@link BottomTabs.Spec}）驱动，可见序列恒等于注册顺序，
 * 增减格靠等宽权重均分，不挤压不变形。
 *
 * 形态（他 2026-10-27 20:06 发图改定）：图标在上、文字标签在下，选中格
 * 图标与字一起变高亮色；其余外观（悬浮胶囊背景、边距、高度基准、位置）
 * 与原 Material 底栏一致，由布局文件与 dimens 提供，本类不另定数值。
 *
 * 接口刻意对齐原 BottomNavigationView 在 HomeActivity 里的用法
 * （setOnItemSelectedListener / setSelectedItemId / getSelectedItemId），
 * 让宿主改动最小：程序化选中同样走监听器，监听器返回 false 不落选；
 * 点已选中格不重复回调（与原组件无 reselected 监听时的行为一致）。
 */
public class XcBottomBar extends LinearLayout {

    /** 选中回调：返回 true 才落选（与原 NavigationBarView.OnItemSelectedListener 同语义）。 */
    public interface OnItemSelectedListener {
        boolean onItemSelected(int itemId);
    }

    /** 标签策略：全部带字（当前定案）。 */
    public static final int LABEL_MODE_ALL = 0;
    /** 标签策略：只给选中格带字（策略层预留，切换不需改结构）。 */
    public static final int LABEL_MODE_SELECTED_ONLY = 1;

    private static final long SELECT_ANIM_MS = 150;

    private final List<BottomTabs.Spec> specs = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();
    private final ArgbEvaluator evaluator = new ArgbEvaluator();
    private final int normalColor;
    private final int selectedColor;
    private final int iconSizePx;
    private final int labelGapPx;

    private OnItemSelectedListener listener;
    private int selectedId = View.NO_ID;
    private int labelMode = LABEL_MODE_ALL;
    private ValueAnimator colorAnimator;

    private static final class Item {
        BottomTabs.Spec spec;
        LinearLayout root;
        ImageView icon;
        TextView label;
        float fraction; // 0=未选中色，1=选中色，切换动画中间值
        boolean filledIcon; // 双态图标当前是否已切到选中图
    }

    public XcBottomBar(Context context) {
        this(context, null);
    }

    public XcBottomBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public XcBottomBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        // 未选色＋选中色同源：都取自底栏现成的 selector_nav（旧 Material 底栏的图标色表，
        // 未选 #171B23／选中 #F0A400）。不再从主题 colorControlNormal 取未选色——该 attr
        // 在当前主题上解析出的值让未选格图标与文字看不见（1.4.52 真机实证：只剩选中格可见）。
        ColorStateList navColors = ContextCompat.getColorStateList(context, R.color.selector_nav);
        normalColor = navColors.getColorForState(new int[]{-android.R.attr.state_checked}, navColors.getDefaultColor());
        selectedColor = navColors.getColorForState(new int[]{android.R.attr.state_checked}, navColors.getDefaultColor());
        iconSizePx = getResources().getDimensionPixelSize(R.dimen.xc_bottombar_icon_size);
        labelGapPx = getResources().getDimensionPixelSize(R.dimen.xc_bottombar_label_gap);
    }

    /** 注册全部页签（只在初始化时调一次；之后的显隐走 setTabVisible，不重建，避免切页闪跳）。 */
    public void setTabs(List<BottomTabs.Spec> tabs) {
        specs.clear();
        specs.addAll(tabs);
        items.clear();
        removeAllViews();
        for (BottomTabs.Spec spec : specs) items.add(buildItem(spec));
        for (Item item : items) applyVisibility(item);
        refreshSelectionVisuals(false);
    }

    private Item buildItem(BottomTabs.Spec spec) {
        Item item = new Item();
        item.spec = spec;
        LinearLayout root = new LinearLayout(getContext());
        root.setId(spec.id);
        root.setOrientation(VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setLayoutParams(new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        root.setClickable(true);
        root.setFocusable(true);
        root.setContentDescription(spec.title);
        TypedValue ripple = new TypedValue();
        if (getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true) && ripple.resourceId != 0) {
            root.setBackground(ContextCompat.getDrawable(getContext(), ripple.resourceId));
        }
        ImageView icon = new ImageView(getContext());
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSizePx, iconSizePx));
        icon.setImageResource(spec.iconRes);
        root.addView(icon);
        TextView label = new TextView(getContext());
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = labelGapPx;
        label.setLayoutParams(labelParams);
        label.setText(spec.title);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.xc_bottombar_label_size));
        label.setSingleLine(true);
        label.setIncludeFontPadding(false);
        label.setGravity(Gravity.CENTER);
        root.addView(label);
        item.root = root;
        item.icon = icon;
        item.label = label;
        root.setOnClickListener(view -> onItemClicked(spec.id));
        addView(root);
        return item;
    }

    private void applyVisibility(Item item) {
        item.root.setVisibility(item.spec.visible ? View.VISIBLE : View.GONE);
    }

    public void setTabVisible(int itemId, boolean visible) {
        BottomTabs.Spec spec = BottomTabs.find(specs, itemId);
        if (spec == null || spec.visible == visible) return;
        spec.visible = visible;
        for (Item item : items) if (item.spec == spec) applyVisibility(item);
    }

    public boolean isTabVisible(int itemId) {
        BottomTabs.Spec spec = BottomTabs.find(specs, itemId);
        return spec != null && spec.visible;
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.listener = listener;
    }

    public int getSelectedItemId() {
        return selectedId;
    }

    /** 程序化选中：与原组件同语义——走监听器，监听器不放行就不落选；已选中同一格不重复触发。 */
    public void setSelectedItemId(int itemId) {
        if (itemId == selectedId) return;
        if (!BottomTabs.isSelectable(specs, itemId)) return;
        if (listener == null || listener.onItemSelected(itemId)) applySelection(itemId, true);
    }

    private void onItemClicked(int itemId) {
        if (itemId == selectedId) return; // 点已选中格：原组件无 reselected 监听时同样无动作
        if (!BottomTabs.isSelectable(specs, itemId)) return;
        if (listener == null || listener.onItemSelected(itemId)) applySelection(itemId, true);
    }

    private void applySelection(int itemId, boolean animate) {
        Item oldItem = findItem(selectedId);
        Item newItem = findItem(itemId);
        selectedId = itemId;
        if (colorAnimator != null) colorAnimator.cancel();
        // 动画中途改选：其余格先各自落定，避免残留中间色
        for (Item item : items) {
            if (item != oldItem && item != newItem) {
                item.fraction = item.spec.id == itemId ? 1f : 0f;
                applyItemVisual(item);
            }
        }
        if (newItem != null) applyLabelMode(newItem);
        if (oldItem != null && oldItem != newItem) applyLabelMode(oldItem);
        if (!animate || oldItem == null || newItem == null || !isLaidOut()) {
            if (oldItem != null) {
                oldItem.fraction = 0f;
                applyItemVisual(oldItem);
            }
            if (newItem != null) {
                newItem.fraction = 1f;
                applyItemVisual(newItem);
            }
            return;
        }
        // 选中色 150ms 交叉淡入（与原组件选中反馈同档的短动画），不做硬变色跳变
        Item from = oldItem;
        Item to = newItem;
        colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.setDuration(SELECT_ANIM_MS);
        colorAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            from.fraction = 1f - progress;
            to.fraction = progress;
            applyItemVisual(from);
            applyItemVisual(to);
        });
        colorAnimator.start();
    }

    private void refreshSelectionVisuals(boolean animate) {
        for (Item item : items) {
            item.fraction = item.spec.id == selectedId ? 1f : 0f;
            applyLabelMode(item);
            applyItemVisual(item);
        }
    }

    private void applyLabelMode(Item item) {
        boolean show = labelMode == LABEL_MODE_ALL || item.spec.id == selectedId;
        item.label.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void applyItemVisual(Item item) {
        int color = (int) evaluator.evaluate(item.fraction, normalColor, selectedColor);
        if (item.spec.selectedIconRes != 0) {
            boolean filled = item.fraction >= 0.5f;
            if (filled != item.filledIcon) {
                item.filledIcon = filled;
                item.icon.setImageResource(filled ? item.spec.selectedIconRes : item.spec.iconRes);
            }
        }
        item.icon.setImageTintList(ColorStateList.valueOf(color));
        item.label.setTextColor(color);
    }

    private Item findItem(int itemId) {
        for (Item item : items) if (item.spec.id == itemId) return item;
        return null;
    }

    /** 标签策略切换（全部带字／只选中带字），默认全部带字。 */
    public void setLabelMode(int mode) {
        if (labelMode == mode) return;
        labelMode = mode;
        for (Item item : items) applyLabelMode(item);
    }

    public int getLabelMode() {
        return labelMode;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (colorAnimator != null) colorAnimator.cancel();
        super.onDetachedFromWindow();
    }
}
