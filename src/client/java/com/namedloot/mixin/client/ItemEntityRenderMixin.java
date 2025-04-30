package com.namedloot.mixin.client;

import com.namedloot.NamedLootClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.RotationAxis;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRenderMixin extends EntityRenderer<ItemEntity, EntityRenderState> {

    @Unique
    private ItemEntity capturedItemEntity = null;

    protected ItemEntityRenderMixin(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Inject(method = "updateRenderState*", at = @At("TAIL"))
    private void captureItemEntity(ItemEntity itemEntity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        // Simpan referensi entity jika diperlukan untuk render kustom.
        this.capturedItemEntity = itemEntity;
    }


    @Inject(method = "render*", at = @At("TAIL"))
    private void renderItemName(ItemEntityRenderState state, MatrixStack matrixStack,
                                VertexConsumerProvider vertexConsumerProvider, int light, CallbackInfo ci) {
        if (this.capturedItemEntity == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        // Check distance if configured
        if (NamedLootClient.CONFIG.displayDistance > 0) {
            double distance = client.gameRenderer.getCamera().getPos().distanceTo(this.capturedItemEntity.getPos());
            if (distance > NamedLootClient.CONFIG.displayDistance) {
                return;
            }
        }

        // Get item count
        int count = this.capturedItemEntity.getStack().getCount();

        // Get the name
        String itemName = this.capturedItemEntity.getStack().getName().getString();
        String countText = String.valueOf(count);

        // Create text components with appropriate styles
        // Set name color
        int nameRed = (int)(NamedLootClient.CONFIG.nameRed * 255);
        int nameGreen = (int)(NamedLootClient.CONFIG.nameGreen * 255);
        int nameBlue = (int)(NamedLootClient.CONFIG.nameBlue * 255);
        int nameColor = (nameRed << 16) | (nameGreen << 8) | nameBlue;

        // Set count color
        int countRed = (int)(NamedLootClient.CONFIG.countRed * 255);
        int countGreen = (int)(NamedLootClient.CONFIG.countGreen * 255);
        int countBlue = (int)(NamedLootClient.CONFIG.countBlue * 255);
        int countColor = (countRed << 16) | (countGreen << 8) | countBlue;

        // Create the formatted text
        MutableText formattedText = Text.literal("");

        // Get the format string and process it correctly
        String format = NamedLootClient.CONFIG.textFormat;

        // Split by {name} and {count} placeholders
        // This approach processes the format string in segments
        int currentIndex = 0;
        int nameIndex, countIndex;

        while (currentIndex < format.length()) {
            nameIndex = format.indexOf("{name}", currentIndex);
            countIndex = format.indexOf("{count}", currentIndex);

            // Find which comes first
            if (nameIndex == -1 && countIndex == -1) {
                // No more placeholders, add remaining text
                formattedText.append(Text.literal(format.substring(currentIndex)));
                break;
            } else if (nameIndex != -1 && (countIndex == -1 || nameIndex < countIndex)) {
                // Name comes next
                // Add text before the placeholder
                if (nameIndex > currentIndex) {
                    formattedText.append(Text.literal(format.substring(currentIndex, nameIndex)));
                }
                // Add the name with its color
                formattedText.append(Text.literal(itemName).setStyle(Style.EMPTY.withColor(nameColor)));
                currentIndex = nameIndex + 6; // Skip over "{name}"
            } else {
                // Count comes next
                // Add text before the placeholder
                if (countIndex > currentIndex) {
                    formattedText.append(Text.literal(format.substring(currentIndex, countIndex)));
                }
                // Add the count with its color
                formattedText.append(Text.literal(countText).setStyle(Style.EMPTY.withColor(countColor)));
                currentIndex = countIndex + 7; // Skip over "{count}"
            }
        }

        // Render text above item
        matrixStack.push();

        // Position the text with configurable offset
        matrixStack.translate(0, this.capturedItemEntity.getHeight() + NamedLootClient.CONFIG.verticalOffset, 0);

        // Make text face the camera
        float cameraYaw = client.gameRenderer.getCamera().getYaw();
        float cameraPitch = client.gameRenderer.getCamera().getPitch();

        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));

        // Scale the text
        matrixStack.scale(-0.025F, -0.025F, 0.025F);

        TextRenderer textRenderer = this.getTextRenderer();
        float textOffset = -textRenderer.getWidth(formattedText) / 2.0F;

        textRenderer.draw(formattedText, textOffset, 0, 0xFFFFFFFF,
                false, matrixStack.peek().getPositionMatrix(),
                vertexConsumerProvider, TextRenderer.TextLayerType.NORMAL,
                0x00000000, light);

        matrixStack.pop();
    }
}