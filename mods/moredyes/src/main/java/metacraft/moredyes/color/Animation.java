package metacraft.moredyes.color;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import metacraft.moredyes.color.ModColor.Ramp;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A colour that drifts through keyframes instead of holding still. Textures become a strip of one
 * frame per keyframe with a vanilla {@code .mcmeta} animation; worn armour follows the same
 * blend on each viewer's pack clock.
 *
 * @param frames	  the keyframes, in order; the last blends back into the first
 * @param frametime   ticks per keyframe
 * @param interpolate whether vanilla blends between keyframes (it does for sprite animations that ask)
 */
public record Animation(List<Frame> frames, int frametime, boolean interpolate) {

	/** One keyframe: the colour itself and the dark/light stops its textures are recoloured with, all plain {@code 0xRRGGBB}. */
	public record Frame(int rgb, int rampDark, int rampLight) {
		// the string codec hands back opaque ARGB; a frame is blended per channel, so it keeps just RGB
		private static final Codec<Integer> RGB = ExtraCodecs.STRING_RGB_COLOR.xmap(argb -> argb & 0xFFFFFF, rgb -> rgb);
		private static final Codec<Ramp> RAMP = RGB.listOf().comapFlatMap(
				colours -> colours.size() == 2 ?
						DataResult.success(new Ramp(colours.get(0), colours.get(1))) :
						DataResult.error(() -> "\"ramp\" must be [dark, light]"),
				ramp -> List.of(ramp.rampDark(), ramp.rampLight())
		);

		private static final Codec<Frame> FROM_HEX = RGB.xmap(
				rgb -> new Frame(rgb, ModColor.deriveRamp(rgb, true), ModColor.deriveRamp(rgb, false)),
				Frame::rgb
		);

		private static final Codec<Frame> FROM_OBJECT = RecordCodecBuilder.create(
				instance -> instance.group(
						RGB.fieldOf("rgb").forGetter(Frame::rgb),
						RAMP.optionalFieldOf("ramp").forGetter(frame -> Optional.of(new Ramp(frame.rampDark(), frame.rampLight())))
				).apply(instance, (rgb, ramp) -> new Frame(
						rgb,
						ramp.map(Ramp::rampDark).orElseGet(() -> ModColor.deriveRamp(rgb, true)),
						ramp.map(Ramp::rampLight).orElseGet(() -> ModColor.deriveRamp(rgb, false))
				))
		);

		public static final Codec<Frame> CODEC = Codec.withAlternative(FROM_OBJECT, FROM_HEX);
	}

	public static final Codec<Animation> CODEC = RecordCodecBuilder.<Animation>create(
			instance -> instance.group(
					Frame.CODEC.listOf().fieldOf("frames").forGetter(Animation::frames),
					Codec.INT.fieldOf("frametime").forGetter(Animation::frametime),
					Codec.BOOL.optionalFieldOf("interpolate", true).forGetter(Animation::interpolate)
			).apply(instance, Animation::new)
	).validate(animation -> {
		if (animation.frames.size() < 2) return DataResult.error(() -> "\"frames\" needs at least 2 keyframes");
		if (animation.frametime < 1) return DataResult.error(() -> "\"frametime\" must be at least 1");
		return DataResult.success(animation);
	});

	public Animation {
		frames = List.copyOf(frames);
	}

	/** Ticks for the whole loop. */
	public int cycleTicks() {
		return frames.size() * frametime;
	}

	/**
	 * The colour {@code tick} ticks into the loop (wrapping), blended per channel between the two
	 * surrounding keyframes and truncated, as vanilla's sprite interpolation does.
	 */
	public int rgbAt(double tick) {
		double cycle = cycleTicks();
		double t = tick % cycle;
		if (t < 0) t += cycle;
		int i = (int) (t / frametime);
		Frame current = frames.get(i);
		if (!interpolate) return current.rgb;
		Frame next = frames.get((i + 1) % frames.size());
		double f = (t - (double) i * frametime) / frametime;
		return (blend(current.rgb >> 16 & 0xFF, next.rgb >> 16 & 0xFF, f) << 16)
				| (blend(current.rgb >> 8 & 0xFF, next.rgb >> 8 & 0xFF, f) << 8)
				| blend(current.rgb & 0xFF, next.rgb & 0xFF, f);
	}

	private static int blend(int from, int to, double f) {
		return (int) (from * (1 - f) + to * f);
	}

	/** The distinct nearest map colours of the keyframes, in frame order. */
	public List<MapColor> mapColors() {
		List<MapColor> colours = new ArrayList<>();
		for (Frame frame : frames) {
			MapColor colour = ModColor.nearestMapColor(frame.rgb);
			if (!colours.contains(colour)) colours.add(colour);
		}
		return colours;
	}

	/** The sprite's {@code .png.mcmeta}. */
	public String mcmeta() {
		return "{\"animation\":{\"frametime\":" + frametime + ",\"interpolate\":" + interpolate + "}}";
	}
}
