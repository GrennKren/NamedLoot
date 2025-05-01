package com.namedloot;

import com.namedloot.config.NamedLootConfig;
import net.fabricmc.api.ClientModInitializer;

public class NamedLootClient implements ClientModInitializer {
	public static NamedLootConfig CONFIG;

	@Override
	public void onInitializeClient() {
		// Load config
		CONFIG = NamedLootConfig.load();

		// Register the world render event handler for AFTER_ENTITIES events
		WorldRenderEventHandler.registerEvents();

		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		NamedLoot.LOGGER.info("Initializing NamedLoot client features");
	}

	public static void saveConfig() {
		NamedLootConfig.save(CONFIG);
	}
}