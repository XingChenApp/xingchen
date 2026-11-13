import hashlib
import itertools
import os
import requests
import json
import textwrap
from urllib.parse import urljoin

try:
    from runner import Runner as _Runner
except Exception:
    _Runner = None

def _install_dns_bridge():
    try:
        import socket as _sock
        from com.github.catvod.net import OkHttp as _java_okhttp
        _orig = _sock.getaddrinfo

        def _patched(host, port, family=0, type=0, proto=0, flags=0):
            if isinstance(host, str):
                try:
                    result = []
                    for addr in _java_okhttp.dns().lookup(host):
                        ip = addr.getHostAddress()
                        af = _sock.AF_INET6 if ':' in ip else _sock.AF_INET
                        if family and family != af:
                            continue
                        st = type if type else _sock.SOCK_STREAM
                        sa = (ip, port, 0, 0) if af == _sock.AF_INET6 else (ip, port)
                        result.append((af, st, proto, '', sa))
                    if result:
                        return result
                except Exception:
                    pass
            return _orig(host, port, family, type, proto, flags)

        _sock.getaddrinfo = _patched
    except Exception as e:
        print('dns bridge failed: %s' % e)


_install_dns_bridge()


_file_hashes = {}
_module_seq = itertools.count(1)


# 首页加载代次（修 3）：Java 侧每次作废旧首页加载时代次 +1 并经 set_home_generation
# 推送到这里。homeContent / homeVideoContent 入口用调用方携带的代次校验，
# 过期直接返回空 JSON，不再进脚本。gen 为 None 或负数表示不校验（兼容老调用）。
_home_generation = 0
_EMPTY_HOME_JSON = '{"list":[]}'


def set_home_generation(gen):
    global _home_generation
    try:
        gen = int(gen)
    except Exception:
        return
    if gen > _home_generation:
        _home_generation = gen


def _home_gen_ok(gen):
    try:
        return gen is None or int(gen) < 0 or int(gen) >= _home_generation
    except Exception:
        return True


def _resolve_pic(spider_obj, pic):
    if not pic or not isinstance(pic, str):
        return pic
    if pic.startswith('http://') or pic.startswith('https://'):
        return pic
    if pic.startswith('//'):
        return 'https:' + pic
    if pic.startswith('/'):
        base = ''
        for attr in ('site', 'host', 'base_url', 'baseUrl', 'domain'):
            try:
                v = getattr(spider_obj, attr, '')
                if v and isinstance(v, str) and v.startswith('http'):
                    base = v.rstrip('/')
                    break
            except Exception:
                pass
        if base:
            return base + pic
    return pic


def _fix_pics(spider_obj, result):
    try:
        if isinstance(result, dict):
            items = result.get('list')
            if isinstance(items, list):
                for it in items:
                    if isinstance(it, dict) and 'vod_pic' in it:
                        it['vod_pic'] = _resolve_pic(spider_obj, it.get('vod_pic', ''))
    except Exception:
        pass
    return result


def spider(cache, source, file_name=None):
    name = file_name or "spider.py"
    if len(name) > 100 or "\n" in name or "class Spider" in name:
        name = "spider.py"
    if not name.endswith('.py'):
        name = name + '.py'
    path = cache + '/' + name
    data = textwrap.dedent(source).encode('utf-8')
    digest = hashlib.sha256(data).hexdigest()
    if _file_hashes.get(name) != digest:
        writeFile(path, data)
        _file_hashes[name] = digest
    mod_name = name.split('.')[0] + '_' + str(next(_module_seq))
    from importlib.machinery import SourceFileLoader
    raw = SourceFileLoader(mod_name, path).load_module().Spider()
    return _Runner(raw) if _Runner is not None else raw


def download(path, api):
    if api.startswith('http'):
        length = None
        try:
            head = requests.head(api, allow_redirects=True, timeout=10)
            ok = head is not None and 200 <= head.status_code < 300
            raw = head.headers.get('Content-Length') if ok else None
            length = int(raw) if raw is not None else None
        except Exception:
            length = None
        if length is not None and os.path.exists(path) and os.path.getsize(path) == length:
            return 'skipped-length'
        try:
            data = redirect(api).content
        except Exception:
            if os.path.exists(path) and os.path.getsize(path) > 0:
                return 'cached-offline'
            raise
    else:
        data = textwrap.dedent(api).encode('utf-8')
    if os.path.exists(path):
        try:
            with open(path, 'rb') as f:
                old = f.read()
        except Exception:
            old = None
        if old == data:
            return 'skipped-hash'
    tmp = path + '.tmp'
    with open(tmp, 'wb') as f:
        f.write(data)
    os.replace(tmp, path)
    return 'updated'


def writeFile(path, content):
    with open(path, 'wb') as f:
        f.write(content)


