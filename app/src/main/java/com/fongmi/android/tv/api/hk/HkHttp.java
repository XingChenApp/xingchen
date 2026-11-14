package com.fongmi.android.tv.api.hk;

import android.content.Context;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.XingChenDoh;
import com.fongmi.quickjs.bean.Req;
import com.github.catvod.bean.Doh;
import com.fongmi.android.tv.clash.ReadingProxyGate;
import com.github.catvod.net.GlobalProxy;
import com.github.catvod.net.SourceGate;
import com.github.catvod.utils.Json;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Dns;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.dnsoverhttps.DnsOverHttps;

/**
 * 海阔 URL 模板展开 + 简单 GET（设计文档 §4.2）。
 * <p>
 * 支持的替换词：
 * <ul>
 * <li>{@code fyclass/fyarea/fyyear/fysort} 分类/地区/年份/排序</li>
 * <li>{@code fyAll} 通用替换（取分类值）</li>
 * <li>{@code fypage} 页码，支持算术后缀 {@code fypage@-1@*20@}（即 (page-1)*20 → 0,20,40…）</li>
 * <li>{@code [firstPage=xxx]} page==1 时用括号内模板，否则去掉</li>
 * <li>{@code hiker://empty#真实地址} 取 # 后的真实地址</li>
 * <li>{@code **} 搜索关键词占位（URL 编码后替换）</li>
 * </ul>
 */
public class HkHttp {

    private static final String EMPTY_PREFIX = "hiker://empty#";
    private static final Pattern FIRST_PAGE = Pattern.compile("\\[firstPage=([^\\]]*)\\]");
    /** fypage@-1@*20@ 即 (page-1)*20；各 op 以 @ 分隔：@op1@op2@… */
    private static final Pattern FYPAGE_EX = Pattern.compile("fypage@((?:[-+*/]\\d+@)+)");
    private static final Pattern FYPAGE_OP = Pattern.compile("([-+*/])(\\d+)@");

    private static final String DEFAULT_UA = "Mozilla/5.0 (Linux; Android 15; 2407FRK8EC Build/AP3A.240617.008; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.127 Mobile Safari/537.36";

    /** 本栈缺省 UA（供同包内自备请求的调用方共用，避免各自写死 UA 串）。 */
    public static String defaultUa() {
        return DEFAULT_UA;
    }

