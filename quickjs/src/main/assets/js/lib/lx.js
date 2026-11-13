/*
 * 落雪（LX Music）自定义源垫片：注入 globalThis.lx，与落雪官方 FAQ 的宿主契约对齐。
 * 宿主（LxBridge）预先注入的原生函数：
 *   __lxSend(event, json)            脚本 -> 宿主事件（inited / updateAlert）
 *   __lxRequest(url, optsJson)       同步网络（走壳子 OkHttp），返回 {resp} 或 {err} 的 JSON
 *   __lxLoginCookie()                -> 壳子统一登录托管的 Cookie 串（脚本头部声明 @loginProvider 才有，未声明恒空串）
 *   __lxLoginRequest()               -> 请壳子弹统一登录框，返回是否已登录（同上，未声明恒 false）
 *   __lxMd5(str)                     -> hex 字符串（同步）
 *   __lxAes(dataB64, mode, keyB64, ivB64) -> 密文 base64（同步）
 *   __lxRsa(dataB64, pem)            -> base64（同步，PKCS1 填充）
 *   __lxRandom(size)                 -> base64（同步）
 *   __lxMeta                         {version, name, version2(scriptVersion), author, description, homepage, rawScript}
 * 脚本只能用这里给出的 lx 能力：无文件、无系统访问，与 FreeJS 同等沙箱。
 */
