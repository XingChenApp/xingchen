package com.fongmi.android.tv.hongguo.api;

import android.content.res.Resources;
import android.os.Build;
import android.util.DisplayMetrics;

import com.github.catvod.utils.Prefers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fongmi.android.tv.hongguo.sign.HgSign;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * 匿名设备身份与注册（移植 openhongguo internal/protocol/register.go、tt_encrypt.go、
 * request.go DeviceQueryPairs、sdk/device.go 身份派生）。
 *
 * <p>定罪依据 openhongguo-dig-20261028.md §3/§4：旧链每次请求随机编造 iid/device_id
 * 且无签名，回包恒 200 空体；正路是先 device_register 拿真 device_id/iid 落盘复用，
 * 之后所有取址请求带注册身份＋有序查询串＋签名。本类只管身份与注册报文，
 * 网络发送在 HgApiClient。</p>
 *
 * <p>身份持久化走 Prefers（键 hongguo_device_profile_v1），注册一次全局复用。</p>
 *
 * <p>共享档案呈现与永久锁存已于 2026-10-30 短剧重建时整件摘除；
 * 其后短剧新层另建的 DramaSign 共享呈现＋策略冷却切换，亦已于
 * 2026-10-31 按 W-03 裁决整套摘除（账 w03-dramasign-removal-
 * 20261031）。现行全 App 只有自家注册档案一张脸，本类只剩自家
 * 注册档案一职（小说共用）。</p>
 */
public final class HgDevice {

    public static final String PACKAGE = "com.phoenix.read";
    public static final String VERSION_NAME = "7.3.9.32";
    public static final String VERSION_CODE = "73932";
    public static final int AID = 8662;
    public static final String APP_NAME = "novelread";
    public static final String DEVICE_PLATFORM = "android";
    public static final String API_HOST = "https://api5-normal-sinfonlinea.fqnovel.com";
    /** 批量取模型口宿主（sinfonlineb；红果果[短]源码与定根探针 Q3/Q4 双证，账 hongguo-fetch-quota-dig2-20261030 §三/§四）。 */
    public static final String API_HOST_B = "https://api5-normal-sinfonlineb.fqnovel.com";
    public static final String REGISTER_HOST = "https://log5-applog-lq.fqnovel.com";
    public static final String PATH_PLAYBACK = "/novel/player/video_model/v1/";
    /** 签名批量取模型口（配额按成功请求计、一发兑整批，定根账同上 §四-3）。 */
    public static final String PATH_PLAYBACK_MULTI = "/novel/player/multi_video_model/v1/";
    public static final String PATH_SERIES = "/novel/player/video_detail/v1/";
    public static final String PATH_ALBUM = "/novel/player/album_detail/v1/";
    public static final String PATH_SERIES_BATCH = "/novel/player/multi_video_detail/player/v1";
    /** bookmall 频道开页（尾段是字面 v 不是 v1，openhongguo app.go PathDiscover 实证）。 */
    public static final String PATH_BOOKMALL_TAB = "/reading/bookapi/bookmall/tab/v";
    /** bookmall 频道续页（openhongguo app.go PathCellChange 实证）。 */
    public static final String PATH_BOOKMALL_CELL_CHANGE = "/reading/bookapi/bookmall/cell/change/v";
    /** plan 通用 feed 口（尾斜杠、字面 v1，openhongguo app.go PathPlan 实证）：
     * 搜索落地页热搜面 from=search_input_page 走此口（账
     * hongguo-hotsearch-dig-20261030 §1.1，官方模板 a45/c 的
     * $GET /reading/bookapi/plan/v:version/ 互证）。 */
    public static final String PATH_PLAN = "/reading/bookapi/plan/v1/";
    /**
     * 短剧筛选面（distribution 族分类浏览口）：官方筛选面板（主题/设定/
     * 背景等维度表）与带筛选取列表共用此口（openhongguo PathShortFilter
     * 线协议实证，探针账 hongguo-chip-probe-20261030：两面实拉全通，
     * 内容类型纯）。只在 API_HOST——distribution 族在 reading.snssdk.com
     * 宿主 404，hostFor 默认回 API_HOST 正合此口径。
     */
    public static final String PATH_DISTRIBUTION_FILTER = "/reading/distribution/category/landpage/v1/";

