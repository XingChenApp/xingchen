package com.example.hikerview.ui.home.model;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.example.hikerview.utils.HeavyTaskUtil;
import com.example.hikerview.utils.PinyinUtil;
import com.example.hikerview.utils.StringUtil;
import com.example.hikerview.utils.UriUtils;
import com.fongmi.android.tv.App;

import org.litepal.annotation.Column;
import org.litepal.crud.LitePalSupport;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * 照官 8.83 本体逐字转写（官源 smali_classes2/com/example/hikerview/ui/home/model/ArticleListRule.smali）。
 * 继承官 LitePalSupport、implements Cloneable/Comparable，42 字段与 fastjson ordinal、
 * LitePal @Column(ignore) 注解逐项照官（ordinal 重复值照官原样）；非平凡法 compareTo/
 * copyFrom/fromJO/deleteFiles/getPageList/save/saveOrUpdate 照官 smali 逐条落。
 * 接法记名两处：①deleteFiles 里官 Application.application 接本树 App.get()（本树既定等价）；
 * ②官 JSEngine.getFilesDir() 未在本树另立 JSEngine 类，按其官公式（UriUtils.getRootDir＋
 * /rules/files/，JSEngine.smali:2747）在本类私有 getFilesDir() 复刻，公式逐字同官。
 */
public class ArticleListRule extends LitePalSupport implements Cloneable, Comparable<ArticleListRule> {

    @JSONField(ordinal = 8)
    private String area_name;
    @JSONField(ordinal = 9)
    private String area_url;
    @JSONField(ordinal = 2)
    private String author;
    @JSONField(deserialize = false, serialize = false)
    @Column(ignore = true)
    private boolean batchDeleting;
    @JSONField(ordinal = 13)
    private String chapter_find;
    @JSONField(ordinal = 6)
    private String class_name;
    @JSONField(ordinal = 7)
    private String class_url;
    @JSONField(ordinal = 5)
    private String col_type;
    @JSONField(ordinal = 25)
    private String detail_col_type;
    @JSONField(ordinal = 26)
    private String detail_find_rule;
    @Column(ignore = true)
    private String displayName;
    @JSONField(ordinal = 12)
    private String find_rule;
    private String firstHeader;
    @JSONField(deserialize = false, serialize = false)
    private long gmtCreate;
    private long gmtModified;
    @JSONField(ordinal = 19)
    private String group;
    @JSONField(ordinal = 32)
    private String icon;
    @JSONField(deserialize = false, serialize = false)
    private long lastUseTime;
    private String last_chapter_rule;
    @JSONField(ordinal = 14)
    private String movie_find;
    @JSONField(deserialize = false, serialize = false)
    @Column(ignore = true)
    private int orderIndex;
    @JSONField(ordinal = 31)
    private String pages;
    @Column(ignore = true)
    private String params;
    @JSONField(deserialize = false, serialize = false)
    @Column(ignore = true)
    private String pinYin;
    @JSONField(ordinal = 30)
    private String preRule;
    @JSONField(ordinal = 31)
    private String proxy;
    @JSONField(ordinal = 27)
    private String sdetail_col_type;
    @JSONField(ordinal = 28)
    private String sdetail_find_rule;
    @JSONField(ordinal = 15)
    private String searchFind;
    @JSONField(ordinal = 17)
    private String search_url;
    @JSONField(deserialize = false, serialize = false)
    @Column(ignore = true)
    private boolean selected;
    @JSONField(ordinal = 10)
    private String sort_name;
    @JSONField(ordinal = 11)
    private String sort_url;
    @JSONField(deserialize = false, serialize = false)
    private String storage;
    @JSONField(ordinal = 1)
    private String title;
    @JSONField(ordinal = 18)
    private String titleColor;
    @JSONField(ordinal = 4)
    private String type;
    @JSONField(ordinal = 29)
    private String ua;
    @JSONField(ordinal = 4)
    private String url;
    @JSONField(ordinal = 3)
    private int version;
    @JSONField(ordinal = 10)
    private String year_name;
    @JSONField(ordinal = 11)
    private String year_url;

