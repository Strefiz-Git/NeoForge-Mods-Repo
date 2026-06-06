package com.littlemine.webbrowser.network

import com.littlemine.webbrowser.MonitorMod
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.handling.IPayloadHandler
import net.neoforged.neoforge.network.registration.PayloadRegistrar

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object Network {

    @SubscribeEvent
    fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        val registrar = PayloadRegistrar(MonitorMod.MOD_ID)

        registrar.playToServer(
            MonitorUpdatePacket.TYPE,
            MonitorUpdatePacket.STREAM_CODEC,
            MonitorUpdateHandler
        )

        registrar.playToServer(
            MonitorInputPacket.TYPE,
            MonitorInputPacket.STREAM_CODEC,
            MonitorInputHandler
        )
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

object MonitorInputHandler : IPayloadHandler<MonitorInputPacket> {
    override fun handle(packet: MonitorInputPacket, ctx: IPayloadContext) {
        // Server acknowledges mouse input; no processing needed
    }
}
