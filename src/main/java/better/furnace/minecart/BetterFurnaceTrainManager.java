package better.furnace.minecart;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 列车链接/跟随/碰撞忽略/整组传送 的核心管理器。
 */
public final class BetterFurnaceTrainManager {
	private static final ThreadLocal<Boolean> GROUP_TELEPORTING = ThreadLocal.withInitial(() -> false);

	private static final int MAX_HISTORY_POINTS = 140;
	private static final int MAX_TRAIN_SCAN = 128;
	private static final int LINK_COOLDOWN_TICKS = 20;
	private static final double MAX_LINK_DISTANCE_SQR = 64.0D;
	private static final double FOLLOW_SPACING = 1.25D;

	private BetterFurnaceTrainManager() {
	}

	public static void tick(AbstractMinecart minecart) {
		if (minecart.level().isClientSide) {
			return;
		}
		if (!(minecart instanceof BetterFurnaceTrainAccess access)) {
			return;
		}

		if (access.betterFurnace$getLinkCooldown() > 0) {
			access.betterFurnace$setLinkCooldown(access.betterFurnace$getLinkCooldown() - 1);
		}

		Deque<Vec3> history = access.betterFurnace$getTrackHistory();
		history.addFirst(minecart.position());
		while (history.size() > MAX_HISTORY_POINTS) {
			history.removeLast();
		}

		validateLinks(minecart, access);
		followPrevious(minecart, access);
	}

	public static boolean shouldIgnoreCollision(AbstractMinecart self, Entity other) {
		if (!(other instanceof AbstractMinecart otherMinecart)) {
			return false;
		}
		return isSameTrain(self, otherMinecart);
	}

	public static void tryLinkOnCollision(AbstractMinecart first, AbstractMinecart second) {
		if (first.level().isClientSide || second.level().isClientSide) {
			return;
		}
		if (!(first instanceof BetterFurnaceTrainAccess firstAccess) || !(second instanceof BetterFurnaceTrainAccess secondAccess)) {
			return;
		}
		if (firstAccess.betterFurnace$getLinkCooldown() > 0 || secondAccess.betterFurnace$getLinkCooldown() > 0) {
			return;
		}
		if (isSameTrain(first, second)) {
			return;
		}
		if (!containsFurnace(first) && !containsFurnace(second)) {
			return;
		}

		AbstractMinecart firstHead = getHead(first);
		AbstractMinecart firstTail = getTail(first);
		AbstractMinecart secondHead = getHead(second);
		AbstractMinecart secondTail = getTail(second);

		LinkCandidate best = null;

		if (getNextEntity(firstTail) == null && getPreviousEntity(secondHead) == null) {
			best = LinkCandidate.pickBetter(best, new LinkCandidate(firstTail, secondHead));
		}
		if (getNextEntity(secondTail) == null && getPreviousEntity(firstHead) == null) {
			best = LinkCandidate.pickBetter(best, new LinkCandidate(secondTail, firstHead));
		}

		if (best == null) {
			return;
		}

		link(best.from, best.to);
	}

	public static void onMinecartRemoved(AbstractMinecart minecart) {
		if (!(minecart instanceof BetterFurnaceTrainAccess access)) {
			return;
		}

		AbstractMinecart previous = resolveMinecart(minecart, access.betterFurnace$getPreviousUuid());
		AbstractMinecart next = resolveMinecart(minecart, access.betterFurnace$getNextUuid());

		if (previous instanceof BetterFurnaceTrainAccess previousAccess) {
			previousAccess.betterFurnace$setNextUuid(next == null ? null : next.getUUID());
		}
		if (next instanceof BetterFurnaceTrainAccess nextAccess) {
			nextAccess.betterFurnace$setPreviousUuid(previous == null ? null : previous.getUUID());
		}

		access.betterFurnace$setPreviousUuid(null);
		access.betterFurnace$setNextUuid(null);
		access.betterFurnace$getTrackHistory().clear();
	}

	public static boolean isGroupTeleporting() {
		return GROUP_TELEPORTING.get();
	}

	public static void teleportLinkedGroupWithLead(MinecartFurnace lead, ServerLevel targetLevel) {
		if (isGroupTeleporting()) {
			return;
		}
		if (!(lead.level() instanceof ServerLevel)) {
			return;
		}

		List<AbstractMinecart> orderedTrain = getOrderedTrain(lead);
		if (orderedTrain.size() <= 1) {
			return;
		}

		GROUP_TELEPORTING.set(true);
		try {
			for (AbstractMinecart minecart : orderedTrain) {
				if (minecart == lead) {
					continue;
				}
				if (minecart.level() == targetLevel) {
					continue;
				}
				if (!minecart.isRemoved()) {
					minecart.changeDimension(targetLevel);
				}
			}
		} finally {
			GROUP_TELEPORTING.set(false);
		}
	}