    public ArticleListRule() {
        this.orderIndex = Integer.MAX_VALUE;
    }

    @Override
    public ArticleListRule clone() throws CloneNotSupportedException {
        return (ArticleListRule) super.clone();
    }

    public String getArea_name() {
        return this.area_name;
    }

    public void setArea_name(String str) {
        this.area_name = str;
    }

    public String getArea_url() {
        return this.area_url;
    }

    public void setArea_url(String str) {
        this.area_url = str;
    }

    public String getAuthor() {
        return this.author;
    }

    public void setAuthor(String str) {
        this.author = str;
    }

    public boolean isBatchDeleting() {
        return this.batchDeleting;
    }

    public void setBatchDeleting(boolean z) {
        this.batchDeleting = z;
    }

    public String getChapter_find() {
        return this.chapter_find;
    }

    public void setChapter_find(String str) {
        this.chapter_find = str;
    }

    public String getClass_name() {
        return this.class_name;
    }

    public void setClass_name(String str) {
        this.class_name = str;
    }

    public String getClass_url() {
        return this.class_url;
    }

    public void setClass_url(String str) {
        this.class_url = str;
    }

    public String getCol_type() {
        return this.col_type;
    }

    public void setCol_type(String str) {
        this.col_type = str;
    }

    public String getDetail_col_type() {
        return this.detail_col_type;
    }

    public void setDetail_col_type(String str) {
        this.detail_col_type = str;
    }

    public String getDetail_find_rule() {
        return this.detail_find_rule;
    }

    public void setDetail_find_rule(String str) {
        this.detail_find_rule = str;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String str) {
        this.displayName = str;
    }

    public String getFind_rule() {
        return this.find_rule;
    }

    public void setFind_rule(String str) {
        this.find_rule = str;
    }

    public String getFirstHeader() {
        return this.firstHeader;
    }

    public void setFirstHeader(String str) {
        this.firstHeader = str;
    }

    public long getGmtCreate() {
        return this.gmtCreate;
    }

    public void setGmtCreate(long j) {
        this.gmtCreate = j;
    }

    public long getGmtModified() {
        return this.gmtModified;
    }

    public void setGmtModified(long j) {
        this.gmtModified = j;
    }

    public String getIcon() {
        return this.icon;
    }

    public void setIcon(String str) {
        this.icon = str;
    }

    public long getLastUseTime() {
        return this.lastUseTime;
    }

    public void setLastUseTime(long j) {
        this.lastUseTime = j;
    }

    public String getLast_chapter_rule() {
        return this.last_chapter_rule;
    }

    public void setLast_chapter_rule(String str) {
        this.last_chapter_rule = str;
    }

    public String getMovie_find() {
        return this.movie_find;
    }

    public void setMovie_find(String str) {
        this.movie_find = str;
    }

    public int getOrderIndex() {
        return this.orderIndex;
    }

    public void setOrderIndex(int i) {
        this.orderIndex = i;
    }

    public String getPages() {
        return this.pages;
    }

    public void setPages(String str) {
        this.pages = str;
    }

    public String getParams() {
        return this.params;
    }

    public void setParams(String str) {
        this.params = str;
    }

    public String getPreRule() {
        return this.preRule;
    }

    public void setPreRule(String str) {
        this.preRule = str;
    }

    public String getProxy() {
        return this.proxy;
    }

    public void setProxy(String str) {
        this.proxy = str;
    }

    public String getSdetail_col_type() {
        return this.sdetail_col_type;
    }

    public void setSdetail_col_type(String str) {
        this.sdetail_col_type = str;
    }

    public String getSdetail_find_rule() {
        return this.sdetail_find_rule;
    }

    public void setSdetail_find_rule(String str) {
        this.sdetail_find_rule = str;
    }

    public String getSearchFind() {
        return this.searchFind;
    }

    public void setSearchFind(String str) {
        this.searchFind = str;
    }

    public String getSearch_url() {
        return this.search_url;
    }

    public void setSearch_url(String str) {
        this.search_url = str;
    }

    public boolean isSelected() {
        return this.selected;
    }

