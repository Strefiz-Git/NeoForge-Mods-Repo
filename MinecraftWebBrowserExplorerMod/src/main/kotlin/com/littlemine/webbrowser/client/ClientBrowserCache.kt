package com.littlemine.webbrowser.client

import com.cinemamod.mcef.MCEFBrowser
import net.minecraft.core.BlockPos

object ClientBrowserCache {
    private val browsers = mutableMapOf<BlockPos, MCEFBrowser>()
    private val LOGGER: org.slf4j.Logger = com.mojang.logging.LogUtils.getLogger()

    fun get(pos: BlockPos): MCEFBrowser? {
        val b = browsers[pos]
        LOGGER.trace("[BrowserCache] get({}) = {}", pos, if (b != null) "found" else "null")
        return b
    }

    fun put(pos: BlockPos, browser: MCEFBrowser) {
        val old = browsers.put(pos, browser)
        if (old != null && old !== browser) {
            LOGGER.debug("[BrowserCache] put({}) - closing old browser, url={}", pos, old.url)
            old.close()
        } else {
            LOGGER.debug("[BrowserCache] put({}) - new browser, url={}", pos, browser.url)
        }
    }

    fun remove(pos: BlockPos) {
        val b = browsers.remove(pos)
        LOGGER.debug("[BrowserCache] remove({}) = {}", pos, if (b != null) "closed url=" + b.url else "absent")
        b?.close()
    }

    fun clear() {
        LOGGER.debug("[BrowserCache] clear ({} browsers)", browsers.size)
        browsers.values.forEach { it.close() }
        browsers.clear()
    }
}
