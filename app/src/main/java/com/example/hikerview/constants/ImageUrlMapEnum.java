package com.example.hikerview.constants;

import com.example.hikerview.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 照官 8.83 本体移植（jadx 官包源）：hiker://images/* 内置图映射表，
 * 条目与资源逐条照官；资源文件自官包 res 原样拷入。
 */
public enum ImageUrlMapEnum {
    icon1(R.drawable.icon1, "hiker://images/icon1"),
    icon2(R.drawable.icon2, "hiker://images/icon2"),
    icon3(R.drawable.icon3, "hiker://images/icon3"),
    icon4(R.drawable.icon4, "hiker://images/icon4"),
    home_bookmark2(R.drawable.home_bookmark2, "hiker://images/home_bookmark2"),
    home_download(R.drawable.home_download, "hiker://images/home_download"),
    home_history2(R.drawable.home_history2, "hiker://images/home_history2"),
    home_setting(R.drawable.home_setting, "hiker://images/home_setting"),
    home_tools(R.drawable.home_tools, "hiker://images/home_tools"),
    icon_fire(R.drawable.icon_fire, "hiker://images/icon_fire"),
    icon_hot(R.drawable.icon_hot, "hiker://images/icon_hot"),
    icon_top(R.drawable.icon_top, "hiker://images/icon_top"),
    icon_class(R.drawable.icon_class, "hiker://images/icon_class"),
    tree1(R.drawable.tree1, "hiker://images/tree1"),
    tree2(R.drawable.tree2, "hiker://images/tree2"),
    tree3(R.drawable.tree3, "hiker://images/tree3"),
    tree4(R.drawable.tree4, "hiker://images/tree4"),
    icon_folder3(R.drawable.icon_folder3, "hiker://images/icon_folder3"),
    icon_app3(R.drawable.icon_app3, "hiker://images/icon_app3"),
    icon_zip2(R.drawable.icon_zip2, "hiker://images/icon_zip2"),
    icon_music3(R.drawable.icon_music3, "hiker://images/icon_music3"),
    icon_txt2(R.drawable.icon_txt2, "hiker://images/icon_txt2"),
    icon_pic3(R.drawable.icon_pic3, "hiker://images/icon_pic3"),
    icon_video2(R.drawable.icon_video2, "hiker://images/icon_video2"),
    icon_unknown(R.drawable.icon_unknown, "hiker://images/icon_unknown"),
    icon_download6(R.drawable.icon_download6, "hiker://images/icon_download6"),
    icon_edit6(R.drawable.icon_edit6, "hiker://images/icon_edit6"),
    icon_search6(R.drawable.icon_search6, "hiker://images/icon_search6"),
    icon_good6(R.drawable.icon_good6, "hiker://images/icon_good6"),
    icon_cloud6(R.drawable.icon_cloud6, "hiker://images/icon_cloud6"),
    icon_del6(R.drawable.icon_del6, "hiker://images/icon_del6"),
    icon_left6(R.drawable.icon_left6, "hiker://images/icon_left6"),
    icon_menu6(R.drawable.icon_menu6, "hiker://images/icon_menu6"),
    icon_time6(R.drawable.icon_time6, "hiker://images/icon_time6"),
    icon_setting6(R.drawable.icon_setting6, "hiker://images/icon_setting6"),
    icon_right6(R.drawable.icon_right6, "hiker://images/icon_right6"),
    rule_type_all(R.drawable.rule_type_all, "hiker://images/rule_type_all"),
    rule_type_video(R.drawable.rule_type_video, "hiker://images/rule_type_video"),
    rule_type_audio(R.drawable.rule_type_audio, "hiker://images/rule_type_audio"),
    rule_type_live(R.drawable.rule_type_live, "hiker://images/rule_type_live"),
    rule_type_comics(R.drawable.rule_type_comics, "hiker://images/rule_type_comics"),
    rule_type_read(R.drawable.rule_type_read, "hiker://images/rule_type_read"),
    rule_type_news(R.drawable.rule_type_news, "hiker://images/rule_type_news"),
    rule_type_pic(R.drawable.rule_type_pic, "hiker://images/rule_type_pic"),
    rule_type_tool(R.drawable.rule_type_tool, "hiker://images/rule_type_tool"),
    rule_type_other(R.drawable.rule_type_other, "hiker://images/rule_type_other"),
    home_icon_bookmark(R.drawable.home_icon_bookmark, "hiker://images/home_icon_bookmark"),
    home_icon_bookmark_group(R.drawable.home_icon_bookmark_group, "hiker://images/home_icon_bookmark_group"),
    home_icon_code(R.drawable.home_icon_code, "hiker://images/home_icon_code"),
    home_icon_fold(R.drawable.home_icon_fold, "hiker://images/home_icon_fold"),
    home_icon_item(R.drawable.home_icon_item, "hiker://images/home_icon_item"),
    home_icon_add(R.drawable.home_icon_add, "hiker://images/home_icon_add"),
    home_icon_edit(R.drawable.home_icon_edit, "hiker://images/home_icon_edit"),
    icon_browser_bookmark(R.drawable.icon_browser_bookmark, "hiker://images/icon_browser_bookmark"),
    icon_bookmark_add(R.drawable.icon_bookmark_add, "hiker://images/icon_bookmark_add"),
    icon_his(R.drawable.icon_his, "hiker://images/icon_his"),
    icon_download(R.drawable.icon_download, "hiker://images/icon_download"),
    icon_plugin(R.drawable.icon_plugin, "hiker://images/icon_plugin"),
    icon_xiutan(R.drawable.icon_xiutan, "hiker://images/icon_xiutan"),
    icon_ua(R.drawable.icon_ua, "hiker://images/icon_ua"),
    icon_browser_history(R.drawable.icon_browser_history, "hiker://images/icon_browser_history"),
    icon_refresh(R.drawable.icon_refresh, "hiker://images/icon_refresh"),
    icon_box(R.drawable.icon_browser_bookmark, "hiker://images/icon_box"),
    icon_filter(R.drawable.icon_filter, "hiker://images/icon_filter"),
    icon_pure(R.drawable.icon_pure, "hiker://images/icon_pure"),
    icon_code_view(R.drawable.icon_code_view, "hiker://images/icon_code_view"),
    icon_mark_ad(R.drawable.icon_mark_ad, "hiker://images/icon_browser_bookmark"),
    icon_domain_config(R.drawable.icon_domain_config, "hiker://images/icon_domain_config"),
    icon_find(R.drawable.icon_find, "hiker://images/icon_find"),
    icon_dev(R.drawable.icon_dev, "hiker://images/icon_dev"),
    icon_trans(R.drawable.icon_trans, "hiker://images/icon_trans"),
    icon_save(R.drawable.icon_save, "hiker://images/icon_save"),
    icon_offline(R.drawable.icon_offline, "hiker://images/icon_offline"),
    icon_share_green(R.drawable.icon_share_green, "hiker://images/icon_share_green"),
    icon_my(R.drawable.icon_my, "hiker://images/icon_my"),
    icon_my_fill(R.drawable.icon_my_fill, "hiker://images/icon_my_fill"),
    icon_account(R.drawable.icon_account, "hiker://images/icon_account"),
    icon_rect(R.drawable.icon_rect, "hiker://images/icon_rect"),
    icon_rect_selected(R.drawable.icon_rect_selected, "hiker://images/icon_rect_selected"),
    icon_rect_fill(R.drawable.icon_rect_fill, "hiker://images/icon_rect_fill"),
    icon_sel5(R.drawable.icon_sel5, "hiker://images/icon_sel5"),
    icon_select(R.drawable.icon_select, "hiker://images/icon_select"),
    icon_select_fill(R.drawable.icon_select_fill, "hiker://images/icon_select_fill"),
    icon_sel(R.drawable.icon_sel, "hiker://images/icon_sel"),
    icon_sel_fill(R.drawable.icon_sel_fill, "hiker://images/icon_sel_fill"),
    icon_star(R.drawable.icon_star, "hiker://images/icon_star"),
    icon_star_fill(R.drawable.icon_star_fill, "hiker://images/icon_star_fill"),
    icon_star_fill_green(R.drawable.icon_star_fill_green, "hiker://images/icon_star_fill_green"),
    icon_star_fill_red(R.drawable.icon_star_fill_red, "hiker://images/icon_star_fill_red"),
    icon_info(R.drawable.icon_info, "hiker://images/icon_info"),
    icon_info_fill(R.drawable.icon_info_fill, "hiker://images/icon_info_fill"),
    icon_add2(R.drawable.icon_add2, "hiker://images/icon_add2"),
    icon_bottom(R.drawable.icon_bottom, "hiker://images/icon_bottom"),
    icon_down5(R.drawable.icon_down5, "hiker://images/icon_down5"),
    icon_clock(R.drawable.icon_clock, "hiker://images/icon_clock"),
    icon_close1(R.drawable.icon_close1, "hiker://images/icon_close1"),
    icon_close_2(R.drawable.icon_close2, "hiker://images/icon_close_2"),
    icon_close_2_fill(R.drawable.icon_close_2_fill, "hiker://images/icon_close_2_fill"),
    icon_del(R.drawable.icon_del, "hiker://images/icon_del"),
    icon_foot(R.drawable.icon_foot, "hiker://images/icon_foot"),
    icon_left5(R.drawable.icon_left5, "hiker://images/icon_left5"),
    icon_right5(R.drawable.icon_right5, "hiker://images/icon_right5"),
    icon_like(R.drawable.icon_like, "hiker://images/icon_like"),
    icon_like_fill(R.drawable.icon_like_fill, "hiker://images/icon_like_fill"),
    icon_menu2(R.drawable.icon_menu2, "hiker://images/icon_menu2"),
    icon_search5(R.drawable.icon_search5, "hiker://images/icon_search5"),
    icon_setting5(R.drawable.icon_setting5, "hiker://images/icon_setting5"),
    icon_share5(R.drawable.icon_share5, "hiker://images/icon_share5"),
    menu_new2(R.drawable.menu_new2, "hiker://images/menu_new2"),
    menu_book2(R.drawable.menu_book2, "hiker://images/menu_book2"),
    menu_now2(R.drawable.menu_now2, "hiker://images/menu_now2"),
    menu_search2(R.drawable.menu_search2, "hiker://images/menu_search2"),
    menu_rules2(R.drawable.menu_rules2, "hiker://images/menu_rules2"),
    menu_clear2(R.drawable.menu_clear2, "hiker://images/menu_clear2"),
    menu_backup2(R.drawable.menu_backup2, "hiker://images/menu_backup2"),
    menu_back2(R.drawable.menu_back2, "hiker://images/menu_back2"),
    code(R.drawable.home_code, "hiker://images/code"),
    backup(R.drawable.main_menu_backup, "hiker://images/backup"),
    ua(R.drawable.home_ua, "hiker://images/ua"),
    block(R.drawable.home_block, "hiker://images/block"),
    collection(R.drawable.home_collection, "hiker://images/collection"),
    bookmark(R.drawable.home_bookmark, "hiker://images/bookmark"),
    history(R.drawable.home_history, "hiker://images/history"),
    video(R.drawable.home_video, "hiker://images/video"),
    home(R.drawable.home_home, "hiker://images/home"),
    account(R.drawable.account_home, "hiker://images/account"),
    placeholder(R.drawable.nothing, "hiker://images/placeholder"),
    bbs(R.drawable.bbs, "hiker://images/bbs"),
    card_bg(R.drawable.card_bg, "hiker://images/card_bg"),
    search(R.drawable.ic_main_nav_search, "hiker://images/search"),
    logo(R.drawable.logo, "hiker://images/logo"),
    setting_green(R.drawable.setting_green, "hiker://images/设置"),
    bookmark_green(R.drawable.bookmark_green, "hiker://images/书签"),
    douban(R.drawable.douban, "hiker://images/豆瓣"),
    switch_source(R.drawable.switch_source, "hiker://images/开关"),
    his_green(R.drawable.his_green, "hiker://images/历史"),
    add_green(R.drawable.add_green, "hiker://images/添加"),
    edit_black(R.drawable.edit_black, "hiker://images/修改"),
    delete(R.drawable.delete, "hiker://images/删除"),
    mv(R.drawable.mv, "hiker://images/移动"),
    backup_cloud(R.drawable.backup_cloud, "hiker://images/云备份"),
    home_bg(R.drawable.home_bg, "hiker://images/home_bg"),
    home_late(R.drawable.home_late, "hiker://images/home_late"),
    home_pic1(R.drawable.home_pic1, "hiker://images/home_pic1"),
    home_pic2(R.drawable.home_pic2, "hiker://images/home_pic2"),
    home_pic3(R.drawable.home_pic3, "hiker://images/home_pic3"),
    home_pic4(R.drawable.home_pic4, "hiker://images/home_pic4"),
    cover(R.drawable.cover, "hiker://images/cover"),
    icon_baidu(R.drawable.icon_baidu, "hiker://images/icon_baidu"),
    icon_360(R.drawable.icon_360, "hiker://images/icon_360"),
    icon_fsou(R.drawable.icon_fsou, "hiker://images/icon_fsou"),
    icon_wuzhui(R.drawable.icon_wuzhui, "hiker://images/icon_wuzhui"),
    icon_kuake(R.drawable.icon_kuake, "hiker://images/icon_kuake"),
    icon_shenma(R.drawable.icon_shenma, "hiker://images/icon_shenma"),
    icon_google(R.drawable.icon_google, "hiker://images/icon_google"),
    icon_sougou(R.drawable.icon_sougou, "hiker://images/icon_sougou"),
    icon_bing(R.drawable.icon_bing, "hiker://images/icon_bing"),
    icon_zhihu(R.drawable.icon_zhihu, "hiker://images/icon_zhihu"),
    icon_weibo(R.drawable.icon_weibo, "hiker://images/icon_weibo"),
    icon_weixin(R.drawable.icon_weixin, "hiker://images/icon_weixin"),
    icon_toutiao(R.drawable.icon_toutiao, "hiker://images/icon_toutiao"),
    icon_yep(R.drawable.icon_yep, "hiker://images/icon_yep"),
    icon_yandex(R.drawable.icon_yandex, "hiker://images/icon_yandex"),
    icon_duck(R.drawable.icon_duck, "hiker://images/icon_duck"),
    icon_zhiwang(R.drawable.icon_zhiwang, "hiker://images/icon_zhiwang"),
    icon_360ai(R.drawable.icon_nami_logo, "hiker://images/icon_360ai"),
    icon_meta(R.drawable.icon_meta, "hiker://images/icon_meta"),
    icon_kfind(R.drawable.icon_kfind, "hiker://images/icon_kfind"),
    icon_phind(R.drawable.icon_phind, "hiker://images/icon_phind"),
    icon_felo(R.drawable.icon_felo, "hiker://images/icon_felo"),
    icon_iask(R.drawable.icon_iask, "hiker://images/icon_iask"),
    icon_baidu_ai(R.drawable.icon_baidu_ai, "hiker://images/icon_baidu_ai"),
    icon_isou(R.drawable.icon_isou, "hiker://images/icon_isou"),
    icon_thinkany(R.drawable.icon_thinkany, "hiker://images/icon_thinkany"),
    icon_zhihu_ai(R.drawable.icon_zhihu_ai, "hiker://images/icon_zhihu_ai"),
    ic_bookmark_url(R.drawable.ic_bookmark_url, "hiker://images/ic_bookmark_url"),
    ic_bookmark_folder(R.drawable.ic_bookmark_folder, "hiker://images/ic_bookmark_folder"),
    icon_ai_big(R.drawable.icon_ai_big, "hiker://images/icon_ai_big"),
    icon_ai(R.drawable.icon_ai, "hiker://images/icon_ai");

    private final int id;
    private final String url;

    ImageUrlMapEnum(int i, String str) {
        this.id = i;
        this.url = str;
    }

    public String getUrl() {
        return this.url;
    }

    public int getId() {
        return this.id;
    }

    public static int getIdByUrl(String str) {
        for (ImageUrlMapEnum imageUrlMapEnum : values()) {
            if (imageUrlMapEnum.getUrl().equals(str)) {
                return imageUrlMapEnum.getId();
            }
        }
        return -1;
    }

    public static List<ImageUrlMapEnum> getList() {
        return new ArrayList<>(Arrays.asList(values()));
    }
}
