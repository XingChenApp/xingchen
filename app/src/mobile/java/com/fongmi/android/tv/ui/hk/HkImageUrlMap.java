package com.fongmi.android.tv.ui.hk;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * 照 8.83 ImageUrlMapEnum 全量转写：url → drawable 名（8.83 侧存的是其 R id，
 * 星辰侧存资源名、运行时经 getIdentifier 解析为本 App 的 id）。drawable 文件已从 8.83 原包拷入 mobile res。
 * 对应 GlideUtil.getGlideUrl 首步 ImageUrlMapEnum.getIdByUrl(url)：全串相等匹配，命中返回 id、未命中 -1。
 */
public final class HkImageUrlMap {

    private static final Map<String, String> URL_TO_DRAWABLE = new HashMap<>();

    static {
        URL_TO_DRAWABLE.put("hiker://images/icon1", "icon1");
        URL_TO_DRAWABLE.put("hiker://images/icon2", "icon2");
        URL_TO_DRAWABLE.put("hiker://images/icon3", "icon3");
        URL_TO_DRAWABLE.put("hiker://images/icon4", "icon4");
        URL_TO_DRAWABLE.put("hiker://images/home_bookmark2", "home_bookmark2");
        URL_TO_DRAWABLE.put("hiker://images/home_download", "home_download");
        URL_TO_DRAWABLE.put("hiker://images/home_history2", "home_history2");
        URL_TO_DRAWABLE.put("hiker://images/home_setting", "home_setting");
        URL_TO_DRAWABLE.put("hiker://images/home_tools", "home_tools");
        URL_TO_DRAWABLE.put("hiker://images/icon_fire", "icon_fire");
        URL_TO_DRAWABLE.put("hiker://images/icon_hot", "icon_hot");
        URL_TO_DRAWABLE.put("hiker://images/icon_top", "icon_top");
        URL_TO_DRAWABLE.put("hiker://images/icon_class", "icon_class");
        URL_TO_DRAWABLE.put("hiker://images/tree1", "tree1");
        URL_TO_DRAWABLE.put("hiker://images/tree2", "tree2");
        URL_TO_DRAWABLE.put("hiker://images/tree3", "tree3");
        URL_TO_DRAWABLE.put("hiker://images/tree4", "tree4");
        URL_TO_DRAWABLE.put("hiker://images/icon_folder3", "icon_folder3");
        URL_TO_DRAWABLE.put("hiker://images/icon_app3", "icon_app3");
        URL_TO_DRAWABLE.put("hiker://images/icon_zip2", "icon_zip2");
        URL_TO_DRAWABLE.put("hiker://images/icon_music3", "icon_music3");
        URL_TO_DRAWABLE.put("hiker://images/icon_txt2", "icon_txt2");
        URL_TO_DRAWABLE.put("hiker://images/icon_pic3", "icon_pic3");
        URL_TO_DRAWABLE.put("hiker://images/icon_video2", "icon_video2");
        URL_TO_DRAWABLE.put("hiker://images/icon_unknown", "icon_unknown");
        URL_TO_DRAWABLE.put("hiker://images/icon_download6", "icon_download6");
        URL_TO_DRAWABLE.put("hiker://images/icon_edit6", "icon_edit6");
        URL_TO_DRAWABLE.put("hiker://images/icon_search6", "icon_search6");
        URL_TO_DRAWABLE.put("hiker://images/icon_good6", "icon_good6");
        URL_TO_DRAWABLE.put("hiker://images/icon_cloud6", "icon_cloud6");
        URL_TO_DRAWABLE.put("hiker://images/icon_del6", "icon_del6");
        URL_TO_DRAWABLE.put("hiker://images/icon_left6", "icon_left6");
        URL_TO_DRAWABLE.put("hiker://images/icon_menu6", "icon_menu6");
        URL_TO_DRAWABLE.put("hiker://images/icon_time6", "icon_time6");
        URL_TO_DRAWABLE.put("hiker://images/icon_setting6", "icon_setting6");
        URL_TO_DRAWABLE.put("hiker://images/icon_right6", "icon_right6");
        URL_TO_DRAWABLE.put("hiker://images/rule_type_all", "rule_type_all");
        URL_TO_DRAWABLE.put("hiker://images/rule_type_video", "rule_type_video");
        URL_TO_DRAWABLE.put("hiker://images/rule_type_audio", "rule_type_audio");
        URL_TO_DRAWABLE.put("hiker://images/rule_type_live", "rule_type_live");
        URL_TO_DRAWABLE.put("hiker://images/rule_type_comics", "rule_type_comics");
        URL_TO_DRAWABLE.put("hiker://images/rule_type_read", "rule_type_read");
        URL_TO_DRAWABLE.put("hiker://images/rule_type_news", "rule_type_news");
        URL_TO_DRAWABLE.put("hiker://images/rule_type_pic", "rule_type_pic");
        URL_TO_DRAWABLE.put("hiker://images/rule_type_tool", "rule_type_tool");
        URL_TO_DRAWABLE.put("hiker://images/rule_type_other", "rule_type_other");
        URL_TO_DRAWABLE.put("hiker://images/home_icon_bookmark", "home_icon_bookmark");
        URL_TO_DRAWABLE.put("hiker://images/home_icon_bookmark_group", "home_icon_bookmark_group");
        URL_TO_DRAWABLE.put("hiker://images/home_icon_code", "home_icon_code");
        URL_TO_DRAWABLE.put("hiker://images/home_icon_fold", "home_icon_fold");
        URL_TO_DRAWABLE.put("hiker://images/home_icon_item", "home_icon_item");
        URL_TO_DRAWABLE.put("hiker://images/home_icon_add", "home_icon_add");
        URL_TO_DRAWABLE.put("hiker://images/home_icon_edit", "home_icon_edit");
        URL_TO_DRAWABLE.put("hiker://images/icon_filter", "icon_filter");
        URL_TO_DRAWABLE.put("hiker://images/icon_pure", "icon_pure");
        URL_TO_DRAWABLE.put("hiker://images/icon_code_view", "icon_code_view");
        URL_TO_DRAWABLE.put("hiker://images/icon_domain_config", "icon_domain_config");
        URL_TO_DRAWABLE.put("hiker://images/icon_find", "icon_find");
        URL_TO_DRAWABLE.put("hiker://images/icon_dev", "icon_dev");
        URL_TO_DRAWABLE.put("hiker://images/icon_trans", "icon_trans");
        URL_TO_DRAWABLE.put("hiker://images/icon_save", "icon_save");
        URL_TO_DRAWABLE.put("hiker://images/icon_offline", "icon_offline");
        URL_TO_DRAWABLE.put("hiker://images/icon_share_green", "icon_share_green");
        URL_TO_DRAWABLE.put("hiker://images/icon_my", "icon_my");
        URL_TO_DRAWABLE.put("hiker://images/icon_my_fill", "icon_my_fill");
        URL_TO_DRAWABLE.put("hiker://images/icon_account", "icon_account");
        URL_TO_DRAWABLE.put("hiker://images/icon_rect", "icon_rect");
        URL_TO_DRAWABLE.put("hiker://images/icon_rect_selected", "icon_rect_selected");
        URL_TO_DRAWABLE.put("hiker://images/icon_rect_fill", "icon_rect_fill");
        URL_TO_DRAWABLE.put("hiker://images/icon_sel5", "icon_sel5");
        URL_TO_DRAWABLE.put("hiker://images/icon_select", "icon_select");
        URL_TO_DRAWABLE.put("hiker://images/icon_select_fill", "icon_select_fill");
        URL_TO_DRAWABLE.put("hiker://images/icon_sel", "icon_sel");
        URL_TO_DRAWABLE.put("hiker://images/icon_sel_fill", "icon_sel_fill");
        URL_TO_DRAWABLE.put("hiker://images/icon_star", "icon_star");
        URL_TO_DRAWABLE.put("hiker://images/icon_star_fill", "icon_star_fill");
        URL_TO_DRAWABLE.put("hiker://images/icon_star_fill_green", "icon_star_fill_green");
        URL_TO_DRAWABLE.put("hiker://images/icon_star_fill_red", "icon_star_fill_red");
        URL_TO_DRAWABLE.put("hiker://images/icon_info", "icon_info");
        URL_TO_DRAWABLE.put("hiker://images/icon_info_fill", "icon_info_fill");
        URL_TO_DRAWABLE.put("hiker://images/icon_add2", "icon_add2");
        URL_TO_DRAWABLE.put("hiker://images/icon_bottom", "icon_bottom");
        URL_TO_DRAWABLE.put("hiker://images/icon_down5", "icon_down5");
        URL_TO_DRAWABLE.put("hiker://images/icon_clock", "icon_clock");
        URL_TO_DRAWABLE.put("hiker://images/icon_close1", "icon_close1");
        URL_TO_DRAWABLE.put("hiker://images/icon_close_2", "icon_close2");
        URL_TO_DRAWABLE.put("hiker://images/icon_close_2_fill", "icon_close_2_fill");
        URL_TO_DRAWABLE.put("hiker://images/icon_del", "icon_del");
        URL_TO_DRAWABLE.put("hiker://images/icon_foot", "icon_foot");
        URL_TO_DRAWABLE.put("hiker://images/icon_left5", "icon_left5");
        URL_TO_DRAWABLE.put("hiker://images/icon_right5", "icon_right5");
        URL_TO_DRAWABLE.put("hiker://images/icon_like", "icon_like");
        URL_TO_DRAWABLE.put("hiker://images/icon_like_fill", "icon_like_fill");
        URL_TO_DRAWABLE.put("hiker://images/icon_menu2", "icon_menu2");
        URL_TO_DRAWABLE.put("hiker://images/icon_search5", "icon_search5");
        URL_TO_DRAWABLE.put("hiker://images/icon_setting5", "icon_setting5");
        URL_TO_DRAWABLE.put("hiker://images/icon_share5", "icon_share5");
        URL_TO_DRAWABLE.put("hiker://images/menu_new2", "menu_new2");
        URL_TO_DRAWABLE.put("hiker://images/menu_book2", "menu_book2");
        URL_TO_DRAWABLE.put("hiker://images/menu_now2", "menu_now2");
        URL_TO_DRAWABLE.put("hiker://images/menu_search2", "menu_search2");
        URL_TO_DRAWABLE.put("hiker://images/menu_rules2", "menu_rules2");
        URL_TO_DRAWABLE.put("hiker://images/menu_clear2", "menu_clear2");
        URL_TO_DRAWABLE.put("hiker://images/menu_backup2", "menu_backup2");
        URL_TO_DRAWABLE.put("hiker://images/menu_back2", "menu_back2");
        URL_TO_DRAWABLE.put("hiker://images/code", "home_code");
        URL_TO_DRAWABLE.put("hiker://images/backup", "main_menu_backup");
        URL_TO_DRAWABLE.put("hiker://images/ua", "home_ua");
        URL_TO_DRAWABLE.put("hiker://images/block", "home_block");
        URL_TO_DRAWABLE.put("hiker://images/collection", "home_collection");
        URL_TO_DRAWABLE.put("hiker://images/bookmark", "home_bookmark");
        URL_TO_DRAWABLE.put("hiker://images/history", "home_history");
        URL_TO_DRAWABLE.put("hiker://images/home", "home_home");
        URL_TO_DRAWABLE.put("hiker://images/account", "account_home");
        URL_TO_DRAWABLE.put("hiker://images/placeholder", "nothing");
        URL_TO_DRAWABLE.put("hiker://images/bbs", "bbs");
        URL_TO_DRAWABLE.put("hiker://images/card_bg", "card_bg");
        URL_TO_DRAWABLE.put("hiker://images/search", "ic_main_nav_search");
        URL_TO_DRAWABLE.put("hiker://images/logo", "logo");
        URL_TO_DRAWABLE.put("hiker://images/\u8bbe\u7f6e", "setting_green");
        URL_TO_DRAWABLE.put("hiker://images/\u4e66\u7b7e", "bookmark_green");
        URL_TO_DRAWABLE.put("hiker://images/\u8c46\u74e3", "douban");
        URL_TO_DRAWABLE.put("hiker://images/\u5f00\u5173", "switch_source");
        URL_TO_DRAWABLE.put("hiker://images/\u5386\u53f2", "his_green");
        URL_TO_DRAWABLE.put("hiker://images/\u6dfb\u52a0", "add_green");
        URL_TO_DRAWABLE.put("hiker://images/\u4fee\u6539", "edit_black");
        URL_TO_DRAWABLE.put("hiker://images/\u5220\u9664", "delete");
        URL_TO_DRAWABLE.put("hiker://images/\u79fb\u52a8", "mv");
        URL_TO_DRAWABLE.put("hiker://images/\u4e91\u5907\u4efd", "backup_cloud");
        URL_TO_DRAWABLE.put("hiker://images/home_bg", "home_bg");
        URL_TO_DRAWABLE.put("hiker://images/home_late", "home_late");
        URL_TO_DRAWABLE.put("hiker://images/home_pic1", "home_pic1");
        URL_TO_DRAWABLE.put("hiker://images/home_pic2", "home_pic2");
        URL_TO_DRAWABLE.put("hiker://images/home_pic3", "home_pic3");
        URL_TO_DRAWABLE.put("hiker://images/home_pic4", "home_pic4");
        URL_TO_DRAWABLE.put("hiker://images/cover", "cover");
        URL_TO_DRAWABLE.put("hiker://images/icon_baidu", "icon_baidu");
        URL_TO_DRAWABLE.put("hiker://images/icon_360", "icon_360");
        URL_TO_DRAWABLE.put("hiker://images/icon_fsou", "icon_fsou");
        URL_TO_DRAWABLE.put("hiker://images/icon_wuzhui", "icon_wuzhui");
        URL_TO_DRAWABLE.put("hiker://images/icon_kuake", "icon_kuake");
        URL_TO_DRAWABLE.put("hiker://images/icon_shenma", "icon_shenma");
        URL_TO_DRAWABLE.put("hiker://images/icon_google", "icon_google");
        URL_TO_DRAWABLE.put("hiker://images/icon_sougou", "icon_sougou");
        URL_TO_DRAWABLE.put("hiker://images/icon_bing", "icon_bing");
        URL_TO_DRAWABLE.put("hiker://images/icon_zhihu", "icon_zhihu");
        URL_TO_DRAWABLE.put("hiker://images/icon_weibo", "icon_weibo");
        URL_TO_DRAWABLE.put("hiker://images/icon_weixin", "icon_weixin");
        URL_TO_DRAWABLE.put("hiker://images/icon_toutiao", "icon_toutiao");
        URL_TO_DRAWABLE.put("hiker://images/icon_yep", "icon_yep");
        URL_TO_DRAWABLE.put("hiker://images/icon_yandex", "icon_yandex");
        URL_TO_DRAWABLE.put("hiker://images/icon_duck", "icon_duck");
        URL_TO_DRAWABLE.put("hiker://images/icon_zhiwang", "icon_zhiwang");
        URL_TO_DRAWABLE.put("hiker://images/icon_360ai", "icon_nami_logo");
        URL_TO_DRAWABLE.put("hiker://images/icon_meta", "icon_meta");
        URL_TO_DRAWABLE.put("hiker://images/icon_kfind", "icon_kfind");
        URL_TO_DRAWABLE.put("hiker://images/icon_phind", "icon_phind");
        URL_TO_DRAWABLE.put("hiker://images/icon_felo", "icon_felo");
        URL_TO_DRAWABLE.put("hiker://images/icon_iask", "icon_iask");
        URL_TO_DRAWABLE.put("hiker://images/icon_baidu_ai", "icon_baidu_ai");
        URL_TO_DRAWABLE.put("hiker://images/icon_isou", "icon_isou");
        URL_TO_DRAWABLE.put("hiker://images/icon_thinkany", "icon_thinkany");
        URL_TO_DRAWABLE.put("hiker://images/icon_zhihu_ai", "icon_zhihu_ai");
        URL_TO_DRAWABLE.put("hiker://images/ic_bookmark_url", "ic_bookmark_url");
        URL_TO_DRAWABLE.put("hiker://images/ic_bookmark_folder", "ic_bookmark_folder");
        URL_TO_DRAWABLE.put("hiker://images/icon_ai_big", "icon_ai_big");
        URL_TO_DRAWABLE.put("hiker://images/icon_ai", "icon_ai");
        // 2026-11-02 审计补齐段：以下 11 对在 8.83 枚举里有、早先转写时整表漏掉
        // （url 与同名 drawable 文件都缺），图文件已从 8.83 原包逐字拷回 mobile res。
        URL_TO_DRAWABLE.put("hiker://images/icon_bookmark_add", "icon_bookmark_add");
        URL_TO_DRAWABLE.put("hiker://images/icon_browser_bookmark", "icon_browser_bookmark");
        URL_TO_DRAWABLE.put("hiker://images/icon_browser_history", "icon_browser_history");
        URL_TO_DRAWABLE.put("hiker://images/icon_box", "icon_box");
        URL_TO_DRAWABLE.put("hiker://images/icon_download", "icon_download");
        URL_TO_DRAWABLE.put("hiker://images/icon_his", "icon_his");
        URL_TO_DRAWABLE.put("hiker://images/icon_plugin", "icon_plugin");
        URL_TO_DRAWABLE.put("hiker://images/icon_refresh", "icon_refresh");
        URL_TO_DRAWABLE.put("hiker://images/icon_ua", "icon_ua");
        URL_TO_DRAWABLE.put("hiker://images/icon_xiutan", "icon_xiutan");
        URL_TO_DRAWABLE.put("hiker://images/video", "home_video");
    }

    private HkImageUrlMap() {
    }

    /** 照 getIdByUrl：命中返回本 App 的 drawable 资源 id，未命中或资源不存在返回 -1。 */
    public static int getIdByUrl(Context context, String url) {
        if (context == null || url == null) return -1;
        String name = URL_TO_DRAWABLE.get(url);
        if (name == null) return -1;
        int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        return id > 0 ? id : -1;
    }
}
