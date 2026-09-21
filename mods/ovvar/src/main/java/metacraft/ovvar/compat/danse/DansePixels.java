package metacraft.ovvar.compat.danse;

import com.google.common.collect.ImmutableList;
import de.tomalbrc.danse.util.MinecraftSkinParser;
import de.tomalbrc.danse.util.MinecraftSkinParser.BodyPart;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModComponents;
import metacraft.ovvar.content.OvveFeetItem;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.OvveTopItem;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.content.SpotPlacements;
import metacraft.ovvar.datagen.Tex;
import metacraft.ovvar.pack.EquipmentJson;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.jspecify.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The armour pixels Danse asks for, for an ovve.
 *
 * <p>Danse animates a gesture as a puppet of item displays, one display per body part and one
 * <em>pixel</em> per skin texel, the colours carried in {@code custom_model_data}. It draws armour
 * itself: {@code TextureCache.armorCustomModelData} reads the server-side stack's equipment asset,
 * opens the equipment JSON and its layer PNGs out of the Polymer pack builder, and samples them as
 * 64×32 armour textures.
 *
 * <p>None of that can work for an ovve. The raw server stack carries only the chapter's base asset:
 * the per-combination asset, the dye colour that ranks the newest patches, the trim that carries one
 * more, the companion top's look and the virtual cuffs are all added on the way out, in
 * {@code getPolymerItemStack}, and Danse never sees them. And ovvar's layer textures are
 * {@value #W}×{@value #H} — {@link Spot#DETAIL} texture pixels per skin texel — so sampling them on
 * a 64×32 grid would read a quarter of the top-left corner of the garment.
 *
 * <p>So we composite the pixels ourselves, from the same layer list the equipment JSON is built
 * from ({@link EquipmentJson#layerTextures}, so the puppet and a real client can never disagree
 * about where a patch goes or what order the patches stack in), and hand Danse the finished array.
 *
 * <h2>Left limbs</h2>
 * The armour model draws the left limb as a mirror image of the right limb's strips, and Danse
 * copies that exactly: {@code armorCustomModelData} always samples through
 * {@link MinecraftSkinParser#NOTCH_TEXTURE_MAP}, whose LEFT_ARM and LEFT_LEG entries are the
 * <em>right</em> limb's rectangles with a negative width — each 4-texel face read backwards. So
 * everything ovvar already pre-mirrors for that model comes out straight through Danse for free: a
 * {@code _l} placement texture sits on the right limb's rectangle with its art flipped, and reading
 * it backwards un-flips it.
 *
 * <p>The one thing that does not come for free is the <b>base cloth</b>. On a real client the
 * asymmetric left-side art is reached by the shader, which samples the limb boxes "one strip up"
 * (skin rows 0–16, {@link #MIRROR_SHIFT} above the limb's own rows) where datagen keeps the
 * per-face-mirrored left art. Danse has no shader, so here that strip is copied down onto the
 * limb's rectangle before the placements go on. That is the whole of the left-limb handling — no
 * layout arithmetic is restated, the rectangles below are datagen's own boxes.
 *
 * <p>Not drawn, deliberately: the dyeable preview layer and the trim. Both exist only to smuggle
 * patches past a vanilla renderer that can be told nothing else per item, and here we have the
 * whole placement list to hand.
 */
public final class DansePixels {
	private DansePixels() {}

	/** The layer textures' own size: the 64×32 armour layout at {@link Spot#DETAIL} pixels per texel. */
	private static final int D = Spot.DETAIL, W = 64 * D, H = 32 * D;
	/** Skin rows between a limb's own box and the mirror strip holding its left-side art. */
	private static final int MIRROR_SHIFT = Spot.MIRROR_SHIFT;

	/** Danse's own threshold for "this pixel is drawn" ({@code MinecraftSkinParser.ColorData#alpha}). */
	private static final int OPAQUE = 0xF0;

	/** The boxes of the armour layout, in skin texels: {u, v, w, h}. Datagen's own numbers. */
	private static final int[] BODY_BOX = {16, 16, 24, 16};
	private static final int[] ARM_BOX = {40, 16, 16, 16};
	private static final int[] LEG_BOX = {0, 16, 16, 16};

	private static final Map<Key, CustomModelData> CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Tex> TEXTURES = new ConcurrentHashMap<>();

	/**
	 * The pixels for one of Danse's parts, or null when this stack is not ours and Danse should
	 * carry on as it was. {@link CustomModelData#EMPTY} is a real answer: "ours, and nothing of it
	 * belongs on this part".
	 */
	public static @Nullable CustomModelData of(ItemStack stack, BodyPart part, boolean inner) {
		Garment garment = garmentOf(stack);
		if (garment == null) return null;
		if (!answers(garment.piece(), part, inner)) return CustomModelData.EMPTY;
		return CACHE.computeIfAbsent(garment.key(part, inner), DansePixels::build);
	}

	/** Is this stack an ovve, a companion top, or the virtual cuffs? */
	public static boolean ours(ItemStack stack) {
		return stack.getItem() instanceof OvveItem
				|| stack.getItem() instanceof OvveTopItem
				|| stack.getItem() instanceof OvveFeetItem;
	}

	/**
	 * The ovve top worn <em>under</em> a real chestplate, for the part Danse is drawing — or null if
	 * this chestplate is not over an ovve, or the wearer cannot be found, or nothing of ours belongs
	 * on this part, in all of which cases Danse's own chestplate pixels stand unchanged.
	 *
	 * <p>A chestplate over an ovve is marked with its wearer ({@link ModComponents#WRAPPED_TOP}) by
	 * {@link metacraft.ovvar.content.OvveTop}, which is how the Polymer side finds the composite
	 * asset to send a client. Here the mark does the same job: the sleeves and collar of the ovve
	 * show through the armour's open arms and neck, so the top is drawn beneath the chestplate.
	 */
	public static @Nullable CustomModelData underChestplate(ItemStack chestplate, BodyPart part, boolean inner) {
		UUID wearerId = chestplate.get(ModComponents.WRAPPED_TOP);
		if (wearerId == null || !answers(Piece.TOP, part, inner)) return null;
		MinecraftServer server = DanseCompat.server();
		if (server == null) return null;
		LivingEntity wearer = findWearer(server, wearerId);
		if (wearer == null) return null;
		ItemStack legs = wearer.getItemBySlot(EquipmentSlot.LEGS);
		if (!(legs.getItem() instanceof OvveItem ovve) || !OvveItem.topUp(legs)) return null;
		Garment garment = new Garment(ovve.chapter, Piece.TOP, false, placements(legs, Piece.TOP));
		return CACHE.computeIfAbsent(garment.key(part, inner), DansePixels::build);
	}

	private static @Nullable LivingEntity findWearer(MinecraftServer server, UUID id) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player != null) return player;
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof LivingEntity living) return living;
		}
		return null;
	}

	/**
	 * Ours drawn under theirs: a pixel of ours shows wherever Danse's is not drawn. Danse's own
	 * {@code merged} does the same thing the other way up and is private, so this is the ten lines
	 * rather than a reach into its internals.
	 */
	public static CustomModelData under(CustomModelData ours, CustomModelData theirs) {
		if (ours == CustomModelData.EMPTY || ours.colors().isEmpty()) return theirs;
		if (theirs == CustomModelData.EMPTY || theirs.colors().isEmpty()) return ours;
		List<Integer> colors = new IntArrayList(theirs.colors());
		List<Boolean> flags = new BooleanArrayList(theirs.flags());
		int n = Math.min(colors.size(), ours.colors().size());
		for (int i = 0; i < n; i++) {
			if (Boolean.TRUE.equals(theirs.getBoolean(i))) continue;   // the chestplate covers it
			Boolean mine = ours.getBoolean(i);
			if (!Boolean.TRUE.equals(mine)) continue;
			colors.set(i, ours.getColor(i));
			flags.set(i, true);
		}
		return new CustomModelData(ImmutableList.of(), flags, ImmutableList.of(), colors);
	}

	// ---- which stack draws what

	/** What a stack of ours is, as the pixels need it. Null for the cuffs: the boots channel draws nothing. */
	private static @Nullable Garment garmentOf(ItemStack stack) {
		if (stack.getItem() instanceof OvveItem ovve) {
			// The ovve is the bottom: its top being rolled down is a different garment texture.
			return new Garment(ovve.chapter, Piece.BOTTOM, !OvveItem.topUp(stack), placements(stack, Piece.BOTTOM));
		}
		if (stack.getItem() instanceof OvveTopItem top) {
			return new Garment(top.chapter, Piece.TOP, false, placements(stack, Piece.TOP));
		}
		return null;
	}

	private static List<Placement> placements(ItemStack stack, Piece piece) {
		return SpotPlacements.asPlacementList(Looks.sewn(stack, piece));
	}

	/**
	 * Danse's rule, kept: for BODY the {@code inner} pass is the legs item and the outer is the
	 * chest item; for a leg, inner is the legs item and outer is the feet item; for an arm both are
	 * the chest item. Ovvar's textures are per half, and a half lives in exactly one of Danse's two
	 * passes — the top is a {@code humanoid} layer (outer), the bottom a {@code humanoid_leggings}
	 * layer (inner) — which is also what stops a sleeve being drawn twice, once on each pass.
	 */
	private static boolean answers(Piece piece, BodyPart part, boolean inner) {
		if (inner != (piece == Piece.BOTTOM)) return false;
		return switch (piece) {
			case TOP -> part == BodyPart.BODY || part.isArm();
			case BOTTOM -> part == BodyPart.BODY || part.isLeg();
		};
	}

	// ---- building the pixels

	private static CustomModelData build(Key key) {
		Tex canvas = compose(key);
		BufferedImage image = toImage(downsample(canvas));

		List<Integer> colors = new IntArrayList();
		List<Boolean> flags = new BooleanArrayList();
		for (Direction direction : MinecraftSkinParser.DIRECTIONS) {
			MinecraftSkinParser.extractTextureRGB(
					image, MinecraftSkinParser.NOTCH_TEXTURE_MAP, key.part(), MinecraftSkinParser.Layer.INNER, direction,
					data -> {
						colors.add(data.color());
						flags.add(data.alpha());
					});
		}
		if (colors.isEmpty()) return CustomModelData.EMPTY;
		return new CustomModelData(ImmutableList.of(), flags, ImmutableList.of(), colors);
	}

	/** The garment's layers for this part, stacked the way the equipment definition stacks them. */
	private static Tex compose(Key key) {
		BodyPart part = classic(key.part());
		Spot.Side side = sideOf(part);

		Tex out = read(key.piece(), EquipmentJson.baseTexture(key.chapter(), key.piece(), key.nercabbad()));
		if (isLeft(part)) {
			// The asymmetric left-side art the shader would reach one strip up, brought down onto the
			// limb's own rectangle — still per-face mirrored, which is exactly what Danse's mirrored
			// read of it expects.
			int[] box = boxOf(part);
			out = out.blit(out, box[0], box[1] - MIRROR_SHIFT, box[2], box[3], box[0], box[1]);
		}

		for (Placement placement : Spot.stacked(onThisPart(key.placements(), side))) {
			out = out.composite(placementTexture(key.piece(), placement, side));
		}
		return out;
	}

	/**
	 * The placements this part draws. A limb draws only the cells marked for it — the two limbs
	 * share one rectangle in the layout, so a right sleeve's texture would otherwise land on the
	 * left arm as well — and the seat, which is one patch cut across both legs, draws its own half.
	 */
	private static List<Placement> onThisPart(List<Placement> placements, Spot.Side side) {
		List<Placement> out = new ArrayList<>();
		for (Placement placement : placements) {
			Spot.Side cell = placement.spot().side;
			if (cell == side || (cell == Spot.Side.SEAT && (side == Spot.Side.LEFT || side == Spot.Side.RIGHT))) {
				out.add(placement);
			}
		}
		return out;
	}

	/** A placement's layer texture; the seat has one per leg, as {@link EquipmentJson#textures} lists them. */
	private static Tex placementTexture(Piece piece, Placement placement, Spot.Side side) {
		List<String> textures = EquipmentJson.textures(placement);
		String texture = textures.size() == 1 ? textures.getFirst() : textures.get(side == Spot.Side.RIGHT ? 0 : 1);
		return read(piece, Ovvar.MOD_ID + ":" + texture);
	}

	/**
	 * Back to the 64×32 grid Danse samples on: each {@link Spot#DETAIL}-square block becomes one
	 * texel, the colour averaged weighted by alpha so a block that is half patch and half nothing
	 * comes out the patch's colour rather than a half-faded version of it, and the block counts as
	 * drawn if any texel in it was.
	 */
	private static int[] downsample(Tex tex) {
		int[] out = new int[64 * 32];
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 64; x++) {
				long r = 0, g = 0, b = 0, weight = 0;
				boolean visible = false;
				for (int dy = 0; dy < D; dy++) {
					for (int dx = 0; dx < D; dx++) {
						int argb = tex.get(x * D + dx, y * D + dy);
						int a = (argb >>> 24) & 0xFF;
						if (a >= OPAQUE) visible = true;
						if (a == 0) continue;
						r += (long) ((argb >> 16) & 0xFF) * a;
						g += (long) ((argb >> 8) & 0xFF) * a;
						b += (long) (argb & 0xFF) * a;
						weight += a;
					}
				}
				if (!visible || weight == 0) continue;   // left transparent: Danse reads alpha < 0xF0 as "not drawn"
				out[y * 64 + x] = 0xFF000000
						| (int) (r / weight) << 16
						| (int) (g / weight) << 8
						| (int) (b / weight);
			}
		}
		return out;
	}

	private static BufferedImage toImage(int[] argb) {
		BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, 64, 32, argb, 0, 64);
		return image;
	}

	// ---- parts

	/** Danse asks about slim arms by their own parts; they are the same boxes of the armour layout. */
	private static BodyPart classic(BodyPart part) {
		return switch (part) {
			case LEFT_ARM_SLIM -> BodyPart.LEFT_ARM;
			case RIGHT_ARM_SLIM -> BodyPart.RIGHT_ARM;
			default -> part;
		};
	}

	private static boolean isLeft(BodyPart part) {
		return part == BodyPart.LEFT_ARM || part == BodyPart.LEFT_LEG;
	}

	private static Spot.Side sideOf(BodyPart part) {
		if (part == BodyPart.LEFT_ARM || part == BodyPart.LEFT_LEG) return Spot.Side.LEFT;
		if (part == BodyPart.RIGHT_ARM || part == BodyPart.RIGHT_LEG) return Spot.Side.RIGHT;
		return Spot.Side.BODY;
	}

	private static int[] boxOf(BodyPart part) {
		if (part.isArm()) return ARM_BOX;
		if (part.isLeg()) return LEG_BOX;
		return BODY_BOX;
	}

	// ---- reading the generated textures

	/**
	 * A generated layer texture off our own classpath. Deliberately <em>not</em> through Danse's
	 * pack builder: these are ovvar's own resources and are there whether or not a pack has been
	 * built, and the pack's copy of them is the same file.
	 */
	private static Tex read(Piece piece, String texture) {
		String path = "assets/" + Ovvar.MOD_ID + "/textures/entity/equipment/" + piece.layer + "/"
				+ texture.substring(texture.indexOf(':') + 1) + ".png";
		return TEXTURES.computeIfAbsent(path, p -> {
			try (InputStream in = DansePixels.class.getResourceAsStream("/" + p)) {
				if (in == null) throw new IOException("missing " + p + " — run ./gradlew runDatagen");
				Tex tex = Tex.read(in);
				if (tex.width != W || tex.height != H) {
					throw new IOException(p + " is " + tex.width + "×" + tex.height + ", expected " + W + "×" + H);
				}
				return tex;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	/** Only for the tests and {@code /ovvar reload}: the pixels are pure, but the arrays are big. */
	public static void clearCache() {
		CACHE.clear();
		TEXTURES.clear();
	}

	// ---- keys

	/** What a stack means to us, before a part is chosen. */
	private record Garment(Chapter chapter, Piece piece, boolean nercabbad, List<Placement> placements) {
		Key key(BodyPart part, boolean inner) {
			return new Key(chapter, piece, nercabbad, List.copyOf(placements), part, inner);
		}
	}

	/** The placements are the key, not the stack: two ovves sewn alike share their pixels. */
	private record Key(Chapter chapter, Piece piece, boolean nercabbad, List<Placement> placements,
					   BodyPart part, boolean inner) {}
}
