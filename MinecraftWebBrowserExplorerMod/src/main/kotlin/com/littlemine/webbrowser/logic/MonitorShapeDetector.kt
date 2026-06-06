package com.littlemine.webbrowser.logic

import com.littlemine.webbrowser.block.MonitorBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.Logger

data class MonitorShape(
    val topLeft: BlockPos,
    val width: Int,
    val height: Int,
    val facing: Direction,
    val rightDir: Direction,
    val downDir: Direction
)

object MonitorShapeDetector {
    private val LOGGER: Logger = com.mojang.logging.LogUtils.getLogger()

    private const val MIN_SIZE = 2
    private const val MAX_WIDTH = 48
    private const val MAX_HEIGHT = 27

    fun findRectangle(origin: BlockPos, level: Level): MonitorShape? {
        val originState = level.getBlockState(origin)
        if (originState.block !is MonitorBlock) {
            LOGGER.debug("[ShapeDetector] findRectangle at {} — not a MonitorBlock", origin)
            return null
        }

        val facing = originState.getValue(MonitorBlock.FACING)
        val (rightDir, downDir) = getPerpendicularDirections(facing) ?: return null

        val topLeft = findTopLeft(origin, level, rightDir, downDir, facing) ?: return null

        val width = measureWidth(topLeft, level, rightDir, facing)
        val height = measureHeight(topLeft, level, downDir, facing)

        LOGGER.debug("[ShapeDetector] topLeft={} facing={} right={} down={} {}x{}", topLeft, facing, rightDir, downDir, width, height)

        if (width < MIN_SIZE || height < MIN_SIZE) {
            LOGGER.debug("[ShapeDetector] too small (min {}x{})", MIN_SIZE, MIN_SIZE)
            return null
        }
        if (width > MAX_WIDTH || height > MAX_HEIGHT) {
            LOGGER.debug("[ShapeDetector] too large (max {}x{})", MAX_WIDTH, MAX_HEIGHT)
            return null
        }

        if (!isRectangleFilled(topLeft, width, height, level, rightDir, downDir, facing)) {
            LOGGER.debug("[ShapeDetector] rectangle not fully filled")
            return null
        }

        if (hasBlocksOutside(topLeft, width, height, level, rightDir, downDir, facing)) {
            LOGGER.debug("[ShapeDetector] blocks found outside rectangle")
            return null
        }

        LOGGER.debug("[ShapeDetector] SUCCESS: {}x{} monitor from {}", width, height, topLeft)
        return MonitorShape(topLeft, width, height, facing, rightDir, downDir)
    }

    private fun findTopLeft(origin: BlockPos, level: Level, rightDir: Direction, downDir: Direction, facing: Direction): BlockPos? {
        var pos = origin
        while (true) {
            val leftPos = pos.relative(rightDir.opposite)
            if (!isMonitorBlock(leftPos, level, facing)) break
            pos = leftPos
        }
        while (true) {
            val upPos = pos.relative(downDir.opposite)
            if (!isMonitorBlock(upPos, level, facing)) break
            pos = upPos
        }
        return pos
    }

    private fun measureWidth(start: BlockPos, level: Level, rightDir: Direction, facing: Direction): Int {
        var width = 0
        var pos = start
        while (isMonitorBlock(pos, level, facing)) {
            width++
            pos = pos.relative(rightDir)
        }
        return width
    }

    private fun measureHeight(start: BlockPos, level: Level, downDir: Direction, facing: Direction): Int {
        var height = 0
        var pos = start
        while (isMonitorBlock(pos, level, facing)) {
            height++
            pos = pos.relative(downDir)
        }
        return height
    }

    private fun isRectangleFilled(
        topLeft: BlockPos, width: Int, height: Int, level: Level,
        rightDir: Direction, downDir: Direction, facing: Direction
    ): Boolean {
        for (row in 0 until height) {
            for (col in 0 until width) {
                val pos = topLeft.offset(
                    rightDir.stepX * col + downDir.stepX * row,
                    rightDir.stepY * col + downDir.stepY * row,
                    rightDir.stepZ * col + downDir.stepZ * row
                )
                if (!isMonitorBlock(pos, level, facing)) return false
            }
        }
        return true
    }

    private fun hasBlocksOutside(
        topLeft: BlockPos, width: Int, height: Int, level: Level,
        rightDir: Direction, downDir: Direction, facing: Direction
    ): Boolean {
        for (col in -1..width) {
            val posAbove = topLeft.offset(
                rightDir.stepX * col + downDir.stepX * (-1),
                rightDir.stepY * col + downDir.stepY * (-1),
                rightDir.stepZ * col + downDir.stepZ * (-1)
            )
            if (isMonitorBlock(posAbove, level, facing)) return true

            val posBelow = topLeft.offset(
                rightDir.stepX * col + downDir.stepX * height,
                rightDir.stepY * col + downDir.stepY * height,
                rightDir.stepZ * col + downDir.stepZ * height
            )
            if (isMonitorBlock(posBelow, level, facing)) return true
        }
        for (row in -1..height) {
            val posLeft = topLeft.offset(
                rightDir.stepX * (-1) + downDir.stepX * row,
                rightDir.stepY * (-1) + downDir.stepY * row,
                rightDir.stepZ * (-1) + downDir.stepZ * row
            )
            if (isMonitorBlock(posLeft, level, facing)) return true

            val posRight = topLeft.offset(
                rightDir.stepX * width + downDir.stepX * row,
                rightDir.stepY * width + downDir.stepY * row,
                rightDir.stepZ * width + downDir.stepZ * row
            )
            if (isMonitorBlock(posRight, level, facing)) return true
        }
        return false
    }

    private fun isMonitorBlock(pos: BlockPos, level: Level, facing: Direction): Boolean {
        val state = level.getBlockState(pos)
        if (state.block !is MonitorBlock) return false
        return state.getValue(MonitorBlock.FACING) == facing
    }

    private fun getPerpendicularDirections(facing: Direction): Pair<Direction, Direction>? {
        return when (facing) {
            Direction.NORTH -> Pair(Direction.EAST, Direction.DOWN)
            Direction.SOUTH -> Pair(Direction.WEST, Direction.DOWN)
            Direction.EAST -> Pair(Direction.SOUTH, Direction.DOWN)
            Direction.WEST -> Pair(Direction.NORTH, Direction.DOWN)
            Direction.UP -> Pair(Direction.EAST, Direction.SOUTH)
            Direction.DOWN -> Pair(Direction.EAST, Direction.SOUTH)
        }
    }
}
