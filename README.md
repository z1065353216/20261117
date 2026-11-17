# 小偷公司去广告（LSPosed）1.2.0

针对《小偷公司》Android 包 `com.WoodenStool.ThiefSimulator`，基于用户上传的 0.2.9 DEX 样本定向适配。

## 1. 1.2.0 更新

本版在激励视频展示入口增加客户端奖励回调：

1. 先尝试调用 `onAdShow()`；
2. 调用 `onRewardVerify(true, 1, "reward", 0, "")`；
3. 再调用 `onAdClose()`；
4. 拦截实际视频展示，不启动广告播放。

默认奖励参数在 `Entry.java` 中配置：

```java
private static final int SIMULATED_REWARD_AMOUNT = 1;
private static final String SIMULATED_REWARD_NAME = "reward";
```

如需调整本地回调传递的数值，可修改这两个常量后重新编译。游戏是否采用这些参数发放奖励，取决于游戏自身逻辑。

## 2. 为什么使用 onRewardVerify，而不是 onVideoComplete

检查上传的 0.2.9 DEX 后，实际 Unity 桥接监听器
`com.dirichlet.unity.DirichletUnityBridge$RewardVideoAutoAdListener`
声明了：

```text
onRewardVerify(boolean, int, String, int, String)
onAdClose()
onAdShow()
onError(String, String)
```

该 Unity 桥接监听器没有 `onVideoComplete()`。`onVideoComplete()` 出现在底层
`TapRewardVideoAd$RewardAdInteractionListener`，但它不是这个游戏 Unity 桥接入口使用的回调接口。因此本版优先调用实际桥接接口中的 `onRewardVerify()`。

## 3. Hook 行为

- Unity `showRewardVideoAutoAd`：调用奖励验证及关闭回调，然后阻止真实展示。
- TapTap Dirichlet `showRewardVideoAutoAd`：同样调用奖励验证及关闭回调。
- `DirichletRewardVideoAd` 对象路径：捕获 `setRewardAdInteractionListener()` 注册的监听器，在 `showRewardVideoAd()` 时调用奖励回调并阻止展示。
- 激励视频加载入口允许继续执行，以便 SDK 创建广告对象和监听器；因此该路径仍可能产生广告请求/加载流量，但不会播放视频。
- 开屏、插屏、横幅、信息流等其他广告仍按原策略阻止展示或返回无广告错误。

## 4. 重要限制

这只是客户端本地回调模拟，不代表真实观看，也不会生成广告平台的有效展示记录、签名或服务端验证凭证。若游戏服务端校验广告回调或奖励订单，服务端可能拒绝奖励。使用修改模块可能违反游戏规则，并有账号或存档风险；建议仅用于离线测试或你有权测试的环境。

## 5. 编译

需要 JDK 17、Android SDK 35、Gradle 8.9：

```bash
gradle --no-daemon :app:assembleDebug
```

APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Xposed API 使用 `compileOnly`，由 LSPosed 在运行时提供。

## 6. GitHub Actions

`.github/workflows/build.yml` 支持 push、Pull Request 和手动触发，构建后上传 `ThiefCompany-AdBlock-debug` Artifact。Android SDK 初始化只安装仍受支持的 `platform-tools`，不再请求已移除的旧 `tools` 包。

## 7. 安装与验证

1. 在 LSPosed 中停用旧版模块。
2. 安装新版 APK；若签名不一致，先卸载旧的模块 APK（不要卸载游戏）。
3. 在 LSPosed 中启用模块，并确认作用域为 `com.WoodenStool.ThiefSimulator`。
4. 强制停止游戏后重新启动。
5. 查看 LSPosed 日志，搜索 `[ThiefNoAds]`，应看到 `sent onRewardVerify`，并尽可能看到 `sent onAdClose`。
6. 测试激励视频按钮，确认广告没有实际播放、游戏界面能否结束等待状态，以及游戏是否实际发放奖励。

如果闪退或状态异常，在 LSPosed 中停用模块并强制停止游戏即可回退。本模块不修改原游戏 APK。
