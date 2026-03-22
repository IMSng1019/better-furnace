package better.furnace;

import better.furnace.menu.BetterFurnaceMinecartMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.world.inventory.MenuType;

/**
 * 所有 ScreenHandler（菜单）注册点。
 */
public final class BetterFurnaceScreenHandlers {
	public static MenuType<BetterFurnaceMinecartMenu> FURNACE_MINECART_MENU;

	private BetterFurnaceScreenHandlers() {
	}

	public static void register() {
		FURNACE_MINECART_MENU = ScreenHandlerRegistry.registerExtended(
			BetterFurnace.id("furnace_minecart"),
			BetterFurnaceMinecartMenu::new
		);
	}
}
