package com.fongmi.android.tv.player.media;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.Util;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.bean.ClearKey;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.track.LangUtil;
import com.fongmi.android.tv.player.track.TrackUtil;
import com.fongmi.android.tv.setting.Setting;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

public final class MediaItemFactory {

    public static MediaMetadata buildMetadata(String title, String artist, String artUri) {
        title = TextUtils.isEmpty(title) ? "" : title;
        artist = TextUtils.isEmpty(artist) ? "" : artist;
        String subtitle = title.isEmpty() || title.equals(artist) ? "" : artist;
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setSubtitle(subtitle).setArtworkUri(getArtworkUri(artUri)).build();
    }

    public static MediaMetadata buildLiveMetadata(String title, @Nullable EpgData data, String artUri) {
        String details = data == null ? "" : data.format();
        return buildMetadata(title, details, artUri).buildUpon().setStation(title).setDisplayTitle(title).setMediaType(MediaMetadata.MEDIA_TYPE_TV_CHANNEL).build();
    }

    public static Uri getArtworkUri(String artUri) {
        return TextUtils.isEmpty(artUri) ? null : Uri.parse(artUri);
    }

    public static String getDisplayTitle(MediaMetadata metadata) {
        if (!TextUtils.isEmpty(metadata.displayTitle)) return metadata.displayTitle.toString();
        if (!TextUtils.isEmpty(metadata.title)) return metadata.title.toString();
        return TextUtils.isEmpty(metadata.artist) ? "" : metadata.artist.toString();
    }

    public static String getDefaultUserAgent() {
        return Util.getUserAgent(App.get(), BuildConfig.APPLICATION_ID);
    }

    // 树适配：入参用树里现成引擎播放规格；定稿的授权标签与光盘版次两处树里无对应概念（树自有起播亦不设授权标签），随树口径去掉。
    public static MediaItem from(PlaySpec spec) {
        return new MediaItem.Builder().setUri(spec.getUri())
                .setSubtitleConfigurations(buildSubtitleConfigs(spec.getSubs()))
                .setDrmConfiguration(buildDrmConfig(spec.getDrm()))
                .setRequestMetadata(buildRequestMetadata(spec))
                .setMediaMetadata(spec.getMetadata())
                .setAdblock(Setting.isAdblock())
                .setMimeType(spec.getFormat())
                .setImageDurationMs(15000)
                .setMediaId(spec.getKey()).build();
    }

    private static MediaItem.RequestMetadata buildRequestMetadata(PlaySpec spec) {
        Bundle extras = toBundle(spec.getHeaders());
        // 环34 复核订正 B2：与 ExoUtil.getMediaItem 同口——海阔伴声音轨非空才
        // 写键进 extras 供 MediaSourceFactory 合流；恒空时 extras 与旧形同。
        java.util.List<String> companion = com.fongmi.android.tv.player.engine.HkPlayCompanion.companionAudioUrls(spec.getHkAudioUrls());
        if (!companion.isEmpty()) {
            extras.putStringArrayList(com.fongmi.android.tv.player.engine.HkPlayCompanion.EXTRA_AUDIO_URLS, new java.util.ArrayList<>(companion));
        }
        return new MediaItem.RequestMetadata.Builder().setMediaUri(spec.getUri()).setExtras(extras).build();
    }

    private static Bundle toBundle(Map<String, String> headers) {
        Bundle bundle = new Bundle();
        if (headers != null) headers.forEach(bundle::putString);
        return bundle;
    }

    private static List<MediaItem.SubtitleConfiguration> buildSubtitleConfigs(List<Sub> subs) {
        if (subs == null) return List.of();
        List<Sub> valid = subs.stream().filter(sub -> sub != null && !sub.isEmpty()).toList();
        if (valid.isEmpty()) return List.of();
        SubtitleFlags flags = SubtitleFlags.create(valid);
        return IntStream.range(0, valid.size()).mapToObj(i -> buildSubConfig(valid.get(i), flags.get(valid.get(i), i))).toList();
    }

    public static MediaItem.SubtitleConfiguration buildSubConfig(Sub sub) {
        return buildSubConfig(sub, sub.getFlag());
    }

    private static MediaItem.SubtitleConfiguration buildSubConfig(Sub sub, int flag) {
        String mimeType = sub.getFormat();
        String id = "external:" + UUID.nameUUIDFromBytes(sub.getUrl().getBytes(StandardCharsets.UTF_8));
        if (TextUtils.isEmpty(mimeType)) mimeType = TrackUtil.getSubtitleMimeType(sub.getUri().getPath());
        return new MediaItem.SubtitleConfiguration.Builder(sub.getUri()).setId(id).setLabel(sub.getName()).setMimeType(mimeType).setSelectionFlags(flag).setLanguage(sub.getLang()).build();
    }

    private static int findPreferredSubtitleIndex(List<Sub> subs) {
        int bestIndex = C.INDEX_UNSET;
        int bestScore = 0;
        for (int i = 0; i < subs.size(); i++) {
            int score = LangUtil.getPreferredTextLanguageScore(subs.get(i).getLang());
            if (score > bestScore) {
                bestIndex = i;
                bestScore = score;
            }
        }
        return bestIndex;
    }

    // 树适配：定稿用 media3 叉版的 LocalClearKeyLicense 嗅探 key 内容，本树 media3
    // 没有这个类；改用树自有 ClearKey 解析（objectFrom 解析成功且 keys 非空＝
    // clearkey 许可），嗅探不过再落 Drm.getUUID() 的类型口径，决策结构与定稿一致。
    private static boolean isClearKeyLicense(String key) {
        if (TextUtils.isEmpty(key)) return false;
        try {
            return ClearKey.objectFrom(key).hasKeys();
        } catch (Exception e) {
            return false;
        }
    }

    private static MediaItem.DrmConfiguration buildDrmConfig(Drm drm) {
        if (drm == null) return null;
        UUID scheme = isClearKeyLicense(drm.getKey()) ? C.CLEARKEY_UUID : drm.getUUID();
        return new MediaItem.DrmConfiguration.Builder(scheme).setMultiSession(!C.CLEARKEY_UUID.equals(scheme)).setForceDefaultLicenseUri(drm.isForceKey()).setLicenseRequestHeaders(drm.getHeader()).setLicenseUri(drm.getKey()).build();
    }

    private record SubtitleFlags(boolean hasExplicitFlags, int defaultIndex) {

        static SubtitleFlags create(List<Sub> subs) {
            if (subs.size() == 1) return new SubtitleFlags(false, C.INDEX_UNSET);
            if (hasExplicitFlags(subs)) return new SubtitleFlags(true, C.INDEX_UNSET);
            int preferredIndex = findPreferredSubtitleIndex(subs);
            return new SubtitleFlags(false, preferredIndex == C.INDEX_UNSET ? 0 : preferredIndex);
        }

        private static boolean hasExplicitFlags(List<Sub> subs) {
            return subs.stream().anyMatch(sub -> sub.getRawFlag() != 0);
        }

        int get(Sub sub, int index) {
            if (sub.getRawFlag() != 0) return sub.getFlag();
            if (hasExplicitFlags) return C.SELECTION_FLAG_AUTOSELECT;
            if (defaultIndex == C.INDEX_UNSET) return sub.getFlag();
            return index == defaultIndex ? C.SELECTION_FLAG_DEFAULT : C.SELECTION_FLAG_AUTOSELECT;
        }
    }
}
