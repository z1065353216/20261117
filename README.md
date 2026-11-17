# 小偷公司去广告（LSPosed）1.1.0

针对《小偷公司》Android 包 `com.WoodenStool.ThiefSimulator`（上传样本版本 0.2.9）的 LSPosed 模块源码。

## 1. 本次修复内容

首版对 `com.tapsdk.tapad.group.l` 的广告加载和自动展示方法直接 `return null`。这会让 Unity 游戏端已经置为“广告正在准备/播放”的状态，却收不到任何结束回调，因此按钮可能一直认为广告还在处理中。

1.1.0 修复为：

- 优先 Hook `com.dirichlet.unity.DirichletUnityBridge` 的广告加载和自动展示入口。
- 不再静默吞掉回调；向 Unity/Dirichlet listener 发送 `onError("<NO_AD_FILL code>", "<description>")`。
- 对直接调用 TapTap Dirichlet facade 的情况，补充 `CommonListener.onError(int, String)` 失败回调。
- `preLoad` 仍直接阻止，避免预加载广告。
- 保留广告对象层的展示拦截。
- 不伪造 `onRewardVerify`、`onVideoComplete` 或其他奖励成功回调；激励广告失败时不会发放奖励。

错误码优先从 SDK 的 `ErrorCodeEnum.NO_AD_FILL` 动态读取，避免硬编码版本相关数字。

## 2. 静态分析依据

基于用户上传的 `小偷公司_0.2.9.zip` 中的 DEX：

- Unity 桥接类：`com.dirichlet.unity.DirichletUnityBridge`
- 自动展示入口：`showRewardVideoAutoAd`、`showInterstitialAutoAd`、`showBannerAutoAd`、`showSplashAutoAd`
- Unity 自动展示回调接口包含 `onError(String, String)`
- TapTap Dirichlet facade：`com.tapsdk.tapad.group.l`
- Dirichlet facade 回调继承 `CommonListener.onError(int, String)`
- SDK 错误枚举包含 `NO_AD_FILL`

这份模块是针对上传的 0.2.9 DEX 的定向 Hook，仍需在设备上验证。样本不含 Unity 原生库及完整资源，不能保证覆盖其他 SDK 或未来版本。

## 3. 编译

需要 JDK 17、Android SDK 35、Gradle 8.9：

```bash
gradle --no-daemon :app:assembleDebug
```

生成文件：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Xposed API 使用 `compileOnly`，不会打进 APK；运行时由 LSPosed 提供。

## 4. GitHub Actions 自动编译

工作流位于 `.github/workflows/build.yml`：

- push 到 `main` / `master` 时自动构建
- Pull Request 时自动构建
- 支持 `workflow_dispatch` 手动触发
- 构建完成后上传 `ThiefCompany-AdBlock-debug` Artifact
- Android SDK 初始化只安装仍受支持的 `platform-tools`，避免请求已移除的旧 `tools` 包

## 5. 安装与验证

1. 在 LSPosed 中先停用旧版模块。
2. 如果新 APK 因签名不一致不能覆盖安装，先卸载旧的“去广告模块 APK”（不要卸载游戏），再安装新 APK。
3. 在 LSPosed 中启用新版模块，确认作用域为 `com.WoodenStool.ThiefSimulator`。
4. 强制停止游戏后重新启动。
5. 查看 LSPosed 日志，搜索 `ThiefNoAds`。新版应出现 `sent Unity onError` 或 `sent Dirichlet onError` 日志。
6. 测试广告按钮：预期是不再播放广告，游戏收到“无广告填充/广告不可用”错误回调，等待状态应结束。

## 6. 激励广告说明

本模块只报告广告不可用，不伪造观看完成或奖励验证。因此激励广告按钮不会凭空发放奖励。如果游戏设计为必须看广告才能获得奖励，该奖励仍不会获得；若游戏对 `onError` 没有正确处理，相关界面仍可能需要进一步针对游戏逻辑排查。

若游戏闪退或功能异常，在 LSPosed 中关闭模块并强制停止游戏即可回退；本模块不修改原游戏 APK。
