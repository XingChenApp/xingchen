package com.example.hikerview.service.parser;

import android.app.Activity;
import android.content.Intent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.hikerview.service.exception.ParseException;
import com.example.hikerview.ui.browser.model.UrlDetector;
import com.example.hikerview.ui.browser.util.CollectionUtil;
import com.example.hikerview.ui.home.model.ArticleListPageRule;
import com.example.hikerview.ui.home.model.ArticleListRule;
import com.example.hikerview.utils.StringUtil;
import com.example.hikerview.utils.ToastMgr;
import com.fongmi.android.tv.api.hk.HkParserKit;
import com.fongmi.android.tv.ui.hk.HkListActivity;

import org.litepal.LitePal;

import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * 照官 8.83 本体移植（官源 smali/com/example/hikerview/service/parser/PageParser.smali）。
 * 已落 isPageUrl、getUrl、parsePageRule（两重载）、getNextPage，逐字照官（①团解锁，
 * 官 model ArticleListRule/ArticleListPageRule 与 LitePal 存储层已同批落位）。
 * goPageByPageUrl 已补落（2026-11-14 ③团宿主面批）：官宿主 FilmListActivity
 * 接本树 HkListActivity（putTempRule 同名同形、类头有对位注）；官对
 * FilmListActivity.tempParentData（public static）的直写清槽，接
 * HkListActivity.clearTempParentData 公开清槽口（同批加）。
 * 记名：官 StringUtils.replaceOnce（commons-lang3，本树未引该库）接本树既有同名转写
 * HkParserKit.replaceOnce（同语义，HkParserKit 类头有 lang3 对位记名）；LitePal 查询走
 * ①团新引 LitePal，与官 FluentQuery.findFirst 同形。
 */
public class PageParser {

    public static boolean isPageUrl(String str) {
        return StringUtil.isNotEmpty(str) && str.startsWith("hiker://page/");
    }

