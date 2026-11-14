package com.example.hikerview.ui.rules;

import android.content.Context;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.example.hikerview.R;
import com.example.hikerview.utils.StringUtil;
import com.lxj.xpopup.core.CenterPopupView;

/**
 * 官 {@code com.example.hikerview.ui.rules.ImportSubscribeCheckPopup} 本体转写
 * （ImportSubscribeCheckPopup.smali 逐法对位，布局 pop_import_sub_check_popup
 * 照官重建）。新建规则订阅的编辑弹窗：名称/地址两输入＋「是否立即启用」
 * 勾选（bind 的 checkedDefault 决定）＋「自动新增、更新和删除」勾选（恒默认
 * 勾选）；取消只关窗，确认关窗后回 ok(名称,地址,启用,自动更新删除)。
 * 消费方＝HomeRulesSubService.addSubWithPopup 的咱家等价支
 * （AutoImportHelper.lambda$realCheckAutoText$11 内，落 HkHomeSubService.addSubRecords）。
 */
public class ImportSubscribeCheckPopup extends CenterPopupView {

    private String title;
    private String titleHint;
    private String titleDefault;
    private String urlHint;
    private String urlDefault;
    private String checkText;
    private boolean checkedDefault;
    private OkListener okListener;
    private EditText titleEdit;
    private EditText urlEdit;
    private CheckBox checkBox;
    private CheckBox update_checkbox;

    public interface OkListener {
        void ok(String title, String url, boolean use, boolean update);
    }

    public ImportSubscribeCheckPopup(Context context) {
        super(context);
    }

    public ImportSubscribeCheckPopup bind(String title, String titleHint, String urlHint,
                                          OkListener okListener) {
        return bind(title, titleHint, null, urlHint, null, null, true, okListener);
    }

    public ImportSubscribeCheckPopup bind(String title, String titleHint, String titleDefault,
                                          String urlHint, String urlDefault, String checkText,
                                          boolean checkedDefault, OkListener okListener) {
        this.title = title;
        this.titleHint = titleHint;
        this.titleDefault = titleDefault;
        this.urlHint = urlHint;
        this.urlDefault = urlDefault;
        this.okListener = okListener;
        this.checkText = checkText;
        this.checkedDefault = checkedDefault;
        return this;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.pop_import_sub_check_popup;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        TextView titleView = (TextView) findViewById(R.id.title);
        titleEdit = (EditText) findViewById(R.id.edit_title);
        urlEdit = (EditText) findViewById(R.id.edit_url);
        titleView.setText(title);
        titleEdit.setHint(titleHint);
        urlEdit.setHint(urlHint);
        if (StringUtil.isNotEmpty(titleDefault)) {
            titleEdit.setText(titleDefault);
        }
        if (StringUtil.isNotEmpty(urlDefault)) {
            urlEdit.setText(urlDefault);
        }
        checkBox = (CheckBox) findViewById(R.id.checkbox);
        if (StringUtil.isNotEmpty(checkText)) {
            checkBox.setText(checkText);
        }
        checkBox.setChecked(checkedDefault);
        update_checkbox = (CheckBox) findViewById(R.id.update_checkbox);
        update_checkbox.setChecked(true);
        ((TextView) findViewById(R.id.tv_cancel)).setOnClickListener(v -> dismiss());
        ((TextView) findViewById(R.id.tv_confirm)).setOnClickListener(v -> {
            dismiss();
            okListener.ok(titleEdit.getText().toString(), urlEdit.getText().toString(),
                    checkBox.isChecked(), update_checkbox.isChecked());
        });
    }
}
