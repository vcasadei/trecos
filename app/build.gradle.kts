plugins {
    jacoco
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "app.trecos"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.trecos"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Feature flags (design D19): off until their releases are ready. Turn one on
        // for a local build with -Ptrecos.driveSync=true or -Ptrecos.encryption=true.
        buildConfigField("boolean", "FEATURE_DRIVE_SYNC", (findProperty("trecos.driveSync") ?: "false").toString())
        buildConfigField("boolean", "FEATURE_ENCRYPTION", (findProperty("trecos.encryption") ?: "false").toString())
        // The benchmark seed receiver (task 14.8) is on only in benchmarkRelease; see androidComponents below.
        buildConfigField("boolean", "BENCHMARK_SEED", "false")
        manifestPlaceholders["benchmarkSeed"] = "false"
    }

    // Release signing reads only these environment variables (design D22); CI sets
    // them from the release secrets. Without them the release build is unsigned.
    val keystoreFile = System.getenv("TRECOS_KEYSTORE_FILE")
    if (keystoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("TRECOS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TRECOS_KEY_ALIAS")
                keyPassword = System.getenv("TRECOS_KEY_PASSWORD")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Migration tests (Robolectric, so CI needs no emulator) read the exported
    // schemas as assets. Unit tests only see the tested variant's assets, so the
    // schemas go into debug builds; release builds never contain them.
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")
}

/**
 * Bundles the user FAQ (`docs/user/<language>/faq.md`, design D18) as
 * `assets/faq/<language>.md`, so the in-app FAQ and the published guide share one source.
 */
abstract class FaqAssets : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val docs: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = output.get().asFile.resolve("faq")
        out.deleteRecursively()
        out.mkdirs()
        docs.get().asFile.listFiles().orEmpty().filter { it.resolve("faq.md").exists() }.forEach { language ->
            language.resolve("faq.md").copyTo(out.resolve("${language.name}.md"), overwrite = true)
        }
    }
}

val faqAssets = tasks.register<FaqAssets>("faqAssets") {
    docs.set(rootProject.layout.projectDirectory.dir("docs/user"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(faqAssets, FaqAssets::output)
        if (variant.name == "benchmarkRelease") {
            variant.manifestPlaceholders.put("benchmarkSeed", "true")
            variant.buildConfigFields?.put("BENCHMARK_SEED", com.android.build.api.variant.BuildConfigField("boolean", "true", "Benchmark seeding"))
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

// Coverage counts non-UI code only: screens are covered by screenshot tests instead.
val coverageExcludes = listOf(
    "app/trecos/ui/**",
    "app/trecos/MainActivity*",
    "**/ComposableSingletons*",
    "**/*_Impl*",
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
)

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoCoverageVerification>("jacocoCoverageVerification") {
    description = "Fails when line coverage of non-UI code drops below 80%."
    group = "verification"
    dependsOn("testDebugUnitTest")
    executionData.setFrom(
        layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"),
    )
    classDirectories.setFrom(
        layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")
            .map { dir -> fileTree(dir) { exclude(coverageExcludes) } },
    )
    sourceDirectories.setFrom("src/main/kotlin")
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)
    implementation(libs.androidx.print)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.auth)
    implementation(libs.billing.ktx)
    implementation(libs.play.review.ktx)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.androidx.work.testing)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
