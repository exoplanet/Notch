package com.exoplanet.notch.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateTest {
    @Test
    fun newerTagsCompareByNumber() {
        assertTrue(isNewerRelease("v1.2", "1.1"))
        assertTrue(isNewerRelease("v1.1.1", "1.1"))
        assertFalse(isNewerRelease("v1.1", "1.1"))
        assertFalse(isNewerRelease("v1.1.0", "1.1"))
        assertFalse(isNewerRelease("v1.0", "1.1"))
        assertTrue(isNewerRelease("v1.10", "1.9"))
        assertFalse(isNewerRelease("v1.6", "1.6.0"))
        assertFalse(isNewerRelease("v1.6", "1.6.1"))
        assertTrue(isNewerRelease("V1.6", "1.5"))
        assertFalse(isNewerRelease("v1.6-rc1", "1.6"))
        assertFalse(isNewerRelease("", "1.6"))
    }

    @Test
    fun onlyThisReposReleaseApkIsAccepted() {
        assertTrue(
            isAllowedDownloadUrl(
                "https://github.com/exoplanet/Notch/releases/download/v1.2/notch-1.2.apk",
            ),
        )
        assertFalse(isAllowedDownloadUrl("https://release-assets.githubusercontent.com/asset?file=notch.apk"))
        assertFalse(
            isAllowedDownloadUrl(
                "https://objects.githubusercontent.com/github-production-release-asset/notch.apk",
            ),
        )
        assertFalse(isAllowedDownloadUrl("http://github.com/exoplanet/Notch/releases/download/v1.2/notch-1.2.apk"))
        assertFalse(isAllowedDownloadUrl("https://github.com/other/Notch/releases/download/v1.2/notch-1.2.apk"))
        assertFalse(isAllowedDownloadUrl("https://example.com/notch.apk"))
        assertTrue(
            isAllowedDownloadUrl(
                "https://GITHUB.com/exoplanet/Notch/releases/download/v1.6/notch-1.6.APK",
            ),
        )
        assertTrue(
            isAllowedDownloadUrl(
                "https://github.com/exoplanet/Notch/releases/download/v1.6/notch-1.6.apk?x=1",
            ),
        )
        assertFalse(
            isAllowedDownloadUrl(
                "https://user:pass@github.com/exoplanet/Notch/releases/download/v1.6/notch-1.6.apk",
            ),
        )
        assertFalse(
            isAllowedDownloadUrl(
                "https://github.com/exoplanet/Notch/releases/download/v1.6/../../notch-1.6.apk",
            ),
        )
        assertFalse(
            isAllowedDownloadUrl(
                "https://github.com/exoplanet/Notch/releases/download/%2e%2e/notch.apk",
            ),
        )
        assertFalse(isAllowedDownloadUrl("https://objects.githubusercontent.com.evil/foo.apk"))
        assertFalse(isAllowedDownloadUrl("not a url"))
    }

    @Test
    fun cdnHostsAreAcceptedOnlyAsRedirects() {
        assertTrue(
            isAllowedRedirectUrl(
                "https://release-assets.githubusercontent.com/asset?file=notch.apk",
            ),
        )
        assertTrue(
            isAllowedRedirectUrl(
                "https://objects.githubusercontent.com/github-production-release-asset/123",
            ),
        )
        assertTrue(
            isAllowedRedirectUrl(
                "https://github.com/exoplanet/Notch/releases/download/v1.6/notch-1.6.apk",
            ),
        )
        assertFalse(
            isAllowedRedirectUrl(
                "https://objects.githubusercontent.com/a/../../secret.apk",
            ),
        )
        assertFalse(isAllowedRedirectUrl("https://user@objects.githubusercontent.com/asset"))
        assertFalse(isAllowedRedirectUrl("http://objects.githubusercontent.com/asset"))
        assertFalse(isAllowedRedirectUrl("https://example.com/notch.apk"))
        assertFalse(isAllowedRedirectUrl("https://objects.githubusercontent.com.evil/foo.apk"))
    }

    @Test
    fun updateMustBeThisPackageAndTheUploadKey() {
        assertTrue(isTrustedUpdate(UPDATE_PACKAGE, listOf(UPLOAD_CERT_SHA256)))
        assertTrue(isTrustedUpdate(UPDATE_PACKAGE, listOf(UPLOAD_CERT_SHA256.uppercase())))
        assertFalse(isTrustedUpdate("com.example.other", listOf(UPLOAD_CERT_SHA256)))
        assertFalse(isTrustedUpdate(UPDATE_PACKAGE, emptyList()))
        assertFalse(isTrustedUpdate(UPDATE_PACKAGE, listOf("aa")))
        assertFalse(isTrustedUpdate(UPDATE_PACKAGE, listOf(UPLOAD_CERT_SHA256, UPLOAD_CERT_SHA256)))
        assertFalse(isTrustedUpdate(null, listOf(UPLOAD_CERT_SHA256)))
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun parserPicksTheNotchApk() {
        val json = """
            {
              "tag_name": "v1.2",
              "assets": [
                {"name": "notes.txt", "browser_download_url": "https://github.com/exoplanet/Notch/releases/download/v1.2/notes.txt"},
                {"name": "extra.apk", "browser_download_url": "https://github.com/exoplanet/Notch/releases/download/v1.2/extra.apk"},
                {"name": "notch-1.2.apk", "browser_download_url": "https://github.com/exoplanet/Notch/releases/download/v1.2/notch-1.2.apk"}
              ]
            }
        """.trimIndent()
        val release = parseLatestRelease(json)
        assertEquals("v1.2", release.tag)
        assertEquals("notch-1.2.apk", release.apkName)
        assertEquals(
            "https://github.com/exoplanet/Notch/releases/download/v1.2/notch-1.2.apk",
            release.apkUrl,
        )
    }

    @Test
    fun parserKeepsTheNotchApkWhenALaterApkIsGeneric() {
        val json = """
            {
              "tag_name": "v1.6",
              "assets": [
                {"name": "extra.apk", "browser_download_url": "https://github.com/exoplanet/Notch/releases/download/v1.6/extra.apk"},
                {"name": "notch-1.6.apk", "browser_download_url": "https://github.com/exoplanet/Notch/releases/download/v1.6/notch-1.6.apk"},
                {"name": "other.apk", "browser_download_url": "https://github.com/exoplanet/Notch/releases/download/v1.6/other.apk"}
              ]
            }
        """.trimIndent()
        val release = parseLatestRelease(json)
        assertEquals("notch-1.6.apk", release.apkName)
    }

    @Test
    fun parserAcceptsTheOnlyApkWhenItIsNotNamedNotch() {
        val json = """
            {
              "tag_name": "v1.6",
              "assets": [
                {"name": "notes.txt", "browser_download_url": "https://github.com/exoplanet/Notch/releases/download/v1.6/notes.txt"},
                {"name": "extra.apk", "browser_download_url": "https://github.com/exoplanet/Notch/releases/download/v1.6/extra.apk"}
              ]
            }
        """.trimIndent()
        val release = parseLatestRelease(json)
        assertEquals("extra.apk", release.apkName)
    }
}
