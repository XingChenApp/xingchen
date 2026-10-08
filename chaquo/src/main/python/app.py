import hashlib
import os
import requests
import json
import textwrap

for _k in ('http_proxy', 'https_proxy', 'HTTP_PROXY', 'HTTPS_PROXY', 'all_proxy', 'ALL_PROXY'):
    os.environ.pop(_k, None)


_file_hashes = {}


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
    mod_name = name.split('.')[0]
    from importlib.machinery import SourceFileLoader
    return SourceFileLoader(mod_name, path).load_module().Spider()


def download(path, api):
    if api.startswith('http'):
        writeFile(path, redirect(api).content)
    else:
        writeFile(path, textwrap.dedent(api).encode('utf-8'))


def writeFile(path, content):
    with open(path, 'wb') as f:
        f.write(content)


def redirect(url):
    rsp = requests.get(url, allow_redirects=False, verify=False)
    if 'Location' in rsp.headers:
        return redirect(rsp.headers['Location'])
    else:
        return rsp


def str2json(content):
    return json.loads(content)


def getDependence(ru):
    result = ru.getDependence()
    return result


def getName(ru):
    result = ru.getName()
    return result


def init(ru, extend):
    ru.init(extend)


def homeContent(ru, filter):
    result = _fix_pics(ru, ru.homeContent(filter))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def homeVideoContent(ru):
    result = _fix_pics(ru, ru.homeVideoContent())
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def categoryContent(ru, tid, pg, filter, extend):
    result = _fix_pics(ru, ru.categoryContent(tid, pg, filter, str2json(extend)))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def detailContent(ru, array):
    # TEMP-DIAG-REMOVE-AFTER
    import traceback as _tb
    import datetime as _dt
    _log_path = '/sdcard/xingchen_py_error.log'
    try:
        from com.chaquo.python import Python as _qp
        _qctx = _qp.getPlatform().getApplication().getApplicationContext()
        _qdir = str(_qctx.getExternalFilesDir(None))
        if _qdir and _qdir != 'None':
            _log_path = _qdir + '/xingchen_py_error.log'
    except Exception:
        pass
    def _dlog(_msg):
        try:
            with open(_log_path, 'a', encoding='utf-8') as _f:
                _f.write(_msg + '\n')
        except Exception:
            pass
    try:
        _r = _fix_pics(ru, ru.detailContent(str2json(array)))
        _jo = json.dumps(_r, ensure_ascii=False)
        _flag = 'EMPTY_RESULT' if len(_jo) < 50 else ''
        _dlog('[%s] detailContent OK ids=%s len=%d %s' % (_dt.datetime.now().isoformat(), array[:200], len(_jo), _flag))
        return _jo
    except Exception:
        _sp = ''
        try:
            _sp = ru.getName()
        except Exception:
            pass
        _dlog('[%s] detailContent EXCEPTION spider=%s ids=%s\n%s' % (_dt.datetime.now().isoformat(), _sp, array[:200], _tb.format_exc()))
        raise
    # TEMP-DIAG-REMOVE-AFTER-END


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


def action(ru, action):
    result = ru.action(action)
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def destroy(ru):
    ru.destroy()


def run():
    pass


if __name__ == '__main__':
    run()
