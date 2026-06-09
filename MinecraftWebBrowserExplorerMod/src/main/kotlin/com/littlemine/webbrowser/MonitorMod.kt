package com.littlemine.webbrowser

import com.littlemine.webbrowser.config.MonitorConfig
import com.littlemine.webbrowser.registry.BlockEntityRegistry
import com.littlemine.webbrowser.registry.BlockRegistry
import com.littlemine.webbrowser.registry.CreativeTabRegistry
import com.littlemine.webbrowser.registry.ItemRegistry
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig

@Mod(MonitorMod.MOD_ID)
class MonitorMod(modBus: IEventBus, container: ModContainer) {

    companion object {
        const val MOD_ID = "webbrowser"
    }

    init {
        MonitorConfig.registerClientConfigs()
        MonitorConfig.registerCommonConfigs()
        container.registerConfig(ModConfig.Type.CLIENT, MonitorConfig.CLIENT_SPEC, "webbrowser-client.toml")
        container.registerConfig(ModConfig.Type.COMMON, MonitorConfig.COMMON_SPEC, "webbrowser-common.toml")

        BlockRegistry.BLOCKS.register(modBus)
        ItemRegistry.ITEMS.register(modBus)
        BlockEntityRegistry.BLOCK_ENTITIES.register(modBus)
        CreativeTabRegistry.TABS.register(modBus)
    }
}
