package com.github.catvod.utils;

import com.orhanobut.logger.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class Shell {

    private static final String TAG = Shell.class.getSimpleName();
    private static final long TIMEOUT_SECONDS = 30;

    public static String exec(String command) {
        try {
            StringBuilder sb = new StringBuilder();
            Process p = Runtime.getRuntime().exec(command);
            // stdout/stderr 各起一条守护线程排空：旧写法只读 stdout，
            // 子进程写满 stderr 管道即死锁；两个 reader 都走
            // try-with-resources，读完即关。
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        synchronized (sb) {
                            sb.append(line).append("\n");
                        }
                    }
                } catch (Exception ignored) {
                }
            });
            Thread drainer = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                    while (br.readLine() != null) {
                    }
                } catch (Exception ignored) {
                }
            });
            reader.setDaemon(true);
            drainer.setDaemon(true);
            reader.start();
            drainer.start();
            // 等待有界：超时强杀，不许 waitFor 无限期挂住调用线程。
            boolean finished = p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) p.destroyForcibly();
            reader.join(TimeUnit.SECONDS.toMillis(5));
            Logger.t(TAG).d("Shell command '%s' with exit code '%s'", command, finished ? p.exitValue() : "timeout");
            synchronized (sb) {
                return Util.substring(sb.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
