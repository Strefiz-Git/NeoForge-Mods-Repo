package com.littlemine.webbrowser

import com.littlemine.webbrowser.registry.BlockEntityRegistry
import com.littlemine.webbrowser.registry.BlockRegistry
import com.littlemine.webbrowser.registry.CreativeTabRegistry
import com.littlemine.webbrowser.registry.ItemRegistry
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod

@Mod(MonitorMod.MOD_ID)
class MonitorMod(modBus: IEventBus) {

    companion object {
        const val MOD_ID = "webbrowser"
    }

    init {
        BlockRegistry.BLOCKS.register(modBus)
        ItemRegistry.ITEMS.register(modBus)
        BlockEntityRegistry.BLOCK_ENTITIES.register(modBus)
        CreativeTabRegistry.TABS.register(modBus)
    }
}
