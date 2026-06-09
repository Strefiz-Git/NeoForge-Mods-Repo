package com.littlemine.webbrowser.network

import com.littlemine.webbrowser.MonitorMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

class MonitorAudioPacket(
    val posX: Int,
    val posY: Int,
    val posZ: Int,
    val audioMode: Int,
    val volume: Int
) : CustomPacketPayload {

    companion object {
        val TYPE: CustomPacketPayload.Type<MonitorAudioPacket> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(MonitorMod.MOD_ID, "monitor_audio"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MonitorAudioPacket> =
            StreamCodec.of(
                { buf, packet ->
                    buf.writeInt(packet.posX)
                    buf.writeInt(packet.posY)
                    buf.writeInt(packet.posZ)
                    buf.writeInt(packet.audioMode)
                    buf.writeInt(packet.volume)
                },
                { buf ->
                    MonitorAudioPacket(
                        buf.readInt(),
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
