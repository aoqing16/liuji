package com.liuji.xiaoqi.log

import android.util.Log

/**
 * 写入系统 Log 的同时写入 [RuntimeLogStore]，供 App 内查看。
 */
object AppLog {
    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        RuntimeLogStore.append("D", tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        RuntimeLogStore.append("I", tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
        RuntimeLogStore.append("W", tag, msg, tr)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        RuntimeLogStore.append("E", tag, msg, tr)
    }
}
