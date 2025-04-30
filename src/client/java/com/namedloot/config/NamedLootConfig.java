package com.namedloot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.namedloot.NamedLoot;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class NamedLootConfig {
    // Default values
    public float verticalOffset = 0.5F;
    public float displayDistance = 0.0F; // 0 means unlimited
    public String textFormat = "{name} x{count}";

    // Colors for item name (white default)
    public float nameRed = 1.0F;
    public float nameGreen = 1.0F;
    public float nameBlue = 1.0F;

    // Colors for count (white default)
    public float countRed = 1.0F;
    public float countGreen = 1.0F;
    public float countBlue = 1.0F;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/namedloot.json");

    public static NamedLootConfig load() {
        NamedLootConfig config = new NamedLootConfig();

        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                NamedLootConfig loaded = GSON.fromJson(reader, NamedLootConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException e) {
                NamedLoot.LOGGER.error("Failed to load config", e);
            }
        }

        save(config); // Create default config
        return config;
    }

    public static void save(NamedLootConfig config) {
        try {
            //if (!CONFIG_FILE.getParentFile().exists()) {
            //    CONFIG_FILE.getParentFile().mkdirs();
            //}

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            NamedLoot.LOGGER.error("Failed to save config", e);
        }
    }
}