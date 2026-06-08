package com.littlemine.webbrowser

import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.client.BrowserConstants
import com.littlemine.webbrowser.client.ClientBrowserCache
import com.littlemine.webbrowser.client.input.InputHandler
import com.littlemine.webbrowser.item.MonitorCommandItem
import com.littlemine.webbrowser.network.CommandSyncPacket
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
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

        event.setCanceled(true)

        if (player.isShiftKeyDown) {
            // Shift+left-click → connect command to this monitor
            if (event.level.isClientSide) {
                PacketDistributor.sendToServer(CommandSyncPacket(
                    event.pos.x, event.pos.y, event.pos.z, true
                ))
            }
        } else {
            // Left-click with command → try to activate input
            if (!event.level.isClientSide) return
            handleActivateInput(player, be, event.level)
        }
    }

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val player = event.entity
        val item = player.mainHandItem
        val be = event.level.getBlockEntity(event.pos)
        if (be !is MonitorBlockEntity || !be.isActive) return

        event.setCanceled(true)

        if (item.item is MonitorCommandItem) {
            if (!event.level.isClientSide) return

            if (player.isShiftKeyDown) {
                // Shift+right-click → URL screen (only if connected to this monitor)
                val connectedPos = MonitorCommandItem.getConnectedPos(item)
                if (connectedPos != null && connectedPos == be.master().blockPos) {
                    ClientBridge.onMonitorCommandShiftUse?.invoke(be)
                } else {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.webbrowser.not_looking"),
                        true
                    )
                }
            } else {
                // Right-click with command → activate input
                handleActivateInput(player, be, event.level)
            }
        } else {
            // No command → blocked
            LOGGER.debug("[GameEvents] right-click on monitor {} WITHOUT command - blocked", event.pos)
            if (event.level.isClientSide) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.webbrowser.need_command"),
                    true
                )
            }
        }
    }

    private fun handleActivateInput(
        player: net.minecraft.world.entity.player.Player,
        be: MonitorBlockEntity,
        level: Level
    ) {
        val stack = player.mainHandItem
        if (stack.item !is MonitorCommandItem) return

        val connectedPos = MonitorCommandItem.getConnectedPos(stack) ?: run {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.webbrowser.not_looking"),
                true
            )
            return
        }

        val master = be.master()
        if (connectedPos != master.blockPos) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.webbrowser.not_looking"),
                true
            )
            return
        }

        // Check if browser still exists (monitor was not broken and reformed)
        if (level.isClientSide && ClientBrowserCache.get(master.blockPos) == null) {
            MonitorCommandItem.disconnect(stack)
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.webbrowser.disconnected"),
                true
            )
            return
        }

        // Check distance: 6 chunks (96 blocks) from nearest monitor edge
        if (!isPlayerWithinRange(player, master)) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.webbrowser.out_of_range"),
                true
            )
            return
        }

        // Check if looking at the screen
        val hit = InputHandler.raycastForMonitorBlock()
        if (hit !is BlockHitResult || hit.type == HitResult.Type.MISS) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.webbrowser.not_looking"),
                true
            )
            return
        }
        val hitBe = player.level().getBlockEntity(hit.blockPos)
        if (hitBe !is MonitorBlockEntity || !hitBe.isActive) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.webbrowser.not_looking"),
                true
            )
            return
        }

        // Check occupied
        if (InputHandler.isInputActive()) {
            // Already occupied by this client; allow switching
            if (InputHandler.activeMonitor?.master()?.blockPos == master.blockPos) return
            // Otherwise deactivate current
            InputHandler.deactivate()
        }

        InputHandler.syncToMonitor(master)
    }

    private fun isPlayerWithinRange(
        player: net.minecraft.world.entity.player.Player,
        master: MonitorBlockEntity
    ): Boolean {
        val shape = master.shape ?: return false
        val topLeft = shape.topLeft
        val rightDir = shape.rightDir
        val downDir = shape.downDir
        val w = shape.width
        val h = shape.height

        // Bounding box of the monitor
        val p0 = topLeft
        val p1 = topLeft.offset(
            rightDir.stepX * (w - 1) + downDir.stepX * (h - 1),
            rightDir.stepY * (w - 1) + downDir.stepY * (h - 1),
            rightDir.stepZ * (w - 1) + downDir.stepZ * (h - 1)
        )
        val minX = minOf(p0.x, p1.x).toDouble()
        val minY = minOf(p0.y, p1.y).toDouble()
        val minZ = minOf(p0.z, p1.z).toDouble()
        val maxX = maxOf(p0.x, p1.x).toDouble() + 1.0
        val maxY = maxOf(p0.y, p1.y).toDouble() + 1.0
        val maxZ = maxOf(p0.z, p1.z).toDouble() + 1.0

        val aabb = AABB(minX, minY, minZ, maxX, maxY, maxZ)
        val playerPos = player.position()
        // Clamp player position to the AABB to find nearest point
        val cx = playerPos.x.coerceIn(aabb.minX, aabb.maxX)
        val cy = playerPos.y.coerceIn(aabb.minY, aabb.maxY)
        val cz = playerPos.z.coerceIn(aabb.minZ, aabb.maxZ)
        val dist = Math.sqrt(
            (playerPos.x - cx) * (playerPos.x - cx) +
            (playerPos.y - cy) * (playerPos.y - cy) +
            (playerPos.z - cz) * (playerPos.z - cz)
        )
        return dist <= BrowserConstants.VIEW_DISTANCE_BLOCKS
    }
}
