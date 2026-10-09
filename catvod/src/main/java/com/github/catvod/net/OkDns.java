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
    private volatile DnsOverHttps fallbackDoh;

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
            // resolver): try a public DoH once before giving up, so System mode
            // keeps working on such networks. Provider modes rethrow unchanged.
            // The fallback client uses plain system DNS (never this wrapper),
            // so there is no recursion.
            DnsOverHttps fallback = (doh == null) ? getFallbackDoh() : null;
            if (fallback == null) throw e;
            try {
                addresses = fallback.lookup(target);
            } catch (Exception ex) {
                throw e;
            }
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

    private synchronized DnsOverHttps getFallbackDoh() {
        if (fallbackDoh == null) {
            HttpUrl url = HttpUrl.parse("https://doh.pub/dns-query");
            if (url != null) fallbackDoh = new DnsOverHttps.Builder().client(new OkHttpClient()).url(url).build();
        }
        return fallbackDoh;
    }

    private synchronized void initDoh(Supplier<Doh> supplier) {
        if (supplier != this.supplier) return;
        setDoh(supplier.get());
    }
}
