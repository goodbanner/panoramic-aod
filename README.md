# Panoramic AOD Lock (API 102 / libxposed)

强制一加/OPPO 的 `full_screen_aod` 恒为 1，阻止系统场景策略（`com.oplus.metis`、
`OplusFeatureLongTakeBrightness`）周期性地把全景息屏降级成局部息屏。

## 功耗（可忽略不计）

模块本身**零持续功耗**：

- 无线程、无定时器、无轮询、无 WakeLock、热路径无 I/O。
- 只挂 2 个 hook：`Settings.Secure.getIntForUser`（读）和 `putIntForUser`（写）。
  `getInt()` 在 Android 内部委托给 `getIntForUser()`，无需单独 hook。
- 拦截器中先用字符串长度做常数时间过滤，不匹配路径不分配任何对象。
- 空闲时模块不执行任何代码，不唤醒 CPU，因此待机耗电增量为 0。
- 唯一开销发生在系统读写这两个设置键时（每小时几次），每次仅几十纳秒 CPU。

注意：全景 AOD 本身比局部 AOD 更耗电（全屏像素常亮），这是功能本身的开销，
与本模块的实现无关。

## 技术要点

- 现代 **libxposed API 102**（`io.github.libxposed:api:102.0.0`），非 legacy API。
- 入口类继承 `io.github.libxposed.api.XposedModule`，无参构造（框架自动 attachFramework）。
- 入口注册：`META-INF/xposed/java_init.list`
- 作用域：`META-INF/xposed/scope.list` 写入特殊包名 `system`（system_server）。
- 模块配置：`META-INF/xposed/module.prop`（minApiVersion=101, targetApiVersion=102）。
- Hook 点：system_server 进程内 `Settings.Secure`，对 `full_screen_aod` /
  `panoramic_aod_enable` 读恒返回 1、写强制改为 1。

## 构建

GitHub Actions 自动构建 **release**（minify + shrink + 签名）：push 到 main/master 或
手动触发 workflow_dispatch，在 Artifacts 下载 `panoramic-aod-release`。

本地构建 release（需 JDK 17 + Android SDK platform 35）：
```bash
gradle assembleRelease
```
签名使用仓库内 `keystore/panoramic.jks`（自签名），密码见 build.gradle.kts 的环境变量默认值。

## 安装

1. 安装 release APK（作为模块，桌面可能无图标，正常）。
2. 在 LSPosed / 支持 API 102 的框架中启用，作用域勾选「系统框架 / system_server」。
3. 重启。日志 tag：`PanoramicAod`。
