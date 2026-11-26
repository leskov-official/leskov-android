package com.example.web

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process

/** A picker result is untrusted. Never pass an arbitrary local URI to WebView. */
object UploadUriPolicy {
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
