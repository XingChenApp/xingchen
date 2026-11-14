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
import android.widget.HorizontalScrollView;
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

    // 品类标签（2026-11-14 他亲定形态：收藏页按品类分签）。判据与直达
    // 再入分流、海阔历史再入 openRecord 同一组合，见 kindOf。
    private static final int KIND_VIDEO = 0;
    private static final int KIND_NOVEL = 1;
    private static final int KIND_AUDIO = 2;
    private static final int KIND_MUSIC = 3;
    private static final String[] KIND_LABELS = {"视频", "小说", "听书", "音乐"};
    private int curKind = KIND_VIDEO;
    private LinearLayout kindRow;
    private final java.util.Map<String, Integer> kindCache = new java.util.HashMap<>();

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
        // 海阔面统一入口仪式：先过模块一次性接线（含 LitePal 存储层初始化）
        com.fongmi.android.tv.ui.hk.HkListActivity.ensureStatics();
        binding.btnBack.setOnClickListener(v -> finish());
        binding.rvFavorite.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FavAdapter();
        binding.rvFavorite.setAdapter(adapter);
        // 品类签行：程序化建行插在标题栏与分组签行之间（不动布局文件），
        // 签形态与下方分组签同一套（选中金底 hk_group_tag、未选浅卡）。
        float density = getResources().getDisplayMetrics().density;
        HorizontalScrollView kindScroll = new HorizontalScrollView(this);
        kindScroll.setHorizontalScrollBarEnabled(false);
        kindScroll.setClipToPadding(false);
        int pad16 = Math.round(16 * density);
        kindScroll.setPadding(pad16, 0, pad16, 0);
        kindRow = new LinearLayout(this);
        kindRow.setOrientation(LinearLayout.HORIZONTAL);
        kindScroll.addView(kindRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout rootLayout = (LinearLayout) binding.getRoot();
        rootLayout.addView(kindScroll, 1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        buildKindChips();
    }

    private void buildKindChips() {
        if (kindRow == null) return;
        kindRow.removeAllViews();
        int dp8 = Math.round(8 * getResources().getDisplayMetrics().density);
        int dp14 = Math.round(14 * getResources().getDisplayMetrics().density);
        for (int k = 0; k < KIND_LABELS.length; k++) {
            boolean selected = k == curKind;
            TextView chip = new TextView(this);
            chip.setText(KIND_LABELS[k]);
            chip.setTextSize(14);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp14, dp8, dp14, dp8);
            chip.setTextColor(selected ? 0xFF7A5A00 : 0xFF1A1D24);
            chip.setBackgroundResource(selected ? R.drawable.hk_group_tag : R.drawable.dialog_option_card_light);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp8;
            final int kv = k;
            chip.setOnClickListener(v -> {
                curKind = kv;
                buildKindChips();
                applyFilter();
            });
            HkPressFeedback.attach(chip);
            kindRow.addView(chip, lp);
        }
    }

    /**
     * 记录品类（收藏页标签过滤与直达再入分流共用一套判据，与海阔历史
     * 再入 openRecord 同组合）：装机规则本体过 ReadingGate 阅读族分类 →
     * 电台名口径归听书 → 声明 music 或音乐名口径归音乐 → 其余视频。
     * 容器壳在宿主层恒视频（详情形进页后由 divert 按子规则再分类）；
     * 规则已删除的记录归视频签，不凭空消失。按规则名缓存一轮。
     */
    private int kindOf(HkFavorite f) {
        Integer cached = kindCache.get(f.getRuleTitle());
        if (cached != null) return cached;
        int kind = KIND_VIDEO;
        HkRule rule = HkRuleManager.get().getInstalledRule(f.getRuleTitle());
        if (rule != null) {
            com.fongmi.android.tv.read.ReadingGate.HkReadKind rk =
                    com.fongmi.android.tv.read.ReadingGate.classifyHkRule(rule);
            if (rk == com.fongmi.android.tv.read.ReadingGate.HkReadKind.NOVEL) {
                kind = KIND_NOVEL;
            } else if (rk == com.fongmi.android.tv.read.ReadingGate.HkReadKind.AUDIO
                    || com.fongmi.android.tv.ui.HkRadioRouter.looksRadio(f.getRuleTitle())) {
                kind = KIND_AUDIO;
            }
        }
        kindCache.put(f.getRuleTitle(), kind);
        return kind;
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        all.clear();
        all.addAll(HkFavoriteStore.list());
        kindCache.clear();
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
            if (kindOf(f) != curKind) continue;
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
        // 直达播放页收藏的条目：存下的是集址，按直达支回放（与历史直达记录同一路）；
        // 当条目址走详情链必空（2026-11-14 再入普查钉死）。
        if (f.isDirect()) {
            // 借面三类（小说/听书/音乐）的直达收藏：按行自带字段当场组装
            // 单集会话/专辑/单曲队列，与海阔历史直达形再入同一组装法
            // （2026-11-14 配套）；三支都按 keyOf（规则名，集址）算直达
            // 形历史键带入，进度回写与海阔历史直达记录落同一行。详情形
            // 收藏不在此分流：下方 startHk 进详情页后 divert 按有效规则
            // 重新分类，四类全覆盖。
            int favKind = kindOf(f);
            String favLine = TextUtils.isEmpty(f.getFlag()) ? "默认" : f.getFlag();
            if (favKind == KIND_NOVEL) {
                com.fongmi.android.tv.read.ReadingSession ns =
                        new com.fongmi.android.tv.read.ReadingSession();
                ns.kind = com.fongmi.android.tv.read.ReadingSession.Kind.NOVEL;
                ns.sourceName = f.getRuleTitle();
                ns.ruleTitle = f.getRuleTitle();
                ns.bookTitle = f.getTitle() == null ? "" : f.getTitle();
                ns.coverUrl = f.getPicUrl() == null ? "" : f.getPicUrl();
                ns.chapters.add(new com.fongmi.android.tv.read.ReadingSession.Chapter(
                        ns.bookTitle, f.getEpisodeUrl()));
                ns.chapterIndex = 0;
                ns.chapterUrl = f.getEpisodeUrl();
                // 直达形键与海阔历史直达记录同键：进度回写落同一行
                ns.hkHistoryKey = com.fongmi.android.tv.api.hk.HkHistoryRecorder
                        .keyOf(f.getRuleTitle(), f.getEpisodeUrl());
                com.fongmi.android.tv.read.ReadingRouter.openReading(this, ns);
                return;
            }
            if (favKind == KIND_AUDIO) {
                com.fongmi.android.tv.legado.LegadoAudio.AudioAlbum album =
                        new com.fongmi.android.tv.legado.LegadoAudio.AudioAlbum();
                album.legadoKey = "hk:" + f.getRuleTitle();
                album.hkRule = f.getRuleTitle();
                album.hkLine = favLine;
                album.sourceName = f.getRuleTitle();
                album.title = f.getTitle() == null ? "" : f.getTitle();
                album.coverUrl = f.getPicUrl() == null ? "" : f.getPicUrl();
                album.chapters.add(new com.fongmi.android.tv.legado.LegadoAudio.Chapter(
                        album.title, f.getEpisodeUrl()));
                album.index = 0;
                album.hkHistoryKey = com.fongmi.android.tv.api.hk.HkHistoryRecorder
                        .keyOf(f.getRuleTitle(), f.getEpisodeUrl());
                com.fongmi.android.tv.ui.activity.AudioBookActivity.start(this,
                        com.fongmi.android.tv.legado.LegadoAudio.put(album));
                return;
            }
            VideoActivity.startHkPlay(this, f.getRuleTitle(),
                    TextUtils.isEmpty(f.getFlag()) ? "默认" : f.getFlag(),
                    f.getEpisodeUrl(), f.getTitle(), f.getTitle(), f.getPicUrl());
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
