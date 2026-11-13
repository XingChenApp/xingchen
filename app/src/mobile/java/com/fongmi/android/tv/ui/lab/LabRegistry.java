package com.fongmi.android.tv.ui.lab;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实验室注册表（两层）：files/lab/registry.json。
 * 第一层＝环境（python / nodejs / php / mihomo / alist / frp / aria2 七张卡平铺）；
 * 第二层＝服务，带 runtime 归属字段，只出现在所属环境页里。
 * lab.json 里的 icon 字段保留（格式 v1 定死），UI 本轮不画头像位。
 */
public final class LabRegistry {

    /** 十一个环境，平铺十一张卡 */
    public static final List<String> ENVS = Arrays.asList(
            "python", "nodejs", "php", "mihomo", "alist", "frp", "aria2",
            "ubuntu", "java", "bun", "ffmpeg");

    /**
     * 服务记录。落盘字段名全部钉 @SerializedName（＋ proguard keep 双保险）：
     * 这张表是 Gson 反射落盘的，没钉名时成品 R8 每版给字段派的字母都可能不同，
     * 旧版写的文件新版按新字母读——轻则字段全空、重则类型对不上（旧版布尔占位
     * 新版是 int 端口）令 fromJson 抛 JsonSyntaxException、整表被当坏文件改名清空
     * （1.4.27→1.4.28 真机实证：他的服务就是这么没的）。钉名后落盘格式与源码名
     * 恒等，跨版往返不再看 R8 脸色；同族前例见 1a5974bf／52b2796e 的钉名修法。
     */
    public static class Svc {
        @SerializedName("id")
        public String id;
        @SerializedName("name")
        public String name;
        @SerializedName("desc")
        public String desc;
        @SerializedName("icon")
        public String icon;
        @SerializedName("version")
        public String version;
        /** python | nodejs | php | mihomo | alist | frp | aria2 | ubuntu | java | bun | ffmpeg | none（none 归 tool，兼容老数据） */
        @SerializedName("runtime")
        public String runtime;
        @SerializedName("autoStartDefault")
        public boolean autoStartDefault;
        /** null=随包声明，true/false=用户手动覆盖 */
        @SerializedName("autoStart")
        public Boolean autoStart;
        @SerializedName("installTime")
        public long installTime;
        /** 分配到的端口（port=auto 时由壳子分配并记住） */
        @SerializedName("port")
        public int port;
        /** 兼容导入时映射不了、要用户人工确认的人话（一条一行），空=无 */
        @SerializedName("needsReview")
        public String needsReview;
        /** 上游 Item.group 分组 id（此前只留在各自 lab.json 里、注册表查不到） */
        @SerializedName("group")
        public String group = "";
        /** 单服务节点开关：开着则启动命令时给进程注入本机节点代理（mihomo 服务本身不注入）；
         *  基本类型，老数据没这字段 Gson 读出来默认关，不用补迁移 */
        @SerializedName("nodeProxy")
        public boolean nodeProxy;
    }

    public static class Runtime {
        @SerializedName("name")
        public String name;
        @SerializedName("version")
        public String version;
        @SerializedName("installTime")
        public long installTime;
    }

    @SerializedName("svcs")
    private final Map<String, Svc> svcs = new LinkedHashMap<>();
    @SerializedName("runtimes")
    private final Map<String, Runtime> runtimes = new LinkedHashMap<>();
    /**
     * 自启总开关已摘除（2026-10-29 他亲定）：自启只认每个包自己的标记
     * （Svc.autoStartDefault／autoStart，见 autoStartOf）。存量注册表 JSON 里
     * 可能还留着旧键 masterAutoStart——Gson 读时对无字段的键直接忽略，
     * 下次落盘即自然消失，不做专门清理、不影响读表。
     */

    /** 上次 load 的恢复/失败提示（非空=要在实验室首页给人看），不落盘 */
    private static String loadNotice;

