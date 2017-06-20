package com.dovar.waverefresh.kotlin

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.View

/**
 * Created by Administrator on 2017-06-20.
 */
class MaterialProgressDrawable(context:Context,parent:View): Drawable(),Animatable {

    lateinit var mResource:Resources
    lateinit var mRing:Ring
    val mCallback:Callback=Callback{


    }




    init {
        mResource=context.resources
        mRing=Ring(mCallback)
        mRing.setColors()
    }
    override fun setAlpha(alpha: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getOpacity(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isRunning(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun start() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun stop() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun draw(canvas: Canvas?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object{
        class Ring(callback:Drawable.Callback){
            lateinit var mPaint:Paint
            lateinit var mArrowPaint:Paint

            init {
                mPaint.strokeCap=Paint.Cap.SQUARE
                mPaint.isAntiAlias=true
                mPaint.style=Paint.Style.STROKE

                mArrowPaint.style=Paint.Style.FILL
                mArrowPaint.isAntiAlias=true
            }


        }
    }
}