import java.io.File
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

val releaseSigningPropertiesFile = rootProject.file("release-signing.properties")
val legacySigningPropertiesFile = rootProject.file("signing.properties")
val releaseSigningProperties = Properties().apply {
  when {
    releaseSigningPropertiesFile.exists() -> releaseSigningPropertiesFile.inputStream().use(::load)
    legacySigningPropertiesFile.exists() -> legacySigningPropertiesFile.inputStream().use(::load)
  }
}

fun signingValue(name: String, legacyName: String): String? =
  providers.environmentVariable(name).orNull
    ?: providers.environmentVariable(legacyName).orNull
    ?: releaseSigningProperties.getProperty(name)
    ?: releaseSigningProperties.getProperty(legacyName)

val releaseStoreFile = signingValue("BEBECUP_RELEASE_STORE_FILE", "STORE_FILE")
  ?: providers.environmentVariable("RELEASE_STORE_FILE").orNull
val releaseStorePassword = signingValue("BEBECUP_RELEASE_STORE_PASSWORD", "STORE_PASSWORD")
  ?: providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = signingValue("BEBECUP_RELEASE_KEY_ALIAS", "KEY_ALIAS")
  ?: providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = signingValue("BEBECUP_RELEASE_KEY_PASSWORD", "KEY_PASSWORD")
  ?: providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigningConfig = listOf(
  releaseStoreFile,
  releaseStorePassword,
  releaseKeyAlias,
  releaseKeyPassword,
).all { !it.isNullOrBlank() }
val requireReleaseSigning = providers.gradleProperty("bebecup.requireReleaseSigning")
  .map(String::toBoolean)
  .orElse(false)
  .get()

if (requireReleaseSigning && !hasReleaseSigningConfig) {
  throw GradleException(
    "Release signing is required, but one or more signing values are missing. " +
      "Set BEBECUP_RELEASE_STORE_FILE, BEBECUP_RELEASE_STORE_PASSWORD, " +
      "BEBECUP_RELEASE_KEY_ALIAS, and BEBECUP_RELEASE_KEY_PASSWORD."
  )
}

if (requireReleaseSigning && !rootProject.file(releaseStoreFile!!).exists()) {
  throw GradleException("Release signing is required, but the keystore file does not exist: $releaseStoreFile")
}

abstract class ExportReleaseToDesktopTask : DefaultTask() {
  @get:Input
  abstract val releaseVersionName: Property<String>

  @get:Input
  abstract val releaseVersionCode: Property<Int>

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val releaseAab: RegularFileProperty

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val koChangelog: RegularFileProperty

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val enChangelog: RegularFileProperty

  @TaskAction
  fun export() {
    val home = File(System.getProperty("user.home"))
    val candidates = listOf(
      File(home, "OneDrive/바탕 화면"),
      File(home, "OneDrive/Desktop"),
      File(home, "Desktop"),
    )
    val desktop = candidates.firstOrNull { it.isDirectory }
      ?: throw GradleException(
        "Could not find a Desktop directory. Tried:\n" +
          candidates.joinToString("\n") { "  - ${it.absolutePath}" }
      )

    val aab = releaseAab.get().asFile
    if (!aab.isFile) {
      throw GradleException("Release AAB not found at ${aab.absolutePath}. Check the bundleRelease log.")
    }
    val versionName = releaseVersionName.get()
    val versionCode = releaseVersionCode.get()
    // Per the user's cross-project convention: artifacts land flat in Desktop\Build\
    // named <project>-v<semver>-vc<code> so multiple builds never collide.
    val buildDir = File(desktop, "Build").apply { mkdirs() }
    val aabTarget = File(buildDir, "bebecup-v$versionName-vc$versionCode.aab")
    aab.copyTo(aabTarget, overwrite = true)
    logger.lifecycle("Wrote ${aabTarget.absolutePath} (${aab.length()} bytes)")

    val koSrc = koChangelog.get().asFile
    val enSrc = enChangelog.get().asFile
    listOf(koSrc, enSrc).forEach { f ->
      if (!f.isFile) {
        throw GradleException(
          "Missing fastlane changelog: ${f.absolutePath}. " +
            "Author both ko-KR and en-US changelogs for versionCode ${releaseVersionCode.get()} before cutting."
        )
      }
      val len = f.readText().length
      if (len > 500) {
        throw GradleException("${f.name} is $len chars; Play Console limit is 500 per locale.")
      }
    }

    val txtTarget = File(buildDir, "bebecup-v$versionName-vc$versionCode-release-notes.txt")
    txtTarget.writeText(
      buildString {
        append("<ko-KR>\n")
        append(koSrc.readText().trim())
        append("\n</ko-KR>\n")
        append("<en-US>\n")
        append(enSrc.readText().trim())
        append("\n</en-US>\n")
      }
    )
    logger.lifecycle("Wrote ${txtTarget.absolutePath}")
  }
}

android {
  namespace = "com.bebecup.app"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.bebecup.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 4
    versionName = "0.6.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Make exported Room schemas available to instrumented migration tests.
  sourceSets {
    getByName("androidTest") {
      assets.srcDirs(files("$projectDir/schemas"))
    }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  signingConfigs {
    if (hasReleaseSigningConfig) {
      create("release") {
        storeFile = rootProject.file(releaseStoreFile!!)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseSigningConfig) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Export Room schemas so we can author + verify explicit migrations.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

val defaultVersionName = android.defaultConfig.versionName
  ?: throw GradleException("versionName is not set in defaultConfig")
val defaultVersionCode = android.defaultConfig.versionCode
  ?: throw GradleException("versionCode is not set in defaultConfig")

val exportReleaseToDesktop by tasks.registering(ExportReleaseToDesktopTask::class) {
  group = "bebecup"
  description = "Copies the release AAB and bilingual Play release-notes TXT to the user's Desktop"

  dependsOn("bundleRelease")

  releaseVersionName.set(defaultVersionName)
  releaseVersionCode.set(defaultVersionCode)
  releaseAab.set(layout.buildDirectory.file("outputs/bundle/release/app-release.aab"))
  koChangelog.set(rootProject.layout.projectDirectory.file("fastlane/metadata/android/ko-KR/changelogs/$defaultVersionCode.txt"))
  enChangelog.set(rootProject.layout.projectDirectory.file("fastlane/metadata/android/en-US/changelogs/$defaultVersionCode.txt"))
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.mlkit.face.detection)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.room.testing)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
