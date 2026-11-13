package com.fongmi.android.tv.player.danmaku.live.bili;

import com.fongmi.android.tv.player.danmaku.LiveDanmakuMessage;
import com.fongmi.android.tv.player.danmaku.live.PlatformDanmakuProvider;
import com.fongmi.android.tv.player.danmaku.live.PreparedTarget;
import com.fongmi.android.tv.player.danmaku.live.RoomRef;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * BiliProvider 对拍：预备步用假 Api 验短号换真实号＋在播校验＋token/host 产物；
 * 解码用真房实录样本（B站房间 6650029，2026-10-24 本机直连录制，录制帧与独立参照解码全量 28 条对齐，
 * 此处嵌前 6 条所在的前缀帧）与合成向量（红弹幕/SC/人气）逐条比对。
 */
public class BiliProviderTest {

    private static final RoomRef SHORT_ROOM = new RoomRef(RoomRef.Platform.BILI, "77");

    private static final String OP3_HEX = "000000140010000100000003000000010012d687";
    private static final String BUNDLE_HEX = "0000016d001000020000000500000001789c636060d8cd20c00002ac40cc58ad949c9ba264a5e4e2e8e71b1aef1becaea4a394999796af64151d6da063a86364aa6368666e68686601e4991b40810e082a252625a728819979a539393ad5b5101aca89d5517abe6bd18bce4d4ff7ec7cba73eab3addd2fd64f55d28936343030d45182709f4f59f1ac63fbf3299b40c628298110d0b8589de85890690648e619c4d6029dbb0dafd3ad4cac0cac8cc0d0c0ca00d323483e3084fb2015bf0f66ee85fb00e27623a020d8d54f76ce24d2d5f3b0b93a3834c03528ded9c33124ded73538d8d1dd1568504a6249a29255b5526e6a7171627a2a50d9cbb649cf67af7b3e75e68b150db020542a28ca04badbca18687b526272767a517e695e4a7c727e4e7e11508bb29b1bc88b4075a5c5a945f19040a8562acd4bcc0599f8a2a1f1d9dcfd4f76cc54aaad05b9ce119beb3cfd425c831c9d43e2c3fd835c901c063764fbfa27bb76014d0000a98ebda4";

    // 实录前缀帧（房间 6650029）：auth 回执＋多个 op5 压缩包，含 6 条真 DANMU_MSG
    private static final String RECORDED_B64 = "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";

    private static BiliProvider.Api fakeApi(JSONObject info, JSONObject danmu) {
        return new BiliProvider.Api() {
            @Override
            public JSONObject getInfoByRoom(String candidateId) {
                return info;
            }

            @Override
            public JSONObject getDanmuInfo(long realId) {
                return danmu;
            }

            @Override
            public String buvid3() {
                return "FAKEBUVID3";
            }
        };
    }

    private static JSONObject infoJson(long realId, int liveStatus) throws Exception {
        return new JSONObject("{\"code\":0,\"data\":{\"room_info\":{\"room_id\":" + realId + ",\"live_status\":" + liveStatus + "}}}");
    }

    private static JSONObject danmuJson(String token, String host) throws Exception {
        String hosts = host == null ? "[]" : "[{\"host\":\"" + host + "\"}]";
        return new JSONObject("{\"code\":0,\"data\":{\"token\":\"" + token + "\",\"host_list\":" + hosts + "}}");
    }

    @Test
    public void prepareConvertsShortIdAndBuildsTarget() throws Exception {
        BiliProvider provider = new BiliProvider(fakeApi(infoJson(6650029L, 1), danmuJson("TOK", "host.example.com")));
        Optional<PreparedTarget> target = provider.prepare(SHORT_ROOM).get(5, TimeUnit.SECONDS);
        assertTrue(target.isPresent());
        assertEquals("wss://host.example.com/sub", target.get().wsUrl());
        assertEquals(30_000L, target.get().heartbeatMs());
        assertTrue(target.get().headers().isEmpty());
        assertEquals(1, target.get().onOpenFrames().size());
        List<BiliPacketCodec.RawPacket> authPackets = BiliPacketCodec.decode(target.get().onOpenFrames().get(0));
        assertEquals(1, authPackets.size());
        assertEquals(BiliPacketCodec.OP_AUTH, authPackets.get(0).op());
        JSONObject auth = new JSONObject(new String(authPackets.get(0).body(), StandardCharsets.UTF_8));
        assertEquals(0, auth.getInt("uid"));
        assertEquals(6650029L, auth.getLong("roomid"));
        assertEquals(2, auth.getInt("protover"));
        assertEquals("FAKEBUVID3", auth.getString("buvid"));
        assertEquals("web", auth.getString("platform"));
        assertEquals(2, auth.getInt("type"));
        assertEquals("TOK", auth.getString("key"));
    }

    @Test
    public void prepareFallsBackToDefaultHost() throws Exception {
        BiliProvider provider = new BiliProvider(fakeApi(infoJson(6650029L, 1), danmuJson("TOK", null)));
        Optional<PreparedTarget> target = provider.prepare(SHORT_ROOM).get(5, TimeUnit.SECONDS);
        assertTrue(target.isPresent());
        assertEquals("wss://" + BiliProvider.DEFAULT_HOST + "/sub", target.get().wsUrl());
    }

