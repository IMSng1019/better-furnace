package better.furnace.client.gui;

import better.furnace.BetterFurnace;
import better.furnace.menu.BetterFurnaceMinecartMenu;
import better.furnace.minecart.BetterFurnaceMinecartMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 动力矿车燃料界面（单槽 + 火焰动画）。
 */
public class BetterFurnaceMinecartScreen extends AbstractContainerScreen<BetterFurnaceMinecartMenu> {
	private static final ResourceLocation TEXTURE = BetterFurnace.id("textures/gui/container/furnace.png");

	public BetterFurnaceMinecartScreen(BetterFurnaceMinecartMenu handler, Inventory inventory, Component title) {
		super(handler, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 166;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		this.renderBackground(guiGraphics, mouseX, mouseY, delta);
		super.render(guiGraphics, mouseX, mouseY, delta);
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float delta, int mouseX, int mouseY) {
		int left = this.leftPos;
		int top = this.topPos;
		guiGraphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight);

		if (this.menu.isLit()) {
			int flame = this.menu.getLitProgress();
			guiGraphics.blit(TEXTURE, left + 81, top + 36 + 12 - flame, 176, 12 - flame, 14, flame + 1);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
		guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

		Component modeText = this.menu.getMode() == BetterFurnaceMinecartMode.DECELERATE
			? Component.translatable("gui.better-furnace.mode.decelerate")
			: Component.translatable("gui.better-furnace.mode.accelerate");
		guiGraphics.drawString(this.font, modeText, 8, 73, 0x666666, false);

		if (this.menu.isBraking()) {
			guiGraphics.drawString(this.font, Component.translatable("gui.better-furnace.braking"), 98, 73, 0xAA0000, false);
		}
	}
}
