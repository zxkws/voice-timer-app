# Voice Timer

Android 原生语音计时器：说“计时10秒”，倒计时结束后响铃。

## 当前能力

- Android SpeechRecognizer 中文语音识别
- 本地规则语义解析，不依赖云端 LLM
- 秒 / 分钟 / 小时组合计时
- 前台服务保证退到后台后继续计时
- 到时响铃、振动、通知
- 每 12 小时检查 GitHub Release 更新
- Root 设备优先通过 pm install 静默更新
- 普通设备自动下载后调用系统安装器
- GitHub Actions 每次推送 main 自动构建并发布签名 APK

## 示例

- 计时10秒
- 倒计时3分钟
- 1分30秒
- 取消计时
- 再来一次

## 本地构建

需要 JDK 17 和 Android SDK 35，执行 `./gradlew assembleDebug`。

## 更新说明

普通 Android 应用没有系统级静默安装权限。本项目优先尝试 Root 静默安装；没有相应权限时自动降级到系统安装确认。企业 Device Owner / 系统签名安装通道后续可继续接入。

## GitHub 自动发布签名

本机签名材料保存在 `~/.config/voice-timer-app/`，不会提交进 Git。首次配置 GitHub Actions 签名时执行 `bash scripts/setup-github-signing.sh`。
