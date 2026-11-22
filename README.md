# Panoramic AOD Lock (API 102 / libxposed)

强制一加/OPPO 的 `full_screen_aod` 恒为 1，阻止系统场景策略（`com.oplus.metis`、
`OplusFeatureLongTakeBrightness`）周期性地把全景息屏降级成局部息屏。

## 技术要点

- 使用现代 **libxposed API 102**（`io.github.libxposed:api:102.0.0`），非 legacy API。
- 入口类继承 `io.github.libxposed.api.XposedModule`，**无参构造**（框架自动 attachFramework）。
- 入口注册：`META-INF/xposed/java_init.list`
- 作用域：`META-INF/xposed/scope.list` 写入特殊包名 `system`（system_server）。
- 模块配置：`META-INF/xposed/module.prop`（minApiVersion=101, targetApiVersion=102）。
- Hook 点：system_server 进程内 `android.provider.Settings$Secure` 的
  `getIntForUser` / `getInt` / `putIntForUser`，针对 `full_screen_aod` 与
  `panoramic_aod_enable` 两个键，读恒返回 1、写强制改为 1。

## 构建

GitHub Actions 自动构建：`push` 到 main/master 或手动触发 workflow_dispatch，
在 Actions 页面下载 `panoramic-aod-apk` 产物。

本地构建（需 JDK 17 + Android SDK platform 35）：
```bash
gradle assembleDebug
```

## 安装

1. 安装编译出的 APK（仅作为模块，桌面可能无图标，正常）。
2. 在 LSPosed / 支持 API 102 的框架中启用该模块，作用域勾选「系统框架 / system_server」。
3. 重启。
4. 日志 tag：`PanoramicAod`。

## 验证

```bash
adb shell dumpsys display | grep mFullScreenAodSupport   # 应为 true
adb shell settings get secure full_screen_aod            # 若被守护脚本占用则为 1
```
