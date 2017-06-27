package com.dovar.waverefresh.kotlin

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.support.v4.view.ViewCompat
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import com.dovar.waverefresh.wave.DropBounceInterpolator
import com.dovar.waverefresh.wave.WaveView

/**
 * Created by Administrator on 2017-06-24.
 */
class WaveView(context: Context) : View(context), ViewTreeObserver.OnPreDrawListener {
    init {
        viewTreeObserver.addOnPreDrawListener(this)
        initView()
    }

    private fun initView() {
        setUpPaint()
        resetAnimator()

        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    private fun setUpPaint() {
        mPaint= Paint()
        mPaint.color = 0xff2196F3.toInt()
        mPaint.isAntiAlias = true
        mPaint.style = Paint.Style.FILL

        mShadowPaint=Paint()
        mShadowPaint.setShadowLayer(10.0f, 0.0f, 2.0f, SHADOW_COLOR)
    }

    private fun resetAnimator() {
        mDropCircleAnimator.start()
        mDisappearCircleAnimator.duration = 1 // immediately finish animation cycle
        mDisappearCircleAnimator.start()
    }

    override fun onPreDraw(): Boolean {
        viewTreeObserver.removeOnPreDrawListener(this)
        if (mDropHeightUpdated) {
            updateMaxDropHeight(mUpdateMaxDropHeight)
        }
        return false
    }

    private fun updateMaxDropHeight(height: Int) {
        if (500 * (mWidth / 1440f) > height) {
            Log.w("WaveView", "DropHeight is more than " + 500 * (mWidth / 1440f))
            return
        }
        mMaxDropHeight = Math.min(height.toFloat(), getHeight() - mDropCircleRadius).toInt()
        if (mIsManualRefreshing) {
            mIsManualRefreshing = false
            manualRefresh()
        }
    }

    fun manualRefresh() {
        if (mIsManualRefreshing) {
            return
        }
        mIsManualRefreshing = true
        mDropCircleAnimator = ValueAnimator.ofFloat(mMaxDropHeight.toFloat(), mMaxDropHeight.toFloat())
        mDropCircleAnimator.start()
        mDropVertexAnimator = ValueAnimator.ofFloat(mMaxDropHeight - mDropCircleRadius,
                mMaxDropHeight - mDropCircleRadius)
        mDropVertexAnimator.start()
        mCurrentCircleCenterY = mMaxDropHeight.toFloat()
        postInvalidate()
    }

    /**
     * [WaveView.mDropCircleAnimator] のDuration
     */
    private val DROP_CIRCLE_ANIMATOR_DURATION: Long = 500

    /**
     * [WaveView.mDropBounceVerticalAnimator] のDuration
     */
    private val DROP_VERTEX_ANIMATION_DURATION: Long = 500

    /**
     * [WaveView.mDropBounceVerticalAnimator] と [WaveView.mDropBounceHorizontalAnimator]
     * のDuration
     */
    private val DROP_BOUNCE_ANIMATOR_DURATION: Long = 500

    /**
     * [WaveView.mDisappearCircleAnimator] のDuration
     */
    private val DROP_REMOVE_ANIMATOR_DURATION = 200

    /**
     * 波がくねくねしているDuration
     */
    private val WAVE_ANIMATOR_DURATION = 1000

    /**
     * 波の最大の高さ
     */
    private val MAX_WAVE_HEIGHT = 0.2f

    /**
     * 影の色
     */
    private val SHADOW_COLOR = 0xFF000000.toInt()

    private var mDropCircleRadius = 100f

    private var mPaint: Paint = Paint()

    /**
     * 画面の波を描画するためのPath
     */
    private var mWavePath: Path = Path()

    /**
     * 落ちる円の接線を描画するためのPath
     */
    private var mDropTangentPath = Path()

    /**
     * 落ちる円を描画するためのPath
     */
    private var mDropCirclePath = Path()

    private var mShadowPaint = Paint()

    private var mShadowPath = Path()

    /**
     * 落ちる円の座標を入れているRectF
     */
    private var mDropRect = RectF()

    /**
     * Viewの横幅
     */
    private var mWidth: Int = 0

    /**
     * [WaveView.mDropCircleAnimator] でアニメーションしてる時の円の中心のY座標
     */
    var mCurrentCircleCenterY: Float = 0.toFloat()

    /**
     * 円が落ちる最大の高さ
     */
    private var mMaxDropHeight: Int = 0

    private var mIsManualRefreshing = false

    /**
     * 落ちる円の高さが更新されたかどうか
     */
    private var mDropHeightUpdated = false

    /**
     * [WaveView.mMaxDropHeight] を更新するための一時的な値の置き場
     */
    private var mUpdateMaxDropHeight: Int = 0

    /**
     * 落ちてくる円についてくる三角形の一番上の頂点のAnimator
     */
    private var mDropVertexAnimator = ValueAnimator.ofFloat(0f, 0f)

    /**
     * 落ちた円が横に伸びるときのAnimator
     */
    private var mDropBounceVerticalAnimator = ValueAnimator.ofFloat(0f, 0f)

    /**
     * 落ちた縁が縦に伸びるときのAnimator
     */
    private var mDropBounceHorizontalAnimator = ValueAnimator.ofFloat(0f, 0f)

    /**
     * 落ちる円の中心座標のAnimator
     */
    private var mDropCircleAnimator = ValueAnimator.ofFloat(-1000f, -1000f)

    /**
     * 落ちた円を消すためのAnimator
     */
    private var mDisappearCircleAnimator = ValueAnimator.ofFloat(1f, 1f)

    /**
     * 帰ってくる波ののAnimator
     */
    private var mWaveReverseAnimator = ValueAnimator.ofFloat(1f, 1f)

    /**
     * ベジェ曲線を引く際の座標
     * 左側の2つのアンカーポイントでいい感じに右側にも
     */
    private val BEGIN_PHASE_POINTS = arrayOf(
            //1
            floatArrayOf(0.1655f, 0f), //ハンドル
            floatArrayOf(0.4188f, -0.0109f), //ハンドル
            floatArrayOf(0.4606f, -0.0049f), //アンカーポイント

            //2
            floatArrayOf(0.4893f, 0f), //ハンドル
            floatArrayOf(0.4893f, 0f), //ハンドル
            floatArrayOf(0.5f, 0f)             //アンカーポイント
    )

    private val APPEAR_PHASE_POINTS = arrayOf(
            //1
            floatArrayOf(0.1655f, 0f), //ハンドル
            floatArrayOf(0.5237f, 0.0553f), //ハンドル
            floatArrayOf(0.4557f, 0.0936f), //アンカーポイント

            //2
            floatArrayOf(0.3908f, 0.1302f), //ハンドル
            floatArrayOf(0.4303f, 0.2173f), //ハンドル
            floatArrayOf(0.5f, 0.2173f)         //アンカーポイント
    )

    private val EXPAND_PHASE_POINTS = arrayOf(
            //1
            floatArrayOf(0.1655f, 0f), //ハンドル
            floatArrayOf(0.5909f, 0.0000f), //ハンドル
            floatArrayOf(0.4557f, 0.1642f), //アンカーポイント

            //2
            floatArrayOf(0.3941f, 0.2061f), //ハンドル
            floatArrayOf(0.4303f, 0.2889f), //ハンドル
            floatArrayOf(0.5f, 0.2889f)         //アンカーポイント
    )

    /**
     * @param maxDropHeight 最大下拉高度
     */
    fun setMaxDropHeight(maxDropHeight: Int) {
        if (mDropHeightUpdated) {
            updateMaxDropHeight(maxDropHeight)
        } else {
            mUpdateMaxDropHeight = maxDropHeight
            mDropHeightUpdated = true
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.removeOnPreDrawListener(this)
                viewTreeObserver.addOnPreDrawListener(this)
            }
        }
    }

