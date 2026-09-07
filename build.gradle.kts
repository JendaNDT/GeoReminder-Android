plugins {
    // API 36 je oficiálně podporované od AGP 8.10; řada 8.11 vyžaduje Gradle 8.13 + JDK 17.
    id("com.android.application") version "8.11.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
