package com.fongmi.android.tv.ui.activity;
import com.fongmi.android.tv.utils.ToastUtil;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.legado.LegadoException;
import com.fongmi.android.tv.legado.LegadoSource;
import com.fongmi.android.tv.legado.LegadoStore;
import com.fongmi.android.tv.read.ReadDialogs;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import com.fongmi.android.tv.R;

/**
 * 书源管理（阶段一最小面）：导入（文本/文件/链接）· 启用停用 · 删除 · 搜索 · 读。
 * 精修稿另案，本件只求能用。
 */
public class LegadoActivity extends Activity {

    private static final int REQ_PICK_FILE = 1001;
    /** 进页类型：LegadoSource.TYPE_TEXT / TYPE_IMAGE / TYPE_AUDIO（书架按栏传）。 */
    private int pageType = LegadoSource.TYPE_TEXT;

    private SrcAdapter adapter;
    private final List<LegadoStore.Entry> entries = new ArrayList<>();
    private final List<LegadoSource> parsed = new ArrayList<>();
    private TextView countTitle;

    public static void start(android.content.Context context) {
        start(context, LegadoSource.TYPE_TEXT);
    }

    public static void start(android.content.Context context, int bookSourceType) {
        Intent intent = new Intent(context, LegadoActivity.class);
        intent.putExtra("book_source_type", bookSourceType);
        context.startActivity(intent);
    }

    /** 类型名：小说源 / 漫画源 / 听书源（标题/添加卡/文案全跟它走）。 */
    private String typeName() {
        if (pageType == LegadoSource.TYPE_IMAGE) return "漫画源";
        if (pageType == LegadoSource.TYPE_AUDIO) return "听书源";
        return "小说源";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ReadingSkin.enableEdgeToEdge(this, true);
        pageType = getIntent().getIntExtra("book_source_type", LegadoSource.TYPE_TEXT);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(ReadingSkin.pageBackground(this));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(16), dp(12), dp(12));
        ReadingSkin.applyTopInset(topBar, this, 16);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = dp(8);
        titles.setLayoutParams(tlp);
        TextView title = new TextView(this);
        title.setText(typeName());
        title.setTextSize(20);
        title.getPaint().setFakeBoldText(true);
        title.setTextColor(ReadingSkin.TEXT);
        titles.addView(title);
        TextView sub = new TextView(this);
        sub.setText("源文件存在本地，自己加自己管");
        sub.setTextSize(12);
        sub.setTextColor(ReadingSkin.SUB);
        sub.setPadding(0, dp(2), 0, 0);
        titles.addView(sub);
        topBar.addView(titles);
        // 右上角「＋ 导入」已删：全页只剩添加卡一个入口（他定的单一入口）
        root.addView(topBar);

        // 添加大卡：唯一的导入入口，三路并排（文件 / 链接 / 粘贴）
        LinearLayout addBox = new LinearLayout(this);
        addBox.setOrientation(LinearLayout.VERTICAL);
        addBox.setBackground(ReadingSkin.glassCard(this, 16));
        try {
            addBox.setElevation(dp(4));
        } catch (Throwable ignored) {
        }
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = dp(16);
        alp.rightMargin = dp(16);
        alp.topMargin = dp(12);
        addBox.setLayoutParams(alp);
        addBox.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView addTitle = new TextView(this);
        addTitle.setText("＋ 添加" + typeName());
        addTitle.setTextSize(17);
        addTitle.getPaint().setFakeBoldText(true);
        addTitle.setTextColor(ReadingSkin.GOLD_TEXT);
        addTitle.setGravity(Gravity.CENTER);
        addBox.addView(addTitle);
        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setPadding(0, dp(12), 0, 0);
        addRow.addView(addButton("📁 导入文件", "选手机里的源文件",
                v -> pickFile()));
        addRow.addView(addButton("🔗 贴链接导入", "粘贴源地址导入",
                v -> showUrlDialog()));
        addRow.addView(addButton("📋 粘贴导入", "粘贴源 JSON 文本",
                v -> showPasteDialog()));
        addBox.addView(addRow);
        root.addView(addBox);

