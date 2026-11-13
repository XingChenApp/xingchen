package com.fongmi.android.tv.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.playback.PlaybackResult;
import com.fongmi.android.tv.playback.vod.VodDataSource;
import com.fongmi.android.tv.playback.vod.VodDetailResult;
import com.fongmi.android.tv.playback.vod.VodPlayRequest;
import com.fongmi.android.tv.playback.vod.VodPlaybackController;
import com.fongmi.android.tv.playback.vod.VodPlaybackHost;
import com.fongmi.android.tv.playback.vod.VodPlaybackState;
import com.github.catvod.utils.Task;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

// 视频视图模型层：实现点播数据源头接口，结构照定稿视频视图模型，取数口径按树里接口适配：
// 详情接口无内置位形参，内置与否不影响详情取数；树里无预热专用接口，预热与播片走同一解析。
public class VideoViewModel extends SiteViewModel implements VodDataSource {

    private final MutableLiveData<VodDetailResult> detail;
    private final MutableLiveData<PlaybackResult<VodPlayRequest>> preload;
    private final MutableLiveData<PlaybackResult<VodPlayRequest>> playback;
    private final Map<String, Future<?>> tasks;
    private final VodPlaybackState playbackState;

    public VideoViewModel() {
        detail = new MutableLiveData<>();
        preload = new MutableLiveData<>();
        playback = new MutableLiveData<>();
        tasks = new ConcurrentHashMap<>();
        playbackState = new VodPlaybackState();
    }

    public LiveData<VodDetailResult> getDetail() {
        return detail;
    }

    public LiveData<PlaybackResult<VodPlayRequest>> getPreload() {
        return preload;
    }

    public LiveData<PlaybackResult<VodPlayRequest>> getPlayback() {
        return playback;
    }

    public VodPlaybackController createPlaybackController(VodPlaybackHost host) {
        return new VodPlaybackController(host, this, playbackState);
    }

    @Override
    public void detailContent(String key, String id) {
        detailContent(key, id, false);
    }

    @Override
    public void detailContent(String key, String id, boolean builtin) {
        cancelTask("detail");
        tasks.put("detail", Task.submit(() -> {
            try {
                detail.postValue(new VodDetailResult(key, id, SiteApi.detailContent(key, id)));
            } catch (Throwable e) {
                String msg = e.getMessage();
                // 扫描 T7：无 message 的异常也要投 Result.error（与 SiteViewModel 同口径），
                // 不许吞成 Result.empty()——用户只看到空白页、无任何错误文案可循
                detail.postValue(new VodDetailResult(key, id, Result.error(msg != null ? msg : "加载失败，请重试")));
            }
        }));
    }

    @Override
    public void playerContent(VodPlayRequest request) {
        cancelTask("playback");
        tasks.put("playback", Task.submit(() -> {
            try {
                playback.postValue(new PlaybackResult<>(request, SiteApi.playerContent(request.getKey(), request.getFlag(), request.getId())));
            } catch (Throwable e) {
                String msg = e.getMessage();
                playback.postValue(new PlaybackResult<>(request, Result.error(msg != null ? msg : "播放失败，请重试")));
            }
        }));
    }

    @Override
    public void preloadContent(VodPlayRequest request) {
        cancelTask("preload");
        tasks.put("preload", Task.submit(() -> {
            try {
                preload.postValue(new PlaybackResult<>(request, SiteApi.playerContent(request.getKey(), request.getFlag(), request.getId())));
            } catch (Throwable e) {
                String msg = e.getMessage();
                preload.postValue(new PlaybackResult<>(request, Result.error(msg != null ? msg : "播放失败，请重试")));
            }
        }));
    }

    @Override
    public void cancelPreloadContent() {
        cancelTask("preload");
    }

    @Override
    public void cancelContent() {
        cancelTask("detail");
        cancelTask("playback");
        cancelTask("preload");
    }

    @Override
    protected void onCleared() {
        cancelContent();
        playbackState.reset();
        super.onCleared();
    }

    private void cancelTask(String type) {
        Future<?> future = tasks.remove(type);
        if (future != null) future.cancel(true);
    }
}
