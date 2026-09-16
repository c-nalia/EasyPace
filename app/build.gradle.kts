// ============================================================================
//  app/build.gradle.kts  —  configuracao do modulo do aplicativo
// ----------------------------------------------------------------------------
//  PONTOS QUE VOCE VAI MEXER COM MAIS FREQUENCIA:
//   * versionCode / versionName  -> a cada nova versao publicada
//   * minSdk                     -> aparelhos mais antigos suportados
//   * dependencies { }           -> novas bibliotecas
// ============================================================================
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------
// Assinatura de release (opcional).
// Crie um arquivo `keystore.properties` na RAIZ do projeto com:
//     storeFile=C:/caminho/para/minha.jks
//     storePassword=...
//     keyAlias=...
//     keyPassword=...
// Se o arquivo nao existir, o build de release sai sem assinatura (util para
// uso pessoal via `adb install` de um build debug).
// ---------------------------------------------------------------------------
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "br.easypace.codec"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.easypace.codec"
        minSdk = 26          // Android 8.0 — necessario para AudioTrack.Builder e canais de notificacao
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (keystoreProps.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            // Minificacao (R8) DESLIGADA de proposito.
            // Ela deixaria o APK ~40% menor, mas pode remover codigo que so e
            // usado por reflexao e causar erro em tempo de execucao - e voce
            // nao tem depuracao USB para investigar. Se um dia quiser ligar,
            // troque as duas linhas para `true`, gere o APK, e teste TODAS as
            // telas antes de confiar nele.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // O lint continua ligado e continua abortando o build de release em
        // erro de verdade. Aqui so desligamos UMA checagem, que e falso
        // positivo neste projeto:
        //
        //   InvalidFragmentVersionForActivityResult
        //     Avisa que registerForActivityResult exige androidx.fragment
        //     >= 1.3.0. O alerta existe porque versoes antigas de
        //     FragmentActivity nao chamavam super.onRequestPermissionsResult().
        //     O EasyPace nao usa Fragment em lugar nenhum - a MainActivity e
        //     uma ComponentActivity pura, e a biblioteca fragment nem esta no
        //     classpath. Sem a lib para inspecionar, o lint assume o pior.
        //
        // Se um dia voce adicionar Fragments ao projeto, APAGUE esta linha.
        disable += "InvalidFragmentVersionForActivityResult"
    }
}

dependencies {
    // --- Base Android / Kotlin ---------------------------------------------
    implementation("androidx.core:core-ktx:1.13.1")

    // --- Ciclo de vida e servicos ------------------------------------------
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    // --- Jetpack Compose (UI declarativa) ----------------------------------
    // O "BOM" fixa versoes compativeis entre si; por isso as libs abaixo
    // aparecem sem numero de versao.
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Localizacao (GPS fundido, mais estavel que o LocationManager cru) --
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // --- Testes -------------------------------------------------------------
    testImplementation("junit:junit:4.13.2")
}