    private fun onPreDragWave() {
        if (mWaveReverseAnimator.isRunning) {
            mWaveReverseAnimator.cancel()
        }
    }

    fun beginPhase(move1: Float) {
        onPreDragWave()
        //円を描画し始める前の引っ張ったら膨れる波の部分の描画
        mWavePath.moveTo(0f, 0f)
        //左半分の描画
        mWavePath.cubicTo(mWidth * BEGIN_PHASE_POINTS[0][0], BEGIN_PHASE_POINTS[0][1],
                mWidth * BEGIN_PHASE_POINTS[1][0], mWidth * (BEGIN_PHASE_POINTS[1][1] + move1),
                mWidth * BEGIN_PHASE_POINTS[2][0], mWidth * (BEGIN_PHASE_POINTS[2][1] + move1))
        mWavePath.cubicTo(mWidth * BEGIN_PHASE_POINTS[3][0],
                mWidth * (BEGIN_PHASE_POINTS[3][1] + move1), mWidth * BEGIN_PHASE_POINTS[4][0],
                mWidth * (BEGIN_PHASE_POINTS[4][1] + move1), mWidth * BEGIN_PHASE_POINTS[5][0],
                mWidth * (BEGIN_PHASE_POINTS[5][1] + move1))
        //右半分の描画
        mWavePath.cubicTo(mWidth - mWidth * BEGIN_PHASE_POINTS[4][0],
                mWidth * (BEGIN_PHASE_POINTS[4][1] + move1), mWidth - mWidth * BEGIN_PHASE_POINTS[3][0],
                mWidth * (BEGIN_PHASE_POINTS[3][1] + move1), mWidth - mWidth * BEGIN_PHASE_POINTS[2][0],
                mWidth * (BEGIN_PHASE_POINTS[2][1] + move1))
        mWavePath.cubicTo(mWidth - mWidth * BEGIN_PHASE_POINTS[1][0],
                mWidth * (BEGIN_PHASE_POINTS[1][1] + move1), mWidth - mWidth * BEGIN_PHASE_POINTS[0][0],
                BEGIN_PHASE_POINTS[0][1], mWidth.toFloat(), 0f)
        ViewCompat.postInvalidateOnAnimation(this)
    }

