package com.littlemine.webbrowser.client.renderer

import com.littlemine.webbrowser.block.MonitorBlock
import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.client.ClientBrowserCache
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
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
            renderTypeCache.clear()
        }

        private fun getOrCreateRenderType(textureId: Int): RenderType =
            renderTypeCache.getOrPut(textureId) { createMCEFRenderType { textureId } }
    }

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
            LOGGER.trace("[Renderer] no browser for master {}", master.blockPos)
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

        val uMin = col.toFloat() / shape.width
        val uMax = (col + 1).toFloat() / shape.width
        val vMin = row.toFloat() / shape.height
        val vMax = (row + 1).toFloat() / shape.height

        val textureId = browser.renderer.textureID
        if (textureId == 0) {
            LOGGER.debug("[Renderer] {} texture ID is 0 (browser not ready yet)", entity.blockPos)
            return
        }
        LOGGER.trace("[Renderer] {} col={} row={} uv=[{},{}]x[{},{}] texId={}",
            entity.blockPos, col, row, uMin, uMax, vMin, vMax, textureId)
        val buffer = bufferSource.getBuffer(getOrCreateRenderType(textureId))

        poseStack.pushPose()
        renderScreenFace(poseStack, buffer, facing, uMin, uMax, vMin, vMax)
        poseStack.popPose()
    }

    private fun renderScreenFace(
        poseStack: PoseStack,
        buffer: VertexConsumer,
        facing: Direction,
        uMin: Float, uMax: Float,
        vMin: Float, vMax: Float
    ) {
        val vertices: Array<Vector4f>
        val normal: Vector3f
        val swapU: Boolean
        val swapV: Boolean

        when (facing) {
            Direction.NORTH -> {
                // Face at z=0, quad slightly OUTSIDE at z=-0.005
                // Camera at -Z: x=0=left, x=1=right, y=0=bottom, y=1=top
                vertices = arrayOf(
                    Vector4f(0f, 0f, -0.005f, 1f),
                    Vector4f(1f, 0f, -0.005f, 1f),
                    Vector4f(1f, 1f, -0.005f, 1f),
                    Vector4f(0f, 1f, -0.005f, 1f)
                )
                normal = Vector3f(0f, 0f, -1f)
                swapU = false; swapV = false
            }
            Direction.SOUTH -> {
                // Face at z=1, quad slightly OUTSIDE at z=1.005
                // Camera at +Z: x=1=left, x=0=right (mirrored), so swap U
                vertices = arrayOf(
                    Vector4f(0f, 0f, 1.005f, 1f),
                    Vector4f(1f, 0f, 1.005f, 1f),
                    Vector4f(1f, 1f, 1.005f, 1f),
                    Vector4f(0f, 1f, 1.005f, 1f)
                )
                normal = Vector3f(0f, 0f, 1f)
                swapU = true; swapV = false
            }
            Direction.EAST -> {
                // Face at x=1, quad slightly OUTSIDE at x=1.005
                // Camera at +X: z=1=left, z=0=right (mirrored), so swap U
                vertices = arrayOf(
                    Vector4f(1.005f, 0f, 0f, 1f),
                    Vector4f(1.005f, 0f, 1f, 1f),
                    Vector4f(1.005f, 1f, 1f, 1f),
                    Vector4f(1.005f, 1f, 0f, 1f)
                )
                normal = Vector3f(1f, 0f, 0f)
                swapU = true; swapV = false
            }
            Direction.WEST -> {
                // Face at x=0, quad slightly OUTSIDE at x=-0.005
                // Camera at -X: z=0=left, z=1=right (standard), no swap
                vertices = arrayOf(
                    Vector4f(-0.005f, 0f, 0f, 1f),
                    Vector4f(-0.005f, 0f, 1f, 1f),
                    Vector4f(-0.005f, 1f, 1f, 1f),
                    Vector4f(-0.005f, 1f, 0f, 1f)
                )
                normal = Vector3f(-1f, 0f, 0f)
                swapU = false; swapV = false
            }
            Direction.UP -> {
                // Face at y=1, quad slightly OUTSIDE at y=1.005
                // Camera at +Y (above): x=0=left, x=1=right, z=0=north(top), z=1=south(bottom)
                // UP uses Z as vertical axis, so swap V to fix orientation
                vertices = arrayOf(
                    Vector4f(0f, 1.005f, 0f, 1f),
                    Vector4f(1f, 1.005f, 0f, 1f),
                    Vector4f(1f, 1.005f, 1f, 1f),
                    Vector4f(0f, 1.005f, 1f, 1f)
                )
                normal = Vector3f(0f, 1f, 0f)
                swapU = false; swapV = true
            }
            Direction.DOWN -> {
                // Face at y=0, quad slightly OUTSIDE at y=-0.005
                // Camera at -Y (below): x=0=left, x=1=right, z=0=north(top), z=1=south(bottom)
                // From below: mirror U, and Z is vertical so swap V
                vertices = arrayOf(
                    Vector4f(0f, -0.005f, 0f, 1f),
                    Vector4f(1f, -0.005f, 0f, 1f),
                    Vector4f(1f, -0.005f, 1f, 1f),
                    Vector4f(0f, -0.005f, 1f, 1f)
                )
                normal = Vector3f(0f, -1f, 0f)
                swapU = true; swapV = true
            }
        }

        val finalUMin = if (swapU) uMax else uMin
        val finalUMax = if (swapU) uMin else uMax
        val finalVMin = if (swapV) vMax else vMin
        val finalVMax = if (swapV) vMin else vMax

        addQuad(buffer, poseStack, vertices[0], vertices[1], vertices[2], vertices[3],
            finalUMin, finalUMax, finalVMin, finalVMax, normal)
    }

    private fun addQuad(
        buffer: VertexConsumer, poseStack: PoseStack,
        v0: Vector4f, v1: Vector4f, v2: Vector4f, v3: Vector4f,
        uMin: Float, uMax: Float, vMin: Float, vMax: Float,
        normal: Vector3f
    ) {
        val pose = poseStack.last()
        val matrix = pose.pose()
        val n = Vector3f(normal)
        pose.normal().transform(n)

        addVertex(buffer, matrix, v0, uMin, vMax, n)
        addVertex(buffer, matrix, v1, uMax, vMax, n)
        addVertex(buffer, matrix, v2, uMax, vMin, n)
        addVertex(buffer, matrix, v3, uMin, vMin, n)
    }

    private fun addVertex(
        buffer: VertexConsumer, matrix: Matrix4f,
        vertex: Vector4f, u: Float, v: Float, normal: Vector3f
    ) {
        val t = Vector4f(vertex).mul(matrix)
        buffer.addVertex(t.x, t.y, t.z)
            .setColor(1f, 1f, 1f, 1f)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(15728880)
            .setNormal(normal.x(), normal.y(), normal.z())
    }
}
