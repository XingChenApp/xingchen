package com.xunlei.downloadlib;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class XLTaskHelperTest {

    @Test
    public void normalizeSchemeLowercasesOnlyTheSchemeSegment() {
        assertEquals("magnet:?xt=urn:btih:ABCDEF0123", XLTaskHelper.normalizeScheme("MAGNET:?xt=urn:btih:ABCDEF0123"));
        assertEquals("magnet:?xt=urn:btih:ABCDEF0123", XLTaskHelper.normalizeScheme("Magnet:?xt=urn:btih:ABCDEF0123"));
        assertEquals("thunder://QUFmdHA6Ly9hQkM=", XLTaskHelper.normalizeScheme("THUNDER://QUFmdHA6Ly9hQkM="));
    }

    @Test
    public void normalizeSchemeTrimsAndToleratesNullAndColonlessInput() {
        assertEquals("", XLTaskHelper.normalizeScheme(null));
        assertEquals("", XLTaskHelper.normalizeScheme("   "));
        assertEquals("magnet:?xt=urn:btih:ABC", XLTaskHelper.normalizeScheme("  magnet:?xt=urn:btih:ABC  "));
        assertEquals("magnet", XLTaskHelper.normalizeScheme("magnet"));
    }
}
