import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import nu.metacraft.nether_rifts.raid.RiftRaid;

public class CaptainTests {

	static void registerTests() {
		NetherRiftsTests.register("captain/has_its_own_bar", ctx -> {
			var inside = ShapeTests.frame(ctx, 10, 10, Blocks.OBSIDIAN);
			ctx.setBlock(inside, Blocks.FIRE);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var player = RaidTests.participant(ctx);
				player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
				rift.tear(ctx.getLevel());
				RiftRaid.startWave(ctx.getLevel(), rift, 7);
				var captain = (LivingEntity) RiftRaid.aliveMobs(ctx.getLevel(), rift).stream()
						.filter(e -> e.entityTags().contains(RiftMobs.CAPTAIN_TAG)).findFirst()
						.orElseThrow(() -> new AssertionError("no tagged Captain in Large wave 7"));
				ctx.runAfterDelay(25, () -> {
					var bar = rift.captainBar();
					ctx.assertTrue(bar != null && bar.getPlayers().contains(player), "the participant can't see the Captain's bar");
					captain.setHealth(captain.getMaxHealth() / 2);
					ctx.runAfterDelay(2, () -> {
						ctx.assertTrue(Math.abs(rift.captainBar().getProgress() - 0.5f) < 0.05f, "the bar shows " + rift.captainBar().getProgress());
						captain.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().playerAttack(player), 1000f);
						ctx.runAfterDelay(2, () -> {
							ctx.assertTrue(rift.captainBar() == null || rift.captainBar().getPlayers().isEmpty(), "the Captain's bar outlived the Captain");
							StageTests.remove(ctx, player);
							ctx.killAllEntities();
							FormingTests.cleanUp(ctx);
							ctx.succeed();
						});
					});
				});
			});
		});
	}
}
