package com.example.hikerview.ui.view.popup;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.transition.ChangeBounds;
import androidx.transition.ChangeImageTransform;
import androidx.transition.ChangeTransform;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.example.hikerview.R;
import com.example.hikerview.ui.view.BaseImageLoader;
import com.example.hikerview.utils.DisplayUtil;
import com.example.hikerview.utils.ImgUtil;
import com.example.hikerview.utils.ToastMgr;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.enums.PopupStatus;
import com.lxj.xpopup.interfaces.OnDragChangeListener;
import com.lxj.xpopup.interfaces.OnImageViewerLongPressListener;
import com.lxj.xpopup.photoview.PhotoView;
import com.lxj.xpopup.util.XPopupUtils;
import com.lxj.xpopup.widget.BlankView;
import com.lxj.xpopup.widget.HackyViewPager;
import com.lxj.xpopup.widget.PhotoViewContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 照官 8.83 本体转写（官 smali_classes2/com/example/hikerview/ui/view/
 * popup/MyImageViewerPopupView.smali 及 $1–$5、$PhotoViewAdapter）：
 * 海阔自家大图查看器面，XPopup 同名查看器的官改版——布局走官自家
 * R.layout.xpopup_image_viewer_popup_view（本批新建，见 res 清单），
 * 加载口是海阔自家 {@link XPopupImageLoader}（4 法版），长按菜单与
 * 「保存/原图」钮行为照官体。新建面，皮走官建面链（XPopup），与既有
 * 页面布局无关、不改任何现有布局。
 */
public class MyImageViewerPopupView extends BasePopupView implements OnDragChangeListener, View.OnClickListener {

    protected ArgbEvaluator argbEvaluator = new ArgbEvaluator();
    protected int bgColor = Color.rgb(32, 36, 46);
    protected FrameLayout container;
    private boolean customSaveBtn = false;
    protected View customView;
    protected XPopupImageLoader imageLoader;
    protected boolean isInfinite = false;
    protected boolean isShowIndicator = true;
    protected boolean isShowPlaceholder = true;
    protected boolean isShowSaveBtn = true;
    public OnImageViewerLongPressListener longPressListener;
    private Runnable onShowTask;
    protected HackyViewPager pager;
    protected PhotoViewContainer photoViewContainer;
    protected int placeholderColor = Color.parseColor("#f1f1f1");
    protected int placeholderRadius = -1;
    protected int placeholderStrokeColor = -1;
    protected BlankView placeholderView;
    protected int position;
    protected Rect rect = null;
    protected PhotoView snapshotView;
    protected ImageView srcView;
    protected OnSrcViewUpdateListener srcViewUpdateListener;
    private Map<Object, Boolean> tagMap = new HashMap<>();
    protected TextView tv_pager_indicator;
    protected TextView tv_save;
    protected List<Object> urls = new ArrayList<>();

    public MyImageViewerPopupView(Context context) {
        super(context);
        container = findViewById(R.id.container);
        if (getImplLayoutId() > 0) {
            customView = LayoutInflater.from(getContext()).inflate(getImplLayoutId(), container, false);
            customView.setVisibility(View.INVISIBLE);
            customView.setAlpha(0f);
            container.addView(customView);
        }
    }

    @Override
    protected final int getInnerLayoutId() {
        return R.layout.xpopup_image_viewer_popup_view;
    }

