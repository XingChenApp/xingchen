package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.DialogReceiveBinding;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;

public class ReceiveDialog extends BaseBottomSheetDialog {

    private DialogReceiveBinding binding;
    private CastEvent event;

    public static ReceiveDialog create() {
        return new ReceiveDialog();
    }

    public ReceiveDialog event(CastEvent event) {
        this.event = event;
        // 事件走 arguments 存一份（三个 bean 都能 Gson 往返）：系统重建弹窗时实例字段会丢，
        // arguments 能原样恢复，否则 initView 对 null 的 event 解引用必崩。
        android.os.Bundle args = getArguments();
        if (args == null) {
            args = new android.os.Bundle();
            setArguments(args);
        }
        if (event != null) {
            args.putString("config", com.fongmi.android.tv.App.gson().toJson(event.config()));
            args.putString("device", com.fongmi.android.tv.App.gson().toJson(event.device()));
            args.putString("history", com.fongmi.android.tv.App.gson().toJson(event.history()));
        }
        return this;
    }

    private CastEvent event() {
        if (event == null && getArguments() != null) {
            android.os.Bundle args = getArguments();
            try {
                com.fongmi.android.tv.bean.Config config = com.fongmi.android.tv.bean.Config.objectFrom(args.getString("config"));
                com.fongmi.android.tv.bean.Device device = com.fongmi.android.tv.bean.Device.objectFrom(args.getString("device"));
                History history = History.objectFrom(args.getString("history"));
                if (config != null && device != null && history != null) event = new CastEvent(config, device, history);
            } catch (Exception ignored) {
            }
        }
        return event;
    }

    public void show(FragmentActivity activity) {
        // 投屏事件经 EventBus 异步到达：宿主已走完存状态流程时 show 必抛，先按本批异步弹窗同口径守住
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || activity.getSupportFragmentManager().isStateSaved()) return;
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof ReceiveDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    public void show(Fragment fragment) {
        if (fragment == null || !fragment.isAdded() || fragment.isStateSaved()) return;
        FragmentActivity activity = fragment.getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || fragment.getChildFragmentManager().isStateSaved()) return;
        for (Fragment f : fragment.getChildFragmentManager().getFragments()) if (f instanceof ReceiveDialog) return;
        show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogReceiveBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        CastEvent e = event();
        if (e == null) {
            dismissAllowingStateLoss();
            return;
        }
        History item = e.history();
        binding.name.setText(item.getVodName());
        binding.from.setText(e.device().getName());
        ImgUtil.load(item.getVodName(), item.getVodPic(), binding.image);
    }

    @Override
    protected void initEvent() {
        binding.frame.setOnClickListener(v -> onReceiveCast());
    }

    private void showProgress() {
        binding.frame.setEnabled(false);
        binding.play.setVisibility(View.GONE);
        binding.progress.getRoot().setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        binding.frame.setEnabled(true);
        binding.play.setVisibility(View.VISIBLE);
        binding.progress.getRoot().setVisibility(View.GONE);
    }

    private void onReceiveCast() {
        CastEvent e = event();
        if (e == null) {
            dismissAllowingStateLoss();
            return;
        }
        if (VodConfig.get().getConfig().equals(e.config())) {
            VideoActivity.cast(requireActivity(), e.history().save(VodConfig.getCid()));
            dismiss();
        } else {
            showProgress();
            VodConfig.load(e.config(), getCallback());
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                onReceiveCast();
                hideProgress();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                hideProgress();
            }
        };
    }
}
