package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.cloud.CloudDrive;
import com.fongmi.android.tv.cloud.CloudFile;
import com.fongmi.android.tv.cloud.CloudTask;
import com.fongmi.android.tv.cloud.drive115.Drive115;
import com.fongmi.android.tv.cloud.drive115.Pan115Cover;
import com.fongmi.android.tv.cloud.drive115.PlayUrlBuilder115;
import com.fongmi.android.tv.cloud.drivepikpak.DrivePikPak;
import com.fongmi.android.tv.cloud.drivequark.DriveQuark;
import com.fongmi.android.tv.databinding.ActivityCloudDriveBinding;
import com.github.catvod.utils.Prefers;
import com.fongmi.android.tv.databinding.ItemCloudFileBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网盘文件浏览器（115 / 夸克 / PikPak 共用）
 * 入口：CloudDriveActivity.start(activity, "115" | "quark" | "pikpak")
 */
public class CloudDriveActivity extends BaseActivity {

    private static final String TAG = "CloudDriveActivity";
    private static final String EXTRA_DRIVE_ID = "drive_id";
    private static final String EXTRA_OPEN_CID = "open_cid";
    private static final String EXTRA_OPEN_NAME = "open_name";
    private static final String EXTRA_OPEN_PARENT = "open_parent";
    private static final int PAGE_LIMIT = 100;
    private static final int SEARCH_LIMIT = 50;
    /** 搜索首拉并发页数：换词后一次并发拉前 3 页（每页 SEARCH_LIMIT 条），先回的先攒、按页序拼，首屏不等满 3 页。 */
    private static final int SEARCH_BURST_PAGES = 3;
    /** 整页被过滤光时自动续拉的连拉上限（防跑飞）：连拉 10 页仍无可见行就停，到顶仍空才是真空（下拉刷新会重置）。 */
    private static final int AUTO_CHAIN_MAX = 10;
    /** 「保存到」上次所选文件夹的偏好键（按网盘分开记，拼 drive.getId()）：cid 与显示名各一份。 */
    private static final String PREF_SAVE_CID = "drive_save_cid_";
    private static final String PREF_SAVE_NAME = "drive_save_name_";
    /** 视频展示下限（他拍板写死）：30MB 以内的短视频多半是广告，列表与搜索结果里直接不显示。 */
    private static final long MIN_VIDEO_SIZE = 30L * 1024 * 1024;

    public static void start(Activity activity, String driveId) {
        Intent intent = new Intent(activity, CloudDriveActivity.class);
        intent.putExtra(EXTRA_DRIVE_ID, driveId);
        activity.startActivity(intent);
    }

    /** 直达某个目录打开（如从搜索结果点文件夹进来）：面包屑＝根目录＋该目录。 */
    public static void startAt(Activity activity, String driveId, String cid, String name) {
        startAt(activity, driveId, cid, name, null);
    }

    /** 直达并带上父级：落地栈＝根目录＋父级＋该目录，返回先退父级、逐层可退；父级未知传空，退化为根＋该目录。 */
    public static void startAt(Activity activity, String driveId, String cid, String name, String parentCid) {
        Intent intent = new Intent(activity, CloudDriveActivity.class);
        intent.putExtra(EXTRA_DRIVE_ID, driveId);
        intent.putExtra(EXTRA_OPEN_CID, cid);
        intent.putExtra(EXTRA_OPEN_NAME, name);
        if (!TextUtils.isEmpty(parentCid)) intent.putExtra(EXTRA_OPEN_PARENT, parentCid);
        activity.startActivity(intent);
    }

