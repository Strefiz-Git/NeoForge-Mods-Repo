package com.littlemine.webbrowser.client.renderer

import com.littlemine.webbrowser.block.MonitorBlock
import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.client.ClientBrowserCache
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

class MonitorBlockEntityRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<MonitorBlockEntity> {

    override fun render(
        entity: MonitorBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        if (!entity.isActive) return
        val master = entity.master()
        val browser = ClientBrowserCache.get(master.blockPos) ?: return

        val state = entity.blockState
        val facing = state.getValue(MonitorBlock.FACING)
        val shape = entity.shape ?: return

        val relX = entity.blockPos.x - shape.topLeft.x
        val relY = entity.blockPos.y - shape.topLeft.y
        val relZ = entity.blockPos.z - shape.topLeft.z

        val col: Int
        val row: Int
        when (facing) {
            Direction.NORTH -> { col = -relX; row = relY }
            Direction.SOUTH -> { col = relX; row = relY }
            Direction.EAST -> { col = relZ; row = relY }
            Direction.WEST -> { col = -relZ; row = relY }
            Direction.UP -> { col = relX; row = -relZ }
            Direction.DOWN -> { col = relX; row = relZ }
            else -> return
        }

        val uMin = col.toFloat() / shape.width
        val uMax = (col + 1).toFloat() / shape.width
        val vMin = row.toFloat() / shape.height
        val vMax = (row + 1).toFloat() / shape.height

        val textureId = browser.renderer.textureID
        val renderType = createMCEFRenderType { textureId }
        val buffer = bufferSource.getBuffer(renderType)

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
        val vertices = arrayOf(
            Vector4f(0f, 0f, 0.005f, 1f),
            Vector4f(1f, 0f, 0.005f, 1f),
            Vector4f(1f, 1f, 0.005f, 1f),
            Vector4f(0f, 1f, 0.005f, 1f)
        )
        var normal = Vector3f(0f, 0f, 1f)

        when (facing) {
            Direction.SOUTH -> {
                for (v in vertices) { v.x = 1f - v.x; v.z = 1f - 0.005f }
                normal = Vector3f(0f, 0f, -1f)
            }
            Direction.EAST -> {
                for (v in vertices) { val tx = v.x; v.x = v.z; v.z = 1f - tx; v.z = 1f - 0.005f }
                normal = Vector3f(-1f, 0f, 0f)
            }
            Direction.WEST -> {
                for (v in vertices) { val tz = v.z; v.z = v.x; v.x = tz; v.z = 0.005f }
                normal = Vector3f(1f, 0f, 0f)
            }
            Direction.UP -> {
                for (v in vertices) { val ty = v.y; v.y = v.z; v.z = 1f - ty; v.y = 1f - 0.005f }
                normal = Vector3f(0f, -1f, 0f)
            }
            Direction.DOWN -> {
                for (v in vertices) { val ty = v.y; v.y = v.z; v.z = ty; v.y = 0.005f }
                normal = Vector3f(0f, 1f, 0f)
            }
            else -> {}
        }

        addQuad(buffer, poseStack, vertices[0], vertices[1], vertices[2], vertices[3],
            uMin, uMax, vMin, vMax, normal)
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
