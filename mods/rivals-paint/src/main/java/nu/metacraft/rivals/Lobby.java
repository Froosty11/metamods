package nu.metacraft.rivals;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelData;
import nu.metacraft.rivals.gun.InkOnScreen;
import nu.metacraft.rivals.gun.Roll;

import java.util.List;

/**
 * What being between matches means for a player: adventure mode and no Rivals kit at all — no gun, no
 * weapon selector, nothing this mod handed them.
 *
 * <p>Adventure because the lobby is not a place to mine the arena from, and because a paint weapon in a
 * lobby is a paint weapon used on the arena before the round starts. Nothing in the kit, because a kit is
 * a match's: a player whose match was stopped is left with the inventory they walked in with rather than a
 * compass they cannot drop. The kit comes back at the next {@code /rivals match start} ({@link Match#arm}),
 * and the pick itself is remembered whatever they are carrying, so it comes back as the weapon they chose.
 *
 * <p>Ops keep whatever mode they are in —
 * an operator in the lobby is usually building it — and the permission asked is the module's own
 * {@code metacraft.rivals}, the same one the admin commands use, so a server with a permissions plugin can
 * hand it out without handing out op.
 *
 * <p>Called from the match's own transitions, from {@code /rivals match} and from the join hook. A player
 * who joins while a match is <em>playing</em> is not given the lobby treatment at all: they are added to
 * the match ({@link Match#addMidMatch}), which arms them and gives them the respawn grace.
 *
 * <p><b>Outside a round this mod does not move anybody.</b> The team spawns are the round's, and this is
 * one minigame of several on the server: a lobby death respawns wherever the world says, and a join
 * only has the Rivals kit taken back, not a game mode set.
 */
public final class Lobby {
	/** The permission that keeps a player's own game mode. */
	public static final String ADMIN_PERMISSION = "metacraft.rivals";

	private Lobby() {}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> receiveOnJoin(handler.getPlayer()));
	}

	/**
	 * A player arriving: into the match if one is on; otherwise only the kit is taken back, because a
	 * server that runs other games too does not want its players' game mode set by this one on every join.
	 */
	public static void receiveOnJoin(ServerPlayer player) {
		if (Match.state() == Match.State.PLAYING
				&& Match.addMidMatch(player, player.level().getServer().getTickCount())) {
			player.sendSystemMessage(Component.literal("A match is running — you are in it. Good luck.")
					.withStyle(ChatFormatting.GREEN));
			return;
		}
		Match.disarm(player);
	}

	/**
	 * The lobby treatment: the Rivals kit off them ({@link Match#disarm} — every paint weapon and the
	 * selector with them), adventure mode unless they are an admin, a clean screen and no roll. Returns how
	 * many stacks were taken off them, which is what the tests read.
	 */
	public static int receive(ServerPlayer player) {
		int taken = Match.disarm(player);
		if (!isAdmin(player)) player.setGameMode(GameType.ADVENTURE);
		InkOnScreen.clear(player);
		Roll.stop(player);
		Match.thaw(player);
		return taken;
	}

	/** Everybody at once: what the match calls on its way back to the lobby, and after the whistle. */
	public static int receiveAll(List<ServerPlayer> players) {
		int taken = 0;
		for (ServerPlayer player : players) taken += receive(player);
		return taken;
	}

	/** The level's own respawn point, whatever team they are on: where a round leaves everybody when it is over. */
	public static void sendHome(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) return;
		LevelData.RespawnData world = level.getRespawnData();
		BlockPos at = world.pos();
		player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
				java.util.Set.<Relative>of(), world.yaw(), world.pitch(), true);
		InkOnScreen.clear(player);
	}

	/** Whoever keeps their own game mode in the lobby. */
	public static boolean isAdmin(Player player) {
		return Permissions.check(player, ADMIN_PERMISSION, PermissionLevel.GAMEMASTERS);
	}
}
