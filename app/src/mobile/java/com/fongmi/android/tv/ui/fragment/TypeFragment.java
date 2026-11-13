package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.ui.ActionResult;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentTypeBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.utils.CoverRatioCache;
import com.fongmi.android.tv.utils.ShortDramaHelper;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.VodAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.ui.custom.VodPreloader;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.HashMap;

public class TypeFragment extends BaseFragment implements CustomScroller.Callback, VodAdapter.OnClickListener, SwipeRefreshLayout.OnRefreshListener, ActionResult.Host {

    private HashMap<String, String> mExtends;
    private long mCoverGen = -1;
    private FragmentTypeBinding mBinding;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private VodAdapter mAdapter;

    public static TypeFragment newInstance(String key, String typeId, Style style, HashMap<String, String> extend, boolean folder, int y) {
        Bundle args = new Bundle();
        args.putInt("y", y);
        args.putString("key", key);
        args.putString("typeId", typeId);
        args.putBoolean("folder", folder);
        args.putParcelable("style", style);
        args.putSerializable("extend", extend);
        TypeFragment fragment = new TypeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKey() {
        return getArguments().getString("key");
    }

    private String getTypeId() {
        return getArguments().getString("typeId");
    }

    private String getTypeName() {
        try {
            com.fongmi.android.tv.bean.Class type = getParent().getType();
            return type == null ? "" : type.getTypeName();
        } catch (Exception e) {
            return "";
        }
    }

    private Style getStyle() {
        if (isFolder()) return Style.list();
        Style siteStyle = getSite().getStyle(getArguments().getParcelable("style"));
        return applyUserCoverStyle(siteStyle);
    }

    private Style applyUserCoverStyle(Style siteStyle) {
        try {
            android.content.SharedPreferences sp = requireActivity().getSharedPreferences("xingchen", android.content.Context.MODE_PRIVATE);
            String orient = sp.getString("cover_orient", "portrait");
            String ratioStr = sp.getString("cover_ratio", "2:3");
            float ratio = parseRatio(ratioStr);
            if ("landscape".equals(orient)) {
                // Landscape: ratio > 1.0, use 16:9 or inverse of portrait ratio
                float landRatio = ratio > 0 && ratio < 1.0f ? 1.0f / ratio : 1.33f;
                return new Style("rect", landRatio);
            } else {
                // Portrait: use user's ratio, never OVAL
                return new Style("rect", ratio > 0 ? ratio : 0.75f);
            }
        } catch (Exception e) {
            return siteStyle;
        }
    }

    private float parseRatio(String ratioStr) {
        try {
            if (ratioStr == null) return 0.75f;
            String[] parts = ratioStr.split(":");
            if (parts.length == 2) {
                float w = Float.parseFloat(parts[0].trim());
                float h = Float.parseFloat(parts[1].trim());
                if (h > 0) return w / h;
            }
        } catch (Exception e) { /* ignore */ }
        return 0.75f;
    }

    private HashMap<String, String> getExtend() {
        return (HashMap<String, String>) getArguments().getSerializable("extend");
    }

    private int getY() {
        return getArguments().getInt("y");
    }

    private boolean isFolder() {
        return getArguments().getBoolean("folder");
    }

    private boolean isHome() {
        return "home".equals(getTypeId());
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private FolderFragment getParent() {
        return (FolderFragment) getParentFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentTypeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mBinding.progressLayout.showProgress();
        mScroller = new CustomScroller(this);
        mExtends = getExtend();
        mCoverGen = readCoverGen();
        setRecyclerView();
        setViewModel();
        getVideo();
    }

    @Override
    protected void initEvent() {
        mBinding.swipeLayout.setOnRefreshListener(this);
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.addOnScrollListener(new VodPreloader());
    }

    private void setRecyclerView() {
        mBinding.recycler.setTranslationY(-ResUtil.dp2px(getY()));
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemViewCacheSize(8);
        setStyle(getStyle());
    }

    private void setStyle(Style style) {
        mBinding.recycler.setAdapter(mAdapter = new VodAdapter(this, style, Product.getSpec(requireActivity(), style)));
        mAdapter.setSiteKey(getKey());
        mBinding.recycler.setLayoutManager(style.isList() ? new LinearLayoutManager(requireActivity()) : new GridLayoutManager(getContext(), Product.getColumn(requireActivity(), style)));
        if (mBinding.recycler.getLayoutManager() instanceof GridLayoutManager) {
            ((GridLayoutManager) mBinding.recycler.getLayoutManager()).setInitialPrefetchItemCount(6);
        }
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(getViewLifecycleOwner(), this::setAdapter);
        mViewModel.getAction().observe(getViewLifecycleOwner(), result -> ActionResult.consume(getActivity(), result, this));
    }

    private void getVideo() {
        mScroller.reset();
        mAdapter.clear(() -> {
            if (!mBinding.swipeLayout.isRefreshing()) mBinding.progressLayout.showProgress();
            if (isHome()) {
                // 独立托管（父不是 VodFragment）时 getResult() 返 null：无数据可设就不设，
                // 不许把 null 喂给 setAdapter（它会直解 getList() 崩）
                Result result = getParent().getResult();
                if (result != null) setAdapter(result);
            }
            else getVideo(getTypeId(), "1");
        });
    }

    private void getVideo(String typeId, String page) {
        mViewModel.categoryContent(getKey(), typeId, page, true, mExtends);
    }

    private void setAdapter(Result result) {
        boolean first = mScroller.first();
        int size = result.getList().size();
        mBinding.progressLayout.showContent(first, size);
        mBinding.swipeLayout.setRefreshing(false);
        mScroller.endLoading(result);
        if (size > 0) addVideo(result);
    }

    private void addVideo(Result result) {
        Style style = result.getVod().getStyle(getStyle());
        if (!style.equals(mAdapter.getStyle())) setStyle(style);
        mAdapter.addAll(result.getList(), this::checkMore);
    }

    // 动作回的整页（协议 E2：原样呈现）：整页替换本页条目。与分类页分工——
    // 分类走 addVideo 续页追加，动作页是一次成页的替换。
    private void setActionPage(Result result) {
        mBinding.progressLayout.showContent(true, result.getList().size());
        mBinding.swipeLayout.setRefreshing(false);
        mScroller.reset();
        mScroller.endLoading(result);
        Style style = result.getVod().getStyle(getStyle());
        if (!style.equals(mAdapter.getStyle())) setStyle(style);
        mAdapter.setItems(result.getList());
    }

    @Override
    public void onActionPage(Result result) {
        setActionPage(result);
    }

    @Override
    public void onActionRefresh() {
        onRefresh();
    }

    @Override
    public void onActionSubmit(String action) {
        mViewModel.action(getKey(), action);
    }

    private void checkMore() {
        mBinding.recycler.post(() -> {
            if (isHome()) return;
            mScroller.checkMore(mBinding.recycler);
        });
    }

    public void scrollToTop() {
        mBinding.recycler.smoothScrollToPosition(0);
    }

    public void setFilter(String key, Value value) {
        if (value.isSelected()) mExtends.put(key, value.getV());
        else mExtends.remove(key);
        onRefresh();
    }

    @Override
    public void onRefresh() {
        // 「首页」页签不自己拉首页：回列表页那一发统一刷新（页签栏与列表同源更新），
        // 本页随列表页重建pager被替换，刷新转圈随旧页一同结束。
        if (isHome()) getParent().refreshHome();
        else getVideo();
    }

    @Override
    public boolean onLoadMore(String page) {
        if (isHome()) return false;
        getVideo(getTypeId(), page);
        return true;
    }

    @Override
    public void onItemClick(Vod item, android.view.View coverView) {
        if (item.isAction()) {
            if (!ActionResult.handleCard(requireActivity(), item, json -> mViewModel.action(getKey(), json)))
                mViewModel.action(getKey(), item.getAction());
        } else if (item.isFolder()) {
            getParent().openFolder(item.getId(), mExtends);
        } else {
            if (getSite().isIndex()) SearchActivity.start(requireActivity(), item.getName());
            else if (ShortDramaHelper.shouldDirectPlay(getKey(), getTypeId(), getTypeName())) VideoActivity.startShortDrama(requireActivity(), getKey(), item.getId(), item.getName(), item.getPic());
            else if (ShortDramaHelper.isLiveShortDramaCover(coverView, item.getPic())) VideoActivity.startShortDrama(requireActivity(), getKey(), item.getId(), item.getName(), item.getPic());
            else VideoActivity.start(requireActivity(), getKey(), item.getId(), item.getName(), item.getPic(), isFolder() ? item.getName() : null);
        }
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction() || item.isFolder()) return false;
        SearchActivity.start(requireActivity(), item.getName());
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        checkCoverChanged();
        // 旧代 jar 动作条目在自有 UI 里改了状态（域名／cookie 等）：回列表时消费 dirty 标刷新一次。
        if (SiteApi.consumeStubDirty(getKey())) onRefresh();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) checkCoverChanged();
    }

