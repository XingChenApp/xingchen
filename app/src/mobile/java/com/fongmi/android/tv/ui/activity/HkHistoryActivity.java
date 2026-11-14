package com.fongmi.android.tv.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.hk.HkDetailBridge;
import com.fongmi.android.tv.api.hk.HkFavorite;
import com.fongmi.android.tv.api.hk.HkFavoriteStore;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.ActivityHkHistoryBinding;
import com.fongmi.android.tv.db.AppDatabase;
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
 * 海阔播放历史页（2026-10-23 分家定案）：数据与点播源共用同一张 History 表，
 * 本页只筛海阔条目（History.getHk，站点段 hk_ 命名空间）；星辰首页播放历史已剔除海阔条目。
 * 点条目：详情页留下的记录回详情页（与收藏直达同一路），列表直达播放留下的记录回播放器直达；
 * 续播位置由 VideoActivity 按历史键找回，本页不另存位置。
 * 长按条目：收藏（四类条目统一的收藏产生面，存海阔自家收藏）/ 删除这条记录。
 */
public class HkHistoryActivity extends BaseActivity {

    public static void start(Context context) {
        context.startActivity(new Intent(context, HkHistoryActivity.class));
    }

    private ActivityHkHistoryBinding binding;
    private final List<History> items = new ArrayList<>();
    private HistoryAdapter adapter;

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityHkHistoryBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        HkDynamicBgView.attachTo(this);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        // 海阔面统一入口仪式：先过模块一次性接线（含 LitePal 存储层初始化）
        com.fongmi.android.tv.ui.hk.HkListActivity.ensureStatics();
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        binding.rvHistory.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        items.clear();
        items.addAll(History.getHk());
        adapter.notifyDataSetChanged();
        binding.tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openHistory(History h) {
        openRecord(this, h);
    }

    /**
     * 海阔视频历史再入的唯一实现（本页与主历史页共用）：主历史页 HistoryActivity
     * 用的是 History.get()（含海阔条目），点海阔条目若走通用 VideoActivity.start，
     * hk_ 键进 SiteApi.detailContent——那条链没有海阔分支（getSite 取空站后按
     * 普通接口源 call 空址），详情必空、再播必死（2026-11-14 再入普查钉死）。
     * 直达记录回直达、详情记录回详情，存档自带的身份（站点键规则名＋vodId＋
     * episodeUrl）足够回引擎现解析，不依赖任何会话残留。
     */
    public static void openRecord(android.app.Activity from, History h) {
        // 核查 136 补：主历史页冷入直调本口时 HkHistoryActivity 本体从未建、
        // 海阔入口仪式（LitePal 初始化＋jsEval 注入＋桥装配）无人跑过——
        // 下游选择器 @js= 段会因 jsEval==null 被静默原样返回。补在唯一实现
        // 入口首行，两个调用方同覆盖（CAS 幂等、已接线时空转）。
        com.fongmi.android.tv.ui.hk.HkListActivity.ensureStatics();
        String ruleTitle = h.getSiteKey().substring(HkDetailBridge.KEY_PREFIX.length());
        HkRule rule = HkRuleManager.get().getInstalledRule(ruleTitle);
        if (rule == null) {
            Notify.show("该小程序已删除");
            return;
        }
        String vodId = extractVodId(h);
        String episodeUrl = h.getEpisodeUrl() == null ? "" : h.getEpisodeUrl();
        boolean directForm = !episodeUrl.isEmpty() && episodeUrl.equals(vodId);
        // 借面三类（小说/听书/音乐）再入（2026-11-14 不留痕新规配套）：
        // 详情形记录不在此分流——落下方原路回详情页，divertHkRead 按有
        // 效规则重新分类改道并从本行恢复进度，与首次打开同路；直达形记
        // 录只有被点那一集/一曲的存量，按行自带字段当场组装单集会话/
        // 专辑/单曲队列，历史键与进度随行带入。分类按装机规则本体判
        // （容器壳恒 VIDEO、落原路；详情形进页后由 divert 按子规则再
        // 分类，子规则直达记录宿主层分不出、同样落原路按视频开）。
        if (directForm) {
            com.fongmi.android.tv.read.ReadingGate.HkReadKind kind =
                    com.fongmi.android.tv.read.ReadingGate.classifyHkRule(rule);
            String remarks = h.getVodRemarks() == null ? "" : h.getVodRemarks();
            String line = TextUtils.isEmpty(h.getVodFlag()) ? "默认" : h.getVodFlag();
            if (kind == com.fongmi.android.tv.read.ReadingGate.HkReadKind.NOVEL) {
                com.fongmi.android.tv.read.ReadingSession ns =
                        new com.fongmi.android.tv.read.ReadingSession();
                ns.kind = com.fongmi.android.tv.read.ReadingSession.Kind.NOVEL;
                ns.sourceName = ruleTitle;
                ns.ruleTitle = ruleTitle;
                ns.bookTitle = h.getVodName() == null ? "" : h.getVodName();
                ns.coverUrl = h.getVodPic() == null ? "" : h.getVodPic();
                ns.chapters.add(new com.fongmi.android.tv.read.ReadingSession.Chapter(
                        remarks.isEmpty() ? ns.bookTitle : remarks, episodeUrl));
                ns.chapterIndex = 0;
                ns.chapterUrl = episodeUrl;
                ns.hkHistoryKey = h.getKey();
                if (h.getPosition() > 0) {
                    ns.startFraction = (int) Math.min(h.getPosition(), 10000);
                }
                com.fongmi.android.tv.read.ReadingRouter.openReading(from, ns);
                return;
            }
            if (kind == com.fongmi.android.tv.read.ReadingGate.HkReadKind.AUDIO
                    || com.fongmi.android.tv.ui.HkRadioRouter.looksRadio(ruleTitle)) {
                com.fongmi.android.tv.legado.LegadoAudio.AudioAlbum album =
                        new com.fongmi.android.tv.legado.LegadoAudio.AudioAlbum();
                album.legadoKey = "hk:" + ruleTitle;
                album.hkRule = ruleTitle;
                album.hkLine = line;
                album.sourceName = ruleTitle;
                album.title = h.getVodName() == null ? "" : h.getVodName();
                album.coverUrl = h.getVodPic() == null ? "" : h.getVodPic();
                album.chapters.add(new com.fongmi.android.tv.legado.LegadoAudio.Chapter(
                        remarks.isEmpty() ? album.title : remarks, episodeUrl));
                album.index = 0;
                album.hkHistoryKey = h.getKey();
                com.fongmi.android.tv.ui.activity.AudioBookActivity.start(from,
                        com.fongmi.android.tv.legado.LegadoAudio.put(album));
                return;
            }
        }
        if (!episodeUrl.isEmpty() && episodeUrl.equals(vodId)) {
            // 列表条目直达播放留下的记录（id 即当时播的那条）：回播放器直达
            VideoActivity.startHkPlay(from, ruleTitle,
                    TextUtils.isEmpty(h.getVodFlag()) ? "默认" : h.getVodFlag(),
                    episodeUrl, h.getVodRemarks(), h.getVodName(), h.getVodPic());
        } else {
            // 详情页留下的记录（id 是详情页地址）：回详情页，与收藏直达同一路
            VideoActivity.startHk(from, ruleTitle, vodId, h.getVodName(), h.getVodPic());
        }
    }

