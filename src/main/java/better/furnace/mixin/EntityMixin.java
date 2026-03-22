package better.furnace.mixin;

import better.furnace.chunk.BetterFurnaceChunkLoadingManager;
import better.furnace.minecart.BetterFurnaceTrainManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 Entity 层面挂钩，以处理继承方法（remove / changeDimension）。
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "remove", at = @At("HEAD"))
	private void betterFurnace$onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (self instanceof MinecartFurnace furnace) {
			BetterFurnaceChunkLoadingManager.clear(furnace);
		}
		if (self instanceof AbstractMinecart minecart && reason != Entity.RemovalReason.CHANGED_DIMENSION) {
			BetterFurnaceTrainManager.onMinecartRemoved(minecart);
		}
	}

	@Inject(method = "changeDimension", at = @At("HEAD"))
	private void betterFurnace$onChangeDimension(ServerLevel targetLevel, CallbackInfoReturnable<Entity> cir) {
		Entity self = (Entity) (Object) this;
		if (!(self instanceof MinecartFurnace furnace)) {
			return;
		}
		if (!BetterFurnaceTrainManager.isGroupTeleporting()) {
			BetterFurnaceTrainManager.teleportLinkedGroupWithLead(furnace, targetLevel);
		}
		BetterFurnaceChunkLoadingManager.clear(furnace);
	}
}
