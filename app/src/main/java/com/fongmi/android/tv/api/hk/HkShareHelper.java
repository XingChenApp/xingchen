package com.fongmi.android.tv.api.hk;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.fongmi.android.tv.utils.ToastUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 海阔规则导入导出助手：对接 8.83 全部本地格式。
 *
 * 支持的导入格式：
 * - 明文口令：…￥home_rule￥{json} / ￥home_rule_v2￥{json}（剪贴板文本）
 * - 裸 JSON：{...} 含 col_type 和 find_rule
 * - 文件：.json / .txt / .hiker（单规则 JSON 文本）
 * - 压缩包：.zip（多个 rule.json）/ .hkzip（rule.json + libs.zip + data.zip + require.json）
 * - base64 包装：￥…￥base64://@标题@BASE64
 *
 * 支持的导出格式：
 * - 明文口令：生成 ￥home_rule￥ 文本并复制到剪贴板（海阔官方可识别）
 * - 云剪贴板 1-7：上传到第三方服务，返回分享 URL（取件码）
 *
 * 云剪贴板映射（对照 8.83 RuleImporterManager#getImporter packed-switch 原文）：
 * 云1 Num1   → PastebinImporter  (pastebin.com)
 * 云2 Num2   → NetCutImporter     (netcut.cn / textdb.online)
 * 云5 Num5   → CmdImporter        (cmd.im)
 * 云6 Num6   → BailanImporter     (pasteme.tyrantg.com)
 * 云7 Num7   → NotesImporter      (note.ms)
 * 云9 Num9   → TxtPadImporter     (txtpad.cn)
 * 云10 Num10 → HastebinImporter   (hassdtebin.com / hastebin.com)
 * 云3、云4、云8 在 8.83 中无实现（getImporter 走 default 返回 null），不支持。
 * 提交云仓库依赖海阔官方服务端，未实现。
 */
public class HkShareHelper {

    /** 从剪贴板检测海阔分享口令，返回规则 JSON，无则返回 null。 */
    public static String detectFromClipboard(Context context) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return null;
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence text = clip.getItemAt(0).getText();
            if (text == null) return null;
            return HkRuleManager.unwrapHomeRuleCommand(text.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 生成明文口令并复制到剪贴板，返回口令文本。 */
    public static String shareAsCommand(Context context, String ruleJson) {
        String cmd = HkRuleManager.buildShareCommand(ruleJson);
        if (cmd == null) return null;
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) throw new IllegalStateException("ClipboardManager 不可用");
            cm.setPrimaryClip(ClipData.newPlainText("hk_rule", cmd));
        } catch (Exception e) {
            // 写剪贴板失败不许假装已复制：记日志、回 null，且唯一调用方
            // （HkListActivity 退回支）不消费返回值，故按本域既有提示形
            // （HkErrorUtil 复制失败 toast）直接显形（批 residual）。
            Log.e("HkShareHelper", "分享口令写入剪贴板失败", e);
            ToastUtil.show(context, "复制失败", Toast.LENGTH_SHORT);
            return null;
        }
        return cmd;
    }

    /** 从文本导入单条规则（自动识别 ￥home_rule￥/裸 JSON/base64 包装）。 */
    public static HkRule importFromText(String text) throws Exception {
        String json = HkRuleManager.unwrapHomeRuleCommand(text);
        if (json == null) throw new IllegalArgumentException("无法识别的规则文本");
        return HkRuleManager.get().importJson(json);
    }

    /** 从文件导入（按扩展名自动选择 .json/.txt/.hiker/.zip/.hkzip）。 */
    public static List<HkRule> importFromFile(File file) throws Exception {
        List<HkRule> result = new ArrayList<>();
        if (file == null || !file.exists()) throw new IllegalArgumentException("文件不存在");
        String name = file.getName().toLowerCase();
        HkRuleManager mgr = HkRuleManager.get();
        if (HkRuleManager.isCollectionPackage(file, name)) {
            // 小程序合集包（8.83 importPackageByZip，批C3 改官形）：只解压落盘到
            // _cache/<净名>.hk合集，本步零自动导入——条目由用户在合集浏览面逐个
            // 点选（HkPageActivity.openCollectionBrowser），故回空列表；
            // 判据含根 rule.json 内容验，名带「合集」的单个小程序包不误入（复核 M7）。
            mgr.extractCollectionPackage(file);
        } else if (name.endsWith(".hkzip") || name.endsWith(".zip")) {
            // .zip 与 .hkzip 同一套解析（parseHkZip：多 rule.json + require.json/libs.zip 附带资源）
            result.addAll(mgr.importHkZip(file));
        } else {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            String json = HkRuleManager.unwrapHomeRuleCommand(text);
            if (json != null) {
                result.add(mgr.importJson(json));
            } else {
                result.addAll(mgr.importJsonList(text));
            }
        }
        return result;
    }

    /** 上传到云剪贴板（8.83：1、2、5、6、7、9、10），返回分享 URL（取件码）。需在后台线程调用。 */
    public static String shareToCloudClip(String ruleJson, int clipNum) throws Exception {
        if (!HkCloudClip.supported(clipNum)) {
            throw new IllegalArgumentException("不支持的云剪贴板编号: " + clipNum);
        }
        return HkCloudClip.upload(ruleJson, clipNum);
    }

    /** 用取件码从云剪贴板下载，返回规则 JSON。需在后台线程调用。 */
    public static String downloadFromCloudClip(String code, int clipNum) throws Exception {
        if (!HkCloudClip.supported(clipNum)) {
            throw new IllegalArgumentException("不支持的云剪贴板编号: " + clipNum);
        }
        return HkCloudClip.download(code, clipNum);
    }

    /** 云剪贴板显示名（8.83：1、2、5、6、7、9、10）。 */
    public static String cloudClipName(int clipNum) {
        return HkCloudClip.name(clipNum);
    }
}
