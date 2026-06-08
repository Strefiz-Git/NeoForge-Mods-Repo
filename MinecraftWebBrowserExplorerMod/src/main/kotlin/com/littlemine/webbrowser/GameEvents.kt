package com.littlemine.webbrowser

import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.ClientBridge
import com.littlemine.webbrowser.item.MonitorCommandItem
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
object GameEvents {
    private val LOGGER: org.slf4j.Logger = com.mojang.logging.LogUtils.getLogger()

    @SubscribeEvent
    fun onLeftClickBlock(event: PlayerInteractEvent.LeftClickBlock) {
        val player = event.entity
        val item = player.mainHandItem
        if (item.item is MonitorCommandItem) {
            val be = event.level.getBlockEntity(event.pos)
            if (be is MonitorBlockEntity && be.isActive) {
                LOGGER.debug("[GameEvents] left-click on monitor {} with command", event.pos)
                event.setCanceled(true)
                if (event.level.isClientSide) {
                    ClientBridge.onMonitorCommandUse?.invoke(be)
                }
            }
        }
    }

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val player = event.entity
        val be = event.level.getBlockEntity(event.pos)
        if (be is MonitorBlockEntity && be.isActive) {
            val item = player.mainHandItem
            if (item.item is MonitorCommandItem) {
                LOGGER.debug("[GameEvents] right-click on monitor {} with command", event.pos)
                event.setCanceled(true)
                if (event.level.isClientSide) {
                    if (player.isShiftKeyDown) {
                        ClientBridge.onMonitorCommandShiftUse?.invoke(be)
                    } else {
                        ClientBridge.onMonitorCommandUse?.invoke(be)
                    }
                }
            } else {
                LOGGER.debug("[GameEvents] right-click on monitor {} WITHOUT command - blocked", event.pos)
                event.setCanceled(true)
                if (event.level.isClientSide) {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.webbrowser.need_command"),
                        true
                    )
                }
            }
        }
    }
}
