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
            //formattedText = parseFormattedTextOptimized(NamedLootClient.CONFIG.textFormat, entity.getStack(), countText);
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

    private static MutableText createAutomaticFormattedText(ItemStack itemStack, String countText) {
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

            } else if ("count".equals(placeholderType)) {
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

    private static String[] splitByPlaceholder(String input) {
        String[] result = new String[3];
        String placeholder = "{name}";

        int placeholderIndex = input.indexOf(placeholder);
        if (placeholderIndex == -1) {
            // Placeholder tidak ditemukan
            result[0] = "";
            result[1] = "";
            result[2] = input;
            return result;
        }

        // Bagian sebelum placeholder
        result[0] = input.substring(0, placeholderIndex);

        // Placeholder itu sendiri
        result[1] = placeholder;

        // Bagian setelah placeholder
        String afterPlaceholder = input.substring(placeholderIndex + placeholder.length());

        // Ekstrak semua karakter format '&n' dari bagian pertama
        StringBuilder formatChars = new StringBuilder();
        for (int i = 0; i < result[0].length(); i++) {
            if (result[0].charAt(i) == '&' && i + 1 < result[0].length()) {
                char next = result[0].charAt(i + 1);
                if (isSpecialChar(next)) {
                    formatChars.append("&").append(next);
                    i++; // Skip karakter berikutnya karena sudah diproses
                }
            }
        }

        result[2] = formatChars.toString() + afterPlaceholder;

        return result;
    }

    private static boolean isSpecialChar(char c) {
        String specialChars = "0123456789abcdefklmnor";
        return specialChars.indexOf(c) != -1;
    }

    // New method to parse manual formatting with & codes
    private static MutableText parseFormattedText(String format, ItemStack itemStack, String countText) {
        MutableText result = Text.literal("");
        int currentIndex = 0;

        // System.out.println(" ");
        // System.out.println("Nama Item : " + itemStack.getFormattedName().getString());
        // System.out.println("itemStack.getFormattedName().getStyle() : " + itemStack.getFormattedName().getStyle());
        // System.out.println("itemStack.getName().getStyle() : " + itemStack.getName().getStyle());
        // System.out.println("itemStack.getCustomName().getStyle() : " + (itemStack.getCustomName() != null ? itemStack.getCustomName().getStyle() : "null"));
        // System.out.println("itemStack.getRarity() : " + itemStack.getRarity());
        // Loop untuk mencari placeholder {name} dan menangani sisanya secara literal
        if(!NamedLootClient.CONFIG.overrideItemColors){
            String[] parts = splitByPlaceholder(format);
            if(itemStack.getName().getStyle().getColor() != null ){
                result.append(parseAmpersand(parts[0]));
                result.append(itemStack.getFormattedName());
            }else{
                if(itemStack.getRarity().equals(Rarity.COMMON)){
                    result.append(parseAmpersand(parts[0]+parts[1].replace("{name}", itemStack.getName().getString())));
                }else {
                    result.append(parseAmpersand(parts[0]));
                    result.append(itemStack.getFormattedName());
                }
            }

            result.append(parseAmpersand(parts[2].replace("{count}", countText)));
        }else{
            // Jika overrideItemColors == true, gunakan style berdasarkan konfigurasi tanpa pemeriksaan tambahan
            String plainName = NamedLootClient.CONFIG.textFormat.replace("{name}", itemStack.getName().getString()).replace("{count}", countText);
            result.append(parseAmpersand(plainName));
        }


        return result;
    }

    // New method to parse manual formatting with & codes
    private static MutableText parseFormattedTextOptimized(String format, ItemStack itemStack, String countText) {
        MutableText result = Text.literal("");
        Style currentStyle = Style.EMPTY; // Menyimpan style saat ini, terakumulasi dari kode format

        int currentIndex = 0;
        while (currentIndex < format.length()) {
            int nextAmpersand = format.indexOf('&', currentIndex);
            int nextNamePlaceholder = format.indexOf("{name}", currentIndex);
            int nextCountPlaceholder = format.indexOf("{count}", currentIndex);

            // Tentukan event terdekat (kode format, placeholder nama, atau placeholder jumlah)
            int nextEventIndex = format.length(); // Default ke akhir string
            String eventType = "end";

            // Prioritaskan ampersand jika valid
            if (nextAmpersand != -1 && nextAmpersand < nextEventIndex) {
                // Pastikan ada karakter setelah '&' dan itu adalah kode format yang valid
                if (nextAmpersand + 1 < format.length() && isSpecialChar(format.charAt(nextAmpersand + 1))) {
                    nextEventIndex = nextAmpersand;
                    eventType = "format";
                } else {
                    // Jika '&' bukan kode format valid, anggap sebagai literal.
                    // Lanjutkan pencarian dari karakter setelah '&' yang tidak valid
                    // untuk menemukan event berikutnya.
                    int searchFrom = nextAmpersand + 1;
                    nextAmpersand = format.indexOf('&', searchFrom); // Cari ampersand valid berikutnya setelah yang tidak valid

                    if (nextAmpersand != -1 && nextAmpersand < nextEventIndex) {
                        nextEventIndex = nextAmpersand;
                        eventType = "format";
                    }
                    // Jika tidak ada ampersand valid lagi, placeholder bisa menjadi event berikutnya
                    // Logika ini sudah dicakup oleh pemeriksaan placeholder di bawah
                }
            }
            // Periksa placeholder jika tidak ada ampersand yang lebih dekat atau tidak ada ampersand valid
            if (nextNamePlaceholder != -1 && nextNamePlaceholder < nextEventIndex) {
                nextEventIndex = nextNamePlaceholder;
                eventType = "name";
            }
            if (nextCountPlaceholder != -1 && nextCountPlaceholder < nextEventIndex) {
                nextEventIndex = nextCountPlaceholder;
                eventType = "count";
            }


            // 1. Tambahkan teks literal sebelum event terdekat DENGAN currentStyle
            if (nextEventIndex > currentIndex) {
                result.append(Text.literal(format.substring(currentIndex, nextEventIndex)).setStyle(currentStyle));
            }

            // 2. Proses event terdekat
            switch (eventType) {
                case "format":
                    // Terapkan style baru dari kode format dan PERBARUI currentStyle
                    char formatCode = format.charAt(nextEventIndex + 1);
                    currentStyle = applyAmpersandStyle(currentStyle, formatCode); // currentStyle diupdate di sini
                    currentIndex = nextEventIndex + 2; // Lewati '&' dan kodenya
                    break;

                case "name":
                    // Tangani placeholder {name}.
                    if (!NamedLootClient.CONFIG.overrideItemColors) {
                        TextColor existingColor = itemStack.getName().getStyle().getColor();
                        boolean isCommon = itemStack.getRarity().equals(Rarity.COMMON);

                        // Logika seperti parseFormattedText sebelumnya:
                        // Jika nama item punya warna bawaan (bukan null/putih) ATAU rarity BUKAN COMMON,
                        // tambahkan itemStack.getFormattedName() apa adanya.
                        // Ampersand style sebelumnya hanya berlaku untuk teks sebelum ini.
                        if (existingColor != null && existingColor != TextColor.fromFormatting(Formatting.WHITE) || !isCommon) {
                            result.append(itemStack.getFormattedName());
                        } else {
                            // Jika tidak ada warna bawaan (atau putih) DAN rarity COMMON,
                            // gunakan nama plain dengan style yang menggabungkan currentStyle
                            // dan style dari konfigurasi nama.
                            String plainName = itemStack.getName().getString();
                            // Gunakan style dari config sebagai 'anak' agar menimpa warna default,
                            // tetapi warisi format dari ampersand ('induk').
                            Style nameSegmentStyle = getManualNameStyle().withParent(currentStyle);
                            result.append(Text.literal(plainName).setStyle(nameSegmentStyle));
                        }
                    } else {
                        // Jika override aktif, selalu gunakan nama plain dengan style
                        // yang menggabungkan currentStyle dan style dari konfigurasi nama.
                        String plainName = itemStack.getName().getString();
                        // Gunakan style dari config sebagai 'anak' agar menimpa warna default,
                        // tetapi warisi format dari ampersand ('induk').
                        Style nameSegmentStyle = getManualNameStyle().withParent(currentStyle);
                        result.append(Text.literal(plainName).setStyle(nameSegmentStyle));
                    }
                    currentIndex = nextEventIndex + "{name}".length();
                    // currentStyle tidak diubah di sini, agar style dari format code SEBELUM {name}
                    // tetap berlaku untuk teks SETELAH {name} jika tidak ada format code lain.
                    break;

                case "count":
                    // Tangani placeholder {count}.
                    Style countSegmentStyle;

                    if (!NamedLootClient.CONFIG.overrideItemColors) {
                        // Jika tidak override, jumlah hanya mewarisi currentStyle
                        countSegmentStyle = currentStyle;
                    } else {
                        // Jika override aktif, gunakan style dari konfigurasi jumlah dan gabungkan dengan currentStyle
                        // Gunakan style dari config sebagai 'anak' agar menimpa warna default,
                        // tetapi warisi format dari ampersand ('induk').
                        countSegmentStyle = getManualCountStyle().withParent(currentStyle);
                    }
                    // Tambahkan teks jumlah dengan style yang sudah digabungkan
                    result.append(Text.literal(countText).setStyle(countSegmentStyle));
                    currentIndex = nextEventIndex + "{count}".length();
                    // currentStyle tidak diubah di sini
                    break;

                case "end":
                default:
                    // Akhir string atau tidak ada event lagi
                    currentIndex = format.length();
                    break;
            }
        }

        return result;
    }

    // Fungsi helper untuk menerapkan style dari kode ampersand (Tidak berubah)
    private static Style applyAmpersandStyle(Style baseStyle, char formatCode) {
        switch (formatCode) {
            case '0': return baseStyle.withColor(0x000000); // Black
            case '1': return baseStyle.withColor(0x0000AA); // Dark Blue
            case '2': return baseStyle.withColor(0x00AA00); // Dark Green
            case '3': return baseStyle.withColor(0x00AAAA); // Dark Aqua
            case '4': return baseStyle.withColor(0xAA0000); // Dark Red
            case '5': return baseStyle.withColor(0xAA00AA); // Dark Purple
            case '6': return baseStyle.withColor(0xFFAA00); // Gold
            case '7': return baseStyle.withColor(0xAAAAAA); // Gray
            case '8': return baseStyle.withColor(0x555555); // Dark Gray
            case '9': return baseStyle.withColor(0x5555FF); // Blue
            case 'a': return baseStyle.withColor(0x55FF55); // Green
            case 'b': return baseStyle.withColor(0x55FFFF); // Aqua
            case 'c': return baseStyle.withColor(0xFF5555); // Red
            case 'd': return baseStyle.withColor(0xFF55FF); // Light Purple
            case 'e': return baseStyle.withColor(0xFFFF55); // Yellow
            case 'f': return baseStyle.withColor(0xFFFFFF); // White
            case 'l': return baseStyle.withBold(true);
            case 'm': return baseStyle.withStrikethrough(true);
            case 'n': return baseStyle.withUnderline(true);
            case 'o': return baseStyle.withItalic(true);
            case 'r': return Style.EMPTY; // Reset semua style
            default:  return baseStyle; // Kode tidak valid, jangan ubah style
        }
    }

    // Helper untuk mendapatkan style manual dari config (Ini tetap sama)
    private static Style getManualNameStyle() {
        int configNameRed = (int)(NamedLootClient.CONFIG.nameRed * 255);
        int configNameGreen = (int)(NamedLootClient.CONFIG.nameGreen * 255);
        int configNameBlue = (int)(NamedLootClient.CONFIG.nameBlue * 255);
        int configNameColor = (configNameRed << 16) | (configNameGreen << 8) | configNameBlue;

        Style nameStyle = Style.EMPTY.withColor(TextColor.fromRgb(configNameColor)); // Gunakan fromRgb
        if (NamedLootClient.CONFIG.nameBold) nameStyle = nameStyle.withBold(true);
        if (NamedLootClient.CONFIG.nameItalic) nameStyle = nameStyle.withItalic(true);
        if (NamedLootClient.CONFIG.nameUnderline) nameStyle = nameStyle.withUnderline(true);
        if (NamedLootClient.CONFIG.nameStrikethrough) nameStyle = nameStyle.withStrikethrough(true);
        return nameStyle;
    }

    // Helper untuk mendapatkan style manual count dari config (Ini tetap sama)
    private static Style getManualCountStyle() {
        int countRed = (int)(NamedLootClient.CONFIG.countRed * 255);
        int countGreen = (int)(NamedLootClient.CONFIG.countGreen * 255);
        int countBlue = (int)(NamedLootClient.CONFIG.countBlue * 255);
        int countColor = (countRed << 16) | (countGreen << 8) | countBlue;

        Style countStyle = Style.EMPTY.withColor(TextColor.fromRgb(countColor)); // Gunakan fromRgb
        if (NamedLootClient.CONFIG.countBold) countStyle = countStyle.withBold(true);
        if (NamedLootClient.CONFIG.countItalic) countStyle = countStyle.withItalic(true);
        if (NamedLootClient.CONFIG.countUnderline) countStyle = countStyle.withUnderline(true);
        if (NamedLootClient.CONFIG.countStrikethrough) countStyle = countStyle.withStrikethrough(true);
        return countStyle;
    }

    private static MutableText parseAmpersand(String textSegment) {
        MutableText segmentResult = Text.literal("");
        int currentIndex = 0;

        while (currentIndex < textSegment.length()) {
            int formatIndex = textSegment.indexOf('&', currentIndex);
            if (formatIndex == -1) {
                segmentResult.append(Text.literal(textSegment.substring(currentIndex)));
                break;
            }

            if (formatIndex > currentIndex) {
                segmentResult.append(Text.literal(textSegment.substring(currentIndex, formatIndex)));
            }

            // Pastikan terdapat karakter setelah '&'
            if (formatIndex + 1 >= textSegment.length()) {
                segmentResult.append(Text.literal("&"));
                break;
            }

            char formatCode = textSegment.charAt(formatIndex + 1);
            Style style = Style.EMPTY;

            switch (formatCode) {
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
                case 'l': style = Style.EMPTY.withBold(true); break;
                case 'm': style = Style.EMPTY.withStrikethrough(true); break;
                case 'n': style = Style.EMPTY.withUnderline(true); break;
                case 'o': style = Style.EMPTY.withItalic(true); break;
                case 'r': style = Style.EMPTY; break; // Reset
                default:
                    // Jika kode tidak valid, masukkan '&' lalu lanjutkan
                    segmentResult.append(Text.literal("&"));
                    currentIndex = formatIndex + 1;
                    continue;
            }

            int nextFormatIndex = textSegment.indexOf('&', formatIndex + 2);
            if (nextFormatIndex == -1) {
                nextFormatIndex = textSegment.length();
            }

            String formattedSection = textSegment.substring(formatIndex + 2, nextFormatIndex);
            segmentResult.append(Text.literal(formattedSection).setStyle(style));
            currentIndex = nextFormatIndex;
        }

        return segmentResult;
    }

}