    public void setSelected(boolean z) {
        this.selected = z;
    }

    public String getSort_name() {
        return this.sort_name;
    }

    public void setSort_name(String str) {
        this.sort_name = str;
    }

    public String getSort_url() {
        return this.sort_url;
    }

    public void setSort_url(String str) {
        this.sort_url = str;
    }

    public String getStorage() {
        return this.storage;
    }

    public void setStorage(String str) {
        this.storage = str;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String str) {
        this.title = str;
    }

    public String getTitleColor() {
        return this.titleColor;
    }

    public void setTitleColor(String str) {
        this.titleColor = str;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String str) {
        this.type = str;
    }

    public String getUa() {
        return this.ua;
    }

    public void setUa(String str) {
        this.ua = str;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String str) {
        this.url = str;
    }

    public int getVersion() {
        return this.version;
    }

    public void setVersion(int i) {
        this.version = i;
    }

    public String getYear_name() {
        return this.year_name;
    }

    public void setYear_name(String str) {
        this.year_name = str;
    }

    public String getYear_url() {
        return this.year_url;
    }

    public void setYear_url(String str) {
        this.year_url = str;
    }

    public String getGroup() {
        if (this.group == null) {
            this.group = "";
        }
        return this.group;
    }

    public void setGroup(String str) {
        this.group = str;
    }

    public void setPinYin(String str) {
        this.pinYin = str;
    }

    public String getPinYin() {
        if (this.pinYin == null) {
            this.pinYin = "";
        }
        return this.pinYin;
    }

    public long getId() {
        return getBaseObjId();
    }
    @Override
    public int compareTo(ArticleListRule other) {
        int len1 = getGroup().length();
        int len2 = other.getGroup().length();
        int min = Math.min(len1, len2);
        if (min == 0) {
            int diff = len2 - len1;
            if (diff != 0) {
                return diff;
            }
        }
        int cmp = getGroup().compareTo(other.getGroup());
        if (cmp == 0) {
            int orderDiff = getOrderIndex() - other.getOrderIndex();
            if (orderDiff == 0) {
                long createDiff = getGmtCreate() - other.getGmtCreate();
                if (createDiff != 0) {
                    return createDiff > 0 ? 1 : -1;
                }
                if (StringUtil.isEmpty(getPinYin())) {
                    setPinYin(PinyinUtil.getPinyin(getTitle()));
                }
                if (StringUtil.isEmpty(other.getPinYin())) {
                    other.setPinYin(PinyinUtil.getPinyin(other.getTitle()));
                }
                return getPinYin().compareTo(other.getPinYin());
            }
            return orderDiff;
        }
        return cmp;
    }

    public ArticleListRule copyFrom(ArticleListRule rule) {
        setTitle(rule.getTitle());
        setUrl(rule.getUrl());
        setType(rule.getType());
        setCol_type(rule.getCol_type());
        setClass_name(rule.getClass_name());
        setClass_url(rule.getClass_url());
        setArea_name(rule.getArea_name());
        setArea_url(rule.getArea_url());
        setYear_name(rule.getYear_name());
        setYear_url(rule.getYear_url());
        setSort_name(rule.getSort_name());
        setSort_url(rule.getSort_url());
        setFind_rule(rule.getFind_rule());
        setChapter_find(rule.getChapter_find());
        setMovie_find(rule.getMovie_find());
        setFirstHeader(rule.getFirstHeader());
        setSearch_url(rule.getSearch_url());
        setTitleColor(rule.getTitleColor());
        setGroup(rule.getGroup());
        setSearchFind(rule.getSearchFind());
        setDetail_col_type(rule.getDetail_col_type());
        setDetail_find_rule(rule.getDetail_find_rule());
        setSdetail_col_type(rule.getSdetail_col_type());
        setSdetail_find_rule(rule.getSdetail_find_rule());
        setAuthor(rule.getAuthor());
        setVersion(rule.getVersion());
        setUa(rule.getUa());
        setPreRule(rule.getPreRule());
        setLast_chapter_rule(rule.getLast_chapter_rule());
        setPages(rule.getPages());
        setIcon(rule.getIcon());
        setProxy(rule.getProxy());
        setGmtCreate(rule.getGmtCreate());
        setGmtModified(rule.getGmtModified());
        return this;
    }

