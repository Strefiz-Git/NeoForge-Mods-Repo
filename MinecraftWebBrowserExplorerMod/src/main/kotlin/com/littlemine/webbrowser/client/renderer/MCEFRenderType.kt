package com.littlemine.webbrowser.client.renderer

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType

class MCEFBrowserTextureShard(textureId: () -> Int) : RenderStateShard.EmptyTextureStateShard(
    { RenderSystem.bindTexture(textureId()) },
    { }
)

@Suppress("INVISIBLE_REFERENCE")
fun createMCEFRenderType(textureId: () -> Int): RenderType {
    val state = RenderType.CompositeState.builder()
        .setTextureState(MCEFBrowserTextureShard(textureId))
        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_SHADER)
        .setLightmapState(RenderStateShard.NO_LIGHTMAP)
        .setOverlayState(RenderStateShard.NO_OVERLAY)
        .createCompositeState(false)
    return RenderType.create(
        "mcef_browser",
        DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
        VertexFormat.Mode.QUADS,
        256,
        false,
        false,
        state
    ) as RenderType
}
