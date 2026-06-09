package com.littlemine.webbrowser.client.renderer

import com.littlemine.webbrowser.block.MonitorBlock
import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.client.BrowserConstants
import com.littlemine.webbrowser.client.ClientBrowserCache
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

class MonitorBlockEntityRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<MonitorBlockEntity> {
    private val LOGGER: org.slf4j.Logger = com.mojang.logging.LogUtils.getLogger()

    companion object {
        private val renderTypeCache = mutableMapOf<Int, RenderType>()

        fun clearRenderTypeCache() {
            val count = renderTypeCache.size
            renderTypeCache.clear()
            // LOGGER is not accessible in companion, use the one from the class
        }

        private fun getOrCreateRenderType(textureId: Int): RenderType =
            renderTypeCache.getOrPut(textureId) { createMCEFRenderType { textureId } }
    }

    override fun shouldRenderOffScreen(entity: MonitorBlockEntity): Boolean = true

    override fun getViewDistance(): Int = BrowserConstants.VIEW_DISTANCE_BLOCKS

    override fun render(
        entity: MonitorBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        if (!entity.isActive) {
            LOGGER.trace("[Renderer] skip inactive at {}", entity.blockPos)
            return
        }
        val master = entity.master()
        val browser = ClientBrowserCache.get(master.blockPos)
        if (browser == null) {
            LOGGER.trace("[Renderer] no browser for master {} at {}", master.blockPos, entity.blockPos)
            return
        }

        val state = entity.blockState
        val facing = state.getValue(MonitorBlock.FACING)
        val shape = entity.shape ?: master.shape
        if (shape == null) {
            LOGGER.trace("[Renderer] no shape for {}", entity.blockPos)
            return
        }

        val relX = entity.blockPos.x - shape.topLeft.x
        val relY = entity.blockPos.y - shape.topLeft.y
        val relZ = entity.blockPos.z - shape.topLeft.z

        val col: Int
        val row: Int
        when (facing) {
            Direction.NORTH -> { col = relX; row = -relY }
            Direction.SOUTH -> { col = -relX; row = -relY }
            Direction.EAST -> { col = relZ; row = -relY }
            Direction.WEST -> { col = -relZ; row = -relY }
            Direction.UP -> { col = relX; row = relZ }
            Direction.DOWN -> { col = relX; row = relZ }
            else -> return
        }

        // MCEF texture is horizontally mirrored - flip U coordinates
        val uMin = (shape.width - 1 - col).toFloat() / shape.width
        val uMax = (shape.width - col).toFloat() / shape.width
        val vMin = row.toFloat() / shape.height
        val vMax = (row + 1).toFloat() / shape.height

        val textureId = browser.renderer.textureID
        LOGGER.trace("[Renderer] {} col={} row={} uv=[{},{},{},{}] texId={}",
            entity.blockPos, col, row, uMin, uMax, vMin, vMax, textureId)

        val renderType = getOrCreateRenderType(textureId)
        renderType.setupRenderState()

        val byteBuffer = ByteBufferBuilder(256)
        val builder = BufferBuilder(byteBuffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY)

        val vertices: Array<Vector4f>
        val normal: Vector3f
        val swapU: Boolean
        val swapV: Boolean

        when (facing) {
            Direction.NORTH -> {
                vertices = arrayOf(
                    Vector4f(0f, 0f, 0f, 1f),
                    Vector4f(1f, 0f, 0f, 1f),
                    Vector4f(1f, 1f, 0f, 1f),
                    Vector4f(0f, 1f, 0f, 1f)
                )
                normal = Vector3f(0f, 0f, -1f)
                swapU = false; swapV = false
            }
            Direction.SOUTH -> {
                vertices = arrayOf(
                    Vector4f(0f, 0f, 1f, 1f),
                    Vector4f(1f, 0f, 1f, 1f),
                    Vector4f(1f, 1f, 1f, 1f),
                    Vector4f(0f, 1f, 1f, 1f)
                )
                normal = Vector3f(0f, 0f, 1f)
                swapU = true; swapV = false
            }
            Direction.EAST -> {
                vertices = arrayOf(
                    Vector4f(1f, 0f, 0f, 1f),
                    Vector4f(1f, 0f, 1f, 1f),
                    Vector4f(1f, 1f, 1f, 1f),
                    Vector4f(1f, 1f, 0f, 1f)
                )
                normal = Vector3f(1f, 0f, 0f)
                swapU = true; swapV = false
            }
            Direction.WEST -> {
                vertices = arrayOf(
                    Vector4f(0f, 0f, 0f, 1f),
                    Vector4f(0f, 0f, 1f, 1f),
                    Vector4f(0f, 1f, 1f, 1f),
                    Vector4f(0f, 1f, 0f, 1f)
                )
                normal = Vector3f(-1f, 0f, 0f)
                swapU = false; swapV = false
            }
            Direction.UP -> {
                vertices = arrayOf(
                    Vector4f(0f, 1f, 0f, 1f),
                    Vector4f(1f, 1f, 0f, 1f),
                    Vector4f(1f, 1f, 1f, 1f),
                    Vector4f(0f, 1f, 1f, 1f)
                )
                normal = Vector3f(0f, 1f, 0f)
                swapU = false; swapV = true
            }
            Direction.DOWN -> {
                vertices = arrayOf(
                    Vector4f(0f, 0f, 0f, 1f),
                    Vector4f(1f, 0f, 0f, 1f),
                    Vector4f(1f, 0f, 1f, 1f),
                    Vector4f(0f, 0f, 1f, 1f)
                )
                normal = Vector3f(0f, -1f, 0f)
                swapU = true; swapV = true
            }
        }

        val finalUMin = if (swapU) uMax else uMin
        val finalUMax = if (swapU) uMin else uMax
        val finalVMin = if (swapV) vMax else vMin
        val finalVMax = if (swapV) vMin else vMax

        val pose = poseStack.last()
        val matrix = pose.pose()
        val n = Vector3f(normal)
        pose.normal().transform(n)

        drawVertex(builder, matrix, vertices[0], finalUMin, finalVMax, n, entity)
        drawVertex(builder, matrix, vertices[1], finalUMax, finalVMax, n, entity)
        drawVertex(builder, matrix, vertices[2], finalUMax, finalVMin, n, entity)
        drawVertex(builder, matrix, vertices[3], finalUMin, finalVMin, n, entity)

        BufferUploader.drawWithShader(builder.buildOrThrow())

        renderType.clearRenderState()
    }

    private fun drawVertex(
        builder: BufferBuilder, matrix: Matrix4f,
        localVertex: Vector4f, u: Float, v: Float, normal: Vector3f,
        entity: MonitorBlockEntity? = null
    ) {
        val worldPos = Vector4f(localVertex).mul(matrix)
        val b = ((entity?.master()?.brightness ?: 100).coerceIn(1, 500) * 255 / 500).coerceIn(0, 255)
        val color = (0xFF shl 24) or (b shl 16) or (b shl 8) or b
        builder.addVertex(worldPos.x, worldPos.y, worldPos.z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(15728880)
            .setNormal(normal.x(), normal.y(), normal.z())
    }
}