    // ---------- 设备 persona（档案随身携带，openhongguo sdk/model.Device 同构） ----------
    //
    // 2026-10-29 根治（hongguo-profile-rootfix-20261029.md）：video_model 的
    // 配额按档案计、合成档案被压进深罚区（feed-fail-1460-20261029.md 定根）。
    // 此前全网每份档案共用同一台设备描述＋同一套 medusa 抓样指纹（传感器
    // 浮点/token/环境哈希逐位相同）——真机群不可能共享这些值，这是合成
    // 设备农场的一眼特征。当时改为：persona 在档案生成时从已证真机元组池里
    // 掷定、随档案落盘、终身稳定；注册体/公参/UA/medusa 四处都经下面的
    // 访问口现取档案 persona，不许再各写一份（medusa 对齐口径见
    // login-medusa-align-20261029.md）。
    //
    // 2026-10-31 签名收口再进一步（hongguo-signer-map-20261031.md 第三节）：
    // 原生 metasec 的 medusa 设备快照是 SDK 在宿主真机上自采的，档案再报
    // 池里别人的机型，一发之内就是两台设备。persona 就此改用宿主真机
    // 实值（HOST_PERSONA：Build 品牌/型号/构建/系统版本＋系统实测分辨
    // 率/dpi），注册、公参、仿签内嵌、原生自采四处同一个设备；元组池
    // 退役。存量档案是按旧 persona 注册的，不许在旧脸上换口径——升级
    // 后首取档案走一次性换脸重注册（swapToRealDevicePersonaOnce，新标
    // 记与 1.4.66 的 hongguo_face_swap 分开立），换完的新脸自始即真机
    // 口径。更早的存量档案（无 persona 字段）迁移时仍钉 legacy
    // persona——它注册时报的就是这台，钉死才自洽（且正常路径下先被
    // 换脸重置，走不到迁移）。channel 不是设备属性、是应用分发渠道串
    // （抓样实值 xiaomi_8662_64），与机型无关，保持原值不随真机变。
    private static final class Persona {
        final String brand;
        final String model;
        final String build;
        final String osVersion;
        final int osApi;
        final String resolution;
        final int dpi;
        final String channel;

        Persona(String brand, String model, String build, String osVersion, int osApi,
                String resolution, int dpi, String channel) {
            this.brand = brand;
            this.model = model;
            this.build = build;
            this.osVersion = osVersion;
            this.osApi = osApi;
            this.resolution = resolution;
            this.dpi = dpi;
            this.channel = channel;
        }
    }

    /** legacy：openhongguo sdk/model DefaultDevice（Xiaomi 23127PN0CC / Android 14）。
     * 只剩两职：档案字段缺位时的访问口兜底、与无 persona 存量档案的迁移钉值
     * ——这两处要的都是「该脸注册时报的那台」，不许换成真机值（见上方口径注）。 */
    private static final Persona PERSONA_LEGACY = new Persona(
            "Xiaomi", "23127PN0CC", "UKQ1.230804.001", "14", 34, "1200*2670", 460, "xiaomi_8662_64");

    /** 应用分发渠道串（抓样实值；非设备属性，不随宿主真机变，见上方口径注）。 */
    private static final String CHANNEL_APP = "xiaomi_8662_64";

    /** 宿主真机 persona：新档案生成时套用（2026-10-31 收口，见上方口径注）。 */
    private static final Persona HOST_PERSONA = buildHostPersona();

    private static Persona buildHostPersona() {
        String resolution = "";
        int dpi = 0;
        try {
            DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
            if (dm != null && dm.widthPixels > 0 && dm.heightPixels > 0) {
                resolution = dm.widthPixels + "*" + dm.heightPixels;
                dpi = dm.densityDpi;
            }
        } catch (Throwable ignored) {
            // 取不到实测显示参数时留空：注册体不带这两项、公参由档案空值
            // 透传——如实留空，不编造数值（类加载期偶发，正常真机不走此路）。
        }
        return new Persona(nz(Build.BRAND), nz(Build.MODEL), nz(Build.ID),
                nz(Build.VERSION.RELEASE), Build.VERSION.SDK_INT,
                resolution, dpi, CHANNEL_APP);
    }

    private static final String PREF_KEY = "hongguo_device_profile_v1";

    /** 一次性换脸标记键（1.4.66，见 swapBurnedFaceOnce）。 */
    private static final String FACE_SWAP_KEY = "hongguo_face_swap_20261031";

    /** 一次性换脸标记键（2026-10-31 签名收口，见 swapToRealDevicePersonaOnce；与 1.4.66 标记分开立）。 */
    private static final String FACE_SWAP_REALDEV_KEY = "hongguo_face_swap_realdev_20261031";

    /** 签名用的 device_id（自家注册档案）。 */
    public static String signDeviceId() {
        return nz(profile().deviceId);
    }

    /** 签名用的 channel（MSConfig.setChannel 与公参 channel 同源同值，档案 persona 的 channel）。 */
    public static String signChannel() {
        return deviceChannel();
    }

    /** medusa 厂商位（字段 34）：注册体 device_manufacturer 口径＝品牌。 */
    public static String deviceManufacturer() {
        return deviceBrand();
    }

    /** medusa 字段 25–28 的时间戳基：签名时刻入参。 */
    public static long medusaTsBase(long nowMillis) {
        return nowMillis;
    }

    /** 搜索面设备查询串（自家档案口径）。 */
    public static List<String[]> searchQueryPairs() {
        return ownQueryPairs();
    }

    private HgDevice() {
    }

    /** 注册档案的机型（注册体 device_model、公参 device_type 与 medusa 机型字段同源）。 */
    public static String deviceModel() {
        String v = profile().model;
        return v == null || v.isEmpty() ? PERSONA_LEGACY.model : v;
    }

    /** 注册档案的品牌（注册体 device_brand 与 device_manufacturer 同用此值，medusa 品牌/厂商字段同源）。 */
    public static String deviceBrand() {
        String v = profile().brand;
        return v == null || v.isEmpty() ? PERSONA_LEGACY.brand : v;
    }

