package io.github.duwenji.sanpoguide.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** A spot photo with what its license asks to be shown next to it. */
data class SpotPhoto(
    val bitmap: Bitmap,
    val author: String?,
    val license: String?,
    /** The file's page on Wikimedia Commons (credits and license in full). */
    val pageUrl: String,
)

/** Where a spot's photo lives on Wikimedia Commons, read from its OpenStreetMap tags. Pure logic. */
object CommonsFile {
    /**
     * The Commons file name ("Foo.jpg", no "File:") from `wikimedia_commons` or a Commons `image`
     * link; null when the tags only point elsewhere (then [wikidataId] may still find one).
     */
    fun fromTags(tags: Map<String, String>): String? {
        tags["wikimedia_commons"]?.let { v ->
            if (v.startsWith("File:", ignoreCase = true)) return v.substringAfter(':').trim().ifEmpty { null }
        }
        val image = tags["image"] ?: return null
        return when {
            "commons.wikimedia.org/wiki/File:" in image -> decode(image.substringAfter("/wiki/File:"))
            "upload.wikimedia.org/wikipedia/commons/" in image -> decode(image.substringAfterLast('/'))
            else -> null
        }?.substringBefore('?')?.substringBefore('#')?.ifEmpty { null }
    }

    /** "Q123" from the `wikidata` tag, if it looks like one. */
    fun wikidataId(tags: Map<String, String>): String? =
        tags["wikidata"]?.trim()?.takeIf { WIKIDATA_ID.matches(it) }

    /** Commons credits come as HTML ("<a href=...>Name</a>"); the sheet shows plain text. */
    fun plainText(html: String?): String? = html
        ?.replace(TAG, "")
        ?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#39;", "'")?.replace("&lt;", "<")?.replace("&gt;", ">")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.ifEmpty { null }

    private fun decode(s: String) = URLDecoder.decode(s, "UTF-8").replace('_', ' ')

    private val WIKIDATA_ID = Regex("Q[1-9][0-9]*")
    private val TAG = Regex("<[^>]*>")
}

/**
 * Photos of spots from Wikimedia Commons, found through the spot's tags (directly, or via its
 * Wikidata item's image, P18). Downloads a thumbnail only, keeps recent ones in memory and
 * responses on disk, and — unless the user allows it — only on an unmetered network (Wi-Fi).
 */
class SpotPhotos(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .cache(Cache(File(context.cacheDir, "spot_photos"), CACHE_BYTES))
        // Wikimedia asks API clients to say who they are.
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    /** By spot id; null means the spot has no photo (so it isn't looked up again). */
    private val memory = LruCache<String, Optional>(MEMORY_ENTRIES)

    private class Optional(val photo: SpotPhoto?)

    /** Whether downloading now would use a metered (mobile) connection. */
    fun isMetered(): Boolean = context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered

    /** The spot's photo, or null if it has none or it can't be fetched. */
    suspend fun photoFor(poi: Poi): SpotPhoto? {
        memory.get(poi.id)?.let { return it.photo }
        val photo = try {
            withContext(Dispatchers.IO) { find(poi.tags)?.let { fetch(it) } }
        } catch (e: IOException) {
            Log.w(TAG, "Photo fetch failed for ${poi.id}", e)
            return null // Not remembered: a network error may pass.
        } catch (e: Exception) {
            Log.w(TAG, "Photo lookup failed for ${poi.id}", e)
            null
        }
        memory.put(poi.id, Optional(photo))
        return photo
    }

    fun hasPhotoHint(poi: Poi): Boolean =
        CommonsFile.fromTags(poi.tags) != null || CommonsFile.wikidataId(poi.tags) != null

    private fun find(tags: Map<String, String>): String? =
        CommonsFile.fromTags(tags) ?: CommonsFile.wikidataId(tags)?.let(::imageOfItem)

    /** The item's image (P18) on Wikidata. */
    private fun imageOfItem(id: String): String? {
        val url = "https://www.wikidata.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "wbgetclaims")
            .addQueryParameter("entity", id)
            .addQueryParameter("property", "P18")
            .addQueryParameter("format", "json")
            .build()
        val json = getJson(url.toString())
        val claims = json.optJSONObject("claims")?.optJSONArray("P18") ?: return null
        if (claims.length() == 0) return null
        return claims.getJSONObject(0).optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")
            ?.ifEmpty { null }
    }

    private fun fetch(fileName: String): SpotPhoto? {
        val url = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("titles", "File:$fileName")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|extmetadata")
            .addQueryParameter("iiurlwidth", THUMB_WIDTH.toString())
            .addQueryParameter("format", "json")
            .build()
        val pages = getJson(url.toString()).optJSONObject("query")?.optJSONObject("pages") ?: return null
        val page = pages.keys().asSequence().map(pages::getJSONObject).firstOrNull() ?: return null
        val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return null
        val thumb = info.optString("thumburl").ifEmpty { return null }
        val meta = info.optJSONObject("extmetadata")
        fun meta(key: String) = meta?.optJSONObject(key)?.optString("value")
        val bitmap = http.newCall(Request.Builder().url(thumb).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("Commons thumbnail: HTTP ${res.code}")
            BitmapFactory.decodeStream(res.body!!.byteStream())
        } ?: return null
        return SpotPhoto(
            bitmap = bitmap,
            author = CommonsFile.plainText(meta("Artist")) ?: CommonsFile.plainText(meta("Credit")),
            license = CommonsFile.plainText(meta("LicenseShortName")),
            pageUrl = info.optString("descriptionurl").ifEmpty { "https://commons.wikimedia.org/wiki/File:$fileName" },
        )
    }

    private fun getJson(url: String): JSONObject =
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("Wikimedia API: HTTP ${res.code}")
            JSONObject(res.body!!.string())
        }

    private companion object {
        const val TAG = "SpotPhotos"
        const val USER_AGENT = "SanpoGuide/0.1 (Android; walking guide app)"
        const val THUMB_WIDTH = 800
        const val CACHE_BYTES = 30L * 1024 * 1024
        // An 800px photo is about 2 MB in memory.
        const val MEMORY_ENTRIES = 8
    }
}
