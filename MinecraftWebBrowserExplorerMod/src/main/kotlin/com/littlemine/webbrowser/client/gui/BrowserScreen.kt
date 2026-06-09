package com.littlemine.webbrowser.client.gui

import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.client.BrowserConstants
import com.littlemine.webbrowser.client.ClientBrowserCache
import com.littlemine.webbrowser.client.input.InputHandler
import com.littlemine.webbrowser.item.MonitorCommandItem
import com.littlemine.webbrowser.network.CommandSyncPacket
import com.littlemine.webbrowser.network.MonitorAudioPacket
import com.littlemine.webbrowser.network.MonitorBrightnessPacket
import com.littlemine.webbrowser.network.MonitorResolutionPacket
import com.littlemine.webbrowser.network.MonitorUpdatePacket
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

class BrowserScreen(private val monitor: MonitorBlockEntity) : Screen(Component.translatable("screen.webbrowser.browser")) {
    private val LOGGER: org.slf4j.Logger = com.mojang.logging.LogUtils.getLogger()

    private lateinit var urlField: EditBox
    private lateinit var pixelsField: EditBox
    private lateinit var volumeField: EditBox
    private lateinit var brightnessField: EditBox
    private var audioMode: Int = monitor.audioMode
    private lateinit var audioButton: Button
    private var browserFocused: Boolean = false
    private var lastBrowserMouseX: Double = 0.0
    private var lastBrowserMouseY: Double = 0.0
    private var lastScreenMouseX: Double = 0.0
    private var lastScreenMouseY: Double = 0.0

    private val margin: Int = 10

    // Right panel dimensions
    private val rX: Int get() = (width * 0.7).toInt() + margin
    private val rW: Int get() = width - rX - margin

    private val previewX: Int get() = margin
    private val previewY: Int get() = margin
    private val previewDrawW: Int get() = (width * 7 / 10) - margin * 2
    private val previewDrawH: Int get() = height - margin * 2

    override fun init() {
        LOGGER.info("[BrowserScreen] init() monitor={} active={} shape={} url={} ppb={}",
            monitor.blockPos, monitor.isActive, monitor.shape, monitor.currentUrl, monitor.pixelsPerBlock)

        if (ClientBrowserCache.get(monitor.blockPos) == null) {
            val shape = monitor.shape
            if (shape != null) {
                val targetW = shape.width * monitor.pixelsPerBlock
                val targetH = shape.height * monitor.pixelsPerBlock
                LOGGER.info("[BrowserScreen] creating browser {}x{} url={}", targetW, targetH, monitor.currentUrl)
                ClientBrowserCache.recreate(monitor.blockPos, targetW, targetH, monitor.currentUrl)
            } else {
                LOGGER.warn("[BrowserScreen] no browser in cache and shape is null - cannot create browser yet")
            }
        }

        urlField = EditBox(font, rX, 40, rW, 20, Component.literal("URL"))
        urlField.setMaxLength(2048)
        urlField.value = monitor.currentUrl
        urlField.isFocused = true
        addRenderableWidget(urlField)

        pixelsField = EditBox(font, rX, 110, rW, 20, Component.literal("Pixels/Block"))
        pixelsField.value = monitor.pixelsPerBlock.toString()
        pixelsField.setFilter { s -> s.all { it.isDigit() } || s.isEmpty() }
        addRenderableWidget(pixelsField)

        addRenderableWidget(
            Button.Builder(Component.literal("Go"), { onGo() })
                .bounds(rX, 65, rW / 2 - 2, 20)
                .build()
        )

        addRenderableWidget(
            Button.Builder(Component.literal("Apply Res"), { onApplyResolution() })
                .bounds(rX + rW / 2 + 2, 65, rW / 2 - 2, 20)
                .build()
        )

        audioButton = addRenderableWidget(
            Button.Builder(Component.literal("Audio: Player"), { onCycleAudio() })
                .bounds(rX, 180, rW, 20)
                .build()
        )
        updateAudioButtonLabel()

        volumeField = EditBox(font, rX, 220, rW, 20, Component.literal("Volume"))
        volumeField.value = monitor.volume.toString()
        volumeField.setFilter { s -> s.all { it.isDigit() } || s.isEmpty() }
        addRenderableWidget(volumeField)

        addRenderableWidget(
            Button.Builder(Component.literal("Apply Audio"), { onApplyAudio() })
                .bounds(rX, 245, rW, 20)
                .build()
        )

        brightnessField = EditBox(font, rX, 285, rW, 20, Component.literal("Brightness"))
        brightnessField.value = monitor.brightness.toString()
        brightnessField.setFilter { s -> s.all { it.isDigit() } || s.isEmpty() }
        addRenderableWidget(brightnessField)

        addRenderableWidget(
            Button.Builder(Component.literal("Apply Brightness"), { onApplyBrightness() })
                .bounds(rX, 310, rW, 20)
                .build()
        )

        addRenderableWidget(
            Button.Builder(Component.literal("Disconnect Command"), { onDisconnectCommand() })
                .bounds(rX, 340, rW, 20)
                .build()
        )
    }