    /** 注册档案的系统版本（注册体/公参 os_version 与 UA 同值，medusa 系统版本字段同源）。 */
    public static String deviceOsVersion() {
        String v = profile().osVersion;
        return v == null || v.isEmpty() ? PERSONA_LEGACY.osVersion : v;
    }

    /** 注册档案的系统 API 号（公参/注册体 os_api 同值，medusa 字段 24 同源，证据见 HgSign.deviceProto 注释）。 */
    public static int deviceOsApi() {
        int v = profile().osApi;
        return v > 0 ? v : PERSONA_LEGACY.osApi;
    }

    private static String deviceBuild() {
        String v = profile().build;
        return v == null || v.isEmpty() ? PERSONA_LEGACY.build : v;
    }

    private static String deviceResolution() {
        String v = profile().resolution;
        return v == null || v.isEmpty() ? PERSONA_LEGACY.resolution : v;
    }

    private static int deviceDpi() {
        int v = profile().dpi;
        return v > 0 ? v : PERSONA_LEGACY.dpi;
    }

    private static String deviceChannel() {
        String v = profile().channel;
        return v == null || v.isEmpty() ? PERSONA_LEGACY.channel : v;
    }

    /** 设备档案：本地派生标识＋注册回的真 id＋注册回包的游客 Cookie 材料
     * ＋随身 persona 与指纹种子（生成时掷定、落盘后终身稳定）。 */
    public static final class Profile {
        public String cdid;
        public String openUdid;
        public String clientUdid;
        public String oaid;
        public String reqId;
        public String deviceId;
        public String installId;
        // persona（档案注册时出示的那台手机；存量档案迁移钉 legacy，见类头注）
        public String brand = "";
        public String model = "";
        public String build = "";
        public String osVersion = "";
        public int osApi;
        public String resolution = "";
        public int dpi;
        public String channel = "";
        /** 指纹种子：64 位 hex（32 随机字节），medusa 指纹族按它派生（见 fpBlock）。 */
        public String fpSeed = "";

        void applyPersona(Persona p) {
            brand = p.brand;
            model = p.model;
            build = p.build;
            osVersion = p.osVersion;
            osApi = p.osApi;
            resolution = p.resolution;
            dpi = p.dpi;
            channel = p.channel;
        }

        boolean hasPersona() {
            return model != null && !model.isEmpty();
        }
        /** 注册回包下发的 device_token（请求头 x-tt-dt 的真值来源，见 login-ticket-dig-20261029.md）。 */
        public String deviceToken = "";
        public String ttReq = "";
        public String storeRegion = "";
        public String storeRegionSrc = "";

        public boolean registered() {
            return nonZero(deviceId) && nonZero(installId);
        }

        private static boolean nonZero(String s) {
            return s != null && !s.isEmpty() && !"0".equals(s);
        }
    }

    private static Profile cached;

    /**
     * 档案代次：每次重置 +1。注册请求发出时记下代次，回包落盘前比对——
     * 期间档案被重置过，那一轮注册回的 id/游客 Cookie 属于旧档案，作废
     * 不许回填到新档案上（HgApiClient.doRegister 据此判定）。
     */
    private static int generation;

    public static synchronized int profileGeneration() {
        return generation;
    }

    /**
     * 重置设备档案：整份匿名身份作废——本地派生标识（cdid/openudid/
     * clientudid/oaid/req_id）重新派生、指纹种子重掷，persona 套宿主真机实值，
     * 注册回的 device_id/install_id 与注册派生的游客 Cookie 材料
     * （ttreq/store-region）随旧档案一并清掉，
     * 落盘后即未注册态；重新注册走 HgApiClient.ensureDevice 既有流程。
     *
     * <p>边界（死线）：这里只动匿名设备档案这一份落盘（PREF_KEY）。
     * 账号登录态是另一回事——passport 会话 Cookie 由壳子账号托管
     * 不读不写（短剧重建后已无登录链）。</p>
     */
    public static synchronized void resetProfile() {
        generation++;
        // 抽屉「刷新设备档案」：重置自家档案（新种子重新派生）。
        Profile p = generate();
        save(p);
        cached = p;
    }

    /** 换脸标记已查旗（每进程只查一次盘）。 */
    private static boolean swapChecked;

    /**
     * 一次性换脸（1.4.66，他 2026-10-31 亲令「换一张新注册脸」）：旧版
     * 把自家注册脸的量烧秃了（预取无闸＋失败切共享死脸轮换），升级后
     * 第一次取档案先把旧档案整份作废、重新派生一张全新未注册的脸，
     * 随后既有 ensureDevice 流程负责注册。标记落盘保证终身只换这一次；
     * 只动设备档案这一份落盘，历史／收藏／配置一概不碰。
     */
    private static void swapBurnedFaceOnce() {
        if (swapChecked) return;
        swapChecked = true;
        try {
            if (Prefers.getString(FACE_SWAP_KEY).isEmpty()) {
                Prefers.put(FACE_SWAP_KEY, "1");
                resetProfile();
            }
        } catch (Throwable ignored) {
            // 换脸失败不拦业务：旧档案照常走既有流程。
        }
    }

