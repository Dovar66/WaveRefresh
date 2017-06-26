package com.dovar.waverefresh.kotlin

import android.content.Context
import android.util.AttributeSet
import android.widget.ListView

/**
 * Created by heweizong on 2017/6/26.
 */
class PullableLsv(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : ListView(context, attrs, defStyleAttr) ,Pullable{
    override fun canPullDown():Boolean {
        return false
    }

    override fun canPullUp():Boolean {
        if (count == 0) {
            // 没有item的时候也可以上拉加载
            return true
        } else if (lastVisiblePosition == count - 1) {
            // 滑到底部了
            if (getChildAt(lastVisiblePosition - firstVisiblePosition) != null && getChildAt(
                    lastVisiblePosition - firstVisiblePosition).bottom <= measuredHeight)
                return true
        }
        return false
    }

    constructor(context: Context?,attrs: AttributeSet?):this(context,attrs,0)

    constructor(context: Context?):this(context,null)
}