/* 星辰 FreeJS(CommonJS) 垫片 v2
 * 给 MusicFree 插件提供运行环境：
 *  - CommonJS：require / module / exports（最小实现）
 *  - 内建模块：axios（最小实现）、he、dayjs（最小实现）
 *  - crypto-js / cheerio：由宿主把库原文包进 __freejsDefine 注入（见下）
 *  - fetch：最小实现，返回 thenable Response（.text()/.json() 也是 thenable）
 *  - console：转宿主 __freejsLog
 *  - setTimeout / clearTimeout：真实现在宿主钩子 __freejsSetTimeout /
 *    __freejsClearTimeout（桥侧 Timer 计时、回调回桥线程）；宿主没给钩子
 *    时退同步忙等兜底，语义边界见第 8 节注。
 *  - atob / Buffer（最小面）：纯 JS base64 解码，供插件解 QQ 歌词那类
 *    base64 正文（见第 9 节）。
 *
 * 网络统一经宿主函数 __freejsRequest(method, url, optsJson) 同步发出，
 * 返回 JSON 字符串 {status, headers, text}；失败抛 Error。
 * 宿主侧（Android 走壳子 OkHttp，吃代理/DoH/节点定向；试验台走 curl）。
 *
 * 语义约定（宿主 Java 桥与试验台必须一致）：
 *  1. require 只认内建模块表，不在表里的抛错；插件不得 require 相对路径。
 *  2. axios.get/post 返回 thenable，resolve {data, status, headers}；
 *     data 在 content-type 含 json 时自动 JSON.parse，否则原文。
 *  3. fetch 返回 thenable<response>；response.text()/.json() 返回 thenable。
 *  4. 插件顶层用 module.exports 导出；宿主读 globalThis.module.exports。
 *  5. setTimeout 只保证「延时后回调」：宿主钩子在时是真异步定时器；
 *     忙等兜底下回调在当前语句流内同步触发（见第 8 节），插件不要依赖
 *     「setTimeout 后的语句先于回调执行」。
 *  6. atob 是宽容实现：非法字符跳过、不抛错（真 atob 会抛
 *     InvalidCharacterError）；返回值是字节串（每字符一个字节），
 *     UTF-8 文本要再经 decodeURIComponent 或 Buffer.toString('utf-8')。
 */
