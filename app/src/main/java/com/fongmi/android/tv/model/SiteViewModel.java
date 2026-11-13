package com.fongmi.android.tv.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.extractor.Thunder;
import com.fongmi.android.tv.player.karaoke.KaraokeResult;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class SiteViewModel extends ViewModel {

    private final MutableLiveData<Result> result;
    private final MutableLiveData<Result> search;
    private final MutableLiveData<SearchProgress> searchProgress;
    private final MutableLiveData<Result> action;
    private volatile String lastActionKey = "";

    private final List<ExecHandle> searchHandles;
    private final AtomicInteger searchEpoch;
    private final AtomicInteger resultEpoch;
    private final AtomicInteger actionEpoch;
    private final AtomicReference<ExecHandle> resultTask;
    private final AtomicReference<ExecHandle> actionTask;
    private KaraokeResult karaokeResult;
    private int karaokeResultAction;

    public SiteViewModel() {
        result = new MutableLiveData<>();
        search = new MutableLiveData<>();
        searchProgress = new MutableLiveData<>();
        action = new MutableLiveData<>();
        searchEpoch = new AtomicInteger(0);
        searchHandles = new CopyOnWriteArrayList<>();
        resultEpoch = new AtomicInteger(0);
        actionEpoch = new AtomicInteger(0);
        resultTask = new AtomicReference<>();
        actionTask = new AtomicReference<>();
    }

    /**
     * 一发 execute 的任务句柄：外层 future 先建好供回调挂载，真正的计时 future 等
     * 任务在池里真正开跑那刻才建（超时从开跑算）。顶替/销毁时 cancel 先断外层、
     * 再断计时 future，在途任务真停，不留超时后继续占池回压的僵尸。
     */
    private static final class ExecHandle {

        private final SettableFuture<Result> outer = SettableFuture.create();
        private final AtomicReference<Future<?>> timed = new AtomicReference<>();

        SettableFuture<Result> future() {
            return outer;
        }

        boolean isDone() {
            return outer.isDone();
        }

        void success(Result result) {
            outer.set(result);
        }

        void fail(Throwable error) {
            outer.setException(error);
        }

        void attach(Future<?> future) {
            timed.set(future);
            if (outer.isCancelled()) future.cancel(true);
        }

        void cancel() {
            outer.cancel(true);
            Future<?> future = timed.get();
            if (future != null) future.cancel(true);
        }
    }

    public LiveData<Result> getResult() {
        return result;
    }

    public LiveData<Result> getSearch() {
        return search;
    }

    public LiveData<SearchProgress> getSearchProgress() {
        return searchProgress;
    }

    public LiveData<Result> getAction() {
        return action;
    }

    public KaraokeResult getKaraokeResult() {
        return karaokeResult;
    }

    public int getKaraokeResultAction() {
        return karaokeResultAction;
    }

    public void setKaraokeResult(KaraokeResult result, int action) {
        karaokeResult = result;
        karaokeResultAction = action;
    }

    public void clearKaraokeResult() {
        karaokeResult = null;
        karaokeResultAction = 0;
    }

    public SiteViewModel init() {
        search.setValue(null);
        searchProgress.setValue(null);
        result.setValue(null);
        action.setValue(null);
        return this;
    }

    public void homeContent() {
        homeContent(false);
    }

    public void homeContent(boolean forceRefresh) {
        com.fongmi.android.tv.bean.Site home = VodConfig.get().getHome();
        // 作废旧首页加载的归属在 SiteApi：新加载真正开跑时认领新批次、只掐上一批
        // 自己登记的子任务。提交侧（这里）与销毁侧（onCleared）都不再全局作废——
        // 全局掐会连坐掐掉无关的在途加载，无 message 的 CancellationException
        // 正是从这来；慢网下进一次详情、返回首页就"打不开"也是这个连坐。
        // 冷启动约 200ms 内 viewmodel execute failed type=RESULT site=（空）、URL 为空：
        // 未配置首页源时 home 是空 Site（getHome() 永不返回 null，空 Site 的 key/api 都是 ""），
        // SiteApi.homeContent 一路走到 OkHttp.newCall("") 才抛 no-scheme。execute 入口的空 key
        // 拦截只看得到 key，看不到 api，所以这里先把 home 为 null／key 为空／api 为空三
        // 种情况都判出来：siteKey 传 "" 走 execute 的空 key 拦截，可见错由 execute 统一发，
        // 不再把空 key/api 的 lambda 交给 SiteApi（lambda 在拦截下根本不会被执行）。
        boolean homeBad = home == null || home.getKey().isEmpty() || home.getApi().isEmpty();
        // 冷启动第一次 py 首页加载：外层给 90 秒预算（冷链：蜘蛛首次加载＋依赖下载＋站点取数），
        // 热了之后一律 30 秒不动。预算由 SiteApi.homeTimeoutFor 按冷热判定。
        // 超时/被取消时子任务的清理归 SiteApi 自己（它认得自己那一批，就地掐），
        // 这里不再挂连带作废的错误回调。外层任务走大池：它要阻塞等两个子任务，
        // 不许占共享 5 线程池把分类/详情/搜索堵在后面。
        execute(TaskType.RESULT, result, () -> SiteApi.homeContent(home, forceRefresh), null, null,
                SiteApi.homeTimeoutFor(home), homeBad ? "" : home.getKey(), Task.largeExecutor());
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) {
        // 超时口径与首页对齐：冷 py 蜘蛛头一发给冷宽限，其余 30 秒（SiteApi.categoryTimeoutFor）。
        execute(TaskType.RESULT, result, () -> SiteApi.categoryContent(key, tid, page, filter, extend), null, null, SiteApi.categoryTimeoutFor(key), key);
    }

    public void action(String key, String act) {
        // 记下最近一发动作的源 key：动作结果里若带表单，消费端回填提交时要按这个 key 再调 action。
        lastActionKey = key == null ? "" : key;
        // 动作链走自己的长超时（慢命令跑完才算完），不与点播共用 30 秒。
        execute(TaskType.ACTION, action, () -> SiteApi.action(key, act), null, null, Constant.TIMEOUT_ACTION, key);
    }

    public String getLastActionKey() {
        return lastActionKey;
    }

    public void detailContent(String key, String id) {
        detailContent(key, id, false);
    }

    /**
     * 详情页轻量取数：跳过 Source.parse 的选集展开（磁力 DHT 最长 60 秒），
     * 主数据一到界面先画线路/选集，展开由界面层在后台跑完再补。播放链不受影响。
     */
    public void detailContentLight(String key, String id) {
        execute(TaskType.RESULT, result, () -> SiteApi.detailContent(key, id, false, false), null, null, Constant.TIMEOUT_VOD, key);
    }

    public void detailContent(String key, String id, boolean forceRefresh) {
        // 推送/磁力详情要在 Source.parse 里拉种子元数据（parse 侧上限 60 秒），
        // 外层沿用 30 秒会先把任务掐掉——首次粘贴、DHT 稍慢必失败。对齐到 75 秒。
        boolean p2p = SiteApi.PUSH.equals(key) || (id != null && Thunder.Parser.match(id));
        execute(TaskType.RESULT, result, () -> SiteApi.detailContent(key, id, forceRefresh), null, null,
                p2p ? Constant.TIMEOUT_PUSH_DETAIL : Constant.TIMEOUT_VOD, key);
    }

    public void searchContent(Site site, String keyword, boolean quick, String page) {
        long start = System.currentTimeMillis();
        execute(TaskType.RESULT, result, SearchTask.create(site, keyword, quick, page),
                result -> SiteHealthStore.recordSearch(site, true, result.getList().size(), System.currentTimeMillis() - start, ""),
                error -> SiteHealthStore.recordSearch(site, false, 0, System.currentTimeMillis() - start, error.getMessage()),
                Constant.TIMEOUT_VOD, site == null ? "" : site.getKey());
    }

    public void searchContent(List<Site> sites, String keyword, boolean quick) {
        int epoch = stopSearch();
        List<Site> tasks = new ArrayList<>();
        for (Site site : sites) {
            if (quick && !site.isQuickSearch()) continue;
            tasks.add(site);
        }
        int total = tasks.size();
        AtomicInteger completed = new AtomicInteger();
        searchProgress.postValue(SearchProgress.start(total));
        for (Site site : tasks) {
            long start = System.currentTimeMillis();
            // 超时从开跑算（与 execute 同口径）：先占住大池线程，任务真正开跑那刻
            // 才建计时 future 起表；旧形 submit 后立即 withTimeout，排队时间也计入，
            // 多源齐搜时排队靠后的源没开跑就被判超时。
            ExecHandle handle = new ExecHandle();
            searchHandles.add(handle);
            Task.largeExecutor().execute(() -> {
                if (handle.isDone()) return;
                ListenableFutureTask<Result> task = ListenableFutureTask.create(SearchTask.create(site, keyword, quick));
                FluentFuture<Result> timed = FluentFuture.from(task).withTimeout(Constant.TIMEOUT_SEARCH, TimeUnit.MILLISECONDS, Task.scheduler());
                handle.attach(timed);
                if (handle.isDone()) return;
                Futures.addCallback(timed, Task.callback(handle::success, handle::fail), MoreExecutors.directExecutor());
                task.run();
            });
            Futures.addCallback(handle.future(), Task.callback(
                    result -> {
                        if (searchEpoch.get() != epoch) return;
                        SiteHealthStore.recordSearch(site, true, result.getList().size(), System.currentTimeMillis() - start, "");
                        postSearchResult(epoch, result);
                        postSearchProgress(epoch, completed, total);
                    },
                    error -> {
                        if (searchEpoch.get() != epoch) return;
                        if (error instanceof CancellationException) return;
                        SiteHealthStore.recordSearch(site, false, 0, System.currentTimeMillis() - start, error.getMessage());
                        postSearchProgress(epoch, completed, total);
                        error.printStackTrace();
                    }
            ), MoreExecutors.directExecutor());
        }
    }

    private void postSearchResult(int epoch, Result result) {
        App.post(() -> {
            if (searchEpoch.get() == epoch) search.setValue(result);
        });
    }

    private void postSearchProgress(int epoch, AtomicInteger completed, int total) {
        if (searchEpoch.get() != epoch) return;
        searchProgress.postValue(SearchProgress.of(completed.incrementAndGet(), total));
    }

    private void execute(TaskType type, MutableLiveData<Result> liveData, Callable<Result> callable) {
        execute(type, liveData, callable, null, null);
    }

    private void execute(TaskType type, MutableLiveData<Result> liveData, Callable<Result> callable, Consumer<Result> onSuccess, Consumer<Throwable> onError) {
        execute(type, liveData, callable, onSuccess, onError, Constant.TIMEOUT_VOD);
    }

    private void execute(TaskType type, MutableLiveData<Result> liveData, Callable<Result> callable, Consumer<Result> onSuccess, Consumer<Throwable> onError, long timeoutMs) {
        execute(type, liveData, callable, onSuccess, onError, timeoutMs, "");
    }

    private void execute(TaskType type, MutableLiveData<Result> liveData, Callable<Result> callable, Consumer<Result> onSuccess, Consumer<Throwable> onError, long timeoutMs, String siteKey) {
        execute(type, liveData, callable, onSuccess, onError, timeoutMs, siteKey, Task.executor());
    }

    private void execute(TaskType type, MutableLiveData<Result> liveData, Callable<Result> callable, Consumer<Result> onSuccess, Consumer<Throwable> onError, long timeoutMs, String siteKey, Executor pool) {
        // 播放结果不再走这里：VideoActivity 直接起播路（playDirect）跑 SiteApi.playerContent。
        // 空 key 执行前拦截是 fork 保留项（冷启动空源不进线程池）：key 为空＝没有可用的源，
        // 绝不把 lambda 交给线程池，否则 SiteApi 里空 Site 一路走到 OkHttp.newCall("") 才抛 no-scheme。
        if (siteKey == null || siteKey.isEmpty()) {
            SpiderDebug.log("viewmodel", "execute rejected type=%s site empty: no home source configured", type);
            liveData.postValue(Result.error("未配置首页源"));
            return;
        }
        // 同一 ViewModel 同链新请求顶替旧请求：先停上一发自己的任务，其迟到的
        // 结果/错误一律被代次挡下（与本文件搜索链的 searchEpoch 同款口径）。
        // 以前 execute 不存句柄：旧任务只能跑完回来盖新结果，超时后还变僵尸占池。
        AtomicInteger epoch = type == TaskType.RESULT ? resultEpoch : actionEpoch;
        int mine = epoch.incrementAndGet();
        AtomicReference<ExecHandle> slot = type == TaskType.RESULT ? resultTask : actionTask;
        ExecHandle handle = new ExecHandle();
        ExecHandle prev = slot.getAndSet(handle);
        if (prev != null) prev.cancel();
        // 超时从开跑算：先到池里占住线程，任务真正开始执行那刻才建计时 future 起表；
        // 以前从提交那刻起表，池里排队的时间也算，任务没跑先被判超时（回压根源之一）。
        pool.execute(() -> {
            if (handle.isDone()) return;
            ListenableFutureTask<Result> task = ListenableFutureTask.create(callable);
            FluentFuture<Result> timed = FluentFuture.from(task).withTimeout(timeoutMs, TimeUnit.MILLISECONDS, Task.scheduler());
            handle.attach(timed);
            if (handle.isDone()) return;
            Futures.addCallback(timed, Task.callback(handle::success, handle::fail), MoreExecutors.directExecutor());
            task.run();
        });
        Futures.addCallback(handle.future(), Task.callback(
                result -> {
                    if (epoch.get() != mine) return;
                    if (onSuccess != null) onSuccess.accept(result);
                    liveData.postValue(result);
                },
                error -> {
                    if (epoch.get() != mine) return;
                    // 被取消的一发不是失败（与上面搜索回调同口径）：取消也可能
                    // 来自子任务被中断后 SiteApi 按 CancellationException 出门，
                    // 那时本 ViewModel 的代次没动、epoch 挡不住，必须按类型
                    // 静默——不弹提示、不投错误结果、不记失败。
                    if (error instanceof CancellationException) return;
                    if (onError != null) onError.accept(error);
                    SpiderDebug.log("viewmodel", "execute failed type=%s site=%s error=%s", type, siteKey, error);
                    // 上游形：所有错误都投 Result.error，不吞成空结果；RESULT 再补一条 Notify。
                    String msg;
                    if (error instanceof ExtractException) msg = error.getMessage();
                    else if (error instanceof TimeoutException) msg = ResUtil.getString(R.string.error_play_timeout);
                    else if (error instanceof SiteApi.ColdHomeTimeoutException) msg = error.getMessage();
                    else msg = error.getMessage() != null ? error.getMessage() : "播放失败，请重试";
                    if (type == TaskType.RESULT) Notify.show(msg);
                    liveData.postValue(Result.error(msg));
                    error.printStackTrace();
                }
        ), MoreExecutors.directExecutor());
    }

    public int stopSearch() {
        int epoch = searchEpoch.incrementAndGet();
        // 走 ExecHandle.cancel() 而不是 cancel 外层 future：SettableFuture 被 cancel
        // 不传导到已 attach 的计时 future，正在跑的任务断不掉（外层 cancel 只挡回报）。
        // ExecHandle.cancel 先断外层、再断计时 future，在途任务真停。
        searchHandles.forEach(handle -> handle.cancel());
        searchHandles.clear();
        return epoch;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopSearch();
        // 只停自己名下的 execute 任务（详情页快速退页能停掉自己的详情任务）：
        // 代次先行递增，迟到回调一律挡下；句柄 cancel 让在途任务真停。
        // 不再全局作废首页加载——任意碎片销毁连坐掐掉正跑的首页加载，
        // 抛出的无 message CancellationException 正是假报错弹窗的根。
        resultEpoch.incrementAndGet();
        actionEpoch.incrementAndGet();
        ExecHandle running = resultTask.getAndSet(null);
        if (running != null) running.cancel();
        running = actionTask.getAndSet(null);
        if (running != null) running.cancel();
    }

    private enum TaskType {RESULT, ACTION}
}
