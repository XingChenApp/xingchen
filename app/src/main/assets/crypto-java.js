(function() {
    // 8.83 官方 crypto-java.js 的 QuickJS 兼容实现
    // 原版依赖 Rhino Java 互操作（java.lang.String/javax.crypto.Cipher/android.util.Base64），
    // 宿主为 QuickJS 无 Java 互操作，故用纯 JS + 全局 CryptoJS（aes.js 已注入）实现相同 API。
    // API 对照 8.83 decompiled/assets/crypto-java.js：Data/Digest/AES/DES/DESede，$.exports 导出。

    function toBytes(arr) {
        var r = [];
        for (var i = 0; i < arr.length; i++) r.push(arr[i] & 255);
        return r;
    }

    // ---- UTF-8 编解码（纯 JS） ----
    function utf8Encode(str) {
        var bytes = [];
        for (var i = 0; i < str.length; i++) {
            var c = str.charCodeAt(i);
            if (c < 0x80) {
                bytes.push(c);
            } else if (c < 0x800) {
                bytes.push(0xC0 | (c >> 6), 0x80 | (c & 0x3F));
            } else if (c >= 0xD800 && c <= 0xDBFF && i + 1 < str.length) {
                var c2 = str.charCodeAt(i + 1);
                if (c2 >= 0xDC00 && c2 <= 0xDFFF) {
                    var cp = 0x10000 + ((c - 0xD800) << 10) + (c2 - 0xDC00);
                    bytes.push(0xF0 | (cp >> 18), 0x80 | ((cp >> 12) & 0x3F), 0x80 | ((cp >> 6) & 0x3F), 0x80 | (cp & 0x3F));
                    i++;
                } else {
                    bytes.push(0xE0 | (c >> 12), 0x80 | ((c >> 6) & 0x3F), 0x80 | (c & 0x3F));
                }
            } else {
                bytes.push(0xE0 | (c >> 12), 0x80 | ((c >> 6) & 0x3F), 0x80 | (c & 0x3F));
            }
        }
        return bytes;
    }

    function utf8Decode(bytes) {
        var str = '';
        for (var i = 0; i < bytes.length; i++) {
            var b = bytes[i];
            if (b < 0x80) {
                str += String.fromCharCode(b);
            } else if ((b & 0xE0) === 0xC0) {
                str += String.fromCharCode(((b & 0x1F) << 6) | (bytes[i + 1] & 0x3F));
                i++;
            } else if ((b & 0xF0) === 0xE0) {
                str += String.fromCharCode(((b & 0x0F) << 12) | ((bytes[i + 1] & 0x3F) << 6) | (bytes[i + 2] & 0x3F));
                i += 2;
            } else if ((b & 0xF8) === 0xF0) {
                var cp = ((b & 0x07) << 18) | ((bytes[i + 1] & 0x3F) << 12) | ((bytes[i + 2] & 0x3F) << 6) | (bytes[i + 3] & 0x3F);
                cp -= 0x10000;
                str += String.fromCharCode(0xD800 + (cp >> 10), 0xDC00 + (cp & 0x3FF));
                i += 3;
            }
        }
        return str;
    }

    // ---- Base64（纯 JS） ----
    var B64_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    function base64Encode(bytes) {
        var s = '';
        for (var i = 0; i < bytes.length; i += 3) {
            var b0 = bytes[i], b1 = i + 1 < bytes.length ? bytes[i + 1] : 0, b2 = i + 2 < bytes.length ? bytes[i + 2] : 0;
            s += B64_CHARS[b0 >> 2] + B64_CHARS[((b0 & 3) << 4) | (b1 >> 4)];
            s += i + 1 < bytes.length ? B64_CHARS[((b1 & 15) << 2) | (b2 >> 6)] : '=';
            s += i + 2 < bytes.length ? B64_CHARS[b2 & 63] : '=';
        }
        return s;
    }
    function base64Decode(str) {
        str = String(str).replace(/[^A-Za-z0-9+/=]/g, '');
        var bytes = [];
        for (var i = 0; i < str.length; i += 4) {
            var c0 = B64_CHARS.indexOf(str[i]), c1 = B64_CHARS.indexOf(str[i + 1]);
            var c2 = str[i + 2] === '=' ? 0 : B64_CHARS.indexOf(str[i + 2]);
            var c3 = str[i + 3] === '=' ? 0 : B64_CHARS.indexOf(str[i + 3]);
            bytes.push((c0 << 2) | (c1 >> 4));
            if (str[i + 2] !== '=') bytes.push(((c1 & 15) << 4) | (c2 >> 2));
            if (str[i + 3] !== '=') bytes.push(((c2 & 3) << 6) | c3);
        }
        return bytes;
    }

    function hexEncode(bytes) {
        var s = '';
        for (var i = 0; i < bytes.length; i++) {
            var h = bytes[i].toString(16);
            s += h.length < 2 ? '0' + h : h;
        }
        return s;
    }
    function hexDecode(str) {
        str = String(str).toLowerCase();
        var bytes = [];
        for (var i = 0; i < str.length; i += 2) {
            bytes.push(parseInt(str.substr(i, 2), 16));
        }
        return bytes;
    }

    // ---- Data ----
    function Data(bytes) {
        this.bytes = toBytes(bytes || []);
    }
    Data.parseStr = function(str) { return new Data(utf8Encode(String(str))); };
    Data.parseUTF8 = function(str) { return Data.parseStr(str); };
    Data.parseUTF16 = function(str) {
        str = String(str);
        var bytes = [];
        for (var i = 0; i < str.length; i++) {
            var c = str.charCodeAt(i);
            bytes.push(c & 255, (c >> 8) & 255);
        }
        return new Data(bytes);
    };
    Data.parseHex = function(str) { return new Data(hexDecode(str)); };
    Data.parseBase64 = function(str) { return new Data(base64Decode(str)); };
    Data.parseLatin1 = function(str) {
        str = String(str);
        var bytes = [];
        for (var i = 0; i < str.length; i++) bytes.push(str.charCodeAt(i) & 255);
        return new Data(bytes);
    };
    Data.parseUTF16LE = Data.parseUTF16;
    Data.parseInputStream = function(input) {
        if (input == null) return new Data([]);
        if (typeof input === 'string') return new Data(utf8Encode(input));
        if (input.bytes) return new Data(input.bytes);
        if (typeof input.length === 'number') return new Data(input);
        return new Data([]);
    };

    Data.prototype.toHex = function() { return hexEncode(this.bytes); };
    Data.prototype.toBytes = function() { return this.bytes.slice(); };
    Data.prototype.toString = function() { return utf8Decode(this.bytes); };
    Data.prototype.toLatin1 = function() {
        var s = '';
        for (var i = 0; i < this.bytes.length; i++) s += String.fromCharCode(this.bytes[i]);
        return s;
    };
    Data.prototype.toUTF16LE = function() {
        var s = '';
        for (var i = 0; i + 1 < this.bytes.length; i += 2) s += String.fromCharCode(this.bytes[i] | (this.bytes[i + 1] << 8));
        return s;
    };
    Data.prototype.toUTF16 = Data.prototype.toUTF16LE;
    Data.prototype.toBase64 = function() { return base64Encode(this.bytes); };
    Data.prototype.toDigest = function() { return new Digest(this); };
    Data.prototype.toInputStream = function() { return this.bytes.slice(); };
    Data.prototype.toUint8Array = function() { return this.bytes.slice(); };
    Data.prototype.base64Decode = function() { this.bytes = base64Decode(utf8Decode(this.bytes)); return this; };
    Data.prototype.base64Encode = function() { this.bytes = utf8Encode(base64Encode(this.bytes)); return this; };
    Data.prototype.length = function() { return this.bytes.length; };

    // ---- Digest（经 CryptoJS） ----
    function Digest(data) { this.data = data; }
    Digest.digest = function(data, algo) {
        var b = toBytes(data.toBytes());
        var words = [];
        for (var i = 0; i < b.length; i += 4) {
            words.push(((b[i] << 24) | ((b[i + 1] || 0) << 16) | ((b[i + 2] || 0) << 8) | (b[i + 3] || 0)) | 0);
        }
        var wa = CryptoJS.lib.WordArray.create(words, b.length);
        var h;
        switch (algo) {
            case 'MD5': h = CryptoJS.MD5(wa); break;
            case 'SHA-1': h = CryptoJS.SHA1(wa); break;
            case 'SHA-256': h = CryptoJS.SHA256(wa); break;
            case 'SHA-384': h = CryptoJS.SHA384(wa); break;
            case 'SHA-512': h = CryptoJS.SHA512(wa); break;
            default: throw new Error('Unsupported digest: ' + algo);
        }
        return h.toString(CryptoJS.enc.Hex);
    };
    Digest.prototype.md5 = function() { return Digest.digest(this.data, 'MD5'); };
    Digest.prototype.sha1 = function() { return Digest.digest(this.data, 'SHA-1'); };
    Digest.prototype.sha256 = function() { return Digest.digest(this.data, 'SHA-256'); };
    Digest.prototype.sha384 = function() { return Digest.digest(this.data, 'SHA-384'); };
    Digest.prototype.sha512 = function() { return Digest.digest(this.data, 'SHA-512'); };
    Digest.prototype.md2 = function() { throw new Error('MD2 not supported'); };

    // ---- 对称加解密（经 CryptoJS） ----
    function toWordArray(data) {
        if (typeof data === 'string') return CryptoJS.enc.Utf8.parse(data);
        var b = toBytes(data.bytes || data);
        // CryptoJS WordArray.words 是 32 位大端字数组，需手动打包（create(byte[]) 会误把每字节当一字）
        var words = [];
        for (var i = 0; i < b.length; i += 4) {
            words.push(((b[i] << 24) | ((b[i + 1] || 0) << 16) | ((b[i + 2] || 0) << 8) | (b[i + 3] || 0)) | 0);
        }
        return CryptoJS.lib.WordArray.create(words, b.length);
    }
    function parseMode(modeStr) {
        var m = String(modeStr || '').toUpperCase();
        if (m.indexOf('/ECB/') >= 0) return CryptoJS.mode.ECB;
        if (m.indexOf('/CTR/') >= 0) return CryptoJS.mode.CTR;
        if (m.indexOf('/CFB/') >= 0) return CryptoJS.mode.CFB;
        if (m.indexOf('/OFB/') >= 0) return CryptoJS.mode.OFB;
        return CryptoJS.mode.CBC;
    }
    function parsePadding(modeStr) {
        var m = String(modeStr || '').toUpperCase();
        if (m.indexOf('NOPADDING') >= 0) return CryptoJS.pad.NoPadding;
        return CryptoJS.pad.Pkcs7;
    }
    function cipherProcess(isEncrypt, algo, input, key, option) {
        option = option || {};
        var keyWa = toWordArray(key);
        var ivWa = option.iv != null ? toWordArray(option.iv) : undefined;
        var cfg = { mode: parseMode(option.mode), padding: parsePadding(option.mode) };
        if (ivWa) cfg.iv = ivWa;
        var inWa;
        if (typeof input === 'string') {
            inWa = isEncrypt ? CryptoJS.enc.Utf8.parse(input) : CryptoJS.enc.Base64.parse(input);
        } else {
            inWa = toWordArray(input);
        }
        var out;
        if (algo === 'AES') {
            out = isEncrypt ? CryptoJS.AES.encrypt(inWa, keyWa, cfg) : CryptoJS.AES.decrypt({ ciphertext: inWa }, keyWa, cfg);
        } else if (algo === 'DES') {
            out = isEncrypt ? CryptoJS.DES.encrypt(inWa, keyWa, cfg) : CryptoJS.DES.decrypt({ ciphertext: inWa }, keyWa, cfg);
        } else {
            out = isEncrypt ? CryptoJS.TripleDES.encrypt(inWa, keyWa, cfg) : CryptoJS.TripleDES.decrypt({ ciphertext: inWa }, keyWa, cfg);
        }
        var words = out.words || (out.ciphertext && out.ciphertext.words);
        var sigBytes = out.sigBytes != null ? out.sigBytes : (out.ciphertext ? out.ciphertext.sigBytes : 0);
        var bytes = [];
        for (var i = 0; i < sigBytes; i++) bytes.push((words[i >>> 2] >>> (24 - (i % 4) * 8)) & 255);
        return new Data(bytes);
    }
    function makeCipher(algo) {
        return {
            encrypt: function(data, key, option) { return cipherProcess(true, algo, data, key, option); },
            decrypt: function(data, key, option) { return cipherProcess(false, algo, data, key, option); }
        };
    }

    $.exports = {
        Data: Data,
        Digest: Digest,
        AES: makeCipher('AES'),
        DES: makeCipher('DES'),
        DESede: makeCipher('DESede')
    };
})()
