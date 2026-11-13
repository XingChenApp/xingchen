package com.example.hikerview.service.parser;

import com.example.hikerview.utils.StringUtil;

/**
 * 照官 8.83 本体移植（jadx 官包源）——本批只落插件实际调用的
 * isPageUrl（hiker://page/ 前缀判定，逐字照官）。其余成员是官的
 * 页规则解析/跳转 machinery（LitePal 规则库＋FilmListActivity），
 * 星辰侧页面路由归引擎自有 HkRouter，不落，记名在案。
 */
public class PageParser {

    public static boolean isPageUrl(String str) {
        return StringUtil.isNotEmpty(str) && str.startsWith("hiker://page/");
    }
}
