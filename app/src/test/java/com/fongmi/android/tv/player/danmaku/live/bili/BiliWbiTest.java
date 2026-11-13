package com.fongmi.android.tv.player.danmaku.live.bili;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * BiliWbi 对拍：向量由独立 Python 实现（照 dart_simple_live 的 getWbiSign 同算法）离线生成，
 * 固定 img/sub key 与 wts，逐字符比对 mixinKey 与带 w_rid 的签名查询串。
 */
public class BiliWbiTest {

    private static final String IMG_KEY = "7cd084941338484aae1ad9425b84077c";
    private static final String SUB_KEY = "4932caff0ff746eab6f01bf08b70ac45";
    private static final String MIXIN = "ea1db124af3c7062474693fa704f4ff8";

    @Test
    public void mixinKeyMatchesReference() {
        assertEquals(MIXIN, BiliWbi.mixinKey(IMG_KEY, SUB_KEY));
        assertEquals(32, BiliWbi.mixinKey(IMG_KEY, SUB_KEY).length());
    }

    @Test
    public void signedQueryMatchesReference() {
        String query = BiliWbi.buildSignedQuery(Map.of("id", "7734200"), MIXIN, 1768886400L);
        assertEquals("id=7734200&wts=1768886400&w_rid=bf616cc3f1069a333c56339d58f003fe", query);
    }

    @Test
    public void signedQueryFiltersSpecialCharsAndSorts() {
        String query = BiliWbi.buildSignedQuery(Map.of("room_id", "2419747", "foo", "a'b(c)d*e!f"), MIXIN, 1700000000L);
        assertEquals("foo=abcdef&room_id=2419747&wts=1700000000&w_rid=93036430c131d0e57c2d8b28c3088cf7", query);
    }

    @Test
    public void filterAndEncode() {
        assertEquals("abcdef", BiliWbi.filterValue("a'b(c)d*e!f"));
        assertEquals("", BiliWbi.filterValue(null));
        assertEquals("a%20b", BiliWbi.percentEncode("a b"));
        assertEquals("%E5%BC%B9", BiliWbi.percentEncode("弹"));
        assertEquals("a-b_c.d~e", BiliWbi.percentEncode("a-b_c.d~e"));
    }
}
