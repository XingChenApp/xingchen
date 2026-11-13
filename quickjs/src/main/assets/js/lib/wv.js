// WvSpider 宿主垫片（新格式宿主型 JS 通道专用，仅在 WvSpider 上下文里求值）。
// 宿主面以真实样本实际调用为准：
//   wvB 类源：fetch(url,{headers}) -> {ok,status,url,headers,text(),json(),doc}
//             DOMParser、host.cache.get/set、host.cookies.get
//   Java.* 类源：Java.req(url,opts)（同步形态，{statusCode,headers,body,error}）、
//             Java.setCache/getCache、Result.error(msg)
// 桥接口由 Java 侧（WvHost）注入，全部同步执行：
//   __wvHttp(json)->json、__wvCacheGet(k)、__wvCacheSet(k,v)、__wvCookies(url)、
//   __wvDomParse(html,base)->docId、__wvDomQuery(docId,sel)->json、
//   __wvDomQueryIn(html,sel,base)->json
(function () {
    var cfg = (typeof __wvConfig !== 'undefined' && __wvConfig) || {};
    var cfgTimeoutMs = (Number(cfg.timeout) > 0 ? Number(cfg.timeout) : 60) * 1000;

    function plainHeaders(pairs) {
        var obj = {};
        (pairs || []).forEach(function (p) {
            if (obj[p[0]] === undefined) obj[p[0]] = p[1];
        });
        obj.get = function (name) {
            var lower = String(name).toLowerCase();
            for (var k in obj) if (typeof obj[k] === 'string' && k.toLowerCase() === lower) return obj[k];
            return null;
        };
        return obj;
    }

    // 同步请求核心：opts.timeout 按源约定为秒；其余形态与 Java.req / fetch 共用。
    function wvRequest(url, opts) {
        opts = opts || {};
        var req = {
            url: String(url),
            method: String(opts.method || 'GET').toUpperCase(),
            headers: opts.headers || {},
            body: opts.body == null ? null : String(opts.body),
            redirect: opts.noRedirect !== true && opts.redirect !== 'manual',
            timeoutMs: opts.timeout != null ? Number(opts.timeout) * 1000 : cfgTimeoutMs
        };
        var raw = __wvHttp(JSON.stringify(req));
        try {
            return JSON.parse(raw || '{}');
        } catch (e) {
            return { status: 0, url: req.url, headers: [], body: '', error: 'host bridge bad response' };
        }
    }

    /* ---------- DOM 门面（jsoup 支撑，仅实现样本用到的面） ---------- */

    function makeElement(d, base) {
        if (!d) return null;
        var el = {
            tagName: String(d.tag || '').toUpperCase(),
            textContent: d.text || '',
            _attrs: d.attrs || {},
            _abs: d.abs || {},
            _html: d.html || '',
            getAttribute: function (name) {
                var v = el._attrs[name];
                return v == null ? null : v;
            },
            querySelector: function (sel) {
                var r = queryIn(el._html, sel, base);
                return r.length ? r[0] : null;
            },
            querySelectorAll: function (sel) {
                return queryIn(el._html, sel, base);
            }
        };
        Object.defineProperty(el, 'href', {
            get: function () { return el._abs.href || el._attrs.href || ''; }
        });
        Object.defineProperty(el, 'src', {
            get: function () { return el._abs.src || el._attrs.src || ''; }
        });
        return el;
    }

    function mapElements(json, base) {
        var arr;
        try {
            arr = JSON.parse(json || '[]');
        } catch (e) {
            arr = [];
        }
        return arr.map(function (d) { return makeElement(d, base); });
    }

    function queryIn(html, sel, base) {
        return mapElements(__wvDomQueryIn(html || '', String(sel), base || ''), base);
    }

    function makeDoc(html, base) {
        var docId = __wvDomParse(html || '', base || '');
        return {
            querySelectorAll: function (sel) {
                return mapElements(__wvDomQuery(docId, String(sel)), base);
            },
            querySelector: function (sel) {
                var r = mapElements(__wvDomQuery(docId, String(sel)), base);
                return r.length ? r[0] : null;
            }
        };
    }

    globalThis.DOMParser = function () {};
    globalThis.DOMParser.prototype.parseFromString = function (html, type) {
        return makeDoc(html, '');
    };

    /* ---------- fetch（returnType: dom 时响应带 .doc） ---------- */

    function makeResponse(raw, reqUrl) {
        var bodyText = raw.body == null ? '' : String(raw.body);
        var res = {
            ok: raw.status >= 200 && raw.status < 300,
            status: raw.status || 0,
            url: raw.url || reqUrl,
            headers: plainHeaders(raw.headers),
            text: function () { return Promise.resolve(bodyText); },
            json: function () {
                return Promise.resolve().then(function () { return JSON.parse(bodyText); });
            }
        };
        Object.defineProperty(res, 'doc', {
            get: function () {
                if (!res._doc) res._doc = makeDoc(bodyText, res.url);
                return res._doc;
            }
        });
        return res;
    }

    globalThis.fetch = function (url, opts) {
        return new Promise(function (resolve, reject) {
            var raw;
            try {
                raw = wvRequest(url, opts);
            } catch (e) {
                reject(e);
                return;
            }
            if (raw.error) {
                reject(new Error(raw.error));
                return;
            }
            resolve(makeResponse(raw, String(url)));
        });
    };

    /* ---------- host.*（wvB 类源） ---------- */

    globalThis.host = {
        cache: {
            get: function (key, def) {
                var v = __wvCacheGet(String(key));
                return v == null || v === '' ? (def === undefined ? '' : def) : v;
            },
            set: function (key, value) {
                __wvCacheSet(String(key), String(value == null ? '' : value));
            }
        },
        cookies: {
            get: function (url) {
                return __wvCookies(String(url || '')) || '';
            }
        }
    };

    /* ---------- Java.*（Java 依赖型源） ---------- */

    globalThis.Java = {
        // 同步返回（源里既有 await 用法也有同步直接读字段用法，同步形态两者都兼容）；
        // 失败不抛、回 error 字段，与样本的 res.error 判读口径一致。
        req: function (url, opts) {
            var raw;
            try {
                raw = wvRequest(url, opts || {});
            } catch (e) {
                return { error: String(e && e.message ? e.message : e), statusCode: 0, headers: {}, body: '' };
            }
            var out = {
                statusCode: raw.status || 0,
                headers: plainHeaders(raw.headers),
                body: raw.body == null ? '' : String(raw.body),
                url: raw.url || String(url)
            };
            if (raw.error) out.error = raw.error;
            return out;
        },
        setCache: function (key, value) {
            __wvCacheSet(String(key), String(value == null ? '' : value));
        },
        getCache: function (key) {
            var v = __wvCacheGet(String(key));
            return v == null ? '' : v;
        }
    };

    /* ---------- Result.* ---------- */

    globalThis.Result = {
        error: function (msg) {
            return { __wvError: String(msg == null ? '' : msg) };
        }
    };
})();
