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
 * 长按条目：删除这条记录。
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
        String ruleTitle = h.getSiteKey().substring(HkDetailBridge.KEY_PREFIX.length());
        HkRule rule = HkRuleManager.get().getInstalledRule(ruleTitle);
        if (rule == null) {
            Notify.show("该小程序已删除");
            return;
        }
        String vodId = extractVodId(h);
        String episodeUrl = h.getEpisodeUrl() == null ? "" : h.getEpisodeUrl();
        if (!episodeUrl.isEmpty() && episodeUrl.equals(vodId)) {
            // 列表条目直达播放留下的记录（id 即当时播的那条）：回播放器直达
            VideoActivity.startHkPlay(this, ruleTitle,
                    TextUtils.isEmpty(h.getVodFlag()) ? "默认" : h.getVodFlag(),
                    episodeUrl, h.getVodRemarks(), h.getVodName(), h.getVodPic());
        } else {
            // 详情页留下的记录（id 是详情页地址）：回详情页，与收藏直达同一路
            VideoActivity.startHk(this, ruleTitle, vodId, h.getVodName(), h.getVodPic());
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
                HkFavoriteDialogs.showOptions(HkHistoryActivity.this, h.getVodName(),
                        new String[]{"删除记录"}, which -> {
                            h.deleteAndSync();
                            Notify.show("已删除");
                            reload();
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
