// Dentro uno script Gradle Kotlin "java" e l'estensione del progetto e
// nasconde il package omonimo: il package Properties va importato.
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Credenziali di firma lette da keystore.properties, che resta fuori da git.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "it.iotatec.starkid"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.iotatec.starkid"
        minSdk = 26
        targetSdk = 35
        // Numerazione: 1.0.1, 1.0.2 ... fino a 1.0.99. Il versionCode e
        // l'ultimo numero: Android aggiorna solo se cresce, quindi non va
        // mai riusato ne abbassato.
        versionCode = 1
        versionName = "1.0.1"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Senza keystore.properties la release resta non firmata, cosi il
            // progetto compila anche su una macchina che non ha le chiavi.
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else null
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // LocalLifecycleOwner e repeatOnLifecycle dentro Compose: servono a non
    // far correre i contatori a schermo spento.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
