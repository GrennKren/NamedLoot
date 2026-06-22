package com.namedloot.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.namedloot.NamedLoot;
import com.namedloot.NamedLootClient;
import com.namedloot.WorldRenderEventHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

import java.util.function.Consumer;

public class NamedLootModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return NamedLootConfigScreen::new;
    }

    public static class NamedLootConfigScreen extends Screen {
        private final Screen parent;
        private EditBox formatField;

        private int previousHeight;

        // Constants for color preview dimensions
        private static final int PREVIEW_SIZE = 30;
        private static final int PREVIEW_X_OFFSET = 120;

        // Add scrolling variables
        private int scrollOffset = 0;
        private boolean isScrolling = false;
        private int contentHeight = 450; // Approximate height of all content

        private int nameColorLabelYPos;
        private int countColorLabelYPos;
        private int textFormatLabelYPos;
        private int formatDescriptionYPos;

        private static final int SECTION_TITLE_COLOR = 0xFFFFAA00;
        private static final int SECTION_SEPARATOR_COLOR = 0x66FFFFFF;

        private boolean needsInlineColorReference = false;

        // private List<CheckboxWidget> checkboxes = new ArrayList<>();

        private int currentTab = 0; // 0: Default, 1: Advanced

        private final java.util.List<java.util.function.Consumer<GuiGraphicsExtractor>> deferredDraws = new java.util.ArrayList<>();

        public NamedLootConfigScreen(Screen parent) {
            super(Component.translatable("text.namedloot.config"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            // Increased space below tabs

            if (previousHeight != 0 && previousHeight != this.height) {
                validateScrollOffset();
            }
            previousHeight = this.height;

            this.clearWidgets();
            deferredDraws.clear();

            // Store tab buttons separately - don't add them as drawableChild yet
            // We'll render them manually in render()

            if (currentTab == 0) {
                renderDefaultTab();
            } else {
                renderAdvancedTab();
            }

            validateScrollOffset();
        }

        private void renderDefaultTab() {
            int yPos = 80 + scrollOffset;

            // ==========================================================
            // GENERAL SECTION
            // ==========================================================
            drawSectionHeader(yPos, "options.namedloot.section.general");
            yPos += 20;

            // Global Enable/Disable Toggle
            addCheckbox(
                    "options.namedloot.mod_enabled",
                    NamedLootClient.CONFIG.enabled,
                    (checkbox) -> NamedLootClient.CONFIG.enabled = checkbox,
                    this.width / 2 - 100, yPos, 200,
                    "options.namedloot.tooltip.mod_enabled"
            );
            yPos += 24;

            // Vertical Offset Slider
            AbstractSliderButton verticalOffsetSlider = new AbstractSliderButton(this.width / 2 - 100, yPos, 200, 20,
                    Component.translatable("options.namedloot.vertical_offset", NamedLootClient.CONFIG.verticalOffset),
                    NamedLootClient.CONFIG.verticalOffset / 2.0F) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Component.translatable("options.namedloot.vertical_offset",
                            String.format("%.2f", NamedLootClient.CONFIG.verticalOffset)));
                }

                @Override
                protected void applyValue() {
                    NamedLootClient.CONFIG.verticalOffset = (float) (this.value * 2.0F);
                    this.updateMessage();
                }
            };
            this.addRenderableWidget(verticalOffsetSlider);

            // Reset vertical offset button
            this.addRenderableWidget(Button.builder(
                            Component.translatable("options.namedloot.reset"), button -> {
                                NamedLootClient.CONFIG.verticalOffset = 0.5F;
                                this.init();
                            }).pos(this.width / 2 + 105, yPos).size(40, 20)
                    .tooltip(Tooltip.create(Component.translatable("options.namedloot.tooltip.reset_format")))
                    .build());
            yPos += 26;

            // Display Distance Slider
            AbstractSliderButton distanceSlider = new AbstractSliderButton(this.width / 2 - 100, yPos, 200, 20,
                    Component.translatable("options.namedloot.display_distance",
                            NamedLootClient.CONFIG.displayDistance == 0 ? "∞" :
                                    String.format("%.1f", NamedLootClient.CONFIG.displayDistance)),
                    NamedLootClient.CONFIG.displayDistance / 64.0F) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Component.translatable("options.namedloot.display_distance",
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
            this.addRenderableWidget(distanceSlider);

            // Reset distance button
            this.addRenderableWidget(Button.builder(
                            Component.translatable("options.namedloot.reset"), button -> {
                                NamedLootClient.CONFIG.displayDistance = 0.0F;
                                this.init();
                            }).pos(this.width / 2 + 105, yPos).size(40, 20)
                    .tooltip(Tooltip.create(Component.translatable("options.namedloot.tooltip.reset_format")))
                    .build());
            yPos += 30;

            // ==========================================================
            // DISPLAY OPTIONS SECTION
            // ==========================================================
            drawSectionHeader(yPos, "options.namedloot.section.display");
            yPos += 20;

            // Replace button toggles with checkboxes
            addCheckbox(
                    "options.namedloot.override_colors",
                    NamedLootClient.CONFIG.overrideItemColors,
                    (checkbox) -> NamedLootClient.CONFIG.overrideItemColors = checkbox,
                    this.width / 2 - 100, yPos, 200,
                    null
            );
            yPos += 24;

            addCheckbox(
                    "options.namedloot.show_details",
                    NamedLootClient.CONFIG.showDetails,
                    (checkbox) -> {
                        NamedLootClient.CONFIG.showDetails = checkbox;
                        this.init(); // Reinitialize to show/hide the sub-option
                    },
                    this.width / 2 - 100, yPos, 200,
                    null
            );
            yPos += 24;

            // Show details on hover sub-option (only shown if showDetails is enabled)
            if (NamedLootClient.CONFIG.showDetails) {
                addCheckbox(
                        "options.namedloot.show_details_on_hover",
                        NamedLootClient.CONFIG.showDetailsOnlyOnHover,
                        (checkbox) -> NamedLootClient.CONFIG.showDetailsOnlyOnHover = checkbox,
                        this.width / 2 - 80, yPos, 160,
                        null
                );
                yPos += 24;
            }

            addCheckbox(
                    "options.namedloot.show_name_on_hover",
                    NamedLootClient.CONFIG.showNameOnHover,
                    (checkbox) -> {
                        NamedLootClient.CONFIG.showNameOnHover = checkbox;
                        this.init(); // Refresh to show/hide sub-option
                    },
                    this.width / 2 - 100, yPos, 200,
                    null
            );
            yPos += 24;

            addCheckbox(
                    "options.namedloot.see_through",
                    NamedLootClient.CONFIG.useSeeThrough,
                    (checkbox) -> NamedLootClient.CONFIG.useSeeThrough = checkbox,
                    this.width / 2 - 100, yPos, 200,
                    null
            );
            yPos += 24;

            addCheckbox(
                    "options.namedloot.background_color",
                    NamedLootClient.CONFIG.useBackgroundColor,
                    (checkbox) -> {
                        NamedLootClient.CONFIG.useBackgroundColor = checkbox;
                        this.init();
                    },
                    this.width / 2 - 100, yPos, 200,
                    null
            );
            yPos += 24;

            // Background color slider (only shown if background color is enabled)
            if (NamedLootClient.CONFIG.useBackgroundColor) {
                // Item name background opacity slider
                int labelSliderBackgroundOpacityY = yPos;
                float opacity = ((NamedLootClient.CONFIG.backgroundColor >>> 24) & 0xFF) / 255.0F;
                deferredDraws.add(context -> context.text(this.font,
                        Component.translatable("options.namedloot.item_background_opacity"),
                        this.width / 2 - 100, labelSliderBackgroundOpacityY, 0xFFFFFFFF));
                yPos += 16;

                AbstractSliderButton bgOpacitySlider = new AbstractSliderButton(this.width / 2 - 100, yPos, 200, 20,
                        Component.translatable("options.namedloot.background_opacity_value", (int)(opacity * 100)),
                        opacity) {
                    @Override
                    protected void updateMessage() {
                        this.setMessage(Component.translatable("options.namedloot.background_opacity_value",
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
                this.addRenderableWidget(bgOpacitySlider);
                yPos += 26;

                // Detail background type options as radio-style buttons
                int detailBackgroundTypeYPos = yPos;
                deferredDraws.add(context -> context.text(this.font,
                        Component.translatable("options.namedloot.detail_background_type"),
                        this.width / 2 - 100, detailBackgroundTypeYPos, 0xFFFFFFFF));
                yPos += 16;

                // Two buttons side by side that act like radio buttons
                Button boxButton = Button.builder(
                        Component.literal("Box"), button -> {
                            NamedLootClient.CONFIG.useDetailBackgroundBox = true;
                            this.init();
                        }).pos(this.width / 2 - 100, yPos).size(95, 20).build();

                Button inlineButton = Button.builder(
                        Component.literal("Inline"), button -> {
                            NamedLootClient.CONFIG.useDetailBackgroundBox = false;
                            this.init();
                        }).pos(this.width / 2 + 5, yPos).size(95, 20).build();

                // Visually highlight the selected option
                if (NamedLootClient.CONFIG.useDetailBackgroundBox) {
                    boxButton.active = false; // Disable the selected button
                } else {
                    inlineButton.active = false;
                }

                this.addRenderableWidget(boxButton);
                this.addRenderableWidget(inlineButton);
                yPos += 26;

                // Detail background opacity slider
                int labelSliderDetailBackgroundOpacityY = yPos;
                float detailOpacity = ((NamedLootClient.CONFIG.detailBackgroundColor >>> 24) & 0xFF) / 255.0F;
                deferredDraws.add(context -> context.text(this.font,
                        Component.translatable("options.namedloot.detail_background_opacity"),
                        this.width / 2 - 100, labelSliderDetailBackgroundOpacityY, 0xFFFFFFFF));
                yPos += 16;

                AbstractSliderButton detailBgOpacitySlider = new AbstractSliderButton(this.width / 2 - 100, yPos, 200, 20,
                        Component.translatable("options.namedloot.background_opacity_value", (int)(detailOpacity * 100)),
                        detailOpacity) {
                    @Override
                    protected void updateMessage() {
                        this.setMessage(Component.translatable("options.namedloot.background_opacity_value",
                                (int)(this.value * 100)));
                    }

                    @Override
                    protected void applyValue() {
                        int alpha = (int)(this.value * 255) & 0xFF;
                        NamedLootClient.CONFIG.detailBackgroundColor = (alpha << 24) |
                                (NamedLootClient.CONFIG.detailBackgroundColor & 0x00FFFFFF);
                        this.updateMessage();
                    }
                };
                this.addRenderableWidget(detailBgOpacitySlider);
                yPos += 26;
            }

            // ==========================================================
            // TEXT FORMAT SECTION
            // ==========================================================
            yPos += 10;
            drawSectionHeader(yPos, "options.namedloot.section.formatting");
            yPos += 20;

            // Manual formatting toggle with a more descriptive label
            this.addRenderableWidget(Button.builder(
                    Component.translatable("options.namedloot.manual_formatting",
                            NamedLootClient.CONFIG.useManualFormatting ? "ON" : "OFF"), button -> {
                        // If manual formatting is active, save the manual value and restore the automatic value
                        if (NamedLootClient.CONFIG.useManualFormatting) {
                            NamedLootClient.CONFIG.manualTextFormat = NamedLootClient.CONFIG.textFormat;
                            NamedLootClient.CONFIG.textFormat = NamedLootClient.CONFIG.automaticTextFormat;
                        } else {
                            // If manual formatting is not active, save the automatic value and restore the manual value
                            NamedLootClient.CONFIG.automaticTextFormat = NamedLootClient.CONFIG.textFormat;
                            NamedLootClient.CONFIG.textFormat = NamedLootClient.CONFIG.manualTextFormat;
                        }
                        NamedLootClient.CONFIG.useManualFormatting = !NamedLootClient.CONFIG.useManualFormatting;
                        button.setMessage(Component.translatable("options.namedloot.manual_formatting",
                                NamedLootClient.CONFIG.useManualFormatting ? "ON" : "OFF"));
                        this.init();
                    }).pos(this.width / 2 - 100, yPos).size(200, 20).build());
            yPos += 26;

            // Fix the text format label and field positioning
            this.textFormatLabelYPos = yPos;
            deferredDraws.add(context -> context.text(this.font,
                    Component.translatable("options.namedloot.text_format"),
                    this.width / 2 - 100, textFormatLabelYPos, 0xFFFFFFFF));
            yPos += 15;

            // Format description if manual formatting is enabled
            this.formatDescriptionYPos = yPos;
            if (NamedLootClient.CONFIG.useManualFormatting) {
                deferredDraws.add(context -> context.text(this.font,
                        Component.translatable("options.namedloot.format_description").withStyle(ChatFormatting.GRAY),
                        this.width / 2 - 100, formatDescriptionYPos, 0xFFFFFFFF));
            }

            yPos += 36;

            // Text Format Field
            formatField = new EditBox(this.font, this.width / 2 - 100, yPos,
                    200, 20, Component.literal(""));
            formatField.setMaxLength(100);
            formatField.setValue(NamedLootClient.CONFIG.textFormat);
            formatField.setResponder(text -> NamedLootClient.CONFIG.textFormat = text);
            this.addRenderableWidget(formatField);

            // Reset format button
            this.addRenderableWidget(Button.builder(
                            Component.translatable("options.namedloot.reset"), button -> {
                                NamedLootClient.CONFIG.textFormat = "{name} x{count}";
                                formatField.setValue("{name} x{count}");
                            }).pos(this.width / 2 + 105, yPos).size(40, 20)
                    .tooltip(Tooltip.create(Component.translatable("options.namedloot.tooltip.reset_format")))
                    .build());
            yPos += 30;

            // If manual formatting is enabled, we show the color code reference
            if (NamedLootClient.CONFIG.useManualFormatting) {
                deferredDraws.add(context -> {
                    // Draw color code reference
                    renderColorCodeReference(context);
                });

                yPos += 170; // Add space for all the color codes
            } else {
                // ==========================================================
                // NAME COLOR SECTION (only if not using manual formatting)
                // ==========================================================
                yPos += 15;

                this.nameColorLabelYPos = yPos;
                deferredDraws.add(context -> context.text(this.font,
                        Component.translatable("options.namedloot.name_color"),
                        this.width / 2 - 100, nameColorLabelYPos, 0xFFFFFFFF));
                yPos += 16;

                // Name style options as checkboxes
                addCheckbox(
                        "options.namedloot.name_bold",
                        NamedLootClient.CONFIG.nameBold,
                        (checkbox) -> NamedLootClient.CONFIG.nameBold = checkbox,
                        this.width / 2 - 100, yPos, 95,
                        null
                );

                addCheckbox(
                        "options.namedloot.name_italic",
                        NamedLootClient.CONFIG.nameItalic,
                        (checkbox) -> NamedLootClient.CONFIG.nameItalic = checkbox,
                        this.width / 2 + 5, yPos, 95,
                        null
                );
                yPos += 24;

                addCheckbox(
                        "options.namedloot.name_underline",
                        NamedLootClient.CONFIG.nameUnderline,
                        (checkbox) -> NamedLootClient.CONFIG.nameUnderline = checkbox,
                        this.width / 2 - 100, yPos, 95,
                        null
                );

                addCheckbox(
                        "options.namedloot.name_strikethrough",
                        NamedLootClient.CONFIG.nameStrikethrough,
                        (checkbox) -> NamedLootClient.CONFIG.nameStrikethrough = checkbox,
                        this.width / 2 + 5, yPos, 95,
                        null
                );
                yPos += 26;

                // Name Color Sliders
                this.addNameColorSlider(yPos, "red", NamedLootClient.CONFIG.nameRed);
                yPos += 26;
                this.addNameColorSlider(yPos, "green", NamedLootClient.CONFIG.nameGreen);
                yPos += 26;
                this.addNameColorSlider(yPos, "blue", NamedLootClient.CONFIG.nameBlue);
                yPos += 26;

                // Reset name color button
                this.addRenderableWidget(Button.builder(
                                Component.translatable("options.namedloot.reset_colors"), button -> {
                                    NamedLootClient.CONFIG.nameRed = 1.0F;
                                    NamedLootClient.CONFIG.nameGreen = 1.0F;
                                    NamedLootClient.CONFIG.nameBlue = 1.0F;
                                    NamedLootClient.CONFIG.nameBold = false;
                                    NamedLootClient.CONFIG.nameItalic = false;
                                    NamedLootClient.CONFIG.nameUnderline = false;
                                    NamedLootClient.CONFIG.nameStrikethrough = false;
                                    this.init();
                                }).pos(this.width / 2 - 50, yPos).size(100, 20)
                        .tooltip(Tooltip.create(Component.translatable("options.namedloot.tooltip.reset_format")))
                        .build());
                yPos += 30;

                // ==========================================================
                // COUNT COLOR SECTION (only if not using manual formatting)
                // ==========================================================

                this.countColorLabelYPos = yPos;
                deferredDraws.add(context -> context.text(this.font,
                        Component.translatable("options.namedloot.count_color"),
                        this.width / 2 - 100, countColorLabelYPos, 0xFFFFFFFF));
                yPos += 16;

                // Count style options as checkboxes
                addCheckbox(
                        "options.namedloot.count_bold",
                        NamedLootClient.CONFIG.countBold,
                        (checkbox) -> NamedLootClient.CONFIG.countBold = checkbox,
                        this.width / 2 - 100, yPos, 95,
                        null
                );

                addCheckbox(
                        "options.namedloot.count_italic",
                        NamedLootClient.CONFIG.countItalic,
                        (checkbox) -> NamedLootClient.CONFIG.countItalic = checkbox,
                        this.width / 2 + 5, yPos, 95,
                        null
                );
                yPos += 24;

                addCheckbox(
                        "options.namedloot.count_underline",
                        NamedLootClient.CONFIG.countUnderline,
                        (checkbox) -> NamedLootClient.CONFIG.countUnderline = checkbox,
                        this.width / 2 - 100, yPos, 95,
                        null
                );

                addCheckbox(
                        "options.namedloot.count_strikethrough",
                        NamedLootClient.CONFIG.countStrikethrough,
                        (checkbox) -> NamedLootClient.CONFIG.countStrikethrough = checkbox,
                        this.width / 2 + 5, yPos, 95,
                        null
                );
                yPos += 26;

                // Count Color Sliders
                this.addCountColorSlider(yPos, "red", NamedLootClient.CONFIG.countRed);
                yPos += 26;
                this.addCountColorSlider(yPos, "green", NamedLootClient.CONFIG.countGreen);
                yPos += 26;
                this.addCountColorSlider(yPos, "blue", NamedLootClient.CONFIG.countBlue);
                yPos += 26;

                // Reset count color button
                this.addRenderableWidget(Button.builder(
                                Component.translatable("options.namedloot.reset_colors"), button -> {
                                    NamedLootClient.CONFIG.countRed = 1.0F;
                                    NamedLootClient.CONFIG.countGreen = 1.0F;
                                    NamedLootClient.CONFIG.countBlue = 1.0F;
                                    NamedLootClient.CONFIG.countBold = false;
                                    NamedLootClient.CONFIG.countItalic = false;
                                    NamedLootClient.CONFIG.countUnderline = false;
                                    NamedLootClient.CONFIG.countStrikethrough = false;
                                    this.init();
                                }).pos(this.width / 2 - 50, yPos).size(100, 20)
                        .tooltip(Tooltip.create(Component.translatable("options.namedloot.tooltip.reset_format")))
                        .build());
                yPos += 30;
            }

            // Save and close button with clearer text
            this.addRenderableWidget(Button.builder(
                    Component.translatable("options.namedloot.save_and_close"), button -> {
                        // Save config and return to previous screen
                        NamedLootClient.saveConfig();
                        this.minecraft.gui.setScreen(this.parent);
                    }).pos(this.width / 2 - 100, yPos).size(200, 20).build());
            yPos += 20;

            // Set initial focus to text field
            this.setInitialFocus(formatField);

            // Calculate final content height based on actual rendered positions
            int finalYPos = yPos;
            this.contentHeight = finalYPos - (80 + scrollOffset);
        }

        private void renderAdvancedTab() {
            int yPos = 80 + scrollOffset;

            // ==========================================================
            // ADVANCED RULES SECTION
            // ==========================================================
            drawSectionHeader(yPos, "options.namedloot.section.advanced_rules");
            yPos += 20;

            // Add Rules Button (adds a new rule group)
            this.addRenderableWidget(Button.builder(
                            Component.translatable("options.namedloot.add_rules"), button -> {
                                NamedLootClient.CONFIG.advancedRules.add(new NamedLootConfig.AdvancedRule());
                                this.init();
                            }).pos(this.width / 2 - 50, yPos).size(100, 20)
                    .tooltip(Tooltip.create(Component.translatable("options.namedloot.add_rules")))
                    .build());
            yPos += 30;


            if (NamedLootClient.CONFIG.advancedRules.isEmpty()) {
                final int noRulesY = yPos;
                deferredDraws.add(context -> {
                    MutableComponent helpText = Component.translatable("options.namedloot.no_rules_help").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
                    context.centeredText(this.font, helpText,
                            this.width / 2, noRulesY, 0xFFFFFFFF);
                });
                yPos += 40;
            } else {
                int ruleDisplayIndex = 1;
                int configRuleIndex = 0;
                while (configRuleIndex < NamedLootClient.CONFIG.advancedRules.size()) {
                    final int groupStartIndex = configRuleIndex;
                    NamedLootConfig.AdvancedRule firstRuleInGroup = NamedLootClient.CONFIG.advancedRules.get(groupStartIndex);

                    // --- Find all conditions that belong to this rule group ---
                    final List<NamedLootConfig.AdvancedRule> groupConditions = new ArrayList<>();
                    groupConditions.add(firstRuleInGroup);

                    int nextIndex = groupStartIndex + 1;
                    while (nextIndex < NamedLootClient.CONFIG.advancedRules.size()) {
                        NamedLootConfig.AdvancedRule nextRule = NamedLootClient.CONFIG.advancedRules.get(nextIndex);
                        // A chained condition is identified by a null or empty textFormat string.
                        if (nextRule.textFormat != null && !nextRule.textFormat.isEmpty()) {
                            break; // This is the start of a new rule group.
                        }
                        groupConditions.add(nextRule);
                        nextIndex++;
                    }

                    // --- Render Rule Header and Remove Button for the entire group ---
                    final int headerY = yPos;
                    int finalRuleDisplayIndex = ruleDisplayIndex;
                    deferredDraws.add(context -> {
                        MutableComponent ruleTitle = Component.literal("Rule " + finalRuleDisplayIndex).withStyle(ChatFormatting.BOLD);
                        context.text(this.font, ruleTitle,
                                this.width / 2 - 100, headerY, SECTION_TITLE_COLOR);
                    });

                    this.addRenderableWidget(Button.builder(
                            Component.literal("−").withStyle(ChatFormatting.RED), btn -> {
                                NamedLootClient.CONFIG.advancedRules.removeAll(groupConditions);
                                this.init();
                            }).pos(this.width / 2 + 80, yPos).size(20, 20).build());
                    yPos += 25;

                    // Enable/Disable Toggle for this specific rule group
                    addCheckbox(
                            "options.namedloot.rule_enabled",
                            firstRuleInGroup.ruleEnabled,
                            (checkbox) -> firstRuleInGroup.ruleEnabled = checkbox,
                            this.width / 2 - 100, yPos, 200,
                            "options.namedloot.tooltip.rule_enabled"
                    );
                    yPos += 24;

                    // --- Loop through and render each condition in the group ---
                    for (int i = 0; i < groupConditions.size(); i++) {
                        final int conditionIndexInConfig = groupStartIndex + i;
                        final NamedLootConfig.AdvancedRule conditionRule = groupConditions.get(i);

                        if (i > 0) {
                            final int andY = yPos;
                            deferredDraws.add(context -> context.centeredText(this.font,
                                            Component.literal("AND").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                                            this.width / 2, andY, 0xFFFFFFFF));
                            yPos += 15;
                        }

                        final int conditionLabelY = yPos;
                        deferredDraws.add(context -> context.text(this.font,
                                        Component.translatable("options.namedloot.condition"),
                                        this.width / 2 - 100, conditionLabelY, 0xFFFFFFFF));
                        yPos += 16;

                        // Condition toggle buttons
                        int buttonWidth = 45;
                        int buttonSpacing = 5;
                        int startX = this.width / 2 - 100;

                        Button containsButton = Button.builder(
                                        Component.literal("Contains"), button -> {
                                            conditionRule.condition = "Contains";
                                            this.init();
                                        }).pos(startX, yPos).size(buttonWidth, 20)
                                .tooltip(Tooltip.create(Component.translatable("options.namedloot.tooltip.name_match")))
                                .build();

                        Button countLessButton = Button.builder(
                                        Component.literal("Count <"), button -> {
                                            conditionRule.condition = "Count <";
                                            this.init();
                                        }).pos(startX + (buttonWidth + buttonSpacing), yPos).size(buttonWidth, 20)
                                .tooltip(Tooltip.create(Component.translatable("options.namedloot.tooltip.count_less")))
                                .build();

                        Button countMoreButton = Button.builder(
                                        Component.literal("Count >"), button -> {
                                            conditionRule.condition = "Count >";
                                            this.init();
                                        }).pos(startX + (buttonWidth + buttonSpacing) * 2, yPos).size(buttonWidth, 20)
                                .tooltip(Tooltip.create(Component.translatable("options.namedloot.tooltip.count_more")))
                                .build();

                        Button countEqualButton = Button.builder(
                                        Component.literal("Count ="), button -> {
                                            conditionRule.condition = "Count =";
                                            this.init();
                                        }).pos(startX + (buttonWidth + buttonSpacing) * 3, yPos).size(buttonWidth, 20)
                                .tooltip(Tooltip.create(Component.translatable("options.namedloot.tooltip.count_equal")))
                                .build();

                        containsButton.active = !"Contains".equals(conditionRule.condition);
                        countLessButton.active = !"Count <".equals(conditionRule.condition);
                        countMoreButton.active = !"Count >".equals(conditionRule.condition);
                        countEqualButton.active = !"Count =".equals(conditionRule.condition);

                        this.addRenderableWidget(containsButton);
                        this.addRenderableWidget(countLessButton);
                        this.addRenderableWidget(countMoreButton);
                        this.addRenderableWidget(countEqualButton);
                        yPos += 26;

                        // Value label, text field, and the new remove condition button
                        final int valueLabelY = yPos;
                        deferredDraws.add(context -> context.text(this.font,
                                        Component.translatable("options.namedloot.rule_value"),
                                        this.width / 2 - 100, valueLabelY, 0xFFFFFFFF));
                        yPos += 16;

                        EditBox valueField = new EditBox(this.font, this.width / 2 - 100, yPos, 180, 20, Component.literal(""));
                        valueField.setValue(conditionRule.value);
                        valueField.setResponder(text -> conditionRule.value = text);
                        this.addRenderableWidget(valueField);

                        // New remove condition ('-') button
                        this.addRenderableWidget(Button.builder(
                                        Component.literal("−").withStyle(ChatFormatting.RED), button -> {
                                            NamedLootClient.CONFIG.advancedRules.remove(conditionIndexInConfig);
                                            // If the first rule in a group is deleted, promote the next one to be the new "leader"
                                            if (conditionIndexInConfig == groupStartIndex && groupConditions.size() > 1) {
                                                NamedLootClient.CONFIG.advancedRules.get(groupStartIndex).textFormat = firstRuleInGroup.textFormat;
                                            }
                                            this.init();
                                        }).pos(this.width / 2 + 85, yPos).size(20, 20)
                                .tooltip(Tooltip.create(Component.translatable("options.namedloot.tooltip.remove_condition")))
                                .build());
                        yPos += 26;
                    }

                    // --- New Add Condition ('+') Button ---
                    this.addRenderableWidget(Button.builder(
                                    Component.translatable("options.namedloot.add_condition").withStyle(ChatFormatting.GREEN), button -> {
                                        int insertAtIndex = groupStartIndex + groupConditions.size();
                                        NamedLootConfig.AdvancedRule newCondition = new NamedLootConfig.AdvancedRule();
                                        newCondition.textFormat = ""; // Empty format marks it as a chained condition
                                        NamedLootClient.CONFIG.advancedRules.add(insertAtIndex, newCondition);
                                        this.init();
                                    }).pos(this.width / 2 - 100, yPos).size(205, 20)
                            .tooltip(Tooltip.create(Component.translatable("options.namedloot.tooltip.add_condition")))
                            .build());
                    yPos += 26;


                    // --- Shared Format Field for the Rule Group ---
                    final int formatLabelY = yPos;
                    deferredDraws.add(context -> context.text(this.font,
                                    Component.translatable("options.namedloot.rule_format"),
                                    this.width / 2 - 100, formatLabelY, 0xFFFFFFFF));
                    yPos += 16;

                    EditBox formatField = new EditBox(this.font, this.width / 2 - 100, yPos, 200, 20, Component.literal(""));
                    formatField.setValue(firstRuleInGroup.textFormat);
                    formatField.setResponder(text -> NamedLootClient.CONFIG.advancedRules.get(groupStartIndex).textFormat = text);
                    this.addRenderableWidget(formatField);

                    this.addRenderableWidget(Button.builder(
                                    Component.translatable("options.namedloot.reset"), button -> {
                                        NamedLootClient.CONFIG.advancedRules.get(groupStartIndex).textFormat = "{name} x{count}";
                                        this.init();
                                    }).pos(this.width / 2 + 105, yPos).size(40, 20)
                            .tooltip(Tooltip.create(Component.translatable("options.namedloot.tooltip.reset_format")))
                            .build());
                    yPos += 26;

                    // Rule separator line
                    if (configRuleIndex + groupConditions.size() < NamedLootClient.CONFIG.advancedRules.size()) {
                        final int separatorY = yPos;
                        deferredDraws.add(context -> context.fill(this.width / 2 - 80, separatorY,
                                this.width / 2 + 80, separatorY + 1, 0x33FFFFFF));
                        yPos += 10;
                    }
                    yPos += 10;

                    // Update loop counters
                    configRuleIndex += groupConditions.size();
                    ruleDisplayIndex++;
                }
            }

            // Save and close button (consistent with default tab)
            this.addRenderableWidget(Button.builder(
                    Component.translatable("options.namedloot.save_and_close"), button -> {
                        NamedLootClient.saveConfig();
                        this.minecraft.gui.setScreen(this.parent);
                    }).pos(this.width / 2 - 100, yPos).size(200, 20).build());
            yPos += 20;

            int minSpacing = 20;
            int referenceWidth = 230;
            int mainContentWidth = 410;

            int availableLeftSpace = (this.width / 2 - mainContentWidth / 2) - minSpacing;
            boolean canFitLeft = availableLeftSpace >= referenceWidth;

            needsInlineColorReference = !(canFitLeft); //!(canFitLeft && referenceBottomY < saveButtonY);

            int extraTopPadding = 12;
            int finalYPos = yPos;
            int computedContentHeight;
            if (needsInlineColorReference) {
                // Reference ditempatkan inline dengan jarak ekstra
                int inlineY = yPos + extraTopPadding;
                deferredDraws.add(context -> renderColorCodeContentAt(context, this.width / 2 - 100, this.width / 2 + 20, inlineY, false));
                //yPos += extraTopPadding + referenceBoxHeight;
                computedContentHeight = finalYPos - (80 + scrollOffset) + 190;
            }else{
                computedContentHeight = finalYPos - (80 + scrollOffset);
            }


            this.contentHeight = computedContentHeight; //Math.max(computedContentHeight, this.contentHeight);
        }


        // Helper method to draw a section header with a separator line
        private void drawSectionHeader(int yPos, String translationKey) {
            final int y = yPos;
            deferredDraws.add(context -> {
                // Draw section title
                MutableComponent sectionTitle = Component.translatable(translationKey).withStyle(ChatFormatting.BOLD);
                context.text(this.font, sectionTitle,
                        this.width / 2 - 100, y, SECTION_TITLE_COLOR);

                // Draw separator line
                context.fill(this.width / 2 - 100, y + 12,
                        this.width / 2 + 100, y + 13, SECTION_SEPARATOR_COLOR);
            });
        }

        // Helper method for adding checkboxes with consistent styling
        private void addCheckbox(String translationKey, boolean configValue, Consumer<Boolean> configSetter,
                                 int x, int y, int width, @Nullable String tooltipKey) {
            MutableComponent label = Component.empty()
                    .append(Component.literal(configValue ? "☑ " : "☐ ").withStyle(ChatFormatting.GREEN))
                    .append(Component.translatable(translationKey));

            Button checkbox = Button.builder(label, button -> {
                        boolean newState = !configValue;
                        configSetter.accept(newState);
                        this.init(); // refresh
                    }).pos(x, y).size(width, 20)
                    .tooltip(tooltipKey != null ? Tooltip.create(Component.translatable(tooltipKey)) : null)
                    .build();

            this.addRenderableWidget(checkbox);
        }

        private void addNameColorSlider(int y, String type, float initialValue) {
            AbstractSliderButton slider = new AbstractSliderButton(this.width / 2 - 100, y, 200, 20,
                    Component.translatable("options.namedloot.name_" + type, (int)(initialValue * 255)),
                    initialValue) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Component.translatable("options.namedloot.name_" + type, (int)(this.value * 255)));
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
            this.addRenderableWidget(slider);
        }

        private void addCountColorSlider(int y, String type, float initialValue) {
            AbstractSliderButton slider = new AbstractSliderButton(this.width / 2 - 100, y, 200, 20,
                    Component.translatable("options.namedloot.count_" + type, (int)(initialValue * 255)),
                    initialValue) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Component.translatable("options.namedloot.count_" + type, (int)(this.value * 255)));
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
            this.addRenderableWidget(slider);
        }

        // Add a new method to ensure the scrollOffset is valid based on current dimensions
        private void validateScrollOffset() {
            final int contentYStart = 80;
            final int bottomMargin = 25;
            int visibleHeight = this.height - contentYStart - bottomMargin;

            if (scrollOffset > 0) {
                scrollOffset = 0;
            }

            if (contentHeight <= visibleHeight) {
                scrollOffset = 0;
            } else if (scrollOffset < -(contentHeight - visibleHeight)) {
                scrollOffset = -(contentHeight - visibleHeight);
            }
        }

        @Override
        public void resize(int width, int height) {
            super.resize(width, height);
        }


        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {

            // Do immediate scroll too for responsive feel
            scrollOffset = scrollOffset + (int)(verticalAmount * 20);
            // Validate the scroll position
            validateScrollOffset();
            // Reinitialize with the new scroll position
            this.init();
            return true;
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
            // Handle mouse dragging for scrolling
            if (isScrolling) {
                scrollOffset = scrollOffset - (int) offsetY;
                // Validate the new scroll position
                validateScrollOffset();
                // Reinitialize all elements with the new scroll position
                this.init();
                return true;
            }
            return super.mouseDragged(click, offsetX, offsetY);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            // Handle tab button clicks first (before scissor area)
            if (click.y() >= 35 && click.y() <= 55) {
                if (click.x() >= (double) this.width / 2 - 100 && click.x() <= (double) this.width / 2 - 5) {
                    // Default tab clicked
                    currentTab = 0;
                    this.init();
                    return true;
                } else if (click.x() >= (double) this.width / 2 + 5 && click.x() <= (double) this.width / 2 + 100) {
                    // Advanced tab clicked
                    currentTab = 1;
                    this.init();
                    return true;
                }
            }

            // Handle scrollbar clicks
            if (click.button() == 0 && click.x() > this.width - 15) {
                isScrolling = true;
                return true;
            }

            // Only handle other clicks if they're in the content area
            if (click.y() >= 80) {
                return super.mouseClicked(click, doubled);
            }

            return false;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent click) {
            // Stop scrolling when mouse is released
            if (click.button() == 0) { // Left mouse button
                isScrolling = false;
            }
            return super.mouseReleased(click);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            // Render background
            //this.renderBackground(context, mouseX, mouseY, delta);

            // Draw title
            MutableComponent titleText = Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
                    .append(this.title)
                    .append(Component.literal(" ✦").withStyle(ChatFormatting.GOLD));
            int titleX = this.width / 2;
            int titleY = 15;

            context.centeredText(this.font, titleText, titleX, titleY, 0xFFFFFFFF);
            //context.fill(this.width / 4, titleY + 12, this.width * 3/4, titleY + 13, 0x55FFFFFF);

            // Render tab buttons manually (always visible, not affected by scroll)
            renderTabButtons(context, mouseX, mouseY, delta);

            // Create scissor area for scrollable content
            int clipStartY = 80;
            int clipHeight = this.height - clipStartY - 25;

            context.enableScissor(0, clipStartY, this.width, clipStartY + clipHeight);

            // Render all scrollable content
            for (java.util.function.Consumer<GuiGraphicsExtractor> draw : deferredDraws) {
                draw.accept(context);
            }

            super.extractRenderState(context, mouseX, mouseY, delta);

            // Render color previews
            renderColorPreviews(context);

            // Render color code reference for Advanced tab (outside scissor)
            renderColorCodeReference(context);

            context.disableScissor();

            // Draw scrollbar outside scissor area
            if (contentHeight > clipHeight) {
                drawScrollbar(context);
            }
        }

        private void renderTabButtons(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            // Create tab buttons
            Button defaultButton = Button.builder(
                    Component.literal("Default"), button -> {
                        currentTab = 0;
                        this.init();
                    }).pos(this.width / 2 - 100, 35).size(95, 20).build();

            Button advancedButton = Button.builder(
                    Component.literal("Advanced"), button -> {
                        currentTab = 1;
                        this.init();
                    }).pos(this.width / 2 + 5, 35).size(95, 20).build();

            // Highlight active tab by making it inactive (darker)
            if (currentTab == 0) {
                defaultButton.active = false;
            } else {
                advancedButton.active = false;
            }

            // Render buttons
            defaultButton.extractRenderState(context, mouseX, mouseY, delta);
            advancedButton.extractRenderState(context, mouseX, mouseY, delta);
        }



        // Improved scrollbar with smoother appearance
        private void drawScrollbar(GuiGraphicsExtractor context) {
            int visibleHeight = this.height - 80 - 25;
            float contentRatio = (float)visibleHeight / Math.max(contentHeight, 1);
            int scrollbarHeight = Math.max((int)(visibleHeight * contentRatio), 32);

            float scrollRatio = 0;
            if (contentHeight > visibleHeight) {
                scrollRatio = (float)Math.abs(scrollOffset) / Math.max(1, contentHeight - visibleHeight);
            }

            int scrollbarY = 80 + (int)((visibleHeight - scrollbarHeight) * scrollRatio);
            int scrollbarX = this.width - 10;

            context.fillGradient(
                    scrollbarX, 80,
                    scrollbarX + 4, this.height - 25,
                    0x40000000, 0x40202020
            );

            context.fillGradient(
                    scrollbarX, scrollbarY,
                    scrollbarX + 4, scrollbarY + scrollbarHeight,
                    0x80BBBBBB, 0x80999999
            );

            context.outline(scrollbarX, scrollbarY, 4, scrollbarHeight, 0x40FFFFFF);
        }

        // Separate method to render color previews
        private void renderColorPreviews(GuiGraphicsExtractor context) {
            // Only show color previews if not using manual formatting AND in Default tab
            if (currentTab == 0 && !NamedLootClient.CONFIG.useManualFormatting) {
                // Render name color preview with gradient for a more appealing look
                int namePreviewY = nameColorLabelYPos + 88;
                // Name color
                int nameRed = (int)(NamedLootClient.CONFIG.nameRed * 255);
                int nameGreen = (int)(NamedLootClient.CONFIG.nameGreen * 255);
                int nameBlue = (int)(NamedLootClient.CONFIG.nameBlue * 255);
                int nameColor = (nameRed << 16) | (nameGreen << 8) | nameBlue | 0xFF000000;

                if (namePreviewY + PREVIEW_SIZE > 25 && namePreviewY < this.height - 25) {
                    drawEnhancedColorPreview(context, this.width / 2 + PREVIEW_X_OFFSET, namePreviewY, nameColor);
                }

                // Render count color preview
                int countPreviewY = countColorLabelYPos + 88;
                // Count color
                int countRed = (int)(NamedLootClient.CONFIG.countRed * 255);
                int countGreen = (int)(NamedLootClient.CONFIG.countGreen * 255);
                int countBlue = (int)(NamedLootClient.CONFIG.countBlue * 255);
                int countColor = (countRed << 16) | (countGreen << 8) | countBlue | 0xFF000000;

                if (countPreviewY + PREVIEW_SIZE > 25 && countPreviewY < this.height - 25) {
                    drawEnhancedColorPreview(context, this.width / 2 + PREVIEW_X_OFFSET, countPreviewY, countColor);
                }
            }

            if (currentTab == 0) {
                // Fix the format preview text position
                int formatPreviewY = this.formatField != null ? this.formatField.getY() - 20 : 0;

                // Only render the preview if it's in the visible area
                if (formatPreviewY > 25 && formatPreviewY < this.height - 25) {
                    // Create the preview text
                    MutableComponent previewText = createPreviewText();

                    // Draw the preview text in a dedicated box with a subtle gradient background
                    int boxWidth = 200;
                    int boxHeight = 20;
                    int boxX = this.width / 2 - boxWidth / 2;
                    int boxY = formatPreviewY - 5;

                    // Draw a pretty gradient background for the preview
                    context.fillGradient(
                            boxX, boxY,
                            boxX + boxWidth, boxY + boxHeight,
                            0x40202040, 0x40404080
                    );

                    // Add subtle border
                    context.outline(boxX, boxY, boxWidth, boxHeight, 0x55AAAAAA);

                    context.centeredText(
                            this.font,
                            previewText,
                            this.width / 2,
                            formatPreviewY,
                            0xFFFFFFFF
                    );
                }
            }
        }

        private void renderColorCodeReference(GuiGraphicsExtractor context) {
            if (currentTab == 0 && NamedLootClient.CONFIG.useManualFormatting) {
                renderColorCodeContentAt(context, this.width / 2 - 100, this.width / 2 + 20,
                        this.formatField.getY() + 26, false);
            } else if (currentTab == 1) {
                // Advanced tab - Check if there's enough space for left side placement

                int referenceBoxHeight = 320;
                int minSpacing = 20;
                int referenceWidth = 230;
                int mainContentWidth = 410;
                int referenceX = this.width / 2 - mainContentWidth / 2 - referenceWidth - minSpacing;
                int referenceY = 120;
                if (!needsInlineColorReference) {

                    context.fillGradient(
                            referenceX - 10, referenceY - 10,
                            referenceX + referenceWidth + 10, referenceY + referenceBoxHeight + 10,
                            0x80000000, 0x80202020
                    );
                    context.outline(referenceX - 10, referenceY - 10, referenceWidth + 20, referenceBoxHeight + 20, 0x88FFFFFF);

                    renderColorCodeContentAt(context, referenceX, referenceX + 110, referenceY, true);
                }
            }
        }

        private void renderColorCodeContentAt(GuiGraphicsExtractor context, int leftX, int rightX, int startY, boolean withTitle) {
            int colorY = startY;

            if (withTitle) {
                context.text(this.font,
                        Component.translatable("options.namedloot.format_codes").withStyle(ChatFormatting.UNDERLINE),
                        leftX, colorY, 0xFFFFFFFF);
                colorY += 20;
            } else {
                context.text(this.font,
                        Component.translatable("options.namedloot.format_codes").withStyle(ChatFormatting.UNDERLINE),
                        leftX, colorY, 0xFFFFFFFF);
                colorY += 16;
            }

            // Color codes dalam 2 kolom
            context.text(this.font, Component.literal("&0 ").append(
                    Component.literal("Black").withStyle(ChatFormatting.BLACK)), leftX, colorY, 0xFFFFFFFF);
            context.text(this.font, Component.literal("&8 ").append(
                    Component.literal("Dark Gray").withStyle(ChatFormatting.DARK_GRAY)), rightX, colorY, 0xFFFFFFFF);
            colorY += 12;

            context.text(this.font, Component.literal("&1 ").append(
                    Component.literal("Dark Blue").withStyle(ChatFormatting.DARK_BLUE)), leftX, colorY, 0xFFFFFFFF);
            context.text(this.font, Component.literal("&9 ").append(
                    Component.literal("Blue").withStyle(ChatFormatting.BLUE)), rightX, colorY, 0xFFFFFFFF);
            colorY += 12;

            context.text(this.font, Component.literal("&2 ").append(
                    Component.literal("Dark Green").withStyle(ChatFormatting.DARK_GREEN)), leftX, colorY, 0xFFFFFFFF);
            context.text(this.font, Component.literal("&a ").append(
                    Component.literal("Green").withStyle(ChatFormatting.GREEN)), rightX, colorY, 0xFFFFFFFF);
            colorY += 12;

            context.text(this.font, Component.literal("&3 ").append(
                    Component.literal("Dark Aqua").withStyle(ChatFormatting.DARK_AQUA)), leftX, colorY, 0xFFFFFFFF);
            context.text(this.font, Component.literal("&b ").append(
                    Component.literal("Aqua").withStyle(ChatFormatting.AQUA)), rightX, colorY, 0xFFFFFFFF);
            colorY += 12;

            context.text(this.font, Component.literal("&4 ").append(
                    Component.literal("Dark Red").withStyle(ChatFormatting.DARK_RED)), leftX, colorY, 0xFFFFFFFF);
            context.text(this.font, Component.literal("&c ").append(
                    Component.literal("Red").withStyle(ChatFormatting.RED)), rightX, colorY, 0xFFFFFFFF);
            colorY += 12;

            context.text(this.font, Component.literal("&5 ").append(
                    Component.literal("Dark Purple").withStyle(ChatFormatting.DARK_PURPLE)), leftX, colorY, 0xFFFFFFFF);
            context.text(this.font, Component.literal("&d ").append(
                    Component.literal("Light Purple").withStyle(ChatFormatting.LIGHT_PURPLE)), rightX, colorY, 0xFFFFFFFF);
            colorY += 12;

            context.text(this.font, Component.literal("&6 ").append(
                    Component.literal("Gold").withStyle(ChatFormatting.GOLD)), leftX, colorY, 0xFFFFFFFF);
            context.text(this.font, Component.literal("&e ").append(
                    Component.literal("Yellow").withStyle(ChatFormatting.YELLOW)), rightX, colorY, 0xFFFFFFFF);
            colorY += 12;

            context.text(this.font, Component.literal("&7 ").append(
                    Component.literal("Gray").withStyle(ChatFormatting.GRAY)), leftX, colorY, 0xFFFFFFFF);
            context.text(this.font, Component.literal("&f ").append(
                    Component.literal("White").withStyle(ChatFormatting.WHITE)), rightX, colorY, 0xFFFFFFFF);
            colorY += 18;

            // Formatting codes section header
            context.text(this.font,
                    Component.literal("Formatting Codes:").withStyle(ChatFormatting.UNDERLINE),
                    leftX, colorY, 0xFFFFFFFF);
            colorY += 16;

            // Formatting codes
            context.text(this.font, Component.literal("&l ").append(
                    Component.literal("Bold").withStyle(ChatFormatting.BOLD)), leftX, colorY, 0xFFFFFFFF);
            context.text(this.font, Component.literal("&n ").append(
                    Component.literal("Underline").withStyle(ChatFormatting.UNDERLINE)), rightX, colorY, 0xFFFFFFFF);
            colorY += 12;

            context.text(this.font, Component.literal("&o ").append(
                    Component.literal("Italic").withStyle(ChatFormatting.ITALIC)), leftX, colorY, 0xFFFFFFFF);
            context.text(this.font, Component.literal("&m ").append(
                    Component.literal("Strikethrough").withStyle(ChatFormatting.STRIKETHROUGH)), rightX, colorY, 0xFFFFFFFF);
            colorY += 12;

            context.text(this.font, Component.literal("&k ").append(
                    Component.literal("Obfuscated").withStyle(ChatFormatting.OBFUSCATED)), leftX, colorY, 0xFFFFFFFF);
            context.text(this.font, Component.literal("&r ").append(
                    Component.literal("Reset")), rightX, colorY, 0xFFFFFFFF);
        }

        // Enhanced color preview with label and better visuals
        private void drawEnhancedColorPreview(GuiGraphicsExtractor context, int x, int y, int color) {
            int previewWidth = PREVIEW_SIZE;
            int previewHeight = PREVIEW_SIZE;

            // Draw outer border (dark with gradient)
            context.fillGradient(
                    x - 2, y - 2,
                    x + previewWidth + 2, y + previewHeight + 2,
                    0xFF222222, 0xFF444444
            );

            // Draw inner border (light)
            context.fill(x - 1, y - 1, x + previewWidth + 1, y + previewHeight + 1, 0xFFAAAAAA);

            // Draw color preview
            context.fill(x, y, x + previewWidth, y + previewHeight, color);

        }


        // Create a separate method for generating the preview text
        private MutableComponent createPreviewText() {
            // Create example ItemStack for preview
            ItemStack previewItem;
            try {
                previewItem = Items.DIAMOND.getDefaultInstance().copyWithCount(64);
            } catch (Throwable t) {
                previewItem = null;
            }

            // Use the same text formatting methods as in WorldRenderEventHandler for consistency
            if (previewItem == null) {
                String fmt = NamedLootClient.CONFIG.textFormat;
                String preview = fmt.replace("{name}", "Diamond").replace("{count}", "64");
                return Component.literal(preview);
            } else if (NamedLootClient.CONFIG.useManualFormatting) {
                // For manual formatting, use the same parsing method
                return WorldRenderEventHandler.parseFormattedText(
                        NamedLootClient.CONFIG.textFormat,
                        previewItem,
                        String.valueOf(previewItem.getCount()));
            } else {
                // For automatic coloring, use the same formatting method
                return WorldRenderEventHandler.createAutomaticFormattedText(
                        previewItem,
                        String.valueOf(previewItem.getCount()));
            }
        }
    }
}