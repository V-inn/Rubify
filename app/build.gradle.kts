import com.android.build.api.artifact.SingleArtifact
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rubify"
    compileSdk = 36

    defaultConfig {
        // Permanent once the first build is uploaded to Play.
        applicationId = "com.rubify"
        // AccessibilityService.takeScreenshot() is API 30. This is the floor.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            // Enabled once a release build is verified on a device with ML Kit's
            // consumer keep rules.
            isMinifyEnabled = false
        }
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
