package com.namedloot.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

@Mixin(GlyphRenderTypes.class)
public class MixinGlyphRenderTypes {

	private static RenderPipeline namedLootTextPipeline = null;
	private static boolean irisRegistered = false;

	private static RenderType createSafeSeeThrough(Identifier textureId) {
		if (namedLootTextPipeline == null) {
			namedLootTextPipeline = RenderPipeline.builder()
					.withLocation("pipeline/namedloot_text_see_through")
					.withVertexShader("core/text")
					.withFragmentShader("core/text")
					.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
					.withBindGroupLayout(BindGroupLayouts.FOG)
					.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
					.withBindGroupLayout(BindGroupLayouts.SAMPLER2)
					.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR)
					.withPrimitiveTopology(PrimitiveTopology.QUADS)
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(Optional.empty())
					.build();

			if (!irisRegistered) {
				irisRegistered = true;
				try {
					Class<?> irisPipelinesClass = Class.forName("net.irisshaders.iris.pipeline.IrisPipelines");
					java.lang.reflect.Method copyPipeline = irisPipelinesClass.getMethod("copyPipeline", RenderPipeline.class, RenderPipeline.class);
					copyPipeline.invoke(null, RenderPipelines.TEXT, namedLootTextPipeline);
				} catch (Throwable ignored) {
				}
			}
		}
		return RenderType.create("namedloot_text_see_through",
				RenderSetup.builder(namedLootTextPipeline)
						.withTexture("Sampler0", textureId)
						.useLightmap()
						.createRenderSetup());
	}

	@Redirect(method = "createForColorTexture(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/gui/font/GlyphRenderTypes;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;textSeeThrough(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
	private static RenderType namedloot$useSafeSeeThrough(Identifier textureId) {
		return createSafeSeeThrough(textureId);
	}
}
