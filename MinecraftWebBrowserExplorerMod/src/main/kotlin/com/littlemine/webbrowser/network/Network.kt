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

private const val MAX_INTERACT_DIST_SQR = 96.0 * 96.0

private fun checkDistance(pos: BlockPos, ctx: IPayloadContext): Boolean {
    val player = ctx.player()
    return pos.distToCenterSqr(player.x, player.y, player.z) <= MAX_INTERACT_DIST_SQR
}

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

        registrar.playToServer(
            MonitorAudioPacket.TYPE,
            MonitorAudioPacket.STREAM_CODEC,
            MonitorAudioHandler
        )

        registrar.playToServer(
            MonitorBrightnessPacket.TYPE,
            MonitorBrightnessPacket.STREAM_CODEC,
            MonitorBrightnessHandler
        )
    }
}

object MonitorAudioHandler : IPayloadHandler<MonitorAudioPacket> {
    override fun handle(packet: MonitorAudioPacket, ctx: IPayloadContext) {
        val pos = BlockPos(packet.posX, packet.posY, packet.posZ)
        if (!checkDistance(pos, ctx)) return
        val level = ctx.player().level()
        val be = level.getBlockEntity(pos)
        if (be is MonitorBlockEntity) {
            be.audioMode = packet.audioMode.coerceIn(0, 1)
            be.volume = packet.volume.coerceIn(0, 300)
            be.setChanged()
            level.sendBlockUpdated(pos, be.blockState, be.blockState, 3)
        }
    }
}

object MonitorBrightnessHandler : IPayloadHandler<MonitorBrightnessPacket> {
    override fun handle(packet: MonitorBrightnessPacket, ctx: IPayloadContext) {
        val pos = BlockPos(packet.posX, packet.posY, packet.posZ)
        if (!checkDistance(pos, ctx)) return
        val level = ctx.player().level()
        val be = level.getBlockEntity(pos)
        if (be is MonitorBlockEntity) {
            be.brightness = packet.brightness.coerceIn(0, BrowserConstants.MAX_BRIGHTNESS)
            be.setChanged()
            level.sendBlockUpdated(pos, be.blockState, be.blockState, 3)
        }
    }
}

object MonitorResolutionHandler : IPayloadHandler<MonitorResolutionPacket> {
    private val serverMaxPixels = 512 // matches client config max

    override fun handle(packet: MonitorResolutionPacket, ctx: IPayloadContext) {
        val pos = BlockPos(packet.posX, packet.posY, packet.posZ)
        if (!checkDistance(pos, ctx)) return
        val level = ctx.player().level()
        val be = level.getBlockEntity(pos)
        if (be is MonitorBlockEntity) {
            val ppb = packet.pixelsPerBlock.coerceIn(BrowserConstants.MIN_BLOCK_PIXELS, serverMaxPixels)
            be.pixelsPerBlock = ppb
            be.setChanged()
            level.sendBlockUpdated(pos, be.blockState, be.blockState, 3)
        }
    }
}

object CommandSyncHandler : IPayloadHandler<CommandSyncPacket> {
    override fun handle(packet: CommandSyncPacket, ctx: IPayloadContext) {
        val player = ctx.player()
        val pos = BlockPos(packet.posX, packet.posY, packet.posZ)
        if (!checkDistance(pos, ctx)) return
        val level = player.level()
        val be = level.getBlockEntity(pos)
        if (be !is MonitorBlockEntity) return
        if (!be.isActive) return

        val stack = player.mainHandItem
        if (stack.item !is MonitorCommandItem) return

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
        val pos = BlockPos(packet.posX, packet.posY, packet.posZ)
        if (!checkDistance(pos, ctx)) return
        val level = ctx.player().level()
        val be = level.getBlockEntity(pos)
        if (be is MonitorBlockEntity) {
            be.currentUrl = packet.url
            be.setChanged()
            level.sendBlockUpdated(pos, be.blockState, be.blockState, 3)
        }
    }
}


