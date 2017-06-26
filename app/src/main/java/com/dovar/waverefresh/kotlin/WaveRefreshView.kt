package com.dovar.waverefresh.kotlin

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Message
import android.support.annotation.IdRes
import android.support.v4.view.MotionEventCompat
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.animation.*
import android.widget.TextView
import com.dovar.waverefresh.R
import java.util.*

/**
 * Created by heweizong on 2017/6/26.
 */
class WaveRefreshView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : ViewGroup(context, attrs, defStyleAttr), ViewTreeObserver.OnPreDrawListener {
    private enum class VERTICAL_DRAG_THRESHOLD constructor(internal val `val`: Float) {
        FIRST(0.1f), SECOND(0.16f + FIRST.`val`), THIRD(0.5f + FIRST.`val`)
    }

    private enum class STATE {
        REFRESHING, PENDING
    }

    private enum class EVENT_PHASE {
        WAITING, BEGINNING, APPEARING, EXPANDING, DROPPING
    }

    private val DECELERATE_INTERPOLATION_FACTOR = 2f

    private val INVALID_POINTER = -1

    private val DRAGGING_WEIGHT = 0.5f

    /**
     * 落ちる前の回転の最大のAngle値
     */
    private val MAX_PROGRESS_ROTATION_RATE = 0.8f


    private val SCALE_DOWN_DURATION = 200


    private val ANIMATE_TO_TRIGGER_DURATION = 200

    /**
     * デフォルトのCircleのTargetの値
     */
    private val DEFAULT_CIRCLE_TARGET = 64

    /**
     * 状态
     */
    private var mState = STATE.PENDING

    private var mEventPhase = EVENT_PHASE.WAITING


    lateinit private var mCircleView: ProgressAnimationImageView

    /**
     * 水滴View
     */
    lateinit private var mWaveView: WaveView

    private var mNotify: Boolean = false

    private var mIsManualRefresh = false

    private var mFirstTouchDownPointY: Float = 0.toFloat()

    private var mIsBeingDropped: Boolean = false

    private var mActivePointerId = INVALID_POINTER

    private var mTopOffset: Int = 0

    /**
     * 检查上拉加载功能是否开启
     */
    fun isEnabledPullUp(): Boolean {
        return enabledPullUp
    }

    /**
     * 开关上拉加载功能
     */
    fun setEnabledPullUp(enabledPullUp: Boolean) {
        this.enabledPullUp = enabledPullUp
    }

    fun setOnRefreshListener(listener: OnRefreshListener) {
        mListener = listener
    }

    fun setTopOffsetOfWave(topOffset: Int) {
        if (topOffset < 0) {
            return
        }
        mTopOffset = topOffset
        layoutWaveView()
    }


