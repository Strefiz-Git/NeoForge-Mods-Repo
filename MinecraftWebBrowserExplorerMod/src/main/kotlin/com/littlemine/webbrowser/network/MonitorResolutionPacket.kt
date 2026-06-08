package com.littlemine.webbrowser.network

import com.littlemine.webbrowser.MonitorMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

class MonitorResolutionPacket(
    val posX: Int,
    val posY: Int,
    val posZ: Int,
    val pixelsPerBlock: Int
) : CustomPacketPayload {

    companion object {
        val TYPE: CustomPacketPayload.Type<MonitorResolutionPacket> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(MonitorMod.MOD_ID, "monitor_resolution"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MonitorResolutionPacket> =
            StreamCodec.of(
                { buf, packet ->
                    buf.writeInt(packet.posX)
                    buf.writeInt(packet.posY)
                    buf.writeInt(packet.posZ)
                    buf.writeInt(packet.pixelsPerBlock)
                },
                { buf ->
                    MonitorResolutionPacket(
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt()
                    )
                }
            )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
