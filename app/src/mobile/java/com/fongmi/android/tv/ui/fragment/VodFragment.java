package com.fongmi.android.tv.ui.fragment;
import com.fongmi.android.tv.utils.ToastUtil;

import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.databinding.FragmentVodBinding;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.StateEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.FilterListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.login.LoginManager;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.fongmi.android.tv.ui.activity.KeepActivity;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.adapter.TypeAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.ApkPushDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.dialog.ApkPushMethodDialog;
import com.fongmi.android.tv.ui.dialog.ApkPushUrlDialog;
import com.fongmi.android.tv.ui.dialog.FilterDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LinkDialog;
import com.fongmi.android.tv.ui.dialog.OneKeySyncDialog;
import com.fongmi.android.tv.ui.dialog.PushPlayDialog;
import com.fongmi.android.tv.ui.dialog.PushPlayUrlDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.dialog.TypeDialog;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.web.HomeWebController;
import com.fongmi.android.tv.web.WebHomeChrome;
import com.fongmi.android.tv.web.WebHomeChromeStartup;
import com.fongmi.android.tv.web.WebHomeViewport;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class VodFragment extends BaseFragment implements ConfigListener, SiteListener, FilterListener, TypeAdapter.OnClickListener, HomeWebController.Listener {

    private final ActivityResultLauncher<String[]> apkLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onApkSelected);

    private FragmentVodBinding mBinding;
    private SiteViewModel mViewModel;
    private HomeWebController mWeb;
    // 上次成功加载首页时的签名（cid|主页 key|主页脚本戳）；null＝还没成功加载过。
    // HOME 刷新（事件转发与 onResume 兜底）共用它把关：签名没变且内容已在，
    // 这一发就是重复触发，不许再整页重拉（详见 refreshHome 入口闸）。
    private String loadedHomeSig;
    private TypeAdapter mAdapter;
    private Result mResult;
    private String mChromeMode = WebHomeChrome.NORMAL;
    private int mHomeWebTopMargin;
    private Device pendingApkDevice;

    public static VodFragment newInstance() {
        return new VodFragment();
    }

    private FolderFragment getFragment() {
        if (mBinding == null || mBinding.pager.getAdapter() == null) return null;
        Object item = mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
        return item instanceof FolderFragment folder ? folder : null;
    }

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentVodBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        mBinding.title.setSelected(true);
        mHomeWebTopMargin = ((ViewGroup.MarginLayoutParams) mBinding.homeWeb.getLayoutParams()).topMargin;
        setRecyclerView();
        setWebView();
        setViewModel();
        showProgress();
        setTitle();
        updateToolbarMenu();
        // 首屏自拉兜底：配置已就绪（例如旋转重建、切回首页时 fragment 重建）直接拉首页，
        // 不用等配置事件；冷启动配置尚未就绪时不拉，等成功事件经 HomeActivity 转发而来。
        // 外部导入的本地脚本动态源不在配置 sites 里（只活在 VodConfig 的动态映射中），
        // 但它就是当前 home：导入落地新建 Fragment 时事件已在未注册窗口期丢光，
        // 这一发自拉就是它唯一的开跑路（定根账 py-import-open-diag-20261031 三·2）。
        boolean pulled = !VodConfig.get().getSites().isEmpty() || getCurrentScriptFile() != null;
        if (pulled) loadHome();
        // 自拉没命中时再看导入待开标记（fix168-pyimport2）：命中说明刚有导入把
        // 这个 home 设进来而刷新事件没送到——由消费方（本 Fragment）在露面时
        // 点补开，不再单靠事件或单条生命周期兜底。
        openPendingImportIfAny(pulled);
    }

    @Override
    protected void initEvent() {
        mBinding.top.setOnClickListener(this::onTop);
        mBinding.link.setOnClickListener(this::onLink);
        mBinding.title.setOnClickListener(this::onSite);
        mBinding.title.setOnLongClickListener(this::onSwitchConfig);
        mBinding.typeMore.setOnTouchListener(this::onTypeMoreTouch);
        mBinding.typeMore.setOnClickListener(this::onTypeMore);
        mBinding.filter.setOnClickListener(this::onFilter);
        mBinding.filter.setOnLongClickListener(this::onLink);
        mBinding.btnSearch.setOnClickListener(v -> SearchActivity.start(requireActivity()));
        mBinding.btnSearch.setOnLongClickListener(v -> {
            SearchActivity.start(requireActivity(), "", getHome().getKey());
            return true;
        });
        mBinding.btnFav.setOnClickListener(v -> KeepActivity.start(requireActivity()));
        mBinding.btnHistory.setOnClickListener(v -> HistoryActivity.start(requireActivity()));
        mBinding.btnMore.setOnClickListener(v -> onMoreActions());
        mBinding.appBar.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            int range = appBarLayout.getTotalScrollRange();
            if (range <= 0) return;
            float factor = Math.abs(verticalOffset * 1f / range);
            int padding = (int) (ResUtil.dp2px(12) * factor);
            if (mBinding.type.getPaddingTop() == padding) return;
            mBinding.type.setPadding(mBinding.type.getPaddingStart(), padding, mBinding.type.getPaddingEnd(), mBinding.type.getPaddingBottom());
        });
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mBinding.type.smoothScrollToPosition(position);
                mAdapter.setSelected(position);
                setFabVisible(position);
            }
        });
    }

    private void setRecyclerView() {
        mBinding.type.setHasFixedSize(true);
        mBinding.type.setItemAnimator(null);
        mBinding.type.setAdapter(mAdapter = new TypeAdapter(this));
        mBinding.pager.setAdapter(new PageAdapter(getChildFragmentManager()));
    }

    private void setWebView() {
        mWeb = new HomeWebController(requireActivity(), mBinding.homeWeb, this);
        syncWebHomeChrome();
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(getViewLifecycleOwner(), this::setAdapter);
    }

    private void setAdapter(Result result) {
        if (mWeb != null && mWeb.isVisible()) return;
        mAdapter.addAll(mResult = result);
        notifyPagerAdapter();
        setFabVisible(0);
        mBinding.typeMore.setVisibility(View.GONE);
        mBinding.type.post(this::updateTypeMoreVisible);
        updateToolbarMenu();
        hideProgress();
        showContent();
    }

    private void updateTypeMoreVisible() {
        if (mBinding.type.getWidth() == 0 || mBinding.typeBar.getWidth() == 0) {
            mBinding.type.post(this::updateTypeMoreVisible);
            return;
        }
        int typeWidth = mBinding.typeBar.getWidth() - mBinding.typeBar.getPaddingStart() - mBinding.typeBar.getPaddingEnd();
        boolean visible = mAdapter.getItemCount() > 0 && mBinding.type.computeHorizontalScrollRange() > typeWidth;
        mBinding.typeMore.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setFabVisible(int position) {
        if (isNativeChromeHidden()) {
            mBinding.top.setVisibility(View.GONE);
            mBinding.link.setVisibility(View.GONE);
            mBinding.filter.setVisibility(View.GONE);
            return;
        }
        if (mAdapter.getItemCount() == 0) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.link.setVisibility(View.VISIBLE);
            mBinding.filter.setVisibility(View.GONE);
        } else if (!mAdapter.get(position).getFilters().isEmpty()) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.link.setVisibility(View.GONE);
            mBinding.filter.show();
        } else if (position == 0 || mAdapter.get(position).getFilters().isEmpty()) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.filter.setVisibility(View.GONE);
            mBinding.link.show();
        }
    }

    private void setTitle() {
        // Title capsule: "星辰 · 源名" or just "星辰"
        String sourceName = "";
        try {
            if (getHome() != null && !TextUtils.isEmpty(getHome().getName())) {
                sourceName = getHome().getName();
            } else if (getConfig() != null && !TextUtils.isEmpty(getConfig().getName())) {
                sourceName = getConfig().getName();
            }
        } catch (Exception e) { e.printStackTrace(); }
        String title = "星辰";
        if (!TextUtils.isEmpty(sourceName) && !"星辰".equals(sourceName)) {
            title = "星辰 · " + sourceName;
        }
        mBinding.title.setText(title);
    }

    private void onTop(View view) {
        FolderFragment fragment = getFragment();
        if (fragment != null) fragment.scrollToTop();
        mBinding.top.setVisibility(View.INVISIBLE);
        if (mBinding.filter.getVisibility() == View.INVISIBLE) mBinding.filter.show();
        else if (mBinding.link.getVisibility() == View.INVISIBLE) mBinding.link.show();
    }

    private boolean onLink(View view) {
        LinkDialog.show(this);
        return true;
    }

    private void onTypeMore(View view) {
        if (mAdapter.getItemCount() > 0) TypeDialog.create().items(mAdapter.getItems()).show(this);
    }

    private boolean onTypeMoreTouch(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            view.animate().cancel();
            view.animate().scaleX(1.06f).scaleY(1.06f).setDuration(80).start();
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            view.animate().cancel();
            view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        }
        return false;
    }


        private void onSite(View view) {
        try {
            java.util.List<com.fongmi.android.tv.bean.Site> sites = com.fongmi.android.tv.api.config.VodConfig.get().getSites();
            String currentKey = getHome() != null ? getHome().getKey() : "";
            com.fongmi.android.tv.ui.dialog.SourceSelectDialog dialog = new com.fongmi.android.tv.ui.dialog.SourceSelectDialog(requireActivity(), sites, currentKey);
            dialog.setOnSelectListener(this::setSite);
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to native dialog
            SiteDialog.create().show(this);
        }
    }

    /**
     * 长按标题：弹出线路切换弹窗（点播接口管理）。
     */
    private boolean onSwitchConfig(View view) {
        try {
            com.fongmi.android.tv.ui.dialog.ConfigHistoryDialog dialog =
                    com.fongmi.android.tv.ui.dialog.ConfigHistoryDialog.create(0);
            dialog.show(getParentFragmentManager(), "config_switch");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private boolean reloadConfig(View view) {
        VodConfig.get().clear().config(getConfig()).load(new Callback() {
            @Override
            public void start() {
                showProgress();
                hideContent();
            }

            @Override
            public void success() {
                hideProgress();
                showContent();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
                hideProgress();
                showContent();
            }
        });
        return true;
    }

    private void onFilter(View view) {
        if (mAdapter.getItemCount() > 0) FilterDialog.create().filter(mAdapter.get(mBinding.pager.getCurrentItem()).getFilters()).show(this);
    }

    private void onMoreActions() {
        try {
            com.fongmi.android.tv.ui.dialog.SourceScriptsDialog dialog = new com.fongmi.android.tv.ui.dialog.SourceScriptsDialog(requireActivity(), (file, isPy) -> {
                try { onScriptSelected(file, isPy); } catch (Exception e) { e.printStackTrace(); }
            });
            dialog.setCurrentFile(getCurrentScriptFile());
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private java.io.File getCurrentScriptFile() {
        try {
            com.fongmi.android.tv.bean.Site home = com.fongmi.android.tv.api.config.VodConfig.get().getHome();
            if (home != null && home.getApi() != null && !home.getApi().startsWith("http")) {
                java.io.File f = new java.io.File(home.getApi());
                if (f.exists()) return f;
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.refresh) {
            if (mWeb != null && mWeb.isVisible()) mWeb.reload();
            else homeContent(true);
        } else if (item.getItemId() == R.id.keep) KeepActivity.start(requireActivity());
        else if (item.getItemId() == R.id.search) SearchActivity.start(requireActivity());
        else if (item.getItemId() == R.id.history) HistoryActivity.start(requireActivity());
        else if (item.getItemId() == R.id.sync) OneKeySyncDialog.create().show(requireActivity());
        else if (item.getItemId() == R.id.web_home_fullscreen) onWebHomeFullscreen();
        else if (item.getItemId() == R.id.more_actions) {
            try {
                com.fongmi.android.tv.ui.dialog.SourceScriptsDialog dialog = new com.fongmi.android.tv.ui.dialog.SourceScriptsDialog(requireActivity(), (file, isPy) -> {
                    try { onScriptSelected(file, isPy); } catch (Exception e) { e.printStackTrace(); }
                });
                dialog.setCurrentFile(getCurrentScriptFile());
                dialog.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else return false;
        return true;
    }

    private void onScriptSelected(java.io.File file, boolean isPy) {
        try {
            ToastUtil.show(requireActivity(), "已选择: " + file.getName(), android.widget.Toast.LENGTH_SHORT);
            String name = file.getName();
            String baseName = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
            String prefix = isPy ? "py_" : "js_";
            com.fongmi.android.tv.bean.Site site = new com.fongmi.android.tv.bean.Site();
            site.setKey(prefix + baseName);
            site.setName(baseName);
            site.setApi(file.getAbsolutePath());
            site.setType(3);
            String extKey = prefix + baseName;
            String savedExt = com.fongmi.android.tv.utils.PyExtConfig.load(requireActivity(), extKey);
            site.setExt(savedExt == null || savedExt.isEmpty() ? "{}" : savedExt);
            site.setJar("");
            com.fongmi.android.tv.api.config.VodConfig.get().setHome(site);
            homeContent(true);
        } catch (Exception e) {
            e.printStackTrace();
            ToastUtil.show(requireActivity(), "加载失败: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT);
        }
    }

    private void onApkSelected(Uri uri) {
        Device device = pendingApkDevice;
        pendingApkDevice = null;
        if (uri != null && device != null) ApkPushDialog.create(device, uri).show(requireActivity());
    }

    private void onApkDeviceSelected(Device device) {
        App.post(() -> {
            if (!isAdded()) return;
            ApkPushMethodDialog.create(device).listener(new ApkPushMethodDialog.Listener() {
                @Override
                public void onLocal(Device device) {
                    selectLocalApk(device);
                }

                @Override
                public void onLink(Device device) {
                    ApkPushUrlDialog.create(device).show(requireActivity());
                }
            }).show(requireActivity());
        });
    }

    private void selectLocalApk(Device device) {
        pendingApkDevice = device;
        App.post(() -> {
            if (isAdded()) apkLauncher.launch(new String[]{"application/vnd.android.package-archive", "application/octet-stream"});
        });
    }

    private void onPushPlayDeviceSelected(Device device) {
        App.post(() -> {
            if (isAdded()) PushPlayUrlDialog.create(device).show(requireActivity());
        });
    }

    private void onWebHomeFullscreen() {
        if (!Setting.isWebHomeFullscreen()) return;
        if (mWeb == null || !mWeb.isVisible()) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("mode", WebHomeChrome.EDGE);
        setChrome(payload);
    }

    private void updateToolbarMenu() {
        // Fullscreen menu item removed in new design (no toolbar menu)
    }


    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
    }

    private void hideContent() {
        mBinding.type.setVisibility(View.INVISIBLE);
        mBinding.typeMore.setVisibility(View.INVISIBLE);
        mBinding.pager.setVisibility(View.INVISIBLE);
    }

    private void showContent() {
        mBinding.type.setVisibility(View.VISIBLE);
        updateTypeMoreVisible();
        mBinding.pager.setVisibility(View.VISIBLE);
    }

    private void homeContent() {
        homeContent(false);
    }

    private void homeContent(boolean forceRefresh) {
        requestNormalChrome();
        showProgress();
        mBinding.homeWeb.setVisibility(View.GONE);
        updateToolbarMenu();
        clearPagerTypes();
        mBinding.pager.setAdapter(new PageAdapter(getChildFragmentManager()));
        setFabVisible(0);
        mViewModel.homeContent(forceRefresh);
        // 首页预热：前台首屏加载已发出后，后台提前跑遍各源首页（每进程一次）。
        com.fongmi.android.tv.api.HomePrewarm.kick();
    }

    private void loadHome() {
        Site home = getHome();
        WebHomeChromeStartup.remember(getConfig(), home);
        setTitle();
        if (mWeb != null && mWeb.load(home)) {
            clearPagerTypes();
            hideProgress();
            hideNativeContent();
        } else {
            showNativeContent();
            homeContent();
        }
        markHomeLoaded();
    }

    private void clearPagerTypes() {
        mAdapter.clear();
        mBinding.typeMore.setVisibility(View.GONE);
        notifyPagerAdapter();
    }

    private void notifyPagerAdapter() {
        PagerAdapter adapter = mBinding.pager.getAdapter();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    public Result getResult() {
        return mResult == null ? new Result() : mResult;
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        // 不再留空方法，但这里只刷新标题、不拉数据：拉数据的唯一驱动是 HomeActivity
        // 把 VOD 事件转成的 RefreshEvent.home()（见 onRefreshEvent 的 HOME 分支），
        // 这里再调 loadHome 就会和转发各拉一次（双拉）。X-02 起事件只在成功时发，
        // 所以标题刷新与转发加载都是成功态，不会把失败重拉带进来。
        if (event.type() == ConfigEvent.Type.VOD || event.type() == ConfigEvent.Type.COMMON) setTitle();
    }

    // 当前首页签名：配置 id＋主页 key＋主页脚本戳。脚本戳走 PyLoader.scriptStamp
    // 同口径（本地脚本删了变 #missing、改了变 mtime/length，http 源恒为 api 本身），
    // 保证「换配置／切源／删改当前源脚本」任一发生签名必变、闸必放行；
    // 纯切后台回前台这类没动过配置的场景签名一字不变。
    private String homeSignature() {
        Site home = getHome();
        String stamp = "";
        try {
            stamp = com.fongmi.android.tv.api.loader.PyLoader.scriptStamp(home.getApi());
        } catch (Throwable ignored) {
        }
        return com.fongmi.android.tv.api.config.VodConfig.getCid() + "|" + home.getKey() + "|" + stamp;
    }

    private boolean hasHomeContent() {
        return mResult != null || (mWeb != null && mWeb.isVisible());
    }

    // HOME 刷新的实际动作（事件与 onResume 兜底共用一份，避免两处口径漂移）
    private void refreshHome() {
        // 入口闸：内容已在且签名与上次成功加载一致，这一发 HOME 刷新不重拉。
        // 发送点有十几处（配置事件转发、脚本失效、登录刷新、窗口形态变化、远程
        // 动作等），此前任一发到达就整页重拉——切后台回前台时只要有一发迟到/
        // 重复投递，用户看到的就是接口又加载一遍、滚动与分类全清。真变了的
        //（换配置 cid 变、切源 key 变、当前源脚本被删改戳变）签名不同，照常
        // 放行重拉；显式下拉刷新走 reloadHome 的强制路，不经这里、不受闸影响。
        if (loadedHomeSig != null && hasHomeContent() && loadedHomeSig.equals(homeSignature())) return;
        if (mWeb != null && mWeb.isVisible()) {
            Site home = getHome();
            WebHomeChromeStartup.remember(getConfig(), home);
            requestNormalChrome();
            setTitle();
            if (!mWeb.load(home, true)) {
                showNativeContent();
                homeContent();
            }
        } else {
            loadHome();
        }
        markHomeLoaded();
    }

    private void markHomeLoaded() {
        loadedHomeSig = homeSignature();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                refreshHome();
                break;
            case SIZE:
                if (mWeb != null && mWeb.isVisible()) return;
                homeContent();
                break;
            case CATEGORY:
                if (mWeb != null && mWeb.isVisible()) return;
                if (mBinding.pager.getAdapter() == null || mBinding.pager.getAdapter().getCount() == 0) return;
                FolderFragment current = getFragment();
                if (current != null) current.onRefresh();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStateEvent(StateEvent event) {
        switch (event.type()) {
            case EMPTY:
                hideProgress();
                break;
            case PROGRESS:
                showProgress();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        ReceiveDialog.create().event(event).show(this);
    }

    @Override
    public void setConfig(Config config) {
        VodConfig.load(config, new Callback() {
            @Override
            public void start() {
                showProgress();
                hideContent();
                setTitle();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
                showContent();
            }
        });
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void onItemClick(int position, Class item) {
        mBinding.pager.setCurrentItem(position);
        mAdapter.setSelected(position);
    }

    @Override
    public boolean onItemLongClick(int position, Class item) {
        // 页签长按不再标短剧（标记只收敛到插件页的可见小标，存量已标数据与生效判定不动）；
        // 仅快手直播页签长按弹快手登录，其余页签长按无动作。
        if (item != null && isKuaishouLiveTab(item)) onKuaishouTabLongClick();
        return true;
    }

    /**
     * 快手直播页签认定（simple_live 源分类结构：type_id=kuaishou、type_name=快手直播）。
     * id 或名含 kuaishou / 快手才认，认不准不挂。
     */
    private boolean isKuaishouLiveTab(Class item) {
        String typeId = item.getTypeId();
        if (typeId != null && typeId.trim().toLowerCase(java.util.Locale.ROOT).contains("kuaishou")) return true;
        String typeName = item.getTypeName();
        return typeName != null && (typeName.contains("快手") || typeName.toLowerCase(java.util.Locale.ROOT).contains("kuaishou"));
    }

    /** 未登录直接弹壳子统一快手登录弹窗；已登录给小弹窗（已登录＋退出登录）。 */
    private void onKuaishouTabLongClick() {
        if (!isAdded()) return;
        if (LoginManager.isKuaishouLoggedIn()) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireActivity(), com.fongmi.android.tv.R.style.ThemeOverlay_WebHTV_LightDialog)
                    .setTitle("快手")
                    .setMessage("已登录")
                    .setPositiveButton("退出登录", (d, w) -> {
                        LoginManager.logoutKuaishou();
                        Notify.show("已退出快手登录");
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            LoginManager.get().loginKuaishou(requireActivity(), new LoginManager.LoginCallback() {
                @Override
                public void onSuccess(String providerId, String cookie) {
                    Notify.show("快手登录成功");
                }

                @Override
                public void onError(String msg) {
                    Notify.show("登录失败：" + msg);
                }

                @Override
                public void onCancel() {
                }
            });
        }
    }

    @Override
    public void setFilter(String key, Value value) {
        FolderFragment fragment = getFragment();
        if (fragment != null) fragment.setFilter(key, value);
    }

    @Override
    public boolean canBack() {
        if (mWeb != null && mWeb.handleBack()) return false;
        if (isNativeChromeHidden()) {
            requestNormalChrome();
            return false;
        }
        if (mBinding.pager.getAdapter() == null || mBinding.pager.getAdapter().getCount() == 0) return true;
        FolderFragment fragment = getFragment();
        if (fragment == null || !fragment.canBack()) return true;
        fragment.goBack();
        return false;
    }

    @Override
    public void onDestroyView() {
        requestNormalChrome();
        if (mWeb != null) mWeb.destroy();
        EventBus.getDefault().unregister(this);
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mWeb != null) mWeb.onResume();
        // 导入待开标记先于签名兜底消费（fix168-pyimport2）：标记是导入方留下的
        // 确定性凭据，指向的正是当前 home 就清标记＋补开；签名兜底管其余情形。
        openPendingImportIfAny(false);
        // 兜底（深挖 L-14）：首页刷新只有 RefreshEvent 单通路，事件在未注册窗口期
        // 丢了就永远陈旧。回前台时比对首页签名（cid＋主页 key＋主页脚本戳，与
        // refreshHome 入口闸同一份口径），变了才按 HOME 口径补刷一次；没变不动作。
        if (loadedHomeSig != null) {
            if (!loadedHomeSig.equals(homeSignature())) refreshHome();
        } else if (!getHome().getKey().isEmpty() && !getHome().getApi().isEmpty()) {
            // 还没成功加载过：新 Fragment 若连 initView 自拉也没命中（如外部导入
            // 落地、事件在未注册窗口期丢光），home 本身有效就补刷一发——refreshHome
            // 入口闸仍管「已加载且签名一致」的重复拉，补成后 markHomeLoaded 赋值，
            // 后续回前台自动回到上面的签名比对支，不会发发都补。
            refreshHome();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            // hide/show 切回可见时既不重走 initView、onResume 也不重发——待开标记
            // 在这里也要消费，不然停在别的签上导入、切回首页仍不开（fix168-pyimport2）
            openPendingImportIfAny(false);
            try {
                FolderFragment f = getFragment();
                if (f != null) f.checkCoverChanged();
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    /**
     * 消费导入待开标记：导入方（ImportActivity）在 setHome 落实后登记了目标 key；
     * 标记指向当前 home 时清标记，首页尚未在为它加载就按 HOME 口径补刷一发。
     * 标记不指向当前 home（用户已切去别的源）时不消费、留待它再次成为 home。
     * homeAlreadyLoading＝调用点本次已为当前 home 发起加载（如 initView 自拉命中），
     * 此时只销标记不重复补刷（双发加载 SiteApi 会掐上一批，避开无谓 churn）。
     */
    private void openPendingImportIfAny(boolean homeAlreadyLoading) {
        try {
            String key = VodConfig.get().pendingImportedHome();
            if (key == null || !key.equals(getHome().getKey())) return;
            VodConfig.get().clearImportedHome();
            if (!homeAlreadyLoading) refreshHome();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onPause() {
        if (mWeb != null) mWeb.onPause();
        super.onPause();
    }

    @Override
    public void onWebLoading() {
        showProgress();
    }

    @Override
    public void onWebReady() {
        hideProgress();
    }

    @Override
    public void onWebError() {
        requestNormalChrome();
        showNativeContent();
        homeContent();
    }

    @Override
    public void setToolbar(boolean visible) {
        if (!Setting.isWebHomeFullscreen()) {
            applyWebHomeChrome(WebHomeChrome.NORMAL);
            return;
        }
        HomeActivity activity = homeActivity();
        if (activity != null) activity.setWebHomeLegacyToolbar(visible);
        else applyWebHomeChrome(visible ? WebHomeChrome.NORMAL : WebHomeChrome.IMMERSIVE);
    }

    @Override
    public void applyDefaultChrome(Site site) {
        if (!Setting.isWebHomeFullscreen()) {
            applyWebHomeChrome(WebHomeChrome.NORMAL);
            return;
        }
        HomeActivity activity = homeActivity();
        if (activity != null) activity.applyWebHomeDefaultChrome(site);
    }

    @Override
    public void setChrome(JsonObject payload) {
        if (!Setting.isWebHomeFullscreen()) {
            applyWebHomeChrome(WebHomeChrome.NORMAL);
            return;
        }
        HomeActivity activity = homeActivity();
        if (activity != null) activity.setWebHomeChrome(payload);
    }

    @Override
    public void restoreChrome() {
        if (!Setting.isWebHomeFullscreen()) {
            applyWebHomeChrome(WebHomeChrome.NORMAL);
            return;
        }
        HomeActivity activity = homeActivity();
        if (activity != null) activity.restoreWebHomeChrome();
    }

    @Override
    public WebHomeViewport getViewport() {
        HomeActivity activity = homeActivity();
        return activity == null ? WebHomeViewport.EMPTY : activity.getWebHomeViewport();
    }

    @Override
    public void openVod() {
        HomeActivity activity = homeActivity();
        if (activity != null) activity.openVod();
    }

    @Override
    public void openSetting() {
        if (getActivity() instanceof HomeActivity) ((HomeActivity) getActivity()).change(1);
    }

    private void hideNativeContent() {
        mBinding.appBar.setExpanded(true, false);
        boolean hidden = isNativeChromeHidden();
        mBinding.appBar.setVisibility(hidden ? View.GONE : View.VISIBLE);
        setHomeWebTopMargin(hidden ? 0 : mHomeWebTopMargin);
        mBinding.type.setVisibility(View.GONE);
        mBinding.typeMore.setVisibility(View.GONE);
        mBinding.pager.setVisibility(View.GONE);
        mBinding.filter.setVisibility(View.GONE);
        mBinding.link.setVisibility(View.GONE);
        mBinding.top.setVisibility(View.GONE);
        updateToolbarMenu();
    }

    private void showNativeContent() {
        requestNormalChrome();
        mBinding.type.setVisibility(View.VISIBLE);
        updateTypeMoreVisible();
        mBinding.pager.setVisibility(View.VISIBLE);
        mBinding.homeWeb.setVisibility(View.GONE);
        updateToolbarMenu();
    }

    public void applyWebHomeChrome(String mode) {
        mChromeMode = WebHomeChrome.normalize(mode, WebHomeChrome.NORMAL);
        boolean hidden = isNativeChromeHidden();
        mBinding.appBar.setExpanded(true, false);
        mBinding.appBar.setVisibility(hidden ? View.GONE : View.VISIBLE);
        setHomeWebTopMargin(hidden ? 0 : mHomeWebTopMargin);
        updateToolbarMenu();
        if (hidden) {
            mBinding.type.setVisibility(View.GONE);
            mBinding.typeMore.setVisibility(View.GONE);
            mBinding.pager.setVisibility(View.GONE);
            mBinding.filter.setVisibility(View.GONE);
            mBinding.link.setVisibility(View.GONE);
            mBinding.top.setVisibility(View.GONE);
        }
    }

    public void applyWebHomeViewport(WebHomeViewport viewport) {
        if (mWeb != null) mWeb.setViewport(viewport);
    }

    public void openVodHome() {
        homeContent();
    }

    /**
     * 「首页」页签的刷新统一走这里：与首屏同一套取数（本碎片的 ViewModel），
     * 页签不再用自己的 ViewModel 再全量拉一遍——同一页面两套首页取数既叠负载、
     * 结果还互不更新（页签拉完新数据，页签栏/筛选仍停在首屏那一发）。
     */
    public void reloadHome() {
        homeContent(true);
    }

    private void setHomeWebTopMargin(int margin) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mBinding.homeWeb.getLayoutParams();
        if (params.topMargin == margin) return;
        params.topMargin = margin;
        mBinding.homeWeb.setLayoutParams(params);
    }

    private void requestNormalChrome() {
        HomeActivity activity = homeActivity();
        if (activity != null) activity.setWebHomeLegacyToolbar(true);
        else applyWebHomeChrome(WebHomeChrome.NORMAL);
    }

    private boolean isNativeChromeHidden() {
        return WebHomeChrome.hidesNativeChrome(mChromeMode);
    }

    private void syncWebHomeChrome() {
        HomeActivity activity = homeActivity();
        if (activity == null) return;
        applyWebHomeChrome(activity.getWebHomeChromeMode());
        applyWebHomeViewport(activity.getWebHomeViewport());
    }

    private HomeActivity homeActivity() {
        return getActivity() instanceof HomeActivity ? (HomeActivity) getActivity() : null;
    }


    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Class type = mAdapter.get(position);
            return FolderFragment.newInstance(getHome().getKey(), type, 4);
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }
    }
}