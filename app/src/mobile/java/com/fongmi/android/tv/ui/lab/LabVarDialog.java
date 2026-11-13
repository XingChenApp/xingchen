package com.fongmi.android.tv.ui.lab;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.fongmi.android.tv.utils.Notify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 命令变量填写弹窗（上游 LabVariableDialog 口径，星辰皮）：
 * 变量类型 text / text_multiline / number / select / switch / file / directory；
 * 渲染只留名字＋必填标记，hint 只做输入框 placeholder，不单独成行（忌介绍文字）。
 * select 点行弹出选项选；file/directory 带「选」钮调系统文件选择器。
 */
public final class LabVarDialog {

    public interface Callback {
        void onConfirm(Map<String, String> values);
    }

    private static final int REQ_VAR_PICK = 0x2107;
    /** 文件选择回填：file/directory 类型用，Activity 在 onActivityResult 里转交 */
    private static PickTarget pendingPick;

    private static class PickTarget {
        EditText edit;
        boolean dir;
    }

    /** Activity 侧在 onActivityResult 里调这一行即可 */
    public static boolean onPickResult(int requestCode, int resultCode, Intent data, Activity activity) {
        if (requestCode != REQ_VAR_PICK || pendingPick == null) return false;
        PickTarget t = pendingPick;
        pendingPick = null;
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null && t.edit != null) {
            Uri uri = data.getData();
            try {
                activity.getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            // 回填必须是真实文件路径：命令拿这个值当路径用，此前直接填 uri.getPath()，
            // content URI 的 path 是 /document/primary:… 这种假路径，命令永远找不到文件
            String path = resolvePath(uri, t.dir);
            if (path != null) {
                t.edit.setText(path);
            } else if (t.dir) {
                Notify.show("这个目录拿不到真实路径，换个位置试试");
            } else {
                String copied = copyToCache(activity, uri);
                if (copied != null) t.edit.setText(copied);
                else Notify.show("这个文件拿不到真实路径，换个位置试试");
            }
        }
        return true;
    }

    /** content URI → 真实路径：file 直取；document/tree 按文档 id 拆卷与相对路径
     * （primary=内部存储根，其它卷=/storage/&lt;卷&gt;，raw: 后面就是真路径）；拆不出返回 null */
    private static String resolvePath(Uri uri, boolean dir) {
        if ("file".equals(uri.getScheme())) return uri.getPath();
        try {
            String docId = dir
                    ? android.provider.DocumentsContract.getTreeDocumentId(uri)
                    : android.provider.DocumentsContract.getDocumentId(uri);
            if (docId == null) return null;
            if (docId.startsWith("raw:")) return docId.substring(4);
            int colon = docId.indexOf(':');
            if (colon < 0) return null;
            String volume = docId.substring(0, colon);
            String rel = docId.substring(colon + 1);
            String base = "primary".equalsIgnoreCase(volume)
                    ? android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
                    : "/storage/" + volume;
            return rel.isEmpty() ? base : base + "/" + rel;
        } catch (Exception e) {
            return null;
        }
    }

