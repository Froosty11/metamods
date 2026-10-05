import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import nu.metacraft.void_anchor.VoidAnchorConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VoidAnchorConfigTests {

	@BeforeAll
	public static void init() {
		VoidAnchorTests.init();
	}

	@Test
	public void defaultsRoundTrip() {
		var json = VoidAnchorConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, VoidAnchorConfig.DEFAULT).getOrThrow();
		var back = VoidAnchorConfig.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(VoidAnchorConfig.DEFAULT, back);
		assertEquals("minecraft:ender_pearl", json.getAsJsonObject().get("fuel_item").getAsString());
	}

	// Item stacks only get their components once a server has loaded, so the stack-level check
	// (isFuel) is exercised by the game tests; here the configured id resolves to an item.
	@Test
	public void fuelIsTheConfiguredItem() {
		assertEquals(Optional.of(Items.ENDER_PEARL), VoidAnchorConfig.DEFAULT.fuel());
	}

	@Test
	public void unknownFuelItem() {
		var config = new VoidAnchorConfig(Identifier.fromNamespaceAndPath("nope", "missing"), 0, 6, 0.3, 30, 3);
		assertEquals(Optional.empty(), config.fuel());
		// A second look neither throws nor warns again.
		assertEquals(Optional.empty(), config.fuel());
	}

	@Test
	public void rejectsOutOfRangeValues() {
		var json = VoidAnchorConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, VoidAnchorConfig.DEFAULT).getOrThrow().getAsJsonObject();
		json.addProperty("rift_ticks", 0);
		assertTrue(VoidAnchorConfig.CODEC.codec().parse(JsonOps.INSTANCE, json).isError());
	}

}
