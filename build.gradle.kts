// Toolchain versions are one decision, not three. AGP 8.10+ is needed to
// compile against SDK 36, KGP 2.2.20 is supported up to AGP 8.11.1, and
// Gradle 8.13 (gradle/wrapper/gradle-wrapper.properties) is AGP 8.11's
// minimum. Move them together, never one at a time.
plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
}
