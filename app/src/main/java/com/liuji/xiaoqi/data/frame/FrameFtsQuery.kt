package com.liuji.xiaoqi.data.frame

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

data class FrameSearchCursor(
    val capturedAt: Long,
    val id: Long,
)

data class FrameSearchPage(
    val hits: List<FrameSearchHit>,
    val nextCursor: FrameSearchCursor?,
)

/**
 * 组装 FTS5 / bigram / LIKE 搜索 SQL。
 * ≥3 字 MATCH；2 字 bigram；1 字 LIKE；严格模式额外 GLOB。
 */
object FrameFtsQuery {
    private const val FTS = FrameFtsIndexer.FTS_TABLE
    private const val BIGRAM = FrameFtsIndexer.BIGRAM_TABLE

    fun pageQuery(
        terms: List<String>,
        matchAll: Boolean,
        strict: Boolean,
        scopePackages: Set<String>?,
        cursor: FrameSearchCursor?,
        limit: Int,
        startMillis: Long? = null,
        endMillisExclusive: Long? = null,
        descending: Boolean = true,
        strategy: Strategy = Strategy.JOIN_THEN_ORDER,
    ): SupportSQLiteQuery {
        val built = buildWhere(
            terms, matchAll, strict, scopePackages, cursor,
            startMillis, endMillisExclusive, descending,
        )
        val needFtsJoin = built.needsFtsJoin
        val from = if (needFtsJoin) {
            "ui_frames f INNER JOIN $FTS t ON t.rowid = f.id"
        } else {
            "ui_frames f"
        }
        val orderBy = if (descending) {
            "f.captured_at DESC, f.id DESC"
        } else {
            "f.captured_at ASC, f.id ASC"
        }
        val sql = when (strategy) {
            Strategy.JOIN_THEN_ORDER -> """
                SELECT f.id AS id, f.package_name AS package_name,
                       f.captured_at AS captured_at, f.texts_json AS texts_json
                FROM $from
                WHERE ${built.where}
                ORDER BY $orderBy
                LIMIT $limit
            """.trimIndent()
            Strategy.MATCH_IDS_THEN_SCAN -> {
                val matchOnly = built.matchExpr
                if (matchOnly == null) {
                    return pageQuery(
                        terms, matchAll, strict, scopePackages, cursor, limit,
                        startMillis, endMillisExclusive, descending,
                        Strategy.JOIN_THEN_ORDER,
                    )
                }
                """
                SELECT f.id AS id, f.package_name AS package_name,
                       f.captured_at AS captured_at, f.texts_json AS texts_json
                FROM ui_frames f
                WHERE f.id IN (SELECT rowid FROM $FTS WHERE $FTS MATCH ?)
                  ${built.pkgAndCursorOnly}
                ORDER BY $orderBy
                LIMIT $limit
                """.trimIndent()
            }
        }
        val args = when (strategy) {
            Strategy.JOIN_THEN_ORDER -> built.args.toTypedArray()
            Strategy.MATCH_IDS_THEN_SCAN -> {
                val matchExpr = built.matchExpr ?: return pageQuery(
                    terms, matchAll, strict, scopePackages, cursor, limit,
                    startMillis, endMillisExclusive, descending,
                    Strategy.JOIN_THEN_ORDER,
                )
                (listOf(matchExpr) + built.pkgCursorArgs).toTypedArray()
            }
        }
        return SimpleSQLiteQuery(sql, args)
    }

    fun countQuery(
        terms: List<String>,
        matchAll: Boolean,
        strict: Boolean,
        scopePackages: Set<String>?,
        startMillis: Long? = null,
        endMillisExclusive: Long? = null,
    ): SupportSQLiteQuery {
        val built = buildWhere(
            terms, matchAll, strict, scopePackages, cursor = null,
            startMillis, endMillisExclusive, descending = true,
        )
        val from = if (built.needsFtsJoin) {
            "ui_frames f INNER JOIN $FTS t ON t.rowid = f.id"
        } else {
            "ui_frames f"
        }
        val sql = """
            SELECT COUNT(*)
            FROM $from
            WHERE ${built.where}
        """.trimIndent()
        return SimpleSQLiteQuery(sql, built.args.toTypedArray())
    }

    private data class Built(
        val where: String,
        val args: List<Any>,
        val matchExpr: String?,
        val pkgAndCursorOnly: String,
        val pkgCursorArgs: List<Any>,
        val needsFtsJoin: Boolean,
    )

