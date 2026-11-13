package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Animatable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.hk.HkHttpParser;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.api.hk.event.OnBackEvent;
import com.fongmi.android.tv.api.hk.event.home.SetPageLastChapterRuleEvent;
import com.fongmi.android.tv.api.hk.event.home.SetPageParamsEvent;
import com.fongmi.android.tv.api.hk.event.home.SetPagePicEvent;
import com.fongmi.android.tv.api.hk.event.rule.CacheCode;
import com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent;
import com.github.catvod.crawler.SpiderDebug;
import com.fongmi.android.tv.ui.hk.view.HkCenterListPopup;
import com.fongmi.android.tv.ui.hk.view.MyWebProgress;
import com.fongmi.android.tv.ui.hk.view.RecyclerFastScroller;
import com.fongmi.android.tv.utils.Task;
import com.scwang.smart.refresh.layout.SmartRefreshLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 照 8.83 ArticleListFragment 转写（切片 1：骨架/初始化/刷新/加载态）。
 * 原版经 BaseFragment 的 initLayout/initView/initData 三段初始化，此处内联到 onViewCreated。
 * 数据经 HkArticleListModel（对应 ArticleListModel）.params(...).process("ARTICLE_LIST_NEW", 本 Fragment) 回灌。
 */
public class HkArticleFragment extends Fragment implements HkArticleListModel.Callback {

    private static final String TAG = "ArticleListFragment";

    // —— 原版字段（切片 1 用到的核心集，其余子系统字段随各切片补入）——
    private HkArticleAdapter adapter;
    private HkArticleListModel articleListModel;
    private HkRule articleListRule;
    private HkRule articleListRuleDTO;
    private int bgViewHeight;
    private boolean cannotRefresh;
    private String currentPageUrl;
    private RecyclerFastScroller fastScroller;
    private String firstPageData;
    private GridLayoutManager gridLayoutManager;
    private boolean hasShowLastClick;
    private int headerHeight;
    private int headerSize = -1;
    private boolean immersiveTheme;
    private boolean isFullTheme;
    private boolean isLoading;
    /** 首拉（initData 第一发）是否已落地（bind/error 任一收尾到达）。 */
    private boolean firstLoadSettled;
    /** 首拉未收尾时被 resumed 守卫挡下的 refreshPage 事件（只留最新一发的 scrollTop），首拉落地后补发。 */
    private Boolean pendingHostRefreshScrollTop;
    private boolean isReadTheme;
    /** 环42：#autoReader# 链累积的阅读组件章节表（映射层，见 HkAutoReaderMapper）。 */
    private final List<com.fongmi.android.tv.read.ReadingSession.Chapter> autoReaderChapters = new ArrayList<>();
    /** 环42：阅读记录在 HkPageRecordStore 复合键中的定位 url／标题（随 $17/$64 改写漂移，懒初始化）。 */
    private String pageRecordUrl;
    private String pageRecordTitle;
    private String lastPageData;
    private int lastSelectedPosition;
    private int lastVisibleItem;
    /** 对应原版 ArticleListFragment$LoadListener（只有 complete()）。 */
    public interface LoadListener {
        void complete();
    }

    private LoadListener loadListener;
    private int loadingPage;
    private int maxLoadedPage;
    private boolean memoryPos = true;
    private String myUrl = "";
    private String nextPageUrl;
    private boolean noHistory;
    private boolean noRecordHistory;
    private View notify_bg;
    private TextView notify_text;
    private String onCloseJS;
    private String onRefreshJS;
    private String originalPageUrl;
    private int page = 1;
    private AppCompatImageView progressView1;
    private MyWebProgress progress_bar;
    private RecyclerView recyclerView;
    private int scrollFlag = -2;
    private RecyclerView.OnScrollListener scrollListener;
    private int scrollPos = -1;
    private SmartRefreshLayout smartRefreshLayout;
    private RelativeLayout webViewContainer;
    private HkWebViewHolder webViewHolder;

    /**
     * 照抄原版 instance(ArticleListRule)：克隆规则并归一化——col_type 空补 movie_3；
     * firstHeader 取首个非空分类组（class→area→year→sort，全空回 "class"）；
     * DTO 再克隆一份，剥 #noHistory#/#noRecordHistory# 标记（#autoPage# 只置历史标记不剥），
     * 四组导航 url 只保留 & 分隔的第一段。
     */
    public HkArticleFragment instance(HkRule rule) {
        HkRule dto;
        try {
            dto = rule.copyRule();
        } catch (Exception e) {
            dto = rule;
        }
        if (TextUtils.isEmpty(dto.getColType())) {
            dto.setColType(HkColType.MOVIE_3.code);
        } else {
            dto.setColType(dto.getColType());
        }
        if (!TextUtils.isEmpty(dto.getClassName())) dto.setFirstHeader("class");
        else if (!TextUtils.isEmpty(dto.getAreaName())) dto.setFirstHeader("area");
        else if (!TextUtils.isEmpty(dto.getYearName())) dto.setFirstHeader("year");
        else if (!TextUtils.isEmpty(dto.getSortName())) dto.setFirstHeader("sort");
        else dto.setFirstHeader("class");
        this.articleListRule = dto;
        try {
            this.articleListRuleDTO = prepareSubPageRule(rule);
            originalPageUrl = rule.getUrl();
            currentPageUrl = rule.getUrl();
            // 标记位看剥标记之前的原始地址（DTO 的地址在 prepareSubPageRule 里已剥过）
            String rawUrl = dto.getUrl();
            if (TextUtils.isEmpty(rawUrl)) {
                noHistory = true;
                noRecordHistory = true;
            } else {
                if (rawUrl.contains("#noHistory#")) noHistory = true;
                if (rawUrl.contains("#noRecordHistory#")) noRecordHistory = true;
                if (rawUrl.contains("#autoPage#")) {
                    noRecordHistory = true;
                    noHistory = true;
                }
            }
        } catch (Exception ignored) {
        }
        return this;
    }

    /**
     * instance() 里 DTO 整备逻辑的静态原样抽取：克隆→归一化→剥取数标记→导航 url 取首段。
     * instance() 本体走这里，不另写第二份解析。
     */
    public static HkRule prepareSubPageRule(HkRule rule) {
        HkRule dto;
        try {
            dto = rule.copyRule();
        } catch (Exception e) {
            dto = rule;
        }
        if (TextUtils.isEmpty(dto.getColType())) {
            dto.setColType(HkColType.MOVIE_3.code);
        } else {
            dto.setColType(dto.getColType());
        }
        if (!TextUtils.isEmpty(dto.getClassName())) dto.setFirstHeader("class");
        else if (!TextUtils.isEmpty(dto.getAreaName())) dto.setFirstHeader("area");
        else if (!TextUtils.isEmpty(dto.getYearName())) dto.setFirstHeader("year");
        else if (!TextUtils.isEmpty(dto.getSortName())) dto.setFirstHeader("sort");
        else dto.setFirstHeader("class");
        HkRule d;
        try {
            d = dto.copyRule();
        } catch (Exception e) {
            d = dto;
        }
        if (!TextUtils.isEmpty(d.getUrl())) {
            if (d.getUrl().contains("#noHistory#")) {
                d.setUrl(d.getUrl().replaceFirst(java.util.regex.Pattern.quote("#noHistory#"), ""));
            }
            if (d.getUrl().contains("#noRecordHistory#")) {
                d.setUrl(d.getUrl().replaceFirst(java.util.regex.Pattern.quote("#noRecordHistory#"), ""));
            }
        }
        if (!TextUtils.isEmpty(d.getAreaUrl())) d.setAreaUrl(d.getAreaUrl().split("&")[0]);
        if (!TextUtils.isEmpty(d.getClassUrl())) d.setClassUrl(d.getClassUrl().split("&")[0]);
        if (!TextUtils.isEmpty(d.getYearUrl())) d.setYearUrl(d.getYearUrl().split("&")[0]);
        if (!TextUtils.isEmpty(d.getSortUrl())) d.setSortUrl(d.getSortUrl().split("&")[0]);
        return d;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_article_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView(view);
        initData();
    }

    /**
     * 照抄原版 initAutoCache（ArticleListFragment.smali:24381）：宿主 HkListActivity 建页时以
     * （页面标题， 规则原始 url）调用（原版 FilmListActivity 以 intent 的 title 与
     * articleListRule.getUrl() 调用，同点位）。链接无 #autoCache#/#cacheOnly# 时键为 null、全链不启用。
     */
    public void initAutoCache(String title, String url) {
        autoCacheMd5 = HkAutoCacheUtil.initAutoCacheMd5(title, url);
        cacheOnly = HkAutoCacheUtil.isCacheOnly(url);
    }

    /**
     * 照抄原版 updateCacheCode（ArticleListFragment.smali:27854）：引擎 cacheCode0 发布的
     * CacheCode 事件。原版守卫为宿主是 FilmListActivity 且未 onPause、autoCacheMd5 非空；
     * 星辰宿主恒为 HkListActivity，等价收敛为页面 resumed 且键非空。
     */
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    public void updateCacheCode(CacheCode event) {
        if (event == null || getActivity() == null || getActivity().isFinishing()) return;
        if (TextUtils.isEmpty(autoCacheMd5)) return;
        if (!isResumed()) return;
        cacheCode = event.getCode();
    }

    /**
     * G1（普查 hk-handover-audit-20261031 T1）：SetPageTitleEvent 的消费方——
     * 此前全树只有发送端（HkJsRuntime.setPageTitle）无人消费，规则声明的片名
     * 进不了标题链，activityTitle() 恒吃导航标题：页函数型详情经线钮进入时
     * 集标题前缀与播放器片名被线名污染（日志「自建4K_2-…」即此链产物）。
     * 守卫同 updateCacheCode：仅 resumed 的页面收（求值发生在前台页面、其
     * fragment 恒 resumed；后台页不收，防别页求值串入）。事件本身不带规则
     * 身份，残余面见 juzhi-seam-fix 账 G1 节。
     */
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    public void onSetPageTitle(com.fongmi.android.tv.api.hk.event.home.SetPageTitleEvent event) {
        if (event == null || getActivity() == null || getActivity().isFinishing()) return;
        if (!isResumed()) return;
        String t = event.getTitle();
        if (TextUtils.isEmpty(t)) return;
        declaredPageTitle = t;
        // 环40：官 SetPageTitleEvent 同步半——宿主 setTitle＋intent "title"
        // 改写为新标题；异步半只动历史记录（按旧标题定位改标题）。
        String oldTitle = intentTitle();
        try {
            getActivity().setTitle(t);
            if (getActivity().getIntent() != null) getActivity().getIntent().putExtra("title", t);
        } catch (Exception ignored) {
        }
        if (articleListRuleDTO != null && articleListRuleDTO.getUrl() != null) {
            String ruleUrl = articleListRuleDTO.getUrl();
            // 环40 路只写 title 不写 time（官 lambda$onSetTitle$10 源行 730-733），走 TitleOnly 口，与 $64 路分开
            Task.execute(() -> HkPageRecordStore.shared().writeHistoryTitleOnly(ruleUrl, oldTitle, t));
        }
    }

    /** 环40：页面标题键（官 MTitle＝宿主 intent 的 "title" extra）。 */
    private String intentTitle() {
        if (getActivity() == null || getActivity().getIntent() == null) return "";
        String t = getActivity().getIntent().getStringExtra("title");
        return t == null ? "" : t;
    }

    /**
     * 环40：官 SetPageParamsEvent 消费（同步半＋异步半）——同步：当前页规则
     * setParams（模型求值读同一 DTO 对象，等价官 fragment.updateParams）；
     * 官第三件「宿主规则串重序列化」咱家 DTO 是宿主规则的副本、无回指，
     * 无法在此改写宿主串（形态差记名）。异步：历史＋收藏两记录改 params。
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSetPageParams(SetPageParamsEvent event) {
        if (event == null || event.getParams() == null) return;
        if (getActivity() == null || getActivity().isFinishing() || !isResumed()) return;
        if (articleListRuleDTO == null) return;
        articleListRuleDTO.setParams(event.getParams());
        String ruleUrl = articleListRuleDTO.getUrl();
        String pageTitle = intentTitle();
        String params = event.getParams();
        Task.execute(() -> {
            HkPageRecordStore store = HkPageRecordStore.shared();
            store.writeHistoryParams(ruleUrl, pageTitle, params);
            store.writeCollectionParams(ruleUrl, pageTitle, params);
        });
    }

    /**
     * 环40：官 SetPagePicEvent 消费——同步：宿主 intent "picUrl" 改写（官同
     * 件；咱家页面图源即此 extra 的消费链）；异步：历史＋收藏两记录改 pic。
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSetPagePic(SetPagePicEvent event) {
        if (event == null || event.getUrl() == null) return;
        if (getActivity() == null || getActivity().isFinishing() || !isResumed()) return;
        if (articleListRuleDTO == null) return;
        try {
            if (getActivity().getIntent() != null) getActivity().getIntent().putExtra("picUrl", event.getUrl());
        } catch (Exception ignored) {
        }
        String ruleUrl = articleListRuleDTO.getUrl();
        String pageTitle = intentTitle();
        String pic = event.getUrl();
        Task.execute(() -> {
            HkPageRecordStore store = HkPageRecordStore.shared();
            store.writeHistoryPic(ruleUrl, pageTitle, pic);
            store.writeCollectionPic(ruleUrl, pageTitle, pic);
        });
    }

    /**
     * 照抄原版 initData（ArticleListFragment.smali:24389 起）：先亮加载态；
     * 有缓存键时后台先灌缓存（lambda$initData$11）；cacheOnly 且已有缓存直接返回不走网络；
     * 否则走模型取数。
     */
    protected void initData() {
        // 环40 进页接线（复核订正 B6 收窄）：收藏记录回读（已有记录的
        // last_chapter_rule/params 覆盖 DTO）＋历史记录登记。收藏记录不在
        // 此产生——官 ViewCollection 只源于真实收藏行为，旧形对每个进页
        // 无条件快照建收藏记录已摘（产生面缺位记名见 HkPageRecordStore）；
        // 历史侧源于浏览行为，登记保留。咱家 DTO 是宿主规则副本，回读只
        // 对本页生效（宿主串不回写，形态差记名见 onSetPageParams）。
        if (articleListRuleDTO != null && articleListRuleDTO.getUrl() != null) {
            // 回读须在首轮取数前同步完成（官进页即以收藏记录内的规则开页）。
            String pageTitle = intentTitle();
            String pic = getActivity() != null && getActivity().getIntent() != null
                    ? getActivity().getIntent().getStringExtra("picUrl") : null;
            try {
                HkPageRecordStore store = HkPageRecordStore.shared();
                store.applyCollectionTo(articleListRuleDTO, pageTitle);
                store.upsertHistoryOnLoad(articleListRuleDTO, pageTitle, pic);
            } catch (Exception ignored) {
            }
        }
        // G1：新一轮页面加载起清声明标题，本页求值若再调 setPageTitle 会重新写入
        declaredPageTitle = "";
        // 首拉窗重置：本轮首发落地前到达的 refreshPage 事件走记下补发（见 onRefreshPage）
        firstLoadSettled = false;
        pendingHostRefreshScrollTop = null;
        loading(true);
        loadingPage = page;
        if (!TextUtils.isEmpty(autoCacheMd5)) {
            Task.execute(() -> loadFromAutoCache());
        }
        if (cacheOnly && getContext() != null
                && HkAutoCacheUtil.hasCache(getContext(), autoCacheMd5)) {
            return;
        }
        articleListModel.params(requireContext(), page, false, articleListRuleDTO,
                        notLoadPreRule(), isFromHistory())
                .process("ARTICLE_LIST_NEW", this);
    }

    /**
     * 照抄原版 lambda$initData$11（ArticleListFragment.smali:14665）：后台读页面缓存；
     * 缓存带 code 时先经引擎求值重建列表（失败/无产出回落缓存 data），
     * data 非空以 fromCache=true 回灌——回灌口已有守卫，网络结果先到时不覆盖。
     */
    private void loadFromAutoCache() {
        Context context = getContext();
        if (context == null) return;
        HkAutoCacheUtil.PageCache cache = HkAutoCacheUtil.getCache(context, autoCacheMd5);
        if (cache == null) return;
        String code = cache.getCode();
        if (!TextUtils.isEmpty(code) && articleListRuleDTO != null) {
            try {
                HkArticleListModel.EngineBridge bridge = HkArticleListModel.getEngineBridge();
                List<HkItem> rebuilt = bridge == null ? null
                        : bridge.evalCacheCode(articleListRuleDTO, code, articleListRuleDTO.getUrl());
                if (rebuilt != null && !rebuilt.isEmpty()) cache.setData(rebuilt);
            } catch (Throwable ignored) {
            }
        }
        List<HkItem> data = cache.getData();
        if (data != null && !data.isEmpty()) {
            bindArrayToView("ARTICLE_LIST_NEW", data, true);
        }
    }

