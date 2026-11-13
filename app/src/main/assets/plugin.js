/**
 * JS环境增强
 */
if (MY_RULE && typeof _displayName != 'undefined') {
//    log("resetTitle:" + MY_RULE.title + "->" + _displayName)
    MY_RULE._title = MY_RULE.title;
    MY_RULE.title = _displayName || MY_RULE.title;
}
;
(function(windows) {
    windows.MY_NAME = '海阔视界';
    function initConfig(obj){
        let config = {}
        if(obj != null && "{}" != JSON.stringify(obj)){
            let _cfg = getMyVar('initConfig', '{}');
            if(_cfg && _cfg.length > 0){
                config = JSON.parse(_cfg)
            }
            for(let key of Object.keys(obj)){
                config[key] = obj[key]
            }
        }
        putMyVar('initConfig', JSON.stringify(config));
        windows.config = config;
    }
    windows.initConfig = initConfig;
    try {
        Object.defineProperty(windows, "config", {
            enumerable: true,
            configurable: true,
            get: function () {
                //懒加载
                if(windows.config0 == null){
                    //log('config0 == null');
                    let _cfg = getMyVar('initConfig', '{}');
                    let config = {};
                    if(_cfg && _cfg.length > 0){
                        config = JSON.parse(_cfg);
                    }
                    windows.config0 = config;
                    return config;
                } else {
                    //log('config0 != null');
                    return windows.config0;
                }
            },
            set: function(v) {
                windows.config0 = v;
            }
        });
    } catch(e){
        log(e.toString())
    }

    windows.console = {
        log: log,
        info: log,
        error: log,
        debug: log,
        warn: log,
        clear: function(){
        }
    };

    /**
    * http接口
    */
    var _fetch0 = fetch;
    var _post0 = post;
    var _request0 = request;
    var _postRequest0 = postRequest;
    var _fetchCookie0 = fetchCookie;

    function _httpFetch(url, options){
        this.url = url;
        this.options = options || {};
        this._error = () => {};
        this._success = () => {};
        this._func = _fetch0;
    }

    function startFetch(that, func){
        try {
            let _a = func(that.url, that.options) || "";
            if(_a == "" || _a.startsWith('error:')) {
                that._error(_a.replace("error:", ""));
            } else if(_a.startsWith("{") && _a.endsWith("}")){
                try {
                    let json = JSON.parse(_a);
                    if(json.url && json.headers){
                        if(json.error){
                            that._error(json.error);
                        } else {
                            if(json.body && json.body.startsWith("{") && json.body.endsWith("}")){
                                try {
                                    json.body = JSON.parse(json.body);
                                } catch(e) {
                                }
                            }
                            that._success(json);
                        }
                    } else {
                        that._success(json);
                    }
                } catch(e) {
                    that._success(_a);
                }
            } else {
                that._success(_a);
            }
        } catch(e) {
            that._error(e);
        }
    }

    _httpFetch.fn = {
        success: function(func){
            this._success = func;
            return this;
        },
        error: function(func){
            this._error = func;
            return this;
        },
        func: function(ff){
            this._func = ff;
            return this;
        },
        headers: function(hds){
            if(!this.options){
                this.options = {};
            }
            Object.assign(this.options, {headers: hds});
            return this;
        },
        start: function() {
            startFetch(this, this._func || _fetch0)
            return this;
        }
    }

    let func0 = {
        fetch: (url, options) => {
            return new _httpFetch(url, options);
        },
        post: (url, options) => {
            return new _httpFetch(url, options).func(_post0);
        },
        request: (url, options) => {
         return new _httpFetch(url, options).func(_request0);
        },
        postRequest: (url, options) => {
            return new _httpFetch(url, options).func(_postRequest0);
        },
        fetchCookie: (url, options) => {
            return new _httpFetch(url, options).func(_fetchCookie0);
        }
    }

    Object.assign(_httpFetch.fn, func0);
    _httpFetch.prototype = _httpFetch.fn;

    windows.http = Object.assign({}, func0);

    /*
    * atob
    */
    windows.window0 = {
      btoa: function(s) {
          let base64hash0 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
          if (/([^\u0000-\u00ff])/.test(s)) {
              throw new Error('INVALID_CHARACTER_ERR');
          }
          let i = 0,
              prev,
              ascii,
              mod,
              result = [];
          while (i < s.length) {
              ascii = s.charCodeAt(i);
              mod = i % 3;
              switch (mod) {
                  case 0:
                      result.push(base64hash0.charAt(ascii >> 2));
                      break;
                  case 1:
                      result.push(base64hash0.charAt((prev & 3) << 4 | (ascii >> 4)));
                      break;
                  case 2:
                      result.push(base64hash0.charAt((prev & 0x0f) << 2 | (ascii >> 6)));
                      result.push(base64hash0.charAt(ascii & 0x3f));
                      break;
              }
              prev = ascii;
              i++;
          }
          if (mod == 0) {
              result.push(base64hash0.charAt((prev & 3) << 4));
              result.push('==');
          } else if (mod == 1) {
              result.push(base64hash0.charAt((prev & 0x0f) << 2));
              result.push('=');
          }
          return result.join('');
      },
      atob: function(s) {
          let base64hash0 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
          s = s.replace(/\s|=/g, '');
          let cur,
              prev,
              mod,
              i = 0,
              result = [];
          while (i < s.length) {
              cur = base64hash0.indexOf(s.charAt(i));
              mod = i % 4;
              switch (mod) {
                  case 0:
                      //TODO
                      break;
                  case 1:
                      result.push(String.fromCharCode(prev << 2 | cur >> 4));
                      break;
                  case 2:
                      result.push(String.fromCharCode((prev & 0x0f) << 4 | cur >> 2));
                      break;
                  case 3:
                      result.push(String.fromCharCode((prev & 3) << 6 | cur));
                      break;
              }
              prev = cur;
              i++;
          }
          return result.join('');
      }
    }

    //图片转base64
    function convertBase64Image(url, headers) {
        const File = java.io.File;
        let javaImport = new JavaImporter();
        javaImport.importPackage(
            Packages.com.example.hikerview.utils
        );
        with(javaImport) {
            let png = "hiker://files/cache/test.png";
            downloadFile(url, png, headers);
            let path = getPath(png).replace("file://", "");
            let bs = _base64.encodeToString(FileUtil.fileToBytes(path), _base64.NO_WRAP);
            new File(path).delete();
            return "data:image/jpeg;base64," + bs;
        }
        return "";
    }
    windows.convertBase64Image = convertBase64Image;

    windows.findItem = function(a){
        let b = windows._findItem(a);
        try {
            if(b && b.extra){
                b.extra = JSON.parse(b.extra);
            }
        } catch(e) {}
        return b;
    }

    windows.findItemsByCls = function(a){
        let b = windows._findItemsByCls(a);
        try {
            if(b){
                for(let i in b){
                    if(b[i] && b[i].extra && b[i].extra.length > 0){
                        b[i].extra = JSON.parse(b[i].extra);
                    }
                }
            }
        } catch(e) {}
        return b;
    }

    function toJsonObj(a) {
        if (!a) {
            return a;
        }
        if(a.startsWith('{') && a.endsWith('}')){
            try {
                return JSON.parse(a);
            }catch(e){}
        }
        if(a.startsWith('[') && a.endsWith(']')){
            try {
                return JSON.parse(a);
            }catch(e){}
        }
        return a;
    }

    function toJsonStr(obj){
        if (obj == null) {
            obj = '';
        }
        if ($.type(obj) !== "string") {
            obj = JSON.stringify(obj);
        }
        return obj;
    }

    windows.storage0 = {
        getItem: function(key, defaultVal) {
            let a = windows.getItem(key, toJsonStr(defaultVal));
            let json = toJsonObj(a);
            return json;
        },
        setItem: function(key, obj){
            obj = toJsonStr(obj);
            windows.setItem(key, obj);
        },
        getPublicItem: function(key, defaultVal) {
            let a = windows.getPublicItem(key, toJsonStr(defaultVal));
            let json = toJsonObj(a);
            return json;
        },
        setPublicItem: function(key, obj){
            obj = toJsonStr(obj);
            windows.setPublicItem(key, obj);
        },
        getVar: function(key, defaultVal) {
            let a = windows.getVar(key, toJsonStr(defaultVal));
            let json = toJsonObj(a);
            return json;
        },
        putVar: function(key, obj){
            obj = toJsonStr(obj);
            windows.putVar(key, obj);
        },
        getMyVar: function(key, defaultVal) {
            let a = windows.getMyVar(key, toJsonStr(defaultVal));
            let json = toJsonObj(a);
            return json;
        },
        putMyVar: function(key, obj) {
            obj = toJsonStr(obj);
            windows.putMyVar(key, obj);
        }
    }
    let addListener0 = windows.addListener;
    windows.addListener = function(param0, param1, param2) {
        if ($.type(param1) !== "string") {
            addListener0(param0, $.toString(param1), param2);
        } else {
            addListener0(param0, param1, param2);
        }
    }

    let confirm0 = windows.confirm;
    windows.confirm = function(param0, param1) {
        if(param0 != null && param0['confirm'] != null && $.type(param0['confirm']) !== "string"){
            param0['confirm'] = $.toString(param0['confirm']);
        }
        if(param0 != null && param0['cancel'] != null && $.type(param0['cancel']) !== "string"){
            param0['cancel'] = $.toString(param0['cancel']);
        }
        confirm0(param0, param1);
    }

    windows.cacheCode = function(param0) {
        if ($.type(param0) !== "string") {
            cacheCode0($.toString(param0));
        } else {
            cacheCode0(param0);
        }
    }

    let requirejs0 = windows.requirejs;
    windows.requirejs = function(param0, param1) {
        let moduleId = param0;
        if(param0 != null && param0.indexOf('http') == 0){
            moduleId = md5(param0);
            fetchCache(param0, -1);
        }
        return requirejs0(moduleId);
    }
    let require0 = windows.require;
    windows.require = function(param0, param1, param2, param3, param4) {
        if(param0 != null && param0.indexOf('://') < 0){
            return requirejs(param0);
        }
        return require0(param0, param1, param2, param3, param4);
    }

    windows.getEpubContent = function(path, c) {
        let content = getEpubContent0(path, c);
        if(content) {
            let key = md5(path) + "@@" + base64Encode(c);
            return content.replace(/\s*?(<div.*?>|<\/div>)\s*?/g, "")
            .replace(/\s*?<p.*?>(.*)<\/p>\s*?/g, "\u3000\u3000$1<br>")
            .replace(/src="(.*?)"/g, 'src="$1?pid=' + key + '"');
        }
        return content;
    }

    windows.findReachableIP = function(arr, timeout) {
        if(arr) {
            let tasks = arr.map(it => {
                return {
                    func: (ip) => {
                        return {
                            ip: ip,
                            ok: ipping(ip, timeout)
                        }
                    },
                    param: it,
                    id: it
                }
            });
            let reachableIP = null;
            be(tasks, {
                func: function(param, id, error, taskResult) {
                    if (taskResult && taskResult.ok) {
                        reachableIP = taskResult.ip;
                        return "break";
                    }
                },
                param: null
            });
            return reachableIP;
        }
        return null;
    }

    windows.clearM3u8Ad = function(url, options, rules) {
        try {
            if(rules != null) {
                return proxyClearM3u8(url, options, rules);
            }
        } catch(e) {

        }
        let f = cacheM3u8(url, options);
        let c = readFile(f.split("##")[0]);
        if (c.includes("#EXT-X-DISCONTINUITY")) {
            let c1 = c.split("#EXT-X-DISCONTINUITY\n");
            if (c1.length == 4) {
                let c2 = c1[0] + c1[1] + c1[3];
                writeFile(f.split("##")[0], c2);
            } else if (c1.length == 6) {
                let c2 = c1[0] + c1[1] + c1[3] + c1[5];
                writeFile(f.split("##")[0], c2);
            } else if (c1.length == 8) {
                let c2 = c1[0] + c1[1] + c1[3] + c1[5] + c1[7];
                writeFile(f.split("##")[0], c2);
            } else if (c1.length == 10) {
                let c2 = c1[0] + c1[1] + c1[3] + c1[5] + c1[7] + c1[9];
                writeFile(f.split("##")[0], c2);
            }
        }
        return f;
    }
    windows.clearM3u8AdLazy = function(url, options, rules) {
        return $().lazyRule((url, options, rules) => { return clearM3u8Ad(url, options, rules)}, url, options, rules);
    }
    windows.convertM3u8WithPngProxy = function(c, options) {
        let c1 = c.split("\n");
        let proxy = null;
        return c1.map(it=> {
            if(it.startsWith("http") && !it.includes(".m3u8") && !it.includes(".key")) {
                if(proxy == null) {
                    proxy = startProxyServer($.toString(() => {
                        let url = base64Decode(MY_PARAMS.u);
                        let headers = JSON.parse(base64Decode(MY_PARAMS.headers));
                        let path = png2Ts(url, headers);
                        return JSON.stringify({
                            statusCode: 302,
                            headers: {
                                "Location": path.startsWith("http") ? path : "file://" + path,
                                "Content-Type": "video/mp2t"
                            },
                            body: path
                        });
                    }));
                }
                return proxy + "?u=" + base64Encode(it) + "&headers=" + base64Encode(JSON.stringify((options || {}).headers || {}));
            }
            return it;
        }).join("\n");
    }
    windows.cacheM3u8WithPngProxy = function(url, options, fileName) {
        let p = cacheM3u8(url, options, fileName);
        if(p.startsWith("file://")) {
            let c = readFile(p.split("##")[0]);
            let c2 = windows.convertM3u8WithPngProxy(c, options);
            writeFile(p.split("##")[0], c2);
            return p;
        } else {
            return p;
        }
    }
    windows.isPageReversed = function() {
        return MY_PARAMS["hiker_reverse"];
    }
    let ruleTitle0 = typeof MY_RULE !== "undefined" && MY_RULE != null ? MY_RULE.title : "";
    function getTypeName(obj) {
        return Object.prototype.toString.call(obj);
    }

    const protoDic = new Map([
        ["[object Array]", Array],
        ["[object Date]", Date],
        ["[object Object]", Object],
        ["[object Promise]", Promise],
        ["[object Map]", Map],
        ["[object Symbol]", Symbol],
        ["[object Array]", Array],
        ["[object Function]", Function],
        ["Error", Error],
        ["TypeError", TypeError],
        ["EvalError", EvalError],
        ["SyntaxError", SyntaxError],
        ["ReferenceError", ReferenceError],
        ["RangeError", RangeError]
    ]);
    //获取变量
    function get0(hashMap, key, def) {
        if (!hashMap.containsKey(key)) return def;
        let val = hashMap.get(key);
        let typeName = getTypeName(val);
        let errorName;
        if (val instanceof java.lang.String) {
            return String(val);
        } else if (val instanceof java.lang.Double) {
            return Number(val);
        } else if (val instanceof java.lang.Boolean) {
            return !!val;
        } else if (typeName === "[object Error]" && protoDic.has((errorName = val.toString()))) {
            val.__proto__ = val.__proto__ && Object.assign(Object.create(protoDic.get(typeName).prototype), val.__proto__);
            return val;
        } else if (typeName !== "[object javaObject]" && protoDic.has(typeName)) {
            val.__proto__ = val.__proto__ && Object.assign(Object.create(protoDic.get(typeName).prototype), val.__proto__);
            return val;
        }
        return val;
    }
    //设置变量
    function put0(hashMap, key, val) {
        //如果是undefined 就不存储 因为会被转成java字符串的undefined，在get方法直接返回undefined
        //或者统一存为[type, val]; get方法在根据type处理
        if (val === void 0) return;
        hashMap.put(key, val);
        return val;
    }
    //清除指定变量
    function clear0(hashMap, key, call) {
        if (hashMap.containsKey(key)) {
            if (typeof call === "function") {
                call(get0(hashMap, key));
            }
            hashMap.remove(key);
        }
    }
    function has0(hashMap, key, call) {
        let res = !!hashMap.containsKey(key);
        if (res && typeof call === "function") {
            call(get0(hashMap, key));
        }
        return res;
    }
    windows.globalMap0 = {
        getVar: (key, def) => {
            return get0(_globalMap, key, def);
        },
        putVar: (key, def) => {
            return put0(_globalMap, key, def);
        },
        hasVar: (key, call) => {
            return has0(_globalMap, key, call);
        },
        clearVar: (key, call) => {
            return clear0(_globalMap, key, call);
        },
        getMyVar: (key, def) => {
            return get0(_globalMap, ruleTitle0 + "@" + key, def);
        },
        putMyVar: (key, def) => {
            return put0(_globalMap, ruleTitle0 + "@" + key, def);
        },
        hasMyVar: (key, call) => {
            return has0(_globalMap, ruleTitle0 + "@" + key, call);
        },
        clearMyVar: (key, call) => {
            return clear0(_globalMap, ruleTitle0 + "@" + key, call);
        }
    }

    windows.getRule = function() {
        return JSON.stringify(MY_RULE);
    }

    function buildOptions0000(op) {
        if(op.jsLoadingInject == undefined) {
            op.jsLoadingInject = true;
        }
        op.blockRules = op.blockRules || [];
        op.blockRules.push(".gif");
        op.blockRules.push(".png");
        op.blockRules.push(".jpg");
        op.blockRules.push(".jpeg");
        op.blockRules.push(".svg");
        op.blockRules.push("google");
    }

    windows.getReadContentData = function(url, options) {
        let js = `
        (function(){
        try {
         let logOpen = true;
         logOpen && fy_bridge_app.log('Readability1: ' + document.readyState + "->" + location.href);
         let originalContent = document.documentElement.outerHTML;
         if(document.readyState == "complete" || originalContent.includes("</p>") || originalContent.includes("</article>")) {
                  logOpen && fy_bridge_app.log('Readability2: ' + document.readyState + "->" + location.href);
                  if(typeof Readability == 'undefined') {
                        eval(fy_bridge_app.fetch('hiker://files/Readability.js', '{}'));
                  }
                  let parser = new DOMParser();
                  let doc = parser.parseFromString(originalContent, 'text/html');
                  let newDoc = doc.cloneNode(true);
                  let article = new Readability(doc).parse();
                  let readabilityContent = article ? article.content : 'Error';
                  let container = newDoc.createElement('div');
                  container.innerHTML = readabilityContent;
                  newDoc.body.appendChild(container);
                  newDoc.querySelectorAll('img').forEach(function(img) {
                     img.remove();
                  });
                  let page = newDoc.documentElement.querySelector('#readability-page-1');
                  let articles = page.querySelectorAll('article');
                  //logOpen && fy_bridge_app.log('Readability: article length->' + articles.length);
                  if(articles && articles.length > 0) {
                    Array.from(articles).forEach(ar => {
                        const fragment = document.createDocumentFragment();
                        Array.from(ar.childNodes).forEach(node => {
                          if (node.nodeType == Node.TEXT_NODE) {
                              // 如果是文本节点，创建一个新的 <p> 元素，并将文本节点内容放入其中
                              const p = document.createElement('p');
                              p.textContent = node.textContent;
                              fragment.appendChild(p);
                          } else {
                              fragment.appendChild(node);
                          }
                      });
                      ar.innerHTML = '';
                      ar.appendChild(fragment);
                    });
                  }
                  let a = newDoc.documentElement.outerHTML;
                  let c = page.innerText.replace(/\s+/g, '');
                  let b = c.length;
                  logOpen && fy_bridge_app.log('Readability: length->' + b + '-->');
                  if(document.readyState == "complete" || b > 300) {
                      if(b < 500 && (c.includes('加载中...') || c.includes('加载...')) && (window.readCount000 == null || window.readCount000 < 20)) {
                        //可能还在渲染中，等5秒
                        window.readCount000 = (window.readCount000 || 1) + 1;
                        logOpen && fy_bridge_app.log('Readability: waiting->' + window.readCount000);
                        return null;
                      } else {
                        logOpen && fy_bridge_app.log('Readability: ok->');
                        let obj = { html: a, next: "", nextPage: "", title: document.title };
                        let allLinks = document.querySelectorAll('a');
                        Array.from(allLinks).forEach(link => {
                            let t = link.textContent.trim();
                            //fy_bridge_app.log("ttt->" + t);
                            if(obj.next == "" && /下一页|下—页|后一页|下页|后页|尾页/.test(t)) {
                                obj.next =  new URL(link.getAttribute("href"), document.location.href).href;
                            } else if(obj.nextPage == "" && /下一章|下—章|下章/.test(t)) {
                                obj.nextPage = new URL(link.getAttribute("href"), document.location.href).href;
                            }
                        });
                        return JSON.stringify(obj);
                      }
                  } else {
                     logOpen && fy_bridge_app.log('Readability: loading->' + b);
                     return null;
                  }
         } else {
            return null;
         }
        } catch(e) {
            fy_bridge_app.log('Readability: error->' + e);
            return null;
        }
        })()
        `;
        let op = options || {};
        buildOptions0000(op);
        let res = executeWebRule(url, js, op);
        if(res == null || res == "" || !res.trim().startsWith("{")) {
            res = "{}";
        }
        let obj = JSON.parse(res);
        //log(obj.html);
        let html = pdfh(obj.html || "", "#readability-page-1&&Html");
        function hasWrongWord(it) {
            if(!it || !it.trim()) {
                return true;
            }
            let words = ['网址', '小说网', '本章未完', '下一页', '章节错误', '下一章', '正在加载...', '更新章节', '上一章', '立即下载',
            '<span> </span>', '<span></span>', 'onclick=', 'javascript:', '域名', 'www.', '.org', '.com', '.top', '.cn'];
            for(let w of words) {
                if(it.includes(w)) {
                    return true;
                }
            }
            let words2 = ['关灯', '开灯', '字体', '大', '小', '中', '.'];
            for(let w of words2) {
                if(it == w) {
                    return true;
                }
            }
            return false;
        }
        if (html.includes('</p>')) {
            let data = pdfa("<div>\n" + html + "\n</div>", 'div&&p')
                                .map(it => pdfh(it, 'p&&Html'))
                                .filter(it => !hasWrongWord(it));
            return { data: data, next:  obj.next, nextPage: obj.nextPage, title: obj.title};
        }
        return { data: [html], next:  obj.next, nextPage: obj.nextPage, title: obj.title};
    }

    windows.loadReadContentPage = function(url0, options) {
        let d = [];
        let url = url0 || MY_URL;
        if(url.includes("baidu.com/tcx") && url.includes("&url=")) {
            url = decodeURIComponent(getParam("url", url, url));
        }
        let data = getReadContentData(url, options);
        for (let i = 0; i < data.data.length; i++) {
            let it = data.data[i];
            d.push({
                title: it,
                col_type: "rich_text",
                extra: {
                    id: i == data.data.length - 1 ? url : undefined
                }
            });
        }
        let fromReader = (options || {}).fromReader || false;
        //log(data.title + '->' + data.nextPage);
        setResult(d);
        if (fromReader) {
            //浏览器阅读模式
            if(data.next || data.nextPage) {
                log('下一页数据：' + data.next + ", " + data.nextPage);
                refreshReadData({
                    title: data.title,
                    currentUrl: url,
                    nextPage: data.next || data.nextPage,
                    id: options.id || MY_RULE.title
                });
            } else {
                //先刷新标题
                refreshReadData({
                    title: data.title,
                    id: options.id || MY_RULE.title
                });
                try {
                    //没有下一页也没有下一章，试试再次获取
                    let o = options || {};
                    o.timeout = 5000;
                    let h = fetch(url, o);
                    if(!data.next) {
                        data.next = pd(h, "a:matches(下一页|下—页|后一页|下页|后页|尾页)&&href", url);
                    }
                    if(!data.nextPage) {
                        data.nextPage = pd(h, "a:matches(下一章|下—章|下章)&&href", url);
                    }
                    log('reload data.next->' + data.next + ", " + data.nextPage);
                    if(!data.next && !data.nextPage) {
                        //还是没有，用网页重试
                        let js2 = `
                        (function() {
                            let obj = { next: "", nextPage: "" };
                            let allLinks = document.querySelectorAll('a');
                            Array.from(allLinks).forEach(link => {
                                let t = link.textContent.trim();
                                if(obj.next == "" && /下一页|下—页|后一页|下页|后页|尾页/.test(t)) {
                                    obj.next =  new URL(link.getAttribute("href"), document.location.href).href;
                                } else if(obj.nextPage == "" && /下一章|下—章|下章/.test(t)) {
                                    obj.nextPage = new URL(link.getAttribute("href"), document.location.href).href;
                                }
                            });
                            return obj.next || obj.nextPage ? JSON.stringify(obj) : null;
                        })()
                        `;
                        let op = options || {};
                        buildOptions0000(op);
                        let hh = executeWebRule(url, js2, op);
                        if(hh == null || hh == "" || !hh.trim().startsWith("{")) {
                            hh = "{}";
                        }
                        let obj2 = JSON.parse(hh);
                        if(!data.next) {
                            data.next = obj2.next;
                        }
                        if(!data.nextPage) {
                            data.nextPage = obj2.nextPage;
                        }
                        log('reload web data.next->' + data.next + ", " + data.nextPage);
                    }
                } catch (e) { log(e.toString()); }
                log('下一页数据：' + data.next + ", " + data.nextPage);
                refreshReadData({
                    currentUrl: url,
                    nextPage: data.next || data.nextPage,
                    id: options.id || MY_RULE.title
                });
            }
        } else {
            //小程序，自动追加下一页
            while (data.next) {
                log('加载下一页：' + data.next);
                let last = url;
                url = data.next;
                data = getReadContentData(url, options);
                let d2 = [];
                for (let i = 0; i < data.data.length; i++) {
                    let it = data.data[i];
                    d2.push({
                        title: it,
                        col_type: "rich_text",
                        extra: {
                            id: i == data.data.length - 1 ? url : undefined
                        }
                    });
                }
                if(d2.length > 0){
                    addItemAfter(last, d2);
                }
            }
        }
    }

    windows.isFromHistoryPage = function() {
        if(typeof FROM_HISTORY == 'undefined') {
            return false;
        }
        return FROM_HISTORY;
    }
})(this);