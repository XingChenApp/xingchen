package com.fongmi.android.tv.api.hk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 列表/搜索交付记账——8.83 JSEngine 的一次性回调＋PRE/AFTER phase 记账同构
 * （callbackHomeResult JSEngine.smali:918-1168、setPreResult :24850、
 * setHomeResult :24534、callbackSearchResult :1170）。
 *
 * 官形：每个 callbackKey 在回调表里只登记一个回调；setPreResult 交付打
 * phase=ARTICLE_LIST_PRE、把 key 记进 preCalled、**不摘回调**；正式交付时
 * key 在 preCalled 里则摘记并打 phase=ARTICLE_LIST_AFTER、否则 phase=null，
 * 交付后**摘除回调**——同 key 的第二次正式交付无回调可达，在引擎侧被丢，
 * 结构上不可能重复交付。咱家旧形是共享 results 累加器＋preResultActive 标志，
 * PRE/AFTER 被压成一表、迟到异步交付还能往下一发的表里写（账 G51 三环口子）。
 *
 * 本类只管记账与分段，不做条目转换、不碰宿主呈现：parseList/parseSearch 入口
 * 按本发 CALLBACK_KEY 登记 Sink，setResult 一族经 deliver/deliverPre 交付，
 * 入口在求值结束后摘 Sink 并按交付序合成返回表（PRE 段在前、AFTER 段追加，
 * 与官消费端 ArticleListFragment.onSuccess 的分段口径一致）。
 *
 * 求值中途回页通道（S3 补面，账 juyue-subrule-full-gap S3 钉根）：官
 * callbackHomeResult 在求值中途就调注册回调 onSuccess(phase, list)——PRE
 * 段当场回页上屏，随后脚本的 deleteItemByCls 才有活表可删，正式交付再以
 * AFTER 段追加。咱家旧形只有记账、求值期间没有回页通道，占位条目躺在汇点
 * 里、删除事件到页面时无对象可删（占位永不撤）。故 Sink 可挂一只分段监听：
 * deliverPre 记账后、deliver 打出 AFTER 段时，同步（求值线程）把该段递给
 * 监听——与官回调同位同时机；无监听时行为与旧形逐字一致（合成返回表）。
 */
public final class HkFindDelivery {

    public static final String PHASE_PRE = "ARTICLE_LIST_PRE";
    public static final String PHASE_AFTER = "ARTICLE_LIST_AFTER";

    /**
     * 分段交付监听（官 callbackHomeResult 注册回调的 onSuccess 同位）：PRE 段
     * 在求值中途触发、AFTER 段在正式交付触发，均在求值线程同步回调；载荷为
     * 该段条目的副本。phase=null 的单段正式交付不经此口（调用方走合成返回表）。
     */
    public interface SegmentListener {
        void onSegment(String phase, List<HkItem> items);
    }

    /** 一次交付：phase 标记（PRE/AFTER/null）＋本段条目。 */
    public static final class Delivery {
        public final String phase;
        public final List<HkItem> items;

        Delivery(String phase, List<HkItem> items) {
            this.phase = phase;
            this.items = items;
        }
    }

    /** 一个 callbackKey 的交付汇点：登记后可收 PRE 多记，最终正式交付一记即关。 */
    public static final class Sink {
        private final String key;
        private final String type;
        private final List<Delivery> deliveries = new ArrayList<>();
        private final List<HkItem> items = new ArrayList<>();
        private boolean closed;
        private volatile SegmentListener segmentListener;

        Sink(String key, String type) {
            this.key = key;
            this.type = type;
        }

        /** 挂分段监听（parse 入口在本发登记后挂、随汇点摘除失效）；null 即无通道。 */
        public void setSegmentListener(SegmentListener listener) {
            this.segmentListener = listener;
        }

        /** 把一段交付同步递给监听（求值线程）；无监听/空载荷（记账形）不递。 */
        void notifySegment(String phase, List<HkItem> payload) {
            SegmentListener listener = segmentListener;
            if (listener == null || payload == null) return;
            listener.onSegment(phase, new ArrayList<>(payload));
        }

        public String key() {
            return key;
        }

        /** 本发类型（"home"/"search"——官包装层按本发 MY_TYPE 覆写 typeKey 的同源值）。 */
        public String type() {
            return type;
        }

        /** 本发全部交付（按交付序，含 phase 标记）。 */
        public synchronized List<Delivery> deliveries() {
            return new ArrayList<>(deliveries);
        }

