package com.fongmi.android.tv.ui.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.databinding.AdapterSearchRecordBinding;
import com.fongmi.android.tv.setting.Setting;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.ViewHolder> {

    private static final String TAG = "RecordAdapter";
    private final OnClickListener listener;
    private final List<String> mItems;

    public RecordAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = getItems();
        this.listener.onDataChanged(mItems.size());
    }

    public interface OnClickListener {

        void onItemClick(String text);

        void onDataChanged(int size);
    }

    private List<String> getItems() {
        if (Setting.getKeyword().isEmpty()) return new ArrayList<>();
        try {
            List<String> items = App.gson().fromJson(Setting.getKeyword(), TypeToken.getParameterized(List.class, String.class).getType());
            return items == null ? new ArrayList<>() : items;
        } catch (Exception e) {
            // 存量关键词整体解析失败：先逐元素抢救还能认的字符串词，救不回的
            // 把原始串备份到 keyword_corrupt_backup 再回空，不许历史词无声全没、
            // 之后写回时把原始数据一并冲掉。
            Log.w(TAG, "parse keyword history failed", e);
            List<String> salvaged = salvageItems(Setting.getKeyword());
            if (!salvaged.isEmpty()) return salvaged;
            try {
                com.github.catvod.utils.Prefers.put("keyword_corrupt_backup", Setting.getKeyword());
            } catch (Exception backupError) {
                Log.w(TAG, "backup corrupt keyword failed", backupError);
            }
            return new ArrayList<>();
        }
    }

    private List<String> salvageItems(String raw) {
        List<String> out = new ArrayList<>();
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(raw);
            if (el != null && el.isJsonArray()) {
                for (com.google.gson.JsonElement item : el.getAsJsonArray()) {
                    if (item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) out.add(item.getAsString());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "salvage keyword history failed", e);
        }
        return out;
    }

    private void checkToAdd(String item) {
        mItems.remove(item);
        mItems.add(0, item);
        if (mItems.size() > 9) mItems.remove(9);
    }

    public void add(String item) {
        checkToAdd(item);
        notifyDataSetChanged();
        listener.onDataChanged(getItemCount());
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSearchRecordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String text = mItems.get(position);
        holder.binding.text.setText(text);
        holder.binding.text.setOnClickListener(v -> listener.onItemClick(text));
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener {

        private final AdapterSearchRecordBinding binding;

        public ViewHolder(@NonNull AdapterSearchRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnLongClickListener(this);
        }

        @Override
        public boolean onLongClick(View v) {
            int position = getLayoutPosition();
            if (position == RecyclerView.NO_POSITION || position >= mItems.size()) return false;
            mItems.remove(position);
            notifyItemRemoved(position);
            listener.onDataChanged(getItemCount());
            Setting.putKeyword(App.gson().toJson(mItems));
            return true;
        }
    }
}
