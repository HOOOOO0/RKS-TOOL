# 单曲 RKS 计算器

一个用 [Miuix](https://github.com/compose-miuix-ui/miuix)（小米 HyperOS 风格的 Compose Multiplatform UI 库）
写的 Android 小工具：输入**定数**和 **ACC**，实时算出单曲 RKS。

## 功能

**首页 · 单曲 RKS 计算器**

- **两个输入框**：定数 + ACC，边输入边出结果（无需点按钮）
- **中间箭头**：点击在正向 / 反向换算之间切换，箭头带动画旋转 180°
- **正向换算**：`RKS = (((100 × ACC − 55) / 45)²) × 定数`
- **反向换算**：给定目标 RKS 与定数，反推需要的 ACC
- **ACC 自动识别**：`99.23`、`0.9923`、`99.23%` 三种写法都能读
- **边界校验**：ACC < 70% 时结果直接显示 **「别越」**；ACC > 100% 同样拦截
- **上限提示**：显示当前定数下 100% ACC 的 RKS 上限
- **导出 JSON**：把记录的成绩写成 JSON 到 App 私有目录

**更多 · 存档与 B30**

- **读取存档**：root 直读 Phigros 的 playerprefs.xml；无 root 时引导去在线工具解密后手动选择文件
- **B30 计算**：27 首最高 RKS + 3 首满分，除以 30；支持「存档数据」和「首页记录数据」两种来源

**通用**

- **底栏导航**：首页 / 关于 / 更多 三个页面
- **Monet 动态取色**：跟随系统深浅色与壁纸取色（Android 12+）

## 功能总览

底栏三个页面：

| 页面 | 内容 |
|---|---|
| **首页** | 单曲 RKS 计算器（定数 + ACC ↔ RKS），底部可「加入导出列表」并「导出 JSON」 |
| **关于** | 公式说明、B30 算法说明、数据来源与免责声明 |
| **更多** | ① 读取存档（root 直读 / 手动选择解密后的文件）② B30 计算（存档数据 / 首页记录的数据） |

### 导出 JSON

点首页的「导出 JSON」，文件写在 App 私有目录（不需要任何存储权限）：

```
/data/user/0/com.rks.calculator/files/rks_data.json
```

格式：

```json
{
  "version": 1,
  "exportedAt": 1791253961905,
  "exportedAtText": "2026-10-06 02:40:00",
  "songs": [
    { "song": "", "level": 16.6, "acc": 99.5, "rks": 16.2332 }
  ]
}
```

### 读取存档

两条路径：

1. **root 直读**：直接读取
   `/data/user/0/com.PigeonGames.Phigros/shared_prefs/com.PigeonGames.Phigros.v2.playerprefs.xml`
   没有 root 时会提示，并弹出解密引导。
2. **手动选择**：用系统文件选择器选一个**已解密**的存档。

> ⚠️ **本工具不包含任何解密算法。** Phigros 原始存档的字段名和数据都是密文，
> 需要先用在线工具（[PhiCrypto](https://phi.yanx.us/)）解密后再交给本 App。

### B30 算法

按需求实现（**不去重**：满分歌若同时进入前 27 名，会被计入两次）：

1. 算出所有歌的单曲 RKS，放进一个列表
2. 排序，取最高的 **27** 首
3. 把所有**满分（1000000 分）**的歌拿出来放进另一个列表，排序，取最高的 **3** 首
4. 把这 27 首与这 3 首的 RKS 全部相加，**除以 30**

> **分母固定为 30**，与数据条数无关。
> 例如只有 1 首满分 + 27 首普通（共 28 条），或者只有 1 条数据，分母同样是 30。
>
> 两个列表相互独立，**不去重**：满分歌若同时进入前 27 名，会被计入两次。

满分歌的 ACC 是 100%，代入公式后 `((100-55)/45)² × 定数 = 定数`，
所以它的 RKS 在数值上等于定数。

### 定数表

存档里**只有**分数、ACC、FC，**没有定数**。定数来自打包在
`app/src/main/assets/level_table.json` 的曲目表（327 首，社区维护）。

匹配方式是**存档 key 的前两段**，两边格式完全一致：

```
存档 key:  歌名.曲师.0.Record.难度   →  Glaciaxion.SunsetRay.0.Record.HD
定数表 key: 歌名.曲师                →  Glaciaxion.SunsetRay
```

在真实存档上实测匹配率 **301/301 = 100%**。

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
| Gradle | **9.8.0** | AGP 9.4.1 需要 Gradle 9.8.0+（9.4.1 缺少 ProjectTypeBinding 类，会报 NoClassDefFoundError）|
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
gradle wrapper --gradle-version 9.8.0

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
├── MainActivity.kt              # 入口 Activity
├── RksMath.kt                   # RKS 换算核心逻辑（纯 Kotlin，与 UI 解耦）
├── data/
│   ├── LevelTable.kt            # 定数表加载（读 assets/level_table.json）
│   ├── SaveParser.kt            # 解析解密后的存档 xml
│   ├── SaveReader.kt            # root 直读 + 文件选择兜底
│   ├── B30Calculator.kt         # B30 计算（27 首 + 3 首满分 ÷ 30）
│   └── AppJson.kt               # 数据模型 + JSON 导入导出
└── ui/
    ├── RksApp.kt                # 主框架：底栏 + 页面切换
    ├── AppState.kt              # 跨页面共享状态
    ├── HomeScreen.kt            # 首页：计算器 + 导出
    ├── AboutScreen.kt           # 关于页
    ├── MoreScreen.kt            # 更多页：读存档 + B30
    ├── Dialogs.kt               # 解密引导弹窗 + B30 明细弹窗
    └── theme/Theme.kt           # Miuix 主题（Monet 动态取色）

app/src/main/assets/
└── level_table.json             # 曲目定数表（327 首）
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

