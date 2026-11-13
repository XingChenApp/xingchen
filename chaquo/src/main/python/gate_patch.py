import threading

_lock = threading.Lock()
_done = [False]


def _proxies():
    try:
        from com.github.catvod.net import SourceGate
        port = SourceGate.proxyPortForCurrent()
        if port:
            u = "http://127.0.0.1:%d" % int(port)
            return {"http": u, "https": u}
    except Exception:
        pass
    return None


def _patch_requests():
    try:
        import requests
        orig = requests.sessions.Session.request
        if getattr(orig, "_gate_patched", False):
            return
        def request(self, method, url, **kw):
            if not kw.get("proxies"):
                p = _proxies()
                if p:
                    kw["proxies"] = p
            return orig(self, method, url, **kw)
        request._gate_patched = True
        requests.sessions.Session.request = request
    except Exception:
        pass


def _patch_urllib():
    try:
        import urllib.request
        orig = urllib.request.urlopen
        if getattr(orig, "_gate_patched", False):
            return
        def urlopen(url, data=None, *a, **kw):
            p = _proxies()
            if p:
                timeout = kw.get("timeout", a[0] if a else 30)
                opener = urllib.request.build_opener(urllib.request.ProxyHandler(p))
                return opener.open(url, data=data, timeout=timeout)
            return orig(url, data, *a, **kw)
        urlopen._gate_patched = True
        urllib.request.urlopen = urlopen
    except Exception:
        pass


def install():
    with _lock:
        if _done[0]:
            return
        _done[0] = True
    _patch_requests()
    _patch_urllib()
