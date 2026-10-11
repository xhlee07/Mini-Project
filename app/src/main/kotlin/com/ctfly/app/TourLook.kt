package com.ctfly.app

import kotlin.math.exp

/** Small frame-rate-independent easing, with unrestricted horizontal turning. */
class TourLook(yaw: Float = 0f, pitch: Float = 0f) {
    var yaw = yaw
        private set

    var pitch = pitch
        private set

    private var targetYaw = yaw
    private var targetPitch = pitch

    fun reset(yaw: Float, pitch: Float) {
        this.yaw = yaw
        this.pitch = pitch
        targetYaw = yaw
        targetPitch = pitch
    }

    fun add(yaw: Float, pitch: Float) {
        targetYaw += yaw
        targetPitch = (targetPitch + pitch).coerceIn(-1.1f, 1.1f)
    }

    fun step(seconds: Float) {
        val blend = 1f - exp(-24f * seconds.coerceIn(0f, 0.1f))
        yaw += (targetYaw - yaw) * blend
        pitch += (targetPitch - pitch) * blend
    }
}
