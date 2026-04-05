package better.furnace.minecart;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

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
}
