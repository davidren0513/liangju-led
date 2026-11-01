package com.davidren.liangju

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.*

data class DisplayStyle(var color: Int = Color.rgb(217,255,86), var direction: String = "left", var speed: Int = 3, var scale: Float = 1f, var dots: Boolean = true)
class LedView(context: Context) : View(context) {
    var message: String = "借过一下，谢谢"; set(value) { field = value; dirty = true; phase = 0f; invalidate() }
    var style = DisplayStyle(); set(value) { field = value.copy(); dirty = true; invalidate() }
    var playing = true; set(value) { field = value; last = 0; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sprite: Bitmap? = null
    private var dirty = true
    private var phase = 0f
    private var last = 0L
    private var fontSize = 0f
    init { contentDescription = "LED 灯牌预览"; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { dirty = true }
    private fun rebuild() {
        sprite?.recycle()
        if (width == 0 || height == 0) return
        val density = resources.displayMetrics.density
        val vertical = style.direction in listOf("up", "down", "still")
        fontSize = min(height * .40f, 110 * density) * style.scale
        if (vertical) fontSize = min(fontSize, width / 4.3f)
        fontSize = min(fontSize, sqrt(4_000_000f / (maxOf(1,message.length) * 1.5f)))
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD); paint.textSize = fontSize
        val lines = mutableListOf<String>()
        var line = ""
        for (code in message.codePoints().toArray()) {
            val c = String(Character.toChars(code))
            if (c == "\n" || (vertical && line.isNotEmpty() && paint.measureText(line + c) > width - 40 * density)) { lines += line; line = if (c == "\n") "" else c }
            else line += c
        }
        if (line.isNotEmpty() || lines.isEmpty()) lines += line
        val w = ceil(lines.maxOf { paint.measureText(it).toDouble() } + 16 * density).toInt().coerceAtLeast(1)
        val h = ceil(lines.size * fontSize * 1.35f + 16 * density).toInt().coerceAtLeast(1)
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val mc = Canvas(mask); paint.color = style.color
        lines.forEachIndexed { i, s -> mc.drawText(s, (w - paint.measureText(s)) / 2, 8 * density + fontSize * (1 + i * 1.35f), paint) }
        if (!style.dots) sprite = mask
        else {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap); val step = max(4f, fontSize / 23f)
            var y = step / 2
            while (y < h) {
                var x = step / 2
                while (x < w) { if (Color.alpha(mask.getPixel(x.toInt(), y.toInt())) > 100) c.drawCircle(x, y, step * .37f, paint); x += step }
                y += step
            }
            mask.recycle(); sprite = bitmap
        }
        contentDescription = "灯牌文字：$message"
        dirty = false
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); canvas.drawColor(Color.rgb(5,8,6))
        if (dirty) rebuild()
        val image = sprite ?: return
        val now = SystemClock.uptimeMillis()
        if (playing && last > 0 && style.direction != "still") phase += min(50L, now - last) / 1000f * (20 + style.speed * 17) * resources.displayMetrics.density
        last = now
        val vertical = style.direction == "up" || style.direction == "down"
        val gap = min(fontSize * 2, (if (vertical) height else width) * .45f)
        val x = (width - image.width) / 2f; val y = (height - image.height) / 2f
        paint.color = style.color
        if (style.direction == "still") {
            val scale = min(1f, min((width - 20f) / image.width, (height - 20f) / image.height))
            canvas.save(); canvas.translate(width / 2f, height / 2f); canvas.scale(scale,scale)
            canvas.drawBitmap(image, -image.width / 2f, -image.height / 2f, paint); canvas.restore()
        } else {
            val cycle = (if (vertical) image.height else image.width) + gap
            val start = max(12f, if (vertical) y else x)
            val direction = if (style.direction == "left" || style.direction == "up") -1 else 1
            val origin = start + direction * (phase % cycle)
            val extent = if (vertical) image.height else image.width
            val viewport = if (vertical) height else width
            val first = ceil((-extent - origin) / cycle).toInt()
            val end = floor((viewport - origin) / cycle).toInt()
            for (i in first..end) canvas.drawBitmap(image, if (vertical) x else origin + i * cycle, if (vertical) origin + i * cycle else y, paint)
        }
        if (playing && style.direction != "still") postInvalidateOnAnimation()
    }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); sprite?.recycle(); sprite = null; dirty = true; last = 0 }
}

/** Translucent highlights and layered light: no blur applied to LED glyphs. */
class AmbientBackground(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var upper: Shader? = null
    private var lower: Shader? = null
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        if(w<=0)return
        upper=RadialGradient(w * .94f,h * .15f,w * .8f,intArrayOf(0x374e6730,0x004e6730),null,Shader.TileMode.CLAMP)
        lower=RadialGradient(w * .04f,h * .65f,w * .85f,intArrayOf(0x30354b65,0x00354b65),null,Shader.TileMode.CLAMP)
    }
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(13,16,14))
        paint.shader=upper;canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
        paint.shader=lower;canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
        paint.shader=null
    }
}