(function () {
    'use strict';

    /* ---------------- 字节编解码（buffer 子集） ---------------- */
    var B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    var B64I = {};
    for (var i = 0; i < B64.length; i++) B64I[B64.charAt(i)] = i;

    function utf8Encode(str) {
        var out = [];
        for (var i = 0; i < str.length; i++) {
            var c = str.charCodeAt(i);
            if (c < 0x80) {
                out.push(c);
            } else if (c < 0x800) {
                out.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f));
            } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < str.length) {
                var c2 = str.charCodeAt(i + 1);
                if (c2 >= 0xdc00 && c2 <= 0xdfff) {
                    var cp = 0x10000 + ((c - 0xd800) << 10) + (c2 - 0xdc00);
                    out.push(0xf0 | (cp >> 18), 0x80 | ((cp >> 12) & 0x3f),
                        0x80 | ((cp >> 6) & 0x3f), 0x80 | (cp & 0x3f));
                    i++;
                } else {
                    out.push(0xef, 0xbf, 0xbd);
                }
            } else {
                out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
            }
        }
        return new Uint8Array(out);
    }

    function utf8Decode(u8) {
        var out = '';
        var i = 0;
        while (i < u8.length) {
            var b = u8[i];
            var cp;
            if (b < 0x80) {
                cp = b;
                i += 1;
            } else if ((b & 0xe0) === 0xc0 && i + 1 < u8.length) {
                cp = ((b & 0x1f) << 6) | (u8[i + 1] & 0x3f);
                i += 2;
            } else if ((b & 0xf0) === 0xe0 && i + 2 < u8.length) {
                cp = ((b & 0x0f) << 12) | ((u8[i + 1] & 0x3f) << 6) | (u8[i + 2] & 0x3f);
                i += 3;
            } else if ((b & 0xf8) === 0xf0 && i + 3 < u8.length) {
                cp = ((b & 0x07) << 18) | ((u8[i + 1] & 0x3f) << 12)
                    | ((u8[i + 2] & 0x3f) << 6) | (u8[i + 3] & 0x3f);
                i += 4;
            } else {
                cp = 0xfffd;
                i += 1;
            }
            if (cp < 0x10000) {
                out += String.fromCharCode(cp);
            } else {
                cp -= 0x10000;
                out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
            }
        }
        return out;
    }

    function b64Encode(u8) {
        var out = '';
        for (var i = 0; i < u8.length; i += 3) {
            var a = u8[i];
            var b = i + 1 < u8.length ? u8[i + 1] : 0;
            var c = i + 2 < u8.length ? u8[i + 2] : 0;
            out += B64.charAt(a >> 2);
            out += B64.charAt(((a & 3) << 4) | (b >> 4));
            out += i + 1 < u8.length ? B64.charAt(((b & 15) << 2) | (c >> 6)) : '=';
            out += i + 2 < u8.length ? B64.charAt(c & 63) : '=';
        }
        return out;
    }

    function b64Decode(str) {
        var clean = String(str).replace(/[^A-Za-z0-9+/=]/g, '');
        var out = [];
        for (var i = 0; i < clean.length; i += 4) {
            var a = B64I[clean.charAt(i)] || 0;
            var b = B64I[clean.charAt(i + 1)] || 0;
            var c = clean.charAt(i + 2) === '=' ? 0 : (B64I[clean.charAt(i + 2)] || 0);
            var d = clean.charAt(i + 3) === '=' ? 0 : (B64I[clean.charAt(i + 3)] || 0);
            out.push((a << 2) | (b >> 4));
            if (clean.charAt(i + 2) !== '=') out.push(((b & 15) << 4) | (c >> 2));
            if (clean.charAt(i + 3) !== '=') out.push(((c & 3) << 6) | d);
        }
        return new Uint8Array(out);
    }

    function hexEncode(u8) {
        var out = '';
        for (var i = 0; i < u8.length; i++) {
            var h = u8[i].toString(16);
            out += h.length < 2 ? '0' + h : h;
        }
        return out;
    }

    function hexDecode(str) {
        var s = String(str);
        var out = [];
        for (var i = 0; i + 1 < s.length; i += 2) out.push(parseInt(s.substr(i, 2), 16) || 0);
        return new Uint8Array(out);
    }

    function toBytes(data, encoding) {
        if (data == null) return new Uint8Array(0);
        if (data instanceof LxBuf) return data._u8;
        if (data instanceof Uint8Array) return data;
        if (typeof ArrayBuffer !== 'undefined' && data instanceof ArrayBuffer) return new Uint8Array(data);
        if (Array.isArray(data)) return new Uint8Array(data);
        if (typeof data === 'string') {
            var enc = (encoding || 'utf8').toLowerCase();
            if (enc === 'base64') return b64Decode(data);
            if (enc === 'hex') return hexDecode(data);
            if (enc === 'latin1' || enc === 'binary') {
                var out = new Uint8Array(data.length);
                for (var i = 0; i < data.length; i++) out[i] = data.charCodeAt(i) & 0xff;
                return out;
            }
            return utf8Encode(data);
        }
        return utf8Encode(String(data));
    }

    function bytesToString(u8, format) {
        var fmt = (format || 'utf8').toLowerCase();
        if (fmt === 'base64') return b64Encode(u8);
        if (fmt === 'hex') return hexEncode(u8);
        if (fmt === 'latin1' || fmt === 'binary') {
            var out = '';
            for (var i = 0; i < u8.length; i++) out += String.fromCharCode(u8[i]);
            return out;
        }
        return utf8Decode(u8);
    }

    function LxBuf(u8) {
        this._u8 = u8;
    }
    LxBuf.prototype.toString = function (format) {
        return bytesToString(this._u8, format);
    };

    /* ---------------- 事件与请求 ----------------
     * 请求走同步宿主调用（与 FreeJS 桥同一出口模型）：__lxRequest 阻塞到响应回来，
     * 回调在脚本语义上仍是 (err, resp) 形态；无回调时返回已兑现的 Promise。
     * 这样桥的单线程串行不会被自己的异步回调堵死。 */
    var handlers = {};

    globalThis.__lxHandlers = handlers;

    function lxRequest(url, options, callback) {
        options = options || {};
        var opts = {};
        for (var k in options) {
            if (Object.prototype.hasOwnProperty.call(options, k)) opts[k] = options[k];
        }
        if (opts.body != null && typeof opts.body !== 'string') {
            if (opts.body instanceof LxBuf || opts.body instanceof Uint8Array) {
                opts.body = b64Encode(toBytes(opts.body));
                opts.__lxBodyB64 = true;
            } else if (typeof opts.body === 'object') {
                opts.body = JSON.stringify(opts.body);
            } else {
                opts.body = String(opts.body);
            }
        }
        var outcome = null;
        try {
            outcome = JSON.parse(String(__lxRequest(String(url), JSON.stringify(opts))));
        } catch (e) {
            outcome = { err: { msg: String((e && e.message) || e) } };
        }
        var err = outcome && outcome.err ? outcome.err : null;
        var resp = outcome && outcome.resp ? outcome.resp : null;
        if (typeof callback === 'function') {
            callback(err, resp);
            return function () {};
        }
        if (err) return Promise.reject(new Error(err.msg || 'request failed'));
        return Promise.resolve(resp);
    }

    /* ---------------- lx 全局对象 ---------------- */
    var meta = (typeof __lxMeta !== 'undefined' && __lxMeta) || {};

    globalThis.lx = {
        EVENT_NAMES: {
            inited: 'inited',
            request: 'request',
            updateAlert: 'updateAlert'
        },
        version: meta.version || '1.0.0',
        env: { platform: 'android' },
        currentScriptInfo: {
            name: meta.name || '',
            version: meta.scriptVersion || '',
            author: meta.author || '',
            description: meta.description || '',
            homepage: meta.homepage || '',
            rawScript: meta.rawScript || ''
        },
        on: function (event, handler) {
            handlers[event] = handler;
        },
        send: function (event, data) {
            try {
                __lxSend(String(event), JSON.stringify(data == null ? null : data));
            } catch (e) {
                // 宿主未就绪时静默（脚本不应因上报失败中断）
            }
        },
        request: lxRequest,
        /* 壳子统一登录通路：提供方由脚本头部 @loginProvider 声明、桥侧闭包绑定，
         * 脚本只能读自己声明那家的托管 Cookie，选不了提供方（与 FreeJS 桥同纪律）。 */
        loginCookie: function () {
            try {
                return typeof __lxLoginCookie === 'function' ? String(__lxLoginCookie() || '') : '';
            } catch (e) {
                return '';
            }
        },
        loginRequest: function () {
            try {
                return typeof __lxLoginRequest === 'function' ? !!__lxLoginRequest() : false;
            } catch (e) {
                return false;
            }
        },
        utils: {
            buffer: {
                from: function (data, encoding) {
                    return new LxBuf(toBytes(data, encoding));
                },
                bufToString: function (buffer, format) {
                    return bytesToString(toBytes(buffer), format);
                }
            },
            crypto: {
                aesEncrypt: function (buffer, mode, key, iv) {
                    var out = __lxAes(
                        b64Encode(toBytes(buffer)),
                        String(mode || 'aes-128-cbc'),
                        b64Encode(toBytes(key)),
                        b64Encode(toBytes(iv)));
                    return new LxBuf(b64Decode(String(out)));
                },
                md5: function (str) {
                    return String(__lxMd5(String(str)));
                },
                randomBytes: function (size) {
                    return new LxBuf(b64Decode(String(__lxRandom(size | 0))));
                },
                rsaEncrypt: function (buffer, key) {
                    var out = __lxRsa(b64Encode(toBytes(buffer)), String(key));
                    return new LxBuf(b64Decode(String(out)));
                }
            }
        }
    };
})();
