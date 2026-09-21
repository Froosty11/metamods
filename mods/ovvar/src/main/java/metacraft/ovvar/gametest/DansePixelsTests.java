package metacraft.ovvar.gametest;

import de.tomalbrc.danse.util.MinecraftSkinParser;
import de.tomalbrc.danse.util.MinecraftSkinParser.BodyPart;
import metacraft.ovvar.compat.danse.DansePixels;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModComponents;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.content.SpotPlacements;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/**
 * The pixels {@link DansePixels} hands Danse for a sewn ovve, read back face by face.
 *
 * <p>These run without Danse being able to animate anything: the pixels are a pure function of
 * (chapter, half, rolled-down, placements, part, pass), so they can be asserted straight, which is
 * the point of keeping that function pure. The client test
 * ({@code OvvarDanseClientTests}) is what proves the puppet actually wears them.
 *
 * <p>Every test returns at once if Danse is not on the classpath, so an ovvar checkout without the
 * dev jar still runs its game tests.
 */
public final class DansePixelsTests {

	/** ITK's green, the same probe the client screenshot test uses: it cannot come from cerise cloth. */
	private static boolean itkGreen(int rgb) {
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		return g > 100 && g > r + 60 && b < 140;
	}

	/** Data's cerise garment: red-dominant with a strong blue component and little green. */
	private static boolean cerise(int rgb) {
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		return r > 90 && r > g + 40 && b > g;
	}

	@GameTest
	public void frontPatchIsOnTheBodysFrontFaceOnly(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		ItemStack top = topWith(new Placement(Spot.FRONT_TOP_LEFT, Patches.get("itk")));
		CustomModelData body = pixels(top, BodyPart.BODY, false);

		// The body's front face is NORTH in the armour layout (the strip is 16 right | 20 front |
		// 28 left | 32 back, and Danse's map puts NORTH at u 20); SOUTH is the back.
		List<Integer> front = face(body, BodyPart.BODY, Direction.NORTH);
		List<Integer> back = face(body, BodyPart.BODY, Direction.SOUTH);

		if (front.stream().noneMatch(DansePixelsTests::itkGreen)) {
			helper.fail("no ITK green on the body's front face: the sewn patch did not reach Danse's pixels");
		}
		if (back.stream().anyMatch(DansePixelsTests::itkGreen)) {
			helper.fail("ITK green on the body's back face: a front cell leaked round the box");
		}
		if (front.stream().noneMatch(DansePixelsTests::cerise)) {
			helper.fail("no cerise on the body's front face: the chapter's own cloth is missing under the patch");
		}
		helper.succeed();
	}

	@GameTest
	public void aLeftSleevePatchIsOnTheLeftArmAndNotTheRight(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		ItemStack top = topWith(new Placement(Spot.SLEEVE_OUT_TOP_L, Patches.get("itk")));

		// The sleeves' outer face is WEST on the right arm and EAST on the left — the two limbs are
		// the same strip read in opposite directions — so ask the whole part rather than one face.
		if (all(pixels(top, BodyPart.LEFT_ARM, false)).stream().noneMatch(DansePixelsTests::itkGreen)) {
			helper.fail("no ITK green on the left arm: a LEFT cell did not reach the left limb's pixels");
		}
		if (all(pixels(top, BodyPart.RIGHT_ARM, false)).stream().anyMatch(DansePixelsTests::itkGreen)) {
			helper.fail("ITK green on the right arm: a LEFT cell was drawn on both limbs");
		}
		helper.succeed();
	}

	@GameTest
	public void aRightSleevePatchIsOnTheRightArmAndNotTheLeft(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		ItemStack top = topWith(new Placement(Spot.SLEEVE_OUT_TOP_R, Patches.get("itk")));
		if (all(pixels(top, BodyPart.RIGHT_ARM, false)).stream().noneMatch(DansePixelsTests::itkGreen)) {
			helper.fail("no ITK green on the right arm");
		}
		if (all(pixels(top, BodyPart.LEFT_ARM, false)).stream().anyMatch(DansePixelsTests::itkGreen)) {
			helper.fail("ITK green on the left arm: a RIGHT cell was drawn on both limbs");
		}
		helper.succeed();
	}

