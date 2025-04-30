package com.namedloot.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import com.namedloot.NamedLootClient;

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
        private final int contentHeight = 450; // Approximate height of all content

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

            // Original init code continues
            this.clearChildren();
            int yBase = this.height / 8;

            // Vertical Offset Slider
            SliderWidget verticalOffsetSlider = new SliderWidget(this.width / 2 - 100, yBase + scrollOffset, 200, 20,
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
                    }).dimensions(this.width / 2 + 105, yBase + scrollOffset, 40, 20).build());

            // Display Distance Slider (0-64 blocks, 0 = unlimited)
            SliderWidget distanceSlider = new SliderWidget(this.width / 2 - 100, yBase + 26 + scrollOffset, 200, 20,
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
                    }).dimensions(this.width / 2 + 105, yBase + 26 + scrollOffset, 40, 20).build());

            // Text Format Label
            this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                    Text.translatable("options.namedloot.text_format"),
                    this.width / 2 - 100, yBase + 56 + scrollOffset, 0xFFFFFF));

            // Text Format Field
            formatField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, yBase + 70 + scrollOffset,
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
                    }).dimensions(this.width / 2 + 105, yBase + 70 + scrollOffset, 40, 20).build());

            // NAME COLOR SECTION
            this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                    Text.translatable("options.namedloot.name_color"),
                    this.width / 2 - 100, yBase + 100 + scrollOffset, 0xFFFFFF));

            // Name Color Sliders
            this.addNameColorSlider(yBase + 120 + scrollOffset, "red",
                    NamedLootClient.CONFIG.nameRed);
            this.addNameColorSlider(yBase + 146 + scrollOffset, "green",
                    NamedLootClient.CONFIG.nameGreen);
            this.addNameColorSlider(yBase + 172 + scrollOffset, "blue",
                    NamedLootClient.CONFIG.nameBlue);

            // Reset name color button
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("options.namedloot.reset_colors"), button -> {
                        NamedLootClient.CONFIG.nameRed = 1.0F;
                        NamedLootClient.CONFIG.nameGreen = 1.0F;
                        NamedLootClient.CONFIG.nameBlue = 1.0F;
                        this.init();
                    }).dimensions(this.width / 2 - 50, yBase + 198 + scrollOffset, 100, 20).build());

            // COUNT COLOR SECTION
            this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                    Text.translatable("options.namedloot.count_color"),
                    this.width / 2 - 100, yBase + 228 + scrollOffset, 0xFFFFFF));

            // Count Color Sliders
            this.addCountColorSlider(yBase + 248 + scrollOffset, "red",
                    NamedLootClient.CONFIG.countRed);
            this.addCountColorSlider(yBase + 274 + scrollOffset, "green",
                    NamedLootClient.CONFIG.countGreen);
            this.addCountColorSlider(yBase + 300 + scrollOffset, "blue",
                    NamedLootClient.CONFIG.countBlue);

            // Reset count color button
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("options.namedloot.reset_colors"), button -> {
                        NamedLootClient.CONFIG.countRed = 1.0F;
                        NamedLootClient.CONFIG.countGreen = 1.0F;
                        NamedLootClient.CONFIG.countBlue = 1.0F;
                        this.init();
                    }).dimensions(this.width / 2 - 50, yBase + 326 + scrollOffset, 100, 20).build());

            // Done button
            this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> {
                // Save config and return to previous screen
                NamedLootClient.saveConfig();
                assert this.client != null;
                this.client.setScreen(this.parent);
            }).dimensions(this.width / 2 - 100, yBase + 382 + scrollOffset, 200, 20).build());

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
            // Then render the color previews *after* all widgets
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
            float contentRatio = (float)visibleHeight / contentHeight;
            int scrollbarHeight = Math.max((int)(visibleHeight * contentRatio), 32); // Minimum scrollbar height
            // Calculate scrollbar position
            float scrollRatio = (float)Math.abs(scrollOffset) / Math.max(1, contentHeight - visibleHeight);
            int scrollbarY = 25 + (int)((visibleHeight - scrollbarHeight) * scrollRatio);
            // Draw scrollbar track
            context.fill(this.width - 10, 25, this.width - 6, this.height - 25, 0x40000000);
            // Draw scrollbar thumb
            context.fill(this.width - 10, scrollbarY, this.width - 6, scrollbarY + scrollbarHeight, 0x80FFFFFF);
        }

        // Separate method to render color previews - this helps ensure they always appear on top
        private void renderColorPreviews(DrawContext context) {
            int yBase = this.height / 6;

            // Render name color preview
            int namePreviewY = yBase + 119 + scrollOffset;
            // Name color
            int nameRed = (int)(NamedLootClient.CONFIG.nameRed * 255);
            int nameGreen = (int)(NamedLootClient.CONFIG.nameGreen * 255);
            int nameBlue = (int)(NamedLootClient.CONFIG.nameBlue * 255);
            int nameColor = (nameRed << 16) | (nameGreen << 8) | nameBlue | 0xFF000000;

            if (namePreviewY + PREVIEW_SIZE > 25 && namePreviewY < this.height - 25) {
                drawColorPreview(context, this.width / 2 + PREVIEW_X_OFFSET, namePreviewY, nameColor
                );
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