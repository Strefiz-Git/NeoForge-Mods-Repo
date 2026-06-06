package com.littlemine.webbrowser.client

import com.cinemamod.mcef.MCEFBrowser
import net.minecraft.core.BlockPos

object ClientBrowserCache {
    private val browsers = mutableMapOf<BlockPos, MCEFBrowser>()

    fun get(pos: BlockPos): MCEFBrowser? = browsers[pos]

    fun put(pos: BlockPos, browser: MCEFBrowser) {
        browsers[pos] = browser
    }

    fun remove(pos: BlockPos) {
        browsers.remove(pos)?.close()
    }

    fun clear() {
        browsers.values.forEach { it.close() }
        browsers.clear()
    }
}
