# EZ记账固定签名配置

本项目使用 GitHub Actions Secrets 注入固定 Android 签名证书。签名私钥不得提交到仓库。

## 必需的 GitHub Actions Secrets

在仓库中依次打开：

`Settings → Secrets and variables → Actions → New repository secret`

创建以下四个 Secret：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

具体值保存在离线签名备份包的 `github-secrets.txt` 中。

## 构建正式签名 APK

打开：

`Actions → Signed Android Release → Run workflow`

工作流会：

1. 从 Secret 还原 keystore；
2. 使用固定证书构建 Release APK；
3. 使用 `apksigner` 验证 APK；
4. 输出证书 SHA-256 指纹；
5. 上传 APK、SHA-256 校验文件和签名证书信息。

## 重要提醒

- 当前测试版 APK 使用 Debug 签名，无法直接覆盖安装首个固定签名版。首次切换到固定签名版时需要卸载旧测试版。
- 从首个固定签名版开始，后续版本只要保持包名不变、版本号递增且使用同一 keystore，即可直接覆盖升级。
- keystore 丢失后，无法继续为已安装用户提供可覆盖升级的 APK。
- 固定签名证书的 SHA-256 指纹应长期保持不变。
