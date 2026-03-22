package better.furnace.chunk;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 负责“燃烧态动力矿车”的 3x3 区块强制加载和延迟释放逻辑。
 */
public final class BetterFurnaceChunkLoadingManager {
	private static final int RELEASE_DELAY_TICKS = 15 * 20;
	private static final int RADIUS = 1;

	private static final Map<UUID, CartLoadState> CART_STATES = new HashMap<>();
	private static final Map<ResourceKey<Level>, Map<Long, Integer>> CHUNK_REFERENCE_COUNTS = new HashMap<>();

	private BetterFurnaceChunkLoadingManager() {
	}

	public static void registerLifecycleCallbacks() {
		ServerTickEvents.END_SERVER_TICK.register(BetterFurnaceChunkLoadingManager::cleanupMissingCarts);
		ServerLifecycleEvents.SERVER_STOPPING.register(BetterFurnaceChunkLoadingManager::clearAll);
	}

	public static void tick(MinecartFurnace cart, boolean burning) {
		if (!(cart.level() instanceof ServerLevel level)) {
			return;
		}

		UUID cartId = cart.getUUID();
		CartLoadState state = CART_STATES.computeIfAbsent(cartId, id -> new CartLoadState(level, new HashSet<>(), -1));

		// 维度变化时，释放旧维度加载并重建当前状态。
		if (state.level != level) {
			releaseChunks(state.level, state.loadedChunks);
			state.level = level;
			state.loadedChunks.clear();
			state.releaseAtTick = -1;
		}

		if (burning) {
			Set<Long> desired = getDesiredChunks(cart.chunkPosition());
			applyDesiredChunks(level, state.loadedChunks, desired);
			state.releaseAtTick = -1;
			return;
		}

		if (state.loadedChunks.isEmpty()) {
			return;
		}

		long now = level.getGameTime();
		if (state.releaseAtTick < 0) {
			state.releaseAtTick = now + RELEASE_DELAY_TICKS;
			return;
		}

		if (now >= state.releaseAtTick) {
			releaseChunks(level, state.loadedChunks);
			state.loadedChunks.clear();
			CART_STATES.remove(cartId);
		}
	}

	public static void clear(MinecartFurnace cart) {
		CartLoadState state = CART_STATES.remove(cart.getUUID());
		if (state != null && !state.loadedChunks.isEmpty()) {
			releaseChunks(state.level, state.loadedChunks);
			state.loadedChunks.clear();
		}
	}

	private static void cleanupMissingCarts(MinecraftServer server) {
		Iterator<Map.Entry<UUID, CartLoadState>> iterator = CART_STATES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, CartLoadState> entry = iterator.next();
			CartLoadState state = entry.getValue();
			Entity entity = state.level.getEntity(entry.getKey());
			if (!(entity instanceof MinecartFurnace minecart) || minecart.isRemoved()) {
				releaseChunks(state.level, state.loadedChunks);
				iterator.remove();
			}
		}
	}

	private static void clearAll(MinecraftServer server) {
		for (CartLoadState state : CART_STATES.values()) {
			releaseChunks(state.level, state.loadedChunks);
		}
		CART_STATES.clear();
		CHUNK_REFERENCE_COUNTS.clear();
	}

	private static Set<Long> getDesiredChunks(ChunkPos center) {
		Set<Long> desired = new HashSet<>();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				desired.add(ChunkPos.asLong(center.x + dx, center.z + dz));
			}
		}
		return desired;
	}

	private static void applyDesiredChunks(ServerLevel level, Set<Long> current, Set<Long> desired) {
		if (!current.equals(desired)) {
			Set<Long> toRelease = new HashSet<>(current);
			toRelease.removeAll(desired);
			releaseChunks(level, toRelease);

			Set<Long> toAdd = new HashSet<>(desired);
			toAdd.removeAll(current);
			forceChunks(level, toAdd);

			current.clear();
			current.addAll(desired);
		}
	}

	private static void forceChunks(ServerLevel level, Set<Long> chunks) {
		for (long chunkLong : chunks) {
			Map<Long, Integer> refs = CHUNK_REFERENCE_COUNTS.computeIfAbsent(level.dimension(), k -> new HashMap<>());
			int ref = refs.getOrDefault(chunkLong, 0) + 1;
			refs.put(chunkLong, ref);
			if (ref == 1) {
				int chunkX = ChunkPos.getX(chunkLong);
				int chunkZ = ChunkPos.getZ(chunkLong);
				level.setChunkForced(chunkX, chunkZ, true);
			}
		}
	}

	private static void releaseChunks(ServerLevel level, Set<Long> chunks) {
		Map<Long, Integer> refs = CHUNK_REFERENCE_COUNTS.get(level.dimension());
		if (refs == null) {
			return;
		}

		for (long chunkLong : chunks) {
			int ref = refs.getOrDefault(chunkLong, 0);
			if (ref <= 1) {
				refs.remove(chunkLong);
				int chunkX = ChunkPos.getX(chunkLong);
				int chunkZ = ChunkPos.getZ(chunkLong);
				level.setChunkForced(chunkX, chunkZ, false);
			} else {
				refs.put(chunkLong, ref - 1);
			}
		}

		if (refs.isEmpty()) {
			CHUNK_REFERENCE_COUNTS.remove(level.dimension());
		}
	}

	private static final class CartLoadState {
		private ServerLevel level;
		private final Set<Long> loadedChunks;
		private long releaseAtTick;

		private CartLoadState(ServerLevel level, Set<Long> loadedChunks, long releaseAtTick) {
			this.level = level;
			this.loadedChunks = loadedChunks;
			this.releaseAtTick = releaseAtTick;
		}
	}
}
