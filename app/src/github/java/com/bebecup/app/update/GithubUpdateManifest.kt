package com.bebecup.app.update

import org.json.JSONObject

/**
 * `update.json`, attached by the release workflow to every GitHub Release:
 *
 * ```json
 * {"versionCode": 10, "versionName": "0.9.0", "apk": "bebecup-v0.9.0.apk",
 *  "sha256": "<64 hex>", "size": 6123456}
 * ```
 *
 * The APK URL is built here from the repository, the tag (`v` + versionName)
 * and the file name, never taken from the file, so a changed manifest can only
 * point at an asset of this repository's own release. Anything that does not
 * look exactly like that is rejected (null).
 */
data class GithubUpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkName: String,
    val sha256: String,
    val sizeBytes: Long,
) {
    fun apkUrl(repo: String): String = "https://github.com/$repo/releases/download/v$versionName/$apkName"

    companion object {
        /** Stable address of the newest release's manifest; GitHub redirects it to the asset. */
        fun latestUrl(repo: String): String = "https://github.com/$repo/releases/latest/download/update.json"

        const val MAX_APK_BYTES = 150L * 1024 * 1024

        private val VERSION_NAME = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}""")
        private val APK_NAME = Regex("""[A-Za-z0-9._-]{1,80}\.apk""")
        private val SHA256 = Regex("""[0-9a-f]{64}""")

        fun parse(json: String): GithubUpdateManifest? = try {
            val o = JSONObject(json)
            val manifest = GithubUpdateManifest(
                versionCode = o.getInt("versionCode"),
                versionName = o.getString("versionName"),
                apkName = o.getString("apk"),
                sha256 = o.getString("sha256").lowercase(),
                sizeBytes = o.getLong("size"),
            )
            manifest.takeIf {
                it.versionCode > 0 &&
                    VERSION_NAME.matches(it.versionName) &&
                    APK_NAME.matches(it.apkName) && !it.apkName.startsWith(".") &&
                    SHA256.matches(it.sha256) &&
                    it.sizeBytes in 1..MAX_APK_BYTES
            }
        } catch (_: Exception) {
            null
        }
    }
}