    private long readCoverGen() {
        try {
            return requireActivity().getSharedPreferences("xingchen", android.content.Context.MODE_PRIVATE).getLong("cover_gen", 0);
        } catch (Exception e) {
            return mCoverGen;
        }
    }

    public void checkCoverChanged() {
        try {
            // 按代次比对而非消费一次性标志：每个碎片各自记住已应用的代次，
            // 先 resume 的碎片不再把变更吞掉，其余页签也能各自刷新到新样式。
            long gen = readCoverGen();
            if (gen != mCoverGen) {
                mCoverGen = gen;
                refreshCoverStyle();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void refreshCoverStyle() {
        try {
            if (mAdapter == null || mAdapter.getItemCount() == 0) {
                // 数据为空时也先纠正列数，否则刷新后列数仍是旧的
                try {
                    Style ns = getStyle();
                    if (!ns.isList()) {
                        int nc = Product.getColumn(requireActivity(), ns);
                        androidx.recyclerview.widget.RecyclerView.LayoutManager l = mBinding.recycler.getLayoutManager();
                        int cc = (l instanceof GridLayoutManager) ? ((GridLayoutManager) l).getSpanCount() : -1;
                        if (cc != nc) mBinding.recycler.setLayoutManager(new GridLayoutManager(getContext(), nc));
                    }
                } catch (Exception ignored) { }
                onRefresh();
                return;
            }
            Style newStyle = getStyle();
            int newColumn = com.fongmi.android.tv.Product.getColumn(requireActivity(), newStyle);
            androidx.recyclerview.widget.RecyclerView.LayoutManager lm = mBinding.recycler.getLayoutManager();
            int curColumn = (lm instanceof androidx.recyclerview.widget.GridLayoutManager)
                    ? ((androidx.recyclerview.widget.GridLayoutManager) lm).getSpanCount() : -1;
            boolean styleChanged = !newStyle.equals(mAdapter.getStyle());
            boolean columnChanged = curColumn != newColumn;
            if (styleChanged || columnChanged) {
                java.util.List<com.fongmi.android.tv.bean.Vod> data = new java.util.ArrayList<>(mAdapter.getItems());
                mAdapter = new VodAdapter(this, newStyle, com.fongmi.android.tv.Product.getSpec(requireActivity(), newStyle));
                mAdapter.setSiteKey(getKey());
                mAdapter.setItems(data);
                mBinding.recycler.setAdapter(mAdapter);
                if (newStyle.isList()) {
                    mBinding.recycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireActivity()));
                } else {
                    mBinding.recycler.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(getContext(), newColumn));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            onRefresh();
        }
    }

    private String getUserOrient() {
        try {
            return requireActivity().getSharedPreferences("xingchen", android.content.Context.MODE_PRIVATE)
                .getString("cover_orient", "portrait");
        } catch (Exception e) {
            return "portrait";
        }
    }


}