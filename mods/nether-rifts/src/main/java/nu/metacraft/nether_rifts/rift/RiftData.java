package nu.metacraft.nether_rifts.rift;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.lib.util.SavedDataTypeCache;
import nu.metacraft.nether_rifts.NetherRifts;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Every rift in one level, and which rift a block or frame position belongs to. */
public class RiftData extends SavedData {

	public static final Codec<RiftData> CODEC = Rift.CODEC.listOf().xmap(RiftData::new, d -> List.copyOf(d.rifts.values()));

	private static final SavedDataTypeCache.Type<RiftData, ServerLevel> TYPE = new SavedDataTypeCache.Type<>(
			level -> new SavedDataType<>(NetherRifts.getID("rifts"), RiftData::new, CODEC, null)
	);

	private final Map<UUID, Rift> rifts = new LinkedHashMap<>();
	private final Map<BlockPos, UUID> index = new HashMap<>();

	public RiftData() {
	}

	private RiftData(List<Rift> loaded) {
		loaded.forEach(this::put);
	}

	public static RiftData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(SavedDataTypeCache.get(level, TYPE));
	}

	/** Torn and raiding rifts across every level. */
	public static int activeRaids(MinecraftServer server) {
		int count = 0;
		for (var level : server.getAllLevels()) {
			for (var rift : get(level).all()) {
				if (rift.stage().raiding()) {
					count++;
				}
			}
		}
		return count;
	}

	public Rift open(ServerLevel level, RiftShape shape, boolean crying) {
		var rift = new Rift(shape, crying);
		put(rift);
		rift.placeBlocks(level);
		setDirty();
		return rift;
	}

	private void put(Rift rift) {
		rifts.put(rift.id(), rift);
		rift.shape().blocks().forEach(p -> index.put(p, rift.id()));
		rift.shape().frame().forEach(p -> index.put(p, rift.id()));
	}

	public void remove(Rift rift) {
		rifts.remove(rift.id());
		index.values().removeIf(rift.id()::equals);
		setDirty();
	}

	public Optional<Rift> at(BlockPos pos) {
		return Optional.ofNullable(index.get(pos)).map(rifts::get);
	}

	public Optional<Rift> byId(UUID id) {
		return Optional.ofNullable(rifts.get(id));
	}

	public List<Rift> all() {
		return List.copyOf(rifts.values());
	}

	public void tick(ServerLevel level) {
		if (rifts.isEmpty()) {
			return;
		}
		for (var rift : all()) {
			rift.tick(level, this);
		}
		setDirty();
	}
}
