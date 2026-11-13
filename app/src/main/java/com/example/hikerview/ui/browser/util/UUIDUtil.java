package com.example.hikerview.ui.browser.util;

import java.util.UUID;

/**
 * 照官逐字转写（官包 smali_classes2/com/example/hikerview/ui/browser/util/
 * UUIDUtil.smali 全文）：嗅探栈一员——DetectUrlUtil.detectVideoComplex
 * 以 genUUID 作嗅探结果的 fileName，随④团转写。
 */
public class UUIDUtil {

    public static String genUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
}
