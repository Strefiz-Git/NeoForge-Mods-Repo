package com.littlemine.webbrowser.client.input

import net.minecraft.core.BlockPos

object InputHandler {
    private var activeMonitorBlockPos: BlockPos? = null

    fun isOccupied(monitorPos: BlockPos): Boolean {
        val active = activeMonitorBlockPos
        return active != null && active != monitorPos
    }

    fun setActiveMonitor(pos: BlockPos?) {
        activeMonitorBlockPos = pos
    }

    fun isInputActive(): Boolean = activeMonitorBlockPos != null
}
