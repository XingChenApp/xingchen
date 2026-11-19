package com.github.catvod.crawler;

import android.content.Context;
import android.net.Uri;

import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import okhttp3.Dns;
import okhttp3.OkHttpClient;

public abstract class Spider {

    public String siteKey;

    // 新形态 py 底座（runtime/base_spider.py）经 JavaSpider.danmakuUrl(siteKey, id) 取弹幕地址，
    // 与 FongMi 5.8.9 crawler/Spider 同名同义补齐；消费方是 app SiteApi.danmaku＋播放器订阅会话。
    public String danmakuUrl(String id) {
        return danmakuUrl(siteKey, id);
    }

    public static String danmakuUrl(String siteKey, String id) {
        if (siteKey == null || siteKey.isEmpty() || id == null || id.isEmpty()) throw new IllegalArgumentException("Danmaku requires a site key and ID");
        return new Uri.Builder().scheme("spider").authority(siteKey).appendPath(id).build().toString();
    }

    public static Dns safeDns() {
        return OkHttp.dns();
    }

    public static OkHttpClient client() {
        return OkHttp.client();
    }

    public void init(Context context) throws Exception {
    }

    public void init(Context context, String extend) throws Exception {
        init(context);
    }

    public String homeContent(boolean filter) throws Exception {
        return "";
    }

    public String homeVideoContent() throws Exception {
        return "";
    }

    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return "";
    }

    public String detailContent(List<String> ids) throws Exception {
        return "";
    }

    public String searchContent(String key, boolean quick) throws Exception {
        return "";
    }

    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return "";
    }

    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return "";
    }

    public String liveContent(String url) throws Exception {
        return "";
    }

    public boolean manualVideoCheck() throws Exception {
        return false;
    }

    public boolean isVideoFormat(String url) throws Exception {
        return false;
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        return null;
    }

    public String action(String action) throws Exception {
        return null;
    }

    // 与 FongMi 5.8.9 crawler/Spider 同名同义：源自供弹幕口，默认不支持、抛
    // UnsupportedOperationException；只有新形态 py 桥（chaquo SpiderNew）真实现。
    // 消费方是 app SiteApi.danmaku（spider://<siteKey>/<id> 解析后调本口）。
    public AutoCloseable danmaku(String id, Consumer<JSONObject> emit) throws Exception {
        throw new UnsupportedOperationException("Spider does not support live danmaku");
    }

    public void destroy() {
    }
}
