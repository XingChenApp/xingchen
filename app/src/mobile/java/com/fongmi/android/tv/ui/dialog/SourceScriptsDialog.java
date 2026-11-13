package com.fongmi.android.tv.ui.dialog;
import com.fongmi.android.tv.utils.ToastUtil;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.PluginKinds;
import com.fongmi.android.tv.ui.adapter.ScriptAdapter;
import com.fongmi.android.tv.utils.PyExtConfig;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SourceScriptsDialog extends Dialog {
    private final OnScriptSelectListener listener;
    private File currentFile;
    private List<File> allPyFiles = new ArrayList<>();
    private List<File> allJsFiles = new ArrayList<>();
    private List<File> allHtmlFiles = new ArrayList<>();
    private ScriptAdapter pyAdapter;
    private ScriptAdapter jsAdapter;
    private ScriptAdapter htmlAdapter;
    private RecyclerView rvPy;
    private RecyclerView rvJs;
    private RecyclerView rvHtml;
    private LinearLayout colPy;
    private LinearLayout colJs;
    private LinearLayout colHtml;
    private LinearLayout layoutColumns;
    private TextView tvEmpty;
    private File pyDir;
    private File jsDir;
    private File htmlDir;
    private String currentQuery = "";
    private LinearLayout.LayoutParams pristinePyParams;
    private LinearLayout.LayoutParams pristineJsParams;
    private LinearLayout.LayoutParams pristineHtmlParams;
    private FileObserver pyObserver;
    private FileObserver jsObserver;
    private FileObserver htmlObserver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean reloadPosted;
    private final Runnable reloadRunnable = new Runnable() {
        @Override
        public void run() {
            reloadPosted = false;
            if (isShowing()) renderLists();
        }
    };

    public interface OnScriptSelectListener {
        /** kind 取 PluginKinds.PY／JS／HTML（替代旧 isPy 布尔，HTML 无二分位置）。 */
        void onScriptSelect(File file, String kind);
    }

    public SourceScriptsDialog(@NonNull Context context, OnScriptSelectListener listener) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        this.listener = listener;
        setCanceledOnTouchOutside(true);
        setCancelable(true);
    }

    public void setCurrentFile(File file) {
        this.currentFile = file;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_source_scripts);
        if (getWindow() != null) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            // 进出统一口径：淡入淡出 200ms（与 BaseAlertDialog 基类同一套 HkDialogFade）
            getWindow().setWindowAnimations(R.style.HkDialogFade);
        }
        try {
            if (getWindow() != null) {
                getWindow().setGravity(android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
                android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = android.view.WindowManager.LayoutParams.MATCH_PARENT;
                lp.softInputMode = android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
                getWindow().setAttributes(lp);
                android.view.View content = findViewById(android.R.id.content);
                if (content != null) {
                    float d = getContext().getResources().getDisplayMetrics().density;
                    content.setPadding((int)(24*d), (int)(60*d), (int)(12*d), 0);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }

        rvPy = findViewById(R.id.rv_py);
        rvJs = findViewById(R.id.rv_js);
        rvHtml = findViewById(R.id.rv_html);
        colPy = findViewById(R.id.column_py);
        colJs = findViewById(R.id.column_js);
        colHtml = findViewById(R.id.column_html);
        layoutColumns = findViewById(R.id.layout_columns);
        tvEmpty = findViewById(R.id.tv_empty);

        // 列参数的布局原值先存一份：单列形态会改 weight/外边距，重列时先复位再套形态，
        // 不许上一形态的改动残留（首开空→补列后直接进多列/单列都要从原值起算）。
        pristinePyParams = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) colPy.getLayoutParams());
        pristineJsParams = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) colJs.getLayoutParams());
        pristineHtmlParams = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) colHtml.getLayoutParams());

        pyDir = new File(getContext().getFilesDir(), "plugins/py");
        jsDir = new File(getContext().getFilesDir(), "plugins/js");
        htmlDir = new File(getContext().getFilesDir(), "plugins/html");

        renderLists();

        EditText etSearch = findViewById(R.id.et_search);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                currentQuery = s == null ? "" : s.toString();
                applyFilter(currentQuery);
            }
        });

        findViewById(android.R.id.content).setOnClickListener(v -> dismiss());
    }

    /**
     * 重读三目录并整表重列（空/单列/多列各形态都从布局原值起算）。
     * 首开空的病根：旧版只在 onCreate 读一次目录——读的那一瞬目录还没就绪
     * （文件在弹窗打开之后才落盘/到位），空态连同「适配器从未创建」被冻住，
     * 弹窗开着永远不会自己好，只能关掉重开才读到。本方法由三路触发：
     * onCreate 首列、onStart 补读（堵构造→显示之间的落盘窗口）、目录监听
     * 事件（弹窗开着时文件到位即补列）。数据本来就绪时重列结果与首列一致。
     * 列形态口径（三列同规）：可见列一律 weight=1；首个可见列左边距归零、
     * 末个可见列右边距归零（单列即两头都归零），其余按布局原值——与旧双列
     * 形态逐项等价（PY/JS 两列时的边距结果一字不变）。
     */
    private void renderLists() {
        if (rvPy == null || rvJs == null || rvHtml == null) return;
        allPyFiles = listScripts(pyDir, ".py");
        allJsFiles = listScripts(jsDir, ".js");
        allHtmlFiles = listScripts(htmlDir, ".html");

        boolean hasPy = !allPyFiles.isEmpty();
        boolean hasJs = !allJsFiles.isEmpty();
        boolean hasHtml = !allHtmlFiles.isEmpty();

        colPy.setLayoutParams(new LinearLayout.LayoutParams(pristinePyParams));
        colJs.setLayoutParams(new LinearLayout.LayoutParams(pristineJsParams));
        colHtml.setLayoutParams(new LinearLayout.LayoutParams(pristineHtmlParams));

        if (!hasPy && !hasJs && !hasHtml) {
            layoutColumns.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        layoutColumns.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        colPy.setVisibility(hasPy ? View.VISIBLE : View.GONE);
        colJs.setVisibility(hasJs ? View.VISIBLE : View.GONE);
        colHtml.setVisibility(hasHtml ? View.VISIBLE : View.GONE);
        LinearLayout[] cols = {colPy, colJs, colHtml};
        boolean[] has = {hasPy, hasJs, hasHtml};
        int first = -1, last = -1;
        for (int i = 0; i < cols.length; i++) {
            if (!has[i]) continue;
            if (first < 0) first = i;
            last = i;
        }
        for (int i = 0; i < cols.length; i++) {
            if (!has[i]) continue;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) cols[i].getLayoutParams();
            lp.weight = 1;
            if (i == first) lp.setMarginStart(0);
            if (i == last) lp.setMarginEnd(0);
            cols[i].setLayoutParams(lp);
        }

        boolean firstFill = pyAdapter == null && jsAdapter == null && htmlAdapter == null;
        if (pyAdapter == null) {
            rvPy.setLayoutManager(new LinearLayoutManager(getContext()));
            pyAdapter = new ScriptAdapter(allPyFiles, f -> {
                if (listener != null) listener.onScriptSelect(f, PluginKinds.PY);
                dismiss();
            });
            pyAdapter.setOnItemLongClickListener(f -> showExtConfigDialog(f, true));
            rvPy.setAdapter(pyAdapter);
        }
        if (jsAdapter == null) {
            rvJs.setLayoutManager(new LinearLayoutManager(getContext()));
            jsAdapter = new ScriptAdapter(allJsFiles, f -> {
                if (listener != null) listener.onScriptSelect(f, PluginKinds.JS);
                dismiss();
            });
            jsAdapter.setOnItemLongClickListener(f -> showExtConfigDialog(f, false));
            rvJs.setAdapter(jsAdapter);
        }
        if (htmlAdapter == null) {
            rvHtml.setLayoutManager(new LinearLayoutManager(getContext()));
            htmlAdapter = new ScriptAdapter(allHtmlFiles, f -> {
                if (listener != null) listener.onScriptSelect(f, PluginKinds.HTML);
                dismiss();
            });
            // HTML 列不挂长按：ext 配置是 PyExtConfig 蜘蛛口径，WebHome 网页源不适用。
            rvHtml.setAdapter(htmlAdapter);
        }

        applyFilter(currentQuery);
        // 滚动定位只在首次填充时做：开着时因文件事件补列不许把用户正看的位置拽走。
        if (firstFill) {
            scrollToCurrent(rvPy, allPyFiles);
            scrollToCurrent(rvJs, allJsFiles);
            scrollToCurrent(rvHtml, allHtmlFiles);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startWatching();
        renderLists();
    }

    @Override
    protected void onStop() {
        stopWatching();
        super.onStop();
    }

    /** 监听三脚本目录（与 JsLoader.watchPlugins 同一目录、同一事件口径）：文件落盘/替换/删除即补列。 */
    private void startWatching() {
        pyObserver = watchDir(pyDir, ".py");
        jsObserver = watchDir(jsDir, ".js");
        htmlObserver = watchDir(htmlDir, ".html");
    }

    private FileObserver watchDir(File dir, final String ext) {
        if (dir == null) return null;
        try {
            // 目录尚不存在时监听挂不上：先建出来（导入链也以此目录为落点，建空目录无副作用）。
            if (!dir.exists()) dir.mkdirs();
            FileObserver observer = new FileObserver(dir.getAbsolutePath(),
                    FileObserver.CREATE | FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.DELETE) {
                @Override
                public void onEvent(int event, String path) {
                    if (!ScriptFiles.isScriptName(path, ext)) return;
                    postReload();
                }
            };
            observer.startWatching();
            return observer;
        } catch (Exception e) {
            return null;
        }
    }

    private void stopWatching() {
        if (pyObserver != null) {
            try { pyObserver.stopWatching(); } catch (Exception ignored) {}
            pyObserver = null;
        }
        if (jsObserver != null) {
            try { jsObserver.stopWatching(); } catch (Exception ignored) {}
            jsObserver = null;
        }
        if (htmlObserver != null) {
            try { htmlObserver.stopWatching(); } catch (Exception ignored) {}
            htmlObserver = null;
        }
        mainHandler.removeCallbacks(reloadRunnable);
        reloadPosted = false;
    }

    /** 文件系统事件可能连发：合并成一次主线程重列（事件合并，非数据重试）。 */
    private void postReload() {
        if (reloadPosted) return;
        reloadPosted = true;
        mainHandler.post(reloadRunnable);
    }

    private void applyFilter(String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        if (pyAdapter != null) pyAdapter.updateData(filterFiles(allPyFiles, kw));
        if (jsAdapter != null) jsAdapter.updateData(filterFiles(allJsFiles, kw));
        if (htmlAdapter != null) htmlAdapter.updateData(filterFiles(allHtmlFiles, kw));
    }

    private List<File> filterFiles(List<File> files, String kw) {
        if (kw.isEmpty()) return new ArrayList<>(files);
        List<File> result = new ArrayList<>();
        for (File f : files) {
            if (f.getName().toLowerCase().contains(kw)) result.add(f);
        }
        return result;
    }

    private void showExtConfigDialog(File file, boolean isPy) {
        try {
            Context ctx = getContext();
            String name = file.getName();
            String baseName = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
            String key = (isPy ? "py_" : "js_") + baseName;

            String saved = PyExtConfig.load(ctx, key);
            String account = "";
            String password = "";
            boolean startAdvanced = false;
            try {
                if (saved != null && !saved.isEmpty()) {
                    JSONObject jo = new JSONObject(saved);
                    boolean onlyUserPass = true;
                    java.util.Iterator<String> keys = jo.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        if (!"username".equals(k) && !"password".equals(k)) {
                            onlyUserPass = false;
                            break;
                        }
                    }
                    if (onlyUserPass) {
                        account = jo.optString("username", "");
                        password = jo.optString("password", "");
                    } else {
                        startAdvanced = true;
                    }
                }
            } catch (Exception e) {
                startAdvanced = true;
            }

            Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
            dialog.setContentView(R.layout.dialog_py_ext_config);
            dialog.setCanceledOnTouchOutside(true);
            dialog.setCancelable(true);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                // 进出统一口径：淡入淡出 200ms（与 BaseAlertDialog 基类同一套 HkDialogFade）
                dialog.getWindow().setWindowAnimations(R.style.HkDialogFade);
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                // 终宽在 show 前一次算清（屏宽减两侧各 24dp）：旧法 show 之后再改宽＝二次摆位，已摘除
                float d = ctx.getResources().getDisplayMetrics().density;
                lp.width = (int) (ctx.getResources().getDisplayMetrics().widthPixels - 48 * d);
                lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
                lp.gravity = android.view.Gravity.CENTER;
                dialog.getWindow().setAttributes(lp);
            }

            TextView tvScriptName = dialog.findViewById(R.id.tv_script_name);
            TextView tvToggle = dialog.findViewById(R.id.tv_toggle);
            View boxSimple = dialog.findViewById(R.id.box_simple);
            View boxAdvanced = dialog.findViewById(R.id.box_advanced);
            EditText etAccount = dialog.findViewById(R.id.et_account);
            EditText etPassword = dialog.findViewById(R.id.et_password);
            EditText etJson = dialog.findViewById(R.id.et_json);
            View btnCancel = dialog.findViewById(R.id.btn_cancel);
            View btnSave = dialog.findViewById(R.id.btn_save);

            tvScriptName.setText(name);
            etAccount.setText(account);
            etPassword.setText(password);
            if (startAdvanced && saved != null) {
                etJson.setText(saved);
            }

            final boolean[] isAdvanced = new boolean[]{startAdvanced};
            Runnable refreshMode = new Runnable() {
                @Override
                public void run() {
                    boolean adv = isAdvanced[0];
                    boxSimple.setVisibility(adv ? View.GONE : View.VISIBLE);
                    boxAdvanced.setVisibility(adv ? View.VISIBLE : View.GONE);
                    tvToggle.setText(adv ? "◂ 简单模式" : "高级 ▸");
                }
            };
            refreshMode.run();
            tvToggle.setOnClickListener(v -> {
                isAdvanced[0] = !isAdvanced[0];
                if (isAdvanced[0] && etJson.getText().toString().trim().isEmpty()) {
                    etJson.setText("{\"cookie\":\"\"}");
                    etJson.setSelection("{\"cookie\":\"".length());
                }
                refreshMode.run();
            });

            btnCancel.setOnClickListener(v -> dialog.dismiss());
            btnSave.setOnClickListener(v -> {
                String json = "";
                if (isAdvanced[0]) {
                    String raw = etJson.getText().toString().trim();
                    if (!raw.isEmpty()) {
                        try {
                            new JSONObject(raw);
                            json = raw;
                        } catch (Exception e) {
                            ToastUtil.show(ctx, "JSON 格式不正确", Toast.LENGTH_SHORT);
                            return;
                        }
                    }
                } else {
                    String a = etAccount.getText().toString().trim();
                    String p = etPassword.getText().toString().trim();
                    if (!a.isEmpty() || !p.isEmpty()) {
                        try {
                            JSONObject jo = new JSONObject();
                            jo.put("username", a);
                            jo.put("password", p);
                            json = jo.toString();
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
                // D-04 接线（定稿：登录完成处直接调 Java 入口，不走 runner.py 路）：
                // runner.py 的 Runner 从未被 Java 实例化（app.py 的 spider() 直接返回裸蜘蛛，
                // Java 活路是 app.py＋chaquo Spider），其 refresh_home_after_login 悬空、
                // 接过去等于新造一条 Python→Java 回调桥，风险大于收益，故不接。
                // 这里是壳子侧唯一确定的登录完成信号：用户刚在插件配置里存了账号／
                // 密码／Cookie（ext）。只改首页缓存键（ext 哈希段）不够——缓存蜘蛛
                // 创建时 init 用的还是旧 ext，换键不换蜘蛛，新登录态进不了蜘蛛；
                // 显式调 refreshHomeAfterLogin 才会摘掉旧蜘蛛（下次按新 ext 重建）
                // 并立即重拉首页。ext 没变时不触发，避免无意义地杀蜘蛛重建。
                String before = PyExtConfig.load(ctx, key);
                PyExtConfig.save(ctx, key, json);
                if (!before.equals(json)) {
                    try {
                        com.fongmi.android.tv.api.SiteApi.refreshHomeAfterLogin(key);
                    } catch (Throwable ignored) {
                    }
                }
                ToastUtil.show(ctx, "已保存", Toast.LENGTH_SHORT);
                dialog.dismiss();
            });

            dialog.show();
        } catch (Exception e) { e.printStackTrace(); }
    }


    private void scrollToCurrent(RecyclerView rv, List<File> files) {
        if (currentFile == null || files == null || files.isEmpty()) return;
        try {
            String cur = currentFile.getAbsolutePath();
            for (int i = 0; i < files.size(); i++) {
                if (cur.equals(files.get(i).getAbsolutePath())) {
                    rv.scrollToPosition(i);
                    break;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private List<File> listScripts(File dir, String ext) {
        return ScriptFiles.list(dir, ext);
    }
}