    private fun onGo() {
        val url = urlField.value
        monitor.currentUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
        ClientBrowserCache.get(monitor.blockPos)?.loadURL(monitor.currentUrl)
        PacketDistributor.sendToServer(MonitorUpdatePacket(
            monitor.blockPos.x, monitor.blockPos.y, monitor.blockPos.z, monitor.currentUrl
        ))
    }

    private fun onApplyResolution() {
        val value = pixelsField.value.toIntOrNull() ?: return
        val ppb = value.coerceIn(BrowserConstants.MIN_BLOCK_PIXELS, BrowserConstants.MAX_BLOCK_PIXELS)
        pixelsField.value = ppb.toString()
        if (ppb == monitor.pixelsPerBlock) return

        val shape = monitor.shape ?: return
        monitor.pixelsPerBlock = ppb
        val targetW = shape.width * ppb
        val targetH = shape.height * ppb

        ClientBrowserCache.recreate(monitor.blockPos, targetW, targetH, monitor.currentUrl)

        PacketDistributor.sendToServer(MonitorResolutionPacket(
            monitor.blockPos.x, monitor.blockPos.y, monitor.blockPos.z, ppb
        ))
    }

    private fun onCycleAudio() {
        audioMode = if (audioMode == 0) 1 else 0
        updateAudioButtonLabel()
    }

    private fun updateAudioButtonLabel() {
        val label = if (audioMode == 0) "Audio: Player" else "Audio: Center Block"
        audioButton.setMessage(Component.literal(label))
    }

    private fun onApplyAudio() {
        val vol = volumeField.value.toIntOrNull() ?: return
        val clampedVol = vol.coerceIn(0, 300)
        volumeField.value = clampedVol.toString()
        monitor.audioMode = audioMode
        monitor.volume = clampedVol
        PacketDistributor.sendToServer(MonitorAudioPacket(
            monitor.blockPos.x, monitor.blockPos.y, monitor.blockPos.z, audioMode, clampedVol
        ))
    }

    private fun onApplyBrightness() {
        val bri = brightnessField.value.toIntOrNull() ?: return
        val clampedBri = bri.coerceIn(0, 100)
        brightnessField.value = clampedBri.toString()
        monitor.brightness = clampedBri
        PacketDistributor.sendToServer(MonitorBrightnessPacket(
            monitor.blockPos.x, monitor.blockPos.y, monitor.blockPos.z, clampedBri
        ))
    }

