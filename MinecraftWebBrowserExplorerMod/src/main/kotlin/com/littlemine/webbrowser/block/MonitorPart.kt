package com.littlemine.webbrowser.block

import net.minecraft.util.StringRepresentable

enum class MonitorPart(val partName: String) : StringRepresentable {
    SINGLE("single"),
    TOP_LEFT("top_left"),
    TOP("top"),
    TOP_RIGHT("top_right"),
    LEFT("left"),
    CENTER("center"),
    RIGHT("right"),
    BOTTOM_LEFT("bottom_left"),
    BOTTOM("bottom"),
    BOTTOM_RIGHT("bottom_right");

    override fun getSerializedName(): String = partName
}