    init {
        viewTreeObserver.addOnPreDrawListener(this)

        //        setWillNotDraw(false);
        //        ViewCompat.setChildrenDrawingOrderEnabled(this, true);

        createWaveView()
        createProgressView()
        createLoadMoreView(context)

        initView(context)
    }

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)


    private fun createLoadMoreView(context: Context) {
        loadmoreView = View.inflate(context, R.layout.load_more, null)
        addView(loadmoreView, 2)
        initLoadView()
    }

    private fun createProgressView() {
        mCircleView = ProgressAnimationImageView(context)
        addView(mCircleView, 1)
    }

    private fun createWaveView() {
        mWaveView = WaveView(context)
        addView(mWaveView, 0)
    }

    /**
     * 需要在重写的onMeasure方法中测量子控件之后在onLayout（）才能获取到有效宽高
     * 或者继承viewGroup改成relativelayout等子类亦是同理
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        Log.d("tests", "onMeasure: ")
        ensureTarget()
        //
        //        //// Propagates measuring to child view and to circle view
        //
        //        // Measures on child view with each directed length without padding
        contentView!!.measure(
                makeMeasureSpecExactly(measuredWidth - (paddingLeft + paddingRight)),
                makeMeasureSpecExactly(measuredHeight - (paddingTop + paddingBottom)))
        mWaveView.measure(widthMeasureSpec, heightMeasureSpec)
        mWaveView.setMaxDropHeight(measuredHeight / 2)

        mCircleView.measure()
        loadmoreView!!.measure(widthMeasureSpec, heightMeasureSpec)
        loadmoreDist = (loadmoreView as ViewGroup).getChildAt(0).measuredHeight.toFloat()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (childCount == 0) {
            return
        }

        ensureTarget()
        contentView!!.layout(paddingLeft, paddingTop + pullUpY.toInt(), contentView!!.measuredWidth, contentView!!.measuredHeight + pullUpY.toInt())

        layoutWaveView()

        loadmoreView!!.layout(0,
                pullUpY.toInt() + contentView!!.measuredHeight + paddingTop,
                loadmoreView!!.measuredWidth,
                pullUpY.toInt() + contentView!!.measuredHeight + loadmoreView!!.measuredHeight + paddingTop)
    }


    private fun layoutWaveView() {
        val thisWidth = measuredWidth
        val thisHeight = measuredHeight

        val circleWidth = mCircleView.measuredWidth
        val circleHeight = mCircleView.measuredHeight
        mCircleView.layout((thisWidth - circleWidth) / 2, -circleHeight + mTopOffset,
                (thisWidth + circleWidth) / 2, mTopOffset)
        val childRight = thisWidth - paddingRight
        val childBottom = thisHeight - paddingBottom
        mWaveView.layout(paddingLeft, mTopOffset + paddingTop, childRight, childBottom)
    }


    override fun onPreDraw(): Boolean {
        viewTreeObserver.removeOnPreDrawListener(this)
        mWaveView.bringToFront()
        mCircleView.bringToFront()
        if (mIsManualRefresh) {
            mIsManualRefresh = false
            mWaveView.manualRefresh()
            reInitCircleView()
            mCircleView.setBackgroundColor(Color.TRANSPARENT)
            mCircleView.translationY = mWaveView.currentCircleCenterY + mCircleView.height / 2
            animateOffsetToCorrectPosition()
        }
        return false
    }

    /**
     * Make circle view be visible and even scale.
     */
    private fun reInitCircleView() {
        if (mCircleView.visibility != View.VISIBLE) {
            mCircleView.visibility = View.VISIBLE
        }

        mCircleView.scaleWithKeepingAspectRatio(1f)
        mCircleView.makeProgressTransparent()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        ensureTarget()

        if (!isEnabled || canChildScrollUp() || isRefreshing()) {
            return false
        }

        val action = MotionEventCompat.getActionMasked(event)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mActivePointerId = MotionEventCompat.getPointerId(event, 0)
                mFirstTouchDownPointY = getMotionEventY(event, mActivePointerId)
            }

            MotionEvent.ACTION_MOVE -> {
                if (mActivePointerId == INVALID_POINTER) {
                    return false
                }

                val currentY = getMotionEventY(event, mActivePointerId)

                if (currentY == -1f) {
                    return false
                }

                if (mFirstTouchDownPointY == -1f) {
                    mFirstTouchDownPointY = currentY
                }

                val yDiff = currentY - mFirstTouchDownPointY

                // State is changed to drag if over slop
                if (yDiff > ViewConfiguration.get(context).scaledTouchSlop && !isRefreshing()) {
                    mCircleView.makeProgressTransparent()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mActivePointerId = INVALID_POINTER
        }

        return false
    }

    private fun onMoveTouchEvent(event: MotionEvent, pointerIndex: Int): Boolean {
        if (mIsBeingDropped) {
            return false
        }

        val y = MotionEventCompat.getY(event, pointerIndex)
        val diffY = y - mFirstTouchDownPointY
        val overScrollTop = diffY * DRAGGING_WEIGHT

        if (overScrollTop < 0) {
            mCircleView.showArrow(false)
            return false
        }

        val metrics = resources.displayMetrics

        val originalDragPercent = overScrollTop / (DEFAULT_CIRCLE_TARGET * metrics.density)
        val dragPercent = Math.min(1f, originalDragPercent)
        val adjustedPercent = Math.max(dragPercent - .4, 0.0).toFloat() * 5 / 3

        // 0f...2f
        val tensionSlingshotPercent: Float = if (originalDragPercent > 3f) 2f else if (originalDragPercent > 1f) originalDragPercent - 1f else 0f
        val tensionPercent = (4f - tensionSlingshotPercent) * tensionSlingshotPercent / 8f

        mCircleView.showArrow(true)
        reInitCircleView()

        if (originalDragPercent < 1f) {
            val strokeStart = adjustedPercent * .8f
            mCircleView.setProgressStartEndTrim(0f, Math.min(MAX_PROGRESS_ROTATION_RATE, strokeStart))
            mCircleView.setArrowScale(Math.min(1f, adjustedPercent))
        }

        val rotation = (-0.25f + .4f * adjustedPercent + tensionPercent * 2) * .5f
        mCircleView.setProgressRotation(rotation)
        mCircleView.translationY = mWaveView.currentCircleCenterY

        val seed = diffY / Math.min(measuredWidth, measuredHeight)
        val firstBounds = seed * (5f - 2 * seed) / 3.5f
        val secondBounds = firstBounds - VERTICAL_DRAG_THRESHOLD.FIRST.`val`
        val finalBounds = (firstBounds - VERTICAL_DRAG_THRESHOLD.SECOND.`val`) / 5

        if (firstBounds < VERTICAL_DRAG_THRESHOLD.FIRST.`val`) {
            // draw a wave and not draw a circle
            onBeginPhase(firstBounds)
        } else if (firstBounds < VERTICAL_DRAG_THRESHOLD.SECOND.`val`) {
            // draw a circle with a wave
            onAppearPhase(firstBounds, secondBounds)
        } else if (firstBounds < VERTICAL_DRAG_THRESHOLD.THIRD.`val`) {
            // draw a circle with expanding a wave
            onExpandPhase(firstBounds, secondBounds, finalBounds)
        } else {
            // stop to draw a wave and drop a circle
            onDropPhase()
        }

        return !mIsBeingDropped
    }

    private fun onBeginPhase(move1: Float) {
        //最初の小波の描画
        mWaveView.beginPhase(move1)

        setEventPhase(EVENT_PHASE.BEGINNING)
    }

    private fun onAppearPhase(move1: Float, move2: Float) {
        //すでに描画されている波に対して追加で円を描画する
        mWaveView.appearPhase(move1, move2)

        setEventPhase(EVENT_PHASE.APPEARING)
    }

    private fun onExpandPhase(move1: Float, move2: Float, move3: Float) {
        mWaveView.expandPhase(move1, move2, move3)

        setEventPhase(EVENT_PHASE.EXPANDING)
    }

    private fun onDropPhase() {
        mWaveView.animationDropCircle()

        val animator = ValueAnimator.ofFloat(0f, 0f)
        animator.duration = 500
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener {
            mCircleView.translationY = mWaveView.currentCircleCenterY + mCircleView.height / 2f
        }
        animator.start()
        setRefreshing(true, true)
        mIsBeingDropped = true
        setEventPhase(EVENT_PHASE.DROPPING)
        isEnabled = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        if (!isEnabled() || canChildScrollUp()) {
            return false
        }
        mIsBeingDropped = mWaveView!!.isDisappearCircleAnimatorRunning

        val action = MotionEventCompat.getActionMasked(event)
        when (action) {
            MotionEvent.ACTION_DOWN -> {
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = MotionEventCompat.findPointerIndex(event, mActivePointerId)
                return pointerIndex >= 0 && onMoveTouchEvent(event, pointerIndex)
            }

            MotionEvent.ACTION_UP -> {
                if (mIsBeingDropped) {
                    mIsBeingDropped = false
                    return false
                }

                val diffY = event.y - mFirstTouchDownPointY
                val waveHeightThreshold = diffY * (5f - 2 * diffY / Math.min(getMeasuredWidth(), getMeasuredHeight())) / 1000f
                mWaveView!!.startWaveAnimation(waveHeightThreshold)
                if (mActivePointerId == INVALID_POINTER) {
                    return false
                }

                if (!isRefreshing()) {
                    mCircleView!!.setProgressStartEndTrim(0f, 0f)
                    mCircleView!!.showArrow(false)
                    mCircleView!!.setVisibility(View.GONE)
                }
                mActivePointerId = INVALID_POINTER
                return false
            }

            MotionEvent.ACTION_CANCEL -> {
                if (mActivePointerId == INVALID_POINTER) {
                    return false
                }
                if (!isRefreshing()) {
                    mCircleView!!.setProgressStartEndTrim(0f, 0f)
                    mCircleView!!.showArrow(false)
                    mCircleView!!.setVisibility(View.GONE)
                }
                mActivePointerId = INVALID_POINTER
                return false
            }
        }// Here is not called from anywhere
        return true
    }

    private fun getMotionEventY(ev: MotionEvent, activePointerId: Int): Float {
        val index = MotionEventCompat.findPointerIndex(ev, activePointerId)
        if (index < 0) {
            return -1f
        }
        return MotionEventCompat.getY(ev, index)
    }

    private fun animateOffsetToCorrectPosition() {
        mAnimateToCorrectPosition.reset()
        mAnimateToCorrectPosition.duration = ANIMATE_TO_TRIGGER_DURATION.toLong()
        mAnimateToCorrectPosition.interpolator = DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR)
        mCircleView!!.setAnimationListener(mRefreshListener)
        mCircleView!!.clearAnimation()
        mCircleView!!.startAnimation(mAnimateToCorrectPosition)
    }

    private val mAnimateToCorrectPosition = object : Animation() {
        public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {}
    }

    /**
     * @param dropHeight 下降高度
     */
    fun setMaxDropHeight(dropHeight: Int) {
        mWaveView!!.setMaxDropHeight(dropHeight)
    }

    private val mRefreshListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {}

        override fun onAnimationRepeat(animation: Animation) {}

        override fun onAnimationEnd(animation: Animation) {
            if (isRefreshing()) {
                mCircleView!!.makeProgressTransparent()
                mCircleView!!.startProgress()
                if (mNotify) {
                    if (mListener != null) {
                        mListener!!.onRefresh(this@WaveRefreshView)
                    }
                }
            } else {
                mCircleView!!.stopProgress()
                mCircleView!!.setVisibility(View.GONE)
                mCircleView!!.makeProgressTransparent()
                mWaveView!!.startDisappearCircleAnimation()
            }
        }
    }

    private fun ensureTarget() {
        if (contentView == null) {
            for (i in 0..getChildCount() - 1) {
                val child = getChildAt(i)
                if (child != mCircleView && child != mWaveView && child != loadmoreView) {
                    contentView = child
                    pullableView = contentView!!.findViewById(R.id.pullable)

                    break
                }
            }
        }

        if (contentView == null) {
            throw IllegalStateException("This view must have at least one AbsListView")
        }
    }

    /**
     * @param refreshing Refreshの状態
     * *
     * @param notify     Listenerに通知するかどうか
     */
    private fun setRefreshing(refreshing: Boolean, notify: Boolean) {
        if (isRefreshing() != refreshing) {
            mNotify = notify
            ensureTarget()
            setState(refreshing)
            if (isRefreshing()) {
                animateOffsetToCorrectPosition()
            } else {
                startScaleDownAnimation(mRefreshListener)
            }
        }
    }

    private fun setEventPhase(eventPhase: EVENT_PHASE) {
        mEventPhase = eventPhase
    }

    private fun setState(state: STATE) {
        mState = state
        setEnabled(true)
        if (!isRefreshing()) {
            setEventPhase(EVENT_PHASE.WAITING)
        }
    }

    private fun setState(doRefresh: Boolean) {
        setState(if (doRefresh) STATE.REFRESHING else STATE.PENDING)
    }

    /**
     * @param listener [Animation.AnimationListener]
     */
    private fun startScaleDownAnimation(listener: Animation.AnimationListener) {
        val scaleDownAnimation = object : Animation() {
            public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                mCircleView!!.scaleWithKeepingAspectRatio(1 - interpolatedTime)
            }
        }

        scaleDownAnimation.duration = SCALE_DOWN_DURATION.toLong()
        mCircleView!!.setAnimationListener(listener)
        mCircleView!!.clearAnimation()
        mCircleView!!.startAnimation(scaleDownAnimation)
    }

    /**
     * @param colorResIds ColorのId達
     */
    fun setColorSchemeResources(@IdRes vararg colorResIds: Int) {
        mCircleView!!.setProgressColorSchemeColorsFromResource(*colorResIds)
    }

    /**
     * @param colors セットするColor達
     */
    fun setColorSchemeColors(vararg colors: Int) {
        // FIXME Add @NonNull to the argument
        ensureTarget()
        mCircleView!!.setProgressColorSchemeColors(*colors)
    }

    /**
     * @return {mState} == REFRESHING
     */
    fun isRefreshing(): Boolean {
        return mState == STATE.REFRESHING
    }

    private fun isBeginning(): Boolean {
        return mEventPhase == EVENT_PHASE.BEGINNING
    }

    private fun isExpanding(): Boolean {
        return mEventPhase == EVENT_PHASE.EXPANDING
    }

    private fun isDropping(): Boolean {
        return mEventPhase == EVENT_PHASE.DROPPING
    }

    private fun isAppearing(): Boolean {
        return mEventPhase == EVENT_PHASE.APPEARING
    }

    private fun isWaiting(): Boolean {
        return mEventPhase == EVENT_PHASE.WAITING
    }

    /**
     * @param refreshing
     */
    fun setRefreshing(refreshing: Boolean) {
        if (refreshing && !isRefreshing()) {
            // scale and show
            setState(true)
            mNotify = false

            mIsManualRefresh = true
            if (mWaveView!!.currentCircleCenterY == 0f) {
                return
            }
            mWaveView!!.manualRefresh()
            reInitCircleView()
            mCircleView!!.translationY = mWaveView!!.currentCircleCenterY + mCircleView!!.getHeight() / 2
            animateOffsetToCorrectPosition()
        } else {
            setRefreshing(refreshing, false /* notify */)
        }
    }

    /**
     * @return ScrollUp出来るかどうか
     */
    fun canChildScrollUp(): Boolean {
        if (pullableView == null) {
            return false
        }
        return ViewCompat.canScrollVertically(pullableView!!, -1)
    }

    /**
     * @param radius 波の影の深さ
     */
    fun setShadowRadius(radius: Int) {
        var radius = radius
        radius = Math.max(0, radius) // set zero if negative
        mWaveView!!.setShadowRadius(radius)
    }

    /**
     * This is an alias to WaveView#setWaveColor(int)

     * @see WaveView.setWaveColor
     */
    fun setWaveColor(argbColor: Int) {
        val alpha = 0xFF and (argbColor shr 24)
        val red = 0xFF and (argbColor shr 16)
        val blue = 0xFF and (argbColor shr 0)
        val green = 0xFF and (argbColor shr 8)
        setWaveARGBColor(alpha, red, green, blue)
    }

    /**
     * WaveView is colored by given rgb color + 0xFF000000

     * @param r int [0, 0xFF]
     * *
     * @param g int [0, 0xFF]
     * *
     * @param b int [0, 0xFF]
     */
    fun setWaveRGBColor(r: Int, g: Int, b: Int) {
        mWaveView!!.setWaveColor(Color.argb(0xFF, r, g, b))
    }

    /**
     * This is an alias to WaveView#setWaveARGBColor(int)

     * @param a int [0, 0xFF]
     * *
     * @param r int [0, 0xFF]
     * *
     * @param g int [0, 0xFF]
     * *
     * @param b int [0, 0xFF]
     * *
     * @see WaveView.setWaveARGBColor
     */
    fun setWaveARGBColor(a: Int, r: Int, g: Int, b: Int) {
        setWaveRGBColor(r, g, b)
        if (a == 0xFF) {
            return
        }
        mWaveView!!.alpha = a.toFloat() / 255f
    }

    private fun makeMeasureSpecExactly(length: Int): Int {
        return View.MeasureSpec.makeMeasureSpec(length, View.MeasureSpec.EXACTLY)
    }


    /**
     * Custom view has progress drawable.

     * @author jmatsu
     */
    private inner class ProgressAnimationImageView
    /**
     * Constructor
     * {@inheritDoc}
     */
    (context: Context) : AnimationImageView(context) {
        private val mProgress: MaterialProgressDrawable

        init {
            mProgress = MaterialProgressDrawable(context, this)
            initialize()
        }

        private fun initialize() {
            setImageDrawable(null)

            mProgress.setBackgroundColor(Color.TRANSPARENT)

            setImageDrawable(mProgress)
            setVisibility(View.GONE)
        }

        fun measure() {
            val circleDiameter = mProgress.intrinsicWidth

            measure(makeMeasureSpecExactly(circleDiameter), makeMeasureSpecExactly(circleDiameter))
        }

        fun makeProgressTransparent() {
            mProgress.alpha = 0xff
        }

        fun showArrow(show: Boolean) {
            mProgress.showArrow(show)
        }

        fun setArrowScale(scale: Float) {
            mProgress.setArrowScale(scale)
        }

        fun setProgressAlpha(alpha: Int) {
            mProgress.alpha = alpha
        }

        fun setProgressStartEndTrim(startAngle: Float, endAngle: Float) {
            mProgress.setStartEndTrim(startAngle, endAngle)
        }

        fun setProgressRotation(rotation: Float) {
            mProgress.setProgressRotation(rotation)
        }

        fun startProgress() {
            mProgress.start()
        }

        fun stopProgress() {
            mProgress.stop()
        }

        fun setProgressColorSchemeColors(vararg colors: Int) {
            mProgress.setColorSchemeColors(*colors)
        }

        fun setProgressColorSchemeColorsFromResource(@IdRes vararg resources: Int) {
            val res = getResources()
            val colorRes = IntArray(resources.size)

            for (i in resources.indices) {
                colorRes[i] = res.getColor(resources[i])
            }

            setColorSchemeColors(*colorRes)
        }

        fun scaleWithKeepingAspectRatio(scale: Float) {
            ViewCompat.setScaleX(this, scale)
            ViewCompat.setScaleY(this, scale)
        }
    }


    // 初始状态
    val INIT = 0
    // 正在刷新
    val REFRESHING = 2
    // 释放加载
    val RELEASE_TO_LOAD = 3
    // 正在加载
    val LOADING = 4
    // 操作完毕
    val DONE = 5
    // 当前状态
    private var state = INIT
    // 刷新回调接口
    private var mListener: OnRefreshListener? = null
    // 刷新成功
    val SUCCEED = 0
    // 刷新失败
    val FAIL = 1
    // 按下Y坐标，上一个事件点Y坐标
    private var downY: Float = 0.toFloat()
    private var lastY: Float = 0.toFloat()
    // 上拉的距离
    private var pullUpY = 0f
    // 释放加载的距离
    private var loadmoreDist = 200f

    lateinit private var timer: MyTimer
    // 回滚速度
    var MOVE_SPEED = 8f
    // 在刷新过程中滑动操作
    private var isTouch = false
    // 手指滑动距离与下拉头的滑动距离比，中间会随正切函数变化
    private var radio = 2f

    // 下拉箭头的转180°动画
    private var rotateAnimation: RotateAnimation? = null
    // 均匀旋转动画
    private var refreshingAnimation: RotateAnimation? = null

    // 上拉头
    private var loadmoreView: View? = null
    // 上拉的箭头
    private var pullUpView: View? = null
    // 正在加载的图标
    private var loadingView: View? = null
    // 加载结果图标
    private var loadStateImageView: View? = null
    // 加载结果：成功或失败
    private var loadStateTextView: TextView? = null

    private var contentView: View? = null
    // 实现了Pullable接口的View
    private var pullableView: View? = null
    // 过滤多点触碰
    private var mEvents: Int = 0
    // 这两个变量用来控制pull的方向，如果不加控制，当情况满足可上拉又可下拉时没法下拉
    private var canPullUp = true
    // 这两个变量用来控制是否需要上拉或下拉
    private var enabledPullUp = true

    /**
     * 执行自动回滚的handler
     */
     var updateHandler= Handler(Handler.Callback {
        // 回弹速度随下拉距离moveDeltaY增大而增大
        MOVE_SPEED = (8 + 5 * Math.tan(Math.PI / 2.0 / measuredHeight.toDouble() * Math.abs(pullUpY))).toFloat()
        if (!isTouch) {
            // 正在刷新，且没有往上推的话则悬停，显示"正在刷新..."
            if (state == LOADING && -pullUpY <= loadmoreDist) {
                pullUpY = -loadmoreDist
                timer.cancel()
            }
        }
        if (pullUpY < 0)
            pullUpY += MOVE_SPEED
        if (pullUpY > 0) {
            // 已完成回弹
            pullUpY = 0f
            pullUpView!!.clearAnimation()
            // 隐藏上拉头时有可能还在刷新，只有当前状态不是正在刷新时才改变状态
            if (state != REFRESHING && state != LOADING)
                changeState(INIT)
            timer.cancel()
            requestLayout()
        }
        // 刷新布局,会自动调用onLayout
        requestLayout()
        // 没有拖拉或者回弹完成
        if (Math.abs(pullUpY) == 0f)
            timer.cancel()

        true
    })

