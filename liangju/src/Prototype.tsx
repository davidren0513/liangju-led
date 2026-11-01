import { useEffect, useRef, useState, type CSSProperties } from "react";
import { ArrowLeftIcon, ArrowRightIcon, ArrowUpIcon, ArrowDownIcon, ArrowsOutSimpleIcon, ArrowsClockwiseIcon, CaretRightIcon, CheckIcon, CircleIcon, GearSixIcon, MicrophoneIcon, PauseIcon, PencilSimpleLineIcon, PlayIcon, SlidersHorizontalIcon, SparkleIcon, InfoIcon, GithubLogoIcon, ArrowSquareOutIcon, XIcon } from "@phosphor-icons/react";
import { BottomSheet, KeyboardTextarea, MobileScroll, useKeyboard, useKeyboardInsets, useMobileDevice } from "./mobile";

type Direction = "left" | "right" | "up" | "down" | "still";
type Panel = "color" | "direction" | "speed" | "edit" | "voice" | "settings" | "about" | null;
const AUTHOR = "davidren0513";
const GITHUB_URL = "https://github.com/davidren0513/liangju-led";
const COLORS = [{ name: "荧光黄", value: "#d9ff56" }, { name: "霓虹粉", value: "#ff75ad" }, { name: "天空蓝", value: "#70d9ff" }, { name: "薄荷绿", value: "#84f2b0" }, { name: "琥珀橙", value: "#ffbd64" }, { name: "纯白", value: "#ffffff" }];
const DIRECTIONS = [{ id: "still", name: "静止显示", Icon: PauseIcon }, { id: "left", name: "向左滚动", Icon: ArrowLeftIcon }, { id: "right", name: "向右滚动", Icon: ArrowRightIcon }, { id: "up", name: "向上滚动", Icon: ArrowUpIcon }, { id: "down", name: "向下滚动", Icon: ArrowDownIcon }] as const;
const EXAMPLES = [
  { name: "礼貌借过", raw: "麻烦前面的朋友让一下，我想过去，谢谢。", result: "借过一下，谢谢", color: 0, direction: "left" as Direction },
  { name: "打车见面", raw: "师傅，我在马路对面，穿白色衣服，你等我一下。", result: "我在对面，白衣服，稍等！", color: 0, direction: "left" as Direction },
  { name: "现场应援", raw: "帮我显示小林加油，粉色，向上滚动。", result: "小林加油！", color: 1, direction: "up" as Direction },
];

