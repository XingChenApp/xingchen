package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.clash.ClashSettings;
import com.fongmi.android.tv.clash.MihomoManager;
import com.fongmi.android.tv.clash.NodeStore;
import com.fongmi.android.tv.databinding.ActivityNodeListBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 节点列表页（2026-10-15 他拍板从代理页拆出）：订阅动辄几百个节点，不在代理页里长翻。
 * 带搜索（按名字过滤）；全选只勾当前显示的；点名字设为当前节点（内核在跑时重启内核）；
 * 每行可改名、删除；勾选后可批量删除。
 */
public class NodeListActivity extends BaseActivity {

    /** 语音口令「测延迟」打开本页时带这个标记，进页自动开测。 */
    public static final String EXTRA_AUTOTEST = "autotest";

    private ActivityNodeListBinding binding;
    private NodeAdapter adapter;
    private volatile boolean testing;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, NodeListActivity.class));
    }

    // 节点列表页底 #0B0E14 深色，白色图标（BaseActivity 默认已改深色图标，这里盖回）。
    @Override
    protected boolean useDarkSystemIcons() {
        return false;
    }

    protected ViewBinding getBinding() {
        binding = ActivityNodeListBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        // 页边框 16dp 用代码写死：布局里的 padding 在本页没生效（真机截图全贴边，
        // 他 19:0x 连问「框不是16dp吗」），代码设置不吃系统栏内边距那套。
        float d = getResources().getDisplayMetrics().density;
        binding.getRoot().setPadding((int) (16 * d), (int) (48 * d), (int) (16 * d), (int) (16 * d));
        adapter = new NodeAdapter();
        binding.rvNodes.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNodes.setAdapter(adapter);
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.refresh();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        binding.btnCheckAll.setOnClickListener(v -> {
            NodeStore store = NodeStore.get(this);
            for (NodeStore.Node n : adapter.data) store.setChecked(n.id, true);
            adapter.refresh();
        });
        binding.btnUncheckAll.setOnClickListener(v -> {
            NodeStore.get(this).clearChecked();
            adapter.refresh();
        });
        binding.btnDeleteChecked.setOnClickListener(v -> {
            NodeStore store = NodeStore.get(this);
            int n = store.getChecked().size();
            if (n == 0) {
                Notify.show("先勾选要删除的节点");
                return;
            }
            store.removeAll(this, store.getChecked());
            adapter.refresh();
            Notify.show("已删除 " + n + " 个节点");
        });
        binding.tvTest.setOnClickListener(v -> runNodeTest());
        adapter.refresh();
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_AUTOTEST, false)) {
            binding.getRoot().post(this::runNodeTest);
        }
    }

    /**
     * 测延迟（他 19:03 要求：右上角闪电开测、每个节点行上显示延迟、用来分好坏）。
     * 测法：直连节点服务器地址做一次连接计时，不依赖内核是否在跑；
     * 超时/连不上记 -1。10 条并发，逐个回刷行，结束报可用/超时数。
     */
    private void runNodeTest() {
        if (testing) return;
        List<NodeStore.Node> all = NodeStore.get(this).getAll();
        if (all.isEmpty()) {
            Notify.show("还没有节点可测");
            return;
        }
        testing = true;
        Notify.show("开始测延迟，共 " + all.size() + " 个节点");
        for (NodeStore.Node n : all) adapter.delays.put(n.id, -2);
        adapter.notifyDataSetChanged();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger done = new AtomicInteger();
        AtomicInteger ok = new AtomicInteger();
        for (NodeStore.Node n : all) {
            pool.execute(() -> {
                int ms = testOne(n);
                adapter.delays.put(n.id, ms);
                if (ms >= 0) ok.incrementAndGet();
                int finished = done.incrementAndGet();
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    if (finished == all.size()) {
                        testing = false;
                        Notify.show("测速完成：可用 " + ok.get() + " 个，超时 "
                                + (all.size() - ok.get()) + " 个");
                        com.github.catvod.crawler.SpiderDebug.log("clash",
                                "delay test total=%d ok=%d", all.size(), ok.get());
                    }
                });
            });
        }
        pool.shutdown();
    }

    private static int testOne(NodeStore.Node node) {
        String server = node.server();
        Object p = node.config.get("port");
        int port = 0;
        if (p instanceof Number) port = ((Number) p).intValue();
        else if (p != null) {
            try {
                port = Integer.parseInt(String.valueOf(p).trim());
            } catch (Exception ignored) {
            }
        }
        if (server.isEmpty() || port <= 0) return -1;
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(server, port), 2000);
            return (int) ((System.nanoTime() - start) / 1_000_000);
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) adapter.refresh();
    }

    private void showRename(NodeStore.Node node) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(node.name);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new MaterialAlertDialogBuilder(this, com.fongmi.android.tv.R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle("改名")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    if (NodeStore.get(this).rename(node.id, input.getText().toString())) {
                        adapter.refresh();
                        Notify.show("已改名");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private class NodeAdapter extends RecyclerView.Adapter<NodeAdapter.Holder> {

        private final List<NodeStore.Node> data = new ArrayList<>();
        /** 测延迟结果：节点 id → 毫秒；-1 超时/连不上，-2 测速中。 */
        final Map<String, Integer> delays = new HashMap<>();

        void refresh() {
            data.clear();
            String kw = binding.etSearch.getText() == null ? ""
                    : binding.etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
            List<NodeStore.Node> all = NodeStore.get(NodeListActivity.this).getAll();
            for (NodeStore.Node n : all) {
                if (kw.isEmpty() || (n.name != null && n.name.toLowerCase(Locale.ROOT).contains(kw))) {
                    data.add(n);
                }
            }
            boolean empty = data.isEmpty();
            binding.tvEmpty.setText(all.isEmpty() ? "还没有节点，先订阅或粘贴导入" : "没有匹配的节点");
            binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.rvNodes.setVisibility(empty ? View.GONE : View.VISIBLE);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_node, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            NodeStore.Node node = data.get(position);
            NodeStore store = NodeStore.get(NodeListActivity.this);
            String selected = ClashSettings.getSelectedNode(NodeListActivity.this);
            boolean isSel = node.id.equals(selected);
            h.name.setText(isSel ? node.name + "（当前）" : node.name);
            h.proto.setText(node.proto + " · " + node.server());
            Integer delay = delays.get(node.id);
            if (delay == null) {
                h.delay.setVisibility(View.GONE);
            } else {
                h.delay.setVisibility(View.VISIBLE);
                if (delay == -2) {
                    h.delay.setText("测速中…");
                    h.delay.setTextColor(0xFF9AA4B5);
                } else if (delay < 0) {
                    h.delay.setText("超时");
                    h.delay.setTextColor(0xFFFF6B6B);
                } else {
                    h.delay.setText("延迟 " + delay + "ms");
                    h.delay.setTextColor(delay < 300 ? 0xFF7ED957 : delay < 1000 ? 0xFFFFD700 : 0xFFFF9F43);
                }
            }
            h.check.setOnCheckedChangeListener(null);
            h.check.setChecked(store.getChecked().contains(node.id));
            h.check.setOnCheckedChangeListener((btn, checked) -> store.setChecked(node.id, checked));
            // 设为当前：整行可点。此前只有名字文字本身能点，点行上其它地方没反应
            // （他 18:58 真机报「点击节点没反应」）；勾选/改名/删除有各自的手柄、照常拦截。
            View.OnClickListener select = v -> {
                store.setSelected(NodeListActivity.this, node.id);
                refresh();
                if (MihomoManager.get().isRunning()) {
                    Notify.show("已切换节点，正在重启内核…");
                    Task.execute(() -> {
                        try {
                            MihomoManager m = MihomoManager.get();
                            m.stopSync();
                            m.startSync(getApplicationContext());
                        } catch (Exception e) {
                            runOnUiThread(() -> Notify.show("内核重启失败"));
                        }
                    });
                } else {
                    Notify.show("已设为当前节点");
                }
            };
            h.itemView.setOnClickListener(select);
            h.name.setOnClickListener(select);
            h.proto.setOnClickListener(select);
            h.rename.setOnClickListener(v -> showRename(node));
            h.delete.setOnClickListener(v -> {
                store.remove(NodeListActivity.this, node.id);
                refresh();
                Notify.show("已删除");
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final CheckBox check;
            final TextView name;
            final TextView proto;
            final TextView rename;
            final TextView delete;
            final TextView delay;

            Holder(View v) {
                super(v);
                check = v.findViewById(R.id.cb_node);
                name = v.findViewById(R.id.tv_name);
                proto = v.findViewById(R.id.tv_proto);
                rename = v.findViewById(R.id.tv_rename);
                delete = v.findViewById(R.id.tv_delete);
                delay = v.findViewById(R.id.tv_delay);
            }
        }
    }
}
