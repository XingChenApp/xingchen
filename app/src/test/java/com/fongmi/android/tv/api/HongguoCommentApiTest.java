package com.fongmi.android.tv.api;

import org.json.JSONObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * HongguoCommentApi 解析单测：头像守卫口径。
 * 红果接口约半数头像是 http:// 明文下发，旧守卫只认 https:// 把这批在解析期
 * 清零（真机成批只剩占位，见 hongguo-avatar-partial-trace.md）。样本是真响应
 * 原文（hongguo-fetch-samples 的 episode-list-p1 / series-replies-p1）。
 */
public class HongguoCommentApiTest {

    private static final String SERIES = "7695727473111092286";
    private static final String VID = "7695729394496588862";

    private String readResource(String resource) throws Exception {
        InputStream stream = getClass().getResourceAsStream(resource);
        assertNotNull(stream);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    @Test
    public void httpAvatarsSurviveCommentParsing() throws Exception {
        JSONObject sample = new JSONObject(readResource("/hongguo-episode-list-p1.json"));
        HongguoCommentApi.Page page = HongguoCommentApi.parseList(sample, SERIES, VID, "", false);
        assertNotNull(page);
        assertFalse(page.items.isEmpty());
        int http = 0;
        int https = 0;
        for (HongguoCommentApi.Comment item : page.items) {
            if (item.avatar.startsWith("http://")) http++;
            else if (item.avatar.startsWith("https://")) https++;
            else assertEquals("非空头像只许是 http/https 地址", "", item.avatar);
        }
        // 旧守卫下 http 必为 0（全被清零）；真样本里 http 是多数，放行后必须留得住
        assertTrue("http 头像应被放行保留，实际 http=" + http, http > 0);
        assertTrue("https 头像应照旧保留，实际 https=" + https, https > 0);
    }

    @Test
    public void httpAvatarsSurviveReplyParsing() throws Exception {
        JSONObject sample = new JSONObject(readResource("/hongguo-series-replies-p1.json"));
        HongguoCommentApi.Page page = HongguoCommentApi.parseReplies(sample, SERIES, SERIES, "");
        assertNotNull(page);
        assertFalse(page.items.isEmpty());
        boolean anyHttp = false;
        for (HongguoCommentApi.Comment item : page.items) {
            assertTrue("非空头像只许是 http/https 地址",
                    item.avatar.isEmpty() || item.avatar.startsWith("http://") || item.avatar.startsWith("https://"));
            if (item.avatar.startsWith("http://")) anyHttp = true;
        }
        // 该样本唯一回复行的 user_avatar 本身就是 http，旧守卫下必被清零
        assertTrue("回复的 http 头像应被放行保留", anyHttp);
    }

    @Test
    public void avatarUrlGateRejectsNonHttpSchemes() {
        assertTrue(HongguoCommentApi.isAvatarUrl("http://a.b/c.jpg"));
        assertTrue(HongguoCommentApi.isAvatarUrl("https://a.b/c.jpg"));
        assertFalse(HongguoCommentApi.isAvatarUrl("ftp://a.b/c.jpg"));
        assertFalse(HongguoCommentApi.isAvatarUrl("file:///c.jpg"));
        assertFalse(HongguoCommentApi.isAvatarUrl(""));
        assertFalse(HongguoCommentApi.isAvatarUrl(null));
    }
}
