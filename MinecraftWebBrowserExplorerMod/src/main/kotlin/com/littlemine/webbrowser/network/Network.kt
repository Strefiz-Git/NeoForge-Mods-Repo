package com.littlemine.webbrowser.network

import com.littlemine.webbrowser.MonitorMod
import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.client.BrowserConstants
import com.littlemine.webbrowser.item.MonitorCommandItem
import net.minecraft.core.BlockPos
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.handling.IPayloadHandler

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object Network {

    @SubscribeEvent
    fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(MonitorMod.MOD_ID)

        registrar.playToServer(
            MonitorUpdatePacket.TYPE,
            MonitorUpdatePacket.STREAM_CODEC,
            MonitorUpdateHandler
        )

        registrar.playToServer(
            CommandSyncPacket.TYPE,
            CommandSyncPacket.STREAM_CODEC,
            CommandSyncHandler
        )

        registrar.playToServer(
            MonitorResolutionPacket.TYPE,
            MonitorResolutionPacket.STREAM_CODEC,
            MonitorResolutionHandler
        )
    }
}

object MonitorResolutionHandler : IPayloadHandler<MonitorResolutionPacket> {
    override fun handle(packet: MonitorResolutionPacket, ctx: IPayloadContext) {
        val pos = BlockPos(packet.posX, packet.posY, packet.posZ)
        val level = ctx.player().level()
        val be = level.getBlockEntity(pos)
        if (be is MonitorBlockEntity) {
            val ppb = packet.pixelsPerBlock.coerceIn(BrowserConstants.MIN_BLOCK_PIXELS, BrowserConstants.MAX_BLOCK_PIXELS)
            be.pixelsPerBlock = ppb
            be.setChanged()
            level.sendBlockUpdated(pos, be.blockState, be.blockState, 3)
        }
    }
}

object CommandSyncHandler : IPayloadHandler<CommandSyncPacket> {
    override fun handle(packet: CommandSyncPacket, ctx: IPayloadContext) {
        val player = ctx.player()
        val stack = player.mainHandItem
        if (stack.item !is MonitorCommandItem) return

        val pos = BlockPos(packet.posX, packet.posY, packet.posZ)
        val level = player.level()
        val be = level.getBlockEntity(pos)
        if (be !is MonitorBlockEntity) return
        if (!be.isActive) return

        if (packet.connect) {
            MonitorCommandItem.connectTo(stack, be.masterPos)
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.webbrowser.connected"),
                true
            )
        } else {
            MonitorCommandItem.disconnect(stack)
        }
    }
}

object MonitorUpdateHandler : IPayloadHandler<MonitorUpdatePacket> {
    override fun handle(packet: MonitorUpdatePacket, ctx: IPayloadContext) {
        val pos = net.minecraft.core.BlockPos(packet.posX, packet.posY, packet.posZ)
        val level = ctx.player().level()
        val be = level.getBlockEntity(pos)
        if (be is com.littlemine.webbrowser.block.MonitorBlockEntity) {
            be.currentUrl = packet.url
            be.setChanged()
            level.sendBlockUpdated(pos, be.blockState, be.blockState, 3)
        }
    }
}


