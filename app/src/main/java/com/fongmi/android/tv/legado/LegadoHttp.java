package com.fongmi.android.tv.legado;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 书源网络层：接口＋实现分离，离线回放脚手架可注入假实现。
 * 超时与异常隔离：任何失败只抛 IOException/LegadoException，不拖垮宿主。
 * 宿主实现见 LegadoHttpAndroid（复用 HkHttp 的 OkHttp 客户端，含节点选路）。
 */
public interface LegadoHttp {

    int CONNECT_TIMEOUT_MS = 12000;
    int READ_TIMEOUT_MS = 20000;

    class Result {
        public byte[] body = new byte[0];
        public String contentType = "";
        public final Map<String, String> cookies = new HashMap<>();

        public int size() {
            return body == null ? 0 : body.length;
        }
    }

    Result fetch(String url, String method, Map<String, String> headers, byte[] body) throws IOException;
}
