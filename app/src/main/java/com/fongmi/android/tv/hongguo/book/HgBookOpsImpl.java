package com.fongmi.android.tv.hongguo.book;

import com.fongmi.android.tv.hongguo.crypto.SpadeKey;
import com.fongmi.android.tv.hongguo.proxy.HgStreamRegistry;
import com.fongmi.android.tv.hongguo.proxy.HgStreamSpec;
import com.fongmi.android.tv.legado.HgBookBridge;
import com.fongmi.android.tv.legado.HgBookOps;
import com.fongmi.android.tv.legado.LegadoException;
import com.fongmi.android.tv.legado.LegadoPipeline;
import com.fongmi.android.tv.server.Server;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 红果书源执行实现（红果包内）：把 HgBookClient 的书数据映射成阅读管线四段。
 * 小说正文走加密链（HgNovelCrypto 在客户端内处理）；听书音频是 CENC 加密，
 * encryption_key 与短剧 spade_a 同一种置乱编码（dig-audio-cenc-key-20261028
 * 实测推翻「另一套」旧判），SpadeKey 推出内容密钥后登记进短剧同款本地解密
 * 代理（HgStreamRegistry），正文段回本地代理地址给听书播放器。
 */
public final class HgBookOpsImpl implements HgBookOps {

    private final HgBookClient client = new HgBookClient();

    @Override
    public List<LegadoPipeline.Book> search(boolean audio, String key, int page) {
        try {
            // A-22：搜索签是单池、无第 21 条之后（探针实证，见 HgBookClient.search）
            // ——整池只在第 1 页交付；第 2 页起直接回空页让管线收口，
            // 不再按 (page-1)×20 发无效 offset 请求把同一池反复当新页。
            if (page > 1) return new ArrayList<>();
            List<HgBookClient.Entry> entries = client.search(audio, key).entries;
            List<LegadoPipeline.Book> out = new ArrayList<>();
            for (HgBookClient.Entry e : entries) {
                LegadoPipeline.Book b = new LegadoPipeline.Book();
                b.name = e.title;
                b.author = e.author;
                b.coverUrl = e.coverUrl;
                b.bookUrl = HgBookBridge.bookUrl(e.bookId);
                out.add(b);
            }
            return out;
        } catch (LegadoException e) {
            throw e;
        } catch (Exception e) {
            throw new LegadoException("红果搜索失败：" + msg(e));
        }
    }

    @Override
    public LegadoPipeline.BookInfo bookInfo(boolean audio, String bookUrl) {
        try {
            String bookId = HgBookBridge.bookIdOf(bookUrl);
            HgBookClient.Entry e = client.detail(bookId);
            if (e.title.isEmpty()) {
                HgBookClient.Entry fromToc = client.tocBookInfo(bookId);
                if (fromToc != null) e = fromToc;
            }
            LegadoPipeline.BookInfo info = new LegadoPipeline.BookInfo();
            info.bookUrl = bookUrl;
            info.name = e.title;
            info.author = e.author;
            info.coverUrl = e.coverUrl;
            info.intro = e.intro;
            info.tocUrl = bookUrl;
            return info;
        } catch (LegadoException e) {
            throw e;
        } catch (Exception e) {
            throw new LegadoException("红果书籍详情失败：" + msg(e));
        }
    }

    @Override
    public List<LegadoPipeline.TocChapter> toc(boolean audio, String tocUrl) {
        try {
            String bookId = HgBookBridge.bookIdOf(tocUrl);
            List<HgBookClient.Chapter> chapters = client.toc(bookId);
            List<LegadoPipeline.TocChapter> out = new ArrayList<>();
            for (HgBookClient.Chapter c : chapters) {
                LegadoPipeline.TocChapter t = new LegadoPipeline.TocChapter();
                t.name = c.title;
                t.url = HgBookBridge.chapterUrl(bookId, c.itemId);
                out.add(t);
            }
            return out;
        } catch (LegadoException e) {
            throw e;
        } catch (Exception e) {
            throw new LegadoException("红果目录失败：" + msg(e));
        }
    }

