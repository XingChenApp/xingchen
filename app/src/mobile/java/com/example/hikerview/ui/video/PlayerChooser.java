package com.example.hikerview.ui.video;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import com.example.hikerview.ui.ActivityManager;
import com.example.hikerview.utils.ToastMgr;
import com.fongmi.android.tv.ui.hk.HkArticleFragment;
import com.fongmi.android.tv.ui.hk.HkDetailBridge;

import java.util.ArrayList;
import java.util.List;

/**
 * 官 {@code com.example.hikerview.ui.video.PlayerChooser} 的插件面适配。
 *
 * <p>官本体是「播放器偏好选择＋VideoPlayerActivity 意图拼装」，星辰的等价
 * 落点是 {@code ui.hk.HkDetailBridge} 的 startPlayerSingle/startPlayerChapters
 * （宿主 HkListActivity 实现，最终走 VideoActivity.startHkPlay），本类只做
 * 签名对齐、官原样前置校验与 VideoChapter → HkChapter 的字段转换。
 * 官的播放器偏好分流（third player 等）在星辰无对应物，不在此复刻。
 * 官 list 版的集名公共前缀裁剪（findSameCharStartCount 写 st）由星辰
 * startPlayerChapters 侧的标题处理承接，本类不重复做。
 */
public class PlayerChooser {

    public static void startPlayer(Context context, List<VideoChapter> list) {
        startPlayer(context, list, null, null, null);
    }

    public static void startPlayer(Context context, List<VideoChapter> list,
                                   String cUrl, String mTitle, Bundle bundle) {
        if (context == null) {
            return;
        }
        if (list == null || list.size() <= 0) {
            ToastMgr.shortBottomCenter(context, "选集有误");
            return;
        }
        boolean hasUse = false;
        for (VideoChapter chapter : list) {
            if (chapter != null && chapter.isUse()) {
                hasUse = true;
                break;
            }
        }
        if (!hasUse) {
            ToastMgr.shortBottomCenter(context, "没有选中的集数");
            return;
        }
        Activity activity = activityOf(context);
        if (activity == null) {
            return;
        }
        List<HkArticleFragment.HkChapter> chapters = new ArrayList<>(list.size());
        for (VideoChapter chapter : list) {
            if (chapter == null) continue;
            HkArticleFragment.HkChapter hc = new HkArticleFragment.HkChapter();
            hc.title = chapter.getTitle();
            hc.memoryTitle = chapter.getMemoryTitle();
            hc.url = chapter.getUrl();
            hc.originalUrl = chapter.getOriginalUrl();
            hc.extra = chapter.getExtra();
            hc.pic = chapter.getPicUrl();
            hc.codeAndHeader = chapter.getCodeAndHeader();
            hc.realPos = chapter.getRealPos();
            hc.use = chapter.isUse();
            chapters.add(hc);
        }
        new HkDetailBridge().startPlayerChapters(activity, chapters, cUrl, mTitle, bundle);
    }

    public static void startPlayer(Context context, String title, String url) {
        if (context == null) {
            return;
        }
        Activity activity = activityOf(context);
        if (activity == null) {
            return;
        }
        new HkDetailBridge().startPlayerSingle(activity, title, url);
    }

    private static Activity activityOf(Context context) {
        if (context instanceof Activity) {
            return (Activity) context;
        }
        return ActivityManager.getInstance().getCurrentActivity();
    }
}
