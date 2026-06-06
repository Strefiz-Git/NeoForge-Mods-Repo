package com.littlemine.webbrowser.network

import com.littlemine.webbrowser.MonitorMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

class MonitorUpdatePacket(
    val posX: Int,
    val posY: Int,
    val posZ: Int,
    val url: String
) : CustomPacketPayload {

    companion object {
        val TYPE: CustomPacketPayload.Type<MonitorUpdatePacket> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(MonitorMod.MOD_ID, "monitor_update"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MonitorUpdatePacket> =
            StreamCodec.of(
                { buf, packet ->
                    buf.writeInt(packet.posX)
                    buf.writeInt(packet.posY)
                    buf.writeInt(packet.posZ)
                    buf.writeUtf(packet.url)
                },
                { buf ->
                    MonitorUpdatePacket(
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readUtf()
                    )
                }
            )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
