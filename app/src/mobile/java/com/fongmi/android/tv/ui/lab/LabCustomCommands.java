package com.fongmi.android.tv.ui.lab;

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义命令：按包增/改/删，存包目录 custom.json（上游 LabCustomCommands 口径的精简版）。
 * custom=true 的命令走与包命令同一套运行链（变量表/依赖下载/前后台），卸载包时随目录一起删。
 * 包设置值：包目录 settings.json，{key:value}，命令里的同名变量优先取这里。
 */
public final class LabCustomCommands {

    /** 自定义命令（字段与 LabManifest.LabCommand 对齐的子集） */
    public static class CustomCommand {
        public String id;
        public String name;
        public String desc;
        public String command;
        public boolean background = true;
        public boolean autoExecute = false;
        /** 与 LabCommand 对齐：运行后是否展示输出（缺省 true） */
        public boolean showOutput = true;
        public int minVersion = 0;
        public final List<LabManifest.LabVar> variables = new ArrayList<>();
    }

    private LabCustomCommands() {
    }

    private static File customFile(Context c, String pkgId) {
        return new File(LabPaths.packageDir(c, pkgId), "custom.json");
    }

    private static File settingsFile(Context c, String pkgId) {
        return new File(LabPaths.packageDir(c, pkgId), "settings.json");
    }

    /** 自定义命令列表（按添加顺序） */
    public static synchronized List<CustomCommand> list(Context c, String pkgId) {
        List<CustomCommand> out = new ArrayList<>();
        File f = customFile(c, pkgId);
        if (!f.exists()) return out;
        try (FileInputStream in = new FileInputStream(f);
             InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            out.addAll(parseCommands(r));
        } catch (Exception ignored) {
        }
        return out;
    }

    /**
     * custom.json 读回（与 LabRegistry.parseRegistry 同一根治口径）：
     * R8 剥泛型签名后 List&lt;CustomCommand&gt; 的元素全是 LinkedTreeMap，直接遍历强转必崩；
     * 嵌套的 variables/options（List&lt;LabVar&gt;/List&lt;LabOption&gt;）同样逐层洗成真 bean。
     */
    static List<CustomCommand> parseCommands(Reader reader) {
        List<CustomCommand> out = new ArrayList<>();
        Gson g = new Gson();
        JsonElement root;
        try {
            root = JsonParser.parseReader(reader);
        } catch (Exception e) {
            return out;
        }
        if (root == null || !root.isJsonArray()) return out;
        for (JsonElement e : root.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            // 逐条兜底（与 LabRegistry.parseRegistry 同口径）：一条外部形状不对
            // 只跳这一条并留痕，不许抛穿把整表吞成空表——用户自建命令与导入
            // 的命令都在这一张表里，整表清空等于全丢且无提示
            try {
                CustomCommand cc = g.fromJson(e, CustomCommand.class);
                if (cc == null || TextUtils.isEmpty(cc.command)) continue;
                // 默影视形 description 与星辰形 desc 对名：Gson 只认 desc，
                // 外部按 description 写的 custom.json 在这里补上，不丢说明
                if (TextUtils.isEmpty(cc.desc) && e.getAsJsonObject().has("description")) {
                    try {
                        cc.desc = e.getAsJsonObject().get("description").getAsString();
                    } catch (Exception ignored) {
                    }
                }
                List<LabManifest.LabVar> vars = new ArrayList<>();
                for (Object vo : rawList(cc.variables)) {
                    LabManifest.LabVar v = g.fromJson(g.toJsonTree(vo), LabManifest.LabVar.class);
                    if (v == null) continue;
                    List<LabManifest.LabOption> opts = new ArrayList<>();
                    for (Object oo : rawList(v.options)) {
                        LabManifest.LabOption op = g.fromJson(g.toJsonTree(oo), LabManifest.LabOption.class);
                        if (op != null) opts.add(op);
                    }
                    v.options.clear();
                    v.options.addAll(opts);
                    vars.add(v);
                }
                cc.variables.clear();
                cc.variables.addAll(vars);
                out.add(cc);
            } catch (Exception ex) {
                android.util.Log.w("LabCustomCommands", "custom.json 有一条命令形状不对，已跳过这一条", ex);
            }
        }
        return out;
    }

