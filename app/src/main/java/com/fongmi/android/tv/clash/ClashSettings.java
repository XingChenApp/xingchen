package com.fongmi.android.tv.clash;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
* 节点开关与相关配置的持久化。
* <p>
*
* 节点开关是长期设置，不是本次会话的临时动作：
* <ul>
* <li>用户开着开关退出星辰，下次打开星辰时开关仍是开的，内核随 App 启动按保存状态自动拉起；
* <li>关着则不启。
* </ul>
* 落实点：
* <ul>
* <li>{@link #setEnabled} 立刻写盘（apply），进程被杀也不丢；
* <li>{@link MihomoManager#bootRestore} 在 App 启动后台流程里读 {@link #isEnabled} 做状态恢复；
* <li>恢复时若内核未下载 / 无可用节点 / 配置生成失败，必须静默降级为直连并把开关置回关，
* 不抛、不弹、不卡启动。
* </ul>
*/
public final class ClashSettings {

private static final String PREFS = "clash_meta";

private static final String KEY_ENABLED = "node_switch_enabled";
private static final String KEY_SUB_URL = "sub_url";
private static final String KEY_SUB_SOURCE = "sub_source";
private static final String KEY_SUB_LOCAL_URIS = "sub_local_uris";
private static final String KEY_MIXED_PORT = "mixed_port";
private static final String KEY_SELECTED_NODE = "selected_node";
private static final String KEY_KERNEL_VERSION = "kernel_version";
private static final String KEY_GLOBAL_MODE = "global_mode";

/** 默认本地混合端口（HTTP 与 SOCKS5 同口，壳子现有代理机制只认这两种地址格式）。 */
public static final int DEFAULT_MIXED_PORT = 7890;

private ClashSettings() {
}

private static SharedPreferences prefs(Context context) {
return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
}

/** 节点开关：持久化，App 重启后保持。 */
public static boolean isEnabled(Context context) {
return prefs(context).getBoolean(KEY_ENABLED, false);
}

/** 用户拨开关时调用：立刻持久化，再由调用方启停内核。 */
public static void setEnabled(Context context, boolean enabled) {
prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
}

/** 订阅来源：网址订阅。 */
public static final String SUB_SOURCE_URL = "url";
/** 订阅来源：本地文件订阅。 */
public static final String SUB_SOURCE_LOCAL = "local";

public static String getSubUrl(Context context) {
return prefs(context).getString(KEY_SUB_URL, "");
}

public static void setSubUrl(Context context, String url) {
prefs(context).edit().putString(KEY_SUB_URL, url == null? "": url.trim()).apply();
}

/** 订阅当前是本地文件模式。 */
public static boolean isSubLocal(Context context) {
return SUB_SOURCE_LOCAL.equals(prefs(context).getString(KEY_SUB_SOURCE, SUB_SOURCE_URL));
}

/** 本地模式下记住的订阅文件 Uri（字符串形式，按选入顺序）。 */
public static List<String> getSubLocalUris(Context context) {
String raw = prefs(context).getString(KEY_SUB_LOCAL_URIS, "");
List<String> list = new ArrayList<>();
for (String s : raw.split("\n")) {
if (s != null && !s.trim().isEmpty()) list.add(s.trim());
}
return list;
}

/** 切到本地订阅：记住一个或多个文件的 Uri，下次刷新重读它们。 */
public static void setSubLocal(Context context, List<String> uriStrings) {
StringBuilder sb = new StringBuilder();
for (String s : uriStrings) {
if (s == null || s.isEmpty()) continue;
if (sb.length() > 0) sb.append('\n');
sb.append(s);
}
prefs(context).edit().putString(KEY_SUB_SOURCE, SUB_SOURCE_LOCAL).putString(KEY_SUB_LOCAL_URIS, sb.toString()).apply();
}

/** 切回网址订阅：清掉本地文件记录。 */
public static void setSubUrlSource(Context context) {
prefs(context).edit().putString(KEY_SUB_SOURCE, SUB_SOURCE_URL).remove(KEY_SUB_LOCAL_URIS).apply();
}

public static int getMixedPort(Context context) {
int p = prefs(context).getInt(KEY_MIXED_PORT, DEFAULT_MIXED_PORT);
return (p > 0 && p <= 65535)? p: DEFAULT_MIXED_PORT;
}

public static void setMixedPort(Context context, int port) {
if (port <= 0 || port > 65535) return;
prefs(context).edit().putInt(KEY_MIXED_PORT, port).apply();
}

/** 当前选中的节点 id（NodeStore.Node.id），空表示未选。 */
public static String getSelectedNode(Context context) {
return prefs(context).getString(KEY_SELECTED_NODE, "");
}

public static void setSelectedNode(Context context, String nodeId) {
prefs(context).edit().putString(KEY_SELECTED_NODE, nodeId == null? "": nodeId).apply();
}

/** 全部走节点：无源归属的流量也走节点（默认关）。 */
public static boolean isGlobalMode(Context context) {
return prefs(context).getBoolean(KEY_GLOBAL_MODE, false);
}

public static void setGlobalMode(Context context, boolean on) {
prefs(context).edit().putBoolean(KEY_GLOBAL_MODE, on).apply();
}

/** 已下载内核的版本号（KernelDownloader 写入，用于将来升级判断）。 */
public static String getKernelVersion(Context context) {
return prefs(context).getString(KEY_KERNEL_VERSION, "");
}

public static void setKernelVersion(Context context, String version) {
prefs(context).edit().putString(KEY_KERNEL_VERSION, version == null? "": version).apply();
}

/** 供本地口地址拼装：http://127.0.0.1:port（壳子代理机制可直接用）。 */
public static String localProxyUrl(Context context) {
return "http://127.0.0.1:" + getMixedPort(context);
}
}
