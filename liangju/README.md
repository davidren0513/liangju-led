# 亮句 · 安卓语音灯牌界面原型

基于用户选定的第 1 版设计制作。默认展示 Pixel 10 安卓手机，采用黑底、荧光黄主色及可实时渲染的 LED 点阵文字。

作者：[davidren0513](https://github.com/davidren0513) · [GitHub 项目](https://github.com/davidren0513/liangju-led)

## 已完成

- 设置末尾的“关于”：作者、版本说明、GitHub 项目链接。
- 主界面：灯牌预览、原话与精简结果切换、文字编辑。
- 展示：静止、向左、向右、向上、向下滚动；暂停/继续；横向文字展示；返回编辑。
- 外观：6 种颜色、5 档速度、点阵/清晰大字、字号。
- 语音流程演示：借过、打车、应援三个明确标记的预设示例。可预览结果并确认上屏，应援示例同时修改颜色和方向。
- 手动输入：最多 80 字、空白校验、安卓键盘避让。

## 范围

这是可交互的前端设计原型，不是原生安卓应用，也不包含 APK。语音与 AI 使用预设示例，不会打开麦克风、下载或运行模型。内容仅保存在当前界面状态中，刷新后重置。全屏与横屏按钮模拟手机内的展示状态，不修改真实设备亮度或系统方向。

后续安卓实现可依据此原型衔接原生界面、录音权限、离线语音识别、本地模型和模型下载管理；识别准确率、推理速度、耗电和机型兼容性尚未验证。

## 本地运行

```sh
npm ci
npm run dev -- --host 127.0.0.1 --port 4173
```

```sh
npm run build
npm run check:runtime
```

应用界面位于 `src/Prototype.tsx` 与 `src/prototype.css`。手机框架与键盘组件沿用模板。图标使用 Phosphor（MIT）；LED 点阵是动态文字渲染，非截图贴图。

## 设计与验证

- `design/source-option-1.png`：用户选定的设计稿。
- `design/implementation-android-final.jpg`：安卓界面实测截图。
- `design/comparison-final.jpg`：整体设计对照。
- `design/edit-keyboard-final.jpg`：键盘编辑状态。
- `design/fullscreen-final.jpg`：横向灯牌展示。
- `design-qa.md`：视觉检查与交互验证记录。
