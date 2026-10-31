package com.fongmi.android.tv.hongguo.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.Player;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.hongguo.drama.DramaEntry;
import com.fongmi.android.tv.hongguo.drama.DramaVideoModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 播放页的小面板（2026-10-30 从零重建；页面层重做按定稿浅色面
 * 整件重落）：更多面板（倍速/清晰度/弹幕开关＋设置/定时关闭，
 * 摘选集行——播放页有整宽选集条）、弹幕设置（四滑杆＋两开关）、
 * 定时关闭选项。发弹幕整件已摘（他定版：弹幕只看不发，发送面与
 * DramaDanmakuSender 一并删除，账 hongguo-1463-triage2 §4.2）。
 */
public final class HgFeedPanels {

    /** 宿主回调面（竖屏页实现；横屏页阶段8 共用同一面）。 */
    public interface Host {
        Player player();

        String vid();

        String seriesId();

        DramaVideoModel currentModel();

        String currentDefinition();

        void changeDefinition(String definition);

        void setSpeed(float speed);

        void openEpisodes();

        /** 定时关闭设档后回调（宿主可据此提示/重排检查）；缺省无动作。 */
        default void onTimerChanged() {
        }

        void openDanmakuSettings();

        void onDanmakuOptionsChanged();
    }

    private HgFeedPanels() {
    }

