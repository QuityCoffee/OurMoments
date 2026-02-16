import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream

// 1. Читаем версию ПЕРЕД сборкой
val versionPropsFile = file("version.properties")
val vProps = Properties()
if (versionPropsFile.exists()) FileInputStream(versionPropsFile).use { vProps.load(it) }
else { vProps["VERSION_CODE"] = "1"; FileOutputStream(versionPropsFile).use { vProps.store(it, null) } }

// Используем -P флаги, если они переданы, иначе берем из файла
val finalVC = project.findProperty("versionCode")?.toString()?.toInt() ?: vProps.getProperty("VERSION_CODE").toInt()
val finalVN = project.findProperty("versionName")?.toString() ?: "1.$finalVC"


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
}

android {
    namespace = "ru.phb.ourmoments"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "ru.phb.ourmoments"
        minSdk = 26
        targetSdk = 36
// Используем переменную из файла
        versionCode = finalVC
        versionName = finalVN

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    signingConfigs {
        create("release") {
            storeFile = file("C:/key/ourmoments.jks") // Путь к файлу, который ты только что создал
            storePassword = "+Rom140603ll6"                     // Твой пароль
            keyAlias = "key0"                                // Твой алиас
            keyPassword = "+Rom140603ll6"                       // Тот же пароль
        }
    }

    buildTypes {
        debug {
            // Для кнопки RUN (Тест)
            buildConfigField("String", "API_URL", "\"http://api.quityrcr.beget.tech/test/apiеtest.php\"")
        }
        release {
            // Для сборки APK (Прод)
            buildConfigField("String", "API_URL", "\"http://api.quityrcr.beget.tech/api.php\"")
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}




dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("io.coil-kt:coil-video:2.5.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    val ktor_version = "2.3.7"
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-android:$ktor_version") // Для Android
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")

// Для сжатия видео
    implementation("com.github.AbedElazizShe:LightCompressor:1.3.2")
// Для удобной работы с корутинами (если еще нет)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")


}



tasks.register("deployToBeget") {
    group = "OurMoments"
    description = "Авто-инкремент, сборка новой версии и деплой"

    doLast {
        // Шаг А: Увеличиваем версию в файле
        val current = vProps.getProperty("VERSION_CODE").toInt()
        val nextVC = current + 1
        val nextVN = "1.$nextVC"

        vProps["VERSION_CODE"] = nextVC.toString()
        FileOutputStream(versionPropsFile).use { vProps.store(it, null) }

        println("🛠 Подготовка версии $nextVN (Code: $nextVC)...")

        // Шаг Б: Собираем новый APK, ПЕРЕДАВАЯ ему новую версию через аргументы
        // Это гарантирует, что внутри APK будет версия nextVC
        val assembleProcess = ProcessBuilder(
            "cmd", "/c", "gradlew.bat assembleRelease -PversionCode=$nextVC -PversionName=$nextVN"
        ).inheritIO().start()
        assembleProcess.waitFor()

        if (assembleProcess.exitValue() != 0) throw GradleException("❌ Сборка провалилась!")

        // Шаг В: Отправляем готовый файл на сервер
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        if (apk.exists()) {
            println("🚀 Загрузка $nextVN на Beget...")
            ProcessBuilder(
                "curl", "-X", "POST",
                "-F", "secret=MyLoveSecret2026quityromgmailcom",
                "-F", "versionCode=$nextVC",
                "-F", "versionName=$nextVN",
                "-F", "apk=@${apk.absolutePath}",
                "http://api.quityrcr.beget.tech/upload_apk.php"
            ).inheritIO().start().waitFor()

            println("\n✅ Релиз $nextVN опубликован. Телефон больше не будет просить 1.4!")
        }
    }
}