package com.fongmi.android.tv.ui.lab;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 进程跟踪（照参照物 LabProcManager 的做法，简化）：
 * pid 落盘＋状态 JSON，App 重启后按 /proc 校验恢复（防 pid 复用：对启动时间）。
 * 停止按进程组收：先收子孙进程，再 kill -9 -pgid。
 * 全走流式 IO，不用 java.nio（minSdk 24 可用）。
 */
public final class LabProc {

    private LabProc() {
    }

    /** Process → pid（反射；API 26 以下没有 Process.pid()） */
    public static int pidOf(Process p) {
        if (p == null) return -1;
        try {
            java.lang.reflect.Field f = p.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            return f.getInt(p);
        } catch (Exception e) {
            return -1;
        }
    }

    /** 进程还活着（API 26 以下无 isAlive()，用 exitValue 探测） */
    public static boolean alive(Process p) {
        if (p == null) return false;
        try {
            p.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** /proc 里这个 pid 还在且启动时间对得上 */
    public static boolean pidAlive(int pid, long start) {
        if (pid <= 0) return false;
        File stat = new File("/proc/" + pid + "/stat");
        if (!stat.exists()) return false;
        if (start <= 0) return true;
        return startTime(pid) == start;
    }

    /** /proc/<pid>/stat 第 22 字段：启动时间（防 pid 复用） */
    public static long startTime(int pid) {
        try {
            String stat = readFile(new File("/proc/" + pid + "/stat"));
            int end = stat.lastIndexOf(')');
            if (end < 0) return 0;
            String[] fs = stat.substring(end + 2).trim().split("\\s+");
            if (fs.length < 20) return 0;
            return Long.parseLong(fs[19]);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int parentPid(int pid) {
        try {
            String stat = readFile(new File("/proc/" + pid + "/stat"));
            int end = stat.lastIndexOf(')');
            if (end < 0) return -1;
            String[] fs = stat.substring(end + 2).trim().split("\\s+");
            if (fs.length < 2) return -1;
            return Integer.parseInt(fs[1]);
        } catch (Exception e) {
            return -1;
        }
    }

    /** 按进程组收：子孙先杀，最后 kill -9 -pgid（setsid 起的，pid 即 pgid） */
    public static void killTree(int pid) {
        if (pid <= 0) return;
        List<Integer> kids = new ArrayList<>();
        collectKids(pid, kids);
        for (int i = kids.size() - 1; i >= 0; i--) killOne(kids.get(i));
        killOne(pid);
        try {
            Runtime.getRuntime().exec(new String[]{"kill", "-9", "-" + pid}).waitFor();
        } catch (Exception ignored) {
        }
    }

    private static void killOne(int pid) {
        try {
            Runtime.getRuntime().exec(new String[]{"kill", "-9", String.valueOf(pid)}).waitFor();
        } catch (Exception ignored) {
        }
    }

    private static void collectKids(int pid, List<Integer> out) {
        File[] dirs = new File("/proc").listFiles();
        if (dirs == null) return;
        for (File d : dirs) {
            if (!d.isDirectory()) continue;
            String n = d.getName();
            boolean num = n.length() > 0;
            for (int i = 0; num && i < n.length(); i++) num = Character.isDigit(n.charAt(i));
            if (!num) continue;
            try {
                int child = Integer.parseInt(n);
                if (parentPid(child) == pid) {
                    out.add(child);
                    collectKids(child, out);
                }
            } catch (Exception ignored) {
            }
        }
    }

    // ---------------- 状态落盘 ----------------

    private static File stateFile(Context c) {
        return new File(LabPaths.pids(c), "state.json");
    }

    public static synchronized void track(Context c, String key, int pid, String logPath) {
        try {
            Map<String, JSONObject> state = load(c);
            JSONObject e = new JSONObject();
            e.put("pid", pid);
            e.put("start", startTime(pid));
            e.put("log", logPath);
            state.put(key, e);
            save(c, state);
            File pf = new File(LabPaths.pids(c), keyFile(key));
            pf.getParentFile().mkdirs();
            writeFile(pf, String.valueOf(pid));
        } catch (Exception ignored) {
        }
    }

    public static synchronized void untrack(Context c, String key) {
        try {
            Map<String, JSONObject> state = load(c);
            state.remove(key);
            save(c, state);
            new File(LabPaths.pids(c), keyFile(key)).delete();
        } catch (Exception ignored) {
        }
    }

    /** 恢复后仍活着的 key 列表 */
    public static synchronized List<String> recoveredAliveKeys(Context c) {
        List<String> alive = new ArrayList<>();
        Map<String, JSONObject> state = load(c);
        Map<String, JSONObject> keep = new HashMap<>();
        for (Map.Entry<String, JSONObject> en : state.entrySet()) {
            int pid = en.getValue().optInt("pid", -1);
            long start = en.getValue().optLong("start", 0);
            if (pidAlive(pid, start)) {
                alive.add(en.getKey());
                keep.put(en.getKey(), en.getValue());
            }
        }
        save(c, keep);
        return alive;
    }

    public static synchronized void stopByState(Context c, String key) {
        Map<String, JSONObject> state = load(c);
        JSONObject e = state.get(key);
        if (e != null) killTree(e.optInt("pid", -1));
        untrack(c, key);
    }

    public static synchronized String logPathOf(Context c, String key) {
        Map<String, JSONObject> state = load(c);
        JSONObject e = state.get(key);
        return e == null ? null : e.optString("log", null);
    }

    public static File logFile(Context c, String key) {
        File dir = LabPaths.logs(c);
        dir.mkdirs();
        return new File(dir, keyFile(key) + ".log");
    }

    private static String keyFile(String key) {
        // 清洗后再加原键短哈希：光清洗会把不同键映成同名（"a/b" 与 "a:b" 都成 "a_b"），
        // 两条命令共用一个 pid/日志文件、互杀互串。改名后旧文件成孤儿，state.json 为准。
        return key.replaceAll("[^a-zA-Z0-9_.-]", "_") + "-" + shortHash(key);
    }

    private static String shortHash(String key) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-1")
                    .digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return "x" + Math.abs(key.hashCode());
        }
    }

    private static Map<String, JSONObject> load(Context c) {
        Map<String, JSONObject> map = new HashMap<>();
        try {
            File f = stateFile(c);
            if (!f.exists()) return map;
            JSONObject o = new JSONObject(readFile(f));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                map.put(k, o.getJSONObject(k));
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private static void save(Context c, Map<String, JSONObject> state) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, JSONObject> e : state.entrySet()) o.put(e.getKey(), e.getValue());
            File f = stateFile(c);
            f.getParentFile().mkdirs();
            writeFile(f, o.toString());
        } catch (Exception ignored) {
        }
    }

    private static String readFile(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream in = new FileInputStream(f);
             InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(r)) {
            char[] buf = new char[4096];
            int n;
            while ((n = br.read(buf)) > 0) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private static void writeFile(File f, String s) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f);
             OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            w.write(s);
        }
    }
}
