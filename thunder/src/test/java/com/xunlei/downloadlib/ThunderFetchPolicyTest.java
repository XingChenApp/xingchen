package com.xunlei.downloadlib;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThunderFetchPolicyTest {

    @Test
    public void aliveStatusOnlyZeroOneTwo() {
        assertTrue(ThunderFetchPolicy.isAliveStatus(0));
        assertTrue(ThunderFetchPolicy.isAliveStatus(1));
        assertTrue(ThunderFetchPolicy.isAliveStatus(2));
        assertFalse(ThunderFetchPolicy.isAliveStatus(3));
        assertFalse(ThunderFetchPolicy.isAliveStatus(-1));
        assertFalse(ThunderFetchPolicy.isAliveStatus(4));
    }

    @Test
    public void hasBytesEitherCounterPositive() {
        assertFalse(ThunderFetchPolicy.hasBytes(0, 0));
        assertTrue(ThunderFetchPolicy.hasBytes(1, 0));
        assertTrue(ThunderFetchPolicy.hasBytes(0, 1));
        assertTrue(ThunderFetchPolicy.hasBytes(1024, 2048));
    }

    @Test
    public void entryReuseNeedsAllFiveConditions() {
        assertTrue(ThunderFetchPolicy.shouldReuseAtEntry(true, true, true, 0, false, true));
        assertTrue(ThunderFetchPolicy.shouldReuseAtEntry(true, true, true, 1, false, true));
        assertTrue(ThunderFetchPolicy.shouldReuseAtEntry(true, true, true, 2, false, true));
        assertFalse(ThunderFetchPolicy.shouldReuseAtEntry(false, true, true, 1, false, true));
        assertFalse(ThunderFetchPolicy.shouldReuseAtEntry(true, false, true, 1, false, true));
        assertFalse(ThunderFetchPolicy.shouldReuseAtEntry(true, true, false, 1, false, true));
        assertFalse(ThunderFetchPolicy.shouldReuseAtEntry(true, true, true, 1, true, true));
        assertFalse(ThunderFetchPolicy.shouldReuseAtEntry(true, true, true, 1, false, false));
    }

    @Test
    public void entryReuseRejectsDeadOrUnreadableTask() {
        assertFalse(ThunderFetchPolicy.shouldReuseAtEntry(true, true, true, 3, false, true));
        assertFalse(ThunderFetchPolicy.shouldReuseAtEntry(true, true, true, -1, false, true));
    }

    @Test
    public void correctErrorStatusOnlyWhenBytesAndNoZeroMark() {
        assertEquals(1, ThunderFetchPolicy.correctErrorStatus(3, true, false));
        assertEquals(3, ThunderFetchPolicy.correctErrorStatus(3, false, false));
        assertEquals(3, ThunderFetchPolicy.correctErrorStatus(3, true, true));
        assertEquals(3, ThunderFetchPolicy.correctErrorStatus(3, false, true));
    }

    @Test
    public void correctErrorStatusLeavesOtherStatusesAlone() {
        assertEquals(0, ThunderFetchPolicy.correctErrorStatus(0, true, false));
        assertEquals(1, ThunderFetchPolicy.correctErrorStatus(1, true, false));
        assertEquals(2, ThunderFetchPolicy.correctErrorStatus(2, false, true));
        assertEquals(-1, ThunderFetchPolicy.correctErrorStatus(-1, true, false));
    }

    @Test
    public void internalActionReusesLiveTaskWithBytes() {
        assertEquals(ThunderFetchPolicy.InternalAction.REUSE, ThunderFetchPolicy.internalAction(true, true, true, 0, true));
        assertEquals(ThunderFetchPolicy.InternalAction.REUSE, ThunderFetchPolicy.internalAction(true, true, true, 1, true));
        assertEquals(ThunderFetchPolicy.InternalAction.REUSE, ThunderFetchPolicy.internalAction(true, true, true, 2, true));
    }

    @Test
    public void internalActionCleansZeroByteSameUrlTask() {
        assertEquals(ThunderFetchPolicy.InternalAction.CLEANUP_REBUILD, ThunderFetchPolicy.internalAction(true, true, true, 0, false));
        assertEquals(ThunderFetchPolicy.InternalAction.CLEANUP_REBUILD, ThunderFetchPolicy.internalAction(true, true, true, 1, false));
        assertEquals(ThunderFetchPolicy.InternalAction.CLEANUP_REBUILD, ThunderFetchPolicy.internalAction(true, true, true, 2, false));
    }

    @Test
    public void internalActionProceedsWhenNotSameLiveTask() {
        assertEquals(ThunderFetchPolicy.InternalAction.PROCEED, ThunderFetchPolicy.internalAction(true, true, true, 3, true));
        assertEquals(ThunderFetchPolicy.InternalAction.PROCEED, ThunderFetchPolicy.internalAction(true, true, true, -1, true));
        assertEquals(ThunderFetchPolicy.InternalAction.PROCEED, ThunderFetchPolicy.internalAction(false, true, true, 1, true));
        assertEquals(ThunderFetchPolicy.InternalAction.PROCEED, ThunderFetchPolicy.internalAction(true, false, true, 1, true));
        assertEquals(ThunderFetchPolicy.InternalAction.PROCEED, ThunderFetchPolicy.internalAction(true, true, false, 1, true));
    }

    @Test
    public void cleanupOnErrorOnlyForPresentTaskInError() {
        assertTrue(ThunderFetchPolicy.shouldCleanupOnError(true, 3));
        assertFalse(ThunderFetchPolicy.shouldCleanupOnError(true, 1));
        assertFalse(ThunderFetchPolicy.shouldCleanupOnError(true, -1));
        assertFalse(ThunderFetchPolicy.shouldCleanupOnError(false, 3));
    }

    @Test
    public void cleanupOnLinkChangeOnlyForDifferentContent() {
        assertTrue(ThunderFetchPolicy.shouldCleanupOnLinkChange(true, false));
        assertFalse(ThunderFetchPolicy.shouldCleanupOnLinkChange(true, true));
        assertFalse(ThunderFetchPolicy.shouldCleanupOnLinkChange(false, false));
    }

    @Test
    public void joinInflightMirrorsPresence() {
        assertTrue(ThunderFetchPolicy.shouldJoinInflight(true));
        assertFalse(ThunderFetchPolicy.shouldJoinInflight(false));
    }

    @Test
    public void stopProtectsLiveTaskWithHandedAddress() {
        assertTrue(ThunderFetchPolicy.shouldProtectOnStop(true, true, 0));
        assertTrue(ThunderFetchPolicy.shouldProtectOnStop(true, true, 1));
        assertTrue(ThunderFetchPolicy.shouldProtectOnStop(true, true, 2));
        assertFalse(ThunderFetchPolicy.shouldProtectOnStop(true, true, 3));
        assertFalse(ThunderFetchPolicy.shouldProtectOnStop(true, true, -1));
        assertFalse(ThunderFetchPolicy.shouldProtectOnStop(true, false, 1));
        assertFalse(ThunderFetchPolicy.shouldProtectOnStop(false, true, 1));
    }

    @Test
    public void directRoundWaitsWithoutInfoOrProgress() {
        assertEquals(ThunderFetchPolicy.DirectRound.WAIT, ThunderFetchPolicy.directRound(false, 1, true));
        assertEquals(ThunderFetchPolicy.DirectRound.WAIT, ThunderFetchPolicy.directRound(false, 3, true));
        assertEquals(ThunderFetchPolicy.DirectRound.WAIT, ThunderFetchPolicy.directRound(true, 0, true));
        assertEquals(ThunderFetchPolicy.DirectRound.WAIT, ThunderFetchPolicy.directRound(true, 1, false));
        assertEquals(ThunderFetchPolicy.DirectRound.WAIT, ThunderFetchPolicy.directRound(true, 2, false));
    }

    @Test
    public void directRoundFailsOnErrorStatus() {
        assertEquals(ThunderFetchPolicy.DirectRound.FAIL_ERROR, ThunderFetchPolicy.directRound(true, 3, true));
        assertEquals(ThunderFetchPolicy.DirectRound.FAIL_ERROR, ThunderFetchPolicy.directRound(true, 3, false));
    }

    @Test
    public void directRoundHandsOffOnlyNonZeroWithBytes() {
        assertEquals(ThunderFetchPolicy.DirectRound.HAND_OFF, ThunderFetchPolicy.directRound(true, 1, true));
        assertEquals(ThunderFetchPolicy.DirectRound.HAND_OFF, ThunderFetchPolicy.directRound(true, 2, true));
    }

    @Test
    public void btRoundParentErrorWinsFirst() {
        assertEquals(ThunderFetchPolicy.BtRound.FAIL_PARENT, ThunderFetchPolicy.btRound(3, false, true, 1));
        assertEquals(ThunderFetchPolicy.BtRound.FAIL_PARENT, ThunderFetchPolicy.btRound(3, true, false, 0));
        assertEquals(ThunderFetchPolicy.BtRound.FAIL_PARENT, ThunderFetchPolicy.btRound(3, false, true, 3));
    }

    @Test
    public void btRoundWaitsOnReadFailureOrSubNotStarted() {
        assertEquals(ThunderFetchPolicy.BtRound.WAIT, ThunderFetchPolicy.btRound(-1, true, false, 0));
        assertEquals(ThunderFetchPolicy.BtRound.WAIT, ThunderFetchPolicy.btRound(1, true, true, 1));
        assertEquals(ThunderFetchPolicy.BtRound.WAIT, ThunderFetchPolicy.btRound(-1, false, true, 0));
        assertEquals(ThunderFetchPolicy.BtRound.WAIT, ThunderFetchPolicy.btRound(0, false, true, 0));
    }

    @Test
    public void btRoundFailsOnEmptyOrErrorSub() {
        assertEquals(ThunderFetchPolicy.BtRound.FAIL_SUB_EMPTY, ThunderFetchPolicy.btRound(-1, false, false, 0));
        assertEquals(ThunderFetchPolicy.BtRound.FAIL_SUB_EMPTY, ThunderFetchPolicy.btRound(1, false, false, 1));
        assertEquals(ThunderFetchPolicy.BtRound.FAIL_SUB, ThunderFetchPolicy.btRound(-1, false, true, 3));
        assertEquals(ThunderFetchPolicy.BtRound.FAIL_SUB, ThunderFetchPolicy.btRound(2, false, true, 3));
    }

    @Test
    public void btRoundHandsOffOnAnyNonZeroSubStatus() {
        assertEquals(ThunderFetchPolicy.BtRound.HAND_OFF, ThunderFetchPolicy.btRound(-1, false, true, 1));
        assertEquals(ThunderFetchPolicy.BtRound.HAND_OFF, ThunderFetchPolicy.btRound(0, false, true, 1));
        assertEquals(ThunderFetchPolicy.BtRound.HAND_OFF, ThunderFetchPolicy.btRound(1, false, true, 2));
    }
}
