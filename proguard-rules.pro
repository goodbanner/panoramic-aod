# Keep the Xposed module entry class and its name (loaded by reflection via
# META-INF/xposed/java_init.list).
-keep class com.example.panoramicaod.MainHook { *; }

# Keep the libxposed API surface and annotation metadata used by the framework.
-keep class io.github.libxposed.api.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations

# Keep line numbers for diagnostics (minimal size impact).
-keepattributes SourceFile,LineNumberTable
