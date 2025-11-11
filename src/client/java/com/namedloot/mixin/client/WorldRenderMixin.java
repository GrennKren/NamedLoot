package com.namedloot.mixin.client;

import com.namedloot.WorldRenderEventHandler;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.buffers.GpuBufferSlice;

@Mixin(WorldRenderer.class)
public class WorldRenderMixin {

    //@Inject(method = "render", at = @At("RETURN"))
    @Inject(method = "render", at = @At("HEAD"))
    //@Inject(method = "render", at = @At("CONSTANT"))
    private void onRenderEnd(
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f matrix4f,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo ci
    ) {
        WorldRenderEventHandler.renderItemNameTags(camera, tickCounter);
    }
}