        countTitle = new TextView(this);
        LinearLayout secRow = new LinearLayout(this);
        secRow.setOrientation(LinearLayout.HORIZONTAL);
        secRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(20);
        slp.rightMargin = dp(20);
        slp.topMargin = dp(20);
        slp.bottomMargin = dp(10);
        secRow.setLayoutParams(slp);
        countTitle.setTextSize(17);
        countTitle.getPaint().setFakeBoldText(true);
        countTitle.setTextColor(ReadingSkin.TEXT);
        LinearLayout.LayoutParams ctlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        countTitle.setLayoutParams(ctlp);
        secRow.addView(countTitle);
        root.addView(secRow);

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SrcAdapter();
        rv.setAdapter(adapter);
        root.addView(rv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        reload();
        // AI 口令「导入书源」直达：打开即弹粘贴导入框
        if (getIntent().getBooleanExtra("auto_import", false)) showPasteDialog();

        // 页面进出统一渐入渐出（与 BaseActivity 一致）
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
}

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
    }


    /** 添加入口小按钮：金描边暗底（走原有逻辑）。 */
    private LinearLayout addButton(String a, String b,
                                   View.OnClickListener onClick) {
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setGravity(Gravity.CENTER_HORIZONTAL);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(0x1F45C08A);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(2), 0x8045C08A);
        btn.setBackground(bg);
        btn.setPadding(dp(8), dp(14), dp(8), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = dp(6);
        lp.rightMargin = dp(6);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(onClick);
        TextView ta = new TextView(this);
        ta.setText(a);
        ta.setTextSize(15);
        ta.getPaint().setFakeBoldText(true);
        ta.setTextColor(ReadingSkin.TEXT);
        ta.setGravity(Gravity.CENTER);
        btn.addView(ta);
        TextView tb = new TextView(this);
        tb.setText(b);
        tb.setTextSize(11);
        tb.setTextColor(ReadingSkin.SUB);
        tb.setGravity(Gravity.CENTER);
        tb.setPadding(0, dp(6), 0, 0);
        btn.addView(tb);
        return btn;
    }

    private void reload() {
        entries.clear();
        parsed.clear();
        for (LegadoStore.Entry e : LegadoStore.all()) {
            try {
                LegadoSource s = LegadoStore.parse(e.json);
                // 按进页类型过滤：两边不串
                if (s.bookSourceType != pageType) continue;
                entries.add(e);
                parsed.add(s);
            } catch (Throwable ignored) {
            }
        }
        adapter.notifyDataSetChanged();
        if (countTitle != null) {
            countTitle.setText("已装 " + entries.size() + " 个");
        }
    }

    // ================= 导入（全页唯一入口：添加卡三路） =================

    private void showPasteDialog() {
        // 定高滚动框：文本在框内滚动，弹窗不被撑满屏
        EditText et = ReadDialogs.input(this, "粘贴" + typeName() + " JSON", true, 150);
        new ReadDialogs(this).setTitle("粘贴导入" + typeName()).setView(et)
                .setPositiveButton("导入书源", (d, w) -> doImport(() -> LegadoStore.importText(et.getText().toString())))
                .setNegativeButton("取消", null).show();
    }

    private void showUrlDialog() {
        EditText et = ReadDialogs.input(this, "https://…/xxx.json", false);
        new ReadDialogs(this).setTitle("链接导入" + typeName()).setView(et)
                .setPositiveButton("拉取", (d, w) -> doImport(() -> LegadoStore.importUrl(et.getText().toString().trim())))
                .setNegativeButton("取消", null).show();
    }

    private void pickFile() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "选择书源文件"), REQ_PICK_FILE);
        } catch (Throwable t) {
            ToastUtil.show(this, "打不开文件选择器（选书源文件）", Toast.LENGTH_SHORT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) {
                ToastUtil.show(this, "没取到文件", Toast.LENGTH_SHORT);
                return;
            }
            doImport(() -> {
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new LegadoException("打不开文件");
                    byte[] buf = new byte[8192];
                    StringBuilder sb = new StringBuilder();
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > 2 * 1024 * 1024) throw new LegadoException("文件过大（>2MB）");
                        sb.append(new String(buf, 0, n, "UTF-8"));
                    }
                    return LegadoStore.importText(sb.toString());
                } catch (LegadoException e) {
                    throw e;
                } catch (Throwable t) {
                    throw new LegadoException("文件读取失败: " + t.getMessage());
                }
            });
        }
    }

    private interface Importer {
        String run();
    }

    private void doImport(Importer importer) {
        ToastUtil.show(this, "导入中…", Toast.LENGTH_SHORT);
        new Thread(() -> {
            try {
                String name = importer.run();
                runOnUiThread(() -> {
                    String toast = (name.startsWith("已导入") || name.startsWith("进了"))
                            ? name : "已导入：" + name;
                    ToastUtil.show(this, toast, Toast.LENGTH_SHORT);
                    reload();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> failDialog("导入失败",
                        "源没导进来。多半是 JSON 格式不对、文件损坏，或者链接打不开。"
                                + "换一份正确的书源再试一次。详细原因已记到日志。", t));
            }
        }).start();
    }

    // ================= 列表 =================

    private class SrcAdapter extends RecyclerView.Adapter<SrcAdapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final View dot;
            final TextView name;
            final TextView sub;
            final TextView pill;
            final android.widget.Switch enabled;
            final TextView del;

            Holder(View v, View dot, TextView name, TextView sub,
                   TextView pill, android.widget.Switch enabled,
                   TextView del) {
                super(v);
                this.dot = dot;
                this.name = name;
                this.sub = sub;
                this.pill = pill;
                this.enabled = enabled;
                this.del = del;
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            android.content.Context c = parent.getContext();
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setBackground(ReadingSkin.glassCard(c, 16));
            try {
                row.setElevation(dp(4));
            } catch (Throwable ignored) {
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.leftMargin = dp(16);
            rlp.rightMargin = dp(16);
            rlp.bottomMargin = dp(10);
            row.setLayoutParams(rlp);

            // 状态灯：开绿 / 关灰（稿里的黄灯需延迟数据，代码没有，未加）
            View dot = new View(c);
            int ds = dp(10);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ds, ds);
            dlp.rightMargin = dp(12);
            dot.setLayoutParams(dlp);
            row.addView(dot);

            LinearLayout info = new LinearLayout(c);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView name = new TextView(c);
            name.setTextSize(15);
            name.getPaint().setFakeBoldText(true);
            name.setTextColor(ReadingSkin.TEXT);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(name);

            TextView sub = new TextView(c);
            sub.setTextSize(12);
            sub.setTextColor(ReadingSkin.SUB);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setPadding(0, dp(4), 0, 0);
            info.addView(sub);
            row.addView(info);

            TextView pill = new TextView(c);
            pill.setTextSize(11);
            pill.setPadding(dp(8), dp(5), dp(8), dp(5));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.rightMargin = dp(8);
            pill.setLayoutParams(plp);
            row.addView(pill);

            android.widget.Switch enabled = new android.widget.Switch(c);
            ReadingSkin.styleSwitch(enabled);
            row.addView(enabled);

            TextView del = new TextView(c);
            del.setText("删除");
            del.setTextSize(13);
            del.setTextColor(0xFFCC7777);
            del.setPadding(dp(10), dp(8), dp(2), dp(8));
            row.addView(del);

            return new Holder(row, dot, name, sub, pill, enabled, del);
        }

        /** 状态灯底：绿/灰。 */
        private android.graphics.drawable.Drawable dotBg(boolean on) {
            android.graphics.drawable.GradientDrawable d =
                    new android.graphics.drawable.GradientDrawable();
            d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            d.setColor(on ? 0xFF35B558 : 0xFF4A4A55);
            return d;
        }

        /** 类型胶囊：小说蓝 / 听书金 / 漫画粉 / 文件灰（稿语言）。 */
        private void stylePill(TextView pill, int type) {
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(dp(8));
            int tc, bc;
            switch (type) {
                case LegadoSource.TYPE_AUDIO:
                    tc = ReadingSkin.GOLD_TEXT;
                    bc = 0x6645C08A;
                    bg.setColor(0x1A45C08A);
                    break;
                case LegadoSource.TYPE_IMAGE:
                    tc = 0xFFFFC4DA;
                    bc = 0x52FF8CB4;
                    bg.setColor(0x1AFF8CB4);
                    break;
                case LegadoSource.TYPE_FILE:
                    tc = 0xFFAAAAAA;
                    bc = 0x55AAAAAA;
                    bg.setColor(0x14FFFFFF);
                    break;
                default:
                    tc = 0xFF9FD8FF;
                    bc = 0x5978B4FF;
                    bg.setColor(0x1F78B4FF);
                    break;
            }
            bg.setStroke(1, bc);
            pill.setBackground(bg);
            pill.setTextColor(tc);
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            LegadoStore.Entry e = entries.get(position);
            LegadoSource s = parsed.get(position);
            h.name.setText(s.tag());
            String typeLabel = s.typeLabel();
            if (!LegadoSource.runnable(s.bookSourceType)) typeLabel += "·暂不支持";
            h.sub.setText(typeLabel + (s.bookSourceGroup.isEmpty() ? "" : " · " + s.bookSourceGroup));
            h.dot.setBackground(dotBg(e.enabled));
            h.pill.setText(s.typeLabel());
            stylePill(h.pill, s.bookSourceType);
            h.enabled.setOnCheckedChangeListener(null);
            h.enabled.setChecked(e.enabled);
            h.enabled.setOnCheckedChangeListener((v, checked) -> {
                LegadoStore.setEnabled(s.bookSourceUrl, checked);
                e.enabled = checked;
                h.dot.setBackground(dotBg(checked));
            });
            h.del.setOnClickListener(v -> {
                new ReadDialogs(LegadoActivity.this)
                        .setTitle("删除书源「" + s.tag() + "」？")
                        .setPositiveButton("删除", (d, w) -> {
                            LegadoStore.delete(s.bookSourceUrl);
                            reload();
                        })
                        .setNegativeButton("取消", null).show();
            });
            h.itemView.setOnClickListener(null);
            // 搜书入口已搬走：行点击不再进搜索（搜书统一走阅读首页右上角）
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }

    /**
     * 失败提示走深色弹窗（同族 GlassDialog）：文案只给人话＋出路，
     * 英文原文/堆栈一律打日志，不上屏。
     */
    private void failDialog(String title, String body, Throwable t) {
        if (isFinishing() || isDestroyed()) return;
        android.util.Log.e("Legado", title + " raw=" + t, t);
        new ReadDialogs(this).setTitle(title).setMessage(body)
                .setPositiveButton("知道了", null).show();
    }

    private int dp(int v) {
        return ReadingSkin.dp(this, v);
    }
}
