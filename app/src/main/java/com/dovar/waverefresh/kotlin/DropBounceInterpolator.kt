package com.dovar.waverefresh.kotlin

import android.view.animation.Interpolator

/**
 * Created by Administrator on 2017-06-20.
 */
class DropBounceInterpolator : Interpolator {

    override fun getInterpolation(input: Float): Float {
        if (input < 0.25f) {
            return -38.4f * Math.pow(input - 0.125, 2.0).toFloat() + 0.6f
        } else if (input >= 0.5 && input < 0.75) {
            return -19.2f * Math.pow(input - 0.625, 2.0).toFloat() + 0.3f
        } else {
            return 0.0f
        }
    }
}