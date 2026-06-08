package com.littlemine.webbrowser.item

import net.minecraft.world.item.Item

class MonitorCommandItem(properties: Properties) : Item(properties) {

    override fun isFoil(stack: net.minecraft.world.item.ItemStack): Boolean = true
}
