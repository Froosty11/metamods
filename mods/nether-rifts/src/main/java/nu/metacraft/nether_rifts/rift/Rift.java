package nu.metacraft.nether_rifts.rift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One rift: where it is, how far along it is, and (for a raid) which wave and how long things have taken. */
public final class Rift {

	public static final Codec<Rift> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(r -> r.id),
			RiftShape.CODEC.fieldOf("shape").forGetter(r -> r.shape),
			Codec.BOOL.fieldOf("crying").forGetter(r -> r.crying),
			Tier.CODEC.fieldOf("tier").forGetter(r -> r.tier),
			Stage.CODEC.fieldOf("stage").forGetter(r -> r.stage),
			Codec.INT.fieldOf("stage_ticks").forGetter(r -> r.stageTicks),
			Codec.INT.fieldOf("countdown").forGetter(r -> r.countdown),
			Codec.INT.fieldOf("wave").forGetter(r -> r.wave),
			Codec.INT.fieldOf("wave_size").forGetter(r -> r.waveSize),
			Codec.INT.fieldOf("wave_ticks").forGetter(r -> r.waveTicks),
			Codec.INT.fieldOf("abandon_ticks").forGetter(r -> r.abandonTicks)
	).apply(i, Rift::new));

	private final UUID id;
	private final RiftShape shape;
	private final boolean crying;
	private final Tier tier;
	private final List<BlockPos> blockList;
	private Stage stage;
	int stageTicks;
	int countdown;
	int wave;
	/** How many mobs the current wave started with (the boss bar's 100%). Written by RiftRaid. */
	public int waveSize;
	public int waveTicks;
	/** Ticks with no participant in range; RiftRaid collapses the raid when it reaches abandon_after. */
	public int abandonTicks;
	@Nullable private RiftVisual visual;
	@Nullable ServerBossEvent bossBar;

	private Rift(UUID id, RiftShape shape, boolean crying, Tier tier, Stage stage, int stageTicks,
			int countdown, int wave, int waveSize, int waveTicks, int abandonTicks) {
		this.id = id;
		this.shape = shape;
		this.crying = crying;
		this.tier = tier;
		this.blockList = new ArrayList<>(shape.blocks());
		this.stage = stage;
		this.stageTicks = stageTicks;
		this.countdown = countdown;
		this.wave = wave;
		this.waveSize = waveSize;
		this.waveTicks = waveTicks;
		this.abandonTicks = abandonTicks;
	}

	Rift(RiftShape shape, boolean crying) {
		this(UUID.randomUUID(), shape, crying, Tier.of(shape.size(), NetherRiftsConfig.getInstance().tiers()), Stage.LIT, 0, 0, 0, 0, 0, 0);
	}

	public UUID id() {
		return id;
	}

	public RiftShape shape() {
		return shape;
	}

	public boolean crying() {
		return crying;
	}

	public Tier tier() {
		return tier;
	}

	public Stage stage() {
		return stage;
	}

	public int wave() {
		return wave;
	}

	@Nullable
	public RiftVisual visual() {
		return visual;
	}

	@Nullable
	public ServerBossEvent bossBar() {
		return bossBar;
	}

	public Vec3 centre() {
		return shape.centre();
	}

	public BlockPos randomBlock(RandomSource random) {
		return blockList.get(random.nextInt(blockList.size()));
	}

	public void setStage(ServerLevel level, Stage stage) {
		this.stage = stage;
		this.stageTicks = 0;
		if (visual != null) {
			visual.setStage(stage);
		}
	}

	public void placeBlocks(ServerLevel level) {
		for (var pos : shape.blocks()) {
			level.setBlock(pos, RiftBlocks.RIFT.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	public void clearBlocks(ServerLevel level) {
		for (var pos : shape.blocks()) {
			if (level.getBlockState(pos).is(RiftBlocks.RIFT)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	public void destroyVisual() {
		if (visual != null) {
			visual.destroy();
			visual = null;
		}
	}

	/** A waiting rift goes away quietly: its blocks and visual, nothing else. */
	public void fade(ServerLevel level, RiftData data) {
		clearBlocks(level);
		destroyVisual();
		data.remove(this);
	}

	public void tick(ServerLevel level, RiftData data) {
		if (!level.isPositionEntityTicking(shape.centreBlock())) {
			return;
		}
		if (visual == null) {
			visual = RiftVisual.attach(level, this);
		}
		stageTicks++;
		if (stageTicks % 20 == 0 && !shape.frameIntact(level)) {
			fade(level, data);
		}
	}
}
