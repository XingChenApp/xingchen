package com.example.hikerview.ui.ext;

import com.fongmi.android.tv.App;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.dnsoverhttps.DnsOverHttps;

/**
 * 官 {@code com.example.hikerview.ui.ext.DohProvidersKt}（DohProviders.kt，
 * smali_classes2，34 法）中 {@code MoreSettingOfficer.loadDns} 所需段的
 * 照官转写（2026-11-14 ④团）：cacheClient、八家 DoH 工厂、dohCustom、
 * checkToHttpUrl、toHostMap。本类置于 main 源集：消费方
 * MoreSettingOfficer（mobile 源集）与引擎侧均可见，照本树
 * PreferenceMgr/UriUtils/FileUtil 的落位先例。
 *
 * <p>未在此重复转写的段（已等价落位、不另起第二套 DoH 体系）：auto-DNS
 * 子系统——autoDomain/dnsAuto 两字段、autoDNSCheckUrl 常量、initDnsAuto、
 * loadAutoDNS/loadAutoDNSFromFile/loadAutoDNSFromRemote/clearAutoDomain、
 * registerDNS、shouldUseAutoDNS、buildAutoClient、buildAutoDNSClient
 * 两重载、getDohUrl——已整套落位于 {@code HkHttp}（AUTO_DOMAIN/dnsAuto/
 * AUTO_DNS_CHECK_URL/initAutoDns/maintainAutoDomain/loadAutoDNS/
 * registerAutoDomain/shouldUseAutoDNS/buildAutoDNSClient 两重载），
 * 逐段对照见 worknotes/hk-14-fill-parts/c4.md。PREF_DOH_* 八个索引
 * 常量咱家无消费方、非 loadDns 所需，未转写、记名在案。
 *
 * <p>官 DnsOverHttps2（DnsOverHttps2.smali，1306 行）是 okhttp
 * DnsOverHttps 的改名副本加 hosts 扩展：Builder 默认值逐项核过官
 * smali——includeIPv6=true、resolvePublicAddresses=true、
 * resolvePrivateAddresses=false、systemDns=Dns.SYSTEM，build() 以
 * BootstrapDns(url.host, bootstrapDnsHosts) 包装传输 client——与
 * okhttp3.dnsoverhttps.DnsOverHttps 同构，故本体以 okhttp 版承接
 * （HkHttp.buildAliDoh 既有先例）；DnsOverHttps2 独有的 hosts 与
 * 127.0.0.1 两支 lookupHttps 头部语义（.line 76-83）由 dohCustom 的
 * DohHostsDns 包装复刻。守卫顺序一处微差记名：官 lookup() 先过
 * 私有/公有主机守卫再进 lookupHttps，本包装的 hosts 命中与
 * 127.0.0.1 支先于 okhttp 版 lookup() 的守卫——仅「dns 值非 http
 * 形或绑表命中、且被查主机名为私有主机」时结果不同（官抛
 * UnknownHostException、本包装回静态表/系统解析），loadDns 路径
 * hosts 恒 null、自定义值官设置面明示仅支持 https，此微差不触
 * 正常路径。
 */
public final class DohProvidersKt {

    private DohProvidersKt() {
    }

