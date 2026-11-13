package com.liuji.xiaoqi.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.liuji.xiaoqi.log.AppLog

/**
 * 仅负责拉起前台基准服务，避免 goAsync 超时。
 * adb -s cf249afc shell am broadcast -a com.liuji.xiaoqi.BENCH_SEARCH -n com.liuji.xiaoqi/.debug.FrameSearchBenchmarkReceiver
 */
class FrameSearchBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val service = Intent(context, FrameSearchBenchmarkService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
            AppLog.i(TAG, "已启动 FrameSearchBenchmarkService")
        } catch (e: Exception) {
            AppLog.e(TAG, "启动基准服务失败", e)
        }
    }

    companion object {
        const val ACTION = "com.liuji.xiaoqi.BENCH_SEARCH"
        private const val TAG = "FrameSearchBench"
    }
}
