import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

val secrets = Properties().apply {
    val secretsFile = rootProject.file("secrets.properties")
    if (secretsFile.exists()) {
        secretsFile.inputStream().use(::load)
    }
}

val releaseSigning = Properties().apply {
    val signingFile = rootProject.file("release-signing.properties")
    if (signingFile.exists()) {
        signingFile.inputStream().use(::load)
    }
}

fun secret(name: String, defaultValue: String): String =
    (secrets.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: defaultValue).trim()

fun secretOrDefaultIfBlank(name: String, defaultValue: String): String =
    secret(name, "").ifBlank { defaultValue }

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.eduspecial"
    compileSdk = 35

    signingConfigs {
        if (releaseSigning.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.eduspecial.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.0.10"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true

        setProperty("archivesBaseName", "Flashino-v${versionName}")
        buildConfigField(
            "String",
            "ADMOB_APP_ID",
            secret("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "ADMOB_NATIVE_AD_UNIT_ID",
            secret("ADMOB_NATIVE_AD_UNIT_ID", "ca-app-pub-3940256099942544/2247696110").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "ADMOB_REWARDED_AD_UNIT_ID",
            secret("ADMOB_REWARDED_AD_UNIT_ID", "ca-app-pub-3940256099942544/5224354917").asBuildConfigString()
        )
        buildConfigField(
            "int",
            "ADMOB_NATIVE_FREQUENCY",
            secret("ADMOB_NATIVE_FREQUENCY", "7")
        )
        buildConfigField(
            "String",
            "SUPABASE_URL",
            secret("SUPABASE_URL", "https://example.supabase.co").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            secret("SUPABASE_ANON_KEY", "").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "ALGOLIA_APP_ID",
            secret("ALGOLIA_APP_ID", "").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "ALGOLIA_SEARCH_KEY",
            secret("ALGOLIA_SEARCH_KEY", "").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "CLOUDINARY_ACCOUNTS_JSON",
            secret("CLOUDINARY_ACCOUNTS_JSON", "[]").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            secretOrDefaultIfBlank("GOOGLE_WEB_CLIENT_ID", "").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "AUDIO_BACKEND_BASE_URL",
            secret("AUDIO_BACKEND_BASE_URL", "").asBuildConfigString()
        )
        manifestPlaceholders["ADMOB_APP_ID"] = secret("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
    }

    buildTypes {
        debug {
            multiDexKeepFile = file("multidex-config.txt")
            multiDexKeepProguard = file("multidex-config.pro")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            multiDexKeepFile = file("multidex-config.txt")
            multiDexKeepProguard = file("multidex-config.pro")
            if (releaseSigning.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.coroutines.android)

    // Image Loading
    implementation(libs.coil.compose)

    // Firebase Auth
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Google Play In-App Review
    implementation("com.google.android.play:review-ktx:2.0.2")

    // Google Sign-In
    implementation(libs.play.services.auth)
    implementation(libs.play.services.ads)

    // Algolia
    implementation(libs.algolia.instantsearch)
    implementation("com.algolia:algoliasearch-client-kotlin:2.1.10")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // DataStore
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.workmanager.ktx)
    implementation(libs.cloudinary.android)

    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer.hls)

    // Lottie
    implementation(libs.lottie.compose)

    // Splash Screen
    implementation(libs.splashscreen)

    // Paging 3
    implementation("androidx.paging:paging-runtime:3.3.4")
    implementation("androidx.paging:paging-compose:3.3.4")
    implementation("androidx.room:room-paging:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest:kotest-property:5.9.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
