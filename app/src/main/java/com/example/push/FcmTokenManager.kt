package com.example.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.net.Uri
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import android.util.Log
import com.example.model.AppLanguage
import com.example.model.LeskovConstants
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

object FcmTokenManager {

    private const val TAG = "LeskovFCM"
    private const val PREFS_NAME = "leskov_fcm_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_LAST_SYNCED_USER = "last_synced_user_id"
    private const val KEY_LAST_SYNCED_AT = "last_synced_at"
    private val callbacks = ConcurrentHashMap<String, (String) -> Unit>()

    /** WebView evaluateJavascript does NOT await JavaScript Promises. This bridge does. */
    fun handleResponse(json: String) {
        try {
            val payload = JSONObject(json)
            val id = payload.optString("id")
            callbacks.remove(id)?.invoke(payload.optString("status", "invalid_response"))
        } catch (e: Exception) {
            Log.w(TAG, "Bad FCM server response", e)
        }
    }

    // Channel IDs
    const val CHANNEL_MESSAGES = "leskov_messages_channel"
    const val CHANNEL_SOCIAL = "leskov_social_channel"
    const val CHANNEL_POSTS = "leskov_posts_channel"
    const val CHANNEL_CALLS = "leskov_calls_channel"
    const val CHANNEL_SYSTEM = "leskov_system_channel"


