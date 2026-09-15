package metacraft.ovvar.content;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * A 4×4 texel cell on the garment, in the 64×32 armour layout — the faces you see, keeping off
 * the collar and the belt (the body's top and bottom texel rows), the hands (the bottom row of
 * the arms), the cuffs (the bottom row of the legs), and the inner faces.
 * Items store cells by name, so entries may be added or removed; the ordinal only travels in the
 * (transient) preview bits. Left/right in sleeve and leg
 * names are the wearer's own; in chest and back names they are as seen by someone facing that
 * side. Sleeve and leg cells sit on the right limb's strips: the armour model draws the left limb
 * as a mirror image off the same strips, and the shader tells the two apart by the handedness
 * of the texture mapping, so a LEFT cell is the same rectangle drawn only on mirrored fragments
 * (with its art flipped back).
 *
 * Box strips (rows 20–32): body 16 right | 20 front | 28 left | 32 back; arm 40 outer | 44 front
 * | 48 inner | 52 back; leg 0 outer | 4 front | 8 inner | 12 back.
 */
public enum Spot implements StringRepresentable {
	// top: chest and back, 2 columns × 2 rows each. The rows sit at v 21 and 26: the body's top
	// texel row is the collar and its bottom row the belt. No cells on the body's sides, the inner
	// arms or the inner legs: hidden most of the time, and their bits buy more instant designs.
	FRONT_TOP_LEFT(Piece.TOP, 20, 21), FRONT_TOP_RIGHT(Piece.TOP, 24, 21),
	FRONT_LOW_LEFT(Piece.TOP, 20, 26), FRONT_LOW_RIGHT(Piece.TOP, 24, 26),
	BACK_TOP_LEFT(Piece.TOP, 32, 21), BACK_TOP_RIGHT(Piece.TOP, 36, 21),
	BACK_LOW_LEFT(Piece.TOP, 32, 26), BACK_LOW_RIGHT(Piece.TOP, 36, 26),
	// top: sleeves, outer, front and back faces, top and middle rows per arm. The rows sit at v 21
	// and 25, a texel below the shoulder (the playtest wanted them lower on the arm); the bottom
	// row of the arm, v 31, is the hand.
	SLEEVE_OUT_TOP_R(Piece.TOP, 40, 21, Side.RIGHT), SLEEVE_OUT_MID_R(Piece.TOP, 40, 25, Side.RIGHT),
	SLEEVE_OUT_TOP_L(Piece.TOP, 40, 21, Side.LEFT), SLEEVE_OUT_MID_L(Piece.TOP, 40, 25, Side.LEFT),
	SLEEVE_FRONT_TOP_R(Piece.TOP, 44, 21, Side.RIGHT), SLEEVE_FRONT_MID_R(Piece.TOP, 44, 25, Side.RIGHT),
	SLEEVE_FRONT_TOP_L(Piece.TOP, 44, 21, Side.LEFT), SLEEVE_FRONT_MID_L(Piece.TOP, 44, 25, Side.LEFT),
	SLEEVE_BACK_TOP_R(Piece.TOP, 52, 21, Side.RIGHT), SLEEVE_BACK_MID_R(Piece.TOP, 52, 25, Side.RIGHT),
	SLEEVE_BACK_TOP_L(Piece.TOP, 52, 21, Side.LEFT), SLEEVE_BACK_MID_L(Piece.TOP, 52, 25, Side.LEFT),
	// bottom: legs, outer, front and back faces, top and middle rows per leg. The rows sit at v 22
	// and 26, two texels below the waist (the playtest wanted them lower still, a patch on the thigh
	// rather than on the hip); the cuff row under a boot stays clear, 26 + 4 = 30 < 31.
	LEG_OUT_TOP_R(Piece.BOTTOM, 0, 22, Side.RIGHT), LEG_OUT_MID_R(Piece.BOTTOM, 0, 26, Side.RIGHT),
	LEG_OUT_TOP_L(Piece.BOTTOM, 0, 22, Side.LEFT), LEG_OUT_MID_L(Piece.BOTTOM, 0, 26, Side.LEFT),
	LEG_FRONT_TOP_R(Piece.BOTTOM, 4, 22, Side.RIGHT), LEG_FRONT_MID_R(Piece.BOTTOM, 4, 26, Side.RIGHT),
	LEG_FRONT_TOP_L(Piece.BOTTOM, 4, 22, Side.LEFT), LEG_FRONT_MID_L(Piece.BOTTOM, 4, 26, Side.LEFT),
	LEG_BACK_TOP_R(Piece.BOTTOM, 12, 22, Side.RIGHT), LEG_BACK_MID_R(Piece.BOTTOM, 12, 26, Side.RIGHT),
	LEG_BACK_TOP_L(Piece.BOTTOM, 12, 22, Side.LEFT), LEG_BACK_MID_L(Piece.BOTTOM, 12, 26, Side.LEFT),
	/** The seat: one 8×4 patch across the back of both legs (LEG_BACK_TOP_R + LEG_BACK_TOP_L), so it follows their row. */
	SEAT(Piece.BOTTOM, 12, 22, Side.SEAT);

