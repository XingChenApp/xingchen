(function(){
    try {
        var revokeObjectURL0 = window.URL.revokeObjectURL;
        window.URL.revokeObjectURL = function(blob) {
            //不能立即回收blob，因为用户可能要下载，延时3秒由软件看看是不是要下载
            window['blobDownload'] = 0;
            setTimeout(function() {
                if(window['blobDownload'] == 1){
                    //用户要下载blob的数据，延时30秒再回收
                    window['blobDownload'] == 0
                    setTimeout(function() {
                        revokeObjectURL0(blob);
                    }, 30 * 1000);
                } else {
                    //无需下载，立即回收
                    revokeObjectURL0(blob);
                }
            }, 3 * 1000);
        }
    } catch(err) {
    }
    try {
        function addCss(styles) {
          let css;
          styles = styles.replace(/\n+\s*/g, ' ');
          css = document.createElement('style');
          if (css.styleSheet) css.styleSheet.cssText = styles;
          // Support for IE
          else css.appendChild(document.createTextNode(styles)); // Support for the rest
          css.type = 'text/css';
          document.getElementsByTagName('head')[0].appendChild(css);
        }

        addCss(`
        video::-webkit-media-controls-fullscreen-button {
            background-image: -webkit-image-set(url(data:image/svg+xml;base64,PHN2ZyB0PSIxNjQ2NDY4OTkzNjg5IiBjbGFzcz0iaWNvbiIgdmlld0JveD0iMCAwIDEwMjQgMTAyNCIgdmVyc2lvbj0iMS4xIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHAtaWQ9IjI4NjMiIHdpZHRoPSIxMjgiIGhlaWdodD0iMTI4Ij48cGF0aCBkPSJNNDIuNjY2NjY3IDI1NmEyMTMuMzMzMzMzIDIxMy4zMzMzMzMgMCAwIDEgMjEzLjMzMzMzMy0yMTMuMzMzMzMzaDUxMmEyMTMuMzMzMzMzIDIxMy4zMzMzMzMgMCAwIDEgMjEzLjMzMzMzMyAyMTMuMzMzMzMzdjUxMmEyMTMuMzMzMzMzIDIxMy4zMzMzMzMgMCAwIDEtMjEzLjMzMzMzMyAyMTMuMzMzMzMzSDI1NmEyMTMuMzMzMzMzIDIxMy4zMzMzMzMgMCAwIDEtMjEzLjMzMzMzMy0yMTMuMzMzMzMzVjI1NnogbTIxMy4zMzMzMzMtMTI4YTEyOCAxMjggMCAwIDAtMTI4IDEyOHY1MTJhMTI4IDEyOCAwIDAgMCAxMjggMTI4aDUxMmExMjggMTI4IDAgMCAwIDEyOC0xMjhWMjU2YTEyOCAxMjggMCAwIDAtMTI4LTEyOEgyNTZ6IiBmaWxsPSIjRkZGRkZGIiBwLWlkPSIyODY0Ij48L3BhdGg+PHBhdGggZD0iTTUzMy4zMzMzMzMgMjU2YTQyLjY2NjY2NyA0Mi42NjY2NjcgMCAwIDEgNDIuNjY2NjY3LTQyLjY2NjY2N0g2ODIuNjY2NjY3YTEyOCAxMjggMCAwIDEgMTI4IDEyOHYxMDYuNjY2NjY3YTQyLjY2NjY2NyA0Mi42NjY2NjcgMCAxIDEtODUuMzMzMzM0IDBWMzQxLjMzMzMzM2E0Mi42NjY2NjcgNDIuNjY2NjY3IDAgMCAwLTQyLjY2NjY2Ni00Mi42NjY2NjZoLTEwNi42NjY2NjdhNDIuNjY2NjY3IDQyLjY2NjY2NyAwIDAgMS00Mi42NjY2NjctNDIuNjY2NjY3ek0yNTYgNTMzLjMzMzMzM2E0Mi42NjY2NjcgNDIuNjY2NjY3IDAgMCAxIDQyLjY2NjY2NyA0Mi42NjY2NjdWNjgyLjY2NjY2N2E0Mi42NjY2NjcgNDIuNjY2NjY3IDAgMCAwIDQyLjY2NjY2NiA0Mi42NjY2NjZoMTA2LjY2NjY2N2E0Mi42NjY2NjcgNDIuNjY2NjY3IDAgMSAxIDAgODUuMzMzMzM0SDM0MS4zMzMzMzNhMTI4IDEyOCAwIDAgMS0xMjgtMTI4di0xMDYuNjY2NjY3YTQyLjY2NjY2NyA0Mi42NjY2NjcgMCAwIDEgNDIuNjY2NjY3LTQyLjY2NjY2N3oiIGZpbGw9IiNGRkZGRkYiIHAtaWQ9IjI4NjUiPjwvcGF0aD48L3N2Zz4=) 1x) !important;
        }
        video::-webkit-media-controls-volume-control-container {
            display: none !important;
        }
        `)
    } catch(e){
        console.log(e)
    }

    function log() {
        args = [];
        // Note: arguments is part of the prototype
        for (var i = 0; i < arguments.length; i++) {
          args.push(arguments[i]);
        }
        console.log.apply(console, args);
    }
	function hexify(color) {
	  var values = color
		.replace(/rgba?\(/, '')
		.replace(/\)/, '')
		.replace(/[\s+]/g, '')
		.split(',');
	  var a = parseFloat(values[3] || 1),
		  r = Math.floor(a * parseInt(values[0]) + (1 - a) * 255),
		  g = Math.floor(a * parseInt(values[1]) + (1 - a) * 255),
		  b = Math.floor(a * parseInt(values[2]) + (1 - a) * 255);
	  return "#" +
		("0" + r.toString(16)).slice(-2) +
		("0" + g.toString(16)).slice(-2) +
		("0" + b.toString(16)).slice(-2);
	}

	//跨域请求
    function request(url, param){
        if(param == null){
            return fy_bridge_app.fetch(url);
        }
        return fy_bridge_app.fetch(url, JSON.stringify(param));
    }
    function ready(fn) {
      if (document.readyState != 'loading') {
        fn();
      } else {
        document.addEventListener('DOMContentLoaded', fn);
      }
    }
    window.request00 = request;
    function findFavicon() {
      let link = document.querySelector('link[rel="shortcut icon"]');
      if(link == null) {
          let iconLinks = document.querySelectorAll('link[rel="icon"]') || [];
          let maxSize = 0;
          let maxLink = iconLinks.length > 0 ? iconLinks[0] : null;
          iconLinks.forEach(link => {
            try {
                let sizes = link.getAttribute('sizes');
                if (sizes) {
                  let sizeArray = sizes.split('x');
                  let width = parseInt(sizeArray[0]);
                  let height = parseInt(sizeArray[1]);
                  let area = width * height;
                  if (area > maxSize) {
                    maxSize = area;
                    maxLink = link;
                  }
                }
            } catch(e) {
                console.log(e);
            }
          });
          if(maxLink != null) {
            link = maxLink;
          }
      }
      if(link == null) {
          link = document.querySelector('link[rel="apple-touch-icon"]');
      }
      if (link && link.href) {
          console.log('favicon found: ' + link.href);
          let faviconUrl = new URL(link.href, document.location.href).href;
          fy_bridge_app.storeFaviconUrl(faviconUrl);
      } else {
          console.log('No favicon found');
          fy_bridge_app.storeFaviconUrl("");
      }
    };
    ready(function(){
        if(window.request00 != null && window.request == null){
            window.request = window.request00;
        }
        try {
            let r = (document.querySelector('meta[name="referer"]') || document.querySelector('meta[name="referrer"]')).content;
            if(r == null) {
                r = "";
            }
            fy_bridge_app.storeRefererPolicy(r);
        } catch(e) { }
        try {
            findFavicon();
        } catch(e) {
            console.log(e);
        }
        try {
//            function findAndClick(finder, clicker, key) {
//                function test111(count) {
//                    try {
//                        let textarea1 = finder();
//                        textarea1.value = key;
//                        textarea1.focus();
//                        let event = new Event('input', { bubbles: true });
//                        textarea1.dispatchEvent(event);
//                        textarea1.blur();
//                        setTimeout(() => {
//                            clicker().click();
//                        }, 100);
//                    } catch(e) {
//                        if(count < 2) {
//                             setTimeout(() => {
//                                test111(count + 1);
//                             }, 150);
//                        }
//                    }
//                 }
//                 setTimeout(() => {
//                    test111(0);
//                 }, 150);
//            }
//            function findAndClickNow(finder, clicker) {
//                let key = fy_bridge_app.getVar("search_key_1");
//                if(key && key.length > 0) {
//                    fy_bridge_app.clearVar("search_key_1");
//                    findAndClick(finder, clicker, key);
//                }
//            }
//            if(location.href == "https://andisearch.com/") {
//                findAndClickNow(() => {
//                    return document.querySelector(".rcw-input");
//                }, () => {
//                    return document.querySelector(".rcw-send");
//                });
//            }
        } catch(e) {
            console.log(e);
        }

        if(!window.listenUrl000) {
            window.listenUrl000 = 1;
            try {
                let currentHost000 = window.location.host;
                navigation.addEventListener("navigate", (event) => {
                    try {
                        let newUrl = event.destination.url;
                        let newHost = new URL(event.destination.url).host;
                        //console.log('navigate', currentPageUrl000, newUrl);
                        if(newHost != currentHost000 && !event.hashChange) {
                            let block = fy_bridge_app.blockUrl(currentHost000, newHost);
                            if(block == 'yes') {
                                event.preventDefault();
                            } else if(block == 'ask') {
                                 let ok = confirm('当前网站想要跳转打开此页面@' + newUrl);
                                 if(!ok) {
                                     event.preventDefault();
                                 }
                            }
                        }
                    } catch(e) {}
                });
            } catch(e) {
                //低版本
                console.log(e);
           }
        }
    });

    window.setCode = function(){
        try {
            fy_bridge_app.setCode(document.getElementsByTagName('html')[0].outerHTML);
            return '1';
        } catch(e){
            console.log(e);
            fy_bridge_app.setCode("");
            return '0';
        }
    }
    window._getUrls = function() {
        return JSON.parse(fy_bridge_app.getUrls())
    }
    window.fba = fy_bridge_app;
    window.getRequestHeaders = function(){
        return JSON.parse(fy_bridge_app.getRequestHeaders0());
    }
	//异步跨域请求
	function requestAsync(url, param, key, callBack) {
        let rParam = {}, rKey = url;
        if (key == null) {
            callBack = param;
        } else if (callBack == null) {
            rParam = param;
            callBack = key;
        } else {
            rParam = param;
            rKey = key;
        }
        log('1');
        if (typeof callBack === "function") {
            let callbackName = callBack.name;
            if (callbackName === "") {
                if (window.funcSet == null) {
                    window.funcSet = new Map();
                }
                let content = callBack.toString();
                if (window.funcSet.has(content)) {
                    callBack = window.funcSet.get(content);
                } else {
                    for (let i = 0; i < 1000; i++) {
                        if (eval('window.hikerFunc' + i + " == null")) {
                            eval('window.hikerFunc' + i + " = " + content);
                            callBack = 'hikerFunc' + i;
                            window.funcSet.set(content, callBack);
                            break;
                        }
                    }
                }
            }else {
                callBack = callbackName;
            }
        }
        if(rParam == null){
            return fy_bridge_app.fetchAsync(url, rKey, callback);
        }
        fy_bridge_app.fetchAsync(url, JSON.stringify(rParam), rKey, callBack);
    }
    window.requestAsync = requestAsync;

    window.fbaHttpRequest = function(obj){
            if (obj.onreadystatechange) {
                obj.onreadystatechange({readyState: 0});
            }
            if(obj.data && obj.data.getAll != null){
                let param = [];
                for(let k of obj.data.keys()){
                    param.push(k + "=" + obj.data.get(k));
                }
                obj.data = param.join("&");
            } else if(obj.data && Object.prototype.toString.call(obj.data) != '[object String]'){
                obj.data = JSON.stringify(obj.data);
            }
            console.log('GM_xmlhttpRequest, obj:' + JSON.stringify(obj));
            let ticket = new Date().getTime() + "";
            if(obj.onerror){
                window["onerror" + ticket] = obj.onerror;
            }
            if(obj.onload){
                window["onload" + ticket] = obj.onload;
            }
            if(obj.responseType == "stream" && obj.onloadstart){
                window["onloadstart" + ticket] = obj.onloadstart;
            }
            function isSameDomain(url1, url2) {
              const parsedUrl1 = new URL(url1);
              const parsedUrl2 = new URL(url2);
              let h1 = parsedUrl1.hostname;
              let h2 = parsedUrl2.hostname;
              let ha1 = h1.split('.');
              let ha2 = h2.split('.');
              let h11 = ha1[ha1.length - 1];
              let h12 = ha1.length < 2 ? "" : ha1[ha1.length - 2];
              let h21 = ha2[ha2.length - 1];
              let h22 = ha2.length < 2 ? "" : ha2[ha2.length - 2];
              return  h11 == h21 && h12 == h22;
            }
            if(!obj.url.startsWith('http') || isSameDomain(obj.url, location.href)) {
                try{
                    obj.url = new URL(obj.url, location.href).toString();
                    let headers = {
                        cookie: fy_bridge_app.getCookie(location.href),
                        referer: location.href
                    };
                    if(obj.headers) {
                        if(obj.headers.Cookie) {
                            obj.headers.cookie = obj.headers.Cookie;
                            delete obj.headers.Cookie;
                        }
                        if(obj.headers.Referer) {
                            obj.headers.referer = obj.headers.Referer;
                            delete obj.headers.Referer;
                        }
                        for(let k of Object.keys(obj.headers)){
                            headers[k] = obj.headers[k];
                        }
                    }
                    obj.headers = headers;
                } catch(e){
                    console.log('GM_xmlhttpRequest: error: ' + e.toString());
                }
            }
            let key = JSON.stringify({
                url: obj.url,
                ticket: ticket,
                responseType: obj.responseType
            });
            if(obj.onloadstart && obj.responseType != "stream"){
                obj.onloadstart();
            }
            if(obj.onreadystatechange) {
                obj.onreadystatechange({readyState: 1});
                window["onreadystatechange" + ticket] = obj.onreadystatechange;
            }
            requestAsync(obj.url, {
                method: obj.method || 'GET',
                timeout: obj.timeout,
                withHeaders: true,
                withStatusCode: true,
                headers: obj.headers || {},
                body: obj.data || null,
                redirect: !(obj.ignoreRedirect || false),
                responseType: obj.responseType
            }, key, function(key, result){
                console.log('GM_xmlhttpRequest, key:' + key);
                try {
                    key = JSON.parse(key);
                    let ticket = key.ticket;
                    let responseType = key.responseType;
                    let res = JSON.parse(result);
                    let data = res.body;
                    let statusCode = res.statusCode + "";
                    if(res.error != null && res.error != "" && window["onerror" + ticket] != null){
                        window["onerror" + ticket](res.error);
                    }
                    if(responseType == "json"){
                        data = JSON.parse(data == null || data == "" ? "{}" : data);
                    } else if(responseType == "stream") {
                        if(data != null) {
                            var byteCharacters = atob(data);
                            var byteArrays = [];
                            for (var offset = 0; offset < byteCharacters.length; offset += 1024) {
                                var slice = byteCharacters.slice(offset, offset + 1024);
                                var byteNumbers = new Array(slice.length);
                                for (var i = 0; i < slice.length; i++) {
                                  byteNumbers[i] = slice.charCodeAt(i);
                                }
                                var byteArray = new Uint8Array(byteNumbers);
                                byteArrays.push(byteArray);
                            }
                            let blob = new Blob(byteArrays, { type: "application/octet-stream" });
                            function createBlobReader(blob) {
                              var chunkSize = 1024 * 64;
                              var offset = 0;
                              return {
                                read: function() {
                                  return new Promise(function(resolve, reject) {
                                    if (offset >= blob.size) {
                                      resolve({ done: true });
                                      return;
                                    }
                                    var reader = new FileReader();
                                    reader.onloadend = function() {
                                      if (reader.error) {
                                        reject(reader.error);
                                        return;
                                      }
                                      var chunk = new Uint8Array(reader.result);
                                      offset += chunk.length;
                                      resolve({ done: false, value: chunk });
                                    };
                                    var slice = blob.slice(offset, offset + chunkSize);
                                    reader.readAsArrayBuffer(slice);
                                  });
                                }
                              };
                            }
                            data = {
                                blob: blob,
                                getReader: function() {
                                   return createBlobReader(blob);
                                }
                            };
                        }
                    }

                    let statusText = "OK";
                    if(res.statusCode >= 300 && res.statusCode < 400){
                        statusText = "See Other";
                    } else if(res.statusCode >= 400 && res.statusCode < 500){
                        statusText = "Bad Request";
                    } else if(res.statusCode >= 500 && res.statusCode < 600){
                        statusText = "Internal Server Error";
                    }
                    let hh = [];
                    for(let key in res.headers) {
                        let value = res.headers[key];
                        for(let v of value) {
                            hh.push(key + ": " + v);
                        }
                    }
                    let responseHeaders = hh.join("\r\n");
                    let response = {
                        responseText: res.body,
                        headers: res.headers,
                        response: data,
                        responseJSON: data,
                        responseHeaders: responseHeaders,
                        status: res.statusCode,
                        finalUrl: res.url,
                        responseURL: res.url,
                        statusText: statusText,
                        readyState: 4
                    }
                    //console.log('GM_xmlhttpRequest, response:' + JSON.stringify(response));
                    if(responseType == "stream" && window["onloadstart" + ticket] != null) {
                        window["onloadstart" + ticket](response);
                    }
                    if(window["onload" + ticket] != null) {
                        window["onload" + ticket](response);
                    }
                    if(window["onreadystatechange" + ticket] != null) {
                        window["onreadystatechange" + ticket](response);
                    }
                }catch(e){
                    console.log('GM_xmlhttpRequest: error: ', e);
                }
            });
      };
    //JS插件加载
    function mxloadScript(url, callback) {
        var script = document.createElement('script');
        script.type = 'text/javascript';
        script.onload = function() {
            callback();
        }
        script.src = url;
        var heads = document.getElementsByTagName('head');
        if (heads.length > 0) {
            heads[0].appendChild(script);
        } else {
            document.getElementsByTagName('body').item(0).appendChild(script);
        }
    }
    window.addScript0 = mxloadScript;
    //加载css
    function addStyle(styles) {
        var css = document.createElement('style');
        css.type = 'text/css';
        if (css.styleSheet) css.styleSheet.cssText = styles;
        // Support for IE
        else css.appendChild(document.createTextNode(styles)); // Support for the rest
        document.getElementsByTagName('head')[0].appendChild(css);
    }
    window.addStyle = addStyle;

	//log(getChildNodeBgColor(document.body.children));
	log("bridge=>", location.href, document.readyState);

    /**
     * @name 海阔url点击事件生成工具
     * @Author LoyDgIk
     * @version 4
     */
    ;
    (function(windows) {
        "use strict";
        //var [emptyUrl, lazyRule, rule, x5, input, confirm] = ["hiker://empty",  "@rule=", "x5WebView://", "input://", "confirm://"];
        var head = {
            empty: "hiker://empty",
            lazyRule: "@lazyRule=",
            rule: "@rule=",
            x5: "x5WebView://",
            input: "input://",
            confirm: "confirm://",
            x5Lazy: "x5Rule://",
            select: "select://",
            webLazy: "webRule://",
        }

        function HikerUrl(param1, param2, param3) {
            this.param1 = param1 === "" ? "" : (param1 || head.empty);
            this.param2 = param2 || "";
            this.param3 = param3;
            this.isbase64 = false;
        }

        function $$$(param1, param2, param3) {
            return new HikerUrl(param1, param2, param3);
        }
        //静态方法
        Object.assign($$$, {
            hiker: windows,
            exports: {},
            toString() {
                if (arguments.length === 0) {
                    return "$$$";
                } else {
                    return toStringFun(arguments);
                }
            },
            require(path = "", headers) {
                let req_code = "";
                if (loadedMap.has(path)) {
                    return loadedMap.get(path);
                }
                if (path.startsWith("hiker://page/")) {
                    req_code = JSON.parse(request(path) || '{"rule":""}').rule;
                } else if (path.startsWith("hiker://files/") || path.startsWith("file://")) {
                    req_code = request(path);
                } else if (path.startsWith("http://") || path.startsWith("https://")) {
                    req_code = request(path,headers);
                } else {
                    return {};
                }
                let temexports = $$$.exports;
                new Function(req_code).apply(windows);
                let value = $$$.exports;
                loadedMap.set(path,value);
                $$$.exports = temexports;
                return value;
            },
            type(obj) {
                if (obj == null) {
                    return String(obj);
                }
                return typeof obj === "object" || typeof obj === "function" ?
                    class2type[core_toString.call(obj)] || "object" :
                    typeof obj;
            },
            stringify(Data, Pattern) {
                switch (Object.prototype.toString.call(Data)) {
                    case "[object Undefined]":
                        return "undefined";
                        break;
                    case "[object Null]":
                        return "null";
                        break;
                    case "[object Function]":
                        return Data.toString();
                        break;
                    case "[object Array]":
                        return "[" + Data.map(item => {
                            return $$$.stringify(item);
                        }).toString() + "]";
                        break;
                    case "[object Object]":
                        return "{" + Object.keys(Data).map(item => {
                            return '"' + item + '":' + $$$.stringify(Data[item]);
                        }).join(",") + "}";
                        break;
                    default:
                        return JSON.stringify(Data);
                }
            },
            log(obj) {
                for (let i = 0; i < arguments.length; i++) {
                    fba.log(arguments[i]);
                }
                return obj;
            }

        });
        let dateFormatCache = {};
        let loadedMap=new Map;
        let class2type = {},
            classtype = ["Boolean", "Number", "String,", "Function", "Array", "Date", "RegExp", "Object", "Error", "Symbol", "Window"],
            core_toString = class2type.toString;

        classtype.forEach((name) => {
            class2type["[object " + name + "]"] = name.toLowerCase();
        });

        function base64Func(tg, funcStr) {
            if (tg.isbase64) {
                let q = tg.base64quote;
                return 'eval(base64Decode(' + q + fba.base64Encode(funcStr) + q + '));';
            } else {
                return funcStr;
            }
        }

        function toStringFun(arr) {
            var args = [];
            for (let i = 1, j = 0; i < arr.length; i++, j++) {
                args[j] = $$$.stringify(arr[i]);
            }
            if (typeof arr[0] === "function") {
                return "(" + arr[0] + ")(" + args.toString() + ")";
            } else {
                return "";
            }
        }
        //方法
        $$$.fn = {
            constructor: HikerUrl,
            b64(quote) {
                this.base64quote = quote || "\"";
                this.isbase64 = !this.isbase64;
                return this;
            },
            rule() {
                return this.param1 + head.rule + (this.param2 || "js:" + base64Func(this, toStringFun(arguments)));
            },
            lazyRule() {
                return this.param1 + head.lazyRule + this.param2 + ".js:" + base64Func(this, toStringFun(arguments));
            },
            input() {
                return head.input + JSON.stringify({
                    value: this.param1,
                    hint: this.param2,
                    js: toStringFun(arguments)
                });
            },
            confirm() {
                return head.confirm + this.param1 + ".js:" + base64Func(this, toStringFun(arguments));
            },
            x5Lazy() {
                return head.x5Lazy + this.param1 + "@" + toStringFun(arguments);
            },
            webLazy() {
                return head.webLazy + this.param1 + "@" + toStringFun(arguments);
            },
            select() {
                return head.select + JSON.stringify({
                    title: this.param3,
                    options: Array.isArray(this.param1) ? this.param1 : [],
                    col: this.param2 || 1,
                    js: toStringFun(arguments)
                });
            },
            x5LazyRule(fun) {
                fy_bridge_app.parseLazyRuleAsync(this.param1 + head.lazyRule + this.param2 + ".js:eval(decodeURIComponent(`" + encodeURIComponent(toStringFun(arguments)) + "`))", "console.log(input)");
            }
        }
        HikerUrl.prototype = $$$.prototype = $$$.fn;
        Object.defineProperty(windows, "$$$", {
            value: $$$,
            writable: true,
            enumerable: true,
            configurable: true
        });
    })(window);

    if(!window.isPc && fy_bridge_app.isPc() == 'true'){
        window.isPc = true;
        var oMeta = document.createElement('meta');
        oMeta.content = 'target-densitydpi=400';
        oMeta.name = 'viewport';
        document.getElementsByTagName('head')[0].appendChild(oMeta);
    }
    try {
        if(!window.isCopyHook000) {
            window.isCopyHook000 = true;
            let dw00 = document.execCommand;
            document.execCommand = function (a) {
                if(a.toLowerCase() == 'copy' ) {
                    if (fy_bridge_app.blockCopy && fy_bridge_app.blockCopy(location.href) == '1') {
                        console.log('block copy');
                    } else {
                        return dw00.call(document, a);
                    }
                } else {
                    return dw00.call(document, a);
                }
            };
            let wt000 = navigator.clipboard.writeText;
            navigator.clipboard.writeText = function(text) {
                if (fy_bridge_app.blockCopy && fy_bridge_app.blockCopy(location.href) == '1') {
                    console.log('block copy');
                    return Promise.resolve();
                } else {
                    return wt000.apply(navigator.clipboard, arguments);
                }
            };
        }
    } catch(e) {
        console.log(e);
    }
})();
