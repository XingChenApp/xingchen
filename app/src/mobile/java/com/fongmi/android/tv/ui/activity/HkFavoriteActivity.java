package com.fongmi.android.tv.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.hk.HkFavorite;
import com.fongmi.android.tv.api.hk.HkFavoriteStore;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.databinding.ActivityHkFavoriteBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.hk.HkDynamicBgView;
import com.fongmi.android.tv.ui.hk.HkFavoriteDialogs;
import com.fongmi.android.tv.ui.hk.HkPressFeedback;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 海阔收藏页：对齐 8.83 CollectionListActivity（分组筛选 + 条目列表）。
 * 收藏只进海阔侧存储（HkFavoriteStore），不混入主 App 首页收藏。
 * 点条目：用存下的 episodeUrl 直达那条内容的详情页（与规则内点条目同一路，对齐原版）。
 * 长按条目：更新收藏 / 取消收藏。
 */
public class HkFavoriteActivity extends BaseActivity {

    public static void start(Context context) {
        context.startActivity(new Intent(context, HkFavoriteActivity.class));
    }

    public static void start(Context context, String group) {
        Intent intent = new Intent(context, HkFavoriteActivity.class);
        intent.putExtra("group", group);
        context.startActivity(intent);
    }

    private ActivityHkFavoriteBinding binding;
    private final List<HkFavorite> all = new ArrayList<>();
    private final List<HkFavorite> shown = new ArrayList<>();
    private FavAdapter adapter;
    private String curGroup = "";

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityHkFavoriteBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        HkDynamicBgView.attachTo(this);
        HkFavoriteStore.migrateIfNeeded();
        String g = getIntent().getStringExtra("group");
        if (!TextUtils.isEmpty(g)) curGroup = g;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.rvFavorite.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FavAdapter();
        binding.rvFavorite.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        all.clear();
        all.addAll(HkFavoriteStore.list());
        buildGroupChips();
        applyFilter();
    }

    private void buildGroupChips() {
        binding.chipRow.removeAllViews();
        List<String> groups = new ArrayList<>();
        groups.add("");
        groups.addAll(HkFavoriteStore.groups());
        if (!TextUtils.isEmpty(curGroup) && !groups.contains(curGroup)) curGroup = "";
        for (String g : groups) {
            boolean selected = g.equals(curGroup);
            TextView chip = new TextView(this);
            chip.setText(TextUtils.isEmpty(g) ? "全部" : g);
            chip.setTextSize(14);
            chip.setGravity(Gravity.CENTER);
            int dp8 = Math.round(8 * getResources().getDisplayMetrics().density);
            int dp14 = Math.round(14 * getResources().getDisplayMetrics().density);
            chip.setPadding(dp14, dp8, dp14, dp8);
            chip.setTextColor(selected ? 0xFF7A5A00 : 0xFF1A1D24);
            chip.setBackgroundResource(selected ? R.drawable.hk_group_tag : R.drawable.dialog_option_card_light);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp8;
            final String gv = g;
            chip.setOnClickListener(v -> {
                curGroup = gv;
                buildGroupChips();
                applyFilter();
            });
            HkPressFeedback.attach(chip);
            binding.chipRow.addView(chip, lp);
        }
        binding.svGroups.setVisibility(groups.size() > 2 ? View.VISIBLE : View.GONE);
    }

    private void applyFilter() {
        shown.clear();
        for (HkFavorite f : all) {
            if (TextUtils.isEmpty(curGroup) || curGroup.equals(f.getGroup())) shown.add(f);
        }
        adapter.notifyDataSetChanged();
        binding.tvEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openFavorite(HkFavorite f) {
        HkRule rule = HkRuleManager.get().getInstalledRule(f.getRuleTitle());
        if (rule == null) {
            Notify.show("该小程序已删除");
            return;
        }
        // 直达那条内容（对齐 8.83 点收藏进 CUrl 页面）：用存下的 episodeUrl 走与规则内
        // 点条目同一条详情链（startHk → VideoActivity 海阔分支），不再只开小程序首页。
        VideoActivity.startHk(this, f.getRuleTitle(), f.getEpisodeUrl(), f.getTitle(), f.getPicUrl());
    }

    private class FavAdapter extends RecyclerView.Adapter<FavAdapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            TextView icon, title, sub;
            ImageView ivIcon;

            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.tv_icon);
                ivIcon = v.findViewById(R.id.iv_icon);
                title = v.findViewById(R.id.tv_title);
                sub = v.findViewById(R.id.tv_sub);
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hk_favorite, parent, false);
            HkPressFeedback.attach(v);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            HkFavorite f = shown.get(position);
            h.title.setText(f.getTitle());
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(f.getTime()));
            h.sub.setText(f.getRuleTitle() + " · " + f.getGroup() + " · " + date);
            String pic = f.getPicUrl();
            if (!TextUtils.isEmpty(pic) && (pic.startsWith("http://") || pic.startsWith("https://") || pic.startsWith("data:"))) {
                h.icon.setVisibility(View.GONE);
                h.ivIcon.setVisibility(View.VISIBLE);
                h.ivIcon.setClipToOutline(true);
                ImgUtil.loadRuleIcon(f.getTitle(), pic, h.ivIcon);
            } else {
                h.ivIcon.setVisibility(View.GONE);
                h.icon.setVisibility(View.VISIBLE);
                String t = f.getTitle();
                h.icon.setText(TextUtils.isEmpty(t) ? "海" : t.substring(0, 1));
            }
            h.itemView.setOnClickListener(v -> openFavorite(f));
            h.itemView.setOnLongClickListener(v -> {
                HkFavoriteDialogs.showOptions(HkFavoriteActivity.this, f.getTitle(),
                        new String[]{"更新收藏", "取消收藏"}, which -> {
                            if (which == 0) {
                                HkFavoriteDialogs.showEdit(HkFavoriteActivity.this, "更新收藏",
                                        f.getTitle(), f.getGroup(), (title, group) -> {
                                            f.setTitle(title);
                                            f.setGroup(group);
                                            f.setTime(System.currentTimeMillis());
                                            HkFavoriteStore.addOrUpdate(f);
                                            Notify.show("收藏已更新");
                                            reload();
                                        });
                            } else {
                                HkFavoriteStore.remove(f.getRuleTitle(), f.getEpisodeUrl());
                                Notify.show("已取消收藏");
                                reload();
                            }
                        });
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}
