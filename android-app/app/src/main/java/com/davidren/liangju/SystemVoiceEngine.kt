package com.davidren.liangju

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Uses the phone's configured recognition service; no app model download is required. */
class SystemVoiceEngine(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    fun available() = SpeechRecognizer.isRecognitionAvailable(context)
    fun stop() { recognizer?.stopListening() }

    // Called on Main. The system service controls whether recognition uses the network.
    suspend fun recognize(onListening: () -> Unit, onProcessing: () -> Unit): String {
        if (!available()) throw IOException("手机未提供系统语音识别，可使用键盘语音输入，或在设置中下载可选离线语音模型")
        val engine = SpeechRecognizer.createSpeechRecognizer(context)
        val handler = Handler(Looper.getMainLooper())
        val stopRecording = Runnable { engine.stopListening(); onProcessing() }
        recognizer = engine
        try {
            return withTimeoutOrNull(35_000) {
                suspendCancellableCoroutine { continuation ->
                    engine.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) { if (continuation.isActive) onListening() }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            handler.removeCallbacks(stopRecording)
                            if (continuation.isActive) onProcessing()
                        }
                        override fun onError(error: Int) {
                            if (continuation.isActive) continuation.resumeWithException(IOException(errorMessage(error)))
                        }
                        override fun onResults(results: Bundle?) {
                            if (!continuation.isActive) return
                            val raw = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                            if (raw.isNullOrEmpty()) continuation.resumeWithException(IOException("未听清内容，请再说一次"))
                            else continuation.resume(raw)
                        }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    engine.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    })
                    handler.postDelayed(stopRecording, 20_000)
                }
            } ?: throw IOException("系统语音识别超时，请检查网络或使用键盘语音输入")
        } finally {
            handler.removeCallbacks(stopRecording)
            recognizer = null
            engine.cancel()
            engine.destroy()
        }
    }

    private fun errorMessage(code: Int) = when (code) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "无法使用麦克风，请检查录音权限"
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未听清内容，请再说一次"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "系统语音服务需要网络，请检查连接后重试"
        SpeechRecognizer.ERROR_AUDIO -> "无法录音，请检查系统麦克风开关或其他应用是否占用麦克风"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "系统语音服务忙，请稍后重试"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "系统语音服务暂不支持中文，可使用键盘语音输入，或下载可选离线语音模型"
        else -> "系统语音识别未完成，请重试、使用键盘语音输入，或在设置中下载可选离线语音模型"
    }
}
