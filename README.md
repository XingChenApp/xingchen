# 星辰播放器

![Build](https://github.com/XingChenApp/xingchen/actions/workflows/main.yml/badge.svg)

基于 webhtv 二次开发的手机版视频播放器，ExoPlayer + IJK + mpv 三内核。

## 特性

- 三内核播放：ExoPlayer / IJK / mpv
- 星辰金主题 UI（#f0a400）
- 毛玻璃界面风格（可调透明度）
- 自定义壁纸（默认/本地/URL）
- 去 Material 紫，全部星辰化

## 下载

到 [Releases](https://github.com/XingChenApp/xingchen/releases) 下载最新 APK，或到 [Actions](https://github.com/XingChenApp/xingchen/actions) 下载构建产物。

## 构建

推送到 main 分支后 GitHub Actions 自动构建，或手动触发 workflow_dispatch。

```bash
./gradlew :app:assembleMobileArm64_v8aRelease
```

## 补丁说明

`tools/` 目录下是星辰定制补丁：
- `xingchen-clean-patch.py`：基础清理和主题补丁
- `xingchen-settings-write.py`：设置页重写
- `xingchen-java-clean.py`：Java 层清理
- `poster_shanjian.jpg`：默认壁纸
- `*.xml`：卡片和分段选择器样式

源码已包含所有补丁，直接构建即可。
