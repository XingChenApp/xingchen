package com.fongmi.android.tv.api.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Restores the missing per-response download cleanup in one verified Pan jar. */
final class PanProxyCompat implements AutoCloseable {

    static final String JAR_SHA256 = "143c93e91cd88bebbad57e8a1ffe23f4acb1904bc9ca3189819e522d1b33f655";

    private final ThreadLocal<Scope> current = new ThreadLocal<>();
    private final Set<Transfer> active = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Class<?> writerClass;
    private final Class<?> transferClass;
    private final Field writerKind;
    private final Field writerOwner;
    private final Field writerOutput;
    private final Field downloadExecutor;
    private final Field downloading;
    private final Field chunks;
    private final Consumer<String> logger;

    static PanProxyCompat install(File jar, ClassLoader loader, Consumer<String> logger) throws Exception {
        if (!matches(jar)) return null;
        Class<?> init = loader.loadClass("com.github.catvod.spider.Init");
        return install(init.getMethod("get").invoke(null),
                loader.loadClass("com.github.catvod.spider.merge.b.o"),
                loader.loadClass("com.github.catvod.spider.merge.m.f"), logger);
    }

    static boolean matches(File jar) throws Exception {
        if (jar.length() != 2044180) return false;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(jar)) {
            byte[] buffer = new byte[16384];
            for (int count; (count = in.read(buffer)) != -1; ) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(Character.forDigit((value & 0xff) >>> 4, 16));
            hex.append(Character.forDigit(value & 0xf, 16));
        }
        return JAR_SHA256.contentEquals(hex);
    }

    // Package-visible so the lifecycle contract can be tested without a third-party binary.
    static PanProxyCompat install(Object init, Class<?> writerClass, Class<?> transferClass,
                                  Consumer<String> logger) throws ReflectiveOperationException {
        PanProxyCompat compat = new PanProxyCompat(writerClass, transferClass, logger);
        Field field = field(init.getClass(), "a", ExecutorService.class);
        ExecutorService delegate = (ExecutorService) field.get(init);
        if (delegate == null) throw new IllegalStateException("Missing Pan shared executor");
        field.set(init, compat.new WriterExecutor(delegate));
        return compat;
    }

    private PanProxyCompat(Class<?> writerClass, Class<?> transferClass, Consumer<String> logger)
            throws ReflectiveOperationException {
        this.writerClass = writerClass;
        this.transferClass = transferClass;
        this.logger = logger;
        writerKind = field(writerClass, "a", int.class);
        writerOwner = field(writerClass, "b", Object.class);
        writerOutput = field(writerClass, "c", Object.class);
        downloadExecutor = field(transferClass, "c", ExecutorService.class);
        downloading = field(transferClass, "m", boolean.class);
        chunks = field(transferClass, "a", BlockingQueue.class);
    }

    private static Field field(Class<?> type, String name, Class<?> expected) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        if (!expected.isAssignableFrom(field.getType())) throw new NoSuchFieldException(type.getName() + "." + name);
        field.setAccessible(true);
        return field;
    }

    Object[] invoke(Callable<Object[]> call) throws Exception {
        if (closed.get()) throw new IOException("Pan jar released");
        Scope previous = current.get();
        Scope scope = new Scope();
        current.set(scope);
        try {
            Object[] response = call.call();
            if (!scope.transfers.isEmpty()) {
                if (response != null && response.length >= 3 && response[2] instanceof InputStream input) {
                    response[2] = new ResourceInputStream(input, scope);
                } else {
                    scope.release("invalid-response");
                }
            }
            return response;
        } catch (Exception | Error error) {
            scope.release("proxy-failed");
            throw error;
        } finally {
            if (previous == null) current.remove();
            else current.set(previous);
        }
    }

    @Override
    public void close() {
        closed.set(true);
        for (Transfer transfer : active) transfer.release("jar-released");
    }

    private void log(String message) {
        try {
            logger.accept(message);
        } catch (RuntimeException ignored) {
            // A diagnostic sink must not interrupt resource cleanup.
        }
    }

    private final class Scope {
        final List<Transfer> transfers = new ArrayList<>();

        void release(String reason) {
            for (Transfer transfer : transfers) transfer.release(reason);
        }
    }

    private final class WriterExecutor extends AbstractExecutorService {
        private final ExecutorService delegate;

        WriterExecutor(ExecutorService delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable command) {
            Scope scope = current.get();
            if (scope == null || command.getClass() != writerClass) {
                delegate.execute(command);
                return;
            }
            Transfer transfer;
            try {
                Object owner = writerOwner.get(command);
                Object output = writerOutput.get(command);
                if (writerKind.getInt(command) != 1 || !transferClass.isInstance(owner)
                        || !(output instanceof PipedOutputStream pipe)) {
                    delegate.execute(command);
                    return;
                }
                transfer = new Transfer(owner, pipe);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Pan writer binding failed", error);
            }
            scope.transfers.add(transfer);
            active.add(transfer);
            if (closed.get()) transfer.release("jar-released");
            try {
                delegate.execute(() -> transfer.run(command));
            } catch (RuntimeException | Error error) {
                transfer.release("writer-rejected");
                throw error;
            }
        }

        @Override public void shutdown() { delegate.shutdown(); }
        @Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
        @Override public boolean isShutdown() { return delegate.isShutdown(); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
    }

    private final class Transfer {
        private final Object owner;
        private final PipedOutputStream output;
        private final ExecutorService executor;
        private final BlockingQueue<?> queue;
        private final AtomicBoolean released = new AtomicBoolean();
        private Thread writer;
        private boolean interruptedWriter;

        Transfer(Object owner, PipedOutputStream output) throws IllegalAccessException {
            this.owner = owner;
            this.output = output;
            executor = (ExecutorService) downloadExecutor.get(owner);
            queue = (BlockingQueue<?>) chunks.get(owner);
        }

        void run(Runnable command) {
            synchronized (this) {
                if (released.get()) return;
                writer = Thread.currentThread();
            }
            try {
                command.run();
            } finally {
                synchronized (this) {
                    writer = null;
                    // Do not carry our cancellation interrupt into an unrelated shared-pool task.
                    if (interruptedWriter) Thread.interrupted();
                }
                release("writer-finished");
            }
        }

        void release(String reason) {
            if (!released.compareAndSet(false, true)) return;
            try {
                downloading.setBoolean(owner, false);
            } catch (IllegalAccessException error) {
                log("stop flag failed: " + error.getClass().getSimpleName());
            }
            try {
                executor.shutdownNow();
            } finally {
                try {
                    output.close();
                } catch (IOException ignored) {
                    // The pipe may already have been closed by the original writer.
                }
                queue.clear();
                synchronized (this) {
                    if (writer != null && writer != Thread.currentThread()) {
                        interruptedWriter = true;
                        writer.interrupt();
                    }
                }
                active.remove(this);
                log("release reason=" + reason + " active=" + active.size());
            }
        }
    }

    private static final class ResourceInputStream extends FilterInputStream {
        private final Scope scope;
        private final AtomicBoolean closed = new AtomicBoolean();

        ResourceInputStream(InputStream input, Scope scope) {
            super(input);
            this.scope = scope;
        }

        @Override
        public int read() throws IOException {
            try {
                int value = in.read();
                if (value == -1) finish("eof");
                return value;
            } catch (IOException | RuntimeException error) {
                failed(error);
                throw error;
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            try {
                int count = in.read(buffer, offset, length);
                if (count == -1) finish("eof");
                return count;
            } catch (IOException | RuntimeException error) {
                failed(error);
                throw error;
            }
        }

        private void failed(Exception error) {
            try {
                finish("read-failed");
            } catch (IOException closeError) {
                error.addSuppressed(closeError);
            }
        }

        @Override
        public void close() throws IOException {
            finish("stream-closed");
        }

        private void finish(String reason) throws IOException {
            if (!closed.compareAndSet(false, true)) return;
            try {
                in.close();
            } finally {
                scope.release(reason);
            }
        }
    }
}
