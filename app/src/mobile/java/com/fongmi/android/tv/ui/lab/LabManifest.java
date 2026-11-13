package com.fongmi.android.tv.ui.lab;

import android.text.TextUtils;

import com.google.gson.annotations.SerializedName;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * .lab 包格式 v1（定稿）：
 * zip 根目录 lab.json，其余文件在 files/ 下（解开后合并到包根）。
 *
 * lab.json 字段：
 *   id            包唯一标识（必填；直接决定包目录，纯点/带分隔的非法 id 进门就拒）
 *   name          显示名（必填）
 *   desc          一句人话简介（上游写法 description 同认）
 *   icon          图标字（1-2 个字，如 Py/Js/网/剧）
 *   version       包版本
 *   group         分组 id（上游 Item.group，可选，如 network）
 *   runtime       python | nodejs | php | mihomo | alist | frp | aria2 | ubuntu | java | bun | ffmpeg | none（必填）
 *                 上游常写 node，进门统一归一成 nodejs（normalizeRuntime，全仓唯一入口）
 *   autoStart     默认是否自动启动（上游写法 auto_execute / auto_start 同认）
 *   commands[]    命令列表：{id,name,desc,command,background,autoExecute,showOutput}
 *                 命令级同认上游蛇形：description、auto_execute
 *                 showOutput（上游 show_output 口径）：前台型命令运行后是否把输出端到眼前，缺省 true
 *                 command 支持占位：{port}（端口自动分配时替换）、{pkgDir}、{filesDir}
 *   output        产出声明：{kind: live|vod|pan|tool, target, port: auto|数字, actionLabel}
 *                 live/vod 的 target 是地址模板（如 http://127.0.0.1:{port}/live.m3u8）
 *                 缺省 output 时，壳子给「当直播源用/当点播源用」两钮让用户选。
 */
public class LabManifest {

    @SerializedName("id")
    public String id;
    @SerializedName("name")
    public String name;
    @SerializedName("desc")
    public String desc;
    @SerializedName("icon")
    public String icon;
    /** 上游 Item.icon 的图片地址（URL 形态时）；icon 字仍是自家口径，两者并存 */
    @SerializedName("iconUrl")
    public String iconUrl = "";
    @SerializedName("version")
    public String version;
    /** 分组 id（上游 Item.group，如 network；兼容导入时落进来），无分组为空串 */
    @SerializedName("group")
    public String group = "";
    @SerializedName("runtime")
    public String runtime = "none";
    @SerializedName("autoStart")
    public boolean autoStart = false;
    /** 包声明预建目录（上游 Item.mkdir 口径：{sdcard} 等占位、相对落包内），安装时真建 */
    @SerializedName("mkdir")
    public final List<String> mkdir = new ArrayList<>();
    /** 包声明环境变量（上游 Item.var_path 口径）：PATH 并入进程 PATH，其余作环境变量注入 */
    @SerializedName("varPath")
    public final java.util.Map<String, String> varPath = new java.util.LinkedHashMap<>();
    @SerializedName("commands")
    public final List<LabCommand> commands = new ArrayList<>();
    /** 包设置项（复用变量表 UI）：{key,name,type,defaultValue,hint,required,...} */
    @SerializedName("settings")
    public final List<LabVar> settings = new ArrayList<>();
    @SerializedName("output")
    public LabOutput output;

    public static class LabCommand {
        @SerializedName("id")
        public String id;
        @SerializedName("name")
        public String name;
        @SerializedName("desc")
        public String desc;
        @SerializedName("command")
        public String command;
        @SerializedName("background")
        public boolean background = true;
        @SerializedName("autoExecute")
        public boolean autoExecute = false;
        /** 运行后是否展示输出（上游 show_output，缺省 true；false=只在后台跑、输出只进记录） */
        @SerializedName("showOutput")
        public boolean showOutput = true;
        /** 包声明最低 App versionCode，不够就提示升级 */
        @SerializedName("minVersion")
        public int minVersion = 0;
        /** 运行前依赖：先把 url 下到包内 file 再跑 */
        @SerializedName("download")
        public LabDownload download;
        /** 命令变量表（上游 Variable 口径）：text/text_multiline/number/select/switch/file/directory */
        @SerializedName("variables")
        public final List<LabVar> variables = new ArrayList<>();
        /** 后续操作按钮（上游 Command.clicks 口径）：服务页渲染成可点动作 */
        @SerializedName("clicks")
        public final List<LabClick> clicks = new ArrayList<>();
    }

    /** 上游 Click：title/action/value/style/content/width/height 原形保存 */
    public static class LabClick {
        @SerializedName("title") public String title;
        @SerializedName("action") public String action;
        @SerializedName("value") public String value;
        @SerializedName("style") public String style;
        @SerializedName("content") public String content;
        @SerializedName("width") public String width;
        @SerializedName("height") public String height;
    }

