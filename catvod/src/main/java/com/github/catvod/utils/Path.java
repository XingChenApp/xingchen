package com.github.catvod.utils;

import android.net.Uri;
import android.os.Environment;
import android.system.ErrnoException;
import android.system.Os;

import com.github.catvod.Init;
import com.github.catvod.Runtime;
import com.orhanobut.logger.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Path {

    private static final String TAG = Path.class.getSimpleName();
    private static final int BUFFER_SIZE = 16 * 1024;

    private static File mkdir(File file) {
        if (file == null || file.exists()) return file;
        if (file.mkdirs()) Logger.t(TAG).d("Created dir:" + file);
        return file;
    }

    public static boolean exists(String path) {
        return new File(path.replace("file://", "")).exists();
    }

    public static boolean exists(File file) {
        return file != null && file.exists() && file.length() > 0;
    }

    public static File root() {
        return Environment.getExternalStorageDirectory();
    }

    public static File cache() {
        return Init.context().getCacheDir();
    }

    public static File files() {
        return Init.context().getFilesDir();
    }

    public static String rootPath() {
        return root().getAbsolutePath();
    }

    public static File tv() {
        return mkdir(new File(root() + File.separator + "TV"));
    }

    public static File so() {
        return mkdir(new File(files() + File.separator + "so"));
    }

    public static File js() {
        return mkdir(new File(cache() + File.separator + "js"));
    }

    public static File py() {
        return mkdir(new File(cache() + File.separator + "py"));
    }

    public static File jar() {
        return mkdir(new File(cache() + File.separator + "jar"));
    }

    public static File exo() {
        return mkdir(new File(cache() + File.separator + "exo"));
    }

    public static File exoCache() {
        return exo();
    }

    public static File epg() {
        return mkdir(new File(cache() + File.separator + "epg"));
    }

    public static File jpa() {
        return mkdir(new File(cache() + File.separator + "jpa"));
    }

    public static File thunder() {
        return mkdir(new File(cache() + File.separator + "thunder"));
    }

    public static File root(String name) {
        return new File(root(), name);
    }

    public static File root(String child, String name) {
        return new File(mkdir(new File(root(), child)), name);
    }

    public static File cache(String name) {
        return new File(cache(), name);
    }

    public static File files(String name) {
        return new File(files(), name);
    }

    public static File epg(String name) {
        return new File(epg(), name);
    }

    public static File js(String name) {
        return new File(js(), name);
    }

    public static File py(String name) {
        return new File(py(), name);
    }

    public static File jar(String name) {
        return new File(jar(), Util.md5(name).concat(".jar"));
    }

    public static File thunder(String name) {
        return mkdir(new File(thunder(), name));
    }

    public static File local(String path) {
        path = path.replace("file:/", "");
        File file = new File(root(), path);
        return file.exists() ? file : new File(path);
    }

    public static String read(File file) {
        try {
            return new String(readToByte(file), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public static String read(InputStream is) {
        try {
            return new String(readToByte(is), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    // 上游原文（RuntimeModule.readAsset 与 catvod Download 用）：抛错版读流。
    public static String readOrThrow(InputStream is) throws IOException {
        return new String(readToByte(is), StandardCharsets.UTF_8);
    }

    public static InputStream open(Uri uri, String error) throws IOException {
        InputStream input = Runtime.context().getContentResolver().openInputStream(uri);
        if (input == null) throw new IOException(error);
        return input;
    }

    // 上游原文（catvod Download.requireSpace 用）。
    public static long available(File file) {
        File directory = file == null ? null : file.getAbsoluteFile();
        while (directory != null && !directory.isDirectory()) directory = directory.getParentFile();
        return directory == null ? 0 : directory.getUsableSpace();
    }

    // 上游原文（catvod Download.requireSpace 用）。
    public static void requireSpace(File file, long bytes) throws IOException {
        if (bytes > 0 && available(file) < bytes) throw new IOException("Insufficient storage space");
    }

    // 上游原文（catvod Download.publish 用）。本树 create() 自有 777 逻辑不动，
    // 此处只补 Download 调用的独立 chmod 口径。
    public static File chmod(File file) {
        try {
            Os.chmod(file.getAbsolutePath(), 0777);
        } catch (ErrnoException e) {
            e.printStackTrace();
        }
        return file;
    }

    public static byte[] readToByte(File file) {
        try (FileInputStream is = new FileInputStream(file)) {
            return readToByte(is);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static byte[] readToByte(InputStream is) throws IOException {
        try (InputStream input = is; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            int read;
            byte[] buffer = new byte[16384];
            while ((read = input.read(buffer)) != -1) bos.write(buffer, 0, read);
            return bos.toByteArray();
        }
    }

    public static File write(File file, InputStream is) {
        try (InputStream input = is; FileOutputStream output = new FileOutputStream(create(file))) {
            int read;
            byte[] buffer = new byte[16384];
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return file;
        } catch (IOException e) {
            return file;
        }
    }

    public static void writeAtomically(File file, InputStream is) throws IOException {
        try (InputStream input = is) {
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent == null || (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())) throw new IOException("Unable to create " + parent);
            File temp = File.createTempFile("write-", ".tmp", parent);
            try {
                writeSynced(temp, input);
                move(temp, file);
            } finally {
                clear(temp);
            }
        }
    }

    private static void writeSynced(File file, InputStream input) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            transfer(input, output);
            output.flush();
            output.getFD().sync();
        }
    }

    public static void writeAtomically(File file, File source) throws IOException {
        if (source.getCanonicalFile().equals(file.getCanonicalFile())) return;
        writeAtomically(file, new FileInputStream(source));
    }

    public static void writeAtomically(File file, Uri source) throws IOException {
        writeAtomically(file, open(source, "Unable to open " + source));
    }

    public static void writeAtomically(File file, byte[] data) throws IOException {
        writeAtomically(file, new ByteArrayInputStream(data));
    }

    public static File write(File file, byte[] data) {
        try (FileOutputStream fos = new FileOutputStream(create(file))) {
            fos.write(data);
            fos.flush();
            return file;
        } catch (IOException e) {
            return file;
        }
    }

    public static void move(File in, File out) throws IOException {
        if (in.renameTo(out)) return;
        copy(in, out);
        clear(in);
    }

    public static void copy(File in, File out) {
        try {
            copy(new FileInputStream(in), out);
        } catch (IOException ignored) {
        }
    }

    public static void copy(InputStream in, File out) {
        try (InputStream input = in; FileOutputStream output = new FileOutputStream(create(out))) {
            int read;
            byte[] buffer = new byte[16384];
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        } catch (IOException ignored) {
        }
    }

    private static void transfer(InputStream input, OutputStream output) throws IOException {
        int read;
        byte[] buffer = new byte[BUFFER_SIZE];
        while ((read = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Canceled");
            output.write(buffer, 0, read);
        }
    }

    public static void sort(File[] files) {
        Arrays.sort(files, (o1, o2) -> {
            if (o1.isDirectory() && o2.isFile()) return -1;
            if (o1.isFile() && o2.isDirectory()) return 1;
            return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
        });
    }

    public static List<File> list(File dir) {
        File[] files = dir.listFiles();
        if (files != null) sort(files);
        return files == null ? new ArrayList<>() : Arrays.asList(files);
    }

    public static void clear(File dir) {
        if (dir == null) return;
        if (dir.isDirectory()) for (File file : list(dir)) clear(file);
        if (dir.delete()) Logger.t(TAG).d("Deleted:" + dir);
    }

    public static File create(File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null) mkdir(parent);
            if (file.exists()) clear(file);
            if (file.createNewFile()) Logger.t(TAG).d("Create:" + file);
            file.setReadable(true);
            file.setWritable(true);
            file.setExecutable(true);
            Shell.exec("chmod 777 " + file);
            return file;
        } catch (IOException e) {
            return file;
        }
    }
}
