package com.fongmi.android.tv.api.loader;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class PanProxyCompatTest {

    private final ConcurrentLinkedQueue<FakeTransfer> transfers = new ConcurrentLinkedQueue<>();
    private ExecutorService shared;
    private FakeInit init;
    private PanProxyCompat compat;

    @Before
    public void setUp() throws Exception {
        shared = Executors.newFixedThreadPool(2);
        init = new FakeInit(shared);
        compat = PanProxyCompat.install(init, FakeWriter.class, FakeTransfer.class, message -> {});
    }

    @After
    public void tearDown() throws Exception {
        compat.close();
        for (FakeTransfer transfer : transfers) {
            transfer.c.shutdownNow();
            if (transfer.input != null) transfer.input.close();
            assertTrue(transfer.c.awaitTermination(3, TimeUnit.SECONDS));
        }
        shared.shutdownNow();
        assertTrue(shared.awaitTermination(3, TimeUnit.SECONDS));
    }

    @Test
    public void eofPreservesBytesAndHeadersAndStopsDownloadPool() throws Exception {
        FakeTransfer transfer = transfer();
        byte[] expected = new byte[32768];
        for (int i = 0; i < expected.length; i++) expected[i] = (byte) i;
        Object[] original = new Object[1];
        Object[] response = compat.invoke(() -> {
            Object[] value = open(transfer, out -> out.write(expected));
            original[0] = value[3];
            return value;
        });
        assertEquals(206, response[0]);
        assertEquals("application/oct-stream", response[1]);
        assertSame(original[0], response[3]);
        assertArrayEquals(expected, drain((InputStream) response[2]));
        stopped(transfer);
        assertFalse(shared.isShutdown());
    }

    @Test
    public void writerCompletionReleasesPoolBeforeConsumerCloses() throws Exception {
        FakeTransfer transfer = transfer();
        Object[] response = compat.invoke(() -> open(transfer, out -> out.write(new byte[]{7, 8, 9})));
        stopped(transfer);
        assertArrayEquals(new byte[]{7, 8, 9}, drain((InputStream) response[2]));
    }

    @Test
    public void cancelBeforeFirstChunkStopsWorkersAndWaitingWriter() throws Exception {
        FakeTransfer transfer = transfer();
        CountDownLatch entered = new CountDownLatch(1);
        Object[] response = compat.invoke(() -> open(transfer, out -> {
            entered.countDown();
            awaitInterrupt();
        }));
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        ((InputStream) response[2]).close();
        stopped(transfer);
        assertFalse(init.a.submit(() -> Thread.currentThread().isInterrupted()).get(3, TimeUnit.SECONDS));
    }

    @Test
    public void cancelQueuedWriterReleasesPoolWithoutWaitingForSharedExecutor() throws Exception {
        CountDownLatch occupied = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        for (int i = 0; i < 2; i++) shared.execute(() -> {
            occupied.countDown();
            await(release);
        });
        assertTrue(occupied.await(3, TimeUnit.SECONDS));
        AtomicBoolean ran = new AtomicBoolean();
        FakeTransfer transfer = transfer();
        Object[] response = compat.invoke(() -> open(transfer, out -> ran.set(true)));
        ((InputStream) response[2]).close();
        stopped(transfer);
        release.countDown();
        shared.submit(() -> {}).get(3, TimeUnit.SECONDS);
        assertFalse(ran.get());
    }

    @Test
    public void proxyFailureAfterSchedulingReleasesItsTransfer() throws Exception {
        FakeTransfer transfer = transfer();
        IOException failure = new IOException("proxy failed");
        try {
            compat.invoke(() -> {
                open(transfer, out -> awaitInterrupt());
                throw failure;
            });
            fail("Expected proxy failure");
        } catch (IOException error) {
            assertSame(failure, error);
        }
        stopped(transfer);
    }

    @Test
    public void invalidResponseAndRejectedWriterReleaseWorkers() throws Exception {
        FakeTransfer invalid = transfer();
        assertNull(compat.invoke(() -> {
            open(invalid, out -> awaitInterrupt());
            return null;
        }));
        stopped(invalid);
        shared.shutdown();
        FakeTransfer rejected = transfer();
        try {
            compat.invoke(() -> open(rejected, out -> {}));
            fail("Expected writer rejection");
        } catch (RejectedExecutionException expected) {
            stopped(rejected);
        }
    }

    @Test
    public void readAndCloseErrorsStillReleaseAndPreserveOriginalException() throws Exception {
        FakeTransfer transfer = transfer();
        IOException failure = new IOException("read failed");
        AtomicInteger closes = new AtomicInteger();
        Object[] response = compat.invoke(() -> {
            Object[] value = open(transfer, out -> awaitInterrupt());
            value[2] = new InputStream() {
                @Override public int read() throws IOException { throw failure; }
                @Override public void close() throws IOException {
                    closes.incrementAndGet();
                    throw new IOException("close failed");
                }
            };
            return value;
        });
        InputStream body = (InputStream) response[2];
        try {
            body.read(new byte[16]);
            fail("Expected read failure");
        } catch (IOException error) {
            assertSame(failure, error);
            assertEquals("close failed", error.getSuppressed()[0].getMessage());
        }
        body.close();
        assertEquals(1, closes.get());
        stopped(transfer);
    }

    @Test
    public void concurrentRequestsDoNotShareCancellationAndRepeatedCloseIsSafe() throws Exception {
        ExecutorService requests = Executors.newFixedThreadPool(4);
        try {
            List<Future<Object[]>> responses = new ArrayList<>();
            List<FakeTransfer> owners = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                FakeTransfer transfer = transfer();
                owners.add(transfer);
                responses.add(requests.submit(() -> compat.invoke(() -> open(transfer, out -> awaitInterrupt()))));
            }
            for (int i = 0; i < responses.size(); i++) {
                InputStream input = (InputStream) responses.get(i).get(3, TimeUnit.SECONDS)[2];
                Future<?> first = requests.submit(() -> close(input));
                Future<?> second = requests.submit(() -> close(input));
                first.get(3, TimeUnit.SECONDS);
                second.get(3, TimeUnit.SECONDS);
                stopped(owners.get(i));
                if (i + 1 < owners.size()) assertFalse(owners.get(i + 1).c.isShutdown());
            }
        } finally {
            requests.shutdownNow();
            assertTrue(requests.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    public void loaderReleaseClosesActiveTransfersButLeavesSharedExecutorUsable() throws Exception {
        FakeTransfer first = transfer();
        FakeTransfer second = transfer();
        compat.invoke(() -> open(first, out -> awaitInterrupt()));
        compat.invoke(() -> open(second, out -> awaitInterrupt()));
        compat.close();
        compat.close();
        stopped(first);
        stopped(second);
        assertEquals(42, (int) init.a.submit(() -> 42).get(3, TimeUnit.SECONDS));
    }

    @Test
    public void unrelatedTasksRemainOnOriginalExecutor() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Object[] expected = {200, "text/plain", new ByteArrayInputStream(new byte[0])};
        assertSame(expected, compat.invoke(() -> {
            init.a.execute(calls::incrementAndGet);
            return expected;
        }));
        init.a.submit(() -> {}).get(3, TimeUnit.SECONDS);
        // Different shared-pool workers may run independently.
        shared.shutdown();
        assertTrue(shared.awaitTermination(3, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
    }

    @Test
    public void sameSizeUnknownJarIsNotInspectedOrModified() throws Exception {
        File file = File.createTempFile("pan-unknown", ".jar");
        try {
            try (RandomAccessFile contents = new RandomAccessFile(file, "rw")) {
                contents.setLength(2044180);
            }
            ClassLoader failIfUsed = new ClassLoader() {
                @Override public Class<?> loadClass(String name) { throw new AssertionError(name); }
            };
            assertNull(PanProxyCompat.install(file, failIfUsed, message -> {}));
        } finally {
            assertTrue(file.delete());
        }
    }

    private FakeTransfer transfer() throws Exception {
        FakeTransfer transfer = new FakeTransfer();
        transfers.add(transfer);
        assertTrue(transfer.started.await(3, TimeUnit.SECONDS));
        return transfer;
    }

    private Object[] open(FakeTransfer transfer, WriteAction action) throws IOException {
        transfer.input = new PipedInputStream(1024);
        PipedOutputStream output = new PipedOutputStream(transfer.input);
        init.a.execute(new FakeWriter(transfer, output, action));
        return new Object[]{206, "application/oct-stream", transfer.input, Map.of("Content-Range", "bytes 0-32767/32768")};
    }

    private static void stopped(FakeTransfer transfer) throws InterruptedException {
        assertTrue("Download pool did not terminate", transfer.c.awaitTermination(3, TimeUnit.SECONDS));
        assertTrue(transfer.c.isShutdown());
        assertFalse(transfer.m);
        assertTrue(transfer.a.isEmpty());
    }

    private static byte[] drain(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int count; (count = input.read(buffer)) != -1; ) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static void close(InputStream input) {
        try { input.close(); } catch (IOException error) { throw new AssertionError(error); }
    }

    private static void awaitInterrupt() {
        await(new CountDownLatch(1));
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
    }

    private interface WriteAction { void run(PipedOutputStream output) throws IOException; }

    private static final class FakeInit {
        private final ExecutorService a;
        FakeInit(ExecutorService executor) { a = executor; }
    }

    // These fixtures reproduce the verified jar's ownership, including its missing shutdown.
    private static final class FakeTransfer {
        private final LinkedBlockingQueue<byte[]> a = new LinkedBlockingQueue<>();
        private final ExecutorService c = Executors.newFixedThreadPool(2);
        private boolean m = true;
        private final CountDownLatch started = new CountDownLatch(2);
        private PipedInputStream input;

        FakeTransfer() {
            a.add(new byte[32768]);
            for (int i = 0; i < 2; i++) c.execute(() -> {
                started.countDown();
                awaitInterrupt();
            });
        }
    }

    private static final class FakeWriter implements Runnable {
        private final int a = 1;
        private final Object b;
        private final Object c;
        private final WriteAction action;

        FakeWriter(FakeTransfer owner, PipedOutputStream output, WriteAction action) {
            b = owner;
            c = output;
            this.action = action;
        }

        @Override
        public void run() {
            try {
                action.run((PipedOutputStream) c);
            } catch (IOException ignored) {
                // The real jar also catches writer errors without releasing the download pool.
            } finally {
                ((FakeTransfer) b).m = false;
                try { ((PipedOutputStream) c).close(); } catch (IOException ignored) {}
            }
        }
    }
}