    fun appearPhase(move1: Float, move2: Float) {
        onPreDragWave()
        mWavePath.moveTo(0f, 0f)
        //左半分の描画
        mWavePath.cubicTo(mWidth * APPEAR_PHASE_POINTS[0][0], mWidth * APPEAR_PHASE_POINTS[0][1],
                mWidth * Math.min(BEGIN_PHASE_POINTS[1][0] + move2, APPEAR_PHASE_POINTS[1][0]),
                mWidth * Math.max(BEGIN_PHASE_POINTS[1][1] + move1 - move2, APPEAR_PHASE_POINTS[1][1]),
                mWidth * Math.max(BEGIN_PHASE_POINTS[2][0] - move2, APPEAR_PHASE_POINTS[2][0]),
                mWidth * Math.max(BEGIN_PHASE_POINTS[2][1] + move1 - move2, APPEAR_PHASE_POINTS[2][1]))
        mWavePath.cubicTo(
                mWidth * Math.max(BEGIN_PHASE_POINTS[3][0] - move2, APPEAR_PHASE_POINTS[3][0]),
                mWidth * Math.min(BEGIN_PHASE_POINTS[3][1] + move1 + move2, APPEAR_PHASE_POINTS[3][1]),
                mWidth * Math.max(BEGIN_PHASE_POINTS[4][0] - move2, APPEAR_PHASE_POINTS[4][0]),
                mWidth * Math.min(BEGIN_PHASE_POINTS[4][1] + move1 + move2, APPEAR_PHASE_POINTS[4][1]),
                mWidth * APPEAR_PHASE_POINTS[5][0],
                mWidth * Math.min(BEGIN_PHASE_POINTS[0][1] + move1 + move2, APPEAR_PHASE_POINTS[5][1]))
        //右半分の描画
        mWavePath.cubicTo(
                mWidth - mWidth * Math.max(BEGIN_PHASE_POINTS[4][0] - move2, APPEAR_PHASE_POINTS[4][0]),
                mWidth * Math.min(BEGIN_PHASE_POINTS[4][1] + move1 + move2, APPEAR_PHASE_POINTS[4][1]),
                mWidth - mWidth * Math.max(BEGIN_PHASE_POINTS[3][0] - move2, APPEAR_PHASE_POINTS[3][0]),
                mWidth * Math.min(BEGIN_PHASE_POINTS[3][1] + move1 + move2, APPEAR_PHASE_POINTS[3][1]),
                mWidth - mWidth * Math.max(BEGIN_PHASE_POINTS[2][0] - move2, APPEAR_PHASE_POINTS[2][0]),
                mWidth * Math.max(BEGIN_PHASE_POINTS[2][1] + move1 - move2, APPEAR_PHASE_POINTS[2][1]))
        mWavePath.cubicTo(
                mWidth - mWidth * Math.min(BEGIN_PHASE_POINTS[1][0] + move2, APPEAR_PHASE_POINTS[1][0]),
                mWidth * Math.max(BEGIN_PHASE_POINTS[1][1] + move1 - move2, APPEAR_PHASE_POINTS[1][1]),
                mWidth - mWidth * APPEAR_PHASE_POINTS[0][0], mWidth * APPEAR_PHASE_POINTS[0][1], mWidth.toFloat(), 0f)
        mCurrentCircleCenterY = mWidth * Math.min(BEGIN_PHASE_POINTS[3][1] + move1 + move2, APPEAR_PHASE_POINTS[3][1]) + mDropCircleRadius
        ViewCompat.postInvalidateOnAnimation(this)
    }

