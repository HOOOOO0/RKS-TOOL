import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9.0+ 内置 Kotlin 支持，不能再应用 org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

/**
 * 固定签名配置。
 *
 * 密钥库与密码通过环境变量注入（CI 里来自 GitHub Secrets，
 * 本地构建时可放在 local.properties 或直接设环境变量）：
 *
 *   KEYSTORE_PATH      密钥库文件路径（由 CI 从 base64 解码得到）
 *   KEYSTORE_PASSWORD  密钥库密码
 *   KEY_ALIAS          密钥别名
 *   KEY_PASSWORD       密钥密码
 *
 * 若这些变量缺失，则回退到 Android 默认的 debug 签名
 * —— 注意 debug 签名每次构建都会变（尤其在 CI 的干净环境里），
 * 会导致新版无法覆盖安装旧版。
 */
val keystorePathEnv: String? = System.getenv("KEYSTORE_PATH")
val keystorePasswordEnv: String? = System.getenv("KEYSTORE_PASSWORD")
val keyAliasEnv: String? = System.getenv("KEY_ALIAS")
val keyPasswordEnv: String? = System.getenv("KEY_PASSWORD")

// 也支持写在 local.properties 里（本地构建用）
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(name: String): String? =
    System.getenv(name) ?: localProps.getProperty(name)

val storeFilePath: String? = prop("KEYSTORE_PATH")
val storePassword: String? = prop("KEYSTORE_PASSWORD")
val keyAliasValue: String? = prop("KEY_ALIAS")
val keyPassword: String? = prop("KEY_PASSWORD")

val hasSigningConfig = !storeFilePath.isNullOrBlank() &&
    !storePassword.isNullOrBlank() &&
    !keyAliasValue.isNullOrBlank() &&
    !keyPassword.isNullOrBlank() &&
    file(storeFilePath).exists()

android {
    namespace = "com.rks.calculator"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.rks.calculator"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0"
    }

    if (hasSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = file(storeFilePath!!)
                storePassword = storePassword
                keyAlias = keyAliasValue
                keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            // debug 也尽量用固定签名，避免每次构建签名都变
            if (hasSigningConfig) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasSigningConfig) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// AGP 9 内置 Kotlin：直接用顶层 kotlin { } 配置编译选项
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    // Miuix：小米 HyperOS 风格的 Compose Multiplatform UI 库
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
}

// 构建时打印签名状态，便于排查「为什么签名又变了」
tasks.matching { it.name.startsWith("assemble") }.configureEach {
    doFirst {
        if (hasSigningConfig) {
            logger.lifecycle("[签名] 使用固定密钥库：$storeFilePath（别名 $keyAliasValue）")
        } else {
            logger.lifecycle(
                "[签名] 未提供密钥库，将使用 Android 默认 debug 签名。" +
                    "注意：该签名在 CI 每次构建都会变化，会导致无法覆盖安装。",
            )
        }
    }
}
