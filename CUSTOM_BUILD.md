# Jellyfin 弹幕版

这是基于 Jellyfin Android TV `v0.19.9` 制作的自定义稳定版，使用独立包名
`org.jellyfin.androidtv.danmaku`，可与官方版和 Debug 版同时安装。

## 弹幕功能

- 支持在播放设置中填写自定义 `danmu_api` 服务地址
- 自动匹配当前电影或剧集的弹幕
- 自动匹配错误时，可在播放控制栏中手动搜索节目和剧集
- 手动匹配按 Jellyfin 媒体项目保存，也可恢复自动匹配
- 播放时可调整显示区域、透明度、字体大小、滚动速度和时间偏移
- 使用逐帧绘制、文本宽度缓存、时间窗口索引和稳定播放时钟降低卡顿
- 使用轨道调度避免弹幕互相覆盖；弹幕过密时优先丢弃无法安全排列的项目

## 安装与数据

定制版采用独立应用数据目录，首次安装后需要重新登录 Jellyfin，并重新设置弹幕源。
固定签名用于后续覆盖升级；签名密钥和密码文件不会提交到 GitHub。

## 构建

在项目根目录执行：

```powershell
.\gradlew.bat assembleDanmaku
```

构建需要在项目根目录准备以下两个被 Git 忽略的文件：

- `danmaku-signing.properties`
- `signing/jellyfin-danmaku.jks`

输出位于 `app/build/outputs/apk/danmaku/`。

## 上游与许可

本项目基于 [Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv)，
并继续遵循上游项目的 GNU GPL v2.0 许可。
