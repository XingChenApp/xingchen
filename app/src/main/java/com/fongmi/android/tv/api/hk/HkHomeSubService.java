package com.fongmi.android.tv.api.hk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.ToastUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 首页订阅服务（移植自 8.83 HomeRulesSubService）。
 * 8.83 原文：com/example/hikerview/ui/rules/service/HomeRulesSubService.smali
 * 8.83 用 BigTextDO.getHomeSub() 存 JSON；星辰用 SharedPreferences 存，语义一致。
 * <p>
 * 环39 执行面（硬门账 D 环39 §6 落码边界，只落执行面、管理 UI 不落）：
 * 记录写入口（add/remove/update，校验与 toast 文案逐字照官）、checkUpdate
 * 全链（三闸→@js= 支四出口→拉取→合入三态→删除支→成败回写）、history
 * 账本（官文件 rules/cache/sub_history.txt 对位咱家同相对路径）、
 * checkUpdateAsync/batchCheckUpdate 异步形与两事件、冷启触发口在
 * HkPageActivity（宿主 init 后延迟 7s 一轮，进程内只排一次）。
 * 合入落盘走 HkRuleManager 既有 saveRule/delete 口，不另造规则存储；
 * 删除支经 HkRuleManager.delete 会连规则数据目录一并清（咱家 delete 的
 * 既定语义，与官 LitePal rule.delete() 只删行有载体差，记名）。
 */
public class HkHomeSubService {

    private static final String PREFS = "hk_home_sub";
    private static final String KEY_SUB_RECORDS = "sub_records";
    /** 官 PreferenceMgr 文件 "subscribe"／键 homeRuleSubSilence 的咱家对位。 */
    private static final String PREFS_SUBSCRIBE = "hk_subscribe";
    private static final String KEY_SUB_SILENCE = "homeRuleSubSilence";
    private static final long DAY_MS = 0x5265c00L;
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = TypeToken.getParameterized(List.class, HkSubscribeRecord.class).getType();
    private static final Type HISTORY_LIST_TYPE = TypeToken.getParameterized(List.class, HkSubscribeHistory.class).getType();

    private static Context appContext;

    public static void init(Context context) {
        if (context != null) appContext = context.getApplicationContext();
    }

