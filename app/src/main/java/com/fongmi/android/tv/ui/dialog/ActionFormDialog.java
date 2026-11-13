package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogActionFormBinding;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 动作协议表单/菜单的宿主渲染面（协议 E5/E6，参照缺位、按协议原文自建）：
 * type=input/multiInput 渲染输入框（带 selectData 或 id 为 file/path/dir/folder 的
 * 字段旁带「选择…」走 FileChooser，口径照 _____11 v191 收窄后的规则）；
 * type=menu 渲染选项行，点中把该行原文当动作发回（_fc_menu_pick 解析）。
 * 提交组包照 py 的 _pick_action_inputs 三式：input 数组回填 value、
 * 单输入走 value 纯文本。样式与点播域玻璃弹窗同一家。
 */
public class ActionFormDialog extends DialogFragment {

    // 提交回调按令牌寄存：表单本体走 arguments（重建可恢复），回调走进程内寄存表按令牌回查。
    // 纯字段传参在 DialogFragment 被系统重建（转屏/配置变化）时字段全丢、build 必崩——
    // 表单是数据、进 arguments；回调是行为、按令牌在同一进程内回查（进程被杀则表单随卡片重点重开）。
    private static final Map<String, Consumer<String>> PENDING = new ConcurrentHashMap<>();

    private DialogActionFormBinding binding;
    private JsonObject form;
    private Consumer<String> submit;
    private String token = "";
    private final List<Field> fields = new ArrayList<>();
    private Field picking;

    private static class Field {
        String id;
        EditText edit;
    }

