package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.hk.HkDynamicBgView;
import com.fongmi.android.tv.ui.hk.HkGlassDrawable;
import com.fongmi.android.tv.ui.hk.HkImageUrlMap;
import com.fongmi.android.tv.ui.hk.HkPressFeedback;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.databinding.ActivityHkRuleSearchBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 独立规则搜索页：搜已安装小程序。
 * 对齐 8.83 ArticleListRuleSearchActivity：
 * - 搜索分两轮：第一轮标题(忽略大小写)/作者；第二轮 URL/各类规则正文
 * - 空关键词显示全部规则
 * - 历史只存关键词
 */
public class HkRuleSearchActivity extends BaseActivity {

    private static final String PREFS = "xingchen";
    private static final String KEY_HIST = "hk_rule_search_history";

    public static void start(Context context) {
        context.startActivity(new Intent(context, HkRuleSearchActivity.class));
    }

    private ActivityHkRuleSearchBinding binding;
    private final List<HkRule> allRules = new ArrayList<>();
    private final List<HkRule> results = new ArrayList<>();
    private ResultAdapter resultAdapter;
    private final List<String> history = new ArrayList<>();
    private HistAdapter histAdapter;

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityHkRuleSearchBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        // 海阔面统一入口仪式：先过模块一次性接线（含 LitePal 存储层初始化）
        com.fongmi.android.tv.ui.hk.HkListActivity.ensureStatics();
        // 海阔浅色页状态栏：透明＋深色图标（BaseActivity 默认白图标，浅底上看不清，这里盖掉）；
        // 液态玻璃动态背景（LinearLayout 根走 decor 注入，画到状态栏底下）
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        HkDynamicBgView.attachTo(this);
        loadHistory();
        binding.rvResults.setLayoutManager(new GridLayoutManager(this, 2));
        resultAdapter = new ResultAdapter();
        binding.rvResults.setAdapter(resultAdapter);
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        histAdapter = new HistAdapter();
        binding.rvHistory.setAdapter(histAdapter);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnClear.setOnClickListener(v -> binding.etKeyword.setText(""));
        // 8.83: TextWatcher 实时搜索；空关键词显示全部规则
        binding.etKeyword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s == null) return;
                String kw = s.toString();
                binding.btnClear.setVisibility(TextUtils.isEmpty(kw) ? View.INVISIBLE : View.VISIBLE);
                doSearch(kw.trim());
            }
        });
        binding.etKeyword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String kw = binding.etKeyword.getText().toString().trim();
                if (!kw.isEmpty()) pushHistory(kw);
                hideKeyboard();
                return true;
            }
            return false;
        });
        binding.etKeyword.requestFocus();
        new Thread(() -> {
            List<HkRule> list = HkRuleManager.get().getRules();
            App.post(() -> {
                allRules.clear();
                allRules.addAll(list);
                doSearch(binding.etKeyword.getText().toString().trim());
            });
        }).start();
    }

    /**
     * 对齐 8.83 searchKey：
     * 第一轮：标题(忽略大小写包含) 或 作者(包含)
     * 第二轮：排除第一轮结果，检查 url/search_url/find_rule/searchFind/
     *         detail_find_rule/sdetail_find_rule/preRule/pages 是否包含关键词
     * 空关键词：显示全部规则
     */
    private void doSearch(String kw) {
        results.clear();
        if (kw.isEmpty()) {
            // 8.83: 空关键词显示全部规则
            results.addAll(allRules);
        } else {
            // 第一轮：标题 / 作者
            String kLower = kw.toLowerCase(Locale.getDefault());
            for (HkRule r : allRules) {
                if (matchFirstRound(r, kw, kLower)) results.add(r);
            }
            // 第二轮：规则正文各字段（排除已命中）
            for (HkRule r : allRules) {
                if (results.contains(r)) continue;
                if (matchSecondRound(r, kw)) results.add(r);
            }
        }
        resultAdapter.notifyDataSetChanged();
        boolean empty = !results.isEmpty();
        binding.tvEmpty.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.rvResults.setVisibility(empty ? View.VISIBLE : View.GONE);
        // 8.83: 有关键词时不显示历史
        boolean showHist = kw.isEmpty() && !history.isEmpty();
        binding.rvHistory.setVisibility(showHist ? View.VISIBLE : View.GONE);
        binding.tvHistoryLabel.setVisibility(showHist ? View.VISIBLE : View.GONE);
    }

    /** 第一轮：标题(忽略大小写) 或 作者 */
    private boolean matchFirstRound(HkRule r, String kw, String kLower) {
        String title = r.getTitle();
        if (!TextUtils.isEmpty(title)
                && title.toLowerCase(Locale.getDefault()).contains(kLower)) {
            return true;
        }
        String author = r.getAuthor();
        return !TextUtils.isEmpty(author) && author.contains(kw);
    }

    /** 第二轮：URL 与各类规则正文字段 */
    private boolean matchSecondRound(HkRule r, String kw) {
        return contains(r.getUrl(), kw)
                || contains(r.getSearchUrl(), kw)
                || contains(r.getFindRule(), kw)
                || contains(r.getSearchFind(), kw)
                || contains(r.getDetailFindRule(), kw)
                || contains(r.getSdetailFindRule(), kw)
                || contains(r.getPreRule(), kw)
                || contains(r.getPages(), kw);
    }

    private boolean contains(String field, String kw) {
        return !TextUtils.isEmpty(field) && field.contains(kw);
    }

    private void pushHistory(String kw) {
        history.remove(kw);
        history.add(0, kw);
        while (history.size() > 12) history.remove(history.size() - 1);
        getPrefs().edit().putString(KEY_HIST, joinHist()).apply();
        histAdapter.notifyDataSetChanged();
        // 8.83: 有关键词时隐藏历史区
        binding.rvHistory.setVisibility(View.GONE);
        binding.tvHistoryLabel.setVisibility(View.GONE);
    }

    private String joinHist() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) sb.append("\u0001");
            sb.append(history.get(i));
        }
        return sb.toString();
    }

    private void loadHistory() {
        history.clear();
        String s = getPrefs().getString(KEY_HIST, "");
        if (!s.isEmpty()) {
            for (String part : s.split("\u0001")) {
                if (!part.isEmpty() && !history.contains(part)) history.add(part);
            }
        }
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(binding.etKeyword.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    private class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.Holder> {
        /** v5 口径（与 HkPageActivity P1 同一套色板/算法）：图标主色板＋按标题首字取色。 */
        private static final int[] TINT_PALETTE = {
                0xFFC9970D, 0xFF2E7FD9, 0xFF35A06B, 0xFFD95F4B, 0xFF8E6BC8, 0xFF2AA5A0
        };

        private static int paletteColorFor(String title) {
            int h = 0;
            if (title != null && !title.isEmpty()) h = Math.abs(title.charAt(0));
            return TINT_PALETTE[h % TINT_PALETTE.length];
        }

        private static int parseIconColor(String iconDef) {
            if (!TextUtils.isEmpty(iconDef) && iconDef.startsWith("#")) {
                try {
                    return android.graphics.Color.parseColor(iconDef);
                } catch (Exception ignored) {
                }
            }
            return -1;
        }

        private static int blend(int base, int tint, float ratio) {
            float r = Math.max(0f, Math.min(1f, ratio));
            int red = Math.round(android.graphics.Color.red(base) * (1 - r) + android.graphics.Color.red(tint) * r);
            int green = Math.round(android.graphics.Color.green(base) * (1 - r) + android.graphics.Color.green(tint) * r);
            int blue = Math.round(android.graphics.Color.blue(base) * (1 - r) + android.graphics.Color.blue(tint) * r);
            return android.graphics.Color.argb(0xFF, red, green, blue);
        }

        private static int lighten(int color, float ratio) {
            return blend(0xFFFFFFFF, color, ratio);
        }

        /** 图标瓷砖：主色渐变（提亮→主色）＋顶部内高光，圆角 10dp（P1 同口径）。 */
        private static Drawable iconTileDrawable(int color) {
            int rad = ResUtil.dp2px(10);
            GradientDrawable base = new GradientDrawable();
            base.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            base.setColors(new int[]{lighten(color, 0.45f), color});
            base.setCornerRadius(rad);
            GradientDrawable gloss = new GradientDrawable();
            gloss.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            gloss.setColors(new int[]{0x59FFFFFF, 0x00FFFFFF});
            gloss.setCornerRadius(rad);
            return new LayerDrawable(new Drawable[]{base, gloss});
        }

        class Holder extends RecyclerView.ViewHolder {
            TextView icon, name;
            ImageView ivIcon;

            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.tv_icon);
                ivIcon = v.findViewById(R.id.iv_icon);
                name = v.findViewById(R.id.tv_name);
                v.findViewById(R.id.sw_enable).setVisibility(View.GONE);
                v.findViewById(R.id.tv_group_tag).setVisibility(View.GONE);
                v.findViewById(R.id.btn_more).setVisibility(View.GONE);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_hk_rule, parent, false);
            // 液态玻璃：结果卡去 CardView 实底换玻璃件（圆角 20dp）＋按压反馈；图标砖取色保留
            if (v instanceof androidx.cardview.widget.CardView) {
                androidx.cardview.widget.CardView cv = (androidx.cardview.widget.CardView) v;
                cv.setCardElevation(0);
                cv.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
                cv.setBackground(new HkGlassDrawable(cv.getContext(), 20));
            }
            HkPressFeedback.attach(v);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            HkRule r = results.get(position);
            String title = r.getTitle();
            h.name.setText(TextUtils.isEmpty(title) ? "未命名规则" : title);
            // 图标与小程序列表（HkPageActivity.applyIconBg）同链，不再写死藏图：
            // 先查内置图映射（hiker://images/*），未命中走 URL 链（loadRuleIcon 失败回退默认图），
            // 都落空才落文字瓷砖。
            String iconDef = r.getIcon();
            int builtinId = -1;
            String imgSrc = null;
            if (!TextUtils.isEmpty(iconDef)) {
                if (iconDef.startsWith("hiker://images/")) {
                    builtinId = HkImageUrlMap.getIdByUrl(h.itemView.getContext(), iconDef);
                }
                if (builtinId <= 0) {
                    if (iconDef.startsWith("http://") || iconDef.startsWith("https://") || iconDef.startsWith("data:")) {
                        imgSrc = iconDef;
                    } else if (iconDef.startsWith("hiker://files/")) {
                        String p = com.fongmi.android.tv.api.hk.HkJsRuntime.getFilePath(iconDef);
                        if (!TextUtils.isEmpty(p) && new java.io.File(p).exists()) imgSrc = p;
                    }
                }
            }
            if (builtinId > 0 || imgSrc != null) {
                h.icon.setVisibility(View.GONE);
                h.ivIcon.setVisibility(View.VISIBLE);
                h.ivIcon.setClipToOutline(true);
                h.ivIcon.setBackgroundResource(R.drawable.hk_miniapp_icon_bg);
                if (builtinId > 0) {
                    com.bumptech.glide.Glide.with(h.ivIcon).clear(h.ivIcon);
                    h.ivIcon.setImageResource(builtinId);
                } else {
                    ImgUtil.loadRuleIcon(title, imgSrc, h.ivIcon);
                }
            } else {
                h.ivIcon.setVisibility(View.GONE);
                h.icon.setVisibility(View.VISIBLE);
                h.icon.setText(!TextUtils.isEmpty(title) ? title.substring(0, 1) : "海");
                // 液态玻璃：图标瓷砖按图标主色（# 色值优先，否则按标题首字取色板），卡面换玻璃（整卡淡染已去）
                int faceBase = parseIconColor(iconDef);
                if (faceBase == -1) faceBase = paletteColorFor(title);
                h.icon.setBackground(iconTileDrawable(faceBase));
            }
            h.itemView.setOnClickListener(v -> {
                // M12: 历史只存关键词，不存标题。点击结果直接返回，不写历史。
                Intent data = new Intent();
                data.putExtra("title", title);
                setResult(Activity.RESULT_OK, data);
                finish();
            });
        }

        @Override
        public int getItemCount() {
            return results.size();
        }
    }

    private class HistAdapter extends RecyclerView.Adapter<HistAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            TextView tv;

            Holder(View v) {
                super(v);
                tv = (TextView) v;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setTextColor(0xFF1A1D24);
            tv.setTextSize(13);
            tv.setBackgroundResource(R.drawable.dialog_option_card_light);
            int p = (int) (12 * parent.getContext().getResources().getDisplayMetrics().density);
            tv.setPadding(p, p / 2, p, p / 2);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.WRAP_CONTENT, RecyclerView.LayoutParams.WRAP_CONTENT);
            int m = (int) (6 * parent.getContext().getResources().getDisplayMetrics().density);
            lp.rightMargin = m;
            tv.setLayoutParams(lp);
            return new Holder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            String kw = history.get(position);
            h.tv.setText(kw);
            h.tv.setOnClickListener(v -> binding.etKeyword.setText(kw));
        }

        @Override
        public int getItemCount() {
            return history.size();
        }
    }
}
