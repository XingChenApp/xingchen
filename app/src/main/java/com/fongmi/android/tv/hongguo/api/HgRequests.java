package com.fongmi.android.tv.hongguo.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 红果请求构造（请求体部分）：取址链三体照 openhongguo internal/protocol/request.go
 * 的 PlaybackBody / SeriesBody / SeriesBatchBody 逐字口径（Go map 序列化＝键字典序，
 * 本类按同序插入 Gson 对象，签名覆盖 body MD5、发送字节与签名体一致）。
 *
 * <p>旧 multi 口径（mixed_video_id_map＋platform=1024＋content_type=1004）已在
 * openhongguo-dig-20261028.md 定罪废弃：无签名＋随机假指纹恒回 200 空体；
 * 取址链改走 HgDevice 注册身份＋HgSign 签名（见 HgApiClient.postSigned）。
 * 短剧短信登录链已复活（passport 段见下，登录走 HgDevice 注册档案＋
 * HgSign 签名，见 HgPassportClient）。旧 freshQuery（每次请求现编
 * 随机指纹查询串）随其唯一消费方 HgApiClient.postJson/get 一并摘除
 * （簇十五 A-08：全树零活调用；类注旧称「书城在用」经全树复核不成立）。</p>
 */
public final class HgRequests {

    public static final int VIDEO_PLATFORM_FQ = 3;

    public static final String UA =
            "com.phoenix.read/73932 (Linux; U; Android 14; zh_CN; 2509FPN0BC; Build/UQ1A.240205.09211156;tt-ok/3.12.13.20)";

    private HgRequests() {
    }

    // ---------- passport 短信登录（样本实证，挖掘账 passport-login-dig.md） ----------

    /**
     * passport 宿主：App 侧配置实现 com.dragon.read.base.w.host() 的 const-string
     * 定版 "security.snssdk.com"（注册点 app.launch.task.s3.k）；路径由
     * zy0/d.c 拼 https:// + host + path。不是 passport.snssdk.com。
     */
    public static final String PASSPORT_HOST = "https://security.snssdk.com";

    /**
     * 发码 type 的登录场景值：passport SDK 内 impl/c.t 的便捷方法硬编码 24；
     * impl/c.r 是 type 透传形态，龙族登录页最终传值未追到底（UI 不走 zy0/a
     * 门面）——先用 24，真机回包校准（见挖掘账未定版第 1 条）。
     */
    public static final int SEND_CODE_TYPE_SMS_LOGIN = 24;

