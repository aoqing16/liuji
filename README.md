# 留迹（liuji）

**留迹**是一款 Android 本机使用记录应用：用系统「使用情况」统计各 App 时长，再用无障碍能力按需记录前台界面上的**可见文字**（不是截屏），让你事后能回看「当时在看什么」，而不仅是「开了多久」。

日常使用数据默认只留在手机上；可选 WebDAV / ima 同步。不依赖作者私有服务器。

## 与 Hindsight 的关系

本项目是 [Hindsight](https://github.com/Tomotsugu-dev/Hindsight)（MIT）的**衍生项目**。

[Hindsight](https://github.com/Tomotsugu-dev/Hindsight) 是本机优先的**桌面端**活动追踪器：统计电脑上时间花在哪里，支持端侧 AI 日总结与可选多设备同步。它目前缺少完整的**手机端**使用统计与回顾。

留迹补上这一侧：

- **手机本机**：在 Android 上独立统计使用时长，并可选记录界面文字
- **电脑看板**：手机上的「电脑看板」数据来自你在电脑上运行的 Hindsight 统计结果（经双方约定的 WebDAV 等云盘路径只读同步），留迹**不会**把手机数据写回该云盘
- 二者配合后，可在手机上同时查看「手机怎么用」与「电脑怎么用」

使用电脑看板前，请先在电脑上安装并配置 [Hindsight](https://github.com/Tomotsugu-dev/Hindsight)，再在留迹设置里填入相同的云盘账号。

## 能做什么

- **使用时长看板**：日 / 周 / 月汇总，按时段拆分，按应用与分类排行
- **自定义分类**：给应用归类（社交、工作、娱乐等），看清时间花在哪一类
- **时间线**：按切换会话回看当天打开过哪些应用、各用了多久
- **画面记录**：在授权无障碍后，记录前台界面的可见文字与大致位置；支持「有操作时」采集（更省电），可进应用详情回看帧视图
- **导出**：把汇总、时间线与画面文字导出为便于阅读 / 给 AI 分析的 Markdown（可设「隐私 App」，导出时去掉其画面文本）
- **电脑看板（可选）**：只读拉取 [Hindsight](https://github.com/Tomotsugu-dev/Hindsight) 在电脑上写入云盘的使用数据；不会写回云盘
- **ima 知识库同步（可选）**：把日笔记推到你自己的 ima 知识库

## 和同类应用差在哪

多数「数字健康 / App 使用统计」只回答「开了多久」。留迹额外补一层**界面文字痕迹**，并作为 [Hindsight](https://github.com/Tomotsugu-dev/Hindsight) 的手机侧补充：

| | 常见使用统计 App | 留迹 |
|---|---|---|
| 核心数据 | 时长、次数、锁屏时间 | 时长 + **界面可见文字快照** |
| 回顾方式 | 图表与排行 | 图表 / 时间线 / **按应用回看画面文字** |
| 隐私默认 | 常绑定账号或云服务 | **默认本机**；云同步可选且用你自己的盘 / 知识库 |
| 导出 | 少见或格式封闭 | **Markdown 导出**，方便自己存档或给 AI |
| 跨端 | 多为手机单端 | **手机自统计** + 可选只读查看 **Hindsight 电脑数据** |
| 许可 | 多为闭源 | **MIT 开源**（衍生自 Hindsight，可自编译安装） |

画面记录会读当前界面文字，请只在信任本机、并按需开启；游戏等可用黑名单跳过录制。

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

4. **（可选）电脑看板 / 云同步**  
   - 电脑看板：先在电脑运行 [Hindsight](https://github.com/Tomotsugu-dev/Hindsight) 并开启其云盘同步，再在留迹设置中填写**同一套** WebDAV（如坚果云）账号。字段说明见 `config/webdav_account.example.json`。  
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

本项目衍生自 [Hindsight](https://github.com/Tomotsugu-dev/Hindsight)（Copyright (c) 2026 Tomotsugu，MIT）。请一并遵守其许可证条款。

## 贡献

当前仓库仅公开源码与安装包，**暂不接受 Issue / Pull Request 合入**。你可以自行 fork 修改；再分发时请保留 MIT 许可证与版权声明。
