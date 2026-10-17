package com.liuji.xiaoqi.ima

import com.liuji.xiaoqi.log.AppLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class ImaKbInfo(val id: String, val name: String)

data class ImaApiResult(
    val ok: Boolean,
    val statusCode: Int = 0,
    val retcode: Int? = null,
    val errmsg: String? = null,
    val data: JSONObject? = null,
    val error: String? = null,
)

/**
 * 腾讯 ima OpenAPI（笔记 + 知识库），契约与 AutoJS 探测脚本一致。
 */
class ImaApiClient(
    private val clientId: String,
    private val apiKey: String,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * 对 403/429/5xx/超时/网络等临时失败做有限次退避重试；
     * 成功即返回；耗尽后返回最后一次失败结果（不假成功）。
     */
    fun post(apiPath: String, body: JSONObject): ImaApiResult {
        var last = postOnce(apiPath, body)
        if (last.ok || !isRetryableResult(last)) return last
        for (i in RETRY_BACKOFF_MS.indices) {
            val delay = RETRY_BACKOFF_MS[i]
            AppLog.w(
                TAG,
                "API 临时失败，${delay}ms 后重试 ${i + 1}/${RETRY_BACKOFF_MS.size} " +
                    "path=$apiPath http=${last.statusCode} err=${last.error ?: last.errmsg}",
            )
            Thread.sleep(delay)
            last = postOnce(apiPath, body)
            if (last.ok) {
                AppLog.i(TAG, "API 重试成功 path=$apiPath attempt=${i + 2}")
                return last
            }
            if (!isRetryableResult(last)) {
                AppLog.w(TAG, "API 不可重试失败 path=$apiPath http=${last.statusCode} err=${last.error}")
                return last
            }
        }
        AppLog.e(
            TAG,
            "API 重试耗尽 path=$apiPath http=${last.statusCode} err=${last.error ?: last.errmsg}",
        )
        return last
    }

    private fun postOnce(apiPath: String, body: JSONObject): ImaApiResult {
        val url = BASE + "/" + apiPath.trimStart('/')
        val req = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("ima-openapi-clientid", clientId)
            .header("ima-openapi-apikey", apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        return try {
            // 轻微节流，降低 openapi 限频概率
            Thread.sleep(REQUEST_GAP_MS)
            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val parsed = runCatching { JSONObject(raw) }.getOrNull()
                if (parsed == null) {
                    return ImaApiResult(
                        ok = false,
                        statusCode = resp.code,
                        error = "服务器返回异常，请稍后重试",
                    )
                }
                val retcode = when {
                    parsed.has("retcode") && !parsed.isNull("retcode") -> parsed.optInt("retcode")
                    parsed.has("code") && !parsed.isNull("code") -> parsed.optInt("code")
                    else -> null
                }
                val data = parsed.optJSONObject("data")
                val httpOk = resp.code in 200..299
                val codeOk = retcode == null || retcode == 0
                val errHint = parsed.optString("errmsg").ifBlank { null }
                    ?: parsed.optString("msg").ifBlank { null }
                    ?: raw.take(180)
                ImaApiResult(
                    ok = httpOk && codeOk,
                    statusCode = resp.code,
                    retcode = retcode,
                    errmsg = parsed.optString("errmsg").ifBlank { null },
                    data = data,
                    error = if (httpOk && codeOk) null else (errHint.ifBlank { "HTTP ${resp.code}" }),
                )
            }
        } catch (e: Exception) {
            ImaApiResult(ok = false, error = e.message ?: e.toString())
        }
    }

    fun listAddableKnowledgeBases(limit: Int = 50): Pair<ImaApiResult, List<ImaKbInfo>> {
        val all = ArrayList<ImaKbInfo>()
        var cursor = ""
        var last: ImaApiResult = ImaApiResult(ok = false, error = "empty")
        repeat(10) {
            last = post(
                "openapi/wiki/v1/get_addable_knowledge_base_list",
                JSONObject().put("cursor", cursor).put("limit", limit),
            )
            if (!last.ok) return last to all
            val arr = last.data?.optJSONArray("addable_knowledge_base_list") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                all += ImaKbInfo(id = o.getString("id"), name = o.optString("name"))
            }
            if (last.data?.optBoolean("is_end", true) == true) return last to all
            cursor = last.data?.optString("next_cursor").orEmpty()
            if (cursor.isBlank()) return last to all
        }
        return last to all
    }

    fun resolveKnowledgeBaseId(name: String): Pair<ImaApiResult, String?> {
        val (res, list) = listAddableKnowledgeBases()
        if (!res.ok) return res to null
        val hit = list.firstOrNull { it.name == name }
        return if (hit == null) {
            ImaApiResult(ok = false, error = "未找到知识库「$name」，请检查名称") to null
        } else {
            res to hit.id
        }
    }

    fun createKnowledgeBase(name: String): Pair<ImaApiResult, String?> {
        val res = post(
            "openapi/wiki/v1/create_knowledge_base",
            JSONObject().put("name", name.trim()),
        )
        if (!res.ok) return res to null
        val data = res.data ?: return res to null
        val id = sequenceOf("id", "knowledge_base_id", "kb_id")
            .mapNotNull { key -> data.optString(key).takeIf { it.isNotBlank() } }
            .firstOrNull()
        return res to id
    }

    /** 按名称查找；不存在则创建。 */
    fun ensureKnowledgeBase(name: String): Pair<ImaApiResult, String?> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return ImaApiResult(ok = false, error = "知识库名称不能为空") to null
        }
        val (findRes, foundId) = resolveKnowledgeBaseId(trimmed)
        if (findRes.ok && !foundId.isNullOrBlank()) return findRes to foundId
        // 列表接口失败则直接失败；仅「未找到」时才创建
        if (!findRes.ok && findRes.error?.contains("未找到知识库") != true) {
            return findRes to null
        }
        val (createRes, createdId) = createKnowledgeBase(trimmed)
        if (createRes.ok && !createdId.isNullOrBlank()) return createRes to createdId
        // 创建成功但未返回 id：再列一次
        if (createRes.ok) {
            val (again, id2) = resolveKnowledgeBaseId(trimmed)
            if (again.ok && !id2.isNullOrBlank()) return again to id2
        }
        return createRes to createdId
    }

    data class CosCredential(
        val mediaId: String,
        val token: String,
        val secretId: String,
        val secretKey: String,
        val startTime: Long,
        val expiredTime: Long,
        val bucketName: String,
        val region: String,
        val cosKey: String,
        val cosUrl: String? = null,
    )

    fun createMedia(
        knowledgeBaseId: String,
        fileName: String,
        fileSize: Long,
        contentType: String = "text/markdown",
        fileExt: String = "md",
    ): Pair<ImaApiResult, CosCredential?> {
        val body = JSONObject()
            .put("file_name", fileName)
            .put("file_size", fileSize)
            .put("content_type", contentType)
            .put("knowledge_base_id", knowledgeBaseId)
            .put("file_ext", fileExt)
        val res = post("openapi/wiki/v1/create_media", body)
        if (!res.ok) return res to null
        val data = res.data ?: return res to null
        val mediaId = data.optString("media_id")
        val cred = data.optJSONObject("cos_credential") ?: return res to null
        if (mediaId.isBlank()) return res to null
        val parsed = CosCredential(
            mediaId = mediaId,
            token = cred.optString("token"),
            secretId = cred.optString("secret_id"),
            secretKey = cred.optString("secret_key"),
            startTime = cred.optString("start_time").toLongOrNull()
                ?: cred.optLong("start_time"),
            expiredTime = cred.optString("expired_time").toLongOrNull()
                ?: cred.optLong("expired_time"),
            bucketName = cred.optString("bucket_name"),
            region = cred.optString("region"),
            cosKey = cred.optString("cos_key").trimStart('/'),
            cosUrl = cred.optString("cos_url").ifBlank { null }
                ?: data.optString("cos_url").ifBlank { null },
        )
        if (parsed.secretId.isBlank() || parsed.secretKey.isBlank() ||
            parsed.bucketName.isBlank() || parsed.region.isBlank() || parsed.cosKey.isBlank()
        ) {
            return ImaApiResult(ok = false, error = "create_media 凭证不完整") to null
        }
        return res to parsed
    }

    fun uploadFileToCos(file: File, contentType: String, cred: CosCredential): ImaApiResult {
        return try {
            val host = "${cred.bucketName}.cos.${cred.region}.myqcloud.com"
            val cosKey = cred.cosKey
            val url = cred.cosUrl?.takeIf { it.isNotBlank() }
                ?: "https://$host/${URLEncoder.encode(cosKey, "UTF-8").replace("+", "%20").replace("%2F", "/")}"
            val start = if (cred.startTime > 0) cred.startTime else System.currentTimeMillis() / 1000
            val expired = if (cred.expiredTime > start) cred.expiredTime else start + 1800
            val keyTime = "$start;$expired"
            val signKey = hmacSha1Hex(cred.secretKey, keyTime)
            val httpString = "put\n/$cosKey\n\nhost=$host\n"
            val stringToSign = "sha1\n$keyTime\n${sha1Hex(httpString)}\n"
            val signature = hmacSha1Hex(signKey, stringToSign)
            val auth = "q-sign-algorithm=sha1" +
                "&q-ak=${cred.secretId}" +
                "&q-sign-time=$keyTime" +
                "&q-key-time=$keyTime" +
                "&q-header-list=host" +
                "&q-url-param-list=" +
                "&q-signature=$signature"
            val bytes = file.readBytes()
            val reqBuilder = Request.Builder()
                .url(url)
                .put(bytes.toRequestBody(contentType.toMediaType()))
                .header("Host", host)
                .header("Authorization", auth)
                .header("Content-Type", contentType)
            if (cred.token.isNotBlank()) {
                reqBuilder.header("x-cos-security-token", cred.token)
            }
            http.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.code in 200..299) {
                    ImaApiResult(ok = true, statusCode = resp.code)
                } else {
                    ImaApiResult(
                        ok = false,
                        statusCode = resp.code,
                        error = "COS 上传失败 HTTP ${resp.code}: ${resp.body?.string()?.take(200)}",
                    )
                }
            }
        } catch (e: Exception) {
            ImaApiResult(ok = false, error = e.message ?: e.toString())
        }
    }

    fun addKnowledgeFile(
        knowledgeBaseId: String,
        mediaId: String,
        fileName: String,
        fileSize: Long,
        cosKey: String,
        mediaType: Int = 7,
    ): ImaApiResult {
        val body = JSONObject()
            .put("media_type", mediaType)
            .put("media_id", mediaId)
            .put("title", fileName)
            .put("knowledge_base_id", knowledgeBaseId)
            .put(
                "file_info",
                JSONObject()
                    .put("cos_key", cosKey)
                    .put("file_size", fileSize)
                    .put("file_name", fileName),
            )
        return post("openapi/wiki/v1/add_knowledge", body)
    }

    fun renameKnowledge(
        knowledgeBaseId: String,
        mediaId: String,
        newName: String,
    ): ImaApiResult {
        return post(
            "openapi/wiki/v1/rename_knowledge",
            JSONObject()
                .put("knowledge_base_id", knowledgeBaseId)
                .put("media_id", mediaId)
                .put("name", newName)
                .put("title", newName),
        )
    }

    fun listKnowledgeByTitle(
        knowledgeBaseId: String,
        title: String,
    ): Pair<ImaApiResult, List<KbHit>> {
        val hits = ArrayList<KbHit>()
        var cursor = ""
        var last: ImaApiResult = ImaApiResult(ok = false, error = "empty")
        repeat(40) {
            last = post(
                "openapi/wiki/v1/get_knowledge_list",
                JSONObject()
                    .put("knowledge_base_id", knowledgeBaseId)
                    .put("cursor", cursor)
                    .put("limit", 50),
            )
            if (!last.ok) return last to hits
            val data = last.data ?: return last to hits
            val arr = data.optJSONArray("knowledge_list")
                ?: data.optJSONArray("info_list")
                ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val t = o.optString("title").ifBlank { o.optString("name") }
                if (t == title) {
                    val mid = o.optString("media_id").ifBlank {
                        sequenceOf("content_id", "id", "note_id")
                            .mapNotNull { k -> o.optString(k).takeIf { it.isNotBlank() } }
                            .firstOrNull()
                            .orEmpty()
                    }
                    if (mid.isNotBlank()) hits += KbHit(mediaId = mid, title = t)
                }
            }
            if (data.optBoolean("is_end", true)) return last to hits
            cursor = data.optString("next_cursor")
            if (cursor.isBlank()) return last to hits
        }
        return last to hits
    }

    /**
     * 覆盖上传：同名旧文件 rename 归档 → create_media → COS → add_knowledge。
     * @return mediaId
     */
    fun overwriteUploadMarkdownFile(
        knowledgeBaseId: String,
        file: File,
        fileName: String = file.name,
    ): Pair<ImaApiResult, String?> {
        val (listRes, olds) = listKnowledgeByTitle(knowledgeBaseId, fileName)
        if (!listRes.ok && listRes.error != null) {
            AppLog.w(TAG, "listKnowledgeByTitle: ${listRes.error}")
        }
        val ts = System.currentTimeMillis() / 1000
        val stem = fileName.substringBeforeLast('.', fileName)
        val ext = if (fileName.contains('.')) ".${fileName.substringAfterLast('.')}" else ".md"
        olds.forEachIndexed { index, hit ->
            val archived = "${stem}_archived_${ts}_$index$ext"
            val ren = renameKnowledge(knowledgeBaseId, hit.mediaId, archived)
            if (!ren.ok) {
                return ImaApiResult(
                    ok = false,
                    error = ren.error ?: ren.errmsg ?: "归档旧文件失败",
                ) to null
            }
        }
        val (createRes, cred) = createMedia(
            knowledgeBaseId = knowledgeBaseId,
            fileName = fileName,
            fileSize = file.length(),
            contentType = "text/markdown",
            fileExt = ext.removePrefix("."),
        )
        if (!createRes.ok || cred == null) {
            return createRes to null
        }
        val putRes = uploadFileToCos(file, "text/markdown", cred)
        if (!putRes.ok) return putRes to null
        val addRes = addKnowledgeFile(
            knowledgeBaseId = knowledgeBaseId,
            mediaId = cred.mediaId,
            fileName = fileName,
            fileSize = file.length(),
            cosKey = cred.cosKey,
        )
        return addRes to if (addRes.ok) cred.mediaId else null
    }

    fun importDoc(title: String, markdown: String): Pair<ImaApiResult, String?> {
        val body = JSONObject()
            .put("content_format", 1)
            .put("content", markdown)
            .put("title", title)
        val res = post("openapi/note/v1/import_doc", body)
        if (!res.ok) return res to null
        val data = res.data ?: return res to null
        val noteId = sequenceOf("note_id", "doc_id", "content_id", "id")
            .mapNotNull { key -> data.optString(key).takeIf { it.isNotBlank() } }
            .firstOrNull()
        return res to noteId
    }

    fun addNoteToKnowledgeBase(
        knowledgeBaseId: String,
        noteId: String,
        title: String,
    ): ImaApiResult {
        val body = JSONObject()
            .put("media_type", 11)
            .put("title", title)
            .put("knowledge_base_id", knowledgeBaseId)
            .put("note_info", JSONObject().put("content_id", noteId))
        return post("openapi/wiki/v1/add_knowledge", body)
    }

    fun appendDoc(noteId: String, markdown: String): ImaApiResult {
        val body = JSONObject()
            .put("doc_id", noteId)
            .put("content_format", 1)
            .put("content", markdown)
        return post("openapi/note/v1/append_doc", body)
    }

    fun getDocContent(noteId: String): ImaApiResult {
        val body = JSONObject()
            .put("doc_id", noteId)
            .put("target_content_format", 0)
        return post("openapi/note/v1/get_doc_content", body)
    }

    data class KbHit(val mediaId: String, val title: String)

    /**
     * 在目标知识库内按关键词搜索（不是笔记本 search_note_book）。
     * 用于判定日笔记是否仍挂在知识库；从库删除后笔记本里可能仍在。
     */
    fun searchKnowledge(
        knowledgeBaseId: String,
        query: String,
        cursor: String = "",
    ): Pair<ImaApiResult, List<KbHit>> {
        val body = JSONObject()
            .put("knowledge_base_id", knowledgeBaseId)
            .put("query", query)
            .put("cursor", cursor)
        val res = post("openapi/wiki/v1/search_knowledge", body)
        if (!res.ok) return res to emptyList()
        val arr = res.data?.optJSONArray("info_list")
            ?: res.data?.optJSONArray("knowledge_list")
            ?: JSONArray()
        val hits = ArrayList<KbHit>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title")
            val mediaId = o.optString("media_id").ifBlank {
                sequenceOf("content_id", "note_id", "doc_id", "id")
                    .mapNotNull { key -> o.optString(key).takeIf { it.isNotBlank() } }
                    .firstOrNull()
                    .orEmpty()
            }
            if (title.isNotBlank() || mediaId.isNotBlank()) {
                hits += KbHit(mediaId = mediaId, title = title)
            }
        }
        return res to hits
    }

    /**
     * 日笔记是否仍在目标知识库中。
     * 仅笔记本能搜到不算；限流抛错；搜索失败时偏保守返回 true，避免误清游标。
     */
    fun isNoteInKnowledgeBase(
        knowledgeBaseId: String,
        title: String,
        noteId: String = "",
    ): Boolean {
        if (knowledgeBaseId.isBlank() || title.isBlank()) return true
        val (res, hits) = searchKnowledge(knowledgeBaseId, title)
        val hint = listOfNotNull(res.error, res.errmsg, "HTTP ${res.statusCode}")
            .joinToString(" ")
        if (isRateLimitHint(hint)) {
            throw IllegalStateException(res.error ?: res.errmsg ?: "请求频率超限")
        }
        if (!res.ok) return true
        return hits.any { hit ->
            hit.title == title ||
                (noteId.isNotBlank() && hit.mediaId.isNotBlank() &&
                    (hit.mediaId == noteId || hit.mediaId.contains(noteId)))
        }
    }

    /**
     * 笔记本侧是否仍有该标题（不能单独作为「知识库已同步」依据）。
     */
    fun noteExistsInNotebook(title: String): Boolean {
        if (title.isBlank()) return false
        val (res, foundId) = searchNoteByTitle(title)
        val hint = listOfNotNull(res.error, res.errmsg, "HTTP ${res.statusCode}")
            .joinToString(" ")
        if (isRateLimitHint(hint)) {
            throw IllegalStateException(res.error ?: res.errmsg ?: "请求频率超限")
        }
        if (res.ok) return !foundId.isNullOrBlank()
        return true
    }

    @Deprecated("易把「仅笔记本残留」当成仍存在；请用 isNoteInKnowledgeBase", ReplaceWith("isNoteInKnowledgeBase(kbId, title, noteId)"))
    fun noteExists(noteId: String, title: String = ""): Boolean {
        if (title.isNotBlank()) return noteExistsInNotebook(title)
        val res = getDocContent(noteId)
        val hint = listOfNotNull(res.error, res.errmsg, "HTTP ${res.statusCode}")
            .joinToString(" ")
        if (isRateLimitHint(hint)) {
            throw IllegalStateException(res.error ?: res.errmsg ?: "请求频率超限")
        }
        if (res.ok && res.data != null) {
            val d = res.data
            val hasBody = sequenceOf("content", "text", "title", "doc_id", "note_id", "id")
                .any { key -> d.optString(key).isNotBlank() } ||
                d.length() > 0
            return hasBody
        }
        return false
    }

    fun searchNoteByTitle(title: String): Pair<ImaApiResult, String?> {
        val body = JSONObject()
            .put("search_type", 0)
            .put("sort_type", 0)
            .put("query_info", JSONObject().put("title", title))
            .put("start", 0)
            .put("end", 20)
        val res = post("openapi/note/v1/search_note_book", body)
        if (!res.ok) return res to null
        val docs = res.data?.optJSONArray("docs") ?: JSONArray()
        for (i in 0 until docs.length()) {
            val item = docs.getJSONObject(i)
            val basic = item.optJSONObject("doc")?.optJSONObject("basic_info")
                ?: item.optJSONObject("basic_info")
                ?: item
            val info = basic.optJSONObject("basic_info") ?: basic
            val t = info.optString("title")
            if (t == title) {
                val id = sequenceOf("docid", "doc_id", "id", "note_id")
                    .mapNotNull { key -> info.optString(key).takeIf { it.isNotBlank() } }
                    .firstOrNull()
                if (id != null) return res to id
            }
        }
        return res to null
    }

    companion object {
        private const val TAG = "ImaApi"
        private const val BASE = "https://ima.qq.com"
        private const val REQUEST_GAP_MS = 350L
        /** 首次失败后的退避：2s / 5s / 10s，共最多 4 次请求 */
        private val RETRY_BACKOFF_MS = longArrayOf(2_000L, 5_000L, 10_000L)
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun isNoteMissingHint(hint: String): Boolean {
            val h = hint.lowercase()
            return h.contains("404") ||
                h.contains("不存在") ||
                h.contains("not found") ||
                h.contains("not_found") ||
                h.contains("已删除") ||
                h.contains("找不到") ||
                h.contains("无权限") ||
                h.contains("110030") ||
                (h.contains("invalid") && (h.contains("doc") || h.contains("note") || h.contains("id")))
        }

        fun isRateLimitHint(hint: String): Boolean {
            val h = hint.lowercase()
            return h.contains("频率") || h.contains("超限") || h.contains("429") ||
                h.contains("403") || h.contains("forbidden") || h.contains("rate")
        }

        /** 403/429/5xx/超时/网络等可自动恢复的临时失败（不含笔记缺失类） */
        fun isRetryableHint(hint: String): Boolean {
            if (hint.isBlank()) return false
            if (isNoteMissingHint(hint)) return false
            val h = hint.lowercase()
            return h.contains("频率") || h.contains("超限") || h.contains("429") ||
                h.contains("403") || h.contains("forbidden") ||
                h.contains("timeout") || h.contains("timed out") || h.contains("超时") ||
                h.contains("connection") || h.contains("network") ||
                h.contains("unable to resolve") || h.contains("failed to connect") ||
                h.contains("503") || h.contains("502") || h.contains("504") || h.contains("500") ||
                h.contains("rate") || h.contains("稍后") || h.contains("繁忙") ||
                h.contains("临时") || h.contains("try again") || h.contains("unavailable") ||
                h.contains("服务器返回异常")
        }

        fun isRetryableResult(result: ImaApiResult): Boolean {
            if (result.ok) return false
            val code = result.statusCode
            if (code == 401 || code == 404) return false
            if (code == 403 || code == 429 || code in 500..599) return true
            val hint = listOfNotNull(result.error, result.errmsg)
                .joinToString(" ")
                .ifBlank { if (code > 0) "HTTP $code" else "" }
            return isRetryableHint(hint)
        }

        private fun sha1Hex(text: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun hmacSha1Hex(key: String, text: String): String {
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA1"))
            return mac.doFinal(text.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
