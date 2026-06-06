package com.littlemine.webbrowser.registry

import com.littlemine.webbrowser.MonitorMod
import com.littlemine.webbrowser.block.MonitorBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister

object BlockRegistry {
    val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(MonitorMod.MOD_ID)

    val MONITOR_BLOCK: DeferredBlock<MonitorBlock> = BLOCKS.register("monitor_block") { rl ->
        MonitorBlock(BlockBehaviour.Properties.of()
            .strength(1.5f, 6.0f)
            .lightLevel { 10 }
            .noOcclusion())
    }
}