    fun createNotificationChannels(context: Context, language: AppLanguage = AppLanguage.RU) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Direct Messages (High importance, sound, vibration)
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                when (language) {
                    AppLanguage.ET -> "Sõnumid ja vestlused"
                    AppLanguage.EN -> "Messages & Chats"
                    AppLanguage.RU -> "Личные сообщения"
                },
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых личных сообщениях"
                enableVibration(true)
                setShowBadge(true)
            }

            // 2. Incoming Calls (High importance, ringtone sound, vibration)
            val callsChannel = NotificationChannel(
                CHANNEL_CALLS,
                when (language) {
                    AppLanguage.ET -> "Sissetulevad kõned"
                    AppLanguage.EN -> "Incoming Calls"
                    AppLanguage.RU -> "Входящие звонки"
                },
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о входящих аудио- и видеозвонках"
                enableVibration(true)
                val defaultRingtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                setSound(
                    defaultRingtone,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            // 3. Social & Reactions (Default importance)
            val socialChannel = NotificationChannel(
                CHANNEL_SOCIAL,
                when (language) {
                    AppLanguage.ET -> "Reaktsioonid ja taotlused"
                    AppLanguage.EN -> "Reactions & Requests"
                    AppLanguage.RU -> "Реакции и заявки"
                },
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Отметки «Нравится», комментарии и заявки в друзья"
            }

            // 4. Posts & Music Releases
            val postsChannel = NotificationChannel(
                CHANNEL_POSTS,
                when (language) {
                    AppLanguage.ET -> "Postitused ja uued lood"
                    AppLanguage.EN -> "Posts & New Releases"
                    AppLanguage.RU -> "Публикации и музыка"
                },
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Новые треки, релизы и новости артиста"
            }

            // 5. System Notifications
            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                when (language) {
                    AppLanguage.ET -> "Süsteemiteated"
                    AppLanguage.EN -> "System Notices"
                    AppLanguage.RU -> "Служебные уведомления"
                },
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Обновления платформы и безопасность"
            }

            nm.createNotificationChannels(listOf(messagesChannel, callsChannel, socialChannel, postsChannel, systemChannel))
        }
    }

    fun saveToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_FCM_TOKEN, null) != token) {
            prefs.edit().putString(KEY_FCM_TOKEN, token).remove(KEY_LAST_SYNCED_USER).apply()
        }
    }

    fun getSavedToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FCM_TOKEN, null)
    }

    /** True only after a REAL google-services.json has initialized Firebase. */
    fun isFirebaseConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /**
     * Fetches and stores the FCM registration token. It does not claim that the LESKOV
     * server can send messages; that requires a secure, authenticated backend integration.
     */
    fun refreshDeviceToken(context: Context) {
        if (!isFirebaseConfigured(context)) {
            Log.w(TAG, "FCM disabled: real Firebase configuration has not been supplied")
            return
        }
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> saveToken(context, token) }
            .addOnFailureListener { error -> Log.e(TAG, "FCM token request failed", error) }
    }

    /** Syncs the device token with the COMPANION server endpoint via an authenticated first-party WebView. */
    fun syncTokenWithServer(context: Context, webView: WebView?, userId: Int, language: AppLanguage) {
        if (userId <= 0 || webView == null || !isFirebaseConfigured(context)) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            saveToken(context, token)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val marker = "$userId:${token.hashCode()}"
            if (marker == prefs.getString(KEY_LAST_SYNCED_USER, null) &&
                System.currentTimeMillis() - prefs.getLong(KEY_LAST_SYNCED_AT, 0L) < 86_400_000L) return@addOnSuccessListener
            firstPartyRequest(webView, "register", token, language) { status ->
                if (status == "status:200") prefs.edit().putString(KEY_LAST_SYNCED_USER, marker)
                    .putLong(KEY_LAST_SYNCED_AT, System.currentTimeMillis()).apply()
                else Log.w(TAG, "FCM server sync unavailable: $status (backend-patch must be deployed)")
            }
        }.addOnFailureListener { Log.w(TAG, "Unable to refresh FCM token", it) }
    }

    private fun firstPartyRequest(webView: WebView, action: String, token: String,
                                  language: AppLanguage, onResult: (String) -> Unit) {
        webView.post {
            val uri = try { Uri.parse(webView.url ?: "") } catch (_: Exception) { null }
            if (uri?.scheme != "https" || uri.host != LeskovConstants.HOST) {
                onResult("wrong_origin"); return@post
            }
            // WebView.evaluateJavascript returns a Promise object, NOT its resolved value.
            // Send the asynchronous result back via an origin-scoped WebMessageListener.
            val requestId = UUID.randomUUID().toString()
            callbacks[requestId] = onResult
            val script = """
                (async function(){
                  const id=${JSONObject.quote(requestId)};
                  let status='unknown';
                  const endpoint='https://lesv.online/mobile_push.php';
                  const abort=new AbortController();
                  const timeout=setTimeout(function(){abort.abort();},3500);
                  try {
                    const csrfReply=await fetch(endpoint+'?action=csrf',{
                      credentials:'same-origin',cache:'no-store',signal:abort.signal});
                    if (!csrfReply.ok) status='csrf:'+csrfReply.status;
                    else {
                      const csrf=await csrfReply.json();
                      if (!csrf.ok || !csrf.csrf) status='csrf_missing';
                      else {
                        const body=new URLSearchParams();
                        body.set('csrf_token',csrf.csrf);
                        body.set('action',${JSONObject.quote(action)});
                        body.set('token',${JSONObject.quote(token)});
                        body.set('language',${JSONObject.quote(language.code)});
                        const resp=await fetch(endpoint,{
                          method:'POST',credentials:'same-origin',
                          headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},
                          signal:abort.signal,body:body});
                        status='status:'+resp.status;
                      }
                    }
                  }catch(e){status='network_error';}
                  finally {
                    clearTimeout(timeout);
                    try { window.LeskovFcmBridge.postMessage(JSON.stringify({id:id,status:status})); }
                    catch(e) { /* the WebView has navigated and will retry next session */ }
                  }
                })();
            """.trimIndent()
            webView.evaluateJavascript(script) { result ->
                // Rejected injection or destroyed page: do not keep callbacks indefinitely.
                if (result == null) {
                    callbacks.remove(requestId)?.invoke("page_unavailable")
                }
            }
            webView.postDelayed({
                callbacks.remove(requestId)?.invoke("bridge_timeout")
            }, 5500)
        }
    }

    /** Unregister before invalidating the local token and ending the PHP session. */
    fun unregisterTokenOnServer(context: Context, webView: WebView?, onComplete: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_FCM_TOKEN, null)
        prefs.edit().remove(KEY_LAST_SYNCED_USER).remove(KEY_FCM_TOKEN).apply()
        fun done() {
            if (isFirebaseConfigured(context)) {
                FirebaseMessaging.getInstance().deleteToken().addOnFailureListener {
                    Log.w(TAG, "Unable to invalidate local FCM token")
                }
            }
            onComplete()
        }
        if (webView == null || token.isNullOrBlank()) done()
        else firstPartyRequest(webView, "unregister", token, AppLanguage.RU) {
            if (it != "status:200") Log.w(TAG, "FCM unregister failed: $it")
            done()
        }
    }
}
