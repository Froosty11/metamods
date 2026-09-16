package metacraft.moredyes.beacon;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * One taken-over beacon column. Polymer creates one of these per beacon block (chunk load / place)
 * and destroys it on unload or break, so lifecycle is free; everything here is the per-player half.
 *
 * Every {@value BeaconBeams#REFRESH_TICKS} ticks it re-walks the column ({@link BeamWalk}) and
 * re-classifies nearby players as near or far. Crossing the boundary resends the beacon block and
 * every one of our glass blocks in the column to that player alone, and starts or stops watching
 * this holder — so the display-entity beam exists only for the players it is meant for.
 *
 * <b>The element set is fixed at construction and never changes.</b> That is a correctness
 * requirement, not a tidiness one: {@code ElementHolder.stopWatching} builds
 * {@code new ClientboundRemoveEntitiesPacket(this.entityIds)} straight off its own live, mutable
 * {@code IntList} field, and that packet's canonical constructor takes the list <i>by reference</i>
 * with no defensive copy. {@code addElement}/{@code removeElement} mutate that same field. Since the
 * packet is encoded later on the Netty thread, any element added or removed in between makes the
 * encoder read a list that is changing under it — a torn length prefix, and the client dies with
 * {@code DecoderException: Failed to decode packet 'clientbound/minecraft:remove_entities'}. So the
 * beam is a fixed pool of segments, shown by giving them an item and hidden by giving them none.
 *
 * Everything that sends a packet runs on the server thread, from {@link #onTick()}. Polymer may call
 * {@link #startWatching} from its chunk-tracking path, so that method only decides whether to watch
 * and queues the player for a refresh on the next tick.
 *
 * Why the resends rather than {@code PolymerBlock#getPolymerBlockState}: that hook has no block
 * position (Polymer maps whole chunk palettes with it), so it cannot answer "is THIS player near
 * THIS block". It stays the default — the real beacon, our real glass colour — and the holder,
 * which does know the positions, pushes the per-player exceptions.
 */
public final class BeaconBeamHolder extends ElementHolder {
	private static final float VIEW_RANGE = 4.0f; // Display renders within viewRange * 64 blocks
	/** Beyond this the player is not tracking the column at all and is not worth a packet. */
	private static final int TRACKING_RANGE = 512;

	private final ServerLevel level;
	private final BlockPos pos;

	private final BlockDisplayElement beacon;
	private final Segment[] segments = new Segment[BeaconBeams.SEGMENTS];

	private boolean engaged;
	/** Degrees of Y rotation last sent to the beam; see {@link #spin()}. */
	private float spin;
	private List<BeamWalk.Section> sections = List.of();
	private List<BlockPos> ours = List.of();
	/** Last known side of the boundary per player, so only crossings cost packets. */
	private final Map<UUID, Boolean> side = new HashMap<>();
	/** Players Polymer just attached, to be caught up on the next tick (filled off-thread). */
	private final Queue<UUID> pendingRefresh = new ConcurrentLinkedQueue<>();
	private int ticks;

	public BeaconBeamHolder(ServerLevel level, BlockPos pos) {
		this.level = level;
		this.pos = pos.immutable();

		// Everything is allocated here, while nobody is watching, and stays for the holder's life.
		this.beacon = new BlockDisplayElement(Blocks.BEACON.defaultBlockState());
		beacon.setTranslation(new Vector3f(-0.5f, -0.5f, -0.5f)); // holder sits at the block centre
		beacon.setBrightness(Brightness.FULL_BRIGHT);
		beacon.setViewRange(VIEW_RANGE);
		beacon.setDisplaySize(0, 0);
		addElement(beacon);
		for (int i = 0; i < segments.length; i++) {
			segments[i] = new Segment();
		}
	}

	/** One slice of beam: the opaque core and the translucent glow, plus what they were last set to. */
	private final class Segment {
		private final ItemDisplayElement core = quad();
		private final ItemDisplayElement glow = quad();
		{
			// Only the core turns — see spin() — so only the core needs somewhere to turn to. One
			// refresh's worth is sent per second and slerped by the client over exactly that second.
			core.setInterpolationDuration(BeaconBeams.REFRESH_TICKS);
		}
		private int color = -1;
		private int height = -1;
		private int offset = Integer.MIN_VALUE;
		private boolean shown;

		private ItemDisplayElement quad() {
			ItemDisplayElement element = new ItemDisplayElement();
			element.setItemDisplayContext(ItemDisplayContext.NONE);
			element.setBrightness(Brightness.FULL_BRIGHT);
			element.setViewRange(VIEW_RANGE);
			// setDisplaySize(0, 0) sets Display#noCulling: the element's own box says nothing
			// about a 256-block beam, so let it render whenever the chunk does.
			element.setDisplaySize(0, 0);
			element.setInvisible(true);
			addElement(element);
			return element;
		}

		/**
		 * Show this segment, if it is not already showing exactly this.
		 *
		 * <b>All three values move together or not at all.</b> Every one of them feeds more than one
		 * setter — the height picks the model as well as the scale, and the translation is the segment's
		 * mid-point, so it is a function of the offset <i>and</i> the height. Updating them piecemeal is
		 * what left a segment cut from 16 blocks to 4 at the same offset still centred where its taller
		 * self had been, six blocks up inside the next segment, with a gap where it should have been.
		 */
		void show(int argb, int fromBeacon, int blocks) {
			// A segment that has been hidden kept whatever angle it was left at. Snap it into step
			// with the rest of the beam (no interpolation start, so it does not sweep there) before
			// the next spin() turns them all together.
			if (!shown) core.setLeftRotation(rotation());
			if (shown && color == argb && height == blocks && offset == fromBeacon) return;

			// The stack's model id carries the height: each segment size has its own sheet, so the beam
			// pattern keeps vanilla's density whatever the segment covers.
			core.setItem(BeaconBeams.beamStack(BeaconBeams.CORE, blocks, argb));
			glow.setItem(BeaconBeams.beamStack(BeaconBeams.GLOW, blocks, argb));
			Vector3f scale = new Vector3f(1, blocks, 1);
			core.setScale(scale);
			glow.setScale(scale);
			// The model is one block tall and renders centred on the element, so the segment's
			// mid-point relative to the beacon's centre is where it has to sit.
			Vector3f translation = new Vector3f(0, fromBeacon + blocks / 2.0f - 0.5f, 0);
			core.setTranslation(translation);
			glow.setTranslation(translation);

			color = argb;
			height = blocks;
			offset = fromBeacon;
			shown = true;
		}

		/** One second's worth of turn, slerped by the client over the second it takes to arrive. */
		void spin(Quaternionf rotation) {
			if (!shown) return;
			core.setLeftRotation(rotation);
			core.startInterpolation();
		}

		/** Hide without removing: an item display with an empty stack draws nothing. */
		void hide() {
			if (!shown) return;
			core.setItem(ItemStack.EMPTY);
			glow.setItem(ItemStack.EMPTY);
			shown = false;
		}
	}

	// ------------------------------------------------------------------ watching

	/**
	 * Polymer calls this from its chunk-tracking path, which is not necessarily our tick. Decide
	 * only; the block resend that has to accompany it is queued for {@link #onTick()}.
	 */
	@Override
	public boolean startWatching(ServerGamePacketListenerImpl handler) {
		ServerPlayer player = handler.getPlayer();
		if (!engaged || player == null || !BeaconBeams.isNear(pos, player)) return false;
		boolean added = super.startWatching(handler);
		if (added) pendingRefresh.add(player.getUUID());
		return added;
	}

	@Override
	public void destroy() {
		for (Map.Entry<UUID, Boolean> entry : Map.copyOf(side).entrySet()) {
			if (!Boolean.TRUE.equals(entry.getValue())) continue;
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			if (player != null) restore(player);
		}
		side.clear();
		super.destroy();
	}

	// ------------------------------------------------------------------ tick

	@Override
	protected void onTick() {
		catchUpNewWatchers();
		if (ticks++ % BeaconBeams.REFRESH_TICKS != 0) return;
		BeamWalk.Result result = BeamWalk.walk(level, pos);
		// Only columns with one of our colours in them are worth taking over; a plain vanilla
		// beacon keeps its own, correct, client-side beam.
		if (!result.lit() || !result.tinted()) {
			if (engaged) disengage();
			return;
		}
		boolean first = !engaged;
		engaged = true;
		ours = result.ours();
		if (first || !sections.equals(result.sections())) {
			sections = result.sections();
			layout();
		}
		spin();
		reclassify();
	}

	/**
	 * Turn the beam like vanilla does. A vanilla client derives the angle from the game time and
	 * turns the beam {@value BeaconBeams#SPIN_DEGREES}° a second; a display entity's angle comes
	 * from the server, so we send one refresh's worth at a time with
	 * {@code interpolation_duration = }{@value BeaconBeams#REFRESH_TICKS} and let the client slerp
	 * across it. One small metadata update per shown segment per second, and the beam never stops.
	 *
	 * <b>Only the core turns.</b> {@code BeaconRenderer} pushes a pose, rotates it by
	 * {@code animationTime * 2.25 - 45} about {@code Axis.YP}, submits the inner beam, and pops it
	 * again <i>before</i> laying out the glow — so vanilla's outer beam is a fixed, axis-aligned box
	 * and only the inner one spins inside it.
	 *
	 * The wrap is at 720°, not 360°: a quaternion halves its angle, so q(720°) is exactly q(0°)
	 * while q(360°) is −q(0°) — the same rotation with the opposite sign, which a shortest-path
	 * slerp would walk backwards through.
	 */
	private void spin() {
		spin += BeaconBeams.SPIN_DEGREES;
		if (spin >= 720.0f) spin -= 720.0f;
		Quaternionf rotation = rotation();
		for (Segment segment : segments) segment.spin(rotation);
	}

	private Quaternionf rotation() {
		return new Quaternionf().rotateY((float) Math.toRadians(spin));
	}

	private void catchUpNewWatchers() {
		UUID id;
		while ((id = pendingRefresh.poll()) != null) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			if (player == null || !engaged || !BeaconBeams.isNear(pos, player)) continue;
			side.put(id, Boolean.TRUE);
			hide(player);
		}
	}

	/** Give every player the vanilla column back and blank the beam; the elements stay allocated. */
	private void disengage() {
		engaged = false;
		for (Map.Entry<UUID, Boolean> entry : Map.copyOf(side).entrySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			if (player != null) restore(player);
		}
		side.clear();
		for (ServerGamePacketListenerImpl handler : List.copyOf(getWatchingPlayers())) stopWatching(handler);
		for (Segment segment : segments) segment.hide();
		sections = List.of();
		ours = List.of();
	}

	// ------------------------------------------------------------------ geometry

	/**
	 * Fill the segment pool from {@link BeaconBeams#slice}, which owns the geometry, and blank the
	 * segments it did not need. The sky the last section is stretched to is the world's build height
	 * plus {@value BeaconBeams#SKY_MARGIN}, measured from the beacon's own bottom.
	 */
	private void layout() {
		int toSky = level.getMaxY() + 1 + BeaconBeams.SKY_MARGIN - pos.getY();
		List<BeaconBeams.Slice> slices = BeaconBeams.slice(sections, segments.length, toSky);
		for (int i = 0; i < slices.size(); i++) {
			BeaconBeams.Slice slice = slices.get(i);
			segments[i].show(slice.color(), slice.fromBeacon(), slice.blocks());
		}
		for (int i = slices.size(); i < segments.length; i++) segments[i].hide();
		// The tuples, not just the totals: a beam whose first section is a block tall is a column with
		// our glass right on the beacon, not a layout that dropped it.
		StringBuilder shape = new StringBuilder();
		for (BeaconBeams.Slice slice : slices) {
			shape.append(shape.isEmpty() ? "" : " ").append('+').append(slice.fromBeacon())
					.append('h').append(slice.blocks()).append('#')
					.append(Integer.toHexString(slice.color() & 0xFFFFFF));
		}
		MoreDyes.LOGGER.info("[{}] beacon {} beam: {} section(s) {}, {} of {} segments: {}",
				MoreDyes.MOD_ID, pos, sections.size(), sections, slices.size(), segments.length, shape);
	}

	/**
	 * Test seam: one layout pass over these sections, the half of {@link #onTick()} that decides
	 * geometry. The pool is reused in place, so what a test is really exercising is a segment being
	 * reassigned from one shape to another.
	 */
	public void layoutFor(List<BeamWalk.Section> sections) {
		this.sections = sections;
		layout();
	}

	/** Test seam: the two elements of pool segment {@code index}, opaque core first. */
	public ItemDisplayElement[] segmentElements(int index) {
		return new ItemDisplayElement[]{segments[index].core, segments[index].glow};
	}

	// ------------------------------------------------------------------ near/far

	private void reclassify() {
		side.keySet().removeIf(id -> level.getServer().getPlayerList().getPlayer(id) == null);
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(pos) > (double) TRACKING_RANGE * TRACKING_RANGE) continue;
			boolean near = BeaconBeams.isNear(pos, player);
			Boolean was = side.put(player.getUUID(), near);
			if (was != null && was == near) continue;
			if (near) {
				// startWatching queues the block resend; doing it here too would only duplicate it.
				startWatching(player);
			} else {
				stopWatching(player);
				restore(player);
			}
		}
	}

	/**
	 * Near: barrier where the beacon is (the block display draws it), our glass as it always is. The
	 * block above each of ours goes back too — {@link #restore} may have put a ghost glass there, and
	 * only resending its real state gets rid of it.
	 */
	private void hide(ServerPlayer player) {
		List<ClientboundBlockUpdatePacket> packets = new ArrayList<>(ours.size() * 2 + 1);
		packets.add(new ClientboundBlockUpdatePacket(pos, Blocks.BARRIER.defaultBlockState()));
		for (BlockPos p : ours) {
			// The server state, not its donor: Polymer maps every state written to a packet, and a
			// donor handed back to it maps a second time into its no-pack look. See isClientSafe.
			packets.add(new ClientboundBlockUpdatePacket(p, level.getBlockState(p)));
			BlockPos above = p.above();
			packets.add(new ClientboundBlockUpdatePacket(above, level.getBlockState(above)));
		}
		send(player, packets);
	}

	/**
	 * Far: the real beacon, and our glass as the vanilla stained glass whose own beam walk lands
	 * closest to our colour. Where there is air above one of ours, that is a <i>pair</i> of ghost
	 * blocks whose average is closer than any single dye — see {@link BeaconBeams#fallback}. The
	 * upper one is a lie told to this player only; the server block stays air.
	 */
	private void restore(ServerPlayer player) {
		List<ClientboundBlockUpdatePacket> packets = new ArrayList<>(ours.size() * 2 + 1);
		packets.add(new ClientboundBlockUpdatePacket(pos, level.getBlockState(pos)));
		for (BlockPos p : ours) {
			BlockState state = level.getBlockState(p);
			if (!(state.getBlock() instanceof GlassBlocks.Glass glass)) {
				packets.add(new ClientboundBlockUpdatePacket(p, state)); // server state; Polymer maps it
				continue;
			}
			BlockPos above = p.above();
			boolean roomAbove = level.getBlockState(above).isAir();
			BeaconBeams.Fallback fallback = BeaconBeams.fallback(glass.color(), roomAbove);
			packets.add(new ClientboundBlockUpdatePacket(p, fallback.lower()));
			if (fallback.upper() != null) packets.add(new ClientboundBlockUpdatePacket(above, fallback.upper()));
		}
		send(player, packets);
	}

	private void send(ServerPlayer player, List<ClientboundBlockUpdatePacket> packets) {
		if (!level.getServer().isSameThread()) {
			// Never write packets from a chunk worker: they would interleave with Polymer's bundles.
			MoreDyes.LOGGER.error("[{}] beacon {} tried to resend blocks off the server thread",
					MoreDyes.MOD_ID, pos);
			return;
		}
		for (ClientboundBlockUpdatePacket packet : packets) {
			BlockState state = packet.getBlockState();
			// A state Polymer does not map to itself is one it will map again on the way out, which
			// puts a block we never chose on the client. Ours have to be sent as server states.
			if (state.getBlock() instanceof PolymerBlock || BeaconBeams.isClientSafe(state)) continue;
			MoreDyes.LOGGER.error("[{}] beacon {} would resend {} at {}, which Polymer re-maps to {}",
					MoreDyes.MOD_ID, pos, state, packet.getPos(),
					PolymerBlockUtils.getPolymerBlockState(state, null));
		}
		for (ClientboundBlockUpdatePacket packet : packets) player.connection.send(packet);
	}
}
