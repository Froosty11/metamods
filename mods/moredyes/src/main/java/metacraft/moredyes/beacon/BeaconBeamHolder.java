package metacraft.moredyes.beacon;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import metacraft.moredyes.MoreDyes;
import metacraft.moredyes.content.GlassBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One taken-over beacon column. Polymer creates one of these per beacon block (chunk load / place)
 * and destroys it on unload or break, so lifecycle is free; everything here is the per-player half.
 *
 * Every {@value BeaconBeams#REFRESH_TICKS} ticks it re-walks the column ({@link BeamWalk}) and
 * re-classifies nearby players as near or far. Crossing the boundary resends the beacon block and
 * every one of our glass blocks in the column to that player alone, and starts or stops watching
 * this holder — so the display-entity beam exists only for the players it is meant for.
 *
 * Why the resends rather than {@code PolymerBlock#getPolymerBlockState}: that hook has no block
 * position (Polymer maps whole chunk palettes with it), so it cannot answer "is THIS player near
 * THIS block". It stays the default — the real beacon, our real glass colour — and the holder,
 * which does know the positions, pushes the per-player exceptions.
 */
public final class BeaconBeamHolder extends ElementHolder {
	/** Vanilla {@code BeaconRenderer.SOLID_BEAM_RADIUS} 0.2 and {@code BEAM_GLOW_RADIUS} 0.25. */
	private static final float VIEW_RANGE = 4.0f; // Display: renders within viewRange * 64 blocks

	private final ServerLevel level;
	private final BlockPos pos;

	private final List<ItemDisplayElement> beam = new ArrayList<>();
	private BlockDisplayElement beacon;

	/** null until the first walk; then true while the column is ours to draw. */
	private Boolean engaged;
	private List<BeamWalk.Section> sections = List.of();
	private List<BlockPos> ours = List.of();
	/** Last known side of the boundary per player, so only crossings cost packets. */
	private final Map<UUID, Boolean> side = new HashMap<>();
	private int ticks;

	public BeaconBeamHolder(ServerLevel level, BlockPos pos) {
		this.level = level;
		this.pos = pos.immutable();
	}

	// ------------------------------------------------------------------ watching

	@Override
	public boolean startWatching(ServerGamePacketListenerImpl handler) {
		ServerPlayer player = handler.getPlayer();
		if (!isEngaged() || player == null || !BeaconBeams.isNear(pos, player)) return false;
		boolean added = super.startWatching(handler);
		if (added) {
			// The chunk may have just arrived with the real beacon in it; hide it again.
			send(player, pos, Blocks.BARRIER.defaultBlockState());
			side.put(player.getUUID(), Boolean.TRUE);
		}
		return added;
	}

	@Override
	public void destroy() {
		for (UUID id : List.copyOf(side.keySet())) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			if (player != null && Boolean.TRUE.equals(side.get(id))) restore(player);
		}
		side.clear();
		super.destroy();
	}

	private boolean isEngaged() {
		return Boolean.TRUE.equals(engaged);
	}

	// ------------------------------------------------------------------ tick

	@Override
	protected void onTick() {
		if (ticks++ % BeaconBeams.REFRESH_TICKS != 0) return;
		BeamWalk.Result result = BeamWalk.walk(level, pos);
		// Only columns with one of our colours in them are worth taking over; a plain vanilla
		// beacon keeps its own, correct, client-side beam.
		boolean wanted = result.lit() && result.tinted();
		if (!wanted) {
			if (isEngaged()) disengage();
			engaged = Boolean.FALSE;
			return;
		}
		boolean first = !isEngaged();
		engaged = Boolean.TRUE;
		ours = result.ours();
		if (first || !sections.equals(result.sections())) {
			sections = result.sections();
			rebuild();
		}
		reclassify();
	}

	/** Give every player the vanilla column back and drop the elements. */
	private void disengage() {
		for (Map.Entry<UUID, Boolean> entry : Map.copyOf(side).entrySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			if (player != null) restore(player);
		}
		side.clear();
		for (ServerGamePacketListenerImpl handler : List.copyOf(getWatchingPlayers())) stopWatching(handler);
		clearElements();
	}

	// ------------------------------------------------------------------ elements

	private void clearElements() {
		for (ItemDisplayElement element : beam) removeElement(element);
		beam.clear();
		if (beacon != null) {
			removeElement(beacon);
			beacon = null;
		}
	}

	private void rebuild() {
		clearElements();
		// The block itself: near players are sent a barrier, so put the beacon back as a display.
		beacon = new BlockDisplayElement(Blocks.BEACON.defaultBlockState());
		beacon.setTranslation(new Vector3f(-0.5f, -0.5f, -0.5f)); // holder sits at the block centre
		beacon.setBrightness(Brightness.FULL_BRIGHT);
		beacon.setViewRange(VIEW_RANGE);
		beacon.setDisplaySize(0, 0);
		addElement(beacon);

		int offset = 0;
		for (BeamWalk.Section section : sections) {
			beam.add(quad(BeaconBeams.CORE, section, offset));
			beam.add(quad(BeaconBeams.GLOW, section, offset));
			offset += section.height();
		}
		MoreDyes.LOGGER.info("[{}] beacon {} beam: {} section(s)", MoreDyes.MOD_ID, pos, sections.size());
	}

	/**
	 * One beam part. The model is a 16-unit-tall box with no {@code display} block, so
	 * {@link ItemDisplayContext#NONE} renders it as an exact 1×1×1 cell centred on the element;
	 * scaling Y by the section height and translating to the section's mid-point puts it in place.
	 * Culling is off ({@code setDisplaySize(0, 0)} sets Display#noCulling) because the element's own
	 * box says nothing about a 200-block beam.
	 */
	private ItemDisplayElement quad(net.minecraft.resources.Identifier model, BeamWalk.Section section, int offset) {
		ItemDisplayElement element = new ItemDisplayElement();
		element.setItem(BeaconBeams.beamStack(model, section.color()));
		element.setItemDisplayContext(ItemDisplayContext.NONE);
		element.setScale(new Vector3f(1, section.height(), 1));
		element.setTranslation(new Vector3f(0, offset + section.height() / 2.0f - 0.5f, 0));
		element.setBrightness(Brightness.FULL_BRIGHT);
		element.setViewRange(VIEW_RANGE);
		element.setDisplaySize(0, 0);
		element.setInvisible(true);
		addElement(element);
		return element;
	}

	// ------------------------------------------------------------------ near/far

	private void reclassify() {
		side.keySet().removeIf(id -> level.getServer().getPlayerList().getPlayer(id) == null);
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(pos) > 512 * 512) continue; // not tracking this column
			boolean near = BeaconBeams.isNear(pos, player);
			Boolean was = side.put(player.getUUID(), near);
			if (was != null && was == near) continue;
			if (near) {
				hide(player);
				startWatching(player);
			} else {
				stopWatching(player);
				restore(player);
			}
		}
	}

	/** Near: barrier where the beacon is (the block display draws it), our glass as it always is. */
	private void hide(ServerPlayer player) {
		send(player, pos, Blocks.BARRIER.defaultBlockState());
		for (BlockPos p : ours) send(player, p, BeaconBeams.clientState(level.getBlockState(p)));
	}

	/** Far: the real beacon, and our glass as the nearest vanilla stained glass so the client tints. */
	private void restore(ServerPlayer player) {
		send(player, pos, level.getBlockState(pos));
		for (BlockPos p : ours) {
			BlockState state = level.getBlockState(p);
			BlockState client = state.getBlock() instanceof GlassBlocks.Glass glass
					? BeaconBeams.nearestVanillaGlass(glass.color())
					: BeaconBeams.clientState(state);
			send(player, p, client);
		}
	}

	private void send(ServerPlayer player, BlockPos at, BlockState state) {
		player.connection.send(new ClientboundBlockUpdatePacket(at, state));
	}
}
