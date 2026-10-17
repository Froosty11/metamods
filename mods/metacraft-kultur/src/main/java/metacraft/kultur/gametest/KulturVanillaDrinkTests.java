package metacraft.kultur.gametest;

import metacraft.kultur.drinks.KulturDrinks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The chapter drinks without Brewery ({@link KulturDrinks}). Every test here passes at once when
 * Brewery is loaded, as {@code KulturDrinkTests} does without it. Run the other mode with
 * {@code -PkulturNoBrewery}.
 */
public final class KulturVanillaDrinkTests {
	@GameTest
	public void drinksAreActiveWithoutBrewery(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		helper.assertTrue(KulturDrinks.active(), "the drinks are not active without Brewery");
		helper.assertTrue(KulturDrinks.registered(), "the drinks were not registered");
		helper.succeed();
	}

	/** True when this mode is on; otherwise the test has nothing to do and succeeds. */
	private static boolean vanilla(GameTestHelper helper) {
		if (KulturDrinks.active()) return true;
		helper.succeed();   // Brewery brews the drinks; KulturDrinkTests covers that
		return false;
	}
}
