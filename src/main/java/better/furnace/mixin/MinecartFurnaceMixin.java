package better.furnace.mixin;

import better.furnace.BetterFurnaceNetworking;
import better.furnace.chunk.BetterFurnaceChunkLoadingManager;
import better.furnace.menu.BetterFurnaceMinecartMenu;
import better.furnace.minecart.BetterFurnaceMinecartAccess;
import better.furnace.minecart.BetterFurnaceMinecartMode;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 动力矿车主逻辑注入：
 * - 服务端燃料槽与 GUI
 * - 加速/减速模式 + 制动
 * - 3x3 区块加载
 * - NBT 持久化
 * - 传送门触发整组传送
 */
@Mixin(MinecartFurnace.class)
public abstract class MinecartFurnaceMixin extends AbstractMinecart implements WorldlyContainer, ExtendedScreenHandlerFactory, BetterFurnaceMinecartAccess {
	@Shadow
	private int fuel;

	@Shadow
	public double xPush;

	@Shadow
	public double zPush;

	@Shadow
	protected abstract boolean hasFuel();

	@Shadow
	protected abstract void setHasFuel(boolean hasFuel);

	@Unique
	private static final int[] BETTER_FURNACE$ALL_FACES = new int[]{0};

	@Unique
	private static final String BETTER_FURNACE$TAG_MODE = "BetterFurnaceMode";
	@Unique
	private static final String BETTER_FURNACE$TAG_BRAKING = "BetterFurnaceBraking";
	@Unique
	private static final String BETTER_FURNACE$TAG_FUEL_STACK = "BetterFurnaceFuelStack";
	@Unique
	private static final String BETTER_FURNACE$TAG_BURN_DURATION = "BetterFurnaceBurnDuration";
	@Unique
	private static final String BETTER_FURNACE$TAG_HALF_BURN_COUNTER = "BetterFurnaceHalfBurnCounter";

	@Unique
	private ItemStack betterFurnace$fuelStack = ItemStack.EMPTY;
	@Unique
	private BetterFurnaceMinecartMode betterFurnace$mode = BetterFurnaceMinecartMode.ACCELERATE;
	@Unique
	private boolean betterFurnace$braking;
	@Unique
	private int betterFurnace$burnDuration;
	@Unique
	private int betterFurnace$halfBurnCounter;
	@Unique
	private boolean betterFurnace$wasPoweredActivator;
	@Unique
	private final ContainerData betterFurnace$containerData = new SimpleContainerData(4);

	protected MinecartFurnaceMixin(EntityType<?> entityType, Level level) {
		super(entityType, level);
	}

	@Overwrite
	public void tick() {
		super.tick();

		if (!this.level().isClientSide) {
			betterFurnace$handleActivatorMode();
			betterFurnace$tickFuelState();
			BetterFurnaceChunkLoadingManager.tick((MinecartFurnace) (Object) this, this.hasFuel());
		}

		if (this.hasFuel() && this.random.nextInt(4) == 0) {
			this.level().addParticle(
				net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
				this.getX(),
				this.getY() + 0.8D,
				this.getZ(),
				0.0D,
				0.0D,
				0.0D
			);
		}

		betterFurnace$updateContainerData();
	}

	@Overwrite
	public InteractionResult interact(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		boolean shift = player.isSecondaryUseActive();

		if (shift && player instanceof ServerPlayer serverPlayer && BetterFurnaceNetworking.hasClientMod(serverPlayer)) {
			if (!this.level().isClientSide) {
				serverPlayer.openMenu(this);
			}
			return InteractionResult.sidedSuccess(this.level().isClientSide);
		}

		if (betterFurnace$tryInsertFuelFromPlayer(player, held)) {
			this.xPush = this.getX() - player.getX();
			this.zPush = this.getZ() - player.getZ();
		}

		return InteractionResult.sidedSuccess(this.level().isClientSide);
	}

