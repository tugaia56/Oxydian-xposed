import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Load keystore credentials from project-root keystore.properties (gitignored)
val keystoreProps = Properties()
val keystoreFile = rootProject.file("keystore.properties")
if (keystoreFile.exists()) keystoreProps.load(keystoreFile.inputStream())

android {
    namespace   = "it.tugaia56.obsidian"
    compileSdk  = 34
    defaultConfig {
        applicationId  = "it.tugaia56.obsidian"
        minSdk         = 31
        targetSdk      = 34
        versionCode    = 103
        versionName    = "1.0.3"
        buildConfigField("int", "MIN_SDK_VERSION", "$minSdk")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures { viewBinding = true; buildConfig = true }

    signingConfigs {
        create("release") {
            if (keystoreFile.exists()) {
                storeFile     = file(keystoreProps["storeFile"]     as String)
                storePassword = keystoreProps["storePassword"]      as String
                keyAlias      = keystoreProps["keyAlias"]           as String
                keyPassword   = keystoreProps["keyPassword"]        as String
            }
        }
    }

    buildTypes {
        release {
            signingConfig  = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Estrae i .so su disco invece di mmap-parli dall'APK — serve al compilatore overlay
    // (icon pack Impostazioni) per poter symlinkare libaapt2.so/libzipalign.so come binari
    // eseguibili in un percorso reale (BinaryInstaller.symLinkBinaries). Le esclusioni
    // evitano i classici conflitti "more than one file found" con BouncyCastle (usata dal
    // firmatore APK custom).
    packaging {
        jniLibs.excludes += setOf(
            "/META-INF/*",
            "/META-INF/versions/**",
            "/org/bouncycastle/**"
        )
        resources.excludes += setOf(
            "/META-INF/*",
            "/META-INF/versions/**",
            "/org/bouncycastle/**"
        )
        jniLibs.useLegacyPackaging = true
    }

    // Rename output APK from app-debug.apk / app-release.apk to Oxydian-debug.apk / Oxydian-release-<version>.apk
    // (debug stays unversioned — reinstall.bat/the "reinstall" task below reference it by that fixed name)
    applicationVariants.all {
        val variant = this
        val suffix = if (variant.name == "release") "-${variant.versionName}" else ""
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "Oxydian-${variant.name}$suffix.apk"
        }
    }
}
// ── reinstall: disinstalla + installa clean (evita il deploy incrementale di Studio) ──
tasks.register("reinstall") {
    group       = "install"
    description = "Disinstalla l'app e reinstalla l'APK debug via ADB"
    dependsOn("assembleDebug")
    doLast {
        val apk = "${projectDir}/build/outputs/apk/debug/Oxydian-debug.apk"
        fun adb(vararg args: String) {
            ProcessBuilder(listOf("adb") + args.toList())
                .inheritIO().start().waitFor()
        }
        adb("uninstall", "it.tugaia56.obsidian") // ignora errore se non installata
        adb("install", "-r", apk)
        println("Oxydian reinstallata. Premi 'Riavvia SystemUI' nell'app.")
    }
}

dependencies {
    // Xposed API — local jar only, not from Maven
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(libs.android.appcompat)
    implementation(libs.android.material)
    implementation(libs.android.recyclerview)
    implementation(libs.eventbus)
    implementation(libs.colorpicker)
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    implementation(libs.remotepreferences)
    implementation(libs.android.documentfile)
    implementation(libs.android.biometric)
    implementation(libs.lottie)
    implementation(libs.bcpkix)
    implementation(libs.work.runtime)
    implementation("com.vanniktech:android-image-cropper:4.7.0")
}
