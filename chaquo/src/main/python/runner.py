class _ExtStr(str):
    # 与 app.py 同一份实现：str 本体保持字符串语义，同时挂解析出的
    # dict 视图（.get/字符串键下标），让 dict 口径蜘蛛一趟跑通 init。
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
            import json
            parsed = json.loads(extend)
        except Exception:
            parsed = None
        if isinstance(parsed, dict):
            return _ExtStr(extend, parsed)
    return extend


class Runner():
    def __init__(self, spider):
        self.spider = spider

    def __getattr__(self, name):
        spider = self.__dict__.get("spider")
        if spider is None:
            raise AttributeError(name)
        return getattr(spider, name)

    def __setattr__(self, name, value):
        if name == "spider" or name.startswith("_"):
            object.__setattr__(self, name, value)
        else:
            setattr(self.__dict__["spider"], name, value)

    def getDependence(self):
        fn = getattr(self.spider, "getDependence", None)
        return fn() if callable(fn) else []

    def getName(self):
        fn = getattr(self.spider, "getName", None)
        return fn() if callable(fn) else ""

    def init(self, extend=""):
        # 与 app.py 同口径：ext 执行前一次成型（str 本体＋dict 视图），
        # init 只调一遍——旧做法撞 AttributeError 后整体重跑会重复副作用。
        self.spider.init(_wrap_extend(extend))

    def homeContent(self, filter):
        return self.spider.homeContent(filter)

    def homeVideoContent(self):
        fn = getattr(self.spider, "homeVideoContent", None)
        return fn() if callable(fn) else {"list": []}

    def categoryContent(self, tid, pg, filter, extend):
        return self.spider.categoryContent(tid, pg, filter, extend)

    def detailContent(self, ids):
        return self.spider.detailContent(ids)

    def searchContent(self, key, quick, pg="1"):
        return self.spider.searchContent(key, quick, pg)

    def playerContent(self, flag, id, vipFlags):
        return self.spider.playerContent(flag, id, vipFlags)

    def liveContent(self, url):
        fn = getattr(self.spider, "liveContent", None)
        return fn(url) if callable(fn) else ""

    def localProxy(self, param):
        fn = getattr(self.spider, "localProxy", None)
        return fn(param) if callable(fn) else None

    def isVideoFormat(self, url):
        fn = getattr(self.spider, "isVideoFormat", None)
        return fn(url) if callable(fn) else False

    def manualVideoCheck(self):
        fn = getattr(self.spider, "manualVideoCheck", None)
        return fn() if callable(fn) else False

    def action(self, action):
        fn = getattr(self.spider, "action", None)
        return fn(action) if callable(fn) else {}

    def destroy(self):
        fn = getattr(self.spider, "destroy", None)
        if callable(fn):
            fn()

    def refresh_home_after_login(self):
        gen = getattr(self, "_login_generation", 0) + 1
        self._login_generation = gen
        fn = getattr(self.spider, "refresh_after_login", None)
        if callable(fn):
            fn()
        return gen
