package com.littlemine.webbrowser.network

import com.littlemine.webbrowser.MonitorMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

class MonitorBrightnessPacket(
    val posX: Int,
    val posY: Int,
    val posZ: Int,
    val brightness: Int
) : CustomPacketPayload {

    companion object {
        val TYPE: CustomPacketPayload.Type<MonitorBrightnessPacket> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(MonitorMod.MOD_ID, "monitor_brightness"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MonitorBrightnessPacket> =
            StreamCodec.of(
                { buf, packet ->
                    buf.writeInt(packet.posX)
                    buf.writeInt(packet.posY)
                    buf.writeInt(packet.posZ)
                    buf.writeInt(packet.brightness)
                },
                { buf ->
                    MonitorBrightnessPacket(
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
