package com.fongmi.android.tv.hongguo.drama;

import java.util.Collections;
import java.util.List;

/**
 * 一集的播放模型（不可变）：multi_video_model 回包里一集的 video_model，
 * 或 fplay 回包的 video_info.data，两者归一到本类。
 */
public final class DramaVideoModel {

    private final String requestedId;
    private final String videoId;
    private final double durationSec;
    private final long urlExpire;
    private final String keySeed;
    private final String fallbackApi;
    private final int videoWidth;
    private final int videoHeight;
    /** 点赞数（模型 JSON digged_count）；没带为 -1（#15）。 */
    private final long diggCount;
    private final List<DramaEntry> entries;
    /** 构建本模型的 video_model 原始 JSON 串（buildModel 入参原文）；模型缓存落盘时存它、读回经 DramaParser.restoreModel 同路重建，保证字段零漂移。 */
    private final String sourceJson;

    DramaVideoModel(String requestedId, String videoId,
                 double durationSec, long urlExpire,
                 String keySeed, String fallbackApi, int videoWidth, int videoHeight,
                 long diggCount, List<DramaEntry> entries, String sourceJson) {
        this.requestedId = requestedId;
        this.videoId = videoId;
        this.durationSec = durationSec;
        this.urlExpire = urlExpire;
        this.keySeed = keySeed;
        this.fallbackApi = fallbackApi;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.diggCount = diggCount;
        this.entries = Collections.unmodifiableList(entries);
        this.sourceJson = sourceJson;
    }

    /** 请求时的集 id（multi 回包 data 的键）；fplay 形态为 null。 */
    public String getRequestedId() { return requestedId; }
    /** 服务端 video_id（v 开头的内部 id）。 */
    public String getVideoId() { return videoId; }
    public double getDurationSec() { return durationSec; }
    /** 直链过期时间戳（秒）；0 表示回包未给。 */
    public long getUrlExpire() { return urlExpire; }
    /** 点赞数；模型没带为 -1。 */
    public long getDiggCount() { return diggCount; }
    /** fplay 形态才有：URL 解密用的 key_seed（Base64，32 字节）；multi 明文链形态为 null。 */
    public String getKeySeed() { return keySeed; }
    /** multi 形态才有：fplay 兜底取址地址；无则 null。 */
    public String getFallbackApi() { return fallbackApi; }
    public int getVideoWidth() { return videoWidth; }
    public int getVideoHeight() { return videoHeight; }
    public List<DramaEntry> getEntries() { return entries; }
    /** 构建来源的 video_model 原始 JSON 串（落盘缓存用）；理论上恒非空，防御性判空在消费方。 */
    public String getSourceJson() { return sourceJson; }

    public DramaEntry entryByDefinition(String definition) {
        if (definition == null) return null;
        for (DramaEntry e : entries) if (definition.equals(e.getDefinition())) return e;
        return null;
    }

    /** 按官方档位序取第一个通用播放器可解的档位；都没有时返回第一档（可能为 null）。 */
    public DramaEntry firstPlayableEntry() {
        String[] order = {"1080p", "720p", "540p", "480p", "360p"};
        for (String def : order) {
            DramaEntry e = entryByDefinition(def);
            if (e != null && e.isPlayableCodec()) return e;
        }
        for (DramaEntry e : entries) if (e.isPlayableCodec()) return e;
        return entries.isEmpty() ? null : entries.get(0);
    }

    /**
     * 模型级保护判定（定罪账借条 3，openhongguo-dig-20261028.md §5）：
     * 任一档带加密标记即整模型受保护。钥匙全模型共用一份，见 {@link #deriveDekHex()}。
     */
    public boolean isProtected() {
        for (DramaEntry e : entries) if (e.hasEncryptMarkers()) return true;
        return false;
    }

    /** 全模型扫 spade_a 推 DEK：第一个推得出的即模型钥匙；全推不出返回 null。 */
    public String deriveDekHex() {
        for (DramaEntry e : entries) {
            String dek = e.deriveDekHex();
            if (dek != null) return dek;
        }
        return null;
    }

    /** 全模型扫 v2 末位候选：正路 DEK 全推不出且 fplay 换形已试过后才许消费（证据未定案，见 SpadeKey）。 */
    public String deriveDekHexV2Candidate() {
        for (DramaEntry e : entries) {
            String dek = e.deriveDekHexV2Candidate();
            if (dek != null) return dek;
        }
        return null;
    }
}