    private ActivityCloudDriveBinding binding;
    private CloudDrive drive;
    private final List<Crumb> crumbs = new ArrayList<>();
    private final List<CloudFile> files = new ArrayList<>();
    private FileAdapter adapter;
    private String rootCid = "";
    /** 分页状态：hasMore 按“本页是否取满 PAGE_LIMIT”判定（115／PikPak 的 getTotal 返回 -1，不可用）。 */
    private boolean loadingMore;
    private boolean hasMore = true;
    /** 已取的原始条数：展示列表过滤掉非视频文件后 files.size() 不再等于服务端偏移，分页 offset 按这个走。 */
    private int fetchedCount;
    private int fetchGen;
    /** 已连续自动续拉的页数（整页过滤光时触发），拉出可见行或重新 fetchFiles 时归零。 */
    private int autoChain;
    /** onResume 刷新守门：initView 成功走完才置真；紧跟 initView 的首个 onResume 跳过，避免同一次进页重复拉取。 */
    private boolean viewReady;
    private boolean firstResume = true;
    /** D5 回页闸门：数据动作在离开期间完成时置真，下次 onResume 才全量重拉；本页是否在前台由 onResume/onPause 维护。 */
    private boolean resumeRefresh;
    private boolean activityResumed;

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityCloudDriveBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        String driveId = getIntent().getStringExtra(EXTRA_DRIVE_ID);
        drive = createDrive(driveId);
        if (drive == null) {
            Notify.show("网盘驱动不可用");
            finish();
            return;
        }
        if (!drive.isLogin()) {
            Notify.show("请先登录" + drive.getName());
            PanLoginActivity.start(this);
            finish();
            return;
        }
        // PikPak 预热 captcha_token：与首屏列表并行取，列表那一发不必串行等 init
        // （token 按动作缓存、开文件夹共用，预热后正常浏览节奏下一直是热的）。
        if (drive instanceof DrivePikPak) ((DrivePikPak) drive).warmUpCaptcha();
        rootCid = Drive115.ID.equals(drive.getId()) ? "0" : "";
        binding.toolbar.setTitle(drive.getName());
        // 顶栏只留标题，不放自带返回箭头（布局里 navigationIcon 已删）。
        // 子目录回退走系统返回：BaseActivity 把 OnBackPressedDispatcher 接到 onBackInvoked()，
        // 本页 onBackInvoked() 有子目录时退目录、否则 finish，不再依赖顶栏图标点击。
        updateDriveInfo();
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter();
        binding.recycler.setAdapter(adapter);
        // 流畅度 D8：行高恒定（行布局 minHeight 68dp＋两行单行文字、名称再长也单行省略），
        // 固定尺寸免逐行重测；默认动画器在整表刷新下只记账、无实际动画收益，摘掉
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        binding.recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loadingMore || !hasMore) return;
                RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
                if (!(lm instanceof LinearLayoutManager)) return;
                int last = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
                if (last >= files.size() - 3) loadMore();
            }
        });
        binding.refresh.setOnRefreshListener(this::fetchFiles);
        // 加载圈配色：默认白底圈与本页深面口径不搭，改深底（#26282C，与登录/网盘弹窗深面同系）＋亮弧（本页深面行主字色），一眼看得清
        binding.refresh.setProgressBackgroundColorSchemeColor(0xFF26282C);
        binding.refresh.setColorSchemeColors(0xFFF1F4F5);
        binding.fab.setOnClickListener(v -> showFabMenu());
        if (fabActions().isEmpty()) binding.fab.setVisibility(View.GONE);
        crumbs.add(new Crumb(rootCid, "根目录"));
        String openCid = getIntent().getStringExtra(EXTRA_OPEN_CID);
        if (!TextUtils.isEmpty(openCid) && !openCid.equals(rootCid)) {
            String openName = getIntent().getStringExtra(EXTRA_OPEN_NAME);
            // 直达落地同样逐层可退：调用方给了父级就把父级垫进栈（根＋父级＋该目录），返回先退父级再退根
            String openParent = getIntent().getStringExtra(EXTRA_OPEN_PARENT);
            if (!TextUtils.isEmpty(openParent) && !isRootCid(openParent)) {
                crumbs.add(new Crumb(openParent, "所在目录"));
            }
            crumbs.add(new Crumb(openCid, TextUtils.isEmpty(openName) ? "目录" : openName));
        }
        // 搜索入口：三家都有，右上角明面按钮（布局里的 btn_search），不藏菜单
        binding.btnSearch.setOnClickListener(v -> showSearchDialog());
        viewReady = true;
        fetchFiles();
    }

    /** 搜索弹窗会话状态：结果（只含文件夹＋视频，过滤在组装处做）、已取原始条数（分页 offset 按原始条数走）、代次（换词重搜时旧请求作废）。 */
    private static class SearchState {
        final List<CloudFile> results = new ArrayList<>();
        String keyword = "";
        int fetched;
        boolean loading;
        boolean hasMore;
        int gen;
        /** 并发首拉攒页：页号（offset/SEARCH_LIMIT）→该页原始结果，先回的先攒、按页序拼进 results 后移除，不许乱序上屏。 */
        final Map<Integer, List<CloudFile>> pendingPages = new HashMap<>();
        /** 下一个待拼页号：只从这一页起连续拼，缺页就等它回来。 */
        int mergePage;
        /** 下一个待发 offset：领完即推进，同一个 offset 绝不重发。 */
        int nextOffset;
        /** 在飞页数（首拉并发时可到 SEARCH_BURST_PAGES，续拉时为 1）；loading 与它同步维护。 */
        int inFlight;
        /** 已拼到尾页（一页不满 SEARCH_LIMIT）：其后攒着的页一律丢弃、fetched 不给它们记账。 */
        boolean endReached;
        /** 任一页失败后停链，首个失败定状态（口径同旧单线版）。 */
        boolean failed;
        /** 本词的并发首拉已发出过（换词在 runSearch 归零）。 */
        boolean burstDone;
        /** 连续拼入 0 条可见行的页数：全过滤自动续拉的连拉计数，含首拉 3 页一起算，到 AUTO_CHAIN_MAX 停。 */
        int chain;
    }

    /**
     * 搜索弹窗（他定的口径）：点右上角按钮在屏幕中间弹，不另开搜索页、不切本页列表；
     * 弹窗不大不小（宽留边、高封顶约七成），输词后结果直接在弹窗里出、列表在弹窗内滑动续拉。
     * 搜的是当前这家网盘自己的文件（115 只搜视频、走 searchVideoFiles，其余两家 drive.searchFiles）；形态走本页既有深面玻璃口径。
     */
    private void showSearchDialog() {
        SearchState st = new SearchState();
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (8 * density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);
        EditText input = makeInput("搜索" + drive.getName() + "文件");
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        layout.addView(input);
        TextView status = new TextView(this);
        status.setTextColor(0x99FFFFFF);
        status.setTextSize(13);
        int padH = (int) (16 * density);
        int padV = (int) (8 * density);
        status.setPadding(padH, padV, padH, 0);
        // 占位不撤位：状态行显隐只换 INVISIBLE/VISIBLE，内容高度恒定——
        // 用 GONE 的话每次搜索状态变化都改一次框高、居中窗口跟着重排（他点名的「乱动」之一）
        status.setVisibility(View.INVISIBLE);
        layout.addView(status);
        ListView list = new ListView(this);
        SearchAdapter adapter = new SearchAdapter(st);
        adapter.listView = list;
        list.setAdapter(adapter);
        int listHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.46f);
        layout.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listHeight));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("搜网盘")
                .setView(layout)
                .setPositiveButton("搜索", null)
                .setNegativeButton("关闭", null)
                .create();
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(st, input, adapter, status);
                return true;
            }
            return false;
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (st.loading || !st.hasMore || totalItemCount == 0) return;
                if (firstVisibleItem + visibleItemCount >= totalItemCount - 3) {
                    fetchSearchPage(st, adapter, status, dialog);
                }
            }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            CloudFile file = st.results.get(position);
            if (file.isDir()) {
                dialog.dismiss();
                openDirFromSearch(file);
            } else if (file.isAudio()) {
                dialog.dismiss();
                Notify.show("暂不支持打开该文件类型");
            } else if (file.isVideo()) {
                // 他定的规矩「在哪点开视频，回来还是在哪里」：点视频不关弹窗，
                // 播放页盖在弹窗上头，看完返回弹窗还在——关键词、结果、滚动位置都原样
                // （状态全在 st 与列表视图本身里，弹窗不关就不会丢）。
                // 只收键盘：不收的话回来时键盘会随弹窗窗口重新聚焦又弹出来盖住结果；
                // 窗口输入模式一并改隐藏，用户回来再点输入框照常弹键盘。
                input.clearFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
                }
                playFile(file);
            } else {
                dialog.dismiss();
                gotoParentDir(file);
            }
        });
        // 「搜索」钮的点击覆盖与键盘行为在 OnShowListener 里装（系统默认行为会关弹窗、不搜）。
        dialog.setOnShowListener(d -> {
            // 覆盖「搜索」的默认点击（默认会关弹窗）：改成不关弹窗、就地在弹窗里出结果
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> runSearch(st, input, adapter, status));
            if (dialog.getWindow() != null) {
                // 键盘只许盖住框底，不许 ADJUST_RESIZE 把居中弹窗顶着重排来回跳
                // （输入框在框顶，输词走键盘上的搜索键提交）
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            }
            // 尺寸在显示这一拍同步定版，不走 post 延后拽尺寸（开框乱动的共用病根，见 sizeDialogNow）
            sizeDialogNow(dialog, 0.7f);
            input.requestFocus();
        });
        dialog.show();
    }

    private void runSearch(SearchState st, EditText input, SearchAdapter adapter, TextView status) {
        String kw = input.getText().toString().trim();
        if (kw.isEmpty()) {
            Notify.show("关键词不能为空");
            return;
        }
        st.keyword = kw;
        st.gen++;
        st.fetched = 0;
        st.hasMore = false;
        st.results.clear();
        // 并发取页的状态整套归零：上一词在飞的页回来会被 gen 校验丢弃，不许碰新词的记账
        st.pendingPages.clear();
        st.mergePage = 0;
        st.nextOffset = 0;
        st.inFlight = 0;
        st.loading = false;
        st.endReached = false;
        st.failed = false;
        st.burstDone = false;
        st.chain = 0;
        adapter.notifyDataSetChanged();
        fetchSearchPage(st, adapter, status, null);
    }

    /**
     * 搜索取页入口：换词后的首拉一次并发发前 SEARCH_BURST_PAGES 页（他怕搜索结果出得慢，
     * 不再单线一页一页等）；3 页之后走老路——滑到底才单页续拉（滚动监听调到这里时 burstDone 已真）。
     * dialog 非空时（滚动续拉）顺带校验弹窗还开着。
     * PikPak 例外只发 1 页：它的搜索分页是 page_token 链，下一页的 token 要等上一页回来才有，
     * 并发发出去只会拿到空页——接口物理限制，它一家维持单页顺序拉。
     */
    private void fetchSearchPage(SearchState st, SearchAdapter adapter, TextView status, AlertDialog dialog) {
        if (st.loading || st.failed || st.endReached) return;
        status.setVisibility(View.VISIBLE);
        status.setText("正在搜索…");
        if (!st.burstDone) {
            st.burstDone = true;
            int burst = drive instanceof DrivePikPak ? 1 : SEARCH_BURST_PAGES;
            for (int i = 0; i < burst; i++) requestSearchPage(st, adapter, status, dialog);
        } else {
            requestSearchPage(st, adapter, status, dialog);
        }
    }

    /** 发一页搜索请求：offset 从 st.nextOffset 领、领完即推进（同一 offset 绝不重发）；页号随请求带走，回来按页号攒。 */
    private void requestSearchPage(SearchState st, SearchAdapter adapter, TextView status, AlertDialog dialog) {
        int gen = st.gen;
        int offset = st.nextOffset;
        int pageIndex = offset / SEARCH_LIMIT;
        st.nextOffset += SEARCH_LIMIT;
        st.inFlight++;
        st.loading = true;
        String kw = st.keyword;
        new Thread(() -> {
            List<CloudFile> page = null;
            Exception error = null;
            try {
                // 115 只搜视频（他亲点）：走 Drive115.searchVideoFiles（服务端 type=4＋结果兜底过滤）；
                // 其余两家维持 searchFiles 全类型口径不变。
                page = Drive115.ID.equals(drive.getId())
                        ? ((Drive115) drive).searchVideoFiles(kw, offset, SEARCH_LIMIT)
                        : drive.searchFiles(kw, offset, SEARCH_LIMIT);
            } catch (Exception e) {
                error = e;
            }
            List<CloudFile> raw = page;
            Exception err = error;
            runOnUiThread(() -> onSearchPageArrived(st, adapter, status, dialog, gen, pageIndex, kw, raw, err));
        }).start();
    }

    /** 一页回来：gen 代次/弹窗校验照旧（换词、关弹窗后在飞的页全作废）；失败首个定状态并停链；成功先按页号攒下，再试按序拼。 */
    private void onSearchPageArrived(SearchState st, SearchAdapter adapter, TextView status, AlertDialog dialog,
                                     int gen, int pageIndex, String kw, List<CloudFile> raw, Exception err) {
        if (gen != st.gen) return;
        if (dialog != null && !dialog.isShowing()) return;
        st.inFlight--;
        if (st.inFlight <= 0) {
            st.inFlight = 0;
            st.loading = false;
        }
        if (st.failed) return;
        if (err != null) {
            st.failed = true;
            st.hasMore = false;
            Log.e(TAG, "搜索失败 kw=" + kw, err); // 真因进日志，脸上只给人话
            if (st.results.isEmpty()) {
                status.setVisibility(View.VISIBLE);
                status.setText(humanSearchError(err));
            } else {
                status.setVisibility(View.INVISIBLE);
                Notify.show("加载更多失败，请稍后再试");
            }
            return;
        }
        st.pendingPages.put(pageIndex, raw == null ? new ArrayList<>() : raw);
        mergeSearchPages(st, adapter, status, dialog);
    }

    /**
     * 按页序拼：从 mergePage 起连续拼（先回的后页只攒着等，缺页绝不抢先上屏、不许乱序）。
     * fetched/hasMore 记账按原始条数走、过滤在拼装处做（searchVisible）；
     * 拼到一页不满 SEARCH_LIMIT 就是尾页（endReached），其后攒着的页整批丢弃、不记账。
     * 首屏不等满 3 页：第一页拼上就先刷新上屏，后两页回来续拼到尾部。
     */
    private void mergeSearchPages(SearchState st, SearchAdapter adapter, TextView status, AlertDialog dialog) {
        // 刷新位置保持（流畅度 D2/D7）：BaseAdapter 没有差量 API，先记下首可见位置＋偏移，刷新后恢复，续拼不跳位
        ListView lv = adapter.listView;
        int firstPos = 0;
        int topOff = 0;
        if (lv != null && !st.results.isEmpty()) {
            firstPos = lv.getFirstVisiblePosition();
            View top = lv.getChildAt(0);
            topOff = top == null ? 0 : top.getTop();
        }
        boolean changed = false;
        while (st.pendingPages.containsKey(st.mergePage)) {
            List<CloudFile> raw = st.pendingPages.remove(st.mergePage);
            st.mergePage++;
            st.fetched += raw.size();
            int before = st.results.size();
            // 搜索结果展示过滤在组装处做、分页记账按原始条数走：115 只出视频（searchVisible），
            // 其余两家同文件列表口径（文件夹＋大于 30MB 的视频）
            for (CloudFile f : raw) if (searchVisible(f)) st.results.add(f);
            if (st.results.size() > before) st.chain = 0;
            else st.chain++;
            changed = true;
            if (raw.size() < SEARCH_LIMIT) {
                st.endReached = true;
                st.pendingPages.clear();
                break;
            }
        }
        st.hasMore = !st.endReached;
        if (changed) {
            adapter.notifyDataSetChanged();
            if (lv != null && firstPos > 0) lv.setSelectionFromTop(firstPos, topOff);
        }
        if (!st.results.isEmpty()) {
            status.setVisibility(View.INVISIBLE);
            return;
        }
        if (st.endReached || st.failed) {
            status.setVisibility(View.VISIBLE);
            status.setText("没找到「" + st.keyword + "」");
            return;
        }
        if (st.inFlight > 0) {
            // 前面的页还没回齐（乱序等待中），保持「正在搜索…」，等它回来再拼
            status.setVisibility(View.VISIBLE);
            status.setText("正在搜索…");
            return;
        }
        // 已回的页全被过滤光、列表还空着滑不动：自动续拉（与主列表 D4 同口径），连拉总上限含首拉 3 页
        // 一起算（chain 计数），到顶仍空按真空收尾——不死循环、不重复发 offset
        if (st.chain < AUTO_CHAIN_MAX) {
            status.setVisibility(View.VISIBLE);
            status.setText("正在搜索…");
            requestSearchPage(st, adapter, status, dialog);
        } else {
            st.hasMore = false;
            status.setVisibility(View.VISIBLE);
            status.setText("没找到「" + st.keyword + "」");
        }
    }

    /** 搜索失败的人话：驱动层已给人话的短消息直接用，空的/带 JSON 的/过长的统一兜底。 */
    private static String humanSearchError(Exception e) {
        String m = msg(e);
        if (m.isEmpty() || m.contains("{") || m.length() > 60) return "搜索失败，请稍后再试";
        return m;
    }

    /** 面包屑栈削回只剩根目录（落地拼栈的起点）。 */
    private void resetCrumbsToRoot() {
        while (crumbs.size() > 1) crumbs.remove(crumbs.size() - 1);
    }

    /** 把面包屑栈截到 cid 那一级（截完它就是栈顶）；栈里没有这一级返回 false、栈不动。 */
    private boolean truncateCrumbsTo(String cid) {
        for (int i = crumbs.size() - 1; i >= 1; i--) {
            if (crumbs.get(i).cid.equals(cid)) {
                while (crumbs.size() > i + 1) crumbs.remove(crumbs.size() - 1);
                return true;
            }
        }
        return false;
    }

    /**
     * 搜索结果点文件夹：关弹窗后本页直达该文件夹。落地拼栈带上父级、逐层可退
     * （他的返回规矩：每次只退一层，绝不无声掉回根目录）：
     * 父级已知且非根——它在来时路径上就截到它再压该文件夹，不在就拼「根＋父级＋该文件夹」，返回先退父级；
     * 父级就是根——栈＝根＋该文件夹；父级未知——保留来时栈、把该文件夹压栈顶，返回先退回他来时的位置。
     */
    private void openDirFromSearch(CloudFile dir) {
        String parent = dir.getParentCid();
        if (TextUtils.isEmpty(parent)) {
            crumbs.add(new Crumb(dir.getId(), dir.getName()));
        } else if (isRootCid(parent)) {
            resetCrumbsToRoot();
            crumbs.add(new Crumb(dir.getId(), dir.getName()));
        } else if (truncateCrumbsTo(parent)) {
            crumbs.add(new Crumb(dir.getId(), dir.getName()));
        } else {
            resetCrumbsToRoot();
            crumbs.add(new Crumb(parent, "所在目录"));
            crumbs.add(new Crumb(dir.getId(), dir.getName()));
        }
        fetchFiles();
    }

    /**
     * 搜索结果点其它文件：跳到它所在目录（目录名服务端没给，面包屑用「所在目录」占位）。
     * 所在目录未知时绝不削栈掉回根目录（旧行为正是这样）：保留来时栈、原地刷新，停在他来时的地方；
     * 已知时——在来时路径上就截到那一级，不在就拼「根＋所在目录」；它就是根目录时栈只剩根一级
     * （这是点选的落点本身，到根再按返回才是退出本页）。
     */
    private void gotoParentDir(CloudFile file) {
        String parent = file.getParentCid();
        if (TextUtils.isEmpty(parent)) {
            fetchFiles();
            return;
        }
        if (isRootCid(parent)) {
            resetCrumbsToRoot();
        } else if (!truncateCrumbsTo(parent)) {
            resetCrumbsToRoot();
            crumbs.add(new Crumb(parent, "所在目录"));
        }
        fetchFiles();
    }

    /** 各家根目录 id 不一：115 是 "0"，夸克/PikPak 的根在本页 crumbs 里是 ""（夸克条目 parentCid 仍可能回 "0"）。 */
    private boolean isRootCid(String cid) {
        if (TextUtils.isEmpty(cid)) return true;
        if (cid.equals(rootCid)) return true;
        return "0".equals(cid) && rootCid.isEmpty();
    }

    /** 搜索结果行：一行一条——文件名在左（单行省略），右侧小字（文件夹/大小）。字色与离线任务行同套。 */
    private class SearchAdapter extends BaseAdapter {

        private final SearchState st;
        /** 宿主 ListView（showSearchDialog 建弹窗时回填）：封面定点刷新、续拉保持滚动位置都靠它定位。 */
        ListView listView;

        SearchAdapter(SearchState st) {
            this.st = st;
        }

        /** 截帧封面落地后只刷这一行（流畅度 D1 搜索侧）：按 pickcode 实时定位，不可见/已不在结果里就丢弃，不整表重排打断滚动。 */
        private void refreshCoverRow(CloudFile file) {
            String pc = file.getPickCode();
            if (TextUtils.isEmpty(pc) || listView == null) return;
            int idx = -1;
            for (int i = 0; i < st.results.size(); i++) {
                if (pc.equals(st.results.get(i).getPickCode())) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) return;
            int first = listView.getFirstVisiblePosition();
            if (idx < first || idx > listView.getLastVisiblePosition()) return;
            View row = listView.getChildAt(idx - first);
            if (row != null) getView(idx, row, listView);
        }

        @Override
        public int getCount() {
            return st.results.size();
        }

        @Override
        public CloudFile getItem(int position) {
            return st.results.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        /** 搜索行视图持有（封面图＋文件名＋右侧小字）。 */
        private class SearchRow {
            final LinearLayout root;
            final ImageView ivThumb;
            final TextView tvName;
            final TextView tvMeta;

            SearchRow(LinearLayout root, ImageView ivThumb, TextView tvName, TextView tvMeta) {
                this.root = root;
                this.ivThumb = ivThumb;
                this.tvName = tvName;
                this.tvMeta = tvMeta;
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            SearchRow row;
            if (convertView instanceof LinearLayout && convertView.getTag() instanceof SearchRow) {
                row = (SearchRow) convertView.getTag();
            } else {
                float density = getResources().getDisplayMetrics().density;
                LinearLayout layout = new LinearLayout(CloudDriveActivity.this);
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.setGravity(Gravity.CENTER_VERTICAL);
                int padH = (int) (16 * density);
                int padV = (int) (11 * density);
                layout.setPadding(padH, padV, padH, padV);
                ImageView ivThumb = new ImageView(CloudDriveActivity.this);
                ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(
                        (int) (40 * density), (int) (40 * density));
                ivLp.setMarginEnd((int) (12 * density));
                layout.addView(ivThumb, ivLp);
                TextView tvName = new TextView(CloudDriveActivity.this);
                tvName.setSingleLine(true);
                tvName.setEllipsize(TextUtils.TruncateAt.END);
                tvName.setTextColor(0xFFF1F4F5);
                tvName.setTextSize(14);
                layout.addView(tvName, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView tvMeta = new TextView(CloudDriveActivity.this);
                tvMeta.setSingleLine(true);
                tvMeta.setTextColor(0x99FFFFFF);
                tvMeta.setTextSize(13);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginStart((int) (12 * density));
                layout.addView(tvMeta, lp);
                row = new SearchRow(layout, ivThumb, tvName, tvMeta);
                layout.setTag(row);
            }
            CloudFile f = st.results.get(position);
            row.tvName.setText(f.getName());
            row.tvMeta.setText(f.isDir() ? "文件夹" : f.getFormatSize());
            // 封面与文件列表同口径（bindRowCover）：文件夹仍是文件夹图标，只给视频行上图；
            // 弹窗是深面，图标占位着浅色
            bindRowCover(row.ivThumb, f, 0xFFF1F4F5, () -> refreshCoverRow(f));
            return row.root;
        }
    }

    /**
     * 弹窗尺寸在显示这一拍同步定版（搜索/离线任务/文件夹选择器共用，他定的「不大不小」口径）：
     * 宽度两侧各留 24dp，高度＝内容自然高与封顶（屏高 fraction）取小，结果在框内滚动。
     * 病根（他点名「115 里面的弹窗都乱动」）：旧法在 show() 之后用 decor.post 排一拍再 setLayout
     * 拽尺寸——框先按系统自然尺寸上屏、下一拍被拽到目标尺寸，开框必跳一跳；冷路径下那一拍
     * 拖得更晚、跳得更明显（与本日队列面板「定位抢跑」同病）。登录链的弹窗（PanLoginDialog、
     * 登录四弹窗）都是在 onStart 里同步 setLayout 定版、没这毛病——照它们的口径收：
     * 在 OnShowListener 里当场对 decor 做一次 AT_MOST 测量取自然高，同一拍 setLayout 定死。
     */
    private void sizeDialogNow(AlertDialog dialog, float maxHeightFraction) {
        Window window = dialog.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        if (decor == null) return;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = dm.widthPixels - (int) (48 * dm.density);
        int maxHeight = (int) (dm.heightPixels * maxHeightFraction);
        if (width <= 0 || maxHeight <= 0) return;
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
        int height = Math.min(decor.getMeasuredHeight(), maxHeight);
        if (height > 0) window.setLayout(width, height);
    }

    /**
     * 带输入框的小弹窗（离线下载/转存/新建文件夹共用）：键盘只许盖住框底，
     * 不许走默认 ADJUST_RESIZE——居中弹窗在键盘弹起时会按缩小后的可视区重排重居中，
     * 收起又弹回来，就是「点输入框框就来回跳」。这几个弹窗的输入框都在框体上部，
     * 键盘盖不到，改 ADJUST_NOTHING 后框纹丝不动、输入照常。
     */
    private void keepDialogStill(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (firstResume) {
            firstResume = false;
            return;
        }
        // 未登录那条路在 initView 里已 start PanLogin＋finish，viewReady 不会置真，走不到这里；
        // 已 finish 的实例也不会再有 onResume，不搅乱现行登录跳转。
        if (!viewReady || drive == null || isFinishing()) return;
        updateDriveInfo();
        // 流畅度 D5（他拍板「我在哪点开视频的，回来还是在哪里」）：纯从播放/子页返回不再重拉目录——
        // 已加载的页数与滚动位置原样保留，只刷顶部会员信息（L-15 的这一半保留在 updateDriveInfo）。
        // 只有页内数据动作（长按管理/离线提交/转存成功/新建文件夹）在离开期间完成的，
        // 才经 markResumeRefresh 标记、回来全量重拉（那种情况数据真变了，位置让位于正确性）。
        if (resumeRefresh) fetchFiles();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        super.onPause();
    }

    /** 数据动作完成后的刷新口：本页在前台就地全量重拉；不在前台只打标记，等 onResume 回来再拉。 */
    private void refreshAfterDataChange() {
        if (activityResumed) fetchFiles();
        else markResumeRefresh();
    }

    private void markResumeRefresh() {
        resumeRefresh = true;
    }

    /** 顶部显示会员状态 + 容量（用户明确要求） */
    private void updateDriveInfo() {
        String vip = "";
        if (Drive115.ID.equals(drive.getId())) vip = com.fongmi.android.tv.utils.PanAuth.get115Vip();
        else if (DriveQuark.ID.equals(drive.getId())) vip = com.fongmi.android.tv.utils.PanAuth.getQuarkVip();
        String vipText = vip.isEmpty() ? "普通用户" : vip;
        binding.tvDriveInfo.setText(vipText);
        new Thread(() -> {
            try {
                String summary = drive.storageSummary();
                String text = vipText + (summary.isEmpty() ? "" : " · " + summary);
                runOnUiThread(() -> binding.tvDriveInfo.setText(text));
            } catch (Exception ignored) {
            }
        }).start();
    }

    private CloudDrive createDrive(String driveId) {
        if (Drive115.ID.equals(driveId)) return new Drive115();
        if (DrivePikPak.ID.equals(driveId)) return new DrivePikPak(this);
        if (DriveQuark.ID.equals(driveId)) return new DriveQuark();
        return null;
    }

    @Override
    protected void onBackInvoked() {
        if (crumbs.size() > 1) {
            crumbs.remove(crumbs.size() - 1);
            fetchFiles();
        } else {
            super.onBackInvoked();
        }
    }

    private String currentCid() {
        return crumbs.get(crumbs.size() - 1).cid;
    }

    private void setLoading(boolean loading) {
        // 圈只留给用户下拉刷新：程序加载（进文件夹等）不许自己起圈，
        // 加载结束只负责把下拉触发的那圈收掉（没在转时调它无副作用）
        if (!loading && binding.refresh.isRefreshing()) binding.refresh.setRefreshing(false);
    }

    /**
     * 搜索结果展示过滤：115 只出视频——Drive115.searchVideoFiles 已在数据源头
     * 保证只回视频，这里再按同一谓词守一道（防其它路混入）；且不套主列表的
     * 30MB 藏小视频口径：搜索是按名主动找文件，搜到了又藏掉等于搜不到。
     * 其余两家搜索同文件列表口径，走 visibleInList。
     */
    private boolean searchVisible(CloudFile f) {
        if (drive != null && Drive115.ID.equals(drive.getId())) return !f.isDir() && f.isVideo();
        return visibleInList(f);
    }

    /**
     * 展示过滤（文件列表与非 115 搜索结果共用）：
     * 115 按他的定案——列表只留文件夹＋视频，文档/图片/压缩包/音频一律不列
     * （2026-10-28「做全」的全类型可见只对夸克/PikPak 成立，两家维持不变、非视频走长按管理动作）。
     * 视频口径三家共同：大小在 30MB 以内的短视频直接藏掉（治短视频广告，「广告没有这么大」）；
     * 大小取不到/为 0 的视频不藏，免得误伤字段缺失的正常文件。文件夹永远不受影响、永远显示。
     */
    private boolean visibleInList(CloudFile f) {
        if (f.isDir()) return true;
        if (drive != null && Drive115.ID.equals(drive.getId())) {
            return f.isVideo() && (f.getSize() <= 0 || f.getSize() > MIN_VIDEO_SIZE);
        }
        if (!f.isVideo()) return true;
        return f.getSize() <= 0 || f.getSize() > MIN_VIDEO_SIZE;
    }

    private void fetchFiles() {
        fetchGen++;
        resumeRefresh = false;
        hasMore = true;
        loadingMore = false;
        fetchedCount = 0;
        autoChain = 0;
        fetchPage(0, false, fetchGen);
    }

    private void loadMore() {
        if (loadingMore || !hasMore) return;
        loadingMore = true;
        fetchPage(fetchedCount, true, fetchGen);
    }

    /** 分页续取：offset 翻页；PikPak 的 listDir 内部用 page_token 兑 offset（见 DrivePikPak）。 */
    private void fetchPage(int offset, boolean append, int gen) {
        if (!append) setLoading(true);
        String cid = currentCid();
        new Thread(() -> {
            try {
                List<CloudFile> result = drive.listDir(cid, offset, PAGE_LIMIT);
                runOnUiThread(() -> {
                    if (gen != fetchGen || !cid.equals(currentCid())) return;
                    if (!append) setLoading(false);
                    loadingMore = false;
                    if (!append) files.clear();
                    int insertStart = files.size();
                    int added = 0;
                    if (result != null) {
                        fetchedCount = append ? fetchedCount + result.size() : result.size();
                        // 展示口径走 visibleInList：115 只留文件夹＋视频（再藏 30MB 以内的短视频），
                        // 夸克/PikPak 全类型可见（同款藏小视频）。
                        // 过滤只在组装展示处做，不动各 Drive 的 listDir；分页 offset 与 hasMore 都按原始条数算，
                        // 免得一页全是被过滤的文件时被误判成到底、把后面的视频漏掉。
                        for (CloudFile f : result) if (visibleInList(f)) {
                            files.add(f);
                            added++;
                        }
                    }
                    // 取满一页＝可能还有下一页；不满一页＝已到头。恰好整 100 倍数时多取一页空页收尾，不丢文件。
                    hasMore = result != null && result.size() >= PAGE_LIMIT;
                    // 流畅度 D2：续拉改差量插入只绑新行，不再整表重绑打断 fling；首屏/换目录仍整表。
                    // 整页被过滤光（added==0）时先不刷新，下面自动续拉，别白打一发空的全表刷新
                    if (!append) {
                        adapter.notifyDataSetChanged();
                        renderBreadcrumb();
                    } else if (added > 0) {
                        adapter.notifyItemRangeInserted(insertStart, added);
                    }
                    // 流畅度 D4：整页被过滤光时展示列表不增长、滚动触发不了续拉——首屏同样自动续拉
                    // （此前只写在续拉路，首屏全过滤就是假空、出不来也滑不动）；拉出可见行即停链，
                    // 连拉到上限仍空才是真空。fetchedCount/hasMore 已在本回调前段更新完，续拉不会重复拉同一页
                    if (added > 0) autoChain = 0;
                    if (added == 0 && hasMore && autoChain < AUTO_CHAIN_MAX) {
                        autoChain++;
                        loadMore();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    // A5：失败分支补与成功路同款 gen／cid 丢弃校验，过期请求失败不误弹“加载失败”。
                    if (gen != fetchGen || !cid.equals(currentCid())) return;
                    if (!append) setLoading(false);
                    loadingMore = false;
                    if (!append) Notify.show("加载失败：" + msg(e));
                    else Notify.show("加载更多失败：" + msg(e));
                });
            }
        }).start();
    }

    private void renderBreadcrumb() {
        binding.breadcrumb.removeAllViews();
        for (int i = 0; i < crumbs.size(); i++) {
            Crumb c = crumbs.get(i);
            if (i > 0) addCrumbSeparator();
            TextView tv = new TextView(this);
            tv.setText(c.name);
            tv.setTextSize(13);
            tv.setSingleLine(true);
            boolean last = (i == crumbs.size() - 1);
            tv.setTextColor(last ? 0xFF0C0E12 : 0xFF5A6675);
            if (!last) {
                final int index = i;
                tv.setClickable(true);
                tv.setFocusable(true);
                tv.setOnClickListener(v -> {
                    while (crumbs.size() > index + 1) crumbs.remove(crumbs.size() - 1);
                    fetchFiles();
                });
            }
            binding.breadcrumb.addView(tv);
        }
    }

    private void addCrumbSeparator() {
        TextView sep = new TextView(this);
        sep.setText("  ›  ");
        sep.setTextSize(13);
        sep.setTextColor(0xFF9AA3AF);
        binding.breadcrumb.addView(sep);
    }

    private void openDir(CloudFile dir) {
        crumbs.add(new Crumb(dir.getId(), dir.getName()));
        fetchFiles();
    }

    private void playFile(CloudFile file) {
        if (drive instanceof Drive115) {
            play115File(this, file);
            return;
        }
        Notify.show("正在获取播放地址…");
        new Thread(() -> {
            try {
                String url = drive.getPlayUrl(file);
                runOnUiThread(() -> {
                    if (!TextUtils.isEmpty(url)) {
                        // 带上文件名做标题：此前只传 url，详情标题只能拿代理 URL 本身充数
                        VideoActivity.start(this, SiteApi.PUSH, url, file.getName());
                    } else {
                        Notify.show("获取播放地址失败");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "播放失败", e); // 完整堆栈打到 Logcat
                String m = msg(e);
                String toast;
                if (m.contains("代理启动失败")) {
                    toast = "代理启动失败，请重试";
                } else if (m.contains("pick_code")) {
                    toast = "文件 pick_code 为空";
                } else {
                    toast = "获取播放地址失败：" + m;
                }
                final String ft = toast;
                runOnUiThread(() -> Notify.show(ft));
            }
        }).start();
    }

    /**
     * 115 文件直达起播（网盘自己的播放会话）：供本页点击与首页搜索的 115 结果共用同一条路。
     * 确保本机代理已启动（播放器只认代理地址，不直连 115），清单组装成直达选集载荷走
     * VideoActivity.startPan115，线路归「115网盘」，不经推送链合成详情。
     */
    public static void play115File(Activity activity, CloudFile file) {
        Notify.show("正在获取播放地址…");
        new Thread(() -> {
            try {
                Drive115 d115 = new Drive115();
                // 用 startProxy()（优选端口 32115+），不要裸调 NanoHTTPD.start()
                com.fongmi.android.tv.cloud.drive115.Proxy115
                        .getInstance(d115.getClient(), d115.getCookieJar())
                        .startProxy();
                PlayUrlBuilder115.PlayList pl = d115.playList(file);
                if (pl == null || pl.entries.isEmpty()) throw new Exception("取播放地址失败");
                String episodesJson = pan115EpisodesJson(pl);
                String firstUrl = pl.entries.get(pl.defaultIndex).proxyUrl;
                int selIdx = pl.defaultIndex;
                activity.runOnUiThread(() -> VideoActivity.startPan115(activity, firstUrl, file.getName(), episodesJson, selIdx));
            } catch (Exception e) {
                Log.e(TAG, "115 播放失败", e); // 完整堆栈打到 Logcat
                String m = msg(e);
                String toast;
                if (m.contains("代理启动失败")) {
                    toast = "代理启动失败，请重试";
                } else if (m.contains("pick_code")) {
                    toast = "文件 pick_code 为空";
                } else {
                    toast = "获取播放地址失败：" + m;
                }
                final String ft = toast;
                activity.runOnUiThread(() -> Notify.show(ft));
            }
        }).start();
    }

    /** 115 播放清单 → 直达选集载荷（单线路「115网盘」，选集即画质档，形态同 startLocal） */
    private static String pan115EpisodesJson(PlayUrlBuilder115.PlayList pl) throws Exception {
        org.json.JSONArray eps = new org.json.JSONArray();
        for (PlayUrlBuilder115.PlayEntry e : pl.entries) {
            JSONObject ep = new JSONObject();
            ep.put("name", e.label);
            ep.put("url", e.proxyUrl);
            eps.put(ep);
        }
        JSONObject flag = new JSONObject();
        flag.put("name", "115网盘");
        flag.put("episodes", eps);
        JSONObject root = new JSONObject();
        root.put("flags", new org.json.JSONArray().put(flag));
        return root.toString();
    }

    private void confirmDelete(CloudFile file) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("删除")
                .setMessage("确定删除「" + file.getName() + "」吗？")
                .setPositiveButton("删除", (d, w) -> doDelete(file))
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
    }

    private void doDelete(CloudFile file) {
        new Thread(() -> {
            try {
                boolean ok = drive.delete(Collections.singletonList(file.getId()), file.isDir());
                runOnUiThread(() -> {
                    Notify.show(ok ? "已删除" : "删除失败");
                    refreshAfterDataChange();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Notify.show("删除失败：" + msg(e)));
            }
        }).start();
    }

    /**
     * 文件管理动作单（长按、或非视频/音频文件点击）：按能力给项，不给点了才失败的空项。
     * 播放只对视频/音频给；重命名/移动/复制目前只有 115 有口（Drive115），其余网盘不显示；
     * 删除三家都有。
     */
    private void showFileActions(CloudFile file) {
        List<String> actions = new ArrayList<>();
        if (file.isVideo() || file.isAudio()) actions.add("播放");
        boolean is115 = drive != null && Drive115.ID.equals(drive.getId());
        if (is115) {
            actions.add("重命名");
            actions.add("移动到…");
            actions.add("复制到…");
        }
        actions.add("删除");
        String[] items = actions.toArray(new String[0]);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(items, (d, which) -> {
                    String action = actions.get(which);
                    if ("播放".equals(action)) {
                        if (file.isVideo()) playFile(file);
                        else Notify.show("暂不支持打开该文件类型");
                    } else if ("重命名".equals(action)) showRenameDialog(file);
                    else if ("移动到…".equals(action)) showMoveOrCopyPicker(file, false);
                    else if ("复制到…".equals(action)) showMoveOrCopyPicker(file, true);
                    else if ("删除".equals(action)) confirmDelete(file);
                })
                .create();
        dialog.show();
    }

    private void showRenameDialog(CloudFile file) {
        EditText input = makeInput("新名称");
        input.setText(file.getName());
        input.setSelection(input.getText().length());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("重命名")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Notify.show("名称不能为空");
                        return;
                    }
                    if (name.equals(file.getName())) return;
                    doRename(file, name);
                })
                .setNegativeButton("取消", null)
                .create();
        keepDialogStill(dialog);
        dialog.show();
    }

    private void doRename(CloudFile file, String newName) {
        new Thread(() -> {
            try {
                boolean ok = ((Drive115) drive).rename(file, newName);
                runOnUiThread(() -> {
                    Notify.show(ok ? "已重命名" : "重命名失败");
                    if (ok) refreshAfterDataChange();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Notify.show("重命名失败：" + msg(e)));
            }
        }).start();
    }

    /** 移动/复制的目标选择：复用文件夹选择器，但不写「保存到」的记忆（那是转存/离线的口径）。 */
    private void showMoveOrCopyPicker(CloudFile file, boolean copy) {
        TargetFolder target = new TargetFolder(rootCid, "根目录");
        showFolderPicker(target, () -> doMoveOrCopy(file, target.cid, copy),
                copy ? "复制到这里" : "移动到这里", false);
    }

    private void doMoveOrCopy(CloudFile file, String targetCid, boolean copy) {
        if (!copy && targetCid != null && targetCid.equals(file.getParentCid())) {
            Notify.show("已经在这个文件夹里了");
            return;
        }
        new Thread(() -> {
            try {
                boolean ok = copy ? ((Drive115) drive).copy(file, targetCid)
                        : ((Drive115) drive).move(file, targetCid);
                runOnUiThread(() -> {
                    Notify.show(ok ? (copy ? "已复制" : "已移动") : (copy ? "复制失败" : "移动失败"));
                    if (ok) refreshAfterDataChange();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Notify.show((copy ? "复制失败：" : "移动失败：") + msg(e)));
            }
        }).start();
    }

    /**
     * FAB 菜单按各家 drive 的真实能力建，不支持的入口直接不显示，不再先给入口、点了才弹失败：
     * 离线下载：115／PikPak 实现，夸克恒抛 → 夸克不显示；
     * 转存分享链接：三家均已实现（115 待真机首验；夸克本批按 sharepage 口径补齐、待真机首验；PikPak 已验实现）→ 三家都显示；
     * 新建文件夹：PikPak 已实现、115 本批补 /files/add（待真机首验），夸克走默认实现恒抛 → 夸克不显示；
     * 离线任务：115／PikPak 的 taskList 已实现，夸克 taskList 返回空表、taskDelete 恒抛 → 夸克不显示。
     */
    private List<String> fabActions() {
        List<String> actions = new ArrayList<>();
        String id = drive == null ? "" : drive.getId();
        boolean isQuark = DriveQuark.ID.equals(id);
        boolean isPikPak = DrivePikPak.ID.equals(id);
        boolean is115 = Drive115.ID.equals(id);
        if (!isQuark) actions.add("离线下载");
        actions.add("转存分享链接");
        if (isPikPak || is115) actions.add("新建文件夹");
        if (!isQuark) actions.add("离线任务");
        return actions;
    }

    private void showFabMenu() {
        List<String> actions = fabActions();
        if (actions.isEmpty()) return;
        String[] items = actions.toArray(new String[0]);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> {
                    String action = actions.get(which);
                    if ("离线下载".equals(action)) showOfflineDialog();
                    else if ("转存分享链接".equals(action)) showSaveShareDialog();
                    else if ("新建文件夹".equals(action)) showCreateFolderDialog();
                    else if ("离线任务".equals(action)) showTaskListDialog();
                })
                .create();
        dialog.show();
    }

    /** 离线任务弹窗的会话状态：已拼接的全部任务、已拉页数、第 1 页条数（满页判定基准）、是否还有下一页。 */
    private static class TaskListState {
        final List<CloudTask> tasks = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        int loadedPages;
        int pageSize;
        boolean hasMore;
        boolean loading;
    }

    private static String taskLabel(CloudTask t) {
        String st = t.getStatusText();
        return t.getName() + "  " + t.getPercent() + "%"
                + (TextUtils.isEmpty(st) ? "" : "  " + st);
    }

    /** 离线任务列表入口（FAB）：从第 1 页拉起，弹窗内可继续翻页拼接。不自动轮询。 */
    private void showTaskListDialog() {
        showTaskListDialog(1);
    }

    /**
     * 拉够 targetPages 页（或到尾页为止）再开弹窗。
     * 删任务后按已拉页数走这里重拉，已翻到的页不丢（旧口径只重拉第 1 页，第 2 页起的任务删完就看不见）。
     * 满页判定与文件列表同口径：第 1 页条数作页大小，某页取满＝可能还有下一页、不满＝已到尾页；
     * 恰好整页数倍数时靠下一次取到空页收尾，不丢任务。
     */
    private void showTaskListDialog(int targetPages) {
        Notify.show("正在加载离线任务…");
        new Thread(() -> {
            try {
                TaskListState st = new TaskListState();
                for (int p = 1; p <= Math.max(1, targetPages); p++) {
                    List<CloudTask> pageTasks = drive.taskList(p);
                    if (pageTasks == null || pageTasks.isEmpty()) break;
                    if (p == 1) st.pageSize = pageTasks.size();
                    st.tasks.addAll(pageTasks);
                    st.loadedPages = p;
                    st.hasMore = st.pageSize > 0 && pageTasks.size() >= st.pageSize;
                    if (!st.hasMore) break;
                }
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (st.tasks.isEmpty()) {
                        Notify.show("暂无离线任务");
                        return;
                    }
                    openTaskListDialog(st);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Notify.show("加载离线任务失败：" + msg(e)));
            }
        }).start();
    }

    private void openTaskListDialog(TaskListState st) {
        for (CloudTask t : st.tasks) st.labels.add(taskLabel(t));
        TaskAdapter adapter = new TaskAdapter(st);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("离线任务")
                .setAdapter(adapter, (d, which) -> confirmDeleteTask(st.tasks.get(which), st.loadedPages))
                .setNegativeButton("关闭", null);
        if (st.hasMore) builder.setNeutralButton("加载更多", null);
        AlertDialog dialog = builder.create();
        // 尺寸同步定版（封顶六成），不走 post 拽尺寸
        dialog.setOnShowListener(d -> {
            sizeDialogNow(dialog, 0.6f);
        });
        dialog.show();
        if (st.hasMore) {
            // 覆盖按钮的默认点击（默认会关弹窗）：改成不关弹窗、就地续拉下一页往后拼接。
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(v -> loadMoreTasks(dialog, adapter, st));
        }
    }

    private void loadMoreTasks(AlertDialog dialog, TaskAdapter adapter, TaskListState st) {
        if (st.loading || !st.hasMore) return;
        st.loading = true;
        int next = st.loadedPages + 1;
        new Thread(() -> {
            try {
                List<CloudTask> pageTasks = drive.taskList(next);
                runOnUiThread(() -> {
                    st.loading = false;
                    if (!dialog.isShowing()) return;
                    if (pageTasks == null || pageTasks.isEmpty()) {
                        st.hasMore = false;
                        markNoMoreTasks(dialog);
                        Notify.show("没有更多了");
                        return;
                    }
                    for (CloudTask t : pageTasks) {
                        st.tasks.add(t);
                        st.labels.add(taskLabel(t));
                    }
                    st.loadedPages = next;
                    st.hasMore = st.pageSize > 0 && pageTasks.size() >= st.pageSize;
                    adapter.notifyDataSetChanged();
                    if (!st.hasMore) markNoMoreTasks(dialog);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    st.loading = false;
                    Notify.show("加载更多失败：" + msg(e));
                });
            }
        }).start();
    }

    private void markNoMoreTasks(AlertDialog dialog) {
        android.widget.Button btn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (btn == null) return;
        btn.setText("没有更多了");
        btn.setEnabled(false);
    }

    /**
     * 离线任务条目：一行一条——文件名在左（单行省略），进度靠右（百分比＋状态）。
     * 原先是一整段 simple_list_item_1 纯文字（名＋百分比＋状态全挤一行）。
     * 行内字色按玻璃口径写（主 #F1F4F5／副 白.60，与 玻璃弹窗 同套）。
     */
    private class TaskAdapter extends BaseAdapter {

        private final TaskListState st;

        TaskAdapter(TaskListState st) {
            this.st = st;
        }

        @Override
        public int getCount() {
            return st.tasks.size();
        }

        @Override
        public CloudTask getItem(int position) {
            return st.tasks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView tvName;
            TextView tvProgress;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                tvName = (TextView) row.getChildAt(0);
                tvProgress = (TextView) row.getChildAt(1);
            } else {
                float density = getResources().getDisplayMetrics().density;
                row = new LinearLayout(CloudDriveActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int padH = (int) (16 * density);
                int padV = (int) (11 * density);
                row.setPadding(padH, padV, padH, padV);
                tvName = new TextView(CloudDriveActivity.this);
                tvName.setSingleLine(true);
                tvName.setEllipsize(TextUtils.TruncateAt.END);
                tvName.setTextColor(0xFFF1F4F5);
                tvName.setTextSize(14);
                row.addView(tvName, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                tvProgress = new TextView(CloudDriveActivity.this);
                tvProgress.setSingleLine(true);
                tvProgress.setTextColor(0x99FFFFFF);
                tvProgress.setTextSize(13);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginStart((int) (12 * density));
                row.addView(tvProgress, lp);
            }
            CloudTask t = st.tasks.get(position);
            tvName.setText(t.getName());
            String status = t.getStatusText();
            tvProgress.setText(t.getPercent() + "%" + (TextUtils.isEmpty(status) ? "" : " · " + status));
            return row;
        }
    }

    private void confirmDeleteTask(CloudTask task, int loadedPages) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("删除离线任务")
                .setMessage("确定删除「" + task.getName() + "」吗？")
                .setPositiveButton("删除", (d, w) -> {
                    new Thread(() -> {
                        try {
                            boolean ok = drive.taskDelete(Collections.singletonList(task.getHash()));
                            // A8＋F-14：删成功当场按已拉页数重拉刷新对话框列表，不要求关闭重进；删失败不刷新。
                            runOnUiThread(() -> {
                                Notify.show(ok ? "已删除离线任务" : "删除离线任务失败");
                                if (ok) showTaskListDialog(loadedPages);
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> Notify.show("删除离线任务失败：" + msg(e)));
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
    }

    private EditText makeInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(0xFF0C0E12);
        input.setHintTextColor(0xFF9AA3AF);
        input.setSingleLine(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        return input;
    }

    private String currentCidName() {
        return crumbs.get(crumbs.size() - 1).name;
    }

    /**
     * 「保存到」的所选目标（离线/转存弹窗共用）：默认＝上次选定的文件夹（按网盘记在偏好里），
     * 没有记录时才是当前所在文件夹；在选择器里点「就存这里」改选后写回并落盘。
     */
    private static class TargetFolder {
        String cid;
        String name;

        TargetFolder(String cid, String name) {
            this.cid = cid;
            this.name = name;
        }
    }

    /** 弹窗默认目标：先读本网盘记住的上次所选，没有记录才用当前所在文件夹。 */
    private TargetFolder loadTargetFolder() {
        String cid = Prefers.getString(PREF_SAVE_CID + drive.getId(), "");
        String name = Prefers.getString(PREF_SAVE_NAME + drive.getId(), "");
        // 只认名字判有没有记过：夸克/PikPak 根目录的 cid 本就是 ""，选根目录时 cid 存空串也是有效记忆
        if (!name.isEmpty()) return new TargetFolder(cid, name);
        return new TargetFolder(currentCid(), currentCidName());
    }

    /**
     * 记住的目标文件夹可能已被删/取不到：后台拿它试列一次，硬失败就静默回退当前目录
     * （清掉记忆、刷新「保存到」行），不报错不卡弹窗；用户在校验回来前已改选的，不冲掉新选择。
     */
    private void verifyTargetFolder(TargetFolder target, TextView row, AlertDialog dialog) {
        String cid = target.cid;
        new Thread(() -> {
            boolean ok;
            try {
                drive.listDir(cid, 0, 1);
                ok = true;
            } catch (Exception e) {
                ok = false;
            }
            if (ok) return;
            runOnUiThread(() -> {
                if (!dialog.isShowing() || !cid.equals(target.cid)) return;
                target.cid = currentCid();
                target.name = currentCidName();
                row.setText("保存到：" + target.name + " ›");
                Prefers.remove(PREF_SAVE_CID + drive.getId());
                Prefers.remove(PREF_SAVE_NAME + drive.getId());
            });
        }).start();
    }

    /** 文件夹选择器的会话状态：从根目录起的路径栈＋当前层的文件夹（只列文件夹，分页取全）。 */
    private static class FolderPickerState {
        final List<Crumb> path = new ArrayList<>();
        final List<CloudFile> dirs = new ArrayList<>();
        boolean loading;
    }

    /**
     * 「保存到」行：显示当前所选文件夹，点击打开文件夹选择器改选。
     * 字色先按玻璃白系写（主 #F1F4F5），与离线任务行同口径，
     * 弹窗壳 玻璃弹窗.apply 还会整树翻一遍，双保险不怕黑字压深面。
     */
    private TextView makeTargetRow(TargetFolder target) {
        TextView row = new TextView(this);
        row.setText("保存到：" + target.name + " ›");
        row.setTextColor(0xFFF1F4F5);
        row.setTextSize(14);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad, pad, pad);
        row.setOnClickListener(v -> showFolderPicker(target,
                () -> row.setText("保存到：" + target.name + " ›")));
        return row;
    }

    /**
     * 文件夹选择器：从上次所选处打开，逐层点进、顶部有「上一级」，底部「就存这里」定当前层。
     * 形态随文件页弹窗口径：玻璃弹窗 玻璃壳（深面白字）＋压小窗口（复用离线任务的压小法）。
     */
    private void showFolderPicker(TargetFolder target, Runnable onChanged) {
        showFolderPicker(target, onChanged, "就存这里", true);
    }

    /**
     * 文件夹选择器本体：positiveText 是定选钮文案；remember=true 时定选写
     * 「保存到」记忆（转存/离线口径），移动/复制传 false 不污染那份记忆。
     */
    private void showFolderPicker(TargetFolder target, Runnable onChanged,
                                  String positiveText, boolean remember) {
        FolderPickerState st = new FolderPickerState();
        st.path.add(new Crumb(rootCid, "根目录"));
        if (!target.cid.equals(rootCid)) st.path.add(new Crumb(target.cid, target.name));
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (8 * density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);
        TextView tvPath = new TextView(this);
        tvPath.setTextColor(0x99FFFFFF);
        tvPath.setTextSize(13);
        tvPath.setSingleLine(true);
        tvPath.setEllipsize(TextUtils.TruncateAt.START);
        layout.addView(tvPath);
        ListView list = new ListView(this);
        FolderAdapter adapter = new FolderAdapter(st);
        list.setAdapter(adapter);
        int listHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.42f);
        layout.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listHeight));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择文件夹")
                .setView(layout)
                .setPositiveButton(positiveText, (d, w) -> {
                    Crumb top = st.path.get(st.path.size() - 1);
                    target.cid = top.cid;
                    target.name = top.name;
                    // 落盘时点定在这里：点定选钮选定即记（按网盘分开），下次打开弹窗默认就是它；
                    // 选择器「打开在上次所选处」用的就是同一个 target，记忆只有这一份，不打架。
                    // 移动/复制是借用选择器（remember=false），不许动这份记忆。
                    if (remember) {
                        Prefers.put(PREF_SAVE_CID + drive.getId(), target.cid);
                        Prefers.put(PREF_SAVE_NAME + drive.getId(), target.name);
                    }
                    onChanged.run();
                })
                .setNegativeButton("取消", null)
                .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            int base = st.path.size() > 1 ? 1 : 0;
            if (base == 1 && position == 0) {
                st.path.remove(st.path.size() - 1);
                fetchPickerDirs(st, adapter, tvPath);
                return;
            }
            CloudFile dir = st.dirs.get(position - base);
            st.path.add(new Crumb(dir.getId(), dir.getName()));
            fetchPickerDirs(st, adapter, tvPath);
        });
        // 尺寸同步定版（封顶六成），不走 post 拽尺寸
        dialog.setOnShowListener(d -> {
            sizeDialogNow(dialog, 0.6f);
        });
        dialog.show();
        fetchPickerDirs(st, adapter, tvPath);
    }

    /** 进/切当前层：清掉旧层后从第一页起逐页增量拉（D6），不再整层拉完才显示。 */
    private void fetchPickerDirs(FolderPickerState st, FolderAdapter adapter, TextView tvPath) {
        String cid = st.path.get(st.path.size() - 1).cid;
        StringBuilder sb = new StringBuilder();
        for (Crumb c : st.path) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(c.name);
        }
        tvPath.setText(sb.toString());
        st.loading = true;
        st.dirs.clear();
        adapter.notifyDataSetChanged();
        fetchPickerPage(st, adapter, cid, 0);
    }

    /**
     * 选择器逐页增量（流畅度 D6）：每页回来先把该页的文件夹 append 进列表显示，再拉下一页——
     * 深目录开选择器不用先干等整层拉完。到底判定沿用现口径：不满一页（PAGE_LIMIT）即到头、
     * 安全阀 10000 条不变；切层作废改按页校验：每页回来先比 cid，层已变就整页丢弃、链也停，
     * 不把旧层文件夹串进新层（比原先「全拉完一次性校验」更严，不退步）。
     */
    private void fetchPickerPage(FolderPickerState st, FolderAdapter adapter, String cid, int offset) {
        new Thread(() -> {
            List<CloudFile> page = null;
            Exception error = null;
            try {
                page = drive.listDir(cid, offset, PAGE_LIMIT);
            } catch (Exception e) {
                error = e;
            }
            List<CloudFile> raw = page;
            Exception err = error;
            runOnUiThread(() -> {
                if (!cid.equals(st.path.get(st.path.size() - 1).cid)) return;
                if (err != null) {
                    st.loading = false;
                    Notify.show("加载文件夹失败：" + msg(err));
                    return;
                }
                if (raw != null) {
                    for (CloudFile f : raw) if (f.isDir()) st.dirs.add(f);
                    adapter.notifyDataSetChanged();
                }
                boolean more = raw != null && raw.size() >= PAGE_LIMIT && offset + PAGE_LIMIT <= 10000;
                if (more) {
                    fetchPickerPage(st, adapter, cid, offset + PAGE_LIMIT);
                } else {
                    st.loading = false;
                    if (st.dirs.isEmpty()) Notify.show("这个文件夹里没有子文件夹");
                }
            });
        }).start();
    }

    /** 选择器行：一行一个文件夹（名在左、› 在右）；子层时第一行是「‹ 上一级」。字色与离线任务行同套。 */
    private class FolderAdapter extends BaseAdapter {

        private final FolderPickerState st;

        FolderAdapter(FolderPickerState st) {
            this.st = st;
        }

        @Override
        public int getCount() {
            return (st.path.size() > 1 ? 1 : 0) + st.dirs.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView tvName;
            TextView tvArrow;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                tvName = (TextView) row.getChildAt(0);
                tvArrow = (TextView) row.getChildAt(1);
            } else {
                float density = getResources().getDisplayMetrics().density;
                row = new LinearLayout(CloudDriveActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int padH = (int) (16 * density);
                int padV = (int) (11 * density);
                row.setPadding(padH, padV, padH, padV);
                tvName = new TextView(CloudDriveActivity.this);
                tvName.setSingleLine(true);
                tvName.setEllipsize(TextUtils.TruncateAt.END);
                tvName.setTextSize(14);
                row.addView(tvName, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                tvArrow = new TextView(CloudDriveActivity.this);
                tvArrow.setSingleLine(true);
                tvArrow.setTextSize(14);
                row.addView(tvArrow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            int base = st.path.size() > 1 ? 1 : 0;
            if (base == 1 && position == 0) {
                tvName.setText("‹ 上一级");
                tvName.setTextColor(0x99FFFFFF);
                tvArrow.setText("");
            } else {
                tvName.setText(st.dirs.get(position - base).getName());
                tvName.setTextColor(0xFFF1F4F5);
                tvArrow.setText("›");
                tvArrow.setTextColor(0x99FFFFFF);
            }
            return row;
        }
    }

    private void showOfflineDialog() {
        TargetFolder target = loadTargetFolder();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        EditText input = makeInput("粘贴 magnet / ed2k / http 链接");
        layout.addView(input);
        TextView targetRow = makeTargetRow(target);
        layout.addView(targetRow);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("离线下载")
                .setView(layout)
                .setPositiveButton("开始", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (url.isEmpty()) {
                        Notify.show("链接不能为空");
                        return;
                    }
                    doOfflineDownload(url, target.cid);
                })
                .setNegativeButton("取消", null)
                .create();
        keepDialogStill(dialog);
        dialog.show();
        // 默认目标来自记忆且不是当前目录时，验一下它还在不在（删了就静默回退当前目录）
        if (!target.cid.equals(currentCid())) verifyTargetFolder(target, targetRow, dialog);
    }

    private void doOfflineDownload(String url, String cid) {
        new Thread(() -> {
            try {
                boolean ok = drive.offlineDownload(url, cid);
                runOnUiThread(() -> {
                    Notify.show(ok ? "离线任务已提交" : "提交失败");
                    refreshAfterDataChange();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Notify.show("提交失败：" + msg(e)));
            }
        }).start();
    }

    private void showSaveShareDialog() {
        TargetFolder target = loadTargetFolder();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        EditText link = makeInput("分享链接");
        EditText pwd = makeInput("提取码（可选）");
        layout.addView(link);
        layout.addView(pwd);
        TextView targetRow = makeTargetRow(target);
        layout.addView(targetRow);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("转存分享链接")
                .setView(layout)
                .setPositiveButton("转存", (d, w) -> {
                    String shareUrl = link.getText().toString().trim();
                    if (shareUrl.isEmpty()) {
                        Notify.show("分享链接不能为空");
                        return;
                    }
                    doSaveShare(shareUrl, pwd.getText().toString().trim(), target.cid);
                })
                .setNegativeButton("取消", null)
                .create();
        keepDialogStill(dialog);
        dialog.show();
        if (!target.cid.equals(currentCid())) verifyTargetFolder(target, targetRow, dialog);
    }

    private void doSaveShare(String shareUrl, String password, String cid) {
        new Thread(() -> {
            try {
                boolean ok = drive.saveShare(shareUrl, password, cid);
                runOnUiThread(() -> {
                    Notify.show(ok ? "转存成功" : "转存失败");
                    refreshAfterDataChange();
                });
            } catch (Exception e) {
                String m = msg(e);
                runOnUiThread(() -> Notify.show(TextUtils.isEmpty(m) ? "暂不支持" : m));
            }
        }).start();
    }

    private void showCreateFolderDialog() {
        EditText input = makeInput("文件夹名称");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("新建文件夹")
                .setView(input)
                .setPositiveButton("创建", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Notify.show("名称不能为空");
                        return;
                    }
                    doCreateFolder(name);
                })
                .setNegativeButton("取消", null)
                .create();
        keepDialogStill(dialog);
        dialog.show();
    }

    private void doCreateFolder(String name) {
        String cid = currentCid();
        new Thread(() -> {
            try {
                drive.createFolder(cid, name);
                runOnUiThread(() -> {
                    Notify.show("已创建");
                    refreshAfterDataChange();
                });
            } catch (Exception e) {
                String m = msg(e);
                runOnUiThread(() -> Notify.show(TextUtils.isEmpty(m) ? "暂不支持" : m));
            }
        }).start();
    }

    private static String msg(Exception e) {
        String m = e.getMessage();
        return m == null ? "" : m;
    }

    /** 115 的视频文件（截帧封面的适用面）：服务端缩略图缺失时本地截帧补。 */
    private boolean is115Video(CloudFile f) {
        return f != null && !f.isDir() && f.isVideo()
                && drive != null && Drive115.ID.equals(drive.getId())
                && !TextUtils.isEmpty(f.getPickCode());
    }

    /**
     * 行封面装配（文件列表与搜索结果共用）：
     * 三家有缩略图直链（thumbUrl）都走 Glide；115 视频没直链时走截帧封面
     * （Pan115Cover 本地缓存，截好后回调 refresh 刷行）；都没有就图标占位。
     * iconTint 是占位图标的着色（浅面行深色、深面弹窗行浅色）。
     */
    private void bindRowCover(ImageView iv, CloudFile file, int iconTint, Runnable refresh) {
        if (!TextUtils.isEmpty(file.getThumbUrl())) {
            iv.setImageTintList(null);
            Glide.with(iv)
                    .load(file.getThumbUrl())
                    .placeholder(file.isDir() ? R.drawable.ic_folder : R.drawable.ic_file)
                    .error(file.isDir() ? R.drawable.ic_folder : R.drawable.ic_file)
                    .into(iv);
            return;
        }
        if (is115Video(file)) {
            File cover = Pan115Cover.coverFile(this, file.getPickCode());
            if (cover != null) {
                iv.setImageTintList(null);
                Glide.with(iv)
                        .load(cover)
                        .placeholder(R.drawable.ic_file)
                        .error(R.drawable.ic_file)
                        .into(iv);
                return;
            }
            Glide.with(iv).clear(iv);
            iv.setImageTintList(ColorStateList.valueOf(iconTint));
            iv.setImageResource(R.drawable.ic_file);
            if (drive instanceof Drive115) {
                Pan115Cover.requestCover(this, (Drive115) drive, file.getPickCode(),
                        (pc, ok) -> runOnUiThread(() -> {
                            if (ok && !isFinishing() && refresh != null) refresh.run();
                        }));
            }
            return;
        }
        Glide.with(iv).clear(iv);
        iv.setImageTintList(ColorStateList.valueOf(iconTint));
        iv.setImageResource(file.isDir() ? R.drawable.ic_folder : R.drawable.ic_file);
    }

    private static class Crumb {
        final String cid;
        final String name;

        Crumb(String cid, String name) {
            this.cid = cid;
            this.name = name;
        }
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemCloudFileBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            CloudFile file = files.get(position);
            h.binding.tvName.setText(file.getName());
            h.binding.tvSize.setText(file.isDir() ? "文件夹" : file.getFormatSize());
            h.binding.ivArrow.setVisibility(file.isDir() ? View.VISIBLE : View.GONE);
            bindThumb(h, file);
            h.itemView.setOnClickListener(v -> {
                if (file.isDir()) openDir(file);
                else if (file.isVideo()) playFile(file);
                else if (file.isAudio()) Notify.show("暂不支持打开该文件类型");
                else showFileActions(file);
            });
            h.itemView.setOnLongClickListener(v -> {
                showFileActions(file);
                return true;
            });
        }

        private void bindThumb(Holder h, CloudFile file) {
            // 流畅度 D1：截帧截成后只刷那一行——按 pickcode 在回调当刻的列表里实时定位行号
            // （不许用 bind 时的旧 position，列表回收会刷错行）；定位不到（已切目录/行已不在）就丢弃，
            // 不许再整表 notifyDataSetChanged 在滑动期打全量重绑脉冲
            bindRowCover(h.binding.ivThumb, file, Color.parseColor("#0C0E12"), () -> {
                String pc = file.getPickCode();
                if (TextUtils.isEmpty(pc)) return;
                for (int i = 0; i < files.size(); i++) {
                    if (pc.equals(files.get(i).getPickCode())) {
                        notifyItemChanged(i);
                        return;
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemCloudFileBinding binding;

            Holder(ItemCloudFileBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