(function (global) {
    "use strict";

    /* ---------- 0. console ---------- */
    if (!global.console) {
        var __log = function (lv, args) {
            var msg = Array.prototype.map.call(args, function (a) {
                try { return typeof a === "string" ? a : JSON.stringify(a); } catch (e) { return String(a); }
            }).join(" ");
            if (typeof global.__freejsLog === "function") { try { global.__freejsLog(lv, msg); } catch (e) { /* 忽略 */ } }
        };
        global.console = {
            log: function () { __log("I", arguments); },
            info: function () { __log("I", arguments); },
            debug: function () { __log("D", arguments); },
            warn: function () { __log("W", arguments); },
            error: function () { __log("E", arguments); }
        };
    }

    /* ---------- 1. CommonJS 最小实现 ---------- */
    var __factories = {};
    var __cache = {};

    function __defineBuiltin(name, factory) {
        __factories[name] = factory;
        delete __cache[name];
    }

    function require(name) {
        if (Object.prototype.hasOwnProperty.call(__cache, name)) return __cache[name];
        var factory = __factories[name];
        if (!factory) throw new Error("[freejs] 找不到内建模块: " + name);
        var m = { exports: {} };
        factory(require, m, m.exports);
        if (m.exports && m.exports["default"] === undefined && typeof m.exports === "object") {
            /* 编译插件常取 .default，顺手挂上自指 */
            try { m.exports["default"] = m.exports; } catch (e) { /* 忽略 */ }
        }
        __cache[name] = m.exports;
        return m.exports;
    }

    var module = { exports: {} };
    global.require = require;
    global.module = module;
    global.exports = module.exports;
    /* 宿主用它注入 crypto-js / cheerio 等库原文 */
    global.__freejsDefine = __defineBuiltin;

    /* ---------- 2. 同步请求桥 ---------- */
    function __nativeRequest(method, url, opts) {
        if (typeof global.__freejsRequest !== "function") {
            throw new Error("[freejs] 宿主未提供 __freejsRequest");
        }
        var raw = global.__freejsRequest(method, url, JSON.stringify(opts || {}));
        var res = JSON.parse(raw);
        if (res && res.error) throw new Error("[freejs] 请求失败: " + res.error);
        return res;
    }

    function __buildUrl(url, params) {
        if (!params) return url;
        var qs = [];
        for (var k in params) {
            if (!Object.prototype.hasOwnProperty.call(params, k)) continue;
            var v = params[k];
            if (v === undefined || v === null) continue;
            qs.push(encodeURIComponent(k) + "=" + encodeURIComponent(v));
        }
        if (!qs.length) return url;
        return url + (url.indexOf("?") >= 0 ? "&" : "?") + qs.join("&");
    }

    function __contentTypeOf(res) {
        var headers = res.headers || {};
        for (var k in headers) {
            if (k.toLowerCase() === "content-type") return headers[k] || "";
        }
        return "";
    }

    function __parseData(res) {
        var ct = __contentTypeOf(res);
        if (ct.indexOf("json") >= 0) {
            try { return JSON.parse(res.text); } catch (e) { return res.text; }
        }
        /* 真 axios 默认对字符串响应也尝试 JSON.parse（forcedJSONParsing），
         * 不少接口（如酷我 r.s）回 text/plain 的 JSON 文本，插件按对象取 data.abslist */
        if (typeof res.text === "string") {
            var t = res.text.replace(/^\s+/, "");
            if (t.charAt(0) === "{" || t.charAt(0) === "[") {
                try { return JSON.parse(res.text); } catch (e) { /* 不是 JSON 就原文 */ }
            }
        }
        return res.text;
    }

    function __thenable(value) {
        return {
            then: function (onOk, onErr) {
                try {
                    return onOk ? onOk(value) : value;
                } catch (e) {
                    if (onErr) return onErr(e);
                    throw e;
                }
            },
            "catch": function (onErr) { return this.then(null, onErr); }
        };
    }

    function __rejected(err) {
        return {
            then: function (onOk, onErr) {
                if (onErr) return onErr(err);
                throw err;
            },
            "catch": function (onErr) { return onErr(err); }
        };
    }

    /* ---------- 3. fetch（最小实现） ---------- */
    function __makeResponse(res) {
        var lower = {};
        var headers = res.headers || {};
        for (var k in headers) lower[String(k).toLowerCase()] = headers[k];
        return {
            status: res.status,
            ok: res.status >= 200 && res.status < 300,
            headers: {
                get: function (k) {
                    var v = lower[String(k).toLowerCase()];
                    return v === undefined ? null : v;
                }
            },
            text: function () { return __thenable(res.text); },
            json: function () {
                try { return __thenable(JSON.parse(res.text)); }
                catch (e) { return __rejected(e); }
            }
        };
    }

    function fetch(url, opts) {
        opts = opts || {};
        try {
            var res = __nativeRequest(opts.method || "GET", url, {
                headers: opts.headers || {},
                body: opts.body,
                timeout: opts.timeout || 20000
            });
            return __thenable(__makeResponse(res));
        } catch (e) {
            return __rejected(e);
        }
    }
    global.fetch = fetch;

    /* ---------- 4. axios（最小实现） ---------- */
    function __axiosRequest(method, url, opts) {
        opts = opts || {};
        var full = __buildUrl(url, opts.params);
        var reqOpts = { headers: opts.headers || {}, timeout: opts.timeout || 20000 };
        if (opts.data !== undefined && opts.data !== null) {
            reqOpts.body = (typeof opts.data === "string") ? opts.data : JSON.stringify(opts.data);
            var hasCt = false;
            for (var k in reqOpts.headers) { if (k.toLowerCase() === "content-type") { hasCt = true; break; } }
            if (!hasCt) reqOpts.headers["Content-Type"] = "application/json";
        }
        var res;
        try {
            res = __nativeRequest(method, full, reqOpts);
        } catch (e) {
            return __rejected(e);
        }
        if (res.status < 200 || res.status >= 300) {
            var err = new Error("[freejs] axios 请求返回 " + res.status);
            err.response = { data: __parseData(res), status: res.status, headers: res.headers };
            return __rejected(err);
        }
        return __thenable({ data: __parseData(res), status: res.status, headers: res.headers, config: opts });
    }

    /* 真 axios 本身是可调用函数（axios({method,url,...})），TS 编译的插件常走
     * (0, axios_1.default)({...}) 这条路；做成函数对象并自挂 default，两条路都通 */
    var axios = function (config) { config = config || {}; return __axiosRequest(config.method || "GET", config.url, config); };
    axios.get = function (url, opts) { return __axiosRequest("GET", url, opts); };
    axios.post = function (url, data, opts) { opts = opts || {}; opts.data = data; return __axiosRequest("POST", url, opts); };
    axios.put = function (url, data, opts) { opts = opts || {}; opts.data = data; return __axiosRequest("PUT", url, opts); };
    axios.head = function (url, opts) { return __axiosRequest("HEAD", url, opts); };
    axios["delete"] = function (url, opts) { return __axiosRequest("DELETE", url, opts); };
    axios.request = function (opts) { opts = opts || {}; return __axiosRequest(opts.method || "GET", opts.url, opts); };
    axios.create = function () { return axios; };
    axios["default"] = axios;
    __defineBuiltin("axios", function (require, module) { module.exports = axios; });

    /* ---------- 5. he（最小实现：只做 decode/encode；命名实体表为 HTML 常用集，
     * 数字/十六进制字符引用在 decode 里单独处理。非 ASCII 值一律 \u 转义写，
     * 防文件编码漂移把实体值写坏） ---------- */
    __defineBuiltin("he", function (require, module) {
        var NAMED = {
            /* 基础六个（原表） */
            amp: "&", lt: "<", gt: ">",
            quot: "\"", apos: "'", nbsp: "\u00a0",
            /* 标点与通用符号 */
            iexcl: "\u00a1", cent: "\u00a2", pound: "\u00a3", curren: "\u00a4", yen: "\u00a5",
            brvbar: "\u00a6", sect: "\u00a7", uml: "\u00a8", copy: "\u00a9", ordf: "\u00aa",
            laquo: "\u00ab", not: "\u00ac", shy: "\u00ad", reg: "\u00ae", macr: "\u00af",
            deg: "\u00b0", plusmn: "\u00b1", sup2: "\u00b2", sup3: "\u00b3", acute: "\u00b4",
            micro: "\u00b5", para: "\u00b6", middot: "\u00b7", cedil: "\u00b8", sup1: "\u00b9",
            ordm: "\u00ba", raquo: "\u00bb", frac14: "\u00bc", frac12: "\u00bd", frac34: "\u00be",
            iquest: "\u00bf", times: "\u00d7", divide: "\u00f7", ndash: "\u2013", mdash: "\u2014",
            lsquo: "\u2018", rsquo: "\u2019", sbquo: "\u201a", ldquo: "\u201c", rdquo: "\u201d",
            bdquo: "\u201e", dagger: "\u2020", Dagger: "\u2021", bull: "\u2022", hellip: "\u2026",
            permil: "\u2030", prime: "\u2032", Prime: "\u2033", lsaquo: "\u2039", rsaquo: "\u203a",
            euro: "\u20ac", trade: "\u2122", larr: "\u2190", uarr: "\u2191", rarr: "\u2192",
            darr: "\u2193", harr: "\u2194", crarr: "\u21b5", spades: "\u2660", clubs: "\u2663",
            hearts: "\u2665", diams: "\u2666", ensp: "\u2002", emsp: "\u2003", thinsp: "\u2009",
            zwnj: "\u200c", zwj: "\u200d", lrm: "\u200e", rlm: "\u200f",
            /* 数学 */
            minus: "\u2212", lowast: "\u2217", radic: "\u221a", prop: "\u221d", infin: "\u221e",
            ang: "\u2220", and: "\u2227", or: "\u2228", cap: "\u2229", cup: "\u222a",
            int: "\u222b", there4: "\u2234", sim: "\u223c", cong: "\u2245", asymp: "\u2248",
            ne: "\u2260", equiv: "\u2261", le: "\u2264", ge: "\u2265", sub: "\u2282",
            sup: "\u2283", nsub: "\u2284", sube: "\u2286", supe: "\u2287", oplus: "\u2295",
            otimes: "\u2297", perp: "\u22a5", sdot: "\u22c5", forall: "\u2200", exist: "\u2203",
            empty: "\u2205", nabla: "\u2207", isin: "\u2208", notin: "\u2209", ni: "\u220b",
            prod: "\u220f", sum: "\u2211", real: "\u211c", image: "\u2111", weierp: "\u2118",
            alefsym: "\u2135",
            /* 希腊字母 */
            alpha: "\u03b1", beta: "\u03b2", gamma: "\u03b3", delta: "\u03b4", epsilon: "\u03b5",
            zeta: "\u03b6", eta: "\u03b7", theta: "\u03b8", iota: "\u03b9", kappa: "\u03ba",
            lambda: "\u03bb", mu: "\u03bc", nu: "\u03bd", xi: "\u03be", pi: "\u03c0",
            rho: "\u03c1", sigma: "\u03c3", tau: "\u03c4", upsilon: "\u03c5", phi: "\u03c6",
            chi: "\u03c7", psi: "\u03c8", omega: "\u03c9", Alpha: "\u0391", Beta: "\u0392",
            Gamma: "\u0393", Delta: "\u0394", Epsilon: "\u0395", Zeta: "\u0396", Eta: "\u0397",
            Theta: "\u0398", Iota: "\u0399", Kappa: "\u039a", Lambda: "\u039b", Mu: "\u039c",
            Nu: "\u039d", Xi: "\u039e", Pi: "\u03a0", Rho: "\u03a1", Sigma: "\u03a3",
            Tau: "\u03a4", Upsilon: "\u03a5", Phi: "\u03a6", Chi: "\u03a7", Psi: "\u03a8",
            Omega: "\u03a9",
            /* 拉丁扩展（外文歌名常见） */
            Agrave: "\u00c0", Aacute: "\u00c1", Acirc: "\u00c2", Atilde: "\u00c3", Auml: "\u00c4",
            Aring: "\u00c5", AElig: "\u00c6", Ccedil: "\u00c7", Egrave: "\u00c8", Eacute: "\u00c9",
            Ecirc: "\u00ca", Euml: "\u00cb", Igrave: "\u00cc", Iacute: "\u00cd", Icirc: "\u00ce",
            Iuml: "\u00cf", Eth: "\u00d0", Ntilde: "\u00d1", Ograve: "\u00d2", Oacute: "\u00d3",
            Ocirc: "\u00d4", Otilde: "\u00d5", Ouml: "\u00d6", Oslash: "\u00d8", Ugrave: "\u00d9",
            Uacute: "\u00da", Ucirc: "\u00db", Uuml: "\u00dc", Yacute: "\u00dd", THORN: "\u00de",
            szlig: "\u00df", agrave: "\u00e0", aacute: "\u00e1", acirc: "\u00e2", atilde: "\u00e3",
            auml: "\u00e4", aring: "\u00e5", aelig: "\u00e6", ccedil: "\u00e7", egrave: "\u00e8",
            eacute: "\u00e9", ecirc: "\u00ea", euml: "\u00eb", igrave: "\u00ec", iacute: "\u00ed",
            icirc: "\u00ee", iuml: "\u00ef", eth: "\u00f0", ntilde: "\u00f1", ograve: "\u00f2",
            oacute: "\u00f3", ocirc: "\u00f4", otilde: "\u00f5", ouml: "\u00f6", oslash: "\u00f8",
            ugrave: "\u00f9", uacute: "\u00fa", ucirc: "\u00fb", uuml: "\u00fc", yacute: "\u00fd",
            thorn: "\u00fe", yuml: "\u00ff", OElig: "\u0152", oelig: "\u0153", Scaron: "\u0160",
            scaron: "\u0161", Yuml: "\u0178", fnof: "\u0192", circ: "\u02c6", tilde: "\u02dc"
        };
        function decode(s) {
            if (s === undefined || s === null) return s;
            return String(s).replace(/&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z][a-zA-Z0-9]+);/g, function (m, e) {
                if (e.charAt(0) === "#") {
                    var c = e.charAt(1).toLowerCase() === "x" ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
                    return isNaN(c) ? m : String.fromCharCode(c);
                }
                return NAMED[e] !== undefined ? NAMED[e] : m;
            });
        }
        function encode(s) {
            if (s === undefined || s === null) return s;
            return String(s).replace(/[&<>"']/g, function (c) {
                return { "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" }[c];
            });
        }
        module.exports = { decode: decode, encode: encode };
    });

    /* ---------- 6. dayjs（最小实现：unix/format/valueOf） ---------- */
    __defineBuiltin("dayjs", function (require, module) {
        function pad(n, l) { n = String(n); while (n.length < (l || 2)) n = "0" + n; return n; }
        function Dayjs(t) { this.t = (t === undefined || t === null) ? Date.now() : t; }
        Dayjs.prototype.format = function (fmt) {
            var d = new Date(this.t);
            var map = {
                YYYY: d.getFullYear(), MM: pad(d.getMonth() + 1), DD: pad(d.getDate()),
                HH: pad(d.getHours()), mm: pad(d.getMinutes()), ss: pad(d.getSeconds())
            };
            return String(fmt).replace(/YYYY|MM|DD|HH|mm|ss/g, function (m) { return map[m]; });
        };
        Dayjs.prototype.valueOf = function () { return this.t; };
        Dayjs.prototype.toDate = function () { return new Date(this.t); };
        function dayjs(a) {
            if (a === undefined) return new Dayjs();
            if (typeof a === "number") return new Dayjs(a);
            var t = Date.parse(a);
            return new Dayjs(isNaN(t) ? Date.now() : t);
        }
        dayjs.unix = function (s) { return new Dayjs(s * 1000); };
        module.exports = dayjs;
    });

    /* ---------- 7. crypto-js / cheerio 由宿主注入 ----------
     * Java 桥（或试验台）在加载插件前执行：
     *   __freejsDefine("crypto-js", function (require, module, exports) { <crypto-js.js 全文> });
     *   __freejsDefine("cheerio",  function (require, module, exports) { <cheerio.cjs.js 全文> });
     * 两个库都是 UMD/CJS 形态，包进来后自动挂 module.exports。
     */

    /* ---------- 8. setTimeout / clearTimeout ----------
     * 真实现在宿主钩子：桥注册 __freejsSetTimeout(fn, delayMs) -> id 与
     * __freejsClearTimeout(id)（java.util.Timer 计时、回调投递回桥单线程
     * 执行，与点播 Global.setTimeout 同一机制）。这里只做参数归一与
     * setTimeout(fn, ms, arg...) 的透传参数打包。
     *
     * 兜底（宿主没给钩子时，如试验台裸跑垫片）：同步忙等——时长是真等够
     * 的，但语义边界与真定时器不同，写明在此：
     *  - 回调在当前语句流内同步触发，不是等本段脚本跑完再异步回调；
     *    排在 setTimeout(...) 之后的语句会在回调之后才执行（顺序与
     *    浏览器相反），依赖这一顺序的插件在兜底路会出错；
     *  - 等待期间宿主线程被占住（同步请求模型下本来也是串行，量级即
     *    延时本身）；
     *  - clearTimeout 无效：返回的 id 恒为 0，回调在 setTimeout 返回前
     *    已经触发完。
     * 它只保证 `await new Promise(r => setTimeout(r, ms))` 这一型（插件
     * 导入歌单的翻页节奏延时正是这一型）能真跑通；Android 桥恒走钩子
     * 真定时器路，不经兜底。setInterval 不提供（无插件用，Global 亦无）。
     */
    if (typeof global.setTimeout !== "function") {
        if (typeof global.__freejsSetTimeout === "function") {
            global.setTimeout = function (fn, delay) {
                if (typeof fn !== "function") return 0;
                var ms = Number(delay);
                if (!(ms > 0)) ms = 0;
                var extra = Array.prototype.slice.call(arguments, 2);
                if (extra.length) {
                    var orig = fn;
                    fn = function () { orig.apply(null, extra); };
                }
                return global.__freejsSetTimeout(fn, ms);
            };
            global.clearTimeout = function (id) {
                if (typeof global.__freejsClearTimeout === "function") {
                    global.__freejsClearTimeout(id);
                }
            };
        } else {
            global.setTimeout = function (fn, delay) {
                var ms = Number(delay);
                if (!(ms > 0)) ms = 0;
                var end = Date.now() + ms;
                while (Date.now() < end) { /* 忙等兜底：语义边界见本节头注 */ }
                if (typeof fn === "function") {
                    fn.apply(null, Array.prototype.slice.call(arguments, 2));
                }
                return 0;
            };
            global.clearTimeout = function () { /* 兜底路回调已同步触发，无从取消 */ };
        }
    }

    /* ---------- 9. atob / Buffer（最小面：base64 解码） ----------
     * 插件解 QQ 歌词那类 base64 正文只有两条路：
     *   decodeURIComponent(atob(b64)) 与 Buffer.from(b64, "base64").toString("utf-8")
     * 这里把两条都补齐。atob 是宽容实现（空白与 URL-safe 字母表都收、
     * 非法字符跳过、不抛错），返回字节串；Buffer 只做 from(base64/utf-8)
     * 与 toString(utf-8/latin1)，其余编码抛错让插件自己的 try/catch 兜。
     */
    var __B64TAB = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    function __b64ToBytes(str) {
        var s = String(str === undefined || str === null ? "" : str)
            .replace(/\s+/g, "").replace(/-/g, "+").replace(/_/g, "/");
        var out = [];
        var acc = 0, bits = 0;
        for (var i = 0; i < s.length; i++) {
            var c = s.charAt(i);
            if (c === "=") break;
            var v = __B64TAB.indexOf(c);
            if (v < 0) continue;
            acc = (acc << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.push((acc >> bits) & 0xff);
            }
        }
        return out;
    }
    function __bytesToString(bytes) {
        var out = "";
        var CH = 8192;
        for (var i = 0; i < bytes.length; i += CH) {
            out += String.fromCharCode.apply(null, bytes.slice(i, i + CH));
        }
        return out;
    }
    function __utf8Decode(bytes) {
        var out = "";
        var i = 0;
        while (i < bytes.length) {
            var b = bytes[i];
            if (b < 0x80) {
                out += String.fromCharCode(b);
                i += 1;
            } else if (b >= 0xc2 && b <= 0xdf && i + 1 < bytes.length) {
                out += String.fromCharCode(((b & 0x1f) << 6) | (bytes[i + 1] & 0x3f));
                i += 2;
            } else if (b >= 0xe0 && b <= 0xef && i + 2 < bytes.length) {
                out += String.fromCharCode(((b & 0x0f) << 12) | ((bytes[i + 1] & 0x3f) << 6) | (bytes[i + 2] & 0x3f));
                i += 3;
            } else if (b >= 0xf0 && b <= 0xf4 && i + 3 < bytes.length) {
                var cp = ((b & 0x07) << 18) | ((bytes[i + 1] & 0x3f) << 12) | ((bytes[i + 2] & 0x3f) << 6) | (bytes[i + 3] & 0x3f);
                cp -= 0x10000;
                out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
                i += 4;
            } else {
                out += "\ufffd";
                i += 1;
            }
        }
        return out;
    }
    function __utf8Encode(str) {
        var out = [];
        var s = String(str);
        for (var i = 0; i < s.length; i++) {
            var cp = s.codePointAt(i);
            if (cp > 0xffff) i++;
            if (cp < 0x80) out.push(cp);
            else if (cp < 0x800) out.push(0xc0 | (cp >> 6), 0x80 | (cp & 0x3f));
            else if (cp < 0x10000) out.push(0xe0 | (cp >> 12), 0x80 | ((cp >> 6) & 0x3f), 0x80 | (cp & 0x3f));
            else out.push(0xf0 | (cp >> 18), 0x80 | ((cp >> 12) & 0x3f), 0x80 | ((cp >> 6) & 0x3f), 0x80 | (cp & 0x3f));
        }
        return out;
    }
    if (typeof global.atob !== "function") {
        global.atob = function (str) { return __bytesToString(__b64ToBytes(str)); };
    }
    if (typeof global.Buffer === "undefined") {
        global.Buffer = {
            from: function (data, encoding) {
                var enc = (encoding === undefined || encoding === null) ? "utf-8" : String(encoding).toLowerCase();
                var bytes;
                if (enc === "base64") bytes = __b64ToBytes(data);
                else if (enc === "utf-8" || enc === "utf8") bytes = __utf8Encode(data);
                else if (enc === "latin1" || enc === "binary") {
                    bytes = [];
                    var s0 = String(data);
                    for (var j = 0; j < s0.length; j++) bytes.push(s0.charCodeAt(j) & 0xff);
                } else {
                    throw new Error("[freejs] Buffer.from 不支持的编码: " + enc);
                }
                return {
                    length: bytes.length,
                    toString: function (e2) {
                        var enc2 = (e2 === undefined || e2 === null) ? "utf-8" : String(e2).toLowerCase();
                        if (enc2 === "utf-8" || enc2 === "utf8") return __utf8Decode(bytes);
                        if (enc2 === "latin1" || enc2 === "binary") return __bytesToString(bytes);
                        throw new Error("[freejs] Buffer.toString 不支持的编码: " + enc2);
                    }
                };
            }
        };
    }

    global.__freejsShimVersion = 2;
})(globalThis);
