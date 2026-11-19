package com.fongmi.android.tv.ui;

import android.app.Activity;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
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
 * 协议出处见 worknotes/py11-action-ref-ledger-20261111.md；OK影视/ys 形
 * （menu 对象选项、提交键 action、msgbox 的 msg 正文、webview 指令与卡、
 * msgbox 卡、__refresh_list__ 刷新指令）的扩形出处见
 * worknotes/tgdrama-xingchen-gap-20261118.md 补查二。两形并存、按数据
 * 形态分发，不按来源 py 分发。
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
        if ("webview".equals(type)) {
            // ys 形开页指令：与 browser 同为「宿主开页」，载体同是咱家浏览器面。
            String url = str(command, "url");
            return !url.isEmpty() && WebActivity.open(url);
        }
        if ("msgbox".equals(type)) {
            // 正文两形：htmlMsg 是 HTML（自家形），msg 是纯文本（ys 形）；图位同理。
            String html = str(command, "htmlMsg");
            boolean isHtml = !html.isEmpty();
            String body = isHtml ? html : str(command, "msg");
            // 带 actionId 的 msgbox 是流程中间步（扫码图待确认、待重试）：确定
            // 关窗时把 actionId 按提交形回派蜘蛛续办，走与表单提交同一条路
            // （host.onActionSubmit）；无 actionId 的是终局提示，只关窗不回派。
            String actionId = str(command, "actionId");
            Runnable onConfirm = !actionId.isEmpty() && host != null ? () -> host.onActionSubmit(msgboxConfirmPackage(actionId)) : null;
            return ActionMsgDialog.show(activity, str(command, "title"), body, isHtml, str(command, "imageUrl"), num(command, "imageHeight"), onConfirm);
        }
        if ("input".equals(type) || "multiInput".equals(type) || "menu".equals(type)) {
            if (host == null || !(activity instanceof FragmentActivity)) return false;
            return ActionFormDialog.show((FragmentActivity) activity, command, host::onActionSubmit);
        }
        if ("__refresh_list__".equals(str(command, "actionId"))) {
            // ys 形刷新指令：无 type 的纯刷新命令，语义就是当前页整页重拉。
            if (host != null) host.onActionRefresh();
            return true;
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
     * 卡片级拦截：被点的 Vod 若 vod_id 是 JSON 动作卡配置（带 actionId），
     * 按 type 在宿主侧直接办：
     * - input/multiInput、带可渲染选项的 menu：宿主渲染表单，提交后由
     *   dispatcher 把组好的 JSON（或菜单选中行原文）发回 action()；
     * - webview：卡面自带 url，宿主直接开页；
     * - msgbox：卡面自带标题与正文，宿主直接弹框；确定关窗时把卡的
     *   actionId 按提交形经 dispatcher 回派蜘蛛续办（无 dispatcher 只弹框）。
     * menu 无任何可渲染选项时不是表单（只剩裸 action 引用），不拦截，
     * 点击照常走 action(裸 actionId) 分发。browser 卡不在此列（走 action
     * 分发＋指令消费，宿主只是最后一级）。
     * 返回 true 表示已接管这次点击。
     */
    public static boolean handleCard(Activity activity, Vod item, Consumer<String> dispatcher) {
        JsonObject form = parseForm(item);
        if (form == null) return false;
        String type = str(form, "type");
        if ("webview".equals(type)) {
            String url = str(form, "url");
            return !url.isEmpty() && WebActivity.open(url);
        }
        if ("msgbox".equals(type)) {
            String html = str(form, "htmlMsg");
            boolean isHtml = !html.isEmpty();
            String body = isHtml ? html : str(form, "msg");
            String actionId = str(form, "actionId");
            Runnable onConfirm = dispatcher != null && !actionId.isEmpty() ? () -> dispatcher.accept(msgboxConfirmPackage(actionId)) : null;
            return ActionMsgDialog.show(activity, str(form, "title"), body, isHtml, str(form, "imageUrl"), num(form, "imageHeight"), onConfirm);
        }
        if (!(activity instanceof FragmentActivity)) return false;
        return ActionFormDialog.show((FragmentActivity) activity, form, dispatcher);
    }

    /**
     * msgbox 确定回派包，与菜单提交同形：{"action": actionId, "value": {}}。
     * 键名认 action（ys 提交形与自家 _action_impl 都先读 action 键）；
     * msgbox 没有选项/输入可载，value 为空对象（蜘蛛 action() 取 value
     * 缺省即空 dict，两形等价）。回派结果照常走 consume 递归消费。
     */
    private static String msgboxConfirmPackage(String actionId) {
        JsonObject pkg = new JsonObject();
        pkg.addProperty("action", actionId);
        pkg.add("value", new JsonObject());
        return App.gson().toJson(pkg);
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
            boolean known = "input".equals(type) || "multiInput".equals(type) || "menu".equals(type)
                    || "webview".equals(type) || "msgbox".equals(type);
            if (!known) return null;
            if (str(obj, "actionId").isEmpty()) return null;
            // menu 的结构内容就是它的选项：一个选项都没有的 menu 不是表单，
            // 是裸 action 引用（卡的 action 字段自带 id），归还给 action 分发。
            if ("menu".equals(type) && !ActionFormDialog.hasRenderableOption(obj)) return null;
            if ("webview".equals(type) && str(obj, "url").isEmpty()) return null;
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    private static int num(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) return 0;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) return "";
        return obj.get(key).getAsString();
    }
}
