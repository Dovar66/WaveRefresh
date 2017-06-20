package com.dovar.waverefresh.wave;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.Transformation;
import android.widget.TextView;

import com.dovar.waverefresh.R;

import java.util.Timer;
import java.util.TimerTask;


/**
 * Created by Administrator on 2016/11/11 0011.
 */
public class WaveRefreshLayout extends ViewGroup implements ViewTreeObserver.OnPreDrawListener {

    private enum VERTICAL_DRAG_THRESHOLD {
        FIRST(0.1f), SECOND(0.16f + FIRST.val), THIRD(0.5f + FIRST.val);

        final float val;

        VERTICAL_DRAG_THRESHOLD(float val) {
            this.val = val;
        }
    }

    private enum STATE {
        REFRESHING, PENDING;
    }

    private enum EVENT_PHASE {
        WAITING, BEGINNING, APPEARING, EXPANDING, DROPPING;
    }

    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;

    private static final int INVALID_POINTER = -1;

    private static final float DRAGGING_WEIGHT = 0.5f;

    /**
     * 落ちる前の回転の最大のAngle値
     */
    private static final float MAX_PROGRESS_ROTATION_RATE = 0.8f;


    private static final int SCALE_DOWN_DURATION = 200;


    private static final int ANIMATE_TO_TRIGGER_DURATION = 200;

    /**
     * デフォルトのCircleのTargetの値
     */
    private static final int DEFAULT_CIRCLE_TARGET = 64;

    /**
     * リフレッシュ状態
     */
    private STATE mState = STATE.PENDING;

    private EVENT_PHASE mEventPhase = EVENT_PHASE.WAITING;


    private final DecelerateInterpolator mDecelerateInterpolator;

    private ProgressAnimationImageView mCircleView;

    /**
     * 水滴View
     */
    private WaveView mWaveView;

    private boolean mNotify;

    private boolean mIsManualRefresh = false;

    private float mFirstTouchDownPointY;

    private boolean mIsBeingDropped;

    private int mActivePointerId = INVALID_POINTER;

    private int mTopOffset;

    /**
     * 检查上拉加载功能是否开启
     */
    public boolean isEnabledPullUp() {
        return enabledPullUp;
    }

    /**
     * 开关上拉加载功能
     */
    public void setEnabledPullUp(boolean enabledPullUp) {
        this.enabledPullUp = enabledPullUp;
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        mListener = listener;
    }

    public void setTopOffsetOfWave(int topOffset) {
        if (topOffset < 0) {
            return;
        }
        mTopOffset = topOffset;
        layoutWaveView();
    }


    public WaveRefreshLayout(Context context) {
        this(context, null);
    }

    public WaveRefreshLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WaveRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        getViewTreeObserver().addOnPreDrawListener(this);

//        setWillNotDraw(false);
        mDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);

//        ViewCompat.setChildrenDrawingOrderEnabled(this, true);

        createWaveView();
        createProgressView();
        createLoadMoreView(context);

        initView(context);

    }

    private void createLoadMoreView(Context context) {
        loadmoreView = View.inflate(context, R.layout.load_more, null);
        addView(loadmoreView, 2);
        initLoadView();
    }

    private void createProgressView() {
        addView(mCircleView = new ProgressAnimationImageView(getContext()), 1);
    }

    private void createWaveView() {
        mWaveView = new WaveView(getContext());
        addView(mWaveView, 0);
    }

    /**
     * 需要在重写的onMeasure方法中测量子控件之后在onLayout（）才能获取到有效宽高
     * 或者继承viewGroup改成relativelayout等子类亦是同理
     */
    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        Log.d("tests", "onMeasure: ");
        ensureTarget();
