package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.hk.HkDetail;
import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkRouter;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.databinding.ActivityHkBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 海阔小程序单页（M3）：V1 规则列表 / V2 分类+列表 / V3 搜索结果 / V4 详情，三视图栈式切换。
 * 点条目 → V4 海阔详情（标题/封面/简介/线路/选集）；点选集 → VideoActivity 直接播放。
 */
public class HkPageActivity extends BaseActivity {

    private static final int V_RULES = 0;
    private static final int V_CONTENT = 1;
    private static final int V_SEARCH = 2;
    private static final int V_DETAIL = 3;
    private static final int REQ_IMPORT_FILE = 0x101;
    private static final String PREFS = "xingchen";
    private static final String KEY_HIST = "hk_search_history";
    private static final String KEY_LAST_CLIP = "hk_last_clip_prompt";

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, HkPageActivity.class));
    }

    private ActivityHkBinding binding;
    private final Deque<Integer> stack = new ArrayDeque<>();

    private RuleAdapter ruleAdapter;
    private final List<HkRule> rules = new ArrayList<>();

    private ContentAdapter contentAdapter;
    private final List<HkItem> videos = new ArrayList<>();
    private GridLayoutManager contentGrid;
    private HkRule currentRule;
    private HkRouter router;
    private String cls = "", area = "", year = "", sort = "";
    private int page = 1;
    private boolean loading, noMore;
    private final Map<String, Integer> scrollMem = new HashMap<>();

    private VideoAdapter searchAdapter;
    private final List<HkItem> searchResults = new ArrayList<>();
    private String keyword = "";
    private int searchPage = 1;
    private boolean searchLoading, searchNoMore;

    private HkItem detailItem;
    private boolean detailFromSearch;
    private HkDetail currentDetail;
    private HkDetail.Line currentLine;
    private EpisodeAdapter episodeAdapter;
    private final List<HkDetail.Episode> episodes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 海阔页浅色主题：状态栏/导航栏用深色图标（BaseActivity 统一强制了白色图标，会盖掉主题的 windowLightStatusBar）
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityHkBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        initRulesView();
        initContentView();
        initSearchView();
        initDetailView();
        stack.push(V_RULES);
        showView(V_RULES);
        refreshRules();
    }

    @Override
    protected void onBackInvoked() {
        if (stack.size() > 1) {
            if (stack.peek() == V_CONTENT && currentRule != null && contentGrid != null) {
                scrollMem.put(currentRule.getTitle(), contentGrid.findFirstVisibleItemPosition());
            }
            stack.pop();
            showView(stack.peek());
        } else {
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        destroyRouterAsync();
        super.onDestroy();
    }

    /**
     * 异步销毁旧路由：HkJsRuntime.destroy() 会 submit(...).get() 等 JS 单线程池清空，
     * 前一个规则的 JS 任务若还在跑（慢网络/动态域名），同步调会冻住主线程几秒。
     * 切到后台线程销毁，点击进 V2 不再卡顿。
     */
    private void destroyRouterAsync() {
        HkRouter old = router;
        router = null;
        if (old == null) return;
        new Thread(() -> {
            try {
                old.destroy();
            } catch (Throwable ignored) {
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // 在窗口获得焦点后延迟检查剪贴板：Android 10+ 限制后台读取剪贴板，
        // onResume 时窗口焦点可能尚未就绪，onWindowFocusChanged 是可靠时机。
        if (hasFocus && binding != null && binding.getRoot() != null) {
            binding.getRoot().postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) checkClipboardForCloudCode();
            }, 200);
        }
    }

    /**
     * 进入小程序时自动识别剪贴板里的云口令（官方 App 同款行为）：
     * 复制口令后打开本页，自动弹出导入确认。同一口令只提示一次。
     */
    private void checkClipboardForCloudCode() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return;
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            CharSequence text = clip.getItemAt(0).coerceToText(this);
            if (TextUtils.isEmpty(text)) return;
            String code = extractCloudCode(text.toString());
            if (code == null) return;
            // 同一口令只提示一次
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            if (code.equals(sp.getString(KEY_LAST_CLIP, ""))) return;
            sp.edit().putString(KEY_LAST_CLIP, code).apply();
            new AlertDialog.Builder(this)
                    .setTitle("检测到云口令")
                    .setMessage("剪贴板中有海阔云口令，是否立即导入？\n\n" + code)
                    .setPositiveButton("导入", (d, w) -> importByCloudCode(code))
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 从文本中提取云口令（云1~云10 开头，取连续的一段）。 */
    private static String extractCloudCode(String text) {
        if (TextUtils.isEmpty(text)) return null;
        // 按空白切分，找第一段云口令
        for (String part : text.trim().split("\\s+")) {
            if (HkRuleManager.isCloudCode(part)) return part;
        }
        return null;
    }

    private void showView(int v) {
        binding.viewRules.setVisibility(v == V_RULES ? View.VISIBLE : View.GONE);
        binding.viewContent.setVisibility(v == V_CONTENT ? View.VISIBLE : View.GONE);
        binding.viewSearch.setVisibility(v == V_SEARCH ? View.VISIBLE : View.GONE);
        binding.viewDetail.setVisibility(v == V_DETAIL ? View.VISIBLE : View.GONE);
    }

    private void pushView(int v) {
        stack.push(v);
        showView(v);
    }

    // ================= V1 规则列表 =================

    private void initRulesView() {
        binding.rvRules.setLayoutManager(new LinearLayoutManager(this));
        ruleAdapter = new RuleAdapter();
        binding.rvRules.setAdapter(ruleAdapter);
        binding.btnImport.setOnClickListener(v -> showImportDialog());
    }

    private void refreshRules() {
        new Thread(() -> {
            List<HkRule> list = HkRuleManager.get().getRules();
            App.post(() -> {
                rules.clear();
                rules.addAll(list);
                ruleAdapter.rebuildSections();
                ruleAdapter.notifyDataSetChanged();
                binding.emptyRules.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                binding.rvRules.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
            });
        }).start();
    }

    /** 导入入口：三个选项各做成独立卡片框，有间距（选项类弹窗统一按此样式）。 */
    private void showImportDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        String[] labels = {"从文件导入", "从云口令导入", "粘贴规则 JSON"};
        AlertDialog dialog = new AlertDialog.Builder(this).setView(root).create();
        for (int i = 0; i < labels.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(labels[i]);
            tv.setTextColor(0xFF1A1D24);
            tv.setTextSize(16);
            tv.setBackgroundResource(R.drawable.dialog_option_card_light);
            tv.setPadding(dp(16), dp(14), dp(16), dp(14));
            final int which = i;
            tv.setOnClickListener(v -> {
                dialog.dismiss();
                if (which == 0) pickRuleFile();
                else if (which == 1) showCloudCodeDialog(null);
                else showPasteDialog();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = dp(10);
            root.addView(tv, lp);
        }
        dialog.show();
    }

    /** 云口令导入对话框（支持 云1~云10 开头，如 云6oooole/apidb/xxxx）。 */
    private void showCloudCodeDialog(String preset) {
        EditText et = new EditText(this);
        et.setHint("粘贴云口令，如：云6oooole/apidb/xxxx");
        et.setSingleLine(true);
        et.setTextColor(0xFF1A1D24);
        et.setHintTextColor(0xFF9AA1B0);
        if (!TextUtils.isEmpty(preset)) et.setText(preset);
        new AlertDialog.Builder(this)
                .setTitle("从云口令导入")
                .setView(et)
                .setPositiveButton("导入", (d, w) -> importByCloudCode(et.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void importByCloudCode(String code) {
        if (TextUtils.isEmpty(code)) {
            Notify.show("口令为空");
            return;
        }
        Notify.show("正在从云端获取规则…");
        new Thread(() -> {
            try {
                HkRule rule = HkRuleManager.get().importByCloudCode(code);
                App.post(() -> {
                    Notify.show("导入成功：" + rule.getTitle());
                    refreshRules();
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("导入失败：" + e.getMessage()));
            }
        }).start();
    }

    /**
     * 多文件导入的勾选项：解析结果 + 其所属 .hkzip 的附带资源（非 hkzip 时为 null）。
     * 附带资源只给本条目选中的规则落盘，避免多包混淆。
     */
    private static class PickEntry {
        final HkRuleManager.ParsedRule parsed;
        final HkRuleManager.HkZipData hkZipData;

        PickEntry(HkRuleManager.ParsedRule parsed, HkRuleManager.HkZipData hkZipData) {
            this.parsed = parsed;
            this.hkZipData = hkZipData;
        }
    }

    private void pickRuleFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/json", "application/zip", "application/javascript", "text/javascript"});
            // 支持一次选多个文件（系统文件选择器长按多选）
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQ_IMPORT_FILE);
        } catch (Exception e) {
            Notify.show("无法打开文件选择器");
        }
    }

    private void showPasteDialog() {
        EditText et = new EditText(this);
        et.setHint("粘贴 rule.json / 规则数组 / js: 规则内容");
        et.setMinLines(4);
        et.setTextColor(0xFF1A1D24);
        et.setHintTextColor(0xFF9AA1B0);
        new AlertDialog.Builder(this)
                .setTitle("从口令导入")
                .setView(et)
                .setPositiveButton("导入", (d, w) -> importRuleText(et.getText().toString(), null))
                .setNegativeButton("取消", null)
                .show();
    }

    private void importRuleJson(String json) {
        importRuleText(json, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT_FILE && resultCode == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                // 多选：ClipData 里每个 item 是一个文件
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri u = clip.getItemAt(i).getUri();
                    if (u != null) uris.add(u);
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) importRuleFiles(uris);
        }
    }

    /**
     * 多文件导入：逐个解析（zip/.hkzip/JSON/JSON 数组/js:），全部合并后走统一勾选弹窗（带全选）。
     * 单个文件解析失败只跳过该文件，不影响其他文件。
     *
     * @param uris 待导入的文件 Uri 列表
     */
    private void importRuleFiles(List<Uri> uris) {
        if (uris.size() > 1) Notify.show("正在解析 " + uris.size() + " 个文件…");
        new Thread(() -> {
            List<PickEntry> entries = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (Uri uri : uris) {
                File tmp = null;
                String displayName = getDisplayName(uri);
                try {
                    // 先落到临时文件：需要按后缀/魔数判断是否为 zip
                    tmp = copyToTemp(uri);
                    if (isZipFile(tmp, displayName)) {
                        // 多规则包：先全部解析 → 弹窗让用户勾选 → 只导入选中的
                        boolean hkzip = displayName != null && displayName.toLowerCase().endsWith(".hkzip");
                        if (hkzip) {
                            HkRuleManager.HkZipData zipData = HkRuleManager.get().parseHkZip(tmp);
                            for (HkRuleManager.ParsedRule p : zipData.rules) entries.add(new PickEntry(p, zipData));
                        } else {
                            for (HkRuleManager.ParsedRule p : HkRuleManager.get().parseZip(tmp)) {
                                entries.add(new PickEntry(p, null));
                            }
                        }
                    } else {
                        String text = readAll(new FileInputStream(tmp));
                        if (HkRuleManager.isJsRuleText(text)) {
                            entries.add(new PickEntry(HkRuleManager.get().parseJsRule(text, displayName), null));
                        } else {
                            for (HkRuleManager.ParsedRule p : HkRuleManager.get().parseJsonList(text)) {
                                entries.add(new PickEntry(p, null));
                            }
                        }
                    }
                } catch (Exception e) {
                    errors.add((displayName == null ? "文件" : displayName) + "：" + e.getMessage());
                } finally {
                    if (tmp != null) tmp.delete();
                }
            }
            final List<PickEntry> result = entries;
            final List<String> failList = errors;
            App.post(() -> {
                if (!failList.isEmpty()) {
                    Notify.show("跳过 " + failList.size() + " 个失败文件：" + TextUtils.join("；", failList));
                }
                if (result.isEmpty()) {
                    if (failList.isEmpty()) Notify.show("未解析到任何规则");
                } else if (result.size() == 1) {
                    // 单个规则直接导入（.hkzip 的附带资源一并落盘）
                    importPickedRules(result);
                } else {
                    showPickRulesDialog(result);
                }
            });
        }).start();
    }

    /** content uri 落盘到临时文件（便于按魔数判断 zip）。 */
    private File copyToTemp(Uri uri) throws Exception {
        File tmp = File.createTempFile("hkimport", ".tmp", getCacheDir());
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return tmp;
    }

    /**
     * 统一文本导入路由：js: 开头 → JS 规则包装导入；否则按 JSON（单个对象或数组）导入。
     *
     * @param text     规则文本
     * @param fileName 来源文件名（可为 null），js: 规则用于取标题
     */
    private void importRuleText(String text, String fileName) {
        if (TextUtils.isEmpty(text)) {
            App.post(() -> Notify.show("内容为空"));
            return;
        }
        new Thread(() -> {
            try {
                if (HkRuleManager.isJsRuleText(text)) {
                    HkRule rule = HkRuleManager.get().importJsRule(text, fileName);
                    App.post(() -> {
                        Notify.show("导入成功：" + rule.getTitle());
                        refreshRules();
                    });
                } else {
                    // 一键导入：先全部解析，多个则弹窗让用户勾选要导入哪些（带全选）
                    List<HkRuleManager.ParsedRule> parsed = HkRuleManager.get().parseJsonList(text);
                    final List<PickEntry> entries = wrapParsed(parsed, null);
                    App.post(() -> {
                        if (entries.size() == 1) {
                            importPickedRules(entries);
                        } else {
                            showPickRulesDialog(entries);
                        }
                    });
                }
            } catch (Exception e) {
                App.post(() -> Notify.show("导入失败：" + e.getMessage()));
            }
        }).start();
    }

    /** 把解析结果包装成勾选项（附带资源统一传入，传 null 表示无）。 */
    private static List<PickEntry> wrapParsed(List<HkRuleManager.ParsedRule> parsed, HkRuleManager.HkZipData hkZipData) {
        List<PickEntry> list = new ArrayList<>(parsed.size());
        for (HkRuleManager.ParsedRule p : parsed) list.add(new PickEntry(p, hkZipData));
        return list;
    }

    /**
     * 把选中的解析结果落盘导入（各 .hkzip 的附带资源按所属包分别落盘）。
     *
     * @param picked 选中的勾选项
     */
    private void importPickedRules(List<PickEntry> picked) {
        if (picked.isEmpty()) {
            Notify.show("未选中任何规则");
            return;
        }
        new Thread(() -> {
            try {
                List<HkRule> rules = new ArrayList<>();
                Map<HkRuleManager.HkZipData, List<HkRule>> assets = new HashMap<>();
                for (PickEntry e : picked) {
                    HkRuleManager.get().saveRule(e.parsed.rule, e.parsed.json);
                    rules.add(e.parsed.rule);
                    if (e.hkZipData != null) {
                        List<HkRule> g = assets.get(e.hkZipData);
                        if (g == null) {
                            g = new ArrayList<>();
                            assets.put(e.hkZipData, g);
                        }
                        g.add(e.parsed.rule);
                    }
                }
                for (Map.Entry<HkRuleManager.HkZipData, List<HkRule>> en : assets.entrySet()) {
                    HkRuleManager.get().saveHkZipAssets(en.getKey(), en.getValue());
                }
                String msg = rules.size() == 1
                        ? "导入成功：" + rules.get(0).getTitle()
                        : "导入成功 " + rules.size() + " 个小程序";
                App.post(() -> {
                    Notify.show(msg);
                    refreshRules();
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("导入失败：" + e.getMessage()));
            }
        }).start();
    }

    /**
     * 一键导入勾选弹窗：多选列表（标题+作者）+ 顶部"全选"复选框。
     * 确定后只导入勾选中的规则。
     *
     * @param entries 解析好的全部勾选项
     */
    private void showPickRulesDialog(List<PickEntry> entries) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * density);
        root.setPadding(pad, pad / 2, pad, 0);

        CheckBox cbAll = new CheckBox(this);
        cbAll.setText("全选");
        cbAll.setChecked(true);
        cbAll.setTextColor(0xFF1A1D24);
        root.addView(cbAll);

        List<String> labels = new ArrayList<>();
        for (PickEntry e : entries) {
            HkRuleManager.ParsedRule p = e.parsed;
            String author = p.rule.getAuthor();
            labels.add(TextUtils.isEmpty(author) ? p.rule.getTitle() : p.rule.getTitle() + "（" + author + "）");
        }
        ListView listView = new ListView(this);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.item_hk_pick, labels);
        listView.setAdapter(adapter);
        for (int i = 0; i < labels.size(); i++) listView.setItemChecked(i, true);
        int rowPx = (int) (64 * density);
        int listH = Math.min(rowPx * labels.size(), rowPx * 7);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, listH);
        lp.topMargin = (int) (8 * density);
        listView.setLayoutParams(lp);
        root.addView(listView);

        cbAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (int i = 0; i < labels.size(); i++) listView.setItemChecked(i, isChecked);
        });

        new AlertDialog.Builder(this)
                .setTitle("选择要导入的小程序（" + labels.size() + "）")
                .setView(root)
                .setPositiveButton("导入", (d, w) -> {
                    SparseBooleanArray checked = listView.getCheckedItemPositions();
                    List<PickEntry> picked = new ArrayList<>();
                    for (int i = 0; i < labels.size(); i++) {
                        if (checked.get(i)) picked.add(entries.get(i));
                    }
                    importPickedRules(picked);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 取 content uri 的显示文件名（可能为 null）。 */
    private String getDisplayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 按后缀或 zip 魔数（PK\x03\x04）判断是否为 zip 包。 */
    private boolean isZipFile(File f, String displayName) {
        if (displayName != null && displayName.toLowerCase().endsWith(".zip")) return true;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] magic = new byte[4];
            int n = in.read(magic);
            return n == 4 && magic[0] == 'P' && magic[1] == 'K' && magic[2] == 3 && magic[3] == 4;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    private void showRuleMenu(HkRule rule) {
        new AlertDialog.Builder(this)
                .setItems(new String[]{"导出为云口令（长）", "导出为云口令（短）", "导出为文件", "删除"}, (d, which) -> {
                    if (which == 0) exportAsCloudCode(rule);
                    else if (which == 1) exportAsShortCloudCode(rule);
                    else if (which == 2) exportAsFile(rule);
                    else showDeleteRuleConfirm(rule);
                })
                .show();
    }

    /** 导出规则为云口令：上传云端后显示口令，可复制分享（官方 App 也可导入）。 */
    private void exportAsCloudCode(HkRule rule) {
        Notify.show("正在上传云端…");
        new Thread(() -> {
            try {
                String code = HkRuleManager.get().exportAsCloudCode(rule.getTitle());
                App.post(() -> showExportCodeDialog(rule.getTitle(), code));
            } catch (Exception e) {
                App.post(() -> Notify.show("导出失败：" + e.getMessage()));
            }
        }).start();
    }

    /** 导出为云5短口令：走 cmd.im，生成 云5oooole/{id} 短格式。 */
    private void exportAsShortCloudCode(HkRule rule) {
        Notify.show("正在上传云端…");
        new Thread(() -> {
            try {
                String code = HkRuleManager.get().exportAsCmdImCode(rule.getTitle());
                App.post(() -> showExportCodeDialog(rule.getTitle(), code));
            } catch (Exception e) {
                App.post(() -> Notify.show("导出失败：" + e.getMessage()));
            }
        }).start();
    }

    private void showExportCodeDialog(String title, String code) {
        TextView tv = new TextView(this);
        tv.setText(code);
        tv.setTextIsSelectable(true);
        tv.setTextColor(0xFF1A1D24);
        tv.setPadding(dp(8), dp(8), dp(8), dp(8));
        new AlertDialog.Builder(this)
                .setTitle("「" + title + "」的云口令")
                .setMessage("口令已生成，可复制分享给他人导入：")
                .setView(tv)
                .setPositiveButton("复制", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("hk_cloud_code", code));
                        Notify.show("口令已复制");
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    /** 导出为文件：弹窗选择格式（.json / .hkzip），保存到 Download 目录并调起分享。 */
    private void exportAsFile(HkRule rule) {
        new AlertDialog.Builder(this)
                .setTitle("选择导出格式")
                .setItems(new String[]{"导出为 .json 文件", "导出为 .hkzip 包"}, (d, which) -> {
                    if (which == 0) doExportAsJsonFile(rule);
                    else doExportAsHkZip(rule);
                })
                .show();
    }

    /** 导出规则为文件：保存到 Download 目录并调起分享。 */
    private void doExportAsJsonFile(HkRule rule) {
        new Thread(() -> {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                java.io.File out = HkRuleManager.get().exportAsFile(rule.getTitle(), dir);
                App.post(() -> {
                    Notify.show("已导出到：" + out.getAbsolutePath());
                    try {
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("application/json");
                        share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(out));
                        startActivity(Intent.createChooser(share, "分享规则文件"));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("导出失败：" + e.getMessage()));
            }
        }).start();
    }

    /** 导出规则为 .hkzip 包（含附带资源）：保存到 Download 目录并调起分享。 */
    private void doExportAsHkZip(HkRule rule) {
        new Thread(() -> {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                java.io.File out = HkRuleManager.get().exportAsHkZip(rule.getTitle(), dir);
                App.post(() -> {
                    Notify.show("已导出到：" + out.getAbsolutePath());
                    try {
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("application/zip");
                        share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(out));
                        startActivity(Intent.createChooser(share, "分享规则包"));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("导出失败：" + e.getMessage()));
            }
        }).start();
    }

    private void showDeleteRuleConfirm(HkRule rule) {        new AlertDialog.Builder(this)
                .setTitle("删除规则")
                .setMessage("确定删除「" + rule.getTitle() + "」吗？")
                .setPositiveButton("删除", (d, w) -> new Thread(() -> {
                    HkRuleManager.get().delete(rule.getTitle());
                    App.post(this::refreshRules);
                }).start())
                .setNegativeButton("取消", null)
                .show();
    }

    private class RuleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_RULE = 1;
        private final List<Object> items = new ArrayList<>();

        /** 不分组：平铺全部规则，保持原顺序。 */
        void rebuildSections() {
            items.clear();
            items.addAll(rules);
        }

        class Holder extends RecyclerView.ViewHolder {
            TextView icon, name, sub;
            SwitchCompat sw;
            ImageButton more;

            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.tv_icon);
                name = v.findViewById(R.id.tv_name);
                sub = v.findViewById(R.id.tv_sub);
                sw = v.findViewById(R.id.sw_enable);
                more = v.findViewById(R.id.btn_more);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return TYPE_RULE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hk_rule, parent, false);
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) v.getLayoutParams();
            lp.bottomMargin = dp(12);
            v.setLayoutParams(lp);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {
            Holder h = (Holder) vh;
            HkRule rule = (HkRule) items.get(position);
            h.name.setText(rule.getTitle());
            h.sub.setText(rule.getAuthor() + " · v" + rule.getVersion());
            String iconDef = rule.getIcon();
            if (!TextUtils.isEmpty(iconDef) && iconDef.startsWith("#")) {
                try {
                    h.icon.setBackgroundColor(android.graphics.Color.parseColor(iconDef));
                } catch (Exception ignored) {
                }
            }
            h.icon.setAlpha(rule.isEnabled() ? 1f : 0.5f);
            h.name.setAlpha(rule.isEnabled() ? 1f : 0.5f);
            h.sub.setAlpha(rule.isEnabled() ? 1f : 0.5f);
            h.sw.setOnCheckedChangeListener(null);
            h.sw.setChecked(rule.isEnabled());
            h.sw.setOnCheckedChangeListener((btn, checked) -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                Object o = items.get(pos);
                if (!(o instanceof HkRule)) return;
                HkRule r = (HkRule) o;
                r.setEnabled(checked);
                HkRuleManager.get().setEnabled(r.getTitle(), checked);
                notifyItemChanged(pos);
            });
            h.more.setOnClickListener(v -> showRuleMenu(rule));
            h.itemView.setOnClickListener(v -> openRule(rule));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    // ================= V2 分类+列表 =================

    private void initContentView() {
        // 官方 12 列栅格：movie_3=4(3列) / icon_4系列=3(4个一行) / icon_2系列=6(2个一行) / 其余=12(全宽)
        contentGrid = new GridLayoutManager(this, 12);
        contentGrid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return contentAdapter == null ? 12 : contentAdapter.spanFor(position);
            }
        });
        binding.rvVideos.setLayoutManager(contentGrid);
        binding.rvVideos.addItemDecoration(new GridSpace());
        contentAdapter = new ContentAdapter(videos);
        binding.rvVideos.setAdapter(contentAdapter);
        binding.rvCategory.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.swipeContent.setColorSchemeColors(0xFFD4A017);
        binding.swipeContent.setOnRefreshListener(() -> loadContent(true));
        binding.btnBackContent.setOnClickListener(v -> onBackInvoked());
        binding.btnSearchContent.setOnClickListener(v -> openSearch());
        binding.tvContentTitle.setSelected(true);
        binding.tvContentEmpty.setOnClickListener(v -> loadContent(true));
        binding.rvVideos.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || noMore) return;
                int last = contentGrid.findLastVisibleItemPosition();
                if (last >= contentAdapter.getItemCount() - 3) loadContent(false);
            }
        });
    }

    private void openRule(HkRule rule) {
        destroyRouterAsync();
        currentRule = rule;
        cls = "";
        area = "";
        year = "";
        sort = "";
        page = 1;
        noMore = false;
        loading = false; // 旧规则的在途加载作废，由新 loadContent 接管
        contentGen++; // 旧回调过期
        videos.clear();
        binding.tvContentTitle.setText(rule.getTitle());
        buildCategoryRow();
        buildFilterRows();
        pushView(V_CONTENT);
        loadContent(true);
        Integer pos = scrollMem.get(rule.getTitle());
        if (pos != null && pos > 0) contentGrid.scrollToPosition(pos);
    }

    private void buildCategoryRow() {
        List<String[]> pairs = new ArrayList<>();
        pairs.add(new String[]{"全部", ""});
        pairs.addAll(currentRule.getClassPairs());
        ChipAdapter adapter = new ChipAdapter(pairs, 0, value -> {
            cls = value;
            loadContent(true);
        });
        binding.rvCategory.setAdapter(adapter);
        binding.rvCategory.setVisibility(pairs.size() > 1 ? View.VISIBLE : View.GONE);
    }

    /**
     * V2 动态分类 tab：规则 find_rule 返回的 scroll_button/flex_button 条目。
     * 标题去 HTML 标签；含 #ff1493 高亮色的为当前选中。点击 tab 求值其 lazyRule
     * （putMyVar 设分类变量），再刷新列表。
     */
    private void buildDynamicTabs(List<HkItem> tabs) {
        List<String[]> pairs = new ArrayList<>();
        int sel = 0;
        for (int i = 0; i < tabs.size(); i++) {
            HkItem it = tabs.get(i);
            String raw = it.getTitle() == null ? "" : it.getTitle();
            if (raw.toLowerCase().contains("#ff1493")) sel = i;
            String name = stripHtml(raw);
            if (name.isEmpty()) name = "分类" + (i + 1);
            pairs.add(new String[]{name, it.getUrl() == null ? "" : it.getUrl()});
        }
        ChipAdapter adapter = new ChipAdapter(pairs, sel, url -> {
            if (url == null || url.isEmpty()) return;
            new Thread(() -> {
                try {
                    getRouter().evalTab(url);
                } catch (Throwable ignored) {
                }
                App.post(() -> loadContent(true));
            }).start();
        });
        binding.rvCategory.setAdapter(adapter);
        binding.rvCategory.setVisibility(View.VISIBLE);
    }

    private void buildFilterRows() {
        binding.filterContainer.removeAllViews();
        addFilterRow("地区", currentRule.getAreaPairs(), value -> {
            area = value;
            loadContent(true);
        });
        addFilterRow("年份", currentRule.getYearPairs(), value -> {
            year = value;
            loadContent(true);
        });
        addFilterRow("类型", currentRule.getSortPairs(), value -> {
            sort = value;
            loadContent(true);
        });
    }

    private void addFilterRow(String label, List<String[]> pairs, ChipAdapter.OnPick pick) {
        if (pairs == null || pairs.isEmpty()) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFF4F555F);
        tv.setTextSize(12);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(tv, labelLp);
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        List<String[]> all = new ArrayList<>();
        all.add(new String[]{"全部", ""});
        all.addAll(pairs);
        rv.setAdapter(new ChipAdapter(all, 0, pick));
        row.addView(rv, new LinearLayout.LayoutParams(0, dp(36), 1));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(8);
        binding.filterContainer.addView(row, rowLp);
    }

    private HkRouter getRouter() throws Exception {
        if (router == null) router = new HkRouter(currentRule);
        return router;
    }

    private int contentGen = 0;

    private void loadContent(boolean reset) {
        if (loading || currentRule == null) return;
        loading = true;
        final int gen = ++contentGen;
        if (reset) {
            page = 1;
            noMore = false;
            videos.clear();
            contentAdapter.refreshGroups();
            contentAdapter.notifyDataSetChanged();
            binding.tvContentEmpty.setVisibility(View.GONE);
            binding.rvVideos.setVisibility(View.GONE);
            binding.loadingContent.setVisibility(View.VISIBLE);
        }
        updateFooter();
        final int p = page;
        final String c = cls, a = area, y = year, s = sort;
        new Thread(() -> {
            List<HkItem> list;
            try {
                list = getRouter().home(p, c, a, y, s);
            } catch (Throwable e) {
                list = new ArrayList<>();
            }
            final List<HkItem> result = list;
            App.post(() -> {
                if (gen != contentGen) return; // 规则已切换/被更新的加载覆盖，丢弃过期结果
                loading = false;
                binding.swipeContent.setRefreshing(false);
                binding.loadingContent.setVisibility(View.GONE);
                // V2：把导航类条目（scroll_button/flex_button 分类）拆出来做顶部 tab，
                // 分隔块（blank_block/line_blank/line）丢弃，其余内容条目按 col_type
                // 由 ContentAdapter 多 viewType 渲染（视频/文本/图片/输入框等）。
                List<HkItem> tabs = new ArrayList<>();
                List<HkItem> contents = new ArrayList<>();
                for (HkItem it : result) {
                    String ct = it.getColType() == null ? "" : it.getColType().trim().toLowerCase();
                    if ("scroll_button".equals(ct) || "flex_button".equals(ct)) tabs.add(it);
                    else if ("blank_block".equals(ct) || "line_blank".equals(ct) || "line".equals(ct)) continue;
                    else contents.add(it);
                }
                if (p == 1 && !tabs.isEmpty()) buildDynamicTabs(tabs);
                if (contents.isEmpty()) {
                    if (p == 1 && videos.isEmpty()) {
                        binding.tvContentEmpty.setVisibility(View.VISIBLE);
                        binding.rvVideos.setVisibility(View.GONE);
                    } else {
                        noMore = true;
                    }
                } else {
                    videos.addAll(contents);
                    page = p + 1;
                    binding.rvVideos.setVisibility(View.VISIBLE);
                    contentAdapter.refreshGroups();
                    contentAdapter.notifyDataSetChanged();
                }
                updateFooter();
            });
        }).start();
    }

    private void updateFooter() {
        if (videos.isEmpty()) {
            binding.tvFooter.setVisibility(View.GONE);
            return;
        }
        binding.tvFooter.setVisibility(View.VISIBLE);
        if (loading) binding.tvFooter.setText("加载中…");
        else if (noMore) binding.tvFooter.setText("已加载全部");
        else binding.tvFooter.setText("");
    }

    // ================= V3 搜索 =================

    private void initSearchView() {
        GridLayoutManager grid = new GridLayoutManager(this, 3);
        binding.rvSearchResults.setLayoutManager(grid);
        binding.rvSearchResults.addItemDecoration(new GridSpace());
        searchAdapter = new VideoAdapter(searchResults, false);
        binding.rvSearchResults.setAdapter(searchAdapter);
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.btnBackSearch.setOnClickListener(v -> onBackInvoked());
        binding.btnClear.setOnClickListener(v -> binding.etKeyword.setText(""));
        binding.btnClearHistory.setOnClickListener(v -> {
            getPrefs().edit().remove(KEY_HIST).apply();
            refreshHistory();
        });
        binding.etKeyword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        binding.etKeyword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch(v.getText().toString().trim());
                return true;
            }
            return false;
        });
        binding.rvSearchResults.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || searchLoading || searchNoMore || keyword.isEmpty()) return;
                GridLayoutManager lm = (GridLayoutManager) rv.getLayoutManager();
                if (lm != null && lm.findLastVisibleItemPosition() >= searchAdapter.getItemCount() - 3) {
                    doSearchPage();
                }
            }
        });
    }

    private void openSearch() {
        keyword = "";
        searchPage = 1;
        searchNoMore = false;
        searchResults.clear();
        searchAdapter.notifyDataSetChanged();
        binding.etKeyword.setText("");
        binding.tvSearchEmpty.setVisibility(View.GONE);
        binding.rvSearchResults.setVisibility(View.VISIBLE);
        refreshHistory();
        binding.historySection.setVisibility(View.VISIBLE);
        pushView(V_SEARCH);
        binding.etKeyword.requestFocus();
    }

    private void doSearch(String kw) {
        if (kw.isEmpty() || currentRule == null) return;
        keyword = kw;
        searchPage = 1;
        searchNoMore = false;
        searchResults.clear();
        searchAdapter.notifyDataSetChanged();
        binding.tvSearchEmpty.setVisibility(View.GONE);
        binding.rvSearchResults.setVisibility(View.VISIBLE);
        binding.historySection.setVisibility(View.GONE);
        saveHistory(kw);
        doSearchPage();
    }

    private void doSearchPage() {
        if (searchLoading || currentRule == null || keyword.isEmpty()) return;
        searchLoading = true;
        final int p = searchPage;
        final String kw = keyword;
        new Thread(() -> {
            List<HkItem> list;
            try {
                list = getRouter().search(kw, p);
            } catch (Throwable e) {
                list = new ArrayList<>();
            }
            final List<HkItem> result = list;
            App.post(() -> {
                searchLoading = false;
                if (result.isEmpty()) {
                    if (p == 1 && searchResults.isEmpty()) {
                        binding.tvSearchEmpty.setVisibility(View.VISIBLE);
                        binding.rvSearchResults.setVisibility(View.GONE);
                    } else {
                        searchNoMore = true;
                    }
                } else {
                    searchResults.addAll(result);
                    searchPage = p + 1;
                    searchAdapter.notifyDataSetChanged();
                }
            });
        }).start();
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private List<String> loadHistory() {
        List<String> out = new ArrayList<>();
        String s = getPrefs().getString(KEY_HIST, "");
        if (!s.isEmpty()) {
            for (String part : s.split("")) {
                if (!part.isEmpty()) out.add(part);
            }
        }
        return out;
    }

    private void saveHistory(String kw) {
        List<String> hist = loadHistory();
        hist.remove(kw);
        hist.add(0, kw);
        while (hist.size() > 20) hist.remove(hist.size() - 1);
        getPrefs().edit().putString(KEY_HIST, TextUtils.join("", hist)).apply();
        refreshHistory();
    }

    private void refreshHistory() {
        List<String> hist = loadHistory();
        List<String[]> pairs = new ArrayList<>();
        for (String h : hist) pairs.add(new String[]{h, h});
        binding.rvHistory.setAdapter(new ChipAdapter(pairs, -1, value -> doSearch(value)));
        binding.historySection.setVisibility(hist.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ================= V4 详情 =================

    private void initDetailView() {
        binding.btnBackDetail.setOnClickListener(v -> onBackInvoked());
        binding.tvDetailTitle.setSelected(true);
        binding.rvDetailLines.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        GridLayoutManager epGrid = new GridLayoutManager(this, 4);
        binding.rvDetailEpisodes.setLayoutManager(epGrid);
        binding.rvDetailEpisodes.setNestedScrollingEnabled(false);
        binding.rvDetailEpisodes.addItemDecoration(new EpSpace());
        episodeAdapter = new EpisodeAdapter();
        binding.rvDetailEpisodes.setAdapter(episodeAdapter);
        binding.tvDetailEmpty.setOnClickListener(v -> {
            if (detailItem != null) openDetail(detailItem, detailFromSearch);
        });
    }

    private void openDetail(HkItem item, boolean fromSearch) {
        openDetail(item, fromSearch, false);
    }

    /**
     * V4 详情。
     *
     * @param lazyPrecheck true=条目 URL 自带 {@code @lazyRule=}（如粉嫩小BB点封面即播）：
     *                     先后台求值，不预推 V4；若判定为直接播放则根本不进 V4，
     *                     避免 V4 loading 闪一下。非直接播放时再补推 V4 展示结果。
     */
    private void openDetail(HkItem item, boolean fromSearch, boolean lazyPrecheck) {
        detailItem = item;
        detailFromSearch = fromSearch;
        currentDetail = null;
        currentLine = null;
        episodes.clear();
        if (episodeAdapter != null) episodeAdapter.notifyDataSetChanged();
        if (!lazyPrecheck) {
            binding.tvDetailTitle.setText(item.getTitle());
            binding.detailScroll.setVisibility(View.GONE);
            binding.tvDetailEmpty.setVisibility(View.GONE);
            binding.tvDetailLoading.setVisibility(View.VISIBLE);
            pushView(V_DETAIL);
        } else {
            // 直接播放预检（条目 URL 自带 @lazyRule=）：后台静默求值，不弹加载框；
            // 求值完成后直接进播放器（用户要求：点封面即播，不弹"加载中"）
        }
        new Thread(() -> {
            HkDetail detail;
            try {
                detail = getRouter().detail(item.getUrl(), item, fromSearch);
            } catch (Throwable e) {
                detail = null;
            }
            final HkDetail result = detail;
            App.post(() -> {
                if (lazyPrecheck) binding.loadingContent.setVisibility(View.GONE);
                else binding.tvDetailLoading.setVisibility(View.GONE);
                // 分类切换（官方 refreshPage 语义：putMyVar 后重刷列表，不进 V4）
                if (result != null && result.isTabSwitch()) {
                    if (!lazyPrecheck) onBackInvoked(); // 弹出已推的 V4 空 loading
                    loadContent(true);
                    return;
                }
                String direct = result == null ? "" : result.getDirectPlayUrl();
                if (!TextUtils.isEmpty(direct)) {
                    // 直接播放：若已推 V4 先弹出，保证播放器返回时回到列表
                    if (!lazyPrecheck) onBackInvoked();
                    startHkDirectPlay(item, direct);
                    return;
                }
                // dealWithUrl 分流（官方：@lazyRule= 解析后不进 V4）
                String dealKind = result == null ? "" : result.getDealKind();
                if (!TextUtils.isEmpty(dealKind)) {
                    if (!lazyPrecheck) onBackInvoked();
                    else binding.loadingContent.setVisibility(View.GONE);
                    handleDealUrl(dealKind, result.getDealUrl(), item);
                    return;
                }
                if (lazyPrecheck) {
                    // 预检非直接播放：补推 V4 再展示（复用已算出的 result，不二次求值）
                    binding.tvDetailTitle.setText(item.getTitle());
                    binding.detailScroll.setVisibility(View.GONE);
                    binding.tvDetailEmpty.setVisibility(View.GONE);
                    binding.tvDetailLoading.setVisibility(View.GONE);
                    pushView(V_DETAIL);
                }
                showDetailResult(result);
            });
        }).start();
    }

    /**
     * 直接播放：从当前列表的视频条目拼选集（官方 getChapters：每条=一集，当前点击的 use=true），
     * 把选集传给播放器，解决"没有线路没有选集"。
     * 只收录视频卡片条目（movie_*），过滤分类/按钮/文本等非视频条目（如粉嫩小BB的
     * "制服情景""国产情色"等分类按钮不能混进选集）。
     */
    private void startHkDirectPlay(HkItem item, String directUrl) {
        java.util.List<HkItem> eps = new java.util.ArrayList<>();
        for (HkItem it : videos) {
            if (it == null || TextUtils.isEmpty(it.getUrl())) continue;
            if (contentTypeOf(it) != ContentAdapter.T_VIDEO) continue;
            eps.add(it);
        }
        // 兜底：被点击条目若因类型特殊被过滤，仍把它加入，保证当前集可播可切
        if (item != null && !TextUtils.isEmpty(item.getUrl()) && !eps.contains(item)) {
            eps.add(item);
        }
        org.json.JSONArray arr = new org.json.JSONArray();
        try {
            for (HkItem it : eps) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("name", stripHtml(it.getTitle()));
                o.put("url", it.getUrl());
                o.put("pic", it.getPic() == null ? "" : it.getPic());
                arr.put(o);
            }
        } catch (Throwable ignored) {
        }
        int selIdx = Math.max(0, eps.indexOf(item));
        VideoActivity.startHkPlay(HkPageActivity.this,
                currentRule == null ? "" : currentRule.getTitle(), "默认",
                directUrl, item.getTitle(), item.getTitle(), item.getPic(),
                arr.toString(), selIdx);
    }

    /**
     * dealWithUrl 分流处理（官方 ArticleListFragment.dealWithUrl）：
     * pics=漫画图片列表 / x5=webview规则 / web=网页 / image=图片查看 / magnet=分享。
     */
    private void handleDealUrl(String kind, String url, HkItem item) {
        if (TextUtils.isEmpty(url)) return;
        try {
            switch (kind) {
                case "pics": {
                    // pics://url1&&url2... → 图片列表查看
                    String raw = url.replaceFirst("(?i)^pics://", "");
                    String[] parts = raw.split("&&");
                    java.util.ArrayList<String> urls = new java.util.ArrayList<>();
                    for (String p : parts) {
                        if (!TextUtils.isEmpty(p.trim())) urls.add(p.trim());
                    }
                    if (urls.isEmpty()) {
                        android.widget.Toast.makeText(this, "无图片", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    android.widget.ImageView iv = new android.widget.ImageView(this);
                    com.fongmi.android.tv.utils.ImgUtil.load(item.getTitle(), urls.get(0), iv, false);
                    new AlertDialog.Builder(this)
                            .setTitle(stripHtml(item.getTitle()))
                            .setView(iv)
                            .setPositiveButton("关闭", (d, w) -> d.dismiss())
                            .show();
                    return;
                }
                case "x5": {
                    // x5://url → 内嵌 webview 规则页
                    String target = url.replaceFirst("(?i)^x5://", "").trim();
                    if (!target.isEmpty()) showWebViewDialog(target);
                    return;
                }
                case "web":
                    showWebViewDialog(url);
                    return;
                case "image": {
                    android.widget.ImageView iv = new android.widget.ImageView(this);
                    com.fongmi.android.tv.utils.ImgUtil.load(item.getTitle(), url, iv, false);
                    new AlertDialog.Builder(this)
                            .setView(iv)
                            .setPositiveButton("关闭", (d, w) -> d.dismiss())
                            .show();
                    return;
                }
                case "magnet": {
                    android.content.Intent it2 = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url));
                    try {
                        startActivity(android.content.Intent.createChooser(it2, "选择应用打开"));
                    } catch (Throwable e) {
                        android.widget.Toast.makeText(this, "无应用可打开", android.widget.Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                default:
                    break;
            }
        } catch (Throwable e) {
            android.util.Log.d("HkPage", "handleDealUrl failed: " + e.getMessage());
        }
        // 未知种类：回退 V4
        pushView(V_DETAIL);
        showDetailResult(null);
    }

    /** V4 结果展示：无线路但有标题/封面/简介时仍渲染，不直接"加载失败"。 */
    private void showDetailResult(HkDetail result) {
        if (result == null || (result.isEmpty() && !result.hasBasicInfo())) {
            binding.detailScroll.setVisibility(View.GONE);
            binding.tvDetailEmpty.setVisibility(View.VISIBLE);
        } else {
            bindDetail(result);
        }
    }

    private void bindDetail(HkDetail detail) {
        currentDetail = detail;
        binding.detailScroll.setVisibility(View.VISIBLE);
        binding.tvDetailEmpty.setVisibility(View.GONE);
        binding.tvDetailTitle.setText(detail.getTitle());
        binding.tvDetailName.setText(detail.getTitle());
        ImgUtil.load(detail.getTitle(), detail.getPic(), binding.ivDetailCover);
        int epCount = 0;
        for (HkDetail.Line l : detail.getLines()) epCount += l.getEpisodes().size();
        String meta = (currentRule == null ? "" : currentRule.getTitle() + " · ")
                + detail.getLines().size() + "条线路 · 共" + epCount + "集";
        binding.tvDetailMeta.setText(meta);
        String content = detail.getContent();
        boolean hasContent = !TextUtils.isEmpty(content);
        binding.tvDetailIntroLabel.setVisibility(hasContent ? View.VISIBLE : View.GONE);
        binding.tvDetailContent.setVisibility(hasContent ? View.VISIBLE : View.GONE);
        if (hasContent) binding.tvDetailContent.setText(content);
        renderCopyButtons(detail.getCopyItems());
        List<HkDetail.Line> lines = detail.getLines();
        boolean hasLines = !lines.isEmpty();
        // 无线路时隐藏线路/选集区，只展示标题/封面/简介（有基本信息才走到这里）
        binding.tvDetailLineLabel.setVisibility(View.GONE);
        binding.rvDetailLines.setVisibility(View.GONE);
        if (hasLines) {
            List<String[]> pairs = new ArrayList<>();
            for (HkDetail.Line l : lines) pairs.add(new String[]{l.getName(), l.getName()});
            binding.rvDetailLines.setAdapter(new ChipAdapter(pairs, 0, value -> {
                if (currentDetail == null) return;
                for (HkDetail.Line l : currentDetail.getLines()) {
                    if (l.getName().equals(value)) {
                        currentLine = l;
                        refreshEpisodes();
                        break;
                    }
                }
            }));
            boolean multiLine = lines.size() > 1;
            binding.tvDetailLineLabel.setVisibility(multiLine ? View.VISIBLE : View.GONE);
            binding.rvDetailLines.setVisibility(multiLine ? View.VISIBLE : View.GONE);
            currentLine = lines.get(0);
            refreshEpisodes();
        } else {
            currentLine = null;
            if (episodeAdapter != null) episodeAdapter.notifyDataSetChanged();
        }
        binding.detailScroll.scrollTo(0, 0);
    }

    /**
     * V4 复制按钮行：简介下方、选集上方横向排列，每 copy 条目一个白色描边按钮；
     * 点击复制 getText() 到剪贴板并 Toast"已复制"。无条目时整行隐藏。
     */
    private void renderCopyButtons(List<HkDetail.CopyItem> items) {
        binding.copyRow.removeAllViews();
        if (items == null || items.isEmpty()) {
            binding.copyScroll.setVisibility(View.GONE);
            return;
        }
        binding.copyScroll.setVisibility(View.VISIBLE);
        for (HkDetail.CopyItem ci : items) {
            TextView btn = new TextView(this);
            btn.setText(ci.getName());
            btn.setTextColor(0xFF1A1D24);
            btn.setTextSize(13);
            btn.setGravity(Gravity.CENTER);
            btn.setMaxLines(1);
            btn.setEllipsize(TextUtils.TruncateAt.END);
            int padH = dp(16), padV = dp(9);
            btn.setPadding(padH, padV, padH, padV);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFFFFFFFF);
            bg.setStroke(dp(1), 0xFFE0E4EA);
            bg.setCornerRadius(dp(10));
            btn.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(10);
            binding.copyRow.addView(btn, lp);
            btn.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("hk_copy", ci.getText()));
                    Notify.show("已复制");
                }
            });
        }
    }

    private void refreshEpisodes() {
        episodes.clear();
        if (currentLine != null) episodes.addAll(currentLine.getEpisodes());
        episodeAdapter.notifyDataSetChanged();
        binding.tvDetailEpLabel.setText(episodes.isEmpty() ? "选集" : "选集（" + episodes.size() + "）");
    }

    private class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            TextView tv;

            Holder(View v) {
                super(v);
                tv = v.findViewById(R.id.tv_episode);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hk_episode, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            HkDetail.Episode ep = episodes.get(position);
            h.tv.setText(ep.getName());
            h.itemView.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || currentRule == null || currentLine == null) return;
                HkDetail.Episode e = episodes.get(pos);
                String title = detailItem != null && !TextUtils.isEmpty(detailItem.getTitle())
                        ? detailItem.getTitle()
                        : (currentDetail == null ? "" : currentDetail.getTitle());
                String pic = detailItem != null ? detailItem.getPic()
                        : (currentDetail == null ? "" : currentDetail.getPic());
                VideoActivity.startHkPlay(HkPageActivity.this,
                        currentRule.getTitle(), currentLine.getName(),
                        e.getUrl(), e.getName(), title, pic);
            });
        }

        @Override
        public int getItemCount() {
            return episodes.size();
        }
    }

    /** 选集 4 列网格间距。 */
    private class EpSpace extends RecyclerView.ItemDecoration {
        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int pos = parent.getChildAdapterPosition(view);
            if (pos < 0) return;
            int col = pos % 4;
            int h = dp(8);
            int v = dp(8);
            outRect.left = col == 0 ? 0 : h / 2;
            outRect.right = col == 3 ? 0 : h / 2;
            if (pos >= 4) outRect.top = v;
        }
    }

    // ================= 通用组件 =================

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 内容网格间距：按实际 span/spanIndex 算列边距；兼容 3 列搜索网格与 12 列内容网格。 */
    private class GridSpace extends RecyclerView.ItemDecoration {
        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int pos = parent.getChildAdapterPosition(view);
            if (pos < 0) return;
            int total = 12, span = 12, spanIndex = 0;
            if (parent.getLayoutManager() instanceof GridLayoutManager) {
                total = ((GridLayoutManager) parent.getLayoutManager()).getSpanCount();
            }
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) view.getLayoutParams();
            if (lp instanceof GridLayoutManager.LayoutParams) {
                GridLayoutManager.LayoutParams glp = (GridLayoutManager.LayoutParams) lp;
                span = glp.getSpanSize();
                if (glp.getSpanIndex() >= 0) spanIndex = glp.getSpanIndex();
            }
            int h = dp(12);
            if (span >= total) {
                outRect.left = 0;
                outRect.right = 0;
                if (pos > 0) outRect.top = dp(12);
            } else {
                int perRow = Math.max(1, total / Math.max(1, span));
                int col = span > 0 ? spanIndex / span : 0;
                outRect.left = col == 0 ? 0 : h / 2;
                outRect.right = col == perRow - 1 ? 0 : h / 2;
                if (pos >= perRow) outRect.top = dp(16);
            }
        }
    }

    private class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.Holder> {
        private final List<HkItem> items;
        private final boolean fromContent;
        private final int imgHeight;

        VideoAdapter(List<HkItem> items, boolean fromContent) {
            this.items = items;
            this.fromContent = fromContent;
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int itemW = (dm.widthPixels - dp(16) * 2 - dp(12) * 2) / 3;
            imgHeight = itemW * 3 / 2;
        }

        class Holder extends RecyclerView.ViewHolder {
            ImageView cover;
            TextView title, desc;

            Holder(View v) {
                super(v);
                cover = v.findViewById(R.id.iv_cover);
                title = v.findViewById(R.id.tv_title);
                desc = v.findViewById(R.id.tv_desc);
                ViewGroup.LayoutParams lp = cover.getLayoutParams();
                lp.height = imgHeight;
                cover.setLayoutParams(lp);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hk_video, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            HkItem item = items.get(position);
            h.title.setText(item.getTitle());
            h.desc.setText(item.getDesc());
            h.desc.setVisibility(TextUtils.isEmpty(item.getDesc()) ? View.GONE : View.VISIBLE);
            ImgUtil.load(item.getTitle(), item.getPic(), h.cover);
            h.itemView.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || currentRule == null) return;
                HkItem it = items.get(pos);
                String mu = it.getUrl() == null ? "" : it.getUrl();
                openDetail(it, !fromContent, mu.contains("@lazyRule="));
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    /** col_type 小写形式（js 结果里填充，未设置则空串）。 */
    private String colTypeOf(HkItem it) {
        return it.getColType() == null ? "" : it.getColType().trim().toLowerCase();
    }

    /** 去 HTML 标签（col_type 标题里常带 <font> 等），逻辑同 buildDynamicTabs。 */
    private String stripHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]*>", "").replace("‘", "").replace("’", "").trim()
                // 规则里常把标题包在引号里（"""最新上传"""），去掉首尾引号
                .replaceAll("^\"+|\"+$", "").replaceAll("^'+|'+$", "").trim();
    }

    /**
     * 标题颜色标记检测（官方约定）："""xxx""" → 红色，''xxx'' → 橙色，并去掉引号。
     * 返回颜色值，无标记返回 0。富文本路径复用。
     */
    private int titleMarkColor(String raw) {
        String t = raw == null ? "" : raw.replaceAll("<[^>]*>", "").trim();
        if (t.length() >= 4 && t.startsWith("\"\"") && t.endsWith("\"\"")) return 0xFFE53935;
        if (t.length() >= 4 && t.startsWith("''") && t.endsWith("''")) return 0xFFFF9800;
        return 0;
    }

    private CharSequence titleSpan(String raw) {
        String s = stripHtml(raw);
        int color = titleMarkColor(raw);
        if (color == 0) return s;
        android.text.SpannableString sp = new android.text.SpannableString(s);
        sp.setSpan(new android.text.style.ForegroundColorSpan(color), 0, s.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    /** col_type → viewType：空/movie_3/未知 → 视频卡片。 */
    private int contentTypeOf(HkItem it) {
        String ct = colTypeOf(it);
        switch (ct) {
            case "text_center_1":
            case "text_1":
            case "long_text":
                return ContentAdapter.T_TEXT;
            case "rich_text":
                return ContentAdapter.T_RICH;
            case "text_2":
            case "text_3":
            case "text_4":
            case "text_5":
                return ContentAdapter.T_COLS;
            case "avatar":
                return ContentAdapter.T_AVATAR;
            case "input":
                return ContentAdapter.T_INPUT;
            case "search":
                return ContentAdapter.T_SEARCH;
            case "x5_webview_single":
                return ContentAdapter.T_WEB;
            case "button_group":
                return ContentAdapter.T_BUTTONS;
            case "":
            case "movie_3":
                return ContentAdapter.T_VIDEO;
            default:
                if (ct.startsWith("icon")) return ContentAdapter.T_ICON;
                if (ct.startsWith("pic")) return ContentAdapter.T_PIC;
                // movie_1/movie_2 等按视频卡片网格渲染（原版粉嫩小BB为 3 列网格，
                // 全宽横向图文与原版差距大）
                if (ct.startsWith("movie")) return ContentAdapter.T_VIDEO;
                return ContentAdapter.T_VIDEO;
        }
    }

    /**
     * V2 非视频条目的统一点击：无 url 的纯展示行无反应；有 url 先过动作协议，
     * 未消费则走 openDetail。条目自带 {@code @lazyRule=} 时用 lazy 预检模式，
     * 直接播放就不推 V4（粉嫩小BB点封面即播，不闪 V4 loading）。
     */
    private void onContentItemClick(HkItem it) {
        if (it == null || currentRule == null || TextUtils.isEmpty(it.getUrl())) return;
        String url = it.getUrl().trim();
        if (handleActionUrl(url, it)) return;
        openDetail(it, true, url.contains("@lazyRule="));
    }

    /**
     * P2：条目 URL 动作协议的基本处理。返回 true 表示已消费，不再走 openDetail。
     * <ul>
     *   <li>{@code toast://文本} → Toast 显示</li>
     *   <li>{@code copy://文本} → 复制到剪贴板</li>
     *   <li>{@code input://...}/{@code select://...}/{@code confirm://...} → 对应对话框（基本处理）</li>
     *   <li>{@code msg@confirmRule=js:...} → $().confirm 序列化：弹确认框，确认后求值 JS</li>
     *   <li>{@code @inputRule=.js:...} → $().input 序列化：弹输入框，输入后求值 JS（input 为输入值）</li>
     *   <li>{@code @x5Rule=js:...} → $().x5Rule 序列化：求值取 URL，用 WebView 打开</li>
     * </ul>
     */
    private boolean handleActionUrl(String url, HkItem it) {
        try {
            if (url.startsWith("toast://")) {
                android.widget.Toast.makeText(this, url.substring(8), android.widget.Toast.LENGTH_SHORT).show();
                return true;
            }
            if (url.startsWith("copy://")) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("hk", url.substring(7)));
                android.widget.Toast.makeText(this, "已复制", android.widget.Toast.LENGTH_SHORT).show();
                return true;
            }
            if (url.startsWith("confirm://")) {
                String msg = url.substring(10);
                int q = msg.indexOf('?');
                if (q >= 0) msg = msg.substring(0, q);
                new AlertDialog.Builder(this)
                        .setMessage(msg.isEmpty() ? "确认？" : msg)
                        .setPositiveButton("确定", (d, w) -> d.dismiss())
                        .setNegativeButton("取消", (d, w) -> d.dismiss())
                        .show();
                return true;
            }
            if (url.startsWith("input://") || url.startsWith("select://")) {
                boolean isInput = url.startsWith("input://");
                showSimpleInputDialog(isInput ? "输入" : "选择", "", input -> {
                    android.widget.Toast.makeText(this,
                            input.isEmpty() ? "已取消" : "输入：" + input,
                            android.widget.Toast.LENGTH_SHORT).show();
                });
                return true;
            }
            int ci = url.indexOf("@confirmRule=");
            if (ci >= 0) {
                String msg = url.substring(0, ci);
                String js = url.substring(ci + 13).trim();
                if (js.startsWith("js:")) js = js.substring(3);
                final String code = js;
                new AlertDialog.Builder(this)
                        .setMessage(msg.isEmpty() ? "确认？" : msg)
                        .setPositiveButton("确定", (d, w) -> {
                            d.dismiss();
                            evalActionJs(code, "");
                        })
                        .setNegativeButton("取消", (d, w) -> d.dismiss())
                        .show();
                return true;
            }
            int ii = url.indexOf("@inputRule=");
            if (ii >= 0) {
                String js = url.substring(ii + 11).trim();
                if (js.startsWith(".js:")) js = js.substring(4);
                else if (js.startsWith("js:")) js = js.substring(3);
                final String code = js;
                showSimpleInputDialog(it.getTitle(), "", input -> evalActionJs(code, input));
                return true;
            }
            int xi = url.indexOf("@x5Rule=");
            if (xi >= 0) {
                String js = url.substring(xi + 8).trim();
                if (js.startsWith("js:")) js = js.substring(3);
                final String code = js;
                new Thread(() -> {
                    String target = "";
                    try {
                        target = getRouter().getEngine().getJsRuntime().evalLazy(code, "");
                    } catch (Throwable ignored) {
                    }
                    final String t = target == null ? "" : target.trim();
                    App.post(() -> {
                        if (t.startsWith("http")) showWebViewDialog(t);
                        else if (!t.isEmpty())
                            android.widget.Toast.makeText(this, t, android.widget.Toast.LENGTH_SHORT).show();
                    });
                }).start();
                return true;
            }
        } catch (Throwable e) {
            android.util.Log.d("HkPage", "handleActionUrl failed: " + e.getMessage());
        }
        return false;
    }

    /** 在 JS 线程求值动作 JS（@confirmRule/@inputRule 回调），input 为输入值。 */
    private void evalActionJs(String code, String input) {
        new Thread(() -> {
            String errMsg = null;
            String result = "";
            try {
                HkJsRuntime rt = getRouter().getEngine().getJsRuntime();
                // 官方语义：input 全局注入后直接求值。不用 evalLazy + 局部 var 包裹，
                // 否则规则顶层语句在 return(...) 包裹下报语法错误，异常被静默吞掉导致"点了没反应"。
                result = rt.eval(code, input);
                // 取走 refreshPage 请求标记（不清零会污染后续 tab 点击的判断）
                rt.consumeRefreshRequest();
                String jsErr = rt.getError();
                if (jsErr != null && !jsErr.isEmpty()) errMsg = jsErr;
            } catch (Throwable e) {
                errMsg = e.getMessage() == null || e.getMessage().isEmpty() ? "执行失败" : e.getMessage();
                android.util.Log.d("HkPage", "evalActionJs failed: " + e.getMessage());
            }
            String r0 = result == null ? "" : result.trim();
            final String r = ("undefined".equals(r0) || "null".equals(r0)) ? "" : r0;
            final String em = errMsg;
            App.post(() -> {
                if (em != null && !em.isEmpty()) {
                    android.widget.Toast.makeText(HkPageActivity.this,
                            "执行失败：" + em, android.widget.Toast.LENGTH_SHORT).show();
                }
                // 回调返回非空 URL 则导航（hiker://empty 为官方"无跳转"标记，hiker://* 为内部标记，均不导航）
                if (!r.isEmpty() && !"hiker://empty".equals(r) && !r.startsWith("hiker://")) {
                    HkItem nav = new HkItem();
                    nav.setTitle("");
                    nav.setUrl(r);
                    if (handleActionUrl(r, nav)) return;
                    onContentItemClick(nav);
                    return;
                }
                loadContent(true);
            });
        }).start();
    }

    /** 简单输入对话框（input:// 与 @inputRule= 共用）。 */
    private void showSimpleInputDialog(String title, String def, java.util.function.Consumer<String> cb) {
        final EditText et = new EditText(this);
        et.setHint("请输入");
        if (!TextUtils.isEmpty(def)) et.setText(def);
        et.setTextColor(0xFF1A1D24);
        int pad = dp(16);
        et.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle(title == null || title.isEmpty() ? "输入" : title)
                .setView(et)
                .setPositiveButton("确定", (d, w) -> {
                    d.dismiss();
                    cb.accept(et.getText().toString().trim());
                })
                .setNegativeButton("取消", (d, w) -> d.dismiss())
                .show();
    }

    /** WebView 弹窗（@x5Rule= 与 x5_webview_single 共用）。 */
    private void showWebViewDialog(String url) {
        try {
            android.webkit.WebView wv = new android.webkit.WebView(this);
            wv.getSettings().setJavaScriptEnabled(true);
            wv.getSettings().setDomStorageEnabled(true);
            wv.setWebViewClient(new android.webkit.WebViewClient());
            wv.loadUrl(url);
            new AlertDialog.Builder(this)
                    .setView(wv)
                    .setPositiveButton("关闭", (d, w) -> {
                        try { wv.destroy(); } catch (Throwable ignored) {}
                        d.dismiss();
                    })
                    .show();
        } catch (Throwable e) {
            android.widget.Toast.makeText(this, "打开网页失败", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** V2 内容多 viewType 适配器：视频卡片/小图标占 1 列，其余占满 3 列。 */
    private class ContentAdapter extends RecyclerView.Adapter<ContentAdapter.Holder> {
        static final int T_VIDEO = 0;
        static final int T_TEXT = 1;
        static final int T_RICH = 2;
        static final int T_COLS = 3;
        static final int T_AVATAR = 4;
        static final int T_ICON = 5;
        static final int T_PIC = 6;
        static final int T_MOVIE = 7;
        static final int T_INPUT = 8;
        static final int T_SEARCH = 9;
        static final int T_WEB = 10;
        static final int T_BUTTONS = 11;

        private final List<HkItem> source;
        /** 分组后的展示列表（groupButtons 快照；videos 变更后必须调 refreshGroups() 重算）。 */
        private List<HkItem> items;
        /** 按钮组：合成条目 → 子条目列表（连续 scroll_button/flex_button 的横向胶囊行）。 */
        private final java.util.Map<HkItem, List<HkItem>> buttonGroups = new java.util.HashMap<>();
        private final int videoImgH;
        private final int picH;

        ContentAdapter(List<HkItem> items) {
            this.source = items;
            this.items = groupButtons(items);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int itemW = (dm.widthPixels - dp(16) * 2 - dp(12) * 2) / 3;
            videoImgH = itemW * 3 / 2;
            picH = (dm.widthPixels - dp(16) * 2) * 9 / 16;
        }

        /**
         * videos 增删后重算按钮分组。groupButtons 返回的是快照，不调此方法
         * 适配器将恒显示首次构造时的空列表（V2"分类页没数据"的根因）。
         */
        void refreshGroups() {
            buttonGroups.clear();
            this.items = groupButtons(source);
        }

        /**
         * 把连续的 scroll_button/flex_button 条目合并为一个横向胶囊行
         * （正常应已被 loadContent 拆为顶部 tab；这里是兜底）。
         */
        private List<HkItem> groupButtons(List<HkItem> src) {
            List<HkItem> out = new ArrayList<>();
            List<HkItem> buf = new ArrayList<>();
            for (HkItem it : src) {
                String ct = it.getColType() == null ? "" : it.getColType().trim().toLowerCase();
                if ("scroll_button".equals(ct) || "flex_button".equals(ct)) {
                    buf.add(it);
                } else {
                    if (!buf.isEmpty()) {
                        out.add(makeButtonGroup(buf));
                        buf = new ArrayList<>();
                    }
                    out.add(it);
                }
            }
            if (!buf.isEmpty()) out.add(makeButtonGroup(buf));
            return out;
        }

        private HkItem makeButtonGroup(List<HkItem> subs) {
            HkItem g = new HkItem();
            g.setColType("button_group");
            g.setTitle("");
            buttonGroups.put(g, new ArrayList<>(subs));
            return g;
        }

        /**
         * 官方 12 列栅格的 span（ArticleColTypeEnum.spanCount）：
         * movie_3=4（3列）；icon_4/icon_small_4/icon_round_4/icon_4_card=3（4个一行，如探色
         * 的首页/抖阴/二次元/暗网导航按钮）；icon_2/icon_2_round=6（2个一行）；
         * icon_small_3=4（3个一行）；icon_1_search=12（全宽搜索）；pic_1系列=12；
         * pic_2=6；pic_3/pic_3_square=4；其余全宽=12。
         */
        int spanFor(int position) {
            if (position < 0 || position >= items.size()) return 12;
            HkItem it = items.get(position);
            int t = getItemViewType(position);
            String ct = it.getColType() == null ? "" : it.getColType().trim().toLowerCase();
            switch (t) {
                case T_VIDEO:
                    return 4;
                case T_ICON:
                    if (ct.startsWith("icon_2")) return 6;
                    if ("icon_small_3".equals(ct)) return 4;
                    if ("icon_1_search".equals(ct)) return 12;
                    return 3; // icon_4 / icon_small_4 / icon_round_4 / icon_round_small_4 / icon_4_card
                case T_PIC:
                    if (ct.startsWith("pic_2")) return 6;
                    if (ct.startsWith("pic_3")) return 4;
                    return 12; // pic_1 / pic_1_full / pic_1_card
                default:
                    return 12;
            }
        }

        class Holder extends RecyclerView.ViewHolder {
            ImageView cover;
            TextView title, desc;
            LinearLayout cols;
            EditText input;
            android.webkit.WebView web;

            Holder(View v, int type) {
                super(v);
                switch (type) {
                    case T_VIDEO:
                        cover = v.findViewById(R.id.iv_cover);
                        title = v.findViewById(R.id.tv_title);
                        desc = v.findViewById(R.id.tv_desc);
                        ViewGroup.LayoutParams vlp = cover.getLayoutParams();
                        vlp.height = videoImgH;
                        cover.setLayoutParams(vlp);
                        break;
                    case T_TEXT:
                    case T_RICH:
                        title = v.findViewById(R.id.tv_title);
                        desc = v.findViewById(R.id.tv_desc);
                        break;
                    case T_COLS:
                    case T_BUTTONS:
                        cols = v.findViewById(R.id.cols_container);
                        break;
                    case T_AVATAR:
                        cover = v.findViewById(R.id.iv_avatar);
                        title = v.findViewById(R.id.tv_title);
                        cover.setClipToOutline(true);
                        cover.setOutlineProvider(new ViewOutlineProvider() {
                            @Override
                            public void getOutline(View view, Outline outline) {
                                outline.setOval(0, 0, view.getWidth(), view.getHeight());
                            }
                        });
                        break;
                    case T_ICON:
                        cover = v.findViewById(R.id.iv_icon);
                        title = v.findViewById(R.id.tv_title);
                        break;
                    case T_PIC:
                        cover = v.findViewById(R.id.iv_pic);
                        ViewGroup.LayoutParams plp = cover.getLayoutParams();
                        plp.height = picH;
                        cover.setLayoutParams(plp);
                        break;
                    case T_MOVIE:
                        cover = v.findViewById(R.id.iv_cover);
                        title = v.findViewById(R.id.tv_title);
                        desc = v.findViewById(R.id.tv_desc);
                        break;
                    case T_INPUT:
                        input = v.findViewById(R.id.et_input);
                        break;
                    case T_SEARCH:
                        title = v.findViewById(R.id.tv_title);
                        break;
                    case T_WEB:
                        web = v.findViewById(R.id.wv_page);
                        web.getSettings().setJavaScriptEnabled(true);
                        web.getSettings().setDomStorageEnabled(true);
                        web.setWebViewClient(new android.webkit.WebViewClient());
                        break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            return contentTypeOf(items.get(position));
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout;
            switch (viewType) {
                case T_TEXT:
                case T_RICH:
                    layout = R.layout.item_hk_text;
                    break;
                case T_COLS:
                    layout = R.layout.item_hk_cols;
                    break;
                case T_BUTTONS:
                    layout = R.layout.item_hk_cols;
                    break;
                case T_AVATAR:
                    layout = R.layout.item_hk_avatar;
                    break;
                case T_ICON:
                    layout = R.layout.item_hk_icon;
                    break;
                case T_PIC:
                    layout = R.layout.item_hk_pic;
                    break;
                case T_MOVIE:
                    layout = R.layout.item_hk_movie;
                    break;
                case T_INPUT:
                    layout = R.layout.item_hk_input;
                    break;
                case T_SEARCH:
                    layout = R.layout.item_hk_search;
                    break;
                case T_WEB:
                    layout = R.layout.item_hk_web;
                    break;
                default:
                    layout = R.layout.item_hk_video;
                    break;
            }
            View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new Holder(v, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            HkItem item = items.get(position);
            switch (getItemViewType(position)) {
                case T_VIDEO:
                    bindVideo(h, item);
                    break;
                case T_TEXT:
                    bindText(h, item);
                    break;
                case T_RICH:
                    bindRich(h, item);
                    break;
                case T_COLS:
                    bindCols(h, item);
                    break;
                case T_BUTTONS:
                    bindButtons(h, item);
                    break;
                case T_AVATAR:
                    bindAvatar(h, item);
                    break;
                case T_ICON:
                    bindIcon(h, item);
                    break;
                case T_PIC:
                    bindPic(h, item);
                    break;
                case T_MOVIE:
                    bindMovie(h, item);
                    break;
                case T_INPUT:
                    bindInput(h, item);
                    break;
                case T_SEARCH:
                    bindSearch(h, item);
                    break;
                case T_WEB:
                    bindWeb(h, item);
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        /** 视频卡片：保持原 VideoAdapter 行为。 */
        private void bindVideo(Holder h, HkItem item) {
            h.title.setText(titleSpan(item.getTitle()));
            h.desc.setText(stripHtml(item.getDesc()));
            h.desc.setVisibility(TextUtils.isEmpty(item.getDesc()) ? View.GONE : View.VISIBLE);
            ImgUtil.load(item.getTitle(), item.getPic(), h.cover);
            h.itemView.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || currentRule == null) return;
                HkItem it = items.get(pos);
                String u = it.getUrl() == null ? "" : it.getUrl();
                openDetail(it, true, u.contains("@lazyRule="));
            });
        }

        /** 文本行：text_center_1 居中灰字；text_1 去标签标题+描述；long_text 多行。 */
        private void bindText(Holder h, HkItem item) {
            String ct = colTypeOf(item);
            if ("text_center_1".equals(ct)) {
                h.title.setGravity(Gravity.CENTER);
                h.title.setTextColor(0xFF4F555F);
                h.title.setTextSize(13);
                h.title.setMaxLines(4);
                h.title.setText(titleSpan(item.getTitle()));
                h.desc.setVisibility(View.GONE);
            } else {
                boolean longText = "long_text".equals(ct);
                h.title.setGravity(Gravity.START);
                h.title.setTextColor(0xFF1A1D24);
                h.title.setTextSize(14);
                h.title.setMaxLines(longText ? 30 : 3);
                h.title.setText(titleSpan(item.getTitle()));
                String d = stripHtml(item.getDesc());
                h.desc.setText(d);
                h.desc.setMaxLines(longText ? 60 : 5);
                h.desc.setVisibility(TextUtils.isEmpty(d) ? View.GONE : View.VISIBLE);
            }
            setContentClick(h, item);
        }

        /** 富文本行：Html.fromHtml 显示，失败回退去标签；标题颜色标记（"""红/''橙）同样生效。 */
        private void bindRich(Holder h, HkItem item) {
            h.title.setGravity(Gravity.START);
            h.title.setTextColor(0xFF1A1D24);
            h.title.setTextSize(14);
            h.title.setMaxLines(30);
            int markColor = titleMarkColor(item.getTitle());
            String rawTitle = item.getTitle() == null ? "" : item.getTitle();
            if (markColor != 0) {
                // 先去掉首尾引号标记再走 Html，避免引号原样显示
                rawTitle = rawTitle.replaceAll("^\"+|\"+$", "").replaceAll("^'+|'+$", "");
            }
            try {
                CharSequence cs = Html.fromHtml(rawTitle, Html.FROM_HTML_MODE_LEGACY);
                if (markColor != 0) {
                    android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(cs);
                    ssb.setSpan(new android.text.style.ForegroundColorSpan(markColor), 0, ssb.length(),
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    h.title.setText(ssb);
                } else {
                    h.title.setText(cs);
                }
            } catch (Throwable t) {
                h.title.setText(titleSpan(item.getTitle()));
            }
            String d = item.getDesc();
            if (TextUtils.isEmpty(d)) {
                h.desc.setVisibility(View.GONE);
            } else {
                h.desc.setVisibility(View.VISIBLE);
                try {
                    h.desc.setText(Html.fromHtml(d, Html.FROM_HTML_MODE_LEGACY));
                } catch (Throwable t) {
                    h.desc.setText(stripHtml(d));
                }
            }
            setContentClick(h, item);
        }

        /** 多列文本：按连续空白/｜/，切分标题为 N 列；切不出则按普通文本行。 */
        private void bindCols(Holder h, HkItem item) {
            h.cols.removeAllViews();
            String ct = colTypeOf(item);
            int n = 2;
            if (!ct.isEmpty()) {
                char c = ct.charAt(ct.length() - 1);
                if (c >= '2' && c <= '5') n = c - '0';
            }
            String raw = item.getTitle() == null ? "" : item.getTitle();
            List<String> parts = new ArrayList<>();
            for (String p : raw.split("[\\s　｜|，,、;；]+")) {
                p = stripHtml(p);
                if (!p.isEmpty()) parts.add(p);
            }
            Context ctx = h.itemView.getContext();
            if (parts.size() >= n) {
                for (int i = 0; i < n; i++) {
                    TextView tv = new TextView(ctx);
                    tv.setText(parts.get(i));
                    tv.setTextColor(0xFF1A1D24);
                    tv.setTextSize(14);
                    tv.setGravity(Gravity.CENTER);
                    tv.setMaxLines(2);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                    h.cols.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                }
            } else {
                TextView tv = new TextView(ctx);
                tv.setText(stripHtml(raw));
                tv.setTextColor(0xFF1A1D24);
                tv.setTextSize(14);
                tv.setMaxLines(3);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                h.cols.addView(tv, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            setContentClick(h, item);
        }

        /** 流式胶囊按钮组：连续 scroll_button/flex_button 聚合为自动换行的流式布局（官方 flex_button）。 */
        private void bindButtons(Holder h, HkItem item) {
            h.cols.removeAllViews();
            List<HkItem> subs = buttonGroups.get(item);
            if (subs == null || subs.isEmpty()) return;
            Context ctx = h.itemView.getContext();
            int parentW = h.cols.getWidth();
            if (parentW <= 0) {
                parentW = ctx.getResources().getDisplayMetrics().widthPixels - dp(16) * 2;
            }
            LinearLayout flow = new LinearLayout(ctx);
            flow.setOrientation(LinearLayout.VERTICAL);
            LinearLayout row = newButtonRow(ctx);
            int rowW = 0;
            int padH = dp(14), padV = dp(7);
            for (HkItem sub : subs) {
                TextView tv = new TextView(ctx);
                tv.setText(titleSpan(sub.getTitle()));
                tv.setTextColor(0xFF1A1D24);
                tv.setTextSize(13);
                tv.setGravity(Gravity.CENTER);
                tv.setMaxLines(1);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setPadding(padH, padV, padH, padV);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xFFF1F3F6);
                bg.setCornerRadius(dp(14));
                tv.setBackground(bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = dp(8);
                lp.bottomMargin = dp(8);
                tv.setLayoutParams(lp);
                tv.setOnClickListener(v -> onContentItemClick(sub));
                tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                int w = tv.getMeasuredWidth() + dp(8);
                if (rowW + w > parentW && rowW > 0) {
                    flow.addView(row, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    row = newButtonRow(ctx);
                    rowW = 0;
                }
                row.addView(tv);
                rowW += w;
            }
            flow.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            h.cols.addView(flow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        private LinearLayout newButtonRow(Context ctx) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            return row;
        }

        /** 头像行：圆形图 + 标题横向。 */
        private void bindAvatar(Holder h, HkItem item) {
            h.title.setText(titleSpan(item.getTitle()));
            ImgUtil.load(item.getTitle(), item.getPic(), h.cover);
            setContentClick(h, item);
        }

        /** 小图标按钮：图片+文字居中，占 1 列。 */
        private void bindIcon(Holder h, HkItem item) {
            h.title.setText(titleSpan(item.getTitle()));
            ImgUtil.load(item.getTitle(), item.getPic(), h.cover);
            setContentClick(h, item);
        }

        /** 图片：pic_1系列全宽16:9；pic_2半宽16:9；pic_3/pic_3_square按列宽正方形。 */
        private void bindPic(Holder h, HkItem item) {
            String ct = colTypeOf(item);
            ViewGroup.LayoutParams plp = h.cover.getLayoutParams();
            if (plp != null) {
                DisplayMetrics dm = h.itemView.getContext().getResources().getDisplayMetrics();
                int W = dm.widthPixels - dp(16) * 2;
                if (ct.startsWith("pic_3")) {
                    plp.height = (W - dp(12) * 2) / 3; // 3列正方形
                } else if (ct.startsWith("pic_2")) {
                    int itemW = (W - dp(12)) / 2;
                    plp.height = itemW * 9 / 16;
                } else {
                    plp.height = picH; // 16:9 全宽
                }
                h.cover.setLayoutParams(plp);
            }
            ImgUtil.load(item.getTitle(), item.getPic(), h.cover);
            h.cover.setContentDescription(stripHtml(item.getTitle()));
            setContentClick(h, item);
        }

        /** 横向图文：左图右文。 */
        private void bindMovie(Holder h, HkItem item) {
            h.title.setText(titleSpan(item.getTitle()));
            String d = stripHtml(item.getDesc());
            h.desc.setText(d);
            h.desc.setVisibility(TextUtils.isEmpty(d) ? View.GONE : View.VISIBLE);
            ImgUtil.load(item.getTitle(), item.getPic(), h.cover);
            setContentClick(h, item);
        }

        /** 输入框行：hint 取标题；搜索键触发且有 url 时走点击处理。 */
        private void bindInput(Holder h, HkItem item) {
            h.input.setHint(stripHtml(item.getTitle()));
            h.input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    if (!TextUtils.isEmpty(item.getUrl())) onContentItemClick(item);
                    return true;
                }
                return false;
            });
        }

        /** 搜索框行：假输入框样式，点击进 V3 搜索。 */
        private void bindSearch(Holder h, HkItem item) {
            String t = stripHtml(item.getTitle());
            h.title.setText(TextUtils.isEmpty(t) ? "搜索…" : t);
            h.itemView.setOnClickListener(v -> openSearch());
        }

        /** P2：x5_webview_single 内嵌网页（JRKAN直播这类网页版小程序），点击整行用外部 WebView 打开。 */
        private void bindWeb(Holder h, HkItem item) {
            String url = item.getUrl() == null ? "" : item.getUrl().trim();
            // url 可能带选择器后缀，只取纯 http 部分
            int sp = url.indexOf(' ');
            if (sp > 0) url = url.substring(0, sp).trim();
            int semi = url.indexOf(';');
            if (semi > 0) url = url.substring(0, semi).trim();
            int hash = url.indexOf('#');
            if (hash > 0) url = url.substring(0, hash).trim();
            if (url.startsWith("http") && h.web != null) {
                h.web.loadUrl(url);
            }
            final String target = url;
            h.itemView.setOnClickListener(v -> {
                if (target.startsWith("http")) showWebViewDialog(target);
            });
        }

        /** 非视频条目点击：无 url 的纯展示行不设点击。 */
        private void setContentClick(Holder h, HkItem item) {
            if (TextUtils.isEmpty(item.getUrl())) {
                h.itemView.setOnClickListener(null);
                return;
            }
            h.itemView.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || currentRule == null) return;
                onContentItemClick(items.get(pos));
            });
        }
    }

    private static class ChipAdapter extends RecyclerView.Adapter<ChipAdapter.Holder> {
        interface OnPick {
            void onPick(String value);
        }

        private final List<String[]> pairs;
        private int selected;
        private final OnPick listener;

        ChipAdapter(List<String[]> pairs, int selected, OnPick listener) {
            this.pairs = pairs;
            this.selected = selected;
            this.listener = listener;
        }

        static class Holder extends RecyclerView.ViewHolder {
            TextView chip;

            Holder(View v) {
                super(v);
                chip = v.findViewById(R.id.tv_chip);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hk_chip, parent, false);
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) v.getLayoutParams();
            lp.rightMargin = (int) (8 * parent.getContext().getResources().getDisplayMetrics().density + 0.5f);
            v.setLayoutParams(lp);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            String[] pair = pairs.get(position);
            h.chip.setText(pair[0]);
            boolean sel = position == selected;
            h.chip.setSelected(sel);
            h.chip.setTextColor(sel ? 0xFF1A1D24 : 0xFF3A3F4B);
            h.chip.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                selected = pos;
                notifyDataSetChanged();
                if (listener != null) listener.onPick(pairs.get(pos)[1]);
            });
        }

        @Override
        public int getItemCount() {
            return pairs.size();
        }
    }
}
