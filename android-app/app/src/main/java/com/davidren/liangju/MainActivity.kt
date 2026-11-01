package com.davidren.liangju

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import android.text.InputFilter
import kotlinx.coroutines.*

class MainActivity : Activity() {
    private val lime = Color.rgb(217,255,86)
    private val white = Color.rgb(244,247,240)
    private val muted = Color.rgb(177,189,174)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var models: ModelStore
    private lateinit var voice: VoiceEngine
    private lateinit var ai: AiEngine
    private var work: Job? = null
    private var state = "idle"
    private var status = "先下载模型，即可离线说话亮屏"
    private var draft = "借过一下，谢谢"
    private var original = ""
    private var fullscreen = false
    private var style = DisplayStyle()
    private lateinit var root: FrameLayout
    private lateinit var editor: EditText
    private lateinit var statusLabel: TextView
    private lateinit var originalLabel: TextView
    private lateinit var mic: Button
    private lateinit var organize: Button
    private lateinit var display: Button
    private lateinit var preview: LedView
    private var modelLabel: TextView? = null
    private var modelDialog: AlertDialog? = null
    private var downloadButtons = listOf<Button>()
    private val prefs by lazy { getSharedPreferences("liangju", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        models = ModelStore(this); voice = VoiceEngine(models); ai = AiEngine(models)
        draft = prefs.getString("draft", draft)!!
        style = DisplayStyle(prefs.getInt("color", lime), prefs.getString("direction", "left")!!, prefs.getInt("speed",3), prefs.getFloat("scale",1f), prefs.getBoolean("dots",true))
        if (models.ready("speech")) status = if (models.ready(models.aiGroup)) "语音与 AI 已就绪 · 全程在手机处理" else "语音已就绪 · 可在设置中下载 AI"
        buildUi()
        onBackInvokedDispatcher.registerOnBackInvokedCallback(0) { if (fullscreen) exitFull() else if (state != "idle") cancelWork() else finish() }
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 14f, color: Int = white) = TextView(this).apply {
        this.text = value; textSize = size; setTextColor(color); setLineSpacing(dp(3).toFloat(),1f)
    }
    private fun glass(radius: Int = 24, active: Boolean = false): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR,
        if (active) intArrayOf(0xffe6ff93.toInt(), lime, 0xffbce536.toInt()) else intArrayOf(0x493e493b,0xb31c241f.toInt(),0x94303937.toInt())).apply {
        cornerRadius = dp(radius).toFloat(); setStroke(dp(1), if(active) 0xffeaffb1.toInt() else 0x626f816b)
    }
    private fun button(label: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f; setTextColor(if(primary) Color.rgb(17,24,9) else white)
        background = glass(24,primary); minHeight = dp(48); setPadding(dp(14),dp(8),dp(14),dp(8))
        setOnClickListener { action() }
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun LinearLayout.add(v: View, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT, gap: Int = 12) {
        addView(v, LinearLayout.LayoutParams(-1, if(height < 0) height else dp(height)).apply { bottomMargin = dp(gap) })
    }
    private fun row(vararg buttons: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEachIndexed { i, b -> addView(b, LinearLayout.LayoutParams(0,-2,1f).apply { if(i>0) leftMargin=dp(8) }) }
    }
    private fun save() {
        if (::editor.isInitialized && !fullscreen) draft = editor.text.toString()
        prefs.edit().putString("draft",draft).putInt("color",style.color).putString("direction",style.direction).putInt("speed",style.speed).putFloat("scale",style.scale).putBoolean("dots",style.dots).apply()
    }
    private fun buildUi() {
        root = FrameLayout(this); root.addView(AmbientBackground(this), FrameLayout.LayoutParams(-1,-1)); setContentView(root)
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            v.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,ime.bottom)); insets
        }
        root.requestApplyInsets()
        if (fullscreen) { buildFull(); return }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; setPadding(0,0,0,dp(198)) }
        val maxWidth = minOf(resources.displayMetrics.widthPixels - dp(28), dp(680))
        root.addView(scroll, FrameLayout.LayoutParams(maxWidth,-1,Gravity.CENTER_HORIZONTAL))
        val content = column().apply { setPadding(dp(6),dp(16),dp(6),dp(20)) }; scroll.addView(content)
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher); contentDescription = "亮句图标" }, LinearLayout.LayoutParams(dp(42),dp(42)))
        header.addView(text("  亮句",24f).apply { typeface = Typeface.DEFAULT_BOLD },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("设置") { showSettings() }); content.add(header,gap=20)
        content.add(text("把想说的，亮出来",30f).apply { typeface = Typeface.DEFAULT_BOLD },gap=4)
        content.add(text("一句话，让远处也看得见",15f,muted),gap=18)
        val previewCard = column().apply { background = glass(); setPadding(dp(12),dp(12),dp(12),dp(12)) }
        previewCard.add(text("LIVE  ·  灯牌预览",11f,muted),gap=8)
        preview = LedView(this).apply { message = draft; style = this@MainActivity.style; background = glass(16); clipToOutline = true }
        previewCard.add(preview,152,gap=0); content.add(previewCard)
        val card = column().apply { background = glass(); setPadding(dp(16),dp(16),dp(16),dp(16)) }
        card.add(text("✦  文字与 AI 整理",16f).apply { typeface=Typeface.DEFAULT_BOLD },gap=5)
        originalLabel = text(if(original.isBlank()) "语音识别后会在这里保留原话，也可以直接输入" else "原话：$original",12f,muted)
        card.add(originalLabel,gap=8)
        editor = EditText(this).apply {
            setText(draft); textSize = 25f; setTextColor(lime); setHintTextColor(muted); hint="输入要展示的文字"
            background = GradientDrawable().apply { setColor(0x550a100b); cornerRadius=dp(14).toFloat(); setStroke(dp(1),0x335b7056) }
            setPadding(dp(12),dp(10),dp(12),dp(10)); maxLines=4; minLines=1
            filters = arrayOf(InputFilter.LengthFilter(80)); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { draft=s.toString(); preview.message=draft }
                override fun afterTextChanged(s:android.text.Editable?) {}
            })
        }; card.add(editor,gap=10)
        organize = button("✦  AI 纠错精简") { organizeTyped() }
        card.add(row(organize,button("恢复原话") { if(original.isNotBlank()) { editor.setText(original.take(80)); setStatus("已恢复原话，可继续编辑") } }),gap=0)
        content.add(card)
        val appearance = column().apply { background=glass(); setPadding(dp(14),dp(14),dp(14),dp(14)) }
        appearance.add(text("灯牌样式",12f,muted),gap=9)
        appearance.add(row(button("颜色") { chooseColor() },button(directionLabel()) { chooseDirection() },button("${style.speed} 档速度") { chooseSpeed() }),gap=9)
        content.add(appearance)
        statusLabel=text(status,12f,muted).apply { accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val actions=column().apply { background=glass(28).apply { colors=intArrayOf(0xf52a3426.toInt(),0xf51a241d.toInt(),0xf52b3430.toInt()) };setPadding(dp(12),dp(12),dp(12),dp(12)) }
        actions.add(statusLabel,gap=8)
        mic=button("●  点击说话") { when(state) { "recording" -> voice.stopRecording(); "idle" -> requestMic(); else -> cancelWork() } }; actions.add(mic,58,gap=10)
        display=button("确认文字，立即亮屏  ↗",true) { enterFull() }; actions.add(display,52,gap=0)
        root.addView(actions,FrameLayout.LayoutParams(maxWidth,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=dp(8) })
        refreshButtons()
    }
    private fun setStatus(message: String) { status=message; if(::statusLabel.isInitialized) statusLabel.text=message; modelLabel?.text=message }
    private fun refreshButtons() {
        if (!::mic.isInitialized || fullscreen) return
        mic.text=when(state) { "recording"->"■  结束录音"; "idle"->"●  点击说话"; "cancelling"->"正在停止…"; else->"停止当前任务" }
        mic.isEnabled=state!="cancelling"; organize.isEnabled=state=="idle"; display.isEnabled=state=="idle"
        editor.isEnabled=state=="idle"; downloadButtons.forEach { it.isEnabled=state=="idle" }
    }
    private fun task(kind: String, block: suspend () -> Unit) {
        if(state!="idle")return
        state=kind; refreshButtons()
        work=scope.launch {
            try { block() }
            catch (_:CancellationException) { setStatus("已停止，现有文字已保留") }
            catch (_:OutOfMemoryError) { setStatus("可用内存不足，请关闭其他应用；你仍可手动编辑并亮屏") }
            catch (e:Exception) {
                val message=e.message
                setStatus(if(message != null && message.any { it in '\u4e00'..'\u9fff' }) message else when(state) {
                    "downloading" -> "下载未完成，请检查网络后继续；已保留下载进度"
                    "recording" -> "无法录音，请检查麦克风权限和系统麦克风开关"
                    "recognizing" -> "语音识别未完成，请重试或手动输入"
                    else -> "处理未完成，现有文字已保留，可继续编辑"
                })
            }
            finally { state="idle"; refreshButtons(); save() }
        }
    }
    private fun cancelWork() { if(state=="idle")return; voice.stopRecording(); ai.cancel(); state="cancelling"; setStatus("正在停止当前任务…"); refreshButtons(); work?.cancel() }
    private fun hideKeyboard() { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(editor.windowToken,0); editor.clearFocus() }
    private fun requestMic() {
        if(!models.ready("speech")) { showModels(); return }
        hideKeyboard()
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),41) else record()
    }
    override fun onRequestPermissionsResult(code:Int,p:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(code,p,results)
        if(code==41) { if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED) record() else setStatus("未开启麦克风权限，可继续手动输入；也可在系统设置中授权") }
    }
    private fun record() {
        voice.prepareRecording(); ai.prepare()
        task("recording") {
            setStatus("正在听你说 · 最长 20 秒，点击按钮结束")
            val samples=voice.record { seconds -> withContext(Dispatchers.Main) { setStatus("正在录音  ${seconds}s / 20s · 点击结束") } }
            state="recognizing"; refreshButtons(); setStatus("正在手机上识别语音…")
            val raw=voice.recognize(samples)
            original=raw; originalLabel.text="原话：$raw"; editor.setText(raw.take(80))
            if(models.ready(models.aiGroup)) {
                state="ai"; refreshButtons(); setStatus("正在手机上纠错、提炼短句…")
                val result=ai.shorten(raw); editor.setText(result.text); setStatus(result.warning ?: "整理完成，请确认后亮屏")
            } else setStatus("已识别。可直接亮屏，或在设置中下载 AI 再整理")
        }
    }
    private fun organizeTyped() {
        val raw=editor.text.toString().trim(); if(raw.isBlank()) { setStatus("先输入或说一句话");return }
        if(!models.ready(models.aiGroup)) { showModels(); return }
        hideKeyboard(); ai.prepare()
        task("ai") { original=raw; originalLabel.text="原话：$raw"; setStatus("正在手机上纠错、提炼短句…"); val result=ai.shorten(raw); editor.setText(result.text); setStatus(result.warning ?: "请确认后亮屏") }
    }
    private fun directionLabel() = when(style.direction) { "left"->"← 向左";"right"->"向右 →";"up"->"↑ 向上";"down"->"↓ 向下";else->"静止" }
    private fun chooseDirection() {
        val ids=listOf("left","right","up","down","still")
        AlertDialog.Builder(this).setTitle("滚动方向").setSingleChoiceItems(arrayOf("← 向左","向右 →","↑ 向上","↓ 向下","静止"),ids.indexOf(style.direction)) { d,i -> save();style.direction=ids[i];d.dismiss();buildUi();save() }.show()
    }
    private fun chooseSpeed() { AlertDialog.Builder(this).setTitle("滚动速度").setSingleChoiceItems(arrayOf("1 · 很慢","2 · 慢","3 · 标准","4 · 快","5 · 很快"),style.speed-1) { d,i-> save();style.speed=i+1;d.dismiss();buildUi();save() }.show() }
    private fun chooseColor() {
        val colors=intArrayOf(lime,0xffff75ad.toInt(),0xff70d9ff.toInt(),0xff84f2b0.toInt(),0xffffbd64.toInt(),Color.WHITE)
        AlertDialog.Builder(this).setTitle("灯牌颜色").setSingleChoiceItems(arrayOf("荧光黄","霓虹粉","天空蓝","薄荷绿","琥珀橙","纯白"),colors.indexOf(style.color)) { d,i-> style.color=colors[i];preview.style=style;save();d.dismiss() }.show()
    }
    private fun showSettings() {
        hideKeyboard()
        AlertDialog.Builder(this).setTitle("设置").setItems(arrayOf("离线语音与 AI 模型","文字大小与点阵","关于")) { _,i-> when(i) { 0->showModels();1->showTypography();else->showAbout() } }.setNegativeButton("关闭",null).show()
    }
    private fun showTypography() {
        val panel=column().apply { setPadding(dp(22),dp(12),dp(22),dp(12)) }
        val label=text("文字大小  ${(style.scale*100).toInt()}%",16f,lime)
        panel.add(label)
        panel.add(SeekBar(this).apply {
            min=50;max=180;progress=(style.scale*100).toInt();contentDescription="文字大小"
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s:SeekBar?,p:Int,from:Boolean) { style.scale=p/100f;label.text="文字大小  $p%";preview.style=style }
                override fun onStartTrackingTouch(s:SeekBar?) {}
                override fun onStopTrackingTouch(s:SeekBar?) {save()}
            })
        },48)
        panel.add(Switch(this).apply {text="LED 点阵效果";setTextColor(white);isChecked=style.dots;minHeight=dp(48);setOnCheckedChangeListener {_,b->style.dots=b;preview.style=style;save()} })
        AlertDialog.Builder(this).setTitle("文字大小与点阵").setView(panel).setPositiveButton("完成",null).show()
    }
    private fun showModels() {
        val panel=column().apply { setPadding(dp(22),dp(12),dp(22),dp(4)) }
        panel.add(text("首次下载需要联网。完成后录音和文字整理均在手机本地运行，不上传音频或文本。",14f,muted))
        panel.add(text("建议使用 Wi-Fi。轻量组合需约 1 GB 可用空间，增强组合需约 1.5 GB。模型来自 Hugging Face，连接失败时可稍后继续下载。",12f,muted))
        modelLabel=text(if(state=="idle") "${models.aiName()} · 语音 ${if(models.ready("speech"))"已就绪" else "未下载"} · AI ${if(models.ready(models.aiGroup))"已就绪" else "未下载"}" else status,13f,lime); panel.add(modelLabel!!)
        val speech=button("${if(models.ready("speech"))"✓ 已安装" else "下载 / 继续"} 语音模型 · 240 MB") { download("speech") }
        val llm=button("${if(models.ready(models.aiGroup))"✓ 已安装" else "下载 / 继续"} ${models.aiName()} · ${models.total(models.aiGroup)/1_000_000} MB") { download(models.aiGroup) }
        val compact=button("${if(models.aiGroup=="ai")"✓ " else ""}轻量 0.5B") { if(state=="idle") { models.aiGroup="ai";setStatus("已选择轻量 AI，请确认模型已安装");modelDialog?.dismiss();showModels() } }
        val quality=button("${if(models.aiGroup=="ai_quality")"✓ " else ""}增强 1.5B") { if(state=="idle") { models.aiGroup="ai_quality";setStatus("已选择增强 AI，请确认模型已安装");modelDialog?.dismiss();showModels() } }
        panel.add(row(compact,quality))
        panel.add(text("轻量占用更少；增强模型更适合纠错与提炼。只需下载所选一款 AI，结果仍需确认。",12f,muted))
        val importButton=button("从文件导入模型") { if(state=="idle") { modelDialog?.dismiss();chooseModelFiles() } }
        downloadButtons=listOf(speech,llm,compact,quality,importButton);panel.add(speech);panel.add(llm);panel.add(importButton)
        panel.add(button("停止下载 / 处理") { cancelWork() })
        modelDialog=AlertDialog.Builder(this).setTitle("离线模型").setView(ScrollView(this).apply { addView(panel) }).setNegativeButton("完成",null).create()
        modelDialog?.setOnDismissListener { modelLabel=null;downloadButtons=emptyList() }; modelDialog?.show();refreshButtons()
    }
    private fun download(group:String) {
        if(models.ready(group)) { setStatus("模型已经安装，可以离线使用");return }
        task("downloading") {
            setStatus("正在连接模型服务器…")
            models.install(group) { current,total -> withContext(Dispatchers.Main) { setStatus("${if(group=="speech")"语音" else "AI"}模型 ${current*100/total}% · ${current/1_000_000} / ${total/1_000_000} MB${if(current==total)" · 正在校验" else ""}") } }
            setStatus("${if(group=="speech")"语音" else "AI"}模型已校验安装，可离线使用")
            downloadButtons.getOrNull(if(group=="speech")0 else 1)?.text="✓ ${if(group=="speech")"语音" else "AI"}模型已安装"
        }
    }
    @Suppress("DEPRECATION")
    private fun chooseModelFiles() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type="*/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },42)
    }
    @Deprecated("Legacy Activity result API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=42 || resultCode!=RESULT_OK || data==null)return
        val uris=data.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } } ?: listOfNotNull(data.data)
        task("importing") {
            for(uri in uris) {
                val name=contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
                    if(cursor.moveToFirst())cursor.getString(0) else null
                } ?: throw java.io.IOException("无法读取模型文件名")
                setStatus("正在导入 $name…")
                models.importFile(name,{contentResolver.openInputStream(uri) ?: throw java.io.IOException("无法打开模型文件")}) { n,total ->
                    withContext(Dispatchers.Main) {setStatus("正在导入并校验模型 ${n*100/total}%")}
                }
            }
            setStatus("模型导入完成。请在设置中确认语音与当前 AI 模型均已就绪")
        }
    }
    private fun showAbout() {
        val content=column().apply {setPadding(dp(24),dp(8),dp(24),dp(8))}
        content.add(ImageView(this).apply {setImageResource(R.mipmap.ic_launcher);contentDescription="亮句应用图标"},92)
        content.add(text("亮句  0.2.0",24f,lime));content.add(text("作者  davidren0513\n\n说话、整理、亮屏。支持文字大小、连续滚动和本地语音 / AI。\n\nAndroid 14+ · arm64\nSenseVoice / sherpa-onnx\nQwen2.5 0.5B / 1.5B · llama.cpp\n\nAI 可能理解有误，请核对人名、地点、时间和否定含义后再展示。",14f,muted))
        content.add(button("GitHub · 项目源码") { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/davidren0513/liangju-led"))) })
        content.add(button("开源组件与模型说明") { val licenses=assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use{it.readText()};AlertDialog.Builder(this).setTitle("开源说明").setMessage(licenses).setPositiveButton("知道了",null).show() })
        AlertDialog.Builder(this).setView(ScrollView(this).apply{addView(content)}).setPositiveButton("关闭",null).show()
    }
    private fun enterFull() {
        if(editor.text.isBlank()) {setStatus("先输入要展示的文字");return}
        hideKeyboard();save();fullscreen=true;window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.insetsController?.hide(WindowInsets.Type.systemBars());window.insetsController?.systemBarsBehavior=WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        buildUi()
    }
    private fun buildFull() {
        val led=LedView(this).apply{message=draft;style=this@MainActivity.style};root.addView(led,FrameLayout.LayoutParams(-1,-1))
        val controls=row(button("返回") {exitFull()},button("暂停 / 播放") {led.playing=!led.playing},button("旋转") {requestedOrientation=if(resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT)ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT})
        root.addView(controls,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply{setMargins(dp(18),0,dp(18),dp(12))})
        controls.alpha=.6f
        led.setOnClickListener { controls.visibility=if(controls.visibility==View.VISIBLE)View.INVISIBLE else View.VISIBLE }
        led.contentDescription="灯牌文字：$draft。轻触可显示或隐藏操作按钮"
    }
    private fun exitFull() {fullscreen=false;requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);window.insetsController?.show(WindowInsets.Type.systemBars());buildUi()}
    override fun onConfigurationChanged(config:Configuration) {super.onConfigurationChanged(config);save();buildUi()}
    override fun onStop() {super.onStop();save();if(state in listOf("recording","recognizing","ai"))cancelWork();if(::preview.isInitialized)preview.playing=false}
    override fun onStart() {super.onStart();if(::preview.isInitialized)preview.playing=true}
    override fun onDestroy() {voice.stopRecording();ai.cancel();scope.cancel();modelDialog?.dismiss();super.onDestroy()}
}
