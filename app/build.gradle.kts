import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// The upload key for Play App Signing. Gitignored, generated once on 23 Sept 2026 and kept only on
// Ian's machine — never committed. A clone without keystore.properties still builds a release
// variant, just an unsigned one, so this is never a hard requirement for anyone else working on the
// repo.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.gallery.sync"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.gallery.sync"
        minSdk = 26
        targetSdk = 37
        // versionName tracks the milestone in .claude/MILESTONES.md that is being built, so a crash
        // report or a Play console entry says which one it came from. "1.0" would have claimed a
        // maturity the app does not have, and the release gate means nothing ships before v0.4
        // anyway.
        //
        // versionCode is a plain incrementing integer with no relationship to the name. Google Play
        // only ever accepts a higher one, so it is bumped per upload and never reset. First real
        // upload was versionCode 1 (Internal Testing, 23 Sept 2026); this is the second, adding the
        // Billing Library so Play Console will allow the pro_unlock product to be created at all.
        // The third (26 Sept 2026) carries the multi-cloud work: Dropbox, Google Drive, Backblaze B2,
        // IDrive e2, the sign-out prompt and the rewritten How To Guide.
        // The fourth (25 Sept 2026) writes a restored file back into the folder it left, through the
        // folder grant, and carries the wording changes made while testing the third on the Moto G.
        versionCode = 4
        versionName = "0.3.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AppAuth's own manifest declares the redirect activity's intent-filter with this
        // placeholder. Must match google_photos_config.json's redirect_uri scheme
        // ("com.gallery.sync:/oauth2redirect") or the OAuth callback never reaches the app.
        manifestPlaceholders["appAuthRedirectScheme"] = "com.gallery.sync"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    // Room's MigrationTestHelper reads the exported schemas at runtime, so they have to ship as
    // assets in the instrumented test APK.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

// Room exports the schema of every database version to app/schemas/. These files are committed:
// they are the input a future migration is written and tested against.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

    // Media3, for the TASK-013 transcode measurement — and provisionally, since that measurement is
    // what decides whether video downscaling gets built at all. If the number says no, this comes
    // straight back out.
    //
    // It has to be `implementation` rather than androidTest, which was tried first and does not
    // work: Transformer needs an application context, the instrumentation context has none, and the
    // GLSL shaders it loads are assets that must live in the same APK as the context it is given.
    // Test-only placement satisfies neither half.
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)
    // media3-container: the MdtaMetadataEntry type the marker probe writes. Provisional, like the
    // rest of media3 here — it stays only if the video proxy-marker path is built on mdta.
    implementation(libs.androidx.media3.container)

    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.okhttp.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Dependency injection
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)

    // Background work
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Security
    implementation(libs.androidx.security.crypto)

    // Microsoft identity (MSAL) — interactive sign-in + silent refresh for Graph
    implementation(libs.msal)

    // Google identity (AppAuth) — interactive sign-in + PKCE + refresh for the Photos Library API
    implementation(libs.appauth)

    // Play Billing — the pro_unlock IAP. CLAUDE.md's monetization section.
    implementation(libs.billing.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit)
    testImplementation(libs.retrofit.converter.kotlinx.serialization)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)


    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
