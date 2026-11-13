package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.hk.HkHttpParser;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkLazyRuleParser;
import com.fongmi.android.tv.api.hk.HkPageParser;
import com.fongmi.android.tv.api.hk.HkParserKit;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.ToastUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 环42 danmu# 调起面：8.83 DanmuSearchPopup（ui/video，BottomPopupView＋
 * pop_danmu_search.xml）的逐支转写。咱家承载形态从 XPopup 换为本 App
 * 既有 DialogFragment 编程式建面（与 DanmakuSearchDialog 同形），结构、
 * 文案、时序、失败/空态尾全照官：
 *
 * <ul>
 *   <li>枚举（onCreate 后台任务 $5）：全部已装规则中 pages 含 "\"danmu#"
 *   且子页 path 以 "danmu#" 开头者组 DTO{ruleName=子页 name, findRule=子页
 *   rule, rule=父规则}，以子页 name 为键入 HashMap（同名后者覆盖）。</li>
 *   <li>枚举完 UI 支（$4）：map 为空 → 「温馨提示／检测到您还没有带弹幕
 *   搜索规则的小程序，是否跳转云盘君简小程序来检索弹幕？」确认面，确认走
 *   startRulePage(hiker://page/loadDm?rule=云盘君.简&amp;title=＋fromKeyWord)；
 *   非空 → 源条 initData 后按 danmuS 选中并 btnClick（写 danmuS＋loadData，
 *   打开即自动搜）。</li>
 *   <li>danmuS 偏好：PreferenceMgr 默认文件 "share_data"、键 "danmuS"、
 *   值＝子页 name；三读一写（$2 唯一写点）全照官，默认值＝键集首键
 *   （HashMap 任意序），陈值（源被删/改名）时 loadData 静默无反应——
 *   官 bug 照录，不许回落默认（夹具 D8）。</li>
 *   <li>求值错（$14）：evalJSForObj 引擎层吞 Exception 回 null → 静默
 *   清空列表，无 toast；只有结果加工期抛错才 toast「出错：」＋msg，
 *   isPageUrl 内层支抛错只 toast msg 原文（shortCenter）。</li>
 *   <li>排序（$10/$11）：sortMode 0 且非悬浮宿主时 sortView 出两项附着
 *   列表（因当前顺序不对而倒序／为查看最新剧集而倒序）；选 0 → sortMode
 *   ＝1；选 1 → sortMode＝2＋toast「已倒序显示，当视频切换下一集时，
 *   弹幕绑定前一个结果」（dig2 账记 toast 归「因当前顺序不对」支，
 *   与官 smali 不符，以官包为准：toast 在 index＝1 支）。其余情形点
 *   sortView 直接 sortMode＝0＋反转回正序。三支末尾都调 listener.startLoad。</li>
 * </ul>
 *
 * SegmentControlView 本体未移植：源条以等宽（80dp）等高（30dp）页签
 * 行承接，btnClick 语义（写 danmuS＋loadData）一字不变（视图载体差记名）。
 * 悬浮播放宿主（FloatVideoController）咱家无此面，fromFloatVideo 恒
 * false（字段与 setter 照官保留）。
 */
public final class HkDanmuSearchDialog extends DialogFragment {

    private static final String TAG = "HkDanmuSearch";

    /** 官 DanmuSearchPopup$DanmuSearchListener 原样（宿主实现见 HkDanmuBinder）。 */
    public interface DanmuSearchListener {
        void startLoad();

        void click(int pos, List<HkDanmuResult> results, String url, int sortMode);
    }

    /** 官 DanmuSearchRuleDTO 原样：三字段。 */
    static final class DanmuSearchRuleDTO {
        String ruleName;
        String findRule;
        HkRule rule;
    }

    // loadData 定源三态（官 loadData 源行 312-341 的循环语义）
    static final int LOAD_START = 0;
    static final int LOAD_CLEAR = 1;
    static final int LOAD_SILENT = 2;

    private final ResultAdapter adapter;
    private final List<HkDanmuResult> results;
    private final Map<String, DanmuSearchRuleDTO> rulesMap;
    private EditText editText;
    private TextView editTextCover;
    private LinearLayout tabsLayout;
    private TextView loadingTextView;
    private DanmuSearchListener listener;
    private String fromKeyWord;
    private boolean fromFloatVideo;
    private int sortMode;
    private Activity hostActivity;
    private volatile boolean dismissed = true;

    public static HkDanmuSearchDialog create() {
        return new HkDanmuSearchDialog();
    }

    public HkDanmuSearchDialog() {
        this.adapter = new ResultAdapter();
        this.results = new ArrayList<>();
        this.rulesMap = new HashMap<>();
    }

    public HkDanmuSearchDialog setDanmuSearchListener(DanmuSearchListener listener) {
        this.listener = listener;
        return this;
    }

    public HkDanmuSearchDialog setFromKeyWord(String fromKeyWord) {
        this.fromKeyWord = fromKeyWord;
        return this;
    }

    public HkDanmuSearchDialog setFromFloatVideo(boolean fromFloatVideo) {
        this.fromFloatVideo = fromFloatVideo;
        return this;
    }

    /** 宿主 Activity（切集续绑时 fetchDanmu 在弹窗已关后仍要跑，上下文取此）。 */
    public HkDanmuSearchDialog setHostActivity(Activity activity) {
        this.hostActivity = activity;
        return this;
    }

    public void show(FragmentActivity activity) {
        // 官每次新建调起面、无去重；咱宿主持单例复用，去重只许收窄到
        // 本实例已 added 且对话框正在显示——管理器里残留的本类 Fragment
        // （已关未摘等）不许把入口永久堵死。
        if (isAdded() && getDialog() != null && getDialog().isShowing()) return;
        show(activity.getSupportFragmentManager(), null);
    }

    // ---------------------------------------------------------------
    // 建面（对应官 onCreate；视图结构照 pop_danmu_search.xml）
    // ---------------------------------------------------------------

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        if (dialog.getWindow() != null) dialog.getWindow().setWindowAnimations(R.style.HkDialogFade);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(createContentView());
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(d -> {
            dismissed = false;
            onPopupCreate();
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Util.isFullscreen(getActivity())) Util.hideSystemUI(window);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        boolean landscape = metrics.widthPixels > metrics.heightPixels;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.copyFrom(window.getAttributes());
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.width = landscape || Util.isLeanback() ? Math.min((int) (metrics.widthPixels * 0.76f), dp(920)) : Math.min(metrics.widthPixels - dp(32), dp(560));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        dismissed = true;
    }

    private LinearLayout createContentView() {
        Context ctx = requireContext();
        LinearLayout bg = new LinearLayout(ctx);
        bg.setOrientation(LinearLayout.VERTICAL);
        bg.setBackground(round(0xF226282C, 10));
        // 头：edit_text_cover／edit_text＋「搜索」钮（官 header）
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(5), dp(10), 0);
        editTextCover = new TextView(ctx);
        editTextCover.setHint("请输入片名");
        editTextCover.setTextColor(Color.WHITE);
        editTextCover.setHintTextColor(0xFF9AA0A6);
        editTextCover.setGravity(Gravity.CENTER_VERTICAL);
        editTextCover.setSingleLine(true);
        editTextCover.setBackground(bottomBorder());
        editText = new EditText(ctx);
        editText.setHint("请输入片名");
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(0xFF9AA0A6);
        editText.setGravity(Gravity.CENTER_VERTICAL);
        editText.setSingleLine(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setVisibility(View.GONE);
        LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        header.addView(editTextCover, fieldLp);
        header.addView(editText, fieldLp);
        TextView search = new TextView(ctx);
        search.setText("搜索");
        search.setTextColor(0xFF00A2FF);
        search.setGravity(Gravity.CENTER);
        search.setPadding(dp(10), 0, dp(10), 0);
        header.addView(search, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)));
        bg.addView(header);
        // 源条（官 SegmentControlView 承载位）
        HorizontalScrollView scroller = new HorizontalScrollView(ctx);
        scroller.setHorizontalScrollBarEnabled(false);
        tabsLayout = new LinearLayout(ctx);
        tabsLayout.setOrientation(LinearLayout.HORIZONTAL);
        tabsLayout.setGravity(Gravity.CENTER);
        scroller.addView(tabsLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollerLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollerLp.gravity = Gravity.CENTER_HORIZONTAL;
        scrollerLp.topMargin = dp(10);
        scrollerLp.bottomMargin = dp(10);
        bg.addView(scroller, scrollerLp);
        // 列表区：RecyclerView＋loadingTextView＋sortView（官 FrameLayout）
        FrameLayout frame = new FrameLayout(ctx);
        RecyclerView recycler = new RecyclerView(ctx);
        recycler.setLayoutManager(new LinearLayoutManager(ctx));
        recycler.setAdapter(adapter);
        frame.addView(recycler, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        loadingTextView = new TextView(ctx);
        loadingTextView.setText("加载中，请稍候...");
        loadingTextView.setTextColor(Color.WHITE);
        loadingTextView.setGravity(Gravity.CENTER);
        loadingTextView.setVisibility(View.GONE);
        frame.addView(loadingTextView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        TextView sort = new TextView(ctx);
        sort.setText("⇅");
        sort.setTextColor(Color.WHITE);
        sort.setTextSize(18);
        sort.setGravity(Gravity.CENTER);
        GradientDrawable sortBg = new GradientDrawable();
        sortBg.setColor(0x80666666);
        sortBg.setCornerRadius(dp(18));
        sort.setBackground(sortBg);
        FrameLayout.LayoutParams sortLp = new FrameLayout.LayoutParams(dp(36), dp(36));
        sortLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        sortLp.setMarginEnd(dp(10));
        frame.addView(sort, sortLp);
        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        frameLp.bottomMargin = dp(10);
        bg.addView(frame, frameLp);
        // 事件（官 onCreate 同点位）
        search.setOnClickListener(v -> loadData());                       // $6
        editTextCover.setOnClickListener(v -> {                           // $7
            editTextCover.setVisibility(View.GONE);
            editText.setVisibility(View.VISIBLE);
            editText.requestFocus();
            showKeyboard(editText);
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {     // $8（回车）
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                loadData();
                return true;
            }
            return false;
        });
        sort.setOnClickListener(this::onSortClick);                       // $11
        return bg;
    }

    /** 官 onCreate 主体：fromKeyWord 预填＋枚举任务投递（$5）。 */
    private void onPopupCreate() {
        if (!TextUtils.isEmpty(fromKeyWord)) {
            editText.setText(fromKeyWord);
            editTextCover.setText(fromKeyWord);
            try {
                editText.setSelection(editText.getText().length());
            } catch (Exception e) {
                Log.e(TAG, "onCreate", e);
            }
        }
        Task.execute(() -> { // $5（HeavyTaskUtil.executeNewTask）
            Map<String, DanmuSearchRuleDTO> collected = collectDanmuRules(HkRuleManager.get().getRules());
            rulesMap.clear();
            rulesMap.putAll(collected);
            App.post(this::onRulesCollected); // $4（ThreadTool.runOnUI）
        });
    }

    /** $4：枚举完 UI 支——无源出确认面；有源建源条并按 danmuS 自动 btnClick。 */
    private void onRulesCollected() {
        Context ctx = getContext();
        if (ctx == null) return;
        if (rulesMap.isEmpty()) {
            Activity activity = getActivity();
            boolean landscape = activity != null
                    && activity.getRequestedOrientation() == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            // 官 XPopup ConfirmPopupView（isDarkTheme＝横屏旗，载体差记名）→ 咱 HkDialogs 同位承载
            new HkDialogs(ctx)
                    .setTitle("温馨提示")
                    .setMessage("检测到您还没有带弹幕搜索规则的小程序，是否跳转云盘君简小程序来检索弹幕？")
                    .setPositiveButton("确定", (d, w) -> onConfirmJumpToLoadDm(landscape))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        String[] keys = rulesMap.keySet().toArray(new String[0]);
        buildTabs(keys);
        String danmuS = prefs().getString("danmuS", keys[0]);
        int selected = 0;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(danmuS)) {
                selected = i;
                break;
            }
        }
        btnClick(selected);
    }

    /** $3：确认后跳云盘君简 loadDm 子页（url 原样拼 fromKeyWord、不编码，照官）。 */
    private void onConfirmJumpToLoadDm(boolean landscape) {
        Activity activity = getActivity() != null ? getActivity() : hostActivity;
        if (activity == null) return;
        startRulePage(activity, "hiker://page/loadDm?rule=云盘君.简&title=" + fromKeyWord, landscape);
    }

    /**
     * 官 startRulePage：PageParser.getNextPage 组临时规则开列表页；
     * 回 null 静默 return（咱 getNextPage 非 page 址回 null、查无规则/子页
     * 抛 ParseException，两形分别落官两支）；抛错 → Timber.e＋
     * ToastMgr.shortCenter(msg)。横屏旗：官 startRulePage:465-466 是把
     * 旗写入 SettingConfig.globalNightMode/globalLandMode（全局夜间/
     * 横屏旗），咱无 SettingConfig 对位、承载不消费此旗（载体差记名，
     * 形参保留对位）。开页载体差：官带 parentUrl/parentTitle extra 且
     * 走 startActivityForResult，咱 HkListActivity.startRule 无此二
     * extra 与 ForResult 变体（记名，见 census 批 C2 追记）。#noHistory#
     * 剥除官在 putTempRule 序列化之后执行、实为死代码，照官实效不剥
     * （注：HkPageParser.resolvePageUrl 的剥除是既有别路口径，不属本面）。
     */
    private void startRulePage(Activity activity, String url, boolean landscape) {
        try {
            HkRule next = HkPageParser.getNextPage(null, url, null);
            if (next == null) return;
            HkListActivity.startRule(activity, next, next.getTitle());
        } catch (Exception e) {
            Log.e(TAG, "startRulePage", e);
            toastCenter(e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // 源条（SegmentControlView 等价承载）
    // ---------------------------------------------------------------

    private void buildTabs(String[] keys) {
        tabsLayout.removeAllViews();
        for (int i = 0; i < keys.length; i++) {
            TextView tab = new TextView(getContext());
            tab.setText(keys[i]);
            tab.setGravity(Gravity.CENTER);
            tab.setSingleLine(true);
            tab.setTextSize(13);
            final int index = i;
            tab.setOnClickListener(v -> btnClick(index));
            tabsLayout.addView(tab, new LinearLayout.LayoutParams(dp(80), dp(30)));
        }
    }

    private void refreshTabs(int selected) {
        for (int i = 0; i < tabsLayout.getChildCount(); i++) {
            TextView tab = (TextView) tabsLayout.getChildAt(i);
            boolean on = i == selected;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(15));
            if (on) {
                bg.setColor(0xFF00A2FF);
                tab.setTextColor(Color.WHITE);
            } else {
                bg.setColor(0x00000000);
                bg.setStroke(dp(1), 0xFF9AA0A6);
                tab.setTextColor(0xFFCCCCCC);
            }
            tab.setBackground(bg);
        }
    }

    /** 官 SegmentControlView.btnClick：触发 $2（写 danmuS＋loadData）。 */
    private void btnClick(int index) {
        refreshTabs(index);
        onSegmentClick(index);
    }

    /** $2：下标在键集范围内才写 danmuS，随后恒 loadData。 */
    private void onSegmentClick(int index) {
        String[] keys = rulesMap.keySet().toArray(new String[0]);
        if (keys.length > index) {
            prefs().edit().putString("danmuS", keys[index]).apply();
        }
        loadData();
    }

    // ---------------------------------------------------------------
    // 搜索（官 loadData / $13 / evalPageRule / $14 / $15）
    // ---------------------------------------------------------------

    private void loadData() {
        if (editText == null || (loadingTextView != null && loadingTextView.getVisibility() == View.VISIBLE)) return;
        if (listener != null) listener.startLoad();
        hideKeyboard();
        String keyword = editText.getText().toString();
        if (TextUtils.isEmpty(keyword) || rulesMap.isEmpty()) {
            results.clear();
            adapter.notifyDataSetChanged();
            return;
        }
        String danmuS = prefs().getString("danmuS", rulesMap.keySet().toArray(new String[0])[0]);
        int action = decideLoadAction(rulesMap.keySet(), danmuS, rulesMap.containsKey(danmuS));
        if (action == LOAD_CLEAR) {
            results.clear();
            adapter.notifyDataSetChanged();
            return;
        }
        if (action == LOAD_SILENT) return; // danmuS 陈值：静默死支（官 bug 照录）
        loadingTextView.setVisibility(View.VISIBLE);
        final String sourceName = danmuS;
        Task.submit(() -> { // $13
            try {
                DanmuSearchRuleDTO dto = rulesMap.get(sourceName);
                // 官形无 dto null 守卫：dto 为 null 时 evalPageRule 内解引用
                // NPE → 下方 catchall（$12 灭 loading）再 rethrow，照官不设守卫。
                evalPageRule(dto, keyword);
            } catch (Throwable t) {
                App.post(() -> { // $12
                    if (loadingTextView != null) loadingTextView.setVisibility(View.GONE);
                });
                throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
            }
        });
    }

    /**
     * 官 loadData 定源循环的纯形抽出（夹具 D8 用）：遍历键集找 danmuS——
     * 找到且 map 含该键 → LOAD_START；找到但 map 不含 → LOAD_CLEAR；
     * 遍历完没找到（陈值）→ LOAD_SILENT。
     */
    static int decideLoadAction(Set<String> keys, String danmuS, boolean mapContains) {
        for (String key : keys) {
            if (key.equals(danmuS)) {
                return mapContains ? LOAD_START : LOAD_CLEAR;
            }
        }
        return LOAD_SILENT;
    }

    /** 官 evalPageRule：拼 getMyRule(父规则)＋findRule、以关键词为 input 求值；dismiss 后只灭 loading（$15）。 */
    private void evalPageRule(DanmuSearchRuleDTO dto, String keyword) {
        HkJsRuntime rt = HkEngineManager.runtimeFor(dto.rule);
        try {
            HkEngineManager.ensureInit(rt);
        } catch (Exception e) {
            Log.e(TAG, "evalPageRule init", e);
            throw new RuntimeException(e);
        }
        Object result = rt.evalJSForObj(HkJsRuntime.getMyRule(dto.rule) + dto.findRule, keyword);
        if (!dismissed) {
            App.post(() -> onEvalResult(result, dto)); // $14
        } else {
            App.post(() -> { // $15
                if (loadingTextView != null) loadingTextView.setVisibility(View.GONE);
            });
        }
    }

    /** $14：结果加工（官源行 352-430 逐支）。 */
    private void onEvalResult(Object result, DanmuSearchRuleDTO dto) {
        Context ctx = getContext() != null ? getContext() : hostActivity;
        try {
            if (loadingTextView != null) loadingTextView.setVisibility(View.GONE);
            if (result instanceof List) {
                List<HkDanmuResult> parsed = HkDanmuResult.parseArray((List<?>) result);
                results.clear();
                if (!parsed.isEmpty()) results.addAll(parsed);
                adapter.notifyDataSetChanged();
                return;
            }
            results.clear();
            adapter.notifyDataSetChanged();
            if (result instanceof String) {
                String s = (String) result;
                if (!TextUtils.isEmpty(s) && !"undefined".equalsIgnoreCase(s)) {
                    if (s.startsWith("toast://")) {
                        toastBottom(replaceOnceIgnoreCase(s, "toast://", ""));
                    } else if (s.startsWith("web://")) {
                        String u = HkParserKit.replaceOnce(s, "web://", "");
                        HkFuck.goWebHook.goWeb(ctx, HkHttpParser.getFirstPageUrl(u));
                    } else if (s.startsWith("x5://")) {
                        String stripped = replaceOnceIgnoreCase(s, "x5://", "");
                        HkRule temp = new HkRule();
                        temp.setUrl("hiker://empty");
                        temp.setFindRule("js:setResult([{\n    url:\"" + HkJsRuntime.escapeJsString(stripped) + "\",\ndesc:\"100%&&float\",\nextra:{canBack: true}\n}]);");
                        temp.setColType(HkColType.X5_WEB_VIEW.code);
                        temp.setTitle("详情");
                        Activity activity = currentActivity();
                        if (activity != null) HkListActivity.startRule(activity, temp, "详情");
                    } else if (HkPageParser.isPageUrl(s)) {
                        try {
                            HkRule next = HkPageParser.getNextPage(dto.rule, s, null);
                            if (next == null) return;
                            Activity activity = currentActivity();
                            if (activity != null) HkListActivity.startRule(activity, next, next.getTitle());
                        } catch (Exception e) {
                            Log.e(TAG, "evalPageRule page", e);
                            toastCenter(e.getMessage());
                        }
                    } else {
                        Activity activity = currentActivity();
                        HkArticleFragment.DetailBridge bridge = HkArticleFragment.detailBridge();
                        if (activity != null && bridge != null) {
                            bridge.dealUrlSimply(activity, null, dto.rule, null, s, null, null);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "evalPageRule result", e);
            toastBottom("出错：" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // 选中与懒规则（官 fetchDanmu / $1 / dealUrl）
    // ---------------------------------------------------------------

    /** 官 fetchDanmu(I)：越界/url 空/listener 空静默 return；含 @lazyRule= 走 parseFroAny、否则直 dealUrl。 */
    public void fetchDanmu(int pos) {
        if (pos < 0 || pos >= results.size()) return;
        HkDanmuResult result = results.get(pos);
        if (TextUtils.isEmpty(result.getUrl()) || listener == null) return;
        listener.startLoad();
        String url = result.getUrl();
        String[] parts = url.split("@lazyRule=");
        if (parts.length > 1) {
            Activity activity = hostActivity != null ? hostActivity : getActivity();
            HkLazyRuleParser.parseFroAny(null, activity, currentArticleListRule(), parts, pos, "", url,
                    new HkLazyRuleParser.BaseParseCallback<Object>() {
                        @Override
                        public void start() {
                            if (loadingTextView != null) loadingTextView.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void error(String msg) {
                            // 官 $1.error：只灭 loading，不 toast、不清弹幕
                            if (loadingTextView != null) loadingTextView.setVisibility(View.GONE);
                        }

                        @Override
                        public void success(Object obj) {
                            if (loadingTextView != null) loadingTextView.setVisibility(View.GONE);
                            dealUrl(pos, obj);
                        }
                    });
        } else {
            dealUrl(pos, url);
        }
    }

    /** 官 currentArticleListRule：按 danmuS 取当前源 DTO 的父规则；全程 try/catch，异常/不匹配回 null。 */
    private HkRule currentArticleListRule() {
        try {
            String[] keys = rulesMap.keySet().toArray(new String[0]);
            String danmuS = prefs().getString("danmuS", keys[0]);
            for (String key : keys) {
                if (key.equals(danmuS)) {
                    DanmuSearchRuleDTO dto = rulesMap.get(danmuS);
                    return dto == null ? null : dto.rule;
                }
            }
            return null;
        } catch (Exception e) {
            // 取规则失败不再与「不匹配」同回 null 静默（残项处置 2026-11-14）：
            // 仍回 null 走原分流，但经本弹窗既有 toast 形态显形。
            Log.e(TAG, "currentArticleListRule", e);
            toastBottom("当前弹幕规则读取失败");
            return null;
        }
    }

    /** 官 dealUrl(I, Object)：String 三支（toast://／web://／listener.click）；List 支换二段列表；其余无操作。 */
    private void dealUrl(int pos, Object obj) {
        if (obj instanceof String) {
            String s = (String) obj;
            if (s.startsWith("toast://")) {
                toastBottom(replaceOnceIgnoreCase(s, "toast://", ""));
            } else if (s.startsWith("web://")) {
                Context ctx = getContext() != null ? getContext() : hostActivity;
                String u = HkParserKit.replaceOnce(s, "web://", "");
                HkFuck.goWebHook.goWeb(ctx, HkHttpParser.getFirstPageUrl(u));
            } else if (listener != null) {
                listener.click(pos, results, s, sortMode);
            }
        } else if (obj instanceof List) {
            List<HkDanmuResult> parsed = HkDanmuResult.parseArray((List<?>) obj);
            results.clear();
            if (!parsed.isEmpty()) results.addAll(parsed);
            adapter.notifyDataSetChanged();
        }
    }

    // ---------------------------------------------------------------
    // 排序（官 $11 sortView 点击 / $10 选后）
    // ---------------------------------------------------------------

    private void onSortClick(View view) {
        if (sortMode == 0 && !fromFloatVideo) {
            Context ctx = getContext() != null ? getContext() : hostActivity;
            if (ctx == null) return;
            HkDialogs.cards(ctx, null,
                    new String[]{"因当前顺序不对而倒序", "为查看最新剧集而倒序"}, null,
                    (dialog, which) -> onSortSelected(which));
        } else {
            sortMode = 0;
            Collections.reverse(results);
            adapter.notifyDataSetChanged();
            if (listener != null) listener.startLoad();
        }
    }

    /** $10：恒反转列表；index＝0 → sortMode＝1；index＝1 → sortMode＝2＋倒序绑定 toast；末尾 startLoad 清绑定。 */
    private void onSortSelected(int index) {
        Collections.reverse(results);
        adapter.notifyDataSetChanged();
        if (index == 0) {
            sortMode = 1;
        } else {
            sortMode = 2;
            toastBottom("已倒序显示，当视频切换下一集时，弹幕绑定前一个结果");
        }
        if (listener != null) listener.startLoad();
    }

    // ---------------------------------------------------------------
    // 枚举（官 $5 本体，抽成静态纯形供夹具）
    // ---------------------------------------------------------------

    /**
     * 官 $5 枚举：规则 pages 非空且含 "\"danmu#" 才看子页表；子页 path
     * 非空且以 "danmu#" 开头者组 DTO，以子页 name 为键入表（同名后者覆盖）。
     */
    static Map<String, DanmuSearchRuleDTO> collectDanmuRules(List<HkRule> rules) {
        Map<String, DanmuSearchRuleDTO> map = new HashMap<>();
        if (rules == null) return map;
        for (HkRule rule : rules) {
            if (rule == null) continue;
            String pages = rule.getPages();
            if (TextUtils.isEmpty(pages) || !pages.contains("\"danmu#")) continue;
            List<HkPageParser.HkPage> pageList = HkPageParser.getPageList(rule);
            if (pageList.isEmpty()) continue;
            for (HkPageParser.HkPage page : pageList) {
                String path = page.getPath();
                if (TextUtils.isEmpty(path) || !path.startsWith("danmu#")) continue;
                DanmuSearchRuleDTO dto = new DanmuSearchRuleDTO();
                dto.ruleName = page.getName();
                dto.findRule = page.getRule();
                dto.rule = rule;
                map.put(page.getName(), dto);
            }
        }
        return map;
    }

    // ---------------------------------------------------------------
    // 小件
    // ---------------------------------------------------------------

    /** 官 PreferenceMgr 默认文件（"share_data"）——danmuS 唯一载体，不许混用其他键域。 */
    private SharedPreferences prefs() {
        return App.get().getSharedPreferences("share_data", Context.MODE_PRIVATE);
    }

    private Activity currentActivity() {
        return getActivity() != null ? getActivity() : hostActivity;
    }

    /** 对应 ToastMgr.shortBottomCenter（与 HkArticleFragment.toastBottom 同款）。 */
    private void toastBottom(String text) {
        Context ctx = getContext() != null ? getContext() : hostActivity;
        if (ctx == null || text == null) return;
        ToastUtil.show(ctx, text, android.widget.Toast.LENGTH_SHORT);
    }

    /** 对应 ToastMgr.shortCenter（咱家 ToastUtil 同口，见 HkArticleAdapter.toastCenter 先例）。 */
    private void toastCenter(String text) {
        toastBottom(text);
    }

    private void showKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        if (editText == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
    }

    /** StringUtils.replaceOnceIgnoreCase 等价（官 toast://／x5:// 剥前缀用）。 */
    static String replaceOnceIgnoreCase(String s, String prefix, String replacement) {
        if (s == null || prefix == null || prefix.isEmpty()) return s;
        int idx = s.toLowerCase(java.util.Locale.ROOT).indexOf(prefix.toLowerCase(java.util.Locale.ROOT));
        if (idx < 0) return s;
        return s.substring(0, idx) + replacement + s.substring(idx + prefix.length());
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable bottomBorder() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x00000000);
        return d;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 官 DanmuSearchAdapter：单 TextView 行（item_danmu_search）。 */
    private final class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setTextSize(13);
            tv.setTextColor(0xFF00A2FF);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setBackgroundColor(0xFF343C3E);
            tv.setMinHeight(dp(44));
            tv.setMaxLines(3);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setPadding(dp(8), 0, dp(8), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(15), 0, dp(15), 0);
            tv.setLayoutParams(lp);
            return new Holder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.text.setText(results.get(position).getTitle());
            holder.itemView.setOnClickListener(v -> fetchDanmu(holder.getBindingAdapterPosition())); // $9
        }

        @Override
        public int getItemCount() {
            return results.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView text;

            Holder(TextView itemView) {
                super(itemView);
                this.text = itemView;
            }
        }
    }
}
