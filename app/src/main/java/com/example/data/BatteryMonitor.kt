package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.model.BatteryRuntimeStatus
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks real Android device battery percentage and AC/USB charging status via the sticky
 * [Intent.ACTION_BATTERY_CHANGED] broadcast without requiring any special permissions.
 */
class BatteryMonitor(context: Context) {

  private val appContext = context.applicationContext
  private val _status = MutableStateFlow(BatteryRuntimeStatus())
  val status: StateFlow<BatteryRuntimeStatus> = _status.asStateFlow()

  private var isRegistered = false

  private val receiver =
    object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        if (intent != null) {
          _status.value = parseBatteryIntent(intent)
        }
      }
    }

  fun start() {
    if (isRegistered) return
    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val sticky = runCatching { appContext.registerReceiver(receiver, filter) }.getOrNull()
    isRegistered = true
    if (sticky != null) {
      _status.value = parseBatteryIntent(sticky)
    }
  }

  fun stop() {
    if (!isRegistered) return
    runCatching { appContext.unregisterReceiver(receiver) }
    isRegistered = false
  }

  companion object {
    fun parseBatteryIntent(intent: Intent): BatteryRuntimeStatus {
      val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
      val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
      val pct =
        if (level >= 0) ((level.toFloat() / scale.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        else 100
      val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
      val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
      val isCharging =
        status == BatteryManager.BATTERY_STATUS_CHARGING ||
          (status == BatteryManager.BATTERY_STATUS_FULL && plugged != 0) ||
          plugged != 0
      return BatteryRuntimeStatus(levelPercent = pct, isCharging = isCharging)
    }
  }
}