    private static Context ctx() {
        if (appContext != null) return appContext;
        try {
            return App.get();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void toast(String msg) {
        try {
            new Handler(Looper.getMainLooper()).post(() -> {
                Context c = ctx();
                if (c != null) ToastUtil.show(c, msg, Toast.LENGTH_SHORT);
            });
        } catch (Throwable ignored) {
        }
    }

    /**
     * 8.83 原文（HomeRulesSubService.smali:1207）：
     *   String json = BigTextDO.getHomeSub();
     *   if (isEmpty(json)) return new ArrayList<>();
     *   return JSON.parseArray(json, SubscribeRecord.class);
     */
    public static List<HkSubscribeRecord> getSubRecords() {
        try {
            Context c = ctx();
            if (c == null) return new ArrayList<>();
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String json = sp.getString(KEY_SUB_RECORDS, "");
            if (TextUtils.isEmpty(json)) return new ArrayList<>();
            List<HkSubscribeRecord> list = GSON.fromJson(json, LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    /** 保存订阅记录（整表写回，官 updateHomeSub 同形） */
    public static void saveSubRecords(List<HkSubscribeRecord> records) {
        try {
            Context c = ctx();
            if (c == null) return;
            String json = GSON.toJson(records != null ? records : new ArrayList<>());
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_SUB_RECORDS, json).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 8.83 hasHomeSub 语义：url 为空返回 "false"；遍历记录比对 url，命中返回 "true" */
    public static String hasHomeSub(String url) {
        if (TextUtils.isEmpty(url)) return "false";
        try {
            for (HkSubscribeRecord r : getSubRecords()) {
                if (r != null && url.equals(r.getUrl())) return "true";
            }
        } catch (Throwable ignored) {
        }
        return "false";
    }

    // ================= 写入口（官 SubSvc:20/:1403/:1562） =================

    /**
     * 官 addSubRecords(ctx, title, url, use, onlyUpdateFlag) 转写：
     * 校验五支（空名/空址/格式/重名/重址）逐支 toast 照官原文、回 false；
     * 通过后落表（onlyUpdate = !onlyUpdateFlag）并立即 checkUpdateAsync。
     */
    public static boolean addSubRecords(Context context, String title, String url,
                                        boolean use, boolean onlyUpdateFlag) {
        if (TextUtils.isEmpty(title)) {
            toast("订阅名称不能为空");
            return false;
        }
        if (TextUtils.isEmpty(url)) {
            toast("订阅地址不能为空");
            return false;
        }
        if (!url.toLowerCase(Locale.ROOT).startsWith("http") && !url.contains("@js=")) {
            toast("订阅地址格式有误");
            return false;
        }
        List<HkSubscribeRecord> records = getSubRecords();
        for (HkSubscribeRecord r : records) {
            if (r == null) continue;
            if (title.equals(r.getTitle())) {
                toast("订阅名称已存在");
                return false;
            }
            if (url.equals(r.getUrl())) {
                toast("订阅地址已存在");
                return false;
            }
        }
        HkSubscribeRecord record = new HkSubscribeRecord();
        record.setTitle(title);
        record.setUrl(url);
        record.setCreateDate(new Date());
        record.setUse(use);
        record.setOnlyUpdate(!onlyUpdateFlag);
        records.add(record);
        saveSubRecords(records);
        checkUpdateAsync(record);
        return true;
    }

    /** 官 removeSubRecords(title) 转写：按 title 删一条即整表写回。 */
    public static void removeSubRecords(String title) {
        if (title == null) return;
        List<HkSubscribeRecord> records = getSubRecords();
        for (int i = 0; i < records.size(); i++) {
            HkSubscribeRecord r = records.get(i);
            if (r != null && title.equals(r.getTitle())) {
                records.remove(i);
                saveSubRecords(records);
                return;
            }
        }
    }

    /** 官 updateSubRecords(record) 转写：按 title 定位替换同位、写回。 */
    public static void updateSubRecords(HkSubscribeRecord record) {
        if (record == null || record.getTitle() == null) return;
        List<HkSubscribeRecord> records = getSubRecords();
        for (int i = 0; i < records.size(); i++) {
            HkSubscribeRecord r = records.get(i);
            if (r != null && record.getTitle().equals(r.getTitle())) {
                records.set(i, record);
                saveSubRecords(records);
                return;
            }
        }
    }

    // ================= history 账本（官 SubSvc:1083/:1144） =================

    /** 官 getHistoryFilePath：&lt;根&gt;/rules/cache/sub_history.txt，咱家经 getFilePath 同相对路径对位。 */
    static File historyFile() {
        return new File(HkJsRuntime.getFilePath("hiker://files/rules/cache/sub_history.txt"));
    }

    /** 官 getHistories 转写：文件不存在/为空回空表，余 parseArray 读。 */
    public static List<HkSubscribeHistory> getHistories() {
        try {
            File f = historyFile();
            if (!f.exists()) return new ArrayList<>();
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (json == null || json.trim().isEmpty()) return new ArrayList<>();
            List<HkSubscribeHistory> list = GSON.fromJson(json, HISTORY_LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    /** 官 saveHistories 转写：整表 JSON 写账本文件。 */
    public static void saveHistories(List<HkSubscribeHistory> histories) {
        try {
            File f = historyFile();
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            String json = GSON.toJson(histories != null ? histories : new ArrayList<>());
            Files.write(f.toPath(), json.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    // ================= 执行面（官 SubSvc:273 checkUpdate） =================

    /**
     * checkUpdate 的宿主接缝（官本体直调的四面：已装规则表／拉取／JS 求值／
     * 账本与记录回写）。生产实现走 HkRuleManager/HkHttp/引擎单例，离线
     * 夹具以内存实现注入同一本体验合入三态与三闸。
     */
    interface SubEnv {
        List<HkRule> installedRules();

        void saveRule(HkRule rule, String rawJson) throws Exception;

        void deleteRule(String title);

        String fetch(String url) throws Exception;

        String evalJs(String script, String input);

        void toast(String msg);

        long now();

        List<HkSubscribeHistory> loadHistories();

        void saveHistories(List<HkSubscribeHistory> histories);

        void updateRecord(HkSubscribeRecord record);
    }

    private static SubEnv defaultEnv() {
        return new SubEnv() {
            @Override
            public List<HkRule> installedRules() {
                return HkRuleManager.get().getRules();
            }

            @Override
            public void saveRule(HkRule rule, String rawJson) throws Exception {
                HkRuleManager.get().saveRule(rule, rawJson);
            }

            @Override
            public void deleteRule(String title) {
                HkRuleManager.get().delete(title);
            }

            @Override
            public String fetch(String url) throws Exception {
                return HkHttp.get(url, null);
            }

            @Override
            public String evalJs(String script, String input) {
                HkJsRuntime rt = HkJsRuntime.imageEngine();
                if (rt == null) return null;
                return rt.evalJS(script, input);
            }

            @Override
            public void toast(String msg) {
                HkHomeSubService.toast(msg);
            }

            @Override
            public long now() {
                return System.currentTimeMillis();
            }

            @Override
            public List<HkSubscribeHistory> loadHistories() {
                return HkHomeSubService.getHistories();
            }

            @Override
            public void saveHistories(List<HkSubscribeHistory> histories) {
                HkHomeSubService.saveHistories(histories);
            }

            @Override
            public void updateRecord(HkSubscribeRecord record) {
                HkHomeSubService.updateSubRecords(record);
            }
        };
    }

    /** 官 FilterUtil.hasFilterWord(String)：硬编码恒 false 的桩（smali 亲验，环27/Dlan 账同判）。 */
    static boolean hasFilterWord(String body) {
        return false;
    }

    /** 官 subUpdateSuccess（SubSvc:1533）：回写成功态并整表落盘。 */
    private static void subUpdateSuccess(HkSubscribeRecord record, int count, SubEnv env) {
        record.setModifyDate(new Date(env.now()));
        record.setErrorCount(0);
        record.setLastUpdateCount(count);
        record.setLastUpdateSuccess(true);
        env.updateRecord(record);
    }

    /** 官 subUpdateError（SubSvc:1500）：失败也吃 24h 闸（modifyDate 同步更新）。 */
    private static void subUpdateError(HkSubscribeRecord record, SubEnv env) {
        record.setModifyDate(new Date(env.now()));
        record.setErrorCount(record.getErrorCount() + 1);
        record.setLastUpdateCount(0);
        record.setLastUpdateSuccess(false);
        env.updateRecord(record);
    }

    /** 官 checkUpdate(record) 转写：返回本次变更数。落码级逐支见硬门账 D 环39 §1。 */
    public static int checkUpdate(HkSubscribeRecord record) {
        return checkUpdate(record, defaultEnv());
    }

    static int checkUpdate(HkSubscribeRecord record, SubEnv env) {
        if (record == null) return 0;
        // 前置三闸（官序）：未启用／连续失败 >10／24h 内已检。
        if (!record.isUse()) return 0;
        if (record.getErrorCount() > 10) return 0;
        Date modifyDate = record.getModifyDate();
        if (modifyDate != null && env.now() - modifyDate.getTime() < DAY_MS) return 0;

        String url = record.getUrl();
        // @js= 支：[0]=input、余段以 @js= 拼回为脚本，经引擎单例 evalJS。
        if (url != null && url.contains("@js=")) {
            String[] parts = url.split("@js=", -1);
            String input = parts[0];
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) sb.append("@js=");
                sb.append(parts[i]);
            }
            String result;
            try {
                result = env.evalJs(sb.toString(), input);
            } catch (Throwable t) {
                subUpdateError(record, env);
                return 0;
            }
            if (result == null || result.isEmpty() || result.startsWith("error:")) {
                subUpdateError(record, env);
                return 0;
            }
            if (result.startsWith("toast://")) {
                env.toast(result.substring("toast://".length()));
                subUpdateSuccess(record, 0, env);
                return 0;
            }
            if (result.startsWith("http://") || result.startsWith("https://")) {
                url = result;
            } else {
                subUpdateSuccess(record, 0, env);
                return 0;
            }
        }

        // 拉取支：已装表与账本先备，拉取/空体/过滤词/解析为空统一走失败支。
        Map<String, HkRule> installed = new HashMap<>();
        for (HkRule r : env.installedRules()) {
            if (r != null) installed.put(r.getTitle(), r);
        }
        Map<String, HkSubscribeHistory> histories = new HashMap<>();
        for (HkSubscribeHistory h : env.loadHistories()) {
            if (h != null && h.getTitle() != null) histories.put(h.getTitle(), h);
        }
        String body;
        try {
            body = env.fetch(url);
        } catch (Throwable t) {
            subUpdateError(record, env);
            return 0;
        }
        if (body == null || body.isEmpty() || hasFilterWord(body)) {
            subUpdateError(record, env);
            return 0;
        }
        List<JsonObject> joObjects = new ArrayList<>();
        List<HkRule> joRules = new ArrayList<>();
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonArray()) throw new IllegalArgumentException("not array");
            JsonArray arr = parsed.getAsJsonArray();
            for (JsonElement el : arr) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                HkRule jo = GSON.fromJson(obj, HkRule.class);
                if (jo == null) continue;
                joObjects.add(obj);
                joRules.add(jo);
            }
        } catch (Throwable t) {
            subUpdateError(record, env);
            return 0;
        }
        if (joRules.isEmpty()) {
            subUpdateError(record, env);
            return 0;
        }

        // 逐条合入（官序）：rulesCount 先记拉到条数。
        int changed = 0;
        record.setRulesCount(joRules.size());
        Set<String> fetchedTitles = new HashSet<>();
        for (int i = 0; i < joRules.size(); i++) {
            HkRule jo = joRules.get(i);
            JsonObject obj = joObjects.get(i);
            String title = jo.getTitle();
            fetchedTitles.add(title);
            if (title == null || title.isEmpty()) continue;
            int joVersion = jo.getVersion();
            HkRule existing = installed.get(title);
            if (existing != null) {
                // 账本先行：无则新建、有则更新 sub/version（跳过与否都记）。
                HkSubscribeHistory h = histories.get(title);
                if (h == null) {
                    histories.put(title, new HkSubscribeHistory(title, joVersion, record.getTitle()));
                } else {
                    h.setSub(record.getTitle());
                    h.setVersion(joVersion);
                }
                if (existing.getVersion() >= joVersion) continue;
                changed++;
                // 保留旧 group 后覆盖：group 写回 JO 对象再落盘，其余字段照 JO。
                obj.addProperty("group", existing.getGroup());
                HkRule merged = GSON.fromJson(obj, HkRule.class);
                try {
                    env.saveRule(merged, obj.toString());
                    installed.put(title, merged);
                } catch (Throwable ignored) {
                }
            } else {
                if (record.isOnlyUpdate()) continue;
                HkSubscribeHistory h = histories.get(title);
                if (h != null && h.getVersion() >= joVersion) continue;
                changed++;
                try {
                    env.saveRule(jo, obj.toString());
                    installed.put(title, jo);
                } catch (Throwable ignored) {
                }
                if (h == null) {
                    histories.put(title, new HkSubscribeHistory(title, joVersion, record.getTitle()));
                } else {
                    h.setSub(record.getTitle());
                    h.setVersion(joVersion);
                }
            }
        }

        // 删除支（仅 !onlyUpdate）：账本里本订阅名下、本次未拉到、已装 version
        // 与账本 version 全等（未被其他来源改过）才删规则并摘账本。
        if (!record.isOnlyUpdate()) {
            List<String> removeTitles = new ArrayList<>();
            for (HkSubscribeHistory h : histories.values()) {
                if (!record.getTitle().equals(h.getSub())) continue;
                if (fetchedTitles.contains(h.getTitle())) continue;
                HkRule existing = installed.get(h.getTitle());
                if (existing == null) continue;
                if (existing.getVersion() != h.getVersion()) continue;
                env.deleteRule(h.getTitle());
                installed.remove(h.getTitle());
                removeTitles.add(h.getTitle());
                changed++;
            }
            for (String t : removeTitles) histories.remove(t);
        }

        subUpdateSuccess(record, changed, env);
        env.saveHistories(new ArrayList<>(histories.values()));
        return changed;
    }

    /** 官 checkUpdateAsync（SubSvc:1070）：后台跑单条，变更>0 发规则变更事件，恒发订阅刷新事件。 */
    public static void checkUpdateAsync(HkSubscribeRecord record) {
        com.fongmi.android.tv.utils.Task.execute(() -> {
            int changed = checkUpdate(record);
            if (changed > 0) {
                EventBus.getDefault().post(new com.fongmi.android.tv.api.hk.event.rule.OnArticleListRuleChangedEvent(changed));
                notifyRulesChanged(changed);
            }
            EventBus.getDefault().post(new com.fongmi.android.tv.api.hk.event.rule.OnSubRefreshEvent());
        });
    }

    /** 官 batchCheckUpdate（SubSvc:260）：后台逐条累加，收尾同单条口径。 */
    public static void batchCheckUpdate() {
        com.fongmi.android.tv.utils.Task.execute(() -> {
            int total = 0;
            for (HkSubscribeRecord record : getSubRecords()) {
                total += checkUpdate(record);
            }
            if (total > 0) {
                EventBus.getDefault().post(new com.fongmi.android.tv.api.hk.event.rule.OnArticleListRuleChangedEvent(total));
                notifyRulesChanged(total);
            }
            EventBus.getDefault().post(new com.fongmi.android.tv.api.hk.event.rule.OnSubRefreshEvent());
        });
    }

    /**
     * 官 MainActivity 消费 OnArticleListRuleChangedEvent 的提示半
     * （lambda$onArticleListRuleChanged$139，MainActivity.smali:9250 区）：
     * updateFromSub&gt;0 且非静默偏好（subscribe/homeRuleSubSilence）才
     * Toast 官原文「从远程订阅更新N条规则」（旧形自造文案「有N个规则
     * 更新了」非官原文，C1 复核订正）；官同支另有 isOnPause 不弹之门，
     * 咱家无 MainActivity 对位消费方、本面是发送侧承接、无宿主暂停态可挂，
     * 记名不硬造；列表刷新半由 HkRuleManager.saveStamp 既有感知面承接。
     */
    private static void notifyRulesChanged(int count) {
        if (isSubSilence()) return;
        toast("从远程订阅更新" + count + "条规则");
    }

    /** 静默开关（官 PreferenceMgr subscribe/homeRuleSubSilence 对位）。 */
    public static boolean isSubSilence() {
        try {
            Context c = ctx();
            if (c == null) return false;
            return c.getSharedPreferences(PREFS_SUBSCRIBE, Context.MODE_PRIVATE)
                    .getBoolean(KEY_SUB_SILENCE, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setSubSilence(boolean silence) {
        try {
            Context c = ctx();
            if (c == null) return;
            c.getSharedPreferences(PREFS_SUBSCRIBE, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_SUB_SILENCE, silence).apply();
        } catch (Throwable ignored) {
        }
    }
}
