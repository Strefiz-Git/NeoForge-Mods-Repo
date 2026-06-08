package com.littlemine.webbrowser.registry

import com.littlemine.webbrowser.MonitorMod
import com.littlemine.webbrowser.item.MonitorCommandItem
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister

object ItemRegistry {
    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(MonitorMod.MOD_ID)

    val MONITOR_BLOCK_ITEM: DeferredItem<BlockItem> = ITEMS.register("monitor_block") { rl ->
        BlockItem(
            BlockRegistry.MONITOR_BLOCK.get(),
            Item.Properties()
        )
    }

    val MONITOR_COMMAND_ITEM: DeferredItem<MonitorCommandItem> = ITEMS.register("monitor_command") { rl ->
        MonitorCommandItem(
            Item.Properties().stacksTo(1)
        )
    }
}
