import com.android.build.api.artifact.SingleArtifact
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing, read from ~/.gradle/gradle.properties, never from this
// repository (see readme, Releasing). Without all four values the
// release build is produced unsigned, so anyone can still build it.
val rubifyKeystoreFile = providers.gradleProperty("rubifyKeystoreFile").orNull
val rubifyKeystorePassword = providers.gradleProperty("rubifyKeystorePassword").orNull
val rubifyKeyAlias = providers.gradleProperty("rubifyKeyAlias").orNull
val rubifyKeyPassword = providers.gradleProperty("rubifyKeyPassword").orNull
val canSignRelease = listOf(rubifyKeystoreFile, rubifyKeystorePassword, rubifyKeyAlias, rubifyKeyPassword)
    .all { it != null } && file(rubifyKeystoreFile!!).exists()

// Per-ABI APKs plus a universal one, for GitHub releases
// (-PrubifyAbiSplits=true, see readme, Releasing). Off by default,
// so debug builds and the Play bundle, which Play splits itself, are unaffected.
val abiSplits = providers.gradleProperty("rubifyAbiSplits").map(String::toBoolean).getOrElse(false)

android {
    namespace = "com.rubify"
    compileSdk = 36

    defaultConfig {
        // Permanent once the first build is uploaded to Play.
        applicationId = "com.rubify"
        // AccessibilityService.takeScreenshot() is API 30. This is the floor.
        minSdk = 30
        targetSdk = 36
        // versionCode is Play's ordering key: +1 for every uploaded build,
        // never reused. versionName is for people.
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(rubifyKeystoreFile!!)
                storePassword = rubifyKeystorePassword
                keyAlias = rubifyKeyAlias
                keyPassword = rubifyKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // ML Kit ships its own keep rules and the app uses no reflection;
            // assets (the pinyin dictionary) are not touched by shrinking.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    splits {
        abi {
            isEnable = abiSplits
            reset()
            // ML Kit's OCR library ships for exactly these four.
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    lint {
        // Suggests androidx.core-ktx helpers (edit {}, toUri()). The app
        // deliberately doesn't depend on core-ktx for a handful of calls.
        disable += "UseKtx"
    }

    buildFeatures {
        // BuildConfig.DEBUG gates debug-only images and recognized-text logs.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Must match compileOptions above, or AGP fails on inconsistent JVM targets.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // OCR (phase 3). The bundled artifact ships the Chinese model in the APK
    // (~5 MB of assets), so recognition works offline from the first tap. The
    // play-services-mlkit-* variant would download it through Play services.
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

    // @RequiresApi and friends; ML Kit brings an older copy, the app uses
    // it directly, so it is declared here.
    implementation("androidx.annotation:annotation:1.10.0")

    testImplementation("junit:junit:4.13.2")
}

/**
 * Rubify is on-device only (AGENTS.md, Privacy). Libraries can merge network
 * permissions in without any change to our own manifest -- ML Kit does -- so
 * the merged manifest of every variant is checked on its way to packaging.
 */
abstract class VerifyNoNetworkPermission : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val verifiedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest)
        val requested = listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
            val nodes = document.getElementsByTagName(tag)
            (0 until nodes.length).map {
                (nodes.item(it) as Element).getAttributeNS(ANDROID_NAMESPACE, "name")
            }
        }
        val network = requested.filter { it in NETWORK_PERMISSIONS }
        if (network.isNotEmpty()) {
            throw GradleException(
                "Merged manifest requests $network. Rubify is on-device only: remove it with " +
                    "tools:node=\"remove\" in AndroidManifest.xml, or get approval first (AGENTS.md)."
            )
        }
        manifest.copyTo(verifiedManifest.get().asFile, overwrite = true)
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        val NETWORK_PERMISSIONS = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
        )
    }
}

androidComponents {
    onVariants { variant ->
        val verify = tasks.register<VerifyNoNetworkPermission>(
            "verify${variant.name.replaceFirstChar(Char::uppercase)}NoNetworkPermission"
        )
        // A transform sits in the manifest's path, so everything that consumes
        // the merged manifest (packaging, install, bundle) runs the check.
        variant.artifacts.use(verify)
            .wiredWithFiles(
                VerifyNoNetworkPermission::mergedManifest,
                VerifyNoNetworkPermission::verifiedManifest,
            )
            .toTransform(SingleArtifact.MERGED_MANIFEST)
    }
}
