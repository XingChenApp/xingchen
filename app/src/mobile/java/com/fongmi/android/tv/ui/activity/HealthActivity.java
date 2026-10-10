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
import com.fongmi.android.tv.utils.PyExtConfig;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.Spider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class HealthActivity extends BaseActivity {

    private ActivityHealthBinding binding;
    private SiteAdapter adapter;
    private final List<SiteItem> items = new ArrayList<>();
    // Dynamically created PY sites scanned from plugins/py/ (not in VodConfig)
    private final Map<String, Site> dynamicSites = new LinkedHashMap<>();
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
        dynamicSites.clear();
        try {
            Map<String, Site> allSites = new LinkedHashMap<>();
            for (Site site : VodConfig.get().getSites()) {
                if (site != null && site.getKey() != null) allSites.put(site.getKey(), site);
            }
            Site home = VodConfig.get().getHome();
            if (home != null && !home.isEmpty() && home.getKey() != null) allSites.put(home.getKey(), home);
            for (Site site : allSites.values()) {
                String key = site.getKey();
                String api = site.getApi();
                if (api == null) continue;
                String type = null;
                if ((site.getType() != null && site.getType() == 3) || key.startsWith("py_") || api.contains(".py")) type = "PY";
                else if (key.startsWith("js_") || api.contains(".js") || api.contains(".wv")) type = "JS";
                if (type != null) items.add(new SiteItem(key, site.getName(), type));
            }
            // Scan plugins/py/ for all PY scripts, not just the current home (same as search)
            java.io.File pyDir = new java.io.File(getFilesDir(), "plugins/py");
            java.io.File[] pyFiles = pyDir.listFiles((dir, name) -> name.endsWith(".py"));
            if (pyFiles != null) {
                for (java.io.File f : pyFiles) {
                    String fileName = f.getName();
                    String baseName = fileName.substring(0, fileName.length() - 3);
                    String key = "py_" + baseName;
                    if (allSites.containsKey(key)) continue;
                    Site pySite = createPySite(baseName);
                    if (pySite != null) {
                        dynamicSites.put(key, pySite);
                        items.add(new SiteItem(key, pySite.getName(), "PY"));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private Site createPySite(String baseName) {
        try {
            java.io.File file = new java.io.File(getFilesDir(), "plugins/py/" + baseName + ".py");
            if (!file.exists()) return null;
            Site site = new Site();
            site.setKey("py_" + baseName);
            site.setName(baseName);
            site.setApi(file.getAbsolutePath());
            site.setType(3);
            String savedExt = PyExtConfig.load(this, "py_" + baseName);
            site.setExt(savedExt == null || savedExt.isEmpty() ? "{}" : savedExt);
            site.setJar("");
            return site;
        } catch (Exception e) {
            return null;
        }
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
                if ("JS".equals(item.type)) item.status = SiteItem.STATUS_UNSUPPORTED;
                else item.status = testSite(item) ? SiteItem.STATUS_OK : SiteItem.STATUS_FAIL;
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
                saveHealthResult();
                Notify.show("检测完成");
            });
        });
    }

    private boolean testSite(SiteItem item) {
        try {
            Site site = VodConfig.get().getSite(item.key);
            if (site == null || site.isEmpty()) site = dynamicSites.get(item.key);
            if (site == null || site.isEmpty()) return false;
            Spider spider = site.spider();
            Future<String> future = Task.executor().submit(() -> spider.homeContent(true));
            String json;
            try {
                json = future.get(60, TimeUnit.SECONDS);
            } finally {
                future.cancel(true);
            }
            Result result = Result.fromJson(json);
            if (result != null && result.getList() != null && !result.getList().isEmpty()) return true;
            // Fallback: some sources return an empty homeContent list but homeVideoContent has data
            // (same fallback logic as SiteApi.homeContent)
            Future<String> fVideo = Task.executor().submit(spider::homeVideoContent);
            String video;
            try {
                video = fVideo.get(60, TimeUnit.SECONDS);
            } finally {
                fVideo.cancel(true);
            }
            Result videoResult = Result.fromJson(video);
            return videoResult != null && videoResult.getList() != null && !videoResult.getList().isEmpty();
        } catch (Throwable e) {
            return false;
        }
    }

    private void updateSummary() {
        int ok = 0, fail = 0, unsupported = 0, testingCount = 0;
        for (SiteItem item : items) {
            if (item.status == SiteItem.STATUS_OK) ok++;
            else if (item.status == SiteItem.STATUS_FAIL) fail++;
            else if (item.status == SiteItem.STATUS_UNSUPPORTED) unsupported++;
            else testingCount++;
        }
        if (testingCount > 0) {
            binding.tvSummary.setText("检测中… " + ok + " 正常");
        } else {
            binding.tvSummary.setText(ok + " 正常 · " + fail + " 异常" + (unsupported > 0 ? " · " + unsupported + " 未支持" : ""));
        }
    }

    private void saveHealthResult() {
        try {
            int ok = 0, fail = 0, unsupported = 0;
            for (SiteItem item : items) {
                if (item.status == SiteItem.STATUS_OK) ok++;
                else if (item.status == SiteItem.STATUS_FAIL) fail++;
                else if (item.status == SiteItem.STATUS_UNSUPPORTED) unsupported++;
            }
            getSharedPreferences("xingchen", MODE_PRIVATE).edit()
                    .putInt("health_ok", ok)
                    .putInt("health_fail", fail)
                    .putInt("health_unsupported", unsupported)
                    .putLong("health_time", System.currentTimeMillis())
                    .apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    static class SiteItem {
        static final int STATUS_TESTING = 0;
        static final int STATUS_OK = 1;
        static final int STATUS_FAIL = 2;
        static final int STATUS_UNSUPPORTED = 3;
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
            } else if (item.status == SiteItem.STATUS_UNSUPPORTED) {
                holder.tvStatus.setText("未支持");
                holder.tvStatus.setTextColor(0xFF61676F);
            } else {
                holder.tvStatus.setText("检测中…");
                holder.tvStatus.setTextColor(0xFF61676F);
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
