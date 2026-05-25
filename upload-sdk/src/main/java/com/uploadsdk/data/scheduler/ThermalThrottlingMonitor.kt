package com.uploadsdk.data.scheduler

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThermalThrottlingMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun isThermallyThrottled(): Boolean {
        // Android doesn't expose direct thermal state before API 29 easily
        // In production, use ThermalManager on API 29+ or listen to jobscheduler callbacks
        return false
    }

    fun getRecommendedParallelism(): Int {
        return if (isThermallyThrottled()) 1 else 3
    }
}
