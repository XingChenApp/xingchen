package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.clash.ClashSettings;
import com.fongmi.android.tv.clash.KernelDownloader;
import com.fongmi.android.tv.clash.MihomoManager;
import com.fongmi.android.tv.clash.NodeStore;
import com.fongmi.android.tv.clash.SourceProxyPolicy;
import com.fongmi.android.tv.clash.SubParser;
import com.fongmi.android.tv.databinding.ActivityNodeManageBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 代理页：总开关、内核状态、订阅（网址订阅/本地文件订阅）、粘贴导入、节点列表入口（黄色卡片，列表本体在 NodeListActivity）、全部走节点、按源定向入口。 */
public class NodeManageActivity extends BaseActivity {

    private static final int REQ_PICK_SUB_FILE = 0x5b01;
    /** 单个订阅文件读取上限 10MB，防大文件撑爆内存。 */
    private static final int MAX_SUB_FILE_BYTES = 10 * 1024 * 1024;

    private ActivityNodeManageBinding binding;
    private boolean suppressSwitch;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, NodeManageActivity.class));
    }

    // 节点管理页底 #0B0E14 深色，白色图标（BaseActivity 默认已改深色图标，这里盖回）。
    @Override
    protected boolean useDarkSystemIcons() {
        return false;
    }

    protected ViewBinding getBinding() {
        binding = ActivityNodeManageBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        refreshSubInput();
        binding.swGlobal.setChecked(ClashSettings.isGlobalMode(this));
        binding.swGlobal.setOnCheckedChangeListener((btn, checked) -> {
            ClashSettings.setGlobalMode(this, checked);
            Notify.show(checked ? "全部走节点已开启" : "全部走节点已关闭");
        });
        binding.btnSaveSub.setOnClickListener(v -> {
            String text = binding.etSubUrl.getText().toString();
            if (ClashSettings.isSubLocal(this) && !looksLikeUrl(text)) {
                // 本地模式下框里是文件名：误点保存不许把文件名存成网址
                Notify.show("当前是本地订阅，粘贴网址后再点保存可切回网址订阅");
                return;
            }
            ClashSettings.setSubUrl(this, text);
            // 保存网址即切回网址订阅（原行为：保存框里文字为订阅地址；新增含义：顺带退出本地模式）
            ClashSettings.setSubUrlSource(this);
            refreshSubInput();
            Notify.show("订阅地址已保存");
        });
        binding.btnRefreshSub.setOnClickListener(v -> refreshSub());
        binding.btnPickSubFile.setOnClickListener(v -> openSubFilePicker());
        binding.btnImport.setOnClickListener(v -> importLinks());
        binding.cardNodeList.setOnClickListener(v -> NodeListActivity.start(this));
        binding.cardPolicy.setOnClickListener(v -> SourcePolicyActivity.start(this));
        suppressSwitch = true;
        binding.swNode.setChecked(ClashSettings.isEnabled(this));
        suppressSwitch = false;
        binding.swNode.setOnCheckedChangeListener((btn, checked) -> {
            if (suppressSwitch) return;
            setNodeEnabled(checked);
        });
        MihomoManager.get().setListener(new MihomoManager.Listener() {
            @Override
            public void onStarted() {
                runOnUiThread(() -> {
                    binding.tvNodeStatus.setText(MihomoManager.get().isRunning() ? "运行中" : "未启用");
                    refreshKernel();
                });
            }

            @Override
            public void onStopped() {
                runOnUiThread(() -> binding.tvNodeStatus.setText("已停止"));
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> binding.tvNodeStatus.setText("启动失败：" + msg));
            }
        });
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    @Override
    protected void onDestroy() {
        MihomoManager.get().setListener(null);
        super.onDestroy();
    }

    private void setNodeEnabled(boolean checked) {
        if (checked && !KernelDownloader.isReady(this)) {
            binding.tvNodeStatus.setText("正在下载内核，请稍候…");
        } else {
            binding.tvNodeStatus.setText(checked ? "正在启动…" : "正在停止…");
        }
        MihomoManager.get().setEnabled(this, checked);
    }

    private void refreshAll() {
        suppressSwitch = true;
        binding.swNode.setChecked(ClashSettings.isEnabled(this));
        suppressSwitch = false;
        refreshKernel();
        refreshPolicySub();
        refreshNodeListSub();
        refreshSubInput();
        if (!ClashSettings.isEnabled(this)) binding.tvNodeStatus.setText("未启用");
        else binding.tvNodeStatus.setText(MihomoManager.get().isRunning() ? "运行中" : "未启动");
    }

    private void refreshKernel() {
        // 随包内置的内核在位时直接认它：内置版不写下载记录，绝不能误报「未下载」。
        if (KernelDownloader.bundledBinary(this) != null) {
            binding.tvKernelStatus.setText("内置内核 v" + KernelDownloader.wantVersion(this) + "，随 App 版本更新");
            return;
        }
        // 内置真不在位才走下载记录的老文案。
        String have = ClashSettings.getKernelVersion(this);
        String want = KernelDownloader.wantVersion(this);
        if (have == null || have.isEmpty()) {
            binding.tvKernelStatus.setText("未下载，打开节点开关后自动下载");
        } else if (have.equals(want)) {
            binding.tvKernelStatus.setText("当前版本 " + have + " / 最新 " + want);
        } else {
            binding.tvKernelStatus.setText("当前版本 " + have + " / 最新 " + want + "，有新版，打开开关后自动更新");
        }
    }

    private void refreshPolicySub() {
        int n = SourceProxyPolicy.listEnabled(this).size();
        binding.tvPolicySub.setText(n == 0 ? "默认全部直连" : "已开 " + n + " 个源走节点");
    }

    /** 黄色卡片副标题：节点总数 + 当前节点名。列表本体在 NodeListActivity。 */
    private void refreshNodeListSub() {
        List<NodeStore.Node> all = NodeStore.get(this).getAll();
        if (all.isEmpty()) {
            binding.tvNodeListSub.setText("还没有节点");
            return;
        }
        NodeStore.Node n = NodeStore.get(this).getSelected(this);
        String cur = n == null || n.name == null || n.name.isEmpty() ? "未选节点" : n.name;
        binding.tvNodeListSub.setText("共 " + all.size() + " 个 · 当前：" + cur);
    }

    /** 订阅输入框显示：网址模式显示保存的地址，本地模式显示文件名（不塞文件内容）。 */
    private void refreshSubInput() {
        if (ClashSettings.isSubLocal(this)) {
            List<String> names = new ArrayList<>();
            for (String s : ClashSettings.getSubLocalUris(this)) {
                String name = subFileName(Uri.parse(s));
                if (!name.isEmpty()) names.add(name);
            }
            binding.etSubUrl.setText(names.isEmpty() ? "本地订阅（文件不可读，请重新选择）" : String.join("、", names));
        } else {
            binding.etSubUrl.setText(ClashSettings.getSubUrl(this));
        }
    }

    private boolean looksLikeUrl(String text) {
        String t = text == null ? "" : text.trim().toLowerCase();
        return t.startsWith("http://") || t.startsWith("https://");
    }

    /** 系统文件选择器：支持多选订阅文件（yaml/yml/txt 等），不加新权限不加新依赖。 */
    private void openSubFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "text/yaml", "text/x-yaml", "application/x-yaml", "application/yaml", "application/json"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(intent, "选择订阅文件"), REQ_PICK_SUB_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_SUB_FILE || resultCode != Activity.RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null) uris.add(u);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) {
            Notify.show("未选中文件");
            return;
        }
        importSubFiles(uris);
    }

    /**
     * 本地订阅导入：逐个文件走跟网址订阅完全同一套解析（SubParser.parse）
     * 与保存流程（replaceSource(SRC_SUB) 整批替换，同网址刷新一致的按 id 去重）。
     * 先解析成功再落存：全部失败时原有配置不动。
     */
    private void importSubFiles(List<Uri> uris) {
        Notify.show("正在导入订阅文件…");
        Task.execute(() -> {
            List<String> okUriStrings = new ArrayList<>();
            List<String> failNames = new ArrayList<>();
            List<NodeStore.Node> nodes = new ArrayList<>();
            for (Uri uri : uris) {
                String name = subFileName(uri);
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                try {
                    List<SubParser.NodeDraft> drafts = SubParser.parse(readUriText(uri));
                    if (drafts.isEmpty()) {
                        failNames.add(name + "：解析失败");
                        continue;
                    }
                    for (SubParser.NodeDraft d : drafts) nodes.add(NodeStore.fromDraft(d, NodeStore.SRC_SUB));
                    okUriStrings.add(uri.toString());
                } catch (Exception e) {
                    failNames.add(name + "：读取失败");
                }
            }
            if (nodes.isEmpty()) {
                runOnUiThread(() -> Notify.show("导入失败：没有解析出可用节点，原有配置未动"));
                return;
            }
            NodeStore.get(this).replaceSource(this, NodeStore.SRC_SUB, nodes);
            ClashSettings.setSubLocal(this, okUriStrings);
            int fileCount = okUriStrings.size();
            int nodeCount = nodes.size();
            com.github.catvod.crawler.SpiderDebug.log("clash", "sub file import total=%d ok=%d failed=%d nodes=%d stored=%d",
                    uris.size(), fileCount, failNames.size(), nodeCount, NodeStore.get(this).size());
            runOnUiThread(() -> {
                refreshSubInput();
                refreshNodeListSub();
                String msg = "已导入 " + fileCount + " 个文件，共 " + nodeCount + " 个节点（本地订阅）";
                if (!failNames.isEmpty()) msg += "；" + failNames.size() + " 个文件失败：" + String.join("、", failNames);
                Notify.show(msg);
            });
        });
    }

    /** 本地文件 Uri 的显示名：优先系统文档名，取不到退回路径尾段。 */
    private String subFileName(Uri uri) {
        if (uri == null) return "";
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception ignored) {
        }
        String seg = uri.getLastPathSegment();
        return seg == null ? "" : seg;
    }

    private String readUriText(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new java.io.IOException("open failed");
            byte[] buf = new byte[8192];
            int total = 0, n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_SUB_FILE_BYTES) throw new java.io.IOException("file too large");
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void refreshSub() {
        if (ClashSettings.isSubLocal(this)) {
            refreshLocalSub();
            return;
        }
        String url = ClashSettings.getSubUrl(this);
        if (url == null || url.isEmpty()) {
            Notify.show("先填写并保存订阅地址");
            return;
        }
        Notify.show("正在刷新订阅…");
        Task.execute(() -> {
            try {
                String content = OkHttp.string(url);
                List<SubParser.NodeDraft> drafts = SubParser.parse(content);
                if (drafts.isEmpty()) {
                    runOnUiThread(() -> Notify.show("订阅内容为空或解析失败"));
                    return;
                }
                List<NodeStore.Node> nodes = new ArrayList<>();
                for (SubParser.NodeDraft d : drafts) nodes.add(NodeStore.fromDraft(d, NodeStore.SRC_SUB));
                // 整批替换订阅节点：优选订阅每次 IP 不同，只累加会每次多出一批重复
                NodeStore.get(this).replaceSource(this, NodeStore.SRC_SUB, nodes);
                int count = drafts.size();
                int stored = NodeStore.get(this).size();
                com.github.catvod.crawler.SpiderDebug.log("clash", "sub parse total=%d ok=%d failed=%d schemes=%s stored=%d",
                        SubParser.lastTotal, count, SubParser.lastFailed, SubParser.lastFailedSchemes, stored);
                runOnUiThread(() -> {
                    refreshNodeListSub();
                    refreshKernel();
                    Notify.show("订阅已更新 " + count + " 个节点（列表共 " + stored + " 个）");
                });
            } catch (Exception e) {
                runOnUiThread(() -> Notify.show("订阅刷新失败"));
            }
        });
    }

    /**
     * 本地订阅刷新：重读记住的全部文件 Uri（每个取过持久读权限），
     * 解析与保存跟导入同一套流程（SubParser.parse + replaceSource 整批替换）。
     * 全部失败时原有配置不动；读不出/解析失败的文件从记住的列表里剔除（下次不再重试）。
     */
    private void refreshLocalSub() {
        List<String> uriStrings = ClashSettings.getSubLocalUris(this);
        if (uriStrings.isEmpty()) {
            Notify.show("本地订阅文件未记录，请重新选择文件");
            return;
        }
        Notify.show("正在重读本地订阅文件…");
        Task.execute(() -> {
            List<String> keepUris = new ArrayList<>();
            List<String> failNames = new ArrayList<>();
            List<NodeStore.Node> nodes = new ArrayList<>();
            for (String s : uriStrings) {
                Uri uri = Uri.parse(s);
                String name = subFileName(uri);
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                try {
                    List<SubParser.NodeDraft> drafts = SubParser.parse(readUriText(uri));
                    if (drafts.isEmpty()) {
                        failNames.add(name + "：解析失败");
                        continue;
                    }
                    for (SubParser.NodeDraft d : drafts) nodes.add(NodeStore.fromDraft(d, NodeStore.SRC_SUB));
                    keepUris.add(s);
                } catch (Exception e) {
                    failNames.add(name + "：读取失败");
                }
            }
            if (nodes.isEmpty()) {
                runOnUiThread(() -> Notify.show("本地订阅刷新失败：文件读不出或解析失败，原有节点未动"));
                return;
            }
            NodeStore.get(this).replaceSource(this, NodeStore.SRC_SUB, nodes);
            ClashSettings.setSubLocal(this, keepUris);
            int fileCount = keepUris.size();
            int nodeCount = nodes.size();
            runOnUiThread(() -> {
                refreshSubInput();
                refreshNodeListSub();
                String msg = "本地订阅已更新 " + fileCount + " 个文件，共 " + nodeCount + " 个节点";
                if (!failNames.isEmpty()) msg += "；" + failNames.size() + " 个文件失败：" + String.join("、", failNames);
                Notify.show(msg);
            });
        });
    }

    private void importLinks() {
        String text = binding.etImport.getText().toString();
        if (text.trim().isEmpty()) {
            Notify.show("先粘贴节点链接");
            return;
        }
        Task.execute(() -> {
            List<SubParser.NodeDraft> drafts = SubParser.parse(text);
            if (drafts.isEmpty()) {
                runOnUiThread(() -> Notify.show("没有解析出可用节点"));
                return;
            }
            List<NodeStore.Node> nodes = new ArrayList<>();
            for (SubParser.NodeDraft d : drafts) nodes.add(NodeStore.fromDraft(d, NodeStore.SRC_MANUAL));
            NodeStore.get(this).addAll(nodes);
            int count = drafts.size();
            com.github.catvod.crawler.SpiderDebug.log("clash", "import parse total=%d ok=%d failed=%d schemes=%s stored=%d",
                    SubParser.lastTotal, count, SubParser.lastFailed, SubParser.lastFailedSchemes, NodeStore.get(this).size());
            runOnUiThread(() -> {
                binding.etImport.setText("");
                refreshNodeListSub();
                Notify.show("已导入 " + count + " 个节点");
            });
        });
    }

}
