package com.example.media

import android.webkit.WebView
import android.util.Log
import org.json.JSONObject
import org.json.JSONTokener

/** Transitions playing first-party HTML audio to real Media3 on cross-page navigation. */
object LeskovAudioHandoff {
    private const val TAG = "LeskovAudioHandoff"

    /** Captures only the ACTUALLY playing first-party audio element, never videos/call streams. */
    private val snapshotJs = """
        (function(){
          try {
            if(window.__leskovAndroidAudio) return window.__leskovAndroidAudio.snapshot();
            var manager=window.__leskovPlayerManager;
            var active=manager && manager.active;
            var audio=active && active.media && active.media.tagName==='AUDIO' ? active.media :
              Array.from(document.querySelectorAll('audio')).find(function(x){return !x.paused && !x.ended;});
            if(!audio || audio.paused || audio.ended || audio.srcObject) return null;
            // Never promote a ringtone, WebRTC remote stream or empty-src <audio>
            // (which would otherwise resolve to the current PHP HTML document).
            var raw=(audio.currentSrc||audio.src||'').trim();
            if(!raw) return null;
            var u=new URL(raw,document.baseURI);
            if(u.protocol!=='https:' || u.hostname!=='lesv.online' || u.port ||
               u.username || u.password || /(?:call[_-]?ringtone|notification[_-]?sound)/i.test(u.pathname)) return null;
            var meta=(typeof navigator !== 'undefined' && navigator.mediaSession) ? navigator.mediaSession.metadata : null;
            var title=(meta&&meta.title)||(active&&active.title)||'';
            var artist=(meta&&meta.artist)||(active&&active.artist)||'Igor Leskov';
            var artwork=(meta&&meta.artwork&&meta.artwork.length&&meta.artwork[meta.artwork.length-1].src)||'';
            var tile=audio.closest && audio.closest('[data-cover], [data-track-cover], .leskov-player, .track-card');
            if(tile){
              title=title||tile.getAttribute('data-title')||tile.getAttribute('data-track-title')||'';
              artwork=artwork||tile.getAttribute('data-cover')||tile.getAttribute('data-track-cover')||'';
            }
            try { var art=new URL(artwork,document.baseURI);
              artwork=(art.protocol==='https:' && art.hostname==='lesv.online' && !art.username && !art.password)?art.href:'';
            }catch(e){artwork='';}
            return JSON.stringify({url:u.href,title:title||'LESKOV',artist:artist,
               artwork:artwork,position:Math.max(0,Math.floor((audio.currentTime||0)*1000))});
          }catch(e){return null;}
        })();
    """.trimIndent()

    private val pauseJs = """
        (function(){try{
          if(window.__leskovAndroidAudio){window.__leskovAndroidAudio.command('pause');return;}
          var p=window.__leskovPlayerManager && window.__leskovPlayerManager.active;
          if(p && p.media && p.media.tagName==='AUDIO') p.pause();
          else Array.from(document.querySelectorAll('audio')).forEach(function(a){if(!a.paused)a.pause();});
        }catch(e){}})();
    """.trimIndent()

    /** Do NOT make navigation depend on a playable/accessible stream. */
    fun beforeLeaving(webView: WebView, after: () -> Unit) {
        var finished = false
        var timeout: Runnable? = null
        fun complete() {
            if (finished) return
            finished = true
            timeout?.let { webView.removeCallbacks(it) }
            after()
        }
        timeout = Runnable { complete() }
        webView.postDelayed(timeout, 1500L)
        try {
            webView.evaluateJavascript(snapshotJs) snapshot@ { result ->
                if (finished) return@snapshot
                val snap = try {
                    val decoded = JSONTokener(result ?: "null").nextValue()
                    if (decoded is String) JSONObject(decoded) else null
                } catch (_: Exception) { null }
                val url = snap?.optString("url").orEmpty()
                if (url.isNotBlank()) {
                    // Crucial ordering: WebView (Chromium) must RELEASE audio focus before
                    // Media3 requests it. Starting the service first caused Play -> Pause.
                    webView.evaluateJavascript(pauseJs) paused@ {
                        if (finished) return@paused
                        val accepted = LeskovNativeAudioService.handoff(
                            webView.context,
                            url,
                            snap?.optString("title") ?: "LESKOV",
                            snap?.optString("artist") ?: "Igor Leskov",
                            snap?.optString("artwork") ?: "",
                            snap?.optLong("position") ?: 0L,
                            snap?.optJSONArray("queue")?.toString(),
                            snap?.optInt("queueIndex", 0) ?: 0,
                            snap?.optString("mode", "sequential") ?: "sequential"
                        )
                        if (!accepted) Log.w(TAG, "Native handoff unavailable; continuing navigation")
                        complete()
                    }
                } else {
                    complete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Handoff skipped; continuing navigation", e)
            complete()
        }
    }
}
