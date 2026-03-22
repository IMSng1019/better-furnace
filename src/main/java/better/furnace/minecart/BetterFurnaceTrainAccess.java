package better.furnace.minecart;

import net.minecraft.world.phys.Vec3;

import java.util.Deque;
import java.util.UUID;

/**
 * 所有矿车共享的“列车链接”运行态数据接口。
 */
public interface BetterFurnaceTrainAccess {
	UUID betterFurnace$getPreviousUuid();

	void betterFurnace$setPreviousUuid(UUID previous);

	UUID betterFurnace$getNextUuid();

	void betterFurnace$setNextUuid(UUID next);

	Deque<Vec3> betterFurnace$getTrackHistory();

	int betterFurnace$getLinkCooldown();

	void betterFurnace$setLinkCooldown(int cooldown);
}
