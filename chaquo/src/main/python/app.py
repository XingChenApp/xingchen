import os
import requests
import json


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


def spider(cache, api, file_name=None):
    name = file_name or os.path.basename(api)
    path = cache + '/' + name
    download(path, api)
    name = name.split('.')[0]
    from importlib.machinery import SourceFileLoader
    return SourceFileLoader(name, path).load_module().Spider()


def download(path, api):
    if api.startswith('http'):
        writeFile(path, redirect(api).content)
    else:
        with open(api, 'r', encoding='utf-8') as f:
            writeFile(path, f.read().encode('utf-8'))


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
