package com.dovar.waverefresh.kotlin

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import com.dovar.waverefresh.wave.WaveView

/**
 * Created by Administrator on 2017-06-24.
 */
class WaveView(context: Context?) : View(context),ViewTreeObserver.OnPreDrawListener {
    init {
        viewTreeObserver.addOnPreDrawListener(this)
        initView()
    }

    private fun initView() {
        setUpPaint()
        setUpPath()
        resetAnimator()

        mDropRect=RectF()
        setLayerType(View.LAYER_TYPE_SOFTWARE,null)
    }

    private fun setUpPaint() {
        mPaint = Paint()
        mPaint.color=0xff2196F3.toInt()
        mPaint.isAntiAlias=true
        mPaint.style=Paint.Style.FILL

        mShadowPaint = Paint()
        mShadowPaint.setShadowLayer(10.0f, 0.0f, 2.0f, SHADOW_COLOR)
    }

    private fun setUpPath() {
        mWavePath = Path()
        mDropTangentPath = Path()
        mDropCirclePath = Path()
        mShadowPath = Path()
    }

    private fun resetAnimator() {
        mDropVertexAnimator = ValueAnimator.ofFloat(0f, 0f)
        mDropBounceVerticalAnimator = ValueAnimator.ofFloat(0f, 0f)
        mDropBounceHorizontalAnimator = ValueAnimator.ofFloat(0f, 0f)
        mDropCircleAnimator = ValueAnimator.ofFloat(-1000f, -1000f)
        mDropCircleAnimator.start()
        mDisappearCircleAnimator = ValueAnimator.ofFloat(1f, 1f)
        mDisappearCircleAnimator.duration=1 // immediately finish animation cycle
        mDisappearCircleAnimator.start()
    }

    override fun onPreDraw(): Boolean {
        viewTreeObserver.removeOnPreDrawListener(this)
        if (mDropHeightUpdated){
            updateMaxDropHeight(mUpdateMaxDropHeight)
        }
        return false
    }

    private fun updateMaxDropHeight(height:Int){
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

    /**
     * 円のRadius
     */
    private var mDropCircleRadius = 100f

    /**
     * すべてを描画するPaint
     */
    private lateinit var mPaint: Paint

    /**
     * 画面の波を描画するためのPath
     */
    private var mWavePath: Path? = null

    /**
     * 落ちる円の接線を描画するためのPath
     */
    private var mDropTangentPath: Path? = null

    /**
     * 落ちる円を描画するためのPath
     */
    private var mDropCirclePath: Path? = null

    /**
     * 影のPaint
     */
    private lateinit var mShadowPaint: Paint

    /**
     * 影のPath
     */
    private var mShadowPath: Path? = null

    /**
     * 落ちる円の座標を入れているRectF
     */
    private var mDropRect: RectF? = null

    /**
     * Viewの横幅
     */
    private var mWidth: Int = 0

    /**
     * [WaveView.mDropCircleAnimator] でアニメーションしてる時の円の中心のY座標
     */
    private var mCurrentCircleCenterY: Float = 0.toFloat()

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
    private lateinit var mDropVertexAnimator: ValueAnimator

    /**
     * 落ちた円が横に伸びるときのAnimator
     */
    private var mDropBounceVerticalAnimator: ValueAnimator? = null

    /**
     * 落ちた縁が縦に伸びるときのAnimator
     */
    private var mDropBounceHorizontalAnimator: ValueAnimator? = null

    /**
     * 落ちる円の中心座標のAnimator
     */
    private lateinit var mDropCircleAnimator: ValueAnimator

    /**
     * 落ちた円を消すためのAnimator
     */
    private lateinit var mDisappearCircleAnimator: ValueAnimator

    /**
     * 帰ってくる波ののAnimator
     */
    private var mWaveReverseAnimator: ValueAnimator? = null

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


}