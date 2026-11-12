package se.metacraft.dungeons.compat;

import de.bluecolored.bluemap.core.util.Key;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlueMapCompat {

	private static final Map<ResourceKey<Level>, Unit> DISABLED_DIMENSIONS = new ConcurrentHashMap<>();

	public static void disableRenderer(ResourceKey<Level> dim) {
		DISABLED_DIMENSIONS.put(dim, Unit.INSTANCE);
	}

	public static boolean isDisabled(Key key) {
		return DISABLED_DIMENSIONS.containsKey(
			ResourceKey.create(
				Registries.DIMENSION,
				Identifier.fromNamespaceAndPath(key.getNamespace(), key.getValue())
			)
		);
	}

}
