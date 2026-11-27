package com.example

import android.net.Uri
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun `document picker retains all photos and supports files`() {
        val first = Uri.parse("content://files/document/1")
        val second = Uri.parse("content://files/document/2")
        val intent = Intent().apply {
            clipData = ClipData.newRawUri("photos", first).apply { addItem(ClipData.Item(second)) }
        }
        assertArrayEquals(arrayOf(first, second), UploadUriPolicy.pickerSelection(Activity.RESULT_OK, intent))
        val third = Uri.parse("content://files/document/3")
        assertArrayEquals(arrayOf(first, second, third), UploadUriPolicy.pickerSelection(Activity.RESULT_OK, Intent(intent).setData(third)))
        assertArrayEquals(arrayOf(first, second), UploadUriPolicy.pickerSelection(Activity.RESULT_OK, Intent(intent).setData(first)))
        assertArrayEquals(arrayOf(first), UploadUriPolicy.pickerSelection(Activity.RESULT_OK, Intent().setData(first)))
        assertNull(UploadUriPolicy.pickerSelection(Activity.RESULT_CANCELED, intent))
        assertNull(UploadUriPolicy.pickerSelection(Activity.RESULT_OK, Intent()))
        val malformed = Intent().apply { clipData = ClipData.newPlainText("invalid", "not a URI") }
        assertNull(UploadUriPolicy.pickerSelection(Activity.RESULT_OK, malformed))
        val photos = UploadUriPolicy.documentIntent(arrayOf("image/*"), true)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, photos.action)
        assertEquals("image/*", photos.type)
        assertTrue(photos.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        assertTrue(photos.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val file = UploadUriPolicy.documentIntent(arrayOf("application/pdf"), false)
        assertEquals("application/pdf", file.type)
        assertFalse(file.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, true))
    }

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