    public static boolean show(FragmentActivity activity, JsonObject form, Consumer<String> submit) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || activity.getSupportFragmentManager().isStateSaved()) return false;
        ActionFormDialog dialog = new ActionFormDialog();
        dialog.form = form.deepCopy();
        dialog.submit = submit;
        dialog.token = UUID.randomUUID().toString();
        if (submit != null) PENDING.put(dialog.token, submit);
        Bundle args = new Bundle();
        args.putString("form", App.gson().toJson(form));
        args.putString("token", dialog.token);
        dialog.setArguments(args);
        dialog.show(activity.getSupportFragmentManager(), "action_form");
        return true;
    }

    private void restore() {
        if (form != null) return;
        Bundle args = getArguments();
        if (args == null) return;
        token = args.getString("token", "");
        String json = args.getString("form", "");
        if (!json.isEmpty()) {
            try {
                JsonElement element = JsonParser.parseString(json);
                if (element.isJsonObject()) form = element.getAsJsonObject();
            } catch (Exception ignored) {
            }
        }
        if (submit == null && !token.isEmpty()) submit = PENDING.get(token);
    }

    private void release() {
        if (!token.isEmpty()) PENDING.remove(token);
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (picking == null || picking.edit == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        String path = "";
        List<String> paths = FileChooser.getPathsFromIntent(result.getData());
        if (!paths.isEmpty()) path = paths.get(0);
        if (TextUtils.isEmpty(path) && result.getData().getData() != null) path = FileChooser.getPathFromUri(result.getData().getData());
        if (!TextUtils.isEmpty(path)) picking.edit.setText(path);
        picking = null;
    });

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        restore();
        binding = DialogActionFormBinding.inflate(LayoutInflater.from(requireActivity()));
        Dialog dialog = new Dialog(requireActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(binding.getRoot());
        if (form != null) build();
        return dialog;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (form == null) {
            dismissAllowingStateLoss();
            return;
        }
        Dialog dialog = getDialog();
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(requireActivity()) * (ResUtil.isLand(requireActivity()) ? 0.62f : 0.92f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
        window.setWindowAnimations(R.style.HkDialogFade);
    }

    private void build() {
        String type = str(form, "type");
        String title = str(form, "title");
        binding.actionFormTitle.setText(title.isEmpty() ? str(form, "actionId") : title);
        String tip = str(form, "tip");
        if (tip.isEmpty()) tip = str(form, "msg");
        if (!tip.isEmpty()) {
            binding.actionFormTip.setText(tip);
            binding.actionFormTip.setVisibility(View.VISIBLE);
        }
        if ("menu".equals(type)) buildMenu();
        else buildInputs(type);
    }

    private void buildMenu() {
        binding.actionFormButtons.setVisibility(View.GONE);
        JsonArray options = form.has("option") && form.get("option").isJsonArray() ? form.getAsJsonArray("option") : new JsonArray();
        TextView first = null;
        for (JsonElement element : options) {
            if (!element.isJsonPrimitive()) continue;
            String text = element.getAsString();
            TextView row = optionRow(text);
            row.setOnClickListener(v -> {
                if (submit != null) submit.accept(text);
                release();
                dismiss();
            });
            binding.actionFormFields.addView(row);
            if (first == null) first = row;
        }
        // TV 遥控：菜单行必须可聚焦，且打开时焦点先落在第一行。
        if (first != null) {
            TextView target = first;
            binding.getRoot().post(target::requestFocus);
        }
    }

    private void buildInputs(String type) {
        List<JsonObject> defs = new ArrayList<>();
        if (form.has("input") && form.get("input").isJsonArray()) {
            for (JsonElement element : form.getAsJsonArray("input")) if (element.isJsonObject()) defs.add(element.getAsJsonObject());
        }
        if (defs.isEmpty()) {
            JsonObject single = new JsonObject();
            single.addProperty("id", str(form, "id").isEmpty() ? "text" : str(form, "id"));
            if (form.has("value") && form.get("value").isJsonPrimitive()) single.addProperty("value", form.get("value").getAsString());
            defs.add(single);
        }
        for (JsonObject def : defs) binding.actionFormFields.addView(fieldRow(def));
        binding.actionFormCancel.setOnClickListener(v -> {
            release();
            dismiss();
        });
        binding.actionFormConfirm.setOnClickListener(v -> onConfirm(defs));
        if (!fields.isEmpty() && fields.get(0).edit != null) fields.get(0).edit.requestFocus();
    }

    private View fieldRow(JsonObject def) {
        Field field = new Field();
        field.id = str(def, "id");
        LinearLayout box = new LinearLayout(requireActivity());
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxParams.topMargin = ResUtil.dp2px(12);
        box.setLayoutParams(boxParams);
        String label = str(def, "name");
        if (label.isEmpty()) label = str(def, "tip");
        if (label.isEmpty()) label = field.id;
        TextView name = new TextView(requireActivity());
        name.setText(label);
        name.setTextColor(0xB3000000);
        name.setTextSize(13);
        box.addView(name);
        LinearLayout row = new LinearLayout(requireActivity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = ResUtil.dp2px(6);
        row.setLayoutParams(rowParams);
        EditText edit = new EditText(requireActivity());
        edit.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        edit.setBackgroundResource(R.drawable.shape_shield_webview_frame);
        edit.setPadding(ResUtil.dp2px(14), ResUtil.dp2px(12), ResUtil.dp2px(14), ResUtil.dp2px(12));
        edit.setSingleLine(true);
        edit.setTextColor(0xB3000000);
        edit.setHintTextColor(0x80000000);
        edit.setTextSize(14);
        if (def.has("value") && def.get("value").isJsonPrimitive()) edit.setText(def.get("value").getAsString());
        String tip = str(def, "tip");
        if (!tip.isEmpty() && !tip.equals(label)) edit.setHint(tip);
        field.edit = edit;
        row.addView(edit);
        if (isFileField(def)) {
            TextView pick = new TextView(requireActivity());
            LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            pickParams.leftMargin = ResUtil.dp2px(8);
            pick.setLayoutParams(pickParams);
            pick.setBackgroundResource(R.drawable.shape_shield_btn_ghost);
            pick.setFocusable(true);
            pick.setGravity(Gravity.CENTER);
            pick.setPadding(ResUtil.dp2px(14), 0, ResUtil.dp2px(14), 0);
            pick.setText(R.string.action_form_pick);
            pick.setTextColor(0xB3000000);
            pick.setTextSize(14);
            pick.setOnClickListener(v -> {
                picking = field;
                if (isDirectoryField(def)) FileChooser.from(launcher).showDirectory();
                else FileChooser.from(launcher).show();
            });
            row.addView(pick);
        }
        box.addView(row);
        fields.add(field);
        return box;
    }

    private TextView optionRow(String text) {
        TextView row = new TextView(requireActivity());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = ResUtil.dp2px(10);
        row.setLayoutParams(params);
        row.setBackgroundResource(R.drawable.shape_shield_btn_ghost);
        row.setFocusable(true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ResUtil.dp2px(14), ResUtil.dp2px(12), ResUtil.dp2px(14), ResUtil.dp2px(12));
        row.setText(text);
        row.setTextColor(0xB3000000);
        row.setTextSize(14);
        return row;
    }

    // v191 收窄口径：只有带 selectData、或 id 就是 file/path/dir/folder 的字段才给选文件钮，
    // 名称/存放路径/封面这类靠名字像的字段不给。
    private boolean isFileField(JsonObject def) {
        if (def.has("selectData")) return true;
        String id = str(def, "id");
        return "file".equals(id) || "path".equals(id) || "dir".equals(id) || "folder".equals(id);
    }

    // 目录还是文件，看 selectData 的内容分流（协议实存形态：'path' 字段带 '[folder]'
    // 是选文件夹、'file' 字段带 '[file]' 是选文件），id 本身是 dir/folder 的恒为目录。
    private boolean isDirectoryField(JsonObject def) {
        String id = str(def, "id");
        if ("dir".equals(id) || "folder".equals(id)) return true;
        return str(def, "selectData").contains("folder");
    }

    private void onConfirm(List<JsonObject> defs) {
        JsonObject out = form.deepCopy();
        boolean hasArray = out.has("input") && out.get("input").isJsonArray();
        if (hasArray) {
            JsonArray array = out.getAsJsonArray("input");
            for (Field field : fields) {
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) continue;
                    JsonObject item = element.getAsJsonObject();
                    if (field.id.equals(str(item, "id"))) item.addProperty("value", field.edit.getText().toString());
                }
            }
        } else if (!fields.isEmpty()) {
            out.addProperty("value", fields.get(0).edit.getText().toString());
        }
        if (submit != null) submit.accept(App.gson().toJson(out));
        release();
        dismiss();
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) return "";
        return obj.get(key).getAsString();
    }
}
