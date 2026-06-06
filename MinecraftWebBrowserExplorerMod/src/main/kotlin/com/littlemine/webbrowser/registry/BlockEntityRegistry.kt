package com.littlemine.webbrowser.registry

import com.littlemine.webbrowser.MonitorMod
import com.littlemine.webbrowser.block.MonitorBlockEntity
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.DeferredHolder

object BlockEntityRegistry {
    val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MonitorMod.MOD_ID)

    @Suppress("UNCHECKED_CAST")
    val MONITOR_BLOCK_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<MonitorBlockEntity>> =
        BLOCK_ENTITIES.register("monitor_block_entity") { rl ->
            BlockEntityType.Builder.of(::MonitorBlockEntity, BlockRegistry.MONITOR_BLOCK.get()).build(null) as BlockEntityType<MonitorBlockEntity>
        }
}
