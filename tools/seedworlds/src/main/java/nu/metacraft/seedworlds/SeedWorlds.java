package nu.metacraft.seedworlds;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;

/**
 * Opens one persistent world per finalist seed (tools/seedscan/results/README.md, top 5 verified),
 * each with the overworld's generator (Tectonic + Metacraft pack + whatever else the server runs)
 * on its own seed, and adds /seedworld to hop between them.
 */
public final class SeedWorlds implements ModInitializer {
    record Seed(int rank, long seed, int x, int z) {}

    static final List<Seed> SEEDS = List.of(
        new Seed(1, -2490713400371329797L, -4160, -1600),
        new Seed(2, -3798611108748814191L, -5504, 1280),
        new Seed(3, 1555912076825267547L, -5184, -4032),
        new Seed(4, 4127421199368335624L, 1664, 2240),
        new Seed(5, 2459899889739035294L, -4288, -1280));

    // Where /seedworld home goes: the main world's border centre.
    static final int HOME_X = -5184;
    static final int HOME_Z = -4032;

    static final Map<Integer, RuntimeLevelHandle> LEVELS = new HashMap<>();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (Seed s : SEEDS) {
                LEVELS.put(s.rank(), open(server, s));
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LEVELS.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
            Commands.literal("seedworld")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("home").executes(ctx -> {
                    ServerLevel overworld = ctx.getSource().getServer().overworld();
                    return go(ctx.getSource(), overworld, HOME_X, HOME_Z, "the main world");
                }))
                .then(Commands.argument("rank", IntegerArgumentType.integer(1, SEEDS.size())).executes(ctx -> {
                    Seed s = SEEDS.get(IntegerArgumentType.getInteger(ctx, "rank") - 1);
                    RuntimeLevelHandle handle = LEVELS.get(s.rank());
                    return go(ctx.getSource(), handle.asLevel(), s.x(), s.z(), "seed #" + s.rank() + " (" + s.seed() + ")");
                }))));
    }

    static RuntimeLevelHandle open(MinecraftServer server, Seed s) {
        RuntimeLevelConfig config = new RuntimeLevelConfig()
            .setDimensionType(BuiltinDimensionTypes.OVERWORLD)
            .setGenerator(server.overworld().getChunkSource().getGenerator())
            .setSeed(s.seed())
            .mirrorOverworldGameState();
        return Fantasy.get(server).getOrOpenPersistentLevel(Identifier.fromNamespaceAndPath("seedworlds", "seed_" + s.rank()), config);
    }

    static int list(CommandSourceStack source) {
        for (Seed s : SEEDS) {
            source.sendSuccess(() -> Component.literal("#" + s.rank() + "  " + s.seed() + "  centre " + s.x() + ", " + s.z()
                + "  -> /seedworld " + s.rank()), false);
        }
        return SEEDS.size();
    }

    static int go(CommandSourceStack source, ServerLevel level, int x, int z, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
        source.sendSuccess(() -> Component.literal("Teleported to " + name + " at " + x + ", " + y + ", " + z), false);
        return 1;
    }
}
