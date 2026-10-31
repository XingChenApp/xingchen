package com.fongmi.android.tv.hongguo.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.legado.HgAudioStore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 七模式（历史/收藏/点赞/预约/追更/小说/听书）的独立编辑页（2026-10-30 海报格批
 * 立页，2026-10-31 他亲定扩到五模式全量；小说支同日晚 W-05 裁定补入，
 * 与五模式同一套交互；听书支同日晚随「红果听书 · 在听」列表补入，
 * 数据走红果听书自家 HgAudioStore、删走其 remove，同为纯本地无上报）：点「编辑」与长按任意一格
 * 海报都进本页（长按的那格预勾），不是在原页原地切换状态。顶栏三段——
 * 左「全选」、中标题（全部历史/全部收藏/全部点赞/全部预约/全部追更/全部小说/全部红果听书）
 * ＋副行「已选择 N 个」、右「完成」返回上一页；每格海报右下角圆形
 * 勾选圈（未选＝深色半透底白勾，选中＝橙色实心白勾）；底部固定删除
 * 条（垃圾桶＋「删除」红橙字，选中 0 个置灰禁点、不隐藏）。删除落
 * 本地 HgStore 对应 id 集（追更与收藏同源同删法：删一边两边同消，
 * 批二同体定案），收藏/追更/预约的删除同步上报服务端（登录态，
 * HgAccountSync），删完留在本页刷新格子。小说支数据走 HgStore 小说
 * 键组、删走 removeNovelHistory（纯本地、无服务端上报）。数据全量
 * （不吃原页的筹过滤——标题「全部」即此义）。
 */
