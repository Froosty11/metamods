package se.metacraft.ovvarclient;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.metacraft.ovvar.combo.ComboAssets;

import java.util.Map;
import java.util.Optional;

/**
 * The ovvar patch combinations this client draws itself: every {@code ovvar:<chapter>/<piece>/<combo>}
 * equipment asset its packs lack is built from the name ({@link ComboAssets#jsonFor}, the code the server
 * builds its combination pack with), so an ovvar server that saw the handshake ({@link OvvarClient}) never
 * pushes this client that pack. The textures the definitions name are all in the server's Polymer pack.
 */
public final class ClientCombos {
	private ClientCombos() {}

	static final Logger LOGGER = LoggerFactory.getLogger("ovvar-client");

	/** Set once the asset manager's mixin has run (a resource reload went through it): only then may the client handshake. */
	private static volatile boolean ready;

	public static boolean ready() {
		return ready;
	}

	public static void markReady() {
		ready = true;
	}

	/** An {@code ovvar:} asset's definition from its path, or empty if the path is not a combination this build can read. */
	public static Optional<EquipmentClientInfo> build(String path) {
		return ComboAssets.jsonFor(path).flatMap(json -> EquipmentClientInfo.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result());
	}

	/**
	 * What to draw for an {@code ovvar:} asset the packs do not hold: the combination built from its name or,
	 * for a name this build cannot read (which a server on the same protocol never sends), the garment one
	 * level up without its patches rather than nothing. Never throws: this runs while rendering.
	 */
	public static EquipmentClientInfo buildOrFallBack(ResourceKey<EquipmentAsset> id, Map<ResourceKey<EquipmentAsset>, EquipmentClientInfo> loaded) {
		String path = id.identifier().getPath();
		try {
			Optional<EquipmentClientInfo> built = build(path);
			if (built.isPresent()) return built.get();
		} catch (RuntimeException e) {
			LOGGER.warn("[ovvar-client] building equipment asset {} failed", id.identifier(), e);
		}
		int slash = path.lastIndexOf('/');
		if (slash < 0) return EquipmentAssetManager.MISSING;
		LOGGER.warn("[ovvar-client] cannot build equipment asset {}; drawing it without its patches", id.identifier());
		return loaded.getOrDefault(ResourceKey.create(EquipmentAssets.ROOT_ID, id.identifier().withPath(path.substring(0, slash))), EquipmentAssetManager.MISSING);
	}
}
