# 亮句 Liangju

说出来，亮出来。面向安卓手持 LED 灯牌应用的交互原型。

作者：[davidren0513](https://github.com/davidren0513) · 项目：[liangju-led](https://github.com/davidren0513/liangju-led)

> 当前版本为浏览器中的安卓界面原型，不是可安装的 APK。语音识别与 AI 精简使用明确标记的预设示例，尚未接入本地模型。

## 功能

- LED 点阵与清晰大字，字号、颜色、速度可调。
- 静止展示、上下左右连续滚动；每轮之间约两个字的间距。
- 手机内全屏展示、横向展示、暂停和继续。
- 原话/智能精简切换、手动编辑及安卓键盘交互。
- 借过、打车、应援三种语音整理流程演示。
- 设置 → 关于：作者、版本说明和 GitHub 链接。

## 预览

<img src="liangju/design/implementation-android-final.jpg" width="320" alt="亮句安卓主界面">

## 本地运行

使用 Node.js 22.12+ 与 npm。

```sh
git clone https://github.com/davidren0513/liangju-led.git
cd liangju-led/liangju
npm ci
npm run dev -- --host 127.0.0.1 --port 4173
```

浏览器访问 `http://127.0.0.1:4173/`。

```sh
npm run build
npm run check:runtime
```

## 结构

- `liangju/src/Prototype.tsx`：应用界面、灯牌渲染与交互。
- `liangju/src/prototype.css`：应用样式。
- `liangju/src/mobile/`：手机预览、键盘与面板组件。
- `liangju/design/`：设计稿与界面验证截图。
- [详细说明](liangju/README.md)

## 后续开发

接入原生安卓工程、麦克风权限、离线语音识别、本地语言模型及模型下载管理。文字纠错、专有名词保留、延迟、功耗与兼容性需要在真机上测试。

本原型的输入只保留在当前页面状态中，刷新即重置。外部 GitHub 链接会打开 GitHub 网站。
