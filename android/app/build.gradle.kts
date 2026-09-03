plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.calcplus.calculator"
    compileSdk = 36

    defaultConfig {
        // Disguise identity — permanent once shipped (idea plan §1).
        applicationId = "com.calcplus.calculator"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // Room's exported schemas on the UNIT-TEST CLASSPATH (java resources, not
    // assets): `MigrationTest` reads `1.json` from there to build a v1 database
    // and then lets Room itself validate the migrated schema.
    //
    // Deliberately NOT `assets`. Robolectric reads assets from the app
    // variant's merged assets (`android_merged_assets` in the generated
    // `test_config.properties`), never from the unit-test source set or the
    // unit-test component, so an asset-based schema would have to be added to
    // the debug build type — which packages the vault's table names into the
    // debug APK. Test java resources go to the unit-test runtime classpath
    // only and are packaged into nothing.
    sourceSets.getByName("test") {
        resources.srcDir(files("$projectDir/schemas"))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.coil.compose)
    // N3 video playback. media3-ui is included for its TextureView-backed
    // PlayerView (decisions §9) — a TextureView draws inside the window, so the
    // activity's unconditional FLAG_SECURE covers the video surface. No
    // media3-session / MediaSessionService anywhere: a media session is what
    // would put vault video on the lock screen and in the PiP auto-enter path.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    // TestNavHostController: drives the real vault graph in SearchNavigationTest.
    testImplementation(libs.navigation.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
