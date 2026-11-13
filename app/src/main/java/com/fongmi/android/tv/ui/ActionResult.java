package com.fongmi.android.tv.ui;

import android.app.Activity;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.ui.activity.WebActivity;
import com.fongmi.android.tv.ui.dialog.ActionFormDialog;
import com.fongmi.android.tv.ui.dialog.ActionMsgDialog;
import com.fongmi.android.tv.utils.Notify;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.function.Consumer;

/**
 * 动作结果的统一消费口：所有 getAction() 观察者都走这里（地板观察口是
 * WebActivity.handleAction，只消费 web+msg；动作协议其余键在此补齐，
 * 顺序＝指令/表单 → 整页 → refresh → toast → handleAction 地板口）。
 * 协议出处见 worknotes/py11-action-ref-ledger-20261111.md。
 */
public final class ActionResult {

    /** 页面落点：TypeFragment 两味有整页渲染面；搜索/首页无落点时空实现（照地板口径）。 */
    public interface Host {
        void onActionPage(Result result);

        void onActionRefresh();

        void onActionSubmit(String action);
    }

    private ActionResult() {
    }

    public static void consume(Activity activity, Result result, Host host) {
        if (activity == null || result == null) return;
        JsonObject command = result.getAction() != null ? result.getAction() : result.getActionForm();
        if (command != null && consumeCommand(activity, command, host)) {
            if (result.hasToast()) Notify.show(result.getToast());
            return;
        }
        if (!result.getList().isEmpty()) {
            if (host != null) host.onActionPage(result);
        } else if (result.isRefresh()) {
            if (host != null) host.onActionRefresh();
        }
        if (result.hasToast()) Notify.show(result.getToast());
        WebActivity.handleAction(activity, result);
    }

    private static boolean consumeCommand(Activity activity, JsonObject command, Host host) {
        String type = str(command, "type");
        if ("browser".equals(type)) {
            String url = str(command, "url");
            // 协议 E4：宿主开浏览器是指令的最后一级，载体是咱家浏览器面。
            return !url.isEmpty() && WebActivity.open(url);
        }
        if ("msgbox".equals(type)) {
            return ActionMsgDialog.show(activity, str(command, "title"), str(command, "htmlMsg"));
        }
        if ("input".equals(type) || "multiInput".equals(type) || "menu".equals(type)) {
            if (host == null || !(activity instanceof FragmentActivity)) return false;
            return ActionFormDialog.show((FragmentActivity) activity, command, host::onActionSubmit);
        }
        if ("toast".equals(type) || "toast".equals(str(command, "actionId"))) {
            Notify.show(str(command, "msg"));
            return true;
        }
        // 通知形指令的通式（协议实存形态，如 {"action":{"actionId":"重命名成功","msg":…}}）：
        // actionId 是业务文案、msg 才是要给用户看的话——带 msg 的未知指令按提示消费。
        String msg = str(command, "msg");
        if (!msg.isEmpty()) {
            Notify.show(msg);
            return true;
        }
        return false;
    }

    /**
     * 卡片级表单拦截：被点的 Vod 若 vod_id 是 JSON 表单配置
     * （带 actionId 且 type∈input/multiInput/menu），先由宿主渲染表单，
     * 提交后由 dispatcher 把组好的 JSON（或菜单选中行原文）发回 action()。
     * browser 卡不在此列（走 action 分发＋指令消费，宿主只是最后一级）。
     * 返回 true 表示已接管这次点击。
     */
    public static boolean handleCard(Activity activity, Vod item, Consumer<String> dispatcher) {
        JsonObject form = parseForm(item);
        if (form == null || !(activity instanceof FragmentActivity)) return false;
        return ActionFormDialog.show((FragmentActivity) activity, form, dispatcher);
    }

    private static JsonObject parseForm(Vod item) {
        if (item == null || item.getId() == null) return null;
        String id = item.getId().trim();
        if (!id.startsWith("{")) return null;
        try {
            JsonElement element = JsonParser.parseString(id);
            if (!element.isJsonObject()) return null;
            JsonObject obj = element.getAsJsonObject();
            String type = str(obj, "type");
            if (!"input".equals(type) && !"multiInput".equals(type) && !"menu".equals(type)) return null;
            if (str(obj, "actionId").isEmpty()) return null;
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) return "";
        return obj.get(key).getAsString();
    }
}
