package com.example.panoramicaod;

import android.util.Log;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * API 102 (libxposed) module: force panoramic (full-screen) AOD on, permanently.
 *
 * Two injection targets:
 *
 * 1) system_server ("system" scope)
 *    - Settings.Secure.getIntForUser("full_screen_aod"/"panoramic_aod_enable")
 *      -> 1, so OplusBrightnessUtils.getFullScreenAodSupport() stays true.
 *    - putIntForUser for those keys -> forced to 1.
 *
 * 2) com.oplus.aod (the app that actually decides panoramic vs partial)
 *    The mode is governed by Settings.Secure "Setting_AodState" / "Setting_AodState_Local",
 *    read/written via com.oplus.aod.util.SettingsUtils using its own helper (NOT
 *    Settings.Secure.getIntForUser). State codes observed on ColorOS 17:
 *        0 = off / partial (local)
 *        1 = full-day AOD (display)
 *        2 = inspiration / panoramic AOD  <-- what we want
 *    We force those getters to return 2 and neutralize the setters so the
 *    system scene policy cannot downgrade to 0.
 *
 *    OnextBox hooks SettingsUtils.getKeyAodAllDaySupportSettings() in the same
 *    process; we hook different methods, so there is no conflict.
 *
 * Ultra-low overhead: no threads/timers/polling/I/O. All work is a handful of
 * constant-time hooks that only run when the framework reads/writes these keys.
 *
 * Entry class keeps the no-arg constructor required by API 102.
 */
public class MainHook extends XposedModule {

    private static final String TAG = "PanoramicAod";

    private static final String KEY_FULL = "full_screen_aod";      // 15
    private static final String KEY_PANO = "panoramic_aod_enable"; // 20

    // Panoramic/inspiration AOD state code (confirmed from com.oplus.aod.dex).
    private static final int AOD_STATE_PANORAMIC = 2;

    private volatile boolean systemHooked = false;
    private volatile boolean aodHooked = false;

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        installSystem(param.getClassLoader());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String pkg = param.getPackageName();
        ClassLoader cl = param.getDefaultClassLoader();
        if ("android".equals(pkg) || "com.android.providers.settings".equals(pkg)) {
            installSystem(cl);
        } else if ("com.oplus.aod".equals(pkg)) {
            installAod(cl);
        }
    }

    // ---------------- system_server ----------------

    private void installSystem(ClassLoader cl) {
        if (systemHooked || cl == null) return;
        Class<?> secure;
        try {
            secure = Class.forName("android.provider.Settings$Secure", true, cl);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Settings$Secure not found", t);
            return;
        }

        try {
            java.lang.reflect.Method m = secure.getMethod(
                    "getIntForUser",
                    android.content.ContentResolver.class,
                    String.class, int.class, int.class);
            hook(m).setPriority(PRIORITY_HIGHEST).intercept(chain -> {
                Object name = chain.getArg(1);
                if (name instanceof String) {
                    String s = (String) name;
                    int len = s.length();
                    if ((len == 15 && KEY_FULL.equals(s)) ||
                        (len == 20 && KEY_PANO.equals(s))) {
                        return 1;
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "getIntForUser hook failed", t);
        }

        try {
            java.lang.reflect.Method m = secure.getMethod(
                    "putIntForUser",
                    android.content.ContentResolver.class,
                    String.class, int.class, int.class);
            hook(m).setPriority(PRIORITY_HIGHEST).intercept(chain -> {
                Object name = chain.getArg(1);
                if (name instanceof String) {
                    String s = (String) name;
                    int len = s.length();
                    if ((len == 15 && KEY_FULL.equals(s)) ||
                        (len == 20 && KEY_PANO.equals(s))) {
                        Object[] args = chain.getArgs().toArray();
                        args[2] = 1;
                        return chain.proceed(args);
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "putIntForUser hook failed", t);
        }

        systemHooked = true;
        log(Log.INFO, TAG, "system_server hooks installed");
    }

    // ---------------- com.oplus.aod ----------------

    private void installAod(ClassLoader cl) {
        if (aodHooked || cl == null) return;
        Class<?> utils;
        try {
            utils = Class.forName("com.oplus.aod.util.SettingsUtils", true, cl);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "aod SettingsUtils not found", t);
            return;
        }

        // getAodStateSettings(Context, int) -> int   (and the Context-only overload)
        hookIntGetter(utils, "getAodStateSettings", android.content.Context.class, int.class);
        hookIntGetter(utils, "getAodStateSettings", android.content.Context.class);
        hookIntGetter(utils, "getAodStateLocalSettings", android.content.Context.class);

        // setAodStateSettings(Context, int) / setAodStateLocalSettings(Context, int)
        hookIntSetter(utils, "setAodStateSettings", android.content.Context.class, int.class);
        hookIntSetter(utils, "setAodStateLocalSettings", android.content.Context.class, int.class);

        aodHooked = true;
        log(Log.INFO, TAG, "com.oplus.aod hooks installed (state -> " + AOD_STATE_PANORAMIC + ")");
    }

    /** Force an int getter to return AOD_STATE_PANORAMIC. */
    private void hookIntGetter(Class<?> owner, String name, Class<?>... params) {
        try {
            java.lang.reflect.Method m = owner.getDeclaredMethod(name, params);
            hook(m).setPriority(PRIORITY_HIGHEST).intercept(chain -> chain.proceed(
                    new Object[]{AOD_STATE_PANORAMIC}));
        } catch (NoSuchMethodException e) {
            // overload may not exist on this ROM; ignore quietly
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "getter hook failed: " + name, t);
        }
    }

    /**
     * Neutralize an int setter: replace its int argument with AOD_STATE_PANORAMIC
     * so any write of 0/1 becomes the panoramic code.
     */
    private void hookIntSetter(Class<?> owner, String name, Class<?>... params) {
        try {
            java.lang.reflect.Method m = owner.getDeclaredMethod(name, params);
            hook(m).setPriority(PRIORITY_HIGHEST).intercept(chain -> {
                Object[] args = chain.getArgs().toArray();
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Integer) {
                        args[i] = AOD_STATE_PANORAMIC;
                    }
                }
                return chain.proceed(args);
            });
        } catch (NoSuchMethodException e) {
            // overload may not exist on this ROM; ignore quietly
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "setter hook failed: " + name, t);
        }
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        // one-shot only; no recurring work
    }
}
