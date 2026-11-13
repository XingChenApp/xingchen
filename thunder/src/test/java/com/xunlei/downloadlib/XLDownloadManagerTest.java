package com.xunlei.downloadlib;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class XLDownloadManagerTest {

    @Test
    public void checkErrorCodeAcceptsNoError() {
        XLDownloadManager.checkErrorCode(9000);
    }

    @Test
    public void checkErrorCodeTurnsNativeCodeIntoVisibleError() {
        try {
            XLDownloadManager.checkErrorCode(9304);
            fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals("版權限制", e.getMessage());
        }
        try {
            XLDownloadManager.checkErrorCode(999999);
            fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals("ErrorCode=999999", e.getMessage());
        }
    }
}
