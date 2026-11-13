package com.fongmi.android.tv.ui.lab;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 命令相对路径兜底（纯函数，无安卓依赖，便于 JVM 单测）。
 *
 * 背景：LabRunner 执行命令时工作目录是包根，但有些包把脚本放在包内 files/ 子目录里，
 * 命令却写成相对路径（如 python3 bot.py），真机上就报 No such file or directory。
 * 这个兜底在命令最终形态（占位符已展开）上做一次检查：相对路径 token 在包根下不
 * 存在、但在包根的 files/ 子目录下存在时，补上 files/ 前缀。
 *
 * 零影响保证：只有「包根没有＋files/ 有」时才改写，其它情况原样返回。
 */
public final class LabPathAlign {

    private LabPathAlign() {
    }

    /** 对最终命令做路径对齐；改没改看返回值和入参是否 equals */
    public static String align(File pkgDir, String command) {
        if (command == null || command.isEmpty() || pkgDir == null) return command;
        List<Tok> toks = tokenize(command);
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        boolean changed = false;
        for (Tok t : toks) {
            sb.append(command, pos, t.start);
            String v = t.value;
            String rep = v;
            if (looksLikeRelPath(v)) {
                String rel = v.startsWith("./") ? v.substring(2) : v;
                if (!new File(pkgDir, rel).exists() && new File(pkgDir, "files/" + rel).exists()) {
                    rep = "files/" + rel;
                    changed = true;
                }
            }
            if (t.quote != 0) sb.append(t.quote).append(rep).append(t.quote);
            else sb.append(rep);
            pos = t.end;
        }
        sb.append(command, pos, command.length());
        return changed ? sb.toString() : command;
    }

    /** 像不像相对路径参数：xxx.py / xxx.sh / ./xxx（../xxx 同理） */
    private static boolean looksLikeRelPath(String v) {
        if (v.isEmpty()) return false;
        if (v.charAt(0) == '/') return false;                    // 绝对路径不动
        if (v.indexOf('{') >= 0 || v.indexOf('}') >= 0) return false; // 没展开的占位符不动
        if (v.charAt(0) == '-') return false;                     // -u、--port 这类开关不动
        int eq = v.indexOf('=');
        int slash = v.indexOf('/');
        if (eq >= 0 && (slash < 0 || eq < slash)) return false;   // FOO=bar 这类赋值不动
        if (v.startsWith("./") || v.startsWith("../")) return true;
        return v.endsWith(".py") || v.endsWith(".sh");
    }

    private static final class Tok {
        int start, end; // 在原串中的 [start,end)
        String value;   // 去引号后的值
        char quote;     // 包裹引号：' 或 "，0=无
    }

    /** shell 风格切词：尊重引号，保留每个 token 在原串中的起止位置 */
    private static List<Tok> tokenize(String s) {
        List<Tok> out = new ArrayList<>();
        int n = s.length(), i = 0;
        while (i < n) {
            while (i < n && Character.isWhitespace(s.charAt(i))) i++;
            if (i >= n) break;
            int start = i;
            char quote = 0;
            StringBuilder v = new StringBuilder();
            if (s.charAt(i) == '\'' || s.charAt(i) == '"') {
                quote = s.charAt(i);
                i++;
                while (i < n && s.charAt(i) != quote) {
                    v.append(s.charAt(i));
                    i++;
                }
                if (i < n) i++; // 跳过闭引号
            } else {
                while (i < n && !Character.isWhitespace(s.charAt(i))) {
                    char ch = s.charAt(i);
                    if (ch == '\'' || ch == '"') {
                        char q = ch;
                        i++;
                        while (i < n && s.charAt(i) != q) {
                            v.append(s.charAt(i));
                            i++;
                        }
                        if (i < n) i++;
                    } else {
                        v.append(ch);
                        i++;
                    }
                }
            }
            Tok t = new Tok();
            t.start = start;
            t.end = i;
            t.value = v.toString();
            t.quote = quote;
            out.add(t);
        }
        return out;
    }
}
