package com.github.catvod.net;

import androidx.annotation.NonNull;

import com.github.catvod.bean.Doh;
import com.github.catvod.utils.Util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

public class OkDns implements Dns {

    private final ConcurrentHashMap<String, String> map;
    private volatile Supplier<Doh> supplier;
    private volatile DnsOverHttps doh;
    private OkHttpClient dohClient;

    public OkDns() {
        this.map = new ConcurrentHashMap<>();
    }

    public synchronized void setDoh(Doh item) {
        HttpUrl url = HttpUrl.parse(item.getUrl());
        OkHttpClient old = dohClient;
        dohClient = url == null ? null : new OkHttpClient();
        this.doh = url == null ? null : new DnsOverHttps.Builder().client(dohClient).url(url).bootstrapDnsHosts(item.getHosts()).build();
        this.supplier = null;
        // 换 DoH 时把旧 client 的线程池与连接池收掉：旧写法每次新建裸
        // client 从不回收，反复改 DoH 设置会持续累积线程与连接。
        shutdownClient(old);
        // 注意：不要 clear map，它是 hosts 覆盖表（接口下发的 host=ip），不是 DNS 缓存
    }

    private static void shutdownClient(OkHttpClient client) {
        if (client == null) return;
        try {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        } catch (Throwable ignored) {
        }
    }

    public synchronized void setDoh(Supplier<Doh> supplier) {
        this.supplier = supplier;
    }

    /** 清除 DoH 设置，恢复系统 DNS（用于“系统”模式） */
    public synchronized void clearDoh() {
        this.doh = null;
        this.supplier = null;
        OkHttpClient old = dohClient;
        dohClient = null;
        shutdownClient(old);
        // 注意：不要 clear map，它是 hosts 覆盖表，不是 DNS 缓存
    }

    public void clear() {
        map.clear();
    }

    public void addAll(List<String> hosts) {
        map.putAll(hosts.stream().filter(Objects::nonNull).map(host -> host.split("=", 2)).filter(splits -> splits.length == 2).collect(Collectors.toMap(s -> s[0].trim(), s -> s[1].trim(), (oldHost, newHost) -> newHost)));
    }

    private String get(String hostname) {
        String target = map.get(hostname);
        if (target != null) return target;
        for (Map.Entry<String, String> entry : map.entrySet()) if (Util.containOrMatch(hostname, entry.getKey())) return entry.getValue();
        return hostname;
    }

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        Supplier<Doh> supplier = this.supplier;
        if (supplier != null) initDoh(supplier);
        String target = get(hostname);
        List<InetAddress> result;
        // 系统模式：先试系统 DNS，失败自动回退到腾讯 DoH（应对 DNS 污染）
        if (doh == null) {
            try {
                result = Dns.SYSTEM.lookup(target);
            } catch (UnknownHostException e) {
                // 系统 DNS 失败，回退到内置腾讯 DoH
                result = getFallbackDoh().lookup(target);
            }
            result = sanitize(result);
            if (result.isEmpty()) {
                // 系统解析只回了 [::] 这类空地址（sanitize 后为空）：等同解析失败，
                // 换腾讯 DoH 再试一次（真机 CloudFront 域名实证：系统回 [::]、
                // 播放器直连空地址被拒、整场播放失败）
                try {
                    List<InetAddress> viaDoh = sanitize(getFallbackDoh().lookup(target));
                    if (!viaDoh.isEmpty()) result = viaDoh;
                } catch (UnknownHostException ignored) {
                }
            }
        } else {
            result = sanitize(doh.lookup(target));
        }
        // 清洗后仍为空就按解析失败抛错，绝不把空地址交给连接层
        if (result.isEmpty()) throw new UnknownHostException(hostname);
        return result;
    }

    /**
     * 地址清洗（播放器与点播共用此层，小程序之外的源也全覆盖）：
     * 先无条件剔除空地址（:: / 0.0.0.0）——它在任何组合下都不是可连接目标，
     * 旧版只在「多地址且混有 IPv4」时才顺带滤掉，单条 [::] 或纯空地址列表会原样漏给
     * 连接层（10-15 真机播放失败实证）。再做 IPv4 优选（剔劫持回环），
     * 无 IPv4 时回退清洗后的原列表（保证纯 IPv6 站点可用）。
     * 结果可为空表，由调用方决定兜底（换 DoH 或抛解析失败）。
     */
    private List<InetAddress> sanitize(List<InetAddress> addresses) {
        List<InetAddress> usable = new ArrayList<>();
        if (addresses != null) {
            for (InetAddress addr : addresses) {
                if (addr != null && !addr.isAnyLocalAddress()) usable.add(addr);
            }
        }
        if (usable.size() <= 1) return usable;
        List<InetAddress> ipv4 = new ArrayList<>();
        for (InetAddress addr : usable) {
            if (addr instanceof java.net.Inet6Address) continue;
            String host = addr.getHostAddress();
            if (host == null) continue;
            // 跳过 127.x.x.x 回环（劫持常见）
            if (host.startsWith("127.")) continue;
            ipv4.add(addr);
        }
        return ipv4.isEmpty() ? usable : ipv4;
    }

    private volatile Dns fallbackDoh;

    private Dns getFallbackDoh() {
        if (fallbackDoh == null) {
            synchronized (this) {
                if (fallbackDoh == null) {
                    // 腾讯 DoH，bootstrap 用硬编码 IP 不依赖系统 DNS
                    try {
                        HttpUrl url = HttpUrl.parse("https://doh.pub/dns-query");
                        fallbackDoh = new DnsOverHttps.Builder()
                                .client(new OkHttpClient())
                                .url(url)
                                .bootstrapDnsHosts(
                                        java.net.InetAddress.getByName("119.29.29.29"),
                                        java.net.InetAddress.getByName("182.254.116.116"))
                                .build();
                    } catch (Exception e) {
                        // bootstrap 解析失败就不用回退
                        fallbackDoh = Dns.SYSTEM;
                    }
                }
            }
        }
        return fallbackDoh;
    }

    private synchronized void initDoh(Supplier<Doh> supplier) {
        if (supplier != this.supplier) return;
        setDoh(supplier.get());
    }
}
