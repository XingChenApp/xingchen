package com.example.hikerview.ui.setting.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.example.hikerview.utils.StringUtil;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkUaEnum;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import timber.log.Timber;

/**
 * 官 8.83 SettingConfig 的最小落点：官类是千行配置总汇，本批只落
 * GlideUtil 实际用到的两名成员，语义照官——
 * rootDir：官在 init 时置为 UriUtils.getRootDir(ctx)，即
 * getExternalFilesDir(DOCUMENTS) 的路径（与星辰引擎 HkJsRuntime
 * 的 hiker://files 根同一口径），此处按官公式现算；
 * glideUA：官为静态字段（初值 null，init 时从偏好读、空则取
 * WebView UA），此处保留字段与读写，默认空串——未接官的偏好/WebView
 * 注入，空值时 GlideUtil 走无 UA 头分支，与官未初始化时同形。
 * 其余配置成员随用到再逐名补入，不整类搬。
 *
 * <p>2026-11-14 对官补缺口批（官源 SettingConfig.smali，
 * smali_classes2/com/example/hikerview/ui/setting/model/）：另落 19 法，
 * 逐法出处见各法注。存储口径：本文件原无 SP 封装，SP 访问照本树已落
 * 的同法对位件先例（HkLivePlayerHelper 的 loadLiveAddress/
 * setLiveTvAddress、HkJsRuntime 的 getMobileUa）直连
 * SharedPreferences，文件名照官 PreferenceMgr（PreferenceMgr.smali:15
 * 默认文件 "share_data"，具名文件随官调用点）；PreferenceMgr 自身的
 * 失败语义（getLong 的 ClassCast 回退、put 对 null 写空串）由本文件
 * 私有 prefGetLong 与各 put 点照官复刻，不另立 PreferenceMgr 类。
 * 停手记名（依赖本树没有的协作者，不落）：dynamicInitConfig、
 * initConfig、setDeveloperMode——原因逐项见回执与各停手点说明，
 * 本文件内不留空壳。
 * ①团订正：getPrivacyFindRule/getTextConfig/initTextConfig/updateTextConfig
 * 四法已解锁落码（官 PreferenceMgr/FilesInAppUtil/TextConfig/ArticleListRule
 * 皆已转写在位），SP 访问走官 PreferenceMgr 本体、不再走本文件私有复刻件。
 */
public class SettingConfig {

    public static String rootDir = "";

    private static String glideUA = "";

    /** 官 adBlock（SettingConfig.smali:9，初值 null）。 */
    private static Boolean adBlock = null;

    /** 官 adblockplus_count（smali:11，初值 -1）。 */
    public static long adblockplus_count = -1L;

    /** 官 adblockpro_count（smali:13，初值 -1）。 */
    public static long adblockpro_count = -1L;

    /** 官 isNight（smali:35，初值 false）。 */
    public static boolean isNight = false;

    /** 官 liveTvAddress（smali:37，初值 null）。 */
    public static String liveTvAddress = null;

    /** 官 openAppNotify（smali:43，初值 true）。 */
    public static boolean openAppNotify = true;

    /** 官 openGeoNotify（smali:45，初值 true）。 */
    public static boolean openGeoNotify = true;

    /** 官 privacyModeUnlocked：字段声明 false、&lt;clinit&gt; 置 true（smali:118），可观察初值 true。 */
    public static boolean privacyModeUnlocked = true;

    /** 官 privacyRules（smali:49，初值 null）。 */
    public static Set<String> privacyRules = null;

    /** 官 homeName：&lt;clinit&gt; 置「我的主页」（smali:88-90）。 */
    public static String homeName = "我的主页";

    /** 官 saveForm：&lt;clinit&gt; 置 true（smali:124）。 */
    public static boolean saveForm = true;

    /** 官 rules（smali:63）：&lt;clinit&gt; 置 new ArrayList（smali:82-86）；元素官为 ArticleListRule，接其等价物 HkRule（记名）。 */
    private static List<HkRule> rules = new ArrayList<>();

