package com.fongmi.android.tv.api;

import com.github.catvod.utils.Prefers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 全局匿名设备档案的中性读桥（§六十六）。
 *
 * <p>档案本体由红果包内的设备类持有（注册、换脸、回填都在那
 * 边），但点播评论链这类共用件在红果包外，又不许直引红果包
 * （朋友版剥离终检全树禁红果包名）。本桥直读同一份 Prefers
 * 档案落盘：同一键、同一 JSON 字段、同一回退口径——档案没
 * 有/未注册/形状不对，一律空串，不在此另编新号。</p>
 *
 * <p>解析口径与档案本体的 load() 逐项镜像：cdid/openudid 任
 * 一缺失或非字符串即整份无效（返回空串）；device_id/install_id
 * 只认字符串原值。档案本体的每次落盘（注册回填、重置、迁移）
 * 都整份写回本键，故桥读落盘与本体内存缓存恒同源同值。</p>
 */
public final class DeviceFaceBridge {

    /** 档案落盘键（与档案本体自持的键同值，桥内自持一份、防直引本体类）。 */
    private static final String PROFILE_KEY = "hongguo_device_profile_v1";

    private DeviceFaceBridge() {
    }

    /** 签名/公参用的 device_id（自家注册档案）；未注册为空串。 */
    public static String deviceId() {
        return readField("device_id");
    }

    /** 签名/公参用的 install_id（iid，与 deviceId 同一张脸）；未注册为空串。 */
    public static String installId() {
        return readField("install_id");
    }

    private static String readField(String field) {
        try {
            String raw = Prefers.getString(PROFILE_KEY);
            if (raw == null || raw.isEmpty()) return "";
            JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
            if (str(o, "cdid") == null || str(o, "openudid") == null) return "";
            String v = str(o, field);
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    /** 与档案本体 load() 的 str() 同口径：只认 JSON 基本类型、取其字符串形。 */
    private static String str(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }
}