	private static void validateLinks(AbstractMinecart minecart, BetterFurnaceTrainAccess access) {
		AbstractMinecart previous = resolveMinecart(minecart, access.betterFurnace$getPreviousUuid());
		AbstractMinecart next = resolveMinecart(minecart, access.betterFurnace$getNextUuid());

		if (previous == null) {
			access.betterFurnace$setPreviousUuid(null);
		}
		if (next == null) {
			access.betterFurnace$setNextUuid(null);
		}

		if (previous != null && minecart.distanceToSqr(previous) > MAX_LINK_DISTANCE_SQR) {
			unlink(previous, minecart);
			return;
		}
		if (next != null && minecart.distanceToSqr(next) > MAX_LINK_DISTANCE_SQR) {
			unlink(minecart, next);
		}

		if (previous instanceof BetterFurnaceTrainAccess previousAccess && !minecart.getUUID().equals(previousAccess.betterFurnace$getNextUuid())) {
			previousAccess.betterFurnace$setNextUuid(minecart.getUUID());
		}
		if (next instanceof BetterFurnaceTrainAccess nextAccess && !minecart.getUUID().equals(nextAccess.betterFurnace$getPreviousUuid())) {
			nextAccess.betterFurnace$setPreviousUuid(minecart.getUUID());
		}
	}

	private static void followPrevious(AbstractMinecart follower, BetterFurnaceTrainAccess followerAccess) {
		AbstractMinecart leader = resolveMinecart(follower, followerAccess.betterFurnace$getPreviousUuid());
		if (!(leader instanceof BetterFurnaceTrainAccess leaderAccess)) {
			return;
		}

		Vec3 target = samplePathPoint(leader.position(), leaderAccess.betterFurnace$getTrackHistory(), FOLLOW_SPACING);
		Vec3 delta = target.subtract(follower.position());
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		if (horizontal < 1.0E-3D) {
			return;
		}

		// 过远时直接纠偏，避免弯道时尾车丢失路径。
		if (horizontal > 3.0D) {
			follower.setPos(target.x, target.y, target.z);
		}

		Vec3 current = follower.getDeltaMovement();
		double correctionX = Mth.clamp(delta.x * 0.25D, -0.7D, 0.7D);
		double correctionZ = Mth.clamp(delta.z * 0.25D, -0.7D, 0.7D);
		Vec3 corrected = new Vec3(current.x * 0.6D + correctionX, current.y, current.z * 0.6D + correctionZ);
		follower.setDeltaMovement(corrected);
	}

	private static Vec3 samplePathPoint(Vec3 fallback, Deque<Vec3> history, double distance) {
		if (history.isEmpty()) {
			return fallback;
		}

		Vec3 last = null;
		double walked = 0.0D;
		for (Vec3 point : history) {
			if (last != null) {
				walked += point.distanceTo(last);
				if (walked >= distance) {
					return point;
				}
			}
			last = point;
		}
		return history.getLast();
	}

	private static void link(AbstractMinecart from, AbstractMinecart to) {
		if (!(from instanceof BetterFurnaceTrainAccess fromAccess) || !(to instanceof BetterFurnaceTrainAccess toAccess)) {
			return;
		}

		fromAccess.betterFurnace$setNextUuid(to.getUUID());
		toAccess.betterFurnace$setPreviousUuid(from.getUUID());
		fromAccess.betterFurnace$setLinkCooldown(LINK_COOLDOWN_TICKS);
		toAccess.betterFurnace$setLinkCooldown(LINK_COOLDOWN_TICKS);
	}

	private static void unlink(AbstractMinecart from, AbstractMinecart to) {
		if (from instanceof BetterFurnaceTrainAccess fromAccess && to.getUUID().equals(fromAccess.betterFurnace$getNextUuid())) {
			fromAccess.betterFurnace$setNextUuid(null);
		}
		if (to instanceof BetterFurnaceTrainAccess toAccess && from.getUUID().equals(toAccess.betterFurnace$getPreviousUuid())) {
			toAccess.betterFurnace$setPreviousUuid(null);
		}
	}

