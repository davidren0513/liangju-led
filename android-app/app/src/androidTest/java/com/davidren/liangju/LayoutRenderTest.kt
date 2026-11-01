package com.davidren.liangju

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Produces an application-rendered QA image without capturing other apps or device screens. */
@RunWith(AndroidJUnit4::class)
class LayoutRenderTest {
    @Test fun mainScreenKeepsPrimaryActionsVisible() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val root=activity.window.decorView
            val buttons=mutableListOf<Button>()
            fun collect(v:View) {
                if(v is Button) buttons+=v
                if(v is ViewGroup) for(i in 0 until v.childCount)collect(v.getChildAt(i))
            }
            collect(root)
            val display=buttons.single {it.text.toString().contains("确认文字")}
            val mic=buttons.single {it.text.toString().contains("点击说话")}
            assertTrue(display.isShown && mic.isShown)
            val position=IntArray(2);display.getLocationInWindow(position)
            assertTrue("Display action must fit inside window",position[1]+display.height<=root.height)
            val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            File(activity.filesDir,"qa-home.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
            activity.finish()
        }
    }
}
