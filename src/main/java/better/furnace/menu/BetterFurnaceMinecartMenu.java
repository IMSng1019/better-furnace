package better.furnace.menu;

import better.furnace.BetterFurnaceScreenHandlers;
import better.furnace.minecart.BetterFurnaceMinecartMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * 动力矿车单槽燃料 GUI 对应的 ScreenHandler。
 */
public class BetterFurnaceMinecartMenu extends AbstractContainerMenu {
	private static final int SLOT_FUEL = 0;

	private static final int PLAYER_INV_START = 1;
	private static final int PLAYER_INV_END = 28;
	private static final int HOTBAR_START = 28;
	private static final int HOTBAR_END = 37;

	private final Container container;
	private final ContainerData data;
	private final int entityId;

	public BetterFurnaceMinecartMenu(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
		this(syncId, playerInventory, new SimpleContainer(1), new SimpleContainerData(4), buf.readVarInt());
	}

	public BetterFurnaceMinecartMenu(int syncId, Inventory playerInventory, Container container, ContainerData data, int entityId) {
		super(BetterFurnaceScreenHandlers.FURNACE_MINECART_MENU, syncId);
		checkContainerSize(container, 1);
		checkContainerDataCount(data, 4);
		this.container = container;
		this.data = data;
		this.entityId = entityId;
		container.startOpen(playerInventory.player);

		// 动力矿车燃料槽（居中）
		this.addSlot(new Slot(container, SLOT_FUEL, 80, 48) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return AbstractFurnaceBlockEntity.isFuel(stack);
			}
		});

		// 玩家背包
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}

		// 玩家快捷栏
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
		}

		this.addDataSlots(data);
	}

	@Override
	public boolean stillValid(Player player) {
		return this.container.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack copied = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack source = slot.getItem();
		copied = source.copy();

		if (index == SLOT_FUEL) {
			if (!this.moveItemStackTo(source, PLAYER_INV_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (AbstractFurnaceBlockEntity.isFuel(source)) {
			if (!this.moveItemStackTo(source, SLOT_FUEL, SLOT_FUEL + 1, false)) {
				if (index < HOTBAR_START) {
					if (!this.moveItemStackTo(source, HOTBAR_START, HOTBAR_END, false)) {
						return ItemStack.EMPTY;
					}
				} else if (!this.moveItemStackTo(source, PLAYER_INV_START, PLAYER_INV_END, false)) {
					return ItemStack.EMPTY;
				}
			}
		} else if (index < HOTBAR_START) {
			if (!this.moveItemStackTo(source, HOTBAR_START, HOTBAR_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(source, PLAYER_INV_START, PLAYER_INV_END, false)) {
			return ItemStack.EMPTY;
		}

		if (source.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		if (source.getCount() == copied.getCount()) {
			return ItemStack.EMPTY;
		}

		slot.onTake(player, source);
		return copied;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.container.stopOpen(player);
	}

	public int getEntityId() {
		return this.entityId;
	}

	public boolean isLit() {
		return this.data.get(0) > 0;
	}

	public int getLitProgress() {
		int burnTime = this.data.get(0);
		int burnDuration = this.data.get(1);
		if (burnDuration <= 0) {
			burnDuration = 200;
		}
		return Mth.clamp(burnTime * 13 / burnDuration, 0, 13);
	}

	public BetterFurnaceMinecartMode getMode() {
		return this.data.get(2) == 1 ? BetterFurnaceMinecartMode.DECELERATE : BetterFurnaceMinecartMode.ACCELERATE;
	}

	public boolean isBraking() {
		return this.data.get(3) == 1;
	}
}
