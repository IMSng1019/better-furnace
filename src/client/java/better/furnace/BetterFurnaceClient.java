package better.furnace;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screens.MenuScreens;
import better.furnace.client.gui.BetterFurnaceMinecartScreen;

public class BetterFurnaceClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(BetterFurnaceScreenHandlers.FURNACE_MINECART_MENU, BetterFurnaceMinecartScreen::new);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (ClientPlayNetworking.canSend(BetterFurnaceNetworking.CLIENT_READY)) {
				ClientPlayNetworking.send(BetterFurnaceNetworking.CLIENT_READY, PacketByteBufs.empty());
			}
		});
	}
}