    /** 命令变量一项（名字＋必填标记渲染；hint 只做输入框 placeholder，不单独成行） */
    public static class LabVar {
        @SerializedName("key")
        public String key;
        @SerializedName("name")
        public String name;
        @SerializedName("type")
        public String type = "text";
        @SerializedName("defaultValue")
        public String defaultValue;
        @SerializedName("hint")
        public String hint;
        @SerializedName("required")
        public boolean required;
        /** 上游 Setting.editable：false=只给看不给改（变量表里置灰）；缺省 null 按可改 */
        @SerializedName("editable")
        public Boolean editable;
        @SerializedName("filter")
        public String filter;
        @SerializedName("min")
        public String min;
        @SerializedName("max")
        public String max;
        @SerializedName("options")
        public final List<LabOption> options = new ArrayList<>();
    }

    public static class LabOption {
        @SerializedName("label")
        public String label;
        @SerializedName("value")
        public String value;
    }

    /**
     * 命令依赖下载（上游 CommandDownload 口径）：运行前按声明备好文件，缺了才下。
     * 两种落位模式（LabDepends 判）：
     *   save 模式：file（save_to）非空且 extractTo 为空——原样落到 file；
     *   extract 模式：extractTo 非空（"."=包根、"rootfs"=包内 rootfs、或相对/绝对目录）——
     *     整包解压进该目录，file 此时是 binary_path 语义（解压后归位到的二进制位置）。
     * checkFile 非空时以它判「备好没」（相对包目录，或带占位的绝对路径）；
     * 路径都可带 {sdcard}/{pkgDir} 等占位，运行时展开。
     */
    public static class LabDownload {
        @SerializedName("url")
        public String url;
        @SerializedName("file")
        public String file;
        @SerializedName("checkFile")
        public String checkFile;
        @SerializedName("extractTo")
        public String extractTo;
        @SerializedName("required")
        public boolean required;
        /** 上游 CommandDownload.title（依赖名）/ size（展示文本）/ 该 arch 的真 version */
        @SerializedName("title")
        public String title;
        @SerializedName("size")
        public String size;
        @SerializedName("version")
        public String version;
        /** 第二分片（上游 Download.liburl，如 nodejs 的依赖库包）：主包后同目录再解一份 */
        @SerializedName("libUrl")
        public String libUrl;
        /** 镜像直链（上游 mirrors 是 Map{名:URL}，归一成 URL 列表保存，url 为空时备选） */
        @SerializedName("mirrors")
        public final List<String> mirrors = new ArrayList<>();
    }

    public static class LabOutput {
        /** live | vod | pan | tool */
        @SerializedName("kind")
        public String kind = "";
        /** 地址模板，支持 {port} */
        @SerializedName("target")
        public String target = "";
        /** auto 或固定端口号 */
        @SerializedName("port")
        public String port = "auto";
        /** 主动作按钮文案，缺省按 kind 取默认 */
        @SerializedName("actionLabel")
        public String actionLabel = "";
    }

    public LabCommand defaultCommand() {
        for (LabCommand c : commands) if (c.autoExecute) return c;
        return commands.isEmpty() ? null : commands.get(0);
    }

    public String actionLabel() {
        if (output != null && !TextUtils.isEmpty(output.actionLabel)) return output.actionLabel;
        String kind = output == null ? "" : output.kind;
        if ("live".equals(kind)) return "变成我的直播源";
        if ("vod".equals(kind)) return "变成我的点播源";
        return "打开看看";
    }

    /** 运行时名归一（全仓唯一入口，别在各调用点各补）：仓与清单常把 Node 写成 node，运行时只认 nodejs */
    public static String normalizeRuntime(String runtime) {
        if (runtime == null) return "none";
        String rt = runtime.trim().toLowerCase();
        return "node".equals(rt) ? "nodejs" : rt;
    }

    public static LabManifest parse(String json) throws Exception {
        return parse(json, "");
    }

