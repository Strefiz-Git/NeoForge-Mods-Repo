package com.littlemine.webbrowser.block

import com.littlemine.webbrowser.ClientBridge
import com.littlemine.webbrowser.logic.MonitorShape
import com.littlemine.webbrowser.logic.MonitorShapeDetector
import com.littlemine.webbrowser.registry.BlockEntityRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class MonitorBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(BlockEntityRegistry.MONITOR_BLOCK_ENTITY.get(), pos, state) {
    private val LOGGER: org.slf4j.Logger = com.mojang.logging.LogUtils.getLogger()

    var width: Int = 1
    var height: Int = 1
    var currentUrl: String = "https://www.google.com"
    var isActive: Boolean = false
    var masterPos: BlockPos = blockPos
    var shape: MonitorShape? = null

    val isMaster: Boolean get() = masterPos == blockPos

    fun master(): MonitorBlockEntity {
        if (isMaster) return this
        val be = level?.getBlockEntity(masterPos)
        return if (be is MonitorBlockEntity) be else this
    }

    fun invalidateMonitor() {
        if (!isActive) return
        val s = shape ?: return
        LOGGER.debug("[BE] invalidateMonitor at {} shape={}", blockPos, s)
        isActive = false

        val oldMasterPos = masterPos
        val wasClient = level?.isClientSide == true

        for (row in 0 until s.height) {
            for (col in 0 until s.width) {
                val p = s.topLeft.offset(
                    s.rightDir.stepX * col + s.downDir.stepX * row,
                    s.rightDir.stepY * col + s.downDir.stepY * row,
                    s.rightDir.stepZ * col + s.downDir.stepZ * row
                )
                val state = level?.getBlockState(p) ?: continue
                if (state.block is MonitorBlock) {
                    level?.setBlock(p, state.setValue(MonitorBlock.ACTIVE, false).setValue(MonitorBlock.PART, MonitorPart.SINGLE), 3)
                    val be = level?.getBlockEntity(p)
                    if (be is MonitorBlockEntity && be !== this) {
                        be.isActive = false
                        be.width = 1
                        be.height = 1
                        be.masterPos = p
                        be.shape = null
                    }
                }
            }
        }
        width = 1
        height = 1
        masterPos = blockPos
        shape = null
        setChanged()

        if (wasClient) {
            val masterBE = level?.getBlockEntity(oldMasterPos)
            if (masterBE is MonitorBlockEntity) {
                ClientBridge.onMonitorBreak?.invoke(masterBE)
            } else {
                ClientBridge.onMonitorBreak?.invoke(this)
            }
        }
    }

    fun validateNeighbors() {
        val detected = level?.let { MonitorShapeDetector.findRectangle(blockPos, it) }
        if (detected == null || detected.width != width || detected.height != height) {
            invalidateMonitor()
        }
    }

    fun tryFormMonitor() {
        val level = level ?: return
        val detected = MonitorShapeDetector.findRectangle(blockPos, level)
        LOGGER.debug("[BE] tryFormMonitor at {} detected={} (was active={} oldShape={})",
            blockPos, detected, isActive, shape)
        if (detected == null) return
        val facing = detected.facing
        val rightDir = detected.rightDir
        val downDir = detected.downDir

        val oldMasterPos = if (isActive && masterPos != blockPos) masterPos else null
        val wasResized = shape != null && (shape!!.width != detected.width || shape!!.height != detected.height)

        shape = detected
        width = detected.width
        height = detected.height
        isActive = true
        masterPos = detected.topLeft

        for (row in 0 until detected.height) {
            for (col in 0 until detected.width) {
                val p = detected.topLeft.offset(
                    rightDir.stepX * col + downDir.stepX * row,
                    rightDir.stepY * col + downDir.stepY * row,
                    rightDir.stepZ * col + downDir.stepZ * row
                )
                val state = level.getBlockState(p)
                if (state.block !is MonitorBlock) continue
                val part = MonitorBlock.calculatePart(col, row, detected.width, detected.height)
                level.setBlock(p, state.setValue(MonitorBlock.PART, part).setValue(MonitorBlock.ACTIVE, true), 3)
                val be = level.getBlockEntity(p)
                if (be is MonitorBlockEntity && be !== this) {
                    be.width = detected.width
                    be.height = detected.height
                    be.isActive = true
                    be.masterPos = detected.topLeft
                    be.shape = detected
                    be.setChanged()
                }
            }
        }
        LOGGER.debug("[BE] tryFormMonitor SUCCESS: {}x{} active at master={} resized={}",
            width, height, masterPos, wasResized)
        setChanged()

        if (level.isClientSide) {
            val oldPos = oldMasterPos
            if (oldPos != null && oldPos != detected.topLeft) {
                val oldMasterBE = level.getBlockEntity(oldPos)
                if (oldMasterBE is MonitorBlockEntity) {
                    ClientBridge.onMonitorBreak?.invoke(oldMasterBE)
                }
            }
            ClientBridge.onMonitorFormed?.invoke(this)
        }
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putInt("width", width)
        tag.putInt("height", height)
        tag.putString("url", currentUrl)
        tag.putBoolean("active", isActive)
        tag.putLong("master_pos", masterPos.asLong())
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        width = tag.getInt("width")
        height = tag.getInt("height")
        currentUrl = tag.getString("url")
        isActive = tag.getBoolean("active")
        if (tag.contains("master_pos")) masterPos = BlockPos.of(tag.getLong("master_pos"))
        recalculateClientShape()
    }

    fun recalculateClientShape() {
        if (level?.isClientSide != true || !isActive || shape != null) return
        val detected = level?.let { MonitorShapeDetector.findRectangle(blockPos, it) }
        if (detected != null) {
            LOGGER.debug("[BE] recalculated shape on client: {}x{} master={}", detected.width, detected.height, detected.topLeft)
            shape = detected
            width = detected.width
            height = detected.height
            masterPos = detected.topLeft
        }
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        saveAdditional(tag, registries)
        return tag
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }
}
