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

    init {
        ClientBridge.onMonitorFormed = { be ->
            val master = be.master()
            val shape = master.shape
            if (shape != null) {
                val browser = ClientBrowserCache.get(master.blockPos)
                if (browser == null) {
                    val sizeW = shape.width * 120
                    val sizeH = shape.height * 120
                    val b = MCEF.createBrowser(master.currentUrl, false, sizeW, sizeH)
                    if (b != null) {
                        ClientBrowserCache.put(master.blockPos, b)
                    }
                }
            }
        }
        ClientBridge.onMonitorUse = { be, player, _ ->
            if (player.isShiftKeyDown) {
                InputHandler.openUrlScreen(be.master())
            } else {
                InputHandler.activate(be.master())
            }
        }
    }

    @SubscribeEvent
    fun onChunkUnload(event: ChunkEvent.Unload) {
        val chunk = event.chunk
        if (chunk !is net.minecraft.world.level.chunk.LevelChunk) return
        for (be in chunk.blockEntities.values) {
            if (be is MonitorBlockEntity && be.isMaster) {
                ClientBrowserCache.remove(be.blockPos)
            }
        }
    }

    @SubscribeEvent
    fun onLevelUnload(event: LevelEvent.Unload) {
        ClientBrowserCache.clear()
        clearRenderTypeCache()
        InputHandler.deactivate()
    }

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Pre) {
        InputHandler.onClientTick()

        val mc = Minecraft.getInstance()
        if (InputHandler.isInputActive() && mc.screen != null) {
            mc.setScreen(null)
        }
    }

    @SubscribeEvent
    fun onMouseClick(event: InputEvent.MouseButton.Pre) {
        if (InputHandler.isInputActive()) {
            InputHandler.onMouseClick(event.button, event.action, event.modifiers)
            event.setCanceled(true)
        }
    }

    @SubscribeEvent
    fun onKeyPress(event: InputEvent.Key) {
        if (InputHandler.isInputActive()) {
            if (event.key == 256) {
                InputHandler.deactivate()
                return
            }
            InputHandler.onKeyPress(event.key, event.scanCode, event.modifiers)
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
