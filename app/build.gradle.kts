import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Bump this for each distributed release. Git availability/history must not change an APK's version.
// CI or release builds may supply an explicit monotonically increasing -PappVersionCode=24.
val releaseVersionCode = providers.gradleProperty("appVersionCode").orElse("32").get().let { value ->
    value.toIntOrNull()?.takeIf { it in 1..2_100_000_000 }
        ?: throw GradleException("appVersionCode must be an integer between 1 and 2100000000 (received '$value').")
}

// Release signing: keystore.properties (gitignored) -- see keystore.properties.example.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(name: String) = localProps.getProperty(name, "").trim().replace("\\", "").replace("\"", "")

android {
    namespace = "dev.iptv.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.iptv.player"
        minSdk = 24
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = "0.2.12"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // OpenSubtitles (optional; empty key hides the feature). Values come from local.properties, never committed.
        buildConfigField("String", "OPENSUBTITLES_API_KEY", "\"${localProp("opensubtitles.apiKey")}\"")
        buildConfigField("String", "OPENSUBTITLES_USERNAME", "\"${localProp("opensubtitles.username")}\"")
        buildConfigField("String", "OPENSUBTITLES_PASSWORD", "\"${localProp("opensubtitles.password")}\"")
        // TMDB key comes from local.properties (tmdb.apiKey); empty disables enrichment.
        buildConfigField("String", "TMDB_API_KEY", "\"${localProp("tmdb.apiKey")}\"")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    bundle {
        // Both languages must remain installed when the user changes the app language offline.
        language {
            enableSplit = false
        }
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
    implementation("androidx.media3:media3-session:1.5.1")
    // FFmpeg software audio decoder (AC3/E-AC3/DTS...). GPL-3.0 licensed (Jellyfin build) - see release notes.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.tvprovider:tvprovider:1.0.0")

    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Pure-JVM XmlPullParser so the XMLTV parser can be unit tested.
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