def redirect(url):
    rsp = None
    for _ in range(10):
        rsp = requests.get(url, allow_redirects=False, timeout=30)
        location = rsp.headers.get('Location')
        if not location:
            return rsp
        url = urljoin(url, location)
    raise Exception('too many redirects: ' + url)


def str2json(content):
    if isinstance(content, str):
        return json.loads(content)
    return content


def getDependence(ru):
    fn = getattr(ru, "getDependence", None)
    return fn() if callable(fn) else []


def getName(ru):
    fn = getattr(ru, "getName", None)
    return fn() if callable(fn) else None


class _ExtStr(str):
    # ext 单次执行容错：本体是 str（字符串口径蜘蛛的 json.loads/切片/拼接
    # 语义全不变），同时把 ext 解析出的 dict 挂上 .get 与字符串键下标，
    # 让按 dict 口径写 init 的蜘蛛一趟跑通。旧做法是先按 str 调 init、
    # 撞 AttributeError 后把 ext 解析成 dict 整个重跑 init——第一趟已执行
    # 的副作用（登录/请求/写缓存）会重复，且判定只看异常文案，蜘蛛内部
    # 无关的字符串误用也会误触发重跑。这里改为执行前一次成型、只调一遍：
    # 解析不出 dict 就原样传 str，该失败的明确失败，不许半成功。
    def __new__(cls, raw, parsed):
        obj = str.__new__(cls, raw)
        obj._parsed = parsed
        return obj

    def get(self, key, default=None):
        if isinstance(self._parsed, dict):
            return self._parsed.get(key, default)
        return default

    def __getitem__(self, key):
        if isinstance(key, str) and isinstance(self._parsed, dict):
            return self._parsed[key]
        return str.__getitem__(self, key)

    def keys(self):
        if isinstance(self._parsed, dict):
            return self._parsed.keys()
        return []

    def items(self):
        if isinstance(self._parsed, dict):
            return self._parsed.items()
        return []

    def values(self):
        if isinstance(self._parsed, dict):
            return self._parsed.values()
        return []


def _wrap_extend(extend):
    if isinstance(extend, str) and extend.strip():
        try:
            parsed = json.loads(extend)
        except Exception:
            parsed = None
        if isinstance(parsed, dict):
            return _ExtStr(extend, parsed)
    return extend


def init(ru, extend):
    ru.init(_wrap_extend(extend))


def homeContent(ru, filter, gen=None):
    # 首页代次令牌（修 3）：Java 侧 SiteApi 批次认领（claimHomeLoadBatch）顶替旧
    # 加载、配置切换作废（invalidateHomeLoads）时把代次 +1 并推送到
    # _home_generation。调用方携带的代次若已过期，直接返回空 JSON，
    # 不再进脚本——防止被取消/排队中的旧任务在取消后仍打站点（僵尸加载止血）。
    # 只限首页两路；播放/详情/搜索/分类绝不受首页代次影响。
    if not _home_gen_ok(gen):
        return _EMPTY_HOME_JSON
    result = _fix_pics(ru, ru.homeContent(filter))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def homeVideoContent(ru, gen=None):
    if not _home_gen_ok(gen):
        return _EMPTY_HOME_JSON
    result = _fix_pics(ru, ru.homeVideoContent())
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def categoryContent(ru, tid, pg, filter, extend):
    result = _fix_pics(ru, ru.categoryContent(tid, pg, filter, str2json(extend)))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def detailContent(ru, array):
    result = _fix_pics(ru, ru.detailContent(str2json(array)))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def searchContent(ru, key, quick, pg="1"):
    result = _fix_pics(ru, ru.searchContent(key, quick, pg))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def playerContent(ru, flag, id, vipFlags):
    result = ru.playerContent(flag, id, str2json(vipFlags))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def liveContent(ru, url):
    result = ru.liveContent(url)
    return result


def localProxy(ru, param):
    result = ru.localProxy(str2json(param))
    return result


def manualVideoCheck(ru):
    fn = getattr(ru, "manualVideoCheck", None)
    return fn() if callable(fn) else False


def isVideoFormat(ru, url):
    fn = getattr(ru, "isVideoFormat", None)
    return fn(url) if callable(fn) else False


def action(ru, action):
    result = ru.action(action)
    # 动作协议（动作.py 那套）：spider 的 action() 会直接回 JSON 字符串
    # （如 '{"msg": ...}'），字符串已是成串的 JSON，必须原样直通；
    # 再 dumps 一次会双层编码，宿主 Gson 解不出、提示全灭。
    if isinstance(result, str):
        return result
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def destroy(ru):
    fn = getattr(ru, "destroy", None)
    if callable(fn):
        fn()


def run():
    pass


if __name__ == '__main__':
    run()
