package better.furnace;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端网络握手管理：
 * - 安装了客户端模组的玩家会在入服后发送一次握手包。
 * - 服务器用该标记决定是否允许打开自定义 GUI。
 */
public final class BetterFurnaceNetworking {
	public static final ResourceLocation CLIENT_READY = BetterFurnace.id("client_ready");

	private static final Set<UUID> CLIENT_WITH_MOD = ConcurrentHashMap.newKeySet();

	private BetterFurnaceNetworking() {
	}

	public static void registerServer() {
		ServerPlayNetworking.registerGlobalReceiver(CLIENT_READY, (server, player, handler, buf, responseSender) ->
			server.execute(() -> CLIENT_WITH_MOD.add(player.getUUID()))
		);

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			CLIENT_WITH_MOD.remove(handler.player.getUUID())
		);
	}

	public static boolean hasClientMod(ServerPlayer player) {
		return CLIENT_WITH_MOD.contains(player.getUUID());
	}
}
