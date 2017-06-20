package com.dovar.waverefresh.kotlin

import android.content.Context
import android.view.animation.Animation
import android.widget.ImageView

/**
 * Created by Administrator on 2017-06-20.
 */
open class AnimationImageView(context: Context) : ImageView(context) {

    lateinit private var mListener: Animation.AnimationListener

    fun setAnimationListener(listener: Animation.AnimationListener) {
        mListener = listener
    }

    override fun onAnimationStart() {
        super.onAnimationStart()
        mListener.onAnimationStart(animation)
    }

    override fun onAnimationEnd() {
        super.onAnimationEnd()
        mListener.onAnimationEnd(animation)
    }
}