package com.littlemine.webbrowser.registry

import com.littlemine.webbrowser.MonitorMod
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

object CreativeTabRegistry {
    val TABS: DeferredRegister<CreativeModeTab> = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MonitorMod.MOD_ID)

    val WEBBROWSER_TAB: DeferredHolder<CreativeModeTab, CreativeModeTab> = TABS.register("webbrowser_tab") { rl ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.webbrowser"))
            .icon { ItemStack(ItemRegistry.MONITOR_BLOCK_ITEM.get()) }
            .displayItems { params, output ->
                output.accept(ItemRegistry.MONITOR_BLOCK_ITEM.get())
            }
            .build()
    }
}