    /** 真机口径换脸标记已查旗（每进程只查一次盘）。 */
    private static boolean realDevSwapChecked;

    /**
     * 一次性换脸·真机口径（2026-10-31 签名收口）：存量档案都是按旧
     * persona 池的机型注册的，口径切到宿主真机后，旧脸的注册描述与
     * 新口径对不上——不许在旧脸上混用。升级后第一次取档案先整份
     * 作废旧档案、按真机 persona 重新派生一张未注册的新脸，随后既有
     * ensureDevice 流程负责注册。本次重置同时完成 1.4.66 换脸的
     * 目的（旧脸作废换新脸），故旧标记一并落下、防紧接着二次重置；
     * 本检查排在 swapBurnedFaceOnce 之前。标记落盘保证终身只换这一
     * 次；只动设备档案这一份落盘，历史／收藏／配置一概不碰。
     */
    private static void swapToRealDevicePersonaOnce() {
        if (realDevSwapChecked) return;
        realDevSwapChecked = true;
        try {
            if (Prefers.getString(FACE_SWAP_REALDEV_KEY).isEmpty()) {
                Prefers.put(FACE_SWAP_REALDEV_KEY, "1");
                Prefers.put(FACE_SWAP_KEY, "1");
                resetProfile();
            }
        } catch (Throwable ignored) {
            // 换脸失败不拦业务：旧档案照常走既有流程。
        }
    }

    /** 取档案：没有就本地派生一份并落盘（device_id/install_id 待注册回填）。 */
    public static synchronized Profile profile() {
        if (cached != null) return cached;
        swapToRealDevicePersonaOnce();
        if (cached != null) return cached;
        swapBurnedFaceOnce();
        if (cached != null) return cached;
        Profile p = load();
        if (p == null) {
            p = generate();
            save(p);
        }
        cached = p;
        return p;
    }

    public static synchronized boolean isRegistered() {
        return profile().registered();
    }

    /** 清内存缓存（落盘内容不动）；下次 profile() 从盘上重读。 */
    public static synchronized void invalidateCache() {
        cached = null;
    }

    /** 注册成功回填真 id（并落盘，device_token 不动）；旧调用口（测试探针）保留。 */
    public static synchronized void saveRegistered(String deviceId, String installId) {
        saveRegistered(deviceId, installId, null);
    }

    /** 注册成功回填真 id 与 device_token（并落盘）；token 为空不覆写已有值。 */
    public static synchronized void saveRegistered(String deviceId, String installId, String deviceToken) {
        Profile p = profile();
        p.deviceId = deviceId;
        p.installId = installId;
        if (deviceToken != null && !deviceToken.isEmpty()) p.deviceToken = deviceToken;
        save(p);
    }

    /**
     * 注册回填的代次原子版：复核代次与落盘在同一把类锁内完成——注册
     * 请求在途时档案被重置（resetProfile 同锁 +1 代次），本轮回的旧 id
     * 绝无可能在「先查代次、后落盘」的缝里写进新档案（扫查 P2）。
     * 代次不符返回 false 且什么都不写，调用方按新档案重新注册。
     * device_token 随同一发回包落盘（官方口径：每轮注册回包重写、以
     * 最近一次为准）；回包没带 token 时不覆写已有值，防坏回包清凭证。
     */
    public static synchronized boolean saveRegisteredIfGeneration(int gen, String deviceId, String installId, String deviceToken) {
        if (generation != gen) return false;
        Profile p = profile();
        p.deviceId = deviceId;
        p.installId = installId;
        if (deviceToken != null && !deviceToken.isEmpty()) p.deviceToken = deviceToken;
        save(p);
        return true;
    }

    /** 档案现存的 device_token（x-tt-dt 填值口）；没刷到过时为空串。 */
    public static String deviceToken() {
        return nz(profile().deviceToken);
    }

    /**
     * 存量档案补刷判定：已注册但 device_token 为空——这类档案是旧版
     * 注册的（旧 parseRegister 把回包 token 扔了），要走一发同身份刷新
     * 注册把 token 捡回来（HgApiClient.ensureDevice 成功路径后静默触发）。
     */
    public static synchronized boolean needsDeviceToken() {
        Profile p = profile();
        return p.registered() && (p.deviceToken == null || p.deviceToken.isEmpty());
    }

    /** 游客 Cookie 落盘的代次原子版（同 saveRegisteredIfGeneration 口径）。 */
    public static synchronized boolean applyGuestCookiesIfGeneration(int gen, Map<String, String> cookies) {
        if (generation != gen) return false;
        applyGuestCookies(cookies);
        return true;
    }