	public static final Codec<Spot> CODEC = StringRepresentable.fromEnum(Spot::values);

	@Override
	public @NonNull String getSerializedName() {
		return id();
	}

	/** BODY = an unmirrored face; RIGHT/LEFT = the limb the cell is drawn on; SEAT = both legs. Ordinal is what the shader reads. */
	public enum Side { BODY, RIGHT, LEFT, SEAT }

	/** A cell's side in skin texels (the coordinates here). */
	public static final int SIZE = 4;
	/**
	 * The box <em>side</em> rows of the armour layout, which is where every cell is: skin rows
	 * {@value #FACE_ROW} to {@value #FACE_ROW} + {@value #FACE_ROWS}. Above them are the boxes' top
	 * and bottom faces. Anything that measures a cell down a face — datagen's clip, the stand
	 * sprites, the aim, the paper doll — reads these rather than writing 20 and 32 again, so moving
	 * a row of cells is one edit in the enum above.
	 */
	public static final int FACE_ROW = 20, FACE_ROWS = 12;
	/** Texels per skin texel in the garment and patch textures (128×64): patch art is {@link #PX} square. */
	public static final int DETAIL = 2;
	public static final int PX = SIZE * DETAIL;

	/** The armour model's inflation for a piece's layer: 1 for the chest layer, 0.5 for the leggings layer. */
	public static double inflate(Piece piece) {
		return piece == Piece.TOP ? 1.0 : 0.5;
	}

	/** The pixel every layer on a part is drawn at, in model units (ovvar_pixel in ovvar.glsl). */
	public static double pixel(int skinX, double inflate) {
		return skinX < 16 ? 13.0 / 12 : (12 + 2 * inflate) / 12;
	}

	/**
	 * The face of its strip a cell is on, 0..3: a limb's outer, front, inner, back; the body's
	 * right side, front, left side, back.
	 */
	public static int face(Spot spot) {
		int local = spot.u - stripStart(spot), n1 = stripWidth(spot) == 24 ? 8 : 4;
		return local < 4 ? 0 : local < 4 + n1 ? 1 : local < 8 + n1 ? 2 : 3;
	}

	/**
	 *      * ovvar_anchored in ovvar.glsl, the same arithmetic: a placement is drawn continuous round
	 * the box from the face it is on — that face's texels centred on it, the neighbours'
	 * continuing past its edges at {@link #pixel} units each — with the slack of the inflated
	 * box in the middle of the opposite face. For a side-row texel {@code skinX} (fractional,
	 * skin texels): the strip-local texel column shown there, or -1 in the slack. Datagen bakes
	 * the trim textures with it, since vanilla draws those.
	 */
	public static double anchored(double skinX, double inflate, int anchor) {
		double p = pixel((int) Math.floor(skinX), inflate), e = 2 * inflate;
		boolean body = skinX >= 16 && skinX < 40;
		int stripStart = skinX < 16 ? 0 : body ? 16 : 40;
		double local = skinX - stripStart;
		int n1 = body ? 8 : 4, total = 8 + 2 * n1;
		double perimeter = total + 4 * e;
		int k = local < 4 ? 0 : local < 4 + n1 ? 1 : local < 8 + n1 ? 2 : 3;
		double[] s = {0, 4, 4 + n1, 8 + n1}, n = {4, n1, 4, n1}, U = {0, 4 + e, 4 + n1 + 2 * e, 8 + n1 + 3 * e};
		double u = U[k] + (local - s[k]) * ((n[k] + e) / n[k]);
		double c = U[anchor] + (n[anchor] + e) / 2, t = s[anchor] + n[anchor] / 2.0;
		double du = u - c;
		if (du >= perimeter / 2) du -= perimeter; else if (du < -perimeter / 2) du += perimeter;
		double dt = du / p;
		if (Math.abs(dt) > total / 2.0) return -1;
		double w = t + dt;
		return w - Math.floor(w / total) * total;
	}


	/** Start of the strip (skin texels) a cell's part draws: legs 0, body 16, arms 40. */
	public static int stripStart(Spot spot) {
		return spot.u < 16 ? 0 : spot.u < 40 ? 16 : 40;
	}

	public static int stripWidth(Spot spot) {
		return spot.u >= 16 && spot.u < 40 ? 24 : 16;
	}
	/** How far up the mirror strip sits from the limb boxes. */
	public static final int MIRROR_SHIFT = 16;

	public final Piece piece;
	public final Side side;
	/** Cell origin in the standard layout (the right limb's strip for limb cells). */
	public final int u, v;

	Spot(Piece piece, int u, int v) {
		this(piece, u, v, Side.BODY);
	}

