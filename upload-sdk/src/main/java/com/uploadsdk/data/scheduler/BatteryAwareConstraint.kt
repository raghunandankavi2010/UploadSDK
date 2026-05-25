package com.uploadsdk.data.scheduler

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryAwareConstraint @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun shouldDefer(): Boolean {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = level * 100 / scale.toFloat()

        // Defer if battery < 15% and not charging
        if (batteryPct < 15f && !isCharging(batteryStatus)) {
            return true
        }
        return false
    }

    fun isCharging(batteryStatus: Intent?): Boolean {
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun getRecommendedNetworkType(): androidx.work.NetworkType {
        return if (shouldDefer()) {
            androidx.work.NetworkType.NOT_REQUIRED // Will be blocked by battery constraint anyway
        } else {
            androidx.work.NetworkType.CONNECTED
        }
    }
}
