package com.davidren.liangju

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

object NativeAi {
    init { System.loadLibrary("liangju") }
    external fun reset()
    external fun cancel()
    external fun generate(path: String, prompt: ByteArray): ByteArray
}
data class Suggestion(val text: String, val warning: String? = null)
object TextPolicy {
    fun prompt(raw: String): String {
        // Delimiter-looking input is plain text, never additional model conversation turns.
        val safe = raw.replace("<|", "＜｜").replace("|>", "｜＞").take(300)
        return """<|im_start|>system
你是中文灯牌编辑。将口语改写为简洁的灯牌短句。删除称呼、寒暄、重复和“帮我显示”等无关要求，纠正明显同音错字。保留人名、地点、数字、否定和核心请求，不编造事实。用短语表达，可主动改写，避免照抄原句。尽量12字以内，多信息时可到20字。只输出JSON：{"text":"短句"}。
<|im_end|>
<|im_start|>user
麻烦你帮我告诉大家，演出马上就要开始了，请大家保持安静。
<|im_end|>
<|im_start|>assistant
{"text":"演出即将开始，请安静"}<|im_end|>
<|im_start|>user
请帮我写一下陈晨加油，陈晨加油，陈晨你是最棒的。
<|im_end|>
<|im_start|>assistant
{"text":"陈晨加油，你最棒！"}<|im_end|>
<|im_start|>user
$safe
<|im_end|>
<|im_start|>assistant
"""
    }
    fun validate(raw: String, output: String): Suggestion {
        val value = try {
            val json = JSONObject(output.trim())
            require(json.length() == 1 && json.get("text") is String)
            json.getString("text").trim()
        } catch (_: Exception) { return Suggestion(raw.take(80), "AI 输出格式异常，已保留原话，请手动确认") }
        if (value.isBlank() || value.codePointCount(0, value.length) > 80 || value.contains(Regex("[\\p{Cc}<>]")))
            return Suggestion(raw.take(80), "AI 结果不适合灯牌，已保留原话")
        val digits = Regex("[0-9]+(?:[.:：/-][0-9]+)*")
        if (digits.findAll(raw).map { it.value }.toList() != digits.findAll(value).map { it.value }.toList())
            return Suggestion(raw.take(80), "AI 改动了数字，已保留原话，请确认时间和号码")
        // Conservative guard: a tiny model may accidentally reverse an instruction.
        val negations = listOf("不", "别", "没", "勿", "禁止", "无需", "未")
        if (negations.any { raw.contains(it) } != negations.any { value.contains(it) })
            return Suggestion(raw.take(80), "AI 可能改变否定含义，已保留原话")
        val rolePhrases = listOf("等我", "等你", "接我", "接你", "找我", "找你", "跟我", "跟你")
        if (rolePhrases.any { raw.contains(it) && !value.contains(it) })
            return Suggestion(raw.take(80), "AI 可能改变人物关系，已保留原话")
        return Suggestion(value, "请核对人名、地点和否定含义，确认后再亮屏")
    }
}
class AiEngine(private val models: ModelStore) {
    fun prepare() = NativeAi.reset()
    fun cancel() = NativeAi.cancel()
    suspend fun shorten(raw: String): Suggestion = withContext(Dispatchers.Default) {
        check(models.ready(models.aiGroup)) { "请先下载 AI 模型" }
        currentCoroutineContext().ensureActive()
        val bytes = try {
            NativeAi.generate(models.aiFile().path, TextPolicy.prompt(raw).toByteArray(Charsets.UTF_8))
        } catch (_: java.io.IOException) {
            currentCoroutineContext().ensureActive()
            return@withContext Suggestion(raw.take(80), "AI 处理未完成，已保留原话；可手动编辑或重试")
        }
        currentCoroutineContext().ensureActive()
        TextPolicy.validate(raw, bytes.toString(Charsets.UTF_8))
    }
}
