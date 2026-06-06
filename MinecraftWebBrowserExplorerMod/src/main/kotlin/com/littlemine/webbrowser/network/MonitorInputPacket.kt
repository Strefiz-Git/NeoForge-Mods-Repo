package com.littlemine.webbrowser.network

import com.littlemine.webbrowser.MonitorMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

class MonitorInputPacket(
    val posX: Int,
    val posY: Int,
    val posZ: Int,
    val mouseX: Double,
    val mouseY: Double,
    val button: Int,
    val pressed: Boolean
) : CustomPacketPayload {

    companion object {
        val TYPE: CustomPacketPayload.Type<MonitorInputPacket> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(MonitorMod.MOD_ID, "monitor_input"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MonitorInputPacket> =
            StreamCodec.of(
                { buf, packet ->
                    buf.writeInt(packet.posX)
                    buf.writeInt(packet.posY)
                    buf.writeInt(packet.posZ)
                    buf.writeDouble(packet.mouseX)
                    buf.writeDouble(packet.mouseY)
                    buf.writeInt(packet.button)
                    buf.writeBoolean(packet.pressed)
                },
                { buf ->
                    MonitorInputPacket(
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readInt(),
                        buf.readBoolean()
                    )
                }
            )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
