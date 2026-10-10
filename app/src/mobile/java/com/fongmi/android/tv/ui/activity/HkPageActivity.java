package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    private VideoAdapter contentAdapter;
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
        if (router != null) {
            router.destroy();
            router = null;
        }
        super.onDestroy();
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

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_RULE = 1;
        private final List<Object> items = new ArrayList<>();

        /** 按 HkRule.getGroup() 分组：组头为 group 名，无 group 的归入"未分组"，组内保持原顺序。 */
        void rebuildSections() {
            items.clear();
            List<String> order = new ArrayList<>();
            Map<String, List<HkRule>> map = new HashMap<>();
            for (HkRule rule : rules) {
                String g = rule.getGroup();
                if (TextUtils.isEmpty(g)) g = "未分组";
                if (!map.containsKey(g)) {
                    map.put(g, new ArrayList<>());
                    order.add(g);
                }
                map.get(g).add(rule);
            }
            for (String g : order) {
                items.add(g);
                items.addAll(map.get(g));
            }
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

        class GroupHolder extends RecyclerView.ViewHolder {
            TextView title;

            GroupHolder(View v) {
                super(v);
                title = (TextView) v;
            }
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof String ? TYPE_HEADER : TYPE_RULE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                TextView tv = new TextView(parent.getContext());
                tv.setTextSize(14);
                tv.setTextColor(0xFFB8890F);
                tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
                int pad = dp(4);
                tv.setPadding(dp(4), dp(16), pad, pad);
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tv.setLayoutParams(lp);
                return new GroupHolder(tv);
            }
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hk_rule, parent, false);
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) v.getLayoutParams();
            lp.bottomMargin = dp(12);
            v.setLayoutParams(lp);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {
            if (vh instanceof GroupHolder) {
                ((GroupHolder) vh).title.setText((String) items.get(position));
                return;
            }
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
        contentGrid = new GridLayoutManager(this, 3);
        binding.rvVideos.setLayoutManager(contentGrid);
        binding.rvVideos.addItemDecoration(new GridSpace());
        contentAdapter = new VideoAdapter(videos, true);
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
        if (router != null) {
            router.destroy();
            router = null;
        }
        currentRule = rule;
        cls = "";
        area = "";
        year = "";
        sort = "";
        page = 1;
        noMore = false;
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
            String name = raw.replaceAll("<[^>]*>", "").replace("‘", "").replace("’", "").trim();
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
        tv.setTextColor(0xFF6E7686);
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

    private void loadContent(boolean reset) {
        if (loading || currentRule == null) return;
        loading = true;
        if (reset) {
            page = 1;
            noMore = false;
            videos.clear();
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
                loading = false;
                binding.swipeContent.setRefreshing(false);
                binding.loadingContent.setVisibility(View.GONE);
                // V2：把导航类条目（scroll_button/flex_button 分类）拆出来做顶部 tab，
                // 分隔块（blank_block/line）丢弃，只有内容条目进视频网格。
                List<HkItem> tabs = new ArrayList<>();
                List<HkItem> contents = new ArrayList<>();
                for (HkItem it : result) {
                    String ct = it.getColType() == null ? "" : it.getColType().trim();
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
        detailItem = item;
        detailFromSearch = fromSearch;
        currentDetail = null;
        currentLine = null;
        episodes.clear();
        if (episodeAdapter != null) episodeAdapter.notifyDataSetChanged();
        binding.tvDetailTitle.setText(item.getTitle());
        binding.detailScroll.setVisibility(View.GONE);
        binding.tvDetailEmpty.setVisibility(View.GONE);
        binding.tvDetailLoading.setVisibility(View.VISIBLE);
        pushView(V_DETAIL);
        new Thread(() -> {
            HkDetail detail;
            try {
                detail = getRouter().detail(item.getUrl(), item, fromSearch);
            } catch (Throwable e) {
                detail = null;
            }
            final HkDetail result = detail;
            App.post(() -> {
                binding.tvDetailLoading.setVisibility(View.GONE);
                String direct = result == null ? "" : result.getDirectPlayUrl();
                if (!TextUtils.isEmpty(direct)) {
                    // 条目自带 @lazyRule= 且求值为 #isVideo=true#：跳过 V4，直接播放
                    // （directPlayUrl 检查必须在 isEmpty() 之前：直接播放的 detail 无线路）
                    onBackInvoked(); // 弹出已 push 的 V_DETAIL，播放器返回时回到列表
                    VideoActivity.startHkPlay(HkPageActivity.this,
                            currentRule == null ? "" : currentRule.getTitle(), "默认",
                            direct, item.getTitle(), item.getTitle(), item.getPic());
                    return;
                }
                if (result == null || result.isEmpty()) {
                    binding.detailScroll.setVisibility(View.GONE);
                    binding.tvDetailEmpty.setVisibility(View.VISIBLE);
                } else {
                    bindDetail(result);
                }
            });
        }).start();
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
        List<HkDetail.Line> lines = detail.getLines();
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
        binding.detailScroll.scrollTo(0, 0);
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

    /** 视频网格间距：横向 12dp、纵向 16dp，边缘 0（父容器已有 16dp 边距）。 */
    private class GridSpace extends RecyclerView.ItemDecoration {
        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int pos = parent.getChildAdapterPosition(view);
            if (pos < 0) return;
            int col = pos % 3;
            int h = dp(12);
            outRect.left = col == 0 ? 0 : h / 2;
            outRect.right = col == 2 ? 0 : h / 2;
            if (pos >= 3) outRect.top = dp(16);
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
                openDetail(it, !fromContent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
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
