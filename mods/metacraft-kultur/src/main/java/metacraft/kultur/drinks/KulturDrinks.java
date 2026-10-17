package metacraft.kultur.drinks;

import metacraft.kultur.Kultur;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/**
 * The chapter drinks when Brewery is not on the server: PolymITer's brewing-stand recipes, and
 * Brewery's drunkenness (the meter, its steps, the stagger, the blackout) done here instead. With
 * Brewery on the server none of this registers and Brewery brews the drinks from
 * {@code data/kultur/brewery_drinks}, as before.
 */
public final class KulturDrinks {
	/** {@code kultur:inebriated}; null with Brewery on the server. */
	public static Holder<MobEffect> INEBRIATED;
	private static boolean registered;

	private KulturDrinks() {}

	/** True when this mode is on: Brewery is not loaded. */
	public static boolean active() {
		return !FabricLoader.getInstance().isModLoaded("brewery");
	}

	/** True once {@link #register} has run in this mode. */
	public static boolean registered() {
		return registered;
	}

	/** Registers everything; does nothing when Brewery is loaded. */
	public static void register() {
		if (!active() || registered) return;
		INEBRIATED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id("inebriated"), (MobEffect) new InebriatedEffect());
		Drunkenness.register();
		DrunkTicker.register();
		registered = true;
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, path);
	}
}
