plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.costalonga.meteofvg"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.costalonga.meteofvg"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Chiave non segreta, depositata di proposito: serve solo a dare a tutti
        // gli APK compilati qui la stessa firma, cosi' gli aggiornamenti si
        // installano sopra i precedenti. Vedi chiavi/LEGGIMI.md.
        create("servizio") {
            storeFile = file("../chiavi/servizio.jks")
            storeType = "PKCS12"
            storePassword = "meteofvg"
            keyAlias = "meteofvg"
            keyPassword = "meteofvg"
        }
    }

    buildTypes {
        // Da firmare con una chiave propria, per chi la ha.
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        // Quella da installare sul telefono: firmata, non apribile dal
        // programma di controllo, senza offuscamento.
        create("pubblica") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("servizio")
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }

        debug {
            applicationIdSuffix = ".prova"
            versionNameSuffix = "-prova"
            signingConfig = signingConfigs.getByName("servizio")
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
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val compose = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(compose)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
