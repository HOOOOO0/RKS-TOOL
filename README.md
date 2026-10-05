# 单曲 RKS 计算器

一个用 [Miuix](https://github.com/compose-miuix-ui/miuix)（小米 HyperOS 风格的 Compose Multiplatform UI 库）
写的 Android 小工具：输入**定数**和 **ACC**，实时算出单曲 RKS。

## 功能

- **两个输入框**：定数 + ACC，边输入边出结果（无需点按钮）
- **中间箭头**：点击在正向 / 反向换算之间切换，箭头带动画旋转 180°
- **正向换算**：`RKS = (((100 × ACC − 55) / 45)²) × level`
- **反向换算**：给定目标 RKS 与定数，反推需要的 ACC
- **ACC 自动识别**：`99.23`、`0.9923`、`99.23%` 三种写法都能读
- **边界校验**：ACC < 70% 时结果直接显示 **「别越」**；ACC > 100% 同样拦截
- **上限提示**：显示当前定数下 100% ACC 的 RKS 上限
- **Monet 动态取色**：跟随系统深浅色与壁纸取色（Android 12+）

## 界面结构

```
┌─────────────────────────┐
│  单曲 RKS 计算器          │  ← TopAppBar
├─────────────────────────┤
│  输入                    │
│  ┌───────────────────┐  │
│  │ 定数               │  │
│  ├───────────────────┤  │
│  │ ACC（99.23/0.9923）│  │  ← 反向模式下变成「目标 RKS」
│  └───────────────────┘  │
│                         │
│         ⌄ (点击切换)     │  ← 箭头：正向朝下，反向旋转为朝上
│   正向：算 RKS           │
│                         │
│  结果                    │
│  ┌───────────────────┐  │
│  │    单曲 RKS         │  │
│  │     14.4911        │  │  ← 实时更新，<70% 时显示「别越」
│  │  定数 15 时上限 15   │  │
│  └───────────────────┘  │
└─────────────────────────┘
```

## 构建

### 环境要求（重要）

本工程依赖较新的工具链，版本不是随便写的：

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **17+** | |
| Gradle | **9.4.1** | 与 AGP 9.4.1 配套 |
| Android Gradle Plugin | **9.4.1** | |
| Kotlin | **2.4.20** | miuix 0.9.4 就是用这个版本编译的 |
| Compose Multiplatform | **1.12.1** | miuix 0.9.4 依赖 Compose 1.12 |
| compileSdk | **37** | **必须 ≥ 37**，见下方说明 |
| minSdk | 26 | |

> ⚠️ **为什么 compileSdk 必须是 37**
> miuix 0.9.4 的 AAR 元数据里写死了 `minCompileSdk=37`。
> 如果你把 `compileSdk` 调低，Gradle 会直接报错拒绝构建：
> `Dependency 'top.yukonga.miuix.kmp:miuix-ui:0.9.4' requires libraries and applications that depend on it to compile against version 37 or later`。
> 在 Android Studio 里安装 **Android 37 (Android 17) SDK Platform** 即可。

### 步骤

```bash
# 1. 生成 Gradle Wrapper（本仓库未附带 wrapper 二进制）
gradle wrapper --gradle-version 9.4.1

# 2. 编译 Debug APK
./gradlew :app:assembleDebug

# 产物位置
# app/build/outputs/apk/debug/app-debug.apk
```

或者直接用 Android Studio 打开本目录，等待 Gradle Sync 完成后点运行。

如果依赖下载慢，可在 `settings.gradle.kts` 的仓库里加国内镜像
（如阿里云 `https://maven.aliyun.com/repository/public`）。

## 代码结构

```
app/src/main/java/com/rks/calculator/
├── MainActivity.kt          # 入口 Activity
├── RksMath.kt               # 换算核心逻辑（纯 Kotlin，与 UI 解耦，可单测）
└── ui/
    ├── RksScreen.kt         # 主界面：双输入框 + 箭头 + 结果卡片
    └── theme/Theme.kt       # Miuix 主题（Monet 动态取色）
```

### 换算逻辑说明

正向函数在 `ACC = 55%` 处取到最小值 0；**ACC 低于 55% 时函数值会「反弹」变大**
（例如 ACC=0 会算出 22.4，比 ACC=70 的 1.67 还大），这在 Phigros 中毫无意义。
因此代码在 ACC < 70% 时直接返回 `Outcome.TooLow`，界面显示「别越」，不输出数字。

反向公式：`ACC = (55 + 45 × √(RKS / level)) / 100`。
若目标 RKS 超过该定数上限（即 RKS > level），同样返回「别越」。

## 已验证项

下面的 API 用法都对着 **miuix 0.9.4 的真实字节码** 核对过（不是凭记忆写的）：

- `TextField(value, onValueChange, label, useLabelAsPlaceholder, singleLine, ...)`
- `Card(modifier, insideMargin, ...)`、`SmallTitle(text)`、`Text(text, fontSize, ...)`
- `Scaffold(topBar = { TopAppBar(title = ...) })`
- `IconButton(onClick) { Icon(imageVector = MiuixIcons.ExpandMore, ...) }`
- `MiuixTheme(controller = ThemeController(colorSchemeMode = ColorSchemeMode.MonetSystem, keyColor = ...))`
- 配色字段：`primary` / `error` / `onBackgroundVariant` / `onSurfaceContainerVariant`

> 图标说明：miuix-icons 0.9.4 **没有** `ArrowDownward` 之类图标，
> 所以箭头用的是 `MiuixIcons.ExpandMore`（向下箭头），反向时用 `Modifier.rotate(180f)` 翻转。

## 许可

示例代码，可自由修改使用。Miuix 本身为 Apache-2.0。
