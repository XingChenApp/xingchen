package com.fongmi.android.tv.ui.dialog;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanDanPlayApi;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.ui.custom.CustomRecyclerView;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 弹弹play 弹幕搜索：输入片名 -> 选番剧 -> 选分集 -> 下载弹幕并加载到播放器。
 * 深色弹窗。
 */
public final class DanDanPlaySearchDialog extends DialogFragment {

    private final ResultAdapter adapter;
    private TextInputEditText keywordView;
    private MaterialTextView searchView;
    private MaterialTextView backView;
    private MaterialTextView titleView;
    private LinearLayout searchRow;
    private CustomRecyclerView recycler;
    private CircularProgressIndicator progress;
    private TextView empty;
    private PlayerManager player;
    private CharSequence keyword;
    private boolean restoreParent;
    private DanDanPlayApi.Anime currentAnime;
    private List<DanDanPlayApi.Anime> animeList = new ArrayList<>();
    private boolean inEpisodes;
    private Runnable retryAction;

    public static DanDanPlaySearchDialog create() {
        return new DanDanPlaySearchDialog();
    }

    public DanDanPlaySearchDialog() {
        this.adapter = new ResultAdapter(this::onItemClick);
    }

    public DanDanPlaySearchDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public DanDanPlaySearchDialog restoreParent(boolean restoreParent) {
        this.restoreParent = restoreParent;
        return this;
    }

