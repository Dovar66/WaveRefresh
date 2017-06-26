package com.dovar.waverefresh.kotlin

/**
 * Created by Administrator on 2017-06-20.
 */
interface Pullable {
    fun canPullUp():Boolean
    fun canPullDown():Boolean
}