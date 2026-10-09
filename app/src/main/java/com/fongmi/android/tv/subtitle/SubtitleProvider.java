package com.fongmi.android.tv.subtitle;

import java.util.List;

/**
 * 字幕源接口：按片名搜索字幕。
 * 实现类必须保证 search() 不在主线程调用（由 SubtitleManager 统一开后台线程）。
 */
public interface SubtitleProvider {

    /** 源 ID：opensubtitles / subhd / shooter / zimuku（与 SubtitleSetting 的开关 key 对应） */
    String getId();

    /** 显示名称 */
    String getName();

    /** 是否可用：开关打开 + 必需的配置（如 API Key）已填 */
    boolean isAvailable();

    /**
     * 按片名搜索字幕。
     * @param query 片名（中文或英文）
     * @return 字幕列表（可能为空）；网络或解析失败时返回空列表，不要抛异常
     */
    List<SubtitleInfo> search(String query);
}
