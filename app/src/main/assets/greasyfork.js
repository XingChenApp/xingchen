(function(){
    requestAsync("${installUrl}", (ckey, code) => {
        const VERSION = '20.4.11';
        const DEBUG = true;
        const RULE_NAME = "${RULE_NAME}";
        function log(...args) {
            if (!DEBUG) return;
            const message =
              `油猴转换 › ` +
              args.map((v) => (typeof v === 'object' ? JSON.stringify(v) : v)).join(' ');
            console.log(
              '%c ' + message,
              'background: #d3f9d8; color: #343a40; padding: 6px; border-radius: 6px;'
            );
        }
        //解析meta
        function parseMeta(metaString) {
          //log('parseMeta - metaString:', metaString);
          const meta = {};
          metaString
            .trim()
            .split(/[\s\n]*\n[\s\n]*/)
            .forEach((v) => {
                const matches = v.match(/\/\/\s*@(\S+)\s+(.+)/);
                if (!matches) return;
                const key = matches[1];
                const value = matches[2];
                if (!meta[key]) {
                  meta[key] = [];
                }
                meta[key].push(value.trim());
            });
          //log('parseMeta - meta:', meta);
          return meta;
        }

        const matches = code.match(
          /\/\/\s*==\s*UserScript\s*==\n*([\s\S\n]+?)\n*\/\/\s*==\s*\/UserScript\s*==\n*([\s\S]+)/
        );

        const metaString = matches[1];
        const codeString = matches[2];
        const meta = parseMeta(metaString);
        const NAME = meta.name && meta.name[0];
        log('start:', NAME);

        function toRegex(s, s2) {
            if(s == null || s == ""){
              return new RegExp(s2, 'i');
            }
            if(s2 == null || s2 == ""){
              return new RegExp(s.replace(/\*/g, '(.*?)').replace(/\//g, '\\/'), 'i');
            }
            return new RegExp(
                s.replace(/\*/g, '(.*?)').replace(/\//g, '\\/') + '|' + s2,
                'i'
            );
        }
        //匹配页面
        if (meta.exclude) {
            let EXCLUDE = toRegex(meta.exclude.join('|'));
            if (EXCLUDE && EXCLUDE.test(location.href)) return;
        }

        let matchUrls = [];
        if (meta.match) {
          matchUrls = matchUrls.concat(meta.match);
        }
        let includeRegex = [];
        if (meta.include) {
          matchUrls = matchUrls.concat(meta.include.map(s => {
            if(s.startsWith('/') && s[s.length - 1] == "/"){
              s = s.slice(1, s.length - 1);
              includeRegex.push(s);
              return null;
            } else {
              return s;
            }
          }).filter(i => i != null));
        }
        const MATCH = toRegex(matchUrls.join('|'), includeRegex.join('|'));
        if (!MATCH.test(location.href)) {
            return
        }
        log('match: ' + NAME);
        if(window[NAME] == '1' && meta['run-at'] != null && meta['run-at'].length == 1){
            log('second load, just return: ' + NAME);
            return;
        }
        window[NAME] = '1';

        let isRequire = !!meta.require;
        let RequireStack = (meta.require && meta.require.length) || 0;
        function setInterval0(func, t){
            setTimeout(() => {
                if(!func()){
                  setInterval0(func, t);
                }
            }, t);
        }
        //Hook油猴方法
        function addCss(styles) {
            let css;
            styles = styles.replace(/\\n+\\s*/g, ' ');
            css = document.createElement('style');
            if (css.styleSheet) css.styleSheet.cssText = styles;
            // Support for IE
            else css.appendChild(document.createTextNode(styles)); // Support for the rest
            css.type = 'text/css';
            document.getElementsByTagName('head')[0].appendChild(css);
        }
        function getFromStore(key, defaultValue = undefined) {
             let value = fy_bridge_app.getItem(RULE_NAME, key);
             if("undefined" === value || "null" === value) {
                 value = undefined;
             }
             try {
               value = JSON.parse(value);
             } catch (_) {}
             return value != null ? value : defaultValue;
        }
        const Store = {
          get(key, defaultValue = undefined) {
            return getFromStore(key, defaultValue);
          },
          set(key, value = null) {
            fy_bridge_app.setItem(RULE_NAME, key, JSON.stringify(value));
            let k = "hiker-ValueChangeListener";
            let base = window[k] || {};
            for(let key in Object.keys(base)) {
                let map = base[key] || {};
                if(Object.keys(map).length > 0) {
                    let old = getFromStore(key, undefined);
                    for(let fnName in Object.keys(map)) {
                          if(map[fnName]) {
                                //暂不支持监听别的脚本修改的值
                                map[fnName](key, old, value, false);
                          }
                    }
                }
            }
          },
          remove(key) {
            fy_bridge_app.removeItem(RULE_NAME, key);
          },
          list() {
            return JSON.parse(fy_bridge_app.listItems(RULE_NAME));
          },
        };

        const unsafeWindow = window;
        const GM_addStyle = addCss;
        const GM_getValue = Store.get;
        const GM_setValue = Store.set;
        const GM_deleteValue = Store.remove;
        const GM_listValues = Store.list;
        const GM_xmlhttpRequest = window.fbaHttpRequest;
        const GM_xmlHttpRequest = window.fbaHttpRequest;
        const GM_setClipboard = function(s){
          let r = $$$().lazyRule((s) => {
              copy(s)
          }, s);
          fy_bridge_app.parseLazyRule(r);
        };
        const GM_download = function(dataUrl,fileName){
           window.open(dataUrl, '_blank')
        }
        function generateUUID00() {
          return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            var r = Math.random() * 16 | 0,
                v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
          });
        }
        const GM_registerMenuCommand = function(name, fn, accessKey){
            let func = fn;
            if($$$.type(fn) !== "string") {
                //函数
                let fnName = "hk" + generateUUID00();
                window[fnName] = fn;
                func = fnName;
            }
            fy_bridge_app.registerMenuCommand(RULE_NAME, name, func);
            return func
        }
        GM_registerMenuCommand('管理插件', '');
        const GM_unregisterMenuCommand = function(name, fn, accessKey){
            fy_bridge_app.unregisterMenuCommand(RULE_NAME, name);
        }
        const GM_log = function(msg){
            fy_bridge_app.log(msg);
        }
        const GM_openInTab = function(url, options){
           window.open(url, '_blank')
        }
        const GM_notification = function(obj) {
            if(obj != null && obj.text != null){
               fy_bridge_app.toast(obj.text);
            }
        }
        const GM_getResourceText = function(resourceName, responseType) {
            let text = "";
            if(meta.resource) {
                for(let res of meta.resource) {
                    if(res.indexOf(resourceName + " ") == 0){
                        let url = res.substring(resourceName.length + 1);
                        log(url);
                        let mem = fy_bridge_app.getVar(url);
                        if (mem != "") {
                            text = mem;
                        } else {
                            text = fy_bridge_app.fetch(url, "{}");
                            fy_bridge_app.putVar(url, text);
                        }
                        return text;
                    }
                }
            }
            return text;
        }

        const GM_getResourceURL = function(resourceName) {
            return new Promise((resolve, reject) => {
              if(meta.resource) {
                  for(let res of meta.resource) {
                      if(res.indexOf(resourceName + " ") == 0){
                          let url = res.substring(resourceName.length + 1);
                          url = fy_bridge_app.getResourceUrl(url);
                          resolve(url);
                          return;
                      }
                  }
              }
              reject('not found ' + resourceName);
            });
        }
        const GM_cookie = function(func, details, callback) {
            return GM_cookie[func](details, callback);
        };
        const GM_cookies = function(func, details, callback) {
            return GM_cookies[func](details, callback);
        };
        GM_cookie.list = function(details, callback) {
            details = details || {};
            let url = details.url || location.href;
            let cookies = JSON.parse(fy_bridge_app.getCookieJSON(url));
            let arr = cookies.filter(it => (details.domain == null || details.domain == it.domain) && (details.name == null || details.name == it.name) && (details.path == null || details.path == it.path));
            callback(arr);
        };
        GM_cookies.list = function(details) {
            return new Promise((resolve, reject) => {
                  details = details || {};
                  let url = details.url || location.href;
                  let cookies = JSON.parse(fy_bridge_app.getCookieJSON(url));
                  resolve(cookies.filter(it => (details.domain == null || details.domain == it.domain) && (details.name == null || details.name == it.name) && (details.path == null || details.path == it.path)));
            });
        };
        GM_cookie.set = function(details, callback){
            let cookie = details.name + "=" + details.value;
            if(details.domain) {
                cookie += "; Domain=" + details.domain;
            }
            if(details.path) {
                cookie += "; Path=" + details.path;
            }
            if(details.expires) {
                cookie += "; Expires=" + details.expires;
            }
            if(details.expirationDate) {
                cookie += "; Expires=" + details.expirationDate;
            }
            if(details.secure) {
                cookie += "; Secure";
            }
            if(details.httpOnly) {
                cookie += "; HttpOnly";
            }
            fy_bridge_app.setCookie(details.url || location.href, cookie);
        };
        GM_cookie.delete = function(details, callback){
            fy_bridge_app.removeCookie(details.url || location.href, details.name);
            callback();
        };
        const GM_info = {
          scriptHandler: 'Tampermonkey',
          version: '4.0.25',
          script: {
              scriptHandler: 'Tampermonkey',
              version: meta.version && meta.version[0],
              name: NAME,
              namespace: meta.namespace && meta.namespace[0],
              description: meta.description && meta.description[0],
              runAt: meta.runAt && meta.runAt[0],
              matches: meta.match,
              includes: meta.include,
              excludes: meta.exclude,
              resources: (meta.resource || []).map(it => {
                let a = it.split(' ');
                return {
                  name: a[0],
                  url: a.length > 1 ? a[1] : a[0]
                }
              })
          },
          scriptMetaStr: metaString
        };
        const GM_addElement = function() {
          var parent_node = null;
          var tag_name = null;
          var attributes = null;

          if (arguments.length == 1) {
            tag_name = arguments[0];
          } else if (arguments.length == 2) {
            tag_name = arguments[0];
            attributes = arguments[1];
          } else if (arguments.length == 3) {
            parent_node = arguments[0];
            tag_name = arguments[1];
            attributes = arguments[2];
          } else {
            console.error("Invalid number of arguments for GM_addElement");
            return null;
          }
          var element = document.createElement(tag_name);
          if (attributes) {
            Object.keys(attributes).forEach(function(key) {
              if ("textContent" == key) {
                element.textContent = attributes[key];
              } else {
                element.setAttribute(key, attributes[key]);
              }
            });
          }
          if (parent_node) {
            parent_node.appendChild(element);
          } else {
              let t = tag_name.toLowerCase();
              if (document.head == null) {
                parent_node = document.body;
              } else if (t== "style" || t == "script" || t == "link" || t == "meta ") {
                parent_node = document.head;
              } else {
                parent_node = document.body;
              }
            parent_node.appendChild(element);
          }
          return element;
        };
        const GM_addValueChangeListener = function(key, fn){
            let fnName = "hk" + generateUUID00();
            let k = "hiker-ValueChangeListener";
            let base = window[k] || {};
            let map = base[key] || {};
            map[fnName] = fn;
            base[key] = map;
            window[k] = base;
            return fnName
        };
        const GM_removeValueChangeListener = function(listenerId){
            let k = "hiker-ValueChangeListener";
            let base = window[k] || {};
            for(let key in Object.keys(base)) {
                let map = base[key] || {};
                for(let fnName in Object.keys(map)) {
                      if(fnName == listenerId) {
                            delete map[fnName];
                      }
                }
                base[key] = map;
            }
            if(window[k]) {
                window[k] = base;
            }
        };
        const GM = {
          info: GM_info,
          notification: GM_notification,
          openInTab: GM_openInTab,
          log: GM_log,
          registerMenuCommand: GM_registerMenuCommand,
          unregisterMenuCommand: GM_unregisterMenuCommand,
          download: GM_download,
          setClipboard: GM_setClipboard,
          xmlhttpRequest: GM_xmlhttpRequest,
          xmlHttpRequest: GM_xmlHttpRequest,
          deleteValue: function (key) {
            return new Promise((resolve, reject) => {
                GM_deleteValue(key);
                resolve();
            })
          },
          setValue: function (key, value = null) {
            return new Promise((resolve, reject) => {
                GM_setValue(key, value);
                resolve();
            })
          },
          getValue: function (key, defaultValue = undefined) {
            return new Promise((resolve, reject) => {
                resolve(GM_getValue(key, defaultValue));
            })
          },
          listValues: function () {
            return new Promise((resolve, reject) => {
                resolve(GM_listValues());
            })
          },
          addStyle: GM_addStyle,
          addElement: GM_addElement,
          getResourceText: GM_getResourceText,
          getResourceURL: GM_getResourceURL,
          cookie: GM_cookie,
          cookies: GM_cookies,
          addValueChangeListener: GM_addValueChangeListener,
          removeValueChangeListener: GM_removeValueChangeListener
        };
        function eval000(str) {
            let ddd0002 = window.define;
            window.define = undefined;
            //解决baidu.com等网站无法注入的问题
            eval("(function() {\n;\n" + str + "\n})();");
            window.define = ddd0002;
        }
        function loadScript000(url, text) {
          let encodedUrl = encodeURI(url);
          let id = encodedUrl.replace(/[^a-zA-Z0-9]/g, '_');
          var existingScript = document.getElementById(id);
          if (existingScript) {
            return;
          }
          var script = document.createElement('script');
          script.text = "let ddd" + id + " = window.define; window.define = undefined;\ntry {\n" + text + "\n} catch(e){ console.log(e); }\nwindow.define = ddd" + id +";";
          script.setAttribute('id', id);
          document.head.appendChild(script);
        }
        //执行前置依赖
        if (RequireStack > 0) {
          for(let v of meta.require){
            if (fy_bridge_app.getVar(v) != "") {
                log('require from cache: ' + v);
                loadScript000(v, fy_bridge_app.getVar(v));
                RequireStack--;
            } else {
                log('require: ' + v);
                requestAsync(v, { FETCH_CACHE: "true" }, (key, result) => {
                  fy_bridge_app.putVar(key, result);
                });
                setInterval0(() => {
                  if (fy_bridge_app.getVar(v) != "") {
                    loadScript000(v, fy_bridge_app.getVar(v));
                    RequireStack--;
                    return true;
                  }
                }, 100);
            }
          }
        }
        //执行代码
        setInterval0(() => {
          if (RequireStack <= 0) {
            try {
                eval000(codeString);
            } catch(e) {
              console.log(e);
            }
            return true;
          }
        }, 100);
    });
})();