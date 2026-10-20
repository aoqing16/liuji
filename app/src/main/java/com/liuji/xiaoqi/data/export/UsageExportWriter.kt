package com.liuji.xiaoqi.data.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.liuji.xiaoqi.data.ExportFrameTexts
import com.liuji.xiaoqi.data.PeriodSummary
import com.liuji.xiaoqi.data.frame.TimelineItem
import java.io.File
import java.nio.charset.StandardCharsets

data class ExportResult(
    val file: File,
    val uri: Uri,
    val fileName: String,
)

object UsageExportWriter {

    fun writeAndGetShareUri(
        context: Context,
        summary: PeriodSummary,
        frameTexts: ExportFrameTexts?,
        timeline: List<TimelineItem> = emptyList(),
        appLabelOf: (String) -> String = { it },
        includeScreenTexts: Boolean = true,
    ): ExportResult {
        val fileName = UsageJsonExporter.fileName(summary)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.bufferedWriter(StandardCharsets.UTF_8).use { out ->
            UsageJsonExporter.writeMarkdown(
                out = out,
                summary = summary,
                timeline = timeline,
                appLabelOf = appLabelOf,
                textsByApp = frameTexts?.textsByApp,
                frameCount = frameTexts?.frameCount,
                includeScreenTexts = includeScreenTexts,
            )
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return ExportResult(file = file, uri = uri, fileName = fileName)
    }

    fun shareIntent(context: Context, result: ExportResult): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, result.uri)
            putExtra(Intent.EXTRA_SUBJECT, result.fileName)
            // 不附带 EXTRA_TEXT，避免文件管理器「另存为」只保存说明字符串
            clipData = ClipData.newUri(context.contentResolver, result.fileName, result.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
