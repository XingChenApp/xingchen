package com.fongmi.android.tv.clash;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 节点仓库：订阅节点 + 手动（粘贴）节点统一存放，JSON 落盘。
 * <p>
 * 节点管理能力：当前选中节点（单选，存在 ClashSettings）、
 * 勾选多选（内存态，不落盘）、全选、批量删除、单个删除、改名。
 * <p>
 * 线程：所有方法 synchronized；IO 很小，直接在调用线程做，
 * 批量导入这类重活调用方自己放后台线程。
 */
public final class NodeStore {

    /** 节点来源：订阅 / 手动粘贴。 */
    public static final String SRC_SUB = "sub";
    public static final String SRC_MANUAL = "manual";

    public static class Node {
        /** id = md5(link)；YAML 订阅节点无 link 时用 md5(name + server + port)。 */
        public String id = "";
        public String name = "";
        /** 原始分享链接；YAML 订阅节点为空。 */
        public String link = "";
        /** 协议：vless/vmess/ss/ssr/trojan/hysteria2/tuic/wireguard/socks5/http。 */
        public String proto = "";
        public String source = SRC_MANUAL;
        /** mihomo proxies 条目（完整 map，含 name/type/server/port 等）。 */
        public Map<String, Object> config = new LinkedHashMap<>();
        public long updatedAt = 0;
        /** 用户是否手动改过名：订阅刷新同 id 覆盖时保留改名。 */
        public boolean nameCustomized = false;

        public String server() {
            Object s = config.get("server");
            return s == null ? "" : String.valueOf(s);
        }
    }

    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<Node>>() {
    }.getType();

    private final File file;
    private List<Node> nodes = new ArrayList<>();
    /** 勾选多选：内存态，界面勾选用。 */
    private final Set<String> checked = new HashSet<>();

    private NodeStore(File file) {
        this.file = file;
        load();
    }

    private static NodeStore instance;

    public static synchronized NodeStore get(Context context) {
        if (instance == null) {
            File dir = new File(context.getApplicationContext().getFilesDir(), "clash");
            instance = new NodeStore(new File(dir, "nodes.json"));
        }
        return instance;
    }

    /** 单元测试/语法检查用：不依赖 Context。 */
    static NodeStore forFile(File file) {
        return new NodeStore(file);
    }

    public synchronized List<Node> getAll() {
        return new ArrayList<>(nodes);
    }

    public synchronized Node get(String id) {
        for (Node n : nodes) if (n.id.equals(id)) return n;
        return null;
    }

    public synchronized int size() {
        return nodes.size();
    }

    /** 新增或按 id 更新（订阅刷新时同 id 覆盖配置、保留改名）。 */
    public synchronized void addOrUpdate(Node node) {
        if (node == null || node.id.isEmpty()) return;
        for (int i = 0; i < nodes.size(); i++) {
            Node old = nodes.get(i);
            if (old.id.equals(node.id)) {
                if (!old.name.equals(node.name) && old.nameCustomized) {
                    node.name = old.name;
                }
                node.nameCustomized = old.nameCustomized;
                nodes.set(i, node);
                save();
                return;
            }
        }
        node.updatedAt = System.currentTimeMillis();
        nodes.add(node);
        save();
    }

    public synchronized void addAll(List<Node> list) {
        for (Node n : list) addOrUpdate(n);
    }

    /**
     * 订阅刷新：同来源节点整批替换，不靠 id 累加。
     * 优选类订阅每次下发的服务器地址都不同、按地址算的 id 全变，只靠 id 去重
     * 会每次多出一整批重复（他真机实证：每刷新一次多 16 个）。
     * 改名继承先按 id、再按名字接（改过名的 YAML 节点无稳定键，接不上不强求）；
     * 选中节点被替换时按名字接续，接不上顺延第一个剩余节点。
     */
    public synchronized void replaceSource(Context context, String source, List<Node> fresh) {
        String selected = ClashSettings.getSelectedNode(context);
        Node oldSelected = get(selected);
        Map<String, Node> oldById = new HashMap<>();
        Map<String, Node> oldByName = new HashMap<>();
        for (Node n : nodes) {
            if (source.equals(n.source)) {
                oldById.put(n.id, n);
                if (!oldByName.containsKey(n.name)) oldByName.put(n.name, n);
            }
        }
        nodes.removeIf(n -> source.equals(n.source));
        Set<String> seen = new HashSet<>();
        for (Node n : fresh) {
            if (n == null || n.id.isEmpty() || !seen.add(n.id)) continue;
            Node old = oldById.get(n.id);
            if (old == null) old = oldByName.get(n.name);
            if (old != null && old.nameCustomized && !old.name.isEmpty()) {
                n.name = old.name;
                n.nameCustomized = true;
            }
            n.updatedAt = System.currentTimeMillis();
            nodes.add(n);
        }
        if (oldSelected != null && source.equals(oldSelected.source)) {
            String next = "";
            for (Node n : nodes) {
                if (source.equals(n.source) && n.name.equals(oldSelected.name)) {
                    next = n.id;
                    break;
                }
            }
            if (next.isEmpty()) next = nodes.isEmpty() ? "" : nodes.get(0).id;
            ClashSettings.setSelectedNode(context, next);
        }
        Set<String> alive = new HashSet<>();
        for (Node n : nodes) alive.add(n.id);
        checked.retainAll(alive);
        save();
    }