	private static boolean isSameTrain(AbstractMinecart first, AbstractMinecart second) {
		if (first == second) {
			return true;
		}
		if (!(first instanceof BetterFurnaceTrainAccess) || !(second instanceof BetterFurnaceTrainAccess)) {
			return false;
		}

		Set<UUID> visited = new HashSet<>();
		Deque<AbstractMinecart> queue = new ArrayDeque<>();
		queue.add(first);
		while (!queue.isEmpty() && visited.size() <= MAX_TRAIN_SCAN) {
			AbstractMinecart current = queue.removeFirst();
			if (!visited.add(current.getUUID())) {
				continue;
			}
			if (current.getUUID().equals(second.getUUID())) {
				return true;
			}

			AbstractMinecart previous = getPreviousEntity(current);
			AbstractMinecart next = getNextEntity(current);
			if (previous != null) {
				queue.addLast(previous);
			}
			if (next != null) {
				queue.addLast(next);
			}
		}
		return false;
	}

	private static boolean containsFurnace(AbstractMinecart start) {
		Set<UUID> visited = new HashSet<>();
		Deque<AbstractMinecart> queue = new ArrayDeque<>();
		queue.add(start);

		while (!queue.isEmpty() && visited.size() <= MAX_TRAIN_SCAN) {
			AbstractMinecart current = queue.removeFirst();
			if (!visited.add(current.getUUID())) {
				continue;
			}
			if (current instanceof MinecartFurnace) {
				return true;
			}
			AbstractMinecart previous = getPreviousEntity(current);
			AbstractMinecart next = getNextEntity(current);
			if (previous != null) {
				queue.addLast(previous);
			}
			if (next != null) {
				queue.addLast(next);
			}
		}
		return false;
	}

	private static List<AbstractMinecart> getOrderedTrain(AbstractMinecart cart) {
		List<AbstractMinecart> ordered = new ArrayList<>();
		AbstractMinecart head = getHead(cart);
		Set<UUID> visited = new HashSet<>();

		AbstractMinecart cursor = head;
		while (cursor != null && visited.size() <= MAX_TRAIN_SCAN && visited.add(cursor.getUUID())) {
			ordered.add(cursor);
			cursor = getNextEntity(cursor);
		}
		return ordered;
	}

	private static AbstractMinecart getHead(AbstractMinecart cart) {
		AbstractMinecart cursor = cart;
		Set<UUID> visited = new HashSet<>();
		while (cursor != null && visited.add(cursor.getUUID())) {
			AbstractMinecart previous = getPreviousEntity(cursor);
			if (previous == null) {
				return cursor;
			}
			cursor = previous;
		}
		return cart;
	}

	private static AbstractMinecart getTail(AbstractMinecart cart) {
		AbstractMinecart cursor = cart;
		Set<UUID> visited = new HashSet<>();
		while (cursor != null && visited.add(cursor.getUUID())) {
			AbstractMinecart next = getNextEntity(cursor);
			if (next == null) {
				return cursor;
			}
			cursor = next;
		}
		return cart;
	}

	private static AbstractMinecart getPreviousEntity(AbstractMinecart minecart) {
		if (!(minecart instanceof BetterFurnaceTrainAccess access)) {
			return null;
		}
		return resolveMinecart(minecart, access.betterFurnace$getPreviousUuid());
	}

	private static AbstractMinecart getNextEntity(AbstractMinecart minecart) {
		if (!(minecart instanceof BetterFurnaceTrainAccess access)) {
			return null;
		}
		return resolveMinecart(minecart, access.betterFurnace$getNextUuid());
	}

	private static AbstractMinecart resolveMinecart(AbstractMinecart context, UUID uuid) {
		if (uuid == null) {
			return null;
		}
		if (!(context.level() instanceof ServerLevel level)) {
			return null;
		}
		Entity entity = level.getEntity(uuid);
		if (entity instanceof AbstractMinecart minecart && !minecart.isRemoved()) {
			return minecart;
		}
		return null;
	}

	private static final class LinkCandidate {
		private final AbstractMinecart from;
		private final AbstractMinecart to;
		private final double distanceSqr;

		private LinkCandidate(AbstractMinecart from, AbstractMinecart to) {
			this.from = from;
			this.to = to;
			this.distanceSqr = from.distanceToSqr(to);
		}

		private static LinkCandidate pickBetter(LinkCandidate current, LinkCandidate incoming) {
			if (current == null) {
				return incoming;
			}
			return incoming.distanceSqr < current.distanceSqr ? incoming : current;
		}
	}
}