    /**
     * 官 cacheClient（DohProvidersKt.smali:372，.line 177-191）：每次
     * 调用新建——Cache( Application.getContext().cacheDir，官
     * Application.getContext() 接本树 App.get()；容量 0x1400000=
     * 20971520)＋Brotli 拦截器＋网络拦截器（cacheClient$lambda$1，
     * .line 182-189：响应去 Pragma 头、Cache-Control 强制
     * maxAge 10 分钟）。官 BrotliInterceptor.INSTANCE 直装；本树
     * okhttp-brotli 不在依赖清单（gradle/libs.versions.toml 只有
     * okhttp/okhttp-dnsoverhttps/okhttp-logging-interceptor），照
     * HkHttp 既有先例反射装，类在场时行为与官一致。
     */
    public static OkHttpClient cacheClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .cache(new Cache(App.get().getCacheDir(), 20971520L));
        try {
            Class<?> c = Class.forName("okhttp3.brotli.BrotliInterceptor");
            Object inst = c.getField("INSTANCE").get(null);
            builder.addInterceptor((Interceptor) inst);
        } catch (Throwable ignored) {
        }
        builder.addNetworkInterceptor(chain -> {
            Response response = chain.proceed(chain.request());
            CacheControl cacheControl = new CacheControl.Builder()
                    .maxAge(10, TimeUnit.MINUTES)
                    .build();
            return response.newBuilder()
                    .removeHeader("Pragma")
                    .header("Cache-Control", cacheControl.toString())
                    .build();
        });
        return builder.build();
    }

    /**
     * 八家工厂的共同官形（各 dohX 法体逐字同构，DohProvidersKt.smali
     * 各法 .line 首段）：DnsOverHttps2.Builder().client(cacheClient())
     * .url(HttpUrl.get(官址)).bootstrapDnsHosts(官自举 IP 序).build()
     * 后 builder.dns(之)。定值（址与 IP）逐家写在各工厂法内、本助手
     * 只收拢装配形。IP 字面量经 InetAddress.getByName 不查 DNS；
     * 官 Kotlin 无受检异常约束，Java 形以 addr() 包裹（定值均为
     * 字面量、该支不可达）。
     */
    private static OkHttpClient.Builder doh(OkHttpClient.Builder builder, String url, String... bootstrapIps) {
        List<InetAddress> bootstrap = new ArrayList<>();
        for (String ip : bootstrapIps) {
            bootstrap.add(addr(ip));
        }
        DnsOverHttps dns = new DnsOverHttps.Builder()
                .client(cacheClient())
                .url(HttpUrl.get(url))
                .bootstrapDnsHosts(bootstrap)
                .build();
        return builder.dns(dns);
    }

    private static InetAddress addr(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 官 dohCloudflare（DohProvidersKt.smali:957，.line 39-53）。 */
    public static OkHttpClient.Builder dohCloudflare(OkHttpClient.Builder builder) {
        return doh(builder, "https://cloudflare-dns.com/dns-query",
                "162.159.36.1", "162.159.46.1", "1.1.1.1", "1.0.0.1", "162.159.132.53",
                "2606:4700:4700::1111", "2606:4700:4700::1001", "2606:4700:4700::0064", "2606:4700:4700::6400");
    }

    /** 官 dohGoogle（DohProvidersKt.smali:1321，.line 56-65）。 */
    public static OkHttpClient.Builder dohGoogle(OkHttpClient.Builder builder) {
        return doh(builder, "https://dns.google/dns-query",
                "8.8.4.4", "8.8.8.8", "2001:4860:4860::8888", "2001:4860:4860::8844");
    }

    /** 官 dohAdGuard（DohProvidersKt.smali:723，.line 70-79）。 */
    public static OkHttpClient.Builder dohAdGuard(OkHttpClient.Builder builder) {
        return doh(builder, "https://dns-unfiltered.adguard.com/dns-query",
                "94.140.14.140", "94.140.14.141", "2a10:50c0::1:ff", "2a10:50c0::2:ff");
    }

    /** 官 dohQuad9（DohProvidersKt.smali:1540，.line 82-91）。 */
    public static OkHttpClient.Builder dohQuad9(OkHttpClient.Builder builder) {
        return doh(builder, "https://dns.quad9.net/dns-query",
                "9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9");
    }

    /** 官 dohAliDNS（DohProvidersKt.smali:840，.line 94-103）。 */
    public static OkHttpClient.Builder dohAliDNS(OkHttpClient.Builder builder) {
        return doh(builder, "https://dns.alidns.com/dns-query",
                "223.5.5.5", "223.6.6.6", "2400:3200::1", "2400:3200:baba::1");
    }

    /** 官 dohDNSPod（DohProvidersKt.smali:1234，.line 106-113）。 */
    public static OkHttpClient.Builder dohDNSPod(OkHttpClient.Builder builder) {
        return doh(builder, "https://doh.pub/dns-query",
                "1.12.12.12", "120.53.53.53");
    }

    /** 官 doh360（DohProvidersKt.smali:561，.line 116-128）。 */
    public static OkHttpClient.Builder doh360(OkHttpClient.Builder builder) {
        return doh(builder, "https://doh.360.cn/dns-query",
                "101.226.4.6", "218.30.118.6", "123.125.81.6", "140.207.198.6",
                "180.163.249.75", "101.199.113.208", "36.99.170.86");
    }

    /** 官 dohQuad101（DohProvidersKt.smali:1438，.line 131-139）。 */
    public static OkHttpClient.Builder dohQuad101(OkHttpClient.Builder builder) {
        return doh(builder, "https://dns.twnic.tw/dns-query",
                "101.101.101.101", "2001:de4::101", "2001:de4::102");
    }

    /**
     * 官 checkToHttpUrl（DohProvidersKt.smali:495，.line 157-160）：
     * 以 "http" 开头（区分大小写，官 startsWith$default 形）走
     * HttpUrl.get 本值（畸形抛错、由 dohCustom 的 catch 收住）；
     * 否则回假端点 http://127.0.0.1:8080。
     */
    public static HttpUrl checkToHttpUrl(String url) {
        if (!url.startsWith("http")) {
            return HttpUrl.get("http://127.0.0.1:8080");
        }
        return HttpUrl.get(url);
    }

    /**
     * 官 toHostMap（DohProvidersKt.smali:2299，.line 163-169）：值以
     * "http" 开头或 bindUrl 为 null 回 null；否则取 bindUrl 的 host
     * （null 回 null），以值按 " " 切分逐个 InetAddress.getByName
     * 成表 {host: IP 表}。异常（bindUrl 畸形/IP 不可解）照官外抛、
     * 由 dohCustom 的 catch 收住。
     */
    public static Map<String, List<InetAddress>> toHostMap(String dnsValue, String bindUrl) throws Exception {
        if (!dnsValue.startsWith("http") && bindUrl != null) {
            Map<String, List<InetAddress>> map = new HashMap<>();
            String host = new URL(bindUrl).getHost();
            if (host != null) {
                List<InetAddress> list = new ArrayList<>();
                for (String ip : dnsValue.split(" ")) {
                    list.add(InetAddress.getByName(ip));
                }
                map.put(host, list);
                return map;
            }
        }
        return null;
    }

    /**
     * 官 dohCustom（DohProvidersKt.smali:1149，.line 144-151）：
     * DnsOverHttps2.Builder().client(cacheClient())
     * .url(checkToHttpUrl(url)).withHosts(toHostMap(url, host))
     * .build() 后 builder.dns(之)，全程 try/catch(Exception)
     * printStackTrace、失败 builder 原样。官 dohCustom$default
     * （mask 0x2 时 host=null）即三参直调、无单独形。withHosts
     * 语义由 DohHostsDns 复刻（见类头记名微差）。
     */
    public static OkHttpClient.Builder dohCustom(OkHttpClient.Builder builder, String url, String host) {
        try {
            HttpUrl httpUrl = checkToHttpUrl(url);
            DnsOverHttps doh = new DnsOverHttps.Builder()
                    .client(cacheClient())
                    .url(httpUrl)
                    .build();
            Map<String, List<InetAddress>> hosts = toHostMap(url, host);
            builder.dns(new DohHostsDns(doh, hosts, httpUrl.host()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return builder;
    }

    /**
     * 官 DnsOverHttps2.lookupHttps 头两支复刻（DnsOverHttps2.smali
     * .line 76-83）：hosts 非空表命中且表项非空直回表项；端点 host
     * 以 "127.0.0.1" 开头回系统解析；余下走 DoH 本体查询。
     */
    private static final class DohHostsDns implements Dns {
        private final DnsOverHttps doh;
        private final Map<String, List<InetAddress>> hosts;
        private final String endpointHost;

        DohHostsDns(DnsOverHttps doh, Map<String, List<InetAddress>> hosts, String endpointHost) {
            this.doh = doh;
            this.hosts = hosts;
            this.endpointHost = endpointHost;
        }

        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            if (hosts != null && hosts.containsKey(hostname)) {
                List<InetAddress> hit = hosts.get(hostname);
                if (hit != null && !hit.isEmpty()) {
                    return hit;
                }
            }
            if (endpointHost != null && endpointHost.startsWith("127.0.0.1")) {
                return Dns.SYSTEM.lookup(hostname);
            }
            return doh.lookup(hostname);
        }
    }
}