//
//        //// Propagates measuring to child view and to circle view
//
//        // Measures on child view with each directed length without padding
        contentView.measure(
//        pullableView.measure(
                makeMeasureSpecExactly(getMeasuredWidth() - (getPaddingLeft() + getPaddingRight())),
                makeMeasureSpecExactly(getMeasuredHeight() - (getPaddingTop() + getPaddingBottom())));
        mWaveView.measure(widthMeasureSpec, heightMeasureSpec);
        mWaveView.setMaxDropHeight(getMeasuredHeight() / 2);

        mCircleView.measure();
        loadmoreView.measure(widthMeasureSpec, heightMeasureSpec);
        loadmoreDist = ((ViewGroup) loadmoreView).getChildAt(0).getMeasuredHeight();
        Log.d("test", "createLoadMoreView: " + loadmoreDist);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getChildCount() == 0) {
            return;
        }

        ensureTarget();
        contentView.layout(getPaddingLeft(), getPaddingTop() + (int) pullUpY, contentView.getMeasuredWidth(), contentView.getMeasuredHeight() + (int) pullUpY);

        layoutWaveView();

        loadmoreView.layout(0,
                (int) pullUpY + contentView.getMeasuredHeight() + getPaddingTop(),
                loadmoreView.getMeasuredWidth(),
                (int) pullUpY + contentView.getMeasuredHeight() + loadmoreView.getMeasuredHeight() + getPaddingTop());
    }


    private void layoutWaveView() {
        if (mWaveView == null) {
            return;
        }
        final int thisWidth = getMeasuredWidth();
        final int thisHeight = getMeasuredHeight();

        final int circleWidth = mCircleView.getMeasuredWidth();
        final int circleHeight = mCircleView.getMeasuredHeight();
        mCircleView.layout((thisWidth - circleWidth) / 2, -circleHeight + mTopOffset,
                (thisWidth + circleWidth) / 2, mTopOffset);
        final int childRight = thisWidth - getPaddingRight();
        final int childBottom = thisHeight - getPaddingBottom();
        mWaveView.layout(getPaddingLeft(), mTopOffset + getPaddingTop(), childRight, childBottom);
    }


    @Override
    public boolean onPreDraw() {
        Log.d("test", "onPreDraw: ");
        getViewTreeObserver().removeOnPreDrawListener(this);
        mWaveView.bringToFront();
        mCircleView.bringToFront();
        if (mIsManualRefresh) {
            mIsManualRefresh = false;
            mWaveView.manualRefresh();
            reInitCircleView();
            mCircleView.setBackgroundColor(Color.TRANSPARENT);
            mCircleView.setTranslationY(
                    mWaveView.getCurrentCircleCenterY() + mCircleView.getHeight() / 2);
            animateOffsetToCorrectPosition();
        }
        return false;
    }

    /**
     * Make circle view be visible and even scale.
     */
    private void reInitCircleView() {
        if (mCircleView.getVisibility() != View.VISIBLE) {
            mCircleView.setVisibility(View.VISIBLE);
        }

        mCircleView.scaleWithKeepingAspectRatio(1f);
        mCircleView.makeProgressTransparent();
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        ensureTarget();

        if (!isEnabled() || canChildScrollUp() || isRefreshing()) {
            return false;
        }

        final int action = MotionEventCompat.getActionMasked(event);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = MotionEventCompat.getPointerId(event, 0);
                mFirstTouchDownPointY = getMotionEventY(event, mActivePointerId);
                break;

            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER) {
                    return false;
                }

                final float currentY = getMotionEventY(event, mActivePointerId);

                if (currentY == -1) {
                    return false;
                }

                if (mFirstTouchDownPointY == -1) {
                    mFirstTouchDownPointY = currentY;
                }

                final float yDiff = currentY - mFirstTouchDownPointY;

                // State is changed to drag if over slop
                if (yDiff > ViewConfiguration.get(getContext()).getScaledTouchSlop() && !isRefreshing()) {
                    mCircleView.makeProgressTransparent();
                    return true;
                }

                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mActivePointerId = INVALID_POINTER;
                break;
        }

        return false;
    }

    private boolean onMoveTouchEvent(@NonNull MotionEvent event, int pointerIndex) {
        if (mIsBeingDropped) {
            return false;
        }

        final float y = MotionEventCompat.getY(event, pointerIndex);
        final float diffY = y - mFirstTouchDownPointY;
        final float overScrollTop = diffY * DRAGGING_WEIGHT;

        if (overScrollTop < 0) {
            mCircleView.showArrow(false);
            return false;
        }

        final DisplayMetrics metrics = getResources().getDisplayMetrics();

        float originalDragPercent = overScrollTop / (DEFAULT_CIRCLE_TARGET * metrics.density);
        float dragPercent = Math.min(1f, originalDragPercent);
        float adjustedPercent = (float) Math.max(dragPercent - .4, 0) * 5 / 3;

        // 0f...2f
        float tensionSlingshotPercent =
                (originalDragPercent > 3f) ? 2f : (originalDragPercent > 1f) ? originalDragPercent - 1f : 0;
        float tensionPercent = (4f - tensionSlingshotPercent) * tensionSlingshotPercent / 8f;

        mCircleView.showArrow(true);
        reInitCircleView();

        if (originalDragPercent < 1f) {
            float strokeStart = adjustedPercent * .8f;
            mCircleView.setProgressStartEndTrim(0f, Math.min(MAX_PROGRESS_ROTATION_RATE, strokeStart));
            mCircleView.setArrowScale(Math.min(1f, adjustedPercent));
        }

        float rotation = (-0.25f + .4f * adjustedPercent + tensionPercent * 2) * .5f;
        mCircleView.setProgressRotation(rotation);
        mCircleView.setTranslationY(mWaveView.getCurrentCircleCenterY());

        float seed = diffY / Math.min(getMeasuredWidth(), getMeasuredHeight());
        float firstBounds = seed * (5f - 2 * seed) / 3.5f;
        float secondBounds = firstBounds - VERTICAL_DRAG_THRESHOLD.FIRST.val;
        float finalBounds = (firstBounds - VERTICAL_DRAG_THRESHOLD.SECOND.val) / 5;

        if (firstBounds < VERTICAL_DRAG_THRESHOLD.FIRST.val) {
            // draw a wave and not draw a circle
            onBeginPhase(firstBounds);
        } else if (firstBounds < VERTICAL_DRAG_THRESHOLD.SECOND.val) {
            // draw a circle with a wave
            onAppearPhase(firstBounds, secondBounds);
        } else if (firstBounds < VERTICAL_DRAG_THRESHOLD.THIRD.val) {
            // draw a circle with expanding a wave
            onExpandPhase(firstBounds, secondBounds, finalBounds);
        } else {
            // stop to draw a wave and drop a circle
            onDropPhase();
        }

        return !mIsBeingDropped;
    }

    private void onBeginPhase(float move1) {
        //最初の小波の描画
        mWaveView.beginPhase(move1);

        setEventPhase(EVENT_PHASE.BEGINNING);
    }

    private void onAppearPhase(float move1, float move2) {
        //すでに描画されている波に対して追加で円を描画する
        mWaveView.appearPhase(move1, move2);

        setEventPhase(EVENT_PHASE.APPEARING);
    }

    private void onExpandPhase(float move1, float move2, float move3) {
        mWaveView.expandPhase(move1, move2, move3);

        setEventPhase(EVENT_PHASE.EXPANDING);
    }

    private void onDropPhase() {
        mWaveView.animationDropCircle();

        ValueAnimator animator = ValueAnimator.ofFloat(0, 0);
        animator.setDuration(500);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mCircleView.setTranslationY(
                        mWaveView.getCurrentCircleCenterY() + mCircleView.getHeight() / 2.f);
            }
        });
        animator.start();
        setRefreshing(true, true);
        mIsBeingDropped = true;
        setEventPhase(EVENT_PHASE.DROPPING);
        setEnabled(false);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {

        if (!isEnabled() || canChildScrollUp()) {
            return false;
        }
        mIsBeingDropped = mWaveView.isDisappearCircleAnimatorRunning();

        final int action = MotionEventCompat.getActionMasked(event);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Here is not called from anywhere
                break;

            case MotionEvent.ACTION_MOVE:
                final int pointerIndex = MotionEventCompat.findPointerIndex(event, mActivePointerId);
                return pointerIndex >= 0 && onMoveTouchEvent(event, pointerIndex);

            case MotionEvent.ACTION_UP:
                if (mIsBeingDropped) {
                    mIsBeingDropped = false;
                    return false;
                }

                final float diffY = event.getY() - mFirstTouchDownPointY;
                final float waveHeightThreshold =
                        diffY * (5f - 2 * diffY / Math.min(getMeasuredWidth(), getMeasuredHeight())) / 1000f;
                mWaveView.startWaveAnimation(waveHeightThreshold);

            case MotionEvent.ACTION_CANCEL:
                if (mActivePointerId == INVALID_POINTER) {
                    return false;
                }

                if (!isRefreshing()) {
                    mCircleView.setProgressStartEndTrim(0f, 0f);
                    mCircleView.showArrow(false);
                    mCircleView.setVisibility(GONE);
                }
                mActivePointerId = INVALID_POINTER;
                return false;
        }
        return true;
    }

    private float getMotionEventY(@NonNull MotionEvent ev, int activePointerId) {
        final int index = MotionEventCompat.findPointerIndex(ev, activePointerId);
        if (index < 0) {
            return -1;
        }
        return MotionEventCompat.getY(ev, index);
    }

    private void animateOffsetToCorrectPosition() {
        mAnimateToCorrectPosition.reset();
        mAnimateToCorrectPosition.setDuration(ANIMATE_TO_TRIGGER_DURATION);
        mAnimateToCorrectPosition.setInterpolator(mDecelerateInterpolator);
        mCircleView.setAnimationListener(mRefreshListener);
        mCircleView.clearAnimation();
        mCircleView.startAnimation(mAnimateToCorrectPosition);
    }

    private final Animation mAnimateToCorrectPosition = new Animation() {
        @Override
        public void applyTransformation(float interpolatedTime, @NonNull Transformation t) {
        }
    };

    /**
     * @param dropHeight 下降高度
     */
    public void setMaxDropHeight(int dropHeight) {
        mWaveView.setMaxDropHeight(dropHeight);
    }

    private Animation.AnimationListener mRefreshListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            if (isRefreshing()) {
                mCircleView.makeProgressTransparent();
                mCircleView.startProgress();
                if (mNotify) {
                    if (mListener != null) {
                        mListener.onRefresh(WaveRefreshLayout.this);
                    }
                }
            } else {
                mCircleView.stopProgress();
                mCircleView.setVisibility(View.GONE);
                mCircleView.makeProgressTransparent();
                mWaveView.startDisappearCircleAnimation();
            }
        }
    };

    private void ensureTarget() {
        if (contentView == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!child.equals(mCircleView) && !child.equals(mWaveView) && !child.equals(loadmoreView)) {
                    contentView = child;
                    pullableView=contentView.findViewById(R.id.pullable);
                    break;
                }
            }
        }

        if (contentView == null) {
            throw new IllegalStateException("This view must have at least one AbsListView");
        }
    }

    /**
     * @param refreshing Refreshの状態
     * @param notify     Listenerに通知するかどうか
     */
    private void setRefreshing(boolean refreshing, final boolean notify) {
        if (isRefreshing() != refreshing) {
            mNotify = notify;
            ensureTarget();
            setState(refreshing);
            if (isRefreshing()) {
                animateOffsetToCorrectPosition();
            } else {
                startScaleDownAnimation(mRefreshListener);
            }
        }
    }

    private void setEventPhase(EVENT_PHASE eventPhase) {
        mEventPhase = eventPhase;
    }

    private void setState(STATE state) {
        mState = state;
        setEnabled(true);
        if (!isRefreshing()) {
            setEventPhase(EVENT_PHASE.WAITING);
        }
    }

    private void setState(boolean doRefresh) {
        setState((doRefresh) ? STATE.REFRESHING : STATE.PENDING);
    }

    /**
     * @param listener {@link Animation.AnimationListener}
     */
    private void startScaleDownAnimation(Animation.AnimationListener listener) {
        Animation scaleDownAnimation = new Animation() {
            @Override
            public void applyTransformation(float interpolatedTime, Transformation t) {
                mCircleView.scaleWithKeepingAspectRatio(1 - interpolatedTime);
            }
        };

        scaleDownAnimation.setDuration(SCALE_DOWN_DURATION);
        mCircleView.setAnimationListener(listener);
        mCircleView.clearAnimation();
        mCircleView.startAnimation(scaleDownAnimation);
    }

    /**
     * @param colorResIds ColorのId達
     */
    public void setColorSchemeResources(@IdRes int... colorResIds) {
        mCircleView.setProgressColorSchemeColorsFromResource(colorResIds);
    }

    /**
     * @param colors セットするColor達
     */
    public void setColorSchemeColors(int... colors) {
        // FIXME Add @NonNull to the argument
        ensureTarget();
        mCircleView.setProgressColorSchemeColors(colors);
    }

    /**
     * @return {mState} == REFRESHING
     */
    public boolean isRefreshing() {
        return mState == STATE.REFRESHING;
    }

    private boolean isBeginning() {
        return mEventPhase == EVENT_PHASE.BEGINNING;
    }

    private boolean isExpanding() {
        return mEventPhase == EVENT_PHASE.EXPANDING;
    }

    private boolean isDropping() {
        return mEventPhase == EVENT_PHASE.DROPPING;
    }

    private boolean isAppearing() {
        return mEventPhase == EVENT_PHASE.APPEARING;
    }

    private boolean isWaiting() {
        return mEventPhase == EVENT_PHASE.WAITING;
    }

    /**
     * @param refreshing
     */
    public void setRefreshing(boolean refreshing) {
        if (refreshing && !isRefreshing()) {
            // scale and show
            setState(true);
            mNotify = false;

            mIsManualRefresh = true;
            if (mWaveView.getCurrentCircleCenterY() == 0) {
                return;
            }
            mWaveView.manualRefresh();
            reInitCircleView();
            mCircleView.setTranslationY(
                    mWaveView.getCurrentCircleCenterY() + mCircleView.getHeight() / 2);
            animateOffsetToCorrectPosition();
        } else {
            setRefreshing(refreshing, false /* notify */);
        }
    }

    /**
     * @return ScrollUp出来るかどうか
     */
    public boolean canChildScrollUp() {
        if (pullableView == null) {
            return false;
        }
        return ViewCompat.canScrollVertically(pullableView, -1);
    }

    /**
     * @param radius 波の影の深さ
     */
    public void setShadowRadius(int radius) {
        radius = Math.max(0, radius); // set zero if negative
        mWaveView.setShadowRadius(radius);
    }

    /**
     * This is an alias to WaveView#setWaveColor(int)
     *
     * @see WaveView#setWaveColor(int)
     */
    public void setWaveColor(int argbColor) {
        int alpha = 0xFF & (argbColor >> 24);
        int red = 0xFF & (argbColor >> 16);
        int blue = 0xFF & (argbColor >> 0);
        int green = 0xFF & (argbColor >> 8);
        setWaveARGBColor(alpha, red, green, blue);
    }

    /**
     * WaveView is colored by given rgb color + 0xFF000000
     *
     * @param r int [0, 0xFF]
     * @param g int [0, 0xFF]
     * @param b int [0, 0xFF]
     */
    public void setWaveRGBColor(int r, int g, int b) {
        mWaveView.setWaveColor(Color.argb(0xFF, r, g, b));
    }

    /**
     * This is an alias to WaveView#setWaveARGBColor(int)
     *
     * @param a int [0, 0xFF]
     * @param r int [0, 0xFF]
     * @param g int [0, 0xFF]
     * @param b int [0, 0xFF]
     * @see WaveView#setWaveARGBColor(int, int, int, int)
     */
    public void setWaveARGBColor(int a, int r, int g, int b) {
        setWaveRGBColor(r, g, b);
        if (a == 0xFF) {
            return;
        }
        mWaveView.setAlpha((float) a / 255f);
    }

    private static int makeMeasureSpecExactly(int length) {
        return MeasureSpec.makeMeasureSpec(length, MeasureSpec.EXACTLY);
    }


    /**
     * Custom view has progress drawable.
     *
     * @author jmatsu
     */
    private class ProgressAnimationImageView extends AnimationImageView {
        private final MaterialProgressDrawable mProgress;

        /**
         * Constructor
         * {@inheritDoc}
         */
        public ProgressAnimationImageView(Context context) {
            super(context);
            mProgress = new MaterialProgressDrawable(context, WaveRefreshLayout.this);
            initialize();
        }

        private void initialize() {
            setImageDrawable(null);

            mProgress.setBackgroundColor(Color.TRANSPARENT);

            setImageDrawable(mProgress);
            setVisibility(View.GONE);
        }

        public void measure() {
            final int circleDiameter = mProgress.getIntrinsicWidth();

            measure(makeMeasureSpecExactly(circleDiameter), makeMeasureSpecExactly(circleDiameter));
        }

        public void makeProgressTransparent() {
            mProgress.setAlpha(0xff);
        }

        public void showArrow(boolean show) {
            mProgress.showArrow(show);
        }

        public void setArrowScale(float scale) {
            mProgress.setArrowScale(scale);
        }

        public void setProgressAlpha(int alpha) {
            mProgress.setAlpha(alpha);
        }

        public void setProgressStartEndTrim(float startAngle, float endAngle) {
            mProgress.setStartEndTrim(startAngle, endAngle);
        }

        public void setProgressRotation(float rotation) {
            mProgress.setProgressRotation(rotation);
        }

        public void startProgress() {
            mProgress.start();
        }

        public void stopProgress() {
            mProgress.stop();
        }

        public void setProgressColorSchemeColors(@NonNull int... colors) {
            mProgress.setColorSchemeColors(colors);
        }

        public void setProgressColorSchemeColorsFromResource(@IdRes int... resources) {
            final Resources res = getResources();
            final int[] colorRes = new int[resources.length];

            for (int i = 0; i < resources.length; i++) {
                colorRes[i] = res.getColor(resources[i]);
            }

            setColorSchemeColors(colorRes);
        }

        public void scaleWithKeepingAspectRatio(float scale) {
            ViewCompat.setScaleX(this, scale);
            ViewCompat.setScaleY(this, scale);
        }
    }


    // 初始状态
    public static final int INIT = 0;
    // 正在刷新
    public static final int REFRESHING = 2;
    // 释放加载
    public static final int RELEASE_TO_LOAD = 3;
    // 正在加载
    public static final int LOADING = 4;
    // 操作完毕
    public static final int DONE = 5;
    // 当前状态
    private int state = INIT;
    // 刷新回调接口
    private OnRefreshListener mListener;
    // 刷新成功
    public static final int SUCCEED = 0;
    // 刷新失败
    public static final int FAIL = 1;
    // 按下Y坐标，上一个事件点Y坐标
    private float downY, lastY;
    // 上拉的距离
    private float pullUpY = 0;
    // 释放加载的距离
    private float loadmoreDist = 200;

    private MyTimer timer;
    // 回滚速度
    public float MOVE_SPEED = 8;
    // 在刷新过程中滑动操作
    private boolean isTouch = false;
    // 手指滑动距离与下拉头的滑动距离比，中间会随正切函数变化
    private float radio = 2;

    // 下拉箭头的转180°动画
    private RotateAnimation rotateAnimation;
    // 均匀旋转动画
    private RotateAnimation refreshingAnimation;

    // 上拉头
    private View loadmoreView;
    // 上拉的箭头
    private View pullUpView;
    // 正在加载的图标
    private View loadingView;
    // 加载结果图标
    private View loadStateImageView;
    // 加载结果：成功或失败
    private TextView loadStateTextView;

    private View contentView;
    // 实现了Pullable接口的View
    private View pullableView;
    // 过滤多点触碰
    private int mEvents;
    // 这两个变量用来控制pull的方向，如果不加控制，当情况满足可上拉又可下拉时没法下拉
    private boolean canPullUp = true;
    // 这两个变量用来控制是否需要上拉或下拉
    private boolean enabledPullUp = true;

    /**
     * 执行自动回滚的handler
     */
    Handler updateHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            // 回弹速度随下拉距离moveDeltaY增大而增大
            MOVE_SPEED = (float) (8 + 5 * Math.tan(Math.PI / 2
                    / getMeasuredHeight() * Math.abs(pullUpY)));
            if (!isTouch) {
                // 正在刷新，且没有往上推的话则悬停，显示"正在刷新..."
                if (state == LOADING && -pullUpY <= loadmoreDist) {
                    pullUpY = -loadmoreDist;
                    timer.cancel();
                }
            }
            if (pullUpY < 0)
                pullUpY += MOVE_SPEED;
            if (pullUpY > 0) {
                // 已完成回弹
                pullUpY = 0;
                pullUpView.clearAnimation();
                // 隐藏上拉头时有可能还在刷新，只有当前状态不是正在刷新时才改变状态
                if (state != REFRESHING && state != LOADING)
                    changeState(INIT);
                timer.cancel();
                requestLayout();
            }
            Log.d("handle", "handle");
            // 刷新布局,会自动调用onLayout
            requestLayout();
            // 没有拖拉或者回弹完成
            if (Math.abs(pullUpY) == 0)
                timer.cancel();
        }

    };

    /**
     * 初始化动画和计时器
     */
    private void initView(Context context) {
        timer = new MyTimer(updateHandler);
        rotateAnimation = (RotateAnimation) AnimationUtils.loadAnimation(
                context, R.anim.reverse_anim);
        refreshingAnimation = (RotateAnimation) AnimationUtils.loadAnimation(
                context, R.anim.rotating);
        // 添加匀速转动动画
        LinearInterpolator lir = new LinearInterpolator();
        rotateAnimation.setInterpolator(lir);
        refreshingAnimation.setInterpolator(lir);
    }

    private void hide() {
        timer.schedule(5);
    }


    private boolean isLoadingMore=false;

    /**
     * 是否正在加载更多
     */
    public boolean isLoadingMore(){
        return isLoadingMore;
    }

    /**
     * 加载完毕，显示加载结果。注意：加载完成后一定要调用这个方法
     *
     * @param refreshResult PullToRefreshLayout.SUCCEED代表成功，PullToRefreshLayout.FAIL代表失败
     */
    public void loadmoreFinish(int refreshResult) {
        isLoadingMore=false;
//        loadingView.clearAnimation();
        loadingView.setVisibility(View.GONE);
        switch (refreshResult) {
            case SUCCEED:
                // 加载成功
                loadStateImageView.setVisibility(View.VISIBLE);
                loadStateTextView.setText("加载成功");
                loadStateImageView.setBackgroundResource(R.mipmap.load_succeed);
                break;
            case FAIL:
            default:
                // 加载失败
                loadStateImageView.setVisibility(View.VISIBLE);
                loadStateTextView.setText("加载失败");
                loadStateImageView.setBackgroundResource(R.mipmap.load_failed);
                break;
        }
        if (pullUpY < 0) {
            // 刷新结果停留1秒
            new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    changeState(DONE);
                    hide();
                }
//            }.sendEmptyMessage(0);
			}.sendEmptyMessageDelayed(0, 1000);
        } else {
            changeState(DONE);
            hide();
        }
    }

    private void changeState(int to) {
        state = to;
        switch (state) {
            case INIT:
                // 上拉布局初始状态
                loadStateImageView.setVisibility(View.GONE);
                loadStateTextView.setText("上拉加载更多");
                pullUpView.clearAnimation();
                pullUpView.setVisibility(View.VISIBLE);
                break;
            case RELEASE_TO_LOAD:
                // 释放加载状态
                loadStateTextView.setText("释放立即加载");
                pullUpView.startAnimation(rotateAnimation);
                break;
            case LOADING:
                // 正在加载状态
                pullUpView.clearAnimation();
                loadingView.setVisibility(View.VISIBLE);
                pullUpView.setVisibility(View.INVISIBLE);
//                loadingView.startAnimation(refreshingAnimation);
                isLoadingMore=true;
                loadStateTextView.setText("正在加载...");
                break;
            case DONE:
                // 刷新或加载完毕，啥都不做
                break;
        }
    }

    /**
     * 不限制上拉或下拉
     */
    private void releasePull() {
        canPullUp = true;
    }

    /*
     * （非 Javadoc）由父控件决定是否分发事件，防止事件冲突
     *
     * @see android.view.ViewGroup#dispatchTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = ev.getY();
                lastY = downY;
                timer.cancel();
                mEvents = 0;
                releasePull();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                // 过滤多点触碰
                mEvents = -1;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mEvents == 0) {
                    Log.d("test", "dispatchTouchEvent: " + pullUpY);
                    if (pullUpY < 0 || (((Pullable) pullableView).canPullUp() && canPullUp && enabledPullUp && state != REFRESHING)) {
                        // 可以上拉，正在刷新时不能上拉
                        pullUpY = pullUpY + (ev.getY() - lastY) / radio;
                        if (pullUpY > 0) {
                            pullUpY = 0;
                            canPullUp = false;
                        }
                        if (pullUpY < -getMeasuredHeight())
                            pullUpY = -getMeasuredHeight();
                        if (state == LOADING) {
                            // 正在加载的时候触摸移动
                            isTouch = true;
                        }
                    } else
                        releasePull();
                } else
                    mEvents = 0;
                lastY = ev.getY();
                // 根据下拉距离改变比例
                radio = (float) (2 + 2 * Math.tan(Math.PI / 2 / getMeasuredHeight()
                        * Math.abs(pullUpY)));
                if (pullUpY < 0)
                    requestLayout();
                if (pullUpY < 0) {
                    // 下面是判断上拉加载的
                    if (-pullUpY <= loadmoreDist
                            && (state == RELEASE_TO_LOAD || state == DONE)) {
                        changeState(INIT);
                    }
                    // 上拉操作
                    if (-pullUpY >= loadmoreDist && state == INIT) {
                        changeState(RELEASE_TO_LOAD);
                    }

                }
                // 因为刷新和加载操作不能同时进行，所以pullDownY和pullUpY不会同时不为0，因此这里用(pullDownY +
                // Math.abs(pullUpY))就可以不对当前状态作区分了
                if (Math.abs(pullUpY) > 8) {
                    // 防止下拉过程中误触发长按事件和点击事件
                    ev.setAction(MotionEvent.ACTION_CANCEL);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (-pullUpY > loadmoreDist)
                // 正在刷新时往下拉（正在加载时往上拉），释放后下拉头（上拉头）不隐藏
                {
                    isTouch = false;
                }
                if (state == RELEASE_TO_LOAD) {
                    changeState(LOADING);
                    // 加载操作
                    if (mListener != null)
                        mListener.onLoadMore(this);
                }
                hide();
            default:
                break;
        }
        // 事件分发交给父类
        super.dispatchTouchEvent(ev);
        return true;
    }

    /**
     * 自动加载
     */
    public void autoLoad() {
        pullUpY = -loadmoreDist;
        requestLayout();
        changeState(LOADING);
        // 加载操作
        if (mListener != null)
            mListener.onLoadMore(this);
    }

    /**
     * 初始化上拉布局
     */
    private void initLoadView() {
        pullUpView = loadmoreView.findViewById(R.id.pullup_icon);
        loadStateTextView = (TextView) loadmoreView.findViewById(R.id.loadstate_tv);
        loadingView = loadmoreView.findViewById(R.id.loading_icon);
        loadStateImageView = loadmoreView.findViewById(R.id.loadstate_iv);
    }


    class MyTimer {
        private Handler handler;
        private Timer timer;
        private MyTask mTask;

        public MyTimer(Handler handler) {
            this.handler = handler;
            timer = new Timer();
        }

        public void schedule(long period) {
            if (mTask != null) {
                mTask.cancel();
                mTask = null;
            }
            mTask = new MyTask(handler);
            timer.schedule(mTask, 0, period);
        }

        public void cancel() {
            if (mTask != null) {
                mTask.cancel();
                mTask = null;
            }
        }

        class MyTask extends TimerTask {
            private Handler handler;

            public MyTask(Handler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                handler.obtainMessage().sendToTarget();
            }

        }
    }

    /**
     * 刷新加载回调接口
     *
     * @author chenjing
     */
    public interface OnRefreshListener {
        /**
         * 刷新操作
         */
        void onRefresh(WaveRefreshLayout mWaveRefreshLayout);

        /**
         * 加载操作
         */
        void onLoadMore(WaveRefreshLayout mWaveRefreshLayout);
    }
}