    public ArticleListRule fromJO(ArticleListRuleJO jo) {
        setTitle(jo.getTitle());
        setUrl(jo.getUrl());
        setType(jo.getType());
        setCol_type(jo.getCol_type());
        setClass_name(jo.getClass_name());
        setClass_url(jo.getClass_url());
        setArea_name(jo.getArea_name());
        setArea_url(jo.getArea_url());
        setYear_name(jo.getYear_name());
        setYear_url(jo.getYear_url());
        setSort_name(jo.getSort_name());
        setSort_url(jo.getSort_url());
        setFind_rule(jo.getFind_rule());
        setChapter_find(jo.getChapter_find());
        setMovie_find(jo.getMovie_find());
        setFirstHeader(jo.getFirstHeader());
        setSearch_url(jo.getSearch_url());
        setTitleColor(jo.getTitleColor());
        setGroup(jo.getGroup());
        setSearchFind(jo.getSearchFind());
        setDetail_col_type(jo.getDetail_col_type());
        setDetail_find_rule(jo.getDetail_find_rule());
        setSdetail_col_type(jo.getSdetail_col_type());
        setSdetail_find_rule(jo.getSdetail_find_rule());
        setAuthor(jo.getAuthor());
        setVersion(jo.getVersion());
        setUa(jo.getUa());
        setPreRule(jo.getPreRule());
        setLast_chapter_rule(jo.getLast_chapter_rule());
        setPages(jo.getPages());
        setIcon(jo.getIcon());
        setProxy(jo.getProxy());
        return this;
    }

    @Override
    public int delete() {
        deleteFiles();
        return super.delete();
    }

    /** 官 JSEngine.getFilesDir() 公式复刻（记名见类头）：rootDir＋/rules/files/。 */
    private static String getFilesDir() {
        return UriUtils.getRootDir(App.get().getApplicationContext()) + File.separator + "rules" + File.separator + "files" + File.separator;
    }

    public void deleteFiles() {
        try {
            if (StringUtil.isNotEmpty(getTitle())) {
                String path = getFilesDir() + getTitle();
                File file = new File(path);
                if (file.exists() && file.isDirectory()) {
                    HeavyTaskUtil.executeNewTask(() -> com.example.hikerview.utils.FileUtil.deleteDirs(path));
                }
                String path2 = UriUtils.getRootDir(App.get().getApplicationContext()) + File.separator + "data" + File.separator + getTitle();
                File file2 = new File(path2);
                if (file2.exists() && file2.isDirectory()) {
                    HeavyTaskUtil.executeNewTask(() -> com.example.hikerview.utils.FileUtil.deleteDirs(path2));
                }
                String path3 = UriUtils.getRootDir(App.get().getApplicationContext()) + File.separator + "data2" + File.separator + getTitle();
                File file3 = new File(path3);
                if (file3.exists() && file3.isDirectory()) {
                    HeavyTaskUtil.executeNewTask(() -> com.example.hikerview.utils.FileUtil.deleteDirs(path3));
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    public List<ArticleListPageRule> getPageList() {
        try {
            if (this.pages != null && !this.pages.isEmpty() && this.pages.startsWith("[")) {
                return JSON.parseArray(this.pages, ArticleListPageRule.class);
            }
            return new ArrayList<>();
        } catch (Exception e) {
            Timber.e(e);
            return new ArrayList<>();
        }
    }

    @Override
    public boolean save() {
        if (this.gmtCreate <= 0) {
            this.gmtCreate = System.currentTimeMillis();
        }
        this.gmtModified = System.currentTimeMillis();
        return super.save();
    }

    public boolean saveNotUpdateTime() {
        return super.save();
    }

    @Override
    public boolean saveOrUpdate(String... conditions) {
        if (this.gmtCreate <= 0) {
            this.gmtCreate = System.currentTimeMillis();
        }
        this.gmtModified = System.currentTimeMillis();
        return super.saveOrUpdate(conditions);
    }
}
