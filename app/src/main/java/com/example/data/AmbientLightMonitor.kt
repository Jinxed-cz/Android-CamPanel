package com.example.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors the hardware ambient light sensor ([Sensor.TYPE_LIGHT]) with exponential smoothing and
 * hysteresis so window brightness adapts cleanly without flickering.
 */
class AmbientLightMonitor(context: Context) {

  private val sensorManager =
    context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  private val lightSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

  val hasLightSensor: Boolean
    get() = lightSensor != null

  private val _ambientLux = MutableStateFlow<Float?>(null)
  val ambientLux: StateFlow<Float?> = _ambientLux.asStateFlow()

  private var smoothedLux: Float? = null
  private var isRegistered = false

  private val listener =
    object : SensorEventListener {
      override fun onSensorChanged(event: SensorEvent?) {
        val rawLux = event?.values?.firstOrNull() ?: return
        if (rawLux.isNaN() || rawLux < 0f) return
        val prev = smoothedLux
        val next =
          if (prev == null) {
            rawLux
          } else {
            prev * 0.72f + rawLux * 0.28f
          }
        smoothedLux = next
        val published = _ambientLux.value
        val significantChange =
          published == null || abs(next - published) >= (published * 0.08f).coerceAtLeast(1.5f)
        if (significantChange) {
          _ambientLux.value = next
        }
      }

      override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

  fun start() {
    val sensor = lightSensor ?: return
    if (isRegistered) return
    isRegistered =
      sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
  }

  fun stop() {
    if (!isRegistered) return
    sensorManager?.unregisterListener(listener)
    isRegistered = false
  }
}