    /** 注册回包 Set-Cookie 里的游客三件（ttreq/store-region/store-region-src）落盘。 */
    public static synchronized void applyGuestCookies(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) return;
        Profile p = profile();
        boolean dirty = false;
        String v;
        if ((v = cookies.get("ttreq")) != null && !v.isEmpty()) { p.ttReq = v; dirty = true; }
        if ((v = cookies.get("store-region")) != null && !v.isEmpty()) { p.storeRegion = v; dirty = true; }
        if ((v = cookies.get("store-region-src")) != null && !v.isEmpty()) { p.storeRegionSrc = v; dirty = true; }
        if (dirty) save(p);
    }

    /** 游客 Cookie 头（ttreq 非空才有，对照 sdk guestCookie 口径）；无则 null。 */
    public static String guestCookieHeader() {
        Profile p = profile();
        if (p.ttReq == null || p.ttReq.isEmpty()) return null;
        return "passport_csrf_token=; passport_csrf_token_default=; store-region=" + nz(p.storeRegion)
                + "; store-region-src=" + nz(p.storeRegionSrc)
                + "; install_id=" + nz(p.installId) + "; ttreq=" + p.ttReq;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ---------- medusa 指纹族派生（按档案种子，唯一且稳定） ----------
    //
    // 2026-10-29 根治（hongguo-profile-rootfix-20261029.md）：此前 deviceProto
    // 字段 5 的 token、字段 16–21 的传感器浮点、消息字段 16 的 token、消息
    // 字段 24 JSON 的 un_h/fkd/pd 全部冻结为 openhongguo 抓样那一台样机的
    // 原值——全网每份档案逐位相同，真机之间不可能共享（传感器校准读数与
    // 令牌是单机唯一的）。生成规则在官方 metasec 原生库里抠不到（登录线
    // login-close-loop-20261029.md 第四-2 节同结论），本派生不冒充官方
    // 算法，只保证三件事：同档案终身稳定（跨请求一致性画像自洽）、跨档案
    // 互不相同、数值量级与形态照抓样（单样本纹理，未证项记在根治账里）。
    // 派生：SHA-256(种子字节 ‖ 标签) 取流；种子缺位（不应发生，迁移已补）
    // 时退用 cdid 字节，仍保持按档案唯一。

    private static byte[] fpBlock(String tag) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            Profile p = profile();
            String seed = nz(p.fpSeed);
            if (seed.length() >= 64) {
                sha.update(HgSign.hexDecode(seed.substring(0, 64)));
            } else {
                sha.update(nz(p.cdid).getBytes(StandardCharsets.UTF_8));
            }
            sha.update(tag.getBytes(StandardCharsets.UTF_8));
            return sha.digest();
        } catch (Exception e) {
            throw new IllegalStateException("指纹派生失败", e);
        }
    }

    private static long fpU32(String tag) {
        byte[] b = fpBlock(tag);
        return ((long) (b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16)
                | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    private static final String FP_TOKEN_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /** 25 字符令牌（抓样两枚令牌同长同字母表：deviceProto 字段 5、消息字段 16）。 */
    private static String fpToken(String tag) {
        byte[] b = fpBlock(tag);
        StringBuilder sb = new StringBuilder(25);
        for (int i = 0; i < 25; i++) sb.append(FP_TOKEN_ALPHABET.charAt((b[i] & 0xFF) % 62));
        return sb.toString();
    }

    /** medusa 设备 proto 字段 5 的令牌（档案种子派生）。 */
    public static String fpToken5() {
        return fpToken("device-token-5");
    }

    /** medusa 消息字段 16 的令牌（口径同 {@link #fpToken5}）。 */
    public static String fpMsgToken() {
        return fpToken("msg-token-16");
    }

    // 抓样传感器原值（量级基准）：f16/f17/f18/f19/f20/f21。f18 与 f19 在
    // 抓样里逐位相同（疑同一底层读数两报），派生保持同源同值。
    private static final float[] FP_SENSOR_BASE = {
            255.24993896484375f, 35.58599090576172f, 3.467449188232422f,
            3.467449188232422f, 255.1754913330078f, 42.17544174194336f};

    /**
     * medusa 设备 proto 字段 16–21 的传感器浮点（idx 0–5）：以抓样值为
     * 量级基准、按档案种子做 ±8% 抖动——离抓样足够远（不命中原值比对）、
     * 又不脱离该量级（不触发量级异常）。语义未证，见根治账未证项。
     */
    public static float fpSensor(int idx) {
        int src = idx == 3 ? 2 : idx;
        double u = fpU32("sensor-" + src) / 4294967296.0;
        return (float) (FP_SENSOR_BASE[src] * (0.92 + 0.16 * u));
    }

    /** 消息字段 24 JSON 的 un_h（种子派生）。 */
    public static long fpUnH() {
        return 1000000000L + fpU32("un_h") % 1000000000L;
    }

    /** 消息字段 24 JSON 的 fkd（种子派生）。 */
    public static long fpFkd() {
        return 2000000000L + fpU32("fkd") % 2000000000L;
    }

    /** 消息字段 24 JSON 的 pd（种子派生）。 */
    public static long fpPd() {
        return -(1000000000L + fpU32("pd") % 1000000000L);
    }

    /** User-Agent（对照 protocol.UserAgent，档案 persona 代入）。 */
    public static String userAgent() {
        return PACKAGE + "/" + VERSION_CODE + " (Linux; U; Android " + deviceOsVersion() + "; zh_CN; "
                + deviceModel() + "; Build/" + deviceBuild()
                + "; Cronet/TTNetVersion:8d40f833 2026-03-03 QuicVersion:21ac1950 2025-11-18)";
    }

    /** x-reading-request 值：ticketMillis-randomInt31（对照 sdk anonymousHeaders）。 */
    public static String readingRequestId(long ticketMillis) {
        int r = new SecureRandom().nextInt(Integer.MAX_VALUE);
        return ticketMillis + "-" + Math.abs(r);
    }

    /**
     * 有序设备查询串（对照 DeviceQueryPairs，31 对、序即签名的一部分）。
     * ts 与 _rticket 由签名器在末尾追加，这里不带。
     */
    public static List<String[]> deviceQueryPairs() {
        return ownQueryPairs();
    }

    /** 自家档案查询串（现行口径逐字：含 cdid/openudid、档案 ids、档案 persona、7.3.9.32）。 */
    private static List<String[]> ownQueryPairs() {
        Profile p = profile();
        List<String[]> pairs = new ArrayList<>(31);
        pairs.add(new String[]{"iid", nz(p.installId)});
        pairs.add(new String[]{"device_id", nz(p.deviceId)});
        pairs.add(new String[]{"ac", "wifi"});
        pairs.add(new String[]{"channel", deviceChannel()});
        pairs.add(new String[]{"aid", String.valueOf(AID)});
        pairs.add(new String[]{"app_name", APP_NAME});
        pairs.add(new String[]{"cdid", nz(p.cdid)});
        pairs.add(new String[]{"version_code", VERSION_CODE});
        pairs.add(new String[]{"version_name", VERSION_NAME});
        pairs.add(new String[]{"device_platform", DEVICE_PLATFORM});
        pairs.add(new String[]{"os", "android"});
        pairs.add(new String[]{"ssmix", "a"});
        pairs.add(new String[]{"device_type", deviceModel()});
        pairs.add(new String[]{"device_brand", deviceBrand()});
        pairs.add(new String[]{"language", "zh"});
        pairs.add(new String[]{"os_api", String.valueOf(deviceOsApi())});
        pairs.add(new String[]{"os_version", deviceOsVersion()});
        pairs.add(new String[]{"manifest_version_code", VERSION_CODE});
        pairs.add(new String[]{"resolution", deviceResolution()});
        pairs.add(new String[]{"dpi", String.valueOf(deviceDpi())});
        pairs.add(new String[]{"update_version_code", VERSION_CODE});
        pairs.add(new String[]{"host_abi", "arm64-v8a"});
        pairs.add(new String[]{"dragon_device_type", "phone"});
        pairs.add(new String[]{"pv_player", VERSION_CODE});
        pairs.add(new String[]{"compliance_status", "0"});
        pairs.add(new String[]{"need_personal_recommend", "1"});
        pairs.add(new String[]{"player_so_load", "1"});
        pairs.add(new String[]{"is_android_pad_screen", "0"});
        pairs.add(new String[]{"openudid", nz(p.openUdid)});
        return pairs;
    }

    // ---------- 注册报文（BuildRegisterBody：gzip 明文 → TTEncrypt） ----------

    public static byte[] buildRegisterBody(long generatedAtMs) {
        Profile p = profile();
        JsonObject header = new JsonObject();
        header.addProperty("display_name", "红果免费短剧");
        header.addProperty("update_version_code", 73932);
        header.addProperty("manifest_version_code", 73932);
        header.addProperty("app_version_minor", VERSION_NAME);
        header.addProperty("aid", AID);
        header.addProperty("channel", deviceChannel());
        header.addProperty("package", PACKAGE);
        header.addProperty("app_version", VERSION_NAME);
        header.addProperty("version_code", 73932);
        header.addProperty("os", "Android");
        header.addProperty("os_version", deviceOsVersion());
        header.addProperty("os_api", deviceOsApi());
        header.addProperty("device_model", deviceModel());
        header.addProperty("device_brand", deviceBrand());
        header.addProperty("device_manufacturer", deviceBrand());
        header.addProperty("cpu_abi", "arm64-v8a");
        header.addProperty("language", "zh");
        header.addProperty("timezone", 8);
        header.addProperty("access", "wifi");
        header.addProperty("region", "CN");
        header.addProperty("tz_name", "Asia/Shanghai");
        header.addProperty("tz_offset", 28800);
        header.addProperty("cdid", nz(p.cdid));
        header.addProperty("openudid", nz(p.openUdid));
        header.addProperty("clientudid", nz(p.clientUdid));
        header.add("ipv6_list", new JsonArray());
        header.add("sim_serial_number", new JsonArray());
        header.add("oaid", bootstrapOaid(nz(p.oaid), generatedAtMs - 200));
        header.addProperty("oaid_may_support", true);
        header.addProperty("req_id", nz(p.reqId));
        header.addProperty("device_platform", DEVICE_PLATFORM);
        header.addProperty("guest_mode", 0);
        header.addProperty("is_system_app", 0);
        header.addProperty("sdk_flavor", "china");
        header.addProperty("device_id", "0");
        header.addProperty("install_id", "0");
        String plain = "{\"magic_tag\":\"ss_app_log\",\"header\":" + header.toString()
                + ",\"_gen_time\":" + generatedAtMs + "}";
        return ttEncrypt(gzip(plain.getBytes(StandardCharsets.UTF_8), generatedAtMs));
    }

    /** OAID 对象：键按字典序（Go map 序列化口径）。 */
    private static JsonObject bootstrapOaid(String id, long issuedAtMs) {
        JsonObject o = new JsonObject();
        o.addProperty("hw_id_version_code", "0");
        o.addProperty("id", id);
        o.addProperty("is_track_limited", "false");
        o.addProperty("query_times", "1");
        o.addProperty("req_id", id);
        o.addProperty("take_ms", "6");
        o.addProperty("time", String.valueOf(issuedAtMs));
        return o;
    }

    /** gzip：DefaultCompression、Header.ModTime=generatedAt、OS=255（对照 Go gzip 头）。 */
    private static byte[] gzip(byte[] plain, long mtimeMs) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(plain);
        deflater.finish();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!deflater.finished()) {
            int n = deflater.deflate(buf);
            body.write(buf, 0, n);
        }
        deflater.end();
        byte[] deflated = body.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(plain);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x1f);
        out.write(0x8b);
        out.write(8);
        out.write(0);
        int mtime = (int) (mtimeMs / 1000);
        out.write(mtime & 0xFF);
        out.write((mtime >>> 8) & 0xFF);
        out.write((mtime >>> 16) & 0xFF);
        out.write((mtime >>> 24) & 0xFF);
        out.write(0);
        out.write(255);
        out.write(deflated, 0, deflated.length);
        long c = crc.getValue();
        out.write((int) (c & 0xFF));
        out.write((int) ((c >>> 8) & 0xFF));
        out.write((int) ((c >>> 16) & 0xFF));
        out.write((int) ((c >>> 24) & 0xFF));
        int size = plain.length;
        out.write(size & 0xFF);
        out.write((size >>> 8) & 0xFF);
        out.write((size >>> 16) & 0xFF);
        out.write((size >>> 24) & 0xFF);
        return out.toByteArray();
    }

    private static final String TT_PREFIX_HEX =
            "3176c9b6d71579d7e82179c9e7cbde787b54d541bad3b15f2e8fed92293fa25a";
    private static final String TT_CONSTANT_HEX =
            "4dd4c2e6b83162090e52b3c7a6733ba41cb2462b829ab58a196b39db57177524" +
            "f49baf7f08e8d68d26a72e37c1a95a2f1f05a51892aef2949732b62a38aadd58";

    /** TTEncrypt 信封（移植 tt_encrypt.go）：SHA512 链派生 key/iv，AES-128-CBC。 */
    static byte[] ttEncrypt(byte[] input) {
        try {
            byte[] prefix = HgSign.hexDecode(TT_PREFIX_HEX);
            byte[] constant = HgSign.hexDecode(TT_CONSTANT_HEX);
            MessageDigest sha = MessageDigest.getInstance("SHA-512");
            byte[] h1 = sha.digest(prefix);
            sha.reset();
            sha.update(h1);
            byte[] keyIv = sha.digest(constant);
            sha.reset();
            byte[] digest = sha.digest(input);
            byte[] plain = new byte[digest.length + input.length];
            System.arraycopy(digest, 0, plain, 0, digest.length);
            System.arraycopy(input, 0, plain, digest.length, input.length);
            int padding = 16 - (plain.length % 16);
            byte[] padded = new byte[plain.length + padding];
            System.arraycopy(plain, 0, padded, 0, plain.length);
            for (int i = plain.length; i < padded.length; i++) padded[i] = (byte) padding;
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(keyIv, 0, 16, "AES"),
                    new javax.crypto.spec.IvParameterSpec(keyIv, 16, 16));
            byte[] encrypted = cipher.doFinal(padded);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(0x74);
            out.write(0x63);
            out.write(0x05);
            out.write(0x10);
            out.write(0);
            out.write(0);
            out.write(prefix, 0, prefix.length);
            out.write(encrypted, 0, encrypted.length);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("TTEncrypt 失败", e);
        }
    }

    /**
     * 解析注册回包：{device_id_str, install_id_str, device_token}（字符串
     * 或数字都收），id 缺失/为 "0" 返回 null（对照 ParseRegister）；
     * device_token 缺失时第三元素为空串（落盘层以非空才覆写处置）。
     * device_token 就是请求头 x-tt-dt 的值：官方注册 SDK 从同一回包
     * 读出落盘、每请求带出（login-ticket-dig-20261029.md 第一节四环
     * 实证）；旧版只取两个 id 把它扔了，HgSign 因此恒发空 x-tt-dt。
     */
    public static String[] parseRegister(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String deviceId = flexString(root, "device_id_str");
            String installId = flexString(root, "install_id_str");
            if (!Profile.nonZero(deviceId) || !Profile.nonZero(installId)) return null;
            String token = flexString(root, "device_token");
            return new String[]{deviceId, installId, token == null ? "" : token};
        } catch (Exception e) {
            return null;
        }
    }

    private static String flexString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return null;
        return el.getAsString();
    }

    // ---------- 身份派生与持久化 ----------

    private static Profile generate() {
        byte[] raw = new byte[120];
        new SecureRandom().nextBytes(raw);
        Profile p = new Profile();
        p.cdid = uuid(raw, 0);
        p.openUdid = hexLower(raw, 16, 8);
        p.clientUdid = uuid(raw, 24);
        p.oaid = uuid(raw, 40);
        p.reqId = uuid(raw, 56);
        // 指纹种子在生成时掷定（根治账）：种子 32 字节取 [72,104)。
        // persona 不再掷池——套宿主真机实值（2026-10-31 收口，见
        // HOST_PERSONA 口径注）：新档案自此就是这台真机本人，唯一性
        // 由本地派生标识与种子保证，不靠编造机型区分。
        p.fpSeed = hexLower(raw, 72, 32);
        p.applyPersona(HOST_PERSONA);
        return p;
    }

    private static String uuid(byte[] raw, int off) {
        int[] id = new int[16];
        for (int i = 0; i < 16; i++) id[i] = raw[off + i] & 0xFF;
        id[6] = (id[6] & 0x0f) | 0x40;
        id[8] = (id[8] & 0x3f) | 0x80;
        StringBuilder sb = new StringBuilder(36);
        for (int i = 0; i < 16; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-');
            sb.append(Character.forDigit((id[i] >>> 4) & 15, 16));
            sb.append(Character.forDigit(id[i] & 15, 16));
        }
        return sb.toString();
    }

    private static String hexLower(byte[] raw, int off, int len) {
        StringBuilder sb = new StringBuilder(len * 2);
        for (int i = 0; i < len; i++) {
            int v = raw[off + i] & 0xFF;
            sb.append(Character.forDigit((v >>> 4) & 15, 16));
            sb.append(Character.forDigit(v & 15, 16));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static Profile load() {
        try {
            String s = Prefers.getString(PREF_KEY);
            if (s == null || s.isEmpty()) return null;
            JsonObject o = JsonParser.parseString(s).getAsJsonObject();
            Profile p = new Profile();
            p.cdid = str(o, "cdid");
            p.openUdid = str(o, "openudid");
            p.clientUdid = str(o, "clientudid");
            p.oaid = str(o, "oaid");
            p.reqId = str(o, "req_id");
            p.deviceId = str(o, "device_id");
            p.installId = str(o, "install_id");
            p.deviceToken = str(o, "device_token");
            p.ttReq = str(o, "ttreq");
            p.storeRegion = str(o, "store_region");
            p.storeRegionSrc = str(o, "store_region_src");
            p.brand = str(o, "brand");
            p.model = str(o, "model");
            p.build = str(o, "build");
            p.osVersion = str(o, "os_version");
            p.osApi = num(o, "os_api");
            p.resolution = str(o, "resolution");
            p.dpi = num(o, "dpi");
            p.channel = str(o, "channel");
            p.fpSeed = str(o, "fp_seed");
            if (p.cdid == null || p.openUdid == null) return null;
            // 存量档案迁移（根治账）：旧落盘没有 persona/种子。persona 钉
            // legacy——旧档案注册时报的就是这台，换台会与已注册的 device_id
            // 描述打架；种子现补一份随机并落盘，此后指纹族按档案唯一派生。
            // 本地数据（HgStore 历史/收藏等）与本地登录托管都不经本落盘，
            // 迁移不丢任何用户数据。
            if (!p.hasPersona() || p.fpSeed == null || p.fpSeed.isEmpty()) {
                if (!p.hasPersona()) p.applyPersona(PERSONA_LEGACY);
                if (p.fpSeed == null || p.fpSeed.isEmpty()) {
                    byte[] seed = new byte[32];
                    new SecureRandom().nextBytes(seed);
                    p.fpSeed = hexLower(seed, 0, 32);
                }
                save(p);
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private static int num(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsInt() : 0;
    }

    private static void save(Profile p) {
        JsonObject o = new JsonObject();
        o.addProperty("cdid", nz(p.cdid));
        o.addProperty("openudid", nz(p.openUdid));
        o.addProperty("clientudid", nz(p.clientUdid));
        o.addProperty("oaid", nz(p.oaid));
        o.addProperty("req_id", nz(p.reqId));
        o.addProperty("device_id", nz(p.deviceId));
        o.addProperty("install_id", nz(p.installId));
        o.addProperty("device_token", nz(p.deviceToken));
        o.addProperty("ttreq", nz(p.ttReq));
        o.addProperty("store_region", nz(p.storeRegion));
        o.addProperty("store_region_src", nz(p.storeRegionSrc));
        o.addProperty("brand", nz(p.brand));
        o.addProperty("model", nz(p.model));
        o.addProperty("build", nz(p.build));
        o.addProperty("os_version", nz(p.osVersion));
        o.addProperty("os_api", p.osApi);
        o.addProperty("resolution", nz(p.resolution));
        o.addProperty("dpi", p.dpi);
        o.addProperty("channel", nz(p.channel));
        o.addProperty("fp_seed", nz(p.fpSeed));
        Prefers.put(PREF_KEY, o.toString());
    }
}
