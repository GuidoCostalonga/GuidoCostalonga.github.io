// Versioni dichiarate una volta sola: i moduli le ereditano.
// Kotlin 2.3 serve a leggere le classi di LiteRT-LM (compilate con Kotlin 2.4).
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