public class HongguoMineEditActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "hg_edit_mode";
    public static final String EXTRA_PRESELECT = "hg_edit_preselect";

    private int editMode = HongguoMineActivity.MODE_HISTORY;
    private TextView selectAllBtn;
    private TextView countText;
    private RecyclerView grid;
    private GridAdapter gridAdapter;
    private TextView emptyText;
    private LinearLayout deleteBar;
    private ImageView deleteIcon;
    private TextView deleteText;
    private final List<EditRow> rows = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();

    private static final class EditRow {
        String id;
        String title;
        String sub;
        String cover;
        String badge;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int navBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    @Override
    public void finish() {
        super.finish();
        HgTransition.applyPushExit(this);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HgWindowStyle.apply(this, true, true);
        editMode = getIntent().getIntExtra(EXTRA_MODE, HongguoMineActivity.MODE_HISTORY);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(0, 0, 0, navBarHeight());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), statusBarHeight() + dp(10), dp(16), dp(6));
        selectAllBtn = new TextView(this);
        selectAllBtn.setText("全选");
        selectAllBtn.setTextColor(0xFF1B1B1E);
        selectAllBtn.setTextSize(15);
        selectAllBtn.setGravity(Gravity.CENTER_VERTICAL);
        selectAllBtn.setOnClickListener(v -> toggleSelectAll());
        top.addView(selectAllBtn, new LinearLayout.LayoutParams(dp(72), dp(44)));
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER);
        TextView titleText = new TextView(this);
        titleText.setText(titleFor(editMode));
        titleText.setTextColor(0xFF1B1B1E);
        titleText.setTextSize(17);
        titleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleText.setGravity(Gravity.CENTER);
        titleBox.addView(titleText);
        countText = new TextView(this);
        countText.setTextColor(0xFF9B9BA1);
        countText.setTextSize(12);
        countText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = dp(2);
        titleBox.addView(countText, clp);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView doneBtn = new TextView(this);
        doneBtn.setText("完成");
        doneBtn.setTextColor(0xFF1B1B1E);
        doneBtn.setTextSize(15);
        doneBtn.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        doneBtn.setOnClickListener(v -> finish());
        top.addView(doneBtn, new LinearLayout.LayoutParams(dp(72), dp(44)));
        root.addView(top);

        grid = new RecyclerView(this);
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        grid.setPadding(dp(12), dp(6), dp(12), dp(16));
        gridAdapter = new GridAdapter();
        grid.setAdapter(gridAdapter);
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        emptyText = new TextView(this);
        emptyText.setText(emptyFor(editMode));
        emptyText.setTextColor(0xFF8E8E96);
        emptyText.setTextSize(14);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setVisibility(View.GONE);
        root.addView(emptyText, new LinearLayout.LayoutParams(-1, 0, 1f));

        View hairline = new View(this);
        hairline.setBackgroundColor(0xFFEEEEF0);
        root.addView(hairline, new LinearLayout.LayoutParams(-1, 1));
        deleteBar = new LinearLayout(this);
        deleteBar.setOrientation(LinearLayout.HORIZONTAL);
        deleteBar.setGravity(Gravity.CENTER);
        deleteBar.setPadding(0, dp(13), 0, dp(13));
        deleteIcon = new ImageView(this);
        deleteIcon.setImageResource(R.drawable.ic_action_delete);
        deleteBar.addView(deleteIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        deleteText = new TextView(this);
        deleteText.setText("删除");
        deleteText.setTextSize(15);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.leftMargin = dp(6);
        deleteBar.addView(deleteText, dlp);
        deleteBar.setOnClickListener(v -> confirmDelete());
        root.addView(deleteBar);

        setContentView(root);
        loadRows();
        String pre = getIntent().getStringExtra(EXTRA_PRESELECT);
        if (pre != null) {
            for (EditRow row : rows) {
                if (pre.equals(row.id)) {
                    selected.add(pre);
                    break;
                }
            }
        }
        if (savedInstanceState != null) {
            // 进程重建存档口（E-02）：已选项此前只活内存 LinkedHashSet，
            // 重建后整表丢失要重选（数据在 HgStore 不丢）。rows 由
            // HgStore 同步重组、与重建前同源同序，按 id 交集恢复已选
            // （重建间被删的条目自然落选），滚动状态一并恢复。
            ArrayList<String> savedSel = savedInstanceState.getStringArrayList("hg_selected");
            if (savedSel != null) {
                for (String id : savedSel) {
                    for (EditRow row : rows) {
                        if (id.equals(row.id)) {
                            selected.add(id);
                            break;
                        }
                    }
                }
            }
            Parcelable scroll = savedInstanceState.getParcelable("hg_scroll");
            if (scroll != null && grid.getLayoutManager() != null) {
                grid.getLayoutManager().onRestoreInstanceState(scroll);
            }
        }
        refreshState();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList("hg_selected", new ArrayList<>(selected));
        if (grid != null && grid.getLayoutManager() != null) {
            outState.putParcelable("hg_scroll", grid.getLayoutManager().onSaveInstanceState());
        }
    }

    private void loadRows() {
        rows.clear();
        if (editMode == HongguoMineActivity.MODE_HISTORY) {
            for (HgStore.HistoryRecord r : HgStore.historyRecords()) {
                EditRow row = new EditRow();
                row.id = r.seriesId;
                row.title = r.title == null ? "" : r.title;
                row.cover = r.cover;
                row.badge = HgStore.badgeTextOf(HgStore.typeOf(r.seriesId));
                int ep = Math.max(1, r.episodeIndex);
                row.sub = r.episodeCount > 0
                        ? ep + "集/" + r.episodeCount + "集" : "看到第" + ep + "集";
                rows.add(row);
            }
        } else if (editMode == HongguoMineActivity.MODE_NOVEL) {
            // 小说支：id 与元信息走小说键组（标题/作者/封面），无类型角标
            for (String id : HgStore.novelHistoryIds()) {
                String[] meta = HgStore.novelMetaOf(id);
                EditRow row = new EditRow();
                row.id = id;
                row.title = meta == null ? "" : meta[0];
                row.sub = meta == null ? "" : meta[1];
                row.cover = meta == null ? null : meta[2];
                rows.add(row);
            }
        } else if (editMode == HongguoMineActivity.MODE_AUDIO) {
            // 听书支：id 与元信息走红果听书自家 HgAudioStore（枚举已按
            // 最近收听倒序），子行口径与在听列表面同（章名/序号＋位置）
            for (HgAudioStore.Entry e : HgAudioStore.listAll(this)) {
                EditRow row = new EditRow();
                row.id = e.bookId;
                row.title = e.title == null ? "" : e.title;
                String sub = e.chapterName == null ? "" : e.chapterName;
                if (sub.isEmpty()) {
                    sub = "第" + (Math.max(0, e.chapterIndex) + 1) + "集";
                }
                if (e.position > 0) {
                    int sec = e.position / 1000;
                    sub += " · " + (sec / 60) + ":" + String.format("%02d", sec % 60);
                }
                row.sub = sub;
                row.cover = e.coverUrl;
                rows.add(row);
            }
        } else {
            for (String id : idsFor(editMode)) {
                String[] meta = HgStore.metaOf(id);
                EditRow row = new EditRow();
                row.id = id;
                row.title = meta == null ? "" : meta[0];
                row.cover = meta == null ? null : meta[1];
                row.badge = HgStore.badgeTextOf(HgStore.typeOf(id));
                int count = -1;
                if (meta != null) {
                    try {
                        count = Integer.parseInt(meta[2]);
                    } catch (Exception ignored) {
                    }
                }
                HgStore.Progress p = HgStore.progressOf(id);
                if (p != null) {
                    int ep = Math.max(1, p.episodeIndex);
                    row.sub = count > 0 ? ep + "集/" + count + "集" : "看到第" + ep + "集";
                } else {
                    row.sub = count > 0 ? "共" + count + "集" : "";
                }
                rows.add(row);
            }
        }
    }

    /** 非历史四模式的 id 集来源（追更与收藏同源：两模式读同一份 followed 集）。 */
    private static java.util.List<String> idsFor(int mode) {
        if (mode == HongguoMineActivity.MODE_LIKE) return HgStore.likedIds();
        if (mode == HongguoMineActivity.MODE_RESERVE) return HgStore.reservedIds();
        if (mode == HongguoMineActivity.MODE_FOLLOW) return HgStore.followedIds();
        return HgStore.favedIds();
    }

    private static String titleFor(int mode) {
        if (mode == HongguoMineActivity.MODE_HISTORY) return "全部历史";
        if (mode == HongguoMineActivity.MODE_LIKE) return "全部点赞";
        if (mode == HongguoMineActivity.MODE_RESERVE) return "全部预约";
        if (mode == HongguoMineActivity.MODE_FOLLOW) return "全部追更";
        if (mode == HongguoMineActivity.MODE_NOVEL) return "全部小说";
        if (mode == HongguoMineActivity.MODE_AUDIO) return "全部红果听书";
        return "全部收藏";
    }

    private static String emptyFor(int mode) {
        if (mode == HongguoMineActivity.MODE_HISTORY) return "暂无历史";
        if (mode == HongguoMineActivity.MODE_LIKE) return "暂无点赞";
        if (mode == HongguoMineActivity.MODE_RESERVE) return "暂无预约";
        if (mode == HongguoMineActivity.MODE_FOLLOW) return "暂无追更";
        if (mode == HongguoMineActivity.MODE_NOVEL) return "还没有小说记录";
        if (mode == HongguoMineActivity.MODE_AUDIO) return "还没有在听的红果书";
        return "暂无收藏";
    }

    private String confirmFor(int n) {
        if (editMode == HongguoMineActivity.MODE_HISTORY) {
            return "删除所选 " + n + " 条历史？删除后无法恢复。";
        }
        if (editMode == HongguoMineActivity.MODE_LIKE) {
            return "删除所选 " + n + " 条点赞？将取消点赞。";
        }
        if (editMode == HongguoMineActivity.MODE_RESERVE) {
            return "删除所选 " + n + " 条预约？将取消预约。";
        }
        if (editMode == HongguoMineActivity.MODE_FOLLOW) {
            return "删除所选 " + n + " 条追更？将取消追更。";
        }
        if (editMode == HongguoMineActivity.MODE_NOVEL) {
            // 与历史同型：纯本地记录，删后无法恢复（无服务端可取消的动作）
            return "删除所选 " + n + " 条小说记录？删除后无法恢复。";
        }
        if (editMode == HongguoMineActivity.MODE_AUDIO) {
            // 同小说支：纯本地在听记录（HgAudioStore），无服务端上报
            return "删除所选 " + n + " 条听书记录？删除后无法恢复。";
        }
        return "删除所选 " + n + " 条收藏？将取消收藏。";
    }

    /**
     * 删一条（按模式落对应 id 集）：历史摘断点＋索引；收藏/追更同源——
     * 都从 followed 正源移除并上报 DelShelf（删一边另一边同消，同体
     * 定案）；点赞摘 liked 集（上报口已通、粒度是集 vid，见
     * HgAccountSync.reportLike；本面只持剧 id、无集 vid，取消上报
     * 缺粒度未接，云端残留待 liked 集补存 vid 后收口）；
     * 预约摘 reserved 集并上报取消预约；小说走 removeNovelHistory
     * 整组摘除（断点＋元信息＋索引，纯本地无上报）；听书走
     * HgAudioStore.remove 摘该 bookId 的在听存档（纯本地无上报）。
     */
    private void deleteOne(String id) {
        if (editMode == HongguoMineActivity.MODE_HISTORY) {
            HgStore.removeHistory(id);
        } else if (editMode == HongguoMineActivity.MODE_NOVEL) {
            HgStore.removeNovelHistory(id);
        } else if (editMode == HongguoMineActivity.MODE_AUDIO) {
            HgAudioStore.remove(this, id);
        } else if (editMode == HongguoMineActivity.MODE_LIKE) {
            HgStore.setLiked(id, false);
        } else if (editMode == HongguoMineActivity.MODE_RESERVE) {
            HgStore.setReserved(id, false);
            com.fongmi.android.tv.hongguo.api.HgAccountSync.reportReserve(id, false);
        } else if (editMode == HongguoMineActivity.MODE_FOLLOW) {
            HgStore.setFollowed(id, false);
            com.fongmi.android.tv.hongguo.api.HgAccountSync.reportShelf(id, false);
        } else {
            HgStore.setFaved(id, false);
            com.fongmi.android.tv.hongguo.api.HgAccountSync.reportShelf(id, false);
        }
    }

    private void toggleSelectAll() {
        if (rows.isEmpty()) return;
        if (selected.size() == rows.size()) {
            selected.clear();
        } else {
            for (EditRow row : rows) selected.add(row.id);
        }
        gridAdapter.notifyDataSetChanged();
        refreshState();
    }

    private void toggleRow(EditRow row) {
        if (!selected.remove(row.id)) selected.add(row.id);
        gridAdapter.notifyDataSetChanged();
        refreshState();
    }

    private void refreshState() {
        int n = selected.size();
        countText.setText("已选择 " + n + " 个");
        selectAllBtn.setText(!rows.isEmpty() && n == rows.size() ? "取消全选" : "全选");
        int color = n > 0 ? 0xFFE8501C : 0xFFBFBFC4;
        deleteText.setTextColor(color);
        deleteIcon.setColorFilter(color);
        deleteBar.setClickable(n > 0);
        grid.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        emptyText.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmDelete() {
        int n = selected.size();
        if (n == 0) return;
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setMessage(confirmFor(n))
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> doDelete())
                .show();
    }

    private void doDelete() {
        List<EditRow> keep = new ArrayList<>();
        for (EditRow row : rows) {
            if (selected.contains(row.id)) {
                deleteOne(row.id);
            } else {
                keep.add(row);
            }
        }
        rows.clear();
        rows.addAll(keep);
        selected.clear();
        gridAdapter.notifyDataSetChanged();
        refreshState();
    }

    private GradientDrawable roundBg(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private GradientDrawable checkBg(boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (on) {
            g.setColor(0xFFF0761A);
        } else {
            g.setColor(0x66000000);
            g.setStroke(dp(1), 0xD9FFFFFF);
        }
        return g;
    }

    /** 编辑格：与原页同款 3:4 海报格，右下多一颗勾选圈点格切换。 */
    private class GridAdapter extends RecyclerView.Adapter<GridAdapter.H> {

        private final int posterW;
        private final int posterH;

        GridAdapter() {
            posterW = (getResources().getDisplayMetrics().widthPixels - dp(48)) / 3;
            posterH = posterW * 4 / 3;
        }

        class H extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView badge;
            final TextView check;
            final TextView title;
            final TextView sub;

            H(LinearLayout cell) {
                super(cell);
                cover = cell.findViewWithTag("cover");
                badge = cell.findViewWithTag("badge");
                check = cell.findViewWithTag("check");
                title = cell.findViewWithTag("title");
                sub = cell.findViewWithTag("sub");
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout cell = new LinearLayout(HongguoMineEditActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(dp(4), dp(6), dp(4), dp(8));
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            FrameLayout posterBox = new FrameLayout(HongguoMineEditActivity.this);
            posterBox.setBackground(roundBg(0xFFEDEDEF, dp(8)));
            ImageView cover = new ImageView(HongguoMineEditActivity.this);
            cover.setTag("cover");
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            posterBox.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            TextView badge = new TextView(HongguoMineEditActivity.this);
            badge.setTag("badge");
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(10);
            badge.setPadding(dp(5), dp(2), dp(5), dp(2));
            badge.setBackground(roundBg(0x8C000000, dp(4)));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2);
            blp.gravity = Gravity.TOP | Gravity.END;
            blp.topMargin = dp(5);
            blp.rightMargin = dp(5);
            posterBox.addView(badge, blp);
            TextView check = new TextView(HongguoMineEditActivity.this);
            check.setTag("check");
            check.setText("✓");
            check.setTextColor(Color.WHITE);
            check.setTextSize(12);
            check.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            check.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams chlp = new FrameLayout.LayoutParams(dp(22), dp(22));
            chlp.gravity = Gravity.BOTTOM | Gravity.END;
            chlp.rightMargin = dp(6);
            chlp.bottomMargin = dp(6);
            posterBox.addView(check, chlp);
            cell.addView(posterBox, new LinearLayout.LayoutParams(posterW, posterH));
            TextView title = new TextView(HongguoMineEditActivity.this);
            title.setTag("title");
            title.setTextColor(0xFF1B1B1E);
            title.setTextSize(14);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.topMargin = dp(6);
            cell.addView(title, tlp);
            TextView sub = new TextView(HongguoMineEditActivity.this);
            sub.setTag("sub");
            sub.setTextColor(0xFF9B9BA1);
            sub.setTextSize(11.5f);
            sub.setMaxLines(1);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
            slp.topMargin = dp(2);
            cell.addView(sub, slp);
            return new H(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            EditRow row = rows.get(position);
            if (row.cover != null && !row.cover.isEmpty()) {
                HgCoverClip.round(h.cover, dp(8));
                Glide.with(HongguoMineEditActivity.this).load(row.cover)
                        .into(h.cover);
            } else {
                h.cover.setImageDrawable(null);
            }
            if (row.badge != null && !row.badge.isEmpty()) {
                h.badge.setVisibility(View.VISIBLE);
                h.badge.setText(row.badge);
            } else {
                h.badge.setVisibility(View.GONE);
            }
            h.check.setBackground(checkBg(selected.contains(row.id)));
            h.title.setText(row.title);
            h.sub.setText(row.sub == null ? "" : row.sub);
            h.itemView.setOnClickListener(v -> toggleRow(row));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
