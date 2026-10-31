package com.fongmi.android.tv.hongguo.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.function.IntUnaryOperator;

/**
 * 评论输入行表情面板（T-06，他亲裁「加」）：真功能件——点格把
 * 表情插入绑定 EditText 的当前光标处（有选区则替换选区），插入
 * 后光标落到表情之后、面板不自动收（可连插）。竖屏评论单与横
 * 屏评论面板共用本件，各自只管显隐与底部让位口径。官方底行
 * （截图 113856）有表情钮，面板内部形态官方未抠到：本件按
 * App 既有输入面口径自洽成形（8 列网格、系统 emoji 字形直显，
 * 不引外部资源），不冒充官方原样。
 * 表情用 Unicode 字形而非平台方括号码（如 [偷笑]）：方括号码
 * 是官方客户端私有贴纸映射，咱家评论行不渲染该映射、发出去
 * 在自家列表里只剩原文；Unicode 表情各端直显无此问题。
 */
public final class HgEmojiPanel {

    private HgEmojiPanel() {
    }

    private static final String[] EMOJIS = {
            "😀", "😄", "😁", "🤣", "😂", "😅", "😊", "😍",
            "😘", "😎", "🥰", "😋", "😜", "🤪", "😏", "😌",
            "😴", "🤔", "😐", "🙄", "😳", "🥺", "😭", "😱",
            "😤", "😡", "😖", "😞", "😓", "😩", "😫", "😷",
            "👍", "👎", "👏", "🙏", "💪", "👀", "🔥", "💯",
            "❤️", "💔", "🎉", "✨", "🌹", "🍉", "🐱", "🐶"};

    /** 表情钮面字（两面输入行共用同字同尺寸口径）。 */
    public static final String BUTTON_FACE = "😊";

    /**
     * 建面板根视图（初始 GONE）。dark＝横屏深形态；浅形态（竖屏
     * 评论单）白底。bottomPadPx 由调用方按系统手势区让位喂入
     * （面板在最底部时它就是让位承担者）。
     */
    public static View create(Context ctx, IntUnaryOperator dp, boolean dark,
                              EditText input, int bottomPadPx) {
        android.widget.LinearLayout root = new android.widget.LinearLayout(ctx);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF1D1D20 : Color.WHITE);
        root.setVisibility(View.GONE);
        View line = new View(ctx);
        line.setBackgroundColor(dark ? 0x14FFFFFF : 0x121B1B1F);
        root.addView(line, new android.widget.LinearLayout.LayoutParams(-1, 1));
        RecyclerView grid = new RecyclerView(ctx);
        grid.setLayoutManager(new GridLayoutManager(ctx, 8));
        grid.setPadding(dp.applyAsInt(10), dp.applyAsInt(8),
                dp.applyAsInt(10), dp.applyAsInt(10) + bottomPadPx);
        grid.setAdapter(new RecyclerView.Adapter<Cell>() {
            @NonNull
            @Override
            public Cell onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextView t = new TextView(ctx);
                t.setGravity(Gravity.CENTER);
                t.setTextSize(22);
                t.setLayoutParams(new RecyclerView.LayoutParams(-1, dp.applyAsInt(42)));
                return new Cell(t);
            }

            @Override
            public void onBindViewHolder(@NonNull Cell h, int position) {
                String e = EMOJIS[position];
                h.text.setText(e);
                h.text.setOnClickListener(v -> insert(input, e));
            }

            @Override
            public int getItemCount() {
                return EMOJIS.length;
            }
        });
        root.addView(grid, new android.widget.LinearLayout.LayoutParams(
                -1, dp.applyAsInt(42 * 4 + 18)));
        return root;
    }

    /** 面板已建后改底部让位（横屏宿主 insets 迟到时跟随刷新）。 */
    public static void setBottomPad(View panel, IntUnaryOperator dp, int bottomPadPx) {
        if (panel == null) return;
        View grid = ((android.widget.LinearLayout) panel).getChildAt(1);
        if (grid != null) {
            grid.setPadding(dp.applyAsInt(10), dp.applyAsInt(8),
                    dp.applyAsInt(10), dp.applyAsInt(10) + bottomPadPx);
        }
    }

    /** 插入到光标处：有选区替换选区；无焦点/无选区时追加到文末。 */
    static void insert(EditText input, String emoji) {
        if (input == null || emoji == null || emoji.isEmpty()) return;
        android.text.Editable text = input.getText();
        if (text == null) {
            input.setText(emoji);
            input.setSelection(input.getText().length());
            return;
        }
        int start = input.getSelectionStart();
        int end = input.getSelectionEnd();
        if (start < 0 || end < 0) {
            start = end = text.length();
        }
        int lo = Math.min(start, end);
        int hi = Math.max(start, end);
        text.replace(lo, hi, emoji);
        input.setSelection(lo + emoji.length());
    }

    private static final class Cell extends RecyclerView.ViewHolder {
        final TextView text;

        Cell(TextView t) {
            super(t);
            text = t;
        }
    }
}
