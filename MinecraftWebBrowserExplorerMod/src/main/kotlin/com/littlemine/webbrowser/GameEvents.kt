package com.littlemine.webbrowser

import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.item.MonitorCommandItem
import com.littlemine.webbrowser.network.CommandSyncPacket
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.network.PacketDistributor

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
object GameEvents {
    private val LOGGER: org.slf4j.Logger = com.mojang.logging.LogUtils.getLogger()

    @SubscribeEvent
    fun onLeftClickBlock(event: PlayerInteractEvent.LeftClickBlock) {
        val player = event.entity
        val item = player.mainHandItem
        if (item.item !is MonitorCommandItem) return

        val be = event.level.getBlockEntity(event.pos)
        if (be !is MonitorBlockEntity || !be.isActive) return

        LOGGER.info("[GameEvents] LeftClickBlock on active monitor at {} shift={}", event.pos, player.isShiftKeyDown)
        event.setCanceled(true)

        // Shift+left-click → connect command to this monitor
        if (player.isShiftKeyDown && event.level.isClientSide) {
            LOGGER.info("[GameEvents] Sending CommandSyncPacket for {}", event.pos)
            PacketDistributor.sendToServer(CommandSyncPacket(
                event.pos.x, event.pos.y, event.pos.z, true
            ))
        }
    }

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val player = event.entity
        val item = player.mainHandItem
        val be = event.level.getBlockEntity(event.pos)
        if (be !is MonitorBlockEntity || !be.isActive) {
            if (be is MonitorBlockEntity) {
                LOGGER.info("[GameEvents] RightClickBlock at {} - monitor not active", event.pos)
            }
            return
        }

        LOGGER.info("[GameEvents] RightClickBlock on active monitor at {} item={}", event.pos, item.item)
        event.setCanceled(true)

        if (item.item is MonitorCommandItem && MonitorCommandItem.isConnected(item)) {
            LOGGER.info("[GameEvents] RightClickBlock - client will open browser GUI")
        } else if (event.level.isClientSide) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.webbrowser.need_command"),
                true
            )
        }
    }
}
