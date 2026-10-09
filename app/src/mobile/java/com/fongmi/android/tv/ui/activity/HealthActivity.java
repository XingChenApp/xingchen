package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivityHealthBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.Spider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class HealthActivity extends BaseActivity {

    private ActivityHealthBinding binding;
    private SiteAdapter adapter;
    private final List<SiteItem> items = new ArrayList<>();
    private volatile boolean testing = false;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, HealthActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityHealthBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        adapter = new SiteAdapter();
        binding.rvSites.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSites.setAdapter(adapter);
        binding.btnRetest.setOnClickListener(v -> {
            if (!testing) startTest();
        });
        collectSites();
        if (items.isEmpty()) {
            binding.tvEmpty.setVisibility(View.VISIBLE);
            binding.rvSites.setVisibility(View.GONE);
            binding.tvSummary.setText("暂无 PY / JS 源");
        } else {
            startTest();
        }
    }

    private void collectSites() {
        items.clear();
        try {
            List<Site> sites = VodConfig.get().getSites();
            for (Site site : sites) {
                String api = site.getApi();
                if (api == null) continue;
                String type = null;
                if (api.contains(".py")) type = "PY";
                else if (api.contains(".js") || api.contains(".wv")) type = "JS";
                if (type != null) items.add(new SiteItem(site.getKey(), site.getName(), type));
            }
        } catch (Throwable ignored) {}
    }

    private void startTest() {
        testing = true;
        for (SiteItem item : items) item.status = SiteItem.STATUS_TESTING;
        adapter.notifyDataSetChanged();
        updateSummary();
        binding.btnRetest.setText("检测中…");
        Task.execute(() -> {
            int done = 0;
            for (SiteItem item : items) {
                boolean ok = testSite(item);
                item.status = ok ? SiteItem.STATUS_OK : SiteItem.STATUS_FAIL;
                done++;
                final int progress = done;
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    updateSummary();
                    binding.btnRetest.setText("检测中 " + progress + "/" + items.size());
                });
            }
            runOnUiThread(() -> {
                testing = false;
                binding.btnRetest.setText("重新检测");
                updateSummary();
                Notify.show("检测完成");
            });
        });
    }

    private boolean testSite(SiteItem item) {
        try {
            Site site = VodConfig.get().getSite(item.key);
            if (site == null || site.isEmpty()) return false;
            Spider spider = site.spider();
            Future<String> future = Task.executor().submit(() -> spider.homeContent(true));
            String json = future.get(20, TimeUnit.SECONDS);
            if (json == null || json.isEmpty()) return false;
            Result result = Result.fromJson(json);
            return result != null && result.getList() != null && !result.getList().isEmpty();
        } catch (Throwable e) {
            return false;
        }
    }

    private void updateSummary() {
        int ok = 0, fail = 0, testingCount = 0;
        for (SiteItem item : items) {
            if (item.status == SiteItem.STATUS_OK) ok++;
            else if (item.status == SiteItem.STATUS_FAIL) fail++;
            else testingCount++;
        }
        if (testingCount > 0) {
            binding.tvSummary.setText("检测中… " + ok + " 正常");
        } else {
            binding.tvSummary.setText(ok + " 正常 · " + fail + " 异常");
        }
    }

    static class SiteItem {
        static final int STATUS_TESTING = 0;
        static final int STATUS_OK = 1;
        static final int STATUS_FAIL = 2;
        final String key;
        final String name;
        final String type;
        int status = STATUS_TESTING;

        SiteItem(String key, String name, String type) {
            this.key = key;
            this.name = name;
            this.type = type;
        }
    }

    private class SiteAdapter extends RecyclerView.Adapter<SiteAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_health_site, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            SiteItem item = items.get(position);
            holder.tvName.setText(item.name);
            holder.tvType.setText(item.type);
            if (item.status == SiteItem.STATUS_OK) {
                holder.tvStatus.setText("正常");
                holder.tvStatus.setTextColor(0xFF1B9E4B);
            } else if (item.status == SiteItem.STATUS_FAIL) {
                holder.tvStatus.setText("异常");
                holder.tvStatus.setTextColor(0xFFE5484D);
            } else {
                holder.tvStatus.setText("检测中…");
                holder.tvStatus.setTextColor(0xFF8A8F99);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            TextView tvName;
            TextView tvType;
            TextView tvStatus;

            Holder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_site_name);
                tvType = itemView.findViewById(R.id.tv_site_type);
                tvStatus = itemView.findViewById(R.id.tv_site_status);
            }
        }
    }
}
