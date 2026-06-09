package com.littlemine.webbrowser.client

import com.littlemine.webbrowser.config.MonitorConfig

object BrowserConstants {
    const val BLOCK_PIXEL_WIDTH = 120
    const val BLOCK_PIXEL_HEIGHT = 120
    val MAX_BLOCK_PIXELS: Int get() = if (MonitorConfig.maxResolutionPerBlock != null) MonitorConfig.maxResolutionPerBlock!!.get() else 120
    const val MIN_BLOCK_PIXELS = 4
    const val VIEW_DISTANCE_CHUNKS = 6
    const val VIEW_DISTANCE_BLOCKS = VIEW_DISTANCE_CHUNKS * 16
    const val MAX_BRIGHTNESS = 500
}
