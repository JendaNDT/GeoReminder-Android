import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Klíč pro Google Maps se čte ze souboru mapskey.properties v kořeni projektu
// (řádek: MAPS_API_KEY=AIza...). Bez klíče se appka sestaví, jen mapa zůstane prázdná.
val mapsKey: String = run {
    val f = rootProject.file("mapskey.properties")
    if (f.exists()) {
        Properties().apply { f.inputStream().use { load(it) } }
            .getProperty("MAPS_API_KEY", "")
    } else ""
}.ifBlank { "PLACEHOLDER_MAPS_KEY" }

android {
    namespace = "cz.jenda.georeminder"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.jenda.georeminder"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "2.8"
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
    }

    // Podpisový keystore není v git repozitáři (bezpečnost) – žije v Jendově
    // lokální kopii projektu. Bez něj se použije běžný debug podpis.
    val keystoreFile = file("georeminder.keystore")

    signingConfigs {
        create("georeminder") {
            storeFile = keystoreFile
            storePassword = "georeminder"
            keyAlias = "georeminder"
            keyPassword = "georeminder"
        }
    }

    buildTypes {
        release {
            // Minifikace/R8 zatím VYPNUTÁ – zapnout až po úspěšném testu na zařízení
            // (proguard-rules.pro chrání JSON modely kompatibilní s iOS).
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("georeminder")
            }
        }
        debug {
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("georeminder")
            }
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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.google.maps.android:maps-compose:6.1.2")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("androidx.glance:glance-appwidget:1.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
