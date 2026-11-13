package com.fongmi.android.tv.player.extractor;

import android.net.Uri;

import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.utils.Path;
import com.p2p.P2PClass;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;

public class JianPian implements Source.Extractor {

    private P2PClass p2p;
    private String path;

    @Override
    public boolean match(Uri uri) {
        return List.of("tvbox-xg", "jianpian", "ftp").contains(UrlUtil.scheme(uri));
    }

    private void init() {
        if (p2p == null) p2p = new P2PClass();
    }

    @Override
    public String fetch(String url) throws Exception {
        init();
        stop();
        // 旧任务没停住（stop 失败时 path 保留）时不许在它上面叠新任务、更不许
        // 把新任务的本地地址当可播地址交出去：直接走 fetch 既有抛错出口。
        if (path != null) throw new Exception("JianPian previous task did not stop: " + path);
        check();
        start(url);
        return "http://127.0.0.1:" + p2p.port + "/" + URLEncoder.encode(UrlUtil.path(path), "GBK");
    }

    private void check() {
        double cache = FileUtil.getDirectorySize(Path.jpa());
        double total = cache + FileUtil.getAvailableStorageSpace(Path.jpa());
        int percent = (int) (cache / total * 100);
        if (percent > 10) Path.clear(Path.jpa());
    }

    private void start(String url) throws Exception {
        path = URLDecoder.decode(url).split("\\|")[0];
        path = path.replace("jianpian://pathtype=url&path=", "");
        path = path.replace("tvbox-xg://", "").replace("tvbox-xg:", "");
        path = path.replace("xg://", "ftp://").replace("xgplay://", "ftp://");
        p2p.P2Pdoxstart(path.getBytes("GBK"));
    }

    @Override
    public void stop() {
        try {
            if (p2p == null || path == null) return;
            p2p.P2Pdoxpause(path.getBytes("GBK"));
            path = null;
        } catch (Exception e) {
            // 暂停失败不许清 path 装作已停：path 保留，下次 stop 可重试，
            // fetch 据 path 非空走抛错出口，不递死引擎地址。
            SpiderDebug.log("jianpian", "pause failed path=%s error=%s", path, e.getMessage());
        }
    }

    @Override
    public void exit() {
    }
}
