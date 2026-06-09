package com.littlemine.webbrowser.client

import com.cinemamod.mcef.MCEF
import com.cinemamod.mcef.MCEFBrowser
import com.littlemine.webbrowser.client.renderer.MonitorBlockEntityRenderer
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
            LOGGER.info("[BrowserCache] put({}) - closing old browser, url={}", pos, old.url)
            old.close()
        } else {
            LOGGER.info("[BrowserCache] put({}) - new browser, url={}", pos, browser.url)
        }
    }

    fun remove(pos: BlockPos) {
        val b = browsers.remove(pos)
        LOGGER.info("[BrowserCache] remove({}) = {}", pos, if (b != null) "closed url=" + b.url else "absent")
        b?.close()
    }

    fun recreate(pos: BlockPos, width: Int, height: Int, url: String): MCEFBrowser? {
        browsers.remove(pos)?.close()
        MonitorBlockEntityRenderer.clearRenderTypeCache()
        val b = MCEF.createBrowser(url, false, width, height)
        if (b != null) {
            browsers[pos] = b
            LOGGER.info("[BrowserCache] recreate({}) {}x{} url={} textureID={}", pos, width, height, url, b.renderer.textureID)
        } else {
            LOGGER.error("[BrowserCache] recreate({}) - MCEF.createBrowser returned null!", pos)
        }
        return b
    }

    fun clear() {
        LOGGER.info("[BrowserCache] clear ({} browsers)", browsers.size)
        browsers.values.forEach { it.close() }
        browsers.clear()
    }
}
