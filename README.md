# EZ记账

**EZ记账**是一款面向自托管 [ezBookkeeping](https://github.com/mayswind/ezbookkeeping) 的轻量 Android 客户端。应用以沉浸式 WebView 承载原始移动端界面，不在记账页面额外叠加标题栏或工具栏。

> 本项目是非官方客户端，不包含 ezBookkeeping 服务端，也不隶属于 ezBookkeeping 官方项目。

## 主要功能

- 全屏显示 ezBookkeeping 原始界面
- 首次启动可同时配置本地地址和公网地址
- 启动时优先检测本地线路，本地不可用时自动切换公网线路
- 页面首次加载失败时自动尝试备用线路
- 保持 Cookie 和登录状态
- 下拉页面刷新
- 双指长按页面打开隐藏功能菜单
- 支持图片、账单和附件上传
- 支持文件下载到 Android“下载”目录
- 返回键优先返回网页上一页，首页再次返回时退出应用
- 外部域名和非 HTTP 链接交给系统应用打开
- 不绕过无效 HTTPS 证书

## 隐藏功能菜单

在记账页面上使用**双指长按**，可打开隐藏菜单：

- 返回首页
- 重新检测线路
- 在系统浏览器中打开
- 更换本地/公网地址
- 清除登录与缓存
- 查看系统 WebView 信息

## 使用要求

- Android 8.0 或更高版本
- 已部署并可访问的 ezBookkeeping 服务端
- 公网访问建议使用具有有效证书的 HTTPS 域名

## 首次配置

应用允许填写一个或两个地址：

- **本地地址**：例如 `http://192.168.1.100:8080`
- **公网地址**：例如 `https://money.example.com`

两个地址都填写时，每次启动会先检测本地线路；本地连接失败后再尝试公网线路。只填写其中一个地址也可以正常使用。

## 自动构建

仓库包含两条 Android 工作流：

- **Android CI**：在提交和 PR 时构建 Debug APK，用于开发测试。
- **Signed Android Release**：通过手动运行或推送 `v*` 标签，使用固定签名证书构建正式 Release APK。

本地调试构建需要 Java 17、Android SDK 35 和 Gradle 8.9：

```bash
gradle --no-daemon :app:assembleDebug
```

## 项目信息

- 应用名称：EZ记账
- 包名：`com.neo.ezaccounting`
- 当前版本：`1.1.0`
- 最低 Android：8.0（API 26）
- 目标 Android：API 35

## 固定签名

Release 构建已强制使用固定签名证书。缺少签名变量时，Gradle 会中止 Release 构建，避免误发布不同证书签名的 APK。

固定签名证书 SHA-256 指纹：

```text
D2:9D:3F:58:1D:1A:90:B1:2A:06:73:17:C6:F7:29:57:83:72:7E:15:73:E7:C2:83:E0:9E:F3:98:CA:64:B7:99
```

GitHub Secrets 配置和正式构建方式见 [SIGNING_SETUP.md](SIGNING_SETUP.md)。

> 旧测试版 APK 使用 Debug 签名，首次安装固定签名版时需要卸载旧测试版。从固定签名版开始，后续版本可在版本号递增后直接覆盖升级。

## 隐私

服务器地址只保存在手机本地。账号和账目数据直接在手机与用户配置的 ezBookkeeping 服务端之间传输，本客户端不提供中转服务。

## 许可证

本客户端代码采用 MIT License 发布。ezBookkeeping 及其名称、图标和服务端代码归原项目及其作者所有。