    /** 文件型实在拿不到真实路径时，把内容复制进缓存目录、落一个真路径 */
    private static String copyToCache(Activity activity, Uri uri) {
        try (java.io.InputStream in = activity.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            java.io.File folder = new java.io.File(activity.getCacheDir(), "lab-var-files");
            folder.mkdirs();
            java.io.File out = new java.io.File(folder, "pick-" + System.currentTimeMillis());
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            }
            return out.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    /** 变量 filter → MIME：含「/」直接当 MIME；「.json」这类扩展名查小表；认不出不限制 */
    private static String mimeOfFilter(String filter) {
        if (filter == null || filter.trim().isEmpty()) return "*/*";
        String f = filter.trim().toLowerCase();
        if (f.contains("/")) return f;
        if (f.startsWith(".")) f = f.substring(1);
        switch (f) {
            case "json":
                return "application/json";
            case "txt":
            case "log":
            case "md":
                return "text/plain";
            case "zip":
                return "application/zip";
            case "7z":
                return "application/x-7z-compressed";
            case "js":
                return "text/javascript";
            case "py":
                return "text/x-python";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            default:
                return "*/*";
        }
    }

    private LabVarDialog() {
    }

    /**
     * @param title    标题（命令名/「包设置」）
     * @param vars     变量表
     * @param cached   已缓存的值（变量值复用）
     * @param okText   确认钮文案（「运行」/「保存」）
     */
    public static void show(Activity activity, String title, List<LabManifest.LabVar> vars,
                            Map<String, String> cached, String okText, Callback callback) {
        if (vars == null || vars.isEmpty()) {
            if (callback != null) callback.onConfirm(new HashMap<>());
            return;
        }
        int dp8 = LabSkin.dp(activity, 8);
        int dp16 = LabSkin.dp(activity, 16);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp16, dp16, dp16, dp16);
        scroll.addView(box, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        List<EditText> edits = new ArrayList<>();
        List<Switch> switches = new ArrayList<>();
        List<TextView> picks = new ArrayList<>();
        for (LabManifest.LabVar v : vars) {
            TextView label = new TextView(activity);
            label.setText(v.name + (v.required ? " *" : ""));
            label.setTextSize(14);
            label.setTextColor(LabSkin.TEXT_ON_DARK);
            label.getPaint().setFakeBoldText(true);
            box.addView(label);

            String type = v.type == null ? "text" : v.type;
            String cachedVal = cached == null ? null : cached.get(v.key);
            if ("switch".equals(type)) {
                Switch sw = new Switch(activity);
                boolean on = cachedVal != null ? !"false".equalsIgnoreCase(cachedVal)
                        : !"false".equalsIgnoreCase(v.defaultValue);
                sw.setChecked(on);
                // 上游 Setting.editable=false：只给看不给改（此前字段没人读，照样能改）
                if (Boolean.FALSE.equals(v.editable)) sw.setEnabled(false);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                slp.bottomMargin = dp16;
                box.addView(sw, slp);
                switches.add(sw);
                edits.add(null);
                picks.add(null);
                continue;
            }
            switches.add(null);

            if ("select".equals(type) && v.options != null && !v.options.isEmpty()) {
                TextView pick = new TextView(activity);
                pick.setTextSize(15);
                pick.setTextColor(LabSkin.TEXT_ON_DARK);
                pick.setBackground(LabSkin.dialogOption(activity));
                pick.setPadding(dp16, dp8, dp16, dp8);
                String cur = cachedVal != null ? cachedVal : v.defaultValue;
                pick.setText(labelOf(v, cur));
                pick.setTag(cur == null ? "" : cur);
                boolean pickEditable = !Boolean.FALSE.equals(v.editable);
                pick.setClickable(pickEditable);
                pick.setFocusable(pickEditable);
                pick.setEnabled(pickEditable);
                pick.setOnClickListener(vv -> {
                    String[] labels = new String[v.options.size()];
                    for (int i = 0; i < labels.length; i++) labels[i] = v.options.get(i).label;
                    LabInstallDialog.showChoice(activity,
                            v.name == null ? "选一个" : v.name, labels, which -> {
                                LabManifest.LabOption op = v.options.get(which);
                                pick.setText(op.label);
                                pick.setTag(op.value);
                            });
                });
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                plp.bottomMargin = dp16;
                box.addView(pick, plp);
                picks.add(pick);
                edits.add(null);
                continue;
            }
            picks.add(null);

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            EditText et = new EditText(activity);
            et.setTextSize(15);
            et.setTextColor(LabSkin.TEXT_ON_DARK);
            et.setHintTextColor(LabSkin.HINT_ON_DARK);
            et.setBackground(LabSkin.darkInput(activity));
            et.setPadding(dp16, dp8, dp16, dp8);
            if (v.hint != null) et.setHint(v.hint);
            String init = cachedVal != null ? cachedVal : (v.defaultValue == null ? "" : v.defaultValue);
            et.setText(init);
            if ("number".equals(type)) {
                et.setInputType(InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            } else if ("text_multiline".equals(type)) {
                et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                et.setMinLines(2);
                et.setMaxLines(5);
            } else {
                et.setInputType(InputType.TYPE_CLASS_TEXT);
            }
            if (Boolean.FALSE.equals(v.editable)) et.setEnabled(false);
            et.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(et);
            if ("file".equals(type) || "directory".equals(type)) {
                TextView sel = new TextView(activity);
                sel.setText("选");
                sel.setTextSize(14);
                sel.setTextColor(LabSkin.ON_YELLOW);
                sel.setBackground(LabSkin.buttonYellow(activity));
                sel.setPadding(dp16, dp8, dp16, dp8);
                boolean dir = "directory".equals(type);
                sel.setOnClickListener(vv -> {
                    pendingPick = new PickTarget();
                    pendingPick.edit = et;
                    pendingPick.dir = dir;
                    Intent i = dir
                            ? new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            : new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    if (!dir) {
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType(mimeOfFilter(v.filter));
                    }
                    try {
                        activity.startActivityForResult(i, REQ_VAR_PICK);
                    } catch (Exception e) {
                        Notify.show("打不开文件选择");
                    }
                });
                if (Boolean.FALSE.equals(v.editable)) sel.setEnabled(false);
                row.addView(sel);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp16;
            box.addView(row, rlp);
            edits.add(et);
        }

        LabInstallDialog.showForm(activity, title, scroll,
                okText == null ? "运行" : okText,
                () -> {
                    Map<String, String> values = new HashMap<>();
                    for (int i = 0; i < vars.size(); i++) {
                        LabManifest.LabVar var = vars.get(i);
                        Switch sw = switches.get(i);
                        if (sw != null) {
                            values.put(var.key, String.valueOf(sw.isChecked()));
                            continue;
                        }
                        TextView pick = picks.get(i);
                        if (pick != null) {
                            Object val = pick.getTag();
                            String s = val == null ? "" : String.valueOf(val);
                            if (var.required && s.isEmpty()) {
                                Notify.show("请选：" + var.name);
                                return false;
                            }
                            values.put(var.key, s);
                            continue;
                        }
                        EditText et = edits.get(i);
                        String text = et.getText() == null ? "" : et.getText().toString().trim();
                        String err = check(var, text);
                        if (err != null) {
                            Notify.show(err);
                            return false;
                        }
                        values.put(var.key, text);
                    }
                    if (callback != null) callback.onConfirm(values);
                    return true;
                }, null, null);
    }

    private static String labelOf(LabManifest.LabVar v, String value) {
        if (value != null && v.options != null) {
            for (LabManifest.LabOption op : v.options) {
                if (value.equals(op.value)) return op.label;
            }
        }
        return value == null ? "点我选择" : value;
    }

    /** 必填/min/max/数字校验，人话一句 */
    private static String check(LabManifest.LabVar var, String text) {
        if (var.required && TextUtils.isEmpty(text)) return "请填：" + var.name;
        if (TextUtils.isEmpty(text)) return null;
        if ("number".equals(var.type)) {
            double n;
            try {
                n = Double.parseDouble(text);
            } catch (Exception e) {
                return var.name + "得是数字";
            }
            if (var.min != null) {
                try {
                    if (n < Double.parseDouble(var.min)) return var.name + "不能小于 " + var.min;
                } catch (Exception ignored) {
                }
            }
            if (var.max != null) {
                try {
                    if (n > Double.parseDouble(var.max)) return var.name + "不能大于 " + var.max;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
