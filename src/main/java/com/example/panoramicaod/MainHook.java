package com.example.panoramicaod;

import android.util.Log;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * API 102 (libxposed) module: force panoramic (full-screen) AOD on.
 *
 * Ultra-low overhead design:
 *   - No thread, no timer, no polling, no wake lock, no I/O on the hot path.
 *   - Only ONE read hook (getIntForUser) and ONE write hook (putIntForUser).
 *     getInt() is NOT hooked: on Android it simply delegates to
 *     getIntForUser(), so hooking the latter already covers it.
 *   - Interceptors do a constant-time length check before equals(), and
 *     allocate nothing on the non-matching (overwhelmingly common) path.
 *
 * Effect: the module adds a handful of nanoseconds of CPU only when the
 * framework reads/writes a setting; idle power draw is exactly zero.
 *
 * Entry class MUST keep the no-arg constructor: API 102 instantiates it and
 * calls attachFramework() itself.
 */
public class MainHook extends XposedModule {

    private static final String TAG = "PanoramicAod";
    private static final String KEY_FULL = "full_screen_aod";      // length 15
    private static final String KEY_PANO = "panoramic_aod_enable"; // length 20

    private volatile boolean hooked = false;

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        install(param.getClassLoader());
    }

    /** Fallback for ROMs that deliver system_server via a package load event. */
    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String pkg = param.getPackageName();
        if ("android".equals(pkg) || "com.android.providers.settings".equals(pkg)) {
            install(param.getDefaultClassLoader());
        }
    }

    private void install(ClassLoader cl) {
        if (hooked || cl == null) return;
        Class<?> secure;
        try {
            secure = Class.forName("android.provider.Settings$Secure", true, cl);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Settings$Secure not found", t);
            return;
        }

        // Read path: Settings.Secure.getIntForUser(...) -> int
        // This is the single hot hook. Keep it allocation-free on miss.
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

        // Write path: low frequency (policy writes a few times an hour).
        // Check the key with zero allocation; only allocate on a hit.
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

        hooked = true;
        log(Log.INFO, TAG, "installed (2 hooks, no threads/timers)");
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        // one-shot log only; no recurring work
    }
}
