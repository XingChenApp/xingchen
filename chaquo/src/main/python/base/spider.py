import re
import os
import sys
import json
import time
import hashlib
import threading
import requests
from lxml import etree
from com.github.catvod import Proxy
from com.chaquo.python import Python
from abc import abstractmethod, ABCMeta
from importlib.machinery import SourceFileLoader


# 已加载依赖模块表：键 (模块名, 文件内容摘要) -> 模块对象。与 app.py 的
# spider() 同口径：同一份文件在一个进程内只执行一次，类身份不被反复加载
# 冲掉（桥的类关联是进程全局按最后定义覆写的，见 app.py 注释）。依赖文件
# 被 download() 更新后摘要变化，照旧重新加载新版。
_loaded_dep_modules = {}
_dep_load_lock = threading.Lock()


def _load_dep_module(name, path):
    with open(path, 'rb') as f:
        digest = hashlib.sha256(f.read()).hexdigest()
    key = (name, digest)
    module = _loaded_dep_modules.get(key)
    if module is None:
        with _dep_load_lock:
            module = _loaded_dep_modules.get(key)
            if module is None:
                # load_module() 对 sys.modules 里已有的同名模块是原地重跑
                # （命名空间被新代码覆盖、类对象当场换掉）。先摘掉旧登记，
                # 让本次加载生成全新模块对象：新版进 sys.modules[name] 供
                # 按名 import，旧对象原样留给已持有它的调用方，不被冲掉。
                sys.modules.pop(name, None)
                module = SourceFileLoader(name, path).load_module()
                _loaded_dep_modules[key] = module
    return module


class Spider(metaclass=ABCMeta):

    def __init__(self):
            self.extend = ''

    def init(self, extend=""):
        pass

    def homeContent(self, filter):
        pass

    def homeVideoContent(self):
        pass

    def categoryContent(self, tid, pg, filter, extend):
        pass

    def detailContent(self, ids):
        pass

    def searchContent(self, key, quick, pg="1"):
        pass

    def playerContent(self, flag, id, vipFlags):
        pass

    def liveContent(self, url):
        pass

    def localProxy(self, param):
        pass

    def isVideoFormat(self, url):
        pass

    def manualVideoCheck(self):
        pass

    def action(self, action):
        pass

    def destroy(self):
        pass

    def getName(self):
        pass

    def getDependence(self):
        return []

    def loadSpider(self, name):
        return self.loadModule(name).Spider()

    def loadModule(self, name):
        cache_dir = Python.getPlatform().getApplication().getCacheDir().getAbsolutePath()
        path = os.path.join(os.path.join(cache_dir, 'py'),  f'{name}.py')
        return _load_dep_module(name, path)

    def regStr(self, reg, src, group=1):
        m = re.search(reg, src)
        src = ''
        if m:
            src = m.group(group)
        return src

    def removeHtmlTags(self, src):
        clean = re.compile('<.*?>')
        return re.sub(clean, '', src)

    def cleanText(self, src):
        clean = re.sub('[\U0001F600-\U0001F64F\U0001F300-\U0001F5FF\U0001F680-\U0001F6FF\U0001F1E0-\U0001F1FF]', '', src)
        return clean

    def _gateProxies(self):
        try:
            from com.github.catvod.net import SourceGate
            port = SourceGate.proxyPortForCurrent()
            if port:
                u = 'http://127.0.0.1:%d' % int(port)
                return {'http': u, 'https': u}
        except Exception as e:
            print('gate proxies failed: %s' % e)
        return None

    def fetch(self, url, params=None, cookies=None, headers=None, timeout=5, verify=True, stream=False, allow_redirects = True):
        rsp = requests.get(url, params=params, cookies=cookies, headers=headers, timeout=timeout, verify=verify, stream=stream, allow_redirects=allow_redirects, proxies=self._gateProxies())
        rsp.encoding = 'utf-8'
        return rsp

    def post(self, url, params=None, data=None, json=None, cookies=None, headers=None, timeout=5, verify=True, stream=False, allow_redirects = True):
        rsp = requests.post(url, params=params, data=data, json=json, cookies=cookies, headers=headers, timeout=timeout, verify=verify, stream=stream, allow_redirects=allow_redirects, proxies=self._gateProxies())
        rsp.encoding = 'utf-8'
        return rsp

    def html(self, content):
        return etree.HTML(content)

    def str2json(str):
        return json.loads(str)

    def json2str(str):
        return json.dumps(str, ensure_ascii=False)
    
    def getProxyUrl(self, local=True):
        return f'{Proxy.getUrl(local)}?do=py'

    def log(self, msg):
        if isinstance(msg, dict) or isinstance(msg, list):
            print(json.dumps(msg, ensure_ascii=False))
        else:
            print(f'{msg}')

    def getCache(self, key):
        value = self.fetch(f'http://127.0.0.1:{Proxy.getPort()}/cache?do=get&key={key}', timeout=5).text
        if len(value) > 0:
            if value.startswith('{') and value.endswith('}') or value.startswith('[') and value.endswith(']'):
                value = json.loads(value)
                if type(value) == dict:
                    if not 'expiresAt' in value or value['expiresAt'] >= int(time.time()):
                        return value
                    else:
                        self.delCache(key)
                        return None
            return value
        else:
            return None

    def setCache(self, key, value):
        if type(value) in [int, float]:
            value = str(value)
        if len(value) > 0:
            if type(value) == dict or type(value) == list:
                value = json.dumps(value, ensure_ascii=False)
        r = self.post(f'http://127.0.0.1:{Proxy.getPort()}/cache?do=set&key={key}', data={"value": value}, timeout=5)
        return 'succeed' if r.status_code == 200 else 'failed'

    def delCache(self, key):
        r = self.fetch(f'http://127.0.0.1:{Proxy.getPort()}/cache?do=del&key={key}', timeout=5)
        return 'succeed' if r.status_code == 200 else 'failed'
