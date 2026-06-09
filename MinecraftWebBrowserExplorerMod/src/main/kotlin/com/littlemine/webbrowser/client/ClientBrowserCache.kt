package com.littlemine.webbrowser.client

import com.cinemamod.mcef.MCEF
import com.cinemamod.mcef.MCEFBrowser
import com.littlemine.webbrowser.client.audio.MCEFAudioEngine
import com.littlemine.webbrowser.client.renderer.MonitorBlockEntityRenderer
import com.littlemine.webbrowser.config.MonitorConfig
import net.minecraft.core.BlockPos

object ClientBrowserCache {
    private val browsers = mutableMapOf<BlockPos, MCEFBrowser>()
    private val LOGGER: org.slf4j.Logger = com.mojang.logging.LogUtils.getLogger()

    private val lifecycleLogging: Boolean get() = MonitorConfig.logBrowserLifecycle?.get() ?: true

    fun get(pos: BlockPos): MCEFBrowser? {
        val b = browsers[pos]
        LOGGER.trace("[BrowserCache] get({}) = {}", pos, if (b != null) "found" else "null")
        return b
    }

    fun getAllPositions(): List<BlockPos> = browsers.keys.toList()

    fun put(pos: BlockPos, browser: MCEFBrowser) {
        val old = browsers.put(pos, browser)
        if (old != null && old !== browser) {
            if (lifecycleLogging) LOGGER.info("[BrowserCache] put({}) - closing old browser, url={}", pos, old.url)
            MCEFAudioEngine.unregister(pos)
            old.close()
        } else {
            if (lifecycleLogging) LOGGER.info("[BrowserCache] put({}) - new browser, url={}", pos, browser.url)
        }
        MCEFAudioEngine.register(pos, browser)
    }

    fun remove(pos: BlockPos) {
        val b = browsers.remove(pos)
        MCEFAudioEngine.unregister(pos)
        if (lifecycleLogging) LOGGER.info("[BrowserCache] remove({}) = {}", pos, if (b != null) "closed url=" + b.url else "absent")
        b?.close()
    }

    fun recreate(pos: BlockPos, width: Int, height: Int, url: String): MCEFBrowser? {
        MCEFAudioEngine.unregister(pos)
        browsers.remove(pos)?.close()
        MonitorBlockEntityRenderer.clearRenderTypeCache()
        val b = MCEF.createBrowser(url, false, width, height)
        if (b != null) {
            browsers[pos] = b
            MCEFAudioEngine.register(pos, b)
            LOGGER.info("[BrowserCache] recreate({}) {}x{} url={} textureID={}", pos, width, height, url, b.renderer.textureID)
        } else {
            LOGGER.error("[BrowserCache] recreate({}) - MCEF.createBrowser returned null!", pos)
        }
        return b
    }

    fun clear() {
        val keys = browsers.keys.toList()
        LOGGER.info("[BrowserCache] clear ({} browsers)", browsers.size)
        browsers.values.forEach { it.close() }
        browsers.clear()
        for (pos in keys) MCEFAudioEngine.unregister(pos)
    }
}
