# 亮句 Android

原生 Kotlin 应用，Android 14+（API 34），arm64-v8a。界面使用 Android View / Canvas，自适应窗口尺寸，折叠屏展开后限制编辑区宽度，全屏灯牌使用整个可用区域。玻璃效果由半透明渐变、光晕和边缘高光实现，LED 文字保持清晰。

## 构建

需要 JDK 17、Python 3.10+、Android SDK 35、Build Tools 35.0.0、NDK 27.2.12479018、CMake 3.22.1。

```sh
python3 scripts/prepare_native.py
# 通过 Android Studio 配置 SDK，或设置 ANDROID_HOME / local.properties。
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

安装包生成到 `app/build/outputs/apk/debug/app-debug.apk`。这是调试签名的测试包，不是应用商店正式发布包。项目未包含签名密钥、下载后的模型或 Android SDK。

`prepare_native.py` 下载并校验固定版本 sherpa-onnx AAR 和 llama.cpp 源码。引擎使用 CPU，限制为四线程，避免依赖某一厂商的 GPU / NPU 接口。

## 使用

1. 安装后即可点击“说话”，首次授权麦克风，默认直接转为文字，不下载 AI 模型。
2. 语音优先使用手机配置的系统识别服务，可能联网。若没有可调用的服务，可使用键盘语音输入；也可在设置中单独下载 240 MB 离线语音模型作为备用。
3. 需要纠错、精简时，在“设置”打开“启用 AI 纠错精简”。首次自动弹出真实下载进度，默认轻量模型约 491 MB，建议使用 Wi-Fi。取消或失败会恢复关闭，保留断点以便再次开启时继续。
4. 模型已下载时再次开启不重复下载；关闭后即使模型仍在，也不运行 AI。开关状态会保存。
5. 检查原话或 AI 结果，可编辑、恢复原话，然后点击“确认文字，立即亮屏”。

模型不放入 APK，也不会在启动时自动下载。模型管理可切换增强 1.5B（约 1.12 GB）或从系统文件选择器导入文件。只需下载所选 AI，无需同时下载离线语音。AI 完全本地运行；系统语音由手机服务决定是否联网，不能将系统语音描述为保证离线。单次录音最长 20 秒。

## 实现

- `ModelStore.kt`：固定版本清单、HTTPS 下载、续传、长度 / SHA-256 校验、本地文件导入、取消和空间检查。
- `SystemVoiceEngine.kt`：系统 SpeechRecognizer，中文识别、结果 / 错误处理、20 秒停止录音、35 秒结果超时及取消释放。
- `VoiceEngine.kt`：AudioRecord 16 kHz 单声道 PCM → SenseVoice；释放录音器、流与识别器。
- `AiEngine.kt`：ChatML 提示词、JSON 结果检查、数字 / 否定词 / 常见人物关系保护与原话回退。
- `cpp/engine.cpp`：llama.cpp CPU 推理，GBNF 约束输出 JSON；45 秒截止、取消、RAII 资源释放。每次推理结束释放模型内存。调试 APK 也开启 C++ 优化，避免未优化矩阵运算拖慢推理。
- `LedView.kt`：缓存文字位图、点阵生成、双向连续平铺滚动。字体 / 颜色 / 文本变更才重建位图。
- `MainActivity.kt`：权限、下载、录音与 AI 状态；用户确认、玻璃界面、全屏、旋转和持久化。

小模型不保证完全正确，数字检查与否定词检查只做保守防护。原话超过 80 字时显示字段截取前 80 字，完整转写仍保留在当前页面的“原话”中。AI 输入上限 300 字，输出上限 80 字；用户始终需要确认。

## 测试

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

单元测试覆盖有效短句、数字遗漏 / 改写、否定含义丢失、异常 JSON、控制字符和提示词分隔符。设备测试直接调用 JNI 生成并解析 JSON，不能用超时回退代替成功；还检查损坏文件导入不会覆盖已验证模型、首页主要按钮可见性。安装模型与语音样本后再运行实际推理测试。未安装模型的相关测试会跳过，不能据此声称推理通过。

主机模型验证可编译同一 C++ 推理文件：

```sh
cmake -S app/src/main/cpp -B build/host -DCMAKE_BUILD_TYPE=Release
cmake --build build/host -j 4
python3 -m pip install sherpa-onnx==2.0.1 soundfile
python3 scripts/smoke_models.py /path/to/models build/host/liangju-smoke /path/to/zh.wav
```

模型清单：[models.json](app/src/main/assets/models.json)。固定来源和校验值用于复现实验，勿将测试结果视为 vivo / 荣耀真机测速。

系统语音 API 依据 [Android SpeechRecognizer 文档](https://developer.android.com/reference/android/speech/SpeechRecognizer)，包含 Android 11+ 服务可见性声明与销毁资源处理。系统服务可能将音频发往服务器，应用设置中已提示。