    private fun buildWhere(
        terms: List<String>,
        matchAll: Boolean,
        strict: Boolean,
        scopePackages: Set<String>?,
        cursor: FrameSearchCursor?,
        startMillis: Long?,
        endMillisExclusive: Long?,
        descending: Boolean,
    ): Built {
        val args = ArrayList<Any>()
        val clauses = ArrayList<String>()

        val longTerms = terms.filter { it.length >= 3 }
        val bigramTerms = terms.filter { FrameFtsIndexer.needsBigram(it) }
        val likeTerms = terms.filter { FrameFtsIndexer.needsLike(it) }

        var matchExpr: String? = null
        var needsFtsJoin = false

        if (matchAll) {
            if (longTerms.isNotEmpty()) {
                needsFtsJoin = true
                matchExpr = longTerms.joinToString(" AND ") { FrameFtsIndexer.ftsPhrase(it) }
                args += matchExpr
                clauses += "t MATCH ?"
            }
            for (t in bigramTerms) {
                args += t
                clauses += "f.id IN (SELECT frame_id FROM $BIGRAM WHERE gram = ?)"
            }
            for (t in likeTerms) {
                needsFtsJoin = true
                args += "%${FrameFtsIndexer.escapeLike(t)}%"
                clauses += "t.search_text LIKE ? ESCAPE '\\'"
            }
        } else {
            // OR：按种类拆开后整体 OR
            val orParts = ArrayList<String>()
            if (longTerms.isNotEmpty()) {
                needsFtsJoin = true
                matchExpr = longTerms.joinToString(" OR ") { FrameFtsIndexer.ftsPhrase(it) }
                args += matchExpr
                orParts += "t MATCH ?"
            }
            for (t in bigramTerms) {
                args += t
                orParts += "f.id IN (SELECT frame_id FROM $BIGRAM WHERE gram = ?)"
            }
            for (t in likeTerms) {
                needsFtsJoin = true
                args += "%${FrameFtsIndexer.escapeLike(t)}%"
                orParts += "t.search_text LIKE ? ESCAPE '\\'"
            }
            when (orParts.size) {
                0 -> clauses += "1"
                1 -> clauses += orParts[0]
                else -> clauses += "(" + orParts.joinToString(" OR ") + ")"
            }
        }

        if (strict) {
            needsFtsJoin = true
            for (t in terms) {
                args += "*${FrameFtsIndexer.escapeGlob(t)}*"
                clauses += "t.search_text GLOB ?"
            }
        }

        val pkgCursorArgs = ArrayList<Any>()
        val pkgCursorClauses = ArrayList<String>()
        if (scopePackages != null) {
            require(scopePackages.isNotEmpty())
            val placeholders = scopePackages.joinToString(",") { "?" }
            for (p in scopePackages) {
                args += p
                pkgCursorArgs += p
            }
            clauses += "f.package_name IN ($placeholders)"
            pkgCursorClauses += "AND f.package_name IN ($placeholders)"
        }
        if (startMillis != null && endMillisExclusive != null) {
            args += startMillis
            args += endMillisExclusive
            pkgCursorArgs += startMillis
            pkgCursorArgs += endMillisExclusive
            clauses += "f.captured_at >= ? AND f.captured_at < ?"
            pkgCursorClauses += "AND f.captured_at >= ? AND f.captured_at < ?"
        }
        if (cursor != null) {
            args += cursor.capturedAt
            args += cursor.capturedAt
            args += cursor.id
            pkgCursorArgs += cursor.capturedAt
            pkgCursorArgs += cursor.capturedAt
            pkgCursorArgs += cursor.id
            val cursorSql = if (descending) {
                "(f.captured_at < ? OR (f.captured_at = ? AND f.id < ?))"
            } else {
                "(f.captured_at > ? OR (f.captured_at = ? AND f.id > ?))"
            }
            clauses += cursorSql
            pkgCursorClauses += "AND $cursorSql"
        }

        return Built(
            where = clauses.joinToString(" AND ").ifEmpty { "1" },
            args = args,
            matchExpr = matchExpr,
            pkgAndCursorOnly = pkgCursorClauses.joinToString(" "),
            pkgCursorArgs = pkgCursorArgs,
            needsFtsJoin = needsFtsJoin,
        )
    }

    enum class Strategy {
        JOIN_THEN_ORDER,
        MATCH_IDS_THEN_SCAN,
    }
}
