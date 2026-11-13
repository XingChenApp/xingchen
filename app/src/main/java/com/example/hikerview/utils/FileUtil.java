package com.example.hikerview.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.HkCryptoFileUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URLDecoder;
import java.text.DecimalFormat;
import java.util.regex.Pattern;

/**
 * 8.83 {@code com.example.hikerview.utils.FileUtil} 的同包名同签名补齐。
 *
 * <p>原版里这个类是 App 包内真实类，规则 JS 经 Rhino Java 互操作在<b>任意</b>
 * 求值作用域（列表/详情/搜索/播放/图片）都能直调
 * {@code com.example.hikerview.utils.FileUtil.xxx(...)}。星辰此前没有这个类，
 * 只有图片作用域靠 JS 链 shim 兜住，列表等作用域直调即抛错（9th 视频规则
 * 列表期解密封面失败的根）。本类按原包名补回真实类，各作用域原生可达；
 * 方法语义对照 8.83 FileUtil.smali 与其开源同源实现，纯文件/流/加解密工具面。
 * 与 App 内部状态绑定的两个方法未补（见类尾注记）。</p>
 */
public class FileUtil {

    private static final HkCryptoFileUtil CRYPTO = new HkCryptoFileUtil();

    // ---- 流/字节互转（crypto-java.js 与图片解密链的核心面，与 HkCryptoFileUtil 同一实现） ----

    /** 读完流并返回全部字节；空流返回空数组（原版同款语义）。 */
    public static byte[] toBytes(InputStream in) throws IOException {
        return CRYPTO.toBytes(in);
    }

    /** 字节数组 → 可重复读的输入流（原版同款语义）。 */
    public static InputStream toInputStream(byte[] bytes) {
        return CRYPTO.toInputStream(bytes);
    }

    /** 把输入流原样写到输出流（不关流，原版同款）。 */
    public static void write(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        out.flush();
    }

    // ---- 文件读写 ----

