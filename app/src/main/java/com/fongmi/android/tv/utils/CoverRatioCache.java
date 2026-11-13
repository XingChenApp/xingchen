package com.fongmi.android.tv.utils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class CoverRatioCache {

    private static final int MAX_SIZE = 200;
    /** 短剧封面高宽比阈值（高／宽，竖图才算短剧；X-17 现算直进复用同一阈值）。 */
    public static final float SHORT_DRAMA_THRESHOLD = 1.6f;

    private static final Map<String, Float> CACHE = Collections.synchronizedMap(new LinkedHashMap<String, Float>(MAX_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
            return size() > MAX_SIZE;
        }
    });

    public static void put(String url, int width, int height) {
        if (url == null || url.isEmpty() || width <= 0 || height <= 0) return;
        CACHE.put(url, (float) height / (float) width);
    }

    public static boolean isShortDramaCover(String url) {
        if (url == null || url.isEmpty()) return false;
        Float ratio = CACHE.get(url);
        return ratio != null && ratio >= SHORT_DRAMA_THRESHOLD;
    }

    public static void clear() {
        CACHE.clear();
    }
}
