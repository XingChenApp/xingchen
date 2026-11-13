package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.Animatable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.hk.HkHttpParser;
import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.api.hk.HkPageParser;
import com.fongmi.android.tv.api.hk.HkParserKit;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkSearchEngine;
import com.fongmi.android.tv.api.hk.SearchResult;
import com.fongmi.android.tv.ui.hk.view.HkCenterListPopup;
import com.fongmi.android.tv.utils.Task;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scwang.smart.refresh.layout.SmartRefreshLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.util.Log;

/**
 * 照 8.83 SearchFragment（ui/search/SearchFragment.smali，6233 行）转写：
 * 单发动机搜索页。initData 为统一加载入口（状态 1 + StatusEvent + SearchModel.process）；
 * bindArrayToView 做精准模式 filterData + 首/末页指纹去重 + 空态/失败伪条目（C3/C4/C5）；
 * 点击走 dealUrl → dealLazyRule / toNextPage / dealUrlSimply / dealWithUrl 管线（与文章页同构，
 * 条目为 SearchResult）；宿主经 SearchHost 打开详情规则页。
 */
public class HkSearchFragment extends Fragment implements HkSearchModel.Callback {
    private static final String TAG = "HkSearchFragment";

    /** 宿主接缝：详情规则页打开（原版直接 start FilmListActivity）与单规则宿主判定（自动打开唯一结果）。 */
    public interface SearchHost {
        void openSearchRulePage(HkRule rule, String title);

        boolean isOneRuleHost();
    }

    /**
     * 全源会话宿主接缝（2026-10-24「提交后全部源一起搜」）：宿主持有 HkSearchSession 时，
     * 页签第一页不再自搜——开搜与结果留存都归会话，页签只领结果呈现；会话里没有本源
     * （未提交过/空词/已切组）时领不到状态，页签回落原 initData 自搜，行为与改前一致。
     */
    public interface SearchSessionHost extends SearchHost {
        /** 领本源在当前会话里的留存状态；无会话/无本源返回 null。 */
        HkSearchSession.State sessionStateFor(HkSearchEngine engine);

        /** 请求会话（重新）搜本源（下拉/长按刷新走这里，排队中的提优先、在途的不重复发起）。 */
        void sessionSearch(HkSearchEngine engine);
    }

    private HkSearchAdapter adapter;
    private String firstPageData;
    private String group;
    private boolean isRefresh;
    private String keyWord;
    private String lastPageData;
    private String myUrl;
    private String orginalUrl;
    private int page = 1;
    private AppCompatImageView progressView1;
    private final List<SearchResult> results = new ArrayList<>();
    private HkSearchEngine searchEngine;
    private HkSearchModel searchModel;
    /** 全源会话模式：宿主有会话且领到本源状态时为真，第一页归会话搜、本页只领结果。 */
    private boolean sessionMode;
    private HkSearchSession.State sessionState;
    /** 会话模式下本源尚未终结、等状态事件回来领结果。 */
    private boolean sessionWaiting;
    private SmartRefreshLayout smartRefreshLayout;
    private RecyclerView recyclerView;
    private SearchHost searchHost;

    public HkSearchFragment() {
        // 照原版构造：searchModel 预建并挂 storeUrl 回调（取页成功后回填 myUrl）
        searchModel = new HkSearchModel().withUrlParseCallBack(url -> myUrl = url);
    }

    /**
     * 引擎进程内槽（原版 Application.tempRuleData 同哲学）：引擎
     * 本体（含规则全文，118 真机实测单个 3.6MB）绝不进 arguments
     * ——状态保存时 arguments 经 Binder 整包发送，超限即
     * TransactionTooLargeException 崩溃（2026-11-09 崩溃图
     * 实证）。arguments 只留槽键（url|title），引擎本体留进程内
     * 静态槽；进程死亡后宿主 onCreate 重建引擎表时同键回填，
     * onViewCreated 的 restore 仍领得到。
     */
    private static final java.util.Map<String, HkSearchEngine> ENGINE_SLOTS =
            new java.util.concurrent.ConcurrentHashMap<>();

    static String engineSlotKey(HkSearchEngine engine) {
        if (engine == null) return null;
        String url = engine.getUrl() == null ? "" : engine.getUrl();
        String title = engine.getTitle() == null ? "" : engine.getTitle();
        return url + "|" + title;
    }

    static void registerEngineSlot(HkSearchEngine engine) {
        String key = engineSlotKey(engine);
        if (key != null) ENGINE_SLOTS.put(key, engine);
    }

