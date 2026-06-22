package com.namedloot;

import com.namedloot.config.NamedLootConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Axis;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.*;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import org.joml.Matrix4f;

public class WorldRenderEventHandler {

    public static void registerEvents() {
        // Register the event that fires after entities are rendered
        LevelRenderEvents.END_MAIN.register((context) -> {
            Minecraft client = Minecraft.getInstance();

            // Skip if game is paused or no world is loaded
            // REMOVE the check for !NamedLootClient.CONFIG.enabled here
            if (client.isPaused() || client.level == null) {
                return;
            }

            // Get all item entities within range
            List<ItemEntity> itemEntitiesToRender = new ArrayList<>();

            // Loop through all entities directly for compatibility
            for (ItemEntity entity : client.level.getEntitiesOfClass(ItemEntity.class,
                    // Use a box around the camera to find item entities
                    new AABB(client.gameRenderer.mainCamera().blockPosition()).inflate(
                            // Use max distance from config, or default to 64 blocks
                            NamedLootClient.CONFIG.displayDistance > 0 ?
                                    NamedLootClient.CONFIG.displayDistance : 64),
                    // Simple predicate that accepts all item entities
                    itemEntity -> true)) {

                // Apply distance check if needed
                if (NamedLootClient.CONFIG.displayDistance > 0) {
                    //double distance = client.gameRenderer.mainCamera().position().distanceTo(entity.position());
                    double distance = client.gameRenderer.mainCamera().position().distanceTo(entity.position());
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

            com.mojang.blaze3d.vertex.PoseStack matrices = context.poseStack();
            float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

            Font textRenderer = client.font;

            Vec3 cameraPos = client.gameRenderer.mainCamera().position();

            // Fix z position
            itemEntitiesToRender.sort(Comparator.comparingDouble(
                    entity -> -entity.position().distanceTo(cameraPos)
            ));

            // Render all item name tags
            for (ItemEntity entity : itemEntitiesToRender) {
                renderItemNameTag(entity, matrices, context, client, textRenderer, tickDelta);
            }
        });
    }

    private static boolean isPlayerLookingAt(Minecraft client, ItemEntity entity) {
        // Get the entity's position and bounding box
        Vec3 entityPos = entity.position();
        AABB entityBox = entity.getBoundingBox();

        // Get player's look vector
        assert client.player != null;
        Vec3 lookVec = client.player.getLookAngle();
        Vec3 playerPos = client.player.getEyePosition();

        // Determine the reach distance for hover detection
        double reachDistance;

        if (NamedLootClient.CONFIG.displayDistance > 0) {
            // If displayDistance is set, use it as the maximum hover distance
            reachDistance = NamedLootClient.CONFIG.displayDistance;
        } else {
            // If displayDistance is 0 (unlimited), use a reasonable hover distance
            reachDistance = 32.0;
        }

        Vec3 endPos = playerPos.add(lookVec.scale(reachDistance));

        // Check if the ray intersects with the entity's bounding box
        // 26.2: AABB.clip returns Optional<Vec3>, not nullable Vec3
        var hitResult = entityBox.inflate(0.5).clip(playerPos, endPos);

        if (hitResult.isEmpty()) {
            return false; // Ray doesn't hit the entity
        }

        // Check if there are blocks in the way
        assert client.level != null;
        var blockHitResult = client.level.clip(
                new ClipContext(
                        playerPos,
                        entityPos,  // Use entity position as the end point
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        client.player
                )
        );

        // If the block hit result is of type MISS, there are no blocks in the way
        // Otherwise, check if the distance to the block is greater than the distance to the entity
        if (blockHitResult.getType() != HitResult.Type.MISS) {
            double blockDist = blockHitResult.getLocation().distanceTo(playerPos);
            double entityDist = entityPos.distanceTo(playerPos);

            // If block is closer than entity, the view is obstructed
            return !(blockDist < entityDist);
        }

        return true;
    }

    private static void renderItemNameTag(ItemEntity entity, com.mojang.blaze3d.vertex.PoseStack matrices,
                                          LevelRenderContext context,
                                          Minecraft client, Font textRenderer,
                                          float tickDelta) {
        if(matrices == null) {
            return;
        }
        MutableComponent formattedText = null;
        ItemStack stack = entity.getItem();
        String name = stack.getHoverName().getString();
        int count = stack.getCount();
        List<NamedLootConfig.AdvancedRule> rules = NamedLootClient.CONFIG.advancedRules;

        boolean advancedRuleApplied = false; // Flag to indicate if an advanced rule was applied

        // 1. First, try to match and apply Advanced Rules
        int i = 0;
        while (i < rules.size()) {
            NamedLootConfig.AdvancedRule leader = rules.get(i);
            // We still need to check ruleEnabled for the leader to apply its format
            // If the leader rule itself is disabled, we skip this whole group for formatting purposes.
            // However, we still need to process subsequent chained conditions to find the start of the next group.
            if (!leader.ruleEnabled) {
                int nextGroupStartIndex = i + 1;
                while (nextGroupStartIndex < rules.size() && (rules.get(nextGroupStartIndex).textFormat == null || rules.get(nextGroupStartIndex).textFormat.isEmpty())) {
                    nextGroupStartIndex++; // Skip chained conditions of a disabled leader
                }
                i = nextGroupStartIndex;
                continue; // Move to the next potential rule group
            }

            List<NamedLootConfig.AdvancedRule> groupConditions = new ArrayList<>();
            groupConditions.add(leader);
            int nextIndex = i + 1;
            while (nextIndex < rules.size() && (rules.get(nextIndex).textFormat == null || rules.get(nextIndex).textFormat.isEmpty())) {
                groupConditions.add(rules.get(nextIndex));
                nextIndex++;
            }

            boolean allConditionsMet = true;
            for (NamedLootConfig.AdvancedRule condition : groupConditions) {
                if (!checkCondition(condition, name, count)) {
                    allConditionsMet = false;
                    break;
                }
            }

            if (allConditionsMet) {
                // If a rule matches AND is enabled, use its formatting and mark as applied
                formattedText = parseFormattedText(leader.textFormat, stack, String.valueOf(count));
                advancedRuleApplied = true;
                break; // Found a matching rule, stop checking
            }

            i = nextIndex;
        }

        // 2. If no Advanced Rule was applied, then fall back to Default/Automatic based on global 'enabled' flag
        if (!advancedRuleApplied) {
            // Check the global 'enabled' flag BEFORE applying default/automatic formatting
            if (!NamedLootClient.CONFIG.enabled) {
                return; // If global mod is disabled AND no advanced rule applied, do not render anything.
            }

            // Normal fallback to default/automatic if global mod is enabled
            if (NamedLootClient.CONFIG.useManualFormatting) {
                formattedText = parseFormattedText(NamedLootClient.CONFIG.textFormat, stack, String.valueOf(count));
            } else {
                formattedText = createAutomaticFormattedText(stack, String.valueOf(count));
            }
        }


        matrices.pushPose();

        // Use getLerpedPos for smoother interpolation
        Vec3 interpolatedPos = entity.getPosition(tickDelta);

        // Set camera-relative position
        Vec3 cameraPos = client.gameRenderer.mainCamera().position();
        matrices.translate(
                interpolatedPos.x - cameraPos.x,
                interpolatedPos.y - cameraPos.y + entity.getBbHeight() + NamedLootClient.CONFIG.verticalOffset,
                interpolatedPos.z - cameraPos.z
        );

        // Face camera
        float cameraYaw = client.gameRenderer.mainCamera().yRot();
        float cameraPitch = client.gameRenderer.mainCamera().xRot();

        matrices.mulPose(Axis.YP.rotationDegrees(-cameraYaw));
        matrices.mulPose(Axis.XP.rotationDegrees(cameraPitch));

        // Scale the text appropriately
        matrices.scale(-0.025F, -0.025F, -0.025F);

        float textOffset = -textRenderer.width(formattedText) / 2.0F;

        boolean shouldShowDetails = false;
        if (advancedRuleApplied) {
            shouldShowDetails = NamedLootClient.CONFIG.showDetails;
        } else if (NamedLootClient.CONFIG.enabled) {
            shouldShowDetails = NamedLootClient.CONFIG.showDetails;
        }

        // If showDetailsOnlyOnHover is enabled, check if the player is looking at this entity
        if (shouldShowDetails && NamedLootClient.CONFIG.showDetailsOnlyOnHover) {
            shouldShowDetails = isPlayerLookingAt(client, entity);
        }

        // Get enchantment details if needed
        List<Component> details = new ArrayList<>();
        if (shouldShowDetails) { // Only attempt to get enchantments if details should be shown
            ItemEnchantments enchantments = entity.getItem().getEnchantments();
            for (Holder<Enchantment> enchantmentEntry : enchantments.keySet()) {
                int level = enchantments.getLevel(enchantmentEntry);
                Component enchantmentName = Enchantment.getFullname(enchantmentEntry, level);
                details.add(enchantmentName);
            }
        }

        // Draw text with the configured layer type and background
        Font.DisplayMode layerType = NamedLootClient.CONFIG.useSeeThrough ?
                Font.DisplayMode.SEE_THROUGH :
                Font.DisplayMode.NORMAL;

        int backgroundColor = NamedLootClient.CONFIG.useBackgroundColor ?
                NamedLootClient.CONFIG.backgroundColor :
                0x00000000; // Background color if enabled, otherwise transparent

        float mainTextY = shouldShowDetails && !details.isEmpty() ? -(details.size() * 10) - 10 : 0;

        context.submitNodeCollector().submitText(
                matrices,
                textOffset,
                mainTextY, // Adjust Y if details present
                formattedText.getVisualOrderText(),
                false,
                layerType,
                0xF000F0, // Full brightness light
                0xFFFFFFFF, // Full brightness
                backgroundColor,
                0 // No outline
        );

        // Draw details background if needed, only if details should be shown
        if (shouldShowDetails && !details.isEmpty()) {
            if (NamedLootClient.CONFIG.useBackgroundColor && NamedLootClient.CONFIG.useDetailBackgroundBox) {
                // Box mode: draw a background rectangle using spaces with bgColor
                // This stays in the texts phase (same as text) so text is readable on top
                int lineHeight = 10;
                int padding = 2;

                int maxWidth = details.stream()
                        .mapToInt(textRenderer::width)
                        .max()
                        .orElse(0);

                // Build a string of spaces wide enough to cover maxWidth + padding
                int spaceWidth = textRenderer.width(" ");
                int numSpaces = (maxWidth + padding * 4) / Math.max(spaceWidth, 1);
                StringBuilder spaces = new StringBuilder();
                for (int s = 0; s < numSpaces; s++) {
                    spaces.append(" ");
                }
                net.minecraft.util.FormattedCharSequence spaceSeq = Component.literal(spaces.toString()).getVisualOrderText();

                // Draw background lines for each detail line position
                float yOffset = 0;
                for (int d = 0; d < details.size(); d++) {
                    context.submitNodeCollector().submitText(
                            matrices,
                            textOffset - padding,
                            -(details.size() * 10) + 2 + yOffset,
                            spaceSeq,
                            false,
                            layerType,
                            0xF000F0,
                            0x00000000,
                            NamedLootClient.CONFIG.detailBackgroundColor,
                            0
                    );
                    yOffset += 10;
                }
            }
        }

        // Render details if enabled and there are details to show
        if (shouldShowDetails && !details.isEmpty()) { // Check details.isEmpty() to prevent drawing empty space
            float yOffset = 0;
            int detailColor = 0xFFAAAAAA;

            int detailBgColor = (NamedLootClient.CONFIG.useBackgroundColor && !NamedLootClient.CONFIG.useDetailBackgroundBox) ?
                    NamedLootClient.CONFIG.detailBackgroundColor : 0x00000000;

            for (Component detail : details) {
                context.submitNodeCollector().submitText(
                        matrices,
                        textOffset,
                        -(details.size() * 10) + 2 + yOffset,
                        detail.getVisualOrderText(),
                        false,
                        layerType,
                        0xF000F0,
                        detailColor,
                        detailBgColor,
                        0
                );
                yOffset += 10;
            }
        }
        matrices.popPose();
    }

    private static boolean checkCondition(NamedLootConfig.AdvancedRule rule, String name, int count) {
        if (rule.value == null || rule.value.isEmpty()) {
            return false;
        }

        switch (rule.condition) {
            case "Contains":
                return name.toLowerCase().contains(rule.value.toLowerCase());
            case "Count <":
                try {
                    return count < Integer.parseInt(rule.value);
                } catch (NumberFormatException e) {
                    return false;
                }
            case "Count >":
                try {
                    return count > Integer.parseInt(rule.value);
                } catch (NumberFormatException e) {
                    return false;
                }
            case "Count =":
                try {
                    return count == Integer.parseInt(rule.value);
                } catch (NumberFormatException e) {
                    return false;
                }
        }
        return false;
    }


    private static void drawBackgroundBox(com.mojang.blaze3d.vertex.PoseStack matrices, LevelRenderContext context, float x1, float y1, float x2, float y2, int color, boolean useSeeThrough) {
        Matrix4f matrix = matrices.last().pose();
        // Select the appropriate RenderLayer based on the useSeeThrough setting
        RenderType layer = useSeeThrough ? RenderTypes.textBackgroundSeeThrough() : RenderTypes.textBackground();

        float red = (float)(color >> 16 & 255) / 255.0F;
        float green = (float)(color >> 8 & 255) / 255.0F;
        float blue = (float)(color & 255) / 255.0F;
        float alpha = (float)(color >> 24 & 255) / 255.0F; // Use alpha from the color

        context.submitNodeCollector().submitCustomGeometry(matrices, layer, (pose, buffer) -> {
            buffer.addVertex(pose, x1, y2, -1).setColor(red, green, blue, alpha).setLight(0xF000F0); // Add light like text
            buffer.addVertex(pose, x2, y2, -1).setColor(red, green, blue, alpha).setLight(0xF000F0);
            buffer.addVertex(pose, x2, y1, -1).setColor(red, green, blue, alpha).setLight(0xF000F0);
            buffer.addVertex(pose, x1, y1, -1).setColor(red, green, blue, alpha).setLight(0xF000F0);
        });
    }

    public static MutableComponent createAutomaticFormattedText(ItemStack itemStack, String countText) {
        MutableComponent formattedText = Component.literal("");
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
                formattedText.append(Component.literal(format.substring(currentIndex)));
                break;
            }

            // Add literal text before placeholder
            if (nextPlaceholderIndex > currentIndex) {
                formattedText.append(Component.literal(format.substring(currentIndex, nextPlaceholderIndex)));
            }

            // Process placeholder
            if ("name".equals(placeholderType)) {
                // Handle {name} placeholder
                if (!NamedLootClient.CONFIG.overrideItemColors) {
                    TextColor existingColor = itemStack.getHoverName().getStyle().getColor();
                    boolean isCommon = itemStack.getRarity().equals(Rarity.COMMON);

                    // Logic: If the item name has a built-in color (not null/white) OR rarity is NOT COMMON,
                    // use getFormattedName (maintaining built-in color and style)
                    if (existingColor != null && existingColor != TextColor.fromLegacyFormat(ChatFormatting.WHITE) || !isCommon) {
                        formattedText.append(itemStack.getStyledHoverName().copy());
                    } else {
                        // If there is no built-in color (or white) AND rarity is COMMON,
                        // use plain name with style from configuration
                        String plainName = itemStack.getHoverName().getString();
                        Style nameStyle = Style.EMPTY.withColor(configNameColor);
                        if (NamedLootClient.CONFIG.nameBold) nameStyle = nameStyle.withBold(true);
                        if (NamedLootClient.CONFIG.nameItalic) nameStyle = nameStyle.withItalic(true);
                        if (NamedLootClient.CONFIG.nameUnderline) nameStyle = nameStyle.withUnderlined(true);
                        if (NamedLootClient.CONFIG.nameStrikethrough) nameStyle = nameStyle.withStrikethrough(true);
                        formattedText.append(Component.literal(plainName).setStyle(nameStyle));
                    }
                } else {
                    // If override is active, always use plain name with style from configuration
                    String plainName = itemStack.getHoverName().getString();
                    Style nameStyle = Style.EMPTY.withColor(configNameColor);
                    if (NamedLootClient.CONFIG.nameBold) nameStyle = nameStyle.withBold(true);
                    if (NamedLootClient.CONFIG.nameItalic) nameStyle = nameStyle.withItalic(true);
                    if (NamedLootClient.CONFIG.nameUnderline) nameStyle = nameStyle.withUnderlined(true);
                    if (NamedLootClient.CONFIG.nameStrikethrough) nameStyle = nameStyle.withStrikethrough(true);
                    formattedText.append(Component.literal(plainName).setStyle(nameStyle));
                }
                currentIndex = nextPlaceholderIndex + "{name}".length();

            } else {
                // Handle {count} placeholder
                Style countStyle = Style.EMPTY.withColor(countColor);
                if (NamedLootClient.CONFIG.countBold) countStyle = countStyle.withBold(true);
                if (NamedLootClient.CONFIG.countItalic) countStyle = countStyle.withItalic(true);
                if (NamedLootClient.CONFIG.countUnderline) countStyle = countStyle.withUnderlined(true);
                if (NamedLootClient.CONFIG.countStrikethrough) countStyle = countStyle.withStrikethrough(true);
                formattedText.append(Component.literal(countText).setStyle(countStyle));
                currentIndex = nextPlaceholderIndex + "{count}".length();
            }
        }

        return formattedText;
    }

