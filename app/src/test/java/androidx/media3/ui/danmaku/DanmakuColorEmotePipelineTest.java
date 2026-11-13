package androidx.media3.ui.danmaku;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import androidx.media3.ui.danmaku.parser.TxtParser;
import org.junit.Test;

/**
 * 颜色/表情端到端钉死：直播侧按 PlayerManager.onLiveDanmakuBatch 的同款构造把
 * LiveDanmakuMessage 的 colorArgb 喂进显示条目，断言颜色一路活到 DanmakuRenderItem；
 * 点播侧用 B 站 p 属性文本行验 ParserUtil 的颜色与渐变解析；emoji 在两条路都原样保留。
 * 样本颜色取自各家录制对拍（斗鱼粉 ffff69b4、虎牙橙 16763904、快手红 4294901760）。
 */
public class DanmakuColorEmotePipelineTest {

    private static DanmakuRenderItem liveItem(String text, int colorArgb) {
        Danmaku danmaku =
                new Danmaku(
                        text,
                        1_000L,
                        Danmaku.TYPE_SCROLL,
                        colorArgb,
                        0f,
                        Danmaku.POOL_NORMAL,
                        "",
                        0L);
        return new DanmakuRenderItem(danmaku);
    }

    @Test
    public void liveProviderColorsSurviveIntoRenderItem() {
        assertEquals(0xFFFF69B4, liveItem("斗鱼粉", 0xFFFF69B4).color);
        assertEquals(0xFFFFB400, liveItem("虎牙橙", 0xFFFFB400).color);
        assertEquals(0xFFFF0000, liveItem("快手红", 0xFFFF0000).color);
        assertEquals(0xFF1E87F0, liveItem("斗鱼蓝", 0xFF1E87F0).color);
        assertEquals(0xFFFFFFFF, liveItem("普通白", 0xFFFFFFFF).color);
    }

    @Test
    public void liveEmojiTextSurvivesIntoRenderItem() {
        DanmakuRenderItem item = liveItem("🌈彩虹🐢[捂脸]", 0xFFFFFFFF);
        assertEquals("🌈彩虹🐢[捂脸]", item.text);
    }

    @Test
    public void vodLineColorAndGradientAreParsed() {
        Danmaku gradient =
                TxtParser.parseLine("[12.5,1,25,16711680,0,0,42,99,FF0000:00FF00]渐变弹幕");
        assertNotNull(gradient);
        assertEquals(0xFFFF0000, gradient.color);
        assertArrayEquals(
                new int[] {0xFFFF0000, 0xFF00FF00}, gradient.getSourceGradientColors());

        Danmaku plain = TxtParser.parseLine("[3.0,1,25,255,0,0,42,100]蓝色弹幕");
        assertNotNull(plain);
        assertEquals(0xFF0000FF, plain.color);
    }

    @Test
    public void vodEmojiTextIsPreserved() {
        Danmaku danmaku = TxtParser.parseLine("[1.0,1,25,16777215,0,0,42,101]🌈好看🐢");
        assertNotNull(danmaku);
        assertEquals("🌈好看🐢", danmaku.text);
        assertEquals(0xFFFFFFFF, danmaku.color);
    }
}