    /** 实验室首页读它决定要不要弹提示；读一次即清，避免每次进页重复弹 */
    public static synchronized String takeLoadNotice() {
        String n = loadNotice;
        loadNotice = null;
        return n;
    }

    private static LabRegistry cache;

    private LabRegistry() {
    }

    public static synchronized LabRegistry get(Context c) {
        if (cache == null) cache = load(c);
        return cache;
    }

    /** 服务归属的环境 id：none → tool（兼容老数据；首页不设卡） */
    public static String envOf(Svc s) {
        String r = s == null || s.runtime == null ? "none" : s.runtime;
        if ("python".equals(r)) return "python";
        if ("nodejs".equals(r)) return "nodejs";
        if ("php".equals(r)) return "php";
        if ("mihomo".equals(r)) return "mihomo";
        if ("alist".equals(r)) return "alist";
        if ("frp".equals(r)) return "frp";
        if ("aria2".equals(r)) return "aria2";
        if ("ubuntu".equals(r)) return "ubuntu";
        if ("java".equals(r)) return "java";
        if ("bun".equals(r)) return "bun";
        if ("ffmpeg".equals(r)) return "ffmpeg";
        return "tool";
    }

    public static String envLabel(String env) {
        if ("python".equals(env)) return "Python 环境";
        if ("nodejs".equals(env)) return "Node 环境";
        if ("php".equals(env)) return "PHP 环境";
        if ("mihomo".equals(env)) return "mihomo";
        if ("alist".equals(env)) return "alist";
        if ("frp".equals(env)) return "frp";
        if ("aria2".equals(env)) return "aria2";
        if ("ubuntu".equals(env)) return "Ubuntu 环境";
        if ("java".equals(env)) return "Java 环境";
        if ("bun".equals(env)) return "Bun 环境";
        if ("ffmpeg".equals(env)) return "ffmpeg";
        return "工具";
    }

    /** 某环境是否就绪：tool 恒为 true（兼容），其余看环境包装没装 */
    public static boolean envReady(Context c, String env) {
        if ("tool".equals(env)) return true;
        return LabRuntimeManager.isInstalled(c, env);
    }

    public synchronized List<Svc> services(String env) {
        List<Svc> out = new ArrayList<>();
        for (Svc s : svcs.values()) {
            if (env.equals(envOf(s))) out.add(s);
        }
        return out;
    }

    public synchronized List<Svc> allServices() {
        return new ArrayList<>(svcs.values());
    }

    /**
     * 与实验室首页同源的可见服务：首页宫格只为 ENVS 十一个环境设卡，
     * runtime 为 none/未知的服务归 tool、首页无卡、平时看不见摸不着。
     * 凡是「给人看的计数」都走这里，跟页面能数出来的对齐；
     * allServices 只留给开机自启这类要全量遍历的内部逻辑。
     */
    public synchronized List<Svc> visibleServices() {
        List<Svc> out = new ArrayList<>();
        for (Svc s : svcs.values()) {
            if (ENVS.contains(envOf(s))) out.add(s);
        }
        return out;
    }

    public synchronized Svc svc(String id) {
        return svcs.get(id);
    }

    public synchronized void upsertSvc(Context c, Svc s) {
        svcs.put(s.id, s);
        save(c);
    }

    public synchronized void removeSvc(Context c, String id) {
        svcs.remove(id);
        save(c);
    }

    public synchronized boolean hasRuntime(String name) {
        return runtimes.containsKey(name);
    }

    public synchronized Runtime runtime(String name) {
        return runtimes.get(name);
    }

    public synchronized void upsertRuntime(Context c, Runtime r) {
        runtimes.put(r.name, r);
        save(c);
    }

    public synchronized void removeRuntime(Context c, String name) {
        runtimes.remove(name);
        save(c);
    }

    public static boolean autoStartOf(Svc s) {
        if (s.autoStart != null) return s.autoStart;
        return s.autoStartDefault;
    }

