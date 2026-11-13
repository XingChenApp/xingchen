package com.fongmi.android.tv.cloud;

import java.util.List;

/**
 * 网盘统一接口（115 / 夸克 / PikPak）
 * 来源：115_tvbox_4_mx3q.py 的 Api 类
 */
public interface CloudDrive {

    /** 网盘标识：115 / quark / pikpak */
    String getId();

    /** 显示名 */
    String getName();

    /** 是否已登录 */
    boolean isLogin();

    /** 当前用户名 */
    String getUserName();

    /** 退出登录 */
    void logout();

    /** 文件列表 */
    List<CloudFile> listDir(String cid, int offset, int limit) throws Exception;

    /** 文件总数 */
    int getTotal(String cid) throws Exception;

    /** 删除文件（fid 或 cid 列表） */
    boolean delete(List<String> ids, boolean isDir) throws Exception;

    /** 离线下载：磁力/ed2k/http → 云端，返回任务是否提交成功 */
    boolean offlineDownload(String url, String cid) throws Exception;

    /** 离线任务列表 */
    List<CloudTask> taskList(int page) throws Exception;

    /** 删除离线任务 */
    boolean taskDelete(List<String> hashes) throws Exception;

    /** 取播放地址（原画直链或 m3u8） */
    String getPlayUrl(CloudFile file) throws Exception;

    /**
     * 全盘搜索：按关键词搜自己网盘里的文件，offset/limit 分页。
     * 默认未实现（抛人话异常）；各家按自家已验证口径覆盖，115 已实现。
     * 结果项须回填 parentCid（所在目录 id），供 UI 从结果直达所在目录。
     */
    default List<CloudFile> searchFiles(String keyword, int offset, int limit) throws Exception {
        throw new Exception("当前网盘暂不支持搜索");
    }

    /**
     * 转存分享链接到自己网盘
     * @param shareUrl 分享链接
     * @param password 提取码（可空）
     * @param toCid 目标目录 cid（"0"=根目录）
     * @return 是否成功
     */
    default boolean saveShare(String shareUrl, String password, String toCid) throws Exception {
        throw new Exception("当前网盘暂不支持分享转存");
    }

    /** 新建文件夹，返回新建的文件夹对象 */
    default CloudFile createFolder(String parentId, String name) throws Exception {
        throw new Exception("当前网盘暂不支持新建文件夹");
    }

    /**
     * 存储容量摘要（如 "已用 12GB / 共 100GB"），供 UI 顶部显示
     * 默认空字符串（未实现时 UI 只显示会员状态）
     */
    default String storageSummary() throws Exception {
        return "";
    }
}
