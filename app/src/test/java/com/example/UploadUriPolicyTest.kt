package com.example

import android.net.Uri
import com.example.web.UploadUriPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UploadUriPolicyTest {
    private val app = "com.aistudio.leskov.musix"
    private fun permits(uri: String, owner: String? = "com.external.files", uid: Int? = 200,
                        grant: Boolean = true) = UploadUriPolicy.permits(Uri.parse(uri), owner, uid, app, 100, grant)

    @Test fun `only explicit grant for external content provider is accepted`() {
        assertTrue(permits("content://files/document/42"))
        assertFalse(permits("file:///data/local/file"))
        assertFalse(permits("https://lesv.online/file"))
        assertFalse(permits("content://files/document/42", grant = false))
        assertFalse(permits("content://files/document/42", owner = app))
        assertFalse(permits("content://files/document/42", uid = 100))
        assertFalse(permits("content://unknown/file", owner = null, uid = null))
        assertFalse(permits("content://10@files/document/42"))
        assertFalse(permits("content:///document/42"))
    }
}
