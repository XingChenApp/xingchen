package com.fongmi.android.tv.ui.lab;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fongmi.android.tv.R;

/**
 * 实验室深底弹窗工具箱（照设计稿 a2-dialogs 五张逐值实现）：
 * 遮罩 rgba(0,0,0,.40)；面板 rgba(26,23,20,.97)、圆角 26dp、内边距 32/28；
 * 标题 22sp #f7f4ee；选项独立卡 rgba(255,255,255,.08)、圆角 16dp；
 * 报错字 #ffa49a；黄钮 #F2B33D＋#171310 字。
 * 实验室所有弹窗（导入三选一/安装二选一/安装中/报错/确认/运行中列表）一律走这里，
 * 不许再用系统白底 AlertDialog。
 */
public final class LabInstallDialog {

    private LabInstallDialog() {
    }

    private static int dp(Activity a, int v) {
        return LabSkin.dp(a, v);
    }

    /** 基础深底 Dialog：透明窗＋居中面板 */
    private static Dialog base(Activity a) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = d.getWindow();
        if (w != null) {
            // 进出统一口径：淡入淡出 200ms（与 BaseAlertDialog 基类同一套 HkDialogFade）
            w.setWindowAnimations(R.style.HkDialogFade);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.40f);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.CENTER;
            w.setAttributes(lp);
        }
        d.setCanceledOnTouchOutside(true);
        return d;
    }

    /** 面板本体 */
    private static LinearLayout panel(Activity a) {
        LinearLayout p = new LinearLayout(a);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(LabSkin.dialogPanel(a));
        p.setPadding(dp(a, 32), dp(a, 32), dp(a, 32), dp(a, 28));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = dp(a, 32);
        lp.setMargins(m, 0, m, 0);
        p.setLayoutParams(lp);
        return p;
    }

    private static TextView titleTv(Activity a, String text) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(22);
        t.getPaint().setFakeBoldText(true);
        t.setTextColor(LabSkin.TEXT_ON_DARK);
        return t;
    }

    /** 选项卡：独立一张，有间距 */
    private static TextView optionTv(Activity a, String text) {
        TextView o = new TextView(a);
        o.setText(text);
        o.setTextSize(20);
        o.getPaint().setFakeBoldText(true);
        o.setTextColor(LabSkin.TEXT_ON_DARK);
        o.setBackground(LabSkin.dialogOption(a));
        o.setPadding(dp(a, 24), dp(a, 21), dp(a, 24), dp(a, 21));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 15);
        o.setLayoutParams(lp);
        o.setClickable(true);
        o.setFocusable(true);
        return o;
    }

    private static TextView yellowBtn(Activity a, String text) {
        TextView b = new TextView(a);
        b.setText(text);
        b.setTextSize(19);
        b.getPaint().setFakeBoldText(true);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(LabSkin.ON_YELLOW);
        b.setBackground(LabSkin.buttonYellow(a));
        b.setPadding(0, dp(a, 15), 0, dp(a, 15));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private static TextView neutralBtn(Activity a, String text) {
        TextView b = new TextView(a);
        b.setText(text);
        b.setTextSize(19);
        b.getPaint().setFakeBoldText(true);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(LabSkin.TEXT_ON_DARK);
        b.setBackground(LabSkin.dialogNeutral(a));
        b.setPadding(0, dp(a, 15), 0, dp(a, 15));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private static void showSafe(Activity a, Dialog d) {
        if (a == null || a.isFinishing() || a.isDestroyed()) return;
        try {
            d.show();
        } catch (Exception ignored) {
        }
    }

    // ---------------- 三选一 / 二选一 ----------------

    public interface PickCallback {
        void onPick(int which);
    }

    /** 导入三选一 / 安装二选一：深底＋选项横排（潇洒哥定：竖排布局违和，一律横着） */
    public static void showChoice(Activity a, String title, String[] options, PickCallback cb) {
        Dialog d = base(a);
        LinearLayout p = panel(a);
        p.addView(titleTv(a, title));
        if (options.length > 3) {
            // 选项太多横排挤到看不清（如变量取值），仍走竖排
            for (int i = 0; i < options.length; i++) {
                final int which = i;
                TextView o = optionTv(a, options[i]);
                o.setOnClickListener(v -> {
                    d.dismiss();
                    cb.onPick(which);
                });
                p.addView(o);
            }
            d.setContentView(wrap(a, p));
            showSafe(a, d);
            return;
        }
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(a, 18);
        row.setLayoutParams(rlp);
        for (int i = 0; i < options.length; i++) {
            final int which = i;
            TextView o = optionTv(a, options[i]);
            o.setGravity(Gravity.CENTER);
            o.setPadding(dp(a, 8), dp(a, 21), dp(a, 8), dp(a, 21));
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) olp.leftMargin = dp(a, 12);
            o.setLayoutParams(olp);
            o.setOnClickListener(v -> {
                d.dismiss();
                cb.onPick(which);
            });
            row.addView(o);
        }
        p.addView(row);
        d.setContentView(wrap(a, p));
        showSafe(a, d);
    }

    // ---------------- 确认 ----------------

    /** 卸载确认等：标题＋人话说明＋黄钮确认/中性取消 */
    public static void showConfirm(Activity a, String title, String message, String okText, Runnable onOk) {
        Dialog d = base(a);
        LinearLayout p = panel(a);
        p.addView(titleTv(a, title));
        TextView m = new TextView(a);
        m.setText(message);
        m.setTextSize(17);
        m.setTextColor(LabSkin.TEXT_ON_DARK_DIM);
        m.setLineSpacing(0, 1.5f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(a, 18);
        m.setLayoutParams(mlp);
        p.addView(m);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(a, 24);
        row.setLayoutParams(rlp);
        TextView ok = yellowBtn(a, okText);
        ok.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ok.setOnClickListener(v -> {
            d.dismiss();
            onOk.run();
        });
        TextView cancel = neutralBtn(a, "算了");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        clp.leftMargin = dp(a, 12);
        cancel.setLayoutParams(clp);
        cancel.setOnClickListener(v -> d.dismiss());
        row.addView(ok);
        row.addView(cancel);
        p.addView(row);
        d.setContentView(wrap(a, p));
        showSafe(a, d);
    }

    // ---------------- 自定义内容（二维码等） ----------------

    /** 标题＋任意 View，深底；返回句柄，调用方可按需关掉重开 */
    public static Dialog showView(Activity a, String title, View content, String dismissText) {
        Dialog d = base(a);
        LinearLayout p = panel(a);
        p.addView(titleTv(a, title));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(a, 18);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        content.setLayoutParams(clp);
        p.addView(content);
        TextView ok = neutralBtn(a, dismissText == null ? "知道了" : dismissText);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(a, 20);
        ok.setLayoutParams(blp);
        ok.setOnClickListener(v -> d.dismiss());
        p.addView(ok);
        d.setContentView(wrap(a, p));
        showSafe(a, d);
        return d;
    }

    // ---------------- 安装中 / 报错 ----------------

    /** 安装中弹窗句柄（照稿 04）：标题＋黄进度条＋「第 N 片 / 共 M 片 · P%」＋状态行 */
    public static final class Installing {
        private final Activity a;
        private final Dialog d;
        private final LinearLayout p;
        private View fill;
        private TextView pct;
        private TextView status;
        private TextView cancelBtn;
        private Runnable onCancel;
        private boolean cancelling;
        private boolean dead;

        private Installing(Activity a, Dialog d, LinearLayout p) {
            this.a = a;
            this.d = d;
            this.p = p;
        }

        /**
         * 挂取消钮：不挂就不露钮（没有取消去处的弹窗不许摆死钮）。
         * 点钮后冻结进度刷新、状态行转「正在取消…」，真正关弹窗等取消收口回调用 cancelled()。
         */
        public void setOnCancel(Runnable r) {
            onCancel = r;
            a.runOnUiThread(() -> {
                if (cancelBtn != null) {
                    cancelBtn.setVisibility(r == null ? View.GONE : View.VISIBLE);
                }
            });
        }

        /** 取消已收口：直接关弹窗（与 ok() 同形），后续提示由调用方 Notify */
        public void cancelled() {
            dead = true;
            a.runOnUiThread(() -> {
                try {
                    d.dismiss();
                } catch (Exception ignored) {
                }
            });
        }

        /** 进度：pctLine 如「第 5 片 / 共 7 片 · 63%」，statusLine 如「正在下载第 5 片…」 */
        public void progress(String pctLine, String statusLine) {
            if (dead || cancelling) return;
            a.runOnUiThread(() -> {
                if (pct != null && pctLine != null) pct.setText(pctLine);
                if (status != null && statusLine != null) status.setText(statusLine);
                if (fill != null && pctLine != null) {
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("·\\s*(\\d+)\\s*%").matcher(pctLine);
                    if (m.find()) {
                        try {
                            float w = Math.max(0.01f, Math.min(1f,
                                    Integer.parseInt(m.group(1)) / 100f));
                            LinearLayout.LayoutParams lp =
                                    (LinearLayout.LayoutParams) fill.getLayoutParams();
                            lp.weight = w;
                            fill.setLayoutParams(lp);
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        }

        /** 不定进度（准备/解压阶段） */
        public void busy(String statusLine) {
            progress(null, statusLine);
        }

        /** 成功：直接关掉 */
        public void ok() {
            dead = true;
            a.runOnUiThread(() -> {
                try {
                    d.dismiss();
                } catch (Exception ignored) {
                }
            });
        }

        /**
         * 失败：切报错态（照稿 05）——浅红人话＋「再试一次」/「算了」。
         * 再试一次：先回忙态，调 retry() 重跑整段。
         */
        public void error(String reason, Runnable retry) {
            if (dead) return;
            a.runOnUiThread(() -> {
                // 失败已是终态：恢复可关（返回键/点外），不再锁着弹窗
                d.setCancelable(true);
                d.setCanceledOnTouchOutside(true);
                p.removeAllViews();
                TextView err = new TextView(a);
                err.setText(reason == null || reason.isEmpty() ? "没装上" : reason);
                err.setTextSize(20);
                err.getPaint().setFakeBoldText(true);
                err.setTextColor(LabSkin.RED_ON_DARK);
                err.setLineSpacing(0, 1.6f);
                p.addView(err);

                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(a, 24);
                row.setLayoutParams(rlp);
                TextView again = yellowBtn(a, "再试一次");
                again.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                again.setOnClickListener(v -> {
                    // 重跑即回到安装中：重新锁住，关窗只能走「取消安装」钮
                    d.setCancelable(false);
                    d.setCanceledOnTouchOutside(false);
                    buildProgress(a, p, this);
                    if (retry != null) retry.run();
                });
                TextView cancel = neutralBtn(a, "算了");
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                clp.leftMargin = dp(a, 12);
                cancel.setLayoutParams(clp);
                cancel.setOnClickListener(v -> {
                    dead = true;
                    d.dismiss();
                });
                row.addView(again);
                row.addView(cancel);
                p.addView(row);
            });
        }
    }

    private static void buildProgress(Activity a, LinearLayout p, Installing h) {
        p.removeAllViews();
        p.addView(titleTv(a, (String) p.getTag()));
        // 进度条：外层轨道＋内层黄 fill（权重实现百分比宽度）
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(LabSkin.progressTrack(a));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 13));
        blp.topMargin = dp(a, 22);
        bar.setLayoutParams(blp);
        View fill = new View(a);
        fill.setBackground(LabSkin.progressFill(a));
        fill.setLayoutParams(new LinearLayout.LayoutParams(0, dp(a, 13), 0.01f));
        bar.addView(fill);
        p.addView(bar);
        h.fill = fill;

        TextView pct = new TextView(a);
        pct.setTextSize(19);
        pct.getPaint().setFakeBoldText(true);
        pct.setTextColor(LabSkin.TEXT_ON_DARK);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(a, 14);
        pct.setLayoutParams(plp);
        p.addView(pct);
        h.pct = pct;

        TextView status = new TextView(a);
        status.setTextSize(17);
        status.setTextColor(LabSkin.TEXT_ON_DARK_DIM);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(a, 9);
        status.setLayoutParams(slp);
        p.addView(status);
        h.status = status;

        // 取消钮：照「算了」的中性钮样式；没挂取消处理就不露面
        TextView cancel = neutralBtn(a, "取消安装");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(a, 20);
        cancel.setLayoutParams(clp);
        cancel.setVisibility(h.onCancel == null ? View.GONE : View.VISIBLE);
        cancel.setOnClickListener(v -> {
            if (h.cancelling || h.dead) return;
            h.cancelling = true;
            cancel.setEnabled(false);
            if (h.status != null) h.status.setText("正在取消…");
            if (h.onCancel != null) h.onCancel.run();
        });
        p.addView(cancel);
        h.cancelBtn = cancel;
    }

    /** 安装中弹窗：title 如「正在安装 Node 环境」。进度百分比由调用方在回调里算好传进来。 */
    public static Installing showInstalling(Activity a, String title) {
        Dialog d = base(a);
        // 安装进行中不可取消：返回键/点外都关不掉，要停只能点「取消安装」走取消链，
        // 免得弹窗关了任务还在后台跑、关后无取消入口、失败终态也没处显示；
        // 失败终态在 error() 里恢复可关，成功/取消终态直接关窗
        d.setCancelable(false);
        d.setCanceledOnTouchOutside(false);
        LinearLayout p = panel(a);
        p.setTag(title);
        Installing h = new Installing(a, d, p);
        buildProgress(a, p, h);
        d.setContentView(wrap(a, p));
        showSafe(a, d);
        return h;
    }

    // ---------------- 表单（带校验的输入弹窗） ----------------

    /** 表单确认回调：返回 true 才关弹窗（校验不过返回 false，弹窗留着） */
    public interface FormCallback {
        boolean onOk();
    }

    /**
     * 表单弹窗：标题＋自定义内容＋可选红色危险行＋「黄ok/算了」双钮。
     * dangerText 为空不放危险行；危险行点后直接关弹窗再跑 onDanger。
     */
    public static Dialog showForm(Activity a, String title, View content,
                                  String okText, FormCallback onOk,
                                  String dangerText, Runnable onDanger) {
        Dialog d = base(a);
        LinearLayout p = panel(a);
        p.addView(titleTv(a, title));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(a, 18);
        content.setLayoutParams(clp);
        p.addView(content);
        if (dangerText != null && !dangerText.isEmpty()) {
            TextView danger = new TextView(a);
            danger.setText(dangerText);
            danger.setTextSize(16);
            danger.getPaint().setFakeBoldText(true);
            danger.setGravity(Gravity.CENTER);
            danger.setTextColor(LabSkin.RED_ON_DARK);
            danger.setPadding(0, dp(a, 10), 0, dp(a, 10));
            danger.setClickable(true);
            danger.setFocusable(true);
            danger.setOnClickListener(v -> {
                d.dismiss();
                if (onDanger != null) onDanger.run();
            });
            p.addView(danger);
        }
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(a, 22);
        row.setLayoutParams(rlp);
        TextView ok = yellowBtn(a, okText == null ? "保存" : okText);
        ok.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ok.setOnClickListener(v -> {
            boolean close = true;
            try {
                if (onOk != null) close = onOk.onOk();
            } catch (Exception e) {
                close = false;
            }
            if (close) d.dismiss();
        });
        TextView cancel = neutralBtn(a, "算了");
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        nlp.leftMargin = dp(a, 12);
        cancel.setLayoutParams(nlp);
        cancel.setOnClickListener(v -> d.dismiss());
        row.addView(ok);
        row.addView(cancel);
        p.addView(row);
        d.setContentView(wrap(a, p));
        showSafe(a, d);
        return d;
    }

    /** 深底输入框（稿 02）：深底＋浅字＋占位 #a8a29a，看得清 */
    public static android.widget.EditText darkEdit(Activity a, String hint, String value) {
        android.widget.EditText et = new android.widget.EditText(a);
        et.setHint(hint);
        et.setText(value == null ? "" : value);
        et.setTextSize(16);
        et.setTextColor(LabSkin.TEXT_ON_DARK);
        et.setHintTextColor(LabSkin.HINT_ON_DARK);
        et.setBackground(LabSkin.darkInput(a));
        int h = dp(a, 12), w = dp(a, 16);
        et.setPadding(w, h, w, h);
        return et;
    }

    /** 把分片进度文案「正在下载（3/8）…」翻成稿式「第 3 片 / 共 8 片 · 37%」 */
    public static String shardLine(String progressText) {        if (progressText == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("（(\\d+)/(\\d+)）").matcher(progressText);
        if (!m.find()) return null;
        try {
            int done = Integer.parseInt(m.group(1));
            int total = Integer.parseInt(m.group(2));
            int pct = total <= 0 ? 0 : Math.min(99, done * 100 / total);
            return "第 " + done + " 片 / 共 " + total + " 片 · " + pct + "%";
        } catch (Exception e) {
            return null;
        }
    }

    private static View wrap(Activity a, LinearLayout p) {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(0, dp(a, 40), 0, dp(a, 40));
        root.addView(p);
        return root;
    }
}
