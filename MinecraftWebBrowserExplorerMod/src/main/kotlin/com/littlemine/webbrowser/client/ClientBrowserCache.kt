package com.littlemine.webbrowser.client

import com.cinemamod.mcef.MCEFBrowser
import net.minecraft.core.BlockPos

object ClientBrowserCache {
    private val browsers = mutableMapOf<BlockPos, MCEFBrowser>()
    private val LOGGER: org.slf4j.Logger = com.mojang.logging.LogUtils.getLogger()

    fun get(pos: BlockPos): MCEFBrowser? {
        val b = browsers[pos]
        LOGGER.debug("[BrowserCache] get({}) = {}", pos, if (b != null) "found" else "null")
        return b
    }

    fun put(pos: BlockPos, browser: MCEFBrowser) {
        LOGGER.debug("[BrowserCache] put({}, {})", pos, browser.url)
        browsers[pos] = browser
    }

    fun remove(pos: BlockPos) {
        val b = browsers.remove(pos)
        LOGGER.debug("[BrowserCache] remove({}) = {}", pos, if (b != null) "closed" else "absent")
        b?.close()
    }

    fun clear() {
        LOGGER.debug("[BrowserCache] clear ({} browsers)", browsers.size)
        browsers.values.forEach { it.close() }
        browsers.clear()
    }
}
