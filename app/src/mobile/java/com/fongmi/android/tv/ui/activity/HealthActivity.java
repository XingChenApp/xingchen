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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
            // 不自动重测：先显示上次缓存结果，用户点按钮才测
            restoreLastResult();
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

    /**
     * 恢复上次检测结果（只读缓存，不自动重测）
     */
    private void restoreLastResult() {
        int restored = 0;
        try {
            String json = getSharedPreferences("xingchen", MODE_PRIVATE).getString("health_results", null);
            if (json != null && !json.isEmpty()) {
                org.json.JSONObject obj = new org.json.JSONObject(json);
                for (SiteItem item : items) {
                    if (obj.has(item.key)) {
                        int status = obj.optInt(item.key, SiteItem.STATUS_UNKNOWN);
                        if (status >= SiteItem.STATUS_OK && status <= SiteItem.STATUS_UNSUPPORTED) {
                            item.status = status;
                            restored++;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        adapter.notifyDataSetChanged();
        if (restored > 0) {
            binding.btnRetest.setText("重新检测");
        } else {
            for (SiteItem item : items) item.status = SiteItem.STATUS_UNKNOWN;
            adapter.notifyDataSetChanged();
            binding.btnRetest.setText("开始检测");
        }
        updateSummary();
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
            // 批量检测走静默加载（同 PluginActivity 健康检查口径）：坏源只记检测结果＋强制日志，
            // 不许逐个弹「PY加载失败」刷屏。
            Spider spider = site.spiderQuiet();
            String json = callSpider(() -> spider.homeContent(true));
            Result result = Result.fromJson(json);
            if (result != null && result.getList() != null && !result.getList().isEmpty()) return true;
            // Fallback: some sources return an empty homeContent list but homeVideoContent has data
            // (same fallback logic as SiteApi.homeContent)
            String video = callSpider(spider::homeVideoContent);
            Result videoResult = Result.fromJson(video);
            return videoResult != null && videoResult.getList() != null && !videoResult.getList().isEmpty();
        } catch (Throwable e) {
            return false;
        }
    }

    // 探针走专用单线程池（同 PluginActivity 口径）：60 秒从真正开跑起表，不吃共用池排队时间，
    // 阻塞等待也不占着共用池线程干等同池任务。
    private String callSpider(Callable<String> call) throws Exception {
        ExecutorService probe = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = probe.submit(call);
            try {
                return future.get(60, TimeUnit.SECONDS);
            } finally {
                future.cancel(true);
            }
        } finally {
            probe.shutdownNow();
        }
    }

    private void updateSummary() {
        int ok = 0, fail = 0, unsupported = 0, testingCount = 0, unknown = 0;
        for (SiteItem item : items) {
            if (item.status == SiteItem.STATUS_OK) ok++;
            else if (item.status == SiteItem.STATUS_FAIL) fail++;
            else if (item.status == SiteItem.STATUS_UNSUPPORTED) unsupported++;
            else if (item.status == SiteItem.STATUS_UNKNOWN) unknown++;
            else testingCount++;
        }
        if (testingCount > 0) {
            binding.tvSummary.setText("检测中… " + ok + " 正常");
        } else if (unknown == items.size()) {
            binding.tvSummary.setText("尚未检测，点击开始检测");
        } else {
            long time = getSharedPreferences("xingchen", MODE_PRIVATE).getLong("health_time", 0);
            String base = ok + " 正常 · " + fail + " 异常" + (unsupported > 0 ? " · " + unsupported + " 未支持" : "");
            if (time > 0) {
                java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault());
                base += " · 上次 " + fmt.format(new java.util.Date(time));
            }
            binding.tvSummary.setText(base);
        }
    }

    private void saveHealthResult() {
        try {
            int ok = 0, fail = 0, unsupported = 0;
            org.json.JSONObject results = new org.json.JSONObject();
            for (SiteItem item : items) {
                if (item.status == SiteItem.STATUS_OK) ok++;
                else if (item.status == SiteItem.STATUS_FAIL) fail++;
                else if (item.status == SiteItem.STATUS_UNSUPPORTED) unsupported++;
                // 逐站点缓存，供下次进入直接显示
                if (item.status >= SiteItem.STATUS_OK && item.status <= SiteItem.STATUS_UNSUPPORTED) {
                    results.put(item.key, item.status);
                    // 同步到源列表的健康状态
                    com.fongmi.android.tv.setting.SiteHealthStore.recordManualCheck(item.key, item.status == SiteItem.STATUS_OK);
                }
            }
            getSharedPreferences("xingchen", MODE_PRIVATE).edit()
                    .putLong("health_time", System.currentTimeMillis())
                    .putString("health_results", results.toString())
                    .apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    static class SiteItem {
        static final int STATUS_TESTING = 0;
        static final int STATUS_OK = 1;
        static final int STATUS_FAIL = 2;
        static final int STATUS_UNSUPPORTED = 3;
        static final int STATUS_UNKNOWN = 4;
        final String key;
        final String name;
        final String type;
        int status = STATUS_UNKNOWN;

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
                holder.tvStatus.setTextColor(0xFF101216);
            } else if (item.status == SiteItem.STATUS_UNKNOWN) {
                holder.tvStatus.setText("未检测");
                holder.tvStatus.setTextColor(0xFF9AA0A6);
            } else {
                holder.tvStatus.setText("检测中…");
                holder.tvStatus.setTextColor(0xFF101216);
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
