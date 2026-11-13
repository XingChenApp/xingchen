package com.fongmi.android.tv.ui.hk;

import android.content.Context;

import com.fongmi.android.tv.api.hk.HkSearchEngine;
import com.fongmi.android.tv.api.hk.SearchResult;
import com.google.gson.Gson;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聚合搜索「提交后全部源一起搜」的执行面会话（2026-10-24 用户定案，推翻页签懒建执行面）。
 *
 * <p>宿主（HkSearchActivity）每次提交关键词建一个会话：当前分组内全部发动机立即按序入队开搜，
 * 结果按发动机留存（{@link State}）；页签渲染仍归 ViewPager2 懒建，页签建出时凭
 * {@link #stateFor(HkSearchEngine)} 领留存结果直接呈现，不重搜。状态落点仍走既有的
 * engine.setStatus + HkSearchStatusEvent，顶部状态条与失败汇总的消费口径不变；
 * 单源失败只落本源状态 3，不影响其余源（用户既定口径）。
 *
 * <p>并发纪律（本机实测形态）：HkSearchModel.process 的阻塞段只有 parsePreRule 的 JS 求值，
 * 取页是 OkHttp enqueue（调用即返回），findList 回到共享 Task 池（固定 5 线程）求值；
 * 主数据 client 的 Dispatcher 是 OkHttp 默认（总 64/同 host 5）。本会话再加一道在途闸
 * {@link #MAX_ACTIVE}：同时在途的发动机搜索不超过 12 个——既防 100+ 发动机把进程/求值一齐压垮，
 * 也防尾部源被埋在上百个 process 提交后面饿死：任一源终结即刻补位下一个，
 * 队列序＝发动机序（＝页签序，先到先搜，用户正在看的页签可经 {@link #requestSearch} 提优先）。
 * JS 运行时按规则各一份（HkEngineManager.runtimeFor），跨发动机并发求值不共上下文；
 * 同一发动机在本会话内由状态机保证同时至多一轮第一页搜索。
 *
 * <p>新提交/切分组/宿主销毁都会 cancel 旧会话：迟到的回调在落状态前先查 cancelled，
 * 不许污染新会话共用的发动机对象与状态条。
 */
public class HkSearchSession {

    /** 同时在途的发动机搜索上限（在途＝已发起未终结）。 */
    static final int MAX_ACTIVE = 12;

    /** 单个发动机的留存状态：状态值与 engine.status 同义（0 待搜/1 搜索中/2 有结果/3 失败/4 无结果）。 */
    public static class State {
        final HkSearchEngine engine;
        int status;
        List<SearchResult> results;
        String json;
        String myUrl;

        State(HkSearchEngine engine) {
            this.engine = engine;
        }

        /** UI 线程领结果用的一致性快照（结果表拷贝，领后与留存互不影响）。 */
        synchronized Snapshot snapshot() {
            return new Snapshot(status,
                    results == null ? null : new ArrayList<>(results), json, myUrl);
        }

        synchronized boolean isTerminal() {
            return status == 2 || status == 3 || status == 4;
        }
    }

    /** {@link State} 的只读快照。 */
    public static class Snapshot {
        public final int status;
        public final List<SearchResult> results;
        public final String json;
        public final String myUrl;

        Snapshot(int status, List<SearchResult> results, String json, String myUrl) {
            this.status = status;
            this.results = results;
            this.json = json;
            this.myUrl = myUrl;
        }
    }

    private final Context appContext;
    private final String keyword;
    private final String group;
    private final Map<String, State> states = new LinkedHashMap<>();
    private final ArrayDeque<State> queue = new ArrayDeque<>();
    private int active;
    private volatile boolean cancelled;

    public HkSearchSession(Context context, String keyword, String group, List<HkSearchEngine> engines) {
        this.appContext = context.getApplicationContext();
        this.keyword = keyword;
        this.group = group;
        if (engines != null) {
            for (HkSearchEngine engine : engines) {
                if (engine == null) continue;
                State state = new State(engine);
                states.put(keyOf(engine), state);
                queue.add(state);
            }
        }
    }

    /** 发动机在本会话内的身份键（页签 getItemId 同字段集：title/search_url/url）。 */
    public static String keyOf(HkSearchEngine engine) {
        if (engine == null) return "";
        return String.valueOf(engine.getTitle()) + "|"
                + String.valueOf(engine.getSearch_url()) + "|"
                + String.valueOf(engine.getUrl());
    }

    /** 全源开搜：把队列泵到在途上限，后续靠每个源的终结回调逐个补位。 */
    public void start() {
        pump();
    }

    /** 废弃本会话：未发起的源不再发起，在途源的迟到回调一律不再落状态/发事件。 */
    public void cancel() {
        cancelled = true;
        synchronized (this) {
            queue.clear();
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * 页签领留存状态：QUEUED 的源顺手提到队首（用户已经滑到它，先搜它）。
     * 本会话没有这个源（未提交/切组后的旧页签）返回 null，页签回落自搜。
     */
    public State stateFor(HkSearchEngine engine) {
        if (cancelled || engine == null) return null;
        State state = states.get(keyOf(engine));
        if (state == null) return null;
        synchronized (this) {
            if (state.status == 0 && queue.remove(state)) queue.addFirst(state);
        }
        return state;
    }

    /**
     * 页签请求（重新）搜某源（下拉刷新/长按刷新）：在途则等它自己终结（页签挂等待即可），
     * 待搜则提队首，已终结则清留存重新入队优先搜。
     */
    public void requestSearch(HkSearchEngine engine) {
        if (cancelled || engine == null) return;
        State state = states.get(keyOf(engine));
        if (state == null) return;
        synchronized (this) {
            if (state.status == 1) return;
            if (state.status == 0) {
                if (queue.remove(state)) queue.addFirst(state);
                return;
            }
            synchronized (state) {
                state.status = 0;
                state.results = null;
                state.json = null;
                state.myUrl = null;
            }
            queue.addFirst(state);
        }
        engine.setStatus(0);
        pump();
    }

    /** 在途未满就按队列序逐个发起；发起只做 process 提交（非阻塞），终结回调再 pump 补位。 */
    private void pump() {
        while (!cancelled) {
            State next;
            synchronized (this) {
                if (active >= MAX_ACTIVE) return;
                next = queue.poll();
                if (next == null) return;
                synchronized (next) {
                    if (next.status != 0) continue;
                    next.status = 1;
                }
                active++;
            }
            launch(next);
        }
    }

    private void launch(State state) {
        HkSearchEngine engine = state.engine;
        engine.setStatus(1);
        EventBus.getDefault().post(new HkSearchStatusEvent(engine, group));
        HkSearchModel model = new HkSearchModel()
                .withUrlParseCallBack(url -> {
                    synchronized (state) {
                        state.myUrl = url;
                    }
                });
        model.params(keyword, engine, 1, Boolean.TRUE);
        try {
            model.process(HkSearchModel.SEARCH_BY_RULE, callbackFor(state));
        } catch (RuntimeException e) {
            // 发起本身炸了也不能占着在途位饿死后面的源：按本源失败终结、补位继续
            finish(state, 3, null, null);
        }
    }

    private HkSearchModel.Callback callbackFor(State state) {
        return new HkSearchModel.Callback() {
            @Override
            public void bindArrayToView(String key, List<SearchResult> list) {
                // 与页签同口径：先按当前模式过滤（精准模式），指纹 json 按过滤后表算
                List<SearchResult> filtered = list == null ? new ArrayList<>() : list;
                HkSearchFragment.filterData(appContext, keyword, filtered);
                finish(state, filtered.isEmpty() ? 4 : 2, filtered, new Gson().toJson(filtered));
            }

            @Override
            public void bindObjectToView(String key, SearchResult item) {
                // 与页签同：空实现
            }

            @Override
            public void error(String title, String msg, String code, Exception e) {
                finish(state, 3, null, null);
            }
        };
    }

    /** 终结落点：写留存→落 engine 状态→发状态事件→补位下一个。cancel 后整段作废。 */
    private void finish(State state, int status, List<SearchResult> results, String json) {
        if (cancelled) return;
        synchronized (this) {
            synchronized (state) {
                if (state.status != 1) return;
                state.status = status;
                state.results = results;
                state.json = json;
            }
            active--;
        }
        if (cancelled) return;
        state.engine.setStatus(status);
        EventBus.getDefault().post(new HkSearchStatusEvent(state.engine, group));
        pump();
    }
}
