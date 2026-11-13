package com.example.hikerview.ui.view.util;

import android.content.Intent;
import android.net.Uri;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.annimon.stream.function.Consumer;
import com.example.hikerview.ui.ActivityManager;
import com.example.hikerview.utils.StringUtil;

/**
 * 照官 8.83 本体移植（jadx 官包源）：引号高亮文本（““…”” 红、‘‘…’’ 橙）
 * 与背景高亮两口。官的链接跨度用 RichText 库的 LongClickableURLSpan，
 * 星辰未引该库，同位换框架 ClickableSpan，点击语义不变：有回调走回调，
 * 无回调官走 WebUtil.goWeb（应用内网页）——星辰此口暂以系统浏览器打开
 * 外部链接，记名待海阔网页口统一时收编。
 */
public class TextViewUtils {

    public static void setSpanText(TextView textView, String str) {
        setSpanText(textView, str, null);
    }

    public static void setSpanText(TextView textView, String str, Consumer<String> consumer) {
        if (StringUtil.isEmpty(str)) {
            textView.setText("");
            return;
        }
        try {
            String[] split = str.split("““");
            if (split.length == 1) {
                String[] split2 = str.split("‘‘");
                if (split2.length == 1) {
                    textView.setText(str);
                    return;
                }
                StringBuilder sb = new StringBuilder("<font>" + split2[0] + "</font>");
                for (int i = 1; i < split2.length; i++) {
                    String[] split3 = split2[i].split("’’");
                    sb.append("<font color=#f0983c>").append(split3[0]).append("</font>");
                    if (split3.length > 1) {
                        sb.append("<font>").append(split3[1]).append("</font>");
                    }
                }
                textView.setText(getClickableHtml(textView, Html.fromHtml(StringUtil.convertBlankToTagP(sb.toString())), consumer));
                return;
            }
            StringBuilder sb2 = new StringBuilder("<font>" + split[0] + "</font>");
            for (int i2 = 1; i2 < split.length; i2++) {
                String[] split4 = split[i2].split("””");
                sb2.append("<font color=#FF0000>").append(split4[0]).append("</font>");
                if (split4.length > 1) {
                    sb2.append("<font>").append(split4[1]).append("</font>");
                }
            }
            textView.setText(getClickableHtml(textView, Html.fromHtml(StringUtil.convertBlankToTagP(sb2.toString())), consumer));
        } catch (Exception unused) {
            textView.setText(str);
        }
    }

    private static CharSequence getClickableHtml(TextView textView, Spanned spanned, Consumer<String> consumer) {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(spanned);
        URLSpan[] uRLSpanArr = spannableStringBuilder.getSpans(0, spanned.length(), URLSpan.class);
        for (URLSpan uRLSpan : uRLSpanArr) {
            setLinkClickable(spannableStringBuilder, uRLSpan, consumer);
        }
        if (uRLSpanArr.length > 0) {
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }
        return spannableStringBuilder;
    }

    private static void setLinkClickable(SpannableStringBuilder spannableStringBuilder, URLSpan uRLSpan, final Consumer<String> consumer) {
        int spanStart = spannableStringBuilder.getSpanStart(uRLSpan);
        int spanEnd = spannableStringBuilder.getSpanEnd(uRLSpan);
        spannableStringBuilder.removeSpan(uRLSpan);
        final String url = uRLSpan.getURL();
        spannableStringBuilder.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                lambda$setLinkClickable$0(consumer, url);
            }
        }, spanStart, spanEnd, 33);
    }

    static boolean lambda$setLinkClickable$0(Consumer<String> consumer, String str) {
        if (consumer != null) {
            consumer.accept(str);
            return true;
        }
        try {
            android.app.Activity activity = ActivityManager.getInstance().getCurrentActivity();
            if (activity != null && str != null) {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(str)));
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    public static void setBackgroundSpanText(TextView textView, String str) {
        if (StringUtil.isEmpty(str)) {
            textView.setText("");
            return;
        }
        try {
            if (str.split("““").length == 1) {
                if (str.split("‘‘").length == 1) {
                    textView.setText(str);
                    return;
                }
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(str);
                int indexOf = str.indexOf("‘‘");
                while (indexOf >= 0 && indexOf <= str.lastIndexOf("‘‘")) {
                    int indexOf2 = str.indexOf("’’", indexOf);
                    if (indexOf2 > "‘‘".length() + indexOf) {
                        spannableStringBuilder.setSpan(new BackgroundColorSpan(-16711936), "‘‘".length() + indexOf, indexOf2, 17);
                    }
                    indexOf = str.indexOf("‘‘", indexOf + 1);
                }
                        textView.setText(spannableStringBuilder);
                return;
            }
            SpannableStringBuilder spannableStringBuilder2 = new SpannableStringBuilder(str);
            int indexOf3 = str.indexOf("““");
            while (indexOf3 >= 0 && indexOf3 <= str.lastIndexOf("““")) {
                int indexOf4 = str.indexOf("””", indexOf3);
                if (indexOf4 > "““".length() + indexOf3) {
                    spannableStringBuilder2.setSpan(new BackgroundColorSpan(0xFFFF0000), "““".length() + indexOf3, indexOf4, 17);
                }
                indexOf3 = str.indexOf("““", indexOf3 + 1);
            }
            textView.setText(spannableStringBuilder2);
        } catch (Exception unused) {
            textView.setText(str);
        }
    }
}