	@Overwrite
	protected void moveAlongTrack(BlockPos blockPos, BlockState state) {
		super.moveAlongTrack(blockPos, state);

		Vec3 movement = this.getDeltaMovement();
		double movementSqr = movement.horizontalDistanceSqr();
		double pushSqr = this.xPush * this.xPush + this.zPush * this.zPush;
		if (pushSqr > 1.0E-4D && movementSqr > 0.001D) {
			double movementLen = Math.sqrt(movementSqr);
			double pushLen = Math.sqrt(pushSqr);
			double sign = Math.signum(this.xPush * movement.x + this.zPush * movement.z);
			if (sign == 0.0D) {
				sign = 1.0D;
			}

			this.xPush = movement.x / movementLen * pushLen * sign;
			this.zPush = movement.z / movementLen * pushLen * sign;
		}
	}

	@Overwrite
	protected void applyNaturalSlowdown() {
		if (this.betterFurnace$mode == BetterFurnaceMinecartMode.DECELERATE && this.betterFurnace$braking) {
			Vec3 movement = this.getDeltaMovement();
			this.setDeltaMovement(0.0D, movement.y, 0.0D);
		}

		double pushSqr = this.xPush * this.xPush + this.zPush * this.zPush;
		if (pushSqr > 1.0E-7D) {
			double pushLen = Math.sqrt(pushSqr);
			this.xPush /= pushLen;
			this.zPush /= pushLen;
			Vec3 movement = this.getDeltaMovement().multiply(0.8D, 0.0D, 0.8D).add(this.xPush, 0.0D, this.zPush);
			if (this.isInWater()) {
				movement = movement.scale(0.1D);
			}
			this.setDeltaMovement(movement);
		} else {
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.98D, 0.0D, 0.98D));
		}

		super.applyNaturalSlowdown();
	}

	@Overwrite
	protected double getMaxSpeed() {
		double furnaceSpeed = this.isInWater() ? 3.0D / 20.0D : 4.0D / 20.0D;
		if (betterFurnace$isOnPoweredRail()) {
			double poweredRailSpeed = this.isInWater() ? 4.0D / 20.0D : 8.0D / 20.0D;
			return furnaceSpeed + poweredRailSpeed;
		}
		return furnaceSpeed;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void betterFurnace$writeCustomData(CompoundTag tag, CallbackInfo ci) {
		tag.putString(BETTER_FURNACE$TAG_MODE, this.betterFurnace$mode.name());
		tag.putBoolean(BETTER_FURNACE$TAG_BRAKING, this.betterFurnace$braking);
		tag.putInt(BETTER_FURNACE$TAG_BURN_DURATION, this.betterFurnace$burnDuration);
		tag.putInt(BETTER_FURNACE$TAG_HALF_BURN_COUNTER, this.betterFurnace$halfBurnCounter);

		if (!this.betterFurnace$fuelStack.isEmpty()) {
			tag.put(BETTER_FURNACE$TAG_FUEL_STACK, this.betterFurnace$fuelStack.save(new CompoundTag()));
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void betterFurnace$readCustomData(CompoundTag tag, CallbackInfo ci) {
		if (tag.contains(BETTER_FURNACE$TAG_MODE, Tag.TAG_STRING)) {
			try {
				this.betterFurnace$mode = BetterFurnaceMinecartMode.valueOf(tag.getString(BETTER_FURNACE$TAG_MODE));
			} catch (IllegalArgumentException ignored) {
				this.betterFurnace$mode = BetterFurnaceMinecartMode.ACCELERATE;
			}
		}
		this.betterFurnace$braking = tag.getBoolean(BETTER_FURNACE$TAG_BRAKING);
		this.betterFurnace$burnDuration = Math.max(0, tag.getInt(BETTER_FURNACE$TAG_BURN_DURATION));
		this.betterFurnace$halfBurnCounter = Math.max(0, tag.getInt(BETTER_FURNACE$TAG_HALF_BURN_COUNTER));
		if (tag.contains(BETTER_FURNACE$TAG_FUEL_STACK, Tag.TAG_COMPOUND)) {
			this.betterFurnace$fuelStack = ItemStack.of(tag.getCompound(BETTER_FURNACE$TAG_FUEL_STACK));
		} else {
			this.betterFurnace$fuelStack = ItemStack.EMPTY;
		}

		this.setHasFuel(this.fuel > 0);
		betterFurnace$updateContainerData();
	}

	@Override
	public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
		buf.writeVarInt(this.getId());
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
		return new BetterFurnaceMinecartMenu(syncId, playerInventory, this, this.betterFurnace$containerData, this.getId());
	}

	@Override
	public int getContainerSize() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return this.betterFurnace$fuelStack.isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot == 0 ? this.betterFurnace$fuelStack : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		if (slot != 0 || this.betterFurnace$fuelStack.isEmpty() || amount <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack split = this.betterFurnace$fuelStack.split(amount);
		if (this.betterFurnace$fuelStack.isEmpty()) {
			this.betterFurnace$fuelStack = ItemStack.EMPTY;
		}
		this.setChanged();
		return split;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		if (slot != 0 || this.betterFurnace$fuelStack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = this.betterFurnace$fuelStack;
		this.betterFurnace$fuelStack = ItemStack.EMPTY;
		this.setChanged();
		return stack;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot != 0) {
			return;
		}
		this.betterFurnace$fuelStack = stack;
		if (!this.betterFurnace$fuelStack.isEmpty() && this.betterFurnace$fuelStack.getCount() > this.getMaxStackSize()) {
			this.betterFurnace$fuelStack.setCount(this.getMaxStackSize());
		}
		this.setChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == 0 && AbstractFurnaceBlockEntity.isFuel(stack);
	}

	@Override
	public boolean stillValid(Player player) {
		return this.isAlive() && player.distanceToSqr(this) <= 64.0D;
	}

	@Override
	public void clearContent() {
		this.betterFurnace$fuelStack = ItemStack.EMPTY;
		this.setChanged();
	}

	@Override
	public void setChanged() {
		betterFurnace$updateContainerData();
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return BETTER_FURNACE$ALL_FACES;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot == 0 && AbstractFurnaceBlockEntity.isFuel(stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot == 0;
	}

	private void betterFurnace$handleActivatorMode() {
		boolean onPoweredActivator = betterFurnace$isOnPoweredActivatorRail();

		if (onPoweredActivator && !this.betterFurnace$wasPoweredActivator) {
			this.betterFurnace$mode = this.betterFurnace$mode.toggle();
		}

		// 减速模式需要持续在红石激活铁轨上维持，否则自动回到加速模式。
		if (this.betterFurnace$mode == BetterFurnaceMinecartMode.DECELERATE && !onPoweredActivator) {
			this.betterFurnace$mode = BetterFurnaceMinecartMode.ACCELERATE;
			this.betterFurnace$braking = false;
			this.betterFurnace$halfBurnCounter = 0;
		}

		this.betterFurnace$wasPoweredActivator = onPoweredActivator;
	}

	private void betterFurnace$tickFuelState() {
		boolean moving = this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D;
		boolean burning = false;

		if (this.betterFurnace$mode == BetterFurnaceMinecartMode.ACCELERATE) {
			this.betterFurnace$braking = false;
			burning = betterFurnace$consumeBurnTick(false);
			if (burning) {
				betterFurnace$setForwardPushFromFacingWhenIdle();
			} else if (this.fuel <= 0) {
				this.xPush = 0.0D;
				this.zPush = 0.0D;
			}
		} else {
			if (moving) {
				this.betterFurnace$braking = false;
				betterFurnace$setReversePushFromMotion();
				burning = betterFurnace$consumeBurnTick(false);
			} else if (betterFurnace$isOnAscendingRail()) {
				this.betterFurnace$braking = true;
				this.xPush = 0.0D;
				this.zPush = 0.0D;
				burning = betterFurnace$consumeBurnTick(true);
			} else {
				this.betterFurnace$braking = false;
				this.xPush = 0.0D;
				this.zPush = 0.0D;
				this.betterFurnace$halfBurnCounter = 0;
			}
		}

		this.setHasFuel(burning);
	}

	private boolean betterFurnace$consumeBurnTick(boolean halfRate) {
		if (!betterFurnace$ensureFuelReady()) {
			return false;
		}

		if (halfRate) {
			this.betterFurnace$halfBurnCounter++;
			if (this.betterFurnace$halfBurnCounter >= 2) {
				this.betterFurnace$halfBurnCounter = 0;
				this.fuel = Math.max(0, this.fuel - 1);
			}
		} else {
			this.betterFurnace$halfBurnCounter = 0;
			this.fuel = Math.max(0, this.fuel - 1);
		}

		return this.fuel > 0;
	}

	private boolean betterFurnace$ensureFuelReady() {
		if (this.fuel > 0) {
			return true;
		}
		if (this.betterFurnace$fuelStack.isEmpty()) {
			return false;
		}

		int burnTime = AbstractFurnaceBlockEntity.getFuel().getOrDefault(this.betterFurnace$fuelStack.getItem(), 0);
		if (burnTime <= 0) {
			return false;
		}

		this.fuel = burnTime;
		this.betterFurnace$burnDuration = burnTime;

		Item consumedFuelItem = this.betterFurnace$fuelStack.getItem();
		this.betterFurnace$fuelStack.shrink(1);
		if (this.betterFurnace$fuelStack.isEmpty() && consumedFuelItem.hasCraftingRemainingItem()) {
			this.betterFurnace$fuelStack = new ItemStack(consumedFuelItem.getCraftingRemainingItem());
		}
		this.setChanged();
		return true;
	}

	private void betterFurnace$setReversePushFromMotion() {
		Vec3 movement = this.getDeltaMovement();
		double horizontal = movement.horizontalDistanceSqr();
		if (horizontal <= 1.0E-6D) {
			this.xPush = 0.0D;
			this.zPush = 0.0D;
			return;
		}

		double len = Math.sqrt(horizontal);
		this.xPush = -movement.x / len;
		this.zPush = -movement.z / len;
	}

	private void betterFurnace$setForwardPushFromFacingWhenIdle() {
		double movementSqr = this.getDeltaMovement().horizontalDistanceSqr();
		double pushSqr = this.xPush * this.xPush + this.zPush * this.zPush;
		if (movementSqr > 1.0E-4D || pushSqr > 1.0E-7D) {
			return;
		}

		double yawRad = Math.toRadians(this.getYRot());
		this.xPush = -Math.sin(yawRad);
		this.zPush = Math.cos(yawRad);
	}

	private boolean betterFurnace$tryInsertFuelFromPlayer(Player player, ItemStack held) {
		if (held.isEmpty() || !AbstractFurnaceBlockEntity.isFuel(held)) {
			return false;
		}

		if (this.betterFurnace$fuelStack.isEmpty()) {
			this.betterFurnace$fuelStack = held.copyWithCount(1);
		} else {
			if (!ItemStack.isSameItemSameTags(this.betterFurnace$fuelStack, held)) {
				return false;
			}
			int max = Math.min(this.betterFurnace$fuelStack.getMaxStackSize(), this.getMaxStackSize());
			if (this.betterFurnace$fuelStack.getCount() >= max) {
				return false;
			}
			this.betterFurnace$fuelStack.grow(1);
		}

		if (!player.getAbilities().instabuild) {
			held.shrink(1);
		}

		this.setChanged();
		return true;
	}

	private boolean betterFurnace$isOnPoweredActivatorRail() {
		BlockPos pos = this.blockPosition();
		return betterFurnace$isPoweredActivatorRail(pos) || betterFurnace$isPoweredActivatorRail(pos.below());
	}

	private boolean betterFurnace$isPoweredActivatorRail(BlockPos pos) {
		BlockState state = this.level().getBlockState(pos);
		if (!state.is(Blocks.ACTIVATOR_RAIL)) {
			return false;
		}
		return state.getValue(PoweredRailBlock.POWERED);
	}

	private boolean betterFurnace$isOnPoweredRail() {
		BlockPos pos = this.blockPosition();
		return betterFurnace$isPoweredRail(pos) || betterFurnace$isPoweredRail(pos.below());
	}

	private boolean betterFurnace$isPoweredRail(BlockPos pos) {
		BlockState state = this.level().getBlockState(pos);
		if (!state.is(Blocks.POWERED_RAIL)) {
			return false;
		}
		return state.getValue(PoweredRailBlock.POWERED);
	}

	private boolean betterFurnace$isOnAscendingRail() {
		BlockPos pos = this.blockPosition();
		return betterFurnace$isAscendingRail(pos) || betterFurnace$isAscendingRail(pos.below());
	}

	@SuppressWarnings("unchecked")
	private boolean betterFurnace$isAscendingRail(BlockPos pos) {
		BlockState state = this.level().getBlockState(pos);
		if (!(state.getBlock() instanceof BaseRailBlock railBlock)) {
			return false;
		}
		RailShape shape = state.getValue(railBlock.getShapeProperty());
		return shape.isAscending();
	}

	private void betterFurnace$updateContainerData() {
		this.betterFurnace$containerData.set(0, this.hasFuel() ? this.fuel : 0);
		this.betterFurnace$containerData.set(1, Math.max(this.betterFurnace$burnDuration, 0));
		this.betterFurnace$containerData.set(2, this.betterFurnace$mode == BetterFurnaceMinecartMode.DECELERATE ? 1 : 0);
		this.betterFurnace$containerData.set(3, this.betterFurnace$braking ? 1 : 0);
	}

	@Override
	public BetterFurnaceMinecartMode betterFurnace$getMode() {
		return this.betterFurnace$mode;
	}

	@Override
	public void betterFurnace$setMode(BetterFurnaceMinecartMode mode) {
		this.betterFurnace$mode = mode;
		this.setChanged();
	}

	@Override
	public boolean betterFurnace$isBraking() {
		return this.betterFurnace$braking;
	}

	@Override
	public void betterFurnace$setBraking(boolean braking) {
		this.betterFurnace$braking = braking;
		this.setChanged();
	}

	@Override
	public ItemStack betterFurnace$getFuelStack() {
		return this.betterFurnace$fuelStack;
	}

	@Override
	public void betterFurnace$setFuelStack(ItemStack stack) {
		this.betterFurnace$fuelStack = stack;
		this.setChanged();
	}

	@Override
	public int betterFurnace$getBurnDuration() {
		return this.betterFurnace$burnDuration;
	}

	@Override
	public void betterFurnace$setBurnDuration(int burnDuration) {
		this.betterFurnace$burnDuration = burnDuration;
		this.setChanged();
	}

	@Override
	public int betterFurnace$getHalfBurnCounter() {
		return this.betterFurnace$halfBurnCounter;
	}

	@Override
	public void betterFurnace$setHalfBurnCounter(int ticks) {
		this.betterFurnace$halfBurnCounter = ticks;
		this.setChanged();
	}

	@Override
	public boolean betterFurnace$wasPoweredActivator() {
		return this.betterFurnace$wasPoweredActivator;
	}

	@Override
	public void betterFurnace$setWasPoweredActivator(boolean powered) {
		this.betterFurnace$wasPoweredActivator = powered;
	}

	@Override
	public ContainerData betterFurnace$getContainerData() {
		return this.betterFurnace$containerData;
	}
}
