package com.fongmi.android.tv.player.danmaku;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 会话层扩口验证（本机回环手写 WS 服务端，无外部依赖）：
 * 建连带 headers、连上投递进房帧、二进制帧收发、心跳按间隔发帧，五件一次验全。
 */
public class LiveDanmakuSessionBinaryTest {

    private static final byte[] LOGIN_FRAME = new byte[]{1, 2, 3, 4};
    private static final byte[] HEARTBEAT_FRAME = new byte[]{9, 9};
    private static final byte[] SERVER_PACKET = new byte[]{5, 6, 7, 8, 9};
    private static final byte[] CLIENT_EXTRA = new byte[]{42, 42, 42};

    @Test
    public void binaryChannelCarriesHeadersOpenFramesMessagesAndHeartbeat() throws Exception {
        try (MiniWsServer server = new MiniWsServer()) {
            server.start();
            CountDownLatch opened = new CountDownLatch(1);
            BlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
            LiveDanmakuWebSocketSession session = new LiveDanmakuWebSocketSession(new LiveDanmakuWebSocketSession.Listener() {
                @Override
                public void onStateChanged(LiveDanmakuWebSocketSession.State state, long generation, String url, int code, String detail) {
                }

                @Override
                public void onMessage(long generation, String text) {
                }

                @Override
                public void onMessage(long generation, byte[] bytes) {
                    received.add(bytes);
                }

                @Override
                public void onOpen(long generation) {
                    opened.countDown();
                }
            });
            try {
                LiveDanmakuWebSocketSession.FrameHooks hooks = new LiveDanmakuWebSocketSession.FrameHooks() {
                    @Override
                    public List<byte[]> onOpenFrames() {
                        return List.of(LOGIN_FRAME);
                    }

                    @Override
                    public byte[] heartbeatFrame() {
                        return HEARTBEAT_FRAME;
                    }

                    @Override
                    public long heartbeatIntervalMs() {
                        return 150L;
                    }
                };
                session.connect(server.url(), Map.of("X-Test-Header", "yes"), "douyu:9999", hooks);

                assertTrue("server handshake", server.handshakeDone.await(10, TimeUnit.SECONDS));
                assertEquals("yes", server.headers.get("x-test-header"));
                assertTrue("client onOpen", opened.await(10, TimeUnit.SECONDS));

                byte[] first = server.clientFrames.poll(10, TimeUnit.SECONDS);
                assertArrayEquals("onOpen frame delivered", LOGIN_FRAME, first);

                server.sendBinary(SERVER_PACKET);
                byte[] message = received.poll(10, TimeUnit.SECONDS);
                assertArrayEquals("binary message delivered", SERVER_PACKET, message);

                assertTrue("session.send works", session.send(CLIENT_EXTRA));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                int heartbeats = 0;
                boolean extraSeen = false;
                while (System.nanoTime() < deadline && (heartbeats < 2 || !extraSeen)) {
                    byte[] frame = server.clientFrames.poll(2, TimeUnit.SECONDS);
                    assertNotNull("expected more client frames", frame);
                    if (Arrays.equals(frame, HEARTBEAT_FRAME)) heartbeats++;
                    if (Arrays.equals(frame, CLIENT_EXTRA)) extraSeen = true;
                }
                assertTrue("heartbeat repeats", heartbeats >= 2);
                assertTrue("extra frame delivered", extraSeen);
            } finally {
                session.release();
            }
        }
    }

    /** 最小 WS 服务端：只做握手＋收掩码帧＋发一帧二进制，够本测试用。 */
    private static final class MiniWsServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final Map<String, String> headers = new java.util.concurrent.ConcurrentHashMap<>();
        private final BlockingQueue<byte[]> clientFrames = new LinkedBlockingQueue<>();
        private final CountDownLatch handshakeDone = new CountDownLatch(1);
        private volatile Socket socket;
        private volatile OutputStream out;
        private Thread thread;

        MiniWsServer() throws IOException {
            serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        }

        String url() {
            return "ws://127.0.0.1:" + serverSocket.getLocalPort() + "/";
        }

        void start() {
            thread = new Thread(this::serve, "mini-ws-server");
            thread.setDaemon(true);
            thread.start();
        }

        private void serve() {
            try {
                socket = serverSocket.accept();
                InputStream in = new BufferedInputStream(socket.getInputStream());
                out = socket.getOutputStream();
                String request = readHeaders(in);
                String key = null;
                for (String line : request.split("\r\n")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
                    if (line.toLowerCase().startsWith("sec-websocket-key:")) key = line.substring(colon + 1).trim();
                }
                String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8)));
                out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                handshakeDone.countDown();
                readFrames(in);
            } catch (Throwable ignored) {
                // 连接关停时退出
            }
        }

        private String readHeaders(InputStream in) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int matched = 0;
            byte[] end = {'\r', '\n', '\r', '\n'};
            int b;
            while ((b = in.read()) >= 0) {
                buffer.write(b);
                matched = b == end[matched] ? matched + 1 : (b == end[0] ? 1 : 0);
                if (matched == 4) break;
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }

        private void readFrames(InputStream in) throws IOException {
            while (true) {
                int first = in.read();
                if (first < 0) return;
                int second = in.read();
                if (second < 0) return;
                int opcode = first & 0x0F;
                boolean masked = (second & 0x80) != 0;
                long length = second & 0x7F;
                if (length == 126) length = (in.read() << 8) | in.read();
                else if (length == 127) {
                    length = 0;
                    for (int i = 0; i < 8; i++) length = (length << 8) | in.read();
                }
                byte[] mask = masked ? readN(in, 4) : new byte[4];
                byte[] payload = readN(in, (int) length);
                if (masked) for (int i = 0; i < payload.length; i++) payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                if (opcode == 8) return;
                if (opcode == 1 || opcode == 2) clientFrames.add(payload);
            }
        }

        private byte[] readN(InputStream in, int count) throws IOException {
            byte[] data = new byte[count];
            int read = 0;
            while (read < count) {
                int n = in.read(data, read, count - read);
                if (n < 0) throw new IOException("eof");
                read += n;
            }
            return data;
        }

        synchronized void sendBinary(byte[] payload) throws IOException {
            OutputStream stream = out;
            if (stream == null) throw new IOException("not connected");
            stream.write(0x82);
            if (payload.length < 126) {
                stream.write(payload.length);
            } else {
                stream.write(126);
                stream.write((payload.length >>> 8) & 0xFF);
                stream.write(payload.length & 0xFF);
            }
            stream.write(payload);
            stream.flush();
        }

        @Override
        public void close() throws IOException {
            if (socket != null) socket.close();
            serverSocket.close();
        }
    }
}
