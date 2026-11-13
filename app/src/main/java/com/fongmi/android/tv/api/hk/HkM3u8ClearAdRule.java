package com.fongmi.android.tv.api.hk;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * M3u8 去广告规则（移植自 8.83）。
 *
 * <p>8.83 原文：{@code smali_classes2/com/example/hikerview/ui/video/model/M3u8ClearAdRule.smali}
 *
 * <p>字段：
 * <ul>
 *   <li>{@code hosts}：生效域名列表（为空则全匹配）；</li>
 *   <li>{@code name}：规则名称；</li>
 *   <li>{@code regex}：广告匹配规则列表。分两种：
 *     <ul>
 *       <li>含 {@code #EXTINF} 或 {@code #EXT-X-DISCONTINUITY} 的：按正则直接删除匹配片段；</li>
 *       <li>纯数字（如 {@code "15"}）：按分片总时长匹配，删除总时长以该数字开头的 DISCONTINUITY 段。</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class HkM3u8ClearAdRule {

    @SerializedName("hosts")
    private List<String> hosts;

    @SerializedName("name")
    private String name;

    @SerializedName("regex")
    private List<String> regex;

    public HkM3u8ClearAdRule() {
    }

    public HkM3u8ClearAdRule(List<String> hosts, String name, List<String> regex) {
        this.hosts = hosts;
        this.name = name;
        this.regex = regex;
    }

    public List<String> getHosts() {
        return hosts;
    }

    public void setHosts(List<String> hosts) {
        this.hosts = hosts;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getRegex() {
        return regex;
    }

    public void setRegex(List<String> regex) {
        this.regex = regex;
    }
}
