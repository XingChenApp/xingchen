// drpy 的 ds 型源适配桥（只在 DrpySpider 探到函数字段规则时装载）。
//
// ds 型源（drpys 生态新式源）的 rule 把 一级/搜索/二级/推荐/class_parse/
// lazy/proxy_rule 写成 async 函数，引擎（drpy2）的 categoryParse 只认
// 字符串型 一级、函数型直接回 "{}"，接不住。本桥把这类 rule 适配成与
// 普通通道同名的蜘蛛对象（init/home/homeVod/category/detail/search/
// play/proxy/sniffer/isVideo/action），方法一律返回 JSON 字符串（与
// catvod 通道口径一致），异步函数由 Async.run 的 Promise 通道等待。
//
// 约定（按 ds 源里的实际用法）：
// - class_parse() 返回 {class:[{type_id,type_name}], filters:{tid:[…]}}，
//   本就 TVBox 形态，home 直通；只有 class_parse 是函数时走本桥，
//   字符串型 class_parse 归引擎通道（混合型源的 class_parse 字符串
//   在本桥回落 class_name/class_url，两者都没有则分类为空，如实记账）。
// - 一级(tid, pg, filter, extend) / 搜索(key, quick, pg) / 推荐() 的
//   返回经 setResult 约定（数组或 {list}），本桥补分页字段：源给了
//   page/pagecount/limit/total 就用源的；没给时列表非空按「还有下一页」
//   记 pagecount=pg+1（壳子多问一页、空页收尾），空列表 pagecount=pg。
// - 二级 以 this={orId, id, requestHost} 调用（源用 this.orId 取详情 id、
//   this.requestHost 拼本壳代理门面地址），返回的 vod 对象直通为
//   {list:[vod]}（源注释：二级不经 setResult、直接返回 vod_* 完整对象）。
// - lazy 以 this={orId, id, requestHost} 调用 (flag, id)，返回对象
//   JSON 直通、字符串直通。
// - proxy_rule(params) 的返回数组 [状态码, 类型, 正文, …] 直通给壳子
//   proxy 通道；__range 等 ds 框架注入字段由壳子 params 决定，本桥不造。
(function () {
    'use strict';

    var rule = globalThis.__DRPY_RULE__;
    if (!rule) return;

    function hostBase() {
        try {
            return 'http://127.0.0.1:' + getPort();
        } catch (e) {
            return 'http://127.0.0.1:9978';
        }
    }

    function pgnum(pg) {
        var n = parseInt(pg, 10);
        return isNaN(n) || n < 1 ? 1 : n;
    }

    var cardToVod = typeof globalThis.__drpyCardToVod === 'function' ? globalThis.__drpyCardToVod : function (c) { return c; };

    function normalize(res, pg) {
        var out = {list: [], page: pg, pagecount: pg, limit: 0, total: 0};
        if (res == null) return JSON.stringify(out);
        var list = null;
        var hasPaging = false;
        if (Array.isArray(res)) {
            list = res;
        } else if (typeof res === 'object' && Array.isArray(res.list)) {
            list = res.list;
            if (typeof res.page === 'number') { out.page = res.page; hasPaging = true; }
            if (typeof res.pagecount === 'number') { out.pagecount = res.pagecount; hasPaging = true; }
            if (typeof res.limit === 'number') out.limit = res.limit;
            if (typeof res.total === 'number') out.total = res.total;
        } else if (typeof res === 'object' && (res.vod_id !== undefined || res.url !== undefined || res.vod_name !== undefined)) {
            list = [res];
        }
        if (!list) return JSON.stringify(out);
        out.list = list.map(cardToVod);
        if (!out.limit) out.limit = out.list.length;
        if (!hasPaging) {
            out.pagecount = out.list.length > 0 ? pg + 1 : pg;
            out.total = out.list.length > 0 ? pg * out.list.length + 1 : 0;
        }
        return JSON.stringify(out);
    }

    function thisCtx(id) {
        return {orId: id, id: id, requestHost: hostBase()};
    }

    globalThis.__JS_SPIDER__ = {
        init: async function (ext) {
            if (typeof rule.预处理 === 'string' && rule.预处理.trim()) {
                var code = rule.预处理.trim();
                if (code.startsWith('js:')) code = code.replace('js:', '');
                try {
                    eval(code);
                } catch (e) {
                    console.log('预处理执行失败:' + e.message);
                }
            }
            if (typeof rule.init === 'function') await rule.init(ext);
        },
        home: async function (filter) {
            if (typeof rule.class_parse === 'function') {
                var r = await rule.class_parse();
                return JSON.stringify({
                    class: (r && r.class) || [],
                    filters: (r && r.filters) || {}
                });
            }
            if (rule.class_name && rule.class_url) {
                var names = String(rule.class_name).split('&');
                var urls = String(rule.class_url).split('&');
                var classes = [];
                for (var i = 0; i < Math.min(names.length, urls.length); i++) {
                    classes.push({type_id: urls[i], type_name: names[i]});
                }
                return JSON.stringify({class: classes, filters: rule.filter || {}});
            }
            return JSON.stringify({class: [], filters: {}});
        },
        homeVod: async function () {
            if (typeof rule.推荐 === 'function') return normalize(await rule.推荐(), 1);
            return JSON.stringify({list: []});
        },
        category: async function (tid, pg, filter, extend) {
            var page = pgnum(pg);
            if (typeof rule.一级 === 'function') {
                return normalize(await rule.一级(tid, page, filter, extend), page);
            }
            return normalize(null, page);
        },
        detail: async function (id) {
            if (typeof rule.二级 === 'function') {
                var res = await rule.二级.call(thisCtx(id));
                if (res == null) return JSON.stringify({list: []});
                if (typeof res === 'string') return res;
                if (Array.isArray(res.list)) return JSON.stringify(res);
                return JSON.stringify({list: [res]});
            }
            return JSON.stringify({list: []});
        },
        search: async function (key, quick, pg) {
            var page = pgnum(pg);
            if (typeof rule.搜索 === 'function') {
                return normalize(await rule.搜索(key, quick, page), page);
            }
            return normalize(null, page);
        },
        play: async function (flag, id, flags) {
            if (typeof rule.lazy === 'function') {
                var res = await rule.lazy.call(thisCtx(id), flag, id);
                if (res == null) return JSON.stringify({parse: 0, url: ''});
                return typeof res === 'string' ? res : JSON.stringify(res);
            }
            return JSON.stringify({parse: 0, url: id});
        },
        proxy: async function (params) {
            if (typeof rule.proxy_rule === 'function') return await rule.proxy_rule(params);
            return null;
        },
        sniffer: function () {
            return !!rule.sniffer;
        },
        isVideo: function (url) {
            return false;
        },
        action: async function (action) {
            if (typeof rule.action === 'function') {
                var res = await rule.action(action);
                if (res == null) return null;
                return typeof res === 'string' ? res : JSON.stringify(res);
            }
            return null;
        }
    };
})();
