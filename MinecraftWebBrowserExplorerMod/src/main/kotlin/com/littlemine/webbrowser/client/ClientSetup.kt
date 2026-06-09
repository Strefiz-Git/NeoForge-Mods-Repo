package com.littlemine.webbrowser.client

import com.cinemamod.mcef.MCEF
import com.littlemine.webbrowser.ClientBridge
import com.littlemine.webbrowser.client.audio.MCEFAudioEngine
import com.littlemine.webbrowser.config.MonitorConfig
import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.client.gui.BrowserScreen
import com.littlemine.webbrowser.client.input.InputHandler
import com.littlemine.webbrowser.client.renderer.MonitorBlockEntityRenderer
import com.littlemine.webbrowser.client.renderer.MonitorBlockEntityRenderer.Companion.clearRenderTypeCache
import com.littlemine.webbrowser.item.MonitorCommandItem
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
                val ppb = master.pixelsPerBlock
                val targetW = shape.width * ppb
                val targetH = shape.height * ppb
                LOGGER.info("[Monitor] monitor formed at {} ppb={} shape={}x{} -> browser {}x{}",
                    master.blockPos, ppb, shape.width, shape.height, targetW, targetH)

                val existing = ClientBrowserCache.get(master.blockPos)
                if (existing != null) {
                    LOGGER.info("[Monitor] browser already exists at {}, closing and recreating", master.blockPos)
                    ClientBrowserCache.remove(master.blockPos)
                }

                LOGGER.info("[Monitor] creating browser {}x{} url={}", targetW, targetH, master.currentUrl)
                val b = MCEF.createBrowser(master.currentUrl, false, targetW, targetH)
                if (b != null) {
                    ClientBrowserCache.put(master.blockPos, b)
                    LOGGER.info("[Monitor] browser created, textureID={}", b.renderer.textureID)
                } else {
                    LOGGER.error("[Monitor] MCEF.createBrowser returned null!")
                }
            } else {
                LOGGER.warn("[Monitor] onMonitorFormed - no shape for master {}", master.blockPos)
            }
        }

        ClientBridge.onMonitorBreak = { be ->
            val master = be.master()
            LOGGER.info("[Monitor] onMonitorBreak at {} master={}", be.blockPos, master.blockPos)
            ClientBrowserCache.remove(master.blockPos)
            clearRenderTypeCache()
        }

        ClientBridge.onMonitorCommandRightClick = { be ->
            val master = be.master()
            LOGGER.info("[Monitor] onMonitorCommandRightClick at {} master={}", be.blockPos, master.blockPos)
            Minecraft.getInstance().setScreen(BrowserScreen(master))
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
                    if (be.shape == null) {
                        be.recalculateClientShape()
                    }
                    if (be.shape != null) {
                        val shape = be.shape!!
                        val targetW = shape.width * be.pixelsPerBlock
                        val targetH = shape.height * be.pixelsPerBlock
                        LOGGER.info("[Monitor] chunk loaded active monitor at {} -> creating browser {}x{} url={}",
                            be.blockPos, targetW, targetH, be.currentUrl)
                        val b = MCEF.createBrowser(be.currentUrl, false, targetW, targetH)
                        if (b != null) {
                            ClientBrowserCache.put(be.blockPos, b)
                            LOGGER.info("[Monitor] browser created for chunk-loaded monitor, textureID={}", b.renderer.textureID)
                        } else {
                            LOGGER.error("[Monitor] MCEF.createBrowser returned null for chunk-loaded monitor at {}!", be.blockPos)
                        }
                    } else {
                        LOGGER.warn("[Monitor] chunk loaded monitor at {} has null shape (neighbors not loaded yet?)", be.blockPos)
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onChunkUnload(event: ChunkEvent.Unload) {
    }

    @SubscribeEvent
    fun onLevelUnload(event: LevelEvent.Unload) {
        LOGGER.info("[Monitor] level unload - clearing all")
        ClientBrowserCache.clear()
        clearRenderTypeCache()
        InputHandler.setActiveMonitor(null)
    }

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Pre) {
        MCEFAudioEngine.tick()
        MonitorBlockEntityRenderer.resetRenderCount()

        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) return
        if (mc.level!!.gameTime % 20 != 0L) return

        val playerPos = mc.player!!.blockPosition()
        val distSq = (12 * 16).toDouble().let { it * it }
        for (pos in ClientBrowserCache.getAllPositions()) {
            if (pos.distSqr(playerPos) > distSq) {
                LOGGER.info("[Monitor] browser {} too far from player, removing", pos)
                ClientBrowserCache.remove(pos)
            }
        }
    }

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val player = event.entity
        val item = player.mainHandItem
        val be = event.level.getBlockEntity(event.pos)

        if (item.item is MonitorCommandItem && MonitorCommandItem.isConnected(item)) {
            LOGGER.info("[Monitor] RightClickBlock with connected command at {}", event.pos)
            event.setCanceled(true)
            val connectedPos = MonitorCommandItem.getConnectedPos(item) ?: return
            val masterBe = event.level.getBlockEntity(connectedPos)
            if (masterBe is MonitorBlockEntity && masterBe.isActive) {
                val master = masterBe.master()
                if (InputHandler.isOccupied(master.blockPos)) {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.webbrowser.occupied"),
                        true
                    )
                    return
                }
                InputHandler.setActiveMonitor(master.blockPos)
                Minecraft.getInstance().setScreen(BrowserScreen(master))
            } else {
                LOGGER.info("[Monitor] RightClickBlock - connected pos {} not active or not monitor", connectedPos)
            }
            return
        }

        if (be is MonitorBlockEntity && be.isActive && item.item !is MonitorCommandItem) {
            LOGGER.info("[Monitor] RightClickBlock on active monitor without command at {}", event.pos)
            event.setCanceled(true)
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.webbrowser.need_command"),
                true
            )
        }
    }

    @SubscribeEvent
    fun onRightClickItem(event: PlayerInteractEvent.RightClickItem) {
        val player = event.entity
        val item = player.mainHandItem
        if (item.item !is MonitorCommandItem) return
        if (!MonitorCommandItem.isConnected(item)) return

        LOGGER.info("[Monitor] RightClickItem with connected command")
        event.setCanceled(true)

        val connectedPos = MonitorCommandItem.getConnectedPos(item) ?: return
        val be = event.level.getBlockEntity(connectedPos)
        if (be !is MonitorBlockEntity || !be.isActive) {
            LOGGER.info("[Monitor] RightClickItem - connected {} not active", connectedPos)
            return
        }

        val master = be.master()
        if (InputHandler.isOccupied(master.blockPos)) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.webbrowser.occupied"),
                true
            )
            return
        }

        InputHandler.setActiveMonitor(master.blockPos)
        Minecraft.getInstance().setScreen(BrowserScreen(master))
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
