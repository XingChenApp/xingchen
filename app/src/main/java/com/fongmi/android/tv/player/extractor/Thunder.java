package com.fongmi.android.tv.player.extractor;

import android.net.Uri;
import android.os.SystemClock;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.runtime.ThunderRuntime;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.xunlei.downloadlib.ThunderFetchPolicy;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public class Thunder implements Source.Extractor {

    private ThunderRuntime.Task taskId;
    private final ThunderGateway gateway = ThunderGateway.get();

    // 同一 URL 的本地地址缓存（照基线 60155c55）：播放链会对同一链接并发多次
    // fetch（见 fetch 合流注释），复用已建任务的地址，不再重复建任务。
    // 复用前提是旧任务仍活着（状态 0/1/2）且已有字节，死任务不复用，
    // 走 fetchInternal 的串行卫生。
    private String lastKey;
    private String lastLocal;

    // 交地址时实际落盘的文件：BT 路是选集的 media 文件，直建路是 task 的
    // getSaveFile()。lastLocal 是 127.0.0.1 地址串，不能反推落盘路径，故另记
    // 一份；复用判断的落盘字节口径只认这份记录。只在交地址点配对写入，
    // fetchInternal 未进缓存时清掉，避免旧地址配新文件误复用。
    private File lastDownloadFile;

    // 进行中的 fetch 合流（照基线）：同内容正在 fetch 时后到者等先到者的结果，
    // 不建第二个任务——两份并发解析共用 Source 单例里的同一个 Thunder 实例，
    // 后建任务会写同一目录、覆盖 taskId 字段，stop() 再把先建任务停删＋删盘，
    // 正是 1.4.12 真机「206 全长、正文 0 字节、15 秒超时」的绞杀链。
    private final ConcurrentHashMap<String, CompletableFuture<String>> inflight = new ConcurrentHashMap<>();

    // 运行时只经网关取：内置 RuntimeEntry（见 ThunderGateway）。
    // Parser 是 record（静态成员），拿不到外层实例字段，一律用 ThunderGateway.get()。
    private ThunderRuntime runtime() throws ExtractException {
        return gateway.runtime();
    }

    @Override
    public boolean match(Uri uri) {
        // 上游 SCHEMES = magnet／ed2k／ftp；fork 修补批加的 thunder:// 保留（已落地，不回退），
        // UrlUtil.scheme 已做小写归一（Magnet: 变体修补保留）。
        return List.of("magnet", "ed2k", "ftp", "thunder").contains(UrlUtil.scheme(uri));
    }

    // 结构沿革（与磁力防护有关的部分，逐批记账）：
    // 上游形（FongMi/TV e5ec0c60f）：fetch 不复用、不合流、不清洗——每次 fetch
    // 都经 createTask 重新建任务，createTask 先停旧再建新。1.4.12 换上游形时把
    // 基线 60155c55 的清理/防护层全拆了（双路合流、同链接复用、状态 3 停删、
    // 换链接停删、零字节标记清洗、直建路字节门禁、交地址后进度监控）。
    // 1.4.12 真机实证拆防护的代价：点一集磁力，playerContent 被并发跑两份
    // （相差 6ms，调用方双点火，详见本批交回），两份共用同一 savePath
    // （Path.thunder(md5(url))）；先一份建任务交址后，后一份的 stop()
    // （playerContent 开头与 createTask 开头各一处）把先一份的任务停删＋删盘，
    // 再建自己的任务。播放器攥着的本地地址随即成死地址：迅雷本地服务能回
    // 206 与全长 5.46GB，正文 0 字节，15 秒 SocketTimeout，干等 30 秒放弃。
    // 本批照基线把与本断点相关的防护搬回：① fetch 合流＋入口复用
    // （fetch／fetchInternal）；② stop() 活任务不杀；③ createTask 不再无条件
    // 先停旧（卫生归 fetchInternal）；④ 直建路交址前状态轮询＋字节门禁
    // （waitDirectTask，照基线 addThunderTask）；⑤ 零字节标记＋交址后进度
    // 监控（zeroMark／startProgressLog，照基线原样）。
    // 未搬：基线 BT 路的字节门禁——现版 BT 交址口径仍是「状态非 0 即交地址」
    // （上游口径，用户定死），本断点走直建路，与 BT 门禁无关，不动。
    // 接线（内置形态）不变：本文件不直接碰迅雷类，一律经 ThunderGateway 取
    // ThunderRuntime——网关直接构造 :thunder 里的 RuntimeEntry（转发
    // XLTaskHelper），迅雷代码与原生库随主包在，不查外置包、无缺模块态。
    // 状态取值经网关：父状态走 getNativeTaskInfo 原生值（绕开 getTaskInfo 的
    // “存档存在即报 2”合成），速度与字节是真值。
    // 定位日志：createTask 内 "bt task created ..."；waitTorrentTask 与
    // waitDirectTask 内 "task handed local=%s status=%d speed=%d size=%d"；
    // 合流/复用/清洗/进度各有专行（照基线），真机查磁力靠它们定绞杀与字节。
    @Override
    public String fetch(String url) throws Exception {
        // 中断判读（可见错保留）：上游中断抛 CancellationException，fork 的
        // ViewModel 会把 CancellationException 静默 return（对照报告 B2），
        // 故中断仍抛 ExtractException 可见错，不照搬。
        if (Thread.currentThread().isInterrupted()) throw new ExtractException("迅雷任务被中断，请重试");
        if (url == null) throw new ExtractException("迅雷本地地址为空，请重试");
        // 入口先归一（基线同款：去首尾空白、scheme 段小写）。match() 认 scheme
        // 大小写不敏感，但下面所有分流判断都是大小写敏感的精确前缀，变体串会
        // 落不进任何一条建任务的路（基线 1.3.13 真机实证）。
        url = normalizeScheme(url);
        synchronized (this) {
            // 入口复用（照基线）：只复用已有字节的活任务；0 字节不复用，落到
            // fetchInternal 清洗重建。有字节口径与交地址门禁对齐：父计数 >0
            // 或落盘文件长度 >0 任一成立即算有字节。判定本体在策略类
            // ThunderFetchPolicy.shouldReuseAtEntry（与原条件逐字等价）；入参
            // 按原条件操作数顺序取齐，均为只读查询（网关状态/标记/计数）。
            if (ThunderFetchPolicy.shouldReuseAtEntry(sameContent(url, lastKey), lastLocal != null, taskId != null, nativeStatus(), zeroMark(url).exists(), hasDownloadedBytes())) {
                SpiderDebug.log("thunder", "reuse live task, skip duplicate fetch");
                return lastLocal;
            }
        }
        // 合流（照基线）：同内容正在 fetch 时后到者等先到者结果（上限 70 秒），
        // 不建第二个任务。并发两份的来源在调用方（双点火），基线就是靠这一层
        // 在 fetch 层兜底，本批照基线口径兜，不动调用方。
        String inflightKey = contentKey(url);
        CompletableFuture<String> mine = new CompletableFuture<>();
        CompletableFuture<String> first = inflight.putIfAbsent(inflightKey, mine);
        if (ThunderFetchPolicy.shouldJoinInflight(first != null)) {
            SpiderDebug.log("thunder", "join in-flight fetch, wait for first result");
            try {
                return first.get(70, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                throw new Exception(cause);
            } catch (TimeoutException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new ExtractException(ResUtil.getString(R.string.error_play_timeout));
            }
        }
        try {
            String local = fetchInternal(url);
            mine.complete(local);
            return local;
        } catch (Throwable t) {
            mine.completeExceptionally(t);
            throw t;
        } finally {
            inflight.remove(inflightKey, mine);
        }
    }

    private synchronized String fetchInternal(String url) throws Exception {
        // 零字节卡死任务的干净重建（照基线）：上一轮播放被进度监控记下「交地址
        // 后 30 秒仍 0 字节」的链接（zeroMark），这次先清掉再重建——卡死任务
        // 状态恒 1、速度恒 0，靠状态判断永远不会被清理，复用只会把尸体一直
        // 递给播放器。只在 fetch 入口、交地址之前清，不碰正在播的任务
        // （有字节在走的任务，监控会把标记撤掉）。
        // 直建路（非磁力）：停删引擎侧同链接记录＋删整个目录再重建。
        if (!url.startsWith("magnet") && zeroMark(url).exists()) wipeZeroMarked(url);
        // 磁力同理：见标记先停删内存里的同内容任务再重建；只删任务不删种子
        // 文件目录——建 BT 任务要靠种子文件。
        if (url.startsWith("magnet") && zeroMark(url).exists()) {
            if (sameContent(url, lastKey) && taskId != null) cleanupStaleTask();
            zeroMark(url).delete();
            SpiderDebug.log("thunder", "magnet zero-byte history wiped, rebuilding clean");
        }
        // 同链接复用（照基线）：旧任务未明确报错（原生状态非 3）就直接复用，
        // 禁止停删重建——后到的 fetch 把先建任务当死任务杀掉重建，播放器对着
        // 已删任务的地址永远 0 字节（基线 1.3.8 日志实证）。
        int ns = nativeStatus();
        // 状态单次读数偶发误报 3（出错）：任务已下出字节且没被零字节监控记账
        // 时，以字节为准（字节口径与复用条件一致）。判定本体在策略类
        // ThunderFetchPolicy.correctErrorStatus（与原条件逐字等价）。
        ns = ThunderFetchPolicy.correctErrorStatus(ns, hasDownloadedBytes(), zeroMark(url).exists());
        // 同链接复用裁决在策略类 ThunderFetchPolicy.internalAction（与原块逐字等价）。
        ThunderFetchPolicy.InternalAction internalAction = ThunderFetchPolicy.internalAction(sameContent(url, lastKey), lastLocal != null, taskId != null, ns, hasDownloadedBytes());
        if (internalAction == ThunderFetchPolicy.InternalAction.REUSE) {
            SpiderDebug.log("thunder", "reuse same-url task, no rebuild status=%d", ns);
            lastKey = url;
            return lastLocal;
        }
        if (internalAction == ThunderFetchPolicy.InternalAction.CLEANUP_REBUILD) {
            // 同链接任务 0 字节：不复用死地址，先停删再由下面流程重建，
            // 重建后仍 0 字节由交址门禁裁决。
            SpiderDebug.log("thunder", "reuse blocked: same-url task zero bytes status=%d, cleanup and rebuild", ns);
            cleanupStaleTask();
        }
        // 串行重试卫生（照基线）：仅当原生状态明确为 3（出错）才停删旧任务。
        // 判定本体在策略类 ThunderFetchPolicy.shouldCleanupOnError。
        if (ThunderFetchPolicy.shouldCleanupOnError(taskId != null, ns)) cleanupStaleTask();
        // 换链接（照基线）：旧任务先停删再建新的，避免多任务并存抢带宽与目录。
        // 换不换只认内容身份（磁力认 btih 指纹）：同磁力不同字符串写法不算换
        // 链接，否则正在下载的任务会被第二路 fetch 当「旧链接」停删
        // （基线 1.3.12 真机实证）。
        if (ThunderFetchPolicy.shouldCleanupOnLinkChange(taskId != null, sameContent(url, lastKey))) cleanupStaleTask();
        String local = dispatch(url);
        if (local != null && local.contains("127.0.0.1")) {
            lastKey = url;
            lastLocal = local;
        } else {
            // 未进缓存的地址不配对落盘文件，避免旧 lastLocal 配新文件误复用。
            lastDownloadFile = null;
        }
        return local;
    }

    // 原生任务状态：经网关走 getNativeTaskInfo（绕开 getTaskInfo 的状态合成）；
    // 读不到返回 -1。0 等待 / 1 下载中 / 2 已完成 / 3 出错。
    private int nativeStatus() {
        if (taskId == null) return -1;
        try {
            ThunderGateway.StatusEx info = gateway.taskStatus(taskId);
            return info == null ? -1 : info.taskStatus();
        } catch (Exception e) {
            return -1;
        }
    }

    // 复用判断的活任务口径已随判定搬入策略类 ThunderFetchPolicy.isAliveStatus：
    // 只认原生状态 0/1/2；-1（读不到）不算活——读不到就重建，不许把死任务
    // 的地址递给播放器。该口径只管复用，不管删任务（删任务走
    // cleanupStaleTask），不存在误杀在用任务。

    // 清理死任务（照基线）：stop + delete，不释放引擎（重建任务走热引擎）。
    private void cleanupStaleTask() {
        int status = nativeStatus();
        try {
            runtime().stopTask(taskId);
            runtime().deleteTask(taskId);
        } catch (Exception e) {
            SpiderDebug.log("thunder", "cleanup stale task err=%s", String.valueOf(e.getMessage()));
        }
        taskId = null;
        lastKey = null;
        lastLocal = null;
        lastDownloadFile = null;
        SpiderDebug.log("thunder", "cleaned stale task before rebuild status=%d", status);
    }

    /** 当前任务已下载字节（经网关原生信息，与 nativeStatus 同源）；读不到按 0。 */
    private long downloadedSize() {
        if (taskId == null) return 0;
        try {
            ThunderGateway.StatusEx info = gateway.taskStatus(taskId);
            return info == null ? 0 : info.downloadSize();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 当前任务对应落盘文件的已有长度；无记录或文件不存在时按 0（File.length() 本身即如此）。 */
    private long onDiskSize() {
        return lastDownloadFile == null ? 0 : lastDownloadFile.length();
    }

    /**
     * 复用/纠错共用的“已有字节”口径（照基线）：父计数或落盘长度任一 >0
     * 即算有字节。父任务计数是否聚合 BT 子任务，离线证实不了——基线就是
     * 双口径并联，本批原样保留，不谎称父计数已闭环。
     */
    private boolean hasDownloadedBytes() {
        return ThunderFetchPolicy.hasBytes(downloadedSize(), onDiskSize());
    }

    // 分流：裸磁力先经 Parser 展开（fork 保留路，上游 fetch 只认已解析选集地址）；
    // 磁力选集路经 getSelection → createTask → waitTorrentTask；
    // thunder:// 经 fetchThunder 解码后复用本分流；其余（ed2k／ftp）直建路经
    // createTask → waitDirectTask（状态轮询＋字节门禁，照基线 addThunderTask）。
    private String dispatch(String url) throws Exception {
        url = normalizeScheme(url);
        if (isRawMagnet(url)) return fetchParsedMagnet(url);
        if (url.startsWith("magnet")) {
            Selection selection = getSelection(Uri.parse(url));
            ThunderRuntime.Task task = createTask(url, selection);
            try {
                return waitTorrentTask(task, selection, url);
            } catch (Exception e) {
                stopFailedTask(task, e);
                throw e;
            }
        }
        if (url.startsWith("thunder")) return fetchThunder(url);
        ThunderRuntime.Task task = createTask(url, null);
        try {
            // 树适配：旧 fork 直连 Task 有 getSavePath()，网关 Task 只有 getSaveFile()，同义判空。
            if (task.getFileName() == null || task.getSaveFile() == null) throw new ExtractException("迅雷本地地址为空，请重试");
            return waitDirectTask(task, url);
        } catch (Exception e) {
            stopFailedTask(task, e);
            throw e;
        }
    }

    // 建任务（偏离上游一处，照基线）：上游 createTask 先停旧再建新；基线没有
    // 这一步——旧任务的卫生（状态 3／换链接／零字节／同链接 0 字节）全在
    // fetchInternal 进本方法之前裁决完，进到这里 taskId 要么已空、要么是
    // fetchInternal 明示保留的任务。本方法再无条件 stop()，会把并发两份解析
    // 里先一份刚建好的任务当场停删＋删盘（1.4.12 真机绞杀链的一把刀），故删。
    // 新 fetch 顶掉旧 fetch 时，旧路在 wait 轮次里因 taskId != task 抛可见错，
    // 不对着旧任务交地址，这条纪律不变。
    private synchronized ThunderRuntime.Task createTask(String url, Selection selection) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new ExtractException("迅雷任务被中断，请重试");
        if (selection != null) {
            taskId = runtime().addTorrentTask(selection.torrent(), selection.torrent().getParentFile(), selection.index());
            // 定位日志（只记日志）：BT 任务建好即记，建任务失败抛错前不记。
            SpiderDebug.log("thunder", "bt task created torrent=%s index=%d", selection.torrent().getPath(), selection.index());
            return taskId;
        }
        taskId = runtime().addThunderTask(url, Path.thunder(Util.md5(url)));
        // 定位日志（只记日志）：直建路（ed2k／ftp）同样记建任务行，便于真机区分
        // “任务根本没建”与“建了但没交地址”。
        SpiderDebug.log("thunder", "bt task created url=%s", url);
        return taskId;
    }

    // 上游 stopFailedTask 原文形：只当失败任务仍是当前任务时才停；已被新 fetch
    // 顶掉的任务不碰——失败旧路绝不杀新路的任务。
    private synchronized void stopFailedTask(ThunderRuntime.Task task, Exception error) {
        if (taskId == task) {
            try {
                stop();
            } catch (Exception cleanup) {
                error.addSuppressed(cleanup);
            }
        }
    }

    // 直建路交址（照基线 addThunderTask 的状态轮询＋字节门禁）：上游零等待交
    // 地址，死任务（有元数据、无字节流）会让播放器永远 BUFFERING 且界面无
    // 报错——本地代理 206 头对、body 一字节不来。改与基线同款门禁：状态 3
    // 抛真错；状态非 0 且引擎计数或落盘文件已有字节才交地址；200 轮
    // （20 秒）仍 0 字节抛带 status/speed/size 的真错，不递死地址。健康任务
    // 首字节通常 1 秒内到，门禁不拖慢正常起播；预算卡在 ViewModel 30 秒
    // 超时内。每轮先核 taskId == task（与 waitTorrentTask 同纪律）：任务已被
    // 新 fetch 顶掉时立即抛可见错，不对着旧任务交地址。
    private String waitDirectTask(ThunderRuntime.Task task, String url) throws Exception {
        File saveFile = task.getSaveFile();
        int lastStatus = 0;
        long lastSpeed = 0;
        long lastSize = 0;
        for (int i = 0; i < 200; i++) {
            if (Thread.currentThread().isInterrupted()) throw new ExtractException("迅雷任务被中断，请重试");
            synchronized (this) {
                if (taskId != task) throw new ExtractException("迅雷任务被中断，请重试");
            }
            ThunderGateway.StatusEx info = null;
            try {
                info = gateway.taskStatus(task);
            } catch (Exception ignored) {
            }
            if (info != null) {
                lastStatus = info.taskStatus();
                lastSpeed = info.downloadSpeed();
                lastSize = info.downloadSize();
            }
            // 单轮裁决在策略类 ThunderFetchPolicy.directRound（与原轮内判定逐字
            // 等价）：信息缺席（info == null，lastStatus/lastSize 是旧值）裁决先
            // 看 infoPresent、不会用到旧值；交址动作本体（取本地地址/记账）留此。
            ThunderFetchPolicy.DirectRound directRound = ThunderFetchPolicy.directRound(info != null, lastStatus, ThunderFetchPolicy.hasBytes(lastSize, saveFile == null ? 0 : saveFile.length()));
            if (directRound == ThunderFetchPolicy.DirectRound.FAIL_ERROR) {
                String err = info.errorMessage();
                if (err == null || err.isEmpty()) err = "迅雷任务出错，请重试";
                throw new ExtractException(err);
            }
            if (directRound == ThunderFetchPolicy.DirectRound.HAND_OFF) {
                String local = null;
                boolean localReadFailed = false;
                try {
                    local = runtime().getLocalUrl(saveFile);
                } catch (Exception ignored) {
                    localReadFailed = true;
                }
                if (!localReadFailed) {
                    SpiderDebug.log("thunder", "task handed local=%s status=%d speed=%d size=%d", local, info.taskStatus(), info.downloadSpeed(), info.downloadSize());
                    if (local == null || local.isEmpty()) {
                        throw new ExtractException("迅雷本地地址为空，请重试");
                    }
                    lastDownloadFile = saveFile;
                    startProgressLog(task, url, -1);
                    return local;
                }
                // 取地址失败或 0 字节：不交地址，继续循环等首字节。
            }
            if (i % 50 == 49) SpiderDebug.log("thunder", "thunder waiting status=%d speed=%d size=%d", lastStatus, lastSpeed, lastSize);
            SystemClock.sleep(100);
        }
        throw new ExtractException(String.format(Locale.ROOT, "迅雷任务%d毫秒仍未下载到字节，无法起播（status=%d speed=%d size=%d），请换源或重试", 200 * 100L, lastStatus, lastSpeed, lastSize));
    }

    private record Selection(File torrent, File media, int index) {
    }

    // 上游 getSelection 原文校验：种子文件须在 thunder 目录内、播出文件须在种子父目录内
    //（canonical 路径 containment，防 ../ 逃逸）。
    // 偏离上游两处（均为可见错保留）：上游非法选集抛 IllegalArgumentException／
    // NumberFormatException，fork 仍转 ExtractException（非 ExtractException 会被上层
    // 吞成空结果、界面静默）；种子文件不存在／空文件先验是上一批已落地的可见错修补，保留。
    private Selection getSelection(Uri uri) throws Exception {
        String path = uri.getPath();
        String name = uri.getQueryParameter("name");
        int index;
        try {
            index = Integer.parseInt(uri.getQueryParameter("index"));
        } catch (Exception e) {
            throw new ExtractException("选集地址不完整，请重新打开详情再播");
        }
        if (path == null || path.isEmpty() || name == null || name.isEmpty() || index < 0) {
            throw new ExtractException("选集地址不完整，请重新打开详情再播");
        }
        File torrent;
        File parent;
        File media;
        try {
            torrent = new File(path).getCanonicalFile();
            parent = torrent.getParentFile();
            String root = Path.thunder().getCanonicalPath() + File.separator;
            if (parent == null || !parent.getPath().startsWith(root)) throw new ExtractException("种子文件已失效，请重新打开详情再播");
            media = new File(parent, name).getCanonicalFile();
            if (!media.getPath().startsWith(parent.getPath() + File.separator)) throw new ExtractException("种子文件已失效，请重新打开详情再播");
        } catch (ExtractException e) {
            throw e;
        } catch (Exception e) {
            throw new ExtractException("种子文件已失效，请重新打开详情再播");
        }
        if (!torrent.exists() || torrent.length() == 0) throw new ExtractException("种子文件已失效，请重新打开详情再播");
        return new Selection(torrent, media, index);
    }

    // 上游 waitTorrentTask 原文结构：100 轮 × 100ms；父任务状态 3 抛错、子任务状态 3 抛错、
    // 子任务状态非 0 立即交 getLocalUrl（不等字节；已完成且 0 字节也立即交，不另抛）。
    // 父状态走 getNativeTaskInfo（绕开 getTaskInfo 的“存档存在即报 2”合成，否则父状态 3 永不可见），
    // 经网关 StatusEx 取（见网关注释）。
    // 每轮先核 taskId == task：任务已被新 fetch／stop() 顶掉时立即抛可见错，不对着旧任务交地址。
    // markUrl：原始 fetch 链接（未解析的裸磁力或选集地址），只给交址后的进度
    // 监控/零字节记账用（照基线 addTorrentTask 的 markUrl），不参与建任务。
    private String waitTorrentTask(ThunderRuntime.Task task, Selection selection, String markUrl) throws Exception {
        int lastStatus = 0;
        long lastSpeed = 0;
        long lastSize = 0;
        for (int i = 0; i < 100; i++) {
            synchronized (this) {
                if (taskId != task || Thread.currentThread().isInterrupted()) throw new ExtractException("迅雷任务被中断，请重试");
                ThunderGateway.StatusEx parentInfo = null;
                try {
                    parentInfo = gateway.taskStatus(task);
                } catch (Exception ignored) {
                }
                ThunderGateway.StatusEx info = null;
                boolean btReadFailed = false;
                try {
                    info = gateway.btSubTaskStatus(task, selection.index());
                } catch (Exception ignored) {
                    btReadFailed = true;
                }
                if (!btReadFailed && info != null) {
                    lastStatus = info.taskStatus();
                    lastSpeed = info.downloadSpeed();
                    lastSize = info.downloadSize();
                }
                // 单轮裁决在策略类 ThunderFetchPolicy.btRound（与原轮内判定顺序
                // 逐字等价）：父状态读不到传 -1；交址动作本体留此。
                ThunderFetchPolicy.BtRound btRound = ThunderFetchPolicy.btRound(parentInfo == null ? -1 : parentInfo.taskStatus(), btReadFailed, info != null, info == null ? 0 : info.taskStatus());
                if (btRound == ThunderFetchPolicy.BtRound.FAIL_PARENT) {
                    String err = parentInfo.errorMessage();
                    throw new ExtractException(err == null || err.isEmpty() ? "迅雷任务出错，请重试" : err);
                }
                if (btRound == ThunderFetchPolicy.BtRound.FAIL_SUB_EMPTY) throw new ExtractException("迅雷子任务信息为空，请重试");
                if (btRound == ThunderFetchPolicy.BtRound.FAIL_SUB) {
                    String err = info.errorMessage();
                    throw new ExtractException(err == null || err.isEmpty() ? "迅雷任务出错，请重试" : err);
                }
                if (btRound == ThunderFetchPolicy.BtRound.HAND_OFF) {
                    String local = null;
                    boolean localReadFailed = false;
                    try {
                        local = runtime().getLocalUrl(selection.media());
                    } catch (Exception ignored) {
                        localReadFailed = true;
                    }
                    if (!localReadFailed) {
                        if (local == null || local.isEmpty()) throw new ExtractException("迅雷本地地址为空，请重试");
                        // 定位日志（只记日志）：状态非 0 交地址即记，含当时速度／已下字节，
                        // 真机查磁力靠“有 created 无 handed”定绞杀／字节门禁。
                        SpiderDebug.log("thunder", "bt task handed local=%s status=%d speed=%d size=%d", local, info.taskStatus(), info.downloadSpeed(), info.downloadSize());
                        // 落盘文件配对记录（供 fetch 复用的落盘字节口径）＋交址后
                        // 进度监控/零字节记账（照基线 addTorrentTask 的交址点）。
                        lastDownloadFile = selection.media();
                        startProgressLog(task, markUrl, selection.index());
                        return local;
                    }
                }
            }
            SystemClock.sleep(100);
        }
        // 超时真因是子任务 10 秒仍停在状态 0（未开始）：无种/元数据未到/子任务未启动，
        // 不是网络连接超时——照 waitDirectTask 口径把末轮状态摆出来，不再套 error_play_timeout。
        throw new ExtractException(String.format(Locale.ROOT, "迅雷任务%d毫秒仍未开始下载（status=%d speed=%d size=%d），可能暂无可用种子或元数据未到达，请换源或重试", 100 * 100L, lastStatus, lastSpeed, lastSize));
    }

    // 与 Parser.normalizeScheme 同款：只归一 scheme 段，不动冒号后的内容
    private static String normalizeScheme(String raw) {
        String u = raw == null ? "" : raw.trim();
        int colon = u.indexOf(':');
        if (colon > 0) u = u.substring(0, colon).toLowerCase(Locale.ROOT) + u.substring(colon);
        return u;
    }

    /**
     * 兜底：裸 magnet（如 magnet:?xt=urn:btih:…）没有经过 Parser，
     * 直接走 getSelection 会因取不到 path/index 而抛错。
     * 先同步解析一遍，拿到标准播放地址再继续。
     */
    private boolean isRawMagnet(String url) {
        if (url == null || !url.startsWith("magnet:")) return false;
        try {
            return Uri.parse(url).getQueryParameter("index") == null;
        } catch (Exception e) {
            return true;
        }
    }

    private String fetchParsedMagnet(String url) throws Exception {
        List<Episode> episodes = Parser.get(url).call();
        if (episodes.isEmpty()) throw new ExtractException("磁力解析失败，请重试");
        String parsed = normalizeScheme(episodes.get(0).getUrl());
        if (!parsed.startsWith("magnet")) return parsed;
        Selection selection = getSelection(Uri.parse(parsed));
        ThunderRuntime.Task task = createTask(parsed, selection);
        try {
            // markUrl 传原始裸磁力（照基线 fetchParsedMagnet）：零字节记账按原链记，
            // 不按解析后的选集地址另立一笔。
            return waitTorrentTask(task, selection, url);
        } catch (Exception e) {
            stopFailedTask(task, e);
            throw e;
        }
    }

    // fetchThunder 保留的覆盖点（上游链确实覆盖不到处）：上游 thunder:// 只走
    // Parser 展开路——Parser.call → ThunderRuntime.parse → XLTaskHelper.parse
    // 第一步 manager.parserThunderUrl 解码，再由 Source.fetch 对展开后的地址起播；
    // 上游 fetch() 本身不解 thunder://（selection 为空时 addThunderTask 只认
    // ftp/ed2k，thunder:// 会抛 Unsupported）。本方法只补“起播入口直接拿到裸
    // thunder://、未经 Parser 展开”这一路：解码 primitive 与 XLTaskHelper.parse
    // 第一步相同（manager.parserThunderUrl），解出 http(s) 直接交、解出磁力/ed2k/ftp
    // 复用 dispatch；其余 thunder:// 形态一律走 Parser 展开，不在此另起炉灶。
    private String fetchThunder(String url) throws Exception {
        ThunderRuntime.Task decoded;
        try {
            decoded = runtime().parse(url, Path.thunder(Util.md5(url)));
        } catch (Exception e) {
            throw new ExtractException("迅雷链接解析失败，请重试");
        }
        String real = decoded == null ? null : decoded.getRealUrl();
        // 首个 parse 只用来解码：解出 real 串后立即停删，不留野任务。
        // 下游裸磁力还要经 Parser 再 parse 一次、再由 createTask 建 BT 任务；
        // 首个任务若不清掉，会与下游任务并存，且从未进 taskId 字段、stop() 也够不着。
        // 这里只动局部解码任务，不动字段任务与 stop()，下游建任务路原样不变。
        if (decoded != null) {
            try {
                runtime().stopTask(decoded);
                runtime().deleteTask(decoded);
            } catch (Exception e) {
                SpiderDebug.log("thunder", "thunder decode task cleanup err=%s", String.valueOf(e.getMessage()));
            }
        }
        if (real == null || real.isEmpty() || real.startsWith("thunder")) {
            SpiderDebug.log("thunder", "thunder url decode failed url=%s real=%s", url, real);
            throw new ExtractException("迅雷链接解析失败，请重试");
        }
        real = normalizeScheme(real);
        if (real.startsWith("http://") || real.startsWith("https://")) return real;
        return dispatch(real);
    }

    // 零字节标记（照基线）：交地址后持续 0 字节的链接在 thunder 根目录留一个
    // 标记文件，下次 fetch 见标记先干净重建（见 fetchInternal）。
    private static File zeroMark(String url) {
        // 标记按内容身份记账：同磁力换个字符串写法进来也要能认出这笔账
        return new File(Path.thunder(), "zero-" + Util.md5(contentKey(url)));
    }

    // —— 内容身份（照基线）：同一条磁力从不同入口进来，字符串常不同（参数
    // 顺序、附加参数、编码），按原文比对会把同内容误判成换链接。磁力认 btih
    // 指纹、电驴认文件哈希，其余用原文。
    static String contentKey(String url) {
        if (url == null) return "";
        String hash = btihOf(url);
        if (hash != null) return "btih:" + hash;
        hash = ed2kHashOf(url);
        if (hash != null) return "ed2k:" + hash;
        return url;
    }

    static boolean sameContent(String a, String b) {
        if (a == null || b == null) return false;
        return a.equals(b) || contentKey(a).equals(contentKey(b));
    }

    private static String btihOf(String url) {
        String low = url.toLowerCase(Locale.ROOT);
        int i = low.indexOf("xt=urn:btih:");
        if (i < 0) return null;
        int start = i + "xt=urn:btih:".length();
        int end = start;
        while (end < url.length()) {
            char c = url.charAt(end);
            if (c == '&' || c == '#' || c == ' ') break;
            end++;
        }
        String hash = url.substring(start, end).trim().toLowerCase(Locale.ROOT);
        return hash.isEmpty() ? null : hash;
    }

    private static String ed2kHashOf(String url) {
        if (!url.startsWith("ed2k://")) return null;
        String[] parts = url.split("\\|");
        // ed2k://|file|名称|大小|哈希|.../ —— 第 5 段是文件哈希
        return parts.length >= 5 && !parts[4].isEmpty()
                ? parts[4].toLowerCase(Locale.ROOT) : null;
    }

    // 零字节直建链接的干净重建（照基线 wipeZeroMarked）：删标记 → 停删引擎侧
    // 同链接记录 → 删整个目录，最后由调用方按原流程新建，保证新任务不挂任何
    // 旧状态。引擎侧记录含跨进程残留：内存里的任务只动属于这条链接的那个；
    // 不属于时用同链接探针任务去删引擎侧记录，不碰可能正在播的别的链接。
    private void wipeZeroMarked(String url) {
        try {
            zeroMark(url).delete();
            boolean mine = sameContent(url, lastKey);
            try {
                ThunderRuntime.Task stale = mine && taskId != null ? taskId : runtime().addThunderTask(url, Path.thunder(Util.md5(url)));
                runtime().stopTask(stale);
                runtime().deleteTask(stale);
            } catch (Exception ignored) {
            }
            if (mine) {
                taskId = null;
                lastKey = null;
                lastLocal = null;
                lastDownloadFile = null;
            }
            deleteRecursive(Path.thunder(Util.md5(url)));
            SpiderDebug.log("thunder", "zero-byte history wiped, rebuilding clean");
        } catch (Exception e) {
            SpiderDebug.log("thunder", "zero-byte wipe err=%s", String.valueOf(e.getMessage()));
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursive(child);
        file.delete();
    }

    // 交接后任务进度日志（照基线）：每 5 秒记一次状态/速度/已下量，最长 2 分钟；
    // 任务被换掉、完成或出错即停。用来分清「任务被自己人杀掉」和「资源本身没速度」。
    // url 非空时兼管零字节标记：有字节流动就撤标记；持续 30 秒 0 字节就落标记，
    // 供下次 fetch 干净重建。
    private void startProgressLog(ThunderRuntime.Task id, String url) {
        startProgressLog(id, url, -1);
    }

    // btIndex >= 0 时按 BT 子任务口径读状态/已下量（磁力记账用，经网关
    // btSubTaskStatus，与 waitTorrentTask 的子任务口径一致）
    private void startProgressLog(ThunderRuntime.Task id, String url, int btIndex) {
        if (id == null) return;
        Thread t = new Thread(() -> {
            for (int i = 0; i < 24; i++) {
                SystemClock.sleep(5000);
                if (taskId != id) return;
                try {
                    ThunderGateway.StatusEx info = btIndex >= 0
                            ? gateway.btSubTaskStatus(id, btIndex)
                            : gateway.taskStatus(id);
                    if (info == null) return;
                    SpiderDebug.log("thunder", "progress status=%d speed=%d size=%d", info.taskStatus(), info.downloadSpeed(), info.downloadSize());
                    if (url != null) {
                        if (info.downloadSize() > 0) {
                            File mark = zeroMark(url);
                            if (mark.exists()) mark.delete();
                        } else if (i >= 5 && (info.taskStatus() == 0 || info.taskStatus() == 1)) {
                            File mark = zeroMark(url);
                            if (!mark.exists()) {
                                try {
                                    mark.createNewFile();
                                } catch (Exception ignored) {
                                }
                                SpiderDebug.log("thunder", "zero-byte task marked for clean rebuild");
                            }
                        }
                    }
                    if (info.taskStatus() == 2 || info.taskStatus() == 3) return;
                } catch (Exception e) {
                    return;
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // stop()（照基线活任务保护，偏离上游）：播放链每次 playerContent 都先
    // Source.stop()，推送播一个磁力会连调多次；同链接任务还活着（原生状态
    // 0/1）且已交过地址时照旧删任务＋删文件，交给播放器的地址立刻成死地址
    // ——有点流量、然后 0 速、最后超时（基线 1.3.9 真机两例）；1.4.12 并发
    // 两份解析时，后一份的 stop() 更是当场绞杀先一份刚交址的任务。故：活
    // 任务＋已交地址 → 不杀，真正的清理放到下一次 fetch 的 fetchInternal
    // （状态 3／换链接／零字节）与 exit 时做。
    // 状态 2（已完成）且已交址同保：播放器可能还在从本地服务读已下完的文件，
    // deleteTask 是否连带删盘取决于 native 语义（未证），不赌——清理同样推迟
    // 到下一次 fetch 与 exit，未交址的已完成任务不受此保护、照旧删。
    // 其余情形照现版纪律：只删任务、不 release（release 只在 exit 走
    // releaseQuietly）；删任务失败时只清字段，不把错抛给 Source.stop() 的
    // 切换链，否则切集会跟着崩（内置后运行时恒在，此 catch 只兜引擎侧异常）。
    @Override
    public synchronized void stop() {
        int ns = nativeStatus();
        // 活任务保护判定在策略类 ThunderFetchPolicy.shouldProtectOnStop（与原条件逐字等价）。
        if (ThunderFetchPolicy.shouldProtectOnStop(taskId != null, lastLocal != null, ns)) {
            SpiderDebug.log("thunder", "stop skipped: task alive status=%d", ns);
            return;
        }
        lastKey = null;
        lastLocal = null;
        lastDownloadFile = null;
        if (taskId == null) return;
        ThunderRuntime.Task task = taskId;
        taskId = null;
        try {
            runtime().deleteTask(task);
        } catch (ExtractException ignored) {
            SpiderDebug.log("thunder", "stop without runtime, task dropped");
        }
    }

    // exit()：无条件清任务（不走 stop() 的活任务保护——退出时没有「还在播」
    // 可言，基线 exit 也是绕开保护直删），再 release 运行时。
    // releaseQuietly 只放已接通的运行时，不为 release 专门去接通一次；
    // taskId 非空才动运行时删任务（有任务必已接通过），不为删一个空任务
    // 专门去接通一次。
    @Override
    public synchronized void exit() {
        lastKey = null;
        lastLocal = null;
        lastDownloadFile = null;
        if (taskId != null) {
            try {
                runtime().deleteTask(taskId);
            } catch (Exception ignored) {
            }
            taskId = null;
        }
        gateway.releaseQuietly();
    }

    public record Parser(String url) implements Callable<List<Episode>> {

        // 上游 Parser.PATTERN 原文：^ 锚定＋大小写不敏感（find() 下 ^ 只认串首，
        // 串中夹带的 magnet:/thunder:/ed2k: 不再误认）。
        private static final Pattern PATTERN = Pattern.compile("^(magnet|thunder|ed2k):.*", Pattern.CASE_INSENSITIVE);

        // 粘贴来的磁力常带首尾空白或 scheme 大小写变体（Magnet:/MAGNET:），而下游
        // XLTaskHelper.parse 的 "magnet:?" 门限是大小写敏感的精确前缀：不变体归一
        // 就会被静默拦下、回退成单集原样（看起来像普通推送）。只归一 scheme 段，
        // 不动冒号后的内容（btih 的 base32 哈希大小写敏感）。
        private static String normalizeScheme(String raw) {
            String u = raw == null ? "" : raw.trim();
            int colon = u.indexOf(':');
            if (colon > 0) u = u.substring(0, colon).toLowerCase(Locale.ROOT) + u.substring(colon);
            return u;
        }

        public static boolean match(String url) {
            return PATTERN.matcher(url).find() || isTorrent(url);
        }

        public static Parser get(String url) {
            return new Parser(url);
        }

        private static boolean isTorrent(String url) {
            return !url.startsWith("magnet") && url.split(";")[0].toLowerCase().endsWith(".torrent");
        }

        private Episode create(ThunderRuntime.Task taskId) {
            return Episode.create(taskId.getFileName(), taskId.getRealUrl());
        }

        private Episode create(ThunderRuntime.Media info) {
            return Episode.create(info.fileName(), info.size(), info.playUrl());
        }

        @Override
        public List<Episode> call() throws Exception {
            String u = normalizeScheme(url);
            boolean torrent = isTorrent(u);
            if (u.startsWith("ed2k:")) {
                return Arrays.asList(Episode.create(parseEd2kName(u), u));
            }
            ThunderRuntime.Task taskId;
            try {
                taskId = ThunderGateway.get().runtime().parse(u, Path.thunder(Util.md5(u)));
            } catch (Exception e) {
                SpiderDebug.log("thunder", "parse failed url=%s error=%s", u, e);
                // ExtractException 原样上抛，不被下面通用文案盖掉，
                // 否则具体错因会丢成一句含糊的解析失败。
                if (e instanceof ExtractException) throw e;
                if (e instanceof InterruptedException || e instanceof java.util.concurrent.TimeoutException) {
                    throw new ExtractException("磁力解析超时，请检查网络后重试");
                }
                String msg = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
                if (msg.contains("timeout") || msg.contains("timed out")) {
                    throw new ExtractException("磁力解析超时，请检查网络后重试");
                }
                if (e instanceof java.io.IOException) {
                    throw new ExtractException("磁力种子下载失败，请检查网络后重试");
                }
                if (msg.contains("dht") || msg.contains("metadata") || msg.contains("torrent")) {
                    throw new ExtractException("磁力种子元数据获取失败，请重试");
                }
                throw new ExtractException("磁力解析失败，请重试");
            }
            // parse 回空任务／空地址时下面 getRealUrl 直接 NPE，而上游 ViewModel 只把
            // ExtractException 发成可见错，NPE 会被吞成空结果、界面静默 00:00。先验先说。
            String realUrl = taskId == null ? null : taskId.getRealUrl();
            if (realUrl == null) throw new ExtractException("磁力解析失败，请重试");
            if (!torrent && !realUrl.startsWith("magnet")) {
                SpiderDebug.log("thunder", "种子未解析出文件列表，回退单集 scheme=%s", u.contains(":") ? u.substring(0, u.indexOf(':')) : "");
                return Arrays.asList(create(taskId));
            }
            if (torrent && u.startsWith("http")) Download.create(u, taskId.getSaveFile()).get();
            if (!torrent) waitDone(taskId);
            try {
                List<Episode> medias = ThunderGateway.get().runtime().getTorrentInfo(taskId.getSaveFile()).stream().map(this::create).toList();
                if (medias.isEmpty()) throw new ExtractException("磁力解析失败，请重试");
                return medias;
            } catch (Exception e) {
                // 种子信息取不到（元数据没到、SDK 回空）时原先 NPE 被上游吞成空结果、
                // 界面静默 00:00；转成可见错，不递空结果。
                throw new ExtractException("磁力解析失败，请重试");
            } finally {
                ThunderGateway.get().runtime().stopTask(taskId);
            }
        }

        private void waitDone(ThunderRuntime.Task taskId) throws Exception {
            // 照上游 waitDone：状态 3 抛真错；状态 2 返回；100 轮 × 100ms 超时抛 error_play_timeout，
            // 不许超时后悄悄落到取种子信息（fork 旧 waitDone 超时无抛错、直接落到取种子信息）。
            // 上游用 Thread.sleep，此处沿用 fork 的 SystemClock.sleep（不改睡眠语义，只补抛错口径）。
            // 状态经网关取（网关 taskStatus 走 getNativeTaskInfo 原生值）。
            for (int i = 0; i < 100; i++) {
                ThunderGateway.StatusEx info = null;
                try {
                    info = ThunderGateway.get().taskStatus(taskId);
                } catch (Exception ignored) {
                }
                // 网关签名上可返 null（现行 native 路恒非 null，此为契约防线）：
                // 照相邻 waitTorrentTask 父状态的口径——null 本轮跳过、继续等，
                // 不直接解引用炸 NPE；真等不到仍由下面超时抛可见错收口。
                if (info != null && info.taskStatus() == 3) {
                    String err = info.errorMessage();
                    if (err == null || err.isEmpty()) err = "迅雷任务出错，请重试";
                    throw new ExtractException(err);
                }
                if (info != null && info.taskStatus() == 2) return;
                SystemClock.sleep(100);
            }
            throw new ExtractException(ResUtil.getString(R.string.error_play_timeout));
        }

        private static String parseEd2kName(String url) {
            try {
                String[] parts = url.split("\\|");
                if (parts.length > 2 && !parts[2].isEmpty()) return java.net.URLDecoder.decode(parts[2], "UTF-8");
            } catch (Exception ignored) {
            }
            return url;
        }
    }
}
