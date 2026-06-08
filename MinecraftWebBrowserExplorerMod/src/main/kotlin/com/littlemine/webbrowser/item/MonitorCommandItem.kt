package com.littlemine.webbrowser.item

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.CustomData

class MonitorCommandItem(properties: Properties) : Item(properties) {

    companion object {
        private const val TAG_CONNECTED = "connected"
        private const val TAG_MONITOR_X = "monitor_x"
        private const val TAG_MONITOR_Y = "monitor_y"
        private const val TAG_MONITOR_Z = "monitor_z"

        private fun getTag(stack: ItemStack): CompoundTag =
            stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()

        private fun saveTag(stack: ItemStack, tag: CompoundTag) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        }

        fun isConnected(stack: ItemStack): Boolean =
            getTag(stack).getBoolean(TAG_CONNECTED)

        fun getConnectedPos(stack: ItemStack): BlockPos? {
            val tag = getTag(stack)
            if (!tag.getBoolean(TAG_CONNECTED)) return null
            return BlockPos(tag.getInt(TAG_MONITOR_X), tag.getInt(TAG_MONITOR_Y), tag.getInt(TAG_MONITOR_Z))
        }

        fun connectTo(stack: ItemStack, pos: BlockPos) {
            val tag = getTag(stack)
            tag.putBoolean(TAG_CONNECTED, true)
            tag.putInt(TAG_MONITOR_X, pos.x)
            tag.putInt(TAG_MONITOR_Y, pos.y)
            tag.putInt(TAG_MONITOR_Z, pos.z)
            saveTag(stack, tag)
        }

        fun disconnect(stack: ItemStack) {
            val tag = CompoundTag()
            saveTag(stack, tag)
        }
    }

    override fun isFoil(stack: ItemStack): Boolean = isConnected(stack)

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltipComponents: MutableList<Component>, tooltipFlag: TooltipFlag) {
        val pos = getConnectedPos(stack)
        if (pos != null) {
            tooltipComponents.add(
                Component.translatable("tooltip.webbrowser.connected_to", pos.x, pos.y, pos.z)
                    .withStyle(ChatFormatting.GREEN)
            )
        } else {
            tooltipComponents.add(
                Component.translatable("tooltip.webbrowser.not_connected")
                    .withStyle(ChatFormatting.GRAY)
            )
        }
    }
}
