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

        // Set name color dari konfigurasi (akan dipakai bila kondisi tidak memenuhi)
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

            // Tentukan placeholder terdekat
            int nextPlaceholderIndex = -1;
            String placeholderType = null;

            if (nameIndex != -1 && (countIndex == -1 || nameIndex < countIndex)) {
                nextPlaceholderIndex = nameIndex;
                placeholderType = "name";
            } else if (countIndex != -1) {
                nextPlaceholderIndex = countIndex;
                placeholderType = "count";
            }

            // Jika tidak ada placeholder lagi, tambahkan sisa literal text
            if (nextPlaceholderIndex == -1) {
                formattedText.append(Text.literal(format.substring(currentIndex)));
                break;
            }

            // Tambahkan literal text sebelum placeholder
            if (nextPlaceholderIndex > currentIndex) {
                formattedText.append(Text.literal(format.substring(currentIndex, nextPlaceholderIndex)));
            }

            // Proses placeholder
            if ("name".equals(placeholderType)) {
                // Tangani placeholder {name}
                if (!NamedLootClient.CONFIG.overrideItemColors) {
                    TextColor existingColor = itemStack.getName().getStyle().getColor();
                    boolean isCommon = itemStack.getRarity().equals(Rarity.COMMON);

                    // Logika: Jika nama item memiliki warna bawaan (bukan null/putih) ATAU rarity BUKAN COMMON
                    // Gunakan getFormattedName (mempertahankan warna dan style bawaan)
                    if (existingColor != null && existingColor != TextColor.fromFormatting(Formatting.WHITE) || !isCommon) {
                        formattedText.append(itemStack.getFormattedName());
                    } else {
                        // Jika tidak ada warna bawaan (atau putih) DAN rarity COMMON
                        // Gunakan nama plain dengan style dari konfigurasi
                        String plainName = itemStack.getName().getString();
                        Style nameStyle = Style.EMPTY.withColor(configNameColor);
                        if (NamedLootClient.CONFIG.nameBold) nameStyle = nameStyle.withBold(true);
                        if (NamedLootClient.CONFIG.nameItalic) nameStyle = nameStyle.withItalic(true);
                        if (NamedLootClient.CONFIG.nameUnderline) nameStyle = nameStyle.withUnderline(true);
                        if (NamedLootClient.CONFIG.nameStrikethrough) nameStyle = nameStyle.withStrikethrough(true);
                        formattedText.append(Text.literal(plainName).setStyle(nameStyle));
                    }
                } else {
                    // Jika override aktif, selalu gunakan nama plain dengan style dari konfigurasi
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
                // Tangani placeholder {count}
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
                            nameText = itemStack.getFormattedName().copy();
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