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
import androidx.media3.common.C;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.SubtitleSetting;
import com.fongmi.android.tv.subtitle.ShieldBypass;
import com.fongmi.android.tv.subtitle.ShooterProvider;
import com.fongmi.android.tv.subtitle.SubtitleInfo;
import com.fongmi.android.tv.subtitle.SubtitleManager;
import com.fongmi.android.tv.ui.custom.CustomRecyclerView;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 字幕搜索：输入片名 -> 四字幕源搜索 -> 选结果下载并加载到播放器。
 * 深色弹窗。
 */
public final class SubtitleSearchDialog extends DialogFragment {

    private final ResultAdapter adapter;
    private TextInputEditText keywordView;
    private MaterialTextView searchView;
    private LinearLayout searchRow;
    private CustomRecyclerView recycler;
    private CircularProgressIndicator progress;
    private TextView empty;
    private PlayerManager player;
    private CharSequence keyword;
    private boolean restoreParent;
    private List<SubtitleInfo> results = new ArrayList<>();

    public static SubtitleSearchDialog create() {
        return new SubtitleSearchDialog();
    }

    public SubtitleSearchDialog() {
        this.adapter = new ResultAdapter(this::onItemClick);
    }

    public SubtitleSearchDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public SubtitleSearchDialog restoreParent(boolean restoreParent) {
        this.restoreParent = restoreParent;
        return this;
    }

    public SubtitleSearchDialog keyword(CharSequence keyword) {
        this.keyword = keyword;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof SubtitleSearchDialog) return;
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
        TrackDialog.create().player(player).type(C.TRACK_TYPE_TEXT).show(activity);
    }

    private LinearLayout createContentView() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(24));
        root.setBackground(round(Color.parseColor("#F226282C"), 10, Color.TRANSPARENT));
        MaterialTextView title = new MaterialTextView(requireContext());
        title.setText(getString(R.string.play_track_text) + getString(R.string.play_search));
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));
        root.addView(createSearchRow(), createTopParams(16));
        root.addView(createResultFrame(), createTopParams(16));
        return root;
    }

    private LinearLayout createSearchRow() {
        searchView = new MaterialTextView(requireContext());
        searchView.setText(R.string.play_search);
        searchView.setTextColor(Color.WHITE);
        searchView.setTextSize(14);
        searchView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        searchView.setGravity(Gravity.CENTER);
        searchView.setClickable(true);
        searchView.setFocusable(true);
        searchView.setBackground(round(Color.parseColor("#CC1A73E8"), 7, Color.TRANSPARENT));
        keywordView.setId(View.generateViewId());

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
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
        if (position < 0 || position >= results.size()) return;
        SubtitleInfo info = results.get(position);
        showProgress();
        SubtitleManager.downloadAndApply(info, player, file -> {
            if (file != null) {
                restoreParent = false;
                Notify.show("字幕已加载");
                dismissAllowingStateLoss();
            } else {
                hideProgress(false);
                Notify.show("下载失败，换一个试试");
            }
        });
    }

    private void showProgress() {
        adapter.clear();
        empty.setVisibility(GONE);
        empty.setOnClickListener(null);
        recycler.setVisibility(GONE);
        progress.setVisibility(VISIBLE);
    }

    private void hideProgress(boolean emptyResult) {
        progress.setVisibility(GONE);
        recycler.setVisibility(emptyResult ? GONE : VISIBLE);
        empty.setVisibility(emptyResult ? VISIBLE : GONE);
        if (emptyResult) {
            empty.setText(R.string.error_empty);
            empty.setOnClickListener(null);
        }
    }

    private void search() {
        showProgress();
        Util.hideKeyboard(keywordView);
        SubtitleManager.search(getKeywordText(), items -> {
            results = items == null ? new ArrayList<>() : items;
            adapter.setItems(results);
            if (results.isEmpty()) showSourceStatus();
            else hideProgress(false);
        });
    }

    /** 空态：列出全部字幕源状态，点击可处理（过验证 / 填 Token） */
    private void showSourceStatus() {
        progress.setVisibility(GONE);
        recycler.setVisibility(GONE);
        empty.setVisibility(VISIBLE);
        List<String> blocked = ShieldBypass.getBlocked();
        StringBuilder sb = new StringBuilder();
        String[] ids = {"subhd", "shooter", "zimuku", "opensubtitles"};
        for (String id : ids) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(ShieldBypass.providerName(id)).append("：").append(statusOf(id, blocked));
        }
        empty.setText(sb.toString());
        empty.setOnClickListener(v -> {
            if (!blocked.isEmpty()) {
                openShieldVerify(blocked.get(0));
            } else if (ShooterProvider.needsToken()) {
                showAssrtTokenInput();
            }
        });
    }

    private String statusOf(String id, List<String> blocked) {
        if (blocked.contains(id)) return "需要过验证（点击验证）";
        if (!SubtitleSetting.isSrcEnabled(id)) return "已关闭";
        if ("shooter".equals(id) && ShooterProvider.needsToken()) return "未填写 Token（点击填写）";
        if ("opensubtitles".equals(id) && TextUtils.isEmpty(SubtitleSetting.getOpenSubtitlesKey())) return "未填写 Key";
        return "无结果";
    }

    /** 射手网 Token 输入：毛玻璃弹窗 + 黄色保存按钮，保存后自动重试 */
    private void showAssrtTokenInput() {
        FragmentActivity activity = getActivity();
        if (activity == null || activity.isFinishing()) return;
        android.view.View view = android.view.LayoutInflater.from(activity).inflate(R.layout.dialog_assrt_token, null);
        android.widget.EditText input = view.findViewById(R.id.et_token);
        input.setText(SubtitleSetting.getAssrtToken());
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(activity).setView(view).create();
        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            SubtitleSetting.putAssrtToken(input.getText().toString());
            Notify.show("已保存");
            dialog.dismiss();
            search();
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            window.setAttributes(params);
        }
    }

    private void openShieldVerify(String providerId) {
        FragmentActivity activity = getActivity();
        if (activity == null || activity.isFinishing()) return;
        ShieldWebViewDialog.create().provider(providerId).listener(this::search).show(activity);
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
        private final List<SubtitleInfo> items = new ArrayList<>();

        private interface OnClickListener {
            void onItemClick(int position);
        }

        private ResultAdapter(OnClickListener listener) {
            this.listener = listener;
        }

        private void clear() {
            int size = items.size();
            items.clear();
            if (size > 0) notifyItemRangeRemoved(0, size);
        }

        private void setItems(List<SubtitleInfo> values) {
            clear();
            if (values == null) return;
            items.addAll(values);
            notifyItemRangeInserted(0, items.size());
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialTextView view = new MaterialTextView(parent.getContext());
            float density = parent.getContext().getResources().getDisplayMetrics().density;
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(48 * density));
            params.setMargins(0, 0, 0, Math.round(10 * density));
            view.setLayoutParams(params);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
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

            private void bind(SubtitleInfo info) {
                text.setText(info.getName() + " [" + info.getLang() + "]");
            }

            @Override
            public void onClick(View view) {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                listener.onItemClick(position);
            }

            private Drawable rowBackground() {
                float density = text.getContext().getResources().getDisplayMetrics().density;
                GradientDrawable normal = new GradientDrawable();
                normal.setColor(Color.parseColor("#18FFFFFF"));
                normal.setCornerRadius(Math.round(7 * density));
                normal.setStroke(Math.round(1 * density), Color.parseColor("#24FFFFFF"));
                return normal;
            }
        }
    }
}
