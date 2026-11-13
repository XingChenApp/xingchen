package com.example.hikerview.ui.rules.service;

import android.app.Activity;

/**
 * 官 {@code com.example.hikerview.ui.rules.service.RuleImporter} 接口照官转写
 * （官源 smali_classes2/com/example/hikerview/ui/rules/service/RuleImporter.smali，全 26 行，
 * 7 法签名逐字）。实现类（Hastebin/TxtPad/Notes/Bailan/Cmd/NetCut/Pastebin＋已弃用
 * Pasteme/Ubuntu/Pasteme2）与分发类 RuleImporterManager 本批停手，堵点见
 * worknotes/hk-14-fill-parts/c1.md（各 Importer 赖官 HttpHelper/DohProvidersKt/
 * CharsetStringConvert/CodeUtil/ClipboardUtil 本树皆无落点）。
 */
public interface RuleImporter {
    boolean canParse(String str);

    boolean canSetPwd();

    boolean canUseSync();

    void parse(Activity activity, String str, boolean z);

    String parseSync(String str);

    void share(Activity activity, String str, String str2, String str3, String str4);

    String shareSync(String str);
}
