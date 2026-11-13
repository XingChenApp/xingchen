package com.fongmi.android.tv.player.danmaku.live.douyin;

import com.fongmi.quickjs.method.Console;
import com.github.catvod.utils.Asset;
import com.whl.quickjs.android.QuickJSLoader;
import com.whl.quickjs.wrapper.QuickJSContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 抖音弹幕 wss 的 signature 生成，照 dart_simple_live 的 DouyinSign.getSignature 对译：
 * 先把固定参数串（live_id,aid,version_code,webcast_sdk_version,room_id,sub_room_id,
 * sub_channel_id,did_rule,user_unique_id,device_platform,device_type,ac,identity 顺序拼接）
 * 做 MD5 得 X-MS-STUB，再把 assets 里的 webmssdk 本体（js/lib/webmssdk.js，
 * 与参照实现内嵌的 kWebMsSDK 同一份字节）装进 QuickJS，调 getMSSDKSignature(stub, UA)
 * 取 X-Bogus 形态的签名串；结果含 '-' 或 '=' 时按参照口径重算（有上限防死循环）。
 * 注意：这是弹幕连接签名，与直播源取流用的 a_bogus 是两套东西，不许混用。
 */
public final class DouyinMssdkSigner implements DouyinProvider.Signer {

    private static final String MSSDK_ASSET = "js/lib/webmssdk.js";
    private static final int MAX_SIGN_ATTEMPTS = 50;
    private static volatile boolean loaderReady;

    @Override
    public String sign(String roomId, String userUniqueId) throws Exception {
        String stub = msStub(roomId, userUniqueId);
        ensureLoader();
        QuickJSContext context = QuickJSContext.create();
        try {
            context.setConsole(new Console());
            context.evaluate(Asset.read(MSSDK_ASSET));
            String signature = null;
            for (int attempt = 0; attempt < MAX_SIGN_ATTEMPTS; attempt++) {
                Object result = context.evaluate("getMSSDKSignature('" + stub + "','" + DouyinProvider.USER_AGENT + "')");
                signature = result == null ? null : String.valueOf(result);
                if (signature != null && !signature.contains("-") && !signature.contains("=")) return signature;
            }
            throw new IllegalStateException("mssdk signature keeps url-unsafe chars");
        } finally {
            context.destroy();
        }
    }

    private static void ensureLoader() {
        if (loaderReady) return;
        synchronized (DouyinMssdkSigner.class) {
            if (!loaderReady) {
                QuickJSLoader.init();
                loaderReady = true;
            }
        }
    }

    /** 签名参数串的 MD5（小写十六进制），拼接顺序与值照参照实现 getMsStub 一字不差。 */
    static String msStub(String roomId, String userUniqueId) throws Exception {
        String params = "live_id=1"
                + ",aid=6383"
                + ",version_code=180800"
                + ",webcast_sdk_version=1.3.0"
                + ",room_id=" + roomId
                + ",sub_room_id="
                + ",sub_channel_id="
                + ",did_rule=3"
                + ",user_unique_id=" + userUniqueId
                + ",device_platform=web"
                + ",device_type="
                + ",ac="
                + ",identity=audience";
        byte[] digest = MessageDigest.getInstance("MD5").digest(params.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return hex.toString();
    }
}