    /** 单个删除；删的是当前选中节点时，选中顺延给第一个剩余节点或清空。 */
    public synchronized void remove(Context context, String id) {
        nodes.removeIf(n -> n.id.equals(id));
        checked.remove(id);
        if (id.equals(ClashSettings.getSelectedNode(context))) {
            ClashSettings.setSelectedNode(context, nodes.isEmpty() ? "" : nodes.get(0).id);
        }
        save();
    }

    /** 批量删除（勾选多选后调用）。 */
    public synchronized void removeAll(Context context, Set<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        String selected = ClashSettings.getSelectedNode(context);
        nodes.removeIf(n -> ids.contains(n.id));
        checked.removeAll(ids);
        if (ids.contains(selected)) {
            ClashSettings.setSelectedNode(context, nodes.isEmpty() ? "" : nodes.get(0).id);
        }
        save();
    }

    public synchronized void clear(Context context) {
        nodes.clear();
        checked.clear();
        ClashSettings.setSelectedNode(context, "");
        save();
    }

    /** 改名：只改展示名，不影响 id 与配置。 */
    public synchronized boolean rename(String id, String newName) {
        Node n = get(id);
        if (n == null || newName == null || newName.trim().isEmpty()) return false;
        n.name = newName.trim();
        n.nameCustomized = true;
        save();
        return true;
    }

    /** 当前选中节点（单选）。 */
    public synchronized Node getSelected(Context context) {
        return get(ClashSettings.getSelectedNode(context));
    }

    public synchronized void setSelected(Context context, String id) {
        if (id == null || id.isEmpty() || get(id) != null) {
            ClashSettings.setSelectedNode(context, id == null ? "" : id);
        }
    }

    // ---- 勾选多选（内存态） ----

    public synchronized void setChecked(String id, boolean on) {
        if (on) checked.add(id);
        else checked.remove(id);
    }

    public synchronized void checkAll() {
        for (Node n : nodes) checked.add(n.id);
    }

    public synchronized void clearChecked() {
        checked.clear();
    }

    public synchronized Set<String> getChecked() {
        return new HashSet<>(checked);
    }

    public synchronized int checkedCount() {
        return checked.size();
    }

    // ---- 持久化 ----

    private void load() {
        try {
            if (!file.exists()) return;
            FileInputStream in = new FileInputStream(file);
            byte[] buf = new byte[(int) file.length()];
            int off = 0, r;
            while (off < buf.length && (r = in.read(buf, off, buf.length - off)) > 0) off += r;
            in.close();
            List<Node> list = GSON.fromJson(new String(buf, 0, off, "UTF-8"), LIST_TYPE);
            if (list != null) nodes = list;
            // Gson 把 Map<String,Object> 里的数字一律读成 Double，重载后 port 会变 443.0，
            // MiniYaml.dump 照 Number.toString 输出 "443.0"，mihomo 配置非法。
            // 落盘前的内存形态（MiniYaml.parseScalar/LinkParser）整数都是 Integer/Long，
            // 故读盘后在此把整数值 Double 归一回去（嵌套 Map/List 递归）。
            for (Node n : nodes) {
                if (n != null && n.config != null) n.config = normalizeMap(n.config);
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeMap(Map<String, Object> map) {
        // 建新表回填，不原地改 Gson 的 LinkedTreeMap 条目（其 Entry.setValue 行为不作依赖）。
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) out.put(e.getKey(), normalizeValue(e.getValue()));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeValue(Object v) {
        if (v instanceof Map) return normalizeMap((Map<String, Object>) v);
        if (v instanceof List) {
            List<Object> list = (List<Object>) v;
            for (int i = 0; i < list.size(); i++) list.set(i, normalizeValue(list.get(i)));
            return list;
        }
        if (v instanceof Double) {
            double d = (Double) v;
            if (Double.isFinite(d) && d == Math.floor(d)) {
                if (d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) return (int) d;
                return (long) d;
            }
        }
        return v;
    }

    private void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            File tmp = new File(file.getParent(), file.getName() + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(GSON.toJson(nodes).getBytes("UTF-8"));
            out.close();
            if (!tmp.renameTo(file)) {
                file.delete();
                tmp.renameTo(file);
            }
        } catch (Exception ignored) {
        }
    }

    /** 从 SubParser.NodeDraft 建 Node。link 为空（YAML 订阅）时用 name+server+port 做 id。 */
    public static Node fromDraft(SubParser.NodeDraft draft, String source) {
        Node n = new Node();
        n.link = draft.link == null ? "" : draft.link;
        n.name = draft.name == null || draft.name.isEmpty() ? draft.proto : draft.name;
        n.proto = draft.proto == null ? "" : draft.proto;
        n.source = source;
        n.config = draft.config == null ? new LinkedHashMap<String, Object>() : draft.config;
        n.updatedAt = System.currentTimeMillis();
        if (!n.link.isEmpty()) {
            n.id = HashUtil.md5(n.link);
        } else {
            n.id = HashUtil.md5(n.name + "|" + n.server() + "|" + String.valueOf(n.config.get("port")));
        }
        return n;
    }
}
