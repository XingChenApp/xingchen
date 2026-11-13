package com.fongmi.android.tv.remote;

import android.content.Context;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.remote.RemoteModels.ClaimResponse;
import com.fongmi.android.tv.remote.RemoteModels.RemoteBindGrant;
import com.fongmi.android.tv.remote.RemoteModels.RemoteDevice;
import com.fongmi.android.tv.remote.RemoteModels.RemoteGroup;
import com.fongmi.android.tv.remote.RemoteModels.RemoteProfile;
import com.fongmi.android.tv.gitcloud.secure.GitCloudTokenStore;
import com.fongmi.android.tv.remote.RemoteModels.RemoteStoreFile;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class RemoteStore {

    private static final String KEY_STORE = "remote_trust_store";
    private static final String FILE_STORE = "WebHTV/RemoteTrust/profile.json";
    private static final long MAX_STORE_FILE_SIZE = 2L * 1024 * 1024;

    private static RemoteStoreFile cache;
    private static boolean loaded;

    private RemoteStore() {
    }

    public static synchronized RemoteStoreFile get() {
        if (!loaded) reloadFromFile();
        return ensure(cache);
    }

    public static synchronized void reloadFromFile() {
        loaded = true;
        RemoteStoreFile store = null;
        boolean[] migrated = new boolean[1];
        boolean fromFile = false;
        if (Setting.hasFileAccess()) {
            File file = file();
            if (file.exists() && file.length() > 0) {
                if (file.length() <= MAX_STORE_FILE_SIZE) {
                    store = parse(Path.read(file), migrated);
                    if (store != null) {
                        fromFile = true;
                        Prefers.put(KEY_STORE, persistJson(ensure(store)));
                    }
                } else {
                    log("skip oversized profile file size=%d", file.length());
                }
            }
        }
        if (store == null) {
            migrated[0] = false;
            store = parse(Prefers.getString(KEY_STORE), migrated);
            // Prefers 路径真发生了迁移才回写剥离版一次；没迁移的读不写盘。
            if (store != null && migrated[0]) {
                try {
                    Prefers.put(KEY_STORE, persistJson(ensure(store)));
                } catch (Throwable e) {
                    SpiderDebug.log("remote", "rewrite stripped prefs failed error=%s", e.getMessage());
                }
            }
        }
        cache = ensure(store);
        // 文件路径：Prefers 已在上面回写剥离版，profile.json 文件本身也要回写，
        // 不让老明文躺在文件里到下一次 save()。回写失败只留痕，不影响读结果。
        if (fromFile && migrated[0]) {
            try {
                String json = persistJson(cache);
                File target = file();
                File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
                Path.write(tmp, json.getBytes(StandardCharsets.UTF_8));
                if (!tmp.renameTo(target)) Path.move(tmp, target);
            } catch (Throwable e) {
                SpiderDebug.log("remote", "rewrite stripped profile file failed error=%s", e.getMessage());
            }
        }
    }

    public static synchronized void save(RemoteStoreFile store) {
        cache = ensure(store);
        String json = persistJson(cache);
        Prefers.put(KEY_STORE, json);
        if (!Setting.hasFileAccess()) return;
        try {
            File target = file();
            File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
            Path.write(tmp, json.getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(target)) Path.move(tmp, target);
        } catch (Throwable e) {
            log("save profile file failed error=%s", e.getMessage());
        }
    }

    public static synchronized RemoteProfile prepareProfile(String serverUrl, boolean enabled, boolean keepOnline) {
        String origin = RemoteTokens.normalizeOrigin(serverUrl);
        if (TextUtils.isEmpty(origin)) throw new IllegalArgumentException(App.get().getString(R.string.remote_trust_server_required));
        RemoteStoreFile store = get();
        RemoteProfile profile = findProfile(store, origin);
        if (profile == null) {
            profile = new RemoteProfile();
            profile.serverOrigin = origin;
            store.profiles.add(profile);
        }
        profile.serverUrl = serverUrl.trim();
        profile.serverOrigin = origin;
        profile.enabled = enabled;
        profile.keepOnline = keepOnline;
        ensureProfile(profile);
        if (TextUtils.isEmpty(profile.deviceToken)) profile.deviceToken = RemoteTokens.randomCapability("dtk");
        profile.deviceId = RemoteTokens.deviceId(profile.serverOrigin, profile.deviceToken);
        profile.updatedAt = System.currentTimeMillis();
        save(store);
        return profile;
    }

    public static synchronized RemoteProfile firstProfile() {
        RemoteStoreFile store = get();
        return store.profiles.isEmpty() ? null : store.profiles.get(0);
    }

    public static synchronized RemoteProfile getProfileByOrigin(String serverOrigin) {
        return findProfile(get(), serverOrigin);
    }

    public static synchronized void upsertProfile(RemoteProfile profile) {
        if (profile == null || TextUtils.isEmpty(profile.serverOrigin)) return;
        RemoteStoreFile store = get();
        RemoteProfile current = findProfile(store, profile.serverOrigin);
        profile.updatedAt = System.currentTimeMillis();
        ensureProfile(profile);
        if (current == null) store.profiles.add(profile);
        else store.profiles.set(store.profiles.indexOf(current), profile);
        save(store);
    }

    public static synchronized void addBindGrant(String serverOrigin, RemoteBindGrant grant) {
        RemoteProfile profile = getProfileByOrigin(serverOrigin);
        if (profile == null || grant == null) return;
        ensureProfile(profile);
        profile.pendingBindGrants.add(grant);
        profile.updatedAt = System.currentTimeMillis();
        upsertProfile(profile);
    }

    public static synchronized boolean consumeBindGrant(String serverOrigin, String grantId, String bindGrantToken) {
        RemoteProfile profile = getProfileByOrigin(serverOrigin);
        if (profile == null) return false;
        ensureProfile(profile);
        boolean matched = false;
        long now = System.currentTimeMillis();
        for (RemoteBindGrant grant : profile.pendingBindGrants) {
            if (grant == null || grant.consumedAt > 0) continue;
            if (!TextUtils.equals(grant.grantId, grantId)) continue;
            if (!TextUtils.equals(grant.bindGrantToken, bindGrantToken)) continue;
            grant.consumedAt = now;
            matched = true;
            break;
        }
        if (matched) upsertProfile(profile);
        return matched;
    }

    public static synchronized void upsertGroup(String serverOrigin, RemoteGroup group) {
        RemoteProfile profile = getProfileByOrigin(serverOrigin);
        if (profile == null || group == null || TextUtils.isEmpty(group.groupId)) return;
        ensureProfile(profile);
        if (profile.groups == null) profile.groups = new ArrayList<>();
        group.updatedAt = System.currentTimeMillis();
        for (int i = 0; i < profile.groups.size(); i++) {
            RemoteGroup current = profile.groups.get(i);
            if (current != null && TextUtils.equals(current.groupId, group.groupId)) {
                profile.groups.set(i, group);
                upsertProfile(profile);
                return;
            }
        }
        profile.groups.add(group);
        upsertProfile(profile);
    }

    public static synchronized RemoteGroup upsertClaimGroup(String serverOrigin, ClaimResponse response, String alias) {
        if (response == null) return null;
        String groupToken = TextUtils.isEmpty(response.groupToken) ? response.familyToken : response.groupToken;
        if (TextUtils.isEmpty(groupToken)) return null;
        RemoteProfile profile = getProfileByOrigin(serverOrigin);
        if (profile == null) return null;
        ensureProfile(profile);
        String groupId = TextUtils.isEmpty(response.groupId) ? RemoteTokens.groupId(serverOrigin, groupToken) : response.groupId;
        String groupTokenHash = TextUtils.isEmpty(response.groupTokenHash) ? RemoteTokens.groupTokenHash(serverOrigin, groupToken) : response.groupTokenHash;
        RemoteGroup group = findGroup(profile, groupId);
        if (group == null) {
            group = new RemoteGroup();
            group.groupId = groupId;
            profile.groups.add(group);
        }
        group.groupToken = groupToken;
        group.groupTokenHash = groupTokenHash;
        if (TextUtils.isEmpty(group.name)) {
            if (!TextUtils.isEmpty(alias)) group.name = alias.trim();
            else if (response.device != null && !TextUtils.isEmpty(response.device.name)) group.name = response.device.name;
            else group.name = "Remote group";
        }
        if (response.device != null && !isLocalDevice(profile, response.device.deviceId)) upsertDevice(group, response.device);
        else if (!TextUtils.isEmpty(response.deviceId)) {
            RemoteDevice device = new RemoteDevice();
            device.deviceId = response.deviceId;
            device.name = response.deviceId;
            if (!isLocalDevice(profile, device.deviceId)) upsertDevice(group, device);
        }
        group.updatedAt = System.currentTimeMillis();
        profile.updatedAt = group.updatedAt;
        upsertProfile(profile);
        return group;
    }

    public static synchronized void upsertDevices(String serverOrigin, String groupId, List<RemoteDevice> devices) {
        RemoteProfile profile = getProfileByOrigin(serverOrigin);
        if (profile == null || TextUtils.isEmpty(groupId)) return;
        ensureProfile(profile);
        RemoteGroup group = findGroup(profile, groupId);
        if (group == null) return;
        if (devices == null) devices = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RemoteDevice device : devices) {
            if (device == null || TextUtils.isEmpty(device.deviceId)) continue;
            if (isLocalDevice(profile, device.deviceId)) continue;
            seen.add(device.deviceId);
            upsertDevice(group, device);
        }
        if (group.devices != null) {
            for (RemoteDevice device : group.devices) {
                if (device != null && !seen.contains(device.deviceId)) device.online = false;
            }
        }
        group.updatedAt = System.currentTimeMillis();
        profile.updatedAt = group.updatedAt;
        upsertProfile(profile);
    }

    public static synchronized boolean hasGroupTokenHash(String serverOrigin, String groupTokenHash) {
        if (TextUtils.isEmpty(groupTokenHash)) return false;
        RemoteProfile profile = getProfileByOrigin(serverOrigin);
        if (profile == null) return false;
        ensureProfile(profile);
        for (RemoteGroup group : profile.groups) {
            if (group != null && TextUtils.equals(group.groupTokenHash, groupTokenHash)) return true;
        }
        return false;
    }

    public static synchronized void removeProfile(String serverOrigin) {
        RemoteStoreFile store = get();
        List<RemoteProfile> removed = new ArrayList<>();
        for (Iterator<RemoteProfile> it = store.profiles.iterator(); it.hasNext(); ) {
            RemoteProfile profile = it.next();
            if (profile != null && TextUtils.equals(profile.serverOrigin, serverOrigin)) {
                removed.add(profile);
                it.remove();
            }
        }
        save(store);
        for (RemoteProfile profile : removed) removeTokens(profile);
    }

    public static synchronized boolean removeDevice(String serverOrigin, String groupId, String deviceId) {
        RemoteProfile profile = getProfileByOrigin(serverOrigin);
        if (profile == null || TextUtils.isEmpty(groupId) || TextUtils.isEmpty(deviceId)) return false;
        ensureProfile(profile);
        boolean removed = false;
        for (Iterator<RemoteGroup> groupIt = profile.groups.iterator(); groupIt.hasNext(); ) {
            RemoteGroup group = groupIt.next();
            if (group == null || !TextUtils.equals(group.groupId, groupId)) continue;
            if (group.devices != null) {
                for (Iterator<RemoteDevice> deviceIt = group.devices.iterator(); deviceIt.hasNext(); ) {
                    RemoteDevice device = deviceIt.next();
                    if (device != null && TextUtils.equals(device.deviceId, deviceId)) {
                        deviceIt.remove();
                        removed = true;
                    }
                }
            }
            if (group.devices == null || group.devices.isEmpty()) groupIt.remove();
            break;
        }
        if (removed) {
            profile.updatedAt = System.currentTimeMillis();
            upsertProfile(profile);
        }
        return removed;
    }

    public static synchronized void clear() {
        if (cache != null && cache.profiles != null) {
            for (RemoteProfile profile : cache.profiles) removeTokens(profile);
        }
        cache = new RemoteStoreFile();
        loaded = true;
        Prefers.remove(KEY_STORE);
        if (Setting.hasFileAccess()) {
            Path.clear(file());
            Path.clear(new File(file().getParentFile(), file().getName() + ".tmp"));
        }
    }

    public static synchronized String exportRelayConfig() {
        JsonObject root = new JsonObject();
        JsonArray profiles = new JsonArray();
        for (RemoteProfile profile : get().profiles) {
            if (profile == null || TextUtils.isEmpty(profile.serverOrigin)) continue;
            JsonObject item = new JsonObject();
            item.addProperty("serverUrl", TextUtils.isEmpty(profile.serverUrl) ? profile.serverOrigin : profile.serverUrl);
            item.addProperty("serverOrigin", profile.serverOrigin);
            item.addProperty("enabled", profile.enabled);
            item.addProperty("keepOnline", true);
            profiles.add(item);
        }
        root.add("profiles", profiles);
        return App.gson().toJson(root);
    }

    public static synchronized int importRelayConfig(String json) {
        if (TextUtils.isEmpty(json)) return 0;
        JsonObject root;
        try {
            root = App.gson().fromJson(json, JsonObject.class);
        } catch (Throwable e) {
            return 0;
        }
        JsonArray profiles = root != null && root.has("profiles") && root.get("profiles").isJsonArray() ? root.getAsJsonArray("profiles") : new JsonArray();
        int count = 0;
        for (int i = 0; i < profiles.size(); i++) {
            if (!profiles.get(i).isJsonObject()) continue;
            JsonObject item = profiles.get(i).getAsJsonObject();
            String serverUrl = string(item, "serverUrl");
            if (TextUtils.isEmpty(serverUrl)) serverUrl = string(item, "serverOrigin");
            String origin = RemoteTokens.normalizeOrigin(serverUrl);
            if (TextUtils.isEmpty(origin)) continue;
            boolean enabled = !item.has("enabled") || item.get("enabled").getAsBoolean();
            prepareProfile(serverUrl, enabled, true);
            count++;
        }
        if (count > 0) RemoteAgent.get().start();
        return count;
    }

    public static synchronized String summary(Context context) {
        RemoteStoreFile store = get();
        int profiles = 0;
        int groups = 0;
        int devices = 0;
        boolean enabled = false;
        boolean keepOnline = false;
        for (RemoteProfile profile : store.profiles) {
            if (profile == null) continue;
            profiles++;
            ensureProfile(profile);
            groups += profile.groups.size();
            for (RemoteGroup group : profile.groups) {
                if (group != null && group.devices != null) devices += group.devices.size();
            }
            enabled |= profile.enabled;
            keepOnline |= profile.enabled && profile.keepOnline;
        }
        if (profiles == 0) return context.getString(R.string.remote_trust_status_unbound);
        if (!enabled) return context.getString(R.string.setting_disable);
        String status = keepOnline ? context.getString(R.string.remote_trust_status_online) : context.getString(R.string.remote_trust_status_enabled);
        return context.getString(R.string.remote_trust_current_status_summary, status, groups, devices);
    }

    public static synchronized boolean hasEnabledProfile() {
        for (RemoteProfile profile : get().profiles) {
            if (profile != null && profile.enabled && !TextUtils.isEmpty(profile.serverOrigin)) return true;
        }
        return false;
    }

    private static void log(String format, Object... args) {
        RemoteStoreFile store = cache;
        if (store == null || store.profiles == null) return;
        for (RemoteProfile profile : store.profiles) {
            if (profile != null && profile.enabled) {
                SpiderDebug.log("remote", format, args);
                return;
            }
        }
    }

    static boolean shouldStart(RemoteProfile profile) {
        if (profile == null || TextUtils.isEmpty(profile.serverOrigin)) return false;
        ensureProfile(profile);
        return profile.enabled;
    }

    static boolean hasPendingGrant(RemoteProfile profile) {
        ensureProfile(profile);
        for (RemoteBindGrant grant : profile.pendingBindGrants) if (grant != null && grant.consumedAt <= 0) return true;
        return false;
    }

    private static RemoteProfile findProfile(RemoteStoreFile store, String serverOrigin) {
        if (store == null || TextUtils.isEmpty(serverOrigin)) return null;
        ensure(store);
        for (RemoteProfile profile : store.profiles) {
            if (profile != null && TextUtils.equals(profile.serverOrigin, serverOrigin)) return profile;
        }
        return null;
    }

    private static RemoteGroup findGroup(RemoteProfile profile, String groupId) {
        if (profile == null || TextUtils.isEmpty(groupId)) return null;
        ensureProfile(profile);
        for (RemoteGroup group : profile.groups) {
            if (group != null && TextUtils.equals(group.groupId, groupId)) return group;
        }
        return null;
    }

    private static void upsertDevice(RemoteGroup group, RemoteDevice device) {
        if (group == null || device == null || TextUtils.isEmpty(device.deviceId)) return;
        if (group.devices == null) group.devices = new ArrayList<>();
        for (int i = 0; i < group.devices.size(); i++) {
            RemoteDevice current = group.devices.get(i);
            if (current != null && TextUtils.equals(current.deviceId, device.deviceId)) {
                group.devices.set(i, device);
                return;
            }
        }
        group.devices.add(device);
    }

    private static boolean isLocalDevice(RemoteProfile profile, String deviceId) {
        return profile != null && !TextUtils.isEmpty(profile.deviceId) && TextUtils.equals(profile.deviceId, deviceId);
    }

    private static RemoteStoreFile parse(String json, boolean[] migrated) {
        if (TextUtils.isEmpty(json)) return null;
        try {
            RemoteStoreFile raw = App.gson().fromJson(json, RemoteStoreFile.class);
            retype(raw, json);
            RemoteStoreFile store = ensure(raw);
            rehydrateTokens(store, migrated);
            return store;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 定型修复（体检类2）：profiles 及内嵌 pendingBindGrants/groups/devices 三层 List
     * 整包反射读靠类文件 Signature 保型，签名失效时元素退化成 LinkedTreeMap，
     * ensure() 的强类型迭代在 parse 的 catch 内被吞成整店判坏回 null、远程信任数据全丢。
     * 这里用程序化构造的参数化类型（不读类文件签名）按 JSON 树逐层重读覆盖；
     * 修复自守、读失败保持反射结果不动。persistJson 的深拷贝读同路复用。
     */
    private static void retype(RemoteStoreFile store, String json) {
        if (store == null || json == null) return;
        try {
            JsonElement tree = JsonParser.parseString(json);
            if (tree == null || !tree.isJsonObject()) return;
            JsonElement profiles = tree.getAsJsonObject().get("profiles");
            if (profiles == null || !profiles.isJsonArray()) return;
            List<RemoteProfile> typed = App.gson().fromJson(profiles, TypeToken.getParameterized(List.class, RemoteProfile.class).getType());
            if (typed == null) return;
            JsonArray array = profiles.getAsJsonArray();
            for (int i = 0; i < typed.size() && i < array.size(); i++) retypeProfile(typed.get(i), array.get(i));
            store.profiles = typed;
        } catch (Throwable ignored) {
        }
    }

    private static void retypeProfile(RemoteProfile profile, JsonElement element) {
        if (profile == null || element == null || !element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        JsonElement grants = object.get("pendingBindGrants");
        if (grants != null && grants.isJsonArray()) {
            try {
                List<RemoteBindGrant> typed = App.gson().fromJson(grants, TypeToken.getParameterized(List.class, RemoteBindGrant.class).getType());
                if (typed != null) profile.pendingBindGrants = typed;
            } catch (Throwable ignored) {
            }
        }
        JsonElement groups = object.get("groups");
        if (groups == null || !groups.isJsonArray()) return;
        try {
            List<RemoteGroup> typed = App.gson().fromJson(groups, TypeToken.getParameterized(List.class, RemoteGroup.class).getType());
            if (typed == null) return;
            JsonArray array = groups.getAsJsonArray();
            for (int i = 0; i < typed.size() && i < array.size(); i++) retypeGroup(typed.get(i), array.get(i));
            profile.groups = typed;
        } catch (Throwable ignored) {
        }
    }

    private static void retypeGroup(RemoteGroup group, JsonElement element) {
        if (group == null || element == null || !element.isJsonObject()) return;
        JsonElement devices = element.getAsJsonObject().get("devices");
        if (devices == null || !devices.isJsonArray()) return;
        try {
            List<RemoteDevice> typed = App.gson().fromJson(devices, TypeToken.getParameterized(List.class, RemoteDevice.class).getType());
            if (typed != null) group.devices = typed;
        } catch (Throwable ignored) {
        }
    }

    // ---- 令牌加密分存（与 gitcloud 同口径：GitCloudTokenStore AES-GCM）----
    // 此前 deviceToken/groupToken/bindGrantToken 随整店 JSON 明文进 Prefers 与
    // profile.json。现落盘前剥离进加密令牌仓、JSON 里只留空位，读盘时回填；
    // 老明文里带回的内联令牌在回填时顺手迁入加密仓，读路径随即回写剥离版（见 reloadFromFile）。

    private static String deviceKey(RemoteProfile profile) {
        return TextUtils.isEmpty(profile.serverOrigin) ? "" : "remote_dt:" + profile.serverOrigin;
    }

    private static String groupKey(RemoteProfile profile, RemoteGroup group) {
        return TextUtils.isEmpty(profile.serverOrigin) || TextUtils.isEmpty(group.groupId) ? "" : "remote_gt:" + profile.serverOrigin + ":" + group.groupId;
    }

    private static String grantKey(RemoteProfile profile, RemoteBindGrant grant) {
        return TextUtils.isEmpty(profile.serverOrigin) || TextUtils.isEmpty(grant.grantId) ? "" : "remote_bg:" + profile.serverOrigin + ":" + grant.grantId;
    }

    /** 落盘序列化：深拷贝后剥离令牌（内存对象不动），令牌同步进加密仓（清空即删） */
    private static String persistJson(RemoteStoreFile store) {
        RemoteStoreFile copy;
        try {
            String copyJson = App.gson().toJson(store);
            RemoteStoreFile raw = App.gson().fromJson(copyJson, RemoteStoreFile.class);
            retype(raw, copyJson);
            copy = ensure(raw);
        } catch (Throwable e) {
            // 拷贝失败绝不能拿活对象剥离（会把内存里的令牌也抹了）：退回整串直写
            return App.gson().toJson(store);
        }
        for (RemoteProfile profile : copy.profiles) {
            if (profile == null) continue;
            // 只有加密写回读校验通过才剥离拷贝上的令牌；校验不过保留内联值落盘，不静默丢令牌。
            if (tokenSync(deviceKey(profile), profile.deviceToken)) profile.deviceToken = "";
            for (RemoteGroup group : profile.groups) {
                if (group == null) continue;
                if (tokenSync(groupKey(profile, group), group.groupToken)) group.groupToken = "";
            }
            for (RemoteBindGrant grant : profile.pendingBindGrants) {
                if (grant == null) continue;
                if (tokenSync(grantKey(profile, grant), grant.bindGrantToken)) grant.bindGrantToken = "";
            }
        }
        return App.gson().toJson(copy);
    }

    private static void rehydrateTokens(RemoteStoreFile store, boolean[] migrated) {
        for (RemoteProfile profile : store.profiles) {
            if (profile == null) continue;
            profile.deviceToken = takeToken(deviceKey(profile), profile.deviceToken, migrated);
            for (RemoteGroup group : profile.groups) {
                if (group != null) group.groupToken = takeToken(groupKey(profile, group), group.groupToken, migrated);
            }
            for (RemoteBindGrant grant : profile.pendingBindGrants) {
                if (grant != null) grant.bindGrantToken = takeToken(grantKey(profile, grant), grant.bindGrantToken, migrated);
            }
        }
    }

    /** 内联有值＝老明文，迁入加密仓后用它（migrated 置真供读路径回写剥离版）；内联为空＝从加密仓回填 */
    private static String takeToken(String key, String inline, boolean[] migrated) {
        if (TextUtils.isEmpty(key)) return inline == null ? "" : inline;
        try {
            if (!TextUtils.isEmpty(inline)) {
                GitCloudTokenStore.put(key, inline);
                if (migrated != null) migrated[0] = true;
                return inline;
            }
            String stored = GitCloudTokenStore.get(key);
            return stored == null ? "" : stored;
        } catch (Throwable e) {
            return inline == null ? "" : inline;
        }
    }

    /**
     * 令牌同步进加密仓；返回 true 才表示落盘副本可以剥离：
     * 非空令牌写后必须回读校验（读回值等于刚写值），校验不过／写抛错都留痕并返回 false。
     * 留痕直走 SpiderDebug，不经 log() 的 enabled 门——未启用的 profile 写失败也要有痕。
     * 空令牌＝清除，删仓即算完成。
     */
    private static boolean tokenSync(String key, String token) {
        if (TextUtils.isEmpty(key)) return TextUtils.isEmpty(token);
        try {
            if (TextUtils.isEmpty(token)) {
                GitCloudTokenStore.remove(key);
                return true;
            }
            GitCloudTokenStore.put(key, token);
            String back = GitCloudTokenStore.get(key);
            if (TextUtils.equals(token, back)) return true;
            SpiderDebug.log("remote", "secure token verify failed key=%s, keep inline", key);
            return false;
        } catch (Throwable e) {
            SpiderDebug.log("remote", "secure token store failed error=%s, keep inline", e.getMessage());
            return false;
        }
    }

    private static void removeTokens(RemoteProfile profile) {
        if (profile == null) return;
        tokenSync(deviceKey(profile), "");
        if (profile.groups != null) {
            for (RemoteGroup group : profile.groups) {
                if (group != null) tokenSync(groupKey(profile, group), "");
            }
        }
        if (profile.pendingBindGrants != null) {
            for (RemoteBindGrant grant : profile.pendingBindGrants) {
                if (grant != null) tokenSync(grantKey(profile, grant), "");
            }
        }
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        return object.get(key).getAsString().trim();
    }

    private static RemoteStoreFile ensure(RemoteStoreFile store) {
        if (store == null) store = new RemoteStoreFile();
        if (store.profiles == null) store.profiles = new ArrayList<>();
        for (RemoteProfile profile : store.profiles) ensureProfile(profile);
        return store;
    }

    private static void ensureProfile(RemoteProfile profile) {
        if (profile == null) return;
        if (profile.pendingBindGrants == null) profile.pendingBindGrants = new ArrayList<>();
        if (profile.groups == null) profile.groups = new ArrayList<>();
        for (RemoteGroup group : profile.groups) {
            if (group == null) continue;
            if (group.devices == null) group.devices = new ArrayList<>();
            for (Iterator<RemoteDevice> it = group.devices.iterator(); it.hasNext(); ) {
                RemoteDevice device = it.next();
                if (device == null || isLocalDevice(profile, device.deviceId)) it.remove();
            }
        }
    }

    private static File file() {
        return Path.root(FILE_STORE);
    }
}