//        override fun handleMessage(msg: Message) {
//            // 回弹速度随下拉距离moveDeltaY增大而增大
//            MOVE_SPEED = (8 + 5 * Math.tan(Math.PI / 2.0 / measuredHeight.toDouble() * Math.abs(pullUpY))).toFloat()
//            if (!isTouch) {
//                // 正在刷新，且没有往上推的话则悬停，显示"正在刷新..."
//                if (state == LOADING && -pullUpY <= loadmoreDist) {
//                    pullUpY = -loadmoreDist
//                    timer.cancel()
//                }
//            }
//            if (pullUpY < 0)
//                pullUpY += MOVE_SPEED
//            if (pullUpY > 0) {
//                // 已完成回弹
//                pullUpY = 0f
//                pullUpView!!.clearAnimation()
//                // 隐藏上拉头时有可能还在刷新，只有当前状态不是正在刷新时才改变状态
//                if (state != REFRESHING && state != LOADING)
//                    changeState(INIT)
//                timer.cancel()
//                requestLayout()
//            }
//            // 刷新布局,会自动调用onLayout
//            requestLayout()
//            // 没有拖拉或者回弹完成
//            if (Math.abs(pullUpY) == 0f)
//                timer.cancel()
//        }


    /**
     * 初始化动画和计时器
     */
    private fun initView(context: Context) {
        if (updateHandler==null){
            updateHandler= Handler(Handler.Callback {
                // 回弹速度随下拉距离moveDeltaY增大而增大
                MOVE_SPEED = (8 + 5 * Math.tan(Math.PI / 2.0 / measuredHeight.toDouble() * Math.abs(pullUpY))).toFloat()
                if (!isTouch) {
                    // 正在刷新，且没有往上推的话则悬停，显示"正在刷新..."
                    if (state == LOADING && -pullUpY <= loadmoreDist) {
                        pullUpY = -loadmoreDist
                        timer.cancel()
                    }
                }
                if (pullUpY < 0)
                    pullUpY += MOVE_SPEED
                if (pullUpY > 0) {
                    // 已完成回弹
                    pullUpY = 0f
                    pullUpView!!.clearAnimation()
                    // 隐藏上拉头时有可能还在刷新，只有当前状态不是正在刷新时才改变状态
                    if (state != REFRESHING && state != LOADING)
                        changeState(INIT)
                    timer.cancel()
                    requestLayout()
                }
                // 刷新布局,会自动调用onLayout
                requestLayout()
                // 没有拖拉或者回弹完成
                if (Math.abs(pullUpY) == 0f)
                    timer.cancel()

                true
            })
        }
        timer = MyTimer(updateHandler)
        rotateAnimation = AnimationUtils.loadAnimation(
                context, R.anim.reverse_anim) as RotateAnimation
        refreshingAnimation = AnimationUtils.loadAnimation(
                context, R.anim.rotating) as RotateAnimation
        // 添加匀速转动动画
        val lir = LinearInterpolator()
        rotateAnimation!!.interpolator = lir
        refreshingAnimation!!.interpolator = lir
    }

    private fun hide() {
        timer!!.schedule(5)
    }


    private var isLoadingMore = false

    /**
     * 是否正在加载更多
     */
    fun isLoadingMore(): Boolean {
        return isLoadingMore
    }

    /**
     * 加载完毕，显示加载结果。注意：加载完成后一定要调用这个方法

     * @param refreshResult PullToRefreshLayout.SUCCEED代表成功，PullToRefreshLayout.FAIL代表失败
     */
    fun loadmoreFinish(refreshResult: Int) {
        isLoadingMore = false
        //        loadingView.clearAnimation();
        loadingView!!.visibility = View.GONE
        when (refreshResult) {
            SUCCEED -> {
                // 加载成功
                loadStateImageView!!.visibility = View.VISIBLE
                loadStateTextView!!.text = "加载成功"
                loadStateImageView!!.setBackgroundResource(R.mipmap.load_succeed)
            }
            FAIL -> {
            }
            else -> {
                // 加载失败
                loadStateImageView!!.visibility = View.VISIBLE
                loadStateTextView!!.text = "加载失败"
                loadStateImageView!!.setBackgroundResource(R.mipmap.load_failed)
            }
        }
        if (pullUpY < 0) {
            // 刷新结果停留1秒
            object : Handler() {
                override fun handleMessage(msg: Message) {
                    changeState(DONE)
                    hide()
                }
                //            }.sendEmptyMessage(0);
            }.sendEmptyMessageDelayed(0, 1000)
        } else {
            changeState(DONE)
            hide()
        }
    }

    private fun changeState(to: Int) {
        state = to
        when (state) {
            INIT -> {
                // 上拉布局初始状态
                loadStateImageView!!.visibility = View.GONE
                loadStateTextView!!.text = "上拉加载更多"
                pullUpView!!.clearAnimation()
                pullUpView!!.visibility = View.VISIBLE
            }
            RELEASE_TO_LOAD -> {
                // 释放加载状态
                loadStateTextView!!.text = "释放立即加载"
                pullUpView!!.startAnimation(rotateAnimation)
            }
            LOADING -> {
                // 正在加载状态
                pullUpView!!.clearAnimation()
                loadingView!!.visibility = View.VISIBLE
                pullUpView!!.visibility = View.INVISIBLE
                //                loadingView.startAnimation(refreshingAnimation);
                isLoadingMore = true
                loadStateTextView!!.text = "正在加载..."
            }
            DONE -> {
            }
        }// 刷新或加载完毕，啥都不做
    }

    /**
     * 不限制上拉或下拉
     */
    private fun releasePull() {
        canPullUp = true
    }

    /*
     * （非 Javadoc）由父控件决定是否分发事件，防止事件冲突
     *
     * @see android.view.ViewGroup#dispatchTouchEvent(android.view.MotionEvent)
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.y
                lastY = downY
                timer!!.cancel()
                mEvents = 0
                releasePull()
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP ->
                // 过滤多点触碰
                mEvents = -1
            MotionEvent.ACTION_MOVE -> {
                if (mEvents == 0) {
                    if (pullUpY < 0 || (pullableView as Pullable).canPullUp() && canPullUp && enabledPullUp && state != REFRESHING) {
                        // 可以上拉，正在刷新时不能上拉
                        pullUpY = pullUpY + (ev.y - lastY) / radio
                        if (pullUpY > 0) {
                            pullUpY = 0f
                            canPullUp = false
                        }
                        if (pullUpY < -measuredHeight)
                            pullUpY = (-measuredHeight).toFloat()
                        if (state == LOADING) {
                            // 正在加载的时候触摸移动
                            isTouch = true
                        }
                    } else
                        releasePull()
                } else
                    mEvents = 0
                lastY = ev.y
                // 根据下拉距离改变比例
                radio = (2 + 2 * Math.tan(Math.PI / 2.0 / measuredHeight.toDouble() * Math.abs(pullUpY))).toFloat()
                if (pullUpY < 0)
                    requestLayout()
                if (pullUpY < 0) {
                    // 下面是判断上拉加载的
                    if (-pullUpY <= loadmoreDist && (state == RELEASE_TO_LOAD || state == DONE)) {
                        changeState(INIT)
                    }
                    // 上拉操作
                    if (-pullUpY >= loadmoreDist && state == INIT) {
                        changeState(RELEASE_TO_LOAD)
                    }

                }
                // 因为刷新和加载操作不能同时进行，所以pullDownY和pullUpY不会同时不为0，因此这里用(pullDownY +
                // Math.abs(pullUpY))就可以不对当前状态作区分了
                if (Math.abs(pullUpY) > 8) {
                    // 防止下拉过程中误触发长按事件和点击事件
                    ev.action = MotionEvent.ACTION_CANCEL
                }
            }
            MotionEvent.ACTION_UP -> {
                if (-pullUpY > loadmoreDist)
                // 正在刷新时往下拉（正在加载时往上拉），释放后下拉头（上拉头）不隐藏
                {
                    isTouch = false
                }
                if (state == RELEASE_TO_LOAD) {
                    changeState(LOADING)
                    // 加载操作
                    if (mListener != null)
                        mListener!!.onLoadMore(this)
                }
                hide()
            }
            else -> {
            }
        }
        // 事件分发交给父类
        super.dispatchTouchEvent(ev)
        return true
    }

    /**
     * 自动加载
     */
    fun autoLoad() {
        pullUpY = -loadmoreDist
        requestLayout()
        changeState(LOADING)
        // 加载操作
        if (mListener != null)
            mListener!!.onLoadMore(this)
    }

    /**
     * 初始化上拉布局
     */
    private fun initLoadView() {
        pullUpView = loadmoreView!!.findViewById(R.id.pullup_icon)
        loadStateTextView = loadmoreView!!.findViewById(R.id.loadstate_tv) as TextView
        loadingView = loadmoreView!!.findViewById(R.id.loading_icon)
        loadStateImageView = loadmoreView!!.findViewById(R.id.loadstate_iv)
    }


    inner class MyTimer(private val handler: Handler) {
        private val timer: Timer = Timer()
        private var mTask: MyTask? = null

        fun schedule(period: Long) {
            if (mTask != null) {
                mTask!!.cancel()
                mTask = null
            }
            mTask = MyTask(handler)
            timer.schedule(mTask, 0, period)
        }

        fun cancel() {
            if (mTask != null) {
                mTask!!.cancel()
                mTask = null
            }
        }

        internal inner class MyTask(private val handler: Handler) : TimerTask() {

            override fun run() {
                handler.obtainMessage().sendToTarget()
            }

        }
    }

    /**
     * 刷新加载回调接口

     * @author chenjing
     */
    interface OnRefreshListener {
        /**
         * 刷新操作
         */
        fun onRefresh(mWaveRefreshView: WaveRefreshView)

        /**
         * 加载操作
         */
        fun onLoadMore(mWaveRefreshView: WaveRefreshView)
    }
}