package com.uploadsdk.data.scheduler

import android.content.Context
import android.os.Build
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val status = powerManager.currentThermalStatus
            return status >= PowerManager.THERMAL_STATUS_SEVERE
        }
        return false
    }

    fun shouldReduceWork(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val status = powerManager.currentThermalStatus
            return status >= PowerManager.THERMAL_STATUS_MODERATE
        }
        return false
    }

    fun getRecommendedParallelism(defaultParallelism: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_SEVERE,
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> 1
                PowerManager.THERMAL_STATUS_MODERATE -> (defaultParallelism / 2).coerceAtLeast(1)
                else -> defaultParallelism
            }
        }
        return defaultParallelism
    }
}
