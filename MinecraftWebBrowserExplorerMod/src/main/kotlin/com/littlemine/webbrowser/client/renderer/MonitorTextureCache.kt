package com.littlemine.webbrowser.client.renderer

import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.client.ClientBrowserCache

object MonitorTextureCache {

    fun getTextureId(entity: MonitorBlockEntity): Int {
        val master = entity.master()
        val browser = ClientBrowserCache.get(master.blockPos) ?: return -1
        return browser.renderer.textureID
    }
}
