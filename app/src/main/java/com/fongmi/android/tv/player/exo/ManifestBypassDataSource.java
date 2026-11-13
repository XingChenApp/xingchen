package com.fongmi.android.tv.player.exo;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps manifest loads (HLS playlists, DASH MPDs) out of the write-through disk
 * cache. A cached live playlist never advances: the player re-reads the same
 * frozen segment list every reload, plays out the few seconds it names, then
 * fails with a source error. Media loads keep the cached chain untouched;
 * manifest loads take the parallel no-cache chain. The split is decided per
 * open from the request URI, using the same manifest indicators the
 * byte-position layer and the resource classifier already use.
 */
final class ManifestBypassDataSource implements DataSource {

    /**
     * Manifest URIs learned by content, not by address: the byte-position
     * layer sniffs the first bytes of every load, and a playlist whose
     * address carries no manifest indicator (extensionless media
     * playlists are common on live CDNs) is registered here the first
     * time its "#EXTM3U" head flows past. Registration is process-wide
     * and bounded (eldest evicted past the cap; an evicted URI simply
     * re-registers the next time it plays): every later open of that URI
     * takes the no-cache chain, and the factory-side observer also evicts
     * the frozen cache entry so the poison cannot outlive the lesson.
     */
    private static final int OBSERVED_MANIFEST_CAP = 256;

    private static final java.util.Set<String> OBSERVED_MANIFEST_URIS =
            java.util.Collections.synchronizedSet(
                    new java.util.LinkedHashSet<>());

    static void noteManifestUri(String uri) {
        if (uri == null || uri.isEmpty()) return;
        // 有界登记（复核败 3）：带签名/时效参数的清单地址每换
        // 一发不得永久多占一条——超上限按登记先后淘汰最老，
        // 被淘汰的地址再播时由字节嗅探重新登记，自愈。
        synchronized (OBSERVED_MANIFEST_URIS) {
            OBSERVED_MANIFEST_URIS.add(uri);
            while (OBSERVED_MANIFEST_URIS.size()
                    > OBSERVED_MANIFEST_CAP) {
                java.util.Iterator<String> it =
                        OBSERVED_MANIFEST_URIS.iterator();
                it.next();
                it.remove();
            }
        }
    }

    static final class Factory implements DataSource.Factory {

        private final DataSource.Factory mediaFactory;
        private final DataSource.Factory manifestFactory;

        Factory(DataSource.Factory mediaFactory, DataSource.Factory manifestFactory) {
            this.mediaFactory = mediaFactory;
            this.manifestFactory = manifestFactory;
        }

        @Override
        public DataSource createDataSource() {
            return new ManifestBypassDataSource(mediaFactory, manifestFactory);
        }
    }

    private final DataSource.Factory mediaFactory;
    private final DataSource.Factory manifestFactory;
    private final List<TransferListener> listeners = new ArrayList<>();

    @Nullable
    private DataSource active;

    private ManifestBypassDataSource(DataSource.Factory mediaFactory,
                                     DataSource.Factory manifestFactory) {
        this.mediaFactory = mediaFactory;
        this.manifestFactory = manifestFactory;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        listeners.add(transferListener);
        if (active != null) active.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        active = (isManifestUri(dataSpec.uri) ? manifestFactory : mediaFactory).createDataSource();
        for (TransferListener listener : listeners) active.addTransferListener(listener);
        return active.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return active == null ? C.RESULT_END_OF_INPUT : active.read(buffer, offset, length);
    }

    @Nullable
    @Override
    public Uri getUri() {
        return active == null ? null : active.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return active == null ? Map.of() : active.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        if (active != null) {
            active.close();
            active = null;
        }
    }

    private static boolean isManifestUri(@Nullable Uri uri) {
        if (uri == null) return false;
        if (OBSERVED_MANIFEST_URIS.contains(uri.toString())) return true;
        String value = uri.toString().toLowerCase(Locale.US);
        return value.contains("m3u8") || value.contains("mpegurl") || value.contains(".mpd")
                || value.contains(".m3u") || value.contains("format=hls")
                || value.contains("format=dash")
                || value.contains("type=m3u8") || value.contains("type=mpd")
                || value.contains("type=hls");
    }
}