    public static MutableComponent parseFormattedText(String format, ItemStack itemStack, String countText) {
        MutableComponent result = Component.literal("");
        Style currentStyle = Style.EMPTY;
        StringBuilder currentSegment = new StringBuilder();

        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);

            if (c == '&' && i + 1 < format.length()) {
                if (!currentSegment.isEmpty()) {
                    result.append(Component.literal(currentSegment.toString()).setStyle(currentStyle));
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
                        result.append(Component.literal(currentSegment.toString()).setStyle(currentStyle));
                        currentSegment.setLength(0);

                        MutableComponent nameText;
                        if (!NamedLootClient.CONFIG.overrideItemColors &&
                                (itemStack.getHoverName().getStyle().getColor() != null ||
                                        !itemStack.getRarity().equals(Rarity.COMMON))) {
                            nameText = itemStack.getStyledHoverName().copy();
                        } else {
                            nameText = Component.literal(itemStack.getHoverName().getString()).setStyle(currentStyle);
                        }
                        result.append(nameText);
                        i = placeholderEnd;
                        continue;
                    } else if (placeholder.equals("{count}")) {
                        result.append(Component.literal(currentSegment.toString()).setStyle(currentStyle));
                        currentSegment.setLength(0);
                        result.append(Component.literal(countText).setStyle(currentStyle));
                        i = placeholderEnd;
                        continue;
                    }
                }
            }

            currentSegment.append(c);
        }

        // Add remaining text
        if (!currentSegment.isEmpty()) {
            result.append(Component.literal(currentSegment.toString()).setStyle(currentStyle));
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
            case 'k' -> currentStyle.withObfuscated(true);
            case 'l' -> currentStyle.withBold(true);
            case 'm' -> currentStyle.withStrikethrough(true);
            case 'n' -> currentStyle.withUnderlined(true);
            case 'o' -> currentStyle.withItalic(true);
            case 'r' -> Style.EMPTY;
            default -> currentStyle;
        };
    }
}
