package com.littlemine.webbrowser.client

import com.cinemamod.mcef.MCEF
import com.littlemine.webbrowser.ClientBridge
import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.client.input.InputHandler
import com.littlemine.webbrowser.client.renderer.MonitorBlockEntityRenderer
import com.littlemine.webbrowser.client.renderer.MonitorBlockEntityRenderer.Companion.clearRenderTypeCache
import com.littlemine.webbrowser.registry.BlockEntityRegistry
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.level.LevelEvent

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, value = [Dist.CLIENT])
object ClientGameEvents {
    private val LOGGER: org.slf4j.Logger = com.mojang.logging.LogUtils.getLogger()

    init {
        ClientBridge.onMonitorFormed = { be ->
            val master = be.master()
            val shape = master.shape
            if (shape != null) {
                val targetW = shape.width * BrowserConstants.BLOCK_PIXEL_WIDTH
                val targetH = shape.height * BrowserConstants.BLOCK_PIXEL_HEIGHT
                LOGGER.debug("[ClientGameEvents] onMonitorFormed at {} shape={}x{} -> browser target {}x{}",
                    master.blockPos, shape.width, shape.height, targetW, targetH)

                val existing = ClientBrowserCache.get(master.blockPos)
                if (existing != null) {
                    LOGGER.debug("[ClientGameEvents] browser already exists at {}, closing and recreating", master.blockPos)
                    ClientBrowserCache.remove(master.blockPos)
                }

                LOGGER.debug("[ClientGameEvents] creating browser {}x{} url={}", targetW, targetH, master.currentUrl)
                val b = MCEF.createBrowser(master.currentUrl, false, targetW, targetH)
                if (b != null) {
                    ClientBrowserCache.put(master.blockPos, b)
                    LOGGER.debug("[ClientGameEvents] browser created, textureID={}", b.renderer.textureID)
                } else {
                    LOGGER.error("[ClientGameEvents] MCEF.createBrowser returned null!")
                }
            } else {
                LOGGER.warn("[ClientGameEvents] onMonitorFormed - no shape for master {}", master.blockPos)
            }
        }

        ClientBridge.onMonitorBreak = { be ->
            val master = be.master()
            LOGGER.debug("[ClientGameEvents] onMonitorBreak at {} master={}", be.blockPos, master.blockPos)
            ClientBrowserCache.remove(master.blockPos)
            clearRenderTypeCache()
        }

        ClientBridge.onMonitorCommandShiftUse = { be ->
            LOGGER.debug("[ClientGameEvents] onMonitorCommandShiftUse at {}", be.blockPos)
            InputHandler.openUrlScreen(be)
        }
    }

    @SubscribeEvent
    fun onChunkUnload(event: ChunkEvent.Unload) {
        val chunk = event.chunk
        if (chunk !is net.minecraft.world.level.chunk.LevelChunk) return
        LOGGER.trace("[ClientGameEvents] chunk unload at {}", chunk.pos)
        for (be in chunk.blockEntities.values) {
            if (be is MonitorBlockEntity && be.isMaster) {
                ClientBrowserCache.remove(be.blockPos)
            }
        }
    }

    @SubscribeEvent
    fun onLevelUnload(event: LevelEvent.Unload) {
        LOGGER.debug("[ClientGameEvents] level unload - clearing all")
        ClientBrowserCache.clear()
        clearRenderTypeCache()
        InputHandler.deactivate()
    }

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Pre) {
        InputHandler.onClientTick()

        val mc = Minecraft.getInstance()
        if (InputHandler.isInputActive() && mc.screen != null) {
            LOGGER.trace("[ClientGameEvents] closing unexpected screen while input active")
            mc.setScreen(null)
        }
    }

    @SubscribeEvent
    fun onMouseClick(event: InputEvent.MouseButton.Pre) {
        if (InputHandler.isInputActive()) {
            LOGGER.trace("[ClientGameEvents] mouse click {} action={}", event.button, event.action)
            InputHandler.onMouseClick(event.button, event.action, event.modifiers)
            event.setCanceled(true)
        }
    }

    @SubscribeEvent
    fun onKeyPress(event: InputEvent.Key) {
        if (!InputHandler.isInputActive()) return

        if (event.key == 256) {
            LOGGER.debug("[ClientGameEvents] ESC pressed - deactivating input")
            InputHandler.deactivate()
            return
        }

        when (event.action) {
            1, 2 -> InputHandler.onKeyPress(event.key, event.scanCode, event.modifiers)
            0 -> InputHandler.onKeyRelease(event.key, event.scanCode, event.modifiers)
        }
    }

    @SubscribeEvent
    fun onScroll(event: InputEvent.MouseScrollingEvent) {
        if (InputHandler.isInputActive()) {
            InputHandler.onScroll(event.scrollDeltaX, event.scrollDeltaY)
            event.setCanceled(true)
        }
    }
}

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object ClientSetup {

    @SubscribeEvent
    fun onRegisterRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(
            BlockEntityRegistry.MONITOR_BLOCK_ENTITY.get(),
            ::MonitorBlockEntityRenderer
        )
    }
}
