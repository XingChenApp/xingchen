package com.xunlei.downloadlib;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UtilTest {

    @Test
    public void sizeFormatsKnownUnits() {
        assertEquals("", Util.size(0));
        assertEquals("[500 bytes] ", Util.size(500));
        assertEquals("[1 KB] ", Util.size(1024));
        assertEquals("[2 MB] ", Util.size(2L * 1024 * 1024));
    }

    @Test
    public void sizeBeyondLargestUnitClampsInsteadOfOverflowing() {
        long petabyte = 1024L * 1024 * 1024 * 1024 * 1024;
        assertEquals("[1024 TB] ", Util.size(petabyte));
        assertTrue(Util.size(Long.MAX_VALUE).endsWith("TB] "));
    }

    @Test
    public void isMediaRequiresMediaExtensionAndSizeAboveMinimal() {
        long above = 31L * 1024 * 1024;
        long minimal = 30L * 1024 * 1024;
        assertTrue(Util.isMedia("mp4", above));
        assertTrue(Util.isMedia("mkv", above));
        assertTrue(Util.isMedia("mp3", above));
        assertFalse(Util.isMedia("mp4", minimal));
        assertFalse(Util.isMedia("txt", above));
    }
}
