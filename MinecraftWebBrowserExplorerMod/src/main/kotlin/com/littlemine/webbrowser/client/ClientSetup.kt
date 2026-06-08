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
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
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
    fun onChunkLoad(event: ChunkEvent.Load) {
        val chunk = event.chunk
        if (chunk !is net.minecraft.world.level.chunk.LevelChunk) return
        for (be in chunk.blockEntities.values) {
            if (be is MonitorBlockEntity && be.isMaster && be.isActive) {
                val existing = ClientBrowserCache.get(be.blockPos)
                if (existing == null) {
                    LOGGER.debug("[ClientGameEvents] chunk loaded with active monitor at {} - browser will be created on interaction", be.blockPos)
                }
            }
        }
    }

    @SubscribeEvent
    fun onChunkUnload(event: ChunkEvent.Unload) {
        // Don't destroy browsers on chunk unload - they survive until level unload
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
        if (InputHandler.isInputActive()) {
            // Keep mouse grabbed while input is active
            if (!mc.mouseHandler.isMouseGrabbed) {
                mc.mouseHandler.grabMouse()
            }
            if (mc.screen != null) {
                LOGGER.trace("[ClientGameEvents] closing unexpected screen while input active")
                mc.setScreen(null)
            }
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

        // ESC = deactivate, then let the event through so the game opens pause menu
        if (event.key == 256) {
            LOGGER.debug("[ClientGameEvents] ESC pressed - deactivating input")
            InputHandler.deactivate()
            return
        }

        // Forward key to CEF
        when (event.action) {
            1, 2 -> InputHandler.onKeyPress(event.key, event.scanCode, event.modifiers)
            0 -> InputHandler.onKeyRelease(event.key, event.scanCode, event.modifiers)
        }

        // Reset key mapping to prevent game from processing this key
        val key = com.mojang.blaze3d.platform.InputConstants.getKey(event.key, event.scanCode)
        net.minecraft.client.KeyMapping.set(key, false)
    }

    @SubscribeEvent
    fun onScroll(event: InputEvent.MouseScrollingEvent) {
        if (InputHandler.isInputActive()) {
            InputHandler.onScroll(event.scrollDeltaX, event.scrollDeltaY)
            event.setCanceled(true)
        }
    }

    @SubscribeEvent
    fun onLeftClickBlock(event: PlayerInteractEvent.LeftClickBlock) {
        if (InputHandler.isInputActive()) {
            event.setCanceled(true)
        }
    }

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (InputHandler.isInputActive()) {
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
