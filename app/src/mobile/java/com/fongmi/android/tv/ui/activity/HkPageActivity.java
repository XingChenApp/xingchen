package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.api.hk.HkRouter;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.databinding.ActivityHkBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 海阔小程序单页（M3）：V1 规则列表 / V2 分类+列表 / V3 搜索结果，三视图栈式切换。
 * 点条目 → VideoActivity.startHk（复用星辰详情页）；详情/播放走 HkRouter。
 */
public class HkPageActivity extends BaseActivity {

    private static final int V_RULES = 0;
    private static final int V_CONTENT = 1;
    private static final int V_SEARCH = 2;
    private static final int REQ_IMPORT_FILE = 0x101;
    private static final String PREFS = "xingchen";
    private static final String KEY_HIST = "hk_search_history";

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

    private void showView(int v) {
        binding.viewRules.setVisibility(v == V_RULES ? View.VISIBLE : View.GONE);
        binding.viewContent.setVisibility(v == V_CONTENT ? View.VISIBLE : View.GONE);
        binding.viewSearch.setVisibility(v == V_SEARCH ? View.VISIBLE : View.GONE);
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
                ruleAdapter.notifyDataSetChanged();
                binding.emptyRules.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                binding.rvRules.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
            });
        }).start();
    }

    private void showImportDialog() {
        new AlertDialog.Builder(this)
                .setItems(new String[]{"从文件导入", "从口令导入"}, (d, which) -> {
                    if (which == 0) pickRuleFile();
                    else showPasteDialog();
                }).show();
    }

    private void pickRuleFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/json");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQ_IMPORT_FILE);
        } catch (Exception e) {
            Notify.show("无法打开文件选择器");
        }
    }

    private void showPasteDialog() {
        EditText et = new EditText(this);
        et.setHint("粘贴 rule.json 内容");
        et.setMinLines(4);
        et.setTextColor(0xFFF2F4F8);
        et.setHintTextColor(0xFF6E7686);
        new AlertDialog.Builder(this)
                .setTitle("从口令导入")
                .setView(et)
                .setPositiveButton("导入", (d, w) -> importRuleJson(et.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void importRuleJson(String json) {
        if (TextUtils.isEmpty(json)) {
            Notify.show("内容为空");
            return;
        }
        new Thread(() -> {
            try {
                HkRuleManager.get().importJson(json);
                App.post(() -> {
                    Notify.show("导入成功");
                    refreshRules();
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("导入失败：" + e.getMessage()));
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importRuleFile(data.getData());
        }
    }

    private void importRuleFile(Uri uri) {
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                importRuleJson(readAll(in));
            } catch (Exception e) {
                App.post(() -> Notify.show("导入失败：" + e.getMessage()));
            }
        }).start();
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
                .setItems(new String[]{"删除"}, (d, which) -> showDeleteRuleConfirm(rule))
                .show();
    }

    private void showDeleteRuleConfirm(HkRule rule) {
        new AlertDialog.Builder(this)
                .setTitle("删除规则")
                .setMessage("确定删除「" + rule.getTitle() + "」吗？")
                .setPositiveButton("删除", (d, w) -> new Thread(() -> {
                    HkRuleManager.get().delete(rule.getTitle());
                    App.post(this::refreshRules);
                }).start())
                .setNegativeButton("取消", null)
                .show();
    }

    private class RuleAdapter extends RecyclerView.Adapter<RuleAdapter.Holder> {

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

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hk_rule, parent, false);
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) v.getLayoutParams();
            lp.bottomMargin = dp(12);
            v.setLayoutParams(lp);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            HkRule rule = rules.get(position);
            h.name.setText(rule.getTitle());
            String sub = rule.getAuthor() + " · v" + rule.getVersion()
                    + (TextUtils.isEmpty(rule.getGroup()) ? "" : " · " + rule.getGroup());
            h.sub.setText(sub);
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
                HkRule r = rules.get(pos);
                r.setEnabled(checked);
                HkRuleManager.get().setEnabled(r.getTitle(), checked);
                notifyItemChanged(pos);
            });
            h.more.setOnClickListener(v -> showRuleMenu(rule));
            h.itemView.setOnClickListener(v -> openRule(rule));
        }

        @Override
        public int getItemCount() {
            return rules.size();
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
        tv.setTextColor(0xFFA7B0C0);
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
            binding.rvVideos.setVisibility(View.VISIBLE);
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
                if (result.isEmpty()) {
                    if (p == 1 && videos.isEmpty()) {
                        binding.tvContentEmpty.setVisibility(View.VISIBLE);
                        binding.rvVideos.setVisibility(View.GONE);
                    } else {
                        noMore = true;
                    }
                } else {
                    videos.addAll(result);
                    page = p + 1;
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
                VideoActivity.startHk(HkPageActivity.this, currentRule.getTitle(), it.getUrl(), it.getTitle(), it.getPic());
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
            h.chip.setTextColor(sel ? 0xFF1A1D24 : 0xFFF2F4F8);
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
