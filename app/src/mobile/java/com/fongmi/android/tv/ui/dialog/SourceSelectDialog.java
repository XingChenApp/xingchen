package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSourceSelectBinding;

import java.util.ArrayList;
import java.util.List;

public class SourceSelectDialog extends Dialog {

    private DialogSourceSelectBinding binding;
    private List<Site> allSites;
    private List<Site> filteredSites;
    private SourceSelectAdapter adapter;
    private boolean isSingleColumn = false;
    private String currentKey;

    public SourceSelectDialog(@NonNull Context context, List<Site> sites, String currentKey) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        this.allSites = new ArrayList<>(sites);
        this.filteredSites = new ArrayList<>(sites);
        this.currentKey = currentKey;
        setCanceledOnTouchOutside(true);
        setCancelable(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DialogSourceSelectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        adapter = new SourceSelectAdapter(filteredSites, currentKey, site -> {
            dismiss();
            if (listener != null) listener.onSelect(site);
        });
        binding.recycler.setAdapter(adapter);
        updateLayoutManager();

        binding.btnToggle.setOnClickListener(v -> {
            isSingleColumn = !isSingleColumn;
            updateLayoutManager();
        });

        binding.search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void updateLayoutManager() {
        int span = isSingleColumn ? 1 : 2;
        binding.recycler.setLayoutManager(new GridLayoutManager(getContext(), span));
    }

    private void filter(String keyword) {
        filteredSites.clear();
        if (keyword == null || keyword.isEmpty()) {
            filteredSites.addAll(allSites);
        } else {
            for (Site site : allSites) {
                if (site.getName() != null && site.getName().contains(keyword)) {
                    filteredSites.add(site);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private OnSelectListener listener;
    public void setOnSelectListener(OnSelectListener listener) {
        this.listener = listener;
    }

    public interface OnSelectListener {
        void onSelect(Site site);
    }
}