    private static LabRegistry load(Context c) {
        LabRegistry r = new LabRegistry();
        File f = LabPaths.registry(c);
        String driftText = null;
        boolean loadThrew = false;
        if (f.exists()) {
            String text = null;
            try {
                text = readText(f);
            } catch (Exception e) {
                loadThrew = true;
                android.util.Log.e("LabRegistry", "registry 读文件失败", e);
            }
            if (text != null && !text.trim().isEmpty()) {
                try {
                    // 语法预检：parseRegistry 对截断 JSON 是半吞（坏一半静默少一半服务），
                    // 用户只看见列表变空不知道为什么。真坏了改名留尸 + 留痕，再走恢复。
                    JsonParser.parseString(text);
                    try (java.io.StringReader sr = new java.io.StringReader(text)) {
                        parseRegistry(sr, r);
                    }
                    // 解析没抛也留底：旧版 R8 字母格式的文件可能被逐条静默吞/误分，
                    // 表面读通、服务全无——下面按「有包无行」触发恢复，文本就是证据。
                    driftText = text;
                } catch (Exception e) {
                    loadThrew = true;
                    android.util.Log.e("LabRegistry", "registry 读取失败，坏文件已改名备份", e);
                    driftText = text;
                    try {
                        f.renameTo(new File(f.getParentFile(),
                                f.getName() + ".corrupt-" + System.currentTimeMillis()));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        if (r.svcs.isEmpty()) recoverIfNeeded(c, r, driftText, loadThrew);
        return r;
    }

    private static String readText(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f);
             java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            return buf.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** 最新一份 .corrupt 备份（load 改名留下的尸），没有返回 null */
    private static File newestCorrupt(Context c) {
        File dir = LabPaths.root(c);
        File[] fs = dir == null ? null : dir.listFiles();
        if (fs == null) return null;
        File best = null;
        for (File x : fs) {
            if (x.isFile() && x.getName().startsWith("registry.json.corrupt-")) {
                if (best == null || x.lastModified() > best.lastModified()) best = x;
            }
        }
        return best;
    }

    /**
     * 读回为空时的恢复（不许静默清空）：注册表只是索引，包本体都在
     * files/lab/packages/<id>/（lab.json 在包根、字面键稳定）。重建顺序：
     * ①扫包目录读 lab.json 立行（名字/运行时/版本以清单为准）；
     * ②旧注册表文本（或 .corrupt 备份）按值的形状把端口找回来
     * （字段名是旧版 R8 字母、认不得，只能认值：运行时名是闭集、端口是
     * 1..65535 的整数、环境行带装机时间大长数可与服务行分开）。
     * 只恢复包目录还存在的 id（卸载过的包不许借尸还魂）。恢复到了就以
     * 钉名格式重新落盘自愈，并留一句给人看的提示；一份都没恢复且确有
     * 读坏发生，同样留提示——不让人对着空列表猜。
     */
    private static void recoverIfNeeded(Context c, LabRegistry r, String driftText, boolean loadThrew) {
        File pkgs = LabPaths.packages(c);
        File[] dirs = pkgs == null ? null : pkgs.listFiles();
        boolean hasPkg = false;
        if (dirs != null) {
            for (File d : dirs) if (d.isDirectory()) { hasPkg = true; break; }
        }
        File corrupt = newestCorrupt(c);
        if (!hasPkg && driftText == null && corrupt == null) return; // 真·空实验室，不打扰
        if (driftText == null && corrupt != null) {
            try {
                driftText = readText(corrupt);
            } catch (Exception ignored) {
            }
        }
        java.util.Set<String> pkgIds = new java.util.HashSet<>();
        Map<String, Svc> byId = new LinkedHashMap<>();
        if (dirs != null) {
            for (File d : dirs) {
                if (!d.isDirectory()) continue;
                pkgIds.add(d.getName());
                File mf = new File(d, "lab.json");
                if (!mf.exists()) continue;
                try {
                    LabManifest m = LabManifest.parse(readText(mf), d.getName());
                    Svc s = new Svc();
                    s.id = d.getName();
                    s.name = m.name;
                    s.desc = m.desc;
                    s.icon = m.icon;
                    s.version = m.version;
                    s.runtime = m.runtime == null ? "none" : m.runtime;
                    s.autoStartDefault = m.autoStart;
                    byId.put(s.id, s);
                } catch (Exception ignored) {
                }
            }
        }
        int driftCandidates = 0;
        if (driftText != null) {
            Map<String, Svc> drift = new LinkedHashMap<>();
            recoverDrifted(driftText, drift);
            driftCandidates = drift.size();
            for (Map.Entry<String, Svc> e : drift.entrySet()) {
                Svc base = byId.get(e.getKey());
                if (base != null) {
                    if (e.getValue().port > 0) base.port = e.getValue().port;
                    if ((base.runtime == null || "none".equals(base.runtime))
                            && e.getValue().runtime != null) {
                        base.runtime = e.getValue().runtime;
                    }
                } else if (pkgIds.contains(e.getKey())) {
                    // 有包、清单读不出：靠漂移形状把行立住，名字先用 id 顶着
                    Svc s = e.getValue();
                    if (s.name == null || s.name.isEmpty()) s.name = s.id;
                    byId.put(s.id, s);
                }
            }
        }
        if (!byId.isEmpty()) {
            for (Svc s : byId.values()) {
                r.svcs.put(s.id, s);
                // 漂移文件里服务行会被误分进环境表（Runtime 的 name 与 Svc 的
                // id 在旧版字母下同名），同 id 的假环境行一并摘掉，不带进新文件
                r.runtimes.remove(s.id);
            }
            r.save(c);
            loadNotice = "实验室注册表与旧版格式对不上，已从包目录重建 " + byId.size()
                    + " 个服务；个别设置（自启覆盖、节点开关、安装时间）未能恢复，已按包声明重置。";
        } else if (loadThrew || driftCandidates > 0) {
            loadNotice = "实验室注册表读取失败：原文件已备份为 registry.json.corrupt-*，"
                    + "自动重建没找到可恢复的服务，列表暂时为空。";
        }
    }

    /**
     * 漂移格式（旧版 R8 字母键）注册表的形状读回：键名认不得，只认值。
     * 顶层布尔＝已摘除的总开关旧键、只跳过；对象套对象里，map 键就是服务 id
     * （写盘时按 id 入表，这一层不是字段名、不漂移）；行内找：值命中运行时
     * 闭集的字符串＝runtime，1..65535 的整数＝port。环境行（名字＋版本＋
     * 装机时间大长数）靠「大长数＋字符串≤2＋无端口」与服务行分开。
     * 返回值是旧总开关值，现已无读者（调用方丢弃），保留只为不改方法形状。
     */
    static Boolean recoverDrifted(String text, Map<String, Svc> out) {
        Boolean master = null;
        JsonElement root;
        try {
            root = JsonParser.parseString(text);
        } catch (Exception e) {
            return null;
        }
        if (root == null || !root.isJsonObject()) return null;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
                master = v.getAsBoolean();
                continue;
            }
            if (!v.isJsonObject()) continue;
            for (Map.Entry<String, JsonElement> en : v.getAsJsonObject().entrySet()) {
                if (!en.getValue().isJsonObject()) continue;
                Svc s = driftSvc(en.getKey(), en.getValue().getAsJsonObject());
                if (s != null) out.put(s.id, s);
            }
        }
        return master;
    }

    private static Svc driftSvc(String key, JsonObject o) {
        try {
            LabPaths.requireSafeId(key);
        } catch (Exception e) {
            return null;
        }
        String runtime = null;
        String nameCand = null;
        int port = 0;
        int strings = 0;
        boolean bigLong = false;
        for (Map.Entry<String, JsonElement> f : o.entrySet()) {
            JsonElement fv = f.getValue();
            if (!fv.isJsonPrimitive()) continue;
            if (fv.getAsJsonPrimitive().isString()) {
                strings++;
                String sv = fv.getAsString();
                if (runtime == null && (ENVS.contains(sv) || "none".equals(sv))) {
                    runtime = sv;
                } else if (nameCand == null && !sv.equals(key)) {
                    nameCand = sv;
                }
            } else if (fv.getAsJsonPrimitive().isNumber()) {
                try {
                    long lv = fv.getAsLong();
                    if (lv >= 1 && lv <= 65535 && port == 0) port = (int) lv;
                    else if (lv > 65535) bigLong = true;
                } catch (Exception ignored) {
                }
            }
        }
        // 环境行不当服务：名字+版本+装机时间戳（大长数），字符串少且没端口
        if (bigLong && strings <= 2 && port == 0) return null;
        // 服务行起码要有运行时、端口、足够字符串之一二，太单薄的宁可不立行
        if (runtime == null && port == 0 && strings < 3) return null;
        Svc s = new Svc();
        s.id = key;
        s.runtime = runtime;
        s.port = port;
        s.name = nameCand;
        return s;
    }

    /**
     * 注册表读回（1.3.52 真机崩溃根治）：
     * R8 会剥掉集合字段的泛型签名（proguard 里没有 -keepattributes Signature），
     * Gson 按裸类型读 Map/List 时值全是 LinkedTreeMap，之后强转 bean 必崩。
     * 这里改走 JsonParser 逐层读：顶层按形态分流（对象=服务/环境两张表，布尔=已摘除的总开关旧键、读掉丢弃），
     * 每个条目按具体类（Svc/Runtime，无泛型字段）重绑；字段名走 Gson 反射
     * （运行时实际名），不依赖源码名，手机上已存的老数据照读。
     */
    static void parseRegistry(Reader reader, LabRegistry r) {
        Gson g = new Gson();
        JsonElement root;
        try {
            root = JsonParser.parseReader(reader);
        } catch (Exception e) {
            return;
        }
        if (root == null || !root.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonObject()) {
                // 服务表 / 环境表：逐条看有没有 id 归位（Svc 必有 id，Runtime 没有）。
                // 逐条各自兜底：一行形状对不上（旧版格式漂移）只丢这一行，
                // 不许整表陪葬——整表抛上去就是 load 改名清空的老路。
                for (Map.Entry<String, JsonElement> en : v.getAsJsonObject().entrySet()) {
                    if (!en.getValue().isJsonObject()) continue;
                    Svc s = null;
                    try {
                        s = g.fromJson(en.getValue(), Svc.class);
                    } catch (Exception ignored) {
                    }
                    if (s != null && s.id != null && !s.id.isEmpty()) {
                        r.svcs.put(en.getKey(), s);
                    } else {
                        try {
                            Runtime rt = g.fromJson(en.getValue(), Runtime.class);
                            if (rt != null) {
                                if (rt.name == null) rt.name = en.getKey();
                                r.runtimes.put(en.getKey(), rt);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
                // 顶层布尔只可能是已摘除的总开关旧键（masterAutoStart）：
                // 读到即丢，不落任何状态（2026-10-29 摘除）。
            }
        }
    }

    private synchronized void save(Context c) {
        // 写临时文件再改名（原子落盘）：直接覆写时进程被杀会留半截 JSON，
        // 下次 load 就把登记表读空；失败留痕，不再静默吞。
        try {
            File f = LabPaths.registry(c);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp);
                 OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                new Gson().toJson(this, writer);
            }
            if (!tmp.renameTo(f)) {
                android.util.Log.e("LabRegistry", "registry 临时文件改名失败，退回直写");
                try (FileOutputStream out = new FileOutputStream(f);
                     OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                    new Gson().toJson(this, writer);
                }
                tmp.delete();
            }
        } catch (Exception e) {
            android.util.Log.e("LabRegistry", "registry 保存失败", e);
        }
    }
}
