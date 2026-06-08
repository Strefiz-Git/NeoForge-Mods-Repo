package com.littlemine.webbrowser

import com.littlemine.webbrowser.block.MonitorBlockEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.BlockHitResult

object ClientBridge {
    var onMonitorFormed: ((MonitorBlockEntity) -> Unit)? = null
    var onMonitorBreak: ((MonitorBlockEntity) -> Unit)? = null
    var onMonitorCommandShiftUse: ((MonitorBlockEntity) -> Unit)? = null
}
