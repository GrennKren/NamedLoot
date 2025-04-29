package com.namedloot.mixin.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.text.Text;
import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.RotationAxis;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRenderMixin extends EntityRenderer<ItemEntity> {

    protected ItemEntityRenderMixin(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Inject(method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("TAIL"))
    private void renderItemName(ItemEntity itemEntity, float f, float g, MatrixStack matrixStack,
                                VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo ci) {
        // Get item count
        int count = itemEntity.getStack().getCount();

        // Create text to display (item name x count)
        String displayText = itemEntity.getStack().getName().getString();
        if (count > 1) {
            displayText += " x" + count;
        }

        // Render text above item
        matrixStack.push();

        // Position the text above the item
        matrixStack.translate(0, itemEntity.getHeight() + 0.5F, 0);

        // Get the camera direction to make text face the player
        // This rotates the text to face the camera
        MinecraftClient client = MinecraftClient.getInstance();
        float cameraYaw = client.gameRenderer.getCamera().getYaw();
        float cameraPitch = client.gameRenderer.getCamera().getPitch();

        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));

        // Scale the text appropriately
        matrixStack.scale(-0.025F, -0.025F, 0.025F);

        TextRenderer textRenderer = this.getTextRenderer();
        float textOffset = -textRenderer.getWidth(displayText) / 2.0F;

        textRenderer.draw(Text.of(displayText), textOffset, 0, 0xFFFFFFFF,
                false, matrixStack.peek().getPositionMatrix(),
                vertexConsumerProvider, TextRenderer.TextLayerType.NORMAL,
                0x00000000, i);

        matrixStack.pop();
    }
}