package com.littlemine.webbrowser.client.input

import com.cinemamod.mcef.MCEF
import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.client.ClientBrowserCache
import com.littlemine.webbrowser.network.MonitorInputPacket
import com.littlemine.webbrowser.network.MonitorUpdatePacket
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor

object InputHandler {

    var activeMonitor: MonitorBlockEntity? = null
        private set

    fun isInputActive(): Boolean = activeMonitor != null

    fun activate(be: MonitorBlockEntity) {
        val master = be.master()
        val shape = master.shape ?: return
        activeMonitor = be
        val browser = ClientBrowserCache.get(master.blockPos)
        if (browser == null) {
            val b = MCEF.createBrowser(master.currentUrl, false,
                shape.width * 120, shape.height * 120)
            ClientBrowserCache.put(master.blockPos, b)
        }
        Minecraft.getInstance().mouseHandler.grabMouse()
    }

    fun deactivate() {
        activeMonitor?.let {
            ClientBrowserCache.remove(it.master().blockPos)
        }
        activeMonitor = null
        Minecraft.getInstance().mouseHandler.releaseMouse()
    }

    fun openUrlScreen(be: MonitorBlockEntity) {
        Minecraft.getInstance().setScreen(UrlScreen(be.master()))
    }

    fun updateUrl(be: MonitorBlockEntity, url: String) {
        be.currentUrl = url
        ClientBrowserCache.get(be.master().blockPos)?.loadURL(url)
        PacketDistributor.sendToServer(MonitorUpdatePacket(
            be.blockPos.x, be.blockPos.y, be.blockPos.z, url
        ))
    }

    fun onClientTick() {
        val monitor = activeMonitor ?: return
        val master = monitor.master()
        val browser = ClientBrowserCache.get(master.blockPos) ?: return

        val (browserX, browserY) = getMousePositionInMonitor(monitor)

        browser.sendMouseMove(browserX.toInt(), browserY.toInt())
    }

    fun onMouseClick(button: Int, action: Int, modifiers: Int) {
        val monitor = activeMonitor ?: return
        val master = monitor.master()
        val browser = ClientBrowserCache.get(master.blockPos) ?: return
        val (x, y) = getMousePositionInMonitor(monitor)

        if (action == 1) {
            browser.sendMousePress(x.toInt(), y.toInt(), button)
            PacketDistributor.sendToServer(MonitorInputPacket(
                monitor.blockPos.x, monitor.blockPos.y, monitor.blockPos.z,
                x, y, button, true
            ))
        } else {
            browser.sendMouseRelease(x.toInt(), y.toInt(), button)
        }
    }

    fun onKeyPress(keyCode: Int, scanCode: Int, modifiers: Int) {
        val monitor = activeMonitor ?: return
        val browser = ClientBrowserCache.get(monitor.master().blockPos) ?: return
        browser.sendKeyPress(keyCode, scanCode.toLong(), modifiers)
    }

    fun onKeyRelease(keyCode: Int, scanCode: Int, modifiers: Int) {
        val monitor = activeMonitor ?: return
        val browser = ClientBrowserCache.get(monitor.master().blockPos) ?: return
        browser.sendKeyRelease(keyCode, scanCode.toLong(), modifiers)
    }

    fun onScroll(deltaX: Double, deltaY: Double) {
        val monitor = activeMonitor ?: return
        val browser = ClientBrowserCache.get(monitor.master().blockPos) ?: return
        val (x, y) = getMousePositionInMonitor(monitor)
        browser.sendMouseWheel(x.toInt(), y.toInt(), deltaY, 0)
    }

    private fun getMousePositionInMonitor(monitor: MonitorBlockEntity): Pair<Double, Double> {
        val mc = Minecraft.getInstance()
        val hit = raycastForMonitor()
        if (hit !is BlockHitResult || hit.type == HitResult.Type.MISS) return Pair(0.0, 0.0)

        val pos = hit.blockPos
        val be = mc.level?.getBlockEntity(pos) ?: return Pair(0.0, 0.0)
        if (be !is MonitorBlockEntity) return Pair(0.0, 0.0)

        val master = be.master()
        val shape = master.shape ?: return Pair(0.0, 0.0)
        val facing = hit.direction

        val relX = pos.x - shape.topLeft.x
        val relY = pos.y - shape.topLeft.y
        val relZ = pos.z - shape.topLeft.z

        val hitU = (hit.location.x - pos.x - 0.5) + 0.5
        val hitV = (hit.location.y - pos.y - 0.5) + 0.5

        val blockPixelWidth = 120
        val blockPixelHeight = 120

        val browserX: Double
        val browserY: Double
        when (facing) {
            Direction.NORTH -> {
                browserX = (1f - hitU) * blockPixelWidth + relX * blockPixelWidth
                browserY = hitV * blockPixelHeight + relY * blockPixelHeight
            }
            Direction.SOUTH -> {
                browserX = hitU * blockPixelWidth + relX * blockPixelWidth
                browserY = hitV * blockPixelHeight + relY * blockPixelHeight
            }
            Direction.EAST -> {
                browserX = hitU * blockPixelWidth + relZ * blockPixelWidth
                browserY = hitV * blockPixelHeight + relY * blockPixelHeight
            }
            Direction.WEST -> {
                browserX = (1f - hitU) * blockPixelWidth + (-relZ) * blockPixelWidth
                browserY = hitV * blockPixelHeight + relY * blockPixelHeight
            }
            Direction.UP -> {
                browserX = hitU * blockPixelWidth + relX * blockPixelWidth
                browserY = (1f - hitV) * blockPixelHeight + (-relZ) * blockPixelHeight
            }
            Direction.DOWN -> {
                browserX = hitU * blockPixelWidth + relX * blockPixelWidth
                browserY = hitV * blockPixelHeight + relZ * blockPixelHeight
            }
            else -> return Pair(0.0, 0.0)
        }

        return Pair(
            browserX.coerceIn(0.0, (shape.width * blockPixelWidth).toDouble()),
            browserY.coerceIn(0.0, (shape.height * blockPixelHeight).toDouble())
        )
    }

    private fun raycastForMonitor(): HitResult {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO)
        val reach = player.blockInteractionRange()
        val start = player.eyePosition
        val look = player.lookAngle
        val end = start.add(look.x * reach, look.y * reach, look.z * reach)
        return mc.level?.clip(ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
            ?: BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO)
    }
}
