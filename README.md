# HPU 用电 · Android 桌面小组件

面向高校宿舍的 Android 用电查询应用与桌面小组件。手机**直接请求学校用电查询站点**，不依赖电脑网关、自建 API 或局域网服务。

> 仅供查询本人有权查看的寝室数据。学校站点并非正式公开 API，页面结构变化可能导致解析失效，以学校系统为准。

## 功能

- 双校区支持（北校区 / 南校区），寝室列表离线内置 + 联网校验；
- 照明余额、空调余额、今日用电/费用；
- 近 90 天用电明细 + 自定义时间区间明细；
- 照明充值记录查询；
- 多寝室保存与切换（可批量添加、左右滑动切换）；
- 用电记录导出 CSV（表格，Excel 可直接打开）；
- 桌面小组件（标准版 + 迷你版）；
- 后台自动更新（可设间隔）；
- 低电量提醒（可分别设置照明/空调阈值，可自选提醒寝室）；
- 远程公告 / 更新源；
- 源站连通性测试（ping 延迟）；
- 日志查看与导出。

## 配置项

### 学校站点地址
`app/src/main/java/cn/edu/hpu/powerwidget/SchoolPowerClient.java` 中定义了北校区与南校区的站点地址（经字符串混淆）。如需更换学校站点，请修改该文件。

### 更新源（公告地址）
公告/配置通过「设置 → 更新源」配置：填入一个完整 JSON 地址后保存，App 启动时直接 GET 该地址。留空则关闭公告。

JSON 示例：

```json
{
  "announcements": [
    { "id": "1", "title": "标题", "content": "正文", "level": "info" }
  ],
  "latestVersion": "2.2.22",
  "latestVersionCode": 28,
  "forceUpdate": false,
  "downloadUrl": "https://..."
}
```

### 反馈邮箱
`PreferencesActivity.java` 中的 `FEEDBACK_EMAIL` 为占位符，请替换为你的邮箱。

## 技术栈

- 纯 Java（无第三方运行时依赖、无 AndroidX）；
- minSdk 23 / targetSdk 35；
- 使用 `HttpsURLConnection` 直连学校站点（Cookie / 表单 / 分页）；
- 本地缓存 + JobScheduler 后台刷新 + 通知；
- R8 混淆与字符串加密（发布版）。

## 构建

### 方式一：Android Studio
1. 安装 Android Studio 与 Android SDK Platform 35；
2. 打开项目根目录并完成 Gradle 同步；
3. 构建 `app` 模块，得到 APK。

### 方式二：命令行（SDK 命令行工具）
使用 `aapt2` / `javac` / `d8`（或 `r8`）/ `zipalign` / `apksigner` 手动构建，详见项目内 `proguard-rules.pro` 与 `obfuscation-dictionary.txt`。

## 目录结构

```text
app/src/main/
  AndroidManifest.xml
  assets/room_catalog.json           离线寝室目录
  java/cn/edu/hpu/powerwidget/       源码
  res/                               布局、资源、小组件配置
proguard-rules.pro                   R8 混淆规则
obfuscation-dictionary.txt           混淆短名字典
```

## 许可证
MIT License，见 `LICENSE`。

## 免责声明
本项目仅用于学习与个人查询本人有权访问的数据，请遵守学校相关规定与当地法律法规。
