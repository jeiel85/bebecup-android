import java.io.File
import java.util.Properties

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

android {
  namespace = "com.bebecup.app"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.bebecup.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 7
    versionName = "0.7.1"

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
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.play.services.base)
  implementation(libs.play.services.mlkit.face.detection)
  // Asks the Google Play app whether a newer version is out (home banner). Play installs it; the app never does.
  implementation(libs.play.app.update)
  // implementation(libs.play.services.location)
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
}