        /** 已交付条目按交付序合成的活表（条目修改面在求值期就地改这一张；外部成表时自带同步）。 */
        public List<HkItem> items() {
            return items;
        }

        public synchronized boolean isClosed() {
            return closed;
        }

        synchronized void add(String phase, List<HkItem> payload) {
            List<HkItem> seg = payload == null ? new ArrayList<>() : new ArrayList<>(payload);
            deliveries.add(new Delivery(phase, seg));
            items.addAll(seg);
        }

        synchronized void close() {
            closed = true;
        }
    }

    private final Map<String, Sink> sinks = new ConcurrentHashMap<>();
    private final Set<String> preCalled = ConcurrentHashMap.newKeySet();

    /** parse 入口登记本发汇点（官：parseHome 每次求值 genUUID 新键＋登记回调表）。 */
    public Sink register(String key, String type) {
        Sink sink = new Sink(key, type);
        if (key != null) sinks.put(key, sink);
        return sink;
    }

    /** 按 key 取汇点；未登记/已摘除回 null（官：callbackMap.get 无此键）。 */
    public Sink sink(String key) {
        return key == null ? null : sinks.get(key);
    }

    /** parse 入口收尾摘除（交付已摘时为空操作），连带清本键的 phase 记账。 */
    public void unregister(Sink sink) {
        if (sink == null) return;
        sinks.remove(sink.key(), sink);
        preCalled.remove(sink.key());
    }

    /**
     * PRE 交付（官 setPreResult→callbackHomeResult p5=true）：打 ARTICLE_LIST_PRE、
     * preCalled 记键、不摘汇点。汇点已关闭回 false。
     */
    public boolean deliverPre(Sink sink, List<HkItem> payload) {
        if (sink == null || sink.isClosed()) return false;
        preCalled.add(sink.key());
        sink.add(PHASE_PRE, payload);
        // 求值中途回页（官 onSuccess(PRE) 同位）：占位段先上屏，后续条目修改
        // 事件才有活表可删（S3）。监听回调在求值线程同步跑完才继续求值。
        sink.notifySegment(PHASE_PRE, payload);
        return true;
    }

    /**
     * 正式交付·home 支（官 setResult/setHomeResult/setSearchResult→callbackHomeResult）：
     * key 在 preCalled 里则摘记并打 ARTICLE_LIST_AFTER、否则 phase=null；
     * 交付后摘除汇点——同 key 二次交付回 false 被丢（官 :942-944 回调不存在即 return）。
     */
    public boolean deliver(Sink sink, List<HkItem> payload) {
        if (sink == null || sink.isClosed()) return false;
        String phase = preCalled.remove(sink.key()) ? PHASE_AFTER : null;
        sink.add(phase, payload);
        sink.close();
        sinks.remove(sink.key(), sink);
        // 分段收口（官 onSuccess(AFTER) 同位）：本发有 PRE 段时正式段经通道
        // 追加回页，调用方不再整表重绑（合成表含已删占位、重绑会令其复活）。
        if (PHASE_AFTER.equals(phase)) sink.notifySegment(phase, payload);
        return true;
    }

    /**
     * 坏载荷正式交付的摘汇点（官 callbackHomeResult 非对象支：showErr 后只
     * remove 回调、不记 phase 账、不产生交付段）：关闭并摘除汇点，preCalled
     * 不动（官该支在 preCalled 记账代码之前 return）。环23 补入的官 remove
     * 语义，既有交付记账语义不变。
     */
    public boolean drop(Sink sink) {
        if (sink == null || sink.isClosed()) return false;
        sink.close();
        sinks.remove(sink.key(), sink);
        return true;
    }

    /**
     * 正式交付·search 支（官 callbackSearchResult，JE:1170 起）：同为一次性交付、
     * 交付后摘除汇点，但不消费 phase 记账——preCalled 只在 callbackHomeResult
     * （JE:918-1168）内被查/摘，search 支全程不碰；phase 恒 null。
     * （G27：setResult/setHomeResult 的 typeKey="search" 与 setSearchResult 的
     * 非 "home" 都落此支。）
     */
    public boolean deliverSearch(Sink sink, List<HkItem> payload) {
        if (sink == null || sink.isClosed()) return false;
        sink.add(null, payload);
        sink.close();
        sinks.remove(sink.key(), sink);
        return true;
    }
}
