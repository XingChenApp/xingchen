package com.fongmi.android.tv.api.loader;

import android.os.Looper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import dalvik.system.DexClassLoader;

/**
 * Standalone ART regression harness for the actual downloaded jar; no cloud credentials or UI.
 * Run with app_process, putting this harness dex and a WebHTV APK on CLASSPATH, then pass
 * the read-only known jar and a writable dex-cache directory. See AV-FREEZE-01 for results.
 */
public final class PanProxyCompatDeviceTest {

    public static void main(String[] args) {
        try {
            if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
            run(new File(args[0]), new File(args[1]));
            System.out.println("PAN_COMPAT_ART_PASS");
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(File jar, File cache) throws Exception {
        check(PanProxyCompat.matches(jar), "Fixture must be the exact observed jar");
        check(cache.isDirectory() || cache.mkdirs(), "Missing dex cache");
        ClassLoader parent = PanProxyCompatDeviceTest.class.getClassLoader();
        ClassLoader loader = new DexClassLoader(jar.getAbsolutePath(), cache.getAbsolutePath(), null, parent);
        Class<?> initClass = loader.loadClass("com.github.catvod.spider.Init");
        Object init = initClass.getMethod("get").invoke(null);
        ExecutorService shared = (ExecutorService) field(initClass, "a").get(init);
        // Use the host's ordinary client so this isolated test never needs App/Spider initialization.
        Class<?> network = loader.loadClass("com.github.catvod.spider.merge.k.b");
        Class<?> holder = loader.loadClass("com.github.catvod.spider.merge.k.b$a");
        Object clientOwner = field(holder, "a").get(null);
        Object client = parent.loadClass("okhttp3.OkHttpClient").getConstructor().newInstance();
        field(network, "a").set(clientOwner, client);
        Class<?> transfer = loader.loadClass("com.github.catvod.spider.merge.m.f");
        PanProxyCompat compat = null;
        try (RangeServer server = new RangeServer()) {
            Object original = owner(transfer, server.url(), 0, server.data.length - 1);
            ThreadPoolExecutor baseline = pool(transfer, original);
            try {
                Object[] response = response(transfer, original);
                try (InputStream input = (InputStream) response[2]) {
                    check(Arrays.equals(server.data, drain(input)), "Baseline response bytes changed");
                }
                check(!baseline.isShutdown(), "Original leak was not reproduced");
                check(baseline.getPoolSize() == 4, "Expected four retained download workers");
                System.out.println("PAN_COMPAT_BASELINE retainedWorkers=" + baseline.getPoolSize());
            } finally {
                baseline.shutdownNow();
                check(baseline.awaitTermination(5, TimeUnit.SECONDS), "Baseline cleanup failed");
            }

            compat = PanProxyCompat.install(jar, loader, System.out::println);
            check(compat != null, "Known jar hook did not install");
            for (int attempt = 0; attempt < 16; attempt++) {
                int start = (attempt % 4) * 4096;
                int end = start + 131071;
                Object download = owner(transfer, server.url(), start, end);
                ThreadPoolExecutor executor = pool(transfer, download);
                Object[] response = compat.invoke(() -> response(transfer, download));
                check(response[0].equals(206), "Range status changed");
                check(response[3] == field(transfer, "b").get(download), "Response headers replaced");
                try (InputStream input = (InputStream) response[2]) {
                    if ((attempt & 1) == 0) {
                        check(Arrays.equals(Arrays.copyOfRange(server.data, start, end + 1), drain(input)),
                                "Range response bytes changed");
                    } else {
                        check(input.read() == (server.data[start] & 0xff), "Cancelled response offset changed");
                    }
                }
                check(executor.awaitTermination(5, TimeUnit.SECONDS), "Workers survived request " + attempt);
                check(executor.getPoolSize() == 0, "Retained workers after request " + attempt);
                check(!field(transfer, "m").getBoolean(download), "Download flag still set");
            }
            System.out.println("PAN_COMPAT_FIXED requests=16 retainedWorkers=0 bytesAndRanges=verified");
        } finally {
            if (compat != null) compat.close();
            shared.shutdownNow();
            check(shared.awaitTermination(5, TimeUnit.SECONDS), "Shared writer did not exit");
            client.getClass().getMethod("connectionPool").invoke(client).getClass().getMethod("evictAll")
                    .invoke(client.getClass().getMethod("connectionPool").invoke(client));
        }
    }

    private static Object owner(Class<?> type, String url, int start, int end) throws Exception {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("Range", "bytes=" + start + "-" + end);
        return type.getConstructor(String.class, Map.class, int.class, int.class)
                .newInstance(url, headers, 4, 8192);
    }

    private static Object[] response(Class<?> type, Object owner) throws Exception {
        return (Object[]) type.getMethod("e").invoke(owner);
    }

    private static ThreadPoolExecutor pool(Class<?> type, Object owner) throws Exception {
        return (ThreadPoolExecutor) field(type, "c").get(owner);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static byte[] drain(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int count; (count = input.read(buffer)) != -1; ) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RangeServer implements AutoCloseable {
        final byte[] data = new byte[524288];
        final ServerSocket listener;
        final ExecutorService workers = Executors.newFixedThreadPool(8);
        final Set<Socket> clients = ConcurrentHashMap.newKeySet();
        final Thread accept;

        RangeServer() throws IOException {
            for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 31 + i / 257);
            listener = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
            accept = new Thread(() -> {
                try {
                    while (!listener.isClosed()) {
                        Socket socket = listener.accept();
                        clients.add(socket);
                        workers.execute(() -> serve(socket));
                    }
                } catch (IOException expectedOnClose) {
                    if (!listener.isClosed()) throw new AssertionError(expectedOnClose);
                }
            }, "pan-test-http");
            accept.start();
        }

        String url() { return "http://127.0.0.1:" + listener.getLocalPort() + "/media"; }

        void serve(Socket socket) {
            try (Socket connection = socket) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
                check(reader.readLine().startsWith("GET "), "Unexpected proxy request method");
                int start = 0;
                int end = data.length - 1;
                for (String line; (line = reader.readLine()) != null && !line.isEmpty(); ) {
                    if (line.regionMatches(true, 0, "Range: bytes=", 0, 13)) {
                        String[] range = line.substring(13).trim().split("-", -1);
                        start = Integer.parseInt(range[0]);
                        if (!range[1].isEmpty()) end = Math.min(end, Integer.parseInt(range[1]));
                    }
                }
                check(start >= 0 && start <= end && end < data.length, "Invalid test range");
                OutputStream output = connection.getOutputStream();
                String headers = "HTTP/1.1 206 Partial Content\r\nContent-Type: application/octet-stream\r\n"
                        + "Content-Length: " + (end - start + 1) + "\r\nContent-Range: bytes "
                        + start + "-" + end + "/" + data.length + "\r\nConnection: close\r\n\r\n";
                output.write(headers.getBytes(StandardCharsets.US_ASCII));
                output.write(data, start, end - start + 1);
                output.flush();
            } catch (IOException expectedOnCancellation) {
                // The real jar deliberately closes its header probe and cancelled range requests.
            } finally {
                clients.remove(socket);
            }
        }

        @Override
        public void close() throws Exception {
            listener.close();
            for (Socket socket : clients) socket.close();
            workers.shutdownNow();
            accept.join(5000);
            check(workers.awaitTermination(5, TimeUnit.SECONDS), "Test HTTP workers did not exit");
        }
    }
}