    public static String getRootDirPath() {
        if (rootDir == null || rootDir.isEmpty()) {
            File docs = App.get().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (docs != null) {
                if (!docs.exists()) {
                    docs.mkdirs();
                }
                rootDir = docs.getAbsolutePath();
            }
        }
        return rootDir;
    }

    public static String getGlideUA() {
        return glideUA;
    }

    public static void setGlideUA(String glideUA) {
        SettingConfig.glideUA = glideUA;
    }

    // —— SP 私有口：照官 PreferenceMgr 语义（PreferenceMgr.smali）——

    private static SharedPreferences sp(Context context, String file) {
        return context.getSharedPreferences(file, Context.MODE_PRIVATE);
    }

    /**
     * 官 PreferenceMgr.getLong(ctx, key, def)（PreferenceMgr.smali:683-760）：
     * 直读抛 ClassCastException 时回退 getInt（其自身 catch Exception 回
     * 默认值），再把结果以 Long 回写后返回。
     */
    private static long prefGetLong(Context context, String key, long def) {
        SharedPreferences pref = sp(context, "share_data");
        try {
            return pref.getLong(key, def);
        } catch (ClassCastException e) {
            int v;
            try {
                v = pref.getInt(key, (int) def);
            } catch (Exception e2) {
                v = (int) def;
            }
            pref.edit().putLong(key, v).apply();
            return v;
        }
    }

