package better.furnace.minecart;

/**
 * 动力矿车工作模式：
 * - ACCELERATE：原版加速模式
 * - DECELERATE：反向加速（制动）模式
 */
public enum BetterFurnaceMinecartMode {
	ACCELERATE,
	DECELERATE;

	public BetterFurnaceMinecartMode toggle() {
		return this == ACCELERATE ? DECELERATE : ACCELERATE;
	}
}
