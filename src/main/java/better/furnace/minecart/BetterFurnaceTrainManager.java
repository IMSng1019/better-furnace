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
 * Core manager for train linking, follow pathing, collision suppression, and group teleport.
 */
public final class BetterFurnaceTrainManager {
	private static final ThreadLocal<Boolean> GROUP_TELEPORTING = ThreadLocal.withInitial(() -> false);

	private static final int MAX_HISTORY_POINTS = 140;
	private static final int MAX_TRAIN_SCAN = 128;
	private static final int MAX_TRAIN_CARS = 4;
	private static final int LINK_COOLDOWN_TICKS = 20;
	private static final double MAX_LINK_DISTANCE_SQR = 6.0D;
	private static final double MIN_LINK_DISTANCE_SQR = 0.64D;
	private static final double MIN_LINK_DISTANCE = Math.sqrt(MIN_LINK_DISTANCE_SQR);
	private static final double MAX_COLLISION_LINK_DISTANCE_SQR = 6.25D;
	private static final double FOLLOW_SPACING = 1.0D;

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

		validateLinks(minecart, access);
		followPrevious(minecart, access);

		Deque<Vec3> history = access.betterFurnace$getTrackHistory();
		history.addFirst(minecart.position());
		while (history.size() > MAX_HISTORY_POINTS) {
			history.removeLast();
		}
	}

	public static boolean shouldIgnoreCollision(AbstractMinecart self, Entity other) {
		if (!(other instanceof AbstractMinecart otherMinecart)) {
			return false;
		}
		if (!isSameTrain(self, otherMinecart)) {
			return false;
		}

		// Let directly linked neighbors still collide when they are compressed too close,
		// so the physics layer can separate them and reduce corner overlap.
		if (isDirectlyLinked(self, otherMinecart)) {
			if (isApproachingNeighbor(self, otherMinecart)) {
				return false;
			}
			return self.distanceToSqr(otherMinecart) >= MIN_LINK_DISTANCE_SQR;
		}
		return true;
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
		if (countTrainCars(first) + countTrainCars(second) > MAX_TRAIN_CARS) {
			return;
		}
		if (!containsFurnace(first) && !containsFurnace(second)) {
			return;
		}

		LinkCandidate candidate = pickDirectionalLinkCandidate(first, second);
		if (candidate == null) {
			return;
		}

		link(candidate.from, candidate.to);
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

			// 清理所有矿车的历史轨迹
			for (AbstractMinecart minecart : orderedTrain) {
				if (minecart instanceof BetterFurnaceTrainAccess access) {
					access.betterFurnace$getTrackHistory().clear();
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

		// Keep furnace minecart as train head: it should not have a previous link.
		if (minecart instanceof MinecartFurnace && previous != null) {
			unlink(previous, minecart);
			previous = null;
		}
		// Clean up legacy/wrong topology: non-furnace carts must not point to furnace as next.
		if (!(minecart instanceof MinecartFurnace) && next instanceof MinecartFurnace) {
			unlink(minecart, next);
			next = null;
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

		PathSample sample = samplePath(leader.position(), leaderAccess.betterFurnace$getTrackHistory(), FOLLOW_SPACING);
		Vec3 target = sample.point();
		Vec3 tangent = sample.tangent();
		Vec3 delta = target.subtract(follower.position());
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		if (horizontal < 1.0E-3D) {
			return;
		}

		Vec3 leaderMotion = leader.getDeltaMovement();
		double leaderAlong = leaderMotion.x * tangent.x + leaderMotion.z * tangent.z;
		if (leaderAlong < 0.0D) {
			tangent = tangent.scale(-1.0D);
			leaderAlong = -leaderAlong;
		}

		Vec3 current = follower.getDeltaMovement();
		double alongError = delta.x * tangent.x + delta.z * tangent.z;
		Vec3 lateral = new Vec3(
			delta.x - tangent.x * alongError,
			0.0D,
			delta.z - tangent.z * alongError
		);

		double currentAlong = current.x * tangent.x + current.z * tangent.z;
		double desiredAlong = Math.max(0.0D, leaderAlong + Mth.clamp(alongError * 0.20D, -0.16D, 0.34D));
		double nextAlong = currentAlong + (desiredAlong - currentAlong) * 0.36D;

		double lateralX = Mth.clamp(lateral.x * 0.22D, -0.26D, 0.26D);
		double lateralZ = Mth.clamp(lateral.z * 0.22D, -0.26D, 0.26D);
		Vec3 nextHorizontal = tangent.scale(nextAlong).add(lateralX, 0.0D, lateralZ);

		double forward = nextHorizontal.x * tangent.x + nextHorizontal.z * tangent.z;
		if (forward < 0.0D) {
			nextHorizontal = nextHorizontal.subtract(tangent.scale(forward));
		}

		double leaderSpeed = Math.sqrt(leaderMotion.x * leaderMotion.x + leaderMotion.z * leaderMotion.z);
		double maxSpeed = Math.max(leaderSpeed + 0.16D, 0.75D);
		double horizontalSpeed = Math.sqrt(nextHorizontal.x * nextHorizontal.x + nextHorizontal.z * nextHorizontal.z);
		if (horizontalSpeed > maxSpeed) {
			nextHorizontal = nextHorizontal.scale(maxSpeed / horizontalSpeed);
		}

		nextHorizontal = applyNeighborSpacingGuard(follower, leader, nextHorizontal);
		follower.setDeltaMovement(nextHorizontal.x, current.y, nextHorizontal.z);
	}

	private static PathSample samplePath(Vec3 fallback, Deque<Vec3> history, double distance) {
		if (history.size() < 2) {
			return new PathSample(fallback, new Vec3(1.0D, 0.0D, 0.0D));
		}

		Vec3 newer = null;
		double walked = 0.0D;
		for (Vec3 point : history) {
			if (newer != null) {
				Vec3 segment = point.subtract(newer);
				double len = segment.length();
				if (len > 1.0E-6D) {
					if (walked + len >= distance) {
						double t = (distance - walked) / len;
						Vec3 target = newer.add(segment.scale(t));
						Vec3 tangent = normalizeHorizontal(newer.subtract(point));
						return new PathSample(target, tangent);
					}
					walked += len;
				}
			}
			newer = point;
		}

		Vec3 oldest = history.getLast();
		Vec3 newest = history.getFirst();
		Vec3 fallbackTangent = normalizeHorizontal(newest.subtract(oldest));
		return new PathSample(oldest, fallbackTangent);
	}

	private static Vec3 normalizeHorizontal(Vec3 vector) {
		double horizontalSqr = vector.x * vector.x + vector.z * vector.z;
		if (horizontalSqr < 1.0E-6D) {
			return new Vec3(1.0D, 0.0D, 0.0D);
		}
		double invLen = 1.0D / Math.sqrt(horizontalSqr);
		return new Vec3(vector.x * invLen, 0.0D, vector.z * invLen);
	}

	private static void link(AbstractMinecart from, AbstractMinecart to) {
		if (!(from instanceof BetterFurnaceTrainAccess fromAccess) || !(to instanceof BetterFurnaceTrainAccess toAccess)) {
			return;
		}

		fromAccess.betterFurnace$setNextUuid(to.getUUID());
		toAccess.betterFurnace$setPreviousUuid(from.getUUID());
		fromAccess.betterFurnace$setLinkCooldown(LINK_COOLDOWN_TICKS);
		toAccess.betterFurnace$setLinkCooldown(LINK_COOLDOWN_TICKS);
		separateAfterLink(from, to);
	}

	private static void unlink(AbstractMinecart from, AbstractMinecart to) {
		if (from instanceof BetterFurnaceTrainAccess fromAccess && to.getUUID().equals(fromAccess.betterFurnace$getNextUuid())) {
			fromAccess.betterFurnace$setNextUuid(null);
		}
		if (to instanceof BetterFurnaceTrainAccess toAccess && from.getUUID().equals(toAccess.betterFurnace$getPreviousUuid())) {
			toAccess.betterFurnace$setPreviousUuid(null);
		}
	}

	private static boolean canLinkFollowingVanillaRule(AbstractMinecart from, AbstractMinecart to) {
		if (from == to) {
			return false;
		}
		// Prevent linking into furnace as follower; furnace should stay at head side.
		if (to instanceof MinecartFurnace) {
			return false;
		}
		if (getNextEntity(from) != null || getPreviousEntity(to) != null) {
			return false;
		}
		double distanceSqr = from.distanceToSqr(to);
		if (distanceSqr < MIN_LINK_DISTANCE_SQR || distanceSqr > MAX_COLLISION_LINK_DISTANCE_SQR) {
			return false;
		}
		return isVanillaCouplingAligned(from, to);
	}

	private static LinkCandidate pickDirectionalLinkCandidate(AbstractMinecart first, AbstractMinecart second) {
		Vec3 axis = getDirectionalLinkAxis(first, second);
		double firstProjection = first.getX() * axis.x + first.getZ() * axis.z;
		double secondProjection = second.getX() * axis.x + second.getZ() * axis.z;

		AbstractMinecart leader = firstProjection >= secondProjection ? first : second;
		AbstractMinecart follower = leader == first ? second : first;
		if (!canLinkFollowingVanillaRule(leader, follower)) {
			if (!canLinkFollowingVanillaRule(follower, leader)) {
				return null;
			}
			return new LinkCandidate(follower, leader);
		}
		return new LinkCandidate(leader, follower);
	}

	private static Vec3 getDirectionalLinkAxis(AbstractMinecart first, AbstractMinecart second) {
		Vec3 offset = normalizeHorizontal(second.position().subtract(first.position()));
		Vec3 axis = canonicalizeAxis(offset);

		Vec3 totalMotion = first.getDeltaMovement().add(second.getDeltaMovement());
		double totalMotionSqr = totalMotion.x * totalMotion.x + totalMotion.z * totalMotion.z;
		if (totalMotionSqr > 1.0E-6D) {
			if (axis.x * totalMotion.x + axis.z * totalMotion.z < 0.0D) {
				axis = axis.scale(-1.0D);
			}
			return axis;
		}

		Vec3 totalPush = getFurnacePushHeading(first).add(getFurnacePushHeading(second));
		double totalPushSqr = totalPush.x * totalPush.x + totalPush.z * totalPush.z;
		if (totalPushSqr > 1.0E-6D && axis.x * totalPush.x + axis.z * totalPush.z < 0.0D) {
			axis = axis.scale(-1.0D);
		}
		return axis;
	}

	private static Vec3 canonicalizeAxis(Vec3 axis) {
		if (axis.x < 0.0D || (Math.abs(axis.x) <= 1.0E-6D && axis.z < 0.0D)) {
			return axis.scale(-1.0D);
		}
		return axis;
	}

	private static Vec3 getFurnacePushHeading(AbstractMinecart minecart) {
		if (!(minecart instanceof MinecartFurnace furnace)) {
			return new Vec3(0.0D, 0.0D, 0.0D);
		}

		double pushSqr = furnace.xPush * furnace.xPush + furnace.zPush * furnace.zPush;
		if (pushSqr <= 1.0E-7D) {
			return new Vec3(0.0D, 0.0D, 0.0D);
		}

		double invLen = 1.0D / Math.sqrt(pushSqr);
		return new Vec3(furnace.xPush * invLen, 0.0D, furnace.zPush * invLen);
	}

	private static boolean isVanillaCouplingAligned(AbstractMinecart from, AbstractMinecart to) {
		Vec3 offset = new Vec3(to.getX() - from.getX(), 0.0D, to.getZ() - from.getZ());
		if (offset.lengthSqr() < 1.0E-4D) {
			return false;
		}

		Vec3 normal = offset.normalize();
		Vec3 heading = getVanillaCouplingHeading(from);
		return Math.abs(normal.dot(heading)) >= 0.8D;
	}

	private static Vec3 getVanillaCouplingHeading(AbstractMinecart minecart) {
		float yawRad = minecart.getYRot() * 0.017453292F;
		return new Vec3(Mth.cos(yawRad), 0.0D, Mth.sin(yawRad)).normalize();
	}

	private static void separateAfterLink(AbstractMinecart leader, AbstractMinecart follower) {
		double dx = follower.getX() - leader.getX();
		double dz = follower.getZ() - leader.getZ();
		double horizontalSqr = dx * dx + dz * dz;
		if (horizontalSqr >= MIN_LINK_DISTANCE_SQR) {
			return;
		}

		Vec3 back = getHorizontalHeading(leader).scale(-1.0D);
		follower.push(back.x * 0.08D, 0.0D, back.z * 0.08D);
		leader.push(-back.x * 0.04D, 0.0D, -back.z * 0.04D);
	}

	private static Vec3 applyNeighborSpacingGuard(AbstractMinecart follower, AbstractMinecart leader, Vec3 proposed) {
		Vec3 toLeader = new Vec3(leader.getX() - follower.getX(), 0.0D, leader.getZ() - follower.getZ());
		double distanceSqr = toLeader.x * toLeader.x + toLeader.z * toLeader.z;
		if (distanceSqr >= MIN_LINK_DISTANCE_SQR) {
			return proposed;
		}

		Vec3 toward = normalizeHorizontal(toLeader);
		double approachSpeed = proposed.x * toward.x + proposed.z * toward.z;
		Vec3 guarded = proposed;
		if (approachSpeed > 0.0D) {
			guarded = guarded.subtract(toward.scale(approachSpeed));
		}

		double distance = Math.sqrt(Math.max(distanceSqr, 1.0E-6D));
		double repel = Mth.clamp((MIN_LINK_DISTANCE - distance) * 0.45D, 0.0D, 0.18D);
		return guarded.add(-toward.x * repel, 0.0D, -toward.z * repel);
	}

	private static Vec3 getHorizontalHeading(AbstractMinecart minecart) {
		Vec3 movement = minecart.getDeltaMovement();
		double horizontalSqr = movement.x * movement.x + movement.z * movement.z;
		if (horizontalSqr > 1.0E-5D) {
			double len = Math.sqrt(horizontalSqr);
			return new Vec3(movement.x / len, 0.0D, movement.z / len);
		}

		float yawRad = minecart.getYRot() * 0.017453292F;
		return new Vec3(Mth.cos(yawRad), 0.0D, Mth.sin(yawRad)).normalize();
	}

	private static boolean isApproachingNeighbor(AbstractMinecart first, AbstractMinecart second) {
		Vec3 offset = new Vec3(second.getX() - first.getX(), 0.0D, second.getZ() - first.getZ());
		double offsetSqr = offset.x * offset.x + offset.z * offset.z;
		if (offsetSqr < 1.0E-6D) {
			return true;
		}

		Vec3 relative = first.getDeltaMovement().subtract(second.getDeltaMovement());
		double closing = relative.x * offset.x + relative.z * offset.z;
		return closing > 0.0025D;
	}

	private static boolean isDirectlyLinked(AbstractMinecart first, AbstractMinecart second) {
		if (!(first instanceof BetterFurnaceTrainAccess firstAccess) || !(second instanceof BetterFurnaceTrainAccess secondAccess)) {
			return false;
		}
		UUID firstId = first.getUUID();
		UUID secondId = second.getUUID();
		return secondId.equals(firstAccess.betterFurnace$getPreviousUuid())
			|| secondId.equals(firstAccess.betterFurnace$getNextUuid())
			|| firstId.equals(secondAccess.betterFurnace$getPreviousUuid())
			|| firstId.equals(secondAccess.betterFurnace$getNextUuid());
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

	private static int countTrainCars(AbstractMinecart start) {
		Set<UUID> visited = new HashSet<>();
		Deque<AbstractMinecart> queue = new ArrayDeque<>();
		queue.add(start);

		while (!queue.isEmpty() && visited.size() <= MAX_TRAIN_SCAN) {
			AbstractMinecart current = queue.removeFirst();
			if (!visited.add(current.getUUID())) {
				continue;
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

		return visited.size();
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

		// 跨维度查找
		for (ServerLevel otherLevel : level.getServer().getAllLevels()) {
			if (otherLevel == level) {
				continue;
			}
			Entity crossDimEntity = otherLevel.getEntity(uuid);
			if (crossDimEntity instanceof AbstractMinecart minecart && !minecart.isRemoved()) {
				return minecart;
			}
		}
		return null;
	}

	private record PathSample(Vec3 point, Vec3 tangent) {
	}

	private static final class LinkCandidate {
		private final AbstractMinecart from;
		private final AbstractMinecart to;

		private LinkCandidate(AbstractMinecart from, AbstractMinecart to) {
			this.from = from;
			this.to = to;
		}
	}
}
