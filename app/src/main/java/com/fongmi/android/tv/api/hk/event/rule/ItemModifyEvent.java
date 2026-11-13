package com.fongmi.android.tv.api.hk.event.rule;

import java.util.List;

/**
 * 8.83 com.example.hikerview.event.rule.ItemModifyEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 直接搬运：字段、构造方法、getter/setter 与 8.83 原文一致；内嵌 Action 枚举（UPDATE/ADD/DELETE）。
 * articleList/list 对应 8.83 ArticleList（复杂模型，此处用 Object 简化）。
 */
public class ItemModifyEvent {

    public enum Action {
        UPDATE, ADD, DELETE
    }

    private Action action;
    private boolean after;
    private String anchorId;
    private Object articleList;
    private String cls;
    private List<Object> list;

    /**
     * 咱家内部透传（非官字段，transient 语义、不入任何序列化）：UPDATE 事件的
     * 源条目 Map，供消费方按 lambda60 的「非 null 才覆盖」逐键判定——HkItem 的
     * getter 把 null 归一成 ""，键存在性信息只能随事件原样带过。
     */
    private transient java.util.Map<String, Object> sourceMap;

    public java.util.Map<String, Object> getSourceMap() {
        return sourceMap;
    }

    public void setSourceMap(java.util.Map<String, Object> sourceMap) {
        this.sourceMap = sourceMap;
    }

    public ItemModifyEvent(Object articleList) {
        this.articleList = articleList;
        this.action = Action.UPDATE;
    }

    public ItemModifyEvent(Object articleList, Action action) {
        this.articleList = articleList;
        this.action = action;
    }

    public ItemModifyEvent(List<Object> list, Action action) {
        this.list = list;
        this.action = action;
    }

    public Action getAction() {
        return action;
    }

    public String getAnchorId() {
        return anchorId;
    }

    public Object getArticleList() {
        return articleList;
    }

    public String getCls() {
        return cls;
    }

    public List<Object> getList() {
        return list;
    }

    public boolean isAfter() {
        return after;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public void setAfter(boolean after) {
        this.after = after;
    }

    public void setAnchorId(String anchorId) {
        this.anchorId = anchorId;
    }

    public void setArticleList(Object articleList) {
        this.articleList = articleList;
    }

    public void setCls(String cls) {
        this.cls = cls;
    }

    public void setList(List<Object> list) {
        this.list = list;
    }

}
