# EZ记账

面向自托管 [ezBookkeeping](https://github.com/mayswind/ezbookkeeping) 的轻量 Android 客户端。它保留 ezBookkeeping 熟悉的移动端界面，并补充原生连接管理、应用锁、文件交互和移动端体验，让日常记账更接近一款完整的 Android 应用。

> 本项目是非官方客户端，不包含 ezBookkeeping 服务端，也不隶属于 ezBookkeeping 官方项目。使用前需要准备可访问的 ezBookkeeping 实例。

## 特色

### 自动选择更合适的连接

- 同时支持公网地址和多条“Wi-Fi — 本地地址”映射
- 识别当前 Wi-Fi 后自动选择匹配的局域网地址，无需手动切换线路
- 首屏优先复用最近成功连接，测速延后到后台执行
- 网络变化时自动重新评估；通过连续失败确认、冷却时间和性能阈值避免反复跳线
- 连接异常时渐进恢复，可重试当前线路、尝试备用线路或进入设置修正地址
- 旧版单一本地地址会自动迁移为不限定 Wi-Fi 的默认规则

### 为手机优化的使用体验

- WebView 常驻，打开设置或错误恢复页时不丢失当前页面和滚动位置
- 原生顶部加载进度、统一返回逻辑和页面位置恢复
- 单手友好的设置中心与快捷中心，可选择是否显示屏幕边缘快捷入口
- 跟随系统深浅主题，适配状态栏、导航栏、大字体和无障碍触控尺寸
- 支持桌面长按快捷菜单、双指快速双击打开快捷中心

### 本地安全保护

- 支持系统生物识别、四位 PIN 和九宫格图形锁
- PIN 与图形凭据使用随机盐和 PBKDF2 哈希保存，不存储明文
- 可设置后台自动锁定时间和熄屏立即锁定
- 连续输错后启用递增等待，降低暴力尝试风险

### Android 原生能力

- 保持 Cookie 和登录状态
- 支持拍照、图片多选和普通附件上传
- 文件下载到系统“下载/EZ记账”目录，完成后可直接打开
- 可在应用内查看 App 与 ezBookkeeping 服务端版本，并检查 GitHub Releases 更新
- 拒绝无效 HTTPS 证书和 HTTPS 页面中的 HTTP 混合内容

## 快速开始

1. 安装 Release 页面提供的 APK。
2. 首次启动时填写公网地址，或添加一条/多条 Wi-Fi 本地地址映射。
3. 保存后应用会自动寻找当前网络下可用且合适的连接。

地址示例：

- 公网：`https://money.example.com`
- 局域网：`http://192.168.1.100:8080`

公网访问建议使用由可信机构签发的 HTTPS 证书。Android 读取 Wi-Fi 名称需要系统授予附近网络或定位相关权限；权限获批后会在当前界面立即刷新，无需退出应用。

## 快捷操作

在记账页面双指快速双击，可打开快捷中心，执行返回首页、刷新页面、查看连接状态、重新测速、在浏览器打开、立即锁定等操作。

在桌面长按应用图标，可快速进入连接设置、安全设置、立即锁定，或控制屏幕边缘快捷入口是否显示。

## 构建

环境要求：Java 17、Android SDK 35、Gradle 8.9。

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

仓库包含两条 GitHub Actions 工作流：

- **Android CI**：在提交和 Pull Request 上运行单元测试并构建 Debug APK。
- **Signed Android Release**：手动触发或推送 `v*` 标签时，构建、校验证书并上传签名 Release APK 与 SHA-256 文件。

正式签名所需的仓库 Secrets 和证书校验方式见 [SIGNING_SETUP.md](SIGNING_SETUP.md)。

## 项目信息

- 应用名称：EZ记账
- 包名：`com.neo.ezaccounting`
- 当前版本：`1.6.0`（versionCode 21）
- 最低 Android：8.0（API 26）
- 目标 Android：API 35
- 许可证：MIT

## 隐私与安全

服务器地址、安全方式及 PIN/图形锁哈希仅保存在应用私有数据中。账户和账目数据直接在设备与用户配置的 ezBookkeeping 服务端之间传输，本客户端不提供中转服务。

Release 构建使用固定签名证书；证书 SHA-256：

```text
D2:9D:3F:58:1D:1A:90:B1:2A:06:73:17:C6:F7:29:57:83:72:7E:15:73:E7:C2:83:E0:9E:F3:98:CA:64:B7:99
```

客户端代码以 [MIT License](LICENSE) 发布。ezBookkeeping 的名称、图标和服务端代码归原项目及其作者所有。
