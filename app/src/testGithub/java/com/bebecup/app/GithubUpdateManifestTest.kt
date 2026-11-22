package com.bebecup.app

import com.bebecup.app.update.GithubUpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** org.json is an Android class, so this runs on Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GithubUpdateManifestTest {

    private val sha = "ab".repeat(32)

    private fun json(
        versionCode: Any = 10,
        versionName: Any = "\"0.9.0\"",
        apk: Any = "\"bebecup-v0.9.0.apk\"",
        sha256: Any = "\"$sha\"",
        size: Any = 6_123_456,
    ) = """{"versionCode": $versionCode, "versionName": $versionName, "apk": $apk, "sha256": $sha256, "size": $size}"""

    @Test
    fun `parses the manifest the release workflow writes`() {
        val m = GithubUpdateManifest.parse(json())
        assertNotNull(m)
        m!!
        assertEquals(10, m.versionCode)
        assertEquals("0.9.0", m.versionName)
        assertEquals("bebecup-v0.9.0.apk", m.apkName)
        assertEquals(sha, m.sha256)
        assertEquals(6_123_456L, m.sizeBytes)
    }

    @Test
    fun `apk url is built from this repository and the version tag`() {
        val m = GithubUpdateManifest.parse(json())!!
        assertEquals(
            "https://github.com/jeiel85/bebecup-android/releases/download/v0.9.0/bebecup-v0.9.0.apk",
            m.apkUrl("jeiel85/bebecup-android")
        )
        assertEquals(
            "https://github.com/jeiel85/bebecup-android/releases/latest/download/update.json",
            GithubUpdateManifest.latestUrl("jeiel85/bebecup-android")
        )
    }

    @Test
    fun `upper-case sha256 is accepted and normalised`() {
        val m = GithubUpdateManifest.parse(json(sha256 = "\"${sha.uppercase()}\""))
        assertEquals(sha, m?.sha256)
    }

    @Test
    fun `anything that could point elsewhere or is malformed is rejected`() {
        val rejected = listOf(
            "not json",
            "{}",
            json(versionCode = 0),
            json(versionCode = -3),
            json(versionName = "\"0.9\""),
            json(versionName = "\"0.9.0/../../evil\""),
            json(versionName = "\"v0.9.0\""),
            json(apk = "\"../other.apk\""),
            json(apk = "\"https://evil.example/app.apk\""),
            json(apk = "\"bebecup.zip\""),
            json(apk = "\".apk\""),
            json(sha256 = "\"1234\""),
            json(sha256 = "\"${"zz".repeat(32)}\""),
            json(size = 0),
            json(size = GithubUpdateManifest.MAX_APK_BYTES + 1),
        )
        rejected.forEach { assertNull(it, GithubUpdateManifest.parse(it)) }
    }
}
