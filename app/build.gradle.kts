import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("jacoco")
}

configure<JacocoPluginExtension> {
    toolVersion = "0.8.12"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

extensions.configure<ApplicationExtension> {
    namespace = "com.eepiemi.materialbook"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.astryx.book"
        minSdk = 26
        targetSdk = 36

        // CI passes -PversionNameOverride=<tag-without-v> from the resolved
        // release tag (see resolve-version job in create-release.yml) so the
        // APK's actual version metadata matches what's on GitHub Releases,
        // instead of a value that never changes. Falls back to a fixed local
        // dev default when building without that property (plain PR runs,
        // local `./gradlew assembleDebug`, etc).
        val overrideVersion = (project.findProperty("versionNameOverride") as String?)
            ?.takeIf { it.isNotBlank() }
        if (overrideVersion != null) {
            versionName = overrideVersion
            val parts = overrideVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val major = parts.getOrElse(0) { 0 }
            val minor = parts.getOrElse(1) { 0 }
            val patch = parts.getOrElse(2) { 0 }
            // 3-digit budget each for minor/patch before any collision risk
            // (e.g. v1.0.100 and v1.1.0 must never produce the same code).
            versionCode = major * 1_000_000 + minor * 1_000 + patch
        } else {
            versionName = "1.0.0"
            versionCode = 13
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".test"
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true

            // Fix: Tells Gradle to skip stripping native symbols for local debug builds
            packaging.jniLibs.keepDebugSymbols.add("**/*.so")
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

    lint {
        // Existing upstream locale files intentionally use Android's English
        // fallback for older, not-yet-translated keys. Keep the CI lint gate
        // focused on actionable Android issues instead of legacy translation debt.
        disable += "MissingTranslation"
    }
}

dependencies {
    api(libs.compose.webview.multiplatform)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    testImplementation(libs.playwright)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// AdblockTest drives real browsers against live facebook.com and needs a
// manually-generated login session (see app/src/test/resources/Readme.md) —
// it was never meant to run unattended. Excluded from the default `test`
// task (local and CI alike); run it explicitly with
// `./gradlew test --tests "com.eepiemi.materialbook.AdblockTest"` once
// auth.json is set up.
tasks.withType<Test>().configureEach {
    filter {
        excludeTestsMatching("com.eepiemi.materialbook.AdblockTest")
    }
}

// Combines unit-test + instrumented-test coverage into one report. Both test
// types run as separate CI jobs on separate runners (see ci.yml), so this
// task only *depends on* testDebugUnitTest (regenerates its own .exec fresh,
// cheap, ~30s) — the instrumented .ec file can't be regenerated without a
// device, so ci.yml downloads it as an artifact into place before this runs.
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest", "bundleDebugClassesToRuntimeJar")
    group = "Reporting"
    description = "Generates a combined Jacoco coverage report (unit + instrumented tests)."

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = listOf(
        "**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*Test*.*", "android/**/*.*",
        "**/*\$Lambda\$*.*", "**/*\$inlined\$*.*"
    )

    // Kotlin 2.2's Gradle plugin no longer outputs loose .class files under
    // tmp/kotlin-classes/debug (that path is gone entirely as of this AGP
    // 9.1/Kotlin 2.2/Gradle 9.3.1 combo) — compiled classes are packaged
    // straight into a JAR now. Verified empirically against a local build,
    // not guessed: app/build/intermediates/runtime_app_classes_jar/debug/
    // bundleDebugClassesToRuntimeJar/classes.jar. Re-verify this path if a
    // future toolchain bump breaks the report again — AGP has moved this
    // more than once before.
    classDirectories.setFrom(
        zipTree(layout.buildDirectory.file("intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar")).matching {
            exclude(fileFilter)
        }
    )
    sourceDirectories.setFrom(files("\$projectDir/src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get().asFile) {
            include(
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/**/*.exec",
                "jacoco/testDebugUnitTest.exec",
                "outputs/code_coverage/debugAndroidTest/connected/**/*.ec",
                "outputs/code_coverage/**/*.ec"
            )
        }
    )
}
