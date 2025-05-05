package com.namedloot;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
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
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Box;
import net.minecraft.enchantment.EnchantmentHelper;

import java.util.*;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.RaycastContext;
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

                // If showNameOnHover is enabled, check if the player is looking at this entity
                if (NamedLootClient.CONFIG.showNameOnHover) {
                    if (isPlayerLookingAt(client, entity)) {
                        itemEntitiesToRender.add(entity);
                    }
                } else {
                    // Normal behavior - add all items
                    itemEntitiesToRender.add(entity);
                }

            }

            // Skip if no entities to render
            if (itemEntitiesToRender.isEmpty()) {
                return;
            }

            // Get render state
            MatrixStack matrices = context.matrixStack();
            float tickDelta = context.tickCounter().getTickDelta(true);

            TextRenderer textRenderer = client.textRenderer;
            VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();


            // Render all item name tags
            for (ItemEntity entity : itemEntitiesToRender) {
                renderItemNameTag(entity, matrices, immediate, client, textRenderer, tickDelta);
            }
        });
    }

    private static boolean isPlayerLookingAt(MinecraftClient client, ItemEntity entity) {
        // Get the entity's position and bounding box
        Vec3d entityPos = entity.getPos();
        Box entityBox = entity.getBoundingBox();

        // Get player's look vector
        assert client.player != null;
        Vec3d lookVec = client.player.getRotationVec(1.0F);
        Vec3d playerPos = client.player.getEyePos();

        // Determine the reach distance for hover detection
        double reachDistance;

        if (NamedLootClient.CONFIG.displayDistance > 0) {
            // If displayDistance is set, use it as the maximum hover distance
            reachDistance = NamedLootClient.CONFIG.displayDistance;
        } else {
            // If displayDistance is 0 (unlimited), use a reasonable hover distance
            reachDistance = 32.0;
        }

        Vec3d endPos = playerPos.add(lookVec.multiply(reachDistance));

        // Check if the ray intersects with the entity's bounding box
        var hitResult = entityBox.expand(0.5).raycast(playerPos, endPos);

        if (hitResult.isEmpty()) {
            return false; // Ray doesn't hit the entity
        }

        // Check if there are blocks in the way
        assert client.world != null;
        var blockHitResult = client.world.raycast(
                new RaycastContext(
                        playerPos,
                        entityPos,  // Use entity position as the end point
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        client.player
                )
        );

        // If the block hit result is of type MISS, there are no blocks in the way
        // Otherwise, check if the distance to the block is greater than the distance to the entity
        if (blockHitResult.getType() != HitResult.Type.MISS) {
            double blockDist = blockHitResult.getPos().distanceTo(playerPos);
            double entityDist = entityPos.distanceTo(playerPos);

            // If block is closer than entity, the view is obstructed
            return !(blockDist < entityDist);
        }

        return true;
    }

    private static void renderItemNameTag(ItemEntity entity, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers,
                                          MinecraftClient client, TextRenderer textRenderer,
                                          float tickDelta) {
        if(matrices == null) {
            return;
        }
        // Get item count
        int count = entity.getStack().getCount();
        String countText = String.valueOf(count);

        // Create formatted text based on whether we're using manual formatting or automatic coloring
        MutableText formattedText;

        if (NamedLootClient.CONFIG.useManualFormatting) {
            // Use manual formatting with & codes
            formattedText = parseFormattedText(NamedLootClient.CONFIG.textFormat, entity.getStack(), countText);
        } else {
            // Use our custom styling
            formattedText = createAutomaticFormattedText(entity.getStack(), countText);
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

        // Check for detail visibility based on hover option
        boolean shouldShowDetails = NamedLootClient.CONFIG.showDetails;

        // If details should only be shown on hover, check if the player is looking at this entity
        if (shouldShowDetails && NamedLootClient.CONFIG.showDetailsOnlyOnHover) {
            shouldShowDetails = isPlayerLookingAt(client, entity);
        }

        // Get enchantment details if needed
        List<Text> details = new ArrayList<>();
        if (shouldShowDetails) {
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
            if (NamedLootClient.CONFIG.useBackgroundColor && NamedLootClient.CONFIG.useDetailBackgroundBox) {
                int lineHeight = 10;
                int padding = 2;

                int maxWidth = details.stream()
                        .mapToInt(textRenderer::getWidth)
                        .max()
                        .orElse(0);

                float xOffset = textOffset - padding;
                float yOffset = -(details.size() * lineHeight);
                float width   = xOffset + maxWidth + padding * 2;
                float height  = yOffset + (details.size() * lineHeight) + padding;

                drawBackgroundBox(matrices, vertexConsumers, xOffset, yOffset, width, height,
                        NamedLootClient.CONFIG.detailBackgroundColor, NamedLootClient.CONFIG.useSeeThrough);
            }
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
                        // Use detail background color if background enabled and not using box style
                        (NamedLootClient.CONFIG.useBackgroundColor && !NamedLootClient.CONFIG.useDetailBackgroundBox) ?
                                NamedLootClient.CONFIG.detailBackgroundColor : 0x00000000,
                        0xF000F0
                );
                yOffset += 10;
            }
        }

        matrices.pop();
    }


    private static void drawBackgroundBox(MatrixStack matrices, VertexConsumerProvider provider, float x1, float y1, float x2, float y2, int color, boolean useSeeThrough) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        // Select the appropriate RenderLayer based on the useSeeThrough setting
        RenderLayer layer = useSeeThrough ? RenderLayer.getTextBackgroundSeeThrough() : RenderLayer.getTextBackground();
        VertexConsumer buffer = provider.getBuffer(layer);

        // Draw the quad
        float red = (float)(color >> 16 & 255) / 255.0F;
        float green = (float)(color >> 8 & 255) / 255.0F;
        float blue = (float)(color & 255) / 255.0F;
        float alpha = (float)(color >> 24 & 255) / 255.0F; // Use alpha from the color

        buffer.vertex(matrix, x1, y2, -1).color(red, green, blue, alpha).light(0xF000F0); // Add light like text
        buffer.vertex(matrix, x2, y2, -1).color(red, green, blue, alpha).light(0xF000F0);
        buffer.vertex(matrix, x2, y1, -1).color(red, green, blue, alpha).light(0xF000F0);
        buffer.vertex(matrix, x1, y1, -1).color(red, green, blue, alpha).light(0xF000F0);
    }

    public static MutableText createAutomaticFormattedText(ItemStack itemStack, String countText) {
        MutableText formattedText = Text.literal("");
        String format = NamedLootClient.CONFIG.textFormat;

        // Set name color from configuration (will be used if conditions are not met)
        int configNameRed = (int)(NamedLootClient.CONFIG.nameRed * 255);
        int configNameGreen = (int)(NamedLootClient.CONFIG.nameGreen * 255);
        int configNameBlue = (int)(NamedLootClient.CONFIG.nameBlue * 255);
        int configNameColor = (configNameRed << 16) | (configNameGreen << 8) | configNameBlue;

        // Set count color
        int countRed = (int)(NamedLootClient.CONFIG.countRed * 255);
        int countGreen = (int)(NamedLootClient.CONFIG.countGreen * 255);
        int countBlue = (int)(NamedLootClient.CONFIG.countBlue * 255);
        int countColor = (countRed << 16) | (countGreen << 8) | countBlue;

        int currentIndex = 0;
        while (currentIndex < format.length()) {
            int nameIndex = format.indexOf("{name}", currentIndex);
            int countIndex = format.indexOf("{count}", currentIndex);

            // Determine the nearest placeholder
            int nextPlaceholderIndex = -1;
            String placeholderType = null;

            if (nameIndex != -1 && (countIndex == -1 || nameIndex < countIndex)) {
                nextPlaceholderIndex = nameIndex;
                placeholderType = "name";
            } else if (countIndex != -1) {
                nextPlaceholderIndex = countIndex;
                placeholderType = "count";
            }

            // If there are no more placeholders, add the remaining literal text
            if (nextPlaceholderIndex == -1) {
                formattedText.append(Text.literal(format.substring(currentIndex)));
                break;
            }

            // Add literal text before placeholder
            if (nextPlaceholderIndex > currentIndex) {
                formattedText.append(Text.literal(format.substring(currentIndex, nextPlaceholderIndex)));
            }

            // Process placeholder
            if ("name".equals(placeholderType)) {
                // Handle {name} placeholder
                if (!NamedLootClient.CONFIG.overrideItemColors) {
                    TextColor existingColor = itemStack.getName().getStyle().getColor();
                    boolean isCommon = itemStack.getRarity().equals(Rarity.COMMON);

                    // Logic: If the item name has a built-in color (not null/white) OR rarity is NOT COMMON,
                    // use getFormattedName (maintaining built-in color and style)
                    if (existingColor != null && existingColor != TextColor.fromFormatting(Formatting.WHITE) || !isCommon) {
                        formattedText.append(itemStack.getName().copy());
                    } else {
                        // If there is no built-in color (or white) AND rarity is COMMON,
                        // use plain name with style from configuration
                        String plainName = itemStack.getName().getString();
                        Style nameStyle = Style.EMPTY.withColor(configNameColor);
                        if (NamedLootClient.CONFIG.nameBold) nameStyle = nameStyle.withBold(true);
                        if (NamedLootClient.CONFIG.nameItalic) nameStyle = nameStyle.withItalic(true);
                        if (NamedLootClient.CONFIG.nameUnderline) nameStyle = nameStyle.withUnderline(true);
                        if (NamedLootClient.CONFIG.nameStrikethrough) nameStyle = nameStyle.withStrikethrough(true);
                        formattedText.append(Text.literal(plainName).setStyle(nameStyle));
                    }
                } else {
                    // If override is active, always use plain name with style from configuration
                    String plainName = itemStack.getName().getString();
                    Style nameStyle = Style.EMPTY.withColor(configNameColor);
                    if (NamedLootClient.CONFIG.nameBold) nameStyle = nameStyle.withBold(true);
                    if (NamedLootClient.CONFIG.nameItalic) nameStyle = nameStyle.withItalic(true);
                    if (NamedLootClient.CONFIG.nameUnderline) nameStyle = nameStyle.withUnderline(true);
                    if (NamedLootClient.CONFIG.nameStrikethrough) nameStyle = nameStyle.withStrikethrough(true);
                    formattedText.append(Text.literal(plainName).setStyle(nameStyle));
                }
                currentIndex = nextPlaceholderIndex + "{name}".length();

            } else {
                // Handle {count} placeholder
                Style countStyle = Style.EMPTY.withColor(countColor);
                if (NamedLootClient.CONFIG.countBold) countStyle = countStyle.withBold(true);
                if (NamedLootClient.CONFIG.countItalic) countStyle = countStyle.withItalic(true);
                if (NamedLootClient.CONFIG.countUnderline) countStyle = countStyle.withUnderline(true);
                if (NamedLootClient.CONFIG.countStrikethrough) countStyle = countStyle.withStrikethrough(true);
                formattedText.append(Text.literal(countText).setStyle(countStyle));
                currentIndex = nextPlaceholderIndex + "{count}".length();
            }
        }

        return formattedText;
    }

    public static MutableText parseFormattedText(String format, ItemStack itemStack, String countText) {
        MutableText result = Text.literal("");
        Style currentStyle = Style.EMPTY;
        StringBuilder currentSegment = new StringBuilder();

        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);

            // Handle ampersand formatting codes
            if (c == '&' && i + 1 < format.length()) {
                if (!currentSegment.isEmpty()) {
                    result.append(Text.literal(currentSegment.toString()).setStyle(currentStyle));
                    currentSegment.setLength(0);
                }
                currentStyle = applyFormatCode(currentStyle, format.charAt(++i));
                continue;
            }

            // Check for placeholder start
            if (c == '{') {
                int placeholderEnd = format.indexOf('}', i);
                if (placeholderEnd != -1) {
                    String placeholder = format.substring(i, placeholderEnd + 1);

                    // Handle placeholder
                    if (placeholder.equals("{name}")) {
                        result.append(Text.literal(currentSegment.toString()).setStyle(currentStyle));
                        currentSegment.setLength(0);

                        MutableText nameText;
                        if (!NamedLootClient.CONFIG.overrideItemColors &&
                                (itemStack.getName().getStyle().getColor() != null ||
                                        !itemStack.getRarity().equals(Rarity.COMMON))) {
                            nameText = itemStack.getName().copy();
                        } else {
                            nameText = Text.literal(itemStack.getName().getString()).setStyle(currentStyle);
                        }
                        result.append(nameText);
                        i = placeholderEnd;
                        continue;
                    } else if (placeholder.equals("{count}")) {
                        result.append(Text.literal(currentSegment.toString()).setStyle(currentStyle));
                        currentSegment.setLength(0);
                        result.append(Text.literal(countText).setStyle(currentStyle));
                        i = placeholderEnd;
                        continue;
                    }
                }
            }

            currentSegment.append(c);
        }

        // Add remaining text
        if (!currentSegment.isEmpty()) {
            result.append(Text.literal(currentSegment.toString()).setStyle(currentStyle));
        }

        return result;
    }

    private static Style applyFormatCode(Style currentStyle, char code) {
        return switch (code) {
            case '0' -> currentStyle.withColor(TextColor.fromRgb(0x000000));
            case '1' -> currentStyle.withColor(TextColor.fromRgb(0x0000AA));
            case '2' -> currentStyle.withColor(TextColor.fromRgb(0x00AA00));
            case '3' -> currentStyle.withColor(TextColor.fromRgb(0x00AAAA));
            case '4' -> currentStyle.withColor(TextColor.fromRgb(0xAA0000));
            case '5' -> currentStyle.withColor(TextColor.fromRgb(0xAA00AA));
            case '6' -> currentStyle.withColor(TextColor.fromRgb(0xFFAA00));
            case '7' -> currentStyle.withColor(TextColor.fromRgb(0xAAAAAA));
            case '8' -> currentStyle.withColor(TextColor.fromRgb(0x555555));
            case '9' -> currentStyle.withColor(TextColor.fromRgb(0x5555FF));
            case 'a' -> currentStyle.withColor(TextColor.fromRgb(0x55FF55));
            case 'b' -> currentStyle.withColor(TextColor.fromRgb(0x55FFFF));
            case 'c' -> currentStyle.withColor(TextColor.fromRgb(0xFF5555));
            case 'd' -> currentStyle.withColor(TextColor.fromRgb(0xFF55FF));
            case 'e' -> currentStyle.withColor(TextColor.fromRgb(0xFFFF55));
            case 'f' -> currentStyle.withColor(TextColor.fromRgb(0xFFFFFF));
            case 'l' -> currentStyle.withBold(true);
            case 'm' -> currentStyle.withStrikethrough(true);
            case 'n' -> currentStyle.withUnderline(true);
            case 'o' -> currentStyle.withItalic(true);
            case 'r' -> Style.EMPTY;
            default -> currentStyle;
        };
    }

}