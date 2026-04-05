package better.furnace.mixin;

import better.furnace.minecart.BetterFurnaceMinecartAccess;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VehicleEntity.class)
public abstract class VehicleEntityMixin {
	@Inject(method = "destroy", at = @At("HEAD"))
	private void betterFurnace$dropStoredFuel(Item item, CallbackInfo ci) {
		VehicleEntity self = (VehicleEntity) (Object) this;
		if (!(self instanceof MinecartFurnace) || !(self instanceof BetterFurnaceMinecartAccess access)) {
			return;
		}
		if (self.level().isClientSide || !self.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
			return;
		}

		ItemStack fuelStack = access.betterFurnace$getFuelStack();
		if (fuelStack.isEmpty()) {
			return;
		}

		self.spawnAtLocation(fuelStack.copy());
		access.betterFurnace$setFuelStack(ItemStack.EMPTY);
	}
}
