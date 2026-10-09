package com.github.catvod.net;

import androidx.annotation.NonNull;

import com.github.catvod.bean.Doh;
import com.github.catvod.utils.Util;

import java.net.Inet4Address;
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
    private volatile List<DnsOverHttps> fallbackDohList;

    public OkDns() {
        this.map = new ConcurrentHashMap<>();
    }

    public synchronized void setDoh(Doh item) {
        boolean wasCustom = isCustom();
        HttpUrl url = HttpUrl.parse(item.getUrl());
        this.doh = url == null ? null : new DnsOverHttps.Builder().client(new OkHttpClient()).url(url).bootstrapDnsHosts(item.getHosts()).build();
        this.supplier = null;
        if (wasCustom != isCustom()) OkHttp.resetClients();
    }

    public synchronized void setDoh(Supplier<Doh> supplier) {
        this.supplier = supplier;
    }

    /**
     * True when this wrapper actually customizes resolution (DoH provider
     * selected or host overrides present). When false, callers should use
     * {@link Dns#SYSTEM} directly so behavior is identical to a stock client.
     */
    public boolean isCustom() {
        return doh != null || !map.isEmpty();
    }

    public void clear() {
        map.clear();
    }

    public void addAll(List<String> hosts) {
        boolean wasCustom = isCustom();
        map.putAll(hosts.stream().filter(Objects::nonNull).map(host -> host.split("=", 2)).filter(splits -> splits.length == 2).collect(Collectors.toMap(s -> s[0].trim(), s -> s[1].trim(), (oldHost, newHost) -> newHost)));
        if (!wasCustom && isCustom()) OkHttp.resetClients();
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
        List<InetAddress> addresses;
        try {
            addresses = (doh != null ? doh : Dns.SYSTEM).lookup(target);
        } catch (UnknownHostException e) {
            // System mode with broken system DNS (NXDOMAIN / timeout / poisoned
            // resolver): try public DoH fallbacks before giving up, so System
            // mode keeps working on such networks. Provider modes rethrow
            // unchanged. Fallback clients carry bootstrap IPs for the DoH
            // servers themselves, so they never touch system DNS (which is
            // exactly what's broken here) and never this wrapper: no recursion.
            if (doh != null) throw e;
            addresses = fallbackLookup(target, e);
        }
        // Prefer IPv4: some networks' DNS returns broken IPv6 (e.g. [::]) or hijacked
        // loopback (e.g. 127.0.1.1) for CDN/API hosts, which makes every connection fail.
        // Keep the original list only when no usable IPv4 exists so IPv6-only hosts keep working.
        List<InetAddress> ipv4 = new ArrayList<>(addresses.size());
        for (InetAddress address : addresses) {
            if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isAnyLocalAddress()) ipv4.add(address);
        }
        return ipv4.isEmpty() ? addresses : ipv4;
    }

    private List<InetAddress> fallbackLookup(String target, UnknownHostException original) throws UnknownHostException {
        for (DnsOverHttps fallback : getFallbackDohList()) {
            try {
                return fallback.lookup(target);
            } catch (Exception ignored) {
            }
        }
        throw original;
    }

    private synchronized List<DnsOverHttps> getFallbackDohList() {
        if (fallbackDohList == null) {
            List<DnsOverHttps> list = new ArrayList<>();
            DnsOverHttps tencent = buildFallbackDoh("https://doh.pub/dns-query", "119.29.29.29", "1.12.12.12", "120.53.53.53");
            if (tencent != null) list.add(tencent);
            DnsOverHttps ali = buildFallbackDoh("https://dns.alidns.com/dns-query", "223.5.5.5", "223.6.6.6");
            if (ali != null) list.add(ali);
            fallbackDohList = list;
        }
        return fallbackDohList;
    }

    private DnsOverHttps buildFallbackDoh(String url, String... bootstrapIps) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) return null;
        List<InetAddress> bootstrap = new ArrayList<>(bootstrapIps.length);
        for (String ip : bootstrapIps) {
            try {
                bootstrap.add(InetAddress.getByName(ip));
            } catch (Exception ignored) {
            }
        }
        DnsOverHttps.Builder builder = new DnsOverHttps.Builder().client(new OkHttpClient()).url(parsed);
        if (!bootstrap.isEmpty()) builder.bootstrapDnsHosts(bootstrap);
        return builder.build();
    }

    private synchronized void initDoh(Supplier<Doh> supplier) {
        if (supplier != this.supplier) return;
        setDoh(supplier.get());
    }
}
