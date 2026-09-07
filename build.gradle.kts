plugins {
    // API 36 je oficiálně podporované od AGP 8.10; řada 8.11 vyžaduje Gradle 8.13 + JDK 17.
    id("com.android.application") version "8.11.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}

// Android instrumentation testy přidáváme centrálně, aby app/build.gradle.kts
// nemusel být přepisován jen kvůli testovacím závislostem.
subprojects {
    pluginManager.withPlugin("com.android.application") {
        dependencies.add("androidTestImplementation", "androidx.test.ext:junit:1.2.1")
        dependencies.add("androidTestImplementation", "androidx.test:core-ktx:1.6.1")
        dependencies.add("androidTestImplementation", "androidx.test:runner:1.6.2")
    }
}
