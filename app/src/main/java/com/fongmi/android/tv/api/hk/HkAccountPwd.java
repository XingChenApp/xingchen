package com.fongmi.android.tv.api.hk;

/**
 * 官 {@code com.example.hikerview.ui.rules.model.AccountPwd} 的等价模型
 * （AccountPwd.smali 字段表逐字：account/password 两 String 字段、无参＋
 * 两参构造、getter/setter 全套）。云仓库（publish）账号的载体，存取走
 * {@link HkPublishHelper}（官 PublishHelper 的等价面）。
 */
public class HkAccountPwd {

    private String account;
    private String password;

    public HkAccountPwd() {
    }

    public HkAccountPwd(String account, String password) {
        this.account = account;
        this.password = password;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
