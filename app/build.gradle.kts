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
        applicationId  = "it.tugaia56.oxydian"
        minSdk         = 31
        targetSdk      = 34
        versionCode    = 113
        versionName    = "1.1.11"
        buildConfigField("int", "MIN_SDK_VERSION", "$minSdk")
    }
    // Due file dello stesso modulo (stesso pacchetto e stessa firma, non si installano insieme): l'elenco "consigliate" di
    // LSPosed e' scritto nell'APK e non puo' cambiare da ROM a ROM. "oos" = OxygenOS/ColorOS, tutto l'ambito consigliato;
    // "aosp" = Android puro (crDroid, ecc.), solo Impostazioni (vedi src/aosp/res/values/arrays.xml).
    flavorDimensions += "rom"
    productFlavors {
        create("oos") { dimension = "rom" }
        create("aosp") { dimension = "rom" }
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

    // Rename output APK to Oxydian-debug.apk / Oxydian-release-<version>.apk (oos) e Oxydian-Android-debug.apk / Oxydian-Android-release-<version>.apk (aosp)
    // (debug stays unversioned — reinstall.bat/the "reinstall" task below reference it by that fixed name)
    applicationVariants.all {
        val variant = this
        val suffix = if (variant.buildType.name == "release") "-${variant.versionName}" else ""
        val prefix = if (variant.flavorName == "aosp") "Oxydian-Android" else "Oxydian"
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "$prefix-${variant.buildType.name}$suffix.apk"
        }
    }
}
// ── reinstall: disinstalla + installa clean (evita il deploy incrementale di Studio) ──
tasks.register("reinstall") {
    group       = "install"
    description = "Disinstalla l'app e reinstalla l'APK debug via ADB"
    dependsOn("assembleOosDebug")
    doLast {
        val apk = "${projectDir}/build/outputs/apk/oos/debug/Oxydian-debug.apk"
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
    implementation(libs.work.runtime)
    implementation("com.vanniktech:android-image-cropper:4.7.0")
}
