package com.littlemine.webbrowser.client.gui

import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.client.BrowserConstants
import com.littlemine.webbrowser.client.ClientBrowserCache
import com.littlemine.webbrowser.client.input.InputHandler
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
    private lateinit var previewWField: EditBox
    private lateinit var previewHField: EditBox
    private var browserFocused: Boolean = false
    private var lastBrowserMouseX: Double = 0.0
    private var lastBrowserMouseY: Double = 0.0

    private val margin: Int = 10

    // Right panel dimensions
    private val rX: Int get() = (width * 0.7).toInt() + margin
    private val rW: Int get() = width - rX - margin

    // Preview dimensions — defaults 70% width, full height
    private var previewW: Int = 0
    private var previewH: Int = 0

    private val previewX: Int get() = margin
    private val previewY: Int get() = margin
    private val previewDrawW: Int get() = if (previewW > 0) previewW else (width * 7 / 10) - margin * 2
    private val previewDrawH: Int get() = if (previewH > 0) previewH else height - margin * 2

    override fun init() {
        LOGGER.info("[BrowserScreen] init() monitor={} active={} shape={} url={} ppb={}",
            monitor.blockPos, monitor.isActive, monitor.shape, monitor.currentUrl, monitor.pixelsPerBlock)
        previewW = 0
        previewH = 0

        urlField = EditBox(font, rX, 40, rW, 20, Component.literal("URL"))
        urlField.setMaxLength(2048)
        urlField.value = monitor.currentUrl
        urlField.isFocused = true
        addRenderableWidget(urlField)

        pixelsField = EditBox(font, rX, 110, rW, 20, Component.literal("Pixels/Block"))
        pixelsField.value = monitor.pixelsPerBlock.toString()
        pixelsField.setFilter { s -> s.all { it.isDigit() } || s.isEmpty() }
        addRenderableWidget(pixelsField)

        previewWField = EditBox(font, rX, 180, rW, 20, Component.literal("Preview Width"))
        previewWField.value = previewDrawW.toString()
        previewWField.setFilter { s -> s.all { it.isDigit() } || s.isEmpty() }
        addRenderableWidget(previewWField)

        previewHField = EditBox(font, rX, 220, rW, 20, Component.literal("Preview Height"))
        previewHField.value = previewDrawH.toString()
        previewHField.setFilter { s -> s.all { it.isDigit() } || s.isEmpty() }
        addRenderableWidget(previewHField)

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

        addRenderableWidget(
            Button.Builder(Component.literal("Apply Preview"), { onApplyPreviewSize() })
                .bounds(rX, 245, rW, 20)
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

    private fun onApplyPreviewSize() {
        val w = previewWField.value.toIntOrNull() ?: return
        val h = previewHField.value.toIntOrNull() ?: return
        if (w > 0 && h > 0) {
            previewW = w
            previewH = h
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        renderBrowserPreview(guiGraphics)

        guiGraphics.drawString(font, Component.literal("URL:"), rX, 28, 0xFFFFFF)
        guiGraphics.drawString(font, Component.literal("Pixels/Block:"), rX, 98, 0xFFFFFF)
        guiGraphics.drawString(font, Component.literal("Preview W:"), rX, 168, 0xFFFFFF)
        guiGraphics.drawString(font, Component.literal("Preview H:"), rX, 208, 0xFFFFFF)

        if (browserFocused) {
            guiGraphics.drawString(font, Component.literal("Browser mode - Press Tab for URL"), 10, height - 20, 0x888888)
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
        val pW = previewDrawW.toFloat()
        val pH = previewDrawH.toFloat()
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

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.bindTexture(texId)
        RenderSystem.setShader(GameRenderer::getPositionTexShader)

        val bb = ByteBufferBuilder(256)
        val builder = BufferBuilder(bb, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)

        // CEF mirror → flip U; GUI Y increases downward → V as-is (0=top in UV)
        builder.addVertex(matrix, drawX, drawY, 0f).setUv(1f, 0f)
        builder.addVertex(matrix, drawX, drawY + drawH, 0f).setUv(1f, 1f)
        builder.addVertex(matrix, drawX + drawW, drawY + drawH, 0f).setUv(0f, 1f)
        builder.addVertex(matrix, drawX + drawW, drawY, 0f).setUv(0f, 0f)

        BufferUploader.drawWithShader(builder.buildOrThrow())
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
            if (keyCode == 258) {
                browserFocused = false
                urlField.isFocused = true
                return true
            }
            ClientBrowserCache.get(monitor.blockPos)?.sendKeyPress(keyCode, scanCode.toLong(), modifiers)
            return true
        }

        if (keyCode == 258) {
            if (urlField.isFocused || pixelsField.isFocused || previewWField.isFocused || previewHField.isFocused) {
                urlField.isFocused = false
                pixelsField.isFocused = false
                previewWField.isFocused = false
                previewHField.isFocused = false
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
