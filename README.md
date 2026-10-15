# 留迹（liuji）

本机使用时长统计与界面文字快照记录的 Android 应用。可选 WebDAV / ima 云同步；日常使用不依赖作者私有服务。

## 下载安装

在 [Releases](https://github.com/aoqing16/liuji/releases) 下载最新 `apk`，安装到 Android 手机即可。

系统可能提示「未知来源」：按系统指引允许安装即可。

## 首次使用：需要手动开启的权限

安装后请按需打开下列权限，否则对应功能无法工作：

1. **使用情况访问权限（Usage Access）**  
   系统设置 → 应用 → 特殊应用权限 / 使用情况访问权限 → 找到「留迹」并开启。  
   用于统计各应用使用时长。

2. **无障碍服务（画面记录）**  
   系统设置 → 无障碍 → 已安装的服务 / 已下载的应用 → 找到「留迹」并开启。  
   用于「画面记录」采集当前界面可见文字；数据默认只保存在本机。

3. **通知权限（Android 13+）**  
   若系统弹窗询问，请允许。前台录制服务会用到通知。

4. **（可选）云同步**  
   - WebDAV（如坚果云）：在 App 设置中填写账号与应用密码。字段说明见 `config/webdav_account.example.json`。  
   - ima 知识库：在 App 设置中填写 clientId / apiKey 等。字段说明见 `config/ima_api_config.example.json`。

部分厂商还有电池优化、自启动限制，若后台统计中断，请在系统设置里为「留迹」关闭电池限制或允许自启动。

## 从源码编译

环境：JDK 17+、Android SDK（建议通过 Android Studio 安装）。

```bash
# 配置 SDK 路径（勿提交）
cp local.properties.example local.properties   # 或自行创建，写入 sdk.dir=...

# 调试包
./gradlew :app:assembleDebug

# 签名发布包（需先按下方说明配置密钥）
./gradlew :app:assembleRelease
```

Windows 使用 `gradlew.bat`。

### 签名配置（本地，不进仓库）

1. 复制 `keystore.properties.example` 为 `keystore.properties` 并填写。  
2. 将密钥库文件放在 `signing/`（该目录已忽略）。  
3. 再执行 `assembleRelease`。

## 许可证

[MIT](LICENSE)

## 贡献

当前仓库仅公开源码与安装包，**暂不接受 Issue / Pull Request 合入**。你可以自行 fork 修改；再分发时请保留 MIT 许可证与版权声明。