    fun expandPhase(move1: Float, move2: Float, move3: Float) {
        onPreDragWave()
        mWavePath.moveTo(0f, 0f)
        //左半分の描画
        mWavePath.cubicTo(mWidth * EXPAND_PHASE_POINTS[0][0], mWidth * EXPAND_PHASE_POINTS[0][1],
                mWidth * Math.min(
                        Math.min(BEGIN_PHASE_POINTS[1][0] + move2, APPEAR_PHASE_POINTS[1][0]) + move3,
                        EXPAND_PHASE_POINTS[1][0]), mWidth * Math.max(
                Math.max(BEGIN_PHASE_POINTS[1][1] + move1 - move2, APPEAR_PHASE_POINTS[1][1]) - move3,
                EXPAND_PHASE_POINTS[1][1]),
                mWidth * Math.max(BEGIN_PHASE_POINTS[2][0] - move2, EXPAND_PHASE_POINTS[2][0]),
                mWidth * Math.min(
                        Math.max(BEGIN_PHASE_POINTS[2][1] + move1 - move2, APPEAR_PHASE_POINTS[2][1]) + move3,
                        EXPAND_PHASE_POINTS[2][1]))
        mWavePath.cubicTo(mWidth * Math.min(
                Math.max(BEGIN_PHASE_POINTS[3][0] - move2, APPEAR_PHASE_POINTS[3][0]) + move3,
                EXPAND_PHASE_POINTS[3][0]), mWidth * Math.min(
                Math.min(BEGIN_PHASE_POINTS[3][1] + move1 + move2, APPEAR_PHASE_POINTS[3][1]) + move3,
                EXPAND_PHASE_POINTS[3][1]),
                mWidth * Math.max(BEGIN_PHASE_POINTS[4][0] - move2, EXPAND_PHASE_POINTS[4][0]),
                mWidth * Math.min(
                        Math.min(BEGIN_PHASE_POINTS[4][1] + move1 + move2, APPEAR_PHASE_POINTS[4][1]) + move3,
                        EXPAND_PHASE_POINTS[4][1]), mWidth * EXPAND_PHASE_POINTS[5][0], mWidth * Math.min(
                Math.min(BEGIN_PHASE_POINTS[0][1] + move1 + move2, APPEAR_PHASE_POINTS[5][1]) + move3,
                EXPAND_PHASE_POINTS[5][1]))

        //右半分の描画
        mWavePath.cubicTo(
                mWidth - mWidth * Math.max(BEGIN_PHASE_POINTS[4][0] - move2, EXPAND_PHASE_POINTS[4][0]),
                mWidth * Math.min(
                        Math.min(BEGIN_PHASE_POINTS[4][1] + move1 + move2, APPEAR_PHASE_POINTS[4][1]) + move3,
                        EXPAND_PHASE_POINTS[4][1]), mWidth - mWidth * Math.min(
                Math.max(BEGIN_PHASE_POINTS[3][0] - move2, APPEAR_PHASE_POINTS[3][0]) + move3,
                EXPAND_PHASE_POINTS[3][0]), mWidth * Math.min(
                Math.min(BEGIN_PHASE_POINTS[3][1] + move1 + move2, APPEAR_PHASE_POINTS[3][1]) + move3,
                EXPAND_PHASE_POINTS[3][1]),
                mWidth - mWidth * Math.max(BEGIN_PHASE_POINTS[2][0] - move2, EXPAND_PHASE_POINTS[2][0]),
                mWidth * Math.min(
                        Math.max(BEGIN_PHASE_POINTS[2][1] + move1 - move2, APPEAR_PHASE_POINTS[2][1]) + move3,
                        EXPAND_PHASE_POINTS[2][1]))
        mWavePath.cubicTo(mWidth - mWidth * Math.min(
                Math.min(BEGIN_PHASE_POINTS[1][0] + move2, APPEAR_PHASE_POINTS[1][0]) + move3,
                EXPAND_PHASE_POINTS[1][0]), mWidth * Math.max(
                Math.max(BEGIN_PHASE_POINTS[1][1] + move1 - move2, APPEAR_PHASE_POINTS[1][1]) - move3,
                EXPAND_PHASE_POINTS[1][1]), mWidth - mWidth * EXPAND_PHASE_POINTS[0][0],
                mWidth * EXPAND_PHASE_POINTS[0][1], mWidth.toFloat(), 0f)
        mCurrentCircleCenterY = mWidth * Math.min(
                Math.min(BEGIN_PHASE_POINTS[3][1] + move1 + move2, APPEAR_PHASE_POINTS[3][1]) + move3,
                EXPAND_PHASE_POINTS[3][1]) + mDropCircleRadius
        ViewCompat.postInvalidateOnAnimation(this)
    }

