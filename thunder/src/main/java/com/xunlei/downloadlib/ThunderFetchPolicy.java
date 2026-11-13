package com.xunlei.downloadlib;

/**
 * 磁力 fetch 流程的纯判定（自 app 侧 Thunder 机械抽出）：只吃已取好的纯数据
 * （原生状态、已下字节、任务存活标志、inflight 存在标志），吐判定结果；
 * 不摸网关、原生与落盘，动作本体（建/停/删任务、取本地地址、等结果）仍在
 * Thunder 侧原样执行。各方法的条件、顺序与返回值与抽出前 Thunder 内的
 * 原判定逐字等价，基线 60155c55 的防护口径不拆。
 *
 * 原生状态口径：0 等待 / 1 下载中 / 2 已完成 / 3 出错 / -1 读不到。
 */
public final class ThunderFetchPolicy {

    private ThunderFetchPolicy() {
    }

    /** fetchInternal 同链接复用裁决：复用交址 / 停删重建 / 不处理往下走。 */
    public enum InternalAction {
        REUSE,
        CLEANUP_REBUILD,
        PROCEED
    }

    /** 直建路单轮裁决：交地址 / 抛任务出错 / 继续等。 */
    public enum DirectRound {
        HAND_OFF,
        FAIL_ERROR,
        WAIT
    }

    /** BT 路单轮裁决：交地址 / 继续等 / 父任务出错 / 子任务信息为空 / 子任务出错。 */
    public enum BtRound {
        HAND_OFF,
        WAIT,
        FAIL_PARENT,
        FAIL_SUB_EMPTY,
        FAIL_SUB
    }

    /** 活任务口径（复用与 stop 保护共用）：原生状态 0/1/2 才算活；-1 读不到不算。 */
    public static boolean isAliveStatus(int status) {
        return status == 0 || status == 1 || status == 2;
    }

    /** 已有字节口径：引擎计数或落盘长度任一 > 0 即算有字节。 */
    public static boolean hasBytes(long downloadedSize, long onDiskSize) {
        return downloadedSize > 0 || onDiskSize > 0;
    }

    /**
     * fetch 入口复用（原 Thunder.fetch 入口条件）：同内容、已有缓存地址、
     * 任务在且活着、无零字节标记、已有字节，五项全立才复用。
     */
    public static boolean shouldReuseAtEntry(boolean sameContent, boolean hasLocal, boolean taskPresent, int nativeStatus, boolean zeroMarked, boolean hasBytes) {
        return sameContent && hasLocal && taskPresent && isAliveStatus(nativeStatus) && !zeroMarked && hasBytes;
    }

    /**
     * 状态 3 纠正（原 Thunder.fetchInternal 首段）：状态单次读数偶发误报 3，
     * 任务已下出字节且没被零字节监控记账时以字节为准，3 改判 1；其余原样。
     */
    public static int correctErrorStatus(int status, boolean hasBytes, boolean zeroMarked) {
        return status == 3 && hasBytes && !zeroMarked ? 1 : status;
    }

    /**
     * fetchInternal 同链接复用裁决（原同链接复用块）：同内容、已有地址、任务
     * 在且状态 0/1/2 时，有字节复用、无字节停删重建；不满足则不处理往下走。
     */
    public static InternalAction internalAction(boolean sameContent, boolean hasLocal, boolean taskPresent, int nativeStatus, boolean hasBytes) {
        if (sameContent && hasLocal && taskPresent && isAliveStatus(nativeStatus)) {
            return hasBytes ? InternalAction.REUSE : InternalAction.CLEANUP_REBUILD;
        }
        return InternalAction.PROCEED;
    }

    /** 串行重试卫生（原 fetchInternal 状态 3 停删条件）：仅状态明确为 3 才停删。 */
    public static boolean shouldCleanupOnError(boolean taskPresent, int nativeStatus) {
        return taskPresent && nativeStatus == 3;
    }

    /** 换链接卫生（原 fetchInternal 换链接停删条件）：内容身份不同才停删旧任务。 */
    public static boolean shouldCleanupOnLinkChange(boolean taskPresent, boolean sameContent) {
        return taskPresent && !sameContent;
    }

    /** inflight 合流（原 fetch 合流判定）：同内容已有进行中的 fetch 即加入等待。 */
    public static boolean shouldJoinInflight(boolean inFlightPresent) {
        return inFlightPresent;
    }

    /**
     * stop() 活任务保护（原 stop 首段条件）：任务在、已交过地址、状态 0/1/2
     * 时不杀，清理推迟到下一次 fetch 与 exit。
     */
    public static boolean shouldProtectOnStop(boolean taskPresent, boolean hasLocal, int nativeStatus) {
        return taskPresent && hasLocal && isAliveStatus(nativeStatus);
    }

    /**
     * 直建路单轮裁决（原 waitDirectTask 轮内判定）：信息缺席继续等；状态 3
     * 抛错；状态非 0 且已有字节才交地址；其余继续等首字节。
     */
    public static DirectRound directRound(boolean infoPresent, int taskStatus, boolean hasBytes) {
        if (!infoPresent) return DirectRound.WAIT;
        if (taskStatus == 3) return DirectRound.FAIL_ERROR;
        if (taskStatus != 0 && hasBytes) return DirectRound.HAND_OFF;
        return DirectRound.WAIT;
    }

    /**
     * BT 路单轮裁决（原 waitTorrentTask 轮内判定）：父状态 3 先裁；子任务读
     * 失败本轮跳过继续等；子信息为空抛错；子状态 3 抛错；子状态非 0 即交
     * 地址（不等字节，上游口径）；子状态 0 继续等。父状态读不到传 -1。
     */
    public static BtRound btRound(int parentStatus, boolean btReadFailed, boolean subPresent, int subStatus) {
        if (parentStatus == 3) return BtRound.FAIL_PARENT;
        if (btReadFailed) return BtRound.WAIT;
        if (!subPresent) return BtRound.FAIL_SUB_EMPTY;
        if (subStatus == 3) return BtRound.FAIL_SUB;
        if (subStatus != 0) return BtRound.HAND_OFF;
        return BtRound.WAIT;
    }
}
