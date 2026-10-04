plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "org.costalonga.sportintv"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.costalonga.sportintv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Indirizzo predefinito del servizio: il file pubblicato da GitHub
        // Actions sul ramo dati-sport-tv di questo deposito. Si può cambiare
        // dalle Impostazioni dell'app. Non è un segreto.
        buildConfigField(
            "String",
            "URL_SERVIZIO",
            "\"https://raw.githubusercontent.com/GuidoCostalonga/GuidoCostalonga.github.io/dati-sport-tv/eventi.json\"",
        )
    }

    signingConfigs {
        // Chiave non segreta, depositata di proposito: serve solo a dare a tutti
        // gli APK compilati qui la stessa firma, così gli aggiornamenti si
        // installano sopra i precedenti. Vedi chiavi/LEGGIMI.md.
        create("servizio") {
            storeFile = file("../chiavi/servizio.jks")
            storeType = "PKCS12"
            storePassword = "sportintv"
            keyAlias = "sportintv"
            keyPassword = "sportintv"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        // Queste dicono solo che esiste una versione più nuova delle librerie.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemi")
}

dependencies {
    implementation(project(":raccolta"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    val compose = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(compose)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Prove dell'app vera sulla JVM (Robolectric): schermate, database, allarmi.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.work:work-testing:2.9.1")
    testImplementation(compose)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