    private fun onDisconnectCommand() {
        val player = net.minecraft.client.Minecraft.getInstance().player ?: return
        val stack = player.mainHandItem
        if (stack.item !is MonitorCommandItem) return
        if (!MonitorCommandItem.isConnected(stack)) return
        MonitorCommandItem.disconnect(stack)
        PacketDistributor.sendToServer(CommandSyncPacket(
            monitor.blockPos.x, monitor.blockPos.y, monitor.blockPos.z, false
        ))
        player.displayClientMessage(
            net.minecraft.network.chat.Component.translatable("message.webbrowser.disconnected"),
            true
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        renderBrowserPreview(guiGraphics)
        renderCursor(guiGraphics)

        guiGraphics.drawString(font, Component.literal("URL:"), rX, 28, 0xFFFFFF)
        guiGraphics.drawString(font, Component.literal("Pixels/Block:"), rX, 98, 0xFFFFFF)
        guiGraphics.drawString(font, Component.literal("Volume (0-300):"), rX, 208, 0xFFFFFF)
        guiGraphics.drawString(font, Component.literal("Brightness (0-100):"), rX, 273, 0xFFFFFF)

        if (browserFocused) {
            guiGraphics.drawString(font, Component.literal("Browser mode - Press Tab for URL"), 10, height - 20, 0x888888)
        }
    }

    private fun renderCursor(guiGraphics: GuiGraphics) {
        if (!browserFocused) return
        val area = getScaledPreviewArea() ?: return
        val (drawX, drawY, drawW, drawH) = area
        val cx = lastScreenMouseX.coerceIn(drawX.toDouble(), drawX + drawW.toDouble())
        val cy = lastScreenMouseY.coerceIn(drawY.toDouble(), drawY + drawH.toDouble())
        val ix = cx.toInt()
        val iy = cy.toInt()
        val col = 0xFFFFFFFF.toInt()
        val outline = 0xFF000000.toInt()

        // Simple arrow cursor shape (7x9 px)
        // Outline pass (offset by ±1)
        for (ox in -1..1) {
            for (oy in -1..1) {
                if (ox == 0 && oy == 0) continue
                drawArrowPixels(guiGraphics, ix + ox, iy + oy, outline)
            }
        }
        // Fill pass
        drawArrowPixels(guiGraphics, ix, iy, col)
    }

    private fun drawArrowPixels(guiGraphics: GuiGraphics, ox: Int, oy: Int, color: Int) {
        val pixels = listOf(
            0 to 0, // tip
            0 to 1, 1 to 1,
            0 to 2, 2 to 2,
            0 to 3, 3 to 3,
            0 to 4, 4 to 4,
            0 to 5, 5 to 5,
            0 to 6, 6 to 6,
            0 to 7,      7 to 7,
            0 to 8,      7 to 8,
                1 to 7, 1 to 8,
                    2 to 7, 2 to 8,
        )
        // Arrow head + shaft: tip at (0,0), extends right and down
        // Row 0: tip (0,0)
        // Row 1: shaft (0,1) + wing (1,1)
        // Row 2: shaft (0,2) + wing (2,2)
        // Row 3-6: shaft (0,3..6) + wing (3..6, 3..6)
        // Row 7: shaft (0,7) + wing (7,7) + bottom bar (1..2,7)
        // Row 8: shaft (0,8) + wing (7,8) + bottom bar (1..2,8)
        // Actually simpler: just draw a simple pointer
        // Let me just use a simple 5x7 cross cursor
        for ((dx, dy) in pixels) {
            guiGraphics.fill(ox + dx, oy + dy, ox + dx + 1, oy + dy + 1, color)
        }
    }

    private fun getBrowserDimensions(): Pair<Int, Int>? {
        val shape = monitor.shape ?: return null
        return Pair(shape.width * monitor.pixelsPerBlock, shape.height * monitor.pixelsPerBlock)
    }

    private fun getScaledPreviewArea(): Quadruple<Float, Float, Float, Float>? {
        val dims = getBrowserDimensions() ?: return null
        val (bw, bh) = dims
        if (bw <= 0 || bh <= 0) return null

        val aspect = bw.toFloat() / bh.toFloat()
        // Clamp container to available area on the left
        val maxW = ((width * 0.7).toInt() - margin * 2).coerceAtLeast(50)
        val maxH = (height - margin * 2).coerceAtLeast(50)
        val pW = previewDrawW.coerceAtMost(maxW).toFloat()
        val pH = previewDrawH.coerceAtMost(maxH).toFloat()
        val pAspect = pW / pH

        val drawW: Float
        val drawH: Float
        if (aspect > pAspect) {
            drawW = pW
            drawH = drawW / aspect
        } else {
            drawH = pH
            drawW = drawH * aspect
        }

        val drawX = previewX + (pW - drawW) / 2f
        val drawY = previewY + (pH - drawH) / 2f
        return Quadruple(drawX, drawY, drawW, drawH)
    }

    private fun renderBrowserPreview(guiGraphics: GuiGraphics) {
        val browser = ClientBrowserCache.get(monitor.blockPos)
        if (browser == null) {
            LOGGER.info("[BrowserScreen] renderBrowserPreview - no browser in cache for {}", monitor.blockPos)
            return
        }
        val area = getScaledPreviewArea()
        if (area == null) {
            LOGGER.info("[BrowserScreen] renderBrowserPreview - null scaled area (shape null?)")
            return
        }
        val (drawX, drawY, drawW, drawH) = area

        // Draw a dark background so we can see the preview area even if texture is missing
        guiGraphics.fill(
            drawX.toInt(), drawY.toInt(),
            (drawX + drawW).toInt(), (drawY + drawH).toInt(),
            0xFF222222.toInt()
        )

        val texId = browser.renderer.textureID
        LOGGER.info("[BrowserScreen] renderBrowserPreview texId={} area={}x{} isGood={}",
            texId, drawW.toInt(), drawH.toInt(), texId > 0)
        if (texId <= 0) return

        val matrix = guiGraphics.pose().last().pose()

        RenderSystem.disableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        val bri = monitor.brightness.coerceIn(1, 100) / 100f
        RenderSystem.setShaderColor(bri, bri, bri, 1f)
        RenderSystem.setShaderTexture(0, texId)
        RenderSystem.setShader(GameRenderer::getPositionTexShader)

        val bb = ByteBufferBuilder(256)
        val builder = BufferBuilder(bb, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)

        builder.addVertex(matrix, drawX, drawY, 0f).setUv(0f, 0f)
        builder.addVertex(matrix, drawX, drawY + drawH, 0f).setUv(0f, 1f)
        builder.addVertex(matrix, drawX + drawW, drawY + drawH, 0f).setUv(1f, 1f)
        builder.addVertex(matrix, drawX + drawW, drawY, 0f).setUv(1f, 0f)

        BufferUploader.drawWithShader(builder.buildOrThrow())
        RenderSystem.enableDepthTest()
    }

    private fun isInPreviewArea(mx: Double, my: Double): Boolean {
        val area = getScaledPreviewArea() ?: return false
        val (drawX, drawY, drawW, drawH) = area
        return mx >= drawX && mx <= drawX + drawW && my >= drawY && my <= drawY + drawH
    }

    private fun screenToBrowser(mx: Double, my: Double): Pair<Double, Double> {
        val area = getScaledPreviewArea() ?: return Pair(0.0, 0.0)
        val (drawX, drawY, drawW, drawH) = area
        val dims = getBrowserDimensions() ?: return Pair(0.0, 0.0)
        val (bw, bh) = dims

        val relX = ((mx - drawX) / drawW).coerceIn(0.0, 1.0)
        val relY = ((my - drawY) / drawH).coerceIn(0.0, 1.0)
        return Pair(relX * bw, relY * bh)
    }

    override fun mouseClicked(mx: Double, my: Double, button: Int): Boolean {
        if (isInPreviewArea(mx, my)) {
            browserFocused = true
            urlField.isFocused = false
            pixelsField.isFocused = false
            lastScreenMouseX = mx
            lastScreenMouseY = my
            val (bx, by) = screenToBrowser(mx, my)
            lastBrowserMouseX = bx
            lastBrowserMouseY = by
            ClientBrowserCache.get(monitor.blockPos)?.let { b ->
                b.sendMouseMove(bx.toInt(), by.toInt())
                b.sendMousePress(bx.toInt(), by.toInt(), button)
            }
            return true
        }

        if (super.mouseClicked(mx, my, button)) return true

        browserFocused = false
        return false
    }

    override fun mouseReleased(mx: Double, my: Double, button: Int): Boolean {
        if (isInPreviewArea(mx, my)) {
            lastScreenMouseX = mx
            lastScreenMouseY = my
            val (bx, by) = screenToBrowser(mx, my)
            lastBrowserMouseX = bx
            lastBrowserMouseY = by
            ClientBrowserCache.get(monitor.blockPos)?.sendMouseRelease(bx.toInt(), by.toInt(), button)
            return true
        }
        return super.mouseReleased(mx, my, button)
    }

    override fun mouseDragged(mx: Double, my: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (browserFocused) {
            lastScreenMouseX = mx
            lastScreenMouseY = my
            val (bx, by) = screenToBrowser(mx, my)
            lastBrowserMouseX = bx
            lastBrowserMouseY = by
            ClientBrowserCache.get(monitor.blockPos)?.sendMouseMove(bx.toInt(), by.toInt())
            return true
        }
        return super.mouseDragged(mx, my, button, dragX, dragY)
    }

    override fun mouseMoved(mx: Double, my: Double) {
        if (browserFocused) {
            lastScreenMouseX = mx
            lastScreenMouseY = my
            val (bx, by) = screenToBrowser(mx, my)
            lastBrowserMouseX = bx
            lastBrowserMouseY = by
            ClientBrowserCache.get(monitor.blockPos)?.sendMouseMove(bx.toInt(), by.toInt())
        }
    }

    override fun mouseScrolled(mx: Double, my: Double, scrollX: Double, scrollY: Double): Boolean {
        if (browserFocused && isInPreviewArea(mx, my)) {
            ClientBrowserCache.get(monitor.blockPos)?.sendMouseWheel(
                lastBrowserMouseX.toInt(), lastBrowserMouseY.toInt(), scrollY, 0
            )
            return true
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (browserFocused) {
            if (keyCode == 256) {
                onClose()
                return true
            }
            if (keyCode == 258) {
                browserFocused = false
                urlField.isFocused = true
                return true
            }
            ClientBrowserCache.get(monitor.blockPos)?.sendKeyPress(keyCode, scanCode.toLong(), modifiers)
            return true
        }

        if (keyCode == 258) {
            if (urlField.isFocused || pixelsField.isFocused || volumeField.isFocused || brightnessField.isFocused) {
                urlField.isFocused = false
                pixelsField.isFocused = false
                volumeField.isFocused = false
                brightnessField.isFocused = false
                browserFocused = true
                return true
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (browserFocused) {
            ClientBrowserCache.get(monitor.blockPos)?.sendKeyRelease(keyCode, scanCode.toLong(), modifiers)
            return true
        }
        return super.keyReleased(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (browserFocused) {
            ClientBrowserCache.get(monitor.blockPos)?.sendKeyTyped(codePoint, modifiers)
            return true
        }
        return super.charTyped(codePoint, modifiers)
    }

    override fun onClose() {
        InputHandler.setActiveMonitor(null)
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
