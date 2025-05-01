package com.namedloot;

import com.namedloot.NamedLootClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

public class WorldRenderEventHandler {

    public static void registerEvents() {
        // Register the event that fires after entities are rendered
        WorldRenderEvents.AFTER_TRANSLUCENT.register((context) -> {
            MinecraftClient client = MinecraftClient.getInstance();

            // Skip if game is paused or no world is loaded
            if (client.isPaused() || client.world == null) {
                return;
            }

            // Get all item entities within range
            List<ItemEntity> itemEntitiesToRender = new ArrayList<>();

            // Loop through all entities directly for compatibility
            for (ItemEntity entity : client.world.getEntitiesByClass(ItemEntity.class,
                    // Use a box around the camera to find item entities
                    new Box(client.gameRenderer.getCamera().getBlockPos()).expand(
                            // Use max distance from config, or default to 64 blocks
                            NamedLootClient.CONFIG.displayDistance > 0 ?
                                    NamedLootClient.CONFIG.displayDistance : 64),
                    // Simple predicate that accepts all item entities
                    itemEntity -> true)) {

                // Apply distance check if needed
                if (NamedLootClient.CONFIG.displayDistance > 0) {
                    double distance = client.gameRenderer.getCamera().getPos().distanceTo(entity.getPos());
                    if (distance > NamedLootClient.CONFIG.displayDistance) {
                        continue;
                    }
                }

                itemEntitiesToRender.add(entity);
            }

            // Skip if no entities to render
            if (itemEntitiesToRender.isEmpty()) {
                return;
            }

            // Get render state
            MatrixStack matrices = context.matrixStack();
            float tickDelta = context.tickCounter().getTickDelta(true);
            TextRenderer textRenderer = client.textRenderer;
            VertexConsumerProvider vertexConsumers = context.consumers();

            // Render all item name tags
            for (ItemEntity entity : itemEntitiesToRender) {
                renderItemNameTag(entity, matrices, vertexConsumers, client, textRenderer, tickDelta);
            }

        });
    }

    private static void renderItemNameTag(ItemEntity entity, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers,
                                          MinecraftClient client, TextRenderer textRenderer,
                                          float tickDelta) {
        // Get item count
        int count = entity.getStack().getCount();

        // Get the name
        String itemName = entity.getStack().getName().getString();
        String countText = String.valueOf(count);

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

        // Create formatted text with the same approach as your original code
        MutableText formattedText = Text.literal("");
        String format = NamedLootClient.CONFIG.textFormat;

        // Process format string in segments
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

        // Setup for rendering
        matrices.push();

        // Get entity's interpolated position
        double x = entity.prevX + (entity.getX() - entity.prevX) * tickDelta;
        double y = entity.prevY + (entity.getY() - entity.prevY) * tickDelta;
        double z = entity.prevZ + (entity.getZ() - entity.prevZ) * tickDelta;

        // Set camera-relative position
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        matrices.translate(x - cameraPos.x, y - cameraPos.y + entity.getHeight() + NamedLootClient.CONFIG.verticalOffset, z - cameraPos.z);

        // Face camera
        float cameraYaw = client.gameRenderer.getCamera().getYaw();
        float cameraPitch = client.gameRenderer.getCamera().getPitch();

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));

        // Scale the text appropriately
        matrices.scale(-0.025F, -0.025F, -0.025F);

        float textOffset = -textRenderer.getWidth(formattedText) / 2.0F;

        // Draw text with a SEE_THROUGH layer type to ensure visibility
        textRenderer.draw(
                formattedText,
                textOffset,
                0,
                0xFFFFFFFF, // Full brightness
                false,
                matrices.peek().getPositionMatrix(),
                vertexConsumers,
                TextRenderer.TextLayerType.SEE_THROUGH,
                0x50000000, // No background
                0xF000F0 // Full brightness light
        );

        matrices.pop();
    }
}