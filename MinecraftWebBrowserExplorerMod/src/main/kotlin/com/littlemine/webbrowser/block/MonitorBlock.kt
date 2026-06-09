package com.littlemine.webbrowser.block

import com.littlemine.webbrowser.registry.BlockEntityRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.BlockHitResult
import org.slf4j.Logger

class MonitorBlock(properties: Properties) : Block(properties), EntityBlock {

    companion object {
        private val LOGGER: Logger = com.mojang.logging.LogUtils.getLogger()
        val FACING: DirectionProperty = DirectionProperty.create("facing")
        val PART: EnumProperty<MonitorPart> = EnumProperty.create("part", MonitorPart::class.java)
        val ACTIVE: BooleanProperty = BooleanProperty.create("active")

        fun calculatePart(col: Int, row: Int, width: Int, height: Int): MonitorPart {
            if (width == 1 && height == 1) return MonitorPart.SINGLE
            val isTop = row == 0
            val isBottom = row == height - 1
            val isLeft = col == 0
            val isRight = col == width - 1
            return when {
                isTop && isLeft -> MonitorPart.TOP_LEFT
                isTop && isRight -> MonitorPart.TOP_RIGHT
                isBottom && isLeft -> MonitorPart.BOTTOM_LEFT
                isBottom && isRight -> MonitorPart.BOTTOM_RIGHT
                isTop -> MonitorPart.TOP
                isBottom -> MonitorPart.BOTTOM
                isLeft -> MonitorPart.LEFT
                isRight -> MonitorPart.RIGHT
                else -> MonitorPart.CENTER
            }
        }
    }

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, MonitorPart.SINGLE)
                .setValue(ACTIVE, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, PART, ACTIVE)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        val facing = context.clickedFace
        LOGGER.info("[MonitorBlock] getStateForPlacement at {} facing={}", context.clickedPos, facing)
        return defaultBlockState().setValue(FACING, facing)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return BlockEntityRegistry.MONITOR_BLOCK_ENTITY.get().create(pos, state)
    }

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        LOGGER.info("[MonitorBlock] onPlace at {} facing={} active={}", pos, state.getValue(FACING), state.getValue(ACTIVE))
        if (!level.isClientSide && state.block !== oldState.block) {
            val be = level.getBlockEntity(pos)
            if (be is MonitorBlockEntity) be.tryFormMonitor()
        }
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        val be = level.getBlockEntity(pos)
        if (be is MonitorBlockEntity && be.isActive) {
            LOGGER.info("[MonitorBlock] useWithoutItem on active monitor {} - blocked (need command)", pos)
            return InteractionResult.PASS
        }
        return InteractionResult.PASS
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        LOGGER.info("[MonitorBlock] onRemove at {} active={}", pos, state.getValue(ACTIVE))
        if (state.block !== newState.block) {
            val be = level.getBlockEntity(pos)
            if (be is MonitorBlockEntity) be.invalidateMonitor()
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    override fun neighborChanged(state: BlockState, level: Level, pos: BlockPos, block: Block, fromPos: BlockPos, isMoving: Boolean) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving)
        if (!level.isClientSide) {
            val be = level.getBlockEntity(pos)
            if (be is MonitorBlockEntity && state.getValue(ACTIVE)) be.validateNeighbors()
        }
    }
}