	/**
	 * The garment is drawn at four texture pixels per skin texel and Danse samples one; the
	 * downsample must not smear a patch outside the cell it was sewn on. ITK on one chest cell may
	 * hang over its neighbours (it is 12×12 art on an 8×8 cell) but must not reach the far half of
	 * the face, and the face must still be mostly cloth.
	 */
	@GameTest
	public void theDownsampleKeepsAPatchNearItsCell(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		ItemStack top = topWith(new Placement(Spot.FRONT_TOP_LEFT, Patches.get("itk")));
		List<Integer> front = face(pixels(top, BodyPart.BODY, false), BodyPart.BODY, Direction.NORTH);
		long green = front.stream().filter(DansePixelsTests::itkGreen).count();
		if (green == 0) helper.fail("no ITK green on the front face at all");
		// The front face is 8×12 = 96 texels; one 12×12-art patch on a 4×4 cell covers a corner of it.
		if (green > front.size() / 2) {
			helper.fail("ITK green on " + green + " of " + front.size()
					+ " front-face texels: the downsample smeared one cell's patch across the body");
		}
		helper.succeed();
	}

	@GameTest
	public void anOvveAnswersItsOwnPassAndNoOther(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		ItemStack ovve = ovveWith(new Placement(Spot.LEG_OUT_TOP_R, Patches.get("itk")));
		// The bottom is a humanoid_leggings layer: it answers the inner pass and nothing else, which
		// is what stops a leg being drawn twice, once on each of Danse's two display layers.
		if (empty(DansePixels.of(ovve, BodyPart.RIGHT_LEG, true))) {
			helper.fail("the ovve drew nothing on the legs' inner pass");
		}
		if (!empty(DansePixels.of(ovve, BodyPart.RIGHT_LEG, false))) {
			helper.fail("the ovve drew on the legs' outer pass too: the boots channel is not ours to draw");
		}
		if (!empty(DansePixels.of(ovve, BodyPart.RIGHT_ARM, true)) || !empty(DansePixels.of(ovve, BodyPart.RIGHT_ARM, false))) {
			helper.fail("the ovve (the bottom half) drew something on an arm");
		}
		helper.succeed();
	}

	// ---- helpers

	private static boolean danse() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("danse");
	}

	private static boolean empty(CustomModelData data) {
		return data == null || data == CustomModelData.EMPTY || data.colors().isEmpty();
	}

	private static ItemStack topWith(Placement... placements) {
		ItemStack top = new ItemStack(ModContent.top(Chapter.DATA));
		top.set(ModComponents.PATCHES, SpotPlacements.fromList(List.of(placements)).getOrThrow());
		return top;
	}

	private static ItemStack ovveWith(Placement... placements) {
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		OvveItem.setTopUp(ovve, true);
		Looks.setSewn(ovve, SpotPlacements.fromList(List.of(placements)).getOrThrow());
		return ovve;
	}

	private static CustomModelData pixels(ItemStack stack, BodyPart part, boolean inner) {
		CustomModelData data = DansePixels.of(stack, part, inner);
		if (empty(data)) throw new AssertionError("no pixels for " + part + " inner=" + inner);
		return data;
	}

	private static List<Integer> all(CustomModelData data) {
		return empty(data) ? List.of() : List.copyOf(data.colors());
	}

	/**
	 * One face out of the flat array, by walking {@link MinecraftSkinParser#DIRECTIONS} in the order
	 * the pixels were written and counting each region's texels out of Danse's own texture map — so
	 * this cannot drift from the layout the pixels were built with.
	 */
	private static List<Integer> face(CustomModelData data, BodyPart part, Direction wanted) {
		var regions = MinecraftSkinParser.NOTCH_TEXTURE_MAP.get(part).get(MinecraftSkinParser.Layer.INNER);
		int at = 0;
		for (Direction direction : MinecraftSkinParser.DIRECTIONS) {
			int[] region = regions.get(direction);
			int size = Math.abs(region[2]) * Math.abs(region[3]);
			if (direction == wanted) return List.copyOf(data.colors()).subList(at, at + size);
			at += size;
		}
		throw new AssertionError("no " + wanted + " on " + part);
	}
}
