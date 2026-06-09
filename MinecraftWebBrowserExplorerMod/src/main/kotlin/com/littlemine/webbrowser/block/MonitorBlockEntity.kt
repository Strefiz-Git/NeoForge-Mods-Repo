package com.littlemine.webbrowser.block

import com.littlemine.webbrowser.ClientBridge
import com.littlemine.webbrowser.item.MonitorCommandItem
import com.littlemine.webbrowser.logic.MonitorShape
import com.littlemine.webbrowser.logic.MonitorShapeDetector
import com.littlemine.webbrowser.registry.BlockEntityRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
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
    var pixelsPerBlock: Int = 120
    var audioMode: Int = 0 // 0=player, 1=center_block
    var volume: Int = 100 // 0-100
    var brightness: Int = 100 // 0-500 (screen brightness)
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
        if (!isActive) {
            LOGGER.info("[BE] invalidateMonitor at {} - not active, skipping", blockPos)
            return
        }
        val s = shape ?: return
        LOGGER.info("[BE] invalidateMonitor at {} shape={}", blockPos, s)
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
                        be.setChanged()
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
        } else {
            val serverLevel = level as? net.minecraft.server.level.ServerLevel
            if (serverLevel != null) {
                for (player in serverLevel.players()) {
                    for (stack in listOf(player.mainHandItem, player.offhandItem)) {
                        if (stack.item is MonitorCommandItem && MonitorCommandItem.isConnected(stack)) {
                            val connectedPos = MonitorCommandItem.getConnectedPos(stack)
                            if (connectedPos == oldMasterPos) {
                                MonitorCommandItem.disconnect(stack)
                                player.displayClientMessage(
                                    Component.translatable("message.webbrowser.monitor_broken"),
                                    true
                                )
                            }
                        }
                    }
                }
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
        if (level.isClientSide) return
        val detected = MonitorShapeDetector.findRectangle(blockPos, level)
        LOGGER.info("[BE] tryFormMonitor at {} detected={} (was active={} oldShape={})",
            blockPos, detected, isActive, shape)
        if (detected == null) {
            LOGGER.info("[BE] tryFormMonitor at {} - no valid shape detected (too small, wrong ratio, or not filled)", blockPos)
            return
        }
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
                    be.pixelsPerBlock = pixelsPerBlock
                    be.audioMode = audioMode
                    be.volume = volume
                    be.brightness = brightness
                    be.isActive = true
                    be.masterPos = detected.topLeft
                    be.shape = detected
                    be.setChanged()
                }
            }
        }
        LOGGER.info("[BE] tryFormMonitor SUCCESS: {}x{} active at master={} resized={}",
            width, height, masterPos, wasResized)
        setChanged()
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putInt("width", width)
        tag.putInt("height", height)
        tag.putString("url", currentUrl)
        tag.putInt("pixelsPerBlock", pixelsPerBlock)
        tag.putInt("audioMode", audioMode)
        tag.putInt("volume", volume)
        tag.putInt("brightness", brightness)
        tag.putBoolean("active", isActive)
        tag.putLong("master_pos", masterPos.asLong())
        val s = shape
        if (s != null) {
            tag.putLong("shape_topLeft", s.topLeft.asLong())
            tag.putInt("shape_width", s.width)
            tag.putInt("shape_height", s.height)
            tag.putString("shape_facing", s.facing.name)
            tag.putString("shape_rightDir", s.rightDir.name)
            tag.putString("shape_downDir", s.downDir.name)
        }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        val wasActive = isActive
        super.loadAdditional(tag, registries)
        width = tag.getInt("width")
        height = tag.getInt("height")
        currentUrl = tag.getString("url")
        if (tag.contains("pixelsPerBlock")) pixelsPerBlock = tag.getInt("pixelsPerBlock")
        if (tag.contains("audioMode")) audioMode = tag.getInt("audioMode")
        if (tag.contains("volume")) volume = tag.getInt("volume")
        if (tag.contains("brightness")) brightness = tag.getInt("brightness")
        isActive = tag.getBoolean("active")
        if (tag.contains("master_pos")) masterPos = BlockPos.of(tag.getLong("master_pos"))
        if (tag.contains("shape_width")) {
            val facing = Direction.byName(tag.getString("shape_facing"))
            val rightDir = Direction.byName(tag.getString("shape_rightDir"))
            val downDir = Direction.byName(tag.getString("shape_downDir"))
            if (facing != null && rightDir != null && downDir != null) {
                shape = MonitorShape(
                    topLeft = BlockPos.of(tag.getLong("shape_topLeft")),
                    width = tag.getInt("shape_width"),
                    height = tag.getInt("shape_height"),
                    facing = facing,
                    rightDir = rightDir,
                    downDir = downDir
                )
            } else {
                shape = null
            }
        } else {
            shape = null
        }
        if (level?.isClientSide == true) {
            if (wasActive && !isActive) {
                val master = if (isMaster) this else level?.getBlockEntity(masterPos)
                if (master is MonitorBlockEntity) {
                    ClientBridge.onMonitorBreak?.invoke(master)
                } else {
                    ClientBridge.onMonitorBreak?.invoke(this)
                }
            } else if (!wasActive && isActive) {
                val master = if (isMaster) this else level?.getBlockEntity(masterPos)
                if (master is MonitorBlockEntity) {
                    ClientBridge.onMonitorFormed?.invoke(master)
                } else {
                    ClientBridge.onMonitorFormed?.invoke(this)
                }
            }
        }
        if (isActive && shape == null) recalculateClientShape()
    }

    fun recalculateClientShape() {
        if (level?.isClientSide != true || !isActive || shape != null) return
        val detected = level?.let { MonitorShapeDetector.findRectangle(blockPos, it) }
        if (detected != null) {
            LOGGER.info("[BE] recalculated shape on client: {}x{} master={}", detected.width, detected.height, detected.topLeft)
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
