package better.furnace.mixin;

import better.furnace.minecart.BetterFurnaceTrainAccess;
import better.furnace.minecart.BetterFurnaceTrainManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * 所有矿车共享注入：
 * - 列车链接运行态字段
 * - 接触链接与同组碰撞忽略
 * - 每 tick 跟随轨迹修正
 */
@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartMixin implements BetterFurnaceTrainAccess {
	@Unique
	private UUID betterFurnace$previousUuid;
	@Unique
	private UUID betterFurnace$nextUuid;
	@Unique
	private final Deque<Vec3> betterFurnace$history = new ArrayDeque<>();
	@Unique
	private int betterFurnace$linkCooldown;

	@Inject(method = "tick", at = @At("TAIL"))
	private void betterFurnace$onTick(CallbackInfo ci) {
		BetterFurnaceTrainManager.tick((AbstractMinecart) (Object) this);
	}

	@Inject(method = "push", at = @At("HEAD"))
	private void betterFurnace$onPush(Entity entity, CallbackInfo ci) {
		AbstractMinecart self = (AbstractMinecart) (Object) this;
		if (!(entity instanceof AbstractMinecart otherMinecart)) {
			return;
		}

		BetterFurnaceTrainManager.tryLinkOnCollision(self, otherMinecart);
	}

	@Override
	public UUID betterFurnace$getPreviousUuid() {
		return betterFurnace$previousUuid;
	}

	@Override
	public void betterFurnace$setPreviousUuid(UUID previous) {
		this.betterFurnace$previousUuid = previous;
	}

	@Override
	public UUID betterFurnace$getNextUuid() {
		return betterFurnace$nextUuid;
	}

	@Override
	public void betterFurnace$setNextUuid(UUID next) {
		this.betterFurnace$nextUuid = next;
	}

	@Override
	public Deque<Vec3> betterFurnace$getTrackHistory() {
		return betterFurnace$history;
	}

	@Override
	public int betterFurnace$getLinkCooldown() {
		return betterFurnace$linkCooldown;
	}

	@Override
	public void betterFurnace$setLinkCooldown(int cooldown) {
		this.betterFurnace$linkCooldown = cooldown;
	}
}