    /** 照抄原版 initView：找齐控件、装 GridLayoutManager(60)+SpanSizeLookup、刷新/加载更多、FastScroller、通知条、进度条。 */
    protected void initView(View root) {
        recyclerView = root.findViewById(R.id.frag_article_list_recycler_view);
        recyclerView.post(() -> {
            View bg = root.findViewById(R.id.bg);
            if (bg != null && getContext() != null) {
                bgViewHeight = pxToDp(bg.getMeasuredHeight());
            }
        });
        progressView1 = root.findViewById(R.id.progress_image_view1);
        if (progressView1 != null && progressView1.getDrawable() instanceof Animatable) {
            ((Animatable) progressView1.getDrawable()).start();
        }
        webViewContainer = root.findViewById(R.id.webview_container);
        gridLayoutManager = new GridLayoutManager(getContext(), 60);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (adapter == null) return 60;
                if (recyclerView.getAdapter() != adapter) return 60;
                try {
                    int viewType = adapter.getItemViewType(position);
                    if (viewType == -10) return 0;
                    if (getActivity() != null && getActivity().getResources().getConfiguration()
                            .orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                        return HkColType.landSpanCountByItemType(viewType);
                    }
                    return HkColType.spanCountByItemType(viewType);
                } catch (Exception e) {
                    return 60;
                }
            }
        });
        recyclerView.setLayoutManager(gridLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        articleListModel = new HkArticleListModel()
                .withUrlParseCallBack(url -> myUrl = url);
        smartRefreshLayout = root.findViewById(R.id.refresh_layout);
        cannotRefresh = articleListRule != null
                && !TextUtils.isEmpty(articleListRule.getUrl())
                && articleListRule.getUrl().contains("#noRefresh#");
        if (cannotRefresh) {
            smartRefreshLayout.setEnableRefresh(false);
        } else {
            smartRefreshLayout.setOnRefreshListener(refreshLayout -> refresh());
        }
        smartRefreshLayout.setEnableAutoLoadMore(true);
        smartRefreshLayout.setOnLoadMoreListener(refreshLayout -> onLoadMore());
        fastScroller = root.findViewById(R.id.frag_article_list_recycler_scroller);
        fastScroller.attachRecyclerView(recyclerView);
        initAdapter();
        fastScroller.setTouchTargetWidth(10);
        fastScroller.setMarginLeft(10);
        fastScroller.setMinItemCount(isReadTheme ? 0x1e8480 : 200);
        fastScroller.setBarColor(androidx.core.content.ContextCompat.getColor(requireContext(),
                android.R.color.transparent));
        android.graphics.drawable.Drawable handle = androidx.core.content.ContextCompat.getDrawable(
                requireContext(), R.drawable.fastscroll_handle);
        fastScroller.setDrawable(handle, handle);
        fastScroller.setMaxScrollHandleHeight(dpToPx(40));
        fastScroller.setPressedListener(pressed -> onFastScrollPressed(pressed));
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
        notify_bg = root.findViewById(R.id.notify_bg);
        if (notify_bg != null) {
            notify_text = notify_bg.findViewById(R.id.notify_text);
            View notifyQueue = notify_bg.findViewById(R.id.notify_queue);
            if (notifyQueue != null) notifyQueue.setOnClickListener(v -> onNotifyQueueClick());
            notify_bg.setOnClickListener(v -> onNotifyClick());
            notify_bg.setOnLongClickListener(v -> onNotifyLongClick());
        }
        progress_bar = root.findViewById(R.id.progress_bar);
        progress_bar.setColor(androidx.core.content.ContextCompat.getColor(requireContext(),
                R.color.progress_blue));
        if (scrollListener != null) {
            recyclerView.addOnScrollListener(scrollListener);
            fastScroller.setScrollListener(offset -> onFastScrollOffset(offset));
        }
    }

    /**
     * 照抄原版 finishParse(DestroyEvent)：WebView 嗅探桥 fy_bridge_app.finishParse 投递的事件，
     * 页面可见时按 mode 路由到对应 Parser 的 finishParse（ticket 在 Parser 内校验）。
     */
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    public void finishParse(HkDestroyEvent event) {
        if (getActivity() != null && !getActivity().isFinishing() && getUserVisibleHint()) {
            // 路由本体与 Router 同步路共用 HkIWebViewParser.dispatchFinishParse（双承载收归后唯一一份）
            HkIWebViewParser.dispatchFinishParse(getContext(), event);
        }
    }

    /**
     * 照抄原版 onRefreshPage(OnRefreshPageEvent)：脚本调 refreshPage() 发来的刷新事件；
     * 本页可见时按事件的 scrollTop 回顶并整页重取（宿主暂停/不可见时忽略，同原版）。
     */
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    public void onRefreshPage(com.fongmi.android.tv.api.hk.event.home.OnRefreshPageEvent event) {
        if (event == null || getActivity() == null || getActivity().isFinishing()) return;
        if (!isResumed() || !getUserVisibleHint()) {
            // 守卫的本意是宿主暂停/不可见时忽略；但「首拉未收尾、只差 resumed
            // 这一步」是页面正在装载的技术窗——「首跑只做装配＋refreshPage、
            // 数据在刷新后那一跑才出」写法的规则（聚合框架常见）在这窗里发的
            // 刷新若被吞，首拉必然空转、下拉才出（账 hk-import-firstload-empty-dig
            // §五候选 2）。此窗内不吞：记下 scrollTop，首拉落地后补发一次
            // （见 onFirstLoadSettled/drainPendingHostRefresh）。首拉已收尾或
            // 本页不可见时被挡，仍按原版忽略，不记。
            if (!isResumed() && getUserVisibleHint() && !firstLoadSettled) {
                pendingHostRefreshScrollTop = event.isScrollTop();
                SpiderDebug.log("hk", "onRefreshPage 首拉窗记下待补发 rule=%s scrollTop=%s",
                        articleListRule == null ? "" : articleListRule.getTitle(), event.isScrollTop());
                return;
            }
            SpiderDebug.log("hk", "onRefreshPage 守卫拦截 rule=%s resumed=%s visible=%s",
                    articleListRule == null ? "" : articleListRule.getTitle(), isResumed(), getUserVisibleHint());
            return;
        }
        refreshFromHost(event.isScrollTop());
    }

    /** 首拉落地（bind/error 任一收尾）：置旗并尝试补发首拉窗记下的 refreshPage。 */
    private void onFirstLoadSettled() {
        if (firstLoadSettled) return;
        firstLoadSettled = true;
        // 本方法可能在回调线程被调，补发涉 UI 操作，统一投 UI 线程再判可见性
        if (pendingHostRefreshScrollTop != null) {
            if (getActivity() != null) getActivity().runOnUiThread(this::drainPendingHostRefresh);
            else drainPendingHostRefresh();
        }
    }

    /** 补发记下的 refreshPage（只发一次；页面须已 resumed 且可见，否则留到 onResume 再试）。 */
    private void drainPendingHostRefresh() {
        if (pendingHostRefreshScrollTop == null || !firstLoadSettled) return;
        if (!isResumed() || !getUserVisibleHint() || getActivity() == null
                || getActivity().isFinishing()) return;
        boolean scrollTop = pendingHostRefreshScrollTop;
        pendingHostRefreshScrollTop = null;
        SpiderDebug.log("hk", "onRefreshPage 首拉窗补发 rule=%s scrollTop=%s",
                articleListRule == null ? "" : articleListRule.getTitle(), scrollTop);
        refreshFromHost(scrollTop);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 首拉若在本页 resumed 之前就已落地，记下的 refreshPage 在此补发
        drainPendingHostRefresh();
    }

    /**
     * 照原版 onBack(OnBackEvent)（ArticleListFragment:355-373）：规则 JS 调 back() 发来的返回事件；
     * 只认 HkListActivity 宿主且宿主未暂停，refreshPage 时先 setResult 回传两旗，再触发宿主返回弹一页。
     */
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    public void onBack(OnBackEvent event) {
        Activity activity = getActivity();
        // 限制能生效的 Activity
        if (activity == null || activity.getClass() != HkListActivity.class) {
            return;
        }
        // 限制调用一次只能返回一次
        HkListActivity hkListActivity = (HkListActivity) activity;
        if (hkListActivity.isOnPause()) {
            return;
        }
        if (event.isRefreshPage()) {
            Intent intentData = new Intent();
            intentData.putExtra("refreshPage", event.isRefreshPage()); // 封装需要返回的数据
            intentData.putExtra("scrollTop", event.isScrollTop());
            activity.setResult(Activity.RESULT_OK, intentData);
        }
        activity.onBackPressed();
    }

    /**
     * 照抄原版 onRefreshWebView(OnRefreshWebViewEvent)：行内 WebView 在场时 setUrl + loadUrl 刷新目标页；
     * http 链接再显示顶部进度（原版看 X5Extra.showProgress，星辰按 HkX5Extra 默认 true 处理）。
     */
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    public void onRefreshWebView(HkDetailBridge.RefreshWebViewEvent event) {
        if (event == null) return;
        applyRefreshWebView(event.url);
    }

    /** 规则 JS 调 refreshX5WebView(url) 的订阅（8.83 OnRefreshWebViewEvent 同一消费面，来源不同）。 */
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    public void onRefreshWebViewFromJs(com.fongmi.android.tv.api.hk.event.rule.RefreshWebViewEvent event) {
        if (event == null) return;
        applyRefreshWebView(event.getUrl());
    }

    /**
     * 规则 JS 调 refreshReadData(data) 的订阅（8.83 ArticleListFragment.refreshReadData，
     * smali:27506 源行 5395-5421）：门＝activity 存活且 !isFinishing（咱以
     * isResumed 等价承载）、isReadTheme、data.id 与本页规则标题相等；命中后
     * 依次：title 非空支（$64 持久化半，见下）、nextPage 非空更新
     * nextPageUrl、currentUrl 非空更新 myUrl（与原版同序同项）。
     * isReadTheme 门此前因字段恒 false 未加（假接防线）；本批已由宿主
     * HkListActivity 在建 Fragment 时按 FilmListActivity.isReadTheme 口径
     * （url 含 #readTheme# 或 #autoReader#）调 setReadTheme 注入，门就此转真。
     * title 支的 read_chapter 显示半无落点：阅读底部栏（view_read_bottom）
     * 未移植、read_chapter View 不在本页装配里——该半记名；$64 持久化半
     * （以 currentPageUrl 找历史/收藏、标题改章标题、time 刷新）在咱家由
     * HkPageRecordStore 承载（环40 载体），若与显示半同门则永不生效、
     * 等于假接，故只摘显示半、$64 半按 title 非空触发（与本订阅旧注的
     * 处理口径一致）。咱载体以（url＋标题）为键：记录定位键随 $17/$64
     * 改写漂移，本页以 pageRecordUrl/pageRecordTitle 两字段追踪（见下）。
     */
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    public void onRefreshReadData(com.fongmi.android.tv.api.hk.event.rule.RefreshReadDataEvent event) {
        if (!isResumed() || event == null || articleListRule == null) return;
        if (!isReadTheme) return;
        if (!TextUtils.equals(articleListRule.getTitle(), event.getId())) return;
        if (HkStringUtil.isNotEmpty(event.getTitle())) {
            // lambda$refreshReadData$64（HeavyTaskUtil.postTaskToQueue）
            String keyUrl = pageRecordUrl();
            String keyTitle = pageRecordTitle();
            String chapterTitle = event.getTitle();
            Task.execute(() -> {
                HkPageRecordStore store = HkPageRecordStore.shared();
                store.writeHistoryTitle(keyUrl, keyTitle, chapterTitle);
                store.writeCollectionMTitle(keyUrl, keyTitle, chapterTitle);
            });
            pageRecordTitle = chapterTitle;
        }
        if (HkStringUtil.isNotEmpty(event.getNextPage())) nextPageUrl = event.getNextPage();
        if (HkStringUtil.isNotEmpty(event.getCurrentUrl())) myUrl = event.getCurrentUrl();
        // 环42 映射层累积：#autoReader# 链回告并入阅读组件章节表（HkAutoReaderMapper）
        if (articleListRuleDTO != null && articleListRuleDTO.getUrl() != null
                && articleListRuleDTO.getUrl().contains("#autoReader#")) {
            HkAutoReaderMapper.applyChainEvent(autoReaderChapters,
                    event.getCurrentUrl(), event.getTitle(), event.getNextPage());
        }
    }

    /** 环42：阅读记录在咱载体（url＋标题复合键）中的当前定位 url：初始＝DTO url，随 $17 翻章改写漂移。 */
    private String pageRecordUrl() {
        if (pageRecordUrl == null) {
            pageRecordUrl = articleListRuleDTO == null ? "" : articleListRuleDTO.getUrl();
        }
        return pageRecordUrl;
    }

    /** 环42：阅读记录在咱载体中的当前定位标题：初始＝页面标题，随 $64 章标题改写漂移。 */
    private String pageRecordTitle() {
        if (pageRecordTitle == null) pageRecordTitle = intentTitle();
        return pageRecordTitle;
    }

    /**
     * 环42 映射层组装口（实况订正，批 C2 复核）：组装段已建、出口未接
     * 记名——本法全树零调用，组装出的会话暂无消费方。官形是规则在
     * 列表内直读，咱 hk 列表无阅读主题渲染面，造触发钮＝自创 UI，
     * 故不补自创触发点。上方 onRefreshReadData 里的链回告累积段
     * （applyChainEvent）已接且在跑、翻页本体与 $17/$64 等已接通段
     * 不受此记名影响。组装形：HkAutoReaderMapper.buildSession →
     * ReadingGate.fromHkNovel（正文按 ruleTitle＋章 url 懒取）。
     * 只供链 A 规则形（规则 type＝小说、章 url 是规则可再跑出正文的
     * 形）；链 B 网页形（章 url 为普通网页、正文靠 Readability）与
     * startReader 合成形（无真实规则名）不送组件、回 null
     * （dig2 §8.3 记名段）。无累积回 null。
     */
    public com.fongmi.android.tv.read.ReadingSession buildAutoReaderSession() {
        if (autoReaderChapters.isEmpty() || articleListRule == null) return null;
        if (!com.fongmi.android.tv.read.ReadingGate.isHkNovelRule(articleListRule.getTitle())) return null;
        return HkAutoReaderMapper.buildSession(articleListRule.getTitle(), activityTitle(),
                autoReaderChapters, myUrl);
    }

    /**
     * 规则 JS 调 refreshVideoSource(url, headers) 的订阅（8.83 ArticleListFragment.onRefreshVideoUrl
     * → ArticleWebViewHolder.updateVideoUrl）：把新视频地址与请求头存进 holder 的视频面；
     * 当前正展示视频条目时按新址立即重载（holder 建面时对视频条目本就直载 videoUrl，此处与之同义）。
     * 原版 updateVideoUrl 另有悬浮播放器重弹（floatVideoController.show），星辰无悬浮播放器件，不做。
     * 原版门是 !isOnPause 且 holder 有 WebView，星辰同口径用 isResumed。
     */
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    public void onRefreshVideoUrl(com.fongmi.android.tv.api.hk.event.rule.RefreshVideoUrlEvent event) {
        if (!isResumed() || event == null || webViewHolder == null) return;
        webViewHolder.setVideoUrl(event.getUrl());
        webViewHolder.setVideoHeaders(event.getHeaders());
        if (webViewHolder.isVideoItem() && webViewHolder.getWebView() != null
                && HkStringUtil.isNotEmpty(event.getUrl())) {
            if (event.getHeaders() != null && !event.getHeaders().isEmpty()) {
                webViewHolder.getWebView().loadUrl(event.getUrl(), event.getHeaders());
            } else {
                webViewHolder.getWebView().loadUrl(event.getUrl());
            }
        }
    }

    /**
     * 照原版 ArticleListFragment.onBackPressed（8.83 源码 2320 行）：行内 WebView 在场、
     * 能后退且条目 extra 允许后退（canBack）时先让 WebView 后退并消费本次返回；
     * 其余情形不消费，交回宿主走原有返回（出页弹栈）。由 HkListActivity.onBackPressed 分发。
     */
    public boolean onBackPressed() {
        if (webViewHolder != null && webViewHolder.getWebView() != null) {
            if (webViewHolder.getWebView().canGoBack()) {
                if (webViewHolder.getX5Extra() != null && webViewHolder.getX5Extra().isCanBack()) {
                    webViewHolder.getWebView().goBack();
                    return true;
                }
            }
        }
        return false;
    }

    private void applyRefreshWebView(String url) {
        if (!isResumed()) return;
        if (webViewHolder == null || webViewHolder.getWebView() == null) return;
        webViewHolder.setUrl(url);
        webViewHolder.getWebView().loadUrl(url);
        if (HkStringUtil.isNotEmpty(url) && url.startsWith("http")
                && progress_bar != null) {
            progress_bar.show();
        }
    }

    /**
     * 原版 OnRefreshX5Height 订阅（ArticleListFragment.OnRefreshX5Height）：规则 JS 调 refreshX5Desc(desc) 后，
     * 先按 desc 更新 holder 的模式/extra，再落高度——FLOAT 走页级容器定高（未挂载则挂载，不触发加载/刷新，
     * 对应原版 updateWebViewHeight 的职责）；LIST 把新 desc 同步进行内条目并重绑该行，行高由 bind 时重算。
     * 此前宿主侧无此订阅且 JS 侧是空实现，网页型规则恒停在默认小高（只剩顶部一小条）。
     */
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    public void onRefreshX5Desc(com.fongmi.android.tv.api.hk.event.rule.RefreshX5DescEvent event) {
        if (!isResumed() || event == null || webViewHolder == null) return;
        updateModeByDesc(event.getDesc());
        if (webViewHolder.getMode() == HkWebViewHolder.Mode.FLOAT) {
            applyFloatWebViewHeight();
        } else {
            if (webViewContainer != null && webViewContainer.getChildCount() > 0) {
                webViewContainer.removeAllViews();
            }
            if (adapter != null) {
                List<HkItem> items = adapter.getList();
                for (int i = 0; i < items.size(); i++) {
                    HkItem item = items.get(i);
                    if (item != null && HkColType.byCode(item.getType()) == HkColType.X5_WEB_VIEW) {
                        item.setDesc(event.getDesc());
                        adapter.notifyItemChanged(i);
                        break;
                    }
                }
            }
        }
    }

    /** FLOAT 模式下按 holder 当前 extra 给页级容器定高；WebView 未挂载时从旧父级摘下挂入。与 updateWebView 的 FLOAT 支同款，但不碰地址加载。 */
    private void applyFloatWebViewHeight() {
        android.webkit.WebView wv = webViewHolder.getWebView();
        if (wv == null || webViewContainer == null || getContext() == null) return;
        int heightDp = HkWebViewHolder.getHeightByExtra(getContext(),
                webViewHolder.getUrl(), webViewHolder.getExtra());
        int heightPx = heightDp < 0 ? heightDp : dpToPx(heightDp);
        android.view.ViewGroup.LayoutParams lp = webViewContainer.getLayoutParams();
        if (webViewContainer.getChildCount() <= 0) {
            lp.height = heightPx;
            webViewContainer.setLayoutParams(lp);
            webViewHolder.setHeight(lp.height);
            if (wv.getParent() != null) {
                ((android.view.ViewGroup) wv.getParent()).removeView(wv);
            }
            webViewContainer.addView(wv);
        } else if (lp.height != heightPx) {
            lp.height = heightPx;
            webViewContainer.setLayoutParams(lp);
            webViewHolder.setHeight(heightPx);
        }
    }

    /** 照抄原版 initAdapter。 */
    private void initAdapter() {
        adapter = new HkArticleAdapter(requireContext(), recyclerView, new ArrayList<>()) {
        };
        adapter.setImmersiveTheme(immersiveTheme);
        fastScroller.attachAdapter(adapter);
        adapter.setOnItemClickListener(new HkArticleAdapter.OnItemClickListener() {
            @Override
            public void onClassClick(View view, String url, String desc) {
                HkArticleFragment.this.onClassClick(view, url, desc);
            }

            @Override
            public void onClick(View view, int position) {
                onItemClick(view, position);
            }

            @Override
            public void onCodeClick(View view, int position, String code) {
                HkArticleFragment.this.onCodeClick(view, position, code);
            }

            @Override
            public void onLoadMore(View view, int position) {
                HkArticleFragment.this.onLoadMoreFooter(view, position);
            }

            @Override
            public void onLongClick(View view, int position) {
                HkArticleFragment.this.onItemLongClick(view, position);
            }

            @Override
            public void onUrlClick(View view, int position, String url) {
                onItemUrlClick(view, position, url);
            }
        });
        // 宿主接线（适配器钩子）：规则信息与引擎/建议/富文本/行内 WebView
        if (articleListRuleDTO != null) {
            adapter.setPageUrl(articleListRuleDTO.getUrl());
            adapter.setRuleTitle(articleListRuleDTO.getTitle());
            adapter.setRuleHeaderInfo(articleListRuleDTO.getUrl(), articleListRuleDTO.getFirstHeader());
        }
        adapter.setWebViewHolder(webViewHolder);
        // 照原版 initAdapter 的三钩子接线：输入 onChange 求值（引擎单例）、百度建议词、富文本动作
        adapter.setOnInputEvalListener((onChangeJs, text) -> {
            HkRule r = articleListRuleDTO;
            if (r == null) return;
            try {
                com.fongmi.android.tv.api.hk.HkJsRuntime rt = HkEngineManager.runtimeFor(r);
                HkEngineManager.ensureInit(rt);
                rt.evalJS(com.fongmi.android.tv.api.hk.HkJsRuntime.getMyRule(r) + onChangeJs, text);
            } catch (Exception e) {
                Log.e(TAG, "hk error", e);
            }
        });
        adapter.setSuggestLoader(HkSuggestModel::loadFromRemote);
        adapter.setOnRichTextActionListener(new HkArticleAdapter.OnRichTextActionListener() {
            @Override
            public void onImageClick(java.util.List<String> urls, int position) {
                if (detailBridge != null) detailBridge.showImageViewer(urls, position, null, null);
            }

            @Override
            public boolean checkCodeImport(String trimmedText) {
                return HkDetailBridge.hooks != null
                        && HkDetailBridge.hooks.checkText(getActivity(), trimmedText);
            }
        });
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(adapter.getDividerItem());
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                // 照原版 Fragment$6：滚动时记录最后可见位置（其余为阅读进度跟踪，阅读子系统不移植）
                if (gridLayoutManager != null) {
                    lastVisibleItem = gridLayoutManager.findLastVisibleItemPosition();
                }
                onListScrolled(dx, dy);
            }
        });
    }

    /** 宿主承接原版 OnRefreshPageEvent（子页以请求码 2 返回 refreshPage 时）：scrollTop 先回顶，再刷新。 */
    public void refreshFromHost(boolean scrollTop) {
        if (scrollTop && recyclerView != null) recyclerView.scrollToPosition(0);
        refresh();
    }

    /** 照抄原版 refresh()：回第一页；onRefreshJS 非空时先在后台跑该 JS（切片 2 补全后续），否则直接走模型。 */
    private void refresh() {
        page = 1;
        loading(true);
        if (onRefreshJS != null && !onRefreshJS.isEmpty()) {
            Task.execute(() -> runOnRefreshJs());
            return;
        }
        loadingPage = page;
        articleListModel.params(requireContext(), page, true, articleListRuleDTO,
                        notLoadPreRule(), isFromHistory())
                .process("ARTICLE_LIST_NEW", this);
    }

    /** 照抄原版 loading(boolean)：结束时收刷新/加载圈；开始且在第一页时亮顶部 AVD 进度线。 */
    @Override
    public void loading(boolean show) {
        if (!show) {
            try {
                if (smartRefreshLayout != null) {
                    smartRefreshLayout.finishRefresh(true);
                    smartRefreshLayout.finishLoadMore(true);
                }
            } catch (Exception ignored) {
            }
        }
        isLoading = show;
        try {
            if (show) {
                if (page == 1) {
                    progressView1.setVisibility(View.VISIBLE);
                    ((Animatable) progressView1.getDrawable()).start();
                }
            } else {
                progressView1.setVisibility(View.INVISIBLE);
                ((Animatable) progressView1.getDrawable()).stop();
            }
        } catch (Exception ignored) {
        }
    }

    // —— BaseCallback 数据回灌（切片 2：照 ArticleListFragment.bindArrayToView + lambda$bindArrayToView$43 转写）——

    /** 宿主回调：对应原版宿主 MainActivity.onChildChanged 与 ArticleListIsland.hideLoading（宿主才有）。 */
    public interface HostListener {
        void onChildChanged(HkArticleFragment fragment, boolean hasWebView);

        default void hideLoading() {
        }

        /** 对应原版 memoryPosition 的记录本体（历史/续看位置），宿主阶段承接。 */
        default void onMemoryPosition(int position) {
        }

        /** 对应原版 func://background 时 FilmListActivity.background()，宿主阶段承接。 */
        default void onBackground() {
        }

        /** 对应原版 showLastClick 首调的历史恢复查询：先 ViewCollection（CUrl+MTitle）后 ViewHistory（url+title+二级列表），返回 lastClick 串，无则 null。 */
        default String onQueryLastClick(String ruleUrl, String title) {
            return null;
        }

        /** 对应原版 FilmListActivity.showLastClick(LastClickShowEvent)：宿主侧的上次点击提示呈现。 */
        default void onShowLastClick(String lastClick) {
        }

        /** 对应原版 updateAutoPageLastClick：#autoPage# 续页时宿主侧记录父页 lastClick。 */
        default void onAutoPageLastClick(AutoPageData data, HkItem item, int globalPos) {
        }
    }

    private HostListener hostListener;
    private boolean mainHost;
    private boolean listHost;
    private int cacheMergeState;
    private String cacheCode;
    /** G1：规则经 setPageTitle 声明的页面标题（SetPageTitleEvent 消费方写入），activityTitle() 优先取它。 */
    private String declaredPageTitle = "";
    /** #autoCache#/#cacheOnly# 页缓存键（原版 ArticleListFragment.autoCacheMd5）；无标记的页面为 null。 */
    private String autoCacheMd5;
    /** 原版 ArticleListFragment.cacheOnly：#cacheOnly# 命中缓存时 initData 跳过网络加载。 */
    private boolean cacheOnly;

    public void setHostListener(HostListener hostListener) {
        this.hostListener = hostListener;
        this.mainHost = hostListener != null;
    }

    /** 对应原版 activity instanceof FilmListActivity 的判定（子页面宿主时为 true）。 */
    public void setListHost(boolean listHost) {
        this.listHost = listHost;
    }

    /** 照 8.83 ArticleListFragment.hasCls（源行 5011）：任一为空回 false；否则两边各包一层空格后包含判定（空格多值整词匹配）。 */
    private static boolean hasCls(String itemCls, String cls) {
        if (TextUtils.isEmpty(itemCls) || TextUtils.isEmpty(cls)) return false;
        return (" " + itemCls + " ").contains(" " + cls + " ");
    }

    /**
     * 照 8.83 ArticleListFragment.onItemModify 全支（源行 5019 起，lambda58/59/60）：
     * 前置静默门（宿主 null/finishing、adapter null、列表空 → return）后四路——
     * DELETE+cls 走 lambda58（hasCls 两趟删＋通知形态）；DELETE+list 走 lambda59
     * 批量删后 return；ADD/UPDATE/DELETE 单条走 lambda60（锚扫 adapter 首命中、
     * 无命中静默 return）。锚恒为 extra.id：ADD/UPDATE 取 event.anchorId、
     * DELETE 单条取桩条目 extra.id，url 不参与（环30 D30-1 补全：此前只有
     * DELETE+cls 一支，展示后 updateItem/addItem/deleteItem 屏上恒无-op）。
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onItemModify(ItemModifyEvent event) {
        if (event == null || event.getAction() == null) return;
        if (getActivity() == null || getActivity().isFinishing()) return;
        if (adapter == null || adapter.getList().isEmpty()) return;
        if (event.getAction() == ItemModifyEvent.Action.DELETE
                && !TextUtils.isEmpty(event.getCls())) {
            onItemModifyDeleteByCls(event.getCls());
            return;
        }
        if (event.getAction() == ItemModifyEvent.Action.DELETE
                && event.getList() != null && !event.getList().isEmpty()) {
            // lambda59 → batchDelete（复核订正 B5，见 onItemModifyBatchDelete）。
            onItemModifyBatchDelete(event.getList());
            return;
        }
        // lambda60 入口：定锚。
        String anchor;
        if (event.getAction() == ItemModifyEvent.Action.DELETE) {
            Object stub = event.getArticleList();
            anchor = stub instanceof HkItem ? ((HkItem) stub).getExtra("id") : "";
        } else {
            anchor = event.getAnchorId() == null ? "" : event.getAnchorId();
        }
        if (TextUtils.isEmpty(anchor)) return;
        List<HkItem> list = adapter.getList();
        int index = indexOfExtraId(list, anchor);
        if (index < 0) return;
        try {
            if (index >= list.size()) return;
            switch (event.getAction()) {
                case ADD: {
                    List<HkItem> adds = new ArrayList<>();
                    if (event.getList() != null) {
                        for (Object o : event.getList()) if (o instanceof HkItem) adds.add((HkItem) o);
                    } else if (event.getArticleList() instanceof HkItem) {
                        adds.add((HkItem) event.getArticleList());
                    }
                    if (adds.isEmpty()) return;
                    int at = event.isAfter() ? index + 1 : index;
                    list.addAll(at, adds);
                    if (adds.size() == 1) adapter.notifyInserted(at);
                    else adapter.notifyRangeInserted(at, adds.size());
                    break;
                }
                case DELETE: {
                    HkItem removed = list.remove(index);
                    adapter.notifyRemoved(removed, index);
                    break;
                }
                case UPDATE: {
                    HkItem target = list.get(index);
                    if (!anchor.equals(target.getExtra("id"))) return;
                    Map<String, Object> m = event.getSourceMap();
                    if (m != null) {
                        applyUpdateFields(target, m);
                    } else if (event.getArticleList() instanceof HkItem) {
                        applyUpdateItem(target, (HkItem) event.getArticleList());
                    }
                    adapter.notifyChanged(index);
                    break;
                }
                default:
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "onItemModify", e);
        }
    }

    /**
     * lambda59 的 batchDelete（官 ArticleListFragment.batchDelete 亲读形，
     * 复核订正 B5）：桩条目 extra.id 先成集合，再迭代 adapter 表删全部命中
     * （同 id 重复条目全删，旧形逐桩扫首命中只删一条）；通知照官——先算
     * 命中位表，位表连续成段时 notifyRangeRemoved(first, count)，否则
     * notifyDataChanged；零命中静默无-op。
     */
    private void onItemModifyBatchDelete(List<?> stubs) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Object o : stubs) {
            if (!(o instanceof HkItem)) continue;
            String id = ((HkItem) o).getExtra("id");
            if (!TextUtils.isEmpty(id)) ids.add(id);
        }
        if (ids.isEmpty()) return;
        List<HkItem> list = adapter.getList();
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            HkItem it = list.get(i);
            if (it != null && ids.contains(it.getExtra("id"))) positions.add(i);
        }
        if (positions.isEmpty()) return;
        boolean contiguous = true;
        for (int i = 1; i < positions.size(); i++) {
            if (positions.get(i) - positions.get(i - 1) != 1) {
                contiguous = false;
                break;
            }
        }
        Iterator<HkItem> iter = list.iterator();
        while (iter.hasNext()) {
            HkItem it = iter.next();
            if (it != null && ids.contains(it.getExtra("id"))) iter.remove();
        }
        if (contiguous) adapter.notifyRangeRemoved(positions.get(0), positions.size());
        else adapter.notifyDataChanged();
    }

    /** adapter 表按 extra.id 扫首命中（官 lambda60 锚扫表口径）。 */
    private static int indexOfExtraId(List<HkItem> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            HkItem it = list.get(i);
            if (it != null && id.equals(it.getExtra("id"))) return i;
        }
        return -1;
    }

    /**
     * lambda60 的 UPDATE 字段覆盖（源 Map 版）：键存在且值非 null 才覆盖，空串
     * 是有效覆盖值；pic 按键存在序 img ＞ pic ＞ pic_url；extra 三态同 convert。
     * 与引擎侧 HkJsRuntime.applyUpdateFields 同口径（两层各持一份、逐键对齐）。
     */
    private static void applyUpdateFields(HkItem item, Map<String, Object> m) {
        if (m.containsKey("title") && m.get("title") != null) item.setTitle(String.valueOf(m.get("title")));
        if (m.containsKey("url") && m.get("url") != null) item.setUrl(String.valueOf(m.get("url")));
        String picKey = m.containsKey("img") ? "img" : m.containsKey("pic") ? "pic" : m.containsKey("pic_url") ? "pic_url" : null;
        if (picKey != null && m.get(picKey) != null) item.setPic(String.valueOf(m.get(picKey)));
        if (m.containsKey("desc") && m.get("desc") != null) item.setDesc(String.valueOf(m.get("desc")));
        if (m.containsKey("content") && m.get("content") != null) item.setContent(String.valueOf(m.get("content")));
        if (m.containsKey("col_type") && m.get("col_type") != null) item.setColType(String.valueOf(m.get("col_type")));
        if (m.containsKey("extra") && m.get("extra") != null) {
            Object ex = m.get("extra");
            item.setExtra(ex instanceof String ? (String) ex
                    : new com.google.gson.Gson().toJson(ex));
        }
    }

    /** lambda60 的 UPDATE 字段覆盖（条目版回退：无源 Map 时按条目非空字段覆盖）。 */
    private static void applyUpdateItem(HkItem target, HkItem src) {
        if (!TextUtils.isEmpty(src.getTitle())) target.setTitle(src.getTitle());
        if (!TextUtils.isEmpty(src.getUrl())) target.setUrl(src.getUrl());
        if (!TextUtils.isEmpty(src.getPic())) target.setPic(src.getPic());
        if (!TextUtils.isEmpty(src.getDesc())) target.setDesc(src.getDesc());
        if (!TextUtils.isEmpty(src.getContent())) target.setContent(src.getContent());
        if (!TextUtils.isEmpty(src.getColType())) target.setColType(src.getColType());
        if (!TextUtils.isEmpty(src.getExtra())) target.setExtra(src.getExtra());
    }

    /** lambda58：按 cls 删（两趟扫描＋通知形态照官，见 onItemModifyDeleteByCls 前身 G37 落码）。 */
    private void onItemModifyDeleteByCls(String cls) {
        List<HkItem> list = adapter.getList();
        int first = -1;
        int run = 0;
        boolean broken = false;
        for (int i = 0; i < list.size(); i++) {
            HkItem it = list.get(i);
            if (it == null || !hasCls(it.getExtra("cls"), cls)) continue;
            if (first < 0) first = i;
            if (i - first != run) {
                broken = true;
                break;
            }
            run++;
        }
        if (first < 0) return;
        boolean flex = !broken && adapter.hasFlex(first, run);
        Iterator<HkItem> iter = list.iterator();
        while (iter.hasNext()) {
            HkItem it = iter.next();
            if (it != null && hasCls(it.getExtra("cls"), cls)) iter.remove();
        }
        if (!broken && run > 0 && !flex) adapter.notifyRangeRemoved(first, run);
        else adapter.notifyDataChanged();
    }

    /**
     * 照 8.83 FilmListActivity.onSetLastChapterRule（源行 826-834）：setLastChapterRule
     * 事件由此消费——页面存活（未暂停/未 finishing）、rule 非空、当前页规则在手时，
     * 把当前页规则的 last_chapter_rule 改成本次值（G52；此前全树只有发送点、
     * 无消费方，事件发出去就丢）。官宿主在 Activity、本宿主当前页规则
     * （articleListRuleDTO）在 Fragment 手里，落点随宿主分工落本面。
     * 环40 补异步持久化半：官写回收藏记录（CUrl=规则 url＋MTitle=intent
     * "title" 定位，params 非空才解析改 last_chapter_rule 再整串存回）——
     * 咱家由 HkPageRecordStore 收藏记录同形承载（G52 记名就此关账）。
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSetLastChapterRule(SetPageLastChapterRuleEvent event) {
        if (event == null || event.getRule() == null) return;
        if (getActivity() == null || getActivity().isFinishing() || !isResumed()) return;
        if (articleListRuleDTO == null) return;
        articleListRuleDTO.setLastChapterRule(event.getRule());
        String ruleUrl = articleListRuleDTO.getUrl();
        String pageTitle = intentTitle();
        String value = event.getRule();
        Task.execute(() -> HkPageRecordStore.shared()
                .writeCollectionLastChapter(ruleUrl, pageTitle, value));
    }

    /**
     * 照 8.83 ArticleListIsland 的 showSelectOptions 消费（JE.showSelectOptions post
     * SelectExtra 本体、页面订阅弹框）：宿主存活＋isResumed 门后弹选项，选中以
     * MY_INDEX＋js、input=选中项标题回求值，求值结果非空/非 "undefined"/
     * 非 "hiker://empty" 时走 dealUrlSimply（第九阶段接线，复核订正五差）：
     * options2 支 span 恒 2、options 支 span＝col（缺省 3）；标题空缺省「请选择」；
     * bottom 走底部形（useCenter(false)）；回求值走 evalJS(code, input, false)
     * 不再多过 decode；长按官为 no-op。options2 的 icon 事件已带、咱家
     * HkCenterListPopup 无图标位，显示只走标题（记名）。
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onShowSelectOptions(com.fongmi.android.tv.api.hk.event.rule.ShowSelectOptionsEvent event) {
        if (event == null) return;
        boolean hasOptions = event.getOptions() != null && !event.getOptions().isEmpty();
        boolean hasOptions2 = event.getOptions2Titles() != null && !event.getOptions2Titles().isEmpty();
        if (!hasOptions && !hasOptions2) return;
        if (getActivity() == null || getActivity().isFinishing() || !isResumed()) return;
        HkRule r = articleListRuleDTO;
        if (r == null) return;
        List<String> titles = hasOptions2 ? event.getOptions2Titles() : event.getOptions();
        HkCenterListPopup popup = new HkCenterListPopup(requireContext());
        popup.withTitle(TextUtils.isEmpty(event.getTitle()) ? "请选择" : event.getTitle());
        if (event.getSelectedIndex() >= 0) popup.withSelectIndex(event.getSelectedIndex());
        if (event.isBottom()) popup.useCenter(false);
        int span = hasOptions2 ? 2 : (event.getCol() > 0 ? event.getCol() : 3);
        popup.with(titles, span, new HkCenterListPopup.ClickListener() {
            @Override
            public void click(String text, int index) {
                try {
                    com.fongmi.android.tv.api.hk.HkJsRuntime rt = HkEngineManager.runtimeFor(r);
                    HkEngineManager.ensureInit(rt);
                    String input = text;
                    if (hasOptions2) {
                        if (index >= 0 && index < event.getOptions2Titles().size()) {
                            input = event.getOptions2Titles().get(index);
                        }
                    } else if (index >= 0 && index < event.getOptions().size()) {
                        input = event.getOptions().get(index);
                    }
                    String js = event.getJs() == null ? "" : event.getJs();
                    Object res = rt.evalJS(com.fongmi.android.tv.api.hk.HkJsRuntime.getMyRule(r)
                            + "var MY_INDEX=" + index + ";\n" + js, input, false);
                    String result = res == null ? null : String.valueOf(res);
                    if (result != null && !result.isEmpty() && !"undefined".equalsIgnoreCase(result)
                            && !"hiker://empty".equals(result)) {
                        DetailBridge bridge = detailBridge();
                        if (bridge != null && getActivity() != null) {
                            bridge.dealUrlSimply(getActivity(), null, r, null, result, null, null);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "hk error", e);
                }
            }

            @Override
            public void onLongClick(String text, int index) {
                // 官 ArticleListIsland 两支 ClickListener.onLongClick 均为空体
            }
        });
        popup.show();
    }

    @Override
    public void bindArrayToView(String key, List<HkItem> list) {
        bindArrayToView(key, list, false);
    }

    /** 照抄原版 bindArrayToView(String, List, boolean)。 */
    public void bindArrayToView(String key, List<HkItem> list, boolean fromCache) {
        if (getContext() == null) return;
        if (fromCache && adapter != null && !adapter.getList().isEmpty()) return;
        boolean isNew = !TextUtils.isEmpty(key) && key.endsWith("ARTICLE_LIST_NEW");
        boolean isPre = !TextUtils.isEmpty(key) && key.startsWith("ARTICLE_LIST_PRE");
        boolean isAfter = !TextUtils.isEmpty(key) && key.startsWith("ARTICLE_LIST_AFTER");
        // 首拉落地记账（缓存回灌不算模型首发落地）：供首拉窗 refreshPage 补发判定
        if (isNew && !isPre && !isAfter && !fromCache) onFirstLoadSettled();
        // 照抄原版 bindArrayToView 的存缓存支（ArticleListFragment.smali 源行 4060-4061）：
        // 非缓存来源的整页 NEW 结果（不含 PRE/AFTER 分片）连同 cacheCode 落盘，
        // 供下次 #autoCache# 秒开 / #cacheOnly# 离线打开；saveCache 内部异步。
        if (!fromCache && isNew && !isPre && !isAfter
                && !TextUtils.isEmpty(autoCacheMd5) && getContext() != null) {
            HkAutoCacheUtil.saveCache(getContext(), autoCacheMd5, list, cacheCode);
        }
        updateReadData(isNew && !isAfter, list);
        if (isNew) hookHomeData(list);
        if (isNew && !isPre && listHost) injectReverseMode(list);
        String articlesIdStr = getArticlesIdStr(list);
        updateWebViewHolder(list);
        injectTextConfig(list);
        updateReadLayout(list);
        try {
            runWaitUI(() -> applyListToView(isNew, isPre, isAfter, articlesIdStr, fromCache, list, key));
        } catch (Exception e) {
            SpiderDebug.log("hk", "bindArrayToView runWaitUI failed key=%s err=%s", key, e.toString());
        }
    }

    /** 照抄原版 lambda$bindArrayToView$43（UI 线程体）：分页状态收尾、宿主通知、收刷新圈，再按 NEW/合并/PRE/AFTER 分路落表。 */
    private void applyListToView(boolean isNew, boolean isPre, boolean isAfter, String idStr,
                                 boolean fromCache, List<HkItem> list, String key) {
        try {
            if (!isNew) {
                if (isPre) maxLoadedPage = page;
                else maxLoadedPage = Math.max(maxLoadedPage, loadingPage);
                loadingPage = -1;
                isLoading = false;
            }
            if (mainHost && hostListener != null) {
                hostListener.onChildChanged(this, webViewHolder != null);
            }
            if (smartRefreshLayout != null) {
                smartRefreshLayout.finishRefresh(true);
                smartRefreshLayout.finishLoadMore(true);
            }
            // 照 8.83 原版 bindArrayToView：收完刷新圈后无条件调一次 updateWebView()，
            // 不分 key、不分缓存状态（原版该方法无任何开关参数）。此前本类自创了
            // show 门：普通新数据（非缓存）路径传 false 直接返回，WebView 永远不建、
            // 不挂、不加载——魔曰首页、游戏集详情这类整页只有一个网页条目的页面，
            // 数据明明到了却整页纯白（1.3.13 真机实证仍白），根子就在这道门。
            updateWebView();
            if (!isPre && !isAfter && !"ARTICLE_LIST".equals(key)) {
                firstPageData = idStr;
                boolean merged = false;
                if (fromCache) {
                    cacheMergeState = 1;
                    clearGlideMemory();
                } else if (cacheMergeState == 1) {
                    list.addAll(0, initHeaders());
                    if (mainHost) list.add(initFooter());
                    merged = mergeCachedList(list);
                    cacheMergeState = 2;
                }
                if (cacheMergeState <= 0) clearGlideMemory();
                if (!merged) replaceListWith(list);
            } else if ("ARTICLE_LIST".equals(key) || isAfter) {
                if (headerSize == -1) headerSize = initHeaders().size();
                List<HkItem> cur = adapter.getList();
                if (cur.isEmpty()) list.addAll(0, initHeaders());
                if (!cur.isEmpty() && list != null && !list.isEmpty()) {
                    List<HkItem> fresh = new ArrayList<>();
                    for (HkItem it : list) {
                        boolean dup = false;
                        for (HkItem old : cur) {
                            if (itemsEqual(old, it)) {
                                dup = true;
                                break;
                            }
                        }
                        if (!dup) fresh.add(it);
                    }
                    list = fresh;
                }
                int insertBase = cur.size() - 1;
                boolean canInsert = list != null && !list.isEmpty() && idStr != null
                        && !idStr.equals(firstPageData) && !idStr.equals(lastPageData);
                if (canInsert && idStr.length() > 50) {
                    String sub = idStr.substring(1);
                    if (!TextUtils.isEmpty(firstPageData) && firstPageData.endsWith(sub)) {
                        canInsert = false;
                    } else if (!TextUtils.isEmpty(lastPageData) && lastPageData.endsWith(sub)) {
                        canInsert = false;
                    }
                }
                if (canInsert) {
                    if (mainHost) {
                        list.add(initFooter());
                        if (!cur.isEmpty() && HkColType.BIG_BIG_BLANK_BLOCK.code.equals(
                                cur.get(cur.size() - 1).getType())) {
                            cur.remove(cur.size() - 1);
                        }
                    }
                    cur.addAll(list);
                    adapter.notifyRangeChanged(insertBase, list.size());
                }
                if (isPre) firstPageData = idStr;
                else lastPageData = idStr;
            }
            showLastClick();
            if (loadListener != null) loadListener.complete();
        } catch (Exception e) {
            // 落表失败不许静默吞：页面会空白无声。记真因并走与加载失败同一个真错误窗出口
            SpiderDebug.log("hk", "applyListToView failed key=%s err=%s", key, e.toString());
            showLoadError("列表加载失败", e);
        } finally {
            loading(false);
        }
    }

    /** 缓存合并路（原版 lambda$43 的 cacheMergeState==1 分支）：与现有列表做最长公共前缀扫描，尾部按 hasFlex 决定整表/区间通知，再插入增量。 */
    private boolean mergeCachedList(List<HkItem> list) {
        List<HkItem> cur = adapter.getList();
        int lastMatch = -1;
        int i = 0;
        while (i < cur.size() && i < list.size()) {
            if (!itemsEqual(cur.get(i), list.get(i))) break;
            lastMatch = i;
            i++;
        }
        if (lastMatch < 0) return false;
        if (list.size() > lastMatch + 1) {
            if (lastMatch + 1 < cur.size()) {
                int count = cur.size() - lastMatch - 1;
                boolean hasFlex = adapter.hasFlex(lastMatch + 1, count);
                for (int j = cur.size() - 1; j > lastMatch; j--) cur.remove(j);
                if (hasFlex) adapter.notifyDataChanged();
                else adapter.notifyRangeRemoved(lastMatch + 1, count);
            }
            List<HkItem> sub = new ArrayList<>(list.subList(lastMatch + 1, list.size()));
            int insertPos = cur.size();
            cur.addAll(sub);
            adapter.notifyRangeInserted(insertPos, sub.size());
        } else if (cur.size() > lastMatch + 1) {
            int count = cur.size() - lastMatch - 1;
            for (int j = cur.size() - 1; j > lastMatch; j--) cur.remove(j);
            adapter.notifyRangeRemoved(lastMatch + 1, count);
        }
        return true;
    }

    /** 常规落表路（原版 lambda$43 的 goto_b 段）：保留已有 header 组、清掉其后旧内容，再接新列表。 */
    private void replaceListWith(List<HkItem> list) {
        List<HkItem> cur = adapter.getList();
        int oldSize = cur.size();
        if (oldSize == 0) list.addAll(0, initHeaders());
        boolean foundHeader = false;
        for (int i = cur.size() - 1; i >= 0; i--) {
            try {
                if ("header".equals(cur.get(i).getType())) foundHeader = true;
                else if (!foundHeader) cur.remove(i);
            } catch (Exception ignored) {
            }
        }
        if (oldSize != 0 && cur.size() == 0) list.addAll(0, initHeaders());
        if (mainHost) list.add(initFooter());
        adapter.notifyDataChanged();
        int pos = cur.size() - 1;
        cur.addAll(list);
        adapter.notifyRangeChanged(pos, list.size());
    }

    /** 照抄原版 initHeaders：非沉浸且非子页面宿主时先垫一个 blank_block；四组分类各生成一个 header 条目。 */
    private synchronized List<HkItem> initHeaders() {
        List<HkItem> headers = new ArrayList<>();
        if (!immersiveTheme && !listHost) {
            HkItem blank = new HkItem();
            blank.setType(HkColType.BLANK_BLOCK.code);
            headers.add(blank);
        }
        if (immersiveTheme && getView() != null) {
            View container = getView().findViewById(R.id.bodyContainer);
            if (container != null) {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) container.getLayoutParams();
                lp.setMargins(0, 0, 0, 0);
            }
        }
        // 原版此处还有隐私规则替换判定（SettingConfig.shouldReplacePrivacyRule）——星辰无隐私模式子系统，恒不触发。
        if (articleListRule != null) {
            addHeaderItem(headers, articleListRule.getClassName(), articleListRule.getClassUrl(), "class");
            addHeaderItem(headers, articleListRule.getAreaName(), articleListRule.getAreaUrl(), "area");
            addHeaderItem(headers, articleListRule.getYearName(), articleListRule.getYearUrl(), "year");
            addHeaderItem(headers, articleListRule.getSortName(), articleListRule.getSortUrl(), "sort");
        }
        return headers;
    }

    private void addHeaderItem(List<HkItem> headers, String name, String url, String desc) {
        if (TextUtils.isEmpty(name)) return;
        HkItem item = new HkItem();
        item.setType("header");
        item.setTitle(name);
        item.setUrl(url);
        item.setDesc(desc);
        headers.add(item);
    }

    /** 照抄原版 initFooter：一个 big_big_blank_block 条目。 */
    private HkItem initFooter() {
        HkItem item = new HkItem();
        item.setType(HkColType.BIG_BIG_BLANK_BLOCK.code);
        return item;
    }

    /**
     * 指纹/比对用的 extra 归一：extra 是原始 JSON 串，规则可在里面塞每次求值都变的
     * 装饰值（BestJav 的 apollo 页头件 extra.backgroundColor 是 Math.random() 现挑的
     * 随机色）——原样进指纹/全等比对，同一页两次求值永远「不相等」：回灌守卫的整页
     * 指纹比对与逐条去重双双失效，重复的第一页会被当成新页整页再追加一遍。
     * 归一只剔顶层 backgroundColor 一个键、只用于比对与指纹，条目本体 extra 不动
     * （它驱动选中片的底色渲染）。非 JSON 对象原样返回。
     */
    public static String stableExtra(String extra) {
        if (extra == null || extra.isEmpty()) return extra;
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(extra);
            if (el == null || !el.isJsonObject()) return extra;
            com.google.gson.JsonObject obj = el.getAsJsonObject();
            if (!obj.has("backgroundColor")) return extra;
            obj.remove("backgroundColor");
            return new com.google.gson.Gson().toJson(obj);
        } catch (Exception e) {
            return extra;
        }
    }

    /** 照抄原版 IDUtilsKt.getArticlesIdStr：列表 JSON 指纹；超 100 条时只取首 50 + 尾 50 的 JSON 拼接。指纹内的 extra 先经 stableExtra 归一（见上）。 */
    public static String getArticlesIdStr(List<HkItem> list) {
        if (list == null) return "null";
        com.google.gson.Gson gson = new com.google.gson.Gson();
        if (list.size() > 100) {
            return stableIdStr(gson, list.subList(0, 50))
                    + stableIdStr(gson, list.subList(list.size() - 50, list.size()));
        }
        return stableIdStr(gson, list);
    }

    /** 列表指纹的实际序列化：逐条把 extra 换成归一形再拼 JSON（不改条目本体）。 */
    private static String stableIdStr(com.google.gson.Gson gson, List<HkItem> list) {
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (HkItem item : list) {
            com.google.gson.JsonElement el = gson.toJsonTree(item);
            if (el != null && el.isJsonObject()) {
                com.google.gson.JsonObject obj = el.getAsJsonObject();
                com.google.gson.JsonElement ex = obj.get("extra");
                if (ex != null && ex.isJsonPrimitive() && ex.getAsJsonPrimitive().isString()) {
                    obj.addProperty("extra", stableExtra(ex.getAsString()));
                }
            }
            arr.add(el);
        }
        return gson.toJson(arr);
    }

    /** 照抄原版 ArticleList.equals(ArticleList, ArticleList)：title/url/desc/type/pic/content/extra 全等（null 安全）。extra 经 stableExtra 归一后比（见上）。 */
    public static boolean itemsEqual(HkItem a, HkItem b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return TextUtils.equals(a.getTitle(), b.getTitle())
                && TextUtils.equals(a.getUrl(), b.getUrl())
                && TextUtils.equals(a.getDesc(), b.getDesc())
                && TextUtils.equals(a.getType(), b.getType())
                && TextUtils.equals(a.getPic(), b.getPic())
                && TextUtils.equals(a.getContent(), b.getContent())
                && java.util.Objects.equals(stableExtra(a.getExtra()), stableExtra(b.getExtra()));
    }

    /** 照抄原版 runWaitUI：投递到 UI 线程并以 CountDownLatch 等待（最长 30 秒）；已在 UI 线程时直接跑。 */
    private void runWaitUI(Runnable runnable) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            runnable.run();
            return;
        }
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                try {
                    runnable.run();
                } finally {
                    latch.countDown();
                }
            });
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    runnable.run();
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private void clearGlideMemory() {
        if (getContext() != null) {
            try {
                com.bumptech.glide.Glide.get(getContext()).clearMemory();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 照抄原版 showLastClick：首调只置 hasShowLastClick 并走恢复段（阅读分支不移植；
     * 列表宿主时向宿主历史/收藏存储查 lastClick，命中则回主线程 applyLastClick）；
     * 后续调用把 lastSelectedPosition 处的条目标记为选中并单条刷新。
     */
    private void showLastClick() {
        if (!hasShowLastClick) {
            hasShowLastClick = true;
            if (listHost && hostListener != null && articleListRule != null) {
                String title = null;
                if (getActivity() != null && getActivity().getIntent() != null) {
                    title = getActivity().getIntent().getStringExtra("title");
                }
                String lastClick = hostListener.onQueryLastClick(articleListRule.getUrl(), title);
                if (!TextUtils.isEmpty(lastClick) && getActivity() != null && !getActivity().isFinishing()) {
                    String click = lastClick;
                    getActivity().runOnUiThread(() -> applyLastClick(click));
                }
            }
            return;
        }
        int pos = lastSelectedPosition;
        if (adapter != null && pos >= 0 && pos < adapter.getList().size()) {
            HkItem item = adapter.getList().get(pos);
            if (!item.isSel()) {
                item.setSel(true);
                adapter.notifyChanged(pos);
            }
        }
    }

    /** 照抄 lambda$showLastClick$53/$54 的公共核：定位 → 滚动 → 通知宿主 → refreshLastClick(pos, lastClick, 3)。 */
    private void applyLastClick(String lastClick) {
        int pos = findPosByTitle(lastClick);
        if (pos > 10) scrollFlag = -1;
        int goodPos = calcGoodPosForScroll(pos);
        if (goodPos > 0 && recyclerView != null) recyclerView.scrollToPosition(goodPos);
        if (hostListener != null) hostListener.onShowLastClick(lastClick);
        refreshLastClick(pos, lastClick, 3);
    }

    /**
     * 照抄原版 findPosByTitle：空 → 0；"标题@@下标" 形式优先解析下标（失败记日志回 0），
     * 否则按标题文本在当前列表扫描首个同名条目（扫描上限 0x186a0 是原版迭代护栏，此处以列表长为界等价）。
     */
    public int findPosByTitle(String title) {
        if (TextUtils.isEmpty(title) || adapter == null) return 0;
        String name = title;
        int parsed = 0;
        if (title.contains("@@")) {
            String[] parts = title.split("@@");
            name = parts.length > 0 ? parts[0] : title;
            try {
                if (parts.length > 1) parsed = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                Log.e(TAG, "hk error", e);
            }
        }
        java.util.List<HkItem> list = adapter.getList();
        for (int i = 0; i < list.size(); i++) {
            String t = detailBridge == null ? list.get(i).getTitle() : detailBridge.getTitleText(list.get(i).getTitle());
            if (name.equals(t)) return i;
        }
        return Math.min(parsed, Math.max(0, list.size() - 1));
    }

    /** 对应原版 calcGoodPosForScroll（经 calcGoodPosForImmersiveTheme 回退同组条目）：回退细则宿主阶段复核（smali 3065），现按已解码门限（≤5 回 0）+ 回退到同 type 连续段段首实现。 */
    private int calcGoodPosForScroll(int position) {
        if (position <= 5 || adapter == null) return 0;
        java.util.List<HkItem> list = adapter.getList();
        if (position >= list.size()) return 0;
        String type = list.get(position).getType();
        int start = position;
        for (int i = position - 1; i >= 0; i--) {
            if (!TextUtils.equals(type, list.get(i).getType())) break;
            start = i;
        }
        return start;
    }

    /** 照抄原版 refreshLastClick 的重试外壳：次数耗尽或活动结束即止；列表为空时延迟重试，否则走 refreshLastClick0 的标记核。 */
    public void refreshLastClick(int position, String title, int tries) {
        if (tries <= 0) return;
        if (getActivity() == null || getActivity().isFinishing()) return;
        if (adapter == null || adapter.getList().isEmpty()) {
            if (recyclerView != null) {
                recyclerView.postDelayed(() -> refreshLastClick(position, title, tries - 1), 200);
            }
            return;
        }
        lastSelectedPosition = position;
        showLastClickMarkOnly();
    }

    private void showLastClickMarkOnly() {
        int pos = lastSelectedPosition;
        if (adapter != null && pos >= 0 && pos < adapter.getList().size()) {
            HkItem item = adapter.getList().get(pos);
            if (!item.isSel()) {
                item.setSel(true);
                adapter.notifyChanged(pos);
            }
        }
    }

    // —— 子系统占位（原版对应物不在移植范围内，已在施工图登记）——

    /** 原版 updateReadData：阅读数据子系统不移植。 */
    private void updateReadData(boolean isNew, List<HkItem> list) {
    }

    /** 原版 hookHomeData：小程序首页数据钩子，首页按用户令保持星辰现状、不接。 */
    private void hookHomeData(List<HkItem> list) {
    }

    /** 原版 injectReverseMode：FilmListActivity 反转模式，宿主阶段再接。 */
    private void injectReverseMode(List<HkItem> list) {
    }

    /** 原版 injectTextConfig：阅读字号配置不移植。 */
    private void injectTextConfig(List<HkItem> list) {
    }

    /** 原版 updateReadLayout：阅读底部栏不移植。 */
    private void updateReadLayout(List<HkItem> list) {
    }

    /**
     * 照抄原版 updateWebViewHolder（ArticleListFragment.smali:22986）：遍历新数据，
     * x5_webview_single 条目 → 建/更新行内 WebView holder（setUrl + 按 desc 定模式 + extra 解析 X5Extra）；
     * video 条目 → holder 记 videoUrl/videoHeaders、url 置空页、条目 desc 改 "float&&0"。
     */
    private void updateWebViewHolder(List<HkItem> list) {
        if (list == null || list.isEmpty()) return;
        for (HkItem item : list) {
            if (item == null) continue;
            HkColType type = HkColType.byCode(item.getType());
            if (type == HkColType.X5_WEB_VIEW) {
                ensureWebViewHolder();
                webViewHolder.setUrl(item.getUrl());
                updateModeByDesc(item.getDesc());
                String extra = extraJson(item);
                if (!TextUtils.isEmpty(extra)) {
                    try {
                        webViewHolder.setX5Extra(
                                new com.google.gson.Gson().fromJson(extra, HkX5Extra.class));
                    } catch (Exception ignored) {
                    }
                }
                if (adapter != null) adapter.setWebViewHolder(webViewHolder);
                wireWebViewInterception(articleListRuleDTO);
            } else if (type == HkColType.VIDEO) {
                ensureWebViewHolder();
                webViewHolder.setVideoUrl(item.getUrl());
                String extra = extraJson(item);
                if (!TextUtils.isEmpty(extra)) {
                    try {
                        HkX5Extra x5 = new com.google.gson.Gson().fromJson(extra, HkX5Extra.class);
                        if (x5 != null) webViewHolder.setVideoHeaders(x5.getHeaders());
                    } catch (Exception ignored) {
                    }
                }
                webViewHolder.setUrl("file:///android_asset/empty.html");
                item.setDesc("float&&0");
                updateModeByDesc("float&&0");
                if (adapter != null) adapter.setWebViewHolder(webViewHolder);
            }
        }
    }

    /** 原版 holder 惰性创建 + setLazyRuleConsumer(consumeLazyRuleForWebView)。 */
    private void ensureWebViewHolder() {
        if (webViewHolder != null) return;
        webViewHolder = new HkWebViewHolder();
        webViewHolder.setLazyRuleConsumer(this::consumeLazyRuleForWebView);
    }

    /**
     * 行内 WebView 的拦截链接线（8.83 ArticleWebViewHolder.shouldOverrideUrlLoading 同款）：
     * 1) x5Extra.urlInterceptor 求值（JSEngine.evalJS(code, url)，input=拦截到的 URL）；
     * 2) 规则 confirm() 弹确认框，确定/取消分别求值回调串，结果走 dealUrlSimply；
     * 3) 网页桥 fy_bridge_app.open({title,url}) 打开规则子页面。
     */
    private void wireWebViewInterception(com.fongmi.android.tv.api.hk.HkRule rule) {
        if (rule == null || webViewHolder == null) return;
        final com.fongmi.android.tv.api.hk.HkJsRuntime rt;
        try {
            rt = com.fongmi.android.tv.ui.hk.HkEngineManager.runtimeFor(rule);
        } catch (Throwable t) {
            return;
        }
        if (rt == null) return;
        final com.fongmi.android.tv.api.hk.HkRule r = rule;
        webViewHolder.setUrlInterceptorEvaluator((code, url) -> {
            try {
                return rt.evalJS(code, url, true);
            } catch (Throwable t) {
                return "";
            }
        });
        rt.setConfirmHandler((title, content, confirmJs, cancelJs) ->
                showRuleConfirm(title, content, confirmJs, cancelJs, rt, r));
        webViewHolder.setBridgeOpenHandler(new HkWebkitHolder.BridgeOpenHandler() {
            @Override
            public void openBridgePage(String title, String url) {
                HkArticleFragment.this.openBridgePage(title, url, r);
            }

            @Override
            public void openBridgePage(String title, String url, String picUrl, String extraJson) {
                openBridgePage(title, url, picUrl, extraJson, null);
            }

            @Override
            public void openBridgePage(String title, String url, String picUrl, String extraJson,
                                       String pages) {
                // 环26 三件：picUrl 直入 SubListRequest（doOpenSubList 以 "picUrl" extra
                // 交 HkListActivity、与官 lambda$14 同名）；newWindow/windowId 自 extraJson
                // 取（官 lambda$14 读 DetailPage.extra 同两键），落 req 的既有两字段
                // （newWindow 的开页形走本树既定口径：不带请求码新开一页、不提旧页，
                // 窗口三旗/moveToFront 形已按 hk-task-single 账整件退役，不在此复活）。
                // C1 复核订正：extraJson 整串另交私有 openBridgePage 的 getNextPage 路
                // 组子规则（与 HkPageActivity 宿主同形），不再只摘两键而丢 rule/url
                // 键与 MY_PARAMS；pages 供 λ16 覆写。
                boolean newWindow = false;
                String windowId = null;
                if (!TextUtils.isEmpty(extraJson)) {
                    try {
                        org.json.JSONObject extra = new org.json.JSONObject(extraJson);
                        Object nw = extra.opt("newWindow");
                        newWindow = nw != null && "true".equals(String.valueOf(nw));
                        String wid = extra.optString("windowId", "");
                        windowId = TextUtils.isEmpty(wid) ? null : wid;
                    } catch (Throwable ignored) {
                    }
                }
                HkArticleFragment.this.openBridgePage(title, url, r, picUrl, newWindow, windowId,
                        extraJson, pages);
            }

            @Override
            public void openDetailRule(String url, String title, String findRule,
                                       String colType, String group, String preRule) {
                // 官 $1.toDetailPage：page 链走本页子页分发，余者组临时规则开列表页
                if (com.fongmi.android.tv.api.hk.HkPageParser.isPageUrl(url)) {
                    HkArticleFragment.this.openBridgePage(title, url, r);
                    return;
                }
                android.app.Activity act = getActivity();
                if (act == null || act.isFinishing()) return;
                com.fongmi.android.tv.api.hk.HkRule sub = new com.fongmi.android.tv.api.hk.HkRule();
                sub.setUrl(url);
                sub.setTitle(title);
                sub.setFindRule(findRule);
                sub.setColType(colType);
                sub.setGroup(group);
                sub.setPreRule(preRule);
                HkListActivity.startRule(act, sub, title);
            }
        });
        // G43 网页桥全量接线：引擎面供给本页运行时；图片/播放面交 DetailBridge 既有出口
        webViewHolder.setBridgeEngine(() -> rt);
        webViewHolder.setBridgePicHandler(new HkWebkitHolder.BridgePicHandler() {
            @Override
            public void showPic(String url) {
                if (detailBridge == null || url == null) return;
                java.util.List<String> urls = new java.util.ArrayList<>();
                urls.add(url);
                detailBridge.showImageViewer(urls, 0, null, null);
            }

            @Override
            public void showPics(java.util.List<String> urls, String pageUrl) {
                android.app.Activity act = getActivity();
                if (detailBridge == null || act == null || urls == null) return;
                PictureListRequest req = new PictureListRequest();
                req.pics = new java.util.ArrayList<>(urls);
                req.url = pageUrl;
                detailBridge.openPictureList(act, req);
            }
        });
        webViewHolder.setBridgePlayHandler(new HkWebkitHolder.BridgePlayHandler() {
            @Override
            public void playVideo(String title, String url) {
                android.app.Activity act = getActivity();
                if (detailBridge == null || act == null) return;
                detailBridge.startPlayerSingle(act, title, url);
            }

            @Override
            public void playVideos(String json) {
                android.app.Activity act = getActivity();
                if (detailBridge == null || act == null || json == null) return;
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(json);
                    java.util.List<HkChapter> chapters = new java.util.ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        HkChapter ch = new HkChapter();
                        ch.title = o.optString("title", o.optString("name"));
                        ch.url = o.optString("url");
                        // 官 VideoChapter 的 pic 字段同在入参 JSON 里，此前未透传（复核 L16）。
                        ch.pic = o.optString("pic");
                        chapters.add(ch);
                    }
                    detailBridge.startPlayerChapters(act, chapters, null, null, null);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** 8.83 ArticleListIsland.confirm 同款确认框（深色底）：确定/取消求值回调串后走路由。 */
    private void showRuleConfirm(String title, String content, String confirmJs, String cancelJs,
                                 com.fongmi.android.tv.api.hk.HkJsRuntime rt,
                                 com.fongmi.android.tv.api.hk.HkRule rule) {
        android.app.Activity act = getActivity();
        if (act == null || act.isFinishing()) return;
        act.runOnUiThread(() -> {
            android.app.Activity a = getActivity();
            if (a == null || a.isFinishing()) return;
            androidx.appcompat.app.AlertDialog dialog = new HkDialogs(a)
                    .setTitle(title == null ? "" : title)
                    .setMessage(content == null ? "" : content)
                    .setPositiveButton("确定", (d, w) -> routeConfirmResult(rt, rule, confirmJs))
                    .setNegativeButton("取消", (d, w) -> routeConfirmResult(rt, rule, cancelJs))
                    .create();
            dialog.setOnShowListener(d -> {
                android.widget.Button b1 = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
                if (b1 != null) b1.setTextColor(0xFFD9A420);
                android.widget.Button b2 = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
                if (b2 != null) b2.setTextColor(0xFFA8B0C0);
            });
            dialog.show();
        });
    }

    /** 8.83 ArticleListIsland.lambda$confirm$1 同款：求值回调串，结果非空且非 undefined 走 dealUrlSimply。 */
    private void routeConfirmResult(com.fongmi.android.tv.api.hk.HkJsRuntime rt,
                                    com.fongmi.android.tv.api.hk.HkRule rule, String js) {
        if (js == null || js.isEmpty()) return;
        com.fongmi.android.tv.utils.Task.execute(() -> {
            String result = rt.evalConfirmJs(js);
            if (result == null || result.isEmpty() || "undefined".equalsIgnoreCase(result)
                    || "null".equalsIgnoreCase(result) || "false".equalsIgnoreCase(result)
                    || "hiker://empty".equalsIgnoreCase(result)) return;
            android.app.Activity act = getActivity();
            if (act == null) return;
            final String url = result;
            act.runOnUiThread(() -> {
                DetailBridge bridge = detailBridge();
                if (bridge != null && getActivity() != null) {
                    bridge.dealUrlSimply(getActivity(), null, rule, null, url, null, null);
                }
            });
        });
    }

    /** 原版 VideoInterface.open 同款：{title,url} 打开规则子页面（本规则或别规则的 pages）。 */
    private void openBridgePage(String title, String url, com.fongmi.android.tv.api.hk.HkRule rule) {
        openBridgePage(title, url, rule, null, false, null);
    }

    /** 环26 三件入 req 形：picUrl/newWindow/windowId 随 SubListRequest 走既有开页口。 */
    private void openBridgePage(String title, String url, com.fongmi.android.tv.api.hk.HkRule rule,
                                String picUrl, boolean newWindow, String windowId) {
        openBridgePage(title, url, rule, picUrl, newWindow, windowId, null, null);
    }

    /**
     * 子规则拼装照官走 HkPageParser.getNextPage 一口（与 HkPageActivity 宿主
     * openPage 同形，C1 复核订正）：extraJson 整串作 paramsJson 透传——extra
     * 的 rule 键换规则、url 键定子页址、{...} 形整串写 params 成 MY_PARAMS、
     * col_type "*" 回退全在 getNextPage 内；旧形在本方法自拼 pages 查表＋
     * deriveSubRule、只摘 extra 的 newWindow/windowId 两键，rule/url 键与
     * MY_PARAMS 全丢，与官四处不符已摘。组好后 DetailPage.pages 非空再覆写
     * sub.pages（官 toNextPage λ16）。失败形：getNextPage 抛 ParseException
     * 或回 null → 静默不开页（本宿主既有失败形；官 λ16 的 toast 支不在本面）。
     */
    private void openBridgePage(String title, String url, com.fongmi.android.tv.api.hk.HkRule rule,
                                String picUrl, boolean newWindow, String windowId,
                                String extraJson, String pages) {
        if (TextUtils.isEmpty(url)) return;
        String u = url.trim();
        if (!u.startsWith("hiker://page/")) {
            android.app.Activity act = getActivity();
            if (act != null) HkRouteBlocker.isRoute(act, u);
            return;
        }
        try {
            // rule 可为 null：官 toNextPage 本就以 null 为第一参调 getNextPage，
            // 靠 url/extra 内的 rule 键定规则、无键则 ParseException 静默不开页。
            com.fongmi.android.tv.api.hk.HkRule sub =
                    com.fongmi.android.tv.api.hk.HkPageParser.getNextPage(rule, u, extraJson);
            if (sub == null) return;
            if (!TextUtils.isEmpty(pages)) sub.setPages(pages);
            SubListRequest req = new SubListRequest();
            req.subRule = sub;
            req.title = TextUtils.isEmpty(title) ? sub.getTitle() : title;
            req.parentTitle = activityTitle();
            req.parentUrl = rule == null ? null : rule.getUrl();
            if (!TextUtils.isEmpty(picUrl)) req.picUrl = picUrl;
            req.newWindow = newWindow;
            req.windowId = windowId;
            if (pageOpener != null) pageOpener.openSubList(req);
        } catch (Throwable ignored) {
        }
    }

    /** 照抄原版 updateModeByDesc（:21142）：先 setExtra(desc)，desc 非空时按 "&&" 分段匹配 Mode 名写回 holder。 */
    private void updateModeByDesc(String desc) {
        if (webViewHolder == null) return;
        webViewHolder.setExtra(desc);
        if (TextUtils.isEmpty(desc)) return;
        for (String raw : desc.split("&&")) {
            String seg = raw.trim();
            for (HkWebViewHolder.Mode mode : HkWebViewHolder.Mode.values()) {
                if (seg.equalsIgnoreCase(mode.name())) {
                    webViewHolder.setMode(mode);
                }
            }
        }
    }

    /** 照抄原版 consumeLazyRuleForWebView（:4731）：行内 WebView 的 JS 桥回吐 lazy 地址时的消费口。 */
    private void consumeLazyRuleForWebView(String url) {
        if (url == null || adapter == null) return;
        String[] lazySplit = url.split("@lazyRule=");
        String codeAndHeader = detailBridge == null ? ""
                : detailBridge.getCodeAndHeader(
                        com.fongmi.android.tv.api.hk.HkHttpParser.getUrlAppendUA(
                                articleListRule.getUrl(), articleListRule.getUa()),
                        lazySplit);
        List<HkItem> items = adapter.getList();
        for (int i = 0; i < items.size(); i++) {
            if (HkColType.byCode(items.get(i).getType()) == HkColType.X5_WEB_VIEW) {
                dealLazyRule(webViewHolder == null ? null : webViewHolder.getWebView(),
                        i, lazySplit, codeAndHeader);
                return;
            }
        }
    }

    /**
     * 照 8.83 原版 updateWebView（ArticleListFragment:1926）：无参数、无开关门——
     * holder 在场且 WebView 未建时建并加载，已建则按地址同步加载/刷新。
     * WebView 的设置/桥/拦截由 HkWebkitHolder.initWebView 承担（原版 holder.initWebView 等价面）。
     */
    private void updateWebView() {
        if (getActivity() == null || getActivity().isFinishing() || webViewHolder == null) return;
        if (webViewHolder.getWebView() == null) {
            webViewHolder.ensureWebView(getActivity());
        }
        android.webkit.WebView wv = webViewHolder.getWebView();
        if (wv == null || webViewContainer == null) return;
        // x5Extra 的 UA 在此统一应用（原版 updateWebView 同款位置）
        HkX5Extra x5 = webViewHolder.getX5Extra();
        if (x5 != null && !TextUtils.isEmpty(x5.getUa())) {
            try {
                wv.getSettings().setUserAgentString(x5.getUa());
            } catch (Exception ignored) {
            }
        }
        // FLOAT 模式：WebView 必须挂到页级容器 webViewContainer 才可见——此前本方法只建不挂，
        // 行内 bind 对 FLOAT 又只清不挂，网页在不可见的 WebView 里加载（进度条爬完仍纯白，
        // 凡需要 webview 的小程序全中招，真机录屏实证）。非 FLOAT 时容器清空、走行内挂载。
        if (webViewHolder.getMode() == HkWebViewHolder.Mode.FLOAT) {
            int heightDp = HkWebViewHolder.getHeightByExtra(getContext(),
                    webViewHolder.getUrl(), webViewHolder.getExtra());
            // 负数是布局哨兵（-1 全高/-2 自适应等），原样透传；正数才是 dp
            int heightPx = heightDp < 0 ? heightDp : dpToPx(heightDp);
            android.view.ViewGroup.LayoutParams lp = webViewContainer.getLayoutParams();
            if (webViewContainer.getChildCount() <= 0) {
                lp.height = heightPx;
                webViewContainer.setLayoutParams(lp);
                webViewHolder.setHeight(lp.height);
                if (wv.getParent() != null) {
                    ((android.view.ViewGroup) wv.getParent()).removeView(wv);
                }
                webViewContainer.addView(wv);
            } else if (lp.height != heightPx) {
                // 已挂载时只对齐高度（原版带 300ms 动画，此处直接设值、结果等价）
                lp.height = heightPx;
                webViewContainer.setLayoutParams(lp);
                webViewHolder.setHeight(heightPx);
            }
        } else if (webViewContainer.getChildCount() > 0) {
            webViewContainer.removeAllViews();
        }
        // 地址同步：变了加载新的，没变刷新一次（原版同款；变空的静音分支缺 mute 脚本资源，暂略）
        String target = webViewHolder.getUrl();
        if (!TextUtils.isEmpty(target)) {
            if (!target.equals(wv.getUrl())) wv.loadUrl(target);
            else wv.reload();
        }
    }

    /** 照抄原版 bindObjectToView：updatePage 改页码；onRefresh/refresh 累积 onRefreshJS；onClose/close 累积 onCloseJS。 */
    @Override
    public void bindObjectToView(String key, HkItem item) {
        if ("updatePage".equals(key)) {
            if (item != null) {
                try {
                    page = Integer.parseInt(item.getTitle());
                } catch (NumberFormatException ignored) {
                    // 规则侧给的页码标题不是数字时保持当前页，不许崩
                }
            }
            return;
        }
        loadingPage = -1;
        if (!TextUtils.isEmpty(key) && item != null) {
            switch (key) {
                case "onRefresh":
                case "refresh":
                    onRefreshJS = appendJs(onRefreshJS, item.getTitle());
                    break;
                case "onClose":
                case "close":
                    onCloseJS = appendJs(onCloseJS, item.getTitle());
                    break;
                default:
                    break;
            }
        }
    }

    /** 照抄原版 appendJs：空串互让、已包含则不重复、否则以 "\n;\n" 拼接。 */
    private String appendJs(String base, String js) {
        if (TextUtils.isEmpty(js)) return base;
        if (TextUtils.isEmpty(base)) return js;
        if (base.contains(js)) return base;
        return base + "\n;\n" + js;
    }

    /** 照抄原版 error：仅在宿主存在时把 (msg, 异常) 投到 UI 线程呈现（title/code 两参本链不用）。 */
    @Override
    public void error(String title, String msg, String code, Exception e) {
        // 错误收尾同样算首拉落地：首拉窗记下的 refreshPage 照样补发
        onFirstLoadSettled();
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> showLoadError(msg, e));
        }
    }

    /** 照抄原版 lambda$error$55：收刷新圈、页码回退到已加载的最大页，再弹原版错误窗（5 秒自动消失）。 */
    private void showLoadError(String msg, Exception e) {
        try {
            if (hostListener != null) hostListener.hideLoading();
            if (smartRefreshLayout != null) smartRefreshLayout.finishRefresh(true);
            loadingPage = -1;
            page = maxLoadedPage > 0 ? maxLoadedPage : 1;
            String ruleTitle = articleListRuleDTO == null ? "" : articleListRuleDTO.getTitle();
            // 诊断：新链空列表弹错处（旧 page empty 已不可达，字段口径与旧链对齐）
            SpiderDebug.log("hk", "page empty rule=%s err=%s fetch=%s",
                    ruleTitle, msg,
                    com.fongmi.android.tv.api.hk.HkJsRuntime.getLastFetchDiag());
            com.fongmi.android.tv.api.hk.HkErrorUtil.showErrorMsg(getActivity(),
                    "规则执行过程中出现错误", "home@" + ruleTitle, msg, e);
        } finally {
            loading(false);
        }
    }

    // —— 规则/来源判定 ——

    private boolean isFromHistory() {
        return getActivity() != null
                && getActivity().getIntent() != null
                && getActivity().getIntent().getBooleanExtra("fromHistory", false);
    }

    /** 照抄原版 notLoadPreRule：来自历史记录时要跑 preRule（返回 false）；子页面宿主里不重跑（返回 true）；首页宿主跑。 */
    private boolean notLoadPreRule() {
        if (isFromHistory()) return false;
        return listHost;
    }

    // —— 条目点击管线（切片 3：照 ArticleListFragment.clickItem/dealRule/dealLazyRule 转写）——

    /** 子页面打开接缝（对应原版 FilmListActivity + Intent extras + putTempRule 静态传递），宿主阶段实现。 */
    public interface PageOpener {
        void openSubList(SubListRequest request);

        /** 对应原版 onCodeClick 开 HighLightViewActivity 看代码（标题固定「代码」）。 */
        default void openCodeView(String code) {
        }

        /** 对应原版 ArticleListRuleEditActivity.editRule（长按菜单「编辑规则」）。 */
        default void openRuleEditor(String ruleJson) {
        }

        default void openMediaFiles() {
        }

        default void openDebug(String url) {
        }

        default void openHighlightEditor(String text, java.util.function.Consumer<String> callback) {
        }
    }

    /** 照原版 dealRule 组装的子页面请求（字段与 Intent extras 一一对应）。 */
    public static class SubListRequest {
        public HkRule subRule;
        public String title;
        public String picUrl;
        public int fromActivity;
        public String parentTitle;
        public String parentUrl;
        public boolean fromLastClick;
        public AutoPageData autoPageData;
        public java.util.List<HkItem> autoPageNextData;
        public int autoPagePosition;
        public boolean autoPageNoRecordHistory;
        public boolean newWindow;
        public String windowId;
    }

    /** DetailUIHelper/RouteBlocker/LazyRuleParser/UrlDetector/PlayerChooser/ThunderManager 等引擎与宿主侧接缝，引擎单例化/宿主阶段绑定真实现。 */
    public interface DetailBridge {
        boolean isRoute(android.app.Activity activity, String url, HkRule rule, HkItem item);

        /**
         * 照原版 DetailUIHelper.dealUrlSimply 7 参版：resultConsumer = 结果回灌（原版 p5），
         * magnetConsumer = 磁力/种子/FTP 路由（原版 p6，可为 null）。
         */
        boolean dealUrlSimply(android.app.Activity activity, View view, HkRule rule, HkItem item, String url,
                              java.util.function.Consumer<String> resultConsumer,
                              java.util.function.Consumer<String> magnetConsumer);

        String getCodeAndHeader(String urlWithUa, String[] lazyParts);

        void showDownloadDialog(android.app.Activity activity, String title, String url, String film, java.util.Map<String, String> headers);

        /** 批量快速下载（原版 fastDownload $9 尾段：按 defaultDownloader 偏好直接入队，不弹编辑框）。 */
        void startFastDownload(android.content.Context context, String title, String url, String film);

        // —— UrlDetector（原版规则表随宿主阶段转写）——
        boolean isImage(String url);

        boolean isVideoOrMusic(String url);

        String clearTag(String url);

        String getTitleText(String title);

        String getViewCollectionExtraData(android.app.Activity activity, String ruleUrl);

        // —— WebView 嗅探解析（X5WebViewParser/WebkitParser）——
        boolean canParseX5(String url);

        boolean canParseWebkit(String url);

        boolean parseX5(android.app.Activity activity, String url, String extraJson,
                        java.util.function.Consumer<String> consumer);

        boolean parseWebkit(android.app.Activity activity, String url, String extraJson,
                            java.util.function.Consumer<String> consumer);

        // —— 播放器交接（PlayerChooser）——
        void startPlayerSingle(android.app.Activity activity, String title, String url);

        void startPlayerChapters(android.app.Activity activity, java.util.List<HkChapter> chapters,
                                 String pageUrl, String mTitle, android.os.Bundle extraData);

        void openPictureList(android.app.Activity activity, PictureListRequest request);

        void showImageViewer(java.util.List<String> urls, int selectPos,
                             java.util.Map<Integer, Integer> posMap, android.widget.ImageView srcView);

        void showSingleImage(String url, android.widget.ImageView srcView);

        // —— 网页/分享/迅雷 ——
        void goWeb(android.content.Context context, String url);

        void goWebWithExtraData(android.content.Context context, String url, android.os.Bundle bundle);

        void findChooserToDeal(android.content.Context context, String url);

        boolean isCannotHandleScheme(String lowerUrl);

        boolean isScheme(String url);

        String getFileExtensionFromUrl(String url);

        void startParseTorrent(android.content.Context context, String url);

        boolean isFTPOrEd2k(String url);

        void startParseFTPOrEd2k(android.content.Context context, String url,
                                 java.util.function.Consumer<String> consumer);

        boolean isMagnetOrTorrent(String url);

        void startDownloadMagnet(android.content.Context context, String url);

        // —— ThunderManager 带同组选集列表的三参版（原版 lambda$clickItem$32 调用面）——
        void startDownloadMagnetList(android.content.Context context, String url, java.util.List<HkChapter> chapters);

        void startParseTorrentList(android.content.Context context, String url, java.util.List<HkChapter> chapters);

        void startParseFTPOrEd2kList(android.content.Context context, String url, java.util.List<HkChapter> chapters);

        void toastBottom(String text);

        // —— 长按菜单/工具（HttpParser/StringUtil/ShareRuleUtil/CustomCopyPopup/FileDetailPopup）——
        String getFirstPageUrl(String url);

        String getHomeUrl(String url);

        String autoFixUrl(String base, String url);

        void shareRule(HkRule rule, boolean moreWays);

        void showCopyPopup(String text);

        void showPosDetail(android.app.Activity activity, HkItem item, HkRule rule, int position);

        void onBatchModeFinish(String mode, int count);
    }

    /** 对应原版 VideoChapter（播放选集条目）。 */
    public static class HkChapter {
        public String title;
        public String memoryTitle;
        public String url;
        public String originalUrl;
        public String extra;
        public String pic;
        public String codeAndHeader;
        public int realPos;
        public boolean use;
    }

    /** 对应原版 PictureListActivity 的入参集。 */
    public static class PictureListRequest {
        public java.util.ArrayList<String> pics;
        public String ruleJson;
        public String url;
        public String title;
        public java.util.List<HkChapter> chapters;
        public String cUrl;
        public String mTitle;
        public boolean fromLastPage;
        public int nowPage;
    }

    private PageOpener pageOpener;
    private static DetailBridge detailBridge;

    public void setPageOpener(PageOpener pageOpener) {
        this.pageOpener = pageOpener;
    }

    public static void setDetailBridge(DetailBridge bridge) {
        detailBridge = bridge;
    }

    /** 搜索页共用同一 DetailBridge 绑定（宿主 ensureStatics 时已注入）。 */
    public static DetailBridge detailBridge() {
        return detailBridge;
    }

    private String fileSelectJs;
    private int fileSelectPosition;
    private java.util.function.Consumer<String> highlightCallback;
    private boolean batchDownloadSelecting;
    private boolean batchDownloadAll;
    private int batchDownloadCount;
    private boolean batchDlanSelecting;
    private int continuousDlanMode = -1;
    private final java.util.List<Integer> continuousDlanHis = new ArrayList<>();
    private final java.util.SortedSet<HkRemotePlaySource> batchDlanParamsSet = new java.util.TreeSet<>();
    private final java.util.SortedMap<Integer, HkRemotePlaySource> batchDlanParamsMapHistory = new java.util.TreeMap<>();
    private final java.util.concurrent.atomic.AtomicInteger batchDlanParseCount = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger batchDlanParseSize = new java.util.concurrent.atomic.AtomicInteger();
    /** 1.4.16 修复（扫查高2）：投屏批次代号——每开新批次/取消/完成收尾时递增；懒解析回调出发时捕获、回来先核对，代号不符的迟到回调直接丢弃，不复活已取消条目、不污染下一批计数。 */
    private final java.util.concurrent.atomic.AtomicInteger dlanGeneration = new java.util.concurrent.atomic.AtomicInteger();
    private com.fongmi.android.tv.ui.hk.view.HkParseStatusPopup parseStatusListPopup;
    private android.app.Dialog loadingPopup;

    /** 照抄原版 clickItem(View, int)：青豆光影特例、input 条目（EditText 回填/高亮编辑/js 求值回灌）、余者转三参版。 */
    protected void clickItem(View view, int position) {
        if (position < 0 || adapter == null || position >= adapter.getList().size()) return;
        HkItem item = adapter.getList().get(position);
        String url = item.getUrl();
        if ("hiker://home@青豆||光影剧场||https://m.douban.com/movie/".equals(url)) {
            if (pageOpener != null) pageOpener.openMediaFiles();
            return;
        }
        if (HkColType.input.code.equals(item.getType())) {
            if (view == null || !(view.getTag() instanceof android.widget.EditText)) return;
            android.widget.EditText edit = (android.widget.EditText) view.getTag();
            String text = edit.getText() == null ? "" : edit.getText().toString();
            if (getActivity() != null) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                        getActivity().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
            }
            if ("true".equals(item.getExtra("highlight"))) {
                if (pageOpener != null) {
                    highlightCallback = result -> {
                        if (!android.text.TextUtils.equals(result, edit.getText())) edit.setText(result);
                    };
                    pageOpener.openHighlightEditor(text, highlightCallback);
                }
                return;
            }
            String js = url.startsWith("js:") ? url.substring("js:".length()) : url;
            Task.execute(() -> {
                HkArticleListModel.EngineBridge bridge = HkArticleListModel.getEngineBridge();
                String result = bridge == null ? "" : bridge.evalWithRule(articleListRule, js, text);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // 照原版：结果为 undefined 时不回灌点击（原版 !"undefined".equalsIgnoreCase(result)）
                        if (result != null && !result.isEmpty() && !"undefined".equalsIgnoreCase(result))
                            clickItem(view, position, result);
                    });
                }
            });
            return;
        }
        clickItem(view, position, url);
    }

    /** 照抄原版 clickItem(View, int, String)：转五参版（recordHistory=true, extraInput=""）。 */
    protected void clickItem(View view, int position, String url) {
        clickItem(view, position, url, true, "");
    }

    /**
     * 照抄原版 clickItem(View, int, String, boolean, String)：
     * 空链接 toast、hiker://debug、路由拦截、base64 解码、hiker://page/ 跳页、fileSelect:// 选文件、
     * DetailUIHelper.dealUrlSimply 简单链接、download://、@lazyRule 懒解析、@rule= 子规则、detail_find_rule 详情、余者 dealWithUrl。
     */
    protected void clickItem(View view, int position, String url, boolean recordHistory, String extraInput) {
        if (position < 0 || adapter == null || position >= adapter.getList().size()) return;
        if (getContext() == null) return;
        HkItem item = adapter.getList().get(position);
        if (getActivity() != null) {
            if (TextUtils.isEmpty(url)) {
                toastCenter("链接为空，规则有误！");
                return;
            }
            if (url.startsWith("hiker://debug")) {
                if (pageOpener != null) pageOpener.openDebug(url);
                return;
            }
            if (detailBridge != null && detailBridge.isRoute(getActivity(), url, articleListRuleDTO, item)) {
                return;
            }
            String decoded = url;
            if (url.startsWith("base64://")) {
                // 照原版（ArticleListFragment 源行 2941-2943）：Base64.decode(trim, 2)，
                // flag 2 = NO_WRAP、标准字母表；官无 try/catch——坏 base64 异常直接
                // 穿出 clickItem 中断本次点击（硬门账 C 环29 D29-3①，旧形 catch 后
                // 保留原串继续分发与官终态分叉，摘除）。
                decoded = new String(android.util.Base64.decode(
                        url.substring("base64://".length()).trim(), android.util.Base64.NO_WRAP));
            }
            if (isPageUrl(decoded)) {
                toNextPage(position, decoded);
                return;
            }
            if (decoded.startsWith("fileSelect://")) {
                fileSelectJs = decoded.replaceFirst("(?i)fileSelect://", "");
                fileSelectPosition = position;
                android.content.Intent pick = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                pick.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                startActivityForResult(android.content.Intent.createChooser(pick, "选择文件"), 0x2385);
                return;
            }
            if (detailBridge != null && detailBridge.dealUrlSimply(getActivity(), view, articleListRuleDTO, item, decoded,
                    result -> clickItem(view, position, result),
                    magnetUrl -> onMagnetRoute(position, magnetUrl))) {
                // 简单链接已处理（原版 DetailUIHelper.dealUrlSimply 返回 true 的分支）
                // 环29 D29-3②：memoryPosition 条件照官三支——①条目 url 含
                // refreshX5WebView/refreshPage → 记；②否则 decoded 含 x5WebView://
                // 或 refreshPage → 记；③否则 holder 是 video 条目 → 记；三者皆无不记。
                // 旧形第二支判 !isVideoItem（方向反）、第三支无条件记，一并订正。
                if (webViewHolder != null) {
                    String itemUrl = item.getUrl();
                    if (itemUrl != null && (itemUrl.contains("refreshX5WebView") || itemUrl.contains("refreshPage"))) {
                        memoryPosition(position);
                    } else if (decoded.contains("x5WebView://") || decoded.contains("refreshPage")) {
                        memoryPosition(position);
                    } else if (webViewHolder.isVideoItem()) {
                        memoryPosition(position);
                    }
                }
                return;
            }
            if (decoded.startsWith("download://")) {
                String full = decoded.replaceFirst("(?i)download://", "");
                // 照 8.83 DetailUIHelper：先把地址尾 ;{...} 的请求头拆出来再弹下载框，
                // 否则带防盗链请求头的下载链接会把 ;{...} 原样塞进下载任务、必然 403。
                java.util.Map<String, String> headers = com.fongmi.android.tv.api.hk.HkHttpParser.getHeaders(full);
                String real = com.fongmi.android.tv.api.hk.HkHttpParser.getRealUrlFilterHeaders(full);
                if (detailBridge != null) {
                    detailBridge.showDownloadDialog(getActivity(), getItemTitle(position), real, getFilm(), headers);
                }
                return;
            }
            dispatchRuleClick(view, position, decoded, recordHistory, extraInput);
            return;
        }
        // 原版：getActivity() == null 时整段直接返回（goto_2），不做任何分发
    }

    /** clickItem 五参版后半（原版 cond_e 起）：拆 @rule=/@lazyRule=、批量模式分流、codeAndHeader、懒解析/子规则/详情/dealWithUrl。 */
    private void dispatchRuleClick(View view, int position, String decoded, boolean recordHistory, String extraInput) {
        HkItem item = adapter.getList().get(position);
        String[] ruleSplit = decoded.split("@rule=");
        String[] lazySplit = ruleSplit[0].split("@lazyRule=");
        if (batchDownloadSelecting) {
            batchDownloadCount++;
            if (notify_text != null) notify_text.setText("完成 (" + batchDownloadCount + ")");
            fastDownload(position, lazySplit);
            return;
        }
        if (batchDlanSelecting) {
            batchDlan(position, lazySplit);
            return;
        }
        if (continuousDlanMode != -1) {
            continuousDlan(position, lazySplit);
            return;
        }
        String codeAndHeader = detailBridge == null ? ""
                : detailBridge.getCodeAndHeader(
                        HkHttpParser_getUrlAppendUA(articleListRule.getUrl(), articleListRule.getUa()), lazySplit);
        if (!recordHistory && TextUtils.isEmpty(codeAndHeader)) codeAndHeader = extraInput;
        if (lazySplit.length > 1) {
            dealLazyRule(view, position, lazySplit, codeAndHeader);
            return;
        }
        String itemTitle = getItemTitle(position);
        if (ruleSplit.length > 1) {
            String ruleStr = arrayToString(ruleSplit, 1, "@rule=");
            String[] seg = ruleStr.split("==>");
            String head = seg[0];
            String[] semi = head.split(";");
            String colType = articleListRule.getColType();
            if (semi.length == 6 && !ruleStr.startsWith("js:")) {
                colType = semi[5];
                head = arrayToString(semi, 0, 5, ";");
            }
            String tail = "";
            if (seg.length > 1) tail = "==>" + arrayToString(seg, 1, "==>");
            dealRule(articleListRule, ruleSplit[0], codeAndHeader, head + tail, colType, itemTitle, position);
            return;
        }
        if (recordHistory && !TextUtils.isEmpty(articleListRule.getDetailFindRule())
                && !decoded.startsWith("video://")) {
            dealRule(articleListRule, decoded, codeAndHeader, articleListRule.getDetailFindRule(),
                    articleListRule.getDetailColType(), itemTitle, position);
            return;
        }
        if (recordHistory) codeAndHeader = null;
        // 定根诊断：只记空/非空不记内容；fromLazy=懒解析回灌（recordHistory=false）
        SpiderDebug.log("hk", "collect-check rule=%s pos=%d fromLazy=%b codeAndHeaderEmpty=%b",
                articleListRule == null ? "" : articleListRule.getTitle(), position, !recordHistory,
                TextUtils.isEmpty(codeAndHeader));
        dealWithUrl(view, decoded, position, codeAndHeader);
    }

    /**
     * 照抄原版 dealRule：组装子规则（url 不含 ";" 时拼 codeAndHeader；继承 ua/group/preRule/title/last_chapter_rule/pages；
     * 条目 extra 非空时作 params），组装子页面请求交宿主打开（原版 Intent+putTempRule+startActivityForResult(2)，新窗口条目走 startActivity）。
     */
    private void dealRule(HkRule parentRule, String url, String codeAndHeader, String findRule,
                          String colType, String title, int position) {
        if (url.contains("#noHistory#")) memoryPos = false;
        HkRule sub = new HkRule();
        sub.setUrl(url.contains(";") ? url : url + (codeAndHeader == null ? "" : codeAndHeader));
        sub.setUa(parentRule.getUa());
        sub.setFindRule(findRule);
        sub.setColType(colType);
        sub.setGroup(parentRule.getGroup());
        sub.setPreRule(parentRule.getPreRule());
        sub.setTitle(parentRule.getTitle());
        sub.setLastChapterRule(parentRule.getLastChapterRule());
        sub.setPages(parentRule.getPages());
        HkItem item = adapter.getList().get(position);
        if (item.getExtra() != null && !item.getExtra().isEmpty()) {
            // extra 已是原始 JSON 串，直接透传（以前 Map 需二次序列化）
            sub.setParams(item.getExtra());
        }
        SubListRequest req = new SubListRequest();
        req.subRule = sub;
        req.title = title;
        req.fromActivity = getContext() == null ? 0 : getContext().hashCode();
        req.parentTitle = activityTitle();
        req.parentUrl = articleListRule == null ? "" : articleListRule.getUrl();
        if (!TextUtils.isEmpty(sub.getUrl())
                && (sub.getUrl().contains("#readTheme#") || sub.getUrl().contains("#autoReader#"))) {
            req.fromLastClick = isClickLastPageBtn(parentRule.getUrl(), activityTitle(), position);
        }
        if (sub.getUrl() != null && sub.getUrl().contains("#autoPage#")) {
            AutoPageData data = new AutoPageData();
            data.nextData = new ArrayList<>(adapter.getList().subList(position, adapter.getList().size()));
            data.url = articleListRule.getUrl();
            data.title = activityTitle();
            data.currentPos = position;
            data.noRecordHistory = noRecordHistory;
            loadChapterCount(data, position);
            req.autoPageData = data;
            req.autoPageNextData = data.nextData;
            req.autoPagePosition = position;
            req.autoPageNoRecordHistory = noRecordHistory;
        }
        try {
            if (!TextUtils.isEmpty(item.getPic())) req.picUrl = item.getPic();
        } catch (Exception ignored) {
        }
        memoryPosition(position);
        req.newWindow = "true".equals(item.getExtra("newWindow"));
        req.windowId = item.getExtra("windowId");
        if (pageOpener != null) pageOpener.openSubList(req);
    }

    /** 照抄原版 dealLazyRule + $13 回调：解析前弹"动态解析规则中，请稍候"（#noLoading# 除外），成功以 recordHistory=false 回灌 clickItem，失败仅关弹窗（错误原文由解析器侧弹）。 */
    private void dealLazyRule(View view, int position, String[] lazySplit, String codeAndHeader) {
        SpiderDebug.log("hk", "lazy click entry rule=%s pos=%d url=%s",
                articleListRule == null ? "" : articleListRule.getTitle(), position,
                lazySplit == null || lazySplit.length == 0 ? "" : lazySplit[0]);
        // 地址形状单独记一行（脱敏后导出日志里还在）：与原版拼出的地址对照用
        try {
            if (lazySplit != null && lazySplit.length > 0 && lazySplit[0] != null) {
                SpiderDebug.log("hk", "lazy click shape pos=%d path=%s", position,
                        com.fongmi.android.tv.api.hk.HkJsRuntime.maskedPath(lazySplit[0]));
            }
        } catch (Throwable ignored) {
        }
        // 照原版：FuckKt.inList(activity) 命中且 goWeb 打开成功则不再走内置解析（FuckKt 为 Fragment 直调，不经 DetailBridge）
        if (HkFuck.inList(getActivity()) && HkFuck.goWeb(getContext(), myUrl, lazySplit[0])) return;
        HkArticleListModel.EngineBridge bridge = HkArticleListModel.getEngineBridge();
        if (bridge == null) return;
        bridge.parseLazyRule(articleListRule, lazySplit, position, codeAndHeader, myUrl,
                new HkArticleListModel.LazyCallback() {
                    @Override
                    public void start() {
                        showLoadingPopup("动态解析规则中，请稍候",
                                lazySplit[0] != null && lazySplit[0].contains("#noLoading#"));
                    }

                    @Override
                    public void success(String result) {
                        // 定位用：点的是第几集、解析结果的形状（脱敏），与播放器实际起播的集数对
                        try {
                            SpiderDebug.log("hk", "lazy ok pos=%d resultShape=%s", position,
                                    com.fongmi.android.tv.api.hk.HkJsRuntime.maskedPath(result == null ? "" : result));
                        } catch (Throwable ignored) {
                        }
                        dismissLoadingPopup();
                        if (result != null && result.startsWith("error:")) {
                            // 引擎真实错误（含行号），直接弹原文，不走 URL 兜底（否则被 dealWithUrl 当成"未知链接"掩盖）
                            toastBottom(result);
                            return;
                        }
                        clickItem(view, position, result, false, codeAndHeader);
                    }

                    @Override
                    public void error(String msg) {
                        dismissLoadingPopup();
                        toastBottom(msg);
                        SpiderDebug.log("hk", "lazy click error rule=%s pos=%d msg=%s",
                                articleListRule == null ? "" : articleListRule.getTitle(), position, msg);
                    }
                });
    }

    /** 原版 initLoadingPopup 的等价物（XPopup LoadingPopupView → 原生 Dialog，先行等价、宿主阶段可换）。 */
    private void showLoadingPopup(String title, boolean skipShow) {
        dismissLoadingPopup();
        if (getContext() == null) return;
        android.widget.TextView tv = new android.widget.TextView(getContext());
        tv.setText(title);
        tv.setPadding(dpToPx(24), dpToPx(18), dpToPx(24), dpToPx(18));
        loadingPopup = new android.app.Dialog(getContext());
        loadingPopup.setContentView(tv);
        loadingPopup.setCancelable(false);
        if (!skipShow) loadingPopup.show();
    }

    private void dismissLoadingPopup() {
        if (loadingPopup != null && loadingPopup.isShowing()) loadingPopup.dismiss();
        loadingPopup = null;
    }

    /** 照抄原版 PageParser.isPageUrl。 */
    private boolean isPageUrl(String url) {
        return !TextUtils.isEmpty(url) && url.startsWith("hiker://page/");
    }

    /** 照抄原版 toNextPage 入口：列表为空直接返回；解析与开页在后台经宿主接缝（原版 lambda 体随规则 pages 子系统在引擎阶段落码）。 */
    private void toNextPage(int position, String pageUrl) {
        SpiderDebug.log("hk", "toNextPage entry pos=%d pageUrl=%s opener=%s", position, pageUrl,
                pageOpener == null ? "null" : pageOpener.getClass().getSimpleName());
        if (adapter == null || adapter.getList().isEmpty()) return;
        Task.execute(() -> {
            if (pageOpener instanceof PageUrlOpener) {
                ((PageUrlOpener) pageOpener).openPageUrl(articleListRule, pageUrl, position);
            }
        });
    }

    /** hiker://page/ 开页接缝（PageOpener 的可选扩展）。 */
    public interface PageUrlOpener {
        void openPageUrl(HkRule rule, String pageUrl, int position);
    }

    /**
     * 照抄原版 getItemTitle（ArticleListFragment 源行 3159-3170）：
     * 越界返空 → extra.pageTitle 非空用它 → inheritTitle 为真走 DetailUIHelper.getItemTitle
     * （活动标题非空时拼「活动标题-」+条目标题文本，异常回退活动标题）→ 否则只取条目标题文本。
     * 旧简化版漏了后两段：带 inheritTitle 的集名条目被原样当播放器标题（真机：标题只剩集名）。
     * 注：原版 extra 为 null 也走 DetailUIHelper 段；星辰 HkItem 的 extra 恒为 Map、读不到
     * inheritTitle 键时按未设处理（影视条目基本都带 extra，此近似影响面最小）。
     */
    public String getItemTitle(int position) {
        if (adapter == null || position < 0 || position >= adapter.getList().size()) return "";
        HkItem item = adapter.getList().get(position);
        String pageTitle = item.getExtra("pageTitle");
        if (!TextUtils.isEmpty(pageTitle)) return pageTitle;
        if ("true".equals(item.getExtra("inheritTitle"))) {
            try {
                String at = activityTitle();
                return (TextUtils.isEmpty(at) ? "" : at + "-") + HkStringUtil.getTitleText(item.getTitle());
            } catch (Exception e) {
                return activityTitle();
            }
        }
        return HkStringUtil.getTitleText(item.getTitle());
    }

    /** 宿主 hiker://page/ 开页用：被点条目的 extra 整份 JSON（作 PageParser.getNextPage 的 paramsJson）。 */
    public String getItemExtraJson(int position) {
        if (adapter == null || position < 0 || position >= adapter.getList().size()) return "";
        return extraJson(adapter.getList().get(position));
    }

    /** 照抄原版 getFilm：宿主标题（等于应用名时视为无，返回 null）。 */
    private String getFilm() {
        if (getActivity() == null) return null;
        CharSequence t = getActivity().getTitle();
        if (t == null) return null;
        String title = t.toString();
        if (title.equals(getString(R.string.app_name))) return null;
        return title;
    }

    /** 宿主标题（对应原版 DetailUIHelper.getActivityTitle 的用途：子页面 parentTitle）。 */
    private String activityTitle() {
        if (getActivity() == null) return "";
        // G1：规则经 setPageTitle 声明的页面标题（片名源）优先，导航标题兜底
        if (!TextUtils.isEmpty(declaredPageTitle)) return declaredPageTitle;
        String extra = getActivity().getIntent() == null ? null
                : getActivity().getIntent().getStringExtra("title");
        if (!TextUtils.isEmpty(extra)) return extra;
        CharSequence t = getActivity().getTitle();
        return t == null ? "" : t.toString();
    }

    /** 照抄原版 memoryPosition 的开关段：memoryPos 关时不记；记录本体（历史/续看位置）随宿主阶段承接。 */
    private void memoryPosition(int position) {
        if (!memoryPos) return;
        if (hostListener != null) hostListener.onMemoryPosition(position);
    }

    /** 阅读主题的"最后一页按钮"判定（阅读子系统不移植，恒 false）。 */
    private boolean isClickLastPageBtn(String url, String title, int position) {
        return false;
    }

    /** 照抄原版 StringUtil.arrayToString(数组, 起始下标, 连接符)。 */
    private static String arrayToString(String[] arr, int from, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < arr.length; i++) {
            if (i > from) sb.append(sep);
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    /** 照抄原版 StringUtil.arrayToString(数组, 起始, 结束(不含), 连接符)。 */
    private static String arrayToString(String[] arr, int from, int to, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to && i < arr.length; i++) {
            if (i > from) sb.append(sep);
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private void toastCenter(String text) {
        if (getContext() != null) {
            com.fongmi.android.tv.utils.ToastUtil.show(getContext(), text, android.widget.Toast.LENGTH_SHORT);
        }
    }

    private static String HkHttpParser_getUrlAppendUA(String url, String ua) {
        return com.fongmi.android.tv.api.hk.HkHttpParser.getUrlAppendUA(url, ua);
    }

    // —— 懒解析取址与下载分发（照原版 parseLazyRuleForUrl/$12/$11/$9 转写；下载对话框与入队本体经 DetailBridge 钩子承接）——

    /**
     * 照抄原版 parseLazyRuleForUrl：lazySplit[0] 为空 → toast「链接为空，请检查规则」；
     * 多段（带 @lazyRule= 规则体）走引擎桥懒解析（回调经 $12 包装）；单段先 callback.start()，
     * 再 X5/Webkit 嗅探或直通 success。
     */
    private void parseLazyRuleForUrl(int position, String[] lazySplit, HkArticleListModel.LazyCallback callback) {
        if (lazySplit == null || lazySplit.length == 0 || TextUtils.isEmpty(lazySplit[0])) {
            toastBottom("链接为空，请检查规则");
            return;
        }
        String codeAndHeader = detailBridge == null ? ""
                : detailBridge.getCodeAndHeader(
                        HkHttpParser_getUrlAppendUA(articleListRule.getUrl(), articleListRule.getUa()), lazySplit);
        if (lazySplit.length > 1) {
            HkArticleListModel.EngineBridge bridge = HkArticleListModel.getEngineBridge();
            if (bridge == null) return;
            bridge.parseLazyRule(articleListRule, lazySplit, position, codeAndHeader, myUrl,
                    wrapLazyCallback(position, callback));
            return;
        }
        callback.start();
        dispatchParsedUrl(position, lazySplit[0], callback);
    }

    /** 照抄原版 $12 包装回调：start/error 直传；success 先过 X5/Webkit 嗅探分发，余直传 delegate。 */
    private HkArticleListModel.LazyCallback wrapLazyCallback(int position, HkArticleListModel.LazyCallback delegate) {
        return new HkArticleListModel.LazyCallback() {
            @Override
            public void start() {
                delegate.start();
            }

            @Override
            public void success(String result) {
                dispatchParsedUrl(position, result, delegate);
            }

            @Override
            public void error(String msg) {
                delegate.error(msg);
            }
        };
    }

    /** parseLazyRuleForUrl 单段支与 $12.success 共用尾段：X5/Webkit 可解析走嗅探（结果回 callback.success），余直接 success。 */
    private void dispatchParsedUrl(int position, String url, HkArticleListModel.LazyCallback callback) {
        HkItem item = adapter == null || position < 0 || position >= adapter.getList().size()
                ? null : adapter.getList().get(position);
        String extra = item == null ? "" : extraJson(item);
        if (detailBridge != null && getActivity() != null && detailBridge.canParseX5(url)) {
            detailBridge.parseX5(getActivity(), url, extra, callback::success);
            return;
        }
        if (detailBridge != null && getActivity() != null && detailBridge.canParseWebkit(url)) {
            detailBridge.parseWebkit(getActivity(), url, extra, callback::success);
            return;
        }
        callback.success(url);
    }

    /**
     * 照抄原版 downloadSource + $11 回调（长按「下载资源」）：解析中弹「动态解析规则中，请稍候」；
     * 结果为空 toast「链接为空」；仍带 @lazyRule= 时以新 lazySplit 递归；
     * 不可处理 scheme/.torrent 交系统选择器；非视频且非系统下载格式 toast「{标题}非视频格式，不支持下载」；
     * 余 clearTag + getRealUrlFilterHeaders 后走下载对话框（DetailBridge 钩子承接本体）。
     * 注：原版尾段的 getSwitchPlayUrl(PlayData) 线路切换已随投屏本体移植（见 getSwitchPlayUrl），
     * 但 downloadSource 尾段仍沿本模块 download:// 支的既有口径直达对话框，未接线路切换（欠账保留）。
     */
    private void downloadSource(int position, String[] lazySplit) {
        parseLazyRuleForUrl(position, lazySplit, new HkArticleListModel.LazyCallback() {
            @Override
            public void start() {
                showLoadingPopup("动态解析规则中，请稍候", false);
            }

            @Override
            public void success(String url) {
                dismissLoadingPopup();
                if (TextUtils.isEmpty(url)) {
                    toastBottom("链接为空");
                    return;
                }
                if (url.contains("@lazyRule=")) {
                    downloadSource(position, url.split("@lazyRule="));
                    return;
                }
                if (detailBridge == null) return;
                if (detailBridge.isCannotHandleScheme(url) || url.endsWith(".torrent")) {
                    detailBridge.findChooserToDeal(getContext(), url);
                    return;
                }
                String title = getItemTitle(position);
                if (!detailBridge.isVideoOrMusic(url) && !HkDetailBridge.useSystemDownload(title, url)) {
                    toastBottom(title + "非视频格式，不支持下载");
                    return;
                }
                String tagged = detailBridge.clearTag(url);
                java.util.Map<String, String> headers = com.fongmi.android.tv.api.hk.HkHttpParser.getHeaders(tagged);
                String real = com.fongmi.android.tv.api.hk.HkHttpParser.getRealUrlFilterHeaders(tagged);
                detailBridge.showDownloadDialog(getActivity(), title, real, getFilm(), headers);
            }

            @Override
            public void error(String msg) {
                dismissLoadingPopup();
            }
        });
    }

    /**
     * 照抄原版 fastDownload + $9 回调（批量下载模式逐项分发）：结构同 $11，但终段不弹编辑框，
     * 按 defaultDownloader 偏好直接入队（DetailBridge.startFastDownload 钩子承接）；
     * 不可处理 scheme/.torrent 同样交系统选择器。原版的线路切换（getSwitchPlayUrl）欠账同 downloadSource。
     */
    private void fastDownload(int position, String[] lazySplit) {
        parseLazyRuleForUrl(position, lazySplit, new HkArticleListModel.LazyCallback() {
            @Override
            public void start() {
            }

            @Override
            public void success(String url) {
                if (TextUtils.isEmpty(url)) {
                    toastBottom("链接为空");
                    return;
                }
                if (url.contains("@lazyRule=")) {
                    fastDownload(position, url.split("@lazyRule="));
                    return;
                }
                if (detailBridge == null) return;
                if (detailBridge.isCannotHandleScheme(url) || url.endsWith(".torrent")) {
                    detailBridge.findChooserToDeal(getContext(), url);
                    return;
                }
                String real = com.fongmi.android.tv.api.hk.HkHttpParser.getRealUrlFilterHeaders(
                        detailBridge.clearTag(url));
                detailBridge.startFastDownload(getContext(), getItemTitle(position), real, getFilm());
            }

            @Override
            public void error(String msg) {
            }
        });
    }

    // —— 批量投屏/连续投屏本体（照原版 batchDlan/$10、continuousDlan/$8、getSwitchPlayUrl 移植）——

    /** 照抄原版 getSwitchPlayUrl(String, boolean)：转三参版（PlayData 为空时现解析）。 */
    private String getSwitchPlayUrl(String url, boolean withHeaders) {
        return getSwitchPlayUrl(null, url, withHeaders);
    }

    /**
     * 照抄原版 getSwitchPlayUrl(PlayData, String, boolean)：PlayData 有多线路时取「线路切换记忆」序号那条
     * （withHeaders 时拼上该线路请求头串，或原地址分号后的头串）；无多线路时带头返回原地址、不带头只取分号前第一段。
     * 线路切换记忆原版查 ViewCollection 的 switchIndex，本树无该记忆，恒 0（原版无记录时亦为 0）。
     */
    private String getSwitchPlayUrl(com.fongmi.android.tv.api.hk.HkPlayData playData, String url, boolean withHeaders) {
        if (playData == null) {
            playData = com.fongmi.android.tv.api.hk.HkHttpParser.getPlayData(
                    com.fongmi.android.tv.api.hk.HkHttpParser.getRealUrlFilterHeaders(url));
        }
        if (playData != null && playData.getUrls() != null && !playData.getUrls().isEmpty()) {
            int index = 0;
            if (index < playData.getUrls().size()) {
                String switched = playData.getUrls().get(index);
                if (!withHeaders) return switched;
                if (playData.getHeaders() != null && !playData.getHeaders().isEmpty()
                        && playData.getHeaders().size() > index) {
                    return switched + ";" + com.fongmi.android.tv.api.hk.HkHttpParser.getHeadersStr(playData.getHeaders().get(index));
                }
                String[] parts = url.split(";");
                if (parts.length <= 1) return switched;
                StringBuilder sb = new StringBuilder(switched);
                for (int i = 1; i < parts.length; i++) sb.append(";").append(parts[i]);
                return sb.toString();
            }
        }
        String[] parts = url.split(";");
        if (withHeaders) {
            if (parts.length <= 1) return parts[0];
            return url;
        }
        return parts[0];
    }

    /** 照抄原版 batchDlan：懒解析取址，结果走 $10 回调收集进投屏队列（batchDlanParamsSet/MapHistory）。 */
    private void batchDlan(int position, String[] lazySplit) {
        final int generation = dlanGeneration.get();
        parseLazyRuleForUrl(position, lazySplit, new HkArticleListModel.LazyCallback() {
            private final HkRemotePlaySource httpParams = new HkRemotePlaySource();

            @Override
            public void start() {
                if (generation != dlanGeneration.get()) return;
                batchDlanParseCount.incrementAndGet();
                // 1.4.16 修复（扫查高1）：直接用 incrementAndGet() 的返回值赋 sort，不再隔行 .get()——
                // 两路懒解析并发时 .get() 可读到同一 sort，导致 mapHistory 互相覆盖、TreeSet 按 sort 判重丢条
                int sort = batchDlanParseSize.incrementAndGet();
                httpParams.setTitle(com.fongmi.android.tv.api.hk.HkHttpParser.encodeUrl(
                        getItemTitle(position).replace(",", "_")));
                httpParams.setSort(sort);
                httpParams.setParseFinish(false);
                synchronized (batchDlanParamsMapHistory) {
                    batchDlanParamsMapHistory.put(httpParams.getSort(), httpParams);
                }
                // 原版此处刷 CompleteToolBar 状态文「队列状态 (n)」；本树完成条无状态文面，计数在队列页内可见
            }

            @Override
            public void success(String url) {
                // 1.4.16 修复（扫查高2）：批次已取消/完成/换新批的迟到回调直接丢弃——不写集合、不改计数、不弹 UI
                if (generation != dlanGeneration.get()) return;
                batchDlanParseCount.decrementAndGet();
                httpParams.setParseFinish(true);
                if (HkUrlDetector.isVideoOrMusic(url)) {
                    String playUrl = com.fongmi.android.tv.api.hk.HkHttpParser.encodeUrl(
                            getSwitchPlayUrl(HkUrlDetector.clearTag(url), true));
                    httpParams.setUrl(playUrl);
                    runUi(() -> {
                        synchronized (batchDlanParamsMapHistory) {
                            // 1.4.16 收尾修复：compareTo/equals 已对齐为 sort+url（扫查中3）后，原靠
                            // TreeSet.contains 按 url 短路的判重失效——同一 url 每次解析 sort 都不同，
                            // 不再判重。此处在入队路径上显式按 url 判重：命中则移出队列里那条同 url 的
                            // 旧条目、本条只标「重复添加」不入队，保持原行为；未命中才入队。
                            HkRemotePlaySource duplicate = null;
                            for (HkRemotePlaySource queued : batchDlanParamsSet) {
                                if (java.util.Objects.equals(queued.getUrl(), httpParams.getUrl())) {
                                    duplicate = queued;
                                    break;
                                }
                            }
                            if (duplicate != null) {
                                batchDlanParamsSet.remove(duplicate);
                                httpParams.setParseResult("重复添加，已移出投屏队列");
                            } else {
                                batchDlanParamsSet.add(httpParams);
                                httpParams.setParseResult("已加入投屏队列");
                            }
                            batchDlanParamsMapHistory.put(httpParams.getSort(), httpParams);
                        }
                        notifyParseStatusChanged();
                        if (notify_text != null) {
                            notify_text.setText("完成 (" + batchDlanParamsSet.size() + ")");
                        }
                        // 原版同处还刷 CompleteToolBar 状态文「队列状态 (n)」，本树无此面
                        memoryPosition(position);
                    });
                } else {
                    httpParams.setParseResult("非视频格式，不支持投屏");
                    synchronized (batchDlanParamsMapHistory) {
                        batchDlanParamsMapHistory.put(httpParams.getSort(), httpParams);
                    }
                    runUi(() -> notifyParseStatusChanged());
                }
            }

            @Override
            public void error(String msg) {
                // 1.4.16 修复（扫查高2）：迟到 error 同 success 口径，代号不符直接丢弃
                if (generation != dlanGeneration.get()) return;
                if (batchDlanParseCount.get() > 0) batchDlanParseCount.decrementAndGet();
                // 1.4.16 修复（扫查中1）：失败也要落终态 parseFinish=true——状态页（HkParseStatusAdapter）
                // 以 isParseFinish 判定：false 永远显示「正在获取地址...」+转圈，错误文案露不出来；置 true 后显示 parseResult(msg) 并停转圈
                httpParams.setParseFinish(true);
                httpParams.setParseResult(msg);
                synchronized (batchDlanParamsMapHistory) {
                    batchDlanParamsMapHistory.put(httpParams.getSort(), httpParams);
                }
                runUi(() -> notifyParseStatusChanged());
            }
        });
    }

    /** 照抄原版 continuousDlan：懒解析取址，结果走 $8 回调按 continuousDlanMode 分发投屏。 */
    private void continuousDlan(int position, String[] lazySplit) {
        final int generation = dlanGeneration.get();
        parseLazyRuleForUrl(position, lazySplit, new HkArticleListModel.LazyCallback() {
            @Override
            public void start() {
            }

            @Override
            public void success(String url) {
                // 1.4.16 修复（扫查高2/中2）：批次已结束的迟到回调直接丢弃；其余全段经 runUi 归置主线程——
                // 原版 success 在引擎后台线程直接 toast/弹 CastDialog/投屏，且 continuousDlanHis 裸 ArrayList
                // 跨线程 add 与主线程遍历/clear 无同步，可 ConcurrentModificationException；现 add 走同步块、UI 操作全在主线程
                if (generation != dlanGeneration.get()) return;
                runUi(() -> {
                    if (generation != dlanGeneration.get()) return;
                    memoryPosition(position);
                    String title = getItemTitle(position);
                    if (!HkUrlDetector.isVideoOrMusic(url)) {
                        toastCenter("非音视频格式，不支持投屏");
                        return;
                    }
                    synchronized (continuousDlanHis) {
                        continuousDlanHis.add(position);
                    }
                    String real = com.fongmi.android.tv.api.hk.HkHttpParser.getRealUrlFilterHeaders(
                            HkUrlDetector.clearTag(url));
                    com.fongmi.android.tv.api.hk.HkPlayData playData =
                            com.fongmi.android.tv.api.hk.HkHttpParser.getPlayData(real);
                    String playUrl = getSwitchPlayUrl(playData, real, true);
                    // 原版此处发 LoadingEvent 关解析加载框；本树懒解析无该加载框，无对应动作
                    if (continuousDlanMode == 4) {
                        if (getActivity() != null) {
                            HkLivePlayerHelper.playWithLiveTv(getActivity(), title, playUrl);
                        }
                    } else if (continuousDlanMode == 2 || continuousDlanMode == 3) {
                        webCast();
                    } else {
                        // mode1 DLNA（及兜底）：地址只取分号前第一段，请求头单独携带
                        java.util.Map<String, String> headers =
                                com.fongmi.android.tv.api.hk.HkHttpParser.getHeaders(playUrl);
                        dlnaCast(title, playUrl.split(";")[0], headers);
                    }
                });
            }

            @Override
            public void error(String msg) {
            }
        });
    }

    /**
     * 连续投屏 mode1（DLNA）的本树落点：原版经 DlanPlayEvent 常驻会话或 DlanListPopUtil 开 MediaPlayActivity，
     * 本树无该常驻投屏页，接到既有 DLNA 投屏链（CastDialog 选设备 + DLNACast 投放，与播放页投屏钮同一条链）。
     * 请求头原版经 DlanListPop.genHeaderString 拼进地址串，本树以 CastVideo 的 headers 字段分开携带，投放端等价。
     */
    private void dlnaCast(String title, String playUrl, java.util.Map<String, String> headers) {
        if (getActivity() == null) return;
        com.fongmi.android.tv.bean.CastVideo video =
                new com.fongmi.android.tv.bean.CastVideo(title, playUrl, 0L, headers);
        com.fongmi.android.tv.ui.dialog.CastDialog.create().video(video).show(getActivity());
    }

    /**
     * 连续投屏 mode2/3（网页投屏/投屏直链）的原版执行端是 WebPlayerHelper + webdlan 子系统
     * （RemoteServerManager 本地服务 + dplayer/webs 网页资产），本树未移植该子系统（欠账，单独立项）。
     */
    private void webCast() {
        toastCenter("网页投屏暂未支持");
    }

    private void notifyParseStatusChanged() {
        if (parseStatusListPopup != null && parseStatusListPopup.getAdapter() != null) {
            parseStatusListPopup.getAdapter().notifyDataSetChanged();
        }
    }

    private void runUi(Runnable runnable) {
        android.app.Activity act = getActivity();
        if (act != null) act.runOnUiThread(runnable);
    }

    /**
     * 照抄原版 dealWithUrl：#noHistory# 剥离、hiker://page/、dealUrlSimply、javascript:、func://background、
     * x5:// 合成规则开页、X5/Webkit 嗅探、pics:// 图集、磁力/迅雷、.torrent、scheme 外部处理、图片预览、
     * 视频/音乐（直达播放或选集交接）、apk 下载、http/file 网页、未知链接 toast。
     */
    /** P2 取证：dealWithUrl 分支标记（点条目后空规则预取案的自证证据）。
     * 只记分支名/规则标题/URL 的 host 与扩展名，不记完整 URL；失败不影响主流程。 */
    private void dealBranchMark(String branch, String url) {
        try {
            String host = "", ext = "", title = "";
            try {
                android.net.Uri u = android.net.Uri.parse(url == null ? "" : url);
                host = u.getHost() == null ? "" : u.getHost();
                String seg = u.getLastPathSegment();
                if (seg != null) {
                    int q = seg.indexOf('?');
                    if (q >= 0) seg = seg.substring(0, q);
                    int d = seg.lastIndexOf('.');
                    if (d >= 0 && d < seg.length() - 1) ext = seg.substring(d + 1).toLowerCase(java.util.Locale.ROOT);
                }
            } catch (Throwable ignored) {}
            try {
                title = articleListRule != null && articleListRule.getTitle() != null ? articleListRule.getTitle() : "";
            } catch (Throwable ignored) {}
            SpiderDebug.log("hk", "dealWithUrl branch=%s rule=%s host=%s ext=%s", branch, title, host, ext);
        } catch (Throwable ignored) {}
    }

    /** 地址剥标记后只留真实页面部分：#标记、@rule=/@lazyRule= 尾巴、# 片段全剥，非 http(s) 回空。 */
    private static String cleanPageUrl(String raw) {
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

    private static String hostOfUrl(String u) {
        try {
            String host = android.net.Uri.parse(u == null ? "" : u).getHost();
            return host == null ? "" : host;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * pics 入口的真实页面地址：产出 pics 的那一页＝被点条目地址剥标记后的 http 地址
     * （懒规则 fetch 的就是它）；取不到时回退规则地址，但规则 host 须与至少一张图
     * 同域相关才认（与 HkPageActivity.hkComicPageUrl 同口径），否则留空走自动判定。
     * 改前 req.url 恒为 articleListRule.getUrl()（列表规则地址），被下游当
     * pageUrl/Referer 用、算出假域（漫小肆法医 §四）。
     */
    private String chapterPageUrl(HkItem item, java.util.List<String> pics) {
        String page = cleanPageUrl(item == null ? "" : item.getUrl());
        if (!page.isEmpty()) return page;
        String ruleUrl = articleListRule == null ? "" : articleListRule.getUrl();
        if (TextUtils.isEmpty(ruleUrl)) return "";
        String ruleHost = hostOfUrl(ruleUrl);
        if (ruleHost.isEmpty() || pics == null) return "";
        for (String p : pics) {
            String h = hostOfUrl(p);
            if (!h.isEmpty() && (ruleHost.equals(h) || ruleHost.contains(h) || h.contains(ruleHost))) {
                return ruleUrl;
            }
        }
        return "";
    }

    private void dealWithUrl(View view, String url, int position, String codeAndHeader) {
        if (position < 0 || adapter == null || position >= adapter.getList().size()) return;
        if (getContext() == null) return;
        if (getActivity() == null) return;
        HkItem item = adapter.getList().get(position);
        if (url.contains("#noHistory#")) {
            url = url.replaceFirst(java.util.regex.Pattern.quote("#noHistory#"), "");
            memoryPos = false;
        }
        if (isPageUrl(url)) {
            dealBranchMark("page", url);
            toNextPage(position, url);
            return;
        }
        if (detailBridge != null && detailBridge.dealUrlSimply(getActivity(), view, articleListRuleDTO, item, url,
                result -> dealWithUrl(view, result, position, codeAndHeader), null)) {
            dealBranchMark("scheme", url);
            if (webViewHolder != null) {
                String itemUrl = item.getUrl();
                if (itemUrl != null && (itemUrl.contains("refreshX5WebView") || itemUrl.contains("refreshPage"))) {
                    memoryPosition(position);
                } else if (url.contains("x5WebView://") || url.contains("refreshPage")) {
                    memoryPosition(position);
                }
            }
            return;
        }
        String itemTitle = getItemTitle(position);
        String actTitle = activityTitle();
        String titlePrefix = !TextUtils.isEmpty(actTitle) ? actTitle + "-" : actTitle;
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("javascript")) {
            dealBranchMark("javascript", url);
            memoryPosition(position);
            if (detailBridge != null) detailBridge.goWeb(getContext(), url);
            return;
        }
        if (lower.startsWith("func://background")) {
            dealBranchMark("background", url);
            if (listHost && hostListener != null) hostListener.onBackground();
            return;
        }
        if (url.startsWith("x5://")) {
            dealBranchMark("x5-sublist", url);
            String inner = url.replaceFirst("(?i)x5://", "");
            HkRule r = new HkRule();
            r.setUrl("hiker://empty");
            r.setFindRule("js:setResult([{\n    url:\"" + com.fongmi.android.tv.api.hk.HkJsRuntime.escapeJsString(inner) + "\",\ncol_type:\"x5_webview_single\",\ndesc:\"100%&&float\",\nextra:{canBack: true}\n}]);");
            r.setColType(HkColType.X5_WEB_VIEW.code);
            r.setGroup(articleListRule.getGroup());
            r.setTitle(articleListRule.getTitle());
            r.setPages(articleListRule.getPages());
            SubListRequest req = new SubListRequest();
            req.subRule = r;
            req.fromActivity = getContext().hashCode();
            req.parentTitle = actTitle;
            req.parentUrl = articleListRule.getUrl();
            req.title = titlePrefix + item.getTitle();
            req.newWindow = "true".equals(item.getExtra("newWindow"));
            req.windowId = item.getExtra("windowId");
            if (pageOpener != null) pageOpener.openSubList(req);
            memoryPosition(position);
            return;
        }
        if (detailBridge != null && detailBridge.canParseX5(url)) {
            dealBranchMark("x5parse", url);
            // 照原版 X5 支：inList 命中且 goWeb 成功才 return，否则继续 parse0
            if (HkFuck.inList(getActivity()) && HkFuck.goWeb(getContext(), myUrl, url)) return;
            boolean started = detailBridge.parseX5(getActivity(), url, extraJson(item),
                    result -> {
                        dismissLoadingPopup();
                        clickItem(view, position, result, false, codeAndHeader);
                    });
            if (started) showLoadingPopup("动态解析规则中，请稍候", false);
            return;
        }
        if (detailBridge != null && detailBridge.canParseWebkit(url)) {
            dealBranchMark("webkitparse", url);
            // 照原版 Webkit 支：inList 命中即 goWeb 后无条件 return（不看 goWeb 结果，原版不对称怪癖照抄）
            if (HkFuck.inList(getActivity())) {
                HkFuck.goWeb(getContext(), myUrl, url);
                return;
            }
            boolean started = detailBridge.parseWebkit(getActivity(), url, extraJson(item),
                    result -> {
                        dismissLoadingPopup();
                        clickItem(view, position, result, false, codeAndHeader);
                    });
            if (started) showLoadingPopup("动态解析规则中，请稍候", false);
            return;
        }
        if (lower.startsWith("pics://")) {
            dealBranchMark("pics", url);
            boolean fromLast = isClickLastPageBtn(articleListRule.getUrl(), actTitle, position);
            memoryPosition(position);
            // 拆分收口共用 HkRouter.parsePics（清标记、按行再按 "&&" 拆、trim
            // 滤空段，与 SiteApi/HkPageActivity 同口径）；旧写法裸 split("&&")
            // 不滤空不判空，空体（"pics://"）也直组会话、开空阅读器还没记录。
            java.util.ArrayList<String> pics =
                    new java.util.ArrayList<>(com.fongmi.android.tv.api.hk.HkRouter.parsePics(url));
            // 真实页面地址（产出 pics 的章节页），不再拿列表规则地址冒充：
            // req.url 下游当 pageUrl/Referer 用，假地址算出假域、图必死。
            String pageUrl = chapterPageUrl(item, pics);
            if (!com.fongmi.android.tv.api.hk.HkRouter.hasLoadablePic(pics)) {
                // 入口无图不许拦（fix4）：阅读器自带回退解析（会话图→章节串
                // →router.play 重取→书源），入口一拦全灭——1.4.19 真机
                // 「漫画详情页都进不去」的根。只强制留底，照常组会话进阅读器。
                SpiderDebug.forceLog("hk", "pics empty at article entry (proceed) rule=%s pics=%d page=%s",
                        articleListRule == null ? "" : articleListRule.getTitle(),
                        pics.size(), pageUrl);
            }
            PictureListRequest req = new PictureListRequest();
            req.pics = pics;
            req.ruleJson = new com.google.gson.Gson().toJson(articleListRule);
            req.url = pageUrl;
            req.title = detailBridge == null ? item.getTitle() : detailBridge.getTitleText(item.getTitle());
            req.chapters = getChapters(url, position, titlePrefix, codeAndHeader);
            if (listHost) {
                req.cUrl = articleListRule.getUrl();
                req.mTitle = actTitle;
            }
            req.fromLastPage = fromLast;
            int nowPage = 0;
            for (int i = 0; i < req.chapters.size(); i++) {
                if (req.chapters.get(i).use) nowPage = i;
            }
            req.nowPage = nowPage;
            if (detailBridge != null) detailBridge.openPictureList(getActivity(), req);
            return;
        }
        if (com.fongmi.android.tv.api.hk.HkRouter.isMagnetLink(url)) {
            // 磁力判据收归 HkRouter 正表（magnet: 全形，带不带问号同支＋thunder://），
            // 全树分发面（dealKind/fragment/simply/搜索）共用此一处、大小写敏感同官口径；
            // 旧形只认 magnet:? 前缀，无问号形会穿落。
            dealBranchMark("magnet", url);
            if (detailBridge != null) detailBridge.startDownloadMagnet(getContext(), url);
            return;
        }
        if (com.fongmi.android.tv.api.hk.HkRouter.isTorrentLink(url)) {
            dealBranchMark("torrent", url);
            if (detailBridge != null) detailBridge.startParseTorrent(getContext(), url);
            return;
        }
        if (detailBridge != null && detailBridge.isFTPOrEd2k(url)) {
            dealBranchMark("ftped2k", url);
            detailBridge.startParseFTPOrEd2k(getContext(), url,
                    result -> {
                        dismissLoadingPopup();
                        clickItem(view, position, result, false, codeAndHeader);
                    });
            return;
        }
        if (detailBridge != null && detailBridge.isScheme(url)) {
            dealBranchMark("scheme-chooser", url);
            detailBridge.findChooserToDeal(getContext(), url);
            return;
        }
        String ext = detailBridge == null ? "" : detailBridge.getFileExtensionFromUrl(url);
        if (detailBridge != null && detailBridge.isImage(url)) {
            dealBranchMark("image", url);
            android.widget.ImageView srcView = null;
            if (view instanceof android.widget.ImageView) {
                srcView = (android.widget.ImageView) view;
            } else if (view != null) {
                android.view.View found = view.findViewById(R.id.item_reult_img);
                if (found instanceof android.widget.ImageView) srcView = (android.widget.ImageView) found;
            }
            if (detailBridge.clearTag(url).equals(detailBridge.clearTag(item.getUrl()))) {
                java.util.List<String> urls = new ArrayList<>();
                int selectPos = collectNeighborImages(urls, position);
                detailBridge.showImageViewer(urls, selectPos, imageUrlPosMap, srcView);
            } else {
                detailBridge.showSingleImage(url, srcView);
            }
            return;
        }
        if (detailBridge != null && detailBridge.isVideoOrMusic(url)) {
            dealBranchMark("play", url);
            memoryPosition(position);
            if (url.startsWith("video://") && HkFuck.inList(getActivity())
                    && HkFuck.goWeb(getContext(), myUrl, url)) return;
            String cleared = detailBridge.clearTag(url);
            String itemCleared = detailBridge.clearTag(item.getUrl());
            // 照 8.83 ArticleListFragment 的分发极性：地址未被改写、或解析带了代码头、
            // 或网页解析器能接手——任一成立都走 getChapters 收全选集；三者皆否才单播。
            // 此前星辰写反了：解析过的地址反而走单播捷径，标题成了链接文字、选集只剩一条
            // （1.3.11 真机录屏实证）。playDataOf 是星辰自加的第四条触发（8.83 没有）：
            // 仅因带播放数据就收全页，会把帖子页的标题条/统计条/简介条等杂项一并收进
            // 选集（1.3.12 真机截图实证）。改为：仅播放数据触发时只带被点这一条走
            // 章节路径（播放数据的多线路展开仍在），不收全页。
            boolean collect = cleared.equals(itemCleared) || !TextUtils.isEmpty(codeAndHeader)
                    || detailBridge.canParseX5(item.getUrl()) || detailBridge.canParseWebkit(item.getUrl());
            boolean byPlayDataOnly = !collect && HkListActivity.playDataOf(url) != null;
            // 定根诊断：记 collect 定值与实际走的分支（单播/单集章节/收全选集），不记地址内容
            SpiderDebug.log("hk", "collect-branch collect=%b byPlayDataOnly=%b branch=%s", collect, byPlayDataOnly,
                    byPlayDataOnly ? "single-chapter" : (!collect ? "single" : "chapters"));
            // 电台问询（媒体支补问，与下方非媒体支的问询前置同口径）：媒体门
            // 认出的地址此前直奔 startHkPlay，那里只认规则名——规则名不含
            // 电台字时（如「一个APP」电台帖里的「播放音频」钮解出真音频
            // 地址），标题/钮面信号一次都不会被问，音频落视频/音乐链。
            // 在此先问 claimSignal：规则名命中（sig=1）不拦、仍走下方原
            // 分发（startHkPlay 内 routeHk 会认领）；标题/钮面命中
            // （sig>=2）按问询前置同款 askRadioDirect 直问路由器，未成
            // 原样落回下方分发，一行不变。
            {
                String rTitle = articleListRule == null ? null : articleListRule.getTitle();
                int sig = com.fongmi.android.tv.ui.HkRadioRouter.claimSignal(rTitle, actTitle, itemTitle);
                if (sig >= 2) {
                    dealBranchMark("radio-ask-media", url);
                    java.util.List<HkChapter> chs = byPlayDataOnly
                            ? singleChapter(url, position, titlePrefix, codeAndHeader)
                            : getChapters(url, position, titlePrefix, codeAndHeader);
                    int sel = 0;
                    for (int i = 0; i < chs.size(); i++) {
                        if (chs.get(i).use) {
                            sel = i;
                            break;
                        }
                    }
                    String albumName = !TextUtils.isEmpty(actTitle) ? actTitle : itemTitle;
                    if (askRadioDirect(getActivity(), rTitle, actTitle, itemTitle, url,
                            albumName, item.getPic() == null ? "" : item.getPic(), chs, sel)) {
                        return;
                    }
                    // 认领未成：落回下方原分发
                }
            }
            if (!collect && !byPlayDataOnly) {
                detailBridge.startPlayerSingle(getActivity(), itemTitle, url);
                return;
            }
            java.util.List<HkChapter> chapters = byPlayDataOnly
                    ? singleChapter(url, position, titlePrefix, codeAndHeader)
                    : getChapters(url, position, titlePrefix, codeAndHeader);
            android.os.Bundle bundle = new android.os.Bundle();
            String vc = detailBridge.getViewCollectionExtraData(getActivity(), articleListRule.getUrl());
            if (!TextUtils.isEmpty(vc)) bundle.putString("viewCollectionExtraData", vc);
            if (getActivity().getTitle() != null) {
                bundle.putString("film", getActivity().getTitle().toString());
            }
            bundle.putString("rule", new com.google.gson.Gson().toJson(articleListRule));
            // G3 线组 + G8 详情回采的交接附带数据（识别拿不准不写键，播放链原样）
            HkListActivity.enrichHandover(bundle, adapter == null ? null : adapter.getList(), chapters, actTitle);
            if (listHost) {
                detailBridge.startPlayerChapters(getActivity(), chapters, articleListRule.getUrl(), actTitle, bundle);
            } else {
                detailBridge.startPlayerChapters(getActivity(), chapters, myUrl, "", bundle);
            }
            return;
        }
        if (lower.startsWith("http") && "apk".equalsIgnoreCase(ext)) {
            dealBranchMark("apk", url);
            if (detailBridge != null) detailBridge.showDownloadDialog(getActivity(), item.getTitle(), url, null, com.fongmi.android.tv.api.hk.HkHttpParser.getHeaders(url));
            return;
        }
        // 电台问询前置（定根账候选二；咱家壳侧加的、非 8.83 原版）：媒体门
        // （HkUrlDetector）没认的 http 地址落 web 支之前，先给电台路由器一次
        // 认领机会。后缀门之外的电台流（.aac/.m4a/无后缀）此前在此被判网页、
        // 路由器一次都不会被问。认领信号两路（HkRadioRouter.claimSignal）：
        // 规则名命中（sig=1）走上面「视频/音乐」支的同款分发（startPlayer
        // Single/Chapters 内部 startHkPlay 会认领）；规则名不命中时再看子页
        // 标题/条目标题的电台特征（sig=2/3，他的电台在规则「一个APP」里、
        // 规则名永远不命中），命中则由本处直问路由器。都没中或认领未成，
        // 原样落 web 支，一行不变。
        if (lower.startsWith("http") && detailBridge != null) {
            String rTitle = articleListRule == null ? null : articleListRule.getTitle();
            int sig = com.fongmi.android.tv.ui.HkRadioRouter.claimSignal(rTitle, actTitle, itemTitle);
            if (sig == 1) {
                dealBranchMark("radio-ask", url);
                memoryPosition(position);
                String cleared = detailBridge.clearTag(url);
                String itemCleared = detailBridge.clearTag(item.getUrl());
                boolean collect = cleared.equals(itemCleared) || !TextUtils.isEmpty(codeAndHeader)
                        || detailBridge.canParseX5(item.getUrl()) || detailBridge.canParseWebkit(item.getUrl());
                boolean byPlayDataOnly = !collect && HkListActivity.playDataOf(url) != null;
                SpiderDebug.log("hk", "radio-ask collect=%b byPlayDataOnly=%b branch=%s", collect, byPlayDataOnly,
                        byPlayDataOnly ? "single-chapter" : (!collect ? "single" : "chapters"));
                if (!collect && !byPlayDataOnly) {
                    detailBridge.startPlayerSingle(getActivity(), itemTitle, url);
                    return;
                }
                java.util.List<HkChapter> chapters = byPlayDataOnly
                        ? singleChapter(url, position, titlePrefix, codeAndHeader)
                        : getChapters(url, position, titlePrefix, codeAndHeader);
                android.os.Bundle bundle = new android.os.Bundle();
                String vc = detailBridge.getViewCollectionExtraData(getActivity(), articleListRule.getUrl());
                if (!TextUtils.isEmpty(vc)) bundle.putString("viewCollectionExtraData", vc);
                if (getActivity().getTitle() != null) {
                    bundle.putString("film", getActivity().getTitle().toString());
                }
                bundle.putString("rule", new com.google.gson.Gson().toJson(articleListRule));
                // G3 线组 + G8 详情回采的交接附带数据（识别拿不准不写键，播放链原样）
                HkListActivity.enrichHandover(bundle, adapter == null ? null : adapter.getList(), chapters, actTitle);
                if (listHost) {
                    detailBridge.startPlayerChapters(getActivity(), chapters, articleListRule.getUrl(), actTitle, bundle);
                } else {
                    detailBridge.startPlayerChapters(getActivity(), chapters, myUrl, "", bundle);
                }
                return;
            }
            if (sig > 1) {
                dealBranchMark("radio-ask-title", url);
                memoryPosition(position);
                String cleared = detailBridge.clearTag(url);
                String itemCleared = detailBridge.clearTag(item.getUrl());
                boolean collect = cleared.equals(itemCleared) || !TextUtils.isEmpty(codeAndHeader)
                        || detailBridge.canParseX5(item.getUrl()) || detailBridge.canParseWebkit(item.getUrl());
                boolean byPlayDataOnly = !collect && HkListActivity.playDataOf(url) != null;
                java.util.List<HkChapter> chs = collect
                        ? getChapters(url, position, titlePrefix, codeAndHeader)
                        : singleChapter(url, position, titlePrefix, codeAndHeader);
                int sel = 0;
                for (int i = 0; i < chs.size(); i++) {
                    if (chs.get(i).use) {
                        sel = i;
                        break;
                    }
                }
                String albumName = !TextUtils.isEmpty(actTitle) ? actTitle : itemTitle;
                if (askRadioDirect(getActivity(), rTitle, actTitle, itemTitle, url,
                        albumName, item.getPic() == null ? "" : item.getPic(), chs, sel)) {
                    return;
                }
                // 认领未成：落回下面的 web 支
            }
        }
        if (lower.startsWith("http") || lower.startsWith("file://")) {
            dealBranchMark("web", url);
            memoryPosition(position);
            android.os.Bundle bundle = new android.os.Bundle();
            if (detailBridge != null) {
                String vc = detailBridge.getViewCollectionExtraData(getActivity(), articleListRule.getUrl());
                if (!TextUtils.isEmpty(vc)) bundle.putString("viewCollectionExtraData", vc);
            }
            if (getActivity().getTitle() != null) {
                bundle.putString("film", getActivity().getTitle().toString());
            }
            if (detailBridge != null) detailBridge.goWebWithExtraData(getContext(), url, bundle);
            return;
        }
        if (detailBridge != null && detailBridge.isCannotHandleScheme(lower)) {
            dealBranchMark("cannot-handle", url);
            detailBridge.findChooserToDeal(getContext(), url);
            return;
        }
        // 路由尾（对官 8.83 dealWithUrl 尾形）：官全包无「未知链接」
        // 一物——各支不中即静默 return。旧形在此自创 toast，lazy
        // 规则完成值 undefined（官 evalJS 同映射字面串、官 clickItem
        // 同直灌）一落此尾就吐「未知链接：undefined」（126 真机聚阅
        // 主页；他 2026-11-12 点名「未知链接很多小程序都有」即此
        // 类）。摘 toast，只留分支标记入日志备查。
        dealBranchMark("unhandled", url);
    }

    /**
     * 电台标题信号命中时的直问路由器（前置问询专用）：章节表在本地组扁平
     * 载荷（与 HkListActivity.startPlayerChapters 的单线路载荷同形态，地址
     * 过 playUrlForChapter 同款加工），交 HkRadioRouter.routeHkWithTitles
     * 建听书专辑。认领未成返回 false，调用方落回原分流。
     */
    private boolean askRadioDirect(android.app.Activity act, String ruleTitle, String pageTitle,
                                   String itemTitle, String episodeUrl, String albumName,
                                   String pic, java.util.List<HkChapter> chapters, int selIdx) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (HkChapter ch : chapters) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("name", !TextUtils.isEmpty(ch.title) ? ch.title
                        : (ch.memoryTitle == null ? "" : ch.memoryTitle));
                o.put("url", HkListActivity.playUrlForChapter(ch));
                o.put("pic", ch.pic == null ? "" : ch.pic);
                arr.put(o);
            }
            return com.fongmi.android.tv.ui.HkRadioRouter.routeHkWithTitles(act, ruleTitle,
                    pageTitle, itemTitle, "默认", episodeUrl, itemTitle, albumName,
                    pic, arr.toString(), selIdx);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private java.util.Map<Integer, Integer> imageUrlPosMap;

    /** 照抄原版图片分支的邻图收集：同 type 连续图片条目，向前最多 9 张、向后最多 16 张，返回当前条在图集中的下标，并回填 imageUrlPosMap（图集下标→条目下标）。 */
    private int collectNeighborImages(java.util.List<String> out, int position) {
        if (imageUrlPosMap == null) imageUrlPosMap = new java.util.HashMap<>();
        else imageUrlPosMap.clear();
        java.util.List<HkItem> list = adapter.getList();
        String type = list.get(position).getType();
        int start = position;
        int count = 0;
        int i = position;
        while (i >= 0) {
            HkItem it = list.get(i);
            if (!TextUtils.equals(type, it.getType())) break;
            if (detailBridge == null || !detailBridge.isImage(it.getUrl())) break;
            count++;
            if (count >= 9) break;
            start = i;
            i--;
        }
        int end = position;
        count = 0;
        i = position;
        while (i < list.size()) {
            HkItem it = list.get(i);
            if (!TextUtils.equals(type, it.getType())) break;
            if (detailBridge == null || !detailBridge.isImage(it.getUrl())) break;
            count++;
            if (count >= 16) break;
            end = i;
            i++;
        }
        int selectPos = 0;
        for (int j = start; j <= end; j++) {
            out.add(list.get(j).getUrl());
            imageUrlPosMap.put(out.size() - 1, j);
            if (j == position) selectPos = out.size() - 1;
        }
        return selectPos;
    }

    private String extraJson(HkItem item) {
        // extra 已是原始 JSON 串，直接返回
        return item.getExtra() == null ? "" : item.getExtra();
    }

    /**
     * 照抄原版 getChapters：以被点条目为 use=true 的当前集，向前/向后收集同组条目为选集。
     * 非 playlist 时向前只到同 type 连续段开头、向后遇不同 type 即止；playlist 时按 extra.cls 分组、全表收集。
     * 过滤：header、空 url、含 ignoreVideo/ignoreMusic、含 @rule= 的条目；每集带 codeAndHeader 时另存 originalUrl。
     */
    /** 只带被点这一条的章节表：构造与 getChapters 的当前集完全一致，不收同页其他条目。 */
    private java.util.List<HkChapter> singleChapter(String url, int position, String titlePrefix, String codeAndHeader) {
        java.util.List<HkChapter> chapters = new ArrayList<>();
        HkItem clicked = adapter.getList().get(position);
        HkChapter cur = new HkChapter();
        cur.memoryTitle = clicked.getTitle();
        cur.title = getResourceTitle(titlePrefix, position);
        cur.url = url;
        cur.use = true;
        cur.realPos = position;
        cur.extra = extraJson(clicked);
        addChapterPic(cur, position);
        if (!TextUtils.isEmpty(codeAndHeader)) {
            cur.codeAndHeader = codeAndHeader;
            cur.originalUrl = clicked.getUrl();
        }
        chapters.add(cur);
        return chapters;
    }

    private java.util.List<HkChapter> getChapters(String url, int position, String titlePrefix, String codeAndHeader) {
        java.util.List<HkChapter> chapters = new ArrayList<>();
        java.util.List<HkItem> list = adapter.getList();
        HkItem clicked = list.get(position);
        boolean playList = isPlayList(clicked);
        String playListCls = getPlayListCls(clicked);
        // 照 8.83 getChapters：前后都从全表收，只靠 chapterCandidate 过滤（跳 header 等），
        // 没有「遇不同 type 就停」的窗口。此前星辰自加了同 type 窗口，详情页里
        // 播放按钮与数字选集格 type 不同时，整排选集被窗口丢掉（1.3.11 录屏实证）。
        int start = 0;
        String clickedRawUrl = clicked.getUrl();
        for (int i = start; i < position; i++) {
            HkItem it = list.get(i);
            if (!chapterCandidate(it, playList, playListCls)) continue;
            if (isDupTitleEntry(it, clickedRawUrl)) continue;
            chapters.add(buildChapter(it, i, titlePrefix, codeAndHeader, false));
        }
        HkChapter cur = new HkChapter();
        cur.memoryTitle = clicked.getTitle();
        cur.title = getResourceTitle(titlePrefix, position);
        cur.url = url;
        cur.use = true;
        cur.realPos = position;
        cur.extra = extraJson(clicked);
        addChapterPic(cur, position);
        if (!TextUtils.isEmpty(codeAndHeader)) {
            cur.codeAndHeader = codeAndHeader;
            cur.originalUrl = clicked.getUrl();
        }
        chapters.add(cur);
        for (int i = position + 1; i < list.size(); i++) {
            HkItem it = list.get(i);
            if (!chapterCandidate(it, playList, playListCls)) continue;
            if (isDupTitleEntry(it, clickedRawUrl)) continue;
            chapters.add(buildChapter(it, i, titlePrefix, codeAndHeader, false));
        }
        return chapters;
    }

    /**
     * P1（用户拍板、偏离原版的自主优化）：海阔详情转点播选集时，去掉与被点条目同址的
     * 标题/文本类条目。两个条件同时满足才丢：①剥标记后 url 与被点条目的原始 url
     * 相同（丢的是重复地址，不丢内容）；②col_type 是文本类（text_1..5/text_center_1/
     * text_icon/rich_text/long_text，即详情页里渲染为标题区/文本行的那类，见官方
     * ArticleColTypeEnum 清单）。
     * 被点条目本身走 cur 分支恒保留、use 标记不动，选中索引不受影响。
     * 判别拿不准时宁可不丢：标题条目若用了另一种 url 形态（剥标记后与被点条目不等），
     * 照旧收进选集，不回退。
     */
    private boolean isDupTitleEntry(HkItem item, String clickedRawUrl) {
        if (item == null || TextUtils.isEmpty(clickedRawUrl)) return false;
        String a = HkUrlDetector.clearTag(item.getUrl());
        String b = HkUrlDetector.clearTag(clickedRawUrl);
        if (TextUtils.isEmpty(a) || !a.equals(b)) return false;
        String t = item.getColType();
        boolean textClass = t.startsWith("text") || "rich_text".equals(t) || "long_text".equals(t);
        if (!textClass) return false;
        try {
            SpiderDebug.log("hk", "drop-title-entry title=%s", item.getTitle());
        } catch (Throwable ignored) {
        }
        return true;
    }

    /**
     * 照抄原版 findMagnetList（ArticleListFragment.smali:8329）：以活动标题为前缀组装同组选集，
     * 只保留磁力/种子或 FTP/ed2k 的集，供迅雷带列表版入口使用。
     */
    private java.util.List<HkChapter> findMagnetList(String url, int position) {
        String title = activityTitle();
        if (!TextUtils.isEmpty(title)) title = title + "-";
        java.util.List<HkChapter> chapters = getChapters(url, position, title, null);
        java.util.List<HkChapter> result = new ArrayList<>();
        for (HkChapter chapter : chapters) {
            if (detailBridge.isMagnetOrTorrent(chapter.url) || detailBridge.isFTPOrEd2k(chapter.url)) {
                result.add(chapter);
            }
        }
        return result;
    }

    /** 照抄原版 lambda$clickItem$32：dealUrlSimply 的磁力 consumer——按链接类型分发迅雷带列表版入口。 */
    private void onMagnetRoute(int position, String magnetUrl) {
        if (detailBridge == null) return;
        java.util.List<HkChapter> list = findMagnetList(magnetUrl, position);
        if (detailBridge.isMagnetOrTorrent(magnetUrl)) {
            detailBridge.startDownloadMagnetList(getActivity(), magnetUrl, list);
        } else if (com.fongmi.android.tv.api.hk.HkRouter.isTorrentLink(magnetUrl)) {
            detailBridge.startParseTorrentList(getActivity(), magnetUrl, list);
        } else if (detailBridge.isFTPOrEd2k(magnetUrl)) {
            detailBridge.startParseFTPOrEd2kList(getActivity(), magnetUrl, list);
        }
    }

    /**
     * getChapters 的条目过滤（原版三段循环共用）：非 header、url 非空、不含 ignoreVideo/ignoreMusic/@rule=、playlist 时 cls 相同。
     * 星辰加一条（他 2026-10-15 拍板，原版不滤）：裸占位地址 hiker://empty 不进选集——
     * 这类条目是详情页的标题条/分节头/页脚（如黄色仓库 videoParse 的标题、相关推荐头、
     * 我是有底线的），本身没有播放目标、也没挂 lazy（挂了 lazy 的地址带 @lazyRule= 后缀、
     * 不是裸占位），进选集纯属杂条。能播的集只有三种形态：直链、@lazyRule=、磁力/ed2k，
     * 都不会是裸占位，不误伤。
     * 星辰再加一条（2026-10-31，定根账 hiker-scheme-diag）：裸导航协议
     * hiker://home@… / hiker://home / hiker://page/… 不进选集——这类是详情页的
     * 回主页/子页面导航钮（如橘汁的「← 返回列表」），不是播放目标；此前不过滤被
     * buildChapter 收为第 0 集，历史重播从它起播、裸串原样进播放器四发全灭。
     * 挂了 @lazyRule= 的同豁免，不误伤。
     */
    private boolean chapterCandidate(HkItem item, boolean playList, String playListCls) {
        if ("header".equals(item.getType())) return false;
        String url = item.getUrl();
        if (TextUtils.isEmpty(url)) return false;
        // 图集协议（协议级判据）：pics:// 是详情页头部的封面展示卡，8.83 里走图片
        // 分支、从不是视频集。此前它靠下面「url==pic」判等拦，但 url 带 pics://
        // 前缀、pic 是封面原串，判等被前缀绕过，片名卡混进选集当了第 1 集
        // （2026-10-23 真机复现）；按协议在源头挡，不再靠形态猜测。
        if (url.regionMatches(true, 0, "pics://", 0, "pics://".length())) return false;
        if (isEmptyPlaceholder(url)) return false;
        if (isHikerNavUrl(url)) return false;
        // ignoreVideo/ignoreMusic 是给通用嗅探看的标记，挂了 @lazyRule= 的条目由
        // 懒函数自己解析播放、正是真选集（91短剧全部 18 集都是
        // hiker://empty#ignoreVideo=true#@lazyRule= 形态，此前被这条误杀光、
        // 只剩误入的封面条和被点的那集，他 2026-10-15 真机实证）。无懒函数的才排除。
        if (!url.contains("@lazyRule=")
                && (url.contains("ignoreVideo") || url.contains("ignoreMusic"))) return false;
        if (url.contains("@rule=")) return false;
        // 详情页头部条：地址就是它自己的封面图（91短剧头部条 url=pic），不是能播的集。
        // 判等先剥 pics:// 前缀再比，堵前缀绕过（带前缀的已被上面协议判据先挡，
        // 这里是其余变体形态的冗余防线）。
        String pic = item.getPic();
        String urlBare = url.regionMatches(true, 0, "pics://", 0, "pics://".length())
                ? url.substring("pics://".length()) : url;
        if (!TextUtils.isEmpty(pic) && urlBare.equals(pic)) return false;
        if (playList && !isSamePlayList(playListCls, item)) return false;
        // 正表形态门（普查提醒二，hk-handover-audit-20261031 E3/G2）：上面是逐
        // 事故累积的排除表，拦不住没见过的新装饰/导航形态；这里再按「能播的集
        // 长什么样」收最后一道——直链/@lazyRule/PlayData/P2P 四形之外一律不
        // 收。被正表拦下的打一行日志（与误杀只隔一层：日后核样本能看到拦了
        // 什么，不再靠真机事故发现）。
        if (!isPlayableChapterForm(url)) {
            try {
                SpiderDebug.log("hk", "chapter dropped by form-gate url=%s title=%s",
                        url, item.getTitle());
            } catch (Throwable ignored) {
            }
            return false;
        }
        return true;
    }

    /**
     * 能播的集的地址正表（chapterCandidate 形态门）：四形——
     * ①带 @lazyRule= 的懒集（含 hiker://empty#标记#@lazyRule=）；
     * ②PlayData JSON（{ 起头且含 "urls"）；
     * ③P2P（ed2k:/magnet:/thunder:/ftp://，口径同 HkRouter.play 步骤 1.6）；
     * ④直链族：http/https/rtsp/rtmp/rtmps/udp/mms/file/content，以及分流层
     * 有支的 webRule://（嗅探）、video://（剥协议）、js:（整段求值）、
     * x5Play://（咱家自产壳，交接层另有分流），与无协议头的相对址/本地
     * 路径（播放器与嗅探的既有处理面）。
     * 四形之外（如 hiker:// 导航串的其余变体、未登记伪协议）回 false。
     */
    private static boolean isPlayableChapterForm(String url) {
        if (url.contains("@lazyRule=")) return true;
        String t = url.trim();
        if (t.startsWith("{") && t.contains("\"urls\"")) return true;
        String l = t.toLowerCase();
        if (com.fongmi.android.tv.api.hk.HkRouter.isP2pPlayLink(t)) return true;
        if (l.startsWith("js:")) return true;
        int sep = l.indexOf("://");
        if (sep < 0) return true;
        switch (l.substring(0, sep)) {
            case "http":
            case "https":
            case "rtsp":
            case "rtmp":
            case "rtmps":
            case "udp":
            case "mms":
            case "file":
            case "content":
            case "webrule":
            case "video":
            case "x5play":
                return true;
            default:
                return false;
        }
    }

    /**
     * 裸占位地址：hiker://empty 本体或只带 #/? 尾巴、且没挂解析函数的变体。
     * 挂了 @lazyRule= 的是真按钮（黄色仓库这类规则的选集按钮就是 $('hiker://empty#noLoading#').lazyRule(...) 形态，
     * 1.3.14 首版没排除这种情况、多集按钮被误杀只剩一集，2026-10-15 他真机截图实证后修正）。
     */
    private static boolean isEmptyPlaceholder(String url) {
        String u = url.trim();
        if (u.contains("@lazyRule=")) return false;
        return u.equals("hiker://empty") || u.startsWith("hiker://empty#") || u.startsWith("hiker://empty?");
    }

    /**
     * 裸导航协议：hiker://home@<规则名>（回规则主页）、hiker://home 本体/带尾巴、
     * hiker://page/…（子页面入口）。语义都是页面导航、从不是播放地址；挂了
     * @lazyRule= 的是真按钮、不在此列（同 isEmptyPlaceholder 的豁免口径）。
     * 前缀后必须紧跟 @ / ? # 之一或就是串尾，防 hiker://homepage、
     * hiker://pages 之类同字头异协议被误拦。
     */
    private static boolean isHikerNavUrl(String url) {
        if (url.contains("@lazyRule=")) return false;
        String u = url.trim().toLowerCase();
        return navPrefix(u, "hiker://home") || navPrefix(u, "hiker://page");
    }

    private static boolean navPrefix(String u, String prefix) {
        if (!u.startsWith(prefix)) return false;
        if (u.length() == prefix.length()) return true;
        char c = u.charAt(prefix.length());
        return c == '@' || c == '/' || c == '?' || c == '#';
    }

    private HkChapter buildChapter(HkItem item, int pos, String titlePrefix, String codeAndHeader, boolean use) {
        HkChapter chapter = new HkChapter();
        chapter.memoryTitle = item.getTitle();
        chapter.title = getResourceTitle(titlePrefix, pos);
        chapter.url = item.getUrl();
        chapter.extra = extraJson(item);
        chapter.realPos = pos;
        addChapterPic(chapter, pos);
        chapter.use = use;
        if (!TextUtils.isEmpty(codeAndHeader)) {
            chapter.codeAndHeader = codeAndHeader;
            chapter.originalUrl = item.getUrl();
        }
        return chapter;
    }

    /** 照抄原版 isPlayList：extra.cls 非空且含 "playlist"。 */
    private boolean isPlayList(HkItem item) {
        if (item == null) return false;
        String cls = item.getExtra("cls");
        return !TextUtils.isEmpty(cls) && cls.contains("playlist");
    }

    /** 照抄原版 getPlayListCls：extra.cls（无则 ""）。 */
    private String getPlayListCls(HkItem item) {
        if (item == null) return "";
        String cls = item.getExtra("cls");
        return cls == null ? "" : cls;
    }

    /** 照抄原版 isSamePlayList：extra.cls 全等。 */
    private boolean isSamePlayList(String cls, HkItem item) {
        if (item == null) return false;
        return TextUtils.equals(cls, item.getExtra("cls"));
    }

    /** 照抄原版 getResourceTitle：inheritTitle 时只取条目标题文本，否则前缀+标题文本。 */
    private String getResourceTitle(String prefix, int position) {
        HkItem item = adapter.getList().get(position);
        String titleText = detailBridge == null ? item.getTitle() : detailBridge.getTitleText(item.getTitle());
        if ("true".equals(item.getExtra("inheritTitle"))) return titleText;
        return (prefix == null ? "" : prefix) + titleText;
    }

    /** 照抄原版 addChapterPic：条目 pic 非空且非 "*" 时带入选集（细则见施工图待复核项）。 */
    private void addChapterPic(HkChapter chapter, int position) {
        String pic = adapter.getList().get(position).getPic();
        if (!TextUtils.isEmpty(pic) && !"*".equals(pic)) chapter.pic = pic;
    }

    /** 照抄原版 $5.onClick：rich_text/long_text 点击不走 clickItem（阅读主题翻页分支不移植、结构保留）；其余先置 memoryPos=true 再 clickItem。 */
    protected void onItemClick(View view, int position) {
        if (adapter == null || position < 0 || position >= adapter.getList().size()) return;
        String type = adapter.getList().get(position).getType();
        if (HkColType.RICH_TEXT.code.equals(type) || HkColType.LONG_TEXT.code.equals(type)) return;
        memoryPos = true;
        clickItem(view, position);
    }

    /** 照抄原版 $5.onUrlClick：memoryPos 先置 false，autoFixUrl 后 clickItem，再置回 true。 */
    protected void onItemUrlClick(View view, int position, String url) {
        memoryPos = false;
        String base = myUrl;
        if (TextUtils.isEmpty(base) && articleListRuleDTO != null) base = articleListRuleDTO.getUrl();
        String fixed = detailBridge == null || base == null ? url : detailBridge.autoFixUrl(base, url);
        clickItem(view, position, fixed);
        memoryPos = true;
    }

    /** 照抄原版 $5.onClassClick：按 desc 把 url 写进 DTO 对应组并设 firstHeader，然后 page=1 整页重载（params 第二参 true、ARTICLE_LIST_NEW）。 */
    protected void onClassClick(View view, String url, String desc) {
        SpiderDebug.log("hk", "classClick entry rule=%s desc=%s url=%s",
                articleListRuleDTO == null ? "" : articleListRuleDTO.getTitle(), desc, url);
        if (articleListRuleDTO == null || desc == null) return;
        switch (desc) {
            case "area":
                articleListRuleDTO.setAreaUrl(url);
                articleListRuleDTO.setFirstHeader("area");
                break;
            case "sort":
                articleListRuleDTO.setSortUrl(url);
                articleListRuleDTO.setFirstHeader("sort");
                break;
            case "year":
                articleListRuleDTO.setYearUrl(url);
                articleListRuleDTO.setFirstHeader("year");
                break;
            case "class":
                articleListRuleDTO.setClassUrl(url);
                articleListRuleDTO.setFirstHeader("class");
                break;
            default:
                break;
        }
        page = 1;
        loading(true);
        loadingPage = page;
        articleListModel.params(getContext(), page, true, articleListRuleDTO, notLoadPreRule(), isFromHistory())
                .process("ARTICLE_LIST_NEW", this);
    }

    /** 照抄原版 $5.onCodeClick：开代码查看页，标题固定「代码」。 */
    protected void onCodeClick(View view, int position, String code) {
        if (pageOpener != null) pageOpener.openCodeView(code);
    }

    /** 照抄原版 $5.onLoadMore（页脚点击）：加载中直接返回；无 fypage 时 toast「没有下一页了哦！」；否则 isLoading=true、page++ 走 ARTICLE_LIST。 */
    protected void onLoadMoreFooter(View view, int position) {
        if (isLoading) return;
        if (articleListRuleDTO == null || articleListRuleDTO.getUrl() == null
                || !articleListRuleDTO.getUrl().contains("fypage")) {
            toastBottom("没有下一页了哦！");
            return;
        }
        isLoading = true;
        page++;
        loading(true);
        loadingPage = page;
        articleListModel.params(getContext(), page, false, articleListRuleDTO, notLoadPreRule(), isFromHistory())
                .process("ARTICLE_LIST", this);
    }

    /**
     * 照抄原版 $5.onLongClick：hiker://home@ 规则条目（非 long_text）先走 longClickForRuleUrl 的「管理规则」五项菜单；
     * rich_text/long_text 弹双项对话框（自由复制/调试数据）；其余弹规则菜单（8 个标准项 + extra.longClick 附加项）。
     * 原版菜单是 XPopup 的 RuleMenuPopup，星辰用原生 HkCenterListPopup 等价。
     */
    protected void onItemLongClick(View view, int position) {
        if (adapter == null || position < 0 || position >= adapter.getList().size()) return;
        if (getContext() == null) return;
        HkItem item = adapter.getList().get(position);
        String rawUrl = item.getUrl();
        String url = rawUrl == null ? "" : rawUrl;
        String[] lazySplit = url.split("@rule=")[0].split("@lazyRule=");
        String type = item.getType();
        if (!TextUtils.isEmpty(url) && url.startsWith("hiker://home@")
                && !HkColType.LONG_TEXT.code.equals(type)) {
            longClickForRuleUrl(url);
            return;
        }
        if (HkColType.RICH_TEXT.code.equals(type) || HkColType.LONG_TEXT.code.equals(type)) {
            String[] items = {"自由复制", "调试数据"};
            HkDialogs.cards(requireContext(), null, items, null,
                    (dialog, which) -> dealLongMenuClick(view, which == 0 ? "自由复制" : "调试数据", position, lazySplit));
            return;
        }
        java.util.List<String> titles = new ArrayList<>(java.util.Arrays.asList(
                "访问链接", "网站首页", "下载资源", "批量下载", "连续投屏", "批量投屏", "调试数据", "复制链接"));
        java.util.List<String[]> longClicks = parseLongClicks(item);
        for (String[] lc : longClicks) titles.add(lc[0]);
        int standardCount = 8;
        HkCenterListPopup popup = new HkCenterListPopup(requireContext());
        popup.with(titles, 1, new HkCenterListPopup.ClickListener() {
            @Override
            public void click(String text, int index) {
                if (index < standardCount) {
                    dealLongMenuClick(view, text, position, lazySplit);
                } else if (index - standardCount < longClicks.size()) {
                    runLongClickExtra(view, position, longClicks.get(index - standardCount));
                }
            }

            @Override
            public void onLongClick(String text, int index) {
                click(text, index);
            }
        });
        popup.show();
    }

    /**
     * 照抄原版 longClickForRuleUrl（ArticleListFragment:1180）：解析 hiker://home@ 后的目标规则名——
     * 以 || 分隔多规则时逐个查已装规则（段以 http 开头直接 goWeb 打开），取首个命中；地址/规则对不上 toast 原文。
     * 命中后弹「管理规则：X」五项菜单（原版 XPopup asCenterList，星辰用 HkCenterListPopup 同位承载，标题与五项文案逐字）。
     */
    private void longClickForRuleUrl(String url) {
        String[] s = url.split("hiker://home@");
        if (s.length != 2 || TextUtils.isEmpty(s[1])) {
            toastBottom("地址有误：" + url);
            return;
        }
        String ruleName = null;
        String[] rules = s[1].split("\\|\\|");
        if (rules.length > 1) {
            for (String rule : rules) {
                if (rule.startsWith("http")) {
                    if (detailBridge != null) detailBridge.goWeb(getContext(), rule);
                    return;
                }
                if (com.fongmi.android.tv.api.hk.HkRuleManager.get().getInstalledRule(rule) != null) {
                    ruleName = rule;
                    break;
                }
            }
            if (ruleName == null) {
                toastBottom("找不到对应的规则：" + s[1]);
                return;
            }
        } else {
            ruleName = s[1];
        }
        final HkRule target = com.fongmi.android.tv.api.hk.HkRuleManager.get().getInstalledRule(ruleName);
        if (target == null) {
            toastBottom("找不到对应的规则：" + ruleName);
            return;
        }
        final String name = ruleName;
        HkCenterListPopup popup = new HkCenterListPopup(requireContext());
        popup.withTitle("管理规则：" + name);
        popup.with(java.util.Arrays.asList("编辑规则", "分享规则", "删除规则", "分享完整编码", "分享明文规则"), 1,
                new HkCenterListPopup.ClickListener() {
                    @Override
                    public void click(String text, int index) {
                        dealRuleManageClick(target, name, text);
                    }

                    @Override
                    public void onLongClick(String text, int index) {
                        click(text, index);
                    }
                });
        popup.show();
    }

    /** 「管理规则」五项分发（照原版 longClickForRuleUrl 的 switch；去向均为星辰既有件，不新造）。 */
    private void dealRuleManageClick(HkRule target, String ruleName, String text) {
        if (getContext() == null || text == null) return;
        switch (text) {
            case "编辑规则": {
                // 原版开 ArticleListRuleEditActivity 编辑该规则；星辰的规则表单编辑器在小程序首页（HkPageActivity），以标题直达。
                android.content.Intent intent = new android.content.Intent(requireContext(),
                        com.fongmi.android.tv.ui.activity.HkPageActivity.class);
                intent.putExtra("edit_rule_title", ruleName);
                startActivity(intent);
                break;
            }
            case "分享规则":
            case "分享明文规则":
                // 原版两项同路（ShareRuleUtil.shareRule 与「分享明文规则」分支代码等价），合流一处。
                shareRulePlainV2(target);
                break;
            case "分享完整编码":
                HkListActivity.shareRuleEncoded(getActivity(), new com.google.gson.Gson().toJson(target));
                break;
            case "删除规则":
                confirmDeleteRule(target, ruleName);
                break;
            default:
                break;
        }
    }

    /**
     * 照原版 ShareRuleUtil.shareRule（与「分享明文规则」同路）：规则 JSON 先过违禁词
     * （原版 FilterUtil.hasFilterWord，星辰接 HkHomeParser.filterWordChecker 同位），
     * 再组 base64://@标题@BASE64、以 ￥home_rule_v2￥ 口令复制剪贴板（原版 AutoImportHelper.shareWithCommand 落点与 toast）。
     */
    private void shareRulePlainV2(HkRule target) {
        String json = new com.google.gson.Gson().toJson(target);
        if (HkHomeParser.filterWordChecker != null && HkHomeParser.filterWordChecker.test(json)) {
            toastBottom("规则含有违禁词，禁止分享");
            return;
        }
        String base64Rule = "base64://@" + target.getTitle() + "@" + android.util.Base64.encodeToString(
                json.getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
        String command = "海阔视界规则分享，当前分享的是：小程序￥home_rule_v2￥" + base64Rule.replaceAll("\\s+", "");
        copyToClipboard(command);
        toastBottom("规则已复制到剪贴板");
    }

    /**
     * 照原版「删除规则」：确认弹窗（标题「温馨提示」、文案逐字，星辰以 HkDialogs 承载）→
     * HkRuleManager.delete（与小程序首页删规则同一落点）→ toast「删除成功」→ refresh 本页（原版宿主为列表页时同款）。
     */
    private void confirmDeleteRule(HkRule target, String ruleName) {
        if (getActivity() == null || getActivity().isFinishing()) return;
        new HkDialogs(requireContext())
                .setTitle("温馨提示")
                .setMessage("确定删除规则'" + target.getTitle() + "'吗？注意删除后无法恢复！")
                .setPositiveButton("确定", (dialog, which) -> Task.execute(() -> {
                    com.fongmi.android.tv.api.hk.HkRuleManager.get().delete(ruleName);
                    if (getActivity() != null && !getActivity().isFinishing()) {
                        getActivity().runOnUiThread(() -> {
                            toastBottom("删除成功");
                            refresh();
                        });
                    }
                }))
                .setNegativeButton("取消", null)
                .show();
    }

    /** 解析条目 extra 里的 longClick 附加项（原版 BaseExtra.longClick：[{title, js}]），返回 [title, js] 列表。 */
    private java.util.List<String[]> parseLongClicks(HkItem item) {
        java.util.List<String[]> out = new ArrayList<>();
        String raw = item.getExtra("longClick");
        if (TextUtils.isEmpty(raw)) return out;
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(raw);
            if (el.isJsonArray()) {
                for (com.google.gson.JsonElement e : el.getAsJsonArray()) {
                    if (!e.isJsonObject()) continue;
                    com.google.gson.JsonObject o = e.getAsJsonObject();
                    String title = o.has("title") ? o.get("title").getAsString() : "";
                    String js = o.has("js") ? o.get("js").getAsString() : "";
                    if (!TextUtils.isEmpty(title)) out.add(new String[]{title, js});
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** 照抄原版 $5 的 longClick 附加项执行：后台 evalJS（规则上下文 + MY_URL 由引擎桥拼装），input=附加项标题；结果非空且非 "undefined" 时回主线程 clickItem 回灌。 */
    private void runLongClickExtra(View view, int position, String[] titleAndJs) {
        HkArticleListModel.EngineBridge bridge = HkArticleListModel.getEngineBridge();
        if (bridge == null) return;
        String js = titleAndJs[1];
        String title = titleAndJs[0];
        Task.execute(() -> {
            String result = bridge.evalWithRule(articleListRule, js, title);
            if (!TextUtils.isEmpty(result) && !"undefined".equalsIgnoreCase(result)
                    && getActivity() != null && !getActivity().isFinishing()) {
                getActivity().runOnUiThread(() -> clickItem(view, position, result));
            }
        });
    }

    /** 照抄原版 dealLongMenuClick：13 个菜单项按标题分发（守卫 toast 与文案逐字）。 */
    private void dealLongMenuClick(View view, String title, int position, String[] lazySplit) {
        if (getContext() == null || title == null) return;
        switch (title) {
            case "下载资源":
                downloadSource(position, lazySplit);
                break;
            case "分享规则":
                if (detailBridge != null) detailBridge.shareRule(articleListRule, false);
                break;
            case "复制链接": {
                String itemUrl = getItemUrl(position, lazySplit);
                if (!TextUtils.isEmpty(itemUrl)) {
                    copyToClipboard(detailBridge == null ? itemUrl : detailBridge.getFirstPageUrl(itemUrl));
                } else {
                    toastBottom("链接为空，请检查规则");
                }
                break;
            }
            case "快速搜索": {
                HkItem item = adapter.getList().get(position);
                String t = detailBridge == null ? item.getTitle() : detailBridge.getTitleText(item.getTitle());
                if (TextUtils.isEmpty(t)) {
                    toastCenter("标题为空，无法搜索");
                    break;
                }
                android.content.SharedPreferences sp =
                        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
                String group = sp.getString("searchGroup", "全部");
                String engine = sp.getString("searchEngine", "百度");
                if (detailBridge != null) {
                    detailBridge.isRoute(getActivity(),
                            "hiker://search?simple=false&ruleGroup=" + group + "&rule=" + engine + "&s=" + t,
                            null, null);
                }
                break;
            }
            case "批量下载":
                if (batchDlanSelecting) {
                    toastCenter("当前正在批量投屏中，不支持此操作");
                    return;
                }
                showBatchDownloadConfirm();
                break;
            case "批量投屏":
                if (batchDownloadSelecting) {
                    toastCenter("当前正在批量下载中，不支持此操作");
                    return;
                }
                if (continuousDlanMode != -1) {
                    toastCenter("当前正在连续投屏中，不支持此操作");
                    return;
                }
                new HkDialogs(requireContext())
                        .setTitle("批量投屏")
                        .setMessage("点击确定后，点击页面中要投屏的内容即可快速添加到投屏队列，注意是投屏到世界直播，不是DLNA投屏！添加完成后请点击右下角的完成按钮结束批量投屏模式")
                        .setPositiveButton("确定", (dialog, which) -> {
                            dlanGeneration.incrementAndGet();
                            batchDlanSelecting = true;
                            showComplete(true);
                            toastBottom("已开启批量投屏模式");
                        })
                        .setNegativeButton("取消", null)
                        .show();
                break;
            case "更多分享":
                if (!mainHost) {
                    toastCenter("当前页面不支持此操作");
                    break;
                }
                if (detailBridge != null) detailBridge.shareRule(articleListRule, true);
                break;
            case "网站首页":
                if (detailBridge != null) {
                    detailBridge.goWeb(getContext(), detailBridge.getHomeUrl(articleListRuleDTO.getUrl()));
                }
                break;
            case "编辑规则":
                if (!mainHost) {
                    toastCenter("当前页面不支持此操作");
                    break;
                }
                if (pageOpener != null) {
                    pageOpener.openRuleEditor(new com.google.gson.Gson().toJson(articleListRule));
                }
                break;
            case "自由复制":
                if (detailBridge != null) {
                    detailBridge.showCopyPopup(adapter.getList().get(position).getTitle());
                }
                break;
            case "调试数据":
                try {
                    showPosDetail(position);
                } catch (Exception e) {
                    Log.e(TAG, "hk error", e);
                }
                break;
            case "访问链接": {
                String itemUrl = getItemUrl(position, lazySplit);
                if (!TextUtils.isEmpty(itemUrl)) {
                    android.os.Bundle bundle = new android.os.Bundle();
                    if (detailBridge != null) {
                        String vc = detailBridge.getViewCollectionExtraData(getActivity(), articleListRule.getUrl());
                        if (!TextUtils.isEmpty(vc)) bundle.putString("viewCollectionExtraData", vc);
                    }
                    if (getActivity() != null && getActivity().getTitle() != null) {
                        bundle.putString("film", getActivity().getTitle().toString());
                    }
                    if (detailBridge != null) {
                        detailBridge.goWebWithExtraData(getContext(), detailBridge.getFirstPageUrl(itemUrl), bundle);
                    }
                } else {
                    toastBottom("链接为空，请检查规则");
                }
                break;
            }
            case "连续投屏": {
                if (batchDownloadSelecting) {
                    toastCenter("当前正在批量下载中，不支持此操作");
                    return;
                }
                if (batchDlanSelecting) {
                    toastCenter("当前正在批量投屏中，不支持此操作");
                    return;
                }
                HkCenterListPopup popup = new HkCenterListPopup(requireContext());
                popup.withTitle("连续投屏模式");
                popup.with(java.util.Arrays.asList("DLNA投屏", "网页投屏", "投屏直链", "世界直播"), 2,
                        new HkCenterListPopup.ClickListener() {
                            @Override
                            public void click(String text, int index) {
                                dlanGeneration.incrementAndGet();
                                continuousDlanMode = index + 1;
                                synchronized (continuousDlanHis) {
                                    continuousDlanHis.clear();
                                }
                                toastBottom("已投屏所选内容，接下来点哪个投哪个哦~");
                                showComplete(false);
                                HkItem item = adapter.getList().get(position);
                                clickItem(view, position, item.getUrl());
                            }

                            @Override
                            public void onLongClick(String text, int index) {
                                click(text, index);
                            }
                        });
                popup.show();
                break;
            }
            default:
                break;
        }
    }

    /** 照抄原版批量下载确认弹窗（XPopup asConfirm 的原生等价）：「确定」进批量选择模式；「下载全部」再弹二次确认。 */
    private void showBatchDownloadConfirm() {
        new HkDialogs(requireContext())
                .setTitle("批量下载")
                .setMessage("点击确定后，点击页面中要下载的内容即可快速添加到下载队列，添加完成后请点击右下角的完成按钮结束批量下载模式")
                .setPositiveButton("确定", (dialog, which) -> {
                    batchDownloadSelecting = true;
                    if (notify_bg != null) notify_bg.setVisibility(View.VISIBLE);
                    toastBottom("已开启批量下载模式");
                })
                .setNegativeButton("下载全部", (dialog, which) -> showDownloadAllConfirm())
                .show();
    }

    /** 照抄原版「下载全部」二次确认弹窗：确定后 batchDownloadSelecting+batchDownloadAll 双置真、亮 notify 条。 */
    private void showDownloadAllConfirm() {
        new HkDialogs(requireContext())
                .setTitle("下载全部")
                .setMessage("如想要下载全部剧集，请先点击要下载的第一项，然后软件就会陆续解析当前选择和后面的所有剧集，解析到下载地址后会加入下载任务，注意在未完成所有下载任务添加前不要离开页面，添加完任务会弹窗提示，此时即可离开页面，确定开始吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    batchDownloadSelecting = true;
                    batchDownloadAll = true;
                    if (notify_bg != null) notify_bg.setVisibility(View.VISIBLE);
                    toastBottom("请点击要开始下载的第一项");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 对应原版 showComplete/CompleteToolBar：宿主完成工具栏在桥绑定阶段承接；未接入时以 notify 条的显隐代替（仅 show 侧）。 */
    private void showComplete(boolean show) {
        if (show && notify_bg != null) notify_bg.setVisibility(View.VISIBLE);
    }

    /** 照抄原版 getItemUrl：lazySplit[0] 为空或非 http 时，回退条目 extra 里的 url 字段（须非空且 http 开头）。 */
    private String getItemUrl(int position, String[] lazySplit) {
        String url = lazySplit.length > 0 ? lazySplit[0] : "";
        if (TextUtils.isEmpty(url) || !url.startsWith("http")) {
            try {
                String extraUrl = adapter.getList().get(position).getExtra("url");
                if (!TextUtils.isEmpty(extraUrl) && extraUrl.startsWith("http")) return extraUrl;
            } catch (Exception ignored) {
            }
        }
        return url;
    }

    /** 照抄原版 showPosDetail 入口：FileDetailPopup「调试数据」（数据组装与弹窗在桥绑定阶段承接）。 */
    private void showPosDetail(int position) {
        if (detailBridge != null) {
            detailBridge.showPosDetail(getActivity(), adapter.getList().get(position), articleListRule, position);
        }
    }

    private void copyToClipboard(String text) {
        if (getContext() == null || text == null) return;
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("", text));
    }

    /** 对应 ToastMgr.shortBottomCenter。 */
    private void toastBottom(String text) {
        if (getContext() == null) return;
        com.fongmi.android.tv.utils.ToastUtil.show(getContext(), text, android.widget.Toast.LENGTH_SHORT);
    }

    /** 宿主高亮编辑页按原版 SettingConfig.highlightRule 模式回填的静态槽（onActivityResult 0x2382 消费后清空）。 */
    public static String pendingHighlightResult = "";

    private android.webkit.ValueCallback<android.net.Uri[]> filePathCallback;

    /** 宿主行内 WebView 文件上传时登记（对应原版 filePathCallback 字段）。 */
    public void setFilePathCallback(android.webkit.ValueCallback<android.net.Uri[]> callback) {
        this.filePathCallback = callback;
    }

    /** 照抄原版 onActivityResult：0x2382 高亮回填、0x2383 WebView 文件上传回填、0x2385 fileSelect 回流。 */
    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0x2382) {
            if (highlightCallback != null) {
                highlightCallback.accept(pendingHighlightResult);
                highlightCallback = null;
                pendingHighlightResult = "";
            }
            return;
        }
        if (requestCode == 0x2383) {
            if (filePathCallback != null) {
                if (resultCode == android.app.Activity.RESULT_OK) {
                    if (data != null && data.getClipData() != null) {
                        android.content.ClipData clip = data.getClipData();
                        android.net.Uri[] uris = new android.net.Uri[clip.getItemCount()];
                        for (int i = 0; i < clip.getItemCount(); i++) {
                            uris[i] = clip.getItemAt(i).getUri();
                        }
                        filePathCallback.onReceiveValue(uris);
                    } else if (data != null && data.getData() != null) {
                        filePathCallback.onReceiveValue(new android.net.Uri[]{data.getData()});
                    } else {
                        filePathCallback.onReceiveValue(new android.net.Uri[]{null});
                    }
                } else if (resultCode == 0) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = null;
            }
            return;
        }
        if (requestCode == 0x2385) {
            if (TextUtils.isEmpty(fileSelectJs)) return;
            if (resultCode != android.app.Activity.RESULT_OK) return;
            android.net.Uri uri = data == null ? null : data.getData();
            if (uri == null) return;
            String js = fileSelectJs;
            fileSelectJs = null;
            String fileName = queryFileName(uri);
            String dirName = "_fileSelect_" + fileName;
            showLoadingPopup("文件提取中，请稍候", false);
            Task.execute(() -> {
                try {
                    if (getContext() == null) return;
                    java.io.File root = getContext().getExternalFilesDir(null);
                    if (root == null) root = getContext().getFilesDir();
                    java.io.File dir = new java.io.File(new java.io.File(root, "cache"), dirName);
                    if (!dir.exists()) dir.mkdirs();
                    java.io.File out = new java.io.File(dir, TextUtils.isEmpty(fileName) ? "file" : fileName);
                    copyUriToFile(uri, out);
                    onFileSelectCopied(uri, js, dirName, out.getAbsolutePath());
                } catch (Exception ignored) {
                    // 原版 failed() 为空实现：拷贝失败不关弹窗、不回流，照抄
                }
            });
        }
    }

    /** 照抄 $15.success：活动已结束则整段不执行；后台求值（MY_PATH=file uri 取其 path、否则取拷贝路径），结果回主线程关弹窗并 clickItem 回灌。 */
    private void onFileSelectCopied(android.net.Uri uri, String js, String input, String copiedPath) {
        if (getActivity() == null || getActivity().isFinishing()) return;
        HkArticleListModel.EngineBridge bridge = HkArticleListModel.getEngineBridge();
        if (bridge == null) return;
        String myPath = "file".equals(uri.getScheme()) ? uri.getPath() : copiedPath;
        String result = bridge.evalFileSelect(articleListRule, js, input, myUrl, myPath);
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                dismissLoadingPopup();
                if (!TextUtils.isEmpty(result) && !"undefined".equalsIgnoreCase(result)) {
                    clickItem(null, fileSelectPosition, result);
                }
            });
        }
    }

    /** 对应 UriUtils.getFileName：先查 DISPLAY_NAME，失败取路径最后一段。 */
    private String queryFileName(android.net.Uri uri) {
        String name = null;
        if (getContext() != null && "content".equals(uri.getScheme())) {
            try (android.database.Cursor c = getContext().getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) name = c.getString(idx);
                }
            } catch (Exception ignored) {
            }
        }
        if (TextUtils.isEmpty(name)) {
            String path = uri.getLastPathSegment();
            if (path != null) {
                int slash = path.lastIndexOf('/');
                name = slash >= 0 ? path.substring(slash + 1) : path;
            }
        }
        return name == null ? "" : name;
    }

    private void copyUriToFile(android.net.Uri uri, java.io.File out) throws java.io.IOException {
        try (java.io.InputStream in = getContext().getContentResolver().openInputStream(uri);
             java.io.OutputStream os = new java.io.FileOutputStream(out)) {
            if (in == null) throw new java.io.IOException("openInputStream null");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
        }
    }

    /** 照抄原版 lambda$initView$3（加载更多）：#autoReader# 走 autoReader(true, true)；无 fypage 时 #autoPage# 走自动翻页、否则直接收圈；有 fypage 才真翻页。 */
    private void onLoadMore() {
        String url = articleListRuleDTO == null ? "" : articleListRuleDTO.getUrl();
        if (url.contains("#autoReader#")) {
            autoReader(true, true);
            return;
        }
        if (!url.contains("fypage")) {
            if (url.contains("#autoPage#")) {
                autoPage(true, true);
            } else if (smartRefreshLayout != null) {
                smartRefreshLayout.finishLoadMore(true);
            }
            return;
        }
        page++;
        if (adapter == null || adapter.getItemCount() == 0) page = 1;
        loading(true);
        loadingPage = page;
        articleListModel.params(requireContext(), page, false, articleListRuleDTO,
                        notLoadPreRule(), isFromHistory())
                .process("ARTICLE_LIST", this);
    }

    /**
     * 照抄原版 autoReader(ZZ)（ArticleListFragment.smali:2277，源行 1957-2008）：
     * 宿主非列表页（官 FilmListActivity）或「无下一章」（nextPageUrl 空／
     * 加载中／nextPageUrl 与 myUrl 相等——读完判据）→ finishLoadMore(true)
     * 收圈 return；否则 page++ 后克隆 DTO、以 HttpParser.replaceQuery 把
     * originalPageUrl 的 pageUrl 参换成 encodeUrl(nextPageUrl) 重跑同一
     * 规则 ARTICLE_LIST 追加；同步段抛错 → loading(false)、page-- 回滚、
     * toast「出错：」＋msg。第二参官本体未读（params 第二元恒 false，
     * 见 process 调用），保留签名对位。
     * 续看位回写（lambda$autoReader$17，HeavyTaskUtil.postTaskToQueue）
     * 在 process 之前投递：以旧 currentPageUrl 定位历史/收藏改 url 为
     * 新址——咱载体 HkPageRecordStore 以（url＋标题）复合键定位，定位键
     * 以 pageRecordUrl()/pageRecordTitle() 追踪（初始＝DTO url＋页面标题）。
     */
    private void autoReader(boolean needLoading, boolean unused) {
        if (!(getActivity() instanceof HkListActivity)) {
            if (smartRefreshLayout != null) smartRefreshLayout.finishLoadMore(true);
            return;
        }
        if (TextUtils.isEmpty(nextPageUrl) || isLoading || TextUtils.equals(nextPageUrl, myUrl)) {
            if (smartRefreshLayout != null) smartRefreshLayout.finishLoadMore(true);
            return;
        }
        try {
            page++;
            if (needLoading) loading(true);
            else isLoading = true;
            HkRule clone = articleListRuleDTO.copyRule();
            String newUrl = HkHttpParser.replaceQuery(originalPageUrl, "pageUrl",
                    HkHttpParser.encodeUrl(nextPageUrl));
            currentPageUrl = newUrl;
            clone.setUrl(newUrl);
            // 咱载体的记录定位键是复合键：记录 url 初始＝DTO url、随每轮
            // 改写漂移（pageRecordUrl 追踪），与官以旧 currentPageUrl 单键
            // 定位同效；首轮两值可因 #noHistory# 剥除而不等，以记录键为准。
            final String recordUrl = pageRecordUrl();
            final String recordTitle = pageRecordTitle();
            Task.execute(() -> { // lambda$autoReader$17
                HkPageRecordStore store = HkPageRecordStore.shared();
                store.rewriteHistoryUrl(recordUrl, recordTitle, newUrl);
                store.rewriteCollectionUrl(recordUrl, recordTitle, newUrl);
            });
            pageRecordUrl = newUrl;
            loadingPage = page;
            articleListModel.params(requireContext(), page, false, clone,
                            notLoadPreRule(), isFromHistory())
                    .process("ARTICLE_LIST", this);
        } catch (Exception e) {
            loading(false);
            page--;
            toastBottom("出错：" + e.getMessage());
            Log.e(TAG, "autoReader", e);
        }
    }

    /** 照抄原版 setReadTheme（宿主 FilmListActivity/RuleWindowActivity 建面时注入）。 */
    public void setReadTheme(boolean readTheme) {
        this.isReadTheme = readTheme;
    }

    /** 对应原版 AutoPageData（宿主在 dealRule 打开 #autoPage# 子列表时构造，子页 Fragment 经 setAutoPageData 注入）。 */
    public static class AutoPageData {
        public java.util.List<HkItem> nextData;
        public String url;
        public String title;
        public int currentPos;
        public boolean noRecordHistory;
        public int chapterCount;
        public int chapterStartIndex;
    }

    private AutoPageData autoPageData;

    /** 宿主注入（对应原版 FilmListActivity.getParentData()/tempParentData）。 */
    public void setAutoPageData(AutoPageData data) {
        this.autoPageData = data;
    }

    /** 宿主记录续看位置时取条目（原版宿主与 Fragment 同进程可直接读列表，等价开放）。 */
    public HkItem getItemAt(int position) {
        if (adapter == null || position < 0 || position >= adapter.getList().size()) return null;
        return adapter.getList().get(position);
    }

    /**
     * 照抄原版 autoPage(boolean p1, boolean recordHistory)：非列表宿主或无续页数据时只收加载圈；
     * 加载中直接返回；否则 page++ 后以 nextData[page-1] 的 url 组装 DTO 克隆（hiker://page/ 走 PageParser.getUrl、
     * 其余只换 url 的 ";" 首段并剥 @rule=），params=next 条目 extra，ARTICLE_LIST 续页；
     * 异常时 loading(false)、page-- 并 toast「出错：」+消息。
     */
    private void autoPage(boolean p1, boolean recordHistory) {
        if (!listHost) {
            if (smartRefreshLayout != null) smartRefreshLayout.finishLoadMore(true);
            return;
        }
        AutoPageData data = autoPageData;
        if (data == null || data.nextData == null || data.nextData.isEmpty()) {
            if (smartRefreshLayout != null) smartRefreshLayout.finishLoadMore(true);
            return;
        }
        if (isLoading) return;
        try {
            page++;
            if (p1) loading(true);
            else isLoading = true;
            HkRule dto = articleListRuleDTO.copyRule();
            if (page > data.nextData.size()) {
                loading(false);
                page--;
                return;
            }
            HkItem next = data.nextData.get(page - 1);
            if (next.getUrl() == null) {
                loading(false);
                page--;
                return;
            }
            if (next.getUrl().startsWith("hiker://page/")) {
                HkArticleListModel.EngineBridge bridge = HkArticleListModel.getEngineBridge();
                String resolved = bridge == null ? next.getUrl()
                        : bridge.pageParserGetUrl(next.getUrl(), extraJson(next));
                dto.setUrl(resolved);
            } else {
                String[] parts = dto.getUrl() == null ? new String[]{""} : dto.getUrl().split(";");
                parts[0] = next.getUrl().split("@rule=")[0].split(";")[0];
                dto.setUrl(arrayToString(parts, 0, ";"));
            }
            dto.setParams(extraJson(next));
            loadingPage = page;
            articleListModel.params(getContext(), page, false, dto, notLoadPreRule(), isFromHistory())
                    .process("ARTICLE_LIST", this);
            if (!data.noRecordHistory && recordHistory && page <= data.nextData.size()) {
                if (hostListener != null) {
                    hostListener.onAutoPageLastClick(data, data.nextData.get(page - 1), data.currentPos + page - 1);
                }
            }
        } catch (Exception e) {
            loading(false);
            page--;
            toastBottom("出错：" + e.getMessage());
            Log.e(TAG, "hk error", e);
        }
    }

    /** 照抄原版 loadChapterCount：统计被点条目所在同 type 连续段的总长（chapterCount）与被点条目在段内的 1 起序号（chapterStartIndex），count>0 才写入。 */
    private void loadChapterCount(AutoPageData data, int position) {
        if (adapter == null || position < 0 || position >= adapter.getList().size()) return;
        java.util.List<HkItem> list = adapter.getList();
        String type = list.get(position).getType();
        int start = position;
        for (int i = position - 1; i >= 0; i--) {
            if (!TextUtils.equals(type, list.get(i).getType())) break;
            start = i;
        }
        int count = position - start;
        int startIndex = count + 1;
        for (int i = position; i < list.size(); i++) {
            if (!TextUtils.equals(type, list.get(i).getType())) break;
            count++;
        }
        if (count > 0) {
            data.chapterCount = count;
            data.chapterStartIndex = startIndex;
        }
    }

    // —— 其余 initView 挂载点（随各切片转写）——

    /** 照抄原版 hookBackJs：给 onRefreshJS 注入 back() 定义（宿主 method_back 在引擎侧承接）。 */
    private String hookBackJs() {
        return "var backTag = false;\nback = (param0) => {\n   if(!backTag) {       method_back.invoke(javaContext, param0);\n       backTag = true;\n   } else log('back 调用多次被拦截')\n}\n";
    }

    /** 照抄原版 lambda$refresh$10（refresh 的后台段）：取走并清空 onRefreshJS，拼 getMyRule+hookBackJs 后求值，再回 UI 线程走 lambda$refresh$9 重新取数。 */
    private void runOnRefreshJs() {
        String js = onRefreshJS;
        onRefreshJS = null;
        HkArticleListModel.EngineBridge bridge = HkArticleListModel.getEngineBridge();
        if (bridge != null && js != null) {
            bridge.evalWithRule(articleListRule, hookBackJs() + js, "");
        }
        if (getActivity() != null && !getActivity().isFinishing()) {
            getActivity().runOnUiThread(() -> {
                loadingPage = page;
                articleListModel.params(requireContext(), page, true, articleListRuleDTO,
                                notLoadPreRule(), isFromHistory())
                        .process("ARTICLE_LIST_NEW", this);
            });
        }
    }

    private void onFastScrollPressed(boolean pressed) {
    }

    private void onFastScrollOffset(int offset) {
    }

    private void onListScrolled(int dx, int dy) {
    }

    private void onNotifyQueueClick() {
        // 照原版 CompleteToolBar 的 onStatus（$4）：批量投屏中开队列页（mapHistory 全量，含解析中/失败条目）；
        // 连续投屏中开「投屏历史」页（continuousDlanHis 逐条，状态「已投屏」）
        if (getActivity() == null) return;
        if (batchDlanSelecting) {
            List<HkRemotePlaySource> sources;
            synchronized (batchDlanParamsMapHistory) {
                sources = new ArrayList<>(batchDlanParamsMapHistory.values());
            }
            parseStatusListPopup = new com.fongmi.android.tv.ui.hk.view.HkParseStatusPopup(getActivity(), sources);
            parseStatusListPopup.show();
        } else if (continuousDlanMode != -1) {
            List<HkRemotePlaySource> his = new ArrayList<>();
            List<Integer> hisSnapshot;
            synchronized (continuousDlanHis) {
                hisSnapshot = new ArrayList<>(continuousDlanHis);
            }
            for (int pos : hisSnapshot) {
                HkRemotePlaySource source = new HkRemotePlaySource();
                source.setTitle(getItemTitle(pos));
                source.setParseFinish(true);
                source.setParseResult("已投屏");
                his.add(source);
            }
            parseStatusListPopup = new com.fongmi.android.tv.ui.hk.view.HkParseStatusPopup(getActivity(), his, "投屏历史");
            parseStatusListPopup.show();
        }
    }

    private void onNotifyClick() {
        completeDlan();
    }

    private boolean onNotifyLongClick() {
        // 原版 CompleteToolBar 另有 onCancel 入口，本树完成条无独立取消钮，长按完成条承接 cancelDlan
        if (batchDownloadSelecting || batchDlanSelecting || continuousDlanMode != -1) {
            cancelDlan();
            return true;
        }
        return false;
    }

    /**
     * 照抄原版 completeDlan（点「完成」）：批量下载→走 finishBatchMode 收尾；
     * 批量投屏→有剧集在动态解析中则 toast「有剧集在动态解析中，请稍候」且不退出，
     * 队列非空则 toast「已开始批量投屏并播放」并整队交世界直播批量投屏，随后清队列收尾；
     * 连续投屏→toast「已结束连续投屏模式」后收尾。
     */
    private void completeDlan() {
        if (batchDlanSelecting) {
            if (batchDlanParseCount.get() > 0) {
                toastCenter("有剧集在动态解析中，请稍候");
                return;
            }
            if (!batchDlanParamsSet.isEmpty()) {
                toastCenter("已开始批量投屏并播放");
                if (getActivity() != null) {
                    HkLivePlayerHelper.finishBatchDlan(getActivity(), new ArrayList<>(batchDlanParamsSet));
                }
            }
            finishBatchMode();
            return;
        }
        if (continuousDlanMode != -1) {
            toastCenter("已结束连续投屏模式");
            finishBatchMode();
            return;
        }
        finishBatchMode();
    }

    /** 照抄原版 cancelDlan：清计数/队列/历史、复位文案、收完成条，不执行投屏（收尾与 finishBatchMode 同口径）。 */
    private void cancelDlan() {
        finishBatchMode();
    }

    /** 点「完成」退出批量模式：复位全部模式标志与计数、收起 notify 条，并经桥回调通知宿主收尾。 */
    private void finishBatchMode() {
        // 1.4.16 修复（扫查高2）：取消/完成统一在此递增批次代号，使全部在飞懒解析回调失效
        dlanGeneration.incrementAndGet();
        String mode = null;
        int count = 0;
        if (batchDownloadSelecting) {
            mode = "batchDownload";
            count = batchDownloadCount;
        } else if (batchDlanSelecting) {
            mode = "batchDlan";
        } else if (continuousDlanMode != -1) {
            mode = "continuousDlan";
        }
        batchDownloadSelecting = false;
        batchDownloadAll = false;
        batchDownloadCount = 0;
        batchDlanSelecting = false;
        continuousDlanMode = -1;
        synchronized (continuousDlanHis) {
            continuousDlanHis.clear();
        }
        batchDlanParamsSet.clear();
        synchronized (batchDlanParamsMapHistory) {
            batchDlanParamsMapHistory.clear();
        }
        batchDlanParseCount.set(0);
        batchDlanParseSize.set(0);
        if (notify_text != null) notify_text.setText("完 成");
        if (notify_bg != null) notify_bg.setVisibility(View.GONE);
        if (mode != null && detailBridge != null) detailBridge.onBatchModeFinish(mode, count);
    }

    private int dpToPx(int dp) {
        return com.fongmi.android.tv.utils.ResUtil.dp2px(dp);
    }

    private int pxToDp(int px) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (px / density + 0.5f);
    }

    // —— 对外访问器（宿主 Activity 用，对应原版同名 getter/setter 集）——

    public List<HkItem> getArticleList() {
        return adapter == null ? new ArrayList<>() : adapter.getList();
    }

    public String getMyUrl() {
        return myUrl;
    }

    public HkWebViewHolder getWebViewHolder() {
        return webViewHolder;
    }

    public boolean isImmersiveTheme() {
        return immersiveTheme;
    }

    public void setImmersiveTheme(boolean immersiveTheme) {
        this.immersiveTheme = immersiveTheme;
        if (adapter != null) adapter.setImmersiveTheme(immersiveTheme);
    }

    public void setLoadListener(LoadListener loadListener) {
        this.loadListener = loadListener;
    }

    public LoadListener getLoadListener() {
        return loadListener;
    }

    public void setScrollListener(RecyclerView.OnScrollListener scrollListener) {
        this.scrollListener = scrollListener;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 环29 复核订正 B1：本面 WebView 销毁即注销承载登记（按宿主身份匹配才清）。
        if (getActivity() != null) HkWebCarrier.unregister(getActivity());
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        if (loadingPopup != null) {
            if (loadingPopup.isShowing()) loadingPopup.dismiss();
            loadingPopup = null;
        }
        if (webViewHolder != null) {
            android.webkit.WebView wv = webViewHolder.getWebView();
            if (wv != null && wv.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) wv.getParent()).removeView(wv);
            }
            HkWebkitHolder kh = webViewHolder.getWebkitHolder();
            if (kh != null) {
                kh.onDestroy();
            } else if (wv != null) {
                wv.stopLoading();
                wv.onPause();
                wv.destroy();
            }
            webViewHolder = null;
        }
    }
}
