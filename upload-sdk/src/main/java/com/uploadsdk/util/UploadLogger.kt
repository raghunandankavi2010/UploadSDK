package com.uploadsdk.util

import android.util.Log

object UploadLogger {
    private const val TAG = "UploadSDK"
    var isEnabled = true

    fun d(message: String) {
        if (isEnabled) Log.d(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (isEnabled) Log.e(TAG, message, throwable)
    }

    fun i(message: String) {
        if (isEnabled) Log.i(TAG, message)
    }
}
