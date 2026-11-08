// StarKid Genitori - la app con cui il genitore gestisce i tablet.
//
// E una APK a parte, non una schermata dentro il launcher: gira sul telefono
// del genitore, mentre il launcher gira sul tablet del bambino. Stesso
// repository perche e lo stesso prodotto e lo stesso marchio; moduli diversi
// perche non condividono nemmeno una riga di codice utile - il launcher non
// deve portarsi dietro un client HTTP, e questa non deve portarsi dietro i
// giochi.
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Le stesse credenziali di firma del launcher: e lo stesso progetto, e senza
// keystore.properties la release esce non firmata, di proposito.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "it.iotatec.starkid.genitore"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.iotatec.starkid.genitore"
        // 26 come il launcher: sotto non c'e nessun telefono che valga la
        // pena, e il launcher ha gia deciso questa soglia.
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.5"
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
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    // buildConfig: serve a Aggiornamenti.kt, che confronta la versione in
    // esecuzione con quella pubblicata su GitHub. Senza, VERSION_CODE
    // andrebbe scritto a mano in un secondo posto.
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ZXing per il QR di arruolamento. Nel sito il QR e scritto a mano
    // (server/web/qr.js) perche li una dipendenza avrebbe voluto dire un file
    // in piu da servire con la CSP addosso; qui e una libreria Apache-2.0 di
    // mezzo mega che fa una cosa sola e la fa giusta, dentro una app che il
    // genitore installa una volta. Non e il launcher del bambino: li la
    // regola "niente dipendenze" nasce dalla pubblicita e dal peso, e qui
    // non vale nessuna delle due.
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