    /** 历史键 = 站点段@@@vodId@@@cid；vodId 本身可能含 @@@（如带 @lazyRule 的地址），按首尾剥。 */
    private static String extractVodId(History h) {
        String key = h.getKey() == null ? "" : h.getKey();
        String siteKey = h.getSiteKey();
        String sym = AppDatabase.SYMBOL;
        if (siteKey.isEmpty() || !key.startsWith(siteKey + sym)) return h.getVodId();
        String rest = key.substring(siteKey.length() + sym.length());
        int tail = rest.lastIndexOf(sym);
        return tail >= 0 ? rest.substring(0, tail) : rest;
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {

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
        public void onBindViewHolder(Holder holder, int position) {
            History h = items.get(position);
            holder.title.setText(h.getVodName());
            String ruleTitle = h.getSiteKey().substring(HkDetailBridge.KEY_PREFIX.length());
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(h.getCreateTime()));
            String remarks = h.getVodRemarks() == null ? "" : h.getVodRemarks().trim();
            holder.sub.setText(ruleTitle + (remarks.isEmpty() ? "" : " · " + remarks) + " · " + date);
            String pic = h.getVodPic();
            if (!TextUtils.isEmpty(pic) && (pic.startsWith("http://") || pic.startsWith("https://") || pic.startsWith("data:"))) {
                holder.icon.setVisibility(View.GONE);
                holder.ivIcon.setVisibility(View.VISIBLE);
                holder.ivIcon.setClipToOutline(true);
                ImgUtil.loadRuleIcon(h.getVodName(), pic, holder.ivIcon);
            } else {
                holder.ivIcon.setVisibility(View.GONE);
                holder.icon.setVisibility(View.VISIBLE);
                String t = h.getVodName();
                holder.icon.setText(TextUtils.isEmpty(t) ? "海" : t.substring(0, 1));
            }
            holder.itemView.setOnClickListener(v -> openHistory(h));
            holder.itemView.setOnLongClickListener(v -> {
                // 收藏产生面（2026-11-14 他亲定补齐）：四类条目（视频/小说/
                // 听书/音乐）都从本行组收藏记录存海阔自家收藏，构建与切换
                // 走 HkFavoriteStore 单源口；已收藏的同口取消。
                HkFavorite probe = HkFavoriteStore.fromHistory(h);
                boolean fav = probe != null && HkFavoriteStore.isFavorite(
                        probe.getRuleTitle(), probe.getEpisodeUrl());
                HkFavoriteDialogs.showOptions(HkHistoryActivity.this, h.getVodName(),
                        new String[]{fav ? "取消收藏" : "收藏", "删除记录"}, which -> {
                            if (which == 0) {
                                if (probe == null) {
                                    Notify.show("收藏失败");
                                    return;
                                }
                                Boolean r = HkFavoriteStore.toggle(probe);
                                Notify.show(r == null ? "收藏失败"
                                        : (r ? "已加入收藏" : "已取消收藏"));
                            } else {
                                h.deleteAndSync();
                                Notify.show("已删除");
                                reload();
                            }
                        });
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
