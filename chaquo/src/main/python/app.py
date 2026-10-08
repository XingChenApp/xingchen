import os
import requests
import json
import textwrap


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
    writeFile(path, textwrap.dedent(source).encode('utf-8'))
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
    result = _fix_pics(ru, ru.detailContent(str2json(array)))
    try:
        lst = result.get('list', [])
        if lst:
            vod = lst[0]
            pf = vod.get('vod_play_from', '')
            pu = vod.get('vod_play_url', '')
            if not pf or not pu:
                import datetime
                log_path = "/sdcard/Android/data/com.XingChen.tv/files/py_play.log"
                with open(log_path, 'a', encoding='utf-8') as f:
                    f.write(str(datetime.datetime.now()) + " DETAIL_EMPTY pf_len=" + str(len(pf)) + " pu_len=" + str(len(pu)) + " result=" + str(result)[:500] + "\n")
    except:
        pass
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
