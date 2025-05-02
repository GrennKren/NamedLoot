package com.namedloot.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.nbt.NbtCompound;
import com.namedloot.NamedLootClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NamedLootModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return NamedLootConfigScreen::new;
    }

    public static class NamedLootConfigScreen extends Screen {
        private final Screen parent;
        private TextFieldWidget formatField;

        private int previousHeight;

        // Constants for color preview dimensions
        private static final int PREVIEW_SIZE = 30;
        private static final int PREVIEW_X_OFFSET = 120;

        // Add scrolling variables
        private int scrollOffset = 0;
        private boolean isScrolling = false;
        private int contentHeight = 450; // Approximate height of all content

        public NamedLootConfigScreen(Screen parent) {
            super(Text.translatable("text.namedloot.config"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            // Handle window resizing by adjusting scroll offset
            if (previousHeight != 0 && previousHeight != this.height) {
                // Ensure the scroll offset doesn't create empty space at the bottom after resize
                int visibleHeight = this.height - 50;
                if (contentHeight - Math.abs(scrollOffset) < visibleHeight) {
                    // Recalculate to show bottom content properly
                    scrollOffset = Math.min(0, -(contentHeight - visibleHeight));
                }
            }

            // Store the current height for future comparison
            previousHeight = this.height;

            // We need to update the contentHeight field, not create a local variable
            // that shadows the class field
            // Replace: final int contentHeight = 650; 
            // With proper assignment to the class field:
            this.contentHeight = 650;

            // Original init code continues
            this.clearChildren();
            int yBase = this.height / 8;
            var ref = new Object() {
                int yPos = yBase + scrollOffset;
            };

            // ==========================================================
            // GENERAL SECTION
            // ==========================================================

            // Vertical Offset Slider
            SliderWidget verticalOffsetSlider = new SliderWidget(this.width / 2 - 100, ref.yPos, 200, 20,
                    Text.translatable("options.namedloot.vertical_offset", NamedLootClient.CONFIG.verticalOffset),
                    NamedLootClient.CONFIG.verticalOffset / 2.0F) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.translatable("options.namedloot.vertical_offset",
                            String.format("%.2f", NamedLootClient.CONFIG.verticalOffset)));
                }

                @Override
                protected void applyValue() {
                    NamedLootClient.CONFIG.verticalOffset = (float) (this.value * 2.0F);
                    this.updateMessage();
                }
            };
            this.addDrawableChild(verticalOffsetSlider);

            // Reset vertical offset button
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("options.namedloot.reset"), button -> {
                        NamedLootClient.CONFIG.verticalOffset = 0.5F;
                        this.init();
                    }).dimensions(this.width / 2 + 105, ref.yPos, 40, 20).build());
            ref.yPos += 26;

            // Display Distance Slider
            SliderWidget distanceSlider = new SliderWidget(this.width / 2 - 100, ref.yPos, 200, 20,
                    Text.translatable("options.namedloot.display_distance",
                            NamedLootClient.CONFIG.displayDistance == 0 ? "∞" :
                                    String.format("%.1f", NamedLootClient.CONFIG.displayDistance)),
                    NamedLootClient.CONFIG.displayDistance / 64.0F) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.translatable("options.namedloot.display_distance",
                            NamedLootClient.CONFIG.displayDistance == 0 ? "∞" :
                                    String.format("%.1f", NamedLootClient.CONFIG.displayDistance)));
                }

                @Override
                protected void applyValue() {
                    NamedLootClient.CONFIG.displayDistance = (float) (this.value * 64.0F);
                    if (NamedLootClient.CONFIG.displayDistance < 0.5F) {
                        NamedLootClient.CONFIG.displayDistance = 0.0F;
                    }
                    this.updateMessage();
                }
            };
            this.addDrawableChild(distanceSlider);

            // Reset distance button
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("options.namedloot.reset"), button -> {
                        NamedLootClient.CONFIG.displayDistance = 0.0F;
                        this.init();
                    }).dimensions(this.width / 2 + 105, ref.yPos, 40, 20).build());
            ref.yPos += 30;

            // ==========================================================
            // FORMATTING OPTIONS SECTION
            // ==========================================================

            // General formatting section header
            this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                    Text.translatable("options.namedloot.formatting_options").formatted(Formatting.YELLOW, Formatting.BOLD),
                    this.width / 2 - 100, ref.yPos, 0xFFFFFF));
            ref.yPos += 16;

            // Manual formatting toggle
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("options.namedloot.manual_formatting",
                            NamedLootClient.CONFIG.useManualFormatting ? "ON" : "OFF"), button -> {
                        NamedLootClient.CONFIG.useManualFormatting = !NamedLootClient.CONFIG.useManualFormatting;
                        button.setMessage(Text.translatable("options.namedloot.manual_formatting",
                                NamedLootClient.CONFIG.useManualFormatting ? "ON" : "OFF"));
                        this.init();
                    }).dimensions(this.width / 2 - 100, ref.yPos, 200, 20).build());
            ref.yPos += 26;

            // Override item colors toggle
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("options.namedloot.override_colors",
                            NamedLootClient.CONFIG.overrideItemColors ? "ON" : "OFF"), button -> {
                        NamedLootClient.CONFIG.overrideItemColors = !NamedLootClient.CONFIG.overrideItemColors;
                        button.setMessage(Text.translatable("options.namedloot.override_colors",
                                NamedLootClient.CONFIG.overrideItemColors ? "ON" : "OFF"));
                    }).dimensions(this.width / 2 - 100, ref.yPos, 200, 20).build());
            ref.yPos += 26;

            // Show details toggle
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("options.namedloot.show_details",
                            NamedLootClient.CONFIG.showDetails ? "ON" : "OFF"), button -> {
                        NamedLootClient.CONFIG.showDetails = !NamedLootClient.CONFIG.showDetails;
                        button.setMessage(Text.translatable("options.namedloot.show_details",
                                NamedLootClient.CONFIG.showDetails ? "ON" : "OFF"));
                    }).dimensions(this.width / 2 - 100, ref.yPos, 200, 20).build());
            ref.yPos += 26;

            // Use see-through rendering toggle
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("options.namedloot.see_through",
                            NamedLootClient.CONFIG.useSeeThrough ? "ON" : "OFF"), button -> {
                        NamedLootClient.CONFIG.useSeeThrough = !NamedLootClient.CONFIG.useSeeThrough;
                        button.setMessage(Text.translatable("options.namedloot.see_through",
                                NamedLootClient.CONFIG.useSeeThrough ? "ON" : "OFF"));
                    }).dimensions(this.width / 2 - 100, ref.yPos, 200, 20).build());
            ref.yPos += 26;

            // Use background color toggle
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("options.namedloot.background_color",
                            NamedLootClient.CONFIG.useBackgroundColor ? "ON" : "OFF"), button -> {
                        NamedLootClient.CONFIG.useBackgroundColor = !NamedLootClient.CONFIG.useBackgroundColor;
                        button.setMessage(Text.translatable("options.namedloot.background_color",
                                NamedLootClient.CONFIG.useBackgroundColor ? "ON" : "OFF"));
                        this.init();
                    }).dimensions(this.width / 2 - 100, ref.yPos, 200, 20).build());
            ref.yPos += 26;

            // Background color slider (only shown if background color is enabled)
            if (NamedLootClient.CONFIG.useBackgroundColor) {
                // Background color label
                this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("options.namedloot.background_opacity",
                                (int)((NamedLootClient.CONFIG.backgroundColor >>> 24) & 0xFF)),
                        this.width / 2 - 100, ref.yPos, 0xFFFFFF));
                ref.yPos += 16;

                // Background opacity slider
                float opacity = ((NamedLootClient.CONFIG.backgroundColor >>> 24) & 0xFF) / 255.0F;
                SliderWidget bgOpacitySlider = new SliderWidget(this.width / 2 - 100, ref.yPos, 200, 20,
                        Text.translatable("options.namedloot.background_opacity_value", (int)(opacity * 100)),
                        opacity) {
                    @Override
                    protected void updateMessage() {
                        this.setMessage(Text.translatable("options.namedloot.background_opacity_value",
                                (int)(this.value * 100)));
                    }

                    @Override
                    protected void applyValue() {
                        int alpha = (int)(this.value * 255) & 0xFF;
                        NamedLootClient.CONFIG.backgroundColor = (alpha << 24) |
                                (NamedLootClient.CONFIG.backgroundColor & 0x00FFFFFF);
                        this.updateMessage();
                    }
                };
                this.addDrawableChild(bgOpacitySlider);
                ref.yPos += 26;
            }

            // ==========================================================
            // TEXT FORMAT SECTION
            // ==========================================================

            // Text Format Label
            this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                    Text.translatable("options.namedloot.text_format"),
                    this.width / 2 - 100, ref.yPos, 0xFFFFFF));
            ref.yPos += 16;

            // Format description if manual formatting is enabled
            if (NamedLootClient.CONFIG.useManualFormatting) {
                this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("options.namedloot.format_description").formatted(Formatting.GRAY),
                        this.width / 2 - 100, ref.yPos, 0xFFFFFF));
                ref.yPos += 16;
            }

            // Text Format Field
            formatField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, ref.yPos,
                    200, 20, Text.literal(""));
            formatField.setMaxLength(100);
            formatField.setText(NamedLootClient.CONFIG.textFormat);
            formatField.setChangedListener(text -> NamedLootClient.CONFIG.textFormat = text);
            this.addDrawableChild(formatField);

            // Reset format button
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("options.namedloot.reset"), button -> {
                        NamedLootClient.CONFIG.textFormat = "{name} x{count}";
                        formatField.setText("{name} x{count}");
                    }).dimensions(this.width / 2 + 105, ref.yPos, 40, 20).build());
            ref.yPos += 30;

            // Format preview
            String previewText = NamedLootClient.CONFIG.useManualFormatting ?
                    "Preview: " + NamedLootClient.CONFIG.textFormat.replace("{name}", "Diamond").replace("{count}", "64") :
                    "Preview: Diamond x64";
            this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                    Text.literal(previewText),
                    this.width / 2 - 100, ref.yPos, 0xFFFFFF));
            ref.yPos += 30;

            // If manual formatting is enabled, we show the color code reference
            if (NamedLootClient.CONFIG.useManualFormatting) {
                this.addDrawable((context, mouseX, mouseY, delta) -> {
                    // Draw color code reference
                    int colorY = ref.yPos;
                    context.drawTextWithShadow(this.textRenderer,
                            Text.translatable("options.namedloot.format_codes").formatted(Formatting.UNDERLINE),
                            this.width / 2 - 100, colorY, 0xFFFFFF);
                    colorY += 16;

                    // Colors
                    context.drawTextWithShadow(this.textRenderer, Text.literal("&0 ").append(
                            Text.literal("Black").formatted(Formatting.BLACK)), this.width / 2 - 100, colorY, 0xFFFFFF);
                    colorY += 12;

                    context.drawTextWithShadow(this.textRenderer, Text.literal("&1 ").append(
                            Text.literal("Dark Blue").formatted(Formatting.DARK_BLUE)), this.width / 2 - 100, colorY, 0xFFFFFF);
                    colorY += 12;

                    // Continue with all color codes
                    // ... (add all color codes from &0 to &f)

                    // Formatting codes
                    context.drawTextWithShadow(this.textRenderer, Text.literal("&l ").append(
                            Text.literal("Bold").formatted(Formatting.BOLD)), this.width / 2 - 100, colorY, 0xFFFFFF);
                    colorY += 12;

                    context.drawTextWithShadow(this.textRenderer, Text.literal("&o ").append(
                            Text.literal("Italic").formatted(Formatting.ITALIC)), this.width / 2 - 100, colorY, 0xFFFFFF);
                    colorY += 12;

                    context.drawTextWithShadow(this.textRenderer, Text.literal("&n ").append(
                            Text.literal("Underline").formatted(Formatting.UNDERLINE)), this.width / 2 - 100, colorY, 0xFFFFFF);
                    colorY += 12;

                    context.drawTextWithShadow(this.textRenderer, Text.literal("&m ").append(
                            Text.literal("Strikethrough").formatted(Formatting.STRIKETHROUGH)), this.width / 2 - 100, colorY, 0xFFFFFF);
                    colorY += 12;

                    context.drawTextWithShadow(this.textRenderer, Text.literal("&r ").append(
                            Text.literal("Reset")), this.width / 2 - 100, colorY, 0xFFFFFF);
                });

                ref.yPos += 140; // Add space for all the color codes
            } else {
                // ==========================================================
                // NAME COLOR SECTION (only if not using manual formatting)
                // ==========================================================

                this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("options.namedloot.name_color"),
                        this.width / 2 - 100, ref.yPos, 0xFFFFFF));
                ref.yPos += 16;

                // Name style options
                // Bold toggle
                this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("options.namedloot.name_bold",
                                NamedLootClient.CONFIG.nameBold ? "ON" : "OFF"), button -> {
                            NamedLootClient.CONFIG.nameBold = !NamedLootClient.CONFIG.nameBold;
                            button.setMessage(Text.translatable("options.namedloot.name_bold",
                                    NamedLootClient.CONFIG.nameBold ? "ON" : "OFF"));
                        }).dimensions(this.width / 2 - 100, ref.yPos, 95, 20).build());

                // Italic toggle
                this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("options.namedloot.name_italic",
                                NamedLootClient.CONFIG.nameItalic ? "ON" : "OFF"), button -> {
                            NamedLootClient.CONFIG.nameItalic = !NamedLootClient.CONFIG.nameItalic;
                            button.setMessage(Text.translatable("options.namedloot.name_italic",
                                    NamedLootClient.CONFIG.nameItalic ? "ON" : "OFF"));
                        }).dimensions(this.width / 2 + 5, ref.yPos, 95, 20).build());
                ref.yPos += 26;

                // Underline toggle
                this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("options.namedloot.name_underline",
                                NamedLootClient.CONFIG.nameUnderline ? "ON" : "OFF"), button -> {
                            NamedLootClient.CONFIG.nameUnderline = !NamedLootClient.CONFIG.nameUnderline;
                            button.setMessage(Text.translatable("options.namedloot.name_underline",
                                    NamedLootClient.CONFIG.nameUnderline ? "ON" : "OFF"));
                        }).dimensions(this.width / 2 - 100, ref.yPos, 95, 20).build());

                // Strikethrough toggle
                this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("options.namedloot.name_strikethrough",
                                NamedLootClient.CONFIG.nameStrikethrough ? "ON" : "OFF"), button -> {
                            NamedLootClient.CONFIG.nameStrikethrough = !NamedLootClient.CONFIG.nameStrikethrough;
                            button.setMessage(Text.translatable("options.namedloot.name_strikethrough",
                                    NamedLootClient.CONFIG.nameStrikethrough ? "ON" : "OFF"));
                        }).dimensions(this.width / 2 + 5, ref.yPos, 95, 20).build());
                ref.yPos += 26;

                // Name Color Sliders
                this.addNameColorSlider(ref.yPos, "red", NamedLootClient.CONFIG.nameRed);
                ref.yPos += 26;
                this.addNameColorSlider(ref.yPos, "green", NamedLootClient.CONFIG.nameGreen);
                ref.yPos += 26;
                this.addNameColorSlider(ref.yPos, "blue", NamedLootClient.CONFIG.nameBlue);
                ref.yPos += 26;

                // Reset name color button
                this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("options.namedloot.reset_colors"), button -> {
                            NamedLootClient.CONFIG.nameRed = 1.0F;
                            NamedLootClient.CONFIG.nameGreen = 1.0F;
                            NamedLootClient.CONFIG.nameBlue = 1.0F;
                            NamedLootClient.CONFIG.nameBold = false;
                            NamedLootClient.CONFIG.nameItalic = false;
                            NamedLootClient.CONFIG.nameUnderline = false;
                            NamedLootClient.CONFIG.nameStrikethrough = false;
                            this.init();
                        }).dimensions(this.width / 2 - 50, ref.yPos, 100, 20).build());
                ref.yPos += 30;

                // ==========================================================
                // COUNT COLOR SECTION (only if not using manual formatting)
                // ==========================================================

                this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("options.namedloot.count_color"),
                        this.width / 2 - 100, ref.yPos, 0xFFFFFF));
                ref.yPos += 16;

                // Count style options
                // Bold toggle
                this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("options.namedloot.count_bold",
                                NamedLootClient.CONFIG.countBold ? "ON" : "OFF"), button -> {
                            NamedLootClient.CONFIG.countBold = !NamedLootClient.CONFIG.countBold;
                            button.setMessage(Text.translatable("options.namedloot.count_bold",
                                    NamedLootClient.CONFIG.countBold ? "ON" : "OFF"));
                        }).dimensions(this.width / 2 - 100, ref.yPos, 95, 20).build());

                // Italic toggle
                this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("options.namedloot.count_italic",
                                NamedLootClient.CONFIG.countItalic ? "ON" : "OFF"), button -> {
                            NamedLootClient.CONFIG.countItalic = !NamedLootClient.CONFIG.countItalic;
                            button.setMessage(Text.translatable("options.namedloot.count_italic",
                                    NamedLootClient.CONFIG.countItalic ? "ON" : "OFF"));
                        }).dimensions(this.width / 2 + 5, ref.yPos, 95, 20).build());
                ref.yPos += 26;

                // Underline toggle
                this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("options.namedloot.count_underline",
                                NamedLootClient.CONFIG.countUnderline ? "ON" : "OFF"), button -> {
                            NamedLootClient.CONFIG.countUnderline = !NamedLootClient.CONFIG.countUnderline;
                            button.setMessage(Text.translatable("options.namedloot.count_underline",
                                    NamedLootClient.CONFIG.countUnderline ? "ON" : "OFF"));
                        }).dimensions(this.width / 2 - 100, ref.yPos, 95, 20).build());

                // Strikethrough toggle
                this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("options.namedloot.count_strikethrough",
                                NamedLootClient.CONFIG.countStrikethrough ? "ON" : "OFF"), button -> {
                            NamedLootClient.CONFIG.countStrikethrough = !NamedLootClient.CONFIG.countStrikethrough;
                            button.setMessage(Text.translatable("options.namedloot.count_strikethrough",
                                    NamedLootClient.CONFIG.countStrikethrough ? "ON" : "OFF"));
                        }).dimensions(this.width / 2 + 5, ref.yPos, 95, 20).build());
                ref.yPos += 26;

                // Count Color Sliders
                this.addCountColorSlider(ref.yPos, "red", NamedLootClient.CONFIG.countRed);
                ref.yPos += 26;
                this.addCountColorSlider(ref.yPos, "green", NamedLootClient.CONFIG.countGreen);
                ref.yPos += 26;
                this.addCountColorSlider(ref.yPos, "blue", NamedLootClient.CONFIG.countBlue);
                ref.yPos += 26;

                // Reset count color button
                this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("options.namedloot.reset_colors"), button -> {
                            NamedLootClient.CONFIG.countRed = 1.0F;
                            NamedLootClient.CONFIG.countGreen = 1.0F;
                            NamedLootClient.CONFIG.countBlue = 1.0F;
                            NamedLootClient.CONFIG.countBold = false;
                            NamedLootClient.CONFIG.countItalic = false;
                            NamedLootClient.CONFIG.countUnderline = false;
                            NamedLootClient.CONFIG.countStrikethrough = false;
                            this.init();
                        }).dimensions(this.width / 2 - 50, ref.yPos, 100, 20).build());
                ref.yPos += 30;
            }

            // Done button
            this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> {
                // Save config and return to previous screen
                NamedLootClient.saveConfig();
                assert this.client != null;
                this.client.setScreen(this.parent);
            }).dimensions(this.width / 2 - 100, ref.yPos, 200, 20).build());

            // Set initial focus to text field
            this.setInitialFocus(formatField);
        }

        private void addNameColorSlider(int y, String type, float initialValue) {
            SliderWidget slider = new SliderWidget(this.width / 2 - 100, y, 200, 20,
                    Text.translatable("options.namedloot.name_" + type, (int)(initialValue * 255)),
                    initialValue) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.translatable("options.namedloot.name_" + type, (int)(this.value * 255)));
                }

                @Override
                protected void applyValue() {
                    switch (type) {
                        case "red" -> NamedLootClient.CONFIG.nameRed = (float) this.value;
                        case "green" -> NamedLootClient.CONFIG.nameGreen = (float) this.value;
                        case "blue" -> NamedLootClient.CONFIG.nameBlue = (float) this.value;
                    }
                    this.updateMessage();
                }
            };
            this.addDrawableChild(slider);
        }

        private void addCountColorSlider(int y, String type, float initialValue) {
            SliderWidget slider = new SliderWidget(this.width / 2 - 100, y, 200, 20,
                    Text.translatable("options.namedloot.count_" + type, (int)(initialValue * 255)),
                    initialValue) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.translatable("options.namedloot.count_" + type, (int)(this.value * 255)));
                }

                @Override
                protected void applyValue() {
                    switch (type) {
                        case "red" -> NamedLootClient.CONFIG.countRed = (float) this.value;
                        case "green" -> NamedLootClient.CONFIG.countGreen = (float) this.value;
                        case "blue" -> NamedLootClient.CONFIG.countBlue = (float) this.value;
                    }
                    this.updateMessage();
                }
            };
            this.addDrawableChild(slider);
        }

        // Add a new method to ensure the scrollOffset is valid based on current dimensions
        private void validateScrollOffset() {
            int visibleHeight = this.height - 50;
            if (scrollOffset > 0) {
                scrollOffset = 0;
            } else if (contentHeight <= visibleHeight) {
                // If all content fits, no need to scroll
                scrollOffset = 0;
            } else if (scrollOffset < -(contentHeight - visibleHeight)) {
                // Limit scrolling so we don't have empty space at the bottom
                scrollOffset = -(contentHeight - visibleHeight);
            }
        }

        @Override
        public void resize(net.minecraft.client.MinecraftClient client, int width, int height) {
            // When the window is resized, validate the scroll position
            super.resize(client, width, height);

            // After resize is complete, adjust the scroll offset based on the new dimensions
            validateScrollOffset();
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            // Handle scrolling - adjust scrollOffset based on scroll direction
            // Adjust 20 for scroll speed
            scrollOffset = scrollOffset + (int)(verticalAmount * 20);
            // Validate the new scroll position
            validateScrollOffset();
            // Reinitialize all elements with the new scroll position
            this.init();
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            // Handle mouse dragging for scrolling
            if (isScrolling) {
                scrollOffset = scrollOffset - (int)deltaY;
                // Validate the new scroll position
                validateScrollOffset();
                // Reinitialize all elements with the new scroll position
                this.init();
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Start scrolling when mouse is clicked in empty area
            if (button == 0 && mouseX > this.width - 15) { // Left mouse button near scrollbar
                isScrolling = true;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            // Stop scrolling when mouse is released
            if (button == 0) { // Left mouse button
                isScrolling = false;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Draw the background
            this.renderBackground(context, mouseX, mouseY, delta);

            // Draw title at a fixed position (not affected by scroll)
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 16777215);

            // Render all widgets (they already have the scroll offset applied)
            super.render(context, mouseX, mouseY, delta);

            // Render color previews and format preview example
            renderColorPreviews(context);

            // Draw scrollbar if needed
            int visibleHeight = this.height - 50;
            if (contentHeight > visibleHeight) {
                drawScrollbar(context);
            }
        }

        // Method to draw a scrollbar
        private void drawScrollbar(DrawContext context) {
            // Calculate scrollbar position and size
            int visibleHeight = this.height - 50;
            float contentRatio = (float)visibleHeight / Math.max(contentHeight, 1);
            int scrollbarHeight = Math.max((int)(visibleHeight * contentRatio), 32); // Minimum scrollbar height

            // Calculate scrollbar position - handle the case when contentHeight <= visibleHeight
            float scrollRatio = 0;
            if (contentHeight > visibleHeight) {
                scrollRatio = (float)Math.abs(scrollOffset) / Math.max(1, contentHeight - visibleHeight);
            }

            int scrollbarY = 25 + (int)((visibleHeight - scrollbarHeight) * scrollRatio);

            // Draw scrollbar track (semi-transparent background)
            context.fill(this.width - 10, 25, this.width - 6, this.height - 25, 0x40000000);

            // Draw scrollbar thumb (handle)
            context.fill(this.width - 10, scrollbarY, this.width - 6, scrollbarY + scrollbarHeight, 0x80FFFFFF);
        }

        // Separate method to render color previews - this helps ensure they always appear on top
        // Update renderColorPreviews to include text format preview
        private void renderColorPreviews(DrawContext context) {
            int yBase = this.height / 8;

            // Only show color previews if not using manual formatting
            if (!NamedLootClient.CONFIG.useManualFormatting) {
                // Render name color preview
                int namePreviewY = yBase + 119 + scrollOffset;
                // Name color
                int nameRed = (int)(NamedLootClient.CONFIG.nameRed * 255);
                int nameGreen = (int)(NamedLootClient.CONFIG.nameGreen * 255);
                int nameBlue = (int)(NamedLootClient.CONFIG.nameBlue * 255);
                int nameColor = (nameRed << 16) | (nameGreen << 8) | nameBlue | 0xFF000000;

                if (namePreviewY + PREVIEW_SIZE > 25 && namePreviewY < this.height - 25) {
                    drawColorPreview(context, this.width / 2 + PREVIEW_X_OFFSET, namePreviewY, nameColor);
                }

                // Render count color preview
                int countPreviewY = yBase + 247 + scrollOffset;
                // Count color
                int countRed = (int)(NamedLootClient.CONFIG.countRed * 255);
                int countGreen = (int)(NamedLootClient.CONFIG.countGreen * 255);
                int countBlue = (int)(NamedLootClient.CONFIG.countBlue * 255);
                int countColor = (countRed << 16) | (countGreen << 8) | countBlue | 0xFF000000;

                if (countPreviewY + PREVIEW_SIZE > 25 && countPreviewY < this.height - 25) {
                    drawColorPreview(context, this.width / 2 + PREVIEW_X_OFFSET, countPreviewY, countColor);
                }
            }

            // Fix the format preview text position
            int formatPreviewY = yBase + 26 + scrollOffset; // Move it near the top so it's visible

            // Only render the preview if it's in the visible area
            if (formatPreviewY > 25 && formatPreviewY < this.height - 25) {
                // Create the preview text
                MutableText previewText = createPreviewText();

                // Draw the preview text in a dedicated box that won't overlap with other elements
                context.fill(this.width / 2 - 100, formatPreviewY - 2,
                        this.width / 2 + 100, formatPreviewY + 12, 0x40000000);

                context.drawCenteredTextWithShadow(
                        this.textRenderer,
                        previewText,
                        this.width / 2,
                        formatPreviewY,
                        0xFFFFFFFF
                );
            }
        }

        // Create a separate method for generating the preview text
        private MutableText createPreviewText() {
            MutableText previewText = Text.literal("");

            if (NamedLootClient.CONFIG.useManualFormatting) {
                // For manual formatting, use the parsing method
                String format = NamedLootClient.CONFIG.textFormat;
                format = format.replace("{name}", "Diamond").replace("{count}", "64");

                // Apply formatting codes
                int currentIndex = 0;
                int formatIndex;

                while (currentIndex < format.length()) {
                    formatIndex = format.indexOf('&', currentIndex);

                    if (formatIndex == -1) {
                        // No more format codes, add remaining text
                        previewText.append(Text.literal(format.substring(currentIndex)));
                        break;
                    }

                    // Add text before the format code
                    if (formatIndex > currentIndex) {
                        previewText.append(Text.literal(format.substring(currentIndex, formatIndex)));
                    }

                    // Make sure we have a character after the &
                    if (formatIndex + 1 >= format.length()) {
                        previewText.append(Text.literal("&"));
                        break;
                    }

                    // Get the format code
                    char formatCode = format.charAt(formatIndex + 1);
                    Formatting mcFormat = null;

                    // Map the format code to MC's Formatting enum
                    switch (formatCode) {
                        case '0': mcFormat = Formatting.BLACK; break;
                        case '1': mcFormat = Formatting.DARK_BLUE; break;
                        case '2': mcFormat = Formatting.DARK_GREEN; break;
                        case '3': mcFormat = Formatting.DARK_AQUA; break;
                        case '4': mcFormat = Formatting.DARK_RED; break;
                        case '5': mcFormat = Formatting.DARK_PURPLE; break;
                        case '6': mcFormat = Formatting.GOLD; break;
                        case '7': mcFormat = Formatting.GRAY; break;
                        case '8': mcFormat = Formatting.DARK_GRAY; break;
                        case '9': mcFormat = Formatting.BLUE; break;
                        case 'a': mcFormat = Formatting.GREEN; break;
                        case 'b': mcFormat = Formatting.AQUA; break;
                        case 'c': mcFormat = Formatting.RED; break;
                        case 'd': mcFormat = Formatting.LIGHT_PURPLE; break;
                        case 'e': mcFormat = Formatting.YELLOW; break;
                        case 'f': mcFormat = Formatting.WHITE; break;
                        case 'l': mcFormat = Formatting.BOLD; break;
                        case 'm': mcFormat = Formatting.STRIKETHROUGH; break;
                        case 'n': mcFormat = Formatting.UNDERLINE; break;
                        case 'o': mcFormat = Formatting.ITALIC; break;
                        case 'r': mcFormat = Formatting.RESET; break;
                    }

                    // Find the next formatting code or the end of the string
                    int nextFormatIndex = format.indexOf('&', formatIndex + 2);
                    if (nextFormatIndex == -1) {
                        nextFormatIndex = format.length();
                    }

                    // Add the text with the formatting applied
                    String formattedSection = format.substring(formatIndex + 2, nextFormatIndex);
                    previewText.append(mcFormat == null ?
                            Text.literal("&" + formatCode + formattedSection) :
                            Text.literal(formattedSection).formatted(mcFormat));

                    // Move to the next format code
                    currentIndex = nextFormatIndex;
                }
            } else {
                // For automatic coloring, create a preview with the configured styles
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
                        previewText.append(Text.literal(format.substring(currentIndex)));
                        break;
                    } else if (nameIndex != -1 && (countIndex == -1 || nameIndex < countIndex)) {
                        // Name comes next
                        // Add text before the placeholder
                        if (nameIndex > currentIndex) {
                            previewText.append(Text.literal(format.substring(currentIndex, nameIndex)));
                        }

                        // Get name color
                        int nameRed = (int)(NamedLootClient.CONFIG.nameRed * 255);
                        int nameGreen = (int)(NamedLootClient.CONFIG.nameGreen * 255);
                        int nameBlue = (int)(NamedLootClient.CONFIG.nameBlue * 255);
                        int nameColor = (nameRed << 16) | (nameGreen << 8) | nameBlue;

                        // Create style with all enabled formatting options for name
                        Style nameStyle = Style.EMPTY.withColor(nameColor);
                        if (NamedLootClient.CONFIG.nameBold) nameStyle = nameStyle.withBold(true);
                        if (NamedLootClient.CONFIG.nameItalic) nameStyle = nameStyle.withItalic(true);
                        if (NamedLootClient.CONFIG.nameUnderline) nameStyle = nameStyle.withUnderline(true);
                        if (NamedLootClient.CONFIG.nameStrikethrough) nameStyle = nameStyle.withStrikethrough(true);

                        // Add the name with its style
                        previewText.append(Text.literal("Diamond").setStyle(nameStyle));
                        currentIndex = nameIndex + 6; // Skip over "{name}"
                    } else {
                        // Count comes next
                        // Add text before the placeholder
                        if (countIndex > currentIndex) {
                            previewText.append(Text.literal(format.substring(currentIndex, countIndex)));
                        }

                        // Get count color
                        int countRed = (int)(NamedLootClient.CONFIG.countRed * 255);
                        int countGreen = (int)(NamedLootClient.CONFIG.countGreen * 255);
                        int countBlue = (int)(NamedLootClient.CONFIG.countBlue * 255);
                        int countColor = (countRed << 16) | (countGreen << 8) | countBlue;

                        // Create style with all enabled formatting options for count
                        Style countStyle = Style.EMPTY.withColor(countColor);
                        if (NamedLootClient.CONFIG.countBold) countStyle = countStyle.withBold(true);
                        if (NamedLootClient.CONFIG.countItalic) countStyle = countStyle.withItalic(true);
                        if (NamedLootClient.CONFIG.countUnderline) countStyle = countStyle.withUnderline(true);
                        if (NamedLootClient.CONFIG.countStrikethrough) countStyle = countStyle.withStrikethrough(true);

                        // Add the count with its style
                        previewText.append(Text.literal("64").setStyle(countStyle));
                        currentIndex = countIndex + 7; // Skip over "{count}"
                    }
                }
            }

            return previewText;
        }

        // Helper method to draw a color preview with borders and optional transparency grid
        private void drawColorPreview(DrawContext context, int x, int y, int color) {
            // Draw outer border (black)
            context.fill(x - 2, y - 2, x + PREVIEW_SIZE + 2, y + PREVIEW_SIZE + 2, 0xFF000000);
            // Draw inner border (white)
            context.fill(x - 1, y - 1, x + PREVIEW_SIZE + 1, y + PREVIEW_SIZE + 1, 0xFFFFFFFF);
            // Draw color preview with exact pixel values
            context.fill(x, y, x + PREVIEW_SIZE, y + PREVIEW_SIZE, color);
        }
    }
}