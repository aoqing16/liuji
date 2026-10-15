package com.liuji.xiaoqi.log

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RuntimeLogExportResult(
    val file: File,
    val uri: Uri,
    val fileName: String,
)

object RuntimeLogExporter {

    fun writeAndGetShareUri(context: Context): RuntimeLogExportResult? {
        val body = RuntimeLogStore.snapshot()
        if (body.isBlank()) return null
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
        val fileName = "liuji-runtime-log-$stamp.txt"
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(body, StandardCharsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return RuntimeLogExportResult(file = file, uri = uri, fileName = fileName)
    }

    fun shareIntent(result: RuntimeLogExportResult): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, result.uri)
            putExtra(Intent.EXTRA_SUBJECT, result.fileName)
            // 不附带 EXTRA_TEXT，避免文件管理器「另存为」只保存说明字符串
            clipData = ClipData.newUri(null, result.fileName, result.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