    fun animationDropCircle() {
        if (mDisappearCircleAnimator.isRunning) {
            return
        }
        startDropAnimation()
        startWaveAnimation(0.1f)
    }

    /**
     * 各AnimatorのAnimatorUpdateListener
     */
    private val mAnimatorUpdateListener = ValueAnimator.AnimatorUpdateListener { postInvalidate() }

    fun startDropAnimation() {
        // show dropBubble again
        mDisappearCircleAnimator = ValueAnimator.ofFloat(1f, 1f)
        mDisappearCircleAnimator.duration = 1
        mDisappearCircleAnimator.start()

        mDropCircleAnimator = ValueAnimator.ofFloat(500 * (mWidth / 1440f), mMaxDropHeight.toFloat())
        mDropCircleAnimator.duration = DROP_CIRCLE_ANIMATOR_DURATION
        mDropCircleAnimator.addUpdateListener { animation ->
            mCurrentCircleCenterY = animation.animatedValue as Float
            ViewCompat.postInvalidateOnAnimation(this)
        }
        mDropCircleAnimator.interpolator = AccelerateDecelerateInterpolator()
        mDropCircleAnimator.start()

        mDropVertexAnimator = ValueAnimator.ofFloat(0f, mMaxDropHeight - mDropCircleRadius)
        mDropVertexAnimator.duration = DROP_VERTEX_ANIMATION_DURATION
        mDropVertexAnimator.addUpdateListener(mAnimatorUpdateListener)
        mDropVertexAnimator.start()

        mDropBounceVerticalAnimator = ValueAnimator.ofFloat(0f, 1f)
        mDropBounceVerticalAnimator.duration = DROP_BOUNCE_ANIMATOR_DURATION
        mDropBounceVerticalAnimator.addUpdateListener(mAnimatorUpdateListener)
        mDropBounceVerticalAnimator.interpolator = DropBounceInterpolator()
        mDropBounceVerticalAnimator.startDelay = DROP_VERTEX_ANIMATION_DURATION
        mDropBounceVerticalAnimator.start()

        mDropBounceHorizontalAnimator = ValueAnimator.ofFloat(0f, 1f)
        mDropBounceHorizontalAnimator.duration = DROP_BOUNCE_ANIMATOR_DURATION
        mDropBounceHorizontalAnimator.addUpdateListener(mAnimatorUpdateListener)
        mDropBounceHorizontalAnimator.interpolator = DropBounceInterpolator()
        mDropBounceHorizontalAnimator.startDelay = (DROP_VERTEX_ANIMATION_DURATION + DROP_BOUNCE_ANIMATOR_DURATION * 0.25).toLong()
        mDropBounceHorizontalAnimator.start()
    }