    /** 裸形态的 List（R8 下元素可能是 LinkedTreeMap）：只按 Object 遍历，不强转 */
    private static List<?> rawList(List<?> l) {
        return l == null ? Collections.emptyList() : l;
    }

    public static synchronized void add(Context c, String pkgId, CustomCommand cmd) {
        List<CustomCommand> all = list(c, pkgId);
        if (TextUtils.isEmpty(cmd.id)) cmd.id = "custom" + System.currentTimeMillis();
        all.add(cmd);
        save(c, pkgId, all);
    }

    public static synchronized void update(Context c, String pkgId, CustomCommand cmd) {
        List<CustomCommand> all = list(c, pkgId);
        for (int i = 0; i < all.size(); i++) {
            if (cmd.id != null && cmd.id.equals(all.get(i).id)) {
                all.set(i, cmd);
                break;
            }
        }
        save(c, pkgId, all);
    }

    public static synchronized void remove(Context c, String pkgId, String cmdId) {
        List<CustomCommand> all = list(c, pkgId);
        for (int i = all.size() - 1; i >= 0; i--) {
            if (cmdId != null && cmdId.equals(all.get(i).id)) all.remove(i);
        }
        save(c, pkgId, all);
    }

    private static synchronized void save(Context c, String pkgId, List<CustomCommand> all) {
        try {
            File f = customFile(c, pkgId);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(f);
                 OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                new Gson().toJson(all, w);
            }
        } catch (Exception ignored) {
        }
    }

    /** 包设置值读 */
    public static synchronized Map<String, String> settingsOf(Context c, String pkgId) {
        Map<String, String> out = new HashMap<>();
        File f = settingsFile(c, pkgId);
        if (!f.exists()) return out;
        try (FileInputStream in = new FileInputStream(f);
             InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            out.putAll(parseSettings(r));
        } catch (Exception ignored) {
        }
        return out;
    }

    /**
     * settings.json 读回（同上根治口径）：裸形态按条目取字符串，
     * 值不是字符串时也不崩（老写法在取值处强转 String 会崩）。
     */
    static Map<String, String> parseSettings(Reader reader) {
        Map<String, String> out = new HashMap<>();
        Gson g = new Gson();
        JsonElement root;
        try {
            root = JsonParser.parseReader(reader);
        } catch (Exception e) {
            return out;
        }
        if (root == null || !root.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
            JsonElement v = e.getValue();
            if (v == null || v.isJsonNull()) continue;
            out.put(e.getKey(), v.isJsonPrimitive() ? v.getAsString() : g.toJson(v));
        }
        return out;
    }

    /** 包设置值写 */
    public static synchronized void saveSettings(Context c, String pkgId, Map<String, String> values) {
        try {
            File f = settingsFile(c, pkgId);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(f);
                 OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                new Gson().toJson(values == null ? new HashMap<String, String>() : values, w);
            }
        } catch (Exception ignored) {
        }
    }

    /** 变量值缓存：按包＋变量 key 落盘（上游变量值缓存复用口径） */
    public static synchronized Map<String, String> varCache(Context c, String pkgId) {
        return settingsOf(c, pkgId);
    }

    /** 变量值缓存写：合并进包设置存储（读侧 varCache 即 settingsOf） */
    public static synchronized void saveVarCache(Context c, String pkgId, Map<String, String> values) {
        if (values == null || values.isEmpty()) return;
        Map<String, String> cur = settingsOf(c, pkgId);
        cur.putAll(values);
        saveSettings(c, pkgId, cur);
    }

    /** 自定义命令转成 LabManifest.LabCommand 形态，复用运行链 */
    public static LabManifest.LabCommand toCommand(CustomCommand cc) {
        LabManifest.LabCommand cmd = new LabManifest.LabCommand();
        cmd.id = cc.id;
        cmd.name = cc.name;
        cmd.desc = cc.desc == null ? "" : cc.desc;
        cmd.command = cc.command;
        cmd.background = cc.background;
        cmd.autoExecute = cc.autoExecute;
        cmd.showOutput = cc.showOutput;
        cmd.minVersion = cc.minVersion;
        cmd.variables.addAll(cc.variables);
        return cmd;
    }
}
