package com.davidren.liangju

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Optional integration test: provision pinned models in the app private models directory first. */
@RunWith(AndroidJUnit4::class)
class NativeEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun jniLoadsAndRejectsMissingModelWithoutCrash() {
        NativeAi.reset()
        try { NativeAi.generate("/nonexistent/model.gguf", "hello".toByteArray()); fail("Missing model must fail") }
        catch (_: java.io.IOException) { /* Expected JNI exception, no native process crash. */ }
    }
    @Test fun offlineAiPreservesNegationAndProducesChinese() = runBlocking {
        val models = ModelStore(context)
        assumeTrue("Install AI model to run inference test",models.ready("ai"))
        val ai = AiEngine(models); ai.prepare()
        val input="别在东门等，来西门找我。"
        val output=NativeAi.generate(models.aiFile().path,TextPolicy.prompt(input).toByteArray()).toString(Charsets.UTF_8)
        assertTrue(org.json.JSONObject(output).getString("text").isNotBlank())
        val result=TextPolicy.validate(input,output)
        assertTrue(result.text.isNotBlank())
        assertTrue(result.text.contains("西门"))
        assertTrue(result.text.contains("别") || result.text.contains("不"))
    }
    @Test fun qualityModelCorrectsHomophoneOffline() = runBlocking {
        val models=ModelStore(context)
        assumeTrue("Install optional quality model first",models.ready("ai_quality"))
        val previous=models.aiGroup
        try {
            models.aiGroup="ai_quality";NativeAi.reset()
            val input="师傅我在对面，传白衣服，你等我一下。"
            val output=NativeAi.generate(models.aiFile().path,TextPolicy.prompt(input).toByteArray()).toString(Charsets.UTF_8)
            val result=TextPolicy.validate(input,output)
            assertTrue(result.text.contains("穿白衣服"))
            assertTrue(result.text.contains("对面"))
            assertTrue(result.text.contains("等我"))
        } finally {models.aiGroup=previous}
    }
    @Test fun corruptImportDoesNotReplaceVerifiedModel() = runBlocking {
        val models=ModelStore(context)
        val tokens=models.file("tokens.txt")
        val before=if(tokens.exists())tokens.readBytes() else null
        try {
            models.importFile("tokens.txt",{java.io.ByteArrayInputStream("corrupt model".toByteArray())}) { _,_ -> }
            fail("Corrupt model must be rejected")
        } catch (_:java.io.IOException) { }
        if(before!=null)assertArrayEquals(before,tokens.readBytes()) else assertFalse(tokens.exists())
        assertFalse(models.file("tokens.txt.importing").exists())
    }
    @Test fun speechModelRecognizesFixtureOffline() = runBlocking {
        val models=ModelStore(context)
        val fixture=File(context.filesDir,"zh.pcm")
        assumeTrue("Install speech model and zh.pcm fixture first",models.ready("speech") && fixture.exists())
        val bytes=fixture.readBytes()
        val buffer=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples=FloatArray(buffer.remaining()) {buffer.get()/32768f}
        val result=VoiceEngine(models).recognize(samples)
        assertTrue(result.isNotBlank())
        assertTrue(result.contains(Regex("[\\u4e00-\\u9fff]")))
    }
}