    /**
     * @param h 波が始まる高さ
     */
    fun startWaveAnimation(h: Float) {
        val height = Math.min(h, MAX_WAVE_HEIGHT) * mWidth
        mWaveReverseAnimator = ValueAnimator.ofFloat(height, 0f)
        mWaveReverseAnimator.duration = WAVE_ANIMATOR_DURATION.toLong()
        mWaveReverseAnimator.addUpdateListener({ valueAnimator ->
            val hh = valueAnimator.animatedValue as Float
            mWavePath.moveTo(0f, 0f)
            mWavePath.quadTo(0.25f * mWidth, 0f, 0.333f * mWidth, hh * 0.5f)
            mWavePath.quadTo(mWidth * 0.5f, hh * 1.4f, 0.666f * mWidth, hh * 0.5f)
            mWavePath.quadTo(0.75f * mWidth, 0f, mWidth.toFloat(), 0f)
            postInvalidate()
        })
        mWaveReverseAnimator.interpolator = BounceInterpolator()
        mWaveReverseAnimator.start()
    }

    fun isDisappearCircleAnimatorRunning(): Boolean {
        return mDisappearCircleAnimator.isRunning
    }

    fun startDisappearCircleAnimation() {
        mDisappearCircleAnimator = ValueAnimator.ofFloat(1f, 0f)
        mDisappearCircleAnimator.addUpdateListener(mAnimatorUpdateListener)
        mDisappearCircleAnimator.duration = DROP_REMOVE_ANIMATOR_DURATION.toLong()
        mDisappearCircleAnimator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animator: Animator) {

            }

            override fun onAnimationEnd(animator: Animator) {
                //アニメーション修旅時にAnimatorをリセットすることにより落ちてくる円の初期位置を-100.fにする
                resetAnimator()
                mIsManualRefreshing = false
            }

            override fun onAnimationCancel(animator: Animator) {

            }

            override fun onAnimationRepeat(animator: Animator) {

            }
        })
        mDisappearCircleAnimator.start()
    }

    /**
     * @param radius 阴影半径
     */
    fun setShadowRadius(radius: Int) {
        mShadowPaint.setShadowLayer(radius.toFloat(), 0.0f, 2.0f, SHADOW_COLOR)
    }

    /**
     * WaveView is colored by given color (including alpha)

     * @param color ARGB color. WaveView will be colored by Black if rgb color is provided.
     * *
     * @see Paint.setColor
     */
    fun setWaveColor(color: Int) {
        mPaint.color = color
        invalidate()
    }
}