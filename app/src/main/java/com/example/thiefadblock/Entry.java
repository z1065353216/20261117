package com.example.thiefadblock;

import android.app.Application;
import android.content.Context;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed module for Thief Company 0.2.9.
 *
 * Important difference from the first build: ad entry points are no longer
 * silently swallowed. The Unity/Dirichlet callbacks receive a normal NO_AD_FILL
 * error so the game's "preparing/playing" state can be cleared. Reward success
 * callbacks are never generated.
 */
public final class Entry implements IXposedHookLoadPackage {
    private static final String TAG = "[ThiefNoAds]";
    private static final String TARGET_PACKAGE = "com.WoodenStool.ThiefSimulator";
    private static final String UNITY_BRIDGE = "com.dirichlet.unity.DirichletUnityBridge";
    private static final String TAP_GROUP_NATIVE = "com.tapsdk.tapad.group.l";
    private static final String FALLBACK_ERROR_MESSAGE = "No ad fill (blocked by LSPosed)";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static volatile int noAdFillCode = -1;
    private static volatile String noAdFillCodeString = "-1";
    private static volatile String noAdFillMessage = FALLBACK_ERROR_MESSAGE;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + " loaded: package=" + lpparam.packageName
                + ", process=" + lpparam.processName);

        XposedHelpers.findAndHookMethod(
                Application.class,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Context context = (Context) param.args[0];
                            installHooks(context.getClassLoader());
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + " failed to get target ClassLoader: " + t);
                        }
                    }
                }
        );
    }

    private static void installHooks(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }

        resolveNoAdFillError(classLoader);
        int hooked = 0;

        // The Unity bridge is the game's actual entry point. Send its listener
        // an error instead of returning silently and leaving its spinner active.
        hooked += hookCallbackString(classLoader, UNITY_BRIDGE, "loadBannerAd", 1);
        hooked += hookCallbackString(classLoader, UNITY_BRIDGE, "loadInterstitialAd", 1);
        hooked += hookCallbackString(classLoader, UNITY_BRIDGE, "loadRewardVideoAd", 1);
        hooked += hookCallbackString(classLoader, UNITY_BRIDGE, "loadSplashAd", 1);

        hooked += hookCallbackString(classLoader, UNITY_BRIDGE, "showBannerAutoAd", 2);
        hooked += hookCallbackString(classLoader, UNITY_BRIDGE, "showInterstitialAutoAd", 1);
        hooked += hookCallbackString(classLoader, UNITY_BRIDGE, "showRewardVideoAutoAd", 1);
        hooked += hookCallbackString(classLoader, UNITY_BRIDGE, "showSplashAutoAd", 2);

        // Generic Unity bridge path: report that the ad is not available.
        hooked += hookReturnFalse(classLoader, UNITY_BRIDGE, "isAdValid", "showAd");
        hooked += hookNoOp(classLoader, UNITY_BRIDGE, "preLoad");

        // Fallback for callers that use TapTap's Dirichlet facade directly.
        // These listeners inherit CommonListener.onError(int, String).
        hooked += hookCallbackInt(classLoader, TAP_GROUP_NATIVE, "loadBannerAd", 1);
        hooked += hookCallbackInt(classLoader, TAP_GROUP_NATIVE, "loadExpressFeedAd", 1);
        hooked += hookCallbackInt(classLoader, TAP_GROUP_NATIVE, "loadInterstitialAd", 1);
        hooked += hookCallbackInt(classLoader, TAP_GROUP_NATIVE, "loadNativeFeedAd", 1);
        hooked += hookCallbackInt(classLoader, TAP_GROUP_NATIVE, "loadRewardVideoAd", 1);
        hooked += hookCallbackInt(classLoader, TAP_GROUP_NATIVE, "loadSplashAd", 1);

        hooked += hookCallbackInt(classLoader, TAP_GROUP_NATIVE, "showBannerAutoAd", 2);
        hooked += hookCallbackInt(classLoader, TAP_GROUP_NATIVE, "showInterstitialAutoAd", 2);
        hooked += hookCallbackInt(classLoader, TAP_GROUP_NATIVE, "showRewardVideoAutoAd", 2);
        hooked += hookCallbackInt(classLoader, TAP_GROUP_NATIVE, "showSplashAutoAd", 2);
        hooked += hookNoOp(classLoader, TAP_GROUP_NATIVE, "preLoad");

        // TapAd object-level fallback: an ad object must not render if a caller
        // bypasses the Unity/Dirichlet bridge. No reward-completion is faked.
        hooked += hookReturnFalse(classLoader, "com.tapsdk.tapad.internal.b0", "isValid");
        hooked += hookNoOp(classLoader, "com.tapsdk.tapad.internal.b0", "showRewardVideoAd");

        hooked += hookReturnFalse(classLoader, "com.tapsdk.tapad.internal.O", "isValid");
        hooked += hookNoOp(classLoader, "com.tapsdk.tapad.internal.O", "show");

        hooked += hookReturnFalse(classLoader, "com.tapsdk.tapad.internal.i0", "isValid");
        hooked += hookNoOp(classLoader, "com.tapsdk.tapad.internal.i0", "show");
        hooked += hookReturnNull(classLoader, "com.tapsdk.tapad.internal.i0", "getSplashView");

        hooked += hookReturnFalse(classLoader, "com.tapsdk.tapad.internal.B", "isValid");
        hooked += hookNoOp(classLoader, "com.tapsdk.tapad.internal.B", "show");
        hooked += hookReturnNull(classLoader, "com.tapsdk.tapad.internal.B", "getBannerView");

        for (String feedClass : new String[]{
                "com.tapsdk.tapad.internal.J",
                "com.tapsdk.tapad.internal.U"}) {
            hooked += hookReturnFalse(classLoader, feedClass, "isValid");
            hooked += hookReturnNull(classLoader, feedClass, "getAdView");
            hooked += hookNoOp(classLoader, feedClass,
                    "render", "registerViewForInteraction");
        }

        // Dirichlet mediated ad objects.
        hooked += hookReturnFalse(classLoader,
                "com.tapsdk.tapad.group.ads.DirichletRewardVideoAd", "isValid");
        hooked += hookNoOp(classLoader,
                "com.tapsdk.tapad.group.ads.DirichletRewardVideoAd", "showRewardVideoAd");

        hooked += hookReturnFalse(classLoader,
                "com.tapsdk.tapad.group.ads.DirichletInterstitialAd", "isValid");
        hooked += hookNoOp(classLoader,
                "com.tapsdk.tapad.group.ads.DirichletInterstitialAd", "show");

        hooked += hookNoOp(classLoader,
                "com.tapsdk.tapad.group.ads.DirichletSplashAd", "show");

        hooked += hookReturnFalse(classLoader,
                "com.tapsdk.tapad.group.ads.DirichletBannerAd", "isValid");
        hooked += hookNoOp(classLoader,
                "com.tapsdk.tapad.group.ads.DirichletBannerAd", "show");
        hooked += hookReturnNull(classLoader,
                "com.tapsdk.tapad.group.ads.DirichletBannerAd", "getBannerView");

        hooked += hookReturnFalse(classLoader,
                "com.tapsdk.tapad.group.ads.DirichletExpressFeedAd", "isValid");
        hooked += hookNoOp(classLoader,
                "com.tapsdk.tapad.group.ads.DirichletExpressFeedAd", "render");

        hooked += hookReturnNull(classLoader,
                "com.tapsdk.tapad.group.ads.DirichletNativeFeedAd", "getAdView");
        hooked += hookNoOp(classLoader,
                "com.tapsdk.tapad.group.ads.DirichletNativeFeedAd",
                "registerViewForInteraction");

        XposedBridge.log(TAG + " installed " + hooked + " method hooks; NO_AD_FILL="
                + noAdFillCode + ", message=" + noAdFillMessage);
        if (hooked == 0) {
            XposedBridge.log(TAG + " WARNING: no target methods were found; "
                    + "the game's SDK may have changed.");
        }
    }

    private static void resolveNoAdFillError(ClassLoader classLoader) {
        try {
            Class<?> errorEnum = XposedHelpers.findClass(
                    "com.tapsdk.tapad.constants.ErrorCodeEnum", classLoader);
            Object noFill = XposedHelpers.getStaticObjectField(errorEnum, "NO_AD_FILL");
            Object code = XposedHelpers.callMethod(noFill, "getCode");
            Object desc = XposedHelpers.callMethod(noFill, "getDesc");
            if (code instanceof Number) {
                noAdFillCode = ((Number) code).intValue();
                noAdFillCodeString = String.valueOf(noAdFillCode);
            }
            if (desc instanceof String && !((String) desc).isEmpty()) {
                noAdFillMessage = (String) desc;
            }
        } catch (Throwable t) {
            // -1 is still an error, never a success/reward callback.
            XposedBridge.log(TAG + " could not resolve NO_AD_FILL enum; using -1: " + t);
        }
    }

    /** Hook a Unity bridge method whose callback uses onError(String, String). */
    private static int hookCallbackString(ClassLoader classLoader,
                                          String className,
                                          String methodName,
                                          final int callbackIndex) {
        return hookMethods(classLoader, className, new String[]{methodName},
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Object callback = callbackAt(param, callbackIndex);
                        if (callback != null) {
                            try {
                                XposedHelpers.callMethod(callback, "onError",
                                        noAdFillCodeString, noAdFillMessage);
                                XposedBridge.log(TAG + " sent Unity onError for " + methodName);
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + " callback failed for " + methodName + ": " + t);
                            }
                        }
                        param.setResult(null);
                    }
                });
    }

    /** Hook a TapTap Dirichlet method whose callback uses onError(int, String). */
    private static int hookCallbackInt(ClassLoader classLoader,
                                       String className,
                                       String methodName,
                                       final int callbackIndex) {
        return hookMethods(classLoader, className, new String[]{methodName},
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Object callback = callbackAt(param, callbackIndex);
                        if (callback != null) {
                            try {
                                XposedHelpers.callMethod(callback, "onError",
                                        noAdFillCode, noAdFillMessage);
                                XposedBridge.log(TAG + " sent Dirichlet onError for " + methodName);
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + " callback failed for " + methodName + ": " + t);
                            }
                        }
                        param.setResult(null);
                    }
                });
    }

    private static Object callbackAt(XC_MethodHook.MethodHookParam param, int index) {
        return param.args != null && index >= 0 && index < param.args.length
                ? param.args[index] : null;
    }

    private static int hookNoOp(ClassLoader classLoader,
                                String className,
                                String... methodNames) {
        return hookMethods(classLoader, className, methodNames, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(null);
            }
        });
    }

    private static int hookReturnFalse(ClassLoader classLoader,
                                       String className,
                                       String... methodNames) {
        return hookMethods(classLoader, className, methodNames, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(false);
            }
        });
    }

    private static int hookReturnNull(ClassLoader classLoader,
                                      String className,
                                      String... methodNames) {
        return hookMethods(classLoader, className, methodNames, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(null);
            }
        });
    }

    private static int hookMethods(ClassLoader classLoader,
                                   String className,
                                   String[] methodNames,
                                   XC_MethodHook callback) {
        Class<?> targetClass = XposedHelpers.findClassIfExists(className, classLoader);
        if (targetClass == null) {
            XposedBridge.log(TAG + " class not found: " + className);
            return 0;
        }

        int count = 0;
        for (String methodName : methodNames) {
            try {
                Set<XC_MethodHook.Unhook> hooks =
                        XposedBridge.hookAllMethods(targetClass, methodName, callback);
                count += hooks.size();
            } catch (Throwable t) {
                XposedBridge.log(TAG + " hook failed: " + className + "#"
                        + methodName + " : " + t);
            }
        }
        return count;
    }
}
