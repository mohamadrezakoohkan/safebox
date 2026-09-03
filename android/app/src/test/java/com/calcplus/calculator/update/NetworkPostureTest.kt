package com.calcplus.calculator.update

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.core.update.UpdateEndpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the network posture decided in §13: exactly one permission, three
 * compiled-in https URLs, and no other host anywhere. Reads the MERGED
 * manifest, so a dependency quietly merging a permission back in fails here.
 */
@RunWith(RobolectricTestRunner::class)
class NetworkPostureTest {
    private val requestedPermissions: List<String>
        get() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val info = context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            return info.requestedPermissions?.toList().orEmpty()
        }

    @Test
    fun internetIsTheOnlyPermissionTheAppDeclares() {
        // Filtered to the platform namespace: androidx.core injects its own
        // self-defined `<package>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
        // signature permission, which grants nothing and is not an OS
        // capability the app-info screen lists.
        val platform = requestedPermissions.filter { it.startsWith("android.permission.") }
        assertEquals(listOf("android.permission.INTERNET"), platform)
    }

    @Test
    fun media3sMergedPermissionsStayRemoved() {
        assertFalse(requestedPermissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse(requestedPermissions.contains("android.permission.WAKE_LOCK"))
    }

    @Test
    fun theThreeEndpointsAreHttpsAndPointAtTheProjectRepo() {
        val all = listOf(
            UpdateEndpoints.SOURCE_URL,
            UpdateEndpoints.VERSION_MANIFEST_URL,
            UpdateEndpoints.RELEASES_URL,
        )
        for (url in all) {
            assertTrue("must be https: $url", url.startsWith("https://"))
            assertTrue("must name the project repo: $url", url.contains("mohamadrezakoohkan/safebox"))
        }
    }

    @Test
    fun theManifestUrlIsAQueryLessRawGithubPath() {
        val url = UpdateEndpoints.VERSION_MANIFEST_URL
        assertEquals(
            "https://raw.githubusercontent.com/mohamadrezakoohkan/safebox/main/version.json",
            url,
        )
        // No query string: nothing about the device or the install is appended.
        assertFalse(url.contains("?"))
        assertFalse(url.contains("#"))
    }
}
