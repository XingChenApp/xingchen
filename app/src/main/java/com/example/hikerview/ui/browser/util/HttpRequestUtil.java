package com.example.hikerview.ui.browser.util;

import android.text.TextUtils;
import android.util.Log;

import com.alibaba.fastjson.JSON;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import timber.log.Timber;

/**
 * 照官逐字转写（官包 smali_classes2/com/example/hikerview/ui/browser/util/
 * HttpRequestUtil.smali 全文＋$1 匿名信任管理器＋$HeadRequestResponse 内类）：
 * 嗅探栈一员——performHeadRequest 手工跟随重定向（上限 maxRedirects=4）、
 * HTTPS 走 trustAllHosts 全信任（照官原样，失败形态照官：异常吞掉回 null、
 * finally 里 disconnect）。DO_NOT_VERIFY 官为合成 lambda 类，本处以等义
 * lambda 落（官 lambda$static$0 本体即恒回 true）。随④团转写。
 */
public class HttpRequestUtil {

    public static final HostnameVerifier DO_NOT_VERIFY = (hostname, session) -> true;

    private static final String TAG = "HttpRequestUtil";

    public static Map<String, String> commonHeaders;

    public static final int connectTimeout = 5000;
    public static final String defaultCharset = "UTF-8";
    public static final int maxRedirects = 4;
    public static final int readTimeout = 5000;

    static {
        commonHeaders = new HashMap<>();
        commonHeaders.put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1");
    }

    private static void trustAllHosts() {
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                Log.i("trustAllHosts", "checkClientTrusted");
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                Log.i("trustAllHosts", "checkServerTrusted");
            }
        }};
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    public static void main(String[] args) throws Exception {
        HeadRequestResponse headRequestResponse = performHeadRequest("https://disp.titan.mgtv.com/vod.do?fmt=4&pno=1121&fid=3BBD5FD649B8DEB99DBDE005F7304103&file=/c1/2017/08/30_0/3BBD5FD649B8DEB99DBDE005F7304103_20170830_1_1_644.mp4");
        System.out.println(headRequestResponse.getRealUrl());
        System.out.println(JSON.toJSONString(headRequestResponse.getHeaderMap()));
    }

    public static HeadRequestResponse performHeadRequest(String url) throws IOException {
        return performHeadRequest(url, commonHeaders);
    }

    public static HeadRequestResponse performHeadRequest(String url, Map<String, String> headers) throws IOException {
        return performHeadRequestForRedirects(url, headers, 0);
    }

    private static HeadRequestResponse performHeadRequestForRedirects(String url, Map<String, String> headers, int redirectCount) throws IOException {
        URL u = new URL(url);
        HttpURLConnection connection = null;
        try {
            if (u.getProtocol().equalsIgnoreCase("HTTPS")) {
                trustAllHosts();
                HttpsURLConnection httpsConnection = (HttpsURLConnection) u.openConnection();
                httpsConnection.setHostnameVerifier(DO_NOT_VERIFY);
                connection = httpsConnection;
            } else {
                URLConnection urlConnection = u.openConnection();
                if (!(urlConnection instanceof HttpURLConnection)) {
                    return null;
                }
                connection = (HttpURLConnection) urlConnection;
            }
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            Map<String, List<String>> headerFields = connection.getHeaderFields();
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            if (isRedirect(responseCode)) {
                if (redirectCount >= 4) {
                    return new HeadRequestResponse(url, new HashMap<>());
                }
                return performHeadRequestForRedirects(new URL(new URL(url), headerFields.get("Location").get(0)).toString(), headers, redirectCount + 1);
            }
            return new HeadRequestResponse(url, headerFields);
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 302 || statusCode == 301 || statusCode == 303;
    }

    public static String getFileExtensionFromUrl(String url) {
        String lowerCaseUrl = url.toLowerCase();
        if (!TextUtils.isEmpty(lowerCaseUrl)) {
            int lastIndexOfHash = lowerCaseUrl.lastIndexOf('#');
            if (lastIndexOfHash > 0) {
                lowerCaseUrl = lowerCaseUrl.substring(0, lastIndexOfHash);
            }
            int lastIndexOfQuestionMark = lowerCaseUrl.lastIndexOf('?');
            if (lastIndexOfQuestionMark > 0) {
                lowerCaseUrl = lowerCaseUrl.substring(0, lastIndexOfQuestionMark);
            }
            int lastIndexOfSlash = lowerCaseUrl.lastIndexOf('/');
            if (lastIndexOfSlash >= 0) {
                lowerCaseUrl = lowerCaseUrl.substring(lastIndexOfSlash + 1);
            }
            if (!lowerCaseUrl.isEmpty()) {
                int lastIndexOfDot = lowerCaseUrl.lastIndexOf('.');
                if (lastIndexOfDot >= 0) {
                    return lowerCaseUrl.substring(lastIndexOfDot + 1);
                }
            }
            return "";
        }
        return "";
    }

    public static class HeadRequestResponse {

        private Map<String, List<String>> headerMap;
        private String realUrl;

        public HeadRequestResponse() {
        }

        public HeadRequestResponse(String realUrl, Map<String, List<String>> headerMap) {
            this.realUrl = realUrl;
            this.headerMap = headerMap;
        }

        public String getRealUrl() {
            return realUrl;
        }

        public void setRealUrl(String realUrl) {
            this.realUrl = realUrl;
        }

        public Map<String, List<String>> getHeaderMap() {
            return headerMap;
        }

        public void setHeaderMap(Map<String, List<String>> headerMap) {
            this.headerMap = headerMap;
        }
    }
}
