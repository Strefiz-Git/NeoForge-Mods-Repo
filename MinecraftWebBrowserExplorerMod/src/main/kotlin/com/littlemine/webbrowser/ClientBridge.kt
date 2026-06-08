package com.littlemine.webbrowser

import com.littlemine.webbrowser.block.MonitorBlockEntity

object ClientBridge {
    var onMonitorFormed: ((MonitorBlockEntity) -> Unit)? = null
    var onMonitorBreak: ((MonitorBlockEntity) -> Unit)? = null
    var onMonitorCommandRightClick: ((MonitorBlockEntity) -> Unit)? = null
}
