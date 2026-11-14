package com.example.hikerview.ui.view.popup;

import android.content.Context;
import android.text.Html;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.example.hikerview.R;
import com.lxj.xpopup.core.CenterPopupView;

/**
 * 官 {@code com.example.hikerview.ui.view.popup.ImportRulesPopup} 本体转写
 * （ImportRulesPopup.smali 逐法对位，布局 pop_import_rules 照官重建）。
 *
 * <p>三钮语义照官 onCreate＋lambda$onCreate$0/1/2：备份勾选框默认勾选；
 * tv_confirm（覆盖导入）→ok(backup,false)；tv_cancel 在无详情形时改文案
 * 「删除原规则」→ok(backup,true)、详情视图隐藏，有详情形时→showDetail()
 * （不关窗）；showDetailView（有详情形时文案＝下划线「删除已有规则再导入」）
 * →ok(backup,true)。ok 的两参为 (backup, delete)，消费方见
 * AutoImportHelper.importRulesWithDialog。
 */
public class ImportRulesPopup extends CenterPopupView {

    private CheckBox checkBox;
    private OkListener okListener;
    private boolean showDetail;

    public interface OkListener {
        void ok(boolean backup, boolean delete);

        void showDetail();
    }

    public ImportRulesPopup(Context context) {
        super(context);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.pop_import_rules;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        checkBox = (CheckBox) findViewById(R.id.checkbox);
        checkBox.setChecked(true);
        TextView tvCancel = (TextView) findViewById(R.id.tv_cancel);
        tvCancel.setOnClickListener(v -> lambda$onCreate$0());
        TextView tvConfirm = (TextView) findViewById(R.id.tv_confirm);
        tvConfirm.setOnClickListener(v -> lambda$onCreate$1());
        TextView showDetailView = (TextView) findViewById(R.id.showDetailView);
        if (!showDetail) {
            tvCancel.setText("删除原规则");
            showDetailView.setVisibility(View.GONE);
        } else {
            showDetailView.setText(Html.fromHtml("<u>删除已有规则再导入</u>"));
            showDetailView.setOnClickListener(v -> lambda$onCreate$2());
        }
    }

    /** 官 lambda$onCreate$0（tv_cancel）：无详情形＝删除导入；有详情形＝查看详情（不关窗）。 */
    private void lambda$onCreate$0() {
        if (!showDetail) {
            dismiss();
            okListener.ok(checkBox.isChecked(), true);
        } else {
            okListener.showDetail();
        }
    }

    /** 官 lambda$onCreate$1（tv_confirm）：覆盖导入。 */
    private void lambda$onCreate$1() {
        dismiss();
        okListener.ok(checkBox.isChecked(), false);
    }

    /** 官 lambda$onCreate$2（showDetailView）：删除已有规则再导入。 */
    private void lambda$onCreate$2() {
        dismiss();
        okListener.ok(checkBox.isChecked(), true);
    }

    public ImportRulesPopup withDetail(boolean showDetail) {
        this.showDetail = showDetail;
        return this;
    }

    public ImportRulesPopup withListener(OkListener okListener) {
        this.okListener = okListener;
        return this;
    }
}
