package com.davidren.liangju

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import kotlinx.coroutines.runBlocking
import android.widget.Switch
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OptionalAiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("liangju", Context.MODE_PRIVATE)
    private fun launch(): MainActivity = instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    private fun field(activity: MainActivity, name: String): Any? = MainActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.get(activity)
    private fun invoke(activity: MainActivity, name: String) { MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible=true }.invoke(activity) }
    private fun texts(view: View): List<String> = (if(view is TextView) listOf(view.text.toString()) else emptyList()) +
        (if(view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList())
    private fun render(activity: MainActivity, dialog: AlertDialog, name: String) {
        val view=dialog.window!!.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(activity.filesDir,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun aiDefaultsOffWithExplanationAndPersistsOff() {
        ModelStore(context).aiGroup="ai"
        prefs.edit().remove("ai_enabled").commit()
        val activity=launch()
        try {
            instrumentation.runOnMainSync { invoke(activity,"showSettings") }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val toggle=field(activity,"aiSwitch") as Switch
                assertFalse(toggle.isChecked)
                assertNull(field(activity,"downloadDialog"))
                val dialog=field(activity,"settingsDialog") as AlertDialog
                val copy=texts(dialog.window!!.decorView).joinToString("\n")
                assertTrue(copy.contains("关闭时直接语音转文字"))
                assertTrue(copy.contains("491 MB"))
                assertTrue(copy.contains("可能需要联网"))
                render(activity,dialog,"qa-settings.png")
                prefs.edit().putBoolean("ai_enabled",false).commit()
                activity.finish()
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
        val restarted=launch()
        try {
            instrumentation.runOnMainSync { invoke(restarted,"showSettings");assertFalse((field(restarted,"aiSwitch") as Switch).isChecked) }
        } finally { instrumentation.runOnMainSync { restarted.finish() } }
    }
    @Test fun enableMissingModelOpensProgressAndCancelLeavesAiOff() {
        val models=ModelStore(context)
        models.aiGroup="ai"
        val stamp=models.file(models.specs("ai").single().file+".verified")
        val previous=stamp.takeIf { it.exists() }?.readBytes()
        stamp.delete()
        prefs.edit().putBoolean("ai_enabled",false).commit()
        val activity=launch()
        try {
            instrumentation.runOnMainSync {
                invoke(activity,"showSettings")
                (field(activity,"aiSwitch") as Switch).performClick()
                val dialog=field(activity,"downloadDialog") as AlertDialog
                assertTrue(dialog.isShowing)
                assertTrue(texts(dialog.window!!.decorView).any {it.contains("491 MB")})
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            val deadline=System.currentTimeMillis()+22_000
            while(System.currentTimeMillis()<deadline) {
                var idle=false
                instrumentation.runOnMainSync { idle=field(activity,"state")=="idle" }
                if(idle) break
                Thread.sleep(100)
            }
            instrumentation.runOnMainSync {
                assertEquals("idle",field(activity,"state"))
                assertNull(field(activity,"downloadDialog"))
                assertFalse(prefs.getBoolean("ai_enabled",true))
                assertFalse((field(activity,"aiSwitch") as Switch).isChecked)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            if(previous!=null) stamp.writeBytes(previous) else stamp.delete()
        }
    }
    @Test fun disabledAiKeepsTranscriptEvenWithModelInstalled() {
        assertTrue("Provision a verified AI model for this test",ModelStore(context).ready("ai"))
        prefs.edit().putBoolean("ai_enabled",true).commit()
        val activity=launch()
        try {
            instrumentation.runOnMainSync {
                invoke(activity,"showSettings")
                val toggle=field(activity,"aiSwitch") as Switch
                assertTrue(toggle.isChecked)
                assertNull(field(activity,"downloadDialog"))
                toggle.performClick()
                assertFalse(prefs.getBoolean("ai_enabled",true))
                runBlocking { activity.acceptTranscript("那个那个，师傅我在对面，传白衣服，你等我一下。") }
                assertEquals("那个那个，师傅我在对面，传白衣服，你等我一下。",(field(activity,"editor") as EditText).text.toString())
                assertTrue((field(activity,"status") as String).contains("AI 整理关闭"))
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun unavailableSystemServiceReturnsActionableError() {
        assertFalse("Use the AOSP test device without a speech provider",android.speech.SpeechRecognizer.isRecognitionAvailable(context))
        instrumentation.runOnMainSync {
            val error=runCatching { runBlocking { SystemVoiceEngine(context).recognize({}, {}) } }.exceptionOrNull()
            assertTrue(error is java.io.IOException)
            assertTrue(error!!.message!!.contains("键盘语音输入"))
        }
    }
}
