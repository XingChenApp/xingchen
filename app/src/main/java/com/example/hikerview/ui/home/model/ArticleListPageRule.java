package com.example.hikerview.ui.home.model;

import com.alibaba.fastjson.annotation.JSONField;

import java.util.Map;

/**
 * 照官 8.83 本体逐字转写（官源 smali_classes2/com/example/hikerview/ui/home/model/ArticleListPageRule.smali，
 * 全类照落）。字段注解照官：params 带 fastjson serialize/deserialize=false 且泛型
 * Map&lt;String,String&gt;；parentRule 只带 serialize/deserialize=false（官 parent 字段无注解）。
 */
public class ArticleListPageRule implements Cloneable {

    private String col_type;
    private String name;

    @JSONField(deserialize = false, serialize = false)
    private Map<String, String> params;
    private String parent;

    @JSONField(deserialize = false, serialize = false)
    private ArticleListRule parentRule;
    private String path;
    private String rule;

    @Override
    public ArticleListPageRule clone() throws CloneNotSupportedException {
        return (ArticleListPageRule) super.clone();
    }

    public String getCol_type() {
        return this.col_type;
    }

    public void setCol_type(String str) {
        this.col_type = str;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String str) {
        this.name = str;
    }

    public Map<String, String> getParams() {
        return this.params;
    }

    public void setParams(Map<String, String> map) {
        this.params = map;
    }

    public String getParent() {
        return this.parent;
    }

    public void setParent(String str) {
        this.parent = str;
    }

    public ArticleListRule getParentRule() {
        return this.parentRule;
    }

    public void setParentRule(ArticleListRule articleListRule) {
        this.parentRule = articleListRule;
    }

    public String getPath() {
        return this.path;
    }

    public void setPath(String str) {
        this.path = str;
    }

    public String getRule() {
        return this.rule;
    }

    public void setRule(String str) {
        this.rule = str;
    }
}
