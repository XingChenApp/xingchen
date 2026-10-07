package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import static androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.FragmentSearchBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.adapter.RecordAdapter;
import com.fongmi.android.tv.ui.adapter.WordAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.dialog.SearchSourceDialog;
import com.fongmi.android.tv.utils.SearchSuggest;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import okhttp3.Call;
import okhttp3.Response;

public class SearchFragment extends BaseFragment implements WordAdapter.OnClickListener, RecordAdapter.OnClickListener {

    private FragmentSearchBinding mBinding;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;
    private List<Word.Data> mIqiyiWords = new ArrayList<>();
    private List<Word.Data> mTencentWords = new ArrayList<>();
    private int mSuggestSeq;

    public static SearchFragment newInstance(String keyword) {
        return newInstance(keyword, null);
    }

    public static SearchFragment newInstance(String keyword, String siteKey) {
        return newInstance(keyword, siteKey, null, null);
    }

    public static SearchFragment newInstance(String keyword, String siteKey, String pic, String wallPic) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        args.putString("siteKey", siteKey);
        args.putString("pic", pic);
        args.putString("wallPic", wallPic);
        SearchFragment fragment = new SearchFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return getArguments().getString("keyword");
    }

    private String getSiteKey() {
        return getArguments().getString("siteKey");
    }

    private String getPic() {
        return getArguments().getString("pic");
    }

    private String getWallPic() {
        return getArguments().getString("wallPic");
    }

    private boolean empty() {
        return mBinding.keyword.getText().toString().trim().isEmpty();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        setRecyclerView();
        checkKeyword();
    }

    private void setRecyclerView() {
        mBinding.wordRecycler.setHasFixedSize(false);
        mBinding.wordRecycler.setAdapter(mWordAdapter = new WordAdapter(this));
        mBinding.wordRecycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
        mBinding.recordRecycler.setHasFixedSize(false);
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
        mBinding.recordRecycler.setLayoutManager(new com.google.android.flexbox.FlexboxLayoutManager(getContext(), com.google.android.flexbox.FlexDirection.ROW));
    }

    @Override
    protected void initEvent() {
        mBinding.back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        mBinding.sourceSettings.setOnClickListener(v -> onSourceSettings());
        mBinding.clearHistory.setOnClickListener(v -> onClearHistory());
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) search();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                getWord(s.toString());
            }
        });
    }

    private void onSourceSettings() {
        Util.hideKeyboard(mBinding.keyword);
        SearchSourceDialog.show(this);
    }

    private void onClearHistory() {
        mRecordAdapter.clear();
    }

    private void checkKeyword() {
        boolean visible = requireActivity().getSupportFragmentManager().findFragmentByTag(CollectFragment.class.getSimpleName()) != null;
        if (TextUtils.isEmpty(getKeyword()) && !visible) Util.showKeyboard(mBinding.keyword);
        setKeyword(getKeyword());
        getWord(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        if (text != null) mBinding.keyword.setSelection(text.length());
    }

    private void search() {
        if (empty()) return;
        String keyword = mBinding.keyword.getText().toString().trim();
        App.post(() -> mRecordAdapter.add(keyword), 250);
        Util.hideKeyboard(mBinding.keyword);
        collect(keyword);
    }

    private void collect(String keyword) {
        FragmentManager fm = requireActivity().getSupportFragmentManager();
        String collectTag = CollectFragment.class.getSimpleName();
        if (fm.findFragmentByTag(collectTag) != null) return;
        String searchTag = SearchFragment.class.getSimpleName();
        FragmentTransaction ft = fm.beginTransaction().setTransition(TRANSIT_FRAGMENT_OPEN);
        ft.add(R.id.container, CollectFragment.newInstance(keyword, getSiteKey(), getPic(), getWallPic()), collectTag);
        Optional.ofNullable(fm.findFragmentByTag(searchTag)).ifPresent(ft::hide);
        ft.setReorderingAllowed(true).addToBackStack(null).commit();
    }

    private void getWord(String text) {
        if (text == null) text = "";
        if (text.isEmpty()) {
            showSuggest(false);
        } else {
            getSuggest(text);
        }
    }

    private void getSuggest(String text) {
        showSuggest(true);
        int seq = ++mSuggestSeq;
        mIqiyiWords = new ArrayList<>();
        mTencentWords = new ArrayList<>();
        OkHttp.newCall(SearchSuggest.iqiyiUrl(text)).enqueue(getSuggestCallback(seq, false));
        OkHttp.newCall(SearchSuggest.tencentUrl(text)).enqueue(getSuggestCallback(seq, true));
    }

    private void showSuggest(boolean show) {
        mBinding.word.setVisibility(show ? View.VISIBLE : View.GONE);
        mBinding.wordRecycler.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private Callback getSuggestCallback(int seq, boolean tencent) {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String result = response.body().string();
                if (TextUtils.isEmpty(result)) return;
                App.post(() -> setSuggestAdapter(seq, result, tencent));
            }
        };
    }

    private void setSuggestAdapter(int seq, String result, boolean tencent) {
        if (seq != mSuggestSeq || mBinding.keyword.getText().toString().trim().isEmpty()) return;
        if (tencent) mTencentWords = SearchSuggest.parseTencent(result);
        else mIqiyiWords = SearchSuggest.parseIqiyi(result);
        mWordAdapter.setItems(SearchSuggest.merge(mIqiyiWords, mTencentWords));
    }

    @Override
    public void onItemClick(String text) {
        setKeyword(text);
        search();
    }

    @Override
    public void onDataChanged(int size) {
        boolean has = size > 0;
        mBinding.record.setVisibility(has ? View.VISIBLE : View.GONE);
        mBinding.clearHistory.setVisibility(has ? View.VISIBLE : View.GONE);
        mBinding.recordRecycler.setVisibility(has ? View.VISIBLE : View.GONE);
    }
}
