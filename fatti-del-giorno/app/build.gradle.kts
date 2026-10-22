plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "org.costalonga.fattidelgiorno"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.costalonga.fattidelgiorno"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Chiave non segreta, depositata di proposito: serve solo a dare a tutti
        // gli APK compilati qui la stessa firma, così gli aggiornamenti si
        // installano sopra i precedenti. Vedi chiavi/LEGGIMI.md.
        create("servizio") {
            storeFile = file("../chiavi/servizio.jks")
            storeType = "PKCS12"
            storePassword = "fattidelgiorno"
            keyAlias = "fattidelgiorno"
            keyPassword = "fattidelgiorno"
        }
    }

    buildTypes {
        // Da firmare con una chiave propria, per chi la ha.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        // Quella da installare sul telefono: firmata con la chiave di servizio.
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
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        // Il controllo del codice è un passo a sé (./gradlew lintPubblica).
        checkReleaseBuilds = false
        // Queste dicono solo che esiste una versione più nuova delle librerie.
        disable += setOf(
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "NewerVersionAvailable",
            "RiskyLibrary",
            "OutdatedLibrary",
            "OldTargetApi",
        )
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    val compose = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(compose)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // Prove sulla JVM delle parti che toccano Android (appunti, condivisione).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
}
