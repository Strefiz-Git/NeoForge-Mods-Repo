package com.littlemine.webbrowser.client

import com.littlemine.webbrowser.config.MonitorConfig

object BrowserConstants {
    const val BLOCK_PIXEL_WIDTH = 120
    const val BLOCK_PIXEL_HEIGHT = 120
    val MAX_BLOCK_PIXELS: Int get() = MonitorConfig.maxResolutionPerBlock?.get() ?: 120
    const val MIN_BLOCK_PIXELS = 4
    const val VIEW_DISTANCE_CHUNKS = 6
    const val VIEW_DISTANCE_BLOCKS = VIEW_DISTANCE_CHUNKS * 16
    const val MAX_BRIGHTNESS = 500

    /** Maps brightness (0-500) to a 0-1 normalized value for rendering. */
    fun brightnessToFloat(raw: Int): Float {
        val clamped = raw.coerceIn(0, MAX_BRIGHTNESS)
        val byte = if (clamped <= 100) {
            (clamped * 180 / 100).coerceIn(0, 255)
        } else {
            (180 + (clamped - 100) * 75 / 400).coerceIn(0, 255)
        }
        return byte / 255f
    }
}