    public HkSearchFragment newInstance(String keyWord, HkSearchEngine engine, int ignored, String group) {
        this.keyWord = keyWord;
        this.searchEngine = engine;
        this.group = group;
        // 同步落 arguments：框架无参重建（进程回收恢复）时实例字段全丢，靠 arguments 把三件找回来
        Bundle args = new Bundle();
        args.putString("hk_keyword", keyWord);
        args.putString("hk_group", group);
        if (engine != null) {
            registerEngineSlot(engine);
            args.putString("hk_engine_slot", engineSlotKey(engine));
        }
        setArguments(args);
        return this;
    }

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        if (context instanceof SearchHost) searchHost = (SearchHost) context;
    }

    /** 进程回收后框架无参重建时，从 arguments 恢复 keyword/group/searchEngine（字段已由 newInstance 赋值时不动）。 */
    private void restoreFromArguments() {
        if (searchEngine != null) return;
        Bundle args = getArguments();
        if (args == null) return;
        keyWord = args.getString("hk_keyword");
        group = args.getString("hk_group");
        String slot = args.getString("hk_engine_slot");
        if (slot != null) searchEngine = ENGINE_SLOTS.get(slot);
    }

    public void setSearchHost(SearchHost host) {
        this.searchHost = host;
    }

    public HkSearchEngine getSearchEngine() {
        return searchEngine;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_hk_search_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        restoreFromArguments();
        initView(view);
        if (!enterSessionMode()) initData();
    }

    @Override
    public void onStart() {
        super.onStart();
        // 环30：恒注册——除会话状态事件外，本面还有 ItemModifyEvent 消费
        // （官 SearchFragment 同构订阅）；会话事件 handler 自带 sessionMode 门。
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    public void onStop() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    private SearchSessionHost sessionHost() {
        return searchHost instanceof SearchSessionHost ? (SearchSessionHost) searchHost : null;
    }

    /**
     * 试进全源会话模式：宿主有会话且领得到本源状态才进。已终结→立刻领结果呈现（不重搜）；
     * 待搜/在途→挂等待，状态事件回来再领。领不到（未提交/空词守卫同口径）返回 false 走原自搜。
     */
    private boolean enterSessionMode() {
        SearchSessionHost host = sessionHost();
        if (host == null || searchEngine == null) return false;
        if (keyWord == null || keyWord.trim().isEmpty()) return false;
        HkSearchSession.State state = host.sessionStateFor(searchEngine);
        if (state == null) return false;
        sessionMode = true;
        sessionState = state;
        adoptSessionState();
        return true;
    }

    /** 按留存快照呈现：终结走与实时回调同一套 UI 落点（bindArrayOnUi/errorOnUi），未终结只同步转圈态。 */
    private void adoptSessionState() {
        if (sessionState == null) return;
        HkSearchSession.Snapshot snap = sessionState.snapshot();
        myUrl = snap.myUrl;
        if (snap.status == 2 || snap.status == 4) {
            sessionWaiting = false;
            List<SearchResult> list = snap.results == null ? new ArrayList<>() : snap.results;
            String json = snap.json == null ? new Gson().toJson(list) : snap.json;
            int smode = 0;
            if (getContext() != null) {
                smode = PreferenceManager.getDefaultSharedPreferences(getContext()).getInt("smode", 0);
            }
            bindArrayOnUi(list, json, smode);
        } else if (snap.status == 3) {
            sessionWaiting = false;
            errorOnUi("", "", "", null);
        } else {
            sessionWaiting = true;
            loading(snap.status == 1);
        }
    }

    /** 会话里本源的状态事件：搜索中→转圈；终结→领留存结果。只认自己那台发动机。 */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSessionStatusEvent(HkSearchStatusEvent event) {
        if (!sessionMode || !sessionWaiting || event == null || event.engine == null) return;
        if (!HkSearchSession.keyOf(event.engine).equals(HkSearchSession.keyOf(searchEngine))) return;
        if (event.engine.getStatus() == 1) {
            loading(true);
            return;
        }
        adoptSessionState();
    }

    /** 照官 hasCls：任一为空回 false；两边包空格整词包含。 */
    private static boolean hasClsS(String itemCls, String cls) {
        if (itemCls == null || itemCls.isEmpty() || cls == null || cls.isEmpty()) return false;
        return (" " + itemCls + " ").contains(" " + cls + " ");
    }

    private static String extraIdOf(SearchResult r) {
        String id = extraField(r, "id");
        return id == null ? "" : id;
    }

    private static int indexOfExtraIdS(List<SearchResult> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(extraIdOf(list.get(i)))) return i;
        }
        return -1;
    }

    private static SearchResult toSearchResult(HkItem it) {
        SearchResult r = new SearchResult();
        r.setTitle(it.getTitle());
        r.setUrl(it.getUrl());
        r.setDesc(it.getDesc());
        r.setImg(it.getPic());
        r.setContent(it.getContent());
        r.setType(it.getType());
        if (it.getExtra() != null) r.setExtra(it.getExtra());
        return r;
    }

    /**
     * 环30：照官 SearchFragment.onItemModify 与 ArticleListFragment 同构全支
     * （此前本面无此订阅、恒无消费方，D30-1）。条目载体是 SearchResult，
     * 锚/cls 同走 extra.id / extra.cls；通知统一走 notifyDataSetChanged
     * （本面 adapter 无 poly 分组面，与官逐支通知的可见终态一致）。
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onItemModify(com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent event) {
        if (event == null || event.getAction() == null) return;
        if (getActivity() == null || getActivity().isFinishing()) return;
        if (adapter == null || adapter.getList().isEmpty()) return;
        List<SearchResult> list = adapter.getList();
        com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action action = event.getAction();
        if (action == com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action.DELETE
                && event.getCls() != null && !event.getCls().isEmpty()) {
            boolean changed = false;
            java.util.Iterator<SearchResult> iter = list.iterator();
            while (iter.hasNext()) {
                if (hasClsS(extraField(iter.next(), "cls"), event.getCls())) {
                    iter.remove();
                    changed = true;
                }
            }
            if (changed) adapter.notifyDataSetChanged();
            return;
        }
        if (action == com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action.DELETE
                && event.getList() != null && !event.getList().isEmpty()) {
            // lambda59 → batchDelete（复核订正 B5）：桩 extra.id 成集合后迭代
            // 删全部命中（含同 id 重复条目），旧形逐桩首命中只删一条。
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (Object o : event.getList()) {
                if (!(o instanceof HkItem)) continue;
                String id = ((HkItem) o).getExtra("id");
                if (id != null && !id.isEmpty()) ids.add(id);
            }
            boolean changed = false;
            if (!ids.isEmpty()) {
                java.util.Iterator<SearchResult> iter = list.iterator();
                while (iter.hasNext()) {
                    if (ids.contains(extraIdOf(iter.next()))) {
                        iter.remove();
                        changed = true;
                    }
                }
            }
            if (changed) adapter.notifyDataSetChanged();
            return;
        }
        String anchor;
        if (action == com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action.DELETE) {
            Object stub = event.getArticleList();
            anchor = stub instanceof HkItem ? ((HkItem) stub).getExtra("id") : "";
        } else {
            anchor = event.getAnchorId() == null ? "" : event.getAnchorId();
        }
        if (anchor == null || anchor.isEmpty()) return;
        int index = indexOfExtraIdS(list, anchor);
        if (index < 0) return;
        try {
            switch (action) {
                case ADD: {
                    List<SearchResult> adds = new ArrayList<>();
                    if (event.getList() != null) {
                        for (Object o : event.getList()) if (o instanceof HkItem) adds.add(toSearchResult((HkItem) o));
                    } else if (event.getArticleList() instanceof HkItem) {
                        adds.add(toSearchResult((HkItem) event.getArticleList()));
                    }
                    if (adds.isEmpty()) return;
                    list.addAll(event.isAfter() ? index + 1 : index, adds);
                    adapter.notifyDataSetChanged();
                    break;
                }
                case DELETE:
                    list.remove(index);
                    adapter.notifyDataSetChanged();
                    break;
                case UPDATE: {
                    SearchResult target = list.get(index);
                    if (!anchor.equals(extraIdOf(target))) return;
                    java.util.Map<String, Object> m = event.getSourceMap();
                    if (m != null) {
                        if (m.containsKey("title") && m.get("title") != null) target.setTitle(String.valueOf(m.get("title")));
                        if (m.containsKey("url") && m.get("url") != null) target.setUrl(String.valueOf(m.get("url")));
                        String picKey = m.containsKey("img") ? "img" : m.containsKey("pic") ? "pic" : m.containsKey("pic_url") ? "pic_url" : null;
                        if (picKey != null && m.get(picKey) != null) target.setImg(String.valueOf(m.get(picKey)));
                        if (m.containsKey("desc") && m.get("desc") != null) target.setDesc(String.valueOf(m.get("desc")));
                        if (m.containsKey("content") && m.get("content") != null) target.setContent(String.valueOf(m.get("content")));
                        if (m.containsKey("col_type") && m.get("col_type") != null) target.setType(String.valueOf(m.get("col_type")));
                        if (m.containsKey("extra") && m.get("extra") != null) {
                            Object ex = m.get("extra");
                            target.setExtra(ex instanceof String ? (String) ex : new Gson().toJson(ex));
                        }
                    }
                    adapter.notifyDataSetChanged();
                    break;
                }
                default:
                    break;
            }
        } catch (Exception e) {
            // 条目变更事件处理失败不再静默停在旧状态（残项处置 2026-11-14）。
            Log.e(TAG, "onItemModify failed", e);
            HkRouteBlocker.toastShortBottom(getContext(), "条目更新失败，列表可能未同步");
        }
    }

    protected void initView(View root) {
        smartRefreshLayout = root.findViewById(R.id.refresh_layout);
        smartRefreshLayout.setOnRefreshListener(layout -> {
            page = 1;
            isRefresh = true;
            // F-13：新一轮搜索先清到底态，否则刷新后仍被 noMore 拦住翻页
            layout.resetNoMoreData();
            if (sessionMode) requestSessionSearch();
            else initData();
        });
        orginalUrl = searchEngine.getSearch_url();
        // 照原版 initView（:5661）：search_url 含 fypage 才挂 OnLoadMoreListener（不动 enable）；
        // 不含时 setEnableAutoLoadMore(false) + setEnableLoadMore(false)。
        boolean hasPage = orginalUrl != null && orginalUrl.contains("fypage");
        if (hasPage) {
            smartRefreshLayout.setOnLoadMoreListener(layout -> {
                page = page + 1;
                isRefresh = false;
                initData();
            });
        } else {
            smartRefreshLayout.setEnableAutoLoadMore(false);
            smartRefreshLayout.setEnableLoadMore(false);
        }
        recyclerView = root.findViewById(R.id.search_recycleview_v2);
        LinearLayoutManager manager = new LinearLayoutManager(getContext());
        manager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(manager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        adapter = new HkSearchAdapter(getContext(), results, keyWord, searchEngine);
        adapter.setOnItemClickListener(new HkSearchAdapter.OnItemClickListener() {
            @Override
            public void onClick(View view, int position, String desc) {
                onItemClick(view, position, desc);
            }

            @Override
            public void onLongClick(View view, int position, String desc) {
                onItemLongClick(view, position, desc);
            }
        });
        recyclerView.setAdapter(adapter);
        progressView1 = root.findViewById(R.id.progress_image_view1);
    }

    /** 会话模式下的重搜请求：归会话排队（在途不重复发起），本页挂等待、终结事件回来领新结果。 */
    private void requestSessionSearch() {
        SearchSessionHost host = sessionHost();
        if (host == null || searchEngine == null) return;
        sessionWaiting = true;
        loading(true);
        host.sessionSearch(searchEngine);
    }

    protected void initData() {
        // 空词不搜：页面刚打开、用户还没输词时页签先建出来，不许拿空关键词自己开搜
        // （真机 1.4.22 实证：一点进全局搜索就全发动机空搜、底部挂「搜索中」）。
        // 提交后 Activity 走 refreshPager 以真词重建页签，本守卫不拦真搜索。
        if (keyWord == null || keyWord.trim().isEmpty()) return;
        // F-13：第一页重搜时清到底态（长按「刷新页面」等不走 OnRefreshListener 的入口同样生效）
        if (page == 1 && smartRefreshLayout != null) smartRefreshLayout.resetNoMoreData();
        // 全源会话模式：第一页归会话搜（长按「刷新页面」等入口同走会话重搜）；
        // 上拉翻页（page≥2）仍走本页模型，与改前一致，结果只并进本页、不回写留存。
        if (sessionMode) {
            if (page == 1) {
                requestSessionSearch();
                return;
            }
            if (sessionWaiting) return;
        }
        searchEngine.setStatus(1);
        EventBus.getDefault().post(new HkSearchStatusEvent(searchEngine, group));
        loading(true);
        searchModel.params(keyWord, searchEngine, page, Boolean.TRUE);
        searchModel.process(HkSearchModel.SEARCH_BY_RULE, this);
    }

    /** 照原版 loading(Z)：未 attach 直接返回；显示时启 AVD 动画，隐藏时停并 INVISIBLE。 */
    public void loading(boolean show) {
        if (!isAdded() || isDetached()) return;
        if (progressView1 == null) return;
        try {
            if (show) {
                progressView1.setVisibility(View.VISIBLE);
                if (progressView1.getDrawable() instanceof Animatable) {
                    ((Animatable) progressView1.getDrawable()).start();
                }
            } else {
                if (progressView1.getDrawable() instanceof Animatable) {
                    ((Animatable) progressView1.getDrawable()).stop();
                }
                progressView1.setVisibility(View.INVISIBLE);
            }
        } catch (Exception e) {
            // 加载圈显隐失败（多为动画启动失败）不再静默（残项处置 2026-11-14）：
            // 退到只设可见性把指示状态落对，动画那部分失败留痕可查。
            Log.e(TAG, "loading 指示动画失败，退到仅设可见性：show=" + show, e);
            try {
                progressView1.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            } catch (Exception e2) {
                Log.e(TAG, "loading 指示可见性设置失败：show=" + show, e2);
            }
        }
    }

    // ---------------- BaseCallback 三法 ----------------

    @Override
    public void bindArrayToView(String key, List<SearchResult> list) {
        Activity activity = getActivity();
        if (activity == null) return;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            int smode = prefs.getInt("smode", 0);
            filterData(list);
            String json = new Gson().toJson(list);
            activity.runOnUiThread(() -> bindArrayOnUi(list, json, smode));
        } catch (Exception e) {
            // 结果落表失败不再静默停在空白/加载态（残项处置 2026-11-14）：
            // 走本页既有 error 失败出口（灭圈、置失败状态、收刷新）。
            Log.e(TAG, "bindArrayToView 搜索结果落表失败", e);
            error("解析失败", "搜索结果处理失败：" + e, "500", e);
        }
    }

    /** 照 lambda$bindArrayToView$13：指纹去重、空态伪条目、状态 2/4、唯一结果自动打开。
     * F-13 到底态：空页，或整页指纹与首/末页相同（源站超页恒回末页）→ 置 noMore，
     * 后续上拉由 SmartRefreshLayout 直接拦截、不再发请求；刷新（page=1）时先 resetNoMoreData。 */
    private void bindArrayOnUi(List<SearchResult> list, String json, int smode) {
        loading(false);
        if (smartRefreshLayout != null) smartRefreshLayout.finishRefresh(true);
        List<SearchResult> oldResults = new ArrayList<>(results);
        int oldSize = results.size();
        if (isRefresh) {
            results.clear();
            isRefresh = false;
        }
        boolean noMore = false;
        if (list.size() < 1) {
            noMore = true;
        } else if (results.size() > 0) {
            if (!json.equals(firstPageData) && !json.equals(lastPageData)) {
                results.addAll(list);
            } else {
                noMore = true;
            }
        } else {
            results.addAll(list);
        }
        if (smartRefreshLayout != null) {
            if (noMore) smartRefreshLayout.finishLoadMoreWithNoMoreData();
            else smartRefreshLayout.finishLoadMore();
        }
        boolean autoOpen = false;
        if (results.size() < 1) {
            results.addAll(oldResults);
            searchEngine.setStatus(4);
            EventBus.getDefault().post(new HkSearchStatusEvent(searchEngine, group));

        } else {
            autoOpen = checkAutoOpen();
            searchEngine.setStatus(2);
            EventBus.getDefault().post(new HkSearchStatusEvent(searchEngine, group));
        }
        if (!autoOpen && adapter != null) adapter.notifyDataSetChanged();
        if (oldSize == 0) {
            firstPageData = json;
        } else {
            lastPageData = json;
        }
        if (autoOpen) {
            SearchResult only = results.get(0);
            dealUrl(null, only.getUrl(), 0, only);
        }
    }

    /** 唯一结果自动打开判定（原版仅单规则宿主）：url 非空、非 hiker://search、extra.rules 非空。 */
    private boolean checkAutoOpen() {
        if (searchHost == null || !searchHost.isOneRuleHost()) return false;
        if (results.size() != 1) return false;
        SearchResult only = results.get(0);
        if (!HkStringUtil.isNotEmpty(only.getUrl())) return false;
        if (only.getUrl().startsWith("hiker://search")) return false;
        return HkStringUtil.isNotEmpty(extraField(only, "rules"));
    }

    @Override
    public void bindObjectToView(String key, SearchResult item) {
        // 原版为空实现
    }

    @Override
    public void error(String title, String msg, String code, Exception e) {
        Activity activity = getActivity();
        if (activity == null || !isAdded() || isDetached()) return;
        activity.runOnUiThread(() -> errorOnUi(title, msg, code, e));
    }

    /**
     * 单源失败落点。原版（lambda$error$14）会往结果列表塞一条 "数据获取失败-_-" 伪条目
     * 把整页变成报错页；2026-10-24 用户定口径改之：单源失败只在它自己的底部状态位
     * 标「失败」（状态 3 + 状态事件，宿主状态条与失败源汇总都走这条），结果页保持空、
     * 不当结果页展示，搜索跳过本源继续其余源。此处刻意不照原版塞伪条目。
     */
    private void errorOnUi(String title, String msg, String code, Exception e) {
        if (!isAdded() || isDetached()) return;
        loading(false);
        if (isRefresh && smartRefreshLayout != null) {
            smartRefreshLayout.finishRefresh(false);
            smartRefreshLayout.finishLoadMore(false);
        }
        searchEngine.setStatus(3);
        EventBus.getDefault().post(new HkSearchStatusEvent(searchEngine, group));
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    /** 照 DebugUtil.getErrorMsg(4 参) 的拼串格式。 */
    static String getErrorMsg(String title, String msg, String code, Throwable e) {
        StringWriter sw = new StringWriter();
        if (e != null) e.printStackTrace(new PrintWriter(sw));
        return "错误：" + title + "   错误码：" + code + "   描述：" + msg + "   堆栈：" + sw;
    }

    // ---------------- 精准模式过滤（C6） ----------------

    /** 照原版 filterData：smode==0 不过滤；单条特殊页豁免；余下按 searchContains(title, keyWord) 逐条删。 */
    private void filterData(List<SearchResult> list) {
        filterData(getContext(), keyWord, list);
    }

    /** filterData 的可复用形态：宿主全源会话（HkSearchSession）回结果时按同一口径过滤，共用此份实现不许分叉。 */
    static void filterData(android.content.Context context, String keyWord, List<SearchResult> list) {
        if (context == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int smode = prefs.getInt("smode", 0);
        if (smode == 0) return;
        if (list == null || list.isEmpty()) return;
        if (list.size() == 1) {
            SearchResult only = list.get(0);
            String title = only.getTitle();
            String url = only.getUrl();
            if (title != null) {
                if (title.contains("验证码")) return;
                if (title.contains("聚合搜索")) return;
                if (title.contains("聚搜")) return;
                if (title.contains("点我")) return;
                if (title.contains("加载") && HkStringUtil.isNotEmpty(extraField(only, "id"))) return;
            }
            if (url != null && url.equals("hiker://empty")) return;
        }
        Iterator<SearchResult> it = list.iterator();
        while (it.hasNext()) {
            SearchResult r = it.next();
            String u = r.getUrl();
            if (HkStringUtil.isNotEmpty(u)) {
                if (u.startsWith("hiker://search")) continue;
                if (u.startsWith("海阔视界")) continue;
            }
            if (!HkStringUtil.searchContains(r.getTitle(), keyWord, false)) {
                it.remove();
            }
        }
    }

    /** SearchResult.extra（BaseExtra JSON）取字段（原版 getBaseExtra().getXxx() 等价）。 */
    static String extraField(SearchResult result, String field) {
        if (result == null || result.getExtra() == null) return null;
        try {
            JsonObject obj = JsonParser.parseString(result.getExtra()).getAsJsonObject();
            if (obj.has(field) && !obj.get(field).isJsonNull()) {
                return obj.get(field).getAsString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ---------------- 条目转换 ----------------

    /** 照原版 toArticleList：SearchResult → HkItem（桥 dealUrlSimply 的条目入参；extra 原始 JSON 串透传）。 */
    static HkItem toItem(SearchResult r) {
        HkItem item = new HkItem();
        if (r == null) return item;
        item.setTitle(r.getTitle());
        item.setUrl(r.getUrl());
        item.setPic(r.getImg());
        item.setDesc(r.getDesc());
        item.setContent(r.getContent());
        item.setType(r.getType());
        // extra 已是 8.83 语义的原始 JSON 串（数字保持数字），直接透传，不再转 Map
        if (r.getExtra() != null) item.setExtra(r.getExtra());
        return item;
    }

    private String getItemTitle(int position) {
        if (adapter == null || position < 0 || position >= adapter.getList().size()) return "";
        String t = adapter.getList().get(position).getTitle();
        return t == null ? "" : t;
    }

    /** 照 SearchFragment.getArticleListRule：引擎 + 目标 url 组装详情规则。 */
    static HkRule getArticleListRule(HkSearchEngine engine, String url, String codeAndHeader,
                                     String findRule, String colType) {
        HkRule rule = new HkRule();
        rule.setUa(engine.getUa());
        if (!url.contains(";") && HkStringUtil.isNotEmpty(codeAndHeader)) {
            rule.setUrl(url + codeAndHeader);
        } else {
            rule.setUrl(url);
        }
        rule.setFindRule(findRule);
        rule.setGroup(engine.getGroup());
        rule.setColType(colType);
        rule.setPreRule(engine.getPreRule());
        rule.setTitle(engine.getTitle());
        rule.setDisplayName(engine.getDisplayName());
        rule.setLastChapterRule(engine.getLast_chapter_rule());
        rule.setPages(engine.getPages());
        return rule;
    }

    private HkArticleFragment.DetailBridge bridge() {
        return HkArticleFragment.detailBridge();
    }

    // ---------------- 点击管线（照 SearchFragment$1 + dealUrl/dealWithUrl） ----------------

    /** 照 SearchFragment$1.onClick：伪条目两支、hiker://home 路由、余下走 dealUrl。 */
    private void onItemClick(View view, int position, String desc) {
        if (adapter == null || position < 0 || position >= adapter.getList().size()) return;
        SearchResult result = adapter.getList().get(position);
        String title = result.getTitle();
        if (title != null && title.startsWith("没有找到你想要的噢-_-")) {
            if (title.contains("精准") && getContext() != null && !HkDialogs.isHostGone(getContext())) {
                new HkDialogs(getContext())
                        .setTitle("温馨提示")
                        .setMessage("当前为精准搜索模式，可能会过滤掉一些结果，可以改为默认搜索模式查看全部搜索结果")
                        .setPositiveButton("知道了", null)
                        .show();
            }
            return;
        }
        if ("数据获取失败-_-".equals(title) && "小棉袄".equals(desc)) {
            showPosDetail(position);
            return;
        }
        String url = result.getUrl();
        if (url != null && url.startsWith("hiker://home")) {
            HkRouteBlocker.isRoute(getActivity(), url);
            return;
        }
        dealUrl(view, url, position, result);
    }

    /** 照原版 dealUrl：@lazyRule= 先行、isPageUrl 走详情规则、dealUrlSimply、x5 合成规则、@rule= 子规则、余下 dealWithUrl。 */
    private void dealUrl(View view, String url, int position, SearchResult result) {
        if (url == null) url = "";
        String[] ruleSplit = url.split("@rule=");
        String[] lazySplit = ruleSplit[0].split("@lazyRule=");
        HkArticleFragment.DetailBridge bridge = bridge();
        String codeAndHeader = bridge == null ? ""
                : bridge.getCodeAndHeader(HkHttpParser.getUrlAppendUA(orginalUrl, searchEngine.getUa()), lazySplit);
        if (lazySplit.length > 1) {
            dealLazyRule(view, position, lazySplit, codeAndHeader);
            return;
        }
        if (HkPageParser.isPageUrl(url)) {
            toNextPage(position, searchEngine, url, codeAndHeader,
                    searchEngine.getDetail_find_rule(), searchEngine.getDetail_col_type(), result);
            return;
        }
        if (bridge != null && bridge.dealUrlSimply(getActivity(), view, searchEngine.toRule(),
                toItem(result), url,
                r -> dealWithUrl(view, r, position, codeAndHeader), null)) {
            return;
        }
        if (url.startsWith("x5://")) {
            openSyntheticWebRule(url, "x5://", position);
            return;
        }
        if (ruleSplit.length > 1) {
            String ruleStr = HkParserKit.arrayToString(ruleSplit, 1, "@rule=");
            String[] seg = ruleStr.split("==>");
            String head = seg[0];
            String[] semi = head.split(";");
            String colType = searchEngine.getDetail_col_type();
            if (semi.length == 6 && !ruleStr.startsWith("js:")) {
                colType = semi[5];
                head = HkParserKit.arrayToString(semi, 0, 5, ";");
            }
            String tail = "";
            if (seg.length > 1) tail = "==>" + HkParserKit.arrayToString(seg, 1, "==>");
            dealRule(searchEngine, result, ruleSplit[0], codeAndHeader, head + tail, colType, position);
            return;
        }
        dealWithUrl(view, url, position, codeAndHeader);
    }

    /** 照原版 x5:// 支：合成 x5_webview_single 规则开页（find_rule 为 js:setResult 单条）。 */
    private void openSyntheticWebRule(String url, String prefix, int position) {
        String target = HkStringUtil.replaceOnceIgnoreCase(url, prefix, "");
        HkRule rule = new HkRule();
        rule.setUrl("hiker://empty");
        rule.setFindRule("js:setResult([{\n    url:\"" + com.fongmi.android.tv.api.hk.HkJsRuntime.escapeJsString(target)
                + "\",\ndesc:\"100%&&float\",\nextra:{canBack: true}\n}]);");
        rule.setColType(HkColType.X5_WEB_VIEW.code);
        rule.setGroup(searchEngine.getGroup());
        rule.setTitle(searchEngine.getTitle());
        rule.setDisplayName(searchEngine.getDisplayName());
        if (searchHost != null) {
            searchHost.openSearchRulePage(rule, getItemTitle(position));
        }
    }

    /** 照原版 dealLazyRule + 回调：Fuck 直开优先；余下桥 parseLazyRule，成功回灌 dealWithUrl。 */
    private void dealLazyRule(View view, int position, String[] lazySplit, String codeAndHeader) {
        if (HkFuck.inList(getActivity()) && HkFuck.goWeb(getContext(), myUrl, lazySplit[0])) return;
        HkArticleListModel.EngineBridge bridge = HkArticleListModel.getEngineBridge();
        if (bridge == null) return;
        bridge.parseLazyRule(searchEngine.toRule(), lazySplit, position, codeAndHeader, myUrl,
                new HkArticleListModel.LazyCallback() {
                    @Override
                    public void start() {
                        showLoadingDialog();
                    }

                    @Override
                    public void success(String result) {
                        dismissLoadingDialog();
                        if (result != null && result.startsWith("error:")) {
                            // 引擎真实错误（含行号），直接弹原文，不走 URL 兜底
                            HkRouteBlocker.toastShortBottom(getContext(), result);
                            return;
                        }
                        dealWithUrl(view, result, position, codeAndHeader);
                    }

                    @Override
                    public void error(String msg) {
                        dismissLoadingDialog();
                    }
                });
    }

    private androidx.appcompat.app.AlertDialog loadingDialog;

    private void showLoadingDialog() {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            Activity current = getActivity();
            if (current == null || current.isFinishing() || current.isDestroyed()) return;
            dismissLoadingDialog();
            android.widget.TextView text = new android.widget.TextView(activity);
            text.setText("动态解析规则中，请稍候");
            text.setTextSize(15);
            text.setTextColor(0xFFF2F4F8);
            text.setPadding(48, 40, 48, 40);
            loadingDialog = new com.fongmi.android.tv.ui.hk.HkDialogs(activity)
                    .setView(text).setCancelable(false).create();
            loadingDialog.show();
        });
    }

    private void dismissLoadingDialog() {
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (loadingDialog != null) {
                loadingDialog.dismiss();
                loadingDialog = null;
            }
        });
    }

    /** 照原版 dealRule：组装详情规则 + params=条目 extra → 经宿主开页。 */
    private void dealRule(HkSearchEngine engine, SearchResult result, String url, String codeAndHeader,
                          String findRule, String colType, int position) {
        HkRule rule = getArticleListRule(engine, url, codeAndHeader, findRule, colType);
        SearchResult item = adapter.getList().get(position);
        if (HkStringUtil.isNotEmpty(item.getExtra())) {
            rule.setParams(item.getExtra());
        }
        if (searchHost != null) {
            searchHost.openSearchRulePage(rule, getItemTitle(position));
        }
    }

    /** 照原版 toNextPage：后台 getArticleListRule + PageParser.getNextPage（extra 为 paramsJson），UI 线程经宿主开页。 */
    private void toNextPage(int position, HkSearchEngine engine, String url, String codeAndHeader,
                            String findRule, String colType, SearchResult result) {
        Task.execute(() -> {
            try {
                HkRule rule = getArticleListRule(engine, url, codeAndHeader, findRule, colType);
                HkRule next = HkPageParser.getNextPage(rule, url, result.getExtra());
                Activity activity = getActivity();
                if (next == null) {
                    // 第④块尾巴①：与 W1 完全同型的回落（组不出页拿原规则开页＝字面重开首页）
                    // 按第③块同法收掉——照 8.83 toNextPage 语义弹错留本页，不开任何新页。
                    if (activity != null && !activity.isFinishing()) {
                        activity.runOnUiThread(() ->
                                HkRouteBlocker.toastShortBottom(activity, "找不到对应的页面"));
                    }
                    return;
                }
                if (activity != null && !activity.isFinishing()) {
                    activity.runOnUiThread(() -> {
                        if (searchHost != null) {
                            searchHost.openSearchRulePage(next, getItemTitle(position));
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "hk error", e);
                Activity activity = getActivity();
                if (activity != null && !activity.isFinishing()) {
                    activity.runOnUiThread(() ->
                            HkRouteBlocker.toastShortBottom(activity, String.valueOf(e.getMessage())));
                }
            }
        });
    }

    /** 地址剥标记后只留真实页面部分：#标记、@rule=/@lazyRule= 尾巴、# 片段全剥，非 http(s) 回空。 */
    private static String cleanSearchPageUrl(String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String s = HkUrlDetector.clearTag(raw);
        int at = s.indexOf("@rule=");
        if (at >= 0) s = s.substring(0, at);
        int lz = s.indexOf("@lazyRule=");
        if (lz >= 0) s = s.substring(0, lz);
        int h = s.indexOf('#');
        if (h >= 0) s = s.substring(0, h);
        s = s.trim();
        return (s.startsWith("http://") || s.startsWith("https://")) ? s : "";
    }

    /** 照原版 dealWithUrl 的主分支集（与文章页同构，条目为 SearchResult）。 */
    private void dealWithUrl(View view, String url, int position, String codeAndHeader) {
        if (position < 0 || adapter == null || position >= adapter.getList().size()) return;
        if (getContext() == null || getActivity() == null) return;
        HkArticleFragment.DetailBridge bridge = bridge();
        SearchResult result = adapter.getList().get(position);
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (HkPageParser.isPageUrl(url)) {
            toNextPage(position, searchEngine, url, codeAndHeader,
                    searchEngine.getDetail_find_rule(), searchEngine.getDetail_col_type(), result);
            return;
        }
        if (bridge != null && bridge.dealUrlSimply(getActivity(), view, searchEngine.toRule(),
                toItem(result), url,
                r -> dealWithUrl(view, r, position, codeAndHeader), null)) {
            return;
        }
        if (lower.startsWith("javascript")) {
            if (bridge != null) bridge.goWeb(getContext(), url);
            return;
        }
        if (url.startsWith("x5://")) {
            openSyntheticWebRule(url, "x5://", position);
            return;
        }
        if (bridge != null && bridge.canParseX5(url)) {
            if (HkFuck.inList(getActivity()) && HkFuck.goWeb(getContext(), myUrl, url)) return;
            boolean started = bridge.parseX5(getActivity(), url, result.getExtra(),
                    r -> dealWithUrl(view, r, position, codeAndHeader));
            if (started) showLoadingDialog();
            return;
        }
        if (bridge != null && bridge.canParseWebkit(url)) {
            if (HkFuck.inList(getActivity())) {
                HkFuck.goWeb(getContext(), myUrl, url);
                return;
            }
            boolean started = bridge.parseWebkit(getActivity(), url, result.getExtra(),
                    r -> dealWithUrl(view, r, position, codeAndHeader));
            if (started) showLoadingDialog();
            return;
        }
        if (lower.startsWith("pics://")) {
            // 拆分收口共用 HkRouter.parsePics（清标记、按行再按 "&&" 拆、trim
            // 滤空段，与其余 pics 入口同口径）；旧写法裸 split 只滤全空段、
            // 不判空，空体也直组会话。
            java.util.ArrayList<String> pics =
                    new java.util.ArrayList<>(com.fongmi.android.tv.api.hk.HkRouter.parsePics(url));
            // 真实页面地址：被点条目地址剥标记后的 http 地址（懒规则 fetch 的
            // 就是它）；取不到才回退当前页 myUrl（须为 http），再不行留空——
            // 不拿非页面地址冒充 pageUrl/Referer（与文章页入口同口径）。
            String pageUrl = cleanSearchPageUrl(result == null ? "" : result.getUrl());
            if (pageUrl.isEmpty()) pageUrl = cleanSearchPageUrl(myUrl);
            if (!com.fongmi.android.tv.api.hk.HkRouter.hasLoadablePic(pics)) {
                // 入口无图不许拦（fix4）：阅读器自带回退解析（会话图→章节串
                // →router.play 重取→书源），入口一拦全灭——1.4.19 真机
                // 「漫画详情页都进不去」的根。只强制留底，照常组会话进阅读器。
                com.github.catvod.crawler.SpiderDebug.forceLog("hk",
                        "pics empty at search entry (proceed) pics=%d page=%s", pics.size(), pageUrl);
            }
            HkArticleFragment.PictureListRequest req = new HkArticleFragment.PictureListRequest();
            req.pics = pics;
            req.url = pageUrl;
            req.title = getItemTitle(position);
            req.chapters = getChapters(url, position, "", codeAndHeader);
            req.cUrl = searchEngine.getUrl();
            req.mTitle = getItemTitle(position);
            int now = 0;
            for (int i = 0; i < req.chapters.size(); i++) {
                if (req.chapters.get(i).use) now = i;
            }
            req.nowPage = now;
            if (bridge != null) bridge.openPictureList(getActivity(), req);
            return;
        }
        if (com.fongmi.android.tv.api.hk.HkRouter.isMagnetLink(url)) {
            if (bridge != null) bridge.startDownloadMagnet(getContext(), url);
            return;
        }
        if (com.fongmi.android.tv.api.hk.HkRouter.isTorrentLink(url)) {
            if (bridge != null) bridge.startParseTorrent(getContext(), url);
            return;
        }
        if (bridge != null && bridge.isFTPOrEd2k(url)) {
            bridge.startParseFTPOrEd2k(getContext(), url, null);
            return;
        }
        if (bridge != null && bridge.isScheme(url)) {
            bridge.findChooserToDeal(getContext(), url);
            return;
        }
        if (bridge != null && bridge.isImage(url)) {
            List<String> urls = new ArrayList<>();
            urls.add(bridge.clearTag(url));
            ImageView srcView = view instanceof ImageView ? (ImageView) view : null;
            if (srcView != null) {
                bridge.showSingleImage(bridge.clearTag(url), srcView);
            } else {
                bridge.showImageViewer(urls, 0, null, null);
            }
            return;
        }
        if (bridge != null && bridge.isVideoOrMusic(url)) {
            if (url.startsWith("video://") && HkFuck.inList(getActivity())
                    && HkFuck.goWeb(getContext(), myUrl, url)) return;
            String cleared = bridge.clearTag(url);
            String itemCleared = bridge.clearTag(result.getUrl());
            if (!cleared.equals(itemCleared) && !TextUtils.isEmpty(codeAndHeader)
                    && !bridge.canParseX5(result.getUrl()) && !bridge.canParseWebkit(result.getUrl())
                    && HkListActivity.playDataOf(url) == null) {
                bridge.startPlayerSingle(getActivity(), getItemTitle(position), url);
                return;
            }
            String actTitle = getActivity().getTitle() == null ? "" : String.valueOf(getActivity().getTitle());
            String titlePrefix = actTitle.isEmpty() ? "" : actTitle + "-";
            List<HkArticleFragment.HkChapter> chapters = getChapters(url, position, titlePrefix, codeAndHeader);
            android.os.Bundle bundle = new android.os.Bundle();
            String vc = bridge.getViewCollectionExtraData(getActivity(), searchEngine.getUrl());
            if (!TextUtils.isEmpty(vc)) bundle.putString("viewCollectionExtraData", vc);
            if (getActivity().getTitle() != null) {
                bundle.putString("film", String.valueOf(getActivity().getTitle()));
            }
            bundle.putString("rule", new Gson().toJson(searchEngine.toRule()));
            bridge.startPlayerChapters(getActivity(), chapters, searchEngine.getUrl(), actTitle, bundle);
            return;
        }
        // 路由尾对官（与 HkArticleFragment 同口径，2026-11-13）：官
        // 8.83 无「未知链接」toast，各支不中静默收口；旧形自创 toast
        // 把 lazy 完成值 undefined 等正常空果吐成报错，整类摘除。
    }

    /**
     * 照原版 getChapters 的同型分组算法（SearchResult 版）：
     * 以点击项为中心，向前后收同 type 的条目为选集，点击项 use=true。
     */
    private List<HkArticleFragment.HkChapter> getChapters(String url, int position,
                                                          String titlePrefix, String codeAndHeader) {
        List<HkArticleFragment.HkChapter> chapters = new ArrayList<>();
        List<SearchResult> list = adapter.getList();
        SearchResult clicked = list.get(position);
        String type = clicked.getType();
        for (int i = position - 1; i >= 0; i--) {
            if (!TextUtils.equals(type, list.get(i).getType())) break;
            chapters.add(0, buildChapter(list.get(i), i, titlePrefix, codeAndHeader, false));
        }
        HkArticleFragment.HkChapter cur = buildChapter(clicked, position, titlePrefix, codeAndHeader, true);
        cur.url = url;
        chapters.add(cur);
        for (int i = position + 1; i < list.size(); i++) {
            if (!TextUtils.equals(type, list.get(i).getType())) break;
            chapters.add(buildChapter(list.get(i), i, titlePrefix, codeAndHeader, false));
        }
        return chapters;
    }

    private HkArticleFragment.HkChapter buildChapter(SearchResult r, int pos, String titlePrefix,
                                                     String codeAndHeader, boolean use) {
        HkArticleFragment.HkChapter ch = new HkArticleFragment.HkChapter();
        ch.memoryTitle = r.getTitle();
        ch.title = titlePrefix + (r.getTitle() == null ? "" : r.getTitle());
        ch.url = r.getUrl();
        ch.use = use;
        ch.realPos = pos;
        ch.extra = r.getExtra();
        ch.pic = r.getImg();
        if (!TextUtils.isEmpty(codeAndHeader)) {
            ch.codeAndHeader = codeAndHeader;
            ch.originalUrl = r.getUrl();
        }
        return ch;
    }

    // ---------------- 长按菜单与调试数据（照 SearchFragment$1.onLongClick） ----------------

    private void onItemLongClick(View view, int position, String desc) {
        if (adapter == null || position < 0 || position >= adapter.getList().size()) return;
        SearchResult result = adapter.getList().get(position);
        String url = result.getUrl();
        List<String> options = new ArrayList<>();
        options.add("调试数据");
        options.add("复制链接");
        options.add("网页搜索");
        options.add("刷新页面");
        options.add("网页查看");
        if (url != null && (com.fongmi.android.tv.api.hk.HkRouter.isMagnetOrTorrent(url)
                || (bridge() != null && bridge().isVideoOrMusic(url)))) {
            options.add("下载资源");
        }
        if (getContext() == null) return;
        new HkCenterListPopup(getContext())
                .withTitle("请选择操作")
                .with(options, 3, new HkCenterListPopup.ClickListener() {
                    @Override
                    public void click(String text, int pos) {
                        onLongMenuSelected(result, position, text);
                    }

                    @Override
                    public void onLongClick(String text, int pos) {
                    }
                })
                .show();
    }

    private void onLongMenuSelected(SearchResult result, int position, String text) {
        HkArticleFragment.DetailBridge bridge = bridge();
        String url = result.getUrl();
        switch (text) {
            case "调试数据":
                showPosDetail(position);
                break;
            case "复制链接":
                copyToClipboard(url == null ? "" : HkStringUtil.getFirstPageUrl(url));
                HkRouteBlocker.toastShortBottom(getContext(), "已复制链接");
                break;
            case "网页搜索":
                if (bridge != null && url != null) bridge.goWeb(getContext(), url);
                break;
            case "刷新页面":
                page = 1;
                isRefresh = true;
                initData();
                break;
            case "网页查看":
                if (url != null && (url.startsWith("http") || url.startsWith("webview://")
                        || url.startsWith("hiker://empty#http") || url.startsWith("hiker://empty##http"))) {
                    if (bridge != null) bridge.goWeb(getContext(), HkStringUtil.getRealUrl(url));
                } else {
                    HkRouteBlocker.toastShortBottom(getContext(), "当前链接不支持网页查看");
                }
                break;
            case "下载资源":
                downloadResult(result, position);
                break;
            default:
                break;
        }
    }

    private void downloadResult(SearchResult result, int position) {
        HkArticleFragment.DetailBridge bridge = bridge();
        if (bridge == null) return;
        String url = result.getUrl();
        if (TextUtils.isEmpty(url)) {
            HkRouteBlocker.toastShortBottom(getContext(), "链接为空，请检查规则");
            return;
        }
        if (bridge.isCannotHandleScheme(url) || com.fongmi.android.tv.api.hk.HkRouter.isTorrentLink(url)) {
            bridge.findChooserToDeal(getContext(), url);
            return;
        }
        String title = getItemTitle(position);
        if (!bridge.isVideoOrMusic(url)) {
            HkRouteBlocker.toastShortBottom(getContext(), title + "非视频格式，不支持下载");
            return;
        }
        String tagged = bridge.clearTag(url);
        java.util.Map<String, String> headers = HkHttpParser.getHeaders(tagged);
        String real = HkHttpParser.getRealUrlFilterHeaders(tagged);
        bridge.showDownloadDialog(getActivity(), title, real, title, headers);
    }

    /** 照原版 showPosDetail：FileDetailPopup 等价（原生对话框列出调试字段，点行复制）。 */
    private void showPosDetail(int position) {
        if (getContext() == null || adapter == null
                || position < 0 || position >= adapter.getList().size()) return;
        String[] data = getDetailData(position);
        HkDialogs.cards(getContext(), "调试数据", data, "关闭",
                (dialog, which) -> copyToClipboard(data[which]));
    }

    /** 照 ClipboardUtil.copyToClipboardForce：直接写主剪贴板。 */
    private void copyToClipboard(String text) {
        if (getContext() == null) return;
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("", text));
        }
    }

    /** 照原版 getDetailData：页面（>200 截断）/标题/描述/详情/类型/链接/图片/附加 八行。 */
    private String[] getDetailData(int position) {
        SearchResult r = adapter.getList().get(position);
        String url = r.getUrl() == null ? "" : r.getUrl();
        String pageText = url.length() > 200 ? url.substring(0, 200) + "..." : url;
        return new String[]{
                "页面：" + pageText,
                "标题：" + nullToEmpty(r.getTitle()),
                "描述：" + nullToEmpty(r.getDesc()),
                "详情：" + nullToEmpty(r.getContent()),
                "类型：" + nullToEmpty(r.getType()),
                "链接：" + url,
                "图片：" + nullToEmpty(r.getImg()),
                "附加：" + nullToEmpty(r.getExtra()),
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
