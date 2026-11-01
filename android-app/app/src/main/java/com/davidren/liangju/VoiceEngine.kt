package com.davidren.liangju

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class VoiceEngine(private val models: ModelStore) {
    private val stop = AtomicBoolean(false)
    fun prepareRecording() { stop.set(false) }
    fun stopRecording() { stop.set(true) }
    @SuppressLint("MissingPermission")
    suspend fun record(onTime: suspend (Int) -> Unit): FloatArray = withContext(Dispatchers.IO) {
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) throw IOException("这台设备无法以 16 kHz 录音")
        val recorder = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setBufferSizeInBytes(maxOf(min * 2, 6400)).build()
        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) throw IOException("麦克风初始化失败")
            val samples = FloatArray(16000 * 20)
            val buffer = ShortArray(1600)
            var count = 0; var lastSecond = -1
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw IOException("麦克风未启动，请检查系统麦克风开关")
            val started = System.currentTimeMillis()
            while (!stop.get() && count < samples.size && System.currentTimeMillis() - started < 21_000) {
                currentCoroutineContext().ensureActive()
                val n = recorder.read(buffer, 0, minOf(buffer.size, samples.size - count), AudioRecord.READ_NON_BLOCKING)
                if (n < 0) throw IOException("录音中断，请关闭占用麦克风的应用后重试")
                for (i in 0 until n) samples[count++] = buffer[i] / 32768f
                val second = count / 16000
                if (second != lastSecond) { onTime(second); lastSecond = second }
                delay(10)
            }
            if (count < 4000) throw IOException("录音太短，请再说一次")
            val result = samples.copyOf(count)
            val rms = sqrt(result.sumOf { (it * it).toDouble() } / count)
            if (rms < 0.0005) throw IOException("没有听到声音，请检查麦克风权限或系统麦克风开关")
            result
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
        }
    }
    suspend fun recognize(samples: FloatArray): String = withContext(Dispatchers.Default) {
        check(models.ready("speech")) { "请先下载语音识别模型" }
        currentCoroutineContext().ensureActive()
        val recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
            senseVoice = OfflineSenseVoiceModelConfig(model = models.file("sensevoice.int8.onnx").path, language = "zh", useInverseTextNormalization = true),
            tokens = models.file("tokens.txt").path, numThreads = 4, provider = "cpu", debug = false
        )))
        try {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples, 16000)
                recognizer.decode(stream)
                currentCoroutineContext().ensureActive()
                recognizer.getResult(stream).text.replace(Regex("<\\|[^>]*\\|>"), "").trim().also {
                    if (it.isBlank()) throw IOException("没听清楚，请靠近手机再试一次")
                }
            } finally { stream.release() }
        } finally { recognizer.release() }
    }
}