    private static volatile OkHttpClient client;
    /** 海阔图片专用 client（8.83 OkHttpUrlLoader.Factory 内建 client 配置） */
    private static volatile OkHttpClient imageClient;
    /** DNS 解析封顶（秒）：见 HK_DNS 注释 */
    private static final long DNS_TIMEOUT_SEC = 10;
    private static final java.util.concurrent.ExecutorService DNS_POOL =
            java.util.concurrent.Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "hk-dns");
                t.setDaemon(true);
                return t;
            });

    /**
     * 海阔专用的 DNS（含 DoH），移植自 8.83 MoreSettingOfficer.loadDns 逻辑。
     * 外加一层解析封顶（兜底、非 8.83 原有）：本地实测（OkHttp 5.4.0 与 4.9.3 两代一致）
     * callTimeout/connect/read/write 全部管不到 DNS 解析阶段——被污染域名让系统解析器
     * 干挂时，execute() 既不返回也不抛错（1.2.7 真机日志：tjasak 的 POST 发出后 50 秒+
     * 无下文）。故把 lookup 放到达守护线程池、10 秒未回就按解析失败处理，让请求走
     * 正常的失败/重试路径，而不是无限静默。成功路径行为不变。
     *
     * <p>2026-10-14：取数 client() 改回系统默认解析（照 8.83
     * HttpHelper.buildOkHttpClient 原文，未设置 Dns）。漫小肆 www.jjmhw5.top
     * 在补了 charset 头后依然被 Connection reset，真机 dns 被动记录确认取数
     * 走了这里的 DoH，而同机同网原版（无自定义 Dns）能取到——解析路径分叉
     * 是唯一未排除的嫌疑；用户其余部位（PY/JS/主站）的 DoH 设置不受影响。
     * 图片 client 曾误装 HK_DNS（恒走 DoH、无回退）致零封面，已换 HK_IMAGE_DNS
     * （默认系统解析，仅 haikuoshijie 类地址在开 DoH 时走 DoH）；HK_DNS 保留，
     * 现无链使用，不要动它的行为。</p>
     */
    private static final Dns HK_DNS = new Dns() {
        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            DnsOverHttps doh = hkDoh;
            Dns delegate = doh != null ? doh : Dns.SYSTEM;
            java.util.concurrent.Future<List<InetAddress>> f =
                    DNS_POOL.submit(() -> delegate.lookup(hostname));
            try {
                return f.get(DNS_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                f.cancel(true);
                throw new UnknownHostException("dns timeout: " + hostname);
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable c = e.getCause();
                if (c instanceof UnknownHostException) throw (UnknownHostException) c;
                throw new UnknownHostException(String.valueOf(c));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UnknownHostException("dns interrupted: " + hostname);
            }
        }
    };
    private static volatile DnsOverHttps hkDoh;

    /**
     * 规则自带 DNS 注册表（8.83 DohProvidersKt.autoDomain 同款）：规则在 preRule 里调
     * registerDNS({网址前缀: DoH 地址或空格分隔的 IP 列表})，之后取数 client 解析命中
     * 的域名走注册的方式，不再走系统解析。原版默认种子 pasteme.tyrantg.com→阿里 DoH。
     * 此前星辰 registerDNS 只是记日志的空壳：靠它绕开污染解析的规则（如花都影视
     * d4.huadudm.net）只能走系统 DNS，解析到坏地址后连接挨个超时、首页无限转圈。
     */
    private static final java.util.Map<String, String> AUTO_DOMAIN = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, DnsOverHttps> AUTO_DOH = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        AUTO_DOMAIN.put("pasteme.tyrantg.com", "https://dns.alidns.com/dns-query");
    }

    /** registerDNS 落地：批量注册/覆盖域名→解析方式。 */
    public static void registerAutoDomain(java.util.Map<String, String> map) {
        if (map == null) return;
        for (java.util.Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) AUTO_DOMAIN.put(e.getKey(), e.getValue());
        }
    }

    /** 命中注册表返回其值：键可为纯域名或网址（取其 host 比对），按原版 endsWith 口径。 */
    private static String autoDomainValue(String hostname) {
        if (hostname == null || AUTO_DOMAIN.isEmpty()) return null;
        for (java.util.Map.Entry<String, String> e : AUTO_DOMAIN.entrySet()) {
            String key = e.getKey();
            String keyHost = key;
            if (key.contains("://")) {
                HttpUrl u = HttpUrl.parse(key);
                if (u != null) keyHost = u.host();
            }
            if (hostname.equals(keyHost) || hostname.endsWith(keyHost)) return e.getValue();
        }
        return null;
    }

    private static DnsOverHttps autoDoh(String dohUrl) {
        DnsOverHttps d = AUTO_DOH.get(dohUrl);
        if (d != null) return d;
        HttpUrl url = HttpUrl.parse(dohUrl);
        if (url == null) return null;
        d = new DnsOverHttps.Builder().client(new OkHttpClient()).url(url).build();
        AUTO_DOH.put(dohUrl, d);
        return d;
    }

    /**
     * 取数 client 专用 DNS：命中注册表的域名走注册的 DoH / 静态 IP，其余系统解析
     * （与改前行为一致，不影响未注册域名）。10 秒解析封顶沿用 HK_DNS 的兜底。
     */
    private static final Dns HK_AUTO_DNS = new Dns() {
        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            java.util.concurrent.Future<List<InetAddress>> f =
                    DNS_POOL.submit(() -> lookupAuto(hostname));
            try {
                return f.get(DNS_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                f.cancel(true);
                throw new UnknownHostException("dns timeout: " + hostname);
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable c = e.getCause();
                if (c instanceof UnknownHostException) throw (UnknownHostException) c;
                throw new UnknownHostException(String.valueOf(c));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UnknownHostException("dns interrupted: " + hostname);
            }
        }
    };

    private static List<InetAddress> lookupAuto(String hostname) throws UnknownHostException {
        String v = autoDomainValue(hostname);
        if (v != null) {
            String value = v.trim();
            if (value.startsWith("http://") || value.startsWith("https://")) {
                DnsOverHttps doh = autoDoh(value);
                if (doh != null) return doh.lookup(hostname);
            } else if (!value.isEmpty()) {
                // 空格分隔的 IP 列表，直接当静态地址用
                List<InetAddress> out = new java.util.ArrayList<>();
                for (String ip : value.split("\\s+")) {
                    try {
                        out.add(InetAddress.getByName(ip));
                    } catch (Exception ignored) {
                    }
                }
                if (!out.isEmpty()) return out;
            }
        }
        List<InetAddress> sys = Dns.SYSTEM.lookup(hostname);
        // 结果里混着 [::] 这类空地址时先剔掉：它永远连不上，留在列表里会被
        // 当成一条路由白试甚至拖死整批连接（18J.TV、Cloudfront 封面图实证）
        List<InetAddress> clean = stripAnyLocal(sys);
        if (!clean.isEmpty()) return clean;
        // 系统解析只回空地址（[::] 这类坏记录，真机 huangseb.com、91crdjai.com 实证）：
        // 换阿里 DoH 再试一次，有可用地址就用它；都没有维持原结果走原失败路径。
        try {
            DnsOverHttps doh = autoDoh("https://dns.alidns.com/dns-query");
            if (doh != null) {
                List<InetAddress> alt = doh.lookup(hostname);
                if (hasUsableAddress(alt)) return stripAnyLocal(alt);
            }
        } catch (Throwable ignored) {
        }
        return sys;
    }

    /** 剔除空地址（:: / 0.0.0.0）；全是空地址时返回空表，交给调用方走兜底。 */
    private static List<InetAddress> stripAnyLocal(List<InetAddress> list) {
        List<InetAddress> out = new java.util.ArrayList<>();
        if (list != null) {
            for (InetAddress a : list) {
                if (a != null && !a.isAnyLocalAddress()) out.add(a);
            }
        }
        return out;
    }

    private static boolean hasUsableAddress(List<InetAddress> list) {
        if (list == null) return false;
        for (InetAddress a : list) {
            if (a != null && !a.isAnyLocalAddress()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 图片自动 DNS（8.83 DohProvidersKt 整套移植，2026-10-23 补齐）：
    // 逐图判定在 HkGlideUrlLoader.buildLoadData（照 OkHttpUrlLoader.smali：
    // 先判 shouldUseAutoDNS，命中才换 client），与原版共用同一张名单——
    // 即上面的 AUTO_DOMAIN（种子 + dns0.txt 文件/远程维护 + 规则 registerDNS
    // 三个来源，原版 autoDomain 亦是同一张表）。此前星辰只有 HK_IMAGE_DNS 里
    // 「haikuoshijie + 用户开 DoH」一支近似实现，名单整套未移植：系统 DNS 被
    // 污染给出错 IP 时不抛异常、兜底永不触发，图床图片反复刷不出。
    // ------------------------------------------------------------------

    /** 自动 DNS 总开关（8.83 DohProvidersKt.dnsAuto，字段默认 true）。 */
    private static volatile boolean dnsAuto = true;

    /** 远程名单下发地址（8.83 DohProvidersKt.autoDNSCheckUrl 原文）。 */
    private static final String AUTO_DNS_CHECK_URL =
            "http://gh.haikuoshijie.cn/https://github.com/qiusunshine/hiker-rules/blob/master/dns0.txt";

    /**
     * APK 内置名单快照（前四条为 8.83 DohProvidersKt$loadAutoDNSFromRemote$1 的
     * 内置文本：01xs.com→360 DoH、raw.githubusercontent.com / github.com /
     * haikuoshijie.cn→阿里 DoH。smali 原文四条记录之间无分隔符、按原版解析法
     * 只会解出一条坏记录，这里按记录边界补回换行，记录内容一字未改）。
     * 后十条为星辰补入：用户海阔规则原件里实测在用的图床域名（8.83 靠服务端
     * 名单覆盖这类域名，那份数据不在包里；逐条出处见 hk-tuchuang-fix.md），
     * 解析方式统一阿里 DoH。名单判定是后缀匹配，写父域名即覆盖其子域名
     * （如 missav888.com 覆盖 static.missav888.com、vinua.cn 覆盖 img.vinua.cn）。
     */
    private static final String AUTO_DNS_BUNDLED =
            "01xs.com https://doh.360.cn/dns-query\n"
                    + "raw.githubusercontent.com https://dns.alidns.com/dns-query\n"
                    + "github.com https://dns.alidns.com/dns-query\n"
                    + "haikuoshijie.cn https://dns.alidns.com/dns-query\n"
                    + "spfm.xn--49sx5y1jln6s.cn https://dns.alidns.com/dns-query\n"
                    + "missav888.com https://dns.alidns.com/dns-query\n"
                    + "mogua.co https://dns.alidns.com/dns-query\n"
                    + "vinua.cn https://dns.alidns.com/dns-query\n"
                    + "thumbsnap.com https://dns.alidns.com/dns-query\n"
                    + "ax1x.com https://dns.alidns.com/dns-query\n"
                    + "sinaimg.cn https://dns.alidns.com/dns-query\n"
                    + "postimg.cc https://dns.alidns.com/dns-query\n"
                    + "bmp.ovh https://dns.alidns.com/dns-query\n"
                    + "hikerfans.com https://dns.alidns.com/dns-query";

    /** 名单刷新间隔：24 小时（8.83 原版常量 0x5265c00 毫秒）。 */
    private static final long AUTO_DNS_REFRESH_MS = 86_400_000L;

    private static final java.util.concurrent.atomic.AtomicBoolean AUTO_DNS_INITED =
            new java.util.concurrent.atomic.AtomicBoolean();
    /** 名单值 → 解析器缓存（避免逐图重建）。 */
    private static final java.util.Map<String, Dns> AUTO_DNS_BY_VALUE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 阿里 DoH（8.83 DohProvidersKt.dohAliDNS：地址 + 四个自举 IP），自动 DNS 的默认解析器。 */
    private static final Dns ALI_AUTO_DNS = cappedDns(buildAliDoh());

    private static DnsOverHttps buildAliDoh() {
        try {
            List<InetAddress> bootstrap = new java.util.ArrayList<>();
            for (String ip : new String[]{"223.5.5.5", "223.6.6.6", "2400:3200::1", "2400:3200:baba::1"}) {
                try {
                    bootstrap.add(InetAddress.getByName(ip));
                } catch (Exception ignored) {
                }
            }
            HttpUrl url = HttpUrl.parse("https://dns.alidns.com/dns-query");
            if (url == null) return null;
            return new DnsOverHttps.Builder()
                    .client(new OkHttpClient())
                    .url(url)
                    .bootstrapDnsHosts(bootstrap)
                    .build();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 给任意 Dns 套与 HK_DNS 同款的 10 秒解析封顶（守护线程池代查、超时按解析失败处理）。 */
    private static Dns cappedDns(Dns delegate) {
        if (delegate == null) return Dns.SYSTEM;
        return new Dns() {
            @Override
            public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                java.util.concurrent.Future<List<InetAddress>> f =
                        DNS_POOL.submit(() -> delegate.lookup(hostname));
                try {
                    List<InetAddress> r = f.get(DNS_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS);
                    // 同图片既有口径：混着的 [::] 空地址先剔掉（有真地址时）
                    List<InetAddress> clean = stripAnyLocal(r);
                    return clean.isEmpty() ? r : clean;
                } catch (java.util.concurrent.TimeoutException e) {
                    f.cancel(true);
                    throw new UnknownHostException("dns timeout: " + hostname);
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable c = e.getCause();
                    if (c instanceof UnknownHostException) throw (UnknownHostException) c;
                    throw new UnknownHostException(String.valueOf(c));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new UnknownHostException("dns interrupted: " + hostname);
                }
            }
        };
    }

    /**
     * 8.83 DohProvidersKt.shouldUseAutoDNS(String) 原文口径：
     * 地址空 → 否；总开关关 → 否；host 含 "haikuoshijie" → 是；
     * host 命中名单 → 是（经 autoDomainValue：精确 equals 或后缀 endsWith，
     * 与原版 containsKey + endsWith 遍历一致）；其余否。
     */
    public static boolean shouldUseAutoDNS(String url) {
        if (url == null || url.isEmpty()) return false;
        if (!dnsAuto) return false;
        try {
            HttpUrl u = HttpUrl.parse(url);
            if (u == null) return false;
            String host = u.host();
            if (host.isEmpty()) return false;
            if (host.contains("haikuoshijie")) return true;
            return autoDomainValue(host) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 自动 DNS 总开关（dnsAuto）的对外只读口径：图片失败换解析重试等兜底以此为闸——用户指定了 DoH（总开关关）时不许兜底覆盖其设置。 */
    public static boolean autoDnsEnabled() {
        return dnsAuto;
    }

    /**
     * 8.83 DohProvidersKt.buildAutoDNSClient(String, OkHttpClient) 原文口径：
     * 名单给了该 host 的解析方式 → 按方式换 Dns 当主解析（值为 DoH 地址走该
     * DoH；值为空格分隔的 IP 列表走静态地址，即原版 dohCustom/toHostMap 的
     * 语义）；名单没给（只有 haikuoshijie 子串命中那类）→ 默认阿里 DoH
     * （dohAliDNS）。异常回退原 client。派生 client 经 newBuilder 与 base
     * 共享连接池/分发器，不新建池；解析器按名单值缓存，避免逐图重建
     * （原版逐图新建 client，星辰这里只缓存 Dns、client 本身仍逐图派生）。
     */
    public static OkHttpClient buildAutoDNSClient(String url, OkHttpClient base) {
        if (base == null) base = imageClient();
        try {
            HttpUrl u = url == null ? null : HttpUrl.parse(url);
            String host = u == null ? null : u.host();
            String value = host == null ? null : autoDomainValue(host);
            if (value != null && !value.trim().isEmpty()) {
                return base.newBuilder().dns(autoDnsFor(value.trim(), host)).build();
            }
            return base.newBuilder().dns(ALI_AUTO_DNS).build();
        } catch (Throwable t) {
            return base;
        }
    }

    /**
     * 名单值 → 解析器（按值缓存）：http(s) 开头是 DoH 地址（经既有 autoDoh 缓存
     * 建 DnsOverHttps，再套 10 秒封顶）；其余按空格分隔的静态 IP 列表，只对
     * 名单命中的那个 host 生效（原版 toHostMap 就是按页面 host 绑定的），
     * 其它 host 回系统解析。
     */
    private static Dns autoDnsFor(String value, String host) {
        String cacheKey = value + "@" + (host == null ? "" : host);
        Dns cached = AUTO_DNS_BY_VALUE.get(cacheKey);
        if (cached != null) return cached;
        Dns d;
        if (value.startsWith("http://") || value.startsWith("https://")) {
            DnsOverHttps doh = autoDoh(value);
            d = doh != null ? cappedDns(doh) : Dns.SYSTEM;
        } else {
            List<InetAddress> ips = new java.util.ArrayList<>();
            for (String ip : value.split("\\s+")) {
                try {
                    ips.add(InetAddress.getByName(ip));
                } catch (Exception ignored) {
                }
            }
            List<InetAddress> fixed = ips;
            String boundHost = host;
            d = hostname -> {
                if (boundHost != null && boundHost.equals(hostname) && !fixed.isEmpty()) return fixed;
                return Dns.SYSTEM.lookup(hostname);
            };
        }
        AUTO_DNS_BY_VALUE.put(cacheKey, d);
        return d;
    }

    /** 环 24-6 官形解析器缓存（与二参名单路的 AUTO_DNS_BY_VALUE 分开，互不串味）。 */
    private static final java.util.Map<String, Dns> OFFICIAL_DNS_BY_VALUE = new java.util.concurrent.ConcurrentHashMap<>();

    /** 官 DnsOverHttps2 的假端点（非 http 的 dns 值时表外查询落点，连接必失败）。 */
    private static volatile DnsOverHttps fakeEndpointDoh;

    private static DnsOverHttps fakeEndpointDoh() {
        DnsOverHttps d = fakeEndpointDoh;
        if (d == null) {
            d = new DnsOverHttps.Builder().client(new OkHttpClient())
                    .url(HttpUrl.parse("http://127.0.0.1:8080")).build();
            fakeEndpointDoh = d;
        }
        return d;
    }

    /**
     * 环 24-6（硬门审计 B＋复核订正，官 DohProvidersKt.dohCustom/checkToHttpUrl 亲读）：
     * per-request dns 值的官形解析器——全 host 同一出口，不许表外回系统解析：
     * 值以 http 开头 → checkToHttpUrl 解析该 URL 建 DnsOverHttps、全 host 走它查；
     * 值以 http 开头但 URL 畸形 → 官 checkToHttpUrl 内 HttpUrl.get 抛错、被 dohCustom
     * 的 catch 收住后<b>不装任何自定义 Dns</b>（builder 原样、回系统解析）——本口以
     * 回 null 表示此形，由三参 buildAutoDNSClient 不装 Dns 直回原 client（批2 首落
     * 装恒抛 UnknownHostException 的 Dns 使全请求失败，方向与官正反，复核订正）；
     * 非 http 值 → toHostMap 只绑请求 url 的 host→空格切 IP 表，
     * 表内直回、表外照官经假端点 127.0.0.1:8080 的 DnsOverHttps 查询（必失败成
     * UnknownHost）。本口只供三参 buildAutoDNSClient；二参名单路（宿主 registerDNS
     * 名单/图片路）的 autoDnsFor 是自有面，不动。
     *
     * @return 官形 Dns；http 畸形值回 null（不装自定义 Dns）
     */
    private static Dns officialDnsFor(String value, String host) {
        String cacheKey = value + "@" + (host == null ? "" : host);
        Dns cached = OFFICIAL_DNS_BY_VALUE.get(cacheKey);
        if (cached != null) return cached;
        Dns d;
        if (value.startsWith("http")) {
            DnsOverHttps doh = HttpUrl.parse(value) == null ? null : autoDoh(value);
            if (doh != null) {
                d = doh;
            } else {
                // http 开头但 URL 畸形：照官 dohCustom catch 形——不装自定义 Dns。
                return null;
            }
        } else {
            List<InetAddress> ips = new java.util.ArrayList<>();
            for (String ip : value.split("\\s+")) {
                try {
                    ips.add(InetAddress.getByName(ip));
                } catch (Exception ignored) {
                }
            }
            List<InetAddress> fixed = ips;
            String boundHost = host;
            DnsOverHttps fake = fakeEndpointDoh();
            d = hostname -> {
                if (boundHost != null && boundHost.equals(hostname) && !fixed.isEmpty()) return fixed;
                return fake.lookup(hostname);
            };
        }
        OFFICIAL_DNS_BY_VALUE.put(cacheKey, d);
        return d;
    }

    /**
     * 8.83 DohProvidersKt.buildAutoDNSClient(dns, url, client) 三参版（官包该类
     * smali:196 亲读）：per-request dns 选项的落点——dns 非空且 client 非 null 才
     * 派生，异常回原 client。dns 语义经 dohCustom/checkToHttpUrl/toHostMap，
     * 环 24-6 已改走 officialDnsFor 全 host 同一出口（旧形复用名单路 autoDnsFor：
     * http 值建不出端点回 Dns.SYSTEM、IP 表外回系统解析——两处都与官分叉，
     * 注释里自记的「与官一格差」随本批订正销账）。
     */
    public static OkHttpClient buildAutoDNSClient(String dns, String url, OkHttpClient client) {
        if (dns == null || dns.trim().isEmpty() || client == null) return client;
        try {
            String host = null;
            if (url != null) {
                HttpUrl u = HttpUrl.parse(url);
                if (u != null) host = u.host();
            }
            Dns officialDns = officialDnsFor(dns.trim(), host);
            // http 畸形值（officialDnsFor 回 null）：照官 dohCustom catch 形不装 Dns、直回原 client（系统解析）。
            if (officialDns == null) return client;
            return client.newBuilder().dns(officialDns).build();
        } catch (Throwable t) {
            return client;
        }
    }

    /**
     * 8.83 DohProvidersKt.initDnsAuto 的星辰落点：由 applyDoh 在启动与改 DoH
     * 设置时调用（原版在 JSEngine 初始化时调）。总开关口径：原版
     * dnsAuto =（DNS 设置 == "智能"默认档）；星辰无"智能"档，未指定 DoH
     * （系统档、hkDoh == null）即等价默认档，故 dnsAuto = (hkDoh == null)，
     * 在 applyDoh 里先行刷新。名单维护每进程只实跑一次（原版
     * loadAutoDNSFromFile 会 clearAutoDomain 重置整张名单，放启动期跑可避开
     * 运行期规则 registerDNS 的登记被冲掉；原版同为引擎初始化期触发）。
     */
    public static void initAutoDns(Context context) {
        if (context == null || !dnsAuto) return;
        if (!AUTO_DNS_INITED.compareAndSet(false, true)) return;
        try {
            DNS_POOL.execute(() -> maintainAutoDomain(context));
        } catch (Throwable ignored) {
        }
    }

    /** 名单文件维护（8.83 loadAutoDNSFromFile + loadAutoDNSFromRemote 的串行等价，终态一致）。 */
    private static void maintainAutoDomain(Context context) {
        try {
            java.io.File file = autoDnsFile(context);
            // FromFile：本地文件在就先重置名单并加载它（原版 clearAutoDomain + fileToString + loadAutoDNS）
            if (file != null && file.exists()) {
                reseedAutoDomain();
                loadEffectiveAutoDns(readTextFile(file));
            }
            // FromRemote：文件缺失或已满 24 小时才刷新。8.83 出厂构建在这一步并未
            // 真发请求，而是把内置快照写回文件再加载（autoDNSCheckUrl 只声明、
            // 全包无消费方）；星辰在此先按该下发地址试拉一次，拉不到或内容不成
            // 名单才回退内置快照，写回文件后重置名单并加载（多一道内容校验，
            // 防下发端点返回网页正文污染名单 —— 与原版的差异仅此一处校验）。
            if (file == null || !file.exists()
                    || System.currentTimeMillis() - file.lastModified() >= AUTO_DNS_REFRESH_MS) {
                String text = fetchAutoDnsText();
                if (text == null || !hasParsableAutoDnsLine(text)) text = AUTO_DNS_BUNDLED;
                if (file != null) writeTextFile(file, text);
                reseedAutoDomain();
                loadEffectiveAutoDns(text);
            }
        } catch (Throwable t) {
            // 维护失败时已生效名单保持不动（loadEffectiveAutoDns 先垫内置快照），
            // 失败走本文件既有 hk 诊断日志留痕，不许在后台线程里无痕死掉。
            com.github.catvod.crawler.SpiderDebug.log("hk", "autoDns maintain failed: %s: %s",
                    t.getClass().getSimpleName(), String.valueOf(t.getMessage()));
        }
    }

    /** 名单文件位置：原版 <外部文件根>/rules/dns0.txt；星辰海阔根在 filesDir/plugins/hk 下，结构照搬。 */
    private static java.io.File autoDnsFile(Context context) {
        try {
            Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            return new java.io.File(new java.io.File(app.getFilesDir(), "plugins/hk"), "rules/dns0.txt");
        } catch (Throwable t) {
            return null;
        }
    }

    /** 8.83 DohProvidersKt.clearAutoDomain：清空名单并放回内置种子。 */
    private static void reseedAutoDomain() {
        AUTO_DOMAIN.clear();
        AUTO_DOMAIN.put("pasteme.tyrantg.com", "https://dns.alidns.com/dns-query");
    }

    /**
     * 名单生效合并入口（并集恒生效，维护链两个加载点共用——文件分支与
     * 远程分支都必须走这里，只改一处等于没改）：reseed 之后先垫内置快照
     * AUTO_DNS_BUNDLED，再加载文件/下发正文。loadAutoDNS 是逐行 put 覆盖，
     * 后加载者赢同名键，故：快照独有键恒在（图床等星辰自补域名不再被一次
     * 远程成功整表盖掉——此前维护是「三选一替换」，远程全文仅 3 行、健康
     * 网络设备上快照 14 条整表休眠）；下发独有键恒在；同名冲突下发赢
     * （下发是上游现行维护值，如 raw.githubusercontent.com 的静态 IP 表）。
     * 两步各自吞异常：名单文本损坏/为空只跳过该步，不许把已生效名单清空，
     * 也不许异常打穿到图片请求链。拉取失败分支 text 本就是快照，重复加载
     * 一遍是幂等 put，无副作用。
     */
    static void loadEffectiveAutoDns(String text) {
        try {
            loadAutoDNS(AUTO_DNS_BUNDLED);
        } catch (Throwable ignored) {
        }
        try {
            loadAutoDNS(text);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 8.83 DohProvidersKt.loadAutoDNS(String) 解析口径：按行切，每行按空白切，
     * 首段是域名、其余段以空格拼回作解析方式（DoH 地址或 IP 列表），不足
     * 两段的行跳过。（原版按字面空格切分且不 trim，对规整名单结果一致。）
     */
    static void loadAutoDNS(String text) {
        if (text == null) return;
        for (String line : text.split("\n")) {
            if (line == null || line.trim().isEmpty()) continue;
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 2) continue;
            StringBuilder value = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) value.append(' ');
                value.append(parts[i]);
            }
            AUTO_DOMAIN.put(parts[0], value.toString());
        }
    }

    /** 下发内容是否至少有一行像名单（域名 + DoH 地址/IP），防网页正文污染名单。 */
    private static boolean hasParsableAutoDnsLine(String text) {
        if (text == null) return false;
        for (String line : text.split("\n")) {
            if (line == null) continue;
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 2 || !parts[0].contains(".")) continue;
            String v = parts[1];
            if (v.startsWith("http://") || v.startsWith("https://")) return true;
            if (v.matches("[0-9a-fA-F.:]+")) {
                try {
                    if (InetAddress.getByName(v) != null) return true;
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    /** 按下发地址拉名单原文（15 秒超时，失败返回 null）。 */
    private static String fetchAutoDnsText() {
        try {
            OkHttpClient c = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();
            Request req = new Request.Builder().url(AUTO_DNS_CHECK_URL).build();
            try (Response resp = c.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                return resp.body().string();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static String readTextFile(java.io.File file) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    private static void writeTextFile(java.io.File file, String text) {
        try {
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            java.nio.file.Files.write(file.toPath(),
                    text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    /**
     * 图片 client 的基础 DNS（非自动 DNS 命中地址走这里）：
     * 默认系统解析；仅 host 含 "haikuoshijie" 的地址（原版
     * DohProvidersKt.shouldUseAutoDNS 口径）在用户开了 DoH 时走 hkDoh，
     * 其余一律系统解析。
     *
     * <p>自动 DNS 总开关开启时，shouldUseAutoDNS 命中的地址在
     * HkGlideUrlLoader.buildLoadData 就换成 buildAutoDNSClient 派生 client
     * （名单内走名单解析、未给解析方式走阿里 DoH 主解析），根本不到本 Dns；
     * 本 Dns 只兜未命中地址与总开关关闭（用户指定 DoH）时的情形，行为与
     * 移植前一致。</p>
     *
     * <p>8.83 的图片 client 在 builder 上不装自定义 Dns（loadDns 默认"智能"
     * 不装任何东西），按地址加 DoH 是逐请求在 buildLoadData 里判的；此前
     * 移植误把恒走 DoH 的 HK_DNS 装到整个图片 client 上，用户开 DoH 后所有
     * 图片域名无回退地走 DoH，picx.drleao.cn 查不到即 UnknownHostException
     * → 解密拿到空 → 封面全白（确定性零封面）。</p>
     *
     * <p>10 秒解析封顶保留（非 8.83 原有、防污染域名干挂的既有保护）。</p>
     */
    private static final Dns HK_IMAGE_DNS = new Dns() {
        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            DnsOverHttps doh = hkDoh;
            Dns delegate = (doh != null && isAutoDnsHost(hostname)) ? doh : Dns.SYSTEM;
            java.util.concurrent.Future<List<InetAddress>> f =
                    DNS_POOL.submit(() -> delegate.lookup(hostname));
            try {
                List<InetAddress> r = f.get(DNS_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS);
                // 同取数侧：混着的 [::] 空地址先剔掉（有真地址时），免得图片连接白试死路由
                List<InetAddress> clean = stripAnyLocal(r);
                return clean.isEmpty() ? r : clean;
            } catch (java.util.concurrent.TimeoutException e) {
                f.cancel(true);
                throw new UnknownHostException("dns timeout: " + hostname);
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable c = e.getCause();
                if (c instanceof UnknownHostException) {
                    // 系统解析直接失败：用 DoH 再查一次（仅兜底，不做主解析，
                    // 免得重蹈"恒走 DoH 无回退致零封面"）。用户配了 DoH 用用户的，
                    // 没配用阿里兜底（与取数侧同口径）。DoH 也查不到才维持原失败。
                    try {
                        DnsOverHttps fb = hkDoh != null ? hkDoh : autoDoh("https://dns.alidns.com/dns-query");
                        if (fb != null) {
                            List<InetAddress> alt = fb.lookup(hostname);
                            List<InetAddress> cleanAlt = stripAnyLocal(alt);
                            if (!cleanAlt.isEmpty()) return cleanAlt;
                            if (alt != null && !alt.isEmpty()) return alt;
                        }
                    } catch (Throwable ignored) {
                    }
                    throw (UnknownHostException) c;
                }
                throw new UnknownHostException(String.valueOf(c));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UnknownHostException("dns interrupted: " + hostname);
            }
        }
    };

    /**
     * 照原版 DohProvidersKt.shouldUseAutoDNS 里 haikuoshijie 子串一支：
     * 本方法只服务 HK_IMAGE_DNS 在自动 DNS 总开关关闭（用户指定了具体 DoH）
     * 时，让这类地址走用户 DoH 的既有分支。总开关开启时的逐图判定与名单
     * 机制已整套移植（shouldUseAutoDNS / buildAutoDNSClient + dns0.txt 名单
     * 维护，判定点在 HkGlideUrlLoader.buildLoadData），不经本方法。
     */
    private static boolean isAutoDnsHost(String hostname) {
        return hostname != null && hostname.toLowerCase(java.util.Locale.US).contains("haikuoshijie");
    }

    /**
     * 应用 DoH 设置到海阔客户端（对应 8.83 MoreSettingOfficer.loadDns）。
     * 8.83 默认"智能"不设置 DNS（走系统）；星辰"系统"同理传空。
     */
    /**
     * @return 是否应用成功；中途失败回 false 并留痕，旧 DNS 保持不动，
     *         不许新设置静默没生效（批 residual；原 void 签名唯一调用方
     *         XingChenDoh.applyToClients 不消费返回值，改 boolean 不影响它）。
     */
    public static boolean applyDoh(Context context) {
        try {
            Doh d = XingChenDoh.currentDoh(context);
            // 先在本地把新值建完再一次性换入：中途抛错时 hkDoh/client
            // 保持旧态，不留半应用状态。
            DnsOverHttps newDoh = null;
            if (d != null) {
                HttpUrl url = HttpUrl.parse(d.getUrl());
                newDoh = url == null ? null : new DnsOverHttps.Builder().client(new OkHttpClient()).url(url).bootstrapDnsHosts(d.getHosts()).build();
            }
            hkDoh = newDoh;
            // 重建 client 使新 DNS 生效
            synchronized (HkHttp.class) {
                client = null;
                imageClient = null;
            }
            // 8.83 initDnsAuto 同步点：自动 DNS 总开关随用户 DoH 选择刷新
            //（未指定 DoH = 原版"智能"默认档 → 开；指定了具体 DoH → 关，
            // 逐图自动 DNS 不再插手），名单维护每进程只实跑一次。
            dnsAuto = hkDoh == null;
            initAutoDns(context);
            return true;
        } catch (Throwable t) {
            com.github.catvod.crawler.SpiderDebug.log("hk", "applyDoh failed: %s: %s",
                    t.getClass().getSimpleName(), String.valueOf(t.getMessage()));
            return false;
        }
    }

    /**
     * 海阔图片专用 OkHttpClient，移植自 8.83 OkHttpUrlLoader.Factory 内建 client：
     * - BrotliInterceptor
     * - 信任所有证书 SSL（UnSafeTrustManager + UnSafeHostnameVerifier）
     * - DNS：HK_IMAGE_DNS——默认系统解析，仅 haikuoshijie 类地址在用户开 DoH 时走 DoH
     *   （此前误装恒走 DoH 的 HK_DNS 致零封面）；8.83 buildLoadData 的逐地址
     *   自动 DNS 判定由 HkGlideUrlLoader 在派生 client 上完成（见 shouldUseAutoDNS）
     * - 超时 + CookieJar
     * 不含 ContentType 拦截器（8.83 图片 client 同样不加）。
     */
    public static OkHttpClient imageClient() {
        if (imageClient == null) {
            synchronized (HkHttp.class) {
                if (imageClient == null) {
                    OkHttpClient.Builder b = new OkHttpClient.Builder()
                            .sslSocketFactory(HkSsl.getSocketFactory(), HkSsl.TRUST_MANAGER)
                            .hostnameVerifier(HkSsl.HOSTNAME_VERIFIER)
                            .dns(HK_IMAGE_DNS)
                            .connectTimeout(15, TimeUnit.SECONDS)
                            // 8.83 OkHttpUrlLoader.Factory 内建 client：read/write 各 15 秒
                            .readTimeout(15, TimeUnit.SECONDS)
                            .writeTimeout(15, TimeUnit.SECONDS)
                            .callTimeout(45, TimeUnit.SECONDS)
                            // W1（2026-10-18 二诊结论）：补回整次调用总超时 45 秒。8.83 内建 client 虽无 callTimeout，
                            // 但星辰的图片 client 多了一层独有的代理选择器（SourceGate/GlobalProxy）：TLS 握手与代理
                            // CONNECT 阶段不受 connect/read/write 约束（OkHttp 5.4.0：connectTimeout 只管 Socket.connect，
                            // Dns.lookup/TLS/代理握手是同步阻塞无上界），请求一旦 hang 在这几阶段就永远不返回——
                            // 1.3.44/45 真机「UU 每次进白、下拉刷新才出」的病根。DNS 阶段已由 HK_IMAGE_DNS 10 秒封顶，
                            // callTimeout 补的是 TLS/代理握手这些真正无上界的阶段。hang 45 秒后 onFailure 进 fetcher
                            // 自带的 3 次 refire 重试，瞬态 hang 在进页时自愈，不再靠用户下拉刷新。
                            // 按源定向：与壳子同套选择器链（SourceGate 包在 GlobalProxy 外），
                            // 但图片链只吃确定归属版（wrapSelectorNoGuess）：封面请求跑在
                            // Glide/OkHttp 线程上、无线程打点，全量版的会话绑定与 15 秒
                            // 近期回退会把封面猜给某个走节点的源，首开连接被拖进本地口
                            // CONNECT 阶段（正是上面 W1 那种 hang 的入口）——官方图片
                            // client 无此层，猜测归属摘除后图片只剩 host 认领/全局开关
                            // 两条确定路。取数 client（下方 client()）仍用全量版，不动。
                            .proxySelector(SourceGate.wrapSelectorNoGuess(GlobalProxy.wrapSelector(java.net.ProxySelector.getDefault())));
                    // 8.83 图片 client dispatcher maxRequestsPerHost = 48
                    okhttp3.Dispatcher d = new okhttp3.Dispatcher();
                    d.setMaxRequestsPerHost(48);
                    b.dispatcher(d);
                    try {
                        Class<?> c = Class.forName("okhttp3.brotli.BrotliInterceptor");
                        Object inst = c.getField("INSTANCE").get(null);
                        b.addInterceptor((okhttp3.Interceptor) inst);
                    } catch (Throwable ignored) {
                    }
                    try {
                        b.cookieJar(HkCookieJar.get(App.get()));
                    } catch (Throwable ignored) {
                    }
                    imageClient = b.build();
                }
            }
        }
        return imageClient;
    }

    /**
     * 构建 OkHttpClient，移植自 8.83 HttpHelper.buildOkHttpClient(int, boolean)：
     * - 信任所有证书 SSL（HttpsUtils.getSslSocketFactory + UnSafeHostnameVerifier）
     * - DNS：系统默认（照 8.83 原文，HttpHelper.buildOkHttpClient 未设置 Dns；
     *   图片 client 用 HK_IMAGE_DNS：默认系统解析，仅 haikuoshijie 类地址在开 DoH 时走 DoH）
     * - ContentTypePreInterceptor + ContentTypeAfterInterceptor（network）
     * - CookieJar 持久化
     * - 统一超时
     */
    // 8.83 HttpHelper.buildOkHttpClient 移植：公开供 HkJsRuntime 等调用方使用，
    // 使海阔 JS 请求与 HkHttp 同走 CookieJar / 内容类型拦截器配置；
    // DNS 走 HK_AUTO_DNS：未注册域名=系统解析（与原版默认一致），规则 registerDNS
    // 注册过的域名走其 DoH/IP（原版 DohProvidersKt.autoDomain 机制；图片 client
    // 另用 HK_IMAGE_DNS，口径不变）。
    public static OkHttpClient client() {
        if (client == null) {
            synchronized (HkHttp.class) {
                if (client == null) {
                    OkHttpClient.Builder b = new OkHttpClient.Builder()
                            .dns(HK_AUTO_DNS)
                            .sslSocketFactory(HkSsl.getSocketFactory(), HkSsl.TRUST_MANAGER)
                            .hostnameVerifier(HkSsl.HOSTNAME_VERIFIER)
                            // 8.83 原版：Application.onCreate 里 OkGo.getInstance().addCommonHeaders
                            // 加了公共头 charset=UTF-8，每个规则请求都带；此前移植漏了，补上
                            .addInterceptor(chain -> {
                                Request req = chain.request();
                                if (req.header("charset") == null) {
                                    req = req.newBuilder().header("charset", "UTF-8").build();
                                }
                                return chain.proceed(req);
                            })
                            .addInterceptor(HkContentTypePreInterceptor.INSTANCE)
                            .addNetworkInterceptor(HkContentTypeAfterInterceptor.INSTANCE)
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(10, TimeUnit.SECONDS)
                            .writeTimeout(10, TimeUnit.SECONDS)
                            // 8.83 原版无总超时截断（HttpHelper.buildOkHttpClient 全 10s、无 callTimeout）：
                            // 规则设 timeout 时 connect/read/write 三项随规则走（见 to()），整条调用
                            // 不得再被固定 45s 掐断。此前加的 callTimeout(45s) 会把 timeout=120000
                            // 的规则请求在 45s 掐掉，属确定性 bug，已移除（P0-A1）。
                            .followRedirects(true)
                            .followSslRedirects(true)
                            // 诊断用：只记解析结果（域名→IP 列表），不改变任何行为。
                            // 漫小肆取数被重置的排查需要知道每次实际连的是哪个 IP
                            .eventListener(new okhttp3.EventListener() {
                                @Override
                                public void dnsEnd(okhttp3.Call call, String domainName, List<InetAddress> inetAddressList) {
                                    try {
                                        StringBuilder sb = new StringBuilder();
                                        if (inetAddressList != null) {
                                            for (InetAddress a : inetAddressList) {
                                                if (sb.length() > 0) sb.append(',');
                                                sb.append(a.getHostAddress());
                                            }
                                        }
                                        com.github.catvod.crawler.SpiderDebug.log("hk", "dns %s -> %s", domainName, sb);
                                    } catch (Throwable ignored) {
                                    }
                                }
                            });
                            // retryOnConnectionFailure 不显式关闭：8.83 HttpHelper 原文未设置
                            // （OkHttp 默认 true，多地址时可换路由重试），此前被仿写版写成 false
                    // Brotli（8.83 标配）：反射加载，缺失则跳过
                    try {
                        Class<?> c = Class.forName("okhttp3.brotli.BrotliInterceptor");
                        Object inst = c.getField("INSTANCE").get(null);
                        b.addInterceptor((okhttp3.Interceptor) inst);
                    } catch (Throwable ignored) {
                    }
                    // 不吞：CookieJar 装不上还照常 build 并缓存，等于全站请求终身
                    // 无 Cookie 且无任何报错；让异常穿出 client()，由取数链的失败
                    // 回调感知，且 client 不缓存、后续调用可重试装配。
                    b.cookieJar(HkCookieJar.get(App.get()));
                    // 按源定向：与图片 client 同套选择器链
                    b.proxySelector(SourceGate.wrapSelector(GlobalProxy.wrapSelector(java.net.ProxySelector.getDefault())));
                    client = b.build();
                }
            }
        }
        return client;
    }

    /**
     * 构造带 Req 参数的请求 Call：请求头/请求体构建逻辑与 Connect.to 同构，
     * 只是客户端换成 8.83 配置的 HkHttp.client()（DoH / CookieJar / 拦截器都生效）。
     *
     * <p>8.83 HttpHelper.buildOkHttpClient(int timeout, boolean redirect) 语义：
     * 规则可通过 Req 指定 redirect（是否跟随跳转）和 timeout（毫秒）；
     * 例如 fetch(url, {redirect: 0}) 用于取 302 的 Location 头。默认跟随跳转。</p>
     */
    /**
     * 按源定向归属登记：本线程带着 hk 归属发请求时，把目标 host 记进认领表
     * （与阅读共用 ReadingProxyGate 的表），后续在无归属线程发出的
     * 同 host 请求（OkHttp 调度线程判读、Glide 图片）可按域名认领回本规则，
     * 不再只靠线程标记＋15 秒兜底窗口（JAV Verse 真机实证：load 首发请求
     * 在调度线程判读时无标记、直连被站方挡死，规则 20 秒超时打不开）。
     * 认领只提供归属，是否走节点仍由 SourceProxyPolicy 的逐源开关裁决。
     */
    private static void noteHkHost(String url) {
        try {
            SourceGate.Mark m = SourceGate.current();
            if (m != null && "hk".equals(m.kind)) {
                ReadingProxyGate.recordUrl(url, m.kind, m.id);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 按归属裁决取 client（治连接池污染，1.4.22 真机实证）：选择器只在新建连接时
     * 被调用，池里已有的直连连接会被后续请求直接复用、判定改了也换不掉——首发
     * 请求一旦直连回池，整页 fetch 全绕过网关。故判走节点时改用显式 proxy 的派生
     * client：显式 proxy 是 Address 的一部分，与直连连接天然不同池、互不复用；
     * 判直连/无裁决时原样返回 client()，选择器链行为不变。
     * 裁决口径与选择器一致：线程标记优先（ReadingProxyGate.decide 的 flagOn 与
     * SourceProxyPolicy 同源），无标记按 host 认领，再无认领看「全部走节点」。
     */
    public static OkHttpClient clientFor(String url) {
        Proxy proxy = explicitProxy(url);
        OkHttpClient base = client();
        return proxy == null ? base : base.newBuilder().proxy(proxy).build();
    }

    private static Proxy explicitProxy(String url) {
        try {
            SourceGate.Mark m = SourceGate.current();
            if (m != null) {
                Proxy p = ReadingProxyGate.proxyFor(m.kind, m.id, true);
                return p == Proxy.NO_PROXY ? null : p;
            }
            String host = ReadingProxyGate.hostOf(url);
            if (host != null && !host.isEmpty()) {
                String claimed = ReadingProxyGate.claim(host);
                if (claimed != null) {
                    int split = claimed.indexOf('|');
                    if (split <= 0) return null;
                    Proxy p = ReadingProxyGate.proxyFor(
                            claimed.substring(0, split), claimed.substring(split + 1), true);
                    return p == Proxy.NO_PROXY ? null : p;
                }
            }
            Proxy p = ReadingProxyGate.proxyFor(null, null, false);
            return p == Proxy.NO_PROXY ? null : p;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Call to(String url, Req req) {
        noteHkHost(url);
        // 入口留痕：此前只有完成/失败才记 fetch 诊断，卡在 execute 里时日志里
        // 分不清"没走到"还是"走到了没回来"（1.2.6 真机日志的教训）
        try {
            okhttp3.HttpUrl hu = okhttp3.HttpUrl.parse(url);
            com.github.catvod.crawler.SpiderDebug.log("hk", "fetch start host=%s", hu == null ? "?" : hu.host());
        } catch (Throwable ignored) {
        }
        return callClient(url, req).newCall(getRequest(url, req, Headers.of(req.getHeader()), null));
    }

    /**
     * 原始字节 body 版（规则以字节数组作 body 的 POST/PUT，如 protobuf 二进制体）：
     * 与 to() 同一 client 派生口径，仅请求体改走 rawBody 原样发送——不经 Req 的
     * String body 与 buildPostBody 的字符串编解码（二进制经那条路必被改坏：
     * 数字数组会被拼成 "[137,80,...]" 文本当正文发出，2026-10-31 橘汁复诊实证）。
     */
    public static Call toRaw(String url, Req req, byte[] rawBody) {
        noteHkHost(url);
        try {
            okhttp3.HttpUrl hu = okhttp3.HttpUrl.parse(url);
            com.github.catvod.crawler.SpiderDebug.log("hk", "fetch start host=%s", hu == null ? "?" : hu.host());
        } catch (Throwable ignored) {
        }
        return callClient(url, req).newCall(getRequest(url, req, Headers.of(req.getHeader()), rawBody));
    }

    /** to()/toRaw() 共用的 client 派生：按 Req 的 redirect/timeout 从 clientFor 派生本次调用的 client。 */
    private static OkHttpClient callClient(String url, Req req) {
        boolean redirect = req.isRedirect();
        // Req.getTimeout() 默认 10000ms；8.83 用传入的 timeout 设 read/write/connect
        int timeout = 10000;
        try {
            Integer t = req.getTimeout();
            if (t != null) timeout = t;
        } catch (Throwable ignored) {
        }
        OkHttpClient c = clientFor(url);
        if (!redirect || timeout != 10000) {
            OkHttpClient.Builder b = c.newBuilder()
                    .followRedirects(redirect)
                    .followSslRedirects(redirect);
            if (timeout != 10000) {
                b.connectTimeout(timeout, TimeUnit.MILLISECONDS)
                 .readTimeout(timeout, TimeUnit.MILLISECONDS)
                 .writeTimeout(timeout, TimeUnit.MILLISECONDS);
            }
            c = b.build();
        }
        // 8.83：per-request dns 在 client 派生链末尾再包一层（HHs 装配末段
        // buildAutoDNSClient(dns,url,client) 同序；G6）——主路此前 Req 无 dns
        // 字段、选项整丢
        String dnsVal = req.getDns();
        if (dnsVal != null && !dnsVal.trim().isEmpty()) {
            c = buildAutoDNSClient(dnsVal, url, c);
        }
        return c;
    }

    private static Request getRequest(String url, Req req, Headers headers, byte[] rawBody) {
        Request.Builder rb = new Request.Builder().url(url).headers(headers);
        applyThreadTag(rb);
        if (req.getMethod().equalsIgnoreCase("post")) {
            return rb.post(rawBody != null ? rawBodyOf(rawBody, headers.get(HttpHeaders.CONTENT_TYPE))
                    : getPostBody(req, headers.get(HttpHeaders.CONTENT_TYPE))).build();
        } else if (req.getMethod().equalsIgnoreCase("put")) {
            // 8.83：PUT 走 OkGo.put 带 body；此前 PUT/HEAD 一律掉进 GET，body 被丢
            return rb.put(rawBody != null ? rawBodyOf(rawBody, headers.get(HttpHeaders.CONTENT_TYPE))
                    : getPostBody(req, headers.get(HttpHeaders.CONTENT_TYPE))).build();
        } else if (req.getMethod().equalsIgnoreCase("head") || req.getMethod().equalsIgnoreCase("header")) {
            return rb.head().build();
        } else if (req.getMethod().equalsIgnoreCase("delete")) {
            // 8.83：DELETE 与 POST/PUT 同走 BodyRequest 装配 body（HHs:1055,1180-1520
            // 区亲读）；此前 DELETE 掉进 GET 支、body 整丢（官包全量抠账 G10）
            return rb.delete(rawBody != null ? rawBodyOf(rawBody, headers.get(HttpHeaders.CONTENT_TYPE))
                    : getPostBody(req, headers.get(HttpHeaders.CONTENT_TYPE))).build();
        } else {
            return rb.get().build();
        }
    }

    /** 原始字节请求体：Content-Type 优先用请求头里规则自带的值（与 getPostBody 的口径一致）。 */
    private static RequestBody rawBodyOf(byte[] rawBody, String contentType) {
        MediaType mt = null;
        if (contentType != null) {
            try {
                mt = MediaType.get(contentType);
            } catch (Throwable ignored) {
            }
        }
        return RequestBody.create(rawBody, mt);
    }

    /** 8.83 addTagForThread 语义：当前线程有 tag 时给请求打上，供 cancelByTag 取消。 */
    private static void applyThreadTag(Request.Builder rb) {
        try {
            Object tag = HkHttpHelper.getTagFromThread();
            if (tag != null) rb.tag(Object.class, tag);
        } catch (Throwable ignored) {
        }
    }

    private static RequestBody getPostBody(Req req, String contentType) {
        // 表单参数回收表：buildPostBody 的切分结果与 data-form 支的参数都落这里，
        // 供下方 8.83 的非 UTF-8 charset 表单重编码（FormBody 重建）使用
        Map<String, String> formParams = new java.util.LinkedHashMap<>();
        RequestBody rb;
        if (req.getData() != null && "json".equals(req.getPostType())) {
            rb = getJsonBody(req);
        } else if (req.getData() != null && "form".equals(req.getPostType())) {
            rb = getFormBody(req, formParams);
        } else if (req.getData() != null && "form-data".equals(req.getPostType())) {
            rb = getFormDataBody(req);
        } else if (req.getBody() != null) {
            // 一律走 8.83 buildPostBody 分发（rejectCoding 原样 / multipart / json /
            // text / 像 JSON / 表单切分）：此前带 Content-Type 时直发原串，表单型
            // 正文不切分、rejectCoding 在 json/form 两支被绕过（官包全量抠账 G14）；
            // 无 Content-Type 时更绝不丢 body（曾直接返回空 body：空 POST 出去、
            // 服务端回 200 空正文，规则全灭）。
            rb = HkHttpHelper.buildPostBody(req.getMethod(), req.getBody(), contentType, req.isRejectCoding(), formParams);
        } else {
            rb = RequestBody.create(new byte[0]);
        }
        // 8.83 HttpHelper 装配段（HHs:1742-1900 亲读；HH fetch0Inner 同款）：charset
        // 非空且非 UTF-8、method 为 POST/PUT 时，已装配的表单参数以该 charset 的
        // FormBody 重建（GBK 站表单提交靠此步，缺省 UTF-8 编码服务端解出乱码）。
        String cs = req.getCharsetOrNull();
        if (cs != null && !cs.trim().isEmpty() && !"UTF-8".equalsIgnoreCase(cs.trim())
                && ("POST".equalsIgnoreCase(req.getMethod()) || "PUT".equalsIgnoreCase(req.getMethod()))
                && !formParams.isEmpty()) {
            FormBody.Builder fb = new FormBody.Builder(java.nio.charset.Charset.forName(cs.trim()));
            for (Map.Entry<String, String> e : formParams.entrySet()) fb.add(e.getKey(), e.getValue());
            return fb.build();
        }
        return rb;
    }

    private static RequestBody getJsonBody(Req req) {
        return RequestBody.create(req.getData().toString(), MediaType.get("application/json; charset=utf-8"));
    }

    private static RequestBody getFormBody(Req req, Map<String, String> formParams) {
        FormBody.Builder builder = new FormBody.Builder();
        Map<String, String> params = Json.toMap(req.getData());
        formParams.putAll(params);
        for (String key : params.keySet()) builder.add(key, params.get(key));
        return builder.build();
    }

    private static RequestBody getFormDataBody(Req req) {
        String boundary = "--dio-boundary-" + new SecureRandom().nextInt(42949) + new SecureRandom().nextInt(67296);
        MultipartBody.Builder builder = new MultipartBody.Builder(boundary).setType(MultipartBody.FORM);
        Map<String, String> params = Json.toMap(req.getData());
        for (String key : params.keySet()) builder.addFormDataPart(key, params.get(key));
        return builder.build();
    }

    /**
     * 展开首页/分类 URL 模板。
     *
     * @param template 规则 url 模板
     * @param cls      分类值（fyclass/fyAll）
     * @param area     地区值（fyarea）
     * @param year     年份值（fyyear）
     * @param sort     排序值（fysort）
     * @param page     页码（从 1 开始）
     */
    public static String expandUrl(String template, String cls, String area, String year, String sort, int page) {
        return expandUrl(template, cls, area, year, sort, page, true);
    }

    /**
     * 展开首页/分类 URL 模板。
     *
     * @param stripEmptyPrefix 是否剥离 {@code hiker://empty#} 前缀。
     *                       js: 规则必须传 false——规则 JS 里常写
     *                       {@code MY_URL.replace("hiker://empty##", host)}，
     *                       MY_URL 必须是原始 url，提前剥离会破坏替换逻辑。
     */
    public static String expandUrl(String template, String cls, String area, String year, String sort, int page, boolean stripEmptyPrefix) {
        if (template == null) return "";
        String url = template.trim();
        if (stripEmptyPrefix && url.startsWith(EMPTY_PREFIX)) url = url.substring(EMPTY_PREFIX.length());
        Matcher fm = FIRST_PAGE.matcher(url);
        if (fm.find()) {
            String first = fm.group(1);
            url = page <= 1 ? first : fm.replaceAll("");
            if (stripEmptyPrefix && url.startsWith(EMPTY_PREFIX)) url = url.substring(EMPTY_PREFIX.length());
        }
        url = url.replace("fyclass", safe(cls));
        url = url.replace("fyarea", safe(area));
        url = url.replace("fyyear", safe(year));
        url = url.replace("fysort", safe(sort));
        url = url.replace("fyAll", safe(cls));
        Matcher pm = FYPAGE_EX.matcher(url);
        StringBuffer sb = new StringBuffer();
        while (pm.find()) {
            pm.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(applyOps(page, pm.group(1)))));
        }
        pm.appendTail(sb);
        return sb.toString().replace("fypage", String.valueOf(page));
    }

    /** 展开搜索 URL：先按 expandUrl 处理，再把 ** 换成编码后的关键词。 */
    public static String expandSearchUrl(String template, String keyword, int page) {
        return expandSearchUrl(template, keyword, page, true);
    }

    /** 展开搜索 URL；stripEmptyPrefix=false 时保留 hiker://empty# 前缀（js: 规则用）。 */
    public static String expandSearchUrl(String template, String keyword, int page, boolean stripEmptyPrefix) {
        String url = expandUrl(template, "", "", "", "", page, stripEmptyPrefix);
        String kw = keyword == null ? "" : keyword;
        try {
            kw = URLEncoder.encode(kw, "UTF-8");
        } catch (Exception ignored) {
        }
        return url.replace("**", kw);
    }

    private static int applyOps(int page, String ops) {
        int v = page;
        if (ops == null) return v;
        Matcher m = FYPAGE_OP.matcher(ops);
        while (m.find()) {
            int n;
            try {
                n = Integer.parseInt(m.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            switch (m.group(1)) {
                case "+":
                    v += n;
                    break;
                case "-":
                    v -= n;
                    break;
                case "*":
                    v *= n;
                    break;
                case "/":
                    if (n != 0) v /= n;
                    break;
                default:
                    break;
            }
        }
        return v;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * URL 增强解析结果（官方格式 {@code url;method;encoding;{K@V&&K2@V2}}）。
     */
    public static class Enhancement {
        public String url = "";
        public String method = "GET";
        /**
         * 8.83 HttpHelper.CharsetStringConvert 语义：空 = 未显式指定，解码时看响应
         * Content-Type（OkHttp body.string() 行为）；非空 = 显式指定，强制用它解码。
         * 以前默认 "UTF-8"，显式和未显式混为一谈，响应头 charset 被无视。
         */
        public String charset = "";
        public final Map<String, String> headers = new HashMap<>();

        /** 是否为"干净"请求（无增强，按原逻辑走即可）。 */
        public boolean isPlain() {
            return "GET".equals(method) && (charset == null || charset.isEmpty()) && headers.isEmpty();
        }
    }

    /**
     * 解析 URL 增强：{@code url;method;encoding;{K@V&&K2@V2}}。
     * header 块用 {@code {}} 包裹、内以 {@code &&} 分隔、键值以 {@code @} 分隔。
     */
    public static Enhancement parseEnhancement(String raw) {
        Enhancement e = new Enhancement();
        if (raw == null) return e;
        String u = raw.trim();
        int b = u.indexOf('{');
        int be = u.lastIndexOf('}');
        if (b > 0 && be > b) {
            for (String kv : u.substring(b + 1, be).split("&&")) {
                int at = kv.indexOf('@');
                if (at > 0) e.headers.put(kv.substring(0, at).trim(), kv.substring(at + 1).trim());
            }
            u = (u.substring(0, b) + u.substring(be + 1)).trim();
        }
        String[] parts = u.split(";", -1);
        e.url = parts[0].trim();
        if (parts.length > 1 && !parts[1].trim().isEmpty()) e.method = parts[1].trim().toUpperCase();
        if (parts.length > 2 && !parts[2].trim().isEmpty()) e.charset = parts[2].trim();
        return e;
    }

    /**
     * 简单 GET 请求，返回文本。
     *
     * @throws IOException        网络失败/非 2xx/空 body
     * @throws IllegalArgumentException 非 http(s) 链接（hiker://、js: 等需 M2 的 JS 运行时处理）
     */
    public static String get(String url, String ua) throws IOException {
        return get(url, ua, 0);
    }

    /**
     * 带超时的 GET（点击预检等交互链路用，避免长时间无响应导致"点了没反应"）。
     * timeoutMs<=0 时走默认超时（connect 15s / read-write 30s）。
     */
    public static String get(String url, String ua, int timeoutMs) throws IOException {
        noteHkHost(url);
        Enhancement e = parseEnhancement(url);
        if (e.isPlain()) return getPlain(e.url, ua, timeoutMs);
        return getEnhanced(e, ua, timeoutMs);
    }

    /**
     * 下载二进制（图片解密用）。支持 headers map。
     */
    public static byte[] getBytes(String url, Map<String, String> headers) throws IOException {
        noteHkHost(url);
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("不支持的链接协议: " + url);
        }
        Request.Builder rb = new Request.Builder().url(url)
                .header("User-Agent", DEFAULT_UA)
                .header("Accept", "image/*,*/*;q=0.8");
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                try {
                    rb.header(h.getKey(), h.getValue());
                } catch (Throwable ignored) {
                }
            }
        }
        applyThreadTag(rb);
        try (Response resp = clientFor(url).newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + " " + url);
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("空响应 " + url);
            return body.bytes();
        }
    }

    /** 无增强的原逻辑（行为与之前完全一致）。timeoutMs>0 时覆盖默认超时。 */
    private static String getPlain(String url, String ua, int timeoutMs) throws IOException {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("不支持的链接协议（需 M2 JS 运行时）：" + url);
        }
        Request.Builder plainRb = new Request.Builder().url(url)
                .header("User-Agent", ua == null || ua.isEmpty() ? DEFAULT_UA : ua)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9");
        applyThreadTag(plainRb);
        Request req = plainRb.build();
        OkHttpClient c = timeoutMs > 0
                ? clientFor(url).newBuilder()
                        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .build()
                : clientFor(url);
        try (Response resp = c.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + " " + url);
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("空响应 " + url);
            return body.string();
        }
    }

    /** 带 method/encoding/headers 的增强请求。timeoutMs>0 时覆盖默认超时。 */
    private static String getEnhanced(Enhancement e, String ua, int timeoutMs) throws IOException {
        String url = e.url;
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("不支持的链接协议（需 M2 JS 运行时）：" + url);
        }
        Request.Builder rb = new Request.Builder().url(url)
                .header("User-Agent", ua == null || ua.isEmpty() ? DEFAULT_UA : ua)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9");
        for (Map.Entry<String, String> h : e.headers.entrySet()) {
            try {
                rb.header(h.getKey(), h.getValue());
            } catch (Throwable ignored) {
            }
        }
        if ("POST".equals(e.method)) {
            rb.post(RequestBody.create(new byte[0], null));
        }
        applyThreadTag(rb);
        OkHttpClient ce = timeoutMs > 0
                ? clientFor(url).newBuilder()
                        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .build()
                : clientFor(url);
        try (Response resp = ce.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + " " + url);
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("空响应 " + url);
            // 8.83 CharsetStringConvert：显式给了 charset 就强制用它；没给时按响应
            // Content-Type 的 charset 解码（OkHttp ResponseBody.string() 的行为）。
            byte[] bytes = body.bytes();
            if (e.charset != null && !e.charset.isEmpty()) {
                try {
                    return new String(bytes, e.charset);
                } catch (Throwable t) {
                    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            java.nio.charset.Charset cs = java.nio.charset.StandardCharsets.UTF_8;
            try {
                okhttp3.MediaType ct = body.contentType();
                if (ct != null && ct.charset() != null) cs = ct.charset();
            } catch (Throwable ignored) {
            }
            return new String(bytes, cs);
        }
    }
}
