plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "org.costalonga.polso"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.costalonga.polso"
        // Health Connect richiede almeno Android 9 (API 28) con l'app Health Connect;
        // sul Magic7 Pro (Android 15) è già integrato nel sistema.
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // Solo arm64: è l'architettura del Magic7 Pro e di quasi tutti i telefoni recenti.
        ndk { abiFilters += setOf("arm64-v8a") }
    }

    signingConfigs {
        // Chiave non segreta, depositata di proposito: vedi chiavi/LEGGIMI.md.
        create("servizio") {
            storeFile = file("../chiavi/servizio.jks")
            storeType = "PKCS12"
            storePassword = "polsoapp"
            keyAlias = "polso"
            keyPassword = "polsoapp"
        }
    }

    buildTypes {
        release {
            // Niente R8: si consegna lo stesso codice eseguito dalle prove automatiche.
            // (La versione ridotta da R8 pesa circa 28 MB ma non può essere provata qui.)
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("servizio")
        }
        debug {
            signingConfig = signingConfigs.getByName("servizio")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.maxHeapSize = "3g" }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi", "ObsoleteLintCustomCheck")
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES")
        jniLibs.useLegacyPackaging = false
        dex.useLegacyPackaging = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemi")
}

dependencies {
    implementation(project(":motore"))

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    val compose = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(compose)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    ksp("androidx.room:room-compiler:2.8.3")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.biometric:biometric:1.1.0")

    // Fonte dati ufficiale: Health Connect (versione stabile).
    implementation("androidx.health.connect:connect-client:1.1.0")
    // IA locale: LiteRT-LM di Google (Apache 2.0), modelli scaricati a richiesta.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("androidx.work:work-testing:2.10.5")
    testImplementation("androidx.room:room-testing:2.8.3")
    testImplementation(compose)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
