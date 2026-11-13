package com.liuji.xiaoqi.data.frame

import android.content.Context
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.liuji.xiaoqi.data.db.UiFrameEntity
import com.liuji.xiaoqi.data.db.UsageDatabase
import com.liuji.xiaoqi.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room 之外维护的 FTS5 trigram 索引：幂等建表/触发器、事务双写、可续跑补建。
 */
class FrameFtsIndexer private constructor(
    private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()
    private val backfillMutex = Mutex()

    @Volatile
    private var probed = false

    @Volatile
    var isAvailable: Boolean = false
        private set

    private val _progress = MutableStateFlow(FrameFtsProgress())
    val progress: StateFlow<FrameFtsProgress> = _progress.asStateFlow()

    fun ensureStarted() {
        scope.launch {
            runCatching { initializeAndBackfill() }
                .onFailure { e -> AppLog.e(TAG, "FTS 初始化失败", e) }
        }
    }

    /** 在 Room 实际连接上探测 FTS5 trigram；通过后建表并补建。 */
    suspend fun initializeAndBackfill() = initMutex.withLock {
        val db = UsageDatabase.getInstance(appContext)
        val sqlite = db.openHelper.writableDatabase
        if (!probed) {
            isAvailable = probe(sqlite)
            probed = true
            AppLog.i(TAG, "FTS5 trigram probe=${isAvailable} sqlite=${sqliteVersion(sqlite)}")
        }
        if (!isAvailable) {
            _progress.value = FrameFtsProgress(state = FrameFtsState.UNAVAILABLE)
            return
        }
        ensureSchema(sqlite)
        maybeResetForVersion(sqlite)
        backfillMutex.withLock {
            backfillLocked(db, sqlite)
            backfillBigramsLocked(db, sqlite)
        }
    }

    fun isReady(): Boolean {
        val p = _progress.value
        if (!isAvailable || p.state != FrameFtsState.READY) return false
        // 二元索引未完成时，2 字查询仍会走慢路径，暂不宣称就绪
        return try {
            val sqlite = UsageDatabase.getInstance(appContext).openHelper.writableDatabase
            readMeta(sqlite, KEY_BIGRAM_STATE) == "done"
        } catch (_: Exception) {
            false
        }
    }

    suspend fun insertFrameIndexed(frame: UiFrameEntity): Long {
        val db = UsageDatabase.getInstance(appContext)
        return db.withTransaction {
            val id = db.usageDao().insertFrame(frame)
            if (isAvailable) {
                indexFrame(db.openHelper.writableDatabase, id, frame.textsJson)
            }
            id
        }
    }

    fun indexFrame(sqlite: SupportSQLiteDatabase, id: Long, textsJson: String) {
        val text = searchTextFromJson(textsJson)
        sqlite.execSQL(
            "INSERT INTO $FTS_TABLE(rowid, search_text) VALUES (?, ?)",
            arrayOf<Any?>(id, text),
        )
        indexBigrams(sqlite, id, text)
    }

    fun updateFrameIndex(sqlite: SupportSQLiteDatabase, id: Long, textsJson: String) {
        deleteFrameIndex(sqlite, id)
        indexFrame(sqlite, id, textsJson)
    }

    fun deleteFrameIndex(sqlite: SupportSQLiteDatabase, id: Long) {
        sqlite.execSQL("DELETE FROM $FTS_TABLE WHERE rowid = ?", arrayOf(id))
        sqlite.execSQL("DELETE FROM $BIGRAM_TABLE WHERE frame_id = ?", arrayOf(id))
    }

    private fun indexBigrams(sqlite: SupportSQLiteDatabase, id: Long, searchText: String) {
        sqlite.execSQL("DELETE FROM $BIGRAM_TABLE WHERE frame_id = ?", arrayOf(id))
        val grams = extractBigrams(searchText)
        if (grams.isEmpty()) return
        val stmt = sqlite.compileStatement(
            "INSERT OR IGNORE INTO $BIGRAM_TABLE(gram, frame_id) VALUES (?, ?)",
        )
        try {
            for (gram in grams) {
                stmt.clearBindings()
                stmt.bindString(1, gram)
                stmt.bindLong(2, id)
                stmt.executeInsert()
            }
        } finally {
            stmt.close()
        }
    }

    private suspend fun backfillLocked(db: UsageDatabase, sqlite: SupportSQLiteDatabase) {
        val total = db.usageDao().frameCount().toInt()
        val indexedAtStart = countIndexed(sqlite)
        writeMeta(sqlite, KEY_TOTAL_AT_START, total.toString())
        writeMeta(sqlite, KEY_BACKFILL_STATE, "running")
        writeMeta(sqlite, KEY_INDEX_VERSION, INDEX_VERSION.toString())
        _progress.value = FrameFtsProgress(
            state = FrameFtsState.BACKFILLING,
            indexed = indexedAtStart,
            total = total,
        )

        var indexed = indexedAtStart
        while (true) {
            val batch = loadMissingBatch(sqlite, BATCH_SIZE)
            if (batch.isEmpty()) break
            sqlite.beginTransaction()
            try {
                for ((id, textsJson) in batch) {
                    indexFrame(sqlite, id, textsJson)
                }
                indexed += batch.size
                writeMeta(sqlite, KEY_INDEXED_COUNT, indexed.toString())
                writeMeta(sqlite, KEY_BACKFILL_STATE, "running")
                sqlite.setTransactionSuccessful()
            } finally {
                sqlite.endTransaction()
            }
            _progress.value = FrameFtsProgress(
                state = FrameFtsState.BACKFILLING,
                indexed = indexed,
                total = total.coerceAtLeast(indexed),
            )
            AppLog.i(TAG, "FTS 补建进度 indexed=$indexed/$total (${_progress.value.percent}%)")
        }

        val missing = countMissing(sqlite)
        if (missing == 0) {
            writeMeta(sqlite, KEY_BACKFILL_STATE, "done")
            writeMeta(sqlite, KEY_INDEXED_COUNT, countIndexed(sqlite).toString())
            // 新装且 indexFrame 已同步写 bigram：若游标已到末尾则标记完成
            val bigramCursor = readMeta(sqlite, KEY_BIGRAM_CURSOR)?.toLongOrNull() ?: 0L
            if (readMeta(sqlite, KEY_BIGRAM_STATE) == "done" ||
                (bigramCursor > 0L && countMissing(sqlite) == 0 && bigramCursor >= (total.toLong()))
            ) {
                writeMeta(sqlite, KEY_BIGRAM_STATE, "done")
            }
            _progress.value = FrameFtsProgress(
                state = FrameFtsState.READY,
                indexed = countIndexed(sqlite),
                total = total,
            )
            AppLog.i(TAG, "FTS 补建完成 indexed=${_progress.value.indexed} total=$total")
        } else {
            writeMeta(sqlite, KEY_BACKFILL_STATE, "running")
            _progress.value = FrameFtsProgress(
                state = FrameFtsState.BACKFILLING,
                indexed = countIndexed(sqlite),
                total = total,
            )
            AppLog.w(TAG, "FTS 补建仍有缺失 missing=$missing")
        }
    }

    private fun maybeResetForVersion(sqlite: SupportSQLiteDatabase) {
        val current = readMeta(sqlite, KEY_INDEX_VERSION)?.toIntOrNull()
        if (current == INDEX_VERSION) return
        AppLog.i(TAG, "FTS 索引版本变更 $current -> $INDEX_VERSION")
        if (current == null || current < 1) {
            sqlite.execSQL("DROP TABLE IF EXISTS $FTS_TABLE")
            sqlite.execSQL("DROP TABLE IF EXISTS $BIGRAM_TABLE")
            ensureSchema(sqlite)
            writeMeta(sqlite, KEY_BACKFILL_STATE, "idle")
            writeMeta(sqlite, KEY_INDEXED_COUNT, "0")
            writeMeta(sqlite, KEY_BIGRAM_CURSOR, "0")
            writeMeta(sqlite, KEY_BIGRAM_STATE, "idle")
        } else if (current < 2) {
            // v2：只加二元索引，不重建 FTS
            ensureBigramSchema(sqlite)
            sqlite.execSQL("DELETE FROM $BIGRAM_TABLE")
            writeMeta(sqlite, KEY_BIGRAM_CURSOR, "0")
            writeMeta(sqlite, KEY_BIGRAM_STATE, "idle")
        }
        writeMeta(sqlite, KEY_INDEX_VERSION, INDEX_VERSION.toString())
    }

    private fun ensureSchema(sqlite: SupportSQLiteDatabase) {
        sqlite.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS $FTS_TABLE
            USING fts5(search_text, tokenize='trigram case_sensitive 0')
            """.trimIndent(),
        )
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $META_TABLE(
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
            )
            """.trimIndent(),
        )
        ensureBigramSchema(sqlite)
        sqlite.execSQL("DROP TRIGGER IF EXISTS ui_frames_fts_ad")
        sqlite.execSQL("DROP TRIGGER IF EXISTS ui_frames_fts_au")
        sqlite.execSQL(
            """
            CREATE TRIGGER ui_frames_fts_ad
            AFTER DELETE ON ui_frames
            BEGIN
                DELETE FROM $FTS_TABLE WHERE rowid = old.id;
                DELETE FROM $BIGRAM_TABLE WHERE frame_id = old.id;
            END
            """.trimIndent(),
        )
        sqlite.execSQL(
            """
            CREATE TRIGGER ui_frames_fts_au
            AFTER UPDATE OF texts_json ON ui_frames
            BEGIN
                DELETE FROM $FTS_TABLE WHERE rowid = old.id;
                DELETE FROM $BIGRAM_TABLE WHERE frame_id = old.id;
            END
            """.trimIndent(),
        )
    }

    private fun ensureBigramSchema(sqlite: SupportSQLiteDatabase) {
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $BIGRAM_TABLE(
                gram TEXT NOT NULL,
                frame_id INTEGER NOT NULL,
                PRIMARY KEY (gram, frame_id)
            ) WITHOUT ROWID
            """.trimIndent(),
        )
        // 按 frame_id 删除/清理时必需，否则会全表扫
        sqlite.execSQL(
            "CREATE INDEX IF NOT EXISTS index_frame_bigram_frame_id ON $BIGRAM_TABLE(frame_id)",
        )
    }

    /** 按 id 游标为已有帧补写二元索引；直接读 frame_fts.search_text，避免再解析 JSON。 */
    private suspend fun backfillBigramsLocked(db: UsageDatabase, sqlite: SupportSQLiteDatabase) {
        if (readMeta(sqlite, KEY_BIGRAM_STATE) == "done") return
        val total = countIndexed(sqlite).coerceAtLeast(db.usageDao().frameCount().toInt())
        var cursor = readMeta(sqlite, KEY_BIGRAM_CURSOR)?.toLongOrNull() ?: 0L
        writeMeta(sqlite, KEY_BIGRAM_STATE, "running")
        _progress.value = FrameFtsProgress(
            state = FrameFtsState.BACKFILLING,
            indexed = cursor.toInt().coerceAtMost(total),
            total = total,
        )
        AppLog.i(TAG, "二元索引补建开始 cursor=$cursor total=$total")
        while (true) {
            val batch = loadFtsTextAfterId(sqlite, cursor, BIGRAM_BATCH)
            if (batch.isEmpty()) break
            sqlite.beginTransaction()
            try {
                val stmt = sqlite.compileStatement(
                    "INSERT OR IGNORE INTO $BIGRAM_TABLE(gram, frame_id) VALUES (?, ?)",
                )
                try {
                    for ((id, searchText) in batch) {
                        sqlite.execSQL(
                            "DELETE FROM $BIGRAM_TABLE WHERE frame_id = ?",
                            arrayOf(id),
                        )
                        for (gram in extractBigrams(searchText)) {
                            stmt.clearBindings()
                            stmt.bindString(1, gram)
                            stmt.bindLong(2, id)
                            stmt.executeInsert()
                        }
                        cursor = id
                    }
                } finally {
                    stmt.close()
                }
                writeMeta(sqlite, KEY_BIGRAM_CURSOR, cursor.toString())
                writeMeta(sqlite, KEY_BIGRAM_STATE, "running")
                sqlite.setTransactionSuccessful()
            } finally {
                sqlite.endTransaction()
            }
            _progress.value = FrameFtsProgress(
                state = FrameFtsState.BACKFILLING,
                indexed = cursor.toInt().coerceAtMost(total),
                total = total,
            )
            AppLog.i(TAG, "二元索引补建 cursor=$cursor/$total")
        }
        writeMeta(sqlite, KEY_BIGRAM_STATE, "done")
        writeMeta(sqlite, KEY_BIGRAM_CURSOR, cursor.toString())
        if (countMissing(sqlite) == 0) {
            _progress.value = FrameFtsProgress(
                state = FrameFtsState.READY,
                indexed = countIndexed(sqlite),
                total = total,
            )
        }
        AppLog.i(TAG, "二元索引补建完成 cursor=$cursor")
    }

    private fun loadFtsTextAfterId(
        sqlite: SupportSQLiteDatabase,
        afterId: Long,
        limit: Int,
    ): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>(limit)
        sqlite.query(
            """
            SELECT rowid AS id, search_text FROM $FTS_TABLE
            WHERE rowid > ?
            ORDER BY rowid ASC
            LIMIT $limit
            """.trimIndent(),
            arrayOf(afterId),
        ).use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val textIdx = c.getColumnIndexOrThrow("search_text")
            while (c.moveToNext()) {
                out += c.getLong(idIdx) to (c.getString(textIdx) ?: "")
            }
        }
        return out
    }

    private fun loadMissingBatch(
        sqlite: SupportSQLiteDatabase,
        limit: Int,
    ): List<Pair<Long, String>> {
        val sql = """
            SELECT f.id, f.texts_json
            FROM ui_frames f
            LEFT JOIN $FTS_TABLE t ON t.rowid = f.id
            WHERE t.rowid IS NULL
            ORDER BY f.id ASC
            LIMIT $limit
        """.trimIndent()
        val out = ArrayList<Pair<Long, String>>(limit)
        sqlite.query(sql).use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val jsonIdx = c.getColumnIndexOrThrow("texts_json")
            while (c.moveToNext()) {
                out += c.getLong(idIdx) to c.getString(jsonIdx)
            }
        }
        return out
    }

    private fun countIndexed(sqlite: SupportSQLiteDatabase): Int {
        sqlite.query("SELECT COUNT(*) FROM $FTS_TABLE").use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun countMissing(sqlite: SupportSQLiteDatabase): Int {
        val sql = """
            SELECT COUNT(*)
            FROM ui_frames f
            LEFT JOIN $FTS_TABLE t ON t.rowid = f.id
            WHERE t.rowid IS NULL
        """.trimIndent()
        sqlite.query(sql).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun readMeta(sqlite: SupportSQLiteDatabase, key: String): String? {
        sqlite.query(
            "SELECT value FROM $META_TABLE WHERE key = ?",
            arrayOf(key),
        ).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    private fun writeMeta(sqlite: SupportSQLiteDatabase, key: String, value: String) {
        sqlite.execSQL(
            "INSERT OR REPLACE INTO $META_TABLE(key, value) VALUES (?, ?)",
            arrayOf(key, value),
        )
    }

    companion object {
        private const val TAG = "FrameFts"
        const val FTS_TABLE = "frame_fts"
        const val BIGRAM_TABLE = "frame_bigram"
        const val META_TABLE = "frame_fts_meta"
        /** v2：增加二元倒排，加速 2 字搜索 */
        const val INDEX_VERSION = 2
        private const val BATCH_SIZE = 500
        private const val BIGRAM_BATCH = 1000
        private const val KEY_INDEX_VERSION = "index_version"
        private const val KEY_BACKFILL_STATE = "backfill_state"
        private const val KEY_INDEXED_COUNT = "indexed_count"
        private const val KEY_TOTAL_AT_START = "total_at_start"
        private const val KEY_BIGRAM_STATE = "bigram_state"
        private const val KEY_BIGRAM_CURSOR = "bigram_cursor"

        private const val SEP = "\u0001"

        @Volatile
        private var instance: FrameFtsIndexer? = null

        fun get(context: Context): FrameFtsIndexer {
            return instance ?: synchronized(this) {
                instance ?: FrameFtsIndexer(context.applicationContext).also { instance = it }
            }
        }

        /** 在实际业务库连接上探测：建临时表 → 插入 → MATCH → LIKE → 删除。 */
        fun probe(sqlite: SupportSQLiteDatabase): Boolean {
            val table = "fts_probe_${System.nanoTime()}"
            return try {
                sqlite.execSQL(
                    "CREATE VIRTUAL TABLE $table USING fts5(x, tokenize='trigram case_sensitive 0')",
                )
                sqlite.execSQL("INSERT INTO $table(rowid, x) VALUES (1, ?)", arrayOf("微信支付成功ABC"))
                val matchOk = sqlite.query(
                    "SELECT rowid FROM $table WHERE $table MATCH ?",
                    arrayOf("\"微信支付\""),
                ).use { it.moveToFirst() }
                val likeOk = sqlite.query(
                    "SELECT rowid FROM $table WHERE x LIKE ? ESCAPE '\\'",
                    arrayOf("%微信%"),
                ).use { it.moveToFirst() }
                matchOk && likeOk
            } catch (e: Exception) {
                AppLog.w(TAG, "FTS5 trigram 不可用: ${e.message}", e)
                false
            } finally {
                try {
                    sqlite.execSQL("DROP TABLE IF EXISTS $table")
                } catch (_: Exception) {
                }
            }
        }

        fun sqliteVersion(sqlite: SupportSQLiteDatabase): String {
            return try {
                sqlite.query("SELECT sqlite_version()").use { c ->
                    if (c.moveToFirst()) c.getString(0) else "?"
                }
            } catch (_: Exception) {
                "?"
            }
        }

        fun searchTextFromJson(textsJson: String): String {
            val nodes = FrameJson.textsFromJson(textsJson)
            if (nodes.isEmpty()) return ""
            val parts = ArrayList<String>(nodes.size * 2)
            for (n in nodes) {
                if (n.text.isNotBlank()) parts += n.text
                if (n.desc.isNotBlank()) parts += n.desc
            }
            return parts.joinToString(SEP)
        }

        fun escapeLike(term: String): String =
            term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

        fun escapeGlob(term: String): String = buildString(term.length) {
            for (c in term) {
                when (c) {
                    '*', '?', '[' -> {
                        append('[')
                        append(c)
                        append(']')
                    }
                    else -> append(c)
                }
            }
        }

        fun ftsPhrase(term: String): String {
            val escaped = term.replace("\"", "\"\"")
            return "\"$escaped\""
        }

        /** 1 字走 LIKE；2 字走 bigram；≥3 走 FTS MATCH。 */
        fun needsLike(term: String): Boolean = term.length == 1

        fun needsBigram(term: String): Boolean = term.length == 2

        fun extractBigrams(searchText: String): Set<String> {
            if (searchText.isEmpty()) return emptySet()
            val out = LinkedHashSet<String>()
            for (part in searchText.split(SEP)) {
                if (part.length < 2) continue
                for (i in 0 until part.length - 1) {
                    out += part.substring(i, i + 2)
                }
            }
            return out
        }
    }
}

enum class FrameFtsState {
    IDLE,
    BACKFILLING,
    READY,
    UNAVAILABLE,
}

data class FrameFtsProgress(
    val state: FrameFtsState = FrameFtsState.IDLE,
    val indexed: Int = 0,
    val total: Int = 0,
) {
    val percent: Int
        get() = if (total <= 0) 0 else ((indexed.toLong() * 100L) / total).toInt().coerceIn(0, 100)

    val message: String?
        get() = when (state) {
            FrameFtsState.BACKFILLING -> "正在建立搜索索引 $percent%"
            FrameFtsState.UNAVAILABLE -> null
            FrameFtsState.READY, FrameFtsState.IDLE -> null
        }
}