    /** 读文件全部字节；入参空或读失败返回 null（原版同款）。 */
    public static byte[] fileToBytes(String filePath) {
        if (filePath == null) return null;
        try {
            File f = new File(filePath);
            byte[] bytes = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            try {
                int off = 0;
                while (off < bytes.length) {
                    int n = in.read(bytes, off, bytes.length - off);
                    if (n < 0) break;
                    off += n;
                }
                return off == bytes.length ? bytes : java.util.Arrays.copyOf(bytes, off);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 打开文件为输入流；失败返回 null。 */
    public static InputStream fileToInputStream(String filePath) {
        try {
            return new FileInputStream(filePath);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 读文件为字符串（原版：平台默认字符集逐行读、每行后补 "\n"；Android 默认即 UTF-8）。
     * 读失败返回已读部分（无内容时为空串）。
     */
    public static String fileToString(String filePath) {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first || sb.length() > 0) sb.append("\n");
                sb.append(line);
                first = false;
            }
            if (sb.length() > 0) sb.append("\n");
        } catch (Exception ignored) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
        return sb.toString();
    }

    /** 写字符串到文件（原版：先建父目录，平台默认字符集，失败吞掉只记日志）。 */
    public static void stringToFile(String text, String fileName) {
        try {
            File target = new File(fileName);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            BufferedWriter writer = new BufferedWriter(new FileWriter(target));
            try {
                writer.write(text == null ? "" : text);
                writer.flush();
            } finally {
                writer.close();
            }
        } catch (Exception ignored) {
        }
    }

    /** 写字节到文件（先建父目录）。 */
    public static void bytesToFile(String fullFilePath, byte[] content) {
        try {
            File target = new File(fullFilePath);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileOutputStream out = new FileOutputStream(target);
            try {
                if (content != null) out.write(content);
                out.flush();
            } finally {
                out.close();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 按字符集探测读文件（原版 FileUtil.getContentByCharsetDetect）：
     * .js/.json 直接按默认字符集（UTF-8、剥 BOM）读；其余用 juniversalchardet
     * 探测编码解码，探测失败回退默认读取。与 HkJsRuntime 内同名移植同口径。
     */
    public static String getContentByCharsetDetect(String path) {
        if (path != null && (path.endsWith(".js") || path.endsWith(".json"))) {
            return readUtf8(path);
        }
        String encode = detectCharset(path);
        if (encode == null || encode.equalsIgnoreCase("UTF-8")) return readUtf8(path);
        try {
            byte[] bytes = fileToBytes(path);
            if (bytes == null) return "";
            return new String(bytes, encode);
        } catch (Exception e) {
            return readUtf8(path);
        }
    }

    private static String readUtf8(String path) {
        try {
            byte[] bytes = fileToBytes(path);
            if (bytes == null) return "";
            String s = new String(bytes, "UTF-8");
            if (!s.isEmpty() && s.charAt(0) == '\uFEFF') s = s.substring(1);
            return s;
        } catch (Exception e) {
            return "";
        }
    }

    private static String detectCharset(String path) {
        org.mozilla.universalchardet.UniversalDetector detector = null;
        InputStream in = null;
        try {
            detector = new org.mozilla.universalchardet.UniversalDetector(null);
            in = new java.io.BufferedInputStream(new FileInputStream(path));
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, n);
            }
            detector.dataEnd();
            return detector.getDetectedCharset();
        } catch (Throwable e) {
            return null;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** 打开文件为缓冲读取器（原版同款，失败抛 IOException）。 */
    public static BufferedReader openFile(String fileName) throws IOException {
        return new BufferedReader(new FileReader(fileName));
    }

    /** 读文件第一行；失败返回 null。 */
    public static String readLine(String inName) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(inName));
            return reader.readLine();
        } catch (Exception e) {
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ---- 拷贝/移动/删除 ----

    public static void copyFile(String inName, String outName) throws IOException {
        FileInputStream in = new FileInputStream(inName);
        try {
            FileOutputStream out = new FileOutputStream(outName);
            try {
                write(in, out);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    public static void copy(File source, File target) throws IOException {
        FileInputStream in = new FileInputStream(source);
        try {
            FileOutputStream out = new FileOutputStream(target);
            try {
                write(in, out);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    public static void copyFile(InputStream is, OutputStream os, boolean close) throws IOException {
        try {
            write(is, os);
        } finally {
            if (close) {
                try {
                    is.close();
                } catch (Exception ignored) {
                }
                try {
                    os.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void copyFile(Reader is, Writer os, boolean close) throws IOException {
        try {
            char[] buf = new char[4096];
            int n;
            while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
            os.flush();
        } finally {
            if (close) {
                try {
                    is.close();
                } catch (Exception ignored) {
                }
                try {
                    os.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void copyFile(String inName, PrintWriter pw, boolean close) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inName));
        try {
            String line;
            while ((line = reader.readLine()) != null) pw.println(line);
            pw.flush();
        } finally {
            reader.close();
            if (close) pw.close();
        }
    }

    public static void copyFileBuffered(String inName, String outName) throws IOException {
        java.io.BufferedInputStream in = new java.io.BufferedInputStream(new FileInputStream(inName));
        try {
            java.io.BufferedOutputStream out = new java.io.BufferedOutputStream(new FileOutputStream(outName));
            try {
                write(in, out);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    /** 目录对目录拷贝（原版 8.83 返回拷贝的文件数）。 */
    public static int copyDirectiory(String objDir, String srcDir) throws IOException {
        new File(objDir).mkdirs();
        File[] files = new File(srcDir).listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File f : files) {
            if (f.isFile()) {
                copy(f, new File(objDir, f.getName()));
                count++;
            } else if (f.isDirectory()) {
                count += copyDirectiory(objDir + "/" + f.getName(), f.getAbsolutePath());
            }
        }
        return count;
    }

    public static void deleteFile(String path) {
        try {
            new File(path).delete();
        } catch (Exception ignored) {
        }
    }

    /** 递归删除整个目录（含自身）。 */
    public static void deleteDirs(String path) {
        File root = new File(path);
        if (!root.exists()) return;
        deleteRecursive(root);
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }

    public static void makeSureDirExist(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) f.mkdirs();
        } catch (Exception ignored) {
        }
    }

    public static void renameFile(String path, String oldname, String newname) {
        try {
            File old = new File(path, oldname);
            File neo = new File(path, newname);
            if (old.exists() && !neo.exists()) old.renameTo(neo);
        } catch (Exception ignored) {
        }
    }

    public static boolean renameDir(String oldDirPath, String newDirPath) {
        try {
            File old = new File(oldDirPath);
            File neo = new File(newDirPath);
            if (!old.exists() || neo.exists()) return false;
            return old.renameTo(neo);
        } catch (Exception e) {
            return false;
        }
    }

    /** 移动文件（先 renameTo，失败退化为拷贝+删源）；返回目标绝对路径。 */
    public static String moveFileCompat(File source, File target) {
        try {
            if (source == null || target == null) return "";
            if (source.renameTo(target)) return target.getAbsolutePath();
            copy(source, target);
            source.delete();
            return target.getAbsolutePath();
        } catch (Exception e) {
            return target == null ? "" : target.getAbsolutePath();
        }
    }

    public static long getFolderSize(File file) {
        if (file == null) return 0;
        if (!file.isDirectory()) return file.length();
        long size = 0;
        File[] list = file.listFiles();
        if (list != null) {
            for (File f : list) size += getFolderSize(f);
        }
        return size;
    }

    public static int getFileCount(String dir) {
        File f = new File(dir);
        if (!f.exists()) return 0;
        if (f.isFile()) return 1;
        int count = 0;
        File[] list = f.listFiles();
        if (list != null) {
            for (File c : list) count += c.isDirectory() ? getFileCount(c.getAbsolutePath()) : 1;
        }
        return count;
    }

    // ---- 名称/路径助手（原版语义） ----

    /** 文件名（去路径，含扩展名）。 */
    public static String getFileName(String fullPath) {
        int i = Math.max(fullPath.lastIndexOf("/"), fullPath.lastIndexOf("\\"));
        return fullPath.substring(i + 1);
    }

    /** 同 getFileName（原版 URL 场景同名方法）。 */
    public static String getFileName0(String url) {
        return getFileName(url);
    }

    /** 后缀名（最后一个 "." 之后，不含点）。 */
    public static String getFileSuffix(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    /** 扩展名；无 "." 或 "." 在首位时返回 ""（原版同款）。 */
    public static String getExtension(String fileName) {
        if (fileName.lastIndexOf(".") < 1) return "";
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    /** 去扩展名的完整串（原版 getName：对整串处理，不先剥路径）。 */
    public static String getName(String fileName) {
        if (fileName.lastIndexOf(".") < 1) return fileName;
        return fileName.substring(0, fileName.lastIndexOf("."));
    }

    /** 文件名去扩展名（先剥路径再去后缀）。 */
    public static String getPureFileName(String fullPath) {
        String name = getFileName(fullPath);
        int dot = name.lastIndexOf(".");
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** 同 getPureFileName 口径（原版 getSimpleName）。 */
    public static String getSimpleName(String path) {
        return getPureFileName(path);
    }

    /** 资源名：剥 ? 查询与 ## 后缀，取最后一段路径，再去扩展名（原版同款）。 */
    public static String getResourceName(String url) {
        if (url == null) return "";
        String s = url.split("\\?")[0].split("##")[0];
        String name = getFileName(s);
        int dot = name.lastIndexOf(".");
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** URL 解码后的文件名（原版 getNameByUrl = decodeUrl(getFileName0(url), "UTF-8")）。 */
    public static String getNameByUrl(String url) {
        String name = getFileName0(url);
        try {
            return URLDecoder.decode(name, "UTF-8");
        } catch (Exception e) {
            return name;
        }
    }

    /** 路径归一：反斜杠转正斜杠且保证以 "/" 结尾（原版同款，含其首行 replace 未赋值的原样行为修正为有效归一）。 */
    public static String wrapFilePath(String filePath) {
        String p = filePath.replace('\\', '/');
        if (p.charAt(p.length() - 1) != '/') p += "/";
        return p;
    }

    /** 清掉文件名非法字符（原版正则：\\ / : * ? " < > | . 一并去掉）。 */
    public static String fileNameFilter(String fileName) {
        Pattern pattern = Pattern.compile("[\\\\/:*?\"<>|.]");
        return fileName == null ? null : pattern.matcher(fileName).replaceAll("");
    }

    public static String getFormatedFileSize(long size) {
        DecimalFormat df = new DecimalFormat("#.00");
        if (size < 1024) return size + "B";
        if (size < 1024 * 1024) return df.format(size / 1024.0) + "KB";
        if (size < 1024 * 1024 * 1024) return df.format(size / (1024.0 * 1024)) + "MB";
        return df.format(size / (1024.0 * 1024 * 1024)) + "GB";
    }

    /**
     * hiker://files/ 与 file:// 路径解析（原版 FileUtil.getFilePath）：
     * hiker://files/<p> → 外部 Documents 根/<p>（与 JSEngine.getFilePath 同根）；
     * file://<p> → <p>；其余返回 null（原版同款）。
     */
    public static String getFilePath(String url) {
        if (url == null) return null;
        try {
            if (url.startsWith("hiker://files/")) {
                String name = url.replace("hiker://files/", "");
                Context ctx = App.get();
                if (ctx == null) return null;
                File docs = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                if (docs == null) return null;
                if (!docs.exists()) docs.mkdirs();
                return new File(docs, name).getAbsolutePath();
            }
            if (url.startsWith("file://")) {
                return new File(url.replace("file://", "")).getAbsolutePath();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 同 getFilePath，但文件不存在时返回 null（原版同款）。 */
    public static String getExistFilePath(String url) {
        String path = getFilePath(url);
        if (path == null) return null;
        return new File(path).exists() ? path : null;
    }

    /** 从 classpath 根读文件为 GBK 字符串（原版同款）；失败返回 null。 */
    public static String getCPFile(String path) {
        try {
            java.net.URL url = FileUtil.class.getClassLoader().getResource(path);
            if (url == null) return null;
            File file = new File(url.getFile());
            byte[] buf = new byte[(int) file.length()];
            FileInputStream in = new FileInputStream(file);
            try {
                int off = 0;
                while (off < buf.length) {
                    int n = in.read(buf, off, buf.length - off);
                    if (n < 0) break;
                    off += n;
                }
            } finally {
                in.close();
            }
            return new String(buf, "GBK");
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 图片相关 ----

    /** Bitmap 存为 PNG 文件（原版 bitmapToFile）。 */
    public static void bitmapToFile(Bitmap bitmap, File file) {
        if (bitmap == null || file == null) return;
        FileOutputStream out = null;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
        } catch (Exception ignored) {
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** assets 目录递归拷贝到目标目录（原版同款）。 */
    public static void copyFilesFromAssets(Context context, String assetsPath, String savePath) {
        try {
            String[] files = context.getAssets().list(assetsPath);
            if (files == null) return;
            if (files.length > 0) {
                File dir = new File(savePath);
                if (!dir.exists()) dir.mkdirs();
                for (String name : files) {
                    String childAssets = assetsPath.isEmpty() ? name : assetsPath + "/" + name;
                    String childSave = savePath + "/" + name;
                    if (context.getAssets().list(childAssets) != null
                            && context.getAssets().list(childAssets).length > 0) {
                        copyFilesFromAssets(context, childAssets, childSave);
                    } else {
                        InputStream in = context.getAssets().open(childAssets);
                        try {
                            FileOutputStream out = new FileOutputStream(new File(childSave));
                            try {
                                write(in, out);
                            } finally {
                                out.close();
                            }
                        } finally {
                            in.close();
                        }
                    }
                }
            } else {
                InputStream in = context.getAssets().open(assetsPath);
                try {
                    FileOutputStream out = new FileOutputStream(new File(savePath));
                    try {
                        write(in, out);
                    } finally {
                        out.close();
                    }
                } finally {
                    in.close();
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 假图片转 TS（原版 png2Ts）：某些分片以真图片头伪装，探测 mime 后按图头
     * 长度（PNG 8 字节 / BMP 14 字节，其余 0）剥掉头部，余下字节写入同名 .ts
     * 文件并返回其路径（目标已存在先删）。
     */
    public static String png2Ts(String path) {
        try {
            int dot = path.lastIndexOf(".");
            String target = (dot > 0 ? path.substring(0, dot) : path) + ".ts";
            File out = new File(target);
            if (out.exists()) out.delete();
            int offset = 0;
            try {
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, opt);
                if ("image/png".equals(opt.outMimeType)) offset = 8;
                else if ("image/bmp".equals(opt.outMimeType)) offset = 14;
            } catch (Exception ignored) {
            }
            java.io.RandomAccessFile in = new java.io.RandomAccessFile(path, "r");
            try {
                if (offset > 0) in.seek(offset);
                FileOutputStream fos = new FileOutputStream(out);
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                    fos.flush();
                } finally {
                    fos.close();
                }
            } finally {
                in.close();
            }
            return target;
        } catch (Exception e) {
            return path;
        }
    }

    /*
     * 未补的两个原版方法（原因如实记录）：
     * - scanFiles(String, com.annimon.stream.function.Consumer)：annimon 流库不在
     *   星辰依赖清单里，原签名无法在本工程编译；无规则 JS 直调样本，不补。
     * - saveFile(Context, Runnable)：原版走 XPermission（lxj xpopup 库，未引入）
     *   申请存储权限的 UI 流程，不是文件工具语义，不补。
     * 另 generateHtml / clearExtension / generateFileName 三个助手依赖原版播放页
     * 模板与 UrlDetector 分类器（App 内部特征），无规则 JS 消费证据，未补。
     */
}
