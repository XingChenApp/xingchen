package com.example.hikerview.ui.browser.view;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 官宿主面按需声明（官源 smali_classes2/com/example/hikerview/ui/browser/view/BaseWebViewActivity.smali：
 * 官为 abstract、继承官 ui.base.BaseActivity）。①团只落类型声明一层：MultiWindowManager.instance(Activity)
 * 的 instanceof 判定与 activity 字段类型需要它在位；官 Activity 本体（页面行为）属宿主面批次、未落，记名在案。
 * 本树无官 BaseActivity，基类接 AppCompatActivity 记名。
 */
public abstract class BaseWebViewActivity extends AppCompatActivity {
}
