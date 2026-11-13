package com.fongmi.android.tv.player.exo;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import com.github.catvod.crawler.SpiderDebug;

import java.io.IOException;
import java.net.ProtocolException;
import java.util.List;
import java.util.Map;

/** Reopens a truncated fixed-length HTTP response at the exact unread byte offset. */
final class HttpEofRecoveryDataSource implements DataSource {

    /**
     * Limit only consecutive EOF recoveries. A long remote file may legitimately
     * close its HTTP response more than once, so a lifetime limit would eventually
     * turn a recoverable stream interruption into a player error.
     */
    private static final int MAX_CONSECUTIVE_RECONNECTS = 3;

    private final DataSource upstream;
    private DataSpec dataSpec;
    private long bytesRead;
    private int consecutiveReconnects;
    /** 本次打开上游自报的总长（open 返回值，重连后按已读+余量更新）；
     * C.LENGTH_UNSET 表示上游未报（分块传输等），不判截断。 */
    private long openedLength = C.LENGTH_UNSET;
    /** 重连时上游以 416 确认所求区间不存在：资源确已到尾。 */
    private boolean endConfirmed;

    HttpEofRecoveryDataSource(DataSource upstream) {
        this.upstream = upstream;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        this.dataSpec = dataSpec;
        this.bytesRead = 0;
        this.consecutiveReconnects = 0;
        this.endConfirmed = false;
        long opened = upstream.open(dataSpec);
        this.openedLength = opened;
        return opened;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        while (true) {
            try {
                int read = upstream.read(buffer, offset, length);
                if (read > 0) {
                    bytesRead += read;
                    // Progress proves the resumed connection is healthy. Do not
                    // carry an old burst of EOFs into a later, independent stall.
                    consecutiveReconnects = 0;
                    return read;
                }
                if (read == C.RESULT_END_OF_INPUT && isTruncatedEnd()) {
                    // 上游在自报长度未满时干净收尾（截断）：这份 EOF 绝
                    // 不能落到磁盘缓存层——CacheDataSource 会把截断位写
                    // 成资源总长存进缓存元数据，该资源从此在截断位之后
                    // 永久零字节楔死（119 真机点播拖动后 96 次零字节空
                    // 转、位置冻死实证）。按协议异常同路重连续读；额度
                    // 耗尽抛错让本次加载失败，也好过毒化缓存。
                    if (!canRecoverConsecutively(consecutiveReconnects) || dataSpec == null) {
                        throw new IOException("premature EOF at " + bytesRead
                                + " of " + openedLength);
                    }
                    consecutiveReconnects++;
                    reconnectAtCurrentPosition();
                    if (endConfirmed) return C.RESULT_END_OF_INPUT;
                    continue;
                }
                return read;
            } catch (IOException error) {
                if (!isRecoverableEof(error) || !canRecoverConsecutively(consecutiveReconnects) || dataSpec == null) throw error;
                consecutiveReconnects++;
                reconnectAtCurrentPosition();
                if (endConfirmed) return C.RESULT_END_OF_INPUT;
            }
        }
    }

    /** 上游已读尽与否：自报总长已知且未读满即截断。 */
    private boolean isTruncatedEnd() {
        return !endConfirmed && openedLength != C.LENGTH_UNSET && bytesRead < openedLength;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return upstream.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        dataSpec = null;
        bytesRead = 0;
        consecutiveReconnects = 0;
        openedLength = C.LENGTH_UNSET;
        endConfirmed = false;
        upstream.close();
    }

    private void reconnectAtCurrentPosition() throws IOException {
        try {
            upstream.close();
        } catch (IOException ignored) {
        }
        long remaining = remainingLength();
        DataSpec retrySpec = remaining == C.LENGTH_UNSET ? dataSpec.subrange(bytesRead) : dataSpec.subrange(bytesRead, remaining);
        long reopened;
        try {
            reopened = upstream.open(retrySpec);
        } catch (androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException e) {
            // 416＝所求区间已在资源尾外：上游自报长度与实际不符时
            // 的真到尾确认——按完整读尽收尾（此位即真尾，缓存层
            // 记长无毒），不作失败抛。
            if (e.responseCode != 416) throw e;
            endConfirmed = true;
            openedLength = bytesRead;
            return;
        }
        openedLength = reopened == C.LENGTH_UNSET ? C.LENGTH_UNSET : bytesRead + reopened;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-network", "unexpected EOF recovered offset=%d remaining=%d consecutiveAttempt=%d", retrySpec.position, remaining, consecutiveReconnects);
    }

    private long remainingLength() {
        if (dataSpec == null || dataSpec.length == C.LENGTH_UNSET) return C.LENGTH_UNSET;
        return remainingLength(dataSpec.length, bytesRead);
    }

    static long remainingLength(long requestedLength, long bytesRead) {
        if (requestedLength == C.LENGTH_UNSET) return C.LENGTH_UNSET;
        return Math.max(0, requestedLength - Math.max(0, bytesRead));
    }

    static boolean isRecoverableEof(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ProtocolException && messageContainsUnexpectedEnd(current.getMessage())) return true;
            current = current.getCause();
        }
        return false;
    }

    static boolean canRecoverConsecutively(int consecutiveReconnects) {
        return consecutiveReconnects >= 0 && consecutiveReconnects < MAX_CONSECUTIVE_RECONNECTS;
    }

    private static boolean messageContainsUnexpectedEnd(String message) {
        return message != null && message.toLowerCase(java.util.Locale.ROOT).contains("unexpected end of stream");
    }

    static final class Factory implements DataSource.Factory {
        private final DataSource.Factory upstreamFactory;

        Factory(DataSource.Factory upstreamFactory) {
            this.upstreamFactory = upstreamFactory;
        }

        @Override
        public DataSource createDataSource() {
            return new HttpEofRecoveryDataSource(upstreamFactory.createDataSource());
        }
    }
}