    @Override
    public LegadoPipeline.Content content(boolean audio, String chapterUrl) {
        if (audio) return audioContent(chapterUrl);
        try {
            String bookId = HgBookBridge.bookIdOf(chapterUrl);
            String itemId = HgBookBridge.itemIdOf(chapterUrl);
            String[] r = client.chapterText(bookId, itemId);
            LegadoPipeline.Content c = new LegadoPipeline.Content();
            c.title = r[0];
            c.text = r[1];
            return c;
        } catch (LegadoException e) {
            throw e;
        } catch (Exception e) {
            throw new LegadoException("红果正文失败：" + msg(e));
        }
    }

    /**
     * 听书朗读兜底：三道判据全过才给正文（2026-10-28 本机实拉定版，样本
     * TTS 书 7596021152615517209／真人书 7632179112899005465）——
     * ① 本章 batch_play 确认双地址皆空（HgBookClient.isNoUrlFailure；
     *    取得到播放信息＝有地址，不走这路）；
     * ② 本书是 TTS 书：detail 的 book_type=0（小说本体、听书靠合成音；
     *    真人书 book_type=1，且真人书同样有正文，所以绝不能拿「正文取得到」
     *    当触发——必须先认书型）；字段缺失/判定不了按不是 TTS 书处理；
     * ③ 小说正文链（reader/full＋registerkey）取本章正文，样本第 1/2 章
     *    实拉 2230/2391 字、81/89 段，形状与小说章无异。
     * 是 TTS 书＋确认没地址、但正文也取不到 → 抛错让页面把话说清。
     */
    @Override
    public LegadoPipeline.Content ttsFallbackText(String chapterUrl) {
        String bookId = HgBookBridge.bookIdOf(chapterUrl);
        String itemId = HgBookBridge.itemIdOf(chapterUrl);
        try {
            client.audioPlayInfo(bookId, itemId);
            return null;
        } catch (Exception e) {
            if (!HgBookClient.isNoUrlFailure(e.getMessage())) return null;
        }
        try {
            if (client.bookType(bookId) != 0) return null;
        } catch (Exception e) {
            return null;
        }
        try {
            String[] r = client.chapterText(bookId, itemId);
            LegadoPipeline.Content c = new LegadoPipeline.Content();
            c.title = r[0];
            c.text = r[1];
            return c;
        } catch (Exception e) {
            throw new LegadoException("这一章正文也没取到：" + msg(e));
        }
    }

    /**
     * 听书正文段＝取本章播放信息 → 推出内容密钥 → 登记本地解密代理 → 回代理地址。
     * 钥匙随章变，每章现取现推；未加密的章（无钥匙材料）代理直喂。
     */
    private LegadoPipeline.Content audioContent(String chapterUrl) {
        try {
            String bookId = HgBookBridge.bookIdOf(chapterUrl);
            String itemId = HgBookBridge.itemIdOf(chapterUrl);
            JsonObject info = client.audioPlayInfo(bookId, itemId);
            String url = strOf(info, "main_url");
            if (url.isEmpty()) url = strOf(info, "backup_url");
            if (url.isEmpty()) throw new LegadoException("红果听书这一章没给播放地址");
            String encKey = strOf(info, "encryption_key");
            String dekHex = null;
            if (!encKey.isEmpty()) {
                dekHex = SpadeKey.deriveKeyHex(encKey);
            } else if (truthy(info, "is_encrypt")) {
                throw new LegadoException("红果听书这一章标了加密却没给钥匙材料");
            }
            HgStreamSpec spec = new HgStreamSpec(url, dekHex, 0, null, "audio:" + itemId);
            String token = HgStreamRegistry.get().register(spec);
            if (token == null) throw new LegadoException("红果听书播放登记失败");
            LegadoPipeline.Content c = new LegadoPipeline.Content();
            c.text = Server.get().getAddress(true)
                    + HgStreamRegistry.get().playbackPath(token);
            return c;
        } catch (LegadoException e) {
            throw e;
        } catch (Exception e) {
            throw new LegadoException("红果听书取播放地址失败：" + msg(e));
        }
    }

    private static String strOf(JsonObject o, String key) {
        try {
            if (o == null || !o.has(key) || o.get(key).isJsonNull()) return "";
            return o.get(key).getAsString().trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean truthy(JsonObject o, String key) {
        try {
            if (o == null || !o.has(key) || o.get(key).isJsonNull()) return false;
            com.google.gson.JsonPrimitive p = o.get(key).getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsInt() != 0;
            String s = p.getAsString();
            return "true".equalsIgnoreCase(s) || "1".equals(s);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String msg(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? "网络不通或接口异常" : m;
    }
}
