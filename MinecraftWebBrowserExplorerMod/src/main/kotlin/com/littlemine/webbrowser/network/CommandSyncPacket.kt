package com.littlemine.webbrowser.network

import com.littlemine.webbrowser.MonitorMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

class CommandSyncPacket(
    val posX: Int,
    val posY: Int,
    val posZ: Int,
    val connect: Boolean
) : CustomPacketPayload {

    companion object {
        val TYPE: CustomPacketPayload.Type<CommandSyncPacket> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(MonitorMod.MOD_ID, "command_sync"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CommandSyncPacket> =
            StreamCodec.of(
                { buf, packet ->
                    buf.writeInt(packet.posX)
                    buf.writeInt(packet.posY)
                    buf.writeInt(packet.posZ)
                    buf.writeBoolean(packet.connect)
                },
                { buf ->
                    CommandSyncPacket(
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readBoolean()
                    )
                }
            )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
