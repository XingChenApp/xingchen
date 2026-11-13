package com.fongmi.android.tv.api.hk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * getDisplayTitle 口径（2026-10-24 修）：HkRule.getDisplayName() 把 null 归一成 ""，
 * 未设 displayName 的规则发动机 displayName 恒为空串——显示名必须空串也回落 title，
 * 否则底部状态条/结果来源标/失败汇总三处源名全灭。设了 displayName 时行为与 8.83 原版一致。
 */
public class HkSearchEngineDisplayTitleTest {

    private static HkSearchEngine engine(String title, String displayName) {
        HkSearchEngine e = new HkSearchEngine();
        e.setTitle(title);
        e.setDisplayName(displayName);
        return e;
    }

    @Test
    public void emptyDisplayNameFallsBackToTitle() {
        assertEquals("MissAV", engine("MissAV", "").getDisplayTitle());
    }

    @Test
    public void nullDisplayNameFallsBackToTitle() {
        assertEquals("MissAV", engine("MissAV", null).getDisplayTitle());
    }

    @Test
    public void setDisplayNameWinsOverTitle() {
        assertEquals("别名", engine("MissAV", "别名").getDisplayTitle());
    }

    @Test
    public void fromRuleWithoutDisplayNameKeepsTitle() {
        HkRule rule = new HkRule();
        rule.setTitle("JAV Verse");
        // HkRule.getDisplayName() 未设时返回 ""（归一），fromRule 照单全收
        assertEquals("", rule.getDisplayName());
        assertEquals("JAV Verse", HkSearchEngine.fromRule(rule).getDisplayTitle());
    }

    @Test
    public void bothEmptyStaysEmptyTitle() {
        assertNull(engine(null, "").getDisplayTitle());
    }
}
