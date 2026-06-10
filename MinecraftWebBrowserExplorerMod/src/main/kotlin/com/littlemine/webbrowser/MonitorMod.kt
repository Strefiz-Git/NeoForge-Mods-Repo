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
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory

@Mod(MonitorMod.MOD_ID)
class MonitorMod(modBus: IEventBus, container: ModContainer) {

    companion object {
        const val MOD_ID = "webbrowser"

        private val CLEANUP_LOGGER: org.slf4j.Logger = com.mojang.logging.LogUtils.getLogger()

        private fun killOrphanedJcefHelpers() {
            try {
                val pb = ProcessBuilder("taskkill", "/f", "/im", "jcef_helper.exe")
                pb.redirectErrorStream(true)
                val p = pb.start()
                if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly()
                }
            } catch (_: Exception) {}
        }
    }

    init {
        MonitorConfig.registerClientConfigs()
        MonitorConfig.registerCommonConfigs()
        container.registerConfig(ModConfig.Type.CLIENT, MonitorConfig.CLIENT_SPEC, "webbrowser-client.toml")
        container.registerConfig(ModConfig.Type.COMMON, MonitorConfig.COMMON_SPEC, "webbrowser-common.toml")

        if (FMLEnvironment.dist.isClient) {
            container.registerExtensionPoint(IConfigScreenFactory::class.java, IConfigScreenFactory { _, parent ->
                ConfigurationScreen(container, parent)
            })

            killOrphanedJcefHelpers()

            Runtime.getRuntime().addShutdownHook(Thread({
                killOrphanedJcefHelpers()
            }, "WB-Cleanup"))
        }

        BlockRegistry.BLOCKS.register(modBus)
        ItemRegistry.ITEMS.register(modBus)
        BlockEntityRegistry.BLOCK_ENTITIES.register(modBus)
        CreativeTabRegistry.TABS.register(modBus)
    }
}