    public DanDanPlaySearchDialog keyword(CharSequence keyword) {
        this.keyword = keyword;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof DanDanPlaySearchDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        keywordView = createKeywordView();
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(createContentView());
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(d -> {
            bindEvents();
            setKeyword(keyword == null ? getTitle() : keyword);
            Util.showKeyboard(keywordView);
        });
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Util.isFullscreen(getActivity())) Util.hideSystemUI(window);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        boolean landscape = metrics.widthPixels > metrics.heightPixels;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.copyFrom(window.getAttributes());
        params.width = landscape ? Math.min((int) (metrics.widthPixels * 0.76f), dp(920)) : Math.min(metrics.widthPixels - dp(32), dp(560));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        FragmentActivity activity = getActivity();
        if (!restoreParent || activity == null || activity.isFinishing()) return;
        DanmakuDialog.create().player(player).show(activity);
    }

    private LinearLayout createContentView() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(24));
        root.setBackground(round(Color.parseColor("#F226282C"), 10, Color.TRANSPARENT));
        titleView = createTitleView();
        root.addView(titleView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));
        root.addView(createSearchRow(), createTopParams(16));
        root.addView(createResultFrame(), createTopParams(16));
        return root;
    }

    private MaterialTextView createTitleView() {
        MaterialTextView title = new MaterialTextView(requireContext());
        title.setText(getString(R.string.danmaku) + getString(R.string.play_search) + " · 弹弹play");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        return title;
    }

    private LinearLayout createSearchRow() {
        searchView = createSearchButton();
        keywordView.setId(View.generateViewId());
        searchView.setId(View.generateViewId());

        backView = new MaterialTextView(requireContext());
        backView.setText("‹ 返回");
        backView.setTextColor(Color.parseColor("#E6FFFFFF"));
        backView.setTextSize(14);
        backView.setGravity(Gravity.CENTER_VERTICAL);
        backView.setPadding(dp(4), dp(8), dp(12), dp(8));
        backView.setVisibility(GONE);
        backView.setOnClickListener(v -> showAnimeList());

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(backView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        LinearLayout.LayoutParams keywordParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        row.addView(keywordView, keywordParams);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(92), dp(44));
        actionParams.setMarginStart(dp(10));
        row.addView(searchView, actionParams);
        return searchRow = row;
    }

    private TextInputEditText createKeywordView() {
        TextInputEditText edit = new TextInputEditText(requireContext());
        edit.setSingleLine(true);
        edit.setMaxLines(1);
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        edit.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(Color.parseColor("#99FFFFFF"));
        edit.setTextSize(14);
        edit.setHint(R.string.search_keyword);
        edit.setPadding(dp(14), 0, dp(14), 0);
        edit.setBackground(round(Color.parseColor("#263A3C41"), 9, Color.parseColor("#33FFFFFF")));
        return edit;
    }

    private MaterialTextView createSearchButton() {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(R.string.play_search);
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(round(Color.parseColor("#CC1A73E8"), 7, Color.TRANSPARENT));
        return view;
    }

    private FrameLayout createResultFrame() {
        FrameLayout frame = new FrameLayout(requireContext());

        progress = new CircularProgressIndicator(requireContext());
        progress.setIndeterminate(true);
        progress.setIndicatorColor(Color.parseColor("#E6FFFFFF"));
        progress.setIndicatorSize(dp(32));
        progress.setTrackThickness(dp(2));
        progress.setVisibility(GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        progressParams.setMargins(0, dp(24), 0, dp(24));
        frame.addView(progress, progressParams);

        empty = new TextView(requireContext());
        empty.setGravity(Gravity.CENTER);
        empty.setText(R.string.error_empty);
        empty.setTextColor(Color.parseColor("#99FFFFFF"));
        empty.setTextSize(14);
        empty.setVisibility(GONE);
        empty.setPadding(0, dp(12), 0, dp(12));
        empty.setOnClickListener(v -> {
            if (retryAction != null) retryAction.run();
        });
        frame.addView(empty, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(72), Gravity.CENTER));

        recycler = new CustomRecyclerView(requireContext());
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setItemAnimator(null);
        recycler.setHasFixedSize(false);
        recycler.setMaxHeight(dp(260));
        recycler.setAdapter(adapter);
        recycler.setVisibility(GONE);
        frame.addView(recycler, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return frame;
    }

    private LinearLayout.LayoutParams createTopParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        return params;
    }

    private void bindEvents() {
        keywordView.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH && !getKeywordText().isEmpty()) search();
            return true;
        });
        searchView.setOnClickListener(view -> {
            if (!getKeywordText().isEmpty()) search();
        });
    }

    private void onItemClick(int position) {
        if (inEpisodes) {
            DanDanPlayApi.Episode ep = episodeAt(position);
            if (ep != null) downloadEpisode(ep);
        } else {
            DanDanPlayApi.Anime anime = animeAt(position);
            if (anime != null) loadEpisodes(anime);
        }
    }

    private DanDanPlayApi.Anime animeAt(int position) {
        return position >= 0 && position < animeList.size() ? animeList.get(position) : null;
    }

    private DanDanPlayApi.Episode episodeAt(int position) {
        List<DanDanPlayApi.Episode> eps = adapter.episodes;
        return position >= 0 && position < eps.size() ? eps.get(position) : null;
    }

    private void showProgress() {
        adapter.clear();
        empty.setVisibility(GONE);
        recycler.setVisibility(GONE);
        progress.setVisibility(VISIBLE);
    }

    private void hideProgress(boolean emptyResult) {
        progress.setVisibility(GONE);
        recycler.setVisibility(emptyResult ? GONE : VISIBLE);
        empty.setVisibility(emptyResult ? VISIBLE : GONE);
    }

    private void search() {
        inEpisodes = false;
        currentAnime = null;
        backView.setVisibility(GONE);
        titleView.setText(getString(R.string.danmaku) + getString(R.string.play_search) + " · 弹弹play");
        showProgress();
        Util.hideKeyboard(keywordView);
        retryAction = this::search;
        empty.setText(R.string.error_empty);
        DanDanPlayApi.searchAnime(getKeywordText(), (items, networkError) -> {
            animeList = items;
            adapter.setAnimes(items);
            if (networkError) empty.setText("网络连接失败，点击重试");
            hideProgress(items.isEmpty());
        });
    }

    private void showAnimeList() {
        inEpisodes = false;
        currentAnime = null;
        backView.setVisibility(GONE);
        titleView.setText(getString(R.string.danmaku) + getString(R.string.play_search) + " · 弹弹play");
        adapter.setAnimes(animeList);
        hideProgress(animeList.isEmpty());
    }

    private void loadEpisodes(DanDanPlayApi.Anime anime) {
        currentAnime = anime;
        inEpisodes = true;
        showProgress();
        retryAction = () -> loadEpisodes(anime);
        empty.setText(R.string.error_empty);
        DanDanPlayApi.getEpisodes(anime.animeId, (items, networkError) -> {
            backView.setVisibility(VISIBLE);
            titleView.setText(anime.title + " · 选分集");
            adapter.setEpisodes(items);
            if (networkError) empty.setText("网络连接失败，点击重试");
            hideProgress(items.isEmpty());
        });
    }

    private void downloadEpisode(DanDanPlayApi.Episode ep) {
        showProgress();
        DanDanPlayApi.downloadDanmaku(ep.episodeId, ep.title, file -> onDownloaded(file, ep));
    }

    private void onDownloaded(File file, DanDanPlayApi.Episode ep) {
        if (file != null && file.exists()) {
            restoreParent = false;
            if (player != null) {
                Danmaku item = Danmaku.from(file.getAbsolutePath());
                item.setName(TextUtils.isEmpty(ep.title) ? file.getName() : ep.title);
                player.setDanmaku(item);
            }
            Notify.show("弹幕已加载");
            dismissAllowingStateLoss();
        } else {
            hideProgress(true);
            empty.setText("弹幕下载失败，换一集试试");
            empty.setVisibility(VISIBLE);
            Notify.show("弹幕下载失败");
        }
    }

    private void setKeyword(CharSequence text) {
        CharSequence value = text == null ? "" : text;
        keywordView.setText(value);
        keywordView.setSelection(value.length());
    }

    private String getKeywordText() {
        return keywordView == null || keywordView.getText() == null ? "" : keywordView.getText().toString().trim();
    }

    private CharSequence getTitle() {
        return player == null || player.getMetadata() == null || player.getMetadata().title == null ? "" : player.getMetadata().title;
    }

    private Drawable round(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }

    private static final class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.ViewHolder> {

        private final OnClickListener listener;
        private final List<String> names = new ArrayList<>();
        private final List<DanDanPlayApi.Episode> episodes = new ArrayList<>();

        private interface OnClickListener {
            void onItemClick(int position);
        }

        private ResultAdapter(OnClickListener listener) {
            this.listener = listener;
        }

        private void clear() {
            int size = names.size();
            names.clear();
            episodes.clear();
            if (size > 0) notifyItemRangeRemoved(0, size);
        }

        private void setAnimes(List<DanDanPlayApi.Anime> items) {
            clear();
            if (items == null) return;
            for (DanDanPlayApi.Anime a : items) names.add(a.title);
            notifyItemRangeInserted(0, names.size());
        }

        private void setEpisodes(List<DanDanPlayApi.Episode> items) {
            clear();
            if (items == null) return;
            for (DanDanPlayApi.Episode e : items) {
                names.add(e.title);
                episodes.add(e);
            }
            notifyItemRangeInserted(0, names.size());
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialTextView view = new MaterialTextView(parent.getContext());
            int d = Math.round(48 * parent.getContext().getResources().getDisplayMetrics().density);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d);
            params.setMargins(0, 0, 0, Math.round(10 * parent.getContext().getResources().getDisplayMetrics().density));
            view.setLayoutParams(params);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(names.get(position));
        }

        @Override
        public int getItemCount() {
            return names.size();
        }

        private final class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

            private final MaterialTextView text;

            private ViewHolder(@NonNull MaterialTextView text) {
                super(text);
                this.text = text;
                float density = text.getContext().getResources().getDisplayMetrics().density;
                this.text.setGravity(Gravity.CENTER_VERTICAL);
                this.text.setPadding(Math.round(12 * density), 0, Math.round(12 * density), 0);
                this.text.setSingleLine(true);
                this.text.setEllipsize(TextUtils.TruncateAt.END);
                this.text.setTextSize(14);
                this.text.setTextColor(Color.parseColor("#E6FFFFFF"));
                this.text.setBackground(rowBackground());
                this.text.setOnClickListener(this);
            }

            private void bind(String name) {
                text.setText(name);
            }

            @Override
            public void onClick(View view) {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                listener.onItemClick(position);
            }

            private Drawable rowBackground() {
                GradientDrawable normal = new GradientDrawable();
                normal.setColor(Color.parseColor("#18FFFFFF"));
                normal.setCornerRadius(Math.round(7 * text.getContext().getResources().getDisplayMetrics().density));
                normal.setStroke(Math.round(1 * text.getContext().getResources().getDisplayMetrics().density), Color.parseColor("#24FFFFFF"));
                return normal;
            }
        }
    }
}
