package com.fongmi.android.tv.clash;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组装 mihomo config.yaml。
 * <p>
 * 骨架说明（用户已拍板的分工）：
 * <ul>
 * <li>只开本地混合口（HTTP/SOCKS5 同口），不开 VPN、不监听局域网；
 * <li>看门人在壳子：只有策略表命中的源的流量才会被壳子送到本地口，
 *     所以到达内核的流量默认走节点组（rules 末行 MATCH,PROXY）；
 * <li>「默认直连」落实在壳子层（SourceProxyPolicy 默认全直连），
 *     不是内核规则层——若将来要内核侧按域名分流，把末行改回 MATCH,DIRECT
 *     并在 rules 上方加 DOMAIN 规则即可（一行改动，见 README）。
 * </ul>
 * 选中的节点排在 select 组第一位（mihomo select 组启动默认选第一个）；
 * 切节点 = 重写配置 + 重启内核（本地操作，毫秒级）。
 */
public final class ConfigBuilder {

    private ConfigBuilder() {
    }

    public static File configFile(Context context) {
        return new File(KernelDownloader.workDir(context), "config.yaml");
    }

    /** 生成配置文本。无可用节点时抛 IllegalStateException（调用方据此降级）。 */
    public static String build(Context context) {
        Context app = context.getApplicationContext();
        int port = ClashSettings.getMixedPort(app);
        List<NodeStore.Node> nodes = NodeStore.get(app).getAll();
        if (nodes.isEmpty()) throw new IllegalStateException("没有可用节点");

        String selected = ClashSettings.getSelectedNode(app);
        List<Map<String, Object>> proxies = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        // 选中节点排第一
        NodeStore.Node first = null;
        for (NodeStore.Node n : nodes) {
            if (n.id.equals(selected)) {
                first = n;
                break;
            }
        }
        List<NodeStore.Node> ordered = new ArrayList<>();
        if (first != null) ordered.add(first);
        for (NodeStore.Node n : nodes) {
            if (first == null || !n.id.equals(first.id)) ordered.add(n);
        }

        for (NodeStore.Node n : ordered) {
            Map<String, Object> p = new LinkedHashMap<>(n.config);
            String name = uniqueName(n.name, seen);
            seen.add(name);
            p.put("name", name);
            proxies.add(p);
            names.add(name);
        }

        Map<String, Object> group = new LinkedHashMap<>();
        group.put("name", "PROXY");
        group.put("type", "select");
        group.put("proxies", names);
        List<Object> groups = new ArrayList<>();
        groups.add(group);

        List<Object> rules = new ArrayList<>();
        rules.add("MATCH,PROXY");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mixed-port", port);
        root.put("allow-lan", false);
        root.put("mode", "rule");
        root.put("log-level", "warning");
        root.put("ipv6", false);
        root.put("proxies", proxies);
        root.put("proxy-groups", groups);
        root.put("rules", rules);
        return MiniYaml.dump(root);
    }

    /** 生成并写盘，返回 config 文件。 */
    public static File write(Context context) throws Exception {
        String yaml = build(context);
        File f = configFile(context.getApplicationContext());
        FileOutputStream out = new FileOutputStream(f);
        out.write(yaml.getBytes("UTF-8"));
        out.close();
        return f;
    }

    private static String uniqueName(String base, Set<String> seen) {
        if (base == null || base.isEmpty()) base = "node";
        String name = base;
        int i = 2;
        while (seen.contains(name)) {
            name = base + " (" + i + ")";
            i++;
        }
        return name;
    }
}
