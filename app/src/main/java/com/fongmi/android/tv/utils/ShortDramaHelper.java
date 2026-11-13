package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.utils.Prefers;

/**
 * 短剧识别：插件卡片手动标记（最高） > 分类手动标记 > 分类名自动匹配。
 * 封面宽高比猜测降级为兜底。
 * 插件标记跟插件文件名绑定，存本地。
 */
public class ShortDramaHelper {

    private static String pluginKey(String fileName) {
        return "short_drama_plugin_" + (fileName == null ? "" : fileName);
    }

    /** 插件（PY/JS 文件）是否被标记为短剧 */
    public static boolean isPluginShortDrama(String fileName) {
        if (fileName == null || fileName.isEmpty()) return false;
        return Prefers.getBoolean(pluginKey(fileName), false);
    }

    public static void setPluginShortDrama(String fileName, boolean value) {
        if (fileName == null || fileName.isEmpty()) return;
        if (value) Prefers.put(pluginKey(fileName), true);
        else Prefers.remove(pluginKey(fileName));
    }

    /** 通过站点 key 找到其插件文件名，再查标记 */
    public static boolean isPluginShortDramaBySite(String siteKey) {
        try {
            if (siteKey == null || siteKey.isEmpty()) return false;
            Site site = VodConfig.get().getSite(siteKey);
            if (site == null) return false;
            String api = site.getApi();
            if (api == null || api.isEmpty()) return false;
            String name = api.substring(api.lastIndexOf('/') + 1);
            int q = name.indexOf('?');
            if (q > 0) name = name.substring(0, q);
            if (name.isEmpty()) return false;
            return isPluginShortDrama(name);
        } catch (Exception e) {
            return false;
        }
    }

    /** VOD 源（站点）手动标记，跟站点 key 绑定 */
    private static String siteMarkKey(String siteKey) {
        return "short_drama_site_" + (siteKey == null ? "" : siteKey);
    }

    public static boolean isSiteShortDrama(String siteKey) {
        if (siteKey == null || siteKey.isEmpty()) return false;
        return Prefers.getBoolean(siteMarkKey(siteKey), false);
    }

    public static void setSiteShortDrama(String siteKey, boolean value) {
        if (siteKey == null || siteKey.isEmpty()) return;
        if (value) Prefers.put(siteMarkKey(siteKey), true);
        else Prefers.remove(siteMarkKey(siteKey));
    }

    /** 分类名自动匹配：短剧/竖屏/微剧/micro（不区分大小写） */
    public static boolean isShortDramaCategoryName(String name) {
        if (name == null || name.isEmpty()) return false;
        String n = name.toLowerCase();
        return n.contains("短剧") || n.contains("竖屏") || n.contains("微剧") || n.contains("micro");
    }

    private static String catKey(String siteKey, String typeId) {
        return "short_drama_cat_" + siteKey + "_" + typeId;
    }

    /** 分类是否被手动标记为短剧分类 */
    public static boolean isShortDramaCategoryMarked(String siteKey, String typeId) {
        if (siteKey == null || siteKey.isEmpty() || typeId == null || typeId.isEmpty()) return false;
        return Prefers.getBoolean(catKey(siteKey, typeId), false);
    }

    public static void setShortDramaCategoryMarked(String siteKey, String typeId, boolean value) {
        if (siteKey == null || siteKey.isEmpty() || typeId == null || typeId.isEmpty()) return;
        if (value) Prefers.put(catKey(siteKey, typeId), true);
        else Prefers.remove(catKey(siteKey, typeId));
    }

    /**
     * 点击分流总入口。优先级：插件卡片标记 / 站点标记 > 分类手动标记 > 分类名自动匹配。
     * 任一命中即直接播短剧。
     */
    public static boolean shouldDirectPlay(String siteKey, String typeId, String typeName) {
        if (isPluginShortDramaBySite(siteKey)) return true;
        if (isSiteShortDrama(siteKey)) return true;
        if (isShortDramaCategoryMarked(siteKey, typeId)) return true;
        if (isShortDramaCategoryName(typeName)) return true;
        return false;
    }

    /**
     * X-17 现算直进：缓存未命中、但 holder 封面图已就绪时，用 drawable 真实尺寸现算高宽比。
     * 占位图是透明 ColorDrawable（无内在尺寸）不会误判；未加载／加载失败直接返回 false 走原路，
     * 不做主线程同步拉图、不按分类名／域名猜。命中同时写回 CoverRatioCache，下次点击直接走缓存。
     */
    public static boolean isLiveShortDramaCover(android.view.View coverView, String pic) {
        if (!(coverView instanceof android.widget.ImageView imageView)) return false;
        android.graphics.drawable.Drawable drawable = imageView.getDrawable();
        if (drawable == null) return false;
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) return false;
        if (pic != null && !pic.isEmpty()) CoverRatioCache.put(pic, width, height);
        return (float) height / (float) width >= CoverRatioCache.SHORT_DRAMA_THRESHOLD;
    }
}
