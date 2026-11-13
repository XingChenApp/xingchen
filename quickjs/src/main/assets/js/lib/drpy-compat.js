// drpy 通道兼容垫片（只在 DrpySpider 的上下文里、引擎求值之前装载）。
//
// 背景：drpys 生态的新式源（ds 型）不是经典 drpy2 的字符串规则，而是把
// 一级/搜索/二级/lazy 写成 async 函数，网络与工具走沙箱全局：axios、
// Buffer、require('node:crypto')、URLSearchParams、setResult。这些全局
// 经典 drpy2 引擎（模块作用域）不提供，缺了源在 init 的 eval 阶段就死。
// 本垫片只补这一层沙箱面，语义按源里的实际用法实现，不冒充 Node 全量：
// - axios：走 http.js 的 http()（宿主 OkHttp），回 {status,data,headers}；
//   responseType 非 text 时按 axios 惯例尝试 JSON 解析；非 2xx 抛带
//   response 的 Error（与 axios 默认 validateStatus 一致）。
// - Buffer：Uint8Array 之上的最小面（from/concat/alloc/isBuffer/
//   byteLength + 实例 toString(enc)），够签名/编解码类用法。
// - require：只认 'node:crypto'/'crypto'，其余模块名明确抛错，不装懂。
//   node:crypto 垫片以全局 CryptoJS 为底（引擎 import 时装载，调用时
//   才读，故装载顺序无碍）：createHash 支持 md5/sha1/sha256/sha512
//   （CryptoJS）与 sm3（纯 JS 实现，标准向量验过）；createCipheriv/
//   createDecipheriv 支持 aes-*-cbc/ctr（CTR 恒无填充，CBC 默认
//   Pkcs7、setAutoPadding(false) 关）。update 分片只累积、final 一次
//   算完——对整段签名/解密语义等价，不支持真正的流式增量输出。
// - setResult：按 ds 源内注释的引擎约定实现（只认卡片的
//   url/title/desc/img，转 vod_id/vod_name/vod_pic/vod_remarks；
//   已是 vod_* 形态的条目原样过）。
(function () {
    'use strict';

    var G = globalThis;

    if (typeof G.log !== 'function') {
        G.log = function () { console.log.apply(null, arguments); };
    }
    if (typeof G.print !== 'function') {
        G.print = G.log;
    }

    /* ---------- 字节工具 ---------- */

    function utf8ToBytes(str) {
        var out = [];
        for (var i = 0; i < str.length; i++) {
            var c = str.charCodeAt(i);
            if (c < 0x80) {
                out.push(c);
            } else if (c < 0x800) {
                out.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f));
            } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < str.length) {
                var c2 = str.charCodeAt(++i);
                var cp = 0x10000 + ((c & 0x3ff) << 10) + (c2 & 0x3ff);
                out.push(0xf0 | (cp >> 18), 0x80 | ((cp >> 12) & 0x3f), 0x80 | ((cp >> 6) & 0x3f), 0x80 | (cp & 0x3f));
            } else {
                out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
            }
        }
        return new Uint8Array(out);
    }

    function bytesToUtf8(bytes) {
        var out = '';
        var i = 0;
        while (i < bytes.length) {
            var b = bytes[i];
            if (b < 0x80) {
                out += String.fromCharCode(b);
                i += 1;
            } else if (b < 0xe0) {
                out += String.fromCharCode(((b & 0x1f) << 6) | (bytes[i + 1] & 0x3f));
                i += 2;
            } else if (b < 0xf0) {
                out += String.fromCharCode(((b & 0x0f) << 12) | ((bytes[i + 1] & 0x3f) << 6) | (bytes[i + 2] & 0x3f));
                i += 3;
            } else {
                var cp = ((b & 0x07) << 18) | ((bytes[i + 1] & 0x3f) << 12) | ((bytes[i + 2] & 0x3f) << 6) | (bytes[i + 3] & 0x3f);
                cp -= 0x10000;
                out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
                i += 4;
            }
        }
        return out;
    }

    var B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

    function bytesToBase64(bytes) {
        var out = '';
        for (var i = 0; i < bytes.length; i += 3) {
            var a = bytes[i];
            var b = i + 1 < bytes.length ? bytes[i + 1] : 0;
            var c = i + 2 < bytes.length ? bytes[i + 2] : 0;
            out += B64.charAt(a >> 2);
            out += B64.charAt(((a & 3) << 4) | (b >> 4));
            out += i + 1 < bytes.length ? B64.charAt(((b & 0x0f) << 2) | (c >> 6)) : '=';
            out += i + 2 < bytes.length ? B64.charAt(c & 0x3f) : '=';
        }
        return out;
    }

    function base64ToBytes(text) {
        var clean = String(text).replace(/[^A-Za-z0-9+/=]/g, '');
        var out = [];
        for (var i = 0; i < clean.length; i += 4) {
            var a = B64.indexOf(clean.charAt(i));
            var b = B64.indexOf(clean.charAt(i + 1));
            var c = clean.charAt(i + 2) === '=' ? 0 : B64.indexOf(clean.charAt(i + 2));
            var d = clean.charAt(i + 3) === '=' ? 0 : B64.indexOf(clean.charAt(i + 3));
            out.push(((a << 2) | (b >> 4)) & 0xff);
            if (clean.charAt(i + 2) !== '=') out.push((((b & 0x0f) << 4) | (c >> 2)) & 0xff);
            if (clean.charAt(i + 3) !== '=') out.push((((c & 0x03) << 6) | d) & 0xff);
        }
        return new Uint8Array(out);
    }

    function bytesToHex(bytes) {
        var out = '';
        for (var i = 0; i < bytes.length; i++) out += ('0' + bytes[i].toString(16)).slice(-2);
        return out;
    }

    function hexToBytes(text) {
        var clean = String(text).replace(/[^0-9a-fA-F]/g, '');
        var out = [];
        for (var i = 0; i + 1 < clean.length; i += 2) out.push(parseInt(clean.substr(i, 2), 16));
        return new Uint8Array(out);
    }

    function bytesToLatin1(bytes) {
        var out = '';
        for (var i = 0; i < bytes.length; i++) out += String.fromCharCode(bytes[i]);
        return out;
    }

    function toBytes(data, encoding) {
        if (data == null) return new Uint8Array(0);
        if (typeof data === 'string') {
            var enc = (encoding || 'utf8').toLowerCase();
            if (enc === 'hex') return hexToBytes(data);
            if (enc === 'base64') return base64ToBytes(data);
            if (enc === 'latin1' || enc === 'binary') {
                var out = new Uint8Array(data.length);
                for (var i = 0; i < data.length; i++) out[i] = data.charCodeAt(i) & 0xff;
                return out;
            }
            return utf8ToBytes(data);
        }
        if (data instanceof Uint8Array) return data;
        if (data instanceof ArrayBuffer) return new Uint8Array(data);
        if (ArrayBuffer.isView(data)) return new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
        if (Array.isArray(data)) return new Uint8Array(data);
        return utf8ToBytes(String(data));
    }

    function concatBytes(chunks) {
        var total = 0;
        for (var i = 0; i < chunks.length; i++) total += chunks[i].length;
        var out = new Uint8Array(total);
        var off = 0;
        for (var j = 0; j < chunks.length; j++) {
            out.set(chunks[j], off);
            off += chunks[j].length;
        }
        return out;
    }

    function bytesToString(bytes, encoding) {
        var enc = (encoding || 'utf8').toLowerCase();
        if (enc === 'hex') return bytesToHex(bytes);
        if (enc === 'base64') return bytesToBase64(bytes);
        if (enc === 'latin1' || enc === 'binary') return bytesToLatin1(bytes);
        return bytesToUtf8(bytes);
    }

    /* ---------- Buffer（最小面） ---------- */

    function makeBuffer(bytes) {
        var u8 = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
        u8.toString = function (encoding) {
            return bytesToString(u8, encoding || 'utf8');
        };
        // 签名类代码（如短剧 App 签名）用 DataView 口径的读写方法，
        // 按需补齐常用面；偏移越界按 DataView 语义抛错，与 node 一致。
        function view() {
            return new DataView(u8.buffer, u8.byteOffset, u8.byteLength);
        }
        u8.readUInt8 = function (offset) { return view().getUint8(offset || 0); };
        u8.readInt8 = function (offset) { return view().getInt8(offset || 0); };
        u8.readInt16BE = function (offset) { return view().getInt16(offset || 0, false); };
        u8.readInt16LE = function (offset) { return view().getInt16(offset || 0, true); };
        u8.readFloatBE = function (offset) { return view().getFloat32(offset || 0, false); };
        u8.readFloatLE = function (offset) { return view().getFloat32(offset || 0, true); };
        u8.readDoubleBE = function (offset) { return view().getFloat64(offset || 0, false); };
        u8.readDoubleLE = function (offset) { return view().getFloat64(offset || 0, true); };
        u8.readUInt16BE = function (offset) { return view().getUint16(offset || 0, false); };
        u8.readUInt16LE = function (offset) { return view().getUint16(offset || 0, true); };
        u8.readUInt32BE = function (offset) { return view().getUint32(offset || 0, false); };
        u8.readUInt32LE = function (offset) { return view().getUint32(offset || 0, true); };
        u8.readInt32BE = function (offset) { return view().getInt32(offset || 0, false); };
        u8.readInt32LE = function (offset) { return view().getInt32(offset || 0, true); };
        u8.readBigUInt64BE = function (offset) { return view().getBigUint64(offset || 0, false); };
        u8.readBigUInt64LE = function (offset) { return view().getBigUint64(offset || 0, true); };
        u8.readBigInt64BE = function (offset) { return view().getBigInt64(offset || 0, false); };
        u8.readBigInt64LE = function (offset) { return view().getBigInt64(offset || 0, true); };
        u8.writeUInt8 = function (value, offset) { view().setUint8(offset || 0, value); return (offset || 0) + 1; };
        u8.writeInt8 = function (value, offset) { view().setInt8(offset || 0, value); return (offset || 0) + 1; };
        u8.writeInt16BE = function (value, offset) { view().setInt16(offset || 0, value, false); return (offset || 0) + 2; };
        u8.writeInt16LE = function (value, offset) { view().setInt16(offset || 0, value, true); return (offset || 0) + 2; };
        u8.writeFloatBE = function (value, offset) { view().setFloat32(offset || 0, value, false); return (offset || 0) + 4; };
        u8.writeFloatLE = function (value, offset) { view().setFloat32(offset || 0, value, true); return (offset || 0) + 4; };
        u8.writeDoubleBE = function (value, offset) { view().setFloat64(offset || 0, value, false); return (offset || 0) + 8; };
        u8.writeDoubleLE = function (value, offset) { view().setFloat64(offset || 0, value, true); return (offset || 0) + 8; };
        u8.writeUInt16BE = function (value, offset) { view().setUint16(offset || 0, value, false); return (offset || 0) + 2; };
        u8.writeUInt16LE = function (value, offset) { view().setUint16(offset || 0, value, true); return (offset || 0) + 2; };
        u8.writeUInt32BE = function (value, offset) { view().setUint32(offset || 0, value >>> 0, false); return (offset || 0) + 4; };
        u8.writeUInt32LE = function (value, offset) { view().setUint32(offset || 0, value >>> 0, true); return (offset || 0) + 4; };
        u8.writeInt32BE = function (value, offset) { view().setInt32(offset || 0, value, false); return (offset || 0) + 4; };
        u8.writeInt32LE = function (value, offset) { view().setInt32(offset || 0, value, true); return (offset || 0) + 4; };
        u8.writeBigUInt64BE = function (value, offset) { view().setBigUint64(offset || 0, BigInt(value), false); return (offset || 0) + 8; };
        u8.writeBigUInt64LE = function (value, offset) { view().setBigUint64(offset || 0, BigInt(value), true); return (offset || 0) + 8; };
        u8.writeBigInt64BE = function (value, offset) { view().setBigInt64(offset || 0, BigInt(value), false); return (offset || 0) + 8; };
        u8.writeBigInt64LE = function (value, offset) { view().setBigInt64(offset || 0, BigInt(value), true); return (offset || 0) + 8; };
        u8.write = function (string, offset, encoding) {
            if (typeof offset === 'string') { encoding = offset; offset = 0; }
            var data = toBytes(String(string), encoding || 'utf8');
            var start = offset || 0;
            var n = Math.min(data.length, u8.length - start);
            for (var i = 0; i < n; i++) u8[start + i] = data[i];
            return n;
        };
        u8.slice = function (start, end) { return makeBuffer(u8.subarray(start, end)); };
        u8.subarray = function (start, end) { return makeBuffer(Uint8Array.prototype.subarray.call(u8, start, end)); };
        u8.equals = function (other) {
            var o = toBytes(other);
            if (o.length !== u8.length) return false;
            for (var i = 0; i < u8.length; i++) if (u8[i] !== o[i]) return false;
            return true;
        };
        u8.copy = function (target, targetStart, sourceStart, sourceEnd) {
            var t = target instanceof Uint8Array ? target : toBytes(target);
            var ts = targetStart || 0;
            var ss = sourceStart || 0;
            var se = sourceEnd == null ? u8.length : sourceEnd;
            var n = Math.min(se - ss, t.length - ts);
            for (var i = 0; i < n; i++) t[ts + i] = u8[ss + i];
            return n;
        };
        u8.fill = function (value, start, end) {
            var s0 = start || 0;
            var e0 = end == null ? u8.length : end;
            if (typeof value === 'string') value = value.length ? value.charCodeAt(0) & 0xff : 0;
            for (var i = s0; i < e0 && i < u8.length; i++) u8[i] = (value || 0) & 0xff;
            return u8;
        };
        u8.compare = function (other) {
            var o = toBytes(other);
            var n = Math.min(o.length, u8.length);
            for (var i = 0; i < n; i++) if (u8[i] !== o[i]) return u8[i] < o[i] ? -1 : 1;
            return u8.length === o.length ? 0 : (u8.length < o.length ? -1 : 1);
        };
        return u8;
    }

    if (typeof G.Buffer !== 'function') {
        var BufferShim = {
            from: function (input, encoding) {
                if (input instanceof Uint8Array) return makeBuffer(new Uint8Array(input));
                if (typeof input === 'string') return makeBuffer(toBytes(input, encoding));
                if (input instanceof ArrayBuffer) return makeBuffer(new Uint8Array(input));
                if (ArrayBuffer.isView(input)) return makeBuffer(new Uint8Array(input.buffer.slice(input.byteOffset, input.byteOffset + input.byteLength)));
                if (Array.isArray(input)) return makeBuffer(new Uint8Array(input));
                return makeBuffer(new Uint8Array(0));
            },
            concat: function (list, totalLength) {
                var chunks = [];
                for (var i = 0; i < list.length; i++) chunks.push(toBytes(list[i]));
                var out = concatBytes(chunks);
                if (typeof totalLength === 'number' && totalLength < out.length) out = out.slice(0, totalLength);
                return makeBuffer(out);
            },
            alloc: function (size, fill) {
                var out = new Uint8Array(size || 0);
                if (fill) out.fill(typeof fill === 'number' ? fill : 0);
                return makeBuffer(out);
            },
            isBuffer: function (value) {
                return value instanceof Uint8Array;
            },
            byteLength: function (input, encoding) {
                if (typeof input === 'string') return toBytes(input, encoding).length;
                if (input instanceof ArrayBuffer) return input.byteLength;
                return input && typeof input.length === 'number' ? input.length : 0;
            }
        };
        G.Buffer = BufferShim;
    }

    /* ---------- URLSearchParams（最小面） ---------- */

    if (typeof G.URLSearchParams !== 'function') {
        var URLSearchParamsShim = function (init) {
            this._pairs = [];
            if (typeof init === 'string') {
                var text = init.charAt(0) === '?' ? init.slice(1) : init;
                if (text) {
                    var parts = text.split('&');
                    for (var i = 0; i < parts.length; i++) {
                        var eq = parts[i].indexOf('=');
                        var k = eq < 0 ? parts[i] : parts[i].slice(0, eq);
                        var v = eq < 0 ? '' : parts[i].slice(eq + 1);
                        this._pairs.push([decodeURIComponent(k.replace(/\+/g, ' ')), decodeURIComponent(v.replace(/\+/g, ' '))]);
                    }
                }
            } else if (init && typeof init === 'object') {
                for (var key in init) {
                    if (Object.prototype.hasOwnProperty.call(init, key)) this._pairs.push([String(key), String(init[key])]);
                }
            }
        };
        URLSearchParamsShim.prototype.get = function (name) {
            for (var i = 0; i < this._pairs.length; i++) if (this._pairs[i][0] === name) return this._pairs[i][1];
            return null;
        };
        URLSearchParamsShim.prototype.getAll = function (name) {
            var out = [];
            for (var i = 0; i < this._pairs.length; i++) if (this._pairs[i][0] === name) out.push(this._pairs[i][1]);
            return out;
        };
        URLSearchParamsShim.prototype.set = function (name, value) {
            var done = false;
            var out = [];
            for (var i = 0; i < this._pairs.length; i++) {
                if (this._pairs[i][0] === name) {
                    if (!done) {
                        out.push([name, String(value)]);
                        done = true;
                    }
                } else out.push(this._pairs[i]);
            }
            if (!done) out.push([name, String(value)]);
            this._pairs = out;
        };
        URLSearchParamsShim.prototype.append = function (name, value) {
            this._pairs.push([name, String(value)]);
        };
        URLSearchParamsShim.prototype.has = function (name) {
            return this.get(name) !== null;
        };
        URLSearchParamsShim.prototype.delete = function (name) {
            this._pairs = this._pairs.filter(function (p) { return p[0] !== name; });
        };
        // application/x-www-form-urlencoded 口径：空格出 '+'（与 node/浏览器
        // URLSearchParams 一致），不能直接用 encodeURIComponent 的 %20。
        function formEncode(s) {
            return encodeURIComponent(s).replace(/%20/g, '+').replace(/[!'()]/g, function (c) {
                return '%' + c.charCodeAt(0).toString(16).toUpperCase();
            }).replace(/\*/g, '%2A');
        }
        URLSearchParamsShim.prototype.toString = function () {
            return this._pairs.map(function (p) {
                return formEncode(p[0]) + '=' + formEncode(p[1]);
            }).join('&');
        };
        URLSearchParamsShim.prototype.forEach = function (fn) {
            for (var i = 0; i < this._pairs.length; i++) fn(this._pairs[i][1], this._pairs[i][0], this);
        };
        URLSearchParamsShim.prototype.entries = function () {
            var pairs = this._pairs.slice();
            var i = 0;
            var it = {
                next: function () {
                    if (i >= pairs.length) return {done: true, value: undefined};
                    var p = pairs[i++];
                    return {done: false, value: [p[0], p[1]]};
                }
            };
            if (typeof Symbol === 'function' && Symbol.iterator) it[Symbol.iterator] = function () { return it; };
            return it;
        };
        URLSearchParamsShim.prototype.keys = function () {
            var e = this.entries();
            var it = {
                next: function () {
                    var n = e.next();
                    return n.done ? n : {done: false, value: n.value[0]};
                }
            };
            if (typeof Symbol === 'function' && Symbol.iterator) it[Symbol.iterator] = function () { return it; };
            return it;
        };
        URLSearchParamsShim.prototype.values = function () {
            var e = this.entries();
            var it = {
                next: function () {
                    var n = e.next();
                    return n.done ? n : {done: false, value: n.value[1]};
                }
            };
            if (typeof Symbol === 'function' && Symbol.iterator) it[Symbol.iterator] = function () { return it; };
            return it;
        };
        // for-of 直接迭代实例（ds 源里常见 for (const [k, v] of params)）
        if (typeof Symbol === 'function' && Symbol.iterator) {
            URLSearchParamsShim.prototype[Symbol.iterator] = function () { return this.entries(); };
        }
        G.URLSearchParams = URLSearchParamsShim;
    }

    /* ---------- setResult（ds 引擎约定） ---------- */

    function cardToVod(card) {
        if (!card || typeof card !== 'object') return card;
        if (card.vod_id !== undefined || card.vod_name !== undefined) return card;
        var vod = {};
        for (var k in card) if (Object.prototype.hasOwnProperty.call(card, k)) vod[k] = card[k];
        vod.vod_id = card.url != null ? String(card.url) : '';
        vod.vod_name = card.title != null ? card.title : '';
        vod.vod_pic = card.img != null ? card.img : (card.pic || '');
        vod.vod_remarks = card.desc != null ? card.desc : (card.remark || '');
        return vod;
    }

    if (typeof G.setResult !== 'function') {
        G.setResult = function (data) {
            if (Array.isArray(data)) return {list: data.map(cardToVod)};
            if (data && typeof data === 'object' && Array.isArray(data.list)) {
                var out = {};
                for (var k in data) if (Object.prototype.hasOwnProperty.call(data, k)) out[k] = data[k];
                out.list = data.list.map(cardToVod);
                return out;
            }
            if (data && typeof data === 'object') return {list: [cardToVod(data)]};
            return {list: []};
        };
    }
    G.__drpyCardToVod = cardToVod;

    /* ---------- 网络：axios / fetch / request ---------- */

    function appendParams(url, params) {
        if (!params) return url;
        var query;
        if (typeof G.URLSearchParams === 'function' && params instanceof G.URLSearchParams) query = params.toString();
        else if (typeof G.URLSearchParams === 'function') {
            query = new G.URLSearchParams(params).toString();
        }
        if (!query) return url;
        return url + (url.indexOf('?') >= 0 ? '&' : '?') + query;
    }

    function axiosShim(urlOrConfig, maybeConfig) {
        var cfg = {};
        if (typeof urlOrConfig === 'string') {
            for (var k1 in (maybeConfig || {})) cfg[k1] = maybeConfig[k1];
            cfg.url = urlOrConfig;
        } else if (urlOrConfig && typeof urlOrConfig === 'object') {
            for (var k2 in urlOrConfig) cfg[k2] = urlOrConfig[k2];
        }
        var method = String(cfg.method || 'GET').toUpperCase();
        var headers = {};
        for (var h in (cfg.headers || {})) headers[h] = cfg.headers[h];
        var data = cfg.data;
        if (data != null && typeof data !== 'string') {
            if (data instanceof Uint8Array) data = bytesToUtf8(data);
            else if (typeof data === 'object') {
                data = JSON.stringify(data);
                var hasCt = Object.keys(headers).some(function (x) { return x.toLowerCase() === 'content-type'; });
                if (!hasCt) headers['Content-Type'] = 'application/json';
            } else data = String(data);
        }
        var options = {method: method, headers: headers};
        if (cfg.timeout) options.timeout = cfg.timeout;
        if (data != null && method !== 'GET' && method !== 'HEAD') options.body = data;
        var url = appendParams(cfg.url, cfg.params);
        return http(url, options).then(function (res) {
            var body = res && res.content != null ? res.content : '';
            var status = res && typeof res.code === 'number' ? res.code : (res && typeof res.status === 'number' ? res.status : 200);
            var outData = body;
            if (cfg.responseType !== 'text' && cfg.responseType !== 'arraybuffer') {
                try {
                    outData = JSON.parse(body);
                } catch (e) {
                    outData = body;
                }
            }
            var result = {status: status, data: outData, headers: (res && res.headers) || {}};
            if (status < 200 || status >= 300) {
                var err = new Error('Request failed with status code ' + status);
                err.response = result;
                err.config = cfg;
                throw err;
            }
            return result;
        });
    }

    if (typeof G.axios !== 'function') {
        ['get', 'delete', 'head', 'options'].forEach(function (m) {
            axiosShim[m] = function (url, cfg) {
                var merged = {};
                for (var k in (cfg || {})) merged[k] = cfg[k];
                merged.method = m.toUpperCase();
                return axiosShim(url, merged);
            };
        });
        ['post', 'put', 'patch'].forEach(function (m) {
            axiosShim[m] = function (url, data, cfg) {
                var merged = {};
                for (var k in (cfg || {})) merged[k] = cfg[k];
                merged.method = m.toUpperCase();
                merged.data = data;
                return axiosShim(url, merged);
            };
        });
        axiosShim.request = function (cfg) { return axiosShim(cfg); };
        axiosShim.create = function (defaults) {
            return function (urlOrConfig, maybeConfig) {
                var merged = {};
                for (var k in (defaults || {})) merged[k] = defaults[k];
                if (typeof urlOrConfig === 'string') {
                    for (var k2 in (maybeConfig || {})) merged[k2] = maybeConfig[k2];
                    merged.url = urlOrConfig;
                } else {
                    for (var k3 in (urlOrConfig || {})) merged[k3] = urlOrConfig[k3];
                }
                return axiosShim(merged);
            };
        };
        axiosShim.default = axiosShim;
        G.axios = axiosShim;
    }

    if (typeof G.fetch !== 'function') {
        G.fetch = function (url, options) {
            return http(url, options || {}).then(function (res) {
                var body = res && res.content != null ? res.content : '';
                var status = res && typeof res.code === 'number' ? res.code : 200;
                return {
                    status: status,
                    ok: status >= 200 && status < 300,
                    headers: (res && res.headers) || {},
                    text: function () { return Promise.resolve(body); },
                    json: function () { return Promise.resolve(JSON.parse(body)); }
                };
            });
        };
    }

    // 同步取正文（与 drpy2 引擎内部 request 的回包口径一致：回正文串）。
    if (typeof G.request !== 'function') {
        G.request = function (url, obj) {
            var res = req(url, obj || {});
            if (!res) return '';
            if (typeof res === 'string') return res;
            return res.content != null ? res.content : '';
        };
    }

    /* ---------- require('node:crypto') 垫片 ---------- */

    function rotl(x, n) {
        return ((x << n) | (x >>> (32 - n))) >>> 0;
    }

    // SM3（GB/T 32905-2016）：纯 JS 实现，标准测试向量验过（见 harness）。
    function sm3Bytes(message) {
        var iv = [0x7380166f, 0x4914b2b9, 0x172442d7, 0xda8a0600, 0xa96f30bc, 0x163138aa, 0xe38dee4d, 0xb0fb0e4e];
        var bitLen = message.length * 8;
        var padded = [];
        for (var i = 0; i < message.length; i++) padded.push(message[i]);
        padded.push(0x80);
        while (padded.length % 64 !== 56) padded.push(0);
        var hi = Math.floor(bitLen / 0x100000000);
        var lo = bitLen >>> 0;
        padded.push((hi >>> 24) & 0xff, (hi >>> 16) & 0xff, (hi >>> 8) & 0xff, hi & 0xff);
        padded.push((lo >>> 24) & 0xff, (lo >>> 16) & 0xff, (lo >>> 8) & 0xff, lo & 0xff);

        function p0(x) { return (x ^ rotl(x, 9) ^ rotl(x, 17)) >>> 0; }
        function p1(x) { return (x ^ rotl(x, 15) ^ rotl(x, 23)) >>> 0; }

        var v = iv.slice();
        var w = new Array(68);
        var w1 = new Array(64);
        for (var block = 0; block < padded.length; block += 64) {
            for (var t = 0; t < 16; t++) {
                var o = block + t * 4;
                w[t] = ((padded[o] << 24) | (padded[o + 1] << 16) | (padded[o + 2] << 8) | padded[o + 3]) >>> 0;
            }
            for (var j = 16; j < 68; j++) {
                w[j] = (p1(w[j - 16] ^ w[j - 9] ^ rotl(w[j - 3], 15)) ^ rotl(w[j - 13], 7) ^ w[j - 6]) >>> 0;
            }
            for (var j2 = 0; j2 < 64; j2++) w1[j2] = (w[j2] ^ w[j2 + 4]) >>> 0;
            var a = v[0], b = v[1], c = v[2], d = v[3], e = v[4], f = v[5], g = v[6], h = v[7];
            for (var r = 0; r < 64; r++) {
                var tj = r < 16 ? 0x79cc4519 : 0x7a879d8a;
                var ss1 = rotl(((rotl(a, 12) + e + rotl(tj, r % 32)) >>> 0), 7);
                var ss2 = (ss1 ^ rotl(a, 12)) >>> 0;
                var ff = r < 16 ? (a ^ b ^ c) : ((a & b) | (a & c) | (b & c));
                var gg = r < 16 ? (e ^ f ^ g) : ((e & f) | (~e & g));
                var tt1 = ((ff + d + ss2 + w1[r]) >>> 0);
                var tt2 = ((gg + h + ss1 + w[r]) >>> 0);
                d = c;
                c = rotl(b, 9);
                b = a;
                a = tt1;
                h = g;
                g = rotl(f, 19);
                f = e;
                e = p0(tt2);
            }
            v = [
                (v[0] ^ a) >>> 0, (v[1] ^ b) >>> 0, (v[2] ^ c) >>> 0, (v[3] ^ d) >>> 0,
                (v[4] ^ e) >>> 0, (v[5] ^ f) >>> 0, (v[6] ^ g) >>> 0, (v[7] ^ h) >>> 0
            ];
        }
        var out = new Uint8Array(32);
        for (var k = 0; k < 8; k++) {
            out[k * 4] = (v[k] >>> 24) & 0xff;
            out[k * 4 + 1] = (v[k] >>> 16) & 0xff;
            out[k * 4 + 2] = (v[k] >>> 8) & 0xff;
            out[k * 4 + 3] = v[k] & 0xff;
        }
        return out;
    }

    function wordArrayToBytes(wordArray) {
        var words = wordArray.words;
        var sigBytes = wordArray.sigBytes;
        var out = new Uint8Array(sigBytes);
        for (var i = 0; i < sigBytes; i++) out[i] = (words[i >>> 2] >>> (24 - (i % 4) * 8)) & 0xff;
        return out;
    }

    function cryptoJs() {
        var c = G.CryptoJS;
        if (!c) throw new Error('CryptoJS 未装载（drpy 引擎未就绪）');
        return c;
    }

    function createHash(algorithm) {
        var algo = String(algorithm || '').toLowerCase();
        var chunks = [];
        var api = {
            update: function (data, encoding) {
                chunks.push(toBytes(data, encoding));
                return api;
            },
            digest: function (encoding) {
                var bytes = concatBytes(chunks);
                var out;
                if (algo === 'sm3') {
                    out = sm3Bytes(bytes);
                } else {
                    var c = cryptoJs();
                    var wa = c.lib.WordArray.create(bytes);
                    var hashed = null;
                    if (algo === 'md5') hashed = c.MD5(wa);
                    else if (algo === 'sha1') hashed = c.SHA1(wa);
                    else if (algo === 'sha256') hashed = c.SHA256(wa);
                    else if (algo === 'sha512') hashed = c.SHA512(wa);
                    else throw new Error('不支持的 hash 算法: ' + algorithm);
                    out = wordArrayToBytes(hashed);
                }
                if (!encoding) return makeBuffer(out);
                return bytesToString(out, encoding);
            }
        };
        return api;
    }

    function parseAesAlgo(algorithm) {
        var m = /^aes-(128|192|256)-(cbc|ctr|ecb)$/i.exec(String(algorithm || ''));
        if (!m) throw new Error('不支持的 cipher 算法: ' + algorithm);
        return {mode: m[2].toLowerCase()};
    }

    function createCipher(algorithm, key, iv, decrypt) {
        var parsed = parseAesAlgo(algorithm);
        var chunks = [];
        var autoPadding = true;
        function run() {
            var c = cryptoJs();
            var data = concatBytes(chunks);
            var params = {
                mode: parsed.mode === 'ctr' ? c.mode.CTR : (parsed.mode === 'ecb' ? c.mode.ECB : c.mode.CBC),
                padding: (parsed.mode === 'ctr' || !autoPadding) ? c.pad.NoPadding : c.pad.Pkcs7
            };
            if (parsed.mode !== 'ecb') params.iv = c.lib.WordArray.create(toBytes(iv));
            var keyWa = c.lib.WordArray.create(toBytes(key));
            if (decrypt) {
                var cipherParams = c.lib.CipherParams.create({ciphertext: c.lib.WordArray.create(data)});
                return wordArrayToBytes(c.AES.decrypt(cipherParams, keyWa, params));
            }
            // AES.encrypt 回的是 CipherParams，密文在 .ciphertext（WordArray）；
            // 直喂 wordArrayToBytes 会读到 undefined 的 words/sigBytes、静默出空。
            return wordArrayToBytes(c.AES.encrypt(c.lib.WordArray.create(data), keyWa, params).ciphertext);
        }
        var api = {
            update: function (data, inputEncoding, outputEncoding) {
                chunks.push(toBytes(data, inputEncoding));
                if (outputEncoding) return '';
                return makeBuffer(new Uint8Array(0));
            },
            final: function (outputEncoding) {
                var out = run();
                if (outputEncoding) return bytesToString(out, outputEncoding);
                return makeBuffer(out);
            },
            setAutoPadding: function (value) {
                autoPadding = value !== false;
                return api;
            }
        };
        return api;
    }

    var nodeCrypto = {
        createHash: createHash,
        createHmac: function () {
            throw new Error('drpy 沙箱未提供 createHmac');
        },
        createCipheriv: function (algorithm, key, iv) { return createCipher(algorithm, key, iv, false); },
        createDecipheriv: function (algorithm, key, iv) { return createCipher(algorithm, key, iv, true); },
        randomBytes: function (size) {
            var out = new Uint8Array(size);
            for (var i = 0; i < size; i++) out[i] = Math.floor(Math.random() * 256);
            return makeBuffer(out);
        },
        constants: {}
    };

    if (typeof G.require !== 'function') {
        G.require = function (name) {
            if (name === 'node:crypto' || name === 'crypto') return nodeCrypto;
            throw new Error('drpy 沙箱不支持的模块: ' + name);
        };
    }
})();
