package com.fongmi.android.tv.player.extractor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThunderContentKeyTest {

    @Test
    public void contentKeyExtractsBtihRegardlessOfParameterOrderAndCase() {
        assertEquals("btih:abcdef0123", Thunder.contentKey("magnet:?xt=urn:btih:ABCDEF0123&dn=Name"));
        assertEquals("btih:abcdef0123", Thunder.contentKey("magnet:?dn=Name&xt=urn:btih:ABCDEF0123"));
        assertEquals("btih:abcdef0123", Thunder.contentKey("magnet:?xt=urn:btih:abcdef0123&tr=udp%3A%2F%2Ftracker"));
    }

    @Test
    public void contentKeyExtractsEd2kFileHash() {
        assertEquals("ed2k:abcdef0123456789abcdef0123456789",
                Thunder.contentKey("ed2k://|file|Movie.mkv|12345|ABCDEF0123456789ABCDEF0123456789|/"));
    }

    @Test
    public void contentKeyFallsBackToRawUrlAndToleratesNull() {
        assertEquals("", Thunder.contentKey(null));
        assertEquals("https://example.com/a.mp4", Thunder.contentKey("https://example.com/a.mp4"));
        assertEquals("magnet:?dn=NoHash", Thunder.contentKey("magnet:?dn=NoHash"));
    }

    @Test
    public void sameContentMatchesSameMagnetWrittenDifferently() {
        assertTrue(Thunder.sameContent(
                "magnet:?xt=urn:btih:AAAA1111&dn=X",
                "magnet:?dn=Y&xt=urn:btih:aaaa1111"));
        assertFalse(Thunder.sameContent(
                "magnet:?xt=urn:btih:AAAA1111",
                "magnet:?xt=urn:btih:BBBB2222"));
    }

    @Test
    public void sameContentMatchesEd2kByHashAndOthersByExactString() {
        assertTrue(Thunder.sameContent(
                "ed2k://|file|A.mkv|100|ABCDEF0123456789ABCDEF0123456789|/",
                "ed2k://|file|B.mkv|200|abcdef0123456789abcdef0123456789|/"));
        assertTrue(Thunder.sameContent("http://a/1", "http://a/1"));
        assertFalse(Thunder.sameContent("http://a/1", "http://a/2"));
        assertFalse(Thunder.sameContent(null, "magnet:?xt=urn:btih:AAAA1111"));
        assertFalse(Thunder.sameContent("magnet:?xt=urn:btih:AAAA1111", null));
    }
}
