package com.fongmi.android.tv.api.hk;

/**
 * 海阔图片 @js= 求值时的"当前规则"信息持有者。
 *
 * <p>W3 之后图片求值走 {@link HkJsRuntime#imageEngine()} 进程单例（8.83
 * {@code JSEngine.getInstance()} 同款），不再用规则运行时；这里只保留当前规则
 * 标题，供单例求值时做 MY_TITLE 回退（{@code $.hiker.MY_TITLE} 提取不到时用）。</p>
 */
public class HkImage {

    private static volatile HkJsRuntime currentRt;
    private static volatile String currentTitle = "";

    public static void setCurrentRuntime(HkJsRuntime rt) {
        currentRt = rt;
    }

    /** 当前规则的 JS 运行时（历史兼容保留；图片求值已改走单例，不再依赖它）。 */
    public static HkJsRuntime currentRuntime() {
        return currentRt;
    }

    /** 登记当前规则标题（HkEngine 创建/包裹运行时时调用）。 */
    public static void setCurrentTitle(String title) {
        currentTitle = title == null ? "" : title;
    }

    /** 当前规则标题；未登记时返回空串（调用方此时不拼 MY_TITLE）。 */
    public static String currentTitle() {
        return currentTitle;
    }
}
