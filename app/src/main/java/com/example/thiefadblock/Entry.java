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
 * LSPosed legacy-API entry point for Thief Company 0.2.9.
 *
 * The uploaded DEX files contain TapTap TapAd and Dirichlet mediation classes.
 * This module hooks their Java facade and ad-object implementations, rather
 * than indiscriminately blocking the game's network stack.
 */
public final class Entry implements IXposedHookLoadPackage {
    private static final String TAG = "[ThiefNoAds]";
    private static final String TARGET_PACKAGE = "com.WoodenStool.ThiefSimulator";

    /**
     * true: suppress ad-load requests as well as display (stronger blocking).
     * false: allow ad loading, but still make ad objects invalid and block display.
     *
     * If an ad-related game screen gets stuck waiting for a load callback,
     * change this to false and rebuild. Reward callbacks are never fabricated.
     */
    private static final boolean BLOCK_LOAD_REQUESTS = true;

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

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

        int hooked = 0;

        // TapTap's single-network ad facade implementation of TapAdNative.
        if (BLOCK_LOAD_REQUESTS) {
            hooked += hookNoOp(classLoader, "com.tapsdk.tapad.i",
                    "loadBannerAd",
                    "loadExpressFeedAd",
                    "loadFeedAd",
                    "loadInterstitialAd",
                    "loadNativeFeedAd",
                    "loadRewardVideoAd",
                    "loadSplashAd");

            // TapTap Dirichlet mediation facade: load, preload and auto-show.
            hooked += hookNoOp(classLoader, "com.tapsdk.tapad.group.l",
                    "loadBannerAd",
                    "loadExpressFeedAd",
                    "loadInterstitialAd",
                    "loadNativeFeedAd",
                    "loadRewardVideoAd",
                    "loadSplashAd",
                    "preLoad",
                    "showBannerAutoAd",
                    "showInterstitialAutoAd",
                    "showRewardVideoAutoAd",
                    "showSplashAutoAd");
        } else {
            // Auto-show methods are display entry points, not load requests.
            hooked += hookNoOp(classLoader, "com.tapsdk.tapad.group.l",
                    "showBannerAutoAd",
                    "showInterstitialAutoAd",
                    "showRewardVideoAutoAd",
                    "showSplashAutoAd");
        }

        // TapAd ad-object implementations: invalidate and block presentation.
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

        // Standard TapAd feed objects (two implementations occur in this DEX).
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

        XposedBridge.log(TAG + " installed " + hooked
                + " method hooks; blockLoadRequests=" + BLOCK_LOAD_REQUESTS);
        if (hooked == 0) {
            XposedBridge.log(TAG + " WARNING: no target methods were found; "
                    + "the game's SDK may have changed.");
        }
    }

    private static int hookNoOp(ClassLoader classLoader,
                                String className,
                                String... methodNames) {
        return hookMethods(classLoader, className, methodNames, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                // All methods in this list are void ad load/show/render methods.
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
