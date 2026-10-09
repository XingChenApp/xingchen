package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityAdblockBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AdBlockActivity extends BaseActivity {

    private static final String PREFS = "xingchen";
    private static final String KEY_ENABLED = "xingchen.adblock_enabled";
    private static final String KEY_KEYWORDS = "xingchen.adblock_keywords";

    private ActivityAdblockBinding binding;
    private KeywordAdapter adapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, AdBlockActivity.class));
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    public static Set<String> getKeywords(Context context) {
        return new HashSet<>(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_KEYWORDS, new HashSet<>()));
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityAdblockBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.swAdblock.setChecked(isEnabled(this));
        binding.swAdblock.setOnCheckedChangeListener((btn, checked) -> {
            getPrefs().edit().putBoolean(KEY_ENABLED, checked).apply();
            Notify.show(checked ? "去广告已启用" : "去广告已关闭");
        });
        adapter = new KeywordAdapter();
        binding.rvKeywords.setLayoutManager(new LinearLayoutManager(this));
        binding.rvKeywords.setAdapter(adapter);
        binding.btnAddKeyword.setOnClickListener(v -> showAddKeyword());
        refreshList();
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void refreshList() {
        List<String> keywords = new ArrayList<>(getKeywords(this));
        adapter.setData(keywords);
        boolean empty = keywords.isEmpty();
        binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvKeywords.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showAddKeyword() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("输入广告关键词");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("添加关键词")
                .setView(input)
                .setPositiveButton("添加", (d, w) -> {
                    String keyword = input.getText().toString().trim();
                    if (!keyword.isEmpty()) {
                        Set<String> keywords = getKeywords(this);
                        keywords.add(keyword);
                        getPrefs().edit().putStringSet(KEY_KEYWORDS, keywords).apply();
                        refreshList();
                        Notify.show("已添加");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private class KeywordAdapter extends RecyclerView.Adapter<KeywordAdapter.Holder> {

        private final List<String> data = new ArrayList<>();

        void setData(List<String> list) {
            data.clear();
            data.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_adblock_keyword, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            String keyword = data.get(position);
            holder.tvKeyword.setText(keyword);
            holder.tvDelete.setOnClickListener(v -> {
                Set<String> keywords = getKeywords(AdBlockActivity.this);
                keywords.remove(keyword);
                getPrefs().edit().putStringSet(KEY_KEYWORDS, keywords).apply();
                refreshList();
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            TextView tvKeyword;
            TextView tvDelete;

            Holder(View itemView) {
                super(itemView);
                tvKeyword = itemView.findViewById(R.id.tv_keyword);
                tvDelete = itemView.findViewById(R.id.tv_delete);
            }
        }
    }
}
