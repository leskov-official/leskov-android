package com.example.web

import android.app.Activity
import android.webkit.MimeTypeMap
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process

/** A picker result is untrusted. Never pass an arbitrary local URI to WebView. */
object UploadUriPolicy {
    // Use the system Documents UI, not an OEM GET_CONTENT/photo-picker redirect.
    // Documents providers return an explicit per-URI read grant, including multi-select.
    fun documentIntent(acceptTypes: Array<String>, multiple: Boolean): Intent {
        val types = acceptTypes.flatMap { it.split(',') }.map { it.trim().lowercase(java.util.Locale.ROOT) }
            .filter { it.isNotEmpty() }.map { type ->
                if (type.startsWith('.')) MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.drop(1)) ?: "*/*"
                else if (type.contains('/')) type else "*/*"
            }.distinct()
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            type = if (types.size == 1) types.single() else "*/*"
            if (types.size > 1 && "*/*" !in types) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        }
    }

    fun pickerSelection(resultCode: Int, data: Intent?): Array<Uri>? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        val clip = data.clipData
        if (clip != null) {
            if (clip.itemCount == 0) return null
            return (0 until clip.itemCount).map { clip.getItemAt(it).uri ?: return null }
                .distinct().toTypedArray()
        }
        return data.data?.let { arrayOf(it) }
    }

    internal fun permits(uri: Uri, ownerPackage: String?, ownerUid: Int?,
                         appPackage: String, appUid: Int, explicitReadGrant: Boolean): Boolean {
        return uri.scheme == "content" && !uri.authority.isNullOrBlank() &&
            !uri.authority.orEmpty().contains('@') && ownerPackage != null && ownerUid != null &&
            ownerPackage != appPackage && ownerUid != appUid && explicitReadGrant
    }

    fun validate(context: Context, uris: Array<Uri>?): Array<Uri>? {
        if (uris.isNullOrEmpty()) return null
        // All-or-nothing: do not silently upload a subset of a multi-file selection.
        if (!uris.all { uri ->
                runCatching {
                    val authority = uri.authority
                    if (uri.scheme != "content" || authority.isNullOrBlank() || '@' in authority) {
                        false
                    } else {
                        val provider = context.packageManager.resolveContentProvider(authority, 0)
                        val grant = context.checkUriPermission(uri, Process.myPid(), Process.myUid(),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
                        permits(uri, provider?.packageName, provider?.applicationInfo?.uid,
                            context.packageName, Process.myUid(), grant)
                    }
                }.getOrDefault(false)
            }) return null
        return uris
    }
}
