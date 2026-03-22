package better.furnace;

import better.furnace.chunk.BetterFurnaceChunkLoadingManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.resources.ResourceLocation;

public class BetterFurnace implements ModInitializer {
	public static final String MOD_ID = "better-furnace";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		BetterFurnaceScreenHandlers.register();
		BetterFurnaceNetworking.registerServer();
		BetterFurnaceChunkLoadingManager.registerLifecycleCallbacks();
		LOGGER.info("Better Furnace initialized.");
	}
}
