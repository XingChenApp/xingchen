package me.codeboy.android.aligntextview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.widget.TextView;

import com.fongmi.android.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 照抄海阔 8.83 内置的 me.codeboy.android.aligntextview.AlignTextView（smali 逐方法转写，
 * 含 calc 分行算法与 onDraw 逐字绘制的原始实现）。long_text 条目用它做两端对齐文本。
 */
public class AlignTextView extends TextView {

    public enum Align {
        ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT
    }

    private Align align;
    private boolean firstCalc;
    private float lineSpacingAdd;
    private float lineSpacingMultiplier;
    private List<String> lines;
    private int originalHeight;
    private int originalLineCount;
    private int originalPaddingBottom;
    private boolean setPaddingFromMe;
    private List<Integer> tailLines;
    private float textHeight;
    private float textLineSpaceExtra;
    private int width;

    public AlignTextView(Context context) {
        super(context);
        textLineSpaceExtra = 0;
        lines = new ArrayList<>();
        tailLines = new ArrayList<>();
        align = Align.ALIGN_LEFT;
        firstCalc = true;
        lineSpacingMultiplier = 1.0f;
        lineSpacingAdd = 0;
        originalHeight = 0;
        originalLineCount = 0;
        originalPaddingBottom = 0;
        setPaddingFromMe = false;
        setTextIsSelectable(false);
    }

    public AlignTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        textLineSpaceExtra = 0;
        lines = new ArrayList<>();
        tailLines = new ArrayList<>();
        align = Align.ALIGN_LEFT;
        firstCalc = true;
        lineSpacingMultiplier = 1.0f;
        lineSpacingAdd = 0;
        originalHeight = 0;
        originalLineCount = 0;
        originalPaddingBottom = 0;
        setPaddingFromMe = false;
        setTextIsSelectable(false);

        TypedArray a = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.lineSpacingExtra, android.R.attr.lineSpacingMultiplier});
        lineSpacingAdd = a.getDimensionPixelSize(0, 0);
        lineSpacingMultiplier = a.getFloat(1, 1.0f);
        originalPaddingBottom = getPaddingBottom();
        a.recycle();

        TypedArray b = context.obtainStyledAttributes(attrs, R.styleable.AlignTextView);
        int al = b.getInt(R.styleable.AlignTextView_align, 0);
        if (al == 1) {
            align = Align.ALIGN_CENTER;
        } else if (al == 2) {
            align = Align.ALIGN_RIGHT;
        } else {
            align = Align.ALIGN_LEFT;
        }
        b.recycle();
    }

    private void calc(Paint paint, String str) {
        if (str.length() == 0) {
            lines.add("\n");
            return;
        }
        float oneWidth = paint.measureText("中");
        int charCount = (int) (width / oneWidth);
        StringBuilder sb = new StringBuilder(str.substring(0, Math.min(charCount + 1, str.length())));
        int i = charCount + 1;
        boolean done = false;
        while (i < str.length() && !done) {
            if (paint.measureText(str.substring(0, i + 1)) > width) {
                lines.add(sb.toString());
                sb = new StringBuilder();
                if (str.length() - i > charCount) {
                    sb.append(str.substring(i, i + charCount));
                    i = i + charCount - 1;
                } else {
                    lines.add(str.substring(i));
                    done = true;
                }
            } else {
                sb.append(str.charAt(i));
            }
            if (!done) i++;
        }
        if (sb.length() > 0) {
            lines.add(sb.toString());
        }
        tailLines.add(lines.size() - 1);
    }

    private void measureTextViewHeight(String text, float textSize, int width) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(0, textSize);
        tv.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, 0));
        originalLineCount = tv.getLineCount();
        originalHeight = tv.getMeasuredHeight();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        TextPaint paint = getPaint();
        paint.setColor(getCurrentTextColor());
        paint.drawableState = getDrawableState();
        width = getMeasuredWidth();
        Paint.FontMetrics fm = paint.getFontMetrics();
        float firstHeight = getTextSize() - (fm.bottom - fm.descent + fm.ascent - fm.top);
        if ((getGravity() & 0x1000) == 0) {
            firstHeight = firstHeight + (textHeight - firstHeight) / 2.0f;
        }
        int paddingTop = getPaddingTop();
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        width = width - paddingLeft - paddingRight;
        for (int i = 0; i < lines.size(); i++) {
            float y = textHeight * (float) i + firstHeight;
            String line = lines.get(i);
            float lineWidth = width - paint.measureText(line);
            float space = lineWidth / (float) (line.length() - 1);
            float x = paddingLeft;
            if (tailLines.contains(i)) {
                if (align == Align.ALIGN_CENTER) {
                    x += lineWidth / 2.0f;
                } else if (align == Align.ALIGN_RIGHT) {
                    x += lineWidth;
                }
                space = 0;
            }
            for (int j = 0; j < line.length(); j++) {
                float charX = paint.measureText(line.substring(0, j)) + j * space + x;
                float charY = paddingTop + y + textLineSpaceExtra * (float) i;
                canvas.drawText(line.substring(j, j + 1), charX, charY, paint);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (firstCalc) {
            width = getMeasuredWidth();
            String text = getText().toString();
            TextPaint paint = getPaint();
            lines.clear();
            tailLines.clear();
            for (String part : text.split("\\n")) {
                calc(paint, part);
            }
            measureTextViewHeight(text, paint.getTextSize(), getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
            textHeight = originalHeight * 1.0f / originalLineCount;
            textLineSpaceExtra = (lineSpacingMultiplier - 1.0f) * textHeight + lineSpacingAdd;
            float extra = (textLineSpaceExtra + textHeight) * (float) (lines.size() - originalLineCount);
            setPaddingFromMe = true;
            setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), originalPaddingBottom + (int) extra);
            firstCalc = false;
        }
    }

    public void setAlign(Align align) {
        this.align = align;
        invalidate();
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        if (!setPaddingFromMe) {
            originalPaddingBottom = bottom;
        }
        setPaddingFromMe = false;
        super.setPadding(left, top, right, bottom);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        firstCalc = true;
        super.setText(text, type);
    }
}
