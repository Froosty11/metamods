package metacraft.ovvar.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The patch catalogue: the art that can be sewn on. A plain patch goes on any cell ({@link Spot})
 * and is normally the cell's size ({@link Spot#PX} square); a bigger one is centred on its cell
 * and hangs over the neighbours — later-sewn on top, the way real ovvar are patched. A seat patch
 * is two cells wide and goes across the seat only, where it may be taller than the cells
 * ({@link #SEAT_HEIGHT_MAX}) and hang onto the cloth below and above them. Adding a patch is one
 * line here plus its PNG at
 * {@code art/ovvar/patches/<id>.png}, then {@code runDatagen}. The first
 * {@value Looks#INSTANT_DESIGNS} cell-sized ones can ride in the dye colour (sewn ones show at once);
 * bigger ones and later ones always go through the pack. Items store patches by id, so the order
 * is otherwise free.
 *
 * <p><b>Per-size art.</b> A patch may ship more than one PNG: the default above, plus any number of
 * {@code <id>_<w>x<h>.png} beside it — art drawn again at a size that suits a particular place,
 * which is cheaper than any amount of scaling or clipping. They are found by file name (nothing to
 * declare: the catalogue entry stays one line), and every path that draws a patch asks
 * {@link #artFor} which of them a place shows, so the item icon, the pack's placement textures, the
 * instant channel's library, the paper doll, the preview glyphs and a stand's sprites can never
 * pick different ones. {@link Fit} is the whole of the decision.
 */
public final class Patches {
	private Patches() {}

	/**
	 * The most a patch's art may be: two cells each way, which is exactly {@link Spot#BACK_BIG}'s
	 * own size — on any other cell art this big hangs over its neighbours. Even sizes only, since
	 * the art is centred in its cell.
	 */
	public static final int MAX_ART = Spot.BIG * Spot.DETAIL;
	/**
	 * The tallest a seat patch may be: a texel of overhang above and below the seat's own row, which
	 * keeps it clear of the waistband above and of the cuff under a boot below. A seat patch is
	 * always the two cells' full width — half of it goes on each leg, so there is nowhere for a
	 * narrower one to be — but it may be this tall, centred on the cells the way oversize plain art
	 * is centred on its own cell.
	 */
	public static final int SEAT_HEIGHT_MAX = Spot.PX + 2 * Spot.DETAIL;

	/** A patch's inventory icon, and the size of the art that fills it without being scaled. */
	public static final int ICON = 16;

	/**
	 * One PNG of a patch's art: the default ({@code art/ovvar/patches/<id>.png}, the size the
	 * catalogue entry declares) or one of the per-size variants beside it
	 * ({@code art/ovvar/patches/<id>_<w>x<h>.png}). Everything that used to be measured off the
	 * patch — where the art's top-left lands on a cell, whether it hangs over — is measured off
	 * this, because which of a patch's PNGs is being drawn is decided per place ({@link #artFor}).
	 *
	 * @param byDefault is this the catalogue's own {@code <id>.png}?
	 */
	public record Art(String id, int width, int height, boolean byDefault) {
		/** The name to load it by, {@code patches/<id>} or {@code patches/<id>_<w>x<h>}, without the extension. */
		public String file() {
			return byDefault ? "patches/" + id : "patches/" + id + "_" + width + "x" + height;
		}

		/** The classpath resource, which is what a variant is discovered by. */
		public String resource() {
			return "/art/" + metacraft.ovvar.Ovvar.MOD_ID + "/" + file() + ".png";
		}

		/** Art width in cells, rounded up: what the preview library allocates a block of. */
		public int cells() {
			return (width + Spot.PX - 1) / Spot.PX;
		}

		/**
		 * Where the art's top-left lands relative to the cell's, in texture pixels: centred in the
		 * cell, which is not one size — {@link Spot#BACK_BIG} is two cells each way and the seat two
		 * cells wide — so this is asked of the cell the art is going on.
		 */
		public int offsetX(Spot spot) {
			return (spot.px() - width) / 2;
		}

		public int offsetY(Spot spot) {
			return (spot.pxHeight() - height) / 2;
		}

		/** Does the art hang over the cell it is on? */
		public boolean oversize(Spot spot) {
			return width > spot.px() || height > spot.pxHeight();
		}

		/** Does the art sit inside a {@code w}×{@code h} box whole, with nothing cut off? */
		public boolean fitsIn(int w, int h) {
			return width <= w && height <= h;
		}

		@Override
		public String toString() {
			return file() + " (" + width + "x" + height + ")";
		}
	}

	/**
	 * What a place asks of a patch's art — the whole of the decision, and no more than the three
	 * paths that draw a patch can tell apart (the instant channel carries a design, not a cell's
	 * choice, so its library has to hold one entry per fit):
	 *
	 * <ul>
	 *   <li>{@link #OVER}: the artist's own size, hanging over the cell if it is bigger. Every
	 *	   ordinary cell — a patch lapping onto its neighbours is the point of them — and the seat,
	 *	   whose art is drawn to the seat's own size rules already.
	 *   <li>{@link #CLIPPED}: a cell the art is cut to, which is a box's <em>top</em> face (the
	 *	   shoulders): its four edges have no neighbouring face in the layout to continue onto. The
	 *	   art must fit the cell, so a patch with a cell-sized variant lands there whole instead of
	 *	   losing its edges.
	 *   <li>{@link #FILLED}: a cell as big as art is allowed to get ({@link #MAX_ART} square — the
	 *	   big back cell), which is meant to be filled rather than to have a smaller patch floating
	 *	   in the middle of it.
	 * </ul>
	 *
	 * <p>The ordinals are the index the instant channel's library is keyed by, and
	 * {@code OVVAR_FIT_*} in {@code ovvar.glsl} is them (a game test holds the two together).
	 */
	public enum Fit {
		OVER, CLIPPED, FILLED;

		/** What the cell {@code spot} asks of the art drawn on it. */
		public static Fit of(Spot spot) {
			if (spot.top()) return CLIPPED;
			if (spot.side != Spot.Side.SEAT && (spot.px() > Spot.PX || spot.pxHeight() > Spot.PX)) return FILLED;
			return OVER;
		}
	}

	/**
	 * @param id	 also the art file name and the item id suffix ({@code ovvar:patch_<id>})
	 * @param width  art width in pixels ({@link Spot#PX} for a cell-sized patch; a seat patch is always 2 cells wide)
	 * @param height art height in pixels
	 * @param artist who drew the art, credited in the tooltip; null when nobody is named
	 */
	public record Patch(String id, String name, boolean seat, int width, int height, String artist) {
		public Patch {
			if (seat && (width != 2 * Spot.PX || height < Spot.PX || height > SEAT_HEIGHT_MAX)) {
				throw new IllegalArgumentException(id + ": a seat patch is " + 2 * Spot.PX + " px wide and "
						+ Spot.PX + "–" + SEAT_HEIGHT_MAX + " px tall, not " + width + "×" + height);
			}
			if (width < 2 || height < 2 || width > MAX_ART || height > MAX_ART || width % 2 != 0 || height % 2 != 0) {
				throw new IllegalArgumentException(id + ": patch art must be an even size up to " + MAX_ART + "×" + MAX_ART + ", not " + width + "×" + height);
			}
		}

		/** A cell-sized patch. */
		public Patch(String id, String name) {
			this(id, name, false, Spot.PX, Spot.PX);
		}

		/** A patch bigger (or smaller) than its cell, centred on it. */
		public Patch(String id, String name, int width, int height) {
			this(id, name, false, width, height);
		}

		public Patch(String id, String name, boolean seat, int width, int height) {
			this(id, name, seat, width, height, null);
		}

		/** A seat patch exactly the two cells' size. */
		public static Patch seat(String id, String name) {
			return seat(id, name, 2 * Spot.PX, Spot.PX);
		}

		/** A seat patch, which may be taller than the cells: it is centred on them and hangs over. */
		public static Patch seat(String id, String name, int width, int height) {
			return new Patch(id, name, true, width, height);
		}

		/** The same patch, credited to an artist. */
		public Patch by(String artist) {
			return new Patch(id, name, seat, width, height, artist);
		}

		public boolean fits(Spot spot) {
			return seat == (spot == Spot.SEAT);
		}

		/** The catalogue's own PNG, {@code art/ovvar/patches/<id>.png}: the size declared above. */
		public Art art() {
			return new Art(id, width, height, true);
		}

		/**
		 * Every PNG this patch ships, the default among them, largest last. Found by file name at
		 * class load; {@link #artFor} is what picks between them.
		 */
		public List<Art> variants() {
			return Patches.variants(this);
		}
	}


	private static final List<Patch> ALL = List.of(
			new Patch("itk", "ITK", 12, 12).by("Froosty11"),
			new Patch("nyckeln", "Nyckeln'26").by("Kexana"),
			Patch.seat("rivals", "METAcraft Rivals '26").by("Froosty11"),   // across the seat
			new Patch("it", "IT", 12, 12).by("Cactooz"),
			new Patch("data", "Data", 12, 12).by("Froosty11"),
			// Hugo/Cactooz's set, and Mackan's maid dress. New entries go last: a design's instant code
			// is its position here, so inserting one in the middle would repaint everything already sewn.
			new Patch("spiken", "Spiken", 12, 12).by("Cactooz"),
			new Patch("slaggan", "Släggan", 12, 12).by("Cactooz"),   // the file is slaggan.png: a resource id is [a-z0-9_.-]
			new Patch("ticket_to_my_heart", "Ticket to my heart", 10, 6).by("Cactooz"),   // drawn 9×6, padded to an even width
			new Patch("maid", "Maid dress", 12, 12).by("Mackan"),
			// Taller than the seat's own row: a texel of the rails hangs onto the cloth below it.
			Patch.seat("pung", "Pung", 16, 10)
	);

	private static final Map<String, Patch> BY_ID = ALL.stream()
			.collect(Collectors.toMap(Patch::id, p -> p, (a, b) -> { throw new IllegalStateException("duplicate patch id " + a.id()); }, LinkedHashMap::new));

	public static final Codec<Patch> ID_CODEC = Codec.STRING.comapFlatMap(
			id -> {
				var patch = BY_ID.get(id);
				if (patch != null) {
					return DataResult.success(patch);
				} else {
					return DataResult.error(() -> "unknown patch '" + id + "'");
				}
			},
			Patch::id
	);

	public static List<Patch> all() {
		return ALL;
	}

	/** index + 1, what the preview bits carry. */
	public static int code(Patch patch) {
		return ALL.indexOf(patch) + 1;
	}

	/** Never null: an id that is not in the catalogue is a bug (a removed entry, a typo in a command), not a state. */
	public static Patch get(String id) {
		return ID_CODEC.parse(JavaOps.INSTANCE, id).getOrThrow(IllegalArgumentException::new);
	}

	public static boolean exists(String id) {
		return BY_ID.containsKey(id);
	}

	// ---- per-size art

	/**
	 * Every patch's PNGs, found by file name once, at class load: the default, plus every
	 * {@code <id>_<w>x<h>.png} on the classpath beside it. Probing the names rather than listing
	 * the directory is what lets the same answer come out on a server, in datagen and in a jar —
	 * the size <em>is</em> the name, and there are only {@link #MAX_ART}/2 squared of them to ask
	 * about. Datagen is where a file whose name no patch could ever ask for is caught (it lists the
	 * directory and checks every entry against this), since that is where an artist finds out.
	 */
	private static final Map<String, List<Art>> VARIANTS = discoverVariants();

	private static Map<String, List<Art>> discoverVariants() {
		Map<String, List<Art>> out = new LinkedHashMap<>();
		for (Patch patch : ALL) {
			List<Art> found = new java.util.ArrayList<>();
			found.add(patch.art());
			for (int w = 2; w <= MAX_ART; w += 2) {
				for (int h = 2; h <= MAX_ART; h += 2) {
					Art art = new Art(patch.id(), w, h, false);
					if (w == patch.width() && h == patch.height()) continue;   // that is the default's own name
					if (Patches.class.getResource(art.resource()) == null) continue;
					validate(patch, art);
					found.add(art);
				}
			}
			found.sort(java.util.Comparator.comparingInt(a -> a.width() * a.height()));
			out.put(patch.id(), List.copyOf(found));
		}
		return Map.copyOf(out);
	}

	/**
	 * A variant that cannot be drawn is a mistake to say out loud rather than to fall back from:
	 * the even sizes and the {@link #MAX_ART} cap are the same rules the catalogue's own art
	 * follows (the art is centred in its cell, so an odd size has no place to sit), and a seat
	 * variant is the seat's full width for the same reason a seat patch is — half of it goes on
	 * each leg, so there is nowhere for a narrower one to be.
	 */
	private static void validate(Patch patch, Art art) {
		if (patch.seat() && art.width() != 2 * Spot.PX) {
			throw new IllegalArgumentException(art.file() + ": a seat patch's art is " + 2 * Spot.PX
					+ " px wide, so a variant of it cannot be " + art.width() + " px wide");
		}
		if (patch.seat() && art.height() > SEAT_HEIGHT_MAX) {
			throw new IllegalArgumentException(art.file() + ": a seat patch's art is at most " + SEAT_HEIGHT_MAX + " px tall");
		}
	}

	/** Every PNG a patch ships, the default among them, smallest first. */
	public static List<Art> variants(Patch patch) {
		return VARIANTS.get(patch.id());
	}

	/**
	 * Which of a patch's PNGs a cell shows: the largest that fits what the cell asks for
	 * ({@link Fit}), and the default when none of them does — which is exactly today's behaviour
	 * for a patch that ships only the one file, and for one whose variants are all too big for a
	 * face the art is clipped to.
	 *
	 * <p>So a 12×12 patch with an 8×8 and a 16×16 variant lands on a shoulder as the 8×8 (whole,
	 * not clipped), on the big back cell as the 16×16 (filling it), on an ordinary chest cell as
	 * the 12×12 it was drawn as (hanging over its neighbours, by design) and in the inventory as
	 * the 16×16 (unscaled). Every path asks this, so none of them can draw a different one.
	 */
	public static Art artFor(Patch patch, Spot spot) {
		return artFor(patch, Fit.of(spot));
	}

	/** The same by fit alone, which is how the instant channel's library is keyed. */
	public static Art artFor(Patch patch, Fit fit) {
		return switch (fit) {
			// The artist's own size: nothing bigger fits it, and anything smaller is a worse fit.
			case OVER -> patch.art();
			case CLIPPED -> largestIn(patch, Spot.PX, Spot.PX);
			case FILLED -> largestIn(patch, MAX_ART, MAX_ART);
		};
	}

	/** A patch's inventory icon: the art that fills the {@value #ICON} px sprite without scaling, if it ships one. */
	public static Art iconArt(Patch patch) {
		return largestIn(patch, ICON, ICON);
	}

	/** The largest of a patch's PNGs that sits in a {@code w}×{@code h} box whole; the default if none does. */
	private static Art largestIn(Patch patch, int w, int h) {
		Art best = null;
		for (Art art : variants(patch)) {
			if (!art.fitsIn(w, h)) continue;
			if (best == null || art.width() * art.height() > best.width() * best.height()) best = art;
		}
		return best == null ? patch.art() : best;
	}
}
