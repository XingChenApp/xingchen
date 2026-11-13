package com.fongmi.android.tv.server.process;

import static fi.iki.elonen.NanoHTTPD.MIME_HTML;
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.github.catvod.utils.Asset;

import org.json.JSONObject;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class Parse implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/parse");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            Map<String, String> params = session.getParms();
            String html = String.format(Asset.read("parse.html"), jsString(params.get("jxs")), jsString(params.get("url")));
            return newFixedLengthResponse(Status.OK, MIME_HTML, html);
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }

    // 把请求参数编码成可安全内嵌进 <script> 的 JS 字符串字面量：JSONObject.quote
    // 负责引号/反斜杠/控制字符转义，再把 <、>、& 转成 unicode 转义，防止参数里的
    // "</script>" 提前闭合脚本块逃逸到页面上下文执行任意脚本。
    private static String jsString(String value) {
        if (value == null) value = "";
        return JSONObject.quote(value).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
    }
}
