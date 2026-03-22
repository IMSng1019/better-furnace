package better.furnace.minecart;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * 由 mixin 注入到 MinecartFurnace 的扩展访问接口。
 * 管理器通过该接口读取/写入运行态与持久化字段。
 */
public interface BetterFurnaceMinecartAccess {
	BetterFurnaceMinecartMode betterFurnace$getMode();

	void betterFurnace$setMode(BetterFurnaceMinecartMode mode);

	boolean betterFurnace$isBraking();

	void betterFurnace$setBraking(boolean braking);

	ItemStack betterFurnace$getFuelStack();

	void betterFurnace$setFuelStack(ItemStack stack);

	int betterFurnace$getBurnDuration();

	void betterFurnace$setBurnDuration(int burnDuration);

	int betterFurnace$getHalfBurnCounter();

	void betterFurnace$setHalfBurnCounter(int ticks);

	boolean betterFurnace$wasPoweredActivator();

	void betterFurnace$setWasPoweredActivator(boolean powered);

	ContainerData betterFurnace$getContainerData();
}