    private static int dp(AppCompatActivity a, float v) {
        return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static GradientDrawable roundBg(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private static int navBarHeight(AppCompatActivity a) {
        int id = a.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? a.getResources().getDimensionPixelSize(id) : 0;
    }

    /**
     * 定稿面板骨（.sheet）：白底、顶部圆 22、顶部 grab 条，底部给
     * 系统手势区让位。more 面板传 #F4F4F6 灰底＋白卡，其余白底。
     */
    private static LinearLayout sheetRoot(AppCompatActivity a, int bg) {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        float r = dp(a, 22);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        root.setBackground(g);
        root.setPadding(dp(a, 18), 0, dp(a, 18), dp(a, 13) + navBarHeight(a));
        View grab = new View(a);
        grab.setBackground(roundBg(0x291B1B1F, dp(a, 2.5f)));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(a, 38), dp(a, 4));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.topMargin = dp(a, 9);
        glp.bottomMargin = dp(a, 2);
        root.addView(grab, glp);
        return root;
    }

    private static TextView sheetHead(AppCompatActivity a, String text) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextColor(0xFF1B1B1F);
        t.setTextSize(16);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(a, 10), 0, dp(a, 8));
        return t;
    }

    /** 定稿 .card：白卡圆 18、内 padding 8/16/12。 */
    private static LinearLayout card(AppCompatActivity a) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(roundBg(Color.WHITE, dp(a, 18)));
        c.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 12));
        return c;
    }

    private interface IntPick {
        void pick(int idx);
    }

    /**
     * 定稿 .seg 分段：槽底 rgba(27,27,31,.055) 圆 12 padding 4，格
     * 字 #6D6D75，选中白底圆 9＋ink 字重＋微影。
     */
    private static LinearLayout segRow(AppCompatActivity a, String[] labels, int selected,
                                       float textSize, IntPick onPick) {
        LinearLayout seg = new LinearLayout(a);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(roundBg(0x0E1B1B1F, dp(a, 12)));
        seg.setPadding(dp(a, 4), dp(a, 4), dp(a, 4), dp(a, 4));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            TextView c = new TextView(a);
            c.setText(labels[i]);
            c.setTextSize(textSize);
            c.setGravity(Gravity.CENTER);
            c.setPadding(0, dp(a, textSize > 13 ? 8 : 7), 0, dp(a, textSize > 13 ? 8 : 7));
            c.setTextColor(on ? 0xFF1B1B1F : 0xFF6D6D75);
            if (on) {
                c.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                c.setBackground(roundBg(Color.WHITE, dp(a, 9)));
                c.setElevation(dp(a, 2));
            }
            c.setOnClickListener(v -> onPick.pick(idx));
            seg.addView(c, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        return seg;
    }

    private static View divider(AppCompatActivity a) {
        View d = new View(a);
        d.setBackgroundColor(0x121B1B1F);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 1);
        lp.topMargin = dp(a, 6);
        lp.bottomMargin = dp(a, 6);
        d.setLayoutParams(lp);
        return d;
    }

    /** 面板行签（定稿 .m-label/.mrow）：15.5/900 或 15/500。 */
    private static TextView rowLabel(AppCompatActivity a, String text, boolean head) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextColor(0xFF1B1B1F);
        t.setTextSize(head ? 15.5f : 15);
        if (head) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private static android.widget.ImageView chevron(AppCompatActivity a) {
        android.widget.ImageView iv = new android.widget.ImageView(a);
        iv.setImageResource(R.drawable.ic_drama_chev);
        iv.setColorFilter(0xFFC9C9CF);
        iv.setRotation(180);
        return iv;
    }

    // ---------- 更多面板 ----------

    public static void showMore(AppCompatActivity a, Host host) {
        // 定稿 .more-sheet：#F4F4F6 底＋白卡两张。卡一＝倍速 seg＋
        // 清晰度 seg＋弹幕开关行＋弹幕设置行；卡二＝定时关闭行。
        // （投屏行已按他亲口决定整件摘除，见 hongguo-cast 账。）
        BottomSheetDialog dialog = new BottomSheetDialog(a);
        LinearLayout root = sheetRoot(a, 0xFFF4F4F6);
        root.setPadding(dp(a, 14), 0, dp(a, 14), dp(a, 14) + navBarHeight(a));

        addMainCard(a, root, host, dialog);

        // 卡二：定时关闭（状态文案在右）
        LinearLayout card2 = card(a);
        LinearLayout.LayoutParams c2lp = new LinearLayout.LayoutParams(-1, -2);
        c2lp.topMargin = dp(a, 12);
        root.addView(card2, c2lp);
        LinearLayout timerRow = new LinearLayout(a);
        timerRow.setOrientation(LinearLayout.HORIZONTAL);
        timerRow.setGravity(Gravity.CENTER_VERTICAL);
        timerRow.setPadding(0, dp(a, 14), 0, dp(a, 14));
        timerRow.addView(rowLabel(a, "定时关闭", false),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView timerState = new TextView(a);
        timerState.setText(HgPanelLogic.timerStateText(
                HgStore.getTimerMode(), HgStore.getTimerMinutes()));
        timerState.setTextColor(0xFF8A8A93);
        timerState.setTextSize(13.5f);
        timerRow.addView(timerState);
        android.widget.ImageView timerChev = chevron(a);
        LinearLayout.LayoutParams tclp = new LinearLayout.LayoutParams(dp(a, 13), dp(a, 13));
        tclp.leftMargin = dp(a, 6);
        timerRow.addView(timerChev, tclp);
        timerRow.setOnClickListener(v -> {
            dialog.dismiss();
            showTimerOptions(a, host);
        });
        card2.addView(timerRow);

        dialog.setContentView(root);
        dialog.show();
        clearSheetBg(dialog);
    }


    /**
     * 更多面板卡一（横竖共用）：倍速 seg＋清晰度 seg＋弹幕开关行
     * ＋弹幕设置行。showMore（竖屏）与 showMoreLand（横屏）同件，
     * 对应官方横竖同源的 ShortSeriesMorePanelDialog。唯一分叉在
     * 倍速段的档表：官方横竖是两张表（竖屏实拍升序表、横屏
     * xr3.f 定版倒序表 LAND_SPEEDS），横屏走 addMainCardLand。
     */
    private static void addMainCard(AppCompatActivity a, LinearLayout root,
                                    Host host, BottomSheetDialog dialog) {
        addMainCardImpl(a, root, host, dialog, false);
    }

    private static void addMainCardLand(AppCompatActivity a, LinearLayout root,
                                        Host host, BottomSheetDialog dialog) {
        addMainCardImpl(a, root, host, dialog, true);
    }

    private static void addMainCardImpl(AppCompatActivity a, LinearLayout root,
                                        Host host, BottomSheetDialog dialog,
                                        boolean land) {
        LinearLayout card1 = card(a);
        LinearLayout.LayoutParams c1lp = new LinearLayout.LayoutParams(-1, -2);
        c1lp.topMargin = dp(a, 6);
        root.addView(card1, c1lp);

        // 倍速（定稿行序在前）：竖屏六段升序（0.75/1/1.25/1.5/2/3，
        // 默认 1x）；横屏走官方 LAND_SPEEDS（2/1.75/1.5/1.25/1/
        // 0.75，C-04——此前横屏借竖屏表，1.75 无处可选、3x 冒充
        // 官方档）。两张表互有表外值（横屏存的 1.75 在竖屏表外、
        // 竖屏存的 3x 在横屏表外）：横竖回显口径收齐（C-04＋
        // C-09）——当前速度不在本面表内时高亮就近档，不许静默
        // 回落 1x 误显（此前竖屏侧恒回落下标 1＝1x，横屏选过
        // 1.75 后竖屏面板会把 1.75 显示成 1x）；落值仍以点选为准。
        TextView speedHead = rowLabel(a, "倍速", true);
        speedHead.setPadding(0, dp(a, 12), 0, dp(a, 11));
        card1.addView(speedHead);
        final float[] speedTable = land ? HgPanelLogic.LAND_SPEEDS : HgPanelLogic.SPEEDS;
        float curSpeed = HgStore.getSpeed();
        int curSpeedIdx = -1;
        String[] speedLabels = new String[speedTable.length];
        for (int i = 0; i < speedLabels.length; i++) {
            speedLabels[i] = speedLabel(speedTable[i]);
            if (speedTable[i] == curSpeed) curSpeedIdx = i;
        }
        if (curSpeedIdx < 0) {
            float best = Float.MAX_VALUE;
            for (int i = 0; i < speedTable.length; i++) {
                float d = Math.abs(speedTable[i] - curSpeed);
                if (d < best) {
                    best = d;
                    curSpeedIdx = i;
                }
            }
        }
        card1.addView(segRow(a, speedLabels, curSpeedIdx, 13.5f, idx -> {
            host.setSpeed(speedTable[idx]);
            dialog.dismiss();
        }));

        // 清晰度（当前模型实有档，官方序高→低；缺省取最高档≈1080P 优先）
        DramaVideoModel model = host.currentModel();
        if (model != null && !model.getEntries().isEmpty()) {
            Set<String> defs = new LinkedHashSet<>();
            for (DramaEntry e : model.getEntries()) {
                if (e.getDefinition() != null && e.isPlayableCodec()) defs.add(e.getDefinition());
            }
            List<String> ordered = HgPanelLogic.orderedDefinitions(defs);
            if (!ordered.isEmpty()) {
                card1.addView(divider(a));
                LinearLayout defHead = new LinearLayout(a);
                defHead.setOrientation(LinearLayout.HORIZONTAL);
                defHead.setGravity(Gravity.CENTER_VERTICAL);
                defHead.setPadding(0, dp(a, 12), 0, dp(a, 11));
                TextView hd = new TextView(a);
                hd.setText("HD");
                hd.setTextColor(0xFF1B1B1F);
                hd.setTextSize(11);
                hd.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                hd.setGravity(Gravity.CENTER);
                GradientDrawable hdBg = new GradientDrawable();
                hdBg.setColor(Color.TRANSPARENT);
                hdBg.setStroke(dp(a, 1.6f), 0xFF1B1B1F);
                hdBg.setCornerRadius(dp(a, 6));
                hd.setBackground(hdBg);
                hd.setPadding(dp(a, 5), dp(a, 2), dp(a, 5), dp(a, 2));
                defHead.addView(hd);
                TextView defLabel = rowLabel(a, "清晰度", true);
                LinearLayout.LayoutParams dllp = new LinearLayout.LayoutParams(-2, -2);
                dllp.leftMargin = dp(a, 8);
                defHead.addView(defLabel, dllp);
                card1.addView(defHead);
                String cur = host.currentDefinition();
                String[] defLabels = new String[ordered.size()];
                int curDefIdx = -1;
                for (int i = 0; i < ordered.size(); i++) {
                    defLabels[i] = defLabel(ordered.get(i));
                    if (ordered.get(i).equals(cur)) curDefIdx = i;
                }
                card1.addView(segRow(a, defLabels, curDefIdx, 12f, idx -> {
                    dialog.dismiss();
                    host.changeDefinition(ordered.get(idx));
                }));
            }
        }

        // 弹幕开关行（定稿 .mrow：弹徽＋签＋自绘开关）
        card1.addView(divider(a));
        LinearLayout dmRow = new LinearLayout(a);
        dmRow.setOrientation(LinearLayout.HORIZONTAL);
        dmRow.setGravity(Gravity.CENTER_VERTICAL);
        dmRow.setPadding(0, dp(a, 14), 0, dp(a, 14));
        TextView dmBadge = new TextView(a);
        dmBadge.setText("弹");
        dmBadge.setTextColor(0xFF1B1B1F);
        dmBadge.setTextSize(12.5f);
        dmBadge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        dmBadge.setGravity(Gravity.CENTER);
        GradientDrawable dmBg = new GradientDrawable();
        dmBg.setColor(Color.TRANSPARENT);
        dmBg.setStroke(dp(a, 1.6f), 0xFF1B1B1F);
        dmBg.setCornerRadius(dp(a, 7));
        dmBadge.setBackground(dmBg);
        dmRow.addView(dmBadge, new LinearLayout.LayoutParams(dp(a, 25), dp(a, 25)));
        TextView dmLabel = rowLabel(a, "弹幕", false);
        LinearLayout.LayoutParams dmlp = new LinearLayout.LayoutParams(0, -2, 1f);
        dmlp.leftMargin = dp(a, 10);
        dmRow.addView(dmLabel, dmlp);
        HgSwitch dmSwitch = new HgSwitch(a);
        dmSwitch.setChecked(HgStore.isDanmakuOn(), false);
        dmSwitch.setOnToggleListener(on -> {
            HgStore.setDanmakuOn(on);
            host.onDanmakuOptionsChanged();
        });
        dmRow.addView(dmSwitch, new LinearLayout.LayoutParams(dp(a, 46), dp(a, 27)));
        card1.addView(dmRow);

        // 弹幕设置行
        card1.addView(divider(a));
        LinearLayout dmsRow = new LinearLayout(a);
        dmsRow.setOrientation(LinearLayout.HORIZONTAL);
        dmsRow.setGravity(Gravity.CENTER_VERTICAL);
        dmsRow.setPadding(0, dp(a, 14), 0, dp(a, 14));
        dmsRow.addView(rowLabel(a, "弹幕设置", false),
                new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.ImageView dmsChev = chevron(a);
        dmsRow.addView(dmsChev, new LinearLayout.LayoutParams(dp(a, 13), dp(a, 13)));
        dmsRow.setOnClickListener(v -> {
            dialog.dismiss();
            host.openDanmakuSettings();
        });
        card1.addView(dmsRow);
    }


    /**
     * 横屏更多面板（#22，他 2026-10-31 14:53 亲令）：只挂卡一
     * （倍速/清晰度/弹幕开关＋弹幕设置行）——官方更多面板横竖
     * 同源（ShortSeriesMorePanelDialog、动作项含 danmaku_options），
     * 弹幕设置是本单的主件且全项已接（开关/四滑杆/两屏蔽开关走
     * 同一套 HgStore 档表，横屏 onDanmakuOptionsChanged 重算配置）。
     * 卡二定时关闭不带：其执行口（完播守卫与计时 tick）在竖屏页，
     * 横屏页内无执行口——带行不执行是半截，是否给横屏补执行口
     * 待裁（账 hongguo-ep-switch-smooth-20261031）。
     */
    public static void showMoreLand(AppCompatActivity a, Host host) {
        BottomSheetDialog dialog = new BottomSheetDialog(a);
        LinearLayout root = sheetRoot(a, 0xFFF4F4F6);
        root.setPadding(dp(a, 14), 0, dp(a, 14), dp(a, 14) + navBarHeight(a));
        addMainCardLand(a, root, host, dialog);
        dialog.setContentView(root);
        dialog.show();
        clearSheetBg(dialog);
    }

    // ---------- 定时关闭选项 ----------

    public static void showTimerOptions(AppCompatActivity a, Host host) {
        BottomSheetDialog dialog = new BottomSheetDialog(a);
        LinearLayout root = sheetRoot(a, Color.WHITE);
        root.addView(sheetHead(a, "定时关闭"));

        int cur = HgStore.getTimerMode();
        String[] labels = {"关闭", "播完本剧", HgPanelLogic.FIXED_MINUTES + "分钟", "自定义"};
        int[] modes = {HgPanelLogic.TIMER_OFF, HgPanelLogic.TIMER_SERIES_END,
                HgPanelLogic.TIMER_FIXED, HgPanelLogic.TIMER_CUSTOM};
        int curIdx = 0;
        for (int i = 0; i < modes.length; i++) if (modes[i] == cur) curIdx = i;
        LinearLayout.LayoutParams sglp = new LinearLayout.LayoutParams(-1, -2);
        sglp.topMargin = dp(a, 6);
        root.addView(segRow(a, labels, curIdx, 13.5f, idx -> {
            int m = modes[idx];
            if (m == HgPanelLogic.TIMER_CUSTOM) {
                dialog.dismiss();
                showTimerCustom(a, host);
                return;
            }
            HgStore.setTimer(m, HgStore.getTimerMinutes());
            host.onTimerChanged();
            dialog.dismiss();
        }), sglp);

        dialog.setContentView(root);
        dialog.show();
        clearSheetBg(dialog);
    }

    private static void showTimerCustom(AppCompatActivity a, Host host) {
        EditText input = new EditText(a);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("分钟数");
        input.setTextColor(0xFF1B1B1F);
        input.setHintTextColor(0xFF8A8A93);
        int cur = HgStore.getTimerMinutes();
        if (cur > 0) input.setText(String.valueOf(cur));
        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("自定义定时（分钟）")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> {
                    int minutes = 0;
                    try {
                        minutes = Integer.parseInt(
                                input.getText() == null ? "" : input.getText().toString().trim());
                    } catch (Exception ignored) {
                    }
                    if (minutes > 0) {
                        HgStore.setTimer(HgPanelLogic.TIMER_CUSTOM, minutes);
                        host.onTimerChanged();
                    }
                })
                .show();
    }

    private static String defLabel(String def) {
        return HgPanelLogic.definitionLabel(def);
    }

    private static String speedLabel(float s) {
        return HgPanelLogic.speedLabel(s);
    }

    // ---------- 弹幕设置 ----------

    public static void showDanmakuSettings(AppCompatActivity a, Host host) {
        BottomSheetDialog dialog = new BottomSheetDialog(a);
        LinearLayout root = sheetRoot(a, Color.WHITE);
        root.addView(sheetHead(a, "弹幕设置"));

        addSlider(a, root, "不透明度", HgStore.getDanmakuOpacity(), 100, v -> {
            HgStore.setDanmakuOpacity(v);
            host.onDanmakuOptionsChanged();
        });
        addSlider(a, root, "移动速度", HgStore.getDanmakuSpeedIndex(),
                HgPanelLogic.DM_SPEED_LABELS.length - 1, v -> {
                    HgStore.setDanmakuSpeedIndex(v);
                    host.onDanmakuOptionsChanged();
                });
        addSlider(a, root, "显示区域", HgStore.getDanmakuLines() - HgPanelLogic.DM_LINES_MIN,
                HgPanelLogic.DM_LINES_MAX - HgPanelLogic.DM_LINES_MIN, v -> {
                    HgStore.setDanmakuLines(v + HgPanelLogic.DM_LINES_MIN);
                    host.onDanmakuOptionsChanged();
                });
        addSlider(a, root, "字体大小", HgStore.getDanmakuFontIndex(),
                HgPanelLogic.DM_FONT_LABELS.length - 1, v -> {
                    HgStore.setDanmakuFontIndex(v);
                    host.onDanmakuOptionsChanged();
                });
        addSwitchRow(a, root, "屏蔽彩色弹幕", HgStore.getDanmakuBlockColorful(), on -> {
            HgStore.setDanmakuBlockColorful(on);
            host.onDanmakuOptionsChanged();
        });
        addSwitchRow(a, root, "清屏时显示弹幕", HgStore.getDanmakuShowInClean(), on -> {
            HgStore.setDanmakuShowInClean(on);
            host.onDanmakuOptionsChanged();
        });
        TextView reset = new TextView(a);
        reset.setText("恢复默认");
        reset.setTextColor(0xFFE63A2E);
        reset.setTextSize(14);
        reset.setPadding(0, dp(a, 16), 0, dp(a, 2));
        reset.setOnClickListener(v -> {
            HgStore.resetDanmakuOptions();
            host.onDanmakuOptionsChanged();
            dialog.dismiss();
            showDanmakuSettings(a, host);
        });
        root.addView(reset);

        dialog.setContentView(root);
        dialog.show();
        clearSheetBg(dialog);
    }

    private interface IntSetter {
        void set(int v);
    }

    private interface BoolSetter2 {
        void set(boolean on);
    }

    private static void addSlider(AppCompatActivity a, LinearLayout root, String label,
                                  int value, int max, IntSetter setter) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = dp(a, 12);
        TextView t = new TextView(a);
        t.setText(label);
        t.setTextColor(0xFF1B1B1F);
        t.setTextSize(14);
        row.addView(t, new LinearLayout.LayoutParams(dp(a, 76), -2));
        SeekBar bar = new SeekBar(a);
        bar.setMax(Math.max(1, max));
        bar.setProgress(Math.min(value, Math.max(1, max)));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFE63A2E));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFE63A2E));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) setter.set(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        row.addView(bar, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(row, rlp);
    }

    private static void addSwitchRow(AppCompatActivity a, LinearLayout root, String label,
                                     boolean initial, BoolSetter2 setter) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = dp(a, 12);
        TextView t = new TextView(a);
        t.setText(label);
        t.setTextColor(0xFF1B1B1F);
        t.setTextSize(14);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        HgSwitch sw = new HgSwitch(a);
        sw.setChecked(initial, false);
        sw.setOnToggleListener(setter::set);
        row.addView(sw, new LinearLayout.LayoutParams(dp(a, 46), dp(a, 27)));
        root.addView(row, rlp);
    }


    private static void clearSheetBg(BottomSheetDialog dialog) {
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) sheet.setBackgroundColor(Color.TRANSPARENT);
    }
}
