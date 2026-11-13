package com.example.hikerview.ui.download;

/**
 * 照官 8.83 本体按需转写（官源 smali_classes2/com/example/hikerview/ui/download/DownloadConfig.smali）。
 * 本批只落带初值的静态字段面（DownloadTask 构造读 rootPath），初值逐个照官 clinit；
 * 官 init(Context)（从 PreferenceMgr 载入各值）与 updateDownloadRootPath 未落、记名在案。
 */
public class DownloadConfig {

    public static boolean autoMerge = true;
    public static int defaultMaxConcurrentTask = 2;
    public static String defaultRootPath = "Download";
    public static int downloadSubFileRetryCountOnFail = 50;
    public static int m3U8DownloadSizeDetectRetryCountOnFail = 20;
    public static int m3U8DownloadThreadNum = 20;
    public static int maxConcurrentTask = 2;
    public static int normalFileDownloadThreadNum = 6;
    public static int normalFileHeaderCheckRetryCountOnFail = 20;
    public static long normalFileSplitSize = -1L;
    public static String rootPath = "Download";
    public static boolean smartFilm = true;
    public static int videoSnifferRetryCountOnFail = 1;
    public static int videoSnifferThreadNum = 5;
}
