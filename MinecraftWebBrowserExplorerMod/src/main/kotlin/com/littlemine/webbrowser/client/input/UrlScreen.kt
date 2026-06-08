package com.littlemine.webbrowser.client.input

import com.cinemamod.mcef.MCEF
import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.client.ClientBrowserCache
import com.littlemine.webbrowser.network.MonitorUpdatePacket
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

class UrlScreen(private val monitor: MonitorBlockEntity) : Screen(Component.translatable("screen.webbrowser.url")) {

    private lateinit var urlField: EditBox

    override fun init() {
        urlField = EditBox(font, width / 2 - 100, height / 2 - 10, 200, 20, Component.literal("URL"))
        urlField.setMaxLength(2048)
        urlField.value = monitor.currentUrl
        urlField.isFocused = true
        addRenderableWidget(urlField)
        addRenderableWidget(
            net.minecraft.client.gui.components.Button.Builder(
                Component.literal("Go"),
                { onGo() }
            ).bounds(width / 2 - 100, height / 2 + 20, 200, 20).build()
        )
    }

    private fun onGo() {
        val url = urlField.value
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            monitor.currentUrl = "https://$url"
        } else {
            monitor.currentUrl = url
        }
        var browser = ClientBrowserCache.get(monitor.blockPos)
        if (browser == null) {
            val shape = monitor.shape
            if (shape != null) {
                val b = MCEF.createBrowser(monitor.currentUrl, false, shape.width * 120, shape.height * 120)
                if (b != null) {
                    ClientBrowserCache.put(monitor.blockPos, b)
                    browser = b
                }
            }
        }
        browser?.loadURL(monitor.currentUrl)
        PacketDistributor.sendToServer(MonitorUpdatePacket(
            monitor.blockPos.x, monitor.blockPos.y, monitor.blockPos.z, monitor.currentUrl
        ))
        onClose()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 257 || keyCode == 335) {
            onGo()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false
}
