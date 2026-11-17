# 小偷公司去广告（LSPosed）

针对《小偷公司》Android 包 `com.WoodenStool.ThiefSimulator`（上传样本版本 0.2.9）的 LSPosed 模块源码。

## 静态分析依据

本项目依据用户上传的 `小偷公司_0.2.9.zip` 编写。压缩包中包含 `AndroidManifest.xml`、`classes.dex`、`classes2.dex`、`classes3.dex`。其中可识别到：

- TapTap TapAd API：`com.tapsdk.tapad.*`
- TapTap Dirichlet 聚合广告 API：`com.tapsdk.tapad.group.*`
- 广告对象实现类：TapAd 的 reward/interstitial/splash/banner/feed，以及 Dirichlet 对应对象
- 聚合适配组件包含穿山甲、优量汇、百度等类

上传样本没有包含完整 APK 的 `lib/` 原生库、Unity 资源和其他资产，所以这是一份基于已见 Java SDK 接口的定向拦截模块，不代表已经完成设备上的动态验证，也不能保证覆盖未来版本或未包含在样本中的其他广告入口。

## 拦截策略

模块只在 `com.WoodenStool.ThiefSimulator` 进程中加载，并针对 TapTap 广告门面及广告对象做 Hook：

- 拦截开屏、插屏、横幅、信息流、激励视频的广告加载入口
- 拦截 Dirichlet 自动展示和预加载入口
- 对广告对象的 `isValid()` 返回 `false`
- 阻止广告对象的 `show()` / `render()` / 视图注册入口
- 不拦截游戏的全局网络，不修改游戏 APK
- 不伪造激励视频完成、奖励验证或游戏货币，不绕过付费内容

### 激励广告说明

激励视频被阻止后不会播放，也不会自动发放奖励。若某个游戏功能必须依赖真实激励广告，相关按钮可能无法完成操作；请关闭模块或使用游戏自身提供的非广告路径。

### 如果游戏界面卡在等待广告

源码中的 `Entry.java` 有一个开关：

```java
private static final boolean BLOCK_LOAD_REQUESTS = true;
```

- `true`：同时阻止广告加载请求和展示（默认，拦截更彻底）。
- `false`：允许广告加载，但仍将广告对象标记为无效并阻止展示；某些依赖加载回调的界面可能更容易继续，但广告网络请求仍可能发生。

修改后重新编译并安装模块。

## 编译

需要 JDK 17、Android SDK 35、Gradle 8.9：

```bash
gradle --no-daemon :app:assembleDebug
```

生成文件：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Xposed API 使用 `compileOnly`，不会被打进 APK；运行时由 LSPosed 提供。

## GitHub Actions 自动编译

工作流位于 `.github/workflows/build.yml`：

- push 到 `main` / `master` 时自动构建
- Pull Request 时自动构建
- 支持 Actions 页面中的 `workflow_dispatch` 手动触发
- 构建完成后上传 `ThiefCompany-AdBlock-debug` Artifact

使用方法：

1. 将本项目全部文件上传到自己的 GitHub 仓库。
2. 打开仓库的 **Actions** 页面。
3. push 或手动运行 **Build LSPosed module**。
4. 在成功的 workflow run 页面底部下载 `ThiefCompany-AdBlock-debug`。
5. 解压后安装 `app-debug.apk`。

## 安装与启用

1. 安装编译得到的 `app-debug.apk`。
2. 打开 LSPosed 管理器，在模块列表中启用“小偷公司去广告（LSPosed）”。
3. 确认作用域包含 `com.WoodenStool.ThiefSimulator`。
4. 强制停止游戏后重新打开；如果仍未生效，再重启设备。
5. 可在 LSPosed 日志中搜索 `ThiefNoAds`，确认是否找到并 Hook 到目标方法。

## 兼容性与回退

- 当前目标：上传样本所对应的 0.2.9 Java SDK 结构。
- 游戏或广告 SDK 更新后，混淆类名/方法可能变化，需要重新分析 DEX 并更新 Hook 列表。
- 若游戏闪退、广告相关 UI 卡住或功能异常，在 LSPosed 中关闭本模块并强制停止游戏即可回退；本模块不修改原游戏 APK。
