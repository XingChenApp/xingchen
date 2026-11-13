package com.fongmi.android.tv.ui.hk;

import android.content.Intent;
import android.net.Uri;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;

import java.util.function.Consumer;

/**
 * 照抄 8.83 com.example.hikerview.ui.view.util.TextViewUtils（smali 逐方法转写）：
 * setSpanText 的 ““/‘‘ 标记分段着色（#FF0000 / #f0983c）、convertBlankToTagP、
 * getClickableHtml 把 URLSpan 换成可回调的 span（原版用 zzhoujay RichText 的
 * LongClickableURLSpan：颜色 #4078C0、带下划线，点击走 OnUrlClickListener）。
 */
public final class HkTextViewUtils {

    private HkTextViewUtils() {
    }

    /** 原版 LongClickableURLSpan 的等价实现（颜色/下划线/点击语义逐项对齐）。 */
    static class HkUrlSpan extends URLSpan {
        private final Consumer<String> urlClick;

        HkUrlSpan(String url, Consumer<String> urlClick) {
            super(url);
            this.urlClick = urlClick;
        }

        @Override
        public void onClick(View widget) {
            if (urlClick != null) urlClick.accept(getURL());
            else super.onClick(widget);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setColor(0xFF4078C0);
            ds.setUnderlineText(true);
        }
    }

    private static CharSequence getClickableHtml(TextView textView, Spanned spanned, Consumer<String> consumer) {
        SpannableStringBuilder builder = new SpannableStringBuilder(spanned);
        URLSpan[] spans = builder.getSpans(0, spanned.length(), URLSpan.class);
        for (URLSpan span : spans) {
            setLinkClickable(builder, span, consumer, textView);
        }
        if (spans.length > 0) {
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }
        return builder;
    }

    private static void setLinkClickable(SpannableStringBuilder builder, URLSpan span, Consumer<String> consumer, TextView textView) {
        int start = builder.getSpanStart(span);
        int end = builder.getSpanEnd(span);
        builder.removeSpan(span);
        Consumer<String> handler = url -> {
            // 照抄原版 lambda$setLinkClickable$0：有 consumer 走 consumer，否则 goWeb 打开网页
            if (consumer != null) {
                consumer.accept(url);
            } else {
                try {
                    textView.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                }
            }
        };
        builder.setSpan(new HkUrlSpan(span.getURL(), handler), start, end, 0x21);
    }

    public static void setSpanText(TextView textView, String text) {
        setSpanText(textView, text, null);
    }

    public static void setSpanText(TextView textView, String text, Consumer<String> consumer) {
        if (text == null || text.isEmpty()) {
            textView.setText("");
            return;
        }
        text = forceEmojiPresentation(text);
        try {
            String[] dq = text.split("““");
            if (dq.length == 1) {
                String[] sq = text.split("‘‘");
                if (sq.length == 1) {
                    textView.setText(text);
                    return;
                }
                StringBuilder sb = new StringBuilder("<font>" + sq[0] + "</font>");
                for (int i = 1; i < sq.length; i++) {
                    String[] parts = sq[i].split("’’");
                    sb.append("<font color=#f0983c>").append(parts[0]).append("</font>");
                    if (parts.length > 1) {
                        sb.append("<font>").append(parts[1]).append("</font>");
                    }
                }
                Spanned spanned = Html.fromHtml(convertBlankToTagP(sb.toString()));
                textView.setText(getClickableHtml(textView, spanned, consumer));
                return;
            }
            StringBuilder sb = new StringBuilder("<font>" + dq[0] + "</font>");
            for (int i = 1; i < dq.length; i++) {
                String[] parts = dq[i].split("””");
                sb.append("<font color=#FF0000>").append(parts[0]).append("</font>");
                if (parts.length > 1) {
                    sb.append("<font>").append(parts[1]).append("</font>");
                }
            }
            Spanned spanned = Html.fromHtml(convertBlankToTagP(sb.toString()));
            textView.setText(getClickableHtml(textView, spanned, consumer));
        } catch (Exception e) {
            textView.setText(text);
        }
    }

    /**
     * 文本形态默认的 emoji 码点（Unicode emoji-data 中 Emoji_Presentation=No）：
     * 规则标题里的 ⬅ 等符号，无 U+FE0F 时排版优先用正文字体的单色字形（咱家
     * 设备默认无衬线含 ⬅ 单色形 → 细黑箭头）；原版实拍为彩色形（其正文书法
     * 字体无此字形、回退系统彩色 emoji 字体）。此处在标题漏斗入口给这类码点
     * 补 U+FE0F 强制 emoji 形态，字形仍由系统彩色 emoji 字体出，与原版同源；
     * 已带 FE0F 的不重复补，非目标字符与 ““/‘‘ 分段标记一律不动。
     */
    private static boolean isTextPresentationEmoji(char c) {
        switch (c) {
            case '\u2194': // ↔
            case '\u2195': // ↕
            case '\u2196': // ↖
            case '\u2197': // ↗
            case '\u2198': // ↘
            case '\u2199': // ↙
            case '\u27A1': // ➡
            case '\u2B05': // ⬅
            case '\u2B06': // ⬆
            case '\u2B07': // ⬇
            case '\u2B50': // ⭐
            case '\u2B55': // ⭕
            case '\u26D4': // ⛔
            case '\u2764': // ❤
            case '\u203C': // ‼
            case '\u2049': // ⁉
            case '\u2753': // ❓
            case '\u2754': // ❔
            case '\u2755': // ❕
            case '\u2757': // ❗
                return true;
            default:
                return false;
        }
    }

    static String forceEmojiPresentation(String text) {
        int n = text.length();
        StringBuilder sb = null;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (sb != null) sb.append(c);
            if (isTextPresentationEmoji(c) && (i + 1 >= n || text.charAt(i + 1) != '\uFE0F')) {
                if (sb == null) {
                    sb = new StringBuilder(n + 8);
                    sb.append(text, 0, i + 1);
                }
                sb.append('\uFE0F');
            }
        }
        return sb == null ? text : sb.toString();
    }

    /** 照抄 StringUtil.convertBlankToTagP：含换行时把 \n 换成 <br>。 */
    public static String convertBlankToTagP(String text) {
        try {
            if (text == null || text.isEmpty()) return text;
            if (!text.contains("\n")) return text;
            return text.replace("\n", "<br>");
        } catch (Exception e) {
            return text;
        }
    }
}
