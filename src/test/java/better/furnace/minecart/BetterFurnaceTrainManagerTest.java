package better.furnace.minecart;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetterFurnaceTrainManagerTest {
	@Test
	void constrainMotionToTrackRemovesSidewaysPushTowardAdjacentRail() {
		Vec3 constrained = BetterFurnaceTrainManager.constrainMotionToTrack(new Vec3(0.28D, 0.0D, 0.34D), new Vec3(0.0D, 0.0D, 1.0D));

		assertEquals(0.0D, constrained.x, 1.0E-6D);
		assertTrue(constrained.z > 0.30D);
	}

	@Test
	void constrainMotionToTrackKeepsForwardSpeedOnCurrentTrack() {
		Vec3 constrained = BetterFurnaceTrainManager.constrainMotionToTrack(new Vec3(0.42D, 0.0D, 0.05D), new Vec3(1.0D, 0.0D, 0.0D));

		assertTrue(constrained.x > 0.40D);
		assertEquals(0.0D, constrained.z, 1.0E-6D);
	}

	@Test
	void constrainMotionToTrackFlipsTrackAxisInsteadOfSendingFollowerBackward() {
		Vec3 constrained = BetterFurnaceTrainManager.constrainMotionToTrack(new Vec3(0.0D, 0.0D, 0.25D), new Vec3(0.0D, 0.0D, -1.0D));

		assertEquals(0.0D, constrained.x, 1.0E-6D);
		assertTrue(constrained.z > 0.24D);
	}

	@Test
	void computeFollowSpeedCapUsesPathLagBoost() {
		double baseCap = BetterFurnaceTrainManager.computeFollowSpeedCap(0.55D, 0.0D);
		double boostedCap = BetterFurnaceTrainManager.computeFollowSpeedCap(0.55D, 0.42D);

		assertEquals(0.75D, baseCap, 1.0E-6D);
		assertTrue(boostedCap > 0.95D);
	}

	@Test
	void computeCatchUpBoostIsZeroWithoutPositivePathLag() {
		assertEquals(0.0D, BetterFurnaceTrainManager.computeCatchUpBoost(0.0D, 0.8D), 1.0E-6D);
		assertEquals(0.0D, BetterFurnaceTrainManager.computeCatchUpBoost(-0.2D, 0.8D), 1.0E-6D);
	}

	@Test
	void computeCatchUpBoostGetsStrongerOnCurvedLag() {
		double straight = BetterFurnaceTrainManager.computeCatchUpBoost(0.45D, 0.0D);
		double curved = BetterFurnaceTrainManager.computeCatchUpBoost(0.45D, 0.8D);

		assertTrue(straight > 0.0D);
		assertTrue(curved > straight + 0.10D);
	}

	@Test
	void computeTurnSeverityIsZeroOnStraightHistory() {
		Deque<Vec3> history = new ArrayDeque<>();
		history.addLast(new Vec3(0.0D, 0.0D, 0.0D));
		history.addLast(new Vec3(-1.0D, 0.0D, 0.0D));
		history.addLast(new Vec3(-2.0D, 0.0D, 0.0D));

		assertEquals(0.0D, BetterFurnaceTrainManager.computeTurnSeverity(history), 1.0E-6D);
	}

	@Test
	void computeTurnSeverityIsPositiveOnTurningHistory() {
		Deque<Vec3> history = new ArrayDeque<>();
		history.addLast(new Vec3(0.0D, 0.0D, 0.0D));
		history.addLast(new Vec3(0.0D, 0.0D, -1.0D));
		history.addLast(new Vec3(-1.0D, 0.0D, -1.0D));

		assertTrue(BetterFurnaceTrainManager.computeTurnSeverity(history) > 0.4D);
	}

	@Test
	void computeAllowedLinkDistanceSqrIncreasesWithGeometryTolerance() {
		double base = BetterFurnaceTrainManager.computeAllowedLinkDistanceSqr(0.0D, 0.0D);
		double tolerant = BetterFurnaceTrainManager.computeAllowedLinkDistanceSqr(0.6D, 0.8D);

		assertEquals(6.0D, base, 1.0E-6D);
		assertTrue(tolerant > base + 0.8D);
	}

	@Test
	void computeBoundedFollowAdjustmentReturnsZeroWhenAlreadyAtTarget() {
		Vec3 adjustment = BetterFurnaceTrainManager.computeBoundedFollowAdjustment(new Vec3(0.25D, 0.0D, 0.0D), new Vec3(0.25D, 0.0D, 0.0D), 0.18D);

		assertEquals(0.0D, adjustment.lengthSqr(), 1.0E-6D);
	}

	@Test
	void computeBoundedFollowAdjustmentReturnsSmallExactDelta() {
		Vec3 adjustment = BetterFurnaceTrainManager.computeBoundedFollowAdjustment(new Vec3(0.20D, 0.0D, 0.0D), new Vec3(0.28D, 0.0D, 0.0D), 0.18D);

		assertEquals(0.08D, adjustment.x, 1.0E-6D);
		assertEquals(0.0D, adjustment.z, 1.0E-6D);
	}

	@Test
	void computeBoundedFollowAdjustmentCapsLargeDelta() {
		Vec3 adjustment = BetterFurnaceTrainManager.computeBoundedFollowAdjustment(new Vec3(0.0D, 0.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), 0.22D);

		assertEquals(0.22D, adjustment.length(), 1.0E-6D);
	}

	@Test
	void computeMaxFollowAdjustmentStaysModerateWithoutCurveLag() {
		assertEquals(0.10D, BetterFurnaceTrainManager.computeMaxFollowAdjustment(0.0D, 0.0D), 1.0E-6D);
	}

	@Test
	void computeMaxFollowAdjustmentIncreasesForCurvedLag() {
		assertTrue(BetterFurnaceTrainManager.computeMaxFollowAdjustment(0.42D, 0.8D) > 0.28D);
	}
}