    @Override
    protected void initPopupContent() {
        super.initPopupContent();
        tv_pager_indicator = findViewById(R.id.tv_pager_indicator);
        tv_save = findViewById(R.id.tv_save);
        placeholderView = findViewById(R.id.placeholderView);
        photoViewContainer = findViewById(R.id.photoViewContainer);
        photoViewContainer.setOnDragChangeListener(this);
        pager = findViewById(R.id.pager);
        PhotoViewAdapter adapter = new PhotoViewAdapter();
        pager.setAdapter(adapter);
        pager.setCurrentItem(position);
        pager.setVisibility(View.INVISIBLE);
        addOrUpdateSnapshot();
        pager.setOffscreenPageLimit(2);
        pager.addOnPageChangeListener(adapter);
        if (!isShowIndicator) {
            tv_pager_indicator.setVisibility(View.GONE);
        }
        if (!isShowSaveBtn) {
            tv_save.setVisibility(View.GONE);
        } else {
            tv_save.setOnClickListener(this);
        }
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (!customSaveBtn) {
                    Object url = urls.get(isInfinite ? position % urls.size() : position);
                    Boolean isOriginal = tagMap.get(url);
                    tv_save.setText(isOriginal != null && isOriginal ? "原图" : "保存");
                }
            }
        });
    }

    private void addOrUpdateSnapshot() {
        if (srcView == null) {
            return;
        }
        if (snapshotView == null) {
            snapshotView = new PhotoView(getContext());
            snapshotView.setEnabled(false);
            photoViewContainer.addView(snapshotView);
            snapshotView.setScaleType(srcView.getScaleType());
            snapshotView.setTranslationX(rect.left);
            snapshotView.setTranslationY(rect.top);
            XPopupUtils.setWidthHeight(snapshotView, rect.width(), rect.height());
        }
        int realPosition = getRealPosition();
        snapshotView.setTag(realPosition);
        setupPlaceholder();
        if (imageLoader != null) {
            imageLoader.loadSnapshot(urls.get(realPosition), snapshotView, srcView);
        }
    }

    private void setupPlaceholder() {
        placeholderView.setVisibility(isShowPlaceholder ? View.VISIBLE : View.INVISIBLE);
        if (isShowPlaceholder) {
            if (placeholderColor != -1) {
                placeholderView.color = placeholderColor;
            }
            if (placeholderRadius != -1) {
                placeholderView.radius = placeholderRadius;
            }
            if (placeholderStrokeColor != -1) {
                placeholderView.strokeColor = placeholderStrokeColor;
            }
            XPopupUtils.setWidthHeight(placeholderView, rect.width(), rect.height());
            placeholderView.setTranslationX(rect.left);
            placeholderView.setTranslationY(rect.top);
            placeholderView.invalidate();
        }
    }

    private void showPagerIndicator() {
        if (urls.size() > 1) {
            tv_pager_indicator.setText((getRealPosition() + 1) + "/" + urls.size());
        }
        if (isShowSaveBtn) {
            tv_save.setVisibility(View.VISIBLE);
        }
    }

    private void animateShadowBg(int endColor) {
        int startColor = ((ColorDrawable) photoViewContainer.getBackground()).getColor();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                photoViewContainer.setBackgroundColor((Integer) argbEvaluator.evaluate(animation.getAnimatedFraction(), startColor, endColor));
            }
        });
        animator.setDuration(getAnimationDuration());
        animator.setInterpolator(new LinearInterpolator());
        animator.start();
    }

    @Override
    public void doShowAnimation() {
        if (srcView == null) {
            photoViewContainer.setBackgroundColor(bgColor);
            pager.setVisibility(View.VISIBLE);
            showPagerIndicator();
            photoViewContainer.isReleasing = false;
            doAfterShow();
            if (customView != null) {
                customView.setAlpha(1f);
                customView.setVisibility(View.VISIBLE);
            }
            return;
        }
        photoViewContainer.isReleasing = true;
        if (customView != null) {
            customView.setVisibility(View.VISIBLE);
        }
        snapshotView.setVisibility(View.VISIBLE);
        doAfterShow();
        snapshotView.post(new Runnable() {
            @Override
            public void run() {
                TransitionSet transitionSet = new TransitionSet()
                        .setDuration(getAnimationDuration())
                        .addTransition(new ChangeBounds())
                        .addTransition(new ChangeTransform())
                        .addTransition(new ChangeImageTransform())
                        .setInterpolator(new FastOutSlowInInterpolator());
                transitionSet.addListener(new Transition.TransitionListener() {
                    @Override
                    public void onTransitionStart(Transition transition) {
                    }

                    @Override
                    public void onTransitionEnd(Transition transition) {
                        pager.setVisibility(View.VISIBLE);
                        snapshotView.setVisibility(View.INVISIBLE);
                        showPagerIndicator();
                        photoViewContainer.isReleasing = false;
                    }

                    @Override
                    public void onTransitionCancel(Transition transition) {
                    }

                    @Override
                    public void onTransitionPause(Transition transition) {
                    }

                    @Override
                    public void onTransitionResume(Transition transition) {
                    }
                });
                TransitionManager.beginDelayedTransition((ViewGroup) snapshotView.getParent(), transitionSet);
                snapshotView.setTranslationY(0f);
                snapshotView.setTranslationX(0f);
                snapshotView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                XPopupUtils.setWidthHeight(snapshotView, photoViewContainer.getWidth(), photoViewContainer.getHeight());
                animateShadowBg(bgColor);
                if (customView != null) {
                    customView.animate().alpha(1f).setDuration(getAnimationDuration()).start();
                }
            }
        });
    }

    @Override
    public void doDismissAnimation() {
        if (srcView == null) {
            photoViewContainer.setBackgroundColor(0);
            doAfterDismiss();
            pager.setVisibility(View.INVISIBLE);
            placeholderView.setVisibility(View.INVISIBLE);
            if (customView != null) {
                customView.setAlpha(0f);
                customView.setVisibility(View.INVISIBLE);
            }
            return;
        }
        tv_pager_indicator.setVisibility(View.INVISIBLE);
        tv_save.setVisibility(View.INVISIBLE);
        pager.setVisibility(View.INVISIBLE);
        photoViewContainer.isReleasing = true;
        snapshotView.setVisibility(View.VISIBLE);
        snapshotView.post(new Runnable() {
            @Override
            public void run() {
                TransitionSet transitionSet = new TransitionSet()
                        .setDuration(getAnimationDuration())
                        .addTransition(new ChangeBounds())
                        .addTransition(new ChangeTransform())
                        .addTransition(new ChangeImageTransform())
                        .setInterpolator(new FastOutSlowInInterpolator());
                transitionSet.addListener(new Transition.TransitionListener() {
                    @Override
                    public void onTransitionStart(Transition transition) {
                        doAfterDismiss();
                    }

                    @Override
                    public void onTransitionEnd(Transition transition) {
                        pager.setScaleX(1f);
                        pager.setScaleY(1f);
                        snapshotView.setScaleX(1f);
                        snapshotView.setScaleY(1f);
                        placeholderView.setVisibility(View.INVISIBLE);
                        snapshotView.setTranslationX(rect.left);
                        snapshotView.setTranslationY(rect.top);
                        XPopupUtils.setWidthHeight(snapshotView, rect.width(), rect.height());
                    }

                    @Override
                    public void onTransitionCancel(Transition transition) {
                    }

                    @Override
                    public void onTransitionPause(Transition transition) {
                    }

                    @Override
                    public void onTransitionResume(Transition transition) {
                    }
                });
                TransitionManager.beginDelayedTransition((ViewGroup) snapshotView.getParent(), transitionSet);
                snapshotView.setScaleX(1f);
                snapshotView.setScaleY(1f);
                snapshotView.setTranslationX(rect.left);
                snapshotView.setTranslationY(rect.top);
                snapshotView.setScaleType(srcView.getScaleType());
                XPopupUtils.setWidthHeight(snapshotView, rect.width(), rect.height());
                animateShadowBg(0);
                if (customView != null) {
                    customView.animate().alpha(0f).setDuration(getAnimationDuration())
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    super.onAnimationEnd(animation);
                                    if (customView != null) {
                                        customView.setVisibility(View.INVISIBLE);
                                    }
                                }
                            }).start();
                }
            }
        });
    }

    @Override
    public void dismiss() {
        if (popupStatus != PopupStatus.Show) {
            return;
        }
        popupStatus = PopupStatus.Dismissing;
        doDismissAnimation();
    }

    @Override
    public void destroy() {
        super.destroy();
        pager.clearOnPageChangeListeners();
        imageLoader = null;
    }

    @Override
    protected void onDismiss() {
        super.onDismiss();
        srcView = null;
        srcViewUpdateListener = null;
    }

    @Override
    protected void onShow() {
        super.onShow();
        if (onShowTask != null) {
            onShowTask.run();
        }
    }

    @Override
    public void onRelease() {
        dismiss();
    }

    @Override
    public void onDragChange(int dy, float scale, float fraction) {
        tv_pager_indicator.setAlpha(1f - fraction);
        if (customView != null) {
            customView.setAlpha(1f - fraction);
        }
        if (isShowSaveBtn) {
            tv_save.setAlpha(1f - fraction);
        }
        photoViewContainer.setBackgroundColor((Integer) argbEvaluator.evaluate(fraction * 0.8f, bgColor, 0));
    }

    public MyImageViewerPopupView setImageUrls(List<Object> urls) {
        this.urls = urls;
        return this;
    }

    public MyImageViewerPopupView setSrcViewUpdateListener(OnSrcViewUpdateListener srcViewUpdateListener) {
        this.srcViewUpdateListener = srcViewUpdateListener;
        return this;
    }

    public MyImageViewerPopupView setSingleSrcView(ImageView imageView, Object url) {
        if (urls == null) {
            urls = new ArrayList<>();
        }
        urls.clear();
        urls.add(url);
        setSrcView(imageView, 0);
        return this;
    }

    public MyImageViewerPopupView setSrcView(ImageView imageView, int position) {
        this.srcView = imageView;
        this.position = position;
        if (imageView != null) {
            int[] location = new int[2];
            imageView.getLocationInWindow(location);
            int left = location[0] - getActivityContentLeft();
            if (XPopupUtils.isLayoutRtl(getContext())) {
                left = -(XPopupUtils.getAppWidth(getContext()) - location[0] - imageView.getWidth());
            }
            rect = new Rect(left, location[1], left + imageView.getWidth(), location[1] + imageView.getHeight());
        }
        return this;
    }

    public void updateSrcView(ImageView imageView) {
        setSrcView(imageView, position);
        addOrUpdateSnapshot();
    }

    public MyImageViewerPopupView isShowPlaceholder(boolean isShowPlaceholder) {
        this.isShowPlaceholder = isShowPlaceholder;
        return this;
    }

    public MyImageViewerPopupView isShowIndicator(boolean isShowIndicator) {
        this.isShowIndicator = isShowIndicator;
        return this;
    }

    public MyImageViewerPopupView isShowSaveButton(boolean isShowSaveBtn) {
        this.isShowSaveBtn = isShowSaveBtn;
        return this;
    }

    public MyImageViewerPopupView isInfinite(boolean isInfinite) {
        this.isInfinite = isInfinite;
        return this;
    }

    public MyImageViewerPopupView setPlaceholderColor(int placeholderColor) {
        this.placeholderColor = placeholderColor;
        return this;
    }

    public MyImageViewerPopupView setPlaceholderRadius(int placeholderRadius) {
        this.placeholderRadius = placeholderRadius;
        return this;
    }

    public MyImageViewerPopupView setPlaceholderStrokeColor(int placeholderStrokeColor) {
        this.placeholderStrokeColor = placeholderStrokeColor;
        return this;
    }

    public MyImageViewerPopupView setBgColor(int bgColor) {
        this.bgColor = bgColor;
        return this;
    }

    public MyImageViewerPopupView setLongPressListener(OnImageViewerLongPressListener longPressListener) {
        this.longPressListener = longPressListener;
        return this;
    }

    public MyImageViewerPopupView setXPopupImageLoader(XPopupImageLoader imageLoader) {
        this.imageLoader = imageLoader;
        if (imageLoader instanceof BaseImageLoader) {
            ((BaseImageLoader) imageLoader).setTagListener((imageView, url, tag) -> {
                if (tag != null && tag.contains("/")) {
                    tagMap.put(url, true);
                } else {
                    tagMap.put(url, false);
                }
                if (!customSaveBtn && pager != null && pager.getAdapter() != null
                        && pager.getAdapter() instanceof PhotoViewAdapter) {
                    PhotoViewAdapter adapter = (PhotoViewAdapter) pager.getAdapter();
                    ImageView currentImageView = adapter.getCurrentImageView();
                    if (currentImageView == null) {
                        if (adapter.getCurrentScaleImageView() != null) {
                            tv_save.setText("保存");
                        }
                    } else if (currentImageView == imageView) {
                        tv_save.setText(tag != null && tag.contains("/") ? "原图" : "保存");
                    }
                }
            });
        }
        return this;
    }

    protected int getRealPosition() {
        return isInfinite ? position % urls.size() : position;
    }

    public int getPos() {
        return position;
    }

    public TextView getSaveView() {
        return tv_save;
    }

    public boolean isCustomSaveBtn() {
        return customSaveBtn;
    }

    public void setCustomSaveBtn(boolean customSaveBtn) {
        this.customSaveBtn = customSaveBtn;
    }

    public Runnable getOnShowTask() {
        return onShowTask;
    }

    public void setOnShowTask(Runnable onShowTask) {
        this.onShowTask = onShowTask;
    }

    @Override
    public void onClick(View v) {
        if (v == tv_save) {
            if ("保存".contentEquals(tv_save.getText())) {
                save();
                return;
            }
            if (pager != null && pager.getAdapter() != null && pager.getAdapter() instanceof PhotoViewAdapter) {
                ImageView currentImageView = ((PhotoViewAdapter) pager.getAdapter()).getCurrentImageView();
                if (currentImageView != null && imageLoader != null) {
                    Object url = urls.get(getRealPosition());
                    imageLoader.loadImage(position, url instanceof String ? url + "#originalSize#" : url, this, currentImageView);
                    ToastMgr.shortBottomCenter(getContext(), "已加载原图");
                }
            }
        }
    }

    protected void save() {
        Object url = urls.get(getRealPosition());
        ImgUtil.savePic2Gallery(container.getContext(), imageLoader, url, new ImgUtil.OnSaveListener() {
            @Override
            public void success(List<String> list) {
                Toast.makeText(getContext(), "保存成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void failed(String error) {
                Toast.makeText(getContext(), "保存失败：" + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public class PhotoViewAdapter extends PagerAdapter implements ViewPager.OnPageChangeListener {

        private View mView;

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            mView = (View) object;
            super.setPrimaryItem(container, position, object);
        }

        public View getCurrentView() {
            return mView;
        }

        public ImageView getCurrentImageView() {
            return (ImageView) findCurrentView(ImageView.class);
        }

        public SubsamplingScaleImageView getCurrentScaleImageView() {
            return (SubsamplingScaleImageView) findCurrentView(SubsamplingScaleImageView.class);
        }

        public View findCurrentView(Class clazz) {
            if (mView == null) {
                return null;
            }
            if (clazz.isAssignableFrom(mView.getClass())) {
                return mView;
            }
            if (mView instanceof ViewGroup) {
                return findChildView((ViewGroup) mView, clazz);
            }
            return null;
        }

        private View findChildView(ViewGroup viewGroup, Class clazz) {
            if (viewGroup == null) {
                return null;
            }
            int count = viewGroup.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = viewGroup.getChildAt(i);
                if (clazz.isAssignableFrom(child.getClass())) {
                    return child;
                }
                if (child instanceof ViewGroup) {
                    View view = findChildView((ViewGroup) child, clazz);
                    if (view != null) {
                        return view;
                    }
                }
            }
            return null;
        }

        @Override
        public int getCount() {
            return isInfinite ? 100000 : urls.size();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            int realPosition = isInfinite ? position % urls.size() : position;
            FrameLayout frameLayout = buildContainer(container.getContext());
            ProgressBar progressBar = buildProgressBar(container.getContext());
            View view = imageLoader.loadImage(realPosition, urls.get(realPosition), MyImageViewerPopupView.this, snapshotView, progressBar);
            view.setOnLongClickListener(v -> {
                new XPopup.Builder(getContext())
                        .borderRadius((float) DisplayUtil.dpToPx(getContext(), 16))
                        .asCenterList(null, new String[]{"保存原图"}, (index, text) -> {
                            if ("保存原图".equals(text)) {
                                save();
                            }
                        })
                        .show();
                return true;
            });
            frameLayout.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            frameLayout.addView(progressBar);
            container.addView(frameLayout);
            return frameLayout;
        }

        private FrameLayout buildContainer(Context context) {
            FrameLayout frameLayout = new FrameLayout(context);
            frameLayout.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return frameLayout;
        }

        private ProgressBar buildProgressBar(Context context) {
            ProgressBar progressBar = new ProgressBar(context);
            progressBar.setIndeterminate(true);
            int size = XPopupUtils.dp2px(context, 40f);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
            params.gravity = Gravity.CENTER;
            progressBar.setLayoutParams(params);
            progressBar.setVisibility(View.GONE);
            return progressBar;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public void onPageSelected(int position) {
            MyImageViewerPopupView.this.position = position;
            showPagerIndicator();
            if (srcViewUpdateListener != null) {
                srcViewUpdateListener.onSrcViewUpdate(MyImageViewerPopupView.this, getRealPosition());
            }
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }
    }
}
