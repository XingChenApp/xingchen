package com.fongmi.android.tv.ui.activity;
import com.fongmi.android.tv.utils.ToastUtil;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.viewbinding.ViewBinding;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.databinding.ActivityPluginBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.adapter.PluginAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.bean.Plugin;
import com.fongmi.android.tv.bean.PluginHealth;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PluginActivity extends BaseActivity {
    private ActivityPluginBinding binding;
    private PluginAdapter adapter;
    private boolean isPy = true;
    private List<Plugin> plugins = new ArrayList<>();
    /** 健康检测进行中标记（防重入）；结果逐行回刷，退出重进只看持久化的上次结果。 */
    private boolean testing = false;
    private final Set<String> testingPaths = new HashSet<>();

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, PluginActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityPluginBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.tabPy.setOnClickListener(v -> switchTab(true));
        binding.tabJs.setOnClickListener(v -> switchTab(false));
        binding.btnImport.setOnClickListener(v -> importPlugins());
        binding.btnHealth.setOnClickListener(v -> runHealthCheck());
        binding.btnDeleteSelected.setOnClickListener(v -> deleteSelected());
        binding.cbSelectAll.setOnCheckedChangeListener((btn, checked) -> {
            if (adapter != null) adapter.selectAll(checked);
        });
        
        binding.rvPlugins.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PluginAdapter(plugins, this::onPluginChanged);
        binding.rvPlugins.setAdapter(adapter);

        switchTab(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // L-15：从子页/外部文件管理器回来重拉当前页签列表（照 EmbyServerActivity 等页的 onResume 口径）。
        // 未加载过（adapter 未建）不刷；健康检测进行中不刷，防列表被换打断逐行回刷。
        if (adapter != null && !testing) loadPlugins();
    }

    private void switchTab(boolean py) {
        isPy = py;
        // Update tab UI
        binding.tabPy.setBackgroundResource(py ? com.fongmi.android.tv.R.drawable.seg_selected : 0);
        binding.tabJs.setBackgroundResource(!py ? com.fongmi.android.tv.R.drawable.seg_selected : 0);
        binding.tabPy.setTextColor(py ? 0xFFFFFFFF : 0xFF5A5F69);
        binding.tabJs.setTextColor(!py ? 0xFFFFFFFF : 0xFF5A5F69);
        loadPlugins();
    }

    private void loadPlugins() {
        plugins.clear();
        File dir = new File(getFilesDir(), isPy ? "plugins/py" : "plugins/js");
        String ext = isPy ? ".py" : ".js";
        if (dir.exists()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(ext));
            if (files != null) {
                for (File f : files) {
                    plugins.add(new Plugin(f.getName(), f.getAbsolutePath(), isPy ? "py" : "js"));
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateCount();
    }

    /** 计数行：共 N 个插件 · 上次检测时间（结果持久，进页直接看上次，不自动重测）。 */
    private void updateCount() {
        String s = "共 " + plugins.size() + " 个插件";
        long last = PluginHealth.lastCheck(this, isPy ? "py" : "js");
        if (last > 0) s += " · 上次检测" + humanTime(last);
        binding.tvCount.setText(s);
    }

    /** 人话时间：刚刚 / X 分钟前 / X 小时前 / X 天前。 */
    private static String humanTime(long ts) {
        long d = System.currentTimeMillis() - ts;
        long min = d / 60000;
        if (min < 1) return "刚刚";
        if (min < 60) return min + "分钟前";
        long h = min / 60;
        if (h < 24) return h + "小时前";
        return (h / 24) + "天前";
    }

    private void importPlugins() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, 1001);
    }

    private void deleteSelected() {
        List<Plugin> toDelete = adapter.getSelected();
        List<String> deleted = new ArrayList<>();
        for (Plugin p : toDelete) {
            if (new File(p.getPath()).delete()) deleted.add(p.getPath());
        }
        invalidateFiles(deleted);
        loadPlugins();
    }

    // 插件文件被删/覆盖后：只清这些文件对应的蜘蛛＋首页缓存并让首页立刻重载，
    // 否则首页还挂着已删插件的旧内容、覆盖的新版也不生效。
    // 旧写法是删／导一个插件就全清全部 py 蜘蛛——源一多，下次首页全量重建。
    // py 走按文件单清；js 沿用原有全清（js 加载无 Python 初始化成本，不在本次范围）。
    private void invalidateFiles(List<String> absolutePaths) {
        try {
            boolean js = false;
            for (String path : absolutePaths) {
                if (path != null && path.endsWith(".py")) BaseLoader.get().invalidatePyScript(path);
                else if (path != null && path.endsWith(".js")) js = true;
            }
            if (js) BaseLoader.get().clearJs();
        } catch (Throwable ignored) {}
        RefreshEvent.home();
    }

    private void onPluginChanged() {
        updateCount();
    }

    // ================= 插件健康检测（简化版） =================
    //
    // 测法：当前页签的插件一起测（10 并发，不排队）。每个插件：静默加载 spider
    // （失败不弹 Toast）＋ 调一次 homeContent(true) 验证真能出数据。
    // 档位：≤5s 能用；5~15s 慢；异常/超时/空数据 挂了（标红）。
    // 阈值依据：壳子自己给 spider 首页的耐心是 35s（SiteApi.HOME_SUB_TIMEOUT_SECONDS，
    // 交互式等待天花板）；节点测延迟用 2s 纯 TCP 超时（网络基线）。探针取 15s 超时——
    // 超过 15s 没回首页数据的源交互式已没法用；5s 是"感觉不卡"的分界（健康站点手机流量下
    // 一般 1~3s 回来）。结果按插件路径持久化，只有点检测钮才重测，进页绝不自动测。

    private static final long PROBE_TIMEOUT_MS = 15000;
    private static final long SLOW_LINE_MS = 5000;

    private void runHealthCheck() {
        if (testing) return;
        final List<Plugin> all = new ArrayList<>(plugins);
        if (all.isEmpty()) {
            ToastUtil.show(this, "没有插件可测", Toast.LENGTH_SHORT);
            return;
        }
        testing = true;
        testingPaths.clear();
        for (Plugin p : all) testingPaths.add(p.getPath());
        adapter.setTesting(testingPaths);
        adapter.notifyDataSetChanged();
        final String type = isPy ? "py" : "js";
        ToastUtil.show(this, "开始检测，共 " + all.size() + " 个插件", Toast.LENGTH_SHORT);
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(10, all.size()));
        AtomicInteger done = new AtomicInteger();
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger slow = new AtomicInteger();
        AtomicInteger dead = new AtomicInteger();
        for (Plugin p : all) {
            pool.execute(() -> {
                int grade;
                long ms;
                try {
                    ms = probeOne(p);
                    grade = ms <= SLOW_LINE_MS ? PluginHealth.OK : PluginHealth.SLOW;
                } catch (Throwable t) {
                    grade = PluginHealth.DEAD;
                    ms = 0;
                }
                PluginHealth.save(PluginActivity.this, p.getPath(), grade, ms);
                if (grade == PluginHealth.OK) ok.incrementAndGet();
                else if (grade == PluginHealth.SLOW) slow.incrementAndGet();
                else dead.incrementAndGet();
                int finished = done.incrementAndGet();
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    if (finished == all.size()) {
                        testing = false;
                        testingPaths.clear();
                        adapter.setTesting(null);
                        PluginHealth.setLastCheck(PluginActivity.this, type);
                        updateCount();
                        adapter.notifyDataSetChanged();
                        ToastUtil.show(PluginActivity.this,
                                "检测完成：能用 " + ok.get() + "，慢 " + slow.get() + "，挂了 " + dead.get(),
                                Toast.LENGTH_SHORT);
                    }
                });
            });
        }
        pool.shutdown();
    }

    /**
     * 探一个插件：静默加载＋一次 homeContent；返回耗时毫秒。
     * 抛异常 = 挂了（加载失败 / 超时 / 空数据）。
     */
    private long probeOne(Plugin p) throws Exception {
        long start = System.nanoTime();
        ExecutorService one = Executors.newSingleThreadExecutor();
        try {
            Future<String> f = one.submit(() -> {
                Spider spider = BaseLoader.get().getSpiderQuiet(
                        "health:" + p.getPath(), p.getPath(), "", null);
                try {
                    if (spider instanceof SpiderNull) throw new RuntimeException("spider load failed");
                    String home = spider.homeContent(true);
                    if (home == null || home.trim().isEmpty()) throw new RuntimeException("empty home");
                    return home;
                } finally {
                    // 不污染正常缓存：健康检查用的 key 用完即清
                    try {
                        if ("py".equals(p.getType())) BaseLoader.get().removePySpider("health:" + p.getPath());
                        else BaseLoader.get().removeJsSpider("health:" + p.getPath());
                    } catch (Throwable ignored) {
                    }
                }
            });
            f.get(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return (System.nanoTime() - start) / 1_000_000;
        } finally {
            one.shutdownNow();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            List<String> imported = new ArrayList<>();
            try {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        String dest = copyPluginFile(data.getClipData().getItemAt(i).getUri());
                        if (dest != null) imported.add(dest);
                    }
                } else if (data.getData() != null) {
                    String dest = copyPluginFile(data.getData());
                    if (dest != null) imported.add(dest);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // 同一脚本的缀号变体一并清掉（与打开方式导入同口径，只留正名一份，
            // fix168-pyimport2）；删掉的路径并进失效名单，免得旧蜘蛛还认得它
            try {
                java.io.File dir = new java.io.File(getFilesDir(), isPy ? "plugins/py" : "plugins/js");
                for (String dest : new ArrayList<>(imported)) {
                    imported.addAll(PluginFileJudge.purgeAvoidVariants(dir, new java.io.File(dest).getName()));
                }
            } catch (Throwable ignored) {
            }
            invalidateFiles(imported);
            loadPlugins();
        }
    }

    private String copyPluginFile(android.net.Uri uri) throws Exception {
        String fileName = getFileName(uri);
        if (fileName == null) return null;
        fileName = fileName.trim();
        if (!fileName.endsWith(".py") && !fileName.endsWith(".js")) return null;
        // 名归一：带避让缀号的名字（「名(1).py」）按正名落盘，同名直接覆盖，
        // 不许缀号坐实成另一份（他亲令，fix168-pyimport2）
        fileName = PluginFileJudge.canonicalName(fileName);
        java.io.File dir = new java.io.File(getFilesDir(), isPy ? "plugins/py" : "plugins/js");
        if (!dir.exists()) dir.mkdirs();
        java.io.File dest = new java.io.File(dir, fileName);
        try (java.io.InputStream in = getContentResolver().openInputStream(uri);
             java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
        return dest.getAbsolutePath();
    }

    private String getFileName(android.net.Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception e) {}
        }
        if (result == null) result = uri.getLastPathSegment();
        return result;
    }
}