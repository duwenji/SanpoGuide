package com.example.sanpoguide.ui

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.abs

/**
 * Which way the user faces, in degrees clockwise from true north; null when unknown.
 *
 * The compass gives it even while standing, which is when people look at the map. Phones
 * without one fall back to the GPS heading, which only exists while walking.
 * The sensor runs only while the screen is in front of the user (resumed).
 */
@Composable
fun rememberHeading(location: Location?): Float? {
    val context = LocalContext.current
    val sensors = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val rotation = remember { sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    val compass = remember { mutableStateOf<Float?>(null) }
    val currentLocation by rememberUpdatedState(location)

    if (rotation != null) {
        LifecycleResumeEffect(sensors) {
            val listener = object : SensorEventListener {
                private val matrix = FloatArray(9)
                private val remapped = FloatArray(9)
                private val angles = FloatArray(3)

                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    val (axisX, axisY) = screenAxes(context)
                    SensorManager.remapCoordinateSystem(matrix, axisX, axisY, remapped)
                    SensorManager.getOrientation(remapped, angles)
                    val magnetic = Math.toDegrees(angles[0].toDouble()).toFloat()
                    // Magnetic north is several degrees off true north in Japan (about 7-9° west).
                    val declination = currentLocation?.let {
                        GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(), it.altitude.toFloat(), it.time)
                            .declination
                    } ?: 0f
                    val degrees = normalize(magnetic + declination)
                    // Small wobbles would redraw the map many times a second for nothing.
                    val last = compass.value
                    if (last == null || angleBetween(last, degrees) >= MIN_CHANGE_DEGREES) compass.value = degrees
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            sensors.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
            onPauseOrDispose { sensors.unregisterListener(listener) }
        }
        return compass.value
    }
    return location?.takeIf { it.hasBearing() && it.speed >= MIN_HEADING_SPEED }?.bearing
}

/** Keeps "up" on the screen as the reference whichever way the phone is turned. */
@Suppress("DEPRECATION") // Display.getRotation via Context.getDisplay needs API 30.
private fun screenAxes(context: Context): Pair<Int, Int> =
    when ((context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }

private fun normalize(degrees: Float) = ((degrees % 360) + 360) % 360

private fun angleBetween(a: Float, b: Float): Float = abs(((b - a) % 360 + 540) % 360 - 180)

private const val MIN_CHANGE_DEGREES = 3f

/** Below walking pace (m/s) the GPS heading is noise; same as the walk service's. */
private const val MIN_HEADING_SPEED = 0.5f
