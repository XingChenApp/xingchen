package com.fongmi.android.tv.ui.hk;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import androidx.webkit.ServiceWorkerClientCompat;
import androidx.webkit.ServiceWorkerControllerCompat;
import androidx.webkit.WebViewFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * 照 8.83 com.example.hikerview.ui.browser.webview.WebViewHelper 中与嗅探解析相关的部分转写：
 * RESPONSE_IGNORE 常量与 ServiceWorker 拦截器注册表（原版同样走 androidx.webkit）。
 * 原版 WebViewHelper 其余浏览器功能（过滤名单等）属浏览器子系统，未移植。
 */
public class HkWebViewHelper {

    /** 原版定义：new WebResourceResponse("text/plain", "UTF-8", null)。 */
    public static final WebResourceResponse RESPONSE_IGNORE =
            new WebResourceResponse("text/plain", "UTF-8", (java.io.InputStream) null);

    /** 原版 ServiceWorkerInterceptor 的等价接缝：返回非 null 即拦截该服务工作者请求。 */
    public interface ServiceWorkerInterceptor {
        WebResourceResponse shouldInterceptRequest(WebResourceRequest request);
    }

    private static List<ServiceWorkerInterceptor> serviceWorkerInterceptors;

    public static synchronized void addServiceWorkerClient(ServiceWorkerInterceptor interceptor) {
        if (serviceWorkerInterceptors == null) serviceWorkerInterceptors = new ArrayList<>();
        serviceWorkerInterceptors.add(interceptor);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(new ServiceWorkerClientCompat() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    List<ServiceWorkerInterceptor> copy;
                    synchronized (HkWebViewHelper.class) {
                        copy = serviceWorkerInterceptors == null
                                ? new ArrayList<>() : new ArrayList<>(serviceWorkerInterceptors);
                    }
                    for (ServiceWorkerInterceptor it : copy) {
                        WebResourceResponse resp = it.shouldInterceptRequest(request);
                        if (resp != null) return resp;
                    }
                    return null;
                }
            });
        }
    }

    public static synchronized void removeServiceWorkerClient(ServiceWorkerInterceptor interceptor) {
        if (serviceWorkerInterceptors != null) serviceWorkerInterceptors.remove(interceptor);
    }
}