    /**
     * 同 parse，id 缺失时用调用方给的派生 id 兜底（上游 webhtv 的包根本没有 id
     * 概念、身份就是包名/目录名；存量包的 lab.json 没写 id 不该整份读死）。
     * 派生 id 同样要过路径安全检查；两边都没有才报「缺 id」。
     */
    public static LabManifest parse(String json, String fallbackId) throws Exception {
        JSONObject o = new JSONObject(json);
        LabManifest m = new LabManifest();
        m.id = o.optString("id", "").trim();
        if (m.id.isEmpty() && fallbackId != null && !fallbackId.trim().isEmpty()) {
            m.id = fallbackId.trim();
        }
        // id 直接决定包目录：纯「..」这类 id 能让安装/卸载删到实验室根，进门就拒
        //（路径层 LabPaths 还有一道 canonical 兜底，两层同口径）
        if (!m.id.isEmpty()) {
            try {
                LabPaths.requireSafeId(m.id);
            } catch (IllegalArgumentException e) {
                throw new Exception("lab.json 的 id 不合法（不能是纯点、不能带路径符号）");
            }
        }
        m.name = o.optString("name", "").trim();
        // 上游写法是 description，自家写法是 desc，两种都吃（与 LabImportCompat 对齐）
        m.desc = o.has("description") ? o.optString("description", "") : o.optString("desc", "");
        m.icon = o.optString("icon", "包");
        String ic = o.optString("icon", "");
        m.iconUrl = ic.startsWith("http") ? ic : o.optString("iconUrl", "");
        m.version = o.optString("version", "1.0");
        m.group = o.optString("group", "");
        m.runtime = normalizeRuntime(o.optString("runtime", "none"));
        // 上游写法 auto_execute / auto_start，自家写法 autoStart，两种都吃
        m.autoStart = o.has("auto_execute") ? o.optBoolean("auto_execute", false)
                : (o.has("auto_start") ? o.optBoolean("auto_start", false)
                : o.optBoolean("autoStart", false));
        // 预建目录（上游 mkdir 双形态：字符串单个 / 数组多个）
        Object mk = o.opt("mkdir");
        if (mk instanceof String) {
            String s = ((String) mk).trim();
            if (!s.isEmpty()) m.mkdir.add(s);
        } else if (mk instanceof JSONArray) {
            JSONArray arr = (JSONArray) mk;
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "").trim();
                if (!s.isEmpty()) m.mkdir.add(s);
            }
        }
        // 包级环境变量（上游 var_path 是 Map；Gson 落盘名 varPath 也认）
        JSONObject vp = o.optJSONObject("var_path");
        if (vp == null) vp = o.optJSONObject("varPath");
        if (vp != null) {
            java.util.Iterator<String> keys = vp.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String v = vp.optString(k, "");
                if (!k.isEmpty() && !v.isEmpty()) m.varPath.put(k, v);
            }
        }
        if (TextUtils.isEmpty(m.id)) throw new Exception("lab.json 缺 id");
        if (TextUtils.isEmpty(m.name)) throw new Exception("lab.json 缺 name");
        JSONArray cs = o.optJSONArray("commands");
        if (cs != null) {
            for (int i = 0; i < cs.length(); i++) {
                JSONObject c = cs.getJSONObject(i);
                LabCommand cmd = new LabCommand();
                cmd.id = c.optString("id", "cmd" + i);
                cmd.name = c.optString("name", cmd.id);
                // 命令级同样两种都吃：description（上游）/ desc（自家）
                cmd.desc = c.has("description") ? c.optString("description", "") : c.optString("desc", "");
                cmd.command = c.optString("command", "");
                cmd.background = c.optBoolean("background", true);
                // 命令级自启：auto_execute（上游蛇形）/ autoExecute（自家驼峰）都吃
                cmd.autoExecute = c.has("auto_execute") ? c.optBoolean("auto_execute", false)
                        : c.optBoolean("autoExecute", false);
                // 两种写法都吃："show_output"（上游原名）与 "showOutput"（Gson 落盘回读的字段名）
                cmd.showOutput = c.has("show_output") ? c.optBoolean("show_output", true)
                        : c.optBoolean("showOutput", true);
                cmd.minVersion = c.optInt("min_version", 0);
                JSONObject dl = c.optJSONObject("download");
                if (dl != null) {
                    LabDownload d = new LabDownload();
                    d.url = dl.optString("url", "");
                    // 落盘键两种都吃：file（自家）/ save_to（上游与 LabImportCompat 口径），两路对齐
                    d.file = dl.has("file") ? dl.optString("file", "") : dl.optString("save_to", "");
                    // 上游键与 Gson 落盘名（驼峰）都吃，回读自家写的清单不能丢字段
                    d.checkFile = dl.has("check_file") ? dl.optString("check_file", "")
                            : dl.optString("checkFile", "");
                    d.extractTo = dl.has("extract_to") ? dl.optString("extract_to", "")
                            : dl.optString("extractTo", "");
                    d.required = dl.optBoolean("required", false);
                    d.title = dl.optString("title", "");
                    d.size = dl.optString("size", "");
                    d.version = dl.optString("version", "");
                    d.libUrl = dl.optString("liburl", dl.optString("libUrl", ""));
                    d.mirrors.addAll(parseMirrorUrls(dl));
                    if (TextUtils.isEmpty(d.url) && !d.mirrors.isEmpty()) d.url = d.mirrors.get(0);
                    if (!TextUtils.isEmpty(d.url) || !TextUtils.isEmpty(d.checkFile)) cmd.download = d;
                }
                cmd.variables.addAll(parseVars(c.optJSONArray("variables")));
                cmd.clicks.addAll(parseClicks(c.optJSONArray("clicks")));
                m.commands.add(cmd);
            }
        }
        if (m.commands.isEmpty()) throw new Exception("lab.json 没有可运行的命令");
        m.settings.addAll(parseVars(o.optJSONArray("settings")));
        JSONObject out = o.optJSONObject("output");
        if (out != null) {
            LabOutput op = new LabOutput();
            op.kind = out.optString("kind", "").trim().toLowerCase();
            op.target = out.optString("target", "").trim();
            op.port = out.optString("port", "auto").trim().toLowerCase();
            op.actionLabel = out.optString("actionLabel", "");
            m.output = op;
        }
        return m;
    }

    /** 上游 mirrors 双认：Map{名:URL}（默影视真形）与数组（字符串或 {url}）都收成 URL 列表 */
    public static List<String> parseMirrorUrls(JSONObject dl) {
        List<String> out = new ArrayList<>();
        if (dl == null) return out;
        Object mv = dl.opt("mirrors");
        if (mv instanceof JSONObject) {
            JSONObject mo = (JSONObject) mv;
            java.util.Iterator<String> keys = mo.keys();
            while (keys.hasNext()) {
                Object v = mo.opt(keys.next());
                String u = v instanceof JSONObject ? ((JSONObject) v).optString("url", "").trim()
                        : (v == null ? "" : String.valueOf(v).trim());
                if (!u.isEmpty() && !"null".equals(u)) out.add(u);
            }
        } else if (mv instanceof JSONArray) {
            JSONArray arr = (JSONArray) mv;
            for (int i = 0; i < arr.length(); i++) {
                Object v = arr.opt(i);
                String u = v instanceof JSONObject ? ((JSONObject) v).optString("url", "").trim()
                        : (v == null ? "" : String.valueOf(v).trim());
                if (!u.isEmpty() && !"null".equals(u)) out.add(u);
            }
        }
        return out;
    }

    /** 上游 clicks[] 原形解析（width/height 数字与字符串都吃） */
    public static List<LabClick> parseClicks(JSONArray arr) {
        List<LabClick> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            LabClick cl = new LabClick();
            cl.title = c.optString("title", "");
            cl.action = c.optString("action", "");
            cl.value = c.optString("value", "");
            cl.style = c.optString("style", "");
            cl.content = c.optString("content", "");
            cl.width = c.has("width") ? String.valueOf(c.opt("width")) : "";
            cl.height = c.has("height") ? String.valueOf(c.opt("height")) : "";
            out.add(cl);
        }
        return out;
    }

    /** 解析变量表（上游 Variable 口径，子集） */
    private static List<LabVar> parseVars(JSONArray arr) {
        List<LabVar> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject v = arr.optJSONObject(i);
            if (v == null) continue;
            LabVar var = new LabVar();
            var.key = v.optString("key", "").trim();
            var.name = v.optString("name", var.key);
            var.type = v.optString("type", "text").trim().toLowerCase();
            // 兼容两种写法："default"（手写 lab.json）与 "defaultValue"（Gson 落盘时的字段名）
            var.defaultValue = v.has("default") ? v.optString("default", null)
                    : (v.has("defaultValue") ? v.optString("defaultValue", null) : null);
            var.hint = v.optString("hint", null);
            var.required = v.optBoolean("required", false);
            if (v.opt("editable") instanceof Boolean) var.editable = (Boolean) v.opt("editable");
            var.filter = v.optString("filter", null);
            var.min = v.has("min") ? v.optString("min", null) : null;
            var.max = v.has("max") ? v.optString("max", null) : null;
            JSONArray opts = v.optJSONArray("options");
            if (opts != null) {
                for (int j = 0; j < opts.length(); j++) {
                    JSONObject o2 = opts.optJSONObject(j);
                    if (o2 == null) continue;
                    LabOption op = new LabOption();
                    op.label = o2.optString("label", "");
                    op.value = o2.optString("value", op.label);
                    var.options.add(op);
                }
            }
            if (!TextUtils.isEmpty(var.key)) out.add(var);
        }
        return out;
    }

    /** 人话地址：http://127.0.0.1:{port}/live.m3u8 →「手机自己 : 8787 /live.m3u8」 */
    public String humanAddress(int port) {
        if (output == null || TextUtils.isEmpty(output.target)) return "";
        String t = output.target.replace("{port}", String.valueOf(port));
        String path = t.replaceFirst("^[a-zA-Z]+://[^/]*", "");
        if (TextUtils.isEmpty(path)) path = "/";
        return "手机自己 : " + port + " " + path;
    }
}
