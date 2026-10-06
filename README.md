# 星辰 ⭐

自己完全可控的手机视频 App，基于 webhtv 精简二开。

![Build](https://github.com/w18455615921/xingchen/actions/workflows/main.yml/badge.svg)

## 特性

- 📱 手机竖屏触控，不做 TV
- 🎬 ExoPlayer + mpv 双内核
- 🎨 全新主题系统：普通 / 毛玻璃，壁纸自定义
- 🚫 去 Material 紫，星辰金主题色

## 下载

到 [Releases](../../releases) 页面下载最新 APK。

## 构建

每次 push 自动触发 GitHub Actions 构建，源码来自上游 webhtv + 本仓库 `tools/` 下的补丁脚本。

| 脚本 | 作用 |
|------|------|
| `tools/xingchen-clean-patch.py` | 应用名、图标、主题、资源精简 |
| `tools/xingchen-java-clean.py` | Java 层：主题系统、新 UI |
| `tools/xingchen-settings-write.py` | 设置页 |

## 许可

仅供学习交流。