	Spot(Piece piece, int u, int v, Side side) {
		this.piece = piece;
		this.u = u;
		this.v = v;
		this.side = side;
	}

	/**
	 * Which half of a seat patch's art a leg wears, as a column across the art: 0 the art's left
	 * half, 1 its right half.
	 *
	 * <p><b>Which half.</b> Seat art is drawn as seen from behind — the only way anybody sees a
	 * seat. Standing behind the wearer you face the way they face, so their left leg is the one on
	 * <em>your</em> left: the art's left half belongs on the wearer's LEFT leg and its right half on
	 * their right.
	 *
	 * <p><b>Which leg is drawn mirrored.</b> The armour model draws the wearer's LEFT limbs as
	 * mirror images off the right limb's strips, so each of the three paths has to say two things —
	 * which half of the art a leg takes, and whether that half must be flipped in x to come out the
	 * way it was drawn:
	 * <ul>
	 *   <li><b>The pack path</b> (datagen, {@code patch/seat/<id>_r.png} and {@code _l.png}, what
	 *	   shows once {@link metacraft.ovvar.pack.Combos} has the combination): {@code _r} carries
	 *	   the art's right half as drawn, because the right leg is not mirrored; {@code _l} carries
	 *	   the art's left half <em>flipped in x</em>, because the model flips the left leg's texture
	 *	   back when it draws it.
	 *   <li><b>The instant path</b> (the dye bits and the shader's library, {@code ovvar.glsl}): the
	 *	   shader tells a left-leg fragment by the handedness of its texture mapping
	 *	   ({@code mirrored}). A mirrored fragment reads column {@code seatColumn(LEFT)} of the
	 *	   library art and mirrors {@code local.x} back; an unmirrored one reads column
	 *	   {@code seatColumn(RIGHT)} as it is. {@code OVVAR_SEAT_COLUMN_RIGHT} in the shader is this
	 *	   method's RIGHT value, and a game test holds the two to each other.
	 *   <li><b>The wardrobe preview</b> ({@code WardrobePreview.shownArt}): the doll lays the two
	 *	   legs out in the order the back view puts them and gives each the slice of the art at its
	 *	   own place across the figure — the same statement again, arrived at without reading this.
	 * </ul>
	 */
	public static int seatColumn(Side side) {
		return side == Side.LEFT ? 0 : 1;
	}

	/** Where a seat patch's art is cut for one leg: the x its half starts at, in art pixels. */
	public static int seatHalf(Side side) {
		return seatColumn(side) * PX;
	}

	/** The cells a seat patch covers, which a seat patch and a plain patch fight over. */
	public static final java.util.List<Spot> SEAT_CELLS = java.util.List.of(LEG_BACK_TOP_R, LEG_BACK_TOP_L);

	/** The cells of one half in a fixed order; a cell's index here is what the instant channel carries. */
	public static java.util.List<Spot> cells(Piece piece) {
		return java.util.Arrays.stream(values()).filter(s -> s.piece == piece).toList();
	}

	/** The cell nearest a strip position in its column (u, side), or null if the column has none. */
	public static Spot nearest(Piece piece, int u, double v, Side side) {
		Spot best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Spot s : values()) {
			if (s.piece != piece || s.u != u || s.side != side) continue;
			double d = v < s.v ? s.v - v : v >= s.v + SIZE ? v - (s.v + SIZE) + 1 : 0;
			if (d < bestDistance) { bestDistance = d; best = s; }
		}
		return best;
	}

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Cells that overlap this one (a seat patch covers two leg cells). */
	public java.util.List<Spot> overlapping() {
		if (this == SEAT) return SEAT_CELLS;
		return SEAT_CELLS.contains(this) ? java.util.List.of(SEAT) : java.util.List.of();
	}

	/** "chest, top left" / "left sleeve, outer top" — for tooltips. */
	public String label() {
		if (this == SEAT) return "seat";
		String n = name().toLowerCase(java.util.Locale.ROOT);
		String limb = side == Side.LEFT ? "left " : side == Side.RIGHT ? "right " : "";
		if (n.startsWith("front_")) return "chest, " + n.substring(6).replace('_', ' ');
		if (n.startsWith("back_")) return "back, " + n.substring(5).replace('_', ' ');
		String rest = n.replaceAll("_[lr]$", "");
		if (rest.startsWith("side_")) return (n.endsWith("_r") ? "right" : "left") + " side, " + rest.substring(5);
		for (String face : new String[]{"out", "front", "in", "back"}) {
			String word = face.equals("out") ? "outer" : face.equals("in") ? "inner" : face;
			if (rest.startsWith("sleeve_" + face + "_")) return limb + "sleeve, " + word + " " + rest.substring(8 + face.length());
			if (rest.startsWith("leg_" + face + "_")) return limb + "leg, " + word + " " + rest.substring(5 + face.length());
		}
		return n.replace('_', ' ');
	}
}
