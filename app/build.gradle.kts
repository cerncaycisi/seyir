import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// versionCode = number of git commits (falls back to 1 when git is unavailable).
val gitCommitCount: Int = try {
    val proc = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootDir).redirectErrorStream(true).start()
    val out = proc.inputStream.bufferedReader().readText().trim()
    if (proc.waitFor() == 0) out.toIntOrNull() ?: 1 else 1
} catch (e: Exception) {
    1
}

// Release signing: keystore.properties (gitignored) -- see keystore.properties.example.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true

android {
    namespace = "dev.iptv.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.iptv.player"
        minSdk = 24
        targetSdk = 35
        versionCode = gitCommitCount.coerceAtLeast(1)
        versionName = "0.2.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("WARNING: keystore.properties not found -- release build is signed with the DEBUG key (not for store upload).")
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
}