    public static String getUrl(String str, String str2) {
        JSONObject jSONObject = null;
        if (StringUtil.isNotEmpty(str2) && str2.startsWith("{") && str2.endsWith("}")) {
            try {
                jSONObject = JSON.parseObject(str2);
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        if (jSONObject != null && jSONObject.containsKey("url") && jSONObject.getString("url") != null) {
            str = jSONObject.getString("url");
        }
        return str;
    }

    /**
     * 官 goPageByPageUrl（PageParser.smali:377，.line 169-188）：getNextPage(null,
     * pageUrl, null) 得下一页规则，空即回；Intent 指向宿主列表页（官
     * FilmListActivity 接本树 HkListActivity），putTempRule 传 fastjson 序列化
     * 原文（在 #noHistory# 剥除之前，照官序），extras 依次 title/parentTitle
     * （同为规则标题）/parentUrl（入参 pageUrl）；url 含 "#autoPage#" 时清
     * 宿主 tempParentData 静态槽（官直写 public static 字段 null，接
     * HkListActivity.clearTempParentData）；含 "#noHistory#" 时 replaceOnce
     * 剥除该标记后回写 url；最后 startActivityForResult(intent, 2)。
     * 全程 try/catch Exception：Timber.e＋ToastMgr.shortCenter 出异常消息。
     */
    public static void goPageByPageUrl(Activity activity, String pageUrl) {
        try {
            ArticleListRule next = getNextPage(null, pageUrl, null);
            if (next == null) {
                return;
            }
            String title = next.getTitle();
            Intent intent = new Intent(activity, HkListActivity.class);
            HkListActivity.putTempRule(intent, JSON.toJSONString(next));
            intent.putExtra("title", title);
            intent.putExtra("parentTitle", title);
            intent.putExtra("parentUrl", pageUrl);
            if (next.getUrl().contains("#autoPage#")) {
                HkListActivity.clearTempParentData();
            }
            if (next.getUrl().contains("#noHistory#")) {
                next.setUrl(HkParserKit.replaceOnce(next.getUrl(), "#noHistory#", ""));
            }
            activity.startActivityForResult(intent, 2);
        } catch (Exception e) {
            Timber.e(e);
            ToastMgr.shortCenter(activity, e.getMessage());
        }
    }

    public static ArticleListRule getNextPage(ArticleListRule articleListRule, String pageUrl, String paramsJson) throws ParseException {
        if (!isPageUrl(pageUrl)) {
            return null;
        }
        ArticleListRule next = new ArticleListRule();
        String ruleTitle = null;
        JSONObject paramsObj = null;
        if (StringUtil.isNotEmpty(paramsJson) && paramsJson.startsWith("{") && paramsJson.endsWith("}")) {
            try {
                paramsObj = JSON.parseObject(paramsJson);
                if (paramsObj != null) {
                    try {
                        if (paramsObj.containsKey("rule")) {
                            ruleTitle = paramsObj.getString("rule");
                        }
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                }
            } catch (Exception e) {
                paramsObj = null;
                Timber.e(e);
            }
        }
        ArticleListPageRule pageRule = parsePageRule(articleListRule, ruleTitle, pageUrl);
        ArticleListRule parentRule = pageRule.getParentRule();
        next.setFind_rule(pageRule.getRule());
        next.setPreRule(parentRule.getPreRule());
        next.setProxy(parentRule.getProxy());
        next.setGroup(parentRule.getGroup());
        next.setUa(parentRule.getUa());
        next.setTitle(parentRule.getTitle());
        next.setLast_chapter_rule(parentRule.getLast_chapter_rule());
        next.setCol_type(pageRule.getCol_type());
        next.setPages(parentRule.getPages());
        if (StringUtil.isEmpty(next.getCol_type()) || "*".equals(next.getCol_type())) {
            next.setCol_type(parentRule.getCol_type());
        }
        if (pageRule.getParams().containsKey("url")) {
            next.setUrl((String) pageRule.getParams().get("url"));
        } else if (paramsObj != null && paramsObj.containsKey("url") && paramsObj.getString("url") != null) {
            next.setUrl(paramsObj.getString("url"));
        } else {
            next.setUrl(pageUrl);
        }
        if (StringUtil.isNotEmpty(paramsJson) && paramsJson.startsWith("{") && paramsJson.endsWith("}")) {
            next.setParams(paramsJson);
        }
        return next;
    }

    public static ArticleListPageRule parsePageRule(ArticleListRule articleListRule, String pageUrl) throws ParseException {
        return parsePageRule(articleListRule, null, pageUrl);
    }

    private static ArticleListPageRule parsePageRule(ArticleListRule articleListRule, String ruleTitle, String pageUrl) throws ParseException {
        String title = articleListRule == null ? null : articleListRule.getTitle();
        Map<String, String> params = HttpParser.getParamsByUrl(pageUrl);
        if (StringUtil.isNotEmpty(ruleTitle) && !params.containsKey("rule")) {
            params.put("rule", ruleTitle);
        }
        if (params.containsKey("rule")) {
            title = params.get("rule");
            if (StringUtil.isEmpty(title)) {
                title = articleListRule == null ? null : articleListRule.getTitle();
            } else {
                ArticleListRule found = LitePal.where("title = ?", title).findFirst(ArticleListRule.class);
                if (found != null) {
                    articleListRule = found;
                } else {
                    throw new ParseException("找不到“" + title + "”这个小程序");
                }
            }
        }
        String path = pageUrl.split(";")[0];
        path = StringUtil.splitUrlByQuestionMark(path)[0];
        path = HkParserKit.replaceOnce(path, "hiker://page/", "");
        path = UrlDetector.clearTag(path);
        if (articleListRule != null) {
            ArticleListPageRule foundPage = null;
            List<ArticleListPageRule> pageList = articleListRule.getPageList();
            if (CollectionUtil.isNotEmpty(pageList)) {
                for (ArticleListPageRule page : pageList) {
                    if (path.equals(page.getPath())) {
                        foundPage = page;
                        break;
                    }
                }
            }
            if (foundPage != null) {
                foundPage.setParent(title);
                foundPage.setParentRule(articleListRule);
                foundPage.setParams(params);
                return foundPage;
            }
            throw new ParseException("找不到“" + path + "”这个页面");
        }
        throw new ParseException("找不到对应的小程序");
    }
}
