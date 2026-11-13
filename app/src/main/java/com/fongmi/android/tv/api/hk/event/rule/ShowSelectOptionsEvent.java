package com.fongmi.android.tv.api.hk.event.rule;

import java.util.List;

/**
 * showSelectOptions 的事件面（8.83 JSEngine.showSelectOptions 把 SelectExtra
 * 本体 post 到 EventBus、详情页订阅弹选择框的等价形，G42）：运行时解析
 * SelectExtra 后发本事件，列表页（HkArticleFragment）消费弹框，选中后在
 * 页面运行时里以 input＝选中标题求值 js（MY_INDEX 注入下标）。
 * options2 为二段选项（官 SelectOption{title, img, url}），此处只承标题集
 * 与下标语义（见第九阶段回执记名）。
 */
public class ShowSelectOptionsEvent {

    private String title;
    private List<String> options;
    private List<String> options2Titles;
    private List<String> options2Icons;
    private String js;
    private int col;
    private int selectedIndex;
    private boolean bottom;
    private boolean attachView;
    private String ruleTitle;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public List<String> getOptions2Titles() {
        return options2Titles;
    }

    public void setOptions2Titles(List<String> options2Titles) {
        this.options2Titles = options2Titles;
    }

    public List<String> getOptions2Icons() {
        return options2Icons;
    }

    public void setOptions2Icons(List<String> options2Icons) {
        this.options2Icons = options2Icons;
    }

    public String getJs() {
        return js;
    }

    public void setJs(String js) {
        this.js = js;
    }

    public int getCol() {
        return col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex = selectedIndex;
    }

    public boolean isBottom() {
        return bottom;
    }

    public void setBottom(boolean bottom) {
        this.bottom = bottom;
    }

    public boolean isAttachView() {
        return attachView;
    }

    public void setAttachView(boolean attachView) {
        this.attachView = attachView;
    }

    public String getRuleTitle() {
        return ruleTitle;
    }

    public void setRuleTitle(String ruleTitle) {
        this.ruleTitle = ruleTitle;
    }
}
