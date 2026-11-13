package com.fongmi.android.tv.api.hk;

import android.content.Context;

import com.fongmi.android.tv.App;

import org.mozilla.javascript.GeneratedClassLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import dalvik.system.DexClassLoader;

/**
 * 官形类生成装载器（8.83 com.example.hikerview.service.parser.rhino.
 * AndroidClassLoader 镜像）：Rhino 生成的 JVM 字节码在 ART 上不能直
 * 接 defineClass（"can't load this type of class file"，聚阅 128 真机
 * 报错原文），官方的解法是把类字节先写成 jar、用包内 dx 工具
 * （com.android.dx.command.dexer.Main）翻成 dex 文件、再用
 * DexClassLoader 加载。本类逐点镜像其语义：
 * defineClass＝写 jar→dx 翻 dex→loadDex→loadClass；linkClass 空实现；
 * loadClass 序＝已定义表→各 DexClassLoader→父加载器。
 *
 * <p>与官形的有意差别只有两处（多引擎上下文并存的安全需要，行为
 * 等价）：①临时文件按「类名＋字节 md5」定名（官方用时间戳临时名＋
 * 构造时清缓存目录，多上下文并存会互相清掉对方已生成的 dex）；
 * ②写 jar 用 java.util.jar（官方用 zip4j，产物同为单条目 jar）。</p>
 *
 * <p>dx 工具本体不在树内：官包雕刻的 hk-dx.dex（com.android.dx＋
 * com.android.dex 共 611 类，账 juyue-hikerpop-gap-20261113）随包放
 * assets，首次用时由 {@link Dx} 解到 codeCacheDir 文件式加载——
 * 反射调用面只有 dexer.Main.run 一处。</p>
 */
public class HkAndroidClassLoader extends ClassLoader implements GeneratedClassLoader {

    private final ClassLoader parent;
    private final File cacheDir;
    private final List<DexClassLoader> dexLoaders = new ArrayList<>();
    private final Map<String, Class<?>> defined = new LinkedHashMap<>();

    public HkAndroidClassLoader(ClassLoader parent) {
        super(parent);
        this.parent = parent;
        File base = App.get().getCodeCacheDir();
        this.cacheDir = new File(base, "hk-rhino-dex");
        //noinspection ResultOfMethodCallIgnored
        cacheDir.mkdirs();
    }

    @Override
    public Class<?> defineClass(String name, byte[] data) {
        synchronized (this) {
            Class<?> old = defined.get(name);
            if (old != null) return old;
            try {
                File jar = writeJar(name, data);
                File dexJar = new File(cacheDir, jar.getName().replace(".jar", "-dex.jar"));
                if (!dexJar.isFile()) {
                    Dx.run(jar, dexJar);
                    markReadOnly(dexJar);
                }
                DexClassLoader loader = loadDex(dexJar);
                Class<?> cl = loader.loadClass(name);
                defined.put(name, cl);
                return cl;
            } catch (Exception e) {
                // 真因直抛：不许回落裸 defineClass 用 ART 的错盖住翻译失败。
                throw new RuntimeException("hk class gen failed: " + name, e);
            }
        }
    }

    @Override
    public void linkClass(Class<?> cl) {
        // 官形空实现：DexClassLoader 加载的类已由 ART 链接。
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> cl = defined.get(name);
        if (cl == null) {
            synchronized (this) {
                cl = defined.get(name);
                if (cl == null) {
                    for (DexClassLoader loader : dexLoaders) {
                        try {
                            cl = loader.loadClass(name);
                            break;
                        } catch (ClassNotFoundException ignored) {
                            // 官形同：逐个问，问不到再落父加载器
                        }
                    }
                }
            }
        }
        if (cl == null) cl = parent.loadClass(name);
        if (resolve) resolveClass(cl);
        return cl;
    }

    /** 官形 loadDex：DexClassLoader（dex 路径, 本缓存目录作优化目录, 无本地库, 父＝传入父加载器），并登记进表。 */
    private DexClassLoader loadDex(File dexFile) {
        DexClassLoader loader = new DexClassLoader(
                dexFile.getPath(), cacheDir.getPath(), null, parent);
        dexLoaders.add(loader);
        return loader;
    }

    /** 类字节写成单条目 jar（条目名＝类名路径形，官形 zip4j 同义）。 */
    private File writeJar(String name, byte[] data) throws IOException {
        File jar = new File(cacheDir, "gen-" + md5(name + ":" + md5(data)) + ".jar");
        if (jar.isFile()) return jar;
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
            out.putNextEntry(new JarEntry(name.replace('.', '/') + ".class"));
            out.write(data);
            out.closeEntry();
        }
        return jar;
    }

    private static void markReadOnly(File f) {
        // API 34+ 的 DexClassLoader 要求被加载文件只读，写完即置。
        //noinspection ResultOfMethodCallIgnored
        f.setReadOnly();
    }

    private static String md5(String s) {
        return md5(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String md5(byte[] bytes) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(bytes);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(java.util.Arrays.hashCode(bytes));
        }
    }

    /**
     * dx 工具桥（官包雕刻 dex 的唯一调用面）：assets/hk-dx.dex 首次
     * 用时解到 codeCacheDir/hk-dx/ 并文件式加载；翻译走 dexer.Main.run，
     * dx 内部有静态状态，全局串行。
     */
    private static final class Dx {

        private static final Object LOCK = new Object();
        private static ClassLoader dxLoader;

        static void run(File jar, File outDexJar) throws Exception {
            synchronized (LOCK) {
                ClassLoader loader = loader();
                Class<?> argsClass = Class.forName(
                        "com.android.dx.command.dexer.Main$Arguments", true, loader);
                Object args = argsClass.getDeclaredConstructor().newInstance();
                Field fileNames = argsClass.getField("fileNames");
                fileNames.set(args, new String[]{jar.getPath()});
                Field outName = argsClass.getField("outName");
                outName.set(args, outDexJar.getPath());
                Field jarOutput = argsClass.getField("jarOutput");
                jarOutput.setBoolean(args, true);
                Class<?> main = Class.forName(
                        "com.android.dx.command.dexer.Main", true, loader);
                Method run = main.getMethod("run", argsClass);
                Object result = run.invoke(null, args);
                if (!(result instanceof Integer) || (Integer) result != 0
                        || !outDexJar.isFile()) {
                    throw new IOException("dx translate failed, result=" + result);
                }
            }
        }

        private static ClassLoader loader() throws IOException {
            if (dxLoader != null) return dxLoader;
            synchronized (LOCK) {
                if (dxLoader != null) return dxLoader;
                Context app = App.get();
                File dir = new File(app.getCodeCacheDir(), "hk-dx");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                byte[] bytes;
                try (InputStream in = app.getAssets().open("hk-dx.dex")) {
                    bytes = readAll(in);
                }
                File dex = new File(dir, "hk-dx-" + md5(bytes) + ".dex");
                if (!dex.isFile()) {
                    try (FileOutputStream out = new FileOutputStream(dex)) {
                        out.write(bytes);
                    }
                    markReadOnly(dex);
                }
                dxLoader = new DexClassLoader(dex.getPath(), dir.getPath(), null,
                        HkAndroidClassLoader.class.getClassLoader());
                return dxLoader;
            }
        }

        private static byte[] readAll(InputStream in) throws IOException {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
