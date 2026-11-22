package com.example.panoramicaod;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * API 102 (libxposed) module: force panoramic (full-screen) AOD on.
 *
 * Root cause being fixed:
 *   OPlus scene/power policy (com.oplus.metis + OplusFeatureLongTakeBrightness)
 *   periodically rewrites Settings.Secure "full_screen_aod" to 0, which flips
 *   OplusBrightnessUtils.getFullScreenAodSupport() to false and silently
 *   degrades panoramic AOD back to partial AOD.
 *
 * Strategy (cleaner than a shell watchdog):
 *   Inject into system_server (scope "system") and override the framework's
 *   reads/writes of the two relevant secure settings so they always resolve
 *   to 1:
 *     - getIntForUser(...) for "full_screen_aod" / "panoramic_aod_enable"
 *       returns 1  -> system code always believes panoramic AOD is enabled.
 *     - putIntForUser(...) writing either key is rewritten to 1, so the
 *       underlying value can never actually become 0.
 *
 *   The entry class MUST keep the no-arg constructor: API 102 instantiates it
 *   and calls attachFramework() itself; do not add your own constructor.
 */
public class MainHook extends XposedModule {

    private static final String TAG = "PanoramicAod";
    private static final String KEY_FULL = "full_screen_aod";
    private static final String KEY_PANO = "panoramic_aod_enable";

    private volatile boolean hooked = false;

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        try {
            hookSettings(param.getClassLoader());
            log(Log.INFO, TAG, "system_server: panoramic AOD hooks installed");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "system_server hook failed", t);
        }
    }

    /**
     * Fallback for frameworks/ROMs that deliver system_server via a package
     * load event instead of onSystemServerStarting().
     */
    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        try {
            String pkg = param.getPackageName();
            if ("android".equals(pkg) || "com.android.providers.settings".equals(pkg)) {
                hookSettings(param.getDefaultClassLoader());
                log(Log.INFO, TAG, "hook installed via package=" + pkg);
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "onPackageLoaded hook failed", t);
        }
    }

    private void hookSettings(ClassLoader cl) {
        if (hooked || cl == null) return;
        Class<?> secure;
        try {
            secure = Class.forName("android.provider.Settings$Secure", true, cl);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "cannot find Settings$Secure", t);
            return;
        }

        // getIntForUser(ContentResolver, String name, int def, int userId) -> int
        try {
            java.lang.reflect.Method m = secure.getMethod(
                    "getIntForUser",
                    android.content.ContentResolver.class,
                    String.class, int.class, int.class);
            hook(m).setPriority(PRIORITY_HIGHEST).intercept(chain -> {
                Object name = chain.getArg(1);
                if (KEY_FULL.equals(name) || KEY_PANO.equals(name)) {
                    return 1;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "getIntForUser hook failed", t);
        }

        // getInt(ContentResolver, String name, int def) -> int  (some ROM paths)
        try {
            java.lang.reflect.Method m = secure.getMethod(
                    "getInt",
                    android.content.ContentResolver.class, String.class, int.class);
            hook(m).setPriority(PRIORITY_HIGHEST).intercept(chain -> {
                Object name = chain.getArg(1);
                if (KEY_FULL.equals(name) || KEY_PANO.equals(name)) {
                    return 1;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "getInt hook failed", t);
        }

        // putIntForUser(ContentResolver, String name, int value, int userId) -> boolean
        try {
            java.lang.reflect.Method m = secure.getMethod(
                    "putIntForUser",
                    android.content.ContentResolver.class,
                    String.class, int.class, int.class);
            hook(m).setPriority(PRIORITY_HIGHEST).intercept(chain -> {
                Object[] args = chain.getArgs().toArray();
                Object name = args[1];
                if (KEY_FULL.equals(name) || KEY_PANO.equals(name)) {
                    args[2] = 1; // force any write to be 1
                    return chain.proceed(args);
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "putIntForUser hook failed", t);
        }

        hooked = true;
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "loaded, systemServer=" + param.isSystemServer()
                + ", proc=" + param.getProcessName());
    }
}
