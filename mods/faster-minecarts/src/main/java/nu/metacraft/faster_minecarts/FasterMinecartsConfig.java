package nu.metacraft.faster_minecarts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.container.ServerAware;
import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Config(name = "Faster Minecarts", description = "Faster minecarts, with speed boosts from blocks the server lists.")
public record FasterMinecartsConfig(
		@Option(description = "Every minecart is fast, not only upgraded ones.") boolean globalFasterMinecarts,
		@Option(description = "Top speed of a fast minecart, blocks per second.", min = 0) double maxMinecartSpeed,
		@Option(description = "Top speed under water, blocks per second.", min = 0) double maxMinecartSpeedUnderwater,
		@Option(description = "Above this speed (blocks per tick) a minecart hurts what it hits; empty for never.", min = 0) Optional<Double> dangerousMinecartSpeed,
		@Option(description = "Damage per block per tick above the dangerous speed.", min = 0) double damageFactor,
		@Option(description = "Use vanilla's experimental minecart physics.", restart = true) ExperimentalMinecartMode experimentalMinecartMode
) {
	private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve(FasterMinecarts.NAMESPACE + ".json");

	public static final FasterMinecartsConfig DEFAULT = new FasterMinecartsConfig(
			false, 60, 45, Optional.of(30 / 3.6 / 20), 2.16 * 20, ExperimentalMinecartMode.EXPERIMENTAL
	);

	public static final MapCodec<FasterMinecartsConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.BOOL.fieldOf("global_faster_minecarts").forGetter(FasterMinecartsConfig::globalFasterMinecarts),
					Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("max_minecart_speed").forGetter(FasterMinecartsConfig::maxMinecartSpeed),
					Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("max_minecart_speed_underwater").forGetter(FasterMinecartsConfig::maxMinecartSpeedUnderwater),
					Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("dangerous_minecart_speed").forGetter(FasterMinecartsConfig::dangerousMinecartSpeed),
					Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("damage_factor").forGetter(FasterMinecartsConfig::damageFactor),
					ExperimentalMinecartMode.CODEC.fieldOf("experimental_minecart_mode").forGetter(FasterMinecartsConfig::experimentalMinecartMode)
			).apply(instance, FasterMinecartsConfig::new)
	);

	private static final ServerAware<ConfigContainer<ServerAware.ConfigPair<FasterMinecartsConfig, Loaded>>, Loaded> CONTAINER = ConfigContainer.Builder.create(
			CODEC, () -> DEFAULT
	).reloadAfterServer().makeRegistryAware(Loaded.CODEC).describedBy(FasterMinecartsConfig.class).setInitializer(Loaded::createDefault).build(configPath);

	public static FasterMinecartsConfig getConfig() {
		return CONTAINER.getContainer().get().staticValues();
	}

	private static final StartupValue<ExperimentalMinecartMode> STARTUP_MODE = new StartupValue<>(() -> getConfig().experimentalMinecartMode());

	/**
	 * {@code experimental_minecart_mode} as it was when first read. It needs a restart (feature
	 * flags are fixed at startup), so every reader uses this and not {@link #getConfig()}.
	 */
	public static ExperimentalMinecartMode startupMode() {
		return STARTUP_MODE.get();
	}

	public static FasterMinecartsConfig.Loaded getConfig(MinecraftServer server) {
		return CONTAINER.get(server);
	}

	public record MinecartModifier(EntityPredicate minecartPredicate, Optional<Double> topSpeedFactor, Optional<Double> poweredRailAccelerationFactor) {
		public static final Codec<MinecartModifier> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						EntityPredicate.CODEC.fieldOf("predicate").forGetter(MinecartModifier::minecartPredicate),
						Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("top_speed").forGetter(MinecartModifier::topSpeedFactor),
						Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("powered_rail_acceleration_factor").forGetter(MinecartModifier::poweredRailAccelerationFactor)
				).apply(instance, MinecartModifier::new)
		);
	}

	public record EntityDamageList(List<EntityPredicate> predicates, Mode mode) {

		public static final Codec<EntityDamageList> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						EntityPredicate.CODEC.listOf().fieldOf("predicates").forGetter(EntityDamageList::predicates),
						Mode.CODEC.fieldOf("mode").forGetter(EntityDamageList::mode)
				).apply(instance, EntityDamageList::new)
		);

		public boolean isIncluded(Vec3 pos, Entity entity) {
			if (entity.level() instanceof ServerLevel sw) {
				for (var predicate : predicates) {
					if (predicate.matches(sw, pos, entity)) {
						return mode == Mode.ONLY;
					}
				}
			}
			return mode == Mode.IGNORE;
		}

		public enum Mode implements StringRepresentable {
			ONLY("only"),
			IGNORE("ignore");

			public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

			private final String name;

			Mode(String name) {
				this.name = name;
			}

			@Override
			public String getSerializedName() {
				return name;
			}
		}
	}

	public record BlockBooster(BlockPredicate predicate, double topSpeedIncrease) {
		public static final Codec<BlockBooster> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						BlockPredicate.CODEC.fieldOf("predicate").forGetter(BlockBooster::predicate),
						Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("top_speed_increase").forGetter(BlockBooster::topSpeedIncrease)
				).apply(instance, BlockBooster::new)
		);
	}

	public enum ExperimentalMinecartMode implements StringRepresentable {
		LEGACY(false, "legacy"),
		EXPERIMENTAL(true, "experimental");

		public static final Codec<ExperimentalMinecartMode> CODEC = StringRepresentable.fromEnum(ExperimentalMinecartMode::values);

		private final boolean enabled;
		private final String name;

		ExperimentalMinecartMode(boolean enabled, String name) {
			this.enabled = enabled;
			this.name = name;
		}

		public boolean isEnabled() {
			return enabled;
		}

		@Override
		public @NotNull String getSerializedName() {
			return name;
		}
	}

	public record Loaded(
			List<MinecartModifier> minecartModifiers,
			EntityDamageList entityDamageList,
			List<BlockBooster> blockBoosters
	) {
		public static final MapCodec<Loaded> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						MinecartModifier.CODEC.listOf().fieldOf("minecart_modifiers").forGetter(c -> c.minecartModifiers),
						EntityDamageList.CODEC.fieldOf("entity_damage_list").forGetter(c -> c.entityDamageList),
						BlockBooster.CODEC.listOf().fieldOf("block_boosters").forGetter(c -> c.blockBoosters)
				).apply(instance, Loaded::new)
		);

		public static Loaded createDefault() {
			return new Loaded(
					List.of(
							new MinecartModifier(EntityPredicate.Builder.entity().build(), Optional.empty(), Optional.of(0.8))
					),
					new EntityDamageList(
							List.of(
									EntityPredicate.Builder.entity().of(
											BuiltInRegistries.ENTITY_TYPE, EntityTypes.MINECART
									).build(),
									EntityPredicate.Builder.entity().of(
											BuiltInRegistries.ENTITY_TYPE, EntityTypes.CHEST_MINECART
									).build(),
									EntityPredicate.Builder.entity().of(
											BuiltInRegistries.ENTITY_TYPE, EntityTypes.COMMAND_BLOCK_MINECART
									).build(),
									EntityPredicate.Builder.entity().of(
											BuiltInRegistries.ENTITY_TYPE, EntityTypes.FURNACE_MINECART
									).build(),
									EntityPredicate.Builder.entity().of(
											BuiltInRegistries.ENTITY_TYPE, EntityTypes.HOPPER_MINECART
									).build(),
									EntityPredicate.Builder.entity().of(
											BuiltInRegistries.ENTITY_TYPE, EntityTypes.TNT_MINECART
									).build(),
									EntityPredicate.Builder.entity().of(
											BuiltInRegistries.ENTITY_TYPE, EntityTypes.SPAWNER_MINECART
									).build(),
									EntityPredicate.Builder.entity().of(
											BuiltInRegistries.ENTITY_TYPE, EntityTypes.ITEM
									).build(),
									EntityPredicate.Builder.entity().of(
											BuiltInRegistries.ENTITY_TYPE, EntityTypes.EXPERIENCE_ORB
									).build(),
									EntityPredicate.Builder.entity().vehicle(
											EntityPredicate.Builder.entity().of(
													BuiltInRegistries.ENTITY_TYPE, EntityTypes.MINECART
											)
									).build()
							),
							EntityDamageList.Mode.IGNORE
					),
					List.of(
							new BlockBooster(BlockPredicate.Builder.block().of(
									BuiltInRegistries.BLOCK, Blocks.ICE
							).build(), 5),
							new BlockBooster(BlockPredicate.Builder.block().of(
									BuiltInRegistries.BLOCK, Blocks.PACKED_ICE
							).build(), 10),
							new BlockBooster(BlockPredicate.Builder.block().of(
									BuiltInRegistries.BLOCK, Blocks.BLUE_ICE
							).build(), 20)
					)
			);
		}

		public boolean shouldDamageEntity(Vec3 pos, Entity entity) {
			return entityDamageList.isIncluded(pos, entity);
		}

		public double getBlockBoost(ServerLevel world, BlockPos pos) {
			double amount = 0;
			for (var boosters : blockBoosters) {
				var targetPos = new BlockPos.MutableBlockPos();
				targetPos.set(pos.below());
				if (world.getBlockState(targetPos).getBlock() instanceof BaseRailBlock) {
					targetPos.move(Direction.DOWN);
				}
				if (boosters.predicate.matches(world, targetPos)) {
					amount += boosters.topSpeedIncrease;
				}
			}
			return amount;
		}

		public Stream<MinecartModifier> getRelevantModifiers(AbstractMinecart minecart) {
			return minecartModifiers.stream().filter(modifier -> modifier.minecartPredicate.matches((ServerLevel) minecart.level(), minecart.position(), minecart));
		}

	}
}