// The dot matrix is the app's live text renderer, not a raster mockup asset.
function LedCanvas({ text, color, direction, speed, playing, large = false, rotated = false, dots = true, size = 1 }: { text: string; color: string; direction: Direction; speed: number; playing: boolean; large?: boolean; rotated?: boolean; dots?: boolean; size?: number }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const progress = useRef(0);
  useEffect(() => { progress.current = 0; }, [text, direction, rotated]);
  useEffect(() => {
    const el = canvas.current;
    if (!el) return;
    const context = el.getContext("2d");
    if (!context) return;
    let frame = 0;
    let last = 0;
    let dirty = true;
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const observer = new ResizeObserver(() => { dirty = true; });
    observer.observe(el);
    let width = 0, height = 0, logicalW = 0, logicalH = 0;
    let sprite = document.createElement("canvas");
    let spriteWidth = 0, spriteHeight = 0, repeatGap = 0;
    function rebuild() {
      if (!el || !context) return;
      const rect = el.getBoundingClientRect();
      width = rect.width; height = rect.height;
      if (!width || !height) return;
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      el.width = Math.round(width * dpr); el.height = Math.round(height * dpr);
      context.setTransform(dpr, 0, 0, dpr, 0, 0);
      logicalW = rotated ? height : width; logicalH = rotated ? width : height;
      const vertical = direction === "up" || direction === "down" || direction === "still";
      const maxFont = (large ? Math.min(logicalH * .32, 104) : 54) * size;
      const measure = document.createElement("canvas").getContext("2d")!;
      measure.font = `600 ${maxFont}px "PingFang SC", "Microsoft YaHei", sans-serif`;
      const fitted = Math.min(maxFont, maxFont * (logicalW - 20) / Math.max(1, measure.measureText(text).width));
      const fs = Math.max(20, vertical ? Math.min(maxFont, logicalW / 4.4) : text.length <= 10 && !large ? fitted : maxFont);
      // Separate consecutive messages by about two characters, not a viewport.
      repeatGap = Math.min(fs * 2, (vertical ? logicalH : logicalW) * .45);
      const source = document.createElement("canvas");
      const g = source.getContext("2d", { willReadFrequently: true })!;
      g.font = `600 ${fs}px "PingFang SC", "Microsoft YaHei", sans-serif`;
      const lines: string[] = [];
      let line = "";
      for (const char of Array.from(text)) {
        if (char === "\n" || (vertical && line && g.measureText(line + char).width > logicalW - 32)) { lines.push(line); line = char === "\n" ? "" : char; }
        else line += char;
      }
      if (line || !lines.length) lines.push(line);
      spriteWidth = Math.ceil(Math.max(...lines.map(l => g.measureText(l).width), 1) + 16);
      spriteHeight = Math.ceil(lines.length * fs * 1.3 + 16);
      source.width = spriteWidth; source.height = spriteHeight;
      g.font = `600 ${fs}px "PingFang SC", "Microsoft YaHei", sans-serif`;
      g.fillStyle = "white"; g.textBaseline = "middle";
      lines.forEach((l, i) => g.fillText(l, (spriteWidth - g.measureText(l).width) / 2, 8 + fs * 1.3 * (i + .5)));
      sprite = document.createElement("canvas"); sprite.width = spriteWidth * 2; sprite.height = spriteHeight * 2;
      const s = sprite.getContext("2d")!; s.scale(2, 2); s.fillStyle = color;
      if (dots) {
        const pixels = g.getImageData(0, 0, source.width, source.height).data;
        const step = large ? Math.max(3, fs / 21) : 3.1;
        s.shadowColor = color; s.shadowBlur = 3;
        for (let y = 1; y < spriteHeight; y += step) for (let x = 1; x < spriteWidth; x += step) {
          if (pixels[(Math.floor(y) * spriteWidth + Math.floor(x)) * 4 + 3] > 90) { s.beginPath(); s.arc(x, y, step * .36, 0, Math.PI * 2); s.fill(); }
        }
      } else {
        s.drawImage(source, 0, 0); s.globalCompositeOperation = "source-in"; s.fillRect(0, 0, spriteWidth, spriteHeight);
      }
      dirty = false;
    }
    function draw(time: number) {
      if (!context) return;
      if (dirty) rebuild();
      const dt = last ? Math.min((time - last) / 1000, .05) : 0; last = time;
      if (playing && !reduced && direction !== "still") progress.current += dt;
      context.clearRect(0, 0, width, height); context.save();
      if (rotated) { context.translate(width, 0); context.rotate(Math.PI / 2); }
      context.fillStyle = "#060806"; context.fillRect(0, 0, logicalW, logicalH);
      if (dots) {
        context.fillStyle = "#11150d";
        for (let y = 4; y < logicalH; y += 5.5) for (let x = 4; x < logicalW; x += 5.5) { context.beginPath(); context.arc(x, y, 1.1, 0, Math.PI * 2); context.fill(); }
      }
      let x = (logicalW - spriteWidth) / 2, y = (logicalH - spriteHeight) / 2;
      const distance = Math.max(0, progress.current - 1.1) * (16 + speed * 15);
      const repeating = (playing && !reduced) || progress.current > 0;
      if (repeating && (direction === "left" || direction === "right")) {
        const cycle = spriteWidth + repeatGap;
        const start = Math.max(12, x);
        x = direction === "left" ? start - distance % cycle : start + distance % cycle;
        // Tile all intersecting copies so the next one enters before this exits.
        for (let i = Math.ceil((-spriteWidth - x) / cycle); i <= Math.floor((logicalW - x) / cycle); i++) {
          context.drawImage(sprite, x + i * cycle, y, spriteWidth, spriteHeight);
        }
      } else if (repeating && (direction === "up" || direction === "down")) {
        const cycle = spriteHeight + repeatGap;
        const start = Math.max(12, y);
        y = direction === "up" ? start - distance % cycle : start + distance % cycle;
        for (let i = Math.ceil((-spriteHeight - y) / cycle); i <= Math.floor((logicalH - y) / cycle); i++) {
          context.drawImage(sprite, x, y + i * cycle, spriteWidth, spriteHeight);
        }
      } else {
        if (spriteHeight > logicalH - 24) {
          const scale = (logicalH - 24) / spriteHeight;
          context.translate(logicalW / 2, logicalH / 2); context.scale(scale, scale);
          x = -spriteWidth / 2; y = -spriteHeight / 2;
        }
        context.drawImage(sprite, x, y, spriteWidth, spriteHeight);
      }
      context.restore();
      frame = requestAnimationFrame(draw);
    }
    frame = requestAnimationFrame(draw);
    return () => { observer.disconnect(); cancelAnimationFrame(frame); };
  }, [text, color, direction, speed, playing, large, rotated, dots, size]);
  return <canvas ref={canvas} className="led-canvas" role="img" aria-label={`灯牌文字：${text}`} />;
}

