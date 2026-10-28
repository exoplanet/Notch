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
    }

    @Test
    fun onlyThisReposReleaseApkIsAccepted() {
        assertTrue(
            isAllowedDownloadUrl(
                "https://github.com/exoplanet/Notch/releases/download/v1.2/notch-1.2.apk",
            ),
        )
        assertTrue(isAllowedDownloadUrl("https://release-assets.githubusercontent.com/asset?file=notch.apk"))
        assertFalse(isAllowedDownloadUrl("http://github.com/exoplanet/Notch/releases/download/v1.2/notch-1.2.apk"))
        assertFalse(isAllowedDownloadUrl("https://github.com/other/Notch/releases/download/v1.2/notch-1.2.apk"))
        assertFalse(isAllowedDownloadUrl("https://example.com/notch.apk"))
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
}
