package com.fongmi.android.tv.read;

import android.app.Activity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * 阅读器共享弹窗：目录搜索、书签列表。小说/漫画两边共用一套。
 */
public final class ReaderDialogs {

    private ReaderDialogs() {
    }

    public interface ChapterPick {
        void onPick(int index);
    }

    public interface CacheAsk {
        void onCache();
    }

    /**
     * 目录弹窗：顶部搜索框过滤章节名，点条目跳转。
     * cacheStatus 非空时显示缓存状态行；onCache 非空时露"缓存后续章节"按钮。
     */
    public static void showChapters(Activity act, String bookTitle, List<String> names,
                                   int current, String cacheStatus, CacheAsk onCache, ChapterPick pick) {
        if (act == null || names == null || names.isEmpty()) return;
        float d = act.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * d);
        root.setPadding(pad, pad, pad, pad);

        if (!TextUtils.isEmpty(cacheStatus)) {
            TextView tvCache = new TextView(act);
            tvCache.setText(cacheStatus);
            tvCache.setTextSize(13);
            tvCache.setTextColor(0xFFB8860B);
            tvCache.setPadding(0, 0, 0, (int) (6 * d));
            root.addView(tvCache);
        }
        if (onCache != null) {
            Button btnCache = new Button(act);
            btnCache.setText("缓存后续章节（离线看）");
            btnCache.setAllCaps(false);
            final AlertDialog[] holder = new AlertDialog[1];
            btnCache.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                onCache.onCache();
            });
            root.addView(btnCache);
            // 下面创建 dialog 后回填 holder
            showChaptersInner(act, bookTitle, names, current, pick, root, holder, btnCache);
            return;
        }
        showChaptersInner(act, bookTitle, names, current, pick, root, null, null);
    }

    private static void showChaptersInner(Activity act, String bookTitle, List<String> names,
                                          int current, ChapterPick pick, LinearLayout root,
                                          final AlertDialog[] holder, Button cacheBtn) {
        float d = act.getResources().getDisplayMetrics().density;
        EditText et = new EditText(act);
        et.setHint("搜索章节名");
        et.setSingleLine(true);
        root.addView(et);

        ListView lv = new ListView(act);
        List<Integer> allIdx = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) allIdx.add(i);
        List<Integer> shown = new ArrayList<>(allIdx);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(act,
                android.R.layout.simple_list_item_1, displayNames(names, shown, current));
        lv.setAdapter(adapter);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (320 * d));
        llp.topMargin = (int) (8 * d);
        root.addView(lv, llp);

        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String kw = s.toString().trim();
                shown.clear();
                for (int i = 0; i < names.size(); i++) {
                    if (kw.isEmpty() || names.get(i).contains(kw)) shown.add(i);
                }
                adapter.clear();
                adapter.addAll(displayNames(names, shown, current));
                adapter.notifyDataSetChanged();
            }
        });

        AlertDialog dialog = new ReadDialogs(act)
                .setTitle(bookTitle + "（" + names.size() + "章）")
                .setView(root)
                .setPositiveButton("关闭", (di, w) -> di.dismiss())
                .create();
        if (holder != null) holder[0] = dialog;
        lv.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            pick.onPick(shown.get(position));
        });
        dialog.show();
        // 定位到当前章
        int pos = shown.indexOf(current);
        if (pos >= 0) lv.setSelection(pos);
    }

    private static List<String> displayNames(List<String> names, List<Integer> idx, int current) {
        List<String> out = new ArrayList<>(idx.size());
        for (int i : idx) out.add((i == current ? "● " : "") + names.get(i));
        return out;
    }

    public interface BookmarkJump {
        void onJump(ReadingBookmarkStore.Mark m);
    }

    public interface BookmarkDelete {
        void onDelete(ReadingBookmarkStore.Mark m);
    }

    /** 书签列表：点条目跳转，点"删"移除。onAdd 非空时顶部露"在当前位置加书签"。 */
    public static void showBookmarks(Activity act, String bookTitle,
                                     List<ReadingBookmarkStore.Mark> marks,
                                     Runnable onAdd, BookmarkJump jump, BookmarkDelete del) {
        if (act == null) return;
        float d = act.getResources().getDisplayMetrics().density;
        int pad = (int) (12 * d);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        if (onAdd != null) {
            Button btnAdd = new Button(act);
            btnAdd.setText("＋ 在当前位置加书签");
            btnAdd.setAllCaps(false);
            final AlertDialog[] holder = new AlertDialog[1];
            btnAdd.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                onAdd.run();
            });
            root.addView(btnAdd);
            showBookmarksInner(act, bookTitle, marks, jump, del, root, holder);
            return;
        }
        showBookmarksInner(act, bookTitle, marks, jump, del, root, new AlertDialog[1]);
    }

    private static void showBookmarksInner(Activity act, String bookTitle,
                                           List<ReadingBookmarkStore.Mark> marks,
                                           BookmarkJump jump, BookmarkDelete del,
                                           LinearLayout root, final AlertDialog[] holder) {
        float d = act.getResources().getDisplayMetrics().density;
        ScrollView sv = new ScrollView(act);
        LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (320 * d));
        root.addView(sv, slp);

        if (marks.isEmpty()) {
            TextView empty = new TextView(act);
            empty.setText("还没有书签");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, (int) (32 * d), 0, (int) (32 * d));
            list.addView(empty);
        }
        for (ReadingBookmarkStore.Mark m : new ArrayList<>(marks)) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (8 * d), 0, (int) (8 * d));

            LinearLayout txt = new LinearLayout(act);
            txt.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            txt.setLayoutParams(tlp);
            TextView tvHead = new TextView(act);
            tvHead.setText(m.chapterName + ("NOVEL".equals(m.kind)
                    ? " · " + Math.round(m.position / 100f) + "%"
                    : " · 第" + (m.position + 1) + "张"));
            tvHead.setTextSize(15);
            txt.addView(tvHead);
            if (!TextUtils.isEmpty(m.excerpt)) {
                TextView tvEx = new TextView(act);
                tvEx.setText(m.excerpt);
                tvEx.setTextSize(12);
                tvEx.setTextColor(0xFF888888);
                tvEx.setMaxLines(2);
                txt.addView(tvEx);
            }
            row.addView(txt);

            TextView btnDel = new TextView(act);
            btnDel.setText("删");
            btnDel.setTextSize(14);
            btnDel.setTextColor(0xFFCC3333);
            btnDel.setPadding((int) (16 * d), (int) (8 * d), (int) (8 * d), (int) (8 * d));
            btnDel.setOnClickListener(v -> {
                del.onDelete(m);
                list.removeView(row);
                if (list.getChildCount() == 0 && holder != null && holder[0] != null) {
                    holder[0].dismiss();
                }
            });
            row.addView(btnDel);

            row.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                jump.onJump(m);
            });
            list.addView(row);
        }

        AlertDialog dialog = new ReadDialogs(act)
                .setTitle(bookTitle + " 的书签（" + marks.size() + "）")
                .setView(root)
                .setPositiveButton("关闭", (di, w) -> di.dismiss())
                .create();
        holder[0] = dialog;
        dialog.show();
    }
}