    @Test
    public void prepareSkipsWhenNotLiveBadCodeOrNoToken() throws Exception {
        assertTrue(new BiliProvider(fakeApi(infoJson(6650029L, 2), danmuJson("TOK", "h"))).prepare(SHORT_ROOM).get(5, TimeUnit.SECONDS).isEmpty());
        assertTrue(new BiliProvider(fakeApi(new JSONObject("{\"code\":-404}"), danmuJson("TOK", "h"))).prepare(SHORT_ROOM).get(5, TimeUnit.SECONDS).isEmpty());
        assertTrue(new BiliProvider(fakeApi(infoJson(6650029L, 1), new JSONObject("{\"code\":-352}"))).prepare(SHORT_ROOM).get(5, TimeUnit.SECONDS).isEmpty());
        assertTrue(new BiliProvider(fakeApi(infoJson(6650029L, 1), danmuJson("", "h"))).prepare(SHORT_ROOM).get(5, TimeUnit.SECONDS).isEmpty());
        BiliProvider broken = new BiliProvider(new BiliProvider.Api() {
            @Override
            public JSONObject getInfoByRoom(String candidateId) throws Exception {
                throw new IllegalStateException("boom");
            }

            @Override
            public JSONObject getDanmuInfo(long realId) {
                return null;
            }

            @Override
            public String buvid3() {
                return "";
            }
        });
        assertTrue(broken.prepare(SHORT_ROOM).get(5, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    public void heartbeatFrameIsOp2Packet() {
        BiliProvider provider = new BiliProvider(fakeApi(null, null));
        assertEquals("00000010001000000000000200000001", HexFormat.of().formatHex(provider.heartbeatFrame(SHORT_ROOM)));
    }

    @Test
    public void decodeSyntheticBundleMatchesReference() {
        BiliProvider provider = new BiliProvider(fakeApi(null, null));
        PlatformDanmakuProvider.DecodeResult result = provider.decode(HexFormat.of().parseHex(BUNDLE_HEX), 3L, 99L);
        assertNull(result.online());
        assertEquals(3, result.messages().size());
        LiveDanmakuMessage red = result.messages().get(0);
        assertEquals("红色弹幕测试", red.text());
        assertEquals(0xFFFF0000, red.colorArgb());
        assertEquals(LiveDanmakuMessage.Type.NORMAL, red.type());
        LiveDanmakuMessage white = result.messages().get(1);
        assertEquals("白色弹幕", white.text());
        assertEquals(0xFFFFFFFF, white.colorArgb());
        LiveDanmakuMessage sc = result.messages().get(2);
        assertEquals("醒目留言测试", sc.text());
        assertEquals(0xFFFF0000, sc.colorArgb());
        assertEquals(LiveDanmakuMessage.Type.SUPER_CHAT, sc.type());
        for (LiveDanmakuMessage message : result.messages()) {
            assertEquals(3L, message.generation());
            assertEquals(99L, message.receivedAtMs());
        }
    }

    @Test
    public void decodeHeartbeatReplyYieldsOnline() {
        BiliProvider provider = new BiliProvider(fakeApi(null, null));
        PlatformDanmakuProvider.DecodeResult result = provider.decode(HexFormat.of().parseHex(OP3_HEX), 1L, 0L);
        assertTrue(result.messages().isEmpty());
        assertEquals(Long.valueOf(1234567L), result.online());
    }

    @Test
    public void decodeRecordedSampleMatchesReference() {
        BiliProvider provider = new BiliProvider(fakeApi(null, null));
        byte[] frame = Base64.getDecoder().decode(RECORDED_B64);
        PlatformDanmakuProvider.DecodeResult result = provider.decode(frame, 5L, 42L);
        assertEquals(6, result.messages().size());
        String[] texts = {"Wolves", "记分板", "TE", "WBG", "计分板", "Wolves"};
        for (int i = 0; i < texts.length; i++) {
            assertEquals(texts[i], result.messages().get(i).text());
            assertEquals(0xFFFFFFFF, result.messages().get(i).colorArgb());
            assertEquals(LiveDanmakuMessage.Type.NORMAL, result.messages().get(i).type());
        }
    }

    @Test
    public void parseMessageKeepsNicknameAndFilters() {
        BiliProvider.DanmuMessage danmu = BiliProvider.parseMessage("{\"cmd\":\"DANMU_MSG\",\"info\":[[0,1,25,16711680],\"hi\",[9,\"昵称甲\"]]}");
        assertEquals("昵称甲", danmu.nickname());
        assertEquals("hi", danmu.text());
        assertEquals(0xFFFF0000, danmu.colorArgb());
        assertNull(BiliProvider.parseMessage("{\"cmd\":\"INTERACT_WORD\",\"data\":{}}"));
        assertNull(BiliProvider.parseMessage("{\"cmd\":\"DANMU_MSG\"}"));
        assertNull(BiliProvider.parseMessage("{\"cmd\":\"DANMU_MSG\",\"info\":[[0,1,25,0],\"\",[9,\"x\"]]}"));
        assertNull(BiliProvider.parseMessage("not json"));
        assertNull(BiliProvider.parseMessage(null));
    }

    @Test
    public void parseHexColorCases() {
        assertEquals(0xFFFF0000, BiliProvider.parseHexColor("#FF0000"));
        assertEquals(0xFF00FF00, BiliProvider.parseHexColor("00FF00"));
        assertEquals(0xFFFFFFFF, BiliProvider.parseHexColor(""));
        assertEquals(0xFFFFFFFF, BiliProvider.parseHexColor("#XYZXYZ"));
        assertEquals(0xFFFFFFFF, BiliProvider.parseHexColor(null));
    }
}
