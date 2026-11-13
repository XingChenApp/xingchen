package com.example.hikerview.ui.view.toast;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.hikerview.R;
import com.example.hikerview.utils.DisplayUtil;
import com.google.android.material.snackbar.BaseTransientBottomBar;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * 照官 8.83 本体移植（jadx 官包源，Kotlin 类落 Java 同签名）：
 * 带确认/取消钮的底部 Snackbar。JS 面与官一致——Companion.make
 * 起手，setText/setAction/setCancelButton 链式，按钮点击先关再回调。
 * 构造：底透明、四边 10dp 内距，内容底为 dark_edit 色 6dp 圆角矩形。
 */
public class ChefSnackbar extends BaseTransientBottomBar<ChefSnackbar> {

    public static final Companion Companion = new Companion();

    private final ChefSnackbarView content;

    public ChefSnackbarView getContent() {
        return this.content;
    }

    public ChefSnackbar(ViewGroup parent, ChefSnackbarView content) {
        super(parent, content, content);
        this.content = content;
        getView().setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
        int dpToPx = DisplayUtil.dpToPx(getContext(), 10);
        getView().setPadding(dpToPx, dpToPx, dpToPx, dpToPx);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(GradientDrawable.RECTANGLE);
        gradientDrawable.setColor(ContextCompat.getColor(getContext(), R.color.dark_edit));
        gradientDrawable.setCornerRadius(DisplayUtil.dpToPx(getContext(), 6));
        content.setBackground(gradientDrawable);
    }

    public ChefSnackbar setConfirmButton(String text, final Function1<? super View, Unit> action) {
        TextView textView = (TextView) this.content.findViewById(R.id.btn_ok);
        textView.setText(text);
        textView.setOnClickListener(view -> {
            dismiss();
            action.invoke(view);
        });
        return this;
    }

    public ChefSnackbar setCancelButton(String text, final Function1<? super View, Unit> action) {
        TextView textView = (TextView) this.content.findViewById(R.id.btn_cancel);
        textView.setText(text);
        textView.setOnClickListener(view -> {
            dismiss();
            action.invoke(view);
        });
        return this;
    }

    public ChefSnackbar setText(String text) {
        ((TextView) this.content.findViewById(R.id.title)).setText(text);
        return this;
    }

    public ChefSnackbar setAction(String t, final View.OnClickListener listener) {
        setConfirmButton(t, (Function1<View, Unit>) view -> {
            listener.onClick(view);
            return Unit.INSTANCE;
        });
        return this;
    }

    public static final class Companion {
        private Companion() {
        }

        public ChefSnackbar make(View view) {
            ViewGroup findSuitableParent = ViewExtensionsKt.findSuitableParent(view);
            if (findSuitableParent == null) {
                throw new IllegalArgumentException("No suitable parent found from the given view. Please provide a valid view.");
            }
            View inflate = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_snackbar_chef, findSuitableParent, false);
            return new ChefSnackbar(findSuitableParent, (ChefSnackbarView) inflate);
        }
    }
}