    /** 官 changeNightMode（SettingConfig.smali:141-247）：夜间加全屏遮罩 View、非夜间摘除；异常照官 Timber.e，加罩段异常回 null。 */
    public static View changeNightMode(Context context, View view, boolean night) {
        if (night) {
            try {
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1, 2, 0x18, -3);
                params.gravity = 0x50;
                params.y = 0;
                if (view == null) {
                    view = new TextView(context);
                    view.setBackgroundColor(0x80000000);
                }
                WindowManager wm = (WindowManager) context.getSystemService("window");
                if (wm != null) {
                    wm.addView(view, params);
                }
            } catch (Exception e) {
                Timber.e(e);
                return null;
            }
            return view;
        }
        if (view != null) {
            try {
                WindowManager wm = (WindowManager) context.getSystemService("window");
                if (wm != null) {
                    wm.removeView(view);
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        return null;
    }

    /** 官 getMobileUA（smali:317-345）：读默认文件 "sysUa"，空回落 UAEnum.MOBILE。Context 源照本文件既有 App.get()；UAEnum 接等价物 HkUaEnum（记名）。 */
    public static String getMobileUA() {
        String ua = sp(App.get(), "share_data").getString("sysUa", null);
        if (StringUtil.isEmpty(ua)) {
            ua = HkUaEnum.MOBILE.getContent();
        }
        return ua;
    }

    /** 官 getProBlockCount（smali:382-411）：静态位 -1 时才从默认文件 "adblockpro_count"（缺省 0）读入。 */
    public static long getProBlockCount(Context context) {
        if (adblockpro_count == -1L) {
            adblockpro_count = prefGetLong(context, "adblockpro_count", 0L);
        }
        return adblockpro_count;
    }

    /** 官 getRules（smali:412-428）。元素类型接等价物 HkRule（记名）。 */
    public static List<HkRule> getRules() {
        return rules;
    }

    /** 官 setRules（smali:1161-1177）。元素类型接等价物 HkRule（记名）。 */
    public static void setRules(List<HkRule> newRules) {
        rules = newRules;
    }

    /** 官 initNightMode（smali:645-651）：官本体即无条件回 null，照官落。 */
    public static View initNightMode(Context context, View view) {
        return null;
    }

    /** 官 initNowConfig（smali:653-687）：rootDir 照官 UriUtils.getRootDir 公式（与本文件 getRootDirPath 同式、改用入参 Context）；homeName/saveForm 读默认文件，缺省照官。 */
    public static void initNowConfig(Context context) {
        File docs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (docs != null) {
            if (!docs.exists()) {
                docs.mkdirs();
            }
            rootDir = docs.getAbsolutePath();
        }
        SharedPreferences pref = sp(context, "share_data");
        homeName = pref.getString("homeName", "我的主页");
        saveForm = pref.getBoolean("saveForm", true);
    }

    /** 官 initPrivacyRules（smali:688-737）：静态集为空时建集并读默认文件 "ppr"，非空按 "&&&" 切分并入。Context 源照本文件既有 App.get()。 */
    public static void initPrivacyRules() {
        if (privacyRules == null) {
            privacyRules = new HashSet<>();
            String ppr = sp(App.get(), "share_data").getString("ppr", null);
            if (StringUtil.isNotEmpty(ppr)) {
                Collections.addAll(privacyRules, ppr.split("&&&"));
            }
        }
    }

    /** 官 loadLiveAddress（smali:819-857）："custom" 文件 liveTvAddressLocal 非空先覆盖回 liveTvAddress，再读入静态位（缺省 null）。 */
    public static void loadLiveAddress(Context context) {
        SharedPreferences pref = sp(context, "custom");
        String local = pref.getString("liveTvAddressLocal", "");
        if (StringUtil.isNotEmpty(local)) {
            pref.edit().putString("liveTvAddress", local).apply();
        }
        liveTvAddress = pref.getString("liveTvAddress", null);
    }

    /** 官 recordBlock（smali:858-908）：ctx 非空且全程 try，静态位 -1 先读默认文件 "adblockplus_count"（缺省 0），再 +1；异常 Timber.e。 */
    public static void recordBlock(Context context) {
        if (context != null) {
            try {
                if (adblockplus_count == -1L) {
                    adblockplus_count = prefGetLong(context, "adblockplus_count", 0L);
                }
                adblockplus_count += 1;
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }

    /** 官 recordProBlock（smali:909-959）：同 recordBlock，键与静态位换 adblockpro_count。 */
    public static void recordProBlock(Context context) {
        if (context != null) {
            try {
                if (adblockpro_count == -1L) {
                    adblockpro_count = prefGetLong(context, "adblockpro_count", 0L);
                }
                adblockpro_count += 1;
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }

    /** 官 saveAdblockPlusCount（smali:960-1039）：两静态位非 -1 且大于已存值才回写默认文件；整体 catch Throwable → Timber.e，照官。 */
    public static void saveAdblockPlusCount(Context context) {
        try {
            SharedPreferences pref = sp(context, "share_data");
            if (adblockplus_count != -1L) {
                long stored = prefGetLong(context, "adblockplus_count", 0L);
                if (stored < adblockplus_count) {
                    pref.edit().putLong("adblockplus_count", adblockplus_count).apply();
                }
            }
            if (adblockpro_count != -1L) {
                long stored = prefGetLong(context, "adblockpro_count", 0L);
                if (stored < adblockpro_count) {
                    pref.edit().putLong("adblockpro_count", adblockpro_count).apply();
                }
            }
        } catch (Throwable t) {
            Timber.e(t);
        }
    }

    /** 官 saveNightMode（smali:1040-1058）：写 PreferenceMgr.SETTING_CONFIG（"setting_config"，PreferenceMgr.smali:17）文件 "night"＝isNight。 */
    public static void saveNightMode(Context context) {
        sp(context, "setting_config").edit().putBoolean("night", isNight).apply();
    }

    /** 官 setAdBlock（smali:1059-1071）。 */
    public static void setAdBlock(boolean block) {
        adBlock = Boolean.valueOf(block);
    }

    /** 官 setLiveTvAddress（smali:1105-1120）：写静态位＋"custom"/"liveTvAddress"；值 null 时照官 PreferenceMgr.put 语义写空串（PreferenceMgr.smali put 的 null 分支）。 */
    public static void setLiveTvAddress(Context context, String address) {
        liveTvAddress = address;
        sp(context, "custom").edit().putString("liveTvAddress", address == null ? "" : address).apply();
    }

    /** 官 setOpenAppNotify（smali:1121-1140）：先写静态位，再写 "custom"/"openAppNotify"。 */
    public static void setOpenAppNotify(Context context, boolean open) {
        openAppNotify = open;
        sp(context, "custom").edit().putBoolean("openAppNotify", open).apply();
    }

    /** 官 setOpenGeoNotify（smali:1141-1160）：先写静态位，再写 "custom"/"openGeoNotify"。 */
    public static void setOpenGeoNotify(Context context, boolean open) {
        openGeoNotify = open;
        sp(context, "custom").edit().putBoolean("openGeoNotify", open).apply();
    }

    /** 官 shouldBlock（smali:1178-1217）：静态位 null 时读默认文件 "adBlock"（缺省 true）回填。Context 源照本文件既有 App.get()。 */
    public static boolean shouldBlock() {
        if (adBlock == null) {
            adBlock = Boolean.valueOf(sp(App.get(), "share_data").getBoolean("adBlock", true));
        }
        return adBlock.booleanValue();
    }

    /** 官 shouldReplacePrivacyRule（smali:1218-1246）：未解锁且隐私集非空且含此名才回 true。 */
    public static boolean shouldReplacePrivacyRule(String name) {
        return !privacyModeUnlocked && privacyRules != null && privacyRules.contains(name);
    }

    /**
     * 官 getPrivacyFindRule（SettingConfig.smali:351-381）逐字转写（①团解锁）：
     * 读 assets "privacy.json" 按 ArticleListRule 解析、回其 find_rule。
     * Context 源：官 Application.getContext() 接本树 App.get()（任务令既定等价，记名）。
     */
    public static String getPrivacyFindRule() {
        String json = com.example.hikerview.utils.FilesInAppUtil.getAssetsString(App.get(), "privacy.json");
        com.example.hikerview.ui.home.model.ArticleListRule rule =
                com.alibaba.fastjson.JSON.parseObject(json, com.example.hikerview.ui.home.model.ArticleListRule.class);
        return rule.getFind_rule();
    }

    /** 官 getTextConfig（smali .line 303-307）逐字：textSize 缺省 19、lineSpacingExtra 缺省 -1、readBg 缺省 "#eee7df"、tc1 缺省 -0xddddde。存储走①团已转写的官 PreferenceMgr。 */
    public static com.example.hikerview.ui.home.model.TextConfig getTextConfig(Context context) {
        int textSize = com.example.hikerview.utils.PreferenceMgr.getInt(context, "textSize", 19);
        int lineSpacingExtra = com.example.hikerview.utils.PreferenceMgr.getInt(context, "lineSpacingExtra", -1);
        String background = com.example.hikerview.utils.PreferenceMgr.getString(context, "readBg", "#eee7df");
        int textColor = com.example.hikerview.utils.PreferenceMgr.getInt(context, "tc1", -0xddddde);
        return new com.example.hikerview.ui.home.model.TextConfig(textSize, lineSpacingExtra, background, textColor);
    }

    /** 官 initTextConfig（smali .line 311-315）逐字：与 getTextConfig 仅 lineSpacingExtra 缺省不同（10）。 */
    public static com.example.hikerview.ui.home.model.TextConfig initTextConfig(Context context) {
        int textSize = com.example.hikerview.utils.PreferenceMgr.getInt(context, "textSize", 19);
        int lineSpacingExtra = com.example.hikerview.utils.PreferenceMgr.getInt(context, "lineSpacingExtra", 10);
        String background = com.example.hikerview.utils.PreferenceMgr.getString(context, "readBg", "#eee7df");
        int textColor = com.example.hikerview.utils.PreferenceMgr.getInt(context, "tc1", -0xddddde);
        return new com.example.hikerview.ui.home.model.TextConfig(textSize, lineSpacingExtra, background, textColor);
    }

    /** 官 updateTextConfig（smali .line 319-322）逐字：四键依次 put 回默认偏好文件。 */
    public static void updateTextConfig(Context context, com.example.hikerview.ui.home.model.TextConfig config) {
        com.example.hikerview.utils.PreferenceMgr.put(context, "textSize", Integer.valueOf(config.getTextSize()));
        com.example.hikerview.utils.PreferenceMgr.put(context, "lineSpacingExtra", Integer.valueOf(config.getLineSpacingExtra()));
        com.example.hikerview.utils.PreferenceMgr.put(context, "readBg", config.getBackground());
        com.example.hikerview.utils.PreferenceMgr.put(context, "tc1", Integer.valueOf(config.getTextColor()));
    }
}
