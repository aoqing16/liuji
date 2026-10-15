package com.liuji.xiaoqi.hindsight

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * 坚果云 WebDAV **只读**客户端：仅 PROPFIND / GET。
 * 故意不提供 PUT / DELETE / MKCOL / MOVE，避免往云盘写入。
 */
class WebDavClient(
    private val baseUrl: String,
    private val user: String,
    private val password: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val credential = Credentials.basic(user, password)
    private val root = baseUrl.trimEnd('/') + "/"

    data class DavEntry(
        val href: String,
        val displayName: String,
        val isCollection: Boolean,
        val lastModified: String?,
        val contentLength: Long?,
    )

    fun propfind(path: String, depth: Int = 1): List<DavEntry> {
        val url = join(path)
        val body = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .header("Depth", depth.toString())
            .method("PROPFIND", body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("PROPFIND ${resp.code}: ${resp.message} ($url)")
            }
            val xml = resp.body?.string().orEmpty()
            return parseMultistatus(xml)
        }
    }

    fun getText(path: String): String {
        val url = join(path)
        val req = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 404) {
                throw NoSuchElementException("404 $url")
            }
            if (!resp.isSuccessful) {
                throw IllegalStateException("GET ${resp.code}: ${resp.message} ($url)")
            }
            return resp.body?.string().orEmpty()
        }
    }

    fun getBytes(path: String): ByteArray {
        val url = join(path)
        val req = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("GET ${resp.code}: ${resp.message} ($url)")
            }
            return resp.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun join(path: String): String {
        val p = path.trim().trimStart('/')
        return if (p.isEmpty()) root else root + p
    }

    private fun parseMultistatus(xml: String): List<DavEntry> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        val out = ArrayList<DavEntry>()
        var href = ""
        var displayName = ""
        var lastModified: String? = null
        var contentLength: Long? = null
        var isCollection = false
        var inResponse = false
        var text = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    text = StringBuilder()
                    val local = parser.name?.lowercase().orEmpty()
                    if (local == "response") {
                        inResponse = true
                        href = ""
                        displayName = ""
                        lastModified = null
                        contentLength = null
                        isCollection = false
                    } else if (inResponse && local == "collection") {
                        isCollection = true
                    }
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val local = parser.name?.lowercase().orEmpty()
                    val value = text.toString().trim()
                    when {
                        !inResponse -> Unit
                        local == "href" -> href = value
                        local == "displayname" -> displayName = value
                        local == "getlastmodified" -> lastModified = value.ifBlank { null }
                        local == "getcontentlength" -> contentLength = value.toLongOrNull()
                        local == "response" -> {
                            if (href.isNotBlank()) {
                                val name = displayName.ifBlank {
                                    href.trimEnd('/').substringAfterLast('/')
                                }
                                out += DavEntry(
                                    href = href,
                                    displayName = name,
                                    isCollection = isCollection,
                                    lastModified = lastModified,
                                    contentLength = contentLength,
                                )
                            }
                            inResponse = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    companion object {
        private const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:getlastmodified/>
    <d:getcontentlength/>
    <d:resourcetype/>
  </d:prop>
</d:propfind>"""
    }
}
