// ============================================================================
//  build.gradle.kts (raiz)  —  declara os plugins usados pelos modulos.
//  `apply false` = so registra a versao; quem aplica de fato e o app/build.gradle.kts
// ============================================================================
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
