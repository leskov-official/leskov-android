package com.example

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.media.LeskovNativeAudioService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NativeAudioQueueTest {
    private val fallback = Uri.parse("android.resource://com.aistudio.leskov.musix/drawable/leskov_logo")

    @Test
    fun `authorized queue preserves track order and metadata`() {
        val items = LeskovNativeAudioService.parseQueue("""[
          {"url":"https://lesv.online/stream.php?id=1","title":"One","artist":"Igor","artwork":"https://lesv.online/one.webp"},
          {"url":"https://lesv.online/stream.php?id=2","title":"Two","artist":"LESKOV"}
        ]""", fallback)
        assertEquals(2, items.size)
        assertEquals("https://lesv.online/stream.php?id=2", items[1].localConfiguration?.uri.toString())
        assertEquals("One", items[0].mediaMetadata.title.toString())
        assertEquals("Igor", items[0].mediaMetadata.artist.toString())
        assertEquals(Uri.parse("https://lesv.online/one.webp"), items[0].mediaMetadata.artworkUri)
        assertEquals(fallback, items[1].mediaMetadata.artworkUri)
    }

    @Test
    fun `invalid track rejects whole queue without changing selected index`() {
        for (url in listOf("http://lesv.online/a.mp3", "https://evil.test/a.mp3",
            "https://user:pass@lesv.online/a.mp3", "blob:https://lesv.online/id",
            "https://lesv.online:8443/a.mp3")) {
            val raw = """[{"url":"https://lesv.online/one.mp3"},{"url":"$url"}]"""
            assertTrue(LeskovNativeAudioService.parseQueue(raw, fallback).isEmpty())
        }
    }

    @Test
    fun `untrusted artwork falls back and text is bounded`() {
        val title = "x".repeat(1000)
        val items = LeskovNativeAudioService.parseQueue("""[
          {"url":"https://lesv.online/a.mp3","title":"$title","artwork":"https://evil.test/image.png"}
        ]""", fallback)
        assertEquals(160, items.single().mediaMetadata.title?.length)
        assertEquals(fallback, items.single().mediaMetadata.artworkUri)
    }

    @Test
    fun `malformed empty and oversized queues fall back to single track`() {
        val tooMany = (1..101).joinToString(",", "[", "]") { """{"url":"https://lesv.online/$it.mp3"}""" }
        for (raw in listOf(null, "[]", "broken", "[{}]", tooMany,
            """[{"url":"https://lesv.online/a.mp3","title":"${"x".repeat(600_001)}"}]""")) {
            assertTrue(LeskovNativeAudioService.parseQueue(raw, fallback).isEmpty())
        }
    }
}
