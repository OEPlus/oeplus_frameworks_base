/*
 * SPDX-FileCopyrightText: The OEPlus Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.shade

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.view.Display
import android.view.Surface
import android.view.SurfaceControl
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shared.system.BlurUtils
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * OEPlus glass theme: moves the light the glass material is lit from with the device tilt, like
 * Apple's Liquid Glass, while the shade or Quick Settings are open and the screen is on.
 *
 * Uses only the accelerometer, only while the shade is open, and tells SurfaceFlinger only when
 * the light turns noticeably, so a device held still causes no redraws. Does nothing unless the
 * glass theme is on.
 */
@SysUISingleton
class GlassLightController
@Inject
constructor(
    private val context: Context,
    private val sensorManager: SensorManager,
    private val shadeInteractor: ShadeInteractor,
    private val powerInteractor: PowerInteractor,
    @Application private val scope: CoroutineScope,
    @Background private val bgHandler: Handler,
) : CoreStartable, SensorEventListener {

    // Accessed on the background handler only.
    private var listening = false
    private var hasGravity = false
    private var upX = 0f
    private var upY = 0f
    private var rotation = Surface.ROTATION_0
    private var sentAngle = Float.NaN

    override fun start() {
        if (!BlurUtils.isOEPlusGlassEnabled()) {
            return
        }
        // Release a light a previous SystemUI process may have left active.
        bgHandler.post { SurfaceControl.setGlassLight(false, 0f, 0f) }
        scope.launch {
            combine(shadeInteractor.isAnyExpanded, powerInteractor.isInteractive) { open, on ->
                    open && on
                }
                .distinctUntilChanged()
                .collect { listen -> bgHandler.post { setListening(listen) } }
        }
    }

    private fun setListening(listen: Boolean) {
        if (listen == listening) {
            return
        }
        if (listen) {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            rotation =
                context
                    .getSystemService(DisplayManager::class.java)
                    ?.getDisplay(Display.DEFAULT_DISPLAY)
                    ?.rotation ?: Surface.ROTATION_0
            hasGravity = false
            sentAngle = Float.NaN
            listening =
                sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                    bgHandler,
                )
        } else {
            listening = false
            sensorManager.unregisterListener(this)
            SurfaceControl.setGlassLight(false, 0f, 0f)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!listening) {
            return
        }
        // At rest the accelerometer points up, away from gravity. Low-pass it to drop shakes.
        // Screen y points down while device y points up.
        val x = event.values[0] / SensorManager.GRAVITY_EARTH
        val y = -event.values[1] / SensorManager.GRAVITY_EARTH
        if (hasGravity) {
            upX += (x - upX) * FILTER
            upY += (y - upY) * FILTER
        } else {
            upX = x
            upY = y
            hasGravity = true
        }

        // Light from above the device, leaning to the top left like the default light, so a
        // device lying flat keeps the default look.
        val naturalX = upX + BIAS
        val naturalY = upY + BIAS
        val (lightX, lightY) =
            when (rotation) {
                Surface.ROTATION_90 -> naturalY to -naturalX
                Surface.ROTATION_180 -> -naturalX to -naturalY
                Surface.ROTATION_270 -> -naturalY to naturalX
                else -> naturalX to naturalY
            }
        val length = hypot(lightX, lightY)
        if (length < 0.001f) {
            return
        }
        val angle = atan2(lightY, lightX)
        if (!sentAngle.isNaN() && angleDelta(angle, sentAngle) < MIN_ANGLE_CHANGE) {
            return
        }
        sentAngle = angle
        SurfaceControl.setGlassLight(true, lightX / length, lightY / length)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun angleDelta(a: Float, b: Float): Float {
        val d = abs(a - b) % TWO_PI
        return if (d > PI.toFloat()) TWO_PI - d else d
    }

    private companion object {
        const val FILTER = 0.15f
        const val BIAS = -0.5f
        // About 1.5 degrees.
        const val MIN_ANGLE_CHANGE = 0.026f
        const val TWO_PI = (2.0 * PI).toFloat()
    }
}
