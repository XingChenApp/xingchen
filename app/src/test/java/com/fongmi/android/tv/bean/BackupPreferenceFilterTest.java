package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackupPreferenceFilterTest {

    @Test
    public void unrelatedWebHomeDisplaySettingRemainsAnAppSetting() {
        SyncOptions webHomeOnly = new SyncOptions().config(false).spider(false).webHome(true).settings(false);
        SyncOptions settingsOnly = new SyncOptions().config(false).spider(false).webHome(false).settings(true);

        assertFalse(Backup.include("web_home_fullscreen", webHomeOnly));
        assertTrue(Backup.include("web_home_fullscreen", settingsOnly));
    }

    @Test
    public void playbackExperimentStateRemainsDeviceLocal() {
        SyncOptions everything = new SyncOptions()
                .config(true)
                .spider(true)
                .webHome(true)
                .settings(true);

        assertFalse(Backup.include("playback_experiment_schema", everything));
        assertFalse(Backup.include("playback_experiment_enabled", everything));
        assertFalse(Backup.include("playback_experiment_exo", everything));
        assertFalse(Backup.include("playback_experiment_mpv", everything));
        assertFalse(Backup.include(
                "playback_experiment_exo_frame_schema", everything));
        assertFalse(Backup.include(
                "playback_experiment_exo_frame_device", everything));
        assertFalse(Backup.include(
                "playback_experiment_exo_frame_unit", everything));
        assertFalse(Backup.include(
                "playback_experiment_profile_ab_schema", everything));
        assertFalse(Backup.include(
                "playback_experiment_profile_ab_device", everything));
        assertFalse(Backup.include(
                "playback_experiment_profile_ab_enabled", everything));
        assertFalse(Backup.include(
                "playback_experiment_profile_ab_samples_v1", everything));
        assertFalse(Backup.include(
                "playback_performance_profile_merge_schema", everything));
        assertFalse(Backup.include(
                "playback_performance_profile_merge_rolled_back",
                everything));
        assertFalse(Backup.include(
                "playback_performance_profile_merge_migrated_mask",
                everything));
        assertFalse(Backup.include(
                "playback_performance_profile_auto_light_v1",
                everything));
        assertFalse(Backup.include(
                "playback_experiment_lightweight_assessment_schema",
                everything));
        assertFalse(Backup.include(
                "playback_experiment_lightweight_assessment_device",
                everything));
        assertFalse(Backup.include(
                "playback_experiment_lightweight_assessment_enabled",
                everything));
        assertFalse(Backup.include(
                "playback_experiment_lightweight_assessment_samples_v1",
                everything));
    }

    @Test
    public void dynamicNetworkProtectionFollowsSettingsBackupButRetiredConsentDoesNot() {
        SyncOptions everything = new SyncOptions()
                .config(true)
                .spider(true)
                .webHome(true)
                .settings(true);

        assertTrue(Backup.include(
                "perf_exo_network_protection_mode", everything));
        assertFalse(Backup.include(
                "perf_exo_single_rate_rescue_enabled_v1", everything));
    }

    @Test
    public void updateDownloadSettingsFollowAppSettingsSync() {
        SyncOptions settings = new SyncOptions().config(false).spider(false).settings(true);

        assertTrue(Backup.include("update_source", settings));
        assertTrue(Backup.include("update_github_proxy", settings));
        assertTrue(Backup.include("update_github_proxy_url", settings));
        assertTrue(Backup.include("update_github_proxy_mode", settings));
        assertTrue(Backup.include("update_oci_mirror", settings));
        assertTrue(Backup.include("update_oci_mirror_url", settings));
        assertFalse(Backup.include("update_channel", settings));
        assertFalse(Backup.include("update_fallback", settings));
    }
}
