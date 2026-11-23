import java.io.File
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

// Each distribution channel signs with its own key (DECISIONS.md, "채널별 빌드").
// play   → the Google Play upload key (BEBECUP_RELEASE_*, unchanged since v0.6)
// github → the standalone APK key on GitHub Releases (BEBECUP_GITHUB_*, since v0.8.1)
// Values come from environment variables (CI) or a git-ignored properties file.
fun loadProperties(vararg files: File): Properties = Properties().apply {
  files.firstOrNull { it.exists() }?.inputStream()?.use(::load)
}

val playSigningProperties = loadProperties(
  rootProject.file("release-signing.properties"),
  rootProject.file("signing.properties"),
)
val githubSigningProperties = loadProperties(
  rootProject.file(".keystore/github-release-signing.properties"),
)

data class ChannelSigning(val storeFile: String?, val storePassword: String?, val keyAlias: String?, val keyPassword: String?) {
  val isComplete: Boolean get() = listOf(storeFile, storePassword, keyAlias, keyPassword).all { !it.isNullOrBlank() }
}

fun channelSigning(prefix: String, properties: Properties, legacy: Boolean): ChannelSigning {
  fun value(suffix: String, legacyName: String): String? =
    providers.environmentVariable("${prefix}_$suffix").orNull
      ?: properties.getProperty("${prefix}_$suffix")
      ?: (if (legacy) providers.environmentVariable(legacyName).orNull ?: properties.getProperty(legacyName) else null)
      ?: (if (legacy) providers.environmentVariable("RELEASE_$suffix").orNull else null)
  return ChannelSigning(
    storeFile = value("STORE_FILE", "STORE_FILE"),
    storePassword = value("STORE_PASSWORD", "STORE_PASSWORD"),
    keyAlias = value("KEY_ALIAS", "KEY_ALIAS"),
    keyPassword = value("KEY_PASSWORD", "KEY_PASSWORD"),
  )
}

val playSigning = channelSigning("BEBECUP_RELEASE", playSigningProperties, legacy = true)
val githubSigning = channelSigning("BEBECUP_GITHUB", githubSigningProperties, legacy = false)

val requireReleaseSigning = providers.gradleProperty("bebecup.requireReleaseSigning")
  .map(String::toBoolean)
  .orElse(false)
  .get()

if (requireReleaseSigning) {
  for ((channel, signing) in listOf("play" to playSigning, "github" to githubSigning)) {
    if (!signing.isComplete) {
      throw GradleException(
        "Release signing is required, but the $channel signing values are incomplete. " +
          "Set BEBECUP_RELEASE_* (play) and BEBECUP_GITHUB_* (github): STORE_FILE, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD."
      )
    }
    if (!rootProject.file(signing.storeFile!!).exists()) {
      throw GradleException("Release signing is required, but the $channel keystore does not exist: ${signing.storeFile}")
    }
  }
}

android {
  namespace = "com.bebecup.app"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.bebecup.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 12
    versionName = "0.11.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Make exported Room schemas available to instrumented migration tests.
  sourceSets {
    getByName("androidTest") {
      assets.directories += "$projectDir/schemas"
    }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  signingConfigs {
    for ((name, signing) in listOf("play" to playSigning, "github" to githubSigning)) {
      if (signing.isComplete) {
        create(name) {
          storeFile = rootProject.file(signing.storeFile!!)
          storePassword = signing.storePassword
          keyAlias = signing.keyAlias
          keyPassword = signing.keyPassword
        }
      }
    }
  }

  // One build per distribution channel (DECISIONS.md, "채널별 빌드"). Same
  // applicationId, different signing keys, so one cannot update over the other.
  flavorDimensions += "channel"
  productFlavors {
    create("play") {
      dimension = "channel"
      if (playSigning.isComplete) signingConfig = signingConfigs.getByName("play")
    }
    create("github") {
      dimension = "channel"
      // Where the in-app updater looks for update.json and the APK (src/github/.../GithubUpdates).
      buildConfigField("String", "UPDATE_REPO", "\"jeiel85/bebecup-android\"")
      // SHA-256 of the GitHub APK signing certificate (docs/RELEASE.md). The updater
      // offers an in-place update only to installs signed with it, since Android
      // refuses any other. -Pbebecup.updateSignerSha256=<hex> points it at another key
      // (e.g. the debug key, to try the update flow on an emulator with a re-signed APK).
      val updateSigner = (findProperty("bebecup.updateSignerSha256") as String?)
        ?: "8734b582f9be30b6f11b33ac608f82b4e599adc870da7a0fbbc7c456f1807966"
      require(Regex("[0-9a-f]{64}").matches(updateSigner)) { "bebecup.updateSignerSha256 must be 64 lower-case hex digits" }
      buildConfigField("String", "UPDATE_SIGNER_SHA256", "\"$updateSigner\"")
      if (githubSigning.isComplete) signingConfig = signingConfigs.getByName("github")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

// The github APK is downloaded whole (no Play split delivery) and carries native code for
// four ABIs (ML Kit face detection, LiteRT). Stored uncompressed that is ~60 MB; compressed
// it is about half. Android extracts them at install. The play AAB keeps the default: Play
// already serves each phone only its ABI.
androidComponents {
  onVariants(selector().withFlavor("channel" to "github")) { variant ->
    variant.packaging.jniLibs.useLegacyPackaging.set(true)
  }
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
  // Face detection: the Play build gets the model from Google Play services
  // (small AAB); the standalone GitHub APK bundles it so it also works on phones
  // without Play services. Both expose the same com.google.mlkit.vision.face API.
  "playImplementation"(libs.play.services.base)
  "playImplementation"(libs.play.services.mlkit.face.detection)
  "githubImplementation"(libs.mlkit.face.detection)
  // Play build only: asks the Google Play app whether a newer version is out (home banner).
  // Play installs it; the app never does.
  "playImplementation"(libs.play.app.update)
  // Telling siblings apart (아이 얼굴 구분): an SFace face-embedding model run with TensorFlow
  // Lite. The model file (~10 MB) is not in either build; the app downloads it when the parent
  // turns the feature on (ai/faceid/FaceIdModelStore). The interpreter itself comes from Google
  // Play services in the play build and is bundled (LiteRT) in the github build, like ML Kit.
  "playImplementation"(libs.play.services.tflite.java)
  "githubImplementation"(libs.litert)
  // implementation(libs.play.services.location)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.work.testing)
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