export default function Prototype() {
  const { setDeviceId } = useMobileDevice();
  const keyboard = useKeyboard();
  const { bottomInset } = useKeyboardInsets();
  const [raw, setRaw] = useState(EXAMPLES[0].raw);
  const [result, setResult] = useState(EXAMPLES[0].result);
  const [mode, setMode] = useState<"smart" | "raw">("smart");
  const [color, setColor] = useState(0);
  const [direction, setDirection] = useState<Direction>("left");
  const [speed, setSpeed] = useState(3);
  const [dots, setDots] = useState(true);
  const [size, setSize] = useState(1);
  const [panel, setPanel] = useState<Panel>(null);
  const [draft, setDraft] = useState("");
  const [editingRaw, setEditingRaw] = useState(false);
  const [full, setFull] = useState(false);
  const [playing, setPlaying] = useState(true);
  const [rotated, setRotated] = useState(false);
  const [previewPlaying, setPreviewPlaying] = useState(false);
  const [sample, setSample] = useState(0);
  const [voiceState, setVoiceState] = useState<"ready" | "processing" | "done">("ready");
  const [holding, setHolding] = useState(false);
  const held = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const text = mode === "raw" ? raw : result;
  const currentDirection = DIRECTIONS.find(d => d.id === direction)!;
  const DirectionIcon = currentDirection.Icon;
  useEffect(() => { setDeviceId("pixel-10"); }, []);
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current); }, []);
  useEffect(() => {
    const escape = (event: KeyboardEvent) => { if (event.key === "Escape" && full) setFull(false); };
    window.addEventListener("keydown", escape); return () => window.removeEventListener("keydown", escape);
  }, [full]);
  function closePanel() { keyboard.hide(); setPanel(null); if (timer.current) clearTimeout(timer.current); }
  function openPanel(next: Panel) { keyboard.hide(); setPanel(next); }
  function edit(manual = false) { setEditingRaw(manual || mode === "raw"); setDraft(manual ? "" : text); openPanel("edit"); }
  function saveText() { if (!draft.trim()) return; if (editingRaw) { setRaw(draft.trim()); setResult(draft.trim()); setMode("raw"); } else setResult(draft.trim()); closePanel(); }
  function showVoice() { held.current = false; setHolding(false); setVoiceState("ready"); openPanel("voice"); }
  function processExample() { setVoiceState("processing"); timer.current = setTimeout(() => setVoiceState("done"), 700); }
  function applyExample() { const e = EXAMPLES[sample]; setRaw(e.raw); setResult(e.result); setMode("smart"); setColor(e.color); setDirection(e.direction); closePanel(); }
  function showFull() { keyboard.hide(); setPlaying(true); setFull(true); }
  const titles = { color: "选择文字颜色", direction: "滚动方向", speed: "调整滚动速度", edit: editingRaw ? "手动输入" : "修改展示文字", voice: "试一试语音整理", settings: "灯牌设置", about: "关于亮句" };
  return <div className="lj-app" style={{ "--led-color": COLORS[color].value } as CSSProperties}>
    <div className="lj-main-shell" inert={full}>
    <MobileScroll className="lj-scroll">
      <main className="lj-content">
        <header className="lj-header"><div className="lj-brand">亮句</div><button className="local-indicator" onClick={() => openPanel("settings")}><CircleIcon weight="fill" size={7} />交互演示</button><button className="icon-button settings-button" aria-label="打开设置" onClick={() => openPanel("settings")}><GearSixIcon size={23} /></button></header>
        <section className="lj-heading"><h1>说出来，<span>亮出来。</span></h1><p>一句话，让对方看懂。</p></section>
        <section className="preview-board" aria-label="灯牌预览"><LedCanvas text={text} color={COLORS[color].value} direction={direction} speed={speed} playing={previewPlaying} dots={dots} size={size} /><div className="preview-top"><span>预览</span><button aria-label="全屏预览" onClick={showFull}><ArrowsOutSimpleIcon size={20} /></button></div><button className="preview-play" aria-label={previewPlaying ? "暂停预览" : "播放预览"} onClick={() => setPreviewPlaying(v => !v)}>{previewPlaying ? <PauseIcon size={13} weight="fill" /> : <PlayIcon size={13} weight="fill" />}</button></section>
        <section className="ai-section"><div className="section-heading"><h2>{mode === "smart" ? "AI 精简" : "原话显示"}</h2><button className="text-button" onClick={() => edit()}><PencilSimpleLineIcon size={18} />编辑</button></div><p className="original" title={raw}>原话：{raw}</p><button className="result-text" onClick={() => edit()} aria-label="编辑展示文字">{text}</button><div className="mode-switch" role="group" aria-label="文字处理模式"><button aria-pressed={mode === "smart"} onClick={() => setMode("smart")}>智能精简</button><button aria-pressed={mode === "raw"} onClick={() => setMode("raw")}>原话显示</button></div></section>
        <section className="appearance"><h2>外观设置</h2><div className="appearance-controls"><button onClick={() => openPanel("color")} aria-label="选择颜色"><CircleIcon weight="fill" size={26} color={COLORS[color].value} /><span>颜色</span><CaretRightIcon size={12} /></button><button onClick={() => openPanel("direction")} aria-label="选择滚动方向"><DirectionIcon size={26} /><span>{currentDirection.name}</span><CaretRightIcon size={12} /></button><button onClick={() => openPanel("speed")} aria-label="调整速度"><SlidersHorizontalIcon size={25} /><span>速度</span><CaretRightIcon size={12} /></button></div></section>
      </main>
    </MobileScroll>
    </div>
    {!full && <footer className="lj-actions" style={{ bottom: bottomInset }}><button className={`voice-button ${holding ? "holding" : ""}`} onPointerDown={e => { e.currentTarget.setPointerCapture(e.pointerId); held.current = true; setHolding(true); }} onPointerUp={() => { if (held.current) showVoice(); }} onPointerCancel={() => { held.current = false; setHolding(false); }} onClick={e => { if (e.detail === 0) showVoice(); }}><span><MicrophoneIcon size={28} weight="bold" />{holding ? "松开，查看演示" : "按住说话"}</span><small>{holding ? "原型演示，不会录制声音" : "松开生成，再确认上屏 · 演示"}</small></button><button className="primary-button display-button" onClick={showFull}><ArrowsOutSimpleIcon size={25} />亮屏展示</button><button className="manual-button" onClick={() => edit(true)}>手动输入<CaretRightIcon size={15} /></button></footer>}
    {full && <section className="presentation" role="dialog" aria-modal="true" aria-label="全屏灯牌"><LedCanvas text={text} color={COLORS[color].value} direction={direction} speed={speed} playing={playing} large rotated={rotated} dots={dots} size={size} /><div className="presentation-top"><button className="glass-button" autoFocus onClick={() => setFull(false)}><ArrowLeftIcon size={19} />返回编辑</button><span>{currentDirection.name}</span></div><div className="presentation-bottom" style={{ bottom: bottomInset + 12 }}><button className="glass-button" onClick={() => setPlaying(v => !v)}>{playing ? <PauseIcon size={19} /> : <PlayIcon size={19} />}{playing ? "暂停" : "继续"}</button><button className="glass-button" aria-pressed={rotated} onClick={() => setRotated(v => !v)}><ArrowsClockwiseIcon size={20} />{rotated ? "竖屏" : "横屏"}</button></div></section>}
    <BottomSheet open={panel !== null} onOpenChange={v => { if (!v) closePanel(); }} title={panel ? titles[panel] : ""} description={panel === "voice" ? "使用示例体验，不会打开麦克风。" : panel === "edit" ? "确认文字后再展示，最多 80 个字符。" : ""} snap={panel === "voice" || panel === "about" ? .8 : panel === "settings" ? .65 : .6}>
      <div className="lj-sheet"><button className="sheet-close icon-button" aria-label="关闭面板" onClick={closePanel}><XIcon size={20} /></button>
        {panel === "color" && <><div className="color-grid">{COLORS.map((c, i) => <button key={c.name} aria-pressed={color === i} onClick={() => setColor(i)}><span className="color-sample"><CircleIcon size={42} weight="fill" color={c.value} />{color === i && <CheckIcon size={20} color="#10120f" weight="bold" />}</span>{c.name}</button>)}</div><button className="primary-button" onClick={closePanel}>完成</button></>}
        {panel === "direction" && <><div className="direction-list">{DIRECTIONS.map(({ id, name, Icon }) => <button key={id} aria-pressed={direction === id} onClick={() => setDirection(id)}><Icon size={23} />{name}{direction === id && <CheckIcon size={19} />}</button>)}</div><p className="hint">短句适合静止展示，长句可以滚动阅读。</p><button className="primary-button" onClick={closePanel}>完成</button></>}
        {panel === "speed" && <><div className="sheet-led"><LedCanvas text={text} color={COLORS[color].value} direction={direction === "still" ? "left" : direction} speed={speed} playing dots={dots} /></div><div className="range-heading"><label htmlFor="speed">滚动速度</label><strong>{["", "很慢", "慢速", "中速", "快速", "很快"][speed]}</strong></div><input id="speed" type="range" min="1" max="5" step="1" value={speed} onChange={e => setSpeed(Number(e.target.value))} /><div className="range-labels"><span>慢一点</span><span>快一点</span></div><button className="primary-button" onClick={closePanel}>完成</button></>}
        {panel === "edit" && <><label className="input-label" htmlFor="message">展示文字</label><KeyboardTextarea id="message" className="message-input" value={draft} onChange={e => setDraft(e.target.value)} maxLength={80} placeholder="输入想让对方看到的话…" rows={3} /><div className="input-meta"><span>{editingRaw ? "手动输入将按原话显示" : "只修改精简结果，保留原话"}</span><span>{draft.length}/80</span></div><button className="primary-button" disabled={!draft.trim()} onClick={saveText}><CheckIcon size={20} />使用这句话</button></>}
        {panel === "voice" && <><div className="example-tabs" role="group" aria-label="语音演示场景">{EXAMPLES.map((e, i) => <button key={e.name} disabled={voiceState === "processing"} aria-pressed={sample === i} onClick={() => { setSample(i); setVoiceState("ready"); }}>{e.name}</button>)}</div><div className="voice-transcript"><span>示例语音内容</span><p>{EXAMPLES[sample].raw}</p></div><div aria-live="polite" className="voice-result">{voiceState === "processing" ? <p className="processing"><SparkleIcon size={23} />正在演示整理过程…</p> : voiceState === "done" ? <><span><SparkleIcon size={16} />精简结果 · 示例</span><strong>{EXAMPLES[sample].result}</strong>{sample === 2 && <p>同时设置为霓虹粉、向上滚动</p>}</> : <p>去掉口头语，保留最想表达的内容。</p>}</div><button className="primary-button" disabled={voiceState === "processing"} onClick={voiceState === "done" ? applyExample : processExample}>{voiceState === "done" ? "使用这句话" : voiceState === "processing" ? "正在整理…" : "预览整理效果"}</button><p className="hint">此处展示预设结果。安卓正式版将接入离线语音识别与本地 AI。</p></>}
        {panel === "settings" && <><div className="settings-row"><span>文字样式</span><div className="small-switch"><button aria-pressed={dots} onClick={() => setDots(true)}>LED 点阵</button><button aria-pressed={!dots} onClick={() => setDots(false)}>清晰大字</button></div></div><div className="range-heading"><label htmlFor="font-size">字号</label><strong>{Math.round(size * 100)}%</strong></div><input id="font-size" type="range" min="0.75" max="1.5" step="0.25" value={size} onChange={e => setSize(Number(e.target.value))} /><button className="about-entry" onClick={() => openPanel("about")}><InfoIcon size={22} /><span>关于<small>作者、项目说明与 GitHub</small></span><CaretRightIcon size={17} /></button><button className="primary-button" onClick={closePanel}>完成</button></>}
        {panel === "about" && <>
          <div className="about-brand"><strong>亮句</strong><span>说出来，亮出来。</span><small>v0.1.0 · 安卓交互原型</small></div>
          <dl className="about-details"><div><dt>作者</dt><dd>{AUTHOR}</dd></div></dl>
          <section className="about-copy"><h3>让一句话被看见</h3><p>把想表达的话变成醒目的手持灯牌，支持 LED 点阵、文字大小、颜色与连续滚动。适合应援、接人和日常交流。</p><h3>当前版本说明</h3><p>文字编辑与灯牌展示可以直接体验。语音识别和 AI 精简目前使用预设示例，不会开启麦克风，也未加载本地模型；本版本不是可安装的 APK。</p><p>内容仅保存在本次页面会话中，刷新后重置。展示前请核对人名、地点、数字和否定词。</p></section>
          <a className="github-link" href={GITHUB_URL} target="_blank" rel="noopener noreferrer"><GithubLogoIcon size={23} /><span>GitHub 项目<small>{AUTHOR}/liangju-led</small></span><ArrowSquareOutIcon size={19} /></a>
          <button className="primary-button" onClick={() => openPanel("settings")}>返回设置</button>
        </>}
      </div>
    </BottomSheet>
  </div>;
}
