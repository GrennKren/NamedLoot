package com.namedloot;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Box;
import net.minecraft.enchantment.EnchantmentHelper;

import java.util.*;

import net.minecraft.registry.entry.RegistryEntry;
import org.joml.Matrix4f;

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
        String countText = String.valueOf(count);

        // Create formatted text based on whether we're using manual formatting or automatic coloring
        MutableText formattedText;

        if (NamedLootClient.CONFIG.useManualFormatting) {
            // Use manual formatting with & codes
            String itemName = entity.getStack().getName().getString();
            formattedText = parseFormattedText(NamedLootClient.CONFIG.textFormat, itemName, countText);
        } else {
            // Use our custom styling
            String itemName = entity.getStack().getName().getString();
            formattedText = createAutomaticFormattedText(itemName, countText);
        }

        // Rest of the method remains unchanged
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

        // Get enchantment details if needed
        List<Text> details = new ArrayList<>();
        if (NamedLootClient.CONFIG.showDetails) {
            Set<RegistryEntry<Enchantment>> enchantmentEntries = EnchantmentHelper.getEnchantments(entity.getStack()).getEnchantments();
            for (RegistryEntry<Enchantment> enchantmentEntry : enchantmentEntries) {
                int level = EnchantmentHelper.getEnchantments(entity.getStack()).getLevel(enchantmentEntry);
                Text enchantmentName = Enchantment.getName(enchantmentEntry, level);
                details.add(enchantmentName);
            }
        }

        // Draw text with the configured layer type and background
        textRenderer.draw(
                formattedText,
                textOffset,
                NamedLootClient.CONFIG.showDetails && !details.isEmpty() ? -(details.size() * 10) - 10 : 0,
                0xFFFFFFFF, // Full brightness
                false,
                matrices.peek().getPositionMatrix(),
                vertexConsumers,
                NamedLootClient.CONFIG.useSeeThrough ?
                        TextRenderer.TextLayerType.SEE_THROUGH :
                        TextRenderer.TextLayerType.NORMAL,
                NamedLootClient.CONFIG.useBackgroundColor ?
                        NamedLootClient.CONFIG.backgroundColor :
                        0x00000000, // Background color if enabled, otherwise transparent
                0xF000F0 // Full brightness light
        );

        // Draw details background if needed
        if (NamedLootClient.CONFIG.showDetails && !details.isEmpty()) {
            int lineHeight = 10;
            int padding = 2;

            int maxWidth = details.stream()
                    .mapToInt(textRenderer::getWidth)
                    .max()
                    .orElse(0);

            float xOffset = textOffset - padding;
            float yOffset = -(details.size() * lineHeight);
            float width = maxWidth + padding * 2;
            float height = (details.size() * lineHeight) + padding;

            drawBackgroundBox(matrices, vertexConsumers, xOffset, yOffset, xOffset + width, yOffset + height, NamedLootClient.CONFIG.backgroundColor);
        }

        // Render details if enabled
        if (NamedLootClient.CONFIG.showDetails && EnchantmentHelper.hasEnchantments(entity.getStack())) {
            float yOffset = 0;
            int detailColor = 0xAAAAAA;

            for (Text detail : details) {
                textRenderer.draw(
                        detail,
                        textOffset,
                        -(details.size() * 10) + 2 + yOffset,
                        detailColor,
                        false,
                        matrices.peek().getPositionMatrix(),
                        vertexConsumers,
                        NamedLootClient.CONFIG.useSeeThrough ?
                                TextRenderer.TextLayerType.SEE_THROUGH :
                                TextRenderer.TextLayerType.NORMAL,
                        0,
                        0xF000F0
                );
                yOffset += 10;
            }
        }

        matrices.pop();
    }


    private static void drawBackgroundBox(MatrixStack matrices, VertexConsumerProvider provider, float x1, float y1, float x2, float y2, int color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = provider.getBuffer(RenderLayer.getGui());
        buffer.vertex(matrix, x1, y1, -1).color(color);
        buffer.vertex(matrix, x1, y2, -1).color(color);
        buffer.vertex(matrix, x2, y2, -1).color(color);
        buffer.vertex(matrix, x2, y1, -1).color(color);
    }

    private static MutableText createAutomaticFormattedText(String itemName, String countText) {
        MutableText formattedText = Text.literal("");
        String format = NamedLootClient.CONFIG.textFormat;

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

                // Create style with all enabled formatting options for name
                Style nameStyle = Style.EMPTY.withColor(nameColor);
                if (NamedLootClient.CONFIG.nameBold) nameStyle = nameStyle.withBold(true);
                if (NamedLootClient.CONFIG.nameItalic) nameStyle = nameStyle.withItalic(true);
                if (NamedLootClient.CONFIG.nameUnderline) nameStyle = nameStyle.withUnderline(true);
                if (NamedLootClient.CONFIG.nameStrikethrough) nameStyle = nameStyle.withStrikethrough(true);

                // Add the name with its style
                formattedText.append(Text.literal(itemName).setStyle(nameStyle));
                currentIndex = nameIndex + 6; // Skip over "{name}"
            } else {
                // Count comes next
                // Add text before the placeholder
                if (countIndex > currentIndex) {
                    formattedText.append(Text.literal(format.substring(currentIndex, countIndex)));
                }

                // Create style with all enabled formatting options for count
                Style countStyle = Style.EMPTY.withColor(countColor);
                if (NamedLootClient.CONFIG.countBold) countStyle = countStyle.withBold(true);
                if (NamedLootClient.CONFIG.countItalic) countStyle = countStyle.withItalic(true);
                if (NamedLootClient.CONFIG.countUnderline) countStyle = countStyle.withUnderline(true);
                if (NamedLootClient.CONFIG.countStrikethrough) countStyle = countStyle.withStrikethrough(true);

                // Add the count with its style
                formattedText.append(Text.literal(countText).setStyle(countStyle));
                currentIndex = countIndex + 7; // Skip over "{count}"
            }
        }

        return formattedText;
    }

    // New method to parse manual formatting with & codes
    private static MutableText parseFormattedText(String format, String itemName, String countText) {
        MutableText result = Text.literal("");

        // Replace placeholders with actual values
        String text = format.replace("{name}", itemName).replace("{count}", countText);

        int currentIndex = 0;
        int formatIndex;

        // Parse the format codes
        while (currentIndex < text.length()) {
            formatIndex = text.indexOf('&', currentIndex);

            if (formatIndex == -1) {
                // No more format codes, add remaining text
                result.append(Text.literal(text.substring(currentIndex)));
                break;
            }

            // Add text before the format code
            if (formatIndex > currentIndex) {
                result.append(Text.literal(text.substring(currentIndex, formatIndex)));
            }

            // Make sure we have a character after the &
            if (formatIndex + 1 >= text.length()) {
                result.append(Text.literal("&"));
                break;
            }

            // Get the format code and apply it
            char formatCode = text.charAt(formatIndex + 1);
            Style style = Style.EMPTY;

            // Check if this is a valid format code
            switch (formatCode) {
                // Colors
                case '0': style = Style.EMPTY.withColor(0x000000); break; // Black
                case '1': style = Style.EMPTY.withColor(0x0000AA); break; // Dark Blue
                case '2': style = Style.EMPTY.withColor(0x00AA00); break; // Dark Green
                case '3': style = Style.EMPTY.withColor(0x00AAAA); break; // Dark Aqua
                case '4': style = Style.EMPTY.withColor(0xAA0000); break; // Dark Red
                case '5': style = Style.EMPTY.withColor(0xAA00AA); break; // Dark Purple
                case '6': style = Style.EMPTY.withColor(0xFFAA00); break; // Gold
                case '7': style = Style.EMPTY.withColor(0xAAAAAA); break; // Gray
                case '8': style = Style.EMPTY.withColor(0x555555); break; // Dark Gray
                case '9': style = Style.EMPTY.withColor(0x5555FF); break; // Blue
                case 'a': style = Style.EMPTY.withColor(0x55FF55); break; // Green
                case 'b': style = Style.EMPTY.withColor(0x55FFFF); break; // Aqua
                case 'c': style = Style.EMPTY.withColor(0xFF5555); break; // Red
                case 'd': style = Style.EMPTY.withColor(0xFF55FF); break; // Light Purple
                case 'e': style = Style.EMPTY.withColor(0xFFFF55); break; // Yellow
                case 'f': style = Style.EMPTY.withColor(0xFFFFFF); break; // White

                // Formatting
                case 'l': style = Style.EMPTY.withBold(true); break;
                case 'm': style = Style.EMPTY.withStrikethrough(true); break;
                case 'n': style = Style.EMPTY.withUnderline(true); break;
                case 'o': style = Style.EMPTY.withItalic(true); break;
                case 'r': style = Style.EMPTY; break; // Reset

                // Invalid format code, include the & in the output
                default:
                    result.append(Text.literal("&"));
                    currentIndex = formatIndex + 1;
                    continue;
            }

            // Find the next formatting code or the end of the string
            int nextFormatIndex = text.indexOf('&', formatIndex + 2);
            if (nextFormatIndex == -1) {
                nextFormatIndex = text.length();
            }

            // Add the text with the formatting applied
            String formattedSection = text.substring(formatIndex + 2, nextFormatIndex);
            result.append(Text.literal(formattedSection).setStyle(style));

            // Move to the next format code
            currentIndex = nextFormatIndex;
        }

        return result;
    }



}