    /**
     * passport 字段混淆 StringUtils.encryptWithXor（classes23.dex 本体逐指令）：
     * UTF-8 字节逐字节 XOR 0x05，再转十六进制串。
     */
    public static String xorHex(String s) {
        if (s == null || s.isEmpty()) return "";
        byte[] bytes;
        try {
            bytes = s.getBytes("UTF-8");
        } catch (Exception e) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = (b ^ 0x05) & 0xFF;
            sb.append(Character.forDigit(v >> 4, 16)).append(Character.forDigit(v & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * 发码表单 POST /passport/mobile/send_code/v1/（xz0/g.H 组参逐字段）：
     * mobile/type 走 xor 混淆、mix_mode=1、check_register=1（查注册态，
     * 登录场景官方口径）；captcha/shark_ticket 等风控回填字段被要求时才带，
     * 首次发码不带。公共参数由客户端另行拼到查询串（与官方网络层
     * NetworkParams.putCommonParams 并入同源一套）。
     */
    /**
     * 发码表单：维持现行 SDK 形（xor mobile＋type=24）——今晨真机
     * 实证此形码真到、断点只在登录步（审表）；登录对齐（2026-10-31）
     * 只动登录形与发码回包的会话件抓存（在 HgPassportClient），不拿
     * 已证步冒险。assistant 发码簇里的 auto_read 字段静态抠不到值，
     * 登记未抠到、不自创（见修复账）。
     */
    public static java.util.Map<String, String> sendCodeForm(String mobile) {
        java.util.Map<String, String> form = new LinkedHashMap<>();
        form.put("mobile", xorHex(mobile));
        form.put("type", xorHex(String.valueOf(SEND_CODE_TYPE_SMS_LOGIN)));
        form.put("mix_mode", "1");
        form.put("check_register", "1");
        return form;
    }

    /**
     * 短信登录表单 POST /passport/mobile/sms_login/（2026-10-31 登录
     * 对齐，照 assistant 链字段簇原文：mobile/code 明文、type=0、
     * unbind_exist）。unbind_exist 的值在静态材料里抠不到（同上），
     * 按布尔字段表单惯例取 "true"，是本表单头号未验变量（见修复账）。
     */
    public static java.util.Map<String, String> smsLoginForm(String mobile, String code) {
        java.util.Map<String, String> form = new LinkedHashMap<>();
        form.put("mobile", mobile);
        form.put("code", code);
        form.put("type", "0");
        form.put("unbind_exist", "true");
        form.put("mix_mode", "1");
        return form;
    }

                                        // ---------- bookmall 频道面（feed 三页签，openhongguo request.go 逐字口径） ----------

    /** 频道开页请求 tab_type：推荐走 -1（响应频道 16）；漫剧 36 / 真人剧 39 请求响应同值。 */
    public static final int TAB_REQUEST_RECOMMEND = -1;
    public static final int TAB_RESPONSE_RECOMMEND = 16;
    public static final int TAB_COMIC = 36;
    public static final int TAB_LIVE = 39;
        /** 短剧商城底栏（bottom_tab_type，三频道同值 7）。 */
    public static final String BOTTOM_TAB_SHORT_DRAMA = "7";
    public static final String BOTTOM_TAB_TYPE_LIST = "7,0,1,2,3,4";

    /**
     * 频道开页有序参数（openhongguo SignedDiscoverPairsIn 逐字）：
     * 设备公共参（31 对）＋ tab_type ＋ offset=0 ＋ client_req_type=3 ＋
     * bottom_tab_type ＋ bottom_tab_type_list。ts / _rticket 由签名器末尾追加。
     */
    public static java.util.List<String[]> discoverPairs(int requestTabType) {
        return discoverPairs(requestTabType, BOTTOM_TAB_SHORT_DRAMA);
    }

    /** 书频道变体：bottom_tab_type 用书城的 0（短剧是 7，规格 dig-audio-novel-source §3）。 */
    public static java.util.List<String[]> discoverPairs(int requestTabType, String bottomTabType) {
        java.util.List<String[]> pairs = HgDevice.deviceQueryPairs();
        pairs.add(new String[]{"tab_type", String.valueOf(requestTabType)});
        pairs.add(new String[]{"offset", "0"});
        pairs.add(new String[]{"client_req_type", "3"});
        pairs.add(new String[]{"bottom_tab_type", bottomTabType});
        pairs.add(new String[]{"bottom_tab_type_list", BOTTOM_TAB_TYPE_LIST});
        return pairs;
    }

    /**
     * 频道续页有序参数（openhongguo SignedCellChangePairs 逐字）：
     * 设备公共参 ＋ cell_id ＋ offset ＋ limit=20 ＋ change_type=0 ＋ tab_type
     * （响应频道值）＋ show_type ＋ client_req_type=2 ＋ session_id；
     * client_template 仅在开页回包带了才回传。
     */
    public static java.util.List<String[]> cellChangePairs(
            com.fongmi.android.tv.hongguo.model.HgContinuation cont) {
        java.util.List<String[]> pairs = HgDevice.deviceQueryPairs();
        pairs.add(new String[]{"cell_id", cont.getCellId()});
        pairs.add(new String[]{"offset", cont.getOffset()});
        pairs.add(new String[]{"limit", "20"});
        pairs.add(new String[]{"change_type", "0"});
        pairs.add(new String[]{"tab_type", cont.getTabType()});
        pairs.add(new String[]{"show_type", cont.getShowType()});
        pairs.add(new String[]{"client_req_type", "2"});
        pairs.add(new String[]{"session_id", cont.getSessionId()});
        if (cont.getClientTemplate() != null && !cont.getClientTemplate().isEmpty()) {
            pairs.add(new String[]{"client_template", cont.getClientTemplate()});
        }
        return pairs;
    }

    /**
     * 搜索结果有序参数（openhongguo SignedSearchPairs 逐字，tab=短剧 11）：
     * 设备公共参 ＋ query（参数名是 query 不是 keyword——旧 App 侧 keyword
     * 写法从未定版）＋ tab_type/count/offset ＋ use_correct ＋
     * bookshelf_search_plan ＋ search_source=1 ＋ tab_name=feed ＋
     * bookstore_tab=16 ＋ user_is_login=0。本机探针实证：此形签名 GET 回
     * 63,856 字节真结果；旧无签名＋假指纹＋keyword 形回 200 空体（与取模型
     * 口同一道签名门，2026-10-28 A/B 实证）。
     */
    public static java.util.List<String[]> searchPairs(String keyword) {
        java.util.List<String[]> pairs = HgDevice.searchQueryPairs();
        pairs.add(new String[]{"query", keyword == null ? "" : keyword});
        pairs.add(new String[]{"tab_type", "11"});
        pairs.add(new String[]{"count", "20"});
        pairs.add(new String[]{"offset", "0"});
        pairs.add(new String[]{"use_correct", "true"});
        pairs.add(new String[]{"bookshelf_search_plan", "4"});
        pairs.add(new String[]{"search_source", "1"});
        pairs.add(new String[]{"tab_name", "feed"});
        pairs.add(new String[]{"bookstore_tab", "16"});
        pairs.add(new String[]{"user_is_login", "0"});
        return pairs;
    }

    /** 换页签：同翻页口径再换 tab_type（回包 search tabs 的 tab_type 与请求同键）。 */
    public static java.util.List<String[]> searchPairs(String keyword, int offset, int tabType) {
        java.util.List<String[]> base = searchPairs(keyword, offset);
        java.util.List<String[]> out = new java.util.ArrayList<>(base.size());
        for (String[] kv : base) {
            out.add("tab_type".equals(kv[0])
                    ? new String[]{"tab_type", String.valueOf(tabType)} : kv);
        }
        return out;
    }

    /** 翻页：同 searchPairs、offset 换成已取条数（openhongguo SearchOffset 口径）。 */
    public static java.util.List<String[]> searchPairs(String keyword, int offset) {
        java.util.List<String[]> base = searchPairs(keyword);
        if (offset <= 0) return base;
        java.util.List<String[]> out = new java.util.ArrayList<>(base.size());
        for (String[] kv : base) {
            out.add("offset".equals(kv[0])
                    ? new String[]{"offset", String.valueOf(offset)} : kv);
        }
        return out;
    }
}
